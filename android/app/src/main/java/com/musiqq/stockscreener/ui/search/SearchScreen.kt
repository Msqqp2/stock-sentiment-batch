package com.musiqq.stockscreener.ui.search

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.musiqq.stockscreener.domain.model.Equity
import com.musiqq.stockscreener.ui.screener.EquityListItem
import com.musiqq.stockscreener.ui.theme.StockColors
import com.musiqq.stockscreener.ui.utils.NumberFormatter

@Composable
fun SearchScreen(
    onNavigateToDetail: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val defaultItems by viewModel.defaultItems.collectAsState()
    val isLoadingDefault by viewModel.isLoadingDefault.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Search input
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.onQueryChange(it) },
            placeholder = { Text("종목 검색 (AAPL, Apple...)", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clear() }) {
                        Icon(Icons.Default.Close, contentDescription = "지우기")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
        )

        if (query.length >= 2) {
            // 검색 모드
            if (results.isNotEmpty()) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(results, key = { it.symbol }) { equity ->
                        SearchResultItem(
                            equity = equity,
                            onClick = {
                                viewModel.clear()
                                onNavigateToDetail(equity.symbol)
                            },
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "검색 결과가 없습니다",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            // 기본 종목 리스트
            val listState = rememberLazyListState()
            val shouldLoadMore by remember {
                derivedStateOf {
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    lastVisible >= defaultItems.size - 10 && !isLoadingDefault
                }
            }
            LaunchedEffect(shouldLoadMore) {
                if (shouldLoadMore) viewModel.loadNextDefaultPage()
            }

            // 컬럼 헤더
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("종목", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp))
                Text("가격", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(70.dp))
                Text("등락", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp))
                Text("시총", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(55.dp))
                Text("거래량", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(50.dp))
                Text("점수", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
            }
            HorizontalDivider()

            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(defaultItems, key = { _, item -> item.symbol }) { index, equity ->
                    EquityListItem(
                        equity = equity,
                        onClick = { onNavigateToDetail(equity.symbol) },
                    )
                    if (index < defaultItems.lastIndex) {
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
                if (isLoadingDefault) {
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
}

@Composable
private fun SearchResultItem(
    equity: Equity,
    onClick: () -> Unit,
) {
    val stockColors = StockColors.current
    val changePct = equity.changePct
    val changeColor = when {
        changePct == null -> MaterialTheme.colorScheme.onSurfaceVariant
        changePct > 0 -> stockColors.up
        changePct < 0 -> stockColors.down
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Symbol + Name
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = equity.symbol,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = equity.assetType.uppercase(),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = equity.name,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Price + Change
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = NumberFormatter.formatPrice(equity.price),
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = NumberFormatter.formatChangePct(changePct),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = changeColor,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.width(12.dp))

        // Market Cap
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = NumberFormatter.formatMarketCap(equity.marketCap),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
            val score = equity.scoreTotal
            if (score != null) {
                Text(
                    text = "S:${NumberFormatter.formatScore(score)}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        score >= 80 -> stockColors.up
                        score >= 60 -> MaterialTheme.colorScheme.onSurface
                        else -> stockColors.down
                    },
                )
            }
        }
    }
}
