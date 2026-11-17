package com.lifetrenz.qrreader.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class ScanState { SENT, QUEUED }

data class ScanRecord(
    val id: Long,
    val value: String,
    val format: String,
    val time: Long,
    val state: ScanState,
)

/** Scan history kept in a small JSON file on the phone. Newest first, capped at [MAX] entries. */
class HistoryStore(context: Context) {
    private val file = File(context.filesDir, "history.json")
    private val _flow = MutableStateFlow(load())
    val flow: StateFlow<List<ScanRecord>> = _flow.asStateFlow()

    private fun load(): List<ScanRecord> = runCatching {
        val arr = JSONArray(file.readText())
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            ScanRecord(
                id = o.getLong("id"),
                value = o.getString("value"),
                format = o.getString("format"),
                time = o.getLong("time"),
                state = ScanState.valueOf(o.getString("state")),
            )
        }
    }.getOrDefault(emptyList())

    private fun save(list: List<ScanRecord>) {
        _flow.value = list
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("value", it.value)
                    .put("format", it.format)
                    .put("time", it.time)
                    .put("state", it.state.name),
            )
        }
        runCatching { file.writeText(arr.toString()) }
    }

    @Synchronized
    fun add(record: ScanRecord) = save((listOf(record) + _flow.value).take(MAX))

    @Synchronized
    fun setState(id: Long, state: ScanState) =
        save(_flow.value.map { if (it.id == id) it.copy(state = state) else it })

    @Synchronized
    fun clear() = save(emptyList())

    companion object {
        private const val MAX = 500
    }
}
