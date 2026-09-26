package dev.terminox.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.view.TextureView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import dev.terminox.core.Prefs
import java.io.File
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Wallpaper files live in app storage so they survive the picker's URI expiring. */
object Wallpaper {
    fun imageFile(ctx: Context) = File(ctx.filesDir, "wallpaper.img")
    fun videoFile(ctx: Context) = File(ctx.filesDir, "wallpaper.vid")

    /** Sharp and blurred versions of the image wallpaper (blur feeds the glass). */
    val sharp = mutableStateOf<ImageBitmap?>(null)
    val blurred = mutableStateOf<ImageBitmap?>(null)

    fun load(ctx: Context) {
        sharp.value = null; blurred.value = null
        if (Prefs.wallpaperType != "image") return
        val f = imageFile(ctx)
        if (!f.exists()) return
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, opts)
        val sample = max(1, max(opts.outWidth, opts.outHeight) / 2400)
        val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return
        sharp.value = bmp.asImageBitmap()
        blurred.value = blur(bmp).asImageBitmap()
    }

    /** Cheap, good-looking blur: downscale, 3 box-blur passes, let bilinear upscaling finish it. */
    private fun blur(src: Bitmap): Bitmap {
        val w = 96
        val h = max(1, src.height * w / src.width)
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        repeat(3) { boxBlur(px, w, h, 3) }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
        val out = IntArray(px.size)
        fun pass(get: (Int, Int) -> Int, set: (Int, Int, Int) -> Unit, len: Int, lines: Int) {
            for (l in 0 until lines) for (i in 0 until len) {
                var rs = 0; var gs = 0; var bs = 0; var n = 0
                for (k in -r..r) {
                    val j = (i + k).coerceIn(0, len - 1)
                    val c = get(l, j)
                    rs += c shr 16 and 0xFF; gs += c shr 8 and 0xFF; bs += c and 0xFF; n++
                }
                set(l, i, (0xFF shl 24) or (rs / n shl 16) or (gs / n shl 8) or (bs / n))
            }
        }
        pass({ y, x -> px[y * w + x] }, { y, x, c -> out[y * w + x] = c }, w, h)
        pass({ x, y -> out[y * w + x] }, { x, y, c -> px[y * w + x] = c }, h, w)
    }
}

/** Full-screen wallpaper layer. */
@Composable
fun WallpaperLayer(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Box(modifier.fillMaxSize().background(Color.Black)) {
        when (Prefs.wallpaperType) {
            "image" -> Wallpaper.sharp.value?.let {
                Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } ?: Aurora(Modifier.fillMaxSize())
            "video" -> VideoWallpaper(Wallpaper.videoFile(ctx), Modifier.fillMaxSize())
            else -> Aurora(Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = Prefs.wallpaperDim)))
    }
}

/** Animated mesh-gradient wallpaper in the current theme's colors. */
@Composable
fun Aurora(modifier: Modifier = Modifier, colors: List<Color> = Themes.current.aurora) {
    val t by rememberInfiniteTransition(label = "aurora").animateFloat(
        0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Restart), label = "t"
    )
    Canvas(modifier) { drawAurora(colors, t, Offset.Zero) }
}

fun DrawScope.drawAurora(colors: List<Color>, t: Float, shift: Offset, screen: androidx.compose.ui.geometry.Size = size) {
    drawRect(colors[0])
    val blobs = listOf(
        Triple(0.2f, 0.25f, colors[1]), Triple(0.8f, 0.3f, colors[2]),
        Triple(0.35f, 0.8f, colors[3]), Triple(0.75f, 0.75f, colors[1]),
    )
    blobs.forEachIndexed { i, (x, y, c) ->
        val cx = screen.width * (x + 0.12f * sin(t + i * 1.7f)) - shift.x
        val cy = screen.height * (y + 0.10f * cos(t * 0.8f + i)) - shift.y
        val r = max(screen.width, screen.height) * 0.62f
        drawCircle(Brush.radialGradient(listOf(c, c.copy(alpha = 0.45f), Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
    }
}

/**
 * Draws the part of the wallpaper that sits behind [area] (screen coordinates),
 * frosted — this is what makes windows look like glass.
 */
fun DrawScope.drawBackdrop(area: Rect, screen: androidx.compose.ui.geometry.Size, auroraT: Float) {
    val img = if (Prefs.wallpaperType == "image") Wallpaper.blurred.value else null
    if (img != null) {
        // Same center-crop mapping as the sharp wallpaper.
        val s = max(screen.width / img.width, screen.height / img.height)
        val ox = (screen.width - img.width * s) / 2
        val oy = (screen.height - img.height * s) / 2
        val sx = ((area.left - ox) / s).toInt().coerceIn(0, img.width - 1)
        val sy = ((area.top - oy) / s).toInt().coerceIn(0, img.height - 1)
        val sw = (area.width / s).toInt().coerceIn(1, img.width - sx)
        val sh = (area.height / s).toInt().coerceIn(1, img.height - sy)
        drawImage(img, IntOffset(sx, sy), IntSize(sw, sh), IntOffset.Zero, IntSize(size.width.toInt(), size.height.toInt()))
    } else if (Prefs.wallpaperType != "video") {
        drawAurora(Themes.current.aurora, auroraT, area.topLeft, screen)
    }
}

@Composable
private fun VideoWallpaper(file: File, modifier: Modifier) {
    val ctx = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            if (file.exists()) { setMediaItem(MediaItem.fromUri(file.toURI().toString())); prepare(); playWhenReady = true }
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    AndroidView(modifier = modifier, factory = { c ->
        TextureView(c).also { tv ->
            player.setVideoTextureView(tv)
            player.addListener(object : Player.Listener {
                override fun onVideoSizeChanged(v: VideoSize) = centerCrop(tv, v)
            })
            tv.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> centerCrop(tv, player.videoSize) }
        }
    })
}

private fun centerCrop(tv: TextureView, v: VideoSize) {
    if (v.width == 0 || tv.width == 0) return
    val vw = v.width * v.pixelWidthHeightRatio
    val s = max(tv.width / vw, tv.height / v.height.toFloat())
    val m = Matrix()
    m.setScale(vw * s / tv.width, v.height * s / tv.height, tv.width / 2f, tv.height / 2f)
    tv.setTransform(m)
}
