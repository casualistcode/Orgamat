package de.tastenhalter.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.tastenhalter.TastenhalterViewModel
import de.tastenhalter.core.NoteNames
import de.tastenhalter.core.Organ
import de.tastenhalter.midi.BundledSong
import de.tastenhalter.midi.MidiPart
import de.tastenhalter.player.MidiPlayer
import kotlin.math.max
import kotlin.math.roundToInt

private fun formatTime(us: Long): String {
    val totalSec = (us / 1_000_000L).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

@Composable
fun PlayerScreen(vm: TastenhalterViewModel) {
    val st by vm.player.state.collectAsStateWithLifecycle()
    val p = vm.player
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.loadMidi(uri)
    }
    // Liederliste anfangs offen, solange noch nichts geladen ist.
    var showSongs by rememberSaveable { mutableStateOf(st.fileName == null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton(
                "Datei öffnen …",
                onClick = { openLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.weight(1f),
                containerColor = PanelColor,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
            if (vm.bundledSongs.isNotEmpty()) {
                BigButton(
                    if (showSongs) "Liste zu" else "Mitgelieferte Lieder",
                    onClick = { showSongs = !showSongs },
                    modifier = Modifier.weight(1f),
                    containerColor = if (showSongs) Gold else PanelColor,
                    contentColor = if (showSongs) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (st.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        st.error?.let { MessageBox(it, DangerBright) }

        if (showSongs && vm.bundledSongs.isNotEmpty()) {
            BundledSongList(
                songs = vm.bundledSongs,
                currentTitle = st.fileName,
                onSelect = { song ->
                    vm.loadBundled(song)
                    showSongs = false
                },
            )
        }

        if (st.fileName == null) {
            Text(
                "Noch keine Datei geladen. Ein mitgeliefertes Lied wählen, eine .mid-Datei vom Handy öffnen – " +
                    "oder im Dateimanager auf eine .mid-Datei tippen und „Tastenhalter“ wählen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LoadedPlayer(st, p)
        }
    }
}

@Composable
private fun BundledSongList(songs: List<BundledSong>, currentTitle: String?, onSelect: (BundledSong) -> Unit) {
    Panel {
        Text("Mitgelieferte Lieder", style = MaterialTheme.typography.titleLarge, color = Gold)
        songs.forEachIndexed { i, song ->
            if (i > 0) HorizontalDivider(color = Outline.copy(alpha = 0.5f))
            val current = song.title == currentTitle
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelect(song) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
            ) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (current) Gold else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    song.composer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            "Gemeinfreie Sätze aus dem Mutopia Project (mutopiaproject.org).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadedPlayer(st: MidiPlayer.UiState, p: MidiPlayer) {
    val cfg by Organ.config.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Panel {
            Text(st.fileName ?: "", style = MaterialTheme.typography.titleLarge)
            Text(
                "Dauer ${formatTime(st.durationUs)} · ${st.noteCount} Noten · ${st.parts.size} Spuren",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                when (st.status) {
                    MidiPlayer.Status.PLAYING -> "▶ Wiedergabe läuft"
                    MidiPlayer.Status.PAUSED -> "Pausiert"
                    MidiPlayer.Status.STOPPED -> "Gestoppt"
                },
                style = MaterialTheme.typography.titleMedium,
                color = when (st.status) {
                    MidiPlayer.Status.PLAYING -> Ok
                    MidiPlayer.Status.PAUSED -> Warn
                    MidiPlayer.Status.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        // Transport
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton(
                if (st.status == MidiPlayer.Status.PAUSED) "Weiter" else "Start",
                onClick = p::play,
                enabled = st.status != MidiPlayer.Status.PLAYING,
                modifier = Modifier.weight(1.2f),
                containerColor = Ok,
                contentColor = Color.White,
                minHeight = 76.dp,
                leadingIcon = Icons.Filled.PlayArrow,
            )
            BigButton(
                "Pause",
                onClick = p::pause,
                enabled = st.status == MidiPlayer.Status.PLAYING,
                modifier = Modifier.weight(1f),
                containerColor = Warn,
                contentColor = Color.Black,
                minHeight = 76.dp,
            )
            BigButton(
                "Stopp",
                onClick = p::stop,
                enabled = st.status != MidiPlayer.Status.STOPPED || st.positionUs > 0,
                modifier = Modifier.weight(1f),
                containerColor = Color(0xFF3A3D45),
                contentColor = Color.White,
                minHeight = 76.dp,
            )
        }

        // Position
        var dragPos by remember { mutableStateOf<Float?>(null) }
        val dur = max(1L, st.durationUs).toFloat()
        Slider(
            value = (dragPos ?: st.positionUs.toFloat()).coerceIn(0f, dur),
            onValueChange = { dragPos = it },
            onValueChangeFinished = {
                dragPos?.let { p.seekTo(it.toLong()) }
                dragPos = null
            },
            valueRange = 0f..dur,
        )
        Row {
            Text(
                formatTime(dragPos?.toLong() ?: st.positionUs),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(formatTime(st.durationUs), style = MaterialTheme.typography.titleMedium)
        }

        HorizontalDivider()

        // Tempo
        Text("Tempo: ${st.tempoPercent} %", style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SquareButton("−", onClick = { p.setTempo(st.tempoPercent - 5) }, enabled = st.tempoPercent > 25)
            Slider(
                value = st.tempoPercent.toFloat(),
                onValueChange = { p.setTempo((it / 5f).roundToInt() * 5) },
                valueRange = 25f..200f,
                steps = (200 - 25) / 5 - 1,
                modifier = Modifier.weight(1f),
            )
            SquareButton("+", onClick = { p.setTempo(st.tempoPercent + 5) }, enabled = st.tempoPercent < 200)
        }
        SecondaryButton(
            "Originaltempo (100 %)",
            onClick = { p.setTempo(100) },
            enabled = st.tempoPercent != 100,
            modifier = Modifier.fillMaxWidth(),
        )

        Stepper(
            "Transp.",
            when {
                st.transpose > 0 -> "+${st.transpose} HT"
                st.transpose < 0 -> "${st.transpose} HT"
                else -> "0 HT"
            },
            onMinus = { p.setTranspose(st.transpose - 1) },
            onPlus = { p.setTranspose(st.transpose + 1) },
            minusEnabled = st.transpose > -12,
            plusEnabled = st.transpose < 12,
        )

        if (st.foldedCount > 0) {
            Text(
                "${st.foldedCount} Noten außerhalb ${NoteNames.name(cfg.lowest)}–${NoteNames.name(cfg.highest)} " +
                    "wurden in den Umfang oktaviert.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        if (st.droppedCount > 0) {
            Text(
                "${st.droppedCount} Noten wegen der Grenze von ${cfg.maxPolyphony} gleichzeitigen Tönen ausgelassen.",
                style = MaterialTheme.typography.bodyMedium,
                color = Warn,
            )
        }

        HorizontalDivider()

        SectionTitle("Spuren")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Alle", onClick = { p.selectAll(true) }, modifier = Modifier.weight(1f))
            SecondaryButton("Keine", onClick = { p.selectAll(false) }, modifier = Modifier.weight(1f))
        }
        st.parts.forEach { part ->
            PartRow(part, selected = part.index in st.selected, onToggle = { p.togglePart(part.index) })
        }
        Text(
            "Alle Spuren werden auf MIDI-Kanal ${cfg.channel + 1} gelegt. Schlagzeug (Kanal 10) ist anfangs abgewählt.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
private fun PartRow(part: MidiPart, selected: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            val title = buildString {
                append("Spur ${part.track + 1} · Kanal ${part.channel + 1}")
                if (part.isDrums) append(" (Schlagzeug)")
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (part.name.isNotBlank()) {
                Text(part.name, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "${part.noteCount} Noten · ${NoteNames.name(part.minPitch)} – ${NoteNames.name(part.maxPitch)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
