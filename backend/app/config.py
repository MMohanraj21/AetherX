"""
AETHER X - Backend Configuration Module
Loads environment variables securely, validates startup credentials,
and provides sanitized provider status without leaking secrets.
"""

import os
from pathlib import Path
from typing import Dict, Any, Optional

# Load .env file manually or via dotenv if available
def _load_env_file():
    env_paths = [
        Path(__file__).resolve().parent.parent / ".env",
        Path.cwd() / ".env",
        Path.cwd() / "backend" / ".env"
    ]
    for p in env_paths:
        if p.exists() and p.is_file():
            with open(p, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if not line or line.startswith("#") or "=" not in line:
                        continue
                    key, val = line.split("=", 1)
                    key = key.strip()
                    val = val.strip().strip("'\"")
                    if key not in os.environ:
                        os.environ[key] = val
            break

_load_env_file()


class Settings:
    """Application settings loaded from environment variables."""

    # Gemini API Configuration
    GEMINI_API_KEY: str = os.getenv("GEMINI_API_KEY", "").strip()
    GEMINI_MODEL: str = os.getenv("GEMINI_MODEL", "gemini-3.8-flash").strip()
    GEMINI_REASONING_MODEL: str = os.getenv("GEMINI_REASONING_MODEL", "gemini-3.1-pro-preview").strip()
    GEMINI_FALLBACK_MODEL: str = os.getenv("GEMINI_FALLBACK_MODEL", "gemini-3.5-flash-lite").strip()

    # OpenAI API Configuration (Secondary / Fallback)
    OPENAI_API_KEY: str = os.getenv("OPENAI_API_KEY", "").strip()
    OPENAI_MODEL: str = os.getenv("OPENAI_MODEL", "gpt-4o").strip()

    # AI Provider routing: "auto", "gemini", "openai"
    AI_PROVIDER: str = os.getenv("AI_PROVIDER", "auto").strip().lower()

    # Security & Auth
    AETHER_AUTH_SECRET: str = os.getenv("AETHER_AUTH_SECRET", "aether-default-dev-secret").strip()
    RATE_LIMIT_PER_MINUTE: int = int(os.getenv("RATE_LIMIT_PER_MINUTE", "60"))

    # Database
    DATABASE_URL: str = os.getenv("DATABASE_URL", "postgresql://aether:aetherpass@localhost:5432/aether_x").strip()

    # Server Network
    HOST: str = os.getenv("HOST", "0.0.0.0").strip()
    PORT: int = int(os.getenv("PORT", "8000"))
    DEBUG: bool = os.getenv("DEBUG", "false").strip().lower() in ("true", "1", "yes")

    @classmethod
    def validate_credentials(cls) -> Dict[str, Any]:
        """
        Validate credentials during startup.
        Ensures required API keys are configured before starting agents.
        """
        errors = []
        warnings = []

        has_gemini = bool(cls.GEMINI_API_KEY)
        has_openai = bool(cls.OPENAI_API_KEY)

        if not has_gemini and not has_openai:
            errors.append("No AI provider credentials found. Set GEMINI_API_KEY or OPENAI_API_KEY in .env")

        if cls.AI_PROVIDER == "gemini" and not has_gemini:
            errors.append("AI_PROVIDER is set to 'gemini' but GEMINI_API_KEY is not configured.")

        if cls.AI_PROVIDER == "openai" and not has_openai:
            errors.append("AI_PROVIDER is set to 'openai' but OPENAI_API_KEY is not configured.")

        if cls.AI_PROVIDER == "auto":
            if not has_gemini:
                warnings.append("AI_PROVIDER is 'auto', but GEMINI_API_KEY is missing. Falling back to OpenAI if available.")
            if not has_openai:
                warnings.append("OPENAI_API_KEY is not configured. Fallback to OpenAI is disabled.")

        is_valid = len(errors) == 0

        return {
            "valid": is_valid,
            "errors": errors,
            "warnings": warnings,
            "has_gemini": has_gemini,
            "has_openai": has_openai,
            "active_provider": cls.AI_PROVIDER,
        }

    @classmethod
    def get_public_provider_status(cls) -> Dict[str, Any]:
        """
        Returns a sanitized configuration summary for health checks.
        NEVER leaks API keys or secrets.
        """
        validation = cls.validate_credentials()
        return {
            "status": "healthy" if validation["valid"] else "degraded",
            "active_provider": cls.AI_PROVIDER,
            "providers": {
                "gemini": {
                    "configured": bool(cls.GEMINI_API_KEY),
                    "default_model": cls.GEMINI_MODEL,
                    "reasoning_model": cls.GEMINI_REASONING_MODEL,
                    "fallback_model": cls.GEMINI_FALLBACK_MODEL,
                    "status": "ready" if cls.GEMINI_API_KEY else "unconfigured"
                },
                "openai": {
                    "configured": bool(cls.OPENAI_API_KEY),
                    "default_model": cls.OPENAI_MODEL,
                    "status": "ready" if cls.OPENAI_API_KEY else "unconfigured"
                }
            },
            "rate_limit_per_minute": cls.RATE_LIMIT_PER_MINUTE,
            "warnings": validation["warnings"]
        }


settings = Settings()
