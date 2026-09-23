package com.emabuia.pokevault.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.emabuia.pokevault.data.model.Deck
import com.emabuia.pokevault.util.PokemonSpriteResolver

/**
 * I Pokemon che rappresentano un mazzo, in fila.
 *
 * Mostra solo le copertine **scelte a mano**, non la selezione automatica:
 * quella richiede di risolvere i nomi delle carte, cioe' di avere in mano
 * l'intera collezione, e le schermate del Match Log non la caricano apposta
 * (vedi il commento in CompetitiveHubScreen). Qui non costa niente --
 * `chosenSpriteCovers` e' un controllo sul prefisso dell'indirizzo, nessun
 * asset da leggere -- e un mazzo senza copertine scelte semplicemente non
 * disegna niente, lasciando il posto a quello che c'era prima.
 */
@Composable
fun DeckSpriteRow(
    deck: Deck?,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val sprites = deck?.chosenSpriteCovers().orEmpty()
    if (sprites.isEmpty()) return

    SpriteRow(sprites = sprites, size = size, modifier = modifier)
}

/** Il disegno vero e proprio, condiviso dalle due porte d'ingresso. */
@Composable
private fun SpriteRow(
    sprites: List<String>,
    size: Dp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        // Leggermente sovrapposti: due sprite affiancati a questa dimensione
        // sembrano due cose separate, sovrapposti si leggono come una coppia.
        horizontalArrangement = Arrangement.spacedBy(-(size / 5)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        sprites.forEach { url ->
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size)
            )
        }
    }
}

/** Vero quando [DeckSpriteRow] disegnerebbe qualcosa. */
fun Deck?.hasChosenSprites(): Boolean = !this?.chosenSpriteCovers().isNullOrEmpty()

/**
 * Gli sprite dei Pokemon nominati in un archetipo scritto a mano.
 *
 * Il mazzo di un avversario non e' uno dei nostri e non ha copertine: si
 * ricava da come si chiama. Vedi PokemonSpriteResolver.spriteUrlsForArchetype.
 *
 * La tabella dei nomi si carica fuori dal thread principale, quindi finche'
 * non e' pronta questa riga e' vuota e compare da se' un istante dopo: e'
 * dentro le dipendenze del remember.
 */
@Composable
fun ArchetypeSpriteRow(
    archetype: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sprites = remember(archetype, PokemonSpriteResolver.isReady) {
        PokemonSpriteResolver.spriteUrlsForArchetype(context, archetype)
    }
    if (sprites.isEmpty()) return

    SpriteRow(sprites = sprites, size = size, modifier = modifier)
}

/** La dimensione usata dentro a un elenco o a un menu. */
val DeckSpriteCompact: Dp = 34.dp
