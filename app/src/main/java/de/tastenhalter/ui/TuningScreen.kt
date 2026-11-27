package de.tastenhalter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.tastenhalter.SettingsSection
import de.tastenhalter.TastenhalterViewModel
import de.tastenhalter.control.TemperamentController
import de.tastenhalter.tuning.Beat
import de.tastenhalter.tuning.Interval
import de.tastenhalter.tuning.Temperaments
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private fun rateText(r: Double) = String.format(Locale.GERMANY, "%.2f", r)

/** true = Licht steht still (für Bildschirmfotos in Tests). */
val LocalStaticLight = staticCompositionLocalOf { false }

@Composable
fun TuningScreen(vm: TastenhalterViewModel) {
    val s by vm.temperament.state.collectAsStateWithLifecycle()
    val t = vm.temperament
    TuningContent(
        s = s,
        fits = t.fitsOrgan(s),
        onPrev = t::previous,
        onNext = t::next,
        onHold = t::toggleHold,
        onGoTo = t::goTo,
        onProbe = t::selectProbe,
        onSettings = { vm.openSettings(SettingsSection.TUNING) },
    )
}

@Composable
fun TuningContent(
    s: TemperamentController.State,
    fits: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onHold: () -> Unit,
    onGoTo: (Int) -> Unit,
    onProbe: (Int) -> Unit,
    onSettings: () -> Unit,
) {
    val onNoteTap: (Int) -> Unit = { tk -> onGoTo(Temperaments.stepForClass(s.steps, tk)) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val boxW = maxWidth
        val boxH = maxHeight
        if (landscape) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val d = min(boxH - 24.dp, boxW * 0.42f).coerceIn(120.dp, 460.dp)
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    TuningRing(s, d, onNoteTap) { if (s.probe >= 0) onProbe(-1) }
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight().padding(start = 16.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Caption(s, fits, onSettings)
                    StepInfo(s, onProbe, compact = true)
                    Controls(s, onPrev, onHold, onNext, height = 56.dp)
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Caption(s, fits, onSettings)
                BoxWithConstraints(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    val d = min(maxWidth, maxHeight - 8.dp).coerceIn(120.dp, 460.dp)
                    TuningRing(s, d, onNoteTap) { if (s.probe >= 0) onProbe(-1) }
                }
                StepInfo(s, onProbe, compact = false)
                Spacer(Modifier.height(10.dp))
                Controls(s, onPrev, onHold, onNext, height = 68.dp)
            }
        }
    }
}

@Composable
private fun Caption(s: TemperamentController.State, fits: Boolean, onSettings: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onSettings)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "${s.circle.name}  ·  ${String.format(Locale.GERMANY, "%.1f", s.kammerton).removeSuffix(",0")} Hz",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Muted,
            maxLines = 1,
        )
        if (!fits) {
            Text(
                "Tasten liegen außerhalb des Orgel-Umfangs",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Danger,
            )
        }
    }
}

@Composable
private fun StepInfo(s: TemperamentController.State, onProbe: (Int) -> Unit, compact: Boolean) {
    val step = s.step
    val main = step.main
    val accent = Palette.accent(main.beat)
    val dim = if (s.probe >= 0) 0.38f else 1f
    val names = s.circle.noteNames
    fun key(n: Int) = Temperaments.noteName(s.circle.key(n), names)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SectionLabel("Schritt ${s.index + 1}/${s.steps.size}" + if (step.hint.isNotEmpty()) " · ${step.hint}" else "")
        Text(
            "${main.kind} ${key(step.from)} – ${key(step.to)}",
            fontFamily = Serif,
            fontSize = if (compact) 28.sp else 34.sp,
            color = Palette.Text,
            maxLines = 1,
            modifier = Modifier.alpha(dim).padding(top = 4.dp),
        )
        Text(
            main.beat.label,
            style = NoteStyle,
            fontSize = 24.sp,
            color = accent,
            modifier = Modifier.alpha(dim),
        )
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontFamily = Serif, fontSize = 26.sp, color = Palette.Text)) {
                    append(rateText(main.rate))
                }
                withStyle(SpanStyle(fontSize = 15.sp, color = Palette.Muted)) { append("  Schwebungen/s") }
            },
            modifier = Modifier.alpha(dim),
        )
        // Proben: Platz für drei Zeilen, damit der Block bei jedem Schritt gleich hoch bleibt
        val hasProbes = s.steps.any { it.probes.isNotEmpty() }
        if (hasProbes) {
            Column(
                Modifier.height(if (compact) 96.dp else 100.dp).padding(top = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                step.probes.forEachIndexed { i, p -> ProbeRow(p, i == s.probe, ::key) { onProbe(i) } }
            }
        }
    }
}

@Composable
private fun ProbeRow(p: Interval, selected: Boolean, key: (Int) -> String, onClick: () -> Unit) {
    val c = Palette.accent(p.beat)
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Palette.Muted.copy(alpha = 0.75f))) { append("Probe: ") }
            withStyle(SpanStyle(color = Palette.Muted)) { append(p.kind + " ") }
            withStyle(SpanStyle(color = Palette.Text)) { append("${key(p.lower)} – ${key(p.upper)}") }
            append("   ")
            withStyle(SpanStyle(color = c, fontStyle = FontStyle.Italic)) { append(p.beat.label) }
            withStyle(SpanStyle(color = Palette.Muted)) { append("  ${rateText(p.rate)}/s") }
        },
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color.White.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
private fun Controls(
    s: TemperamentController.State,
    onPrev: () -> Unit,
    onHold: () -> Unit,
    onNext: () -> Unit,
    height: Dp,
) {
    val accent = Palette.accent(s.active.beat)
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SoftButton(
            "Zurück",
            onPrev,
            Modifier.weight(1f),
            leading = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            height = height,
        )
        val shape = RoundedCornerShape(22.dp)
        Row(
            Modifier
                .weight(0.85f)
                .height(height)
                .clip(shape)
                .background(if (s.holding) accent.copy(alpha = 0.18f) else Palette.Surface)
                .border(1.dp, if (s.holding) accent else Palette.Line, shape)
                .clickable(role = Role.Switch, onClick = onHold),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(if (s.holding) accent else Palette.Muted.copy(alpha = 0.5f), 10.dp)
            Text(
                if (s.holding) "  Klingt" else "  Halten",
                style = MaterialTheme.typography.titleMedium,
                color = if (s.holding) accent else Palette.Text,
            )
        }
        SoftButton(
            "Weiter",
            onNext,
            Modifier.weight(1f),
            emphasized = true,
            trailing = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            height = height,
        )
    }
}

/** Quintenzirkel mit pulsierendem Licht in der Mitte (wie Stimmlicht). */
@Composable
fun TuningRing(
    s: TemperamentController.State,
    diameter: Dp,
    onNoteTap: (Int) -> Unit,
    onCenterTap: () -> Unit,
) {
    val active = s.active
    val light = Palette.light(active.beat)
    val accent = Palette.accent(s.step.main.beat)
    val brightness = remember { mutableFloatStateOf(1f) }
    val rate = active.rate
    val static = LocalStaticLight.current

    LaunchedEffect(rate, s.index, s.probe, s.circle.id, static) {
        if (static) return@LaunchedEffect
        var start = -1L
        while (true) {
            withFrameNanos { now ->
                if (start < 0) start = now
                val sec = (now - start) / 1e9
                brightness.floatValue = if (rate <= 0.0) 1f else abs(cos(PI * rate * sec)).toFloat()
            }
        }
    }

    val step = s.step
    val activeArcs = Temperaments.path(step.from, step.to).toSet()
    val doneArcs = HashSet<Int>()
    val tuned = HashSet<Int>().apply { add(Temperaments.pitchClass(s.circle.reference)) }
    for (i in 0 until s.index) {
        doneArcs += Temperaments.path(s.steps[i].from, s.steps[i].to)
        tuned += Temperaments.pitchClass(s.steps[i].to)
    }
    val probe = if (s.probe >= 0) step.probes[s.probe] else null

    Box(Modifier.size(diameter)) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val c = center
            val b = brightness.floatValue

            // Lichtschein
            val glowR = r * 1.7f
            drawCircle(
                brush = Brush.radialGradient(
                    0f to light.glow.copy(alpha = 0.30f * b),
                    0.47f to light.glow.copy(alpha = 0.13f * b),
                    0.73f to light.glow.copy(alpha = 0.04f * b),
                    1f to Color.Transparent,
                    center = c,
                    radius = glowR,
                ),
                radius = glowR,
                center = c,
            )
            // Fassung (bleibt immer schwach sichtbar)
            val coreR = r * 0.6f
            drawCircle(Color.White.copy(alpha = 0.018f), coreR, c)
            drawCircle(Color.White.copy(alpha = 0.10f), coreR, c, style = Stroke(1.dp.toPx()))
            // Kern
            drawCircle(
                brush = Brush.radialGradient(
                    0f to light.core, 0.44f to light.mid, 1f to light.rim,
                    center = c, radius = coreR,
                ),
                radius = coreR,
                center = c,
                alpha = b,
            )

            // Ring: zwölf Bögen zwischen den Tönen
            val ringR = r * 0.89f
            val topLeft = Offset(c.x - ringR, c.y - ringR)
            val arcSize = Size(ringR * 2, ringR * 2)
            for (k in 0 until 12) {
                val isActive = k in activeArcs
                drawArc(
                    color = when {
                        isActive -> accent
                        k in doneArcs -> Color.White.copy(alpha = 0.36f)
                        else -> Color.White.copy(alpha = 0.13f)
                    },
                    startAngle = k * 30f + 9f - 90f,
                    sweepAngle = 12f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = (if (isActive) 2.5f else 1.5f).dp.toPx(), cap = StrokeCap.Round),
                )
            }
            // Proben als innere Bögen
            step.probes.forEachIndexed { i, p ->
                val (start, span) = Temperaments.probeSpan(p)
                val pr = r * (0.78f - 0.07f * i)
                val on = i == s.probe
                drawArc(
                    color = Palette.accent(p.beat).copy(alpha = if (on) 1f else 0.42f),
                    startAngle = start - 90f,
                    sweepAngle = span,
                    useCenter = false,
                    topLeft = Offset(c.x - pr, c.y - pr),
                    size = Size(pr * 2, pr * 2),
                    style = Stroke(width = (if (on) 3f else 2f).dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }

        // Mitte antippen: zurück zum Hauptintervall
        Box(
            Modifier
                .align(Alignment.Center)
                .size(diameter * 0.6f)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCenterTap,
                )
        )

        // Die zwölf Töne: antippen springt zu dem Schritt, in dem der Ton gestimmt wird
        val density = LocalDensity.current
        val label = maxOf(44.dp, diameter * 0.21f)
        val fontSize = with(density) { (diameter * 0.052f).toSp() }.let { if (it.value < 13f) 13.sp else it }
        val ringR = diameter / 2 * 0.89f
        for (place in 0 until 12) {
            val tk = Temperaments.classAt(place)
            val angle = place * 30.0 * PI / 180.0
            val x = diameter / 2 + ringR * sin(angle).toFloat() - label / 2
            val y = diameter / 2 - ringR * cos(angle).toFloat() - label / 2
            val isTarget = tk == Temperaments.pitchClass(step.to)
            val color = when {
                probe != null && (tk == Temperaments.pitchClass(probe.lower) || tk == Temperaments.pitchClass(probe.upper)) ->
                    Palette.accent(probe.beat)
                isTarget -> accent
                tk == Temperaments.pitchClass(step.from) -> Palette.Text
                tk in tuned -> Color.White.copy(alpha = 0.56f)
                else -> Color.White.copy(alpha = 0.30f)
            }
            Box(
                Modifier
                    .offset(x, y)
                    .size(label)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onNoteTap(tk) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    s.circle.noteNames[tk],
                    style = NoteStyle,
                    fontSize = fontSize,
                    fontWeight = if (isTarget) FontWeight.Bold else FontWeight.Normal,
                    color = color,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
