"""
Sanity check 실패 시 이메일 알림.
GitHub Actions 환경에서는 workflow의 failure email이 우선 동작하므로,
이 모듈은 로컬 실행 시 보조 알림 + GitHub Actions annotation 출력용.
"""

import logging
import os
import smtplib
from email.mime.text import MIMEText

from pipeline.config import ALERT_EMAIL

logger = logging.getLogger(__name__)


def send_failure_alert(subject: str, body: str):
    """
    Sanity check 실패 시 알림 발송.
    1) GitHub Actions annotation (::error::) 출력
    2) SMTP 설정이 있으면 이메일 발송 시도
    """
    # GitHub Actions annotation — workflow summary에 표시됨
    if os.environ.get("GITHUB_ACTIONS"):
        print(f"::error::{subject}")

    # SMTP 이메일 (선택적 — 환경변수 설정 시에만 동작)
    smtp_host = os.environ.get("SMTP_HOST")
    smtp_user = os.environ.get("SMTP_USER")
    smtp_pass = os.environ.get("SMTP_PASS")

    if not all([smtp_host, smtp_user, smtp_pass]):
        logger.info("[Alert] SMTP 미설정 — 이메일 스킵 (GitHub Actions email에 의존)")
        return

    try:
        msg = MIMEText(body, "plain", "utf-8")
        msg["Subject"] = f"[StockScreener] {subject}"
        msg["From"] = smtp_user
        msg["To"] = ALERT_EMAIL

        smtp_port = int(os.environ.get("SMTP_PORT", "587"))
        with smtplib.SMTP(smtp_host, smtp_port) as server:
            server.starttls()
            server.login(smtp_user, smtp_pass)
            server.send_message(msg)

        logger.info(f"[Alert] 이메일 발송 완료 → {ALERT_EMAIL}")
    except Exception as e:
        logger.warning(f"[Alert] 이메일 발송 실패: {e}")
