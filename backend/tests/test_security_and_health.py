"""
AETHER X - Test Suite: Security & Health Check
Tests token authentication, rate limiting, request validation,
and verifies that health endpoints never expose secret API keys.
"""

import sys
import unittest
from pathlib import Path

backend_dir = Path(__file__).resolve().parent.parent
if str(backend_dir) not in sys.path:
    sys.path.insert(0, str(backend_dir))

from app.config import settings
from app.routes.chat import process_chat_message
from app.routes.providers import health_check_handler
from app.services.security_service import security_service


class TestSecurityAndHealth(unittest.TestCase):

    def test_01_health_endpoint_no_credentials_leaked(self):
        """
        CRITICAL TEST: Ensure health endpoint NEVER exposes Gemini or OpenAI API keys.
        """
        health = health_check_handler()
        raw_health_str = str(health)

        self.assertIn("status", health)
        self.assertIn("gemini", health)
        self.assertIn("openai", health)

        # Assert no real secret keys appear in the response!
        if settings.GEMINI_API_KEY:
            self.assertNotIn(settings.GEMINI_API_KEY, raw_health_str)
        if settings.OPENAI_API_KEY:
            self.assertNotIn(settings.OPENAI_API_KEY, raw_health_str)

    def test_02_authentication(self):
        """Test token validation."""
        valid_token = f"Bearer {settings.AETHER_AUTH_SECRET}"
        invalid_token = "Bearer completely_fake_and_wrong_token_999"

        self.assertTrue(security_service.authenticate_token(valid_token))
        self.assertFalse(security_service.authenticate_token(invalid_token))
        self.assertFalse(security_service.authenticate_token(None))

    def test_03_rate_limiting(self):
        """Test sliding window rate limiter."""
        test_client = "test_client_rate_limit_ip"
        # Reset client
        security_service._request_timestamps[test_client] = []

        # Consume up to limit
        for _ in range(settings.RATE_LIMIT_PER_MINUTE):
            allowed = security_service.check_rate_limit(test_client)
            self.assertTrue(allowed)

        # Next request must be rate-limited
        rate_limited = security_service.check_rate_limit(test_client)
        self.assertFalse(rate_limited)

    def test_04_invalid_request_empty_message(self):
        """Test that empty messages are rejected."""
        res = process_chat_message(
            message="   ",
            conversation_id="test_conv",
            client_ip="127.0.0.1"
        )
        self.assertEqual(res.get("status"), "error")
        self.assertIn("empty", res.get("error", "").lower())


if __name__ == "__main__":
    unittest.main()
