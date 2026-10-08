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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
 * 1. autorisation VPN Android déjà acceptée ;
 * 2. audit AVANT dans le process :privacy_audit ;
 * 3. sauvegarde locale ;
 * 4. transition automatique vers WelcomeScreen.
 */
@Composable
fun PrivacyAuditBootstrapScreen(
    onCompleted: () -> Unit
) {

    val context =
        LocalContext.current

    val manager =
        PrivacyAuditManager.shared

    var state by remember {
        mutableStateOf(
            InstallationAuditState.PREPARING
        )
    }

    var errorText by remember {
        mutableStateOf<String?>(
            null
        )
    }

    var receiverRegistered by remember {
        mutableStateOf(false)
    }

    var launchSerial by remember {
        mutableStateOf(0)
    }


    // ============================================================
    // RESULTAT DU PROCESS BASELINE
    // ============================================================

    DisposableEffect(
        context
    ) {

        val receiver =
            object : BroadcastReceiver() {

                override fun onReceive(
                    receiverContext: Context?,
                    intent: Intent?
                ) {

                    when (
                        intent?.action
                    ) {

                        HomePrivacyAuditContract
                            .ACTION_BASELINE_RESULT -> {

                            val raw =
                                intent.getStringExtra(
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
                                 * Le manager sauvegarde aussi le snapshot,
                                 * mais on appelle saveBaseline ici pour rendre
                                 * l'intention explicite et robuste.
                                 */
                                PrivacyAuditCache
                                    .saveBaseline(
                                        payload
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
                                    "Le résultat de l’analyse n’a pas pu être lu."
                            }
                        }


                        HomePrivacyAuditContract
                            .ACTION_BASELINE_FINISHED -> {

                            val success =
                                intent.getBooleanExtra(
                                    HomePrivacyAuditContract
                                        .EXTRA_SUCCESS,
                                    false
                                )

                            if (success) {

                                /*
                                 * Si le broadcast RESULT a été reçu avant,
                                 * state est déjà COMPLETE. Si Android a livré
                                 * FINISHED en premier, le cache écrit depuis
                                 * :privacy_audit permet de restaurer ici.
                                 */
                                PrivacyAuditCache
                                    .restoreInto(
                                        manager
                                    )

                                if (
                                    PrivacyAuditCache
                                        .hasBaseline()
                                ) {

                                    state =
                                        InstallationAuditState.COMPLETE

                                    errorText =
                                        null
                                }

                            } else if (
                                !PrivacyAuditCache
                                    .hasBaseline()
                            ) {

                                state =
                                    InstallationAuditState.FAILED

                                errorText =
                                    "L’analyse de référence n’a pas pu être terminée."
                            }
                        }
                    }
                }
            }

        ContextCompat.registerReceiver(
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
                context.unregisterReceiver(
                    receiver
                )
            } catch (_: Exception) {
                // Receiver déjà retiré : rien à faire.
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

        if (!receiverRegistered) {
            return@LaunchedEffect
        }

        if (
            PrivacyAuditCache
                .hasBaseline()
        ) {

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

        if (launchSerial > 0) {
            /*
             * L'ancien process :privacy_audit se termine volontairement
             * quelques centaines de ms après un échec. On lui laisse
             * le temps de disparaître avant un retry.
             */
            delay(650L)
        }

        /*
         * Aucun tunnel n'est ouvert ici. La popup Android a seulement
         * accordé le droit de créer un VPN ; elle ne l'a pas activé.
         */
        BaselineAuditActivity.start(
            context
        )
    }


    // ============================================================
    // TRANSITION AUTOMATIQUE VERS WELCOME
    // ============================================================

    LaunchedEffect(
        state
    ) {

        if (
            state ==
            InstallationAuditState.COMPLETE
        ) {

            delay(
                450L
            )

            onCompleted()
        }
    }


    // ============================================================
    // UI
    // ============================================================

    val transition =
        rememberInfiniteTransition(
            label = "privacy-bootstrap"
        )

    val scale by
        transition.animateFloat(
            initialValue = 0.98f,
            targetValue = 1.02f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis = 1200
                        ),
                    repeatMode =
                        RepeatMode.Reverse
                ),
            label = "bootstrap-logo"
        )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
    ) {

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    Color.Cyan.copy(alpha = 0.16f),
                                    Color(0xFF8A2BE2).copy(alpha = 0.08f),
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
                        horizontal = 28.dp,
                        vertical = 34.dp
                    ),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Spacer(
                modifier =
                    Modifier.weight(1f)
            )

            Image(
                painter =
                    painterResource(
                        id = R.drawable.logo
                    ),
                contentDescription =
                    "Deliriuum",
                modifier =
                    Modifier
                        .size(108.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
            )

            Spacer(
                modifier =
                    Modifier.height(20.dp)
            )

            Text(
                text = "DELIRIIUM",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp
            )

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            Text(
                text =
                    "Finalisation de votre installation",
                color = Color.White,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            Text(
                text =
                    "Deliriuum effectue les dernières vérifications avant votre première utilisation.",
                color =
                    Color.White.copy(
                        alpha = 0.76f
                    ),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )

            Spacer(
                modifier =
                    Modifier.height(28.dp)
            )

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Color.White.copy(
                                alpha = 0.06f
                            ),
                            RoundedCornerShape(18.dp)
                        )
                        .border(
                            width = 1.dp,
                            color =
                                Color.White.copy(
                                    alpha = 0.11f
                                ),
                            shape =
                                RoundedCornerShape(18.dp)
                        )
                        .padding(18.dp),
                verticalArrangement =
                    Arrangement.spacedBy(14.dp)
            ) {

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Column(
                        modifier =
                            Modifier.weight(1f)
                    ) {

                        Text(
                            text =
                                "Analyse de référence",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(
                            modifier =
                                Modifier.height(4.dp)
                        )

                        Text(
                            text =
                                when (state) {

                                    InstallationAuditState.PREPARING ->
                                        "Mesure de votre environnement avant l’activation de Deep Shield…"

                                    InstallationAuditState.COMPLETE ->
                                        "Analyse terminée. Votre référence AVANT est enregistrée."

                                    InstallationAuditState.FAILED ->
                                        errorText
                                            ?: "L’analyse n’a pas pu être terminée."
                                },
                            color =
                                Color.White.copy(
                                    alpha = 0.66f
                                ),
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.size(12.dp)
                    )

                    when (state) {

                        InstallationAuditState.PREPARING ->
                            CircularProgressIndicator(
                                modifier =
                                    Modifier.size(28.dp),
                                strokeWidth =
                                    2.5.dp,
                                color = Color.Cyan
                            )

                        InstallationAuditState.COMPLETE ->
                            Box(
                                modifier =
                                    Modifier
                                        .size(30.dp)
                                        .background(
                                            Color(0xFF42E695)
                                                .copy(alpha = 0.14f),
                                            CircleShape
                                        )
                                        .border(
                                            1.dp,
                                            Color(0xFF42E695)
                                                .copy(alpha = 0.35f),
                                            CircleShape
                                        ),
                                contentAlignment =
                                    Alignment.Center
                            ) {
                                Text(
                                    text = "✓",
                                    color =
                                        Color(0xFF42E695),
                                    fontWeight =
                                        FontWeight.Black
                                )
                            }

                        InstallationAuditState.FAILED ->
                            Text(
                                text = "!",
                                color =
                                    Color(0xFFFF5A6F),
                                fontSize = 24.sp,
                                fontWeight =
                                    FontWeight.Black
                            )
                    }
                }

                when (state) {

                    InstallationAuditState.PREPARING -> {

                        LinearProgressIndicator(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(7.dp),
                            color = Color.Cyan,
                            trackColor =
                                Color.White.copy(
                                    alpha = 0.08f
                                )
                        )
                    }

                    InstallationAuditState.COMPLETE -> {

                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .background(
                                        Color.White.copy(
                                            alpha = 0.08f
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
                                            Color(0xFF42E695),
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
                                ButtonDefaults.buttonColors(
                                    containerColor =
                                        Color.Cyan,
                                    contentColor =
                                        Color.Black
                                )
                        ) {

                            Text(
                                text = "Réessayer",
                                fontWeight =
                                    FontWeight.Bold
                            )
                        }
                    }
                }

                Text(
                    text =
                        "Cette étape n’est effectuée qu’une seule fois.",
                    color =
                        Color.White.copy(
                            alpha = 0.50f
                        ),
                    fontSize = 11.sp
                )
            }

            Spacer(
                modifier =
                    Modifier.weight(1f)
            )

            Text(
                text =
                    "Le résultat de cette analyse reste enregistré localement sur votre appareil.",
                color =
                    Color.White.copy(
                        alpha = 0.46f
                    ),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier.padding(
                        horizontal = 16.dp
                    )
            )
        }
    }
}
