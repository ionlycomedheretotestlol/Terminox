package dev.terminox.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Settings as Compose state, persisted to SharedPreferences. */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) { sp = context.getSharedPreferences("terminox", Context.MODE_PRIVATE) }

    private class Pref<T>(private val key: String, private val default: T) : ReadWriteProperty<Any?, T> {
        private val state by lazy { mutableStateOf(load()) }

        @Suppress("UNCHECKED_CAST")
        private fun load(): T = when (default) {
            is Boolean -> sp.getBoolean(key, default)
            is Int -> sp.getInt(key, default)
            is Float -> sp.getFloat(key, default)
            is String -> sp.getString(key, default)
            else -> default
        } as T

        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state.value = value
            sp.edit().apply {
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Float -> putFloat(key, value)
                    is String -> putString(key, value)
                }
            }.apply()
        }
    }

    // flow
    var introSeen by Pref("introSeen", false)
    var wallpaperChosen by Pref("wallpaperChosen", false)
    var guideSeen by Pref("guideSeen", false)

    // wallpaper: "aurora" | "image" | "video"
    var wallpaperType by Pref("wallpaperType", "aurora")
    var wallpaperDim by Pref("wallpaperDim", 0.15f)

    // look
    var theme by Pref("theme", "Hyprland")
    var gapsIn by Pref("gapsIn", 6)
    var gapsOut by Pref("gapsOut", 12)
    var rounding by Pref("rounding", 18)
    var borderWidth by Pref("borderWidth", 2)
    var glassOpacity by Pref("glassOpacity", 0.55f)
    var blur by Pref("blur", true)
    var borderAnim by Pref("borderAnim", true)
    var dimInactive by Pref("dimInactive", 0.12f)
    var showBar by Pref("showBar", true)

    // layout + motion: "dwindle" | "master" | "grid" | "columns"
    var layout by Pref("layout", "dwindle")
    var masterRatio by Pref("masterRatio", 0.55f)
    var smartGaps by Pref("smartGaps", false)
    var animations by Pref("animations", true)
    var animSpeed by Pref("animSpeed", 1f)
    var bounce by Pref("bounce", 0.72f)

    // terminal
    var fontSize by Pref("fontSize", 26)
    var termScheme by Pref("termScheme", "Terminox")
}
