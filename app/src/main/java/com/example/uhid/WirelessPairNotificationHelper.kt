package com.example.uhid

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.example.R
import com.example.bridge.HostRuntimeHolder

object WirelessPairParser {
    /**
     * Accepts either:
     * - "38411 123456" / "38411:123456" / "38411-123456" -> Pair("38411", "123456")
     * - "123456" (when mDNS already discovered pairing port) -> Pair(fallbackPort.toString(), "123456")
     */
    fun parseReply(rawInput: String, fallbackPort: Int?): Pair<String, String>? {
        val tokens = rawInput.trim()
            .split(Regex("""[\s:,/\-]+"""))
            .filter { it.isNotEmpty() }
            .map { tok -> tok.filter { ch -> ch.isDigit() } }
            .filter { it.isNotEmpty() }

        return when {
            tokens.size >= 2 -> {
                val port = tokens[0]
                val code = tokens[1]
                val portInt = port.toIntOrNull() ?: return null
                if (portInt in 1..65535 && code.length == 6) port to code else null
            }
            tokens.size == 1 && tokens[0].length == 6 && fallbackPort != null && fallbackPort in 1..65535 -> {
                fallbackPort.toString() to tokens[0]
            }
            else -> null
        }
    }
}

object WirelessPairNotificationHelper {
    const val CHANNEL_ID = "linkpad_wireless_pair_channel"
    const val NOTIFICATION_ID = 4786
    const val KEY_TEXT_REPLY = "key_wireless_pair_reply"
    const val EXTRA_FALLBACK_PORT = "extra_fallback_port"
    const val ACTION_PAIR_SUBMITTED = "com.example.uhid.ACTION_WIRELESS_PAIR_SUBMITTED"

    fun showQuickPairNotification(context: Context, discoveredPort: Int?) {
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val nm = appContext.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "LinkPad Wireless Debugging Pair",
                NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(ch)
        }

        val hint = if (discoveredPort != null) {
            "Port $discoveredPort auto-detected! Enter 6-digit code"
        } else {
            "Enter: PORT CODE (e.g. 41235 123456)"
        }

        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel(hint)
            .build()

        val submitIntent = Intent(appContext, WirelessPairReceiver::class.java).apply {
            action = ACTION_PAIR_SUBMITTED
            if (discoveredPort != null) {
                putExtra(EXTRA_FALLBACK_PORT, discoveredPort)
            }
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pendingIntent = PendingIntent.getBroadcast(appContext, 101, submitIntent, flags)

        val action = NotificationCompat.Action.Builder(
            R.mipmap.ic_launcher,
            "Enter Pair PIN",
            pendingIntent
        ).addRemoteInput(remoteInput).build()

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Stay in Wireless Debugging · Tap Reply")
            .setContentText(hint)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(action)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        runCatching { nm?.cancel(NOTIFICATION_ID) }
    }
}

class WirelessPairReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != WirelessPairNotificationHelper.ACTION_PAIR_SUBMITTED) return
        val results = RemoteInput.getResultsFromIntent(intent) ?: return
        val reply = results.getCharSequence(WirelessPairNotificationHelper.KEY_TEXT_REPLY)?.toString().orEmpty()
        val fallbackPort = intent.getIntExtra(WirelessPairNotificationHelper.EXTRA_FALLBACK_PORT, -1)
            .takeIf { it in 1..65535 }

        val parsed = WirelessPairParser.parseReply(reply, fallbackPort)
        WirelessPairNotificationHelper.cancel(context)
        if (parsed != null) {
            HostRuntimeHolder.pairWirelessDebugging(context.applicationContext, parsed.first, parsed.second)
        }
    }
}
