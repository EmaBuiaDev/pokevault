package com.emabuia.pokevault.ui.collection

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.Constants
import com.emabuia.pokevault.viewmodel.AddCardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCardScreen(
    onBack: () -> Unit,
    cardId: String? = null,
    viewModel: AddCardViewModel = viewModel()
) {
    val state = viewModel.uiState

    // Carica carta in modalità edit
    LaunchedEffect(cardId) {
        if (cardId != null) {
            viewModel.loadCardForEdit(cardId)
        }
    }

    // Torna indietro quando salvata
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
    ) {
        // ── Top Bar ──
        TopAppBar(
            title = {
                Text(
                    if (state.isEditMode) "Modifica carta" else "Aggiungi carta",
                    fontWeight = FontWeight.SemiBold,
                    color = TextWhite
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro", tint = TextWhite)
                }
            },
            actions = {
                // Bottone Salva
                TextButton(
                    onClick = { viewModel.saveCard() },
                    enabled = !state.isLoading
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = BlueCard,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Salva", color = BlueCard, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
        )

        // ── Errore ──
        AnimatedVisibility(visible = state.errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(RedCard.copy(alpha = 0.15f))
                    .padding(12.dp)
            ) {
                Text(text = state.errorMessage ?: "", color = RedCard, fontSize = 13.sp)
            }
        }

        // ── Form ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Nome (obbligatorio)
            FormField(
                label = "Nome carta *",
                value = state.name,
                onValueChange = { viewModel.updateName(it) },
                placeholder = "es. Charizard VMAX"
            )

            // Set
            FormField(
                label = "Set / Espansione",
                value = state.set,
                onValueChange = { viewModel.updateSet(it) },
                placeholder = "es. Base Set, Evolving Skies"
            )

            // Tipo (dropdown)
            FormDropdown(
                label = "Tipo",
                selected = state.type,
                options = Constants.POKEMON_TYPES.keys.toList(),
                onSelect = { viewModel.updateType(it) }
            )

            // Rarità (dropdown)
            FormDropdown(
                label = "Rarità",
                selected = state.rarity,
                options = Constants.CARD_RARITIES,
                onSelect = { viewModel.updateRarity(it) }
            )

            // HP e Valore sulla stessa riga
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FormField(
                    label = "HP",
                    value = state.hp,
                    onValueChange = { viewModel.updateHp(it) },
                    placeholder = "es. 180",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
                FormField(
                    label = "Valore (€)",
                    value = state.estimatedValue,
                    onValueChange = { viewModel.updateEstimatedValue(it) },
                    placeholder = "es. 25.50",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f)
                )
            }

            // Quantità e Condizione
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FormField(
                    label = "Quantità",
                    value = state.quantity,
                    onValueChange = { viewModel.updateQuantity(it) },
                    placeholder = "1",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
                FormDropdown(
                    label = "Condizione",
                    selected = state.condition,
                    options = Constants.CARD_CONDITIONS,
                    onSelect = { viewModel.updateCondition(it) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Toggle Gradata
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCard)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Carta gradata", color = TextWhite, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text("PSA, BGS, CGC", color = TextMuted, fontSize = 12.sp)
                }
                Switch(
                    checked = state.isGraded,
                    onCheckedChange = { viewModel.updateIsGraded(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextWhite,
                        checkedTrackColor = BlueCard
                    )
                )
            }

            // Campi Grade (solo se gradata)
            AnimatedVisibility(
                visible = state.isGraded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FormField(
                            label = "Voto (Grade)",
                            value = state.grade,
                            onValueChange = { viewModel.updateGrade(it) },
                            placeholder = "es. 9.5",
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f)
                        )
                        FormDropdown(
                            label = "Ente",
                            selected = state.gradingCompany,
                            options = com.emabuia.pokevault.data.model.CardOptions.GRADING_COMPANIES,
                            onSelect = { viewModel.updateGradingCompany(it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Note
            FormField(
                label = "Note",
                value = state.notes,
                onValueChange = { viewModel.updateNotes(it) },
                placeholder = "Note aggiuntive...",
                singleLine = false,
                minHeight = 80.dp
            )

            // URL immagine
            FormField(
                label = "URL immagine (opzionale)",
                value = state.imageUrl,
                onValueChange = { viewModel.updateImageUrl(it) },
                placeholder = "https://..."
            )

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minHeight: androidx.compose.ui.unit.Dp = 0.dp
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = TextGray,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = if (minHeight > 0.dp) minHeight else 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DarkCard)
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            if (value.isEmpty()) {
                Text(text = placeholder, color = TextMuted, fontSize = 14.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = TextWhite,
                    fontSize = 14.sp
                ),
                singleLine = singleLine,
                cursorBrush = SolidColor(BlueCard),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = label,
            color = TextGray,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkCard)
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .clickable { expanded = true }
                    .padding(horizontal = 14.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = selected, color = TextWhite, fontSize = 14.sp)
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess
                                      else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(DarkSurface)
            )  {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, color = TextWhite, fontSize = 14.sp) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
