package com.emabuia.pokevault.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

/**
 * Lanciata quando una richiesta a Limitless non parte, o torna 429.
 *
 * E' una IOException perche' deve poter essere sollevata da dentro un
 * [Interceptor] di OkHttp: e' l'unico modo di fermare una chiamata prima che
 * tocchi la rete senza far sembrare il fallimento un errore di programmazione.
 */
class LimitlessRateLimitException(
    /** Fra quanti secondi ha senso riprovare. */
    val retryAfterSeconds: Long
) : IOException("Limitless rate limit: riprovare fra ${retryAfterSeconds}s")

/**
 * Contatore delle richieste verso l'API Limitless.
 *
 * L'API concede **50 richieste ogni 5 minuti** per client, e lo dichiara nella
 * risposta con l'header `RateLimit: "50-in-5min"; r=49; t=300`. Superata la
 * soglia risponde 429 e da quel momento ogni sezione meta dell'app smette di
 * funzionare finche' la finestra non si riapre.
 *
 * Il limite era facile da superare senza accorgersene: aprire il Deck Lab
 * costava una trentina di richieste fra archetipi e tornei, e bastava un cambio
 * di formato o un refresh per arrivare a 429 — dal punto di vista dell'utente,
 * "la sezione meta ogni tanto non carica".
 *
 * Questo oggetto tiene due conti in parallelo:
 *  - una finestra scorrevole locale, che sa anche delle richieste ancora in
 *    volo (l'header di una risposta arriva sempre troppo tardi per le sei
 *    partite insieme a lei);
 *  - il contatore vero del server, letto dall'header, che e' l'unico a sapere
 *    delle richieste fatte prima che il processo partisse.
 *
 * Vale il piu' prudente dei due.
 */
object LimitlessRateLimiter {

    private const val WINDOW_MS = 5 * 60 * 1000L
    private const val QUOTA = 50

    /**
     * Richieste che non spendiamo mai in un caricamento automatico.
     *
     * Servono a lasciare aperta la strada al gesto dell'utente: se una schermata
     * consumasse l'intera finestra, il tocco su "aggiorna" subito dopo
     * troverebbe solo un 429.
     */
    private const val RESERVE = 6

    private val lock = Any()

    /** Istanti delle nostre richieste recenti, per la finestra scorrevole. */
    private val recent = ArrayDeque<Long>()

    private var serverRemaining: Int? = null
    private var serverResetAtMs: Long = 0L
    private var blockedUntilMs: Long = 0L

    /**
     * Prova a prendere uno slot. `false` significa "non chiamare adesso".
     *
     * Chi riceve false non deve riprovare in un ciclo: deve rinunciare e
     * mostrare quello che ha, perche' la finestra si riapre fra minuti, non
     * fra millisecondi.
     */
    fun tryAcquire(): Boolean = synchronized(lock) {
        val now = System.currentTimeMillis()
        if (now < blockedUntilMs) return false

        pruneLocked(now)

        if (remainingLocked(now) <= RESERVE) {
            Timber.w("Limitless: finestra quasi esaurita, richiesta non inviata")
            return false
        }

        recent.addLast(now)
        true
    }

    /** Aggiorna il contatore con quello che ha detto il server. */
    fun onHeaders(rateLimitHeader: String?) {
        if (rateLimitHeader.isNullOrBlank()) return

        // Formato: "50-in-5min"; r=49; t=300
        val remaining = REMAINING_REGEX.find(rateLimitHeader)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val resetIn = RESET_REGEX.find(rateLimitHeader)?.groupValues?.getOrNull(1)?.toLongOrNull()

        synchronized(lock) {
            val now = System.currentTimeMillis()
            if (remaining != null) serverRemaining = remaining
            if (resetIn != null) serverResetAtMs = now + resetIn * 1000L
        }
    }

    /** Il server ha risposto 429: da qui in poi si aspetta. */
    fun onTooManyRequests(retryAfterSeconds: Long?) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val waitMs = (retryAfterSeconds?.times(1000L))
                ?: (serverResetAtMs - now).takeIf { it > 0 }
                ?: WINDOW_MS
            blockedUntilMs = max(blockedUntilMs, now + waitMs)
            serverRemaining = 0
            Timber.w("Limitless: 429, in pausa per ${waitMs / 1000}s")
        }
    }

    /** Secondi che mancano prima che abbia senso riprovare. 0 se si puo' gia'. */
    fun retryAfterSeconds(): Long = synchronized(lock) {
        val now = System.currentTimeMillis()
        if (now < blockedUntilMs) {
            return ((blockedUntilMs - now) / 1000L) + 1
        }

        pruneLocked(now)
        if (remainingLocked(now) > RESERVE) return 0

        // La finestra si libera quando scade la richiesta piu' vecchia.
        val oldest = recent.firstOrNull() ?: return 0
        (((oldest + WINDOW_MS) - now) / 1000L).coerceAtLeast(1L)
    }

    /** Quante richieste possiamo ancora spendere adesso, riserva esclusa. */
    fun availableNow(): Int = synchronized(lock) {
        val now = System.currentTimeMillis()
        if (now < blockedUntilMs) return 0
        pruneLocked(now)
        (remainingLocked(now) - RESERVE).coerceAtLeast(0)
    }

    /** Solo per i test: riporta il contatore allo stato iniziale. */
    internal fun reset() = synchronized(lock) {
        recent.clear()
        serverRemaining = null
        serverResetAtMs = 0L
        blockedUntilMs = 0L
    }

    private fun pruneLocked(now: Long) {
        while (recent.isNotEmpty() && now - recent.first() >= WINDOW_MS) {
            recent.removeFirst()
        }
    }

    /**
     * Il minore fra quello che risulta a noi e quello che risulta al server.
     *
     * Il conto del server si ignora quando la sua finestra e' scaduta: da quel
     * momento `r` descrive una finestra che non esiste piu'.
     */
    private fun remainingLocked(now: Long): Int {
        val local = QUOTA - recent.size
        val server = serverRemaining?.takeIf { now < serverResetAtMs } ?: QUOTA
        return min(local, server)
    }

    private val REMAINING_REGEX = Regex("""\br=(\d+)""")
    private val RESET_REGEX = Regex("""\bt=(\d+)""")
}

/**
 * Fa passare ogni chiamata a Limitless dal [LimitlessRateLimiter].
 *
 * Sta nel client OkHttp e non nei singoli metodi del repository perche' una
 * regola che si puo' dimenticare di applicare non e' una regola: qualunque
 * endpoint venga aggiunto in futuro e' coperto senza doverselo ricordare.
 */
class LimitlessRateLimitInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!LimitlessRateLimiter.tryAcquire()) {
            throw LimitlessRateLimitException(LimitlessRateLimiter.retryAfterSeconds())
        }

        val response = chain.proceed(chain.request())
        LimitlessRateLimiter.onHeaders(response.header("RateLimit"))

        if (response.code == 429) {
            val retryAfter = response.header("Retry-After")?.toLongOrNull()
            response.close()
            LimitlessRateLimiter.onTooManyRequests(retryAfter)
            throw LimitlessRateLimitException(LimitlessRateLimiter.retryAfterSeconds())
        }

        return response
    }
}
