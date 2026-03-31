package com.musiqq.stockscreener.ui.detail.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.musiqq.stockscreener.ui.theme.Pretendard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musiqq.stockscreener.domain.model.Equity
import com.musiqq.stockscreener.ui.utils.FreshnessIndicator
import com.musiqq.stockscreener.ui.utils.NumberFormatter

@Composable
fun OverviewTab(equity: Equity) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 기본 정보
        SectionCard("기본 정보") {
            DataRow("거래소", equity.exchange ?: "-")
            DataRow("섹터", equity.sector ?: "-")
            DataRow("산업", equity.industry ?: "-")
            DataRow("시가총액", NumberFormatter.formatMarketCap(equity.marketCap))
            DataRow("거래량", NumberFormatter.formatVolume(equity.volume))
            DataRow("베타", NumberFormatter.formatRatio(equity.beta))
        }

        // 밸류에이션
        SectionCard("밸류에이션") {
            DataRow("PER (TTM)", NumberFormatter.formatRatio(equity.peTtm))
            DataRow("예상PER", NumberFormatter.formatRatio(equity.peForward))
            DataRow("PBR", NumberFormatter.formatRatio(equity.pbRatio))
            DataRow("EPS", NumberFormatter.formatPrice(equity.epsTtm))
            DataRow("배당률", NumberFormatter.formatYield(equity.dividendYield))
        }

        // 기술적
        SectionCard("기술적 지표") {
            DataRow("RSI(14)", NumberFormatter.formatRatio(equity.rsi14))
            DataRow("50일선", NumberFormatter.formatPrice(equity.ma50))
            DataRow("200일선", NumberFormatter.formatPrice(equity.ma200))
            DataRow("52주 고가", NumberFormatter.formatPrice(equity.week52High))
            DataRow("52주 저가", NumberFormatter.formatPrice(equity.week52Low))
            DataRow("고가괴리", NumberFormatter.formatPct(equity.pctFrom52h?.let { it / 100 }))
            DataRow("상대거래량", NumberFormatter.formatMultiple(equity.relativeVolume))
        }

        // 스코어
        SectionCard("종합 스코어") {
            DataRow("밸류", NumberFormatter.formatScore(equity.scoreValue))
            DataRow("퀄리티", NumberFormatter.formatScore(equity.scoreQuality))
            DataRow("모멘텀", NumberFormatter.formatScore(equity.scoreMomentum))
            DataRow("성장", NumberFormatter.formatScore(equity.scoreGrowth))
            DataRow("종합", NumberFormatter.formatScore(equity.scoreTotal))
        }

        // Freshness
        equity.dataDate?.let {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                FreshnessIndicator(dateString = it, label = "데이터")
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            content()
        }
    }
}

@Composable
fun DataRow(label: String, value: String) {
    val tooltip = LABEL_TOOLTIPS[label]
    var showTooltip by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = if (tooltip != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (tooltip != null) Modifier.clickable { showTooltip = true }
                        else Modifier,
                    ),
            )
            Text(
                text = value,
                fontSize = 12.sp,
                fontFamily = Pretendard,
                fontWeight = FontWeight.Medium,
            )
        }

        if (showTooltip && tooltip != null) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = { showTooltip = false },
                modifier = Modifier.widthIn(max = 280.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        tooltip.english,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        tooltip.formula,
                        fontSize = 11.sp,
                        fontFamily = Pretendard,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
