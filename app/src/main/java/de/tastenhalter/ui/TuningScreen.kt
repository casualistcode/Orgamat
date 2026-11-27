package de.tastenhalter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.tastenhalter.TastenhalterViewModel
import de.tastenhalter.control.TuningController
import de.tastenhalter.core.NoteNames
import de.tastenhalter.core.Organ
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val STEP_VALUES_MS = listOf(100, 150, 200, 300, 400, 500, 750, 1000, 1500, 2000, 3000, 4000, 5000, 7500, 10000)

private fun formatSeconds(ms: Int): String =
    DecimalFormat("0.##", DecimalFormatSymbols(Locale.GERMANY)).format(ms / 1000.0) + " s"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TuningScreen(vm: TastenhalterViewModel) {
    val s by vm.tuning.state.collectAsStateWithLifecycle()
    val active by vm.output.active.collectAsStateWithLifecycle()
    val cfg by Organ.config.collectAsStateWithLifecycle()
    val t = vm.tuning
    val sounding = s.note in active

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Panel {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    NoteNames.name(s.note),
                    fontSize = 84.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (sounding) Gold else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Ausgang ${cfg.outputNumber(s.note)} · MIDI ${s.note}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (s.interval != TuningController.Interval.NONE) {
                    val second = t.notesFor(s.note, s).getOrNull(1)
                    Text(
                        if (second != null) {
                            "+ ${s.interval.label} ${if (s.downward) "abwärts" else "aufwärts"}: ${NoteNames.name(second)}"
                        } else {
                            "${s.interval.label}: außerhalb des Umfangs " +
                                "${NoteNames.name(cfg.lowest)}–${NoteNames.name(cfg.highest)}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (second != null) MaterialTheme.colorScheme.secondary else Warn,
                        textAlign = TextAlign.Center,
                    )
                }
                Text(
                    when {
                        s.running -> "Durchlauf läuft …"
                        s.holding -> "Ton wird gehalten"
                        s.tapping -> "Ton klingt"
                        else -> "Ton aus"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (s.holding || s.running || s.tapping) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton(
                "voriger",
                onClick = t::previous,
                enabled = s.note > cfg.lowest && !s.running,
                modifier = Modifier.weight(1f),
                containerColor = PanelColor,
                contentColor = MaterialTheme.colorScheme.onSurface,
                minHeight = 72.dp,
                leadingIcon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            )
            BigButton(
                "nächster",
                onClick = t::next,
                enabled = s.note < cfg.highest && !s.running,
                modifier = Modifier.weight(1f),
                containerColor = PanelColor,
                contentColor = MaterialTheme.colorScheme.onSurface,
                minHeight = 72.dp,
                trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            )
        }

        Slider(
            value = s.note.toFloat(),
            onValueChange = { t.select(it.roundToInt()) },
            valueRange = cfg.lowest.toFloat()..cfg.highest.toFloat(),
            steps = cfg.keyCount - 2,
            enabled = !s.running,
        )

        BigButton(
            if (s.holding) "LOSLASSEN" else "TON HALTEN",
            onClick = t::toggleHold,
            modifier = Modifier.fillMaxWidth(),
            containerColor = if (s.holding) Ok else Gold,
            contentColor = if (s.holding) Color.White else MaterialTheme.colorScheme.onPrimary,
            minHeight = 84.dp,
        )
        HoldButton(
            "Antippen (klingt, solange gedrückt)",
            onPress = t::tapDown,
            onRelease = t::tapUp,
            enabled = !s.holding && !s.running,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionTitle("Intervall")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TuningController.Interval.entries.forEach { iv ->
                ChoiceChip(iv.label, selected = s.interval == iv, onClick = { t.setInterval(iv) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(
                "aufwärts",
                selected = !s.downward,
                onClick = { t.setDownward(false) },
                modifier = Modifier.weight(1f),
                enabled = s.interval != TuningController.Interval.NONE,
            )
            ChoiceChip(
                "abwärts",
                selected = s.downward,
                onClick = { t.setDownward(true) },
                modifier = Modifier.weight(1f),
                enabled = s.interval != TuningController.Interval.NONE,
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 6.dp))

        SectionTitle("Chromatischer Durchlauf")
        Stepper(
            "Von",
            NoteNames.name(s.runFrom),
            onMinus = { t.setRunFrom(s.runFrom - 1) },
            onPlus = { t.setRunFrom(s.runFrom + 1) },
            minusEnabled = s.runFrom > cfg.lowest && !s.running,
            plusEnabled = s.runFrom < cfg.highest && !s.running,
        )
        Stepper(
            "Bis",
            NoteNames.name(s.runTo),
            onMinus = { t.setRunTo(s.runTo - 1) },
            onPlus = { t.setRunTo(s.runTo + 1) },
            minusEnabled = s.runTo > cfg.lowest && !s.running,
            plusEnabled = s.runTo < cfg.highest && !s.running,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                "ganzer Umfang",
                onClick = { t.setRunFrom(cfg.lowest); t.setRunTo(cfg.highest) },
                enabled = !s.running,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                "abwärts tauschen",
                onClick = { val a = s.runFrom; t.setRunFrom(s.runTo); t.setRunTo(a) },
                enabled = !s.running,
                modifier = Modifier.weight(1f),
            )
        }

        val stepIndex = STEP_VALUES_MS.indices.minByOrNull { abs(STEP_VALUES_MS[it] - s.stepMs) } ?: 0
        Text(
            "Tondauer: ${formatSeconds(s.stepMs)} pro Ton",
            style = MaterialTheme.typography.titleMedium,
        )
        Slider(
            value = stepIndex.toFloat(),
            onValueChange = { t.setStepMs(STEP_VALUES_MS[it.roundToInt().coerceIn(0, STEP_VALUES_MS.size - 1)]) },
            valueRange = 0f..(STEP_VALUES_MS.size - 1).toFloat(),
            steps = STEP_VALUES_MS.size - 2,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Endlos wiederholen", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = s.loop, onCheckedChange = t::setLoop)
        }
        BigButton(
            if (s.running) "Durchlauf stoppen" else "Durchlauf starten",
            onClick = { if (s.running) t.stopRun() else t.startRun() },
            modifier = Modifier.fillMaxWidth(),
            containerColor = if (s.running) Danger else Gold,
            contentColor = if (s.running) Color.White else MaterialTheme.colorScheme.onPrimary,
            minHeight = 72.dp,
        )
        Text(
            "Tipp: Einen festen Bezugston kannst du zusätzlich auf der Klaviatur im Haltemodus halten.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
