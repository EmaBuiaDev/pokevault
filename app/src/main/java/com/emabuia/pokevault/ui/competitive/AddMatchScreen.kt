@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.emabuia.pokevault.ui.competitive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.emabuia.pokevault.ui.components.ArchetypeSpriteRow
import com.emabuia.pokevault.ui.components.DeckSpriteCompact
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.PokemonSpriteResolver
import com.emabuia.pokevault.viewmodel.CompetitiveLogViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMatchScreen(
    onBack: () -> Unit,
    tournamentId: String,
    editMatchId: String? = null,
    viewModel: CompetitiveLogViewModel = viewModel()
) {
    LaunchedEffect(tournamentId, editMatchId) {
        viewModel.loadMatchesForTournament(tournamentId)
        if (editMatchId == null) {
            viewModel.resetMatchForm()
            viewModel.matchTournamentId = tournamentId
        }
    }

    // Il precaricamento deve attendere che i match siano davvero arrivati.
    // loadMatchesForTournament e' asincrono e questo schermo riceve sempre un
    // CompetitiveLogViewModel nuovo (scoped alla nav entry): leggere
    // tournamentMatches subito dopo la chiamata restituiva sempre una lista
    // vuota, quindi il form di modifica restava vuoto ogni volta.
    LaunchedEffect(editMatchId, viewModel.tournamentMatches) {
        if (editMatchId != null && viewModel.editingMatchId != editMatchId) {
            viewModel.getMatchById(editMatchId)?.let { viewModel.loadMatchForEdit(it) }
        }
    }

    val isEditing = editMatchId != null

    Scaffold(
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEditing) AppLocale.editMatch else AppLocale.addMatch,
                        color = AppColors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, AppLocale.back, tint = AppColors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Risultato ──
            SectionLabel(AppLocale.matchResult)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ResultButton("W", AppLocale.matchWin, AppColors.green, viewModel.matchResult == "W", { viewModel.matchResult = "W" }, Modifier.weight(1f))
                ResultButton("L", AppLocale.matchLoss, AppColors.red, viewModel.matchResult == "L", { viewModel.matchResult = "L" }, Modifier.weight(1f))
                ResultButton("T", AppLocale.matchTie, AppColors.orange, viewModel.matchResult == "T", { viewModel.matchResult = "T" }, Modifier.weight(1f))
            }

            // ── Turno ──
            SectionLabel(AppLocale.matchRound)
            MatchTextField(
                value = viewModel.matchRound,
                onValueChange = { viewModel.matchRound = it },
                label = AppLocale.matchRound,
                placeholder = AppLocale.matchRoundPlaceholder,
                keyboardType = KeyboardType.Number
            )

            // ── Avversario ──
            SectionLabel(AppLocale.matchOpponent)

            // Risolti una volta: servono per decidere se il campo ha
            // un'icona, e la stessa risposta la riusa la riga qui sotto.
            val spriteContext = LocalContext.current
            val opponentSprites = remember(
                viewModel.matchOpponentDeck,
                PokemonSpriteResolver.isReady
            ) {
                PokemonSpriteResolver.spriteUrlsForArchetype(spriteContext, viewModel.matchOpponentDeck)
            }

            MatchTextField(
                value = viewModel.matchOpponentName,
                onValueChange = { viewModel.matchOpponentName = it },
                label = AppLocale.matchOpponentName,
                placeholder = if (AppLocale.isItalian) "Es. Mario Rossi" else "E.g. John Doe"
            )
            MatchTextField(
                value = viewModel.matchOpponentDeck,
                onValueChange = { viewModel.matchOpponentDeck = it },
                label = AppLocale.matchOpponentDeck,
                placeholder = if (AppLocale.isItalian) "Es. Lugia VSTAR" else "E.g. Lugia VSTAR",
                // Gli sprite compaiono mentre si scrive, appena il nome viene
                // riconosciuto: sono anche la conferma di aver scritto
                // l'archetipo in un modo che l'app capisce.
                //
                // null e non un composable vuoto quando non si riconosce
                // niente: lo slot dell'icona esiste comunque, e riempirlo di
                // nulla lascerebbe uno scalino nel campo.
                leadingIcon = if (opponentSprites.isEmpty()) null else {
                    {
                        ArchetypeSpriteRow(
                            archetype = viewModel.matchOpponentDeck,
                            size = DeckSpriteCompact,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            )

            // I mazzi gia' incontrati, da toccare invece che riscrivere.
            // Questo campo alimenta la tabella dei matchup, e la tabella vale
            // solo se lo stesso archetipo si chiama sempre allo stesso modo:
            // "Charizard ex", "charizard" e "Zard" battuti in tre serate
            // diverse diventerebbero tre avversari che non c'entrano niente.
            OpponentDeckSuggestions(
                suggestions = viewModel.knownOpponentDecks,
                current = viewModel.matchOpponentDeck,
                onPick = { viewModel.matchOpponentDeck = it }
            )

            // ── Note ──
            SectionLabel(AppLocale.matchNotes)
            MatchTextField(
                value = viewModel.matchNotes,
                onValueChange = { viewModel.matchNotes = it },
                label = AppLocale.matchNotes,
                placeholder = AppLocale.matchNotesPlaceholder,
                maxLines = 4
            )

            Spacer(Modifier.height(8.dp))

            // ── Save ──
            Button(
                onClick = { viewModel.saveMatch(onSuccess = onBack) },
                enabled = viewModel.matchResult.isNotBlank() && !viewModel.isSaving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.orange,
                    disabledContainerColor = AppColors.orange.copy(alpha = 0.4f)
                )
            ) {
                if (viewModel.isSaving) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Save, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(AppLocale.save, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

/**
 * I mazzi avversari gia' incontrati, come chip da toccare.
 *
 * Si filtrano su quello che si sta scrivendo, cosi' il campo funziona come un
 * completamento: due lettere e l'archetipo giusto e' li'. Il chip gia' scelto
 * resta evidenziato per confermare che il nome coincide con quello storico e
 * non e' una variante nuova.
 */
@Composable
private fun OpponentDeckSuggestions(
    suggestions: List<String>,
    current: String,
    onPick: (String) -> Unit
) {
    val typed = current.trim()
    val visible = remember(suggestions, typed) {
        if (typed.isBlank()) {
            suggestions.take(8)
        } else {
            val matching = suggestions.filter { it.contains(typed, ignoreCase = true) }
            // Se quello scritto e' gia' uno storico va mostrato lo stesso,
            // altrimenti il chip selezionato sparirebbe appena lo si tocca.
            (matching + suggestions.filter { it.equals(typed, ignoreCase = true) })
                .distinct()
                .take(8)
        }
    }

    if (visible.isEmpty()) return

    Column {
        Text(
            text = AppLocale.matchOpponentDeckSuggestions,
            color = AppColors.textMuted,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            visible.forEach { deck ->
                val selected = deck.equals(typed, ignoreCase = true)
                Surface(
                    color = if (selected) AppColors.orange.copy(alpha = 0.18f) else AppColors.card,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { onPick(deck) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Su una fila di archetipi scritti in fretta, la figura
                        // si riconosce prima del nome.
                        ArchetypeSpriteRow(archetype = deck, size = 24.dp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = deck,
                            color = if (selected) AppColors.orange else AppColors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultButton(
    code: String,
    label: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) color.copy(alpha = 0.2f) else AppColors.card)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) color else AppColors.textMuted.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // lineHeight esplicito e spacedBy(1.dp): coi default le due righe si
        // allontanano abbastanza da far sembrare il bottone sbilanciato.
        // "Sconfitta" e' la parola piu' lunga e su un terzo di larghezza sta
        // al limite: una riga sola, e se non entra si accorcia.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                code,
                color = if (isSelected) color else AppColors.textSecondary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                lineHeight = 16.sp
            )
            Text(
                label,
                color = if (isSelected) color else AppColors.textMuted,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = AppColors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun MatchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    maxLines: Int = 1,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = leadingIcon,
        placeholder = if (placeholder.isNotBlank()) {{ Text(placeholder) }} else null,
        maxLines = maxLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        colors = matchTextFieldColors(),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun matchTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AppColors.orange,
    unfocusedBorderColor = AppColors.textMuted,
    cursorColor = AppColors.orange,
    focusedLabelColor = AppColors.orange,
    unfocusedLabelColor = AppColors.textMuted,
    focusedTextColor = AppColors.textPrimary,
    unfocusedTextColor = AppColors.textPrimary,
    focusedPlaceholderColor = AppColors.textMuted,
    unfocusedPlaceholderColor = AppColors.textMuted
)
