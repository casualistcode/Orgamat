package de.tastenhalter.control

import de.tastenhalter.core.AppPrefs
import de.tastenhalter.core.Organ
import de.tastenhalter.core.OrganOutput
import de.tastenhalter.core.runOnMain
import de.tastenhalter.tuning.Interval
import de.tastenhalter.tuning.Step
import de.tastenhalter.tuning.Temperaments
import de.tastenhalter.tuning.TuningCircle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Temperatur legen wie in Stimmlicht – zusätzlich hält die Orgel auf Wunsch das aktuelle
 * Intervall (bzw. die angetippte Probe). Alle Aufrufe auf dem Main-Thread.
 */
class TemperamentController(private val out: OrganOutput, private val prefs: AppPrefs) {

    data class State(
        val circle: TuningCircle,
        val kammerton: Double,
        val steps: List<Step>,
        val index: Int = 0,
        /** Angetippte Probe im aktuellen Schritt, -1 = Hauptintervall. */
        val probe: Int = -1,
        /** Orgel hält das Intervall. */
        val holding: Boolean = false,
    ) {
        val step: Step get() = steps[index]
        val active: Interval get() = if (probe >= 0) step.probes[probe] else step.main
        /** Tasten, die die Orgel für das aktive Intervall drückt. */
        val keys: List<Int> get() = listOf(circle.key(active.lower), circle.key(active.upper))
    }

    private val _state: MutableStateFlow<State>
    val state: StateFlow<State>

    private var pressed: List<Int> = emptyList()
    private var pressedEpoch = 0

    init {
        val circle = Temperaments.byId(prefs.circleId)
        val k = prefs.kammerton
        _state = MutableStateFlow(State(circle, k, Temperaments.steps(circle, k)))
        state = _state.asStateFlow()
        out.addResetListener {
            runOnMain {
                if (pressedEpoch != out.epoch) pressed = emptyList()
                _state.update { it.copy(holding = false) }
            }
        }
    }

    fun next() = goTo(_state.value.index + 1)
    fun previous() = goTo(_state.value.index - 1)

    fun goTo(i: Int) {
        val n = _state.value.steps.size
        _state.update { it.copy(index = ((i % n) + n) % n, probe = -1) }
        repress()
    }

    /** Probe antippen: Licht (und Orgel) folgen ihr; nochmal tippen = zurück zum Hauptintervall. */
    fun selectProbe(i: Int) {
        _state.update { s -> s.copy(probe = if (s.probe == i || i !in s.step.probes.indices) -1 else i) }
        repress()
    }

    fun toggleHold() {
        if (_state.value.holding) {
            release()
            _state.update { it.copy(holding = false) }
        } else {
            _state.update { it.copy(holding = true) }
            press()
        }
    }

    fun setCircle(id: String) {
        val c = Temperaments.byId(id)
        prefs.circleId = c.id
        _state.update { it.copy(circle = c, steps = Temperaments.steps(c, it.kammerton), index = 0, probe = -1) }
        repress()
    }

    fun setKammerton(hz: Double) {
        val v = (Math.round(hz * 10) / 10.0).coerceIn(Temperaments.KAMMERTON_MIN, Temperaments.KAMMERTON_MAX)
        prefs.kammerton = v
        _state.update { it.copy(kammerton = v, steps = Temperaments.steps(it.circle, v)) }
    }

    /** Liegen die Tasten des aktuellen Zirkels im Umfang der Orgel? */
    fun fitsOrgan(s: State = _state.value): Boolean {
        val r = Organ.current.range
        return s.circle.key(s.circle.lower) in r && s.circle.key(s.circle.upper) in r
    }

    private fun repress() {
        if (_state.value.holding) press()
    }

    private fun press() {
        release()
        val e = out.epoch
        pressedEpoch = e
        pressed = _state.value.keys.filter { out.noteOn(it, e) }
    }

    private fun release() {
        for (n in pressed) out.noteOff(n, pressedEpoch)
        pressed = emptyList()
    }
}
