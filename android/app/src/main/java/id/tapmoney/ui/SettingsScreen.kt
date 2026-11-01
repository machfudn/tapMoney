package id.tapmoney.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
    surfaceColor: Color,
    surfaceAltColor: Color,
    borderColor: Color,
    textColor: Color,
    textSubtleColor: Color
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
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
                            title = strings.secSensorFeedback
                        ) {
                            SettingRow(
                                title = strings.soundTitle,
                                subtitle = strings.soundSubtitle,
                                checked = settings.enableSound,
                                textColor = textColor,
                                textSubtleColor = textSubtleColor,
                                onCheckedChange = { onSettingsChanged(settings.copy(enableSound = it)) }
                            )
                            HorizontalDivider(color = borderColor.copy(alpha = 0.2f), thickness = 1.dp)
                            SettingRow(
                                title = strings.hapticsTitle,
                                subtitle = strings.hapticsSubtitle,
                                checked = settings.enableHaptics,
                                textColor = textColor,
                                textSubtleColor = textSubtleColor,
                                onCheckedChange = { onSettingsChanged(settings.copy(enableHaptics = it)) }
                            )
                            HorizontalDivider(color = borderColor.copy(alpha = 0.2f), thickness = 1.dp)
                            SettingRow(
                                title = strings.speakTitle,
                                subtitle = strings.speakSubtitle,
                                checked = settings.speakAmount,
                                textColor = textColor,
                                textSubtleColor = textSubtleColor,
                                onCheckedChange = { onSettingsChanged(settings.copy(speakAmount = it)) }
                            )
                        }
                    }

                    item {
                        SettingCard(
                            surfaceColor = surfaceColor,
                            borderColor = borderColor,
                            title = strings.secScanCategories
                        ) {
                            SettingRow(
                                title = strings.catBankingTitle,
                                subtitle = strings.catBankingSubtitle,
                                checked = settings.scanDomesticBanking,
                                textColor = textColor,
                                textSubtleColor = textSubtleColor,
                                onCheckedChange = { onSettingsChanged(settings.copy(scanDomesticBanking = it)) }
                            )
                            HorizontalDivider(color = borderColor.copy(alpha = 0.2f), thickness = 1.dp)
                            SettingRow(
                                title = strings.catDomesticTransitTitle,
                                subtitle = strings.catDomesticTransitSubtitle,
                                checked = settings.scanDomesticTransit,
                                textColor = textColor,
                                textSubtleColor = textSubtleColor,
                                onCheckedChange = { onSettingsChanged(settings.copy(scanDomesticTransit = it)) }
                            )
                            HorizontalDivider(color = borderColor.copy(alpha = 0.2f), thickness = 1.dp)
                            SettingRow(
                                title = strings.catRegionalTransitTitle,
                                subtitle = strings.catRegionalTransitSubtitle,
                                checked = settings.scanRegionalTransit,
                                textColor = textColor,
                                textSubtleColor = textSubtleColor,
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
                            title = strings.secLanguage
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
                                borderColor = borderColor,
                                textColor = textColor,
                                strings = strings,
                                onSelect = { onSettingsChanged(settings.copy(appLanguage = it)) }
                            )
                        }
                    }

                    item {
                        SettingCard(
                            surfaceColor = surfaceColor,
                            borderColor = borderColor,
                            title = strings.secTheme
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
                            title = strings.secCardPrivacy
                        ) {
                            SettingRow(
                                title = strings.txColorsTitle,
                                subtitle = strings.txColorsSubtitle,
                                checked = settings.showTransactionColors,
                                textColor = textColor,
                                textSubtleColor = textSubtleColor,
                                onCheckedChange = { onSettingsChanged(settings.copy(showTransactionColors = it)) }
                            )
                        }
                    }

                    item {
                        SettingCard(
                            surfaceColor = surfaceColor,
                            borderColor = borderColor,
                            title = strings.secStorage
                        ) {
                            SettingRow(
                                title = strings.autoSaveTitle,
                                subtitle = strings.autoSaveSubtitle,
                                checked = settings.autoSaveHistory,
                                textColor = textColor,
                                textSubtleColor = textSubtleColor,
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
                            title = strings.secSupportedCards
                        ) {
                            Text(
                                text = strings.catBankingHeader,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = NeoColors.CyanDate
                            )
                            SupportedCardItem("e-Money Mandiri", "Saldo + riwayat", textColor, textSubtleColor)
                            SupportedCardItem("BCA Flazz", "Saldo + riwayat", textColor, textSubtleColor)
                            SupportedCardItem("Brizzi BRI", "Saldo + riwayat", textColor, textSubtleColor)
                            SupportedCardItem("TapCash BNI", "Saldo + riwayat", textColor, textSubtleColor)
                            SupportedCardItem("JakCard Bank DKI", "Saldo", textColor, textSubtleColor)
                            SupportedCardItem("MegaCash Bank Mega", "Saldo", textColor, textSubtleColor)

                            Spacer(modifier = Modifier.height(4.dp))
                            HorizontalDivider(color = borderColor.copy(alpha = 0.2f), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = strings.catDomesticTransitHeader,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = NeoColors.CyanDate
                            )
                            SupportedCardItem("KMT Commuter Line", "Saldo + riwayat + stasiun", textColor, textSubtleColor)
                            SupportedCardItem("MRT Jakarta (MRTJ)", "Saldo + transaksi terakhir", textColor, textSubtleColor)

                            Spacer(modifier = Modifier.height(4.dp))
                            HorizontalDivider(color = borderColor.copy(alpha = 0.2f), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = strings.catRegionalTransitHeader,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = NeoColors.CyanDate
                            )
                            SupportedCardItem("CEPAS (EZ-Link / NETS)", "Saldo SGD + riwayat", textColor, textSubtleColor)
                            SupportedCardItem("Suica / PASMO / IC", "Saldo JPY + riwayat", textColor, textSubtleColor)
                            SupportedCardItem("Rakuten Edy", "Saldo JPY + riwayat", textColor, textSubtleColor)
                            SupportedCardItem("T-Money & Cashbee", "Saldo KRW", textColor, textSubtleColor)
                            SupportedCardItem("China T-Union / PBOC", "Saldo CNY", textColor, textSubtleColor)
                            SupportedCardItem("Sydney Opal", "Saldo AUD", textColor, textSubtleColor)
                        }
                    }

                    item {
                        SettingCard(
                            surfaceColor = surfaceColor,
                            borderColor = borderColor,
                            title = strings.secLocalStorage
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
            title = {
                Text(strings.deleteConfirmTitle, fontWeight = FontWeight.Black, fontSize = 18.sp)
            },
            text = {
                Text(
                    strings.deleteConfirmMessage,
                    fontSize = 13.sp
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
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor)
                ) {
                    Text(strings.deleteConfirmButton, color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(strings.cancel, fontWeight = FontWeight.Bold)
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
            color = NeoColors.CyanDate,
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
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SupportedCardItem(
    name: String,
    capability: String,
    textColor: Color,
    textSubtleColor: Color
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
        Box(
            modifier = Modifier
                .background(NeoColors.Yellow.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = capability,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

@Composable
private fun LanguageSelector(
    selectedLanguage: String?,
    surfaceColor: Color,
    borderColor: Color,
    textColor: Color,
    strings: AppStrings,
    onSelect: (String?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            Triple(null, strings.langSystem, Icons.Default.SettingsSuggest),
            Triple("id", strings.langIndonesian, Icons.Default.Language),
            Triple("en", strings.langEnglish, Icons.Default.Language)
        ).forEach { (langValue, label, icon) ->
            val isSelected = selectedLanguage == langValue
            val btnBg = if (isSelected) NeoColors.Yellow else surfaceColor
            val contentColor = if (isSelected) Color.Black else textColor

            Button(
                onClick = { onSelect(langValue) },
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

@Composable
private fun ThemeSelector(
    selectedTheme: Boolean?,
    surfaceColor: Color,
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
            val btnBg = if (isSelected) NeoColors.Yellow else surfaceColor
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
