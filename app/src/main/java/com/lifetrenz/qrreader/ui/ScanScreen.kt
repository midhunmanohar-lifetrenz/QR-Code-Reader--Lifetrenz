package com.lifetrenz.qrreader.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifetrenz.qrreader.MainViewModel
import com.lifetrenz.qrreader.data.ScanState

@SuppressLint("MissingPermission")
fun deviceLabel(d: BluetoothDevice): String = runCatching { d.name }.getOrNull() ?: d.address

@Composable
fun StatusChip(connected: BluetoothDevice?) {
    val color = if (connected != null) OkGreen else BadRed
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.padding(0.dp).clip(RoundedCornerShape(50)).background(color).padding(4.dp))
            Text(
                if (connected != null) "Bluetooth · ${deviceLabel(connected)}" else "Laptop not connected",
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
fun ScanScreen(vm: MainViewModel) {
    val settings by vm.settings.flow.collectAsStateWithLifecycle()
    val hid by vm.hid.state.collectAsStateWithLifecycle()
    val last by vm.lastScan.collectAsStateWithLifecycle()
    val paused by vm.paused.collectAsStateWithLifecycle()
    val history by vm.history.flow.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    var torch by remember { mutableStateOf(false) }

    val view = LocalView.current
    DisposableEffect(settings.keepAwake) {
        view.keepScreenOn = settings.keepAwake
        onDispose { view.keepScreenOn = false }
    }

    val queued = history.count { it.state == ScanState.QUEUED }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Lifetrenz QR Reader", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                StatusChip(hid.connected)
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.Black)) {
            CameraPreview(Modifier.fillMaxSize(), torchOn = torch, active = !paused, onBarcode = vm::onBarcode)
            ScanOverlay(Modifier.fillMaxSize(), showLaser = !paused)
            IconButton(onClick = { torch = !torch }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(if (torch) Icons.Filled.FlashOn else Icons.Filled.FlashOff, "Torch", tint = Color.White)
            }
            Text(
                if (paused) "Scan paused" else "Centre the barcode in the frame",
                color = Color(0xCCFFFFFF),
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            )
            if (paused) {
                Button(onClick = vm::resumeScanning, modifier = Modifier.align(Alignment.Center)) { Text("Scan next") }
            }
        }

        Card(colors = appCardColors()) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("LAST SCAN", fontSize = 11.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    last?.let { StateLabel(it.state) }
                }
                Text(
                    last?.value ?: "Nothing scanned yet",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                )
                last?.let { Text(it.format, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }

        notice?.let {
            Text(it, color = WarnAmber, fontSize = 13.sp)
        }
        if (hid.connected == null) {
            Text(
                if (queued > 0) "Scans waiting: $queued. They are sent when the laptop connects."
                else "Connect a laptop in the Connect tab. Scans are kept until then.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        } else {
            Text(
                "Sent: ${history.count { it.state == ScanState.SENT }} · Waiting: $queued",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
fun StateLabel(state: ScanState) {
    val (text, color) = if (state == ScanState.SENT) "Sent" to OkGreen else "Waiting" to WarnAmber
    Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun ScanOverlay(modifier: Modifier, showLaser: Boolean) {
    val transition = rememberInfiniteTransition(label = "laser")
    val t by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "laserY",
    )
    Canvas(modifier) {
        val w = size.width * 0.74f
        val h = size.height * 0.42f
        val left = (size.width - w) / 2
        val top = (size.height - h) / 2
        val len = 28.dp.toPx()
        val sw = 4.dp.toPx()
        val c = Color.White
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(c, Offset(x, y), Offset(x + dx * len, y), sw, StrokeCap.Round)
            drawLine(c, Offset(x, y), Offset(x, y + dy * len), sw, StrokeCap.Round)
        }
        corner(left, top, 1f, 1f)
        corner(left + w, top, -1f, 1f)
        corner(left, top + h, 1f, -1f)
        corner(left + w, top + h, -1f, -1f)
        if (showLaser) {
            val y = top + h * t
            drawLine(Color(0xFFFF5A5F), Offset(left + 8.dp.toPx(), y), Offset(left + w - 8.dp.toPx(), y), 2.dp.toPx())
        }
    }
}
