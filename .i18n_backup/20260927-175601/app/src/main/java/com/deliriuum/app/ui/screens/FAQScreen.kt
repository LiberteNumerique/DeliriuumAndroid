package com.deliriuum.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Frequently asked questions, mirroring the FAQ section added to
 * deliriuum.com. Opened from the side menu.
 */
@Composable
fun FAQScreen(onDismiss: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        // --- Background, matching the rest of the app's dark + radial glow ---
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF050508)))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF005973), Color(0x8C2E1459), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(x = 500f, y = 100f),
                        radius = 1000f
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
            // Top bar
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("FAQ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.White.copy(alpha = 0.08f), shape = CircleShape)
                        .border(1.dp, Color.Cyan.copy(alpha = 0.3f), shape = CircleShape)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = Color.Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Header()

            FAQItem(
                question = "Deliriuum peut-il voir mes mots de passe des réseaux sociaux ?",
                answer = "Non. Ton mot de passe est chiffré en HTTPS directement par ton appareil avant même de quitter ton téléphone, puis transite à travers notre tunnel WireGuard sans jamais être déchiffré en cours de route — impossible d'y accéder, même pour nous. Deliriuum applique une politique stricte de zéro log : aucune trace de ta navigation n'est conservée."
            )

            FAQItem(
                question = "Pourquoi Deliriuum est-il gratuit ?",
                answer = "Parce que ta vie privée ne devrait jamais avoir de prix. Le projet est financé uniquement par les dons de la communauté — jamais obligatoires, jamais bloquants. Pas d'abonnement, pas de limite de temps, pas de revente de données à des tiers."
            )

            FAQItem(
                question = "Deliriuum garde-t-il un historique de ma navigation ?",
                answer = "Non. Aucune trace des sites visités, des horaires de connexion ou des volumes de données n'est conservée après ta session."
            )

            FAQItem(
                question = "Est-ce légal d'utiliser un VPN ?",
                answer = "Oui, en France et dans l'Union Européenne, l'utilisation d'un VPN est parfaitement légale. Deliriuum ne facilite aucune activité illégale — l'app sert à protéger ta vie privée, pas à contourner la loi."
            )

            FAQItem(
                question = "Que se passe-t-il si je désactive la protection ?",
                answer = "Les réseaux sociaux deviennent inaccessibles depuis l'app tant que la protection n'est pas réactivée — pour éviter de naviguer sans protection sans t'en rendre compte."
            )

            FAQItem(
                question = "Le code source est-il consultable ?",
                answer = "Oui. Le code de l'application (iOS et Android) est open source, sous licence GPL-3.0. Tu peux le consulter, le vérifier, et même contribuer."
            )

            FAQItem(
                question = "Comment supprimer mon compte ?",
                answer = "Directement depuis l'app, dans Mon compte → Supprimer mon compte, avec confirmation par mot de passe. La suppression est immédiate et définitive — toutes tes données associées sont effacées."
            )
        }
    }
}

@Composable
private fun Header() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "QUESTIONS FRÉQUENTES",
            color = Color.Cyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Tu te poses des questions ?\nC'est normal.",
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun FAQItem(question: String, answer: String) {
    var isOpen by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(targetValue = if (isOpen) 45f else 0f, label = "plusRotation")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .background(Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape = RoundedCornerShape(16.dp))
            .clickable { isOpen = !isOpen }
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                question,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                "+",
                color = if (isOpen) Color.Cyan else Color.White.copy(alpha = 0.5f),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.rotate(rotation)
            )
        }

        if (isOpen) {
            Text(
                answer,
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}