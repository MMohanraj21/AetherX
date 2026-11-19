"""
AETHER X - Main Application Entrypoint
FastAPI server connecting Android clients with Gemini & OpenAI hybrid AI architecture.
Includes CORS, health checks, startup credential validation, and standalone fallback server.
"""

import json
import logging
import sys
from typing import Dict, Any

from app.config import settings
from app.routes.chat import router as chat_router, process_chat_message
from app.routes.providers import router as providers_router, health_check_handler
from app.routes.vision import router as vision_router, analyze_image_handler

logging.basicConfig(
    level=logging.INFO if not settings.DEBUG else logging.DEBUG,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger("aether.main")

# FastAPI Application Setup
app = None
try:
    from fastapi import FastAPI
    from fastapi.middleware.cors import CORSMiddleware

    app = FastAPI(
        title="AETHER X - Hybrid AI Assistant Backend",
        description="Google Gemini & OpenAI multi-agent intelligence server for Android",
        version="1.0.0"
    )

    # Enable CORS for Android emulators, devices, and dev tools
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    @app.on_event("startup")
    async def startup_event():
        logger.info("Initializing AETHER X Backend Services...")
        cred_status = settings.validate_credentials()
        if cred_status["valid"]:
            logger.info(f"✓ AI Provider credentials validated. Active preference: {cred_status['active_provider']}")
        else:
            for err in cred_status["errors"]:
                logger.error(f"✗ Startup Credential Warning: {err}")

    @app.get("/")
    def root():
        return {
            "name": "AETHER X Backend",
            "version": "1.0.0",
            "documentation": "/docs",
            "health": "/health"
        }

    @app.get("/health")
    def health():
        return health_check_handler()

    # Mount Route Modules
    if chat_router:
        app.include_router(chat_router)
    if vision_router:
        app.include_router(vision_router)
    if providers_router:
        app.include_router(providers_router)

except ImportError:
    logger.info("FastAPI not installed in current python environment. Standalone HTTP runner enabled.")


def run_standalone_http_server(host: str = "0.0.0.0", port: int = 8000):
    """
    Zero-dependency built-in HTTP server runner for testing and Termux environments.
    Handles /health, /api/v1/chat, /api/v1/providers/health, and /api/v1/vision/analyze.
    """
    from http.server import HTTPServer, BaseHTTPRequestHandler
    from urllib.parse import urlparse

    class AetherHttpHandler(BaseHTTPRequestHandler):
        def _send_json(self, status_code: int, data: Dict[str, Any]):
            body = json.dumps(data, indent=2).encode("utf-8")
            self.send_response(status_code)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Access-Control-Allow-Headers", "*")
            self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            self.end_headers()
            self.wfile.write(body)

        def do_OPTIONS(self):
            self.send_response(200)
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Access-Control-Allow-Headers", "*")
            self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            self.end_headers()

        def do_GET(self):
            parsed = urlparse(self.path)
            if parsed.path in ("/", "/health", "/api/v1/providers/health"):
                self._send_json(200, health_check_handler())
            elif parsed.path == "/api/v1/providers":
                self._send_json(200, settings.get_public_provider_status())
            else:
                self._send_json(404, {"error": "Not Found", "path": parsed.path})

        def do_POST(self):
            parsed = urlparse(self.path)
            content_length = int(self.headers.get("Content-Length", 0))
            post_data = self.rfile.read(content_length)

            payload = {}
            if post_data:
                try:
                    payload = json.loads(post_data.decode("utf-8"))
                except Exception:
                    pass

            auth_token = self.headers.get("Authorization")
            client_ip = self.client_address[0] if self.client_address else "127.0.0.1"

            if parsed.path == "/api/v1/chat":
                res = process_chat_message(
                    message=payload.get("message", ""),
                    conversation_id=payload.get("conversation_id", "default_conv"),
                    user_id=payload.get("user_id", "default_user"),
                    explicit_provider=payload.get("provider"),
                    client_ip=client_ip,
                    auth_token=auth_token
                )
                code = 200 if res.get("status") == "success" else 400
                self._send_json(code, res)

            elif parsed.path == "/api/v1/vision/analyze":
                res = analyze_image_handler(
                    image_bytes_or_b64=payload.get("image_base64", ""),
                    mime_type=payload.get("mime_type", "image/jpeg"),
                    prompt=payload.get("prompt", "Analyze this image."),
                    mode=payload.get("mode", "general"),
                    explicit_provider=payload.get("provider")
                )
                code = 200 if res.get("status") != "error" else 500
                self._send_json(code, res)
            else:
                self._send_json(404, {"error": "Endpoint not found"})

        def log_message(self, format, *args):
            logger.info("%s - - [%s] %s" % (self.client_address[0], self.log_date_time_string(), format % args))

    server_address = (host, port)
    httpd = HTTPServer(server_address, AetherHttpHandler)
    logger.info(f"🚀 AETHER X Standalone Server listening on http://{host}:{port}")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        logger.info("Server stopped.")
        httpd.server_close()


if __name__ == "__main__":
    validation = settings.validate_credentials()
    logger.info(f"Validation summary: {validation}")

    if app is not None:
        try:
            import uvicorn
            uvicorn.run("app.main:app", host=settings.HOST, port=settings.PORT, reload=settings.DEBUG)
        except ImportError:
            run_standalone_http_server(host=settings.HOST, port=settings.PORT)
    else:
        run_standalone_http_server(host=settings.HOST, port=settings.PORT)
