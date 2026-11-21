package com.example.transport

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.UUID

object LinkPadConstants {
    const val GITHUB_REPO_URL = "https://github.com/Nimen-star/linkpad-app"
    const val GITHUB_RELEASES_URL = "https://github.com/Nimen-star/linkpad-app/releases/latest"

    const val HOST_UDP_PORT = 47850
    const val BEACON_UDP_PORT = 47851
    const val MULTICAST_GROUP_IP = "239.255.77.77"
    const val NSD_SERVICE_TYPE = "_linkpad._udp."
    const val NSD_SERVICE_PREFIX = "LinkPad-"

    const val BT_SERVICE_NAME = "LinkPadController"
    val BT_SERVICE_UUID: UUID = UUID.fromString("8c4f1d20-6f2a-4c3b-9b71-2a0e5d904101")

    const val MAX_PLAYERS = 4
    const val CLIENT_TIMEOUT_MS = 5_500L
    const val PING_INTERVAL_MS = 300L
    const val BEACON_INTERVAL_MS = 750L

    fun hasBluetoothConnectPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasBluetoothScanPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            hasBluetoothConnectPermission(context)
        }
    }

    fun hasBluetoothAdvertisePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_ADVERTISE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            hasBluetoothConnectPermission(context)
        }
    }

    fun requiredBluetoothPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(android.Manifest.permission.BLUETOOTH)
        }
    }

    fun findPrimaryLocalIpv4(context: Context? = null): String {
        if (context != null) {
            runCatching {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val active = cm?.activeNetwork
                val props = cm?.getLinkProperties(active)
                val activeIpv4 = props?.linkAddresses
                    ?.mapNotNull { it.address as? Inet4Address }
                    ?.firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
                    ?.hostAddress
                if (!activeIpv4.isNullOrBlank()) return activeIpv4
            }
        }
        val addresses = findAllLocalIpv4s()
        return addresses.firstOrNull { it.startsWith("192.168.") }
            ?: addresses.firstOrNull { it.startsWith("10.") }
            ?: addresses.firstOrNull { it.startsWith("172.") }
            ?: addresses.firstOrNull()
            ?: "127.0.0.1"
    }

    fun findAllLocalIpv4s(): List<String> {
        val result = mutableListOf<String>()
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        addr.hostAddress?.let { result.add(it) }
                    }
                }
            }
        }
        return result
    }

    fun findBroadcastAddresses(): List<InetAddress> {
        val out = LinkedHashSet<InetAddress>()
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                for (ifAddr in iface.interfaceAddresses) {
                    val bcast = ifAddr.broadcast
                    if (bcast is Inet4Address) {
                        out.add(bcast)
                    }
                }
            }
        }
        runCatching { out.add(InetAddress.getByName("255.255.255.255")) }
        runCatching { out.add(InetAddress.getByName(MULTICAST_GROUP_IP)) }
        return out.toList()
    }
}
