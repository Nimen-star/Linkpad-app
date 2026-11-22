package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.flow.MutableStateFlow

object Routes {
    const val HOME = "home"
    const val HOST = "host"
    const val PC_COMPANION = "pc_companion"
    const val CLIENT_DISCOVERY = "client_discovery"
    const val CONTROLLER = "controller"
}

data class AutoHostLaunchRequest(
    val seq: Long,
    val autoHost: Boolean,
    val trustClientId: Int
)

class MainActivity : ComponentActivity() {
    private var launchSeq = 0L
    private val launchRequestFlow = MutableStateFlow<AutoHostLaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeAutoHostIntent(intent)
        setContent {
            LinkPadTheme {
                val navController = rememberNavController()
                val viewModel: LinkPadViewModel = viewModel()

                val settings by viewModel.settings.collectAsStateWithLifecycle()
                val envStatus by viewModel.environmentStatus.collectAsStateWithLifecycle()
                val hostActive by viewModel.hostActive.collectAsStateWithLifecycle()
                val pairingCode by viewModel.pairingCode.collectAsStateWithLifecycle()
                val localIp by viewModel.hostLocalIp.collectAsStateWithLifecycle()
                val hostSlots by viewModel.hostSlots.collectAsStateWithLifecycle()
                val hardwareBridgeState by viewModel.hardwareBridgeState.collectAsStateWithLifecycle()
                val accessibilityBridgeActive by viewModel.accessibilityBridgeActive.collectAsStateWithLifecycle()
                val imeBridgeActive by viewModel.imeBridgeActive.collectAsStateWithLifecycle()
                val osInputBridgeMode by viewModel.osInputBridgeMode.collectAsStateWithLifecycle()
                val remoteAdbBusy by viewModel.remoteAdbBusy.collectAsStateWithLifecycle()
                val remoteAdbFeedback by viewModel.remoteAdbFeedback.collectAsStateWithLifecycle()
                val usbAdbTargets by viewModel.usbAdbTargets.collectAsStateWithLifecycle()
                val wirelessAdbTargets by viewModel.wirelessAdbTargets.collectAsStateWithLifecycle()
                val wirelessScanning by viewModel.wirelessScanning.collectAsStateWithLifecycle()
                val provisionState by viewModel.provisionState.collectAsStateWithLifecycle()
                val discoveredHosts by viewModel.discoveredHosts.collectAsStateWithLifecycle()
                val clientState by viewModel.clientConnectionState.collectAsStateWithLifecycle()
                val launchReq by launchRequestFlow.collectAsStateWithLifecycle()

                LaunchedEffect(launchReq?.seq) {
                    val req = launchReq ?: return@LaunchedEffect
                    if (req.trustClientId != 0) {
                        viewModel.trustRemoteClient(req.trustClientId)
                    }
                    if (req.autoHost) {
                        viewModel.startHostSession()
                        navController.navigate(Routes.HOST) {
                            launchSingleTop = true
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    viewModel.navigateToControllerEvent.collect {
                        navController.navigate(Routes.CONTROLLER) {
                            launchSingleTop = true
                        }
                    }
                }

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
                            slots = hostSlots,
                            environmentStatus = envStatus,
                            accessibilityBridgeActive = accessibilityBridgeActive,
                            imeBridgeActive = imeBridgeActive,
                            osInputBridgeMode = osInputBridgeMode,
                            hardwareBridgeState = hardwareBridgeState,
                            allowFavoritesWithoutCode = settings.allowFavoritesWithoutCode,
                            trustedDeviceCount = settings.trustedClientIds.size,
                            onBack = { navController.popBackStack() },
                            onStartHost = { viewModel.startHostSession() },
                            onStopHost = { viewModel.stopHostSession() },
                            onRegenerateCode = { viewModel.regeneratePairingCode() },
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
                            onSelectOsInputBridgeMode = { mode ->
                                viewModel.setOsInputBridgeMode(mode)
                            },
                            onRefreshSystemStatus = {
                                viewModel.refreshSystemStatus()
                            },
                            onRefreshHardwareBridge = {
                                viewModel.refreshHardwareBridge()
                            },
                            onActivateHardwareGamepads = {
                                viewModel.activateHardwareGamepads()
                            },
                            onPairWirelessDebugging = { port, code ->
                                viewModel.pairWirelessDebugging(port, code)
                            },
                            onPostWirelessPairNotification = {
                                viewModel.postWirelessPairShadeNotification()
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
                            prefillIp = prefillIp,
                            prefillPort = prefillPort,
                            usbAdbTargets = usbAdbTargets,
                            wirelessAdbTargets = wirelessAdbTargets,
                            wirelessScanning = wirelessScanning,
                            provisionState = provisionState,
                            remoteAdbBusy = remoteAdbBusy,
                            remoteAdbFeedback = remoteAdbFeedback,
                            onStartDiscovery = { viewModel.startDiscovery() },
                            onStopDiscovery = { viewModel.stopDiscovery() },
                            onOpenControllerCustomizer = {
                                navController.navigate("${Routes.CONTROLLER}?preview=true")
                            },
                            onClearError = { viewModel.clearClientError() },
                            onRefreshUsbTargets = { viewModel.refreshUsbTargets() },
                            onRescanWirelessTargets = { viewModel.refreshWirelessTargets() },
                            onSetupUsbDevice = { dev -> viewModel.triggerUsbTargetSetup(dev) },
                            onStartWifiTargetSetup = { ip, name, port ->
                                viewModel.startWifiTargetSetup(ip, name, port)
                            },
                            onStartWirelessPairAndSetup = { ip, pairPort, pin, connectPort, name ->
                                viewModel.startWirelessPairAndSetup(ip, pairPort, pin, connectPort, name)
                            },
                            onDismissProvisioning = { viewModel.dismissProvisioning() },
                            onActivateRemoteTvOverWifi = { ip ->
                                viewModel.activateRemoteTvOverWifi(ip)
                            },
                            onClearRemoteAdbFeedback = { viewModel.clearRemoteAdbFeedback() },
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeAutoHostIntent(intent)
    }

    private fun consumeAutoHostIntent(intent: Intent?) {
        intent ?: return
        val autoHost = intent.getBooleanExtra("linkpad_auto_host", false)
        val trustId = intent.getIntExtra("linkpad_trust_client_id", 0)
        if (autoHost || trustId != 0) {
            launchSeq += 1L
            launchRequestFlow.value = AutoHostLaunchRequest(
                seq = launchSeq,
                autoHost = autoHost,
                trustClientId = trustId
            )
            intent.removeExtra("linkpad_auto_host")
            intent.removeExtra("linkpad_trust_client_id")
        }
    }
}
