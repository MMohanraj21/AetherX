"""
AETHER X - Chat & Multi-Agent Conversation Routes
Handles standard and streaming chat, history retrieval, action approvals, and security checks.
"""

from typing import Any, Dict, List, Optional
from app.orchestration.graph import aether_orchestrator
from app.providers.provider_manager import provider_manager
from app.services.memory_service import memory_service
from app.services.security_service import security_service

# FastAPI router check
try:
    from fastapi import APIRouter, Header, HTTPException, Request
    from fastapi.responses import StreamingResponse
    router = APIRouter(prefix="/api/v1/chat", tags=["Chat"])
except ImportError:
    APIRouter = None
    router = None
    StreamingResponse = None


def process_chat_message(
    message: str,
    conversation_id: str = "default_conv",
    user_id: str = "default_user",
    explicit_provider: Optional[str] = None,
    client_ip: str = "127.0.0.1",
    auth_token: Optional[str] = None
) -> Dict[str, Any]:
    """Core chat controller executing auth, rate limits, and orchestrator."""
    # 1. Rate Limiting
    if not security_service.check_rate_limit(client_ip):
        return {"status": "error", "error": "Rate limit exceeded. Please wait a moment."}

    # 2. Authentication
    if auth_token and not security_service.authenticate_token(auth_token):
        return {"status": "error", "error": "Invalid or expired authorization token."}

    # 3. Validation
    if not message or not message.strip():
        return {"status": "error", "error": "Message content cannot be empty."}

    # 4. Multi-agent Graph execution
    result = aether_orchestrator.run(
        user_message=message,
        conversation_id=conversation_id,
        user_id=user_id,
        explicit_provider=explicit_provider
    )

    return {
        "status": "success",
        "conversation_id": conversation_id,
        "intent": result.get("intent"),
        "response": result.get("response"),
        "provider": result.get("provider"),
        "requires_approval": result.get("requires_approval", False),
        "agent_details": result.get("agent_details", {})
    }


def stream_chat_messages(
    message: str,
    conversation_id: str = "default_conv",
    explicit_provider: Optional[str] = None
):
    """SSE Generator for incremental tokens."""
    history = memory_service.get_conversation_history(conversation_id, limit=6)
    messages = list(history)
    messages.append({"role": "user", "content": message})

    stream = provider_manager.stream_chat(
        messages=messages,
        task_type="simple_conversation",
        explicit_provider=explicit_provider
    )

    full_response = ""
    for token in stream:
        full_response += token
        yield f"data: {token}\n\n"

    # Save to history once completed
    memory_service.add_turn(conversation_id, "user", message)
    memory_service.add_turn(conversation_id, "assistant", full_response)
    yield "data: [DONE]\n\n"


def approve_action_handler(action_id: str, approved: bool) -> Dict[str, Any]:
    """Resolve a pending sensitive action that required user confirmation."""
    resolved = security_service.resolve_pending_approval(action_id, approved)
    if not resolved:
        return {"status": "error", "error": "Action ID not found or already processed."}

    status_str = "approved and executed" if approved else "rejected by user"
    return {
        "status": "success",
        "action_id": action_id,
        "tool": resolved.get("tool"),
        "approved": approved,
        "message": f"Action was {status_str}."
    }


if router:
    @router.post("")
    async def chat_endpoint(
        request: Request,
        payload: Dict[str, Any],
        authorization: Optional[str] = Header(None)
    ):
        client_ip = request.client.host if request.client else "127.0.0.1"
        res = process_chat_message(
            message=payload.get("message", ""),
            conversation_id=payload.get("conversation_id", "default_conv"),
            user_id=payload.get("user_id", "default_user"),
            explicit_provider=payload.get("provider"),
            client_ip=client_ip,
            auth_token=authorization
        )
        if res.get("status") == "error":
            raise HTTPException(status_code=400, detail=res["error"])
        return res

    @router.post("/stream")
    async def chat_stream_endpoint(payload: Dict[str, Any]):
        msg = payload.get("message", "")
        cid = payload.get("conversation_id", "default_conv")
        prov = payload.get("provider")
        return StreamingResponse(
            stream_chat_messages(msg, cid, prov),
            media_type="text/event-stream"
        )

    @router.get("/history/{conversation_id}")
    async def get_history(conversation_id: str):
        turns = memory_service.get_conversation_history(conversation_id, limit=30)
        return {"conversation_id": conversation_id, "history": turns}

    @router.post("/approve")
    async def approve_action(payload: Dict[str, Any]):
        aid = payload.get("action_id", "")
        appr = payload.get("approved", False)
        return approve_action_handler(aid, appr)
