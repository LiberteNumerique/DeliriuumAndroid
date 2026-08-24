package com.deliriuum.app.data

import android.content.Context
import android.util.Log
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.WebExtension

object DeliriumGeckoRuntime {

    private const val TAG =
        "DeepShield"

    private const val EXTENSION_LOCATION =
        "resource://android/assets/deepshield/"

    private const val EXTENSION_ID =
        "deepshield@deliriuum.com"

    const val NATIVE_APP =
        "deepshield"


    @Volatile
    private var runtime:
            GeckoRuntime? =
        null


    @Volatile
    var extension:
            WebExtension? =
        null
        private set


    private val extensionReadyCallbacks =
        mutableListOf<
                    (WebExtension) -> Unit
                >()


    // ============================================================
    // EXTENSION READY CALLBACK
    // ============================================================

    fun whenExtensionReady(
        callback:
            (WebExtension) -> Unit
    ) {

        val ready =
            extension

        if (
            ready != null
        ) {

            callback(
                ready
            )

            return
        }


        synchronized(
            extensionReadyCallbacks
        ) {

            val secondCheck =
                extension


            if (
                secondCheck != null
            ) {

                callback(
                    secondCheck
                )

            } else {

                extensionReadyCallbacks
                    .add(
                        callback
                    )
            }
        }
    }


    // ============================================================
    // GET RUNTIME
    // ============================================================

    fun get(
        context: Context
    ): GeckoRuntime {

        runtime?.let {
            return it
        }


        synchronized(
            this
        ) {

            runtime?.let {
                return it
            }


            val appContext =
                context.applicationContext


            // ====================================================
            // DEEP SHIELD NETWORK CONFIG
            // ====================================================

            val configPath =
                DeepShieldConfig.write(
                    appContext
                )


            // ====================================================
            // GECKO SETTINGS
            // ====================================================

            val settings =
                GeckoRuntimeSettings
                    .Builder()

                    /*
                     * TEMPORAIRE pendant validation.
                     *
                     * A désactiver pour le build release.
                     */
                    .consoleOutput(
                        true
                    )

                    .debugLogging(
                        true
                    )

                    /*
                     * Préférences réseau Deep Shield.
                     */
                    .configFilePath(
                        configPath
                    )

                    .build()


            // ====================================================
            // MOZILLA FINGERPRINTING PROTECTION
            // ====================================================

            settings
                .setFingerprintingProtection(
                    true
                )


            settings
                .setFingerprintingProtectionPrivateBrowsing(
                    true
                )


            settings
                .setFingerprintingProtectionOverrides(
                    listOf(

                        // Audio
                        "+AudioContext",
                        "+AudioSampleRate",

                        // WebGL
                        "+WebGLRenderCapability",
                        "+WebGLRenderInfo",

                        // Network
                        "+NetworkConnection",

                        // Media / sensors
                        "+MediaDevices",
                        "+MediaCapabilities",
                        "+DeviceSensors",

                        // Navigator / User-Agent
                        "+NavigatorAppVersion",
                        "+NavigatorOscpu",
                        "+NavigatorPlatform",
                        "+NavigatorUserAgent",
                        "+HttpUserAgent"

                    ).joinToString(
                        ","
                    )
                )


            /*
             * Math fdlibm :
             * réduit certaines différences liées
             * à la plateforme.
             */
            settings
                .setFdlibmMathEnabled(
                    true
                )


            /*
             * Global Privacy Control.
             */
            settings
                .setGlobalPrivacyControl(
                    true
                )


            // ====================================================
            // CREATE RUNTIME
            // ====================================================

            val created =
                GeckoRuntime.create(
                    appContext,
                    settings
                )


            // ====================================================
            // DEEP SHIELD WEBEXTENSION
            // ====================================================

            created
                .webExtensionController
                .ensureBuiltIn(
                    EXTENSION_LOCATION,
                    EXTENSION_ID
                )
                .accept(

                    { installedExtension ->

                        if (
                            installedExtension != null
                        ) {

                            extension =
                                installedExtension


                            Log.d(
                                TAG,
                                "Extension ready: " +
                                        installedExtension.id
                            )


                            val callbacks =
                                synchronized(
                                    extensionReadyCallbacks
                                ) {

                                    val copy =
                                        extensionReadyCallbacks
                                            .toList()


                                    extensionReadyCallbacks
                                        .clear()


                                    copy
                                }


                            callbacks.forEach {
                                    callback ->

                                try {

                                    callback(
                                        installedExtension
                                    )

                                } catch (
                                    e: Exception
                                ) {

                                    Log.e(
                                        TAG,
                                        "Extension ready callback failed",
                                        e
                                    )
                                }
                            }

                        } else {

                            Log.e(
                                TAG,
                                "ensureBuiltIn returned null"
                            )
                        }
                    },


                    { error ->

                        Log.e(
                            TAG,
                            "Extension installation failed",
                            error
                        )
                    }
                )


            runtime =
                created


            return created
        }
    }
}