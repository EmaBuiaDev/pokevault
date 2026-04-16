package com.emabuia.pokevault.ui.album

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.AlbumViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAlbumScreen(
    onBack: () -> Unit,
    editAlbumId: String? = null,
    viewModel: AlbumViewModel = viewModel()
) {
    // Se siamo in modifica, carica i dati dell'album
    LaunchedEffect(editAlbumId) {
        if (editAlbumId != null) {
            val album = viewModel.getAlbumById(editAlbumId)
            if (album != null) {
                viewModel.loadAlbumForEdit(album)
            }
        } else {
            viewModel.resetForm()
        }
    }

    val isEditing = editAlbumId != null
    val scrollState = rememberScrollState()

    val pokemonTypes = listOf("") + AppLocale.getTypes()
    val expansions = listOf("") + viewModel.getAvailableExpansions()
    val categories = listOf("", "Pokémon", "Trainer", "Energy")
    val sizes = listOf(9, 18, 36, 72, 120)
    val themes = listOf("classic", "fire", "water", "grass", "electric", "dark", "psychic")

    var showTypeDropdown by remember { mutableStateOf(false) }
    var showExpansionDropdown by remember { mutableStateOf(false) }
    var showCategoryDropdown by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEditing) AppLocale.editAlbum else AppLocale.createAlbum,
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
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

            // Nome Album
            OutlinedTextField(
                value = viewModel.albumName,
                onValueChange = { viewModel.albumName = it },
                label = { Text(AppLocale.albumName) },
                placeholder = { Text(AppLocale.albumNamePlaceholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OrangeCard,
                    unfocusedBorderColor = TextMuted,
                    cursorColor = OrangeCard,
                    focusedLabelColor = OrangeCard,
                    unfocusedLabelColor = TextMuted,
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    focusedPlaceholderColor = TextMuted,
                    unfocusedPlaceholderColor = TextMuted
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Descrizione
            OutlinedTextField(
                value = viewModel.albumDescription,
                onValueChange = { viewModel.albumDescription = it },
                label = { Text(AppLocale.albumDescription) },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OrangeCard,
                    unfocusedBorderColor = TextMuted,
                    cursorColor = OrangeCard,
                    focusedLabelColor = OrangeCard,
                    unfocusedLabelColor = TextMuted,
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    focusedPlaceholderColor = TextMuted,
                    unfocusedPlaceholderColor = TextMuted
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Tipo Pokémon Dropdown
            SectionLabel(AppLocale.albumPokemonType)
            ExposedDropdownMenuBox(
                expanded = showTypeDropdown,
                onExpandedChange = { showTypeDropdown = it }
            ) {
                OutlinedTextField(
                    value = if (viewModel.albumPokemonType.isBlank()) AppLocale.albumAllTypes
                    else AppLocale.translateType(viewModel.albumPokemonType),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showTypeDropdown) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OrangeCard,
                        unfocusedBorderColor = TextMuted,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedTrailingIconColor = OrangeCard,
                        unfocusedTrailingIconColor = TextMuted
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = showTypeDropdown,
                    onDismissRequest = { showTypeDropdown = false },
                    containerColor = DarkSurface
                ) {
                    pokemonTypes.forEach { type ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (type.isBlank()) AppLocale.albumAllTypes else type,
                                    color = TextWhite
                                )
                            },
                            onClick = {
                                viewModel.albumPokemonType = type
                                showTypeDropdown = false
                            }
                        )
                    }
                }
            }

            // Espansione Dropdown
            SectionLabel(AppLocale.albumExpansion)
            ExposedDropdownMenuBox(
                expanded = showExpansionDropdown,
                onExpandedChange = { showExpansionDropdown = it }
            ) {
                OutlinedTextField(
                    value = if (viewModel.albumExpansion.isBlank()) AppLocale.albumAllExpansions
                    else viewModel.albumExpansion,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showExpansionDropdown) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OrangeCard,
                        unfocusedBorderColor = TextMuted,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedTrailingIconColor = OrangeCard,
                        unfocusedTrailingIconColor = TextMuted
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = showExpansionDropdown,
                    onDismissRequest = { showExpansionDropdown = false },
                    containerColor = DarkSurface
                ) {
                    expansions.forEach { exp ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (exp.isBlank()) AppLocale.albumAllExpansions else exp,
                                    color = TextWhite
                                )
                            },
                            onClick = {
                                viewModel.albumExpansion = exp
                                showExpansionDropdown = false
                            }
                        )
                    }
                }
            }

            // Categoria Dropdown
            SectionLabel(AppLocale.albumCategory)
            ExposedDropdownMenuBox(
                expanded = showCategoryDropdown,
                onExpandedChange = { showCategoryDropdown = it }
            ) {
                OutlinedTextField(
                    value = if (viewModel.albumSupertype.isBlank()) AppLocale.albumAllCategories
                    else viewModel.albumSupertype,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCategoryDropdown) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OrangeCard,
                        unfocusedBorderColor = TextMuted,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedTrailingIconColor = OrangeCard,
                        unfocusedTrailingIconColor = TextMuted
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = showCategoryDropdown,
                    onDismissRequest = { showCategoryDropdown = false },
                    containerColor = DarkSurface
                ) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (cat.isBlank()) AppLocale.albumAllCategories else cat,
                                    color = TextWhite
                                )
                            },
                            onClick = {
                                viewModel.albumSupertype = cat
                                showCategoryDropdown = false
                            }
                        )
                    }
                }
            }

            // Grandezza Album
            SectionLabel(AppLocale.albumSize)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                sizes.forEach { size ->
                    val isSelected = viewModel.albumSize == size
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) OrangeCard else DarkCard)
                            .border(
                                width = if (isSelected) 0.dp else 1.dp,
                                color = if (isSelected) Color.Transparent else TextMuted,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { viewModel.albumSize = size },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$size",
                            color = if (isSelected) Color.White else TextGray,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Tematica
            SectionLabel(AppLocale.albumTheme)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                themes.forEach { theme ->
                    val isSelected = viewModel.albumTheme == theme
                    val colors = getThemeColors(theme)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(brush = Brush.linearGradient(colors))
                            .then(
                                if (isSelected) Modifier.border(
                                    2.dp,
                                    Color.White,
                                    CircleShape
                                ) else Modifier
                            )
                            .clickable { viewModel.albumTheme = theme }
                    )
                }
            }

            // Theme name label
            Text(
                text = getThemeLabel(viewModel.albumTheme),
                color = TextMuted,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Save button
            Button(
                onClick = { viewModel.saveAlbum(onSuccess = onBack) },
                enabled = viewModel.albumName.isNotBlank() && !viewModel.isSaving,
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

private fun getThemeLabel(theme: String): String {
    return when (theme) {
        "fire" -> AppLocale.albumThemeFire
        "water" -> AppLocale.albumThemeWater
        "grass" -> AppLocale.albumThemeGrass
        "electric" -> AppLocale.albumThemeElectric
        "dark" -> AppLocale.albumThemeDark
        "psychic" -> AppLocale.albumThemePsychic
        else -> AppLocale.albumThemeClassic
    }
}
