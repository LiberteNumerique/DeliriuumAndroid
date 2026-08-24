package com.deliriuum.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deliriuum.app.R
import com.deliriuum.app.data.AuthManager
import com.deliriuum.app.ui.screens.GeckoBrowserActivity

@Composable
fun SideMenuLayout(
    isOpen: Boolean,
    onClose: () -> Unit,
    authManager: AuthManager,
    onOpenAccount: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenFAQ: () -> Unit,
    onLogout: () -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val menuWidth = 280.dp

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    LaunchedEffect(isOpen) {
        if (isOpen) {
            drawerState.open()
        } else {
            drawerState.close()
        }
    }

    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Closed && isOpen) {
            onClose()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = isOpen,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color.Black,
                drawerShape = androidx.compose.ui.graphics.RectangleShape,
                modifier = Modifier
                    .width(menuWidth)
                    .fillMaxHeight()
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val widthPx = with(density) { maxWidth.toPx() }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF005973).copy(alpha = 0.5f),
                                        Color(0x662E1459),
                                        Color.Transparent
                                    ),
                                    center = androidx.compose.ui.geometry.Offset(
                                        x = widthPx * 0.3f,
                                        y = widthPx * 0.1f
                                    ),
                                    radius = with(density) { 300.dp.toPx() }
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp)
                            .padding(top = 70.dp, bottom = 32.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.logo),
                                contentDescription = "Deliriuum Logo",
                                modifier = Modifier.size(36.dp)
                            )

                            Text(
                                text = "DELIRIUUM",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 16.dp),
                            color = Color.White.copy(alpha = 0.1f)
                        )

                        MenuItem(
                            icon = "👤",
                            title = "Mon compte",
                            iconColor = Color.Cyan
                        ) {
                            onOpenAccount()
                            onClose()
                        }

                        MenuItem(
                            icon = "ℹ️",
                            title = "À propos de Deliriuum",
                            iconColor = Color.Cyan
                        ) {
                            onOpenAbout()
                            onClose()
                        }

                        MenuItem(
                            icon = "❓",
                            title = "FAQ",
                            iconColor = Color.Cyan
                        ) {
                            onOpenFAQ()
                            onClose()
                        }

                        MenuItem(
                            icon = "📖",
                            title = "Guide d'hygiène numérique",
                            iconColor = Color.Cyan
                        ) {
                            onOpenGuide()
                            onClose()
                        }

                        MenuItem(
                            icon = "❤️",
                            title = "Soutenir le projet",
                            iconColor = Color(0xFFFFC107)
                        ) {
                            onClose()
                            val intent = Intent(
                                context,
                                GeckoBrowserActivity::class.java
                            ).apply {
                                putExtra("url", "https://deliriuum.com/soutenir.html")
                                putExtra("title", "Soutenir Deliriuum")
                            }

                            context.startActivity(intent)
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        if (authManager.isLoggedIn) {
                            HorizontalDivider(
                                modifier = Modifier.padding(bottom = 12.dp),
                                color = Color.White.copy(alpha = 0.1f)
                            )

                            MenuItem(
                                icon = "🚪",
                                title = "Se déconnecter",
                                iconColor = Color.Red.copy(alpha = 0.85f)
                            ) {
                                onLogout()
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(Color.White.copy(alpha = 0.08f))
                            .align(Alignment.CenterEnd)
                    )
                }
            }
        },
        content = content
    )
}

@Composable
private fun MenuItem(
    icon: String,
    title: String,
    iconColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = icon,
            color = iconColor,
            fontSize = 16.sp,
            modifier = Modifier.width(22.dp)
        )

        Text(
            text = title,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}