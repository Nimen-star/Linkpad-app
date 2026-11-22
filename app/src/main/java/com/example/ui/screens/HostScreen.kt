package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.protocol.KeymapProfile
import com.example.protocol.OsInputBridgeMode
import com.example.protocol.PadButton
import com.example.protocol.PlayerSlotState
import com.example.transport.LinkPadConstants
import com.example.uhid.UhidBridgeState
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.components.LiquidGlassDialog
import com.example.ui.components.LiquidGlassPanel
import com.example.ui.components.LiquidGlassTextField
import com.example.ui.components.TapToRevealIpChip
import com.example.ui.theme.BrushedSilver
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.GlassFillSubtle
import com.example.ui.theme.GlassRimMid
import com.example.ui.theme.GraphiteElevated
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.HairlineSubtle
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PewterMuted
import com.example.ui.theme.PrecisionCrimson
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TitaniumText
import com.example.ui.theme.WarmAmber
import com.example.ui.theme.playerAccentColor
import com.example.ui.viewmodel.EnvironmentStatus

@Composable
fun HostScreen(
    hostActive: Boolean,
    pairingCode: String,
    localIp: String,
    slots: List<PlayerSlotState>,
    environmentStatus: EnvironmentStatus,
    accessibilityBridgeActive: Boolean,
    imeBridgeActive: Boolean,
    osInputBridgeMode: OsInputBridgeMode,
    hardwareBridgeState: UhidBridgeState,
    allowFavoritesWithoutCode: Boolean,
    trustedDeviceCount: Int,
    onBack: () -> Unit,
    onStartHost: () -> Unit,
    onStopHost: () -> Unit,
    onRegenerateCode: () -> Unit,
    onUpdateSlotKeymap: (Int, KeymapProfile) -> Unit,
    onKickPlayer: (Int) -> Unit,
    onToggleAllowFavoritesWithoutCode: (Boolean) -> Unit,
    onClearTrustedDevices: () -> Unit,
    onSelectOsInputBridgeMode: (OsInputBridgeMode) -> Unit,
    onRefreshSystemStatus: () -> Unit,
    onRefreshHardwareBridge: () -> Unit,
    onActivateHardwareGamepads: () -> Unit,
    onPairWirelessDebugging: (String, String) -> Unit,
    onPostWirelessPairNotification: () -> Unit = {}
) {
    val context = LocalContext.current
    var showWirelessPairModal by remember { mutableStateOf(false) }
    var showAccessibilityConsentDialog by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        onRefreshSystemStatus()
        onRefreshHardwareBridge()
    }

    LaunchedEffect(Unit) {
        if (!hostActive) {
            onStartHost()
        }
    }

    LiquidGlassBackdrop {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 1040.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                val isWide = maxWidth >= 680.dp

                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("host_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = TitaniumText
                                )
                            }
                            Text(
                                text = "Host",
                                style = MaterialTheme.typography.titleLarge,
                                color = TitaniumText
                            )
                        }

                        if (hostActive) {
                            OutlinedButton(
                                onClick = onStopHost,
                                border = BorderStroke(1.dp, PrecisionCrimson.copy(alpha = 0.45f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = GlassFillSubtle,
                                    contentColor = PrecisionCrimson
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("stop_host_button")
                            ) {
                                Text("Stop", style = MaterialTheme.typography.labelLarge)
                            }
                        } else {
                            Button(
                                onClick = onStartHost,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BrushedSilver,
                                    contentColor = ObsidianBg
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("start_host_button")
                            ) {
                                Text("Start", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    val leftColumn: @Composable () -> Unit = {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            // Pairing Code Panel (Clean Wi-Fi UDP Host card)
                            LiquidGlassPanel(
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "PAIRING CODE",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = PewterMuted
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(
                                                onClick = {
                                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                    cm?.setPrimaryClip(ClipData.newPlainText("Pairing Code", pairingCode))
                                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                                },
                                                enabled = pairingCode.isNotEmpty(),
                                                modifier = Modifier.testTag("copy_code_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = "Copy code",
                                                    tint = BrushedSilver,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = onRegenerateCode,
                                                enabled = hostActive,
                                                modifier = Modifier.testTag("regenerate_code_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = "New code",
                                                    tint = BrushedSilver,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }

                                    val digits = if (pairingCode.length == 6) pairingCode else "------"
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("host_pairing_code"),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        for (i in 0 until 6) {
                                            if (i == 3) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }
                                            LiquidGlassPanel(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(54.dp),
                                                shape = RoundedCornerShape(12.dp),
                                                subtle = true
                                            ) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = digits[i].toString(),
                                                        fontFamily = JetBrainsMonoFamily,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 24.sp,
                                                        color = if (hostActive) TitaniumText else PewterMuted
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TapToRevealIpChip(
                                            address = localIp,
                                            port = LinkPadConstants.HOST_UDP_PORT,
                                            testTag = "host_ip_reveal_chip"
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Wifi,
                                                contentDescription = "Wi-Fi UDP Host",
                                                tint = if (hostActive) SignalEmerald else PewterMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = if (hostActive) "WI-FI UDP READY" else "OFFLINE",
                                                fontFamily = JetBrainsMonoFamily,
                                                fontSize = 10.sp,
                                                color = if (hostActive) SignalEmerald else PewterMuted
                                            )
                                        }
                                    }
                                }
                            }

                            // Players P1-P4
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "PLAYERS · ${slots.count { it.connected }}/4",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = PewterMuted
                                )
                                slots.forEach { slotState ->
                                    PlayerSlotRow(
                                        slotState = slotState,
                                        onUpdateKeymap = { onUpdateSlotKeymap(slotState.slot, it) },
                                        onKick = { onKickPlayer(slotState.slot) }
                                    )
                                }
                            }
                        }
                    }

                    val rightColumn: @Composable () -> Unit = {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            // Primary Zero-ADB OS Input Bridge (Works on Fire TV, Android TV, Phones — No Root / No ADB)
                            ZeroAdbOsInputCard(
                                environmentStatus = environmentStatus,
                                accessibilityActive = accessibilityBridgeActive,
                                imeActive = imeBridgeActive,
                                selectedMode = osInputBridgeMode,
                                onSelectMode = onSelectOsInputBridgeMode,
                                onEnableAccessibilityClick = { showAccessibilityConsentDialog = true },
                                onOpenImeSettings = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                },
                                onOpenImePicker = {
                                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                    runCatching { imm?.showInputMethodPicker() }
                                        .onFailure {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                )
                                            }
                                        }
                                }
                            )

                            // Optional Advanced Kernel UHID Gamepad Engine
                            HardwareGamepadsCard(
                                bridgeState = hardwareBridgeState,
                                onRefresh = onRefreshHardwareBridge,
                                onActivate = onActivateHardwareGamepads,
                                onOpenPairDialog = { showWirelessPairModal = true },
                                onPostPairNotification = onPostWirelessPairNotification
                            )

                            // Trusted Favorites Setting Card
                            LiquidGlassPanel(
                                shape = RoundedCornerShape(22.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "HOST SECURITY",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = PewterMuted
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = null,
                                                tint = BrushedSilver,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Favorites · No Code",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = TitaniumText
                                                )
                                                Text(
                                                    text = if (trustedDeviceCount == 0) {
                                                        "No saved phones yet"
                                                    } else {
                                                        "$trustedDeviceCount paired phone${if (trustedDeviceCount == 1) "" else "s"} remembered"
                                                    },
                                                    fontFamily = JetBrainsMonoFamily,
                                                    fontSize = 10.sp,
                                                    color = PewterMuted
                                                )
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (trustedDeviceCount > 0) {
                                                OutlinedButton(
                                                    onClick = onClearTrustedDevices,
                                                    border = BorderStroke(1.dp, GlassRimMid),
                                                    modifier = Modifier
                                                        .minimumInteractiveComponentSize()
                                                        .testTag("clear_trusted_devices_button")
                                                ) {
                                                    Text(
                                                        text = "Clear",
                                                        color = PrecisionCrimson,
                                                        fontSize = 12.sp,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                            Switch(
                                                checked = allowFavoritesWithoutCode,
                                                onCheckedChange = onToggleAllowFavoritesWithoutCode,
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = TitaniumText,
                                                    checkedTrackColor = SignalEmerald.copy(alpha = 0.65f)
                                                ),
                                                modifier = Modifier.testTag("allow_favorites_switch")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isWide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(modifier = Modifier.weight(1f)) { leftColumn() }
                            Box(modifier = Modifier.weight(1f)) { rightColumn() }
                        }
                    } else {
                        leftColumn()
                        rightColumn()
                    }
                }
            }
        }
    }

    if (showWirelessPairModal) {
        val detectedPort = (hardwareBridgeState as? UhidBridgeState.SetupRequired)?.wirelessPairPort
        var pairPortText by remember(detectedPort) {
            mutableStateOf(detectedPort?.toString() ?: "")
        }
        var pairPinText by remember { mutableStateOf("") }

        LiquidGlassDialog(
            onDismissRequest = { showWirelessPairModal = false },
            accentColor = CobaltBlue,
            maxWidthDp = 420
        ) {
            Text(
                text = "Pair Wireless Debugging",
                style = MaterialTheme.typography.titleLarge,
                color = TitaniumText
            )
            Text(
                text = "On Android 11+, open Developer Options → Wireless debugging → Pair device with pairing code, then enter the 6-digit PIN and Port below.",
                style = MaterialTheme.typography.bodySmall,
                color = PewterMuted
            )
            LiquidGlassTextField(
                value = pairPortText,
                onValueChange = { input -> pairPortText = input.filter { it.isDigit() }.take(5) },
                label = "PAIRING PORT",
                placeholder = detectedPort?.toString() ?: "e.g. 41235",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wireless_pair_port_input")
            )
            LiquidGlassTextField(
                value = pairPinText,
                onValueChange = { input -> pairPinText = input.filter { it.isDigit() }.take(6) },
                label = "6-DIGIT WI-FI PAIRING CODE",
                placeholder = "••••••",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    letterSpacing = 5.sp,
                    color = TitaniumText
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wireless_pair_pin_input")
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showWirelessPairModal = false },
                    border = BorderStroke(1.dp, GlassRimMid),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                ) {
                    Text("Cancel", color = PewterMuted)
                }
                Button(
                    enabled = pairPinText.length == 6 && pairPortText.isNotBlank(),
                    onClick = {
                        showWirelessPairModal = false
                        onPairWirelessDebugging(pairPortText, pairPinText)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CobaltBlue,
                        contentColor = ObsidianBg
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("submit_wireless_pair_button")
                ) {
                    Text("Pair & Enable", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    if (showAccessibilityConsentDialog) {
        LiquidGlassDialog(
            onDismissRequest = { showAccessibilityConsentDialog = false },
            accentColor = SignalEmerald,
            maxWidthDp = 440
        ) {
            Text(
                text = "LinkPad OS Input Bridge",
                style = MaterialTheme.typography.titleLarge,
                color = TitaniumText
            )
            Text(
                text = "To let your connected Wi-Fi UDP controller navigate Fire TV / Android TV apps (D-Pad, Back, Home) and map controls onto touch-only games without Root or ADB, LinkPad uses Android's Accessibility Service.",
                style = MaterialTheme.typography.bodySmall,
                color = TitaniumText
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = GlassFillSubtle,
                border = BorderStroke(1.dp, SignalEmerald.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Privacy guarantee: LinkPad never reads passwords, personal text, notifications, or screen contents. It only dispatches your controller presses locally on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PewterMuted,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showAccessibilityConsentDialog = false },
                    border = BorderStroke(1.dp, GlassRimMid),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                ) {
                    Text("Not Now", color = PewterMuted)
                }
                Button(
                    onClick = {
                        showAccessibilityConsentDialog = false
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SignalEmerald,
                        contentColor = ObsidianBg
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("confirm_accessibility_settings_button")
                ) {
                    Text("Open Settings", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ZeroAdbOsInputCard(
    environmentStatus: EnvironmentStatus,
    accessibilityActive: Boolean,
    imeActive: Boolean,
    selectedMode: OsInputBridgeMode,
    onSelectMode: (OsInputBridgeMode) -> Unit,
    onEnableAccessibilityClick: () -> Unit,
    onOpenImeSettings: () -> Unit,
    onOpenImePicker: () -> Unit
) {
    val bridgeReady = accessibilityActive || environmentStatus.imeSelected || imeActive
    val accent = if (bridgeReady) SignalEmerald else CobaltBlue

    LiquidGlassPanel(
        shape = RoundedCornerShape(22.dp),
        elevated = bridgeReady,
        accentBorderTint = accent.copy(alpha = 0.72f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessibilityNew,
                        contentDescription = "Zero-ADB OS Input",
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "OS Input · No ADB / No Root",
                            style = MaterialTheme.typography.titleMedium,
                            color = TitaniumText
                        )
                        Text(
                            text = "WI-FI UDP → TV, TOUCH GAMES & EMULATORS",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            color = accent
                        )
                    }
                }
            }

            // Live bridge status pills
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (accessibilityActive) SignalEmerald.copy(alpha = 0.16f) else GlassFillSubtle,
                    border = BorderStroke(
                        1.dp,
                        if (accessibilityActive) SignalEmerald.copy(alpha = 0.7f) else HairlineBorder
                    )
                ) {
                    Text(
                        text = if (accessibilityActive) "ACCESSIBILITY · ACTIVE" else "ACCESSIBILITY · OFF",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (accessibilityActive) SignalEmerald else WarmAmber,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                val imeLabel = when {
                    environmentStatus.imeSelected || imeActive -> "KEYBOARD IME · SELECTED"
                    environmentStatus.imeEnabled -> "KEYBOARD IME · ENABLED"
                    else -> "KEYBOARD IME · OFF"
                }
                val imeColor = when {
                    environmentStatus.imeSelected || imeActive -> SignalEmerald
                    environmentStatus.imeEnabled -> CobaltBlue
                    else -> PewterMuted
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (environmentStatus.imeSelected || imeActive) {
                        SignalEmerald.copy(alpha = 0.16f)
                    } else {
                        GlassFillSubtle
                    },
                    border = BorderStroke(1.dp, imeColor.copy(alpha = 0.55f))
                ) {
                    Text(
                        text = imeLabel,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = imeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Input mode switcher
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "OS INPUT MODE",
                    style = MaterialTheme.typography.labelMedium,
                    color = PewterMuted
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OsInputBridgeMode.entries.forEach { mode ->
                        val isSelected = mode == selectedMode
                        val icon = when (mode) {
                            OsInputBridgeMode.TV_DPAD_NAV -> Icons.Default.Tv
                            OsInputBridgeMode.TOUCH_GAMEPAD -> Icons.Default.TouchApp
                            OsInputBridgeMode.KEYBOARD_IME -> Icons.Default.Keyboard
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) CobaltBlue.copy(alpha = 0.22f) else GlassFillSubtle,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) CobaltBlue else GlassRimMid
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectMode(mode) }
                                .testTag("os_mode_${mode.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = mode.label,
                                    tint = if (isSelected) CobaltBlue else PewterMuted,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = mode.label,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) TitaniumText else PewterMuted
                                )
                            }
                        }
                    }
                }
                Text(
                    text = selectedMode.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = PewterMuted
                )
            }

            // Action Buttons for Accessibility & Keyboard IME
            Button(
                onClick = onEnableAccessibilityClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (accessibilityActive) GlassFillSubtle else SignalEmerald,
                    contentColor = if (accessibilityActive) SignalEmerald else ObsidianBg
                ),
                border = if (accessibilityActive) BorderStroke(1.dp, SignalEmerald.copy(alpha = 0.6f)) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("enable_accessibility_bridge_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AccessibilityNew,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (accessibilityActive) "Accessibility Bridge Active · Settings" else "Enable Accessibility Bridge",
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenImeSettings,
                    border = BorderStroke(1.dp, GlassRimMid),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("open_ime_settings_button")
                ) {
                    Text(
                        text = "1. Enable Keyboard",
                        color = BrushedSilver,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onOpenImePicker,
                    border = BorderStroke(
                        1.dp,
                        if (environmentStatus.imeSelected) SignalEmerald.copy(alpha = 0.55f) else CobaltBlue.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("select_ime_picker_button")
                ) {
                    Text(
                        text = "2. Use Keyboard",
                        color = if (environmentStatus.imeSelected) SignalEmerald else CobaltBlue,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun HardwareGamepadsCard(
    bridgeState: UhidBridgeState,
    onRefresh: () -> Unit,
    onActivate: () -> Unit,
    onOpenPairDialog: () -> Unit,
    onPostPairNotification: () -> Unit
) {
    val context = LocalContext.current
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onPostPairNotification()
            Toast.makeText(context, "Open Developer Options → Wireless debugging, then reply in notification shade", Toast.LENGTH_LONG).show()
        }
    }

    val isActive = bridgeState is UhidBridgeState.Active
    val isBusy = bridgeState is UhidBridgeState.Checking ||
        bridgeState is UhidBridgeState.StartingAgent ||
        bridgeState is UhidBridgeState.WaitingTrustPrompt
    val isError = bridgeState is UhidBridgeState.Error

    val borderAccent = when {
        isActive -> SignalEmerald
        isError -> PrecisionCrimson
        isBusy -> CobaltBlue
        else -> GlassRimMid
    }

    LiquidGlassPanel(
        shape = RoundedCornerShape(22.dp),
        elevated = isActive,
        subtle = !isActive,
        accentBorderTint = if (isActive || isError || isBusy) borderAccent else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Gamepad,
                        contentDescription = "Optional Kernel Hardware Gamepad ID",
                        tint = if (isActive) SignalEmerald else BrushedSilver,
                        modifier = Modifier.size(19.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Kernel Gamepad ID · Optional",
                            style = MaterialTheme.typography.titleMedium,
                            color = TitaniumText
                        )
                        Text(
                            text = when (bridgeState) {
                                is UhidBridgeState.Idle -> "OPTIONAL /DEV/UHID FOR NATIVE CONTROLLER APPS"
                                is UhidBridgeState.Checking -> "PROBING KERNEL BRIDGE..."
                                is UhidBridgeState.SetupRequired ->
                                    if (bridgeState.tcp5555Open) "ADB PORT 5555 OPEN · TAP ENABLE"
                                    else "BLOCKED BY ANDROID WITHOUT ADB/ROOT"
                                is UhidBridgeState.WaitingTrustPrompt -> "TAP 'ALLOW' ON SYSTEM PROMPT"
                                is UhidBridgeState.StartingAgent -> "LAUNCHING UHID ENGINE..."
                                is UhidBridgeState.Active -> "ACTIVE · ${bridgeState.backend.label.uppercase()}"
                                is UhidBridgeState.Error -> "ADB NOT REACHABLE ON THIS DEVICE"
                            },
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            color = if (isActive) SignalEmerald else if (isError) PrecisionCrimson else PewterMuted
                        )
                    }
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("refresh_hardware_bridge_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh hardware status",
                        tint = BrushedSilver,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            when (bridgeState) {
                is UhidBridgeState.Active -> {
                    Text(
                        text = "Physical /dev/uhid gamepads active for all 4 Wi-Fi UDP slots.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PewterMuted
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (slot in 1..4) {
                            val active = (bridgeState.activeSlotsMask and (1 shl (slot - 1))) != 0
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (active) SignalEmerald.copy(alpha = 0.2f) else GlassFillSubtle,
                                border = BorderStroke(
                                    1.dp,
                                    if (active) SignalEmerald else HairlineBorder
                                )
                            ) {
                                Text(
                                    text = "HID $slot",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (active) SignalEmerald else PewterMuted,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                is UhidBridgeState.Checking,
                is UhidBridgeState.StartingAgent,
                is UhidBridgeState.WaitingTrustPrompt -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = CobaltBlue,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = if (bridgeState is UhidBridgeState.WaitingTrustPrompt) {
                                "Check 'Always allow' and tap Allow."
                            } else {
                                "Starting UHID hardware service..."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TitaniumText
                        )
                    }
                }

                is UhidBridgeState.SetupRequired -> {
                    Text(
                        text = bridgeState.guidance,
                        style = MaterialTheme.typography.bodySmall,
                        color = PewterMuted
                    )
                }

                is UhidBridgeState.Error -> {
                    Text(
                        text = bridgeState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = PrecisionCrimson
                    )
                    Text(
                        text = "Tip: Fire TV firmware blocks self-loopback ADB. Use OS Input above without ADB, or tap 'Activate TV UHID' from your Controller phone after connecting on Wi-Fi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PewterMuted
                    )
                }

                is UhidBridgeState.Idle -> {
                    Text(
                        text = "Only needed if a game ignores Accessibility D-Pad, Touch Mapper, and Keyboard IME and strictly demands a kernel hardware gamepad.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PewterMuted
                    )
                }
            }

            if (!isActive && !isBusy) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onActivate,
                        border = BorderStroke(1.dp, GlassRimMid),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("enable_hardware_gamepads_button")
                    ) {
                        Text(
                            text = if (isError) "Retry Kernel ID" else "Try Kernel ID",
                            color = BrushedSilver,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        OutlinedButton(
                            onClick = onOpenPairDialog,
                            border = BorderStroke(1.dp, CobaltBlue.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("pair_wireless_debugging_button")
                        ) {
                            Text(
                                text = "Pair Wi-Fi Debug",
                                color = CobaltBlue,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onPostPairNotification()
                                Toast.makeText(
                                    context,
                                    "Shade notification active — open Wireless debugging and enter code in notification",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        border = BorderStroke(1.dp, WarmAmber.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("shade_pair_notification_button")
                    ) {
                        Text(
                            text = "Quick-Pair from Notification Shade (Stay in Settings)",
                            color = WarmAmber,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerSlotRow(
    slotState: PlayerSlotState,
    onUpdateKeymap: (KeymapProfile) -> Unit,
    onKick: () -> Unit
) {
    val accent = playerAccentColor(slotState.slot)
    var menuExpanded by remember { mutableStateOf(false) }

    val pressedButtons = remember(slotState.buttonMask) {
        if (slotState.buttonMask == 0) emptyList()
        else PadButton.entries.filter { (slotState.buttonMask and it.bit) != 0 }.take(5)
    }

    LiquidGlassPanel(
        shape = RoundedCornerShape(16.dp),
        elevated = slotState.connected,
        subtle = !slotState.connected,
        accentBorderTint = if (slotState.connected) accent else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (slotState.connected) accent.copy(alpha = 0.16f) else GlassFillSubtle,
                        border = BorderStroke(1.dp, if (slotState.connected) accent.copy(alpha = 0.65f) else GlassRimMid)
                    ) {
                        Text(
                            text = "P${slotState.slot}",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            color = if (slotState.connected) accent else PewterMuted,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (slotState.connected) slotState.deviceName else "Empty",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (slotState.connected) TitaniumText else PewterMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (slotState.connected) {
                            Text(
                                text = "${slotState.transport.badge} · ${slotState.layout.label} · ${slotState.rttMs} ms",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = PewterMuted
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        var focused by remember { mutableStateOf(false) }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = GlassFillSubtle,
                            border = BorderStroke(1.dp, if (focused) BrushedSilver else GlassRimMid),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .onFocusChanged { focused = it.isFocused }
                                .focusable()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { menuExpanded = true }
                                .testTag("slot_${slotState.slot}_keymap")
                        ) {
                            Text(
                                text = slotState.keymapProfile.displayName,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false,
                                color = BrushedSilver,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            containerColor = GraphiteElevated
                        ) {
                            KeymapProfile.entries.forEach { profile ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = profile.displayName,
                                            color = if (profile == slotState.keymapProfile) CobaltBlue else TitaniumText
                                        )
                                    },
                                    onClick = {
                                        onUpdateKeymap(profile)
                                        menuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (slotState.connected) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = onKick,
                            modifier = Modifier.testTag("kick_slot_${slotState.slot}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Kick P${slotState.slot}",
                                tint = PewterMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (slotState.connected && pressedButtons.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    pressedButtons.forEach { btn ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accent.copy(alpha = 0.22f),
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.65f))
                        ) {
                            Text(
                                text = btn.shortLabel,
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false,
                                color = TitaniumText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
