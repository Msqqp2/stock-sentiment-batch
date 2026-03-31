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
fun AnalystTab(equity: Equity) {
    val dto = equity.dto

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionCard("컨센서스") {
            DataRow("투자의견", equity.analystRating ?: "-")
            DataRow("의견점수", dto?.analystRatingScore?.let { String.format("%.1f", it) } ?: "-")
            DataRow("애널리스트수", NumberFormatter.formatInt(equity.analystCount))
        }

        SectionCard("목표가") {
            DataRow("목표가(평균)", NumberFormatter.formatPrice(equity.targetMean))
            DataRow("목표괴리율", NumberFormatter.formatPct(equity.targetUpsidePct?.let { it / 100 }))
            DataRow("목표가(최고)", NumberFormatter.formatPrice(dto?.targetHigh))
            DataRow("목표가(최저)", NumberFormatter.formatPrice(dto?.targetLow))
        }

        SectionCard("성과") {
            DataRow("1주 수익률", NumberFormatter.formatPct(dto?.perf1w))
            DataRow("1개월 수익률", NumberFormatter.formatPct(dto?.perf1m))
            DataRow("3개월 수익률", NumberFormatter.formatPct(dto?.perf3m))
            DataRow("6개월 수익률", NumberFormatter.formatPct(dto?.perf6m))
            DataRow("1년 수익률", NumberFormatter.formatPct(dto?.perf1y))
            DataRow("YTD 수익률", NumberFormatter.formatPct(dto?.perfYtd))
        }

        Spacer(Modifier.height(16.dp))
    }
}
