package com.emabuia.pokevault.ui.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.data.model.Deck
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale

/**
 * Quali deck mostra l'elenco.
 *
 * Un deck di prova usa carte che l'utente non possiede, uno di collezione no:
 * sono due cose diverse -- uno si porta a un torneo, l'altro no -- e mescolati
 * in un elenco solo, con qualche import alle spalle, non si ritrovava piu'
 * niente.
 */
enum class DeckListFilter {
    ALL,
    COLLECTION,
    TEST;

    fun accepts(deck: Deck): Boolean = when (this) {
        ALL -> true
        COLLECTION -> !deck.deckOnly
        TEST -> deck.deckOnly
    }
}

/** Una riga dell'elenco: un'intestazione di sezione o un deck. */
sealed interface DeckListRow {
    val key: String

    data class Header(val label: String, val count: Int) : DeckListRow {
        override val key: String get() = "header_$label"
    }

    data class Item(val deck: Deck) : DeckListRow {
        override val key: String get() = deck.id
    }
}

/**
 * Le righe da mostrare per [filter].
 *
 * Su "Tutti" i deck si dividono in due sezioni, prima quelli di collezione:
 * se l'utente non ha deck di prova, o solo quelli, le intestazioni non
 * servono e non si mettono. Dentro ogni sezione resta l'ordine di Firestore.
 */
fun buildDeckListRows(decks: List<Deck>, filter: DeckListFilter): List<DeckListRow> {
    if (filter != DeckListFilter.ALL) {
        return decks.filter(filter::accepts).map { DeckListRow.Item(it) }
    }

    val (test, collection) = decks.partition { it.deckOnly }
    if (test.isEmpty() || collection.isEmpty()) return decks.map { DeckListRow.Item(it) }

    return buildList {
        add(DeckListRow.Header(AppLocale.deckFilterCollection, collection.size))
        collection.forEach { add(DeckListRow.Item(it)) }
        add(DeckListRow.Header(AppLocale.deckFilterTest, test.size))
        test.forEach { add(DeckListRow.Item(it)) }
    }
}

@Composable
fun DeckListFilterBar(
    selected: DeckListFilter,
    decks: List<Deck>,
    onSelect: (DeckListFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val testCount = decks.count { it.deckOnly }
    val options = listOf(
        DeckListFilter.ALL to (AppLocale.deckFilterAll to decks.size),
        DeckListFilter.COLLECTION to (AppLocale.deckFilterCollection to decks.size - testCount),
        DeckListFilter.TEST to (AppLocale.deckFilterTest to testCount)
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.card)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (filter, labelAndCount) ->
            val (label, count) = labelAndCount
            val isSelected = filter == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) AppColors.blue else Color.Transparent)
                    .clickable { onSelect(filter) }
                    .padding(vertical = 9.dp, horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$label ($count)",
                    color = if (isSelected) Color.White else AppColors.textMuted,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun DeckListSectionHeader(label: String, count: Int) {
    Text(
        text = "$label · $count",
        color = AppColors.textMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}
