package com.deliriuum.app.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.io.IOException

// MARK: - API Errors

sealed class APIError(message: String) : Exception(message) {
    object InvalidURL : APIError("Invalid URL.")
    class Server(val detail: String) : APIError(detail)
    class Conflict(val detail: String) : APIError(detail)
    object Decoding : APIError("Could not read the server response.")
    object NoToken : APIError("Session expired, please log in again.")
}


// MARK: - Models

data class UserOut(
    val id: String,
    val email: String,
    val plan: String,
    @SerializedName("is_active")
    val isActive: Boolean,

    @SerializedName("is_verified")
    val isVerified: Boolean,

    @SerializedName("is_supporter")
    val isSupporter: Boolean,

    @SerializedName("created_at")
    val createdAt: String
)

data class TokenPair(
    @SerializedName("access_token")
    val accessToken: String,

    @SerializedName("refresh_token")
    val refreshToken: String,

    @SerializedName("token_type")
    val tokenType: String
)

data class DeviceOut(
    val id: String,
    val name: String,

    @SerializedName("public_key")
    val publicKey: String,

    @SerializedName("assigned_ip")
    val assignedIp: String?,

    @SerializedName("is_active")
    val isActive: Boolean,

    @SerializedName("created_at")
    val createdAt: String,

    @SerializedName("last_seen_at")
    val lastSeenAt: String?
)

data class WireGuardConfigOut(
    @SerializedName("session_id")
    val sessionId: String,

    @SerializedName("client_address")
    val clientAddress: String,

    @SerializedName("client_dns")
    val clientDns: List<String>,

    @SerializedName("server_public_key")
    val serverPublicKey: String,

    @SerializedName("server_endpoint")
    val serverEndpoint: String,

    @SerializedName("allowed_ips")
    val allowedIps: List<String>,

    @SerializedName("persistent_keepalive")
    val persistentKeepalive: Int
)

data class SessionOut(
    val id: String,

    @SerializedName("device_id")
    val deviceId: String,

    @SerializedName("node_id")
    val nodeId: String,

    @SerializedName("started_at")
    val startedAt: String,

    @SerializedName("ended_at")
    val endedAt: String?,

    @SerializedName("duration_seconds")
    val durationSeconds: Int?,

    @SerializedName("bytes_in")
    val bytesIn: Long,

    @SerializedName("bytes_out")
    val bytesOut: Long
)

data class GenericMessage(
    val detail: String
)


// MARK: - Retrofit Interface

interface DeliriuumApiService {

    @POST("/auth/register")
    suspend fun register(
        @Body body: Map<String, String>
    ): Response<UserOut>


    @POST("/auth/login")
    suspend fun login(
        @Body body: Map<String, String>
    ): Response<TokenPair>


    // NOUVEAU :
    // renouvellement automatique access_token / refresh_token
    @POST("/auth/refresh")
    suspend fun refresh(
        @Body body: Map<String, String>
    ): Response<TokenPair>


    @POST("/auth/resend-verification")
    suspend fun resendVerification(
        @Body body: Map<String, String>
    ): Response<ResponseBody>


    @GET("/auth/me")
    suspend fun me(): Response<UserOut>


    @HTTP(
        method = "DELETE",
        path = "/auth/me",
        hasBody = true
    )
    suspend fun deleteAccount(
        @Body body: Map<String, String>
    ): Response<ResponseBody>


    @POST("/auth/forgot-password")
    suspend fun forgotPassword(
        @Body body: Map<String, String>
    ): Response<GenericMessage>


    @POST("/auth/reset-password")
    suspend fun resetPassword(
        @Body body: Map<String, String>
    ): Response<UserOut>


    @POST("/devices")
    suspend fun createDevice(
        @Body body: Map<String, String>
    ): Response<DeviceOut>


    @GET("/devices")
    suspend fun listDevices(): Response<List<DeviceOut>>


    @POST("/sessions/connect")
    suspend fun connectSession(
        @Body body: Map<String, String>
    ): Response<WireGuardConfigOut>


    @POST("/sessions/{sessionId}/disconnect")
    suspend fun disconnectSession(
        @Path("sessionId")
        sessionId: String,

        @Body
        body: Map<String, Long>
    ): Response<SessionOut>


    @POST("/sessions/device/{deviceId}/disconnect-active")
    suspend fun disconnectActiveDeviceSession(
        @Path("deviceId")
        deviceId: String
    ): Response<ResponseBody>


    @DELETE("/devices/{deviceId}")
    suspend fun deleteDevice(
        @Path("deviceId") deviceId: String
    ): Response<ResponseBody>
}


// MARK: - API Client Singleton

class APIClient private constructor() {

    /*
     * Évite que plusieurs requêtes expirées lancent
     * plusieurs refresh simultanément.
     */
    private val refreshMutex = Mutex()


    private val okHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->

                val requestBuilder =
                    chain.request()
                        .newBuilder()

                /*
                 * Le token est relu depuis KeychainStore
                 * À CHAQUE requête.
                 *
                 * Après un refresh, la requête suivante
                 * utilisera donc automatiquement le nouveau token.
                 */
                KeychainStore.get()
                    .get(KeychainStore.Key.AccessToken)
                    ?.let { token ->

                        requestBuilder.header(
                            "Authorization",
                            "Bearer $token"
                        )
                    }

                chain.proceed(
                    requestBuilder.build()
                )
            }
            .build()


    private val apiService: DeliriuumApiService =
        Retrofit.Builder()
            .baseUrl("https://master.deliriuum.com")
            .client(okHttpClient)
            .addConverterFactory(
                GsonConverterFactory.create()
            )
            .build()
            .create(
                DeliriuumApiService::class.java
            )


    companion object {
        val shared = APIClient()
    }


    // MARK: - Helpers

    private fun extractErrorDetail(
        response: Response<*>
    ): String {

        val errorBody =
            response.errorBody()?.string()

        if (!errorBody.isNullOrEmpty()) {
            return try {

                Gson()
                    .fromJson(
                        errorBody,
                        GenericMessage::class.java
                    )
                    .detail

            } catch (_: Exception) {

                "Server error (${response.code()})."
            }
        }

        return "Server error (${response.code()})."
    }


    /*
     * Renouvelle les deux tokens.
     *
     * IMPORTANT :
     * cette méthode N'UTILISE PAS executeCall().
     *
     * Sinon :
     *
     * /me -> 401
     * refresh -> 401
     * refresh -> 401
     * ...
     *
     * boucle infinie.
     */
    private suspend fun refreshTokens() {

        refreshMutex.withLock {

            val refreshToken =
                KeychainStore.get()
                    .get(
                        KeychainStore.Key.RefreshToken
                    )
                    ?: throw APIError.NoToken

            try {

                val response =
                    apiService.refresh(
                        mapOf(
                            "refresh_token" to refreshToken
                        )
                    )

                if (response.isSuccessful) {

                    val pair =
                        response.body()
                            ?: throw APIError.Decoding

                    /*
                     * Le master renvoie un nouveau
                     * access_token ET un nouveau refresh_token.
                     *
                     * On remplace donc les deux.
                     */
                    KeychainStore.get().set(
                        pair.accessToken,
                        KeychainStore.Key.AccessToken
                    )

                    KeychainStore.get().set(
                        pair.refreshToken,
                        KeychainStore.Key.RefreshToken
                    )

                    return
                }

                val detail =
                    extractErrorDetail(response)

                /*
                 * Refresh token invalide / expiré.
                 *
                 * Là seulement, la session utilisateur
                 * est réellement terminée.
                 */
                if (
                    response.code() == 401 ||
                    response.code() == 403
                ) {

                    KeychainStore.get()
                        .clearAll()

                    throw APIError.NoToken
                }

                throw APIError.Server(detail)

            } catch (e: IOException) {

                /*
                 * IMPORTANT :
                 *
                 * panne réseau != utilisateur déconnecté.
                 *
                 * On NE supprime PAS les tokens.
                 */
                throw APIError.Server(
                    "Network failure: ${e.localizedMessage}"
                )
            }
        }
    }


    /*
     * Exécute une requête API.
     *
     * Si access_token expiré :
     *
     * requête
     * -> 401
     * -> refresh
     * -> sauvegarde nouveaux tokens
     * -> rejoue UNE FOIS la requête
     *
     * À N'UTILISER QUE pour les endpoints qui renvoient
     * réellement un corps JSON. Pour 204 No Content,
     * voir executeEmptyCall().
     */
    private suspend fun <T> executeCall(
        isAuthEndpoint: Boolean = false,
        allowRefresh: Boolean = true,
        call: suspend () -> Response<T>
    ): T {

        try {

            var response =
                call()

            /*
             * Access token expiré sur une route authentifiée.
             */
            if (
                response.code() == 401 &&
                !isAuthEndpoint &&
                allowRefresh
            ) {

                /*
                 * On renouvelle les tokens.
                 */
                refreshTokens()

                /*
                 * Puis on rejoue UNE SEULE FOIS
                 * la requête initiale.
                 *
                 * L'interceptor prendra automatiquement
                 * le nouveau access_token.
                 */
                response =
                    call()
            }


            if (response.isSuccessful) {

                return response.body()
                    ?: throw APIError.Decoding
            }


            val detail =
                extractErrorDetail(response)


            if (response.code() == 409) {
                throw APIError.Conflict(detail)
            }


            if (response.code() == 401) {

                if (isAuthEndpoint) {

                    /*
                     * Login :
                     * mauvais email / mot de passe.
                     */
                    throw APIError.Server(detail)

                } else {

                    /*
                     * Même après refresh,
                     * la requête reste refusée.
                     */
                    throw APIError.NoToken
                }
            }


            throw APIError.Server(detail)


        } catch (e: IOException) {

            throw APIError.Server(
                "Network failure: ${e.localizedMessage}"
            )
        }
    }


    /*
     * Variante pour les endpoints sans corps de réponse
     * exploitable (204 No Content, corps vide...).
     *
     * CORRECTIF :
     *
     * isAuthEndpoint a été ajouté pour que les routes
     * publiques (resend-verification) ne déclenchent
     * pas un refresh inutile sur un 401.
     */
    private suspend fun executeEmptyCall(
        isAuthEndpoint: Boolean = false,
        allowRefresh: Boolean = true,
        call: suspend () -> Response<ResponseBody>
    ) {
        try {

            var response = call()

            if (
                response.code() == 401 &&
                !isAuthEndpoint &&
                allowRefresh
            ) {

                refreshTokens()

                response = call()
            }

            if (response.isSuccessful) {
                return
            }

            val detail =
                extractErrorDetail(response)

            if (response.code() == 409) {
                throw APIError.Conflict(detail)
            }

            if (response.code() == 401) {

                if (isAuthEndpoint) {

                    throw APIError.Server(detail)

                } else {

                    throw APIError.NoToken
                }
            }

            throw APIError.Server(detail)

        } catch (e: IOException) {

            throw APIError.Server(
                "Network failure: ${e.localizedMessage}"
            )
        }
    }


    // MARK: - Auth

    suspend fun register(
        email: String,
        password: CharSequence
    ): UserOut =
        executeCall(
            isAuthEndpoint = true
        ) {

            apiService.register(
                mapOf(
                    "email" to email,
                    "password" to password.toString()
                )
            )
        }


    suspend fun login(
        email: String,
        password: CharSequence
    ): TokenPair {

        val pair =
            executeCall(
                isAuthEndpoint = true
            ) {

                apiService.login(
                    mapOf(
                        "email" to email,
                        "password" to password.toString()
                    )
                )
            }


        /*
         * Sauvegarde persistante des tokens.
         */
        KeychainStore.get().set(
            pair.accessToken,
            KeychainStore.Key.AccessToken
        )

        KeychainStore.get().set(
            pair.refreshToken,
            KeychainStore.Key.RefreshToken
        )

        return pair
    }


    /*
     * CORRECTIF :
     *
     * passait par executeCall<ResponseBody>, qui exige
     * un corps non nul. Un 204 du master faisait donc
     * remonter "Could not read the server response"
     * alors que l'email était bien parti.
     */
    suspend fun resendVerification(
        email: String
    ) {

        executeEmptyCall(
            isAuthEndpoint = true
        ) {

            apiService.resendVerification(
                mapOf(
                    "email" to email
                )
            )
        }
    }


    suspend fun me(): UserOut =
        executeCall {

            apiService.me()
        }


    fun logout() {

        KeychainStore.get()
            .clearAll()
    }


    val isLoggedIn: Boolean
        get() =
            KeychainStore.get()
                .get(
                    KeychainStore.Key.AccessToken
                ) != null ||
                    KeychainStore.get()
                        .get(
                            KeychainStore.Key.RefreshToken
                        ) != null


    /*
     * CORRECTIF : même problème que resendVerification.
     *
     * Une suppression réussie renvoyant 204 affichait
     * une fausse erreur à l'utilisateur.
     */
    suspend fun deleteAccount(
        password: CharSequence
    ) {

        executeEmptyCall {

            apiService.deleteAccount(
                mapOf(
                    "password" to password.toString()
                )
            )
        }

        /*
         * Le compte n'existe plus :
         * nettoyage local.
         */
        logout()
    }


    // MARK: - Password reset

    suspend fun forgotPassword(
        email: String
    ): String {

        val response =
            executeCall(
                isAuthEndpoint = true
            ) {

                apiService.forgotPassword(
                    mapOf(
                        "email" to email
                    )
                )
            }

        return response.detail
    }


    suspend fun resetPassword(
        token: String,
        newPassword: CharSequence
    ): UserOut =
        executeCall(
            isAuthEndpoint = true
        ) {

            apiService.resetPassword(
                mapOf(
                    "token" to token,
                    "new_password" to
                            newPassword.toString()
                )
            )
        }


    // MARK: - Devices

    suspend fun createDevice(
        name: String,
        publicKey: String
    ): DeviceOut =
        executeCall {

            apiService.createDevice(
                mapOf(
                    "name" to name,
                    "public_key" to
                            publicKey.trim()
                )
            )
        }


    suspend fun listDevices():
            List<DeviceOut> =
        executeCall {

            apiService.listDevices()
        }


    // MARK: - Sessions

    suspend fun connectSession(
        deviceId: String
    ): WireGuardConfigOut =
        executeCall {

            apiService.connectSession(
                mapOf(
                    "device_id" to deviceId
                )
            )
        }


    suspend fun disconnectSession(
        sessionId: String,
        bytesIn: Long,
        bytesOut: Long
    ): SessionOut =
        executeCall {

            apiService.disconnectSession(
                sessionId,
                mapOf(
                    "bytes_in" to bytesIn,
                    "bytes_out" to bytesOut
                )
            )
        }


    suspend fun disconnectActiveDeviceSession(
        deviceId: String
    ) {
        executeEmptyCall {
            apiService.disconnectActiveDeviceSession(
                deviceId
            )
        }
    }


    suspend fun deleteDevice(
        deviceId: String
    ) {
        executeEmptyCall {
            apiService.deleteDevice(deviceId)
        }
    }
}