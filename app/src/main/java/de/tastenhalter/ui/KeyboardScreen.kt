package de.tastenhalter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.tastenhalter.TastenhalterViewModel
import de.tastenhalter.core.NoteNames
import de.tastenhalter.core.Organ
import de.tastenhalter.core.OrganConfig
import kotlin.math.min

private val KeyWhite = Color(0xFFF3EFE6)
private val KeyBlack = Color(0xFF1A1A1C)
private val KeyActiveWhite = Color(0xFFFFC857)
private val KeyActiveBlack = Color(0xFFE09A2B)

@Composable
fun KeyboardScreen(vm: TastenhalterViewModel) {
    val active by vm.output.active.collectAsStateWithLifecycle()
    val holdMode by vm.keyboard.holdMode.collectAsStateWithLifecycle()
    val held by vm.keyboard.held.collectAsStateWithLifecycle()
    val firstWhiteRaw by vm.keyboardFirstWhite.collectAsStateWithLifecycle()
    val keysSetting by vm.keyboardKeys.collectAsStateWithLifecycle()
    val cfg by Organ.config.collectAsStateWithLifecycle()
    val totalWhites = cfg.whiteKeys.size

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        val autoCount = (maxWidth / 56.dp).toInt().coerceIn(7, totalWhites)
        val count = (keysSetting ?: autoCount).coerceIn(7, totalWhites)
        val maxFirst = totalWhites - count
        val first = firstWhiteRaw.coerceIn(0, maxFirst)
        val last = min(first + count, totalWhites) - 1

        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceChip(
                    if (holdMode) "Haltemodus: AN" else "Haltemodus: AUS",
                    selected = holdMode,
                    onClick = { vm.keyboard.setHoldMode(!holdMode) },
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    "Alle los",
                    onClick = vm.keyboard::releaseHeld,
                    enabled = held.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton(
                    "Oktave",
                    onClick = { vm.setKeyboardFirstWhite((first - 7).coerceIn(0, maxFirst)) },
                    enabled = first > 0,
                    modifier = Modifier.weight(1f),
                    containerColor = PanelColor,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    leadingIcon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                )
                BigButton(
                    "Oktave",
                    onClick = { vm.setKeyboardFirstWhite((first + 7).coerceIn(0, maxFirst)) },
                    enabled = first < maxFirst,
                    modifier = Modifier.weight(1f),
                    containerColor = PanelColor,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${NoteNames.name(cfg.whiteKeys[first])} – ${NoteNames.name(cfg.whiteKeys[last])}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text("Tasten", style = MaterialTheme.typography.bodyMedium)
                SquareButton("−", onClick = { vm.setKeyboardKeys(count - 2) }, enabled = count > 7, size = 52.dp)
                SquareButton("+", onClick = { vm.setKeyboardKeys(count + 2) }, enabled = count < totalWhites, size = 52.dp)
            }

            OrganKeyboard(
                config = cfg,
                firstWhite = first,
                whiteCount = count,
                active = active,
                held = held,
                onDown = vm.keyboard::down,
                onUp = vm.keyboard::up,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )

            val info = if (holdMode) {
                if (held.isEmpty()) "Haltemodus: Taste antippen = Ton an, nochmal antippen = Ton aus."
                else "Gehalten: " + held.sorted().joinToString(", ") { NoteNames.name(it) }
            } else {
                "Ton klingt, solange die Taste gedrückt ist."
            }
            Text(info, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        val bw = ww * 0.62f
        val bh = maxHeight * 0.6f
        val end = min(firstWhite + whiteCount, config.whiteKeys.size)
        for (i in firstWhite until end) {
            val note = config.whiteKeys[i]
            key(note) {
                PianoKey(
                    note = note,
                    white = true,
                    sounding = note in active,
                    held = note in held,
                    onDown = onDown,
                    onUp = onUp,
                    modifier = Modifier
                        .offset(x = ww * (i - firstWhite))
                        .width(ww)
                        .fillMaxHeight(),
                )
            }
        }
        for (note in config.blackKeys) {
            // Schwarze Taste sitzt rechts neben der weißen Taste darunter (ggf. außerhalb des Umfangs).
            val leftWhite = config.whiteIndex(note - 1).takeIf { it >= 0 } ?: -1
            val rel = leftWhite - firstWhite
            if (rel < -1 || rel >= whiteCount) continue
            key(note) {
                PianoKey(
                    note = note,
                    white = false,
                    sounding = note in active,
                    held = note in held,
                    onDown = onDown,
                    onUp = onUp,
                    modifier = Modifier
                        .offset(x = ww * (rel + 1) - bw / 2)
                        .width(bw)
                        .height(bh),
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
        sounding && white -> KeyActiveWhite
        sounding -> KeyActiveBlack
        white -> KeyWhite
        else -> KeyBlack
    }
    val shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
    Box(
        modifier
            .padding(horizontal = if (white) 1.5.dp else 0.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, if (white) Color(0xFF6A6A6A) else Color.Black, shape)
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
            modifier = Modifier.padding(bottom = 10.dp),
        ) {
            if (held) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Danger)
                )
                Spacer(Modifier.height(6.dp))
            }
            if (white) {
                Text(
                    NoteNames.name(note),
                    color = Color(0xFF2A2A2A),
                    fontSize = 15.sp,
                    fontWeight = if (note % 12 == 0) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}
