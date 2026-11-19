"""
AETHER X - Centralized AI Provider Manager
Orchestrates multi-provider routing (Gemini, OpenAI, Auto), intelligent task-based model selection,
automatic fallback, and token usage tracking.
Strictly respects explicit user provider selections: never silently switches if user forced a provider.
"""

import logging
from typing import Any, Dict, Generator, List, Optional, Union

from app.config import settings
from app.providers.gemini_provider import GeminiProvider
from app.providers.openai_provider import OpenAIProvider

logger = logging.getLogger("aether.provider_manager")


class ProviderManager:
    """
    Manages and routes requests between Gemini and OpenAI providers.
    """

    def __init__(self):
        # Initialize providers
        self.gemini = GeminiProvider(
            api_key=settings.GEMINI_API_KEY,
            default_model=settings.GEMINI_MODEL,
            reasoning_model=settings.GEMINI_REASONING_MODEL,
            fallback_model=settings.GEMINI_FALLBACK_MODEL
        )
        self.openai = OpenAIProvider(
            api_key=settings.OPENAI_API_KEY,
            default_model=settings.OPENAI_MODEL
        )
        self.active_provider_preference: str = settings.AI_PROVIDER.lower()

    def set_user_provider_preference(self, provider: str):
        """Allows user or client to set routing preference: 'auto', 'gemini', 'openai'."""
        provider = provider.lower().strip()
        if provider not in ("auto", "gemini", "openai"):
            raise ValueError(f"Invalid provider: '{provider}'. Must be 'auto', 'gemini', or 'openai'.")
        self.active_provider_preference = provider
        logger.info(f"User provider preference set to: {self.active_provider_preference}")

    def get_effective_provider_for_task(
        self,
        task_type: str = "simple_conversation",
        user_explicit_provider: Optional[str] = None
    ) -> str:
        """
        Determines which provider should handle the request.
        Routing rules:
        - If user explicitly requested 'gemini' or 'openai', strictly use that provider.
        - If 'auto':
            * Simple conversations -> Gemini Flash
            * Complex reasoning -> Configured reasoning model (Gemini Pro or OpenAI)
            * Image understanding -> Gemini multimodal model
            * Data analysis -> Gemini or OpenAI analytics
        """
        chosen_preference = (user_explicit_provider or self.active_provider_preference).lower()

        if chosen_preference in ("gemini", "openai"):
            return chosen_preference

        # Auto mode routing:
        if task_type == "image_understanding":
            return "gemini" if self.gemini.api_key else "openai"
        elif task_type == "complex_reasoning":
            return "gemini" if self.gemini.api_key else "openai"
        elif task_type == "data_analysis":
            return "gemini" if self.gemini.api_key else "openai"
        else:
            # Simple conversation default
            return "gemini" if self.gemini.api_key else "openai"

    def generate_text(
        self,
        prompt: str,
        task_type: str = "simple_conversation",
        explicit_provider: Optional[str] = None,
        model: Optional[str] = None,
        system_instruction: Optional[str] = None,
    ) -> Dict[str, Any]:
        """
        Generate text with intelligent routing and safe fallback.
        """
        target_provider = self.get_effective_provider_for_task(task_type, explicit_provider)
        is_explicit = explicit_provider is not None or self.active_provider_preference in ("gemini", "openai")

        if target_provider == "gemini":
            try:
                # Select appropriate Gemini model based on task
                chosen_model = model
                if not chosen_model:
                    if task_type == "complex_reasoning":
                        chosen_model = self.gemini.reasoning_model
                    else:
                        chosen_model = self.gemini.default_model

                result = self.gemini.generate_text(
                    prompt=prompt,
                    model=chosen_model,
                    system_instruction=system_instruction
                )
                return {
                    "text": result,
                    "provider": "gemini",
                    "model": chosen_model,
                    "fallback_triggered": False
                }
            except Exception as e:
                logger.error(f"Gemini execution failed: {e}")
                # RULE: Never silently switch providers when user selected a specific provider!
                if is_explicit:
                    raise RuntimeError(f"Requested provider 'gemini' failed: {e}")

                # Auto fallback to OpenAI if configured
                if self.openai.is_configured:
                    logger.warning("Auto mode: Falling back to OpenAI after Gemini error.")
                    result = self.openai.generate_text(
                        prompt=prompt,
                        model=self.openai.default_model,
                        system_instruction=system_instruction
                    )
                    return {
                        "text": result,
                        "provider": "openai",
                        "model": self.openai.default_model,
                        "fallback_triggered": True,
                        "primary_error": str(e)
                    }
                raise

        elif target_provider == "openai":
            if not self.openai.is_configured:
                raise RuntimeError("OpenAI was selected but is not configured.")
            try:
                result = self.openai.generate_text(
                    prompt=prompt,
                    model=model or self.openai.default_model,
                    system_instruction=system_instruction
                )
                return {
                    "text": result,
                    "provider": "openai",
                    "model": model or self.openai.default_model,
                    "fallback_triggered": False
                }
            except Exception as e:
                logger.error(f"OpenAI execution failed: {e}")
                if is_explicit:
                    raise RuntimeError(f"Requested provider 'openai' failed: {e}")
                if self.gemini.api_key:
                    logger.warning("Auto mode: Falling back to Gemini after OpenAI error.")
                    result = self.gemini.generate_text(
                        prompt=prompt,
                        model=self.gemini.default_model,
                        system_instruction=system_instruction
                    )
                    return {
                        "text": result,
                        "provider": "gemini",
                        "model": self.gemini.default_model,
                        "fallback_triggered": True,
                        "primary_error": str(e)
                    }
                raise
        else:
            raise ValueError(f"Unknown target provider: {target_provider}")

    def chat(
        self,
        messages: List[Dict[str, str]],
        task_type: str = "simple_conversation",
        explicit_provider: Optional[str] = None,
        model: Optional[str] = None,
        system_instruction: Optional[str] = None,
        tools: Optional[List[Dict[str, Any]]] = None,
    ) -> Dict[str, Any]:
        """Multi-turn chat routing."""
        target_provider = self.get_effective_provider_for_task(task_type, explicit_provider)
        is_explicit = explicit_provider is not None or self.active_provider_preference in ("gemini", "openai")

        if target_provider == "gemini":
            try:
                chosen_model = model or (self.gemini.reasoning_model if task_type == "complex_reasoning" else self.gemini.default_model)
                res = self.gemini.chat(
                    messages=messages,
                    model=chosen_model,
                    system_instruction=system_instruction,
                    tools=tools
                )
                res["provider"] = "gemini"
                res["fallback_triggered"] = False
                return res
            except Exception as e:
                if is_explicit:
                    raise RuntimeError(f"Requested provider 'gemini' failed: {e}")
                if self.openai.is_configured:
                    logger.warning("Auto mode fallback to OpenAI for chat.")
                    res = self.openai.chat(
                        messages=messages,
                        model=self.openai.default_model,
                        system_instruction=system_instruction
                    )
                    res["provider"] = "openai"
                    res["fallback_triggered"] = True
                    return res
                raise

        elif target_provider == "openai":
            if not self.openai.is_configured:
                raise RuntimeError("OpenAI provider is not configured.")
            res = self.openai.chat(
                messages=messages,
                model=model or self.openai.default_model,
                system_instruction=system_instruction
            )
            res["provider"] = "openai"
            res["fallback_triggered"] = False
            return res
        else:
            raise ValueError(f"Unknown provider: {target_provider}")

    def stream_chat(
        self,
        messages: List[Dict[str, str]],
        task_type: str = "simple_conversation",
        explicit_provider: Optional[str] = None,
        model: Optional[str] = None,
        system_instruction: Optional[str] = None,
    ) -> Generator[str, None, None]:
        """Streaming chat routing."""
        target_provider = self.get_effective_provider_for_task(task_type, explicit_provider)
        if target_provider == "gemini":
            chosen_model = model or (self.gemini.reasoning_model if task_type == "complex_reasoning" else self.gemini.default_model)
            return self.gemini.stream_chat(messages, model=chosen_model, system_instruction=system_instruction)
        else:
            return self.openai.stream_chat(messages, model=model or self.openai.default_model, system_instruction=system_instruction)

    def analyze_image(
        self,
        image_data: Union[bytes, str],
        mime_type: str = "image/jpeg",
        prompt: str = "Describe this image.",
        mode: str = "general",
        explicit_provider: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Image and multimodal vision analysis."""
        target_provider = self.get_effective_provider_for_task("image_understanding", explicit_provider)
        is_explicit = explicit_provider is not None or self.active_provider_preference in ("gemini", "openai")

        if target_provider == "gemini":
            try:
                res = self.gemini.analyze_image(
                    image_data=image_data,
                    mime_type=mime_type,
                    prompt=prompt,
                    mode=mode
                )
                res["provider"] = "gemini"
                return res
            except Exception as e:
                if is_explicit:
                    raise RuntimeError(f"Requested provider 'gemini' vision failed: {e}")
                if self.openai.is_configured:
                    logger.warning("Auto mode: Falling back to OpenAI for image analysis.")
                    res = self.openai.analyze_image(
                        image_data=image_data,
                        mime_type=mime_type,
                        prompt=prompt
                    )
                    res["provider"] = "openai"
                    res["fallback_triggered"] = True
                    return res
                raise
        else:
            return self.openai.analyze_image(image_data, mime_type, prompt)

    def get_system_telemetry(self) -> Dict[str, Any]:
        """Aggregated telemetry from all configured providers."""
        return {
            "active_preference": self.active_provider_preference,
            "gemini_metrics": self.gemini.get_usage_metrics(),
            "openai_metrics": self.openai.get_usage_metrics(),
        }


# Global singleton instance
provider_manager = ProviderManager()
