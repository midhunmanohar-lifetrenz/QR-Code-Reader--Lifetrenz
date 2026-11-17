package com.lifetrenz.qrreader

import android.app.Application
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetrenz.qrreader.data.HistoryStore
import com.lifetrenz.qrreader.data.ScanRecord
import com.lifetrenz.qrreader.data.ScanState
import com.lifetrenz.qrreader.data.SettingsStore
import com.lifetrenz.qrreader.data.Suffix
import com.lifetrenz.qrreader.hid.HidKeyboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val settings = SettingsStore(app)
    val history = HistoryStore(app)
    val hid = HidKeyboard(app)

    private val _lastScan = MutableStateFlow<ScanRecord?>(null)
    val lastScan: StateFlow<ScanRecord?> = _lastScan.asStateFlow()

    /** In single-scan mode the camera pauses after each read until the user taps "Scan next". */
    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val outbox = Channel<ScanRecord>(Channel.UNLIMITED)
    private var lastValue: String? = null
    private var lastSeen = 0L

    private val tone by lazy { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }
    private val vibrator by lazy { app.getSystemService(VibratorManager::class.java).defaultVibrator }

    init {
        // One worker types scans strictly in order.
        viewModelScope.launch(Dispatchers.Default) { for (record in outbox) deliver(record) }
        // When a laptop connects, send scans that were waiting (recent ones only).
        viewModelScope.launch {
            hid.state.map { it.connected?.address }.distinctUntilChanged().filterNotNull().collect {
                val cutoff = System.currentTimeMillis() - AUTO_SEND_WINDOW_MS
                history.flow.value
                    .filter { it.state == ScanState.QUEUED && it.time >= cutoff }
                    .sortedBy { it.time }
                    .forEach { outbox.trySend(it) }
            }
        }
    }

    fun onBarcode(raw: String, format: String) {
        val s = settings.flow.value
        val value = if (s.trim) raw.trim() else raw
        if (value.isEmpty()) return

        val now = SystemClock.elapsedRealtime()
        val repeat = value == lastValue && now - lastSeen < s.dupWindowMs
        // Refresh the timer on every sighting so a code held in front of the camera is sent only once.
        lastValue = value
        lastSeen = now
        if (repeat) return

        val record = ScanRecord(
            id = System.nanoTime(),
            value = value,
            format = format,
            time = System.currentTimeMillis(),
            state = ScanState.QUEUED,
        )
        history.add(record)
        _lastScan.value = record
        _notice.value = null
        feedback(connected = hid.state.value.connected != null)
        if (hid.state.value.connected != null) outbox.trySend(record)
        if (!s.continuous) _paused.value = true
    }

    fun resumeScanning() {
        lastValue = null
        _paused.value = false
    }

    fun resend(record: ScanRecord) {
        if (hid.state.value.connected == null) {
            _notice.value = "Laptop not connected. Connect it first."
        } else {
            outbox.trySend(record)
        }
    }

    fun sendAllQueued() {
        if (hid.state.value.connected == null) {
            _notice.value = "Laptop not connected. Connect it first."
            return
        }
        history.flow.value.filter { it.state == ScanState.QUEUED }.sortedBy { it.time }
            .forEach { outbox.trySend(it) }
    }

    fun sendTest() {
        viewModelScope.launch(Dispatchers.Default) {
            val r = hid.type(TEST_TEXT, Suffix.NONE)
            _notice.value = if (r.ok) "Test text sent. Check the field on the laptop." else "Laptop not connected."
        }
    }

    fun dismissNotice() {
        _notice.value = null
    }

    private suspend fun deliver(record: ScanRecord) {
        val s = settings.flow.value
        val result = hid.type(s.prefix + record.value, s.suffix)
        val state = if (result.ok) ScanState.SENT else ScanState.QUEUED
        history.setState(record.id, state)
        _lastScan.update { if (it?.id == record.id) it.copy(state = state) else it }
        if (result.skipped > 0) {
            _notice.value = "${result.skipped} character(s) could not be typed (not on a standard keyboard)."
        }
    }

    private fun feedback(connected: Boolean) {
        val s = settings.flow.value
        if (s.beep) runCatching { tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120) }
        if (s.vibrate) {
            val effect = if (connected) {
                VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE)
            } else {
                VibrationEffect.createWaveform(longArrayOf(0, 80, 80, 80), -1)
            }
            runCatching { vibrator.vibrate(effect) }
        }
    }

    override fun onCleared() {
        hid.stop()
        runCatching { tone.release() }
    }

    companion object {
        private const val AUTO_SEND_WINDOW_MS = 120_000L
        private const val TEST_TEXT = "143555-ABC_xyz/#@:"
    }
}
