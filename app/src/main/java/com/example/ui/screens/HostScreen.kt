package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
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

@Composable
fun HostScreen(
    hostActive: Boolean,
    pairingCode: String,
    localIp: String,
    slots: List<PlayerSlotState>,
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
    onRefreshSystemStatus: () -> Unit,
    onRefreshHardwareBridge: () -> Unit,
    onActivateHardwareGamepads: () -> Unit,
    onPairWirelessDebugging: (String, String) -> Unit
) {
    val context = LocalContext.current
    var showWirelessPairModal by remember { mutableStateOf(false) }

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
                            // OS Hardware Gamepad Bridge Card (Wi-Fi UDP -> 4 Kernel UHID Pads, Zero Shizuku, Zero Root)
                            HardwareGamepadsCard(
                                bridgeState = hardwareBridgeState,
                                onRefresh = onRefreshHardwareBridge,
                                onActivate = onActivateHardwareGamepads,
                                onOpenPairDialog = { showWirelessPairModal = true },
                                onOpenImeSettings = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }
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
}

@Composable
private fun HardwareGamepadsCard(
    bridgeState: UhidBridgeState,
    onRefresh: () -> Unit,
    onActivate: () -> Unit,
    onOpenPairDialog: () -> Unit,
    onOpenImeSettings: () -> Unit
) {
    val isActive = bridgeState is UhidBridgeState.Active
    val isBusy = bridgeState is UhidBridgeState.Checking ||
        bridgeState is UhidBridgeState.StartingAgent ||
        bridgeState is UhidBridgeState.WaitingTrustPrompt
    val isError = bridgeState is UhidBridgeState.Error

    val borderAccent = when {
        isActive -> SignalEmerald
        isError -> PrecisionCrimson
        isBusy -> CobaltBlue
        else -> WarmAmber.copy(alpha = 0.65f)
    }

    LiquidGlassPanel(
        shape = RoundedCornerShape(22.dp),
        elevated = isActive,
        accentBorderTint = borderAccent,
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
                        imageVector = Icons.Default.Gamepad,
                        contentDescription = "Hardware Gamepad Engine",
                        tint = borderAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "OS Hardware Gamepads",
                            style = MaterialTheme.typography.titleMedium,
                            color = TitaniumText
                        )
                        Text(
                            text = when (bridgeState) {
                                is UhidBridgeState.Idle -> "WI-FI UDP → 4 KERNEL PADS"
                                is UhidBridgeState.Checking -> "PROBING DEVICE BRIDGE..."
                                is UhidBridgeState.SetupRequired ->
                                    if (bridgeState.tcp5555Open) "LOCAL ADB READY · TAP ENABLE"
                                    else "ENABLE ADB DEBUGGING FOR HARDWARE PADS"
                                is UhidBridgeState.WaitingTrustPrompt -> "TAP 'ALLOW' ON SYSTEM PROMPT"
                                is UhidBridgeState.StartingAgent -> "LAUNCHING UHID HARDWARE ENGINE..."
                                is UhidBridgeState.Active -> "ACTIVE · ${bridgeState.backend.label.uppercase()}"
                                is UhidBridgeState.Error -> "NEEDS ATTENTION"
                            },
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            color = borderAccent
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
                        text = "Every connected Wi-Fi phone automatically creates a physical OS Gamepad (/dev/uhid) on this TV or phone. No Bluetooth, root, or extra apps required.",
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
                                "Check 'Always allow from this computer' and tap Allow."
                            } else {
                                "Connecting built-in hardware bridge..."
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
                        color = TitaniumText
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GlassFillSubtle,
                        border = BorderStroke(1.dp, GlassRimMid),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Fire TV / Redmi TV: Settings → My Fire TV / Device Preferences → Developer Options → Turn ON ADB Debugging (one time).",
                            style = MaterialTheme.typography.bodySmall,
                            color = PewterMuted,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                is UhidBridgeState.Error -> {
                    Text(
                        text = bridgeState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = PrecisionCrimson
                    )
                }

                is UhidBridgeState.Idle -> {
                    Text(
                        text = "Registers up to 4 real Wi-Fi UDP gamepads for any Android game, emulator, or TV app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PewterMuted
                    )
                }
            }

            if (!isActive && !isBusy) {
                Button(
                    onClick = onActivate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (bridgeState is UhidBridgeState.SetupRequired && bridgeState.tcp5555Open) SignalEmerald else BrushedSilver,
                        contentColor = ObsidianBg
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("enable_hardware_gamepads_button")
                ) {
                    Text(
                        text = if (isError) "Retry Enable Hardware Gamepads" else "Enable Hardware Gamepads",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    OutlinedButton(
                        onClick = onOpenPairDialog,
                        border = BorderStroke(1.dp, CobaltBlue.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("pair_wireless_debugging_button")
                    ) {
                        Text(
                            text = "Pair Wireless Debugging (Android 11+)",
                            color = CobaltBlue,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(HairlineSubtle)
            )

            OutlinedButton(
                onClick = onOpenImeSettings,
                border = BorderStroke(1.dp, GlassRimMid),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("open_ime_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = null,
                    tint = BrushedSilver,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Zero-ADB Fallback · LinkPad Virtual Keyboard",
                    color = BrushedSilver,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
