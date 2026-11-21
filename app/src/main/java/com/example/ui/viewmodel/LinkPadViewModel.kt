package com.example.ui.viewmodel

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bridge.ControllerHostService
import com.example.bridge.HostRuntimeHolder
import com.example.bridge.InputBridgeBus
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
import com.example.protocol.PlayerSlotState
import com.example.protocol.ProPaddleConfig
import com.example.protocol.RecentHostEntry
import com.example.protocol.TransportType
import com.example.transport.BluetoothHidController
import com.example.transport.BluetoothHidState
import com.example.transport.ClientConnectionState
import com.example.transport.ControllerClientSession
import com.example.transport.HidDiscoveredDevice
import com.example.transport.HostDiscoveryManager
import com.example.transport.LinkPadConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EnvironmentStatus(
    val wifiConnected: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val hasBluetoothPermission: Boolean = false
)

class LinkPadViewModel(application: Application) : AndroidViewModel(application) {

    private enum class ActiveControllerTransport {
        NONE,
        HOST_PROTOCOL,
        BLUETOOTH_HID
    }

    private val appContext = application.applicationContext
    private val preferences = LinkPadPreferences(appContext)
    private val discoveryManager = HostDiscoveryManager(appContext)
    private val clientSession = ControllerClientSession(appContext)
    private val bluetoothHidController = BluetoothHidController(appContext)

    @Volatile
    private var activeTransport: ActiveControllerTransport = ActiveControllerTransport.NONE

    val settings: StateFlow<UserSettings> = preferences.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        UserSettings()
    )

    val hostActive: StateFlow<Boolean> = InputBridgeBus.hostActive
    val pairingCode: StateFlow<String> = InputBridgeBus.pairingCode
    val hostLocalIp: StateFlow<String> = InputBridgeBus.localIpAddress
    val hostBluetoothActive: StateFlow<Boolean> = InputBridgeBus.bluetoothHostingActive
    val hostPacketsPerSecond: StateFlow<Int> = InputBridgeBus.packetsPerSecond
    val hostSlots: StateFlow<List<PlayerSlotState>> = InputBridgeBus.slots

    val discoveredHosts: StateFlow<List<DiscoveredHost>> = discoveryManager.hosts
    val clientConnectionState: StateFlow<ClientConnectionState> = clientSession.connectionState
    val hidState: StateFlow<BluetoothHidState> = bluetoothHidController.state
    val hidNearbyDevices: StateFlow<List<HidDiscoveredDevice>> = bluetoothHidController.nearbyDevices
    val hidIsScanning: StateFlow<Boolean> = bluetoothHidController.isScanning

    private val _environmentStatus = MutableStateFlow(EnvironmentStatus())
    val environmentStatus: StateFlow<EnvironmentStatus> = _environmentStatus

    private val _selectedMonitorSlot = MutableStateFlow(0)
    val selectedMonitorSlot: StateFlow<Int> = _selectedMonitorSlot

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
        refreshSystemStatus()
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

        val btAdapter = (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        val hasBtPerm = LinkPadConstants.hasBluetoothConnectPermission(appContext)
        val btOn = runCatching { btAdapter?.isEnabled == true }.getOrDefault(false)

        _environmentStatus.value = EnvironmentStatus(
            wifiConnected = wifiUp,
            bluetoothEnabled = btOn,
            hasBluetoothPermission = hasBtPerm
        )

        if (hasBtPerm && btOn) {
            bluetoothHidController.refreshStateFromProxy()
        }
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

    fun startBluetoothHidRegistration() {
        bluetoothHidController.setHostModeBlocking(InputBridgeBus.hostActive.value)
        bluetoothHidController.startRegistration(settings.value.deviceName)
        refreshSystemStatus()
    }

    fun connectBluetoothHidTarget(address: String) {
        bluetoothHidController.setHostModeBlocking(InputBridgeBus.hostActive.value)
        bluetoothHidController.connectToTarget(address)
    }

    fun connectBluetoothHidByMac(mac: String) {
        bluetoothHidController.setHostModeBlocking(InputBridgeBus.hostActive.value)
        bluetoothHidController.connectByMacAddress(mac)
    }

    fun startBluetoothHidScan() {
        bluetoothHidController.setHostModeBlocking(InputBridgeBus.hostActive.value)
        bluetoothHidController.startNearbyScan()
    }

    fun stopBluetoothHidScan() {
        bluetoothHidController.stopNearbyScan()
    }

    fun markHidControllerActive() {
        clientSession.disconnect()
        activeTransport = ActiveControllerTransport.BLUETOOTH_HID
    }

    fun disconnectBluetoothHid() {
        bluetoothHidController.disconnectHost()
        if (activeTransport == ActiveControllerTransport.BLUETOOTH_HID) {
            activeTransport = ActiveControllerTransport.NONE
        }
    }

    fun startHostSession() {
        disconnectClient()
        bluetoothHidController.setHostModeBlocking(true)
        val currentSettings = settings.value
        val hasBt = LinkPadConstants.hasBluetoothConnectPermission(appContext) &&
            _environmentStatus.value.bluetoothEnabled
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
            enableBluetooth = hasBt,
            initialKeymaps = currentSettings.slotKeymaps
        )
        ControllerHostService.start(
            context = appContext,
            deviceName = currentSettings.deviceName,
            enableBluetooth = hasBt
        )
    }

    fun stopHostSession() {
        ControllerHostService.stop(appContext)
        bluetoothHidController.setHostModeBlocking(false)
    }

    fun toggleBluetoothHosting(enabled: Boolean) {
        HostRuntimeHolder.setBluetoothEnabled(enabled)
        refreshSystemStatus()
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
        refreshSystemStatus()
    }

    fun refreshBluetoothDiscovery() {
        discoveryManager.refreshPairedBluetoothDevices()
        if (hidState.value is BluetoothHidState.Ready || hidState.value is BluetoothHidState.Error) {
            bluetoothHidController.refreshStateFromProxy()
        }
        refreshSystemStatus()
    }

    fun stopDiscovery() {
        discoveryManager.stopDiscovery()
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
            transport = favorite.transport
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
                activeTransport = ActiveControllerTransport.HOST_PROTOCOL
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
                activeTransport = ActiveControllerTransport.HOST_PROTOCOL
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
        if (activeTransport == ActiveControllerTransport.BLUETOOTH_HID &&
            hidState.value is BluetoothHidState.Connected
        ) {
            bluetoothHidController.sendGamepadState(buttonMask, lx, ly, rx, ry)
        } else {
            clientSession.sendControllerState(buttonMask, layout, lx, ly, rx, ry)
        }
    }

    fun disconnectClient() {
        when (activeTransport) {
            ActiveControllerTransport.HOST_PROTOCOL -> clientSession.disconnect()
            ActiveControllerTransport.BLUETOOTH_HID -> bluetoothHidController.disconnectHost()
            ActiveControllerTransport.NONE -> {
                clientSession.disconnect()
                if (hidState.value is BluetoothHidState.Connected) {
                    bluetoothHidController.disconnectHost()
                }
            }
        }
        activeTransport = ActiveControllerTransport.NONE
    }

    override fun onCleared() {
        discoveryManager.stopDiscovery()
        clientSession.disconnect()
        bluetoothHidController.stop()
        super.onCleared()
    }
}
