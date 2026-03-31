package com.musiqq.stockscreener.ui.watchlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.musiqq.stockscreener.ui.screener.EquityListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    onNavigateToDetail: (String) -> Unit,
    viewModel: WatchlistViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadWatchlist() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("관심종목") })

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
            state.items.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "관심종목이 없습니다\n종목 상세에서 ★를 눌러 추가하세요",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(state.items, key = { _, item -> item.symbol }) { index, equity ->
                        val dismissState = rememberSwipeToDismissBoxState()

                        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                            LaunchedEffect(equity.symbol) {
                                viewModel.remove(equity.symbol)
                            }
                        }

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    Text("삭제", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            enableDismissFromStartToEnd = false,
                        ) {
                            EquityListItem(
                                equity = equity,
                                onClick = { onNavigateToDetail(equity.symbol) },
                            )
                        }

                        if (index < state.items.lastIndex) {
                            Divider(thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}
