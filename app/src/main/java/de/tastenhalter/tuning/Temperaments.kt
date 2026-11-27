package de.tastenhalter.tuning

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Stimmzirkel und Schwebungsrechnung – übernommen aus Stimmlicht
 * (https://github.com/casualistcode/stimmlicht, index.html).
 *
 * Alle Töne sind klingende MIDI-Nummern (a¹ = 69 = Kammerton). Bei Orgel-Zirkeln wird auf einem
 * Register mit Fußlage [TuningCircle.foot] gestimmt; die Taste liegt dann um [TuningCircle.offset] tiefer.
 */
enum class Beat(val label: String) { ENG("eng"), WEIT("weit"), REIN("rein") }

data class Interval(
    val kind: String,
    val lower: Int,
    val upper: Int,
    /** Schwebungen pro Sekunde (0 = rein). */
    val rate: Double,
    val beat: Beat,
)

data class StepDef(
    val from: Int,
    val to: Int,
    val probes: List<Pair<Int, Int>> = emptyList(),
    val hint: String = "",
    /** Töne, die in diesem Schritt noch nicht an ihrem Endplatz liegen: Tonklasse -> Cent (a = 0). */
    val provisional: Map<Int, Double>? = null,
)

data class Step(
    val main: Interval,
    val from: Int,
    val to: Int,
    val hint: String,
    val probes: List<Interval>,
)

class TuningCircle(
    val id: String,
    val title: String,
    val fixedName: String?,
    /** Fußlage des Registers (4 = 4′); null = Klavier, Taste und Klang gleich. */
    val foot: Int?,
    val noteNames: List<String>,
    /** Abweichung je Tonklasse von gleichstufig in Cent (a = 0); null = gleichstufig. */
    val deviation: DoubleArray?,
    val lower: Int,
    val upper: Int,
    val reference: Int,
    val steps: List<StepDef>,
) {
    /** Klingender Ton -> Taste: im 4′ eine Oktave tiefer, im 2′ zwei. */
    val offset: Int get() = if (foot == null) 0 else (12 * log2(foot / 8.0)).roundToInt()

    fun key(sounding: Int): Int = sounding + offset

    val name: String
        get() = fixedName ?: (title + " " + Temperaments.noteName(key(lower), noteNames) + "–" +
            Temperaments.noteName(key(upper), noteNames) + (if (foot != null) " ($foot′)" else " (Klavier)"))
}

object Temperaments {
    const val A1 = 69
    const val DEFAULT_KAMMERTON = 440.0
    const val KAMMERTON_MIN = 200.0
    const val KAMMERTON_MAX = 520.0

    private data class Ratio(val kind: String, val upper: Int, val lower: Int)

    /** Reine Intervalle nach Halbtonabstand und das Verhältnis ihrer Koinzidenzteiltöne (oben : unten). */
    private val INTERVALS = mapOf(
        3 to Ratio("kleine Terz", 6, 5),
        4 to Ratio("Terz", 5, 4),
        5 to Ratio("Quarte", 4, 3),
        7 to Ratio("Quinte", 3, 2),
        9 to Ratio("Sexte", 5, 3),
        12 to Ratio("Oktave", 2, 1),
    )

    val PURE_FIFTH = 1200 * log2(1.5)
    val PYTH_COMMA = 1200 * log2(3.0.pow(12) / 2.0.pow(19))

    /** Rundung wie JavaScript Math.round (halbe Werte aufwärts). */
    private fun jsRound(x: Double): Double = floor(x + 0.5)

    private fun pc(n: Int): Int = ((n % 12) + 12) % 12

    /** Vallotti: F–C–G–D–A–E–H um je 1/6 pythagoreisches Komma verengt, H–Fis–Cis–Gis–Dis–B–F rein. */
    fun vallotti(): DoubleArray {
        val narrow = PURE_FIFTH - PYTH_COMMA / 6
        val pos = DoubleArray(12)
        var cent = 0.0
        var tk = 0
        pos[0] = 0.0
        repeat(5) { cent += narrow; tk = (tk + 7) % 12; pos[tk] = cent }       // g d a e h
        cent = -narrow; tk = 5; pos[5] = cent                                    // f
        repeat(5) { cent -= PURE_FIFTH; tk = (tk + 5) % 12; pos[tk] = cent }     // b es as des ges
        val dev = DoubleArray(12) { k -> val x = pos[k] - 100 * k; x - 1200 * jsRound(x / 1200) }
        val a = dev[9]
        return DoubleArray(12) { dev[it] - a }
    }

    /**
     * Stimmung aus der Verengung der elf Quinten ab A aufwärts (A–E, E–H, … G–D; D–A ergibt sich),
     * jeweils als Bruchteil des pythagoreischen Kommas. Ergebnis: Cent je Tonklasse, a = 0.
     */
    fun fromFifths(parts: DoubleArray): DoubleArray {
        val dev = DoubleArray(12)
        dev[9] = 0.0
        var cent = 0.0
        var tk = 9
        for (i in 0 until 11) {
            cent += PURE_FIFTH - parts[i] * PYTH_COMMA
            tk = (tk + 7) % 12
            val x = cent - 100 * ((tk - 9 + 12) % 12)
            dev[tk] = x - 1200 * jsRound(x / 1200)
        }
        return dev
    }

    private const val T6 = 1.0 / 6
    private const val T12 = 1.0 / 12
    private const val T4 = 1.0 / 4

    private val KLEINE_STADT = fromFifths(doubleArrayOf(T6, T12, T12, 0.0, 0.0, T12, T12, 0.0, 0.0, T6, T6))
    private val H_VORLAEUFIG = mapOf(11 to KLEINE_STADT[4] + PURE_FIFTH - PYTH_COMMA / 6 - 700)
    private val DORF = fromFifths(doubleArrayOf(T4, 0.0, T12, 0.0, T12, 0.0, 0.0, T12, 0.0, T12, T6))
    private val GROSSE_STADT = fromFifths(doubleArrayOf(T12, 0.0, T12, T12, T12, 0.0, T12, T12, 0.0, T6, T6))
    private val GS_E = mapOf(4 to GROSSE_STADT[4] - PYTH_COMMA / 12)
    private val GS_H = mapOf(11 to GROSSE_STADT[11] - PYTH_COMMA / 12)
    private val GS_F = mapOf(5 to GROSSE_STADT[5] + PYTH_COMMA / 6)
    private val GS_B = mapOf(10 to GROSSE_STADT[10] + PYTH_COMMA / 12)

    private val NAMES_DIS = listOf("c", "cis", "d", "dis", "e", "f", "fis", "g", "gis", "a", "b", "h")
    private val NAMES_ES = listOf("c", "cis", "d", "es", "e", "f", "fis", "g", "gis", "a", "b", "h")

    private fun fifthsCircle(start: Int): List<StepDef> {
        val out = ArrayList<StepDef>()
        var from = start
        repeat(12) {
            val to = if (from + 7 <= start + 12) from + 7 else from - 5
            out += StepDef(from, to)
            from = to
        }
        return out
    }

    private fun p(a: Int, b: Int) = a to b

    val circles: List<TuningCircle> = listOf(
        TuningCircle(
            id = "quinten", title = "Quintenzirkel", fixedName = null, foot = 4, noteNames = NAMES_DIS,
            deviation = null, lower = 69, upper = 81, reference = 69, steps = fifthsCircle(69),
        ),
        // Steinway-Stimmzirkel e–e¹ nach Ulrich van Aaken (EUROPIANO 1985)
        TuningCircle(
            id = "steinway", title = "Steinway", fixedName = null, foot = null, noteNames = NAMES_ES,
            deviation = null, lower = 52, upper = 64, reference = 57,
            steps = listOf(
                StepDef(57, 53),
                StepDef(57, 62, listOf(p(53, 62))),
                StepDef(53, 58, listOf(p(58, 62))),
                StepDef(62, 55, listOf(p(55, 58))),
                StepDef(55, 60, listOf(p(53, 60), p(57, 60))),
                StepDef(58, 54),
                StepDef(54, 59, listOf(p(55, 59), p(53, 62))),
                StepDef(59, 64, listOf(p(57, 64), p(60, 64), p(55, 64))),
                StepDef(64, 52, listOf(p(52, 57), p(52, 59))),
                StepDef(52, 56, listOf(p(56, 60))),
                StepDef(56, 61, listOf(p(57, 61), p(54, 61), p(52, 61))),
                StepDef(58, 63),
            ),
        ),
        TuningCircle(
            id = "vallotti", title = "Vallotti", fixedName = "Vallotti", foot = 4, noteNames = NAMES_ES,
            deviation = vallotti(), lower = 50, upper = 62, reference = 57,
            steps = listOf(
                StepDef(57, 53),
                StepDef(57, 52),
                StepDef(57, 50, listOf(p(50, 53))),
                StepDef(53, 58),
                StepDef(58, 51),
                StepDef(51, 56),
                StepDef(56, 61),
                StepDef(61, 54, listOf(p(50, 54))),
                StepDef(54, 59, listOf(p(52, 59))),
                StepDef(50, 55, listOf(p(55, 59))),
                StepDef(55, 60, listOf(p(53, 60), p(56, 60), p(57, 61))),
            ),
        ),
        TuningCircle(
            id = "neidhardtDorf", title = "Neidhardt", fixedName = "Neidhardt „Dorf“ 1732", foot = 4,
            noteNames = NAMES_DIS, deviation = DORF, lower = 50, upper = 62, reference = 57,
            steps = listOf(
                StepDef(57, 52),
                StepDef(57, 50),
                StepDef(50, 55),
                StepDef(55, 60),
                StepDef(60, 53, listOf(p(53, 57))),
                StepDef(53, 58),
                StepDef(58, 51),
                StepDef(51, 56),
                StepDef(52, 59, listOf(p(55, 59))),
                StepDef(59, 54, listOf(p(50, 54))),
                StepDef(54, 61, listOf(p(56, 61), p(57, 61), p(52, 56))),
            ),
        ),
        TuningCircle(
            id = "neidhardt", title = "Neidhardt", fixedName = "Neidhardt „Kleine Stadt“ 1732", foot = 4,
            noteNames = NAMES_DIS, deviation = KLEINE_STADT, lower = 50, upper = 62, reference = 57,
            steps = listOf(
                StepDef(57, 52),
                StepDef(57, 50),
                StepDef(50, 55),
                StepDef(55, 60),
                StepDef(60, 53, listOf(p(53, 57))),
                StepDef(53, 58),
                StepDef(52, 59, hint = "vorläufig", provisional = H_VORLAEUFIG),
                StepDef(59, 54, listOf(p(50, 54)), provisional = H_VORLAEUFIG),
                StepDef(54, 61, listOf(p(57, 61))),
                StepDef(61, 56, listOf(p(52, 56))),
                StepDef(52, 59, listOf(p(54, 59), p(55, 59)), hint = "ausgleichen"),
                StepDef(56, 51, listOf(p(51, 58)), hint = "ausgleichen"),
            ),
        ),
        TuningCircle(
            id = "neidhardtGross", title = "Neidhardt", fixedName = "Neidhardt „Große Stadt“ 1724", foot = 4,
            noteNames = NAMES_DIS, deviation = GROSSE_STADT, lower = 50, upper = 62, reference = 57,
            steps = listOf(
                StepDef(57, 52, hint = "vorläufig", provisional = GS_E),
                StepDef(57, 50),
                StepDef(50, 55),
                StepDef(55, 60),
                StepDef(60, 53, hint = "vorläufig", provisional = GS_F),
                StepDef(53, 58, hint = "vorläufig", provisional = GS_F + GS_B),
                StepDef(58, 51, provisional = GS_B),
                StepDef(51, 56),
                StepDef(52, 59, hint = "vorläufig", provisional = GS_E + GS_H),
                StepDef(59, 54, provisional = GS_H),
                StepDef(54, 61, listOf(p(56, 61)), hint = "ausgleichen"),
                StepDef(57, 52, listOf(p(52, 59)), hint = "ausgleichen", provisional = GS_H),
                StepDef(52, 59, listOf(p(55, 59)), hint = "nachstimmen"),
                StepDef(60, 53, listOf(p(53, 57)), hint = "nachstimmen"),
                StepDef(53, 58, listOf(p(51, 58)), hint = "ausgleichen"),
            ),
        ),
    )

    fun byId(id: String?): TuningCircle = circles.firstOrNull { it.id == id } ?: circles.first()

    fun frequency(n: Int, circle: TuningCircle, kammerton: Double, provisional: Map<Int, Double>?): Double {
        val tk = pc(n)
        var cent = circle.deviation?.get(tk) ?: 0.0
        provisional?.get(tk)?.let { cent = it }
        return kammerton * 2.0.pow((n - A1) / 12.0 + cent / 1200.0)
    }

    /** Schwebung zwischen zwei Tönen über die Koinzidenzteiltöne. */
    fun interval(a: Int, b: Int, circle: TuningCircle, kammerton: Double, provisional: Map<Int, Double>? = null): Interval {
        val lower = min(a, b)
        val upper = max(a, b)
        val r = INTERVALS[upper - lower] ?: error("Kein bekanntes Intervall: $lower–$upper")
        val diff = r.lower * frequency(upper, circle, kammerton, provisional) -
            r.upper * frequency(lower, circle, kammerton, provisional)
        val pure = abs(diff) < 1e-6
        return Interval(
            kind = r.kind,
            lower = lower,
            upper = upper,
            rate = if (pure) 0.0 else abs(diff),
            beat = when {
                pure -> Beat.REIN
                diff < 0 -> Beat.ENG
                else -> Beat.WEIT
            },
        )
    }

    fun steps(circle: TuningCircle, kammerton: Double): List<Step> = circle.steps.map { d ->
        Step(
            main = interval(d.from, d.to, circle, kammerton, d.provisional),
            from = d.from,
            to = d.to,
            hint = d.hint,
            probes = d.probes.map { (x, y) -> interval(x, y, circle, kammerton, d.provisional) },
        )
    }

    /** Platz eines Tons im Ring: a = 0, e = 1, h = 2 … (Quinten im Uhrzeigersinn). */
    fun place(n: Int): Int = (((pc(n) - 9) * 7) % 12 + 12) % 12

    /** Tonklasse an einem Platz im Ring. */
    fun classAt(place: Int): Int = (9 + 7 * place) % 12

    /** Bögen auf dem kürzeren Weg zwischen zwei Tönen (Quinte/Quarte: 1, Terz: 4, Oktave: keiner). */
    fun path(from: Int, to: Int): List<Int> {
        val a = place(from)
        val b = place(to)
        val fwd = (b - a + 12) % 12
        return if (fwd <= 6) List(fwd) { (a + it) % 12 } else List(12 - fwd) { (b + it) % 12 }
    }

    /** Strecke eines Proben-Bogens im Ring: Startwinkel und Spanne in Grad (0° = oben, im Uhrzeigersinn). */
    fun probeSpan(p: Interval): Pair<Float, Float> {
        val a = place(p.lower)
        val b = place(p.upper)
        val fwd = (b - a + 12) % 12
        return if (fwd <= 6) (a * 30f) to (fwd * 30f) else (b * 30f) to ((12 - fwd) * 30f)
    }

    /** Erster Schritt, in dem diese Tonklasse gestimmt wird (der Bezugston führt zu Schritt 1). */
    fun stepForClass(steps: List<Step>, tk: Int): Int {
        steps.forEachIndexed { i, s -> if (pc(s.to) == tk) return i }
        return 0
    }

    fun pitchClass(n: Int): Int = pc(n)

    private val SUP = "⁰¹²³⁴⁵⁶⁷⁸⁹"
    private val SUB = "₀₁₂₃₄₅₆₇₈₉"

    /** Deutscher Tonname mit hoch-/tiefgestellter Oktavziffer, wie im Orgelbau üblich: A, c, a¹, C₁. */
    fun noteName(n: Int, names: List<String> = NAMES_DIS): String {
        val name = names[pc(n)]
        val octave = Math.floorDiv(n, 12) - 4
        return when {
            octave >= 1 -> name + octave.toString().map { SUP[it - '0'] }.joinToString("")
            octave == 0 -> name
            else -> {
                val big = name.replaceFirstChar { it.uppercaseChar() }
                if (octave == -1) big else big + (-octave - 1).toString().map { SUB[it - '0'] }.joinToString("")
            }
        }
    }
}
