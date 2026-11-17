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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

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
    private var noticeJob: Job? = null

    /** When each value was last seen by the camera, so two codes in view cannot retrigger each other. */
    private val seen = HashMap<String, Long>()

    /** Scans already waiting in [outbox], so a second tap cannot type them twice. */
    private val inFlight = ConcurrentHashMap.newKeySet<Long>()

    /** Scans whose typing was cut off midway. They are never resent automatically. */
    private val partial = ConcurrentHashMap.newKeySet<Long>()

    private val tone by lazy { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }
    private val vibrator by lazy { app.getSystemService(VibratorManager::class.java).defaultVibrator }

    init {
        history.prune(settings.flow.value.autoClearDays)
        // One worker types scans strictly in order.
        viewModelScope.launch(Dispatchers.Default) { for (record in outbox) deliver(record) }
        // When a laptop connects, send scans that were waiting (recent ones only).
        viewModelScope.launch {
            hid.state.map { it.connected?.address }.distinctUntilChanged().filterNotNull().collect {
                val cutoff = System.currentTimeMillis() - AUTO_SEND_WINDOW_MS
                history.flow.value
                    .filter { it.state == ScanState.QUEUED && it.time >= cutoff && it.id !in partial }
                    .sortedBy { it.time }
                    .forEach(::enqueue)
            }
        }
    }

    private fun enqueue(record: ScanRecord) {
        if (inFlight.add(record.id)) outbox.trySend(record)
    }

    private fun showNotice(message: String) {
        _notice.value = message
        noticeJob?.cancel()
        noticeJob = viewModelScope.launch {
            delay(NOTICE_MS)
            _notice.value = null
        }
    }

    fun onBarcode(raw: String, format: String) {
        if (_paused.value) return
        val s = settings.flow.value
        val value = if (s.trim) raw.trim() else raw
        if (value.isEmpty()) return

        val now = SystemClock.elapsedRealtime()
        val previous = seen[value]
        // Refresh the timer on every sighting so a code held in front of the camera is sent only once.
        seen[value] = now
        if (seen.size > 64) seen.values.removeAll { now - it > 60_000 }
        if (previous != null && now - previous < s.dupWindowMs) return

        val record = ScanRecord(
            id = System.nanoTime(),
            value = value,
            format = format,
            time = System.currentTimeMillis(),
            state = ScanState.QUEUED,
        )
        history.add(record)
        history.prune(s.autoClearDays)
        _lastScan.value = record
        feedback(connected = hid.state.value.connected != null)
        if (hid.state.value.connected != null) enqueue(record)
        if (!s.continuous) _paused.value = true
    }

    fun resumeScanning() {
        seen.clear()
        _paused.value = false
    }

    fun resend(record: ScanRecord) {
        if (hid.state.value.connected == null) {
            showNotice("Laptop not connected. Connect it first.")
        } else {
            enqueue(record)
        }
    }

    fun sendAllQueued() {
        if (hid.state.value.connected == null) {
            showNotice("Laptop not connected. Connect it first.")
            return
        }
        history.flow.value.filter { it.state == ScanState.QUEUED }.sortedBy { it.time }
            .forEach(::enqueue)
    }

    fun sendTest() {
        viewModelScope.launch(Dispatchers.Default) {
            val r = hid.type(TEST_TEXT, Suffix.NONE)
            showNotice(if (r.ok) "Test text sent. Check the field on the laptop." else "Laptop not connected.")
        }
    }

    fun dismissNotice() {
        _notice.value = null
    }

    private suspend fun deliver(record: ScanRecord) {
        val s = settings.flow.value
        val result = try {
            hid.type(s.prefix + record.value, s.suffix)
        } finally {
            inFlight.remove(record.id)
        }
        val state = if (result.ok) ScanState.SENT else ScanState.QUEUED
        history.setState(record.id, state)
        _lastScan.update { if (it?.id == record.id) it.copy(state = state) else it }
        when {
            result.partial -> {
                partial.add(record.id)
                showNotice("Bluetooth dropped while typing ${record.value}. Part of it may be in the field on the laptop. Clear the field, then resend from History.")
            }
            result.ok -> {
                partial.remove(record.id)
                if (result.skipped > 0) {
                    showNotice("${result.skipped} character(s) could not be typed (not on a standard keyboard).")
                }
            }
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
        private const val NOTICE_MS = 8_000L
        private const val TEST_TEXT = "143555-ABC_xyz/#@:"
    }
}
