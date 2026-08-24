package com.deliriuum.app.util

import android.util.Log
import com.deliriuum.app.BuildConfig

/*
 * ================================================================
 * DEBUG LOG
 * ================================================================
 *
 * Remplace tous les appels directs à android.util.Log.
 *
 * Pourquoi ce wrapper alors que proguard-rules.pro contient
 * déjà -assumenosideeffects ?
 *
 * Parce que R8 supprime l'APPEL, pas ses ARGUMENTS.
 *
 *   Log.e(TAG, "CHECK reachable=$reachable failures=$n ...")
 *
 * devient, après R8, une concaténation de chaînes exécutée
 * puis jetée. Sur le watchdog — toutes les 3 secondes, en
 * permanence — c'est de l'allocation pure perte.
 *
 * Ici, le lambda n'est évalué que si BuildConfig.DEBUG est vrai.
 * En release, il n'y a ni concaténation, ni appel.
 *
 * ================================================================
 * MIGRATION
 * ================================================================
 *
 * TunnelManager.kt :
 *
 *   Log.e(TAG, "CHECK reachable=$reachable")
 *   ->
 *   DebugLog.e(TAG) { "CHECK reachable=$reachable" }
 *
 * HomeView.kt :
 *
 *   android.util.Log.d("PrivacyAuditUI", "HOME RECOMPOSE ...")
 *   ->
 *   DebugLog.d("PrivacyAuditUI") { "HOME RECOMPOSE ..." }
 *
 * ================================================================
 * CAS PARTICULIER : LES IDENTIFIANTS
 * ================================================================
 *
 * Ces deux lignes de TunnelManager sortent une clé publique
 * WireGuard et un device_id en clair dans logcat :
 *
 *   Log.i("DeepShield", "WIREGUARD NEW KEY public=" + ...take(16))
 *   Log.i("DeepShield", "DEVICE CACHED id=" + cachedId.take(16))
 *
 * Une clé publique n'est pas un secret, mais pour une app dont
 * l'argument est la confidentialité, un identifiant d'appareil
 * lisible par toute application disposant de READ_LOGS sur un
 * téléphone rooté est un reproche facile à formuler.
 *
 * Utilise DebugLog.sensitive() : rien n'est émis en release,
 * et en debug la valeur est tronquée.
 */
object DebugLog {

    private val enabled: Boolean =
        BuildConfig.DEBUG


    inline fun v(
        tag: String,
        message: () -> String
    ) {
        if (BuildConfig.DEBUG) {
            Log.v(tag, message())
        }
    }


    inline fun d(
        tag: String,
        message: () -> String
    ) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message())
        }
    }


    inline fun i(
        tag: String,
        message: () -> String
    ) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message())
        }
    }


    inline fun w(
        tag: String,
        message: () -> String
    ) {
        if (BuildConfig.DEBUG) {
            Log.w(tag, message())
        }
    }


    /*
     * Les vraies erreurs sont conservées en release.
     *
     * Elles servent aux rapports de crash et ne décrivent pas
     * le fonctionnement nominal.
     *
     * ATTENTION : ne jamais y faire passer un identifiant.
     */
    fun e(
        tag: String,
        message: String,
        throwable: Throwable? = null
    ) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }


    /*
     * Pour toute valeur qui identifie l'appareil ou l'utilisateur :
     * device_id, clé publique WireGuard, session_id, email.
     *
     * Silencieux en release, tronqué en debug.
     */
    fun sensitive(
        tag: String,
        label: String,
        value: String?
    ) {
        if (!enabled) {
            return
        }

        val shown =
            when {
                value == null -> "null"
                value.length <= 8 -> "***"
                else -> value.take(8) + "…"
            }

        Log.d(tag, "$label=$shown")
    }
}