package com.lifetrenz.qrreader.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetrenz.qrreader.data.ScanState
import com.lifetrenz.qrreader.data.Suffix

@SuppressLint("MissingPermission")
fun deviceLabel(d: BluetoothDevice): String = runCatching { d.name }.getOrNull() ?: d.address

fun suffixLabel(s: Suffix): String = when (s) {
    Suffix.ENTER -> "then Enter"
    Suffix.TAB -> "then Tab"
    Suffix.NONE -> "no key after"
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, fontSize = 32.sp, fontWeight = FontWeight.Medium)
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        fontSize = 12.sp,
        letterSpacing = 1.2.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Small rounded label, as used for scan status, barcode type and suffix. */
@Composable
fun Pill(text: String, container: Color, content: Color, icon: ImageVector? = null) {
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(14.dp)) }
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun NeutralPill(text: String) {
    Pill(text, MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Sent is green with a tick; Waiting is orange, so it reads as pending rather than an error. */
@Composable
fun StatePill(state: ScanState) {
    val dark = isSystemInDarkTheme()
    if (state == ScanState.SENT) {
        Pill(
            "Sent",
            if (dark) Color(0xFF1F3A2A) else Color(0xFFC4EED0),
            if (dark) Color(0xFF8BD0A0) else Color(0xFF0A3818),
            Icons.Filled.Check,
        )
    } else {
        Pill(
            "Waiting",
            if (dark) Color(0xFF4A2800) else Color(0xFFFFDDB3),
            if (dark) Color(0xFFFFB95C) else Color(0xFF4A2800),
        )
    }
}
