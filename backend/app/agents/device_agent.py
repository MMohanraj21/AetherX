"""
AETHER X - Device Control Agent
Translates natural language into structured Android device actions (volume, brightness, alarms, apps).
Enforces security rules: sensitive actions (phone calls, SMS, deletion) require explicit user approval.
Arbitrary shell command execution is strictly forbidden.
"""

from typing import Any, Dict, List, Optional
from app.providers.provider_manager import provider_manager
from app.services.security_service import security_service


class DeviceControlAgent:
    """Agent responsible for managing Android device actions safely."""

    SYSTEM_INSTRUCTION = (
        "You are the AETHER Device Control Agent. You interpret user requests to control Android device features: "
        "adjusting volume, brightness, setting alarms, toggling Wi-Fi/Bluetooth, launching apps, and reading battery state. "
        "When a user asks to perform an action, specify the action name and parameters in clean JSON format: "
        "{\"action\": \"set_volume\", \"parameters\": {\"level\": 80}, \"requires_confirmation\": false} "
        "SENSITIVE ACTIONS: 'make_phone_call', 'send_sms', 'delete_file', 'modify_system_setting' REQUIRE confirmation. "
        "NEVER execute arbitrary shell scripts, terminal commands, or unverified code."
    )

    def __init__(self):
        self.name = "device_agent"

    def process(
        self,
        command_text: str,
        is_user_approved: bool = False,
        explicit_provider: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Parses device command and validates safety permissions.
        """
        prompt = (
            f"User device request: '{command_text}'. "
            "Identify the intended device tool action and parameters. "
            "Output JSON with keys: 'tool', 'arguments', 'summary'."
        )

        structured_result = provider_manager.gemini.generate_structured(
            prompt=prompt,
            system_instruction=self.SYSTEM_INSTRUCTION
        )

        tool_name = structured_result.get("tool", "unknown_action")
        arguments = structured_result.get("arguments", {})
        summary = structured_result.get("summary", "Attempting device action")

        # Security check: Validate tool against safety allowlist and approval rules
        sec_check = security_service.validate_tool_execution(
            tool_name=tool_name,
            arguments=arguments,
            is_user_approved=is_user_approved
        )

        if not sec_check.get("allowed", False):
            if sec_check.get("status") == "requires_approval":
                return {
                    "agent": self.name,
                    "status": "requires_approval",
                    "action_id": sec_check.get("action_id"),
                    "tool": tool_name,
                    "arguments": arguments,
                    "message": f"Sensitive action requested: {summary}. Please confirm in the dialog to proceed.",
                    "requires_dialog": True
                }
            else:
                return {
                    "agent": self.name,
                    "status": "blocked",
                    "error": sec_check.get("error", "Execution blocked by security policy.")
                }

        # Safe simulated device execution
        return {
            "agent": self.name,
            "status": "success",
            "tool": tool_name,
            "arguments": arguments,
            "message": f"Successfully executed: {summary}"
        }


device_agent = DeviceControlAgent()
