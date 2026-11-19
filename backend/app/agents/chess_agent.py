"""
AETHER X - Chess Agent
Analyzes chess positions (FEN / PGN), opening strategy, tactical blunders,
and calculates best candidate moves.
"""

from typing import Any, Dict, Optional
from app.providers.provider_manager import provider_manager


class ChessAgent:
    """Specialized chess mentor and tactical analysis agent."""

    SYSTEM_INSTRUCTION = (
        "You are the AETHER Chess Grandmaster Agent. You analyze chess positions given in FEN, PGN, or move lists. "
        "Evaluate piece placement, pawn structure, king safety, tactical motifs (pins, forks, skewers, discovery), "
        "and suggest top 3 engine-caliber candidate moves with concise tactical explanations."
    )

    def __init__(self):
        self.name = "chess_agent"

    def process(
        self,
        query: str,
        fen_or_moves: Optional[str] = None,
        explicit_provider: Optional[str] = None
    ) -> Dict[str, Any]:
        prompt = query
        if fen_or_moves:
            prompt = f"Board Position / Moves: {fen_or_moves}\n\nAnalysis Request: {query}"

        result = provider_manager.generate_text(
            prompt=prompt,
            task_type="complex_reasoning",
            explicit_provider=explicit_provider,
            system_instruction=self.SYSTEM_INSTRUCTION
        )

        return {
            "agent": self.name,
            "analysis": result.get("text", ""),
            "provider_used": result.get("provider", "gemini"),
            "model_used": result.get("model", "")
        }


chess_agent = ChessAgent()
