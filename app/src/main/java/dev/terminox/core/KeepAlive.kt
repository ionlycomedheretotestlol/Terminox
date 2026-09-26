package dev.terminox.core

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.PowerManager
import androidx.core.content.ContextCompat

/** terminox-lock: holds a CPU wake lock + Wi-Fi lock so shells keep working with the screen off. */
object KeepAlive {
    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null

    fun lock(context: Context) {
        val app = context.applicationContext
        acquire(app)
        Prefs.locked = true
        ContextCompat.startForegroundService(app, Intent(app, TermService::class.java))
        TermService.refresh(app)
    }

    /** Takes the locks without touching the service (called by the service itself). */
    fun acquire(context: Context) {
        val app = context.applicationContext
        if (wake?.isHeld != true) {
            wake = app.getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "terminox:lock").apply { setReferenceCounted(false); acquire() }
        }
        if (wifi?.isHeld != true) {
            @Suppress("DEPRECATION")
            wifi = (app.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "terminox:lock").apply { setReferenceCounted(false); acquire() }
        }
    }

    fun unlock(context: Context) {
        wake?.let { if (it.isHeld) it.release() }
        wifi?.let { if (it.isHeld) it.release() }
        wake = null; wifi = null
        Prefs.locked = false
        TermService.refresh(context.applicationContext)
    }
}
