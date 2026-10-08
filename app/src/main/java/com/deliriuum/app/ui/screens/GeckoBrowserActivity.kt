package com.deliriuum.app.ui.screens

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deliriuum.app.data.TunnelManager
import com.deliriuum.app.ui.components.DeliriumGeckoView
import com.deliriuum.app.ui.components.GeckoBrowserNavigationState
import com.deliriuum.app.ui.components.VpnLostDialog
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.WebRequestError
import java.util.UUID
import com.deliriuum.app.R
import com.deliriuum.app.i18n.AppStrings


class GeckoBrowserActivity : ComponentActivity() {

    override fun attachBaseContext(
        newBase: Context
    ) {
        super.attachBaseContext(
            AppStrings.localizedContext(
                newBase
            )
        )
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        val url =
            intent.getStringExtra("url")
                ?: "https://example.com"

        setContent {
            GeckoBrowserScreen(
                url = url,
                onClose = {
                    finish()
                }
            )
        }
    }
}


@Composable
private fun GeckoBrowserScreen(
    url: String,
    onClose: () -> Unit
) {
    val _i18nContext = LocalContext.current

    val context =
        LocalContext.current

    val tunnelManager =
        TunnelManager.shared

    val scope =
        rememberCoroutineScope()

    val navigationState =
        remember {
            GeckoBrowserNavigationState()
        }

    var reconnecting by remember {
        mutableStateOf(false)
    }

    /*
     * URL demandée par l'Activity.
     *
     * Elle ne suit PAS chaque navigation interne : sinon le changement
     * d'URL détruirait/recréerait la GeckoView et ferait perdre l'historique.
     */
    var requestedUrl by remember {
        mutableStateOf(url)
    }

    var loadError by remember {
        mutableStateOf<Int?>(null)
    }

    var reloadKey by remember {
        mutableStateOf(0)
    }

    var addMenuExpanded by remember {
        mutableStateOf(false)
    }

    var browserShortcutSaved by remember {
        mutableStateOf(false)
    }

    var browserSpaces by remember {
        mutableStateOf(
            loadBrowserShortcutSpaces(
                context
            )
        )
    }

    val protected =
        tunnelManager.isProtected

    val visibleUrl =
        navigationState.currentUrl
            ?.takeIf {
                it.startsWith("http://") ||
                        it.startsWith("https://")
            }
            ?: requestedUrl

    val canAddCurrentPage =
        protected &&
                loadError == null &&
                (
                        visibleUrl.startsWith("http://") ||
                                visibleUrl.startsWith("https://")
                        )

    /*
     * Comme sur iOS, "Ajouté" ne concerne que la page courante.
     * Dès que l'utilisateur navigue ailleurs, le bouton redevient Ajouter.
     */
    LaunchedEffect(
        navigationState.currentUrl
    ) {
        browserShortcutSaved = false
    }


    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
    ) {
        Column(
            modifier =
                Modifier.fillMaxSize()
        ) {

            // ====================================================
            // BARRE NAVIGATEUR
            // ====================================================

            BrowserTopBar(
                navigationState = navigationState,
                enabled = protected && loadError == null,
                canAddCurrentPage = canAddCurrentPage,
                shortcutSaved = browserShortcutSaved,
                spaces = browserSpaces,
                addMenuExpanded = addMenuExpanded,

                onToggleAddMenu = {
                    if (canAddCurrentPage) {
                        browserSpaces =
                            loadBrowserShortcutSpaces(
                                context
                            )
                        addMenuExpanded =
                            !addMenuExpanded
                    }
                },

                onDismissAddMenu = {
                    addMenuExpanded = false
                },

                onAddToSpace = { space ->
                    val saved =
                        addCurrentPageToSpace(
                            context = context,
                            spaceId = space.id,
                            pageUrl = visibleUrl,
                            pageTitle = navigationState.currentTitle
                        )

                    browserShortcutSaved = saved
                    addMenuExpanded = false
                },

                onClose = onClose
            )


            // ====================================================
            // BROWSER
            // ====================================================

            if (!protected) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color.Black),

                    contentAlignment =
                        Alignment.Center
                ) {
                    Text(
                        text = _i18nContext.getString(R.string.browser_navigation_suspendue),
                        color = Color.White.copy(alpha = 0.45f),
                        fontWeight = FontWeight.Bold
                    )
                }

            } else {
                val error = loadError

                if (error != null) {
                    LoadErrorScreen(
                        url = visibleUrl,
                        errorCode = error,

                        onRetry = {
                            loadError = null
                            reloadKey++
                        },

                        onSearch = {
                            requestedUrl =
                                searchUrlFor(
                                    visibleUrl
                                )
                            loadError = null
                            reloadKey++
                        },

                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                    )

                } else {
                    /*
                     * requestedUrl ne change que lorsqu'une nouvelle URL est
                     * imposée par l'interface (premier chargement / recherche
                     * après erreur). Les clics dans les pages utilisent donc
                     * le véritable historique de la même GeckoSession.
                     */
                    key(
                        reloadKey,
                        requestedUrl
                    ) {
                        DeliriumGeckoView(
                            url = requestedUrl,
                            navigationState = navigationState,

                            onLoadError = { code ->
                                loadError = code
                            },

                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                        )
                    }
                }
            }
        }


        // ========================================================
        // VPN LOST
        // ========================================================

        if (!protected) {
            VpnLostDialog(
                isReconnecting = reconnecting,

                onReconnect = {
                    if (!reconnecting) {
                        scope.launch {
                            reconnecting = true

                            try {
                                tunnelManager.connect()

                                loadError = null
                                reloadKey++

                            } catch (_: Exception) {
                            } finally {
                                reconnecting = false
                            }
                        }
                    }
                },

                onClose = {
                    onClose()
                }
            )
        }
    }
}


// ================================================================
// BARRE SUPÉRIEURE DU NAVIGATEUR
// ================================================================

@Composable
private fun BrowserTopBar(
    navigationState: GeckoBrowserNavigationState,
    enabled: Boolean,
    canAddCurrentPage: Boolean,
    shortcutSaved: Boolean,
    spaces: List<BrowserShortcutSpace>,
    addMenuExpanded: Boolean,
    onToggleAddMenu: () -> Unit,
    onDismissAddMenu: () -> Unit,
    onAddToSpace: (BrowserShortcutSpace) -> Unit,
    onClose: () -> Unit
) {
    val _i18nContext = LocalContext.current

    val accent =
        Color.Cyan

    val disabledColor =
        Color.White.copy(alpha = 0.22f)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(90.dp)
                .background(Color.Black)
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = 42.dp
                ),

        verticalAlignment =
            Alignment.CenterVertically,

        horizontalArrangement =
            Arrangement.spacedBy(5.dp)
    ) {

        BrowserToolbarIcon(
            text = "‹",
            enabled =
                enabled &&
                        navigationState.canGoBack,
            accent = accent,
            disabledColor = disabledColor,
            onClick = {
                navigationState.goBack()
            }
        )

        BrowserToolbarIcon(
            text = "›",
            enabled =
                enabled &&
                        navigationState.canGoForward,
            accent = accent,
            disabledColor = disabledColor,
            onClick = {
                navigationState.goForward()
            }
        )

        BrowserToolbarIcon(
            text =
                if (
                    navigationState.isLoading
                ) {
                    "×"
                } else {
                    "↻"
                },
            enabled = enabled,
            accent = accent,
            disabledColor = disabledColor,
            onClick = {
                if (
                    navigationState.isLoading
                ) {
                    navigationState.stop()
                } else {
                    navigationState.reload()
                }
            }
        )

        Spacer(
            modifier =
                Modifier.weight(1f)
        )

        Box {
            Row(
                modifier =
                    Modifier
                        .height(34.dp)
                        .background(
                            Color.White.copy(alpha = 0.06f),
                            RoundedCornerShape(17.dp)
                        )
                        .border(
                            1.dp,
                            (
                                    if (shortcutSaved) {
                                        Color(0xFF42E695)
                                    } else {
                                        accent
                                    }
                                    ).copy(alpha = 0.35f),
                            RoundedCornerShape(17.dp)
                        )
                        .clickable(
                            enabled =
                                canAddCurrentPage &&
                                        !shortcutSaved
                        ) {
                            onToggleAddMenu()
                        }
                        .padding(
                            horizontal = 11.dp
                        ),

                verticalAlignment =
                    Alignment.CenterVertically,

                horizontalArrangement =
                    Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text =
                        if (shortcutSaved) {
                            "✓"
                        } else {
                            "+"
                        },
                    color =
                        when {
                            shortcutSaved ->
                                Color(0xFF42E695)

                            canAddCurrentPage ->
                                accent

                            else ->
                                disabledColor
                        },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black
                )

                Text(
                    text =
                        if (shortcutSaved) {
                            _i18nContext.getString(R.string.browser_ajoute)
                        } else {
                            _i18nContext.getString(R.string.browser_ajouter)
                        },
                    color =
                        when {
                            shortcutSaved ->
                                Color(0xFF42E695)

                            canAddCurrentPage ->
                                accent

                            else ->
                                disabledColor
                        },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            DropdownMenu(
                expanded =
                    addMenuExpanded &&
                            canAddCurrentPage &&
                            !shortcutSaved,
                onDismissRequest =
                    onDismissAddMenu
            ) {
                spaces.forEach { space ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = space.name,
                                    fontWeight =
                                        FontWeight.SemiBold
                                )

                                Text(
                                    text =
                                        when (
                                            space.kind
                                                .uppercase()
                                        ) {
                                            "SOCIAL" ->
                                                _i18nContext.getString(R.string.shortcut_space_social)

                                            "VIDEO" ->
                                                _i18nContext.getString(R.string.shortcut_space_video)

                                            else ->
                                                _i18nContext.getString(R.string.browser_espace_personnalise)
                                        },
                                    color =
                                        Color.White.copy(
                                            alpha = 0.52f
                                        ),
                                    fontSize = 10.sp
                                )
                            }
                        },
                        onClick = {
                            onAddToSpace(
                                space
                            )
                        }
                    )
                }

                if (spaces.isEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text =
                                    _i18nContext.getString(R.string.browser_aucun_espace_disponible)
                            )
                        },
                        enabled = false,
                        onClick = {}
                    )
                }
            }
        }

        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .background(
                        Color.White.copy(alpha = 0.08f),
                        CircleShape
                    )
                    .border(
                        1.dp,
                        accent.copy(alpha = 0.30f),
                        CircleShape
                    )
                    .clickable {
                        onClose()
                    },

            contentAlignment =
                Alignment.Center
        ) {
            Text(
                text = "✕",
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}


@Composable
private fun BrowserToolbarIcon(
    text: String,
    enabled: Boolean,
    accent: Color,
    disabledColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier =
            Modifier
                .size(34.dp)
                .background(
                    Color.White.copy(alpha = 0.055f),
                    CircleShape
                )
                .clickable(
                    enabled = enabled
                ) {
                    onClick()
                },

        contentAlignment =
            Alignment.Center
    ) {
        Text(
            text = text,
            color =
                if (enabled) {
                    accent
                } else {
                    disabledColor
                },
            fontSize =
                if (
                    text == "↻"
                ) {
                    18.sp
                } else {
                    22.sp
                },
            fontWeight =
                FontWeight.SemiBold
        )
    }
}


// ================================================================
// STOCKAGE RACCOURCIS — MÊME FORMAT QUE HOMEVIEW
// ================================================================

private const val SHORTCUT_PREFS_NAME =
    "deliriuum_shortcut_spaces"

private const val SHORTCUT_STORAGE_KEY =
    "deliriuum.shortcut.spaces.v1"


private data class BrowserShortcutSpace(
    val id: String,
    val name: String,
    val kind: String
)


private fun loadBrowserShortcutSpaces(
    context: Context
): List<BrowserShortcutSpace> {
    val preferences =
        context.getSharedPreferences(
            SHORTCUT_PREFS_NAME,
            Context.MODE_PRIVATE
        )

    val raw =
        preferences.getString(
            SHORTCUT_STORAGE_KEY,
            null
        )
            ?: return emptyList()

    return runCatching {
        val array =
            JSONArray(
                raw
            )

        buildList {
            for (
            index in
            0 until array.length()
            ) {
                val space =
                    array.getJSONObject(
                        index
                    )

                add(
                    BrowserShortcutSpace(
                        id =
                            space.optString(
                                "id"
                            ),
                        name =
                            space.optString(
                                "name",
                                "Espace"
                            ),
                        kind =
                            space.optString(
                                "kind",
                                "CUSTOM"
                            )
                    )
                )
            }
        }
            .filter {
                it.id.isNotBlank()
            }

    }.getOrElse {
        emptyList()
    }
}


private fun addCurrentPageToSpace(
    context: Context,
    spaceId: String,
    pageUrl: String,
    pageTitle: String
): Boolean {
    if (
        !pageUrl.startsWith(
            "http://"
        ) &&
        !pageUrl.startsWith(
            "https://"
        )
    ) {
        return false
    }

    val preferences =
        context.getSharedPreferences(
            SHORTCUT_PREFS_NAME,
            Context.MODE_PRIVATE
        )

    val raw =
        preferences.getString(
            SHORTCUT_STORAGE_KEY,
            null
        )
            ?: return false

    return runCatching {
        val array =
            JSONArray(
                raw
            )

        var found =
            false

        for (
        index in
        0 until array.length()
        ) {
            val space =
                array.getJSONObject(
                    index
                )

            if (
                space.optString(
                    "id"
                ) != spaceId
            ) {
                continue
            }

            val shortcuts =
                space.optJSONArray(
                    "shortcuts"
                )
                    ?: JSONArray()
                        .also {
                            space.put(
                                "shortcuts",
                                it
                            )
                        }

            shortcuts.put(
                JSONObject()
                    .put(
                        "id",
                        UUID
                            .randomUUID()
                            .toString()
                    )
                    .put(
                        "name",
                        browserShortcutDisplayName(
                            url = pageUrl,
                            pageTitle = pageTitle
                        )
                    )
                    .put(
                        "url",
                        pageUrl
                    )
            )

            found =
                true

            break
        }

        if (
            found
        ) {
            preferences
                .edit()
                .putString(
                    SHORTCUT_STORAGE_KEY,
                    array.toString()
                )
                .apply()
        }

        found

    }.getOrDefault(
        false
    )
}


/*
 * Même logique de nommage que la version Swift :
 * marques connues -> nom canonique ; sinon titre court ; sinon domaine.
 */
private fun browserShortcutDisplayName(
    url: String,
    pageTitle: String
): String {
    val host =
        runCatching {
            Uri.parse(
                url
            )
                .host
                .orEmpty()
                .lowercase()
                .removePrefix(
                    "www."
                )
        }
            .getOrDefault(
                ""
            )

    when {
        host == "x.com" ||
                host.endsWith(
                    ".x.com"
                ) ||
                host == "twitter.com" ||
                host.endsWith(
                    ".twitter.com"
                ) ->
            return "X"

        host.contains(
            "facebook."
        ) ->
            return "Facebook"

        host.contains(
            "instagram."
        ) ->
            return "Instagram"

        host.contains(
            "youtube."
        ) ||
                host == "youtu.be" ->
            return "YouTube"

        host.contains(
            "tiktok."
        ) ->
            return "TikTok"

        host.contains(
            "linkedin."
        ) ->
            return "LinkedIn"

        host.contains(
            "telegram."
        ) ||
                host == "t.me" ->
            return "Telegram"

        host.contains(
            "spotify."
        ) ->
            return "Spotify"

        host.contains(
            "rumble."
        ) ->
            return "Rumble"

        host.contains(
            "odysee."
        ) ->
            return "Odysee"
    }

    val cleanedTitle =
        pageTitle
            .substringBefore(
                " | "
            )
            .substringBefore(
                " – "
            )
            .trim()

    if (
        cleanedTitle.isNotEmpty() &&
        cleanedTitle.length <= 28
    ) {
        return cleanedTitle
    }

    val firstHostComponent =
        host
            .substringBefore(
                "."
            )
            .ifBlank {
                "Site"
            }

    return firstHostComponent
        .replaceFirstChar {
            if (
                it.isLowerCase()
            ) {
                it.titlecase()
            } else {
                it.toString()
            }
        }
}


// ================================================================
// ÉCRAN D'ERREUR
// ================================================================

@Composable
private fun LoadErrorScreen(
    url: String,
    errorCode: Int,
    onRetry: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val _i18nContext = LocalContext.current


    val host =
        runCatching { Uri.parse(url).host }
            .getOrNull()
            ?: url


    val (heading, explanation) =
        messageFor(errorCode, host)


    Column(
        modifier =
            modifier
                .background(Color.Black)
                .padding(horizontal = 30.dp),

        horizontalAlignment =
            Alignment.CenterHorizontally,

        verticalArrangement =
            Arrangement.Center
    ) {

        Box(
            modifier =
                Modifier
                    .size(66.dp)
                    .background(
                        Color(0xFFFFC633).copy(alpha = 0.12f),
                        CircleShape
                    )
                    .border(
                        1.dp,
                        Color(0xFFFFC633).copy(alpha = 0.28f),
                        CircleShape
                    ),

            contentAlignment =
                Alignment.Center
        ) {

            Text(
                text = "⚠",
                color = Color(0xFFFFC633),
                fontSize = 30.sp
            )
        }


        Spacer(Modifier.height(22.dp))


        Text(
            text = heading,
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )


        Spacer(Modifier.height(10.dp))


        Text(
            text = explanation,
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 14.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center
        )


        Spacer(Modifier.height(28.dp))


        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        Color.Cyan,
                        RoundedCornerShape(16.dp)
                    )
                    .clickable { onRetry() }
                    .padding(vertical = 14.dp),

            contentAlignment =
                Alignment.Center
        ) {

            Text(
                text = _i18nContext.getString(R.string.browser_reessayer),
                color = Color.Black,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }


        Spacer(Modifier.height(11.dp))


        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .border(
                        1.5.dp,
                        Color.Cyan.copy(alpha = 0.5f),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable { onSearch() }
                    .padding(vertical = 14.dp),

            contentAlignment =
                Alignment.Center
        ) {

            Text(
                text = _i18nContext.getString(R.string.browser_rechercher_sur_duckduckgo),
                color = Color.Cyan,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}


/**
 * Traduit un code GeckoView en phrase compréhensible.
 *
 * Un utilisateur n'a rien à faire d'un code numérique : il a
 * besoin de savoir si le problème vient du site, de sa connexion,
 * ou d'une faute de frappe.
 */
private fun messageFor(
    code: Int,
    host: String
): Pair<String, String> =
    when (code) {

        WebRequestError.ERROR_UNKNOWN_HOST ->
            AppStrings.get(
                R.string.browser_site_introuvable
            ) to
                    AppStrings.get(
                        R.string.browser_l_adresse_n_existe_pas_ou_comporte_une_faute_de_frappe,
                        host
                    )

        WebRequestError.ERROR_CONNECTION_REFUSED ->
            AppStrings.get(
                R.string.browser_connexion_refusee
            ) to
                    AppStrings.get(
                        R.string.browser_a_refuse_la_connexion_le_site_est_peut_etre_hors_service,
                        host
                    )

        WebRequestError.ERROR_NET_TIMEOUT ->
            AppStrings.get(
                R.string.browser_pas_de_reponse
            ) to
                    AppStrings.get(
                        R.string.browser_met_trop_de_temps_a_repondre_la_connexion_est_peut_etre,
                        host
                    )

        WebRequestError.ERROR_NET_INTERRUPT ->
            AppStrings.get(
                R.string.browser_connexion_interrompue
            ) to
                    AppStrings.get(
                        R.string.browser_le_chargement_de_a_ete_interrompu_avant_la_fin,
                        host
                    )

        WebRequestError.ERROR_OFFLINE ->
            AppStrings.get(
                R.string.browser_aucune_connexion
            ) to
                    AppStrings.get(
                        R.string.browser_ton_appareil_semble_hors_ligne_verifie_ton_wifi_ou_tes_d
                    )

        WebRequestError.ERROR_MALFORMED_URI,
        WebRequestError.ERROR_UNKNOWN_PROTOCOL ->
            AppStrings.get(
                R.string.browser_adresse_incorrecte
            ) to
                    AppStrings.get(
                        R.string.browser_cette_adresse_n_est_pas_valide_tu_peux_lancer_une_recher
                    )

        WebRequestError.ERROR_SECURITY_SSL,
        WebRequestError.ERROR_SECURITY_BAD_CERT ->
            AppStrings.get(
                R.string.browser_connexion_non_securisee
            ) to
                    AppStrings.get(
                        R.string.browser_le_certificat_de_pose_probleme_par_precaution_la_page_n,
                        host
                    )

        else ->
            AppStrings.get(
                R.string.browser_page_non_affichee
            ) to
                    AppStrings.get(
                        R.string.browser_n_a_pas_pu_etre_charge,
                        host
                    )
    }


/** Bascule une adresse échouée vers une recherche. */
private fun searchUrlFor(url: String): String {

    val term =
        runCatching { Uri.parse(url).host }
            .getOrNull()
            ?: url

    return "https://duckduckgo.com/?q=" + Uri.encode(term)
}
