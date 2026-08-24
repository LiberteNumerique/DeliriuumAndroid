package com.deliriuum.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.deliriuum.app.data.AuthManager
import com.deliriuum.app.data.TunnelManager
import com.deliriuum.app.data.TunnelStatus
import com.deliriuum.app.ui.components.SideMenuLayout
import kotlinx.coroutines.launch
import com.deliriuum.app.data.PrivacyAuditManager
import com.deliriuum.app.data.PrivacyAuditState
import com.deliriuum.app.data.PrivacyCheckStatus
import com.deliriuum.app.data.PrivacyProtectionLevel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeView(
    authManager: AuthManager,
    onNavigateBackToWelcome: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tunnelManager = TunnelManager.shared

    val privacyAuditManager = PrivacyAuditManager.shared
    val privacyAuditState = privacyAuditManager.state

    LaunchedEffect(privacyAuditState) {
        android.util.Log.d(
            "PrivacyAuditUI",
            "HOME RECOMPOSE AUDIT = $privacyAuditState"
        )
    }

    var isSideMenuOpen by remember { mutableStateOf(false) }
    var activeSheet by remember { mutableStateOf<HomeSheet?>(null) }
    var isToggling by remember { mutableStateOf(false) }

    /*
     * Remplacement de session en cours.
     *
     * Distinct d'isToggling : la popup a son propre indicateur
     * de chargement, et le bouton principal de la carte
     * Protection ne doit pas clignoter pendant l'opération.
     */
    var isReplacingSession by remember { mutableStateOf(false) }

    /*
     * NOTE :
     *
     * l'ancien booléen local showSessionConflictDialog a été
     * supprimé. Il était déclaré mais jamais écrit, donc la
     * popup ne pouvait jamais s'ouvrir.
     *
     * La source de vérité est tunnelManager.sessionConflictMessage,
     * renseignée dans connect() lorsque le master répond 409
     * (APIError.Conflict), et remise à null par connect(),
     * disconnect(), prepareForLogout() ou clearSessionConflict().
     */

    val protectedNavigationEnabled =
        authManager.isLoggedIn &&
                tunnelManager.isProtected

    // ============================================================
    // VPN PERMISSION
    // ============================================================

    val vpnPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                scope.launch {
                    try {
                        tunnelManager.connect()
                    } catch (_: Exception) {
                        /*
                         * En cas de 409, connect() a déjà renseigné
                         * sessionConflictMessage : la popup s'ouvre
                         * d'elle-même.
                         */
                    }
                }
            }
        }

    // ============================================================
    // INTERNAL BROWSER
    // ============================================================

    val openInternalPage: (String, String) -> Unit =
        { title, targetUrl ->

            /*
             * Sécurité supplémentaire :
             * aucun écran Gecko Deliriuum ne s'ouvre
             * si le tunnel n'est pas réellement protégé.
             */
            if (protectedNavigationEnabled) {
                val intent =
                    Intent(
                        context,
                        GeckoBrowserActivity::class.java
                    ).apply {
                        putExtra("url", targetUrl)
                        putExtra("title", title)
                    }

                context.startActivity(intent)
            }
        }

    // ============================================================
    // TOGGLE PROTECTION
    // ============================================================

    val toggleProtection: () -> Unit =
        {
            if (!authManager.isLoggedIn) {
                activeSheet = HomeSheet.AUTH
            } else {
                scope.launch {
                    isToggling = true

                    try {
                        /*
                         * Si WireGuard est encore techniquement UP
                         * mais que le watchdog a déclaré le chemin
                         * indisponible, on autorise aussi la coupure.
                         */
                        if (
                            tunnelManager.isProtected ||
                            tunnelManager.status == TunnelStatus.CONNECTED
                        ) {
                            tunnelManager.disconnect()
                        } else {
                            tunnelManager.clearAutoDisconnectedNotice()

                            val intent =
                                tunnelManager.checkVpnPermissionIntent()

                            if (intent != null) {
                                vpnPermissionLauncher.launch(intent)
                            } else {
                                tunnelManager.connect()
                            }
                        }
                    } catch (_: Exception) {
                        /*
                         * Le cas 409 n'est pas traité ici :
                         * connect() a positionné
                         * sessionConflictMessage avant de relancer
                         * l'exception, et la popup se déclenche
                         * sur cet état observable.
                         */
                    } finally {
                        isToggling = false
                    }
                }
            }
        }

    // ============================================================
    // REPLACE ACTIVE SESSION
    // ============================================================

    val replaceActiveSession: () -> Unit =
        {
            scope.launch {
                isReplacingSession = true

                try {
                    /*
                     * Ferme l'ancienne session côté master,
                     * puis rouvre immédiatement un tunnel avec
                     * la même identité WireGuard.
                     *
                     * La permission VPN est nécessairement déjà
                     * accordée : un 409 ne peut survenir qu'après
                     * un connect() lancé une fois l'autorisation
                     * obtenue.
                     */
                    tunnelManager.replaceActiveSession()

                    /*
                     * Succès : connect() a déjà remis
                     * sessionConflictMessage à null. Appel
                     * défensif au cas où le flux évoluerait.
                     */
                    tunnelManager.clearSessionConflict()

                } catch (_: Exception) {
                    /*
                     * Échec : on garde la popup ouverte.
                     *
                     * - nouveau 409 -> sessionConflictMessage
                     *   a été réécrit avec le message du master
                     * - autre erreur -> lastErrorMessage est
                     *   renseigné et affiché dans la popup
                     */
                } finally {
                    isReplacingSession = false
                }
            }
        }

    // ============================================================
    // LOGOUT
    // ============================================================

    val logoutFromMenu: () -> Unit =
        {
            scope.launch {
                isToggling = true

                try {
                    /*
                     * AuthManager.logout() effectue désormais :
                     * - nettoyage du tunnel
                     * - suppression du Device master
                     * - destruction de l'identité WireGuard locale
                     * - suppression des tokens
                     */
                    authManager.logout()
                } catch (_: Exception) {
                } finally {
                    isSideMenuOpen = false
                    activeSheet = null
                    isToggling = false
                }
            }
        }

    // ============================================================
    // AUTH
    // ============================================================

    LaunchedEffect(authManager.isLoggedIn) {
        if (
            authManager.isLoggedIn &&
            activeSheet == HomeSheet.AUTH
        ) {
            activeSheet = null
            toggleProtection()
        }
    }

    // ============================================================
    // CONTENT
    // ============================================================

    SideMenuLayout(
        isOpen = isSideMenuOpen,
        onClose = {
            isSideMenuOpen = false
        },
        authManager = authManager,
        onOpenAccount = {
            activeSheet = HomeSheet.ACCOUNT
        },
        onOpenGuide = {
            activeSheet = HomeSheet.GUIDE
        },
        onOpenAbout = {
            activeSheet = HomeSheet.ABOUT
        },
        onOpenFAQ = {
            activeSheet = HomeSheet.FAQ
        },
        onLogout = logoutFromMenu
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            HomeBackground()

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp)
                        .padding(top = 50.dp, bottom = 32.dp),
                verticalArrangement =
                    Arrangement.spacedBy(20.dp)
            ) {
                TopButtons(
                    onMenuClick = {
                        isSideMenuOpen = true
                    },
                    onCloseClick = onNavigateBackToWelcome
                )

                SupportBanner {
                    /*
                     * Le bandeau de soutien n'a pas besoin du VPN.
                     * S'il n'est pas actif, on ouvre le navigateur
                     * système afin de ne pas donner l'impression
                     * que Deliriuum bloque Internet.
                     */
                    if (protectedNavigationEnabled) {
                        openInternalPage(
                            "Soutenir Deliriuum",
                            "https://deliriuum.com/soutenir.html"
                        )
                    } else {
                        val intent =
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(
                                    "https://deliriuum.com/soutenir.html"
                                )
                            )
                        context.startActivity(intent)
                    }
                }

                if (tunnelManager.autoDisconnected) {
                    AutoDisconnectedCard(
                        onReconnect = {
                            tunnelManager.clearAutoDisconnectedNotice()
                            toggleProtection()
                        }
                    )
                }

                ProtectionCard(
                    tunnelManager = tunnelManager,
                    isToggling = isToggling,
                    onToggle = toggleProtection
                )



                WebNavigationSpace(
                    enabled = protectedNavigationEnabled,
                    onOpen = { rawAddress ->
                        val cleaned =
                            rawAddress.trim()

                        if (cleaned.isNotBlank()) {
                            val targetUrl =
                                when {
                                    cleaned.startsWith(
                                        "https://",
                                        ignoreCase = true
                                    ) ->
                                        cleaned

                                    cleaned.startsWith(
                                        "http://",
                                        ignoreCase = true
                                    ) ->
                                        cleaned

                                    else ->
                                        "https://$cleaned"
                                }

                            openInternalPage(
                                "Navigation",
                                targetUrl
                            )
                        }
                    }
                )

                SocialSpace(
                    enabled = protectedNavigationEnabled,
                    openSocial = { name, targetUrl ->
                        openInternalPage(
                            "$name via Deliriuum",
                            targetUrl
                        )
                    }
                )

                if (tunnelManager.isProtected) {
                    DeepShieldStatusCard()

                    PrivacyAuditCard(
                        auditState = privacyAuditState,
                        tunnelManager = tunnelManager
                    )
                }
            }

            // ====================================================
            // SESSION CONFLICT DIALOG
            // ====================================================

            /*
             * S'ouvre dès que le master signale une session VPN
             * déjà active pour cet appareil (HTTP 409).
             *
             * Cas typique : l'application a été tuée sans passer
             * par disconnect(), la session distante est restée
             * ouverte, et la reconnexion est refusée.
             */
            tunnelManager.sessionConflictMessage?.let { conflictMessage ->

                SessionConflictDialog(
                    message = conflictMessage,
                    errorMessage = tunnelManager.lastErrorMessage,
                    isBusy = isReplacingSession,
                    onDismiss = {
                        if (!isReplacingSession) {
                            tunnelManager.clearSessionConflict()
                        }
                    },
                    onReplace = replaceActiveSession
                )
            }

            // ====================================================
            // SHEETS
            // ====================================================

            activeSheet?.let { sheet ->
                ModalBottomSheet(
                    onDismissRequest = {
                        activeSheet = null
                    },
                    containerColor = Color(0xFF0C0C12),
                    scrimColor =
                        Color.Black.copy(alpha = 0.6f)
                ) {
                    when (sheet) {
                        HomeSheet.ACCOUNT -> {
                            Box(
                                Modifier.fillMaxHeight(0.88f)
                            ) {
                                AccountScreen(
                                    authManager = authManager,
                                    onDismiss = {
                                        activeSheet = null
                                    },
                                    onLogout = logoutFromMenu
                                )
                            }
                        }

                        HomeSheet.ABOUT -> {
                            Box(
                                Modifier.fillMaxHeight(0.88f)
                            ) {
                                AboutScreen(
                                    onDismiss = {
                                        activeSheet = null
                                    }
                                )
                            }
                        }

                        HomeSheet.GUIDE -> {
                            Box(
                                Modifier.fillMaxHeight(0.88f)
                            ) {
                                PrivacyGuideScreen(
                                    onDismiss = {
                                        activeSheet = null
                                    }
                                )
                            }
                        }

                        HomeSheet.FAQ -> {
                            Box(
                                Modifier.fillMaxHeight(0.88f)
                            ) {
                                FAQScreen(
                                    onDismiss = {
                                        activeSheet = null
                                    }
                                )
                            }
                        }

                        HomeSheet.AUTH -> {
                            Box(
                                Modifier.wrapContentHeight()
                            ) {
                                AuthScreen(
                                    authManager = authManager,
                                    onDismiss = {
                                        activeSheet = null
                                    }
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
// SESSION CONFLICT DIALOG
// ================================================================

@Composable
private fun SessionConflictDialog(
    message: String,
    errorMessage: String?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onReplace: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,

        /*
         * Pendant le remplacement, on bloque la fermeture :
         * l'opération touche à la session distante et ne doit
         * pas être interrompue par un tap hors de la popup.
         */
        properties =
            DialogProperties(
                dismissOnBackPress = !isBusy,
                dismissOnClickOutside = !isBusy
            ),

        containerColor = Color(0xFF12121A),
        titleContentColor = Color.White,
        textContentColor = Color.White.copy(alpha = 0.74f),
        shape = RoundedCornerShape(24.dp),

        icon = {
            Box(
                modifier =
                    Modifier
                        .size(52.dp)
                        .background(
                            Color(0xFFFFC633)
                                .copy(alpha = 0.14f),
                            CircleShape
                        )
                        .border(
                            1.dp,
                            Color(0xFFFFC633)
                                .copy(alpha = 0.30f),
                            CircleShape
                        ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⚠",
                    fontSize = 24.sp,
                    color = Color(0xFFFFC633)
                )
            }
        },

        title = {
            Text(
                text = "Une session est déjà active",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },

        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {
                /*
                 * Message renvoyé par le master (detail du 409).
                 */
                Text(
                    text = message,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text =
                        "Cela arrive lorsque l'application a été fermée sans couper la protection. " +
                                "Vous pouvez fermer cette session et vous reconnecter.",
                    color = Color.White.copy(alpha = 0.50f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                /*
                 * Erreur survenue pendant une tentative de
                 * remplacement précédente.
                 */
                errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = Color.Red.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },

        confirmButton = {
            Box(
                modifier =
                    Modifier
                        .background(
                            Color.Cyan.copy(
                                alpha = if (isBusy) 0.35f else 1f
                            ),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable(enabled = !isBusy) {
                            onReplace()
                        }
                        .padding(
                            horizontal = 18.dp,
                            vertical = 11.dp
                        ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            strokeWidth = 2.dp,
                            color = Color.Black
                        )
                    }

                    Text(
                        text =
                            if (isBusy) {
                                "Fermeture…"
                            } else {
                                "Fermer et me reconnecter"
                            },
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },

        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isBusy
            ) {
                Text(
                    text = "Annuler",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}


// ================================================================
// BACKGROUND
// ================================================================

@Composable
private fun HomeBackground() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
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
                                y = 200f
                            ),
                        radius = 1200f
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
// TOP BUTTONS
// ================================================================

@Composable
private fun TopButtons(
    onMenuClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween
    ) {
        IconButtonWithBorder(
            iconEmoji = "☰",
            onClick = onMenuClick
        )

        IconButtonWithBorder(
            iconEmoji = "✕",
            onClick = onCloseClick
        )
    }
}


// ================================================================
// SUPPORT BANNER
// ================================================================

@Composable
private fun SupportBanner(
    onClick: () -> Unit
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFFFFC633),
                            Color(0xFFFFA626)
                        )
                    ),
                    RoundedCornerShape(16.dp)
                )
                .clickable {
                    onClick()
                }
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Text(
            text =
                "Deliriuum est gratuit grâce aux dons.",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        Spacer(
            modifier = Modifier.weight(1f)
        )

        Text(
            text = "Soutenir ➔",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
    }
}


// ================================================================
// AUTO DISCONNECTED
// ================================================================

@Composable
private fun AutoDisconnectedCard(
    onReconnect: () -> Unit
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    Color.White.copy(alpha = 0.075f),
                    RoundedCornerShape(24.dp)
                )
                .border(
                    1.dp,
                    Color(0xFFFFC633)
                        .copy(alpha = 0.35f),
                    RoundedCornerShape(24.dp)
                )
                .padding(18.dp),
        verticalArrangement =
            Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .background(
                            Color(0xFFFFC633)
                                .copy(alpha = 0.16f),
                            CircleShape
                        ),
                contentAlignment =
                    Alignment.Center
            ) {
                Text(
                    text = "🔒",
                    fontSize = 22.sp
                )
            }

            Column {
                Text(
                    text =
                        "Session fermée automatiquement",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text =
                        "Protection après inactivité",
                    color = Color(0xFFFFC633),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text(
            text =
                "Votre session a été fermée automatiquement après 30 minutes d'inactivité afin de protéger votre confidentialité.",
            color =
                Color.White.copy(alpha = 0.74f),
            fontSize = 13.sp,
            lineHeight = 20.sp
        )

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0xFFFFC633),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable {
                        onReconnect()
                    }
                    .padding(vertical = 13.dp),
            contentAlignment =
                Alignment.Center
        ) {
            Text(
                text = "Se reconnecter",
                color = Color.Black,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}


// ================================================================
// LOCKED WEB NOTICE
// ================================================================

@Composable
private fun LockedWebNotice(
    isLoggedIn: Boolean
) {
    val message =
        if (!isLoggedIn) {
            "Connectez-vous puis activez la protection Deliriuum pour accéder à la navigation Web et aux réseaux sociaux dans le navigateur protégé."
        } else {
            "Activez la protection Deliriuum pour accéder à la navigation Web et aux réseaux sociaux dans le navigateur protégé."
        }

    Text(
        text = message,
        color =
            Color.White.copy(alpha = 0.58f),
        fontSize = 12.sp,
        lineHeight = 18.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}


// ================================================================
// PROTECTION CARD
// ================================================================

@Composable
private fun ProtectionCard(
    tunnelManager: TunnelManager,
    isToggling: Boolean,
    onToggle: () -> Unit
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    Color.White.copy(alpha = 0.07f),
                    RoundedCornerShape(24.dp)
                )
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.10f),
                    RoundedCornerShape(24.dp)
                )
                .padding(18.dp),
        verticalArrangement =
            Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Protection Deliriuum",
                    fontSize = 12.sp,
                    color =
                        Color.White.copy(alpha = 0.55f)
                )

                val statusLabel =
                    when {
                        tunnelManager.status ==
                                TunnelStatus.CONNECTED &&
                                !tunnelManager.tunnelReachable ->
                            "Tunnel indisponible"

                        tunnelManager.status ==
                                TunnelStatus.CONNECTED ->
                            "Active"

                        tunnelManager.status ==
                                TunnelStatus.CONNECTING ->
                            "Activation…"

                        tunnelManager.status ==
                                TunnelStatus.DISCONNECTING ->
                            "Désactivation…"

                        else ->
                            "Inactive"
                    }

                Text(
                    text = statusLabel,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(
                modifier = Modifier.weight(1f)
            )

            Text(
                text =
                    if (tunnelManager.isProtected) {
                        "🔓"
                    } else {
                        "🔒"
                    },
                fontSize = 22.sp
            )
        }

        val buttonBgColor by
        animateColorAsState(
            targetValue =
                if (tunnelManager.isProtected) {
                    Color.Cyan
                } else {
                    Color.Cyan.copy(alpha = 0.08f)
                },
            label = "btnBg"
        )

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        buttonBgColor,
                        RoundedCornerShape(16.dp)
                    )
                    .border(
                        if (tunnelManager.isProtected) {
                            0.dp
                        } else {
                            1.5.dp
                        },
                        Color.Cyan.copy(alpha = 0.5f),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable(
                        enabled = !isToggling
                    ) {
                        onToggle()
                    }
                    .padding(vertical = 13.dp),
            contentAlignment =
                Alignment.Center
        ) {
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                if (isToggling) {
                    CircularProgressIndicator(
                        modifier =
                            Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color =
                            if (tunnelManager.isProtected) {
                                Color.Black
                            } else {
                                Color.Cyan
                            }
                    )
                }

                Text(
                    text =
                        when (tunnelManager.status) {
                            TunnelStatus.CONNECTED ->
                                "Me déconnecter"

                            TunnelStatus.CONNECTING ->
                                "Connexion…"

                            TunnelStatus.DISCONNECTING ->
                                "Déconnexion…"

                            else ->
                                "Me protéger"
                        },
                    fontWeight = FontWeight.Bold,
                    color =
                        if (tunnelManager.isProtected) {
                            Color.Black
                        } else {
                            Color.Cyan
                        },
                    fontSize = 14.sp
                )
            }
        }

        tunnelManager.lastErrorMessage
            ?.let { error ->
                Text(
                    text = error,
                    fontSize = 12.sp,
                    color =
                        Color.Red.copy(alpha = 0.85f)
                )
            }
    }
}


// ================================================================
// BROWSER SAFETY CARD
// ================================================================


@Composable
private fun RowScope.SafetyBadge(
    text: String
) {
    Box(
        modifier =
            Modifier
                .weight(1f)
                .background(
                    Color.Cyan.copy(alpha = 0.07f),
                    RoundedCornerShape(50)
                )
                .border(
                    1.dp,
                    Color.Cyan.copy(alpha = 0.16f),
                    RoundedCornerShape(50)
                )
                .padding(
                    horizontal = 8.dp,
                    vertical = 8.dp
                ),
        contentAlignment =
            Alignment.Center
    ) {
        Text(
            text = "✓ $text",
            color =
                Color.Cyan.copy(alpha = 0.88f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}


// ================================================================
// WEB NAVIGATION SPACE
// ================================================================

@Composable
private fun WebNavigationSpace(
    enabled: Boolean,
    onOpen: (String) -> Unit
) {
    var address by remember {
        mutableStateOf("")
    }

    val focusManager =
        LocalFocusManager.current

    SectionCard(
        emoji = "🌐",
        title = "Navigation Web privé",
        subtitle =
            "Ouvrir directement un site dans Deliriuum"
    ) {
        OutlinedTextField(
            value = address,
            onValueChange = {
                address = it
            },
            enabled = enabled,
            placeholder = {
                Text("exemple.org")
            },
            singleLine = true,
            modifier =
                Modifier.fillMaxWidth(),
            shape =
                RoundedCornerShape(16.dp),
            colors = protectedTextFieldColors(),
            keyboardOptions =
                KeyboardOptions(
                    imeAction = ImeAction.Go
                ),
            keyboardActions =
                KeyboardActions(
                    onGo = {
                        val value =
                            address.trim()

                        if (
                            enabled &&
                            value.isNotBlank()
                        ) {
                            focusManager.clearFocus()
                            onOpen(value)
                        }
                    }
                ),
            trailingIcon = {
                Text(
                    text =
                        if (enabled) {
                            "→"
                        } else {
                            "🔒"
                        },
                    color =
                        if (enabled) {
                            Color.Cyan
                        } else {
                            Color.White.copy(alpha = 0.25f)
                        },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier =
                        Modifier
                            .padding(end = 12.dp)
                            .clickable(
                                enabled =
                                    enabled &&
                                            address.isNotBlank()
                            ) {
                                focusManager.clearFocus()
                                onOpen(address.trim())
                            }
                )
            }
        )

        Text(
            text =
                "Les adresses sans protocole sont automatiquement ouvertes en HTTPS.",
            color =
                Color.White.copy(alpha = 0.45f),
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
    }
}


// ================================================================
// SOCIAL SPACE
// ================================================================

@Composable
private fun SocialSpace(
    enabled: Boolean,
    openSocial:
        (String, String) -> Unit
) {
    SectionCard(
        emoji = "◎",
        title = "Réseaux sociaux",
        subtitle =
            "Accès Web via Deliriuum + Deep Shield"
    ) {
        SocialGridRow {
            SocialTile(
                name = "X",
                icon = "𝕏",
                color = Color.White,
                enabled = enabled
            ) {
                openSocial(
                    "X",
                    "https://x.com/"
                )
            }

            SocialTile(
                name = "TikTok",
                icon = "♪",
                color = Color(0xFFFF2D75),
                enabled = enabled
            ) {
                openSocial(
                    "TikTok",
                    "https://www.tiktok.com/"
                )
            }
        }

        SocialGridRow {
            SocialTile(
                name = "Facebook",
                icon = "f",
                color = Color(0xFF1877F2),
                enabled = enabled
            ) {
                openSocial(
                    "Facebook",
                    "https://www.facebook.com/"
                )
            }

            SocialTile(
                name = "Instagram",
                icon = "◎",
                color = Color(0xFFC13584),
                enabled = enabled
            ) {
                openSocial(
                    "Instagram",
                    "https://www.instagram.com/"
                )
            }
        }

        SocialGridRow {
            SocialTile(
                name = "YouTube",
                icon = "▶",
                color = Color(0xFFFF0033),
                enabled = enabled
            ) {
                openSocial(
                    "YouTube",
                    "https://www.youtube.com/"
                )
            }

            SocialTile(
                name = "Bluesky",
                icon = "🦋",
                color = Color(0xFF1185FE),
                enabled = enabled
            ) {
                openSocial(
                    "Bluesky",
                    "https://bsky.app/"
                )
            }
        }

        SocialGridRow {
            SocialTile(
                name = "LinkedIn",
                icon = "in",
                color = Color(0xFF0A66C2),
                enabled = enabled
            ) {
                openSocial(
                    "LinkedIn",
                    "https://www.linkedin.com/"
                )
            }
            SocialTile(
                name = "Telegram",
                icon = "➤",
                color = Color(0xFF229ED9),
                enabled = enabled
            ) {
                openSocial(
                    "Telegram",
                    "https://web.telegram.org/"
                )
            }
        }

    }
}


@Composable
private fun SocialGridRow(
    content:
    @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(10.dp),
        content = content
    )
}


@Composable
private fun RowScope.SocialTile(
    name: String,
    icon: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier =
            Modifier
                .weight(1f)
                .height(74.dp)
                .background(
                    if (enabled) {
                        Color.White.copy(alpha = 0.065f)
                    } else {
                        Color.White.copy(alpha = 0.025f)
                    },
                    RoundedCornerShape(18.dp)
                )
                .border(
                    1.dp,
                    if (enabled) {
                        color.copy(alpha = 0.30f)
                    } else {
                        Color.White.copy(alpha = 0.07f)
                    },
                    RoundedCornerShape(18.dp)
                )
                .clickable(
                    enabled = enabled
                ) {
                    onClick()
                }
                .padding(
                    horizontal = 12.dp,
                    vertical = 10.dp
                ),
        verticalArrangement =
            Arrangement.Center
    ) {
        Row(
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier =
                    Modifier
                        .size(34.dp)
                        .background(
                            color.copy(
                                alpha =
                                    if (enabled) {
                                        0.15f
                                    } else {
                                        0.06f
                                    }
                            ),
                            CircleShape
                        ),
                contentAlignment =
                    Alignment.Center
            ) {
                Text(
                    text =
                        if (enabled) {
                            icon
                        } else {
                            "🔒"
                        },
                    color =
                        if (enabled) {
                            color
                        } else {
                            Color.White.copy(alpha = 0.28f)
                        },
                    fontSize =
                        if (icon.length <= 1) {
                            17.sp
                        } else {
                            13.sp
                        },
                    fontWeight = FontWeight.Black
                )
            }

            Text(
                text = name,
                color =
                    if (enabled) {
                        Color.White
                    } else {
                        Color.White.copy(alpha = 0.30f)
                    },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}


// ================================================================
// SECTION CARD
// ================================================================

@Composable
private fun SectionCard(
    emoji: String,
    title: String,
    subtitle: String,
    content:
    @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    Color.White.copy(alpha = 0.07f),
                    RoundedCornerShape(24.dp)
                )
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.10f),
                    RoundedCornerShape(24.dp)
                )
                .padding(18.dp),
        verticalArrangement =
            Arrangement.spacedBy(13.dp)
    ) {
        Row(
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(11.dp)
        ) {
            Text(
                text = emoji,
                fontSize = 21.sp
            )

            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = subtitle,
                    color =
                        Color.White.copy(alpha = 0.48f),
                    fontSize = 11.sp
                )
            }
        }

        content()
    }
}


// ================================================================
// TEXT FIELD COLORS
// ================================================================

@Composable
private fun protectedTextFieldColors():
        TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        disabledTextColor =
            Color.White.copy(alpha = 0.32f),

        focusedBorderColor =
            Color.Cyan.copy(alpha = 0.75f),
        unfocusedBorderColor =
            Color.White.copy(alpha = 0.15f),
        disabledBorderColor =
            Color.White.copy(alpha = 0.08f),

        focusedContainerColor =
            Color.Black.copy(alpha = 0.20f),
        unfocusedContainerColor =
            Color.Black.copy(alpha = 0.20f),
        disabledContainerColor =
            Color.Black.copy(alpha = 0.10f),

        focusedPlaceholderColor =
            Color.White.copy(alpha = 0.38f),
        unfocusedPlaceholderColor =
            Color.White.copy(alpha = 0.38f),
        disabledPlaceholderColor =
            Color.White.copy(alpha = 0.20f),

        cursorColor = Color.Cyan
    )


// ================================================================
// DEEP SHIELD STATUS
// ================================================================

@Composable
private fun DeepShieldStatusCard() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    Color.White.copy(alpha = 0.07f),
                    RoundedCornerShape(24.dp)
                )
                .border(
                    1.dp,
                    Color.Cyan.copy(alpha = 0.18f),
                    RoundedCornerShape(24.dp)
                )
                .padding(18.dp),
        verticalArrangement =
            Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .background(
                            Color.Cyan.copy(alpha = 0.12f),
                            CircleShape
                        )
                        .border(
                            1.dp,
                            Color.Cyan.copy(alpha = 0.24f),
                            CircleShape
                        ),
                contentAlignment =
                    Alignment.Center
            ) {
                Text(
                    text = "🛡️",
                    fontSize = 22.sp
                )
            }

            Column {
                Text(
                    text =
                        "VPN et Deep Shield actifs",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text =
                        "Tunnel chiffré + navigateur protégé intégré",
                    color =
                        Color.Cyan.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text(
            text =
                "Votre navigation Deliriuum passe par le tunnel VPN WireGuard. " +
                        "Dans le navigateur intégré, Deep Shield réduit en plus plusieurs " +
                        "sources d'empreinte numérique et limite les fuites réseau.\n\n" +
                        "Cette protection concerne la navigation effectuée dans Deliriuum. " +
                        "Les applications natives installées séparément sur le téléphone " +
                        "suivent leurs propres règles.",
            color =
                Color.White.copy(alpha = 0.72f),
            fontSize = 13.sp,
            lineHeight = 20.sp
        )

    }
}

// ================================================================
// PRIVACY AUDIT CARD
// ================================================================

@Composable
private fun PrivacyAuditCard(
    auditState: PrivacyAuditState,
    tunnelManager: TunnelManager
) {
    val hasAuditResult =
        auditState.checks.isNotEmpty()

    val tunnelProtected =
        tunnelManager.isProtected

    val level =
        auditState.level

    val accent =
        if (!hasAuditResult) {
            Color.Cyan
        } else {
            when (level) {
                PrivacyProtectionLevel.HIGH ->
                    Color(0xFF42E695)

                PrivacyProtectionLevel.REINFORCED ->
                    Color(0xFFFFC633)

                PrivacyProtectionLevel.LOW ->
                    Color(0xFFFF5A6F)
            }
        }

    val levelLabel =
        if (!hasAuditResult) {
            "Analyse en attente"
        } else {
            when (level) {
                PrivacyProtectionLevel.HIGH ->
                    "Protection élevée"

                PrivacyProtectionLevel.REINFORCED ->
                    "Protection renforcée"

                PrivacyProtectionLevel.LOW ->
                    "Protection insuffisante"
            }
        }

    val levelEmoji =
        if (!hasAuditResult) {
            "…"
        } else {
            when (level) {
                PrivacyProtectionLevel.HIGH ->
                    "✓"

                PrivacyProtectionLevel.REINFORCED ->
                    "◐"

                PrivacyProtectionLevel.LOW ->
                    "!"
            }
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    Color.White.copy(alpha = 0.07f),
                    RoundedCornerShape(24.dp)
                )
                .border(
                    1.dp,
                    accent.copy(alpha = 0.30f),
                    RoundedCornerShape(24.dp)
                )
                .padding(18.dp),
        verticalArrangement =
            Arrangement.spacedBy(16.dp)
    ) {

        // ========================================================
        // HEADER
        // ========================================================

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            Box(
                modifier =
                    Modifier
                        .size(46.dp)
                        .background(
                            accent.copy(alpha = 0.14f),
                            CircleShape
                        )
                        .border(
                            1.dp,
                            accent.copy(alpha = 0.28f),
                            CircleShape
                        ),
                contentAlignment =
                    Alignment.Center
            ) {
                Text(
                    text = levelEmoji,
                    color = accent,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {
                Text(
                    text =
                        "État de confidentialité",
                    color =
                        Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = levelLabel,
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier =
                    Modifier
                        .background(
                            accent.copy(alpha = 0.12f),
                            RoundedCornerShape(50)
                        )
                        .border(
                            1.dp,
                            accent.copy(alpha = 0.25f),
                            RoundedCornerShape(50)
                        )
                        .padding(
                            horizontal = 10.dp,
                            vertical = 6.dp
                        )
            ) {
                Text(
                    text =
                        when {
                            !tunnelProtected -> "OFF"
                            !hasAuditResult -> "ATTENTE"
                            else -> "LIVE"
                        },
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }


        // ========================================================
        // INTRO
        // ========================================================

        Text(
            text =
                if (tunnelProtected) {
                    "Deep Shield vérifie directement plusieurs informations accessibles aux pages Web. Les résultats ci-dessous proviennent de l'environnement réellement observé dans Deliriuum."
                } else {
                    "Le tunnel Deliriuum n'est pas actuellement protégé. L'audit de confidentialité ne peut pas confirmer une protection complète."
                },
            color =
                Color.White.copy(alpha = 0.68f),
            fontSize = 12.sp,
            lineHeight = 18.sp
        )


        // ========================================================
        // COUNTERS
        // ========================================================

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            AuditCounter(
                value =
                    auditState.protectedCount.toString(),
                label = "Protégés",
                color = Color(0xFF42E695),
                modifier =
                    Modifier.weight(1f)
            )

            AuditCounter(
                value =
                    auditState.partialCount.toString(),
                label = "Partiels",
                color = Color(0xFFFFC633),
                modifier =
                    Modifier.weight(1f)
            )

            AuditCounter(
                value =
                    auditState.exposedCount.toString(),
                label = "Observables",
                color = Color(0xFFFF5A6F),
                modifier =
                    Modifier.weight(1f)
            )
        }


        // ========================================================
        // IMPORTANT CHECKS
        // ========================================================

        Column(
            modifier =
                Modifier.fillMaxWidth(),
            verticalArrangement =
                Arrangement.spacedBy(9.dp)
        ) {

            AuditCheckLine(
                title = "Tunnel WireGuard",
                status =
                    if (tunnelProtected) {
                        PrivacyCheckStatus.PROTECTED
                    } else {
                        PrivacyCheckStatus.EXPOSED
                    }
            )

            ImportantAuditCheck(
                auditState = auditState,
                id = "webrtc",
                fallbackTitle = "WebRTC"
            )

            ImportantAuditCheck(
                auditState = auditState,
                id = "webgl",
                fallbackTitle = "Identité graphique WebGL"
            )

            ImportantAuditCheck(
                auditState = auditState,
                id = "media_devices",
                fallbackTitle = "Micro et caméra"
            )

            ImportantAuditCheck(
                auditState = auditState,
                id = "timezone_consistency",
                fallbackTitle = "Cohérence du fuseau horaire"
            )
        }


        // ========================================================
        // LIMITS
        // ========================================================

        if (
            auditState.exposedCount > 0 ||
            auditState.partialCount > 0
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Color.White.copy(alpha = 0.045f),
                            RoundedCornerShape(16.dp)
                        )
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(13.dp)
            ) {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text =
                            "Transparence Deep Shield",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text =
                            buildString {
                                if (auditState.exposedCount > 0) {
                                    append(
                                        "${auditState.exposedCount} caractéristique"
                                    )

                                    if (
                                        auditState.exposedCount > 1
                                    ) {
                                        append("s")
                                    }

                                    append(
                                        " reste"
                                    )

                                    if (
                                        auditState.exposedCount > 1
                                    ) {
                                        append("nt")
                                    }

                                    append(" observable")

                                    if (
                                        auditState.exposedCount > 1
                                    ) {
                                        append("s")
                                    }

                                    append(". ")
                                }

                                if (auditState.partialCount > 0) {
                                    append(
                                        "${auditState.partialCount} protection"
                                    )

                                    if (
                                        auditState.partialCount > 1
                                    ) {
                                        append("s")
                                    }

                                    append(
                                        " ne peu"
                                    )

                                    if (
                                        auditState.partialCount > 1
                                    ) {
                                        append("vent")
                                    } else {
                                        append("t")
                                    }

                                    append(
                                        " être confirmée"
                                    )

                                    if (
                                        auditState.partialCount > 1
                                    ) {
                                        append("s")
                                    }

                                    append(
                                        " totalement par ce test."
                                    )
                                }
                            },
                        color =
                            Color.White.copy(alpha = 0.56f),
                        fontSize = 11.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }


        // ========================================================
        // FOOTER
        // ========================================================

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Text(
                text =
                    "Mesuré localement par Deep Shield",
                color =
                    Color.White.copy(alpha = 0.38f),
                fontSize = 10.sp
            )

            Spacer(
                modifier =
                    Modifier.weight(1f)
            )

            Text(
                text =
                    "Pas de score artificiel",
                color =
                    Color.Cyan.copy(alpha = 0.65f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}


// ================================================================
// AUDIT COUNTER
// ================================================================

@Composable
private fun AuditCounter(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier =
            modifier
                .background(
                    color.copy(alpha = 0.075f),
                    RoundedCornerShape(16.dp)
                )
                .border(
                    1.dp,
                    color.copy(alpha = 0.16f),
                    RoundedCornerShape(16.dp)
                )
                .padding(
                    vertical = 11.dp,
                    horizontal = 5.dp
                ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            color = color,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black
        )

        Text(
            text = label,
            color =
                Color.White.copy(alpha = 0.55f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}


// ================================================================
// IMPORTANT AUDIT CHECK
// ================================================================

@Composable
private fun ImportantAuditCheck(
    auditState: PrivacyAuditState,
    id: String,
    fallbackTitle: String
) {
    val check =
        auditState.checks.firstOrNull {
            it.id == id
        }

    AuditCheckLine(
        title =
            check?.title
                ?: fallbackTitle,
        status =
            check?.status
                ?: PrivacyCheckStatus.NOT_TESTED
    )
}


// ================================================================
// AUDIT CHECK LINE
// ================================================================

@Composable
private fun AuditCheckLine(
    title: String,
    status: PrivacyCheckStatus
) {
    val color =
        when (status) {
            PrivacyCheckStatus.PROTECTED ->
                Color(0xFF42E695)

            PrivacyCheckStatus.PARTIAL ->
                Color(0xFFFFC633)

            PrivacyCheckStatus.EXPOSED ->
                Color(0xFFFF5A6F)

            PrivacyCheckStatus.NOT_TESTED ->
                Color.White.copy(alpha = 0.38f)
        }

    val icon =
        when (status) {
            PrivacyCheckStatus.PROTECTED ->
                "✓"

            PrivacyCheckStatus.PARTIAL ->
                "◐"

            PrivacyCheckStatus.EXPOSED ->
                "!"

            PrivacyCheckStatus.NOT_TESTED ->
                "—"
        }

    val statusLabel =
        when (status) {
            PrivacyCheckStatus.PROTECTED ->
                "Protégé"

            PrivacyCheckStatus.PARTIAL ->
                "Partiel"

            PrivacyCheckStatus.EXPOSED ->
                "Observable"

            PrivacyCheckStatus.NOT_TESTED ->
                "Non testé"
        }

    Row(
        modifier =
            Modifier.fillMaxWidth(),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Box(
            modifier =
                Modifier
                    .size(25.dp)
                    .background(
                        color.copy(alpha = 0.12f),
                        CircleShape
                    ),
            contentAlignment =
                Alignment.Center
        ) {
            Text(
                text = icon,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
        }

        Spacer(
            modifier =
                Modifier.width(9.dp)
        )

        Text(
            text = title,
            color =
                Color.White.copy(alpha = 0.78f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier =
                Modifier.weight(1f)
        )

        Text(
            text = statusLabel,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ================================================================
// ICON BUTTON
// ================================================================

@Composable
private fun IconButtonWithBorder(
    iconEmoji: String,
    onClick: () -> Unit
) {
    Box(
        modifier =
            Modifier
                .size(36.dp)
                .background(
                    Color.White.copy(alpha = 0.08f),
                    CircleShape
                )
                .border(
                    1.dp,
                    Color.Cyan.copy(alpha = 0.3f),
                    CircleShape
                )
                .clickable {
                    onClick()
                },
        contentAlignment =
            Alignment.Center
    ) {
        Text(
            text = iconEmoji,
            color = Color.Cyan,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}


// ================================================================
// SHIELD BADGE
// ================================================================

@Composable
private fun RowScope.ShieldBadge(
    text: String
) {
    Box(
        modifier =
            Modifier
                .weight(1f)
                .background(
                    Color.Cyan.copy(alpha = 0.07f),
                    RoundedCornerShape(50)
                )
                .border(
                    1.dp,
                    Color.Cyan.copy(alpha = 0.16f),
                    RoundedCornerShape(50)
                )
                .padding(vertical = 8.dp),
        contentAlignment =
            Alignment.Center
    ) {
        Text(
            text = text,
            color =
                Color.Cyan.copy(alpha = 0.88f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}


// ================================================================
// SHEETS
// ================================================================

enum class HomeSheet {
    ACCOUNT,
    ABOUT,
    GUIDE,
    AUTH,
    FAQ
}