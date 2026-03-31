"""
배치 실행 Summary 로깅.
GitHub Actions Summary에 Markdown으로 기록.
"""

import os
from datetime import date


def build_batch_summary(
    elapsed_seconds: float, sanity_result, step_timings: dict | None = None
) -> str:
    """배치 완료 후 최종 Summary 생성."""
    minutes = elapsed_seconds / 60
    status = "PASSED" if sanity_result.passed else "FAILED"

    lines = [
        f"=== Daily Batch Summary — {date.today()} ===",
        f"  소요 시간:     {minutes:.1f}분",
        f"  Sanity Check: {status}",
        f"  CRITICAL:     {len(sanity_result.criticals)}건",
        f"  WARNING:      {len(sanity_result.warnings)}건",
    ]

    if step_timings:
        lines.append("  --- Step Timings ---")
        for step, secs in step_timings.items():
            lines.append(f"    {step}: {secs:.1f}초")

    return "\n".join(lines)


def write_github_summary(summary: str):
    """GitHub Actions Job Summary에 Markdown으로 기록."""
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a") as f:
            f.write(f"```\n{summary}\n```\n")
