package com.example.pokevault.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pokevault.ui.theme.*

data class MenuItemData(
    val title: String,
    val icon: ImageVector,
    val gradientColors: List<Color>,
    val routeKey: String
)

@Composable
fun MenuGrid(
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val menuItems = listOf(
        MenuItemData(
            title = "Le mie\ncarte",
            icon = Icons.Default.Style,
            gradientColors = listOf(BlueCard, BlueCard.copy(alpha = 0.7f)),
            routeKey = "my_cards"
        ),
        MenuItemData(
            title = "Statistiche",
            icon = Icons.Default.BarChart,
            gradientColors = listOf(PurpleCard, PurpleCard.copy(alpha = 0.7f)),
            routeKey = "statistics"
        ),
        MenuItemData(
            title = "Carte\ngradate",
            icon = Icons.Default.Star,
            gradientColors = listOf(GreenCard, GreenCard.copy(alpha = 0.7f)),
            routeKey = "graded"
        ),
        MenuItemData(
            title = "Pokédex",
            icon = Icons.Default.CatchingPokemon,
            gradientColors = listOf(YellowCard, RedCard),
            routeKey = "pokedex"
        )
    )

    val deckLabItem = MenuItemData(
        title = "Deck Lab",
        icon = Icons.Default.Science,
        gradientColors = listOf(LavenderCard, LavenderCard.copy(alpha = 0.7f)),
        routeKey = "deck_lab"
    )

    Column(
        modifier = modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Prima riga: 2 card
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            MenuCard(
                item = menuItems[0],
                onClick = { onItemClick(menuItems[0].routeKey) },
                modifier = Modifier.weight(1f)
            )
            MenuCard(
                item = menuItems[1],
                onClick = { onItemClick(menuItems[1].routeKey) },
                modifier = Modifier.weight(1f)
            )
        }

        // Seconda riga: 2 card
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            MenuCard(
                item = menuItems[2],
                onClick = { onItemClick(menuItems[2].routeKey) },
                modifier = Modifier.weight(1f)
            )
            MenuCard(
                item = menuItems[3],
                onClick = { onItemClick(menuItems[3].routeKey) },
                modifier = Modifier.weight(1f)
            )
        }

        // Terza riga: card larga - Deck Lab
        MenuCard(
            item = deckLabItem,
            onClick = { onItemClick(deckLabItem.routeKey) },
            modifier = Modifier.fillMaxWidth(),
            isWide = true
        )
    }
}

@Composable
fun MenuCard(
    item: MenuItemData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isWide: Boolean = false
) {
    Box(
        modifier = modifier
            .height(if (isWide) 60.dp else 100.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.linearGradient(item.gradientColors)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        if (isWide) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = item.title.replace("\n", " "),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Bottom
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
