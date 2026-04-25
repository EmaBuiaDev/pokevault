package com.emabuia.pokevault.ui.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale

@Composable
fun AuthScreen(
    onLogin: (email: String, password: String) -> Unit,
    onRegister: (email: String, password: String, name: String) -> Unit,
    onGoogleSignIn: () -> Unit,
    onForgotPassword: (email: String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onClearError: () -> Unit
) {
    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val mainGradient = Brush.verticalGradient(
        listOf(DarkBackground, Color(0xFF16213E))
    )

    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val rotation by infiniteTransition.animateFloat(
        initialValue = -15f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(150, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shake"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(mainGradient)
    ) {
        // Decorazione sfondo
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = 150.dp, y = (-100).dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(PurpleCard.copy(alpha = 0.12f), Color.Transparent)
                    )
                )
        )

        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier.graphicsLayer {
                    if (isLoading) rotationZ = rotation
                }
            ) {
                VaultLogo()
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "CardsVaultTCG",
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextWhite,
                letterSpacing = 1.sp
            )
            Text(
                text = AppLocale.legendaryCollection,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextGray
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Toggle Tab
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .border(BorderStroke(1.dp, TextMuted.copy(alpha = 0.2f)), RoundedCornerShape(24.dp)),
                color = DarkCard.copy(alpha = 0.6f)
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    AuthTabButton(
                        text = AppLocale.loginTab,
                        isSelected = isLoginMode,
                        onClick = { isLoginMode = true; onClearError() }
                    )
                    AuthTabButton(
                        text = AppLocale.registerTab,
                        isSelected = !isLoginMode,
                        onClick = { isLoginMode = false; onClearError() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Form
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(DarkCard.copy(alpha = 0.4f))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedVisibility(visible = !isLoginMode) {
                    AuthTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = AppLocale.nameTrainer,
                        leadingIcon = Icons.Default.CatchingPokemon,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    )
                }

                AuthTextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = AppLocale.emailPokemonCenter,
                    leadingIcon = Icons.Default.Email,
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                )

                Column {
                    AuthTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = AppLocale.passwordSecret,
                        leadingIcon = Icons.Default.Lock,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        isPassword = true,
                        passwordVisible = passwordVisible,
                        onTogglePassword = { passwordVisible = !passwordVisible }
                    )
                    
                    if (isLoginMode) {
                        Text(
                            text = AppLocale.forgotPassword,
                            color = BlueCard,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .align(Alignment.End)
                                .clickable { onForgotPassword(email) }
                                .padding(top = 8.dp, end = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Errore
            AnimatedVisibility(visible = errorMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(RedCard.copy(alpha = 0.2f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ErrorOutline, null, tint = RedCard, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = errorMessage ?: "", color = RedCard, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bottone Login
            Button(
                onClick = { if (isLoginMode) onLogin(email, password) else onRegister(email, password, name) },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLoginMode) RedCard else BlueCard,
                    disabledContainerColor = (if (isLoginMode) RedCard else BlueCard).copy(alpha = 0.5f)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                if (isLoading) {
                    Text(text = AppLocale.loadingAuth, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                } else {
                    Text(text = if (isLoginMode) AppLocale.loginButton else AppLocale.registerButton, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = TextMuted.copy(alpha = 0.2f))
                Text(AppLocale.or, color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp))
                HorizontalDivider(modifier = Modifier.weight(1f), color = TextMuted.copy(alpha = 0.2f))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottone Google compatto
            OutlinedButton(
                onClick = onGoogleSignIn,
                enabled = !isLoading,
                modifier = Modifier.height(44.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF3C4043)
                ),
                border = BorderStroke(1.dp, Color(0xFFDADCE0)),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                GoogleColorIcon(modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = AppLocale.googleSignInLabel,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = Color(0xFF3C4043)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun GoogleColorIcon(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) / 2f

        // Clip to circle - draw G shape using colored arcs/rectangles
        // Blue top-right arc
        drawArc(color = Color(0xFF4285F4), startAngle = -90f, sweepAngle = 90f, useCenter = true,
            topLeft = androidx.compose.ui.geometry.Offset(cx - r, cy - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2))
        // Red bottom-right arc
        drawArc(color = Color(0xFFEA4335), startAngle = 0f, sweepAngle = 90f, useCenter = true,
            topLeft = androidx.compose.ui.geometry.Offset(cx - r, cy - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2))
        // Yellow bottom-left arc
        drawArc(color = Color(0xFFFBBC05), startAngle = 90f, sweepAngle = 90f, useCenter = true,
            topLeft = androidx.compose.ui.geometry.Offset(cx - r, cy - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2))
        // Green top-left arc
        drawArc(color = Color(0xFF34A853), startAngle = 180f, sweepAngle = 90f, useCenter = true,
            topLeft = androidx.compose.ui.geometry.Offset(cx - r, cy - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2))
        // White inner circle
        drawCircle(color = Color.White, radius = r * 0.65f, center = androidx.compose.ui.geometry.Offset(cx, cy))
        // Blue horizontal bar (right side of G)
        drawRect(color = Color(0xFF4285F4),
            topLeft = androidx.compose.ui.geometry.Offset(cx, cy - r * 0.18f),
            size = androidx.compose.ui.geometry.Size(r * 0.9f, r * 0.36f))
        // White mask to round the right edge slightly
        drawRect(color = Color.White,
            topLeft = androidx.compose.ui.geometry.Offset(cx + r * 0.9f - 2f, cy - r * 0.18f),
            size = androidx.compose.ui.geometry.Size(r * 0.2f, r * 0.36f))
    }
}

@Composable
fun VaultLogo() {
    Box(
        modifier = Modifier
            .size(100.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(listOf(BlueCard, PurpleCard))
            )
            .border(3.dp, StarGold.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(86.dp)
                .clip(CircleShape)
                .background(DarkBackground),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(52.dp)) {
                val cardW = size.width * 0.45f
                val cardH = size.height * 0.65f
                val cx = size.width / 2
                val cy = size.height / 2

                // Carta posteriore (inclinata a sinistra)
                rotate(degrees = -15f, pivot = Offset(cx, cy)) {
                    drawRoundRect(
                        color = BlueCard.copy(alpha = 0.6f),
                        topLeft = Offset(cx - cardW / 2, cy - cardH / 2),
                        size = Size(cardW, cardH),
                        cornerRadius = CornerRadius(4f)
                    )
                }

                // Carta centrale
                drawRoundRect(
                    color = PurpleCard.copy(alpha = 0.7f),
                    topLeft = Offset(cx - cardW / 2, cy - cardH / 2),
                    size = Size(cardW, cardH),
                    cornerRadius = CornerRadius(4f)
                )

                // Carta frontale (inclinata a destra)
                rotate(degrees = 15f, pivot = Offset(cx, cy)) {
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(StarGold, OrangeCard)
                        ),
                        topLeft = Offset(cx - cardW / 2, cy - cardH / 2),
                        size = Size(cardW, cardH),
                        cornerRadius = CornerRadius(4f)
                    )
                }

                // Riflesso luce sulla carta frontale
                rotate(degrees = 15f, pivot = Offset(cx, cy)) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.6f),
                        radius = 3f,
                        center = Offset(cx - cardW * 0.1f, cy - cardH * 0.25f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthTabButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) RedCard else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 40.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = if (isSelected) TextWhite else TextGray, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePassword: (() -> Unit)? = null
) {
    Column {
        Text(
            text = placeholder.uppercase(),
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurface)
                .border(BorderStroke(1.dp, TextMuted.copy(alpha = 0.1f)), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (value.isNotEmpty()) StarGold else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = androidx.compose.ui.text.TextStyle(color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    singleLine = true,
                    cursorBrush = SolidColor(StarGold),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                    visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                    modifier = Modifier.weight(1f)
                )
                if (isPassword && onTogglePassword != null) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp).clickable { onTogglePassword() }
                    )
                }
            }
        }
    }
}
