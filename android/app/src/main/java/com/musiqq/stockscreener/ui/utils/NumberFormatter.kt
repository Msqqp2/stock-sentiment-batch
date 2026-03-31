package com.musiqq.stockscreener.ui.utils

import java.text.DecimalFormat
import kotlin.math.abs

object NumberFormatter {

    private val priceFormat = DecimalFormat("#,##0.00")
    private val pctFormat = DecimalFormat("0.00")
    private val ratioFormat = DecimalFormat("0.0")
    private val intFormat = DecimalFormat("#,##0")
    private val yieldFormat = DecimalFormat("0.00")

    fun formatPrice(value: Double?): String {
        if (value == null) return "-"
        return "$${priceFormat.format(value)}"
    }

    fun formatPct(value: Double?): String {
        if (value == null) return "-"
        val pct = value * 100
        val sign = if (pct > 0) "+" else ""
        return "$sign${pctFormat.format(pct)}%"
    }

    fun formatChangePct(value: Double?): String {
        if (value == null) return "-"
        val sign = if (value > 0) "+" else ""
        return "$sign${pctFormat.format(value)}%"
    }

    fun formatMarketCap(value: Long?): String {
        if (value == null) return "-"
        return when {
            abs(value) >= 1_000_000_000_000L -> "$${ratioFormat.format(value / 1_000_000_000_000.0)}T"
            abs(value) >= 1_000_000_000L -> "$${ratioFormat.format(value / 1_000_000_000.0)}B"
            abs(value) >= 1_000_000L -> "$${ratioFormat.format(value / 1_000_000.0)}M"
            else -> "$${intFormat.format(value)}"
        }
    }

    fun formatVolume(value: Long?): String {
        if (value == null) return "-"
        return when {
            value >= 1_000_000_000L -> "${ratioFormat.format(value / 1_000_000_000.0)}B"
            value >= 1_000_000L -> "${ratioFormat.format(value / 1_000_000.0)}M"
            value >= 1_000L -> "${ratioFormat.format(value / 1_000.0)}K"
            else -> intFormat.format(value)
        }
    }

    fun formatRatio(value: Double?): String {
        if (value == null) return "-"
        return ratioFormat.format(value)
    }

    fun formatYield(value: Double?): String {
        if (value == null) return "-"
        return "${yieldFormat.format(value * 100)}%"
    }

    fun formatScore(value: Int?): String {
        if (value == null) return "-"
        return value.toString()
    }

    fun formatInt(value: Int?): String {
        if (value == null) return "-"
        return intFormat.format(value)
    }

    fun formatMultiple(value: Double?): String {
        if (value == null) return "-"
        return "${pctFormat.format(value)}x"
    }
}
