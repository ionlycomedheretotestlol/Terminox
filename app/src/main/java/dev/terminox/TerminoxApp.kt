package dev.terminox

import android.app.Application
import dev.terminox.ai.Agent
import dev.terminox.core.Env
import dev.terminox.core.Installer
import dev.terminox.core.Ipc
import dev.terminox.core.KeepAlive
import dev.terminox.core.Prefs
import dev.terminox.core.Scripts
import dev.terminox.core.TermService
import dev.terminox.music.Music
import dev.terminox.term.Sessions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TerminoxApp : Application() {
    lateinit var env: Env
    lateinit var installer: Installer
    lateinit var agent: Agent
    private var ipc: Ipc? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        env = Env(this)
        installer = Installer(env)
        agent = Agent(env)
        Sessions.init(this)
        TermService.channel(this)
        onDebianReady()
    }

    /** Refreshes helper scripts and starts listening to them. Safe to call repeatedly. */
    fun onDebianReady() {
        if (!env.isInstalled || ipc != null) return
        Scripts.install(env)
        ipc = Ipc(env.ipcDir, ::onCommand).also { it.start(); Music.ipc = it }
        if (Prefs.locked) KeepAlive.lock(this)
    }

    private fun onCommand(args: List<String>) {
        val ipc = ipc ?: return
        when (args[0]) {
            "lock" -> KeepAlive.lock(this)
            "unlock" -> KeepAlive.unlock(this)
            "music-search" -> {
                if (!Prefs.musicEnabled) {
                    ipc.reply("music-results", listOf("ERROR", "Music Player is off. Enable it in Settings › Advanced."))
                    return
                }
                scope.launch {
                    runCatching { Music.search(args.getOrElse(1) { "" }) }
                        .onSuccess { r -> ipc.reply("music-results", r.map { "${it.title} — ${it.artist} (${Music.fmt(it.durationSec * 1000L)})" }) }
                        .onFailure { ipc.reply("music-results", listOf("ERROR", "Search failed: ${it.message}")) }
                }
            }
            "music-pick" -> args.getOrNull(1)?.toIntOrNull()?.let { Music.results.getOrNull(it) }?.let { Music.play(it) }
            "music-toggle" -> Music.toggle()
            "music-stop" -> Music.stop()
        }
    }
}
