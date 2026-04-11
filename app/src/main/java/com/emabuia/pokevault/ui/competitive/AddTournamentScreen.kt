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
import com.emabuia.pokevault.data.model.Tournament
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.CompetitiveLogViewModel
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTournamentScreen(
    onBack: () -> Unit,
    editTournamentId: String? = null,
    viewModel: CompetitiveLogViewModel = viewModel()
) {
    LaunchedEffect(editTournamentId) {
        if (editTournamentId != null) {
            val tournament = viewModel.getTournamentById(editTournamentId)
            if (tournament != null) viewModel.loadTournamentForEdit(tournament)
        } else {
            viewModel.resetTournamentForm()
        }
    }

    val isEditing = editTournamentId != null
    var showFormatDropdown by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var useDeckFromList by remember { mutableStateOf(false) }

    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val currentDateStr = viewModel.tournamentDate.toDate().let { dateFormat.format(it) }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEditing) AppLocale.editTournament else AppLocale.addTournament,
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

            // ── Tipologia (Required) ──
            SectionLabel(AppLocale.tournamentType)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Tournament.TYPES.forEach { type ->
                    val color = when (type) {
                        "Cup" -> StarGold
                        "Challenge" -> BlueCard
                        "Local" -> GreenCard
                        else -> TextMuted
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (viewModel.tournamentType == type) color.copy(alpha = 0.2f) else DarkCard)
                            .border(
                                width = if (viewModel.tournamentType == type) 2.dp else 1.dp,
                                color = if (viewModel.tournamentType == type) color else TextMuted.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { viewModel.tournamentType = type },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            type,
                            color = if (viewModel.tournamentType == type) color else TextGray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // ── Data ──
            SectionLabel(AppLocale.tournamentDate)
            OutlinedTextField(
                value = currentDateStr,
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarToday, null, tint = OrangeCard)
                    }
                },
                modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                colors = tournamentTextFieldColors(),
                shape = RoundedCornerShape(12.dp)
            )

            // ── Luogo ──
            SectionLabel(AppLocale.tournamentLocation)
            TournamentTextField(
                value = viewModel.tournamentLocation,
                onValueChange = { viewModel.tournamentLocation = it },
                label = AppLocale.tournamentLocation,
                placeholder = AppLocale.tournamentLocationPlaceholder
            )

            // ── Partecipanti + Budget ──
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    SectionLabel(AppLocale.tournamentParticipants)
                    TournamentTextField(
                        value = viewModel.tournamentParticipants,
                        onValueChange = { viewModel.tournamentParticipants = it },
                        label = AppLocale.tournamentParticipants,
                        placeholder = AppLocale.tournamentParticipantsPlaceholder,
                        keyboardType = KeyboardType.Number
                    )
                }
                Column(Modifier.weight(1f)) {
                    SectionLabel(AppLocale.tournamentFee)
                    TournamentTextField(
                        value = viewModel.tournamentFee,
                        onValueChange = { viewModel.tournamentFee = it },
                        label = "€",
                        placeholder = AppLocale.tournamentFeePlaceholder,
                        keyboardType = KeyboardType.Decimal
                    )
                }
            }

            // ── Formato ──
            SectionLabel(AppLocale.tournamentFormat)
            ExposedDropdownMenuBox(
                expanded = showFormatDropdown,
                onExpandedChange = { showFormatDropdown = it }
            ) {
                OutlinedTextField(
                    value = viewModel.tournamentFormat.ifBlank {
                        if (AppLocale.isItalian) "Seleziona formato" else "Select format"
                    },
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showFormatDropdown) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    colors = tournamentTextFieldColors(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = showFormatDropdown,
                    onDismissRequest = { showFormatDropdown = false },
                    containerColor = DarkSurface
                ) {
                    Tournament.FORMATS.forEach { format ->
                        DropdownMenuItem(
                            text = { Text(format, color = TextWhite) },
                            onClick = {
                                viewModel.tournamentFormat = format
                                showFormatDropdown = false
                            }
                        )
                    }
                }
            }

            // ── Deck ──
            SectionLabel(AppLocale.tournamentDeck)

            // Toggle: scegli dai deck o scrivi nome
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = { useDeckFromList = false },
                    shape = RoundedCornerShape(10.dp),
                    color = if (!useDeckFromList) OrangeCard.copy(alpha = 0.2f) else DarkCard,
                    border = if (!useDeckFromList) androidx.compose.foundation.BorderStroke(1.dp, OrangeCard) else null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        AppLocale.tournamentDeckCustom,
                        color = if (!useDeckFromList) OrangeCard else TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
                Surface(
                    onClick = { useDeckFromList = true },
                    shape = RoundedCornerShape(10.dp),
                    color = if (useDeckFromList) OrangeCard.copy(alpha = 0.2f) else DarkCard,
                    border = if (useDeckFromList) androidx.compose.foundation.BorderStroke(1.dp, OrangeCard) else null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        AppLocale.tournamentDeckFromList,
                        color = if (useDeckFromList) OrangeCard else TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }

            if (useDeckFromList) {
                if (viewModel.userDecks.isEmpty()) {
                    Text(
                        if (AppLocale.isItalian) "Nessun deck creato" else "No decks created",
                        color = TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    var showDeckDropdown by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = showDeckDropdown,
                        onExpandedChange = { showDeckDropdown = it }
                    ) {
                        OutlinedTextField(
                            value = viewModel.tournamentDeckName.ifBlank {
                                if (AppLocale.isItalian) "Seleziona deck" else "Select deck"
                            },
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showDeckDropdown) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            colors = tournamentTextFieldColors(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = showDeckDropdown,
                            onDismissRequest = { showDeckDropdown = false },
                            containerColor = DarkSurface
                        ) {
                            viewModel.userDecks.forEach { deck ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "${deck.name} (${deck.totalCards} carte)",
                                            color = TextWhite,
                                            fontSize = 14.sp
                                        )
                                    },
                                    onClick = {
                                        viewModel.tournamentDeckName = deck.name
                                        viewModel.tournamentDeckId = deck.id
                                        showDeckDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                TournamentTextField(
                    value = viewModel.tournamentDeckName,
                    onValueChange = {
                        viewModel.tournamentDeckName = it
                        viewModel.tournamentDeckId = ""
                    },
                    label = AppLocale.tournamentDeck,
                    placeholder = AppLocale.tournamentDeckPlaceholder
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── Save ──
            Button(
                onClick = { viewModel.saveTournament(onSuccess = onBack) },
                enabled = viewModel.tournamentType.isNotBlank() && !viewModel.isSaving,
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

    // Date picker
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = viewModel.tournamentDate.toDate().time
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        viewModel.tournamentDate = Timestamp(Date(millis))
                    }
                    showDatePicker = false
                }) { Text(AppLocale.save, color = OrangeCard) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(AppLocale.cancel, color = TextGray)
                }
            },
            colors = DatePickerDefaults.colors(containerColor = DarkSurface)
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = DarkSurface,
                    titleContentColor = TextWhite,
                    headlineContentColor = TextWhite,
                    weekdayContentColor = TextMuted,
                    subheadContentColor = TextGray,
                    yearContentColor = TextWhite,
                    currentYearContentColor = OrangeCard,
                    selectedYearContentColor = Color.White,
                    selectedYearContainerColor = OrangeCard,
                    dayContentColor = TextWhite,
                    selectedDayContentColor = Color.White,
                    selectedDayContainerColor = OrangeCard,
                    todayContentColor = OrangeCard,
                    todayDateBorderColor = OrangeCard
                )
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = TextGray, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun TournamentTextField(
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
        colors = tournamentTextFieldColors(),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun tournamentTextFieldColors() = OutlinedTextFieldDefaults.colors(
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
