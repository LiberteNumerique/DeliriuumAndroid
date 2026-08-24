package com.deliriuum.app.ui.screens

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class PrivacyTip(
    val icon: String,
    val title: String,
    val text: String
)

@Composable
fun PrivacyGuideScreen(onDismiss: () -> Unit) {
    val tips = listOf(
        PrivacyTip(
            icon = "📍",
            title = "Désactive la localisation précise",
            text = "Paramètres → Sécurité et confidentialité → Confidentialité → Gestionnaire d'autorisations → Position : retire l'accès ou désactive \"Utiliser la position exacte\" pour les apps non essentielles."
        ),
        PrivacyTip(
            icon = "🚫",
            title = "Limite le suivi publicitaire",
            text = "Paramètres → Sécurité et confidentialité → Confidentialité → Autres paramètres de confidentialité → Annonces : clique sur \"Supprimer l'identifiant publicitaire\"."
        ),
        PrivacyTip(
            icon = "📸",
            title = "Vérifie la caméra et le micro",
            text = "Passe régulièrement en revue le Gestionnaire d'autorisations pour bloquer les applications en arrière-plan qui n'ont aucune raison de t'écouter ou de te filmer."
        ),
        PrivacyTip(
            icon = "🌐",
            title = "Évite les Wi-Fi publics ouverts",
            text = "Active systématiquement le tunnel Deliriuum dès que tu te connectes au réseau d'un café, d'une gare ou d'un hôtel. C'est là que tes paquets réseaux sont le plus interceptables."
        ),
        PrivacyTip(
            icon = "🔑",
            title = "Utilise des clés uniques",
            text = "Un gestionnaire robuste comme Google Password Manager, Bitwarden ou Dashlane t'évitera la pire erreur : utiliser le même mot de passe pour ton email et tes autres comptes."
        ),
        PrivacyTip(
            icon = "🔒",
            title = "Masque le contenu sur l'écran verrouillé",
            text = "Paramètres → Notifications → Notifications sur l'écran verrouillé : sélectionne \"Masquer le contenu sensible\" pour éviter les regards indiscrets sur tes SMS de double-authentification."
        )
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF050508)))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF005973),
                            Color(0x8C2E1459),
                            Color.Transparent
                        ),
                        center = androidx.compose.ui.geometry.Offset(x = 500f, y = 100f),
                        radius = 1000f
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF050508)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = 24.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Guide d'hygiène numérique",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        .border(1.dp, Color.Cyan.copy(alpha = 0.3f), CircleShape)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✕",
                        color = Color.Cyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = "Quelques réglages simples pour reprendre le contrôle de ta vie privée sur Android, en complément de Deliriuum.",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            tips.forEach { tip ->
                TipRow(tip)
            }
        }
    }
}

@Composable
private fun TipRow(tip: PrivacyTip) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = tip.icon,
                fontSize = 20.sp,
                modifier = Modifier
                    .width(28.dp)
                    .wrapContentWidth(Alignment.CenterHorizontally)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = tip.title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = tip.text,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}