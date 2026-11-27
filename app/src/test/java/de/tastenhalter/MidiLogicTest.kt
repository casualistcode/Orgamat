package de.tastenhalter

import de.tastenhalter.core.NoteNames
import de.tastenhalter.core.OrganConfig
import de.tastenhalter.midi.MidiFileParser
import de.tastenhalter.player.Arranger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class MidiLogicTest {

    private fun vlq(value: Int): ByteArray {
        var v = value
        val bytes = ArrayList<Int>()
        bytes += v and 0x7F
        v = v shr 7
        while (v > 0) {
            bytes += (v and 0x7F) or 0x80
            v = v shr 7
        }
        return bytes.reversed().map { it.toByte() }.toByteArray()
    }

    private fun ev(delta: Int, vararg data: Int): ByteArray = vlq(delta) + data.map { it.toByte() }.toByteArray()

    private fun smf(format: Int, division: Int, vararg tracks: List<ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("MThd".toByteArray())
        out.write(byteArrayOf(0, 0, 0, 6))
        out.write(byteArrayOf(0, format.toByte(), 0, tracks.size.toByte()))
        out.write(byteArrayOf((division shr 8).toByte(), (division and 0xFF).toByte()))
        for (t in tracks) {
            val body = ByteArrayOutputStream()
            t.forEach { body.write(it) }
            body.write(ev(0, 0xFF, 0x2F, 0x00))
            val b = body.toByteArray()
            out.write("MTrk".toByteArray())
            out.write(byteArrayOf((b.size shr 24).toByte(), (b.size shr 16).toByte(), (b.size shr 8).toByte(), b.size.toByte()))
            out.write(b)
        }
        return out.toByteArray()
    }

    @Test
    fun noteNames() {
        assertEquals("C", NoteNames.name(36))
        assertEquals("c", NoteNames.name(48))
        assertEquals("c'", NoteNames.name(60))
        assertEquals("cis'", NoteNames.name(61))
        assertEquals("a'", NoteNames.name(69))
        assertEquals("b'", NoteNames.name(70))
        assertEquals("h'", NoteNames.name(71))
        assertEquals("g'''", NoteNames.name(91))
    }

    @Test
    fun parsesNotesRunningStatusAndFolding() {
        val data = smf(
            0, 480,
            listOf(
                ev(0, 0xFF, 0x51, 0x03, 0x07, 0xA1, 0x20), // 120 BPM
                ev(0, 0xFF, 0x03, 0x05, 'S'.code, 'o'.code, 'p'.code, 'r'.code, '.'.code),
                ev(0, 0x90, 60, 100),
                ev(480, 0x80, 60, 0),
                ev(0, 0x90, 20, 100),
                ev(240, 20, 0), // Running Status, Velocity 0 = Note Off
            ),
        )
        val song = MidiFileParser.parse(data)
        assertEquals(1, song.parts.size)
        assertEquals("Sopr.", song.parts[0].name)
        assertEquals(2, song.notes.size)
        val c = song.notes.first { it.pitch == 60 }
        assertEquals(0L, c.startUs)
        assertEquals(500_000L, c.endUs)
        val low = song.notes.first { it.pitch == 20 }
        assertEquals(500_000L, low.startUs)
        assertEquals(750_000L, low.endUs)

        val sched = Arranger.build(song, setOf(0), 0)
        assertEquals(1, sched.foldedCount)
        assertTrue(sched.notes.any { it.pitch == 44 })
        assertTrue(sched.notes.all { it.pitch in 36..91 })
    }

    @Test
    fun tempoChangeIsApplied() {
        val data = smf(
            1, 480,
            listOf(
                ev(0, 0xFF, 0x51, 0x03, 0x07, 0xA1, 0x20), // 500 000 µs/Viertel
                ev(480, 0xFF, 0x51, 0x03, 0x0F, 0x42, 0x40), // 1 000 000 µs/Viertel
            ),
            listOf(
                ev(480, 0x91, 64, 90),
                ev(480, 0x81, 64, 0),
            ),
        )
        val song = MidiFileParser.parse(data)
        val n = song.notes.single()
        assertEquals(500_000L, n.startUs)
        assertEquals(1_500_000L, n.endUs)
        assertEquals(1, song.parts.single().channel)
    }

    @Test
    fun polyphonyLimitedTo15() {
        val events = ArrayList<ByteArray>()
        for (p in 40 until 60) events += ev(0, 0x90, p, 100)
        events += ev(480, 0x80, 40, 0)
        for (p in 41 until 60) events += ev(0, 0x80, p, 0)
        val song = MidiFileParser.parse(smf(0, 480, events))
        val sched = Arranger.build(song, setOf(0), 0)
        assertEquals(15, sched.notes.size)
        assertEquals(5, sched.droppedCount)
        var active = 0
        var maxActive = 0
        for (e in sched.events) {
            active += if (e.on) 1 else -1
            maxActive = maxOf(maxActive, active)
        }
        assertEquals(15, maxActive)
        assertEquals(0, active)
    }

    @Test
    fun repeatedNotesGetAGap() {
        val data = smf(
            0, 480,
            listOf(
                ev(0, 0x90, 60, 100),
                ev(480, 0x80, 60, 0),
                ev(0, 0x90, 60, 100),
                ev(480, 0x80, 60, 0),
            ),
        )
        val sched = Arranger.build(MidiFileParser.parse(data), setOf(0), 0)
        assertEquals(2, sched.notes.size)
        assertEquals(500_000L - Arranger.REPEAT_GAP_US, sched.notes[0].endUs)
        assertEquals(500_000L, sched.notes[1].startUs)
    }

    @Test
    fun unisonVoicesAreMerged() {
        val data = smf(
            1, 480,
            listOf(ev(0, 0x90, 60, 100), ev(960, 0x80, 60, 0)),
            listOf(ev(240, 0x90, 60, 100), ev(480, 0x80, 60, 0)),
        )
        val song = MidiFileParser.parse(data)
        assertEquals(2, song.parts.size)
        val sched = Arranger.build(song, setOf(0, 1), 0)
        assertEquals(1, sched.notes.size)
        assertEquals(0L, sched.notes[0].startUs)
        assertEquals(1_000_000L, sched.notes[0].endUs)
    }

    @Test
    fun customRangeAndPolyphony() {
        val cfg = OrganConfig(lowest = 48, highest = 72, maxPolyphony = 3, channel = 4)
        val events = ArrayList<ByteArray>()
        for (p in listOf(30, 50, 52, 55, 90)) events += ev(0, 0x90, p, 100)
        events += ev(480, 0x80, 30, 0)
        for (p in listOf(50, 52, 55, 90)) events += ev(0, 0x80, p, 0)
        val sched = Arranger.build(MidiFileParser.parse(smf(0, 480, events)), setOf(0), 0, cfg)
        assertTrue(sched.notes.all { it.pitch in 48..72 })
        assertEquals(2, sched.foldedCount) // 30 -> 54, 90 -> 66
        assertEquals(3, sched.notes.size)
        assertEquals(2, sched.droppedCount)
    }

    @Test
    fun configIsSanitized() {
        val c = OrganConfig(lowest = 80, highest = 82, maxPolyphony = 0, channel = 20).sanitized()
        assertTrue(c.highest - c.lowest >= OrganConfig.MIN_SPAN - 1)
        assertEquals(1, c.maxPolyphony)
        assertEquals(15, c.channel)
        assertEquals(7, OrganConfig(lowest = 61, highest = 72).whiteKeys.size)
        assertEquals(56, OrganConfig().keyCount)
        assertEquals(1, OrganConfig().outputNumber(36))
    }

    @Test
    fun seekFindsActiveNotes() {
        val data = smf(0, 480, listOf(ev(0, 0x90, 60, 100), ev(960, 0x80, 60, 0)))
        val sched = Arranger.build(MidiFileParser.parse(data), setOf(0), 0)
        assertEquals(1, sched.notesActiveAt(300_000).size)
        assertEquals(0, sched.notesActiveAt(1_000_000).size)
        assertEquals(1, sched.firstEventAfter(0))
    }
}
