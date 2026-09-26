package dev.terminox

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.terminox.core.Prefs
import dev.terminox.ui.Aurora
import dev.terminox.ui.Guide
import dev.terminox.ui.SettingsPanel
import dev.terminox.ui.TerminoxTheme
import dev.terminox.ui.WallpaperSetup
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class Shots {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, advanceMs: Long = 0, content: @Composable () -> Unit) {
        Prefs.init(RuntimeEnvironment.getApplication())
        rule.mainClock.autoAdvance = false
        rule.setContent { TerminoxTheme { content() } }
        rule.mainClock.advanceTimeBy(advanceMs)
        rule.onRoot().captureRoboImage("build/shots/$name.png")
    }

    @Test fun desktop() = shot("desktop", 1500) { FakeDesktop() }
    @Test fun desktopMaster() = shot("desktop-master", 1500) { Prefs.layout = "master"; FakeDesktop(focusIdx = 1) }
    @Test fun perms() = shot("perms", 500) { dev.terminox.ui.PermissionsScreen(dev.terminox.ui.PermState(true, false, true), {}, {}, {}, {}) }
    @Test fun wallpaper() = shot("wallpaper", 500) { WallpaperSetup({}, {}) }
    @Test fun settings() = shot("settings", 500) { Box(Modifier.fillMaxSize()) { Aurora(Modifier.fillMaxSize()); SettingsPanel({}, {}, {}) } }
    @Test fun guide() = shot("guide", 500) { Box(Modifier.fillMaxSize()) { Aurora(Modifier.fillMaxSize()); Guide {} } }
    @Test fun intro() {
        Prefs.init(RuntimeEnvironment.getApplication())
        var time by androidx.compose.runtime.mutableFloatStateOf(0f)
        rule.mainClock.autoAdvance = false
        rule.setContent { TerminoxTheme { dev.terminox.ui.IntroFrame(time) {} } }
        for (s in listOf(2.5f, 6f, 9f, 12.2f, 14.5f, 19.7f, 22.5f, 28f, 33.2f, 35.5f, 36.3f, 41f, 46.5f, 50f, 54f)) {
            time = s
            rule.mainClock.advanceTimeBy(1000)
            rule.onRoot().captureRoboImage("build/shots/intro-%05.1f.png".format(s))
        }
    }
}

private val FAKE = listOf(
    "root@terminox ~ # neofetch\n       _,met\$\$\$\$\$gg.     root@terminox\n    ,g\$\$\$\$\$\$\$\$\$\$\$\$\$P.  OS: Debian 13 trixie\n  ,g\$\$P\"     \"\"\"Y\$\$.\". Kernel: 6.1.0-android\n ,\$\$P'              `\$\$\$. Shell: bash 5.2\n',\$\$P       ,ggs.     `\$\$b: Terminal: terminox",
    "root@terminox ~ # apt install htop\nReading package lists... Done\nSetting up htop (3.3.0-5) ...\nroot@terminox ~ # _",
    "root@terminox ~ # ls /sdcard\nDCIM  Download  Music  Pictures",
)

@androidx.compose.runtime.Composable
private fun FakeDesktop(focusIdx: Int = 0) {
    val wins = listOf(dev.terminox.wm.WindowManager.Win(1), dev.terminox.wm.WindowManager.Win(2), dev.terminox.wm.WindowManager.Win(3))
    dev.terminox.wm.WindowManager.focused = focusIdx + 1
    val tr = androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0.6f) }
    val ang = androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0.785f) }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = androidx.compose.ui.geometry.Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        dev.terminox.ui.WallpaperLayer()
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().padding(top = 28.dp)) {
            dev.terminox.wm.TopBar()
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
                val g = 36f
                val rects = dev.terminox.wm.Layout.tile(Prefs.layout, 3, androidx.compose.ui.geometry.Rect(g, g, w - g, h - g), 18f, Prefs.masterRatio, focusIdx)
                val targets = wins.indices.associate { wins[it].id to rects[it] }
                val top = 28f * 3 + 36f * 3 + 60f
                wins.forEachIndexed { i, win ->
                    dev.terminox.wm.WindowFrame(win, "term ${win.id}", rects[i], targets,
                        androidx.compose.ui.geometry.Offset(0f, top), screen, tr, ang, {}) {
                        androidx.compose.material3.Text(FAKE[i], color = androidx.compose.ui.graphics.Color(0xFFE6E9F5),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp,
                            modifier = Modifier.padding(horizontal = 10.dp))
                    }
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(84.dp))
        }
        dev.terminox.wm.Dock(Modifier.align(androidx.compose.ui.Alignment.BottomStart).padding(start = 14.dp, bottom = 30.dp), {}, {}, {})
    }
}
