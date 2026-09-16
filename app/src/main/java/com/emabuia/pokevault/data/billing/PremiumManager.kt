package com.emabuia.pokevault.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class PremiumManager private constructor(private val context: Context) {

    companion object {
        const val FREE_DECK_LIMIT = 1
        const val FREE_ALBUM_LIMIT = 1
        const val FREE_GOAL_ALBUM_LIMIT = 1
        const val FREE_WISHLIST_LIMIT = 1
        const val FREE_TOURNAMENT_LIMIT = 1
        const val FREE_META_DECK_VIEWS = 10
        const val PRODUCT_MONTHLY = "pokevault_premium_monthly"
        const val PRODUCT_ANNUAL = "pokevault_premium_annual"

        private const val PREFS_NAME = "pokevault_premium"
        private const val KEY_META_DECK_VIEWS = "meta_deck_views"
        private const val KEY_HOME_SPRITE_ID = "home_sprite_id"
        private const val KEY_HAND_SIM_RUN_PREFIX = "hand_sim_runs_"
        private const val KEY_GIFT_UNTIL_MS = "gift_until_ms"
        private const val KEY_GIFT_UID = "gift_uid"

        /**
         * Regola unica dei limiti free, in forma pura e testabile.
         *
         * Le funzioni di gate leggevano lo stato premium dell'istanza, quindi
         * non erano verificabili senza un BillingClient e un Context: non
         * esisteva alcun test su PremiumManager.
         */
        fun isWithinFreeLimit(isPremium: Boolean, currentCount: Int, freeLimit: Int): Boolean =
            isPremium || currentCount < freeLimit

        /**
         * Perche' il servizio di fatturazione non e' raggiungibile.
         *
         * Serve a dire all'utente qualcosa di utile al posto del `debugMessage`
         * inglese di Google ("Server is disconnected"), che non e' ne' tradotto
         * ne' azionabile.
         */
        enum class BillingProblem {
            /** Connessione al servizio caduta o mai stabilita. Di solito passa da sola. */
            DISCONNECTED,
            /** Rete assente o instabile. */
            NETWORK,
            /** Play Store assente o disattivato, o account senza fatturazione. */
            UNAVAILABLE,
            /**
             * Prodotti non pubblicati, o app non riconosciuta da Play.
             *
             * E' quello che si vede installando una build firmata con la chiave
             * di debug: il package combacia, la firma no.
             */
            MISCONFIGURED,
            OTHER
        }

        /**
         * Traduce un response code di BillingClient nel guasto corrispondente.
         *
         * Funzione pura e in companion apposta: e' l'unico pezzo di questa
         * logica verificabile senza un BillingClient e un Context.
         */
        fun billingProblemFor(responseCode: Int): BillingProblem = when (responseCode) {
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> BillingProblem.DISCONNECTED
            BillingClient.BillingResponseCode.NETWORK_ERROR,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> BillingProblem.NETWORK
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> BillingProblem.UNAVAILABLE
            BillingClient.BillingResponseCode.DEVELOPER_ERROR,
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> BillingProblem.MISCONFIGURED
            else -> BillingProblem.OTHER
        }

        private const val ACK_MAX_ATTEMPTS = 3
        private const val ACK_RETRY_DELAY_MS = 1500L

        private val HOME_SPRITE_IDS = listOf(25, 1, 4, 7, 133, 150, 151, 384, 448, 94, 158, 258, 393, 6, 9, 3)

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

    // All'avvio, la fonte di verità è sempre BillingClient/server, non SharedPreferences
    private val _billingPremium = MutableStateFlow(false)

    /**
     * Scadenza del mese regalo riscattato, in millisecondi epoch. 0 = nessuno.
     *
     * A differenza dell'abbonamento, questa la teniamo anche su disco: la
     * concede il server una volta sola e non c'è un BillingClient da
     * interrogare per riscoprirla, quindi senza rete l'utente perderebbe un
     * mese che ha già ricevuto. Resta comunque il server a deciderla: qui c'è
     * solo una copia con una scadenza dentro, che scade da sé.
     */
    private val _giftUntilMs = MutableStateFlow(storedGiftUntilMs())
    val giftUntilMs: StateFlow<Long> = _giftUntilMs.asStateFlow()

    /**
     * Il premium ha due fonti: l'abbonamento Play e il mese regalo.
     *
     * Sono indipendenti — un regalo non è un acquisto e non va confermato a
     * Google — quindi vale la somma, non l'ultima delle due che ha scritto.
     * Prima era un singolo MutableStateFlow, e un riscatto sarebbe stato
     * cancellato dalla prima queryExistingPurchases() che non trovava acquisti.
     */
    val isPremium: StateFlow<Boolean> =
        combine(_billingPremium, _giftUntilMs) { fromBilling, giftUntil ->
            fromBilling || giftUntil > System.currentTimeMillis()
        }.stateIn(
            scope,
            SharingStarted.Eagerly,
            // Il valore iniziale non può essere false a prescindere: stateIn
            // emette la prima combinazione sul dispatcher Main, cioè al giro
            // successivo del looper, e fino ad allora un utente con un regalo
            // attivo si vedrebbe negare le funzioni premium.
            storedGiftUntilMs() > System.currentTimeMillis()
        )

    init {
        // Il regalo è di un account, non del telefono. Senza questo, bastava
        // riscattare, uscire e rientrare con un altro utente per portarsi
        // dietro il mese: esattamente il giro che i vincoli sul server
        // esistono per impedire.
        FirebaseAuth.getInstance().addAuthStateListener {
            _giftUntilMs.value = storedGiftUntilMs()
            refreshGiftEntitlement()
        }
    }

    /**
     * Copia locale del regalo, ma solo se è di chi ha fatto l'accesso adesso.
     *
     * Un uid diverso (o assente) vale come nessun regalo: la copia resta su
     * disco e torna valida se quell'utente rientra, senza un secondo giro sul
     * server.
     */
    private fun storedGiftUntilMs(): Long {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (uid.isBlank() || prefs.getString(KEY_GIFT_UID, null) != uid) return 0L
        return prefs.getLong(KEY_GIFT_UNTIL_MS, 0L)
    }

    private val _metaDeckViewsUsed = MutableStateFlow(prefs.getInt(KEY_META_DECK_VIEWS, 0))
    private val _selectedHomeSpriteId = MutableStateFlow(prefs.getInt(KEY_HOME_SPRITE_ID, 0))
    val selectedHomeSpriteId: StateFlow<Int> = _selectedHomeSpriteId.asStateFlow()

    val homeSpriteIds: List<Int>
        get() = HOME_SPRITE_IDS

    val metaDeckViewsRemaining: Int
        get() = if (isPremium.value) Int.MAX_VALUE
                else (FREE_META_DECK_VIEWS - _metaDeckViewsUsed.value).coerceAtLeast(0)

    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products.asStateFlow()

    private val _purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.Idle)
    val purchaseState: StateFlow<PurchaseState> = _purchaseState.asStateFlow()

    /**
     * Guasto del servizio di fatturazione, o null se e' raggiungibile.
     *
     * Separato da [purchaseState] perche' risponde a una domanda diversa: non
     * "com'e' andato l'acquisto" ma "si puo' comprare adesso". Il primo merita
     * una snackbar, il secondo una riga spenta accanto ai piani.
     */
    private val _billingProblem = MutableStateFlow<BillingProblem?>(null)
    val billingProblem: StateFlow<BillingProblem?> = _billingProblem.asStateFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener { billingResult, purchases ->
            scope.launch { handlePurchasesUpdated(billingResult, purchases) }
        }
        // enableOneTimeProducts() NON e' un no-op: dalla 8.0 e' obbligatorio, e
        // senza di esso build() lancia IllegalArgumentException, uccidendo l'app
        // dentro Application.onCreate. enablePrepaidPlans() si aggiunge, non
        // sostituisce: serve agli abbonamenti prepagati.
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build()
        )
        // Sostituisce la riconnessione a mano: la libreria ristabilisce da se'
        // la connessione quando una chiamata arriva a servizio disconnesso.
        .enableAutoServiceReconnection()
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
                    clearBillingProblem()
                    scope.launch {
                        queryProducts()
                        queryExistingPurchases()
                    }
                } else {
                    // Prima ogni esito diverso da OK veniva ignorato: con
                    // BILLING_UNAVAILABLE o SERVICE_DISABLED l'utente non
                    // riceveva alcun segnale.
                    reportBillingUnavailable(result, "startConnection")
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

        // Da Play Billing 8 il listener riceve un QueryProductDetailsResult, non
        // piu' una List<ProductDetails>: si usa la forma con listener, che ha una
        // firma esplicita, invece dell'estensione suspend.
        val details = suspendCancellableCoroutine<List<ProductDetails>> { cont ->
            billingClient.queryProductDetailsAsync(params) { billingResult, queryResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    cont.resume(queryResult.productDetailsList)
                } else {
                    reportBillingUnavailable(billingResult, "queryProductDetails")
                    cont.resume(emptyList())
                }
            }
        }
        if (details.isNotEmpty()) {
            _products.value = details
        }
    }

    private suspend fun queryExistingPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val result = queryPurchasesSuspending(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            // Su errore NON si tocca lo stato: un problema di rete non deve
            // togliere il premium a chi ha pagato.
            reportBillingUnavailable(result.billingResult, "queryPurchases")
            return
        }

        val purchased = result.purchasesList.filter {
            it.purchaseState == Purchase.PurchaseState.PURCHASED
        }

        purchased.filterNot { it.isAcknowledged }.forEach { acknowledgePurchase(it) }

        updatePremiumStatus(purchased.isNotEmpty())
    }

    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails) {
        // Il base plan ha offerId vuoto; le eventuali offerte introduttive o di
        // prova hanno un offerId valorizzato. Prima si prendeva il PRIMO
        // dell'elenco, che non e' necessariamente il base plan: appena si
        // configura un'offerta in Play Console l'utente comprerebbe quella.
        // Stessa logica gia' usata da getBasePlanFormattedPrice().
        val offers = productDetails.subscriptionOfferDetails.orEmpty()
        val offerToken = (offers.firstOrNull { it.offerId.isNullOrEmpty() } ?: offers.firstOrNull())
            ?.offerToken ?: return

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
                val list = purchases.orEmpty()

                // Lo stato PENDING non veniva gestito: un acquisto in attesa di
                // conferma (contanti, bonifico, piani prepagati) lasciava la UI
                // bloccata su Loading per sempre.
                if (list.any { it.purchaseState == Purchase.PurchaseState.PENDING }) {
                    _purchaseState.value = PurchaseState.Pending
                }

                list.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    .forEach { purchase ->
                        // Il premium si concede SOLO dopo una conferma riuscita:
                        // prima veniva concesso comunque, e un ack fallito si
                        // traduceva in un rimborso automatico dopo 3 giorni.
                        if (acknowledgePurchase(purchase)) {
                            updatePremiumStatus(true)
                            _purchaseState.value = PurchaseState.Success
                        } else {
                            _purchaseState.value = PurchaseState.NotAcknowledged
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
                android.util.Log.w(
                    "PremiumManager",
                    "acquisto: codice ${billingResult.responseCode} ${billingResult.debugMessage}"
                )
                _purchaseState.value =
                    PurchaseState.Failed(billingProblemFor(billingResult.responseCode))
            }
        }
    }

    /**
     * Conferma l'acquisto a Google, ritentando in caso di errore.
     *
     * Prima l'esito veniva scartato e non c'era alcun retry. Se la conferma
     * fallisce (per esempio la rete cade subito dopo l'acquisto) Google rimborsa
     * automaticamente dopo 3 giorni: l'utente pagava, vedeva il premium attivo e
     * lo perdeva tre giorni dopo senza spiegazione.
     *
     * @return true se l'acquisto risulta confermato.
     */
    private suspend fun acknowledgePurchase(purchase: Purchase): Boolean {
        if (purchase.isAcknowledged) return true

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        repeat(ACK_MAX_ATTEMPTS) { attempt ->
            val result = acknowledgeSuspending(params)
            if (result.responseCode == BillingClient.BillingResponseCode.OK) return true

            // ITEM_NOT_OWNED significa rimborso o annullamento: ritentare e' inutile.
            if (result.responseCode == BillingClient.BillingResponseCode.ITEM_NOT_OWNED) return false

            if (attempt < ACK_MAX_ATTEMPTS - 1) {
                delay(ACK_RETRY_DELAY_MS * (attempt + 1))
            } else {
                // Solo log: questa funzione ha due chiamanti. Da
                // handlePurchasesUpdated l'utente ha comprato davvero, e li' il
                // fallimento diventa gia' un PurchaseState.Error con una frase
                // sua; da queryExistingPurchases siamo all'avvio, e non c'e'
                // nessun acquisto di cui annunciare il fallimento.
                android.util.Log.w(
                    "PremiumManager",
                    "acknowledgePurchase: codice ${result.responseCode} ${result.debugMessage}"
                )
            }
        }
        return false
    }

    /**
     * Wrapper coroutine su queryPurchasesAsync.
     *
     * Prima si usava l'estensione suspend di billing-ktx, che nella 9.x e'
     * compilata con metadata Kotlin 2.3.0 e non e' leggibile da Kotlin 2.0.21.
     * Si usa quindi l'artefatto Java e si adatta qui.
     */
    private suspend fun queryPurchasesSuspending(
        params: QueryPurchasesParams
    ): PurchasesResult = suspendCancellableCoroutine { cont ->
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            cont.resume(PurchasesResult(billingResult, purchases))
        }
    }

    /** Wrapper coroutine su acknowledgePurchase. Vedi [queryPurchasesSuspending]. */
    private suspend fun acknowledgeSuspending(
        params: AcknowledgePurchaseParams
    ): BillingResult = suspendCancellableCoroutine { cont ->
        billingClient.acknowledgePurchase(params) { billingResult ->
            cont.resume(billingResult)
        }
    }

    /** Esito di [queryPurchasesSuspending]. */
    private data class PurchasesResult(
        val billingResult: BillingResult,
        val purchasesList: List<Purchase>
    )

    /**
     * Registra che il servizio di fatturazione non e' raggiungibile.
     *
     * Prima questi guasti finivano in [PurchaseState.Error], che la schermata
     * Premium mostra come "Acquisto non riuscito: <messaggio di Google>". Ma
     * connessione caduta, prodotti non interrogabili e acquisti non rileggibili
     * capitano all'AVVIO dell'app, senza che nessuno abbia comprato niente:
     * l'errore restava nello stato e la snackbar partiva appena si apriva la
     * schermata. L'utente leggeva di un acquisto fallito che non aveva mai
     * tentato, per giunta con la frase inglese grezza di Google dentro.
     *
     * Ora sono due cose separate: qui la disponibilita' del servizio, in
     * [PurchaseState] solo l'esito di un acquisto davvero avviato.
     */
    private fun reportBillingUnavailable(result: BillingResult, operation: String) {
        android.util.Log.w(
            "PremiumManager",
            "$operation: codice ${result.responseCode} ${result.debugMessage}"
        )
        _billingProblem.value = billingProblemFor(result.responseCode)
    }

    /** Il servizio risponde: si cancella un eventuale guasto precedente. */
    private fun clearBillingProblem() {
        _billingProblem.value = null
    }

    /**
     * Riprova a connettersi, per il bottone nella schermata Premium.
     *
     * Azzera anche [retryCount]: i tentativi automatici si esauriscono dopo tre,
     * e senza questo un utente che riapre la schermata mezz'ora dopo non avrebbe
     * piu' alcun modo di far ritentare la connessione.
     */
    fun retryBillingConnection() {
        retryCount = 0
        connectAndQueryPurchases()
    }

    private fun updatePremiumStatus(entitledByBilling: Boolean) {
        _billingPremium.value = entitledByBilling
        // Su Firestore va lo stato che l'utente vede davvero, regalo incluso:
        // scriverci solo l'abbonamento direbbe "non premium" a chi il premium
        // ce l'ha per un mese.
        syncToFirestore(entitledByBilling || hasActiveGift())
    }

    /** true finché il mese regalo riscattato non è scaduto. */
    fun hasActiveGift(): Boolean = _giftUntilMs.value > System.currentTimeMillis()

    /**
     * Registra il mese regalo appena concesso dal server.
     *
     * La scadenza arriva sempre da lì: calcolarla sul telefono la renderebbe
     * spostabile con l'orologio di sistema.
     */
    fun applyGiftGrant(giftUntilMs: Long) {
        _giftUntilMs.value = giftUntilMs
        prefs.edit()
            .putLong(KEY_GIFT_UNTIL_MS, giftUntilMs)
            .putString(KEY_GIFT_UID, FirebaseAuth.getInstance().currentUser?.uid.orEmpty())
            .apply()
    }

    /**
     * Riallinea il regalo a quello che dice il server.
     *
     * Serve al caso opposto della copia locale: un regalo revocato, o un
     * riscatto fatto su un altro dispositivo dello stesso account. Se il server
     * non risponde la copia locale resta, perché un problema di rete non deve
     * togliere un mese già ricevuto.
     */
    fun refreshGiftEntitlement() {
        if (!GiftCodeRepository.isConfigured) return
        scope.launch {
            val status = GiftCodeRepository.fetchStatus() ?: return@launch
            applyGiftGrant(status.giftUntilMs ?: 0L)
        }
    }

    /**
     * Riporta l'entitlement su Firestore.
     *
     * Prima si usava update(), che FALLISCE se il documento utente non esiste o
     * non ha ancora il campo, e senza alcun addOnFailureListener: l'errore era
     * invisibile. set() con merge crea il campo quando manca, e il fallimento
     * viene almeno segnalato.
     *
     * Resta un dato scritto e mai riletto: l'entitlement autorevole richiede la
     * verifica lato server (vedi pokevault-proxy-worker/BILLING.md).
     */
    private fun syncToFirestore(isPremium: Boolean) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .set(mapOf("isPremium" to isPremium), SetOptions.merge())
            .addOnFailureListener { e ->
                android.util.Log.w("PremiumManager", "Sync isPremium su Firestore fallito", e)
            }
    }

    /**
     * Rilegge gli acquisti da Google.
     *
     * Va chiamata quando l'app torna in primo piano: prima l'unica query era
     * quella in init, quindi una disdetta, un rimborso o una scadenza restavano
     * invisibili per tutta la vita del processo.
     */
    fun refreshEntitlement() {
        expireGiftIfNeeded()
        refreshGiftEntitlement()
        scope.launch { queryExistingPurchases() }
    }

    /**
     * Azzera un regalo scaduto.
     *
     * Necessario perché isPremium è un combine: il tempo che passa non fa
     * emettere niente, quindi senza questa spinta un mese finito resterebbe
     * "attivo" finché l'app non viene chiusa. Il posto giusto è qui, che è il
     * punto già chiamato a ogni ritorno in primo piano.
     */
    private fun expireGiftIfNeeded() {
        if (_giftUntilMs.value != 0L && !hasActiveGift()) applyGiftGrant(0L)
    }

    fun canCreateDeck(currentDeckCount: Int): Boolean =
        isWithinFreeLimit(isPremium.value, currentDeckCount, FREE_DECK_LIMIT)

    fun canCreateAlbum(currentAlbumCount: Int): Boolean =
        isWithinFreeLimit(isPremium.value, currentAlbumCount, FREE_ALBUM_LIMIT)

    fun canCreateGoalAlbum(currentGoalAlbumCount: Int): Boolean =
        isWithinFreeLimit(isPremium.value, currentGoalAlbumCount, FREE_GOAL_ALBUM_LIMIT)

    fun canCreateWishlist(currentWishlistCount: Int): Boolean =
        isWithinFreeLimit(isPremium.value, currentWishlistCount, FREE_WISHLIST_LIMIT)

    fun canCreateTournament(currentTournamentCount: Int): Boolean =
        isWithinFreeLimit(isPremium.value, currentTournamentCount, FREE_TOURNAMENT_LIMIT)

    fun canViewMetaDeck(): Boolean =
        isWithinFreeLimit(isPremium.value, _metaDeckViewsUsed.value, FREE_META_DECK_VIEWS)

    fun canExportDecklist(): Boolean {
        return isPremium.value
    }

    fun canRunHandSimulator(deckId: String, currentDeckCount: Int): Boolean {
        if (isPremium.value) return true
        if (deckId.isBlank()) return false
        if (currentDeckCount != FREE_DECK_LIMIT) return false
        return getHandSimulatorRuns(deckId) < 1
    }

    fun consumeHandSimulatorRun(deckId: String) {
        if (isPremium.value || deckId.isBlank()) return
        val key = handSimulatorRunsKey(deckId)
        val currentRuns = prefs.getInt(key, 0)
        prefs.edit().putInt(key, currentRuns + 1).apply()
    }

    fun getHandSimulatorRuns(deckId: String): Int {
        if (deckId.isBlank()) return 0
        return prefs.getInt(handSimulatorRunsKey(deckId), 0)
    }

    fun canChooseHomeSprite(): Boolean {
        return isPremium.value
    }

    fun setSelectedHomeSpriteId(spriteId: Int) {
        if (!isPremium.value) return

        val validSpriteId = if (spriteId == 0 || HOME_SPRITE_IDS.contains(spriteId)) spriteId else return
        _selectedHomeSpriteId.value = validSpriteId
        prefs.edit().putInt(KEY_HOME_SPRITE_ID, validSpriteId).apply()
    }

    fun consumeMetaDeckView() {
        if (isPremium.value) return
        val newCount = _metaDeckViewsUsed.value + 1
        _metaDeckViewsUsed.value = newCount
        prefs.edit().putInt(KEY_META_DECK_VIEWS, newCount).apply()
    }

    private fun handSimulatorRunsKey(deckId: String): String {
        return "$KEY_HAND_SIM_RUN_PREFIX$deckId"
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

    /**
     * Esito di un acquisto **avviato dall'utente**, e nient'altro.
     *
     * I guasti del servizio che capitano all'avvio stanno in [billingProblem]:
     * mescolarli qui e' ciò che faceva comparire "Acquisto non riuscito" a chi
     * si limitava ad aprire la schermata.
     */
    sealed class PurchaseState {
        data object Idle : PurchaseState()
        data object Loading : PurchaseState()
        data object Success : PurchaseState()
        /** Acquisto avviato ma non ancora confermato da Google. */
        data object Pending : PurchaseState()
        /**
         * Pagato, ma la conferma a Google non è passata.
         *
         * Va detto all'utente perché senza conferma Google rimborsa da sé dopo
         * tre giorni: chi non riapre l'app perde il premium senza spiegazione.
         */
        data object NotAcknowledged : PurchaseState()
        /**
         * L'acquisto è fallito. Porta il motivo, non la frase.
         *
         * Prima portava una `String`, che era il `debugMessage` inglese di
         * Google inoltrato tale e quale: un utente italiano leggeva "Acquisto
         * non riuscito: Server is disconnected".
         */
        data class Failed(val problem: BillingProblem) : PurchaseState()
    }
}
