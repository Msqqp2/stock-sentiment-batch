# US Stock Screener — 배치 아키텍처 통합 스펙

## 프로젝트 구조 개요

이 프로젝트는 **하나의 앱(US Stock Screener)**이다.
단, GitHub Actions 무료 시간(월 2,000분/계정)의 한계로 **2개의 GitHub 계정 + 2개의 repo**를 사용하여 배치를 분산 실행한다.

- **repo-A (기존)**: 메인 배치 — 종목 목록, 가격, 상세 정보, 기술적 지표, 심화 재무, 점수
- **repo-B (신규)**: Sentiment 배치 + ETF 데이터 — Finnhub, StockGeist, Adanos Polymarket, Finnhub ETF
- **Supabase**: 두 repo 모두 **동일한 DB, 동일한 테이블**에 적재

코드는 **하나의 로컬 폴더(stockscreener)**에서 관리하며, git remote 2개를 설정하여 양쪽 repo에 push한다.

```
git remote -v
origin-a   https://github.com/계정A/stockscreener.git       (repo-A)
origin-b   https://github.com/계정B/stock-sentiment-batch.git (repo-B)
```

push 시:
```bash
git push origin-a main   # 계정A repo로 push
git push origin-b main   # 계정B repo로 push
```

같은 코드가 양쪽에 올라가지만, **GitHub Secrets가 다르기 때문에** 각 repo에서 해당 워크플로우만 정상 실행된다.

---

## 로컬 폴더 구조

```
stockscreener/
├── .github/
│   └── workflows/
│       ├── daily_main.yml            # repo-A 전용 (메인 배치)
│       ├── daily_finnhub.yml         # repo-B 전용 (Finnhub daily)
│       ├── weekly_polymarket.yml     # repo-B 전용 (Adanos 화요일)
│       ├── weekly_stockgeist.yml     # repo-B 전용 (StockGeist 수요일)
│       └── weekly_etf.yml            # repo-B 전용 (Finnhub ETF 목요일)
├── pipeline/
│   ├── daily_sync.py                 # 기존 메인 파이프라인 (수정)
│   ├── common.py                     # 공통 유틸 (종목 선정, Supabase 연결 등)
│   ├── finnhub_sync.py               # 신규: Finnhub Sentiment 수집
│   ├── stockgeist_sync.py            # 신규: StockGeist 수집
│   ├── adanos_sync.py                # 신규: Adanos 수집
│   └── etf_sync.py                   # 신규: Finnhub ETF 수집
├── requirements.txt
└── README.md
```

---

## 워크플로우 실행 분리 방법

각 워크플로우 YAML에서 해당 Secrets 존재 여부를 체크한다.
Secrets가 없으면 해당 워크플로우는 스킵된다.

### repo-A에 등록된 Secrets
```
FMP_API_KEY
SUPABASE_URL
SUPABASE_SERVICE_KEY
SMTP 관련 (추후 등록)
```
→ `daily_main.yml`만 정상 실행됨

### repo-B에 등록된 Secrets
```
SUPABASE_URL
SUPABASE_SERVICE_KEY
FINNHUB_API_KEY
STOCKGEIST_TOKEN
ADANOS_API_KEY
SMTP_HOST
SMTP_PORT
SMTP_USERNAME
SMTP_PASSWORD
EMAIL_TO
```
→ `daily_finnhub.yml`, `weekly_polymarket.yml`, `weekly_stockgeist.yml`, `weekly_etf.yml`만 정상 실행됨
→ 각 워크플로우에 배치 성공/실패 시 이메일 발송 스텝 포함 (repo-A와 동일한 방식)

### 워크플로우 스킵 구현 예시

```yaml
jobs:
  run:
    runs-on: ubuntu-latest
    if: ${{ secrets.FINNHUB_API_KEY != '' }}
    steps:
      ...
```

---

## repo-A: 기존 메인 배치 수정

### 수정 1: yfinance Info 상한 변경

- **현재**: Ticker.info 수집이 500건으로 제한됨
- **변경**: 코드에서 500 또는 관련 변수(max_tickers, limit 등)를 찾아 **3,000**으로 수정
- 현재 배치 로그에서 `[yfinance] Ticker.info: 500/500 완료`로 확인됨

### 수정 2: Info 실행을 월요일 주 1회로 분리

- Info 수집 스텝은 **한국 시간 월요일에만** 실행
- 나머지 요일은 Info 스텝을 스킵
- 방법: 코드 내 요일 분기 처리 또는 워크플로우 cron 분리
- yfinance Ticker.info의 데이터(sector, industry, 재무비율 등)는 대부분 분기 단위로 바뀌므로 주 1회 충분
- ETF 종목도 Info에 포함됨 (expense_ratio, fund_family, total_assets 등 yfinance에서 제공)

### 수정 3: SEC EDGAR 제거

- 기존 EDGAR Form 4 수집 스텝 전체 제거
- 관련 코드, import, 설정 정리
- 이유: 385종목 조회 시 0건 수집되는 이슈 + Finnhub MSPR로 대체

### 수정 4: X Sentiment 제거

- 기존 X Sentiment 수집 스텝 제거
- GitHub Secrets의 X_SENTIMENT_KEY는 값이 비어있었음
- Sentiment 데이터는 repo-B에서 Finnhub/StockGeist/Adanos로 수집

### 수정 후 파이프라인 구성

| # | 항목 | 데이터 소스 | 주기 | 종목 수 | 예상 소요 |
|---|------|-----------|------|---------|----------|
| 1 | 종목 목록 (Universe) | NASDAQ Trader | daily | ~10,692 | 수 초 |
| 2 | 가격 벌크 | yfinance yf.download() | daily | ~10,692 (ETF 포함) | ~19분 |
| 3 | 상세 정보 (Info) | yfinance Ticker.info | **월요일만** | **3,000** (ETF 포함) | ~180분 |
| 4 | 히스토리 / 기술적 지표 | yfinance | daily | **5,000** (ETF 제외, 보통주만, 거래대금 Top 5,000) | ~10분 |
| 5 | 심화 재무 (DeepFinancial) | FMP Free API | daily | ~385 | ~10분 |
| 6 | 종합 점수 산출 | 내부 로직 | daily | - | 수 초 |
| 7 | Supabase 업서트 | Supabase | daily | ~10,692 | ~20초 |
| 8 | **ETF Profile 복사** | latest_equities에서 복사 | daily | ETF 전체 | 수 초 |

#### 수정 5: ETF Profile 테이블 추가 (신규)

repo-A 배치에서 `latest_equities`에 적재된 ETF 데이터를 `etf_profile` 테이블로 복사한다.
추가 API 호출 없이 기존 데이터에서 ETF만 필터하여 upsert하는 구조.
프론트엔드에서 ETF 상세 조회 시 이 테이블을 기본으로 사용한다.

**etf_profile 테이블**
```sql
CREATE TABLE IF NOT EXISTS etf_profile (
  symbol TEXT PRIMARY KEY,
  name TEXT,                        -- ETF명
  fund_family TEXT,                 -- 운용사 (Vanguard, iShares 등)
  category TEXT,                    -- 펀드 카테고리
  expense_ratio FLOAT,              -- 총보수율 (%)
  total_assets FLOAT,               -- AUM (USD)
  yield FLOAT,                      -- 배당 수익률 (%)
  beta_3y FLOAT,                    -- 3년 베타
  price FLOAT,                      -- 현재가
  prev_close FLOAT,                 -- 전일 종가
  change_pct FLOAT,                 -- 변동률 (%)
  volume BIGINT,                    -- 거래량
  avg_volume_10d BIGINT,            -- 10일 평균 거래량
  week52_high FLOAT,                -- 52주 최고
  week52_low FLOAT,                 -- 52주 최저
  inception_date TEXT,              -- 설정일
  asof TIMESTAMPTZ                  -- 데이터 수집 시점
);
```

**복사 로직:**
- `latest_equities`에서 `asset_type = 'ETF'` 조건으로 필터
- 해당 컬럼들을 매핑하여 `etf_profile`에 upsert (`on_conflict=symbol`)
- daily 가격 벌크 후 매일 실행 (가격, 거래량, 변동률 등 반영)
- Info 컬럼(fund_family, expense_ratio 등)은 월요일 Info 실행 후 반영

**프론트엔드 ETF 상세 화면 조회 구조:**
- `etf_profile` → 기본 정보 (가격, 운용사, 보수율, AUM 등) 1건 조회
- `etf_holdings` → 구성종목 리스트 (repo-B Finnhub ETF)
- `etf_sector_exposure` → 섹터 비중 차트 (repo-B Finnhub ETF)
- `etf_country_exposure` → 국가 비중 차트 (repo-B Finnhub ETF)

### GitHub Actions 사용량 (repo-A)

- **미국 영업일 기준**으로만 실행 (월 ~22일, 주말/미국 공휴일 제외)
- 한국 공휴일이라도 미국 영업일이면 실행
- 미국 금요일 마감 데이터 → 한국 월요일 아침 배치에 반영
- 월요일 (Info 실행): ~219분 × 4회 = 876분
- 화~금 (Info 미실행): ~39분 × 18회 = 702분
- 월 합계: **~1,578분** (한도 2,000분, 여유 422분)

---

## repo-B: Sentiment + ETF 배치

### 공통 종목 선정 기준 — 주식 (3개 Sentiment 소스 공통)

Supabase `latest_equities` 테이블에서 아래 조건으로 종목을 동적 선정한다:

```
조건:
- is_delisted = False
- 보통주(Common Stock)만 포함
- 아래 전부 제외: ETF, 우선주(Preferred), 워런트(Warrant), 유닛(Unit), Rights
- 정렬: price × volume DESC (거래대금 내림차순)
- Finnhub Sentiment, Adanos: 상위 500종목
- StockGeist: 상위 2,000종목
```

### ETF 종목 선정 기준

Supabase `latest_equities` 테이블에서 아래 조건으로 ETF를 동적 선정한다:

```
조건:
- is_delisted = False
- asset_type = 'ETF'
- 정렬: price × volume DESC (거래대금 내림차순)
- 상위 200개 ETF
추가:
- Finnhub /etf/list 를 월 1회 호출하여 Finnhub 지원 ETF 목록 캐싱
- 거래대금 Top 200과 Finnhub 지원 목록의 교집합을 최종 대상으로 선정
```

이 로직들은 `pipeline/common.py`에 공통 함수로 구현한다.

---

### 소스 1: Finnhub Sentiment + Recommendation (daily, 500종목)

#### 스케줄
- **매일** 실행

#### API 정보
- Base URL: `https://finnhub.io/api/v1`
- 인증: 쿼리 파라미터 `token=FINNHUB_API_KEY` 또는 헤더 `X-Finnhub-Token`
- Rate Limit: **분당 60건**, 월 무제한
- Python: `pip install finnhub-python`

#### 수집 엔드포인트 (종목당 4건 호출)

**1) Social Sentiment**
```
GET /stock/social-sentiment?symbol=AAPL&from=2026-03-30&to=2026-03-31
```
- Reddit + Twitter 소셜 감성 데이터
- mention 수, positive/negative score

**2) Insider Sentiment (MSPR)**
```
GET /stock/insider-sentiment?symbol=AAPL&from=2026-01-01&to=2026-03-31
```
- MSPR (Monthly Share Purchase Ratio): -100 ~ +100
- 내부자 매수 우세(양수) / 매도 우세(음수)

**3) Insider Transactions**
```
GET /stock/insider-transactions?symbol=AAPL
```
- 내부자 거래 내역 (Form 3, 4, 5 기반)

**4) Recommendation Trends (애널리스트 투자의견)**
```
GET /stock/recommendation?symbol=AAPL
```
- 애널리스트 Buy/Sell/Hold/StrongBuy/StrongSell 추이
- yfinance의 애널리스트 데이터와 **소스가 다름** (Finnhub 자체 집계)
- 프론트엔드에서 yfinance 출처와 Finnhub 출처를 **나란히 2개 컬럼**으로 표시
- 각 컬럼명에 출처 표기 (예: "애널리스트 의견 (Yahoo)", "애널리스트 의견 (Finnhub)")

#### 호출 방식
- 500종목 × 4 엔드포인트 = **2,000건/일**
- 2,000 ÷ 60(분당) = **~33분**
- 분당 60건 제한 → sleep/rate limiter 적용

#### Supabase 저장 컬럼 (latest_equities 테이블에 추가)

```sql
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS fh_social_sentiment FLOAT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS fh_social_positive INT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS fh_social_negative INT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS fh_insider_mspr FLOAT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS fh_insider_change FLOAT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS fh_rec_buy INT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS fh_rec_hold INT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS fh_rec_sell INT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS fh_rec_strong_buy INT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS fh_rec_strong_sell INT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS fh_rec_period TEXT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS fh_asof TIMESTAMPTZ;
```

---

### 소스 2: StockGeist (수요일 주 1회, 2,000종목)

#### 스케줄
- **한국 시간 수요일** 주 1회

#### API 정보
- Base URL: `https://api.stockgeist.ai/v2`
- 인증: API 토큰
- 무료 한도: **월 10,000 크레딧** (1 호출 = 1 크레딧)
- 2,000종목 × 5주(최대) = 10,000건/월
- Python: `pip install stockgeist` 또는 직접 REST 호출
- API 문서: https://api.stockgeist.ai/v2/docs

#### 수집 데이터
- sentiment: positive / neutral / negative 분류
- emotionality: 감정 강도
- 멘션 수 (소셜 + 뉴스)

#### 호출 방식
- 2,000종목 개별 호출

#### Supabase 저장 컬럼 (latest_equities 테이블에 추가)

```sql
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS sg_sentiment_pos FLOAT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS sg_sentiment_neg FLOAT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS sg_sentiment_neu FLOAT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS sg_emotionality FLOAT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS sg_mention_count INT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS sg_asof TIMESTAMPTZ;
```

---

### 소스 3: Adanos Polymarket (화요일 주 1회, 500종목)

#### 스케줄
- **한국 시간 화요일** 주 1회

#### API 정보
- Base URL: `https://api.adanos.org/polymarket/stocks/v1`
- 인증: 헤더 `X-API-Key: ADANOS_API_KEY`
- 무료 한도: **월 250건**, 분당 100건
- 엔드포인트: `/v1/compare` (1회 최대 10종목)

#### 호출 방식
- 500종목 ÷ 10 = **50회 호출**
- 50회 × 4주 = 200건/월 (한도 250건 이내)
- `GET /v1/compare?tickers=AAPL,MSFT,NVDA,...&days=7`

#### compare 응답 구조

```json
{
  "period_days": 7,
  "stocks": [
    {
      "ticker": "TSLA",
      "company_name": "Tesla, Inc.",
      "buzz_score": 68.9,
      "trend": "stable",
      "trade_count": 312,
      "market_count": 27,
      "unique_traders": 119,
      "sentiment_score": 0.28,
      "bullish_pct": 64,
      "bearish_pct": 36,
      "total_liquidity": 128000
    }
  ]
}
```

#### Supabase 저장 컬럼 (latest_equities 테이블에 추가)

```sql
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS pm_buzz_score FLOAT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS pm_trend TEXT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS pm_sentiment_score FLOAT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS pm_bullish_pct INT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS pm_bearish_pct INT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS pm_trade_count INT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS pm_market_count INT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS pm_unique_traders INT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS pm_total_liquidity FLOAT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS pm_asof TIMESTAMPTZ;
```

---

### 소스 4: Finnhub ETF (목요일 주 1회, 200 ETF)

#### 스케줄
- **한국 시간 목요일** 주 1회

#### API 정보
- Finnhub 동일 API 키 사용 (Sentiment와 공유)
- Rate Limit: 분당 60건, 월 무제한

#### ETF 종목 선정 로직
1. Supabase `latest_equities`에서 `asset_type = 'ETF'`, `is_delisted = False` 조건으로 `price × volume DESC` 상위 200개 추출
2. Finnhub `/etf/list` (월 1회 호출, 캐싱)로 Finnhub 지원 ETF 목록 확보
3. 위 두 리스트의 **교집합**을 최종 대상으로 선정

#### 수집 엔드포인트 (ETF당 3건 호출)

**1) Holdings (구성종목)**
```
GET /etf/holdings?symbol=SPY
```
- 응답: 구성종목별 ticker, 비중(%), 보유 수량, 시가총액
- ETF에서 가장 중요한 데이터
- 리밸런싱 시 변경됨 (분기/월)

**2) Sector Exposure (섹터 비중)**
```
GET /etf/sector?symbol=SPY
```
- 응답: 섹터명, 비중(%)
- 예: Technology 30%, Healthcare 15%, Financials 12%

**3) Country Exposure (국가 비중)**
```
GET /etf/country?symbol=SPY
```
- 응답: 국가명, 비중(%)
- 예: United States 60%, China 10%, Japan 8%

#### 호출 방식
- 200 ETF × 3 엔드포인트 = **600건/주**
- 600 ÷ 60(분당) = **10분**
- Finnhub `/etf/list`는 월 1회 호출하여 DB에 캐싱

#### 참고: yfinance에서 이미 수집되는 ETF 데이터 (repo-A)
아래 데이터는 repo-A의 yfinance Ticker.info에서 이미 수집되므로 Finnhub에서 중복 수집하지 않는다:
- expense_ratio (총보수율)
- fund_family (운용사: Vanguard, iShares 등)
- total_assets (AUM)
- yield (배당 수익률)
- category (펀드 카테고리)
- beta3Year (3년 베타)

#### Supabase 저장 — 별도 테이블 (1:N 관계)

**etf_holdings 테이블**
```sql
CREATE TABLE IF NOT EXISTS etf_holdings (
  id BIGSERIAL PRIMARY KEY,
  etf_symbol TEXT NOT NULL,
  holding_symbol TEXT,
  holding_name TEXT,
  weight FLOAT,
  shares INT,
  market_value FLOAT,
  asof TIMESTAMPTZ,
  UNIQUE(etf_symbol, holding_symbol)
);
```

**etf_sector_exposure 테이블**
```sql
CREATE TABLE IF NOT EXISTS etf_sector_exposure (
  id BIGSERIAL PRIMARY KEY,
  etf_symbol TEXT NOT NULL,
  sector TEXT NOT NULL,
  weight FLOAT,
  asof TIMESTAMPTZ,
  UNIQUE(etf_symbol, sector)
);
```

**etf_country_exposure 테이블**
```sql
CREATE TABLE IF NOT EXISTS etf_country_exposure (
  id BIGSERIAL PRIMARY KEY,
  etf_symbol TEXT NOT NULL,
  country TEXT NOT NULL,
  weight FLOAT,
  asof TIMESTAMPTZ,
  UNIQUE(etf_symbol, country)
);
```

**etf_list_cache 테이블 (Finnhub 지원 목록 캐싱, 월 1회 갱신)**
```sql
CREATE TABLE IF NOT EXISTS etf_list_cache (
  symbol TEXT PRIMARY KEY,
  name TEXT,
  cached_at TIMESTAMPTZ
);
```

ETF 업서트 방식: 매주 목요일 기존 데이터 DELETE 후 INSERT (전체 교체)

---

## 워크플로우 cron 스케줄

```yaml
# daily_main.yml (repo-A 전용)
on:
  schedule:
    - cron: '0 22 * * *'     # UTC 22:00 = KST 07:00

# daily_finnhub.yml (repo-B 전용)
on:
  schedule:
    - cron: '30 22 * * *'    # UTC 22:30 = KST 07:30

# weekly_polymarket.yml (repo-B 전용, 화요일)
on:
  schedule:
    - cron: '30 22 * * 1'    # UTC 22:30 월요일 = KST 07:30 화요일

# weekly_stockgeist.yml (repo-B 전용, 수요일)
on:
  schedule:
    - cron: '30 22 * * 2'    # UTC 22:30 화요일 = KST 07:30 수요일

# weekly_etf.yml (repo-B 전용, 목요일)
on:
  schedule:
    - cron: '30 22 * * 3'    # UTC 22:30 수요일 = KST 07:30 목요일
```

---

## GitHub Actions 사용량 합계

### 배치 실행 기준

**daily 배치 (repo-A 메인, repo-B Finnhub Sentiment+Rec)**
- **미국 영업일(US Business Day) 기준**으로만 실행 (월 ~22일)
- 미국 주말(토/일) → 배치 스킵
- 미국 공휴일 → 배치 스킵
- 한국 공휴일이라도 미국 영업일이면 → **배치 실행**
- 미국 금요일 마감 데이터 → **한국 월요일 아침** 배치에 반영 (토/일 스킵)
- 워크플로우에서 미국 시장 휴장 여부를 체크하여 휴장일에는 early exit 처리

**주 1회 배치 (Adanos 화요일, StockGeist 수요일, ETF 목요일)**
- 미국 공휴일 여부와 **무관하게 매주 실행**
- 해당 요일이 미국 공휴일이라도 정상 실행

### repo-A (계정A)
| 요일 | 내용 | 소요 |
|------|------|------|
| 월요일 | 메인 + Info 3,000건 | ~219분 |
| 화~금 | 메인 (Info 스킵) | ~39분 |
| 주말/미국 공휴일 | 스킵 | 0분 |
| **월 합계** | 219×4 + 39×18 | **~1,578분** (한도 2,000분) |

### repo-B (계정B)
| 요일 | 내용 | 소요 |
|------|------|------|
| 월,금 | Finnhub Sentiment+Rec daily | ~33분 |
| 화요일 | Finnhub Sentiment+Rec + Adanos Polymarket | ~38분 |
| 수요일 | Finnhub Sentiment+Rec + StockGeist | ~43분 |
| 목요일 | Finnhub Sentiment+Rec + Finnhub ETF | ~43분 |
| 주말/미국 공휴일 | 스킵 | 0분 |
| **월 합계** | 33×10 + 38×4 + 43×4 + 43×4 | **~926분** (한도 2,000분) |

---

## Supabase 업서트 주의사항

- 메인 배치(repo-A)와 Sentiment/ETF 배치(repo-B)가 같은 DB에 쓴다
- Sentiment 배치에서 업서트 시 **sentiment 컬럼만** 선택적으로 업데이트
- 메인 배치가 적재한 가격, Info, 재무 데이터를 덮어쓰지 않도록 주의
- 업서트 키: `symbol` (on_conflict=symbol)
- ETF holdings/sector/country는 별도 테이블이므로 충돌 없음
- ETF 업서트 시 기존 데이터 DELETE 후 INSERT (주 1회 전체 교체)

---

## 태우 수동 작업 체크리스트

### repo-A (기존)
- [ ] Gmail 앱 비밀번호 생성 (myaccount.google.com/apppasswords)
- [ ] GitHub Secrets에 SMTP 관련 시크릿 등록 (변수명은 워크플로우 YAML 확인)

### repo-B (신규)
- [ ] 신규 GitHub 계정 생성
- [ ] 신규 repo 생성 (예: stock-sentiment-batch)
- [ ] Finnhub API 키 발급 (https://finnhub.io)
- [ ] StockGeist API 토큰 발급 (https://www.stockgeist.ai)
- [ ] Adanos API 키 발급 (https://adanos.org/polymarket-stock-sentiment#api)
- [ ] 신규 repo GitHub Secrets 등록:
  - SUPABASE_URL (계정A와 동일)
  - SUPABASE_SERVICE_KEY (계정A와 동일)
  - FINNHUB_API_KEY
  - STOCKGEIST_TOKEN
  - ADANOS_API_KEY
  - SMTP_HOST (계정A와 동일)
  - SMTP_PORT (계정A와 동일)
  - SMTP_USERNAME (계정A와 동일)
  - SMTP_PASSWORD (계정A와 동일)
  - EMAIL_TO (계정A와 동일)

### 로컬 git remote 설정
```bash
cd stockscreener
git remote add origin-b https://github.com/계정B유저명/stock-sentiment-batch.git
```
이후 push:
```bash
git push origin main        # 기존 계정A repo
git push origin-b main      # 신규 계정B repo
```

---

## GitHub Secrets 전체 정리

### repo-A
```
FMP_API_KEY          -- FMP Free API 키 (기존)
SUPABASE_URL         -- Supabase 프로젝트 URL (기존)
SUPABASE_SERVICE_KEY -- Supabase 서비스 키 (기존)
SMTP_HOST            -- smtp.gmail.com (신규)
SMTP_PORT            -- 587 (신규)
SMTP_USERNAME        -- Gmail 주소 (신규)
SMTP_PASSWORD        -- Gmail 앱 비밀번호 16자리 (신규)
EMAIL_TO             -- 수신 이메일 (신규)
```

### repo-B
```
SUPABASE_URL         -- 계정A와 동일
SUPABASE_SERVICE_KEY -- 계정A와 동일
FINNHUB_API_KEY      -- Finnhub 무료 API 키 (Sentiment + ETF 공용)
STOCKGEIST_TOKEN     -- StockGeist API 토큰
ADANOS_API_KEY       -- Adanos API 키
SMTP_HOST            -- smtp.gmail.com (계정A와 동일)
SMTP_PORT            -- 587 (계정A와 동일)
SMTP_USERNAME        -- Gmail 주소 (계정A와 동일)
SMTP_PASSWORD        -- Gmail 앱 비밀번호 16자리 (계정A와 동일)
EMAIL_TO             -- 수신 이메일 (계정A와 동일)
```

---

## API 키 발급 방법

### Finnhub
1. https://finnhub.io 가입
2. Dashboard에서 API Key 확인
3. 무료: 분당 60건, 월 무제한

### StockGeist
1. https://www.stockgeist.ai 가입
2. Dashboard에서 API 토큰 확인
3. 무료: 월 10,000 크레딧

### Adanos
1. https://adanos.org/polymarket-stock-sentiment#api 에서 이메일 등록
2. 이메일로 API 키 수신 (24시간 내 1회 사용 링크)
3. 무료: 월 250건

---

## 기존 이슈 요약

| 이슈 | 원인 | 해결 |
|------|------|------|
| Info 500건 제한 → 점수 776건만 산출 | 코드에 500 하드코딩 | 3,000으로 수정, 월요일 주 1회 |
| 이메일 발송 실패 (530 Authentication) | Gmail SMTP 미설정 | 앱 비밀번호 + Secrets 등록 |
| EDGAR Form 4 → 0건 수집 | 파싱 이슈 | 스텝 제거, Finnhub MSPR로 대체 |
| X_SENTIMENT_KEY 비어있음 | 미설정 | 스텝 제거, repo-B Sentiment로 대체 |
| ETF Holdings 스킵 | FMP Free tier 제약 | Finnhub ETF 엔드포인트로 대체 (repo-B 목요일) |
