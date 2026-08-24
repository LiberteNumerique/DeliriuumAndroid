package com.deliriuum.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

class AuthManager private constructor() {

    companion object {

        val shared = AuthManager()

        private const val PREFS_NAME =
            "deliriuum_auth_cache"

        private const val KEY_USER_CACHE =
            "current_user"
    }


    // ============================================================
    // OBSERVABLE STATE
    // ============================================================

    var isLoggedIn by mutableStateOf(false)
        private set

    var currentUser by mutableStateOf<UserOut?>(null)
        private set

    /*
     * isLoading concerne les opérations visibles :
     * login, register, logout, resend...
     *
     * Le rafraîchissement de /auth/me ne doit PAS
     * bloquer l'interface "Mon compte".
     */
    var isLoading by mutableStateOf(false)
        private set

    /*
     * État dédié au rafraîchissement du profil,
     * distinct de isLoading.
     */
    var isRefreshingProfile by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)

    var needsEmailVerification by mutableStateOf(false)

    var pendingVerificationEmail by mutableStateOf<String?>(null)


    /*
     * ============================================================
     * POINT CENTRAL DU CORRECTIF
     * ============================================================
     *
     * Ce scope appartient au singleton, pas à un écran.
     *
     * Il n'est jamais annulé tant que le process vit.
     *
     * Toute récupération de profil DOIT passer par lui.
     *
     * Auparavant, refreshCurrentUser() s'exécutait dans le
     * scope de l'appelant :
     *
     *   - depuis login(), c'était le scope de l'écran de login,
     *     lequel quitte la composition dès que isLoggedIn passe
     *     à true. La requête /auth/me était donc annulée en vol.
     *
     *   - depuis un LaunchedEffect d'AccountScreen, la fermeture
     *     ou la recomposition de la modale produisait le même
     *     effet.
     *
     * Résultat : tokens enregistrés, currentUser resté null,
     * et un profil qui n'apparaissait qu'au redémarrage — seul
     * moment où initialize() utilisait ce scope-ci.
     */
    private val scope =
        CoroutineScope(
            SupervisorJob() +
                    Dispatchers.Main
        )


    /*
     * Contexte application.
     *
     * Il est fourni par initialize().
     */
    private var appContext: Context? =
        null


    // ============================================================
    // INITIALISATION
    // ============================================================

    /*
     * À appeler une fois au démarrage de l'application,
     * idéalement depuis Application.onCreate().
     *
     * Exemple :
     *
     * AuthManager.shared.initialize(this)
     */
    fun initialize(
        context: Context
    ) {

        /*
         * Empêche une double initialisation.
         */
        if (appContext != null) {
            return
        }


        appContext =
            context.applicationContext


        /*
         * Une session est considérée localement récupérable
         * lorsqu'un access token OU un refresh token existe.
         */
        isLoggedIn =
            APIClient.shared.isLoggedIn


        if (!isLoggedIn) {

            currentUser =
                null

            clearCachedUser()

            return
        }


        /*
         * ÉTAPE 1 :
         *
         * lecture immédiate du dernier profil connu.
         *
         * Aucun réseau.
         */
        currentUser =
            readCachedUser()


        /*
         * ÉTAPE 2 :
         *
         * actualisation silencieuse depuis /auth/me.
         *
         * L'interface peut déjà afficher le cache.
         */
        scope.launch {

            refreshCurrentUser(
                silent = true
            )
        }
    }


    // ============================================================
    // REGISTER
    // ============================================================

    suspend fun register(
        email: String,
        password: String
    ) {

        isLoading =
            true

        errorMessage =
            null


        try {

            withContext(
                Dispatchers.IO
            ) {

                APIClient.shared.register(
                    email,
                    password
                )
            }


            pendingVerificationEmail =
                email

            needsEmailVerification =
                true


        } catch (
            e: CancellationException
        ) {

            throw e


        } catch (
            e: Exception
        ) {

            errorMessage =
                e.localizedMessage
                    ?: "Impossible de créer le compte."


        } finally {

            isLoading =
                false
        }
    }


    // ============================================================
    // LOGIN
    // ============================================================

    suspend fun login(
        email: String,
        password: String
    ) {

        isLoading =
            true

        errorMessage =
            null

        needsEmailVerification =
            false


        try {

            /*
             * APIClient.login() stocke déjà
             * access_token + refresh_token.
             */
            withContext(
                Dispatchers.IO
            ) {

                APIClient.shared.login(
                    email,
                    password
                )
            }


            isLoggedIn =
                true


            /*
             * NE PAS appeler refreshCurrentUser() directement ici.
             *
             * Ce coroutine appartient à l'écran de login, lequel
             * disparaît de la composition à la ligne précédente.
             *
             * refreshProfile() délègue au scope du singleton :
             * la requête survit à la navigation.
             */
            refreshProfile(
                force = true
            )


        } catch (
            e: CancellationException
        ) {

            throw e


        } catch (
            e: Exception
        ) {

            val message =
                e.localizedMessage
                    ?: ""


            if (
                message
                    .lowercase(
                        Locale.ROOT
                    )
                    .contains(
                        "vérifiée"
                    )
            ) {

                pendingVerificationEmail =
                    email

                needsEmailVerification =
                    true

            } else {

                isLoggedIn =
                    APIClient.shared.isLoggedIn


                errorMessage =
                    if (
                        message.isNotBlank()
                    ) {
                        message
                    } else {
                        "Connexion impossible."
                    }
            }


        } finally {

            isLoading =
                false
        }
    }


    // ============================================================
    // RESEND VERIFICATION
    // ============================================================

    suspend fun resendVerification() {

        val email =
            pendingVerificationEmail
                ?: return


        isLoading =
            true

        errorMessage =
            null


        try {

            withContext(
                Dispatchers.IO
            ) {

                APIClient.shared
                    .resendVerification(
                        email
                    )
            }


        } catch (
            e: CancellationException
        ) {

            throw e


        } catch (
            e: Exception
        ) {

            errorMessage =
                e.localizedMessage
                    ?: "Impossible de renvoyer l'email."


        } finally {

            isLoading =
                false
        }
    }


    // ============================================================
    // PUBLIC PROFILE REFRESH
    // ============================================================

    /*
     * Volontairement NON suspend.
     *
     * L'appelant déclenche la récupération, mais n'en porte
     * pas le cycle de vie. Un écran qui se ferme n'annule
     * donc plus la requête.
     *
     * Points d'appel :
     *
     *   - login() (force = true)
     *   - ouverture d'AccountScreen
     *   - retour au premier plan (ON_RESUME)
     *   - bouton "Réessayer" (force = true)
     *
     * force = true ignore la garde anti-concurrence. Comme
     * tout l'état est muté sur Dispatchers.Main, deux passages
     * simultanés restent inoffensifs : le dernier /auth/me
     * arrivé fait foi.
     */
    fun refreshProfile(
        force: Boolean = false
    ) {

        if (!isLoggedIn) {
            return
        }


        if (
            isRefreshingProfile &&
            !force
        ) {
            return
        }


        scope.launch {

            isRefreshingProfile =
                true


            try {

                refreshCurrentUser(
                    silent = false
                )

            } finally {

                isRefreshingProfile =
                    false
            }
        }
    }


    // ============================================================
    // REFRESH CURRENT USER
    // ============================================================

    private suspend fun refreshCurrentUser(
        silent: Boolean
    ) {

        try {

            val freshUser =
                withContext(
                    Dispatchers.IO
                ) {

                    APIClient.shared.me()
                }


            /*
             * /me a réussi :
             * la session est valide.
             */
            currentUser =
                freshUser

            isLoggedIn =
                true

            errorMessage =
                null


            /*
             * Sauvegarde du dernier profil valide.
             */
            cacheUser(
                freshUser
            )


        } catch (
            e: CancellationException
        ) {

            /*
             * IMPORTANT :
             *
             * une annulation n'est pas une erreur réseau.
             *
             * Le catch (e: Exception) plus bas l'avalait
             * silencieusement, ce qui masquait exactement
             * le bug d'annulation décrit en haut de ce fichier.
             *
             * On relance pour ne jamais la traiter comme un
             * échec serveur.
             */
            throw e


        } catch (
            e: APIError.NoToken
        ) {

            /*
             * Access + refresh token ne permettent
             * plus de récupérer la session.
             *
             * Ici seulement on détruit réellement
             * la session locale.
             */
            forceLocalLogout()


        } catch (
            e: APIError.Server
        ) {

            /*
             * Réseau lent / serveur indisponible / timeout :
             *
             * ON GARDE :
             * - les tokens
             * - isLoggedIn
             * - le profil en cache
             */
            isLoggedIn =
                APIClient.shared.isLoggedIn


            if (!silent) {

                errorMessage =
                    e.localizedMessage
                        ?: "Service temporairement indisponible."
            }


        } catch (
            e: Exception
        ) {

            /*
             * Même philosophie :
             *
             * une erreur inattendue de récupération du profil
             * ne doit pas effacer une session locale valide.
             */
            isLoggedIn =
                APIClient.shared.isLoggedIn


            if (!silent) {

                errorMessage =
                    e.localizedMessage
                        ?: "Impossible d'actualiser le compte."
            }
        }
    }


    // ============================================================
    // CACHE USER
    // ============================================================

    private fun cacheUser(
        user: UserOut
    ) {

        val context =
            appContext
                ?: return


        try {

            val json =
                JSONObject()
                    .put(
                        "id",
                        user.id
                    )
                    .put(
                        "email",
                        user.email
                    )
                    .put(
                        "plan",
                        user.plan
                    )
                    .put(
                        "isActive",
                        user.isActive
                    )
                    .put(
                        "isVerified",
                        user.isVerified
                    )
                    .put(
                        "isSupporter",
                        user.isSupporter
                    )
                    .put(
                        "createdAt",
                        user.createdAt
                    )


            context
                .getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )
                .edit()
                .putString(
                    KEY_USER_CACHE,
                    json.toString()
                )
                .apply()


        } catch (
            _: Exception
        ) {

            /*
             * Une erreur de cache ne doit jamais
             * casser l'authentification.
             */
        }
    }


    // ============================================================
    // READ USER CACHE
    // ============================================================

    private fun readCachedUser():
            UserOut? {

        val context =
            appContext
                ?: return null


        return try {

            val raw =
                context
                    .getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                    )
                    .getString(
                        KEY_USER_CACHE,
                        null
                    )
                    ?: return null


            val json =
                JSONObject(
                    raw
                )


            UserOut(
                id =
                    json.getString(
                        "id"
                    ),

                email =
                    json.getString(
                        "email"
                    ),

                plan =
                    json.getString(
                        "plan"
                    ),

                isActive =
                    json.getBoolean(
                        "isActive"
                    ),

                isVerified =
                    json.getBoolean(
                        "isVerified"
                    ),

                isSupporter =
                    json.getBoolean(
                        "isSupporter"
                    ),

                createdAt =
                    json.getString(
                        "createdAt"
                    )
            )


        } catch (
            _: Exception
        ) {

            /*
             * Cache ancien/corrompu :
             * on l'ignore simplement.
             */
            clearCachedUser()

            null
        }
    }


    // ============================================================
    // CLEAR CACHE
    // ============================================================

    private fun clearCachedUser() {

        val context =
            appContext
                ?: return


        try {

            context
                .getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )
                .edit()
                .remove(
                    KEY_USER_CACHE
                )
                .apply()


        } catch (
            _: Exception
        ) {
        }
    }


    // ============================================================
    // LOGOUT
    // ============================================================

    suspend fun logout() {

        if (isLoading) {
            return
        }


        isLoading =
            true

        errorMessage =
            null


        try {

            /*
             * IMPORTANT :
             *
             * nettoyage WireGuard AVANT suppression
             * des tokens, puisque le master peut
             * encore avoir besoin du JWT.
             */
            try {

                TunnelManager.shared
                    .prepareForLogout()

            } catch (
                _: Exception
            ) {
            }


            /*
             * Suppression access_token +
             * refresh_token.
             */
            APIClient.shared.logout()


            clearLocalAuthState()


        } finally {

            isLoading =
                false
        }
    }


    // ============================================================
    // FORCED LOGOUT
    // ============================================================

    private suspend fun forceLocalLogout() {

        try {

            TunnelManager.shared
                .prepareForLogout()

        } catch (
            _: Exception
        ) {
        }


        APIClient.shared.logout()


        clearLocalAuthState()
    }


    // ============================================================
    // CLEAR LOCAL STATE
    // ============================================================

    private fun clearLocalAuthState() {

        isLoggedIn =
            false

        currentUser =
            null

        needsEmailVerification =
            false

        pendingVerificationEmail =
            null

        errorMessage =
            null


        /*
         * Très important :
         *
         * un utilisateur déconnecté ne doit jamais
         * voir le profil de la session précédente.
         */
        clearCachedUser()
    }
}