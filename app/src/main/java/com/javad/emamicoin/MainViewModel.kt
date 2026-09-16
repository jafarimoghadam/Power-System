package com.javad.emamicoin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javad.emamicoin.data.local.SnapshotEntity
import com.javad.emamicoin.data.remote.GoldApiProvider
import com.javad.emamicoin.data.remote.QuoteResult
import com.javad.emamicoin.data.remote.TgjuPublicProvider
import com.javad.emamicoin.domain.CoinInput
import com.javad.emamicoin.domain.CoinResult
import com.javad.emamicoin.domain.EmamiCoinCalculator
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale


data class UiState(
    val goldText: String = "",
    val usdTomanText: String = "",
    val coinTomanText: String = "",
    val goldSource: String = "Manual",
    val usdSource: String = "Manual",
    val coinSource: String = "Manual",
    val premiumSource: String = "—",
    val reportedPremiumIrr: Double? = null,
    val goldTimestamp: Long? = null,
    val iranTimestamp: Long? = null,
    val result: CoinResult? = null,
    val loadingGold: Boolean = false,
    val loadingIran: Boolean = false,
    val message: String? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as EmamiApplication).database
    private val goldProvider = GoldApiProvider()
    private val iranProvider = TgjuPublicProvider()
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    val history = db.snapshots().recent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            loadCached()
            launch { refreshGoldInternal() }
            launch { refreshIranInternal() }
        }
    }

    private suspend fun loadCached() {
        val cached = db.snapshots().latest() ?: return
        _state.update {
            it.copy(
                goldText = String.format(Locale.US, "%.2f", cached.goldUsdPerOunce),
                usdTomanText = String.format(Locale.US, "%.0f", cached.usdIrr / 10),
                coinTomanText = String.format(Locale.US, "%.0f", cached.coinPriceIrr / 10),
                goldSource = "Cache • ${cached.goldSource}",
                usdSource = "Cache • ${cached.usdSource}",
                coinSource = "Cache • ${cached.coinSource}"
            )
        }
        recalc()
    }

    fun setGold(value: String) { _state.update { it.copy(goldText = value, goldSource = "Manual") }; recalc() }
    fun setUsdToman(value: String) { _state.update { it.copy(usdTomanText = value, usdSource = "Manual") }; recalc() }
    fun setCoinToman(value: String) { _state.update { it.copy(coinTomanText = value, coinSource = "Manual") }; recalc() }
    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun refreshAll() {
        refreshGold()
        refreshIran()
    }

    fun refreshGold() = viewModelScope.launch { refreshGoldInternal() }
    private suspend fun refreshGoldInternal() {
        _state.update { it.copy(loadingGold = true, message = null) }
        when (val q = goldProvider.fetch()) {
            is QuoteResult.Success -> _state.update {
                it.copy(
                    goldText = String.format(Locale.US, "%.2f", q.value),
                    goldSource = q.source,
                    goldTimestamp = q.timestamp,
                    loadingGold = false
                )
            }
            is QuoteResult.Error -> _state.update { it.copy(loadingGold = false, message = "اونس زنده دریافت نشد؛ مقدار کش/دستی حفظ شد. ${q.message}") }
        }
        recalc()
    }

    fun refreshIran() = viewModelScope.launch { refreshIranInternal() }
    private suspend fun refreshIranInternal() {
        _state.update { it.copy(loadingIran = true, message = null) }
        val usdD = viewModelScope.async { iranProvider.usdIrr() }
        val coinD = viewModelScope.async { iranProvider.emamiCoinIrr() }
        val premiumD = viewModelScope.async { iranProvider.reportedPremiumIrr() }
        val usd = usdD.await(); val coin = coinD.await(); val premium = premiumD.await()
        val now = System.currentTimeMillis()
        _state.update { old ->
            old.copy(
                usdTomanText = if (usd is QuoteResult.Success) String.format(Locale.US, "%.0f", usd.value / 10.0) else old.usdTomanText,
                usdSource = if (usd is QuoteResult.Success) usd.source else old.usdSource,
                coinTomanText = if (coin is QuoteResult.Success) String.format(Locale.US, "%.0f", coin.value / 10.0) else old.coinTomanText,
                coinSource = if (coin is QuoteResult.Success) coin.source else old.coinSource,
                reportedPremiumIrr = (premium as? QuoteResult.Success)?.value ?: old.reportedPremiumIrr,
                premiumSource = (premium as? QuoteResult.Success)?.source ?: old.premiumSource,
                iranTimestamp = if (usd is QuoteResult.Success || coin is QuoteResult.Success) now else old.iranTimestamp,
                loadingIran = false,
                message = if (usd is QuoteResult.Error && coin is QuoteResult.Error)
                    "TGJU در دسترس نبود؛ مقادیر کش/دستی حفظ شدند." else old.message
            )
        }
        recalc()
    }

    fun recalc() {
        val s = _state.value
        val gold = s.goldText.cleanNumber()?.toDoubleOrNull()
        val usdToman = s.usdTomanText.cleanNumber()?.toDoubleOrNull()
        val coinToman = s.coinTomanText.cleanNumber()?.toDoubleOrNull()
        val r = if (gold != null && usdToman != null && coinToman != null && gold > 0 && usdToman > 0 && coinToman > 0) {
            runCatching { EmamiCoinCalculator.calculate(CoinInput(gold, usdToman * 10.0, coinToman * 10.0)) }.getOrNull()
        } else null
        _state.update { it.copy(result = r) }
    }

    fun saveSnapshot() = viewModelScope.launch {
        val s = _state.value
        val r = s.result ?: return@launch
        val gold = s.goldText.cleanNumber()?.toDoubleOrNull() ?: return@launch
        val usdIrr = (s.usdTomanText.cleanNumber()?.toDoubleOrNull() ?: return@launch) * 10
        val coinIrr = (s.coinTomanText.cleanNumber()?.toDoubleOrNull() ?: return@launch) * 10
        db.snapshots().insert(
            SnapshotEntity(
                capturedAt = System.currentTimeMillis(), goldUsdPerOunce = gold, usdIrr = usdIrr,
                coinPriceIrr = coinIrr, theoreticalValueIrr = r.theoreticalValueIrr,
                premiumIrr = r.premiumIrr, premiumPercent = r.premiumPercent,
                impliedUsdIrr = r.impliedUsdIrr, goldSource = s.goldSource,
                usdSource = s.usdSource, coinSource = s.coinSource
            )
        )
        _state.update { it.copy(message = "نتیجه ذخیره شد") }
    }

    fun clearHistory() = viewModelScope.launch { db.snapshots().clear() }
}

private fun String.cleanNumber(): String = replace(",", "").replace("٬", "").trim()
