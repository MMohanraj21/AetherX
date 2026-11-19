"""
AETHER X - Test Suite: Gemini Provider
Tests Gemini API connectivity, API key validation, text generation,
multi-turn conversation, multimodal image analysis, streaming, and error handling.
"""

import base64
import sys
import unittest
from pathlib import Path

backend_dir = Path(__file__).resolve().parent.parent
if str(backend_dir) not in sys.path:
    sys.path.insert(0, str(backend_dir))

from app.config import settings
from app.providers.gemini_provider import GeminiProvider


class TestGeminiProvider(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.provider = GeminiProvider(
            api_key=settings.GEMINI_API_KEY,
            default_model=settings.GEMINI_MODEL,
            reasoning_model=settings.GEMINI_REASONING_MODEL,
            fallback_model=settings.GEMINI_FALLBACK_MODEL
        )

    def test_01_api_connectivity_and_key_validation(self):
        """Verify API connectivity and key validation against Google's live server."""
        status = self.provider.validate_connection()
        self.assertTrue(status.get("valid"), f"API validation failed: {status}")
        self.assertEqual(status.get("status_code"), 200)
        self.assertGreater(status.get("model_count", 0), 0)

    def test_02_text_generation(self):
        """Test text generation with Gemini model."""
        prompt = "Respond with the single word: PONG."
        # Use fallback model if flash is rate limited, else default
        reply = self.provider.generate_text(prompt=prompt, temperature=0.1)
        self.assertIsNotNone(reply)
        self.assertIn("PONG", reply.upper())

    def test_03_multi_turn_conversation(self):
        """Test multi-turn chat memory and turn structure."""
        messages = [
            {"role": "user", "content": "My secret code is 98765."},
            {"role": "model", "content": "Got it! Your code is 98765."},
            {"role": "user", "content": "What is my secret code?"}
        ]
        result = self.provider.chat(messages=messages)
        self.assertIn("98765", result.get("content", ""))
        self.assertEqual(result.get("role"), "model")

    def test_04_image_analysis(self):
        """Test multimodal image understanding with sample image data."""
        # 1x1 red PNG base64
        sample_png_b64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
        result = self.provider.analyze_image(
            image_data=sample_png_b64,
            mime_type="image/png",
            prompt="What color is this image?",
            mode="general"
        )
        self.assertIsNotNone(result.get("analysis"))
        self.assertEqual(result.get("mode"), "general")

    def test_05_structured_json_generation(self):
        """Test strict structured JSON output."""
        prompt = "Provide a JSON object with keys 'greeting' (string) and 'score' (number 10)."
        result = self.provider.generate_structured(prompt=prompt)
        self.assertIsInstance(result, dict)
        self.assertIn("greeting", result)
        self.assertEqual(result.get("score"), 10)

    def test_06_streaming(self):
        """Test streaming SSE token generator."""
        messages = [{"role": "user", "content": "Count from 1 to 3."}]
        stream = self.provider.stream_chat(messages=messages)
        tokens = list(stream)
        full_text = "".join(tokens)
        self.assertGreater(len(tokens), 0)
        self.assertIn("1", full_text)

    def test_07_invalid_key_error_handling(self):
        """Test error handling when API key is invalid."""
        bad_provider = GeminiProvider(api_key="INVALID_TEST_KEY_12345")
        status = bad_provider.validate_connection()
        self.assertFalse(status.get("valid"))

    def test_08_token_metrics_tracking(self):
        """Verify token metrics are updated."""
        metrics = self.provider.get_usage_metrics()
        self.assertIn("total_tokens", metrics)
        self.assertIn("total_requests", metrics)
        self.assertGreaterEqual(metrics["total_requests"], 1)


if __name__ == "__main__":
    unittest.main()
