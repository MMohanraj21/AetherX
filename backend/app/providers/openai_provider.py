"""
AETHER X - OpenAI API Provider
Provides secondary/fallback model provider integration for AETHER X.
Supports text generation, chat, image analysis, and token tracking.
"""

import base64
import json
import logging
from typing import Any, Dict, Generator, List, Optional, Union

import requests

logger = logging.getLogger("aether.openai")


class OpenAIProvider:
    """
    OpenAI provider implementation for AETHER X.
    Acts as primary or secondary fallback provider depending on system configuration.
    """

    def __init__(
        self,
        api_key: Optional[str] = None,
        default_model: str = "gpt-4o",
        base_url: str = "https://api.openai.com/v1",
    ):
        self.api_key = (api_key or "").strip()
        self.default_model = default_model
        self.base_url = base_url.rstrip("/")

        # Metrics
        self.total_prompt_tokens = 0
        self.total_completion_tokens = 0
        self.total_requests = 0
        self.failed_requests = 0

    @property
    def is_configured(self) -> bool:
        return bool(self.api_key)

    def validate_connection(self) -> Dict[str, Any]:
        """Verify API key validity against OpenAI."""
        if not self.is_configured:
            return {"valid": False, "error": "OpenAI API key not configured"}

        headers = {"Authorization": f"Bearer {self.api_key}"}
        try:
            res = requests.get(f"{self.base_url}/models", headers=headers, timeout=10)
            if res.status_code == 200:
                data = res.json()
                models = [m.get("id") for m in data.get("data", [])]
                return {
                    "valid": True,
                    "status_code": 200,
                    "model_count": len(models),
                    "default_model_available": self.default_model in models,
                }
            else:
                self.failed_requests += 1
                return {"valid": False, "status_code": res.status_code, "error": res.text[:200]}
        except Exception as e:
            self.failed_requests += 1
            return {"valid": False, "error": str(e)}

    def generate_text(
        self,
        prompt: str,
        model: Optional[str] = None,
        system_instruction: Optional[str] = None,
        temperature: float = 0.7,
        max_tokens: Optional[int] = None,
    ) -> str:
        """Text generation via Chat Completions."""
        if not self.is_configured:
            raise RuntimeError("OpenAI provider is not configured (missing OPENAI_API_KEY).")

        chosen_model = model or self.default_model
        self.total_requests += 1

        messages = []
        if system_instruction:
            messages.append({"role": "system", "content": system_instruction})
        messages.append({"role": "user", "content": prompt})

        payload: Dict[str, Any] = {
            "model": chosen_model,
            "messages": messages,
            "temperature": temperature,
        }
        if max_tokens:
            payload["max_tokens"] = max_tokens

        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json"
        }

        res = requests.post(f"{self.base_url}/chat/completions", json=payload, headers=headers, timeout=45)
        if res.status_code != 200:
            self.failed_requests += 1
            raise RuntimeError(f"OpenAI API error ({res.status_code}): {res.text}")

        data = res.json()
        choices = data.get("choices", [])
        text = choices[0].get("message", {}).get("content", "") if choices else ""

        usage = data.get("usage", {})
        self.total_prompt_tokens += usage.get("prompt_tokens", len(prompt.split()) * 2)
        self.total_completion_tokens += usage.get("completion_tokens", len(text.split()) * 2)

        return text

    def chat(
        self,
        messages: List[Dict[str, str]],
        model: Optional[str] = None,
        system_instruction: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Multi-turn chat."""
        if not self.is_configured:
            raise RuntimeError("OpenAI provider is not configured.")

        chosen_model = model or self.default_model
        self.total_requests += 1

        formatted_messages = []
        if system_instruction:
            formatted_messages.append({"role": "system", "content": system_instruction})

        for m in messages:
            role = m.get("role", "user")
            if role in ("model", "assistant", "ai"):
                role = "assistant"
            formatted_messages.append({"role": role, "content": m.get("content", "")})

        payload = {
            "model": chosen_model,
            "messages": formatted_messages,
            "temperature": 0.7
        }
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json"
        }

        res = requests.post(f"{self.base_url}/chat/completions", json=payload, headers=headers, timeout=45)
        if res.status_code != 200:
            self.failed_requests += 1
            raise RuntimeError(f"OpenAI chat error ({res.status_code}): {res.text}")

        data = res.json()
        choices = data.get("choices", [])
        content = choices[0].get("message", {}).get("content", "") if choices else ""

        usage = data.get("usage", {})
        self.total_prompt_tokens += usage.get("prompt_tokens", 50)
        self.total_completion_tokens += usage.get("completion_tokens", 50)

        return {
            "role": "model",
            "content": content,
            "tool_calls": [],
            "model_used": chosen_model
        }

    def stream_chat(
        self,
        messages: List[Dict[str, str]],
        model: Optional[str] = None,
        system_instruction: Optional[str] = None,
    ) -> Generator[str, None, None]:
        """SSE Streaming response."""
        if not self.is_configured:
            yield "Error: OpenAI provider not configured."
            return

        chosen_model = model or self.default_model
        self.total_requests += 1

        formatted_messages = []
        if system_instruction:
            formatted_messages.append({"role": "system", "content": system_instruction})
        for m in messages:
            role = m.get("role", "user")
            if role in ("model", "assistant", "ai"):
                role = "assistant"
            formatted_messages.append({"role": role, "content": m.get("content", "")})

        payload = {
            "model": chosen_model,
            "messages": formatted_messages,
            "stream": True
        }
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json"
        }

        res = requests.post(f"{self.base_url}/chat/completions", json=payload, headers=headers, stream=True, timeout=60)
        if res.status_code != 200:
            self.failed_requests += 1
            yield f"Error: OpenAI returned status {res.status_code}"
            return

        for line in res.iter_lines():
            if not line:
                continue
            decoded = line.decode("utf-8").strip()
            if decoded.startswith("data:"):
                raw = decoded[5:].strip()
                if raw == "[DONE]":
                    break
                try:
                    chunk = json.loads(raw)
                    choices = chunk.get("choices", [])
                    if choices:
                        delta = choices[0].get("delta", {})
                        if "content" in delta and delta["content"]:
                            yield delta["content"]
                except Exception:
                    continue

    def analyze_image(
        self,
        image_data: Union[bytes, str],
        mime_type: str = "image/jpeg",
        prompt: str = "Describe this image in detail.",
        model: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Vision analysis using gpt-4o."""
        if not self.is_configured:
            raise RuntimeError("OpenAI provider not configured.")

        chosen_model = model or self.default_model
        self.total_requests += 1

        if isinstance(image_data, bytes):
            b64_image = base64.b64encode(image_data).decode("utf-8")
        else:
            b64_image = image_data

        image_url = f"data:{mime_type};base64,{b64_image}"

        messages = [
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": prompt},
                    {"type": "image_url", "image_url": {"url": image_url}}
                ]
            }
        ]

        payload = {
            "model": chosen_model,
            "messages": messages,
            "max_tokens": 1000
        }
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json"
        }

        res = requests.post(f"{self.base_url}/chat/completions", json=payload, headers=headers, timeout=60)
        if res.status_code != 200:
            self.failed_requests += 1
            raise RuntimeError(f"OpenAI vision error: {res.text}")

        data = res.json()
        analysis = data["choices"][0]["message"]["content"]
        return {"analysis": analysis, "model_used": chosen_model}

    def get_usage_metrics(self) -> Dict[str, Any]:
        return {
            "total_prompt_tokens": self.total_prompt_tokens,
            "total_completion_tokens": self.total_completion_tokens,
            "total_tokens": self.total_prompt_tokens + self.total_completion_tokens,
            "total_requests": self.total_requests,
            "failed_requests": self.failed_requests,
        }
