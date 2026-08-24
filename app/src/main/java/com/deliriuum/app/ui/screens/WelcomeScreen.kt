package com.deliriuum.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deliriuum.app.R

@Composable
fun WelcomeScreen(onContinueClick: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // --- ANIMATION DU LOGO (.easeInOut de 1.6s comme sur iOS) ---
    val infiniteTransition = rememberInfiniteTransition(label = "logoPulse")
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }

        // Calcul du rayon de 420 points iOS transformés en pixels équivalents
        val endRadiusPx = with(density) { 420.dp.toPx() }

        // --- BACKGROUND DUPLICATA COMPLET DU RADIALGRADIENT APPLE ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF005973), // Color(red: 0.0, green: 0.35, blue: 0.45)
                            Color(0x8C2E1459), // Color(red: 0.18, green: 0.08, blue: 0.35).opacity(0.55)
                            Color.Transparent
                        ),
                        center = androidx.compose.ui.geometry.Offset(
                            x = screenWidthPx * 0.5f,
                            y = screenHeightPx * 0.35f // Centré exactement à y: 0.35 comme défini sur ton iOS
                        ),
                        radius = endRadiusPx
                    )
                )
        )

        // --- ARBORESCENCE DE L'INTERFACE ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // VStack principal (spacing: 24)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.wrapContentHeight()
            ) {
                // Image Logo + Animation de pulsation de l'opacité/scale
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "delirium_logo",
                    modifier = Modifier
                        .size(120.dp)
                        .scale(logoScale)
                )

                // VStack (spacing: 14)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // VStack (spacing: 8)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "BIEN PLUS QU'UN SIMPLE VPN",
                            color = Color.Cyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black, // .heavy
                            fontFamily = FontFamily.Monospace, // .monospaced
                            letterSpacing = 1.5.sp
                        )

                        Text(
                            text = "L'APP POUR SURVIVRE EN DELIRISTAN",
                            color = Color(0xFFC08CFF), // Color(red: 0.75, green: 0.55, blue: 1.0)
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.5.sp
                        )
                    }

                    Text(
                        text = "DELIRIUUM",
                        color = Color.White,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Black, // .black
                        letterSpacing = 3.sp,
                        fontFamily = FontFamily.SansSerif // Équivalent visuel du .rounded lourd
                    )

                    // VStack (spacing: 2)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Ta vie privée",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "n'est pas",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "à vendre.",
                                color = Color(0xFFFFC033), // Color(red: 1.0, green: 0.75, blue: 0.2)
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                Text(
                    text = "La plupart des VPN cachent seulement ton IP. Deliriuum va plus loin : en plus du tunnel chiffré, notre technologie Deep Shield modifie l'empreinte de ton appareil. Pour les réseaux sociaux, tu n'es plus en Europe. Fini le ciblage.",
                    color = Color.White,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp,
                    modifier = Modifier
                        .padding(horizontal = 28.dp)
                        .fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // --- SECTION BOUTONS (padding horizontal: 28, bottom: 40) ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .padding(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Bouton Continuer (LinearGradient de .cyan à .mint)
                val buttonGradient = Brush.horizontalGradient(
                    colors = listOf(Color.Cyan, Color(0xFF98FFD9))
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(buttonGradient, RoundedCornerShape(16.dp))
                        .clickable {
                            onContinueClick()
                            Unit // Sécurité anti-bug d'interopérabilité (Void vs Unit)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Continuer",
                        color = Color.Black,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Link En savoir plus
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(Color.Cyan.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .border(
                            width = 1.5.dp,
                            color = Color.Cyan.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://deliriuum.com"))
                            context.startActivity(intent)
                            Unit // Sécurité
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "En savoir plus",
                            color = Color.Cyan,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "↗",
                            color = Color.Cyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}