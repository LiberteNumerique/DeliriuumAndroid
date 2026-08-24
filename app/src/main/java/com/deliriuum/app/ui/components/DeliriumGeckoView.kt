package com.deliriuum.app.ui.components

import android.content.Intent
import android.util.Log
import android.view.ViewGroup
import androidx.compose.runtime.Composable
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
import com.deliriuum.app.data.PrivacyAuditManager

@Composable
fun DeliriumGeckoView(
    url: String,
    modifier: Modifier = Modifier,
    privateSession: Boolean = false
) {

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

                        /*
                         * ETAPE SUIVANTE :
                         *
                         * PrivacyAuditManager.shared
                         *     .updateFromProbe(
                         *         payload
                         *     )
                         */


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
                }


            session
                .navigationDelegate =
                navigationDelegate


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