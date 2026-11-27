package de.tastenhalter.midi

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Sucht Bluetooth-LE-MIDI-Geräte (Dienst-UUID 03B80E5A-…, oder "MIDI" im Namen). */
class BleMidiScanner(context: Context) {

    data class Found(
        val address: String,
        val name: String,
        val rssi: Int,
        val advertisesMidi: Boolean,
        val device: BluetoothDevice,
    )

    companion object {
        val MIDI_SERVICE: ParcelUuid = ParcelUuid.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700")
        private const val SCAN_MS = 12_000L

        fun requiredPermissions(): List<String> =
            if (Build.VERSION.SDK_INT >= 31) {
                listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                listOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
    }

    private val appContext = context.applicationContext
    private val manager: BluetoothManager? = appContext.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = manager?.adapter
    private val main = Handler(Looper.getMainLooper())
    private val results = LinkedHashMap<String, Found>()

    private val _found = MutableStateFlow<List<Found>>(emptyList())
    val found: StateFlow<List<Found>> = _found.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val stopRunnable = Runnable { stop() }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            main.post { handle(result) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            main.post { results.forEach { handle(it) } }
        }

        override fun onScanFailed(errorCode: Int) {
            main.post {
                _error.value = "BLE-Suche fehlgeschlagen (Fehlercode $errorCode). Bluetooth aus- und wieder einschalten."
                _scanning.value = false
            }
        }
    }

    fun hasAdapter(): Boolean = adapter != null
    fun isBluetoothOn(): Boolean = adapter?.isEnabled == true
    fun isLocationOn(): Boolean =
        appContext.getSystemService(LocationManager::class.java)?.isLocationEnabled ?: true

    fun setError(message: String?) {
        _error.value = message
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (_scanning.value) return
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            _error.value = "Bluetooth ist nicht verfügbar oder ausgeschaltet."
            return
        }
        results.clear()
        _found.value = emptyList()
        _error.value = null
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            scanner.startScan(null, settings, callback)
            _scanning.value = true
            main.postDelayed(stopRunnable, SCAN_MS)
        } catch (e: SecurityException) {
            _error.value = "Keine Berechtigung für die Bluetooth-Suche."
        } catch (e: Exception) {
            _error.value = "BLE-Suche konnte nicht starten: ${e.message}"
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        main.removeCallbacks(stopRunnable)
        if (!_scanning.value) return
        runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
        _scanning.value = false
    }

    @SuppressLint("MissingPermission")
    private fun handle(r: ScanResult) {
        val dev = r.device ?: return
        val address = dev.address ?: return
        val previous = results[address]
        val record = r.scanRecord
        val isMidi = record?.serviceUuids?.contains(MIDI_SERVICE) == true || previous?.advertisesMidi == true
        val advName = record?.deviceName?.takeIf { it.isNotBlank() }
            ?: runCatching { dev.name }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: previous?.name
        if (advName == null && !isMidi) return
        results[address] = Found(address, advName ?: "Unbenanntes MIDI-Gerät", r.rssi, isMidi, dev)
        _found.value = results.values.sortedWith(
            compareByDescending<Found> { it.advertisesMidi }
                .thenByDescending { it.name.contains("midi", ignoreCase = true) }
                .thenByDescending { it.rssi }
        )
    }
}
