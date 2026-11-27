package de.tastenhalter.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.tastenhalter.TastenhalterViewModel
import de.tastenhalter.core.Organ
import de.tastenhalter.midi.BundledSong
import de.tastenhalter.midi.MidiPart
import de.tastenhalter.player.MidiPlayer
import de.tastenhalter.tuning.Temperaments
import kotlin.math.max
import kotlin.math.roundToInt

private fun formatTime(us: Long): String {
    val totalSec = (us / 1_000_000L).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

@Composable
fun PlayerScreen(vm: TastenhalterViewModel) {
    val st by vm.player.state.collectAsStateWithLifecycle()
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.loadMidi(uri)
    }
    PlayerContent(
        st = st,
        songs = vm.bundledSongs,
        player = vm.player,
        onSelect = vm::loadBundled,
        onOpenFile = { openLauncher.launch(arrayOf("*/*")) },
    )
}

@Composable
fun PlayerContent(
    st: MidiPlayer.UiState,
    songs: List<BundledSong>,
    player: MidiPlayer?,
    onSelect: (BundledSong) -> Unit,
    onOpenFile: () -> Unit,
) {
    var showOptions by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(end = 12.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    NowPlaying(st, player) { showOptions = true }
                }
                Library(songs, st.fileName, onSelect, onOpenFile, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                NowPlaying(st, player) { showOptions = true }
                Spacer(Modifier.height(14.dp))
                Library(songs, st.fileName, onSelect, onOpenFile, Modifier.weight(1f))
            }
        }
    }
    if (showOptions && player != null) {
        SongOptionsSheet(st, player) { showOptions = false }
    }
}

@Composable
private fun NowPlaying(st: MidiPlayer.UiState, p: MidiPlayer?, onOptions: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (st.fileName == null) {
            SectionLabel("Kein Lied geladen", Modifier.padding(top = 10.dp))
            Text(
                "Ein Lied aus der Liste antippen oder eine eigene MIDI-Datei öffnen.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Muted,
                modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
            )
        } else {
            SectionLabel(
                when (st.status) {
                    MidiPlayer.Status.PLAYING -> "Wiedergabe"
                    MidiPlayer.Status.PAUSED -> "Pausiert"
                    MidiPlayer.Status.STOPPED -> "Bereit"
                },
                color = if (st.status == MidiPlayer.Status.PLAYING) Palette.Orange else Palette.Muted,
            )
            Text(
                st.fileName,
                fontFamily = Serif,
                fontSize = 24.sp,
                color = Palette.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            st.subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1)
            }

            var dragPos by remember { mutableStateOf<Float?>(null) }
            val dur = max(1L, st.durationUs).toFloat()
            ThinSlider(
                value = (dragPos ?: st.positionUs.toFloat()) / dur,
                onChange = { dragPos = it * dur },
                onFinished = {
                    dragPos?.let { p?.seekTo(it.toLong()) }
                    dragPos = null
                },
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text(formatTime(dragPos?.toLong() ?: st.positionUs), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                Spacer(Modifier.weight(1f))
                Text(formatTime(st.durationUs), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
            Row(
                Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundButton(
                    Icons.Filled.Stop, "Stopp", { p?.stop() }, size = 52.dp,
                    enabled = st.status != MidiPlayer.Status.STOPPED || st.positionUs > 0,
                )
                val playing = st.status == MidiPlayer.Status.PLAYING
                RoundButton(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (playing) "Pause" else "Start",
                    { if (playing) p?.pause() else p?.play() },
                    size = 72.dp,
                    filled = true,
                    color = Palette.Orange,
                )
                RoundButton(Icons.Outlined.Tune, "Lied-Optionen", onOptions, size = 52.dp)
            }
            val info = buildList {
                if (st.tempoPercent != 100) add("Tempo ${st.tempoPercent} %")
                if (st.transpose != 0) add("Transponiert ${if (st.transpose > 0) "+" else ""}${st.transpose}")
                if (st.droppedCount > 0) add("${st.droppedCount} Töne ausgelassen")
            }
            if (info.isNotEmpty()) {
                Text(
                    info.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        if (st.loading) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                color = Palette.Orange,
                trackColor = Palette.Line,
            )
        }
        st.error?.let { Banner(it, Palette.Danger, Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun Library(
    songs: List<BundledSong>,
    current: String?,
    onSelect: (BundledSong) -> Unit,
    onOpenFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = remember(songs) { songs.map { it.group }.distinct() }
    var group by rememberSaveable {
        mutableStateOf(songs.firstOrNull { it.title == current }?.group ?: groups.firstOrNull().orEmpty())
    }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Lieder", Modifier.weight(1f))
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onOpenFile)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.FolderOpen, null, tint = Palette.Muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Datei öffnen", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            groups.forEach { g -> Pill(g, g == group, { group = g }) }
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(songs.filter { it.group == group }, key = { it.file }) { song ->
                val sel = song.title == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (sel) Palette.Surface else Color.Transparent)
                        .clickable { onSelect(song) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(30.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (sel) Palette.Orange else Color.Transparent)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            song.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (sel) Palette.Orange else Palette.Text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            song.composer,
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongOptionsSheet(st: MidiPlayer.UiState, p: MidiPlayer, onDismiss: () -> Unit) {
    val cfg by Organ.config.collectAsStateWithLifecycle()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF151412),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(st.fileName ?: "", fontFamily = Serif, fontSize = 22.sp, color = Palette.Text, maxLines = 2)

            SectionLabel("Tempo")
            Row(verticalAlignment = Alignment.CenterVertically) {
                ThinSlider(
                    value = (st.tempoPercent - 25) / 175f,
                    onChange = { f -> p.setTempo(((25 + f * 175) / 5f).roundToInt() * 5) },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${st.tempoPercent} %",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Text,
                    modifier = Modifier.width(72.dp).padding(start = 12.dp),
                )
            }
            if (st.tempoPercent != 100) {
                Pill("Originaltempo", false, { p.setTempo(100) })
            }

            SectionLabel("Transponieren")
            MiniStepper(
                when {
                    st.transpose > 0 -> "+${st.transpose} Halbtöne"
                    st.transpose < 0 -> "${st.transpose} Halbtöne"
                    else -> "0 Halbtöne"
                },
                onMinus = { p.setTranspose(st.transpose - 1) },
                onPlus = { p.setTranspose(st.transpose + 1) },
                minusEnabled = st.transpose > -12,
                plusEnabled = st.transpose < 12,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Spuren", Modifier.weight(1f))
                Pill("Alle", false, { p.selectAll(true) })
                Spacer(Modifier.width(8.dp))
                Pill("Keine", false, { p.selectAll(false) })
            }
            Card {
                st.parts.forEachIndexed { i, part ->
                    if (i > 0) RowDivider()
                    PartRow(part, part.index in st.selected) { p.togglePart(part.index) }
                }
            }
            val notes = buildList {
                add("Alles wird auf MIDI-Kanal ${cfg.channel + 1} gespielt.")
                if (st.foldedCount > 0) add("${st.foldedCount} Noten außerhalb des Umfangs werden oktaviert.")
                if (st.droppedCount > 0) add("${st.droppedCount} Noten fallen wegen der Grenze von ${cfg.maxPolyphony} Tönen weg.")
            }
            Text(notes.joinToString(" "), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
    }
}

@Composable
private fun PartRow(part: MidiPart, selected: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = Palette.Orange, checkmarkColor = Palette.Ground),
        )
        Column(Modifier.weight(1f)) {
            Text(
                part.name.ifBlank { "Spur ${part.track + 1}" } + if (part.isDrums) " (Schlagzeug)" else "",
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Spur ${part.track + 1} · Kanal ${part.channel + 1} · ${part.noteCount} Noten · " +
                    "${Temperaments.noteName(part.minPitch)}–${Temperaments.noteName(part.maxPitch)}",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Muted,
                maxLines = 1,
            )
        }
    }
}
