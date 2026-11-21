package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.transport.LinkPadConstants
import com.example.ui.screens.ClientDiscoveryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.HostScreen
import com.example.ui.screens.PcCompanionScreen
import com.example.ui.screens.RetroControllerScreen
import com.example.ui.theme.LinkPadTheme
import com.example.ui.viewmodel.LinkPadViewModel

object Routes {
    const val HOME = "home"
    const val HOST = "host"
    const val PC_COMPANION = "pc_companion"
    const val CLIENT_DISCOVERY = "client_discovery"
    const val CONTROLLER = "controller"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LinkPadTheme {
                val navController = rememberNavController()
                val viewModel: LinkPadViewModel = viewModel()

                val settings by viewModel.settings.collectAsStateWithLifecycle()
                val envStatus by viewModel.environmentStatus.collectAsStateWithLifecycle()
                val hostActive by viewModel.hostActive.collectAsStateWithLifecycle()
                val pairingCode by viewModel.pairingCode.collectAsStateWithLifecycle()
                val localIp by viewModel.hostLocalIp.collectAsStateWithLifecycle()
                val btHostActive by viewModel.hostBluetoothActive.collectAsStateWithLifecycle()
                val hostSlots by viewModel.hostSlots.collectAsStateWithLifecycle()
                val discoveredHosts by viewModel.discoveredHosts.collectAsStateWithLifecycle()
                val clientState by viewModel.clientConnectionState.collectAsStateWithLifecycle()
                val hidState by viewModel.hidState.collectAsStateWithLifecycle()
                val hidNearbyDevices by viewModel.hidNearbyDevices.collectAsStateWithLifecycle()
                val hidIsScanning by viewModel.hidIsScanning.collectAsStateWithLifecycle()

                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME
                ) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            settings = settings,
                            environmentStatus = envStatus,
                            hostActive = hostActive,
                            pairingCode = pairingCode,
                            favoriteHosts = settings.favoriteHosts,
                            onOpenControllerDiscovery = {
                                navController.navigate(Routes.CLIENT_DISCOVERY)
                            },
                            onOpenHostMode = {
                                navController.navigate(Routes.HOST)
                            },
                            onOpenPcCompanion = {
                                navController.navigate(Routes.PC_COMPANION)
                            },
                            onOpenControllerCustomizer = {
                                navController.navigate("${Routes.CONTROLLER}?preview=true")
                            },
                            onUpdateDeviceName = { name ->
                                viewModel.setDeviceName(name)
                            },
                            onConnectFavorite = { fav ->
                                viewModel.connectToFavorite(
                                    favorite = fav,
                                    onConnected = {
                                        navController.navigate(Routes.CONTROLLER)
                                    },
                                    onFallback = {
                                        val encodedIp = Uri.encode(fav.address)
                                        navController.navigate(
                                            "${Routes.CLIENT_DISCOVERY}?ip=$encodedIp&port=${fav.port}"
                                        )
                                    }
                                )
                            },
                            onRemoveFavorite = { fav ->
                                viewModel.removeFavoriteHost(fav)
                            },
                            onQuickReconnectRecent = { recent ->
                                val encodedIp = Uri.encode(recent.address)
                                navController.navigate(
                                    "${Routes.CLIENT_DISCOVERY}?ip=$encodedIp&port=${recent.port}"
                                )
                            }
                        )
                    }

                    composable(Routes.PC_COMPANION) {
                        PcCompanionScreen(
                            onOpenControllerDiscovery = {
                                navController.navigate(Routes.CLIENT_DISCOVERY) {
                                    popUpTo(Routes.HOME)
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(Routes.HOST) {
                        HostScreen(
                            hostActive = hostActive,
                            pairingCode = pairingCode,
                            localIp = localIp,
                            bluetoothHostingActive = btHostActive,
                            slots = hostSlots,
                            environmentStatus = envStatus,
                            allowFavoritesWithoutCode = settings.allowFavoritesWithoutCode,
                            trustedDeviceCount = settings.trustedClientIds.size,
                            onBack = { navController.popBackStack() },
                            onStartHost = { viewModel.startHostSession() },
                            onStopHost = { viewModel.stopHostSession() },
                            onRegenerateCode = { viewModel.regeneratePairingCode() },
                            onToggleBluetoothHost = { enabled ->
                                viewModel.toggleBluetoothHosting(enabled)
                            },
                            onUpdateSlotKeymap = { slot, profile ->
                                viewModel.updateSlotKeymap(slot, profile)
                            },
                            onKickPlayer = { slot ->
                                viewModel.kickPlayer(slot)
                            },
                            onToggleAllowFavoritesWithoutCode = { enabled ->
                                viewModel.setAllowFavoritesWithoutCode(enabled)
                            },
                            onClearTrustedDevices = {
                                viewModel.clearTrustedDevices()
                            },
                            onOpenControllerDiscovery = {
                                navController.navigate(Routes.CLIENT_DISCOVERY)
                            },
                            onRefreshSystemStatus = {
                                viewModel.refreshSystemStatus()
                            }
                        )
                    }

                    composable(
                        route = "${Routes.CLIENT_DISCOVERY}?ip={ip}&port={port}",
                        arguments = listOf(
                            navArgument("ip") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument("port") {
                                type = NavType.StringType
                                defaultValue = LinkPadConstants.HOST_UDP_PORT.toString()
                            }
                        )
                    ) { backStackEntry ->
                        val prefillIp = backStackEntry.arguments?.getString("ip").orEmpty()
                        val prefillPort = backStackEntry.arguments?.getString("port")
                            ?: LinkPadConstants.HOST_UDP_PORT.toString()

                        ClientDiscoveryScreen(
                            discoveredHosts = discoveredHosts,
                            favoriteHosts = settings.favoriteHosts,
                            connectionState = clientState,
                            hidState = hidState,
                            hidNearbyDevices = hidNearbyDevices,
                            hidIsScanning = hidIsScanning,
                            environmentStatus = envStatus,
                            prefillIp = prefillIp,
                            prefillPort = prefillPort,
                            onStartDiscovery = { viewModel.startDiscovery() },
                            onStopDiscovery = { viewModel.stopDiscovery() },
                            onRefreshBluetooth = { viewModel.refreshBluetoothDiscovery() },
                            onStartHidRegistration = { viewModel.startBluetoothHidRegistration() },
                            onConnectHidTarget = { addr -> viewModel.connectBluetoothHidTarget(addr) },
                            onConnectHidByMac = { mac -> viewModel.connectBluetoothHidByMac(mac) },
                            onStartHidScan = { viewModel.startBluetoothHidScan() },
                            onStopHidScan = { viewModel.stopBluetoothHidScan() },
                            onOpenHidController = {
                                viewModel.markHidControllerActive()
                                navController.navigate(Routes.CONTROLLER)
                            },
                            onOpenControllerCustomizer = {
                                navController.navigate("${Routes.CONTROLLER}?preview=true")
                            },
                            onClearError = { viewModel.clearClientError() },
                            onConnectHost = { host, code, saveAsFavorite ->
                                viewModel.connectToHost(host, code, saveAsFavorite) {
                                    navController.navigate(Routes.CONTROLLER)
                                }
                            },
                            onConnectFavorite = { fav ->
                                viewModel.connectToFavorite(
                                    favorite = fav,
                                    onConnected = {
                                        navController.navigate(Routes.CONTROLLER)
                                    }
                                )
                            },
                            onToggleFavorite = { host ->
                                viewModel.toggleFavoriteHost(host)
                            },
                            onRemoveFavorite = { fav ->
                                viewModel.removeFavoriteHost(fav)
                            },
                            onSaveManualFavorite = { name, ip, port ->
                                viewModel.saveManualFavorite(name, ip, port)
                            },
                            onConnectManualIp = { ip, port, code, saveAsFavorite ->
                                viewModel.connectManualIp(ip, port, code, saveAsFavorite) {
                                    navController.navigate(Routes.CONTROLLER)
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = "${Routes.CONTROLLER}?preview={preview}",
                        arguments = listOf(
                            navArgument("preview") {
                                type = NavType.BoolType
                                defaultValue = false
                            }
                        )
                    ) { backStackEntry ->
                        val isPreview = backStackEntry.arguments?.getBoolean("preview") ?: false
                        RetroControllerScreen(
                            previewMode = isPreview,
                            connectionState = clientState,
                            hidState = hidState,
                            controllerLayout = settings.controllerLayout,
                            controllerTheme = settings.controllerTheme,
                            hapticStrength = settings.hapticStrength,
                            gyroMode = settings.gyroMode,
                            gyroResponseMode = settings.gyroResponseMode,
                            gyroSensitivity = settings.gyroSensitivity,
                            invertGyroY = settings.invertGyroY,
                            stickDeadzonePct = settings.stickDeadzonePct,
                            proPaddleConfig = settings.proPaddleConfig,
                            customization = settings.customizationFor(settings.controllerLayout),
                            onSelectLayout = { layout -> viewModel.setControllerLayout(layout) },
                            onSelectTheme = { theme -> viewModel.setControllerTheme(theme) },
                            onSelectHapticStrength = { strength -> viewModel.setHapticStrength(strength) },
                            onSelectGyroMode = { mode -> viewModel.setGyroMode(mode) },
                            onSelectGyroResponseMode = { mode -> viewModel.setGyroResponseMode(mode) },
                            onChangeGyroSensitivity = { sens -> viewModel.setGyroSensitivity(sens) },
                            onChangeInvertGyroY = { inv -> viewModel.setInvertGyroY(inv) },
                            onChangeDeadzonePct = { pct -> viewModel.setStickDeadzonePct(pct) },
                            onChangeProPaddleConfig = { cfg -> viewModel.setProPaddleConfig(cfg) },
                            onChangeCustomization = { custom ->
                                viewModel.setControlCustomization(settings.controllerLayout, custom)
                            },
                            onResetCustomization = {
                                viewModel.resetControlCustomization(settings.controllerLayout)
                            },
                            onSendControllerState = { mask, layout, lx, ly, rx, ry ->
                                if (!isPreview) {
                                    viewModel.sendControllerState(mask, layout, lx, ly, rx, ry)
                                }
                            },
                            onDisconnect = {
                                if (!isPreview) {
                                    viewModel.disconnectClient()
                                }
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
}
