package com.lifetrenz.qrreader.hid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.lifetrenz.qrreader.data.Suffix
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

data class HidState(
    val bluetoothOn: Boolean = true,
    val registered: Boolean = false,
    val connected: BluetoothDevice? = null,
    /** Address of the laptop the phone is currently trying to connect to. */
    val connectingTo: String? = null,
    val capsLock: Boolean = false,
    val problem: String? = null,
    /** Plain-language advice shown after a failed connection attempt. */
    val hint: String? = null,
    /** Newest first. Shown in the Connect screen to help diagnose problems. */
    val log: List<String> = emptyList(),
)

data class TypeResult(val ok: Boolean, val skipped: Int = 0)

/**
 * Makes the phone act as a Bluetooth keyboard (HID device profile) and types text to the paired laptop.
 * Needs the BLUETOOTH_CONNECT permission to be granted before [start] is called.
 */
@SuppressLint("MissingPermission")
class HidKeyboard(private val context: Context) {

    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter
    private val prefs = context.getSharedPreferences("hid", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val typeLock = Mutex()
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var hid: BluetoothHidDevice? = null

    private val _state = MutableStateFlow(HidState())
    val state: StateFlow<HidState> = _state.asStateFlow()

    private fun log(message: String) {
        val line = "${timeFmt.format(Date())}  $message"
        _state.update { it.copy(log = (listOf(line) + it.log).take(40)) }
    }

    private fun nameOf(d: BluetoothDevice): String = runCatching { d.name }.getOrNull() ?: d.address

    private val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            log("Keyboard service available")
            hid = proxy as BluetoothHidDevice
            register()
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            log("Keyboard service stopped")
            hid = null
            _state.update { it.copy(registered = false, connected = null, connectingTo = null) }
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            log(if (registered) "Keyboard registered" else "Keyboard not registered")
            _state.update {
                it.copy(
                    registered = registered,
                    problem = if (registered) null else "Keyboard mode could not start on this phone.",
                )
            }
            if (!registered) return
            val already = hid?.getDevicesMatchingConnectionStates(intArrayOf(BluetoothProfile.STATE_CONNECTED))
                ?.firstOrNull()
            if (already != null) {
                log("Already connected to ${nameOf(already)}")
                _state.update { it.copy(connected = already) }
            } else {
                reconnectLast()
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            val label = when (state) {
                BluetoothProfile.STATE_CONNECTED -> "connected"
                BluetoothProfile.STATE_CONNECTING -> "connecting"
                BluetoothProfile.STATE_DISCONNECTING -> "disconnecting"
                else -> "disconnected"
            }
            log("${nameOf(device)}: $label")
            when (state) {
                BluetoothProfile.STATE_CONNECTING ->
                    _state.update { it.copy(connectingTo = device.address) }

                BluetoothProfile.STATE_CONNECTED -> {
                    prefs.edit().putString(LAST_HOST, device.address).apply()
                    _state.update { it.copy(connected = device, connectingTo = null, hint = null) }
                }

                BluetoothProfile.STATE_DISCONNECTED -> _state.update { s ->
                    val failedAttempt = s.connectingTo == device.address
                    s.copy(
                        connected = if (s.connected?.address == device.address) null else s.connected,
                        connectingTo = null,
                        hint = if (failedAttempt) CONNECT_HINT else s.hint,
                    )
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            hid?.replyReport(device, type, id, ByteArray(8))
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            readLeds(data)
            hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
        }

        override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {
            readLeds(data)
        }
    }

    private fun readLeds(data: ByteArray) {
        val leds = data.lastOrNull()?.toInt() ?: return
        _state.update { it.copy(capsLock = (leds and 0x02) != 0) }
    }

    /** Safe to call repeatedly (for example from onResume). */
    fun start() {
        val a = adapter
        if (a == null) {
            _state.update { it.copy(problem = "This phone has no Bluetooth.") }
            return
        }
        _state.update { it.copy(bluetoothOn = a.isEnabled) }
        if (!a.isEnabled) return
        if (_state.value.registered) return
        if (hid != null) {
            register()
        } else if (!a.getProfileProxy(context, listener, BluetoothProfile.HID_DEVICE)) {
            log("Keyboard service refused by the phone")
            _state.update { it.copy(problem = "Keyboard mode is not available on this phone.") }
        }
    }

    fun stop() {
        runCatching { hid?.unregisterApp() }
        hid?.let { runCatching { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) } }
        hid = null
        _state.update { it.copy(registered = false, connected = null, connectingTo = null) }
    }

    private fun register() {
        val h = hid ?: return
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "Lifetrenz QR Reader",
            "Barcode scanner keyboard",
            "Lifetrenz",
            BluetoothHidDevice.SUBCLASS1_KEYBOARD,
            DESCRIPTOR,
        )
        if (!h.registerApp(sdp, null, null, executor, callback)) {
            log("registerApp refused")
            _state.update { it.copy(problem = "Keyboard mode could not start on this phone.") }
        }
    }

    /** Paired devices, laptops and computers first. */
    fun pairedDevices(): List<BluetoothDevice> =
        adapter?.bondedDevices.orEmpty().sortedBy {
            if (it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.COMPUTER) 0 else 1
        }

    /** After the keyboard registers, try the laptop that was connected last time. */
    private fun reconnectLast() {
        val address = prefs.getString(LAST_HOST, null) ?: return
        val device = pairedDevices().firstOrNull { it.address == address } ?: return
        log("Reconnecting to ${nameOf(device)}")
        connect(device)
    }

    /**
     * Asks Windows to connect. Many laptops refuse a connection started from the phone, so a timeout
     * shows advice to click Connect on the laptop instead. A connection made from the laptop is detected automatically.
     */
    fun connect(device: BluetoothDevice) {
        val h = hid ?: return
        _state.update { it.copy(connectingTo = device.address, hint = null) }
        val started = h.connect(device)
        log("Connect to ${nameOf(device)}: ${if (started) "request sent" else "refused by phone"}")
        if (!started) {
            _state.update { it.copy(connectingTo = null, hint = CONNECT_HINT) }
            return
        }
        main.postDelayed({
            _state.update { s ->
                if (s.connected?.address != device.address && s.connectingTo == device.address) {
                    log("No answer from ${nameOf(device)} after ${CONNECT_TIMEOUT_MS / 1000} s")
                    s.copy(connectingTo = null, hint = CONNECT_HINT)
                } else s
            }
        }, CONNECT_TIMEOUT_MS)
    }

    fun disconnect() {
        val d = _state.value.connected ?: return
        hid?.disconnect(d)
    }

    /** Types [text] then the [suffix] key. Returns not-ok if no laptop is connected or a report fails. */
    suspend fun type(text: String, suffix: Suffix): TypeResult {
        val h = hid ?: return TypeResult(false)
        val device = _state.value.connected ?: return TypeResult(false)
        var skipped = 0
        typeLock.withLock {
            for (ch in text) {
                val key = KeyMap.forChar(ch)
                if (key == null) {
                    skipped++
                    continue
                }
                var mod = key.modifier
                // Caps Lock on the laptop inverts letter case, so flip Shift to compensate.
                if (key.isLetter && _state.value.capsLock) mod = mod xor KeyMap.SHIFT
                if (!press(h, device, mod, key.usage)) return TypeResult(false, skipped)
            }
            when (suffix) {
                Suffix.ENTER -> if (!press(h, device, 0, KeyMap.ENTER)) return TypeResult(false, skipped)
                Suffix.TAB -> if (!press(h, device, 0, KeyMap.TAB)) return TypeResult(false, skipped)
                Suffix.NONE -> Unit
            }
        }
        return TypeResult(true, skipped)
    }

    private suspend fun press(h: BluetoothHidDevice, d: BluetoothDevice, modifier: Int, usage: Int): Boolean {
        val down = byteArrayOf(modifier.toByte(), 0, usage.toByte(), 0, 0, 0, 0, 0)
        val ok = h.sendReport(d, REPORT_ID, down)
        delay(KEY_GAP_MS)
        h.sendReport(d, REPORT_ID, ByteArray(8))
        delay(KEY_GAP_MS)
        return ok
    }

    companion object {
        private const val REPORT_ID = 1
        private const val KEY_GAP_MS = 6L
        private const val CONNECT_TIMEOUT_MS = 8_000L
        private const val LAST_HOST = "lastHost"
        const val CONNECT_HINT =
            "The laptop did not accept a connection started from the phone. This is normal with Windows. " +
                "On the laptop open Settings › Bluetooth & devices, find this phone and click Connect. " +
                "The app detects it by itself."

        /** Standard keyboard: 1 modifier byte, 1 reserved, 6 key slots in; 5 LED bits out. */
        private val DESCRIPTOR = intArrayOf(
            0x05, 0x01, 0x09, 0x06, 0xA1, 0x01, 0x85, REPORT_ID,
            0x05, 0x07, 0x19, 0xE0, 0x29, 0xE7, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
            0x95, 0x01, 0x75, 0x08, 0x81, 0x01,
            0x95, 0x05, 0x75, 0x01, 0x05, 0x08, 0x19, 0x01, 0x29, 0x05, 0x91, 0x02,
            0x95, 0x01, 0x75, 0x03, 0x91, 0x01,
            0x95, 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65, 0x05, 0x07, 0x19, 0x00, 0x29, 0x65, 0x81, 0x00,
            0xC0,
        ).map { it.toByte() }.toByteArray()
    }
}
