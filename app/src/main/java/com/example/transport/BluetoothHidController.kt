package com.example.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.protocol.PadButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.Executors

sealed class BluetoothHidState {
    data object Unsupported : BluetoothHidState()
    data object Idle : BluetoothHidState()
    data object Registering : BluetoothHidState()
    data class Ready(val bondedDevices: List<HidPairedTarget>) : BluetoothHidState()
    data class Connecting(val targetName: String) : BluetoothHidState()
    data class Connected(val targetName: String, val address: String) : BluetoothHidState()
    data class Error(val message: String) : BluetoothHidState()
}

enum class HidTargetRole(val label: String) {
    TV_OR_DISPLAY("TV / Display"),
    COMPUTER("PC / Laptop"),
    PHONE("Phone / Tablet"),
    OTHER("Bluetooth Host")
}

data class HidPairedTarget(
    val name: String,
    val address: String,
    val role: HidTargetRole = HidTargetRole.OTHER
)

data class HidDiscoveredDevice(
    val name: String,
    val address: String,
    val role: HidTargetRole,
    val bonded: Boolean
)

/**
 * Registers the Android device as a standards-compliant Bluetooth HID Hardware Gamepad (API 28+).
 * Target Android TVs, Android phones, Windows PCs, and Web Gamepad API browsers recognize it
 * natively as a genuine physical controller with zero keyboard/Gboard switching or host app needed.
 */
class BluetoothHidController(private val context: Context) {

    private val _state = MutableStateFlow<BluetoothHidState>(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) BluetoothHidState.Idle else BluetoothHidState.Unsupported
    )
    val state: StateFlow<BluetoothHidState> = _state.asStateFlow()

    private val _nearbyDevices = MutableStateFlow<List<HidDiscoveredDevice>>(emptyList())
    val nearbyDevices: StateFlow<List<HidDiscoveredDevice>> = _nearbyDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val executor = Executors.newSingleThreadExecutor()
    private val adapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    @Volatile
    private var hidProxy: BluetoothHidDevice? = null
    @Volatile
    private var connectedHost: BluetoothDevice? = null
    @Volatile
    private var appRegistered = false
    @Volatile
    private var armedByUser = false
    @Volatile
    private var userInitiatedTargetAddress: String? = null
    @Volatile
    private var pendingBondConnectAddress: String? = null
    @Volatile
    private var hostModeBlocking = false
    @Volatile
    private var advertisedDeviceName: String = "LinkPad Controller"
    @Volatile
    private var triedFallbackWithoutQos = false

    private var btReceiver: BroadcastReceiver? = null

    @Volatile
    private var lastReportBytes: ByteArray = DEFAULT_REPORT_BYTES.copyOf()

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    fun setHostModeBlocking(blocking: Boolean) {
        hostModeBlocking = blocking
        if (blocking) {
            stop()
        }
    }

    @SuppressLint("MissingPermission")
    fun startRegistration(deviceName: String = advertisedDeviceName) {
        advertisedDeviceName = deviceName.trim().ifBlank { "LinkPad Controller" }.take(28)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            _state.value = BluetoothHidState.Unsupported
            return
        }
        if (hostModeBlocking) {
            _state.value = BluetoothHidState.Error("Stop Host Mode before using Bluetooth OS Gamepad.")
            return
        }
        if (!LinkPadConstants.hasBluetoothConnectPermission(context)) {
            _state.value = BluetoothHidState.Error("Grant Bluetooth permission to use OS Gamepad mode.")
            return
        }
        val btAdapter = adapter
        if (btAdapter == null || !btAdapter.isEnabled) {
            _state.value = BluetoothHidState.Error("Enable Bluetooth to connect as an OS Gamepad.")
            return
        }

        ensureReceiverRegistered()
        armedByUser = true
        if (hidProxy != null && appRegistered) {
            refreshStateFromProxy()
            val pendingMac = userInitiatedTargetAddress
            if (!pendingMac.isNullOrBlank() && connectedHost == null) {
                connectByMacAddress(pendingMac)
            }
            return
        }

        triedFallbackWithoutQos = false
        _state.value = BluetoothHidState.Registering
        val requested = btAdapter.getProfileProxy(
            context.applicationContext,
            object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    if (profile != BluetoothProfile.HID_DEVICE || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
                    val hid = proxy as BluetoothHidDevice
                    hidProxy = hid
                    registerApp(hid)
                }

                override fun onServiceDisconnected(profile: Int) {
                    if (profile == BluetoothProfile.HID_DEVICE) {
                        hidProxy = null
                        connectedHost = null
                        userInitiatedTargetAddress = null
                        pendingBondConnectAddress = null
                        appRegistered = false
                        _state.value = BluetoothHidState.Idle
                    }
                }
            },
            BluetoothProfile.HID_DEVICE
        )
        if (!requested) {
            _state.value = BluetoothHidState.Error(
                "Android rejected the Bluetooth HID profile request on this device."
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerApp(hid: BluetoothHidDevice) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val sdp = BluetoothHidDeviceAppSdpSettings(
            advertisedDeviceName,
            "LinkPad Hardware Bluetooth Gamepad",
            "LinkPad",
            BluetoothHidDevice.SUBCLASS2_GAMEPAD,
            GAMEPAD_HID_DESCRIPTOR
        )

        val outQos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800,
            9,
            0,
            11250,
            BluetoothHidDeviceAppQosSettings.MAX
        )

        val callback = object : BluetoothHidDevice.Callback() {
            override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                if (!registered) {
                    if (!triedFallbackWithoutQos) {
                        triedFallbackWithoutQos = true
                        val fallbackOk = runCatching {
                            hid.registerApp(sdp, null, null, executor, this)
                        }.getOrDefault(false)
                        if (fallbackOk) return
                    }
                    appRegistered = false
                    connectedHost = null
                    userInitiatedTargetAddress = null
                    pendingBondConnectAddress = null
                    _state.value = BluetoothHidState.Error(
                        "Bluetooth HID Gamepad profile unavailable on this device. Toggle Bluetooth off/on or tap Make Discoverable."
                    )
                } else {
                    appRegistered = true
                    if (pluggedDevice != null && shouldAcceptConnection(pluggedDevice)) {
                        connectedHost = pluggedDevice
                        sendNeutralReportAsync(hid, pluggedDevice)
                    }
                    refreshStateFromProxy()
                    val pendingMac = pendingBondConnectAddress ?: userInitiatedTargetAddress
                    if (!pendingMac.isNullOrBlank() && connectedHost == null) {
                        connectByMacAddress(pendingMac)
                    }
                }
            }

            override fun onConnectionStateChanged(device: BluetoothDevice?, connState: Int) {
                when (connState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        if (device == null || !shouldAcceptConnection(device)) {
                            if (device != null && LinkPadConstants.hasBluetoothConnectPermission(context)) {
                                executor.execute { runCatching { hid.disconnect(device) } }
                            }
                            return
                        }
                        connectedHost = device
                        pendingBondConnectAddress = null
                        lastReportBytes = DEFAULT_REPORT_BYTES.copyOf()
                        sendNeutralReportAsync(hid, device)
                        val name = runCatching { device.name }.getOrNull().orEmpty()
                            .ifBlank { device.address ?: "Bluetooth Host" }
                        _state.value = BluetoothHidState.Connected(
                            targetName = name,
                            address = device.address.orEmpty()
                        )
                    }
                    BluetoothProfile.STATE_CONNECTING -> {
                        if (device == null || !shouldAcceptConnection(device)) return
                        val name = runCatching { device.name }.getOrNull().orEmpty()
                            .ifBlank { device.address ?: "Bluetooth Host" }
                        _state.value = BluetoothHidState.Connecting(name)
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        if (connectedHost?.address == device?.address) {
                            connectedHost = null
                            userInitiatedTargetAddress = null
                            lastReportBytes = DEFAULT_REPORT_BYTES.copyOf()
                        }
                        refreshStateFromProxy()
                    }
                }
            }

            override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
                if (device == null) return
                val requestedId = id.toInt() and 0xFF
                if (type == BluetoothHidDevice.REPORT_TYPE_INPUT &&
                    (requestedId == REPORT_ID || requestedId == 0)
                ) {
                    val reply = lastReportBytes.copyOf()
                    executor.execute {
                        runCatching {
                            hid.replyReport(
                                device,
                                BluetoothHidDevice.REPORT_TYPE_INPUT,
                                REPORT_ID.toByte(),
                                reply
                            )
                        }
                    }
                } else {
                    executor.execute {
                        runCatching {
                            hid.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
                        }
                    }
                }
            }

            override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
                // Report ID 1 is input-only; acknowledge gracefully
            }

            override fun onSetProtocol(device: BluetoothDevice?, protocol: Byte) {
                // Keep report protocol active
            }
        }

        // Some vendor Bluetooth stacks (e.g. MIUI / ColorOS / older Samsung firmware) reject non-null QoS;
        // try strict gaming QoS first and automatically retry with default (null, null) QoS if rejected.
        var ok = runCatching {
            hid.registerApp(sdp, null, outQos, executor, callback)
        }.getOrDefault(false)

        if (!ok && !triedFallbackWithoutQos) {
            triedFallbackWithoutQos = true
            ok = runCatching {
                hid.registerApp(sdp, null, null, executor, callback)
            }.getOrDefault(false)
        }

        if (!ok) {
            _state.value = BluetoothHidState.Error(
                "Could not register Bluetooth HID profile on this device."
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun shouldAcceptConnection(device: BluetoothDevice): Boolean {
        if (hostModeBlocking || !armedByUser) return false
        val addr = device.address
        if (addr != null && (addr == userInitiatedTargetAddress || addr == pendingBondConnectAddress)) {
            return true
        }
        val role = classifyDeviceRole(device)
        return role != HidTargetRole.PHONE
    }

    @SuppressLint("MissingPermission")
    fun refreshStateFromProxy() {
        if (hostModeBlocking) return
        val hasConnPerm = LinkPadConstants.hasBluetoothConnectPermission(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && hasConnPerm && appRegistered) {
            val hid = hidProxy
            if (hid != null && connectedHost == null) {
                val activeDevices = runCatching {
                    hid.getDevicesMatchingConnectionStates(
                        intArrayOf(BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_CONNECTING)
                    )
                }.getOrDefault(emptyList())

                val connected = activeDevices.firstOrNull { dev ->
                    hid.getConnectionState(dev) == BluetoothProfile.STATE_CONNECTED && shouldAcceptConnection(dev)
                }
                if (connected != null) {
                    connectedHost = connected
                    lastReportBytes = DEFAULT_REPORT_BYTES.copyOf()
                    sendNeutralReportAsync(hid, connected)
                    val name = runCatching { connected.name }.getOrNull().orEmpty()
                        .ifBlank { connected.address }
                    _state.value = BluetoothHidState.Connected(name, connected.address)
                    return
                }

                val connecting = activeDevices.firstOrNull { dev ->
                    hid.getConnectionState(dev) == BluetoothProfile.STATE_CONNECTING && shouldAcceptConnection(dev)
                }
                if (connecting != null) {
                    val name = runCatching { connecting.name }.getOrNull().orEmpty()
                        .ifBlank { connecting.address }
                    _state.value = BluetoothHidState.Connecting(name)
                    return
                }
            }
        }

        val currentHost = connectedHost
        if (currentHost != null) {
            val name = runCatching { currentHost.name }.getOrNull().orEmpty()
                .ifBlank { currentHost.address }
            _state.value = BluetoothHidState.Connected(name, currentHost.address)
            return
        }

        val targets = if (hasConnPerm) {
            runCatching {
                adapter?.bondedDevices.orEmpty().map { dev ->
                    HidPairedTarget(
                        name = dev.name?.takeIf { it.isNotBlank() } ?: dev.address,
                        address = dev.address,
                        role = classifyDeviceRole(dev)
                    )
                }.sortedWith(
                    compareBy<HidPairedTarget> { it.role == HidTargetRole.PHONE }
                        .thenBy { it.name.lowercase(Locale.US) }
                )
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        _state.value = if (appRegistered) {
            BluetoothHidState.Ready(targets)
        } else {
            BluetoothHidState.Idle
        }
    }

    @SuppressLint("MissingPermission")
    fun startNearbyScan() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        if (!LinkPadConstants.hasBluetoothConnectPermission(context) ||
            !LinkPadConstants.hasBluetoothScanPermission(context)
        ) {
            _state.value = BluetoothHidState.Error("Grant Bluetooth permissions to scan nearby TVs or PCs.")
            return
        }
        val btAdapter = adapter ?: return
        if (!btAdapter.isEnabled) {
            _state.value = BluetoothHidState.Error("Enable Bluetooth before scanning.")
            return
        }
        ensureReceiverRegistered()
        _nearbyDevices.value = emptyList()
        runCatching {
            if (btAdapter.isDiscovering) btAdapter.cancelDiscovery()
            val started = btAdapter.startDiscovery()
            _isScanning.value = started
        }.onFailure {
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopNearbyScan() {
        _isScanning.value = false
        if (LinkPadConstants.hasBluetoothScanPermission(context)) {
            runCatching {
                if (adapter?.isDiscovering == true) adapter?.cancelDiscovery()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectByMacAddress(rawMac: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        if (hostModeBlocking) {
            _state.value = BluetoothHidState.Error("Stop Host Mode before connecting Bluetooth OS Gamepad.")
            return
        }
        if (!LinkPadConstants.hasBluetoothConnectPermission(context)) {
            _state.value = BluetoothHidState.Error("Grant Bluetooth permission first.")
            return
        }
        val normalized = normalizeMacAddress(rawMac)
        if (!BluetoothAdapter.checkBluetoothAddress(normalized)) {
            _state.value = BluetoothHidState.Error(
                "Invalid Bluetooth MAC address. Use format AA:BB:CC:DD:EE:FF."
            )
            return
        }
        val btAdapter = adapter
        if (btAdapter == null || !btAdapter.isEnabled) {
            _state.value = BluetoothHidState.Error("Enable Bluetooth first.")
            return
        }

        ensureReceiverRegistered()
        stopNearbyScan()
        armedByUser = true
        userInitiatedTargetAddress = normalized

        val hid = hidProxy
        if (hid == null || !appRegistered) {
            startRegistration(advertisedDeviceName)
            return
        }

        runCatching {
            val device = btAdapter.getRemoteDevice(normalized)
            val name = device.name?.takeIf { it.isNotBlank() } ?: normalized
            when (device.bondState) {
                BluetoothDevice.BOND_BONDED -> {
                    pendingBondConnectAddress = null
                    connectToTarget(normalized)
                }
                BluetoothDevice.BOND_BONDING -> {
                    pendingBondConnectAddress = normalized
                    _state.value = BluetoothHidState.Connecting("Pairing with $name...")
                }
                else -> {
                    pendingBondConnectAddress = normalized
                    _state.value = BluetoothHidState.Connecting("Pairing with $name...")
                    val bondStarted = device.createBond()
                    if (!bondStarted) {
                        pendingBondConnectAddress = null
                        userInitiatedTargetAddress = null
                        _state.value = BluetoothHidState.Error(
                            "Could not initiate bonding with $normalized. Tap Make Discoverable and pair from the TV/PC Bluetooth menu."
                        )
                    }
                }
            }
        }.onFailure { err ->
            pendingBondConnectAddress = null
            userInitiatedTargetAddress = null
            _state.value = BluetoothHidState.Error(err.message ?: "Bluetooth MAC connect failed.")
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToTarget(address: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        if (hostModeBlocking) {
            _state.value = BluetoothHidState.Error("Stop Host Mode before connecting Bluetooth OS Gamepad.")
            return
        }
        val hid = hidProxy
        val btAdapter = adapter
        if (hid == null || btAdapter == null || !appRegistered) {
            userInitiatedTargetAddress = address
            startRegistration(advertisedDeviceName)
            return
        }
        if (!LinkPadConstants.hasBluetoothConnectPermission(context)) return

        runCatching {
            stopNearbyScan()
            val device = btAdapter.getRemoteDevice(address)
            val name = device.name?.takeIf { it.isNotBlank() } ?: address
            armedByUser = true
            userInitiatedTargetAddress = address
            _state.value = BluetoothHidState.Connecting(name)
            val started = hid.connect(device)
            if (!started) {
                userInitiatedTargetAddress = null
                _state.value = BluetoothHidState.Error(
                    "Could not initiate HID link to $name. Tap Make Discoverable and select ${advertisedDeviceName} on your TV/PC."
                )
            }
        }.onFailure { err ->
            userInitiatedTargetAddress = null
            _state.value = BluetoothHidState.Error(err.message ?: "Bluetooth HID connect failed.")
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnectHost() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val hid = hidProxy
        val host = connectedHost
        userInitiatedTargetAddress = null
        pendingBondConnectAddress = null
        connectedHost = null
        lastReportBytes = DEFAULT_REPORT_BYTES.copyOf()
        if (hid != null && host != null && LinkPadConstants.hasBluetoothConnectPermission(context)) {
            executor.execute {
                runCatching {
                    hid.sendReport(host, REPORT_ID, DEFAULT_REPORT_BYTES)
                    hid.disconnect(host)
                }
            }
        }
        refreshStateFromProxy()
    }

    @SuppressLint("MissingPermission")
    fun sendGamepadState(
        buttonMask: Int,
        lx: Byte,
        ly: Byte,
        rx: Byte,
        ry: Byte
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val hid = hidProxy ?: return
        val host = connectedHost ?: return
        if (!LinkPadConstants.hasBluetoothConnectPermission(context)) return

        val report = encodeHidReportPayload(buttonMask, lx, ly, rx, ry)
        if (lastReportBytes.contentEquals(report)) return
        lastReportBytes = report
        executor.execute {
            runCatching { hid.sendReport(host, REPORT_ID, report) }
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        armedByUser = false
        userInitiatedTargetAddress = null
        pendingBondConnectAddress = null
        stopNearbyScan()
        unregisterReceiver()
        val prevHost = connectedHost
        connectedHost = null
        lastReportBytes = DEFAULT_REPORT_BYTES.copyOf()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val hid = hidProxy
            if (hid != null) {
                executor.execute {
                    runCatching {
                        if (prevHost != null) {
                            hid.sendReport(prevHost, REPORT_ID, DEFAULT_REPORT_BYTES)
                            hid.disconnect(prevHost)
                        }
                        if (appRegistered) hid.unregisterApp()
                        adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid)
                    }
                }
            }
        }
        hidProxy = null
        appRegistered = false
        _state.value = if (isSupported()) BluetoothHidState.Idle else BluetoothHidState.Unsupported
    }

    private fun ensureReceiverRegistered() {
        if (btReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = getParcelableDevice(intent) ?: return
                        val addr = device.address ?: return
                        val name = runCatching { device.name }.getOrNull().orEmpty().ifBlank { addr }
                        val role = classifyDeviceRole(device)
                        val bonded = runCatching { device.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false)
                        val updated = _nearbyDevices.value.filterNot { it.address == addr } +
                            HidDiscoveredDevice(name = name, address = addr, role = role, bonded = bonded)
                        _nearbyDevices.value = updated.sortedWith(
                            compareBy<HidDiscoveredDevice> { it.role == HidTargetRole.PHONE }
                                .thenBy { it.name.lowercase(Locale.US) }
                        )
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        _isScanning.value = false
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val device = getParcelableDevice(intent) ?: return
                        val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                        val prevState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)
                        val addr = device.address ?: return
                        if (bondState == BluetoothDevice.BOND_BONDED) {
                            _nearbyDevices.value = _nearbyDevices.value.map {
                                if (it.address == addr) it.copy(bonded = true) else it
                            }
                            if (pendingBondConnectAddress == addr) {
                                pendingBondConnectAddress = null
                                connectToTarget(addr)
                            } else {
                                refreshStateFromProxy()
                            }
                        } else if (bondState == BluetoothDevice.BOND_NONE &&
                            prevState == BluetoothDevice.BOND_BONDING &&
                            pendingBondConnectAddress == addr
                        ) {
                            pendingBondConnectAddress = null
                            userInitiatedTargetAddress = null
                            val name = runCatching { device.name }.getOrNull().orEmpty().ifBlank { addr }
                            _state.value = BluetoothHidState.Error(
                                "Pairing with $name did not complete. Make sure the target TV/PC accepted pairing."
                            )
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        runCatching {
            ContextCompat.registerReceiver(
                context.applicationContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            btReceiver = receiver
        }
    }

    private fun unregisterReceiver() {
        val receiver = btReceiver ?: return
        runCatching { context.applicationContext.unregisterReceiver(receiver) }
        btReceiver = null
    }

    private fun getParcelableDevice(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendNeutralReportAsync(hid: BluetoothHidDevice, device: BluetoothDevice) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        executor.execute {
            runCatching { hid.sendReport(device, REPORT_ID, DEFAULT_REPORT_BYTES) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun classifyDeviceRole(device: BluetoothDevice): HidTargetRole {
        val major = runCatching { device.bluetoothClass?.majorDeviceClass }.getOrNull()
        return when (major) {
            BluetoothClass.Device.Major.AUDIO_VIDEO -> HidTargetRole.TV_OR_DISPLAY
            BluetoothClass.Device.Major.COMPUTER -> HidTargetRole.COMPUTER
            BluetoothClass.Device.Major.PHONE -> HidTargetRole.PHONE
            else -> HidTargetRole.OTHER
        }
    }

    companion object {
        const val REPORT_ID = 1

        // Neutral 7-byte payload (buttons = 0, hat = 0, axes = 128 center)
        val DEFAULT_REPORT_BYTES = byteArrayOf(0, 0, 0, 128.toByte(), 128.toByte(), 128.toByte(), 128.toByte())

        fun normalizeMacAddress(input: String): String {
            val cleaned = input.trim().uppercase(Locale.US).replace("-", ":")
            val hexOnly = cleaned.replace(":", "")
            return if (hexOnly.length == 12 && hexOnly.all { it in '0'..'9' || it in 'A'..'F' }) {
                hexOnly.chunked(2).joinToString(":")
            } else {
                cleaned
            }
        }

        /**
         * Maps internal [PadButton] bits into Linux/Android standard BTN_GAMEPAD HID usage indices (1..16):
         * Usage 1=A (BTN_SOUTH), 2=B (BTN_EAST), 4=X (BTN_NORTH), 5=Y (BTN_WEST),
         * 7=L1, 8=R1, 9=L2, 10=R2, 11=SELECT, 12=START, 14=THUMBL, 15=THUMBR.
         */
        fun encodeHidReportPayload(
            buttonMask: Int,
            lx: Byte,
            ly: Byte,
            rx: Byte,
            ry: Byte
        ): ByteArray {
            var hidButtons = 0
            if ((buttonMask and PadButton.A.bit) != 0) hidButtons = hidButtons or (1 shl 0)
            if ((buttonMask and PadButton.B.bit) != 0) hidButtons = hidButtons or (1 shl 1)
            if ((buttonMask and PadButton.X.bit) != 0) hidButtons = hidButtons or (1 shl 3)
            if ((buttonMask and PadButton.Y.bit) != 0) hidButtons = hidButtons or (1 shl 4)
            if ((buttonMask and PadButton.L.bit) != 0) hidButtons = hidButtons or (1 shl 6)
            if ((buttonMask and PadButton.R.bit) != 0) hidButtons = hidButtons or (1 shl 7)
            if ((buttonMask and PadButton.L2.bit) != 0) hidButtons = hidButtons or (1 shl 8)
            if ((buttonMask and PadButton.R2.bit) != 0) hidButtons = hidButtons or (1 shl 9)
            if ((buttonMask and PadButton.SELECT.bit) != 0) hidButtons = hidButtons or (1 shl 10)
            if ((buttonMask and PadButton.START.bit) != 0) hidButtons = hidButtons or (1 shl 11)
            if ((buttonMask and PadButton.L3.bit) != 0) hidButtons = hidButtons or (1 shl 13)
            if ((buttonMask and PadButton.R3.bit) != 0) hidButtons = hidButtons or (1 shl 14)

            val hat = dpadMaskToHat(buttonMask)
            return byteArrayOf(
                (hidButtons and 0xFF).toByte(),
                ((hidButtons ushr 8) and 0xFF).toByte(),
                hat.toByte(),
                (lx.toInt() + 128).coerceIn(0, 255).toByte(),
                (ly.toInt() + 128).coerceIn(0, 255).toByte(),
                (rx.toInt() + 128).coerceIn(0, 255).toByte(),
                (ry.toInt() + 128).coerceIn(0, 255).toByte()
            )
        }

        private fun dpadMaskToHat(mask: Int): Int {
            val up = (mask and PadButton.UP.bit) != 0
            val down = (mask and PadButton.DOWN.bit) != 0
            val left = (mask and PadButton.LEFT.bit) != 0
            val right = (mask and PadButton.RIGHT.bit) != 0
            return when {
                up && right -> 2
                down && right -> 4
                down && left -> 6
                up && left -> 8
                up -> 1
                right -> 3
                down -> 5
                left -> 7
                else -> 0
            }
        }

        // Standards-compliant 7-byte HID Gamepad Report Descriptor with explicit Report ID (1).
        // Physical Minimum/Maximum and Unit are explicitly reset before the 4 analog stick axes.
        val GAMEPAD_HID_DESCRIPTOR = byteArrayOf(
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
            0x09.toByte(), 0x05.toByte(), // Usage (Gamepad)
            0xA1.toByte(), 0x01.toByte(), // Collection (Application)
            0x85.toByte(), 0x01.toByte(), //   Report ID (1)

            // 16 Buttons (2 bytes)
            0x05.toByte(), 0x09.toByte(), //   Usage Page (Button)
            0x19.toByte(), 0x01.toByte(), //   Usage Minimum (Button 1)
            0x29.toByte(), 0x10.toByte(), //   Usage Maximum (Button 16)
            0x15.toByte(), 0x00.toByte(), //   Logical Minimum (0)
            0x25.toByte(), 0x01.toByte(), //   Logical Maximum (1)
            0x75.toByte(), 0x01.toByte(), //   Report Size (1)
            0x95.toByte(), 0x10.toByte(), //   Report Count (16)
            0x81.toByte(), 0x02.toByte(), //   Input (Data, Var, Abs)

            // Hat Switch (4 bits) + 4-bit padding (1 byte)
            0x05.toByte(), 0x01.toByte(), //   Usage Page (Generic Desktop)
            0x09.toByte(), 0x39.toByte(), //   Usage (Hat switch)
            0x15.toByte(), 0x01.toByte(), //   Logical Minimum (1)
            0x25.toByte(), 0x08.toByte(), //   Logical Maximum (8)
            0x35.toByte(), 0x00.toByte(), //   Physical Minimum (0)
            0x46.toByte(), 0x3B.toByte(), 0x01.toByte(), // Physical Maximum (315)
            0x65.toByte(), 0x14.toByte(), //   Unit (Eng Rot: Angular Pos)
            0x75.toByte(), 0x04.toByte(), //   Report Size (4)
            0x95.toByte(), 0x01.toByte(), //   Report Count (1)
            0x81.toByte(), 0x42.toByte(), //   Input (Data, Var, Abs, Null)
            0x65.toByte(), 0x00.toByte(), //   Unit (None)
            0x35.toByte(), 0x00.toByte(), //   Physical Minimum (0 -> reset)
            0x45.toByte(), 0x00.toByte(), //   Physical Maximum (0 -> reset)
            0x75.toByte(), 0x04.toByte(), //   Report Size (4)
            0x95.toByte(), 0x01.toByte(), //   Report Count (1)
            0x81.toByte(), 0x03.toByte(), //   Input (Const, Var, Abs)

            // 4 Analog Axes inside Physical Collection: LX (X), LY (Y), RX (Z), RY (Rz) (4 bytes)
            0x05.toByte(), 0x01.toByte(), //   Usage Page (Generic Desktop)
            0x09.toByte(), 0x01.toByte(), //   Usage (Pointer)
            0xA1.toByte(), 0x00.toByte(), //   Collection (Physical)
            0x09.toByte(), 0x30.toByte(), //     Usage (X)
            0x09.toByte(), 0x31.toByte(), //     Usage (Y)
            0x09.toByte(), 0x32.toByte(), //     Usage (Z)
            0x09.toByte(), 0x35.toByte(), //     Usage (Rz)
            0x15.toByte(), 0x00.toByte(), //     Logical Minimum (0)
            0x26.toByte(), 0xFF.toByte(), 0x00.toByte(), // Logical Maximum (255)
            0x75.toByte(), 0x08.toByte(), //     Report Size (8)
            0x95.toByte(), 0x04.toByte(), //     Report Count (4)
            0x81.toByte(), 0x02.toByte(), //     Input (Data, Var, Abs)
            0xC0.toByte(),                //   End Collection (Physical)

            0xC0.toByte()                 // End Collection (Application)
        )
    }
}
