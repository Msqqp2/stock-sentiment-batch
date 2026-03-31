package com.musiqq.stockscreener.ui.detail.tabs

data class LabelTooltip(
    val english: String,
    val formula: String,
)

val LABEL_TOOLTIPS = mapOf(
    // ── 개요 > 기본 정보 ──
    "거래소" to LabelTooltip("Exchange", "The stock exchange where the security is listed"),
    "섹터" to LabelTooltip("Sector", "Industry sector classification (GICS)"),
    "산업" to LabelTooltip("Industry", "Specific industry within the sector"),
    "시가총액" to LabelTooltip("Market Capitalization", "Share Price * Shares Outstanding"),
    "거래량" to LabelTooltip("Volume", "Number of shares traded in the most recent session"),
    "베타" to LabelTooltip("Beta", "Sensitivity to market movements (S&P 500 = 1.0), based on 5-year monthly returns"),

    // ── 개요 > 밸류에이션 ──
    "PER (TTM)" to LabelTooltip("Price-to-Earnings Ratio (TTM)", "Price / EPS (Trailing 12 Months)"),
    "예상PER" to LabelTooltip("Forward P/E", "Price / Estimated Next Year EPS"),
    "PBR" to LabelTooltip("Price-to-Book Ratio", "Price / Book Value Per Share"),
    "EPS" to LabelTooltip("Earnings Per Share", "Net Income / Shares Outstanding (TTM)"),
    "배당률" to LabelTooltip("Dividend Yield", "Annual Dividend Per Share / Price * 100"),

    // ── 개요 > 기술적 지표 ──
    "RSI(14)" to LabelTooltip("Relative Strength Index (14)", "100 - 100 / (1 + AvgGain / AvgLoss)\nWilder's exponential smoothing over 14 periods"),
    "50일선" to LabelTooltip("50-Day Moving Average", "Average closing price over last 50 trading days"),
    "200일선" to LabelTooltip("200-Day Moving Average", "Average closing price over last 200 trading days"),
    "52주 고가" to LabelTooltip("52-Week High", "Highest price in the last 52 weeks"),
    "52주 저가" to LabelTooltip("52-Week Low", "Lowest price in the last 52 weeks"),
    "고가괴리" to LabelTooltip("% from 52-Week High", "(Price - 52wHigh) / 52wHigh * 100"),
    "상대거래량" to LabelTooltip("Relative Volume", "Today's Volume / 20-Day Average Volume"),

    // ── 개요 > 종합 스코어 ──
    "밸류" to LabelTooltip("Value Score", "Composite: PE, PB, PS, FCF Yield percentile ranks (0-100)"),
    "퀄리티" to LabelTooltip("Quality Score", "Composite: normalized ROE, ROA, Operating Margin (0-100)"),
    "모멘텀" to LabelTooltip("Momentum Score", "Composite: RSI, price performance percentile ranks (0-100)"),
    "성장" to LabelTooltip("Growth Score", "Composite: Revenue growth, Earnings growth percentile ranks (0-100)"),
    "종합" to LabelTooltip("Total Score", "Weighted average of Value, Quality, Momentum, Growth (0-100)"),

    // ── 재무 > 수익성 ──
    "ROE" to LabelTooltip("Return on Equity", "Net Income / Shareholders' Equity"),
    "ROA" to LabelTooltip("Return on Assets", "Net Income / Total Assets"),
    "ROIC" to LabelTooltip("Return on Invested Capital", "NOPAT / (Total Equity + Total Debt - Cash)"),
    "매출총이익률" to LabelTooltip("Gross Margin", "(Revenue - COGS) / Revenue * 100"),
    "영업이익률" to LabelTooltip("Operating Margin", "Operating Income / Revenue * 100"),
    "순이익률" to LabelTooltip("Net Profit Margin", "Net Income / Revenue * 100"),
    "FCF수익률" to LabelTooltip("Free Cash Flow Yield", "FCF Per Share / Price * 100"),

    // ── 재무 > 성장 ──
    "매출성장률" to LabelTooltip("Revenue Growth (YoY)", "(Current Rev - Prior Rev) / Prior Rev * 100"),
    "이익성장률" to LabelTooltip("Earnings Growth (YoY)", "(Current EPS - Prior EPS) / Prior EPS * 100"),
    "영업이익성장" to LabelTooltip("Operating Income Growth (YoY)", "(Current OI - Prior OI) / Prior OI * 100"),

    // ── 재무 > 재무안정성 ──
    "부채비율" to LabelTooltip("Debt-to-Equity Ratio", "Total Debt / Shareholders' Equity"),
    "유동비율" to LabelTooltip("Current Ratio", "Current Assets / Current Liabilities"),
    "당좌비율" to LabelTooltip("Quick Ratio", "(Current Assets - Inventory) / Current Liabilities"),
    "이자보상배율" to LabelTooltip("Interest Coverage Ratio", "EBIT / Interest Expense"),
    "부채증가율" to LabelTooltip("Debt Growth (YoY)", "(Current Debt - Prior Debt) / Prior Debt * 100"),

    // ── 재무 > 밸류에이션 ──
    "PSR" to LabelTooltip("Price-to-Sales Ratio", "Market Cap / Revenue (TTM)"),
    "EV/EBITDA" to LabelTooltip("Enterprise Value / EBITDA", "(Market Cap + Debt - Cash) / EBITDA"),
    "PEG" to LabelTooltip("Price/Earnings-to-Growth", "PE Ratio / EPS Growth Rate"),
    "그레이엄넘버" to LabelTooltip("Graham Number", "sqrt(22.5 * EPS * Book Value Per Share)"),
    "DCF가치" to LabelTooltip("DCF Value", "Sum of projected Free Cash Flows discounted to present value"),
    "DCF괴리" to LabelTooltip("DCF Upside %", "(DCF Value - Price) / Price * 100"),

    // ── 수급 > 공매도 ──
    "공매도비율" to LabelTooltip("Short % of Float", "Shares Sold Short / Float * 100"),

    // ── 수급 > 내부자 거래 ──
    "매수건수" to LabelTooltip("Insider Buy Count (3M)", "Number of insider purchase transactions in last 3 months (SEC Form 4)"),
    "매도건수" to LabelTooltip("Insider Sell Count (3M)", "Number of insider sale transactions in last 3 months (SEC Form 4)"),
    "최근거래" to LabelTooltip("Latest Insider Trade", "Most recent insider transaction type (Purchase / Sale)"),
    "순매수주식" to LabelTooltip("Net Insider Shares (3M)", "Shares Bought - Shares Sold over last 3 months"),

    // ── 수급 > 기관 ──
    "기관보유비율" to LabelTooltip("Institutional Ownership %", "Shares held by institutions / Float * 100"),

    // ── 애널리스트 > 컨센서스 ──
    "투자의견" to LabelTooltip("Analyst Rating", "Consensus: Strong Buy / Buy / Hold / Sell / Strong Sell"),
    "의견점수" to LabelTooltip("Rating Score", "1.0 (Strong Buy) ~ 5.0 (Strong Sell), average of all analyst ratings"),
    "애널리스트수" to LabelTooltip("Analyst Count", "Number of analysts covering this stock"),

    // ── 애널리스트 > 목표가 ──
    "목표가(평균)" to LabelTooltip("Mean Target Price", "Average of all analyst 12-month target prices"),
    "목표괴리율" to LabelTooltip("Target Upside %", "(Mean Target - Price) / Price * 100"),
    "목표가(최고)" to LabelTooltip("High Target Price", "Highest analyst 12-month target price"),
    "목표가(최저)" to LabelTooltip("Low Target Price", "Lowest analyst 12-month target price"),

    // ── 애널리스트 > 성과 ──
    "1주 수익률" to LabelTooltip("1-Week Return", "(Price - Price_1w_ago) / Price_1w_ago * 100"),
    "1개월 수익률" to LabelTooltip("1-Month Return", "(Price - Price_1m_ago) / Price_1m_ago * 100"),
    "3개월 수익률" to LabelTooltip("3-Month Return", "(Price - Price_3m_ago) / Price_3m_ago * 100"),
    "6개월 수익률" to LabelTooltip("6-Month Return", "(Price - Price_6m_ago) / Price_6m_ago * 100"),
    "1년 수익률" to LabelTooltip("1-Year Return", "(Price - Price_1y_ago) / Price_1y_ago * 100"),
    "YTD 수익률" to LabelTooltip("Year-to-Date Return", "(Price - Price_Jan1) / Price_Jan1 * 100"),
)
