package com.musiqq.stockscreener.ui.screener

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musiqq.stockscreener.domain.model.Equity
import com.musiqq.stockscreener.ui.theme.StockColors
import com.musiqq.stockscreener.ui.utils.NumberFormatter

@Composable
fun EquityListItem(
    equity: Equity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stockColors = StockColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Symbol + Name
        Column(
            modifier = Modifier.width(100.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = equity.symbol,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = equity.name,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Price
        Text(
            text = NumberFormatter.formatPrice(equity.price),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(70.dp),
            maxLines = 1,
        )

        // Change %
        val changePct = equity.changePct
        val changeColor = when {
            changePct == null -> MaterialTheme.colorScheme.onSurfaceVariant
            changePct > 0 -> stockColors.up
            changePct < 0 -> stockColors.down
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(
            text = NumberFormatter.formatChangePct(changePct),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = changeColor,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(60.dp),
            maxLines = 1,
        )

        // Market Cap
        Text(
            text = NumberFormatter.formatMarketCap(equity.marketCap),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(55.dp),
            maxLines = 1,
        )

        // Volume
        Text(
            text = NumberFormatter.formatVolume(equity.volume),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(50.dp),
            maxLines = 1,
        )

        // Score
        val score = equity.scoreTotal
        Text(
            text = NumberFormatter.formatScore(score),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = when {
                score == null -> MaterialTheme.colorScheme.onSurfaceVariant
                score >= 80 -> stockColors.up
                score >= 60 -> MaterialTheme.colorScheme.onSurface
                else -> stockColors.down
            },
            modifier = Modifier.width(30.dp),
            maxLines = 1,
        )
    }
}
