package com.emabuia.pokevault.ui.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.R
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale

@OptIn(ExperimentalLayoutApi::class)
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
    val focusManager = LocalFocusManager.current
    val motion = AppMotion.current

    val submit = {
        focusManager.clearFocus()
        if (isLoginMode) onLogin(email, password) else onRegister(email, password, name)
    }

    // Entrata a cascata: ogni blocco sale e compare poco dopo il precedente.
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    // Un errore nuovo fa "scuotere la testa" al riquadro che lo mostra.
    val shake = remember { Animatable(0f) }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null && motion.enabled) {
            for (x in listOf(10f, -8f, 6f, -4f, 2f, 0f)) shake.animateTo(x, tween(45))
        }
    }

    // Lo sfondo riprende quello dell'icona: un alone blu-viola in alto e il
    // taglio diagonale in basso a destra. Nel tema chiaro restano appena accennati.
    val isLight = AppColors.isLight
    val halo = AppColors.purple.copy(alpha = if (isLight) 0.10f else 0.22f)
    val haloBlue = AppColors.blue.copy(alpha = if (isLight) 0.08f else 0.16f)
    val cut = Color.Black.copy(alpha = if (isLight) 0.03f else 0.22f)

    // L'alone viola respira piano, sfasato rispetto al logo.
    val drift = if (motion.enabled) {
        val t = rememberInfiniteTransition(label = "sfondo")
        val v by t.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(motion.foil * 3, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "drift"
        )
        v
    } else 0.5f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(halo, Color.Transparent),
                        center = Offset(size.width * (0.72f + 0.16f * drift), size.height * (0.04f + 0.06f * drift)),
                        radius = size.width * 0.9f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(haloBlue, Color.Transparent),
                        center = Offset(size.width * (0.18f - 0.12f * drift), size.height * 0.3f),
                        radius = size.width * 0.7f
                    )
                )
                val path = Path().apply {
                    moveTo(0f, size.height)
                    lineTo(size.width, size.height * 0.55f)
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(path, cut)
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                AppIconLogo(
                    isLoading = isLoading,
                    modifier = Modifier.enterItem(appeared, 0, scaleFrom = 0.6f)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier.enterItem(appeared, 1),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "PokeVault",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AppColors.textPrimary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = AppLocale.legendaryCollection,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppColors.textSecondary
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                AuthModeSwitch(
                    isLoginMode = isLoginMode,
                    modifier = Modifier.enterItem(appeared, 2),
                    onSelect = { login ->
                        if (login != isLoginMode) {
                            isLoginMode = login
                            onClearError()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Form
                Column(
                    modifier = Modifier
                        .enterItem(appeared, 3)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(AppColors.surface)
                        .border(BorderStroke(1.dp, AppColors.textMuted.copy(alpha = 0.12f)), RoundedCornerShape(24.dp))
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                        .animateContentSize(tween(motion.content)),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AnimatedVisibility(
                        visible = !isLoginMode,
                        enter = fadeIn(tween(motion.content)) + expandVertically(tween(motion.content)),
                        exit = fadeOut(tween(motion.state)) + shrinkVertically(tween(motion.content))
                    ) {
                        AuthTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = AppLocale.nameTrainer,
                            leadingIcon = Icons.Default.CatchingPokemon,
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next,
                            onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
                        )
                    }

                    AuthTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = AppLocale.emailPokemonCenter,
                        leadingIcon = Icons.Default.Email,
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                        onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
                    )

                    Column {
                        AuthTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = AppLocale.passwordSecret,
                            leadingIcon = Icons.Default.Lock,
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                            onImeAction = { if (!isLoading) submit() },
                            isPassword = true,
                            passwordVisible = passwordVisible,
                            onTogglePassword = { passwordVisible = !passwordVisible }
                        )

                        AnimatedVisibility(
                            visible = isLoginMode,
                            modifier = Modifier.align(Alignment.End),
                            enter = fadeIn(tween(motion.state)),
                            exit = fadeOut(tween(motion.state))
                        ) {
                            TextButton(
                                onClick = { onForgotPassword(email) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = AppLocale.forgotPassword,
                                    color = AppColors.blue,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Errore
                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn(tween(motion.state)) + expandVertically(tween(motion.state)),
                    exit = fadeOut(tween(motion.state)) + shrinkVertically(tween(motion.state))
                ) {
                    Row(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .graphicsLayer { translationX = shake.value * density }
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppColors.red.copy(alpha = 0.12f))
                            .border(BorderStroke(1.dp, AppColors.red.copy(alpha = 0.3f)), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, tint = AppColors.red, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = errorMessage ?: "", color = AppColors.red, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                PrimaryAuthButton(
                    text = when {
                        isLoading -> AppLocale.loadingAuth
                        isLoginMode -> AppLocale.loginButton
                        else -> AppLocale.registerButton
                    },
                    isLoading = isLoading,
                    modifier = Modifier.enterItem(appeared, 4),
                    onClick = submit
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Google sta fuori dallo scorrimento: e' sempre in vista, qualunque sia
            // l'altezza del telefono o la modalita' scelta. Con la tastiera aperta
            // si toglie, per lasciare spazio al campo su cui si sta scrivendo.
            AnimatedVisibility(
                visible = !WindowInsets.isImeVisible,
                enter = fadeIn(tween(motion.state)) + expandVertically(tween(motion.state)),
                exit = fadeOut(tween(motion.press)) + shrinkVertically(tween(motion.state))
            ) {
                Column(
                    modifier = Modifier
                        .enterItem(appeared, 5)
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = AppColors.textMuted.copy(alpha = 0.2f))
                        Text(
                            AppLocale.or.trim(),
                            color = AppColors.textMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = AppColors.textMuted.copy(alpha = 0.2f))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onGoogleSignIn,
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF3C4043),
                            disabledContainerColor = Color.White.copy(alpha = 0.6f)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFDADCE0))
                    ) {
                        GoogleColorIcon(modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = AppLocale.googleSignInLabel,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color(0xFF3C4043)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Entrata a cascata: il blocco [index] compare salendo di qualche dp, con il
 * ritardo della cascata di [AppMotion]. Con le animazioni di sistema spente le
 * durate sono zero e tutto e' subito al suo posto.
 */
private fun Modifier.enterItem(appeared: Boolean, index: Int, scaleFrom: Float = 1f): Modifier = composed {
    val motion = AppMotion.current
    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(motion.content + motion.cascadeStagger * 2, delayMillis = motion.cascade(index), easing = FastOutSlowInEasing),
        label = "entrata"
    )
    val rise = with(LocalDensity.current) { 18.dp.toPx() }
    graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * rise
        val s = scaleFrom + (1f - scaleFrom) * progress
        scaleX = s
        scaleY = s
    }
}

/**
 * L'icona del launcher, fatta con gli stessi due livelli dell'adaptive icon.
 *
 * I drawable sono 108dp di cui il launcher mostra solo i 72 centrali: qui si
 * disegnano allargati di 108/72 dentro un riquadro ritagliato, cosi' il logo e'
 * esattamente l'icona che l'utente ha sulla home, senza un secondo disegno da
 * tenere allineato.
 *
 * Da fermo galleggia piano e ogni tanto un riflesso olografico lo attraversa,
 * come sulla carta al centro del disegno; durante il login invece respira.
 * L'alone dietro e' disegnato fuori dai bordi e non occupa spazio nel layout.
 */
@Composable
private fun AppIconLogo(isLoading: Boolean, modifier: Modifier = Modifier, size: Dp = 92.dp) {
    val motion = AppMotion.current
    val glow = AppColors.purple.copy(alpha = if (AppColors.isLight) 0.28f else 0.5f)
    val shape = RoundedCornerShape(size * 0.28f)

    var float = 0f
    var sweep = -1f
    var breathe = 1f
    if (motion.enabled) {
        val t = rememberInfiniteTransition(label = "logo")
        val f by t.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(motion.foil, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "galleggia"
        )
        // Il riflesso passa nel primo terzo del ciclo e poi resta fuori: una
        // passata ogni qualche secondo, non un luccichio continuo.
        val s by t.animateFloat(
            initialValue = -1f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(
                keyframes {
                    durationMillis = motion.foil * 2
                    -1f at 0
                    2f at motion.foil * 2 / 3 using FastOutSlowInEasing
                    2f at motion.foil * 2
                }
            ),
            label = "riflesso"
        )
        val b by t.animateFloat(
            initialValue = 1f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(tween(motion.glow / 3, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "respiro"
        )
        float = f
        sweep = s
        if (isLoading) breathe = b
    }

    Box(
        modifier = modifier
            .graphicsLayer { translationY = float * 4.dp.toPx() }
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(glow, Color.Transparent), radius = this.size.minDimension * 0.95f),
                    radius = this.size.minDimension * 0.95f
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .scale(breathe)
                .size(size)
                .clip(shape)
                .drawWithContent {
                    drawContent()
                    val w = this.size.width
                    val x = sweep * w
                    drawRect(
                        Brush.linearGradient(
                            0f to Color.Transparent,
                            0.5f to Color.White.copy(alpha = 0.28f),
                            1f to Color.Transparent,
                            start = Offset(x - w * 0.35f, 0f),
                            end = Offset(x + w * 0.05f, this.size.height)
                        )
                    )
                }
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), shape),
            contentAlignment = Alignment.Center
        ) {
            val layerSize = size * 1.5f
            Image(
                painter = painterResource(R.drawable.ic_launcher_background),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.requiredSize(layerSize)
            )
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.requiredSize(layerSize)
            )
        }
    }
}

/** Accedi / Unisciti, con l'indicatore che scorre sotto la voce scelta. */
@Composable
private fun AuthModeSwitch(isLoginMode: Boolean, modifier: Modifier = Modifier, onSelect: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val motion = AppMotion.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(shape)
            .background(AppColors.surface)
            .border(BorderStroke(1.dp, AppColors.textMuted.copy(alpha = 0.12f)), shape)
            .padding(4.dp)
    ) {
        val half = maxWidth / 2
        val offset by animateDpAsState(
            targetValue = if (isLoginMode) 0.dp else half,
            // Una molla appena elastica: l'indicatore arriva, supera di un filo e si ferma.
            animationSpec = if (motion.enabled) spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow) else snap(),
            label = "switch"
        )
        Box(
            modifier = Modifier
                .offset(x = offset)
                .width(half)
                .fillMaxHeight()
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(AppColors.blue, AppColors.purple)))
        )
        Row(modifier = Modifier.fillMaxSize()) {
            AuthTabButton(AppLocale.loginTab, isLoginMode, Modifier.weight(1f)) { onSelect(true) }
            AuthTabButton(AppLocale.registerTab, !isLoginMode, Modifier.weight(1f)) { onSelect(false) }
        }
    }
}

@Composable
private fun AuthTabButton(text: String, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color by animateColorAsState(
        targetValue = if (isSelected) AppColors.onAccent else AppColors.textSecondary,
        animationSpec = tween(AppMotion.current.state),
        label = "tab"
    )
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun PrimaryAuthButton(text: String, isLoading: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = tween(AppMotion.current.press),
        label = "press"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(pressScale)
            .clip(shape)
            .background(
                Brush.horizontalGradient(listOf(AppColors.blue, AppColors.purple)),
                alpha = if (isLoading) 0.7f else 1f
            )
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = !isLoading,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = AppColors.onAccent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            // Cambiando modalita' la scritta nuova sale e quella vecchia esce in alto.
            val swap = AppMotion.current.state
            val out = AppMotion.current.press
            AnimatedContent(
                targetState = text,
                transitionSpec = {
                    (fadeIn(tween(swap)) + slideInVertically(tween(swap)) { it / 2 }) togetherWith
                        (fadeOut(tween(out)) + slideOutVertically(tween(swap)) { -it / 2 })
                },
                label = "testo"
            ) { label ->
                Text(
                    text = label,
                    color = AppColors.onAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 0.5.sp
                )
            }
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
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePassword: (() -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        targetValue = if (focused) AppColors.purple else AppColors.textMuted.copy(alpha = 0.15f),
        animationSpec = tween(AppMotion.current.state),
        label = "border"
    )
    val iconTint by animateColorAsState(
        targetValue = if (focused || value.isNotEmpty()) AppColors.purple else AppColors.textMuted,
        animationSpec = tween(AppMotion.current.state),
        label = "icon"
    )
    val shape = RoundedCornerShape(14.dp)

    Column {
        Text(
            text = label,
            color = AppColors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(shape)
                .background(AppColors.searchBar)
                .border(BorderStroke(if (focused) 1.5.dp else 1.dp, borderColor), shape)
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = androidx.compose.ui.text.TextStyle(color = AppColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                singleLine = true,
                cursorBrush = SolidColor(AppColors.purple),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                keyboardActions = KeyboardActions(onAny = { onImeAction() }),
                visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                interactionSource = interaction,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 14.dp, horizontal = 0.dp)
                    .padding(end = if (isPassword) 0.dp else 10.dp)
            )
            if (isPassword && onTogglePassword != null) {
                IconButton(onClick = onTogglePassword) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) AppLocale.hidePassword else AppLocale.showPassword,
                        tint = AppColors.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
