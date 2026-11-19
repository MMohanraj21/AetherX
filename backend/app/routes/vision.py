"""
AETHER X - Vision & Multimodal Image Analysis Routes
Handles camera image uploads, base64 visual analysis, fashion recommendations,
landmark discovery, signboard translation, and PDF document processing.
"""

import base64
from typing import Any, Dict, Optional
from app.agents.vision_agent import vision_agent
from app.services.security_service import security_service

# FastAPI router check
try:
    from fastapi import APIRouter, File, Form, HTTPException, Request, UploadFile
    router = APIRouter(prefix="/api/v1/vision", tags=["Vision"])
except ImportError:
    APIRouter = None
    router = None


def analyze_image_handler(
    image_bytes_or_b64: Any,
    mime_type: str = "image/jpeg",
    prompt: str = "Analyze this image.",
    mode: str = "general",
    explicit_provider: Optional[str] = None
) -> Dict[str, Any]:
    """Process visual analysis with Gemini multimodal model."""
    try:
        return vision_agent.process(
            image_data=image_bytes_or_b64,
            mime_type=mime_type,
            prompt=prompt,
            mode=mode,
            explicit_provider=explicit_provider
        )
    except Exception as e:
        return {"status": "error", "error": str(e)}


def analyze_document_handler(
    doc_bytes: bytes,
    mime_type: str = "application/pdf",
    prompt: str = "Summarize this document."
) -> Dict[str, Any]:
    """Process document analysis."""
    try:
        return vision_agent.process_document(
            doc_data=doc_bytes,
            mime_type=mime_type,
            prompt=prompt
        )
    except Exception as e:
        return {"status": "error", "error": str(e)}


if router:
    @router.post("/analyze")
    async def analyze_image_endpoint(
        request: Request,
        payload: Optional[Dict[str, Any]] = None,
        file: Optional[UploadFile] = File(None)
    ):
        # Support both JSON with base64 and Multipart form upload
        if file:
            content = await file.read()
            mime_type = file.content_type or "image/jpeg"
            prompt = "Describe this image in detail."
            mode = "general"
        elif payload:
            b64_data = payload.get("image_base64", "")
            if not b64_data:
                raise HTTPException(status_code=400, detail="Missing 'image_base64' in payload.")
            content = b64_data
            mime_type = payload.get("mime_type", "image/jpeg")
            prompt = payload.get("prompt", "Analyze this image.")
            mode = payload.get("mode", "general")
        else:
            raise HTTPException(status_code=400, detail="Either image file or JSON payload required.")

        res = analyze_image_handler(content, mime_type, prompt, mode)
        if res.get("status") == "error":
            raise HTTPException(status_code=500, detail=res["error"])
        return res

    @router.post("/document")
    async def analyze_document_endpoint(
        file: UploadFile = File(...),
        prompt: str = Form("Summarize key findings of this document.")
    ):
        content = await file.read()
        mime_type = file.content_type or "application/pdf"
        res = analyze_document_handler(content, mime_type, prompt)
        if res.get("status") == "error":
            raise HTTPException(status_code=500, detail=res["error"])
        return res
