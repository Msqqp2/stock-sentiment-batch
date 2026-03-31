package com.musiqq.stockscreener.ui.screener

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.musiqq.stockscreener.ui.components.TooltipHeader

@Composable
fun ScreenerScreen(
    onNavigateToDetail: (String) -> Unit,
    viewModel: ScreenerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex >= state.items.size - 10 && !state.isLoading && state.hasMore
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadNextPage()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            FilterChip(
                selected = state.criteria.assetType == "stock",
                onClick = { viewModel.switchAssetType("stock") },
                label = { Text("Stock", fontSize = 12.sp) },
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = state.criteria.assetType == "etf",
                onClick = { viewModel.switchAssetType("etf") },
                label = { Text("ETF", fontSize = 12.sp) },
            )
        }

        PresetChips(
            activePreset = state.activePreset,
            onSelect = { viewModel.applyPreset(it) },
        )

        FilterPanel(
            criteria = state.criteria,
            onApply = { viewModel.updateCriteria(it) },
        )

        // Column headers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("종목", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp))
            TooltipHeader("price", Modifier.width(70.dp)) { viewModel.toggleSort("price") }
            TooltipHeader("change_pct", Modifier.width(60.dp)) { viewModel.toggleSort("change_pct") }
            TooltipHeader("market_cap", Modifier.width(55.dp)) { viewModel.toggleSort("market_cap") }
            TooltipHeader("volume", Modifier.width(50.dp)) { viewModel.toggleSort("volume") }
            TooltipHeader("score_total", Modifier.width(30.dp)) { viewModel.toggleSort("score_total") }
        }
        HorizontalDivider()

        state.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(state.items, key = { _, item -> item.symbol }) { index, equity ->
                EquityListItem(
                    equity = equity,
                    onClick = { onNavigateToDetail(equity.symbol) },
                )
                if (index < state.items.lastIndex) {
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
            if (state.isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}
