package com.emabuia.pokevault.ui.wishlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.data.model.Wishlist
import com.emabuia.pokevault.ui.components.pressScale
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale

/**
 * Scelta delle liste in cui mettere una carta.
 *
 * Stava dentro SetDetailScreen come funzione privata: da fuori non si poteva
 * riusare, e il Chase — che sa esattamente quali carte mancano — non aveva modo
 * di mandarle in wishlist. Il [title] permette di dire cosa si sta aggiungendo
 * quando non e' una carta sola.
 *
 * Ogni riga porta il segno e il colore della sua lista: qui si sceglie fra liste
 * che si sono volute diverse, e un elenco di soli nomi buttava via proprio la
 * cosa che le distingue.
 */
@Composable
internal fun WishlistPickerDialog(
    wishlists: List<Wishlist>,
    selectedWishlistIds: Set<String>,
    canCreateNew: Boolean,
    onDismiss: () -> Unit,
    onCreateNewRequested: () -> Unit,
    onConfirmSelection: (Set<String>) -> Unit,
    title: String = AppLocale.wishlistAddToList,
    confirmLabel: String = AppLocale.addCard
) {
    var selectedIds by remember(wishlists, selectedWishlistIds) {
        mutableStateOf(selectedWishlistIds.filterTo(mutableSetOf()) { id ->
            wishlists.any { wishlist -> wishlist.id == id }
        })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.surface,
        title = {
            Text(
                text = title,
                color = AppColors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(AppLocale.wishlistPickerSubtitle, color = AppColors.textMuted, fontSize = 12.sp)

                wishlists.forEach { wishlist ->
                    val selected = wishlist.id in selectedIds
                    val accent = wishlistAccentColor(wishlist.resolvedAccentKey)
                    val wasIn = wishlist.id in selectedWishlistIds

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) accent.copy(alpha = 0.18f) else AppColors.card)
                            .border(
                                1.dp,
                                if (selected) accent else AppColors.textMuted.copy(alpha = 0.2f),
                                RoundedCornerShape(12.dp)
                            )
                            .pressScale {
                                selectedIds = selectedIds.toMutableSet().apply {
                                    if (!add(wishlist.id)) remove(wishlist.id)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (selected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                            contentDescription = null,
                            tint = if (selected) accent else AppColors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        WishlistBadge(
                            iconKey = wishlist.iconKey,
                            accentKey = wishlist.resolvedAccentKey,
                            size = 28.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = wishlist.name,
                                color = AppColors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${wishlistIconLabel(wishlist.iconKey)} · " +
                                    AppLocale.wishlistCardsCount(wishlist.cardIds.size),
                                color = AppColors.textMuted,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (wasIn) {
                            Box(
                                modifier = Modifier
                                    .background(AppColors.green.copy(alpha = 0.18f), CircleShape)
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = AppLocale.wishlistAlreadyIn,
                                    color = AppColors.green,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (wishlists.isEmpty()) {
                    Text(
                        text = AppLocale.wishlistEmptySubtitle,
                        color = AppColors.textMuted,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            // Sempre abilitato anche a selezione vuota: confermare senza nessuna
            // lista e' il modo in cui si toglie una carta dalle wishlist.
            Button(
                onClick = { onConfirmSelection(selectedIds) },
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue)
            ) {
                Text(confirmLabel, color = AppColors.onAccent)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onCreateNewRequested) {
                    Text(
                        text = if (canCreateNew) {
                            AppLocale.wishlistCreateNewList
                        } else {
                            "${AppLocale.wishlistCreateNewList} • Premium"
                        },
                        color = if (canCreateNew) AppColors.purple else AppColors.gold
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(AppLocale.cancel, color = AppColors.textMuted)
                }
            }
        }
    )
}
