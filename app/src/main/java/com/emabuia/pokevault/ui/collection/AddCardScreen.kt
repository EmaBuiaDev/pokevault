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
import com.emabuia.pokevault.util.AppLocale
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
            .background(AppColors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
    ) {
        // ── Top Bar ──
        TopAppBar(
            title = {
                Text(
                    if (state.isEditMode) AppLocale.editCard else AppLocale.addCard,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.textPrimary
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, AppLocale.back, tint = AppColors.textPrimary)
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
                            color = AppColors.blue,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(AppLocale.save, color = AppColors.blue, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background)
        )

        // ── Errore ──
        AnimatedVisibility(visible = state.errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.red.copy(alpha = 0.15f))
                    .padding(12.dp)
            ) {
                Text(text = state.errorMessage ?: "", color = AppColors.red, fontSize = 13.sp)
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
                label = AppLocale.cardNameRequiredLabel,
                value = state.name,
                onValueChange = { viewModel.updateName(it) },
                placeholder = AppLocale.cardNamePlaceholder
            )

            // Set
            FormField(
                label = AppLocale.setExpansionLabel,
                value = state.set,
                onValueChange = { viewModel.updateSet(it) },
                placeholder = AppLocale.setPlaceholder
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
                    placeholder = AppLocale.hpPlaceholder,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
                FormField(
                    label = AppLocale.valueLabel,
                    value = state.estimatedValue,
                    onValueChange = { viewModel.updateEstimatedValue(it) },
                    placeholder = AppLocale.valuePlaceholder,
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
                    .background(AppColors.card)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(AppLocale.gradedCard, color = AppColors.textPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(AppLocale.gradedHint, color = AppColors.textMuted, fontSize = 12.sp)
                }
                Switch(
                    checked = state.isGraded,
                    onCheckedChange = { viewModel.updateIsGraded(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AppColors.textPrimary,
                        checkedTrackColor = AppColors.blue
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
                            label = AppLocale.gradeFieldLabel,
                            value = state.grade,
                            onValueChange = { viewModel.updateGrade(it) },
                            placeholder = AppLocale.gradePlaceholder,
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
                label = AppLocale.notes,
                value = state.notes,
                onValueChange = { viewModel.updateNotes(it) },
                placeholder = AppLocale.additionalNotesPlaceholder,
                singleLine = false,
                minHeight = 80.dp
            )

            // URL immagine
            FormField(
                label = AppLocale.imageUrlOptionalLabel,
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
            color = AppColors.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = if (minHeight > 0.dp) minHeight else 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.card)
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            if (value.isEmpty()) {
                Text(text = placeholder, color = AppColors.textMuted, fontSize = 14.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = AppColors.textPrimary,
                    fontSize = 14.sp
                ),
                singleLine = singleLine,
                cursorBrush = SolidColor(AppColors.blue),
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
            color = AppColors.textSecondary,
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
                    .background(AppColors.card)
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .clickable { expanded = true }
                    .padding(horizontal = 14.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = selected, color = AppColors.textPrimary, fontSize = 14.sp)
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess
                                      else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = AppColors.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(AppColors.surface)
            )  {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, color = AppColors.textPrimary, fontSize = 14.sp) },
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
