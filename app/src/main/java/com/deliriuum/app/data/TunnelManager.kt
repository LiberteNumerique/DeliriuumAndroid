package com.deliriuum.app.data

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket


enum class TunnelStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING
}


class TunnelManager private constructor(
    private val context: Context
) : Tunnel {


    companion object {

        @Volatile
        private var INSTANCE: TunnelManager? = null


        // ========================================================
        // WATCHDOG
        // ========================================================

        private const val TAG =
            "TunnelWatchdog"


        /*
         * Vérification toutes les 3 secondes.
         */
        private const val HEALTH_CHECK_INTERVAL_MS =
            3_000L


        /*
         * Timeout d'une tentative TCP.
         */
        private const val HEALTH_CHECK_TIMEOUT_MS =
            2_000


        /*
         * Deux échecs consécutifs suffisent.
         *
         * Cela donne une détection typique en environ
         * 6 à 8 secondes sans réagir à une micro-coupure unique.
         */
        private const val HEALTH_CHECK_FAILURE_THRESHOLD =
            2


        /*
         * On teste directement des IP publiques sur TCP/443.
         *
         * Aucun DNS.
         * Aucun cache HTTP.
         * Aucun redirect.
         */
        private const val HEALTH_CHECK_HOST_PRIMARY =
            "1.1.1.1"

        private const val HEALTH_CHECK_HOST_FALLBACK =
            "8.8.8.8"

        private const val HEALTH_CHECK_PORT =
            443


        fun initialize(
            context: Context
        ): TunnelManager {

            return INSTANCE
                ?: synchronized(this) {

                    INSTANCE
                        ?: TunnelManager(
                            context.applicationContext
                        ).also {

                            INSTANCE = it
                        }
                }
        }


        val shared: TunnelManager
            get() =
                INSTANCE
                    ?: throw IllegalStateException(
                        "TunnelManager doit être initialisé dans MainActivity"
                    )
    }


    // ============================================================
    // BACKEND
    // ============================================================

    private val backend =
        GoBackend(context)


    private val prefs =
        context.getSharedPreferences(
            "deliriuum_secure_prefs",
            Context.MODE_PRIVATE
        )


    private val scope =
        CoroutineScope(
            SupervisorJob() +
                    Dispatchers.Main
        )


    // ============================================================
    // SESSION MASTER
    // ============================================================

    private var currentSessionId: String?
        get() =
            prefs.getString(
                "current_session_id",
                null
            )

        set(value) {

            prefs.edit {

                if (value == null) {

                    remove(
                        "current_session_id"
                    )

                } else {

                    putString(
                        "current_session_id",
                        value
                    )
                }
            }
        }


    // ============================================================
    // RECONNEXION
    // ============================================================

    private var shouldAutoReconnect =
        false

    private var manualDisconnect =
        false

    private var reconnectInProgress =
        false


    // ============================================================
    // WATCHDOG
    // ============================================================

    private var watchdogJob: Job? =
        null


    private var consecutiveHealthCheckFailures =
        0


    /*
     * Etat observable Compose.
     *
     * true :
     * le chemin réseau du tunnel répond.
     *
     * false :
     * le tunnel peut éventuellement rester marqué UP
     * par WireGuard, mais il ne permet plus d'atteindre
     * Internet.
     */
    var tunnelReachable by mutableStateOf(false)
        private set


    // ============================================================
    // ETAT TUNNEL
    // ============================================================

    var status by mutableStateOf(
        TunnelStatus.DISCONNECTED
    )
        private set


    var lastErrorMessage by mutableStateOf<String?>(
        null
    )
        private set


    /*
     * Conflit spécifique signalé par le master lorsqu'une
     * ancienne session VPN est encore active pour ce device.
     *
     * Ce cas n'est pas affiché comme une erreur technique brute :
     * HomeView le transforme en dialogue pédagogique.
     */
    var sessionConflictMessage by mutableStateOf<String?>(
        null
    )
        private set


    fun clearSessionConflict() {
        sessionConflictMessage =
            null
    }


    var autoDisconnected by mutableStateOf(false)
        private set


    // ============================================================
    // KILL-SWITCH ANDROID
    // ============================================================

    var alwaysOnVpnEnabled by mutableStateOf<Boolean?>(
        null
    )
        private set


    var lockdownEnabled by mutableStateOf<Boolean?>(
        null
    )
        private set


    val killSwitchActive: Boolean
        get() =
            alwaysOnVpnEnabled == true &&
                    lockdownEnabled == true


    /*
     * Très important :
     *
     * CONNECTED seul ne suffit plus.
     *
     * La navigation est considérée protégée uniquement
     * lorsque WireGuard est monté ET que le watchdog
     * confirme le chemin réseau.
     */
    val isProtected: Boolean
        get() =
            status == TunnelStatus.CONNECTED &&
                    tunnelReachable


    override fun getName(): String =
        "Deliriuum"


    // ============================================================
    // CALLBACK WIREGUARD
    // ============================================================

    override fun onStateChange(
        newState: Tunnel.State
    ) {

        Log.e(
            TAG,
            "onStateChange=$newState currentStatus=$status"
        )


        if (
            newState == Tunnel.State.DOWN &&
            status == TunnelStatus.CONNECTED
        ) {

            stopTunnelWatchdog()


            tunnelReachable =
                false


            status =
                TunnelStatus.DISCONNECTED


            Log.e(
                TAG,
                "WIREGUARD DOWN -> isProtected=$isProtected"
            )


            if (
                shouldAutoReconnect &&
                !manualDisconnect &&
                !reconnectInProgress
            ) {

                reconnectInProgress =
                    true


                scope.launch {

                    delay(
                        2_000L
                    )


                    try {

                        connect()

                    } catch (
                        e: Exception
                    ) {

                        lastErrorMessage =
                            e.localizedMessage
                                ?: "Reconnexion impossible."

                    } finally {

                        reconnectInProgress =
                            false
                    }
                }
            }
        }
    }


    // ============================================================
    // AUTO DISCONNECT
    // ============================================================

    fun clearAutoDisconnectedNotice() {

        autoDisconnected =
            false
    }


    suspend fun disconnectAutomaticallyAfterInactivity() {

        if (!isProtected) {
            return
        }


        autoDisconnected =
            true


        disconnect(
            manual = false,
            allowAutoReconnect = false
        )
    }


    // ============================================================
    // KILL-SWITCH STATUS
    // ============================================================

    fun refreshKillSwitchStatus() {

        scope.launch {

            try {

                val result =
                    withContext(
                        Dispatchers.IO
                    ) {

                        Pair(
                            backend.isAlwaysOn,
                            backend.isLockdownEnabled
                        )
                    }


                alwaysOnVpnEnabled =
                    result.first


                lockdownEnabled =
                    result.second


                Log.e(
                    TAG,
                    "KILLSWITCH alwaysOn=${result.first} lockdown=${result.second}"
                )

            } catch (
                e: Exception
            ) {

                /*
                 * Si GoBackend ne peut pas interroger le VpnService
                 * à cet instant, on CONSERVE la dernière valeur
                 * connue.
                 *
                 * Si aucune lecture n'a encore réussi, on utilise
                 * false afin d'éviter "Vérification..." éternellement.
                 */

                if (
                    alwaysOnVpnEnabled == null
                ) {

                    alwaysOnVpnEnabled =
                        false
                }


                if (
                    lockdownEnabled == null
                ) {

                    lockdownEnabled =
                        false
                }


                Log.e(
                    TAG,
                    "KILLSWITCH read failed, keeping last state: " +
                            "alwaysOn=$alwaysOnVpnEnabled lockdown=$lockdownEnabled " +
                            "error=${e.javaClass.simpleName}:${e.message}"
                )
            }
        }
    }


    // ============================================================
    // HEALTH CHECK TCP
    // ============================================================

    private suspend fun checkTcpEndpoint(
        host: String
    ): Boolean {

        return withContext(
            Dispatchers.IO
        ) {

            var socket: Socket? =
                null


            try {

                socket =
                    Socket()


                socket.connect(
                    InetSocketAddress(
                        host,
                        HEALTH_CHECK_PORT
                    ),
                    HEALTH_CHECK_TIMEOUT_MS
                )


                true

            } catch (
                e: Exception
            ) {

                Log.e(
                    TAG,
                    "TCP $host FAILED: " +
                            "${e.javaClass.simpleName}: ${e.message}"
                )


                false

            } finally {

                try {

                    socket
                        ?.close()

                } catch (
                    _: Exception
                ) {
                }
            }
        }
    }


    private suspend fun checkTunnelInternet():
            Boolean {

        /*
         * Première cible.
         */
        if (
            checkTcpEndpoint(
                HEALTH_CHECK_HOST_PRIMARY
            )
        ) {

            return true
        }


        /*
         * Deuxième cible pour éviter de déclarer le VPN mort
         * si 1.1.1.1 a simplement un problème ponctuel.
         */
        return checkTcpEndpoint(
            HEALTH_CHECK_HOST_FALLBACK
        )
    }


    private fun startTunnelWatchdog() {

        watchdogJob
            ?.cancel()


        watchdogJob =
            null


        consecutiveHealthCheckFailures =
            0


        /*
         * WireGuard vient de passer UP.
         *
         * On permet immédiatement la navigation.
         * Le watchdog surveille ensuite le chemin réel.
         */
        tunnelReachable =
            true


        Log.e(
            TAG,
            "WATCHDOG STARTED " +
                    "status=$status reachable=$tunnelReachable protected=$isProtected"
        )


        watchdogJob =
            scope.launch {

                while (true) {

                    delay(
                        HEALTH_CHECK_INTERVAL_MS
                    )


                    if (
                        status != TunnelStatus.CONNECTED ||
                        manualDisconnect
                    ) {

                        Log.e(
                            TAG,
                            "WATCHDOG EXIT " +
                                    "status=$status manualDisconnect=$manualDisconnect"
                        )


                        break
                    }


                    val reachable =
                        checkTunnelInternet()


                    Log.e(
                        TAG,
                        "CHECK reachable=$reachable " +
                                "failures=$consecutiveHealthCheckFailures " +
                                "status=$status " +
                                "tunnelReachable=$tunnelReachable " +
                                "isProtected=$isProtected"
                    )


                    if (
                        reachable
                    ) {

                        consecutiveHealthCheckFailures =
                            0


                        if (
                            !tunnelReachable
                        ) {

                            tunnelReachable =
                                true


                            lastErrorMessage =
                                null


                            Log.e(
                                TAG,
                                "TUNNEL RESTORED -> " +
                                        "reachable=$tunnelReachable " +
                                        "isProtected=$isProtected"
                            )
                        }

                    } else {

                        consecutiveHealthCheckFailures++


                        Log.e(
                            TAG,
                            "FAILURE " +
                                    "$consecutiveHealthCheckFailures/" +
                                    HEALTH_CHECK_FAILURE_THRESHOLD
                        )


                        if (
                            consecutiveHealthCheckFailures >=
                            HEALTH_CHECK_FAILURE_THRESHOLD
                        ) {

                            if (
                                tunnelReachable
                            ) {

                                tunnelReachable =
                                    false


                                lastErrorMessage =
                                    "Le tunnel VPN ne répond plus. " +
                                            "La navigation dans Deliriuum est suspendue."


                                Log.e(
                                    TAG,
                                    "TUNNEL DECLARED UNREACHABLE -> " +
                                            "reachable=$tunnelReachable " +
                                            "isProtected=$isProtected"
                                )
                            }
                        }
                    }
                }
            }
    }


    private fun stopTunnelWatchdog() {

        watchdogJob
            ?.cancel()


        watchdogJob =
            null


        consecutiveHealthCheckFailures =
            0


        tunnelReachable =
            false


        Log.e(
            TAG,
            "WATCHDOG STOPPED -> " +
                    "reachable=$tunnelReachable protected=$isProtected"
        )
    }


    // ============================================================
    // KEYPAIR
    // ============================================================

    private fun ensureLocalKeyPair():
            KeyPair {

        val storedPrivate =
            prefs.getString(
                "wireguard_private_key",
                null
            )


        if (
            storedPrivate != null
        ) {

            val privateKey =
                Key.fromBase64(
                    storedPrivate
                )

            val keyPair =
                KeyPair(
                    privateKey
                )

            Log.i(
                "DeepShield",
                "WIREGUARD EXISTING KEY public=" +
                        keyPair.publicKey
                            .toBase64()
                            .take(16)
            )

            return keyPair
        }


        val keyPair =
            KeyPair()

        Log.i(
            "DeepShield",
            "WIREGUARD NEW KEY public=" +
                    keyPair.publicKey
                        .toBase64()
                        .take(16)
        )


        prefs.edit {

            putString(
                "wireguard_private_key",
                keyPair.privateKey
                    .toBase64()
            )


            putString(
                "wireguard_public_key",
                keyPair.publicKey
                    .toBase64()
            )
        }


        return keyPair
    }


    // ============================================================
    // DEVICE
    // ============================================================

    private suspend fun ensureRegisteredDevice(
        forceRefresh: Boolean =
            false
    ): String {

        if (
            forceRefresh
        ) {

            Log.i(
                "DeepShield",
                "DEVICE FORCE REFRESH"
            )

            prefs.edit {

                remove(
                    "device_id"
                )
            }
        }


        val cachedId =
            prefs.getString(
                "device_id",
                null
            )


        if (
            cachedId != null
        ) {

            Log.i(
                "DeepShield",
                "DEVICE CACHED id=" +
                        cachedId.take(16)
            )

            return cachedId
        }


        val keyPair =
            ensureLocalKeyPair()


        val deviceName =
            "${Build.MANUFACTURER} ${Build.MODEL}"

        Log.i(
            "DeepShield",
            "REGISTER DEVICE key=" +
                    keyPair.publicKey
                        .toBase64()
                        .take(16)
        )


        val device =
            APIClient.shared
                .createDevice(
                    name =
                        deviceName,

                    publicKey =
                        keyPair
                            .publicKey
                            .toBase64()
                )


        prefs.edit {

            putString(
                "device_id",
                device.id
            )
        }


        return device.id
    }



    // ============================================================
    // LOGOUT / DESTRUCTION DE L'IDENTITÉ WIREGUARD
    // ============================================================

    /*
     * Efface uniquement l'identité WireGuard locale.
     *
     * Cette identité est volontairement liée à la session
     * d'authentification utilisateur, et non à l'installation
     * Android pour toute sa durée de vie.
     *
     * Au prochain login, ensureLocalKeyPair() générera donc
     * automatiquement une nouvelle paire de clés et
     * ensureRegisteredDevice() créera un nouveau Device.
     */
    private fun clearLocalWireGuardIdentity() {

        prefs.edit {

            remove(
                "device_id"
            )

            remove(
                "wireguard_private_key"
            )

            remove(
                "wireguard_public_key"
            )

            /*
             * Un session_id ne doit jamais survivre à un
             * changement de compte.
             */
            remove(
                "current_session_id"
            )
        }


        Log.i(
            "DeepShield",
            "WIREGUARD IDENTITY CLEARED"
        )
    }


    /*
     * Prépare un logout explicite.
     *
     * ORDRE IMPORTANT :
     *
     * 1. stopper la reconnexion automatique / watchdog
     * 2. couper le tunnel pendant que le JWT est encore disponible
     * 3. supprimer le Device côté master pendant que le JWT est encore disponible
     * 4. supprimer la clé WireGuard et le device_id locaux
     *
     * APIClient.logout() doit être appelé ENSUITE par AuthManager,
     * jamais avant cette méthode.
     *
     * La suppression distante est best-effort :
     * si le master est momentanément indisponible, on efface quand
     * même l'identité locale pour empêcher sa réutilisation par
     * un autre compte.
     */
    suspend fun prepareForLogout() {

        Log.i(
            "DeepShield",
            "LOGOUT PREPARE START"
        )


        /*
         * Empêche toute tentative de reconnexion pendant le logout.
         */
        shouldAutoReconnect =
            false

        manualDisconnect =
            true

        reconnectInProgress =
            false

        stopTunnelWatchdog()


        /*
         * On mémorise le Device AVANT toute suppression locale.
         */
        val deviceId =
            prefs.getString(
                "device_id",
                null
            )


        /*
         * 1. Couper le tunnel et fermer la session master.
         *
         * disconnect() utilise encore le JWT puisque
         * APIClient.logout() n'a pas encore été appelé.
         */
        if (
            status != TunnelStatus.DISCONNECTED
        ) {

            try {

                disconnect()

            } catch (
                e: Exception
            ) {

                /*
                 * Un échec réseau ne doit jamais empêcher
                 * l'utilisateur de se déconnecter du compte.
                 */
                Log.w(
                    "DeepShield",
                    "LOGOUT tunnel cleanup failed: " +
                            "${e.javaClass.simpleName}: ${e.message}"
                )
            }
        }


        /*
         * 2. Supprimer le Device côté master tant que
         * les credentials sont encore présents.
         *
         * Si les tokens sont déjà expirés, la suppression
         * distante n'est plus possible ; on poursuit néanmoins
         * le nettoyage local.
         */
        if (
            deviceId != null &&
            APIClient.shared.isLoggedIn
        ) {

            try {

                APIClient.shared
                    .deleteDevice(
                        deviceId
                    )


                Log.i(
                    "DeepShield",
                    "DEVICE DELETED FROM MASTER id=" +
                            deviceId.take(16)
                )

            } catch (
                e: Exception
            ) {

                /*
                 * Best effort :
                 * l'ancien Device pourra être purgé côté serveur
                 * ultérieurement, mais sa clé ne sera jamais
                 * réutilisée par le prochain compte.
                 */
                Log.w(
                    "DeepShield",
                    "DEVICE DELETE FAILED id=" +
                            deviceId.take(16) +
                            " error=${e.javaClass.simpleName}:${e.message}"
                )
            }
        }


        /*
         * 3. Destruction locale inconditionnelle.
         */
        clearLocalWireGuardIdentity()


        tunnelReachable =
            false

        status =
            TunnelStatus.DISCONNECTED

        autoDisconnected =
            false

        lastErrorMessage =
            null


        sessionConflictMessage =
            null


        Log.i(
            "DeepShield",
            "LOGOUT PREPARE DONE"
        )
    }


    // ============================================================
    // VPN PERMISSION
    // ============================================================

    fun checkVpnPermissionIntent():
            Intent? {

        return VpnService
            .prepare(
                context
            )
    }


    // ============================================================
    // CONNECT
    // ============================================================

    suspend fun connect() {

        stopTunnelWatchdog()


        lastErrorMessage =
            null


        sessionConflictMessage =
            null


        autoDisconnected =
            false


        manualDisconnect =
            false


        status =
            TunnelStatus.CONNECTING


        var sessionCreatedDuringThisAttempt =
            false


        try {

            var deviceId =
                ensureRegisteredDevice()


            val sessionConfig =
                try {

                    APIClient.shared
                        .connectSession(
                            deviceId =
                                deviceId
                        )

                } catch (
                    e: Exception
                ) {

                    val message =
                        e.localizedMessage
                            ?: ""


                    if (
                        message.contains(
                            "Device introuvable",
                            ignoreCase = true
                        ) ||
                        message.contains(
                            "device not found",
                            ignoreCase = true
                        )
                    ) {

                        deviceId =
                            ensureRegisteredDevice(
                                forceRefresh =
                                    true
                            )


                        APIClient.shared
                            .connectSession(
                                deviceId =
                                    deviceId
                            )

                    } else {

                        throw e
                    }
                }


            currentSessionId =
                sessionConfig
                    .sessionId


            sessionCreatedDuringThisAttempt =
                true


            val keyPair =
                ensureLocalKeyPair()


            // ====================================================
            // WIREGUARD INTERFACE
            // ====================================================

            val wgInterface =
                Interface.Builder()
                    .setKeyPair(
                        keyPair
                    )
                    .parseAddresses(
                        sessionConfig
                            .clientAddress
                    )
                    .apply {

                        if (
                            sessionConfig
                                .clientDns
                                .isNotEmpty()
                        ) {

                            parseDnsServers(
                                sessionConfig
                                    .clientDns
                                    .joinToString(
                                        ","
                                    )
                            )
                        }
                    }
                    .build()


            // ====================================================
            // WIREGUARD PEER
            // ====================================================

            val peer =
                Peer.Builder()
                    .parsePublicKey(
                        sessionConfig
                            .serverPublicKey
                    )
                    .parseEndpoint(
                        sessionConfig
                            .serverEndpoint
                    )
                    .parseAllowedIPs(
                        sessionConfig
                            .allowedIps
                            .joinToString(
                                ","
                            )
                    )
                    .setPersistentKeepalive(
                        sessionConfig
                            .persistentKeepalive
                    )
                    .build()


            val config =
                Config.Builder()
                    .setInterface(
                        wgInterface
                    )
                    .addPeer(
                        peer
                    )
                    .build()


            // ====================================================
            // START WIREGUARD
            // ====================================================

            withContext(
                Dispatchers.IO
            ) {

                backend.setState(
                    this@TunnelManager,
                    Tunnel.State.UP,
                    config
                )
            }


            shouldAutoReconnect =
                true


            manualDisconnect =
                false


            status =
                TunnelStatus.CONNECTED


            /*
             * A partir d'ici :
             *
             * status = CONNECTED
             * tunnelReachable = true
             *
             * donc isProtected = true.
             */
            startTunnelWatchdog()


            refreshKillSwitchStatus()


            Log.e(
                TAG,
                "CONNECTED -> " +
                        "status=$status " +
                        "reachable=$tunnelReachable " +
                        "protected=$isProtected"
            )


        } catch (
            e: Exception
        ) {

            stopTunnelWatchdog()


            if (
                sessionCreatedDuringThisAttempt
            ) {

                val sessionId =
                    currentSessionId


                if (
                    sessionId != null
                ) {

                    try {

                        APIClient.shared
                            .disconnectSession(
                                sessionId =
                                    sessionId,

                                bytesIn =
                                    0,

                                bytesOut =
                                    0
                            )


                        currentSessionId =
                            null

                    } catch (
                        _: Exception
                    ) {

                        /*
                         * On conserve session_id si le master
                         * est injoignable.
                         */
                    }
                }
            }


            shouldAutoReconnect =
                false


            tunnelReachable =
                false


            status =
                TunnelStatus.DISCONNECTED


            if (e is APIError.Conflict) {

                sessionConflictMessage =
                    e.detail

                lastErrorMessage =
                    null

            } else {

                lastErrorMessage =
                    e.localizedMessage
                        ?: "Impossible d'établir la connexion."
            }


            refreshKillSwitchStatus()


            throw e
        }
    }


    // ============================================================
    // DISCONNECT PUBLIC
    // ============================================================

    suspend fun disconnect() {

        disconnect(
            manual = true,
            allowAutoReconnect = false
        )
    }


    // ============================================================
    // REPLACE ACTIVE SESSION
    // ============================================================

    suspend fun replaceActiveSession() {

        /*
         * Cette méthode est appelée uniquement après un HTTP 409
         * du master indiquant qu'une ancienne session VPN est
         * encore active pour ce device.
         *
         * Le compte utilisateur reste connecté : on remplace
         * seulement la session VPN.
         */

        stopTunnelWatchdog()


        shouldAutoReconnect =
            false


        manualDisconnect =
            true


        reconnectInProgress =
            false


        tunnelReachable =
            false


        status =
            TunnelStatus.DISCONNECTED


        lastErrorMessage =
            null


        val deviceId =
            prefs.getString(
                "device_id",
                null
            )
                ?: throw IllegalStateException(
                    "Impossible de retrouver l'appareil Deliriuum local."
                )


        /*
         * On coupe aussi le tunnel WireGuard local en best-effort.
         * Après un redémarrage de processus, notre état Compose peut
         * être DISCONNECTED alors que le VpnService Android possède
         * encore un ancien état technique.
         */
        try {

            withContext(
                Dispatchers.IO
            ) {

                backend.setState(
                    this@TunnelManager,
                    Tunnel.State.DOWN,
                    null
                )
            }

        } catch (
            _: Exception
        ) {
            /*
             * Ce nettoyage local ne doit pas empêcher le master
             * de fermer la session distante.
             */
        }


        try {

            APIClient.shared
                .disconnectActiveDeviceSession(
                    deviceId =
                        deviceId
                )

            /*
             * L'ancienne session est maintenant fermée côté master.
             * Un éventuel ID local devenu obsolète ne doit plus être
             * réutilisé.
             */
            currentSessionId =
                null

        } catch (
            e: Exception
        ) {

            lastErrorMessage =
                e.localizedMessage
                    ?: "Impossible de fermer l'ancienne session."

            throw e
        }


        /*
         * Le master a libéré le device : on ouvre immédiatement
         * une nouvelle session avec la même identité WireGuard.
         */
        connect()
    }


    // ============================================================
    // DISCONNECT INTERNAL
    // ============================================================

    private suspend fun disconnect(
        manual: Boolean,
        allowAutoReconnect: Boolean
    ) {

        lastErrorMessage =
            null


        sessionConflictMessage =
            null


        /*
         * Le changement d'état observable arrive immédiatement.
         *
         * Les boutons sociaux doivent donc être désactivés dès ici.
         */
        stopTunnelWatchdog()


        manualDisconnect =
            manual


        shouldAutoReconnect =
            allowAutoReconnect


        reconnectInProgress =
            false


        status =
            TunnelStatus.DISCONNECTING


        val sessionId =
            currentSessionId


        var masterSessionClosed =
            sessionId == null


        try {

            // ====================================================
            // STOP WG
            // ====================================================

            try {

                withContext(
                    Dispatchers.IO
                ) {

                    backend.setState(
                        this@TunnelManager,
                        Tunnel.State.DOWN,
                        null
                    )
                }

            } catch (
                e: Exception
            ) {

                lastErrorMessage =
                    e.localizedMessage
                        ?: "Erreur lors de l'arrêt du tunnel."
            }


            // ====================================================
            // CLOSE MASTER SESSION
            // ====================================================

            if (
                sessionId != null
            ) {

                try {

                    APIClient.shared
                        .disconnectSession(
                            sessionId =
                                sessionId,

                            bytesIn =
                                0,

                            bytesOut =
                                0
                        )


                    masterSessionClosed =
                        true

                } catch (
                    e: Exception
                ) {

                    masterSessionClosed =
                        false


                    lastErrorMessage =
                        e.localizedMessage
                            ?: "Le tunnel est fermé, mais la session distante n'a pas pu être clôturée."
                }
            }


        } finally {

            if (
                masterSessionClosed
            ) {

                currentSessionId =
                    null
            }


            tunnelReachable =
                false


            status =
                TunnelStatus.DISCONNECTED


            refreshKillSwitchStatus()


            Log.e(
                TAG,
                "DISCONNECTED -> " +
                        "status=$status " +
                        "reachable=$tunnelReachable " +
                        "protected=$isProtected"
            )
        }
    }

}