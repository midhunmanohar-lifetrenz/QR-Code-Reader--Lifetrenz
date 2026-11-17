package com.lifetrenz.qrreader.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    var showPairing by rememberSaveable { mutableStateOf(false) }
    var showLog by rememberSaveable { mutableStateOf(false) }

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

    val colors = MaterialTheme.colorScheme
    val connected = hid.connected

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenTitle("Connect") }

        item {
            if (connected != null) {
                Surface(shape = RoundedCornerShape(28.dp), color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(48.dp).clip(CircleShape).background(colors.primary), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = colors.onPrimary)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(deviceLabel(connected), fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Connected · keyboard ready", fontSize = 13.sp)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = vm::sendTest, modifier = Modifier.weight(1f).height(48.dp)) { Text("Send test text") }
                            OutlinedButton(onClick = { vm.hid.disconnect() }, modifier = Modifier.height(48.dp)) { Text("Disconnect") }
                        }
                        Text(
                            notice ?: "Click a text field on the laptop first. The test text 143555-ABC_xyz/#@: should appear there.",
                            fontSize = 13.sp,
                        )
                    }
                }
            } else {
                Surface(shape = RoundedCornerShape(28.dp), color = colors.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(48.dp).clip(CircleShape).background(colors.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = colors.onSurfaceVariant)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("No laptop connected", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    when {
                                        !hid.bluetoothOn -> "Bluetooth is off."
                                        !hid.registered -> "Keyboard mode is starting."
                                        else -> "Keyboard ready. Connect a laptop below."
                                    },
                                    fontSize = 13.sp,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                        hid.problem?.let { Text(it, color = BadRed, fontSize = 13.sp) }
                        hid.hint?.let { Text(it, color = WarnAmber, fontSize = 13.sp) }
                        if (!hid.bluetoothOn) {
                            Button(
                                onClick = {
                                    context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                },
                                modifier = Modifier.height(48.dp),
                            ) { Text("Turn on Bluetooth") }
                        }
                    }
                }
            }
        }

        item { SectionLabel("Paired laptops", Modifier.padding(top = 6.dp)) }

        if (paired.isEmpty()) {
            item {
                Text("No paired laptops yet. Tap \"Pair a new laptop\" below.", fontSize = 13.sp, color = colors.onSurfaceVariant)
            }
        } else {
            item {
                Column(Modifier.clip(RoundedCornerShape(24.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    paired.forEach { d ->
                        val isConnected = connected?.address == d.address
                        val busy = hid.connectingTo == d.address
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(colors.surfaceContainerLowest)
                                .heightIn(min = 64.dp)
                                .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(if (isConnected) OkGreen else colors.outlineVariant))
                            Text(deviceLabel(d), fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (isConnected) {
                                Text("Connected", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = OkGreen, modifier = Modifier.padding(end = 8.dp))
                            } else {
                                FilledTonalButton(onClick = { vm.hid.connect(d) }, enabled = hid.registered && !busy) {
                                    Text(if (busy) "Connecting…" else "Connect")
                                }
                            }
                        }
                    }
                }
            }
        }

        if (hidden > 0 || showAll) {
            item {
                TextButton(onClick = { showAll = !showAll }) {
                    Text(if (showAll) "Show laptops only" else "Show $hidden other paired device(s)")
                }
            }
        }

        item {
            FilledTonalButton(
                onClick = { showPairing = !showPairing },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (showPairing) "Hide pairing steps" else "Pair a new laptop", fontSize = 15.sp)
            }
        }

        if (showPairing || paired.isEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainerLowest) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Pair a new laptop (Windows 11)", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                        Text("1. Tap \"Make phone visible\" below.", fontSize = 14.sp)
                        Text("2. On the laptop open Settings › Bluetooth & devices › Add device › Bluetooth.", fontSize = 14.sp)
                        Text("3. Choose \"Lifetrenz QR Reader\" and confirm the code on both screens.", fontSize = 14.sp)
                        Text("4. Click a text field on the laptop, then tap \"Send test text\".", fontSize = 14.sp)
                        Button(
                            onClick = {
                                visibleProblem = null
                                advertisePermission.launch(Manifest.permission.BLUETOOTH_ADVERTISE)
                            },
                            enabled = hid.bluetoothOn,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) { Text("Make phone visible (2 minutes)") }
                        visibleProblem?.let { Text(it, color = WarnAmber, fontSize = 13.sp) }
                        OutlinedButton(
                            onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) { Text("Open Bluetooth settings") }
                    }
                }
            }
        }

        item {
            Text(
                "If Connect does not work, click Connect next to this phone in the laptop's Bluetooth list. The app picks it up by itself.",
                fontSize = 13.sp,
                color = colors.onSurfaceVariant,
            )
        }

        item {
            TextButton(onClick = { showLog = !showLog }) {
                Text(if (showLog) "Hide connection log" else "Show connection log")
            }
        }
        if (showLog) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (hid.log.isEmpty()) Text("No events yet.", fontSize = 12.sp, color = colors.onSurfaceVariant)
                    hid.log.take(12).forEach {
                        Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
