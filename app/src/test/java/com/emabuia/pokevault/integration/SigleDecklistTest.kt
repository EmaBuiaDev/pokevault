package com.emabuia.pokevault.integration

import android.app.Application
import com.emabuia.pokevault.BuildConfig
import com.emabuia.pokevault.data.remote.RepositoryProvider
import com.emabuia.pokevault.integration.EmulatorEnv.emu
import com.google.gson.JsonParser
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/**
 * Ogni sigla delle decklist porta al set giusto.
 *
 * Nelle decklist (Limitless, PTCGL) una carta e' "Air Balloon BLK 79": la
 * sigla del set e il numero. L'import passa la sigla a SetCodeMapper e cerca
 * la carta nel catalogo italiano; se la tabella delle sigle non conosce un set
 * nuovo, o ne scambia due, le carte entrano senza immagine o dall'altro set,
 * e nessun test unitario se ne accorge. E' successo con PBL, 30C e BLK/WHT.
 *
 * Qui per ogni espansione con una sigla (expansions.upstream_set_code su D1,
 * scritta dall'import del catalogo) si chiede alla funzione vera dell'import,
 * findExactItalianCard, una carta con quella sigla e un numero del set, e si
 * controlla che arrivi da quel set. Un set nuovo entra nel controllo da solo,
 * appena l'import del catalogo lo scrive su D1.
 *
 * Serve la rete: il catalogo dal worker, le sigle da D1 in sola lettura
 * (CLOUDFLARE_API_TOKEN). Non usa l'emulatore Firebase.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class SigleDecklistTest {

    private val token = System.getenv("CLOUDFLARE_API_TOKEN").orEmpty()

    @Before
    fun setUp() {
        EmulatorEnv.richiedi(
            BuildConfig.POKEWALLET_PROXY_ENABLED && BuildConfig.POKEWALLET_PROXY_URL.isNotBlank() &&
                BuildConfig.ITALIAN_CATALOG_URL.isNotBlank(),
            "Mancano POKEWALLET_PROXY_URL o ITALIAN_CATALOG_URL in local.properties: senza catalogo non si cerca niente"
        )
        EmulatorEnv.richiedi(
            token.isNotBlank(),
            "Manca CLOUDFLARE_API_TOKEN: le sigle dei set si leggono da D1"
        )
        RepositoryProvider.init(RuntimeEnvironment.getApplication())
    }

    private data class Espansione(val id: String, val nome: String, val sigla: String)

    /** Le espansioni pubblicate che hanno una sigla, lette da D1 (sola lettura). */
    private fun espansioniConSigla(): List<Espansione> {
        val url = URL("https://api.cloudflare.com/client/v4/accounts/$CF_ACCOUNT_ID/d1/database/$D1_DATABASE_ID/query")
        val sql = "SELECT id, name, upstream_set_code FROM expansions " +
            "WHERE upstream_set_code IS NOT NULL AND upstream_set_code <> '' AND published = 1 ORDER BY release_date DESC"
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
        }
        conn.outputStream.use { it.write("""{"sql":"$sql"}""".toByteArray()) }
        val body = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText()
        check(conn.responseCode == 200) { "D1 ha risposto ${conn.responseCode}: ${body.take(300)}" }
        val righe = JsonParser.parseString(body).asJsonObject["result"].asJsonArray[0].asJsonObject["results"].asJsonArray
        return righe.map { r ->
            val o = r.asJsonObject
            Espansione(o["id"].asString, o["name"].asString, o["upstream_set_code"].asString)
        }
    }

    /** Un numero di carta del set, preso a meta' della lista (le prime sono spesso uguali fra set). */
    private fun numeroDiUnaCarta(espansioneId: String): String? {
        val base = BuildConfig.POKEWALLET_PROXY_URL.trimEnd('/')
        val body = URL("$base/v1/expansions/$espansioneId/cards").readText()
        val numeri = JsonParser.parseString(body).asJsonObject["cards"].asJsonArray
            .mapNotNull { Regex("_IT_0*(\\d+)\\.").find(it.asJsonObject["cardId"].asString)?.groupValues?.get(1) }
        return numeri.getOrNull(numeri.size / 2)
    }

    @Test
    fun `ogni sigla delle decklist porta al suo set`() {
        val context = RuntimeEnvironment.getApplication()
        val espansioni = espansioniConSigla()
        check(espansioni.size >= 20) { "Da D1 arrivano solo ${espansioni.size} set con sigla: qualcosa non va nella lettura" }

        val problemi = emu(timeoutMs = 300_000) {
            espansioni.mapNotNull { e ->
                val numero = numeroDiUnaCarta(e.id) ?: return@mapNotNull "${e.sigla}: ${e.nome} (${e.id}) non ha carte numerate nel catalogo"
                val carta = RepositoryProvider.tcgRepository.findExactItalianCard(
                    setCode = e.sigla,
                    number = numero,
                    context = context
                )
                val trovataIn = carta?.set?.id?.substringBefore("__")
                when {
                    carta == null -> "${e.sigla} ${numero}: nessuna carta trovata (atteso ${e.nome}, ${e.id})"
                    !trovataIn.equals(e.id, ignoreCase = true) ->
                        "${e.sigla} ${numero}: trovata \"${carta.name}\" in $trovataIn, atteso ${e.nome} (${e.id})"
                    else -> null
                }
            }
        }

        if (problemi.isNotEmpty()) {
            fail(
                "${problemi.size} sigle su ${espansioni.size} non portano al set giusto " +
                    "(da aggiungere o correggere in SetCodeMapper):\n" + problemi.joinToString("\n") { "  - $it" }
            )
        }
    }

    private companion object {
        const val CF_ACCOUNT_ID = "e6c4d1ff864abf6dbcb4ec1e0de6b34a"
        const val D1_DATABASE_ID = "2a219637-d11d-42fe-b888-8b95f2d7e6f7"
    }
}
