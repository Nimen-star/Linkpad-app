package com.example.bridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.protocol.KeymapProfile
import com.example.transport.HostSessionManager
import com.example.uhid.UhidBridgeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

object HostRuntimeHolder {
    @Volatile
    private var manager: HostSessionManager? = null

    fun getOrCreate(context: Context): HostSessionManager {
        return manager ?: synchronized(this) {
            manager ?: HostSessionManager(context.applicationContext).also { manager = it }
        }
    }

    fun hardwareBridgeState(context: Context): StateFlow<UhidBridgeState> =
        getOrCreate(context).hardwareBridge.state

    fun refreshHardwareBridge(context: Context) {
        getOrCreate(context).hardwareBridge.refreshProbe()
    }

    fun activateHardwareGamepads(context: Context) {
        getOrCreate(context).hardwareBridge.activateHardwareGamepads()
    }

    fun pairWirelessDebugging(context: Context, portText: String, pairingCode: String) {
        getOrCreate(context).hardwareBridge.pairAndActivateWirelessDebugging(portText, pairingCode)
    }

    fun shutdownHardwareBridge() {
        manager?.hardwareBridge?.shutdownAgent()
    }

    fun updateKeymap(slot: Int, profile: KeymapProfile) {
        manager?.updateSlotKeymap(slot, profile)
    }

    fun kickPlayer(slot: Int) {
        manager?.kickPlayer(slot)
    }

    fun regenerateCode(): String? = manager?.regeneratePairingCode()

    fun setBluetoothEnabled(enabled: Boolean) {
        manager?.setBluetoothEnabled(enabled)
    }

    fun setTrustedConfiguration(trustedIds: Set<Int>, allowAnyFavorite: Boolean) {
        manager?.setTrustedConfiguration(trustedIds, allowAnyFavorite)
    }

    fun setOnTrustClientCallback(callback: ((Int) -> Unit)?) {
        manager?.onTrustClientId = callback
    }
}

class ControllerHostService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var codeObserverJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                HostRuntimeHolder.getOrCreate(this).stopHosting()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val deviceName = intent?.getStringExtra(EXTRA_DEVICE_NAME) ?: "LinkPad Host"
                val enableBt = intent?.getBooleanExtra(EXTRA_ENABLE_BT, false) ?: false
                ensureNotificationChannel()
                val manager = HostRuntimeHolder.getOrCreate(this)
                val code = manager.startHosting(
                    deviceName = deviceName,
                    enableBluetooth = enableBt
                )
                val notification = buildNotification(code)
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                }

                if (codeObserverJob == null) {
                    codeObserverJob = scope.launch {
                        InputBridgeBus.pairingCode.collectLatest { updatedCode ->
                            if (updatedCode.isNotEmpty() && InputBridgeBus.hostActive.value) {
                                val nm = getSystemService(NotificationManager::class.java)
                                runCatching {
                                    nm?.notify(NOTIFICATION_ID, buildNotification(updatedCode))
                                }
                            }
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        codeObserverJob?.cancel()
        codeObserverJob = null
        HostRuntimeHolder.getOrCreate(this).stopHosting()
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.host_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(code: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ControllerHostService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.host_notification_title))
            .setContentText(getString(R.string.host_notification_text, code))
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "linkpad_host_channel"
        const val NOTIFICATION_ID = 4785
        const val ACTION_START = "com.example.action.START_HOST"
        const val ACTION_STOP = "com.example.action.STOP_HOST"
        const val EXTRA_DEVICE_NAME = "extra_device_name"
        const val EXTRA_ENABLE_BT = "extra_enable_bt"

        fun start(context: Context, deviceName: String, enableBluetooth: Boolean) {
            val intent = Intent(context, ControllerHostService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DEVICE_NAME, deviceName)
                putExtra(EXTRA_ENABLE_BT, enableBluetooth)
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ControllerHostService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
        }
    }
}
