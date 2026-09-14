package com.emabuia.pokevault.ui.wishlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.emabuia.pokevault.data.model.WishlistPriority
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
 * Con [showPriority] il dialogo chiede anche quanto la vuoi: e' l'unico momento
 * in cui uno ci sta gia' pensando, e una priorita' che si puo' mettere solo
 * dopo, aprendo la lista, e' una priorita' che resta "media" per sempre.
 */
@Composable
internal fun WishlistPickerDialog(
    wishlists: List<Wishlist>,
    selectedWishlistIds: Set<String>,
    canCreateNew: Boolean,
    onDismiss: () -> Unit,
    onCreateNewRequested: () -> Unit,
    onConfirmSelection: (Set<String>, WishlistPriority) -> Unit,
    title: String = AppLocale.wishlistAddToList,
    confirmLabel: String = AppLocale.addCard,
    showPriority: Boolean = false
) {
    var selectedIds by remember(wishlists, selectedWishlistIds) {
        mutableStateOf(selectedWishlistIds.filterTo(mutableSetOf()) { id ->
            wishlists.any { wishlist -> wishlist.id == id }
        })
    }
    var priority by remember { mutableStateOf(WishlistPriority.MEDIUM) }

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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(AppLocale.wishlistChooseList, color = AppColors.textMuted, fontSize = 13.sp)

                wishlists.forEach { wishlist ->
                    val selected = wishlist.id in selectedIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) AppColors.blue.copy(alpha = 0.22f) else AppColors.card)
                            .border(
                                1.dp,
                                if (selected) AppColors.blue else AppColors.textMuted.copy(alpha = 0.2f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
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
                            tint = if (selected) AppColors.blue else AppColors.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        WishlistIconBadge(iconKey = wishlist.iconKey, size = 22)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = wishlist.name,
                                color = AppColors.textPrimary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = AppLocale.wishlistCardsCount(wishlist.cardIds.size),
                                color = AppColors.textMuted,
                                fontSize = 10.sp
                            )
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

                if (showPriority && wishlists.isNotEmpty()) {
                    Text(AppLocale.wishlistPriority, color = AppColors.textMuted, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WishlistPriority.entries.forEach { option ->
                            WishlistChip(
                                label = priorityLabel(option),
                                selected = priority == option,
                                color = priorityColor(option),
                                onClick = { priority = option }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            // Sempre abilitato anche a selezione vuota: confermare senza nessuna
            // lista e' il modo in cui si toglie una carta dalle wishlist.
            Button(
                onClick = { onConfirmSelection(selectedIds, priority) },
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue)
            ) {
                Text(confirmLabel, color = AppColors.textPrimary)
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
