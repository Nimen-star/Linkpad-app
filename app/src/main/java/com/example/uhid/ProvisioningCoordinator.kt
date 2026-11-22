package com.example.uhid

import android.content.Context
import com.example.BuildConfig
import com.example.transport.LinkPadConstants
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong

enum class ProvisionStage(val stepIndex: Int, val label: String) {
    IDLE(0, "Idle"),
    CONNECTING(1, "Connecting to Target"),
    WAIT_TRUST(1, "Approve Debugging Prompt"),
    CHECKING_APK(2, "Checking LinkPad APK"),
    INSTALLING_APK(2, "Installing LinkPad APK"),
    UNLOCKING_WIFI_ADB(3, "Unlocking Wi-Fi ADB"),
    STARTING_UHID(4, "Starting Host & UHID Engine"),
    VERIFYING(4, "Verifying Hardware Bridge"),
    DONE(4, "Connecting Gamepad..."),
    ERROR(0, "Needs Attention")
}

data class ProvisionUiState(
    val runId: Long = 0L,
    val active: Boolean = false,
    val transportLabel: String = "",
    val targetLabel: String = "",
    val stage: ProvisionStage = ProvisionStage.IDLE,
    val statusText: String = "",
    val apkProgress: Float = 0f,
    val targetIp: String? = null,
    val targetPort: Int = LinkPadConstants.HOST_UDP_PORT,
    val autoConnectReady: Boolean = false,
    val errorMessage: String? = null
)

object ProvisionParsers {
    fun parseInstalledVersionCode(dumpsysOutput: String): Int? {
        val regex = Regex("""versionCode=(\d+)""")
        return regex.find(dumpsysOutput)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    fun parseWlanIpv4(ipAddrOutput: String): String? {
        val regex = Regex("""inet\s+(\d+\.\d+\.\d+\.\d+)/\d+""")
        return regex.find(ipAddrOutput)?.groupValues?.getOrNull(1)
            ?.takeUnless { it == "127.0.0.1" }
    }

    fun parseTargetModel(probeOutput: String): String? {
        val line = probeOutput.lineSequence().firstOrNull { it.startsWith("MODEL=") } ?: return null
        return line.substringAfter("MODEL=").trim().takeIf { it.isNotBlank() }
    }
}

/**
 * In-app 1-Click TV & Phone Provisioning Engine.
 * Runs Install APK -> Unlock Wi-Fi ADB -> Start Persistent UHID Engine -> Auto-Launch Host
 * with pre-trusted controller ID -> Auto-Connect Controller over Wi-Fi UDP.
 */
class ProvisioningCoordinator(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runCounter = AtomicLong(0L)

    private val _state = MutableStateFlow(ProvisionUiState())
    val state: StateFlow<ProvisionUiState> = _state.asStateFlow()

    fun dismiss() {
        _state.value = ProvisionUiState()
    }

    fun startWifiProvisioning(
        targetIp: String,
        connectPort: Int = 5555,
        displayName: String? = null,
        controllerClientId: Int = 0
    ) {
        val cleanIp = targetIp.trim()
        if (cleanIp.isEmpty()) return
        val runId = runCounter.incrementAndGet()

        scope.launch {
            val label = displayName?.takeIf { it.isNotBlank() } ?: cleanIp
            _state.value = ProvisionUiState(
                runId = runId,
                active = true,
                transportLabel = if (connectPort == 5555) "WI-FI 1-CLICK · :5555" else "WI-FI TLS · :$connectPort",
                targetLabel = label,
                targetIp = cleanIp,
                stage = ProvisionStage.CONNECTING,
                statusText = "Connecting wirelessly to $cleanIp:$connectPort..."
            )

            var mgr: LinkPadAdbConnectionManager? = null
            try {
                if (!isTcpPortOpen(cleanIp, connectPort, 1_100)) {
                    fail(
                        "Port $connectPort is closed on $cleanIp. Turn ON 'ADB Debugging' on your Fire TV/Android TV or Wireless Debugging on your phone."
                    )
                    return@launch
                }

                mgr = LinkPadAdbConnectionManager(appContext)
                _state.value = _state.value.copy(
                    stage = ProvisionStage.WAIT_TRUST,
                    statusText = "If prompted on $label, check 'Always allow' and tap Allow."
                )

                val connected = runCatching { mgr.connect(cleanIp, connectPort) }.getOrDefault(false)
                if (!connected) {
                    fail("Could not authenticate with $cleanIp:$connectPort. Accept the debugging popup on $label and tap Retry.")
                    return@launch
                }

                executeRemoteWifiPipeline(
                    mgr = mgr,
                    cleanIp = cleanIp,
                    connectPort = connectPort,
                    fallbackLabel = label,
                    controllerClientId = controllerClientId
                )
            } catch (t: Throwable) {
                fail(t.message ?: "Wireless setup failed.")
            } finally {
                runCatching { mgr?.disconnect() }
            }
        }
    }

    fun startWirelessPairAndProvision(
        targetIp: String,
        pairPort: Int,
        pairingCode: String,
        connectPort: Int? = null,
        displayName: String? = null,
        controllerClientId: Int = 0
    ) {
        val cleanIp = targetIp.trim()
        val cleanCode = pairingCode.filter { it.isDigit() }
        if (cleanIp.isEmpty() || cleanCode.length != 6 || pairPort !in 1..65535) return
        val runId = runCounter.incrementAndGet()

        scope.launch {
            val label = displayName?.takeIf { it.isNotBlank() } ?: cleanIp
            _state.value = ProvisionUiState(
                runId = runId,
                active = true,
                transportLabel = "WIRELESS PIN PAIR",
                targetLabel = label,
                targetIp = cleanIp,
                stage = ProvisionStage.WAIT_TRUST,
                statusText = "Pairing wirelessly with $cleanIp:$pairPort..."
            )

            var mgr: LinkPadAdbConnectionManager? = null
            try {
                mgr = LinkPadAdbConnectionManager(appContext)
                val paired = runCatching { mgr.pair(cleanIp, pairPort, cleanCode) }.getOrDefault(false)
                if (!paired) {
                    fail("Wireless pairing failed on $cleanIp:$pairPort. Check the 6-digit PIN and port.")
                    return@launch
                }

                delay(400L)
                _state.value = _state.value.copy(
                    stage = ProvisionStage.CONNECTING,
                    statusText = "Paired! Opening wireless ADB session on $label..."
                )

                val candidatePort = when {
                    connectPort != null && isTcpPortOpen(cleanIp, connectPort, 900) -> connectPort
                    isTcpPortOpen(cleanIp, 5555, 900) -> 5555
                    else -> null
                }

                var connected = false
                if (candidatePort != null) {
                    connected = runCatching { mgr.connect(cleanIp, candidatePort) }.getOrDefault(false)
                }
                if (!connected) {
                    connected = runCatching { mgr.connectTls(appContext, 3_500L) }.getOrDefault(false)
                }
                if (!connected) {
                    fail("Paired cleanly, but could not reach ADB connect port on $cleanIp. Re-scan after Wireless Debugging settles.")
                    return@launch
                }

                executeRemoteWifiPipeline(
                    mgr = mgr,
                    cleanIp = cleanIp,
                    connectPort = candidatePort ?: 5555,
                    fallbackLabel = label,
                    controllerClientId = controllerClientId
                )
            } catch (t: Throwable) {
                fail(t.message ?: "Wireless PIN pairing failed.")
            } finally {
                runCatching { mgr?.disconnect() }
            }
        }
    }

    private fun executeRemoteWifiPipeline(
        mgr: LinkPadAdbConnectionManager,
        cleanIp: String,
        connectPort: Int,
        fallbackLabel: String,
        controllerClientId: Int
    ) {
        val pkg = appContext.packageName
        _state.value = _state.value.copy(
            stage = ProvisionStage.CHECKING_APK,
            statusText = "Checking LinkPad version on $fallbackLabel..."
        )
        val probeOut = runWifiShell(
            mgr,
            "echo MODEL=\$(getprop ro.product.model); pm path $pkg; dumpsys package $pkg | grep versionCode"
        )
        val resolvedLabel = ProvisionParsers.parseTargetModel(probeOut) ?: fallbackLabel
        val installedCode = ProvisionParsers.parseInstalledVersionCode(probeOut)
        val apkMissing = !probeOut.contains("package:")

        if (apkMissing || installedCode == null || installedCode < BuildConfig.VERSION_CODE) {
            val localApk = File(appContext.applicationInfo.sourceDir)
            if (!localApk.exists()) {
                fail("Could not locate local APK package to transfer.")
                return
            }
            _state.value = _state.value.copy(
                targetLabel = resolvedLabel,
                stage = ProvisionStage.INSTALLING_APK,
                statusText = "Installing LinkPad v${BuildConfig.VERSION_NAME} onto $resolvedLabel...",
                apkProgress = 0f
            )
            val remoteApk = "/data/local/tmp/linkpad-setup.apk"
            val pushed = pushFileViaWifiSync(mgr, localApk, remoteApk) { progress ->
                _state.value = _state.value.copy(apkProgress = progress)
            }
            if (!pushed) {
                fail("Failed transferring APK to $resolvedLabel over Wi-Fi ADB.")
                return
            }
            val installOut = runWifiShell(mgr, "pm install -r -d $remoteApk; rm -f $remoteApk")
            if (!installOut.contains("Success", ignoreCase = true)) {
                fail("APK install failed on $resolvedLabel: ${installOut.trim().take(120)}")
                return
            }
        }

        _state.value = _state.value.copy(
            targetLabel = resolvedLabel,
            stage = ProvisionStage.UNLOCKING_WIFI_ADB,
            statusText = "Locking Wi-Fi ADB port 5555 on $resolvedLabel..."
        )
        runWifiShell(mgr, "setprop persist.adb.tcp.port 5555 >/dev/null 2>&1 || true")
        if (connectPort != 5555) {
            runCatching { mgr.openStream("tcpip:5555").close() }
        }

        _state.value = _state.value.copy(
            stage = ProvisionStage.STARTING_UHID,
            statusText = "Starting Host Mode & kernel UHID gamepad engine..."
        )
        val agentStatus = runWifiShell(mgr, buildLaunchCommand(pkg, controllerClientId))

        _state.value = _state.value.copy(
            stage = ProvisionStage.VERIFYING,
            statusText = "Verifying UHID hardware & wireless Host..."
        )
        parseAndFinalizeAgentResult(
            agentOutput = agentStatus,
            targetLabel = resolvedLabel,
            resolvedIp = cleanIp,
            wasUsbUnlock = false
        )
    }

    fun startUsbProvisioning(
        tracker: UsbAdbTracker,
        deviceId: Int,
        displayName: String,
        controllerClientId: Int = 0
    ) {
        val runId = runCounter.incrementAndGet()
        scope.launch {
            _state.value = ProvisionUiState(
                runId = runId,
                active = true,
                transportLabel = "USB OTG CABLE",
                targetLabel = displayName,
                stage = ProvisionStage.CONNECTING,
                statusText = "Opening USB OTG ADB session with $displayName..."
            )

            var session: UsbAdbSession? = null
            try {
                session = tracker.openSession(deviceId)
                if (session == null) {
                    fail("USB permission not granted or ADB device unplugged.")
                    return@launch
                }

                val authed = session.connect(
                    onWaitingTrustPrompt = {
                        _state.value = _state.value.copy(
                            stage = ProvisionStage.WAIT_TRUST,
                            statusText = "Check 'Always allow from this computer' and tap Allow on $displayName!"
                        )
                    }
                )
                if (!authed) {
                    fail("Target device did not accept USB debugging prompt within 30s.")
                    return@launch
                }

                val pkg = appContext.packageName
                _state.value = _state.value.copy(
                    stage = ProvisionStage.CHECKING_APK,
                    statusText = "Checking LinkPad on $displayName..."
                )
                val probeOut = session.runShellCommand(
                    "echo MODEL=\$(getprop ro.product.model); pm path $pkg; dumpsys package $pkg | grep versionCode"
                )
                val resolvedLabel = ProvisionParsers.parseTargetModel(probeOut) ?: displayName
                val installedCode = ProvisionParsers.parseInstalledVersionCode(probeOut)
                val apkMissing = !probeOut.contains("package:")

                if (apkMissing || installedCode == null || installedCode < BuildConfig.VERSION_CODE) {
                    val localApk = File(appContext.applicationInfo.sourceDir)
                    _state.value = _state.value.copy(
                        targetLabel = resolvedLabel,
                        stage = ProvisionStage.INSTALLING_APK,
                        statusText = "Transferring LinkPad v${BuildConfig.VERSION_NAME} over USB...",
                        apkProgress = 0f
                    )
                    val remoteApk = "/data/local/tmp/linkpad-setup.apk"
                    val pushed = session.pushFileViaSync(localApk, remoteApk) { p ->
                        _state.value = _state.value.copy(apkProgress = p)
                    }
                    if (!pushed) {
                        fail("USB file push failed while transferring APK.")
                        return@launch
                    }
                    val installOut = session.runShellCommand("pm install -r -d $remoteApk; rm -f $remoteApk", timeoutMs = 45_000)
                    if (!installOut.contains("Success", ignoreCase = true)) {
                        fail("Target package install failed: ${installOut.trim().take(120)}")
                        return@launch
                    }
                }

                val wlanInfo = session.runShellCommand("ip -4 addr show wlan0")
                val targetIp = ProvisionParsers.parseWlanIpv4(wlanInfo)

                _state.value = _state.value.copy(
                    targetLabel = resolvedLabel,
                    stage = ProvisionStage.STARTING_UHID,
                    statusText = "Launching Host Mode & persistent UHID engine...",
                    targetIp = targetIp
                )
                val agentStatus = session.runShellCommand(
                    buildLaunchCommand(pkg, controllerClientId),
                    timeoutMs = 12_000
                )

                _state.value = _state.value.copy(
                    stage = ProvisionStage.UNLOCKING_WIFI_ADB,
                    statusText = "Unlocking Wi-Fi ADB port 5555 (safe to unplug cable right after)..."
                )
                val tcpOk = session.enableTcpIp(5555)
                delay(400L)

                parseAndFinalizeAgentResult(
                    agentOutput = agentStatus,
                    targetLabel = resolvedLabel,
                    resolvedIp = targetIp,
                    wasUsbUnlock = tcpOk
                )
            } catch (t: Throwable) {
                fail(t.message ?: "USB OTG setup failed.")
            } finally {
                runCatching { session?.close() }
            }
        }
    }

    private fun parseAndFinalizeAgentResult(
        agentOutput: String,
        targetLabel: String,
        resolvedIp: String?,
        wasUsbUnlock: Boolean
    ) {
        val statusLine = agentOutput.lineSequence().lastOrNull {
            it.startsWith("READY:") || it.startsWith("ERROR:")
        }?.trim()

        if (statusLine != null && statusLine.startsWith("ERROR:")) {
            val code = statusLine.substringAfter("ERROR:")
            fail("Target device blocked hardware engine ($code). You can still connect for Zero-ADB Accessibility/Keyboard mode.")
            return
        }

        val backendLabel = when (statusLine?.substringAfter("READY:")) {
            UhidBackendType.KERNEL_UHID.name -> "Kernel /dev/uhid"
            UhidBackendType.UINPUT_CLI.name -> "AOSP uinput"
            UhidBackendType.INPUT_MANAGER.name -> "Binder InputManager"
            else -> "UHID Engine"
        }

        val note = when {
            wasUsbUnlock && !resolvedIp.isNullOrBlank() ->
                "Wi-Fi ADB unlocked ($resolvedIp:5555). Connecting gamepad..."
            !resolvedIp.isNullOrBlank() ->
                "Connecting controller directly to $resolvedIp..."
            else ->
                "Bridge active on $targetLabel."
        }

        _state.value = _state.value.copy(
            stage = ProvisionStage.DONE,
            apkProgress = 1f,
            targetLabel = targetLabel,
            targetIp = resolvedIp,
            targetPort = LinkPadConstants.HOST_UDP_PORT,
            autoConnectReady = !resolvedIp.isNullOrBlank(),
            statusText = "$backendLabel active on $targetLabel. $note"
        )
    }

    private fun buildLaunchCommand(pkg: String, controllerClientId: Int): String {
        val trustFlag = if (controllerClientId != 0) " --ei extra_trust_client_id $controllerClientId" else ""
        val uiTrustFlag = if (controllerClientId != 0) " --ei linkpad_trust_client_id $controllerClientId" else ""

        return "rm -f /data/local/tmp/linkpad_agent.status && " +
            "APK=\$(pm path $pkg | tr -d '\\r' | head -n1 | cut -d: -f2); " +
            "if [ -z \"\$APK\" ]; then echo ERROR:APK_NOT_FOUND; exit 1; fi; " +
            "if command -v setsid >/dev/null 2>&1; then " +
            "setsid sh -c \"CLASSPATH=\\\"\$APK\\\" /system/bin/app_process / com.example.uhid.UhidHostAgent --persistent\" </dev/null >/dev/null 2>&1 & " +
            "else " +
            "nohup sh -c \"CLASSPATH=\\\"\$APK\\\" /system/bin/app_process / com.example.uhid.UhidHostAgent --persistent\" </dev/null >/dev/null 2>&1 & " +
            "fi; " +
            "i=0; while [ \$i -lt 20 ]; do [ -s /data/local/tmp/linkpad_agent.status ] && break; sleep 0.2; i=\$((i+1)); done; " +
            "am start-foreground-service -n $pkg/com.example.bridge.ControllerHostService -a com.example.action.START_HOST$trustFlag >/dev/null 2>&1 || " +
            "am startservice -n $pkg/com.example.bridge.ControllerHostService -a com.example.action.START_HOST$trustFlag >/dev/null 2>&1 || true; " +
            "am start -n $pkg/com.example.MainActivity --activity-single-top --ez linkpad_auto_host true$uiTrustFlag >/dev/null 2>&1 || " +
            "monkey -p $pkg -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; " +
            "cat /data/local/tmp/linkpad_agent.status 2>/dev/null || echo PENDING"
    }

    private fun runWifiShell(mgr: AbsAdbConnectionManager, command: String): String {
        val stream = mgr.openStream("shell:$command")
        val reader = BufferedReader(InputStreamReader(stream.openInputStream()))
        val out = StringBuilder()
        while (true) {
            val line = reader.readLine() ?: break
            out.appendLine(line)
        }
        runCatching { stream.close() }
        return out.toString()
    }

    private fun pushFileViaWifiSync(
        mgr: AbsAdbConnectionManager,
        localFile: File,
        remotePath: String,
        onProgress: (Float) -> Unit
    ): Boolean {
        val stream = mgr.openStream("sync:")
        return try {
            val out = stream.openOutputStream()
            val inp = stream.openInputStream()

            val spec = "$remotePath,33188".toByteArray(Charsets.UTF_8)
            out.write(AdbWireCodec.encodeSyncCommand("SEND", spec.size))
            out.write(spec)

            val total = localFile.length().coerceAtLeast(1L)
            val buf = ByteArray(16384)
            var sent = 0L
            FileInputStream(localFile).use { fis ->
                while (true) {
                    val n = fis.read(buf)
                    if (n <= 0) break
                    out.write(AdbWireCodec.encodeSyncCommand("DATA", n))
                    out.write(buf, 0, n)
                    sent += n
                    onProgress((sent.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                }
            }

            val mtime = (localFile.lastModified() / 1000L).toInt()
            out.write(AdbWireCodec.encodeSyncCommand("DONE", mtime))
            out.flush()

            val resp = ByteArray(8)
            var read = 0
            while (read < 8) {
                val r = inp.read(resp, read, 8 - read)
                if (r <= 0) break
                read += r
            }
            val ok = read >= 4 && String(resp, 0, 4, Charsets.US_ASCII) == "OKAY"

            runCatching {
                out.write(AdbWireCodec.encodeSyncCommand("QUIT", 0))
                out.flush()
            }
            ok
        } catch (_: Throwable) {
            false
        } finally {
            runCatching { stream.close() }
        }
    }

    private fun fail(message: String) {
        _state.value = _state.value.copy(
            stage = ProvisionStage.ERROR,
            autoConnectReady = false,
            errorMessage = message,
            statusText = message
        )
    }

    companion object {
        fun isTcpPortOpen(host: String, port: Int, timeoutMs: Int = 350): Boolean {
            return try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(host, port), timeoutMs)
                    true
                }
            } catch (_: Throwable) {
                false
            }
        }
    }
}
