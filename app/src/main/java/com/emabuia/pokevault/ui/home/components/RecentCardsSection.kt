package com.emabuia.pokevault.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.ui.collection.CollectionCardGridItem
import com.emabuia.pokevault.ui.components.SkeletonBlock
import com.emabuia.pokevault.ui.components.pressScale
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.CardGroup
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Quanto si aspetta prima di ammettere che si sta caricando.
 *
 * La cache locale di Firestore risponde quasi sempre entro pochi frame: uno
 * scheletro che parte subito trasforma quell'istante in un lampo di "sto
 * caricando" sotto i menu. Sotto questa soglia la sezione tiene solo lo
 * spazio, in silenzio; oltre, lo scheletro entra e l'attesa e' vera.
 */
private const val SkeletonGraceMs = 300L

private val TileWidth = 104.dp

/** Tessera piu' nome e data sotto: lo spazio che la sezione tiene mentre tace. */
private val RowHeight = 186.dp

/**
 * Le ultime carte aggiunte.
 *
 * Al posto della vecchia riga "Collezione", che mostrava venti carte in un
 * ordine casuale. Questa risponde a una domanda vera -- "l'ultima carta che ho
 * scansionato e' entrata?" -- e porta sulla carta con un tocco.
 */
@Composable
fun RecentCardsSection(
    groups: List<CardGroup>,
    hasCards: Boolean,
    isLoading: Boolean,
    onCardClick: (String) -> Unit,
    onSeeAll: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showSkeleton by produceState(initialValue = false, isLoading) {
        value = false
        if (isLoading) {
            delay(SkeletonGraceMs)
            value = true
        }
    }

    Column(modifier = modifier.padding(top = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = AppLocale.homeRecentTitle,
                color = AppColors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (!isLoading && hasCards) {
                TextButton(onClick = onSeeAll) {
                    Text(AppLocale.collectorSeeAll, color = AppColors.blue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = AppColors.blue,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        when {
            // Una lista vuota mentre si carica non vuol dire "collezione
            // vuota": vuol dire "non lo so ancora". Senza questo ramo l'invito
            // a scansionare lampeggerebbe a ogni avvio, anche su collezioni piene.
            isLoading && showSkeleton -> RecentRowSkeleton()
            isLoading -> Spacer(modifier = Modifier.height(RowHeight))
            !hasCards -> EmptyInvite(onScan = onScan)
            else -> LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(groups, key = { it.key }) { group ->
                    RecentTile(group = group, onClick = { onCardClick(group.key) })
                }
            }
        }
    }
}

@Composable
private fun RecentTile(group: CardGroup, onClick: () -> Unit) {
    val added = remember(group.newestAddedSeconds) { relativeAddedLabel(group.newestAddedSeconds) }
    Column(modifier = Modifier.width(TileWidth)) {
        CollectionCardGridItem(
            card = group.representative,
            gridColumns = 3,
            ownedVariants = group.variants,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            group.representative.name,
            color = AppColors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (added != null) {
            Text(added, color = AppColors.textMuted, fontSize = 11.sp, maxLines = 1)
        }
    }
}

/**
 * "Oggi", "Ieri", "3 giorni fa", poi la data. Null per le carte vecchie senza
 * data d'inserimento: meglio niente che una data inventata.
 */
private fun relativeAddedLabel(seconds: Long): String? {
    if (seconds == Long.MIN_VALUE || seconds <= 0L) return null
    val addedMs = TimeUnit.SECONDS.toMillis(seconds)
    val today = startOfDay(System.currentTimeMillis())
    val day = startOfDay(addedMs)
    val days = TimeUnit.MILLISECONDS.toDays(today - day).toInt()
    return when {
        days <= 0 -> AppLocale.addedToday
        days == 1 -> AppLocale.addedYesterday
        days < 7 -> AppLocale.addedDaysAgo(days)
        else -> SimpleDateFormat("d MMM", if (AppLocale.isItalian) Locale.ITALIAN else Locale.ENGLISH).format(Date(addedMs))
    }
}

private fun startOfDay(millis: Long): Long {
    val calendar = java.util.Calendar.getInstance()
    calendar.timeInMillis = millis
    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
    calendar.set(java.util.Calendar.MINUTE, 0)
    calendar.set(java.util.Calendar.SECOND, 0)
    calendar.set(java.util.Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

/** Collezione vuota: al posto di una scritta, la strada per la prima carta. */
@Composable
private fun EmptyInvite(onScan: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(AppColors.blue.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .border(1.dp, AppColors.blue.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .pressScale(onClick = onScan)
            .padding(16.dp)
    ) {
        Icon(
            Icons.Default.DocumentScanner,
            contentDescription = null,
            tint = AppColors.blue,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(AppLocale.homeEmptyTitle, color = AppColors.textPrimary, fontWeight = FontWeight.SemiBold)
            Text(AppLocale.homeEmptyHint, color = AppColors.textMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun RecentRowSkeleton() {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(3) { index ->
            Column {
                SkeletonBlock(
                    modifier = Modifier.width(TileWidth).aspectRatio(0.72f),
                    shape = RoundedCornerShape(10.dp),
                    index = index
                )
                Spacer(modifier = Modifier.height(6.dp))
                SkeletonBlock(
                    modifier = Modifier.width(72.dp).height(12.dp),
                    shape = RoundedCornerShape(6.dp),
                    index = index
                )
            }
        }
    }
}
