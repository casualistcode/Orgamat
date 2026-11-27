package de.tastenhalter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Piano
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.tastenhalter.SettingsSection
import de.tastenhalter.Tab
import de.tastenhalter.TastenhalterViewModel
import de.tastenhalter.midi.MidiConnection

private fun Tab.icon(): ImageVector = when (this) {
    Tab.TUNING -> Icons.Outlined.Tune
    Tab.KEYBOARD -> Icons.Outlined.Piano
    Tab.PLAYER -> Icons.Outlined.LibraryMusic
}

/** Verbindungszustand für die Kopfzeile. */
data class StatusInfo(val color: Color, val text: String)

@Composable
fun TastenhalterApp(vm: TastenhalterViewModel) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val conn by vm.connection.state.collectAsStateWithLifecycle()
    val active by vm.output.active.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    val status = statusOf(conn, active.size)
    val onStop = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        vm.emergencyStop()
    }

    AppFrame(
        tab = tab,
        status = status,
        onTab = vm::selectTab,
        onStatus = { vm.openSettings(SettingsSection.CONNECTION) },
        onSettings = { vm.openSettings(SettingsSection.CONNECTION) },
        onEmergencyStop = onStop,
    ) { t ->
        when (t) {
            Tab.TUNING -> TuningScreen(vm)
            Tab.KEYBOARD -> KeyboardScreen(vm)
            Tab.PLAYER -> PlayerScreen(vm)
        }
    }

    settings?.let { section -> SettingsScreen(vm, section, vm::closeSettings) }
}

fun statusOf(conn: MidiConnection.State, sounding: Int): StatusInfo {
    val tones = when {
        sounding == 0 -> ""
        sounding == 1 -> " · 1 Ton"
        else -> " · $sounding Töne"
    }
    return when (conn) {
        is MidiConnection.State.Connected -> StatusInfo(Palette.Ok, conn.name + tones)
        is MidiConnection.State.Connecting -> StatusInfo(Palette.Orange, "Verbinde …")
        MidiConnection.State.Disconnected -> StatusInfo(Palette.Danger, "Nicht verbunden$tones")
    }
}

/** Rahmen: hochkant Kopfzeile + Leiste unten, quer schmale Leiste links. */
@Composable
fun AppFrame(
    tab: Tab,
    status: StatusInfo,
    onTab: (Tab) -> Unit,
    onStatus: () -> Unit,
    onSettings: () -> Unit,
    onEmergencyStop: () -> Unit,
    content: @Composable (Tab) -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Palette.Ground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize()) {
                SideRail(tab, status, onTab, onStatus, onSettings, onEmergencyStop)
                Box(Modifier.weight(1f).fillMaxHeight()) { content(tab) }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                TopBar(status, onStatus, onSettings, onEmergencyStop)
                Box(Modifier.weight(1f).fillMaxWidth()) { content(tab) }
                BottomBar(tab, onTab)
            }
        }
    }
}

@Composable
private fun TopBar(status: StatusInfo, onStatus: () -> Unit, onSettings: () -> Unit, onEmergencyStop: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onStatus)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(status.color)
            Spacer(Modifier.width(10.dp))
            Text(
                status.text,
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Settings, "Einstellungen", tint = Palette.Muted)
        }
        Spacer(Modifier.width(8.dp))
        EmergencyStopPill(onEmergencyStop)
    }
}

/** NOT-AUS: immer sichtbar, rot. */
@Composable
fun EmergencyStopPill(onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .height(44.dp)
            .clip(shape)
            .background(Palette.Danger)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Stop, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text("Not-Aus", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BottomBar(tab: Tab, onTab: (Tab) -> Unit) {
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
        Row(Modifier.fillMaxWidth().height(64.dp)) {
            Tab.entries.forEach { t ->
                val sel = t == tab
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(role = Role.Tab) { onTab(t) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(t.icon(), null, tint = if (sel) Palette.Orange else Palette.Muted, modifier = Modifier.size(24.dp))
                    Text(
                        t.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (sel) Palette.Text else Palette.Muted,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun SideRail(
    tab: Tab,
    status: StatusInfo,
    onTab: (Tab) -> Unit,
    onStatus: () -> Unit,
    onSettings: () -> Unit,
    onEmergencyStop: () -> Unit,
) {
    Row {
        Column(
            Modifier
                .width(88.dp)
                .fillMaxHeight()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // NOT-AUS oben
            Column(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Palette.Danger)
                    .clickable(role = Role.Button, onClick = onEmergencyStop)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.Stop, null, tint = Color.White, modifier = Modifier.size(24.dp))
                Text("Not-Aus", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Tab.entries.forEach { t ->
                val sel = t == tab
                Column(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Tab) { onTab(t) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(t.icon(), null, tint = if (sel) Palette.Orange else Palette.Muted, modifier = Modifier.size(24.dp))
                    Text(
                        t.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (sel) Palette.Text else Palette.Muted,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Settings, "Einstellungen", tint = Palette.Muted)
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onStatus)
                    .padding(6.dp),
            ) {
                StatusDot(status.color, 10.dp)
            }
        }
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(Palette.Line)
        )
    }
}
