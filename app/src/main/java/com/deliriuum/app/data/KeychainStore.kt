package com.deliriuum.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.core.content.edit

/**
 * Android counterpart to iOS KeychainStore.swift.
 * Secure storage via EncryptedSharedPreferences, using hardware-backed keystore
 * on supported devices.
 */
class KeychainStore private constructor(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    // Using the same service name for the shared prefs file
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "com.deliriuum.app.secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // Enum exact duplicata de Key enum Swift
    enum class Key(val value: String) {
        AccessToken("access_token"),
        RefreshToken("refresh_token"),
        WireGuardPrivateKey("wireguard_private_key"),
        DeviceId("device_id")
    }

    // MARK: - Core Operations

    // Change 'for key: Key' to just 'key: Key'
    fun set(value: String, key: Key) {
        // We now use 'key.value' to refer to the parameter inside the function
        prefs.edit { putString(key.value, value) }
    }

    fun get(key: Key): String? {
        return prefs.getString(key.value, null)
    }

    fun delete(key: Key) {
        prefs.edit { remove(key.value) }
    }

    // MARK: - Domain Logic (Replicated from iOS comment)

    /**
     * Clears session tokens on logout, but deliberately keeps the
     * WireGuard keypair and device id — those identify the physical
     * device with the backend, not the user session, and re-generating
     * them on every login would orphan the device's assigned IP.
     */
    fun clearAll() {
        delete(Key.AccessToken)
        delete(Key.RefreshToken)
    }

    // --- SINGLETON INITIALIZATION ---

    companion object {
        @Volatile
        private var INSTANCE: KeychainStore? = null

        /**
         * Standard initialization. Must be called in Application.onCreate()
         * or MainActivity.onCreate() before any get() call.
         */
        fun initialize(context: Context): KeychainStore {
            return INSTANCE ?: synchronized(this) {
                val instance = KeychainStore(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        /**
         * Global getter. Throws if not initialized first.
         */
        fun get(): KeychainStore {
            return INSTANCE ?: throw IllegalStateException("KeychainStore must be initialized with Context first.")
        }
    }
}