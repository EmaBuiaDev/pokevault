package com.emabuia.pokevault.ui.wishlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CatchingPokemon
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emabuia.pokevault.data.billing.PremiumManager
import com.emabuia.pokevault.data.model.Wishlist
import com.emabuia.pokevault.data.model.WishlistIcons
import com.emabuia.pokevault.ui.premium.PremiumRequiredDialog
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.WishlistViewModel

private data class WishlistIconOption(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val color: Color
)

// @Composable perche' i colori vengono dai token del tema, non piu' da
// costanti fisse.
@Composable
private fun wishlistIconOptions(): List<WishlistIconOption> = listOf(
    WishlistIconOption(WishlistIcons.POKEBALL, "Poke Ball", Icons.Default.CatchingPokemon, AppColors.red),
    WishlistIconOption(WishlistIcons.MASTER_BALL, "Master Ball", Icons.Default.Stars, AppColors.purple),
    WishlistIconOption(WishlistIcons.PIKACHU, "Pikachu", Icons.Default.Bolt, AppColors.gold),
    WishlistIconOption(WishlistIcons.CHARIZARD, "Charizard", Icons.Default.LocalFireDepartment, Color(0xFFE87A35)),
    WishlistIconOption(WishlistIcons.EEVEE, "Eevee", Icons.Default.Pets, AppColors.blue)
)

@Composable
private fun iconForKey(iconKey: String): WishlistIconOption {
    return wishlistIconOptions().firstOrNull { it.key == iconKey } ?: wishlistIconOptions().first()
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun WishlistListScreen(
    onBack: () -> Unit,
    onPremiumRequired: () -> Unit,
    onWishlistClick: (String) -> Unit,
    viewModel: WishlistViewModel = viewModel()
) {
    val premiumManager = remember { PremiumManager.getInstance() }
    val isPremium by premiumManager.isPremium.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showPremiumDialog by remember { mutableStateOf(false) }
    var wishlistToDelete by remember { mutableStateOf<Wishlist?>(null) }
    var wishlistToEdit by remember { mutableStateOf<Wishlist?>(null) }

    Scaffold(
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = AppLocale.wishlistTitle,
                        color = AppColors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
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
                    if (viewModel.canCreateWishlist(isPremium)) {
                        showCreateDialog = true
                    } else {
                        showPremiumDialog = true
                    }
                },
                containerColor = AppColors.purple,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = AppLocale.wishlistCreate, tint = AppColors.textPrimary)
            }
        }
    ) { padding ->
        when {
            viewModel.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AppColors.purple)
                }
            }

            viewModel.wishlists.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = null,
                            tint = AppColors.textMuted,
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = AppLocale.wishlistEmpty,
                            color = AppColors.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = AppLocale.wishlistEmptySubtitle,
                            color = AppColors.textMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 90.dp)
                ) {
                    items(viewModel.wishlists, key = { it.id }) { wishlist ->
                        WishlistRow(
                            wishlist = wishlist,
                            onClick = { onWishlistClick(wishlist.id) },
                            onEdit = { wishlistToEdit = wishlist },
                            onDelete = { wishlistToDelete = wishlist }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateWishlistDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, iconKey ->
                viewModel.createWishlist(name, iconKey, isPremium) { success ->
                    if (success) showCreateDialog = false
                }
            },
            isSaving = viewModel.isSaving
        )
    }

    wishlistToEdit?.let { wishlist ->
        CreateWishlistDialog(
            onDismiss = { wishlistToEdit = null },
            onConfirm = { name, iconKey ->
                viewModel.updateWishlistDetails(wishlist.id, name, iconKey) { success ->
                    if (success) wishlistToEdit = null
                }
            },
            isSaving = viewModel.isSaving,
            initialName = wishlist.name,
            initialIconKey = wishlist.iconKey,
            titleText = AppLocale.wishlistEdit,
            confirmText = AppLocale.save
        )
    }

    wishlistToDelete?.let { wishlist ->
        AlertDialog(
            onDismissRequest = { wishlistToDelete = null },
            containerColor = AppColors.surface,
            title = { Text(AppLocale.wishlistDeleteTitle, color = AppColors.textPrimary) },
            text = { Text(AppLocale.wishlistDeleteMessage, color = AppColors.textSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteWishlist(wishlist.id)
                        wishlistToDelete = null
                    }
                ) {
                    Text(AppLocale.delete, color = AppColors.red)
                }
            },
            dismissButton = {
                TextButton(onClick = { wishlistToDelete = null }) {
                    Text(AppLocale.cancel, color = AppColors.textMuted)
                }
            }
        )
    }

    if (showPremiumDialog) {
        PremiumRequiredDialog(
            title = AppLocale.premiumWishlistLimitTitle,
            message = AppLocale.premiumWishlistLimitMessage,
            onDismiss = { showPremiumDialog = false },
            onUpgrade = {
                showPremiumDialog = false
                onPremiumRequired()
            }
        )
    }

    LaunchedEffect(viewModel.successMessage, viewModel.errorMessage) {
        if (viewModel.successMessage != null || viewModel.errorMessage != null) {
            viewModel.clearMessages()
        }
    }
}

@Composable
private fun WishlistRow(
    wishlist: Wishlist,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val option = iconForKey(wishlist.iconKey)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.card.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .border(1.dp, AppColors.textMuted.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(option.color.copy(alpha = 0.2f), CircleShape)
                .border(1.dp, option.color.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(option.icon, contentDescription = null, tint = option.color, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.size(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = wishlist.name,
                color = AppColors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = AppLocale.wishlistCardsCount(wishlist.cardIds.size),
                color = AppColors.textMuted,
                fontSize = 12.sp
            )
        }

        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = AppLocale.wishlistEdit, tint = AppColors.textMuted)
        }

        IconButton(onClick = onDelete) {
            Icon(Icons.Default.DeleteOutline, contentDescription = AppLocale.delete, tint = AppColors.textMuted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateWishlistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
    isSaving: Boolean,
    canDismiss: Boolean = true,
    initialName: String = "",
    initialIconKey: String = WishlistIcons.POKEBALL,
    titleText: String = AppLocale.wishlistCreate,
    confirmText: String = AppLocale.wishlistCreate
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var selectedIconKey by remember(initialIconKey) { mutableStateOf(initialIconKey) }
    val options = wishlistIconOptions()

    AlertDialog(
        onDismissRequest = { if (canDismiss && !isSaving) onDismiss() },
        containerColor = AppColors.surface,
        title = {
            Text(
                text = titleText,
                color = AppColors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 40) name = it },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    label = { Text(AppLocale.wishlistName, color = AppColors.textMuted) },
                    placeholder = { Text(AppLocale.wishlistNamePlaceholder, color = AppColors.textMuted) }
                )

                Text(AppLocale.wishlistChooseIcon, color = AppColors.textSecondary, fontSize = 13.sp)

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    options.forEach { option ->
                        val selected = selectedIconKey == option.key
                        Row(
                            modifier = Modifier
                                .background(
                                    if (selected) option.color.copy(alpha = 0.22f) else AppColors.card,
                                    RoundedCornerShape(16.dp)
                                )
                                .border(
                                    1.dp,
                                    if (selected) option.color else AppColors.textMuted.copy(alpha = 0.25f),
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { selectedIconKey = option.key }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(option.icon, contentDescription = null, tint = option.color, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(option.label, color = AppColors.textPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim(), selectedIconKey) },
                enabled = name.trim().isNotBlank() && !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.purple)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.6.dp, color = AppColors.textPrimary)
                } else {
                    Text(confirmText, color = AppColors.textPrimary)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(AppLocale.cancel, color = AppColors.textMuted)
            }
        }
    )
}
