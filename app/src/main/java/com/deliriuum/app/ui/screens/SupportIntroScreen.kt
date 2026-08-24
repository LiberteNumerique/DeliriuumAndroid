package com.deliriuum.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import com.deliriuum.app.data.AuthManager
import com.deliriuum.app.data.TunnelManager

@Composable
fun SupportIntroScreen(
    onContinue: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF3A2600),
                        Color(0xFF101018),
                        Color.Black
                    ),
                    radius = 1100f
                )
            )
            .padding(horizontal = 26.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 90.dp, bottom = 34.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "✦",
                color = Color(0xFFFFC633),
                fontSize = 34.sp
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "BIENVENUE DANS\nLA RÉSISTANCE NUMÉRIQUE",
                color = Color(0xFFFFC633),
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.3.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(34.dp))

            Text(
                text = "Pendant que les autres monétisent leurs utilisateurs,\nDeliriuum protège les siens.",
                color = Color.White,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 32.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Notre seule ressource,\nc'est votre soutien.",
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 18.sp,
                lineHeight = 27.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(26.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color.White.copy(alpha = 0.06f),
                        RoundedCornerShape(24.dp)
                    )
                    .border(
                        1.dp,
                        Color(0xFFFFC633).copy(alpha = 0.28f),
                        RoundedCornerShape(24.dp)
                    )
                    .padding(22.dp)
            ) {
                Text(
                    text = "Chaque don finance les serveurs,\nle développement de Deep Shield\net l'indépendance du projet.",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = "La vie privée n'est pas un luxe.\nC'est un droit.",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 26.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(26.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFFFFC633), Color(0xFFFFA626))
                        ),
                        RoundedCornerShape(18.dp)
                    )
                    .clickable {

                        val authManager =
                            AuthManager.shared

                        val tunnelManager =
                            TunnelManager.shared

                        val url =
                            "https://deliriuum.com/soutenir.html"

                        if (
                            authManager.isLoggedIn &&
                            tunnelManager.isProtected
                        ) {

                            val intent =
                                Intent(
                                    context,
                                    GeckoBrowserActivity::class.java
                                ).apply {

                                    putExtra(
                                        "url",
                                        url
                                    )

                                    putExtra(
                                        "title",
                                        "Soutenir Deliriuum"
                                    )
                                }

                            context.startActivity(
                                intent
                            )

                        } else {

                            val intent =
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(
                                        url
                                    )
                                )

                            context.startActivity(
                                intent
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Rejoindre les soutiens",
                    color = Color.Black,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .border(
                        1.5.dp,
                        Color(0xFFFFC633).copy(alpha = 0.65f),
                        RoundedCornerShape(18.dp)
                    )
                    .clickable { onContinue() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Continuer gratuitement",
                    color = Color(0xFFFFC633),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}