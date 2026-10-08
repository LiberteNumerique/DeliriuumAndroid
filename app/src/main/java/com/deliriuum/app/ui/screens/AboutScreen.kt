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
    val _i18nContext = LocalContext.current

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
                    text = _i18nContext.getString(R.string.about_a_propos),
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
                                _i18nContext.getString(R.string.about_un_espace_web_protege),
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
                            _i18nContext.getString(R.string.about_deliriuum_reunit_dans_une_meme_application_un_tunnel_vpn),
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
                            _i18nContext.getString(R.string.about_recherche_web_integree)
                    )

                    InfoBadge(
                        text =
                            _i18nContext.getString(R.string.about_navigation_par_adresse)
                    )

                    InfoBadge(
                        text =
                            _i18nContext.getString(R.string.about_reseaux_sociaux_dans_le_navigateur_protege)
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
                                _i18nContext.getString(R.string.privacy_audit_manager_tunnel_vpn_wireguard),
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
                            _i18nContext.getString(R.string.about_lorsque_la_protection_est_active_la_connexion_passe_par),
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
                            _i18nContext.getString(R.string.about_ipv4_et_ipv6_pris_en_charge)
                    )

                    InfoBadge(
                        text =
                            _i18nContext.getString(R.string.about_dns_route_avec_la_connexion_vpn)
                    )

                    InfoBadge(
                        text =
                            _i18nContext.getString(R.string.about_navigation_deliriuum_suspendue_si_le_tunnel_est_perdu)
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
                                _i18nContext.getString(R.string.about_protection_de_navigation),
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
                            _i18nContext.getString(R.string.about_si_le_tunnel_devient_indisponible_deliriuum_suspend_auto),
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
                            _i18nContext.getString(R.string.about_watchdog_reseau_actif)
                    )

                    InfoBadge(
                        text =
                            _i18nContext.getString(R.string.about_geckoview_inaccessible_hors_protection)
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
                        _i18nContext.getString(R.string.about_canvas_webgl),
                    text =
                        _i18nContext.getString(R.string.about_deep_shield_reduit_les_informations_graphiques_exploitab)
                )

                FeatureRow(
                    emoji = "🎧",
                    title =
                        _i18nContext.getString(R.string.about_empreinte_audio_reduite),
                    text =
                        _i18nContext.getString(R.string.about_les_informations_exposees_par_audiocontext_sont_normalis)
                )

                FeatureRow(
                    emoji = "🔤",
                    title =
                        _i18nContext.getString(R.string.about_polices_limitees),
                    text =
                        _i18nContext.getString(R.string.about_l_ensemble_de_polices_detectables_par_les_pages_est_fort)
                )

                FeatureRow(
                    emoji = "🎥",
                    title =
                        _i18nContext.getString(R.string.about_peripheriques_media),
                    text =
                        _i18nContext.getString(R.string.about_les_libelles_et_identifiants_de_micro_et_camera_ne_sont)
                )

                FeatureRow(
                    emoji = "📡",
                    title =
                        _i18nContext.getString(R.string.about_webrtc_limite),
                    text =
                        _i18nContext.getString(R.string.about_les_candidats_reseau_webrtc_susceptibles_de_reveler_des)
                )

                FeatureRow(
                    emoji = "🌍",
                    title =
                        _i18nContext.getString(R.string.about_coherence_geographique),
                    text =
                        _i18nContext.getString(R.string.about_le_navigateur_adapte_certaines_informations_notamment_le)
                )

                FeatureRow(
                    emoji = "📶",
                    title =
                        _i18nContext.getString(R.string.about_informations_reseau_reduites),
                    text =
                        _i18nContext.getString(R.string.about_l_api_network_information_n_est_pas_exposee_au_contenu_w)
                )

                FeatureRow(
                    emoji = "🧩",
                    title =
                        _i18nContext.getString(R.string.about_protections_natives_gecko),
                    text =
                        _i18nContext.getString(R.string.about_deliriuum_s_appuie_sur_les_mecanismes_anti_fingerprintin)
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
                            _i18nContext.getString(R.string.about_ce_que_deep_shield_reduit_ou_bloque),
                        color =
                            Color.White,
                        fontSize =
                            17.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    SecurityLine(
                        text =
                            _i18nContext.getString(R.string.about_exposition_directe_de_l_adresse_ip_habituelle_dans_la_na)
                    )

                    SecurityLine(
                        text =
                            _i18nContext.getString(R.string.about_fuite_webrtc_de_candidats_reseau)
                    )

                    SecurityLine(
                        text =
                            _i18nContext.getString(R.string.about_identification_precise_du_gpu_via_webgl)
                    )

                    SecurityLine(
                        text =
                            _i18nContext.getString(R.string.about_inventaire_detaille_des_polices_disponibles)
                    )

                    SecurityLine(
                        text =
                            _i18nContext.getString(R.string.about_exposition_des_identifiants_de_peripheriques_media)
                    )

                    SecurityLine(
                        text =
                            _i18nContext.getString(R.string.about_incoherence_evidente_entre_ip_vpn_et_fuseau_horaire)
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
                                _i18nContext.getString(R.string.about_ce_que_deliriuum_ne_pretend_pas_faire),
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
                            _i18nContext.getString(R.string.about_deliriuum_reduit_le_pistage_et_certaines_possibilites_de),
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
                    label = _i18nContext.getString(R.string.about_acces),
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
                    label = _i18nContext.getString(R.string.about_donnee_revendue),
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
                            _i18nContext.getString(R.string.about_le_projet_vit_grace_a_vous),
                        color =
                            Color.White,
                        fontSize =
                            17.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        text =
                            _i18nContext.getString(R.string.about_deliriuum_est_gratuit_et_ne_repose_pas_sur_la_revente_de),
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
                                _i18nContext.getString(R.string.about_faire_un_don),
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
    val _i18nContext = LocalContext.current

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
                _i18nContext.getString(R.string.about_logo_deliriuum),
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
                    _i18nContext.getString(R.string.about_bien_plus_qu_un_simple_vpn),
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
                    _i18nContext.getString(R.string.about_l_app_pour_survivre_au_deliristan),
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
                _i18nContext.getString(R.string.about_un_tunnel_vpn_pour_masquer_votre_adresse_ip_un_navigateu),
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
    val _i18nContext = LocalContext.current

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
                _i18nContext.getString(R.string.about_deep_shield),
            color =
                Color.White,
            fontSize =
                20.sp,
            fontWeight =
                FontWeight.Bold
        )

        Text(
            text =
                _i18nContext.getString(R.string.about_le_vpn_agit_sur_votre_adresse_ip_deep_shield_agit_sur_le),
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
    val _i18nContext = LocalContext.current

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
                _i18nContext.getString(R.string.about_text, text),
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