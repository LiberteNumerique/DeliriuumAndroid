package com.deliriuum.app.data

import android.content.Context
import java.io.File

object DeepShieldConfig {

    fun write(
        context: Context
    ): String {

        val file =
            File(
                context.filesDir,
                "deepshield-gecko.yaml"
            )

        val yaml =
            """
            prefs:

              # ==================================================
              # MOZILLA RESIST FINGERPRINTING
              # ==================================================

              # Protection anti-fingerprinting complète native Gecko.
              #
              # Important :
              # aucune valeur spécifique à Deliriuum n'est fabriquée.
              # Nous utilisons le profil RFP standard de Mozilla.
              privacy.resistFingerprinting: true
              privacy.resistFingerprinting.pbmode: true


              # ==================================================
              # WEBRTC
              # ==================================================

              # Réduit l'exposition des adresses réseau locales.
              media.peerconnection.ice.no_host: true

              # Limite la sélection ICE à l'adresse par défaut.
              media.peerconnection.ice.default_address_only: true


              # ==================================================
              # GEOLOCATION
              # ==================================================

              # L'API de géolocalisation du navigateur est désactivée.
              geo.enabled: false


              # ==================================================
              # SPECULATIVE NETWORK REQUESTS
              # ==================================================

              network.dns.disablePrefetch: true
              network.prefetch-next: false
              network.predictor.enabled: false


              # ==================================================
              # NETWORK STATE PARTITIONING
              # ==================================================

              privacy.partition.network_state: true

            """.trimIndent()

        /*
         * Réécriture volontaire à chaque démarrage :
         * le fichier ne constitue pas un état utilisateur.
         */
        file.writeText(
            yaml
        )

        return file.absolutePath
    }
}