package com.deliriuum.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONObject

/**
 * Cache local persistant des deux snapshots Privacy Audit.
 *
 * Règle :
 * - baseline = une seule mesure AVANT Deliriuum
 * - protected = une seule mesure APRÈS activation de Deliriuum
 *
 * Les valeurs survivent aux redémarrages de l'application et sont
 * supprimées uniquement lors d'une désinstallation / effacement des données,
 * ou lorsqu'on incrémente AUDIT_CACHE_VERSION.
 */
object PrivacyAuditCache {

    private const val TAG =
        "PrivacyAuditCache"

    private const val PREFS_NAME =
        "deliriuum_preferences"

    private const val KEY_CACHE_VERSION =
        "privacy_audit_cache_version"

    private const val KEY_BASELINE_JSON =
        "privacy_audit_baseline_json"

    private const val KEY_PROTECTED_JSON =
        "privacy_audit_protected_json"

    /*
     * À incrémenter uniquement si le protocole d'audit change au point
     * qu'une ancienne paire AVANT / APRÈS ne soit plus comparable.
     */
    const val AUDIT_CACHE_VERSION =
        1

    @Volatile
    private var preferences:
            SharedPreferences? =
        null


    fun initialize(
        context: Context
    ) {

        if (preferences != null) {
            return
        }

        synchronized(this) {

            if (preferences != null) {
                return
            }

            val prefs =
                context.applicationContext
                    .getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                    )

            val storedVersion =
                prefs.getInt(
                    KEY_CACHE_VERSION,
                    0
                )

            if (
                storedVersion != 0 &&
                storedVersion != AUDIT_CACHE_VERSION
            ) {

                Log.d(
                    TAG,
                    "Audit cache version changed: $storedVersion -> $AUDIT_CACHE_VERSION"
                )

                prefs.edit()
                    .remove(KEY_BASELINE_JSON)
                    .remove(KEY_PROTECTED_JSON)
                    .putInt(
                        KEY_CACHE_VERSION,
                        AUDIT_CACHE_VERSION
                    )
                    .apply()

            } else if (storedVersion == 0) {

                prefs.edit()
                    .putInt(
                        KEY_CACHE_VERSION,
                        AUDIT_CACHE_VERSION
                    )
                    .apply()
            }

            preferences =
                prefs
        }
    }


    fun hasBaseline(): Boolean =
        loadBaseline() != null


    fun hasProtected(): Boolean =
        loadProtected() != null


    fun loadBaseline(): JSONObject? =
        loadJson(
            KEY_BASELINE_JSON
        )


    fun loadProtected(): JSONObject? =
        loadJson(
            KEY_PROTECTED_JSON
        )


    fun saveBaseline(
        payload: JSONObject
    ) {

        val prefs =
            preferences
                ?: return

        /*
         * Le premier snapshot gagne. On ne remplace jamais la vraie
         * référence AVANT par une mesure ultérieure.
         */
        if (
            prefs.contains(
                KEY_BASELINE_JSON
            )
        ) {
            return
        }

        prefs.edit()
            .putInt(
                KEY_CACHE_VERSION,
                AUDIT_CACHE_VERSION
            )
            .putString(
                KEY_BASELINE_JSON,
                payload.toString()
            )
            .apply()

        Log.d(
            TAG,
            "Baseline cached"
        )
    }


    fun saveProtected(
        payload: JSONObject
    ) {

        val prefs =
            preferences
                ?: return

        /*
         * Même règle pour APRÈS : un seul snapshot de référence.
         */
        if (
            prefs.contains(
                KEY_PROTECTED_JSON
            )
        ) {
            return
        }

        prefs.edit()
            .putInt(
                KEY_CACHE_VERSION,
                AUDIT_CACHE_VERSION
            )
            .putString(
                KEY_PROTECTED_JSON,
                payload.toString()
            )
            .apply()

        Log.d(
            TAG,
            "Protected snapshot cached"
        )
    }


    fun restoreInto(
        manager: PrivacyAuditManager
    ) {

        manager.restoreCachedSnapshots(
            baseline =
                loadBaseline(),
            protected =
                loadProtected()
        )
    }


    private fun loadJson(
        key: String
    ): JSONObject? {

        val raw =
            preferences
                ?.getString(
                    key,
                    null
                )
                ?: return null

        return try {

            JSONObject(
                raw
            )

        } catch (
            error: Exception
        ) {

            Log.e(
                TAG,
                "Invalid cached JSON for $key; removing it",
                error
            )

            preferences
                ?.edit()
                ?.remove(key)
                ?.apply()

            null
        }
    }
}
