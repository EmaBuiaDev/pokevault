package com.emabuia.pokevault.ui.wishlist

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CatchingPokemon
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.data.model.WishlistIcons
import com.emabuia.pokevault.data.model.WishlistPriority
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.ui.theme.AppMotion
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.WishlistLab

/**
 * I pezzi comuni della Wishlist.
 *
 * Icone, chip, badge e dialoghi stavano dentro la schermata elenco: il
 * dettaglio non poteva riusarli e ha vissuto finora senza, con l'aria di
 * un'altra app. Qui stanno in un punto solo e le due schermate parlano la
 * stessa lingua.
 */

internal data class WishlistIconOption(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val color: Color
)

// @Composable perche' i colori vengono dai token del tema, non piu' da
// costanti fisse.
@Composable
internal fun wishlistIconOptions(): List<WishlistIconOption> = listOf(
    WishlistIconOption(WishlistIcons.POKEBALL, "Poke Ball", Icons.Default.CatchingPokemon, AppColors.red),
    WishlistIconOption(WishlistIcons.MASTER_BALL, "Master Ball", Icons.Default.Stars, AppColors.purple),
    WishlistIconOption(WishlistIcons.PIKACHU, "Pikachu", Icons.Default.Bolt, AppColors.gold),
    WishlistIconOption(WishlistIcons.CHARIZARD, "Charizard", Icons.Default.LocalFireDepartment, Color(0xFFE87A35)),
    WishlistIconOption(WishlistIcons.EEVEE, "Eevee", Icons.Default.Pets, AppColors.blue)
)

@Composable
internal fun iconForKey(iconKey: String): WishlistIconOption {
    val options = wishlistIconOptions()
    return options.firstOrNull { it.key == iconKey } ?: options.first()
}

// ── Priorita' ────────────────────────────────────────────────────────────────

@Composable
internal fun priorityColor(priority: WishlistPriority): Color = when (priority) {
    WishlistPriority.HIGH -> AppColors.red
    WishlistPriority.MEDIUM -> AppColors.blue
    WishlistPriority.LOW -> AppColors.textMuted
}

internal fun priorityLabel(priority: WishlistPriority): String = when (priority) {
    WishlistPriority.HIGH -> AppLocale.wishlistPriorityHigh
    WishlistPriority.MEDIUM -> AppLocale.wishlistPriorityMedium
    WishlistPriority.LOW -> AppLocale.wishlistPriorityLow
}

/**
 * La priorita' come tre tacche, non come testo.
 *
 * In una riga alta 66dp una parola in piu' e' rumore: le tacche si leggono
 * con la coda dell'occhio mentre si scorre, che e' il momento in cui la
 * priorita' serve davvero.
 */
@Composable
internal fun PriorityBars(priority: WishlistPriority, modifier: Modifier = Modifier) {
    val color = priorityColor(priority)
    val filled = when (priority) {
        WishlistPriority.HIGH -> 3
        WishlistPriority.MEDIUM -> 2
        WishlistPriority.LOW -> 1
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((5 + index * 3).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (index < filled) color else AppColors.textMuted.copy(alpha = 0.25f)
                    )
            )
        }
    }
}

@Composable
internal fun WishlistTag(
    text: String,
    color: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(10.dp))
            Spacer(modifier = Modifier.width(3.dp))
        }
        Text(text = text, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun DealTag(modifier: Modifier = Modifier) {
    WishlistTag(
        text = AppLocale.wishlistDealBadge,
        color = AppColors.green,
        icon = Icons.Default.LocalOffer,
        modifier = modifier
    )
}

@Composable
internal fun OwnedTag(modifier: Modifier = Modifier) {
    WishlistTag(
        text = AppLocale.wishlistOwnedBadge,
        color = AppColors.green,
        icon = Icons.Default.CheckCircle,
        modifier = modifier
    )
}

// ── Chip di ordinamento e filtro ─────────────────────────────────────────────

@Composable
internal fun WishlistChip(
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    val background by animateColorAsState(
        targetValue = if (selected) color.copy(alpha = 0.22f) else AppColors.card,
        animationSpec = tween(AppMotion.state),
        label = "chipBackground"
    )
    val border by animateColorAsState(
        targetValue = if (selected) color else AppColors.textMuted.copy(alpha = 0.22f),
        animationSpec = tween(AppMotion.state),
        label = "chipBorder"
    )
    Text(
        text = label,
        color = if (selected) color else AppColors.textSecondary,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

// ── Budget ───────────────────────────────────────────────────────────────────

/**
 * La barra del budget.
 *
 * Non usa la FillBar delle liste: li' il 100% e' un traguardo e si colora
 * d'oro, qui e' invece il momento in cui hai finito i soldi. Oltre il tetto la
 * barra resta piena e diventa rossa — saturarla in silenzio sarebbe l'unico
 * modo per far sembrare innocuo uno sforamento.
 */
@Composable
internal fun BudgetBar(percent: Float, modifier: Modifier = Modifier) {
    val over = percent > 100f
    val animated by animateFloatAsState(
        targetValue = (percent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(AppMotion.bar),
        label = "budget"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(AppColors.textMuted.copy(alpha = 0.18f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(6.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (over) AppColors.red else AppColors.green)
        )
    }
}

// ── Dialoghi ─────────────────────────────────────────────────────────────────

@Composable
private fun wishlistTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AppColors.purple,
    unfocusedBorderColor = AppColors.textMuted.copy(alpha = 0.4f),
    focusedTextColor = AppColors.textPrimary,
    unfocusedTextColor = AppColors.textPrimary,
    focusedContainerColor = AppColors.card,
    unfocusedContainerColor = AppColors.card
)

/**
 * Creazione e modifica di una lista.
 *
 * Il budget e' opzionale e sta qui dentro perche' e' una proprieta' della
 * lista: chiederlo dopo, in un secondo dialogo, significherebbe che nessuno lo
 * imposta mai.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateWishlistDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, iconKey: String, budget: Double) -> Unit,
    isSaving: Boolean,
    canDismiss: Boolean = true,
    initialName: String = "",
    initialIconKey: String = WishlistIcons.POKEBALL,
    initialBudget: Double = 0.0,
    titleText: String = AppLocale.wishlistCreate,
    confirmText: String = AppLocale.wishlistCreate
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var selectedIconKey by remember(initialIconKey) { mutableStateOf(initialIconKey) }
    var budget by remember(initialBudget) {
        mutableStateOf(if (initialBudget > 0.0) "%.2f".format(initialBudget) else "")
    }
    val options = wishlistIconOptions()

    AlertDialog(
        onDismissRequest = { if (canDismiss && !isSaving) onDismiss() },
        containerColor = AppColors.surface,
        title = {
            Text(
                text = titleText,
                color = AppColors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 40) name = it },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = wishlistTextFieldColors(),
                    label = { Text(AppLocale.wishlistName, color = AppColors.textMuted) },
                    placeholder = { Text(AppLocale.wishlistNamePlaceholder, color = AppColors.textMuted) }
                )

                OutlinedTextField(
                    value = budget,
                    onValueChange = { input -> budget = input.filter { it.isDigit() || it == ',' || it == '.' } },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = wishlistTextFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text(AppLocale.wishlistBudgetOptional, color = AppColors.textMuted) },
                    placeholder = { Text(AppLocale.wishlistBudgetPlaceholder, color = AppColors.textMuted) },
                    suffix = { Text("€", color = AppColors.textMuted) }
                )

                Text(AppLocale.wishlistChooseIcon, color = AppColors.textSecondary, fontSize = 13.sp)

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    options.forEach { option ->
                        val selected = selectedIconKey == option.key
                        Row(
                            modifier = Modifier
                                .background(
                                    if (selected) option.color.copy(alpha = 0.22f) else AppColors.card,
                                    RoundedCornerShape(16.dp)
                                )
                                .border(
                                    1.dp,
                                    if (selected) option.color else AppColors.textMuted.copy(alpha = 0.25f),
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { selectedIconKey = option.key }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(option.icon, contentDescription = null, tint = option.color, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(option.label, color = AppColors.textPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim(), selectedIconKey, WishlistLab.normalizePrice(budget)) },
                enabled = name.trim().isNotBlank() && !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.purple)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.6.dp, color = AppColors.textPrimary)
                } else {
                    Text(confirmText, color = AppColors.textPrimary)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(AppLocale.cancel, color = AppColors.textMuted)
            }
        }
    )
}

/**
 * Priorita', tetto di prezzo e nota di una carta.
 *
 * E' il dialogo che trasforma un elenco di id in una lista della spesa: senza
 * di questo la wishlist sa *cosa* vuoi ma non quanto, ne' a che prezzo.
 */
@Composable
internal fun WishlistItemDialog(
    cardName: String,
    initialPriority: WishlistPriority,
    initialNote: String,
    initialTargetPrice: Double,
    currentPrice: Double,
    isOwned: Boolean,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (WishlistPriority, String, Double) -> Unit,
    onMarkPurchased: () -> Unit,
    onRemove: () -> Unit
) {
    var priority by remember(initialPriority) { mutableStateOf(initialPriority) }
    var note by remember(initialNote) { mutableStateOf(initialNote) }
    var target by remember(initialTargetPrice) {
        mutableStateOf(if (initialTargetPrice > 0.0) "%.2f".format(initialTargetPrice) else "")
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = AppColors.surface,
        title = {
            Column {
                Text(
                    text = AppLocale.wishlistCardSettings,
                    color = AppColors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = cardName,
                    color = AppColors.textMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(AppLocale.wishlistPriority, color = AppColors.textSecondary, fontSize = 13.sp)
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

                OutlinedTextField(
                    value = target,
                    onValueChange = { input -> target = input.filter { it.isDigit() || it == ',' || it == '.' } },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = wishlistTextFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text(AppLocale.wishlistTargetPrice, color = AppColors.textMuted) },
                    placeholder = {
                        Text(
                            text = if (currentPrice > 0.0) WishlistLab.formatPrice(currentPrice) else "—",
                            color = AppColors.textMuted
                        )
                    },
                    suffix = { Text("€", color = AppColors.textMuted) }
                )
                Text(
                    text = AppLocale.wishlistTargetPriceHint,
                    color = AppColors.textMuted,
                    fontSize = 11.sp
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= WishlistLab.MAX_NOTE_LENGTH) note = it },
                    shape = RoundedCornerShape(12.dp),
                    colors = wishlistTextFieldColors(),
                    label = { Text(AppLocale.wishlistNote, color = AppColors.textMuted) },
                    placeholder = { Text(AppLocale.wishlistNotePlaceholder, color = AppColors.textMuted) }
                )

                if (!isOwned) {
                    Button(
                        onClick = onMarkPurchased,
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.green)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AppColors.textPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(AppLocale.wishlistMarkPurchased, color = AppColors.textPrimary)
                    }
                }

                TextButton(onClick = onRemove, enabled = !isSaving) {
                    Text(AppLocale.delete, color = AppColors.red)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(priority, WishlistLab.normalizeNote(note), WishlistLab.normalizePrice(target))
                },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.purple)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.6.dp, color = AppColors.textPrimary)
                } else {
                    Text(AppLocale.save, color = AppColors.textPrimary)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(AppLocale.cancel, color = AppColors.textMuted)
            }
        }
    )
}

/** Il tetto di spesa della lista, modificabile senza passare dalla modifica del nome. */
@Composable
internal fun WishlistBudgetDialog(
    initialBudget: Double,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var budget by remember(initialBudget) {
        mutableStateOf(if (initialBudget > 0.0) "%.2f".format(initialBudget) else "")
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = AppColors.surface,
        title = { Text(AppLocale.wishlistBudgetTitle, color = AppColors.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(AppLocale.wishlistBudgetMessage, color = AppColors.textSecondary, fontSize = 13.sp)
                OutlinedTextField(
                    value = budget,
                    onValueChange = { input -> budget = input.filter { it.isDigit() || it == ',' || it == '.' } },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = wishlistTextFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text(AppLocale.wishlistBudget, color = AppColors.textMuted) },
                    placeholder = { Text(AppLocale.wishlistBudgetPlaceholder, color = AppColors.textMuted) },
                    suffix = { Text("€", color = AppColors.textMuted) }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(WishlistLab.normalizePrice(budget)) },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.purple)
            ) {
                Text(AppLocale.save, color = AppColors.textPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(AppLocale.cancel, color = AppColors.textMuted)
            }
        }
    )
}

/** Il pallino colorato con l'icona della lista, alla misura richiesta. */
@Composable
internal fun WishlistIconBadge(iconKey: String, size: Int = 40) {
    val option = iconForKey(iconKey)
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(option.color.copy(alpha = 0.2f), CircleShape)
            .border(1.dp, option.color.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            option.icon,
            contentDescription = null,
            tint = option.color,
            modifier = Modifier.size((size * 0.5f).dp)
        )
    }
}
