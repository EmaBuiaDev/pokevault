package com.emabuia.pokevault.data.billing

import com.emabuia.pokevault.data.billing.GiftCodeRepository.RedeemRejection
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Contratto fra il Worker e l'app sui motivi di rifiuto di un riscatto.
 *
 * Le sigle sono l'unica cosa che attraversa il confine: il Worker le decide in
 * pokevault-proxy-worker/src/gift.ts (tipo GiftRejection) e qui diventano il
 * messaggio che l'utente legge. Se una sigla cambia di là e non di qua, il
 * rifiuto scivola su UNKNOWN e l'utente vede "non riesco a contattare il
 * server" quando il server aveva risposto benissimo — un errore che in
 * esecuzione sembra un problema di rete e manda a cercare nel posto sbagliato.
 */
class GiftRejectionMappingTest {

    /** Le stesse stringhe del tipo GiftRejection in src/gift.ts, nello stesso ordine. */
    private val wireContract = mapOf(
        "code_missing" to RedeemRejection.CODE_MISSING,
        "code_not_found" to RedeemRejection.CODE_NOT_FOUND,
        "code_disabled" to RedeemRejection.CODE_DISABLED,
        "code_expired" to RedeemRejection.CODE_EXPIRED,
        "code_exhausted" to RedeemRejection.CODE_EXHAUSTED,
        "own_code" to RedeemRejection.OWN_CODE,
        "already_redeemed" to RedeemRejection.ALREADY_REDEEMED,
        "device_already_redeemed" to RedeemRejection.DEVICE_ALREADY_REDEEMED,
        "rate_limited" to RedeemRejection.RATE_LIMITED
    )

    @Test
    fun `ogni sigla del worker ha il suo motivo`() {
        wireContract.forEach { (wire, expected) ->
            assertEquals(wire, expected, RedeemRejection.fromWire(wire))
        }
    }

    @Test
    fun `ogni motivo tranne UNKNOWN e' raggiungibile dal worker`() {
        val mapped = wireContract.values.toSet()
        val unreachable = RedeemRejection.entries.filter {
            it != RedeemRejection.UNKNOWN && it !in mapped
        }
        assertEquals(
            "motivi che nessuna risposta del Worker può produrre: $unreachable",
            emptyList<RedeemRejection>(),
            unreachable
        )
    }

    @Test
    fun `una sigla sconosciuta o assente non finisce in un motivo sbagliato`() {
        assertEquals(RedeemRejection.UNKNOWN, RedeemRejection.fromWire(null))
        assertEquals(RedeemRejection.UNKNOWN, RedeemRejection.fromWire(""))
        assertEquals(RedeemRejection.UNKNOWN, RedeemRejection.fromWire("codice_scaduto"))
    }
}
