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
import com.emabuia.pokevault.ui.theme.BlueCard
import com.emabuia.pokevault.ui.theme.DarkBackground
import com.emabuia.pokevault.ui.theme.DarkCard
import com.emabuia.pokevault.ui.theme.DarkSurface
import com.emabuia.pokevault.ui.theme.PurpleCard
import com.emabuia.pokevault.ui.theme.RedCard
import com.emabuia.pokevault.ui.theme.StarGold
import com.emabuia.pokevault.ui.theme.TextGray
import com.emabuia.pokevault.ui.theme.TextMuted
import com.emabuia.pokevault.ui.theme.TextWhite
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.WishlistViewModel

private data class WishlistIconOption(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val color: Color
)

private fun wishlistIconOptions(): List<WishlistIconOption> = listOf(
    WishlistIconOption(WishlistIcons.POKEBALL, "Poke Ball", Icons.Default.CatchingPokemon, RedCard),
    WishlistIconOption(WishlistIcons.MASTER_BALL, "Master Ball", Icons.Default.Stars, PurpleCard),
    WishlistIconOption(WishlistIcons.PIKACHU, "Pikachu", Icons.Default.Bolt, StarGold),
    WishlistIconOption(WishlistIcons.CHARIZARD, "Charizard", Icons.Default.LocalFireDepartment, Color(0xFFE87A35)),
    WishlistIconOption(WishlistIcons.EEVEE, "Eevee", Icons.Default.Pets, BlueCard)
)

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
    val isPremium by premiumManager.isPremium.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showPremiumDialog by remember { mutableStateOf(false) }
    var wishlistToDelete by remember { mutableStateOf<Wishlist?>(null) }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = AppLocale.wishlistTitle,
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
                containerColor = PurpleCard,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = AppLocale.wishlistCreate, tint = TextWhite)
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
                    CircularProgressIndicator(color = PurpleCard)
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
                            tint = TextMuted,
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = AppLocale.wishlistEmpty,
                            color = TextWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = AppLocale.wishlistEmptySubtitle,
                            color = TextMuted,
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
                viewModel.createWishlist(name, iconKey) { success ->
                    if (success) showCreateDialog = false
                }
            },
            isSaving = viewModel.isSaving
        )
    }

    wishlistToDelete?.let { wishlist ->
        AlertDialog(
            onDismissRequest = { wishlistToDelete = null },
            containerColor = DarkSurface,
            title = { Text(AppLocale.wishlistDeleteTitle, color = TextWhite) },
            text = { Text(AppLocale.wishlistDeleteMessage, color = TextGray) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteWishlist(wishlist.id)
                        wishlistToDelete = null
                    }
                ) {
                    Text(AppLocale.delete, color = RedCard)
                }
            },
            dismissButton = {
                TextButton(onClick = { wishlistToDelete = null }) {
                    Text(AppLocale.cancel, color = TextMuted)
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
    onDelete: () -> Unit
) {
    val option = iconForKey(wishlist.iconKey)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkCard.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .border(1.dp, TextMuted.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
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
                color = TextWhite,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = AppLocale.wishlistCardsCount(wishlist.cardIds.size),
                color = TextMuted,
                fontSize = 12.sp
            )
        }

        IconButton(onClick = onDelete) {
            Icon(Icons.Default.DeleteOutline, contentDescription = AppLocale.delete, tint = TextMuted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateWishlistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
    isSaving: Boolean,
    canDismiss: Boolean = true
) {
    var name by remember { mutableStateOf("") }
    var selectedIconKey by remember { mutableStateOf(WishlistIcons.POKEBALL) }
    val options = remember { wishlistIconOptions() }

    AlertDialog(
        onDismissRequest = { if (canDismiss && !isSaving) onDismiss() },
        containerColor = DarkSurface,
        title = {
            Text(
                text = AppLocale.wishlistCreate,
                color = TextWhite,
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
                    label = { Text(AppLocale.wishlistName, color = TextMuted) },
                    placeholder = { Text(AppLocale.wishlistNamePlaceholder, color = TextMuted) }
                )

                Text(AppLocale.wishlistChooseIcon, color = TextGray, fontSize = 13.sp)

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    options.forEach { option ->
                        val selected = selectedIconKey == option.key
                        Row(
                            modifier = Modifier
                                .background(
                                    if (selected) option.color.copy(alpha = 0.22f) else DarkCard,
                                    RoundedCornerShape(16.dp)
                                )
                                .border(
                                    1.dp,
                                    if (selected) option.color else TextMuted.copy(alpha = 0.25f),
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { selectedIconKey = option.key }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(option.icon, contentDescription = null, tint = option.color, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(option.label, color = TextWhite, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim(), selectedIconKey) },
                enabled = name.trim().isNotBlank() && !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = PurpleCard)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.6.dp, color = TextWhite)
                } else {
                    Text(AppLocale.addCard, color = TextWhite)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(AppLocale.cancel, color = TextMuted)
            }
        }
    )
}
