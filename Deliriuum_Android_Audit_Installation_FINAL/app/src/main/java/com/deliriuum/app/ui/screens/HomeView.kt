package com.deliriuum.app.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.deliriuum.app.R
import com.deliriuum.app.data.AuthManager
import com.deliriuum.app.data.HomePrivacyAuditContract
import com.deliriuum.app.data.TunnelManager
import com.deliriuum.app.data.TunnelStatus
import com.deliriuum.app.ui.components.SideMenuLayout
import com.deliriuum.app.ui.components.DeliriumGeckoView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.util.UUID
import kotlin.math.roundToInt
import com.deliriuum.app.data.PrivacyAuditManager
import com.deliriuum.app.data.PrivacyAuditState
import com.deliriuum.app.data.PrivacyCheckStatus


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

    /*
     * Comme sur iOS, la Home affiche UN audit courant.
     *
     * - tunnel OFF  -> audit réel AVANT Deliriuum
     * - tunnel ON   -> audit réel AVEC Deep Shield
     *
     * Le manager garde séparément la baseline brute afin de
     * conserver la référence avant activation, mais l'interface
     * ne présente pas deux colonnes artificielles.
     */
    val protectedReady =
        privacyAuditManager.protectedReady

    val currentAuditReady =
        privacyAuditManager.currentAuditReady

    /*
     * La mesure AVANT a déjà été effectuée pendant la finalisation
     * de l'installation et restaurée depuis le cache local.
     * Home ne lance jamais de baseline.
     */

    LaunchedEffect(privacyAuditState) {
        android.util.Log.d(
            "PrivacyAuditUI",
            "HOME RECOMPOSE AUDIT = $privacyAuditState"
        )
    }

    var isSideMenuOpen by remember { mutableStateOf(false) }
    var activeSheet by remember { mutableStateOf<HomeSheet?>(null) }
    var isToggling by remember { mutableStateOf(false) }

    // La feuille modale s'ouvre directement en état complètement déployé.
    // Sans cela, Material 3 peut choisir l'état PartiallyExpanded et
    // obliger l'utilisateur à tirer la vue vers le haut.
    val modalSheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        )

    // ============================================================
    // ESPACES / RACCOURCIS — PARITÉ iOS
    // ============================================================

    val shortcutStore = remember(context) {
        ShortcutSpaceStore(context)
    }

    var shortcutSpaces by remember {
        mutableStateOf(shortcutStore.load())
    }

    var editingSpace by remember {
        mutableStateOf<SpaceEditorState?>(null)
    }

    var editingShortcut by remember {
        mutableStateOf<ShortcutEditorState?>(null)
    }

    var pendingSpaceDeletion by remember {
        mutableStateOf<ShortcutSpace?>(null)
    }

    var pendingShortcutDeletion by remember {
        mutableStateOf<PendingShortcutDeletion?>(null)
    }

    LaunchedEffect(shortcutSpaces) {
        shortcutStore.save(shortcutSpaces)
    }

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

    // Espacement visuel proche d’iOS, mais adapté aux barres système Android.
    val homeTopInset =
        WindowInsets.statusBars
            .asPaddingValues()
            .calculateTopPadding()

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

                            /*
                             * IMPORTANT UX : l'audit ne bloque jamais
                             * l'activation. La baseline a été lancée en
                             * arrière-plan dès l'entrée sur Home.
                             */
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
                        .padding(
                            top = homeTopInset + 16.dp,
                            bottom = 32.dp
                        ),
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
                     * Le soutien ouvre toujours le navigateur Android classique.
                     * Même si la protection Deliriuum est active, on ne passe
                     * jamais par GeckoBrowserActivity pour cette page.
                     */
                    val intent =
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(
                                "https://deliriuum.com/soutenir.html"
                            )
                        )

                    context.startActivity(intent)
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

                /*
                 * Parité UX avec iOS : Deep Shield et l’audit sont visibles
                 * dès l’ouverture. Quand le tunnel est coupé, les cartes
                 * expliquent l’état OFF au lieu de disparaître.
                 *
                 * La logique et les caractéristiques Deep Shield restent
                 * celles d’Android (Gecko + protections/audit Android).
                 */
                DeepShieldStatusCard(
                    tunnelManager = tunnelManager
                )

                PrivacyAuditCard(
                    auditState =
                        privacyAuditState,
                    tunnelProtected =
                        tunnelManager.isProtected,
                    auditReady =
                        currentAuditReady
                )

                /*
                 * Comme PrivacyAuditBackgroundRunner sur iOS :
                 * dès que l'état passe en mode protégé, on recrée
                 * un runner invisible utilisant LE VRAI runtime
                 * Deep Shield Android.
                 */
                if (
                    tunnelManager.isProtected &&
                    !protectedReady
                ) {
                    key(
                        "privacy-audit-protected-" +
                                "${tunnelManager.status}-" +
                                "${tunnelManager.tunnelReachable}"
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(1.dp)
                                    .graphicsLayer {
                                        alpha = 0.001f
                                    }
                        ) {
                            DeliriumGeckoView(
                                url =
                                    HomePrivacyAuditContract
                                        .AUDIT_URL,
                                privateSession =
                                    true,
                                onLoadError = {
                                    android.util.Log.w(
                                        "PrivacyAuditUI",
                                        "Protected audit page failed to load: $it"
                                    )
                                },
                                modifier =
                                    Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                WebNavigationSpace(
                    enabled = protectedNavigationEnabled,
                    onOpen = { rawAddress ->
                        openInternalPage(
                            "Navigation",
                            buildTargetUrl(rawAddress)
                        )
                    }
                )

                ShortcutSpacesSection(
                    spaces = shortcutSpaces,
                    enabled = protectedNavigationEnabled,
                    onOpen = { item ->
                        openInternalPage(
                            "${item.name} via Deliriuum",
                            item.url
                        )
                    },
                    onCreateSpace = {
                        editingSpace = SpaceEditorState()
                    },
                    onEditSpace = { space ->
                        editingSpace =
                            SpaceEditorState(
                                spaceId = space.id,
                                name = space.name,
                                iconKey = space.iconKey
                            )
                    },
                    onDeleteSpace = { space ->
                        pendingSpaceDeletion = space
                    },
                    onAddShortcut = { space ->
                        editingShortcut =
                            ShortcutEditorState(
                                spaceId = space.id
                            )
                    },
                    onEditShortcut = { space, item ->
                        editingShortcut =
                            ShortcutEditorState(
                                spaceId = space.id,
                                shortcutId = item.id,
                                name = item.name,
                                url = item.url
                            )
                    },
                    onDeleteShortcut = { space, item ->
                        pendingShortcutDeletion =
                            PendingShortcutDeletion(
                                spaceId = space.id,
                                item = item
                            )
                    },
                    onMoveShortcut = { spaceId, itemId, targetIndex ->
                        shortcutSpaces =
                            moveShortcut(
                                spaces = shortcutSpaces,
                                spaceId = spaceId,
                                itemId = itemId,
                                targetIndex = targetIndex
                            )
                    }
                )
            }

            // ====================================================
            // ESPACES / RACCOURCIS — DIALOGUES
            // ====================================================

            editingSpace?.let { editor ->
                SpaceEditorDialog(
                    editor = editor,
                    onDismiss = {
                        editingSpace = null
                    },
                    onSave = { name, iconKey ->
                        val cleaned = name.trim()
                        if (cleaned.isNotEmpty()) {
                            shortcutSpaces =
                                if (editor.spaceId == null) {
                                    shortcutSpaces +
                                            ShortcutSpace(
                                                name = cleaned,
                                                iconKey = iconKey,
                                                kind = ShortcutSpaceKind.CUSTOM,
                                                shortcuts = emptyList()
                                            )
                                } else {
                                    shortcutSpaces.map { space ->
                                        if (space.id == editor.spaceId) {
                                            space.copy(
                                                name = cleaned,
                                                iconKey = iconKey,
                                                kind = ShortcutSpaceKind.CUSTOM
                                            )
                                        } else {
                                            space
                                        }
                                    }
                                }
                            editingSpace = null
                        }
                    }
                )
            }

            editingShortcut?.let { editor ->
                ShortcutEditorDialog(
                    editor = editor,
                    onDismiss = {
                        editingShortcut = null
                    },
                    onSave = { name, rawUrl ->
                        val cleanedName = name.trim()
                        val cleanedUrl = normalizeShortcutUrl(rawUrl)

                        if (
                            cleanedName.isNotEmpty() &&
                            cleanedUrl != null
                        ) {
                            shortcutSpaces =
                                shortcutSpaces.map { space ->
                                    if (space.id != editor.spaceId) {
                                        space
                                    } else if (editor.shortcutId == null) {
                                        space.copy(
                                            shortcuts =
                                                space.shortcuts +
                                                        ShortcutItem(
                                                            name = cleanedName,
                                                            url = cleanedUrl
                                                        )
                                        )
                                    } else {
                                        space.copy(
                                            shortcuts =
                                                space.shortcuts.map { item ->
                                                    if (item.id == editor.shortcutId) {
                                                        item.copy(
                                                            name = cleanedName,
                                                            url = cleanedUrl
                                                        )
                                                    } else {
                                                        item
                                                    }
                                                }
                                        )
                                    }
                                }
                            editingShortcut = null
                        }
                    }
                )
            }

            pendingSpaceDeletion?.let { space ->
                ConfirmDeleteDialog(
                    title = "Supprimer l'espace ?",
                    message =
                        "L'espace « ${space.name} » et ses raccourcis seront supprimés de cet appareil.",
                    onDismiss = {
                        pendingSpaceDeletion = null
                    },
                    onConfirm = {
                        shortcutSpaces =
                            shortcutSpaces.filterNot {
                                it.id == space.id
                            }
                        pendingSpaceDeletion = null
                    }
                )
            }

            pendingShortcutDeletion?.let { pending ->
                ConfirmDeleteDialog(
                    title = "Supprimer le raccourci ?",
                    message =
                        "« ${pending.item.name} » sera retiré de cet espace.",
                    onDismiss = {
                        pendingShortcutDeletion = null
                    },
                    onConfirm = {
                        shortcutSpaces =
                            shortcutSpaces.map { space ->
                                if (space.id == pending.spaceId) {
                                    space.copy(
                                        shortcuts =
                                            space.shortcuts.filterNot {
                                                it.id == pending.item.id
                                            }
                                    )
                                } else {
                                    space
                                }
                            }
                        pendingShortcutDeletion = null
                    }
                )
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
                    sheetState = modalSheetState,
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
                            /*
                             * L'authentification doit être immédiatement utilisable :
                             * la feuille est déjà Expanded et cette zone occupe presque
                             * toute la hauteur disponible. AuthScreen conserve son propre
                             * scroll pour les petits écrans et le mode inscription.
                             */
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
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
// ADRESSE OU RECHERCHE
// ================================================================

/**
 * Une saisie peut être une adresse ou une recherche.
 *
 * Sans ce tri, taper autre chose qu'un domaine ouvrait un navigateur
 * qui se refermait aussitôt, sans rien dire à l'utilisateur.
 */
private fun buildTargetUrl(raw: String): String {
    val cleaned = raw.trim()

    if (cleaned.isBlank()) {
        return "https://duckduckgo.com/"
    }

    if (
        cleaned.startsWith("https://", ignoreCase = true) ||
        cleaned.startsWith("http://", ignoreCase = true)
    ) {
        return cleaned
    }

    // Un domaine n'a pas d'espace et contient au moins un point.
    val looksLikeDomain =
        !cleaned.contains(" ") &&
                Regex("^[\\w-]+(\\.[\\w-]{2,})+(/.*)?$")
                    .matches(cleaned)

    return if (looksLikeDomain) {
        "https://$cleaned"
    } else {
        "https://duckduckgo.com/?q=" + Uri.encode(cleaned)
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
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bouton explicite : l'icône seule n'était pas assez claire.
        Box(
            modifier =
                Modifier
                    .height(38.dp)
                    .background(
                        Color.White.copy(alpha = 0.08f),
                        RoundedCornerShape(19.dp)
                    )
                    .border(
                        1.dp,
                        Color.Cyan.copy(alpha = 0.30f),
                        RoundedCornerShape(19.dp)
                    )
                    .clickable {
                        onMenuClick()
                    }
                    .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    text = "☰",
                    color = Color.Cyan,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Menu",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

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
// PROTECTION CARD — UX ALIGNÉE SUR iOS
// ================================================================

@Composable
private fun ProtectionCard(
    tunnelManager: TunnelManager,
    isToggling: Boolean,
    onToggle: () -> Unit
) {
    val protectedState =
        tunnelManager.isProtected

    val transitionalState =
        isToggling ||
                tunnelManager.status == TunnelStatus.CONNECTING ||
                tunnelManager.status == TunnelStatus.DISCONNECTING

    val statusLabel =
        when {
            tunnelManager.status == TunnelStatus.CONNECTED &&
                    !tunnelManager.tunnelReachable ->
                "Tunnel indisponible"

            tunnelManager.status == TunnelStatus.CONNECTED ->
                "Active"

            tunnelManager.status == TunnelStatus.CONNECTING ->
                "Activation…"

            tunnelManager.status == TunnelStatus.DISCONNECTING ->
                "Désactivation…"

            else ->
                "Inactive"
        }

    val statusColor =
        when {
            tunnelManager.status == TunnelStatus.CONNECTED &&
                    !tunnelManager.tunnelReachable ->
                Color(0xFFFFC633)

            protectedState ->
                Color(0xFF42E695)

            transitionalState ->
                Color(0xFF8E7CFF)

            else ->
                Color(0xFFFF5A6F)
        }

    val buttonGradient =
        when {
            protectedState ->
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF1FC5F5),
                        Color(0xFF42E695)
                    )
                )

            transitionalState ->
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF467AF5),
                        Color(0xFFC25BEA)
                    )
                )

            else ->
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFFE62E3D),
                        Color(0xFFFF4F3B)
                    )
                )
        }

    val actionLabel =
        when (tunnelManager.status) {
            TunnelStatus.CONNECTED ->
                "Désactiver la protection"

            TunnelStatus.CONNECTING ->
                "Connexion sécurisée…"

            TunnelStatus.DISCONNECTING ->
                "Désactivation…"

            else ->
                "Activer la protection"
        }

    val buttonStatusLabel =
        when {
            tunnelManager.status == TunnelStatus.CONNECTED &&
                    !tunnelManager.tunnelReachable ->
                "Tunnel indisponible"

            tunnelManager.status == TunnelStatus.CONNECTED ->
                "Protection active"

            tunnelManager.status == TunnelStatus.CONNECTING ->
                "Connexion au tunnel"

            tunnelManager.status == TunnelStatus.DISCONNECTING ->
                "Fermeture du tunnel"

            else ->
                "Protection inactive"
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
                    Color.White.copy(alpha = 0.10f),
                    RoundedCornerShape(24.dp)
                )
                .padding(18.dp),
        verticalArrangement =
            Arrangement.spacedBy(13.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "Protection Deliriuum",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.58f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(7.dp)
                                .background(
                                    statusColor,
                                    CircleShape
                                )
                    )

                    Text(
                        text = statusLabel,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Box(
                modifier =
                    Modifier
                        .size(42.dp)
                        .background(
                            Color.White.copy(alpha = 0.06f),
                            CircleShape
                        )
                        .border(
                            1.dp,
                            statusColor.copy(alpha = 0.22f),
                            CircleShape
                        ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (protectedState) "🛡" else "🔓",
                    fontSize = 20.sp
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(
                        buttonGradient,
                        RoundedCornerShape(20.dp)
                    )
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.16f),
                        RoundedCornerShape(20.dp)
                    )
                    .clickable(
                        enabled = !transitionalState
                    ) {
                        onToggle()
                    }
                    .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .background(
                                Color.White.copy(alpha = 0.16f),
                                CircleShape
                            ),
                    contentAlignment = Alignment.Center
                ) {
                    if (transitionalState) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(21.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = if (protectedState) "✓" else "→",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = actionLabel,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false
                    )

                    Text(
                        text = buttonStatusLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Box(
                    modifier =
                        Modifier
                            .size(32.dp)
                            .background(
                                Color.Black.copy(alpha = 0.12f),
                                CircleShape
                            ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (protectedState) "⏻" else "→",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Text(
            text =
                if (protectedState) {
                    "Votre trafic passe par le tunnel Deliriuum. La navigation intégrée peut utiliser Deep Shield."
                } else {
                    "Votre trafic n’est pas protégé par Deliriuum. Activez la protection pour ouvrir le navigateur sécurisé."
                },
            color =
                if (protectedState) {
                    Color(0xFF42E695)
                } else {
                    Color.White.copy(alpha = 0.58f)
                },
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 17.sp
        )

        tunnelManager.lastErrorMessage
            ?.let { error ->
                Text(
                    text = error,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = Color(0xFFFF5A6F)
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
        title = "Navigateur Deliriuum",
        subtitle =
            "Rechercher ou ouvrir un site avec le navigateur protégé"
    ) {
        OutlinedTextField(
            value = address,
            onValueChange = {
                address = it
            },
            enabled = enabled,
            placeholder = {
                Text("Rechercher ou saisir une adresse")
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
                "Une adresse s'ouvre directement, le reste est recherché sur DuckDuckGo.",
            color =
                Color.White.copy(alpha = 0.45f),
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
    }
}


// ================================================================
// ESPACES & RACCOURCIS — PORTAGE DU MODÈLE iOS
// ================================================================

private enum class ShortcutSpaceKind {
    SOCIAL,
    VIDEO,
    CUSTOM
}

private data class ShortcutItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String
)

private data class ShortcutSpace(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val iconKey: String,
    val kind: ShortcutSpaceKind,
    val shortcuts: List<ShortcutItem>
)

private data class SpaceEditorState(
    val spaceId: String? = null,
    val name: String = "",
    val iconKey: String = "sparkles"
)

private data class ShortcutEditorState(
    val spaceId: String,
    val shortcutId: String? = null,
    val name: String = "",
    val url: String = ""
)

private data class PendingShortcutDeletion(
    val spaceId: String,
    val item: ShortcutItem
)


private class ShortcutSpaceStore(
    context: android.content.Context
) {
    private val preferences =
        context.getSharedPreferences(
            "deliriuum_shortcut_spaces",
            android.content.Context.MODE_PRIVATE
        )

    private val storageKey =
        "deliriuum.shortcut.spaces.v1"

    fun load(): List<ShortcutSpace> {
        val raw = preferences.getString(storageKey, null)
            ?: return defaultShortcutSpaces()

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val objectValue = array.getJSONObject(i)
                    val shortcutArray =
                        objectValue.optJSONArray("shortcuts")
                            ?: JSONArray()

                    val shortcuts =
                        buildList {
                            for (j in 0 until shortcutArray.length()) {
                                val shortcut =
                                    shortcutArray.getJSONObject(j)
                                add(
                                    ShortcutItem(
                                        id =
                                            shortcut.optString(
                                                "id",
                                                UUID.randomUUID().toString()
                                            ),
                                        name = shortcut.optString("name"),
                                        url = shortcut.optString("url")
                                    )
                                )
                            }
                        }

                    val kind =
                        runCatching {
                            ShortcutSpaceKind.valueOf(
                                objectValue.optString(
                                    "kind",
                                    ShortcutSpaceKind.CUSTOM.name
                                )
                            )
                        }.getOrDefault(
                            ShortcutSpaceKind.CUSTOM
                        )

                    add(
                        ShortcutSpace(
                            id =
                                objectValue.optString(
                                    "id",
                                    UUID.randomUUID().toString()
                                ),
                            name = objectValue.optString("name"),
                            iconKey =
                                objectValue.optString(
                                    "iconKey",
                                    "sparkles"
                                ),
                            kind = kind,
                            shortcuts = shortcuts
                        )
                    )
                }
            }
        }.getOrElse {
            defaultShortcutSpaces()
        }
    }

    fun save(spaces: List<ShortcutSpace>) {
        val array = JSONArray()

        spaces.forEach { space ->
            val shortcutArray = JSONArray()
            space.shortcuts.forEach { shortcut ->
                shortcutArray.put(
                    JSONObject()
                        .put("id", shortcut.id)
                        .put("name", shortcut.name)
                        .put("url", shortcut.url)
                )
            }

            array.put(
                JSONObject()
                    .put("id", space.id)
                    .put("name", space.name)
                    .put("iconKey", space.iconKey)
                    .put("kind", space.kind.name)
                    .put("shortcuts", shortcutArray)
            )
        }

        preferences
            .edit()
            .putString(storageKey, array.toString())
            .apply()
    }
}

private fun defaultShortcutSpaces(): List<ShortcutSpace> =
    listOf(
        ShortcutSpace(
            name = "Réseaux sociaux",
            iconKey = "social",
            kind = ShortcutSpaceKind.SOCIAL,
            shortcuts =
                listOf(
                    ShortcutItem(
                        name = "X",
                        url = "https://x.com/"
                    ),
                    ShortcutItem(
                        name = "TikTok",
                        url = "https://www.tiktok.com/"
                    ),
                    ShortcutItem(
                        name = "Facebook",
                        url = "https://www.facebook.com/"
                    ),
                    ShortcutItem(
                        name = "Instagram",
                        url = "https://www.instagram.com/"
                    ),
                    ShortcutItem(
                        name = "Telegram",
                        url = "https://web.telegram.org/"
                    ),
                    ShortcutItem(
                        name = "LinkedIn",
                        url = "https://www.linkedin.com/"
                    )
                )
        ),
        ShortcutSpace(
            name = "Vidéo",
            iconKey = "video",
            kind = ShortcutSpaceKind.VIDEO,
            shortcuts =
                listOf(
                    ShortcutItem(
                        name = "YouTube",
                        url = "https://www.youtube.com/"
                    ),
                    ShortcutItem(
                        name = "Rumble",
                        url = "https://rumble.com/"
                    ),
                    ShortcutItem(
                        name = "Odysee",
                        url = "https://odysee.com/"
                    ),
                    ShortcutItem(
                        name = "Dailymotion",
                        url = "https://www.dailymotion.com/"
                    ),
                    ShortcutItem(
                        name = "Vimeo",
                        url = "https://vimeo.com/"
                    ),
                    ShortcutItem(
                        name = "Twitch",
                        url = "https://www.twitch.tv/"
                    )
                )
        )
    )

private fun moveShortcut(
    spaces: List<ShortcutSpace>,
    spaceId: String,
    itemId: String,
    targetIndex: Int
): List<ShortcutSpace> =
    spaces.map { space ->
        if (space.id != spaceId) {
            space
        } else {
            val currentIndex =
                space.shortcuts.indexOfFirst {
                    it.id == itemId
                }

            if (
                currentIndex < 0 ||
                targetIndex !in space.shortcuts.indices ||
                currentIndex == targetIndex
            ) {
                space
            } else {
                val mutable = space.shortcuts.toMutableList()
                val moved = mutable.removeAt(currentIndex)

                /*
                 * targetIndex représente la case visée dans la grille
                 * AVANT le retrait. L'insertion au même index donne
                 * le comportement attendu d'un vrai drag & drop :
                 * la tuile arrive exactement à l'emplacement relâché.
                 */
                val insertionIndex =
                    targetIndex.coerceIn(
                        0,
                        mutable.size
                    )

                mutable.add(
                    insertionIndex,
                    moved
                )

                space.copy(
                    shortcuts = mutable
                )
            }
        }
    }

private fun normalizeShortcutUrl(raw: String): String? {
    val cleaned = raw.trim()
    if (cleaned.isEmpty()) return null

    val candidate =
        if (
            cleaned.startsWith("https://", ignoreCase = true) ||
            cleaned.startsWith("http://", ignoreCase = true)
        ) {
            cleaned
        } else {
            "https://$cleaned"
        }

    val parsed = Uri.parse(candidate)
    return if (
        !parsed.scheme.isNullOrBlank() &&
        !parsed.host.isNullOrBlank()
    ) {
        candidate
    } else {
        null
    }
}

@Composable
private fun ShortcutSpacesSection(
    spaces: List<ShortcutSpace>,
    enabled: Boolean,
    onOpen: (ShortcutItem) -> Unit,
    onCreateSpace: () -> Unit,
    onEditSpace: (ShortcutSpace) -> Unit,
    onDeleteSpace: (ShortcutSpace) -> Unit,
    onAddShortcut: (ShortcutSpace) -> Unit,
    onEditShortcut: (ShortcutSpace, ShortcutItem) -> Unit,
    onDeleteShortcut: (ShortcutSpace, ShortcutItem) -> Unit,
    onMoveShortcut: (String, String, Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Vrai logo Deliriuum — même ressource que l'écran d'accueil.
            Box(
                modifier =
                    Modifier
                        .size(46.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color.Cyan.copy(alpha = 0.16f),
                                    Color(0xFF8A4DFF).copy(alpha = 0.18f)
                                )
                            ),
                            RoundedCornerShape(14.dp)
                        )
                        .border(
                            1.dp,
                            Color.Cyan.copy(alpha = 0.24f),
                            RoundedCornerShape(14.dp)
                        )
                        .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "Logo Deliriuum",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "Mes espaces",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Organise tes raccourcis comme tu veux",
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            TextButton(
                onClick = onCreateSpace,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = Color.Cyan
                    )
            ) {
                Text(
                    text = "+ Nouveau",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Indication gestuelle discrète et permanente.
        // On évite une phrase de tutoriel : l'interface indique simplement
        // le geste disponible et les actions qu'il révèle.
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Cyan.copy(alpha = 0.08f),
                                Color(0xFF8A4DFF).copy(alpha = 0.07f),
                                Color.White.copy(alpha = 0.035f)
                            )
                        ),
                        RoundedCornerShape(15.dp)
                    )
                    .border(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(
                                Color.Cyan.copy(alpha = 0.26f),
                                Color(0xFF8A4DFF).copy(alpha = 0.18f),
                                Color.White.copy(alpha = 0.08f)
                            )
                        ),
                        RoundedCornerShape(15.dp)
                    )
                    .padding(
                        horizontal = 12.dp,
                        vertical = 9.dp
                    )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier =
                        Modifier
                            .background(
                                Color.Cyan.copy(alpha = 0.12f),
                                RoundedCornerShape(9.dp)
                            )
                            .border(
                                1.dp,
                                Color.Cyan.copy(alpha = 0.22f),
                                RoundedCornerShape(9.dp)
                            )
                            .padding(
                                horizontal = 9.dp,
                                vertical = 5.dp
                            )
                ) {
                    Text(
                        text = "APPUI LONG",
                        color = Color.Cyan.copy(alpha = 0.92f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.7.sp,
                        maxLines = 1
                    )
                }

                Text(
                    text = "Déplacer  ·  Modifier  ·  Supprimer",
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "↕",
                    color = Color(0xFFB58CFF).copy(alpha = 0.85f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        spaces.forEach { space ->
            ShortcutSpaceCard(
                space = space,
                enabled = enabled,
                onOpen = onOpen,
                onEditSpace = {
                    onEditSpace(space)
                },
                onDeleteSpace = {
                    onDeleteSpace(space)
                },
                onAddShortcut = {
                    onAddShortcut(space)
                },
                onEditShortcut = { item ->
                    onEditShortcut(
                        space,
                        item
                    )
                },
                onDeleteShortcut = { item ->
                    onDeleteShortcut(
                        space,
                        item
                    )
                },
                onMoveShortcut = { itemId, targetIndex ->
                    onMoveShortcut(
                        space.id,
                        itemId,
                        targetIndex
                    )
                },
                onLongPressDiscovered = {
                    // L'aide reste volontairement visible.
                }
            )
        }
    }
}

@Composable
private fun ShortcutSpaceCard(
    space: ShortcutSpace,
    enabled: Boolean,
    onOpen: (ShortcutItem) -> Unit,
    onEditSpace: () -> Unit,
    onDeleteSpace: () -> Unit,
    onAddShortcut: () -> Unit,
    onEditShortcut: (ShortcutItem) -> Unit,
    onDeleteShortcut: (ShortcutItem) -> Unit,
    onMoveShortcut: (String, Int) -> Unit,
    onLongPressDiscovered: () -> Unit
) {
    var showSpaceMenu by remember(space.id) {
        mutableStateOf(false)
    }

    val subtitle =
        when (space.kind) {
            ShortcutSpaceKind.SOCIAL ->
                "Réseaux sociaux via Deliriuum + Deep Shield"

            ShortcutSpaceKind.VIDEO ->
                "Plateformes vidéo via Deliriuum + Deep Shield"

            ShortcutSpaceKind.CUSTOM ->
                "Espace personnalisé"
        }

    SectionCard(
        emoji = spaceIconGlyph(
            space.iconKey,
            space.kind
        ),
        title = space.name,
        subtitle = subtitle
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            /*
             * Les raccourcis restent totalement propres au repos.
             * Les raccourcis restent totalement propres au repos.
             * Le sous-titre de « Mes espaces » reprend la présentation iOS.
             */
            Spacer(
                modifier = Modifier.weight(1f)
            )

            Box {
                TextButton(
                    onClick = {
                        showSpaceMenu = true
                    },
                    contentPadding = PaddingValues(
                        horizontal = 10.dp,
                        vertical = 4.dp
                    )
                ) {
                    Text(
                        text = "•••",
                        color = Color.White.copy(alpha = 0.68f),
                        fontWeight = FontWeight.Bold
                    )
                }

                DropdownMenu(
                    expanded = showSpaceMenu,
                    onDismissRequest = {
                        showSpaceMenu = false
                    }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text("Renommer / modifier")
                        },
                        onClick = {
                            showSpaceMenu = false
                            onEditSpace()
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text("Supprimer")
                        },
                        onClick = {
                            showSpaceMenu = false
                            onDeleteSpace()
                        }
                    )
                }
            }
        }

        if (space.shortcuts.isEmpty()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    text = "+",
                    color = Color.Cyan.copy(alpha = 0.75f),
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Aucun raccourci dans cet espace",
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            ShortcutGrid(
                items = space.shortcuts,
                enabled = enabled,
                onOpen = onOpen,
                onEdit = onEditShortcut,
                onDelete = onDeleteShortcut,
                onMove = onMoveShortcut,
                onLongPressDiscovered = onLongPressDiscovered
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        Color.Cyan.copy(alpha = 0.08f),
                        RoundedCornerShape(14.dp)
                    )
                    .border(
                        1.dp,
                        Color.Cyan.copy(alpha = 0.25f),
                        RoundedCornerShape(14.dp)
                    )
                    .clickable {
                        onAddShortcut()
                    }
                    .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+ Ajouter un raccourci",
                color = Color.Cyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ShortcutGrid(
    items: List<ShortcutItem>,
    enabled: Boolean,
    onOpen: (ShortcutItem) -> Unit,
    onEdit: (ShortcutItem) -> Unit,
    onDelete: (ShortcutItem) -> Unit,
    onMove: (String, Int) -> Unit,
    onLongPressDiscovered: () -> Unit
) {
    var draggingId by remember {
        mutableStateOf<String?>(null)
    }

    /*
     * Le menu ⋮ n'est visible que pour le raccourci qui vient
     * d'être sélectionné par appui long.
     */
    var longPressSelectedId by remember {
        mutableStateOf<String?>(null)
    }

    var dragOffset by remember {
        mutableStateOf(Offset.Zero)
    }

    var dropIndex by remember {
        mutableStateOf(-1)
    }

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val spacingPx =
        with(density) {
            10.dp.toPx()
        }

    fun resetDrag() {
        draggingId = null
        dragOffset = Offset.Zero
        dropIndex = -1
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.chunked(3).forEachIndexed { rowIndex, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEachIndexed { columnIndex, item ->
                    val absoluteIndex =
                        rowIndex * 3 + columnIndex

                    val isDragging =
                        draggingId == item.id

                    val isDropTarget =
                        draggingId != null &&
                                !isDragging &&
                                dropIndex == absoluteIndex

                    ShortcutTile(
                        item = item,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        isDragging = isDragging,
                        isDropTarget = isDropTarget,
                        isLongPressSelected =
                            longPressSelectedId == item.id,
                        dragOffset =
                            if (isDragging) {
                                dragOffset
                            } else {
                                Offset.Zero
                            },
                        onOpen = {
                            if (longPressSelectedId != null) {
                                longPressSelectedId = null
                            } else if (
                                enabled &&
                                draggingId == null
                            ) {
                                onOpen(item)
                            }
                        },
                        onEdit = {
                            onEdit(item)
                        },
                        onDelete = {
                            onDelete(item)
                        },
                        onDragStart = {
                            haptic.performHapticFeedback(
                                HapticFeedbackType.LongPress
                            )

                            /*
                             * Un appui long sélectionne aussi la tuile :
                             * au relâchement son menu ⋮ devient visible.
                             */
                            longPressSelectedId = item.id
                            onLongPressDiscovered()

                            draggingId = item.id
                            dragOffset = Offset.Zero
                            dropIndex = absoluteIndex
                        },
                        onDrag = { dragAmount, tileWidth, tileHeight ->
                            if (draggingId == item.id) {
                                dragOffset += dragAmount

                                val cellWidth =
                                    tileWidth + spacingPx

                                val cellHeight =
                                    tileHeight + spacingPx

                                val startColumn =
                                    absoluteIndex % 3

                                val startRow =
                                    absoluteIndex / 3

                                val columnShift =
                                    (
                                            dragOffset.x /
                                                    cellWidth
                                            ).roundToInt()

                                val rowShift =
                                    (
                                            dragOffset.y /
                                                    cellHeight
                                            ).roundToInt()

                                val targetColumn =
                                    (
                                            startColumn +
                                                    columnShift
                                            ).coerceIn(
                                            0,
                                            2
                                        )

                                val maxRow =
                                    items.lastIndex / 3

                                val targetRow =
                                    (
                                            startRow +
                                                    rowShift
                                            ).coerceIn(
                                            0,
                                            maxRow
                                        )

                                dropIndex =
                                    (
                                            targetRow * 3 +
                                                    targetColumn
                                            ).coerceIn(
                                            0,
                                            items.lastIndex
                                        )
                            }
                        },
                        onDragEnd = {
                            val dragged =
                                draggingId

                            val target =
                                dropIndex

                            if (
                                dragged != null &&
                                target in items.indices &&
                                target != absoluteIndex
                            ) {
                                onMove(
                                    dragged,
                                    target
                                )
                            }

                            resetDrag()
                        },
                        onDragCancel = {
                            resetDrag()
                        }
                    )
                }

                repeat(
                    3 - rowItems.size
                ) {
                    Spacer(
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ShortcutTile(
    item: ShortcutItem,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    isDragging: Boolean,
    isDropTarget: Boolean,
    isLongPressSelected: Boolean,
    dragOffset: Offset,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (
        dragAmount: Offset,
        tileWidth: Float,
        tileHeight: Float
    ) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    var showShortcutMenu by remember(item.id) {
        mutableStateOf(false)
    }

    LaunchedEffect(isLongPressSelected) {
        if (!isLongPressSelected) {
            showShortcutMenu = false
        }
    }

    val shape =
        RoundedCornerShape(18.dp)

    Box(
        modifier =
            modifier
                .height(102.dp)
                .zIndex(
                    if (isDragging) {
                        20f
                    } else {
                        0f
                    }
                )
                .graphicsLayer {
                    translationX =
                        if (isDragging) {
                            dragOffset.x
                        } else {
                            0f
                        }

                    translationY =
                        if (isDragging) {
                            dragOffset.y
                        } else {
                            0f
                        }

                    scaleX =
                        if (isDragging) {
                            1.06f
                        } else {
                            1f
                        }

                    scaleY =
                        if (isDragging) {
                            1.06f
                        } else {
                            1f
                        }

                    shadowElevation =
                        if (isDragging) {
                            18f
                        } else {
                            0f
                        }

                    alpha =
                        if (isDragging) {
                            0.96f
                        } else {
                            1f
                        }
                }
                .background(
                    when {
                        isDragging ->
                            Color.Cyan.copy(
                                alpha = 0.13f
                            )

                        isDropTarget ->
                            Color.Cyan.copy(
                                alpha = 0.11f
                            )

                        enabled ->
                            Color.White.copy(
                                alpha = 0.09f
                            )

                        else ->
                            Color.White.copy(
                                alpha = 0.045f
                            )
                    },
                    shape
                )
                .border(
                    width =
                        if (
                            isDragging ||
                            isDropTarget
                        ) {
                            2.dp
                        } else {
                            1.dp
                        },
                    color =
                        when {
                            isDragging ->
                                Color.Cyan.copy(
                                    alpha = 0.90f
                                )

                            isDropTarget ->
                                Color.Cyan.copy(
                                    alpha = 0.60f
                                )

                            enabled ->
                                Color.White.copy(
                                    alpha = 0.18f
                                )

                            else ->
                                Color.White.copy(
                                    alpha = 0.09f
                                )
                        },
                    shape = shape
                )
                /*
                 * UX demandée :
                 * appui long -> soulèvement -> glisser -> relâcher.
                 * Aucun bouton / aucune flèche de déplacement.
                 */
                .pointerInput(
                    item.id
                ) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            onDragStart()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()

                            onDrag(
                                dragAmount,
                                size.width.toFloat(),
                                size.height.toFloat()
                            )
                        },
                        onDragEnd = {
                            onDragEnd()
                        },
                        onDragCancel = {
                            onDragCancel()
                        }
                    )
                }
                .clickable(
                    enabled =
                        enabled &&
                                !isDragging
                ) {
                    onOpen()
                }
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = 5.dp,
                        vertical = 8.dp
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            ShortcutBrandBadge(
                item = item,
                enabled = enabled
            )

            Text(
                text = item.name,
                color =
                    if (enabled) {
                        Color.White
                    } else {
                        Color.White.copy(
                            alpha = 0.34f
                        )
                    },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        /*
         * Aucun bouton parasite au repos.
         *
         * Après un appui long et uniquement après le relâchement,
         * le ⋮ apparaît sur la tuile sélectionnée. Il donne accès
         * à Modifier / Supprimer.
         */
        if (
            isLongPressSelected &&
            !isDragging
        ) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(30.dp)
                            .background(
                                Color.Black.copy(
                                    alpha = 0.82f
                                ),
                                CircleShape
                            )
                            .border(
                                1.dp,
                                Color.Cyan.copy(
                                    alpha = 0.30f
                                ),
                                CircleShape
                            )
                            .clickable {
                                showShortcutMenu = true
                            },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⋮",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                DropdownMenu(
                    expanded = showShortcutMenu,
                    onDismissRequest = {
                        showShortcutMenu = false
                    }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text("Modifier")
                        },
                        onClick = {
                            showShortcutMenu = false
                            onEdit()
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text("Supprimer")
                        },
                        onClick = {
                            showShortcutMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }

        if (isDragging) {
            Text(
                text = "DÉPLACER",
                color = Color.Black,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                modifier =
                    Modifier
                        .align(
                            Alignment.BottomCenter
                        )
                        .padding(
                            bottom = 5.dp
                        )
                        .background(
                            Color.Cyan,
                            RoundedCornerShape(
                                7.dp
                            )
                        )
                        .padding(
                            horizontal = 7.dp,
                            vertical = 3.dp
                        )
            )
        }
    }
}

private fun brandResourceName(name: String): String? =
    when (name.trim().lowercase()) {
        "x" -> "brand_x"
        "tiktok" -> "brand_tiktok"
        "facebook" -> "brand_facebook"
        "instagram" -> "brand_instagram"
        "telegram" -> "brand_telegram"
        "linkedin" -> "brand_linkedin"
        "youtube" -> "brand_youtube"
        "rumble" -> "brand_rumble"
        "odysee" -> "brand_odysee"
        "dailymotion" -> "brand_dailymotion"
        "vimeo" -> "brand_vimeo"
        "twitch" -> "brand_twitch"
        else -> null
    }

private fun brandNeedsBlackBackground(name: String): Boolean =
    name.trim().lowercase() in
            setOf("x", "dailymotion")

@Composable
private fun ShortcutBrandBadge(
    item: ShortcutItem,
    enabled: Boolean
) {
    val context = LocalContext.current
    val resourceName = brandResourceName(item.name)
    val resourceId =
        remember(resourceName) {
            if (resourceName == null) {
                0
            } else {
                context.resources.getIdentifier(
                    resourceName,
                    "drawable",
                    context.packageName
                )
            }
        }

    Box(
        modifier =
            Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (brandNeedsBlackBackground(item.name)) {
                        Color.Black
                    } else {
                        Color.White
                    }
                )
                .border(
                    1.dp,
                    Color.White.copy(
                        alpha = if (enabled) 0.16f else 0.06f
                    ),
                    RoundedCornerShape(16.dp)
                ),
        contentAlignment = Alignment.Center
    ) {
        val localBitmap =
            remember(resourceId) {
                if (resourceId == 0) {
                    null
                } else {
                    runCatching {
                        BitmapFactory.decodeResource(
                            context.resources,
                            resourceId
                        )
                    }.getOrNull()
                }
            }

        when {
            localBitmap != null -> {
                Image(
                    bitmap = localBitmap.asImageBitmap(),
                    contentDescription = item.name,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(
                                if (brandNeedsBlackBackground(item.name)) {
                                    8.dp
                                } else {
                                    7.dp
                                }
                            ),
                    contentScale = ContentScale.Fit,
                    alpha = if (enabled) 1f else 0.70f
                )
            }

            resourceName == null -> {
                RemoteShortcutFavicon(
                    item = item,
                    enabled = enabled
                )
            }

            else -> {
                ShortcutFallbackGlyph(
                    name = item.name,
                    enabled = enabled
                )
            }
        }

        /*
         * État verrouillé premium :
         * le logo reste lisible sous un ruban "verre fumé" diagonal,
         * avec une bordure cyan/violette très discrète.
         */
        if (!enabled) {
            Box(
                modifier =
                    Modifier
                        .width(78.dp)
                        .height(18.dp)
                        .rotate(-38f)
                        .graphicsLayer {
                            shadowElevation = 10f
                            shape = RoundedCornerShape(50)
                            clip = true
                        }
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF050508).copy(alpha = 0.94f),
                                    Color(0xFF17131F).copy(alpha = 0.96f),
                                    Color(0xFF07080D).copy(alpha = 0.94f)
                                )
                            ),
                            RoundedCornerShape(50)
                        )
                        .border(
                            0.8.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Cyan.copy(alpha = 0.34f),
                                    Color.White.copy(alpha = 0.14f),
                                    Color(0xFF9B6CFF).copy(alpha = 0.34f)
                                )
                            ),
                            RoundedCornerShape(50)
                        ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "OFF",
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun ShortcutFallbackGlyph(
    name: String,
    enabled: Boolean
) {
    val initial =
        name.trim()
            .take(1)
            .uppercase()
            .ifEmpty { "•" }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.Cyan.copy(
                                alpha = if (enabled) 0.92f else 0.25f
                            ),
                            Color(0xFF8A4DFF).copy(
                                alpha = if (enabled) 0.88f else 0.22f
                            )
                        )
                    )
                ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White.copy(
                alpha = if (enabled) 1f else 0.5f
            ),
            fontSize = 23.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun RemoteShortcutFavicon(
    item: ShortcutItem,
    enabled: Boolean
) {
    val faviconUrl =
        remember(item.url) {
            runCatching {
                val parsed = Uri.parse(item.url)
                val host = parsed.host
                    ?: return@runCatching null
                val scheme = parsed.scheme ?: "https"
                "$scheme://$host/favicon.ico"
            }.getOrNull()
        }

    val bitmap by
    produceState<android.graphics.Bitmap?>(
        initialValue = null,
        key1 = faviconUrl
    ) {
        value =
            if (faviconUrl == null) {
                null
            } else {
                withContext(Dispatchers.IO) {
                    runCatching {
                        URL(faviconUrl)
                            .openConnection()
                            .apply {
                                connectTimeout = 2500
                                readTimeout = 2500
                            }
                            .getInputStream()
                            .use(BitmapFactory::decodeStream)
                    }.getOrNull()
                }
            }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = item.name,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            contentScale = ContentScale.Fit,
            alpha = if (enabled) 1f else 0.70f
        )
    } else {
        ShortcutFallbackGlyph(
            name = item.name,
            enabled = enabled
        )
    }
}

private fun spaceIconGlyph(
    key: String,
    kind: ShortcutSpaceKind
): String =
    when (kind) {
        ShortcutSpaceKind.SOCIAL -> "◎"
        ShortcutSpaceKind.VIDEO -> "▶"
        ShortcutSpaceKind.CUSTOM ->
            when (key) {
                "social" -> "◎"
                "video" -> "▶"
                "music" -> "♫"
                "news" -> "▤"
                "work" -> "▣"
                "study" -> "▰"
                "travel" -> "✈"
                "shopping" -> "▱"
                "gaming" -> "✦"
                "reading" -> "▥"
                "photos" -> "▧"
                "web" -> "🌐"
                "favorites" -> "★"
                "personal" -> "●"
                "folder" -> "▰"
                else -> "✦"
            }
    }

@Composable
private fun SpaceEditorDialog(
    editor: SpaceEditorState,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember(editor.spaceId) {
        mutableStateOf(editor.name)
    }
    var iconKey by remember(editor.spaceId) {
        mutableStateOf(editor.iconKey)
    }

    val presets =
        listOf(
            "sparkles",
            "social",
            "video",
            "music",
            "news",
            "work",
            "study",
            "travel",
            "shopping",
            "gaming",
            "reading",
            "photos",
            "web",
            "favorites",
            "personal",
            "folder"
        )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF12121A),
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text =
                    if (editor.spaceId == null) {
                        "Créer un espace"
                    } else {
                        "Modifier l'espace"
                    },
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nom") },
                    singleLine = true,
                    colors = protectedTextFieldColors()
                )

                Text(
                    text = "Icône",
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                presets.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { preset ->
                            val selected = iconKey == preset
                            Box(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .background(
                                            if (selected) {
                                                Color.Cyan.copy(alpha = 0.18f)
                                            } else {
                                                Color.White.copy(alpha = 0.05f)
                                            },
                                            RoundedCornerShape(13.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (selected) {
                                                Color.Cyan.copy(alpha = 0.65f)
                                            } else {
                                                Color.White.copy(alpha = 0.08f)
                                            },
                                            RoundedCornerShape(13.dp)
                                        )
                                        .clickable {
                                            iconKey = preset
                                        },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text =
                                        spaceIconGlyph(
                                            preset,
                                            ShortcutSpaceKind.CUSTOM
                                        ),
                                    fontSize = 19.sp
                                )
                            }
                        }

                        repeat(4 - row.size) {
                            Spacer(
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(name, iconKey)
                },
                enabled = name.trim().isNotEmpty()
            ) {
                Text(
                    text =
                        if (editor.spaceId == null) {
                            "Créer"
                        } else {
                            "Enregistrer"
                        },
                    color = Color.Cyan,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Annuler",
                    color = Color.White.copy(alpha = 0.60f)
                )
            }
        }
    )
}

@Composable
private fun ShortcutEditorDialog(
    editor: ShortcutEditorState,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember(editor.shortcutId, editor.spaceId) {
        mutableStateOf(editor.name)
    }
    var url by remember(editor.shortcutId, editor.spaceId) {
        mutableStateOf(editor.url)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF12121A),
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text =
                    if (editor.shortcutId == null) {
                        "Ajouter un raccourci"
                    } else {
                        "Modifier le raccourci"
                    },
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nom") },
                    singleLine = true,
                    colors = protectedTextFieldColors()
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Adresse du site") },
                    placeholder = { Text("https://exemple.com") },
                    singleLine = true,
                    colors = protectedTextFieldColors(),
                    keyboardOptions =
                        KeyboardOptions(
                            imeAction = ImeAction.Done
                        )
                )

                Text(
                    text =
                        "Pour un site connu, Deliriuum utilise son logo local. Pour un raccourci personnalisé, le favicon du site est utilisé si disponible.",
                    color = Color.White.copy(alpha = 0.48f),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(name, url)
                },
                enabled =
                    name.trim().isNotEmpty() &&
                            url.trim().isNotEmpty()
            ) {
                Text(
                    text = "Enregistrer",
                    color = Color.Cyan,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Annuler",
                    color = Color.White.copy(alpha = 0.60f)
                )
            }
        }
    )
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF12121A),
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "Supprimer",
                    color = Color(0xFFFF5A6F),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Annuler",
                    color = Color.White.copy(alpha = 0.60f)
                )
            }
        }
    )
}


// ================================================================
// SECTION CARD — EN-TÊTE VISUEL ALIGNÉ SUR iOS
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
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Box(
                modifier =
                    Modifier
                        .size(38.dp)
                        .background(
                            Color.Cyan.copy(alpha = 0.10f),
                            RoundedCornerShape(10.dp)
                        )
                        .border(
                            1.dp,
                            Color.Cyan.copy(alpha = 0.16f),
                            RoundedCornerShape(10.dp)
                        ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emoji,
                    fontSize = 18.sp,
                    color = Color.Cyan,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 16.sp
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
// DEEP SHIELD STATUS — PARITÉ VISUELLE iOS
// ================================================================

@Composable
private fun DeepShieldStatusCard(
    tunnelManager: TunnelManager
) {
    val protectedState =
        tunnelManager.isProtected

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
                DeepShieldGlyph(
                    modifier =
                        Modifier.size(22.dp),
                    color =
                        Color.Cyan
                )
            }

            Column(
                modifier =
                    Modifier.weight(1f),
                verticalArrangement =
                    Arrangement.spacedBy(3.dp)
            ) {

                Text(
                    text = "Deep Shield",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                /*
                 * Visuellement identique à iOS.
                 * Le contenu reste spécifique à Android/GeckoView.
                 */
                Text(
                    text =
                        "Protection renforcée de la navigation",
                    color =
                        Color.Cyan.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(
            verticalArrangement =
                Arrangement.spacedBy(5.dp)
        ) {

            Text(
                text =
                    if (protectedState) {
                        "MODE PROTÉGÉ"
                    } else {
                        "MODE NON PROTÉGÉ"
                    },
                color =
                    if (protectedState) {
                        Color(0xFF42E695)
                    } else {
                        Color(0xFFFF5A6F)
                    },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text =
                    if (protectedState) {
                        "Le tunnel WireGuard est actif et le navigateur GeckoView " +
                                "applique les protections Deep Shield Android contre " +
                                "plusieurs sources d’empreinte et de fuite réseau."
                    } else {
                        "Deep Shield n’est pas encore appliqué. Deliriuum mesure " +
                                "automatiquement cet environnement avant activation " +
                                "afin de disposer d’une référence réelle."
                    },
                color =
                    Color.White.copy(alpha = 0.68f),
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
    }
}


@Composable
private fun DeepShieldGlyph(
    modifier: Modifier = Modifier,
    color: Color
) {
    Canvas(
        modifier = modifier
    ) {
        val w =
            size.width

        val h =
            size.height

        val shield =
            Path().apply {

                moveTo(
                    w * 0.50f,
                    h * 0.06f
                )

                lineTo(
                    w * 0.84f,
                    h * 0.20f
                )

                lineTo(
                    w * 0.79f,
                    h * 0.62f
                )

                quadraticBezierTo(
                    w * 0.73f,
                    h * 0.82f,
                    w * 0.50f,
                    h * 0.94f
                )

                quadraticBezierTo(
                    w * 0.27f,
                    h * 0.82f,
                    w * 0.21f,
                    h * 0.62f
                )

                lineTo(
                    w * 0.16f,
                    h * 0.20f
                )

                close()
            }

        drawPath(
            path = shield,
            color = color,
            style =
                Stroke(
                    width =
                        size.minDimension * 0.09f
                )
        )

        drawLine(
            color = color,
            start =
                Offset(
                    w * 0.50f,
                    h * 0.15f
                ),
            end =
                Offset(
                    w * 0.50f,
                    h * 0.84f
                ),
            strokeWidth =
                size.minDimension * 0.07f
        )
    }
}


// ================================================================
// PRIVACY AUDIT CARD — PARITÉ VISUELLE iOS
// ================================================================

@Composable
private fun PrivacyAuditCard(
    auditState: PrivacyAuditState,
    tunnelProtected: Boolean,
    auditReady: Boolean
) {
    var showAllAuditDetails by remember {
        mutableStateOf(false)
    }

    val accent =
        if (!auditReady) {

            Color.Cyan

        } else {

            when (auditState.level) {

                com.deliriuum.app.data.PrivacyProtectionLevel.HIGH ->
                    Color(0xFF42E695)

                com.deliriuum.app.data.PrivacyProtectionLevel.REINFORCED ->
                    Color(0xFFFFC633)

                com.deliriuum.app.data.PrivacyProtectionLevel.LOW ->
                    Color(0xFFFF5A6F)
            }
        }

    val levelLabel =
        if (!auditReady) {

            "Analyse en attente"

        } else {

            when (auditState.level) {

                com.deliriuum.app.data.PrivacyProtectionLevel.HIGH ->
                    "Protection élevée"

                com.deliriuum.app.data.PrivacyProtectionLevel.REINFORCED ->
                    "Protection renforcée"

                com.deliriuum.app.data.PrivacyProtectionLevel.LOW ->
                    "Protection faible"
            }
        }

    val levelEmoji =
        if (!auditReady) {

            "…"

        } else {

            when (auditState.level) {

                com.deliriuum.app.data.PrivacyProtectionLevel.HIGH ->
                    "✓"

                com.deliriuum.app.data.PrivacyProtectionLevel.REINFORCED ->
                    "◐"

                com.deliriuum.app.data.PrivacyProtectionLevel.LOW ->
                    "!"
            }
        }

    val liveLabel =
        when {

            !tunnelProtected ->
                "OFF"

            !auditReady ->
                "ATTENTE"

            else ->
                "LIVE"
        }

    val transparencyText =
        buildString {

            if (
                auditState.exposedCount > 0
            ) {

                append(
                    "${auditState.exposedCount} élément"
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

                append(
                    " observable"
                )

                if (
                    auditState.exposedCount > 1
                ) {
                    append("s")
                }

                append(". ")
            }

            if (
                auditState.partialCount > 0
            ) {

                append(
                    "${auditState.partialCount} protection"
                )

                if (
                    auditState.partialCount > 1
                ) {
                    append("s")
                }

                append(
                    if (
                        auditState.partialCount > 1
                    ) {
                        " sont partielles."
                    } else {
                        " est partielle."
                    }
                )
            }
        }
            .trim()

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

        // --------------------------------------------------------
        // Header — même structure que l'iOS
        // --------------------------------------------------------

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
                    Modifier.weight(1f),
                verticalArrangement =
                    Arrangement.spacedBy(2.dp)
            ) {

                Text(
                    text = "État de confidentialité",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color =
                        Color.White.copy(alpha = 0.62f)
                )

                Text(
                    text = levelLabel,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
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
                    text = liveLabel,
                    color = accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // --------------------------------------------------------
        // Intro
        // --------------------------------------------------------

        Text(
            text =
                if (tunnelProtected) {
                    if (auditReady) {
                        "Deep Shield mesure ce que les sites peuvent réellement " +
                                "observer avec la protection Deliriuum active."
                    } else {
                        "Le tunnel est protégé. Deep Shield mesure maintenant " +
                                "l’environnement réellement exposé aux sites."
                    }
                } else {
                    if (auditReady) {
                        "Mesure réalisée avant activation de Deliriuum : " +
                                "voici ce qu’un site peut observer dans l’environnement standard."
                    } else {
                        "Deliriuum mesure automatiquement l’environnement avant " +
                                "d’activer la protection. Aucune action n’est demandée."
                    }
                },
            fontSize = 12.sp,
            color =
                Color.White.copy(alpha = 0.62f),
            lineHeight = 18.sp
        )

        // --------------------------------------------------------
        // Counters — mêmes 3 cases que l'iOS
        // --------------------------------------------------------

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            AuditCounter(
                value =
                    if (auditReady) {
                        auditState.protectedCount.toString()
                    } else {
                        "—"
                    },
                label = "Protégés",
                color = Color(0xFF42E695),
                modifier =
                    Modifier.weight(1f)
            )

            AuditCounter(
                value =
                    if (auditReady) {
                        auditState.partialCount.toString()
                    } else {
                        "—"
                    },
                label = "Partiels",
                color = Color(0xFFFFC633),
                modifier =
                    Modifier.weight(1f)
            )

            AuditCounter(
                value =
                    if (auditReady) {
                        auditState.exposedCount.toString()
                    } else {
                        "—"
                    },
                label = "Observables",
                color = Color(0xFFFF5A6F),
                modifier =
                    Modifier.weight(1f)
            )
        }

        // --------------------------------------------------------
        // Full audit details — même bouton que l'iOS
        // --------------------------------------------------------

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        Color.White.copy(alpha = 0.045f),
                        RoundedCornerShape(15.dp)
                    )
                    .border(
                        1.dp,
                        Color.Cyan.copy(alpha = 0.15f),
                        RoundedCornerShape(15.dp)
                    )
                    .clickable {
                        showAllAuditDetails =
                            !showAllAuditDetails
                    }
                    .padding(
                        horizontal = 13.dp,
                        vertical = 11.dp
                    ),
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {

            Box(
                modifier =
                    Modifier
                        .size(30.dp)
                        .background(
                            Color.Cyan.copy(alpha = 0.12f),
                            CircleShape
                        ),
                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    text =
                        if (showAllAuditDetails) {
                            "⌃"
                        } else {
                            "≡"
                        },
                    color = Color.Cyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier =
                    Modifier.weight(1f),
                verticalArrangement =
                    Arrangement.spacedBy(2.dp)
            ) {

                Text(
                    text =
                        if (showAllAuditDetails) {
                            "Masquer le détail des protections"
                        } else {
                            "Voir le détail des protections"
                        },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text =
                        "Comprendre ce que chaque contrôle mesure",
                    fontSize = 12.sp,
                    color =
                        Color.White.copy(alpha = 0.62f)
                )
            }

            Text(
                text =
                    if (showAllAuditDetails) {
                        "⌃"
                    } else {
                        "⌄"
                    },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color =
                    Color.White.copy(alpha = 0.62f)
            )
        }

        if (
            showAllAuditDetails
        ) {

            Column(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalArrangement =
                    Arrangement.spacedBy(9.dp)
            ) {

                auditState.checks
                    .forEach { check ->

                        FullAuditDetailRowAndroid(
                            title =
                                check.title,
                            detail =
                                check.detail,
                            status =
                                check.status
                        )
                    }
            }
        }

        // --------------------------------------------------------
        // Transparency
        // --------------------------------------------------------

        if (
            auditReady &&
            (
                auditState.exposedCount > 0 ||
                auditState.partialCount > 0
            )
        ) {

            Column(
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
                        .padding(13.dp),
                verticalArrangement =
                    Arrangement.spacedBy(5.dp)
            ) {

                Text(
                    text = "Transparence Deep Shield",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = transparencyText,
                    fontSize = 12.sp,
                    color =
                        Color.White.copy(alpha = 0.62f),
                    lineHeight = 17.sp
                )
            }
        }

        // --------------------------------------------------------
        // Footer
        // --------------------------------------------------------

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(
                text = "Mesuré localement par Deep Shield",
                fontSize = 12.sp,
                color =
                    Color.White.copy(alpha = 0.55f)
            )

            Spacer(
                modifier =
                    Modifier.weight(1f)
            )

            Text(
                text = "Pas de score artificiel",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color =
                    Color.Cyan.copy(alpha = 0.65f)
            )
        }
    }
}


@Composable
private fun FullAuditDetailRowAndroid(
    title: String,
    detail: String,
    status: PrivacyCheckStatus
) {
    val color =
        auditStatusColor(
            status
        )

    val icon =
        when (status) {

            PrivacyCheckStatus.PROTECTED ->
                "✓"

            PrivacyCheckStatus.PARTIAL ->
                "◐"

            PrivacyCheckStatus.EXPOSED ->
                "!"

            PrivacyCheckStatus.NOT_TESTED ->
                "…"
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
            Modifier
                .fillMaxWidth()
                .background(
                    color.copy(alpha = 0.055f),
                    RoundedCornerShape(15.dp)
                )
                .border(
                    1.dp,
                    color.copy(alpha = 0.14f),
                    RoundedCornerShape(15.dp)
                )
                .padding(12.dp),
        verticalAlignment =
            Alignment.Top,
        horizontalArrangement =
            Arrangement.spacedBy(11.dp)
    ) {

        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .background(
                        color.copy(alpha = 0.13f),
                        CircleShape
                    ),
            contentAlignment =
                Alignment.Center
        ) {

            Text(
                text = icon,
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black
            )
        }

        Column(
            modifier =
                Modifier.weight(1f),
            verticalArrangement =
                Arrangement.spacedBy(5.dp)
        ) {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    text = title,
                    modifier =
                        Modifier.weight(1f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color =
                        Color.White.copy(alpha = 0.78f)
                )

                Box(
                    modifier =
                        Modifier
                            .background(
                                color.copy(alpha = 0.10f),
                                RoundedCornerShape(50)
                            )
                            .padding(
                                horizontal = 8.dp,
                                vertical = 4.dp
                            )
                ) {

                    Text(
                        text = statusLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = color
                    )
                }
            }

            Text(
                text = detail,
                fontSize = 12.sp,
                color =
                    Color.White.copy(alpha = 0.62f),
                lineHeight = 17.sp
            )
        }
    }
}


private fun auditStatusColor(
    status: PrivacyCheckStatus
): Color =
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
    enabled: Boolean = true,
    id: String,
    fallbackTitle: String
) {
    val check =
        if (enabled) {
            auditState.checks.firstOrNull {
                it.id == id
            }
        } else {
            null
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
