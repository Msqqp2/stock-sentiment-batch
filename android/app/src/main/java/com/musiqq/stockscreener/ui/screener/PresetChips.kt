package com.musiqq.stockscreener.ui.screener

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musiqq.stockscreener.domain.model.PRESET_SIGNALS
import com.musiqq.stockscreener.domain.model.PresetSignal

@Composable
fun PresetChips(
    activePreset: PresetSignal?,
    onSelect: (PresetSignal?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // "전체" reset chip
        FilterChip(
            selected = activePreset == null,
            onClick = { onSelect(null) },
            label = { Text("전체", fontSize = 12.sp) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )

        PRESET_SIGNALS.forEach { preset ->
            FilterChip(
                selected = activePreset?.id == preset.id,
                onClick = {
                    onSelect(if (activePreset?.id == preset.id) null else preset)
                },
                label = { Text(preset.label, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}
