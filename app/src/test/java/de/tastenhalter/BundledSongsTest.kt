package de.tastenhalter

import de.tastenhalter.core.OrganConfig
import de.tastenhalter.midi.BundledSongs
import de.tastenhalter.midi.DEFAULT_GROUP
import de.tastenhalter.midi.MidiFileParser
import de.tastenhalter.player.Arranger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Prüft alle mitgelieferten Lieder: Liste vollständig, Dateien lesbar, spielbar im Standard-Umfang. */
class BundledSongsTest {

    private val dir = File("src/main/assets/${BundledSongs.DIR}")

    @Test
    fun allBundledSongsParseAndFitTheOrgan() {
        val songs = BundledSongs.parseIndex(File(dir, BundledSongs.INDEX).readText(Charsets.UTF_8))
        assertTrue("Liederliste ist leer", songs.size >= 10)

        val midiFiles = dir.listFiles { f -> f.name.endsWith(".mid") }!!.map { it.name }.toSet()
        assertEquals("Liste und Dateien passen nicht zusammen", midiFiles, songs.map { it.file }.toSet())

        val cfg = OrganConfig()
        for (s in songs) {
            assertTrue(
                "Lizenz von ${s.file} nicht freigegeben: ${s.license}",
                listOf("Public Domain", "CC0", "CC BY 3.0", "CC BY 4.0").any { s.license.startsWith(it) },
            )
            assertTrue("Quelle fehlt bei ${s.file}", s.sourceUrl.startsWith("https://"))
            assertTrue("Gruppe fehlt bei ${s.file}", s.group != DEFAULT_GROUP)
            val song = MidiFileParser.parse(File(dir, s.file).readBytes())
            val parts = song.parts.filter { !it.isDrums }.map { it.index }.toSet()
            val sched = Arranger.build(song, parts, 0, cfg)
            var active = 0
            var maxActive = 0
            for (e in sched.events) {
                active += if (e.on) 1 else -1
                maxActive = maxOf(maxActive, active)
            }
            println(
                "%-34s %3d s  %2d Spuren  %4d Noten  max %2d gleichzeitig  %3d oktaviert  %d ausgelassen".format(
                    s.file, sched.durationUs / 1_000_000, song.parts.size, sched.notes.size,
                    maxActive, sched.foldedCount, sched.droppedCount,
                )
            )
            assertTrue("${s.file} hat keine Noten", sched.notes.isNotEmpty())
            assertTrue("${s.file} außerhalb des Umfangs", sched.notes.all { it.pitch in cfg.range })
            assertTrue("${s.file} zu lang/kurz", sched.durationUs in 10_000_000L..900_000_000L)
            assertEquals("${s.file}: Töne wegen 15er-Grenze ausgelassen", 0, sched.droppedCount)
        }
    }
}
