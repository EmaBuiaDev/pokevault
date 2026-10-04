package com.emabuia.pokevault.ui.trade

import android.content.Context
import android.os.Build.VERSION.SDK_INT
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Il Pokemon che compare sul podio della classifica al posto dell'iniziale.
 *
 * Solo sul podio: nelle liste resta l'iniziale, cosi' le righe non caricano
 * immagini e si scorrono come prima. Una trentina di Pokemon sono di tutti,
 * gli altri e gli sprite animati sono Premium.
 */
internal object TradeAvatars {

    /** Gratis: Pikachu, Eevee, Snorlax e gli starter di ogni generazione. */
    val FREE: List<Int> = listOf(
        25, 133, 143,
        1, 4, 7, 152, 155, 158, 252, 255, 258, 387, 390, 393,
        495, 498, 501, 650, 653, 656, 722, 725, 728, 810, 813, 816, 906, 909, 912
    )
    private val freeSet = FREE.toSet()

    /** Gli sprite animati (Nero e Bianco) esistono fino a Genesect. */
    const val ANIMATED_MAX = 649

    private const val BASE = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon"

    fun isFree(id: Int) = id in freeSet

    fun url(id: Int, animated: Boolean): String =
        if (animated && id <= ANIMATED_MAX) "$BASE/versions/generation-v/black-white/animated/$id.gif" else "$BASE/$id.png"

    @Volatile
    private var species: List<Pair<Int, String>>? = null

    /** Numero e nome di tutte le specie, dall'asset; letto una volta sola, fuori dal thread principale. */
    suspend fun species(context: Context): List<Pair<Int, String>> {
        species?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                context.applicationContext.assets.open("pokemon_species.txt").bufferedReader().useLines { lines ->
                    lines.mapNotNull { line ->
                        if (line.isBlank() || line.startsWith("#")) return@mapNotNull null
                        val id = line.substringBefore(',').trim().toIntOrNull() ?: return@mapNotNull null
                        id to displayName(line.substringAfter(',').trim())
                    }.distinctBy { it.first }.sortedBy { it.first }.toList()
                }
            }.getOrDefault(emptyList()).also { if (it.isNotEmpty()) species = it }
        }
    }

    /** "mr-mime" -> "Mr Mime": i nomi di PokeAPI, leggibili. */
    private fun displayName(raw: String) = raw.split('-').joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
}

/** Un ImageLoader che sa anche le GIF: serve per gli sprite animati. */
@Composable
internal fun rememberSpriteLoader(): ImageLoader {
    val context = LocalContext.current
    return remember(context) {
        ImageLoader.Builder(context)
            .components {
                if (SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
            }
            .build()
    }
}

/**
 * Lo sprite di un Pokemon, nitido (sono pixel art: niente sfumatura
 * nell'ingrandirli). Se l'immagine non arriva si mostra [fallback].
 */
@Composable
internal fun PokemonSprite(
    id: Int,
    animated: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    loader: ImageLoader? = null,
    fallback: @Composable () -> Unit = {}
) {
    var failed by remember(id, animated) { mutableStateOf(false) }
    if (failed) {
        fallback()
        return
    }
    val context = LocalContext.current
    val request = remember(id, animated) {
        ImageRequest.Builder(context).data(TradeAvatars.url(id, animated)).crossfade(true).build()
    }
    if (loader != null) {
        AsyncImage(
            model = request,
            contentDescription = null,
            imageLoader = loader,
            filterQuality = FilterQuality.None,
            onError = { failed = true },
            modifier = modifier.size(size)
        )
    } else {
        AsyncImage(
            model = request,
            contentDescription = null,
            filterQuality = FilterQuality.None,
            onError = { failed = true },
            modifier = modifier.size(size)
        )
    }
}

/**
 * La scelta del Pokemon del podio: prima i gratis, poi tutti gli altri
 * (con il lucchetto se non si e' Premium: toccarli porta a Premium).
 * In cima la ricerca per nome o numero e, per i Premium, lo sprite animato.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AvatarPickerDialog(
    current: Int?,
    currentAnimated: Boolean,
    isPremium: Boolean,
    onPick: (avatar: Int?, animated: Boolean) -> Unit,
    onPremiumRequired: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var species by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    LaunchedEffect(Unit) { species = TradeAvatars.species(context) }
    var query by rememberSaveable { mutableStateOf("") }
    // Chi e' Premium e non ha ancora scelto parte con l'animato acceso.
    var animated by rememberSaveable { mutableStateOf(if (current != null) currentAnimated else isPremium) }
    val filtered = remember(species, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) species else species.filter { (id, name) -> name.lowercase().contains(q) || id.toString() == q }
    }
    val free = remember(filtered) { filtered.filter { TradeAvatars.isFree(it.first) }.sortedBy { TradeAvatars.FREE.indexOf(it.first) } }
    val others = remember(filtered) { filtered.filterNot { TradeAvatars.isFree(it.first) } }
    val fullSpan: (LazyGridItemSpanScope) -> GridItemSpan = { GridItemSpan(it.maxLineSpan) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            containerColor = AppColors.background,
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(AppLocale.tradeRadarAvatarTitle, fontWeight = FontWeight.Bold, color = AppColors.textPrimary) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, AppLocale.tradeRadarClose, tint = AppColors.textPrimary) }
                    },
                    actions = {
                        if (current != null) TextButton(onClick = { onPick(null, false) }) { Text(AppLocale.tradeRadarAvatarNone) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background)
                )
            }
        ) { padding ->
            LazyVerticalGrid(
                columns = GridCells.Adaptive(76.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "hint", span = { fullSpan(this) }) {
                    Text(AppLocale.tradeRadarAvatarHint, fontSize = 12.sp, color = AppColors.textSecondary)
                }
                item(key = "search", span = { fullSpan(this) }) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(AppLocale.tradeRadarAvatarSearch) },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (isPremium) {
                    item(key = "animated", span = { fullSpan(this) }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppColors.card).padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(AppLocale.tradeRadarAvatarAnimated, fontSize = 13.sp, color = AppColors.textPrimary, modifier = Modifier.weight(1f))
                            Switch(checked = animated, onCheckedChange = { animated = it })
                        }
                    }
                }
                if (free.isNotEmpty()) {
                    item(key = "freeLabel", span = { fullSpan(this) }) { SectionLabel(AppLocale.tradeRadarAvatarFree) }
                    items(free, key = { "free|${it.first}" }) { (id, name) ->
                        AvatarCell(id, name, selected = id == current, locked = false) { onPick(id, isPremium && animated) }
                    }
                }
                if (others.isNotEmpty()) {
                    item(key = "allLabel", span = { fullSpan(this) }) {
                        SectionLabel(if (isPremium) AppLocale.tradeRadarAvatarAll else "🔒 " + AppLocale.tradeRadarAvatarPremium)
                    }
                    items(others, key = { "all|${it.first}" }) { (id, name) ->
                        AvatarCell(id, name, selected = id == current, locked = !isPremium) {
                            if (isPremium) onPick(id, animated) else onPremiumRequired()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AppColors.textPrimary, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun AvatarCell(id: Int, name: String, selected: Boolean, locked: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) AppColors.blue.copy(alpha = 0.15f) else AppColors.card)
            .then(if (selected) Modifier.border(2.dp, AppColors.blue, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            PokemonSprite(id, animated = false, size = 56.dp, modifier = Modifier.graphicsLayer { alpha = if (locked) 0.35f else 1f })
            if (locked) Text("🔒", fontSize = 12.sp)
        }
        Text(name, fontSize = 10.sp, color = AppColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
