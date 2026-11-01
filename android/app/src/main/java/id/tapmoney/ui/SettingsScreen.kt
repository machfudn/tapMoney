package id.tapmoney.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tapmoney.model.UserSettings

@Composable
fun SettingsScreen(
    settings: UserSettings,
    onSettingsChanged: (UserSettings) -> Unit,
    onClearAllData: () -> Unit,
    onBack: () -> Unit,
    isDark: Boolean,
    surfaceColor: Color,
    surfaceAltColor: Color,
    borderColor: Color,
    textColor: Color,
    textSubtleColor: Color
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val headerColor = if (isDark) NeoColors.CyanDateDark else NeoColors.CyanDate
    var selectedTab by remember { mutableStateOf("scan") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(38.dp)
                    .neoCard(bgColor = surfaceColor, borderColor = borderColor, shadowOffset = 2.dp, cornerRadius = 8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.settingsTitle, tint = textColor, modifier = Modifier.size(20.dp))
            }
            Text(
                text = strings.settingsTitle,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = textColor
            )
        }

        // Tab Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .neoCard(bgColor = surfaceAltColor, borderColor = borderColor, shadowOffset = 2.dp, cornerRadius = 8.dp)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                "scan" to strings.tabScanning,
                "privacy" to strings.tabPrivacy,
                "cards" to strings.tabCardsData
            ).forEach { (id, label) ->
                val active = selectedTab == id
                val bg = if (active) NeoColors.Yellow else Color.Transparent
                val textCol = if (active) Color.Black else textColor
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(bg)
                        .clickable { selectedTab = id }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = textCol,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tab Content
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            when (selectedTab) {
                "scan" -> {
                    // Tab 1: Pemindaian
                    item {
                        SettingCard(
                            surfaceColor = surfaceColor,
                            borderColor = borderColor,
                            title = strings.secSensorFeedback,
                            titleColor = headerColor
                        ) {
                            SettingRow(
                                title = strings.soundTitle,
                                subtitle = strings.soundSubtitle,
                                checked = settings.enableSound,
                                textColor = textColor,
                                textSubtleColor = textSubtleColor,
                                switchTrackColor = headerColor,
                                borderColor = borderColor,
                                surfaceAltColor = surfaceAltColor,
                                onCheckedChange = { onSettingsChanged(settings.copy(enableSound = it)) }
                            )
                            HorizontalDivider(color = borderColor.copy(alpha = 0.2f), thickness = 1.dp)
                            SettingRow(
                                title = strings.hapticsTitle,
                                subtitle = strings.hapticsSubtitle,
                                checked = settings.enableHaptics,
                                textColor = textColor,
                                textSubtleColor = textSubtleColor,
                                switchTrackColor = headerColor,
                                borderColor = borderColor,
                                surfaceAltColor = surfaceAltColor,
                                onCheckedChange = { onSettingsChanged(settings.copy(enableHaptics = it)) }
                            )
                            HorizontalDivider(color = borderColor.copy(alpha = 0.2f), thickness = 1.dp)
                            SettingRow(
                                title = strings.speakTitle,
                                subtitle = strings.speakSubtitle,
                                checked = settings.speakAmount,
                                textColor = textColor,
                                textSubtleColor = textSubtleColor,
                                switchTrackColor = headerColor,
                                borderColor = borderColor,
                                surfaceAltColor = surfaceAltColor,
                                onCheckedChange = { onSettingsChanged(settings.copy(speakAmount = it)) }
                            )
                        }
                    }

                    item {
                        SettingCard(
                            surfaceColor = surfaceColor,
                            borderColor = borderColor,
                            title = strings.secScanCategories,
                            titleColor = headerColor
                        ) {
                            SettingRow(
                                title = strings.catBankingTitle,
                                subtitle = strings.catBankingSubtitle,
                                checked = settings.scanDomesticBanking,
                                textColor = textColor,
                                textSubtleColor = textSubtleColor,
                                switchTrackColor = headerColor,
                                borderColor = borderColor,
                                surfaceAltColor = surfaceAltColor,
                                onCheckedChange = { onSettingsChanged(settings.copy(scanDomesticBanking = it)) }
                            )
                            HorizontalDivider(color = borderColor.copy(alpha = 0.2f), thickness = 1.dp)
                            SettingRow(
                                title = strings.catDomesticTransitTitle,
                                subtitle = strings.catDomesticTransitSubtitle,
                                checked = settings.scanDomesticTransit,
                                textColor = textColor,
                                textSubtleColor = textSubtleColor,
                                switchTrackColor = headerColor,
                                borderColor = borderColor,
                                surfaceAltColor = surfaceAltColor,
                                onCheckedChange = { onSettingsChanged(settings.copy(scanDomesticTransit = it)) }
                            )
                            HorizontalDivider(color = borderColor.copy(alpha = 0.2f), thickness = 1.dp)
                            SettingRow(
                                title = strings.catRegionalTransitTitle,
                                subtitle = strings.catRegionalTransitSubtitle,
                                checked = settings.scanRegionalTransit,
                                textColor = textColor,
                                textSubtleColor = textSubtleColor,
                                switchTrackColor = headerColor,
                                borderColor = borderColor,
                                surfaceAltColor = surfaceAltColor,
                                onCheckedChange = { onSettingsChanged(settings.copy(scanRegionalTransit = it)) }
                            )
                        }
                    }
                }

                "privacy" -> {
                    // Tab 2: Bahasa, Tema, & Privasi
                    item {
                        SettingCard(
                            surfaceColor = surfaceColor,
                            borderColor = borderColor,
                            title = strings.secLanguage,
                            titleColor = headerColor
                        ) {
                            Text(
                                text = strings.languageSubtitle,
                                fontSize = 12.sp,
                                color = textSubtleColor,
                                fontWeight = FontWeight.SemiBold
                            )
                            LanguageSelector(
                                selectedLanguage = settings.appLanguage,
                                surfaceColor = surfaceColor,
                                surfaceAltColor = surfaceAltColor,
                                borderColor = borderColor,
                                textColor = textColor,
                                textSubtleColor = textSubtleColor,
                                headerColor = headerColor,
                                strings = strings,
                                onSelect = { onSettingsChanged(settings.copy(appLanguage = it)) }
                            )
                        }
                    }

                    item {
                        SettingCard(
                            surfaceColor = surfaceColor,
                            borderColor = borderColor,
                            title = strings.secTheme,
                            titleColor = headerColor
                        ) {
                            Text(
                                text = strings.themeSubtitle,
                                fontSize = 12.sp,
                                color = textSubtleColor,
                                fontWeight = FontWeight.SemiBold
                            )
                            ThemeSelector(
                                selectedTheme = settings.darkTheme,
                                surfaceColor = surfaceColor,
                                surfaceAltColor = surfaceAltColor,
                                borderColor = borderColor,
                                textColor = textColor,
                                strings = strings,
                                onSelect = { onSettingsChanged(settings.copy(darkTheme = it)) }
                            )
                        }
                    }

                    item {
                        SettingCard(
                            surfaceColor = surfaceColor,
                            borderColor = borderColor,
                            title = strings.secCardPrivacy,
                            titleColor = headerColor
                        ) {
                            SettingRow(
                                title = strings.txColorsTitle,
                                subtitle = strings.txColorsSubtitle,
                                checked = settings.showTransactionColors,
                                textColor = textColor,
                                textSubtleColor = textSubtleColor,
                                switchTrackColor = headerColor,
                                borderColor = borderColor,
                                surfaceAltColor = surfaceAltColor,
                                onCheckedChange = { onSettingsChanged(settings.copy(showTransactionColors = it)) }
                            )
                        }
                    }

                    item {
                        SettingCard(
                            surfaceColor = surfaceColor,
                            borderColor = borderColor,
                            title = strings.secStorage,
                            titleColor = headerColor
                        ) {
                            SettingRow(
                                title = strings.autoSaveTitle,
                                subtitle = strings.autoSaveSubtitle,
                                checked = settings.autoSaveHistory,
                                textColor = textColor,
                                textSubtleColor = textSubtleColor,
                                switchTrackColor = headerColor,
                                borderColor = borderColor,
                                surfaceAltColor = surfaceAltColor,
                                onCheckedChange = { onSettingsChanged(settings.copy(autoSaveHistory = it)) }
                            )
                        }
                    }
                }

                "cards" -> {
                    // Tab 3: Kartu yang Didukung & Manajemen Data
                    item {
                        SettingCard(
                            surfaceColor = surfaceColor,
                            borderColor = borderColor,
                            title = strings.secSupportedCards,
                            titleColor = headerColor
                        ) {
                            Text(
                                text = strings.catBankingHeader,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = headerColor
                            )
                            SupportedCardItem("e-Money Mandiri", strings.capBalanceHistory, textColor, textSubtleColor, isDark, headerColor)
                            SupportedCardItem("BCA Flazz", strings.capBalanceHistory, textColor, textSubtleColor, isDark, headerColor)
                            SupportedCardItem("Brizzi BRI", strings.capBalanceHistory, textColor, textSubtleColor, isDark, headerColor)
                            SupportedCardItem("TapCash BNI", strings.capBalanceHistory, textColor, textSubtleColor, isDark, headerColor)
                            SupportedCardItem("JakCard Bank DKI", strings.capBalanceOnly, textColor, textSubtleColor, isDark, headerColor)
                            SupportedCardItem("MegaCash Bank Mega", strings.capBalanceOnly, textColor, textSubtleColor, isDark, headerColor)

                            Spacer(modifier = Modifier.height(4.dp))
                            HorizontalDivider(color = borderColor.copy(alpha = 0.2f), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = strings.catDomesticTransitHeader,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = headerColor
                            )
                            SupportedCardItem("KMT Commuter Line", strings.capBalanceHistoryStation, textColor, textSubtleColor, isDark, headerColor)
                            SupportedCardItem("MRT Jakarta (MRTJ)", strings.capBalanceLastTx, textColor, textSubtleColor, isDark, headerColor)

                            Spacer(modifier = Modifier.height(4.dp))
                            HorizontalDivider(color = borderColor.copy(alpha = 0.2f), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = strings.catRegionalTransitHeader,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = headerColor
                            )
                            SupportedCardItem("CEPAS (EZ-Link / NETS)", strings.capSgdBalanceHistory, textColor, textSubtleColor, isDark, headerColor)
                            SupportedCardItem("Suica / PASMO / IC", strings.capJpyBalanceHistory, textColor, textSubtleColor, isDark, headerColor)
                            SupportedCardItem("Rakuten Edy", strings.capJpyBalanceHistory, textColor, textSubtleColor, isDark, headerColor)
                            SupportedCardItem("T-Money & Cashbee", strings.capKrwBalance, textColor, textSubtleColor, isDark, headerColor)
                            SupportedCardItem("China T-Union / PBOC", strings.capCnyBalance, textColor, textSubtleColor, isDark, headerColor)
                            SupportedCardItem("Sydney Opal", strings.capAudBalance, textColor, textSubtleColor, isDark, headerColor)
                        }
                    }

                    item {
                        SettingCard(
                            surfaceColor = surfaceColor,
                            borderColor = borderColor,
                            title = strings.secLocalStorage,
                            titleColor = headerColor
                        ) {
                            Text(
                                text = strings.localStorageSubtitle,
                                fontSize = 12.sp,
                                color = textSubtleColor,
                                lineHeight = 16.sp
                            )
                            Button(
                                onClick = { showDeleteConfirmDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = NeoColors.Red),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .neoCard(bgColor = NeoColors.Red, borderColor = borderColor, shadowOffset = 2.dp, cornerRadius = 6.dp),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Text(strings.deleteAllDataButton, color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation dialog before deleting all data
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = surfaceColor,
            titleContentColor = textColor,
            textContentColor = textSubtleColor,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.border(2.dp, borderColor, RoundedCornerShape(12.dp)),
            title = {
                Text(strings.deleteConfirmTitle, fontWeight = FontWeight.Black, fontSize = 18.sp, color = textColor)
            },
            text = {
                Text(
                    strings.deleteConfirmMessage,
                    fontSize = 13.sp,
                    color = textSubtleColor
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllData()
                        showDeleteConfirmDialog = false
                        Toast.makeText(context, strings.dataClearedToast, Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeoColors.Red),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor)
                ) {
                    Text(strings.deleteConfirmButton, color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showDeleteConfirmDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = surfaceAltColor),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor)
                ) {
                    Text(strings.cancel, color = textColor, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun SettingCard(
    surfaceColor: Color,
    borderColor: Color,
    title: String,
    titleColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = titleColor,
            modifier = Modifier.padding(start = 2.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .neoCard(bgColor = surfaceColor, borderColor = borderColor, shadowOffset = 3.dp, cornerRadius = 8.dp)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    textColor: Color,
    textSubtleColor: Color,
    switchTrackColor: Color,
    borderColor: Color,
    surfaceAltColor: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = textColor
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = textSubtleColor,
                lineHeight = 15.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = switchTrackColor,
                checkedBorderColor = borderColor,
                uncheckedThumbColor = textSubtleColor,
                uncheckedTrackColor = surfaceAltColor,
                uncheckedBorderColor = borderColor
            )
        )
    }
}

@Composable
private fun SupportedCardItem(
    name: String,
    capability: String,
    textColor: Color,
    textSubtleColor: Color,
    isDark: Boolean,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = textColor
        )
        val badgeBg = if (isDark) accentColor.copy(alpha = 0.2f) else NeoColors.Yellow.copy(alpha = 0.35f)
        val badgeText = if (isDark) accentColor else Color(0xFF854D0E)
        Box(
            modifier = Modifier
                .background(badgeBg, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = capability,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = badgeText
            )
        }
    }
}

@Composable
private fun LanguageSelector(
    selectedLanguage: String?,
    surfaceColor: Color,
    surfaceAltColor: Color,
    borderColor: Color,
    textColor: Color,
    textSubtleColor: Color,
    headerColor: Color,
    strings: AppStrings,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val languages = listOf(
        Triple(null, strings.langSystem, Icons.Default.SettingsSuggest),
        Triple("id", strings.langIndonesian, Icons.Default.Language),
        Triple("en", strings.langEnglish, Icons.Default.Language)
    )

    val currentSelection = languages.find { it.first == selectedLanguage } ?: languages.first()

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .neoCard(
                    bgColor = surfaceAltColor,
                    borderColor = borderColor,
                    shadowOffset = 2.dp,
                    cornerRadius = 8.dp
                )
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = currentSelection.third,
                    contentDescription = null,
                    tint = headerColor,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = currentSelection.second,
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(24.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(surfaceColor)
                .border(2.dp, borderColor, RoundedCornerShape(8.dp))
        ) {
            languages.forEach { (langValue, label, icon) ->
                val isSelected = selectedLanguage == langValue
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) headerColor else textSubtleColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = label,
                                color = if (isSelected) headerColor else textColor,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium
                            )
                        }
                    },
                    trailingIcon = {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = NeoColors.Green,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    onClick = {
                        onSelect(langValue)
                        expanded = false
                    },
                    modifier = Modifier.background(
                        if (isSelected) surfaceAltColor else surfaceColor
                    )
                )
            }
        }
    }
}

@Composable
private fun ThemeSelector(
    selectedTheme: Boolean?,
    surfaceColor: Color,
    surfaceAltColor: Color,
    borderColor: Color,
    textColor: Color,
    strings: AppStrings,
    onSelect: (Boolean?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            Triple(null, strings.themeSystem, Icons.Default.SettingsSuggest),
            Triple(false, strings.themeLight, Icons.Default.LightMode),
            Triple(true, strings.themeDark, Icons.Default.DarkMode)
        ).forEach { (themeValue, label, icon) ->
            val isSelected = selectedTheme == themeValue
            val btnBg = if (isSelected) NeoColors.Yellow else surfaceAltColor
            val contentColor = if (isSelected) Color.Black else textColor

            Button(
                onClick = { onSelect(themeValue) },
                colors = ButtonDefaults.buttonColors(containerColor = btnBg),
                modifier = Modifier
                    .weight(1f)
                    .neoCard(
                        bgColor = btnBg,
                        borderColor = borderColor,
                        shadowOffset = if (isSelected) 2.dp else 1.dp,
                        cornerRadius = 6.dp
                    ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = label,
                        color = contentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}
