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
import androidx.compose.ui.platform.LocalContext
import com.deliriuum.app.R

@Composable
fun VpnConsentDialog(
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    val _i18nContext = LocalContext.current

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
                text = _i18nContext.getString(R.string.vpn_consent_utilisation_du_service_vpn)
            )
        },

        text = {
            Text(
                text =
                    _i18nContext.getString(R.string.vpn_consent_deliriuum_utilise_le_service_android_vpnservice_afin_de)
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
                        text = _i18nContext.getString(R.string.vpn_consent_accepter_et_continuer),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = _i18nContext.getString(R.string.vpn_consent_refuser_et_quitter),
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