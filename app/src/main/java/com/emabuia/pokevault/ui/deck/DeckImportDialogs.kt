package com.emabuia.pokevault.ui.deck

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.emabuia.pokevault.data.billing.PremiumManager
import com.emabuia.pokevault.data.model.Deck
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.ui.premium.PremiumRequiredDialog
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.ImageUrlUtils
import com.emabuia.pokevault.viewmodel.DeckLabViewModel
import com.emabuia.pokevault.viewmodel.MetaDeckViewModel

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
        containerColor = DarkSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FileDownload, contentDescription = null, tint = PurpleCard, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(AppLocale.importDeck, color = TextWhite, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    text = "Incolla una decklist in formato PTCG standard:",
                    color = TextMuted,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Es: 4 Charizard ex SVI 125",
                    color = TextMuted.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextField(
                    value = decklistText,
                    onValueChange = { decklistText = it },
                    placeholder = {
                        Text(
                            "Pokémon: 12\n4 Charizard ex SVI 125\n2 Charmander SVI 10\n...",
                            color = TextMuted.copy(alpha = 0.4f),
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DarkCard,
                        unfocusedContainerColor = DarkCard,
                        focusedIndicatorColor = PurpleCard,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = PurpleCard,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
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
                colors = ButtonDefaults.buttonColors(containerColor = PurpleCard),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(AppLocale.import)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppLocale.cancel, color = TextMuted)
            }
        }
    )
}


@Composable
fun ImportResultDialog(
    result: DeckLabViewModel.ImportResult,
    isAddingMissingCards: Boolean = false,
    onDismiss: () -> Unit,
    onAddMissingCards: () -> Unit = {}
) {
    val hasMissingCards = result.missingMetaDeckCards.isNotEmpty()

    AlertDialog(
        onDismissRequest = { if (!isAddingMissingCards) onDismiss() },
        containerColor = DarkSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (result.matched > 0 && !hasMissingCards) Icons.Default.CheckCircle
                    else if (hasMissingCards) Icons.Default.Warning
                    else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (result.matched > 0 && !hasMissingCards) GreenCard else YellowCard,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(AppLocale.importResultTitle, color = TextWhite, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    text = "${result.matched} ${AppLocale.importCardsFound} ${result.totalRequested}",
                    color = TextWhite,
                    fontSize = 14.sp
                )

                    if (result.setMismatchWarnings.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Carte abbinate con espansione diversa (${result.setMismatchWarnings.size}):",
                            color = OrangeCard,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        result.setMismatchWarnings.take(8).forEach { card ->
                            Text(
                                text = "• $card",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        if (result.setMismatchWarnings.size > 8) {
                            Text(
                                text = "... e altre ${result.setMismatchWarnings.size - 8}",
                                color = TextMuted.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                        }
                    }

                if (result.missingCards.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "${AppLocale.importMissingTitle} (${result.missing}):",
                        color = YellowCard,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    result.missingCards.take(10).forEach { card ->
                        Text(
                            text = "• $card",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    if (result.missingCards.size > 10) {
                        Text(
                            text = "... ${AppLocale.importAndMore} ${result.missingCards.size - 10}",
                            color = TextMuted.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }
                }

                if (hasMissingCards) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = OrangeCard.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.AddCircle,
                                contentDescription = null,
                                tint = OrangeCard,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = AppLocale.importAddMissingMessage,
                                color = TextWhite,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else if (result.matched > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = AppLocale.importMatchedMessage,
                        color = GreenCard.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = AppLocale.importNoMatchMessage,
                        color = RedCard.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                }

                if (isAddingMissingCards) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = OrangeCard,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = AppLocale.importAddingCards,
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (hasMissingCards && !isAddingMissingCards) {
                Button(
                    onClick = onAddMissingCards,
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeCard),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(AppLocale.importAddMissingConfirm, fontSize = 13.sp)
                }
            } else if (!isAddingMissingCards) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = BlueCard),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(AppLocale.ok)
                }
            }
        },
        dismissButton = {
            if (hasMissingCards && !isAddingMissingCards) {
                TextButton(onClick = onDismiss) {
                    Text(AppLocale.importAddMissingSkip, color = TextMuted)
                }
            }
        }
    )
}
