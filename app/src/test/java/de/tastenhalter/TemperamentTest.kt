package de.tastenhalter

import de.tastenhalter.tuning.Beat
import de.tastenhalter.tuning.Step
import de.tastenhalter.tuning.Temperaments
import org.junit.Assert.assertEquals
import org.junit.Test

/** Schwebungen wie in den Tabellen der Stimmlicht-README (Kammerton 440 Hz). */
class TemperamentTest {

    private fun steps(id: String): List<Step> = Temperaments.steps(Temperaments.byId(id), 440.0)

    private fun check(s: Step, rate: Double, beat: Beat) {
        assertEquals(beat, s.main.beat)
        assertEquals(rate, s.main.rate, 0.006)
    }

    @Test
    fun quintenzirkel() {
        val s = steps("quinten")
        assertEquals(12, s.size)
        check(s[0], 1.49, Beat.ENG)
        check(s[1], 2.23, Beat.WEIT)
        check(s[5], 2.81, Beat.WEIT)
        check(s[11], 1.99, Beat.ENG)
        // Tasten im 4′: a – e¹
        val c = Temperaments.byId("quinten")
        assertEquals("a", Temperaments.noteName(c.key(s[0].from), c.noteNames))
        assertEquals("e¹", Temperaments.noteName(c.key(s[0].to), c.noteNames))
    }

    @Test
    fun steinway() {
        val s = steps("steinway")
        check(s[0], 6.93, Beat.WEIT)
        check(s[3], 0.66, Beat.ENG)
        check(s[8], 0.0, Beat.REIN)
        assertEquals(0.74, s[7].probes[0].rate, 0.006)
        assertEquals(10.38, s[7].probes[1].rate, 0.006)
    }

    @Test
    fun vallotti() {
        val s = steps("vallotti")
        check(s[0], 2.98, Beat.WEIT)
        check(s[2], 0.99, Beat.ENG)
        check(s[3], 0.0, Beat.REIN)
        check(s[10], 1.78, Beat.WEIT)
        assertEquals(10.62, s[10].probes[1].rate, 0.006)
        val c = Temperaments.byId("vallotti")
        assertEquals("A", Temperaments.noteName(c.key(s[0].from), c.noteNames))
    }

    @Test
    fun neidhardt() {
        val ks = steps("neidhardt")
        check(ks[6], 1.11, Beat.ENG)          // H vorläufig
        check(ks[10], 0.56, Beat.ENG)         // H ausgleichen
        check(ks[11], 0.70, Beat.WEIT)        // Dis ausgleichen
        assertEquals(0.53, ks[11].probes[0].rate, 0.006)

        val dorf = steps("neidhardtDorf")
        check(dorf[0], 2.23, Beat.WEIT)
        check(dorf[5], 0.79, Beat.WEIT)
        check(dorf[9], 0.84, Beat.WEIT)

        val gs = steps("neidhardtGross")
        assertEquals(15, gs.size)
        check(gs[10], 0.63, Beat.ENG)
        check(gs[11], 0.74, Beat.WEIT)
        check(gs[14], 0.79, Beat.WEIT)
        assertEquals(0.53, gs[14].probes[0].rate, 0.006)
    }

    @Test
    fun ringPlaces() {
        assertEquals(0, Temperaments.place(69))   // a oben
        assertEquals(1, Temperaments.place(76))   // e
        assertEquals(9, Temperaments.classAt(0))
        assertEquals(listOf(0), Temperaments.path(69, 76))
        assertEquals(4, Temperaments.path(57, 53).size)   // große Terz: vier Bögen
    }
}
