package com.deliriuum.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun VpnConsentDialog(
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        /*
         * La popup ne peut pas être fermée en cliquant
         * à l'extérieur ou avec le bouton Retour.
         *
         * L'utilisateur doit explicitement choisir
         * Accepter ou Refuser.
         */
        onDismissRequest = {},

        title = {
            Text(
                text = "Utilisation du service VPN"
            )
        },

        text = {
            Text(
                text =
                    "Deliriuum utilise le service Android VpnService afin de créer " +
                            "un tunnel chiffré entre votre appareil et notre infrastructure VPN.\n\n" +

                            "• Objectif : protéger votre trafic internet et votre confidentialité.\n\n" +

                            "• Données de navigation : Deliriuum n'enregistre ni votre historique " +
                            "de navigation ni le contenu de vos communications et ne vend aucune " +
                            "donnée de navigation à des tiers.\n\n" +

                            "Android vous demandera ensuite une autorisation système avant " +
                            "l'établissement du tunnel VPN."
            )
        },

        /*
         * Les deux actions sont volontairement placées
         * dans le même conteneur afin d'obtenir deux
         * boutons de même largeur, parfaitement alignés.
         */
        confirmButton = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 8.dp,
                        end = 8.dp,
                        bottom = 8.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                Button(
                    onClick = onAccept,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Accepter et continuer",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Refuser et quitter",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        },

        /*
         * Aucun deuxième bloc d'actions :
         * le bouton Refuser est déjà dans confirmButton.
         */
        dismissButton = null
    )
}