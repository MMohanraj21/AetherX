"""
AETHER X - Test Suite: Provider Manager
Tests multi-provider routing, explicit user provider selection,
fallback mechanics, and telemetry tracking.
"""

import sys
import unittest
from pathlib import Path

backend_dir = Path(__file__).resolve().parent.parent
if str(backend_dir) not in sys.path:
    sys.path.insert(0, str(backend_dir))

from app.providers.provider_manager import ProviderManager


class TestProviderManager(unittest.TestCase):

    def setUp(self):
        self.manager = ProviderManager()

    def test_01_default_auto_routing(self):
        """Test routing under 'auto' mode."""
        self.manager.set_user_provider_preference("auto")
        # Simple conversation should route to Gemini
        target = self.manager.get_effective_provider_for_task("simple_conversation")
        self.assertEqual(target, "gemini")

        # Image understanding should route to Gemini
        img_target = self.manager.get_effective_provider_for_task("image_understanding")
        self.assertEqual(img_target, "gemini")

    def test_02_explicit_provider_selection(self):
        """Test explicit selection of Gemini and OpenAI."""
        self.manager.set_user_provider_preference("gemini")
        self.assertEqual(self.manager.active_provider_preference, "gemini")
        self.assertEqual(self.manager.get_effective_provider_for_task(), "gemini")

        self.manager.set_user_provider_preference("openai")
        self.assertEqual(self.manager.active_provider_preference, "openai")
        self.assertEqual(self.manager.get_effective_provider_for_task(), "openai")

    def test_03_invalid_provider_selection(self):
        """Test that invalid provider preference raises ValueError."""
        with self.assertRaises(ValueError):
            self.manager.set_user_provider_preference("unsupported_ai")

    def test_04_no_silent_fallback_when_explicit(self):
        """
        Verify rule: Never silently switch providers when user has selected a specific provider.
        """
        self.manager.set_user_provider_preference("openai")
        # If OpenAI is not configured, it must raise an error and NOT silently switch to Gemini!
        if not self.manager.openai.is_configured:
            with self.assertRaises(RuntimeError):
                self.manager.generate_text(prompt="Hello", explicit_provider="openai")

    def test_05_telemetry_tracking(self):
        """Verify telemetry returns both gemini and openai stats."""
        telemetry = self.manager.get_system_telemetry()
        self.assertIn("active_preference", telemetry)
        self.assertIn("gemini_metrics", telemetry)
        self.assertIn("openai_metrics", telemetry)


if __name__ == "__main__":
    unittest.main()
