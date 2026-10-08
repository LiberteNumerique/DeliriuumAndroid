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
import com.deliriuum.app.R
import com.deliriuum.app.i18n.AppStrings


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
     * Résultat brut mesuré AVANT l'activation de Deliriuum.
     *
     * Cette valeur est conservée lorsque le tunnel s'active afin
     * de pouvoir expliquer ensuite ce qui a réellement changé.
     */
    var baselinePayload by mutableStateOf<JSONObject?>(
        null
    )
        private set


    /*
     * Résultat brut mesuré AVEC Deliriuum.
     */
    var protectedPayload by mutableStateOf<JSONObject?>(
        null
    )
        private set


    /*
     * Indique si le probe actuellement affiché correspond bien
     * à l'état courant du tunnel.
     */
    var currentAuditReady by mutableStateOf(
        false
    )
        private set


    private var lastProbeWasProtected:
            Boolean? =
        null


    val baselineReady: Boolean
        get() =
            baselinePayload != null


    val protectedReady: Boolean
        get() =
            protectedPayload != null


    /*
     * Vrai dès qu'un runtime Gecko protégé a été demandé dans le
     * process principal.
     *
     * Une baseline Gecko standard ne doit plus être lancée ensuite :
     * les deux moteurs Gecko simultanés se disputent les processus
     * enfants GeckoView et peuvent tuer le process principal.
     */
    var protectedRuntimeEverStarted by mutableStateOf(
        false
    )
        private set


    fun markProtectedRuntimeStarted() {

        protectedRuntimeEverStarted =
            true
    }


    /*
     * Dernier probe valide affiché.
     *
     * La baseline AVANT est conservée pendant toute la vie
     * du process principal. Lors d'une déconnexion, on la
     * réutilise pour revenir à l'état AVANT sans relancer un
     * second GeckoRuntime baseline.
     */
    private var lastProbePayload:
            JSONObject? =
        null


    // ============================================================
    // RESTORE PERSISTENT CACHE
    // ============================================================

    fun restoreCachedSnapshots(
        baseline: JSONObject?,
        protected: JSONObject?
    ) {

        ensureTunnelObserver()

        if (
            baselinePayload == null &&
            baseline != null
        ) {

            baselinePayload =
                JSONObject(
                    baseline.toString()
                )

            android.util.Log.d(
                "PrivacyAudit",
                "BASELINE RESTORED FROM CACHE"
            )
        }

        if (
            protectedPayload == null &&
            protected != null
        ) {

            protectedPayload =
                JSONObject(
                    protected.toString()
                )

            protectedRuntimeEverStarted =
                true

            android.util.Log.d(
                "PrivacyAudit",
                "PROTECTED AUDIT RESTORED FROM CACHE"
            )
        }

        val currentProtected =
            TunnelManager.shared.isProtected

        val currentSnapshot =
            if (currentProtected) {
                protectedPayload
            } else {
                baselinePayload
            }

        lastProbePayload =
            currentSnapshot?.let {
                JSONObject(
                    it.toString()
                )
            }

        lastProbeWasProtected =
            if (currentSnapshot != null) {
                currentProtected
            } else {
                null
            }

        currentAuditReady =
            currentSnapshot != null

        rebuildState()
    }


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
    // UPDATE BASELINE — AVANT DELIRIIUM
    // ============================================================

    fun updateBaselineFromProbe(
        payload: JSONObject
    ) {

        ensureTunnelObserver()

        /*
         * UNE SEULE baseline par vie du process principal.
         *
         * L'audit baseline est lancé lorsque le tunnel est OFF. Si
         * l'utilisateur active très vite Deliriuum pendant que Gecko
         * termine ses mesures, on conserve quand même le résultat :
         * on ne relance surtout pas une deuxième baseline.
         */
        if (baselinePayload != null) {
            android.util.Log.d(
                "PrivacyAudit",
                "BASELINE already stored; duplicate ignored"
            )
            return
        }

        val copy =
            JSONObject(
                payload.toString()
            )

        baselinePayload =
            JSONObject(
                copy.toString()
            )

        PrivacyAuditCache
            .saveBaseline(
                copy
            )

        android.util.Log.d(
            "PrivacyAudit",
            "BASELINE STORED ONCE"
        )

        /*
         * Si le tunnel est encore OFF, la baseline devient l'audit
         * courant affiché. S'il est déjà ON, on la garde simplement
         * comme référence AVANT pendant que l'audit protégé arrive.
         */
        if (!TunnelManager.shared.isProtected) {
            lastProbePayload = copy
            lastProbeWasProtected = false
            currentAuditReady = true
        }

        rebuildState()
    }


    // ============================================================
    // UPDATE FROM PROBE — AVEC DELIRIIUM
    // ============================================================

    fun updateFromProbe(
        payload: JSONObject
    ) {

        ensureTunnelObserver()

        protectedRuntimeEverStarted =
            true

        val probeVersion =
            payload
                .optJSONObject("meta")
                ?.optString("version", "")
                .orEmpty()

        if (probeVersion != "11.1.0") {

            android.util.Log.d(
                "PrivacyAudit",
                "OLD PROTECTED PROBE IGNORED version=$probeVersion"
            )

            return
        }

        if (!TunnelManager.shared.isProtected) {
            return
        }

        /*
         * UNE SEULE mesure APRÈS. Le content-script peut envoyer plus
         * d'un message pendant les navigations/recompositions ; les
         * suivants sont volontairement ignorés.
         */
        if (protectedPayload != null) {
            android.util.Log.d(
                "PrivacyAudit",
                "PROTECTED AUDIT already stored; duplicate ignored"
            )
            return
        }

        val copy =
            JSONObject(
                payload.toString()
            )

        protectedPayload =
            JSONObject(
                copy.toString()
            )

        PrivacyAuditCache
            .saveProtected(
                copy
            )

        android.util.Log.d(
            "PrivacyAudit",
            "PROTECTED AUDIT STORED ONCE"
        )

        lastProbePayload =
            copy

        lastProbeWasProtected =
            true

        currentAuditReady =
            true

        rebuildState()
    }


    // ============================================================
    // TUNNEL OBSERVER
    // ============================================================

    private fun ensureTunnelObserver() {

        if (tunnelObserverStarted) {
            return
        }

        tunnelObserverStarted =
            true

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

                        /*
                         * AVANT et APRÈS sont des snapshots immuables.
                         * On ne les efface jamais lors d'une connexion,
                         * déconnexion ou recomposition.
                         *
                         * On change seulement le snapshot utilisé comme
                         * audit courant par la carte.
                         */
                        val storedForCurrentMode =
                            if (protected) {
                                protectedPayload
                            } else {
                                baselinePayload
                            }

                        lastProbePayload =
                            storedForCurrentMode?.let {
                                JSONObject(it.toString())
                            }

                        lastProbeWasProtected =
                            if (storedForCurrentMode != null) {
                                protected
                            } else {
                                null
                            }

                        currentAuditReady =
                            storedForCurrentMode != null

                        rebuildState()
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
                    AppStrings.get(R.string.privacy_audit_manager_tunnel_vpn_wireguard),

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

                        AppStrings.get(R.string.privacy_audit_manager_le_tunnel_wireguard_est_actif)

                    } else {

                        AppStrings.get(R.string.privacy_audit_manager_le_tunnel_wireguard_n_est_pas_actif)
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
                    AppStrings.get(R.string.privacy_audit_manager_chemin_reseau_du_tunnel),

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
                            AppStrings.get(R.string.privacy_audit_manager_le_tunnel_est_deconnecte)

                        tunnelManager.tunnelReachable ->
                            AppStrings.get(R.string.privacy_audit_manager_le_watchdog_confirme_que_le_tunnel_permet_d_atteindre_in)

                        else ->
                            AppStrings.get(R.string.privacy_audit_manager_wireguard_est_actif_mais_le_chemin_reseau_ne_repond_plus)
                    }
            )


        // ========================================================
        // NO VALID WEB PROBE
        // ========================================================

        val currentProtectedMode =
            tunnelManager.isProtected

        val payload =
            if (
                lastProbeWasProtected ==
                currentProtectedMode
            ) {
                lastProbePayload
            } else {
                null
            }


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

            currentAuditReady =
                false

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
                        AppStrings.get(R.string.privacy_audit_manager_informations_reseau),

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

                            AppStrings.get(R.string.privacy_audit_manager_l_api_network_information_n_est_pas_exposee)

                        } else {

                            AppStrings.get(R.string.privacy_audit_manager_le_contenu_web_peut_acceder_a_des_informations_reseau)
                        }
                )

        } else {

            checks +=
                PrivacyCheck(
                    id =
                        "network_information",

                    title =
                        AppStrings.get(R.string.privacy_audit_manager_informations_reseau),

                    status =
                        PrivacyCheckStatus.NOT_TESTED,

                    detail =
                        AppStrings.get(R.string.privacy_audit_manager_mesure_indisponible)
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
                            AppStrings.get(R.string.privacy_audit_manager_identite_graphique_webgl),

                        status =
                            PrivacyCheckStatus.PARTIAL,

                        detail =
                            AppStrings.get(R.string.privacy_audit_manager_webgl_n_est_pas_disponible_dans_ce_contexte)
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
                            AppStrings.get(R.string.privacy_audit_manager_identite_graphique_webgl),

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

                                AppStrings.get(R.string.privacy_audit_manager_le_fabricant_et_le_renderer_materiels_ne_sont_pas_revele)

                            } else {

                                AppStrings.get(R.string.privacy_audit_manager_des_informations_graphiques_identifiantes_restent_visibl)
                            }
                    )
            }

        } else {

            checks +=
                PrivacyCheck(
                    id =
                        "webgl",

                    title =
                        AppStrings.get(R.string.privacy_audit_manager_identite_graphique_webgl),

                    status =
                        PrivacyCheckStatus.NOT_TESTED,

                    detail =
                        AppStrings.get(R.string.privacy_audit_manager_mesure_indisponible)
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
                            AppStrings.get(R.string.privacy_audit_manager_polices_detectables),

                        status =
                            fontStatus,

                        detail =
                            AppStrings.quantity(R.plurals.privacy_audit_fonts_detected, detected, detected, testedCount)
                    )

            } else {

                checks +=
                    PrivacyCheck(
                        id =
                            "fonts",

                        title =
                            AppStrings.get(R.string.privacy_audit_manager_polices_detectables),

                        status =
                            PrivacyCheckStatus.NOT_TESTED,

                        detail =
                            AppStrings.get(R.string.privacy_audit_manager_mesure_incomplete)
                    )
            }

        } else {

            checks +=
                PrivacyCheck(
                    id =
                        "fonts",

                    title =
                        AppStrings.get(R.string.privacy_audit_manager_polices_detectables),

                    status =
                        PrivacyCheckStatus.NOT_TESTED,

                    detail =
                        AppStrings.get(R.string.privacy_audit_manager_mesure_indisponible)
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
                            AppStrings.get(R.string.privacy_audit_manager_micro_camera_et_peripheriques),

                        status =
                            PrivacyCheckStatus.PARTIAL,

                        detail =
                            AppStrings.get(R.string.privacy_audit_manager_l_api_mediadevices_n_est_pas_disponible_dans_ce_contexte)
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
                            AppStrings.get(R.string.privacy_audit_manager_micro_camera_et_peripheriques),

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

                                AppStrings.get(R.string.privacy_audit_manager_les_labels_et_identifiants_des_peripheriques_ne_sont_pas)

                            } else {

                                AppStrings.get(R.string.privacy_audit_manager_certains_identifiants_de_peripheriques_sont_accessibles)
                            }
                    )
            }

        } else {

            checks +=
                PrivacyCheck(
                    id =
                        "media_devices",

                    title =
                        AppStrings.get(R.string.privacy_audit_manager_micro_camera_et_peripheriques),

                    status =
                        PrivacyCheckStatus.NOT_TESTED,

                    detail =
                        AppStrings.get(R.string.privacy_audit_manager_mesure_indisponible)
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
                            AppStrings.get(R.string.privacy_audit_manager_webrtc_n_est_pas_disponible)
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

                                AppStrings.get(R.string.privacy_audit_manager_aucun_candidat_ice_n_a_ete_expose_lors_du_test)

                            } else {

                                AppStrings.quantity(R.plurals.privacy_audit_ice_candidates_observed, candidateCount, candidateCount)
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
                        AppStrings.get(R.string.privacy_audit_manager_mesure_indisponible)
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
                            AppStrings.get(R.string.privacy_audit_manager_coherence_de_l_affichage),

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

                                AppStrings.get(R.string.privacy_audit_manager_le_dpr_javascript_est_coherent_avec_les_media_queries_cs)

                            } else {

                                AppStrings.get(R.string.privacy_audit_manager_la_coherence_dpr_css_n_a_pas_pu_etre_confirmee_completem)
                            }
                    )

            } else {

                checks +=
                    PrivacyCheck(
                        id =
                            "dpr_consistency",

                        title =
                            AppStrings.get(R.string.privacy_audit_manager_coherence_de_l_affichage),

                        status =
                            PrivacyCheckStatus.NOT_TESTED,

                        detail =
                            AppStrings.get(R.string.privacy_audit_manager_mesure_incomplete)
                    )
            }

        } else {

            checks +=
                PrivacyCheck(
                    id =
                        "dpr_consistency",

                    title =
                        AppStrings.get(R.string.privacy_audit_manager_coherence_de_l_affichage),

                    status =
                        PrivacyCheckStatus.NOT_TESTED,

                    detail =
                        AppStrings.get(R.string.privacy_audit_manager_mesure_indisponible)
                )
        }


        // ========================================================
        // TIMEZONE CONSISTENCY
        // ========================================================
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

        val observedOffsetMinutes =
            timezone
                ?.optInt(
                    "offsetMinutes",
                    Int.MIN_VALUE
                )
                ?: Int.MIN_VALUE

        val expectedTimezone =
            expectedExitTimezone
        android.util.Log.d(
            "PrivacyAuditTZ",
            "observedTimezone=$observedTimezone " +
                    "offsetMinutes=$observedOffsetMinutes " +
                    "expectedTimezone=$expectedTimezone " +
                    "protectedMode=$currentProtectedMode"
        )

        /*
         * Resist Fingerprinting ne cherche pas à reproduire le nom
         * exact du fuseau du serveur VPN.
         *
         * Mozilla normalise volontairement le fuseau vers UTC /
         * un profil islandais. Pour notre sortie actuelle islandaise,
         * ces valeurs sont cohérentes et surtout non spécifiques au
         * fuseau réel de l'utilisateur.
         */
        val rfpTimezoneNormalized =
            observedOffsetMinutes == 0 &&
                    (
                            observedTimezone.equals("UTC", ignoreCase = true) ||
                                    observedTimezone.equals("Etc/UTC", ignoreCase = true) ||
                                    observedTimezone.equals("GMT", ignoreCase = true) ||
                                    observedTimezone.equals("Atlantic/Reykjavik", ignoreCase = true) ||
                                    observedTimezone.equals("Iceland", ignoreCase = true)
                            )


        when {

            observedTimezone == null -> {

                checks +=
                    PrivacyCheck(
                        id =
                            "timezone_consistency",

                        title =
                            AppStrings.get(
                                R.string.privacy_audit_manager_coherence_du_fuseau_horaire
                            ),

                        status =
                            PrivacyCheckStatus.NOT_TESTED,

                        detail =
                            AppStrings.get(
                                R.string.privacy_audit_manager_le_fuseau_horaire_n_a_pas_pu_etre_mesure
                            )
                    )
            }


            currentProtectedMode &&
                    rfpTimezoneNormalized -> {

                checks +=
                    PrivacyCheck(
                        id =
                            "timezone_consistency",

                        title =
                            AppStrings.get(
                                R.string.privacy_audit_manager_coherence_du_fuseau_horaire
                            ),

                        status =
                            PrivacyCheckStatus.PROTECTED,

                        detail =
                            AppStrings.get(
                                R.string.privacy_audit_manager_timezone_rfp_normalized,
                                observedTimezone
                            )
                    )
            }


            expectedTimezone == null -> {

                checks +=
                    PrivacyCheck(
                        id =
                            "timezone_consistency",

                        title =
                            AppStrings.get(
                                R.string.privacy_audit_manager_coherence_du_fuseau_horaire
                            ),

                        status =
                            PrivacyCheckStatus.PARTIAL,

                        detail =
                            AppStrings.get(
                                R.string.privacy_audit_manager_fuseau_observe_le_profil_de_sortie_attendu_n_est_pas_con,
                                observedTimezone
                            )
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
                            AppStrings.get(
                                R.string.privacy_audit_manager_coherence_du_fuseau_horaire
                            ),

                        status =
                            PrivacyCheckStatus.PROTECTED,

                        detail =
                            AppStrings.get(
                                R.string.privacy_audit_manager_le_fuseau_horaire_est_coherent_avec_la_sortie_vpn,
                                observedTimezone
                            )
                    )
            }


            else -> {

                checks +=
                    PrivacyCheck(
                        id =
                            "timezone_consistency",

                        title =
                            AppStrings.get(
                                R.string.privacy_audit_manager_coherence_du_fuseau_horaire
                            ),

                        status =
                            PrivacyCheckStatus.EXPOSED,

                        detail =
                            AppStrings.get(
                                R.string.privacy_audit_manager_fuseau_observe_attendu,
                                observedTimezone,
                                expectedTimezone
                            )
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
                            AppStrings.get(
                                R.string.privacy_audit_manager_canvas_n_est_pas_disponible
                            )
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

                /*
                 * Le fait que plusieurs lectures dans le même contexte
                 * retournent le même résultat ne prouve PAS l'absence
                 * de randomisation.
                 *
                 * On dispose justement d'une vraie baseline AVANT
                 * Deep Shield : on compare donc le rendu exposé avant
                 * et après.
                 */
                val currentCanvasHash =
                    canvas
                        .optJSONArray(
                            "hashes"
                        )
                        ?.optString(
                            0,
                            ""
                        )
                        .orEmpty()

                val baselineCanvasHash =
                    baselinePayload
                        ?.optJSONObject(
                            "canvas"
                        )
                        ?.optJSONArray(
                            "hashes"
                        )
                        ?.optString(
                            0,
                            ""
                        )
                        .orEmpty()

                val canvasProtectionObserved =
                    currentProtectedMode &&
                            currentCanvasHash.isNotBlank() &&
                            baselineCanvasHash.isNotBlank() &&
                            currentCanvasHash !=
                            baselineCanvasHash

                checks +=
                    PrivacyCheck(
                        id =
                            "canvas",

                        title =
                            "Canvas",

                        status =
                            if (
                                canvasProtectionObserved
                            ) {
                                PrivacyCheckStatus.PROTECTED
                            } else {
                                PrivacyCheckStatus.PARTIAL
                            },

                        detail =
                            when {

                                canvasProtectionObserved ->

                                    AppStrings.get(
                                        R.string.privacy_audit_manager_canvas_differs_from_baseline
                                    )


                                stable ->

                                    AppStrings.get(
                                        R.string.privacy_audit_manager_le_canvas_est_stable_sur_les_lectures_de_ce_test_le_hash
                                    )


                                else ->

                                    AppStrings.quantity(
                                        R.plurals.privacy_audit_canvas_distinct_renders,
                                        uniqueHashCount,
                                        uniqueHashCount
                                    )
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
                        AppStrings.get(
                            R.string.privacy_audit_manager_mesure_indisponible
                        )
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
                            AppStrings.get(
                                R.string.privacy_audit_manager_empreinte_audio
                            ),

                        status =
                            PrivacyCheckStatus.NOT_TESTED,

                        detail =
                            AppStrings.get(
                                R.string.privacy_audit_manager_audiocontext_n_est_pas_disponible
                            )
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

                val maxChannelCount =
                    audio.optInt(
                        "maxChannelCount",
                        -1
                    )


                /*
                 * On ne déclare PAS l'audio protégé simplement parce
                 * qu'une valeur particulière est présente.
                 *
                 * On compare la surface audio AVANT et APRES
                 * l'activation de Deep Shield.
                 *
                 * Dans le profil protégé actuel Gecko expose aussi
                 * une surface de sortie stéréo normalisée.
                 */
                val baselineAudio =
                    baselinePayload
                        ?.optJSONObject(
                            "audio"
                        )

                val baselineSampleRate =
                    baselineAudio
                        ?.optInt(
                            "sampleRate",
                            -1
                        )
                        ?: -1


                val sampleRateChanged =
                    currentProtectedMode &&
                            baselineSampleRate > 0 &&
                            sampleRate > 0 &&
                            baselineSampleRate !=
                            sampleRate


                val normalizedChannelSurface =
                    maxChannelCount == 2


                val audioProtectionObserved =
                    sampleRateChanged


                android.util.Log.d(
                    "PrivacyAuditAudio",
                    "baselineSampleRate=$baselineSampleRate " +
                            "protectedSampleRate=$sampleRate " +
                            "maxChannelCount=$maxChannelCount " +
                            "protected=$audioProtectionObserved"
                )


                checks +=
                    PrivacyCheck(
                        id =
                            "audio",

                        title =
                            AppStrings.get(
                                R.string.privacy_audit_manager_empreinte_audio
                            ),

                        status =
                            if (
                                audioProtectionObserved
                            ) {

                                PrivacyCheckStatus.PROTECTED

                            } else {

                                PrivacyCheckStatus.PARTIAL
                            },

                        detail =
                            if (
                                audioProtectionObserved
                            ) {

                                "sampleRate=$baselineSampleRate → $sampleRate · " +
                                        "maxChannelCount=$maxChannelCount"

                            } else {

                                AppStrings.get(
                                    R.string.privacy_audit_manager_audiocontext_mesure_samplerate_baselatency_outputlatency,
                                    sampleRate,
                                    baseLatency,
                                    outputLatency
                                )
                            }
                    )
            }

        } else {

            checks +=
                PrivacyCheck(
                    id =
                        "audio",

                    title =
                        AppStrings.get(
                            R.string.privacy_audit_manager_empreinte_audio
                        ),

                    status =
                        PrivacyCheckStatus.NOT_TESTED,

                    detail =
                        AppStrings.get(
                            R.string.privacy_audit_manager_mesure_indisponible
                        )
                )
        }


        // ========================================================
        // NAVIGATOR / FINGERPRINTING
        // ========================================================

        val navigator =
            payload.optJSONObject(
                "navigator"
            )

        if (
            navigator != null
        ) {

            // ----------------------------------------------------
            // PLATFORM
            // ----------------------------------------------------

            val platform =
                navigator.optString(
                    "platform",
                    ""
                )

            if (
                platform.isNotBlank()
            ) {

                /*
                 * Gecko Android renvoie volontairement cette valeur
                 * générique. Elle ne révèle pas l'architecture réelle
                 * de l'appareil : elle est identique sur Android.
                 */
                val genericAndroidPlatform =
                    platform.equals(
                        "Linux armv81",
                        ignoreCase = true
                    )

                checks +=
                    PrivacyCheck(
                        id =
                            "platform",

                        title =
                            AppStrings.get(
                                R.string.privacy_audit_manager_architecture_navigateur
                            ),

                        status =
                            if (
                                genericAndroidPlatform
                            ) {
                                PrivacyCheckStatus.PROTECTED
                            } else {
                                PrivacyCheckStatus.EXPOSED
                            },

                        detail =
                            if (
                                genericAndroidPlatform
                            ) {
                                AppStrings.get(
                                    R.string.privacy_audit_manager_platform_generic_android,
                                    platform
                                )
                            } else {
                                AppStrings.get(
                                    R.string.privacy_audit_manager_navigator_platform_reste_observable,
                                    platform
                                )
                            }
                    )
            }


            // ----------------------------------------------------
            // HARDWARE CONCURRENCY
            // ----------------------------------------------------

            val hardwareConcurrency =
                navigator.optInt(
                    "hardwareConcurrency",
                    -1
                )

            if (
                hardwareConcurrency > 0
            ) {

                /*
                 * Avec Resist Fingerprinting actif, Gecko normalise
                 * hardwareConcurrency à 4 sur les plateformes
                 * non-macOS, donc notamment Android.
                 */
                val normalizedHardwareConcurrency =
                    currentProtectedMode &&
                            hardwareConcurrency == 4

                checks +=
                    PrivacyCheck(
                        id =
                            "hardware_concurrency",

                        title =
                            AppStrings.get(
                                R.string.privacy_audit_manager_nombre_de_c_urs
                            ),

                        status =
                            if (
                                normalizedHardwareConcurrency
                            ) {
                                PrivacyCheckStatus.PROTECTED
                            } else {
                                PrivacyCheckStatus.EXPOSED
                            },

                        detail =
                            if (
                                normalizedHardwareConcurrency
                            ) {
                                AppStrings.get(
                                    R.string.privacy_audit_manager_hardwareconcurrency_normalized,
                                    hardwareConcurrency
                                )
                            } else {
                                AppStrings.get(
                                    R.string.privacy_audit_manager_navigator_hardwareconcurrency_reste_observable,
                                    hardwareConcurrency
                                )
                            }
                    )
            }


            // ----------------------------------------------------
            // MAX TOUCH POINTS
            // ----------------------------------------------------

            val maxTouchPoints =
                navigator.optInt(
                    "maxTouchPoints",
                    -1
                )

            if (
                maxTouchPoints >= 0
            ) {

                /*
                 * Le mode RFP complet utilise la cible
                 * MaxTouchPoints et renvoie 0 au contenu Web.
                 *
                 * À ne pas confondre avec le FPP standard qui peut
                 * seulement regrouper les valeurs en cohortes.
                 */
                val normalizedTouchPoints =
                    currentProtectedMode &&
                            maxTouchPoints == 5

                checks +=
                    PrivacyCheck(
                        id =
                            "max_touch_points",

                        title =
                            AppStrings.get(
                                R.string.privacy_audit_manager_capacites_tactiles
                            ),

                        status =
                            if (
                                normalizedTouchPoints
                            ) {
                                PrivacyCheckStatus.PROTECTED
                            } else {
                                PrivacyCheckStatus.EXPOSED
                            },

                        detail =
                            if (
                                normalizedTouchPoints
                            ) {
                                AppStrings.get(
                                    R.string.privacy_audit_manager_maxtouchpoints_normalized,
                                    maxTouchPoints
                                )
                            } else {
                                AppStrings.get(
                                    R.string.privacy_audit_manager_navigator_maxtouchpoints_reste_observable,
                                    maxTouchPoints
                                )
                            }
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


        currentAuditReady =
            true


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
