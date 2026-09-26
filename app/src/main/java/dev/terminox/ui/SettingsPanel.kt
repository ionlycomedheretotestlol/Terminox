package dev.terminox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.terminox.core.Prefs
import dev.terminox.term.Schemes
import kotlin.math.roundToInt

@Composable
fun SettingsPanel(onClose: () -> Unit, onPickWallpaper: (String) -> Unit, onReplayIntro: () -> Unit) {
    val theme = Themes.current
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
        GlassPanel(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(10.dp),
            shape = RoundedCornerShape(30.dp), alpha = 0.78f
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Settings", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).clickable(onClick = onClose),
                        contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, "Close", tint = Color.White) }
                }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp)) {

                    Section("Theme")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Themes.all.forEach { t ->
                            val sel = t.name == Prefs.theme
                            Column(
                                Modifier.width(112.dp).clip(RoundedCornerShape(18.dp))
                                    .border(2.dp, if (sel) Brush.linearGradient(listOf(t.a, t.b)) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)), RoundedCornerShape(18.dp))
                                    .background(Color.White.copy(alpha = 0.06f)).clickable { Themes.apply(t) }.padding(10.dp)
                            ) {
                                Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp))
                                    .background(Brush.linearGradient(t.aurora)))
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(t.a))
                                    Spacer(Modifier.width(4.dp))
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(t.b))
                                    Spacer(Modifier.width(6.dp))
                                    Text(t.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }
                            }
                        }
                    }

                    Section("Wallpaper")
                    Chips(listOf("aurora" to "Aurora", "image" to "Image…", "video" to "Video…"), Prefs.wallpaperType) {
                        if (it == "aurora") Prefs.wallpaperType = "aurora" else onPickWallpaper(it)
                    }
                    Slide("Dim", Prefs.wallpaperDim, 0f..0.8f, pct = true) { Prefs.wallpaperDim = it }

                    Section("Windows")
                    Slide("Glass opacity", Prefs.glassOpacity, 0f..1f, pct = true) { Prefs.glassOpacity = it }
                    Slide("Rounding", Prefs.rounding.toFloat(), 0f..36f) { Prefs.rounding = it.roundToInt() }
                    Slide("Border width", Prefs.borderWidth.toFloat(), 0f..6f) { Prefs.borderWidth = it.roundToInt() }
                    Slide("Gaps inside", Prefs.gapsIn.toFloat(), 0f..30f) { Prefs.gapsIn = it.roundToInt() }
                    Slide("Gaps outside", Prefs.gapsOut.toFloat(), 0f..40f) { Prefs.gapsOut = it.roundToInt() }
                    Slide("Dim inactive", Prefs.dimInactive, 0f..0.6f, pct = true) { Prefs.dimInactive = it }
                    Toggle("Frosted glass (blur)", Prefs.blur) { Prefs.blur = it }
                    Toggle("Animated border", Prefs.borderAnim) { Prefs.borderAnim = it }
                    Toggle("Top bar", Prefs.showBar) { Prefs.showBar = it }

                    Section("Layout")
                    Chips(listOf("dwindle" to "Dwindle", "master" to "Master", "grid" to "Grid", "columns" to "Columns"), Prefs.layout) { Prefs.layout = it }
                    Slide("Master size", Prefs.masterRatio, 0.3f..0.8f, pct = true) { Prefs.masterRatio = it }
                    Toggle("Smart gaps (no gaps with one window)", Prefs.smartGaps) { Prefs.smartGaps = it }

                    Section("Animations")
                    Toggle("Enabled", Prefs.animations) { Prefs.animations = it }
                    Slide("Speed", Prefs.animSpeed, 0.3f..3f) { Prefs.animSpeed = it }
                    Slide("Bounciness", 1f - Prefs.bounce, 0f..0.6f, pct = true) { Prefs.bounce = 1f - it }

                    Section("Terminal")
                    Slide("Font size (or pinch a terminal)", Prefs.fontSize.toFloat(), dev.terminox.term.MIN_FONT.toFloat()..dev.terminox.term.MAX_FONT.toFloat()) { Prefs.fontSize = it.roundToInt() }
                    Chips(Schemes.all.map { it.name to it.name }, Prefs.termScheme) { Prefs.termScheme = it }

                    Section("AI (the blob)")
                    var keyDraft by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
                    Text(if (Prefs.geminiKey.isBlank()) "No Gemini key set" else "Key: ${Prefs.geminiKey.take(5)}…${Prefs.geminiKey.takeLast(4)}",
                        color = Palette.dim, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    dev.terminox.music.GlassField(keyDraft, { keyDraft = it }, "Paste a new Gemini API key", androidx.compose.ui.text.input.ImeAction.Done) {
                        if (keyDraft.isNotBlank()) { Prefs.geminiKey = keyDraft.trim(); keyDraft = "" }
                    }
                    Chips(listOf("gemini-3.8-flash" to "3.8 Flash", "gemini-3.7-flash" to "3.7 Flash", "gemini-3.6-flash" to "3.6 Flash"), Prefs.geminiModel) { Prefs.geminiModel = it }
                    Slide("Max autonomous steps", Prefs.aiMaxSteps.toFloat(), 3f..30f) { Prefs.aiMaxSteps = it.roundToInt() }
                    Toggle("Ask before destructive commands", Prefs.aiConfirmRisky) { Prefs.aiConfirmRisky = it }

                    Section("Advanced")
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    Toggle("terminox-lock (run forever in background)", Prefs.locked) {
                        if (it) dev.terminox.core.KeepAlive.lock(ctx) else dev.terminox.core.KeepAlive.unlock(ctx)
                    }
                    Text("Exclude from battery optimization", color = theme.a, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                            runCatching {
                                ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    android.net.Uri.parse("package:${ctx.packageName}")))
                            }
                        }.padding(vertical = 10.dp))
                    Toggle("Music Player", Prefs.musicEnabled) { Prefs.musicEnabled = it }
                    if (Prefs.musicEnabled) {
                        Chips(listOf("background" to "Background lyrics", "command" to "Command (ter-music)"), Prefs.musicMode) { Prefs.musicMode = it }
                        Slide("Lyrics offset (ms)", Prefs.lyricOffsetMs.toFloat(), -2000f..2000f) { Prefs.lyricOffsetMs = (it / 50).roundToInt() * 50 }
                    }

                    Section("About")
                    Text("Replay intro", color = theme.a, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onReplayIntro).padding(vertical = 10.dp))
                    Text("Replay guide", color = theme.a, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { Prefs.guideSeen = false; onClose() }.padding(vertical = 10.dp))
                    Text("Terminox · Debian via proot · terminal by termux (Apache-2.0)", color = Palette.dim, fontSize = 12.sp)
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(22.dp))
    Text(title.uppercase(), color = Themes.current.a, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun Slide(label: String, value: Float, range: ClosedFloatingPointRange<Float>, pct: Boolean = false, onChange: (Float) -> Unit) {
    val theme = Themes.current
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row {
            Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(if (pct) "${(value * 100).roundToInt()}%" else if (range.endInclusive <= 5f) "%.1f".format(value) else "${value.roundToInt()}",
                color = Palette.dim, fontSize = 13.sp)
        }
        Slider(value, onChange, valueRange = range, colors = SliderDefaults.colors(
            thumbColor = Color.White, activeTrackColor = theme.a, inactiveTrackColor = Color.White.copy(alpha = 0.15f)))
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onChange(!value) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(value, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Themes.current.a, checkedThumbColor = Color.White,
            uncheckedTrackColor = Color.White.copy(alpha = 0.1f), uncheckedBorderColor = Color.White.copy(alpha = 0.2f)))
    }
}

@Composable
private fun Chips(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (id, label) ->
            val sel = id == selected
            Text(label, color = if (sel) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (sel) Themes.current.a else Color.White.copy(alpha = 0.08f))
                    .clickable { onSelect(id) }.padding(horizontal = 16.dp, vertical = 9.dp))
        }
    }
}
