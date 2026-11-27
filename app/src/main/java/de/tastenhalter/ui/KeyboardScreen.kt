package de.tastenhalter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.tastenhalter.TastenhalterViewModel
import de.tastenhalter.control.RunController
import de.tastenhalter.core.Organ
import de.tastenhalter.core.OrganConfig
import de.tastenhalter.tuning.Temperaments
import kotlin.math.min

private val KeyIvory = Color(0xFFEDE6DA)
private val KeyEbony = Color(0xFF1E1C1A)
private val KeyOnWhite = Palette.Orange
private val KeyOnBlack = Color(0xFFE08A2B)

@Composable
fun KeyboardScreen(vm: TastenhalterViewModel) {
    val active by vm.output.active.collectAsStateWithLifecycle()
    val holdMode by vm.keyboard.holdMode.collectAsStateWithLifecycle()
    val held by vm.keyboard.held.collectAsStateWithLifecycle()
    val firstWhiteRaw by vm.keyboardFirstWhite.collectAsStateWithLifecycle()
    val keysSetting by vm.keyboardKeys.collectAsStateWithLifecycle()
    val cfg by Organ.config.collectAsStateWithLifecycle()
    val run by vm.run.state.collectAsStateWithLifecycle()

    KeyboardContent(
        cfg = cfg,
        active = active,
        held = held,
        holdMode = holdMode,
        firstWhiteRaw = firstWhiteRaw,
        keysSetting = keysSetting,
        run = run,
        onFirstWhite = vm::setKeyboardFirstWhite,
        onHoldMode = { vm.keyboard.setHoldMode(!holdMode) },
        onReleaseAll = vm.keyboard::releaseHeld,
        onRun = vm.run::toggle,
        onDown = vm.keyboard::down,
        onUp = vm.keyboard::up,
    )
}

@Composable
fun KeyboardContent(
    cfg: OrganConfig,
    active: Set<Int>,
    held: Set<Int>,
    holdMode: Boolean,
    firstWhiteRaw: Int,
    keysSetting: Int?,
    run: RunController.State,
    onFirstWhite: (Int) -> Unit,
    onHoldMode: () -> Unit,
    onReleaseAll: () -> Unit,
    onRun: () -> Unit,
    onDown: (Int) -> Unit,
    onUp: (Int) -> Unit,
) {
    val total = cfg.whiteKeys.size
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        val landscape = maxWidth > maxHeight
        val autoCount = (maxWidth / 52.dp).toInt().coerceIn(7, total)
        val count = (keysSetting ?: autoCount).coerceIn(7, total)
        val maxFirst = total - count
        val first = firstWhiteRaw.coerceIn(0, maxFirst)
        val last = min(first + count, total) - 1
        val rangeLabel = "${Temperaments.noteName(cfg.whiteKeys[first])} – ${Temperaments.noteName(cfg.whiteKeys[last])}"

        val nav: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundButton(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Oktave tiefer",
                    { onFirstWhite((first - 7).coerceIn(0, maxFirst)) }, size = 44.dp, enabled = first > 0,
                )
                Text(
                    rangeLabel,
                    style = NoteStyle,
                    fontSize = 19.sp,
                    color = Palette.Text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(120.dp),
                )
                RoundButton(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight, "Oktave höher",
                    { onFirstWhite((first + 7).coerceIn(0, maxFirst)) }, size = 44.dp, enabled = first < maxFirst,
                )
            }
        }
        val actions: @Composable () -> Unit = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill(if (holdMode) "Halten an" else "Halten", holdMode, onHoldMode)
                Pill(
                    if (run.running) "Durchlauf stoppen" else "Durchlauf",
                    run.running,
                    onRun,
                    color = Palette.Blue,
                )
            }
        }
        val footer: @Composable () -> Unit = {
            Row(
                Modifier.fillMaxWidth().height(32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val text = when {
                    run.running && run.note != null -> "Durchlauf · ${Temperaments.noteName(run.note)}"
                    held.isNotEmpty() -> "Gehalten: " + held.sorted().joinToString("  ") { Temperaments.noteName(it) }
                    holdMode -> "Tippen schaltet einen Ton an oder aus"
                    else -> "Ton klingt, solange die Taste gedrückt ist"
                }
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (run.running || held.isNotEmpty()) Palette.Text else Palette.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (held.isNotEmpty()) {
                    Text(
                        "Alle los",
                        style = MaterialTheme.typography.labelLarge,
                        color = Palette.Orange,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onReleaseAll)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        Column(Modifier.fillMaxSize()) {
            if (landscape) {
                Row(
                    Modifier.fillMaxWidth().height(52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    nav()
                    Spacer(Modifier.weight(1f))
                    actions()
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().height(56.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) { nav() }
            }
            OrganKeyboard(
                config = cfg,
                firstWhite = first,
                whiteCount = count,
                active = active,
                held = held,
                onDown = onDown,
                onUp = onUp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )
            footer()
            if (!landscape) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { actions() }
            }
        }
    }
}

@Composable
fun OrganKeyboard(
    config: OrganConfig,
    firstWhite: Int,
    whiteCount: Int,
    active: Set<Int>,
    held: Set<Int>,
    onDown: (Int) -> Unit,
    onUp: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.clipToBounds()) {
        val ww = maxWidth / whiteCount
        val bw = ww * 0.6f
        val bh = maxHeight * 0.6f
        val end = min(firstWhite + whiteCount, config.whiteKeys.size)
        for (i in firstWhite until end) {
            val note = config.whiteKeys[i]
            key(note) {
                PianoKey(
                    note, white = true, sounding = note in active, held = note in held,
                    onDown = onDown, onUp = onUp,
                    modifier = Modifier.offset(x = ww * (i - firstWhite)).width(ww).fillMaxHeight(),
                )
            }
        }
        for (note in config.blackKeys) {
            val rel = config.whiteIndex(note - 1) - firstWhite
            if (rel < -1 || rel >= whiteCount) continue
            key(note) {
                PianoKey(
                    note, white = false, sounding = note in active, held = note in held,
                    onDown = onDown, onUp = onUp,
                    modifier = Modifier.offset(x = ww * (rel + 1) - bw / 2).width(bw).height(bh),
                )
            }
        }
    }
}

@Composable
private fun PianoKey(
    note: Int,
    white: Boolean,
    sounding: Boolean,
    held: Boolean,
    onDown: (Int) -> Unit,
    onUp: (Int) -> Unit,
    modifier: Modifier,
) {
    val down by rememberUpdatedState(onDown)
    val up by rememberUpdatedState(onUp)
    val bg = when {
        sounding && white -> KeyOnWhite
        sounding -> KeyOnBlack
        white -> KeyIvory
        else -> KeyEbony
    }
    val shape = RoundedCornerShape(bottomStart = 7.dp, bottomEnd = 7.dp)
    Box(
        modifier
            .padding(horizontal = if (white) 1.dp else 0.dp)
            .clip(shape)
            .background(bg)
            .then(if (!white) Modifier.border(1.dp, Color.Black.copy(alpha = 0.6f), shape) else Modifier)
            .pointerInput(note) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    down(note)
                    try {
                        waitForUpOrCancellation()?.consume()
                    } finally {
                        up(note)
                    }
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            if (held) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (white) Palette.Ground else Palette.Text)
                )
                Spacer(Modifier.height(6.dp))
            }
            if (white) {
                Text(
                    Temperaments.noteName(note),
                    style = NoteStyle,
                    fontSize = 13.sp,
                    color = Color(0xFF5A544C),
                    maxLines = 1,
                )
            }
        }
    }
}
