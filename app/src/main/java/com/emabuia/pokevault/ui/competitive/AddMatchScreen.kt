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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
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
        if (editMatchId != null) {
            val match = viewModel.getMatchById(editMatchId)
            if (match != null) {
                viewModel.loadMatchForEdit(match)
            }
        } else {
            viewModel.resetMatchForm()
            viewModel.matchTournamentId = tournamentId
        }
    }

    val isEditing = editMatchId != null

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEditing) AppLocale.editMatch else AppLocale.addMatch,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, AppLocale.back, tint = TextWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ResultButton("W", AppLocale.matchWin, GreenCard, viewModel.matchResult == "W", { viewModel.matchResult = "W" }, Modifier.weight(1f))
                ResultButton("L", AppLocale.matchLoss, RedCard, viewModel.matchResult == "L", { viewModel.matchResult = "L" }, Modifier.weight(1f))
                ResultButton("T", AppLocale.matchTie, YellowCard, viewModel.matchResult == "T", { viewModel.matchResult = "T" }, Modifier.weight(1f))
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
                placeholder = if (AppLocale.isItalian) "Es. Lugia VSTAR" else "E.g. Lugia VSTAR"
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
                    containerColor = OrangeCard,
                    disabledContainerColor = OrangeCard.copy(alpha = 0.4f)
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
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) color.copy(alpha = 0.2f) else DarkCard)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) color else TextMuted.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(code, color = if (isSelected) color else TextGray, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text(label, color = if (isSelected) color else TextMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = TextGray, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun MatchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    maxLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
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
    focusedBorderColor = OrangeCard,
    unfocusedBorderColor = TextMuted,
    cursorColor = OrangeCard,
    focusedLabelColor = OrangeCard,
    unfocusedLabelColor = TextMuted,
    focusedTextColor = TextWhite,
    unfocusedTextColor = TextWhite,
    focusedPlaceholderColor = TextMuted,
    unfocusedPlaceholderColor = TextMuted
)
