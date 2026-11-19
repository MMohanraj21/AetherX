"""
AETHER X - LangGraph Orchestration & Multi-Agent Workflow
Coordinates specialized agents (Companion, Device, Vision, Chess, Travel, Research, Analytics, Memory)
using a state machine graph with controlled context sharing, tool allowlists, and approval gates.
"""

import logging
from typing import Any, Dict, List, Optional, TypedDict

from app.agents.analytics_agent import analytics_agent
from app.agents.chess_agent import chess_agent
from app.agents.companion_agent import companion_agent
from app.agents.device_agent import device_agent
from app.agents.memory_agent import memory_agent
from app.agents.research_agent import research_agent
from app.agents.travel_agent import travel_agent
from app.agents.vision_agent import vision_agent
from app.providers.provider_manager import provider_manager
from app.services.memory_service import memory_service

logger = logging.getLogger("aether.graph")

# LangGraph availability check
HAS_LANGGRAPH = False
try:
    from langgraph.graph import StateGraph, END
    HAS_LANGGRAPH = True
except ImportError:
    HAS_LANGGRAPH = False
    logger.info("LangGraph package not installed; using embedded high-performance state workflow graph.")


class AetherGraphState(TypedDict, total=False):
    """Shared state dictionary passed across agents in the graph."""
    user_id: str
    conversation_id: str
    user_message: str
    image_data: Optional[Any]
    image_mode: Optional[str]
    explicit_provider: Optional[str]
    intent_target: str
    agent_output: Dict[str, Any]
    requires_approval: bool
    final_response: str
    provider_used: str
    status: str


class AetherOrchestrator:
    """
    Orchestrates the multi-agent workflow graph.
    """

    def __init__(self):
        self.has_langgraph = HAS_LANGGRAPH
        if self.has_langgraph:
            self._graph = self._build_langgraph()
        else:
            self._graph = None

    def classify_intent(self, user_message: str, has_image: bool = False) -> str:
        """Route user query to the best-suited specialized agent."""
        if has_image:
            return "vision"

        msg = user_message.lower()

        # Device control triggers
        device_keywords = ["turn on", "turn off", "volume", "brightness", "wifi", "bluetooth", "alarm", "battery", "call ", "sms "]
        if any(k in msg for k in device_keywords):
            return "device"

        # Chess triggers
        chess_keywords = ["chess", "fen", "pgn", "checkmate", "gambit", "e4", "d4", "castle", "queen", "rook"]
        if any(k in msg for k in chess_keywords):
            return "chess"

        # Travel triggers
        travel_keywords = ["itinerary", "travel to", "hotel in", "flight to", "visit ", "vacation", "trip to"]
        if any(k in msg for k in travel_keywords):
            return "travel"

        # Analytics triggers
        analytics_keywords = ["calculate", "statistics", "mean", "median", "standard deviation", "dataset", "average of"]
        if any(k in msg for k in analytics_keywords):
            return "analytics"

        # Memory triggers
        memory_keywords = ["remember that", "remember my", "my favorite", "my birthday is", "forget that"]
        if any(k in msg for k in memory_keywords):
            return "memory"

        # Research triggers
        research_keywords = ["research", "deep dive", "academic papers", "history of", "explain the theory", "comprehensive analysis"]
        if any(k in msg for k in research_keywords):
            return "research"

        # Default to warm empathetic companion agent
        return "companion"

    def _build_langgraph(self):
        """Construct StateGraph when langgraph library is present."""
        try:
            workflow = StateGraph(AetherGraphState)

            def router_node(state: AetherGraphState) -> AetherGraphState:
                intent = self.classify_intent(state.get("user_message", ""), bool(state.get("image_data")))
                state["intent_target"] = intent
                return state

            def dispatch_node(state: AetherGraphState) -> AetherGraphState:
                return self._execute_agent(state)

            workflow.add_node("router", router_node)
            workflow.add_node("dispatcher", dispatch_node)
            workflow.set_entry_point("router")
            workflow.add_edge("router", "dispatcher")
            workflow.add_edge("dispatcher", END)

            return workflow.compile()
        except Exception as e:
            logger.warning(f"Failed to build LangGraph StateGraph: {e}")
            return None

    def _execute_agent(self, state: AetherGraphState) -> AetherGraphState:
        """Execute selected agent within controlled security and context boundary."""
        target = state.get("intent_target", "companion")
        user_message = state.get("user_message", "")
        conversation_id = state.get("conversation_id", "default_conv")
        user_id = state.get("user_id", "default_user")
        provider = state.get("explicit_provider")

        agent_result: Dict[str, Any] = {}

        if target == "vision":
            agent_result = vision_agent.process(
                image_data=state.get("image_data"),
                mime_type="image/jpeg",
                prompt=user_message,
                mode=state.get("image_mode", "general"),
                explicit_provider=provider
            )
            state["final_response"] = agent_result.get("analysis", "")

        elif target == "device":
            agent_result = device_agent.process(
                command_text=user_message,
                is_user_approved=False,
                explicit_provider=provider
            )
            state["requires_approval"] = agent_result.get("requires_dialog", False)
            state["final_response"] = agent_result.get("message", agent_result.get("error", ""))

        elif target == "chess":
            agent_result = chess_agent.process(
                query=user_message,
                explicit_provider=provider
            )
            state["final_response"] = agent_result.get("analysis", "")

        elif target == "travel":
            agent_result = travel_agent.process(
                query=user_message,
                explicit_provider=provider
            )
            state["final_response"] = agent_result.get("itinerary", "")

        elif target == "research":
            agent_result = research_agent.process(
                research_topic=user_message,
                explicit_provider=provider
            )
            state["final_response"] = agent_result.get("research_report", "")

        elif target == "analytics":
            agent_result = analytics_agent.process(
                query=user_message,
                explicit_provider=provider
            )
            state["final_response"] = agent_result.get("analysis", "")

        elif target == "memory":
            agent_result = memory_agent.process(
                conversation_text=user_message,
                user_id=user_id,
                explicit_provider=provider
            )
            state["final_response"] = agent_result.get("message", "")

        else:  # companion
            agent_result = companion_agent.process(
                user_message=user_message,
                conversation_id=conversation_id,
                user_id=user_id,
                explicit_provider=provider
            )
            state["final_response"] = agent_result.get("response", "")

        state["agent_output"] = agent_result
        state["provider_used"] = agent_result.get("provider_used", "gemini")
        state["status"] = "completed"
        return state

    def run(
        self,
        user_message: str,
        conversation_id: str = "default_conv",
        user_id: str = "default_user",
        image_data: Optional[Any] = None,
        image_mode: Optional[str] = "general",
        explicit_provider: Optional[str] = None
    ) -> Dict[str, Any]:
        """Entry point for executing the multi-agent graph."""
        initial_state: AetherGraphState = {
            "user_id": user_id,
            "conversation_id": conversation_id,
            "user_message": user_message,
            "image_data": image_data,
            "image_mode": image_mode,
            "explicit_provider": explicit_provider,
            "intent_target": self.classify_intent(user_message, bool(image_data)),
            "status": "started"
        }

        # If compiled langgraph exists, invoke it
        if self._graph:
            try:
                final_state = self._graph.invoke(initial_state)
                return {
                    "intent": final_state.get("intent_target"),
                    "response": final_state.get("final_response"),
                    "provider": final_state.get("provider_used"),
                    "agent_details": final_state.get("agent_output", {}),
                    "requires_approval": final_state.get("requires_approval", False)
                }
            except Exception as e:
                logger.warning(f"LangGraph invocation error: {e}; falling back to direct executor")

        # Fallback executor
        final_state = self._execute_agent(initial_state)
        return {
            "intent": final_state.get("intent_target"),
            "response": final_state.get("final_response"),
            "provider": final_state.get("provider_used"),
            "agent_details": final_state.get("agent_output", {}),
            "requires_approval": final_state.get("requires_approval", False)
        }


aether_orchestrator = AetherOrchestrator()
