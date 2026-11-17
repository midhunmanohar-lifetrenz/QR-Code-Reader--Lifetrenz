package com.lifetrenz.qrreader.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifetrenz.qrreader.MainViewModel

@Composable
fun ConnectScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val hid by vm.hid.state.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    // Re-read the paired list whenever the connection state changes.
    val allPaired = remember(hid.connected, hid.registered, hid.bluetoothOn) { vm.hid.pairedDevices() }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val computers = allPaired.filter { vm.hid.isComputer(it) || it.address == hid.connected?.address }
    val paired = if (showAll) allPaired else computers
    val hidden = allPaired.size - computers.size
    var visibleProblem by remember { mutableStateOf<String?>(null) }

    val discoverable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        visibleProblem = if (result.resultCode > 0) null
        else "The phone was not made visible. Use \"Open Bluetooth settings\" instead: the phone is visible while that screen is open."
    }
    fun requestDiscoverable() {
        runCatching {
            discoverable.launch(
                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                    .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120),
            )
        }.onFailure { visibleProblem = "This phone would not open the visibility prompt. Use \"Open Bluetooth settings\" instead." }
    }
    val advertisePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) requestDiscoverable()
        else visibleProblem = "Nearby devices permission is needed to make the phone visible."
    }

    LazyColumn(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Connect", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }

        item {
            Card(colors = appCardColors()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusRow("Bluetooth", if (hid.bluetoothOn) "On" else "Off", hid.bluetoothOn)
                    StatusRow("Keyboard mode", if (hid.registered) "Ready" else "Not ready", hid.registered)
                    StatusRow("Laptop", hid.connected?.let { deviceLabel(it) } ?: "Not connected", hid.connected != null)
                    hid.problem?.let { Text(it, color = BadRed, fontSize = 13.sp) }
                    hid.hint?.let { Text(it, color = WarnAmber, fontSize = 13.sp) }
                    if (!hid.bluetoothOn) {
                        Button(onClick = {
                            context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }) { Text("Turn on Bluetooth") }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = vm::sendTest,
                    enabled = hid.connected != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Send test text") }
                notice?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp) }
                Text(
                    "Click a text field on the laptop first. The test text 143555-ABC_xyz/#@: should appear there.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { Text("Paired devices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }

        if (paired.isEmpty()) {
            item { Text("No paired laptops yet. Follow the steps below.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(paired, key = { it.address }) { d ->
            val isConnected = hid.connected?.address == d.address
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(deviceLabel(d), fontWeight = FontWeight.Medium)
                    Text(if (isConnected) "Connected" else "Not connected", fontSize = 12.sp, color = if (isConnected) OkGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isConnected) {
                    TextButton(onClick = { vm.hid.disconnect() }) { Text("Disconnect") }
                } else {
                    val busy = hid.connectingTo == d.address
                    OutlinedButton(onClick = { vm.hid.connect(d) }, enabled = hid.registered && !busy) {
                        Text(if (busy) "Connecting…" else "Connect")
                    }
                }
            }
            HorizontalDivider()
        }

        if (hidden > 0 || showAll) {
            item {
                TextButton(onClick = { showAll = !showAll }) {
                    Text(if (showAll) "Show laptops only" else "Show $hidden other paired device(s)")
                }
            }
        }

        item {
            Card(colors = appCardColors()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pair a new laptop (Windows 11)", fontWeight = FontWeight.SemiBold)
                    Text("1. Tap \"Make phone visible\" below.", fontSize = 13.sp)
                    Text("2. On the laptop open Settings › Bluetooth & devices › Add device › Bluetooth.", fontSize = 13.sp)
                    Text("3. Choose \"Lifetrenz QR Reader\" and confirm the code on both screens.", fontSize = 13.sp)
                    Text("4. Click a text field on the laptop, then tap \"Send test text\".", fontSize = 13.sp)
                    Text("Tip: if the Connect button does not work, click Connect next to this phone in the laptop's Bluetooth list. The app detects it automatically.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = {
                            visibleProblem = null
                            advertisePermission.launch(Manifest.permission.BLUETOOTH_ADVERTISE)
                        },
                        enabled = hid.bluetoothOn,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Make phone visible (2 minutes)") }
                    visibleProblem?.let { Text(it, color = WarnAmber, fontSize = 13.sp) }
                    OutlinedButton(
                        onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Open Bluetooth settings") }
                }
            }
        }
        item { Text("Connection log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (hid.log.isEmpty()) Text("No events yet.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                hid.log.take(12).forEach {
                    Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Text("", modifier = Modifier.padding(8.dp)) }
    }
}

@Composable
private fun StatusRow(label: String, value: String, good: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, color = if (good) OkGreen else WarnAmber, fontWeight = FontWeight.Medium)
    }
}
