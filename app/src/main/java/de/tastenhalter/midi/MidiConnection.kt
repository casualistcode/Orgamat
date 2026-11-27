package de.tastenhalter.midi

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Verwaltet die Verbindung zu genau einem MIDI-Ausgabegerät (USB-MIDI oder Bluetooth LE MIDI)
 * über die Android-MIDI-API (android.media.midi).
 */
class MidiConnection(context: Context) {

    data class DeviceEntry(
        val id: Int,
        val name: String,
        val type: Int,
        val inputPortNames: List<String>,
        val info: MidiDeviceInfo,
    )

    sealed interface State {
        data object Disconnected : State
        data class Connecting(val name: String) : State
        data class Connected(val deviceId: Int, val name: String, val type: Int, val port: Int) : State
    }

    private val appContext = context.applicationContext
    private val midiManager: MidiManager? =
        if (appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI))
            appContext.getSystemService(MidiManager::class.java) else null

    private val main = Handler(Looper.getMainLooper())

    private val _devices = MutableStateFlow<List<DeviceEntry>>(emptyList())
    val devices: StateFlow<List<DeviceEntry>> = _devices.asStateFlow()

    private val _state = MutableStateFlow<State>(State.Disconnected)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _autoConnectUsb = MutableStateFlow(true)
    val autoConnectUsb: StateFlow<Boolean> = _autoConnectUsb.asStateFlow()

    /** Nach erfolgreichem Verbinden (Main-Thread). */
    var onConnected: (() -> Unit)? = null
    /** Gerät ist unerwartet verschwunden (Main-Thread). */
    var onLost: (() -> Unit)? = null

    private var device: MidiDevice? = null
    @Volatile
    private var port: MidiInputPort? = null
    private var token = 0
    private var started = false

    private val callback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(info: MidiDeviceInfo) {
            refresh()
            maybeAutoConnect(info)
        }

        override fun onDeviceRemoved(info: MidiDeviceInfo) {
            refresh()
            val s = _state.value
            if (s is State.Connected && s.deviceId == info.id) handleLost(s.name)
        }
    }

    fun start() {
        val mm = midiManager
        if (mm == null) {
            _message.value = "Dieses Handy unterstützt kein MIDI (android.software.midi fehlt)."
            return
        }
        if (started) return
        started = true
        if (Build.VERSION.SDK_INT >= 33) {
            mm.registerDeviceCallback(MidiManager.TRANSPORT_MIDI_BYTE_STREAM, appContext.mainExecutor, callback)
        } else {
            @Suppress("DEPRECATION")
            mm.registerDeviceCallback(callback, main)
        }
        refresh()
        if (_autoConnectUsb.value && _state.value == State.Disconnected) {
            _devices.value.firstOrNull { it.type == MidiDeviceInfo.TYPE_USB }?.let { connect(it, 0) }
        }
    }

    fun shutdown() {
        if (started) {
            runCatching { midiManager?.unregisterDeviceCallback(callback) }
            started = false
        }
        token++
        closeCurrent()
        _state.value = State.Disconnected
    }

    fun setAutoConnectUsb(enabled: Boolean) {
        _autoConnectUsb.value = enabled
    }

    fun clearMessage() {
        _message.value = null
    }

    fun refresh() {
        val mm = midiManager ?: return
        val infos: Collection<MidiDeviceInfo> = if (Build.VERSION.SDK_INT >= 33) {
            mm.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM)
        } else {
            @Suppress("DEPRECATION")
            mm.devices.toList()
        }
        _devices.value = infos
            .filter { it.inputPortCount > 0 }
            .map { toEntry(it) }
            .sortedWith(compareBy<DeviceEntry>({ typeOrder(it.type) }, { it.name }))
    }

    fun connect(entry: DeviceEntry, portIndex: Int) {
        val mm = midiManager ?: return
        val cur = _state.value
        if (cur is State.Connected && cur.deviceId == entry.id && cur.port == portIndex) return
        closeCurrent()
        val myToken = ++token
        _state.value = State.Connecting(entry.name)
        _message.value = null
        try {
            mm.openDevice(entry.info, { dev -> onOpened(myToken, dev, portIndex, entry.name) }, main)
        } catch (e: Exception) {
            _state.value = State.Disconnected
            _message.value = "Öffnen fehlgeschlagen: ${e.message}"
            return
        }
        scheduleTimeout(myToken, entry.name, 15_000)
    }

    @SuppressLint("MissingPermission")
    fun connectBluetooth(bt: BluetoothDevice, name: String) {
        val mm = midiManager ?: return
        closeCurrent()
        val myToken = ++token
        _state.value = State.Connecting(name)
        _message.value = null
        try {
            mm.openBluetoothDevice(bt, { dev -> onOpened(myToken, dev, 0, name) }, main)
        } catch (e: SecurityException) {
            _state.value = State.Disconnected
            _message.value = "Keine Bluetooth-Berechtigung („Geräte in der Nähe“) erteilt."
            return
        } catch (e: Exception) {
            _state.value = State.Disconnected
            _message.value = "Bluetooth-Verbindung fehlgeschlagen: ${e.message}"
            return
        }
        scheduleTimeout(myToken, name, 20_000)
    }

    fun disconnect() {
        token++
        closeCurrent()
        _state.value = State.Disconnected
        _message.value = null
    }

    /** Sendet rohe MIDI-Bytes. Thread-sicher. */
    fun send(bytes: ByteArray): Boolean {
        val p = port ?: return false
        return try {
            p.send(bytes, 0, bytes.size)
            true
        } catch (e: Exception) {
            main.post { _message.value = "Sendefehler: ${e.message ?: e.javaClass.simpleName}" }
            false
        }
    }

    private fun onOpened(myToken: Int, dev: MidiDevice?, portIndex: Int, name: String) {
        if (myToken != token) {
            runCatching { dev?.close() }
            return
        }
        if (dev == null) {
            _state.value = State.Disconnected
            _message.value = "„$name“ konnte nicht geöffnet werden."
            return
        }
        val p = dev.openInputPort(portIndex)
        if (p == null) {
            runCatching { dev.close() }
            _state.value = State.Disconnected
            _message.value = "Port ${portIndex + 1} von „$name“ ist belegt (wird evtl. von einer anderen App benutzt)."
            return
        }
        device = dev
        port = p
        _state.value = State.Connected(dev.info.id, name, dev.info.type, portIndex)
        refresh()
        onConnected?.invoke()
    }

    private fun scheduleTimeout(myToken: Int, name: String, ms: Long) {
        main.postDelayed({
            if (token == myToken && _state.value is State.Connecting) {
                token++
                _state.value = State.Disconnected
                _message.value = "Zeitüberschreitung beim Verbinden mit „$name“."
            }
        }, ms)
    }

    private fun handleLost(name: String) {
        token++
        closeCurrent()
        _state.value = State.Disconnected
        _message.value = "Verbindung zu „$name“ wurde getrennt! Falls an der Orgel Töne hängen: " +
            "nach dem Wiederverbinden sendet die App automatisch NOT-AUS."
        onLost?.invoke()
    }

    /** Port/Gerät verzögert schließen, damit zuvor gesendete Note-Offs noch rausgehen. */
    private fun closeCurrent() {
        val p = port
        val d = device
        port = null
        device = null
        if (p != null || d != null) {
            main.postDelayed({
                runCatching { p?.close() }
                runCatching { d?.close() }
            }, 300)
        }
    }

    private fun maybeAutoConnect(info: MidiDeviceInfo) {
        if (!_autoConnectUsb.value || _state.value != State.Disconnected) return
        if (info.type != MidiDeviceInfo.TYPE_USB || info.inputPortCount == 0) return
        connect(toEntry(info), 0)
    }

    private fun toEntry(info: MidiDeviceInfo): DeviceEntry {
        val p = info.properties
        val name = p.getString(MidiDeviceInfo.PROPERTY_NAME)?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(
                p.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER),
                p.getString(MidiDeviceInfo.PROPERTY_PRODUCT),
            ).joinToString(" ").takeIf { it.isNotBlank() }
            ?: "MIDI-Gerät ${info.id}"
        val ports = info.ports
            .filter { it.type == MidiDeviceInfo.PortInfo.TYPE_INPUT }
            .sortedBy { it.portNumber }
            .map { pi -> pi.name?.takeIf { it.isNotBlank() } ?: "Port ${pi.portNumber + 1}" }
        return DeviceEntry(info.id, name, info.type, ports, info)
    }

    private fun typeOrder(type: Int) = when (type) {
        MidiDeviceInfo.TYPE_USB -> 0
        MidiDeviceInfo.TYPE_BLUETOOTH -> 1
        else -> 2
    }

    companion object {
        fun typeLabel(type: Int): String = when (type) {
            MidiDeviceInfo.TYPE_USB -> "USB"
            MidiDeviceInfo.TYPE_BLUETOOTH -> "Bluetooth LE"
            MidiDeviceInfo.TYPE_VIRTUAL -> "Virtuell (App)"
            else -> "Sonstiges"
        }
    }
}
