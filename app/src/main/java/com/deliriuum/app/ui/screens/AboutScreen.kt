package com.deliriuum.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deliriuum.app.R


@Composable
fun AboutScreen(
    onDismiss: () -> Unit
) {
    val context =
        LocalContext.current

    Box(
        modifier =
            Modifier.fillMaxSize()
    ) {
        // ========================================================
        // BACKGROUND
        // ========================================================

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
        )

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    Color(0xFF005973),
                                    Color(0x8C2E1459),
                                    Color.Transparent
                                ),
                            center =
                                androidx.compose.ui.geometry.Offset(
                                    x = 500f,
                                    y = 100f
                                ),
                            radius = 1000f
                        )
                    )
        )

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color.Transparent,
                                    Color(0xFF050508)
                                )
                        )
                    )
        )

        // ========================================================
        // CONTENT
        // ========================================================

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(
                        rememberScrollState()
                    )
                    .padding(
                        horizontal = 22.dp
                    )
                    .padding(
                        top = 24.dp,
                        bottom = 40.dp
                    ),
            verticalArrangement =
                Arrangement.spacedBy(
                    24.dp
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {
            // ====================================================
            // HEADER
            // ====================================================

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Spacer(
                    modifier =
                        Modifier.size(32.dp)
                )

                Text(
                    text = "À propos",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                Box(
                    modifier =
                        Modifier
                            .size(32.dp)
                            .background(
                                Color.White.copy(
                                    alpha = 0.08f
                                ),
                                CircleShape
                            )
                            .border(
                                1.dp,
                                Color.Cyan.copy(
                                    alpha = 0.3f
                                ),
                                CircleShape
                            )
                            .clickable {
                                onDismiss()
                            },
                    contentAlignment =
                        Alignment.Center
                ) {
                    Text(
                        text = "✕",
                        color = Color.Cyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ====================================================
            // INTRO
            // ====================================================

            IntroSection()

            // ====================================================
            // WHAT DELIRIIUM IS
            // ====================================================

            CardBox {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(
                            12.dp
                        )
                ) {
                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                10.dp
                            ),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🧭",
                            fontSize = 22.sp
                        )

                        Text(
                            text =
                                "Un espace Web protégé",
                            color =
                                Color.White,
                            fontSize =
                                17.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }

                    Text(
                        text =
                            "Deliriuum réunit dans une même application un tunnel VPN, " +
                                    "un navigateur GeckoView protégé par Deep Shield, " +
                                    "un moteur de recherche, une navigation Web directe " +
                                    "et des accès aux principaux réseaux sociaux.",
                        color =
                            Color.White.copy(
                                alpha = 0.75f
                            ),
                        fontSize =
                            14.sp,
                        lineHeight =
                            21.sp
                    )

                    InfoBadge(
                        text =
                            "Recherche Web intégrée"
                    )

                    InfoBadge(
                        text =
                            "Navigation par adresse"
                    )

                    InfoBadge(
                        text =
                            "Réseaux sociaux dans le navigateur protégé"
                    )
                }
            }

            // ====================================================
            // VPN
            // ====================================================

            CardBox {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(
                            12.dp
                        )
                ) {
                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                10.dp
                            ),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔐",
                            fontSize = 22.sp
                        )

                        Text(
                            text =
                                "Tunnel VPN WireGuard",
                            color =
                                Color.White,
                            fontSize =
                                17.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }

                    Text(
                        text =
                            "Lorsque la protection est active, la connexion passe par " +
                                    "un tunnel WireGuard. Les sites voient l'adresse IP du serveur " +
                                    "de sortie au lieu de l'adresse IP publique habituelle de votre connexion.",
                        color =
                            Color.White.copy(
                                alpha = 0.75f
                            ),
                        fontSize =
                            14.sp,
                        lineHeight =
                            21.sp
                    )

                    InfoBadge(
                        text =
                            "IPv4 et IPv6 pris en charge"
                    )

                    InfoBadge(
                        text =
                            "DNS routé avec la connexion VPN"
                    )

                    InfoBadge(
                        text =
                            "Navigation Deliriuum suspendue si le tunnel est perdu"
                    )
                }
            }

            // ====================================================
            // PROTECTION MODEL
            // ====================================================

            CardBox {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(
                            12.dp
                        )
                ) {
                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                10.dp
                            ),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🛡️",
                            fontSize = 22.sp
                        )

                        Text(
                            text =
                                "Protection de navigation",
                            color =
                                Color.White,
                            fontSize =
                                17.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }

                    Text(
                        text =
                            "Si le tunnel devient indisponible, Deliriuum suspend automatiquement " +
                                    "son navigateur intégré, la recherche et les accès Web. " +
                                    "Les autres applications du téléphone et leur accès Internet " +
                                    "ne sont pas bloqués.",
                        color =
                            Color.White.copy(
                                alpha = 0.75f
                            ),
                        fontSize =
                            14.sp,
                        lineHeight =
                            21.sp
                    )

                    InfoBadge(
                        text =
                            "Watchdog réseau actif"
                    )

                    InfoBadge(
                        text =
                            "GeckoView inaccessible hors protection"
                    )
                }
            }

            // ====================================================
            // DEEP SHIELD
            // ====================================================

            DeepShieldHeader()

            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        12.dp
                    )
            ) {
                FeatureRow(
                    emoji = "🎨",
                    title =
                        "Canvas & WebGL",
                    text =
                        "Deep Shield réduit les informations graphiques exploitables " +
                                "pour construire une empreinte. Le rendu WebGL sensible est masqué " +
                                "et Canvas bénéficie des protections anti-fingerprinting de Gecko."
                )

                FeatureRow(
                    emoji = "🎧",
                    title =
                        "Empreinte audio réduite",
                    text =
                        "Les informations exposées par AudioContext sont normalisées afin " +
                                "de réduire les différences liées au matériel audio."
                )

                FeatureRow(
                    emoji = "🔤",
                    title =
                        "Polices limitées",
                    text =
                        "L'ensemble de polices détectables par les pages est fortement réduit, " +
                                "ce qui diminue une source classique d'identification du terminal."
                )

                FeatureRow(
                    emoji = "🎥",
                    title =
                        "Périphériques média",
                    text =
                        "Les libellés et identifiants de micro et caméra ne sont pas exposés " +
                                "aux pages avant autorisation, ce qui réduit les informations disponibles " +
                                "pour le fingerprinting."
                )

                FeatureRow(
                    emoji = "📡",
                    title =
                        "WebRTC limité",
                    text =
                        "Les candidats réseau WebRTC susceptibles de révéler des informations " +
                                "sur le réseau local sont bloqués ou fortement limités."
                )

                FeatureRow(
                    emoji = "🌍",
                    title =
                        "Cohérence géographique",
                    text =
                        "Le navigateur adapte certaines informations, notamment le fuseau horaire, " +
                                "au profil de sortie VPN afin d'éviter des incohérences évidentes " +
                                "entre l'adresse IP visible et l'environnement du navigateur."
                )

                FeatureRow(
                    emoji = "📶",
                    title =
                        "Informations réseau réduites",
                    text =
                        "L'API Network Information n'est pas exposée au contenu Web, ce qui évite " +
                                "de révéler directement le type de connexion, la latence ou le débit estimé."
                )

                FeatureRow(
                    emoji = "🧩",
                    title =
                        "Protections natives Gecko",
                    text =
                        "Deliriuum s'appuie sur les mécanismes anti-fingerprinting de Gecko " +
                                "et ajoute une couche de cohérence dédiée à son environnement VPN."
                )
            }

            // ====================================================
            // TESTED / TARGETED PROTECTIONS
            // ====================================================

            CardBox {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(
                            14.dp
                        )
                ) {
                    Text(
                        text =
                            "Ce que Deep Shield réduit ou bloque",
                        color =
                            Color.White,
                        fontSize =
                            17.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    SecurityLine(
                        text =
                            "Exposition directe de l'adresse IP habituelle dans la navigation protégée"
                    )

                    SecurityLine(
                        text =
                            "Fuite WebRTC de candidats réseau"
                    )

                    SecurityLine(
                        text =
                            "Identification précise du GPU via WebGL"
                    )

                    SecurityLine(
                        text =
                            "Inventaire détaillé des polices disponibles"
                    )

                    SecurityLine(
                        text =
                            "Exposition des identifiants de périphériques média"
                    )

                    SecurityLine(
                        text =
                            "Incohérence évidente entre IP VPN et fuseau horaire"
                    )
                }
            }

            // ====================================================
            // LIMITS
            // ====================================================

            CardBox {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(
                            10.dp
                        )
                ) {
                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                10.dp
                            ),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ℹ️",
                            fontSize = 20.sp
                        )

                        Text(
                            text =
                                "Ce que Deliriuum ne prétend pas faire",
                            color =
                                Color.White,
                            fontSize =
                                16.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }

                    Text(
                        text =
                            "Deliriuum réduit le pistage et certaines possibilités de corrélation, " +
                                    "mais aucun navigateur ne peut garantir l'anonymat absolu. " +
                                    "Si vous vous connectez à un compte personnel, le service concerné " +
                                    "continue de vous reconnaître par ce compte. Certaines caractéristiques " +
                                    "du navigateur, du protocole réseau ou du système peuvent également rester observables.",
                        color =
                            Color.White.copy(
                                alpha = 0.68f
                            ),
                        fontSize =
                            13.sp,
                        lineHeight =
                            20.sp
                    )
                }
            }

            // ====================================================
            // STATS
            // ====================================================

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        12.dp
                    ),
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                StatBlock(
                    value = "0 €",
                    label = "Accès",
                    modifier =
                        Modifier.weight(1f)
                )

                StatBlock(
                    value = "WG",
                    label = "WireGuard",
                    modifier =
                        Modifier.weight(1f)
                )

                StatBlock(
                    value = "0",
                    label = "Donnée revendue",
                    modifier =
                        Modifier.weight(1f)
                )
            }

            // ====================================================
            // SUPPORT
            // ====================================================

            CardBox {
                Column(
                    horizontalAlignment =
                        Alignment.CenterHorizontally,
                    verticalArrangement =
                        Arrangement.spacedBy(
                            12.dp
                        )
                ) {
                    Text(
                        text =
                            "Le projet vit grâce à vous",
                        color =
                            Color.White,
                        fontSize =
                            17.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        text =
                            "Deliriuum est gratuit et ne repose pas sur la revente de données. " +
                                    "Le projet est financé par les dons de la communauté, " +
                                    "sans obligation pour utiliser l'application.",
                        color =
                            Color.White.copy(
                                alpha = 0.65f
                            ),
                        fontSize =
                            13.sp,
                        lineHeight =
                            19.sp,
                        textAlign =
                            TextAlign.Center
                    )

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(
                                    45.dp
                                )
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color(0xFFFFC733),
                                            Color(0xFFFFA626)
                                        )
                                    ),
                                    RoundedCornerShape(
                                        14.dp
                                    )
                                )
                                .clickable {
                                    val intent =
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(
                                                "https://deliriuum.com/soutenir.html"
                                            )
                                        )

                                    context.startActivity(
                                        intent
                                    )
                                },
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Text(
                            text =
                                "Faire un don  ➔",
                            color =
                                Color.Black,
                            fontSize =
                                15.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}


// ================================================================
// INTRO
// ================================================================

@Composable
private fun IntroSection() {
    Column(
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(
                10.dp
            )
    ) {
        Image(
            painter =
                painterResource(
                    id = R.drawable.logo
                ),
            contentDescription =
                "Logo Deliriuum",
            modifier =
                Modifier
                    .size(
                        64.dp
                    )
                    .shadow(
                        elevation =
                            14.dp,
                        shape =
                            CircleShape,
                        ambientColor =
                            Color.Cyan.copy(
                                alpha = 0.4f
                            ),
                        spotColor =
                            Color.Cyan.copy(
                                alpha = 0.4f
                            )
                    )
        )

        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(
                    4.dp
                )
        ) {
            Text(
                text =
                    "BIEN PLUS QU'UN SIMPLE VPN",
                color =
                    Color.Cyan,
                fontSize =
                    12.sp,
                fontWeight =
                    FontWeight.Black,
                fontFamily =
                    FontFamily.Monospace,
                letterSpacing =
                    1.2.sp
            )

            Text(
                text =
                    "L'APP POUR SURVIVRE AU DELIRISTAN",
                color =
                    Color(0xFFC08CFF),
                fontSize =
                    12.sp,
                fontWeight =
                    FontWeight.Black,
                fontFamily =
                    FontFamily.Monospace,
                letterSpacing =
                    1.2.sp
            )
        }

        Text(
            text =
                "Un tunnel VPN pour masquer votre adresse IP. " +
                        "Un navigateur protégé pour réduire votre empreinte numérique.",
            color =
                Color.White,
            fontSize =
                15.sp,
            lineHeight =
                21.sp,
            textAlign =
                TextAlign.Center,
            modifier =
                Modifier.padding(
                    top = 6.dp
                )
        )
    }
}


// ================================================================
// DEEP SHIELD HEADER
// ================================================================

@Composable
private fun DeepShieldHeader() {
    Column(
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(
                8.dp
            ),
        modifier =
            Modifier.padding(
                top = 8.dp
            )
    ) {
        Text(
            text = "🛡️",
            fontSize = 32.sp
        )

        Text(
            text =
                "Deep Shield",
            color =
                Color.White,
            fontSize =
                20.sp,
            fontWeight =
                FontWeight.Bold
        )

        Text(
            text =
                "Le VPN agit sur votre adresse IP. Deep Shield agit sur les informations " +
                        "que les pages Web peuvent exploiter pour distinguer votre appareil : " +
                        "graphismes, audio, polices, périphériques, réseau et environnement.",
            color =
                Color.White.copy(
                    alpha = 0.75f
                ),
            fontSize =
                15.sp,
            lineHeight =
                21.sp,
            textAlign =
                TextAlign.Center,
            modifier =
                Modifier.padding(
                    horizontal = 8.dp
                )
        )
    }
}


// ================================================================
// CARD
// ================================================================

@Composable
private fun CardBox(
    content: @Composable () -> Unit
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    Color.White.copy(
                        alpha = 0.07f
                    ),
                    RoundedCornerShape(
                        20.dp
                    )
                )
                .border(
                    1.dp,
                    Color.White.copy(
                        alpha = 0.10f
                    ),
                    RoundedCornerShape(
                        20.dp
                    )
                )
                .padding(
                    18.dp
                )
    ) {
        content()
    }
}


// ================================================================
// FEATURE
// ================================================================

@Composable
private fun FeatureRow(
    emoji: String,
    title: String,
    text: String
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    Color.White.copy(
                        alpha = 0.07f
                    ),
                    RoundedCornerShape(
                        20.dp
                    )
                )
                .border(
                    1.dp,
                    Color.White.copy(
                        alpha = 0.10f
                    ),
                    RoundedCornerShape(
                        20.dp
                    )
                )
                .padding(
                    16.dp
                )
    ) {
        Row(
            horizontalArrangement =
                Arrangement.spacedBy(
                    14.dp
                ),
            verticalAlignment =
                Alignment.Top
        ) {
            Text(
                text =
                    emoji,
                fontSize =
                    22.sp
            )

            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        3.dp
                    )
            ) {
                Text(
                    text =
                        title,
                    color =
                        Color.White,
                    fontSize =
                        15.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    text =
                        text,
                    color =
                        Color.White.copy(
                            alpha = 0.6f
                        ),
                    fontSize =
                        13.sp,
                    lineHeight =
                        18.sp
                )
            }
        }
    }
}


// ================================================================
// INFO BADGE
// ================================================================

@Composable
private fun InfoBadge(
    text: String
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    Color.Cyan.copy(
                        alpha = 0.07f
                    ),
                    RoundedCornerShape(
                        50
                    )
                )
                .border(
                    1.dp,
                    Color.Cyan.copy(
                        alpha = 0.16f
                    ),
                    RoundedCornerShape(
                        50
                    )
                )
                .padding(
                    horizontal = 12.dp,
                    vertical = 8.dp
                ),
        contentAlignment =
            Alignment.Center
    ) {
        Text(
            text =
                "✓  $text",
            color =
                Color.Cyan.copy(
                    alpha = 0.88f
                ),
            fontSize =
                11.sp,
            fontWeight =
                FontWeight.Bold,
            textAlign =
                TextAlign.Center
        )
    }
}


// ================================================================
// SECURITY LINE
// ================================================================

@Composable
private fun SecurityLine(
    text: String
) {
    Row(
        horizontalArrangement =
            Arrangement.spacedBy(
                10.dp
            ),
        verticalAlignment =
            Alignment.Top
    ) {
        Text(
            text = "✓",
            color = Color(0xFF42E695),
            fontWeight = FontWeight.Black
        )

        Text(
            text = text,
            color =
                Color.White.copy(
                    alpha = 0.72f
                ),
            fontSize =
                13.sp,
            lineHeight =
                18.sp
        )
    }
}


// ================================================================
// STAT BLOCK
// ================================================================

@Composable
private fun StatBlock(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier =
            modifier
                .background(
                    Color.White.copy(
                        alpha = 0.07f
                    ),
                    RoundedCornerShape(
                        20.dp
                    )
                )
                .border(
                    1.dp,
                    Color.White.copy(
                        alpha = 0.10f
                    ),
                    RoundedCornerShape(
                        20.dp
                    )
                )
                .padding(
                    vertical = 14.dp
                ),
        contentAlignment =
            Alignment.Center
    ) {
        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(
                    4.dp
                )
        ) {
            Text(
                text =
                    value,
                color =
                    Color.Cyan,
                fontSize =
                    22.sp,
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                text =
                    label,
                color =
                    Color.White.copy(
                        alpha = 0.6f
                    ),
                fontSize =
                    11.sp,
                textAlign =
                    TextAlign.Center,
                modifier =
                    Modifier.padding(
                        horizontal = 4.dp
                    )
            )
        }
    }
}