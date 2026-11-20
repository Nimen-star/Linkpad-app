package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BrushedSilver
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.GlassFillSubtle
import com.example.ui.theme.GlassRimMid
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PewterMuted

fun formatTapToRevealIpText(
    address: String,
    port: Int? = null,
    revealed: Boolean
): String {
    val cleanAddr = address.ifBlank { "0.0.0.0" }
    val portSuffix = if (port != null && port > 0) ":$port" else ""
    return if (revealed) {
        "$cleanAddr$portSuffix"
    } else {
        "•••.•••.•••.•••$portSuffix"
    }
}

@Composable
fun TapToRevealIpChip(
    address: String,
    port: Int? = null,
    compact: Boolean = false,
    testTag: String = "tap_to_reveal_ip",
    modifier: Modifier = Modifier
) {
    var revealed by remember(address, port) { mutableStateOf(false) }
    val displayText = formatTapToRevealIpText(address = address, port = port, revealed = revealed)

    Surface(
        shape = RoundedCornerShape(if (compact) 8.dp else 10.dp),
        color = GlassFillSubtle,
        border = BorderStroke(1.dp, if (revealed) CobaltBlue.copy(alpha = 0.55f) else GlassRimMid),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(if (compact) 8.dp else 10.dp))
            .clickable { revealed = !revealed }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = if (compact) 4.dp else 6.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (revealed) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = if (revealed) "Hide IP address" else "Tap to reveal IP address",
                tint = if (revealed) CobaltBlue else PewterMuted,
                modifier = Modifier.size(if (compact) 12.dp else 14.dp)
            )
            Text(
                text = displayText,
                fontFamily = JetBrainsMonoFamily,
                fontSize = if (compact) 11.sp else 12.sp,
                maxLines = 1,
                softWrap = false,
                color = if (revealed) BrushedSilver else PewterMuted
            )
        }
    }
}
