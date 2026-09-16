package com.emabuia.pokevault.data.billing

import com.android.billingclient.api.BillingClient
import com.emabuia.pokevault.data.billing.PremiumManager.Companion.BillingProblem
import com.emabuia.pokevault.data.billing.PremiumManager.Companion.billingProblemFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Traduzione dei response code di BillingClient nei guasti mostrati all'utente.
 *
 * Prima ogni esito diverso da OK finiva in PurchaseState.Error e la schermata
 * Premium lo mostrava come "Acquisto non riuscito: <debugMessage di Google>".
 * Due difetti in uno: l'utente leggeva di un acquisto fallito che non aveva mai
 * tentato — quegli errori nascono all'avvio dell'app — e lo leggeva in inglese.
 *
 * Questa e' l'unica parte di quella logica verificabile senza un BillingClient
 * e un Context, ed e' il motivo per cui e' una funzione pura in companion.
 */
class BillingProblemMappingTest {

    @Test
    fun `servizio disconnesso non viene confuso con assenza di rete`() {
        // E' il caso che si vede in debug: la connessione al servizio cade
        // perche' la firma dell'APK non e' quella che Play conosce. Dire
        // "controlla la rete" manderebbe l'utente a cercare nel posto sbagliato.
        assertEquals(
            BillingProblem.DISCONNECTED,
            billingProblemFor(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
        )
        assertNotEquals(
            BillingProblem.NETWORK,
            billingProblemFor(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
        )
    }

    @Test
    fun `i codici di rete finiscono su NETWORK`() {
        assertEquals(
            BillingProblem.NETWORK,
            billingProblemFor(BillingClient.BillingResponseCode.NETWORK_ERROR)
        )
        assertEquals(
            BillingProblem.NETWORK,
            billingProblemFor(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
        )
    }

    @Test
    fun `Play assente o account senza fatturazione e' UNAVAILABLE`() {
        assertEquals(
            BillingProblem.UNAVAILABLE,
            billingProblemFor(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE)
        )
    }

    @Test
    fun `prodotti o firma sbagliati sono un problema di configurazione`() {
        assertEquals(
            BillingProblem.MISCONFIGURED,
            billingProblemFor(BillingClient.BillingResponseCode.DEVELOPER_ERROR)
        )
        assertEquals(
            BillingProblem.MISCONFIGURED,
            billingProblemFor(BillingClient.BillingResponseCode.ITEM_UNAVAILABLE)
        )
    }

    @Test
    fun `un codice non previsto non viene scambiato per qualcosa di specifico`() {
        // Meglio un messaggio generico che uno preciso e sbagliato: suggerire
        // di controllare la rete quando il problema e' un altro fa perdere
        // tempo a chi ci prova.
        assertEquals(
            BillingProblem.OTHER,
            billingProblemFor(BillingClient.BillingResponseCode.ERROR)
        )
        assertEquals(BillingProblem.OTHER, billingProblemFor(9999))
    }
}
