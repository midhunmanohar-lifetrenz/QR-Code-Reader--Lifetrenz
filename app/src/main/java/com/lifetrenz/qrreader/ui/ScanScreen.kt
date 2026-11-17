package com.lifetrenz.qrreader.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifetrenz.qrreader.MainViewModel
import com.lifetrenz.qrreader.R
import com.lifetrenz.qrreader.data.ScanState

private val OverlayText = Color(0xFF1F1F1F)
private val OverlayAccent = Color(0xFF0B57D0)
private val ScanLine = Color(0xFF7FCFFF)

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

    val connected = hid.connected
    val queued = history.count { it.state == ScanState.QUEUED }

    Column(Modifier.fillMaxSize()) {
        // Camera fills the top of the screen, with the status and torch floating over it.
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
                .background(Color.Black),
        ) {
            CameraPreview(Modifier.fillMaxSize(), torchOn = torch, active = !paused, onBarcode = vm::onBarcode)
            ScanOverlay(Modifier.fillMaxSize(), showLaser = !paused)

            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(shape = CircleShape, color = Color.White, contentColor = OverlayText, modifier = Modifier.weight(1f)) {
                    Row(
                        Modifier.padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Image(
                            painterResource(R.drawable.ic_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFFF0F4F9)),
                        )
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (connected != null) OkGreen else BadRed))
                        Text(
                            if (connected != null) "Typing into ${deviceLabel(connected)}" else "Laptop not connected",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(
                    onClick = { torch = !torch },
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White),
                ) {
                    Icon(if (torch) Icons.Filled.FlashOn else Icons.Filled.FlashOff, contentDescription = "Torch", tint = OverlayAccent)
                }
            }

            Text(
                if (paused) "Scan paused" else "Centre the barcode in the frame",
                color = Color(0xFFE3E3E3),
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
            )
            if (paused) {
                Button(onClick = vm::resumeScanning, modifier = Modifier.align(Alignment.Center)) { Text("Scan next") }
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel("Last scan")
            val value = last?.value
            Text(
                value ?: "Nothing scanned yet",
                fontFamily = if (value != null) FontFamily.Monospace else FontFamily.Default,
                fontSize = when {
                    value == null -> 22.sp
                    value.length > 14 -> 26.sp
                    else -> 40.sp
                },
                fontWeight = FontWeight.Medium,
                color = if (value != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            last?.let { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatePill(r.state)
                    NeutralPill(r.format)
                    NeutralPill(suffixLabel(settings.suffix))
                }
            }
            notice?.let { Text(it, color = WarnAmber, fontSize = 13.sp) }
            if (queued > 0) {
                Text(
                    if (connected == null) "$queued scan(s) waiting. They are sent when the laptop connects."
                    else "$queued scan(s) waiting. Send them from History.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (connected == null) {
                Text(
                    "Connect a laptop in the Connect tab. Scans are kept until then.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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
        val w = size.width * 0.78f
        val h = size.height * 0.40f
        val left = (size.width - w) / 2
        val top = (size.height - h) / 2
        val len = 36.dp.toPx()
        val sw = 5.dp.toPx()
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
            drawLine(ScanLine, Offset(left + 10.dp.toPx(), y), Offset(left + w - 10.dp.toPx(), y), 4.dp.toPx(), StrokeCap.Round)
        }
    }
}
