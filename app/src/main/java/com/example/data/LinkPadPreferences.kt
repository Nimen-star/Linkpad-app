package com.example.data

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.protocol.ControlCustomization
import com.example.protocol.ControlOffset
import com.example.protocol.ControllerLayout
import com.example.protocol.ControllerTheme
import com.example.protocol.FavoriteHostEntry
import com.example.protocol.GyroMode
import com.example.protocol.GyroResponseMode
import com.example.protocol.HapticStrength
import com.example.protocol.KeymapProfile
import com.example.protocol.OsInputBridgeMode
import com.example.protocol.PadButton
import com.example.protocol.ProPaddleConfig
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
    val controllerTheme: ControllerTheme = ControllerTheme.LIQUID_GLASS,
    val gyroMode: GyroMode = GyroMode.OFF,
    val gyroResponseMode: GyroResponseMode = GyroResponseMode.TILT_HOLD,
    val gyroSensitivity: Float = 1.25f,
    val invertGyroY: Boolean = false,
    val stickDeadzonePct: Int = 8,
    val proPaddleConfig: ProPaddleConfig = ProPaddleConfig(),
    val customizationsByLayout: Map<ControllerLayout, ControlCustomization> = defaultCustomizations(),
    val allowFavoritesWithoutCode: Boolean = false,
    val osInputBridgeMode: OsInputBridgeMode = OsInputBridgeMode.TV_DPAD_NAV,
    val slotKeymaps: Map<Int, KeymapProfile> = defaultSlotKeymaps(),
    val recentHosts: List<RecentHostEntry> = emptyList(),
    val favoriteHosts: List<FavoriteHostEntry> = emptyList(),
    val trustedClientIds: Set<Int> = emptySet()
) {
    fun customizationFor(layout: ControllerLayout): ControlCustomization =
        customizationsByLayout[layout] ?: ControlCustomization()

    companion object {
        fun defaultDeviceName(): String {
            val raw = Build.MODEL?.trim().orEmpty()
            return if (raw.isBlank()) "Android Device" else raw.take(20)
        }

        fun defaultCustomizations(): Map<ControllerLayout, ControlCustomization> =
            ControllerLayout.entries.associateWith { ControlCustomization() }

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
        val CONTROLLER_THEME = stringPreferencesKey("controller_theme")
        val GYRO_MODE = stringPreferencesKey("gyro_mode")
        val GYRO_RESPONSE_MODE = stringPreferencesKey("gyro_response_mode")
        val GYRO_SENSITIVITY = floatPreferencesKey("gyro_sensitivity")
        val INVERT_GYRO_Y = booleanPreferencesKey("invert_gyro_y")
        val STICK_DEADZONE_PCT = intPreferencesKey("stick_deadzone_pct")
        val PRO_PADDLE_BINDINGS = stringPreferencesKey("pro_paddle_bindings")

        // Legacy global customization keys (kept strictly for 1-time fallback into active layout)
        val CONTROL_SCALE = floatPreferencesKey("control_scale")
        val CONTROL_OPACITY = floatPreferencesKey("control_opacity")
        val CONTROL_OFFSETS = stringPreferencesKey("control_offsets")
        val MIGRATED_PER_LAYOUT = booleanPreferencesKey("migrated_per_layout")

        val ALLOW_FAV_NO_CODE = booleanPreferencesKey("allow_fav_no_code")
        val OS_INPUT_BRIDGE_MODE = stringPreferencesKey("os_input_bridge_mode")
        val SLOT_1_KEYMAP = stringPreferencesKey("slot_1_keymap")
        val SLOT_2_KEYMAP = stringPreferencesKey("slot_2_keymap")
        val SLOT_3_KEYMAP = stringPreferencesKey("slot_3_keymap")
        val SLOT_4_KEYMAP = stringPreferencesKey("slot_4_keymap")
        val RECENT_HOSTS = stringPreferencesKey("recent_hosts")
        val FAVORITE_HOSTS = stringPreferencesKey("favorite_hosts")
        val TRUSTED_CLIENT_IDS = stringPreferencesKey("trusted_client_ids")

        fun customLayoutKey(layout: ControllerLayout): Preferences.Key<String> =
            stringPreferencesKey("custom_layout_${layout.name}")
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
        val activeLayout = parseControllerLayout(prefs[Keys.CONTROLLER_LAYOUT])
        val migrated = prefs[Keys.MIGRATED_PER_LAYOUT] ?: false
        val legacyScale = (prefs[Keys.CONTROL_SCALE] ?: 1.0f).coerceIn(0.72f, 1.35f)
        val legacyOpacity = (prefs[Keys.CONTROL_OPACITY] ?: 1.0f).coerceIn(0.35f, 1.0f)
        val legacyOffsetsRaw = prefs[Keys.CONTROL_OFFSETS].orEmpty()
        val hasLegacy = prefs[Keys.CONTROL_SCALE] != null ||
            prefs[Keys.CONTROL_OPACITY] != null ||
            legacyOffsetsRaw.isNotBlank()

        val customizationsByLayout = ControllerLayout.entries.associateWith { layout ->
            val raw = prefs[Keys.customLayoutKey(layout)]
            when {
                !raw.isNullOrBlank() -> parseScopedCustomization(raw)
                !migrated && hasLegacy && layout == activeLayout ->
                    parseControlOffsets(legacyOffsetsRaw).copy(
                        scale = legacyScale,
                        opacity = legacyOpacity
                    )
                else -> ControlCustomization()
            }
        }

        UserSettings(
            deviceName = prefs[Keys.DEVICE_NAME]?.takeIf { it.isNotBlank() }
                ?: UserSettings.defaultDeviceName(),
            clientId = prefs[Keys.CLIENT_ID] ?: 0,
            hapticsEnabled = parsedStrength != HapticStrength.OFF,
            hapticStrength = parsedStrength,
            controllerLayout = activeLayout,
            controllerTheme = parseControllerTheme(prefs[Keys.CONTROLLER_THEME]),
            gyroMode = parseGyroMode(prefs[Keys.GYRO_MODE]),
            gyroResponseMode = parseGyroResponseMode(prefs[Keys.GYRO_RESPONSE_MODE]),
            gyroSensitivity = (prefs[Keys.GYRO_SENSITIVITY] ?: 1.25f).coerceIn(0.5f, 2.5f),
            invertGyroY = prefs[Keys.INVERT_GYRO_Y] ?: false,
            stickDeadzonePct = (prefs[Keys.STICK_DEADZONE_PCT] ?: 8).coerceIn(0, 25),
            proPaddleConfig = parseProPaddleConfig(prefs[Keys.PRO_PADDLE_BINDINGS].orEmpty()),
            customizationsByLayout = customizationsByLayout,
            allowFavoritesWithoutCode = prefs[Keys.ALLOW_FAV_NO_CODE] ?: false,
            osInputBridgeMode = parseOsInputBridgeMode(prefs[Keys.OS_INPUT_BRIDGE_MODE]),
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

    suspend fun setControllerTheme(theme: ControllerTheme) {
        context.dataStore.edit { it[Keys.CONTROLLER_THEME] = theme.name }
    }

    suspend fun setGyroMode(mode: GyroMode) {
        context.dataStore.edit { it[Keys.GYRO_MODE] = mode.name }
    }

    suspend fun setGyroResponseMode(mode: GyroResponseMode) {
        context.dataStore.edit { it[Keys.GYRO_RESPONSE_MODE] = mode.name }
    }

    suspend fun setGyroSensitivity(sensitivity: Float) {
        context.dataStore.edit { it[Keys.GYRO_SENSITIVITY] = sensitivity.coerceIn(0.5f, 2.5f) }
    }

    suspend fun setInvertGyroY(invert: Boolean) {
        context.dataStore.edit { it[Keys.INVERT_GYRO_Y] = invert }
    }

    suspend fun setStickDeadzonePct(pct: Int) {
        context.dataStore.edit { it[Keys.STICK_DEADZONE_PCT] = pct.coerceIn(0, 25) }
    }

    suspend fun setProPaddleConfig(config: ProPaddleConfig) {
        context.dataStore.edit {
            it[Keys.PRO_PADDLE_BINDINGS] = listOf(config.m1, config.m2, config.m3, config.m4)
                .joinToString(",") { btn -> btn.name }
        }
    }

    suspend fun setControlCustomization(
        layout: ControllerLayout,
        customization: ControlCustomization
    ) {
        val safe = customization.copy(
            scale = customization.scale.coerceIn(0.72f, 1.35f),
            opacity = customization.opacity.coerceIn(0.35f, 1.0f)
        )
        context.dataStore.edit { prefs ->
            prefs[Keys.MIGRATED_PER_LAYOUT] = true
            prefs[Keys.customLayoutKey(layout)] = serializeScopedCustomization(safe)
        }
    }

    suspend fun resetControlCustomization(layout: ControllerLayout) {
        context.dataStore.edit { prefs ->
            prefs[Keys.MIGRATED_PER_LAYOUT] = true
            prefs[Keys.customLayoutKey(layout)] = serializeScopedCustomization(ControlCustomization())
        }
    }

    suspend fun setAllowFavoritesWithoutCode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ALLOW_FAV_NO_CODE] = enabled }
    }

    suspend fun setOsInputBridgeMode(mode: OsInputBridgeMode) {
        context.dataStore.edit { it[Keys.OS_INPUT_BRIDGE_MODE] = mode.name }
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

    private fun parseOsInputBridgeMode(value: String?): OsInputBridgeMode =
        OsInputBridgeMode.entries.firstOrNull { it.name == value } ?: OsInputBridgeMode.TV_DPAD_NAV

    private fun parseHapticStrength(value: String?): HapticStrength? =
        HapticStrength.entries.firstOrNull { it.name == value }

    private fun parseControllerLayout(value: String?): ControllerLayout =
        ControllerLayout.entries.firstOrNull { it.name == value } ?: ControllerLayout.DUAL_STICK

    private fun parseControllerTheme(value: String?): ControllerTheme =
        ControllerTheme.entries.firstOrNull { it.name == value } ?: ControllerTheme.LIQUID_GLASS

    private fun parseGyroMode(value: String?): GyroMode =
        GyroMode.entries.firstOrNull { it.name == value } ?: GyroMode.OFF

    private fun parseGyroResponseMode(value: String?): GyroResponseMode =
        GyroResponseMode.entries.firstOrNull { it.name == value } ?: GyroResponseMode.TILT_HOLD

    private fun parseProPaddleConfig(raw: String): ProPaddleConfig {
        if (raw.isBlank()) return ProPaddleConfig()
        val parts = raw.split(",")
        fun btnAt(idx: Int, fallback: PadButton): PadButton =
            parts.getOrNull(idx)?.let { name -> PadButton.entries.firstOrNull { it.name == name } }
                ?: fallback
        return ProPaddleConfig(
            m1 = btnAt(0, PadButton.A),
            m2 = btnAt(1, PadButton.B),
            m3 = btnAt(2, PadButton.L3),
            m4 = btnAt(3, PadButton.R3)
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

    companion object {
        internal fun serializeScopedCustomization(c: ControlCustomization): String {
            val values = listOf(
                c.scale.coerceIn(0.72f, 1.35f),
                c.opacity.coerceIn(0.35f, 1.0f),
                c.dpadOffset.x, c.dpadOffset.y,
                c.leftStickOffset.x, c.leftStickOffset.y,
                c.rightStickOffset.x, c.rightStickOffset.y,
                c.abxyOffset.x, c.abxyOffset.y,
                c.leftShoulderOffset.x, c.leftShoulderOffset.y,
                c.rightShoulderOffset.x, c.rightShoulderOffset.y,
                c.menuOffset.x, c.menuOffset.y,
                c.paddlesOffset.x, c.paddlesOffset.y
            )
            return values.joinToString(",") { "%.2f".format(java.util.Locale.US, it) }
        }

        internal fun parseScopedCustomization(raw: String): ControlCustomization {
            if (raw.isBlank()) return ControlCustomization()
            val p = raw.split(",").mapNotNull { it.toFloatOrNull() }
            if (p.size < 16) return ControlCustomization()
            return ControlCustomization(
                scale = p[0].coerceIn(0.72f, 1.35f),
                opacity = p[1].coerceIn(0.35f, 1.0f),
                dpadOffset = ControlOffset(p[2], p[3]),
                leftStickOffset = ControlOffset(p[4], p[5]),
                rightStickOffset = ControlOffset(p[6], p[7]),
                abxyOffset = ControlOffset(p[8], p[9]),
                leftShoulderOffset = ControlOffset(p[10], p[11]),
                rightShoulderOffset = ControlOffset(p[12], p[13]),
                menuOffset = ControlOffset(p[14], p[15]),
                paddlesOffset = if (p.size >= 18) ControlOffset(p[16], p[17]) else ControlOffset()
            )
        }

        internal fun parseControlOffsets(raw: String): ControlCustomization {
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
    }
}
