package de.tastenhalter.control

import de.tastenhalter.core.OrganOutput
import de.tastenhalter.core.runOnMain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Klaviatur zum Antippen. Alle Aufrufe auf dem Main-Thread. */
class KeyboardController(private val out: OrganOutput) {

    private val _holdMode = MutableStateFlow(false)
    val holdMode: StateFlow<Boolean> = _holdMode.asStateFlow()

    private val _held = MutableStateFlow<Set<Int>>(emptySet())
    /** Im Haltemodus eingerastete Töne. */
    val held: StateFlow<Set<Int>> = _held.asStateFlow()

    /** Ton -> Epoche, in der er gedrückt wurde. */
    private val momentary = HashMap<Int, Int>()
    private val latched = HashMap<Int, Int>()

    init {
        out.addResetListener {
            runOnMain {
                val e = out.epoch
                momentary.entries.removeAll { it.value != e }
                latched.entries.removeAll { it.value != e }
                _held.value = latched.keys.toSet()
            }
        }
    }

    fun down(note: Int) {
        if (_holdMode.value) {
            val e = latched.remove(note)
            if (e != null) {
                out.noteOff(note, e)
            } else {
                val ep = out.epoch
                if (out.noteOn(note, ep)) latched[note] = ep
            }
            _held.value = latched.keys.toSet()
        } else {
            if (momentary.containsKey(note)) return
            val ep = out.epoch
            if (out.noteOn(note, ep)) momentary[note] = ep
        }
    }

    fun up(note: Int) {
        momentary.remove(note)?.let { out.noteOff(note, it) }
    }

    fun setHoldMode(enabled: Boolean) {
        if (!enabled) releaseHeld()
        _holdMode.value = enabled
    }

    fun releaseHeld() {
        for ((n, e) in latched) out.noteOff(n, e)
        latched.clear()
        _held.value = emptySet()
    }
}
