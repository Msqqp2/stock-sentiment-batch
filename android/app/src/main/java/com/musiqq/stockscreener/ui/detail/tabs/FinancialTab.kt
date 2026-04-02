package com.musiqq.stockscreener.ui.detail.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.musiqq.stockscreener.domain.model.Equity
import com.musiqq.stockscreener.ui.utils.NumberFormatter

@Composable
fun FinancialTab(equity: Equity) {
    val dto = equity.dto

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionCard("수익성") {
            DataRow("ROE", NumberFormatter.formatPct(dto?.roe))
            DataRow("ROA", NumberFormatter.formatPct(dto?.roa))
            DataRow("ROIC (투하자본이익률)", NumberFormatter.formatPct(dto?.roic))
            DataRow("매출총이익률", NumberFormatter.formatPct(dto?.grossMargin))
            DataRow("영업이익률", NumberFormatter.formatPct(dto?.operatingMargin))
            DataRow("순이익률", NumberFormatter.formatPct(dto?.profitMargin))
            DataRow("FCF수익률", NumberFormatter.formatPct(dto?.fcfYield))
        }

        SectionCard("재무(절대값)") {
            DataRow("총매출", NumberFormatter.formatMarketCap(dto?.totalRevenue))
            DataRow("EBITDA", NumberFormatter.formatMarketCap(dto?.ebitda))
            DataRow("잉여현금흐름", NumberFormatter.formatMarketCap(dto?.freeCashflow))
            DataRow("현금", NumberFormatter.formatMarketCap(dto?.totalCash))
            DataRow("총부채", NumberFormatter.formatMarketCap(dto?.totalDebt))
            DataRow("주당순자산", NumberFormatter.formatPrice(dto?.bookValue))
        }

        SectionCard("성장") {
            DataRow("매출성장률", NumberFormatter.formatPct(dto?.revenueGrowth))
            DataRow("이익성장률", NumberFormatter.formatPct(dto?.earningsGrowth))
            DataRow("영업이익성장", NumberFormatter.formatPct(dto?.opIncomeGrowth))
        }

        SectionCard("재무안정성") {
            DataRow("부채비율", NumberFormatter.formatRatio(dto?.debtToEquity))
            DataRow("유동비율", NumberFormatter.formatRatio(dto?.currentRatio))
            DataRow("당좌비율", NumberFormatter.formatRatio(dto?.quickRatio))
            DataRow("이자보상배율", NumberFormatter.formatRatio(dto?.interestCoverage))
            DataRow("부채증가율", NumberFormatter.formatPct(dto?.debtGrowth))
            DataRow("피오트로스키", NumberFormatter.formatInt(dto?.piotroskiScore))
            DataRow("알트만Z", NumberFormatter.formatRatio(dto?.altmanZScore))
        }

        SectionCard("밸류에이션") {
            DataRow("PSR", NumberFormatter.formatRatio(dto?.psRatio))
            DataRow("EV/EBITDA", NumberFormatter.formatRatio(dto?.evEbitda))
            DataRow("PEG", NumberFormatter.formatRatio(dto?.pegRatio))
            DataRow("그레이엄넘버", NumberFormatter.formatPrice(dto?.grahamNumber))
            DataRow("DCF가치", NumberFormatter.formatPrice(dto?.dcfValue))
            DataRow("DCF괴리", NumberFormatter.formatPct(dto?.dcfUpsidePct?.let { it / 100 }))
            DataRow("P/FCF", NumberFormatter.formatRatio(dto?.pfcfRatio))
            DataRow("기업가치", NumberFormatter.formatMarketCap(dto?.ev))
            DataRow("EV/매출", NumberFormatter.formatRatio(dto?.evRevenue))
        }

        SectionCard("배당") {
            DataRow("배당률", NumberFormatter.formatYield(dto?.dividendYield))
            DataRow("주당배당금", NumberFormatter.formatPrice(dto?.dividendRate))
            DataRow("배당성향", NumberFormatter.formatPct(dto?.payoutRatio))
            DataRow("5년평균배당률", NumberFormatter.formatYield(dto?.avgDividendYield5y))
            DataRow("주주환원율", NumberFormatter.formatPct(dto?.shareholderYield))
        }

        Spacer(Modifier.height(16.dp))
    }
}
