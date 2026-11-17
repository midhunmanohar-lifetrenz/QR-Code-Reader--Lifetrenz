package com.lifetrenz.qrreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    val settings by vm.settings.flow.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val timeFmt = remember { SimpleDateFormat("dd MMM, HH:mm:ss", Locale.getDefault()) }
    val queued = history.count { it.state == ScanState.QUEUED }
    var confirmClear by remember { mutableStateOf(false) }
    var waitingOnly by rememberSaveable { mutableStateOf(false) }
    val shown = if (waitingOnly) history.filter { it.state == ScanState.QUEUED } else history
    val colors = MaterialTheme.colorScheme

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

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScreenTitle("History")
                TextButton(onClick = { confirmClear = true }, enabled = history.isNotEmpty()) { Text("Clear") }
            }
        }
        item {
            Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !waitingOnly, onClick = { waitingOnly = false }, label = { Text("All · ${history.size}") })
                FilterChip(selected = waitingOnly, onClick = { waitingOnly = true }, label = { Text("Waiting · $queued") })
            }
        }
        if (queued > 0) {
            item {
                Button(onClick = vm::sendAllQueued, modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth().height(52.dp)) {
                    Text("Send $queued waiting scan(s) now", fontSize = 15.sp)
                }
            }
        }
        notice?.let { item { Text(it, color = WarnAmber, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp)) } }
        if (shown.isEmpty()) {
            item {
                Text(
                    if (waitingOnly) "No scans are waiting." else "Scans appear here with their delivery state.",
                    color = colors.onSurfaceVariant,
                )
            }
        }
        itemsIndexed(shown, key = { _, r -> r.id }) { i, r ->
            val top = if (i == 0) 24.dp else 6.dp
            val bottom = if (i == shown.lastIndex) 24.dp else 6.dp
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom))
                    .background(colors.surfaceContainerLowest)
                    .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(r.value, fontFamily = FontFamily.Monospace, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${timeFmt.format(Date(r.time))} · ${r.format}", fontSize = 12.sp, color = colors.onSurfaceVariant)
                }
                StatePill(r.state)
                TextButton(onClick = { vm.resend(r) }) { Text("Resend") }
            }
        }
        item {
            Text(
                if (settings.autoClearDays > 0) "Scans older than ${settings.autoClearDays} day(s) are deleted. Change this in Settings."
                else "Scans are kept until you clear them.",
                fontSize = 13.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
