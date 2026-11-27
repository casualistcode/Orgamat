package de.tastenhalter.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.tastenhalter.SettingsSection
import de.tastenhalter.TastenhalterViewModel
import de.tastenhalter.core.Organ
import de.tastenhalter.core.OrganConfig
import de.tastenhalter.midi.BleMidiScanner
import de.tastenhalter.midi.MidiConnection
import de.tastenhalter.tuning.Temperaments
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

private val STEP_VALUES_MS = listOf(100, 150, 200, 300, 400, 500, 750, 1000, 1500, 2000, 3000, 4000, 5000, 7500, 10000)

private fun seconds(ms: Int) = DecimalFormat("0.##", DecimalFormatSymbols(Locale.GERMANY)).format(ms / 1000.0) + " s"
private fun hz(v: Double) = DecimalFormat("0.0", DecimalFormatSymbols(Locale.GERMANY)).format(v).removeSuffix(",0") + " Hz"

@Composable
fun SettingsScreen(vm: TastenhalterViewModel, section: SettingsSection, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val listState = rememberLazyListState()
    LaunchedEffect(section) {
        listState.scrollToItem(
            when (section) {
                SettingsSection.CONNECTION -> 0
                SettingsSection.TUNING -> 2
                SettingsSection.KEYBOARD -> 4
                SettingsSection.ORGAN -> 6
                SettingsSection.INFO -> 8
            }
        )
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Ground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = Palette.Text)
            }
            Text("Einstellungen", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            item { Header("Verbindung") }
            item { ConnectionSettings(vm) }
            item { Header("Stimmen") }
            item { TuningSettings(vm) }
            item { Header("Klaviatur und Durchlauf") }
            item { KeyboardSettings(vm) }
            item { Header("Orgel") }
            item { OrganSettingsCard(vm) }
            item { Header("Info") }
            item { InfoCard() }
        }
    }
}

@Composable
private fun Header(text: String) {
    SectionLabel(text, Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp))
}

@Composable
private fun AppSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Palette.Ground,
            checkedTrackColor = Palette.Orange,
            uncheckedThumbColor = Palette.Muted,
            uncheckedTrackColor = Palette.Surface,
            uncheckedBorderColor = Palette.LineStrong,
        ),
    )
}

@Composable
private fun TextAction(text: String, enabled: Boolean = true, color: androidx.compose.ui.graphics.Color = Palette.Orange, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (enabled) color else Palette.Muted.copy(alpha = 0.5f),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

// ---------------------------------------------------------------- Verbindung

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectionSettings(vm: TastenhalterViewModel) {
    val context = LocalContext.current
    val state by vm.connection.state.collectAsStateWithLifecycle()
    val devices by vm.connection.devices.collectAsStateWithLifecycle()
    val message by vm.connection.message.collectAsStateWithLifecycle()
    val autoUsb by vm.connection.autoConnectUsb.collectAsStateWithLifecycle()
    val bleFound by vm.ble.found.collectAsStateWithLifecycle()
    val scanning by vm.ble.scanning.collectAsStateWithLifecycle()
    val bleError by vm.ble.error.collectAsStateWithLifecycle()
    val cfg by Organ.config.collectAsStateWithLifecycle()

    val enableBt = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (vm.ble.isBluetoothOn()) vm.ble.start() else vm.ble.setError("Bluetooth ist ausgeschaltet.")
    }

    fun startScan() {
        when {
            !vm.ble.hasAdapter() -> vm.ble.setError("Dieses Handy hat kein Bluetooth.")
            !vm.ble.isBluetoothOn() -> runCatching { enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                .onFailure { vm.ble.setError("Bitte Bluetooth einschalten.") }
            Build.VERSION.SDK_INT < 31 && !vm.ble.isLocationOn() ->
                vm.ble.setError("Bitte den Standort einschalten – Android 10/11 braucht ihn für die Bluetooth-Suche.")
            else -> vm.ble.start()
        }
    }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) startScan()
        else vm.ble.setError("Ohne die Berechtigung „Geräte in der Nähe“ ist keine Bluetooth-Suche möglich.")
    }

    fun onScan() {
        val missing = BleMidiScanner.requiredPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) startScan() else permissions.launch(missing.toTypedArray())
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card {
            val (dot, title, sub) = when (val s = state) {
                is MidiConnection.State.Connected -> Triple(
                    Palette.Ok, s.name,
                    "${MidiConnection.typeLabel(s.type)} · Port ${s.port + 1} · MIDI-Kanal ${cfg.channel + 1}",
                )
                is MidiConnection.State.Connecting -> Triple(Palette.Orange, "Verbinde …", s.name)
                MidiConnection.State.Disconnected -> Triple(Palette.Danger, "Nicht verbunden", "Gerät unten auswählen")
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(dot, 10.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, color = Palette.Text)
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
                when (state) {
                    is MidiConnection.State.Connected -> {
                        TextAction("Prüfton") { vm.testTone() }
                        TextAction("Trennen", color = Palette.Muted) { vm.disconnect() }
                    }
                    is MidiConnection.State.Connecting -> TextAction("Abbrechen", color = Palette.Muted) { vm.disconnect() }
                    MidiConnection.State.Disconnected -> {}
                }
            }
            RowDivider()
            SettingRow("Automatisch mit USB-MIDI verbinden", subtitle = "Beim Einstecken eines USB-MIDI-Geräts") {
                AppSwitch(autoUsb, vm::setAutoConnectUsb)
            }
        }
        message?.let { Banner(it, Palette.Orange, onDismiss = vm.connection::clearMessage) }

        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("USB-MIDI", Modifier.weight(1f).padding(start = 4.dp))
            IconButton(onClick = vm.connection::refresh) {
                Icon(Icons.Outlined.Refresh, "Liste aktualisieren", tint = Palette.Muted)
            }
        }
        Card {
            if (devices.isEmpty()) {
                SettingRow(
                    "Kein MIDI-Gerät gefunden",
                    subtitle = "USB-MIDI-Interface oder Funk-MIDI-Empfänger per USB-OTG anstecken.",
                )
            }
            val connected = state as? MidiConnection.State.Connected
            devices.forEachIndexed { i, d ->
                if (i > 0) RowDivider()
                var port by remember(d.id) { mutableIntStateOf(if (connected?.deviceId == d.id) connected.port else 0) }
                val here = connected?.deviceId == d.id && connected.port == port
                Column {
                    SettingRow(
                        d.name,
                        subtitle = "${MidiConnection.typeLabel(d.type)} · ${d.inputPortNames.size} " +
                            if (d.inputPortNames.size == 1) "Eingang" else "Eingänge",
                    ) {
                        if (here) Text("Verbunden", style = MaterialTheme.typography.labelLarge, color = Palette.Ok)
                        else TextAction("Verbinden", enabled = state !is MidiConnection.State.Connecting) { vm.connect(d, port) }
                    }
                    if (d.inputPortNames.size > 1) {
                        FlowRow(
                            Modifier.padding(start = 18.dp, end = 18.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            d.inputPortNames.forEachIndexed { pi, name -> Pill(name, port == pi, { port = pi }) }
                        }
                    }
                }
            }
        }

        SectionLabel("Bluetooth LE MIDI", Modifier.padding(start = 4.dp, top = 6.dp))
        Card {
            SettingRow(
                if (scanning) "Suche läuft …" else "Nach Bluetooth-MIDI-Geräten suchen",
                subtitle = "Das Gerät muss eingeschaltet und in der Nähe sein.",
            ) {
                if (scanning) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Palette.Orange, strokeWidth = 2.dp)
                    TextAction("Stopp", color = Palette.Muted) { vm.ble.stop() }
                } else {
                    TextAction("Suchen") { onScan() }
                }
            }
            bleFound.forEach { f ->
                RowDivider()
                SettingRow(
                    f.name,
                    subtitle = (if (f.advertisesMidi) "MIDI-Gerät · " else "") + "${f.rssi} dBm",
                ) {
                    TextAction("Verbinden", enabled = state !is MidiConnection.State.Connecting) { vm.connectBle(f) }
                }
            }
        }
        bleError?.let { Banner(it, Palette.Danger, onDismiss = { vm.ble.setError(null) }) }
    }
}

// ---------------------------------------------------------------- Stimmen

@Composable
private fun TuningSettings(vm: TastenhalterViewModel) {
    val s by vm.temperament.state.collectAsStateWithLifecycle()
    Card {
        Temperaments.circles.forEachIndexed { i, c ->
            if (i > 0) RowDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.temperament.setCircle(c.id) }
                    .padding(start = 6.dp, end = 18.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = c.id == s.circle.id,
                    onClick = { vm.temperament.setCircle(c.id) },
                    colors = RadioButtonDefaults.colors(selectedColor = Palette.Orange, unselectedColor = Palette.Muted),
                )
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.bodyLarge, color = Palette.Text)
                    Text(
                        "${c.steps.size} Schritte · " + if (c.foot != null) "auf einem ${c.foot}′-Register" else "Klavier",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Muted,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Card {
        SettingRow("Kammerton", subtitle = "klingendes a¹") {
            MiniStepper(
                hz(s.kammerton),
                onMinus = { vm.temperament.setKammerton(s.kammerton - 0.5) },
                onPlus = { vm.temperament.setKammerton(s.kammerton + 0.5) },
                minusEnabled = s.kammerton > Temperaments.KAMMERTON_MIN,
                plusEnabled = s.kammerton < Temperaments.KAMMERTON_MAX,
            )
        }
        if (s.kammerton != Temperaments.DEFAULT_KAMMERTON) {
            RowDivider()
            SettingRow("Auf 440 Hz zurücksetzen", onClick = { vm.temperament.setKammerton(Temperaments.DEFAULT_KAMMERTON) })
        }
    }
}

// ---------------------------------------------------------------- Klaviatur und Durchlauf

@Composable
private fun KeyboardSettings(vm: TastenhalterViewModel) {
    val keys by vm.keyboardKeys.collectAsStateWithLifecycle()
    val run by vm.run.state.collectAsStateWithLifecycle()
    val cfg by Organ.config.collectAsStateWithLifecycle()
    val total = cfg.whiteKeys.size
    Card {
        SettingRow("Sichtbare Tasten", subtitle = "Weiße Tasten auf dem Bildschirm") {
            MiniStepper(
                keys?.toString() ?: "Auto",
                onMinus = { vm.setKeyboardKeys(if (keys == null) total else if (keys!! <= 7) null else keys!! - 2) },
                onPlus = { vm.setKeyboardKeys(if (keys == null) 7 else if (keys!! >= total) null else keys!! + 2) },
            )
        }
        RowDivider()
        SettingRow("Durchlauf von") {
            MiniStepper(
                Temperaments.noteName(run.from),
                onMinus = { vm.run.setFrom(run.from - 1) },
                onPlus = { vm.run.setFrom(run.from + 1) },
                minusEnabled = run.from > cfg.lowest,
                plusEnabled = run.from < cfg.highest,
            )
        }
        RowDivider()
        SettingRow("bis") {
            MiniStepper(
                Temperaments.noteName(run.to),
                onMinus = { vm.run.setTo(run.to - 1) },
                onPlus = { vm.run.setTo(run.to + 1) },
                minusEnabled = run.to > cfg.lowest,
                plusEnabled = run.to < cfg.highest,
            )
        }
        RowDivider()
        val idx = STEP_VALUES_MS.indices.minByOrNull { abs(STEP_VALUES_MS[it] - run.stepMs) } ?: 0
        SettingRow("Tondauer") {
            MiniStepper(
                seconds(run.stepMs),
                onMinus = { vm.run.setStepMs(STEP_VALUES_MS[(idx - 1).coerceAtLeast(0)]) },
                onPlus = { vm.run.setStepMs(STEP_VALUES_MS[(idx + 1).coerceAtMost(STEP_VALUES_MS.size - 1)]) },
                minusEnabled = idx > 0,
                plusEnabled = idx < STEP_VALUES_MS.size - 1,
            )
        }
        RowDivider()
        SettingRow("Endlos wiederholen") { AppSwitch(run.loop, vm.run::setLoop) }
    }
}

// ---------------------------------------------------------------- Orgel

@Composable
private fun OrganSettingsCard(vm: TastenhalterViewModel) {
    val cfg by Organ.config.collectAsStateWithLifecycle()
    val span = OrganConfig.MIN_SPAN - 1
    Card {
        SettingRow("Tiefster Ton", subtitle = "Ausgang 1 · MIDI ${cfg.lowest}") {
            MiniStepper(
                Temperaments.noteName(cfg.lowest),
                onMinus = { vm.updateOrganConfig { it.copy(lowest = it.lowest - 1) } },
                onPlus = { vm.updateOrganConfig { it.copy(lowest = it.lowest + 1) } },
                minusEnabled = cfg.lowest > 0,
                plusEnabled = cfg.lowest < cfg.highest - span,
            )
        }
        RowDivider()
        SettingRow("Höchster Ton", subtitle = "Ausgang ${cfg.keyCount} · MIDI ${cfg.highest}") {
            MiniStepper(
                Temperaments.noteName(cfg.highest),
                onMinus = { vm.updateOrganConfig { it.copy(highest = it.highest - 1) } },
                onPlus = { vm.updateOrganConfig { it.copy(highest = it.highest + 1) } },
                minusEnabled = cfg.highest > cfg.lowest + span,
                plusEnabled = cfg.highest < 127,
            )
        }
        RowDivider()
        SettingRow("Gleichzeitige Töne", subtitle = "Höchstens so viele klingen zusammen") {
            MiniStepper(
                "${cfg.maxPolyphony}",
                onMinus = { vm.updateOrganConfig { it.copy(maxPolyphony = it.maxPolyphony - 1) } },
                onPlus = { vm.updateOrganConfig { it.copy(maxPolyphony = it.maxPolyphony + 1) } },
                minusEnabled = cfg.maxPolyphony > 1,
                plusEnabled = cfg.maxPolyphony < cfg.keyCount,
            )
        }
        RowDivider()
        SettingRow("MIDI-Kanal") {
            MiniStepper(
                "${cfg.channel + 1}",
                onMinus = { vm.updateOrganConfig { it.copy(channel = it.channel - 1) } },
                onPlus = { vm.updateOrganConfig { it.copy(channel = it.channel + 1) } },
                minusEnabled = cfg.channel > 0,
                plusEnabled = cfg.channel < 15,
            )
        }
        if (cfg != OrganConfig()) {
            RowDivider()
            SettingRow(
                "Standard wiederherstellen",
                subtitle = "C–g³ (56 Töne), 15 Töne, Kanal 1",
                onClick = { vm.updateOrganConfig { OrganConfig() } },
            )
        }
    }
    Text(
        "Beim Ändern werden alle Töne abgeschaltet.",
        style = MaterialTheme.typography.bodySmall,
        color = Palette.Muted,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

// ---------------------------------------------------------------- Info

@Composable
private fun InfoCard() {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    Card {
        SettingRow("Tastenhalter $version", subtitle = "Quellcode: github.com/casualistcode/Tastenhalter (MIT)")
        RowDivider()
        SettingRow(
            "Stimmen",
            subtitle = "Stimmzirkel und Schwebungsrechnung aus Stimmlicht. Neidhardt-Werte nach Orgelbau Rohlf, " +
                "Stimmfolgen nach Bradley Lehman, Steinway-Zirkel nach Ulrich van Aaken.",
        )
        RowDivider()
        SettingRow(
            "Mitgelieferte Lieder",
            subtitle = "Mutopia Project und Wikimedia Commons – gemeinfrei bzw. CC0. Einzelnachweise in der README.",
        )
    }
    Spacer(Modifier.height(4.dp).widthIn(min = 1.dp))
}
