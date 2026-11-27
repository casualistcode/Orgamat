package de.tastenhalter.control

import de.tastenhalter.core.AppPrefs
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

/** Chromatischer Durchlauf zum Prüfen der Magnete. Alle Aufrufe auf dem Main-Thread. */
class RunController(
    private val out: OrganOutput,
    private val scope: CoroutineScope,
    private val prefs: AppPrefs,
) {
    data class State(
        val running: Boolean = false,
        val note: Int? = null,
        val from: Int,
        val to: Int,
        val stepMs: Int,
        val loop: Boolean,
    )

    private val _state = MutableStateFlow(
        State(
            from = prefs.runFrom.takeIf { it in Organ.current.range } ?: Organ.current.lowest,
            to = prefs.runTo.takeIf { it in Organ.current.range } ?: Organ.current.highest,
            stepMs = prefs.runStepMs.coerceIn(50, 60_000),
            loop = prefs.runLoop,
        )
    )
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null
    private var pressed: Int? = null
    private var pressedEpoch = 0

    init {
        out.addResetListener {
            runOnMain {
                job?.cancel()
                job = null
                if (pressedEpoch != out.epoch) pressed = null
                _state.update { it.copy(running = false, note = null) }
            }
        }
    }

    fun setFrom(n: Int) { val v = n.coerceIn(Organ.current.range); prefs.runFrom = v; _state.update { it.copy(from = v) } }
    fun setTo(n: Int) { val v = n.coerceIn(Organ.current.range); prefs.runTo = v; _state.update { it.copy(to = v) } }
    fun setStepMs(ms: Int) { val v = ms.coerceIn(50, 60_000); prefs.runStepMs = v; _state.update { it.copy(stepMs = v) } }
    fun setLoop(loop: Boolean) { prefs.runLoop = loop; _state.update { it.copy(loop = loop) } }

    fun onConfigChanged() {
        val r = Organ.current.range
        _state.update { it.copy(from = it.from.coerceIn(r), to = it.to.coerceIn(r)) }
    }

    fun toggle() = if (_state.value.running) stop() else start()

    fun start() {
        stop()
        _state.update { it.copy(running = true) }
        job = scope.launch {
            val self = coroutineContext[Job]
            try {
                do {
                    val s0 = _state.value
                    val notes = if (s0.from <= s0.to) s0.from..s0.to else s0.from downTo s0.to
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
                if (job === self) {
                    release()
                    job = null
                    _state.update { it.copy(running = false, note = null) }
                }
            }
        }
    }

    fun stop() {
        val j = job ?: return
        job = null
        j.cancel()
        release()
        _state.update { it.copy(running = false, note = null) }
    }

    private fun press(n: Int) {
        release()
        val e = out.epoch
        pressedEpoch = e
        if (out.noteOn(n, e)) pressed = n
    }

    private fun release() {
        pressed?.let { out.noteOff(it, pressedEpoch) }
        pressed = null
    }
}
