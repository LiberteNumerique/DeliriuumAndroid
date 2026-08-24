package com.deliriuum.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember

@Composable
fun VpnLostDialog(
    isReconnecting: Boolean,
    onReconnect: () -> Unit,
    onClose: () -> Unit
) {

    Dialog(
        onDismissRequest = {
            // Intentionnellement vide :
            // impossible de fermer en touchant derrière.
        },
        properties =
            DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0xFF0C0C12),
                        RoundedCornerShape(26.dp)
                    )
                    .border(
                        1.dp,
                        Color(0xFFFFC633).copy(alpha = 0.40f),
                        RoundedCornerShape(26.dp)
                    )
                    .padding(22.dp),

            horizontalAlignment =
                Alignment.CenterHorizontally,

            verticalArrangement =
                Arrangement.spacedBy(18.dp)
        ) {

            Box(
                modifier =
                    Modifier
                        .size(58.dp)
                        .background(
                            Color(0xFFFFC633).copy(alpha = 0.12f),
                            CircleShape
                        )
                        .border(
                            1.dp,
                            Color(0xFFFFC633).copy(alpha = 0.30f),
                            CircleShape
                        ),

                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    text = "🔒",
                    fontSize = 27.sp
                )
            }


            Text(
                text = "Protection interrompue",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )


            Text(
                text =
                    "Le tunnel VPN Deliriuum a été interrompu. " +
                            "La navigation dans Deliriuum est suspendue afin d'éviter " +
                            "qu'une page soit chargée sans la protection du VPN.",

                color = Color.White.copy(alpha = 0.76f),
                fontSize = 14.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center
            )


            Text(
                text =
                    if (isReconnecting) {
                        "Tentative de reconnexion…"
                    } else {
                        "Vous pouvez tenter de rétablir la connexion ou fermer le navigateur."
                    },

                color = Color.Cyan.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )


            // ====================================================
            // RECONNECT
            // ====================================================

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (isReconnecting) {
                                Color.Cyan.copy(alpha = 0.35f)
                            } else {
                                Color.Cyan
                            },
                            RoundedCornerShape(16.dp)
                        )
                        .then(
                            if (!isReconnecting) {
                                Modifier
                                    .noRippleClickable {
                                        onReconnect()
                                    }
                            } else {
                                Modifier
                            }
                        )
                        .padding(vertical = 14.dp),

                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    text =
                        if (isReconnecting) {
                            "Reconnexion…"
                        } else {
                            "Rétablir la protection"
                        },

                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
            }


            // ====================================================
            // CLOSE
            // ====================================================

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.18f),
                            RoundedCornerShape(16.dp)
                        )
                        .noRippleClickable {
                            onClose()
                        }
                        .padding(vertical = 13.dp),

                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    text = "Fermer le navigateur",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
@Composable
private fun Modifier.noRippleClickable(
    onClick: () -> Unit
): Modifier {

    val interactionSource =
        remember {
            MutableInteractionSource()
        }

    return clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick
    )
}