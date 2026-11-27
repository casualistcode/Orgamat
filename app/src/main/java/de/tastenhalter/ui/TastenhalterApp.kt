package de.tastenhalter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.tastenhalter.TastenhalterViewModel
import de.tastenhalter.Tab
import de.tastenhalter.core.Organ
import de.tastenhalter.midi.MidiConnection

@Composable
fun TastenhalterApp(vm: TastenhalterViewModel) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopBar(vm) },
        bottomBar = { TabBar(tab, vm::selectTab) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (tab) {
                Tab.CONNECTION -> ConnectionScreen(vm)
                Tab.KEYBOARD -> KeyboardScreen(vm)
                Tab.TUNING -> TuningScreen(vm)
                Tab.PLAYER -> PlayerScreen(vm)
            }
        }
    }
}

/** Kopfzeile: Verbindungsstatus + großer NOT-AUS-Knopf (immer sichtbar). */
@Composable
private fun TopBar(vm: TastenhalterViewModel) {
    val conn by vm.connection.state.collectAsStateWithLifecycle()
    val active by vm.output.active.collectAsStateWithLifecycle()
    val cfg by Organ.config.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    val (dot, title, subtitle) = when (val s = conn) {
        is MidiConnection.State.Connected ->
            Triple(Ok, "Verbunden", "${s.name} · ${MidiConnection.typeLabel(s.type)}")
        is MidiConnection.State.Connecting -> Triple(Warn, "Verbinde …", s.name)
        MidiConnection.State.Disconnected -> Triple(DangerBright, "Nicht verbunden", "Tippen zum Verbinden")
    }
    val soundingText = when {
        active.isEmpty() -> null
        active.size >= cfg.maxPolyphony -> "${active.size} Töne an (Maximum)"
        active.size == 1 -> "1 Ton an"
        else -> "${active.size} Töne an"
    }

    Surface(color = BarColor) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .height(84.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(PanelColor)
                    .clickable { vm.selectTab(Tab.CONNECTION) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(dot)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    soundingText ?: subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (soundingText != null) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.emergencyStop()
                },
                modifier = Modifier
                    .weight(1.15f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(3.dp, Color(0xFFFFD54F)),
                colors = ButtonDefaults.buttonColors(containerColor = Danger, contentColor = Color.White),
                contentPadding = PaddingValues(4.dp),
            ) {
                Text("NOT-AUS", fontSize = 28.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
        }
    }
}

@Composable
private fun TabBar(current: Tab, onSelect: (Tab) -> Unit) {
    Surface(color = BarColor) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(6.dp)
                .height(64.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Tab.entries.forEach { t ->
                val selected = t == current
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Gold else PanelColor)
                        .clickable { onSelect(t) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        t.label,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}
