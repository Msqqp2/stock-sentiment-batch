package com.musiqq.stockscreener.ui.screener

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    "strong_buy" to "Strong Buy", "buy" to "Buy", "hold" to "Hold",
    "sell" to "Sell", "strong_sell" to "Strong Sell",
)

private val ETF_ASSET_CLASSES = listOf("Equity", "Fixed Income", "Commodities", "Real Estate", "Currency")

private val EXCHANGES = listOf("NYSE", "NASDAQ", "AMEX")

private val MCAP_PRESETS = listOf(
    "Nano <50M" to (null to 50L),
    "Micro 50~300M" to (50L to 300L),
    "Small 300M~2B" to (300L to 2000L),
    "Mid 2~10B" to (2000L to 10000L),
    "Large 10~200B" to (10000L to 200000L),
    "Mega >200B" to (200000L to null),
)

private val FILTER_TABS = listOf("기본", "밸류에이션", "수익성", "성장", "재무", "배당", "기술적", "퍼포먼스", "공매도", "애널리스트", "점수")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterPanel(
    criteria: FilterCriteria,
    onApply: (FilterCriteria) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }

    // State holders for all filters (using string state)
    var state by remember(criteria) { mutableStateOf(FilterState.from(criteria)) }

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
            Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                // Category tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 0.dp,
                ) {
                    FILTER_TABS.forEachIndexed { i, title ->
                        Tab(
                            selected = selectedTab == i,
                            onClick = { selectedTab = i },
                            text = { Text(title, fontSize = 11.sp) },
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                        .verticalScroll(rememberScrollState())
                        .height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (selectedTab) {
                        0 -> { // 기본 정보
                            if (!isEtf) {
                                FilterSectionHeader("거래소")
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    EXCHANGES.forEach { ex ->
                                        FilterChip(
                                            selected = state.exchange == ex,
                                            onClick = { state = state.copy(exchange = if (state.exchange == ex) "" else ex) },
                                            label = { Text(ex, fontSize = 10.sp) },
                                        )
                                    }
                                }
                                FilterSectionHeader("섹터")
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    SECTORS.forEach { sec ->
                                        FilterChip(
                                            selected = sec in state.selectedSectors,
                                            onClick = { state = state.copy(selectedSectors = if (sec in state.selectedSectors) state.selectedSectors - sec else state.selectedSectors + sec) },
                                            label = { Text(sec, fontSize = 10.sp) },
                                        )
                                    }
                                }
                            } else {
                                FilterSectionHeader("자산 유형")
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ETF_ASSET_CLASSES.forEach { cls ->
                                        FilterChip(
                                            selected = state.assetClass == cls,
                                            onClick = { state = state.copy(assetClass = if (state.assetClass == cls) "" else cls) },
                                            label = { Text(cls, fontSize = 10.sp) },
                                        )
                                    }
                                }
                                FilterField("AUM 최소 (USD)", state.aumMin) { state = state.copy(aumMin = it) }
                                FilterField("보수율 최대 (%)", state.expenseRatioMax) { state = state.copy(expenseRatioMax = it) }
                            }
                            FilterSectionHeader("시가총액 (M)")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                MCAP_PRESETS.forEach { (label, range) ->
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            state = state.copy(
                                                marketCapMin = range.first?.toString() ?: "",
                                                marketCapMax = range.second?.toString() ?: "",
                                            )
                                        },
                                        label = { Text(label, fontSize = 9.sp) },
                                    )
                                }
                            }
                            FilterRangeRow("시가총액 (M)", state.marketCapMin, { state = state.copy(marketCapMin = it) }, state.marketCapMax, { state = state.copy(marketCapMax = it) })
                            FilterRangeRow("주가", state.priceMin, { state = state.copy(priceMin = it) }, state.priceMax, { state = state.copy(priceMax = it) })
                        }
                        1 -> { // 밸류에이션
                            FilterRangeRow("P/E (TTM)", state.peMin, { state = state.copy(peMin = it) }, state.peMax, { state = state.copy(peMax = it) })
                            FilterRangeRow("Forward P/E", state.peForwardMin, { state = state.copy(peForwardMin = it) }, state.peForwardMax, { state = state.copy(peForwardMax = it) })
                            FilterRangeRow("PEG", state.pegMin, { state = state.copy(pegMin = it) }, state.pegMax, { state = state.copy(pegMax = it) })
                            FilterRangeRow("P/B", state.pbMin, { state = state.copy(pbMin = it) }, state.pbMax, { state = state.copy(pbMax = it) })
                            FilterRangeRow("P/S", state.psMin, { state = state.copy(psMin = it) }, state.psMax, { state = state.copy(psMax = it) })
                            FilterRangeRow("P/FCF", state.pfcfMin, { state = state.copy(pfcfMin = it) }, state.pfcfMax, { state = state.copy(pfcfMax = it) })
                            FilterRangeRow("EV/EBITDA", state.evEbitdaMin, { state = state.copy(evEbitdaMin = it) }, state.evEbitdaMax, { state = state.copy(evEbitdaMax = it) })
                            FilterRangeRow("EV/Revenue", state.evRevenueMin, { state = state.copy(evRevenueMin = it) }, state.evRevenueMax, { state = state.copy(evRevenueMax = it) })
                            FilterRangeRow("DCF 괴리율(%)", state.dcfUpsideMin, { state = state.copy(dcfUpsideMin = it) }, state.dcfUpsideMax, { state = state.copy(dcfUpsideMax = it) })
                            FilterRangeRow("그레이엄 괴리율(%)", state.grahamUpsideMin, { state = state.copy(grahamUpsideMin = it) }, state.grahamUpsideMax, { state = state.copy(grahamUpsideMax = it) })
                        }
                        2 -> { // 수익성
                            FilterRangeRow("ROE(%)", state.roeMin, { state = state.copy(roeMin = it) }, state.roeMax, { state = state.copy(roeMax = it) })
                            FilterRangeRow("ROA(%)", state.roaMin, { state = state.copy(roaMin = it) }, state.roaMax, { state = state.copy(roaMax = it) })
                            FilterRangeRow("ROIC(%)", state.roicMin, { state = state.copy(roicMin = it) }, state.roicMax, { state = state.copy(roicMax = it) })
                            FilterRangeRow("영업이익률(%)", state.operatingMarginMin, { state = state.copy(operatingMarginMin = it) }, state.operatingMarginMax, { state = state.copy(operatingMarginMax = it) })
                            FilterRangeRow("순이익률(%)", state.profitMarginMin, { state = state.copy(profitMarginMin = it) }, state.profitMarginMax, { state = state.copy(profitMarginMax = it) })
                            FilterRangeRow("매출총이익률(%)", state.grossMarginMin, { state = state.copy(grossMarginMin = it) }, state.grossMarginMax, { state = state.copy(grossMarginMax = it) })
                            FilterRangeRow("Piotroski F-Score", state.piotroskiMin, { state = state.copy(piotroskiMin = it) }, state.piotroskiMax, { state = state.copy(piotroskiMax = it) })
                            FilterRangeRow("Altman Z-Score", state.altmanZMin, { state = state.copy(altmanZMin = it) }, state.altmanZMax, { state = state.copy(altmanZMax = it) })
                        }
                        3 -> { // 성장
                            FilterRangeRow("매출 성장률(%)", state.revenueGrowthMin, { state = state.copy(revenueGrowthMin = it) }, state.revenueGrowthMax, { state = state.copy(revenueGrowthMax = it) })
                            FilterRangeRow("이익 성장률(%)", state.earningsGrowthMin, { state = state.copy(earningsGrowthMin = it) }, state.earningsGrowthMax, { state = state.copy(earningsGrowthMax = it) })
                            FilterRangeRow("EPS 성장률 올해(%)", state.epsGrowthThisYrMin, { state = state.copy(epsGrowthThisYrMin = it) }, state.epsGrowthThisYrMax, { state = state.copy(epsGrowthThisYrMax = it) })
                            FilterRangeRow("EPS 성장률 5년(%)", state.epsGrowthPast5yMin, { state = state.copy(epsGrowthPast5yMin = it) }, state.epsGrowthPast5yMax, { state = state.copy(epsGrowthPast5yMax = it) })
                            FilterRangeRow("영업이익 성장률(%)", state.opIncomeGrowthMin, { state = state.copy(opIncomeGrowthMin = it) }, state.opIncomeGrowthMax, { state = state.copy(opIncomeGrowthMax = it) })
                        }
                        4 -> { // 재무 건전성
                            FilterRangeRow("부채비율", state.debtToEquityMin, { state = state.copy(debtToEquityMin = it) }, state.debtToEquityMax, { state = state.copy(debtToEquityMax = it) })
                            FilterRangeRow("장기부채비율", state.ltDebtEquityMin, { state = state.copy(ltDebtEquityMin = it) }, state.ltDebtEquityMax, { state = state.copy(ltDebtEquityMax = it) })
                            FilterRangeRow("유동비율", state.currentRatioMin, { state = state.copy(currentRatioMin = it) }, state.currentRatioMax, { state = state.copy(currentRatioMax = it) })
                            FilterRangeRow("당좌비율", state.quickRatioMin, { state = state.copy(quickRatioMin = it) }, state.quickRatioMax, { state = state.copy(quickRatioMax = it) })
                            FilterRangeRow("이자보상배율", state.interestCoverageMin, { state = state.copy(interestCoverageMin = it) }, state.interestCoverageMax, { state = state.copy(interestCoverageMax = it) })
                            FilterRangeRow("부채증가율(%)", state.debtGrowthMin, { state = state.copy(debtGrowthMin = it) }, state.debtGrowthMax, { state = state.copy(debtGrowthMax = it) })
                        }
                        5 -> { // 배당
                            FilterRangeRow("배당수익률(%)", state.dividendYieldMin, { state = state.copy(dividendYieldMin = it) }, state.dividendYieldMax, { state = state.copy(dividendYieldMax = it) })
                            FilterRangeRow("배당성향(%)", state.payoutRatioMin, { state = state.copy(payoutRatioMin = it) }, state.payoutRatioMax, { state = state.copy(payoutRatioMax = it) })
                            FilterRangeRow("5년평균배당률(%)", state.avgDividendYield5yMin, { state = state.copy(avgDividendYield5yMin = it) }, state.avgDividendYield5yMax, { state = state.copy(avgDividendYield5yMax = it) })
                            FilterRangeRow("주주환원수익률(%)", state.shareholderYieldMin, { state = state.copy(shareholderYieldMin = it) }, state.shareholderYieldMax, { state = state.copy(shareholderYieldMax = it) })
                            FilterRangeRow("자사주매입수익률(%)", state.buybackYieldMin, { state = state.copy(buybackYieldMin = it) }, state.buybackYieldMax, { state = state.copy(buybackYieldMax = it) })
                        }
                        6 -> { // 기술적 지표
                            FilterRangeRow("RSI(14)", state.rsiMin, { state = state.copy(rsiMin = it) }, state.rsiMax, { state = state.copy(rsiMax = it) })
                            FilterRangeRow("20일선 대비(%)", state.sma20PctMin, { state = state.copy(sma20PctMin = it) }, state.sma20PctMax, { state = state.copy(sma20PctMax = it) })
                            FilterRangeRow("50일선 대비(%)", state.sma50PctMin, { state = state.copy(sma50PctMin = it) }, state.sma50PctMax, { state = state.copy(sma50PctMax = it) })
                            FilterRangeRow("200일선 대비(%)", state.sma200PctMin, { state = state.copy(sma200PctMin = it) }, state.sma200PctMax, { state = state.copy(sma200PctMax = it) })
                            FilterRangeRow("52주 고점 대비(%)", state.pctFrom52hMin, { state = state.copy(pctFrom52hMin = it) }, state.pctFrom52hMax, { state = state.copy(pctFrom52hMax = it) })
                            FilterRangeRow("52주 저점 대비(%)", state.pctFrom52lMin, { state = state.copy(pctFrom52lMin = it) }, state.pctFrom52lMax, { state = state.copy(pctFrom52lMax = it) })
                            FilterRangeRow("베타", state.betaMin, { state = state.copy(betaMin = it) }, state.betaMax, { state = state.copy(betaMax = it) })
                            FilterRangeRow("주간 변동성(%)", state.volatilityWMin, { state = state.copy(volatilityWMin = it) }, state.volatilityWMax, { state = state.copy(volatilityWMax = it) })
                        }
                        7 -> { // 퍼포먼스
                            FilterRangeRow("등락률(당일)(%)", state.changePctMin, { state = state.copy(changePctMin = it) }, state.changePctMax, { state = state.copy(changePctMax = it) })
                            FilterRangeRow("1주 수익률(%)", state.perf1wMin, { state = state.copy(perf1wMin = it) }, state.perf1wMax, { state = state.copy(perf1wMax = it) })
                            FilterRangeRow("1개월 수익률(%)", state.perf1mMin, { state = state.copy(perf1mMin = it) }, state.perf1mMax, { state = state.copy(perf1mMax = it) })
                            FilterRangeRow("3개월 수익률(%)", state.perf3mMin, { state = state.copy(perf3mMin = it) }, state.perf3mMax, { state = state.copy(perf3mMax = it) })
                            FilterRangeRow("6개월 수익률(%)", state.perf6mMin, { state = state.copy(perf6mMin = it) }, state.perf6mMax, { state = state.copy(perf6mMax = it) })
                            FilterRangeRow("1년 수익률(%)", state.perf1yMin, { state = state.copy(perf1yMin = it) }, state.perf1yMax, { state = state.copy(perf1yMax = it) })
                            FilterRangeRow("YTD 수익률(%)", state.perfYtdMin, { state = state.copy(perfYtdMin = it) }, state.perfYtdMax, { state = state.copy(perfYtdMax = it) })
                        }
                        8 -> { // 공매도/기관
                            FilterRangeRow("공매도 비율(%)", state.shortPctFloatMin, { state = state.copy(shortPctFloatMin = it) }, state.shortPctFloatMax, { state = state.copy(shortPctFloatMax = it) })
                            FilterRangeRow("Short Ratio(일)", state.shortRatioMin, { state = state.copy(shortRatioMin = it) }, state.shortRatioMax, { state = state.copy(shortRatioMax = it) })
                            FilterRangeRow("기관보유비율(%)", state.instPctMin, { state = state.copy(instPctMin = it) }, state.instPctMax, { state = state.copy(instPctMax = it) })
                            FilterRangeRow("내부자보유비율(%)", state.insiderPctMin, { state = state.copy(insiderPctMin = it) }, state.insiderPctMax, { state = state.copy(insiderPctMax = it) })
                        }
                        9 -> { // 애널리스트
                            Text("투자의견", style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                ANALYST_RATINGS.forEach { rating ->
                                    FilterChip(
                                        selected = rating in state.selectedRatings,
                                        onClick = { state = state.copy(selectedRatings = if (rating in state.selectedRatings) state.selectedRatings - rating else state.selectedRatings + rating) },
                                        label = { Text(ANALYST_RATING_LABELS[rating] ?: rating, fontSize = 10.sp) },
                                    )
                                }
                            }
                            FilterRangeRow("목표가 괴리율(%)", state.targetUpsideMin, { state = state.copy(targetUpsideMin = it) }, state.targetUpsideMax, { state = state.copy(targetUpsideMax = it) })
                            FilterRangeRow("애널리스트 수", state.analystCountMin, { state = state.copy(analystCountMin = it) }, state.analystCountMax, { state = state.copy(analystCountMax = it) })
                        }
                        10 -> { // 종합 점수
                            FilterRangeRow("종합 점수", state.scoreTotalMin, { state = state.copy(scoreTotalMin = it) }, state.scoreTotalMax, { state = state.copy(scoreTotalMax = it) })
                            FilterRangeRow("Value 점수", state.scoreValueMin, { state = state.copy(scoreValueMin = it) }, state.scoreValueMax, { state = state.copy(scoreValueMax = it) })
                            FilterRangeRow("Quality 점수", state.scoreQualityMin, { state = state.copy(scoreQualityMin = it) }, state.scoreQualityMax, { state = state.copy(scoreQualityMax = it) })
                            FilterRangeRow("Momentum 점수", state.scoreMomentumMin, { state = state.copy(scoreMomentumMin = it) }, state.scoreMomentumMax, { state = state.copy(scoreMomentumMax = it) })
                            FilterRangeRow("Growth 점수", state.scoreGrowthMin, { state = state.copy(scoreGrowthMin = it) }, state.scoreGrowthMax, { state = state.copy(scoreGrowthMax = it) })
                        }
                    }
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = {
                        state = FilterState()
                        onApply(FilterCriteria(assetType = criteria.assetType))
                    }) {
                        Text("초기화")
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        onApply(state.toCriteria(criteria.assetType, criteria.orderBy, criteria.orderDesc))
                        expanded = false
                    }) {
                        Text("검색")
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/**
 * All filter field states as strings for text input.
 */
private data class FilterState(
    // 기본
    val exchange: String = "",
    val selectedSectors: Set<String> = emptySet(),
    val marketCapMin: String = "", val marketCapMax: String = "",
    val priceMin: String = "", val priceMax: String = "",
    // 밸류에이션
    val peMin: String = "", val peMax: String = "",
    val peForwardMin: String = "", val peForwardMax: String = "",
    val pegMin: String = "", val pegMax: String = "",
    val pbMin: String = "", val pbMax: String = "",
    val psMin: String = "", val psMax: String = "",
    val pfcfMin: String = "", val pfcfMax: String = "",
    val evEbitdaMin: String = "", val evEbitdaMax: String = "",
    val evRevenueMin: String = "", val evRevenueMax: String = "",
    val dcfUpsideMin: String = "", val dcfUpsideMax: String = "",
    val grahamUpsideMin: String = "", val grahamUpsideMax: String = "",
    // 수익성
    val roeMin: String = "", val roeMax: String = "",
    val roaMin: String = "", val roaMax: String = "",
    val roicMin: String = "", val roicMax: String = "",
    val operatingMarginMin: String = "", val operatingMarginMax: String = "",
    val profitMarginMin: String = "", val profitMarginMax: String = "",
    val grossMarginMin: String = "", val grossMarginMax: String = "",
    val piotroskiMin: String = "", val piotroskiMax: String = "",
    val altmanZMin: String = "", val altmanZMax: String = "",
    // 성장
    val revenueGrowthMin: String = "", val revenueGrowthMax: String = "",
    val earningsGrowthMin: String = "", val earningsGrowthMax: String = "",
    val epsGrowthThisYrMin: String = "", val epsGrowthThisYrMax: String = "",
    val epsGrowthPast5yMin: String = "", val epsGrowthPast5yMax: String = "",
    val opIncomeGrowthMin: String = "", val opIncomeGrowthMax: String = "",
    // 재무 건전성
    val debtToEquityMin: String = "", val debtToEquityMax: String = "",
    val ltDebtEquityMin: String = "", val ltDebtEquityMax: String = "",
    val currentRatioMin: String = "", val currentRatioMax: String = "",
    val quickRatioMin: String = "", val quickRatioMax: String = "",
    val interestCoverageMin: String = "", val interestCoverageMax: String = "",
    val debtGrowthMin: String = "", val debtGrowthMax: String = "",
    // 배당
    val dividendYieldMin: String = "", val dividendYieldMax: String = "",
    val payoutRatioMin: String = "", val payoutRatioMax: String = "",
    val avgDividendYield5yMin: String = "", val avgDividendYield5yMax: String = "",
    val shareholderYieldMin: String = "", val shareholderYieldMax: String = "",
    val buybackYieldMin: String = "", val buybackYieldMax: String = "",
    // 기술적
    val rsiMin: String = "", val rsiMax: String = "",
    val sma20PctMin: String = "", val sma20PctMax: String = "",
    val sma50PctMin: String = "", val sma50PctMax: String = "",
    val sma200PctMin: String = "", val sma200PctMax: String = "",
    val pctFrom52hMin: String = "", val pctFrom52hMax: String = "",
    val pctFrom52lMin: String = "", val pctFrom52lMax: String = "",
    val betaMin: String = "", val betaMax: String = "",
    val volatilityWMin: String = "", val volatilityWMax: String = "",
    // 퍼포먼스
    val changePctMin: String = "", val changePctMax: String = "",
    val perf1wMin: String = "", val perf1wMax: String = "",
    val perf1mMin: String = "", val perf1mMax: String = "",
    val perf3mMin: String = "", val perf3mMax: String = "",
    val perf6mMin: String = "", val perf6mMax: String = "",
    val perf1yMin: String = "", val perf1yMax: String = "",
    val perfYtdMin: String = "", val perfYtdMax: String = "",
    // 공매도/기관
    val shortPctFloatMin: String = "", val shortPctFloatMax: String = "",
    val shortRatioMin: String = "", val shortRatioMax: String = "",
    val instPctMin: String = "", val instPctMax: String = "",
    val insiderPctMin: String = "", val insiderPctMax: String = "",
    // 애널리스트
    val selectedRatings: Set<String> = emptySet(),
    val targetUpsideMin: String = "", val targetUpsideMax: String = "",
    val analystCountMin: String = "", val analystCountMax: String = "",
    // 점수
    val scoreTotalMin: String = "", val scoreTotalMax: String = "",
    val scoreValueMin: String = "", val scoreValueMax: String = "",
    val scoreQualityMin: String = "", val scoreQualityMax: String = "",
    val scoreMomentumMin: String = "", val scoreMomentumMax: String = "",
    val scoreGrowthMin: String = "", val scoreGrowthMax: String = "",
    // ETF
    val expenseRatioMax: String = "",
    val aumMin: String = "",
    val assetClass: String = "",
) {
    fun toCriteria(assetType: String, orderBy: String, orderDesc: Boolean) = FilterCriteria(
        assetType = assetType,
        exchange = exchange.ifBlank { null },
        sectors = selectedSectors.toList(),
        marketCapMin = marketCapMin.toLongOrNull()?.times(1_000_000),
        marketCapMax = marketCapMax.toLongOrNull()?.times(1_000_000),
        priceMin = priceMin.toDoubleOrNull(),
        priceMax = priceMax.toDoubleOrNull(),
        peMin = peMin.toDoubleOrNull(), peMax = peMax.toDoubleOrNull(),
        peForwardMin = peForwardMin.toDoubleOrNull(), peForwardMax = peForwardMax.toDoubleOrNull(),
        pegMin = pegMin.toDoubleOrNull(), pegMax = pegMax.toDoubleOrNull(),
        pbMin = pbMin.toDoubleOrNull(), pbMax = pbMax.toDoubleOrNull(),
        psMin = psMin.toDoubleOrNull(), psMax = psMax.toDoubleOrNull(),
        pfcfMin = pfcfMin.toDoubleOrNull(), pfcfMax = pfcfMax.toDoubleOrNull(),
        evEbitdaMin = evEbitdaMin.toDoubleOrNull(), evEbitdaMax = evEbitdaMax.toDoubleOrNull(),
        evRevenueMin = evRevenueMin.toDoubleOrNull(), evRevenueMax = evRevenueMax.toDoubleOrNull(),
        dcfUpsideMin = dcfUpsideMin.toDoubleOrNull(), dcfUpsideMax = dcfUpsideMax.toDoubleOrNull(),
        grahamUpsideMin = grahamUpsideMin.toDoubleOrNull(), grahamUpsideMax = grahamUpsideMax.toDoubleOrNull(),
        roeMin = roeMin.toDoubleOrNull(), roeMax = roeMax.toDoubleOrNull(),
        roaMin = roaMin.toDoubleOrNull(), roaMax = roaMax.toDoubleOrNull(),
        roicMin = roicMin.toDoubleOrNull(), roicMax = roicMax.toDoubleOrNull(),
        operatingMarginMin = operatingMarginMin.toDoubleOrNull(), operatingMarginMax = operatingMarginMax.toDoubleOrNull(),
        profitMarginMin = profitMarginMin.toDoubleOrNull(), profitMarginMax = profitMarginMax.toDoubleOrNull(),
        grossMarginMin = grossMarginMin.toDoubleOrNull(), grossMarginMax = grossMarginMax.toDoubleOrNull(),
        piotroskiMin = piotroskiMin.toIntOrNull(), piotroskiMax = piotroskiMax.toIntOrNull(),
        altmanZMin = altmanZMin.toDoubleOrNull(), altmanZMax = altmanZMax.toDoubleOrNull(),
        revenueGrowthMin = revenueGrowthMin.toDoubleOrNull(), revenueGrowthMax = revenueGrowthMax.toDoubleOrNull(),
        earningsGrowthMin = earningsGrowthMin.toDoubleOrNull(), earningsGrowthMax = earningsGrowthMax.toDoubleOrNull(),
        epsGrowthThisYrMin = epsGrowthThisYrMin.toDoubleOrNull(), epsGrowthThisYrMax = epsGrowthThisYrMax.toDoubleOrNull(),
        epsGrowthPast5yMin = epsGrowthPast5yMin.toDoubleOrNull(), epsGrowthPast5yMax = epsGrowthPast5yMax.toDoubleOrNull(),
        opIncomeGrowthMin = opIncomeGrowthMin.toDoubleOrNull(), opIncomeGrowthMax = opIncomeGrowthMax.toDoubleOrNull(),
        debtToEquityMin = debtToEquityMin.toDoubleOrNull(), debtToEquityMax = debtToEquityMax.toDoubleOrNull(),
        ltDebtEquityMin = ltDebtEquityMin.toDoubleOrNull(), ltDebtEquityMax = ltDebtEquityMax.toDoubleOrNull(),
        currentRatioMin = currentRatioMin.toDoubleOrNull(), currentRatioMax = currentRatioMax.toDoubleOrNull(),
        quickRatioMin = quickRatioMin.toDoubleOrNull(), quickRatioMax = quickRatioMax.toDoubleOrNull(),
        interestCoverageMin = interestCoverageMin.toDoubleOrNull(), interestCoverageMax = interestCoverageMax.toDoubleOrNull(),
        debtGrowthMin = debtGrowthMin.toDoubleOrNull(), debtGrowthMax = debtGrowthMax.toDoubleOrNull(),
        dividendYieldMin = dividendYieldMin.toDoubleOrNull(), dividendYieldMax = dividendYieldMax.toDoubleOrNull(),
        payoutRatioMin = payoutRatioMin.toDoubleOrNull(), payoutRatioMax = payoutRatioMax.toDoubleOrNull(),
        avgDividendYield5yMin = avgDividendYield5yMin.toDoubleOrNull(), avgDividendYield5yMax = avgDividendYield5yMax.toDoubleOrNull(),
        shareholderYieldMin = shareholderYieldMin.toDoubleOrNull(), shareholderYieldMax = shareholderYieldMax.toDoubleOrNull(),
        buybackYieldMin = buybackYieldMin.toDoubleOrNull(), buybackYieldMax = buybackYieldMax.toDoubleOrNull(),
        rsiMin = rsiMin.toDoubleOrNull(), rsiMax = rsiMax.toDoubleOrNull(),
        sma20PctMin = sma20PctMin.toDoubleOrNull(), sma20PctMax = sma20PctMax.toDoubleOrNull(),
        sma50PctMin = sma50PctMin.toDoubleOrNull(), sma50PctMax = sma50PctMax.toDoubleOrNull(),
        sma200PctMin = sma200PctMin.toDoubleOrNull(), sma200PctMax = sma200PctMax.toDoubleOrNull(),
        pctFrom52hMin = pctFrom52hMin.toDoubleOrNull(), pctFrom52hMax = pctFrom52hMax.toDoubleOrNull(),
        pctFrom52lMin = pctFrom52lMin.toDoubleOrNull(), pctFrom52lMax = pctFrom52lMax.toDoubleOrNull(),
        betaMin = betaMin.toDoubleOrNull(), betaMax = betaMax.toDoubleOrNull(),
        volatilityWMin = volatilityWMin.toDoubleOrNull(), volatilityWMax = volatilityWMax.toDoubleOrNull(),
        changePctMin = changePctMin.toDoubleOrNull(), changePctMax = changePctMax.toDoubleOrNull(),
        perf1wMin = perf1wMin.toDoubleOrNull(), perf1wMax = perf1wMax.toDoubleOrNull(),
        perf1mMin = perf1mMin.toDoubleOrNull(), perf1mMax = perf1mMax.toDoubleOrNull(),
        perf3mMin = perf3mMin.toDoubleOrNull(), perf3mMax = perf3mMax.toDoubleOrNull(),
        perf6mMin = perf6mMin.toDoubleOrNull(), perf6mMax = perf6mMax.toDoubleOrNull(),
        perf1yMin = perf1yMin.toDoubleOrNull(), perf1yMax = perf1yMax.toDoubleOrNull(),
        perfYtdMin = perfYtdMin.toDoubleOrNull(), perfYtdMax = perfYtdMax.toDoubleOrNull(),
        shortPctFloatMin = shortPctFloatMin.toDoubleOrNull(), shortPctFloatMax = shortPctFloatMax.toDoubleOrNull(),
        shortRatioMin = shortRatioMin.toDoubleOrNull(), shortRatioMax = shortRatioMax.toDoubleOrNull(),
        instPctMin = instPctMin.toDoubleOrNull(), instPctMax = instPctMax.toDoubleOrNull(),
        insiderPctMin = insiderPctMin.toDoubleOrNull(), insiderPctMax = insiderPctMax.toDoubleOrNull(),
        analystRatings = selectedRatings.toList(),
        targetUpsideMin = targetUpsideMin.toDoubleOrNull(), targetUpsideMax = targetUpsideMax.toDoubleOrNull(),
        analystCountMin = analystCountMin.toIntOrNull(), analystCountMax = analystCountMax.toIntOrNull(),
        scoreTotalMin = scoreTotalMin.toIntOrNull(), scoreTotalMax = scoreTotalMax.toIntOrNull(),
        scoreValueMin = scoreValueMin.toIntOrNull(), scoreValueMax = scoreValueMax.toIntOrNull(),
        scoreQualityMin = scoreQualityMin.toIntOrNull(), scoreQualityMax = scoreQualityMax.toIntOrNull(),
        scoreMomentumMin = scoreMomentumMin.toIntOrNull(), scoreMomentumMax = scoreMomentumMax.toIntOrNull(),
        scoreGrowthMin = scoreGrowthMin.toIntOrNull(), scoreGrowthMax = scoreGrowthMax.toIntOrNull(),
        expenseRatioMax = expenseRatioMax.toDoubleOrNull(),
        aumMin = aumMin.toLongOrNull(),
        assetClass = assetClass.ifBlank { null },
        orderBy = orderBy,
        orderDesc = orderDesc,
    )

    companion object {
        fun from(c: FilterCriteria) = FilterState(
            selectedSectors = c.sectors.toSet(),
            marketCapMin = c.marketCapMin?.div(1_000_000)?.toString() ?: "",
            marketCapMax = c.marketCapMax?.div(1_000_000)?.toString() ?: "",
            peMin = c.peMin?.toString() ?: "", peMax = c.peMax?.toString() ?: "",
            roeMin = c.roeMin?.toString() ?: "",
            dividendYieldMin = c.dividendYieldMin?.toString() ?: "",
            pctFrom52hMin = c.pctFrom52hMin?.toString() ?: "",
            pctFrom52hMax = c.pctFrom52hMax?.toString() ?: "",
            shortPctFloatMin = c.shortPctFloatMin?.toString() ?: "",
            shortPctFloatMax = c.shortPctFloatMax?.toString() ?: "",
            selectedRatings = c.analystRatings.toSet(),
            targetUpsideMin = c.targetUpsideMin?.toString() ?: "",
        )
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
            fontSize = 12.sp,
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
        Text(label, style = MaterialTheme.typography.labelMedium, fontSize = 11.sp)
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
