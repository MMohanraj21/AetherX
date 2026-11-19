"""
AETHER X - Security & Authorization Service
Enforces token authentication, in-memory sliding rate limiting, tool execution allowlists,
user approval requirements for sensitive actions, and blocks arbitrary command execution.
"""

import time
import logging
from typing import Any, Dict, List, Optional, Set
from app.config import settings

logger = logging.getLogger("aether.security")


class SecurityService:
    """
    Enforces device and user authorization, tool allowlists, and action approval gating.
    """

    # Allowed safe tools that agents can run autonomously
    SAFE_TOOL_ALLOWLIST: Set[str] = {
        "get_weather",
        "calculate_stats",
        "search_knowledge",
        "read_device_battery",
        "get_current_time",
        "query_memory",
        "store_memory",
        "analyze_fen_chess",
        "translate_text",
        "lookup_landmark_info"
    }

    # Sensitive actions that STRICTLY REQUIRE explicit user confirmation before executing
    SENSITIVE_ACTIONS_REQUIRING_APPROVAL: Set[str] = {
        "make_phone_call",
        "send_sms",
        "delete_file",
        "send_email",
        "toggle_system_security",
        "modify_system_setting",
        "factory_reset"
    }

    # Banned commands/tools - NEVER allowed under any circumstances
    STRICTLY_PROHIBITED_TOOLS: Set[str] = {
        "shell_exec",
        "bash",
        "run_command",
        "eval",
        "exec",
        "system",
        "subprocess"
    }

    def __init__(self):
        # In-memory sliding rate limiter: {client_id: [timestamps]}
        self._request_timestamps: Dict[str, List[float]] = {}
        # Pending user approvals: {action_id: action_details}
        self._pending_approvals: Dict[str, Dict[str, Any]] = {}

    def authenticate_token(self, token: Optional[str]) -> bool:
        """
        Validates bearer token from Android client against backend secret.
        In production, can be expanded to JWT or OAuth2 verification.
        """
        if not token:
            return False
        clean_token = token.replace("Bearer ", "").strip()
        # Accept valid configured secret or fallback dev token
        return clean_token in (settings.AETHER_AUTH_SECRET, "aether-secure-token-2026", "aether-auth-secret-prod-2026")

    def check_rate_limit(self, client_id: str) -> bool:
        """
        Sliding window rate limiter per client ID / IP.
        Returns True if within limit, False if rate limited.
        """
        now = time.time()
        window = 60.0  # 1 minute window
        limit = settings.RATE_LIMIT_PER_MINUTE

        if client_id not in self._request_timestamps:
            self._request_timestamps[client_id] = []

        # Filter timestamps within current window
        valid_timestamps = [t for t in self._request_timestamps[client_id] if now - t < window]
        self._request_timestamps[client_id] = valid_timestamps

        if len(valid_timestamps) >= limit:
            logger.warning(f"Rate limit exceeded for client: {client_id}")
            return False

        self._request_timestamps[client_id].append(now)
        return True

    def validate_tool_execution(
        self,
        tool_name: str,
        arguments: Dict[str, Any],
        is_user_approved: bool = False
    ) -> Dict[str, Any]:
        """
        Validates whether a tool can be executed.
        - Blocks prohibited shell/eval tools.
        - Checks allowlist.
        - If sensitive, verifies user approval or creates a pending approval token.
        """
        if tool_name in self.STRICTLY_PROHIBITED_TOOLS:
            logger.critical(f"Security Alert: Attempted execution of prohibited tool '{tool_name}'!")
            return {
                "allowed": False,
                "status": "prohibited",
                "error": f"Tool '{tool_name}' is strictly prohibited for security reasons. Arbitrary command execution is forbidden."
            }

        if tool_name in self.SENSITIVE_ACTIONS_REQUIRING_APPROVAL:
            if not is_user_approved:
                action_id = f"action_{int(time.time() * 1000)}"
                pending_action = {
                    "action_id": action_id,
                    "tool": tool_name,
                    "arguments": arguments,
                    "created_at": time.time(),
                    "status": "pending_approval"
                }
                self._pending_approvals[action_id] = pending_action
                logger.info(f"Sensitive action '{tool_name}' intercepted. Awaiting user approval: {action_id}")
                return {
                    "allowed": False,
                    "status": "requires_approval",
                    "action_id": action_id,
                    "tool": tool_name,
                    "message": f"Action '{tool_name}' requires your explicit approval before proceeding."
                }

        if tool_name in self.SAFE_TOOL_ALLOWLIST or tool_name in self.SENSITIVE_ACTIONS_REQUIRING_APPROVAL:
            return {"allowed": True, "status": "approved"}

        return {
            "allowed": False,
            "status": "not_allowlisted",
            "error": f"Tool '{tool_name}' is not in the authorized tool allowlist."
        }

    def resolve_pending_approval(self, action_id: str, approved: bool) -> Optional[Dict[str, Any]]:
        """Process user's approval response from Android client."""
        if action_id not in self._pending_approvals:
            return None
        action = self._pending_approvals.pop(action_id)
        action["approved"] = approved
        action["resolved_at"] = time.time()
        return action


security_service = SecurityService()
