package com.deliriuum.app

import android.app.Activity
import android.content.Context
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.deliriuum.app.data.AuthManager
import com.deliriuum.app.data.KeychainStore
import com.deliriuum.app.data.PrivacyAuditCache
import com.deliriuum.app.data.PrivacyAuditManager
import com.deliriuum.app.data.TunnelManager
import com.deliriuum.app.ui.screens.DeliriuumLaunchScreen
import com.deliriuum.app.ui.screens.HomeView
import com.deliriuum.app.ui.screens.PrivacyAuditBootstrapScreen
import com.deliriuum.app.ui.screens.SupportIntroScreen
import com.deliriuum.app.ui.screens.VpnConsentDialog
import com.deliriuum.app.ui.screens.WelcomeScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    enum class AppScreen {
        WELCOME,
        SUPPORT,
        HOME
    }

    companion object {

        private const val MINUTE = 60_000L

        private const val AUTO_DISCONNECT_TIMEOUT_MS =
            30 * MINUTE

        private const val PREFS_NAME =
            "deliriuum_preferences"

        private const val KEY_VPN_CONSENT_VERSION =
            "vpn_consent_version"

        /*
         * Modifier cette valeur permet de forcer
         * une nouvelle demande de consentement.
         */
        private const val CURRENT_VPN_CONSENT_VERSION =
            2

        private const val LAUNCH_SCREEN_DURATION_MS =
            700L
    }

    private var backgroundAt: Long? = null

    private val lifecycleScopeLite =
        CoroutineScope(
            SupervisorJob() +
                    Dispatchers.Main
        )

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        // ========================================================
        // INITIALISATION
        // ========================================================

        KeychainStore.initialize(
            applicationContext
        )

        TunnelManager.initialize(
            this
        )

        val authManager =
            AuthManager.shared

        authManager.initialize(
            applicationContext
        )

        val tunnelManager =
            TunnelManager.shared

        /*
         * Le cache de l'audit est initialisé AVANT l'interface.
         *
         * - AVANT : mesuré une seule fois pendant l'installation
         * - APRÈS : mesuré une seule fois lors de la première protection
         *
         * Les deux snapshots survivent ensuite aux redémarrages de l'app.
         */
        PrivacyAuditCache.initialize(
            applicationContext
        )

        PrivacyAuditCache.restoreInto(
            PrivacyAuditManager.shared
        )


        // ========================================================
        // CONSENTEMENT DELIRIIUM
        // ========================================================

        val preferences =
            getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

        val storedConsentVersion =
            preferences.getInt(
                KEY_VPN_CONSENT_VERSION,
                0
            )

        val initialConsentAccepted =
            storedConsentVersion >=
                    CURRENT_VPN_CONSENT_VERSION


        // ========================================================
        // LIFECYCLE APPLICATION
        // ========================================================

        ProcessLifecycleOwner
            .get()
            .lifecycle
            .addObserver(

                LifecycleEventObserver { _, event ->

                    when (event) {

                        Lifecycle.Event.ON_STOP -> {

                            backgroundAt =
                                System.currentTimeMillis()
                        }

                        Lifecycle.Event.ON_START -> {

                            val startedAt =
                                backgroundAt

                            backgroundAt =
                                null

                            if (startedAt != null) {

                                val elapsed =
                                    System.currentTimeMillis() -
                                            startedAt

                                if (
                                    elapsed >=
                                    AUTO_DISCONNECT_TIMEOUT_MS &&
                                    tunnelManager.isProtected
                                ) {

                                    lifecycleScopeLite.launch {

                                        tunnelManager
                                            .disconnectAutomaticallyAfterInactivity()
                                    }
                                }
                            }
                        }

                        else -> Unit
                    }
                }
            )


        // ========================================================
        // INTERFACE
        // ========================================================

        enableEdgeToEdge()

        setContent {

            MaterialTheme {

                Surface(
                    modifier =
                        Modifier.fillMaxSize(),

                    color =
                        MaterialTheme
                            .colorScheme
                            .background
                ) {

                    // ====================================================
                    // ETATS DE DEMARRAGE
                    // ====================================================

                    var launcherFinished by
                    rememberSaveable {
                        mutableStateOf(false)
                    }

                    var vpnConsentAccepted by
                    rememberSaveable {
                        mutableStateOf(
                            initialConsentAccepted
                        )
                    }

                    /*
                     * Une baseline présente dans le cache signifie que
                     * l'étape "Finalisation de votre installation" a déjà
                     * été exécutée avec succès sur cette installation.
                     */
                    var privacyBootstrapComplete by
                    rememberSaveable {
                        mutableStateOf(
                            PrivacyAuditCache.hasBaseline()
                        )
                    }

                    var currentScreen by
                    rememberSaveable {
                        mutableStateOf(
                            AppScreen.WELCOME
                        )
                    }

                    LaunchedEffect(Unit) {
                        if (!launcherFinished) {
                            delay(
                                LAUNCH_SCREEN_DURATION_MS
                            )
                            launcherFinished =
                                true
                        }
                    }


                    // ====================================================
                    // AUTORISATION VPN ANDROID
                    // ====================================================

                    val vpnPermissionLauncher =
                        rememberLauncherForActivityResult(

                            contract =
                                ActivityResultContracts
                                    .StartActivityForResult()

                        ) { result ->

                            /*
                             * Réponse à la vraie popup Android :
                             * "Deliriuum souhaite configurer une connexion VPN".
                             *
                             * IMPORTANT : accepter cette popup N'ACTIVE PAS le VPN.
                             * On peut donc encore effectuer une vraie mesure AVANT.
                             */
                            if (
                                result.resultCode ==
                                Activity.RESULT_OK
                            ) {

                                preferences
                                    .edit()
                                    .putInt(
                                        KEY_VPN_CONSENT_VERSION,
                                        CURRENT_VPN_CONSENT_VERSION
                                    )
                                    .apply()

                                vpnConsentAccepted =
                                    true

                                /*
                                 * Ne jamais aller directement à HOME ici.
                                 * La prochaine étape est obligatoirement
                                 * l'audit AVANT si son cache n'existe pas.
                                 */
                                currentScreen =
                                    AppScreen.WELCOME

                            } else {

                                vpnConsentAccepted =
                                    false
                            }
                        }


                    // ====================================================
                    // GATE DE DEMARRAGE
                    // ====================================================

                    when {

                        // ------------------------------------------------
                        // 1. Launch screen Deliriuum
                        // ------------------------------------------------
                        !launcherFinished -> {

                            DeliriuumLaunchScreen()
                        }


                        // ------------------------------------------------
                        // 2. Consentement + popup VPN Android
                        // ------------------------------------------------
                        !vpnConsentAccepted -> {

                            VpnConsentDialog(

                                onAccept = {

                                    val permissionIntent =
                                        VpnService.prepare(
                                            this@MainActivity
                                        )

                                    if (permissionIntent != null) {

                                        /*
                                         * La popup système Android est affichée
                                         * AVANT l'audit de référence.
                                         */
                                        vpnPermissionLauncher.launch(
                                            permissionIntent
                                        )

                                    } else {

                                        /*
                                         * Permission Android déjà accordée.
                                         * On mémorise le consentement puis on
                                         * laisse le gate passer au bootstrap.
                                         */
                                        preferences
                                            .edit()
                                            .putInt(
                                                KEY_VPN_CONSENT_VERSION,
                                                CURRENT_VPN_CONSENT_VERSION
                                            )
                                            .apply()

                                        vpnConsentAccepted =
                                            true

                                        currentScreen =
                                            AppScreen.WELCOME
                                    }
                                },

                                onDismiss = {

                                    /*
                                     * Refus : aucun accès à l'app.
                                     * Le refus n'est pas mémorisé afin que
                                     * la demande soit reproposée au prochain lancement.
                                     */
                                    finishAndRemoveTask()
                                }
                            )
                        }


                        // ------------------------------------------------
                        // 3. Une seule mesure AVANT, puis cache local
                        // ------------------------------------------------
                        !privacyBootstrapComplete -> {

                            PrivacyAuditBootstrapScreen(

                                onCompleted = {

                                    privacyBootstrapComplete =
                                        true

                                    currentScreen =
                                        AppScreen.WELCOME
                                }
                            )
                        }


                        // ------------------------------------------------
                        // 4. Application normale
                        // ------------------------------------------------
                        else -> {

                            when (currentScreen) {

                                AppScreen.WELCOME -> {

                                    WelcomeScreen(

                                        onContinueClick = {

                                            currentScreen =
                                                AppScreen.SUPPORT
                                        }
                                    )
                                }


                                AppScreen.SUPPORT -> {

                                    SupportIntroScreen(

                                        onContinue = {

                                            currentScreen =
                                                AppScreen.HOME
                                        }
                                    )
                                }


                                AppScreen.HOME -> {

                                    HomeView(

                                        authManager =
                                            authManager,

                                        onNavigateBackToWelcome = {

                                            currentScreen =
                                                AppScreen.WELCOME
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
}
