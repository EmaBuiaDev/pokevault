package com.emabuia.pokevault.ui.album

import com.emabuia.pokevault.util.ChaseRow
import com.emabuia.pokevault.util.IllustratorRow
import org.junit.Assert.*
import org.junit.Test

/**
 * Il riquadro in cima al Collector Lab: quale traguardo proporre.
 *
 * Si sceglie per carte mancanti, non per percentuale: un artista da 579 carte
 * non arrivera' mai al 90%, ma se gliene mancano due e' la cosa piu' facile da
 * chiudere oggi.
 */
class NextStepTest {

    private fun chase(id: String, owned: Int, total: Int) =
        ChaseRow(id = id, name = id, criteriaLabel = "Set", owned = owned, total = total, createdAtSeconds = 0L)

    private fun artist(key: String, owned: Int, total: Int) = IllustratorRow(
        key = key, displayName = key, owned = owned, total = total,
        expansionCount = 1, previewUrls = emptyList(), isFollowed = false
    )

    @Test
    fun `vince il traguardo con meno carte mancanti, non la percentuale piu alta`() {
        val step = pickNextStep(
            chases = listOf(chase("set", owned = 9, total = 10)),        // 90%, ne manca 1
            illustrators = listOf(artist("arita", owned = 577, total = 579)) // 99%, ne mancano 2
        )
        assertEquals("set", step?.id)
        assertEquals(NextStepKind.CHASE, step?.kind)
    }

    @Test
    fun `un artista enorme quasi finito batte un chase appena iniziato`() {
        val step = pickNextStep(
            chases = listOf(chase("set", owned = 1, total = 200)),
            illustrators = listOf(artist("arita", owned = 575, total = 579))
        )
        assertEquals(NextStepKind.ILLUSTRATOR, step?.kind)
        assertEquals(4, step?.missing)
    }

    @Test
    fun `i completati non sono un prossimo passo`() {
        val step = pickNextStep(
            chases = listOf(chase("fatto", owned = 10, total = 10)),
            illustrators = listOf(artist("finito", owned = 3, total = 3))
        )
        assertNull(step)
    }

    @Test
    fun `quello che non e' iniziato non e' un traguardo vicino`() {
        val step = pickNextStep(
            chases = listOf(chase("vuoto", owned = 0, total = 5)),
            illustrators = listOf(artist("mai", owned = 0, total = 2))
        )
        assertNull(step)
    }

    @Test
    fun `niente di niente, niente riquadro`() {
        assertNull(pickNextStep(emptyList(), emptyList()))
    }
}
