package id.tapmoney.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.tapmoney.BuildConfig
import id.tapmoney.model.UserSettings
import id.tapmoney.update.AppReleaseInfo
import id.tapmoney.update.AppUpdateChecker
import id.tapmoney.update.ApkDownloader
import id.tapmoney.update.DualReleaseResult
import id.tapmoney.update.UpdateCheckResult
import kotlinx.coroutines.launch
import java.util.Locale

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
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf("scan") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateResultDialog by remember { mutableStateOf<UpdateCheckResult?>(null) }

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

                    item {
                        SettingCard(
                            surfaceColor = surfaceColor,
                            borderColor = borderColor,
                            title = strings.secAppUpdate,
                            titleColor = headerColor
                        ) {
                            Text(
                                text = strings.appUpdateSubtitle,
                                fontSize = 12.sp,
                                color = textSubtleColor,
                                lineHeight = 16.sp
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(surfaceAltColor)
                                    .border(1.5.dp, borderColor, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = strings.currentVersionLabel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textSubtleColor
                                )
                                Text(
                                    text = "v${BuildConfig.VERSION_NAME}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = headerColor
                                )
                            }

                            Button(
                                onClick = {
                                    if (!isCheckingUpdate) {
                                        isCheckingUpdate = true
                                        coroutineScope.launch {
                                            val result = AppUpdateChecker.checkForUpdates(BuildConfig.VERSION_NAME)
                                            isCheckingUpdate = false
                                            updateResultDialog = result
                                        }
                                    }
                                },
                                enabled = !isCheckingUpdate,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = headerColor,
                                    disabledContainerColor = headerColor.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .neoCard(bgColor = headerColor, borderColor = borderColor, shadowOffset = 2.dp, cornerRadius = 6.dp),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (isCheckingUpdate) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                        Text(
                                            text = strings.checkingUpdate,
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = strings.checkUpdateButton,
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp
                                        )
                                    }
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

    when (val res = updateResultDialog) {
        is UpdateCheckResult.Success -> {
            DualChannelUpdateDialog(
                dual = res.dual,
                settings = settings,
                isDark = isDark,
                surfaceColor = surfaceColor,
                surfaceAltColor = surfaceAltColor,
                borderColor = borderColor,
                textColor = textColor,
                textSubtleColor = textSubtleColor,
                headerColor = headerColor,
                onDismiss = { updateResultDialog = null }
            )
        }
        is UpdateCheckResult.UpdateAvailable -> {
            DualChannelUpdateDialog(
                dual = DualReleaseResult(
                    stable = if (!res.info.isPrerelease) res.info else null,
                    beta = if (res.info.isPrerelease) res.info else null,
                    currentVersion = BuildConfig.VERSION_NAME
                ),
                settings = settings,
                isDark = isDark,
                surfaceColor = surfaceColor,
                surfaceAltColor = surfaceAltColor,
                borderColor = borderColor,
                textColor = textColor,
                textSubtleColor = textSubtleColor,
                headerColor = headerColor,
                onDismiss = { updateResultDialog = null }
            )
        }
        is UpdateCheckResult.UpToDate -> {
            AlertDialog(
                onDismissRequest = { updateResultDialog = null },
                containerColor = surfaceColor,
                titleContentColor = textColor,
                textContentColor = textSubtleColor,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.border(2.dp, borderColor, RoundedCornerShape(12.dp)),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = NeoColors.Green,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = strings.updateUpToDateTitle,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = textColor
                        )
                    }
                },
                text = {
                    Text(
                        text = String.format(strings.updateUpToDateMessage, BuildConfig.VERSION_NAME),
                        fontSize = 13.sp,
                        color = textSubtleColor,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { updateResultDialog = null },
                        colors = ButtonDefaults.buttonColors(containerColor = headerColor),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor)
                    ) {
                        Text("OK", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
        is UpdateCheckResult.Error -> {
            AlertDialog(
                onDismissRequest = { updateResultDialog = null },
                containerColor = surfaceColor,
                titleContentColor = textColor,
                textContentColor = textSubtleColor,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.border(2.dp, borderColor, RoundedCornerShape(12.dp)),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = NeoColors.Red,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = strings.updateErrorTitle,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = textColor
                        )
                    }
                },
                text = {
                    Text(
                        text = res.message,
                        fontSize = 13.sp,
                        color = textSubtleColor,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            isCheckingUpdate = true
                            updateResultDialog = null
                            coroutineScope.launch {
                                val result = AppUpdateChecker.checkForUpdates(BuildConfig.VERSION_NAME)
                                isCheckingUpdate = false
                                updateResultDialog = result
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = headerColor),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor)
                    ) {
                        Text(strings.checkUpdateButton, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { updateResultDialog = null },
                        colors = ButtonDefaults.buttonColors(containerColor = surfaceAltColor),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor)
                    ) {
                        Text(strings.cancel, color = textColor, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
        null -> {}
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

@Composable
private fun DualChannelUpdateDialog(
    dual: DualReleaseResult,
    settings: UserSettings,
    isDark: Boolean,
    surfaceColor: Color,
    surfaceAltColor: Color,
    borderColor: Color,
    textColor: Color,
    textSubtleColor: Color,
    headerColor: Color,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val isCurrentBeta = BuildConfig.VERSION_NAME.contains("beta", ignoreCase = true) ||
            BuildConfig.VERSION_NAME.contains("rc", ignoreCase = true)

    val defaultTab = if ((isCurrentBeta && dual.beta != null) || (dual.beta?.isNewer == true && dual.stable?.isNewer != true)) 1 else 0
    var selectedTab by remember { mutableStateOf(defaultTab) }
    val activeRelease = if (selectedTab == 0) dual.stable else dual.beta

    val isEnglish = remember(settings.appLanguage) { resolveIsEnglish(settings.appLanguage) }
    val stableChannelTitle = if (isEnglish) "Stable" else "Stabil"
    val betaChannelTitle = "Beta"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
                    .heightIn(max = 620.dp)
                    .neoCard(
                        bgColor = surfaceColor,
                        borderColor = borderColor,
                        borderWidth = 2.dp,
                        shadowOffset = 4.dp,
                        cornerRadius = 14.dp
                    )
                    .padding(16.dp)
            ) {
                // --- 1. PINNED HEADER ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (dual.hasAnyUpdate) NeoColors.Green else headerColor,
                                    RoundedCornerShape(8.dp)
                                )
                                .border(1.5.dp, borderColor, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (dual.hasAnyUpdate) Icons.Default.NewReleases else Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = if (dual.hasAnyUpdate) Color.Black else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = strings.secAppUpdate,
                                fontWeight = FontWeight.Black,
                                fontSize = 17.sp,
                                color = textColor,
                                lineHeight = 20.sp
                            )
                            Text(
                                text = "TapMoney v${BuildConfig.VERSION_NAME}",
                                fontSize = 11.sp,
                                color = textSubtleColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .background(surfaceAltColor, RoundedCornerShape(8.dp))
                            .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = strings.cancel,
                            tint = textColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // --- Channel Segmented Tabs ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(surfaceAltColor, RoundedCornerShape(10.dp))
                        .border(1.5.dp, borderColor, RoundedCornerShape(10.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isStableActive = selectedTab == 0
                    val stableHasUpdate = dual.stable?.isNewer == true

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (isStableActive) headerColor else Color.Transparent,
                                RoundedCornerShape(7.dp)
                            )
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 7.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stableChannelTitle,
                                fontSize = 12.sp,
                                fontWeight = if (isStableActive) FontWeight.Black else FontWeight.SemiBold,
                                color = if (isStableActive) Color.White else textColor
                            )
                            if (stableHasUpdate) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(NeoColors.Green, CircleShape)
                                        .border(1.dp, Color.Black, CircleShape)
                                )
                            }
                        }
                    }

                    val isBetaActive = selectedTab == 1
                    val betaHasUpdate = dual.beta?.isNewer == true

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (isBetaActive) headerColor else Color.Transparent,
                                RoundedCornerShape(7.dp)
                            )
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 7.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = betaChannelTitle,
                                fontSize = 12.sp,
                                fontWeight = if (isBetaActive) FontWeight.Black else FontWeight.SemiBold,
                                color = if (isBetaActive) Color.White else textColor
                            )
                            if (betaHasUpdate) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(NeoColors.Yellow, CircleShape)
                                        .border(1.dp, Color.Black, CircleShape)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // --- 2. UNIFIED SCROLLABLE BODY ---
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (activeRelease != null) {
                        // Hero Release Card
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(surfaceAltColor, RoundedCornerShape(10.dp))
                                .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "v${activeRelease.versionName}",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Black,
                                        color = textColor
                                    )
                                    if (activeRelease.isPrerelease) {
                                        Box(
                                            modifier = Modifier
                                                .background(NeoColors.Yellow, RoundedCornerShape(4.dp))
                                                .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = "BETA",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color.Black
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "${strings.currentVersionLabel}: v${BuildConfig.VERSION_NAME}",
                                    fontSize = 11.sp,
                                    color = textSubtleColor,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            val badgeText = if (activeRelease.isNewer) strings.updateAvailableTitle else strings.updateUpToDateTitle
                            val badgeBg = if (activeRelease.isNewer) NeoColors.Green else surfaceColor
                            val badgeFg = if (activeRelease.isNewer) Color.Black else textSubtleColor

                            Box(
                                modifier = Modifier
                                    .background(badgeBg, RoundedCornerShape(6.dp))
                                    .border(1.dp, if (activeRelease.isNewer) borderColor else borderColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = badgeText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = badgeFg
                                )
                            }
                        }

                        // Changelog Section
                        val localizedNotes = remember(activeRelease.releaseNotes, settings.appLanguage) {
                            AppUpdateChecker.localizeReleaseNotes(
                                rawNotes = activeRelease.releaseNotes,
                                isEnglish = isEnglish
                            )
                        }

                        if (localizedNotes.isNotBlank()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = headerColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = strings.changelogHeader,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(surfaceAltColor, RoundedCornerShape(8.dp))
                                        .border(1.dp, borderColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    MarkdownContent(
                                        markdown = localizedNotes,
                                        textColor = textColor,
                                        textSubtleColor = textSubtleColor,
                                        headerColor = headerColor,
                                        surfaceColor = surfaceColor
                                    )
                                }
                            }
                        }

                        // File Info & Checksum Row
                        if (activeRelease.apkSize != null || !activeRelease.checksum.isNullOrBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(surfaceAltColor, RoundedCornerShape(8.dp))
                                    .border(1.dp, borderColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (activeRelease.apkSize != null) {
                                    val mbSize = activeRelease.apkSize / (1024.0 * 1024.0)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FolderZip,
                                            contentDescription = null,
                                            tint = textSubtleColor,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = String.format(Locale.US, "%.1f MB", mbSize),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(1.dp))
                                }

                                if (!activeRelease.checksum.isNullOrBlank()) {
                                    val shortHash = remember(activeRelease.checksum) {
                                        val hash = activeRelease.checksum
                                        if (hash.length > 12) "${hash.take(6)}...${hash.takeLast(6)}" else hash
                                    }
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(surfaceColor)
                                            .border(1.dp, borderColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                            .clickable {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                val clip = ClipData.newPlainText("SHA256", activeRelease.checksum)
                                                clipboard?.setPrimaryClip(clip)
                                                Toast.makeText(context, strings.checksumCopiedToast, Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(horizontal = 7.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Fingerprint,
                                            contentDescription = null,
                                            tint = headerColor,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "SHA: $shortHash",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Salin SHA-256",
                                            tint = textSubtleColor,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Play Protect Tip Card
                        if (activeRelease.downloadUrl != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(surfaceAltColor, RoundedCornerShape(8.dp))
                                    .border(1.dp, borderColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = headerColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = strings.playProtectTipTitle,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                }
                                Text(
                                    text = strings.playProtectTipDesc,
                                    fontSize = 10.sp,
                                    color = textSubtleColor,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    } else {
                        // Empty State
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(surfaceAltColor, RoundedCornerShape(10.dp))
                                .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = textSubtleColor,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = if (selectedTab == 1) strings.noBetaReleaseAvailable else strings.noStableReleaseAvailable,
                                    fontSize = 12.sp,
                                    color = textSubtleColor,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // --- 3. PINNED FOOTER ACTIONS ---
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(borderColor.copy(alpha = 0.15f))
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = surfaceAltColor),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
                            contentPadding = PaddingValues(vertical = 10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = strings.cancel,
                                color = textColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (activeRelease?.downloadUrl != null) {
                            val isNewer = activeRelease.isNewer
                            Button(
                                onClick = {
                                    val apkName = activeRelease.apkName ?: "TapMoney-${activeRelease.versionName}.apk"
                                    val downloadId = ApkDownloader.downloadApk(
                                        context = context,
                                        downloadUrl = activeRelease.downloadUrl,
                                        apkFileName = apkName
                                    )
                                    if (downloadId != null) {
                                        Toast.makeText(context, strings.downloadStartedToast, Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, strings.downloadFailedToast, Toast.LENGTH_SHORT).show()
                                    }
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isNewer) NeoColors.Green else headerColor
                                ),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
                                contentPadding = PaddingValues(vertical = 10.dp),
                                modifier = Modifier.weight(1.4f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        tint = if (isNewer) Color.Black else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (isNewer) strings.updateButton else strings.downloadApkButton,
                                        color = if (isNewer) Color.Black else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }

                    if (activeRelease != null && activeRelease.htmlUrl.isNotBlank()) {
                        TextButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(activeRelease.htmlUrl))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                                onDismiss()
                            },
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInBrowser,
                                    contentDescription = null,
                                    tint = textSubtleColor,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = strings.openInBrowserButton,
                                    color = textSubtleColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
