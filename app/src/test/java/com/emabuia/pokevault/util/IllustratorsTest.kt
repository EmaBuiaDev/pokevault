package com.emabuia.pokevault.util

import org.junit.Assert.*
import org.junit.Test

/**
 * Nomi, conti e ordinamenti della sezione illustratori.
 *
 * I casi qui sotto non sono inventati: vengono dai 388 nomi veri che
 * `/v1/illustrators` restituisce sul catalogo di produzione. E' la parte che si
 * sbaglia senza accorgersene, perche' un nome spezzato male non fa fallire
 * niente -- crea solo un illustratore che non esiste, con le sue carte rubate a
 * quello vero.
 */
class IllustratorsTest {

    // ── Separazione dei crediti ───────────────────────────────────────────

    @Test
    fun `il piu separa due autori`() {
        assertEquals(
            listOf("Shinji Higuchi", "Sachiko Eba"),
            IllustratorNames.credits("Shinji Higuchi + Sachiko Eba")
        )
    }

    @Test
    fun `la barra separa due autori e il ruolo sparisce`() {
        assertEquals(
            listOf("Kent Kanetsuna", "Shinji Higuchi"),
            IllustratorNames.credits("Kent Kanetsuna/Direc. Shinji Higuchi")
        )
    }

    /**
     * Il caso che ha fatto cambiare la regola: "&" sembrerebbe il separatore
     * piu' ovvio, ma nel catalogo compare solo dentro questo credito unico.
     * Spezzandolo nascerebbero due illustratori inventati.
     */
    @Test
    fun `la e commerciale non separa niente`() {
        assertEquals(
            listOf("Illus. & Direc. The Pokémon Company Art Team"),
            IllustratorNames.credits("Illus. & Direc. The Pokémon Company Art Team")
        )
    }

    @Test
    fun `la virgola non separa, i nomi traslitterati la usano`() {
        assertEquals(listOf("Saitou, Kouki"), IllustratorNames.credits("Saitou, Kouki"))
    }

    @Test
    fun `niente illustratore, nessun credito`() {
        assertEquals(emptyList<String>(), IllustratorNames.credits(null))
        assertEquals(emptyList<String>(), IllustratorNames.credits("   "))
    }

    @Test
    fun `gli spazi multipli collassano`() {
        assertEquals(listOf("Ken Sugimori"), IllustratorNames.credits("  Ken   Sugimori  "))
    }

    // ── Chiavi ────────────────────────────────────────────────────────────

    @Test
    fun `le tre doppie grafie del catalogo cadono insieme senza alias`() {
        assertEquals(IllustratorNames.keyOf("takuyoa"), IllustratorNames.keyOf("Takuyoa"))
        assertEquals(IllustratorNames.keyOf("K. Hoshiba"), IllustratorNames.keyOf("K Hoshiba"))
        assertEquals(IllustratorNames.keyOf("Zu-ka"), IllustratorNames.keyOf("Zu-Ka"))
    }

    @Test
    fun `gli accenti si ripiegano`() {
        assertEquals(IllustratorNames.keyOf("Pokémon"), IllustratorNames.keyOf("Pokemon"))
    }

    @Test
    fun `due persone diverse restano due chiavi`() {
        assertNotEquals(IllustratorNames.keyOf("Ken Sugimori"), IllustratorNames.keyOf("Kouki Saitou"))
    }

    @Test
    fun `una carta a quattro mani conta per entrambi`() {
        val keys = IllustratorNames.keysOf("Shinji Higuchi + Noriko Takaya")
        assertEquals(2, keys.size)
        assertTrue(IllustratorNames.keyOf("Shinji Higuchi") in keys)
        assertTrue(IllustratorNames.keyOf("Noriko Takaya") in keys)
    }

    @Test
    fun `lo stesso autore ripetuto nel credito non si sdoppia`() {
        assertEquals(1, IllustratorNames.keysOf("Shinji Higuchi/Direc. Shinji Higuchi").size)
    }

    // ── Nome da mostrare ──────────────────────────────────────────────────

    @Test
    fun `fra due grafie vince quella su piu carte`() {
        assertEquals(
            "K. Hoshiba",
            IllustratorNames.bestDisplayName(mapOf("K Hoshiba" to 2, "K. Hoshiba" to 9))
        )
    }

    @Test
    fun `a pari merito il nome mostrato non balla fra un caricamento e l altro`() {
        val a = IllustratorNames.bestDisplayName(mapOf("Zu-ka" to 3, "Zu-Ka" to 3))
        val b = IllustratorNames.bestDisplayName(mapOf("Zu-Ka" to 3, "Zu-ka" to 3))
        assertEquals(a, b)
    }

    // ── Righe e avanzamento ───────────────────────────────────────────────

    private fun entry(
        key: String,
        total: Int,
        expansions: Int = 1
    ) = IllustratorEntry(
        key = key,
        displayName = key,
        rawNames = listOf(key),
        cardApiIds = (1..total).map { "ita:$key:$it" },
        previewUrls = emptyList(),
        expansionCount = expansions
    )

    @Test
    fun `l avanzamento conta solo le carte di quell illustratore`() {
        val rows = Illustrators.rows(
            entries = listOf(entry("arita", 4), entry("sugimori", 2)),
            ownedApiIds = setOf("ita:arita:1", "ita:arita:3", "ita:sugimori:1", "ita:altro:9"),
            followedKeys = emptySet()
        )
        assertEquals(2, rows.first { it.key == "arita" }.owned)
        assertEquals(4, rows.first { it.key == "arita" }.total)
        assertEquals(1, rows.first { it.key == "sugimori" }.owned)
    }

    @Test
    fun `un illustratore senza carte possedute non divide per zero`() {
        val row = Illustrators.rows(listOf(entry("vuoto", 0)), emptySet(), emptySet()).single()
        assertEquals(0f, row.percent, 0.001f)
        assertFalse(row.isComplete)
        assertEquals(0, row.missing)
    }

    @Test
    fun `completo quando le ha tutte`() {
        val row = Illustrators.rows(
            listOf(entry("arita", 2)),
            setOf("ita:arita:1", "ita:arita:2"),
            emptySet()
        ).single()
        assertTrue(row.isComplete)
        assertEquals(100f, row.percent, 0.001f)
    }

    // ── Ordinamenti ───────────────────────────────────────────────────────

    private fun row(
        key: String,
        owned: Int,
        total: Int,
        followed: Boolean = false
    ) = IllustratorRow(
        key = key,
        displayName = key,
        owned = owned,
        total = total,
        expansionCount = 1,
        previewUrls = emptyList(),
        isFollowed = followed
    )

    @Test
    fun `i completati scendono in fondo`() {
        val sorted = Illustrators.sort(
            listOf(row("finito", 10, 10), row("quasi", 8, 10), row("appena", 1, 10)),
            IllustratorSort.CLOSEST
        )
        assertEquals(listOf("quasi", "appena", "finito"), sorted.map { it.key })
    }

    @Test
    fun `per numero di carte vince chi ne ha disegnate di piu`() {
        val sorted = Illustrators.sort(
            listOf(row("piccolo", 0, 5), row("grande", 0, 500)),
            IllustratorSort.CARDS
        )
        assertEquals(listOf("grande", "piccolo"), sorted.map { it.key })
    }

    // ── Ricerca ───────────────────────────────────────────────────────────

    @Test
    fun `si cerca senza accenti e senza maiuscole`() {
        val rows = listOf(row(IllustratorNames.keyOf("Kouki Saitou"), 0, 1))
        assertEquals(1, Illustrators.filter(rows, "KOUKI").size)
        assertEquals(1, Illustrators.filter(rows, "saitou").size)
        assertEquals(0, Illustrators.filter(rows, "sugimori").size)
    }

    @Test
    fun `ricerca vuota non filtra`() {
        val rows = listOf(row("a", 0, 1), row("b", 0, 1))
        assertEquals(2, Illustrators.filter(rows, "   ").size)
    }
}
