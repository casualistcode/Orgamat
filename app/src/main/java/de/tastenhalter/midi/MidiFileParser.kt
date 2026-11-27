package de.tastenhalter.midi

import kotlin.math.max
import kotlin.math.min

class MidiParseException(message: String) : Exception(message)

/** Eine Note mit absoluter Zeit in Mikrosekunden (bei Originaltempo). */
data class MidiNote(val part: Int, val pitch: Int, val startUs: Long, val endUs: Long)

/** Eine auswählbare Stimme = Kombination aus Spur und MIDI-Kanal. */
data class MidiPart(
    val index: Int,
    val track: Int,
    val channel: Int,
    val name: String,
    val noteCount: Int,
    val minPitch: Int,
    val maxPitch: Int,
) {
    val isDrums: Boolean get() = channel == 9
}

class MidiSong(
    val format: Int,
    val trackCount: Int,
    val parts: List<MidiPart>,
    val notes: List<MidiNote>,
    val durationUs: Long,
)

/** Leser für Standard-MIDI-Dateien (Format 0/1/2, auch RIFF-RMID). Bewusst tolerant. */
object MidiFileParser {

    private class Eof : Exception()

    private class Reader(val d: ByteArray, var pos: Int, val end: Int) {
        fun u8(): Int {
            if (pos >= end) throw Eof()
            return d[pos++].toInt() and 0xFF
        }

        fun u16(): Int = (u8() shl 8) or u8()
        fun u32(): Long = (u16().toLong() shl 16) or u16().toLong()

        fun varLen(): Int {
            var v = 0
            for (i in 0 until 4) {
                val c = u8()
                v = (v shl 7) or (c and 0x7F)
                if ((c and 0x80) == 0) return v
            }
            return v
        }

        fun bytes(n: Int): ByteArray {
            val e = min(end.toLong(), pos.toLong() + max(0, n)).toInt()
            val r = d.copyOfRange(pos, e)
            pos = e
            return r
        }

        fun skip(n: Int) {
            pos = min(end.toLong(), pos.toLong() + max(0, n)).toInt()
        }
    }

    private class RawNoteEv(val tick: Long, val channel: Int, val pitch: Int, val on: Boolean)
    private class RawTrack(val name: String?, val events: List<RawNoteEv>, val endTick: Long)

    fun parse(data: ByteArray): MidiSong {
        var start = 0
        var end = data.size

        // RIFF-MIDI (.rmi): SMF steckt im "data"-Block
        if (data.size >= 12 && ascii(data, 0) == "RIFF" && ascii(data, 8) == "RMID") {
            var p = 12L
            var found = false
            while (p + 8 <= data.size) {
                val pi = p.toInt()
                val id = ascii(data, pi)
                val len = le32(data, pi + 4)
                if (id == "data") {
                    start = pi + 8
                    end = min(data.size.toLong(), start + len).toInt()
                    found = true
                    break
                }
                p += 8 + len + (len and 1L)
            }
            if (!found) throw MidiParseException("RIFF-Datei ohne MIDI-Daten.")
        }

        val hdr = find(data, "MThd", start, min(end, start + 4096))
        if (hdr < 0) throw MidiParseException("Keine gültige MIDI-Datei (Kennung „MThd“ fehlt).")

        val r = Reader(data, hdr + 4, end)
        var format = 0
        var division = 0
        try {
            val hlen = r.u32()
            format = r.u16()
            r.u16() // angegebene Spuranzahl – wir zählen selbst
            division = r.u16()
            r.pos = min(end.toLong(), hdr + 8 + hlen).toInt()
        } catch (e: Eof) {
            throw MidiParseException("Der MIDI-Dateikopf ist unvollständig.")
        }
        if (division == 0) throw MidiParseException("Ungültige Zeitbasis in der MIDI-Datei.")

        val tempos = ArrayList<Pair<Long, Int>>()
        val tracks = ArrayList<RawTrack>()
        while (r.pos + 8 <= end) {
            val id = ascii(data, r.pos)
            r.pos += 4
            val len = try { r.u32() } catch (e: Eof) { break }
            val chunkStart = r.pos
            val chunkEnd = min(end.toLong(), chunkStart + len).toInt()
            if (id == "MTrk") tracks += parseTrack(data, chunkStart, chunkEnd, tempos)
            r.pos = chunkEnd
        }
        if (tracks.isEmpty()) throw MidiParseException("Die MIDI-Datei enthält keine Spuren.")

        val conv = TickConverter(division, tempos.sortedBy { it.first })

        // Note-On/Off paaren. Key der Stimme: Spur * 16 + Kanal
        val byPart = java.util.TreeMap<Int, MutableList<LongArray>>()
        tracks.forEachIndexed { ti, t ->
            val open = HashMap<Int, Long>()
            fun add(ch: Int, pitch: Int, s: Long, e: Long) {
                byPart.getOrPut(ti * 16 + ch) { ArrayList() }.add(longArrayOf(pitch.toLong(), s, e))
            }
            for (ev in t.events) {
                val key = ev.channel * 128 + ev.pitch
                if (ev.on) {
                    // erneutes Note-On ohne Note-Off: vorherige Note hier beenden
                    open.remove(key)?.let { s -> add(ev.channel, ev.pitch, s, ev.tick) }
                    open[key] = ev.tick
                } else {
                    open.remove(key)?.let { s -> add(ev.channel, ev.pitch, s, ev.tick) }
                }
            }
            for ((key, s) in open) add(key / 128, key % 128, s, max(s, t.endTick))
        }

        val parts = ArrayList<MidiPart>()
        val notes = ArrayList<MidiNote>()
        var duration = 0L
        for ((key, list) in byPart) {
            val idx = parts.size
            val ti = key / 16
            val ch = key % 16
            var lo = 127
            var hi = 0
            for (n in list) {
                val p = n[0].toInt()
                val s = conv.toUs(n[1])
                val e = conv.toUs(n[2])
                notes += MidiNote(idx, p, s, e)
                lo = min(lo, p)
                hi = max(hi, p)
                duration = max(duration, e)
            }
            parts += MidiPart(idx, ti, ch, tracks[ti].name.orEmpty(), list.size, lo, hi)
        }
        if (notes.isEmpty()) throw MidiParseException("Die MIDI-Datei enthält keine Noten.")
        notes.sortBy { it.startUs }
        return MidiSong(format, tracks.size, parts, notes, duration)
    }

    private fun parseTrack(d: ByteArray, from: Int, to: Int, tempos: MutableList<Pair<Long, Int>>): RawTrack {
        val r = Reader(d, from, to)
        var tick = 0L
        var running = 0
        var name: String? = null
        val events = ArrayList<RawNoteEv>()
        try {
            loop@ while (r.pos < to) {
                tick += r.varLen()
                var status = r.u8()
                if (status < 0x80) {
                    if (running == 0) break@loop
                    r.pos--
                    status = running
                }
                when {
                    status == 0xFF -> {
                        val type = r.u8()
                        val len = r.varLen()
                        when (type) {
                            0x2F -> break@loop
                            0x51 -> {
                                val b = r.bytes(len)
                                if (b.size >= 3) {
                                    val us = ((b[0].toInt() and 0xFF) shl 16) or
                                        ((b[1].toInt() and 0xFF) shl 8) or (b[2].toInt() and 0xFF)
                                    tempos += Pair(tick, us)
                                }
                            }
                            0x03 -> {
                                val b = r.bytes(len)
                                if (name == null) name = decodeText(b).takeIf { it.isNotEmpty() }
                            }
                            else -> r.skip(len)
                        }
                    }
                    status == 0xF0 || status == 0xF7 -> r.skip(r.varLen())
                    status >= 0xF0 -> when (status) {
                        0xF1, 0xF3 -> r.skip(1)
                        0xF2 -> r.skip(2)
                        else -> Unit
                    }
                    else -> {
                        running = status
                        val type = status and 0xF0
                        val ch = status and 0x0F
                        val d1 = r.u8() and 0x7F
                        val d2 = if (type == 0xC0 || type == 0xD0) 0 else (r.u8() and 0x7F)
                        if (type == 0x90) events += RawNoteEv(tick, ch, d1, d2 > 0)
                        else if (type == 0x80) events += RawNoteEv(tick, ch, d1, false)
                    }
                }
            }
        } catch (e: Eof) {
            // Spur abgeschnitten – bis hierhin verwenden
        }
        return RawTrack(name, events, tick)
    }

    /** Umrechnung Ticks -> Mikrosekunden über die Tempokarte. */
    private class TickConverter(division: Int, tempos: List<Pair<Long, Int>>) {
        private val ticks: LongArray
        private val baseUs: DoubleArray
        private val usPerTick: DoubleArray

        init {
            if ((division and 0x8000) != 0) {
                val fps = -((division shr 8).toByte().toInt())
                val tpf = max(1, division and 0xFF)
                val realFps = if (fps == 29) 29.97 else fps.toDouble().coerceAtLeast(1.0)
                ticks = longArrayOf(0)
                baseUs = doubleArrayOf(0.0)
                usPerTick = doubleArrayOf(1_000_000.0 / (realFps * tpf))
            } else {
                val ppq = max(1, division)
                val t = ArrayList<Long>()
                val b = ArrayList<Double>()
                val u = ArrayList<Double>()
                t += 0L; b += 0.0; u += 500_000.0 / ppq
                for ((tick, tempo) in tempos) {
                    if (tempo <= 0) continue
                    val upt = tempo.toDouble() / ppq
                    val last = t.size - 1
                    if (tick <= t[last]) {
                        u[last] = upt
                    } else {
                        b += b[last] + (tick - t[last]) * u[last]
                        t += tick
                        u += upt
                    }
                }
                ticks = t.toLongArray()
                baseUs = b.toDoubleArray()
                usPerTick = u.toDoubleArray()
            }
        }

        fun toUs(tick: Long): Long {
            var lo = 0
            var hi = ticks.size - 1
            while (lo < hi) {
                val mid = (lo + hi + 1) ushr 1
                if (ticks[mid] <= tick) lo = mid else hi = mid - 1
            }
            return (baseUs[lo] + (tick - ticks[lo]) * usPerTick[lo]).toLong()
        }
    }

    private fun decodeText(b: ByteArray): String {
        val utf = String(b, Charsets.UTF_8)
        val s = if (utf.contains('�')) String(b, Charsets.ISO_8859_1) else utf
        return s.trim { it <= ' ' }
    }

    private fun ascii(d: ByteArray, p: Int): String =
        if (p < 0 || p + 4 > d.size) "" else String(d, p, 4, Charsets.ISO_8859_1)

    private fun le32(d: ByteArray, p: Int): Long {
        if (p + 4 > d.size) return 0
        return (d[p].toLong() and 0xFF) or ((d[p + 1].toLong() and 0xFF) shl 8) or
            ((d[p + 2].toLong() and 0xFF) shl 16) or ((d[p + 3].toLong() and 0xFF) shl 24)
    }

    private fun find(d: ByteArray, tag: String, from: Int, to: Int): Int {
        val t = tag.toByteArray(Charsets.ISO_8859_1)
        var i = from
        while (i + t.size <= min(to + t.size, d.size)) {
            var ok = true
            for (k in t.indices) if (d[i + k] != t[k]) { ok = false; break }
            if (ok) return i
            i++
        }
        return -1
    }
}
