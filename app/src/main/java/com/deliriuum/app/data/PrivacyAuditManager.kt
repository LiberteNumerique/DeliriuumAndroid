package com.deliriuum.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.json.JSONObject


class PrivacyAuditManager private constructor() {

    companion object {

        val shared =
            PrivacyAuditManager()
    }


    // ============================================================
    // STATE
    // ============================================================

    var state by mutableStateOf(
        PrivacyAuditState.empty()
    )
        private set


    /*
     * Dernier probe valide reçu depuis Gecko.
     *
     * Il est volontairement supprimé lorsque le tunnel
     * cesse d'être protégé afin de ne jamais réutiliser
     * automatiquement un ancien audit après reconnexion.
     */
    private var lastProbePayload:
            JSONObject? =
        null


    // ============================================================
    // EXPECTED EXIT PROFILE
    // ============================================================

    /*
     * V1 :
     * le serveur de sortie actuel est islandais.
     *
     * Lorsque Deliriuum proposera plusieurs pays,
     * TunnelManager / SessionConfig devra simplement appeler :
     *
     * PrivacyAuditManager.shared
     *     .setExpectedExitTimezone("...")
     */
    private var expectedExitTimezone:
            String? =
        "Atlantic/Reykjavik"


    fun setExpectedExitTimezone(
        timezone: String?
    ) {

        expectedExitTimezone =
            timezone

        rebuildState()
    }


    // ============================================================
    // COROUTINES
    // ============================================================

    private val scope =
        CoroutineScope(
            SupervisorJob() +
                    Dispatchers.Main
        )


    private var tunnelObserverJob:
            Job? =
        null


    private var tunnelObserverStarted =
        false


    // ============================================================
    // UPDATE FROM PROBE
    // ============================================================

    fun updateFromProbe(
        payload: JSONObject
    ) {

        /*
         * On démarre l'observation du tunnel au premier audit.
         */
        ensureTunnelObserver()


        /*
         * Le probe ne doit être considéré valable
         * que si Deliriuum est réellement protégé.
         */
        val tunnelManager =
            TunnelManager.shared


        if (
            !tunnelManager.isProtected
        ) {

            lastProbePayload =
                null

            rebuildState()

            return
        }


        /*
         * Copie défensive :
         * on évite de conserver une référence susceptible
         * d'être modifiée ailleurs.
         */
        lastProbePayload =
            JSONObject(
                payload.toString()
            )


        rebuildState()
    }


    // ============================================================
    // TUNNEL OBSERVER
    // ============================================================

    private fun ensureTunnelObserver() {

        if (tunnelObserverStarted) {
            return
        }

        tunnelObserverStarted = true

        val tunnelManager =
            TunnelManager.shared

        tunnelObserverJob =
            scope.launch {

                snapshotFlow {

                    Triple(
                        tunnelManager.status,
                        tunnelManager.tunnelReachable,
                        tunnelManager.isProtected
                    )
                }
                    .distinctUntilChanged()
                    .collect {
                            (status, reachable, protected) ->

                        android.util.Log.d(
                            "PrivacyAudit",
                            "TUNNEL OBSERVER status=$status " +
                                    "reachable=$reachable " +
                                    "protected=$protected"
                        )

                        if (!protected) {
                            lastProbePayload = null
                        }

                        rebuildState()

                        android.util.Log.d(
                            "PrivacyAudit",
                            "STATE AFTER TUNNEL CHANGE = $state"
                        )
                    }
            }
    }


    // ============================================================
    // REBUILD
    // ============================================================

    private fun rebuildState() {

        val tunnelManager =
            TunnelManager.shared


        val checks =
            mutableListOf<PrivacyCheck>()


        // ========================================================
        // VPN TUNNEL
        // ========================================================

        val tunnelConnected =
            tunnelManager.status ==
                    TunnelStatus.CONNECTED


        checks +=
            PrivacyCheck(
                id =
                    "vpn_tunnel",

                title =
                    "Tunnel VPN WireGuard",

                status =
                    if (
                        tunnelConnected
                    ) {

                        PrivacyCheckStatus.PROTECTED

                    } else {

                        PrivacyCheckStatus.EXPOSED
                    },

                detail =
                    if (
                        tunnelConnected
                    ) {

                        "Le tunnel WireGuard est actif."

                    } else {

                        "Le tunnel WireGuard n'est pas actif."
                    }
            )


        // ========================================================
        // TUNNEL REACHABILITY
        // ========================================================

        checks +=
            PrivacyCheck(
                id =
                    "tunnel_reachability",

                title =
                    "Chemin réseau du tunnel",

                status =
                    when {

                        !tunnelConnected ->
                            PrivacyCheckStatus.NOT_TESTED

                        tunnelManager.tunnelReachable ->
                            PrivacyCheckStatus.PROTECTED

                        else ->
                            PrivacyCheckStatus.EXPOSED
                    },

                detail =
                    when {

                        !tunnelConnected ->
                            "Le tunnel est déconnecté."

                        tunnelManager.tunnelReachable ->
                            "Le watchdog confirme que le tunnel permet d'atteindre Internet."

                        else ->
                            "WireGuard est actif mais le chemin réseau ne répond plus."
                    }
            )


        // ========================================================
        // NO VALID WEB PROBE
        // ========================================================

        val payload =
            lastProbePayload


        /*
         * Si aucun probe actuel n'est disponible,
         * on publie uniquement l'état réseau.
         *
         * Cela évite d'afficher comme actuels les résultats
         * d'une ancienne session Gecko.
         */
        if (
            payload == null
        ) {

            val protectedCount =
                checks.count {
                    it.status ==
                            PrivacyCheckStatus.PROTECTED
                }


            val partialCount =
                checks.count {
                    it.status ==
                            PrivacyCheckStatus.PARTIAL
                }


            val exposedCount =
                checks.count {
                    it.status ==
                            PrivacyCheckStatus.EXPOSED
                }


            val notTestedCount =
                checks.count {
                    it.status ==
                            PrivacyCheckStatus.NOT_TESTED
                }


            state =
                PrivacyAuditState(
                    level =
                        if (
                            tunnelManager.isProtected
                        ) {

                            /*
                             * Tunnel OK mais audit navigateur
                             * pas encore reçu.
                             */
                            PrivacyProtectionLevel.REINFORCED

                        } else {

                            PrivacyProtectionLevel.LOW
                        },

                    protectedCount =
                        protectedCount,

                    partialCount =
                        partialCount,

                    exposedCount =
                        exposedCount,

                    notTestedCount =
                        notTestedCount,

                    checks =
                        checks
                )


            return
        }


        // ========================================================
        // NETWORK INFORMATION
        // ========================================================

        val networkInformation =
            payload.optJSONObject(
                "networkInformation"
            )


        if (
            networkInformation != null
        ) {

            val exposed =
                networkInformation.optBoolean(
                    "exposed",
                    true
                )


            checks +=
                PrivacyCheck(
                    id =
                        "network_information",

                    title =
                        "Informations réseau",

                    status =
                        if (
                            !exposed
                        ) {

                            PrivacyCheckStatus.PROTECTED

                        } else {

                            PrivacyCheckStatus.EXPOSED
                        },

                    detail =
                        if (
                            !exposed
                        ) {

                            "L'API Network Information n'est pas exposée."

                        } else {

                            "Le contenu Web peut accéder à des informations réseau."
                        }
                )

        } else {

            checks +=
                PrivacyCheck(
                    id =
                        "network_information",

                    title =
                        "Informations réseau",

                    status =
                        PrivacyCheckStatus.NOT_TESTED,

                    detail =
                        "Mesure indisponible."
                )
        }


        // ========================================================
        // WEBGL
        // ========================================================

        val webgl =
            payload.optJSONObject(
                "webgl"
            )


        if (
            webgl != null
        ) {

            val available =
                webgl.optBoolean(
                    "available",
                    false
                )


            if (
                !available
            ) {

                checks +=
                    PrivacyCheck(
                        id =
                            "webgl",

                        title =
                            "Identité graphique WebGL",

                        status =
                            PrivacyCheckStatus.PARTIAL,

                        detail =
                            "WebGL n'est pas disponible dans ce contexte."
                    )

            } else {

                val vendor =
                    webgl.optString(
                        "vendor",
                        ""
                    )


                val renderer =
                    webgl.optString(
                        "renderer",
                        ""
                    )


                val unmaskedVendor =
                    webgl.optString(
                        "unmaskedVendor",
                        ""
                    )


                val unmaskedRenderer =
                    webgl.optString(
                        "unmaskedRenderer",
                        ""
                    )


                val masked =
                    vendor.equals(
                        "Mozilla",
                        ignoreCase = true
                    ) &&
                            renderer.equals(
                                "Mozilla",
                                ignoreCase = true
                            ) &&
                            (
                                    unmaskedVendor.isBlank() ||
                                            unmaskedVendor.equals(
                                                "Mozilla",
                                                ignoreCase = true
                                            )
                                    ) &&
                            (
                                    unmaskedRenderer.isBlank() ||
                                            unmaskedRenderer.equals(
                                                "Mozilla",
                                                ignoreCase = true
                                            )
                                    )


                checks +=
                    PrivacyCheck(
                        id =
                            "webgl",

                        title =
                            "Identité graphique WebGL",

                        status =
                            if (
                                masked
                            ) {

                                PrivacyCheckStatus.PROTECTED

                            } else {

                                PrivacyCheckStatus.EXPOSED
                            },

                        detail =
                            if (
                                masked
                            ) {

                                "Le fabricant et le renderer matériels ne sont pas révélés."

                            } else {

                                "Des informations graphiques identifiantes restent visibles."
                            }
                    )
            }

        } else {

            checks +=
                PrivacyCheck(
                    id =
                        "webgl",

                    title =
                        "Identité graphique WebGL",

                    status =
                        PrivacyCheckStatus.NOT_TESTED,

                    detail =
                        "Mesure indisponible."
                )
        }


        // ========================================================
        // FONTS
        // ========================================================

        val fonts =
            payload.optJSONObject(
                "fonts"
            )


        if (
            fonts != null
        ) {

            val testedCount =
                fonts
                    .optJSONArray(
                        "testedCandidates"
                    )
                    ?.length()
                    ?: 0


            val canvasCount =
                fonts.optInt(
                    "canvasCount",
                    -1
                )


            val domCount =
                fonts.optInt(
                    "domCount",
                    -1
                )


            if (
                testedCount > 0 &&
                canvasCount >= 0 &&
                domCount >= 0
            ) {

                val detected =
                    maxOf(
                        canvasCount,
                        domCount
                    )


                val fontStatus =
                    when {

                        detected <= 2 ->
                            PrivacyCheckStatus.PROTECTED

                        detected <=
                                testedCount / 3 ->
                            PrivacyCheckStatus.PARTIAL

                        else ->
                            PrivacyCheckStatus.EXPOSED
                    }


                checks +=
                    PrivacyCheck(
                        id =
                            "fonts",

                        title =
                            "Polices détectables",

                        status =
                            fontStatus,

                        detail =
                            "$detected police(s) détectée(s) sur $testedCount testées."
                    )

            } else {

                checks +=
                    PrivacyCheck(
                        id =
                            "fonts",

                        title =
                            "Polices détectables",

                        status =
                            PrivacyCheckStatus.NOT_TESTED,

                        detail =
                            "Mesure incomplète."
                    )
            }

        } else {

            checks +=
                PrivacyCheck(
                    id =
                        "fonts",

                    title =
                        "Polices détectables",

                    status =
                        PrivacyCheckStatus.NOT_TESTED,

                    detail =
                        "Mesure indisponible."
                )
        }


        // ========================================================
        // MEDIA DEVICES
        // ========================================================

        val mediaDevices =
            payload.optJSONObject(
                "mediaDevices"
            )


        if (
            mediaDevices != null
        ) {

            val supported =
                mediaDevices.optBoolean(
                    "supported",
                    false
                )


            if (
                !supported
            ) {

                checks +=
                    PrivacyCheck(
                        id =
                            "media_devices",

                        title =
                            "Micro, caméra et périphériques",

                        status =
                            PrivacyCheckStatus.PARTIAL,

                        detail =
                            "L'API MediaDevices n'est pas disponible dans ce contexte."
                    )

            } else {

                val labelsExposed =
                    mediaDevices.optBoolean(
                        "labelsExposed",
                        false
                    )


                val deviceIdsExposed =
                    mediaDevices.optBoolean(
                        "deviceIdsExposed",
                        false
                    )


                val groupIdsExposed =
                    mediaDevices.optBoolean(
                        "groupIdsExposed",
                        false
                    )


                val exposed =
                    labelsExposed ||
                            deviceIdsExposed ||
                            groupIdsExposed


                checks +=
                    PrivacyCheck(
                        id =
                            "media_devices",

                        title =
                            "Micro, caméra et périphériques",

                        status =
                            if (
                                !exposed
                            ) {

                                PrivacyCheckStatus.PROTECTED

                            } else {

                                PrivacyCheckStatus.EXPOSED
                            },

                        detail =
                            if (
                                !exposed
                            ) {

                                "Les labels et identifiants des périphériques ne sont pas exposés."

                            } else {

                                "Certains identifiants de périphériques sont accessibles."
                            }
                    )
            }

        } else {

            checks +=
                PrivacyCheck(
                    id =
                        "media_devices",

                    title =
                        "Micro, caméra et périphériques",

                    status =
                        PrivacyCheckStatus.NOT_TESTED,

                    detail =
                        "Mesure indisponible."
                )
        }


        // ========================================================
        // WEBRTC
        // ========================================================

        val webrtc =
            payload.optJSONObject(
                "webrtc"
            )


        if (
            webrtc != null
        ) {

            val available =
                webrtc.optBoolean(
                    "available",
                    false
                )


            val candidateCount =
                webrtc.optInt(
                    "candidateCount",
                    0
                )


            val hasHostCandidate =
                webrtc.optBoolean(
                    "hasHostCandidate",
                    false
                )


            val hasSrflxCandidate =
                webrtc.optBoolean(
                    "hasSrflxCandidate",
                    false
                )


            if (
                !available
            ) {

                checks +=
                    PrivacyCheck(
                        id =
                            "webrtc",

                        title =
                            "WebRTC",

                        status =
                            PrivacyCheckStatus.PARTIAL,

                        detail =
                            "WebRTC n'est pas disponible."
                    )

            } else {

                val leakCandidate =
                    hasHostCandidate ||
                            hasSrflxCandidate


                val safe =
                    candidateCount == 0 &&
                            !leakCandidate


                checks +=
                    PrivacyCheck(
                        id =
                            "webrtc",

                        title =
                            "WebRTC",

                        status =
                            if (
                                safe
                            ) {

                                PrivacyCheckStatus.PROTECTED

                            } else {

                                PrivacyCheckStatus.EXPOSED
                            },

                        detail =
                            if (
                                safe
                            ) {

                                "Aucun candidat ICE n'a été exposé lors du test."

                            } else {

                                "$candidateCount candidat(s) ICE ont été observés."
                            }
                    )
            }

        } else {

            checks +=
                PrivacyCheck(
                    id =
                        "webrtc",

                    title =
                        "WebRTC",

                    status =
                        PrivacyCheckStatus.NOT_TESTED,

                    detail =
                        "Mesure indisponible."
                )
        }


        // ========================================================
        // DPR CONSISTENCY
        // ========================================================

        val screen =
            payload.optJSONObject(
                "screen"
            )


        if (
            screen != null
        ) {

            val dpr =
                screen.optDouble(
                    "devicePixelRatio",
                    -1.0
                )


            val cssResolution =
                screen.optJSONObject(
                    "cssResolution"
                )


            if (
                dpr > 0 &&
                cssResolution != null
            ) {

                val exactMatch =
                    when {

                        dpr == 1.0 ->
                            cssResolution.optBoolean(
                                "dppx1",
                                false
                            )

                        dpr == 2.0 ->
                            cssResolution.optBoolean(
                                "dppx2",
                                false
                            )

                        dpr == 3.0 ->
                            cssResolution.optBoolean(
                                "dppx3",
                                false
                            )

                        /*
                         * On ne prétend pas tester toutes
                         * les valeurs possibles pour l'instant.
                         */
                        else ->
                            false
                    }


                checks +=
                    PrivacyCheck(
                        id =
                            "dpr_consistency",

                        title =
                            "Cohérence de l'affichage",

                        status =
                            if (
                                exactMatch
                            ) {

                                PrivacyCheckStatus.PROTECTED

                            } else {

                                PrivacyCheckStatus.PARTIAL
                            },

                        detail =
                            if (
                                exactMatch
                            ) {

                                "Le DPR JavaScript est cohérent avec les media queries CSS."

                            } else {

                                "La cohérence DPR/CSS n'a pas pu être confirmée complètement."
                            }
                    )

            } else {

                checks +=
                    PrivacyCheck(
                        id =
                            "dpr_consistency",

                        title =
                            "Cohérence de l'affichage",

                        status =
                            PrivacyCheckStatus.NOT_TESTED,

                        detail =
                            "Mesure incomplète."
                    )
            }

        } else {

            checks +=
                PrivacyCheck(
                    id =
                        "dpr_consistency",

                    title =
                        "Cohérence de l'affichage",

                    status =
                        PrivacyCheckStatus.NOT_TESTED,

                    detail =
                        "Mesure indisponible."
                )
        }


        // ========================================================
        // TIMEZONE CONSISTENCY
        // ========================================================

        val timezone =
            payload.optJSONObject(
                "timezone"
            )


        val observedTimezone =
            timezone
                ?.optString(
                    "name",
                    ""
                )
                ?.takeIf {
                    it.isNotBlank()
                }


        val expectedTimezone =
            expectedExitTimezone


        when {

            observedTimezone == null -> {

                checks +=
                    PrivacyCheck(
                        id =
                            "timezone_consistency",

                        title =
                            "Cohérence du fuseau horaire",

                        status =
                            PrivacyCheckStatus.NOT_TESTED,

                        detail =
                            "Le fuseau horaire n'a pas pu être mesuré."
                    )
            }


            expectedTimezone == null -> {

                checks +=
                    PrivacyCheck(
                        id =
                            "timezone_consistency",

                        title =
                            "Cohérence du fuseau horaire",

                        status =
                            PrivacyCheckStatus.PARTIAL,

                        detail =
                            "Fuseau observé : $observedTimezone. Le profil de sortie attendu n'est pas connu."
                    )
            }


            observedTimezone.equals(
                expectedTimezone,
                ignoreCase = true
            ) -> {

                checks +=
                    PrivacyCheck(
                        id =
                            "timezone_consistency",

                        title =
                            "Cohérence du fuseau horaire",

                        status =
                            PrivacyCheckStatus.PROTECTED,

                        detail =
                            "Le fuseau horaire est cohérent avec la sortie VPN : $observedTimezone."
                    )
            }


            else -> {

                checks +=
                    PrivacyCheck(
                        id =
                            "timezone_consistency",

                        title =
                            "Cohérence du fuseau horaire",

                        status =
                            PrivacyCheckStatus.EXPOSED,

                        detail =
                            "Fuseau observé : $observedTimezone ; attendu : $expectedTimezone."
                    )
            }
        }


        // ========================================================
        // CANVAS
        // ========================================================

        val canvas =
            payload.optJSONObject(
                "canvas"
            )


        if (
            canvas != null
        ) {

            val available =
                canvas.optBoolean(
                    "available",
                    false
                )


            if (
                !available
            ) {

                checks +=
                    PrivacyCheck(
                        id =
                            "canvas",

                        title =
                            "Canvas",

                        status =
                            PrivacyCheckStatus.NOT_TESTED,

                        detail =
                            "Canvas n'est pas disponible."
                    )

            } else {

                val stable =
                    canvas.optBoolean(
                        "stable",
                        true
                    )


                val uniqueHashCount =
                    canvas.optInt(
                        "uniqueHashCount",
                        0
                    )


                checks +=
                    PrivacyCheck(
                        id =
                            "canvas",

                        title =
                            "Canvas",

                        status =
                            PrivacyCheckStatus.PARTIAL,

                        detail =
                            if (
                                stable
                            ) {

                                "Le Canvas est stable sur les lectures de ce test. Le hash seul ne permet pas de conclure sur la protection."

                            } else {

                                "$uniqueHashCount rendus distincts ont été observés. Le verdict reste volontairement partiel."
                            }
                    )
            }

        } else {

            checks +=
                PrivacyCheck(
                    id =
                        "canvas",

                    title =
                        "Canvas",

                    status =
                        PrivacyCheckStatus.NOT_TESTED,

                    detail =
                        "Mesure indisponible."
                )
        }


        // ========================================================
        // AUDIO
        // ========================================================

        val audio =
            payload.optJSONObject(
                "audio"
            )


        if (
            audio != null
        ) {

            val available =
                audio.optBoolean(
                    "available",
                    false
                )


            if (
                !available
            ) {

                checks +=
                    PrivacyCheck(
                        id =
                            "audio",

                        title =
                            "Empreinte audio",

                        status =
                            PrivacyCheckStatus.NOT_TESTED,

                        detail =
                            "AudioContext n'est pas disponible."
                    )

            } else {

                val sampleRate =
                    audio.optInt(
                        "sampleRate",
                        -1
                    )


                val baseLatency =
                    audio.optDouble(
                        "baseLatency",
                        -1.0
                    )


                val outputLatency =
                    audio.optDouble(
                        "outputLatency",
                        -1.0
                    )


                checks +=
                    PrivacyCheck(
                        id =
                            "audio",

                        title =
                            "Empreinte audio",

                        status =
                            PrivacyCheckStatus.PARTIAL,

                        detail =
                            "AudioContext mesuré : sampleRate=$sampleRate, baseLatency=$baseLatency, outputLatency=$outputLatency. " +
                                    "Ces valeurs seules ne suffisent pas à prouver une protection complète."
                    )
            }

        } else {

            checks +=
                PrivacyCheck(
                    id =
                        "audio",

                    title =
                        "Empreinte audio",

                    status =
                        PrivacyCheckStatus.NOT_TESTED,

                    detail =
                        "Mesure indisponible."
                )
        }


        // ========================================================
        // KNOWN OBSERVABLE LIMITS
        // ========================================================

        val navigator =
            payload.optJSONObject(
                "navigator"
            )


        if (
            navigator != null
        ) {

            val platform =
                navigator.optString(
                    "platform",
                    ""
                )


            if (
                platform.isNotBlank()
            ) {

                checks +=
                    PrivacyCheck(
                        id =
                            "platform",

                        title =
                            "Architecture navigateur",

                        status =
                            PrivacyCheckStatus.EXPOSED,

                        detail =
                            "navigator.platform reste observable : $platform"
                    )
            }


            val hardwareConcurrency =
                navigator.optInt(
                    "hardwareConcurrency",
                    -1
                )


            if (
                hardwareConcurrency > 0
            ) {

                checks +=
                    PrivacyCheck(
                        id =
                            "hardware_concurrency",

                        title =
                            "Nombre de cœurs",

                        status =
                            PrivacyCheckStatus.EXPOSED,

                        detail =
                            "navigator.hardwareConcurrency reste observable : $hardwareConcurrency"
                    )
            }


            val maxTouchPoints =
                navigator.optInt(
                    "maxTouchPoints",
                    -1
                )


            if (
                maxTouchPoints >= 0
            ) {

                checks +=
                    PrivacyCheck(
                        id =
                            "max_touch_points",

                        title =
                            "Capacités tactiles",

                        status =
                            PrivacyCheckStatus.EXPOSED,

                        detail =
                            "navigator.maxTouchPoints reste observable : $maxTouchPoints"
                    )
            }
        }


        // ========================================================
        // COUNTS
        // ========================================================

        val protectedCount =
            checks.count {
                it.status ==
                        PrivacyCheckStatus.PROTECTED
            }


        val partialCount =
            checks.count {
                it.status ==
                        PrivacyCheckStatus.PARTIAL
            }


        val exposedCount =
            checks.count {
                it.status ==
                        PrivacyCheckStatus.EXPOSED
            }


        val notTestedCount =
            checks.count {
                it.status ==
                        PrivacyCheckStatus.NOT_TESTED
            }


        // ========================================================
        // GLOBAL LEVEL
        // ========================================================

        /*
         * Protections Web réellement critiques.
         */
        val criticalBrowserIds =
            setOf(
                "network_information",
                "webgl",
                "media_devices",
                "webrtc"
            )


        val criticalBrowserChecks =
            checks.filter {
                it.id in
                        criticalBrowserIds
            }


        val criticalExposure =
            criticalBrowserChecks.any {
                it.status ==
                        PrivacyCheckStatus.EXPOSED
            }


        val allCriticalProtected =
            criticalBrowserChecks.size ==
                    criticalBrowserIds.size &&
                    criticalBrowserChecks.all {

                        it.status ==
                                PrivacyCheckStatus.PROTECTED
                    }


        val timezoneCheck =
            checks.firstOrNull {
                it.id ==
                        "timezone_consistency"
            }


        val level =
            when {

                /*
                 * Règle absolue :
                 * pas de tunnel réellement opérationnel
                 * = niveau faible.
                 */
                !tunnelManager.isProtected ->

                    PrivacyProtectionLevel.LOW


                /*
                 * Une fuite critique navigateur
                 * fait également tomber le niveau.
                 */
                criticalExposure ->

                    PrivacyProtectionLevel.LOW


                /*
                 * Tout le cœur est confirmé ET
                 * aucune incohérence timezone.
                 */
                allCriticalProtected &&
                        timezoneCheck?.status ==
                        PrivacyCheckStatus.PROTECTED ->

                    PrivacyProtectionLevel.HIGH


                /*
                 * Tunnel opérationnel mais audit
                 * partiel / cohérence imparfaite.
                 */
                else ->

                    PrivacyProtectionLevel.REINFORCED
            }


        state =
            PrivacyAuditState(
                level =
                    level,

                protectedCount =
                    protectedCount,

                partialCount =
                    partialCount,

                exposedCount =
                    exposedCount,

                notTestedCount =
                    notTestedCount,

                checks =
                    checks
            )
    }
}
