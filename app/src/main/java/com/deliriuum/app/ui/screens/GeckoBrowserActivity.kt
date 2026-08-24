package com.deliriuum.app.ui.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.deliriuum.app.data.TunnelManager
import com.deliriuum.app.ui.components.DeliriumGeckoView
import com.deliriuum.app.ui.components.VpnLostDialog
import kotlinx.coroutines.launch


class GeckoBrowserActivity : ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)


        val url =
            intent.getStringExtra("url")
                ?: "https://example.com"


        val title =
            intent.getStringExtra("title")
                ?: "Deliriuum"


        setContent {

            GeckoBrowserScreen(
                title = title,
                url = url,
                onClose = {
                    finish()
                }
            )
        }
    }
}


@Composable
private fun GeckoBrowserScreen(
    title: String,
    url: String,
    onClose: () -> Unit
) {

    val tunnelManager =
        TunnelManager.shared


    val scope =
        rememberCoroutineScope()


    var reconnecting by remember {
        mutableStateOf(false)
    }


    /*
     * Le navigateur n'est autorisé à afficher le Web
     * que lorsque le tunnel est réellement connecté.
     */
    val protected =
        tunnelManager.isProtected


    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
    ) {

        Column(
            modifier =
                Modifier.fillMaxSize()
        ) {

            // ====================================================
            // HEADER
            // ====================================================

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .background(Color.Black)
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 42.dp
                        ),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Spacer(
                    modifier =
                        Modifier.size(34.dp)
                )


                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )


                Box(
                    modifier =
                        Modifier
                            .size(34.dp)
                            .background(
                                Color.White.copy(alpha = 0.08f),
                                CircleShape
                            )
                            .border(
                                1.dp,
                                Color.Cyan.copy(alpha = 0.3f),
                                CircleShape
                            )
                            .clickable {
                                onClose()
                            },

                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text = "✕",
                        color = Color.Cyan,
                        fontWeight = FontWeight.Bold
                    )
                }
            }


            // ====================================================
            // BROWSER
            // ====================================================

            if (protected) {

                DeliriumGeckoView(
                    url = url,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                )

            } else {

                /*
                 * Aucune GeckoView n'est affichée sans VPN.
                 *
                 * Cela empêche notamment une nouvelle navigation
                 * depuis cette Activity pendant l'absence du tunnel.
                 */
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color.Black),

                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text = "Navigation suspendue",
                        color = Color.White.copy(alpha = 0.45f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }


        // ========================================================
        // VPN LOST
        // ========================================================

        if (!protected) {

            VpnLostDialog(
                isReconnecting = reconnecting,

                onReconnect = {

                    if (!reconnecting) {

                        scope.launch {

                            reconnecting = true

                            try {

                                tunnelManager.connect()

                            } catch (_: Exception) {

                            } finally {

                                reconnecting = false
                            }
                        }
                    }
                },

                onClose = {
                    onClose()
                }
            )
        }
    }
}