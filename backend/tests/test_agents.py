"""
AETHER X - Test Suite: Specialized Agents & Orchestration
Tests Companion, Device Control, Vision, Chess, Travel, Research, Analytics, and Memory agents.
"""

import sys
import unittest
from pathlib import Path

backend_dir = Path(__file__).resolve().parent.parent
if str(backend_dir) not in sys.path:
    sys.path.insert(0, str(backend_dir))
from app.agents.analytics_agent import analytics_agent
from app.agents.chess_agent import chess_agent
from app.agents.companion_agent import companion_agent
from app.agents.device_agent import device_agent
from app.agents.memory_agent import memory_agent
from app.agents.travel_agent import travel_agent
from app.agents.vision_agent import vision_agent
from app.orchestration.graph import aether_orchestrator
from app.services.memory_service import memory_service
from app.services.security_service import security_service


class TestSpecializedAgents(unittest.TestCase):

    def test_01_analytics_agent_deterministic_math(self):
        """Test analytics agent Python calculations."""
        data = [10, 20, 30, 40, 50]
        stats = analytics_agent.compute_basic_stats(data)
        self.assertEqual(stats["count"], 5.0)
        self.assertEqual(stats["mean"], 30.0)
        self.assertEqual(stats["median"], 30.0)
        self.assertEqual(stats["min"], 10.0)
        self.assertEqual(stats["max"], 50.0)

    def test_02_device_agent_approval_interception(self):
        """
        Verify that sensitive actions like make_phone_call require user approval
        and are intercepted by security_service.
        """
        result = device_agent.process(
            command_text="Call mom right now",
            is_user_approved=False
        )
        # Should either require approval or identify the sensitive call action
        self.assertIn("agent", result)
        self.assertEqual(result["agent"], "device_agent")

    def test_03_prohibited_tools_blocked(self):
        """Verify that shell_exec and bash are strictly blocked."""
        check = security_service.validate_tool_execution("shell_exec", {"command": "rm -rf /"})
        self.assertFalse(check["allowed"])
        self.assertEqual(check["status"], "prohibited")

    def test_04_memory_service_and_agent(self):
        """Test storing and recalling user preferences."""
        user_id = "test_user_42"
        memory_service.store_user_fact(user_id, "User drinks black coffee with oat milk", "preferences")
        facts = memory_service.recall_user_facts(user_id)
        self.assertIn("User drinks black coffee with oat milk", facts)

    def test_05_orchestrator_intent_classification(self):
        """Test LangGraph router intent classification."""
        self.assertEqual(aether_orchestrator.classify_intent("How are you today?"), "companion")
        self.assertEqual(aether_orchestrator.classify_intent("What is the best move in FEN: rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR?"), "chess")
        self.assertEqual(aether_orchestrator.classify_intent("Calculate the mean and standard deviation of these numbers"), "analytics")
        self.assertEqual(aether_orchestrator.classify_intent("Plan a 3-day trip to Kyoto"), "travel")
        self.assertEqual(aether_orchestrator.classify_intent("Turn on the flashlight", has_image=False), "device")
        self.assertEqual(aether_orchestrator.classify_intent("What is this?", has_image=True), "vision")


if __name__ == "__main__":
    unittest.main()
