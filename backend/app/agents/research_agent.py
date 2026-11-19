"""
AETHER X - Research Agent
Executes structured scientific, academic, and technical deep-dives with citations,
hypothesis testing, and multi-perspective synthesis.
"""

from typing import Any, Dict, Optional
from app.providers.provider_manager import provider_manager


class ResearchAgent:
    """Specialized agent for comprehensive research queries and document synthesis."""

    SYSTEM_INSTRUCTION = (
        "You are the AETHER Research Agent, powered by advanced reasoning AI models. "
        "When investigating a subject, provide: "
        "1. Executive Summary\n"
        "2. Core Concepts & Evidence\n"
        "3. Counter-arguments & Nuance\n"
        "4. Practical Applications & Citations\n"
        "Maintain academic rigor, logical structure, and objective clarity."
    )

    def __init__(self):
        self.name = "research_agent"

    def process(
        self,
        research_topic: str,
        explicit_provider: Optional[str] = None
    ) -> Dict[str, Any]:
        result = provider_manager.generate_text(
            prompt=research_topic,
            task_type="complex_reasoning",
            explicit_provider=explicit_provider,
            system_instruction=self.SYSTEM_INSTRUCTION
        )

        return {
            "agent": self.name,
            "research_report": result.get("text", ""),
            "provider_used": result.get("provider", "gemini"),
            "model_used": result.get("model", ""),
            "task_type": "complex_reasoning"
        }


research_agent = ResearchAgent()
