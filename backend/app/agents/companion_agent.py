"""
AETHER X - Companion Agent
Specialized in proactive, warm, empathetic conversation, daily scheduling,
and personal assistance with voice-optimized natural tone.
"""

from typing import Any, Dict, List, Optional
from app.providers.provider_manager import provider_manager
from app.services.memory_service import memory_service


class CompanionAgent:
    """Agent for daily conversation, companionship, and ambient assistance."""

    SYSTEM_INSTRUCTION = (
        "You are AETHER, an intelligent, empathetic, and witty Android AI companion. "
        "You speak naturally and concisely, optimized for both voice reading and text display. "
        "You remember the user's habits, offer constructive advice, and provide genuine warmth. "
        "Always maintain a helpful, proactive, and respectful demeanor."
    )

    def __init__(self):
        self.name = "companion_agent"

    def process(
        self,
        user_message: str,
        conversation_id: str = "default",
        user_id: str = "default_user",
        explicit_provider: Optional[str] = None
    ) -> Dict[str, Any]:
        """Generate companion reply taking user memory into account."""
        # Recall long-term user context
        facts = memory_service.recall_user_facts(user_id)
        context_str = "\n".join(f"- {f}" for f in facts) if facts else "No prior recorded facts."

        full_system = f"{self.SYSTEM_INSTRUCTION}\n\nKnown context about the user:\n{context_str}"

        # Fetch recent turns
        history = memory_service.get_conversation_history(conversation_id, limit=6)
        messages = list(history)
        messages.append({"role": "user", "content": user_message})

        result = provider_manager.chat(
            messages=messages,
            task_type="simple_conversation",
            explicit_provider=explicit_provider,
            system_instruction=full_system
        )

        reply = result.get("content", "")
        # Save turns to memory
        memory_service.add_turn(conversation_id, "user", user_message)
        memory_service.add_turn(conversation_id, "assistant", reply)

        return {
            "agent": self.name,
            "response": reply,
            "provider_used": result.get("provider", "gemini"),
            "model_used": result.get("model_used", ""),
            "fallback_triggered": result.get("fallback_triggered", False)
        }


companion_agent = CompanionAgent()
