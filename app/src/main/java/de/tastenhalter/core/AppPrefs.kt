package de.tastenhalter.core

import android.content.Context
import de.tastenhalter.tuning.Temperaments

/** Dauerhaft gespeicherte Einstellungen der App (außer den Orgel-Werten, siehe [OrganSettings]). */
class AppPrefs(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("app", Context.MODE_PRIVATE)

    var circleId: String
        get() = p.getString("circle", "quinten") ?: "quinten"
        set(v) = p.edit().putString("circle", v).apply()

    var kammerton: Double
        get() = p.getString("kammerton", null)?.toDoubleOrNull()
            ?.takeIf { it in Temperaments.KAMMERTON_MIN..Temperaments.KAMMERTON_MAX }
            ?: Temperaments.DEFAULT_KAMMERTON
        set(v) = p.edit().putString("kammerton", v.toString()).apply()

    var runFrom: Int
        get() = p.getInt("run_from", -1)
        set(v) = p.edit().putInt("run_from", v).apply()

    var runTo: Int
        get() = p.getInt("run_to", -1)
        set(v) = p.edit().putInt("run_to", v).apply()

    var runStepMs: Int
        get() = p.getInt("run_step_ms", 800)
        set(v) = p.edit().putInt("run_step_ms", v).apply()

    var runLoop: Boolean
        get() = p.getBoolean("run_loop", false)
        set(v) = p.edit().putBoolean("run_loop", v).apply()

    /** Sichtbare weiße Tasten; 0 = automatisch nach Bildschirmbreite. */
    var keyboardKeys: Int
        get() = p.getInt("keyboard_keys", 0)
        set(v) = p.edit().putInt("keyboard_keys", v).apply()

    var autoConnectUsb: Boolean
        get() = p.getBoolean("auto_usb", true)
        set(v) = p.edit().putBoolean("auto_usb", v).apply()
}
