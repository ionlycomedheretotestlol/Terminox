package dev.terminox.term

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.ContextCompat
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import dev.terminox.core.Env
import dev.terminox.core.TermService

/** Owns every running shell; lives in the app process so shells survive the UI. */
object Sessions {
    class Term(val id: Int, val session: TerminalSession) {
        /** Bumped on every screen update so Compose can redraw the matching view. */
        val version = mutableIntStateOf(0)
    }

    val terms = mutableStateListOf<Term>()
    private var nextId = 1
    private lateinit var app: Context

    fun init(context: Context) { app = context.applicationContext }

    fun create(env: Env): Term {
        val args = env.shellArgs()
        val term = arrayOfNulls<Term>(1)
        val session = TerminalSession(args[0], env.files.absolutePath, args, env.hostEnv(), 5000, client { term[0] })
        val t = Term(nextId++, session)
        term[0] = t
        terms += t
        ContextCompat.startForegroundService(app, Intent(app, TermService::class.java))
        return t
    }

    fun close(t: Term) {
        t.session.finishIfRunning()
        terms.remove(t)
        if (terms.isEmpty()) app.stopService(Intent(app, TermService::class.java))
        else TermService.refresh(app)
    }

    private fun client(term: () -> Term?) = object : TerminalSessionClient {
        override fun onTextChanged(s: TerminalSession) { term()?.version?.let { it.intValue++ } }
        override fun onTitleChanged(s: TerminalSession) {}
        override fun onSessionFinished(s: TerminalSession) { term()?.let { close(it) } }
        override fun onCopyTextToClipboard(s: TerminalSession, text: String) {
            app.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("terminox", text))
        }
        override fun onPasteTextFromClipboard(s: TerminalSession?) {
            val clip = app.getSystemService(ClipboardManager::class.java).primaryClip ?: return
            val text = clip.getItemAt(0).coerceToText(app).toString()
            s?.emulator?.paste(text)
        }
        override fun onBell(s: TerminalSession) {}
        override fun onColorsChanged(s: TerminalSession) = Schemes.apply(s.emulator)
        override fun onTerminalCursorStateChange(state: Boolean) {}
        override fun getTerminalCursorStyle(): Int? = null
        override fun logError(tag: String?, message: String?) {}
        override fun logWarn(tag: String?, message: String?) {}
        override fun logInfo(tag: String?, message: String?) {}
        override fun logDebug(tag: String?, message: String?) {}
        override fun logVerbose(tag: String?, message: String?) {}
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
        override fun logStackTrace(tag: String?, e: Exception?) {}
    }
}
