@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.javad.emamicoin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javad.emamicoin.data.local.SnapshotEntity
import com.javad.emamicoin.ui.theme.EmamiTheme
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { EmamiTheme { EmamiApp() } }
    }
}

@Composable
private fun EmamiApp(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = { TopAppBar(title = { Text("محاسبه‌گر سکه امامی", fontWeight = FontWeight.Bold) }) },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("محاسبه") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.History, null) }, label = { Text("تاریخچه") })
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                if (tab == 0) CalculatorScreen(state, vm) else HistoryScreen(history, vm::clearHistory)
            }
        }
    }

    state.message?.let { msg ->
        AlertDialog(onDismissRequest = vm::dismissMessage, confirmButton = { TextButton(onClick = vm::dismissMessage) { Text("باشه") } }, text = { Text(msg) })
    }
}

@Composable
private fun CalculatorScreen(state: UiState, vm: MainViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            MarketInputCard(
                title = "اونس جهانی طلا", value = state.goldText, suffix = "USD / oz",
                source = state.goldSource, onChange = vm::setGold,
                action = {
                    Button(onClick = vm::refreshGold, enabled = !state.loadingGold) {
                        Text(if (state.loadingGold) "در حال دریافت…" else "دریافت زنده")
                    }
                }
            )
        }
        item {
            MarketInputCard(
                title = "دلار آزاد", value = state.usdTomanText, suffix = "تومان",
                source = state.usdSource, onChange = vm::setUsdToman,
                action = { Button(onClick = vm::refreshIran, enabled = !state.loadingIran) { Text(if (state.loadingIran) "در حال دریافت…" else "دریافت TGJU") } }
            )
        }
        item {
            MarketInputCard(
                title = "قیمت سکه امامی", value = state.coinTomanText, suffix = "تومان",
                source = state.coinSource, onChange = vm::setCoinToman
            )
        }
        item {
            state.result?.let { r ->
                ResultCard(r.theoreticalValueIrr / 10, r.premiumIrr / 10, r.premiumPercent, r.impliedUsdIrr / 10, r.impliedUsdDifferenceIrr / 10, r.impliedUsdDifferencePercent, state.reportedPremiumIrr?.div(10.0), state.premiumSource)
            } ?: Card(Modifier.fillMaxWidth()) {
                Text("برای محاسبه، نرخ دلار و قیمت سکه را وارد کنید.", Modifier.padding(18.dp))
            }
        }
        item {
            Button(
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = state.result != null,
                onClick = vm::saveSnapshot
            ) { Text("ذخیره نتیجه") }
        }
        item {
            Text("مبنای محاسبه: وزن ۸٫۱۳۳ گرم، عیار ۹۰۰، و هر اونس تروا ۳۱٫۱۰۳۴۷۶۸ گرم. تمام محاسبات داخلی با ریال انجام می‌شود و نمایش به تومان تبدیل می‌شود.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MarketInputCard(title: String, value: String, suffix: String, source: String, onChange: (String) -> Unit, action: (@Composable () -> Unit)? = null) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text("منبع: $source", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedTextField(
                value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
                singleLine = true, suffix = { Text(suffix) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            action?.invoke()
        }
    }
}

@Composable
private fun ResultCard(theoreticalToman: Double, premiumToman: Double, premiumPercent: Double, impliedUsdToman: Double, usdDiffToman: Double, usdDiffPercent: Double, reportedPremiumToman: Double?, reportedPremiumSource: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("نتیجه", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Metric("ارزش ذاتی سکه", money(theoreticalToman) + " تومان")
            HorizontalDivider()
            Metric("حباب محاسبه‌شده", signedMoney(premiumToman) + " تومان  (${signedPct(premiumPercent)})")
            if (reportedPremiumToman != null) {
                Metric("حباب گزارش‌شده", money(reportedPremiumToman) + " تومان")
                Text(reportedPremiumSource, style = MaterialTheme.typography.labelSmall)
            }
            HorizontalDivider()
            Metric("دلار مستتر در قیمت سکه", money(impliedUsdToman) + " تومان")
            Metric("اختلاف دلار مستتر با بازار", signedMoney(usdDiffToman) + " تومان  (${signedPct(usdDiffPercent)})")
        }
    }
}

@Composable private fun Metric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HistoryScreen(history: List<SnapshotEntity>, clear: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("تاریخچه محاسبات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = clear, enabled = history.isNotEmpty()) { Text("پاک کردن") }
        }
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("هنوز نتیجه‌ای ذخیره نشده است.") }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(history, key = { it.id }) { s ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(dateTime(s.capturedAt), fontWeight = FontWeight.SemiBold)
                            Text("سکه: ${money(s.coinPriceIrr / 10)} تومان")
                            Text("حباب: ${signedMoney(s.premiumIrr / 10)} تومان (${signedPct(s.premiumPercent)})")
                            Text("دلار مستتر: ${money(s.impliedUsdIrr / 10)} تومان")
                        }
                    }
                }
            }
        }
    }
}

private val nf: NumberFormat = NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 0 }
private fun money(v: Double) = nf.format(v)
private fun signedMoney(v: Double) = (if (v >= 0) "+" else "−") + nf.format(kotlin.math.abs(v))
private fun signedPct(v: Double) = String.format(Locale.US, "%+.2f%%", v)
private fun dateTime(ms: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ms))
