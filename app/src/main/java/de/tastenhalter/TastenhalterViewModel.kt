package de.tastenhalter

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.tastenhalter.control.KeyboardController
import de.tastenhalter.control.RunController
import de.tastenhalter.control.TemperamentController
import de.tastenhalter.core.AppPrefs
import de.tastenhalter.core.Organ
import de.tastenhalter.core.OrganConfig
import de.tastenhalter.core.OrganOutput
import de.tastenhalter.core.OrganSettings
import de.tastenhalter.midi.BleMidiScanner
import de.tastenhalter.midi.BundledSong
import de.tastenhalter.midi.BundledSongs
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
    TUNING("Stimmen"),
    KEYBOARD("Klaviatur"),
    PLAYER("Lieder"),
}

/** Abschnitte im Einstellungsmenü (zum direkten Hinspringen). */
enum class SettingsSection { CONNECTION, TUNING, KEYBOARD, ORGAN, INFO }

class TastenhalterViewModel(app: Application) : AndroidViewModel(app) {

    // Zuerst die gespeicherten Orgel-Einstellungen laden – alles Weitere baut darauf auf.
    private val organSettings = OrganSettings(app).also { Organ.setConfig(it.load()) }
    val prefs = AppPrefs(app)

    val connection = MidiConnection(app)
    val output = OrganOutput(connection::send)
    val keyboard = KeyboardController(output)
    val temperament = TemperamentController(output, prefs)
    val run = RunController(output, viewModelScope, prefs)
    val player = MidiPlayer(output, viewModelScope)
    val ble = BleMidiScanner(app)

    /** Mit der App ausgelieferte Lieder (aus assets/lieder). */
    val bundledSongs: List<BundledSong> = runCatching {
        app.assets.open("${BundledSongs.DIR}/${BundledSongs.INDEX}").bufferedReader(Charsets.UTF_8).use {
            BundledSongs.parseIndex(it.readText())
        }
    }.getOrDefault(emptyList())

    private val _tab = MutableStateFlow(Tab.TUNING)
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    /** Geöffnetes Einstellungsmenü (null = geschlossen) und Abschnitt, zu dem gesprungen wird. */
    private val _settings = MutableStateFlow<SettingsSection?>(null)
    val settings: StateFlow<SettingsSection?> = _settings.asStateFlow()

    private val _keyboardFirstWhite = MutableStateFlow(Organ.current.whiteIndex(48).coerceAtLeast(0))
    val keyboardFirstWhite: StateFlow<Int> = _keyboardFirstWhite.asStateFlow()

    /** Anzahl sichtbarer weißer Tasten; null = automatisch nach Bildschirmbreite. */
    private val _keyboardKeys = MutableStateFlow(prefs.keyboardKeys.takeIf { it > 0 })
    val keyboardKeys: StateFlow<Int?> = _keyboardKeys.asStateFlow()

    /** Bildschirm wach halten: beim Stimmen, während Wiedergabe und Durchlauf und solange ein Ton klingt. */
    val keepScreenOn: StateFlow<Boolean> = combine(player.state, run.state, output.active, _tab, _settings) { p, r, a, t, s ->
        p.status == MidiPlayer.Status.PLAYING || r.running || a.isNotEmpty() || (t == Tab.TUNING && s == null)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        connection.setAutoConnectUsb(prefs.autoConnectUsb)
        // Nach jedem (Wieder-)Verbinden zuerst alle Töne aus – räumt evtl. hängende Töne ab.
        connection.onConnected = { output.allNotesOff() }
        connection.onLost = { output.allNotesOff() }
        connection.start()
    }

    fun selectTab(tab: Tab) {
        _tab.value = tab
    }

    fun openSettings(section: SettingsSection = SettingsSection.CONNECTION) {
        _settings.value = section
    }

    fun closeSettings() {
        _settings.value = null
        ble.stop()
    }

    fun setAutoConnectUsb(enabled: Boolean) {
        prefs.autoConnectUsb = enabled
        connection.setAutoConnectUsb(enabled)
    }

    fun setKeyboardFirstWhite(index: Int) {
        _keyboardFirstWhite.value = index.coerceIn(0, Organ.current.whiteKeys.size - 1)
    }

    fun setKeyboardKeys(count: Int?) {
        val v = count?.coerceIn(7, Organ.current.whiteKeys.size)
        _keyboardKeys.value = v
        prefs.keyboardKeys = v ?: 0
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
        organSettings.save(next)
        output.allNotesOff()
        run.onConfigChanged()
        player.reconfigure()
        _keyboardKeys.value?.let { setKeyboardKeys(it) }
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

    /** Kurzer Prüfton a¹ (MIDI 69). */
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
        loadSong(subtitle = null) {
            val cr = getApplication<Application>().contentResolver
            val name = displayName(uri)
            val bytes = cr.openInputStream(uri)?.use { input ->
                val data = input.readBytes()
                if (data.size > 16 * 1024 * 1024) throw MidiParseException("Datei ist zu groß für eine MIDI-Datei.")
                data
            } ?: throw MidiParseException("Datei konnte nicht geöffnet werden.")
            name to bytes
        }
    }

    fun loadBundled(song: BundledSong) {
        loadSong(subtitle = song.composer) {
            val bytes = getApplication<Application>().assets.open("${BundledSongs.DIR}/${song.file}").use { it.readBytes() }
            song.title to bytes
        }
    }

    /** Liest und zerlegt eine MIDI-Datei im Hintergrund und übergibt sie an den Player. */
    private fun loadSong(subtitle: String?, read: () -> Pair<String, ByteArray>) {
        _tab.value = Tab.PLAYER
        player.setLoading()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val (name, bytes) = read()
                    name to MidiFileParser.parse(bytes)
                }
            }
            result.fold(
                onSuccess = { (name, song) -> player.load(name, song, subtitle) },
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
        run.stop()
        connection.shutdown()
        player.release()
    }
}
