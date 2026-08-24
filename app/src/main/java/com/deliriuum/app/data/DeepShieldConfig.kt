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
              # WEBRTC
              # ==================================================

              # Réduit l'exposition des adresses réseau locales.
              media.peerconnection.ice.no_host: true

              # Limite la sélection ICE à l'adresse par défaut.
              media.peerconnection.ice.default_address_only: true


              # ==================================================
              # GEOLOCATION
              # ==================================================

              # Deep Shield n'expose pas la localisation réelle
              # par l'API navigateur.
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