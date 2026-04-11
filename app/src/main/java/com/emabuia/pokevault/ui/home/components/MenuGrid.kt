package com.emabuia.pokevault.ui.home.components

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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale

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
            title = AppLocale.myCards,
            icon = Icons.Default.Style,
            gradientColors = listOf(BlueCard, BlueCard.copy(alpha = 0.7f)),
            routeKey = "my_cards"
        ),
        MenuItemData(
            title = AppLocale.statistics,
            icon = Icons.Default.BarChart,
            gradientColors = listOf(PurpleCard, PurpleCard.copy(alpha = 0.7f)),
            routeKey = "statistics"
        ),
        MenuItemData(
            title = AppLocale.gradedCardsTitle,
            icon = Icons.Default.Star,
            gradientColors = listOf(GreenCard, GreenCard.copy(alpha = 0.7f)),
            routeKey = "graded"
        ),
        MenuItemData(
            title = AppLocale.cardsAndExpansions,
            icon = Icons.Default.CatchingPokemon,
            gradientColors = listOf(YellowCard, RedCard),
            routeKey = "pokedex"
        )
    )

    val competitiveItem = MenuItemData(
        title = AppLocale.competitiveTitle,
        icon = Icons.Default.EmojiEvents,
        gradientColors = listOf(LavenderCard, LavenderCard.copy(alpha = 0.6f)),
        routeKey = "competitive"
    )

    val albumItem = MenuItemData(
        title = AppLocale.albumTitle,
        icon = Icons.Default.PhotoAlbum,
        gradientColors = listOf(OrangeCard, OrangeCard.copy(alpha = 0.7f)),
        routeKey = "album"
    )

    Column(
        modifier = modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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

        FeaturedCard(
            item = competitiveItem,
            subtitle = AppLocale.competitiveSubtitle,
            onClick = { onItemClick(competitiveItem.routeKey) }
        )

        FeaturedCard(
            item = albumItem,
            subtitle = AppLocale.albumSubtitle,
            onClick = { onItemClick(albumItem.routeKey) }
        )
    }
}

@Composable
fun FeaturedCard(
    item: MenuItemData,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = item.gradientColors
                )
            )
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = Icons.Default.Style,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.1f),
            modifier = Modifier
                .size(100.dp)
                .align(Alignment.CenterEnd)
                .offset(x = 20.dp, y = 10.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun MenuCard(
    item: MenuItemData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(100.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.linearGradient(item.gradientColors)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
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
