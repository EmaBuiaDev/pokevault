package com.example.pokevault.ui.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pokevault.ui.theme.TextGray

@Composable
fun WelcomeHeader(
    userName: String = "Allenatore",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pokeball emoji come icona
        Text(
            text = "🔴",
            fontSize = 32.sp
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = "Ciao, $userName!",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Gestisci la tua collezione con stile ✨",
                style = MaterialTheme.typography.bodyMedium,
                color = TextGray
            )
        }
    }
}
