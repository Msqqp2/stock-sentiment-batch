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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musiqq.stockscreener.data.remote.dto.EtfCountryExposureDto
import com.musiqq.stockscreener.data.remote.dto.EtfHoldingDto
import com.musiqq.stockscreener.data.remote.dto.EtfSectorExposureDto
import com.musiqq.stockscreener.ui.utils.NumberFormatter

@Composable
fun EtfHoldingsTab(holdings: List<EtfHoldingDto>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionCard("보유종목 (Top ${holdings.size})") {
            // 헤더
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
            ) {
                Text("종목", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.15f))
                Text("이름", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.40f))
                Text("비중", fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(0.20f))
                Text("시가총액", fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(0.25f))
            }
            HorizontalDivider(thickness = 1.dp)

            holdings.forEach { h ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                ) {
                    Text(
                        text = h.holdingSymbol ?: "-",
                        fontSize = 11.sp,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(0.15f),
                    )
                    Text(
                        text = h.holdingName ?: "-",
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(0.40f),
                    )
                    Text(
                        text = h.weight?.let { String.format("%.2f%%", it) } ?: "-",
                        fontSize = 11.sp,
                        fontFamily = Pretendard,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(0.20f),
                    )
                    Text(
                        text = h.marketValue?.let { NumberFormatter.formatDollarVolume(it) } ?: "-",
                        fontSize = 11.sp,
                        fontFamily = Pretendard,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(0.25f),
                    )
                }
                HorizontalDivider(thickness = 0.5.dp)
            }
        }

        if (holdings.isEmpty()) {
            Text(
                text = "보유종목 데이터가 없습니다.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun EtfSectorTab(sectors: List<EtfSectorExposureDto>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionCard("섹터 비중") {
            sectors.forEach { s ->
                DataRow(s.sector, s.weight?.let { String.format("%.2f%%", it) } ?: "-")
            }
        }

        if (sectors.isEmpty()) {
            Text(
                text = "섹터 데이터가 없습니다.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun EtfCountryTab(countries: List<EtfCountryExposureDto>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionCard("국가 비중") {
            countries.forEach { c ->
                DataRow(c.country, c.weight?.let { String.format("%.2f%%", it) } ?: "-")
            }
        }

        if (countries.isEmpty()) {
            Text(
                text = "국가 데이터가 없습니다.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}
