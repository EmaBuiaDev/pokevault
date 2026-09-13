package com.emabuia.pokevault.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.ui.components.pressSlide
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.searchBar)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = AppLocale.search,
                tint = AppColors.textMuted,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = AppLocale.searchCard,
                        color = AppColors.textMuted,
                        fontSize = 14.sp
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = TextStyle(
                        color = AppColors.textPrimary,
                        fontSize = 14.sp
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(AppColors.textPrimary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * La barra di ricerca della Home.
 *
 * Non e' un campo di testo: e' un pulsante che ne ha l'aspetto. La ricerca vera
 * vive nel Pokedex, con i suoi filtri e il match esatto, e questa barra ci
 * porta dentro col campo gia' a fuoco. Un campo editabile qui vorrebbe dire o
 * scrivere due volte, o far sparire la tastiera a meta' parola quando la
 * schermata cambia sotto le dita.
 *
 * Cerca in tutto il catalogo, non nella collezione: quella e' la [SearchBar]
 * qui sopra, che filtra le carte gia' possedute dentro "Le mie carte".
 */
@Composable
fun HomeSearchEntry(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.searchBar)
            // Il pressSlide viene dopo lo sfondo: il ripple si disegna sopra,
            // e il clip qui sopra lo tiene dentro gli angoli arrotondati.
            .pressSlide(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = AppColors.textMuted,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = AppLocale.searchInSets,
                color = AppColors.textMuted,
                fontSize = 14.sp
            )
        }
    }
}
