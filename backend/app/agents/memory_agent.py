"""
AETHER X - Memory Agent
Responsible for extracting persistent facts, user preferences, and profile updates
from conversations, and storing them in the MemoryService.
"""

from typing import Any, Dict, List, Optional
from app.providers.provider_manager import provider_manager
from app.services.memory_service import memory_service


class MemoryAgent:
    """Specialized agent for extracting and consolidating user facts and preferences."""

    SYSTEM_INSTRUCTION = (
        "You are the AETHER Memory Consolidation Agent. Your duty is to analyze conversational statements "
        "and extract durable facts about the user (e.g. name, preferences, favorite things, dietary restrictions, "
        "family, hobbies, work). Output JSON: {\"facts\": [\"User likes Earl Grey tea\", \"User is studying Kotlin\"], "
        "\"user_reply\": \"Acknowledged memory update.\"}"
    )

    def __init__(self):
        self.name = "memory_agent"

    def process(
        self,
        conversation_text: str,
        user_id: str = "default_user",
        explicit_provider: Optional[str] = None
    ) -> Dict[str, Any]:
        result = provider_manager.gemini.generate_structured(
            prompt=f"Extract key personal facts from this text:\n{conversation_text}",
            system_instruction=self.SYSTEM_INSTRUCTION
        )

        extracted_facts = result.get("facts", [])
        for f in extracted_facts:
            memory_service.store_user_fact(user_id=user_id, fact=f, category="profile")

        return {
            "agent": self.name,
            "facts_saved": extracted_facts,
            "message": result.get("user_reply", f"Saved {len(extracted_facts)} preferences to memory.")
        }


memory_agent = MemoryAgent()
