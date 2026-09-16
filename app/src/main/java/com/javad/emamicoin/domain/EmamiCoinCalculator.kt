package com.javad.emamicoin.domain

import kotlin.math.abs

data class CoinInput(
    val goldUsdPerOunce: Double,
    val usdIrr: Double,
    val coinPriceIrr: Double
)

data class CoinResult(
    val pureGoldGrams: Double,
    val intrinsicValueUsd: Double,
    val theoreticalValueIrr: Double,
    val premiumIrr: Double,
    val premiumPercent: Double,
    val impliedUsdIrr: Double,
    val impliedUsdDifferenceIrr: Double,
    val impliedUsdDifferencePercent: Double
)

object EmamiCoinCalculator {
    const val TROY_OUNCE_GRAMS = 31.1034768
    const val EMAMI_WEIGHT_GRAMS = 8.133
    const val EMAMI_FINENESS = 0.900
    const val PURE_GOLD_GRAMS = EMAMI_WEIGHT_GRAMS * EMAMI_FINENESS

    fun calculate(input: CoinInput): CoinResult {
        require(input.goldUsdPerOunce > 0.0)
        require(input.usdIrr > 0.0)
        require(input.coinPriceIrr > 0.0)

        val goldUsdPerGram = input.goldUsdPerOunce / TROY_OUNCE_GRAMS
        val intrinsicUsd = goldUsdPerGram * PURE_GOLD_GRAMS
        val theoreticalIrr = intrinsicUsd * input.usdIrr
        val premiumIrr = input.coinPriceIrr - theoreticalIrr
        val premiumPercent = premiumIrr / theoreticalIrr * 100.0
        val impliedUsdIrr = input.coinPriceIrr / intrinsicUsd
        val diffIrr = impliedUsdIrr - input.usdIrr
        val diffPercent = diffIrr / input.usdIrr * 100.0

        return CoinResult(
            pureGoldGrams = PURE_GOLD_GRAMS,
            intrinsicValueUsd = intrinsicUsd,
            theoreticalValueIrr = theoreticalIrr,
            premiumIrr = premiumIrr,
            premiumPercent = premiumPercent,
            impliedUsdIrr = impliedUsdIrr,
            impliedUsdDifferenceIrr = diffIrr,
            impliedUsdDifferencePercent = diffPercent
        )
    }

    fun isReasonable(result: CoinResult): Boolean =
        result.theoreticalValueIrr > 0 && abs(result.premiumPercent) < 100
}
