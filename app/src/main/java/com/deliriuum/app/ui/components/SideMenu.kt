package com.deliriuum.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.deliriuum.app.i18n.AppStrings


private data class LanguageOption(
    val tag: String,
    val nativeName: String
)


/*
 * IMPORTANT :
 *
 * Cette liste correspond exactement aux 30 locales générées pour Android :
 *
 * fr, en, de, es, it, pt, nl, pl, cs, sk, hu, ro, bg, hr, sl,
 * el, sv, da, no, fi, et, lv, lt, tr, uk, ru, ar, he, ja, ko.
 *
 * Les noms sont volontairement écrits dans leur propre langue.
 * Ils ne doivent donc PAS être traduits.
 */
private val LANGUAGE_OPTIONS =
    listOf(
        LanguageOption("fr", "Français"),
        LanguageOption("en", "English"),
        LanguageOption("de", "Deutsch"),
        LanguageOption("es", "Español"),
        LanguageOption("it", "Italiano"),
        LanguageOption("pt", "Português"),
        LanguageOption("nl", "Nederlands"),
        LanguageOption("pl", "Polski"),
        LanguageOption("cs", "Čeština"),
        LanguageOption("sk", "Slovenčina"),
        LanguageOption("hu", "Magyar"),
        LanguageOption("ro", "Română"),
        LanguageOption("bg", "Български"),
        LanguageOption("hr", "Hrvatski"),
        LanguageOption("sl", "Slovenščina"),
        LanguageOption("el", "Ελληνικά"),
        LanguageOption("sv", "Svenska"),
        LanguageOption("da", "Dansk"),
        LanguageOption("no", "Norsk"),
        LanguageOption("fi", "Suomi"),
        LanguageOption("et", "Eesti"),
        LanguageOption("lv", "Latviešu"),
        LanguageOption("lt", "Lietuvių"),
        LanguageOption("tr", "Türkçe"),
        LanguageOption("uk", "Українська"),
        LanguageOption("ru", "Русский"),
        LanguageOption("ar", "العربية"),
        LanguageOption("he", "עברית"),
        LanguageOption("ja", "日本語"),
        LanguageOption("ko", "한국어")
    )


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
    val _i18nContext = LocalContext.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val menuWidth = 280.dp

    var showLanguagePicker by
        remember {
            mutableStateOf(false)
        }

    var selectedLanguageTag by
        remember {
            mutableStateOf(
                AppStrings.selectedLanguageTag()
            )
        }

    val selectedLanguageLabel =
        if (
            selectedLanguageTag ==
            AppStrings.AUTOMATIC
        ) {
            _i18nContext.getString(
                R.string.language_automatic
            )
        } else {
            LANGUAGE_OPTIONS
                .firstOrNull {
                    it.tag == selectedLanguageTag
                }
                ?.nativeName
                ?: selectedLanguageTag
        }

    val drawerState =
        rememberDrawerState(
            initialValue =
                DrawerValue.Closed
        )

    LaunchedEffect(isOpen) {
        if (isOpen) {
            drawerState.open()
        } else {
            drawerState.close()
        }
    }

    LaunchedEffect(
        drawerState.currentValue
    ) {
        if (
            drawerState.currentValue ==
            DrawerValue.Closed &&
            isOpen
        ) {
            onClose()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = isOpen,
        scrimColor =
            Color.Black.copy(
                alpha = 0.5f
            ),
        drawerContent = {

            ModalDrawerSheet(
                drawerContainerColor =
                    Color.Black,
                drawerShape =
                    androidx.compose.ui.graphics
                        .RectangleShape,
                modifier =
                    Modifier
                        .width(menuWidth)
                        .fillMaxHeight()
            ) {

                BoxWithConstraints(
                    modifier =
                        Modifier.fillMaxSize()
                ) {

                    val widthPx =
                        with(density) {
                            maxWidth.toPx()
                        }

                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                Color(
                                                    0xFF005973
                                                ).copy(
                                                    alpha =
                                                        0.5f
                                                ),
                                                Color(
                                                    0x662E1459
                                                ),
                                                Color.Transparent
                                            ),
                                        center =
                                            androidx.compose.ui
                                                .geometry
                                                .Offset(
                                                    x =
                                                        widthPx *
                                                                0.3f,
                                                    y =
                                                        widthPx *
                                                                0.1f
                                                ),
                                        radius =
                                            with(density) {
                                                300.dp.toPx()
                                            }
                                    )
                                )
                    )

                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(
                                    horizontal =
                                        24.dp
                                )
                                .padding(
                                    top =
                                        70.dp,
                                    bottom =
                                        32.dp
                                )
                    ) {

                        Row(
                            verticalAlignment =
                                Alignment
                                    .CenterVertically,
                            horizontalArrangement =
                                Arrangement
                                    .spacedBy(
                                        12.dp
                                    )
                        ) {

                            Image(
                                painter =
                                    painterResource(
                                        id =
                                            R.drawable
                                                .logo
                                    ),
                                contentDescription =
                                    _i18nContext
                                        .getString(
                                            R.string
                                                .side_menu_deliriuum_logo
                                        ),
                                modifier =
                                    Modifier
                                        .size(
                                            36.dp
                                        )
                            )

                            Text(
                                text =
                                    "DELIRIUUM",
                                color =
                                    Color.White,
                                fontSize =
                                    17.sp,
                                fontWeight =
                                    FontWeight.Black,
                                letterSpacing =
                                    1.5.sp,
                                fontFamily =
                                    FontFamily
                                        .SansSerif
                            )
                        }

                        HorizontalDivider(
                            modifier =
                                Modifier
                                    .padding(
                                        vertical =
                                            16.dp
                                    ),
                            color =
                                Color.White
                                    .copy(
                                        alpha =
                                            0.1f
                                    )
                        )

                        MenuItem(
                            icon = "👤",
                            title =
                                _i18nContext
                                    .getString(
                                        R.string
                                            .side_menu_mon_compte
                                    ),
                            iconColor =
                                Color.Cyan
                        ) {
                            onOpenAccount()
                            onClose()
                        }

                        MenuItem(
                            icon = "ℹ️",
                            title =
                                _i18nContext
                                    .getString(
                                        R.string
                                            .side_menu_a_propos_de_deliriuum
                                    ),
                            iconColor =
                                Color.Cyan
                        ) {
                            onOpenAbout()
                            onClose()
                        }

                        MenuItem(
                            icon = "❓",
                            title = "FAQ",
                            iconColor =
                                Color.Cyan
                        ) {
                            onOpenFAQ()
                            onClose()
                        }

                        MenuItem(
                            icon = "📖",
                            title =
                                _i18nContext
                                    .getString(
                                        R.string
                                            .side_menu_guide_d_hygiene_numerique
                                    ),
                            iconColor =
                                Color.Cyan
                        ) {
                            onOpenGuide()
                            onClose()
                        }

                        /*
                         * LANGUE
                         *
                         * Le sous-titre montre le mode courant :
                         * "Automatique" ou le nom natif de la langue.
                         */
                        MenuItem(
                            icon = "🌐",
                            title =
                                _i18nContext
                                    .getString(
                                        R.string
                                            .side_menu_langue
                                    ),
                            subtitle =
                                selectedLanguageLabel,
                            iconColor =
                                Color.Cyan
                        ) {
                            showLanguagePicker =
                                true

                            /*
                             * On referme le drawer derrière
                             * la boîte de sélection.
                             */
                            onClose()
                        }

                        MenuItem(
                            icon = "❤️",
                            title =
                                _i18nContext
                                    .getString(
                                        R.string
                                            .side_menu_soutenir_le_projet
                                    ),
                            iconColor =
                                Color(
                                    0xFFFFC107
                                )
                        ) {
                            onClose()

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
                        }

                        Spacer(
                            modifier =
                                Modifier
                                    .weight(
                                        1f
                                    )
                        )

                        if (
                            authManager
                                .isLoggedIn
                        ) {

                            HorizontalDivider(
                                modifier =
                                    Modifier
                                        .padding(
                                            bottom =
                                                12.dp
                                        ),
                                color =
                                    Color.White
                                        .copy(
                                            alpha =
                                                0.1f
                                        )
                            )

                            MenuItem(
                                icon = "🚪",
                                title =
                                    _i18nContext
                                        .getString(
                                            R.string
                                                .side_menu_se_deconnecter
                                        ),
                                iconColor =
                                    Color.Red
                                        .copy(
                                            alpha =
                                                0.85f
                                        )
                            ) {
                                onLogout()
                            }
                        }
                    }

                    Box(
                        modifier =
                            Modifier
                                .fillMaxHeight()
                                .width(
                                    1.dp
                                )
                                .background(
                                    Color.White
                                        .copy(
                                            alpha =
                                                0.08f
                                        )
                                )
                                .align(
                                    Alignment
                                        .CenterEnd
                                )
                    )
                }
            }
        },
        content = content
    )


    if (
        showLanguagePicker
    ) {

        LanguagePickerDialog(
            selectedTag =
                selectedLanguageTag,
            onDismiss = {
                showLanguagePicker =
                    false
            },
            onSelect = {
                    newTag ->

                showLanguagePicker =
                    false

                if (
                    newTag !=
                    selectedLanguageTag
                ) {

                    selectedLanguageTag =
                        newTag

                    AppStrings
                        .setLanguageTag(
                            if (
                                newTag ==
                                AppStrings.AUTOMATIC
                            ) {
                                null
                            } else {
                                newTag
                            }
                        )

                    /*
                     * Recréation de l'Activity :
                     *
                     * attachBaseContext() relira alors
                     * immédiatement la nouvelle locale.
                     */
                    context
                        .findActivity()
                        ?.recreate()
                }
            }
        )
    }
}


@Composable
private fun MenuItem(
    icon: String,
    title: String,
    iconColor: Color,
    subtitle: String? = null,
    onClick: () -> Unit
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    onClick()
                }
                .padding(
                    vertical =
                        11.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(
                14.dp
            )
    ) {

        Text(
            text = icon,
            color = iconColor,
            fontSize =
                16.sp,
            modifier =
                Modifier
                    .width(
                        22.dp
                    )
        )

        Column(
            modifier =
                Modifier
                    .weight(
                        1f
                    ),
            verticalArrangement =
                Arrangement.spacedBy(
                    2.dp
                )
        ) {

            Text(
                text = title,
                color =
                    Color.White
                        .copy(
                            alpha =
                                0.9f
                        ),
                fontSize =
                    14.sp,
                fontWeight =
                    FontWeight.Bold
            )

            if (
                !subtitle
                    .isNullOrBlank()
            ) {

                Text(
                    text =
                        subtitle,
                    color =
                        Color.White
                            .copy(
                                alpha =
                                    0.50f
                            ),
                    fontSize =
                        11.sp,
                    fontWeight =
                        FontWeight.Medium
                )
            }
        }
    }
}


@Composable
private fun LanguagePickerDialog(
    selectedTag: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {

    val _i18nContext =
        LocalContext.current

    AlertDialog(
        onDismissRequest =
            onDismiss,
        containerColor =
            Color(
                0xFF111116
            ),
        title = {

            Text(
                text =
                    _i18nContext
                        .getString(
                            R.string
                                .language_dialog_title
                        ),
                color =
                    Color.White,
                fontWeight =
                    FontWeight.Black
            )
        },
        text = {

            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(
                            max =
                                520.dp
                        ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        2.dp
                    )
            ) {

                item(
                    key =
                        AppStrings.AUTOMATIC
                ) {

                    LanguageRow(
                        label =
                            _i18nContext
                                .getString(
                                    R.string
                                        .language_automatic
                                ),
                        selected =
                            selectedTag ==
                                    AppStrings.AUTOMATIC,
                        onClick = {
                            onSelect(
                                AppStrings
                                    .AUTOMATIC
                            )
                        }
                    )
                }

                items(
                    items =
                        LANGUAGE_OPTIONS,
                    key = {
                        it.tag
                    }
                ) {
                        language ->

                    LanguageRow(
                        label =
                            language
                                .nativeName,
                        selected =
                            selectedTag ==
                                    language.tag,
                        onClick = {
                            onSelect(
                                language.tag
                            )
                        }
                    )
                }
            }
        },
        confirmButton = {

            TextButton(
                onClick =
                    onDismiss
            ) {

                Text(
                    text =
                        _i18nContext
                            .getString(
                                R.string
                                    .account_annuler
                            )
                )
            }
        }
    )
}


@Composable
private fun LanguageRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    onClick()
                }
                .padding(
                    horizontal =
                        4.dp,
                    vertical =
                        4.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        RadioButton(
            selected =
                selected,
            onClick =
                onClick,
            colors =
                RadioButtonDefaults
                    .colors(
                        selectedColor =
                            Color.Cyan,
                        unselectedColor =
                            Color.White
                                .copy(
                                    alpha =
                                        0.42f
                                )
                    )
        )

        Spacer(
            modifier =
                Modifier
                    .width(
                        8.dp
                    )
        )

        Text(
            text =
                label,
            color =
                if (
                    selected
                ) {
                    Color.White
                } else {
                    Color.White
                        .copy(
                            alpha =
                                0.82f
                        )
                },
            fontSize =
                14.sp,
            fontWeight =
                if (
                    selected
                ) {
                    FontWeight.Bold
                } else {
                    FontWeight.Medium
                }
        )
    }
}


private tailrec fun Context.findActivity():
        Activity? =
    when (this) {

        is Activity ->
            this

        is ContextWrapper ->
            baseContext
                .findActivity()

        else ->
            null
    }
