package com.example.pokevault.ui.competitive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pokevault.data.model.MatchLog
import com.example.pokevault.ui.theme.*
import com.example.pokevault.util.AppLocale
import com.example.pokevault.viewmodel.CompetitiveLogViewModel
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMatchScreen(
    onBack: () -> Unit,
    editMatchId: String? = null,
    viewModel: CompetitiveLogViewModel = viewModel()
) {
    // Load match data if editing
    LaunchedEffect(editMatchId) {
        if (editMatchId != null) {
            val match = viewModel.getMatchById(editMatchId)
            if (match != null) {
                viewModel.loadMatchForEdit(match)
            }
        } else {
            viewModel.resetForm()
        }
    }

    val isEditing = editMatchId != null
    val scrollState = rememberScrollState()
    var showFormatDropdown by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val currentDateStr = viewModel.matchDate.toDate().let { dateFormat.format(it) }

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
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = AppLocale.back,
                            tint = TextWhite
                        )
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
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ── Risultato ──
            SectionLabel(AppLocale.matchResult)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ResultButton(
                    label = AppLocale.matchWin,
                    code = "W",
                    color = GreenCard,
                    isSelected = viewModel.matchResult == "W",
                    onClick = { viewModel.matchResult = "W" },
                    modifier = Modifier.weight(1f)
                )
                ResultButton(
                    label = AppLocale.matchLoss,
                    code = "L",
                    color = RedCard,
                    isSelected = viewModel.matchResult == "L",
                    onClick = { viewModel.matchResult = "L" },
                    modifier = Modifier.weight(1f)
                )
                ResultButton(
                    label = AppLocale.matchTie,
                    code = "T",
                    color = YellowCard,
                    isSelected = viewModel.matchResult == "T",
                    onClick = { viewModel.matchResult = "T" },
                    modifier = Modifier.weight(1f)
                )
            }

            // ── Mazzo usato ──
            SectionLabel(AppLocale.matchDeckUsed)
            MatchTextField(
                value = viewModel.matchDeckName,
                onValueChange = { viewModel.matchDeckName = it },
                label = AppLocale.matchDeckName,
                placeholder = if (AppLocale.isItalian) "Es. Charizard ex" else "E.g. Charizard ex"
            )

            // ── Formato ──
            SectionLabel(AppLocale.matchFormat)
            ExposedDropdownMenuBox(
                expanded = showFormatDropdown,
                onExpandedChange = { showFormatDropdown = it }
            ) {
                OutlinedTextField(
                    value = viewModel.matchFormat.ifBlank {
                        if (AppLocale.isItalian) "Seleziona formato" else "Select format"
                    },
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showFormatDropdown) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    colors = matchTextFieldColors(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = showFormatDropdown,
                    onDismissRequest = { showFormatDropdown = false },
                    containerColor = DarkSurface
                ) {
                    MatchLog.FORMATS.forEach { format ->
                        DropdownMenuItem(
                            text = { Text(format, color = TextWhite) },
                            onClick = {
                                viewModel.matchFormat = format
                                showFormatDropdown = false
                            }
                        )
                    }
                }
            }

            // ── Data ──
            SectionLabel(AppLocale.matchDate)
            OutlinedTextField(
                value = currentDateStr,
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = OrangeCard)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true },
                colors = matchTextFieldColors(),
                shape = RoundedCornerShape(12.dp)
            )

            // ── Luogo ──
            SectionLabel(AppLocale.matchLocation)
            MatchTextField(
                value = viewModel.matchLocation,
                onValueChange = { viewModel.matchLocation = it },
                label = AppLocale.matchLocation,
                placeholder = AppLocale.matchLocationPlaceholder
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

            // ── Lista mazzo (opzionale) ──
            MatchTextField(
                value = viewModel.matchDeckList,
                onValueChange = { viewModel.matchDeckList = it },
                label = AppLocale.matchDeckList,
                maxLines = 4
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

            Spacer(modifier = Modifier.height(8.dp))

            // ── Save button ──
            Button(
                onClick = { viewModel.saveMatch(onSuccess = onBack) },
                enabled = viewModel.matchDeckName.isNotBlank() &&
                        viewModel.matchResult.isNotBlank() &&
                        !viewModel.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OrangeCard,
                    disabledContainerColor = OrangeCard.copy(alpha = 0.4f)
                )
            ) {
                if (viewModel.isSaving) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Save, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = AppLocale.save,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Date picker dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = viewModel.matchDate.toDate().time
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        viewModel.matchDate = Timestamp(Date(millis))
                    }
                    showDatePicker = false
                }) {
                    Text(AppLocale.save, color = OrangeCard)
                }
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
private fun ResultButton(
    label: String,
    code: String,
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
            Text(
                text = code,
                color = if (isSelected) color else TextGray,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp
            )
            Text(
                text = label,
                color = if (isSelected) color else TextMuted,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = TextGray,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun MatchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    maxLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder.isNotBlank()) {{ Text(placeholder) }} else null,
        maxLines = maxLines,
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
