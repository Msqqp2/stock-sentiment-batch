package com.musiqq.stockscreener.domain.model

data class ScoreWeights(
    val value: Float = 0.25f,
    val quality: Float = 0.25f,
    val momentum: Float = 0.25f,
    val growth: Float = 0.25f,
) {
    fun customTotal(equity: Equity): Double {
        val v = (equity.scoreValue ?: 0).toDouble()
        val q = (equity.scoreQuality ?: 0).toDouble()
        val m = (equity.scoreMomentum ?: 0).toDouble()
        val g = (equity.scoreGrowth ?: 0).toDouble()
        return v * value + q * quality + m * momentum + g * growth
    }
}
