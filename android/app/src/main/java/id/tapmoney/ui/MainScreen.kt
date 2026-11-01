package id.tapmoney.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val headerColor = if (isDark) NeoColors.CyanDateDark else NeoColors.CyanDate

    var activeTab by remember { mutableStateOf("chip") }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var editingTerminalId by remember { mutableStateOf<String?>(null) }
    var editingTerminalName by remember { mutableStateOf("") }
    var loadingTerminalIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showAllHistory by remember(currentCard?.cardId, currentCard?.cardNumber) { mutableStateOf(false) }
    var isInitialLocLoading by remember(currentCard?.cardId, currentCard?.cardNumber) {
        mutableStateOf(currentCard != null && currentCard.history.isNotEmpty())
    }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(currentCard?.cardId, currentCard?.cardNumber) {
        if (currentCard != null && currentCard.history.isNotEmpty()) {
            isInitialLocLoading = true
            delay(400)
            isInitialLocLoading = false
        }
    }

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
                isDark = isDark,
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
                        isDark = isDark,
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
                                isDark = isDark,
                                borderColor = borderColor,
                                textColor = textColor,
                                onClick = { activeTab = "chip" }
                            )

                            TabPill(
                                label = strings.tabSavedCards,
                                badgeCount = if (savedCards.isNotEmpty()) savedCards.size else null,
                                active = activeTab == "saved",
                                isDark = isDark,
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
                    val history = currentCard?.let { ReceiptFormatter.resolveEndingBalances(it) } ?: emptyList()
                    if (history.isNotEmpty()) {
                        val displayHistory = if (showAllHistory) history else history.take(3)
                        val isEn = resolveIsEnglish(settings.appLanguage)
                        val grouped = groupHistoryByDate(displayHistory, isEn)
                        items(grouped) { group ->
                            HistoryGroupWidget(
                                group = group,
                                locationAliases = locationAliases,
                                loadingTerminalIds = loadingTerminalIds,
                                isInitialLocLoading = isInitialLocLoading,
                                isDark = isDark,
                                surfaceColor = surfaceColor,
                                borderColor = borderColor,
                                textColor = textColor,
                                textSubtleColor = textSubtleColor,
                                headerColor = headerColor,
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
            containerColor = surfaceColor,
            titleContentColor = textColor,
            textContentColor = textColor,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.border(2.dp, borderColor, RoundedCornerShape(12.dp)),
            title = {
                Text(text = strings.editLocationTitle, fontWeight = FontWeight.Black, fontSize = 16.sp, color = textColor)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "${strings.terminalIdLabel}$tid", fontSize = 12.sp, color = textSubtleColor)
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        label = { Text(strings.locationInputLabel) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor,
                            focusedBorderColor = headerColor,
                            unfocusedBorderColor = borderColor,
                            focusedLabelColor = headerColor,
                            unfocusedLabelColor = textSubtleColor
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = tempName.trim()
                        editingTerminalId = null
                        coroutineScope.launch {
                            loadingTerminalIds = loadingTerminalIds + tid
                            onSaveLocationAlias(tid, trimmed)
                            delay(600)
                            loadingTerminalIds = loadingTerminalIds - tid
                            Toast.makeText(context, strings.locationSavedToast, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeoColors.Yellow),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor)
                ) {
                    Text(strings.save, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Button(
                    onClick = { editingTerminalId = null },
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
    val strings = LocalAppStrings.current
    val issuer = currentCard?.issuer ?: CardIssuer.UNKNOWN

    val cardColor = when (issuer) {
        CardIssuer.BCA -> Color(0xFF005CA9)
        CardIssuer.MANDIRI -> Color(0xFF003876)
        CardIssuer.BRI -> Color(0xFF00529C)
        CardIssuer.BNI -> Color(0xFFF15A24)
        CardIssuer.DKI -> Color(0xFFE31E24)
        CardIssuer.KCI -> Color(0xFFDA251D)
        CardIssuer.MEGA -> Color(0xFFF37023)
        CardIssuer.MRTJ -> Color(0xFF005BAA)
        CardIssuer.CEPAS -> Color(0xFF6B2D82)
        CardIssuer.SUICA -> Color(0xFF2E7D32)
        CardIssuer.EDY -> Color(0xFF000059)
        CardIssuer.TMONEY -> Color(0xFFD32F2F)
        CardIssuer.CHINA_TRANSIT -> Color(0xFFC62828)
        CardIssuer.OPAL -> Color(0xFF0072CE)
        else -> Color(0xFF1E293B)
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
            .neoCard(bgColor = cardColor, borderColor = borderColor, shadowOffset = 4.dp, cornerRadius = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cardColor)
    ) {
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
                    Text(
                        text = currentCard?.cardType ?: strings.defaultCardLabel,
                        color = Color.White,
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
                            clipboard.setPrimaryClip(ClipData.newPlainText(strings.cardNumberLabel, number))
                            Toast.makeText(context, strings.cardNumberCopiedToast, Toast.LENGTH_SHORT).show()
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
                                contentDescription = strings.toggleMaskCardNumber,
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = currentCard?.cardType ?: strings.readyToScan,
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
                                text = strings.statusRead,
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
    isDark: Boolean,
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
                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(strings.share, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
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
        val actionTextColor = if (isDark) Color.White else Color.Black
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { currentCard?.let(onExportPdf) },
                enabled = currentCard != null,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = actionTextColor,
                    disabledContentColor = textSubtleColor
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    if (currentCard != null) borderColor else borderColor.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = strings.exportPdf,
                    color = if (currentCard != null) actionTextColor else textSubtleColor,
                    fontWeight = FontWeight.Black
                )
            }
            OutlinedButton(
                onClick = { currentCard?.let(onPrintReceipt) },
                enabled = currentCard != null,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = actionTextColor,
                    disabledContentColor = textSubtleColor
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    if (currentCard != null) borderColor else borderColor.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = strings.print,
                    color = if (currentCard != null) actionTextColor else textSubtleColor,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
fun TabPill(
    label: String,
    badgeCount: Int?,
    active: Boolean,
    isDark: Boolean,
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
            val badgeBg = if (active) Color.Black else (if (isDark) Color(0xFF141416) else Color.Black)
            val badgeBorder = if (active) Color.Black else (if (isDark) borderColor else Color.Black)
            Box(
                modifier = Modifier
                    .background(badgeBg, RoundedCornerShape(99.dp))
                    .border(1.dp, badgeBorder, RoundedCornerShape(99.dp))
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

fun groupHistoryByDate(items: List<CardTransactionItem>, isEnglish: Boolean = false): List<TransactionGroup> {
    val sortedItems = items.sortedWith(compareByDescending<CardTransactionItem> { "${it.date} ${it.time}" })
    val map = linkedMapOf<String, MutableList<CardTransactionItem>>()
    for (tx in sortedItems) {
        val list = map.getOrPut(tx.date) { mutableListOf() }
        list.add(tx)
    }

    val sdfIn = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val outLocale = if (isEnglish) Locale.US else Locale("id", "ID")
    val sdfOut = SimpleDateFormat("dd MMMM yyyy", outLocale)

    return map.map { (dateStr, txs) ->
        val dispDate = try {
            val d = sdfIn.parse(dateStr)
            if (d != null) sdfOut.format(d) else dateStr
        } catch (_: Exception) {
            dateStr
        }
        TransactionGroup(dateStr, dispDate, txs)
    }
}

@Composable
fun LocationLoadingPulse(
    modifier: Modifier = Modifier,
    isDark: Boolean = false,
    borderColor: Color = if (isDark) Color(0xFF3F3F46) else Color(0xFF111827)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "LocationPulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    val pulseColor = if (isDark) Color.White else Color.Black

    Box(
        modifier = modifier
            .width(110.dp)
            .height(15.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(pulseColor.copy(alpha = alpha))
            .border(1.dp, borderColor.copy(alpha = 0.35f), RoundedCornerShape(3.dp))
    )
}

@Composable
fun HistoryGroupWidget(
    group: TransactionGroup,
    locationAliases: Map<String, String>,
    loadingTerminalIds: Set<String>,
    isInitialLocLoading: Boolean,
    isDark: Boolean,
    surfaceColor: Color,
    borderColor: Color,
    textColor: Color,
    textSubtleColor: Color,
    headerColor: Color,
    showTransactionColors: Boolean,
    onEditLocation: (String, String) -> Unit
) {
    val strings = LocalAppStrings.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Date Header
        Text(
            text = group.displayDate,
            color = headerColor,
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
                                text = strings.locationHeader,
                                color = textSubtleColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            val isLocLoading = isInitialLocLoading || (!tx.terminalId.isNullOrEmpty() && loadingTerminalIds.contains(tx.terminalId))
                            Crossfade(
                                targetState = isLocLoading,
                                animationSpec = tween(durationMillis = 220),
                                label = "LocationCrossfade"
                            ) { loading ->
                                if (loading) {
                                    LocationLoadingPulse(
                                        modifier = Modifier.padding(vertical = 1.dp),
                                        isDark = isDark,
                                        borderColor = borderColor
                                    )
                                } else {
                                    val locName = id.tapmoney.data.TerminalCatalog.resolveLocation(tx.terminalId, locationAliases, strings.locationUnknown)
                                    Text(
                                        text = locName,
                                        color = textColor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (!tx.terminalId.isNullOrEmpty()) {
                            IconButton(
                                onClick = { onEditLocation(tx.terminalId, locationAliases[tx.terminalId] ?: "") },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = strings.editLocationAction,
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
    val dateLocale = if (resolveIsEnglish(settings.appLanguage)) Locale.US else Locale("id", "ID")
    val sdf = SimpleDateFormat("dd MMM, HH:mm", dateLocale)
    val dateStr = sdf.format(Date(card.timestamp))

    val activeCardBg = Color(0xFF1E293B)
    val cardBg = if (active) activeCardBg else surfaceColor
    val cardBorder = if (active) NeoColors.Blue else borderColor

    val titleColor = if (active) Color.White else textColor
    val cardNumColor = if (active) Color.White.copy(alpha = 0.85f) else textSubtleColor
    val timeColor = if (active) Color.White.copy(alpha = 0.7f) else textSubtleColor
    val balanceColor = if (active) Color.White else textColor

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .neoCard(
                bgColor = cardBg,
                borderColor = cardBorder,
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
                    color = titleColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black
                )
                if (active) {
                    Box(
                        modifier = Modifier
                            .background(NeoColors.Green, RoundedCornerShape(3.dp))
                            .border(1.dp, Color.Black, RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(strings.active, color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            Text(
                text = formatCardNumber(card.cardNumber, settings.maskCardNumber),
                color = cardNumColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${strings.tapTimePrefix}$dateStr",
                color = timeColor,
                fontSize = 10.sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = if (settings.hideBalance) "Rp ••••••" else formatRupiah(card.balance),
                color = balanceColor,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black
            )

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = strings.delete,
                    tint = if (active) Color(0xFFFCA5A5) else NeoColors.Red,
                    modifier = Modifier.size(16.dp)
                )
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
    val strings = getAppStrings(if (isEnglish) "en" else "id", context)

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(strings.receiptShareTitle, text))
    Toast.makeText(
        context,
        strings.receiptCopiedToast,
        Toast.LENGTH_SHORT
    ).show()
}
