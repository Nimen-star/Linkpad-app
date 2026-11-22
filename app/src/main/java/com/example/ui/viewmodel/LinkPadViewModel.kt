package com.example.ui.viewmodel

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bridge.ControllerHostService
import com.example.bridge.HostRuntimeHolder
import com.example.bridge.InputBridgeBus
import com.example.bridge.LinkPadInputMethodService
import com.example.data.LinkPadPreferences
import com.example.data.UserSettings
import com.example.protocol.ControlCustomization
import com.example.protocol.ControllerLayout
import com.example.protocol.ControllerTheme
import com.example.protocol.DiscoveredHost
import com.example.protocol.FavoriteHostEntry
import com.example.protocol.GyroMode
import com.example.protocol.GyroResponseMode
import com.example.protocol.HapticStrength
import com.example.protocol.KeymapProfile
import com.example.protocol.OsInputBridgeMode
import com.example.protocol.PlayerSlotState
import com.example.protocol.ProPaddleConfig
import com.example.protocol.RecentHostEntry
import com.example.protocol.TransportType
import com.example.transport.ClientConnectionState
import com.example.transport.ControllerClientSession
import com.example.transport.HostDiscoveryManager
import com.example.transport.LinkPadConstants
import com.example.uhid.ProvisionStage
import com.example.uhid.ProvisionUiState
import com.example.uhid.ProvisioningCoordinator
import com.example.uhid.UhidBridgeState
import com.example.uhid.UsbAdbDeviceInfo
import com.example.uhid.UsbAdbTracker
import com.example.uhid.WirelessAdbScanner
import com.example.uhid.WirelessAdbTarget
import com.example.uhid.WirelessPairNotificationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EnvironmentStatus(
    val wifiConnected: Boolean = false,
    val imeEnabled: Boolean = false,
    val imeSelected: Boolean = false
)

class LinkPadViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val preferences = LinkPadPreferences(appContext)
    private val discoveryManager = HostDiscoveryManager(appContext)
    private val clientSession = ControllerClientSession(appContext)
    private val usbAdbTracker = UsbAdbTracker(appContext)
    private val wirelessScanner = WirelessAdbScanner(appContext)
    private val provisioningCoordinator = ProvisioningCoordinator(appContext)

    val settings: StateFlow<UserSettings> = preferences.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        UserSettings()
    )

    val hostActive: StateFlow<Boolean> = InputBridgeBus.hostActive
    val pairingCode: StateFlow<String> = InputBridgeBus.pairingCode
    val hostLocalIp: StateFlow<String> = InputBridgeBus.localIpAddress
    val hostPacketsPerSecond: StateFlow<Int> = InputBridgeBus.packetsPerSecond
    val hostSlots: StateFlow<List<PlayerSlotState>> = InputBridgeBus.slots
    val accessibilityBridgeActive: StateFlow<Boolean> = InputBridgeBus.accessibilityBridgeActive
    val imeBridgeActive: StateFlow<Boolean> = InputBridgeBus.imeBridgeActive
    val osInputBridgeMode: StateFlow<OsInputBridgeMode> = InputBridgeBus.osInputBridgeMode
    val hardwareBridgeState: StateFlow<UhidBridgeState> =
        HostRuntimeHolder.hardwareBridgeState(appContext)

    val usbAdbTargets: StateFlow<List<UsbAdbDeviceInfo>> = usbAdbTracker.targets
    val wirelessAdbTargets: StateFlow<List<WirelessAdbTarget>> = wirelessScanner.targets
    val wirelessScanning: StateFlow<Boolean> = wirelessScanner.scanning
    val provisionState: StateFlow<ProvisionUiState> = provisioningCoordinator.state

    private val _navigateToControllerEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToControllerEvent: SharedFlow<Unit> = _navigateToControllerEvent.asSharedFlow()

    private val _remoteAdbBusy = MutableStateFlow(false)
    val remoteAdbBusy: StateFlow<Boolean> = _remoteAdbBusy.asStateFlow()

    private val _remoteAdbFeedback = MutableStateFlow<String?>(null)
    val remoteAdbFeedback: StateFlow<String?> = _remoteAdbFeedback.asStateFlow()

    val discoveredHosts: StateFlow<List<DiscoveredHost>> = discoveryManager.hosts
    val clientConnectionState: StateFlow<ClientConnectionState> = clientSession.connectionState

    private val _environmentStatus = MutableStateFlow(EnvironmentStatus())
    val environmentStatus: StateFlow<EnvironmentStatus> = _environmentStatus

    private val _selectedMonitorSlot = MutableStateFlow(0)
    val selectedMonitorSlot: StateFlow<Int> = _selectedMonitorSlot

    private var pendingUsbSetupDeviceId: Int? = null
    private var lastAutoConnectRunId: Long = 0L

    val combinedMonitorMask: StateFlow<Int> = combine(
        hostSlots,
        _selectedMonitorSlot
    ) { slots, selected ->
        if (selected in 1..4) {
            slots.firstOrNull { it.slot == selected }?.buttonMask ?: 0
        } else {
            slots.fold(0) { acc, slot -> if (slot.connected) acc or slot.buttonMask else acc }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), 0)

    init {
        usbAdbTracker.start()
        refreshSystemStatus()
        viewModelScope.launch {
            usbAdbTargets.collect { targets ->
                val pendingId = pendingUsbSetupDeviceId ?: return@collect
                val grantedDev = targets.firstOrNull { it.deviceId == pendingId && it.hasPermission }
                if (grantedDev != null) {
                    pendingUsbSetupDeviceId = null
                    val cid = preferences.getOrCreateClientId()
                    provisioningCoordinator.startUsbProvisioning(
                        tracker = usbAdbTracker,
                        deviceId = grantedDev.deviceId,
                        displayName = grantedDev.productName,
                        controllerClientId = cid
                    )
                }
            }
        }
        viewModelScope.launch {
            provisionState.collect { pState ->
                if (pState.stage == ProvisionStage.DONE &&
                    pState.autoConnectReady &&
                    !pState.targetIp.isNullOrBlank() &&
                    pState.runId != lastAutoConnectRunId
                ) {
                    lastAutoConnectRunId = pState.runId
                    autoConnectToProvisionedHost(
                        ip = pState.targetIp,
                        port = pState.targetPort,
                        displayName = pState.targetLabel.ifBlank { pState.targetIp }
                    )
                }
            }
        }
        viewModelScope.launch {
            preferences.getOrCreateClientId()
        }
        viewModelScope.launch {
            settings.collect { userSettings ->
                userSettings.slotKeymaps.forEach { (slot, profile) ->
                    HostRuntimeHolder.updateKeymap(slot, profile)
                }
                HostRuntimeHolder.setTrustedConfiguration(
                    trustedIds = userSettings.trustedClientIds,
                    allowAnyFavorite = userSettings.allowFavoritesWithoutCode
                )
                InputBridgeBus.setOsInputBridgeMode(userSettings.osInputBridgeMode)
            }
        }
    }

    fun refreshSystemStatus() {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNetwork)
        val localIp = LinkPadConstants.findPrimaryLocalIpv4(appContext)
        val wifiUp = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true ||
            localIp != "127.0.0.1"

        val imm = appContext.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val imeEnabled = runCatching {
            imm?.enabledInputMethodList?.any {
                it.packageName == appContext.packageName
            } == true
        }.getOrDefault(false)

        val expectedImeShort = ComponentName(appContext, LinkPadInputMethodService::class.java).flattenToShortString()
        val expectedImeFull = ComponentName(appContext, LinkPadInputMethodService::class.java).flattenToString()
        val currentDefaultIme = runCatching {
            Settings.Secure.getString(appContext.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty()
        }.getOrDefault("")
        val imeSelected = currentDefaultIme == expectedImeShort ||
            currentDefaultIme == expectedImeFull ||
            currentDefaultIme.contains(LinkPadInputMethodService::class.java.simpleName)

        val am = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val accActiveInSystem = runCatching {
            am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                ?.any { it.resolveInfo?.serviceInfo?.packageName == appContext.packageName } == true
        }.getOrDefault(false)
        InputBridgeBus.setAccessibilityBridgeActive(accActiveInSystem)

        _environmentStatus.value = EnvironmentStatus(
            wifiConnected = wifiUp,
            imeEnabled = imeEnabled,
            imeSelected = imeSelected
        )
    }

    fun setDeviceName(name: String) {
        viewModelScope.launch {
            preferences.setDeviceName(name)
        }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setHapticsEnabled(enabled)
        }
    }

    fun setHapticStrength(strength: HapticStrength) {
        viewModelScope.launch {
            preferences.setHapticStrength(strength)
        }
    }

    fun setControllerLayout(layout: ControllerLayout) {
        viewModelScope.launch {
            preferences.setControllerLayout(layout)
        }
    }

    fun setControllerTheme(theme: ControllerTheme) {
        viewModelScope.launch {
            preferences.setControllerTheme(theme)
        }
    }

    fun setGyroMode(mode: GyroMode) {
        viewModelScope.launch {
            preferences.setGyroMode(mode)
        }
    }

    fun setGyroResponseMode(mode: GyroResponseMode) {
        viewModelScope.launch {
            preferences.setGyroResponseMode(mode)
        }
    }

    fun setGyroSensitivity(sensitivity: Float) {
        viewModelScope.launch {
            preferences.setGyroSensitivity(sensitivity)
        }
    }

    fun setInvertGyroY(invert: Boolean) {
        viewModelScope.launch {
            preferences.setInvertGyroY(invert)
        }
    }

    fun setStickDeadzonePct(pct: Int) {
        viewModelScope.launch {
            preferences.setStickDeadzonePct(pct)
        }
    }

    fun setProPaddleConfig(config: ProPaddleConfig) {
        viewModelScope.launch {
            preferences.setProPaddleConfig(config)
        }
    }

    fun setControlCustomization(layout: ControllerLayout, customization: ControlCustomization) {
        viewModelScope.launch {
            preferences.setControlCustomization(layout, customization)
        }
    }

    fun resetControlCustomization(layout: ControllerLayout) {
        viewModelScope.launch {
            preferences.resetControlCustomization(layout)
        }
    }

    fun setAllowFavoritesWithoutCode(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAllowFavoritesWithoutCode(enabled)
        }
    }

    fun clearTrustedDevices() {
        viewModelScope.launch {
            preferences.clearTrustedClientIds()
        }
    }

    fun startHostSession() {
        disconnectClient()
        val currentSettings = settings.value
        val manager = HostRuntimeHolder.getOrCreate(appContext)
        manager.onTrustClientId = { trustedId ->
            viewModelScope.launch {
                preferences.addTrustedClientId(trustedId)
            }
        }
        manager.setTrustedConfiguration(
            trustedIds = currentSettings.trustedClientIds,
            allowAnyFavorite = currentSettings.allowFavoritesWithoutCode
        )
        manager.startHosting(
            deviceName = currentSettings.deviceName,
            enableBluetooth = false,
            initialKeymaps = currentSettings.slotKeymaps
        )
        ControllerHostService.start(
            context = appContext,
            deviceName = currentSettings.deviceName,
            enableBluetooth = false
        )
    }

    fun stopHostSession() {
        ControllerHostService.stop(appContext)
    }

    fun setOsInputBridgeMode(mode: OsInputBridgeMode) {
        InputBridgeBus.setOsInputBridgeMode(mode)
        viewModelScope.launch {
            preferences.setOsInputBridgeMode(mode)
        }
    }

    fun refreshHardwareBridge() {
        HostRuntimeHolder.refreshHardwareBridge(appContext)
    }

    fun activateHardwareGamepads() {
        HostRuntimeHolder.activateHardwareGamepads(appContext)
    }

    fun pairWirelessDebugging(portText: String, pairingCode: String) {
        HostRuntimeHolder.pairWirelessDebugging(appContext, portText, pairingCode)
    }

    fun postWirelessPairShadeNotification() {
        WirelessPairNotificationHelper.showQuickPairNotification(
            appContext,
            HostRuntimeHolder.currentDiscoveredPairPort()
        )
    }

    fun trustRemoteClient(clientId: Int) {
        if (clientId == 0) return
        HostRuntimeHolder.trustClientImmediately(appContext, clientId)
        viewModelScope.launch {
            preferences.addTrustedClientId(clientId)
        }
    }

    fun refreshUsbTargets() {
        usbAdbTracker.refresh()
    }

    fun refreshWirelessTargets() {
        val priority = buildList {
            discoveredHosts.value.forEach { add(it.hostAddress) }
            settings.value.favoriteHosts.forEach { add(it.address) }
            settings.value.recentHosts.forEach { add(it.address) }
        }
        wirelessScanner.startScan(priority)
    }

    fun triggerUsbTargetSetup(device: UsbAdbDeviceInfo) {
        if (device.hasPermission) {
            viewModelScope.launch {
                val cid = preferences.getOrCreateClientId()
                provisioningCoordinator.startUsbProvisioning(
                    tracker = usbAdbTracker,
                    deviceId = device.deviceId,
                    displayName = device.productName,
                    controllerClientId = cid
                )
            }
        } else {
            pendingUsbSetupDeviceId = device.deviceId
            usbAdbTracker.requestPermission(device.deviceId)
        }
    }

    fun startWifiTargetSetup(
        targetIp: String,
        displayName: String? = null,
        connectPort: Int = 5555
    ) {
        viewModelScope.launch {
            val cid = preferences.getOrCreateClientId()
            provisioningCoordinator.startWifiProvisioning(
                targetIp = targetIp,
                connectPort = connectPort,
                displayName = displayName,
                controllerClientId = cid
            )
        }
    }

    fun startWirelessPairAndSetup(
        targetIp: String,
        pairPort: Int,
        pairingCode: String,
        connectPort: Int? = null,
        displayName: String? = null
    ) {
        viewModelScope.launch {
            val cid = preferences.getOrCreateClientId()
            provisioningCoordinator.startWirelessPairAndProvision(
                targetIp = targetIp,
                pairPort = pairPort,
                pairingCode = pairingCode,
                connectPort = connectPort,
                displayName = displayName,
                controllerClientId = cid
            )
        }
    }

    fun dismissProvisioning() {
        provisioningCoordinator.dismiss()
    }

    private suspend fun autoConnectToProvisionedHost(ip: String, port: Int, displayName: String) {
        val cid = preferences.getOrCreateClientId()
        val host = DiscoveredHost(
            id = "auto:$ip:$port",
            name = displayName,
            hostAddress = ip,
            port = port,
            transport = TransportType.WIFI_UDP
        )
        for (attempt in 0 until 6) {
            delay(if (attempt == 0) 350L else 500L)
            val connected = clientSession.connect(
                host = host,
                pairingCode = "",
                deviceName = settings.value.deviceName,
                clientId = cid,
                trustedFavoriteMode = true
            )
            if (connected) {
                preferences.addFavoriteHost(
                    FavoriteHostEntry(
                        name = displayName,
                        address = ip,
                        port = port,
                        transport = TransportType.WIFI_UDP
                    )
                )
                preferences.addRecentHost(
                    RecentHostEntry(
                        name = displayName,
                        address = ip,
                        port = port,
                        transport = TransportType.WIFI_UDP
                    )
                )
                provisioningCoordinator.dismiss()
                discoveryManager.stopDiscovery()
                wirelessScanner.stopScan()
                _navigateToControllerEvent.tryEmit(Unit)
                return
            }
        }
    }

    fun activateRemoteTvOverWifi(targetIp: String) {
        if (_remoteAdbBusy.value) return
        viewModelScope.launch {
            _remoteAdbBusy.value = true
            _remoteAdbFeedback.value = null
            val res = HostRuntimeHolder.activateRemoteHostOverWifi(appContext, targetIp)
            _remoteAdbFeedback.value = res.fold(
                onSuccess = { it },
                onFailure = { it.message ?: "Remote TV hardware trigger failed." }
            )
            _remoteAdbBusy.value = false
        }
    }

    fun clearRemoteAdbFeedback() {
        _remoteAdbFeedback.value = null
    }

    fun regeneratePairingCode() {
        HostRuntimeHolder.regenerateCode()
    }

    fun updateSlotKeymap(slot: Int, profile: KeymapProfile) {
        HostRuntimeHolder.updateKeymap(slot, profile)
        viewModelScope.launch {
            preferences.setSlotKeymap(slot, profile)
        }
    }

    fun kickPlayer(slot: Int) {
        HostRuntimeHolder.kickPlayer(slot)
    }

    fun selectMonitorSlot(slot: Int) {
        _selectedMonitorSlot.value = slot
    }

    fun startDiscovery() {
        discoveryManager.startDiscovery()
        refreshWirelessTargets()
        refreshSystemStatus()
    }

    fun stopDiscovery() {
        discoveryManager.stopDiscovery()
        wirelessScanner.stopScan()
    }

    fun clearClientError() {
        clientSession.clearError()
    }

    fun toggleFavoriteHost(host: DiscoveredHost) {
        viewModelScope.launch {
            val entry = FavoriteHostEntry(
                name = host.name,
                address = host.hostAddress,
                port = host.port,
                transport = host.transport,
                bluetoothAddress = host.bluetoothAddress
            )
            val exists = settings.value.favoriteHosts.any {
                it.address == entry.address &&
                    it.port == entry.port &&
                    it.bluetoothAddress == entry.bluetoothAddress
            }
            if (exists) {
                preferences.removeFavoriteHost(entry)
            } else {
                preferences.addFavoriteHost(entry)
            }
        }
    }

    fun saveManualFavorite(name: String, ipAddress: String, portText: String) {
        val cleanedIp = ipAddress.trim()
        if (cleanedIp.isEmpty()) return
        val port = portText.trim().toIntOrNull() ?: LinkPadConstants.HOST_UDP_PORT
        val cleanName = name.trim().ifBlank { cleanedIp }
        viewModelScope.launch {
            preferences.addFavoriteHost(
                FavoriteHostEntry(
                    name = cleanName,
                    address = cleanedIp,
                    port = port,
                    transport = TransportType.WIFI_UDP
                )
            )
        }
    }

    fun removeFavoriteHost(entry: FavoriteHostEntry) {
        viewModelScope.launch {
            preferences.removeFavoriteHost(entry)
        }
    }

    fun connectToFavorite(
        favorite: FavoriteHostEntry,
        onConnected: () -> Unit,
        onFallback: () -> Unit = {}
    ) {
        val host = DiscoveredHost(
            id = "fav:${favorite.address}:${favorite.port}",
            name = favorite.name,
            hostAddress = favorite.address,
            port = favorite.port,
            bluetoothAddress = favorite.bluetoothAddress,
            transport = TransportType.WIFI_UDP
        )
        viewModelScope.launch {
            val cid = preferences.getOrCreateClientId()
            val success = clientSession.connect(
                host = host,
                pairingCode = "",
                deviceName = settings.value.deviceName,
                clientId = cid,
                trustedFavoriteMode = true
            )
            if (success) {
                preferences.addRecentHost(
                    RecentHostEntry(
                        name = host.name,
                        address = host.hostAddress,
                        port = host.port,
                        transport = host.transport,
                        bluetoothAddress = host.bluetoothAddress
                    )
                )
                discoveryManager.stopDiscovery()
                onConnected()
            } else {
                onFallback()
            }
        }
    }

    fun connectToHost(
        host: DiscoveredHost,
        pairingCode: String,
        saveAsFavorite: Boolean = false,
        onConnected: () -> Unit
    ) {
        viewModelScope.launch {
            val cid = preferences.getOrCreateClientId()
            val success = clientSession.connect(
                host = host,
                pairingCode = pairingCode,
                deviceName = settings.value.deviceName,
                clientId = cid,
                trustedFavoriteMode = false
            )
            if (success) {
                preferences.addRecentHost(
                    RecentHostEntry(
                        name = host.name,
                        address = host.hostAddress,
                        port = host.port,
                        transport = host.transport,
                        bluetoothAddress = host.bluetoothAddress
                    )
                )
                if (saveAsFavorite) {
                    preferences.addFavoriteHost(
                        FavoriteHostEntry(
                            name = host.name,
                            address = host.hostAddress,
                            port = host.port,
                            transport = host.transport,
                            bluetoothAddress = host.bluetoothAddress
                        )
                    )
                }
                discoveryManager.stopDiscovery()
                onConnected()
            }
        }
    }

    fun connectManualIp(
        ipAddress: String,
        portText: String,
        pairingCode: String,
        saveAsFavorite: Boolean = false,
        onConnected: () -> Unit
    ) {
        val cleanedIp = ipAddress.trim()
        val port = portText.trim().toIntOrNull() ?: LinkPadConstants.HOST_UDP_PORT
        if (cleanedIp.isEmpty()) return
        val manualHost = DiscoveredHost(
            id = "manual:$cleanedIp:$port",
            name = cleanedIp,
            hostAddress = cleanedIp,
            port = port,
            transport = TransportType.WIFI_UDP
        )
        connectToHost(manualHost, pairingCode, saveAsFavorite, onConnected)
    }

    fun sendControllerMask(mask: Int) {
        sendControllerState(
            buttonMask = mask,
            layout = settings.value.controllerLayout,
            lx = 0,
            ly = 0,
            rx = 0,
            ry = 0
        )
    }

    fun sendControllerState(
        buttonMask: Int,
        layout: ControllerLayout,
        lx: Byte,
        ly: Byte,
        rx: Byte,
        ry: Byte
    ) {
        clientSession.sendControllerState(buttonMask, layout, lx, ly, rx, ry)
    }

    fun disconnectClient() {
        clientSession.disconnect()
    }

    override fun onCleared() {
        usbAdbTracker.stop()
        wirelessScanner.stopScan()
        discoveryManager.stopDiscovery()
        clientSession.disconnect()
        super.onCleared()
    }
}
