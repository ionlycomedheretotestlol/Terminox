package dev.terminox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.terminox.ui.LoadingScreen
import dev.terminox.ui.TerminalScreen
import dev.terminox.ui.TerminoxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as TerminoxApp
        setContent {
            TerminoxTheme {
                var installed by remember { mutableStateOf(app.env.isInstalled) }
                if (installed) TerminalScreen(app.env)
                else LoadingScreen(app.installer) { installed = true }
            }
        }
    }
}
