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
import androidx.compose.ui.platform.LocalContext
import com.deliriuum.app.R

private data class PrivacyTip(
    val icon: String,
    val title: String,
    val text: String
)

@Composable
fun PrivacyGuideScreen(onDismiss: () -> Unit) {
    val _i18nContext = LocalContext.current

    val tips = listOf(
        PrivacyTip(
            icon = "📍",
            title = _i18nContext.getString(R.string.privacy_guide_desactive_la_localisation_precise),
            text = _i18nContext.getString(R.string.privacy_guide_parametres_securite_et_confidentialite_confidentialite_g)
        ),
        PrivacyTip(
            icon = "🚫",
            title = _i18nContext.getString(R.string.privacy_guide_limite_le_suivi_publicitaire),
            text = _i18nContext.getString(R.string.privacy_guide_parametres_securite_et_confidentialite_confidentialite_a)
        ),
        PrivacyTip(
            icon = "📸",
            title = _i18nContext.getString(R.string.privacy_guide_verifie_la_camera_et_le_micro),
            text = _i18nContext.getString(R.string.privacy_guide_passe_regulierement_en_revue_le_gestionnaire_d_autorisat)
        ),
        PrivacyTip(
            icon = "🌐",
            title = _i18nContext.getString(R.string.privacy_guide_evite_les_wi_fi_publics_ouverts),
            text = _i18nContext.getString(R.string.privacy_guide_active_systematiquement_le_tunnel_deliriuum_des_que_tu_t)
        ),
        PrivacyTip(
            icon = "🔑",
            title = _i18nContext.getString(R.string.privacy_guide_utilise_des_cles_uniques),
            text = _i18nContext.getString(R.string.privacy_guide_un_gestionnaire_robuste_comme_google_password_manager_bi)
        ),
        PrivacyTip(
            icon = "🔒",
            title = _i18nContext.getString(R.string.privacy_guide_masque_le_contenu_sur_l_ecran_verrouille),
            text = _i18nContext.getString(R.string.privacy_guide_parametres_notifications_notifications_sur_l_ecran_verro)
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
                    text = _i18nContext.getString(R.string.side_menu_guide_d_hygiene_numerique),
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
                text = _i18nContext.getString(R.string.privacy_guide_quelques_reglages_simples_pour_reprendre_le_controle_de),
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