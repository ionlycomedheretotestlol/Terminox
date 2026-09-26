package dev.terminox.term

import android.content.Context
import android.graphics.Typeface
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import dev.terminox.core.Prefs
import androidx.compose.ui.unit.sp

const val MIN_FONT = 5
const val MAX_FONT = 28

/** True while a panel with its own text field is open: terminals must not steal focus then. */
object FocusGuard {
    var overlayOpen by mutableStateOf(false)
}

/** Sticky modifier keys shared by the extra-keys row and the terminal view. */
object Modifiers {
    var ctrl by mutableStateOf(false)
    var alt by mutableStateOf(false)
}

@Composable
fun TerminalPane(
    term: Sessions.Term,
    fontSize: Int,
    modifier: Modifier = Modifier,
    focused: Boolean = true,
    onTap: () -> Unit = {},
    onView: (TerminalView) -> Unit = {},
) {
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { fontSize.sp.roundToPx() }
    val scheme = Schemes.current()
    var viewRef by remember { mutableStateOf<TerminalView?>(null) }
    LaunchedEffect(scheme.name, viewRef) { viewRef?.let { Schemes.apply(it.mEmulator); it.invalidate() } }
    androidx.compose.runtime.DisposableEffect(term) { onDispose { if (term.view === viewRef) term.view = null } }
    // Take focus only when this window becomes focused (or a panel closes), not on output.
    LaunchedEffect(focused, FocusGuard.overlayOpen, viewRef) {
        if (focused && !FocusGuard.overlayOpen) viewRef?.let { it.requestFocus(); onView(it) }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TerminalView(ctx, null).apply {
                // Own GPU layer: the renderer clears with a transparent color, which must not
                // punch through the glass drawn behind the view.
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                setTerminalViewClient(ViewClient(this, onTap))
                setTextSize(px)
                setTypeface(Typeface.MONOSPACE)
                isFocusable = true
                isFocusableInTouchMode = true
                attachSession(term.session)
                Schemes.apply(mEmulator)
                tag = px
                term.view = this
                viewRef = this
            }
        },
        update = { v ->
            if (v.currentSession !== term.session) v.attachSession(term.session)
            term.view = v
            // setTextSize rebuilds the renderer: only when the size actually changed.
            if (v.tag != px) { v.setTextSize(px); v.tag = px }
            // Only report the view here; focus is requested on changes (below), never on every redraw,
            // or a terminal printing output would yank the keyboard away from other text fields.
            if (focused) onView(v)
        }
    )
}

private class ViewClient(private val view: TerminalView, private val onTap: () -> Unit) : TerminalViewClient {
    /**
     * Pinch zoom. The view accumulates the gesture's scale; once it passes ±10% we step the font
     * one sp and reset, so zooming feels continuous. The size is global and saved.
     */
    override fun onScale(scale: Float): Float {
        if (scale < 0.9f || scale > 1.1f) {
            Prefs.fontSize = (Prefs.fontSize + if (scale > 1f) 1 else -1).coerceIn(MIN_FONT, MAX_FONT)
            return 1f
        }
        return scale
    }

    override fun onSingleTapUp(e: MotionEvent) {
        onTap()
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
    override fun onEmulatorSet() { Schemes.apply(view.mEmulator) }
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
