package com.emabuia.pokevault.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PremiumManager private constructor(private val context: Context) {

    companion object {
        const val FREE_DECK_LIMIT = 1
        const val FREE_ALBUM_LIMIT = 1
        const val FREE_WISHLIST_LIMIT = 1
        const val FREE_TOURNAMENT_LIMIT = 1
        const val FREE_META_DECK_VIEWS = 10
        const val PRODUCT_MONTHLY = "pokevault_premium_monthly"
        const val PRODUCT_ANNUAL = "pokevault_premium_annual"

        private const val PREFS_NAME = "pokevault_premium"
        private const val KEY_IS_PREMIUM = "is_premium"
        private const val KEY_META_DECK_VIEWS = "meta_deck_views"

        @Volatile
        private var INSTANCE: PremiumManager? = null

        fun init(context: Context) {
            if (INSTANCE == null) {
                synchronized(this) {
                    if (INSTANCE == null) {
                        INSTANCE = PremiumManager(context.applicationContext)
                    }
                }
            }
        }

        fun getInstance(): PremiumManager = INSTANCE
            ?: throw IllegalStateException("PremiumManager not initialized")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isPremium = MutableStateFlow(prefs.getBoolean(KEY_IS_PREMIUM, false))
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _metaDeckViewsUsed = MutableStateFlow(prefs.getInt(KEY_META_DECK_VIEWS, 0))

    val metaDeckViewsRemaining: Int
        get() = if (_isPremium.value) Int.MAX_VALUE
                else (FREE_META_DECK_VIEWS - _metaDeckViewsUsed.value).coerceAtLeast(0)

    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products.asStateFlow()

    private val _purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.Idle)
    val purchaseState: StateFlow<PurchaseState> = _purchaseState.asStateFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener { billingResult, purchases ->
            scope.launch { handlePurchasesUpdated(billingResult, purchases) }
        }
        .enablePendingPurchases()
        .build()

    init {
        connectAndQueryPurchases()
    }

    private var retryCount = 0
    private val maxRetries = 3

    private fun connectAndQueryPurchases() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    retryCount = 0
                    scope.launch {
                        queryProducts()
                        queryExistingPurchases()
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                if (retryCount < maxRetries) {
                    retryCount++
                    scope.launch {
                        delay(retryCount * 2000L)
                        connectAndQueryPurchases()
                    }
                }
            }
        })
    }

    private fun ensureConnected(onReady: () -> Unit) {
        if (billingClient.isReady) {
            onReady()
        } else {
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        onReady()
                    }
                }
                override fun onBillingServiceDisconnected() {}
            })
        }
    }

    private suspend fun queryProducts() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ANNUAL)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        val result = billingClient.queryProductDetails(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            _products.value = result.productDetailsList ?: emptyList()
        }
    }

    private suspend fun queryExistingPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val result = billingClient.queryPurchasesAsync(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            val hasActive = result.purchasesList.any { purchase ->
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            updatePremiumStatus(hasActive)

            // Acknowledge unacknowledged purchases
            result.purchasesList
                .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
                .forEach { acknowledgePurchase(it) }
        }
    }

    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails) {
        val offerToken = productDetails.subscriptionOfferDetails
            ?.firstOrNull()?.offerToken ?: return

        _purchaseState.value = PurchaseState.Loading

        ensureConnected {
            val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()

            val params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productDetailsParams))
                .build()

            billingClient.launchBillingFlow(activity, params)
        }
    }

    private suspend fun handlePurchasesUpdated(
        billingResult: BillingResult,
        purchases: List<Purchase>?
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        acknowledgePurchase(purchase)
                        updatePremiumStatus(true)
                        _purchaseState.value = PurchaseState.Success
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _purchaseState.value = PurchaseState.Idle
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // User already has an active subscription
                scope.launch { queryExistingPurchases() }
                _purchaseState.value = PurchaseState.Success
            }
            else -> {
                _purchaseState.value = PurchaseState.Error(
                    billingResult.debugMessage ?: "Purchase error"
                )
            }
        }
    }

    private suspend fun acknowledgePurchase(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params)
        }
    }

    private fun updatePremiumStatus(isPremium: Boolean) {
        _isPremium.value = isPremium
        prefs.edit().putBoolean(KEY_IS_PREMIUM, isPremium).apply()
        syncToFirestore(isPremium)
    }

    private fun syncToFirestore(isPremium: Boolean) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .update("isPremium", isPremium)
    }

    fun canCreateDeck(currentDeckCount: Int): Boolean {
        return _isPremium.value || currentDeckCount < FREE_DECK_LIMIT
    }

    fun canCreateAlbum(currentAlbumCount: Int): Boolean {
        return _isPremium.value || currentAlbumCount < FREE_ALBUM_LIMIT
    }

    fun canCreateWishlist(currentWishlistCount: Int): Boolean {
        return _isPremium.value || currentWishlistCount < FREE_WISHLIST_LIMIT
    }

    fun canCreateTournament(currentTournamentCount: Int): Boolean {
        return _isPremium.value || currentTournamentCount < FREE_TOURNAMENT_LIMIT
    }

    fun canViewMetaDeck(): Boolean {
        return _isPremium.value || _metaDeckViewsUsed.value < FREE_META_DECK_VIEWS
    }

    fun consumeMetaDeckView() {
        if (_isPremium.value) return
        val newCount = _metaDeckViewsUsed.value + 1
        _metaDeckViewsUsed.value = newCount
        prefs.edit().putInt(KEY_META_DECK_VIEWS, newCount).apply()
    }

    fun restorePurchases() {
        _purchaseState.value = PurchaseState.Loading
        ensureConnected {
            scope.launch {
                queryExistingPurchases()
                _purchaseState.value = PurchaseState.Idle
            }
        }
    }

    fun resetPurchaseState() {
        _purchaseState.value = PurchaseState.Idle
    }

    fun getMonthlyProduct(): ProductDetails? =
        _products.value.find { it.productId == PRODUCT_MONTHLY }

    fun getAnnualProduct(): ProductDetails? =
        _products.value.find { it.productId == PRODUCT_ANNUAL }

    /**
     * Restituisce il prezzo formattato del base plan di un prodotto subscription,
     * ignorando eventuali intro offer, free trial o accelerazioni di periodo
     * di fatturazione (che su alcune configurazioni del Play Console fanno
     * comparire suffissi tipo "/min" dentro [PricingPhase.formattedPrice]).
     *
     * Se possibile ricostruiamo il prezzo a mano da [priceAmountMicros] +
     * [priceCurrencyCode] in modo da eliminare qualunque suffisso di periodo
     * che Google dovesse includere nella stringa formattata.
     */
    fun getBasePlanFormattedPrice(product: ProductDetails?): String? {
        val offers = product?.subscriptionOfferDetails ?: return null
        // Il base plan non ha offerId (o è vuoto): le offerte promozionali
        // come free trial/intro hanno un offerId valorizzato.
        val baseOffer = offers.firstOrNull { it.offerId.isNullOrEmpty() }
            ?: offers.firstOrNull()
            ?: return null

        val phases = baseOffer.pricingPhases.pricingPhaseList
        // L'ultima phase del base plan è quella ricorrente regolare
        // (le prime sono eventuali intro/trial scontati).
        val regularPhase = phases.lastOrNull() ?: return null

        // Prova a ricostruire il prezzo dai valori grezzi, così non
        // dipendiamo da cosa Google mette in formattedPrice.
        val micros = regularPhase.priceAmountMicros
        val currency = regularPhase.priceCurrencyCode
        if (micros > 0 && currency.isNotBlank()) {
            return try {
                val amount = micros / 1_000_000.0
                val nf = java.text.NumberFormat.getCurrencyInstance(
                    java.util.Locale.getDefault()
                )
                nf.currency = java.util.Currency.getInstance(currency)
                nf.format(amount)
            } catch (_: Exception) {
                regularPhase.formattedPrice
            }
        }
        return regularPhase.formattedPrice
    }

    sealed class PurchaseState {
        data object Idle : PurchaseState()
        data object Loading : PurchaseState()
        data object Success : PurchaseState()
        data class Error(val message: String) : PurchaseState()
    }
}
