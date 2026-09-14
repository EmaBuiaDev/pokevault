package com.emabuia.pokevault.ui.wishlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.data.model.Wishlist
import com.emabuia.pokevault.data.model.WishlistAccents
import com.emabuia.pokevault.data.model.WishlistDraft
import com.emabuia.pokevault.data.model.WishlistIcons
import com.emabuia.pokevault.ui.components.pressScale
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.WishlistViewModel
import java.util.Locale

/**
 * Creazione e modifica di una lista.
 *
 * Tre domande, in ordine di quanto contano: come si chiama, a cosa serve, quanto
 * sei disposto a spenderci. La seconda prima era "scegli un'icona" fra cinque
 * Pokemon, cioe' una domanda senza risposta giusta; adesso e' l'unica cosa che
 * distingue davvero due liste quando sono in fila.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WishlistEditorDialog(
    onDismiss: () -> Unit,
    onConfirm: (WishlistDraft) -> Unit,
    isSaving: Boolean,
    initial: Wishlist? = null,
    canDismiss: Boolean = true,
    titleText: String = AppLocale.wishlistCreate,
    confirmText: String = AppLocale.wishlistCreate
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var iconKey by remember(initial) {
        mutableStateOf(WishlistIcons.normalize(initial?.iconKey ?: WishlistIcons.DEFAULT))
    }
    var accentKey by remember(initial) {
        mutableStateOf(initial?.resolvedAccentKey ?: WishlistIcons.defaultAccentFor(WishlistIcons.DEFAULT))
    }
    // Finche' il colore non lo si sceglie a mano, segue l'icona: chi tocca solo
    // le icone non deve ritrovarsi una lista "Regalo" color oro per caso.
    var accentPickedByHand by remember(initial) { mutableStateOf(initial?.accentKey?.isNotBlank() == true) }
    var budget by remember(initial) {
        mutableStateOf(
            initial?.budgetEur?.takeIf { it > 0.0 }?.let { formatBudgetInput(it) } ?: ""
        )
    }

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
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 40) name = it },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(AppLocale.wishlistName) },
                    placeholder = { Text(AppLocale.wishlistNamePlaceholder, color = AppColors.textMuted) },
                    colors = editorFieldColors()
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        AppLocale.wishlistChooseIcon,
                        color = AppColors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WishlistIcons.all.forEach { key ->
                            val selected = key == iconKey
                            val chipAccent = wishlistAccentColor(
                                if (selected) accentKey else WishlistIcons.defaultAccentFor(key)
                            )
                            Row(
                                modifier = Modifier
                                    .background(
                                        if (selected) chipAccent.copy(alpha = 0.20f) else AppColors.card,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) chipAccent else AppColors.textMuted.copy(alpha = 0.25f),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .pressScale {
                                        iconKey = key
                                        if (!accentPickedByHand) {
                                            accentKey = WishlistIcons.defaultAccentFor(key)
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                WishlistGlyph(iconKey = key, accent = chipAccent, size = 16.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = wishlistIconLabel(key),
                                    color = if (selected) AppColors.textPrimary else AppColors.textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        AppLocale.wishlistChooseColor,
                        color = AppColors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WishlistAccents.all.forEach { key ->
                            val color = wishlistAccentColor(key)
                            val selected = key == accentKey
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(color.copy(alpha = if (selected) 1f else 0.55f), CircleShape)
                                    .border(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) AppColors.textPrimary else AppColors.textMuted.copy(alpha = 0.3f),
                                        CircleShape
                                    )
                                    .pressScale {
                                        accentKey = key
                                        accentPickedByHand = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = AppColors.onAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = budget,
                        onValueChange = { input ->
                            // Solo cifre e un separatore: il campo e' un tetto di
                            // spesa, non un posto dove scrivere "circa 30".
                            if (input.length <= 9 && input.all { it.isDigit() || it == ',' || it == '.' }) {
                                budget = input
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        label = { Text("${AppLocale.wishlistBudget} € · ${AppLocale.wishlistBudgetOptional}") },
                        colors = editorFieldColors()
                    )
                    Text(
                        AppLocale.wishlistBudgetHint,
                        color = AppColors.textMuted,
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        WishlistDraft(
                            name = name.trim(),
                            iconKey = iconKey,
                            accentKey = accentKey,
                            budgetEur = WishlistViewModel.parseBudget(budget)
                        )
                    )
                },
                enabled = name.trim().isNotBlank() && !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = wishlistAccentColor(accentKey))
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.6.dp,
                        color = AppColors.onAccent
                    )
                } else {
                    Text(confirmText, color = AppColors.onAccent, fontWeight = FontWeight.SemiBold)
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

@Composable
private fun editorFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AppColors.purple,
    unfocusedBorderColor = AppColors.textMuted.copy(alpha = 0.35f),
    cursorColor = AppColors.purple,
    focusedTextColor = AppColors.textPrimary,
    unfocusedTextColor = AppColors.textPrimary,
    focusedLabelColor = AppColors.purple,
    unfocusedLabelColor = AppColors.textMuted,
    focusedContainerColor = AppColors.card,
    unfocusedContainerColor = AppColors.card
)

/** Il budget nel campo si riscrive come lo si scriverebbe a mano: 30, non 30.0. */
private fun formatBudgetInput(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString()
    else String.format(Locale.ITALY, "%.2f", value)
