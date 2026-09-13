package com.emabuia.pokevault.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emabuia.pokevault.data.billing.PremiumManager
import com.emabuia.pokevault.ui.components.EmptyStateView
import com.emabuia.pokevault.ui.components.SkeletonBlock
import com.emabuia.pokevault.ui.components.pressScale
import com.emabuia.pokevault.ui.premium.PremiumRequiredDialog
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AlbumRow
import com.emabuia.pokevault.util.AlbumSort
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.CollectorLab
import com.emabuia.pokevault.viewmodel.AlbumViewModel

/**
 * La lista degli album.
 *
 * Con piu' di tre album la lista semplice non basta piu': qui si cerca, si
 * ordina e ogni riga dice a che punto e' l'album e quanto vale, senza doverlo
 * aprire.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumCollectionListScreen(
    onBack: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onCreateAlbum: (String?) -> Unit,
    onPremiumRequired: () -> Unit,
    viewModel: AlbumViewModel = viewModel()
) {
    val premiumManager = remember { PremiumManager.getInstance() }
    var showPremiumDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<AlbumRow?>(null) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(AlbumSort.RECENT) }

    val rows = viewModel.albumRows
    val visibleRows = remember(rows, query, sort) {
        CollectorLab.sortAlbums(CollectorLab.filterAlbums(rows, query), sort)
    }

    Scaffold(
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            AppLocale.myAlbums,
                            color = AppColors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        if (rows.isNotEmpty()) {
                            Text(
                                AppLocale.albumsSummary(rows.size, rows.sumOf { it.used }),
                                color = AppColors.textMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = AppLocale.back,
                            tint = AppColors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (premiumManager.canCreateAlbum(rows.size)) {
                        onCreateAlbum(null)
                    } else {
                        showPremiumDialog = true
                    }
                },
                containerColor = AppColors.orange,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = AppLocale.createAlbum, tint = AppColors.textPrimary)
            }
        }
    ) { padding ->
        // Lo scheletro distingue "sto caricando" da "non hai album": prima la
        // lista mostrava lo stato vuoto anche mentre Firestore rispondeva.
        if (viewModel.isLoading && rows.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                repeat(4) { index ->
                    SkeletonBlock(
                        modifier = Modifier.fillMaxWidth().height(104.dp),
                        shape = RoundedCornerShape(16.dp),
                        index = index
                    )
                }
            }
            return@Scaffold
        }

        if (rows.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                EmptyStateView(
                    icon = Icons.Default.PhotoAlbum,
                    title = AppLocale.albumEmpty,
                    subtitle = AppLocale.albumEmptySubtitle
                )
                Button(
                    onClick = {
                        if (premiumManager.canCreateAlbum(0)) onCreateAlbum(null)
                        else showPremiumDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.orange),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(AppLocale.albumCreateCta, color = AppColors.textPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item(key = "controls") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CollectorSearchField(
                        value = query,
                        onValueChange = { query = it },
                        hint = AppLocale.albumSearchHint
                    )
                    SortChipRow(
                        labels = listOf(
                            AppLocale.albumSortRecent,
                            AppLocale.albumSortName,
                            AppLocale.albumSortFill,
                            AppLocale.albumSortValue
                        ),
                        selectedIndex = sort.ordinal,
                        onSelect = { index -> sort = AlbumSort.entries[index] }
                    )
                }
            }

            if (visibleRows.isEmpty()) {
                item(key = "no-results") {
                    Text(
                        AppLocale.albumNoResults,
                        color = AppColors.textMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }

            items(visibleRows, key = { it.id }) { row ->
                AlbumRowCard(
                    row = row,
                    onClick = { onAlbumClick(row.id) },
                    onDelete = { showDeleteDialog = row },
                    onEdit = { onCreateAlbum(row.id) }
                )
            }

            item(key = "fab-space") { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    showDeleteDialog?.let { row ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            containerColor = AppColors.surface,
            title = { Text(AppLocale.albumDeleteTitle, color = AppColors.textPrimary) },
            text = {
                Column {
                    Text(row.name, color = AppColors.textPrimary, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(AppLocale.albumDeleteMessage, color = AppColors.textSecondary)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAlbum(row.id)
                    showDeleteDialog = null
                }) {
                    Text(AppLocale.delete, color = AppColors.red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(AppLocale.cancel, color = AppColors.textMuted)
                }
            }
        )
    }

    if (showPremiumDialog) {
        PremiumRequiredDialog(
            title = AppLocale.premiumAlbumLimitTitle,
            message = AppLocale.premiumAlbumLimitMessage,
            onDismiss = { showPremiumDialog = false },
            onUpgrade = {
                showPremiumDialog = false
                onPremiumRequired()
            }
        )
    }
}

/**
 * Una riga della lista album.
 *
 * Mostra le prime carte a ventaglio, il riempimento e il valore: prima si
 * vedeva solo la copertina e il conteggio, e due album diversi si somigliavano.
 */
@Composable
private fun AlbumRowCard(
    row: AlbumRow,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val gradient = getThemeColors(row.theme)
    val covers = remember(row) {
        if (row.coverUrl.isNotBlank()) {
            (listOf(row.coverUrl) + row.previewUrls).distinct().take(3)
        } else {
            row.previewUrls
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.card)
            .pressScale(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverCollage(urls = covers, gradient = gradient, slotSize = 66.dp)

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.name,
                    color = AppColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (row.description.isNotBlank()) {
                    Text(
                        text = row.description,
                        color = AppColors.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = AppLocale.albumSlots(row.used, row.size),
                        color = AppColors.textMuted,
                        fontSize = 12.sp
                    )
                    if (row.value > 0.0) {
                        Text(
                            text = formatEurCompact(row.value),
                            color = AppColors.green,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (row.pokemonType.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(gradient.first().copy(alpha = 0.18f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = AppLocale.translateType(row.pokemonType),
                                color = gradient.first(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Column {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = AppLocale.editAlbum,
                        tint = AppColors.textMuted,
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = AppLocale.delete,
                        tint = AppColors.textMuted,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            FillBar(
                percent = row.fillPercent,
                modifier = Modifier.weight(1f),
                accent = gradient.first()
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (row.isFull) AppLocale.albumFull else "${row.fillPercent.toInt()}%",
                color = if (row.isFull) AppColors.gold else AppColors.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
