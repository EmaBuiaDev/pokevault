package com.emabuia.pokevault.ui.competitive

import com.emabuia.pokevault.util.AppLocale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * I testi dell'Hand Simulator in italiano non usano il gergo dei giocatori
 * competitivi.
 *
 * Fino alla 3.1.8 c'erano "Out T1", "Setup T2", "Starter rate", "Key card",
 * "Run": chi non segue il competitivo non capiva cosa misurassero. Il test
 * legge tutti i testi della sezione (getter e funzioni) e cerca le sigle,
 * cosi' un testo nuovo scritto di fretta non le reintroduce.
 */
class HandSimulatorWordingTest {

    private var previous = AppLocale.Language.IT

    @Before
    fun setUp() {
        previous = AppLocale.current
        setLanguage(AppLocale.Language.IT)
    }

    @After
    fun tearDown() {
        setLanguage(previous)
    }

    /** Il setter e' privato: l'app cambia lingua solo passando dalle preferenze. */
    private fun setLanguage(language: AppLocale.Language) {
        AppLocale::class.java.getDeclaredMethod("setCurrent", AppLocale.Language::class.java)
            .apply { isAccessible = true }
            .invoke(AppLocale, language)
    }

    /** Ogni testo della sezione, chiamando le funzioni con valori d'esempio. */
    private fun allHandSimulatorTexts(): Map<String, String> {
        val texts = mutableMapOf<String, String>()
        AppLocale::class.java.declaredMethods
            .filter { it.name.startsWith("getHand") || it.name.startsWith("hand") }
            .filter { it.returnType == String::class.java }
            .forEach { method ->
                val args = method.parameterTypes.map { type ->
                    when (type) {
                        Int::class.javaPrimitiveType -> 2
                        String::class.java -> "2"
                        else -> return@forEach
                    }
                }
                method.isAccessible = true
                texts[method.name] = method.invoke(AppLocale, *args.toTypedArray()) as String
            }
        return texts
    }

    private val jargon = listOf(
        Regex("""\bT[12]\b"""),
        Regex("""\bouts?\b""", RegexOption.IGNORE_CASE),
        Regex("""\bsetup\b""", RegexOption.IGNORE_CASE),
        Regex("""\bstarter\b""", RegexOption.IGNORE_CASE),
        Regex("""\bkey card""", RegexOption.IGNORE_CASE),
        Regex("""\bruns?\b""", RegexOption.IGNORE_CASE),
        Regex("""\bbrick""", RegexOption.IGNORE_CASE),
        Regex("""\bsupporter""", RegexOption.IGNORE_CASE),
        Regex("""\bvault\b""", RegexOption.IGNORE_CASE)
    )

    @Test
    fun `i testi italiani non hanno sigle da giocatore esperto`() {
        val texts = allHandSimulatorTexts()
        assertTrue("dovrebbero esserci decine di testi, trovati ${texts.size}", texts.size > 50)

        val offenders = texts.flatMap { (name, text) ->
            jargon.filter { it.containsMatchIn(text) }.map { "$name: \"$text\" (${it.pattern})" }
        }
        assertEquals("Testi con gergo:\n" + offenders.joinToString("\n"), emptyList<String>(), offenders)
    }

    @Test
    fun `le medie si scrivono con la virgola in italiano`() {
        assertEquals("2,13", 2.1349.roundTo2Decimals(italian = true))
        assertEquals("2.13", 2.1349.roundTo2Decimals(italian = false))
        assertEquals("1", 1.0.roundTo2Decimals(italian = true))
    }
}
