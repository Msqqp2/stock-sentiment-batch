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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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

private val ETF_ASSET_CLASSES = listOf("Equity", "Fixed Income", "Commodities", "Real Estate", "Currency")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterPanel(
    criteria: FilterCriteria,
    onApply: (FilterCriteria) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(true) }

    // 공통
    var marketCapMin by remember(criteria) { mutableStateOf(criteria.marketCapMin?.toString() ?: "") }
    var marketCapMax by remember(criteria) { mutableStateOf(criteria.marketCapMax?.toString() ?: "") }
    var peMin by remember(criteria) { mutableStateOf(criteria.peMin?.toString() ?: "") }
    var peMax by remember(criteria) { mutableStateOf(criteria.peMax?.toString() ?: "") }
    var divYieldMin by remember(criteria) { mutableStateOf(criteria.dividendYieldMin?.toString() ?: "") }
    var roeMin by remember(criteria) { mutableStateOf(criteria.roeMin?.toString() ?: "") }
    var debtMax by remember(criteria) { mutableStateOf(criteria.debtToEquityMax?.toString() ?: "") }
    var revGrowthMin by remember(criteria) { mutableStateOf(criteria.revenueGrowthMin?.toString() ?: "") }
    var targetUpsideMin by remember(criteria) { mutableStateOf(criteria.targetUpsideMin?.toString() ?: "") }
    var pctFrom52hMin by remember(criteria) { mutableStateOf(criteria.pctFrom52hMin?.toString() ?: "") }
    var pctFrom52hMax by remember(criteria) { mutableStateOf(criteria.pctFrom52hMax?.toString() ?: "") }
    var insiderBuy3mMin by remember(criteria) { mutableStateOf(criteria.insiderBuy3mMin?.toString() ?: "") }
    var shortPctFloatMin by remember(criteria) { mutableStateOf(criteria.shortPctFloatMin?.toString() ?: "") }
    var shortPctFloatMax by remember(criteria) { mutableStateOf(criteria.shortPctFloatMax?.toString() ?: "") }
    var selectedSectors by remember(criteria) { mutableStateOf(criteria.sectors.toSet()) }
    var selectedRatings by remember(criteria) { mutableStateOf(criteria.analystRatings.toSet()) }

    // ETF 전용
    var expenseRatioMax by remember(criteria) { mutableStateOf(criteria.expenseRatioMax?.toString() ?: "") }
    var aumMin by remember(criteria) { mutableStateOf(criteria.aumMin?.toString() ?: "") }
    var selectedAssetClass by remember(criteria) { mutableStateOf(criteria.assetClass) }
    var holdingSymbol by remember(criteria) { mutableStateOf(criteria.holdingSymbol ?: "") }
    var holdingMinWeight by remember(criteria) { mutableStateOf(criteria.holdingMinWeight?.toString() ?: "") }

    val isEtf = criteria.assetType == "etf"

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
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!isEtf) {
                    // ═══════════ Stock 조건 ═══════════

                    // ── 밸류에이션 ──
                    FilterSectionHeader("밸류에이션")
                    FilterRangeRow("시가총액 (USD)", marketCapMin, { marketCapMin = it }, marketCapMax, { marketCapMax = it })
                    FilterRangeRow("PER", peMin, { peMin = it }, peMax, { peMax = it })

                    // ── 재무 ──
                    FilterSectionHeader("재무")
                    FilterField("배당수익률 최소 (%)", divYieldMin) { divYieldMin = it }
                    FilterField("ROE 최소", roeMin) { roeMin = it }
                    FilterField("D/E (부채비율) 최대", debtMax) { debtMax = it }
                    FilterField("매출성장률 최소", revGrowthMin) { revGrowthMin = it }

                    // ── 애널리스트 ──
                    FilterSectionHeader("애널리스트")
                    FilterField("목표가 괴리율 최소 (%)", targetUpsideMin) { targetUpsideMin = it }
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

                    // ── 기술적 ──
                    FilterSectionHeader("기술적 / 모멘텀")
                    FilterRangeRow("52주 고점 대비 (%)", pctFrom52hMin, { pctFrom52hMin = it }, pctFrom52hMax, { pctFrom52hMax = it })

                    // ── 수급 ──
                    FilterSectionHeader("수급")
                    FilterField("내부자 매수 3개월 최소 (건)", insiderBuy3mMin) { insiderBuy3mMin = it }
                    FilterRangeRow("공매도 비율 (%)", shortPctFloatMin, { shortPctFloatMin = it }, shortPctFloatMax, { shortPctFloatMax = it })

                    // ── 섹터 ──
                    FilterSectionHeader("섹터")
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
                } else {
                    // ═══════════ ETF 조건 ═══════════

                    // ── 기본 ──
                    FilterSectionHeader("ETF 기본")
                    FilterField("AUM 최소 (USD)", aumMin) { aumMin = it }
                    FilterField("보수율 최대 (%)", expenseRatioMax) { expenseRatioMax = it }
                    FilterField("배당수익률 최소 (%)", divYieldMin) { divYieldMin = it }

                    // ── 자산 유형 ──
                    FilterSectionHeader("자산 유형")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ETF_ASSET_CLASSES.forEach { cls ->
                            FilterChip(
                                selected = selectedAssetClass == cls,
                                onClick = {
                                    selectedAssetClass = if (selectedAssetClass == cls) null else cls
                                },
                                label = { Text(cls, fontSize = 10.sp) },
                            )
                        }
                    }

                    // ── 기술적 ──
                    FilterSectionHeader("기술적 / 모멘텀")
                    FilterRangeRow("52주 고점 대비 (%)", pctFrom52hMin, { pctFrom52hMin = it }, pctFrom52hMax, { pctFrom52hMax = it })

                    // ── 보유 종목 역검색 ──
                    FilterSectionHeader("보유 종목 역검색")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterTextField("종목 심볼", holdingSymbol, { holdingSymbol = it }, Modifier.weight(1f), isDecimal = false)
                        FilterTextField("최소 비중 (%)", holdingMinWeight, { holdingMinWeight = it }, Modifier.weight(1f))
                    }
                }

                // ── 버튼 (공통) ──
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        marketCapMin = ""; marketCapMax = ""; peMin = ""; peMax = ""
                        divYieldMin = ""; roeMin = ""; debtMax = ""
                        revGrowthMin = ""; targetUpsideMin = ""
                        pctFrom52hMin = ""; pctFrom52hMax = ""
                        insiderBuy3mMin = ""
                        shortPctFloatMin = ""; shortPctFloatMax = ""
                        selectedSectors = emptySet(); selectedRatings = emptySet()
                        expenseRatioMax = ""; aumMin = ""
                        selectedAssetClass = null
                        holdingSymbol = ""; holdingMinWeight = ""
                        onApply(FilterCriteria(assetType = criteria.assetType))
                    }) {
                        Text("초기화")
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        val applied = if (!isEtf) {
                            // Stock: Stock 전용 필드만 전달
                            FilterCriteria(
                                assetType = "stock",
                                marketCapMin = marketCapMin.toLongOrNull(),
                                marketCapMax = marketCapMax.toLongOrNull(),
                                peMin = peMin.toDoubleOrNull(),
                                peMax = peMax.toDoubleOrNull(),
                                dividendYieldMin = divYieldMin.toDoubleOrNull(),
                                roeMin = roeMin.toDoubleOrNull(),
                                debtToEquityMax = debtMax.toDoubleOrNull(),
                                revenueGrowthMin = revGrowthMin.toDoubleOrNull(),
                                targetUpsideMin = targetUpsideMin.toDoubleOrNull(),
                                pctFrom52hMin = pctFrom52hMin.toDoubleOrNull(),
                                pctFrom52hMax = pctFrom52hMax.toDoubleOrNull(),
                                insiderBuy3mMin = insiderBuy3mMin.toIntOrNull(),
                                shortPctFloatMin = shortPctFloatMin.toDoubleOrNull(),
                                shortPctFloatMax = shortPctFloatMax.toDoubleOrNull(),
                                sectors = selectedSectors.toList(),
                                analystRatings = selectedRatings.toList(),
                                orderBy = criteria.orderBy,
                                orderDesc = criteria.orderDesc,
                            )
                        } else {
                            // ETF: ETF 전용 필드만 전달
                            FilterCriteria(
                                assetType = "etf",
                                dividendYieldMin = divYieldMin.toDoubleOrNull(),
                                pctFrom52hMin = pctFrom52hMin.toDoubleOrNull(),
                                pctFrom52hMax = pctFrom52hMax.toDoubleOrNull(),
                                expenseRatioMax = expenseRatioMax.toDoubleOrNull(),
                                aumMin = aumMin.toLongOrNull(),
                                assetClass = selectedAssetClass,
                                holdingSymbol = holdingSymbol.ifBlank { null },
                                holdingMinWeight = holdingMinWeight.toDoubleOrNull(),
                                orderBy = criteria.orderBy,
                                orderDesc = criteria.orderDesc,
                            )
                        }
                        onApply(applied)
                        expanded = false
                    }) {
                        Text("검색")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun FilterSectionHeader(title: String) {
    Column {
        HorizontalDivider(thickness = 0.5.dp)
        Spacer(Modifier.height(4.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun FilterField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 10.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun FilterRangeRow(
    label: String,
    minValue: String,
    onMinChange: (String) -> Unit,
    maxValue: String,
    onMaxChange: (String) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterTextField("최소", minValue, onMinChange, Modifier.weight(1f))
            FilterTextField("최대", maxValue, onMaxChange, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FilterTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isDecimal: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 10.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isDecimal) KeyboardType.Decimal else KeyboardType.Text,
        ),
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodySmall,
    )
}
