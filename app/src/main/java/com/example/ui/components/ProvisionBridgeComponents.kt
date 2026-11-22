package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.protocol.DiscoveredHost
import com.example.uhid.ProvisionStage
import com.example.uhid.ProvisionUiState
import com.example.uhid.UsbAdbDeviceInfo
import com.example.uhid.WirelessAdbTarget
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.GlassFillSubtle
import com.example.ui.theme.GlassRimMid
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PewterMuted
import com.example.ui.theme.PrecisionCrimson
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TitaniumText
import com.example.ui.theme.WarmAmber
import kotlin.math.roundToInt

@Composable
fun WirelessAdbTargetsCard(
    targets: List<WirelessAdbTarget>,
    discoveredUdpHosts: List<DiscoveredHost>,
    scanning: Boolean,
    busy: Boolean,
    onRescan: () -> Unit,
    onOneClickSetupAndPlay: (WirelessAdbTarget, String) -> Unit,
    onOpenWirelessPinPair: (WirelessAdbTarget?) -> Unit
) {
    val accent = if (targets.isNotEmpty()) SignalEmerald else CobaltBlue

    LiquidGlassPanel(
        shape = RoundedCornerShape(22.dp),
        elevated = targets.isNotEmpty(),
        subtle = targets.isEmpty(),
        accentBorderTint = accent.copy(alpha = if (targets.isNotEmpty()) 0.72f else 0.42f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wireless_adb_targets_card")
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
                        imageVector = Icons.Default.Tv,
                        contentDescription = "Wireless TV & Phone 1-Click Setup",
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "WIRELESS TV & PHONE · 1-CLICK",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accent
                        )
                        Text(
                            text = if (targets.isNotEmpty()) {
                                "Tap once to install, start UHID hardware, and auto-connect."
                            } else {
                                "Turn on ADB Debugging on Fire TV / TV (same Wi-Fi) for 1-click setup."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = PewterMuted
                        )
                    }
                }

                if (scanning) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(8.dp)
                            .size(16.dp),
                        color = accent,
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(
                        onClick = onRescan,
                        modifier = Modifier.testTag("rescan_wireless_adb_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Scan Wi-Fi devices",
                            tint = PewterMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            targets.forEach { target ->
                val matchedHost = discoveredUdpHosts.firstOrNull { it.hostAddress == target.ip }
                val friendlyName = matchedHost?.name ?: target.label

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = GlassFillSubtle,
                    border = BorderStroke(1.dp, if (target.canOneClickSetup) SignalEmerald.copy(alpha = 0.55f) else GlassRimMid),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = friendlyName,
                                style = MaterialTheme.typography.titleMedium,
                                color = TitaniumText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TapToRevealIpChip(
                                    address = target.ip,
                                    port = if (target.canOneClickSetup) target.connectPort else target.pairPort,
                                    compact = true,
                                    testTag = "wireless_target_ip_chip"
                                )
                                Text(
                                    text = when {
                                        target.isTcp5555Open -> "ADB READY"
                                        target.hasTlsConnect -> "TLS READY"
                                        else -> "PIN PAIR"
                                    },
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (target.canOneClickSetup) SignalEmerald else WarmAmber
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        if (target.canOneClickSetup) {
                            Button(
                                enabled = !busy,
                                onClick = { onOneClickSetupAndPlay(target, friendlyName) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SignalEmerald,
                                    contentColor = ObsidianBg
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("wireless_setup_play_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "1-Click Play",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        } else {
                            OutlinedButton(
                                enabled = !busy,
                                onClick = { onOpenWirelessPinPair(target) },
                                border = BorderStroke(1.dp, WarmAmber.copy(alpha = 0.65f)),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("wireless_pin_pair_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Pin,
                                    contentDescription = null,
                                    tint = WarmAmber,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Pair PIN",
                                    color = WarmAmber,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                enabled = !busy,
                onClick = { onOpenWirelessPinPair(null) },
                border = BorderStroke(1.dp, GlassRimMid),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("open_wireless_pair_pin_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Pin,
                    contentDescription = null,
                    tint = CobaltBlue,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Pair Phone / Android 11+ TV Wirelessly (6-Digit PIN)",
                    color = CobaltBlue,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun RemoteWirelessPairModal(
    prefillTarget: WirelessAdbTarget?,
    onDismiss: () -> Unit,
    onPairAndPlay: (ip: String, pairPort: Int, pin: String, connectPort: Int?, name: String?) -> Unit
) {
    var ipText by remember(prefillTarget?.ip) { mutableStateOf(prefillTarget?.ip.orEmpty()) }
    var portText by remember(prefillTarget?.pairPort) {
        mutableStateOf(prefillTarget?.pairPort?.toString().orEmpty())
    }
    var pinText by remember { mutableStateOf("") }

    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        accentColor = CobaltBlue,
        maxWidthDp = 430
    ) {
        Text(
            text = "Wireless PIN Setup & Play",
            style = MaterialTheme.typography.titleLarge,
            color = TitaniumText
        )
        Text(
            text = "On the target phone or TV, open Developer Options → Wireless debugging → Pair device with pairing code, then enter the details below.",
            style = MaterialTheme.typography.bodySmall,
            color = PewterMuted
        )
        LiquidGlassTextField(
            value = ipText,
            onValueChange = { ipText = it.trim() },
            label = "TARGET WI-FI IP",
            placeholder = "192.168.1.42",
            modifier = Modifier
                .fillMaxWidth()
                .testTag("remote_pair_ip_input")
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LiquidGlassTextField(
                value = portText,
                onValueChange = { input -> portText = input.filter { it.isDigit() }.take(5) },
                label = "PAIR PORT",
                placeholder = "41235",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .weight(1f)
                    .testTag("remote_pair_port_input")
            )
            LiquidGlassTextField(
                value = pinText,
                onValueChange = { input -> pinText = input.filter { it.isDigit() }.take(6) },
                label = "6-DIGIT PIN",
                placeholder = "••••••",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = 3.sp,
                    color = TitaniumText
                ),
                modifier = Modifier
                    .weight(1.2f)
                    .testTag("remote_pair_pin_input")
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, GlassRimMid),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
            ) {
                Text("Cancel", color = PewterMuted)
            }
            val parsedPort = portText.toIntOrNull()
            Button(
                enabled = ipText.isNotBlank() && parsedPort != null && parsedPort in 1..65535 && pinText.length == 6,
                onClick = {
                    onDismiss()
                    onPairAndPlay(
                        ipText,
                        parsedPort!!,
                        pinText,
                        prefillTarget?.connectPort?.takeIf { prefillTarget.hasTlsConnect },
                        prefillTarget?.label
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SignalEmerald,
                    contentColor = ObsidianBg
                ),
                modifier = Modifier
                    .weight(1.3f)
                    .minimumInteractiveComponentSize()
                    .testTag("submit_remote_wireless_pair_button")
            ) {
                Text("Pair & Play", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun UsbOtgTargetCard(
    devices: List<UsbAdbDeviceInfo>,
    onSetupDevice: (UsbAdbDeviceInfo) -> Unit
) {
    if (devices.isEmpty()) return

    LiquidGlassPanel(
        shape = RoundedCornerShape(20.dp),
        elevated = true,
        accentBorderTint = SignalEmerald.copy(alpha = 0.78f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("usb_otg_target_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Usb,
                    contentDescription = "USB OTG Device Detected",
                    tint = SignalEmerald,
                    modifier = Modifier.size(20.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "USB TARGET DETECTED · 1-CLICK BRIDGE",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SignalEmerald
                    )
                    Text(
                        text = "Installs LinkPad, unlocks Wi-Fi ADB (:5555), starts UHID, and auto-connects.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PewterMuted
                    )
                }
            }

            devices.forEach { dev ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = GlassFillSubtle,
                    border = BorderStroke(1.dp, GlassRimMid),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = dev.productName,
                                style = MaterialTheme.typography.titleMedium,
                                color = TitaniumText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (dev.hasPermission) {
                                    "USB authorized · ready to setup & play"
                                } else {
                                    "Tap to grant USB permission & run setup"
                                },
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp,
                                color = if (dev.hasPermission) SignalEmerald else WarmAmber
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onSetupDevice(dev) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SignalEmerald,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("usb_setup_device_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (dev.hasPermission) "1-Click Setup" else "Authorize",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProvisionProgressDialog(
    state: ProvisionUiState,
    onUseTargetIp: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!state.active || state.stage == ProvisionStage.IDLE) return

    val isDone = state.stage == ProvisionStage.DONE
    val isError = state.stage == ProvisionStage.ERROR
    val accent = when {
        isDone -> SignalEmerald
        isError -> PrecisionCrimson
        state.stage == ProvisionStage.WAIT_TRUST -> WarmAmber
        else -> CobaltBlue
    }

    LiquidGlassDialog(
        onDismissRequest = { if (isDone || isError) onDismiss() },
        accentColor = accent,
        maxWidthDp = 440
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = state.transportLabel.ifBlank { "1-CLICK TV & PHONE BRIDGE" },
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = accent
                )
                Text(
                    text = state.targetLabel.ifBlank { "Target Device" },
                    style = MaterialTheme.typography.titleLarge,
                    color = TitaniumText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            when {
                isDone && !state.autoConnectReady -> Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Setup Complete",
                    tint = SignalEmerald,
                    modifier = Modifier.size(26.dp)
                )
                isError -> Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "Setup Error",
                    tint = PrecisionCrimson,
                    modifier = Modifier.size(26.dp)
                )
                else -> CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = accent,
                    strokeWidth = 2.5.dp
                )
            }
        }

        val steps = listOf(
            1 to "Connect & Trust Debugging",
            2 to "Install / Update LinkPad APK",
            3 to "Unlock Wi-Fi ADB (:5555)",
            4 to "Launch Host, UHID & Gamepad"
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            steps.forEach { (stepNum, stepTitle) ->
                val completed = (isDone && !state.autoConnectReady) || (state.stage.stepIndex > stepNum)
                val current = !isError && ((isDone && state.autoConnectReady && stepNum == 4) || (!isDone && state.stage.stepIndex == stepNum))
                val stepColor = when {
                    completed -> SignalEmerald
                    current -> CobaltBlue
                    else -> PewterMuted.copy(alpha = 0.55f)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (completed) "✓" else if (current) "▶" else "$stepNum.",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = stepColor
                    )
                    Text(
                        text = stepTitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (current || completed) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (completed || current) TitaniumText else PewterMuted
                    )
                }
            }
        }

        AnimatedVisibility(visible = state.stage == ProvisionStage.INSTALLING_APK) {
            val pct = (state.apkProgress * 100f).roundToInt().coerceIn(0, 100)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { state.apkProgress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = CobaltBlue,
                    trackColor = GlassFillSubtle
                )
                Text(
                    text = "Uploading LinkPad APK · $pct%",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = CobaltBlue
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = GlassFillSubtle,
            border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = state.statusText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) PrecisionCrimson else TitaniumText,
                modifier = Modifier.padding(12.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isDone || isError) {
                OutlinedButton(
                    onClick = onDismiss,
                    border = BorderStroke(1.dp, GlassRimMid),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("dismiss_provision_dialog_button")
                ) {
                    Text("Close", color = PewterMuted)
                }
            }

            if (isDone && !state.targetIp.isNullOrBlank()) {
                Button(
                    onClick = {
                        val ip = state.targetIp
                        onDismiss()
                        onUseTargetIp(ip)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SignalEmerald,
                        contentColor = ObsidianBg
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("use_provisioned_target_button")
                ) {
                    Text("Use ${state.targetIp}", fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}
