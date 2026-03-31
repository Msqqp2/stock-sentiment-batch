package com.musiqq.stockscreener.ui.detail.tabs

data class LabelTooltip(
    val english: String,
    val formula: String,
)

val LABEL_TOOLTIPS = mapOf(
    // ── 개요 > 기본 정보 ──
    "거래소" to LabelTooltip("Exchange", "해당 종목이 상장된 증권거래소"),
    "섹터" to LabelTooltip("Sector", "GICS 기준 산업 섹터 분류"),
    "산업" to LabelTooltip("Industry", "섹터 내 세부 산업 분류"),
    "시가총액" to LabelTooltip("Market Capitalization", "주가 × 발행주식수"),
    "거래량" to LabelTooltip("Volume", "최근 거래일 동안 거래된 주식 수"),
    "베타" to LabelTooltip("Beta", "시장(S&P 500) 대비 변동성 (1.0 = 시장과 동일)\n5년 월간 수익률 기반"),
    "국가" to LabelTooltip("Country", "기업 본사 소재 국가"),
    "시가" to LabelTooltip("Open Price", "당일 시초가"),
    "고가" to LabelTooltip("Day High", "당일 최고가"),
    "저가" to LabelTooltip("Day Low", "당일 최저가"),
    "전일종가" to LabelTooltip("Previous Close", "전 거래일 종가"),
    "평균거래량" to LabelTooltip("Avg Volume (10D)", "최근 10거래일 평균 거래량"),

    // ── 개요 > 밸류에이션 ──
    "PER (TTM)" to LabelTooltip("Price-to-Earnings Ratio (TTM)", "주가 / 주당순이익 (최근 12개월)"),
    "예상PER" to LabelTooltip("Forward P/E", "주가 / 내년 예상 주당순이익"),
    "PBR" to LabelTooltip("Price-to-Book Ratio", "주가 / 주당순자산가치"),
    "EPS" to LabelTooltip("Earnings Per Share", "순이익 / 발행주식수 (최근 12개월)"),
    "배당률" to LabelTooltip("Dividend Yield", "연간 주당배당금 / 주가 × 100"),
    "주당배당금" to LabelTooltip("Dividend Rate", "연간 주당 배당금 (달러)"),
    "5년평균배당률" to LabelTooltip("5-Year Avg Dividend Yield", "최근 5년간 평균 배당수익률"),

    // ── 개요 > 기술적 지표 ──
    "RSI(14)" to LabelTooltip("Relative Strength Index (14)", "100 - 100 / (1 + 평균상승폭 / 평균하락폭)\n14일 기간, Wilder 지수이동평균 방식\n70 이상 과매수, 30 이하 과매도"),
    "50일선" to LabelTooltip("50-Day Moving Average", "최근 50거래일 종가 평균"),
    "200일선" to LabelTooltip("200-Day Moving Average", "최근 200거래일 종가 평균"),
    "52주 고가" to LabelTooltip("52-Week High", "최근 52주간 최고가"),
    "52주 저가" to LabelTooltip("52-Week Low", "최근 52주간 최저가"),
    "고가괴리" to LabelTooltip("% from 52-Week High", "(현재가 - 52주고가) / 52주고가 × 100"),
    "상대거래량" to LabelTooltip("Relative Volume", "당일 거래량 / 20일 평균 거래량"),
    "MACD히스토" to LabelTooltip("MACD Histogram", "MACD선 - 시그널선\n양수: 상승 모멘텀, 음수: 하락 모멘텀"),
    "주간변동성" to LabelTooltip("Weekly Volatility", "최근 1주간 수익률의 표준편차\n높을수록 가격 변동이 큼"),
    "월간변동성" to LabelTooltip("Monthly Volatility", "최근 1개월간 수익률의 표준편차\n높을수록 가격 변동이 큼"),

    // ── 개요 > 종합 스코어 ──
    "밸류" to LabelTooltip("Value Score (0-100)", """avg(-PE, -PB, -PS, -EV/EBITDA)의 백분위 순위
저평가일수록 높은 점수
필터: 0<PE<500, 0<PB<100, 0<PS<100, 0<EV/EBITDA<200
출처: yfinance → 일일 배치 (KST 07:00, 평일)"""),
    "퀄리티" to LabelTooltip("Quality Score (0-100)", """avg(정규화ROE, 정규화ROA, 정규화영업이익률, 정규화유동비율)의 백분위 순위
ROE: clamp((roe+0.5)/1.0, 0, 1)
ROA: clamp((roa+0.2)/0.4, 0, 1)
영업이익률: clamp((margin+0.3)/0.8, 0, 1)
유동비율: min(CR/3, 1)
출처: yfinance → 일일 배치 (KST 07:00, 평일)"""),
    "모멘텀" to LabelTooltip("Momentum Score (0-100)", """avg(등락률/10, 52주고점괴리/50+1, 상대거래량/3, RSI/100)의 백분위 순위
등락률: 일일 주가 변동 %
52주고점괴리: 52주 고가 대비 거리
상대거래량: 당일 거래량 / 20일 평균 거래량
RSI: Wilder의 상대강도지수
출처: yfinance → 일일 배치 (KST 07:00, 평일)"""),
    "성장" to LabelTooltip("Growth Score (0-100)", """avg(매출성장률, 이익성장률, 영업이익성장률)의 백분위 순위
모두 전년 동기 대비(YoY) 성장률
출처: yfinance → 일일 배치 (KST 07:00, 평일)"""),
    "종합" to LabelTooltip("Total Score (0-100)", """4개 하위 점수의 단순 평균 (각 25%)
= (밸류 + 퀄리티 + 모멘텀 + 성장) / 4
값이 없는 하위 점수는 평균에서 제외
출처: 일일 배치 (KST 07:00, 평일)"""),

    // ── 재무 > 수익성 ──
    "ROE" to LabelTooltip("Return on Equity", "순이익 / 자기자본\n자기자본 대비 수익 창출 능력"),
    "ROA" to LabelTooltip("Return on Assets", "순이익 / 총자산\n총자산 대비 수익 창출 능력"),
    "ROIC" to LabelTooltip("Return on Invested Capital", "NOPAT / (자기자본 + 총부채 - 현금)\nNOPAT = 영업이익 × (1 - 세율 21%)\n투하자본 대비 수익 효율성"),
    "매출총이익률" to LabelTooltip("Gross Margin", "(매출 - 매출원가) / 매출 × 100"),
    "영업이익률" to LabelTooltip("Operating Margin", "영업이익 / 매출 × 100"),
    "순이익률" to LabelTooltip("Net Profit Margin", "순이익 / 매출 × 100"),
    "FCF수익률" to LabelTooltip("Free Cash Flow Yield", "잉여현금흐름 / 시가총액 × 100"),

    // ── 재무(절대값) ──
    "총매출" to LabelTooltip("Total Revenue", "최근 12개월 총 매출액"),
    "EBITDA" to LabelTooltip("EBITDA", "영업이익 + 감가상각비\n현금 창출 능력 측정"),
    "잉여현금흐름" to LabelTooltip("Free Cash Flow", "영업현금흐름 - 자본적 지출\n실제 사용 가능한 현금"),
    "현금" to LabelTooltip("Cash & Equivalents", "현금 및 현금성 자산 총액"),
    "총부채" to LabelTooltip("Total Debt", "단기부채 + 장기부채 합계"),
    "주당순자산" to LabelTooltip("Book Value Per Share", "자기자본 / 발행주식수"),

    // ── 재무 > 성장 ──
    "매출성장률" to LabelTooltip("Revenue Growth (YoY)", "(당기 매출 - 전기 매출) / 전기 매출 × 100"),
    "이익성장률" to LabelTooltip("Earnings Growth (YoY)", "(당기 EPS - 전기 EPS) / 전기 EPS × 100"),
    "영업이익성장" to LabelTooltip("Operating Income Growth (YoY)", "(당기 영업이익 - 전기 영업이익) / 전기 영업이익 × 100"),

    // ── 재무 > 재무안정성 ──
    "부채비율" to LabelTooltip("Debt-to-Equity Ratio", "총부채 / 자기자본\n높을수록 부채 의존도가 높음"),
    "유동비율" to LabelTooltip("Current Ratio", "유동자산 / 유동부채\n1.0 이상이면 단기 지급 능력 양호"),
    "당좌비율" to LabelTooltip("Quick Ratio", "(유동자산 - 재고자산) / 유동부채\n재고 제외 즉시 현금화 가능 자산 기준"),
    "이자보상배율" to LabelTooltip("Interest Coverage Ratio", "영업이익 / 이자비용\n높을수록 이자 지급 능력 양호"),
    "부채증가율" to LabelTooltip("Debt Growth (YoY)", "(당기 부채 - 전기 부채) / 전기 부채 × 100"),
    "피오트로스키" to LabelTooltip("Piotroski F-Score", "재무 건전성 9개 항목 점수 합산 (0~9)\n수익성, 레버리지, 효율성 평가\n8~9: 매우 우량, 0~2: 위험"),
    "알트만Z" to LabelTooltip("Altman Z-Score", "부도 위험 예측 지표\n>2.99: 안전, 1.81~2.99: 회색지대, <1.81: 위험"),

    // ── 재무 > 밸류에이션 ──
    "PSR" to LabelTooltip("Price-to-Sales Ratio", "시가총액 / 매출 (최근 12개월)"),
    "EV/EBITDA" to LabelTooltip("Enterprise Value / EBITDA", "(시가총액 + 부채 - 현금) / EBITDA\n기업 인수 시 투자금 회수 기간 추정"),
    "PEG" to LabelTooltip("Price/Earnings-to-Growth", "PER / EPS 성장률\n1.0 미만이면 성장 대비 저평가"),
    "그레이엄넘버" to LabelTooltip("Graham Number", "sqrt(22.5 × EPS × 주당순자산)\n벤저민 그레이엄의 적정주가 산출식"),
    "P/FCF" to LabelTooltip("Price to Free Cash Flow", "시가총액 / 잉여현금흐름\n낮을수록 현금 창출력 대비 저평가"),
    "기업가치" to LabelTooltip("Enterprise Value", "시가총액 + 총부채 - 현금\n기업 인수 시 실제 비용"),
    "EV/매출" to LabelTooltip("EV / Revenue", "기업가치 / 매출\n매출 대비 기업가치 평가"),
    "DCF가치" to LabelTooltip("DCF Value", "미래 잉여현금흐름을 현재가치로 할인한 합계"),
    "DCF괴리" to LabelTooltip("DCF Upside %", "(DCF 가치 - 현재가) / 현재가 × 100"),

    // ── 수급 > 공매도 ──
    "공매도비율" to LabelTooltip("Short % of Float", "공매도 주식수 / 유통주식수 × 100"),
    "공매도주식수" to LabelTooltip("Shares Short", "현재 공매도된 주식 수"),
    "숏레이시오" to LabelTooltip("Short Ratio (Days to Cover)", "공매도 주식수 / 일평균 거래량\n공매도 청산에 필요한 예상 일수"),
    "발행주식수" to LabelTooltip("Shares Outstanding", "기업이 발행한 총 주식 수"),
    "유통주식수" to LabelTooltip("Float Shares", "시장에서 자유롭게 거래 가능한 주식 수\n발행주식수 - 내부자/기관 고정 보유분"),
    "내부자보유" to LabelTooltip("Insider Ownership %", "내부자(임원, 이사) 보유 주식 비율"),

    // ── 수급 > 내부자 거래 ──
    "매수건수" to LabelTooltip("Insider Buy Count (3M)", "최근 3개월간 내부자 매수 거래 건수 (SEC Form 4 기준)"),
    "매도건수" to LabelTooltip("Insider Sell Count (3M)", "최근 3개월간 내부자 매도 거래 건수 (SEC Form 4 기준)"),
    "최근거래" to LabelTooltip("Latest Insider Trade", "가장 최근 내부자 거래 유형 (매수 / 매도)"),
    "순매수주식" to LabelTooltip("Net Insider Shares (3M)", "최근 3개월간 내부자 매수 주식수 - 매도 주식수"),

    // ── 수급 > 기관 ──
    "기관보유비율" to LabelTooltip("Institutional Ownership %", "기관 보유 주식수 / 유통주식수 × 100"),

    // ── 애널리스트 > 컨센서스 ──
    "투자의견" to LabelTooltip("Analyst Rating", "컨센서스: 적극매수 / 매수 / 보유 / 매도 / 적극매도"),
    "의견점수" to LabelTooltip("Rating Score", "1.0(적극매수) ~ 5.0(적극매도)\n전체 애널리스트 의견의 평균"),
    "애널리스트수" to LabelTooltip("Analyst Count", "해당 종목을 커버하는 애널리스트 수"),

    // ── 애널리스트 > 목표가 ──
    "목표가(평균)" to LabelTooltip("Mean Target Price", "전체 애널리스트 12개월 목표가의 평균"),
    "목표괴리율" to LabelTooltip("Target Upside %", "(평균 목표가 - 현재가) / 현재가 × 100"),
    "목표가(최고)" to LabelTooltip("High Target Price", "애널리스트 12개월 목표가 중 최고값"),
    "목표가(최저)" to LabelTooltip("Low Target Price", "애널리스트 12개월 목표가 중 최저값"),

    // ── 애널리스트 > 성과 ──
    "1주 수익률" to LabelTooltip("1-Week Return", "(현재가 - 1주 전 가격) / 1주 전 가격 × 100"),
    "1개월 수익률" to LabelTooltip("1-Month Return", "(현재가 - 1개월 전 가격) / 1개월 전 가격 × 100"),
    "3개월 수익률" to LabelTooltip("3-Month Return", "(현재가 - 3개월 전 가격) / 3개월 전 가격 × 100"),
    "6개월 수익률" to LabelTooltip("6-Month Return", "(현재가 - 6개월 전 가격) / 6개월 전 가격 × 100"),
    "1년 수익률" to LabelTooltip("1-Year Return", "(현재가 - 1년 전 가격) / 1년 전 가격 × 100"),
    "YTD 수익률" to LabelTooltip("Year-to-Date Return", "(현재가 - 연초 가격) / 연초 가격 × 100"),

    // ── 소셜 ──
    "소셜점수" to LabelTooltip("Social Sentiment Score", "X(구 트위터) 소셜 미디어 센티먼트 분석 점수\n주간 배치 수집 (일요일)"),

    // ── ETF 추가 ──
    "NAV" to LabelTooltip("Net Asset Value", "ETF 1주당 순자산가치"),
    "보유종목수" to LabelTooltip("Holdings Count", "ETF가 보유한 총 종목 수"),
)
