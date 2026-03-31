package com.musiqq.stockscreener.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.musiqq.stockscreener.ui.theme.Pretendard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.musiqq.stockscreener.ui.detail.tabs.AnalystTab
import com.musiqq.stockscreener.ui.detail.tabs.FinancialTab
import com.musiqq.stockscreener.ui.detail.tabs.OverviewTab
import com.musiqq.stockscreener.ui.detail.tabs.SupplyDemandTab
import com.musiqq.stockscreener.ui.theme.StockColors
import com.musiqq.stockscreener.ui.utils.NumberFormatter
import kotlinx.coroutines.launch

private val TABS = listOf("개요", "재무", "수급", "애널리스트")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    symbol: String,
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val stockColors = StockColors.current
    val pagerState = rememberPagerState(pageCount = { TABS.size })
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                val equity = state.equity
                if (equity != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(equity.symbol, fontWeight = FontWeight.Bold, fontFamily = Pretendard)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = NumberFormatter.formatPrice(equity.price),
                            fontSize = 16.sp,
                            fontFamily = Pretendard,
                        )
                        // 날짜 표시 (yy.MM.dd 종가)
                        equity.dataDate?.let { date ->
                            val short = try {
                                val parts = date.split("-")
                                "${parts[0].takeLast(2)}.${parts[1]}.${parts[2]}"
                            } catch (_: Exception) { date }
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "($short 종가)",
                                fontSize = 9.sp,
                                fontFamily = Pretendard,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        val changePct = equity.changePct
                        val color = when {
                            changePct == null -> MaterialTheme.colorScheme.onSurface
                            changePct > 0 -> stockColors.up
                            changePct < 0 -> stockColors.down
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Text(
                            text = NumberFormatter.formatChangePct(changePct),
                            fontSize = 14.sp,
                            fontFamily = Pretendard,
                            color = color,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                } else {
                    Text(symbol)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                }
            },
            actions = {
                IconButton(onClick = { viewModel.toggleWatchlist() }) {
                    Icon(
                        imageVector = if (state.isWatchlisted) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = "관심종목",
                        tint = if (state.isWatchlisted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )

        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                }
            }
            state.equity != null -> {
                TabRow(selectedTabIndex = pagerState.currentPage) {
                    TABS.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(title, fontSize = 13.sp) },
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val equity = state.equity!!
                    when (page) {
                        0 -> OverviewTab(equity)
                        1 -> FinancialTab(equity)
                        2 -> SupplyDemandTab(equity, state.insiderTrades)
                        3 -> AnalystTab(equity)
                    }
                }
            }
        }
    }
}
