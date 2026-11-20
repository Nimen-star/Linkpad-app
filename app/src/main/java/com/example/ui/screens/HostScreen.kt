package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.protocol.KeymapProfile
import com.example.protocol.PadButton
import com.example.protocol.PlayerSlotState
import com.example.transport.LinkPadConstants
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.components.LiquidGlassPanel
import com.example.ui.components.TapToRevealIpChip
import com.example.ui.theme.BrushedSilver
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.GlassFillSubtle
import com.example.ui.theme.GlassRimMid
import com.example.ui.theme.GraphiteElevated
import com.example.ui.theme.HairlineSubtle
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PewterMuted
import com.example.ui.theme.PrecisionCrimson
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TitaniumText
import com.example.ui.theme.playerAccentColor
import com.example.ui.viewmodel.EnvironmentStatus

@Composable
fun HostScreen(
    hostActive: Boolean,
    pairingCode: String,
    localIp: String,
    bluetoothHostingActive: Boolean,
    slots: List<PlayerSlotState>,
    environmentStatus: EnvironmentStatus,
    allowFavoritesWithoutCode: Boolean,
    trustedDeviceCount: Int,
    onBack: () -> Unit,
    onStartHost: () -> Unit,
    onStopHost: () -> Unit,
    onRegenerateCode: () -> Unit,
    onToggleBluetoothHost: (Boolean) -> Unit,
    onUpdateSlotKeymap: (Int, KeymapProfile) -> Unit,
    onKickPlayer: (Int) -> Unit,
    onToggleAllowFavoritesWithoutCode: (Boolean) -> Unit,
    onClearTrustedDevices: () -> Unit,
    onOpenControllerDiscovery: () -> Unit,
    onRefreshSystemStatus: () -> Unit
) {
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        onRefreshSystemStatus()
    }

    LaunchedEffect(Unit) {
        if (!hostActive) {
            onStartHost()
        }
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        onRefreshSystemStatus()
        if (results.values.any { it }) {
            onToggleBluetoothHost(true)
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
                            // Pairing Code Panel
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
                                            text = "CODE",
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
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Bluetooth,
                                                contentDescription = "Bluetooth",
                                                tint = if (bluetoothHostingActive) CobaltBlue else PewterMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "BT",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = TitaniumText
                                            )
                                            Switch(
                                                checked = bluetoothHostingActive,
                                                onCheckedChange = { wantEnable ->
                                                    if (wantEnable && !environmentStatus.hasBluetoothPermission) {
                                                        btPermissionLauncher.launch(
                                                            LinkPadConstants.requiredBluetoothPermissions()
                                                        )
                                                    } else {
                                                        onToggleBluetoothHost(wantEnable)
                                                    }
                                                },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = TitaniumText,
                                                    checkedTrackColor = CobaltBlue.copy(alpha = 0.65f)
                                                ),
                                                modifier = Modifier.testTag("host_bluetooth_switch")
                                            )
                                        }
                                    }
                                }
                            }

                            // Players P1-P4 (compact live indicators right inside slots; monitor preview removed)
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
                            LiquidGlassPanel(
                                shape = RoundedCornerShape(22.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "HOST SETTINGS & TV CONTROL",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = PewterMuted
                                    )

                                    // Favorites No-Code Pairing
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

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(HairlineSubtle)
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Gamepad,
                                            contentDescription = null,
                                            tint = CobaltBlue,
                                            modifier = Modifier
                                                .padding(top = 2.dp)
                                                .size(18.dp)
                                        )
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "Native Android TV & Phone Control",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = TitaniumText
                                            )
                                            Text(
                                                text = "For universal hardware gamepad input on Android TV, phones, or Chrome Hardware Tester (no Gboard switch or debugging), open Controller Mode and pair via Bluetooth OS Gamepad. Multiple phones can connect to the same TV or PC.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = PewterMuted
                                            )
                                            OutlinedButton(
                                                onClick = onOpenControllerDiscovery,
                                                border = BorderStroke(1.dp, CobaltBlue.copy(alpha = 0.5f)),
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("open_bt_gamepad_discovery_button")
                                            ) {
                                                Text(
                                                    text = "Open Controller Mode",
                                                    color = CobaltBlue,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
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
