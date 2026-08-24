package com.deliriuum.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.deliriuum.app.data.APIClient
import com.deliriuum.app.data.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@Composable
fun AccountScreen(
    authManager: AuthManager,
    onDismiss: () -> Unit,
    onLogout: () -> Unit
) {

    val scope =
        rememberCoroutineScope()

    var showForgotPassword by remember {
        mutableStateOf(false)
    }

    var resetEmail by remember {
        mutableStateOf("")
    }

    var resetMessage by remember {
        mutableStateOf<String?>(null)
    }

    var resetError by remember {
        mutableStateOf<String?>(null)
    }

    var isSendingReset by remember {
        mutableStateOf(false)
    }


    var showDeleteConfirmation by remember {
        mutableStateOf(false)
    }

    var deletePassword by remember {
        mutableStateOf("")
    }

    var deleteError by remember {
        mutableStateOf<String?>(null)
    }

    var isDeletingAccount by remember {
        mutableStateOf(false)
    }


    /*
     * Rafraîchissement à l'ouverture de l'écran.
     *
     * refreshProfile() n'est PAS suspend : elle délègue au
     * scope interne d'AuthManager. Ce LaunchedEffect ne fait
     * donc que déclencher — s'il est annulé (fermeture de la
     * modale, recomposition), la requête /auth/me continue.
     *
     * C'était précisément le point de blocage : tant que la
     * requête vivait dans le scope de l'écran, toute sortie
     * de composition la tuait, et le profil n'apparaissait
     * qu'au redémarrage du process.
     */
    LaunchedEffect(Unit) {

        authManager.refreshProfile()
    }


    /*
     * Rafraîchissement au retour au premier plan.
     *
     * Couvre notamment la vérification d'email : l'utilisateur
     * part dans son navigateur, clique le lien, revient — et le
     * badge passe à "vérifié" sans relancer l'application.
     */
    val lifecycleOwner =
        LocalLifecycleOwner.current

    DisposableEffect(
        lifecycleOwner
    ) {

        val observer =
            LifecycleEventObserver { _, event ->

                if (
                    event == Lifecycle.Event.ON_RESUME
                ) {

                    authManager.refreshProfile()
                }
            }


        lifecycleOwner
            .lifecycle
            .addObserver(observer)


        onDispose {

            lifecycleOwner
                .lifecycle
                .removeObserver(observer)
        }
    }


    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        Background()


        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(
                        rememberScrollState()
                    )
                    .padding(
                        horizontal = 22.dp
                    )
                    .padding(
                        top = 24.dp,
                        bottom = 40.dp
                    ),
            verticalArrangement =
                Arrangement.spacedBy(20.dp)
        ) {

            TopBar(
                onDismiss
            )


            IdentityCard(
                authManager
            )


            // ====================================================
            // RESET PASSWORD
            // ====================================================

            if (showForgotPassword) {

                CardBox {

                    Column(
                        verticalArrangement =
                            Arrangement.spacedBy(12.dp)
                    ) {

                        DarkTextField(
                            value = resetEmail,
                            onValueChange = {
                                resetEmail = it
                            },
                            placeholder = "Email",
                            keyboardType =
                                KeyboardType.Email
                        )


                        resetMessage?.let {

                            Text(
                                text = it,
                                color =
                                    Color(0xFF66E6B3),
                                fontSize = 13.sp
                            )
                        }


                        resetError?.let {

                            Text(
                                text = it,
                                color =
                                    Color.Red.copy(
                                        alpha = 0.9f
                                    ),
                                fontSize = 13.sp
                            )
                        }


                        GradientButton(
                            text =
                                "Envoyer le lien",
                            enabled =
                                resetEmail.isNotBlank() &&
                                        !isSendingReset,
                            isLoading =
                                isSendingReset
                        ) {

                            scope.launch {

                                isSendingReset =
                                    true

                                resetError =
                                    null

                                resetMessage =
                                    null


                                try {

                                    resetMessage =
                                        withContext(
                                            Dispatchers.IO
                                        ) {

                                            APIClient.shared
                                                .forgotPassword(
                                                    resetEmail
                                                )
                                        }

                                } catch (
                                    e: Exception
                                ) {

                                    resetError =
                                        e.localizedMessage
                                            ?: "Erreur inconnue."

                                } finally {

                                    isSendingReset =
                                        false
                                }
                            }
                        }
                    }
                }

            } else {

                OutlineButton(
                    text =
                        "🔑  Réinitialiser mon mot de passe",
                    color =
                        Color.Cyan
                ) {

                    resetEmail =
                        authManager
                            .currentUser
                            ?.email
                            ?: ""

                    showForgotPassword =
                        true
                }
            }


            // ====================================================
            // ACTIONS AUTHENTIFIEES
            // ====================================================

            if (authManager.isLoggedIn) {

                DangerButton(
                    "Se déconnecter"
                ) {

                    onLogout()

                    onDismiss()
                }


                if (showDeleteConfirmation) {

                    CardBox {

                        Column(
                            verticalArrangement =
                                Arrangement.spacedBy(12.dp)
                        ) {

                            Text(
                                text =
                                    "Cette action est définitive. Confirme avec ton mot de passe.",
                                color =
                                    Color.White.copy(
                                        alpha = 0.7f
                                    ),
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                textAlign =
                                    TextAlign.Center
                            )


                            DarkTextField(
                                value =
                                    deletePassword,
                                onValueChange = {
                                    deletePassword = it
                                },
                                placeholder =
                                    "Mot de passe",
                                keyboardType =
                                    KeyboardType.Password,
                                isPassword =
                                    true
                            )


                            deleteError?.let {

                                Text(
                                    text = it,
                                    color =
                                        Color.Red.copy(
                                            alpha = 0.9f
                                        ),
                                    fontSize = 13.sp
                                )
                            }


                            Row(
                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        12.dp
                                    )
                            ) {

                                Box(
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .background(
                                                Color.White.copy(
                                                    alpha = 0.06f
                                                ),
                                                RoundedCornerShape(
                                                    14.dp
                                                )
                                            )
                                            .clickable {

                                                showDeleteConfirmation =
                                                    false

                                                deletePassword =
                                                    ""

                                                deleteError =
                                                    null
                                            }
                                            .padding(
                                                vertical = 13.dp
                                            ),
                                    contentAlignment =
                                        Alignment.Center
                                ) {

                                    Text(
                                        text = "Annuler",
                                        color =
                                            Color.White.copy(
                                                alpha = 0.75f
                                            ),
                                        fontWeight =
                                            FontWeight.Bold
                                    )
                                }


                                Box(
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .background(
                                                Color.Red.copy(
                                                    alpha =
                                                        if (
                                                            deletePassword.isBlank()
                                                        ) {
                                                            0.35f
                                                        } else {
                                                            0.7f
                                                        }
                                                ),
                                                RoundedCornerShape(
                                                    14.dp
                                                )
                                            )
                                            .clickable(
                                                enabled =
                                                    deletePassword.isNotBlank() &&
                                                            !isDeletingAccount
                                            ) {

                                                scope.launch {

                                                    isDeletingAccount =
                                                        true

                                                    deleteError =
                                                        null


                                                    try {

                                                        withContext(
                                                            Dispatchers.IO
                                                        ) {

                                                            APIClient.shared
                                                                .deleteAccount(
                                                                    deletePassword
                                                                )
                                                        }


                                                        authManager
                                                            .logout()


                                                        onDismiss()

                                                    } catch (
                                                        e: Exception
                                                    ) {

                                                        deleteError =
                                                            e.localizedMessage
                                                                ?: "Erreur inconnue."

                                                    } finally {

                                                        isDeletingAccount =
                                                            false
                                                    }
                                                }
                                            }
                                            .padding(
                                                vertical = 13.dp
                                            ),
                                    contentAlignment =
                                        Alignment.Center
                                ) {

                                    if (isDeletingAccount) {

                                        CircularProgressIndicator(
                                            modifier =
                                                Modifier.size(
                                                    16.dp
                                                ),
                                            strokeWidth =
                                                2.dp,
                                            color =
                                                Color.White
                                        )

                                    } else {

                                        Text(
                                            text =
                                                "Supprimer",
                                            color =
                                                Color.White,
                                            fontWeight =
                                                FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                } else {

                    OutlineButton(
                        text =
                            "🗑️  Supprimer mon compte",
                        color =
                            Color(0xFFB375FF)
                    ) {

                        showDeleteConfirmation =
                            true
                    }
                }
            }
        }
    }
}


// ================================================================
// BACKGROUND
// ================================================================

@Composable
private fun Background() {

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color.Black
                )
    )


    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors =
                            listOf(
                                Color(0xFF005973),
                                Color(0x8C2E1459),
                                Color.Transparent
                            ),
                        center =
                            androidx.compose.ui.geometry.Offset(
                                x = 500f,
                                y = 100f
                            ),
                        radius =
                            1000f
                    )
                )
    )


    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                Color.Transparent,
                                Color(0xFF050508)
                            )
                    )
                )
    )
}


// ================================================================
// TOP BAR
// ================================================================

@Composable
private fun TopBar(
    onDismiss: () -> Unit
) {

    Row(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween,
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Spacer(
            modifier =
                Modifier.size(32.dp)
        )


        Text(
            text =
                "Mon compte",
            color =
                Color.White,
            fontWeight =
                FontWeight.Bold,
            fontSize =
                18.sp
        )


        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .background(
                        Color.White.copy(
                            alpha = 0.08f
                        ),
                        CircleShape
                    )
                    .border(
                        1.dp,
                        Color.Cyan.copy(
                            alpha = 0.3f
                        ),
                        CircleShape
                    )
                    .clickable {
                        onDismiss()
                    },
            contentAlignment =
                Alignment.Center
        ) {

            Text(
                text = "✕",
                color = Color.Cyan,
                fontSize = 13.sp,
                fontWeight =
                    FontWeight.Bold
            )
        }
    }
}


// ================================================================
// IDENTITY CARD
// ================================================================

@Composable
private fun IdentityCard(
    authManager: AuthManager
) {

    val isLoggedIn =
        authManager.isLoggedIn

    val user =
        authManager.currentUser


    CardBox {

        Column(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(16.dp)
        ) {

            Box(
                modifier =
                    Modifier
                        .size(72.dp)
                        .background(
                            Color.Cyan.copy(
                                alpha = 0.12f
                            ),
                            CircleShape
                        ),
                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    text = "👤",
                    fontSize = 28.sp,
                    color = Color.Cyan
                )
            }


            when {

                !isLoggedIn -> {

                    Text(
                        text =
                            "Non connecté",
                        color =
                            Color.White,
                        fontSize =
                            17.sp,
                        fontWeight =
                            FontWeight.Bold
                    )
                }


                user != null -> {

                    Text(
                        text =
                            user.email,
                        color =
                            Color.White,
                        fontSize =
                            17.sp,
                        fontWeight =
                            FontWeight.Bold,
                        textAlign =
                            TextAlign.Center
                    )


                    val verifiedColor =
                        if (user.isVerified) {

                            Color(0xFF66E6B3)

                        } else {

                            Color(0xFFFFA726)
                        }


                    Box(
                        modifier =
                            Modifier
                                .background(
                                    verifiedColor.copy(
                                        alpha = 0.12f
                                    ),
                                    RoundedCornerShape(
                                        50
                                    )
                                )
                                .padding(
                                    horizontal = 12.dp,
                                    vertical = 6.dp
                                )
                    ) {

                        Text(
                            text =
                                if (user.isVerified) {

                                    "✅ Compte vérifié"

                                } else {

                                    "⏳ Vérification en attente"
                                },
                            color =
                                verifiedColor,
                            fontSize =
                                12.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }


                    if (user.isSupporter) {

                        Text(
                            text =
                                "💚 Supporter du projet",
                            color =
                                Color(0xFFFFD54F),
                            fontSize =
                                12.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }


                /*
                 * Session valide, profil en cours de récupération.
                 */
                authManager.isRefreshingProfile -> {

                    CircularProgressIndicator(
                        modifier =
                            Modifier.size(22.dp),
                        strokeWidth =
                            2.dp,
                        color =
                            Color.Cyan
                    )


                    Text(
                        text =
                            "Chargement du compte…",
                        color =
                            Color.White.copy(
                                alpha = 0.55f
                            ),
                        fontSize =
                            12.sp,
                        textAlign =
                            TextAlign.Center
                    )
                }


                /*
                 * Session valide, récupération terminée, mais
                 * toujours pas de profil.
                 *
                 * Avant : cul-de-sac muet, il fallait relancer
                 * l'application.
                 *
                 * Maintenant : cause affichée + relance possible.
                 */
                else -> {

                    Text(
                        text =
                            "Profil indisponible",
                        color =
                            Color(0xFFFFA726),
                        fontSize =
                            15.sp,
                        fontWeight =
                            FontWeight.Bold,
                        textAlign =
                            TextAlign.Center
                    )


                    Text(
                        text =
                            authManager.errorMessage
                                ?: "Impossible de contacter le serveur.",
                        color =
                            Color.White.copy(
                                alpha = 0.55f
                            ),
                        fontSize =
                            12.sp,
                        lineHeight =
                            17.sp,
                        textAlign =
                            TextAlign.Center
                    )


                    OutlineButton(
                        text =
                            "↻  Réessayer",
                        color =
                            Color.Cyan
                    ) {

                        authManager.refreshProfile(
                            force = true
                        )
                    }
                }
            }
        }
    }
}


// ================================================================
// CARD BOX
// ================================================================

@Composable
private fun CardBox(
    content:
    @Composable () -> Unit
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    Color.White.copy(
                        alpha = 0.07f
                    ),
                    RoundedCornerShape(
                        24.dp
                    )
                )
                .border(
                    1.dp,
                    Color.White.copy(
                        alpha = 0.10f
                    ),
                    RoundedCornerShape(
                        24.dp
                    )
                )
                .padding(
                    20.dp
                )
    ) {

        content()
    }
}


// ================================================================
// TEXT FIELD
// ================================================================

@Composable
private fun DarkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    isPassword: Boolean = false
) {

    OutlinedTextField(
        value =
            value,
        onValueChange =
            onValueChange,
        placeholder = {

            Text(
                text =
                    placeholder,
                color =
                    Color.White.copy(
                        alpha = 0.35f
                    )
            )
        },
        singleLine =
            true,
        keyboardOptions =
            KeyboardOptions(
                keyboardType =
                    keyboardType
            ),
        visualTransformation =
            if (isPassword) {

                PasswordVisualTransformation()

            } else {

                androidx.compose.ui.text.input
                    .VisualTransformation.None
            },
        colors =
            OutlinedTextFieldDefaults.colors(

                focusedTextColor =
                    Color.White,

                unfocusedTextColor =
                    Color.White,

                focusedBorderColor =
                    Color.Cyan.copy(
                        alpha = 0.5f
                    ),

                unfocusedBorderColor =
                    Color.White.copy(
                        alpha = 0.12f
                    ),

                focusedContainerColor =
                    Color.White.copy(
                        alpha = 0.08f
                    ),

                unfocusedContainerColor =
                    Color.White.copy(
                        alpha = 0.08f
                    ),

                cursorColor =
                    Color.Cyan
            ),
        shape =
            RoundedCornerShape(
                14.dp
            ),
        modifier =
            Modifier.fillMaxWidth()
    )
}


// ================================================================
// GRADIENT BUTTON
// ================================================================

@Composable
private fun GradientButton(
    text: String,
    enabled: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Cyan,
                            Color(0xFF66E6B3)
                        )
                    ),
                    RoundedCornerShape(
                        14.dp
                    )
                )
                .clickable(
                    enabled = enabled
                ) {
                    onClick()
                }
                .padding(
                    vertical = 13.dp
                ),
        contentAlignment =
            Alignment.Center
    ) {

        if (isLoading) {

            CircularProgressIndicator(
                modifier =
                    Modifier.size(16.dp),
                strokeWidth =
                    2.dp,
                color =
                    Color.Black
            )

        } else {

            Text(
                text =
                    text,
                color =
                    Color.Black,
                fontWeight =
                    FontWeight.Bold,
                fontSize =
                    15.sp
            )
        }
    }
}


// ================================================================
// OUTLINE BUTTON
// ================================================================

@Composable
private fun OutlineButton(
    text: String,
    color: Color,
    onClick: () -> Unit
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color.copy(
                        alpha = 0.08f
                    ),
                    RoundedCornerShape(
                        16.dp
                    )
                )
                .border(
                    1.5.dp,
                    color.copy(
                        alpha = 0.4f
                    ),
                    RoundedCornerShape(
                        16.dp
                    )
                )
                .clickable {
                    onClick()
                }
                .padding(
                    vertical = 14.dp
                ),
        contentAlignment =
            Alignment.Center
    ) {

        Text(
            text =
                text,
            color =
                color,
            fontWeight =
                FontWeight.Bold,
            fontSize =
                14.sp
        )
    }
}


// ================================================================
// DANGER BUTTON
// ================================================================

@Composable
private fun DangerButton(
    text: String,
    onClick: () -> Unit
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    Color.Red.copy(
                        alpha = 0.08f
                    ),
                    RoundedCornerShape(
                        14.dp
                    )
                )
                .clickable {
                    onClick()
                }
                .padding(
                    vertical = 13.dp
                ),
        contentAlignment =
            Alignment.Center
    ) {

        Text(
            text =
                text,
            color =
                Color.Red.copy(
                    alpha = 0.85f
                ),
            fontWeight =
                FontWeight.Bold,
            fontSize =
                14.sp
        )
    }
}