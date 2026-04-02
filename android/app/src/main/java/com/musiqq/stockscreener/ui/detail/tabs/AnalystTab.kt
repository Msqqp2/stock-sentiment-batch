package com.musiqq.stockscreener.ui.detail.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musiqq.stockscreener.data.remote.AnalystRatingDto
import com.musiqq.stockscreener.data.remote.RecommendationHistoryDto
import com.musiqq.stockscreener.domain.model.Equity
import com.musiqq.stockscreener.ui.utils.NumberFormatter

@Composable
fun AnalystTab(
    equity: Equity,
    recommendationHistory: List<RecommendationHistoryDto> = emptyList(),
    analystRatings: List<AnalystRatingDto> = emptyList(),
) {
    val dto = equity.dto

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 회사 설명 (Finviz)
        dto?.description?.let { desc ->
            if (desc.isNotBlank()) {
                SectionCard("회사 개요") {
                    Text(desc, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }

        // Yahoo 컨센서스
        SectionCard("애널리스트 의견 (Yahoo)") {
            DataRow("투자의견", equity.analystRating ?: "-")
            DataRow("의견점수", dto?.analystRatingScore?.let { String.format("%.1f", it) } ?: "-")
            DataRow("애널리스트수", NumberFormatter.formatInt(equity.analystCount))
        }

        // Finnhub 투자의견
        val hasFhRec = dto?.fhRecBuy != null || dto?.fhRecHold != null || dto?.fhRecSell != null
        if (hasFhRec) {
            SectionCard("애널리스트 의견 (Finnhub)") {
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
                DataRow("MSPR (내부자 순매수 비율)", NumberFormatter.formatRatio(dto?.fhInsiderMspr))
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

        // Recommendation Trends 스택 바 차트
        if (recommendationHistory.isNotEmpty()) {
            SectionCard("Recommendation Trends (Finnhub)") {
                RecommendationTrendsChart(recommendationHistory)
            }
        }

        // 애널리스트 업/다운그레이드 히스토리 (Finviz)
        if (analystRatings.isNotEmpty()) {
            SectionCard("애널리스트 히스토리 (Finviz)") {
                analystRatings.take(10).forEach { r ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(r.date ?: "", fontSize = 10.sp, modifier = Modifier.width(70.dp))
                        Text(
                            r.action ?: "",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                r.action?.contains("Upgrade", ignoreCase = true) == true -> Color(0xFF4CAF50)
                                r.action?.contains("Downgrade", ignoreCase = true) == true -> Color(0xFFF44336)
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.width(70.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(r.analyst ?: "", fontSize = 10.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(r.rating ?: "", fontSize = 10.sp, modifier = Modifier.width(60.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // 유사 종목 (Finviz peers)
        dto?.peers?.let { peers ->
            if (peers.isNotEmpty()) {
                SectionCard("유사 종목") {
                    Text(peers.joinToString(", "), fontSize = 12.sp)
                }
            }
        }

        // 보유 ETF (Finviz)
        dto?.etfHolders?.let { holders ->
            if (holders.isNotEmpty()) {
                SectionCard("보유 ETF") {
                    Text(holders.joinToString(", "), fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun RecommendationTrendsChart(history: List<RecommendationHistoryDto>) {
    val colorStrongBuy = Color(0xFF1B5E20)
    val colorBuy = Color(0xFF4CAF50)
    val colorHold = Color(0xFF9E9E9E)
    val colorSell = Color(0xFFF44336)
    val colorStrongSell = Color(0xFFB71C1C)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        history.forEach { item ->
            val total = (item.strongBuy ?: 0) + (item.buy ?: 0) + (item.hold ?: 0) +
                    (item.sell ?: 0) + (item.strongSell ?: 0)
            val maxHeight = 120.dp

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(60.dp),
            ) {
                // Stacked bar
                if (total > 0) {
                    val segments = listOf(
                        (item.strongBuy ?: 0) to colorStrongBuy,
                        (item.buy ?: 0) to colorBuy,
                        (item.hold ?: 0) to colorHold,
                        (item.sell ?: 0) to colorSell,
                        (item.strongSell ?: 0) to colorStrongSell,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        segments.forEach { (count, color) ->
                            if (count > 0) {
                                val h = (count.toFloat() / total * 100).dp.coerceAtMost(maxHeight)
                                Box(
                                    modifier = Modifier
                                        .width(30.dp)
                                        .height(h)
                                        .background(color),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("$count", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                // Period label (yyyy-MM-dd → MMM yyyy)
                Text(
                    text = item.period?.take(7) ?: "",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    // Legend
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        listOf(
            "StBuy" to Color(0xFF1B5E20),
            "Buy" to Color(0xFF4CAF50),
            "Hold" to Color(0xFF9E9E9E),
            "Sell" to Color(0xFFF44336),
            "StSell" to Color(0xFFB71C1C),
        ).forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(10.dp).height(10.dp).background(color))
                Spacer(Modifier.width(2.dp))
                Text(label, fontSize = 9.sp)
            }
        }
    }
}
