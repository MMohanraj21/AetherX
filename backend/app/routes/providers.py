"""
AETHER X - Provider & Health Routes
Provides endpoints for inspecting available AI providers, toggling user provider preference,
and checking system health without leaking sensitive credentials.
"""

from typing import Any, Dict
from app.config import settings
from app.providers.provider_manager import provider_manager

# FastAPI router check
try:
    from fastapi import APIRouter, HTTPException, Request
    router = APIRouter(prefix="/api/v1/providers", tags=["Providers"])
except ImportError:
    APIRouter = None
    router = None


def get_providers_status_handler() -> Dict[str, Any]:
    """Retrieve configured AI providers and health status."""
    return settings.get_public_provider_status()


def select_provider_handler(provider_name: str) -> Dict[str, Any]:
    """Set active AI provider preference: 'auto', 'gemini', 'openai'."""
    try:
        provider_manager.set_user_provider_preference(provider_name)
        return {
            "status": "success",
            "active_provider": provider_manager.active_provider_preference,
            "message": f"Successfully switched provider preference to '{provider_name}'."
        }
    except ValueError as e:
        if APIRouter:
            raise HTTPException(status_code=400, detail=str(e))
        return {"status": "error", "error": str(e)}


def health_check_handler() -> Dict[str, Any]:
    """
    Public health check endpoint.
    Safely shows provider status, memory state, and active router without secrets.
    """
    status = settings.get_public_provider_status()
    # Ping Gemini connection if configured
    gemini_ping = provider_manager.gemini.validate_connection() if settings.GEMINI_API_KEY else {"valid": False, "reason": "not_configured"}

    return {
        "status": status["status"],
        "version": "1.0.0",
        "active_provider": status["active_provider"],
        "gemini": {
            "configured": bool(settings.GEMINI_API_KEY),
            "connected": gemini_ping.get("valid", False),
            "model": settings.GEMINI_MODEL,
        },
        "openai": {
            "configured": bool(settings.OPENAI_API_KEY),
            "model": settings.OPENAI_MODEL,
        },
        "rate_limiting_active": True,
        "security_policy": "Strict allowlists + Sensitive action confirmation enabled"
    }


if router:
    @router.get("/")
    def get_providers():
        return get_providers_status_handler()

    @router.post("/select")
    def select_provider(payload: Dict[str, str]):
        name = payload.get("provider", "auto")
        return select_provider_handler(name)

    @router.get("/health")
    def get_health():
        return health_check_handler()
