package id.tapmoney.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tapmoney.R
import id.tapmoney.model.CardBalanceResult
import id.tapmoney.model.CardIssuer
import id.tapmoney.model.CardTransactionItem
import id.tapmoney.model.StoredCard
import id.tapmoney.model.UserSettings
import id.tapmoney.util.ReceiptFormatter
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MainScreen(
    currentCard: CardBalanceResult?,
    savedCards: List<StoredCard>,
    locationAliases: Map<String, String>,
    settings: UserSettings,
    nfcEnabled: Boolean,
    statusMessage: String,
    onSettingsChanged: (UserSettings) -> Unit,
    onClearCurrentCard: () -> Unit,
    onDeleteSavedCard: (String) -> Unit,
    onClearAllData: () -> Unit,
    onSaveLocationAlias: (String, String) -> Unit,
    onSelectSavedCard: (StoredCard) -> Unit,
    onExportPdf: (CardBalanceResult) -> Unit,
    onPrintReceipt: (CardBalanceResult) -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val systemDark = isSystemInDarkTheme()
    val isDark = settings.darkTheme ?: systemDark

    val bgColor = if (isDark) NeoColors.DarkBg else NeoColors.LightBg
    val surfaceColor = if (isDark) NeoColors.DarkSurface else NeoColors.LightSurface
    val surfaceAltColor = if (isDark) NeoColors.DarkSurfaceAlt else NeoColors.LightSurfaceAlt
    val textColor = if (isDark) NeoColors.DarkText else NeoColors.LightText
    val textSubtleColor = if (isDark) NeoColors.DarkTextSubtle else NeoColors.LightTextSubtle
    val borderColor = if (isDark) Color(0xFF3F3F46) else NeoColors.Border

    var activeTab by remember { mutableStateOf("chip") }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var editingTerminalId by remember { mutableStateOf<String?>(null) }
    var editingTerminalName by remember { mutableStateOf("") }
    var showAllHistory by remember(currentCard?.cardNumber) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        if (showSettingsScreen) {
            SettingsScreen(
                settings = settings,
                onSettingsChanged = onSettingsChanged,
                onClearAllData = onClearAllData,
                onBack = { showSettingsScreen = false },
                surfaceColor = surfaceColor,
                surfaceAltColor = surfaceAltColor,
                borderColor = borderColor,
                textColor = textColor,
                textSubtleColor = textSubtleColor
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher),
                    contentDescription = "TapMoney Logo",
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (currentCard != null) {
                        IconButton(
                            onClick = onClearCurrentCard,
                            modifier = Modifier
                                .size(36.dp)
                                .neoCard(bgColor = surfaceColor, borderColor = borderColor, shadowOffset = 2.dp, cornerRadius = 6.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = strings.closeCard, tint = NeoColors.Red, modifier = Modifier.size(18.dp))
                        }
                    }
                    IconButton(
                        onClick = { showSettingsScreen = true },
                        modifier = Modifier
                            .size(36.dp)
                            .neoCard(bgColor = surfaceColor, borderColor = borderColor, shadowOffset = 2.dp, cornerRadius = 6.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = strings.settings, tint = textColor, modifier = Modifier.size(18.dp))
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                // Card Visual Component
                item {
                    CardVisualWidget(
                        currentCard = currentCard,
                        settings = settings,
                        surfaceColor = surfaceColor,
                        borderColor = borderColor,
                        textColor = textColor,
                        textSubtleColor = textSubtleColor,
                        onToggleMaskCardNumber = {
                            onSettingsChanged(settings.copy(maskCardNumber = !settings.maskCardNumber))
                        }
                    )
                }

                // Balance Container & Action Buttons
                item {
                    BalanceWidget(
                        currentCard = currentCard,
                        settings = settings,
                        surfaceColor = surfaceColor,
                        borderColor = borderColor,
                        textColor = textColor,
                        textSubtleColor = textSubtleColor,
                        onToggleHideBalance = {
                            onSettingsChanged(settings.copy(hideBalance = !settings.hideBalance))
                        },
                        onShareReceipt = {
                            val isEn = resolveIsEnglish(settings.appLanguage)
                            shareReceipt(context, currentCard, isEn)
                        },
                        onCopyReceipt = {
                            val isEn = resolveIsEnglish(settings.appLanguage)
                            copyReceipt(context, currentCard, isEn)
                        },
                        onExportPdf = { card -> onExportPdf(card) },
                        onPrintReceipt = { card -> onPrintReceipt(card) }
                    )
                }

                // Dual History Tab Switcher
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .neoCard(bgColor = surfaceAltColor, borderColor = borderColor, shadowOffset = 2.dp, cornerRadius = 8.dp)
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val chipCount = currentCard?.history?.size ?: 0
                            TabPill(
                                label = strings.tabChipHistory,
                                badgeCount = if (chipCount > 0) chipCount else null,
                                active = activeTab == "chip",
                                borderColor = borderColor,
                                textColor = textColor,
                                onClick = { activeTab = "chip" }
                            )

                            TabPill(
                                label = strings.tabSavedCards,
                                badgeCount = if (savedCards.isNotEmpty()) savedCards.size else null,
                                active = activeTab == "saved",
                                borderColor = borderColor,
                                textColor = textColor,
                                onClick = { activeTab = "saved" }
                            )
                        }

                        if (activeTab == "saved" && savedCards.isNotEmpty()) {
                            Text(
                                text = strings.clear,
                                color = NeoColors.Red,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { onClearAllData() }
                                    .padding(4.dp)
                            )
                        }
                    }
                }

                // Tab 1: Chip Physical History
                if (activeTab == "chip") {
                    val history = currentCard?.history ?: emptyList()
                    if (history.isNotEmpty()) {
                        val displayHistory = if (showAllHistory) history else history.take(3)
                        val grouped = groupHistoryByDate(displayHistory)
                        items(grouped) { group ->
                            HistoryGroupWidget(
                                group = group,
                                locationAliases = locationAliases,
                                surfaceColor = surfaceColor,
                                borderColor = borderColor,
                                textColor = textColor,
                                textSubtleColor = textSubtleColor,
                                showTransactionColors = settings.showTransactionColors,
                                onEditLocation = { tid, curName ->
                                    editingTerminalId = tid
                                    editingTerminalName = curName
                                }
                            )
                        }

                        if (history.size > 3) {
                            item {
                                Button(
                                    onClick = { showAllHistory = !showAllHistory },
                                    colors = ButtonDefaults.buttonColors(containerColor = surfaceColor),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .neoCard(bgColor = surfaceColor, borderColor = borderColor, shadowOffset = 2.dp, cornerRadius = 8.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(vertical = 10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = if (showAllHistory) strings.showLess else strings.viewAll,
                                            color = textColor,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                        Icon(
                                            imageVector = if (showAllHistory) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = textColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            EmptyTray(
                                title = strings.noChipTransactionsTitle,
                                subtitle = if (currentCard != null) {
                                    strings.noChipTransactionsCardSubtitle
                                } else {
                                    strings.noChipTransactionsIdleSubtitle
                                },
                                surfaceColor = surfaceColor,
                                borderColor = borderColor,
                                textColor = textColor,
                                textSubtleColor = textSubtleColor
                            )
                        }
                    }
                } else {
                    // Tab 2: Saved Cards
                    if (savedCards.isNotEmpty()) {
                        items(savedCards) { card ->
                            SavedCardItemWidget(
                                card = card,
                                active = currentCard?.cardNumber == card.cardNumber,
                                settings = settings,
                                surfaceColor = surfaceColor,
                                borderColor = borderColor,
                                textColor = textColor,
                                textSubtleColor = textSubtleColor,
                                onSelect = { onSelectSavedCard(card) },
                                onDelete = { onDeleteSavedCard(card.cardNumber) }
                            )
                        }
                    } else {
                        item {
                            EmptyTray(
                                title = strings.noSavedCardsTitle,
                                subtitle = strings.noSavedCardsSubtitle,
                                surfaceColor = surfaceColor,
                                borderColor = borderColor,
                                textColor = textColor,
                                textSubtleColor = textSubtleColor
                            )
                        }
                    }
                }
            }
        }

        // Bottom Scanning Status Bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            if (!nfcEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neoCard(bgColor = NeoColors.Red, borderColor = borderColor, shadowOffset = 3.dp, cornerRadius = 8.dp)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.nfcDisabled,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(text = strings.enable, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neoCard(bgColor = surfaceColor, borderColor = borderColor, shadowOffset = 3.dp, cornerRadius = 8.dp)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(NeoColors.Green, CircleShape)
                            .border(1.dp, borderColor, CircleShape)
                    )
                    Text(
                        text = statusMessage,
                        color = textColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

    // Edit Location Alias Dialog
    if (editingTerminalId != null) {
        val tid = editingTerminalId!!
        var tempName by remember { mutableStateOf(editingTerminalName) }
        AlertDialog(
            onDismissRequest = { editingTerminalId = null },
            title = {
                Text(text = strings.editLocationTitle, fontWeight = FontWeight.Black, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "${strings.terminalIdLabel}$tid", fontSize = 12.sp, color = textSubtleColor)
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        label = { Text(strings.locationInputLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveLocationAlias(tid, tempName.trim())
                        editingTerminalId = null
                        Toast.makeText(context, strings.locationSavedToast, Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeoColors.Yellow),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor)
                ) {
                    Text(strings.save, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingTerminalId = null }) {
                    Text(strings.cancel, color = textSubtleColor)
                }
            }
        )
    }
}

@Composable
fun CardVisualWidget(
    currentCard: CardBalanceResult?,
    settings: UserSettings,
    surfaceColor: Color,
    borderColor: Color,
    textColor: Color,
    textSubtleColor: Color,
    onToggleMaskCardNumber: () -> Unit
) {
    val context = LocalContext.current
    val issuer = currentCard?.issuer ?: CardIssuer.UNKNOWN
    val cardRes = when (issuer) {
        CardIssuer.MANDIRI -> R.drawable.emoney
        CardIssuer.BCA -> R.drawable.flazz
        CardIssuer.BRI -> R.drawable.brizzi
        CardIssuer.BNI -> R.drawable.tapcash
        CardIssuer.KCI -> R.drawable.kmt
        CardIssuer.MEGA -> R.drawable.megacash
        CardIssuer.DKI -> R.drawable.jaklingko
        else -> null
    }

    val logoRes = when (issuer) {
        CardIssuer.MANDIRI -> R.drawable.logo_emoney
        CardIssuer.BCA -> R.drawable.logo_flazz
        CardIssuer.BRI -> R.drawable.logo_brizzi
        CardIssuer.BNI -> R.drawable.logo_tapcash
        CardIssuer.KCI -> R.drawable.logo_kmt
        CardIssuer.MEGA -> R.drawable.logo_megacash
        CardIssuer.DKI -> R.drawable.logo_jaklinko
        else -> null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .neoCard(bgColor = surfaceColor, borderColor = borderColor, shadowOffset = 4.dp, cornerRadius = 12.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        if (cardRes != null) {
            Image(
                painter = painterResource(id = cardRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        when (issuer) {
                            CardIssuer.MRTJ -> Color(0xFF005BAA)
                            CardIssuer.CEPAS -> Color(0xFF6B2D82)
                            CardIssuer.SUICA -> Color(0xFF2E7D32)
                            CardIssuer.EDY -> Color(0xFF000059)
                            CardIssuer.TMONEY -> Color(0xFFD32F2F)
                            CardIssuer.CHINA_TRANSIT -> Color(0xFFC62828)
                            CardIssuer.OPAL -> Color(0xFF0072CE)
                            else -> NeoColors.Yellow
                        }
                    )
            )
        }

        // Overlay Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Card Chip Icon
                Box(
                    modifier = Modifier
                        .size(34.dp, 26.dp)
                        .background(Color(0xFFFFD700), RoundedCornerShape(4.dp))
                        .border(1.dp, Color(0xFFB8860B), RoundedCornerShape(4.dp))
                )

                if (logoRes != null) {
                    Image(
                        painter = painterResource(id = logoRes),
                        contentDescription = null,
                        modifier = Modifier.height(28.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    val isDarkBg = issuer in listOf(
                        CardIssuer.MRTJ, CardIssuer.CEPAS, CardIssuer.SUICA,
                        CardIssuer.EDY, CardIssuer.TMONEY, CardIssuer.CHINA_TRANSIT, CardIssuer.OPAL
                    )
                    Text(
                        text = currentCard?.cardType ?: "NFC CARD",
                        color = if (isDarkBg) Color.White else Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Column {
                Text(
                    text = if (currentCard != null) {
                        formatCardNumber(currentCard.cardNumber, settings.maskCardNumber)
                    } else {
                        "•••• •••• •••• ••••"
                    },
                    color = Color.White,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.clickable(enabled = currentCard != null) {
                        currentCard?.cardNumber?.let { number ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Nomor kartu", number))
                            Toast.makeText(context, "Nomor kartu disalin", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = onToggleMaskCardNumber,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = if (settings.maskCardNumber) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle Sensor Nomor Kartu",
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = currentCard?.cardType ?: "Siap Memindai Kartu",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (currentCard != null) {
                        Box(
                            modifier = Modifier
                                .background(NeoColors.Green, RoundedCornerShape(4.dp))
                                .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "TERBACA",
                                color = Color.Black,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BalanceWidget(
    currentCard: CardBalanceResult?,
    settings: UserSettings,
    surfaceColor: Color,
    borderColor: Color,
    textColor: Color,
    textSubtleColor: Color,
    onToggleHideBalance: () -> Unit,
    onShareReceipt: () -> Unit,
    onCopyReceipt: () -> Unit,
    onExportPdf: (CardBalanceResult) -> Unit,
    onPrintReceipt: (CardBalanceResult) -> Unit
) {
    val strings = LocalAppStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .neoCard(bgColor = surfaceColor, borderColor = borderColor, shadowOffset = 3.dp, cornerRadius = 8.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleHideBalance,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    if (settings.hideBalance) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Toggle Saldo",
                    tint = textSubtleColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = strings.currentBalanceTitle,
                color = textSubtleColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
        }

        Text(
            text = if (currentCard != null) {
                if (settings.hideBalance) "Rp ••••••••" else formatRupiah(currentCard.balance)
            } else {
                "Rp 0"
            },
            color = textColor,
            fontSize = 32.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black
        )

        // Actions: Share & Copy
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onShareReceipt,
                enabled = currentCard != null,
                colors = ButtonDefaults.buttonColors(containerColor = NeoColors.Blue),
                modifier = Modifier
                    .weight(1f)
                    .neoCard(bgColor = NeoColors.Blue, borderColor = borderColor, shadowOffset = 2.dp, cornerRadius = 6.dp),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Text(strings.share, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }

            Button(
                onClick = onCopyReceipt,
                enabled = currentCard != null,
                colors = ButtonDefaults.buttonColors(containerColor = surfaceColor),
                modifier = Modifier
                    .weight(1f)
                    .neoCard(bgColor = surfaceColor, borderColor = borderColor, shadowOffset = 2.dp, cornerRadius = 6.dp),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = textColor, modifier = Modifier.size(16.dp))
                    Text(strings.copy, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { currentCard?.let(onExportPdf) },
                enabled = currentCard != null,
                modifier = Modifier.weight(1f)
            ) { Text(strings.exportPdf, fontWeight = FontWeight.Black) }
            OutlinedButton(
                onClick = { currentCard?.let(onPrintReceipt) },
                enabled = currentCard != null,
                modifier = Modifier.weight(1f)
            ) { Text(strings.print, fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
fun TabPill(
    label: String,
    badgeCount: Int?,
    active: Boolean,
    borderColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    val bg = if (active) NeoColors.Yellow else Color.Transparent
    val textCol = if (active) Color.Black else textColor

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            color = textCol,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black
        )
        if (badgeCount != null) {
            Box(
                modifier = Modifier
                    .background(Color.Black, RoundedCornerShape(99.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = badgeCount.toString(),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

data class TransactionGroup(
    val date: String,
    val displayDate: String,
    val transactions: List<CardTransactionItem>
)

fun groupHistoryByDate(items: List<CardTransactionItem>): List<TransactionGroup> {
    val map = linkedMapOf<String, MutableList<CardTransactionItem>>()
    for (tx in items) {
        val list = map.getOrPut(tx.date) { mutableListOf() }
        list.add(tx)
    }

    val sdfIn = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val sdfOut = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))

    return map.map { (dateStr, txs) ->
        val dispDate = try {
            val d = sdfIn.parse(dateStr)
            if (d != null) sdfOut.format(d) else dateStr
        } catch (e: Exception) {
            dateStr
        }
        TransactionGroup(dateStr, dispDate, txs)
    }
}

@Composable
fun HistoryGroupWidget(
    group: TransactionGroup,
    locationAliases: Map<String, String>,
    surfaceColor: Color,
    borderColor: Color,
    textColor: Color,
    textSubtleColor: Color,
    showTransactionColors: Boolean,
    onEditLocation: (String, String) -> Unit
) {
    val strings = LocalAppStrings.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Date Header (Blue / Cyan)
        Text(
            text = group.displayDate,
            color = NeoColors.CyanDate,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 2.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .neoCard(bgColor = surfaceColor, borderColor = borderColor, shadowOffset = 3.dp, cornerRadius = 8.dp)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            group.transactions.forEachIndexed { idx, tx ->
                if (idx > 0) {
                    HorizontalDivider(color = borderColor.copy(alpha = 0.2f), thickness = 1.dp)
                }

                val isTopUp = tx.type == "Top up"
                val typeColor = if (showTransactionColors) {
                    if (isTopUp) NeoColors.Green else NeoColors.Red
                } else textColor

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Top row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text(
                                text = if (isTopUp) strings.topUp else strings.payment,
                                color = typeColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = if (tx.time.length >= 5) tx.time.substring(0, 5) else tx.time,
                                color = textSubtleColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${if (isTopUp) "+ " else "- "}${formatRupiah(tx.amount)}",
                                color = typeColor,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black
                            )
                            if (tx.balance != null) {
                                Text(
                                    text = "${strings.endingBalance}${formatRupiah(tx.balance)}",
                                    color = textSubtleColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Bottom row: Lokasi
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Lokasi",
                                color = textSubtleColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val locName = id.tapmoney.data.TerminalCatalog.resolveLocation(tx.terminalId, locationAliases, strings.locationUnknown)
                            Text(
                                text = locName,
                                color = textColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (!tx.terminalId.isNullOrEmpty()) {
                            IconButton(
                                onClick = { onEditLocation(tx.terminalId, locationAliases[tx.terminalId] ?: "") },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit Lokasi",
                                    tint = textSubtleColor,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SavedCardItemWidget(
    card: StoredCard,
    active: Boolean,
    settings: UserSettings,
    surfaceColor: Color,
    borderColor: Color,
    textColor: Color,
    textSubtleColor: Color,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val strings = LocalAppStrings.current
    val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale("id", "ID"))
    val dateStr = sdf.format(Date(card.timestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .neoCard(
                bgColor = if (active) NeoColors.Yellow.copy(alpha = 0.2f) else surfaceColor,
                borderColor = if (active) NeoColors.Blue else borderColor,
                shadowOffset = 2.dp,
                cornerRadius = 8.dp
            )
            .clickable { onSelect() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = card.cardType,
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black
                )
                if (active) {
                    Box(
                        modifier = Modifier
                            .background(NeoColors.Green, RoundedCornerShape(3.dp))
                            .border(1.dp, borderColor, RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(strings.active, color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            Text(
                text = formatCardNumber(card.cardNumber, settings.maskCardNumber),
                color = textSubtleColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${strings.tapTimePrefix}$dateStr",
                color = textSubtleColor,
                fontSize = 10.sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = if (settings.hideBalance) "Rp ••••••" else formatRupiah(card.balance),
                color = textColor,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black
            )

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = strings.delete, tint = NeoColors.Red, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun EmptyTray(
    title: String,
    subtitle: String,
    surfaceColor: Color,
    borderColor: Color,
    textColor: Color,
    textSubtleColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .neoCard(bgColor = surfaceColor, borderColor = borderColor, shadowOffset = 3.dp, cornerRadius = 8.dp)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            Icons.Default.CreditCard,
            contentDescription = null,
            tint = textSubtleColor,
            modifier = Modifier.size(36.dp)
        )
        Text(
            text = title,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            color = textSubtleColor,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

fun receiptText(card: CardBalanceResult, isEnglish: Boolean = false): String {
    return ReceiptFormatter.formatTextReceipt(card, isEnglish)
}

fun shareReceipt(context: Context, card: CardBalanceResult?, isEnglish: Boolean = false) {
    if (card == null) return
    val text = receiptText(card, isEnglish)

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, if (isEnglish) "Share Receipt" else "Bagikan Struk"))
}

fun copyReceipt(context: Context, card: CardBalanceResult?, isEnglish: Boolean = false) {
    if (card == null) return
    val text = receiptText(card, isEnglish)

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Struk Saldo", text))
    Toast.makeText(
        context,
        if (isEnglish) "Receipt copied to clipboard" else "Struk disalin ke clipboard",
        Toast.LENGTH_SHORT
    ).show()
}
