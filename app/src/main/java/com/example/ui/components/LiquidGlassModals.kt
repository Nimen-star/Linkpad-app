package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.BrushedSilver
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.GlassFillSubtle
import com.example.ui.theme.GlassRimMid
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OledWell
import com.example.ui.theme.PewterMuted
import com.example.ui.theme.TitaniumText

@Composable
fun LiquidGlassDialog(
    onDismissRequest: () -> Unit,
    accentColor: Color = CobaltBlue,
    maxWidthDp: Int = 440,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg.copy(alpha = 0.66f))
                .clickable(onClick = onDismissRequest)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            LiquidGlassPanel(
                shape = RoundedCornerShape(26.dp),
                elevated = true,
                accentBorderTint = accentColor.copy(alpha = 0.50f),
                modifier = Modifier
                    .widthIn(max = maxWidthDp.dp)
                    .fillMaxWidth()
                    .clickable(enabled = false, onClick = {})
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
fun LiquidGlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge.copy(color = TitaniumText),
    trailingContent: (@Composable () -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (focused) BrushedSilver else PewterMuted
            )
        }
        LiquidGlassPanel(
            shape = RoundedCornerShape(14.dp),
            subtle = !focused,
            elevated = focused,
            accentBorderTint = if (focused) CobaltBlue else GlassRimMid,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OledWell.copy(alpha = 0.45f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle.copy(color = PewterMuted.copy(alpha = 0.48f))
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        keyboardOptions = keyboardOptions,
                        textStyle = textStyle,
                        cursorBrush = SolidColor(CobaltBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focused = it.isFocused }
                    )
                }
                if (trailingContent != null) {
                    trailingContent()
                }
            }
        }
    }
}

@Composable
fun LiquidGlassCheckRow(
    checked: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = CobaltBlue
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (checked) accentColor.copy(alpha = 0.25f) else GlassFillSubtle)
                .border(
                    BorderStroke(1.2.dp, if (checked) accentColor else GlassRimMid),
                    RoundedCornerShape(7.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = TitaniumText,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 13.sp,
            color = if (checked) TitaniumText else PewterMuted
        )
    }
}
