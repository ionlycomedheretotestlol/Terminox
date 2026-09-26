package dev.terminox.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import dev.terminox.MainActivity
import dev.terminox.R
import dev.terminox.term.Sessions

/** Foreground service that keeps shells alive while the app is in the background. */
class TermService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(ID, build(this))
        if (Prefs.locked) KeepAlive.acquire(this)
        return START_NOT_STICKY
    }

    companion object {
        private const val ID = 1
        private const val CHANNEL = "sessions"

        fun channel(context: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                    NotificationChannel(CHANNEL, "Terminal sessions", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }

        fun refresh(context: Context) =
            context.getSystemService(NotificationManager::class.java).notify(ID, build(context))

        private fun build(context: Context): Notification {
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = Sessions.terms.size
            val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL)
            else @Suppress("DEPRECATION") Notification.Builder(context)
            return builder
                .setSmallIcon(R.drawable.ic_stat_terminox)
                .setContentTitle("Terminox")
                .setContentText("$n terminal${if (n == 1) "" else "s"} running" + if (Prefs.locked) " · 🔒 locked" else "")
                .setContentIntent(open)
                .setOngoing(true)
                .build()
        }
    }
}
