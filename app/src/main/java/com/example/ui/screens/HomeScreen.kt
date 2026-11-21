package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UserSettings
import com.example.protocol.FavoriteHostEntry
import com.example.protocol.RecentHostEntry
import com.example.protocol.TransportType
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.components.LiquidGlassDialog
import com.example.ui.components.LiquidGlassPanel
import com.example.ui.components.LiquidGlassTextField
import com.example.ui.components.TapToRevealIpChip
import com.example.ui.theme.BrushedSilver
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.GlassFillSubtle
import com.example.ui.theme.GlassRimMid
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PewterMuted
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TitaniumText
import com.example.ui.viewmodel.EnvironmentStatus

@Composable
fun HomeScreen(
    settings: UserSettings,
    environmentStatus: EnvironmentStatus,
    hostActive: Boolean,
    pairingCode: String,
    favoriteHosts: List<FavoriteHostEntry>,
    onOpenControllerDiscovery: () -> Unit,
    onOpenHostMode: () -> Unit,
    onOpenPcCompanion: () -> Unit,
    onOpenControllerCustomizer: () -> Unit = {},
    onUpdateDeviceName: (String) -> Unit,
    onConnectFavorite: (FavoriteHostEntry) -> Unit,
    onRemoveFavorite: (FavoriteHostEntry) -> Unit,
    onQuickReconnectRecent: (RecentHostEntry) -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }

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
                    .widthIn(max = 920.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                val isWide = maxWidth >= 600.dp

                Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LiquidGlassPanel(
                                shape = RoundedCornerShape(12.dp),
                                elevated = true
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SportsEsports,
                                    contentDescription = null,
                                    tint = BrushedSilver,
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .size(20.dp)
                                )
                            }
                            Text(
                                text = "LinkPad",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TitaniumText
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GlassStatusIcon(
                                icon = Icons.Default.Wifi,
                                active = environmentStatus.wifiConnected,
                                activeColor = SignalEmerald,
                                contentDesc = "Network"
                            )
                            GlassStatusIcon(
                                icon = Icons.Default.Bluetooth,
                                active = environmentStatus.bluetoothEnabled,
                                activeColor = CobaltBlue,
                                contentDesc = "Bluetooth"
                            )
                            var renameFocused by remember { mutableStateOf(false) }
                            LiquidGlassPanel(
                                shape = RoundedCornerShape(14.dp),
                                subtle = true,
                                accentBorderTint = if (renameFocused) BrushedSilver else null,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .onFocusChanged { renameFocused = it.isFocused }
                                    .focusable()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { showRenameDialog = true }
                                    .testTag("edit_device_name_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = settings.deviceName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TitaniumText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Rename device",
                                        tint = PewterMuted,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Dual Role Glass Cards
                    if (isWide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            RoleGlassTile(
                                title = "Controller",
                                tag = "WI-FI · BT HID",
                                icon = Icons.Default.SportsEsports,
                                accent = CobaltBlue,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("role_controller_card"),
                                onClick = onOpenControllerDiscovery
                            )
                            RoleGlassTile(
                                title = if (hostActive) "Host · $pairingCode" else "Host",
                                tag = "4 PLAYERS",
                                icon = Icons.Default.Tv,
                                accent = if (hostActive) SignalEmerald else BrushedSilver,
                                activeGlow = hostActive,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("role_host_card"),
                                onClick = onOpenHostMode
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            RoleGlassTile(
                                title = "Controller",
                                tag = "WI-FI · BT HID",
                                icon = Icons.Default.SportsEsports,
                                accent = CobaltBlue,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("role_controller_card"),
                                onClick = onOpenControllerDiscovery
                            )
                            RoleGlassTile(
                                title = if (hostActive) "Host · $pairingCode" else "Host",
                                tag = "4 PLAYERS",
                                icon = Icons.Default.Tv,
                                accent = if (hostActive) SignalEmerald else BrushedSilver,
                                activeGlow = hostActive,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("role_host_card"),
                                onClick = onOpenHostMode
                            )
                        }
                    }

                    // Compact PC Host Transfer Strip
                    var pcFocused by remember { mutableStateOf(false) }
                    LiquidGlassPanel(
                        shape = RoundedCornerShape(18.dp),
                        subtle = true,
                        accentBorderTint = if (pcFocused) BrushedSilver else null,
                        borderWidth = if (pcFocused) 1.5.dp else 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .onFocusChanged { pcFocused = it.isFocused }
                            .focusable()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(onClick = onOpenPcCompanion)
                            .testTag("open_pc_companion_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Computer,
                                    contentDescription = "PC Host",
                                    tint = BrushedSilver,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "PC Host",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TitaniumText
                                    )
                                    Text(
                                        text = "WINDOWS · MAC · LINUX",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 10.sp,
                                        color = PewterMuted
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Open PC Host",
                                tint = BrushedSilver,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Offline Controller Customizer Strip (Themes, Layouts, Gyro, Drag Positions)
                    var customFocused by remember { mutableStateOf(false) }
                    LiquidGlassPanel(
                        shape = RoundedCornerShape(18.dp),
                        subtle = true,
                        accentBorderTint = if (customFocused) BrushedSilver else CobaltBlue.copy(alpha = 0.36f),
                        borderWidth = if (customFocused) 1.5.dp else 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .onFocusChanged { customFocused = it.isFocused }
                            .focusable()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(onClick = onOpenControllerCustomizer)
                            .testTag("open_customizer_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Customize Controller",
                                    tint = CobaltBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "Customize Controller",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TitaniumText
                                    )
                                    Text(
                                        text = "${settings.controllerLayout.label.uppercase()} · ${settings.controllerTheme.label.uppercase()} · OFFLINE",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 10.sp,
                                        color = PewterMuted
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Open Controller Customizer",
                                tint = CobaltBlue,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Favorite Hosts (1-tap reconnect without code)
                    if (favoriteHosts.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "FAVORITES · NO CODE",
                                style = MaterialTheme.typography.labelMedium,
                                color = BrushedSilver
                            )
                            favoriteHosts.forEach { fav ->
                                var favFocused by remember { mutableStateOf(false) }
                                LiquidGlassPanel(
                                    shape = RoundedCornerShape(16.dp),
                                    elevated = true,
                                    accentBorderTint = if (favFocused) BrushedSilver else CobaltBlue.copy(alpha = 0.45f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .onFocusChanged { favFocused = it.isFocused }
                                        .focusable()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { onConnectFavorite(fav) }
                                        .testTag("home_favorite_host_item")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = null,
                                                tint = CobaltBlue,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = GlassFillSubtle,
                                                border = BorderStroke(1.dp, GlassRimMid)
                                            ) {
                                                Text(
                                                    text = fav.transport.badge,
                                                    fontFamily = JetBrainsMonoFamily,
                                                    fontSize = 10.sp,
                                                    color = BrushedSilver,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                )
                                            }
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = fav.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = TitaniumText,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (fav.transport == TransportType.WIFI_UDP) {
                                                    TapToRevealIpChip(
                                                        address = fav.address,
                                                        port = fav.port,
                                                        compact = true,
                                                        testTag = "home_fav_ip_reveal"
                                                    )
                                                } else {
                                                    Text(
                                                        text = "BT · ${fav.address}",
                                                        fontFamily = JetBrainsMonoFamily,
                                                        fontSize = 11.sp,
                                                        color = PewterMuted,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { onRemoveFavorite(fav) },
                                                modifier = Modifier.testTag("home_remove_favorite_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Remove favorite ${fav.name}",
                                                    tint = PewterMuted,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = "Connect to ${fav.name}",
                                                tint = CobaltBlue,
                                                modifier = Modifier
                                                    .padding(end = 8.dp)
                                                    .size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Recent Hosts
                    if (settings.recentHosts.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "RECENT",
                                style = MaterialTheme.typography.labelMedium,
                                color = PewterMuted
                            )
                            settings.recentHosts.forEach { recent ->
                                var itemFocused by remember { mutableStateOf(false) }
                                LiquidGlassPanel(
                                    shape = RoundedCornerShape(16.dp),
                                    subtle = true,
                                    accentBorderTint = if (itemFocused) BrushedSilver else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .onFocusChanged { itemFocused = it.isFocused }
                                        .focusable()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { onQuickReconnectRecent(recent) }
                                        .testTag("recent_host_item")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = GlassFillSubtle,
                                                border = BorderStroke(1.dp, GlassRimMid)
                                            ) {
                                                Text(
                                                    text = recent.transport.badge,
                                                    fontFamily = JetBrainsMonoFamily,
                                                    fontSize = 10.sp,
                                                    color = BrushedSilver,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                )
                                            }
                                            Text(
                                                text = recent.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = TitaniumText
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (recent.transport == TransportType.WIFI_UDP) {
                                                TapToRevealIpChip(
                                                    address = recent.address,
                                                    compact = true,
                                                    testTag = "home_recent_ip_reveal"
                                                )
                                            } else {
                                                Text(
                                                    text = recent.address,
                                                    fontFamily = JetBrainsMonoFamily,
                                                    fontSize = 11.sp,
                                                    color = PewterMuted
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = "Connect to ${recent.name}",
                                                tint = BrushedSilver,
                                                modifier = Modifier.size(16.dp)
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
    }

    if (showRenameDialog) {
        var draftName by remember(settings.deviceName) { mutableStateOf(settings.deviceName) }
        LiquidGlassDialog(
            onDismissRequest = { showRenameDialog = false },
            accentColor = CobaltBlue,
            maxWidthDp = 400
        ) {
            Text(
                text = "Device Name",
                style = MaterialTheme.typography.titleLarge,
                color = TitaniumText
            )
            Text(
                text = "Visible to LAN hosts and Bluetooth HID receivers.",
                style = MaterialTheme.typography.bodySmall,
                color = PewterMuted
            )
            LiquidGlassTextField(
                value = draftName,
                onValueChange = { if (it.length <= 20) draftName = it },
                label = "DEVICE ALIAS",
                placeholder = "LinkPad Controller",
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rename_device_input")
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showRenameDialog = false },
                    border = BorderStroke(1.dp, GlassRimMid),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                ) {
                    Text("Cancel", color = PewterMuted)
                }
                Button(
                    onClick = {
                        onUpdateDeviceName(draftName.ifBlank { settings.deviceName })
                        showRenameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CobaltBlue,
                        contentColor = ObsidianBg
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("save_device_name_button")
                ) {
                    Text("Save", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun GlassStatusIcon(
    icon: ImageVector,
    active: Boolean,
    activeColor: Color,
    contentDesc: String
) {
    LiquidGlassPanel(
        shape = RoundedCornerShape(12.dp),
        subtle = true,
        accentBorderTint = if (active) activeColor.copy(alpha = 0.6f) else null
    ) {
        Box(
            modifier = Modifier.padding(9.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDesc,
                tint = if (active) activeColor else PewterMuted.copy(alpha = 0.55f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun RoleGlassTile(
    title: String,
    tag: String,
    icon: ImageVector,
    accent: Color,
    activeGlow: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    LiquidGlassPanel(
        shape = RoundedCornerShape(26.dp),
        elevated = focused || activeGlow,
        accentBorderTint = when {
            focused -> BrushedSilver
            activeGlow -> SignalEmerald
            else -> null
        },
        borderWidth = if (focused) 1.5.dp else 1.dp,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LiquidGlassPanel(
                    shape = RoundedCornerShape(16.dp),
                    subtle = true,
                    accentBorderTint = accent.copy(alpha = 0.5f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accent,
                        modifier = Modifier
                            .padding(14.dp)
                            .size(26.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = TitaniumText
                    )
                    Text(
                        text = tag,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = PewterMuted
                    )
                }
            }

            LiquidGlassPanel(
                shape = CircleShape,
                elevated = true
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = title,
                    tint = BrushedSilver,
                    modifier = Modifier
                        .padding(11.dp)
                        .size(18.dp)
                )
            }
        }
    }
}
