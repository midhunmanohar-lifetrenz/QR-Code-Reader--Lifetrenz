package com.lifetrenz.qrreader.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Suffix { ENTER, TAB, NONE }

data class Settings(
    val suffix: Suffix = Suffix.ENTER,
    val prefix: String = "",
    val trim: Boolean = true,
    val dupWindowMs: Long = 1500,
    val beep: Boolean = true,
    val vibrate: Boolean = true,
    val keepAwake: Boolean = true,
    val continuous: Boolean = true,
    /** Delete history entries older than this many days. 0 keeps everything. */
    val autoClearDays: Int = 7,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(load())
    val flow: StateFlow<Settings> = _flow.asStateFlow()

    private fun load(): Settings {
        val d = Settings()
        return Settings(
            suffix = runCatching { Suffix.valueOf(prefs.getString("suffix", d.suffix.name)!!) }.getOrDefault(d.suffix),
            prefix = prefs.getString("prefix", d.prefix) ?: d.prefix,
            trim = prefs.getBoolean("trim", d.trim),
            dupWindowMs = prefs.getLong("dup", d.dupWindowMs),
            beep = prefs.getBoolean("beep", d.beep),
            vibrate = prefs.getBoolean("vibrate", d.vibrate),
            keepAwake = prefs.getBoolean("keepAwake", d.keepAwake),
            continuous = prefs.getBoolean("continuous", d.continuous),
            autoClearDays = prefs.getInt("autoClearDays", d.autoClearDays),
        )
    }

    fun update(change: (Settings) -> Settings) {
        val s = change(_flow.value)
        _flow.value = s
        prefs.edit()
            .putString("suffix", s.suffix.name)
            .putString("prefix", s.prefix)
            .putBoolean("trim", s.trim)
            .putLong("dup", s.dupWindowMs)
            .putBoolean("beep", s.beep)
            .putBoolean("vibrate", s.vibrate)
            .putBoolean("keepAwake", s.keepAwake)
            .putBoolean("continuous", s.continuous)
            .putInt("autoClearDays", s.autoClearDays)
            .apply()
    }
}
