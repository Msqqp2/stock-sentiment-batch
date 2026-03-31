"""
배치 파이프라인 설정 상수.
"""

import os

# ── API Keys & URLs ──
FMP_API_KEY = os.environ.get("FMP_API_KEY", "")
FMP_BASE_URL = "https://financialmodelingprep.com/stable"

SUPABASE_URL = os.environ.get("SUPABASE_URL", "")
SUPABASE_SERVICE_KEY = os.environ.get("SUPABASE_SERVICE_KEY", "")

# ── 이메일 알림 ──
ALERT_EMAIL = os.environ.get("ALERT_EMAIL", "musiqq86@gmail.com")

# ── yfinance 설정 ──
YFINANCE_CHUNK_SIZE = 80
YFINANCE_SLEEP_BETWEEN = 2
YFINANCE_MAX_RETRIES = 3
YFINANCE_RETRY_BACKOFF = 30
YFINANCE_INFO_SLEEP = 2  # Ticker.info 호출 간격 (초)

# ── FMP 설정 (Free 플랜: 개별 quote/profile만 사용) ──
FMP_DAILY_LIMIT = 250

# ── EDGAR 설정 ──
EDGAR_HEADERS = {"User-Agent": "StockScreener musiqq86@gmail.com"}
EDGAR_RATE_LIMIT_SLEEP = 0.15  # ~7 req/s (10 req/s 한도)
EDGAR_TOP_N = 1000  # Form 4 수집 대상 종목 수

# ── Supabase UPSERT 설정 ──
UPSERT_CHUNK_SIZE = 500

# ── 우선 티커 리스트 ──
PRIORITY_INDUSTRIES = [
    "Semiconductors",
    "Semiconductor Equipment & Materials",
    "Uranium",
    "Solar",
    "Electrical Equipment & Parts",
    "Aerospace & Defense",
    "Software - Infrastructure",
    "Specialty Industrial Machinery",
]

PRIORITY_TICKERS = [
    "IONQ", "RGTI", "QBTS", "QUBT",   # 양자컴퓨팅
    "OKLO", "SMR", "NNE", "LEU",       # 원자력/SMR
    "BWXT", "CCJ", "UEC", "DNN",       # 우라늄/원자력
]

# ── X Sentiment API (Nice-to-Have) ──
X_SENTIMENT_BASE = os.environ.get("X_SENTIMENT_BASE", "https://adanos.org/x-stock-sentiment/v1")
X_SENTIMENT_KEY = os.environ.get("X_SENTIMENT_KEY", "")
X_SENTIMENT_HEALTH_TIMEOUT = 10
X_SENTIMENT_MAX_CONSECUTIVE_FAILURES = 3

# ── ETF 롤링 배치 ──
ETF_TIER1_COUNT = 300
ETF_TIER2_COUNT = 700

# ── 심화 재무 ──
DEEP_FINANCIAL_TOP_N = 500

# ── 대상 거래소 ──
TARGET_EXCHANGES = {"NYSE", "NASDAQ", "AMEX"}
TARGET_ASSET_TYPES = {"stock", "etf"}
