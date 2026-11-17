package com.lifetrenz.qrreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifetrenz.qrreader.MainViewModel
import com.lifetrenz.qrreader.data.ScanState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(vm: MainViewModel) {
    val history by vm.history.flow.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val timeFmt = SimpleDateFormat("dd MMM, HH:mm:ss", Locale.getDefault())
    val queued = history.count { it.state == ScanState.QUEUED }
    var confirmClear by remember { mutableStateOf(false) }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = {
                Text(
                    if (queued > 0) "This deletes all ${history.size} scans, including $queued that have not been sent yet. It cannot be undone."
                    else "This deletes all ${history.size} scans. It cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.history.clear()
                    confirmClear = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { confirmClear = true }, enabled = history.isNotEmpty()) { Text("Clear") }
        }
        if (queued > 0) {
            TextButton(onClick = vm::sendAllQueued) { Text("Send $queued waiting scan(s) now") }
        }
        notice?.let { Text(it, color = WarnAmber, fontSize = 13.sp) }
        if (history.isEmpty()) {
            Text("Scans appear here with their delivery state.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn {
            items(history, key = { it.id }) { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(r.value, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, maxLines = 2)
                        Text("${timeFmt.format(Date(r.time))} · ${r.format}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StateLabel(r.state)
                    TextButton(onClick = { vm.resend(r) }) { Text("Resend") }
                }
                HorizontalDivider()
            }
        }
    }
}
