"""
FMP (Financial Modeling Prep) Stable API 클라이언트.
Free 플랜 제약으로 개별 quote/profile만 사용.
Universe Sync, Batch Quote, ETF Holdings는 다른 소스로 대체.
"""

import logging
import time

import requests
from tenacity import retry, stop_after_attempt, wait_exponential

from pipeline.config import (
    FMP_API_KEY,
    FMP_BASE_URL,
    FMP_DAILY_LIMIT,
)

logger = logging.getLogger(__name__)


class FMPClient:
    """FMP Stable API 래퍼 — 요청 카운터 내장."""

    def __init__(self):
        self.api_key = FMP_API_KEY
        self.base_url = FMP_BASE_URL
        self.request_count = 0
        self._available = bool(FMP_API_KEY)

    def _check_limit(self, n: int = 1):
        if self.request_count + n > FMP_DAILY_LIMIT:
            raise RuntimeError(
                f"FMP 일일 한도 초과 예상: 현재 {self.request_count}, "
                f"추가 {n}, 한도 {FMP_DAILY_LIMIT}"
            )

    @retry(stop=stop_after_attempt(3), wait=wait_exponential(min=2, max=30))
    def _get(self, endpoint: str, params: dict | None = None) -> dict | list:
        self._check_limit()
        url = f"{self.base_url}/{endpoint}"
        p = {"apikey": self.api_key}
        if params:
            p.update(params)

        resp = requests.get(url, params=p, timeout=30)
        resp.raise_for_status()
        self.request_count += 1
        return resp.json()

    def fetch_quote(self, symbol: str) -> dict | None:
        """개별 종목 시세 조회. Free 플랜 가능. 1 req."""
        if not self._available:
            return None
        try:
            data = self._get("quote", params={"symbol": symbol})
            if isinstance(data, list) and data:
                return data[0]
            return None
        except Exception as e:
            logger.debug(f"[FMP] Quote 실패 ({symbol}): {e}")
            return None

    def fetch_profile(self, symbol: str) -> dict | None:
        """개별 종목 프로필 조회. Free 플랜 가능. 1 req."""
        if not self._available:
            return None
        try:
            data = self._get("profile", params={"symbol": symbol})
            if isinstance(data, list) and data:
                return data[0]
            return None
        except Exception as e:
            logger.debug(f"[FMP] Profile 실패 ({symbol}): {e}")
            return None

    def get_request_count(self) -> int:
        return self.request_count
