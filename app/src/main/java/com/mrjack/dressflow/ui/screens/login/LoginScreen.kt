package com.mrjack.dressflow.ui.screens.login

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrjack.dressflow.viewmodel.AuthState
import com.mrjack.dressflow.viewmodel.AuthViewModel

@Composable
fun LoginScreen(viewModel: AuthViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("df_login_prefs", android.content.Context.MODE_PRIVATE) }

    var email by remember { mutableStateOf(prefs.getString("saved_email", "") ?: "") }
    var senha by remember { mutableStateOf(prefs.getString("saved_senha", "") ?: "") }
    var lembrar by remember { mutableStateOf(prefs.getBoolean("lembrar", false)) }
    var senhaVisivel by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val isLoading = state is AuthState.Loading
    val erro = (state as? AuthState.Error)?.message

    // ── Animação de entrada ───────────────────────────────────────────
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val logoAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(700),
        label = "logoAlpha",
    )
    val logoSlide by animateFloatAsState(
        targetValue = if (visible) 0f else (-28f),
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "logoSlide",
    )
    val formAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(600, delayMillis = 280),
        label = "formAlpha",
    )
    val formSlide by animateFloatAsState(
        targetValue = if (visible) 0f else 36f,
        animationSpec = tween(600, delayMillis = 280, easing = FastOutSlowInEasing),
        label = "formSlide",
    )

    // ── Flutuação do ícone (igual ao keyframes float do site) ─────────
    val infiniteTransition = rememberInfiniteTransition(label = "float")
    val floatY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "floatY",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    0.0f to Color(0xFF1E40AF).copy(alpha = 0.22f),
                    1.0f to Color(0xFF020817),
                    radius = 1100f,
                )
            )
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(36.dp),
        ) {
            // ── Logo + título ─────────────────────────────────────────
            Column(
                modifier = Modifier
                    .offset(y = logoSlide.dp)
                    .alpha(logoAlpha),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .offset(y = floatY.dp)
                        .size(68.dp)
                        .background(Color(0xFF2563EB), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("D", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "DressFlow",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp,
                    )
                    Text(
                        "Sistema de gestão de locações",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                    )
                }
            }

            // ── Card do formulário ────────────────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = formSlide.dp)
                    .alpha(formAlpha),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, Color(0xFF334155).copy(alpha = 0.6f)),
                shadowElevation = 20.dp,
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Acessar conta",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White,
                        )
                        Text(
                            "Digite seu e-mail e senha para entrar.",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // E-mail
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "E-mail",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFCBD5E1),
                            )
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                placeholder = {
                                    Text("admin@dressflow.com", fontSize = 13.sp, color = Color(0xFF475569))
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next,
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                                ),
                                enabled = !isLoading,
                                colors = darkFieldColors(),
                            )
                        }
                        // Senha
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Senha",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFCBD5E1),
                            )
                            OutlinedTextField(
                                value = senha,
                                onValueChange = { senha = it },
                                placeholder = {
                                    Text("••••••••", fontSize = 13.sp, color = Color(0xFF475569))
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                visualTransformation = if (senhaVisivel) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(onDone = {
                                    focusManager.clearFocus()
                                    if (email.isNotBlank() && senha.isNotBlank()) viewModel.login(email.trim(), senha)
                                }),
                                trailingIcon = {
                                    IconButton(onClick = { senhaVisivel = !senhaVisivel }) {
                                        Icon(
                                            if (senhaVisivel) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = null,
                                            tint = Color(0xFF64748B),
                                        )
                                    }
                                },
                                enabled = !isLoading,
                                colors = darkFieldColors(),
                            )
                        }
                    }

                    if (erro != null) {
                        Surface(
                            color = Color(0xFFEF4444).copy(alpha = 0.1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.2f)),
                        ) {
                            Text(
                                erro,
                                color = Color(0xFFF87171),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                    }

                    // Checkbox "Lembrar dados"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { lembrar = !lembrar },
                    ) {
                        Checkbox(
                            checked = lembrar,
                            onCheckedChange = { lembrar = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF2563EB),
                                uncheckedColor = Color(0xFF475569),
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Lembrar dados de login", fontSize = 13.sp, color = Color(0xFFCBD5E1))
                    }

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            // Salva ou limpa dados conforme checkbox
                            if (lembrar) {
                                prefs.edit()
                                    .putString("saved_email", email.trim())
                                    .putString("saved_senha", senha)
                                    .putBoolean("lembrar", true)
                                    .apply()
                            } else {
                                prefs.edit().clear().apply()
                            }
                            viewModel.login(email.trim(), senha)
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        enabled = email.isNotBlank() && senha.isNotBlank() && !isLoading,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB),
                            disabledContainerColor = Color(0xFF2563EB).copy(alpha = 0.45f),
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Entrar →", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                    }
                }
            }

            Text(
                "by Luan Castro",
                color = Color(0xFF334155),
                fontSize = 11.sp,
                modifier = Modifier.alpha(formAlpha),
            )
        }
    }
}

@Composable
private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = Color(0xFF334155),
    focusedBorderColor = Color(0xFF3B82F6),
    unfocusedContainerColor = Color(0xFF1E293B),
    focusedContainerColor = Color(0xFF1E293B),
    unfocusedTextColor = Color.White,
    focusedTextColor = Color.White,
    cursorColor = Color(0xFF3B82F6),
    unfocusedLabelColor = Color(0xFF64748B),
    focusedLabelColor = Color(0xFF3B82F6),
)
