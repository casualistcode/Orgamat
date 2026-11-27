package de.tastenhalter.midi

/** Ein mit der App ausgeliefertes Lied (assets/lieder). */
data class BundledSong(
    val file: String,
    val title: String,
    val composer: String,
    val sourceUrl: String,
    val license: String,
)

object BundledSongs {
    const val DIR = "lieder"
    const val INDEX = "lieder.tsv"

    /** Liest die Liederliste: Datei, Titel, Komponist, Quelle, Lizenz – durch Tabs getrennt, # = Kommentar. */
    fun parseIndex(text: String): List<BundledSong> =
        text.lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val p = line.split('\t')
                if (p.size < 5) null else BundledSong(p[0], p[1], p[2], p[3], p[4])
            }
            .toList()
}
