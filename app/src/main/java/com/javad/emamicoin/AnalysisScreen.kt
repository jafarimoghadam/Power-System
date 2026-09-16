package com.javad.emamicoin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.javad.emamicoin.data.local.SnapshotEntity
import com.javad.emamicoin.domain.EmamiMarketAnalyzer
import com.javad.emamicoin.domain.MarketAnalysis
import com.javad.emamicoin.domain.MarketPoint
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private data class LineSeries(
    val label: String,
    val values: List<Double>,
    val color: Color,
    val formatter: (Double) -> String
)

@Composable
fun AnalysisScreen(state: UiState, history: List<SnapshotEntity>) {
    val now = System.currentTimeMillis()
    val points = remember(history, state.result, state.goldText, state.usdTomanText, state.coinTomanText) {
        buildMarketPoints(history, state, now)
    }
    val analysis = remember(points, state.goldTimestamp, state.iranTimestamp) {
        EmamiMarketAnalyzer.analyze(
            points = points,
            goldFresh = state.goldTimestamp.isFresh(now),
            iranFresh = state.iranTimestamp.isFresh(now)
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "تحلیل بازار و شاخص تصمیم",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            if (analysis == null) {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "برای ساخت شاخص‌ها، اونس، دلار و قیمت سکه را وارد یا به‌روزرسانی کنید.",
                        Modifier.padding(18.dp)
                    )
                }
            } else {
                DecisionSignalCard(analysis)
            }
        }

        if (analysis != null) {
            item { IndicatorGrid(analysis) }
            item { ReasonCard(analysis) }
        }

        item {
            PriceVsIntrinsicChart(points)
        }
        item {
            PremiumChart(points)
        }
        item {
            UsdChart(points)
        }
        item {
            GoldChart(points)
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("نحوه استفاده", fontWeight = FontWeight.Bold)
                    Text(
                        "امتیاز ۰ تا ۱۰۰ از سه عامل ساخته می‌شود: حباب نسبت به سابقه، فاصله دلار مستتر با دلار بازار، و سرعت حرکت قیمت سکه نسبت به ارزش ذاتی. امتیاز بالاتر فقط به معنی قیمت‌گذاری نسبی کم‌تنش‌تر است؛ تضمین سود یا توصیه قطعی خرید/فروش نیست.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "برای تحلیل روند معتبرتر، در زمان‌های مختلف نتیجه را ذخیره کنید. با کمتر از ۵ رکورد، وزن تحلیل تاریخی محدود است.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun DecisionSignalCard(a: MarketAnalysis) {
    val scheme = MaterialTheme.colorScheme
    val accent = when {
        a.score >= 68 -> scheme.primary
        a.score >= 44 -> scheme.tertiary
        else -> scheme.error
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("شاخص ترکیبی", style = MaterialTheme.typography.labelLarge)
                    Text(a.titleFa, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Surface(shape = CircleShape, color = accent.copy(alpha = 0.12f)) {
                    Text(
                        "${a.score}",
                        color = accent,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
            LinearProgressIndicator(
                progress = { a.score / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = accent
            )
            Text(a.explanationFa, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, tonalElevation = 1.dp) {
                    Text("اعتماد: ${a.confidenceFa}", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
                }
                Text("۰ فروش/احتیاط  •  ۵۰ خنثی  •  ۱۰۰ خرید/حمایت", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun IndicatorGrid(a: MarketAnalysis) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IndicatorCard(
            title = "حباب فعلی",
            value = pct(a.premiumPercent),
            detail = a.premiumVsMedianPoints?.let { "فاصله با میانه اخیر: ${signedPctPoints(it)}" }
                ?: "برای مقایسه با میانه، حداقل ۵ رکورد لازم است."
        )
        IndicatorCard(
            title = "فاصله دلار مستتر",
            value = signedPct(a.fxSpreadPercent),
            detail = if (a.fxSpreadPercent > 0) "دلار مستتر بالاتر از دلار بازار است." else "دلار مستتر پایین‌تر یا نزدیک دلار بازار است."
        )
        IndicatorCard(
            title = "مومنتوم مازاد سکه",
            value = a.excessMomentumPercent?.let(::signedPct) ?: "—",
            detail = "تغییر قیمت سکه منهای تغییر ارزش ذاتی در پنجره اخیر. مقدار مثبت یعنی سکه سریع‌تر از عوامل بنیادی حرکت کرده است."
        )
        if (a.coinMomentumPercent != null && a.intrinsicMomentumPercent != null) {
            IndicatorCard(
                title = "حرکت اخیر",
                value = "سکه ${signedPct(a.coinMomentumPercent)} / ذاتی ${signedPct(a.intrinsicMomentumPercent)}",
                detail = "اونس ${a.goldMomentumPercent?.let(::signedPct) ?: "—"}  •  دلار ${a.usdMomentumPercent?.let(::signedPct) ?: "—"}"
            )
        }
    }
}

@Composable
private fun IndicatorCard(title: String, value: String, detail: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(value, fontWeight = FontWeight.Bold)
            }
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ReasonCard(a: MarketAnalysis) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("دلایل امتیاز", fontWeight = FontWeight.Bold)
            a.reasonsFa.forEach { reason ->
                Text("• $reason", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PriceVsIntrinsicChart(points: List<MarketPoint>) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    TrendChartCard(
        title = "قیمت سکه در برابر ارزش ذاتی",
        unit = "میلیون تومان",
        points = points,
        series = listOf(
            LineSeries("قیمت بازار", points.map { it.coinPriceIrr / 10.0 / 1_000_000.0 }, primary) { oneDecimal(it) },
            LineSeries("ارزش ذاتی", points.map { it.theoreticalValueIrr / 10.0 / 1_000_000.0 }, tertiary) { oneDecimal(it) }
        )
    )
}

@Composable
private fun PremiumChart(points: List<MarketPoint>) {
    val primary = MaterialTheme.colorScheme.primary
    TrendChartCard(
        title = "روند درصد حباب",
        unit = "درصد",
        points = points,
        series = listOf(
            LineSeries("حباب", points.map { it.premiumPercent }, primary) { String.format(Locale.US, "%.2f%%", it) }
        )
    )
}

@Composable
private fun UsdChart(points: List<MarketPoint>) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    TrendChartCard(
        title = "دلار بازار در برابر دلار مستتر سکه",
        unit = "تومان",
        points = points,
        series = listOf(
            LineSeries("دلار بازار", points.map { it.usdIrr / 10.0 }, primary) { whole(it) },
            LineSeries("دلار مستتر", points.map { it.impliedUsdIrr / 10.0 }, tertiary) { whole(it) }
        )
    )
}

@Composable
private fun GoldChart(points: List<MarketPoint>) {
    val primary = MaterialTheme.colorScheme.primary
    TrendChartCard(
        title = "روند اونس جهانی",
        unit = "USD / oz",
        points = points,
        series = listOf(
            LineSeries("XAU/USD", points.map { it.goldUsdPerOunce }, primary) { twoDecimals(it) }
        )
    )
}

@Composable
private fun TrendChartCard(
    title: String,
    unit: String,
    points: List<MarketPoint>,
    series: List<LineSeries>
) {
    val visiblePoints = points.takeLast(40)
    val visibleSeries = series.map { it.copy(values = it.values.takeLast(40)) }
    val allValues = visibleSeries.flatMap { it.values }.filter { it.isFinite() }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(unit, style = MaterialTheme.typography.labelSmall)
            }

            if (visiblePoints.size < 2 || allValues.size < 2) {
                Text(
                    "برای نمایش روند حداقل دو نقطه زمانی لازم است. یک نتیجه را ذخیره کنید و بعداً دوباره بازار را ثبت کنید.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                val rawMin = allValues.minOrNull() ?: 0.0
                val rawMax = allValues.maxOrNull() ?: 1.0
                val span = (rawMax - rawMin).takeIf { it > 0 } ?: (abs(rawMax).coerceAtLeast(1.0) * 0.02)
                val minY = rawMin - span * 0.08
                val maxY = rawMax + span * 0.08

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("بیشینه ${compact(maxY)}", style = MaterialTheme.typography.labelSmall)
                    Text("کمینه ${compact(minY)}", style = MaterialTheme.typography.labelSmall)
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    val left = 4.dp.toPx()
                    val right = size.width - 4.dp.toPx()
                    val top = 6.dp.toPx()
                    val bottom = size.height - 6.dp.toPx()
                    val chartWidth = (right - left).coerceAtLeast(1f)
                    val chartHeight = (bottom - top).coerceAtLeast(1f)

                    repeat(5) { i ->
                        val y = top + chartHeight * (i / 4f)
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.18f),
                            start = androidx.compose.ui.geometry.Offset(left, y),
                            end = androidx.compose.ui.geometry.Offset(right, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    visibleSeries.forEach { line ->
                        if (line.values.size >= 2) {
                            val path = Path()
                            line.values.forEachIndexed { index, value ->
                                val x = left + chartWidth * index / (line.values.size - 1).toFloat()
                                val normalized = ((value - minY) / (maxY - minY)).coerceIn(0.0, 1.0)
                                val y = bottom - chartHeight * normalized.toFloat()
                                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(line.color, path, style = Stroke(width = 2.4.dp.toPx()))
                            val lastValue = line.values.last()
                            val lastNormalized = ((lastValue - minY) / (maxY - minY)).coerceIn(0.0, 1.0)
                            drawCircle(
                                color = line.color,
                                radius = 3.8.dp.toPx(),
                                center = androidx.compose.ui.geometry.Offset(right, bottom - chartHeight * lastNormalized.toFloat())
                            )
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(shortTime(visiblePoints.first().timestamp), style = MaterialTheme.typography.labelSmall)
                    Text("${visiblePoints.size} نقطه", style = MaterialTheme.typography.labelSmall)
                    Text(shortTime(visiblePoints.last().timestamp), style = MaterialTheme.typography.labelSmall)
                }

                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    visibleSeries.forEach { line ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Box(Modifier.size(9.dp).background(line.color, CircleShape))
                            Text(line.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            Text(line.formatter(line.values.last()), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

private fun buildMarketPoints(history: List<SnapshotEntity>, state: UiState, now: Long): List<MarketPoint> {
    val historical = history
        .sortedBy { it.capturedAt }
        .map {
            MarketPoint(
                timestamp = it.capturedAt,
                goldUsdPerOunce = it.goldUsdPerOunce,
                usdIrr = it.usdIrr,
                coinPriceIrr = it.coinPriceIrr,
                theoreticalValueIrr = it.theoreticalValueIrr,
                premiumPercent = it.premiumPercent,
                impliedUsdIrr = it.impliedUsdIrr
            )
        }
        .toMutableList()

    val r = state.result
    val gold = state.goldText.cleanNumberForAnalysis()?.toDoubleOrNull()
    val usdToman = state.usdTomanText.cleanNumberForAnalysis()?.toDoubleOrNull()
    val coinToman = state.coinTomanText.cleanNumberForAnalysis()?.toDoubleOrNull()
    if (r != null && gold != null && usdToman != null && coinToman != null && gold > 0 && usdToman > 0 && coinToman > 0) {
        historical += MarketPoint(
            timestamp = now,
            goldUsdPerOunce = gold,
            usdIrr = usdToman * 10.0,
            coinPriceIrr = coinToman * 10.0,
            theoreticalValueIrr = r.theoreticalValueIrr,
            premiumPercent = r.premiumPercent,
            impliedUsdIrr = r.impliedUsdIrr
        )
    }
    return historical.takeLast(50)
}

private fun Long?.isFresh(now: Long): Boolean = this != null && now - this in 0..15 * 60_000L
private fun String.cleanNumberForAnalysis(): String = replace(",", "").replace("٬", "").trim()

private val analysisNumber: NumberFormat = NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 0 }
private fun whole(v: Double): String = analysisNumber.format(v)
private fun oneDecimal(v: Double): String = String.format(Locale.US, "%.1f", v)
private fun twoDecimals(v: Double): String = String.format(Locale.US, "%.2f", v)
private fun pct(v: Double): String = String.format(Locale.US, "%.2f%%", v)
private fun signedPct(v: Double): String = String.format(Locale.US, "%+.2f%%", v)
private fun signedPctPoints(v: Double): String = String.format(Locale.US, "%+.2f pp", v)
private fun compact(v: Double): String = when {
    abs(v) >= 1_000_000 -> String.format(Locale.US, "%.1fM", v / 1_000_000.0)
    abs(v) >= 1_000 -> String.format(Locale.US, "%.1fK", v / 1_000.0)
    else -> String.format(Locale.US, "%.1f", v)
}
private fun shortTime(ms: Long): String = SimpleDateFormat("MM/dd HH:mm", Locale.US).format(Date(ms))
