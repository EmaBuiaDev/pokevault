package com.emabuia.pokevault.ui.album

import com.emabuia.pokevault.util.AlbumRow
import com.emabuia.pokevault.util.ChaseRow
import com.emabuia.pokevault.util.IllustratorRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Continua" in cima al Collector Lab: le tre cose piu' vicine al traguardo,
 * fra album, chase e artisti, con la regola del prossimo passo (meno carte
 * mancanti, non la percentuale piu' alta).
 */
class ContinueStepsTest {

    private fun album(id: String, used: Int, size: Int) = AlbumRow(
        id = id, name = id, description = "", theme = "", pokemonType = "", coverUrl = "",
        previewUrls = emptyList(), used = used, size = size, value = 0.0, createdAtSeconds = 0L
    )

    private fun chase(id: String, owned: Int, total: Int) =
        ChaseRow(id = id, name = id, criteriaLabel = "Set", owned = owned, total = total, createdAtSeconds = 0L)

    private fun artist(key: String, owned: Int, total: Int) = IllustratorRow(
        key = key, displayName = key, owned = owned, total = total,
        expansionCount = 1, previewUrls = emptyList(), isFollowed = false
    )

    @Test
    fun `le tre piu' vicine, mescolando album chase e artisti`() {
        val steps = pickContinueSteps(
            albums = listOf(album("raccoglitore", used = 7, size = 9)),          // ne mancano 2
            chases = listOf(chase("set", owned = 9, total = 10), chase("lontano", owned = 1, total = 200)),
            illustrators = listOf(artist("arita", owned = 575, total = 579))      // ne mancano 4
        )
        assertEquals(listOf("set", "raccoglitore", "arita"), steps.map { it.id })
        assertEquals(listOf(NextStepKind.CHASE, NextStepKind.ALBUM, NextStepKind.ILLUSTRATOR), steps.map { it.kind })
    }

    @Test
    fun `album vuoti, pieni o senza tasche non sono un passo`() {
        val steps = pickContinueSteps(
            albums = listOf(album("vuoto", 0, 9), album("pieno", 9, 9), album("senza", 0, 0)),
            chases = emptyList(),
            illustrators = emptyList()
        )
        assertTrue(steps.isEmpty())
    }

    @Test
    fun `mai piu' di tre`() {
        val steps = pickContinueSteps(
            albums = (1..5).map { album("a$it", used = 1, size = 10) },
            chases = emptyList(),
            illustrators = emptyList()
        )
        assertEquals(3, steps.size)
    }

    @Test
    fun `un album di carte da trovare conta quelle trovate, non le tasche occupate`() {
        val pieno = AlbumRow(
            id = "da-trovare", name = "da-trovare", description = "", theme = "", pokemonType = "", coverUrl = "",
            previewUrls = emptyList(), used = 9, size = 9, value = 0.0, createdAtSeconds = 0L, found = 7
        )
        val step = pickContinueSteps(listOf(pieno), emptyList(), emptyList()).single()
        assertEquals(7, step.owned)
        assertEquals(9, step.total)
        assertEquals(2, step.missing)
    }

    @Test
    fun `un album di sole carte da trovare non e' ancora iniziato`() {
        val niente = AlbumRow(
            id = "niente", name = "niente", description = "", theme = "", pokemonType = "", coverUrl = "",
            previewUrls = emptyList(), used = 5, size = 9, value = 0.0, createdAtSeconds = 0L, found = 0
        )
        assertTrue(pickContinueSteps(listOf(niente), emptyList(), emptyList()).isEmpty())
    }
}
