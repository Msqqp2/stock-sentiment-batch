package com.musiqq.stockscreener.ui.heatmap

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HeatMapScreen(
    onNavigateToDetail: (String) -> Unit,
    viewModel: HeatMapViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
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
            else -> {
                // 기준일 표시
                state.dataDate?.let { dateStr ->
                    val formatted = dateStr.replace("-", ".")
                    Text(
                        text = "${formatted} 기준",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp),
                    )
                }

                val jsonData = viewModel.toJsonForWebView()

                val mainHandler = Handler(Looper.getMainLooper())

                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = WebViewClient()

                            addJavascriptInterface(
                                object {
                                    @JavascriptInterface
                                    fun onSymbolClick(symbol: String) {
                                        mainHandler.post { onNavigateToDetail(symbol) }
                                    }

                                    @JavascriptInterface
                                    fun getData(): String = jsonData
                                },
                                "Android",
                            )

                            loadUrl("file:///android_asset/heatmap.html")
                        }
                    },
                    onRelease = { webView -> webView.destroy() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
