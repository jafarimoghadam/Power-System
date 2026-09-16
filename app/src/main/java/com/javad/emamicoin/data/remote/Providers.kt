package com.javad.emamicoin.data.remote

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.jsoup.Jsoup
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.time.Instant

sealed interface QuoteResult {
    data class Success(val value: Double, val source: String, val timestamp: Long) : QuoteResult
    data class Error(val message: String) : QuoteResult
}

interface GoldApiService {
    @GET("price/XAU") suspend fun gold(): GoldApiResponse
}

data class GoldApiResponse(
    val name: String?,
    val symbol: String?,
    val price: Double,
    @SerializedName("updatedAt") val updatedAt: String?
)

private fun baseClient(): OkHttpClient = OkHttpClient.Builder()
    .addInterceptor { chain ->
        chain.proceed(
            chain.request().newBuilder()
                .header("User-Agent", "EmamiCoinCalculator/1.0 Android")
                .build()
        )
    }
    .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
    .build()

class GoldApiProvider {
    private val service: GoldApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.gold-api.com/")
            .client(baseClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GoldApiService::class.java)
    }

    suspend fun fetch(): QuoteResult = runCatching {
        val r = service.gold()
        require(r.price > 0) { "Invalid gold quote" }
        val ts = r.updatedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            ?: System.currentTimeMillis()
        QuoteResult.Success(r.price, "gold-api.com", ts)
    }.getOrElse { QuoteResult.Error(it.message ?: "Gold feed unavailable") }
}

interface IranMarketProvider {
    suspend fun usdIrr(): QuoteResult
    suspend fun emamiCoinIrr(): QuoteResult
    suspend fun reportedPremiumIrr(): QuoteResult
}

class TgjuPublicProvider : IranMarketProvider {
    private interface TgjuPages {
        @GET("profile/price_dollar_rl") suspend fun usd(): ResponseBody
        @GET("profile/sekee") suspend fun emami(): ResponseBody
        @GET("profile/coin_blubber") suspend fun premium(): ResponseBody
    }

    private val service: TgjuPages by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.tgju.org/")
            .client(baseClient())
            .build()
            .create(TgjuPages::class.java)
    }

    override suspend fun usdIrr(): QuoteResult = fetchAndParse("TGJU • USD") { service.usd() }
    override suspend fun emamiCoinIrr(): QuoteResult = fetchAndParse("TGJU • Emami") { service.emami() }
    override suspend fun reportedPremiumIrr(): QuoteResult = fetchAndParse("TGJU • Reported premium") { service.premium() }

    private suspend fun fetchAndParse(source: String, body: suspend () -> ResponseBody): QuoteResult = runCatching {
        val html = body().string()
        val text = Jsoup.parse(html).text()
        val value = parseCurrentRate(text) ?: error("TGJU page format changed")
        QuoteResult.Success(value, source, System.currentTimeMillis())
    }.getOrElse { QuoteResult.Error(it.message ?: "TGJU feed unavailable") }

    private fun parseCurrentRate(text: String): Double? {
        val normalized = normalizeDigits(text)
        val regex = Regex("""نرخ\s*فعلی\s*[:：]*\s*([0-9][0-9,٬]*)""")
        return regex.find(normalized)?.groupValues?.getOrNull(1)
            ?.replace(",", "")?.replace("٬", "")?.toDoubleOrNull()
    }

    private fun normalizeDigits(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            append(when (ch) {
                '۰','٠' -> '0'; '۱','١' -> '1'; '۲','٢' -> '2'; '۳','٣' -> '3'; '۴','٤' -> '4'
                '۵','٥' -> '5'; '۶','٦' -> '6'; '۷','٧' -> '7'; '۸','٨' -> '8'; '۹','٩' -> '9'
                else -> ch
            })
        }
    }
}
