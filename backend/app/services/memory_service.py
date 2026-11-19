"""
AETHER X - Memory Service
Manages short-term conversation buffers, long-term user preferences,
and context retrieval for personalized agent interactions.
"""

import time
import logging
from typing import Any, Dict, List, Optional

logger = logging.getLogger("aether.memory")


class MemoryService:
    """
    Hybrid short-term turn manager and long-term user profile store.
    """

    def __init__(self):
        # Short-term conversation history: {conversation_id: [messages]}
        self._conversations: Dict[str, List[Dict[str, Any]]] = {}
        # Long-term semantic user facts: {user_id: [facts]}
        self._user_memory: Dict[str, List[Dict[str, Any]]] = {}

    def add_turn(
        self,
        conversation_id: str,
        role: str,
        content: str,
        metadata: Optional[Dict[str, Any]] = None
    ):
        """Record a single dialogue turn in conversation history."""
        if conversation_id not in self._conversations:
            self._conversations[conversation_id] = []

        turn = {
            "role": role,
            "content": content,
            "timestamp": time.time(),
            "metadata": metadata or {}
        }
        self._conversations[conversation_id].append(turn)

        # Keep last 50 turns in buffer
        if len(self._conversations[conversation_id]) > 50:
            self._conversations[conversation_id] = self._conversations[conversation_id][-50:]

    def get_conversation_history(
        self,
        conversation_id: str,
        limit: int = 20
    ) -> List[Dict[str, str]]:
        """Retrieve formatted conversation history for AI provider context."""
        turns = self._conversations.get(conversation_id, [])[-limit:]
        return [{"role": t["role"], "content": t["content"]} for t in turns]

    def store_user_fact(self, user_id: str, fact: str, category: str = "general"):
        """Save a long-term preference or fact about the user."""
        if user_id not in self._user_memory:
            self._user_memory[user_id] = []

        self._user_memory[user_id].append({
            "fact": fact,
            "category": category,
            "timestamp": time.time()
        })
        logger.info(f"Stored long-term fact for user {user_id}: {fact}")

    def recall_user_facts(self, user_id: str, limit: int = 10) -> List[str]:
        """Recall relevant stored facts for prompt enrichment."""
        facts = self._user_memory.get(user_id, [])
        return [f["fact"] for f in facts[-limit:]]

    def clear_conversation(self, conversation_id: str):
        """Wipe conversation history."""
        if conversation_id in self._conversations:
            del self._conversations[conversation_id]


memory_service = MemoryService()
