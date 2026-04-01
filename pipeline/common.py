"""
공통 유틸리티 — 종목 선정, Supabase 연결, 이메일 알림.
repo-A / repo-B 양쪽에서 사용.
"""

import logging
import smtplib
import time
from datetime import date
from email.mime.text import MIMEText
from pathlib import Path

from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parent.parent / ".env")

from pipeline.config import (
    EMAIL_TO,
    SMTP_HOST,
    SMTP_PASSWORD,
    SMTP_PORT,
    SMTP_USERNAME,
)
from pipeline.loaders.supabase_upsert import get_supabase_client

logger = logging.getLogger(__name__)


def get_sentiment_symbols(supabase, top_n: int) -> list[str]:
    """
    Sentiment 수집 대상 주식 선정.
    조건: 보통주만, ETF/우선주/워런트/유닛/Rights 제외, 거래대금 내림차순.
    """
    try:
        resp = (
            supabase.table("latest_equities")
            .select("symbol, price, volume, asset_type, name")
            .eq("is_delisted", False)
            .eq("asset_type", "stock")
            .execute()
        )
        rows = resp.data or []
    except Exception as e:
        logger.error(f"[Common] 종목 조회 실패: {e}")
        return []

    # 우선주/워런트/유닛/Rights 제외
    filtered = []
    for r in rows:
        sym = r.get("symbol", "")
        name = (r.get("name") or "").lower()
        if any(c in sym for c in (".", "$", "-")):
            continue
        if any(kw in name for kw in ("preferred", "warrant", "unit", "rights")):
            continue
        price = r.get("price") or 0
        volume = r.get("volume") or 0
        if price > 0 and volume > 0:
            r["_turnover"] = price * volume
            filtered.append(r)

    filtered.sort(key=lambda x: x["_turnover"], reverse=True)
    symbols = [r["symbol"] for r in filtered[:top_n]]
    logger.info(f"[Common] Sentiment 대상: {len(symbols)}종목 (요청: {top_n})")
    return symbols


def get_etf_symbols(supabase, top_n: int) -> list[str]:
    """
    ETF 수집 대상 선정.
    조건: ETF, 미상장폐지, 거래대금 내림차순.
    """
    try:
        resp = (
            supabase.table("latest_equities")
            .select("symbol, price, volume")
            .eq("is_delisted", False)
            .eq("asset_type", "etf")
            .execute()
        )
        rows = resp.data or []
    except Exception as e:
        logger.error(f"[Common] ETF 조회 실패: {e}")
        return []

    for r in rows:
        price = r.get("price") or 0
        volume = r.get("volume") or 0
        r["_turnover"] = price * volume

    rows.sort(key=lambda x: x["_turnover"], reverse=True)
    symbols = [r["symbol"] for r in rows[:top_n]]
    logger.info(f"[Common] ETF 대상: {len(symbols)}종목 (요청: {top_n})")
    return symbols


def get_finnhub_etf_list_cache(supabase) -> set[str]:
    """Finnhub 지원 ETF 목록 캐시 조회."""
    try:
        resp = supabase.table("etf_list_cache").select("symbol").execute()
        return {r["symbol"] for r in (resp.data or [])}
    except Exception:
        return set()


def send_batch_email(subject: str, body: str):
    """배치 완료/실패 이메일 발송."""
    if not SMTP_USERNAME or not SMTP_PASSWORD:
        logger.info("[Email] SMTP 미설정 — 이메일 스킵")
        return

    try:
        msg = MIMEText(body, "plain", "utf-8")
        msg["Subject"] = subject
        msg["From"] = f"Stock Screener Bot <{SMTP_USERNAME}>"
        msg["To"] = EMAIL_TO

        with smtplib.SMTP(SMTP_HOST, SMTP_PORT) as server:
            server.starttls()
            server.login(SMTP_USERNAME, SMTP_PASSWORD)
            server.send_message(msg)

        logger.info(f"[Email] 발송 완료: {subject}")
    except Exception as e:
        logger.warning(f"[Email] 발송 실패: {e}")


def is_us_trading_day() -> bool:
    """오늘이 미국 거래일인지 확인."""
    from pipeline.utils.us_market_calendar import is_us_trading_day
    return is_us_trading_day(date.today())


def rate_limit_sleep(seconds: float):
    """Rate limit 대기."""
    time.sleep(seconds)
