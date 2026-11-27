package de.tastenhalter.core

import android.content.Context

/** Speichert die Orgel-Einstellungen dauerhaft auf dem Handy. */
class OrganSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("organ", Context.MODE_PRIVATE)

    fun load(): OrganConfig {
        val d = OrganConfig()
        return OrganConfig(
            lowest = prefs.getInt(KEY_LOWEST, d.lowest),
            highest = prefs.getInt(KEY_HIGHEST, d.highest),
            maxPolyphony = prefs.getInt(KEY_POLY, d.maxPolyphony),
            channel = prefs.getInt(KEY_CHANNEL, d.channel),
        ).sanitized()
    }

    fun save(c: OrganConfig) {
        prefs.edit()
            .putInt(KEY_LOWEST, c.lowest)
            .putInt(KEY_HIGHEST, c.highest)
            .putInt(KEY_POLY, c.maxPolyphony)
            .putInt(KEY_CHANNEL, c.channel)
            .apply()
    }

    private companion object {
        const val KEY_LOWEST = "lowest"
        const val KEY_HIGHEST = "highest"
        const val KEY_POLY = "max_polyphony"
        const val KEY_CHANNEL = "channel"
    }
}
