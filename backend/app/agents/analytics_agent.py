"""
AETHER X - Analytics Agent
Performs quantitative analysis, statistics, dataset interpretation,
and mathematical logic by combining deterministic Python analytics with Gemini AI insights.
Never executes arbitrary shell or system code.
"""

import math
from typing import Any, Dict, List, Optional, Union
from app.providers.provider_manager import provider_manager


class AnalyticsAgent:
    """Specialized agent for data analysis, numbers, and statistical insights."""

    SYSTEM_INSTRUCTION = (
        "You are the AETHER Analytics Agent. You interpret quantitative metrics, financial data, "
        "and numerical trends. Present findings clearly with key performance indicators (KPIs), "
        "percentage deltas, statistical distribution, and strategic takeaways."
    )

    def __init__(self):
        self.name = "analytics_agent"

    def compute_basic_stats(self, numbers: List[Union[int, float]]) -> Dict[str, float]:
        """Deterministic Python calculation helper."""
        if not numbers:
            return {}
        n = len(numbers)
        mean_val = sum(numbers) / n
        sorted_nums = sorted(numbers)
        median_val = (sorted_nums[n // 2] if n % 2 != 0 else (sorted_nums[n // 2 - 1] + sorted_nums[n // 2]) / 2.0)
        variance = sum((x - mean_val) ** 2 for x in numbers) / n
        std_dev = math.sqrt(variance)
        return {
            "count": float(n),
            "sum": float(sum(numbers)),
            "min": float(min(numbers)),
            "max": float(max(numbers)),
            "mean": round(mean_val, 4),
            "median": round(median_val, 4),
            "std_dev": round(std_dev, 4)
        }

    def process(
        self,
        query: str,
        data_points: Optional[List[Union[int, float]]] = None,
        explicit_provider: Optional[str] = None
    ) -> Dict[str, Any]:
        stats_summary = ""
        computed_stats = {}
        if data_points:
            computed_stats = self.compute_basic_stats(data_points)
            stats_summary = f"Pre-computed Python Stats: {computed_stats}\n\n"

        prompt = f"{stats_summary}User Analytics Query: {query}"

        result = provider_manager.generate_text(
            prompt=prompt,
            task_type="data_analysis",
            explicit_provider=explicit_provider,
            system_instruction=self.SYSTEM_INSTRUCTION
        )

        return {
            "agent": self.name,
            "computed_stats": computed_stats,
            "analysis": result.get("text", ""),
            "provider_used": result.get("provider", "gemini"),
            "model_used": result.get("model", "")
        }


analytics_agent = AnalyticsAgent()
