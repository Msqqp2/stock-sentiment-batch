package com.musiqq.stockscreener.ui.screener

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musiqq.stockscreener.domain.model.FilterCriteria

private val SECTORS = listOf(
    "Technology", "Healthcare", "Financial Services", "Consumer Cyclical",
    "Communication Services", "Industrials", "Consumer Defensive",
    "Energy", "Basic Materials", "Real Estate", "Utilities",
)

private val ANALYST_RATINGS = listOf("strong_buy", "buy", "hold", "sell", "strong_sell")
private val ANALYST_RATING_LABELS = mapOf(
    "strong_buy" to "Strong Buy",
    "buy" to "Buy",
    "hold" to "Hold",
    "sell" to "Sell",
    "strong_sell" to "Strong Sell",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterPanel(
    criteria: FilterCriteria,
    onApply: (FilterCriteria) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    var marketCapMin by remember(criteria) { mutableStateOf(criteria.marketCapMin?.toString() ?: "") }
    var marketCapMax by remember(criteria) { mutableStateOf(criteria.marketCapMax?.toString() ?: "") }
    var peMin by remember(criteria) { mutableStateOf(criteria.peMin?.toString() ?: "") }
    var peMax by remember(criteria) { mutableStateOf(criteria.peMax?.toString() ?: "") }
    var divYieldMin by remember(criteria) { mutableStateOf(criteria.dividendYieldMin?.toString() ?: "") }
    var roeMin by remember(criteria) { mutableStateOf(criteria.roeMin?.toString() ?: "") }
    var debtMax by remember(criteria) { mutableStateOf(criteria.debtToEquityMax?.toString() ?: "") }
    var revGrowthMin by remember(criteria) { mutableStateOf(criteria.revenueGrowthMin?.toString() ?: "") }
    var targetUpsideMin by remember(criteria) { mutableStateOf(criteria.targetUpsideMin?.toString() ?: "") }
    var selectedSectors by remember(criteria) { mutableStateOf(criteria.sectors.toSet()) }
    var selectedRatings by remember(criteria) { mutableStateOf(criteria.analystRatings.toSet()) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { expanded = !expanded }) {
                Icon(Icons.Default.FilterList, contentDescription = "필터")
            }
            Text("필터", fontSize = 14.sp, style = MaterialTheme.typography.titleSmall)
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                // 시가총액
                Text("시가총액 (USD)", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterTextField("최소", marketCapMin, { marketCapMin = it }, Modifier.weight(1f))
                    FilterTextField("최대", marketCapMax, { marketCapMax = it }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))

                // PER
                Text("PER", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterTextField("최소", peMin, { peMin = it }, Modifier.weight(1f))
                    FilterTextField("최대", peMax, { peMax = it }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))

                // 배당률 / ROE / 부채비율
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterTextField("배당률 최소", divYieldMin, { divYieldMin = it }, Modifier.weight(1f))
                    FilterTextField("ROE 최소", roeMin, { roeMin = it }, Modifier.weight(1f))
                    FilterTextField("D/E 최대", debtMax, { debtMax = it }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterTextField("매출성장 최소", revGrowthMin, { revGrowthMin = it }, Modifier.weight(1f))
                    FilterTextField("목표괴리 최소", targetUpsideMin, { targetUpsideMin = it }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))

                // 섹터
                Text("섹터", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SECTORS.forEach { sector ->
                        FilterChip(
                            selected = sector in selectedSectors,
                            onClick = {
                                selectedSectors = if (sector in selectedSectors) selectedSectors - sector
                                else selectedSectors + sector
                            },
                            label = { Text(sector, fontSize = 10.sp) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                // 투자의견
                Text("투자의견", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ANALYST_RATINGS.forEach { rating ->
                        FilterChip(
                            selected = rating in selectedRatings,
                            onClick = {
                                selectedRatings = if (rating in selectedRatings) selectedRatings - rating
                                else selectedRatings + rating
                            },
                            label = { Text(ANALYST_RATING_LABELS[rating] ?: rating, fontSize = 10.sp) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        marketCapMin = ""; marketCapMax = ""; peMin = ""; peMax = ""
                        divYieldMin = ""; roeMin = ""; debtMax = ""
                        revGrowthMin = ""; targetUpsideMin = ""
                        selectedSectors = emptySet(); selectedRatings = emptySet()
                        onApply(FilterCriteria(assetType = criteria.assetType))
                    }) {
                        Text("초기화")
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        onApply(
                            criteria.copy(
                                marketCapMin = marketCapMin.toLongOrNull(),
                                marketCapMax = marketCapMax.toLongOrNull(),
                                peMin = peMin.toDoubleOrNull(),
                                peMax = peMax.toDoubleOrNull(),
                                dividendYieldMin = divYieldMin.toDoubleOrNull(),
                                roeMin = roeMin.toDoubleOrNull(),
                                debtToEquityMax = debtMax.toDoubleOrNull(),
                                revenueGrowthMin = revGrowthMin.toDoubleOrNull(),
                                targetUpsideMin = targetUpsideMin.toDoubleOrNull(),
                                sectors = selectedSectors.toList(),
                                analystRatings = selectedRatings.toList(),
                            )
                        )
                        expanded = false
                    }) {
                        Text("적용")
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 10.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodySmall,
    )
}
