"""
AETHER X - Vision Agent
Orchestrates multimodal computer vision workflows using Gemini 3.8 Flash.
Provides deep visual analysis for:
- Fashion styling & recommendations
- Clothing item breakdown & combinations
- Object recognition & categorization
- Travel landmark recognition & historical context
- Signboard reading & multilingual translation
- Visual question answering (VQA)
"""

from typing import Any, Dict, Optional, Union
from app.providers.provider_manager import provider_manager


class VisionAgent:
    """Specialized agent for multimodal image and document understanding."""

    def __init__(self):
        self.name = "vision_agent"

    def process(
        self,
        image_data: Union[bytes, str],
        mime_type: str = "image/jpeg",
        prompt: str = "Analyze this image.",
        mode: str = "general",
        explicit_provider: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Executes vision task with Gemini multimodal model.
        """
        # Ensure mode is standard
        mode = mode.lower()
        valid_modes = {"fashion", "clothing", "object", "landmark", "signboard", "vqa", "general"}
        if mode not in valid_modes:
            mode = "general"

        result = provider_manager.analyze_image(
            image_data=image_data,
            mime_type=mime_type,
            prompt=prompt,
            mode=mode,
            explicit_provider=explicit_provider
        )

        return {
            "agent": self.name,
            "mode": mode,
            "analysis": result.get("analysis", ""),
            "provider_used": result.get("provider", "gemini"),
            "model_used": result.get("model_used", ""),
            "fallback_triggered": result.get("fallback_triggered", False)
        }

    def process_document(
        self,
        doc_data: Union[bytes, str],
        mime_type: str = "application/pdf",
        prompt: str = "Extract and summarize key points from this document."
    ) -> Dict[str, Any]:
        """Process PDF and documents."""
        result = provider_manager.gemini.analyze_document(
            doc_bytes=doc_data,
            mime_type=mime_type,
            prompt=prompt
        )
        return {
            "agent": self.name,
            "task": "document_analysis",
            "analysis": result.get("analysis", ""),
            "mime_type": mime_type,
            "model_used": result.get("model_used", "")
        }


vision_agent = VisionAgent()
