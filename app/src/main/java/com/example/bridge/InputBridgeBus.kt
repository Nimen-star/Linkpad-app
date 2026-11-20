package com.example.bridge

import com.example.protocol.AnalogAxes
import com.example.protocol.KeymapProfile
import com.example.protocol.PadButton
import com.example.protocol.PlayerInputTransition
import com.example.protocol.PlayerSlotState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object InputBridgeBus {

    private val _slots = MutableStateFlow(defaultSlots())
    val slots: StateFlow<List<PlayerSlotState>> = _slots.asStateFlow()

    private val _hostActive = MutableStateFlow(false)
    val hostActive: StateFlow<Boolean> = _hostActive.asStateFlow()

    private val _pairingCode = MutableStateFlow("")
    val pairingCode: StateFlow<String> = _pairingCode.asStateFlow()

    private val _localIpAddress = MutableStateFlow("")
    val localIpAddress: StateFlow<String> = _localIpAddress.asStateFlow()

    private val _bluetoothHostingActive = MutableStateFlow(false)
    val bluetoothHostingActive: StateFlow<Boolean> = _bluetoothHostingActive.asStateFlow()

    private val _packetsPerSecond = MutableStateFlow(0)
    val packetsPerSecond: StateFlow<Int> = _packetsPerSecond.asStateFlow()

    private val _p1AnalogAxes = MutableStateFlow(AnalogAxes())
    val p1AnalogAxes: StateFlow<AnalogAxes> = _p1AnalogAxes.asStateFlow()

    private val _inputTransitions = MutableSharedFlow<PlayerInputTransition>(
        replay = 0,
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val inputTransitions: SharedFlow<PlayerInputTransition> = _inputTransitions.asSharedFlow()

    fun updateSlots(updated: List<PlayerSlotState>) {
        _slots.value = updated
    }

    fun updateP1AnalogAxes(axes: AnalogAxes) {
        if (_p1AnalogAxes.value != axes) {
            _p1AnalogAxes.value = axes
        }
    }

    fun setHostActive(active: Boolean, code: String = _pairingCode.value) {
        _hostActive.value = active
        _pairingCode.value = if (active) code else ""
        if (!active) {
            _packetsPerSecond.value = 0
            _bluetoothHostingActive.value = false
            _p1AnalogAxes.value = AnalogAxes()
            _slots.value = defaultSlots()
        }
    }

    fun setLocalIpAddress(ip: String) {
        _localIpAddress.value = ip
    }

    fun setBluetoothHostingActive(active: Boolean) {
        _bluetoothHostingActive.value = active
    }

    fun setPacketsPerSecond(pps: Int) {
        _packetsPerSecond.value = pps
    }

    fun emitMaskChange(
        slot: Int,
        previousMask: Int,
        newMask: Int,
        keymapProfile: KeymapProfile
    ) {
        val diff = previousMask xor newMask
        if (diff == 0) return
        for (button in PadButton.entries) {
            val bit = button.bit
            if ((diff and bit) != 0) {
                val pressed = (newMask and bit) != 0
                _inputTransitions.tryEmit(
                    PlayerInputTransition(
                        slot = slot,
                        button = button,
                        pressed = pressed,
                        keymapProfile = keymapProfile
                    )
                )
            }
        }
    }

    fun defaultSlots(): List<PlayerSlotState> = (1..4).map { PlayerSlotState(slot = it) }
}
