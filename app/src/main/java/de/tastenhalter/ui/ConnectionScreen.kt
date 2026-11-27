package de.tastenhalter.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.tastenhalter.TastenhalterViewModel
import de.tastenhalter.core.NoteNames
import de.tastenhalter.core.Organ
import de.tastenhalter.core.OrganConfig
import de.tastenhalter.midi.BleMidiScanner
import de.tastenhalter.midi.MidiConnection

@Composable
fun ConnectionScreen(vm: TastenhalterViewModel) {
    val context = LocalContext.current
    val state by vm.connection.state.collectAsStateWithLifecycle()
    val devices by vm.connection.devices.collectAsStateWithLifecycle()
    val message by vm.connection.message.collectAsStateWithLifecycle()
    val autoUsb by vm.connection.autoConnectUsb.collectAsStateWithLifecycle()
    val bleFound by vm.ble.found.collectAsStateWithLifecycle()
    val scanning by vm.ble.scanning.collectAsStateWithLifecycle()
    val bleError by vm.ble.error.collectAsStateWithLifecycle()

    val enableBtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (vm.ble.isBluetoothOn()) vm.ble.start() else vm.ble.setError("Bluetooth ist ausgeschaltet.")
    }

    fun startScan() {
        if (!vm.ble.hasAdapter()) {
            vm.ble.setError("Dieses Handy hat kein Bluetooth.")
            return
        }
        if (!vm.ble.isBluetoothOn()) {
            runCatching { enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                .onFailure { vm.ble.setError("Bitte Bluetooth in den Schnelleinstellungen einschalten.") }
            return
        }
        if (Build.VERSION.SDK_INT < 31 && !vm.ble.isLocationOn()) {
            vm.ble.setError("Bitte den Standort einschalten – Android 10/11 braucht ihn für die BLE-Suche.")
            return
        }
        vm.ble.start()
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) startScan()
        else vm.ble.setError("Ohne die Berechtigung „Geräte in der Nähe“ ist keine Bluetooth-Suche möglich.")
    }

    fun onScanClicked() {
        val missing = BleMidiScanner.requiredPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) startScan() else permLauncher.launch(missing.toTypedArray())
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StatusPanel(state, onDisconnect = vm::disconnect, onTestTone = vm::testTone)

        message?.let { MessageBox(it, Warn, onDismiss = vm.connection::clearMessage) }

        SectionTitle("USB-MIDI")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Automatisch mit USB-MIDI verbinden",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = autoUsb, onCheckedChange = vm.connection::setAutoConnectUsb)
        }
        BigButton(
            "Geräteliste aktualisieren",
            onClick = vm.connection::refresh,
            modifier = Modifier.fillMaxWidth(),
            containerColor = PanelColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            leadingIcon = Icons.Filled.Refresh,
        )
        if (devices.isEmpty()) {
            Text(
                "Kein MIDI-Gerät gefunden. Ein USB-MIDI-Interface oder einen Funk-MIDI-Empfänger per " +
                    "USB-OTG-Adapter anstecken – das Gerät erscheint dann hier automatisch.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val connectedId = (state as? MidiConnection.State.Connected)?.deviceId
        val connectedPort = (state as? MidiConnection.State.Connected)?.port
        devices.forEach { d ->
            DevicePanel(
                entry = d,
                connectedPort = if (d.id == connectedId) connectedPort else null,
                busy = state is MidiConnection.State.Connecting,
                onConnect = { port -> vm.connect(d, port) },
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 6.dp))

        SectionTitle("Bluetooth LE MIDI")
        BigButton(
            if (scanning) "Suche stoppen" else "Nach BLE-MIDI-Geräten suchen",
            onClick = { if (scanning) vm.ble.stop() else onScanClicked() },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = Icons.Filled.Search,
        )
        if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
        bleError?.let { MessageBox(it, DangerBright, onDismiss = { vm.ble.setError(null) }) }
        bleFound.forEach { f ->
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            (if (f.advertisesMidi) "MIDI-Dienst erkannt · " else "") + "${f.rssi} dBm",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (f.advertisesMidi) Ok else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            f.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    BigButton(
                        "Verbinden",
                        onClick = { vm.connectBle(f) },
                        enabled = state !is MidiConnection.State.Connecting,
                    )
                }
            }
        }
        Text(
            "Hinweis: Es ist immer nur ein Gerät verbunden. Wenn deine Orgel per USB-Funkempfänger und " +
                "Bluetooth erreichbar ist, nur einen der beiden Wege benutzen. Nach dem Koppeln erscheint " +
                "ein Bluetooth-Gerät auch oben in der Liste.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider(Modifier.padding(vertical = 6.dp))

        OrganSettingsPanel(vm)
    }
}

/** Umfang, Polyphonie und MIDI-Kanal des angeschlossenen Decoders. */
@Composable
private fun OrganSettingsPanel(vm: TastenhalterViewModel) {
    val cfg by Organ.config.collectAsStateWithLifecycle()
    val minSpan = OrganConfig.MIN_SPAN - 1

    SectionTitle("Orgel-Einstellungen")
    Text(
        "Passend zum MIDI-Decoder der Orgel einstellen. Beim Ändern werden alle Töne abgeschaltet.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Panel {
        Text("Tiefster Ton (Ausgang 1)", style = MaterialTheme.typography.bodyLarge)
        Stepper(
            "",
            "${NoteNames.name(cfg.lowest)} · MIDI ${cfg.lowest}",
            onMinus = { vm.updateOrganConfig { it.copy(lowest = it.lowest - 1) } },
            onPlus = { vm.updateOrganConfig { it.copy(lowest = it.lowest + 1) } },
            minusEnabled = cfg.lowest > 0,
            plusEnabled = cfg.lowest < cfg.highest - minSpan,
        )
        Text("Höchster Ton", style = MaterialTheme.typography.bodyLarge)
        Stepper(
            "",
            "${NoteNames.name(cfg.highest)} · MIDI ${cfg.highest}",
            onMinus = { vm.updateOrganConfig { it.copy(highest = it.highest - 1) } },
            onPlus = { vm.updateOrganConfig { it.copy(highest = it.highest + 1) } },
            minusEnabled = cfg.highest > cfg.lowest + minSpan,
            plusEnabled = cfg.highest < 127,
        )
        Text(
            "${cfg.keyCount} Töne / Ausgänge",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Max. gleichzeitige Töne", style = MaterialTheme.typography.bodyLarge)
        Stepper(
            "",
            "${cfg.maxPolyphony}",
            onMinus = { vm.updateOrganConfig { it.copy(maxPolyphony = it.maxPolyphony - 1) } },
            onPlus = { vm.updateOrganConfig { it.copy(maxPolyphony = it.maxPolyphony + 1) } },
            minusEnabled = cfg.maxPolyphony > 1,
            plusEnabled = cfg.maxPolyphony < cfg.keyCount,
        )
        Text("MIDI-Kanal", style = MaterialTheme.typography.bodyLarge)
        Stepper(
            "",
            "${cfg.channel + 1}",
            onMinus = { vm.updateOrganConfig { it.copy(channel = it.channel - 1) } },
            onPlus = { vm.updateOrganConfig { it.copy(channel = it.channel + 1) } },
            minusEnabled = cfg.channel > 0,
            plusEnabled = cfg.channel < 15,
        )
        SecondaryButton(
            "Standard: C–g''' (56 Töne), 15 Töne, Kanal 1",
            onClick = { vm.updateOrganConfig { OrganConfig() } },
            enabled = cfg != OrganConfig(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StatusPanel(state: MidiConnection.State, onDisconnect: () -> Unit, onTestTone: () -> Unit) {
    Panel {
        when (state) {
            is MidiConnection.State.Connected -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(Ok, 18.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Verbunden", style = MaterialTheme.typography.titleLarge)
                }
                Text(state.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${MidiConnection.typeLabel(state.type)} · Port ${state.port + 1} · " +
                        "Ausgabe auf MIDI-Kanal ${Organ.current.channel + 1}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton("Prüfton a'", onClick = onTestTone, modifier = Modifier.weight(1f))
                    BigButton(
                        "Trennen",
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f),
                        containerColor = DisconnectColor,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            is MidiConnection.State.Connecting -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(Warn, 18.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Verbinde …", style = MaterialTheme.typography.titleLarge)
                }
                Text(state.name, style = MaterialTheme.typography.bodyLarge)
                LinearProgressIndicator(Modifier.fillMaxWidth())
                SecondaryButton("Abbrechen", onClick = onDisconnect, modifier = Modifier.fillMaxWidth())
            }
            MidiConnection.State.Disconnected -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(DangerBright, 18.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Nicht verbunden", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    "Unten ein Gerät auswählen. Ohne Verbindung werden keine Töne an die Orgel gesendet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DevicePanel(
    entry: MidiConnection.DeviceEntry,
    connectedPort: Int?,
    busy: Boolean,
    onConnect: (Int) -> Unit,
) {
    var port by remember(entry.id) { mutableIntStateOf(connectedPort ?: 0) }
    Panel {
        Text(entry.name, style = MaterialTheme.typography.titleMedium)
        Text(
            "${MidiConnection.typeLabel(entry.type)} · ${entry.inputPortNames.size} " +
                (if (entry.inputPortNames.size == 1) "Eingang" else "Eingänge"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (entry.inputPortNames.size > 1) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                entry.inputPortNames.forEachIndexed { i, name ->
                    ChoiceChip(name, selected = port == i, onClick = { port = i })
                }
            }
        }
        val isConnectedHere = connectedPort != null && connectedPort == port
        BigButton(
            if (isConnectedHere) "Verbunden ✓" else "Verbinden",
            onClick = { onConnect(port) },
            enabled = !isConnectedHere && !busy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val DisconnectColor = Color(0xFF3A3D45)
