package com.emabuia.pokevault.data.remote

import com.emabuia.pokevault.BuildConfig
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// ── PokeWallet API Data Models ──

data class PokeWalletSearchResponse(
    val query: String = "",
    val results: List<PokeWalletCard> = emptyList(),
    val pagination: PokeWalletPagination? = null
)

data class PokeWalletSetsResponse(
    val success: Boolean = false,
    val data: List<PokeWalletSet> = emptyList(),
    val total: Int = 0
)

data class PokeWalletSetDetailResponse(
    val success: Boolean = false,
    val set: PokeWalletSet? = null,
    val cards: List<PokeWalletCard> = emptyList(),
    val matches: List<PokeWalletSet> = emptyList(),
    val total: Int = 0
)

data class PokeWalletPagination(
    val page: Int = 1,
    val limit: Int = 20,
    val total: Int = 0,
    @SerializedName("total_pages") val totalPages: Int = 1
)

data class PokeWalletCard(
    val id: String = "",
    @SerializedName("card_info") val cardInfo: PokeWalletCardInfo? = null,
    val tcgplayer: PokeWalletTcgPlayer? = null,
    val cardmarket: PokeWalletCardMarket? = null
)

data class PokeWalletSet(
    val name: String = "",
    @SerializedName("set_code") val setCode: String? = null,
    @SerializedName("set_id") val setId: String = "",
    @SerializedName("card_count") val cardCount: Int = 0,
    @SerializedName("total_cards") val totalCards: Int = 0,
    val language: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null
)

data class PokeWalletCardInfo(
    val name: String = "",
    @SerializedName("clean_name") val cleanName: String? = null,
    @SerializedName("set_name") val setName: String? = null,
    @SerializedName("set_code") val setCode: String? = null,
    @SerializedName("set_id") val setId: String? = null,
    @SerializedName("card_number") val cardNumber: String? = null,
    val rarity: String? = null,
    @SerializedName("card_type") val cardType: String? = null,
    val hp: String? = null,
    val stage: String? = null
)

data class PokeWalletTcgPlayer(
    val url: String? = null,
    val prices: List<PokeWalletTcgPrice> = emptyList()
)

data class PokeWalletTcgPrice(
    @SerializedName("sub_type_name") val subTypeName: String? = null,
    @SerializedName("low_price") val lowPrice: Double? = null,
    @SerializedName("mid_price") val midPrice: Double? = null,
    @SerializedName("high_price") val highPrice: Double? = null,
    @SerializedName("market_price") val marketPrice: Double? = null,
    @SerializedName("direct_low_price") val directLowPrice: Double? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class PokeWalletCardMarket(
    @SerializedName("product_name") val productName: String? = null,
    @SerializedName("product_url") val productUrl: String? = null,
    val prices: List<PokeWalletCmPrice> = emptyList()
)

data class PokeWalletCmPrice(
    val avg: Double? = null,
    val low: Double? = null,
    val avg1: Double? = null,
    val avg7: Double? = null,
    val avg30: Double? = null,
    val trend: Double? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("variant_type") val variantType: String? = null
)

// ── Simplified price data exposed to the UI layer ──

data class PokeWalletPriceData(
    // CardMarket EUR
    val eurAvg: Double? = null,
    val eurLow: Double? = null,
    val eurTrend: Double? = null,
    val eurAvg1: Double? = null,
    val eurAvg7: Double? = null,
    val eurAvg30: Double? = null,
    val eurVariantType: String? = null,
    // TCGPlayer USD (best matching variant)
    val usdMarket: Double? = null,
    val usdLow: Double? = null,
    // Source URLs
    val cardMarketUrl: String? = null,
    val tcgPlayerUrl: String? = null
) {
    /** True if at least one EUR price is available. */
    val hasEurPrices: Boolean
        get() = eurAvg != null || eurLow != null || eurTrend != null

    /** True if avg1/avg7/avg30 are all available (sparkline can be drawn). */
    val hasSparklineData: Boolean
        get() = eurAvg1 != null && eurAvg7 != null && eurAvg30 != null
}

// ── Retrofit Interface ──

interface PokeWalletApiService {
    @GET("sets")
    suspend fun getSets(): PokeWalletSetsResponse

    @GET("sets/{setCode}")
    suspend fun getSet(
        @Path("setCode") setCode: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 200,
        @Query("language") language: String? = null
    ): PokeWalletSetDetailResponse

    @GET("cards/{id}")
    suspend fun getCard(
        @Path("id") id: String,
        @Query("set_code") setCode: String? = null
    ): PokeWalletCard

    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 10
    ): PokeWalletSearchResponse
}

// ── Retrofit Client ──

object PokeWalletRetrofitClient {
    private const val DIRECT_API_URL = "https://api.pokewallet.io/"
    val imageBaseUrl: String
        get() = resolveBaseUrl()

    private fun resolveBaseUrl(): String {
        val proxyUrl = BuildConfig.POKEWALLET_PROXY_URL.trim()
        val useProxy = proxyUrl.isNotEmpty()
        val raw = if (useProxy) proxyUrl else DIRECT_API_URL
        return if (raw.endsWith("/")) raw else "$raw/"
    }

    fun create(apiKey: String): PokeWalletApiService {
        val baseUrl = resolveBaseUrl()
        val proxyUrl = BuildConfig.POKEWALLET_PROXY_URL.trim()
        val useProxy = proxyUrl.isNotEmpty()

        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)

        // Only add X-API-Key header when using direct API
        // When using Cloudflare Worker proxy, the Worker handles the API key server-side
        if (!useProxy) {
            clientBuilder.addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("X-API-Key", apiKey)
                    .build()
                chain.proceed(request)
            }
        }

        val client = clientBuilder.build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PokeWalletApiService::class.java)
    }
}
