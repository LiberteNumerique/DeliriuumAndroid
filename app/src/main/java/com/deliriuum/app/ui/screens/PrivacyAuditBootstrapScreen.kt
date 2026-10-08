package com.deliriuum.app.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.deliriuum.app.R
import com.deliriuum.app.data.BaselineAuditActivity
import com.deliriuum.app.data.HomePrivacyAuditContract
import com.deliriuum.app.data.PrivacyAuditCache
import com.deliriuum.app.data.PrivacyAuditManager
import com.deliriuum.app.data.TunnelManager
import com.deliriuum.app.data.TunnelStatus
import kotlinx.coroutines.delay
import org.json.JSONObject


private const val BOOTSTRAP_TAG =
    "PrivacyBootstrapUI"


private enum class InstallationAuditState {
    PREPARING,
    COMPLETE,
    FAILED
}


/**
 * Petit écran de lancement visuel, calqué sur le bootstrap iOS.
 * Il ne lance aucun audit : il masque simplement le cold start Compose.
 */
@Composable
fun DeliriuumLaunchScreen() {

    val transition =
        rememberInfiniteTransition(
            label = "deliriuum-launch"
        )

    val scale by
    transition.animateFloat(
        initialValue = 0.975f,
        targetValue = 1.025f,
        animationSpec =
            infiniteRepeatable(
                animation =
                    tween(
                        durationMillis = 1150
                    ),
                repeatMode =
                    RepeatMode.Reverse
            ),
        label = "logo-pulse"
    )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        contentAlignment =
            Alignment.Center
    ) {

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    Color.Cyan.copy(alpha = 0.20f),
                                    Color(0xFF8A2BE2).copy(alpha = 0.10f),
                                    Color.Black
                                )
                        )
                    )
        )

        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.Center
        ) {

            Image(
                painter =
                    painterResource(
                        id = R.drawable.logo
                    ),
                contentDescription =
                    "Deliriuum",
                modifier =
                    Modifier
                        .size(176.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
            )

            Spacer(
                modifier =
                    Modifier.height(22.dp)
            )

            Text(
                text = "DELIRIIUM",
                color = Color.White,
                fontSize = 25.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.5.sp
            )
        }
    }
}


/**
 * Gate exécuté uniquement lorsqu'aucune baseline persistante n'existe.
 *
 * Ordre voulu :
 *
 * 1. si une baseline existe déjà : restauration immédiate ;
 * 2. si le VPN est déjà actif (par exemple après une mise à jour),
 *    mémoriser cet état et couper temporairement le tunnel ;
 * 3. audit AVANT dans le process :privacy_audit ;
 * 4. sauvegarde locale de la baseline ;
 * 5. restauration automatique du VPN s'il était actif auparavant ;
 * 6. transition vers l'application.
 *
 * Important :
 *
 * - l'audit ne doit jamais laisser l'utilisateur sans VPN à la suite
 *   d'une erreur ;
 * - si le kill-switch Android est actif, on ne coupe pas le VPN :
 *   sans tunnel, Android bloquerait également l'accès réseau nécessaire
 *   à l'audit.
 */
@Composable
fun PrivacyAuditBootstrapScreen(
    onCompleted: () -> Unit
) {

    val _i18nContext =
        LocalContext.current

    val context =
        LocalContext.current

    val manager =
        PrivacyAuditManager.shared

    val tunnelManager =
        TunnelManager.shared


    /*
     * true lorsque le bootstrap a trouvé Deliriuum déjà actif
     * et l'a temporairement coupé.
     *
     * Après COMPLETE ou FAILED, le VPN sera restauré avant
     * de continuer.
     */
    var reconnectTunnelAfterBaseline by
    remember {
        mutableStateOf(
            false
        )
    }


    var state by
    remember {
        mutableStateOf(
            InstallationAuditState.PREPARING
        )
    }


    var errorText by
    remember {
        mutableStateOf<String?>(
            null
        )
    }


    var receiverRegistered by
    remember {
        mutableStateOf(
            false
        )
    }


    var launchSerial by
    remember {
        mutableStateOf(
            0
        )
    }


    // ============================================================
    // RESULTAT DU PROCESS BASELINE
    // ============================================================

    DisposableEffect(
        context
    ) {

        val receiver =
            object :
                BroadcastReceiver() {

                override fun onReceive(
                    receiverContext: Context?,
                    intent: Intent?
                ) {

                    when (
                        intent?.action
                    ) {

                        // =========================================
                        // PAYLOAD DE LA BASELINE
                        // =========================================

                        HomePrivacyAuditContract
                            .ACTION_BASELINE_RESULT -> {

                            val raw =
                                intent
                                    .getStringExtra(
                                        HomePrivacyAuditContract
                                            .EXTRA_PAYLOAD
                                    )
                                    ?: return


                            try {

                                val payload =
                                    JSONObject(
                                        raw
                                    )


                                manager
                                    .updateBaselineFromProbe(
                                        payload
                                    )


                                /*
                                 * Le manager sauvegarde déjà le
                                 * snapshot.
                                 *
                                 * L'appel explicite est conservé
                                 * pour rendre le bootstrap robuste
                                 * même si la logique du manager
                                 * évolue ultérieurement.
                                 */
                                PrivacyAuditCache
                                    .saveBaseline(
                                        payload
                                    )


                                Log.i(
                                    BOOTSTRAP_TAG,
                                    "Baseline reçue et enregistrée"
                                )


                                state =
                                    InstallationAuditState.COMPLETE


                                errorText =
                                    null

                            } catch (
                                error: Exception
                            ) {

                                Log.e(
                                    BOOTSTRAP_TAG,
                                    "Invalid baseline payload",
                                    error
                                )


                                state =
                                    InstallationAuditState.FAILED


                                errorText =
                                    _i18nContext
                                        .getString(
                                            R.string
                                                .privacy_audit_bootstrap_le_resultat_de_l_analyse_n_a_pas_pu_etre_lu
                                        )
                            }
                        }


                        // =========================================
                        // FIN DU PROCESS BASELINE
                        // =========================================

                        HomePrivacyAuditContract
                            .ACTION_BASELINE_FINISHED -> {

                            val success =
                                intent
                                    .getBooleanExtra(
                                        HomePrivacyAuditContract
                                            .EXTRA_SUCCESS,
                                        false
                                    )


                            if (
                                success
                            ) {

                                /*
                                 * RESULT et FINISHED sont deux
                                 * broadcasts distincts.
                                 *
                                 * Si FINISHED arrive avant RESULT,
                                 * le cache permet de retrouver
                                 * la baseline.
                                 */
                                PrivacyAuditCache
                                    .restoreInto(
                                        manager
                                    )


                                if (
                                    PrivacyAuditCache
                                        .hasBaseline()
                                ) {

                                    Log.i(
                                        BOOTSTRAP_TAG,
                                        "Baseline restaurée depuis le cache"
                                    )


                                    state =
                                        InstallationAuditState.COMPLETE


                                    errorText =
                                        null
                                }

                            } else if (
                                !PrivacyAuditCache
                                    .hasBaseline()
                            ) {

                                Log.e(
                                    BOOTSTRAP_TAG,
                                    "Baseline terminée en échec"
                                )


                                state =
                                    InstallationAuditState.FAILED


                                errorText =
                                    _i18nContext
                                        .getString(
                                            R.string
                                                .privacy_audit_bootstrap_l_analyse_de_reference_n_a_pas_pu_etre_terminee
                                        )
                            }
                        }
                    }
                }
            }


        ContextCompat
            .registerReceiver(
                context,
                receiver,
                IntentFilter().apply {

                    addAction(
                        HomePrivacyAuditContract
                            .ACTION_BASELINE_RESULT
                    )

                    addAction(
                        HomePrivacyAuditContract
                            .ACTION_BASELINE_FINISHED
                    )
                },
                ContextCompat.RECEIVER_NOT_EXPORTED
            )


        receiverRegistered =
            true


        onDispose {

            receiverRegistered =
                false


            try {

                context
                    .unregisterReceiver(
                        receiver
                    )

            } catch (
                _: Exception
            ) {

                /*
                 * Receiver déjà retiré.
                 */
            }
        }
    }


    // ============================================================
    // DEMARRAGE / RETRY
    // ============================================================

    LaunchedEffect(
        receiverRegistered,
        launchSerial
    ) {

        if (
            !receiverRegistered
        ) {

            return@LaunchedEffect
        }


        // ========================================================
        // BASELINE DEJA PRESENTE
        // ========================================================

        if (
            PrivacyAuditCache
                .hasBaseline()
        ) {

            Log.i(
                BOOTSTRAP_TAG,
                "Baseline déjà présente : bootstrap immédiat"
            )


            PrivacyAuditCache
                .restoreInto(
                    manager
                )


            state =
                InstallationAuditState.COMPLETE


            return@LaunchedEffect
        }


        state =
            InstallationAuditState.PREPARING


        errorText =
            null


        /*
         * Après un échec, le process :privacy_audit peut
         * mettre quelques centaines de millisecondes
         * à disparaître.
         */
        if (
            launchSerial >
            0
        ) {

            delay(
                650L
            )
        }


        // ========================================================
        // VPN DEJA ACTIF
        // ========================================================

        if (
            tunnelManager.status !=
            TunnelStatus.DISCONNECTED
        ) {

            Log.i(
                BOOTSTRAP_TAG,
                "VPN actif avant baseline : " +
                        "status=${tunnelManager.status}"
            )


            /*
             * Avec Always-on + Lockdown :
             *
             * Android bloque tout trafic réseau lorsque le VPN
             * n'est pas actif.
             *
             * Couper le tunnel empêcherait donc la baseline
             * de charger sa page.
             *
             * On ne bloque surtout pas l'utilisateur dans
             * cette situation.
             */
            if (
                tunnelManager
                    .killSwitchActive
            ) {

                Log.w(
                    BOOTSTRAP_TAG,
                    "Baseline différée : " +
                            "kill-switch Android actif"
                )


                onCompleted()


                return@LaunchedEffect
            }


            /*
             * L'utilisateur avait son VPN actif.
             *
             * On mémorise cet état AVANT de le couper.
             */
            reconnectTunnelAfterBaseline =
                true


            try {

                Log.i(
                    BOOTSTRAP_TAG,
                    "Déconnexion temporaire du VPN " +
                            "pour mesurer la baseline"
                )


                tunnelManager
                    .disconnect()


                Log.i(
                    BOOTSTRAP_TAG,
                    "disconnect() terminé : " +
                            "status=${tunnelManager.status}"
                )

            } catch (
                error: Exception
            ) {

                Log.e(
                    BOOTSTRAP_TAG,
                    "Impossible de couper temporairement le VPN",
                    error
                )


                /*
                 * On ne bloque jamais l'application
                 * à cause de l'audit.
                 *
                 * Dans ce cas le VPN est supposé avoir conservé
                 * son état précédent.
                 */
                reconnectTunnelAfterBaseline =
                    false


                onCompleted()


                return@LaunchedEffect
            }


            // ====================================================
            // ATTENDRE LE VRAI ETAT DISCONNECTED
            // ====================================================

            var waitedMs =
                0L


            while (
                tunnelManager.status !=
                TunnelStatus.DISCONNECTED &&
                waitedMs <
                5_000L
            ) {

                delay(
                    100L
                )


                waitedMs +=
                    100L
            }


            if (
                tunnelManager.status !=
                TunnelStatus.DISCONNECTED
            ) {

                Log.e(
                    BOOTSTRAP_TAG,
                    "Tunnel toujours actif après 5 secondes : " +
                            "baseline abandonnée"
                )


                /*
                 * Si l'état n'est pas DISCONNECTED,
                 * on considère que le VPN n'a pas été réellement
                 * arrêté et on ne tente pas une nouvelle connexion.
                 */
                reconnectTunnelAfterBaseline =
                    false


                onCompleted()


                return@LaunchedEffect
            }


            Log.i(
                BOOTSTRAP_TAG,
                "Tunnel réellement DISCONNECTED"
            )


            /*
             * On laisse Android retirer complètement
             * l'interface VPN avant de démarrer Gecko.
             */
            delay(
                400L
            )
        }


        // ========================================================
        // VERIFICATION FINALE
        // ========================================================

        if (
            tunnelManager.status !=
            TunnelStatus.DISCONNECTED
        ) {

            Log.e(
                BOOTSTRAP_TAG,
                "Baseline impossible : tunnel non déconnecté"
            )


            onCompleted()


            return@LaunchedEffect
        }


        // ========================================================
        // BASELINE AVANT DELIRIIUM
        // ========================================================

        Log.i(
            BOOTSTRAP_TAG,
            "Démarrage baseline avec tunnel OFF"
        )


        BaselineAuditActivity
            .start(
                context
            )
    }


    // ============================================================
    // FIN DE BASELINE / RESTAURATION DU VPN
    // ============================================================

    LaunchedEffect(
        state
    ) {

        /*
         * PREPARING :
         * aucun traitement de sortie.
         */
        if (
            state ==
            InstallationAuditState.PREPARING
        ) {

            return@LaunchedEffect
        }


        // ========================================================
        // RESTAURATION DU VPN
        // ========================================================

        if (
            reconnectTunnelAfterBaseline
        ) {

            /*
             * On remet immédiatement le flag à false.
             *
             * Cela empêche toute double reconnexion provoquée
             * par une recomposition Compose.
             */
            reconnectTunnelAfterBaseline =
                false


            if (
                tunnelManager.status ==
                TunnelStatus.DISCONNECTED
            ) {

                Log.i(
                    BOOTSTRAP_TAG,
                    "Restauration du VPN après baseline : " +
                            "result=$state"
                )


                try {

                    tunnelManager
                        .connect()


                    Log.i(
                        BOOTSTRAP_TAG,
                        "VPN reconnecté après baseline : " +
                                "status=${tunnelManager.status}"
                    )

                } catch (
                    error: Exception
                ) {

                    Log.e(
                        BOOTSTRAP_TAG,
                        "Impossible de reconnecter le VPN " +
                                "après baseline",
                        error
                    )


                    /*
                     * IMPORTANT :
                     *
                     * On ne transforme jamais une erreur
                     * de reconnexion en blocage infini
                     * du bootstrap.
                     */
                }
            }
        }


        // ========================================================
        // BASELINE REUSSIE
        // ========================================================

        if (
            state ==
            InstallationAuditState.COMPLETE
        ) {

            delay(
                450L
            )


            onCompleted()
        }


        /*
         * En cas FAILED :
         *
         * - si le VPN était actif, il vient d'être restauré ;
         * - l'écran reste affiché ;
         * - l'utilisateur peut appuyer sur "Réessayer".
         *
         * Lors du retry, si le VPN est de nouveau actif,
         * le bootstrap le recoupera temporairement.
         */
    }


    // ============================================================
    // UI
    // ============================================================

    val transition =
        rememberInfiniteTransition(
            label =
                "privacy-bootstrap"
        )


    val scale by
    transition
        .animateFloat(
            initialValue =
                0.98f,
            targetValue =
                1.02f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis =
                                1200
                        ),
                    repeatMode =
                        RepeatMode.Reverse
                ),
            label =
                "bootstrap-logo"
        )


    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color.Black
                )
    ) {

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush
                            .radialGradient(
                                colors =
                                    listOf(
                                        Color.Cyan
                                            .copy(
                                                alpha =
                                                    0.16f
                                            ),
                                        Color(
                                            0xFF8A2BE2
                                        )
                                            .copy(
                                                alpha =
                                                    0.08f
                                            ),
                                        Color.Black
                                    )
                            )
                    )
        )


        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal =
                            28.dp,
                        vertical =
                            34.dp
                    ),
            horizontalAlignment =
                Alignment
                    .CenterHorizontally
        ) {

            Spacer(
                modifier =
                    Modifier
                        .weight(
                            1f
                        )
            )


            Image(
                painter =
                    painterResource(
                        id =
                            R.drawable.logo
                    ),
                contentDescription =
                    "Deliriuum",
                modifier =
                    Modifier
                        .size(
                            108.dp
                        )
                        .graphicsLayer {

                            scaleX =
                                scale

                            scaleY =
                                scale
                        }
            )


            Spacer(
                modifier =
                    Modifier
                        .height(
                            20.dp
                        )
            )


            Text(
                text =
                    "DELIRIIUM",
                color =
                    Color.White,
                fontSize =
                    17.sp,
                fontWeight =
                    FontWeight.Black,
                letterSpacing =
                    3.sp
            )


            Spacer(
                modifier =
                    Modifier
                        .height(
                            12.dp
                        )
            )


            Text(
                text =
                    _i18nContext
                        .getString(
                            R.string
                                .privacy_audit_bootstrap_finalisation_de_votre_installation
                        ),
                color =
                    Color.White,
                fontSize =
                    23.sp,
                fontWeight =
                    FontWeight.Bold,
                textAlign =
                    TextAlign.Center
            )


            Spacer(
                modifier =
                    Modifier
                        .height(
                            8.dp
                        )
            )


            Text(
                text =
                    _i18nContext
                        .getString(
                            R.string
                                .privacy_audit_bootstrap_deliriuum_effectue_les_dernieres_verifications_avant_vot
                        ),
                color =
                    Color.White
                        .copy(
                            alpha =
                                0.76f
                        ),
                fontSize =
                    14.sp,
                lineHeight =
                    20.sp,
                textAlign =
                    TextAlign.Center
            )


            Spacer(
                modifier =
                    Modifier
                        .height(
                            28.dp
                        )
            )


            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Color.White
                                .copy(
                                    alpha =
                                        0.06f
                                ),
                            RoundedCornerShape(
                                18.dp
                            )
                        )
                        .border(
                            width =
                                1.dp,
                            color =
                                Color.White
                                    .copy(
                                        alpha =
                                            0.11f
                                    ),
                            shape =
                                RoundedCornerShape(
                                    18.dp
                                )
                        )
                        .padding(
                            18.dp
                        ),
                verticalArrangement =
                    Arrangement
                        .spacedBy(
                            14.dp
                        )
            ) {

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    verticalAlignment =
                        Alignment
                            .CenterVertically
                ) {

                    Column(
                        modifier =
                            Modifier
                                .weight(
                                    1f
                                )
                    ) {

                        Text(
                            text =
                                _i18nContext
                                    .getString(
                                        R.string
                                            .privacy_audit_bootstrap_analyse_de_reference
                                    ),
                            color =
                                Color.White,
                            fontSize =
                                14.sp,
                            fontWeight =
                                FontWeight.Bold
                        )


                        Spacer(
                            modifier =
                                Modifier
                                    .height(
                                        4.dp
                                    )
                        )


                        Text(
                            text =
                                when (
                                    state
                                ) {

                                    InstallationAuditState.PREPARING ->
                                        _i18nContext
                                            .getString(
                                                R.string
                                                    .privacy_audit_bootstrap_mesure_de_votre_environnement_avant_l_activation_de_deep
                                            )


                                    InstallationAuditState.COMPLETE ->
                                        _i18nContext
                                            .getString(
                                                R.string
                                                    .privacy_audit_bootstrap_analyse_terminee_votre_reference_avant_est_enregistree
                                            )


                                    InstallationAuditState.FAILED ->
                                        errorText
                                            ?: _i18nContext
                                                .getString(
                                                    R.string
                                                        .privacy_audit_bootstrap_l_analyse_n_a_pas_pu_etre_terminee
                                                )
                                },
                            color =
                                Color.White
                                    .copy(
                                        alpha =
                                            0.66f
                                    ),
                            fontSize =
                                12.sp,
                            lineHeight =
                                17.sp
                        )
                    }


                    Spacer(
                        modifier =
                            Modifier
                                .size(
                                    12.dp
                                )
                    )


                    when (
                        state
                    ) {

                        InstallationAuditState.PREPARING ->

                            CircularProgressIndicator(
                                modifier =
                                    Modifier
                                        .size(
                                            28.dp
                                        ),
                                strokeWidth =
                                    2.5.dp,
                                color =
                                    Color.Cyan
                            )


                        InstallationAuditState.COMPLETE ->

                            Box(
                                modifier =
                                    Modifier
                                        .size(
                                            30.dp
                                        )
                                        .background(
                                            Color(
                                                0xFF42E695
                                            )
                                                .copy(
                                                    alpha =
                                                        0.14f
                                                ),
                                            CircleShape
                                        )
                                        .border(
                                            1.dp,
                                            Color(
                                                0xFF42E695
                                            )
                                                .copy(
                                                    alpha =
                                                        0.35f
                                                ),
                                            CircleShape
                                        ),
                                contentAlignment =
                                    Alignment.Center
                            ) {

                                Text(
                                    text =
                                        "✓",
                                    color =
                                        Color(
                                            0xFF42E695
                                        ),
                                    fontWeight =
                                        FontWeight.Black
                                )
                            }


                        InstallationAuditState.FAILED ->

                            Text(
                                text =
                                    "!",
                                color =
                                    Color(
                                        0xFFFF5A6F
                                    ),
                                fontSize =
                                    24.sp,
                                fontWeight =
                                    FontWeight.Black
                            )
                    }
                }


                when (
                    state
                ) {

                    InstallationAuditState.PREPARING -> {

                        LinearProgressIndicator(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(
                                        7.dp
                                    ),
                            color =
                                Color.Cyan,
                            trackColor =
                                Color.White
                                    .copy(
                                        alpha =
                                            0.08f
                                    )
                        )
                    }


                    InstallationAuditState.COMPLETE -> {

                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(
                                        7.dp
                                    )
                                    .background(
                                        Color.White
                                            .copy(
                                                alpha =
                                                    0.08f
                                            ),
                                        CircleShape
                                    )
                        ) {

                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight()
                                        .background(
                                            Color(
                                                0xFF42E695
                                            ),
                                            CircleShape
                                        )
                            )
                        }
                    }


                    InstallationAuditState.FAILED -> {

                        Button(
                            onClick = {

                                launchSerial +=
                                    1
                            },
                            colors =
                                ButtonDefaults
                                    .buttonColors(
                                        containerColor =
                                            Color.Cyan,
                                        contentColor =
                                            Color.Black
                                    )
                        ) {

                            Text(
                                text =
                                    _i18nContext
                                        .getString(
                                            R.string
                                                .browser_reessayer
                                        ),
                                fontWeight =
                                    FontWeight.Bold
                            )
                        }
                    }
                }


                Text(
                    text =
                        _i18nContext
                            .getString(
                                R.string
                                    .privacy_audit_bootstrap_cette_etape_n_est_effectuee_qu_une_seule_fois
                            ),
                    color =
                        Color.White
                            .copy(
                                alpha =
                                    0.50f
                            ),
                    fontSize =
                        11.sp
                )
            }


            Spacer(
                modifier =
                    Modifier
                        .weight(
                            1f
                        )
            )


            Text(
                text =
                    _i18nContext
                        .getString(
                            R.string
                                .privacy_audit_bootstrap_le_resultat_de_cette_analyse_reste_enregistre_localement
                        ),
                color =
                    Color.White
                        .copy(
                            alpha =
                                0.46f
                        ),
                fontSize =
                    11.sp,
                lineHeight =
                    16.sp,
                textAlign =
                    TextAlign.Center,
                modifier =
                    Modifier
                        .padding(
                            horizontal =
                                16.dp
                        )
            )
        }
    }
}