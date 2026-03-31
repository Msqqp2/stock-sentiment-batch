package com.musiqq.stockscreener.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musiqq.stockscreener.ui.constants.COLUMN_MAP

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TooltipHeader(
    columnKey: String,
    modifier: Modifier = Modifier,
    onSort: (() -> Unit)? = null,
) {
    val colDef = COLUMN_MAP[columnKey]
    val korean = colDef?.korean ?: columnKey
    val english = colDef?.english ?: columnKey

    var showEnglish by remember { mutableStateOf(false) }

    Text(
        text = if (showEnglish) english else korean,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        maxLines = 1,
        modifier = modifier
            .padding(horizontal = 4.dp)
            .combinedClickable(
                onClick = { onSort?.invoke() },
                onLongClick = { showEnglish = !showEnglish },
            ),
    )
}
