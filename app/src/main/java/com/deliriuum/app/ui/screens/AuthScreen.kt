package com.deliriuum.app.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deliriuum.app.R
import com.deliriuum.app.data.APIClient
import com.deliriuum.app.data.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@Composable
fun AuthScreen(
    authManager: AuthManager,
    onDismiss: () -> Unit
) {
    val _i18nContext = LocalContext.current

    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    // ============================================================
    // FORM STATES
    // ============================================================

    var email by remember {
        mutableStateOf("")
    }

    var password by remember {
        mutableStateOf("")
    }

    var confirmPassword by remember {
        mutableStateOf("")
    }

    var isRegisterMode by remember {
        mutableStateOf(false)
    }

    // ============================================================
    // VISIBILITÉ MOTS DE PASSE
    // ============================================================

    var passwordVisible by remember {
        mutableStateOf(false)
    }

    var confirmPasswordVisible by remember {
        mutableStateOf(false)
    }

    // ============================================================
    // MOT DE PASSE OUBLIÉ
    // ============================================================

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

    // ============================================================
    // VALIDATION MOT DE PASSE
    // ============================================================

    val hasMinLength =
        password.length >= 12

    val hasUppercase =
        password.any {
            it.isUpperCase()
        }

    val hasDigit =
        password.any {
            it.isDigit()
        }

    val hasSymbol =
        password.any {
            !it.isLetterOrDigit()
        }

    val passwordsMatch =
        confirmPassword.isNotEmpty() &&
                password == confirmPassword

    val isPasswordStrongEnough =
        hasMinLength &&
                hasUppercase &&
                hasDigit &&
                hasSymbol

    val isFormValid =
        email.isNotEmpty() &&
                password.isNotEmpty() &&
                (
                        !isRegisterMode ||
                                (
                                        isPasswordStrongEnough &&
                                                passwordsMatch
                                        )
                        )

    // ============================================================
    // FERMETURE AUTOMATIQUE APRÈS CONNEXION
    // ============================================================

    LaunchedEffect(
        authManager.isLoggedIn
    ) {
        if (
            authManager.isLoggedIn
        ) {
            onDismiss()
        }
    }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
    ) {

        val screenWidthPx =
            with(density) {
                maxWidth.toPx()
            }

        val endRadiusPx =
            with(density) {
                420.dp.toPx()
            }

        // ========================================================
        // BACKGROUND
        // ========================================================

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    Color(
                                        0xFF005973
                                    ),
                                    Color(
                                        0x8C2E1459
                                    ),
                                    Color.Transparent
                                ),
                            center =
                                androidx.compose.ui.geometry.Offset(
                                    x =
                                        screenWidthPx *
                                                0.5f,
                                    y =
                                        screenWidthPx *
                                                0.12f
                                ),
                            radius =
                                endRadiusPx
                        )
                    )
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color.Transparent,
                                    Color(
                                        0xFF050508
                                    )
                                )
                        )
                    )
        )

        // ========================================================
        // CONTENT
        // ========================================================

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(
                        scrollState
                    )
                    .imePadding()
                    .padding(
                        horizontal =
                            24.dp
                    )
                    .padding(
                        top =
                            60.dp,
                        bottom =
                            40.dp
                    ),
            verticalArrangement =
                Arrangement.spacedBy(
                    28.dp
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            // ====================================================
            // HEADER
            // ====================================================

            Column(
                horizontalAlignment =
                    Alignment.CenterHorizontally,
                verticalArrangement =
                    Arrangement.spacedBy(
                        10.dp
                    )
            ) {

                Text(
                    "🛡️",
                    fontSize =
                        36.sp,
                    modifier =
                        Modifier.padding(
                            bottom =
                                4.dp
                        )
                )

                Text(
                    text =
                        _i18nContext.getString(
                            R.string
                                .auth_un_compte_pour_activer_ta_protection
                        ),
                    color =
                        Color.White,
                    fontSize =
                        20.sp,
                    fontWeight =
                        FontWeight.Bold,
                    textAlign =
                        TextAlign.Center
                )

                Text(
                    text =
                        _i18nContext.getString(
                            R.string
                                .auth_on_en_a_besoin_pour_creer_ton_tunnel_securise_personnel
                        ),
                    color =
                        Color.White,
                    fontSize =
                        14.sp,
                    lineHeight =
                        20.sp,
                    textAlign =
                        TextAlign.Center,
                    modifier =
                        Modifier.padding(
                            horizontal =
                                8.dp
                        )
                )
            }

            // ====================================================
            // CARD
            // ====================================================

            CardBackground {

                if (
                    authManager
                        .needsEmailVerification
                ) {

                    // ============================================
                    // ATTENTE VÉRIFICATION EMAIL
                    // ============================================

                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally,
                        verticalArrangement =
                            Arrangement.spacedBy(
                                16.dp
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    6.dp
                                )
                    ) {

                        Text(
                            "✉️",
                            fontSize =
                                40.sp
                        )

                        Text(
                            _i18nContext.getString(
                                R.string
                                    .auth_verifie_ta_boite_mail
                            ),
                            color =
                                Color.White,
                            fontSize =
                                17.sp,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            text =
                                _i18nContext.getString(
                                    R.string
                                        .auth_un_lien_de_confirmation_a_ete_envoye_a_clique_le_puis_re,
                                    authManager
                                        .pendingVerificationEmail
                                        ?: _i18nContext
                                            .getString(
                                                R.string
                                                    .auth_fallback_email_address
                                            )
                                ),
                            color =
                                Color.White.copy(
                                    alpha =
                                        0.85f
                                ),
                            fontSize =
                                14.sp,
                            lineHeight =
                                20.sp,
                            textAlign =
                                TextAlign.Center
                        )

                        Box(
                            modifier =
                                Modifier
                                    .clickable(
                                        enabled =
                                            !authManager
                                                .isLoading
                                    ) {
                                        scope.launch {
                                            authManager
                                                .resendVerification()
                                        }
                                    }
                                    .padding(
                                        vertical =
                                            4.dp
                                    )
                        ) {

                            Text(
                                _i18nContext.getString(
                                    R.string
                                        .auth_renvoyer_le_lien
                                ),
                                color =
                                    Color.Cyan,
                                fontSize =
                                    15.sp,
                                fontWeight =
                                    FontWeight.Bold
                            )
                        }

                        Box(
                            modifier =
                                Modifier
                                    .clickable {

                                        authManager
                                            .needsEmailVerification =
                                            false

                                        isRegisterMode =
                                            false
                                    }
                                    .padding(
                                        vertical =
                                            4.dp
                                    )
                        ) {

                            Text(
                                _i18nContext.getString(
                                    R.string
                                        .auth_retour_a_la_connexion
                                ),
                                color =
                                    Color.White.copy(
                                        alpha =
                                            0.5f
                                    ),
                                fontSize =
                                    13.sp
                            )
                        }
                    }

                } else if (
                    showForgotPassword
                ) {

                    // ============================================
                    // MOT DE PASSE OUBLIÉ
                    // ============================================

                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally,
                        verticalArrangement =
                            Arrangement.spacedBy(
                                16.dp
                            ),
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Text(
                            text = "🔑",
                            fontSize =
                                38.sp
                        )

                        Text(
                            text =
                                _i18nContext.getString(
                                    R.string
                                        .auth_mot_de_passe_oublie
                                ),
                            color =
                                Color.White,
                            fontSize =
                                18.sp,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            text =
                                _i18nContext.getString(
                                    R.string
                                        .auth_indique_l_adresse_e_mail_de_ton_compte_tu_recevras_un_li
                                ),
                            color =
                                Color.White.copy(
                                    alpha =
                                        0.72f
                                ),
                            fontSize =
                                13.sp,
                            lineHeight =
                                19.sp,
                            textAlign =
                                TextAlign.Center
                        )

                        OutlinedTextField(
                            value =
                                resetEmail,
                            onValueChange = {

                                resetEmail =
                                    it

                                resetMessage =
                                    null

                                resetError =
                                    null
                            },
                            placeholder = {

                                Text(
                                    "Email",
                                    color =
                                        Color.White.copy(
                                            alpha =
                                                0.4f
                                        )
                                )
                            },
                            keyboardOptions =
                                KeyboardOptions(
                                    keyboardType =
                                        KeyboardType.Email
                                ),
                            singleLine =
                                true,
                            colors =
                                OutlinedTextFieldDefaults
                                    .colors(
                                        focusedBorderColor =
                                            Color.Transparent,
                                        unfocusedBorderColor =
                                            Color.Transparent,
                                        focusedContainerColor =
                                            Color.White.copy(
                                                alpha =
                                                    0.08f
                                            ),
                                        unfocusedContainerColor =
                                            Color.White.copy(
                                                alpha =
                                                    0.08f
                                            ),
                                        focusedTextColor =
                                            Color.White,
                                        unfocusedTextColor =
                                            Color.White,
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

                        resetMessage
                            ?.let {
                                    message ->

                                Text(
                                    text =
                                        message,
                                    color =
                                        Color(
                                            0xFF66E6B3
                                        ),
                                    fontSize =
                                        13.sp,
                                    lineHeight =
                                        18.sp,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                )
                            }

                        resetError
                            ?.let {
                                    error ->

                                Text(
                                    text =
                                        error,
                                    color =
                                        Color.Red.copy(
                                            alpha =
                                                0.9f
                                        ),
                                    fontSize =
                                        13.sp,
                                    lineHeight =
                                        18.sp,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                )
                            }

                        val canSendReset =
                            resetEmail
                                .trim()
                                .isNotEmpty() &&
                                    !isSendingReset

                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(
                                        48.dp
                                    )
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color.Cyan,
                                                Color(
                                                    0xFF00E676
                                                )
                                            )
                                        ),
                                        RoundedCornerShape(
                                            16.dp
                                        )
                                    )
                                    .clickable(
                                        enabled =
                                            canSendReset
                                    ) {

                                        scope.launch {

                                            isSendingReset =
                                                true

                                            resetMessage =
                                                null

                                            resetError =
                                                null

                                            try {

                                                resetMessage =
                                                    withContext(
                                                        Dispatchers.IO
                                                    ) {

                                                        APIClient
                                                            .shared
                                                            .forgotPassword(
                                                                resetEmail
                                                                    .trim()
                                                            )
                                                    }

                                            } catch (
                                                e: Exception
                                            ) {

                                                resetError =
                                                    e.localizedMessage
                                                        ?: _i18nContext
                                                            .getString(
                                                                R.string
                                                                    .auth_impossible_d_envoyer_le_lien
                                                            )

                                            } finally {

                                                isSendingReset =
                                                    false
                                            }
                                        }
                                    },
                            contentAlignment =
                                Alignment.Center
                        ) {

                            if (
                                isSendingReset
                            ) {

                                CircularProgressIndicator(
                                    modifier =
                                        Modifier.size(
                                            20.dp
                                        ),
                                    color =
                                        Color.Black,
                                    strokeWidth =
                                        2.dp
                                )

                            } else {

                                Text(
                                    text =
                                        _i18nContext
                                            .getString(
                                                R.string
                                                    .auth_envoyer_le_lien
                                            ),
                                    color =
                                        Color.Black.copy(
                                            alpha =
                                                if (
                                                    canSendReset
                                                ) {
                                                    1f
                                                } else {
                                                    0.45f
                                                }
                                        ),
                                    fontSize =
                                        15.sp,
                                    fontWeight =
                                        FontWeight.Bold
                                )
                            }
                        }

                        Text(
                            text =
                                _i18nContext
                                    .getString(
                                        R.string
                                            .auth_retour_a_la_connexion
                                    ),
                            color =
                                Color.White.copy(
                                    alpha =
                                        0.55f
                                ),
                            fontSize =
                                13.sp,
                            fontWeight =
                                FontWeight.Bold,
                            modifier =
                                Modifier
                                    .clickable(
                                        enabled =
                                            !isSendingReset
                                    ) {

                                        showForgotPassword =
                                            false

                                        resetMessage =
                                            null

                                        resetError =
                                            null
                                    }
                                    .padding(
                                        vertical =
                                            4.dp
                                    )
                        )
                    }

                } else {

                    // ============================================
                    // CONNEXION / INSCRIPTION
                    // ============================================

                    Column(
                        verticalArrangement =
                            Arrangement.spacedBy(
                                16.dp
                            )
                    ) {

                        // ========================================
                        // SWITCH CONNEXION / INSCRIPTION
                        // ========================================

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Color.Black.copy(
                                            alpha =
                                                0.25f
                                        ),
                                        RoundedCornerShape(
                                            16.dp
                                        )
                                    )
                                    .padding(
                                        4.dp
                                    ),
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    8.dp
                                )
                        ) {

                            ModeButton(
                                title =
                                    _i18nContext
                                        .getString(
                                            R.string
                                                .auth_connexion
                                        ),
                                isSelected =
                                    !isRegisterMode,
                                modifier =
                                    Modifier.weight(
                                        1f
                                    )
                            ) {
                                isRegisterMode =
                                    false

                                showForgotPassword =
                                    false
                            }

                            ModeButton(
                                title =
                                    _i18nContext
                                        .getString(
                                            R.string
                                                .auth_s_inscrire
                                        ),
                                isSelected =
                                    isRegisterMode,
                                modifier =
                                    Modifier.weight(
                                        1f
                                    )
                            ) {
                                isRegisterMode =
                                    true

                                showForgotPassword =
                                    false
                            }
                        }

                        // ========================================
                        // EMAIL
                        // ========================================

                        OutlinedTextField(
                            value =
                                email,
                            onValueChange = {
                                email =
                                    it
                            },
                            placeholder = {

                                Text(
                                    "Email",
                                    color =
                                        Color.White.copy(
                                            alpha =
                                                0.4f
                                        )
                                )
                            },
                            keyboardOptions =
                                KeyboardOptions(
                                    keyboardType =
                                        KeyboardType.Email
                                ),
                            singleLine =
                                true,
                            colors =
                                OutlinedTextFieldDefaults
                                    .colors(
                                        focusedBorderColor =
                                            Color.Transparent,
                                        unfocusedBorderColor =
                                            Color.Transparent,
                                        focusedContainerColor =
                                            Color.White.copy(
                                                alpha =
                                                    0.08f
                                            ),
                                        unfocusedContainerColor =
                                            Color.White.copy(
                                                alpha =
                                                    0.08f
                                            ),
                                        focusedTextColor =
                                            Color.White,
                                        unfocusedTextColor =
                                            Color.White
                                    ),
                            shape =
                                RoundedCornerShape(
                                    14.dp
                                ),
                            modifier =
                                Modifier.fillMaxWidth()
                        )

                        // ========================================
                        // MOT DE PASSE
                        // ========================================

                        OutlinedTextField(
                            value =
                                password,
                            onValueChange = {
                                password =
                                    it
                            },
                            placeholder = {

                                Text(
                                    _i18nContext
                                        .getString(
                                            R.string
                                                .account_mot_de_passe
                                        ),
                                    color =
                                        Color.White.copy(
                                            alpha =
                                                0.4f
                                        )
                                )
                            },
                            visualTransformation =
                                if (
                                    passwordVisible
                                ) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                            keyboardOptions =
                                KeyboardOptions(
                                    keyboardType =
                                        KeyboardType.Password
                                ),
                            trailingIcon = {

                                PasswordVisibilityButton(
                                    isVisible =
                                        passwordVisible,
                                    onClick = {
                                        passwordVisible =
                                            !passwordVisible
                                    }
                                )
                            },
                            singleLine =
                                true,
                            colors =
                                OutlinedTextFieldDefaults
                                    .colors(
                                        focusedBorderColor =
                                            Color.Transparent,
                                        unfocusedBorderColor =
                                            Color.Transparent,
                                        focusedContainerColor =
                                            Color.White.copy(
                                                alpha =
                                                    0.08f
                                            ),
                                        unfocusedContainerColor =
                                            Color.White.copy(
                                                alpha =
                                                    0.08f
                                            ),
                                        focusedTextColor =
                                            Color.White,
                                        unfocusedTextColor =
                                            Color.White,
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

                        // ========================================
                        // MOT DE PASSE OUBLIÉ
                        // ========================================

                        if (
                            !isRegisterMode
                        ) {

                            Row(
                                modifier =
                                    Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.End
                            ) {

                                Text(
                                    text =
                                        _i18nContext
                                            .getString(
                                                R.string
                                                    .auth_mot_de_passe_oublie
                                            ),
                                    color =
                                        Color.Cyan.copy(
                                            alpha =
                                                0.90f
                                        ),
                                    fontSize =
                                        13.sp,
                                    fontWeight =
                                        FontWeight.SemiBold,
                                    modifier =
                                        Modifier
                                            .clickable(
                                                enabled =
                                                    !authManager
                                                        .isLoading
                                            ) {

                                                resetEmail =
                                                    email
                                                        .trim()

                                                resetMessage =
                                                    null

                                                resetError =
                                                    null

                                                showForgotPassword =
                                                    true
                                            }
                                            .padding(
                                                start =
                                                    8.dp,
                                                top =
                                                    2.dp,
                                                bottom =
                                                    2.dp
                                            )
                                )
                            }
                        }

                        // ========================================
                        // INSCRIPTION UNIQUEMENT
                        // ========================================

                        if (
                            isRegisterMode
                        ) {

                            // ====================================
                            // CONFIRMATION MOT DE PASSE
                            // ====================================

                            OutlinedTextField(
                                value =
                                    confirmPassword,
                                onValueChange = {
                                    confirmPassword =
                                        it
                                },
                                placeholder = {

                                    Text(
                                        _i18nContext
                                            .getString(
                                                R.string
                                                    .auth_confirmer_le_mot_de_passe
                                            ),
                                        color =
                                            Color.White.copy(
                                                alpha =
                                                    0.4f
                                            )
                                    )
                                },
                                visualTransformation =
                                    if (
                                        confirmPasswordVisible
                                    ) {
                                        VisualTransformation.None
                                    } else {
                                        PasswordVisualTransformation()
                                    },
                                keyboardOptions =
                                    KeyboardOptions(
                                        keyboardType =
                                            KeyboardType.Password
                                    ),
                                trailingIcon = {

                                    PasswordVisibilityButton(
                                        isVisible =
                                            confirmPasswordVisible,
                                        onClick = {
                                            confirmPasswordVisible =
                                                !confirmPasswordVisible
                                        }
                                    )
                                },
                                singleLine =
                                    true,
                                colors =
                                    OutlinedTextFieldDefaults
                                        .colors(
                                            focusedBorderColor =
                                                Color.Transparent,
                                            unfocusedBorderColor =
                                                Color.Transparent,
                                            focusedContainerColor =
                                                Color.White.copy(
                                                    alpha =
                                                        0.08f
                                                ),
                                            unfocusedContainerColor =
                                                Color.White.copy(
                                                    alpha =
                                                        0.08f
                                                ),
                                            focusedTextColor =
                                                Color.White,
                                            unfocusedTextColor =
                                                Color.White,
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

                            // ====================================
                            // CRITÈRES MOT DE PASSE
                            // ====================================

                            Column(
                                verticalArrangement =
                                    Arrangement.spacedBy(
                                        6.dp
                                    ),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            top =
                                                2.dp
                                        )
                            ) {

                                RequirementRow(
                                    _i18nContext
                                        .getString(
                                            R.string
                                                .auth_text_12_caracteres_minimum
                                        ),
                                    isMet =
                                        hasMinLength
                                )

                                RequirementRow(
                                    _i18nContext
                                        .getString(
                                            R.string
                                                .auth_une_majuscule
                                        ),
                                    isMet =
                                        hasUppercase
                                )

                                RequirementRow(
                                    _i18nContext
                                        .getString(
                                            R.string
                                                .auth_un_chiffre
                                        ),
                                    isMet =
                                        hasDigit
                                )

                                RequirementRow(
                                    _i18nContext
                                        .getString(
                                            R.string
                                                .auth_un_symbole
                                        ),
                                    isMet =
                                        hasSymbol
                                )

                                if (
                                    confirmPassword
                                        .isNotEmpty()
                                ) {

                                    RequirementRow(
                                        _i18nContext
                                            .getString(
                                                R.string
                                                    .auth_les_deux_mots_de_passe_correspondent
                                            ),
                                        isMet =
                                            passwordsMatch
                                    )
                                }
                            }
                        }

                        // ========================================
                        // ERREUR SERVEUR
                        // ========================================

                        authManager
                            .errorMessage
                            ?.let {
                                    error ->

                                Text(
                                    text =
                                        error,
                                    color =
                                        Color.Red.copy(
                                            alpha =
                                                0.9f
                                        ),
                                    fontSize =
                                        13.sp,
                                    modifier =
                                        Modifier.fillMaxWidth()
                                )
                            }

                        // ========================================
                        // BOUTON ACTION
                        // ========================================

                        val canSubmit =
                            isFormValid &&
                                    !authManager
                                        .isLoading

                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(
                                        48.dp
                                    )
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color.Cyan,
                                                Color(
                                                    0xFF00E676
                                                )
                                            )
                                        ),
                                        RoundedCornerShape(
                                            16.dp
                                        )
                                    )
                                    .clickable(
                                        enabled =
                                            canSubmit
                                    ) {

                                        scope.launch {

                                            if (
                                                isRegisterMode
                                            ) {

                                                authManager
                                                    .register(
                                                        email,
                                                        password
                                                    )

                                            } else {

                                                authManager
                                                    .login(
                                                        email,
                                                        password
                                                    )
                                            }
                                        }
                                    },
                            contentAlignment =
                                Alignment.Center
                        ) {

                            if (
                                authManager
                                    .isLoading
                            ) {

                                CircularProgressIndicator(
                                    modifier =
                                        Modifier.size(
                                            20.dp
                                        ),
                                    color =
                                        Color.Black,
                                    strokeWidth =
                                        2.dp
                                )

                            } else {

                                Text(
                                    text =
                                        if (
                                            isRegisterMode
                                        ) {

                                            _i18nContext
                                                .getString(
                                                    R.string
                                                        .auth_s_inscrire
                                                )

                                        } else {

                                            _i18nContext
                                                .getString(
                                                    R.string
                                                        .auth_connexion
                                                )
                                        },
                                    color =
                                        Color.Black,
                                    fontSize =
                                        15.sp,
                                    fontWeight =
                                        FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


// ================================================================
// PASSWORD VISIBILITY BUTTON
//
// Équivalent Android du composant iOS :
//     eye
//     eye.slash
//
// Pas d'emoji et aucune dépendance material-icons nécessaire.
// ================================================================

@Composable
private fun PasswordVisibilityButton(
    isVisible: Boolean,
    onClick: () -> Unit
) {

    IconButton(
        onClick =
            onClick,
        modifier =
            Modifier.size(
                38.dp
            )
    ) {

        PasswordEyeIcon(
            isVisible =
                isVisible,
            modifier =
                Modifier.size(
                    20.dp
                )
        )
    }
}


@Composable
private fun PasswordEyeIcon(
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {

    val color =
        Color.White.copy(
            alpha =
                0.58f
        )

    Canvas(
        modifier =
            modifier
    ) {

        val strokeWidth =
            1.7.dp.toPx()

        /*
         * Forme de l'œil.
         *
         * On utilise deux courbes de Bézier afin d'obtenir
         * un dessin proche du SF Symbol "eye" utilisé sur iOS.
         */
        val eyePath =
            Path().apply {

                moveTo(
                    x =
                        size.width *
                                0.07f,
                    y =
                        size.height *
                                0.50f
                )

                cubicTo(
                    x1 =
                        size.width *
                                0.27f,
                    y1 =
                        size.height *
                                0.20f,
                    x2 =
                        size.width *
                                0.73f,
                    y2 =
                        size.height *
                                0.20f,
                    x3 =
                        size.width *
                                0.93f,
                    y3 =
                        size.height *
                                0.50f
                )

                cubicTo(
                    x1 =
                        size.width *
                                0.73f,
                    y1 =
                        size.height *
                                0.80f,
                    x2 =
                        size.width *
                                0.27f,
                    y2 =
                        size.height *
                                0.80f,
                    x3 =
                        size.width *
                                0.07f,
                    y3 =
                        size.height *
                                0.50f
                )

                close()
            }

        drawPath(
            path =
                eyePath,
            color =
                color,
            style =
                Stroke(
                    width =
                        strokeWidth,
                    cap =
                        StrokeCap.Round
                )
        )

        /*
         * Pupille.
         */
        drawCircle(
            color =
                color,
            radius =
                size.minDimension *
                        0.105f,
            center =
                Offset(
                    x =
                        size.width /
                                2f,
                    y =
                        size.height /
                                2f
                )
        )

        /*
         * Mot de passe caché :
         * équivalent du SF Symbol "eye.slash".
         */
        if (
            !isVisible
        ) {

            /*
             * Petit fond sous le slash afin qu'il reste
             * visuellement propre en traversant l'œil.
             */
            drawLine(
                color =
                    Color(
                        0xFF15151B
                    ),
                start =
                    Offset(
                        x =
                            size.width *
                                    0.14f,
                        y =
                            size.height *
                                    0.14f
                    ),
                end =
                    Offset(
                        x =
                            size.width *
                                    0.86f,
                        y =
                            size.height *
                                    0.86f
                    ),
                strokeWidth =
                    strokeWidth *
                            2.5f,
                cap =
                    StrokeCap.Round
            )

            drawLine(
                color =
                    color,
                start =
                    Offset(
                        x =
                            size.width *
                                    0.14f,
                        y =
                            size.height *
                                    0.14f
                    ),
                end =
                    Offset(
                        x =
                            size.width *
                                    0.86f,
                        y =
                            size.height *
                                    0.86f
                    ),
                strokeWidth =
                    strokeWidth,
                cap =
                    StrokeCap.Round
            )
        }
    }
}


// ================================================================
// MODE BUTTON
// ================================================================

@Composable
private fun ModeButton(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {

    Box(
        modifier =
            modifier
                .background(
                    if (
                        isSelected
                    ) {
                        Color.Cyan
                    } else {
                        Color.Transparent
                    },
                    RoundedCornerShape(
                        12.dp
                    )
                )
                .clickable {
                    onClick()
                }
                .padding(
                    vertical =
                        11.dp
                ),
        contentAlignment =
            Alignment.Center
    ) {

        Text(
            text =
                title,
            color =
                if (
                    isSelected
                ) {
                    Color.Black
                } else {
                    Color.White.copy(
                        alpha =
                            0.75f
                    )
                },
            fontSize =
                14.sp,
            fontWeight =
                FontWeight.Bold
        )
    }
}


// ================================================================
// PASSWORD REQUIREMENT
// ================================================================

@Composable
private fun RequirementRow(
    text: String,
    isMet: Boolean
) {

    Row(
        horizontalArrangement =
            Arrangement.spacedBy(
                6.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Text(
            text =
                if (
                    isMet
                ) {
                    "✓"
                } else {
                    "○"
                },
            color =
                if (
                    isMet
                ) {
                    Color(
                        0xFF00E676
                    )
                } else {
                    Color.White.copy(
                        alpha =
                            0.3f
                    )
                },
            fontSize =
                12.sp,
            fontWeight =
                FontWeight.Bold
        )

        Text(
            text =
                text,
            color =
                if (
                    isMet
                ) {
                    Color.White.copy(
                        alpha =
                            0.85f
                    )
                } else {
                    Color.White.copy(
                        alpha =
                            0.4f
                    )
                },
            fontSize =
                12.sp
        )
    }
}


// ================================================================
// CARD BACKGROUND
// ================================================================

@Composable
private fun CardBackground(
    content: @Composable () -> Unit
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    Color.White.copy(
                        alpha =
                            0.07f
                    ),
                    RoundedCornerShape(
                        24.dp
                    )
                )
                .border(
                    width =
                        1.dp,
                    color =
                        Color.White.copy(
                            alpha =
                                0.10f
                        ),
                    shape =
                        RoundedCornerShape(
                            24.dp
                        )
                )
                .padding(
                    18.dp
                )
    ) {
        content()
    }
}