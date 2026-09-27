package com.deliriuum.app.ui.components

import android.content.Intent
import android.util.Log
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.deliriuum.app.data.DeliriumGeckoRuntime
import com.deliriuum.app.data.GeckoPopupHolder
import com.deliriuum.app.ui.screens.GeckoPopupActivity
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebRequestError
import com.deliriuum.app.data.PrivacyAuditManager

/**
 * État de navigation exposé à l'interface Compose du navigateur.
 *
 * DeliriumGeckoView reste responsable de la GeckoSession ; l'Activity
 * n'a donc pas besoin de manipuler directement GeckoView. Elle dispose
 * seulement de commandes sûres et d'un état observable.
 */
class GeckoBrowserNavigationState {

    var canGoBack by mutableStateOf(false)
        private set

    var canGoForward by mutableStateOf(false)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var currentUrl by mutableStateOf<String?>(null)
        private set

    var currentTitle by mutableStateOf("")
        private set

    private var session: GeckoSession? = null

    fun goBack() {
        if (canGoBack) {
            session?.goBack()
        }
    }

    fun goForward() {
        if (canGoForward) {
            session?.goForward()
        }
    }

    fun reload() {
        session?.reload()
    }

    fun stop() {
        session?.stop()
    }

    internal fun attach(
        session: GeckoSession,
        initialUrl: String
    ) {
        this.session = session
        canGoBack = false
        canGoForward = false
        isLoading = false
        currentUrl = initialUrl
        currentTitle = ""
    }

    internal fun updateCanGoBack(value: Boolean) {
        canGoBack = value
    }

    internal fun updateCanGoForward(value: Boolean) {
        canGoForward = value
    }

    internal fun updateLoading(value: Boolean) {
        isLoading = value
    }

    internal fun updateUrl(value: String?) {
        if (!value.isNullOrBlank()) {
            currentUrl = value
        }
    }

    internal fun updateTitle(value: String?) {
        currentTitle = value?.trim().orEmpty()
    }
}

@Composable
fun DeliriumGeckoView(
    url: String,
    modifier: Modifier = Modifier,
    privateSession: Boolean = false,
    navigationState: GeckoBrowserNavigationState? = null,

    /*
     * Appelé lorsque Gecko renonce à charger la page.
     *
     * Sans ce signal, un site injoignable laissait une page
     * blanche : l'utilisateur ne pouvait pas distinguer un site
     * hors service d'une faute de frappe ou d'une panne réseau.
     */
    onLoadError: (Int) -> Unit = {}
) {

    /*
     * La factory ne s'exécute qu'une fois, alors que la lambda
     * peut changer à chaque recomposition. rememberUpdatedState
     * garantit que la vue appelle toujours la version courante.
     */
    val currentOnLoadError =
        rememberUpdatedState(
            onLoadError
        )

    val currentNavigationState =
        rememberUpdatedState(
            navigationState
        )


    AndroidView(
        modifier =
            modifier,

        factory = {
                context ->


            // ====================================================
            // RUNTIME
            // ====================================================

            val runtime =
                DeliriumGeckoRuntime
                    .get(
                        context
                    )


            // ====================================================
            // SESSION SETTINGS
            // ====================================================

            val settings =
                GeckoSessionSettings
                    .Builder()

                    .usePrivateMode(
                        privateSession
                    )

                    .userAgentMode(
                        GeckoSessionSettings
                            .USER_AGENT_MODE_MOBILE
                    )

                    .build()


            // ====================================================
            // SESSION
            // ====================================================

            val session =
                GeckoSession(
                    settings
                )


            currentNavigationState
                .value
                ?.attach(
                    session = session,
                    initialUrl = url
                )


            // ====================================================
            // PRIVACY AUDIT MESSAGE DELEGATE
            // ====================================================

            val messageDelegate =
                object :
                    WebExtension
                    .MessageDelegate {


                    override fun onMessage(
                        nativeApp: String,
                        message: Any,
                        sender:
                        WebExtension
                        .MessageSender
                    ): GeckoResult<Any>? {


                        Log.d(
                            "PrivacyAudit",
                            "Message reçu nativeApp=$nativeApp"
                        )


                        if (
                            nativeApp !=
                            DeliriumGeckoRuntime
                                .NATIVE_APP
                        ) {

                            return null
                        }


                        val json =
                            when (
                                message
                            ) {

                                is JSONObject ->
                                    message

                                is Map<*, *> -> {

                                    try {

                                        JSONObject(
                                            message
                                        )

                                    } catch (
                                        e: Exception
                                    ) {

                                        Log.e(
                                            "PrivacyAudit",
                                            "Impossible de convertir le message Map en JSON",
                                            e
                                        )

                                        return null
                                    }
                                }

                                else -> {

                                    Log.w(
                                        "PrivacyAudit",
                                        "Message inattendu : " +
                                                message
                                                    .javaClass
                                                    .name
                                    )

                                    return null
                                }
                            }


                        val type =
                            json.optString(
                                "type"
                            )


                        if (
                            type !=
                            "privacy_audit"
                        ) {

                            Log.d(
                                "PrivacyAudit",
                                "Message ignoré type=$type"
                            )

                            return null
                        }


                        val payload =
                            json
                                .optJSONObject(
                                    "payload"
                                )


                        if (
                            payload == null
                        ) {

                            Log.w(
                                "PrivacyAudit",
                                "privacy_audit sans payload"
                            )

                            return null
                        }


                        Log.d(
                            "PrivacyAudit",
                            "AUDIT RECEIVED = " +
                                    payload
                                        .toString()
                        )

                        PrivacyAuditManager
                            .shared
                            .updateFromProbe(
                                payload
                            )


                        return null
                    }
                }


            // ====================================================
            // NAVIGATION DELEGATE
            // ====================================================

            val navigationDelegate =
                object :
                    GeckoSession
                    .NavigationDelegate {


                    override fun onCanGoBack(
                        session: GeckoSession,
                        canGoBack: Boolean
                    ) {
                        currentNavigationState
                            .value
                            ?.updateCanGoBack(
                                canGoBack
                            )
                    }


                    override fun onCanGoForward(
                        session: GeckoSession,
                        canGoForward: Boolean
                    ) {
                        currentNavigationState
                            .value
                            ?.updateCanGoForward(
                                canGoForward
                            )
                    }


                    override fun onNewSession(
                        session:
                        GeckoSession,
                        uri:
                        String
                    ): GeckoResult<GeckoSession> {


                        /*
                         * Gecko exige une session neuve
                         * et non ouverte.
                         */
                        val popupSession =
                            GeckoSession(
                                settings
                            )


                        /*
                         * On conserve la session pour
                         * GeckoPopupActivity.
                         */
                        GeckoPopupHolder
                            .session =
                            popupSession


                        /*
                         * Lancement de l'Activity dédiée
                         * à la popup.
                         */
                        val intent =
                            Intent(
                                context,
                                GeckoPopupActivity::class.java
                            ).apply {

                                addFlags(
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                                )
                            }


                        context.startActivity(
                            intent
                        )


                        /*
                         * IMPORTANT :
                         *
                         * NE PAS faire :
                         *
                         * popupSession.open(runtime)
                         *
                         * Gecko ouvre lui-même la session.
                         */
                        return GeckoResult
                            .fromValue(
                                popupSession
                            )
                    }


                    /*
                     * Échec de chargement.
                     *
                     * Renvoyer null laisse Gecko afficher sa propre
                     * page d'erreur, illisible et hors charte. On
                     * renvoie donc une page vide et on remonte le
                     * code à l'interface, qui affiche un écran
                     * compréhensible avec Réessayer et Rechercher.
                     */
                    override fun onLoadError(
                        session:
                        GeckoSession,

                        uri:
                        String?,

                        error:
                        WebRequestError
                    ): GeckoResult<String>? {


                        Log.w(
                            "DeliriumGecko",
                            "Chargement échoué uri=$uri " +
                                    "category=${error.category} " +
                                    "code=${error.code}"
                        )


                        currentNavigationState
                            .value
                            ?.updateLoading(
                                false
                            )


                        currentOnLoadError
                            .value
                            .invoke(
                                error.code
                            )


                        return GeckoResult
                            .fromValue(
                                "about:blank"
                            )
                    }
                }


            // ====================================================
            // PROGRESS + PAGE METADATA
            // ====================================================

            val progressDelegate =
                object :
                    GeckoSession
                    .ProgressDelegate {

                    override fun onPageStart(
                        session: GeckoSession,
                        url: String
                    ) {
                        currentNavigationState
                            .value
                            ?.updateUrl(
                                url
                            )

                        currentNavigationState
                            .value
                            ?.updateLoading(
                                true
                            )
                    }

                    override fun onPageStop(
                        session: GeckoSession,
                        success: Boolean
                    ) {
                        currentNavigationState
                            .value
                            ?.updateLoading(
                                false
                            )
                    }
                }


            val contentDelegate =
                object :
                    GeckoSession
                    .ContentDelegate {

                    override fun onTitleChange(
                        session: GeckoSession,
                        title: String?
                    ) {
                        currentNavigationState
                            .value
                            ?.updateTitle(
                                title
                            )
                    }
                }


            session
                .navigationDelegate =
                navigationDelegate

            session
                .progressDelegate =
                progressDelegate

            session
                .contentDelegate =
                contentDelegate


            // ====================================================
            // OPEN SESSION
            // ====================================================

            session.open(
                runtime
            )


            // ====================================================
            // REGISTER MESSAGE DELEGATE
            // ====================================================

            /*
             * ensureBuiltIn() est asynchrone.
             *
             * On attend donc explicitement que l'extension
             * Deep Shield soit prête avant d'attacher
             * le MessageDelegate à la GeckoSession.
             */
            DeliriumGeckoRuntime
                .whenExtensionReady {
                        extension ->


                    try {

                        session
                            .webExtensionController
                            .setMessageDelegate(
                                extension,
                                messageDelegate,
                                DeliriumGeckoRuntime
                                    .NATIVE_APP
                            )


                        Log.d(
                            "PrivacyAudit",
                            "MessageDelegate registered for " +
                                    extension.id
                        )

                    } catch (
                        e: Exception
                    ) {

                        Log.e(
                            "PrivacyAudit",
                            "MessageDelegate registration failed",
                            e
                        )
                    }
                }


            // ====================================================
            // GECKOVIEW
            // ====================================================

            GeckoView(
                context
            ).apply {

                layoutParams =
                    ViewGroup
                        .LayoutParams(
                            ViewGroup
                                .LayoutParams
                                .MATCH_PARENT,

                            ViewGroup
                                .LayoutParams
                                .MATCH_PARENT
                        )


                setSession(
                    session
                )


                tag =
                    session


                session
                    .loadUri(
                        url
                    )
            }
        }
    )
}
