package com.emabuia.pokevault

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

/**
 * UI tests per le Composable
 * 
 * Testa il rendering e l'interazione dei componenti Compose
 */
class ComposeUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testButtonClickInteraction() {
        // Arrange
        composeTestRule.setContent {
            var count = remember { mutableStateOf(0) }
            Column {
                Text("Count: ${count.value}")
                Button(onClick = { count.value++ }) {
                    Text("Click me")
                }
            }
        }

        // Act & Assert
        composeTestRule.onNodeWithText("Click me").performClick()
        composeTestRule.onNodeWithText("Count: 1").assertExists()
    }

    @Test
    fun testTextDisplays() {
        // Arrange
        composeTestRule.setContent {
            Text("Hello, Test World!")
        }

        // Act & Assert
        composeTestRule.onNodeWithText("Hello, Test World!").assertExists()
    }
}
