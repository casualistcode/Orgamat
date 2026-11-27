package de.tastenhalter

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.tastenhalter.control.KeyboardController
import de.tastenhalter.control.TuningController
import de.tastenhalter.core.Organ
import de.tastenhalter.core.OrganConfig
import de.tastenhalter.core.OrganOutput
import de.tastenhalter.core.OrganSettings
import de.tastenhalter.midi.BleMidiScanner
import de.tastenhalter.midi.MidiConnection
import de.tastenhalter.midi.MidiFileParser
import de.tastenhalter.midi.MidiParseException
import de.tastenhalter.player.MidiPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tab(val label: String) {
    CONNECTION("Verbindung"),
    KEYBOARD("Klaviatur"),
    TUNING("Stimmen"),
    PLAYER("Lieder"),
}

class TastenhalterViewModel(app: Application) : AndroidViewModel(app) {

    // Zuerst die gespeicherten Orgel-Einstellungen laden – alles Weitere baut darauf auf.
    private val settings = OrganSettings(app).also { Organ.setConfig(it.load()) }

    val connection = MidiConnection(app)
    val output = OrganOutput(connection::send)
    val keyboard = KeyboardController(output)
    val tuning = TuningController(output, viewModelScope)
    val player = MidiPlayer(output, viewModelScope)
    val ble = BleMidiScanner(app)

    private val _tab = MutableStateFlow(Tab.CONNECTION)
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    private val _keyboardFirstWhite = MutableStateFlow(Organ.current.whiteIndex(48).coerceAtLeast(0))
    val keyboardFirstWhite: StateFlow<Int> = _keyboardFirstWhite.asStateFlow()

    /** Anzahl sichtbarer weißer Tasten; null = automatisch nach Bildschirmbreite. */
    private val _keyboardKeys = MutableStateFlow<Int?>(null)
    val keyboardKeys: StateFlow<Int?> = _keyboardKeys.asStateFlow()

    /** Bildschirm wach halten: während der Wiedergabe, des Durchlaufs und solange ein Ton klingt. */
    val keepScreenOn: StateFlow<Boolean> = combine(player.state, tuning.state, output.active) { p, t, a ->
        p.status == MidiPlayer.Status.PLAYING || t.running || a.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // Nach jedem (Wieder-)Verbinden zuerst alle Töne aus – räumt evtl. hängende Töne ab.
        connection.onConnected = { output.allNotesOff() }
        connection.onLost = { output.allNotesOff() }
        connection.start()
    }

    fun selectTab(tab: Tab) {
        _tab.value = tab
    }

    fun setKeyboardFirstWhite(index: Int) {
        _keyboardFirstWhite.value = index.coerceIn(0, Organ.current.whiteKeys.size - 1)
    }

    fun setKeyboardKeys(count: Int?) {
        _keyboardKeys.value = count?.coerceIn(7, Organ.current.whiteKeys.size)
    }

    /**
     * Orgel-Einstellungen ändern. Vorher NOT-AUS mit den alten Werten (Umfang/Kanal),
     * danach noch einmal mit den neuen – so kann beim Umstellen nichts hängen bleiben.
     */
    fun updateOrganConfig(transform: (OrganConfig) -> OrganConfig) {
        val next = transform(Organ.current).sanitized()
        if (next == Organ.current) return
        output.allNotesOff()
        Organ.setConfig(next)
        settings.save(next)
        output.allNotesOff()
        tuning.onConfigChanged()
        player.reconfigure()
        _keyboardKeys.value = _keyboardKeys.value?.coerceIn(7, next.whiteKeys.size)
    }

    fun emergencyStop() {
        output.allNotesOff()
    }

    /** App verlässt den Vordergrund (Home, Bildschirm aus, anderer Dialog …). */
    fun onAppLeft() {
        output.allNotesOff()
        ble.stop()
    }

    fun connect(entry: MidiConnection.DeviceEntry, port: Int) {
        if (connection.state.value is MidiConnection.State.Connected) output.allNotesOff()
        connection.connect(entry, port)
    }

    fun connectBle(found: BleMidiScanner.Found) {
        ble.stop()
        if (connection.state.value is MidiConnection.State.Connected) output.allNotesOff()
        connection.connectBluetooth(found.device, found.name)
    }

    fun disconnect() {
        output.allNotesOff()
        connection.disconnect()
    }

    /** Kurzer Prüfton a'. */
    fun testTone() {
        viewModelScope.launch {
            val e = output.epoch
            if (output.noteOn(69, e)) {
                delay(700)
                output.noteOff(69, e)
            }
        }
    }

    fun loadMidi(uri: Uri) {
        _tab.value = Tab.PLAYER
        player.setLoading()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val cr = getApplication<Application>().contentResolver
                    val name = displayName(uri)
                    val bytes = cr.openInputStream(uri)?.use { input ->
                        val data = input.readBytes()
                        if (data.size > 16 * 1024 * 1024) throw MidiParseException("Datei ist zu groß für eine MIDI-Datei.")
                        data
                    } ?: throw MidiParseException("Datei konnte nicht geöffnet werden.")
                    name to MidiFileParser.parse(bytes)
                }
            }
            result.fold(
                onSuccess = { (name, song) -> player.load(name, song) },
                onFailure = { e ->
                    val msg = if (e is MidiParseException) e.message else "Fehler beim Lesen: ${e.message}"
                    player.setError(msg ?: "Unbekannter Fehler")
                },
            )
        }
    }

    private fun displayName(uri: Uri): String {
        val cr = getApplication<Application>().contentResolver
        runCatching {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) c.getString(i)?.let { return it }
                }
            }
        }
        return uri.lastPathSegment ?: "MIDI-Datei"
    }

    override fun onCleared() {
        output.allNotesOff()
        ble.stop()
        tuning.stopRun()
        connection.shutdown()
        player.release()
    }
}
