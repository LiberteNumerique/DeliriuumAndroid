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
import androidx.compose.ui.platform.LocalContext
import com.deliriuum.app.R

/**
 * Frequently asked questions, mirroring the FAQ section added to
 * deliriuum.com. Opened from the side menu.
 */
@Composable
fun FAQScreen(onDismiss: () -> Unit) {
    val _i18nContext = LocalContext.current

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
                question = _i18nContext.getString(R.string.faq_deliriuum_peut_il_voir_mes_mots_de_passe_des_reseaux_soc),
                answer = _i18nContext.getString(R.string.faq_non_ton_mot_de_passe_est_chiffre_en_https_directement_pa)
            )

            FAQItem(
                question = _i18nContext.getString(R.string.faq_pourquoi_deliriuum_est_il_gratuit),
                answer = _i18nContext.getString(R.string.faq_parce_que_ta_vie_privee_ne_devrait_jamais_avoir_de_prix)
            )

            FAQItem(
                question = _i18nContext.getString(R.string.faq_deliriuum_garde_t_il_un_historique_de_ma_navigation),
                answer = _i18nContext.getString(R.string.faq_non_aucune_trace_des_sites_visites_des_horaires_de_conne)
            )

            FAQItem(
                question = _i18nContext.getString(R.string.faq_est_ce_legal_d_utiliser_un_vpn),
                answer = _i18nContext.getString(R.string.faq_oui_en_france_et_dans_l_union_europeenne_l_utilisation_d)
            )

            FAQItem(
                question = _i18nContext.getString(R.string.faq_que_se_passe_t_il_si_je_desactive_la_protection),
                answer = _i18nContext.getString(R.string.faq_les_reseaux_sociaux_deviennent_inaccessibles_depuis_l_ap)
            )

            FAQItem(
                question = _i18nContext.getString(R.string.faq_le_code_source_est_il_consultable),
                answer = _i18nContext.getString(R.string.faq_oui_le_code_de_l_application_ios_et_android_est_open_sou)
            )

            FAQItem(
                question = _i18nContext.getString(R.string.faq_comment_supprimer_mon_compte),
                answer = _i18nContext.getString(R.string.faq_directement_depuis_l_app_dans_mon_compte_supprimer_mon_c)
            )
        }
    }
}

@Composable
private fun Header() {
    val _i18nContext = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            _i18nContext.getString(R.string.faq_questions_frequentes),
            color = Color.Cyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            _i18nContext.getString(R.string.faq_tu_te_poses_des_questions_c_est_normal),
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