package com.deliriuum.app.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import java.util.concurrent.atomic.AtomicBoolean


object HomePrivacyAuditContract {

    const val ACTION_BASELINE_RESULT =
        "com.deliriuum.app.PRIVACY_BASELINE_RESULT"

    const val ACTION_BASELINE_FINISHED =
        "com.deliriuum.app.PRIVACY_BASELINE_FINISHED"

    const val EXTRA_PAYLOAD =
        "privacy_audit_payload"

    const val EXTRA_SUCCESS =
        "privacy_audit_success"

    const val AUDIT_URL =
        "https://example.com/"

    const val NATIVE_APP =
        "deepshield"
}


/*
 * Etat strictement local au process :privacy_audit.
 *
 * On empêche deux Activities baseline de lancer deux GeckoRuntime
 * en parallèle dans CE process.
 *
 * Contrairement à la version précédente, on ne conserve PAS le
 * GeckoRuntime après l'audit : le process :privacy_audit est
 * volontairement terminé après la mesure AVANT.
 *
 * C'est essentiel car Deliriuum utilise ensuite son propre
 * GeckoRuntime protégé dans le process principal. Les deux moteurs
 * Gecko ne doivent pas rester actifs simultanément.
 */
private object BaselineAuditProcessState {

    private val running =
        AtomicBoolean(false)

    fun tryBeginAudit(): Boolean =
        running.compareAndSet(
            false,
            true
        )

    fun endAudit() {
        running.set(false)
    }
}


/*
 * Audit AVANT Deliriuum.
 *
 * Cette Activity tourne dans :privacy_audit avec un GeckoRuntime
 * standard :
 *
 * - aucun DeepShieldConfig
 * - aucun timezone.js
 * - aucune Fingerprinting Protection imposée par Deliriuum
 * - aucun override Deep Shield
 *
 * Une GeckoView 1 x 1 px est réellement attachée afin que les
 * mesures Canvas/WebGL soient exécutées dans un contexte de rendu.
 *
 * Une fois le résultat obtenu :
 *
 * 1. la session est fermée ;
 * 2. le runtime baseline est arrêté ;
 * 3. le résultat est envoyé à Home ;
 * 4. le process :privacy_audit est terminé.
 *
 * Ainsi le GeckoRuntime protégé de Deliriuum pourra démarrer ensuite
 * sans cohabiter avec le runtime baseline.
 */
class BaselineAuditActivity :
    Activity() {

    companion object {

        private const val TAG =
            "PrivacyBaseline"

        private const val EXTENSION_LOCATION =
            "resource://android/assets/deepshield_baseline/"

        private const val EXTENSION_ID =
            "deepshield-baseline@deliriuum.com"

        private const val TIMEOUT_MS =
            15_000L

        private const val PROCESS_KILL_DELAY_MS =
            350L


        fun start(
            context: Context
        ) {

            val intent =
                Intent(
                    context,
                    BaselineAuditActivity::class.java
                ).apply {

                    addFlags(
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    )

                    if (
                        context !is Activity
                    ) {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                        )
                    }
                }

            context.startActivity(
                intent
            )
        }
    }


    private val mainHandler =
        Handler(
            Looper.getMainLooper()
        )

    private var runtime:
            GeckoRuntime? =
        null

    private var session:
            GeckoSession? =
        null

    private var finished =
        false

    private var ownsAudit =
        false

    private var processTerminationScheduled =
        false


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        PrivacyAuditCache.initialize(
            applicationContext
        )

        ownsAudit =
            BaselineAuditProcessState
                .tryBeginAudit()

        if (!ownsAudit) {

            Log.d(
                TAG,
                "Baseline audit already running; duplicate launch ignored"
            )

            finish()

            overridePendingTransition(
                0,
                0
            )

            return
        }

        overridePendingTransition(
            0,
            0
        )

        /*
         * L'Activity technique doit être visuellement imperceptible.
         */
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_DIM_BEHIND
        )

        window.setDimAmount(
            0f
        )

        val root =
            FrameLayout(
                this
            ).apply {

                layoutParams =
                    ViewGroup.LayoutParams(
                        1,
                        1
                    )
            }

        setContentView(
            root
        )

        startBaseline(
            root
        )
    }


    override fun onNewIntent(
        intent: Intent?
    ) {

        super.onNewIntent(
            intent
        )

        Log.d(
            TAG,
            "Duplicate baseline request ignored while audit is running"
        )
    }


    private fun startBaseline(
        root: FrameLayout
    ) {

        Log.d(
            TAG,
            "Starting baseline audit BEFORE Deliriuum"
        )

        try {

            val runtimeSettings =
                GeckoRuntimeSettings
                    .Builder()
                    .build()

            val createdRuntime =
                GeckoRuntime.create(
                    applicationContext,
                    runtimeSettings
                )

            runtime =
                createdRuntime

            createdRuntime
                .webExtensionController
                .ensureBuiltIn(
                    EXTENSION_LOCATION,
                    EXTENSION_ID
                )
                .accept(

                    { nullableExtension ->

                        if (finished) {
                            return@accept
                        }

                        val extension =
                            nullableExtension
                                ?: run {

                                    failAndFinish(
                                        "Baseline extension unavailable"
                                    )

                                    return@accept
                                }

                        runOnUiThread {

                            if (!finished) {

                                createSessionAndLoad(
                                    root =
                                        root,
                                    runtime =
                                        createdRuntime,
                                    extension =
                                        extension
                                )
                            }
                        }
                    },

                    { error ->

                        Log.e(
                            TAG,
                            "Baseline extension installation failed",
                            error
                        )

                        runOnUiThread {

                            failAndFinish(
                                "Baseline extension installation failed"
                            )
                        }
                    }
                )

            mainHandler.postDelayed(
                {

                    if (!finished) {

                        failAndFinish(
                            "Baseline audit timeout"
                        )
                    }

                },
                TIMEOUT_MS
            )

        } catch (
            error: Throwable
        ) {

            Log.e(
                TAG,
                "Unable to start baseline audit",
                error
            )

            failAndFinish(
                "Unable to start baseline audit"
            )
        }
    }


    private fun createSessionAndLoad(
        root: FrameLayout,
        runtime: GeckoRuntime,
        extension: WebExtension
    ) {

        if (finished) {
            return
        }

        try {

            val settings =
                GeckoSessionSettings
                    .Builder()
                    .usePrivateMode(
                        true
                    )
                    .userAgentMode(
                        GeckoSessionSettings
                            .USER_AGENT_MODE_MOBILE
                    )
                    .build()

            val createdSession =
                GeckoSession(
                    settings
                )

            session =
                createdSession

            val messageDelegate =
                object :
                    WebExtension.MessageDelegate {

                    override fun onMessage(
                        nativeApp: String,
                        message: Any,
                        sender:
                        WebExtension.MessageSender
                    ): GeckoResult<Any>? {

                        if (
                            nativeApp !=
                            HomePrivacyAuditContract.NATIVE_APP
                        ) {
                            return null
                        }

                        val json =
                            when (message) {

                                is JSONObject ->
                                    message

                                is Map<*, *> -> {

                                    try {

                                        JSONObject(
                                            message
                                        )

                                    } catch (
                                        error: Exception
                                    ) {

                                        Log.e(
                                            TAG,
                                            "Invalid baseline audit message",
                                            error
                                        )

                                        return null
                                    }
                                }

                                else ->
                                    return null
                            }

                        if (
                            json.optString(
                                "type"
                            ) !=
                            "privacy_audit"
                        ) {
                            return null
                        }

                        val payload =
                            json.optJSONObject(
                                "payload"
                            )
                                ?: return null

                        Log.d(
                            TAG,
                            "Baseline probe received"
                        )

                        runOnUiThread {

                            completeSuccessfully(
                                payload
                            )
                        }

                        return null
                    }
                }

            createdSession.open(
                runtime
            )

            createdSession
                .webExtensionController
                .setMessageDelegate(
                    extension,
                    messageDelegate,
                    HomePrivacyAuditContract.NATIVE_APP
                )

            val geckoView =
                GeckoView(
                    this
                ).apply {

                    alpha =
                        0.01f

                    layoutParams =
                        FrameLayout.LayoutParams(
                            1,
                            1
                        )

                    setSession(
                        createdSession
                    )
                }

            root.addView(
                geckoView
            )

            createdSession.loadUri(
                HomePrivacyAuditContract
                    .AUDIT_URL
            )

        } catch (
            error: Throwable
        ) {

            Log.e(
                TAG,
                "Unable to create baseline Gecko session",
                error
            )

            failAndFinish(
                "Unable to create baseline Gecko session"
            )
        }
    }


    private fun completeSuccessfully(
        payload: JSONObject
    ) {

        if (finished) {
            return
        }

        finished =
            true

        val serializedPayload =
            payload.toString()

        /*
         * Persistance immédiate dans le process :privacy_audit.
         * Même si l'Activity principale est momentanément recréée,
         * la baseline reste acquise et ne sera jamais relancée.
         */
        PrivacyAuditCache
            .saveBaseline(
                payload
            )

        /*
         * IMPORTANT :
         * on arrête d'abord Gecko baseline.
         *
         * Le résultat n'est publié qu'après avoir demandé la fermeture
         * de la session et du runtime afin de réduire au maximum toute
         * cohabitation avec le runtime Deep Shield du process principal.
         */
        shutdownBaselineGecko()

        sendBroadcast(
            Intent(
                HomePrivacyAuditContract
                    .ACTION_BASELINE_RESULT
            ).apply {

                setPackage(
                    packageName
                )

                putExtra(
                    HomePrivacyAuditContract
                        .EXTRA_PAYLOAD,
                    serializedPayload
                )
            }
        )

        Log.d(
            TAG,
            "Baseline result sent to Home"
        )

        notifyFinished(
            success = true
        )

        closeActivityAndTerminateProcess()
    }


    private fun failAndFinish(
        reason: String
    ) {

        if (finished) {
            return
        }

        finished =
            true

        Log.e(
            TAG,
            reason
        )

        shutdownBaselineGecko()

        notifyFinished(
            success = false
        )

        closeActivityAndTerminateProcess()
    }


    private fun shutdownBaselineGecko() {

        mainHandler.removeCallbacksAndMessages(
            null
        )

        try {

            session?.close()

        } catch (
            error: Throwable
        ) {

            Log.w(
                TAG,
                "Error while closing baseline GeckoSession",
                error
            )
        }

        session =
            null

        try {

            runtime?.shutdown()

        } catch (
            error: Throwable
        ) {

            Log.w(
                TAG,
                "Error while shutting down baseline GeckoRuntime",
                error
            )
        }

        runtime =
            null
    }


    private fun notifyFinished(
        success: Boolean
    ) {

        sendBroadcast(
            Intent(
                HomePrivacyAuditContract
                    .ACTION_BASELINE_FINISHED
            ).apply {

                setPackage(
                    packageName
                )

                putExtra(
                    HomePrivacyAuditContract
                        .EXTRA_SUCCESS,
                    success
                )
            }
        )
    }


    private fun closeActivityAndTerminateProcess() {

        if (ownsAudit) {

            ownsAudit =
                false

            BaselineAuditProcessState
                .endAudit()
        }

        finish()

        overridePendingTransition(
            0,
            0
        )

        scheduleProcessTermination()
    }


    private fun scheduleProcessTermination() {

        if (processTerminationScheduled) {
            return
        }

        processTerminationScheduled =
            true

        mainHandler.postDelayed(
            {

                Log.d(
                    TAG,
                    "Terminating baseline process"
                )

                Process.killProcess(
                    Process.myPid()
                )

            },
            PROCESS_KILL_DELAY_MS
        )
    }


    override fun onDestroy() {

        /*
         * Si Android détruit l'Activity autrement que par le chemin
         * normal, on ne laisse jamais un Gecko baseline vivant.
         */
        if (!finished) {

            finished =
                true

            shutdownBaselineGecko()
        }

        if (ownsAudit) {

            ownsAudit =
                false

            BaselineAuditProcessState
                .endAudit()
        }

        /*
         * Ce process n'a aucune autre responsabilité que l'audit AVANT.
         */
        scheduleProcessTermination()

        super.onDestroy()
    }
}
