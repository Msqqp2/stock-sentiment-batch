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
YFINANCE_INFO_TOP_N = 3000  # 월요일 Info 수집 대상 (ETF 포함)

# ── 기술적 지표 설정 ──
TECHNICALS_TOP_N = 5000  # 거래대금 상위 보통주 (ETF 제외)

# ── FMP 설정 (Free 플랜: 개별 quote/profile만 사용) ──
FMP_DAILY_LIMIT = 250


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


# ── 심화 재무 ──
DEEP_FINANCIAL_TOP_N = 500

# ── Finnhub API ──
FINNHUB_API_KEY = os.environ.get("FINNHUB_API_KEY", "")
FINNHUB_BASE_URL = "https://finnhub.io/api/v1"
FINNHUB_RATE_LIMIT_SLEEP = 1.05  # 60 req/min → ~1초 간격
FINNHUB_SENTIMENT_TOP_N = 500
FINNHUB_ETF_TOP_N = 200

# ── StockGeist API ──
STOCKGEIST_TOKEN = os.environ.get("STOCKGEIST_TOKEN", "")
STOCKGEIST_BASE_URL = "https://api.stockgeist.ai/v2"
STOCKGEIST_TOP_N = 2000

# ── Adanos Polymarket API ──
ADANOS_API_KEY = os.environ.get("ADANOS_API_KEY", "")
ADANOS_BASE_URL = "https://api.adanos.org/polymarket/stocks/v1"
ADANOS_TOP_N = 500
ADANOS_BATCH_SIZE = 10  # /compare 1회 최대 10종목

# ── SMTP 이메일 알림 ──
SMTP_HOST = os.environ.get("SMTP_HOST", "smtp.gmail.com")
SMTP_PORT = int(os.environ.get("SMTP_PORT", "587"))
SMTP_USERNAME = os.environ.get("SMTP_USERNAME", "")
SMTP_PASSWORD = os.environ.get("SMTP_PASSWORD", "")
EMAIL_TO = os.environ.get("EMAIL_TO", "musiqq86@gmail.com")

# ── 대상 거래소 ──
TARGET_EXCHANGES = {"NYSE", "NASDAQ", "AMEX"}
TARGET_ASSET_TYPES = {"stock", "etf"}
