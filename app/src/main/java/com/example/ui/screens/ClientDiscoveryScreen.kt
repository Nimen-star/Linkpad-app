package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.protocol.DiscoveredHost
import com.example.protocol.FavoriteHostEntry
import com.example.transport.ClientConnectionState
import com.example.transport.LinkPadConstants
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.components.LiquidGlassCheckRow
import com.example.ui.components.LiquidGlassDialog
import com.example.ui.components.LiquidGlassPanel
import com.example.ui.components.LiquidGlassTextField
import com.example.ui.components.TapToRevealIpChip
import com.example.ui.theme.BrushedSilver
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.GlassFillSubtle
import com.example.ui.theme.GlassRimMid
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PewterMuted
import com.example.ui.theme.PrecisionCrimson
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TitaniumText
import com.example.ui.theme.WarmAmber

@Composable
fun ClientDiscoveryScreen(
    discoveredHosts: List<DiscoveredHost>,
    favoriteHosts: List<FavoriteHostEntry>,
    connectionState: ClientConnectionState,
    prefillIp: String = "",
    prefillPort: String = LinkPadConstants.HOST_UDP_PORT.toString(),
    onStartDiscovery: () -> Unit,
    onStopDiscovery: () -> Unit,
    onOpenControllerCustomizer: () -> Unit = {},
    onClearError: () -> Unit,
    onConnectHost: (DiscoveredHost, String, Boolean) -> Unit,
    onConnectFavorite: (FavoriteHostEntry) -> Unit,
    onToggleFavorite: (DiscoveredHost) -> Unit,
    onRemoveFavorite: (FavoriteHostEntry) -> Unit,
    onSaveManualFavorite: (String, String, String) -> Unit,
    onConnectManualIp: (String, String, String, Boolean) -> Unit,
    onBack: () -> Unit
) {
    var pairingCode by rememberSaveable { mutableStateOf("") }
    var showManualIp by rememberSaveable { mutableStateOf(prefillIp.isNotBlank()) }
    var manualIp by rememberSaveable { mutableStateOf(prefillIp) }
    var manualPort by rememberSaveable { mutableStateOf(prefillPort) }
    var manualFavName by rememberSaveable { mutableStateOf("") }
    var pendingHostForPin by remember { mutableStateOf<DiscoveredHost?>(null) }

    DisposableEffect(Unit) {
        onStartDiscovery()
        onDispose {
            onStopDiscovery()
        }
    }

    LaunchedEffect(connectionState) {
        if (connectionState is ClientConnectionState.NeedsPairingCode) {
            pendingHostForPin = connectionState.host
        }
    }

    val isConnecting = connectionState is ClientConnectionState.Connecting

    LiquidGlassBackdrop {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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
                            modifier = Modifier.testTag("discovery_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TitaniumText
                            )
                        }
                        Text(
                            text = "Connect Controller",
                            style = MaterialTheme.typography.titleLarge,
                            color = TitaniumText
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenControllerCustomizer,
                            border = BorderStroke(1.dp, CobaltBlue.copy(alpha = 0.55f)),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("discovery_customize_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Customize Controller",
                                tint = CobaltBlue,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Customize", color = CobaltBlue, fontSize = 11.sp, maxLines = 1)
                        }

                        IconButton(
                            onClick = onStartDiscovery,
                            modifier = Modifier.testTag("rescan_hosts_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Rescan Wi-Fi hosts",
                                tint = BrushedSilver
                            )
                        }
                    }
                }

                // Segmented Glass Pairing Code Input (for LinkPad Wi-Fi UDP Host on PC, TV, or Phone)
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
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "HOST PAIRING CODE",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = PewterMuted
                                )
                                Text(
                                    text = "Enter the 6-digit code shown on your PC, TV, or Host phone",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PewterMuted
                                )
                            }
                            if (pairingCode.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = {
                                            pairingCode = pairingCode.dropLast(1)
                                            onClearError()
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("backspace_code_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                                            contentDescription = "Delete last digit",
                                            tint = BrushedSilver,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            pairingCode = ""
                                            onClearError()
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("clear_code_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear pairing code",
                                            tint = PewterMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        val fieldValue = TextFieldValue(
                            text = pairingCode,
                            selection = TextRange(pairingCode.length)
                        )

                        BasicTextField(
                            value = fieldValue,
                            onValueChange = { incoming ->
                                val digits = incoming.text.filter { it.isDigit() }.take(6)
                                val changed = digits != pairingCode
                                pairingCode = digits
                                if (changed) {
                                    onClearError()
                                }
                                if (digits.length == 6 && changed && discoveredHosts.size == 1 && !isConnecting) {
                                    onConnectHost(discoveredHosts.first(), digits, false)
                                }
                            },
                            singleLine = true,
                            cursorBrush = SolidColor(Color.Transparent),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = TextStyle(color = Color.Transparent, fontSize = 18.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pairing_code_input"),
                            decorationBox = { innerTextField ->
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        for (i in 0 until 6) {
                                            if (i == 3) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }
                                            val ch = pairingCode.getOrNull(i)?.toString() ?: ""
                                            LiquidGlassPanel(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(54.dp),
                                                shape = RoundedCornerShape(12.dp),
                                                subtle = true,
                                                accentBorderTint = if (ch.isNotEmpty()) CobaltBlue else null
                                            ) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = if (ch.isNotEmpty()) ch else "•",
                                                        fontFamily = JetBrainsMonoFamily,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 22.sp,
                                                        color = if (ch.isNotEmpty()) TitaniumText else PewterMuted.copy(alpha = 0.35f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .alpha(0.01f),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        innerTextField()
                                    }
                                }
                            }
                        )
                    }
                }

                // Status / Error Banner
                if (connectionState is ClientConnectionState.Error) {
                    LiquidGlassPanel(
                        shape = RoundedCornerShape(16.dp),
                        accentBorderTint = PrecisionCrimson,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = connectionState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TitaniumText,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = onClearError,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss error",
                                    tint = PewterMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                } else if (connectionState is ClientConnectionState.Connecting) {
                    LiquidGlassPanel(
                        shape = RoundedCornerShape(16.dp),
                        accentBorderTint = CobaltBlue,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = CobaltBlue,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = connectionState.hostName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TitaniumText
                            )
                        }
                    }
                }

                // Permanent Favorites Section
                if (favoriteHosts.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "FAVORITES · NO CODE",
                            style = MaterialTheme.typography.labelMedium,
                            color = WarmAmber
                        )
                        favoriteHosts.forEach { fav ->
                            FavoriteHostCard(
                                favorite = fav,
                                enabled = !isConnecting,
                                onConnect = { onConnectFavorite(fav) },
                                onRemove = { onRemoveFavorite(fav) }
                            )
                        }
                    }
                }

                // Discovered Wi-Fi Hosts List
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "WI-FI UDP HOSTS · ${discoveredHosts.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = PewterMuted
                    )

                    if (discoveredHosts.isEmpty()) {
                        LiquidGlassPanel(
                            shape = RoundedCornerShape(18.dp),
                            subtle = true,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = SignalEmerald,
                                    strokeWidth = 2.dp
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "Searching Wi-Fi network for LinkPad Hosts...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TitaniumText
                                    )
                                    Text(
                                        text = "Open LinkPad Host on your TV, phone, or LinkPadHost.exe on PC.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PewterMuted
                                    )
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            discoveredHosts.forEach { host ->
                                val isFav = favoriteHosts.any {
                                    it.address == host.hostAddress && it.port == host.port
                                }
                                DiscoveredHostCard(
                                    host = host,
                                    isFavorite = isFav,
                                    enabled = !isConnecting,
                                    onToggleFavorite = { onToggleFavorite(host) },
                                    onClick = {
                                        if (isFav && pairingCode.length < 6) {
                                            onConnectFavorite(
                                                FavoriteHostEntry(
                                                    name = host.name,
                                                    address = host.hostAddress,
                                                    port = host.port,
                                                    transport = host.transport
                                                )
                                            )
                                        } else if (pairingCode.length == 6) {
                                            onConnectHost(host, pairingCode, isFav)
                                        } else {
                                            pendingHostForPin = host
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Direct IP + Add to Favorites
                LiquidGlassPanel(
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clickable { showManualIp = !showManualIp }
                                .testTag("toggle_manual_ip"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Direct IP / Add Favorite",
                                style = MaterialTheme.typography.titleMedium,
                                color = TitaniumText
                            )
                            Icon(
                                imageVector = if (showManualIp) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Direct IP",
                                tint = PewterMuted
                            )
                        }

                        AnimatedVisibility(visible = showManualIp) {
                            Column(
                                modifier = Modifier.padding(top = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                LiquidGlassTextField(
                                    value = manualFavName,
                                    onValueChange = { if (it.length <= 24) manualFavName = it },
                                    label = "LABEL (OPTIONAL)",
                                    placeholder = "Living Room Fire TV",
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    LiquidGlassTextField(
                                        value = manualIp,
                                        onValueChange = { manualIp = it },
                                        label = "IP ADDRESS",
                                        placeholder = "192.168.1.42",
                                        modifier = Modifier
                                            .weight(2f)
                                            .testTag("manual_ip_input")
                                    )
                                    LiquidGlassTextField(
                                        value = manualPort,
                                        onValueChange = { input ->
                                            manualPort = input.filter { it.isDigit() }.take(5)
                                        },
                                        label = "PORT",
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("manual_port_input")
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            onSaveManualFavorite(manualFavName, manualIp, manualPort)
                                        },
                                        enabled = manualIp.isNotBlank(),
                                        border = BorderStroke(1.dp, WarmAmber.copy(alpha = 0.55f)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .testTag("save_manual_favorite_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = WarmAmber,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Save Favorite", color = WarmAmber, fontSize = 12.sp)
                                    }

                                    Button(
                                        onClick = {
                                            onConnectManualIp(manualIp, manualPort, pairingCode, true)
                                        },
                                        enabled = !isConnecting && manualIp.isNotBlank(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = BrushedSilver,
                                            contentColor = ObsidianBg
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .testTag("manual_connect_button")
                                    ) {
                                        Text("Connect", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingHostForPin?.let { targetHost ->
        var modalCode by remember(targetHost.id) { mutableStateOf(pairingCode) }
        var saveFav by remember(targetHost.id) { mutableStateOf(true) }
        LiquidGlassDialog(
            onDismissRequest = {
                pendingHostForPin = null
                onClearError()
            },
            accentColor = CobaltBlue,
            maxWidthDp = 420
        ) {
            Text(
                text = targetHost.name,
                style = MaterialTheme.typography.titleLarge,
                color = TitaniumText
            )
            LiquidGlassTextField(
                value = modalCode,
                onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }.take(6)
                    modalCode = digits
                    pairingCode = digits
                    if (digits.length == 6) {
                        pendingHostForPin = null
                        onConnectHost(targetHost, digits, saveFav)
                    }
                },
                label = "6-DIGIT PAIRING CODE",
                placeholder = "••••••",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    letterSpacing = 6.sp,
                    color = TitaniumText
                ),
                trailingContent = {
                    if (modalCode.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                modalCode = ""
                                pairingCode = ""
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = PewterMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            LiquidGlassCheckRow(
                checked = saveFav,
                label = "Save to Favorites (no code next time)",
                onCheckedChange = { saveFav = it }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        pendingHostForPin = null
                        onClearError()
                    },
                    border = BorderStroke(1.dp, GlassRimMid),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                ) {
                    Text("Cancel", color = PewterMuted)
                }
                Button(
                    enabled = modalCode.length == 6,
                    onClick = {
                        pendingHostForPin = null
                        onConnectHost(targetHost, modalCode, saveFav)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CobaltBlue,
                        contentColor = ObsidianBg
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                ) {
                    Text("Connect", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun FavoriteHostCard(
    favorite: FavoriteHostEntry,
    enabled: Boolean,
    onConnect: () -> Unit,
    onRemove: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    LiquidGlassPanel(
        shape = RoundedCornerShape(18.dp),
        elevated = true,
        accentBorderTint = if (focused) BrushedSilver else WarmAmber.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onConnect)
            .testTag("favorite_host_card")
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GlassFillSubtle,
                    border = BorderStroke(1.dp, WarmAmber.copy(alpha = 0.4f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Favorite",
                        tint = WarmAmber,
                        modifier = Modifier
                            .padding(9.dp)
                            .size(18.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = favorite.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TitaniumText
                    )
                    TapToRevealIpChip(
                        address = favorite.address,
                        port = favorite.port,
                        compact = true,
                        testTag = "fav_host_ip_reveal"
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Connect to ${favorite.name}",
                    tint = BrushedSilver,
                    modifier = Modifier.size(18.dp)
                )
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.testTag("remove_favorite_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove favorite",
                        tint = PewterMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoveredHostCard(
    host: DiscoveredHost,
    isFavorite: Boolean,
    enabled: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    val accent = SignalEmerald
    var focused by remember { mutableStateOf(false) }

    LiquidGlassPanel(
        shape = RoundedCornerShape(18.dp),
        elevated = true,
        accentBorderTint = if (focused) BrushedSilver else null,
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag("discovered_host_card")
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GlassFillSubtle,
                    border = BorderStroke(1.dp, GlassRimMid)
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = host.transport.badge,
                        tint = accent,
                        modifier = Modifier
                            .padding(9.dp)
                            .size(18.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = host.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TitaniumText
                    )
                    TapToRevealIpChip(
                        address = host.hostAddress,
                        port = host.port,
                        compact = true,
                        testTag = "discovered_host_ip_reveal"
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in 0 until 4) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i < host.activePlayers) accent else HairlineBorder
                                )
                        )
                    }
                }
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.testTag("toggle_favorite_host_button")
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (isFavorite) "Remove favorite" else "Save favorite",
                        tint = if (isFavorite) WarmAmber else PewterMuted,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Connect to ${host.name}",
                    tint = BrushedSilver,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(18.dp)
                )
            }
        }
    }
}
