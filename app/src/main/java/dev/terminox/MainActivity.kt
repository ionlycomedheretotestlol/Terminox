package dev.terminox

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import dev.terminox.core.Prefs
import dev.terminox.ui.Intro
import dev.terminox.ui.LoadingScreen
import dev.terminox.ui.TerminoxTheme
import dev.terminox.ui.Wallpaper
import dev.terminox.ui.WallpaperSetup
import dev.terminox.wm.Desktop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var pendingType = "image"
    private var installed by mutableStateOf(false)
    private var replayIntro by mutableStateOf(false)

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { importWallpaper(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as TerminoxApp
        installed = app.env.isInstalled
        lifecycleScope.launch(Dispatchers.Default) { Wallpaper.load(this@MainActivity) }

        setContent {
            TerminoxTheme {
                val stage = when {
                    !Prefs.introSeen || replayIntro -> "intro"
                    !Prefs.wallpaperChosen -> "wallpaper"
                    !installed -> "loading"
                    else -> "desktop"
                }
                Crossfade(stage, animationSpec = tween(600), label = "stage") { s ->
                    when (s) {
                        "intro" -> Intro { Prefs.introSeen = true; replayIntro = false }
                        "wallpaper" -> WallpaperSetup(onPick = ::pick, onAurora = { Prefs.wallpaperType = "aurora"; Prefs.wallpaperChosen = true })
                        "loading" -> LoadingScreen(app.installer) { installed = true; app.onDebianReady() }
                        else -> Desktop(app.env, app.agent, onPickWallpaper = ::pick, onReplayIntro = { replayIntro = true })
                    }
                }
            }
        }
    }

    private fun pick(type: String) {
        pendingType = type
        picker.launch(if (type == "video") "video/*" else "image/*")
    }

    private fun importWallpaper(uri: Uri) {
        val type = pendingType
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val out = if (type == "video") Wallpaper.videoFile(this@MainActivity) else Wallpaper.imageFile(this@MainActivity)
                    contentResolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
                }.isSuccess
            }
            if (!ok) return@launch
            Prefs.wallpaperType = type
            withContext(Dispatchers.Default) { Wallpaper.load(this@MainActivity) }
            Prefs.wallpaperChosen = true
        }
    }
}
