package com.musiqq.stockscreener.ui.detail.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.musiqq.stockscreener.ui.theme.Pretendard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musiqq.stockscreener.data.remote.dto.InsiderTradeDto
import com.musiqq.stockscreener.domain.model.Equity
import com.musiqq.stockscreener.ui.theme.StockColors
import com.musiqq.stockscreener.ui.utils.NumberFormatter

@Composable
fun SupplyDemandTab(equity: Equity, insiderTrades: List<InsiderTradeDto>) {
    val stockColors = StockColors.current
    val dto = equity.dto

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionCard("공매도") {
            DataRow("공매도비율", NumberFormatter.formatPct(equity.shortPctFloat))
        }

        SectionCard("내부자 거래 (3개월)") {
            DataRow("매수건수", NumberFormatter.formatInt(equity.insiderBuy3m))
            DataRow("매도건수", NumberFormatter.formatInt(dto?.insiderSell3m))
            DataRow("최근거래", equity.insiderLatestType ?: "-")
            DataRow("순매수주식", NumberFormatter.formatInt(dto?.insiderNetShares))
        }

        SectionCard("기관보유") {
            DataRow("기관보유비율", NumberFormatter.formatPct(dto?.instOwnership))
        }

        // 최근 내부자 거래 리스트
        if (insiderTrades.isNotEmpty()) {
            SectionCard("최근 내부자 거래") {
                insiderTrades.take(10).forEach { trade ->
                    val isBuy = trade.txnType.contains("Purchase", ignoreCase = true) ||
                        trade.txnType.contains("Buy", ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) {
                        Text(
                            text = trade.filingDate,
                            fontSize = 10.sp,
                            fontFamily = Pretendard,
                            modifier = Modifier.weight(0.25f),
                        )
                        Text(
                            text = trade.insiderName,
                            fontSize = 10.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(0.35f),
                        )
                        Text(
                            text = trade.txnType,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isBuy) stockColors.up else stockColors.down,
                            modifier = Modifier.weight(0.2f),
                        )
                        Text(
                            text = NumberFormatter.formatVolume(trade.shares),
                            fontSize = 10.sp,
                            fontFamily = Pretendard,
                            modifier = Modifier.weight(0.2f),
                        )
                    }
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
