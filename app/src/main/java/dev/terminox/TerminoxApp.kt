package dev.terminox

import android.app.Application
import dev.terminox.core.Env
import dev.terminox.core.Installer
import dev.terminox.core.TermService
import dev.terminox.term.Sessions

class TerminoxApp : Application() {
    lateinit var env: Env
    lateinit var installer: Installer

    override fun onCreate() {
        super.onCreate()
        env = Env(this)
        installer = Installer(env)
        Sessions.init(this)
        TermService.channel(this)
    }
}
