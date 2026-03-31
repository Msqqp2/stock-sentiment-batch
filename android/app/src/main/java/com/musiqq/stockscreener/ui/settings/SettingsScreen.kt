package com.musiqq.stockscreener.ui.settings

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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.musiqq.stockscreener.data.local.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val weights by viewModel.scoreWeights.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        TopAppBar(title = { Text("설정") })

        // Theme
        Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("테마", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))

                ThemeMode.entries.forEach { mode ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RadioButton(
                            selected = themeMode == mode,
                            onClick = { viewModel.setTheme(mode) },
                        )
                        Text(
                            text = when (mode) {
                                ThemeMode.SYSTEM -> "시스템 설정"
                                ThemeMode.DARK -> "다크 모드"
                                ThemeMode.LIGHT -> "라이트 모드"
                            },
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }

        // Score Weights
        Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("스코어 가중치", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { viewModel.resetWeights() }) {
                        Text("초기화", fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))

                WeightSlider("밸류 (Value)", weights.value) { newVal ->
                    viewModel.setScoreWeights(weights.copy(value = newVal))
                }
                WeightSlider("퀄리티 (Quality)", weights.quality) { newVal ->
                    viewModel.setScoreWeights(weights.copy(quality = newVal))
                }
                WeightSlider("모멘텀 (Momentum)", weights.momentum) { newVal ->
                    viewModel.setScoreWeights(weights.copy(momentum = newVal))
                }
                WeightSlider("성장 (Growth)", weights.growth) { newVal ->
                    viewModel.setScoreWeights(weights.copy(growth = newVal))
                }

                Spacer(Modifier.height(4.dp))
                val total = weights.value + weights.quality + weights.momentum + weights.growth
                Text(
                    text = "합계: ${String.format("%.0f", total * 100)}%",
                    fontSize = 12.sp,
                    color = if (total in 0.99f..1.01f) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error,
                )
            }
        }

        // App Info
        Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("앱 정보", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("US Stock Screener", fontSize = 13.sp)
                Text("v1.0.0", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun WeightSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, fontSize = 12.sp)
            Text("${(value * 100).toInt()}%", fontSize = 12.sp)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            steps = 19,
        )
    }
}
