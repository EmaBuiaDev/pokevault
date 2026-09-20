package com.emabuia.pokevault.ui.deck

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.DeckLabViewModel
// ══════════════════════════════════════
// IMPORT DIALOGS
// ══════════════════════════════════════

@Composable
fun DeckImportDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    var decklistText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FileDownload, contentDescription = null, tint = AppColors.purple, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(AppLocale.importDeck, color = AppColors.textPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    text = "Incolla una decklist in formato PTCG standard:",
                    color = AppColors.textMuted,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Es: 4 Charizard ex SVI 125",
                    color = AppColors.textMuted.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextField(
                    value = decklistText,
                    onValueChange = { decklistText = it },
                    placeholder = {
                        Text(
                            "Pokémon: 12\n4 Charizard ex SVI 125\n2 Charmander SVI 10\n...",
                            color = AppColors.textMuted.copy(alpha = 0.4f),
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = AppColors.card,
                        unfocusedContainerColor = AppColors.card,
                        focusedIndicatorColor = AppColors.purple,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = AppColors.purple,
                        focusedTextColor = AppColors.textPrimary,
                        unfocusedTextColor = AppColors.textPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (decklistText.isNotBlank()) onImport(decklistText) },
                enabled = decklistText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.purple),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(AppLocale.import)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppLocale.cancel, color = AppColors.textMuted)
            }
        }
    )
}

/**
 * Il riepilogo di un import, e basta.
 *
 * La domanda "che ne facciamo delle carte che non hai?" ha una sua schermata,
 * [DeckCardSourceDialog], e arriva prima di questa: qui non si decide piu'
 * niente, si legge com'e' andata.
 */
@Composable
fun ImportResultDialog(
    result: DeckLabViewModel.ImportResult,
    onDismiss: () -> Unit
) {
    val hasMissingCards = result.missingMetaDeckCards.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (result.matched > 0 && !hasMissingCards) Icons.Default.CheckCircle
                    else if (hasMissingCards) Icons.Default.Warning
                    else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (result.matched > 0 && !hasMissingCards) AppColors.green else AppColors.yellow,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(AppLocale.importResultTitle, color = AppColors.textPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    text = "${result.matched} ${AppLocale.importCardsFound} ${result.totalRequested}",
                    color = AppColors.textPrimary,
                    fontSize = 14.sp
                )

                    if (result.setMismatchWarnings.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Carte abbinate con espansione diversa (${result.setMismatchWarnings.size}):",
                            color = AppColors.orange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        result.setMismatchWarnings.take(8).forEach { card ->
                            Text(
                                text = "• $card",
                                color = AppColors.textMuted,
                                fontSize = 11.sp
                            )
                        }
                        if (result.setMismatchWarnings.size > 8) {
                            Text(
                                text = "... e altre ${result.setMismatchWarnings.size - 8}",
                                color = AppColors.textMuted.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                        }
                    }

                if (result.missingCards.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "${AppLocale.importMissingTitle} (${result.missing}):",
                        color = AppColors.yellow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    result.missingCards.take(10).forEach { card ->
                        Text(
                            text = "• $card",
                            color = AppColors.textMuted,
                            fontSize = 11.sp
                        )
                    }
                    if (result.missingCards.size > 10) {
                        Text(
                            text = "... ${AppLocale.importAndMore} ${result.missingCards.size - 10}",
                            color = AppColors.textMuted.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }
                }

                if (hasMissingCards) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = AppColors.orange.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = AppColors.orange,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = AppLocale.importLeftOutMessage,
                                color = AppColors.textPrimary,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else if (result.matched > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = AppLocale.importMatchedMessage,
                        color = AppColors.green.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = AppLocale.importNoMatchMessage,
                        color = AppColors.red.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                }

            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(AppLocale.ok)
            }
        }
    )
}

/**
 * Dove finiscono le carte che l'utente non possiede.
 *
 * Una domanda sola per due momenti diversi: dopo un import, dove le carte
 * mancanti si contano gia'; e prima di creare un deck da zero, dove la
 * risposta vale per quelle che verranno. Farla qui, una volta, evita di
 * tenere un selettore acceso in cima all'editor per tutto il tempo -- che era
 * spazio occupato per ripetere una cosa gia' decisa.
 *
 * [onSkip] c'e' solo nel caso dell'import: "lascia il deck incompleto" ha
 * senso quando le carte mancanti esistono gia'. Creando un mazzo vuoto non
 * c'e' niente da saltare, e chiudere il dialog vuol dire rinunciare.
 */
@Composable
fun DeckCardSourceDialog(
    prompt: String,
    isWorking: Boolean = false,
    onChoose: (DeckLabViewModel.DeckCardSource) -> Unit,
    onSkip: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isWorking) (onSkip ?: onDismiss)() },
        containerColor = AppColors.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = AppColors.purple,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(AppLocale.deckSourceTitle, color = AppColors.textPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    text = prompt,
                    color = AppColors.textSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                DeckImportSourceOption(
                    icon = Icons.Default.Inventory2,
                    accent = AppColors.green,
                    title = AppLocale.deckSourceCollection,
                    description = AppLocale.deckSourceCollectionDesc,
                    enabled = !isWorking,
                    onClick = { onChoose(DeckLabViewModel.DeckCardSource.COLLECTION) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                DeckImportSourceOption(
                    icon = Icons.Default.Science,
                    accent = AppColors.purple,
                    title = AppLocale.deckSourceDeckOnly,
                    description = AppLocale.deckSourceDeckOnlyDesc,
                    enabled = !isWorking,
                    onClick = { onChoose(DeckLabViewModel.DeckCardSource.DECK_ONLY) }
                )

                if (isWorking) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = AppColors.purple,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = AppLocale.importAddingCards,
                            color = AppColors.textMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            // Solo dopo un import: senza carte mancanti non c'e' niente da
            // lasciare incompleto.
            if (!isWorking && onSkip != null) {
                TextButton(onClick = onSkip) {
                    Text(AppLocale.deckSourceSkip, color = AppColors.textMuted, fontSize = 12.sp)
                }
            }
        }
    )
}

@Composable
private fun DeckImportSourceOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = accent.copy(alpha = 0.10f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    color = AppColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = AppColors.textMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

