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
        // Yahoo 컨센서스
        SectionCard("Yahoo 컨센서스") {
            DataRow("투자의견", equity.analystRating ?: "-")
            DataRow("의견점수", dto?.analystRatingScore?.let { String.format("%.1f", it) } ?: "-")
            DataRow("애널리스트수", NumberFormatter.formatInt(equity.analystCount))
        }

        // Finnhub 투자의견
        val hasFhRec = dto?.fhRecBuy != null || dto?.fhRecHold != null || dto?.fhRecSell != null
        if (hasFhRec) {
            SectionCard("Finnhub 투자의견") {
                DataRow("FH 적극매수", NumberFormatter.formatInt(dto?.fhRecStrongBuy))
                DataRow("FH 매수", NumberFormatter.formatInt(dto?.fhRecBuy))
                DataRow("FH 보유", NumberFormatter.formatInt(dto?.fhRecHold))
                DataRow("FH 매도", NumberFormatter.formatInt(dto?.fhRecSell))
                DataRow("FH 적극매도", NumberFormatter.formatInt(dto?.fhRecStrongSell))
                DataRow("의견기간", dto?.fhRecPeriod ?: "-")
            }
        }

        SectionCard("목표가") {
            DataRow("목표가(평균)", NumberFormatter.formatPrice(equity.targetMean))
            DataRow("목표괴리율", NumberFormatter.formatPct(equity.targetUpsidePct?.let { it / 100 }))
            DataRow("목표가(최고)", NumberFormatter.formatPrice(dto?.targetHigh))
            DataRow("목표가(최저)", NumberFormatter.formatPrice(dto?.targetLow))
        }

        // Finnhub 내부자 센티먼트
        val hasFhInsider = dto?.fhInsiderMspr != null || dto?.fhInsiderBuyCount != null
        if (hasFhInsider) {
            SectionCard("Finnhub 내부자") {
                DataRow("MSPR", NumberFormatter.formatRatio(dto?.fhInsiderMspr))
                DataRow("내부자변동", NumberFormatter.formatRatio(dto?.fhInsiderChange))
                DataRow("FH매수건수", NumberFormatter.formatInt(dto?.fhInsiderBuyCount))
                DataRow("FH매도건수", NumberFormatter.formatInt(dto?.fhInsiderSellCount))
            }
        }

        // 소셜 센티먼트 (Finnhub)
        val hasFhSocial = dto?.fhSocialSentiment != null
        if (hasFhSocial) {
            SectionCard("소셜 센티먼트") {
                DataRow("소셜센티먼트", NumberFormatter.formatRatio(dto?.fhSocialSentiment))
                DataRow("소셜긍정", NumberFormatter.formatInt(dto?.fhSocialPositive))
                DataRow("소셜부정", NumberFormatter.formatInt(dto?.fhSocialNegative))
            }
        }

        // Polymarket
        val hasPm = dto?.pmBuzzScore != null || dto?.pmSentimentScore != null
        if (hasPm) {
            SectionCard("Polymarket") {
                DataRow("버즈점수", NumberFormatter.formatRatio(dto?.pmBuzzScore))
                DataRow("PM트렌드", dto?.pmTrend ?: "-")
                DataRow("PM센티먼트", NumberFormatter.formatRatio(dto?.pmSentimentScore))
                DataRow("PM강세", dto?.pmBullishPct?.let { "${it}%" } ?: "-")
                DataRow("PM약세", dto?.pmBearishPct?.let { "${it}%" } ?: "-")
            }
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
