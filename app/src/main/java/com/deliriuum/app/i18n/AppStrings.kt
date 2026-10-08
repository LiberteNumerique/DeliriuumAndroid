package com.deliriuum.app.i18n

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import java.util.Locale

/**
 * Accès centralisé aux ressources traduites de Deliriuum.
 *
 * - "auto" : langue du téléphone.
 * - "fr", "en", "de", ... : langue forcée dans l'application.
 *
 * Les Activities visibles doivent utiliser :
 *
 * override fun attachBaseContext(newBase: Context) {
 *     super.attachBaseContext(
 *         AppStrings.localizedContext(newBase)
 *     )
 * }
 */
object AppStrings {

    private const val PREFS_NAME =
        "deliriuum_i18n"

    private const val KEY_LANGUAGE =
        "language_tag"

    const val AUTOMATIC =
        "auto"

    private val supportedLanguageTags =
        setOf(
            "fr",
            "en",
            "de",
            "es",
            "it",
            "pt",
            "nl",
            "pl",
            "cs",
            "sk",
            "hu",
            "ro",
            "bg",
            "hr",
            "sl",
            "el",
            "sv",
            "da",
            "no",
            "fi",
            "et",
            "lv",
            "lt",
            "tr",
            "uk",
            "ru",
            "ar",
            "he",
            "ja",
            "ko"
        )

    @Volatile
    private var appContext: Context? =
        null


    fun initialize(
        context: Context
    ) {

        appContext =
            context.applicationContext
    }


    /**
     * Valeur réellement enregistrée :
     *
     * "auto" ou un tag parmi les 30 langues.
     */
    fun selectedLanguageTag(
        base: Context? = null
    ): String {

        val context =
            base
                ?.applicationContext
                ?: appContext
                ?: error(
                    "AppStrings n'est pas initialisé."
                )

        return context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .getString(
                KEY_LANGUAGE,
                AUTOMATIC
            )
            ?.takeIf {
                it == AUTOMATIC ||
                        it in
                        supportedLanguageTags
            }
            ?: AUTOMATIC
    }


    /**
     * null = mode automatique.
     */
    fun currentLanguageTag(
        base: Context? = null
    ): String? =
        selectedLanguageTag(
            base
        ).takeUnless {
            it == AUTOMATIC
        }


    fun setLanguageTag(
        tag: String?
    ) {

        val context =
            checkNotNull(
                appContext
            ) {
                "AppStrings n'est pas initialisé."
            }

        val normalized =
            tag
                ?.trim()
                ?.takeIf {
                    it.isNotEmpty() &&
                            it !=
                            AUTOMATIC &&
                            it in
                            supportedLanguageTags
                }

        /*
         * commit() est volontaire ici :
         * l'Activity est recréée immédiatement après.
         * On veut que la préférence soit écrite avant
         * attachBaseContext() de la nouvelle Activity.
         */
        context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                KEY_LANGUAGE,
                normalized
                    ?: AUTOMATIC
            )
            .commit()
    }


    /**
     * Contexte portant la locale choisie.
     *
     * En mode automatique on renvoie le contexte reçu tel quel,
     * donc Android suit la langue du téléphone.
     */
    fun localizedContext(
        base: Context? = null
    ): Context {

        val context =
            base
                ?: checkNotNull(
                    appContext
                ) {
                    "AppStrings n'est pas initialisé."
                }

        val tag =
            currentLanguageTag(
                context
            )
                ?: return context

        val locale =
            Locale.forLanguageTag(
                tag
            )

        val configuration =
            Configuration(
                context
                    .resources
                    .configuration
            )

        /*
         * LocaleList permet aussi à Android de calculer
         * correctement la direction RTL pour arabe/hébreu.
         */
        configuration.setLocales(
            LocaleList(
                locale
            )
        )

        configuration
            .setLayoutDirection(
                locale
            )

        return context
            .createConfigurationContext(
                configuration
            )
    }


    fun get(
        @StringRes id: Int,
        vararg args: Any
    ): String {

        val context =
            localizedContext()

        return if (
            args.isEmpty()
        ) {

            context.getString(
                id
            )

        } else {

            context.getString(
                id,
                *args
            )
        }
    }


    fun quantity(
        @PluralsRes id: Int,
        quantity: Int,
        vararg args: Any
    ): String {

        val context =
            localizedContext()

        return context
            .resources
            .getQuantityString(
                id,
                quantity,
                *args
            )
    }
}
