package dev.terminox.wm

import android.os.BatteryManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.termux.view.TerminalView
import dev.terminox.core.Env
import dev.terminox.core.Prefs
import dev.terminox.term.ExtraKeys
import dev.terminox.term.Sessions
import dev.terminox.term.TerminalPane
import dev.terminox.ui.Blob
import dev.terminox.ui.GlassPanel
import dev.terminox.ui.Guide
import dev.terminox.ui.Palette
import dev.terminox.ui.SettingsPanel
import dev.terminox.ui.Themes
import dev.terminox.ui.WallpaperLayer
import dev.terminox.ui.drawBackdrop
import dev.terminox.ui.glass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

private val LAYOUTS = listOf("dwindle", "master", "grid", "columns")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Desktop(env: Env, agent: dev.terminox.ai.Agent, onPickWallpaper: (String) -> Unit, onReplayIntro: () -> Unit) {
    val density = LocalDensity.current
    var settings by remember { mutableStateOf(false) }
    var ai by remember { mutableStateOf(false) }
    var musicSearch by remember { mutableStateOf(false) }
    var focusedView by remember { mutableStateOf<TerminalView?>(null) }
    val imeVisible = WindowInsets.isImeVisible
    // Panels with text fields keep the keyboard; terminals won't grab focus while one is open.
    androidx.compose.runtime.SideEffect { dev.terminox.term.FocusGuard.overlayOpen = ai || settings || musicSearch }

    LaunchedEffect(Unit) { if (Sessions.terms.isEmpty()) Sessions.create(env) }
    val termCount = Sessions.terms.size
    LaunchedEffect(termCount) { WindowManager.sync() }

    val tr = rememberInfiniteTransition(label = "desk")
    val auroraT = tr.animateFloat(0f, (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Restart), label = "aurora")
    val angle = tr.animateFloat(0f, (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(5_000, easing = LinearEasing), RepeatMode.Restart), label = "border")

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        WallpaperLayer()
        dev.terminox.music.LyricsLayer()

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            if (Prefs.showBar && !imeVisible) TopBar(onMusic = { musicSearch = true })
            var origin by remember { mutableStateOf(Offset.Zero) }
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { origin = it.positionInRoot() }
            ) {
                val w = constraints.maxWidth.toFloat()
                val h = constraints.maxHeight.toFloat()
                val gIn = with(density) { Prefs.gapsIn.dp.toPx() }
                val tiled = WindowManager.wins.filter { !it.floating }
                val gOut = if (Prefs.smartGaps && tiled.size == 1) 0f else with(density) { Prefs.gapsOut.dp.toPx() }
                val area = Rect(gOut, gOut, w - gOut, h - gOut)
                val focusIdx = max(0, tiled.indexOfFirst { it.id == WindowManager.focused })
                val rects = Layout.tile(Prefs.layout, tiled.size, area, gIn, Prefs.masterRatio, focusIdx)
                val targets = HashMap<Int, Rect>()
                tiled.forEachIndexed { i, win -> targets[win.id] = rects[i] }
                WindowManager.wins.filter { it.floating }.forEach { win ->
                    if (win.floatRect == Rect.Zero) win.floatRect = Rect(w * 0.1f, h * 0.2f, w * 0.9f, h * 0.65f)
                    targets[win.id] = win.floatRect
                }

                WindowManager.wins.forEach { win ->
                    val term = Sessions.terms.firstOrNull { it.id == win.id } ?: return@forEach
                    val target = targets[win.id] ?: return@forEach
                    key(win.id) {
                        WindowFrame(
                            win = win, title = "term ${term.id}", target = target, targets = targets,
                            origin = origin, screen = screen, auroraT = auroraT, angle = angle,
                            onClose = { Sessions.close(term) },
                        ) { focused ->
                            TerminalPane(
                                term, Prefs.fontSize,
                                Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                                focused = focused,
                                onTap = { WindowManager.focused = win.id },
                                onView = { focusedView = it },
                            )
                        }
                    }
                }
                if (WindowManager.wins.isEmpty()) {
                    Text("Tap + to spawn a terminal", color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.Center), fontSize = 16.sp)
                }
            }
            if (imeVisible) ExtraKeys(view = { focusedView }, accent = Themes.current.a)
            else Spacer(Modifier.height(84.dp))
        }

        if (!imeVisible) {
            Dock(
                Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 14.dp, bottom = 14.dp),
                onAdd = { WindowManager.focused = Sessions.create(env).id },
                onPosition = { WindowManager.positioning = !WindowManager.positioning },
                onAi = { ai = true },
            )
            Box(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 14.dp, bottom = 14.dp)
                    .size(60.dp).glass(CircleShape).clickable { settings = true },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Settings, "Settings", tint = Color.White, modifier = Modifier.size(26.dp)) }
        }

        AnimatedVisibility(WindowManager.positioning, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp),
            enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
            PositionToolbar()
        }
        dev.terminox.music.MusicPlayerHost()
        AnimatedVisibility(musicSearch, enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut() + slideOutVertically { it / 3 }) {
            dev.terminox.music.MusicSearchSheet { musicSearch = false }
        }
        AnimatedVisibility(ai, enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut() + slideOutVertically { it / 3 }) {
            dev.terminox.ai.AiPanel(agent) { ai = false }
        }
        AnimatedVisibility(settings, enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut() + slideOutVertically { it / 3 }) {
            SettingsPanel(onClose = { settings = false }, onPickWallpaper = onPickWallpaper, onReplayIntro = onReplayIntro)
        }
        if (!Prefs.guideSeen) Guide { Prefs.guideSeen = true }
    }
}

@Composable
internal fun WindowFrame(
    win: WindowManager.Win,
    title: String,
    target: Rect,
    targets: Map<Int, Rect>,
    origin: Offset,
    screen: Size,
    auroraT: State<Float>,
    angle: State<Float>,
    onClose: () -> Unit,
    content: @Composable (focused: Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val focused = WindowManager.focused == win.id
    val theme = Themes.current
    val anim = remember {
        val c = target.center
        Animatable(Rect(c.x - target.width * 0.42f, c.y - target.height * 0.42f, c.x + target.width * 0.42f, c.y + target.height * 0.42f), Rect.VectorConverter)
    }
    val fade = remember { Animatable(0f) }
    var drag by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(target) {
        if (Prefs.animations) anim.animateTo(target, spring(Prefs.bounce, Spring.StiffnessMediumLow * Prefs.animSpeed))
        else anim.snapTo(target)
    }
    LaunchedEffect(Unit) { fade.animateTo(1f, tween(if (Prefs.animations) 240 else 0)) }

    val shape = RoundedCornerShape(Prefs.rounding.dp)
    val radius = with(density) { Prefs.rounding.dp.toPx() }
    val border = with(density) { Prefs.borderWidth.dp.toPx() }

    Box(
        Modifier
            .offset { IntOffset(target.left.roundToInt(), target.top.roundToInt()) }
            .size(with(density) { target.width.toDp() }, with(density) { target.height.toDp() })
            .zIndex((if (win.floating) 2f else 0f) + (if (focused) 1f else 0f))
            .graphicsLayer {
                val a = anim.value
                translationX = a.left - target.left + drag.x
                translationY = a.top - target.top + drag.y
                scaleX = if (target.width > 0) a.width / target.width else 1f
                scaleY = if (target.height > 0) a.height / target.height else 1f
                transformOrigin = TransformOrigin(0f, 0f)
                alpha = fade.value
                shadowElevation = 20.dp.toPx()
                this.shape = shape
                clip = true
            }
            .pointerInput(win.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    WindowManager.focused = win.id
                }
            }
            .drawBehind {
                val a = anim.value
                if (Prefs.blur) drawBackdrop(Rect(a.topLeft + origin + drag, a.size), screen, auroraT.value)
                drawRect(Color(0xFF0A0A12).copy(alpha = Prefs.glassOpacity))
                drawRect(Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.10f), 0.4f to Color.Transparent,
                    start = Offset.Zero, end = Offset(size.width * 0.6f, size.height * 0.6f)))
            }
            .drawWithContent {
                drawContent()
                if (!focused && Prefs.dimInactive > 0f) drawRect(Color.Black.copy(alpha = Prefs.dimInactive))
                val brush = if (focused) {
                    val t = if (Prefs.borderAnim) angle.value else 0.785f
                    val c = center
                    val d = Offset(cos(t), sin(t)) * (size.maxDimension / 2)
                    Brush.linearGradient(listOf(theme.a, theme.b), c - d, c + d)
                } else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.06f)))
                drawRoundRect(brush, Offset(border / 2, border / 2), Size(size.width - border, size.height - border),
                    CornerRadius(radius - border / 2), style = Stroke(if (focused) border else max(1f, border * 0.6f)))
            }
    ) {
        Column(Modifier.fillMaxSize()) {
            TitleBar(title, focused, theme.a, onClose = onClose)
            Box(Modifier.weight(1f).fillMaxWidth()) { content(focused) }
        }

        if (WindowManager.positioning) {
            Box(
                Modifier.fillMaxSize()
                    .background(theme.a.copy(alpha = 0.10f))
                    .pointerInput(win.id) {
                        detectDragGestures(
                            onDragStart = { WindowManager.focused = win.id },
                            onDrag = { change, d -> change.consume(); drag += d },
                            onDragEnd = {
                                val moved = drag
                                scope.launch { anim.snapTo(Rect(anim.value.topLeft + moved, anim.value.size)) }
                                if (win.floating) {
                                    win.floatRect = Rect(win.floatRect.topLeft + moved, win.floatRect.size)
                                } else {
                                    val c = target.center + moved
                                    targets.entries.firstOrNull { (id, r) -> id != win.id && r.contains(c) }
                                        ?.let { WindowManager.swap(win.id, it.key) }
                                }
                                drag = Offset.Zero
                            },
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text(if (win.floating) "drag to move · corner to scale" else "drag onto another window to swap",
                        color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                }
                // float / tile toggle
                Box(
                    Modifier.align(Alignment.TopEnd).padding(10.dp).size(38.dp).glass(CircleShape, alpha = 0.7f)
                        .clickable { if (!win.floating) win.floatRect = target; win.floating = !win.floating },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.PushPin, if (win.floating) "Tile" else "Float",
                        tint = if (win.floating) theme.a else Color.White, modifier = Modifier.size(18.dp))
                }
                // resize handle
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(8.dp).size(44.dp).glass(RoundedCornerShape(14.dp), alpha = 0.7f)
                        .pointerInput(win.id) {
                            detectDragGestures(
                                onDragStart = { if (!win.floating) { win.floatRect = target; win.floating = true } },
                                onDrag = { change, d ->
                                    change.consume()
                                    val r = win.floatRect
                                    val minW = 160.dp.toPx(); val minH = 120.dp.toPx()
                                    win.floatRect = Rect(r.left, r.top, max(r.left + minW, r.right + d.x), max(r.top + minH, r.bottom + d.y))
                                },
                            )
                        },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.OpenInFull, "Resize", tint = Color.White, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}

@Composable
private fun TitleBar(title: String, focused: Boolean, accent: Color, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (focused) accent else Color.White.copy(alpha = 0.3f)))
        Spacer(Modifier.width(8.dp))
        Text(title, color = Color.White.copy(alpha = if (focused) 0.9f else 0.5f), fontSize = 12.sp,
            fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.Close, "Close", tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(22.dp).clip(CircleShape).clickable(onClick = onClose).padding(3.dp))
    }
}

@Composable
internal fun TopBar(onMusic: () -> Unit = {}) {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    var battery by remember { mutableIntStateOf(-1) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            battery = ctx.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            delay(15_000)
        }
    }
    val theme = Themes.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.glass(RoundedCornerShape(50), alpha = 0.5f, elevation = 8.dp)
                .clickable { Prefs.layout = LAYOUTS[(LAYOUTS.indexOf(Prefs.layout) + 1) % LAYOUTS.size] }
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.SpaceDashboard, null, tint = theme.a, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(Prefs.layout, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        if (Prefs.musicEnabled) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(34.dp).glass(CircleShape, alpha = 0.5f).clickable(onClick = onMusic), contentAlignment = Alignment.Center) {
                Icon(androidx.compose.material.icons.Icons.Rounded.MusicNote, "Music", tint = if (dev.terminox.music.Music.current != null) theme.b else Color.White, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now),
            modifier = Modifier.glass(RoundedCornerShape(50), alpha = 0.5f, elevation = 8.dp).padding(horizontal = 16.dp, vertical = 7.dp),
            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text((if (Prefs.locked) "🔒 " else "") + "${WindowManager.wins.size} win${if (battery >= 0) "  ·  $battery%" else ""}",
            modifier = Modifier.glass(RoundedCornerShape(50), alpha = 0.5f, elevation = 8.dp).padding(horizontal = 14.dp, vertical = 7.dp),
            color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
internal fun Dock(modifier: Modifier, onAdd: () -> Unit, onPosition: () -> Unit, onAi: () -> Unit) {
    val theme = Themes.current
    Row(modifier.glass(RoundedCornerShape(30.dp), alpha = 0.5f).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DockButton(onAdd) { Icon(Icons.Rounded.Add, "New terminal", tint = Color.White, modifier = Modifier.size(28.dp)) }
        DockButton(onPosition, active = WindowManager.positioning) {
            Icon(Icons.Rounded.SpaceDashboard, "Position and scale", tint = if (WindowManager.positioning) theme.a else Color.White, modifier = Modifier.size(24.dp))
        }
        DockButton(onAi) { Blob(Modifier.size(38.dp)) }
    }
}

@Composable
private fun DockButton(onClick: () -> Unit, active: Boolean = false, content: @Composable () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(RoundedCornerShape(24.dp))
            .background(if (active) Themes.current.a.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun PositionToolbar() {
    val theme = Themes.current
    Row(
        Modifier.glass(RoundedCornerShape(50), alpha = 0.7f).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        LAYOUTS.forEach { l ->
            val sel = Prefs.layout == l
            Text(l, color = if (sel) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (sel) theme.a else Color.Transparent)
                    .clickable { Prefs.layout = l }.padding(horizontal = 10.dp, vertical = 6.dp))
        }
        Text("tile all", color = Color.White, fontSize = 12.sp,
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { WindowManager.tileAll() }.padding(horizontal = 10.dp, vertical = 6.dp))
        Box(Modifier.size(32.dp).clip(CircleShape).background(theme.b).clickable { WindowManager.positioning = false },
            contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, "Done", tint = Color.Black, modifier = Modifier.size(18.dp))
        }
    }
}
