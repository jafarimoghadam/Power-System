package com.javad.emamicoin.domain

import kotlin.math.abs
import kotlin.math.roundToInt

data class MarketPoint(
    val timestamp: Long,
    val goldUsdPerOunce: Double,
    val usdIrr: Double,
    val coinPriceIrr: Double,
    val theoreticalValueIrr: Double,
    val premiumPercent: Double,
    val impliedUsdIrr: Double
)

enum class SignalLevel {
    SUPPORTIVE,
    MILDLY_SUPPORTIVE,
    NEUTRAL,
    CAUTION,
    HIGH_CAUTION
}

data class MarketAnalysis(
    val score: Int,
    val level: SignalLevel,
    val titleFa: String,
    val explanationFa: String,
    val confidenceFa: String,
    val premiumPercent: Double,
    val premiumVsMedianPoints: Double?,
    val fxSpreadPercent: Double,
    val excessMomentumPercent: Double?,
    val coinMomentumPercent: Double?,
    val intrinsicMomentumPercent: Double?,
    val goldMomentumPercent: Double?,
    val usdMomentumPercent: Double?,
    val reasonsFa: List<String>
)

object EmamiMarketAnalyzer {
    fun analyze(
        points: List<MarketPoint>,
        goldFresh: Boolean,
        iranFresh: Boolean
    ): MarketAnalysis? {
        val clean = points
            .filter {
                it.goldUsdPerOunce > 0 && it.usdIrr > 0 && it.coinPriceIrr > 0 &&
                    it.theoreticalValueIrr > 0 && it.impliedUsdIrr > 0
            }
            .sortedBy { it.timestamp }

        val current = clean.lastOrNull() ?: return null
        val fxSpread = pctChange(current.usdIrr, current.impliedUsdIrr)

        val premiums = clean.map { it.premiumPercent }.sorted()
        val medianPremium = if (premiums.size >= 5) median(premiums) else null
        val premiumVsMedian = medianPremium?.let { current.premiumPercent - it }

        val anchor = if (clean.size >= 2) clean[(clean.size - 5).coerceAtLeast(0)] else null
        val coinMomentum = anchor?.let { pctChange(it.coinPriceIrr, current.coinPriceIrr) }
        val intrinsicMomentum = anchor?.let { pctChange(it.theoreticalValueIrr, current.theoreticalValueIrr) }
        val goldMomentum = anchor?.let { pctChange(it.goldUsdPerOunce, current.goldUsdPerOunce) }
        val usdMomentum = anchor?.let { pctChange(it.usdIrr, current.usdIrr) }
        val excessMomentum = if (coinMomentum != null && intrinsicMomentum != null) coinMomentum - intrinsicMomentum else null

        var score = 50
        val reasons = mutableListOf<String>()

        if (premiumVsMedian != null) {
            when {
                premiumVsMedian <= -5 -> {
                    score += 20
                    reasons += "حباب بیش از ۵ واحد درصد پایین‌تر از میانه اخیر است."
                }
                premiumVsMedian <= -2 -> {
                    score += 12
                    reasons += "حباب پایین‌تر از میانه اخیر است."
                }
                premiumVsMedian < 2 -> reasons += "حباب نزدیک به محدوده معمول اخیر است."
                premiumVsMedian < 5 -> {
                    score -= 12
                    reasons += "حباب بالاتر از میانه اخیر است."
                }
                else -> {
                    score -= 20
                    reasons += "حباب بیش از ۵ واحد درصد بالاتر از میانه اخیر است."
                }
            }
        } else {
            when {
                current.premiumPercent <= 0 -> {
                    score += 15
                    reasons += "قیمت سکه در یا پایین‌تر از ارزش طلای محاسباتی قرار دارد."
                }
                current.premiumPercent <= 5 -> {
                    score += 10
                    reasons += "حباب مطلق پایین است."
                }
                current.premiumPercent <= 10 -> {
                    score += 4
                    reasons += "حباب مطلق در محدوده میانه است."
                }
                current.premiumPercent <= 20 -> {
                    score -= 8
                    reasons += "حباب مطلق قابل توجه است."
                }
                else -> {
                    score -= 15
                    reasons += "حباب مطلق بالا است."
                }
            }
        }

        when {
            fxSpread <= -3 -> {
                score += 15
                reasons += "دلار مستتر حداقل ۳٪ پایین‌تر از دلار بازار است."
            }
            fxSpread <= -1 -> {
                score += 8
                reasons += "دلار مستتر پایین‌تر از دلار بازار است."
            }
            fxSpread < 1 -> reasons += "دلار مستتر و دلار بازار نزدیک هم هستند."
            fxSpread < 3 -> {
                score -= 8
                reasons += "دلار مستتر کمی بالاتر از دلار بازار است."
            }
            else -> {
                score -= 15
                reasons += "دلار مستتر حداقل ۳٪ بالاتر از دلار بازار است."
            }
        }

        if (excessMomentum != null) {
            when {
                excessMomentum <= -3 -> {
                    score += 10
                    reasons += "سکه نسبت به ارزش ذاتی عقب‌مانده و حباب در حال فشرده‌شدن است."
                }
                excessMomentum <= -1 -> {
                    score += 5
                    reasons += "رشد سکه از رشد ارزش ذاتی کندتر بوده است."
                }
                excessMomentum < 1.5 -> reasons += "حرکت سکه و ارزش ذاتی تقریباً همسو است."
                excessMomentum < 3 -> {
                    score -= 5
                    reasons += "سکه کمی سریع‌تر از ارزش ذاتی رشد کرده است."
                }
                else -> {
                    score -= 10
                    reasons += "سکه به‌وضوح سریع‌تر از ارزش ذاتی رشد کرده و ریسک گسترش حباب بالاتر است."
                }
            }
        }

        if (current.premiumPercent > 30) score -= 5
        if (abs(fxSpread) > 8) score -= 3
        score = score.coerceIn(0, 100)

        val level = when {
            score >= 68 -> SignalLevel.SUPPORTIVE
            score >= 56 -> SignalLevel.MILDLY_SUPPORTIVE
            score >= 44 -> SignalLevel.NEUTRAL
            score >= 32 -> SignalLevel.CAUTION
            else -> SignalLevel.HIGH_CAUTION
        }

        val title = when (level) {
            SignalLevel.SUPPORTIVE -> "شرایط نسبی حمایتی برای بررسی خرید"
            SignalLevel.MILDLY_SUPPORTIVE -> "تمایل ملایم به سمت خرید"
            SignalLevel.NEUTRAL -> "شرایط خنثی / نیازمند صبر"
            SignalLevel.CAUTION -> "احتیاط؛ شرایط متمایل به فروش یا کاهش ریسک"
            SignalLevel.HIGH_CAUTION -> "احتیاط بالا؛ حباب/قیمت‌گذاری کشیده"
        }

        val explanation = when (level) {
            SignalLevel.SUPPORTIVE -> "ترکیب حباب، دلار مستتر و مومنتوم نسبت به گذشته فعلاً به نفع قیمت‌گذاری کم‌تنش‌تر است."
            SignalLevel.MILDLY_SUPPORTIVE -> "چند عامل حمایتی دیده می‌شود، اما قدرت سیگنال برای تصمیم قطعی کافی نیست."
            SignalLevel.NEUTRAL -> "شاخص‌ها مزیت واضحی برای خرید یا فروش نشان نمی‌دهند."
            SignalLevel.CAUTION -> "قیمت‌گذاری نسبت به ارزش ذاتی یا دلار بازار کشیده‌تر شده است."
            SignalLevel.HIGH_CAUTION -> "چند شاخص هم‌زمان از حباب یا فاصله زیاد با عوامل بنیادی خبر می‌دهند."
        }

        val confidence = when {
            clean.size >= 10 && goldFresh && iranFresh -> "زیاد"
            clean.size >= 5 && (goldFresh || iranFresh) -> "متوسط"
            else -> "کم"
        }

        return MarketAnalysis(
            score = score,
            level = level,
            titleFa = title,
            explanationFa = explanation,
            confidenceFa = confidence,
            premiumPercent = current.premiumPercent,
            premiumVsMedianPoints = premiumVsMedian,
            fxSpreadPercent = fxSpread,
            excessMomentumPercent = excessMomentum,
            coinMomentumPercent = coinMomentum,
            intrinsicMomentumPercent = intrinsicMomentum,
            goldMomentumPercent = goldMomentum,
            usdMomentumPercent = usdMomentum,
            reasonsFa = reasons.take(4)
        )
    }

    private fun pctChange(from: Double, to: Double): Double = if (from == 0.0) 0.0 else (to / from - 1.0) * 100.0

    private fun median(sorted: List<Double>): Double {
        if (sorted.isEmpty()) return 0.0
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
    }
}
