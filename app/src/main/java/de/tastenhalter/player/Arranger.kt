package de.tastenhalter.player

import de.tastenhalter.core.Organ
import de.tastenhalter.core.OrganConfig
import de.tastenhalter.midi.MidiSong
import kotlin.math.max
import kotlin.math.min

data class PlayNote(val pitch: Int, val startUs: Long, val endUs: Long)
data class PlayEvent(val timeUs: Long, val pitch: Int, val on: Boolean)

/** Fertig aufbereiteter Ablauf für die Orgel (im eingestellten Umfang, mit Polyphonie-Grenze). */
class Schedule(
    val notes: List<PlayNote>,
    val events: List<PlayEvent>,
    val durationUs: Long,
    val foldedCount: Int,
    val droppedCount: Int,
) {
    fun notesActiveAt(t: Long): List<PlayNote> = notes.filter { it.startUs <= t && it.endUs > t }

    /** Index des ersten Ereignisses mit Zeit > t. */
    fun firstEventAfter(t: Long): Int {
        var lo = 0
        var hi = events.size
        while (lo < hi) {
            val m = (lo + hi) ushr 1
            if (events[m].timeUs <= t) lo = m + 1 else hi = m
        }
        return lo
    }

    companion object {
        val EMPTY = Schedule(emptyList(), emptyList(), 0, 0, 0)
    }
}

object Arranger {
    /** Pause vor einem wiederholten Ton, damit die Pfeife neu anspricht. */
    const val REPEAT_GAP_US = 50_000L
    /** Überlappt eine Folgenote gleicher Höhe um höchstens so viel, gilt sie als Tonwiederholung. */
    const val REPEAT_TOLERANCE_US = 30_000L
    const val MIN_NOTE_US = 40_000L

    /** Noten außerhalb des Umfangs in den Umfang oktavieren (Umfang mind. eine Oktave). */
    fun fold(pitch: Int, config: OrganConfig = Organ.current): Int {
        var x = pitch
        while (x < config.lowest) x += 12
        while (x > config.highest) x -= 12
        return x
    }

    fun build(
        song: MidiSong,
        selectedParts: Set<Int>,
        transpose: Int,
        config: OrganConfig = Organ.current,
    ): Schedule {
        val maxPolyphony = config.maxPolyphony
        var folded = 0
        val byPitch = HashMap<Int, MutableList<LongArray>>()
        for (n in song.notes) {
            if (n.part !in selectedParts) continue
            val raw = n.pitch + transpose
            val p = fold(raw, config)
            if (p != raw) folded++
            byPitch.getOrPut(p) { ArrayList() }
                .add(longArrayOf(n.startUs, max(n.endUs, n.startUs + MIN_NOTE_US)))
        }

        // Pro Tonhöhe: Überlappungen (z. B. Unisono zweier Stimmen) zusammenfassen,
        // Tonwiederholungen mit kurzer Pause trennen.
        val notes = ArrayList<PlayNote>()
        for ((pitch, list) in byPitch) {
            list.sortBy { it[0] }
            var s = list[0][0]
            var e = list[0][1]
            for (i in 1 until list.size) {
                val ns = list[i][0]
                val ne = list[i][1]
                if (e - ns > REPEAT_TOLERANCE_US) {
                    e = max(e, ne)
                    continue
                }
                if (ns - e < REPEAT_GAP_US) {
                    val target = ns - REPEAT_GAP_US
                    e = min(e, if (target >= s + MIN_NOTE_US) target else s + (ns - s) / 2)
                }
                notes += PlayNote(pitch, s, e)
                s = ns
                e = ne
            }
            notes += PlayNote(pitch, s, e)
        }
        notes.sortWith(compareBy<PlayNote>({ it.startUs }, { it.pitch }))

        // Ereignisse; bei gleicher Zeit erst Note-Off, dann Note-On.
        class Ev(val time: Long, val id: Int, val on: Boolean)
        val evs = ArrayList<Ev>(notes.size * 2)
        notes.forEachIndexed { i, n ->
            evs += Ev(n.startUs, i, true)
            evs += Ev(n.endUs, i, false)
        }
        evs.sortWith(compareBy<Ev>({ it.time }, { if (it.on) 1 else 0 }))

        // Polyphonie-Grenze: Noten, die über die Grenze gingen, komplett auslassen.
        val dropped = BooleanArray(notes.size)
        var active = 0
        var droppedCount = 0
        for (ev in evs) {
            if (ev.on) {
                if (active >= maxPolyphony) {
                    dropped[ev.id] = true
                    droppedCount++
                } else {
                    active++
                }
            } else if (!dropped[ev.id]) {
                active--
            }
        }

        val keptNotes = notes.filterIndexed { i, _ -> !dropped[i] }
        val events = evs.filter { !dropped[it.id] }.map { PlayEvent(it.time, notes[it.id].pitch, it.on) }
        val duration = max(song.durationUs, keptNotes.maxOfOrNull { it.endUs } ?: 0L)
        return Schedule(keptNotes, events, duration, folded, droppedCount)
    }
}
