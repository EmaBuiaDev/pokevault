package com.emabuia.pokevault.ui.trade

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.data.trade.TradeApi
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale

/**
 * TradeRadar, fase 0: esiste solo nel flavor staging e per ora dice una cosa
 * sola, se l'app arriva al suo server autenticata. E' la prova che la catena
 * app staging -> Worker di staging -> progetto Firebase di staging tiene,
 * prima di costruirci sopra match e proposte.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeRadarScreen(onBack: () -> Unit) {
    var attempt by remember { mutableIntStateOf(0) }
    var check by remember { mutableStateOf<TradeApi.Check?>(null) }

    LaunchedEffect(attempt) {
        check = null
        check = TradeApi.checkConnection()
    }

    Scaffold(
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = { Text(AppLocale.tradeRadarTitle, fontWeight = FontWeight.Bold, color = AppColors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, AppLocale.back, tint = AppColors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                AppLocale.tradeRadarPreview,
                color = AppColors.textSecondary,
                fontSize = 14.sp
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.searchBar)
                    .padding(16.dp)
            ) {
                when (val current = check) {
                    null -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    is TradeApi.Check.Ok -> Icon(Icons.Default.CheckCircle, null, tint = AppColors.green)
                    TradeApi.Check.Unauthorized -> Icon(Icons.Default.Lock, null, tint = AppColors.orange)
                    is TradeApi.Check.Unavailable -> Icon(Icons.Default.CloudOff, null, tint = AppColors.red)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = when (val current = check) {
                        null -> AppLocale.tradeRadarChecking
                        is TradeApi.Check.Ok -> AppLocale.tradeRadarConnected(current.uid.take(8), current.schemaVersion)
                        TradeApi.Check.Unauthorized -> AppLocale.tradeRadarUnauthorized
                        is TradeApi.Check.Unavailable -> AppLocale.tradeRadarUnavailable(current.httpCode)
                    },
                    color = AppColors.textPrimary,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
            }

            if (check != null && check !is TradeApi.Check.Ok) {
                OutlinedButton(onClick = { attempt++ }) { Text(AppLocale.retry) }
            }
        }
    }
}
