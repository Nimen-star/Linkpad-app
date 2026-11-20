package com.example.data

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.protocol.ControlCustomization
import com.example.protocol.ControlOffset
import com.example.protocol.ControllerLayout
import com.example.protocol.FavoriteHostEntry
import com.example.protocol.GyroMode
import com.example.protocol.HapticStrength
import com.example.protocol.KeymapProfile
import com.example.protocol.RecentHostEntry
import com.example.protocol.TransportType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.random.Random

private val Context.dataStore by preferencesDataStore(name = "linkpad_prefs")

data class UserSettings(
    val deviceName: String = defaultDeviceName(),
    val clientId: Int = 0,
    val hapticsEnabled: Boolean = true,
    val hapticStrength: HapticStrength = HapticStrength.CRISP,
    val controllerLayout: ControllerLayout = ControllerLayout.DUAL_STICK,
    val gyroMode: GyroMode = GyroMode.OFF,
    val stickDeadzonePct: Int = 8,
    val controlCustomization: ControlCustomization = ControlCustomization(),
    val allowFavoritesWithoutCode: Boolean = false,
    val slotKeymaps: Map<Int, KeymapProfile> = defaultSlotKeymaps(),
    val recentHosts: List<RecentHostEntry> = emptyList(),
    val favoriteHosts: List<FavoriteHostEntry> = emptyList(),
    val trustedClientIds: Set<Int> = emptySet()
) {
    companion object {
        fun defaultDeviceName(): String {
            val raw = Build.MODEL?.trim().orEmpty()
            return if (raw.isBlank()) "Android Device" else raw.take(20)
        }

        fun defaultSlotKeymaps(): Map<Int, KeymapProfile> = mapOf(
            1 to KeymapProfile.GAMEPAD_STANDARD,
            2 to KeymapProfile.GAMEPAD_STANDARD,
            3 to KeymapProfile.GAMEPAD_STANDARD,
            4 to KeymapProfile.GAMEPAD_STANDARD
        )
    }
}

class LinkPadPreferences(private val context: Context) {

    private object Keys {
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val CLIENT_ID = intPreferencesKey("client_id")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val HAPTIC_STRENGTH = stringPreferencesKey("haptic_strength")
        val CONTROLLER_LAYOUT = stringPreferencesKey("controller_layout")
        val GYRO_MODE = stringPreferencesKey("gyro_mode")
        val STICK_DEADZONE_PCT = intPreferencesKey("stick_deadzone_pct")
        val CONTROL_SCALE = floatPreferencesKey("control_scale")
        val CONTROL_OPACITY = floatPreferencesKey("control_opacity")
        val CONTROL_OFFSETS = stringPreferencesKey("control_offsets")
        val ALLOW_FAV_NO_CODE = booleanPreferencesKey("allow_fav_no_code")
        val SLOT_1_KEYMAP = stringPreferencesKey("slot_1_keymap")
        val SLOT_2_KEYMAP = stringPreferencesKey("slot_2_keymap")
        val SLOT_3_KEYMAP = stringPreferencesKey("slot_3_keymap")
        val SLOT_4_KEYMAP = stringPreferencesKey("slot_4_keymap")
        val RECENT_HOSTS = stringPreferencesKey("recent_hosts")
        val FAVORITE_HOSTS = stringPreferencesKey("favorite_hosts")
        val TRUSTED_CLIENT_IDS = stringPreferencesKey("trusted_client_ids")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        val slotKeymaps = mapOf(
            1 to parseKeymap(prefs[Keys.SLOT_1_KEYMAP]),
            2 to parseKeymap(prefs[Keys.SLOT_2_KEYMAP]),
            3 to parseKeymap(prefs[Keys.SLOT_3_KEYMAP]),
            4 to parseKeymap(prefs[Keys.SLOT_4_KEYMAP])
        )
        val hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true
        val parsedStrength = parseHapticStrength(prefs[Keys.HAPTIC_STRENGTH]) ?: if (hapticsEnabled) {
            HapticStrength.CRISP
        } else {
            HapticStrength.OFF
        }
        val scale = (prefs[Keys.CONTROL_SCALE] ?: 1.0f).coerceIn(0.72f, 1.35f)
        val opacity = (prefs[Keys.CONTROL_OPACITY] ?: 1.0f).coerceIn(0.35f, 1.0f)
        val customization = parseControlOffsets(prefs[Keys.CONTROL_OFFSETS].orEmpty())
            .copy(scale = scale, opacity = opacity)

        UserSettings(
            deviceName = prefs[Keys.DEVICE_NAME]?.takeIf { it.isNotBlank() }
                ?: UserSettings.defaultDeviceName(),
            clientId = prefs[Keys.CLIENT_ID] ?: 0,
            hapticsEnabled = parsedStrength != HapticStrength.OFF,
            hapticStrength = parsedStrength,
            controllerLayout = parseControllerLayout(prefs[Keys.CONTROLLER_LAYOUT]),
            gyroMode = parseGyroMode(prefs[Keys.GYRO_MODE]),
            stickDeadzonePct = (prefs[Keys.STICK_DEADZONE_PCT] ?: 8).coerceIn(0, 25),
            controlCustomization = customization,
            allowFavoritesWithoutCode = prefs[Keys.ALLOW_FAV_NO_CODE] ?: false,
            slotKeymaps = slotKeymaps,
            recentHosts = parseRecentHosts(prefs[Keys.RECENT_HOSTS].orEmpty()),
            favoriteHosts = parseFavoriteHosts(prefs[Keys.FAVORITE_HOSTS].orEmpty()),
            trustedClientIds = parseTrustedIds(prefs[Keys.TRUSTED_CLIENT_IDS].orEmpty())
        )
    }

    suspend fun getOrCreateClientId(): Int {
        val existing = context.dataStore.data.first()[Keys.CLIENT_ID] ?: 0
        if (existing != 0) return existing
        var generated = 0
        while (generated == 0) {
            generated = Random.nextInt()
        }
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.CLIENT_ID] ?: 0
            if (current == 0) {
                prefs[Keys.CLIENT_ID] = generated
            } else {
                generated = current
            }
        }
        return generated
    }

    suspend fun setDeviceName(name: String) {
        val cleaned = name.trim().take(20)
        if (cleaned.isNotEmpty()) {
            context.dataStore.edit { it[Keys.DEVICE_NAME] = cleaned }
        }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HAPTICS_ENABLED] = enabled
            prefs[Keys.HAPTIC_STRENGTH] = if (enabled) HapticStrength.CRISP.name else HapticStrength.OFF.name
        }
    }

    suspend fun setHapticStrength(strength: HapticStrength) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HAPTIC_STRENGTH] = strength.name
            prefs[Keys.HAPTICS_ENABLED] = strength != HapticStrength.OFF
        }
    }

    suspend fun setControllerLayout(layout: ControllerLayout) {
        context.dataStore.edit { it[Keys.CONTROLLER_LAYOUT] = layout.name }
    }

    suspend fun setGyroMode(mode: GyroMode) {
        context.dataStore.edit { it[Keys.GYRO_MODE] = mode.name }
    }

    suspend fun setStickDeadzonePct(pct: Int) {
        context.dataStore.edit { it[Keys.STICK_DEADZONE_PCT] = pct.coerceIn(0, 25) }
    }

    suspend fun setControlScale(scale: Float) {
        context.dataStore.edit { it[Keys.CONTROL_SCALE] = scale.coerceIn(0.72f, 1.35f) }
    }

    suspend fun setControlOpacity(opacity: Float) {
        context.dataStore.edit { it[Keys.CONTROL_OPACITY] = opacity.coerceIn(0.35f, 1.0f) }
    }

    suspend fun setControlCustomization(customization: ControlCustomization) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CONTROL_SCALE] = customization.scale.coerceIn(0.72f, 1.35f)
            prefs[Keys.CONTROL_OPACITY] = customization.opacity.coerceIn(0.35f, 1.0f)
            prefs[Keys.CONTROL_OFFSETS] = serializeControlOffsets(customization)
        }
    }

    suspend fun resetControlCustomization() {
        context.dataStore.edit { prefs ->
            prefs[Keys.CONTROL_SCALE] = 1.0f
            prefs[Keys.CONTROL_OPACITY] = 1.0f
            prefs.remove(Keys.CONTROL_OFFSETS)
        }
    }

    suspend fun setAllowFavoritesWithoutCode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ALLOW_FAV_NO_CODE] = enabled }
    }

    suspend fun setSlotKeymap(slot: Int, profile: KeymapProfile) {
        val key = when (slot) {
            1 -> Keys.SLOT_1_KEYMAP
            2 -> Keys.SLOT_2_KEYMAP
            3 -> Keys.SLOT_3_KEYMAP
            4 -> Keys.SLOT_4_KEYMAP
            else -> return
        }
        context.dataStore.edit { it[key] = profile.name }
    }

    suspend fun addRecentHost(entry: RecentHostEntry) {
        context.dataStore.edit { prefs ->
            val current = parseRecentHosts(prefs[Keys.RECENT_HOSTS].orEmpty())
                .filterNot {
                    it.address == entry.address &&
                        it.port == entry.port &&
                        it.bluetoothAddress == entry.bluetoothAddress
                }
            val updated = (listOf(entry) + current).take(5)
            prefs[Keys.RECENT_HOSTS] = serializeRecentHosts(updated)
        }
    }

    suspend fun addFavoriteHost(entry: FavoriteHostEntry) {
        context.dataStore.edit { prefs ->
            val current = parseFavoriteHosts(prefs[Keys.FAVORITE_HOSTS].orEmpty())
                .filterNot {
                    it.address == entry.address &&
                        it.port == entry.port &&
                        it.bluetoothAddress == entry.bluetoothAddress
                }
            val updated = (listOf(entry) + current).take(12)
            prefs[Keys.FAVORITE_HOSTS] = serializeFavoriteHosts(updated)
        }
    }

    suspend fun removeFavoriteHost(entry: FavoriteHostEntry) {
        context.dataStore.edit { prefs ->
            val updated = parseFavoriteHosts(prefs[Keys.FAVORITE_HOSTS].orEmpty())
                .filterNot {
                    it.address == entry.address &&
                        it.port == entry.port &&
                        it.bluetoothAddress == entry.bluetoothAddress
                }
            prefs[Keys.FAVORITE_HOSTS] = serializeFavoriteHosts(updated)
        }
    }

    suspend fun addTrustedClientId(clientId: Int) {
        if (clientId == 0) return
        context.dataStore.edit { prefs ->
            val current = parseTrustedIds(prefs[Keys.TRUSTED_CLIENT_IDS].orEmpty()).toMutableSet()
            if (current.add(clientId)) {
                prefs[Keys.TRUSTED_CLIENT_IDS] = current.joinToString(",")
            }
        }
    }

    suspend fun clearTrustedClientIds() {
        context.dataStore.edit { it.remove(Keys.TRUSTED_CLIENT_IDS) }
    }

    private fun parseKeymap(value: String?): KeymapProfile =
        KeymapProfile.entries.firstOrNull { it.name == value } ?: KeymapProfile.GAMEPAD_STANDARD

    private fun parseHapticStrength(value: String?): HapticStrength? =
        HapticStrength.entries.firstOrNull { it.name == value }

    private fun parseControllerLayout(value: String?): ControllerLayout =
        ControllerLayout.entries.firstOrNull { it.name == value } ?: ControllerLayout.DUAL_STICK

    private fun parseGyroMode(value: String?): GyroMode =
        GyroMode.entries.firstOrNull { it.name == value } ?: GyroMode.OFF

    private fun serializeControlOffsets(c: ControlCustomization): String {
        val values = listOf(
            c.dpadOffset.x, c.dpadOffset.y,
            c.leftStickOffset.x, c.leftStickOffset.y,
            c.rightStickOffset.x, c.rightStickOffset.y,
            c.abxyOffset.x, c.abxyOffset.y,
            c.leftShoulderOffset.x, c.leftShoulderOffset.y,
            c.rightShoulderOffset.x, c.rightShoulderOffset.y,
            c.menuOffset.x, c.menuOffset.y
        )
        return values.joinToString(",") { "%.2f".format(java.util.Locale.US, it) }
    }

    private fun parseControlOffsets(raw: String): ControlCustomization {
        if (raw.isBlank()) return ControlCustomization()
        val p = raw.split(",").mapNotNull { it.toFloatOrNull() }
        if (p.size < 14) return ControlCustomization()
        return ControlCustomization(
            dpadOffset = ControlOffset(p[0], p[1]),
            leftStickOffset = ControlOffset(p[2], p[3]),
            rightStickOffset = ControlOffset(p[4], p[5]),
            abxyOffset = ControlOffset(p[6], p[7]),
            leftShoulderOffset = ControlOffset(p[8], p[9]),
            rightShoulderOffset = ControlOffset(p[10], p[11]),
            menuOffset = ControlOffset(p[12], p[13])
        )
    }

    private fun serializeRecentHosts(list: List<RecentHostEntry>): String =
        list.joinToString("\n") { item ->
            val safeName = item.name.replace("|", " ").replace("\n", " ")
            listOf(
                safeName,
                item.address,
                item.port.toString(),
                item.transport.name,
                item.bluetoothAddress.orEmpty()
            ).joinToString("|")
        }

    private fun parseRecentHosts(raw: String): List<RecentHostEntry> {
        if (raw.isBlank()) return emptyList()
        return raw.lines().mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size < 4) return@mapNotNull null
            val port = parts[2].toIntOrNull() ?: return@mapNotNull null
            val transport = TransportType.entries.firstOrNull { it.name == parts[3] }
                ?: TransportType.WIFI_UDP
            val btAddr = parts.getOrNull(4)?.takeIf { it.isNotBlank() }
            RecentHostEntry(
                name = parts[0],
                address = parts[1],
                port = port,
                transport = transport,
                bluetoothAddress = btAddr
            )
        }
    }

    private fun serializeFavoriteHosts(list: List<FavoriteHostEntry>): String =
        list.joinToString("\n") { item ->
            val safeName = item.name.replace("|", " ").replace("\n", " ")
            listOf(
                safeName,
                item.address,
                item.port.toString(),
                item.transport.name,
                item.bluetoothAddress.orEmpty()
            ).joinToString("|")
        }

    private fun parseFavoriteHosts(raw: String): List<FavoriteHostEntry> {
        if (raw.isBlank()) return emptyList()
        return raw.lines().mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size < 4) return@mapNotNull null
            val port = parts[2].toIntOrNull() ?: return@mapNotNull null
            val transport = TransportType.entries.firstOrNull { it.name == parts[3] }
                ?: TransportType.WIFI_UDP
            val btAddr = parts.getOrNull(4)?.takeIf { it.isNotBlank() }
            FavoriteHostEntry(
                name = parts[0],
                address = parts[1],
                port = port,
                transport = transport,
                bluetoothAddress = btAddr
            )
        }
    }

    private fun parseTrustedIds(raw: String): Set<Int> {
        if (raw.isBlank()) return emptySet()
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it != 0 }.toSet()
    }
}
