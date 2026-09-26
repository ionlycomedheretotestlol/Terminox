package dev.terminox.core

import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import java.io.File

/** Receives commands that guest scripts drop into /run/terminox (see terminox-ipc). */
class Ipc(private val dir: File, private val handle: (List<String>) -> Unit) {
    private val main = Handler(Looper.getMainLooper())

    @Suppress("DEPRECATION")
    private val observer = object : FileObserver(dir.path, CLOSE_WRITE or MOVED_TO) {
        override fun onEvent(event: Int, path: String?) {
            if (path == null || !path.startsWith("cmd.")) return
            consume(File(dir, path))
        }
    }

    fun start() {
        dir.mkdirs()
        observer.startWatching()
        dir.listFiles { f -> f.name.startsWith("cmd.") }?.forEach { consume(it) }
    }

    /** Writes a reply file the guest can read, atomically. */
    fun reply(name: String, lines: List<String>) {
        val tmp = File(dir, ".$name.tmp")
        tmp.writeText(lines.joinToString("\n", postfix = "\n"))
        tmp.renameTo(File(dir, name))
    }

    private fun consume(f: File) {
        val lines = runCatching { f.readLines() }.getOrNull() ?: return
        f.delete()
        if (lines.isNotEmpty()) main.post { handle(lines) }
    }
}
