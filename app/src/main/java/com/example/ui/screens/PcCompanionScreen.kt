package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.transport.LinkPadConstants
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.components.LiquidGlassPanel
import com.example.ui.theme.BrushedSilver
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.GlassRimMid
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PewterMuted
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TitaniumText

@Composable
fun PcCompanionScreen(
    onOpenControllerDiscovery: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val permanentUrl = LinkPadConstants.GITHUB_RELEASES_URL

    BackHandler(onBack = onBack)

    LiquidGlassBackdrop {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("pc_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TitaniumText
                        )
                    }
                    Text(
                        text = "PC Host Installer",
                        style = MaterialTheme.typography.titleLarge,
                        color = TitaniumText
                    )
                }

                // Permanent GitHub Releases URL Card
                LiquidGlassPanel(
                    shape = RoundedCornerShape(24.dp),
                    elevated = true,
                    accentBorderTint = SignalEmerald,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Computer,
                                contentDescription = null,
                                tint = SignalEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "PERMANENT DOWNLOAD LINK",
                                style = MaterialTheme.typography.labelMedium,
                                color = PewterMuted
                            )
                        }

                        Text(
                            text = permanentUrl,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = TitaniumText,
                            modifier = Modifier.testTag("pc_download_url")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("LinkPad Releases URL", permanentUrl))
                                    Toast.makeText(context, "Release URL copied", Toast.LENGTH_SHORT).show()
                                },
                                border = BorderStroke(1.dp, GlassRimMid),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("copy_pc_url_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = BrushedSilver,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy URL", color = TitaniumText, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, "LinkPad PC Host Installer")
                                            putExtra(Intent.EXTRA_TEXT, permanentUrl)
                                        }
                                        context.startActivity(
                                            Intent.createChooser(shareIntent, "Send LinkPad Installer URL")
                                        )
                                    }
                                },
                                border = BorderStroke(1.dp, GlassRimMid),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("share_pc_url_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    tint = BrushedSilver,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share URL", color = TitaniumText, maxLines = 1)
                            }
                        }

                        Button(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(permanentUrl))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrushedSilver,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("open_releases_browser_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Releases Page", maxLines = 1)
                        }
                    }
                }

                // Minimal Single-Installer Setup Steps
                LiquidGlassPanel(
                    shape = RoundedCornerShape(22.dp),
                    subtle = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "WINDOWS SINGLE INSTALLER (WI-FI UDP)",
                            style = MaterialTheme.typography.labelMedium,
                            color = PewterMuted
                        )
                        StepRow(num = "1", text = "Open $permanentUrl on your Windows PC")
                        StepRow(num = "2", text = "Download & run LinkPadHostSetup.exe (install wizard + Xbox driver)")
                        StepRow(num = "3", text = "Launch LinkPad Host (appears in joy.cpl as LinkPad Controller) & pair")
                    }
                }

                // Zero-Install Bluetooth HID Alternative
                LiquidGlassPanel(
                    shape = RoundedCornerShape(20.dp),
                    subtle = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = CobaltBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "No PC Software Needed via Bluetooth",
                                style = MaterialTheme.typography.titleMedium,
                                color = TitaniumText
                            )
                            Text(
                                text = "Pair this phone to your Windows PC or TV in Bluetooth Settings, then pick it under OS Gamepad mode.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PewterMuted
                            )
                        }
                    }
                }

                // Jump to Controller Pairing
                Button(
                    onClick = onOpenControllerDiscovery,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CobaltBlue,
                        contentColor = ObsidianBg
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("pc_open_controller_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pair Controller to PC", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun StepRow(num: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LiquidGlassPanel(shape = RoundedCornerShape(8.dp), elevated = true) {
            Text(
                text = num,
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = CobaltBlue,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = TitaniumText
        )
    }
}
