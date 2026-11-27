package de.tastenhalter.player

import de.tastenhalter.core.OrganOutput
import de.tastenhalter.midi.MidiPart
import de.tastenhalter.midi.MidiSong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Spielt eine MIDI-Datei auf der Orgel ab. Die gesamte Wiedergabelogik läuft auf einem eigenen Thread;
 * öffentliche Methoden dürfen von überall aufgerufen werden.
 */
class MidiPlayer(private val out: OrganOutput, private val scope: CoroutineScope) {

    enum class Status { STOPPED, PLAYING, PAUSED }

    data class UiState(
        val fileName: String? = null,
        val parts: List<MidiPart> = emptyList(),
        val selected: Set<Int> = emptySet(),
        val transpose: Int = 0,
        val tempoPercent: Int = 100,
        val status: Status = Status.STOPPED,
        val positionUs: Long = 0,
        val durationUs: Long = 0,
        val noteCount: Int = 0,
        val foldedCount: Int = 0,
        val droppedCount: Int = 0,
        val loading: Boolean = false,
        val error: String? = null,
    )

    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "tastenhalter-player").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY
        }
    }
    private val dispatcher = executor.asCoroutineDispatcher()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    // --- nur auf dem Player-Thread verwendet ---
    private var song: MidiSong? = null
    private var schedule: Schedule = Schedule.EMPTY
    private var status = Status.STOPPED
    private var idx = 0
    private var posUs = 0L
    private var lastNanos = 0L
    private var tempo = 1.0
    private var epoch = 0
    private val sounding = HashSet<Int>()
    private var loopJob: Job? = null

    init {
        out.addResetListener { onPlayer { onEmergency() } }
    }

    private fun onPlayer(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(dispatcher, block = block)

    fun setLoading() = _state.update { it.copy(loading = true, error = null) }
    fun setError(message: String) = _state.update { it.copy(loading = false, error = message) }

    fun load(fileName: String, newSong: MidiSong) = onPlayer {
        halt()
        song = newSong
        val all = newSong.parts.map { it.index }.toSet()
        val sel = newSong.parts.filter { !it.isDrums }.map { it.index }.toSet().ifEmpty { all }
        _state.update {
            UiState(fileName = fileName, parts = newSong.parts, selected = sel, tempoPercent = it.tempoPercent)
        }
        rebuild()
    }

    fun play() = onPlayer {
        if (status == Status.PLAYING) return@onPlayer
        if (song == null) {
            setError("Bitte zuerst eine MIDI-Datei öffnen.")
            return@onPlayer
        }
        if (schedule.events.isEmpty()) {
            setError("Keine spielbaren Noten – bitte mindestens eine Spur auswählen.")
            return@onPlayer
        }
        if (posUs >= schedule.durationUs) posUs = 0
        epoch = out.epoch
        pressActiveAt(posUs)
        status = Status.PLAYING
        lastNanos = System.nanoTime()
        _state.update { it.copy(error = null) }
        publish()
        loopJob = onPlayer { runLoop() }
    }

    fun pause() = onPlayer {
        if (status != Status.PLAYING) return@onPlayer
        loopJob?.cancel()
        loopJob = null
        advance()
        releaseSounding()
        status = Status.PAUSED
        publish()
    }

    /** Stopp: Wiedergabe beenden, an den Anfang, Note Off für alle Noten 36–91. */
    fun stop() = onPlayer {
        halt()
        out.allNotesOff()
    }

    fun seekTo(us: Long) = onPlayer {
        val target = us.coerceIn(0L, schedule.durationUs)
        if (status == Status.PLAYING) {
            advance()
            releaseSounding()
            posUs = target
            pressActiveAt(target)
            lastNanos = System.nanoTime()
        } else {
            posUs = target
        }
        publish()
    }

    fun setTempo(percent: Int) {
        val p = percent.coerceIn(25, 200)
        _state.update { it.copy(tempoPercent = p) }
        onPlayer {
            advance()
            tempo = p / 100.0
        }
    }

    fun setTranspose(semitones: Int) {
        _state.update { it.copy(transpose = semitones.coerceIn(-24, 24)) }
        onPlayer { rebuild() }
    }

    fun togglePart(index: Int) {
        _state.update { s ->
            s.copy(selected = if (index in s.selected) s.selected - index else s.selected + index)
        }
        onPlayer { rebuild() }
    }

    fun selectAll(all: Boolean) {
        _state.update { s -> s.copy(selected = if (all) s.parts.map { it.index }.toSet() else emptySet()) }
        onPlayer { rebuild() }
    }

    /** Nach Änderung der Orgel-Einstellungen neu aufbereiten. */
    fun reconfigure() {
        onPlayer { rebuild() }
    }

    fun release() {
        executor.shutdown()
    }

    // ---------------- intern (Player-Thread) ----------------

    private suspend fun runLoop() {
        var lastPublish = 0L
        while (status == Status.PLAYING) {
            advance()
            val ev = schedule.events
            while (idx < ev.size && ev[idx].timeUs <= posUs) {
                apply(ev[idx])
                idx++
            }
            val now = System.nanoTime()
            if (now - lastPublish >= 100_000_000L) {
                publish()
                lastPublish = now
            }
            if (idx >= ev.size) {
                finish()
                return
            }
            val waitMs = ((ev[idx].timeUs - posUs) / tempo / 1000.0).toLong()
            delay(waitMs.coerceIn(1L, 20L))
        }
    }

    private fun apply(e: PlayEvent) {
        if (e.on) {
            if (out.noteOn(e.pitch, epoch)) sounding += e.pitch
        } else {
            if (sounding.remove(e.pitch)) out.noteOff(e.pitch, epoch)
        }
    }

    private fun advance() {
        val now = System.nanoTime()
        if (status == Status.PLAYING) posUs += ((now - lastNanos) / 1000.0 * tempo).toLong()
        lastNanos = now
    }

    private fun pressActiveAt(t: Long) {
        for (n in schedule.notesActiveAt(t)) {
            if (out.noteOn(n.pitch, epoch)) sounding += n.pitch
        }
        idx = schedule.firstEventAfter(t)
    }

    private fun releaseSounding() {
        for (p in sounding) out.noteOff(p, epoch)
        sounding.clear()
    }

    private fun halt() {
        loopJob?.cancel()
        loopJob = null
        releaseSounding()
        status = Status.STOPPED
        posUs = 0
        idx = 0
        publish()
    }

    private fun finish() {
        loopJob = null
        releaseSounding()
        status = Status.STOPPED
        posUs = 0
        idx = 0
        publish()
        out.allNotesOff()
    }

    /** NOT-AUS wurde ausgelöst: Wiedergabe anhalten (Position bleibt erhalten). */
    private fun onEmergency() {
        loopJob?.cancel()
        loopJob = null
        if (status == Status.PLAYING) {
            advance()
            status = Status.PAUSED
        }
        sounding.clear()
        publish()
    }

    private fun rebuild() {
        val sg = song ?: return
        val st = _state.value
        val newSchedule = Arranger.build(sg, st.selected, st.transpose)
        val wasPlaying = status == Status.PLAYING
        if (wasPlaying) {
            advance()
            releaseSounding()
        }
        schedule = newSchedule
        if (posUs > newSchedule.durationUs) posUs = newSchedule.durationUs
        if (wasPlaying) {
            pressActiveAt(posUs)
            lastNanos = System.nanoTime()
        }
        _state.update {
            it.copy(
                durationUs = newSchedule.durationUs,
                noteCount = newSchedule.notes.size,
                foldedCount = newSchedule.foldedCount,
                droppedCount = newSchedule.droppedCount,
                loading = false,
            )
        }
        publish()
    }

    private fun publish() {
        val s = status
        val p = posUs
        _state.update { it.copy(status = s, positionUs = p) }
    }
}
