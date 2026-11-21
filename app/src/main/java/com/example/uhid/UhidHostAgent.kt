package com.example.uhid

import android.annotation.SuppressLint
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import com.example.protocol.PadButton
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileDescriptor
import java.lang.reflect.Method
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * Executed under shell UID (2000) via:
 *   CLASSPATH=<apkPath> app_process / com.example.uhid.UhidHostAgent --parent-pid=<pid>
 *
 * Bridges incoming Wi-Fi UDP packets (forwarded over localhost loopback) directly into
 * up to 4 real Linux kernel HID Gamepad devices (/dev/uhid), with automatic fallbacks to
 * AOSP uinput CLI and Binder IInputManager injection.
 */
object UhidHostAgent {

    private const val LISTEN_PORT = 47852
    private const val VENDOR_ID = 0x1209
    private const val PRODUCT_ID_BASE = 0x4C50

    private interface SlotBackend {
        val type: UhidBackendType
        fun applyState(slot: Int, buttonMask: Int, lx: Byte, ly: Byte, rx: Byte, ry: Byte): Boolean
        fun destroySlot(slot: Int)
        fun activeMask(): Int
        fun closeAll()
    }

    /**
     * Tier 1: Direct /dev/uhid kernel HID gamepad driver (supports full analog sticks + 16 buttons + Hat switch).
     */
    private class KernelUhidBackend : SlotBackend {
        override val type = UhidBackendType.KERNEL_UHID
        private val slotFds = arrayOfNulls<FileDescriptor>(4)
        private val lastPayloads = arrayOfNulls<ByteArray>(4)
        private val drainBuf = ByteArray(4384)

        fun probeAvailable(): Boolean {
            return try {
                val fd = Os.open("/dev/uhid", OsConstants.O_RDWR or OsConstants.O_NONBLOCK, 0)
                runCatching { Os.close(fd) }
                true
            } catch (_: Throwable) {
                false
            }
        }

        private fun ensureSlotOpen(slot: Int): FileDescriptor? {
            val idx = slot - 1
            if (idx !in 0..3) return null
            slotFds[idx]?.let { return it }

            return try {
                val fd = Os.open("/dev/uhid", OsConstants.O_RDWR or OsConstants.O_NONBLOCK, 0)
                val createPacket = UhidProtocol.buildUhidCreate2Event(
                    name = "LinkPad Wireless Pad $slot",
                    phys = "linkpad/udp/slot$slot",
                    uniq = "linkpad-uhid-$slot",
                    vendorId = VENDOR_ID,
                    productId = PRODUCT_ID_BASE + slot
                )
                Os.write(fd, createPacket, 0, createPacket.size)
                drainEvents(fd)
                // Emit neutral initial report so Android InputReader registers axes immediately
                val neutralInput2 = UhidProtocol.buildUhidInput2Event(UhidProtocol.NEUTRAL_PAYLOAD)
                Os.write(fd, neutralInput2, 0, neutralInput2.size)
                slotFds[idx] = fd
                fd
            } catch (_: Throwable) {
                null
            }
        }

        private fun drainEvents(fd: FileDescriptor) {
            for (i in 0 until 8) {
                try {
                    val read = Os.read(fd, drainBuf, 0, drainBuf.size)
                    if (read <= 0) break
                } catch (_: ErrnoException) {
                    break
                } catch (_: Throwable) {
                    break
                }
            }
        }

        override fun applyState(
            slot: Int,
            buttonMask: Int,
            lx: Byte,
            ly: Byte,
            rx: Byte,
            ry: Byte
        ): Boolean {
            val idx = slot - 1
            if (idx !in 0..3) return false
            val fd = ensureSlotOpen(slot) ?: return false
            drainEvents(fd)
            val payload = UhidProtocol.encodeHidReportPayload(buttonMask, lx, ly, rx, ry)
            val prev = lastPayloads[idx]
            if (prev != null && prev.contentEquals(payload)) {
                return true
            }
            val input2 = UhidProtocol.buildUhidInput2Event(payload)
            return try {
                Os.write(fd, input2, 0, input2.size)
                lastPayloads[idx] = payload
                true
            } catch (_: Throwable) {
                destroySlot(slot)
                false
            }
        }

        override fun destroySlot(slot: Int) {
            val idx = slot - 1
            if (idx !in 0..3) return
            lastPayloads[idx] = null
            val fd = slotFds[idx] ?: return
            slotFds[idx] = null
            runCatching {
                val destroy = UhidProtocol.buildUhidDestroyEvent()
                Os.write(fd, destroy, 0, destroy.size)
            }
            runCatching { Os.close(fd) }
        }

        override fun activeMask(): Int {
            var m = 0
            for (i in 0..3) {
                if (slotFds[i] != null) m = m or (1 shl i)
            }
            return m
        }

        override fun closeAll() {
            for (slot in 1..4) destroySlot(slot)
        }
    }

    /**
     * Tier 2: Android 11+ built-in /system/bin/uinput JSON stdin bridge (when /dev/uhid is wrapped by uinput helper).
     */
    private class AospUinputCliBackend : SlotBackend {
        override val type = UhidBackendType.UINPUT_CLI
        private var process: Process? = null
        private var out: BufferedOutputStream? = null
        private val createdSlots = BooleanArray(4)

        fun probeAvailable(): Boolean = File("/system/bin/uinput").canExecute()

        private fun ensureProcess(): BufferedOutputStream? {
            out?.let { existing ->
                if (process != null) return existing
            }
            return try {
                val p = ProcessBuilder("/system/bin/uinput", "-").redirectErrorStream(true).start()
                process = p
                BufferedOutputStream(p.outputStream).also { out = it }
            } catch (_: Throwable) {
                null
            }
        }

        override fun applyState(
            slot: Int,
            buttonMask: Int,
            lx: Byte,
            ly: Byte,
            rx: Byte,
            ry: Byte
        ): Boolean {
            val idx = slot - 1
            if (idx !in 0..3) return false
            val stream = ensureProcess() ?: return false
            return try {
                if (!createdSlots[idx]) {
                    val regJson = buildString {
                        append("{\"id\":$slot,\"type\":\"uinput\",\"command\":\"register\",")
                        append("\"name\":\"LinkPad Wireless Pad $slot\",")
                        append("\"vid\":$VENDOR_ID,\"pid\":${PRODUCT_ID_BASE + slot},\"bus\":\"usb\",")
                        append("\"configuration\":[")
                        append("{\"type\":100,\"data\":[1,3]},") // UI_SET_EVBIT: EV_KEY(1), EV_ABS(3)
                        append("{\"type\":101,\"data\":[304,305,307,308,310,311,312,313,314,315,317,318,544,545,546,547]},")
                        append("{\"type\":103,\"data\":[0,1,2,5,16,17]},") // ABS_X,Y,Z,RZ,HAT0X,HAT0Y
                        append("{\"type\":110,\"data\":{\"axis\":0,\"minimum\":-127,\"maximum\":127}},")
                        append("{\"type\":110,\"data\":{\"axis\":1,\"minimum\":-127,\"maximum\":127}},")
                        append("{\"type\":110,\"data\":{\"axis\":2,\"minimum\":-127,\"maximum\":127}},")
                        append("{\"type\":110,\"data\":{\"axis\":5,\"minimum\":-127,\"maximum\":127}},")
                        append("{\"type\":110,\"data\":{\"axis\":16,\"minimum\":-1,\"maximum\":1}},")
                        append("{\"type\":110,\"data\":{\"axis\":17,\"minimum\":-1,\"maximum\":1}}")
                        append("]}\n")
                    }
                    stream.write(regJson.toByteArray(Charsets.UTF_8))
                    createdSlots[idx] = true
                }

                val hatX = when {
                    (buttonMask and PadButton.RIGHT.bit) != 0 -> 1
                    (buttonMask and PadButton.LEFT.bit) != 0 -> -1
                    else -> 0
                }
                val hatY = when {
                    (buttonMask and PadButton.DOWN.bit) != 0 -> 1
                    (buttonMask and PadButton.UP.bit) != 0 -> -1
                    else -> 0
                }

                val events = mutableListOf<String>()
                fun addKey(btn: PadButton, code: Int) {
                    val v = if ((buttonMask and btn.bit) != 0) 1 else 0
                    events.add("1,$code,$v")
                }
                addKey(PadButton.A, 304)
                addKey(PadButton.B, 305)
                addKey(PadButton.X, 307)
                addKey(PadButton.Y, 308)
                addKey(PadButton.L, 310)
                addKey(PadButton.R, 311)
                addKey(PadButton.L2, 312)
                addKey(PadButton.R2, 313)
                addKey(PadButton.SELECT, 314)
                addKey(PadButton.START, 315)
                addKey(PadButton.L3, 317)
                addKey(PadButton.R3, 318)
                events.add("3,0,${lx.toInt()}")
                events.add("3,1,${ly.toInt()}")
                events.add("3,2,${rx.toInt()}")
                events.add("3,5,${ry.toInt()}")
                events.add("3,16,$hatX")
                events.add("3,17,$hatY")
                events.add("0,0,0")

                val injJson = "{\"id\":$slot,\"type\":\"uinput\",\"command\":\"inject\",\"events\":[${events.joinToString(",")}]}\n"
                stream.write(injJson.toByteArray(Charsets.UTF_8))
                stream.flush()
                true
            } catch (_: Throwable) {
                false
            }
        }

        override fun destroySlot(slot: Int) {
            val idx = slot - 1
            if (idx !in 0..3 || !createdSlots[idx]) return
            createdSlots[idx] = false
            runCatching {
                out?.write("{\"id\":$slot,\"type\":\"uinput\",\"command\":\"unregister\"}\n".toByteArray(Charsets.UTF_8))
                out?.flush()
            }
        }

        override fun activeMask(): Int {
            var m = 0
            for (i in 0..3) if (createdSlots[i]) m = m or (1 shl i)
            return m
        }

        override fun closeAll() {
            for (s in 1..4) destroySlot(s)
            runCatching { out?.close() }
            runCatching { process?.destroy() }
            out = null
            process = null
        }
    }

    /**
     * Tier 3: Hidden Binder IInputManager.injectInputEvent fallback via shell UID 2000.
     */
    private class BinderInputManagerBackend : SlotBackend {
        override val type = UhidBackendType.INPUT_MANAGER
        private val prevMasks = IntArray(4)
        private val activeSlots = BooleanArray(4)
        private var inputMgr: Any? = null
        private var injectMethod: Method? = null

        @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
        fun init(): Boolean {
            return try {
                val smClass = Class.forName("android.os.ServiceManager")
                val getService = smClass.getMethod("getService", String::class.java)
                val binder = getService.invoke(null, "input") as? IBinder ?: return false
                val stubClass = Class.forName("android.hardware.input.IInputManager\$Stub")
                val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
                val mgr = asInterface.invoke(null, binder) ?: return false
                val inject = mgr.javaClass.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
                inputMgr = mgr
                injectMethod = inject
                true
            } catch (_: Throwable) {
                false
            }
        }

        override fun applyState(
            slot: Int,
            buttonMask: Int,
            lx: Byte,
            ly: Byte,
            rx: Byte,
            ry: Byte
        ): Boolean {
            val idx = slot - 1
            if (idx !in 0..3) return false
            val mgr = inputMgr ?: return false
            val inject = injectMethod ?: return false
            activeSlots[idx] = true

            val prev = prevMasks[idx]
            prevMasks[idx] = buttonMask
            val diff = prev xor buttonMask
            val now = SystemClock.uptimeMillis()

            var ok = true
            for (btn in PadButton.entries) {
                if ((diff and btn.bit) != 0) {
                    val pressed = (buttonMask and btn.bit) != 0
                    val keyCode = buttonToAndroidKeyCode(btn)
                    val ev = KeyEvent(
                        now,
                        now,
                        if (pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP,
                        keyCode,
                        0,
                        0,
                        -1,
                        0,
                        0,
                        InputDevice.SOURCE_GAMEPAD
                    )
                    runCatching { inject.invoke(mgr, ev, 0) }.onFailure { ok = false }
                }
            }

            // Inject joystick analog axes
            val props = arrayOf(
                MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_UNKNOWN }
            )
            val coords = arrayOf(
                MotionEvent.PointerCoords().apply {
                    setAxisValue(MotionEvent.AXIS_X, lx / 127f)
                    setAxisValue(MotionEvent.AXIS_Y, ly / 127f)
                    setAxisValue(MotionEvent.AXIS_Z, rx / 127f)
                    setAxisValue(MotionEvent.AXIS_RZ, ry / 127f)
                }
            )
            val motion = MotionEvent.obtain(
                now,
                now,
                MotionEvent.ACTION_MOVE,
                1,
                props,
                coords,
                0,
                0,
                1f,
                1f,
                -1,
                0,
                InputDevice.SOURCE_JOYSTICK,
                0
            )
            runCatching {
                inject.invoke(mgr, motion, 0)
            }.onFailure { ok = false }
            motion.recycle()
            return ok
        }

        override fun destroySlot(slot: Int) {
            val idx = slot - 1
            if (idx !in 0..3) return
            if (prevMasks[idx] != 0) {
                applyState(slot, 0, 0, 0, 0, 0)
            }
            activeSlots[idx] = false
        }

        override fun activeMask(): Int {
            var m = 0
            for (i in 0..3) if (activeSlots[i]) m = m or (1 shl i)
            return m
        }

        override fun closeAll() {
            for (s in 1..4) destroySlot(s)
        }

        private fun buttonToAndroidKeyCode(btn: PadButton): Int = when (btn) {
            PadButton.UP -> KeyEvent.KEYCODE_DPAD_UP
            PadButton.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
            PadButton.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
            PadButton.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
            PadButton.A -> KeyEvent.KEYCODE_BUTTON_A
            PadButton.B -> KeyEvent.KEYCODE_BUTTON_B
            PadButton.X -> KeyEvent.KEYCODE_BUTTON_X
            PadButton.Y -> KeyEvent.KEYCODE_BUTTON_Y
            PadButton.L -> KeyEvent.KEYCODE_BUTTON_L1
            PadButton.R -> KeyEvent.KEYCODE_BUTTON_R1
            PadButton.L2 -> KeyEvent.KEYCODE_BUTTON_L2
            PadButton.R2 -> KeyEvent.KEYCODE_BUTTON_R2
            PadButton.L3 -> KeyEvent.KEYCODE_BUTTON_THUMBL
            PadButton.R3 -> KeyEvent.KEYCODE_BUTTON_THUMBR
            PadButton.START -> KeyEvent.KEYCODE_BUTTON_START
            PadButton.SELECT -> KeyEvent.KEYCODE_BUTTON_SELECT
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        if (Looper.getMainLooper() == null) {
            @Suppress("DEPRECATION")
            Looper.prepareMainLooper()
        }

        val parentPid = args.firstOrNull { it.startsWith("--parent-pid=") }
            ?.substringAfter("=")
            ?.toIntOrNull()
            ?: -1

        val uhidBackend = KernelUhidBackend()
        val uinputCliBackend = AospUinputCliBackend()
        val binderBackend = BinderInputManagerBackend()

        val activeBackend: SlotBackend = when {
            uhidBackend.probeAvailable() -> uhidBackend
            uinputCliBackend.probeAvailable() -> uinputCliBackend
            binderBackend.init() -> binderBackend
            else -> {
                println("LINKPAD_AGENT_ERROR:NO_BACKEND")
                return
            }
        }

        val bindAddr = InetAddress.getByName("127.0.0.1")
        val socket = try {
            DatagramSocket(LISTEN_PORT, bindAddr).apply {
                reuseAddress = true
                soTimeout = 2_000
            }
        } catch (_: Throwable) {
            // Gracefully replace any lingering agent instance on LISTEN_PORT
            runCatching {
                DatagramSocket().use { ctl ->
                    val stop = UhidProtocol.encodePipeState(UhidProtocol.CMD_STOP_AGENT, 0)
                    ctl.send(DatagramPacket(stop, stop.size, bindAddr, LISTEN_PORT))
                }
                Thread.sleep(250L)
            }
            try {
                DatagramSocket(LISTEN_PORT, bindAddr).apply {
                    reuseAddress = true
                    soTimeout = 2_000
                }
            } catch (t: Throwable) {
                println("LINKPAD_AGENT_ERROR:${t.message}")
                activeBackend.closeAll()
                return
            }
        }

        println("LINKPAD_AGENT_READY:${activeBackend.type.name}")
        System.out.flush()

        val buf = ByteArray(64)
        val packet = DatagramPacket(buf, buf.size)
        var idleTicks = 0

        try {
            while (true) {
                if (parentPid > 0 && !File("/proc/$parentPid").exists()) {
                    break
                }
                packet.length = buf.size
                try {
                    socket.receive(packet)
                    idleTicks = 0
                } catch (_: SocketTimeoutException) {
                    idleTicks++
                    if (parentPid <= 0 && idleTicks > 30) break
                    continue
                }

                val decoded = UhidProtocol.decodePipeState(packet.data, packet.length) ?: continue
                when (decoded.cmd) {
                    UhidProtocol.CMD_INPUT -> {
                        activeBackend.applyState(
                            slot = decoded.slot,
                            buttonMask = decoded.buttonMask,
                            lx = decoded.lx,
                            ly = decoded.ly,
                            rx = decoded.rx,
                            ry = decoded.ry
                        )
                    }
                    UhidProtocol.CMD_DESTROY_SLOT -> {
                        activeBackend.destroySlot(decoded.slot)
                    }
                    UhidProtocol.CMD_PING -> {
                        val pong = UhidProtocol.encodePipePong(
                            backend = activeBackend.type,
                            activeMask = activeBackend.activeMask()
                        )
                        runCatching {
                            socket.send(DatagramPacket(pong, pong.size, packet.address, packet.port))
                        }
                    }
                    UhidProtocol.CMD_STOP_AGENT -> {
                        break
                    }
                }
            }
        } finally {
            activeBackend.closeAll()
            runCatching { socket.close() }
        }
    }
}
