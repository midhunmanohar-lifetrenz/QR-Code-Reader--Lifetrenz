package com.lifetrenz.qrreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifetrenz.qrreader.BuildConfig
import com.lifetrenz.qrreader.MainViewModel
import com.lifetrenz.qrreader.data.Suffix

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val s by vm.settings.flow.collectAsStateWithLifecycle()

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))

        Section("Typing on the laptop")
        Text("After the value press", fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Suffix.ENTER to "Enter", Suffix.TAB to "Tab", Suffix.NONE to "Nothing").forEach { (value, label) ->
                FilterChip(selected = s.suffix == value, onClick = { vm.settings.update { it.copy(suffix = value) } }, label = { Text(label) })
            }
        }
        OutlinedTextField(
            value = s.prefix,
            onValueChange = { v -> vm.settings.update { it.copy(prefix = v) } },
            label = { Text("Text typed before the value (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SwitchRow("Trim spaces around the value", s.trim) { v -> vm.settings.update { it.copy(trim = v) } }

        Section("Scanning")
        SwitchRow("Keep scanning after each read", s.continuous) { v -> vm.settings.update { it.copy(continuous = v) } }
        Text("Ignore the same code again within", fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(500L to "0.5 s", 1500L to "1.5 s", 3000L to "3 s").forEach { (ms, label) ->
                FilterChip(selected = s.dupWindowMs == ms, onClick = { vm.settings.update { it.copy(dupWindowMs = ms) } }, label = { Text(label) })
            }
        }
        SwitchRow("Beep on scan", s.beep) { v -> vm.settings.update { it.copy(beep = v) } }
        SwitchRow("Vibrate on scan", s.vibrate) { v -> vm.settings.update { it.copy(vibrate = v) } }
        SwitchRow("Keep screen on while scanning", s.keepAwake) { v -> vm.settings.update { it.copy(keepAwake = v) } }

        Section("Keep the app running")
        Text(
            "On iQOO and vivo phones set Battery to \"No restrictions\" for this app, so Bluetooth stays connected.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val context = androidx.compose.ui.platform.LocalContext.current
        androidx.compose.material3.OutlinedButton(onClick = {
            context.startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", context.packageName, null),
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }) { Text("Open app settings") }

        Section("About")
        Text("Lifetrenz QR Reader ${BuildConfig.VERSION_NAME}", fontSize = 14.sp)
        Text(
            "The phone acts as a Bluetooth keyboard. A US keyboard layout is assumed, which matches India QWERTY laptops for letters, digits and common symbols. Characters such as the rupee sign cannot be typed.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("", modifier = Modifier.padding(8.dp))
    }
}

@Composable
private fun Section(title: String) {
    Text(title.uppercase(), fontSize = 11.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
