package com.deliriuum.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deliriuum.app.data.AuthManager
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    authManager: AuthManager,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    // Form states
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isRegisterMode by remember { mutableStateOf(false) }

    // Règle de validation de mot de passe (Synchronisé iOS / Backend)
    val hasMinLength = password.length >= 12
    val hasUppercase = password.any { it.isUpperCase() }
    val hasDigit = password.any { it.isDigit() }
    val hasSymbol = password.any { !it.isLetterOrDigit() }
    val passwordsMatch = confirmPassword.isNotEmpty() && password == confirmPassword

    val isPasswordStrongEnough = hasMinLength && hasUppercase && hasDigit && hasSymbol
    val isFormValid = email.isNotEmpty() && password.isNotEmpty() &&
            (!isRegisterMode || (isPasswordStrongEnough && passwordsMatch))

    // Fermeture automatique de l'écran si l'utilisateur réussit à se connecter
    LaunchedEffect(authManager.isLoggedIn) {
        if (authManager.isLoggedIn) {
            onDismiss()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val endRadiusPx = with(density) { 420.dp.toPx() }

        // --- BACKGROUND GRADIENTS ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF005973),
                            Color(0x8C2E1459),
                            Color.Transparent
                        ),
                        center = androidx.compose.ui.geometry.Offset(x = screenWidthPx * 0.5f, y = screenWidthPx * 0.12f),
                        radius = endRadiusPx
                    )
                )
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xFF050508))
                    )
                )
        )

        // --- CONTENT ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
                .padding(top = 60.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Header Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("🛡️", fontSize = 36.sp, modifier = Modifier.padding(bottom = 4.dp))

                Text(
                    text = "Un compte pour activer ta protection",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "On en a besoin pour créer ton tunnel sécurisé personnel — comme une clé qui n'ouvre que ta porte. Rien d'autre n'est demandé.",
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            // Card Form OR Verification state
            CardBackground {
                if (authManager.needsEmailVerification) {
                    // --- MODE ATTENTE VERIFICATION EMAIL ---
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(6.dp)
                    ) {
                        Text("✉️", fontSize = 40.sp)

                        Text("Vérifie ta boîte mail", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)

                        Text(
                            text = "Un lien de confirmation a été envoyé à ${authManager.pendingVerificationEmail ?: "ton adresse"}. Clique-le, puis reviens te connecter ici.",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Center
                        )

                        Box(
                            modifier = Modifier
                                .clickable(enabled = !authManager.isLoading) {
                                    scope.launch { authManager.resendVerification() }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Text("Renvoyer le lien", color = Color.Cyan, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }

                        Box(
                            modifier = Modifier
                                .clickable {
                                    authManager.needsEmailVerification = false
                                    isRegisterMode = false
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Text("Retour à la connexion", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                        }
                    }
                } else {
                    // --- MODE S'INSCRIRE / SE CONNECTER ---
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                        // Mode Switcher personnalisé (Connexion / S'inscrire)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ModeButton(title = "Connexion", isSelected = !isRegisterMode, modifier = Modifier.weight(1f)) {
                                isRegisterMode = false
                            }
                            ModeButton(title = "S'inscrire", isSelected = isRegisterMode, modifier = Modifier.weight(1f)) {
                                isRegisterMode = true
                            }
                        }

                        // Input Email
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            placeholder = { Text("Email", color = Color.White.copy(alpha = 0.4f)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.White.copy(alpha = 0.08f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Input Mot de passe
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            placeholder = { Text("Mot de passe", color = Color.White.copy(alpha = 0.4f)) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.White.copy(alpha = 0.08f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Validation visuelle temps réel (Inscription uniquement)
                        if (isRegisterMode) {
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it },
                                placeholder = { Text("Confirmer le mot de passe", color = Color.White.copy(alpha = 0.4f)) },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedContainerColor = Color.White.copy(alpha = 0.08f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Liste des critères de force du mot de passe
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                            ) {
                                RequirementRow("12 caractères minimum", isMet = hasMinLength)
                                RequirementRow("Une majuscule", isMet = hasUppercase)
                                RequirementRow("Un chiffre", isMet = hasDigit)
                                RequirementRow("Un symbole (!, ?, #, …)", isMet = hasSymbol)
                                if (confirmPassword.isNotEmpty()) {
                                    RequirementRow("Les deux mots de passe correspondent", isMet = passwordsMatch)
                                }
                            }
                        }

                        // Message d'erreur global retourné par le serveur
                        authManager.errorMessage?.let { error ->
                            Text(
                                text = error,
                                color = Color.Red.copy(alpha = 0.9f),
                                fontSize = 13.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Bouton d'action principal de soumission
                        val canSubmit = isFormValid && !authManager.isLoading
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(
                                    Brush.horizontalGradient(listOf(Color.Cyan, Color(0xFF00E676))),
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable(enabled = canSubmit) {
                                    scope.launch {
                                        if (isRegisterMode) {
                                            authManager.register(email, password)
                                        } else {
                                            authManager.login(email, password)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (authManager.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                            } else {
                                Text(
                                    text = if (isRegisterMode) "S'inscrire" else "Connexion",
                                    color = Color.Black,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeButton(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(if (isSelected) Color.Cyan else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.75f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RequirementRow(text: String, isMet: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isMet) "✓" else "○",
            color = if (isMet) Color(0xFF00E676) else Color.White.copy(alpha = 0.3f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = text,
            color = if (isMet) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.4f),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun CardBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(24.dp))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        content()
    }
}