package de.tastenhalter.core

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Zentrale Ausgabe an die Orgel. Alle Quellen (Klaviatur, Stimmmodus, Player) laufen hier durch.
 *
 * - Referenzzählung pro Ton: drücken zwei Quellen denselben Ton, wird er erst losgelassen,
 *   wenn beide ihn loslassen.
 * - Hartes Limit von [OrganConfig.maxPolyphony] gleichzeitigen Tönen.
 * - "Epoche": [allNotesOff] (NOT-AUS) erhöht die Epoche. Befehle einer Quelle mit alter Epoche
 *   werden verworfen. So kann kein Ton, der kurz vor dem NOT-AUS unterwegs war, danach noch anspringen.
 */
class OrganOutput(private val sender: (ByteArray) -> Boolean) {

    private val lock = Any()
    private val counts = IntArray(128)

    @Volatile
    var epoch: Int = 0
        private set

    private val _active = MutableStateFlow<Set<Int>>(emptySet())
    /** Töne, die gerade an der Orgel gedrückt sind. */
    val active: StateFlow<Set<Int>> = _active.asStateFlow()

    private val resetListeners = CopyOnWriteArrayList<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private val repeatOff = Runnable { resendOffsForSilentNotes() }

    /** Wird nach jedem NOT-AUS aufgerufen (auf dem Thread des Aufrufers). */
    fun addResetListener(listener: () -> Unit) {
        resetListeners.add(listener)
    }

    fun noteOn(note: Int, epoch: Int): Boolean = synchronized(lock) {
        val c = Organ.current
        if (epoch != this.epoch || note !in c.range) return false
        if (counts[note] == 0) {
            if (soundingCountLocked() >= c.maxPolyphony) return false
            sender(byteArrayOf((0x90 or c.channel).toByte(), note.toByte(), Organ.VELOCITY.toByte()))
        }
        counts[note]++
        publishLocked()
        true
    }

    fun noteOff(note: Int, epoch: Int) {
        synchronized(lock) {
            val c = Organ.current
            if (epoch != this.epoch || note !in c.range || counts[note] == 0) return
            counts[note]--
            if (counts[note] == 0) {
                sender(byteArrayOf((0x80 or c.channel).toByte(), note.toByte(), 0))
            }
            publishLocked()
        }
    }

    /** NOT-AUS: Note Off für alle Noten des Umfangs (plus "All Notes Off"), alle Quellen zurücksetzen. */
    fun allNotesOff() {
        synchronized(lock) {
            val c = Organ.current
            epoch++
            counts.fill(0)
            val out = ByteArrayOutputStream(c.keyCount * 3 + 3)
            for (n in c.range) {
                out.write(0x80 or c.channel); out.write(n); out.write(0)
            }
            out.write(0xB0 or c.channel); out.write(123); out.write(0)
            sender(out.toByteArray())
            publishLocked()
        }
        for (l in resetListeners) runCatching { l() }
        // Funkstrecke zur Sicherheit: nach 300 ms noch einmal Note Off für alle Töne, die nicht klingen sollen.
        main.removeCallbacks(repeatOff)
        main.postDelayed(repeatOff, 300)
    }

    private fun resendOffsForSilentNotes() {
        synchronized(lock) {
            val c = Organ.current
            val out = ByteArrayOutputStream(c.keyCount * 3)
            for (n in c.range) {
                if (counts[n] == 0) {
                    out.write(0x80 or c.channel); out.write(n); out.write(0)
                }
            }
            if (out.size() > 0) sender(out.toByteArray())
        }
    }

    private fun soundingCountLocked(): Int {
        var c = 0
        for (n in 0..127) if (counts[n] > 0) c++
        return c
    }

    private fun publishLocked() {
        _active.value = (0..127).filterTo(HashSet()) { counts[it] > 0 }
    }
}

/** Führt [block] sofort aus, wenn wir auf dem Main-Thread sind, sonst per post. */
internal fun runOnMain(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) block() else Handler(Looper.getMainLooper()).post(block)
}
