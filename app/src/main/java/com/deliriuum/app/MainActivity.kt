package com.deliriuum.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.deliriuum.app.data.AuthManager
import com.deliriuum.app.data.KeychainStore
import com.deliriuum.app.data.TunnelManager
import com.deliriuum.app.ui.screens.HomeView
import com.deliriuum.app.ui.screens.SupportIntroScreen
import com.deliriuum.app.ui.screens.WelcomeScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    enum class AppScreen {
        WELCOME,
        SUPPORT,
        HOME
    }

    companion object {
        private const val MINUTE = 60_000L
        private const val AUTO_DISCONNECT_TIMEOUT_MS = 30 * MINUTE
    }

    private var backgroundAt: Long? = null

    private val lifecycleScopeLite =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Main
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ========================================================
        // INITIALISATION DES STORES / MANAGERS
        // ========================================================

        /*
         * KeychainStore doit être initialisé en premier.
         *
         * AuthManager et APIClient utilisent ensuite ce store
         * pour déterminer si une session locale existe.
         */
        KeychainStore.initialize(
            applicationContext
        )

        /*
         * Initialisation du gestionnaire WireGuard.
         */
        TunnelManager.initialize(
            this
        )

        /*
         * Singleton d'authentification.
         */
        val authManager =
            AuthManager.shared

        /*
         * Initialise le cache du profil utilisateur.
         *
         * Si une session existe :
         * - le dernier profil connu est chargé immédiatement ;
         * - /auth/me est ensuite actualisé silencieusement.
         */
        authManager.initialize(
            applicationContext
        )

        val tunnelManager =
            TunnelManager.shared


        // ========================================================
        // APP LIFECYCLE
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


                        else ->
                            Unit
                    }
                }
            )


        // ========================================================
        // UI
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

                    var currentScreen by
                    remember {
                        mutableStateOf(
                            AppScreen.WELCOME
                        )
                    }


                    when (currentScreen) {

                        // ========================================
                        // WELCOME
                        // ========================================

                        AppScreen.WELCOME -> {

                            WelcomeScreen(
                                onContinueClick = {

                                    currentScreen =
                                        AppScreen.SUPPORT
                                }
                            )
                        }


                        // ========================================
                        // SUPPORT
                        // ========================================

                        AppScreen.SUPPORT -> {

                            SupportIntroScreen(
                                onContinue = {

                                    currentScreen =
                                        AppScreen.HOME
                                }
                            )
                        }


                        // ========================================
                        // HOME
                        // ========================================

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