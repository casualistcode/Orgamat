package de.tastenhalter.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

fun isWhiteKey(note: Int): Boolean = when (((note % 12) + 12) % 12) {
    1, 3, 6, 8, 10 -> false
    else -> true
}

/**
 * Einstellbare Eigenschaften der angeschlossenen Orgel bzw. des MIDI-Decoders.
 * Voreinstellung: 56 Töne C–g''' (MIDI 36–91), max. 15 Töne gleichzeitig, MIDI-Kanal 1.
 */
data class OrganConfig(
    val lowest: Int = 36,
    val highest: Int = 91,
    val maxPolyphony: Int = 15,
    /** 0–15 = MIDI-Kanal 1–16. */
    val channel: Int = 0,
) {
    val range: IntRange get() = lowest..highest
    val keyCount: Int get() = highest - lowest + 1
    val whiteKeys: List<Int> = (lowest..highest).filter { isWhiteKey(it) }
    val blackKeys: List<Int> = (lowest..highest).filter { !isWhiteKey(it) }

    /** Nummer des Decoder-Ausgangs (1 = tiefster Ton). */
    fun outputNumber(note: Int): Int = note - lowest + 1
    fun whiteIndex(note: Int): Int = whiteKeys.indexOf(note)

    /** Werte in gültige Grenzen bringen; der Umfang umfasst immer mindestens eine Oktave. */
    fun sanitized(): OrganConfig {
        val lo = lowest.coerceIn(0, 127 - (MIN_SPAN - 1))
        val hi = highest.coerceIn(lo + MIN_SPAN - 1, 127)
        return OrganConfig(lo, hi, maxPolyphony.coerceIn(1, 128), channel.coerceIn(0, 15))
    }

    companion object {
        const val MIN_SPAN = 12
    }
}

object Organ {
    const val VELOCITY = 100

    private val _config = MutableStateFlow(OrganConfig())
    val config: StateFlow<OrganConfig> = _config.asStateFlow()
    val current: OrganConfig get() = _config.value

    fun setConfig(config: OrganConfig) {
        _config.value = config.sanitized()
    }
}

/** Deutsche Tonnamen in Helmholtz-Schreibweise: 36 = C, 48 = c, 60 = c', 69 = a', 91 = g'''. */
object NoteNames {
    private val names = arrayOf("c", "cis", "d", "dis", "e", "f", "fis", "g", "gis", "a", "b", "h")

    fun name(note: Int): String {
        val base = names[((note % 12) + 12) % 12]
        val octave = Math.floorDiv(note, 12) - 1
        return when {
            octave <= 1 -> base.replaceFirstChar { it.uppercaseChar() } + ",".repeat(2 - octave)
            octave == 2 -> base.replaceFirstChar { it.uppercaseChar() }
            octave == 3 -> base
            else -> base + "'".repeat(octave - 3)
        }
    }
}
