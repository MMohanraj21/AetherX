"""
AETHER X - Travel Agent
Designs personalized travel itineraries, recommends flights, accommodations,
cultural etiquette, and transit solutions.
"""

from typing import Any, Dict, Optional
from app.providers.provider_manager import provider_manager


class TravelAgent:
    """Specialized agent for trip planning and navigation recommendations."""

    SYSTEM_INSTRUCTION = (
        "You are the AETHER Travel Specialist Agent. You craft structured, realistic, day-by-day travel itineraries. "
        "Include morning, afternoon, and evening activities, transit tips, local culinary highlights, estimated budgets, "
        "and cultural customs. Provide structured, actionable travel advice."
    )

    def __init__(self):
        self.name = "travel_agent"

    def process(
        self,
        query: str,
        destination: Optional[str] = None,
        duration_days: Optional[int] = None,
        explicit_provider: Optional[str] = None
    ) -> Dict[str, Any]:
        prompt = query
        if destination:
            prompt = f"Destination: {destination} (Duration: {duration_days or 'flexible'} days)\nUser Query: {query}"

        result = provider_manager.generate_text(
            prompt=prompt,
            task_type="simple_conversation",
            explicit_provider=explicit_provider,
            system_instruction=self.SYSTEM_INSTRUCTION
        )

        return {
            "agent": self.name,
            "itinerary": result.get("text", ""),
            "provider_used": result.get("provider", "gemini"),
            "model_used": result.get("model", "")
        }


travel_agent = TravelAgent()
