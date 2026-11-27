package de.tastenhalter.control

import de.tastenhalter.core.Organ
import de.tastenhalter.core.OrganOutput
import de.tastenhalter.core.runOnMain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.min

/** Stimm-/Testmodus. Alle Aufrufe auf dem Main-Thread. */
class TuningController(private val out: OrganOutput, private val scope: CoroutineScope) {

    enum class Interval(val label: String, val semitones: Int) {
        NONE("Einzelton", 0),
        MINOR_THIRD("kleine Terz", 3),
        MAJOR_THIRD("große Terz", 4),
        FOURTH("Quarte", 5),
        FIFTH("Quinte", 7),
        OCTAVE("Oktave", 12),
        TWELFTH("Duodezime", 19),
        DOUBLE_OCTAVE("Doppeloktave", 24),
    }

    data class State(
        val note: Int = 69,
        val holding: Boolean = false,
        val tapping: Boolean = false,
        val interval: Interval = Interval.NONE,
        val downward: Boolean = false,
        val running: Boolean = false,
        val runFrom: Int = Organ.current.lowest,
        val runTo: Int = Organ.current.highest,
        val stepMs: Int = 800,
        val loop: Boolean = false,
    )

    private val _state = MutableStateFlow(State(note = 69.coerceIn(Organ.current.range)))
    val state: StateFlow<State> = _state.asStateFlow()

    private var pressed: List<Int> = emptyList()
    private var pressedEpoch = 0
    private var runJob: Job? = null

    init {
        out.addResetListener {
            runOnMain {
                runJob?.cancel()
                runJob = null
                if (pressedEpoch != out.epoch) pressed = emptyList()
                _state.update { it.copy(holding = false, tapping = false, running = false) }
            }
        }
    }

    /** Grundton plus ggf. Intervallton (nur wenn im Umfang der Orgel). */
    fun notesFor(note: Int, s: State = _state.value): List<Int> {
        if (s.interval == Interval.NONE) return listOf(note)
        val other = if (s.downward) note - s.interval.semitones else note + s.interval.semitones
        return if (other in Organ.current.range) listOf(note, other) else listOf(note)
    }

    /** Nach Änderung des Umfangs: Töne in den neuen Umfang holen (vorher wurde NOT-AUS ausgelöst). */
    fun onConfigChanged() {
        val r = Organ.current.range
        _state.update {
            it.copy(
                note = it.note.coerceIn(r),
                runFrom = it.runFrom.coerceIn(r),
                runTo = it.runTo.coerceIn(r),
            )
        }
    }

    private fun press(note: Int) {
        release()
        val e = out.epoch
        pressedEpoch = e
        pressed = notesFor(note).filter { out.noteOn(it, e) }
    }

    private fun release() {
        for (n in pressed) out.noteOff(n, pressedEpoch)
        pressed = emptyList()
    }

    fun toggleHold() {
        stopRun()
        val s = _state.value
        if (s.holding) {
            release()
            _state.update { it.copy(holding = false) }
        } else {
            press(s.note)
            _state.update { it.copy(holding = true, tapping = false) }
        }
    }

    fun tapDown() {
        val s = _state.value
        if (s.holding || s.running) return
        press(s.note)
        _state.update { it.copy(tapping = true) }
    }

    fun tapUp() {
        if (!_state.value.tapping) return
        release()
        _state.update { it.copy(tapping = false) }
    }

    fun select(note: Int) {
        if (_state.value.running) return
        val n = note.coerceIn(Organ.current.range)
        _state.update { it.copy(note = n) }
        val s = _state.value
        if (s.holding || s.tapping) press(n)
    }

    fun next() = select(_state.value.note + 1)
    fun previous() = select(_state.value.note - 1)

    fun setInterval(interval: Interval) {
        _state.update { it.copy(interval = interval) }
        repressIfHolding()
    }

    fun setDownward(downward: Boolean) {
        _state.update { it.copy(downward = downward) }
        repressIfHolding()
    }

    private fun repressIfHolding() {
        val s = _state.value
        if (s.holding || s.tapping) press(s.note)
    }

    fun setRunFrom(note: Int) = _state.update { it.copy(runFrom = note.coerceIn(Organ.current.range)) }
    fun setRunTo(note: Int) = _state.update { it.copy(runTo = note.coerceIn(Organ.current.range)) }
    fun setStepMs(ms: Int) = _state.update { it.copy(stepMs = ms.coerceIn(50, 60_000)) }
    fun setLoop(loop: Boolean) = _state.update { it.copy(loop = loop) }

    /** Chromatischer Durchlauf von runFrom nach runTo (auch abwärts, wenn runFrom > runTo). */
    fun startRun() {
        stopRun()
        release()
        _state.update { it.copy(holding = false, tapping = false, running = true) }
        runJob = scope.launch {
            val self = coroutineContext[Job]
            try {
                do {
                    val s0 = _state.value
                    val notes = if (s0.runFrom <= s0.runTo) s0.runFrom..s0.runTo else s0.runFrom downTo s0.runTo
                    for (n in notes) {
                        _state.update { it.copy(note = n) }
                        press(n)
                        val step = _state.value.stepMs.toLong()
                        val gap = min(120L, step / 5)
                        delay(step - gap)
                        release()
                        delay(gap)
                    }
                } while (_state.value.loop)
            } finally {
                if (runJob === self) {
                    release()
                    runJob = null
                    _state.update { it.copy(running = false) }
                }
            }
        }
    }

    fun stopRun() {
        val j = runJob ?: return
        runJob = null
        j.cancel()
        release()
        _state.update { it.copy(running = false) }
    }
}
