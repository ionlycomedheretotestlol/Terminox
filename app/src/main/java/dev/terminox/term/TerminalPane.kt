package dev.terminox.term

import android.content.Context
import android.graphics.Typeface
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

/** Sticky modifier keys shared by the extra-keys row and the terminal view. */
object Modifiers {
    var ctrl by mutableStateOf(false)
    var alt by mutableStateOf(false)
}

@Composable
fun TerminalPane(term: Sessions.Term, fontSize: Int, modifier: Modifier = Modifier, onView: (TerminalView) -> Unit = {}) {
    val version = term.version.intValue
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TerminalView(ctx, null).apply {
                setTerminalViewClient(ViewClient(this))
                setTextSize(fontSize)
                setTypeface(Typeface.MONOSPACE)
                isFocusable = true
                isFocusableInTouchMode = true
                attachSession(term.session)
                requestFocus()
            }
        },
        update = { v ->
            if (v.currentSession !== term.session) v.attachSession(term.session)
            v.setTextSize(fontSize)
            // Reading `version` makes new shell output re-run this block.
            if (version >= 0) v.onScreenUpdated()
            onView(v)
        }
    )
}

private class ViewClient(private val view: TerminalView) : TerminalViewClient {
    private var scale = 1f

    override fun onScale(s: Float): Float {
        scale = (scale * s).coerceIn(0.5f, 3f)
        return scale
    }

    override fun onSingleTapUp(e: MotionEvent) {
        view.requestFocus()
        view.context.getSystemService(InputMethodManager::class.java)
            .showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun shouldBackButtonBeMappedToEscape() = false
    override fun shouldEnforceCharBasedInput() = true
    override fun shouldUseCtrlSpaceWorkaround() = false
    override fun isTerminalViewSelected() = true
    override fun copyModeChanged(copyMode: Boolean) {}
    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession) = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent) = false
    override fun onLongPress(event: MotionEvent) = false

    override fun readControlKey(): Boolean = Modifiers.ctrl.also { if (it) Modifiers.ctrl = false }
    override fun readAltKey(): Boolean = Modifiers.alt.also { if (it) Modifiers.alt = false }
    override fun readShiftKey() = false
    override fun readFnKey() = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession) = false
    override fun onEmulatorSet() {}
    override fun logError(tag: String?, message: String?) {}
    override fun logWarn(tag: String?, message: String?) {}
    override fun logInfo(tag: String?, message: String?) {}
    override fun logDebug(tag: String?, message: String?) {}
    override fun logVerbose(tag: String?, message: String?) {}
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
    override fun logStackTrace(tag: String?, e: Exception?) {}
}

fun Context.hideKeyboard(view: TerminalView) =
    getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(view.windowToken, 0)
