package com.musiqq.stockscreener.ui.utils

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.musiqq.stockscreener.ui.theme.Pretendard
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun FreshnessIndicator(dateString: String?, label: String = "", modifier: Modifier = Modifier) {
    if (dateString.isNullOrBlank()) {
        FreshnessBadge(text = label.ifEmpty { "N/A" }, color = Color.Gray, modifier = modifier)
        return
    }

    val date = try {
        LocalDate.parse(dateString, DateTimeFormatter.ISO_LOCAL_DATE)
    } catch (_: Exception) {
        null
    }

    if (date == null) {
        FreshnessBadge(text = label.ifEmpty { dateString }, color = Color.Gray, modifier = modifier)
        return
    }

    val daysOld = ChronoUnit.DAYS.between(date, LocalDate.now())
    val color = when {
        daysOld <= 1 -> Color(0xFF2EA043)  // green - fresh
        daysOld <= 3 -> Color(0xFFD29922)  // yellow - slightly stale
        daysOld <= 7 -> Color(0xFFE3B341)  // orange - stale
        else -> Color(0xFFF85149)           // red - very stale
    }
    val displayText = if (label.isNotEmpty()) "$label ${date.format(DateTimeFormatter.ofPattern("MM/dd"))}"
    else date.format(DateTimeFormatter.ofPattern("MM/dd"))

    FreshnessBadge(text = displayText, color = color, modifier = modifier)
}

@Composable
private fun FreshnessBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 9.sp,
            fontFamily = Pretendard,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
