package dev.terminox.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Checks whether the device grants su (the user approves it in their root manager). */
object Root {
    suspend fun check(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val p = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
            if (!p.waitFor(20, TimeUnit.SECONDS)) { p.destroy(); return@runCatching false }
            p.inputStream.bufferedReader().readText().contains("uid=0")
        }.getOrDefault(false)
    }
}
