package dev.terminox.ai

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.terminox.core.Env
import dev.terminox.core.Prefs
import dev.terminox.music.Music
import dev.terminox.term.Sessions
import dev.terminox.ui.Themes
import dev.terminox.wm.WindowManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** The blob: a Gemini agent that drives real terminals until the job is done. */
class Agent(private val env: Env) {
    sealed class Msg {
        data class User(val text: String) : Msg()
        data class Ai(val text: String) : Msg()
        data class Tool(val label: String, val ok: Boolean? = null) : Msg()
        data class Error(val text: String) : Msg()
        object RootButton : Msg()
    }

    val messages = mutableStateListOf<Msg>()
    var busy by mutableStateOf(false)
        private set
    /** A risky command waiting for the user's yes/no. */
    var confirm by mutableStateOf<Pair<String, CompletableDeferred<Boolean>>?>(null)
        private set

    private val contents = JSONArray()
    private var runId = 0

    fun reset() { messages.clear(); while (contents.length() > 0) contents.remove(0) }

    suspend fun send(text: String) {
        if (busy || text.isBlank()) return
        if (Prefs.geminiKey.isBlank()) { messages += Msg.Error("Add your Gemini API key first (Settings › AI)."); return }
        busy = true
        messages += Msg.User(text)
        contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", text))))
        try {
            for (step in 0 until Prefs.aiMaxSteps) {
                val reply = Gemini.generate(Prefs.geminiKey, Prefs.geminiModel, system(), contents, tools())
                contents.put(reply) // keep parts verbatim (thought signatures must round-trip)
                val parts = reply.optJSONArray("parts") ?: JSONArray()
                val results = JSONArray()
                for (i in 0 until parts.length()) {
                    val p = parts.getJSONObject(i)
                    p.optString("text").takeIf { it.isNotBlank() && !p.optBoolean("thought") }?.let { messages += Msg.Ai(it.trim()) }
                    p.optJSONObject("functionCall")?.let { call ->
                        val name = call.getString("name")
                        val out = runCatching { execute(name, call.optJSONObject("args") ?: JSONObject()) }
                            .getOrElse { JSONObject().put("error", it.message ?: it.toString()) }
                        results.put(JSONObject().put("functionResponse", JSONObject()
                            .put("name", name).apply { call.optString("id").takeIf { it.isNotEmpty() }?.let { put("id", it) } }
                            .put("response", out)))
                    }
                }
                if (results.length() == 0) break
                contents.put(JSONObject().put("role", "user").put("parts", results))
                if (step == Prefs.aiMaxSteps - 1) messages += Msg.Error("Stopped after ${Prefs.aiMaxSteps} steps (change in Settings › AI).")
            }
        } catch (t: Throwable) {
            messages += Msg.Error(t.message ?: t.toString())
        } finally {
            busy = false
        }
    }

    fun answerConfirm(yes: Boolean) { confirm?.second?.complete(yes); confirm = null }

    private fun terminal(id: Int?): Sessions.Term {
        Sessions.terms.firstOrNull { it.id == id }?.let { return it }
        Sessions.terms.firstOrNull { it.id == WindowManager.focused }?.let { return it }
        return Sessions.terms.lastOrNull() ?: Sessions.create(env).also { WindowManager.focused = it.id }
    }

    private suspend fun execute(name: String, a: JSONObject): JSONObject = when (name) {
        "open_terminal" -> {
            val t = Sessions.create(env)
            WindowManager.focused = t.id
            messages += Msg.Tool("opened term ${t.id}")
            delay(1500) // let bash print its prompt
            JSONObject().put("terminal_id", t.id)
        }
        "run_command" -> runCommand(a)
        "read_screen" -> {
            val t = terminal(a.optInt("terminal_id", -1).takeIf { it > 0 })
            val text = t.session.emulator?.screen?.transcriptText.orEmpty()
            JSONObject().put("terminal_id", t.id).put("screen", text.lines().takeLast(60).joinToString("\n"))
        }
        "send_input" -> {
            val t = terminal(a.optInt("terminal_id", -1).takeIf { it > 0 })
            val text = a.optString("text").replace("\\n", "\n")
            t.session.write(text)
            messages += Msg.Tool("typed into term ${t.id}: ${text.trim()}")
            delay(800)
            JSONObject().put("sent", true)
        }
        "close_terminal" -> {
            val t = terminal(a.optInt("terminal_id", -1).takeIf { it > 0 })
            Sessions.close(t)
            messages += Msg.Tool("closed term ${t.id}")
            JSONObject().put("closed", t.id)
        }
        "set_theme" -> {
            val th = Themes.all.firstOrNull { it.name.equals(a.optString("name"), true) } ?: error("Unknown theme. Options: ${Themes.all.joinToString { it.name }}")
            Themes.apply(th); messages += Msg.Tool("theme → ${th.name}")
            JSONObject().put("theme", th.name)
        }
        "set_layout" -> {
            val l = a.optString("layout").lowercase()
            require(l in listOf("dwindle", "master", "grid", "columns")) { "Layout must be dwindle, master, grid or columns" }
            Prefs.layout = l; messages += Msg.Tool("layout → $l")
            JSONObject().put("layout", l)
        }
        "show_root_button" -> {
            messages += Msg.RootButton
            JSONObject().put("shown", true).put("note", "The user now sees a GET ROOT button that verifies root access.")
        }
        "play_music" -> {
            require(Prefs.musicEnabled) { "The music player is disabled. Tell the user to enable it in Settings › Advanced." }
            val picked = Music.quickPlay(a.optString("query"))
            messages += Msg.Tool("♪ $picked")
            JSONObject().put("playing", picked)
        }
        else -> error("Unknown tool $name")
    }

    private suspend fun runCommand(a: JSONObject): JSONObject {
        val cmd = a.getString("command")
        if (Prefs.aiConfirmRisky && RISKY.containsMatchIn(cmd)) {
            val d = CompletableDeferred<Boolean>()
            confirm = cmd to d
            if (!d.await()) { messages += Msg.Tool("✗ you declined: $cmd", false); return JSONObject().put("error", "The user declined to run this command.") }
        }
        val t = terminal(a.optInt("terminal_id", -1).takeIf { it > 0 })
        WindowManager.focused = t.id
        val id = ++runId
        val out = File(env.guestTmp, ".tx-$id.out").apply { delete() }
        val rc = File(env.guestTmp, ".tx-$id.rc").apply { delete() }
        messages += Msg.Tool("term ${t.id} ▸ $cmd")
        // Leading space keeps it out of shell history.
        t.session.write(" __tx $id '${cmd.replace("'", "'\\''")}'\r")
        val timeout = a.optInt("timeout_seconds", 180).coerceIn(5, 900) * 1000L
        var waited = 0L
        while (!rc.exists() && waited < timeout) { delay(300); waited += 300 }
        val output = clean(if (out.exists()) out.readText() else "")
        return if (rc.exists()) {
            val code = rc.readText().trim().toIntOrNull() ?: -1
            messages[messages.lastIndex] = Msg.Tool("term ${t.id} ▸ $cmd", code == 0)
            JSONObject().put("terminal_id", t.id).put("exit_code", code).put("output", output)
        } else {
            JSONObject().put("terminal_id", t.id).put("status", "still running after ${timeout / 1000}s")
                .put("output_so_far", output)
        }
    }

    private fun clean(s: String): String {
        val noAnsi = s.replace(Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]"), "").replace("\r", "")
        return if (noAnsi.length > 6000) "…" + noAnsi.takeLast(6000) else noAnsi
    }

    private fun system() = """
        You are "the blob", the assistant inside Terminox, a Hyprland-style tiling terminal app for Android.
        Terminals run a real Debian 13 system through proot. The user is root inside it (fake root: no real
        device root). Package manager: apt (always use -y; run `apt update` first if a package isn't found).
        Phone storage is at /sdcard when permitted.

        Work autonomously: use run_command to act, read the output, and fix problems yourself. If a command
        is missing (exit code 127 or "command not found"), install the package that provides it and retry.
        Prefer non-interactive flags. For interactive prompts use send_input. Open a new terminal when the
        user asks for one; otherwise use the focused one.
        If the user asks for root, an "upgrade", admin powers or similar, call show_root_button.
        Current state: ${Sessions.terms.size} terminal(s) open; theme ${Prefs.theme}; layout ${Prefs.layout};
        music player ${if (Prefs.musicEnabled) "enabled" else "disabled"}; device root ${if (Prefs.rootGranted) "verified" else "not verified"}.
        Keep replies short, friendly and a little playful. Summarize what you did at the end.
    """.trimIndent()

    private fun tools() = JSONArray().apply {
        put(Gemini.fn("open_terminal", "Open a new terminal window. Returns its terminal_id."))
        put(Gemini.fn("run_command", "Type a bash command into a terminal, wait for it to finish, and return exit_code and output.",
            Triple("command", "string", "The bash command"),
            Triple("terminal_id", "integer", "Target terminal (default: focused)"),
            Triple("timeout_seconds", "integer", "Max seconds to wait (default 180)"),
            required = listOf("command")))
        put(Gemini.fn("read_screen", "Read the recent text on a terminal's screen.",
            Triple("terminal_id", "integer", "Target terminal (default: focused)")))
        put(Gemini.fn("send_input", "Type raw text into a terminal (use \\n for Enter), e.g. to answer a prompt.",
            Triple("text", "string", "Text to type"), Triple("terminal_id", "integer", "Target terminal"),
            required = listOf("text")))
        put(Gemini.fn("close_terminal", "Close a terminal window.", Triple("terminal_id", "integer", "Terminal to close"), required = listOf("terminal_id")))
        put(Gemini.fn("set_theme", "Change the desktop theme.", Triple("name", "string", Themes.all.joinToString { it.name }), required = listOf("name")))
        put(Gemini.fn("set_layout", "Change the tiling layout.", Triple("layout", "string", "dwindle, master, grid or columns"), required = listOf("layout")))
        put(Gemini.fn("show_root_button", "Show the user a giant GET ROOT button that checks whether the device is rooted."))
        put(Gemini.fn("play_music", "Search a song and start playing it with live lyrics.", Triple("query", "string", "Song and artist"), required = listOf("query")))
    }

    companion object {
        private val RISKY = Regex("""rm\s+-[a-zA-Z]*r[a-zA-Z]*f?\s+/(\s|$)|rm\s+-rf\s+(~|/sdcard|/storage)|mkfs|dd\s+if=|:\(\)\s*\{|>\s*/dev/(sd|block)""")
    }
}
