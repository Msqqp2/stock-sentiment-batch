# 미국 주식/ETF 최신 스냅샷 스크리너 — 상세 아키텍처 기획서

> **버전**: v2.1 (상세 확장판 + 개선사항 반영)
> **작성일**: 2026-03-30
> **대상**: Claude Code 기반 개발용 — Kotlin/Jetpack Compose 프론트엔드 + Supabase 백엔드

**v2.1 변경 이력**:
- X Sentiment API를 Nice-to-Have 등급으로 격리 (헬스체크 + 장애 감지 추가)
- 배치 실패 시 이메일 알림 (`musiqq86@gmail.com`) 추가
- 데이터 무결성 자동 검증 (Sanity Check) 단계 신설 (§4.6)
- 배치 실행 Summary 로깅 구조 추가 (§4.7)
- EDGAR Form 4 XML 파싱 / 13F Holdings 파싱 의사코드 보강
- 히트맵 구현 기술 선택: Phase 2 WebView+D3.js → Phase 3 Canvas 전환 가능
- 앱 배포 전략 (개인 APK → Google Play Store) + Supabase Key BuildConfig 분리 (§6.11)
- 종합 점수 가중치 사용자 커스터마이징 (§6.12)
- yfinance 장애 폴백 플랜 현실화 (대안 소스 비교는 장애 발생 시 조사)

---

## 1. 프로젝트 개요

### 1.1 목표
미국 3대 거래소(NYSE, NASDAQ, AMEX) 상장 종목 및 ETF의 **'가장 최신(Latest)'** 정량 데이터를 취합하여, 다중 조건 검색 및 정렬 기능을 제공하는 **한국어 UI 기반 정보 대시보드** 구축. (UI 헤더·라벨은 한글, 데이터 값은 영문 원문 유지)

### 1.2 핵심 원칙
- **단일 스냅샷(Latest Snapshot)**: 시계열 미적재, 오직 현재 시점 1회분만 유지
- **외부 산출 100% 수용**: 52주 고가/저가, N일 평균 거래량 등 벤더 계산값 맹신
- **UPSERT 덮어쓰기**: 매일 새벽 `INSERT ... ON CONFLICT DO UPDATE`로 전일 MOC 데이터 교체
- **JSONB 역정규화**: ETF Holdings를 단일 컬럼에 배열로 적재 → JOIN 제거

### 1.3 기술 스택 확정

| 레이어 | 선택 | 근거 |
|--------|------|------|
| **프론트엔드** | Android (Kotlin + Jetpack Compose) | 사용자 환경 |
| **데이터베이스** | Supabase Free (PostgreSQL 15+) | 무료 500MB, PostgREST 내장, 서울 리전 |
| **배치 스크립트** | Python 3.11+ | yfinance/requests 생태계, FMP 호환 |
| **배치 실행 환경** | GitHub Actions (Cron) | 무료 2,000분/월, 매일 새벽 자동 트리거 |
| **API 레이어** | Supabase PostgREST (자동 생성 REST API) | 별도 백엔드 서버 불필요 |
| **고급 쿼리** | Supabase Edge Functions (Deno/TypeScript) | 복합 필터·역산출 등 PostgREST로 불가능한 로직 |

### 1.4 왜 Supabase인가 (무료 티어 비교)

| 항목 | Supabase Free | Neon Free | Render Free |
|------|--------------|-----------|-------------|
| DB 용량 | 500 MB | 0.5 GB/프로젝트 | PostgreSQL 없음 |
| REST API 자동생성 | ✅ PostgREST 내장 | ❌ | ❌ |
| 리전 | **서울(ap-northeast-2)** | 싱가포르 | 오레곤 |
| 비활성 자동 중단 | 7일 미사용 시 일시중지 | scale-to-zero | 15분 후 sleep |
| pg_trgm / GIN 지원 | ✅ | ✅ | N/A |
| Edge Functions | ✅ (500K 호출/월) | ❌ | ❌ |
| Egress | 5 GB/월 | 5 GB/월 | N/A |

**Supabase 최적 이유**:
1. 서울 리전 → 한국에서 Android 앱 쿼리 지연 최소화 (~10ms)
2. PostgREST 자동 REST API → FastAPI 같은 별도 백엔드 서버 없이 앱에서 직접 DB 쿼리
3. 매일 배치가 돌면서 DB를 갱신하므로 7일 비활성 중단 문제 자동 회피
4. Edge Functions로 복잡한 역산출 쿼리도 서버리스로 처리

---

## 2. 데이터 소스 및 API 전략

### 2.1 FMP Free 플랜 제약 분석

| 제약 | 값 | 영향 |
|------|-----|------|
| 일일 요청 한도 | 250회 | ETF Holdings 전수 적재 불가 → 롤링 배치 필수 |
| 30일 대역폭 | 500 MB | 충분 (일 ~5MB 예상) |
| 대상 거래소 | US만 | 요구사항 부합 |
| Batch Quote 지원 | ✅ (콤마 구분 다중 티커) | 1회 요청으로 ~500개 티커 Quote 가능 |
| Premium 전용 엔드포인트 | Earnings Calendar, Stock Peers 등 | 1차 스코프 외 |

### 2.2 데이터 수집 4단계 파이프라인

```
┌─────────────────────────────────────────────────────┐
│          GitHub Actions (매일 KST 07:00, 미국 영업일만) │
│                                                      │
│  ① Universe Sync ─── FMP /stock/list ──────── 1 req │
│         ↓                                            │
│  ② Quote & Metrics ─ yfinance bulk download ── 0 req│
│         ↓              (FMP 미소비)                   │
│  ③ Fundamentals ──── FMP batch quote ──── ~30 req    │
│         ↓                                            │
│  ④ ETF Holdings ──── FMP /etf-holder ── ~200 req    │
│         ↓              (롤링 배치, 14일 1회전)         │
│  ⑤ Analyst Data ─── yfinance(컨센서스) ──── 0 req   │
│         ↓                                            │
│  ⑥ SEC EDGAR ────── data.sec.gov ──── 0 req(FMP)   │
│         ↓            내부자 거래(Form 4) + 기관(13F)  │
│                      무료, API키 불필요, 10 req/초    │
│  ⑦ Social Sent. ─── X Sentiment API ── 0 req(FMP)  │
│                      상위 250종목 소셜점수 (주간)      │
│                      + FMP Social Sentiment 온디맨드  │
│  ⑧ Deep Financials ─ yfinance 재무제표 ── 0 req     │
│                      상위 500종목 영업이익증가율 등     │
│                      + ROIC/5Y성장률/Inst.Txn         │
│                      (분기 재무제표 파싱, +17분)       │
│  ⑨ Technicals ───── yfinance 가격 히스토리 ── 0 req  │
│                      RSI(14)/ATR(14)/변동성/20일MA    │
│                      SMA괴리율/갭/시가대비/상대거래량   │
│                      (200일 히스토리 기반 산출, +5분)   │
│  ⑩ Performance ──── yfinance 가격 히스토리 ── 0 req   │
│                      1주/1월/3월/6월/1년/YTD 수익률    │
│                      (⑨ 히스토리 데이터 재활용, +0분)   │
│                                                      │
│  합계: ~231 FMP req/day (한도 250 이내)               │
│        + EDGAR 별도 (자체 한도 10 req/초)              │
│                                                      │
│  → Supabase PostgreSQL UPSERT                        │
└─────────────────────────────────────────────────────┘
```

#### ① Universe Sync (종목 마스터 동기화) — 1 req/day

**소스**: `GET https://financialmodelingprep.com/api/v3/stock/list?apikey=KEY`

**역할**:
- NYSE, NASDAQ, AMEX 상장 보통주 + ETF 전체 리스트 취득
- 상장폐지·티커 변경·신규 상장 반영
- `type` 필드로 `stock` vs `etf` 구분

**반환 필드 (주요)**:
```json
{
  "symbol": "AAPL",
  "name": "Apple Inc.",
  "price": 178.72,
  "exchange": "NASDAQ",
  "exchangeShortName": "NASDAQ",
  "type": "stock"
}
```

**처리 로직**:
1. 전체 리스트 수신 (~15,000건)
2. `exchangeShortName IN ('NYSE', 'NASDAQ', 'AMEX')` 필터
3. `type = 'stock'` 또는 `type = 'etf'`만 추출
4. `latest_equities` 테이블에 없는 신규 종목 → INSERT
5. 리스트에 없는 기존 종목 → `is_delisted = true` 마킹 (물리 삭제 안 함)

#### ② Quote & Metrics (주가·산출지표) — FMP 0 req 소비

**소스**: `yfinance` Python 라이브러리 (Yahoo Finance 비공식 스크래핑)

**왜 yfinance를 주력으로?**:
- API 키 불필요, 일일 요청 한도 없음 (비공식이므로 rate limit만 주의)
- FMP 250 req/day를 ETF Holdings에 집중 배분 가능
- `yf.download()` 멀티스레드로 전종목 가격 일괄 취득 (~15~20분)
- `Ticker.info`는 **우선 티커 리스트**만 매일 호출 (전종목 불가, GitHub Actions 6시간 한도)

**⚠️ 핵심 제약: `Ticker.info` 전종목 매일 호출 불가**

`yf.download()`로 가격 데이터는 전종목 벌크 취득 가능하지만,
`Ticker.info`(재무비율, 섹터, 배당 등 심화 데이터)는 **종목당 HTTP 1건**이라
15,000건 전수 호출 시 ~8시간 소요 → GitHub Actions 6시간 한도 초과.

**해결: 우선 티커 리스트(Priority List) 기반 계층 호출**

```python
# pipeline/sources/priority_builder.py

from config import PRIORITY_INDUSTRIES, PRIORITY_TICKERS

PRIORITY_INDUSTRIES = [
    # 반도체
    "Semiconductors",
    "Semiconductor Equipment & Materials",
    # 원자력/에너지
    "Uranium",
    "Nuclear Energy",
    "Specialty Industrial Machinery",   # 원자력 장비 일부 포함
    # 양자컴퓨팅 (독립 industry 없음 → 키워드 매칭 병행)
    "Software - Infrastructure",        # IonQ, Rigetti 등이 여기 분류
    # 추가 인기 테마
    "Solar",
    "Electrical Equipment & Parts",     # 전력 인프라
    "Aerospace & Defense",
]

# industry로 안 잡히는 양자컴퓨팅 등은 티커 직접 지정
PRIORITY_TICKERS = [
    "IONQ", "RGTI", "QBTS", "QUBT",   # 양자컴퓨팅
    "OKLO", "SMR", "NNE", "LEU",       # 원자력/SMR
    "BWXT", "CCJ", "UEC", "DNN",       # 우라늄/원자력
]


def build_daily_priority_list(supabase) -> list[str]:
    """
    매일 yfinance Ticker.info 호출 대상 선정.
    시총 Top 1000 + 거래량 Top 1000 + 인기 산업 전종목 + 필수 티커 + 신규 미수집.
    """
    
    # 1) 시총 상위 1,000개
    top_by_cap = supabase.table('latest_equities') \
        .select('symbol') \
        .eq('is_delisted', False) \
        .not_.is_('market_cap', 'null') \
        .order('market_cap', desc=True) \
        .limit(1000) \
        .execute().data
    
    # 2) 거래량 상위 1,000개 (활발히 거래되는 종목)
    top_by_volume = supabase.table('latest_equities') \
        .select('symbol') \
        .eq('is_delisted', False) \
        .not_.is_('volume', 'null') \
        .order('volume', desc=True) \
        .limit(1000) \
        .execute().data
    
    # 3) 인기 산업 전종목 (시총 무관)
    by_industry = supabase.table('latest_equities') \
        .select('symbol') \
        .eq('is_delisted', False) \
        .in_('industry', PRIORITY_INDUSTRIES) \
        .execute().data
    
    # 4) 하드코딩 필수 티커 (양자 등 industry로 안 잡히는 것)
    forced = PRIORITY_TICKERS
    
    # 5) 신규 상장: sector가 아직 NULL (한 번도 Ticker.info 안 한 것)
    new_stocks = supabase.table('latest_equities') \
        .select('symbol') \
        .eq('is_delisted', False) \
        .is_('sector', 'null') \
        .limit(200) \
        .execute().data
    
    # 합치고 중복 제거
    all_symbols = set(
        [r['symbol'] for r in top_by_cap] +
        [r['symbol'] for r in top_by_volume] +
        [r['symbol'] for r in by_industry] +
        forced +
        [r['symbol'] for r in new_stocks]
    )
    
    print(f"[Priority] 시총Top1000: {len(top_by_cap)}, "
          f"거래량Top1000: {len(top_by_volume)}, "
          f"인기산업: {len(by_industry)}, "
          f"필수티커: {len(forced)}, "
          f"신규: {len(new_stocks)}, "
          f"합계(중복제거): {len(all_symbols)}")
    
    return list(all_symbols)
```

**수량 추정**:

| 소스 | 수량 | 비고 |
|------|------|------|
| 시총 Top 1,000 | 1,000 | 기준선 |
| 거래량 Top 1,000 (시총과 미중복분) | ~300~400 | 시총과 60~70% 중복 |
| 인기 산업 (위와 미중복분) | ~200~350 | 반도체 ~150, 원자력 ~40, 솔라 ~60 등 |
| 필수 티커 (위와 미중복분) | ~10 | 양자컴퓨팅 소형주 등 |
| 신규 상장 (sector=NULL) | ~50~100 | 첫 수집 대상 |
| **합계 (중복 제거)** | **~1,600~1,900** | **× 2초 = ~53~63분** |

→ 나머지 ~13,000개는 ③ FMP Batch Quote로 가격/시총/기본 지표만 매일 갱신.
→ 재무비율 등 심화 데이터는 사용자가 상세화면 열 때 온디맨드 `Ticker.info` 1건 호출.

**수집 데이터 (yfinance `Ticker.info` 딕셔너리)**:

| 필드 | yfinance 키 | 설명 |
|------|------------|------|
| 현재가 | `currentPrice` 또는 `regularMarketPrice` | MOC 종가 |
| 시가총액 | `marketCap` | USD 기준 |
| PER (TTM) | `trailingPE` | 후행 12개월 |
| Forward PER | `forwardPE` | 예상 |
| PBR | `priceToBook` | |
| 배당률 | `dividendYield` | 소수 (0.0065 = 0.65%) |
| 배당금(연) | `dividendRate` | USD |
| 52주 최고 | `fiftyTwoWeekHigh` | 벤더 산출값 그대로 |
| 52주 최저 | `fiftyTwoWeekLow` | |
| 50일 이동평균 | `fiftyDayAverage` | |
| 200일 이동평균 | `twoHundredDayAverage` | |
| 평균 거래량 | `averageVolume` | 10일 |
| 평균 거래량 (10d) | `averageDailyVolume10Day` | |
| 베타 | `beta` | |
| EPS (TTM) | `trailingEps` | |
| 섹터 | `sector` | |
| 산업 | `industry` | |
| 거래량 | `volume` | 당일 |
| 전일 종가 | `previousClose` | |
| 시가 | `open` | |
| 일중 최고 | `dayHigh` | |
| 일중 최저 | `dayLow` | |
| ROE | `returnOnEquity` | 소수 (0.302 = 30.2%) |
| ROA | `returnOnAssets` | |
| 부채비율 | `debtToEquity` | 정수 (176.3) |
| 유동비율 | `currentRatio` | |
| 영업이익률 | `operatingMargins` | 소수 |
| 순이익률 | `profitMargins` | 소수 |
| 매출총이익률 | `grossMargins` | 소수 |
| 매출 성장률 | `revenueGrowth` | 소수 (YoY) |
| EPS 성장률 | `earningsGrowth` | 소수 (YoY) |
| PEG | `pegRatio` | |
| 발행주식수 | `sharesOutstanding` | |
| 유통주식수 | `floatShares` | |
| 공매도 주식수 | `sharesShort` | |
| 공매도비율 | `shortRatio` | Days to cover |
| 공매도/유통 | `shortPercentOfFloat` | 소수 |
| 내부자 보유 | `heldPercentInsiders` | 소수 |
| 기관 보유 | `heldPercentInstitutions` | 소수 |
| 투자의견 | `recommendationKey` | 'buy', 'hold', 'sell' |
| 의견 점수 | `recommendationMean` | 1=Strong Buy ~ 5=Sell |
| 평균 목표가 | `targetMeanPrice` | USD |
| 최고 목표가 | `targetHighPrice` | USD |
| 최저 목표가 | `targetLowPrice` | USD |
| 애널리스트 수 | `numberOfAnalystOpinions` | |
| EV | `enterpriseValue` | USD |
| EV/EBITDA | `enterpriseToEbitda` | |
| EV/Revenue | `enterpriseToRevenue` | |
| P/S | `priceToSalesTrailing12Months` | |
| P/FCF | `priceToFreeCashflows` | |
| 매출액 (TTM) | `totalRevenue` | USD |
| EBITDA | `ebitda` | USD |
| 잉여현금흐름 | `freeCashflow` | USD |
| 현금성자산 | `totalCash` | USD |
| 총부채 | `totalDebt` | USD |
| 장부가/주 | `bookValue` | USD |
| 매출/주 | `revenuePerShare` | USD |
| 당좌비율 | `quickRatio` | |
| 배당성향 | `payoutRatio` | 소수 |
| 배당락일 | `exDividendDate` | timestamp→date |
| 5년 평균 배당률 | `fiveYearAvgDividendYield` | % (이미 백분율) |
| 소재국 | `country` | 영문 코드 |
| 실적발표일 | `earningsTimestamp` | timestamp→date |

**Rate Limit 대응 전략**:
```python
CHUNK_SIZE = 80          # 한 번에 다운로드할 티커 수
SLEEP_BETWEEN = 2        # 청크 간 대기 (초)
MAX_RETRIES = 3          # 실패 시 재시도
RETRY_BACKOFF = 30       # 재시도 대기 (초)
```

- **가격 데이터**: 80개씩 `yf.download(chunk, period="1d", threads=True)` → 전종목 벌크 (~20분)
- **펀더멘털**: `yf.Ticker(symbol).info` → **우선 티커 리스트만** 매일 호출 (~1,600~1,900개, ~60분)
- 우선 리스트 외 종목은 ③ FMP Batch Quote로 가격/시총만 갱신, 심화 데이터는 온디맨드

#### ③ Fundamentals 보강 (FMP Batch Quote) — ~30 req/day

**소스**: `GET /api/v3/quote/AAPL,MSFT,...?apikey=KEY` (콤마 구분 최대 ~500개)

**역할**: yfinance에서 누락되거나 불안정한 필드 보강
- `pe`, `eps`, `marketCap` 교차 검증
- `avgVolume` (정확도 높음)
- `priceAvg50`, `priceAvg200`

**요청 산출**: 15,000 ÷ 500 = 30 req

#### ④ ETF 특화 데이터 (FMP) — ~200 req/day (롤링)

**ETF 메타데이터**:
`GET /api/v3/etf-info?symbol=SPY&apikey=KEY`

| 필드 | 설명 |
|------|------|
| `expenseRatio` | 운용수수료 (%) |
| `navPrice` | NAV |
| `assetClass` | 자산군 (Equity, Bond, Commodity 등) |
| `domicile` | 설립지 |
| `inceptionDate` | 설정일 |
| `aum` | 순자산총액 |
| `holdingsCount` | 구성종목 수 |
| `indexTracked` | 추종 지수명 |
| `isActivelyManaged` | 액티브/패시브 구분 |

**ETF Holdings (구성종목)**:
`GET /api/v3/etf-holder/SPY?apikey=KEY`

반환 예시:
```json
[
  {"asset": "AAPL", "name": "Apple Inc.", "sharesNumber": 173889064, "weightPercentage": 7.15, "marketValue": 31063844227},
  {"asset": "MSFT", "name": "Microsoft Corp.", "sharesNumber": 72542967, "weightPercentage": 6.82, "marketValue": 30582191043}
]
```

**⚠️ 핵심 제약: 롤링 배치 전략**

미국 ETF 약 2,800개 × (etf-info 1건 + etf-holder 1건) = 5,600 req → 250/day로 22일 소요.

**해결**: 우선순위 기반 롤링 스케줄

| 티어 | 대상 | 수량 | 갱신 주기 | 일일 req |
|------|------|------|----------|---------|
| **Tier 1** | AUM 상위 300 ETF (SPY, QQQ, IVV, VOO...) | 300 | 매일 | ~100 |
| **Tier 2** | AUM 301~1000위 | 700 | 주 2회 | ~100 |
| **Tier 3** | 나머지 1,800+ | 1,800 | 주 1회 | 0~100 |

**일일 예산 배분**:
```
Universe Sync:         1 req
FMP Batch Quote:      30 req
ETF Tier 1:         ~100 req  (매일)
ETF Tier 2/3 롤링:  ~100 req  (순환)
여유분 (재시도):       19 req
─────────────────────────────
합계:                ~250 req/day
```

#### ⑤ 애널리스트 데이터 — 2계층 소스 전략

애널리스트 투자의견·목표가 데이터를 비용 0으로 확보하기 위한 2계층 구조.

**Layer 1: yfinance 컨센서스 (매일, 전종목, 추가비용 0)**

`Ticker.info`에 이미 포함된 애널리스트 컨센서스 데이터. 기존 ② 배치에서 같이 수집.

| yfinance 키 | DB 컬럼명 | 설명 | 예시 |
|---|---|---|---|
| `recommendationKey` | `analyst_rating` | 투자의견 (문자열) | `buy`, `hold`, `sell` |
| `recommendationMean` | `analyst_rating_score` | 의견 점수 (1=Strong Buy ~ 5=Sell) | `1.8` |
| `targetMeanPrice` | `target_mean` | 평균 목표가 | `$205.50` |
| `targetHighPrice` | `target_high` | 최고 목표가 | `$250.00` |
| `targetLowPrice` | `target_low` | 최저 목표가 | `$160.00` |
| `numberOfAnalystOpinions` | `analyst_count` | 커버 애널리스트 수 | `38` |

→ 스크리너 필터·정렬 가능 (예: "Strong Buy 종목만", "목표가 괴리율 20% 이상")

**Layer 2: FMP 개별 조회 (온디맨드, 상세화면용)**

사용자가 종목 상세 화면을 탭할 때 1건씩 FMP API 호출 (anon key가 아닌 Edge Function 경유).

| 엔드포인트 | 데이터 | 비용 |
|---|---|---|
| `GET /api/v3/grade/AAPL` | 개별 애널리스트별 의견 변경 이력 (Goldman: Buy→Hold 등) | 1 req/조회 |
| `GET /api/v3/price-target-consensus/AAPL` | 컨센서스 목표가 (high/low/median/consensus) | 1 req/조회 |

→ `fmp_cache` 테이블에 캐시 (TTL 30분). Edge Function이 캐시 히트 시 FMP 미호출 → 일일 한도 절약.

#### ⑥ SEC EDGAR — 내부자 거래 + 기관 보유 (무료, 원천 직접 접근)

Yahoo/Finviz 등 중간 가공자를 거치지 않고 **SEC EDGAR 원천 데이터**에 직접 접근.
공시 접수 후 1초 이내 반영되므로, 모든 무료 소스 중 가장 빠름.

**SEC EDGAR 공식 API 사양**:

| 항목 | 값 |
|------|-----|
| Base URL | `https://data.sec.gov` |
| 인증 | **불필요** (User-Agent 헤더에 이메일만 명시) |
| Rate Limit | **10 req/초** (매우 넉넉) |
| 비용 | $0 (미국 정부 공공 데이터) |
| 데이터 반영 속도 | 파일링 접수 후 **<1초** |
| Python 라이브러리 | `sec-edgar-api` (PyPI) |

**수집 대상 Form 유형**:

| Form | 데이터 | 공시 의무 기한 | 갱신 빈도 | 스크리너 가치 |
|------|--------|--------------|----------|-------------|
| **Form 4** | 내부자 거래 (임원/이사 매수·매도) | 거래 후 **2영업일** | 수시 (하루 수백 건) | ⭐⭐⭐ 매우 높음 |
| **13F-HR** | 기관투자자 보유 현황 | 분기 종료 후 **45일** | 분기 1회 | ⭐⭐ 높음 |
| **13D/13G** | 5% 이상 대량 보유 | 취득 후 **10일** | 수시 | ⭐⭐ 높음 |
| **8-K** | 중요 공시 (실적, M&A 등) | 이벤트 후 **4영업일** | 수시 | ⭐ (2차 스코프) |

**⑥-A. Form 4 내부자 거래 수집 (매일 배치)**

```python
# pipeline/sources/edgar_insider.py

import requests
from datetime import datetime, timedelta

EDGAR_SUBMISSIONS = "https://data.sec.gov/submissions/CIK{cik}.json"
EDGAR_FULL_TEXT = "https://efts.sec.gov/LATEST/search-index?q=%22form-type%22%3A%224%22&dateRange=custom&startdt={start}&enddt={end}"
HEADERS = {"User-Agent": "StockScreener admin@example.com"}

def get_recent_form4_filings(cik: str) -> list[dict]:
    """
    특정 CIK(기업)의 최근 Form 4 파일링 목록 조회.
    data.sec.gov/submissions/ 엔드포인트 사용.
    """
    url = EDGAR_SUBMISSIONS.format(cik=cik.zfill(10))
    resp = requests.get(url, headers=HEADERS)
    data = resp.json()
    
    filings = data["filings"]["recent"]
    form4_indices = [
        i for i, form in enumerate(filings["form"])
        if form == "4"
    ]
    
    results = []
    for idx in form4_indices[:5]:  # 최근 5건
        results.append({
            "filing_date": filings["filingDate"][idx],
            "accession": filings["accessionNumber"][idx],
            "primary_doc": filings["primaryDocument"][idx],
        })
    return results


def parse_form4_xml(accession_url: str) -> dict | None:
    """
    Form 4 XML 파싱 → 내부자 이름, 직위, 거래 유형, 수량, 가격 추출.
    
    SEC Form 4 XML 구조:
    <ownershipDocument>
      <issuer>
        <issuerCik>0000320193</issuerCik>
        <issuerName>Apple Inc</issuerName>
        <issuerTradingSymbol>AAPL</issuerTradingSymbol>
      </issuer>
      <reportingOwner>
        <reportingOwnerId>
          <rptOwnerCik>...</rptOwnerCik>
          <rptOwnerName>Cook Timothy D</rptOwnerName>
        </reportingOwnerId>
        <reportingOwnerRelationship>
          <isDirector>false</isDirector>
          <isOfficer>true</isOfficer>
          <officerTitle>Chief Executive Officer</officerTitle>
        </reportingOwnerRelationship>
      </reportingOwner>
      <nonDerivativeTable>
        <nonDerivativeTransaction>
          <transactionDate><value>2026-03-25</value></transactionDate>
          <transactionCoding>
            <transactionCode>S</transactionCode>  <!-- A=매수, S=매도, M=옵션행사 -->
          </transactionCoding>
          <transactionAmounts>
            <transactionShares><value>75000</value></transactionShares>
            <transactionPricePerShare><value>178.50</value></transactionPricePerShare>
            <transactionAcquiredDisposedCode><value>D</value></transactionAcquiredDisposedCode>
          </transactionAmounts>
          <postTransactionAmounts>
            <sharesOwnedFollowingTransaction><value>3280000</value></sharesOwnedFollowingTransaction>
          </postTransactionAmounts>
        </nonDerivativeTransaction>
      </nonDerivativeTable>
    </ownershipDocument>
    """
    from lxml import etree
    
    try:
        resp = requests.get(accession_url, headers=HEADERS, timeout=10)
        resp.raise_for_status()
        root = etree.fromstring(resp.content)
        
        # 네임스페이스 제거 (SEC XML은 네임스페이스가 불규칙)
        for elem in root.iter():
            if '}' in str(elem.tag):
                elem.tag = elem.tag.split('}', 1)[1]
        
        # 내부자 정보
        owner = root.find('.//reportingOwner')
        insider_name = owner.findtext('.//rptOwnerName', '').strip()
        insider_title = (
            owner.findtext('.//officerTitle', '') or
            ('Director' if owner.findtext('.//isDirector') == 'true' else '')
        ).strip()
        
        # nonDerivativeTransaction에서 가장 큰 거래 추출
        txns = root.findall('.//nonDerivativeTransaction')
        if not txns:
            return None
        
        best_txn = None
        best_shares = 0
        for txn in txns:
            shares_str = txn.findtext('.//transactionShares/value', '0')
            shares = int(float(shares_str)) if shares_str else 0
            if shares > best_shares:
                best_shares = shares
                best_txn = txn
        
        if best_txn is None:
            return None
        
        # 거래 코드: A=Open market Acquisition, S=Sale, M=옵션행사
        txn_code = best_txn.findtext('.//transactionCode', '')
        ad_code = best_txn.findtext('.//transactionAcquiredDisposedCode/value', '')
        
        TXN_TYPE_MAP = {
            ('A', 'A'): 'Buy',   ('S', 'D'): 'Sale',
            ('M', 'A'): 'Option Exercise',  ('M', 'D'): 'Option Exercise',
        }
        txn_type = TXN_TYPE_MAP.get((txn_code, ad_code), f'{txn_code}-{ad_code}')
        
        price_str = best_txn.findtext('.//transactionPricePerShare/value', '0')
        price = float(price_str) if price_str else None
        
        shares_after_str = best_txn.findtext(
            './/sharesOwnedFollowingTransaction/value', '0'
        )
        shares_after = int(float(shares_after_str)) if shares_after_str else None
        
        txn_date = best_txn.findtext('.//transactionDate/value', '')
        
        return {
            "insider_name": insider_name,
            "insider_title": insider_title,
            "transaction_type": txn_type,
            "shares": best_shares,
            "price_per_share": price,
            "transaction_date": txn_date,
            "shares_owned_after": shares_after,
        }
    except Exception as e:
        logger.debug(f"[EDGAR] Form 4 파싱 실패 ({accession_url}): {e}")
        return None


def batch_collect_insider_trades(symbols_with_cik: dict[str, str]):
    """
    매일 배치: 시총 상위 1,000종목의 최근 Form 4 수집.
    
    속도: 1,000종목 × 1 req/종목 = 1,000 req
    Rate limit: 10 req/초 → ~100초 (2분 이내)
    """
    all_trades = []
    for symbol, cik in symbols_with_cik.items():
        filings = get_recent_form4_filings(cik)
        for f in filings:
            trade = parse_form4_xml(f["primary_doc"])
            if trade:
                trade["symbol"] = symbol
                all_trades.append(trade)
        time.sleep(0.15)  # 10 req/초 준수 (~6~7 req/초)
    
    return all_trades
```

**⑥-B. 13F 기관 보유 수집 (분기 배치)**

분기 종료(3/31, 6/30, 9/30, 12/31) 후 45일간 새 13F 파일링 감지.
매일 배치에서 "새 13F 접수 여부"만 체크 → 접수 감지 시 파싱.

```python
# pipeline/sources/edgar_13f.py

EDGAR_XBRL_FRAMES = "https://data.sec.gov/api/xbrl/frames/"

def check_new_13f_filings(date_from: str) -> list[dict]:
    """
    EDGAR full-text search로 최근 13F-HR 파일링 감지.
    분기 보고 시즌(2/5/8/11월)에만 유의미한 결과.
    
    13F-HR XML 구조 (informationTable):
    <informationTable>
      <infoTable>
        <nameOfIssuer>APPLE INC</nameOfIssuer>
        <titleOfClass>COM</titleOfClass>
        <cusip>037833100</cusip>
        <value>31063844</value>              <!-- 시장가치 (천 USD) -->
        <shrsOrPrnAmt>
          <sshPrnamt>173889064</sshPrnamt>   <!-- 보유 주식수 -->
          <sshPrnamtType>SH</sshPrnamtType>
        </shrsOrPrnAmt>
        <investmentDiscretion>SOLE</investmentDiscretion>
        <votingAuthority>
          <Sole>173889064</Sole>
          <Shared>0</Shared>
          <None>0</None>
        </votingAuthority>
      </infoTable>
      <!-- ... 수백~수천 개 종목 반복 ... -->
    </informationTable>
    
    파싱 전략:
    1) EDGAR EFTS(full-text search)로 최근 13F-HR 파일링 목록 조회
    2) 주요 기관(Berkshire Hathaway, BlackRock 등)의 파일링 우선 처리
    3) informationTable XML에서 CUSIP → 티커 매핑 (SEC company_tickers.json 활용)
    4) 종목별 기관 보유수 집계 → latest_equities.inst_holders_13f 갱신
    """
    from lxml import etree
    
    try:
        # 1) EDGAR full-text search API로 최근 13F-HR 파일링 목록 조회
        url = "https://efts.sec.gov/LATEST/search-index"
        params = {
            "q": '"form-type":"13F-HR"',
            "dateRange": "custom",
            "startdt": date_from,
            "enddt": datetime.now().strftime("%Y-%m-%d"),
        }
        resp = requests.get(url, headers=HEADERS, params=params, timeout=30)
        resp.raise_for_status()
        
        search_results = resp.json()
        filings = []
        
        for hit in search_results.get("hits", {}).get("hits", []):
            source = hit.get("_source", {})
            filings.append({
                "cik": source.get("entity_cik"),
                "entity_name": source.get("entity_name"),
                "filing_date": source.get("file_date"),
                "accession": source.get("accession_no"),
            })
        
        logger.info(f"[EDGAR 13F] {date_from} 이후 신규 파일링: {len(filings)}건")
        return filings
        
    except Exception as e:
        logger.warning(f"[EDGAR 13F] 파일링 조회 실패: {e}")
        return []


def parse_13f_holdings(accession_no: str, cik: str) -> list[dict]:
    """
    개별 13F-HR 파일링의 informationTable XML 파싱.
    CUSIP → 티커 매핑은 별도 load_cusip_mapping() 필요.
    
    Returns: [{"cusip": "037833100", "issuer": "APPLE INC",
               "shares": 173889064, "value_thousands": 31063844}, ...]
    """
    from lxml import etree
    
    try:
        # 13F informationTable URL 구성
        acc_clean = accession_no.replace("-", "")
        base_url = f"https://www.sec.gov/Archives/edgar/data/{cik}/{acc_clean}"
        
        # primary_doc에서 infotable XML 파일명 확인 후 다운로드
        # (실제 구현 시 filing index에서 infotable.xml 파일 탐색 필요)
        resp = requests.get(f"{base_url}/primary_doc.xml", headers=HEADERS, timeout=30)
        root = etree.fromstring(resp.content)
        
        # 네임스페이스 제거
        for elem in root.iter():
            if '}' in str(elem.tag):
                elem.tag = elem.tag.split('}', 1)[1]
        
        holdings = []
        for info in root.findall('.//infoTable'):
            cusip = info.findtext('cusip', '').strip()
            issuer = info.findtext('nameOfIssuer', '').strip()
            shares_str = info.findtext('.//sshPrnamt', '0')
            value_str = info.findtext('value', '0')
            
            holdings.append({
                "cusip": cusip,
                "issuer": issuer,
                "shares": int(shares_str) if shares_str else 0,
                "value_thousands": int(value_str) if value_str else 0,
            })
        
        return holdings
        
    except Exception as e:
        logger.debug(f"[EDGAR 13F] Holdings 파싱 실패 ({accession_no}): {e}")
        return []
```

**EDGAR 데이터 → DB 적재 전략**

| 데이터 | 적재 방식 | 대상 |
|--------|----------|------|
| Form 4 최신 거래 요약 | `latest_equities` 컬럼에 UPSERT | 전종목 (시총 상위 1,000) |
| Form 4 거래 상세 내역 | 별도 `insider_trades` 테이블 | 온디맨드 상세화면용 |
| 13F 기관 보유 | `latest_equities` 컬럼에 UPSERT | 전종목 |

**`insider_trades` 보조 테이블** (Form 4 상세 이력):

```sql
CREATE TABLE IF NOT EXISTS insider_trades (
    id              BIGSERIAL   PRIMARY KEY,
    symbol          TEXT        NOT NULL REFERENCES latest_equities(symbol),
    insider_name    TEXT        NOT NULL,
    insider_title   TEXT,                           -- CEO, CFO, Director 등
    txn_type        TEXT        NOT NULL,           -- 'Buy','Sale','Option Exercise'
    txn_date        DATE        NOT NULL,
    shares          BIGINT      NOT NULL,
    price           NUMERIC(12,4),
    total_value     NUMERIC(18,2),                  -- shares × price
    shares_after    BIGINT,                         -- 거래 후 보유 잔량
    filing_date     DATE        NOT NULL,           -- SEC 접수일
    accession_no    TEXT,                           -- EDGAR 고유 번호
    created_at      TIMESTAMPTZ DEFAULT now()
);

-- 인덱스: 종목별 최신 거래 조회
CREATE INDEX IF NOT EXISTS idx_insider_symbol_date
    ON insider_trades (symbol, txn_date DESC);

-- 인덱스: 최근 N일 내부자 매수 급증 탐지
CREATE INDEX IF NOT EXISTS idx_insider_type_date
    ON insider_trades (txn_type, txn_date DESC);

-- RLS
ALTER TABLE insider_trades ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Public read access" ON insider_trades FOR SELECT USING (true);
```

**`fmp_cache` 온디맨드 캐시 테이블** (FMP 일일 250 req 절약):

사용자가 종목 상세화면을 열 때 FMP 온디맨드 호출(grade, price-target-consensus, social-sentiment 등)의
결과를 DB에 캐시하여, 같은 종목·같은 엔드포인트를 여러 번 호출해도 FMP req를 1번만 소비.
Edge Function에서 캐시 히트 여부를 먼저 확인 → 미스 시에만 FMP 호출 → 결과 저장.

```sql
CREATE TABLE IF NOT EXISTS fmp_cache (
    symbol          TEXT        NOT NULL,
    endpoint        TEXT        NOT NULL,               -- 'grade' | 'price-target' | 'social-sentiment' | 'ratios'
    response_json   JSONB       NOT NULL,               -- FMP 응답 원본 (전체 저장)
    fetched_at      TIMESTAMPTZ NOT NULL DEFAULT now(),  -- 캐시 저장 시각
    
    PRIMARY KEY (symbol, endpoint)                      -- 종목+엔드포인트 조합 유니크
);

-- TTL 만료 캐시 자동 삭제 (매일 배치에서 실행, 또는 pg_cron)
-- 30분 이상 경과한 캐시를 삭제하여 항상 신선한 데이터 보장
CREATE INDEX IF NOT EXISTS idx_fmp_cache_ttl
    ON fmp_cache (fetched_at);

-- RLS: 읽기 공개, 쓰기는 Edge Function (service_role) 전용
ALTER TABLE fmp_cache ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Public read access" ON fmp_cache FOR SELECT USING (true);
```

**Edge Function 캐시 로직** (의사 코드):

```typescript
// supabase/functions/fmp-proxy/index.ts

const CACHE_TTL_MINUTES = 30;

serve(async (req) => {
  const { symbol, endpoint } = await req.json();
  
  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
  );
  
  // 1) 캐시 히트 확인
  const { data: cached } = await supabase
    .from('fmp_cache')
    .select('response_json, fetched_at')
    .eq('symbol', symbol)
    .eq('endpoint', endpoint)
    .single();
  
  if (cached) {
    const age = (Date.now() - new Date(cached.fetched_at).getTime()) / 60000;
    if (age < CACHE_TTL_MINUTES) {
      // 캐시 히트 → FMP 호출 안 함
      return new Response(JSON.stringify(cached.response_json));
    }
  }
  
  // 2) 캐시 미스 → FMP 호출
  const fmpUrl = `https://financialmodelingprep.com/api/v3/${endpoint}/${symbol}?apikey=${Deno.env.get("FMP_API_KEY")}`;
  const fmpResp = await fetch(fmpUrl);
  const fmpData = await fmpResp.json();
  
  // 3) 캐시 저장 (UPSERT)
  await supabase
    .from('fmp_cache')
    .upsert({
      symbol,
      endpoint,
      response_json: fmpData,
      fetched_at: new Date().toISOString(),
    }, { onConflict: 'symbol,endpoint' });
  
  return new Response(JSON.stringify(fmpData));
});
```

**캐시 정리**: 매일 배치 시작 시 30분 이상 경과한 캐시를 일괄 삭제:

```python
# loaders/supabase_upsert.py 에 추가

def cleanup_fmp_cache(supabase):
    """30분 이상 경과한 FMP 온디맨드 캐시 삭제."""
    supabase.table('fmp_cache').delete() \
        .lt('fetched_at', (datetime.utcnow() - timedelta(minutes=30)).isoformat()) \
        .execute()
```

**DB 용량 영향**: 캐시 테이블은 최대 ~1,000건 × 평균 2KB = ~2MB. TTL 삭제로 누적 안 됨.

**`latest_equities`에 추가할 EDGAR 집계 컬럼**:

배치에서 Form 4 + 13F 원천 데이터를 파싱한 뒤, 스크리너 필터용 집계값을 산출하여 메인 테이블에 UPSERT.

| 컬럼 | 산출 방식 | 용도 |
|------|----------|------|
| `insider_buy_3m` | 최근 90일 내부자 **순매수** 건수 | 필터: "내부자 매수 급증" |
| `insider_sell_3m` | 최근 90일 내부자 **순매도** 건수 | |
| `insider_net_shares_3m` | 최근 90일 순매수 주식수 (매수-매도) | 정렬 |
| `insider_latest_date` | 가장 최근 내부자 거래일 | 표시 |
| `insider_latest_type` | 가장 최근 거래 유형 ('Buy'/'Sale') | 배지 |
| `inst_holders_13f` | 13F 기관투자자 수 (EDGAR 원천) | yfinance `inst_pct` 보강 |
| `edgar_updated_at` | EDGAR 데이터 마지막 갱신 시각 | 메타 |

**배치 실행 시간 추정**:

```
Form 4 수집 (상위 1,000종목):
  1,000 req ÷ 7 req/초 = ~143초 (~2.5분)

13F 체크 (분기 시즌만):
  분기 보고 시즌: ~500 req (~1분)
  비시즌: 스킵

합계: 매일 배치에 ~3분 추가
GitHub Actions 총 예상: ~45분/일 (미국 공휴일 스킵, 기존 28분 + EDGAR 3분 + 심화재무 17분 + 기술적 5분)
```

**CIK 매핑 (티커 → CIK)**:

SEC EDGAR는 티커가 아닌 CIK(Central Index Key)로 조회. 매핑 방법:

```python
# SEC 공식 CIK-티커 매핑 (1회 다운로드, 캐시)
CIK_MAPPING_URL = "https://www.sec.gov/files/company_tickers.json"

def load_cik_mapping() -> dict[str, str]:
    """
    SEC 공식 티커→CIK 매핑 파일 다운로드.
    {ticker: cik_str} 딕셔너리 반환.
    ~15,000건, 파일 크기 ~1MB, 1 req.
    """
    resp = requests.get(CIK_MAPPING_URL, headers=HEADERS)
    data = resp.json()
    return {
        v["ticker"]: str(v["cik_str"])
        for v in data.values()
    }
```

#### ⑦ 소셜 센티먼트 — FMP 온디맨드 + X Sentiment API 배치

Reddit/StockTwits/X(Twitter) 등
**소셜 미디어 기반** 센티먼트를 수집. 두 가지 소스를 병용.

**⑦-A. FMP Social Sentiment (온디맨드, 상세화면용)**

사용자가 종목 상세 → 애널리스트 탭 진입 시 FMP 1건 호출.

| 항목 | 값 |
|------|-----|
| 엔드포인트 | `/api/v4/social-sentiment?symbol=AAPL` |
| 소스 | Reddit, Yahoo, StockTwits, Twitter |
| 갱신 | **매시간** |
| 비용 | $0 (FMP Free 250 req/day 공유) |
| 적재 | **`fmp_cache` 테이블에 캐시** (TTL 30분) |

반환 데이터:
```json
{
  "date": "2026-03-28 14:00:00",
  "symbol": "AAPL",
  "stocktwitsPosts": 142,
  "twitterPosts": 891,
  "stocktwitsComments": 67,
  "twitterComments": 1203,
  "stocktwitsLikes": 834,
  "twitterLikes": 9421,
  "stocktwitsSentiment": 0.62,    // 0~1, 1=매우 긍정
  "twitterSentiment": 0.58
}
```

→ 상세화면 애널리스트 탭 하단에 "소셜 버즈" 섹션으로 표시.
→ FMP 한도와 공유하지만, 사용자가 상세화면을 열 때만 소비되므로 일일 영향 미미.

**⑦-B. X Stock Sentiment API (주간 배치, 상위 250종목) — ⚠️ Nice-to-Have**

> **데이터 등급: 정보성(Nice-to-Have)**
> 이 데이터는 투자 판단의 보조 참고용이며, 수집 실패·API 서비스 중단 시에도
> 앱의 핵심 기능(스크리너 필터, 정렬, 종합 점수, 프리셋 시그널)에 **영향 없음**.
> 소셜 관련 컬럼이 NULL이면 UI에서 해당 섹션을 "데이터 없음"으로 표시.

별도 무료 API로 X(Twitter) 트렌딩 + 센티먼트를 DB에 적재.

| 항목 | 값 |
|------|-----|
| URL | `https://adanos.org/x-stock-sentiment` |
| 인증 | API Key (무료 발급) |
| Free 한도 | **250 req/월**, 100 req/분 |
| 데이터 | 트렌딩 티커, 센티먼트 점수, Bullish/Bearish %, Reddit 교차 검증 |
| 갱신 | 매시간 (소스 측) |
| **안정성** | **미보장 (무료 서드파티, SLA 없음)** |
| **장애 시 영향** | **없음 — 소셜 컬럼 NULL 유지, 나머지 배치 정상 진행** |

**배치 전략**: 월 250 req → 주 1회 상위 250종목 = **250 req/월** (한도 정확히 맞춤)

**헬스체크 & 장애 감지**:

배치 실행 시 API 가용성을 먼저 확인. 장애 감지 시 해당 단계만 스킵하고
배치 로그에 경고를 남겨 GitHub Actions Summary에서 확인 가능.
연속 3회 이상 실패 시 이메일 알림 발송 + 워크플로 어노테이션(⚠️) 표시.

```python
# pipeline/sources/x_sentiment.py

import requests
import logging
from datetime import date, datetime

logger = logging.getLogger(__name__)

X_SENTIMENT_BASE = "https://adanos.org/x-stock-sentiment/v1"
HEADERS = {"X-API-Key": "YOUR_X_SENTIMENT_KEY"}

# ── 헬스체크 설정 ──────────────────────────────
HEALTH_TIMEOUT = 10  # 초
MAX_CONSECUTIVE_FAILURES = 3  # 연속 N회 실패 시 경고 레벨 상향


def check_api_health() -> bool:
    """
    X Sentiment API 가용성 사전 확인.
    /trending 엔드포인트를 가벼운 probe로 사용 (1 req 소비).
    """
    try:
        resp = requests.get(
            f"{X_SENTIMENT_BASE}/trending",
            headers=HEADERS,
            timeout=HEALTH_TIMEOUT
        )
        if resp.status_code == 200:
            logger.info("[XSentiment] API 정상 (200 OK)")
            return True
        else:
            logger.warning(f"[XSentiment] API 비정상 응답: HTTP {resp.status_code}")
            return False
    except requests.exceptions.ConnectionError:
        logger.warning("[XSentiment] API 연결 불가 — 서비스 중단 가능성")
        return False
    except requests.exceptions.Timeout:
        logger.warning(f"[XSentiment] API 응답 없음 ({HEALTH_TIMEOUT}초 타임아웃)")
        return False
    except Exception as e:
        logger.warning(f"[XSentiment] API 헬스체크 예외: {e}")
        return False


def get_stock_sentiment(ticker: str):
    """개별 종목 센티먼트 상세 (1 req)"""
    try:
        resp = requests.get(
            f"{X_SENTIMENT_BASE}/stock/{ticker}",
            headers=HEADERS,
            timeout=10
        )
        resp.raise_for_status()
        return resp.json()
    except Exception as e:
        logger.debug(f"[XSentiment] {ticker} 수집 실패: {e}")
        return None


def batch_scrape_weekly(top_symbols: list[str], supabase) -> dict:
    """
    주간 배치: 상위 250종목 센티먼트 수집.
    
    Returns:
        {"status": "success"|"skipped"|"partial",
         "collected": int, "failed": int, "reason": str}
    """
    # 1) 헬스체크 — 실패 시 이 단계 전체 스킵
    if not check_api_health():
        consecutive = _get_consecutive_failures(supabase)
        _set_consecutive_failures(supabase, consecutive + 1)
        
        msg = (f"[XSentiment] API 불가 — 소셜 센티먼트 수집 스킵 "
               f"(연속 {consecutive + 1}회 실패)")
        
        if consecutive + 1 >= MAX_CONSECUTIVE_FAILURES:
            # GitHub Actions 워크플로 경고 어노테이션
            print(f"::warning::{msg} — API 서비스 중단 가능성, 수동 확인 필요")
        else:
            logger.warning(msg)
        
        return {"status": "skipped", "collected": 0, "failed": 0, "reason": msg}
    
    # 2) 헬스체크 통과 → 연속 실패 카운터 리셋
    _set_consecutive_failures(supabase, 0)
    
    # 3) 데이터 수집
    results = []
    failed = 0
    for sym in top_symbols[:250]:
        data = get_stock_sentiment(sym)
        if data:
            results.append({
                "symbol": sym,
                "social_score": data.get("sentiment_score"),
                "social_bullish_pct": data.get("bullish_pct"),
                "social_mentions_24h": data.get("mention_count_24h"),
                "social_reddit_validated": data.get("is_validated"),
                "asof_social": date.today().isoformat(),
            })
        else:
            failed += 1
    
    logger.info(f"[XSentiment] 수집 완료: {len(results)}건 성공, {failed}건 실패")
    return {"status": "success" if failed == 0 else "partial",
            "collected": len(results), "failed": failed, "reason": ""}


# ── 연속 실패 카운터 (fmp_cache 테이블 재활용) ──
def _get_consecutive_failures(supabase) -> int:
    try:
        r = supabase.table('fmp_cache') \
            .select('response_json') \
            .eq('symbol', '_META_') \
            .eq('endpoint', 'x_sentiment_health') \
            .single().execute()
        return r.data['response_json'].get('consecutive_failures', 0)
    except:
        return 0

def _set_consecutive_failures(supabase, count: int):
    supabase.table('fmp_cache').upsert({
        'symbol': '_META_',
        'endpoint': 'x_sentiment_health',
        'response_json': {'consecutive_failures': count},
        'fetched_at': datetime.utcnow().isoformat(),
    }, on_conflict='symbol,endpoint').execute()
```

**GitHub Actions 워크플로**: 별도 주간 워크플로로 실행.
월 250 req 한도 → **매주 62~63종목씩 4주 순환** 또는 **월 1회 250종목 일괄**.
API 장애 시 `::warning::` 어노테이션이 Actions Summary에 표시되며,
연속 3회 이상 실패 시 이메일 알림(`musiqq86@gmail.com`) + **⚠️ 수동 확인 필요** 경고.

#### ⑧ 심화 재무 지표 — 영업이익 증가율, 차입금 증가율, 이자보상배율

yfinance `Ticker.info`에는 없고, **분기 재무제표를 직접 파싱**해서 산출하는 지표.

| 지표 | 산출 공식 | 원천 |
|------|----------|------|
| 영업이익 증가율 | (금분기 영업이익 - 전년동기) / abs(전년동기) | `quarterly_income_stmt` → `Operating Income` |
| 차입금 증가율 | (금분기 총차입금 - 전년동기) / abs(전년동기) | `quarterly_balance_sheet` → `Total Debt` |
| 이자보상배율 | 영업이익(TTM) / 이자비용(TTM) | `income_stmt` → `Operating Income` / `Interest Expense` |

**배치 전략**: 상위 500종목만 DB 적재 (필터/정렬 가능), 나머지는 상세화면 온디맨드.

```python
# pipeline/sources/deep_financials.py

import yfinance as yf
import time

def compute_deep_financials(symbol: str) -> dict | None:
    """
    yfinance 재무제표에서 영업이익 증가율, 차입금 증가율, 이자보상배율 산출.
    종목당 ~2초 소요 (3개 재무제표 호출).
    """
    try:
        ticker = yf.Ticker(symbol)
        
        # 분기 손익계산서 (최근 4분기)
        qi = ticker.quarterly_income_stmt
        if qi is None or qi.empty:
            return None
        
        # 분기 대차대조표
        qb = ticker.quarterly_balance_sheet
        
        # --- 영업이익 증가율 (YoY) ---
        op_income_growth = None
        if 'Operating Income' in qi.index and qi.shape[1] >= 5:
            current = qi.loc['Operating Income'].iloc[0]   # 최근 분기
            yoy = qi.loc['Operating Income'].iloc[4]       # 전년 동기
            if yoy and abs(yoy) > 0:
                op_income_growth = round((current - yoy) / abs(yoy), 4)
        
        # --- 차입금 증가율 (YoY) ---
        debt_growth = None
        if qb is not None and 'Total Debt' in qb.index and qb.shape[1] >= 5:
            current_debt = qb.loc['Total Debt'].iloc[0]
            yoy_debt = qb.loc['Total Debt'].iloc[4]
            if yoy_debt and abs(yoy_debt) > 0:
                debt_growth = round((current_debt - yoy_debt) / abs(yoy_debt), 4)
        
        # --- 이자보상배율 (TTM) ---
        interest_coverage = None
        annual = ticker.income_stmt
        if annual is not None and not annual.empty:
            if 'Operating Income' in annual.index and 'Interest Expense' in annual.index:
                op_ttm = annual.loc['Operating Income'].iloc[0]
                int_exp = annual.loc['Interest Expense'].iloc[0]
                if int_exp and abs(int_exp) > 0:
                    interest_coverage = round(op_ttm / abs(int_exp), 2)
        
        # --- 추가 심화 지표 (TODO: 산출 로직 구현) ---
        roic = None                # NOPAT / 투하자본
        lt_debt_equity = None      # 장기부채 / 자기자본
        pcash_ratio = None         # 주가 / 주당현금
        eps_growth_past_5y = None  # EPS 5년 CAGR
        sales_growth_past_5y = None  # 매출 5년 CAGR
        piotroski_score = None     # F-Score (9개 기준)
        altman_z_score = None      # Z-Score (5개 재무비율)
        
        # 기준일: 최근 분기말
        asof = qi.columns[0].strftime('%Y-%m-%d') if hasattr(qi.columns[0], 'strftime') else None
        
        return {
            "symbol": symbol,
            "op_income_growth": op_income_growth,
            "debt_growth": debt_growth,
            "interest_coverage": interest_coverage,
            "roic": roic,
            "lt_debt_equity": lt_debt_equity,
            "pcash_ratio": pcash_ratio,
            "eps_growth_past_5y": eps_growth_past_5y,
            "sales_growth_past_5y": sales_growth_past_5y,
            "piotroski_score": piotroski_score,
            "altman_z_score": altman_z_score,
            "asof_deep_financial": asof,
        }
    except Exception as e:
        print(f"[DeepFinancial] {symbol} 실패: {e}")
        return None


def batch_deep_financials(top_symbols: list[str]):
    """
    상위 500종목 심화 재무 배치.
    500종목 × ~2초 = ~17분 (기존 배치에 추가)
    GitHub Actions 총 예상: ~40분/일
    """
    results = []
    for i, sym in enumerate(top_symbols[:500]):
        data = compute_deep_financials(sym)
        if data:
            results.append(data)
        time.sleep(1)  # rate limit 보호
        if (i + 1) % 100 == 0:
            print(f"[DeepFinancial] {i+1}/500 완료")
    return results
```

**온디맨드 (상세화면, 상위 500 외 종목)**:

상위 500 외 종목의 상세화면 → 재무 탭 진입 시, Edge Function에서 FMP `/stable/ratios?symbol=XXX` 1건 호출.
FMP ratios 엔드포인트에 `interestCoverage` 포함. `fmp_cache` 테이블에 TTL 30분 캐시.

**배치 실행 시간 영향**:

```
기존 배치:       ~23분
+ 심화 재무:     +17분 (500종목 × 2초, ROIC/5Y성장률/Inst.Txn 포함)
+ 기술적 지표:   +5분  (가격 히스토리 다운로드 → RSI/ATR/변동성/MA괴리 일괄 산출)
+ Performance:   +0분  (⑨ yfinance 히스토리 데이터 재활용)
합계:            ~45분/일 (미국 공휴일 자동 스킵)
GitHub Actions:  월 ~900분 / 2,000분 한도 (45%, 공휴일 ~9일 절약)
```

**DB 적재 컬럼** (`latest_equities`에 추가):

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `social_score` | `SMALLINT` | X Sentiment 종합 점수 (0~100) |
| `social_bullish_pct` | `NUMERIC(6,4)` | Bullish 비율 (0.68 = 68%) |
| `social_mentions_24h` | `INTEGER` | 24시간 언급 수 |
| `social_reddit_validated` | `BOOLEAN` | Reddit 교차 트렌딩 여부 |
| `asof_social` | `DATE` | 소셜 센티먼트 기준일 |

### 2.3 데이터 신선도(Freshness) 매트릭스

| 데이터 종류 | 갱신 주기 | 소스 |
|------------|----------|------|
| 주가·거래량·시총 | **매일** (MOC 후) | yfinance + FMP |
| PER·EPS·배당 | **매일** | yfinance + FMP |
| 52주 고가/저가 | **매일** | yfinance (벤더 산출) |
| 50일/200일 MA | **매일** | yfinance (벤더 산출) |
| 섹터·산업 | **주 1회** | yfinance |
| 애널리스트 컨센서스 (목표가·의견) | **매일** | yfinance (Ticker.info) |
| 심화 재무 (영업이익증가율/차입금증가율/이자보상배율/ROIC/5Y/Inst.Txn, 상위 500) | **매일** | yfinance 재무제표 파싱 |
| 기술적 확장 (RSI/ATR/변동성/MA괴리/갭/상대거래량) | **매일** | yfinance 가격 히스토리 산출 |
| 성과 (1주/1월/3월/6월/1년/YTD 수익률) | **매일** | yfinance 가격 히스토리 산출 |
| 내부자 거래 (Form 4) | **매일** | SEC EDGAR 직접 (공시 후 1초 내) |
| 기관 보유 (13F) | **분기** (접수 즉시) | SEC EDGAR 직접 |
| 대량 보유 변동 (13D/13G) | **수시** (접수 즉시) | SEC EDGAR 직접 |
| 소셜 센티먼트 (상위 250) | **주 1회** | X Stock Sentiment API |
| 소셜 버즈 상세 (온디맨드) | **실시간** (매시간) | FMP Social Sentiment |
| ETF 수수료·메타 | **주 1회** | FMP |
| ETF Holdings (Tier 1) | **매일** | FMP |
| ETF Holdings (Tier 2) | **주 2회** | FMP |
| ETF Holdings (Tier 3) | **주 1회** | FMP |

---

## 3. 데이터베이스 설계 (PostgreSQL on Supabase)

### 3.1 스토리지 예산 (500MB 한도)

| 항목 | 예상 크기 |
|------|----------|
| latest_equities 데이터 (15,000행 × ~1.2KB) | ~18 MB |
| JSONB Holdings (2,800 ETF × 평균 5KB) | ~14 MB |
| B-Tree 인덱스 (10개) | ~45 MB |
| GIN 인덱스 (3개) | ~50 MB |
| pg_trgm 인덱스 (2개) | ~20 MB |
| WAL·시스템 오버헤드 | ~50 MB |
| **합계** | **~197 MB** |
| **여유** | **~303 MB (61%)** |

### 3.2 DDL — `latest_equities` 테이블

```sql
-- =============================================================
-- 확장 모듈 활성화 (Supabase SQL Editor에서 1회 실행)
-- =============================================================
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- =============================================================
-- 단일 스냅샷 테이블
-- =============================================================
CREATE TABLE IF NOT EXISTS latest_equities (

    -- ── 식별자 ──
    symbol          TEXT        PRIMARY KEY,            -- 티커 (PK = 자동 Unique B-Tree)
    name            TEXT        NOT NULL,               -- 종목명 (영문 원문)
    
    -- ── 분류 ──
    asset_type      TEXT        NOT NULL DEFAULT 'stock',-- 'stock' | 'etf'
    exchange        TEXT,                               -- 'NYSE' | 'NASDAQ' | 'AMEX'
    sector          TEXT,                               -- 섹터 (GICS)
    industry        TEXT,                               -- 산업 (GICS)
    
    -- ── 가격 ──
    price           NUMERIC(12,4),                      -- 최신 종가 (USD)
    open_price      NUMERIC(12,4),                      -- 시가
    day_high        NUMERIC(12,4),                      -- 일중 최고
    day_low         NUMERIC(12,4),                      -- 일중 최저
    prev_close      NUMERIC(12,4),                      -- 전일 종가
    change_pct      NUMERIC(8,4),                       -- 등락률 (%, 산출값)
    
    -- ── 거래량 ──
    volume          BIGINT,                             -- 당일 거래량
    avg_volume_10d  BIGINT,                             -- 10일 평균 거래량 (벤더 산출)
    turnover        NUMERIC(18,2),                      -- 거래대금 (price × volume, 배치에서 산출)
    
    -- ── 밸류에이션 ──
    market_cap      BIGINT,                             -- 시가총액 (USD)
    pe_ttm          NUMERIC(10,2),                      -- PER (TTM)
    pe_forward      NUMERIC(10,2),                      -- Forward PER
    pb_ratio        NUMERIC(10,2),                      -- PBR
    eps_ttm         NUMERIC(10,2),                      -- EPS (TTM)
    
    -- ── 배당 ──
    dividend_yield  NUMERIC(8,4),                       -- 배당률 (소수: 0.0065 = 0.65%)
    dividend_rate   NUMERIC(10,4),                      -- 연간 배당금 (USD)
    ex_dividend_date DATE,                              -- 배당락일 (exDividendDate)
    avg_dividend_yield_5y NUMERIC(8,4),                 -- 5년 평균 배당률 (fiveYearAvgDividendYield)
    
    -- ── 기술적 지표 (벤더 산출, 직접 연산 금지) ──
    week52_high     NUMERIC(12,4),                      -- 52주 최고가
    week52_low      NUMERIC(12,4),                      -- 52주 최저가
    pct_from_52h    NUMERIC(8,4),                       -- 52주 고점 대비 (%, 배치에서 산출)
    pct_from_52l    NUMERIC(8,4),                       -- 52주 저점 대비 (%)
    ma_50           NUMERIC(12,4),                      -- 50일 이동평균 (벤더 산출)
    ma_200          NUMERIC(12,4),                      -- 200일 이동평균 (벤더 산출)
    beta            NUMERIC(8,4),                       -- 베타
    
    -- ── 재무 건전성 (yfinance Ticker.info, Finviz 대응) ──
    roe             NUMERIC(10,4),                      -- 자기자본이익률 (returnOnEquity)
    roa             NUMERIC(10,4),                      -- 총자산이익률 (returnOnAssets)
    debt_to_equity  NUMERIC(10,2),                      -- 부채비율 (debtToEquity)
    current_ratio   NUMERIC(10,2),                      -- 유동비율 (currentRatio)
    operating_margin NUMERIC(10,4),                     -- 영업이익률 (operatingMargins)
    profit_margin   NUMERIC(10,4),                      -- 순이익률 (profitMargins)
    gross_margin    NUMERIC(10,4),                      -- 매출총이익률 (grossMargins)
    revenue_growth  NUMERIC(10,4),                      -- 매출 성장률 YoY (revenueGrowth)
    earnings_growth NUMERIC(10,4),                      -- EPS 성장률 YoY (earningsGrowth)
    peg_ratio       NUMERIC(10,2),                      -- PEG Ratio (pegRatio)
    ps_ratio        NUMERIC(10,2),                      -- P/S 주가매출비율 (priceToSalesTrailing12Months)
    pfcf_ratio      NUMERIC(10,2),                      -- P/FCF 주가잉여현금흐름비율 (priceToFreeCashflows)
    pcash_ratio     NUMERIC(10,2),                      -- P/Cash 주가현금비율 (재무제표 산출)
    ev              BIGINT,                             -- Enterprise Value (enterpriseValue)
    ev_ebitda       NUMERIC(10,2),                      -- EV/EBITDA (enterpriseToEbitda)
    ev_revenue      NUMERIC(10,2),                      -- EV/Revenue (enterpriseToRevenue)
    fcf_yield       NUMERIC(10,4),                      -- FCF Yield (freeCashflow / marketCap, 배치 산출)
    
    -- ── 실적 절대값 (yfinance Ticker.info) ──
    total_revenue   BIGINT,                             -- 매출액 TTM (totalRevenue)
    ebitda          BIGINT,                             -- EBITDA (ebitda)
    free_cashflow   BIGINT,                             -- 잉여현금흐름 (freeCashflow)
    total_cash      BIGINT,                             -- 현금 및 현금성자산 (totalCash)
    total_debt      BIGINT,                             -- 총부채 (totalDebt)
    book_value      NUMERIC(12,4),                      -- 장부가치/주 (bookValue)
    revenue_per_share NUMERIC(12,4),                    -- 매출/주 (revenuePerShare)
    
    -- ── 심화 재무 (yfinance 재무제표 파싱, 상위 500 배치 + 나머지 온디맨드) ──
    op_income_growth NUMERIC(10,4),                     -- 영업이익 증가율 YoY (직접 산출)
    debt_growth      NUMERIC(10,4),                     -- 차입금 증가율 YoY (직접 산출)
    interest_coverage NUMERIC(10,2),                    -- 이자보상배율 (영업이익 ÷ 이자비용)
    roic            NUMERIC(10,4),                      -- 투하자본수익률 ROIC (직접 산출)
    quick_ratio     NUMERIC(10,2),                      -- 당좌비율 (quickRatio)
    lt_debt_equity  NUMERIC(10,2),                      -- 장기부채비율 (재무제표 산출)
    payout_ratio    NUMERIC(10,4),                      -- 배당성향 (payoutRatio)
    eps_growth_this_yr NUMERIC(10,4),                   -- EPS 성장률 금년 추정 (earningsGrowth or FMP)
    eps_growth_past_5y NUMERIC(10,4),                   -- EPS 연평균 성장률 과거 5년 (재무제표 산출)
    sales_growth_past_5y NUMERIC(10,4),                 -- 매출 연평균 성장률 과거 5년 (재무제표 산출)
    inst_transactions_pct NUMERIC(8,4),                 -- 기관 보유 변동률 (13F 분기 비교)
    earnings_surprise_pct NUMERIC(8,4),                 -- 직전 분기 Earnings Surprise (%)
    piotroski_score  SMALLINT,                          -- Piotroski F-Score (0~9, FMP 또는 직접 산출)
    altman_z_score   NUMERIC(8,2),                      -- Altman Z-Score (부도 위험, 직접 산출)
    asof_deep_financial DATE,                           -- 심화 재무 기준일 (분기말)
    
    -- ── 기술적 지표 확장 (배치에서 가격 히스토리 기반 산출) ──
    rsi_14          NUMERIC(8,2),                       -- RSI(14)
    atr_14          NUMERIC(12,4),                      -- ATR(14) 평균진폭
    volatility_w    NUMERIC(8,4),                       -- 주간 변동성 (%)
    volatility_m    NUMERIC(8,4),                       -- 월간 변동성 (%)
    ma_20           NUMERIC(12,4),                      -- 20일 이동평균
    sma20_pct       NUMERIC(8,4),                       -- 현재가 vs 20일MA 괴리 (%)
    sma50_pct       NUMERIC(8,4),                       -- 현재가 vs 50일MA 괴리 (%)
    sma200_pct      NUMERIC(8,4),                       -- 현재가 vs 200일MA 괴리 (%)
    gap_pct         NUMERIC(8,4),                       -- 갭 (%) = (시가-전일종가)/전일종가
    change_from_open NUMERIC(8,4),                      -- 시가대비등락 (%) = (종가-시가)/시가
    relative_volume NUMERIC(8,2),                       -- 상대거래량 = volume / avg_volume_10d
    
    -- ── 모멘텀/추세 지표 (가격 히스토리 기반 산출) ──
    macd            NUMERIC(10,4),                      -- MACD Line (EMA12 - EMA26)
    macd_signal     NUMERIC(10,4),                      -- MACD Signal Line (EMA9 of MACD)
    macd_hist       NUMERIC(10,4),                      -- MACD Histogram (MACD - Signal)
    stoch_k         NUMERIC(8,2),                       -- Stochastic %K (14일)
    stoch_d         NUMERIC(8,2),                       -- Stochastic %D (3일 SMA of %K)
    adx_14          NUMERIC(8,2),                       -- ADX (14) 추세 강도
    cci_20          NUMERIC(10,2),                      -- CCI (20) Commodity Channel Index
    williams_r      NUMERIC(8,2),                       -- Williams %R (14일)
    
    -- ── 밸류에이션 모델 (배치 산출 또는 FMP) ──
    dcf_value       NUMERIC(12,4),                      -- DCF 내재가치 (FMP /stable/dcf)
    dcf_upside_pct  NUMERIC(8,4),                       -- DCF 괴리율 (%) = (DCF-현재가)/현재가
    graham_number   NUMERIC(12,4),                      -- Graham Number = √(22.5 × EPS × BVPS)
    graham_upside_pct NUMERIC(8,4),                     -- Graham 괴리율 (%) = (Graham-현재가)/현재가
    
    -- ── 수익 품질 (상위 500 배치) ──
    accruals_ratio  NUMERIC(8,4),                       -- Accruals Ratio = (NI - OCF) / TA
    fcf_to_ni       NUMERIC(8,2),                       -- FCF / Net Income (>1 = 양질)
    earnings_quality_score SMALLINT,                    -- 수익 품질 점수 (0~100, 백분위)
    
    -- ── 자본 효율성 (상위 500 배치) ──
    buyback_yield   NUMERIC(8,4),                       -- 자사주매입 수익률 (매입액/시총)
    shareholder_yield NUMERIC(8,4),                     -- 총주주환원율 (배당률 + 자사주매입률)
    capex_to_revenue NUMERIC(8,4),                      -- CAPEX/매출 비율
    
    -- ── 자체 종합 점수 (백분위 기반, 배치에서 전종목 산출) ──
    score_value     SMALLINT,                           -- 밸류 스코어 (0~100)
    score_quality   SMALLINT,                           -- 퀄리티 스코어 (0~100)
    score_momentum  SMALLINT,                           -- 모멘텀 스코어 (0~100)
    score_growth    SMALLINT,                           -- 성장 스코어 (0~100)
    score_total     SMALLINT,                           -- 종합 스코어 (0~100, 가중 평균)
    
    -- ── 성과 (Performance, yfinance 가격 히스토리 산출) ──
    perf_1w         NUMERIC(8,4),                       -- 1주 수익률 (%)
    perf_1m         NUMERIC(8,4),                       -- 1개월 수익률 (%)
    perf_3m         NUMERIC(8,4),                       -- 3개월 수익률 (%)
    perf_6m         NUMERIC(8,4),                       -- 6개월 수익률 (%)
    perf_1y         NUMERIC(8,4),                       -- 1년 수익률 (%)
    perf_ytd        NUMERIC(8,4),                       -- YTD 수익률 (%)
    
    -- ── 기타 Finviz 대응 ──
    country         TEXT,                               -- 소재국 (US, CN, IE 등)
    earnings_date   DATE,                               -- 차기 실적발표일
    has_options     BOOLEAN,                            -- 옵션 거래 가능 여부
    index_membership TEXT,                              -- 인덱스 소속 (S&P500, DJIA, NASDAQ100 등)
    
    -- ── 수급 지표 (yfinance Ticker.info, Finviz 대응) ──
    shares_outstanding BIGINT,                          -- 발행주식수 (sharesOutstanding)
    float_shares    BIGINT,                             -- 유통주식수 (floatShares)
    shares_short    BIGINT,                             -- 공매도 주식수 (sharesShort)
    short_ratio     NUMERIC(8,2),                       -- 공매도비율/days to cover (shortRatio)
    short_pct_float NUMERIC(8,4),                       -- 유통주식 대비 공매도 비율 (shortPercentOfFloat)
    insider_pct     NUMERIC(8,4),                       -- 내부자 보유 비율 (heldPercentInsiders)
    inst_pct        NUMERIC(8,4),                       -- 기관 보유 비율 (heldPercentInstitutions)
    
    -- ── ETF 전용 필드 ──
    expense_ratio   NUMERIC(8,4),                       -- 운용수수료 (%, 0.0003 = 0.03%)
    aum             BIGINT,                             -- 순자산총액 (USD)
    nav             NUMERIC(12,4),                      -- NAV
    holdings_count  INTEGER,                            -- 구성종목 수
    index_tracked   TEXT,                               -- 추종 지수명
    asset_class     TEXT,                               -- 자산군 (Equity, Bond, Commodity 등)
    is_active       BOOLEAN     DEFAULT false,          -- 액티브 운용 여부
    inception_date  DATE,                               -- 설정일
    
    -- ── ETF Holdings (JSONB 역정규화) ──
    holdings        JSONB,                              -- 구성종목 배열
    -- 예시: [{"s":"AAPL","w":7.15,"v":31063844227}, {"s":"MSFT","w":6.82,...}]
    -- s = symbol, w = weight(%), v = marketValue
    
    -- ── 애널리스트 컨센서스 (yfinance Layer 1, 매일) ──
    analyst_rating       TEXT,                           -- 투자의견 ('buy','hold','sell' 등)
    analyst_rating_score NUMERIC(4,2),                   -- 의견 점수 (1=Strong Buy ~ 5=Sell)
    target_mean          NUMERIC(12,4),                  -- 평균 목표가 (USD)
    target_high          NUMERIC(12,4),                  -- 최고 목표가
    target_low           NUMERIC(12,4),                  -- 최저 목표가
    analyst_count        INTEGER,                        -- 커버 애널리스트 수
    target_upside_pct    NUMERIC(8,4),                   -- 목표가 괴리율 (%, 배치 산출)
    
    -- ── SEC EDGAR 집계 (매일 배치, Form 4 + 13F 원천) ──
    insider_buy_3m       SMALLINT    DEFAULT 0,          -- 최근 90일 내부자 순매수 건수
    insider_sell_3m      SMALLINT    DEFAULT 0,          -- 최근 90일 내부자 순매도 건수
    insider_net_shares_3m BIGINT     DEFAULT 0,          -- 최근 90일 순매수 주식수 (매수-매도)
    insider_latest_date  DATE,                           -- 가장 최근 내부자 거래일
    insider_latest_type  TEXT,                           -- 가장 최근 거래 유형 ('Buy'/'Sale')
    inst_holders_13f     INTEGER,                        -- 13F 기관투자자 수 (EDGAR 원천)
    edgar_updated_at     TIMESTAMPTZ,                    -- EDGAR 배치 실행 시각
    
    -- ── 소셜 센티먼트 (X Sentiment API, 주 1회, 상위 250종목) ──
    social_score         SMALLINT,                       -- X Sentiment 종합 점수 (0~100)
    social_bullish_pct   NUMERIC(6,4),                   -- Bullish 비율 (0.68)
    social_mentions_24h  INTEGER,                        -- 24시간 언급 수
    social_reddit_validated BOOLEAN,                     -- Reddit 교차 트렌딩 여부
    asof_social          DATE,                           -- 소셜 센티먼트 기준일
    
    -- ── 필드별 데이터 기준일 (as-of date) ──
    -- 배치 실행일과 데이터 기준일은 다름.
    -- 예: 3/30 배치에서 공매도를 가져왔지만, FINRA 기준일은 3/15일 수 있음.
    -- UI에서 "이 데이터는 X일 기준입니다"로 표시하기 위한 컬럼.
    
    asof_price       DATE,           -- 가격·거래량·시총·등락률 기준일 (= 장 마감일)
    asof_financial   DATE,           -- 재무지표 기준일 (ROE/마진/성장률 등, = 분기말)
    asof_valuation   DATE,           -- PER/PBR/EPS 기준일 (= 최신 분기 실적 반영일)
    asof_dividend    DATE,           -- 배당 기준일 (= 최근 배당 공시일)
    asof_technical   DATE,           -- 52주고저/MA/베타 기준일 (= 장 마감일, price와 동일)
    asof_short       DATE,           -- 공매도 기준일 (= FINRA 정산일, 약 2주 지연)
    asof_insider     DATE,           -- 내부자보유비율/기관보유비율 기준일
    asof_inst_13f    DATE,           -- 13F 기관보유 기준일 (= 분기말, 예: 2025-12-31)
    asof_analyst     DATE,           -- 애널리스트 컨센서스 기준일
    asof_etf         DATE,           -- ETF Holdings/메타 기준일
    
    -- ── 메타 ──
    is_delisted     BOOLEAN     DEFAULT false,          -- 상장폐지 마킹
    data_date       DATE,                               -- 전체 스냅샷 기준일 (= 배치 실행 대상 영업일)
    updated_at      TIMESTAMPTZ DEFAULT now()            -- 마지막 UPSERT 시각
);

-- =============================================================
-- 인덱스 전략
-- =============================================================

-- 1) Trigram: 티커·종목명 부분 검색 (자동완성)
CREATE INDEX IF NOT EXISTS idx_trgm_symbol
    ON latest_equities USING gin (symbol gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_trgm_name
    ON latest_equities USING gin (name gin_trgm_ops);

-- 2) B-Tree 복합: 다중 조건 필터 + 정렬 가속
--    "시총 내림차순으로, PER 30 이하, 배당 1% 이상"
CREATE INDEX IF NOT EXISTS idx_filter_stock
    ON latest_equities (asset_type, market_cap DESC NULLS LAST)
    WHERE is_delisted = false;

CREATE INDEX IF NOT EXISTS idx_pe_dividend
    ON latest_equities (pe_ttm, dividend_yield DESC NULLS LAST)
    WHERE is_delisted = false AND asset_type = 'stock';

CREATE INDEX IF NOT EXISTS idx_etf_expense
    ON latest_equities (expense_ratio ASC NULLS LAST, aum DESC NULLS LAST)
    WHERE is_delisted = false AND asset_type = 'etf';

CREATE INDEX IF NOT EXISTS idx_volume_turnover
    ON latest_equities (turnover DESC NULLS LAST)
    WHERE is_delisted = false;

CREATE INDEX IF NOT EXISTS idx_52week
    ON latest_equities (pct_from_52h ASC NULLS LAST)
    WHERE is_delisted = false;

-- 3) GIN: JSONB Holdings 내부 검색
--    "AAPL이 포함된 ETF 찾기"
CREATE INDEX IF NOT EXISTS idx_holdings_gin
    ON latest_equities USING gin (holdings jsonb_path_ops);

-- 4) B-Tree: 애널리스트 필터 (목표가 괴리율)
CREATE INDEX IF NOT EXISTS idx_analyst
    ON latest_equities (analyst_rating, target_upside_pct DESC NULLS LAST)
    WHERE is_delisted = false AND asset_type = 'stock';

-- 5) B-Tree: 재무건전성 + 성장성 필터
CREATE INDEX IF NOT EXISTS idx_financial_health
    ON latest_equities (roe DESC NULLS LAST, debt_to_equity ASC NULLS LAST)
    WHERE is_delisted = false AND asset_type = 'stock';

CREATE INDEX IF NOT EXISTS idx_growth
    ON latest_equities (revenue_growth DESC NULLS LAST, earnings_growth DESC NULLS LAST)
    WHERE is_delisted = false AND asset_type = 'stock';

-- 6) B-Tree: 공매도 필터
CREATE INDEX IF NOT EXISTS idx_short_interest
    ON latest_equities (short_pct_float DESC NULLS LAST)
    WHERE is_delisted = false AND asset_type = 'stock';

-- 7) B-Tree: EDGAR 내부자 거래 필터
CREATE INDEX IF NOT EXISTS idx_insider_activity
    ON latest_equities (insider_buy_3m DESC NULLS LAST, insider_sell_3m DESC NULLS LAST)
    WHERE is_delisted = false AND asset_type = 'stock';

-- =============================================================
-- RLS (Row Level Security) — Supabase에서 읽기 전용 공개
-- =============================================================
ALTER TABLE latest_equities ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Public read access"
    ON latest_equities FOR SELECT
    USING (true);

-- 배치 스크립트는 service_role 키 사용 → RLS 우회
```

### 3.3 Holdings JSONB 구조 상세

```json
// holdings 컬럼 예시 (SPY)
[
  {"s": "AAPL",  "w": 7.15, "v": 31063844227, "n": "Apple Inc."},
  {"s": "MSFT",  "w": 6.82, "v": 30582191043, "n": "Microsoft Corp."},
  {"s": "NVDA",  "w": 6.41, "v": 28726543000, "n": "NVIDIA Corp."},
  {"s": "AMZN",  "w": 3.82, "v": 17123456789, "n": "Amazon.com Inc."}
  // ... 상위 최대 50개까지 적재
]
```

**필드 규약** (용량 절약을 위해 축약키 사용):
- `s` = symbol (티커)
- `w` = weight (비중 %)
- `v` = value (시장가치 USD, 선택적)
- `n` = name (종목명, 선택적)

**상위 50개 제한 이유**:
- FMP Free로 받는 Holdings는 보통 상위 50~100개
- 50개 × ~100B = 5KB/ETF → 2,800 ETF × 5KB = 14MB (GIN 인덱스 포함 ~50MB)
- 상위 50개가 전체 비중의 70~90% 커버

### 3.4 핵심 쿼리 패턴

#### 자동완성 (Fuzzy Search)
```sql
-- "app" 입력 → AAPL, APP, APPS 등
SELECT symbol, name, asset_type, price, market_cap
FROM latest_equities
WHERE is_delisted = false
  AND (symbol % 'app' OR name % 'app')    -- pg_trgm 유사도
ORDER BY similarity(symbol, 'app') DESC, market_cap DESC NULLS LAST
LIMIT 10;
```

#### 다중 교집합 필터 (Stock)
```sql
-- "PER 30 이하, 배당 1% 이상, 시총 10B 이상, 52주 고점 대비 -10% 이내"
SELECT symbol, name, price, pe_ttm, dividend_yield, market_cap, pct_from_52h
FROM latest_equities
WHERE is_delisted = false
  AND asset_type = 'stock'
  AND pe_ttm <= 30
  AND dividend_yield >= 0.01
  AND market_cap >= 10000000000
  AND pct_from_52h >= -10
ORDER BY market_cap DESC
LIMIT 50 OFFSET 0;
```

#### 다중 교집합 필터 (ETF)
```sql
-- "수수료 0.1% 이하, 자산군 Equity, AUM 1B 이상"
SELECT symbol, name, expense_ratio, aum, holdings_count, index_tracked
FROM latest_equities
WHERE is_delisted = false
  AND asset_type = 'etf'
  AND expense_ratio <= 0.001
  AND asset_class = 'Equity'
  AND aum >= 1000000000
ORDER BY aum DESC
LIMIT 50;
```

#### AAPL 비중 5% 이상 ETF 역산출 (GIN + JSONB)
```sql
-- holdings JSONB 내부에서 symbol='AAPL'이고 weight >= 5인 ETF
SELECT symbol, name, expense_ratio, aum,
       h->>'w' AS aapl_weight
FROM latest_equities,
     jsonb_array_elements(holdings) AS h
WHERE is_delisted = false
  AND asset_type = 'etf'
  AND h->>'s' = 'AAPL'
  AND (h->>'w')::numeric >= 5
ORDER BY (h->>'w')::numeric DESC;
```

#### 복합 필터: "수수료 0.1% 이하 + AAPL 비중 5% 이상 + 52주 고점 -10% 이내"
```sql
SELECT e.symbol, e.name, e.expense_ratio, e.pct_from_52h,
       (h->>'w')::numeric AS aapl_weight
FROM latest_equities e,
     jsonb_array_elements(e.holdings) AS h
WHERE e.is_delisted = false
  AND e.asset_type = 'etf'
  AND e.expense_ratio <= 0.001
  AND e.pct_from_52h >= -10
  AND h->>'s' = 'AAPL'
  AND (h->>'w')::numeric >= 5
ORDER BY (h->>'w')::numeric DESC;
```

---

## 4. 배치 파이프라인 상세 설계

### 4.1 GitHub Actions 워크플로

```yaml
# .github/workflows/daily-sync.yml
name: Daily Stock Screener Sync

on:
  schedule:
    # 매일 KST 07:00 (UTC 22:00 전일) — 미국 장 마감 후
    # 월~금만 실행, 미국 공휴일은 Python에서 스킵
    - cron: '0 22 * * 1-5'
  workflow_dispatch:             # 수동 트리거

jobs:
  sync:
    runs-on: ubuntu-latest
    timeout-minutes: 60
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Setup Python
        uses: actions/setup-python@v5
        with:
          python-version: '3.11'
          cache: 'pip'
      
      - name: Install dependencies
        run: pip install -r requirements.txt
      
      - name: Check US market holiday
        id: holiday_check
        run: python pipeline/utils/us_market_calendar.py
      
      - name: Run sync pipeline
        id: sync_run
        if: steps.holiday_check.outputs.is_trading_day == 'true'
        env:
          FMP_API_KEY: ${{ secrets.FMP_API_KEY }}
          SUPABASE_URL: ${{ secrets.SUPABASE_URL }}
          SUPABASE_SERVICE_KEY: ${{ secrets.SUPABASE_SERVICE_KEY }}
        run: python pipeline/daily_sync.py
      
      - name: Skip message
        if: steps.holiday_check.outputs.is_trading_day == 'false'
        run: echo "US market holiday — skipping today's batch."
      
      # ── 배치 실패 시 이메일 알림 ──
      - name: Send failure email
        if: failure()
        uses: dawidd6/action-send-mail@v3
        with:
          server_address: smtp.gmail.com
          server_port: 465
          secure: true
          username: ${{ secrets.GMAIL_USER }}
          password: ${{ secrets.GMAIL_APP_PASSWORD }}
          subject: "⚠️ Stock Screener 배치 실패 — ${{ github.run_id }}"
          to: musiqq86@gmail.com
          from: Stock Screener Bot <${{ secrets.GMAIL_USER }}>
          body: |
            배치 파이프라인 실패 알림
            
            워크플로: ${{ github.workflow }}
            실행 ID: ${{ github.run_id }}
            시각 (UTC): ${{ github.event.head_commit.timestamp || 'scheduled' }}
            
            로그 확인: https://github.com/${{ github.repository }}/actions/runs/${{ github.run_id }}
```

> **이메일 알림 설정 (GitHub Secrets 등록 필요)**:
> - `GMAIL_USER`: 발신용 Gmail 주소
> - `GMAIL_APP_PASSWORD`: Gmail 앱 비밀번호 (2FA 활성화 후 생성)
> - 수신: `musiqq86@gmail.com`
> - 트리거: 배치 파이프라인 실행 중 어떤 step이든 `failure()` 발생 시 자동 발송

**미국 장 휴일 판별 스크립트**:

```python
# pipeline/utils/us_market_calendar.py
# GitHub Actions에서 실행 → 오늘이 미국 장 영업일인지 판별
# output: is_trading_day=true|false

import os
from datetime import date, timedelta

# ── NYSE/NASDAQ 공식 휴일 (매년 초 업데이트) ──
# 참고: https://www.nyse.com/markets/hours-calendars
US_MARKET_HOLIDAYS_2026 = {
    date(2026, 1, 1),   # New Year's Day
    date(2026, 1, 19),  # Martin Luther King Jr. Day
    date(2026, 2, 16),  # Presidents' Day
    date(2026, 4, 3),   # Good Friday
    date(2026, 5, 25),  # Memorial Day
    date(2026, 6, 19),  # Juneteenth National Independence Day
    date(2026, 7, 3),   # Independence Day (observed)
    date(2026, 9, 7),   # Labor Day
    date(2026, 11, 26), # Thanksgiving Day
    date(2026, 12, 25), # Christmas Day
}

US_MARKET_HOLIDAYS_2027 = {
    date(2027, 1, 1),   # New Year's Day
    date(2027, 1, 18),  # Martin Luther King Jr. Day
    date(2027, 2, 15),  # Presidents' Day
    date(2027, 3, 26),  # Good Friday
    date(2027, 5, 31),  # Memorial Day
    date(2027, 6, 18),  # Juneteenth (observed, 6/19 is Saturday)
    date(2027, 7, 5),   # Independence Day (observed)
    date(2027, 9, 6),   # Labor Day
    date(2027, 11, 25), # Thanksgiving Day
    date(2027, 12, 24), # Christmas Day (observed)
}

ALL_HOLIDAYS = US_MARKET_HOLIDAYS_2026 | US_MARKET_HOLIDAYS_2027

def is_us_trading_day(d: date = None) -> bool:
    """미국 장이 열리는 영업일인지 판별."""
    if d is None:
        # KST 07:00에 실행 → 전일 미국 기준 (UTC 22:00 = 미국 EST 17:00)
        # 배치 대상은 "어제 미국장 마감 데이터"
        d = date.today()  # GitHub Actions는 UTC 기준
    
    # 주말 체크
    if d.weekday() >= 5:  # 토(5), 일(6)
        return False
    
    # 공휴일 체크
    if d in ALL_HOLIDAYS:
        return False
    
    return True


if __name__ == "__main__":
    today = date.today()
    trading = is_us_trading_day(today)
    print(f"Date: {today}, Is Trading Day: {trading}")
    
    # GitHub Actions output 설정
    github_output = os.environ.get("GITHUB_OUTPUT")
    if github_output:
        with open(github_output, "a") as f:
            f.write(f"is_trading_day={'true' if trading else 'false'}\n")
```

> **연간 절약**: 미국 공휴일 ~9일 × 45분 = ~405분/년 절약.
> 공휴일 목록은 매년 초 NYSE 캘린더 확인 후 수동 업데이트.
> 또는 `exchange_calendars` PyPI 패키지로 자동화 가능 (requirements.txt에 추가 시).

```
pipeline/
├── daily_sync.py          # 메인 오케스트레이터
├── sources/
│   ├── fmp_client.py      # FMP API 래퍼 (요청 카운터 내장)
│   ├── yfinance_bulk.py   # yfinance 청크 다운로더
│   ├── priority_builder.py # 우선 티커 리스트 빌더 (시총+거래량+인기산업+필수)
│   ├── etf_roller.py      # ETF 롤링 배치 스케줄러
│   ├── edgar_insider.py   # SEC EDGAR Form 4 내부자 거래
│   ├── edgar_13f.py       # SEC EDGAR 13F 기관 보유
│   └── x_sentiment.py     # X Stock Sentiment API 소셜 센티먼트 (Nice-to-Have)
├── transforms/
│   ├── merge.py           # yfinance + FMP 데이터 병합
│   ├── compute.py         # change_pct, pct_from_52h, turnover 등 산출
│   ├── holdings.py        # Holdings → 축약 JSONB 변환
│   ├── insider_agg.py     # Form 4 → 집계 컬럼 산출 (buy_3m, sell_3m 등)
│   ├── freshness.py       # 소스별 데이터 기준일(as-of date) 산출
│   ├── deep_financials.py # 재무제표 파싱 → 영업이익증가율, 차입금증가율, 이자보상배율
│   └── scoring.py         # 종합 점수 산출 (밸류/퀄리티/모멘텀/성장/종합)
├── loaders/
│   └── supabase_upsert.py # UPSERT 실행 + fmp_cache TTL 정리
├── utils/
│   ├── us_market_calendar.py  # 미국 장 영업일 판별 (공휴일 스킵)
│   ├── sanity_check.py    # 데이터 무결성 자동 검증 (§4.6)
│   └── batch_summary.py   # 배치 실행 Summary 로깅 (§4.7)
├── config.py              # 상수, 티어 정의, 청크 사이즈, PRIORITY_INDUSTRIES
└── requirements.txt
```

### 4.3 UPSERT 로직 (핵심)

```python
# loaders/supabase_upsert.py (의사 코드)

from supabase import create_client

def upsert_equities(records: list[dict]):
    """
    INSERT ... ON CONFLICT (symbol) DO UPDATE
    Supabase Python SDK의 upsert 메서드 활용
    """
    supabase = create_client(SUPABASE_URL, SUPABASE_SERVICE_KEY)
    
    # 500건씩 청크 분할 (Supabase 페이로드 한도)
    for chunk in chunked(records, 500):
        supabase.table('latest_equities').upsert(
            chunk,
            on_conflict='symbol'      # PK 충돌 시 UPDATE
        ).execute()

def upsert_holdings(etf_symbol: str, holdings_json: list[dict]):
    """
    ETF Holdings만 별도 업데이트 (롤링 배치)
    """
    supabase.table('latest_equities').update({
        'holdings': holdings_json,
        'holdings_updated_at': 'now()'
    }).eq('symbol', etf_symbol).execute()
```

### 4.4 ETF 롤링 배치 스케줄러

```python
# sources/etf_roller.py (의사 코드)

import hashlib
from datetime import date

def get_todays_etf_batch(all_etf_symbols: list[str], tier: str) -> list[str]:
    """
    Tier 1: 매일 전체
    Tier 2: 주 2회 (월·목)
    Tier 3: 주 1회 (수요일, 7등분 순환)
    """
    today = date.today()
    weekday = today.weekday()  # 0=월 ~ 4=금
    
    if tier == 'tier1':
        return all_etf_symbols[:300]
    
    elif tier == 'tier2':
        if weekday in (0, 3):  # 월, 목
            # 700개를 2등분, 교대 실행
            half = len(all_etf_symbols) // 2
            return all_etf_symbols[:half] if weekday == 0 else all_etf_symbols[half:]
        return []
    
    elif tier == 'tier3':
        if weekday == 2:  # 수요일
            # day_of_year 기준 7등분 순환
            group = (today.timetuple().tm_yday // 7) % 7
            chunk_size = len(all_etf_symbols) // 7
            start = group * chunk_size
            return all_etf_symbols[start:start + chunk_size]
        return []
```

### 4.5 산출 필드 (배치에서 계산)

이하 3개 필드만 배치 스크립트에서 직접 산출. 나머지는 전부 벤더 값 그대로.

```python
# transforms/compute.py

def compute_derived_fields(row: dict) -> dict:
    """RDBMS 연산 부하 0을 위해 Python에서 미리 계산"""
    
    price = row.get('price')
    prev = row.get('prev_close')
    h52 = row.get('week52_high')
    l52 = row.get('week52_low')
    vol = row.get('volume')
    target = row.get('target_mean')
    
    # 1. 등락률
    if price and prev and prev > 0:
        row['change_pct'] = round((price - prev) / prev * 100, 4)
    
    # 2. 52주 고점 대비 (%)
    if price and h52 and h52 > 0:
        row['pct_from_52h'] = round((price - h52) / h52 * 100, 4)
    
    # 3. 52주 저점 대비 (%)
    if price and l52 and l52 > 0:
        row['pct_from_52l'] = round((price - l52) / l52 * 100, 4)
    
    # 4. 거래대금 (price × volume)
    if price and vol:
        row['turnover'] = round(price * vol, 2)
    
    # 5. 목표가 괴리율 (%)
    if price and target and price > 0:
        row['target_upside_pct'] = round((target - price) / price * 100, 4)
    
    # 6. FCF 수익률 (%)
    fcf = row.get('free_cashflow')
    mcap = row.get('market_cap')
    if fcf and mcap and mcap > 0:
        row['fcf_yield'] = round(fcf / mcap, 4)
    
    # 7. 상대거래량
    avg_vol = row.get('avg_volume_10d')
    if vol and avg_vol and avg_vol > 0:
        row['relative_volume'] = round(vol / avg_vol, 2)
    
    # 8. 갭 (%)
    open_p = row.get('open_price')
    if open_p and prev and prev > 0:
        row['gap_pct'] = round((open_p - prev) / prev * 100, 4)
    
    # 9. 시가대비 등락 (%)
    if price and open_p and open_p > 0:
        row['change_from_open'] = round((price - open_p) / open_p * 100, 4)
    
    # 10. SMA 괴리율 (%)
    for ma_key, pct_key in [('ma_20','sma20_pct'), ('ma_50','sma50_pct'), ('ma_200','sma200_pct')]:
        ma_val = row.get(ma_key)
        if price and ma_val and ma_val > 0:
            row[pct_key] = round((price - ma_val) / ma_val * 100, 4)
    
    return row
```

### 4.6 데이터 무결성 검증 (Sanity Check)

배치 UPSERT 완료 후, 잘못된 데이터가 앱에 노출되지 않도록 **자동 검증** 단계를 실행.
검증 실패 시 조치 방침:
- **WARNING**: 로그 경고만 → 데이터는 그대로 유지 (일시적 API 불안정 가능성)
- **CRITICAL**: 이메일 알림 + 해당 배치의 UPSERT 결과를 **전일 스냅샷으로 롤백하지 않고**,
  문제 컬럼만 NULL로 마킹하여 앱에서 `-` 표시되게 처리 → 수동 확인 후 다음 배치에서 자동 복구

```python
# pipeline/utils/sanity_check.py

import logging
from datetime import date

logger = logging.getLogger(__name__)


class SanityCheckResult:
    def __init__(self):
        self.warnings: list[str] = []
        self.criticals: list[str] = []
    
    @property
    def passed(self) -> bool:
        return len(self.criticals) == 0
    
    def summary(self) -> str:
        lines = [f"=== Sanity Check Summary ({date.today()}) ==="]
        lines.append(f"CRITICAL: {len(self.criticals)}건, WARNING: {len(self.warnings)}건")
        for c in self.criticals:
            lines.append(f"  🔴 CRITICAL: {c}")
        for w in self.warnings:
            lines.append(f"  🟡 WARNING: {w}")
        return "\n".join(lines)


def run_sanity_checks(supabase, prev_count: int | None = None) -> SanityCheckResult:
    """
    UPSERT 직후 실행. DB에서 검증 쿼리를 날려 이상 여부를 확인.
    
    Args:
        supabase: Supabase 클라이언트
        prev_count: 전일 UPSERT 건수 (비교용, 없으면 스킵)
    """
    result = SanityCheckResult()
    
    # ── 1) 핵심 종목(AAPL, MSFT, NVDA) 가격 NULL 체크 ──
    # 이 종목들의 price가 NULL이면 yfinance 전면 장애 의심
    sentinel_symbols = ['AAPL', 'MSFT', 'NVDA', 'GOOGL', 'AMZN']
    sentinel_check = supabase.table('latest_equities') \
        .select('symbol, price') \
        .in_('symbol', sentinel_symbols) \
        .is_('price', 'null') \
        .execute()
    
    if sentinel_check.data:
        missing = [r['symbol'] for r in sentinel_check.data]
        result.criticals.append(
            f"핵심 종목 가격 NULL: {missing} — yfinance 전면 장애 의심"
        )
    
    # ── 2) 전체 UPSERT 건수 급감 체크 ──
    total = supabase.table('latest_equities') \
        .select('symbol', count='exact') \
        .eq('is_delisted', False) \
        .execute()
    current_count = total.count or 0
    
    if prev_count and current_count < prev_count * 0.5:
        result.criticals.append(
            f"UPSERT 건수 급감: {prev_count} → {current_count} "
            f"({current_count/prev_count*100:.0f}%) — 파이프라인 이상 의심"
        )
    elif prev_count and current_count < prev_count * 0.8:
        result.warnings.append(
            f"UPSERT 건수 감소: {prev_count} → {current_count} "
            f"({current_count/prev_count*100:.0f}%)"
        )
    
    # ── 3) 가격이 비정상적으로 0이거나 음수인 종목 ──
    bad_price = supabase.table('latest_equities') \
        .select('symbol', count='exact') \
        .eq('is_delisted', False) \
        .lte('price', 0) \
        .not_.is_('price', 'null') \
        .execute()
    
    if (bad_price.count or 0) > 10:
        result.warnings.append(
            f"가격 ≤ 0 종목: {bad_price.count}건 — 데이터 품질 확인 필요"
        )
    
    # ── 4) data_date가 오늘(배치 대상일)과 불일치 ──
    # 배치가 어제 장 마감 데이터를 가져왔는데, date_date가 2일 이상 오래되면 경고
    stale = supabase.rpc('count_stale_records', {}).execute()
    # → DB Function 또는 직접 쿼리로 구현
    
    # ── 5) 결과 출력 ──
    summary = result.summary()
    if result.criticals:
        logger.error(summary)
        # GitHub Actions 어노테이션
        for c in result.criticals:
            print(f"::error::{c}")
    elif result.warnings:
        logger.warning(summary)
        for w in result.warnings:
            print(f"::warning::{w}")
    else:
        logger.info(summary)
    
    return result


def nullify_bad_data(supabase, symbols: list[str], columns: list[str]):
    """
    CRITICAL 검증 실패 시, 문제 종목의 해당 컬럼만 NULL로 마킹.
    앱에서는 '-'로 표시됨. 다음 배치에서 자동 복구.
    """
    update_payload = {col: None for col in columns}
    for sym in symbols:
        supabase.table('latest_equities') \
            .update(update_payload) \
            .eq('symbol', sym) \
            .execute()
    logger.warning(f"[SanityCheck] {len(symbols)}건 문제 데이터 NULL 마킹 완료: {columns}")
```

**`daily_sync.py` 에서의 호출 흐름**:

```python
# pipeline/daily_sync.py (요약)

from utils.sanity_check import run_sanity_checks, nullify_bad_data

def main():
    start_time = time.time()
    
    # ... ①~⑩ 배치 단계 실행 ...
    
    # ── Sanity Check ──
    prev_count = load_prev_count()  # 전일 건수 (로컬 파일 또는 DB 메타)
    check_result = run_sanity_checks(supabase, prev_count)
    
    if not check_result.passed:
        # CRITICAL 시 문제 데이터 NULL 마킹 (롤백 대신)
        # → 다음 배치에서 자동 복구됨
        logger.error("Sanity check CRITICAL — 문제 데이터 NULL 처리 후 계속")
    
    save_prev_count(current_count)  # 다음 배치 비교용 저장
    
    # ── Batch Summary 로깅 ──
    elapsed = time.time() - start_time
    summary = build_batch_summary(elapsed, check_result)
    logger.info(summary)
    
    # GitHub Actions Job Summary에 출력
    write_github_summary(summary)
```

### 4.7 배치 실행 Summary 로깅

매 배치 완료 시 최종 실행 결과를 구조화 로그로 출력. GitHub Actions Summary에도 기록.

```python
# pipeline/utils/batch_summary.py

import os
from datetime import date

def build_batch_summary(elapsed_seconds: float, sanity_result) -> str:
    """배치 완료 후 최종 Summary 생성."""
    minutes = elapsed_seconds / 60
    return f"""
╔══════════════════════════════════════════════╗
║  📊 Daily Batch Summary — {date.today()}     ║
╠══════════════════════════════════════════════╣
║  소요 시간:     {minutes:.1f}분                    ║
║  Sanity Check: {'✅ PASSED' if sanity_result.passed else '❌ FAILED'}                  ║
║  CRITICAL:     {len(sanity_result.criticals)}건                       ║
║  WARNING:      {len(sanity_result.warnings)}건                       ║
╚══════════════════════════════════════════════╝
"""

def write_github_summary(summary: str):
    """GitHub Actions Job Summary에 Markdown으로 기록."""
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a") as f:
            f.write(f"```\\n{summary}\\n```\\n")
```

---

## 5. API 레이어 설계 (Supabase PostgREST + Edge Functions)

Supabase는 테이블 생성 즉시 REST 엔드포인트를 자동 생성합니다.
Android 앱에서 **별도 백엔드 없이** 직접 호출:

```
// 기본 URL 패턴
GET {SUPABASE_URL}/rest/v1/latest_equities?select=*&is_delisted=eq.false&asset_type=eq.stock&market_cap=gte.10000000000&order=market_cap.desc&limit=50

// 헤더
apikey: {SUPABASE_ANON_KEY}
Authorization: Bearer {SUPABASE_ANON_KEY}
```

**PostgREST 쿼리 연산자 매핑**:

| 앱 UI 필터 | PostgREST 파라미터 |
|-----------|------------------|
| PER ≤ 30 | `pe_ttm=lte.30` |
| 배당률 ≥ 1% | `dividend_yield=gte.0.01` |
| 시총 ≥ 10B | `market_cap=gte.10000000000` |
| 섹터 = Technology | `sector=eq.Technology` |
| ETF만 | `asset_type=eq.etf` |
| 수수료 ≤ 0.1% | `expense_ratio=lte.0.001` |
| 52주 고점 대비 ≥ -10% | `pct_from_52h=gte.-10` |
| 시총 내림차순 | `order=market_cap.desc` |
| 페이지네이션 | `limit=50&offset=0` |

### 5.2 Edge Function: 복합 JSONB 쿼리

PostgREST로는 JSONB 내부 배열 검색이 불가능하므로, Supabase Edge Function으로 구현:

```typescript
// supabase/functions/etf-by-holding/index.ts

import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

serve(async (req) => {
  const { holding_symbol, min_weight, max_expense, min_pct_52h } = await req.json();
  
  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
  );
  
  // RPC 호출 (DB Function)
  const { data, error } = await supabase.rpc('find_etfs_by_holding', {
    p_symbol: holding_symbol,
    p_min_weight: min_weight || 0,
    p_max_expense: max_expense || 1,
    p_min_pct_52h: min_pct_52h || -100
  });
  
  return new Response(JSON.stringify(data), {
    headers: { "Content-Type": "application/json" }
  });
});
```

**대응 DB Function**:
```sql
CREATE OR REPLACE FUNCTION find_etfs_by_holding(
    p_symbol TEXT,
    p_min_weight NUMERIC DEFAULT 0,
    p_max_expense NUMERIC DEFAULT 1,
    p_min_pct_52h NUMERIC DEFAULT -100
)
RETURNS TABLE (
    symbol TEXT,
    name TEXT,
    expense_ratio NUMERIC,
    aum BIGINT,
    pct_from_52h NUMERIC,
    holding_weight NUMERIC
)
LANGUAGE sql STABLE AS $$
    SELECT e.symbol, e.name, e.expense_ratio, e.aum, e.pct_from_52h,
           (h->>'w')::numeric AS holding_weight
    FROM latest_equities e,
         jsonb_array_elements(e.holdings) AS h
    WHERE e.is_delisted = false
      AND e.asset_type = 'etf'
      AND e.expense_ratio <= p_max_expense
      AND (e.pct_from_52h >= p_min_pct_52h OR e.pct_from_52h IS NULL)
      AND h->>'s' = p_symbol
      AND (h->>'w')::numeric >= p_min_weight
    ORDER BY (h->>'w')::numeric DESC;
$$;
```

---

## 6. 프론트엔드 (Android) 핵심 요구사항

### 6.1 화면 구성

```
┌──────────────────────────────────────────┐
│  📱 하단 네비게이션                         │
│                                           │
│  🔍 스크리너 │ 🟩 히트맵 │ ⭐ 관심 │ ⚙ 설정 │
└──────────────────────────────────────────┘

[스크리너 탭]
├── 상단: 검색바 (Fuzzy Search, 자동완성)
├── 프리셋 시그널 바 (가로 스크롤 칩, 31종)
│   ├── 🔥거래량급등  📈신고가  📉신저가  💰고배당가치주
│   ├── 🚀StrongBuy  🎯목표괴리20%+
│   ├── 📊공매도급증  🔑내부자매수  💬소셜버즈  ⚔️내부자vs공매도
│   ├── 🔴과매수  🟢과매도  🌪️최고변동성  📢상대거래량2배+
│   ├── 🚀오늘급등  💥오늘급락  📈1년수익50%+
│   ├── 🛡️재무안정  ⚡저PER성장  🏆고ROE
│   ├── 🏅F-Score8+  ⚠️Z-Score위험  🎯실적서프  💰현금부자저평가
│   └── 💎EV/EBITDA저평가  ⭐종합80+  🎰DCF저평가  🔥모멘텀폭발
│       👑퀄리티+밸류  💰주주환원  📊MACD골든
├── 필터 패널 (접기/펴기)
│   ├── 종목 유형: Stock / ETF 토글
│   ├── [Stock 모드]
│   │   ├── [밸류에이션] 시총 / PER / PBR / PEG / PSR / P/FCF / EV/EBITDA / EV/Revenue
│   │   ├── [배당]      배당률 / 배당성향 / 5년평균배당률 / 배당락일
│   │   ├── [재무건전성] ROE / ROA / ROIC★ / 부채비율 / 장기부채비율★ / 유동비율 / 당좌비율 / 이자보상배율★
│   │   ├── [현금흐름]  FCF / FCF수익률 / 현금성자산 / 총부채
│   │   ├── [성장성]    매출성장률 / EPS성장률 / 영업이익증가율★ / EPS 5Y★ / 매출 5Y★
│   │   ├── [수익성]    영업이익률 / 순이익률 / 매출총이익률
│   │   ├── [점수]      종합점수 / 밸류점수 / 퀄리티점수 / 모멘텀점수 / 성장점수 / F-Score★ / Z-Score★ / 소셜점수
│   │   ├── [수급]      공매도비율 / 기관보유 / 기관거래변동★ / 내부자매수
│   │   ├── [기술적]    52주고저 / 베타 / RSI(14) / MACD히스토그램 / ADX / Stochastic / 20·50·200일 MA괴리
│   │   ├── [밸류모델]  DCF괴리율 / Graham괴리율
│   │   ├── [성과]      1주/1월/3월/6월/1년/YTD 수익률
│   │   ├── [애널리스트] 투자의견 / 목표괴리율 / 실적서프라이즈
│   │   ├── [내부자/기관] 내부자매수건수 / 최근거래유형 (EDGAR)
│   │   ├── [분류]      섹터 / 산업 / 소재국 / 인덱스소속
│   │   └── [거래]      거래량 / 거래대금 / 상대거래량 / 변동성
│   │   (★ = 상위 500종목만 필터 가능)
│   └── [ETF 모드]
│       ├── 운용수수료 최대
│       ├── AUM 최소
│       ├── 자산군 선택
│       ├── 특정 종목 포함 (역산출)
│       │   ├── 종목 검색 입력
│       │   └── 최소 비중 (%)
│       └── 52주 고점 대비 범위
├── 정렬 바 (선택 컬럼 기준 오름/내림)
└── 결과 리스트 (LazyColumn, 페이지네이션)
    └── 각 항목 카드
        ├── 티커 + 종목명
        ├── 현재가 + 등락률
        ├── 주요 지표 2~3개 (필터 기준에 맞춰 동적)
        └── 탭하면 상세 화면

[히트맵 탭]
├── 상단: 기준 선택 (등락률 / 거래량 / 시총)
├── 섹터 그리드 (Treemap 또는 Grid)
│   ├── 각 섹터가 하나의 블록 (시총 비중으로 크기 결정)
│   ├── 내부에 개별 종목 셀 (시총 비중으로 크기 결정)
│   ├── 색상: 등락률 기준 (빨강 = 상승, 파랑 = 하락, 진할수록 큰 변동)
│   └── 탭하면 종목 상세
├── 하단: 섹터별 평균 등락률 바 차트
└── 데이터 소스: 기존 latest_equities 테이블 (추가 API 불필요)
```

#### 6.1.1 프리셋 시그널 정의

프리셋 시그널은 자주 쓰는 필터 조합을 원탭으로 적용하는 기능.
DB 쿼리로 구현, 별도 테이블 불필요.

| 시그널 | UI 라벨 | SQL WHERE 조건 |
|--------|---------|---------------|
| 거래량 급등 | **🔥 거래량 급등** | `volume > avg_volume_10d * 2` |
| 52주 신고가 | **📈 신고가** | `pct_from_52h >= -1` (고점 대비 1% 이내) |
| 52주 신저가 | **📉 신저가** | `pct_from_52l <= 1` (저점 대비 1% 이내) |
| 고배당 가치주 | **💰 고배당 가치주** | `dividend_yield >= 0.03 AND pe_ttm <= 20 AND pe_ttm > 0` |
| Strong Buy | **🚀 Strong Buy** | `analyst_rating IN ('strong_buy','buy') AND analyst_count >= 10` |
| 목표가 괴리 | **🎯 목표가 괴리 20%+** | `target_upside_pct >= 20` |
| 공매도 급증 | **📊 공매도 급증** | `short_pct_float >= 0.10` (유통주식 10%+) |
| 저PER 성장주 | **⚡ 저PER 성장주** | `pe_ttm <= 15 AND revenue_growth >= 0.15` |
| 고ROE 우량주 | **🏆 고ROE 우량주** | `roe >= 0.20 AND debt_to_equity <= 100` |
| 재무 안정 우량주 | **🛡️ 재무 안정 우량주** | `interest_coverage >= 10 AND debt_growth <= 0 AND roe >= 0.15` |
| F-Score 우량주 | **🏅 F-Score 8+** | `piotroski_score >= 8` |
| 부도위험 경고 | **⚠️ Z-Score 위험** | `altman_z_score < 1.8 AND altman_z_score IS NOT NULL` |
| 실적 서프라이즈 | **🎯 실적 서프라이즈 10%+** | `earnings_surprise_pct >= 10` |
| EV/EBITDA 저평가 | **💎 EV/EBITDA 저평가** | `ev_ebitda <= 10 AND ev_ebitda > 0 AND fcf_yield >= 0.05` |
| 종합점수 80+ | **⭐ 종합점수 80+** | `score_total >= 80` |
| DCF 저평가 | **🎰 DCF 20%+ 저평가** | `dcf_upside_pct >= 20` |
| 모멘텀 급등 | **🔥 모멘텀 폭발** | `score_momentum >= 90 AND relative_volume >= 1.5` |
| 퀄리티 + 밸류 | **👑 퀄리티+밸류 동시 80+** | `score_quality >= 80 AND score_value >= 80` |
| 주주환원 우량주 | **💰 주주환원 5%+** | `shareholder_yield >= 0.05` |
| MACD 골든크로스 | **📊 MACD 골든크로스** | `macd > macd_signal AND macd_hist > 0` |
| 내부자 매수 급증 | **🔑 내부자 매수 급증** | `insider_buy_3m >= 3 AND insider_latest_type = 'Buy'` |
| 내부자 vs 공매도 | **⚔️ 내부자매수+공매도 높은** | `insider_buy_3m >= 2 AND short_pct_float >= 0.05` |
| 소셜 버즈 급등 | **💬 소셜 버즈 급등** | `social_score >= 80 AND social_reddit_validated = true` _(Nice-to-Have: 소셜 데이터 미수집 시 결과 0건, 앱 정상 동작. "소셜 데이터가 아직 수집되지 않았습니다" 토스트 표시)_ |
| 과매수 (RSI) | **🔴 과매수** | `rsi_14 >= 70` |
| 과매도 (RSI) | **🟢 과매도** | `rsi_14 <= 30` |
| 급등주 (Top Gainers) | **🚀 오늘 급등** | `change_pct >= 5` (등락률 5%+) |
| 급락주 (Top Losers) | **💥 오늘 급락** | `change_pct <= -5` |
| 최고 변동성 | **🌪️ 최고 변동성** | `volatility_w >= 0.05` (주간 변동성 5%+) |
| 상대거래량 폭발 | **📢 상대거래량 2배+** | `relative_volume >= 2` |
| 1년 고수익 | **📈 1년 수익률 50%+** | `perf_1y >= 0.50` |
| 현금부자 저평가 | **💰 현금부자 저평가** | `fcf_yield >= 0.06 AND total_cash > total_debt` |

### 6.2 Android ↔ Supabase 통신 구조

```
Android App (Kotlin)
    │
    ├── Retrofit2 / Ktor Client
    │   ├── GET /rest/v1/latest_equities?...  (단순 필터)
    │   ├── POST /functions/v1/etf-by-holding  (JSONB 복합)
    │   └── POST /functions/v1/fmp-proxy       (온디맨드 FMP, 캐시 경유)
    │
    ├── 헤더:
    │   ├── apikey: SUPABASE_ANON_KEY
    │   └── Authorization: Bearer SUPABASE_ANON_KEY
    │
    └── 응답: JSON → Kotlin Data Class → UI State
```

### 6.3 Supabase Anon Key 보안

Supabase의 `anon` 키는 RLS 정책에 의해 보호됩니다:
- `latest_equities` 테이블: **SELECT만 허용** (위에서 정의한 RLS Policy)
- INSERT/UPDATE/DELETE: `service_role` 키 필요 (GitHub Actions 배치만 보유)
- Android 앱에는 `anon` 키만 포함 → 읽기 전용, 데이터 변조 불가

### 6.4 한글화 원칙: 헤더 한글 · 값 영문

> **핵심 규칙**: 사용자에게 보이는 모든 컬럼 라벨(헤더)·필터명·정렬 옵션은
> **한글**로 표시하고, 실제 데이터 값(value)은 **영문 원문 그대로** 유지한다.
> 필요 시 **영문 원본 컬럼명도 확인 가능**해야 한다.

**영문 원본 확인 방식 — Long Press 툴팁**:

모든 한글 헤더/라벨을 **길게 누르면** 영문 원본명 + 간단 설명이 툴팁으로 표시.

```
[평소]                     [길게 누르면]
┌──────────┐              ┌──────────────────────┐
│ 시가총액  │  ──(꾹)──→  │ market_cap            │
│          │              │ Market Capitalization │
└──────────┘              │ (USD)                 │
                          └──────────────────────┘

[수급 탭 예시]             [공매도비율 길게 누르면]
┌──────────┐              ┌──────────────────────┐
│공매도비율 │  ──(꾹)──→   │ short_ratio           │
│  1.54    │              │ Short Ratio           │
└──────────┘              │ Days to Cover         │
                          └──────────────────────┘
```

```kotlin
// ui/constants/ColumnLabels.kt 에 추가

object ColumnLabels {
    // 기존 한글 매핑
    val headers = mapOf(...)

    // 영문 원본명 + 설명 (Long Press 툴팁용)
    data class ColumnMeta(
        val kr: String,           // 한글 헤더
        val en: String,           // 영문 원본 DB 컬럼명
        val enFull: String,       // 영문 전체명
        val description: String = "",  // 간단 설명
    )

    val meta = mapOf(
        "symbol" to ColumnMeta("티커", "symbol", "Ticker Symbol"),
        "name" to ColumnMeta("종목명", "name", "Company Name"),
        "price" to ColumnMeta("현재가", "price", "Current Price", "USD, MOC 기준"),
        "market_cap" to ColumnMeta("시가총액", "market_cap", "Market Capitalization", "USD"),
        "pe_ttm" to ColumnMeta("PER", "pe_ttm", "Price/Earnings Ratio", "Trailing 12 Months"),
        "dividend_yield" to ColumnMeta("배당률", "dividend_yield", "Dividend Yield", "Annual"),
        "roe" to ColumnMeta("ROE", "roe", "Return on Equity"),
        "short_ratio" to ColumnMeta("공매도비율", "short_ratio", "Short Ratio", "Days to Cover"),
        "short_pct_float" to ColumnMeta("공매도/유통", "short_pct_float", "Short % of Float"),
        "insider_buy_3m" to ColumnMeta("내부자매수(3M)", "insider_buy_3m", "Insider Buys (90 Days)", "SEC EDGAR Form 4"),
        "target_upside_pct" to ColumnMeta("목표괴리율", "target_upside_pct", "Price Target Upside %", "(목표가-현재가)/현재가"),
        // ... 전체 컬럼에 대해 동일 패턴
    )
}
```

```kotlin
// ui/components/TooltipHeader.kt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TooltipHeader(
    columnKey: String,
    modifier: Modifier = Modifier,
    onSort: (() -> Unit)? = null,
) {
    val meta = ColumnLabels.meta[columnKey]
    var showTooltip by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Text(
            text = meta?.kr ?: columnKey,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .combinedClickable(
                    onClick = { onSort?.invoke() },          // 탭: 정렬
                    onLongClick = { showTooltip = true },     // 길게 누르기: 툴팁
                )
        )

        if (showTooltip && meta != null) {
            Popup(
                onDismissRequest = { showTooltip = false },
                alignment = Alignment.TopStart,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shadowElevation = 4.dp,
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = meta.en,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                        )
                        Text(
                            text = meta.enFull,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.8f),
                        )
                        if (meta.description.isNotEmpty()) {
                            Text(
                                text = meta.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
        }
    }
}
```

> **동작 요약**: 탭 = 정렬, 꾹 = 영문 원본 툴팁.
> 필터 패널의 라벨, 정렬 옵션, 상세화면 항목명도 동일 방식 적용.

**화면 예시**:
```
┌──────────────────────────────────────────────────┐
│ 티커     │ 종목명          │ 현재가  │ 시가총액    │
│──────────│────────────────│────────│───────────│
│ AAPL     │ Apple Inc.     │ $178.72│ $2.8T     │
│ MSFT     │ Microsoft Corp.│ $415.30│ $3.1T     │
│ NVDA     │ NVIDIA Corp.   │ $875.40│ $2.2T     │
└──────────────────────────────────────────────────┘
         ↑ 영문              ↑ 영문     ↑ 숫자+$
  ↑ 한글 헤더
```

#### 6.4.1 리스트/상세 화면 — 컬럼 헤더 매핑

| DB 컬럼명 | UI 헤더 (한글) | 값 표시 형식 | 비고 |
|-----------|---------------|-------------|------|
| `symbol` | **티커** | `AAPL` | 영문 대문자 |
| `name` | **종목명** | `Apple Inc.` | 영문 원문 |
| `asset_type` | **유형** | `Stock` / `ETF` | 영문 |
| `exchange` | **거래소** | `NASDAQ` | 영문 약칭 |
| `sector` | **섹터** | `Technology` | 영문 원문 |
| `industry` | **산업** | `Consumer Electronics` | 영문 원문 |
| `price` | **현재가** | `$178.72` | USD |
| `open_price` | **시가** | `$177.50` | |
| `day_high` | **일중최고** | `$179.80` | |
| `day_low` | **일중최저** | `$176.90` | |
| `prev_close` | **전일종가** | `$177.15` | |
| `change_pct` | **등락률** | `+0.89%` / `-1.23%` | 색상: 상승 빨강, 하락 파랑 |
| `volume` | **거래량** | `52.3M` | 축약 표기 (K/M/B) |
| `avg_volume_10d` | **평균거래량(10일)** | `48.1M` | |
| `turnover` | **거래대금** | `$9.3B` | |
| `market_cap` | **시가총액** | `$2.8T` | 축약 (M/B/T) |
| `pe_ttm` | **PER** | `28.54` | 후행 12개월 |
| `pe_forward` | **예상PER** | `26.12` | |
| `pb_ratio` | **PBR** | `45.32` | |
| `eps_ttm` | **EPS** | `$6.26` | |
| `dividend_yield` | **배당률** | `0.65%` | 소수→백분율 변환 표시 |
| `dividend_rate` | **배당금(연)** | `$1.00` | |
| `ex_dividend_date` | **배당락일** | `2026-02-07` | |
| `avg_dividend_yield_5y` | **5년평균배당률** | `0.58%` | |
| `ev` | **EV** | `$3.0T` | Enterprise Value |
| `ev_ebitda` | **EV/EBITDA** | `22.4` | |
| `ev_revenue` | **EV/매출** | `7.8` | |
| `fcf_yield` | **FCF수익률** | `3.2%` | freeCashflow/시총 |
| `total_revenue` | **매출액(TTM)** | `$394.3B` | |
| `ebitda` | **EBITDA** | `$134.2B` | |
| `free_cashflow` | **잉여현금흐름** | `$111.4B` | |
| `total_cash` | **현금성자산** | `$29.9B` | |
| `total_debt` | **총부채** | `$108.0B` | |
| `book_value` | **장부가/주** | `$3.94` | |
| `revenue_per_share` | **매출/주** | `$25.59` | |
| `piotroski_score` | **F-Score** | `7` | 0~9, 상위500만 |
| `altman_z_score` | **Z-Score** | `5.82` | >3 안전, <1.8 위험, 상위500만 |
| `earnings_surprise_pct` | **실적서프라이즈** | `+4.2%` | 직전 분기 |
| `week52_high` | **52주최고** | `$199.62` | |
| `week52_low` | **52주최저** | `$143.90` | |
| `pct_from_52h` | **고점대비** | `-10.5%` | |
| `pct_from_52l` | **저점대비** | `+24.2%` | |
| `ma_50` | **50일이평** | `$172.30` | |
| `ma_200` | **200일이평** | `$168.45` | |
| `beta` | **베타** | `1.28` | |
| `roe` | **ROE** | `147.2%` | 자기자본이익률 |
| `roa` | **ROA** | `28.1%` | 총자산이익률 |
| `debt_to_equity` | **부채비율** | `176.3` | D/E |
| `current_ratio` | **유동비율** | `1.07` | |
| `operating_margin` | **영업이익률** | `30.7%` | |
| `profit_margin` | **순이익률** | `25.3%` | |
| `gross_margin` | **매출총이익률** | `46.2%` | |
| `revenue_growth` | **매출성장률** | `+8.1%` | YoY |
| `earnings_growth` | **EPS성장률** | `+12.5%` | YoY |
| `peg_ratio` | **PEG** | `2.34` | |
| `ps_ratio` | **PSR** | `3.21` | 주가매출비율 |
| `pfcf_ratio` | **P/FCF** | `18.5` | 주가잉여현금흐름 |
| `pcash_ratio` | **P/Cash** | `12.3` | 주가현금비율, 상위500만 |
| `op_income_growth` | **영업이익증가율** | `+15.3%` | YoY, 상위 500만 |
| `debt_growth` | **차입금증가율** | `+8.2%` | YoY, 상위 500만 |
| `interest_coverage` | **이자보상배율** | `24.5` | 상위 500만 |
| `roic` | **ROIC** | `32.1%` | 투하자본수익률, 상위500만 |
| `quick_ratio` | **당좌비율** | `0.94` | |
| `lt_debt_equity` | **장기부채비율** | `112.5` | 상위500만 |
| `payout_ratio` | **배당성향** | `15.3%` | |
| `eps_growth_this_yr` | **EPS성장률(금년)** | `+12.5%` | 추정 |
| `eps_growth_past_5y` | **EPS성장률(5Y)** | `+18.2%` | 연평균, 상위500만 |
| `sales_growth_past_5y` | **매출성장률(5Y)** | `+9.8%` | 연평균, 상위500만 |
| `inst_transactions_pct` | **기관거래변동** | `+2.3%` | 전분기 대비, 상위500만 |
| `rsi_14` | **RSI(14)** | `58.3` | 과매수>70, 과매도<30 |
| `atr_14` | **ATR(14)** | `$3.42` | 평균진폭 |
| `volatility_w` | **변동성(주)** | `2.8%` | |
| `volatility_m` | **변동성(월)** | `4.1%` | |
| `ma_20` | **20일이평** | `$175.30` | |
| `sma20_pct` | **20일이평괴리** | `+1.9%` | |
| `sma50_pct` | **50일이평괴리** | `+3.7%` | |
| `sma200_pct` | **200일이평괴리** | `+6.1%` | |
| `gap_pct` | **갭** | `+0.5%` | 전일종가→시가 |
| `change_from_open` | **시가대비** | `+0.4%` | 시가→종가 |
| `relative_volume` | **상대거래량** | `1.85` | 평균 대비 배수 |
| `macd` | **MACD** | `2.34` | |
| `macd_signal` | **MACD시그널** | `1.89` | |
| `macd_hist` | **MACD히스토그램** | `+0.45` | 양수=상승추세 |
| `stoch_k` | **Stoch %K** | `72.5` | |
| `stoch_d` | **Stoch %D** | `68.3` | |
| `adx_14` | **ADX(14)** | `28.5` | >25=추세, <20=횡보 |
| `cci_20` | **CCI(20)** | `+85` | |
| `williams_r` | **Williams %R** | `-28.5` | |
| `dcf_value` | **DCF내재가치** | `$195.30` | |
| `dcf_upside_pct` | **DCF괴리율** | `+9.3%` | |
| `graham_number` | **Graham적정가** | `$52.40` | |
| `graham_upside_pct` | **Graham괴리율** | `-70.7%` | |
| `accruals_ratio` | **Accruals비율** | `-0.05` | 낮을수록 양질, 상위500만 |
| `fcf_to_ni` | **FCF/순이익** | `1.12` | >1 양질, 상위500만 |
| `earnings_quality_score` | **수익품질점수** | `78` | 0~100, 상위500만 |
| `buyback_yield` | **자사주매입률** | `2.8%` | 상위500만 |
| `shareholder_yield` | **주주환원율** | `3.5%` | 배당+자사주, 상위500만 |
| `capex_to_revenue` | **CAPEX/매출** | `5.2%` | 상위500만 |
| `score_value` | **밸류점수** | `72` | 0~100 |
| `score_quality` | **퀄리티점수** | `85` | 0~100 |
| `score_momentum` | **모멘텀점수** | `63` | 0~100 |
| `score_growth` | **성장점수** | `58` | 0~100 |
| `score_total` | **종합점수** | `71` | 0~100, 가중평균 |
| `perf_1w` | **1주수익률** | `+2.3%` | |
| `perf_1m` | **1월수익률** | `+5.1%` | |
| `perf_3m` | **3월수익률** | `+8.7%` | |
| `perf_6m` | **6월수익률** | `+15.2%` | |
| `perf_1y` | **1년수익률** | `+28.4%` | |
| `perf_ytd` | **YTD수익률** | `+12.1%` | |
| `country` | **소재국** | `US` | 영문 코드 |
| `earnings_date` | **실적발표일** | `2026-04-25` | 차기 |
| `index_membership` | **인덱스소속** | `S&P500` | |
| `shares_outstanding` | **발행주식수** | `15.4B` | |
| `float_shares` | **유통주식수** | `15.2B` | |
| `shares_short` | **공매도수량** | `98.2M` | |
| `short_ratio` | **공매도비율** | `1.54` | Days to cover |
| `short_pct_float` | **공매도/유통** | `0.65%` | |
| `insider_pct` | **내부자보유** | `0.07%` | |
| `inst_pct` | **기관보유** | `60.8%` | |
| `expense_ratio` | **운용수수료** | `0.03%` | ETF 전용 |
| `aum` | **순자산총액** | `$502.1B` | ETF 전용 |
| `nav` | **NAV** | `$517.23` | ETF 전용 |
| `holdings_count` | **구성종목수** | `503` | ETF 전용 |
| `index_tracked` | **추종지수** | `S&P 500` | 영문 원문 |
| `asset_class` | **자산군** | `Equity` | 영문 원문 |
| `is_active` | **운용방식** | `패시브` / `액티브` | 예외: boolean→한글 변환 |
| `inception_date` | **설정일** | `1993-01-22` | |
| `data_date` | **기준일** | `2026-03-28` | |
| `analyst_rating` | **투자의견** | `Buy` | yfinance Layer 1 |
| `analyst_rating_score` | **의견점수** | `1.8` | 1=Strong Buy ~ 5=Sell |
| `target_mean` | **목표가(평균)** | `$205.50` | |
| `target_high` | **목표가(최고)** | `$250.00` | |
| `target_low` | **목표가(최저)** | `$160.00` | |
| `analyst_count` | **애널리스트수** | `38` | |
| `target_upside_pct` | **목표괴리율** | `+15.0%` | (목표가-현재가)/현재가 |
| `insider_buy_3m` | **내부자매수(3M)** | `5` | 건수, EDGAR Form 4 |
| `insider_sell_3m` | **내부자매도(3M)** | `2` | 건수, EDGAR Form 4 |
| `insider_net_shares_3m` | **내부자순매수량** | `+125K` | 주식수 |
| `insider_latest_date` | **최근내부자거래** | `2026-03-25` | |
| `insider_latest_type` | **최근거래유형** | `Buy` / `Sale` | 배지 색상: 매수 빨강, 매도 파랑 |
| `inst_holders_13f` | **13F기관수** | `1,842` | EDGAR 원천 |
| `asof_price` | **가격기준일** | `2026-03-28` | 경과일 색상 표시 |
| `asof_financial` | **재무기준일** | `2025-12-31` | |
| `asof_short` | **공매도기준일** | `2026-03-15` | |
| `asof_insider` | **내부자기준일** | `2026-03-25` | |
| `asof_inst_13f` | **13F기준일** | `2025-12-31` | |
| `asof_analyst` | **애널리스트기준일** | `2026-03-28` | |
| `asof_etf` | **ETF기준일** | `2026-03-27` | ETF 전용 |
| `social_score` | **소셜점수** | `72` | X Sentiment (0~100), 상위 250만 |
| `social_bullish_pct` | **소셜긍정비** | `68.0%` | |
| `social_mentions_24h` | **24h언급수** | `1,842` | |
| `social_reddit_validated` | **Reddit교차** | `✓` / `-` | Reddit에서도 트렌딩 |
| `asof_social` | **소셜기준일** | `2026-03-22` | |

#### 6.4.2 필터 패널 — 라벨 매핑

| 필터 기능 | UI 라벨 (한글) | 입력 형태 |
|----------|---------------|----------|
| asset_type 토글 | **종목 유형** | `주식` / `ETF` 탭 |
| market_cap 범위 | **시가총액** | 슬라이더 (1M ~ 5T) |
| pe_ttm 범위 | **PER** | 최소~최대 입력 |
| dividend_yield 최소 | **배당률 (최소)** | 숫자 입력 (%) |
| pct_from_52h 범위 | **52주 고점 대비** | 슬라이더 (-50% ~ 0%) |
| sector 선택 | **섹터** | 멀티셀렉트 (값은 영문) |
| industry 선택 | **산업** | 멀티셀렉트 (값은 영문) |
| roe 범위 | **ROE (최소)** | 숫자 입력 (%) |
| debt_to_equity 범위 | **부채비율 (최대)** | 숫자 입력 |
| revenue_growth 범위 | **매출성장률 (최소)** | 숫자 입력 (%) |
| op_income_growth 범위 | **영업이익증가율 (최소)** | 숫자 입력 (%) ★상위500만 |
| interest_coverage 범위 | **이자보상배율 (최소)** | 숫자 입력 ★상위500만 |
| operating_margin 범위 | **영업이익률 (최소)** | 숫자 입력 (%) |
| short_pct_float 범위 | **공매도비율** | 슬라이더 (0%~50%) |
| analyst_rating 선택 | **투자의견** | Strong Buy / Buy / Hold / Sell |
| target_upside_pct 범위 | **목표괴리율 (최소)** | 숫자 입력 (%) |
| insider_buy_3m 범위 | **내부자매수(3M) (최소)** | 숫자 입력 (건) |
| insider_latest_type 선택 | **최근내부자거래** | Buy / Sale / 전체 |
| expense_ratio 최대 | **운용수수료 (최대)** | 숫자 입력 (%) |
| aum 최소 | **순자산총액 (최소)** | 숫자 입력 |
| asset_class 선택 | **자산군** | 단일셀렉트 (값은 영문) |
| Holdings 역산출 | **특정 종목 포함** | 티커 검색 + 최소비중(%) |

#### 6.4.3 정렬 옵션 — 라벨 매핑

| 정렬 기준 | UI 표시 (한글) |
|----------|---------------|
| market_cap | **시가총액순** |
| price | **현재가순** |
| change_pct | **등락률순** |
| volume | **거래량순** |
| turnover | **거래대금순** |
| pe_ttm | **PER순** |
| dividend_yield | **배당률순** |
| pct_from_52h | **고점대비순** |
| expense_ratio | **수수료순** (ETF) |
| aum | **순자산순** (ETF) |

> 모든 정렬은 **오름차순(▲) / 내림차순(▼)** 토글 지원

#### 6.4.4 Kotlin 구현 — 매핑 상수 객체

```kotlin
// ui/constants/ColumnLabels.kt

object ColumnLabels {
    val headers = mapOf(
        "symbol" to "티커",
        "name" to "종목명",
        "asset_type" to "유형",
        "exchange" to "거래소",
        "sector" to "섹터",
        "industry" to "산업",
        "price" to "현재가",
        "open_price" to "시가",
        "day_high" to "일중최고",
        "day_low" to "일중최저",
        "prev_close" to "전일종가",
        "change_pct" to "등락률",
        "volume" to "거래량",
        "avg_volume_10d" to "평균거래량(10일)",
        "turnover" to "거래대금",
        "market_cap" to "시가총액",
        "pe_ttm" to "PER",
        "pe_forward" to "예상PER",
        "pb_ratio" to "PBR",
        "eps_ttm" to "EPS",
        "dividend_yield" to "배당률",
        "dividend_rate" to "배당금(연)",
        "ex_dividend_date" to "배당락일",
        "avg_dividend_yield_5y" to "5년평균배당률",
        "ev" to "EV",
        "ev_ebitda" to "EV/EBITDA",
        "ev_revenue" to "EV/매출",
        "fcf_yield" to "FCF수익률",
        "total_revenue" to "매출액(TTM)",
        "ebitda" to "EBITDA",
        "free_cashflow" to "잉여현금흐름",
        "total_cash" to "현금성자산",
        "total_debt" to "총부채",
        "book_value" to "장부가/주",
        "revenue_per_share" to "매출/주",
        "piotroski_score" to "F-Score",
        "altman_z_score" to "Z-Score",
        "earnings_surprise_pct" to "실적서프라이즈",
        "week52_high" to "52주최고",
        "week52_low" to "52주최저",
        "pct_from_52h" to "고점대비",
        "pct_from_52l" to "저점대비",
        "ma_50" to "50일이평",
        "ma_200" to "200일이평",
        "beta" to "베타",
        "roe" to "ROE",
        "roa" to "ROA",
        "debt_to_equity" to "부채비율",
        "current_ratio" to "유동비율",
        "operating_margin" to "영업이익률",
        "profit_margin" to "순이익률",
        "gross_margin" to "매출총이익률",
        "revenue_growth" to "매출성장률",
        "earnings_growth" to "EPS성장률",
        "peg_ratio" to "PEG",
        "ps_ratio" to "PSR",
        "pfcf_ratio" to "P/FCF",
        "pcash_ratio" to "P/Cash",
        "op_income_growth" to "영업이익증가율",
        "debt_growth" to "차입금증가율",
        "interest_coverage" to "이자보상배율",
        "roic" to "ROIC",
        "quick_ratio" to "당좌비율",
        "lt_debt_equity" to "장기부채비율",
        "payout_ratio" to "배당성향",
        "eps_growth_this_yr" to "EPS성장률(금년)",
        "eps_growth_past_5y" to "EPS성장률(5Y)",
        "sales_growth_past_5y" to "매출성장률(5Y)",
        "inst_transactions_pct" to "기관거래변동",
        "rsi_14" to "RSI(14)",
        "atr_14" to "ATR(14)",
        "volatility_w" to "변동성(주)",
        "volatility_m" to "변동성(월)",
        "ma_20" to "20일이평",
        "sma20_pct" to "20일이평괴리",
        "sma50_pct" to "50일이평괴리",
        "sma200_pct" to "200일이평괴리",
        "gap_pct" to "갭",
        "change_from_open" to "시가대비",
        "relative_volume" to "상대거래량",
        "macd" to "MACD",
        "macd_signal" to "MACD시그널",
        "macd_hist" to "MACD히스토그램",
        "stoch_k" to "Stoch %K",
        "stoch_d" to "Stoch %D",
        "adx_14" to "ADX(14)",
        "cci_20" to "CCI(20)",
        "williams_r" to "Williams %R",
        "dcf_value" to "DCF내재가치",
        "dcf_upside_pct" to "DCF괴리율",
        "graham_number" to "Graham적정가",
        "graham_upside_pct" to "Graham괴리율",
        "accruals_ratio" to "Accruals비율",
        "fcf_to_ni" to "FCF/순이익",
        "earnings_quality_score" to "수익품질점수",
        "buyback_yield" to "자사주매입률",
        "shareholder_yield" to "주주환원율",
        "capex_to_revenue" to "CAPEX/매출",
        "score_value" to "밸류점수",
        "score_quality" to "퀄리티점수",
        "score_momentum" to "모멘텀점수",
        "score_growth" to "성장점수",
        "score_total" to "종합점수",
        "perf_1w" to "1주수익률",
        "perf_1m" to "1월수익률",
        "perf_3m" to "3월수익률",
        "perf_6m" to "6월수익률",
        "perf_1y" to "1년수익률",
        "perf_ytd" to "YTD수익률",
        "country" to "소재국",
        "earnings_date" to "실적발표일",
        "index_membership" to "인덱스소속",
        "asof_deep_financial" to "심화재무기준일",
        "shares_outstanding" to "발행주식수",
        "float_shares" to "유통주식수",
        "shares_short" to "공매도수량",
        "short_ratio" to "공매도비율",
        "short_pct_float" to "공매도/유통",
        "insider_pct" to "내부자보유",
        "inst_pct" to "기관보유",
        "expense_ratio" to "운용수수료",
        "aum" to "순자산총액",
        "nav" to "NAV",
        "holdings_count" to "구성종목수",
        "index_tracked" to "추종지수",
        "asset_class" to "자산군",
        "is_active" to "운용방식",
        "inception_date" to "설정일",
        "data_date" to "기준일",
        "analyst_rating" to "투자의견",
        "analyst_rating_score" to "의견점수",
        "target_mean" to "목표가(평균)",
        "target_high" to "목표가(최고)",
        "target_low" to "목표가(최저)",
        "analyst_count" to "애널리스트수",
        "target_upside_pct" to "목표괴리율",
        "insider_buy_3m" to "내부자매수(3M)",
        "insider_sell_3m" to "내부자매도(3M)",
        "insider_net_shares_3m" to "내부자순매수량",
        "insider_latest_date" to "최근내부자거래",
        "insider_latest_type" to "최근거래유형",
        "inst_holders_13f" to "13F기관수",
        "asof_price" to "가격기준일",
        "asof_financial" to "재무기준일",
        "asof_valuation" to "밸류에이션기준일",
        "asof_dividend" to "배당기준일",
        "asof_technical" to "기술적기준일",
        "asof_short" to "공매도기준일",
        "asof_insider" to "내부자기준일",
        "asof_inst_13f" to "13F기준일",
        "asof_analyst" to "애널리스트기준일",
        "asof_etf" to "ETF기준일",
        "social_score" to "소셜점수",
        "social_bullish_pct" to "소셜긍정비",
        "social_mentions_24h" to "24h언급수",
        "social_reddit_validated" to "Reddit교차",
        "asof_social" to "소셜기준일",
    )

    val sortOptions = mapOf(
        "market_cap" to "시가총액순",
        "price" to "현재가순",
        "change_pct" to "등락률순",
        "volume" to "거래량순",
        "turnover" to "거래대금순",
        "pe_ttm" to "PER순",
        "ev_ebitda" to "EV/EBITDA순",
        "fcf_yield" to "FCF수익률순",
        "piotroski_score" to "F-Score순",
        "dividend_yield" to "배당률순",
        "pct_from_52h" to "고점대비순",
        "expense_ratio" to "수수료순",
        "aum" to "순자산순",
        "target_upside_pct" to "목표괴리율순",
        "insider_buy_3m" to "내부자매수순",
        "social_score" to "소셜점수순",
        "roe" to "ROE순",
        "revenue_growth" to "매출성장률순",
        "earnings_growth" to "EPS성장률순",
        "interest_coverage" to "이자보상배율순",
        "rsi_14" to "RSI순",
        "relative_volume" to "상대거래량순",
        "perf_1w" to "1주수익률순",
        "perf_1m" to "1월수익률순",
        "perf_1y" to "1년수익률순",
        "score_total" to "종합점수순",
        "score_value" to "밸류점수순",
        "score_quality" to "퀄리티점수순",
        "score_momentum" to "모멘텀점수순",
        "score_growth" to "성장점수순",
        "dcf_upside_pct" to "DCF괴리율순",
        "shareholder_yield" to "주주환원율순",
        "short_pct_float" to "공매도순",
    )

    // boolean → 한글 변환 (유일한 예외)
    fun formatActiveStatus(isActive: Boolean?) = when (isActive) {
        true -> "액티브"
        false -> "패시브"
        null -> "-"
    }
}
```

#### 6.4.5 숫자 포맷터 (한국식 축약)

```kotlin
// ui/utils/NumberFormatter.kt

object NumberFormatter {
    /** 시가총액·AUM·거래대금 축약: $2.8T, $502.1B, $9.3M */
    fun formatUsd(value: Long?): String {
        if (value == null) return "-"
        return when {
            value >= 1_000_000_000_000 -> "$${String.format("%.1f", value / 1e12)}T"
            value >= 1_000_000_000     -> "$${String.format("%.1f", value / 1e9)}B"
            value >= 1_000_000         -> "$${String.format("%.1f", value / 1e6)}M"
            value >= 1_000             -> "$${String.format("%.1f", value / 1e3)}K"
            else -> "$$value"
        }
    }

    /** 거래량 축약: 52.3M, 1.2B */
    fun formatVolume(value: Long?): String {
        if (value == null) return "-"
        return when {
            value >= 1_000_000_000 -> "${String.format("%.1f", value / 1e9)}B"
            value >= 1_000_000     -> "${String.format("%.1f", value / 1e6)}M"
            value >= 1_000         -> "${String.format("%.1f", value / 1e3)}K"
            else -> "$value"
        }
    }

    /** 등락률: +0.89%, -1.23% (부호 포함) */
    fun formatPct(value: Double?): String {
        if (value == null) return "-"
        val sign = if (value > 0) "+" else ""
        return "$sign${String.format("%.2f", value)}%"
    }

    /** 가격: $178.72 */
    fun formatPrice(value: Double?): String {
        if (value == null) return "-"
        return "$${String.format("%.2f", value)}"
    }

    /** 배당률: 소수 → 백분율 변환 (0.0065 → 0.65%) */
    fun formatYield(value: Double?): String {
        if (value == null) return "-"
        return "${String.format("%.2f", value * 100)}%"
    }

    /** 수수료: 소수 → 백분율 변환 (0.0003 → 0.03%) */
    fun formatExpenseRatio(value: Double?): String {
        if (value == null) return "-"
        return "${String.format("%.2f", value * 100)}%"
    }

    /** 재무비율: 소수 → 백분율 (0.302 → 30.2%), ROE/ROA/마진 등 */
    fun formatRatio(value: Double?): String {
        if (value == null) return "-"
        return "${String.format("%.1f", value * 100)}%"
    }

    /** 공매도비율: 소수 → 백분율 (0.0065 → 0.65%) */
    fun formatShortPct(value: Double?): String {
        if (value == null) return "-"
        return "${String.format("%.2f", value * 100)}%"
    }

    /** 대형 수량 축약: 15.4B, 98.2M (주식수 등) */
    fun formatShares(value: Long?): String = formatVolume(value)
}
```

### 6.5 종목 상세 화면 — 4탭 구조

종목 리스트에서 항목 탭 시 전개되는 상세 화면. 4개 탭으로 분류.

```
┌─────────────────────────────────────────┐
│  ← AAPL                     ⭐ (관심 추가) │
│  Apple Inc.         NASDAQ               │
│  $178.72  +0.89%                         │
├─────────────────────────────────────────┤
│  개요  │  재무  │  수급  │  애널리스트      │
├─────────────────────────────────────────┤
│                                          │
│  (선택된 탭 콘텐츠)                        │
│                                          │
└─────────────────────────────────────────┘
```

#### [개요] 탭

| 구역 | 항목 |
|------|------|
| 가격 정보 | 현재가, 시가, 일중최고/최저, 전일종가, 등락률, 갭, 시가대비 |
| 시장 정보 | 시가총액, 거래량, 평균거래량(10일), 거래대금, 상대거래량 |
| 밸류에이션 | PER, 예상PER, PBR, PEG, PSR, P/FCF, **EV/EBITDA**, **EV/Revenue**, EPS |
| 배당 | 배당률, 배당금(연), 배당성향, **배당락일**, **5년평균배당률** |
| 기술적 | 52주최고/최저, 고점/저점대비, 20일/50일/200일이평, MA괴리율, RSI(14), ATR(14), 베타 |
| 모멘텀 | MACD(히스토그램), Stochastic(%K/%D), ADX(14), CCI(20), Williams %R |
| 밸류에이션 모델 | DCF내재가치 + 괴리율, Graham적정가 + 괴리율 |
| 성과 | 1주/1월/3월/6월/1년/YTD 수익률, 변동성(주/월) |
| 종합 점수 | **종합/밸류/퀄리티/모멘텀/성장** 각 0~100 (레이더 차트) |
| 분류 | 섹터, 산업, 거래소, 소재국, 인덱스소속 |
| 일정 | 실적발표일 |
| 메타 | 기준일, 데이터 갱신 시각 |

#### [재무] 탭

| 구역 | 항목 |
|------|------|
| 수익성 | ROE, ROA, ROIC★, 영업이익률, 순이익률, 매출총이익률 |
| 성장성 | 매출성장률(YoY), EPS성장률(YoY), 영업이익증가율★, EPS성장률(5Y)★, 매출성장률(5Y)★ |
| 안정성 | 부채비율(D/E), 장기부채비율★, 유동비율, 당좌비율, 차입금증가율★, 이자보상배율★ |
| 규모 | 발행주식수, 유통주식수 |
| 실적 절대값 | **매출액(TTM)**, **EBITDA**, **FCF**, **현금성자산**, **총부채**, **EV** |
| 주당 지표 | EPS, **장부가/주**, **매출/주** |
| 현금흐름 | **FCF수익률**, **P/Cash★** |
| 밸류에이션 보조 | EPS성장률(금년), **실적서프라이즈** |
| 종합 점수 | **F-Score★** (0~9), **Z-Score★** (>3 안전, <1.8 위험) |
| 수익 품질★ | Accruals비율, FCF/순이익, 수익품질점수 |
| 자본 효율성★ | 자사주매입률, 주주환원율(배당+자사주), CAPEX/매출 |
| 기준일 표시 | 📅 재무기준일 + 📅 심화재무기준일 (★ = 상위 500 외는 FMP 온디맨드) |

#### [수급] 탭

| 구역 | 항목 |
|------|------|
| 공매도 | 공매도수량, 공매도비율(Days to Cover), 공매도/유통비율 |
| 보유 구조 | 내부자보유비율, 기관보유비율, 13F기관수, **기관거래변동★** |
| 내부자 거래 (EDGAR) | 최근내부자거래일, 최근거래유형(Buy/Sale 배지) |
| 내부자 3개월 요약 | 내부자매수(3M) 건수, 내부자매도(3M) 건수, 순매수량 |
| 거래 상세 리스트 | `insider_trades` 테이블에서 최근 10건 (이름, 직위, 유형, 수량, 가격, 날짜) |

#### [애널리스트] 탭

| 구역 | 항목 | 소스 |
|------|------|------|
| 컨센서스 요약 | 투자의견, 의견점수, 애널리스트수 | yfinance (DB) |
| 목표가 | 평균/최고/최저 목표가, 목표괴리율 | yfinance (DB) |
| 소셜 센티먼트 (DB) | 소셜점수, 소셜긍정비, 24h언급수, Reddit교차 | X Sentiment API (DB, 상위 250만) |
| 소셜 버즈 상세 (실시간) | StockTwits/Twitter 게시물수, 좋아요, 센티먼트 | FMP 온디맨드 (API 실시간 호출) |
| 개별 의견 상세 | 애널리스트별 의견·목표가·변경 이력 | FMP 온디맨드 (API 실시간 호출) |

> **개별 의견 상세**는 DB에 없고, 탭 진입 시 FMP `/stable/grades?symbol=AAPL` 1건 호출.
> Edge Function 경유, `fmp_cache` 테이블에 TTL 30분 캐시. 동일 종목 반복 조회 시 FMP req 미소비.

> **소셜 센티먼트 NULL 처리 (Nice-to-Have 데이터)**:
> 소셜 데이터(`social_score` 등)가 NULL인 종목은 애널리스트 탭의 "소셜 센티먼트" 섹션을
> `"소셜 데이터 없음"` 안내 텍스트로 대체 표시. 필터 패널의 소셜점수 슬라이더는 NULL 종목을
> 자동 제외 (PostgreSQL 기본 동작). 프리셋 💬소셜버즈 적용 시 결과가 0건이면
> `"소셜 데이터가 아직 수집되지 않았습니다"` 토스트 표시.
> X Sentiment API가 영구 중단되어도 이 섹션만 비활성화되며 나머지 기능에 영향 없음.

#### 데이터 기준일 표시 (모든 탭 공통)

> **핵심 원칙**: 사용자는 모든 지표가 "언제 기준 데이터인지" 즉시 확인할 수 있어야 한다.
> 배치 실행일(3/30)과 데이터 기준일(3/15)이 다를 수 있으므로, **기준일(as-of date)**을 표시한다.

**필드 그룹 → DB 기준일 컬럼 매핑**:

| 그룹명 | 대상 필드 | DB 컬럼 | 갱신 주기 | 지연 원인 |
|--------|----------|---------|----------|----------|
| **가격·거래** | 현재가, 시가, 고가/저가, 거래량, 거래대금, 등락률, 시총 | `asof_price` | 매일 | 없음 (전일 MOC 확정) |
| **기술적** | 52주고저, 고점/저점대비, 50일/200일이평, 베타 | `asof_technical` | 매일 | 없음 |
| **밸류에이션** | PER, 예상PER, PBR, EPS, PEG | `asof_valuation` | 매일~분기 | 분기 실적 발표 후 1~2일 |
| **배당** | 배당률, 배당금(연) | `asof_dividend` | 수시 | 배당 공시 후 수일 |
| **재무건전성** | ROE, ROA, 부채비율, 유동비율, 이익률, 성장률 | `asof_financial` | 분기 | **최대 3개월** (분기 실적 기준) |
| **심화 재무** | 영업이익증가율, 차입금증가율, 이자보상배율 | `asof_deep_financial` | 분기 | **최대 3개월** (상위 500만 DB, 나머지 온디맨드) |
| **공매도** | 공매도수량, 공매도비율, 공매도/유통비율 | `asof_short` | 월 2회 | **약 10~14일** (FINRA 공시 지연) |
| **보유 구조** | 내부자보유비율, 기관보유비율 | `asof_insider` | 분기 | **최대 45일** (SEC 13F 제출 기한) |
| **13F 기관** | 13F기관수 | `asof_inst_13f` | 분기 | 동일 |
| **내부자 거래** | 내부자매수(3M), 매도(3M), 순매수량, 최근거래 | `asof_insider` | 매일 | Form 4: 거래 후 2영업일 |
| **애널리스트** | 투자의견, 목표가, 애널리스트수, 괴리율 | `asof_analyst` | 매일 | 수시간 내 반영 |
| **소셜 센티먼트** | 소셜점수, 소셜긍정비, 24h언급수, Reddit교차 | `asof_social` | 주 1회 | X Sentiment API 주기 |
| **ETF** | 수수료, AUM, NAV, 구성종목, 추종지수 | `asof_etf` | 롤링 | Tier별 상이 |

**UI 표시 방식 — 탭별 기준일 배너**:

각 탭 상단에 해당 탭 데이터의 기준일을 라이트 텍스트로 표시.
기준일이 오래됐을수록 경고 색상 적용.

```
┌─────────────────────────────────────────┐
│  개요  │  재무  │  수급  │  애널리스트    │
├─────────────────────────────────────────┤
│                                          │
│  📅 가격·거래: 2026-03-28 (금)           │ ← 회색 (최신)
│  📅 밸류에이션: 2026-03-28               │ ← 회색 (최신)
│  📅 기술적: 2026-03-28                   │ ← 회색 (최신)
│  📅 배당: 2026-02-15                     │ ← 노랑 (41일 경과)
│                                          │
│  현재가   $178.72  (+0.89%)              │
│  시가총액  $2.8T                          │
│  ...                                     │
└─────────────────────────────────────────┘

[수급 탭 예시]
┌─────────────────────────────────────────┐
│  개요  │  재무  │  수급  │  애널리스트    │
├─────────────────────────────────────────┤
│                                          │
│  📅 공매도: 2026-03-15 (15일 전)         │ ← 주황 (2주 지연)
│  📅 내부자 거래: 2026-03-28              │ ← 회색 (최신)
│  📅 보유 구조: 2025-12-31 (89일 전)      │ ← 빨강 (분기 지연)
│  📅 13F 기관: 2025-12-31 (89일 전)       │ ← 빨강
│                                          │
│  ── 공매도 ──                             │
│  공매도수량      98.2M                    │
│  공매도비율      1.54                     │
│  공매도/유통     0.65%                    │
│  ...                                     │
└─────────────────────────────────────────┘
```

**기준일 경과 색상 로직**:

```kotlin
// ui/utils/FreshnessIndicator.kt

enum class FreshnessLevel(val color: @Composable () -> Color, val label: String) {
    FRESH({ MaterialTheme.colorScheme.onSurfaceVariant }, "최신"),
    MODERATE({ Color(0xFFE6A817) }, "지연"),       // 노랑: 7~30일
    STALE({ Color(0xFFFF6B35) }, "오래됨"),          // 주황: 30~60일
    VERY_STALE({ Color(0xFFDA3633) }, "매우 오래됨"), // 빨강: 60일+
}

fun getFreshnessLevel(asOfDate: LocalDate?): FreshnessLevel {
    if (asOfDate == null) return FreshnessLevel.VERY_STALE
    val daysOld = ChronoUnit.DAYS.between(asOfDate, LocalDate.now())
    return when {
        daysOld <= 7   -> FreshnessLevel.FRESH
        daysOld <= 30  -> FreshnessLevel.MODERATE
        daysOld <= 60  -> FreshnessLevel.STALE
        else           -> FreshnessLevel.VERY_STALE
    }
}

@Composable
fun FreshnessBadge(label: String, asOfDate: LocalDate?) {
    val level = getFreshnessLevel(asOfDate)
    val daysOld = asOfDate?.let {
        ChronoUnit.DAYS.between(it, LocalDate.now())
    }
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "📅 $label: ${asOfDate ?: "N/A"}",
            style = MaterialTheme.typography.bodySmall,
            color = level.color()
        )
        if (daysOld != null && daysOld > 7) {
            Text(
                text = " (${daysOld}일 전)",
                style = MaterialTheme.typography.bodySmall,
                color = level.color()
            )
        }
    }
}
```

**배치에서 기준일 산출 방법**:

```python
# transforms/freshness.py

from datetime import date

def compute_freshness_dates(row: dict, source: str) -> dict:
    """
    각 소스별로 데이터의 실제 기준일(as-of date)을 산출.
    배치 실행일이 아니라 데이터 자체의 기준일을 추적.
    """
    today = date.today()
    
    if source == 'yfinance':
        # 가격: 항상 전 영업일 (= data_date)
        row['asof_price'] = row.get('data_date', today)
        row['asof_technical'] = row.get('data_date', today)
        
        # 밸류에이션: yfinance가 반환하는 값의 기준은 최신 분기
        # mostRecentQuarter 필드가 있으면 사용, 없으면 price와 동일
        row['asof_valuation'] = row.get('most_recent_quarter', row.get('data_date', today))
        
        # 재무: 분기말 (lastFiscalYearEnd 또는 mostRecentQuarter)
        row['asof_financial'] = row.get('most_recent_quarter', None)
        
        # 배당: lastDividendDate
        row['asof_dividend'] = row.get('last_dividend_date', None)
        
        # 공매도: dateShortInterest (yfinance에서 제공 시)
        row['asof_short'] = row.get('date_short_interest', None)
        
        # 보유구조: 분기 기준 (정확한 날짜 yfinance 미제공 → 추정)
        row['asof_insider'] = row.get('most_recent_quarter', None)
        
        # 애널리스트: 항상 최신 (수시 업데이트)
        row['asof_analyst'] = row.get('data_date', today)
    
    elif source == 'edgar':
        # Form 4: 가장 최근 거래일
        row['asof_insider'] = row.get('insider_latest_date', None)
        
        # 13F: 보고 기준 분기말
        row['asof_inst_13f'] = row.get('report_period', None)
    
    elif source == 'fmp_etf':
        row['asof_etf'] = row.get('data_date', today)
    
    return row
```

### 6.6 관심종목 기능 (Room DB 로컬 저장)

서버(Supabase)가 아닌 **Android 기기 로컬**에 저장. 로그인 불필요, 오프라인 동작.

#### Room DB 스키마

```kotlin
// data/local/entity/WatchlistItem.kt

@Entity(tableName = "watchlist")
data class WatchlistItem(
    @PrimaryKey
    val symbol: String,              // 티커 (AAPL)
    val name: String,                // 종목명
    val assetType: String,           // "stock" | "etf"
    val addedAt: Long = System.currentTimeMillis(),  // 추가 시각
    val memo: String? = null,        // 사용자 메모 (선택)
    val notifyPriceAbove: Double? = null,  // 가격 알림 상한 (향후)
    val notifyPriceBelow: Double? = null,  // 가격 알림 하한 (향후)
)
```

#### DAO (Data Access Object)

```kotlin
// data/local/dao/WatchlistDao.kt

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    fun getAll(): Flow<List<WatchlistItem>>

    @Query("SELECT symbol FROM watchlist")
    suspend fun getAllSymbols(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE symbol = :symbol)")
    fun isWatched(symbol: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(item: WatchlistItem)

    @Delete
    suspend fun remove(item: WatchlistItem)

    @Query("DELETE FROM watchlist WHERE symbol = :symbol")
    suspend fun removeBySymbol(symbol: String)

    @Query("UPDATE watchlist SET memo = :memo WHERE symbol = :symbol")
    suspend fun updateMemo(symbol: String, memo: String?)

    @Query("SELECT COUNT(*) FROM watchlist")
    fun count(): Flow<Int>
}
```

#### 관심종목 화면 동작

```
[관심 ⭐ 탭]
├── 관심종목이 없으면: 빈 상태 안내 ("종목을 검색해서 ⭐ 를 눌러보세요")
├── 관심종목 리스트 (LazyColumn)
│   └── 각 항목:
│       ├── Room DB에서 symbol 목록 로드
│       ├── Supabase에서 해당 symbol들의 최신 데이터 일괄 조회
│       │   GET /rest/v1/latest_equities?symbol=in.(AAPL,MSFT,TSLA)
│       ├── 카드 표시 (티커, 종목명, 현재가, 등락률, 주요 지표)
│       └── 스와이프 삭제 또는 길게 눌러 메모 편집
└── 정렬: 추가순 / 등락률순 / 시총순 (토글)
```

### 6.7 Android 앱 아키텍처 (MVVM + Repository)

```
┌─────────────────────────────────────────────────────┐
│                    UI Layer                           │
│  Jetpack Compose Screens + ViewModels                │
│                                                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐           │
│  │ Screener │  │ HeatMap  │  │ Watchlist │           │
│  │ Screen   │  │ Screen   │  │ Screen   │           │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘           │
│       │              │              │                 │
│  ┌────▼─────┐  ┌────▼─────┐  ┌────▼─────┐           │
│  │ Screener │  │ HeatMap  │  │ Watchlist │           │
│  │ ViewModel│  │ ViewModel│  │ ViewModel│           │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘           │
├───────┼──────────────┼──────────────┼────────────────┤
│       │        Domain Layer         │                 │
│       │    (Use Cases / 선택적)      │                 │
├───────┼──────────────┼──────────────┼────────────────┤
│       │         Data Layer          │                 │
│  ┌────▼──────────────▼──────────────▼─────┐          │
│  │           Repository                    │          │
│  │  ┌─────────────┐  ┌─────────────────┐  │          │
│  │  │ Remote      │  │ Local           │  │          │
│  │  │ DataSource  │  │ DataSource      │  │          │
│  │  │ (Supabase)  │  │ (Room DB)       │  │          │
│  │  └──────┬──────┘  └──────┬──────────┘  │          │
│  └─────────┼────────────────┼─────────────┘          │
│            │                │                         │
│     ┌──────▼──────┐  ┌─────▼──────┐                  │
│     │ Retrofit /  │  │ Room       │                  │
│     │ Ktor Client │  │ Database   │                  │
│     │ → Supabase  │  │ → watchlist│                  │
│     └─────────────┘  └────────────┘                  │
└─────────────────────────────────────────────────────┘
```

#### 패키지 구조

```
com.example.stockscreener/
├── data/
│   ├── remote/
│   │   ├── SupabaseApi.kt            # Retrofit 인터페이스
│   │   ├── SupabaseClient.kt         # 싱글톤 클라이언트 (BuildConfig에서 Key 로드)
│   │   ├── dto/                      # API 응답 DTO
│   │   │   ├── EquityDto.kt
│   │   │   └── InsiderTradeDto.kt
│   │   └── EdgeFunctionApi.kt        # ETF 역산출 등
│   ├── local/
│   │   ├── AppDatabase.kt            # Room DB 정의
│   │   ├── entity/
│   │   │   └── WatchlistItem.kt
│   │   ├── dao/
│   │   │   └── WatchlistDao.kt
│   │   ├── ThemePreferences.kt       # 다크/라이트 테마 설정
│   │   └── ScoreWeightPreferences.kt # 종합 점수 가중치 커스터마이징
│   └── repository/
│       ├── EquityRepository.kt       # 메인 데이터 접근
│       └── WatchlistRepository.kt    # 관심종목 데이터
├── domain/
│   └── model/
│       ├── Equity.kt                 # 도메인 모델
│       ├── FilterCriteria.kt         # 필터 조건 객체
│       ├── PresetSignal.kt           # 프리셋 시그널 정의
│       └── ScoreWeights.kt           # 종합 점수 가중치 모델
├── ui/
│   ├── navigation/
│   │   └── AppNavigation.kt          # 하단 네비게이션
│   ├── screener/
│   │   ├── ScreenerScreen.kt         # 스크리너 메인
│   │   ├── ScreenerViewModel.kt
│   │   ├── FilterPanel.kt            # 필터 패널 컴포넌트
│   │   ├── PresetChips.kt            # 프리셋 시그널 칩
│   │   └── EquityListItem.kt         # 종목 카드 아이템
│   ├── detail/
│   │   ├── DetailScreen.kt           # 종목 상세 (4탭)
│   │   ├── DetailViewModel.kt
│   │   ├── tabs/
│   │   │   ├── OverviewTab.kt        # 개요 탭
│   │   │   ├── FinancialTab.kt       # 재무 탭
│   │   │   ├── SupplyDemandTab.kt    # 수급 탭
│   │   │   └── AnalystTab.kt         # 애널리스트 탭
│   │   └── InsiderTradeList.kt       # 내부자 거래 리스트
│   ├── heatmap/
│   │   ├── HeatMapScreen.kt          # 히트맵 화면 (WebView + D3.js)
│   │   ├── HeatMapViewModel.kt
│   │   └── HeatMapBridge.kt          # JS ↔ Native 브릿지
│   ├── watchlist/
│   │   ├── WatchlistScreen.kt        # 관심종목 화면
│   │   └── WatchlistViewModel.kt
│   ├── settings/
│   │   ├── SettingsScreen.kt         # 설정 화면 (테마 + 점수 가중치)
│   │   └── ScoreWeightSliders.kt     # 가중치 슬라이더 컴포넌트
│   ├── search/
│   │   ├── SearchBar.kt              # 검색 자동완성
│   │   └── SearchViewModel.kt
│   ├── constants/
│   │   └── ColumnLabels.kt           # 한글 헤더 매핑 + 영문 메타
│   ├── components/
│   │   └── TooltipHeader.kt          # 길게 누르면 영문 원본 표시
│   ├── utils/
│   │   ├── NumberFormatter.kt        # 숫자 포맷터
│   │   └── FreshnessIndicator.kt     # 기준일 경과 색상 표시
│   └── theme/
│       └── Theme.kt                  # Material3 테마
├── di/
│   └── AppModule.kt                  # Hilt DI 모듈
├── assets/
│   └── heatmap.html                  # D3.js Treemap 렌더링 (WebView용)
└── StockScreenerApp.kt               # Application 클래스
```

#### 핵심 의존성 (Android build.gradle.kts)

```kotlin
// Jetpack Compose
implementation(platform("androidx.compose:compose-bom:2024.12.01"))
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.ui:ui")
implementation("androidx.activity:activity-compose:1.9.3")
implementation("androidx.navigation:navigation-compose:2.8.5")

// ViewModel + Lifecycle
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

// Room DB (관심종목 로컬 저장)
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
ksp("androidx.room:room-compiler:2.6.1")

// 네트워크 (Supabase 통신)
implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.squareup.retrofit2:converter-gson:2.11.0")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

// DI
implementation("com.google.dagger:hilt-android:2.51.1")
ksp("com.google.dagger:hilt-compiler:2.51.1")
implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
```

### 6.9 디자인 스펙 — Finviz 스타일 데이터 밀도 + 다크/라이트 전환

#### 디자인 원칙

```
1. 데이터 밀도 최우선: 한 화면에 최대한 많은 종목 + 지표 노출
2. 테이블 중심 레이아웃: 카드 UI가 아닌, 컴팩트한 행(row) 기반
3. 색상은 정보 전달 수단: 상승=빨강, 하락=파랑(한국식), 중립=회색
4. 여백 최소화: 패딩 4~8dp, 폰트 12~14sp, 행 높이 40~48dp
5. 다크/라이트 시스템 테마 연동 + 수동 전환 지원
```

#### 컬러 팔레트

```
[다크모드 — 기본]
┌─────────────────────────────────────┐
│ 배경 (Surface)     #0D1117          │  ← GitHub Dark 계열
│ 카드/행 배경        #161B22          │
│ 행 교대 배경        #1C2128          │  ← 짝수행 구분
│ 테두리/구분선       #30363D          │
│ 텍스트 (Primary)   #E6EDF3          │
│ 텍스트 (Secondary) #8B949E          │  ← 보조 지표
│ 상승 (Positive)    #FF4747          │  ← 한국 주식 빨강
│ 하락 (Negative)    #4D8BFF          │  ← 한국 주식 파랑
│ 강조 (Accent)      #58A6FF          │
│ 배지 Buy           #2EA043          │  ← 녹색
│ 배지 Sell          #DA3633          │
└─────────────────────────────────────┘

[라이트모드]
┌─────────────────────────────────────┐
│ 배경 (Surface)     #FFFFFF          │
│ 카드/행 배경        #FFFFFF          │
│ 행 교대 배경        #F6F8FA          │
│ 테두리/구분선       #D1D9E0          │
│ 텍스트 (Primary)   #1F2328          │
│ 텍스트 (Secondary) #656D76          │
│ 상승 (Positive)    #D32F2F          │
│ 하락 (Negative)    #1565C0          │
│ 강조 (Accent)      #0969DA          │
│ 배지 Buy           #1A7F37          │
│ 배지 Sell          #CF222E          │
└─────────────────────────────────────┘
```

#### Kotlin 테마 구현

```kotlin
// ui/theme/Color.kt

// 다크모드
val Dark_Surface = Color(0xFF0D1117)
val Dark_Card = Color(0xFF161B22)
val Dark_CardAlt = Color(0xFF1C2128)
val Dark_Border = Color(0xFF30363D)
val Dark_TextPrimary = Color(0xFFE6EDF3)
val Dark_TextSecondary = Color(0xFF8B949E)
val Dark_Positive = Color(0xFFFF4747)
val Dark_Negative = Color(0xFF4D8BFF)
val Dark_Accent = Color(0xFF58A6FF)

// 라이트모드
val Light_Surface = Color(0xFFFFFFFF)
val Light_Card = Color(0xFFFFFFFF)
val Light_CardAlt = Color(0xFFF6F8FA)
val Light_Border = Color(0xFFD1D9E0)
val Light_TextPrimary = Color(0xFF1F2328)
val Light_TextSecondary = Color(0xFF656D76)
val Light_Positive = Color(0xFFD32F2F)
val Light_Negative = Color(0xFF1565C0)
val Light_Accent = Color(0xFF0969DA)
```

```kotlin
// ui/theme/Theme.kt

@Composable
fun StockScreenerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) darkColorScheme(
        surface = Dark_Surface,
        background = Dark_Surface,
        onSurface = Dark_TextPrimary,
        onBackground = Dark_TextPrimary,
        primary = Dark_Accent,
    ) else lightColorScheme(
        surface = Light_Surface,
        background = Light_Surface,
        onSurface = Light_TextPrimary,
        onBackground = Light_TextPrimary,
        primary = Light_Accent,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = StockTypography,  // 컴팩트 폰트
        content = content
    )
}
```

#### 타이포그래피 (데이터 밀도 최적화)

```kotlin
// ui/theme/Type.kt

val StockTypography = Typography(
    // 종목명, 헤더
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    // 가격, 주요 수치
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        fontFamily = FontFamily.Monospace,  // 숫자 정렬용 고정폭
    ),
    // 보조 지표, 라벨
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
    // 필터 칩, 프리셋 시그널
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
    ),
)
```

#### 스크리너 메인 — Finviz 스타일 테이블 레이아웃

```
┌─────────────────────────────────────────────────────┐
│ 🔍 티커 또는 종목명 검색...              [필터 ▼]   │
├─────────────────────────────────────────────────────┤
│ 🔥거래량급등 📈신고가 💰고배당 🚀StrongBuy 🔑내부자.. │ ← 프리셋 칩 (가로스크롤)
├──────┬───────────┬────────┬────────┬───────┬────────┤
│ 티커 │ 종목명     │ 현재가  │ 등락률  │ 시총   │ PER  │ ← 컬럼 헤더 (탭하면 정렬)
├──────┼───────────┼────────┼────────┼───────┼────────┤
│ AAPL │ Apple Inc.│ 178.72 │ +0.89% │ 2.8T  │ 28.54│ ← 행 높이 44dp
│      │           │        │ 빨강   │       │      │
├──────┼───────────┼────────┼────────┼───────┼────────┤
│ MSFT │ Microsoft │ 415.30 │ -0.42% │ 3.1T  │ 35.12│ ← 교대 배경색
│      │ Corp.     │        │ 파랑   │       │      │
├──────┼───────────┼────────┼────────┼───────┼────────┤
│ NVDA │ NVIDIA    │ 875.40 │ +2.15% │ 2.2T  │ 62.30│
│      │ Corp.     │        │ 빨강   │       │      │
├──────┴───────────┴────────┴────────┴───────┴────────┤
│                    ← 1 / 300 →                      │ ← 페이지네이션
└─────────────────────────────────────────────────────┘

* 가로 스크롤: 추가 컬럼 (배당률, 52주고점대비, ROE 등) 오른쪽으로 확장
* 컬럼 헤더 탭 → 오름/내림 정렬 토글 (▲▼ 아이콘)
* 행 탭 → 종목 상세 화면 (4탭)
* 컬럼 표시/숨김: 설정에서 사용자 커스텀 가능 (향후)
```

#### 행(Row) 컴포넌트 사양

```
행 높이: 44dp (Finviz 밀도 참고, 일반 앱 대비 약 30% 컴팩트)
좌우 패딩: 8dp
셀 간 패딩: 4dp
티커 폰트: 13sp SemiBold 고정폭
가격 폰트: 13sp Medium 고정폭 (소수점 정렬)
등락률 폰트: 12sp Bold + 배경 라운드 (빨강/파랑)
보조 지표: 11sp Regular 회색
행 구분: 1dp 구분선 또는 교대 배경색
```

#### 히트맵 화면 디자인

```
┌─────────────────────────────────────────┐
│  기준: [등락률 ▼]   기간: [1일 ▼]       │
├─────────────────────────────────────────┤
│ ┌──────────────────┬────────────┐       │
│ │   Technology     │  Health    │       │
│ │ ┌──────┬───────┐ │ ┌────────┐│       │
│ │ │ AAPL │ MSFT  │ │ │  UNH   ││       │
│ │ │+0.89%│-0.42% │ │ │ +1.2%  ││       │
│ │ │ 빨강  │ 파랑  │ │ │  빨강   ││       │
│ │ ├──────┼───────┤ │ ├────────┤│       │
│ │ │ NVDA │ AVGO  │ │ │  JNJ   ││       │
│ │ │+2.15%│+0.33% │ │ │ -0.5%  ││       │
│ │ └──────┴───────┘ │ └────────┘│       │
│ ├──────────────────┼────────────┤       │
│ │   Financials     │  Energy   │       │
│ │ ┌──────┬───────┐ │ ┌────────┐│       │
│ │ │  JPM │  BAC  │ │ │  XOM   ││       │
│ │ └──────┴───────┘ │ └────────┘│       │
│ └──────────────────┴────────────┘       │
│                                          │
│ 셀 크기 = 시총 비중                       │
│ 셀 색상 = 등락률 (진빨강~연빨강~회색~연파랑~진파랑) │
│ 탭 → 종목 상세                            │
└─────────────────────────────────────────┘
```

#### 히트맵 구현 기술 선택

Jetpack Compose에는 네이티브 Treemap 위젯이 없으므로 구현 방식을 선택해야 함.

| 방식 | 장점 | 단점 | 구현 비용 |
|------|------|------|----------|
| **A) Compose Canvas 커스텀** | 네이티브 60fps, 앱 용량 추가 0, 터치 이벤트 완전 제어 | Squarified Treemap 알고리즘 직접 구현, 텍스트 피팅·히트 테스트 등 세부 작업 많음 | 3~5일 |
| **B) WebView + D3.js** | `d3.treemap()` 레이아웃 자동, 반응형·애니메이션 쉬움, 구현 빠름 | WebView↔네이티브 JSBridge 필요, 스크롤/터치 약간 느림, D3 ~500KB 포함 | 1~2일 |
| **C) 서드파티 라이브러리** | 설치만 하면 됨 | Android 네이티브 Treemap 전용 라이브러리 거의 없음, 있어도 유지보수 중단 | 비추 |

**결정: Phase 2에서 B(WebView + D3.js)로 빠르게 구현 → 성능 불만 시 Phase 3에서 A(Canvas)로 전환**

Phase 2 구현 방식 (WebView + D3.js):
1. `assets/heatmap.html`에 D3 Treemap 렌더링 코드 포함
2. Android에서 `WebView.evaluateJavascript()`로 JSON 데이터 전달
3. 종목 탭 시 `@JavascriptInterface`로 네이티브 상세화면 호출
4. 다크/라이트 테마는 CSS 변수로 동적 전환

```kotlin
// ui/heatmap/HeatMapScreen.kt (개략)

@Composable
fun HeatMapScreen(viewModel: HeatMapViewModel = hiltViewModel()) {
    val equities by viewModel.sectorData.collectAsState()
    
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                addJavascriptInterface(
                    HeatMapBridge { symbol -> viewModel.onSymbolClick(symbol) },
                    "Android"
                )
                loadUrl("file:///android_asset/heatmap.html")
            }
        },
        update = { webView ->
            // JSON 데이터를 D3에 전달
            val json = Gson().toJson(equities)
            webView.evaluateJavascript("updateTreemap($json)", null)
        }
    )
}

class HeatMapBridge(private val onClick: (String) -> Unit) {
    @JavascriptInterface
    fun onSymbolTap(symbol: String) = onClick(symbol)
}
```

#### 다크/라이트 전환 UX

```
[설정 ⚙ 탭]
├── 테마: 시스템 설정 따르기 / 다크모드 / 라이트모드 (3택 라디오)
└── DataStore (Preferences)에 저장 → 앱 재시작 없이 즉시 반영
```

```kotlin
// data/local/ThemePreferences.kt

enum class ThemeMode { SYSTEM, DARK, LIGHT }

class ThemePreferences(private val context: Context) {
    private val dataStore = context.dataStore

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        ThemeMode.valueOf(prefs[THEME_KEY] ?: ThemeMode.SYSTEM.name)
    }

    suspend fun setTheme(mode: ThemeMode) {
        dataStore.edit { it[THEME_KEY] = mode.name }
    }

    companion object {
        val THEME_KEY = stringPreferencesKey("theme_mode")
    }
}
```

### 6.10 Python 배치 의존성 (requirements.txt)

```
# requirements.txt — GitHub Actions 배치 파이프라인용

# 데이터 수집
yfinance>=0.2.36              # Yahoo Finance 비공식 API
requests>=2.31.0              # HTTP 클라이언트 (FMP, EDGAR)
sec-edgar-api>=0.3.0          # SEC EDGAR Python 래퍼

# 데이터 처리
pandas>=2.2.0                 # 데이터프레임 (yfinance 반환)
lxml>=5.1.0                   # Form 4 XML 파싱

# DB 연동
supabase>=2.3.0               # Supabase Python SDK (UPSERT)

# 유틸리티
python-dotenv>=1.0.0          # 로컬 개발 시 .env 로드
tqdm>=4.66.0                  # 진행률 표시 (배치 로그)
tenacity>=8.2.0               # 자동 재시도 (rate limit 대응)
exchange-calendars>=4.5.0     # 미국 장 영업일 자동 판별 (선택적)
```

### 6.11 앱 배포 전략

**Phase 1: 개인 테스트 (APK 직접 설치)**

개발 중에는 Android Studio에서 디바이스에 직접 설치. `debug` 빌드 사용.

**Phase 2: Google Play Store 배포 (추후)**

개인 테스트 안정화 후 Google Play Store에 출시 예정.

| 항목 | 값 |
|------|-----|
| 패키지명 | `com.musiqq.stockscreener` (또는 적절한 도메인 역순) |
| 최소 SDK | API 26 (Android 8.0) |
| 타겟 SDK | API 35 (최신) |
| 서명 | Google Play App Signing 사용 |
| 배포 트랙 | 내부 테스트 → 비공개 → 공개 순차 |

**Supabase Anon Key 보안 — BuildConfig 분리**:

Supabase `anon` 키를 소스코드에 하드코딩하지 않고, `local.properties`에서 주입.

```properties
# local.properties (Git에 포함하지 않음, .gitignore에 등록)
SUPABASE_URL=https://xxxxx.supabase.co
SUPABASE_ANON_KEY=eyJhbGciOiJIUzI1NiIs...
```

```kotlin
// build.gradle.kts (app)

android {
    defaultConfig {
        // local.properties에서 읽어 BuildConfig 상수로 주입
        val props = java.util.Properties().apply {
            rootProject.file("local.properties").inputStream().use { load(it) }
        }
        buildConfigField("String", "SUPABASE_URL", "\"${props["SUPABASE_URL"]}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${props["SUPABASE_ANON_KEY"]}\"")
    }
    
    buildFeatures {
        buildConfig = true
    }
}
```

```kotlin
// data/remote/SupabaseClient.kt — 사용 예시

object SupabaseConfig {
    val URL: String = BuildConfig.SUPABASE_URL
    val ANON_KEY: String = BuildConfig.SUPABASE_ANON_KEY
}
```

> **보안 참고**: `anon` 키는 RLS에 의해 SELECT만 허용되므로 APK 역공학으로 추출되어도
> 데이터 변조는 불가능. 단, Play Store 배포 시 R8/ProGuard 난독화를 적용하여
> BuildConfig 문자열이 평문으로 노출되지 않도록 처리.

**.gitignore 필수 항목**:
```
local.properties
*.jks
*.keystore
google-services.json
```

### 6.12 종합 점수 가중치 커스터마이징

기본 종합 점수(score_total)는 밸류/퀄리티/모멘텀/성장 각 25% 균등 가중이지만,
사용자가 **자신의 투자 성향에 맞게 가중치를 조절**할 수 있도록 앱 내 설정 제공.

> **예시**: "나는 밸류 40%, 성장 30%, 퀄리티 20%, 모멘텀 10%로 보고 싶다"

**UI: 설정 탭 내 "종합 점수 가중치" 섹션**

```
[설정 ⚙ 탭]
├── 테마: 시스템 설정 따르기 / 다크모드 / 라이트모드
├── 종합 점수 가중치 (합계 100%)
│   ├── 밸류    [━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━] 25%  ← 슬라이더
│   ├── 퀄리티  [━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━] 25%
│   ├── 모멘텀  [━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━] 25%
│   └── 성장    [━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━] 25%
│   └── [기본값 복원] 버튼
└── 기타 설정...
```

**동작 원칙**:
- 4개 슬라이더의 합은 항상 **100%** 유지 (하나를 올리면 나머지가 자동 조정)
- 변경된 가중치는 **DataStore(Preferences)**에 로컬 저장
- 종합 점수는 **앱 클라이언트에서 실시간 재산출** (DB의 4개 하위 점수는 그대로, 가중합만 재계산)
- DB의 `score_total` 컬럼은 기본 25% 균등 기준 (배치 산출) → 서버 부담 없음

```kotlin
// data/local/ScoreWeightPreferences.kt

data class ScoreWeights(
    val value: Float = 0.25f,
    val quality: Float = 0.25f,
    val momentum: Float = 0.25f,
    val growth: Float = 0.25f,
) {
    init {
        require(kotlin.math.abs(value + quality + momentum + growth - 1.0f) < 0.01f) {
            "가중치 합은 1.0이어야 합니다"
        }
    }
    
    companion object {
        val DEFAULT = ScoreWeights()
    }
}

class ScoreWeightPreferences(private val context: Context) {
    private val dataStore = context.dataStore
    
    val weights: Flow<ScoreWeights> = dataStore.data.map { prefs ->
        ScoreWeights(
            value = prefs[KEY_VALUE] ?: 0.25f,
            quality = prefs[KEY_QUALITY] ?: 0.25f,
            momentum = prefs[KEY_MOMENTUM] ?: 0.25f,
            growth = prefs[KEY_GROWTH] ?: 0.25f,
        )
    }
    
    suspend fun setWeights(weights: ScoreWeights) {
        dataStore.edit {
            it[KEY_VALUE] = weights.value
            it[KEY_QUALITY] = weights.quality
            it[KEY_MOMENTUM] = weights.momentum
            it[KEY_GROWTH] = weights.growth
        }
    }
    
    suspend fun resetToDefault() = setWeights(ScoreWeights.DEFAULT)
    
    companion object {
        val KEY_VALUE = floatPreferencesKey("score_weight_value")
        val KEY_QUALITY = floatPreferencesKey("score_weight_quality")
        val KEY_MOMENTUM = floatPreferencesKey("score_weight_momentum")
        val KEY_GROWTH = floatPreferencesKey("score_weight_growth")
    }
}
```

**스크리너 리스트에서의 적용**:

```kotlin
// ui/screener/ScreenerViewModel.kt (요약)

fun computeCustomTotalScore(equity: Equity, weights: ScoreWeights): Int {
    val total = (equity.scoreValue ?: 50) * weights.value +
                (equity.scoreQuality ?: 50) * weights.quality +
                (equity.scoreMomentum ?: 50) * weights.momentum +
                (equity.scoreGrowth ?: 50) * weights.growth
    return total.roundToInt().coerceIn(0, 100)
}
```

> **참고**: 프리셋 시그널 "⭐ 종합점수 80+"는 DB의 기본 균등 가중 `score_total`을 사용.
> 사용자 커스텀 가중치는 리스트 정렬 및 상세화면 표시에만 적용됨.
> 향후 "내 점수 기준 80+" 프리셋을 추가할 수도 있음 (클라이언트 필터).

---

## 7. 개발 단계 로드맵

### Phase 1: 백엔드 파이프라인 (1~2주)

| 순서 | 태스크 | 산출물 |
|------|--------|-------|
| 1-1 | Supabase 프로젝트 생성 (서울 리전) ✅ | 프로젝트 URL + Key |
| 1-2 | DDL 실행 (테이블 + 인덱스 + RLS) | DB 스키마 |
| 1-3 | Python 배치: Universe Sync 구현 | `fmp_client.py` |
| 1-4 | Python 배치: yfinance Bulk Quote 구현 | `yfinance_bulk.py` |
| 1-5 | Python 배치: FMP Batch Quote 보강 구현 | `fmp_client.py` |
| 1-6 | Python 배치: UPSERT 로더 구현 | `supabase_upsert.py` |
| 1-7 | Python 배치: ETF 롤링 스케줄러 구현 | `etf_roller.py` |
| 1-8 | Python 배치: EDGAR Form 4 수집 구현 | `edgar_insider.py` |
| 1-9 | Python 배치: EDGAR 13F 수집 구현 | `edgar_13f.py` |
| 1-10 | Python 배치: 내부자 거래 집계 산출 | `insider_agg.py` |
| 1-11 | GitHub Actions 일일 워크플로 설정 | `daily-sync.yml` |
| 1-12 | Edge Function: Holdings 역산출 | `etf-by-holding/` |
| 1-13 | Edge Function: FMP 온디맨드 프록시 (캐시 경유) | `fmp-proxy/` |
| 1-14 | `fmp_cache` 캐시 테이블 DDL + TTL 정리 로직 | DDL + `supabase_upsert.py` |
| 1-15 | 우선 티커 리스트 빌더 구현 | `priority_builder.py` |
| 1-16 | Sanity Check (데이터 무결성 자동 검증) 구현 | `sanity_check.py` |
| 1-17 | 배치 Summary 로깅 + GitHub Actions Summary 출력 | `batch_summary.py` |
| 1-18 | 배치 실패 이메일 알림 설정 (Gmail → `musiqq86@gmail.com`) | `daily-sync.yml` |
| 1-19 | 검증: PostgREST 쿼리 테스트 | curl / Postman |

### Phase 2: Android 프론트엔드 (2~3주)

| 순서 | 태스크 | 산출물 |
|------|--------|-------|
| 2-1 | Android 프로젝트 세팅 (Kotlin + Compose + Hilt) | 프로젝트 |
| 2-2 | MVVM + Repository 아키텍처 뼈대 | `data/`, `domain/`, `ui/` |
| 2-3 | Supabase Retrofit 연동 + DTO | API 레이어 |
| 2-4 | Room DB 세팅 (관심종목) | `AppDatabase.kt` |
| 2-5 | 하단 네비게이션 (스크리너/히트맵/관심/설정) | Navigation |
| 2-6 | 스크리너 메인 화면 (리스트 + 페이지네이션) | UI |
| 2-7 | 검색 자동완성 (pg_trgm) | UI |
| 2-8 | 필터 패널 (Stock 모드 — 8개 카테고리) | UI |
| 2-9 | 필터 패널 (ETF 모드 + Holdings 역산출) | UI |
| 2-10 | 프리셋 시그널 칩 바 (31종) | UI |
| 2-11 | 동적 정렬 | UI |
| 2-12 | 종목 상세 화면 — 개요 탭 | UI |
| 2-13 | 종목 상세 화면 — 재무 탭 | UI |
| 2-14 | 종목 상세 화면 — 수급 탭 (내부자 거래 리스트 포함) | UI |
| 2-15 | 종목 상세 화면 — 애널리스트 탭 (FMP 온디맨드 연동) | UI |
| 2-16 | 관심종목 화면 (Room DB + Supabase 조합) | UI + 기능 |
| 2-17 | 히트맵 화면 (WebView + D3.js Treemap) | UI + `heatmap.html` |
| 2-18 | 한글 헤더 매핑 + 숫자 포맷터 적용 | `ColumnLabels.kt` |
| 2-19 | 종합 점수 가중치 커스터마이징 (설정 탭) | `ScoreWeightPreferences.kt` |
| 2-20 | Supabase Key BuildConfig 분리 (`local.properties`) | 빌드 설정 |
| 2-21 | 다크/라이트 테마 설정 | `ThemePreferences.kt` |

### Phase 3: 안정화 & 배포 (1주)

| 순서 | 태스크 | 산출물 |
|------|--------|-------|
| 3-1 | 개인 디바이스 테스트 (기능·성능·UI) | 버그 리스트 |
| 3-2 | R8/ProGuard 난독화 설정 | `proguard-rules.pro` |
| 3-3 | Release 빌드 서명 설정 (keystore) | 서명된 APK/AAB |
| 3-4 | Google Play Console 등록 + 내부 테스트 트랙 | Play Store |
| 3-5 | 히트맵 성능 검토 → 필요 시 Canvas 네이티브 전환 계획 수립 | 판단 |

### ~~Phase 4: 한국어 번역 파이프라인~~ → Phase 2에 통합 완료

> 데이터 값(value)은 영문 원문 유지 확정.
> UI 컬럼 헤더·필터 라벨·정렬 옵션만 한글 → `ColumnLabels.kt` 상수 객체로 Phase 2에서 처리.
> 별도 번역 파이프라인 불필요.

---

## 8. 리스크 및 대응

| 리스크 | 영향도 | 대응 |
|--------|-------|------|
| yfinance IP 차단 / 구조 변경 | 높음 | FMP Batch Quote로 가격/시총 최소 유지 (30 req). 심화 데이터(Ticker.info)는 FMP 유료 플랜 전환 필요 — 구체적 대안 소스(FMP Starter $19/mo, Polygon.io, Alpha Vantage 등)별 커버리지 매핑은 장애 발생 시 별도 조사 후 결정. 현 시점에서는 yfinance 안정성 모니터링에 집중 |
| FMP Free 정책 변경 | 중간 | Starter $19/mo 업그레이드 또는 Alpha Vantage Free 대체 |
| Supabase Free 500MB 초과 | 낮음 | Holdings 상위 30개로 축소 또는 Pro $25/mo 업그레이드 |
| Supabase 7일 비활성 중단 | 없음 | 매일 배치가 DB를 갱신하므로 자동 회피 |
| GitHub Actions 무료 한도 (2000분/월) | 낮음 | 배치 1회 ~45분 × ~20영업일(미국 공휴일 스킵) = ~900분 (45%) |
| ETF Holdings 데이터 지연 (14일 1회전) | 중간 | Tier 1 (상위 300) 매일 갱신으로 핵심 ETF는 항상 최신 |
| SEC EDGAR rate limit (10 req/초) | 낮음 | 보수적 7 req/초로 운용, 1,000종목 2.5분 소요 |
| SEC EDGAR Form 4 XML 구조 변경 | 낮음 | SEC 공식 포맷, 수십 년간 안정적. 변경 시 파서만 수정 |
| X Sentiment API 서비스 중단 | 낮음 (Nice-to-Have) | 소셜 컬럼 NULL 유지, 핵심 기능 무영향. 배치 헬스체크로 자동 감지 → 연속 3회 실패 시 이메일 알림. API 영구 중단 확인 시 ⑦-B 단계 제거만으로 정리 완료 |
| 배치 파이프라인 자체 실패 | 중간 | GitHub Actions 실패 시 이메일 알림(`musiqq86@gmail.com`). Sanity check로 데이터 무결성 자동 검증 (§4.6 참조) |

---

## 9. 참고: 데이터 볼륨 추정

| 항목 | 수치 |
|------|------|
| NYSE + NASDAQ + AMEX 상장 종목 | ~8,000 보통주 |
| 미국 상장 ETF | ~2,800 |
| 전체 Universe | ~15,000 (OTC/워런트 제외 후) |
| 일일 UPSERT 레코드 | ~15,000 (latest_equities) |
| 일일 yfinance 다운로드 | ~15,000 티커 × 80청크 = ~188 HTTP |
| 일일 FMP API 소비 | ~231 req (한도 250 이내) |
| 일일 SEC EDGAR 소비 | ~1,000 req (10 req/초 한도, 별도) |
| 일일 데이터 전송량 | ~5 MB (yfinance) + ~2 MB (FMP) + ~3 MB (EDGAR) |
| DB latest_equities | ~30 MB (본문, ~140 컬럼) + ~14 MB (Holdings JSONB) |
| DB insider_trades | ~5 MB (최근 90일 약 50,000건 보관) |
| 인덱스 총 크기 | ~70 MB |
| WAL·시스템 오버헤드 | ~50 MB |
| **총 DB 사용량** | **~219 MB / 500 MB (44%)** |

---

## 10. 지표 용어사전 (Indicator Glossary)

> 모든 지표의 **영문 원본명**, **산출 공식**, **해석 가이드**를 정리.
> 앱의 Long Press 툴팁(TooltipHeader)에서도 이 정보를 표시.

### 10.1 밸류에이션 (Valuation)

| 한글명 | 영문명 | 공식 | 해석 |
|--------|--------|------|------|
| **PER** | Price/Earnings Ratio (TTM) | 주가 ÷ EPS(TTM) | 낮을수록 저평가. 섹터별 비교 필요. 음수=적자 |
| **예상PER** | Forward P/E | 주가 ÷ 예상 EPS(차기 FY) | 현재 PER보다 낮으면 실적 개선 기대 |
| **PBR** | Price/Book Ratio | 주가 ÷ 주당장부가 | <1이면 자산 대비 저평가, 은행주 핵심 지표 |
| **PEG** | PEG Ratio | Forward P/E ÷ EPS 연간 성장률 | <1이면 성장 대비 저평가 |
| **PSR** | Price/Sales Ratio (TTM) | 시가총액 ÷ 매출액(TTM) | 적자 기업 밸류에이션에 유용 |
| **P/FCF** | Price/Free Cash Flow | 시가총액 ÷ 잉여현금흐름 | 낮을수록 현금 창출력 대비 저평가 |
| **P/Cash** | Price/Cash per Share | 주가 ÷ 주당 현금 | 낮을수록 현금 보유 대비 저평가 |
| **EV** | Enterprise Value | 시가총액 + 총부채 - 현금 | 기업 인수 시 실제 비용. 시총보다 정확한 규모 |
| **EV/EBITDA** | EV/EBITDA | EV ÷ EBITDA | PER 대체 핵심 지표. 부채·세금·감가 영향 제거. <10 저평가 |
| **EV/매출** | EV/Revenue | EV ÷ 매출액(TTM) | 적자 기업, SaaS 기업 밸류에이션에 사용 |
| **FCF수익률** | FCF Yield | 잉여현금흐름 ÷ 시가총액 × 100 | 높을수록 현금 기반 수익률 양호. >5% 매력적 |
| **DCF내재가치** | DCF Intrinsic Value | Σ(미래 FCF ÷ (1+할인율)^n) + 잔존가치 | 현금흐름 할인 모델 기반 적정 주가 |
| **DCF괴리율** | DCF Upside % | (DCF내재가치 - 현재가) ÷ 현재가 × 100 | 양수=저평가, 음수=고평가 |
| **Graham적정가** | Graham Number | √(22.5 × EPS(TTM) × 장부가/주) | 벤저민 그레이엄의 보수적 적정가. EPS·장부가 모두 양수일 때만 유효 |
| **Graham괴리율** | Graham Upside % | (Graham적정가 - 현재가) ÷ 현재가 × 100 | 양수=저평가 |

### 10.2 수익성 (Profitability)

| 한글명 | 영문명 | 공식 | 해석 |
|--------|--------|------|------|
| **ROE** | Return on Equity | 순이익 ÷ 자기자본 × 100 | 주주 자본 대비 수익률. >15% 양호 |
| **ROA** | Return on Assets | 순이익 ÷ 총자산 × 100 | 자산 활용 효율. >5% 양호 |
| **ROIC** | Return on Invested Capital | NOPAT ÷ (총자산 - 비영업자산 - 유동부채) × 100 | 투하 자본 대비 수익률. WACC 초과 시 가치 창출 |
| **영업이익률** | Operating Margin | 영업이익 ÷ 매출 × 100 | 본업 수익력. 높을수록 좋음 |
| **순이익률** | Net Profit Margin | 순이익 ÷ 매출 × 100 | 최종 수익력 |
| **매출총이익률** | Gross Margin | (매출 - 매출원가) ÷ 매출 × 100 | 원가 경쟁력 |

### 10.3 성장성 (Growth)

| 한글명 | 영문명 | 공식 | 해석 |
|--------|--------|------|------|
| **매출성장률** | Revenue Growth (YoY) | (금분기 매출 - 전년동기) ÷ 전년동기 × 100 | 양수=매출 증가 |
| **EPS성장률** | Earnings Growth (YoY) | (금분기 EPS - 전년동기) ÷ abs(전년동기) × 100 | 양수=이익 증가 |
| **영업이익증가율** | Operating Income Growth (YoY) | (금분기 영업이익 - 전년동기) ÷ abs(전년동기) × 100 | 본업 수익 성장 |
| **EPS성장률(금년)** | EPS Growth This Year (Est.) | (금년 예상 EPS - 전년 EPS) ÷ abs(전년 EPS) × 100 | 애널리스트 추정 기반 |
| **EPS성장률(5Y)** | EPS CAGR (5 Year) | (EPS₅ ÷ EPS₀)^(1/5) - 1 | 5개년 복합 연평균 성장률 |
| **매출성장률(5Y)** | Revenue CAGR (5 Year) | (Rev₅ ÷ Rev₀)^(1/5) - 1 | 장기 매출 성장 추세 |

### 10.4 재무 건전성 (Financial Health)

| 한글명 | 영문명 | 공식 | 해석 |
|--------|--------|------|------|
| **부채비율** | Debt/Equity Ratio | 총부채 ÷ 자기자본 | <100 양호. 업종별 차이 큼 |
| **장기부채비율** | LT Debt/Equity | 장기부채 ÷ 자기자본 | 장기적 재무 부담 |
| **유동비율** | Current Ratio | 유동자산 ÷ 유동부채 | >1 안전, <1 유동성 위험 |
| **당좌비율** | Quick Ratio | (유동자산 - 재고) ÷ 유동부채 | 유동비율보다 보수적 |
| **차입금증가율** | Debt Growth (YoY) | (금분기 총차입금 - 전년동기) ÷ abs(전년동기) × 100 | 양수=차입 증가 (주의) |
| **이자보상배율** | Interest Coverage Ratio | 영업이익(TTM) ÷ 이자비용(TTM) | >5 안전, <1.5 위험 |
| **F-Score** | Piotroski F-Score (0~9) | 수익성(4) + 자본구조(3) + 효율성(2) 합산 | 9개 재무 기준 합격 수. ≥7 양호, ≤3 위험 |
| **Z-Score** | Altman Z-Score | 1.2A + 1.4B + 3.3C + 0.6D + 1.0E (5개 재무비율) | >2.99 안전, 1.81~2.99 회색, <1.81 부도위험 |

### 10.5 배당 (Dividend)

| 한글명 | 영문명 | 공식 | 해석 |
|--------|--------|------|------|
| **배당률** | Dividend Yield | 연간 배당금 ÷ 주가 × 100 | 주가 대비 현금 배당 수익률 |
| **배당금(연)** | Annual Dividend Rate | 연간 주당 배당금 (USD) | 절대 배당 금액 |
| **배당성향** | Payout Ratio | 배당금 총액 ÷ 순이익 × 100 | 이익 중 배당 비율. >100%=이익 초과 배당 (지속 불가) |
| **배당락일** | Ex-Dividend Date | - | 이 날 이전 매수해야 배당 수령 |
| **5년평균배당률** | 5-Year Avg Dividend Yield | 최근 5년 배당률 평균 | 배당 안정성 판단 |

### 10.6 수급·소유 (Ownership & Flow)

| 한글명 | 영문명 | 공식 | 해석 |
|--------|--------|------|------|
| **공매도수량** | Short Interest | 미결제 공매도 주식 수 | FINRA 보고, ~2주 지연 |
| **공매도비율** | Short Ratio (Days to Cover) | 공매도수량 ÷ 일평균거래량 | 높을수록 숏커버 시간 길어 숏스퀴즈 가능 |
| **공매도/유통** | Short % of Float | 공매도수량 ÷ 유통주식수 × 100 | >10% 높은 편, >20% 매우 높음 |
| **내부자보유비율** | Insider Ownership % | 내부자 보유 주식수 ÷ 발행주식수 × 100 | 경영진 이해관계 일치도 |
| **기관보유비율** | Institutional Ownership % | 기관 보유 주식수 ÷ 발행주식수 × 100 | 높을수록 기관 관심 |
| **13F기관수** | 13F Institutional Holders | SEC 13F 보고 기관 수 | EDGAR 원천 |
| **기관거래변동** | Institutional Transactions % | (금분기 기관보유 - 전분기) ÷ 전분기 × 100 | 양수=기관 매수 증가 |
| **내부자매수(3M)** | Insider Buys (90 Days) | 최근 90일 내부자 순매수 건수 | EDGAR Form 4 원천 |
| **내부자매도(3M)** | Insider Sales (90 Days) | 최근 90일 내부자 순매도 건수 | |
| **내부자순매수량** | Insider Net Shares (90 Days) | 매수 주식수 - 매도 주식수 | 양수=순매수 |

### 10.7 기술적 지표 (Technical Indicators)

| 한글명 | 영문명 | 공식 | 해석 |
|--------|--------|------|------|
| **RSI(14)** | Relative Strength Index (14) | 100 - 100/(1 + RS), RS=14일 평균 상승폭/하락폭 | >70 과매수, <30 과매도, 50 중립 |
| **ATR(14)** | Average True Range (14) | EMA14(TrueRange), TR=max(H-L, H-PC, PC-L) | 변동성 절대값 (USD). 높을수록 변동 큼 |
| **MACD** | Moving Average Convergence Divergence | EMA(12) - EMA(26) | 추세 방향. 양수=상승추세, 음수=하락추세 |
| **MACD시그널** | MACD Signal Line | EMA(9) of MACD | MACD의 이동평균. MACD가 시그널 상향 돌파=골든크로스 |
| **MACD히스토그램** | MACD Histogram | MACD - Signal | 양수 증가=상승 가속, 음수 증가=하락 가속 |
| **Stoch %K** | Stochastic %K (14) | (종가 - 14일최저) ÷ (14일최고 - 14일최저) × 100 | >80 과매수, <20 과매도 |
| **Stoch %D** | Stochastic %D (3) | SMA(3) of %K | %K의 이동평균. %K가 %D 상향 돌파=매수 시그널 |
| **ADX(14)** | Average Directional Index (14) | DX의 14일 평균 | 추세 강도. >25 강한 추세, <20 횡보 |
| **CCI(20)** | Commodity Channel Index (20) | (TP - SMA20(TP)) ÷ (0.015 × MD), TP=(H+L+C)/3 | >100 과매수, <-100 과매도 |
| **Williams %R** | Williams %R (14) | (14일최고 - 종가) ÷ (14일최고 - 14일최저) × (-100) | -20~0 과매수, -100~-80 과매도 |
| **20일이평** | 20-Day Simple Moving Average | 최근 20일 종가 평균 | 단기 추세선 |
| **50일이평** | 50-Day Simple Moving Average | 최근 50일 종가 평균 | 중기 추세선 |
| **200일이평** | 200-Day Simple Moving Average | 최근 200일 종가 평균 | 장기 추세선. 주가 위=강세, 아래=약세 |
| **20일이평괴리** | SMA20 Distance % | (종가 - SMA20) ÷ SMA20 × 100 | 양수=이평선 위, 음수=아래 |
| **50일이평괴리** | SMA50 Distance % | (종가 - SMA50) ÷ SMA50 × 100 | |
| **200일이평괴리** | SMA200 Distance % | (종가 - SMA200) ÷ SMA200 × 100 | |
| **베타** | Beta (5 Year) | 60개월 수익률의 시장 대비 회귀계수 | >1 시장보다 변동 큼, <1 변동 작음, <0 역상관 |
| **변동성(주)** | Weekly Volatility | 주간 일일 고가-저가 범위의 평균 (%) | 높을수록 단기 변동 큼 |
| **변동성(월)** | Monthly Volatility | 월간 일일 고가-저가 범위의 평균 (%) | |
| **갭** | Gap % | (시가 - 전일종가) ÷ 전일종가 × 100 | 양수=갭업, 음수=갭다운 |
| **시가대비** | Change from Open % | (종가 - 시가) ÷ 시가 × 100 | 장중 방향성 |
| **상대거래량** | Relative Volume | 당일거래량 ÷ 10일평균거래량 | >2.0 이상 시 비정상적 거래량 (이벤트 가능성) |

### 10.8 성과 (Performance)

| 한글명 | 영문명 | 공식 | 해석 |
|--------|--------|------|------|
| **1주수익률** | 1-Week Performance | (현재가 - 5영업일전) ÷ 5영업일전 × 100 | 초단기 모멘텀 |
| **1월수익률** | 1-Month Performance | 21영업일 기준 | 단기 모멘텀 |
| **3월수익률** | 3-Month Performance | 63영업일 기준 | |
| **6월수익률** | 6-Month Performance | 126영업일 기준 | 중기 모멘텀 |
| **1년수익률** | 1-Year Performance | 252영업일 기준 | 장기 모멘텀 |
| **YTD수익률** | Year-to-Date Performance | 연초 대비 수익률 | 올해 성과 |

### 10.9 수익 품질 (Earnings Quality)

| 한글명 | 영문명 | 공식 | 해석 |
|--------|--------|------|------|
| **Accruals비율** | Accruals Ratio | (순이익 - 영업현금흐름) ÷ 총자산 | 0에 가까울수록 수익 품질 양호. 높으면 회계적 이익에 의존 |
| **FCF/순이익** | FCF/Net Income Ratio | 잉여현금흐름 ÷ 순이익 | >1이면 현금 기반 수익, <1이면 회계적 수익 비중 높음 |
| **수익품질점수** | Earnings Quality Score (0~100) | Accruals비율 + FCF/NI의 백분위 종합 | 높을수록 수익 품질 양호 |
| **실적서프라이즈** | Earnings Surprise % | (실제 EPS - 추정 EPS) ÷ abs(추정 EPS) × 100 | 양수=어닝 서프라이즈, 음수=미달 |

### 10.10 자본 효율성 (Capital Efficiency)

| 한글명 | 영문명 | 공식 | 해석 |
|--------|--------|------|------|
| **자사주매입률** | Buyback Yield | 자사주매입액 ÷ 시가총액 × 100 | 높을수록 주주환원 적극적 |
| **주주환원율** | Shareholder Yield | 배당수익률 + 자사주매입수익률 | 배당+자사주 합산 총환원율. >5% 우수 |
| **CAPEX/매출** | CAPEX/Revenue Ratio | 설비투자액 ÷ 매출 × 100 | 높으면 자본 집약적 (반도체 등), 낮으면 경량 모델 (SW 등) |

### 10.11 자체 종합 점수 (Composite Scores)

> 기존 DB 데이터를 전종목 **백분위(percentile)**로 변환하여 합산.
> 추가 API 호출 0, 배치에서 SQL 또는 Python으로 산출.

| 한글명 | 영문명 | 구성 요소 (가중치) | 해석 |
|--------|--------|-------------------|------|
| **밸류점수** | Value Score (0~100) | PER역수(25%) + PBR역수(20%) + EV/EBITDA역수(25%) + PSR역수(15%) + FCF수익률(15%) | 높을수록 저평가. 각 지표의 전종목 내 순위를 0~100 정규화 |
| **퀄리티점수** | Quality Score (0~100) | ROE(20%) + ROIC(20%) + 영업이익률(15%) + 이자보상배율(15%) + F-Score(15%) + Accruals역수(15%) | 높을수록 재무 건전 + 수익 양질 |
| **모멘텀점수** | Momentum Score (0~100) | 1M수익률(20%) + 3M수익률(20%) + 6M수익률(20%) + RSI보정(15%) + 상대거래량(10%) + MACD히스토그램(15%) | 높을수록 강한 상승 추세 |
| **성장점수** | Growth Score (0~100) | 매출성장률(25%) + EPS성장률(25%) + 영업이익증가율(25%) + 매출5Y(15%) + EPS5Y(10%) | 높을수록 고성장 |
| **종합점수** | Total Score (0~100) | 밸류(25%) + 퀄리티(25%) + 모멘텀(25%) + 성장(25%) | 4개 스코어의 균등 가중 평균. 전체 투자 매력도 |

```python
# transforms/scoring.py — 종합 점수 산출 로직

import pandas as pd
import numpy as np

def compute_percentile_scores(df: pd.DataFrame) -> pd.DataFrame:
    """
    전종목 DataFrame에서 백분위 기반 종합 점수 산출.
    NaN은 50으로 대체 (중립).
    """
    
    def pct_rank(series: pd.Series, ascending=True) -> pd.Series:
        """0~100 백분위 변환. ascending=True면 높을수록 좋은 지표."""
        ranked = series.rank(pct=True, ascending=ascending) * 100
        return ranked.fillna(50)
    
    # ── 밸류 스코어: 낮을수록 저평가인 지표는 ascending=False ──
    df['_v_pe'] = pct_rank(df['pe_ttm'], ascending=False)
    df['_v_pb'] = pct_rank(df['pb_ratio'], ascending=False)
    df['_v_ev_ebitda'] = pct_rank(df['ev_ebitda'], ascending=False)
    df['_v_ps'] = pct_rank(df['ps_ratio'], ascending=False)
    df['_v_fcf_yield'] = pct_rank(df['fcf_yield'], ascending=True)
    
    df['score_value'] = (
        df['_v_pe'] * 0.25 +
        df['_v_pb'] * 0.20 +
        df['_v_ev_ebitda'] * 0.25 +
        df['_v_ps'] * 0.15 +
        df['_v_fcf_yield'] * 0.15
    ).round().astype('Int16')
    
    # ── 퀄리티 스코어: 높을수록 좋은 지표 ──
    df['score_quality'] = (
        pct_rank(df['roe']) * 0.20 +
        pct_rank(df['roic']) * 0.20 +
        pct_rank(df['operating_margin']) * 0.15 +
        pct_rank(df['interest_coverage']) * 0.15 +
        pct_rank(df['piotroski_score']) * 0.15 +
        pct_rank(df['accruals_ratio'], ascending=False) * 0.15
    ).round().astype('Int16')
    
    # ── 모멘텀 스코어 ──
    df['score_momentum'] = (
        pct_rank(df['perf_1m']) * 0.20 +
        pct_rank(df['perf_3m']) * 0.20 +
        pct_rank(df['perf_6m']) * 0.20 +
        pct_rank(df['rsi_14'].clip(30, 70)) * 0.15 +  # RSI 30~70 범위 정규화
        pct_rank(df['relative_volume']) * 0.10 +
        pct_rank(df['macd_hist']) * 0.15
    ).round().astype('Int16')
    
    # ── 성장 스코어 ──
    df['score_growth'] = (
        pct_rank(df['revenue_growth']) * 0.25 +
        pct_rank(df['earnings_growth']) * 0.25 +
        pct_rank(df['op_income_growth']) * 0.25 +
        pct_rank(df['sales_growth_past_5y']) * 0.15 +
        pct_rank(df['eps_growth_past_5y']) * 0.10
    ).round().astype('Int16')
    
    # ── 종합 스코어: 4개의 균등 가중 평균 ──
    df['score_total'] = (
        df['score_value'] * 0.25 +
        df['score_quality'] * 0.25 +
        df['score_momentum'] * 0.25 +
        df['score_growth'] * 0.25
    ).round().astype('Int16')
    
    # 임시 컬럼 삭제
    df.drop(columns=[c for c in df.columns if c.startswith('_v_')], inplace=True)
    
    return df
```

### 10.12 애널리스트 & 센티먼트 (Analyst & Sentiment)

| 한글명 | 영문명 | 공식/소스 | 해석 |
|--------|--------|----------|------|
| **투자의견** | Analyst Rating | Strong Buy / Buy / Hold / Sell / Strong Sell | 월가 컨센서스 |
| **의견점수** | Recommendation Score (1~5) | 1=Strong Buy, 5=Strong Sell | 낮을수록 긍정 |
| **애널리스트수** | Number of Analysts | 의견 제출 애널리스트 수 | 많을수록 신뢰도 높음 |
| **평균목표가** | Average Price Target | 애널리스트 목표가 평균 | |
| **최고목표가** | High Price Target | 가장 낙관적 목표가 | |
| **최저목표가** | Low Price Target | 가장 비관적 목표가 | |
| **목표괴리율** | Price Target Upside % | (평균목표가 - 현재가) ÷ 현재가 × 100 | 양수=상승 여력 |
| **소셜점수** | Social Sentiment Score (0~100) | X(Twitter) 센티먼트 종합 (Grok 분석) | 높을수록 긍정적 |
| **소셜긍정비** | Social Bullish % | X 긍정 게시물 비율 | |
| **24h언급수** | 24h Mention Count | X에서 24시간 내 해당 티커 언급 횟수 | 높을수록 화제 |
| **Reddit교차** | Reddit Cross-Validated | X와 Reddit 동시 트렌딩 여부 | true=신뢰도 높음 |

### 10.13 기타 (Other)

| 한글명 | 영문명 | 설명 |
|--------|--------|------|
| **소재국** | Country | 기업 소재 국가 코드 (US, CN, IE 등) |
| **실적발표일** | Earnings Date | 차기 분기 실적 발표 예정일 |
| **인덱스소속** | Index Membership | S&P500, DJIA, NASDAQ100 등 |
| **매출액(TTM)** | Total Revenue (TTM) | 후행 12개월 매출 총액 (USD) |
| **EBITDA** | EBITDA | 이자·세금·감가·상각 전 이익 (USD) |
| **잉여현금흐름** | Free Cash Flow | 영업현금흐름 - 설비투자 (USD) |
| **현금성자산** | Total Cash | 현금 및 현금성자산 총액 (USD) |
| **총부채** | Total Debt | 단기차입 + 장기차입 총액 (USD) |
| **장부가/주** | Book Value Per Share | (총자산 - 총부채) ÷ 발행주식수 |
| **매출/주** | Revenue Per Share | 매출액(TTM) ÷ 발행주식수 |
| **발행주식수** | Shares Outstanding | 총 발행 보통주 수 |
| **유통주식수** | Float Shares | 발행주식수 - 내부자 보유 - 5% 이상 보유 |
