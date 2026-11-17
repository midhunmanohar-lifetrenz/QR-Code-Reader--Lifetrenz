package com.lifetrenz.qrreader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.lifetrenz.qrreader.ui.ConnectScreen
import com.lifetrenz.qrreader.ui.HistoryScreen
import com.lifetrenz.qrreader.ui.QRReaderTheme
import com.lifetrenz.qrreader.ui.ScanScreen
import com.lifetrenz.qrreader.ui.SettingsScreen

private val REQUIRED = arrayOf(Manifest.permission.CAMERA, Manifest.permission.BLUETOOTH_CONNECT)

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { QRReaderTheme { Root(vm) } }
    }

    override fun onResume() {
        super.onResume()
        if (REQUIRED.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            vm.hid.start()
        }
    }
}

@Composable
private fun Root(vm: MainViewModel) {
    val context = LocalContext.current
    fun allGranted() = REQUIRED.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    var granted by remember { mutableStateOf(allGranted()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = allGranted()
        if (granted) vm.hid.start()
    }

    // Picks up permissions granted in system settings while the app was away.
    LifecycleResumeEffect(Unit) {
        granted = allGranted()
        onPauseOrDispose { }
    }

    if (granted) {
        MainScreen(vm)
    } else {
        PermissionScreen(
            onAllow = { launcher.launch(REQUIRED) },
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            },
        )
    }
}

@Composable
private fun PermissionScreen(onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painterResource(R.drawable.ic_foreground),
            contentDescription = null,
            modifier = Modifier.size(112.dp).clip(RoundedCornerShape(32.dp)).background(Color.White),
        )
        Text("Lifetrenz QR Reader", fontSize = 26.sp, fontWeight = FontWeight.Medium)
        Text(
            "The camera reads barcodes. It never saves or uploads images.\n\n" +
                "Nearby devices lets the phone connect to your laptop as a Bluetooth keyboard.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onAllow, modifier = Modifier.height(52.dp)) { Text("Allow camera and nearby devices") }
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.height(48.dp)) { Text("Open app settings") }
    }
}

private val TABS = listOf(
    "Scan" to Icons.Filled.QrCodeScanner,
    "Connect" to Icons.Filled.Bluetooth,
    "History" to Icons.Filled.History,
    "Settings" to Icons.Filled.Settings,
)

/** Height of the floating navigation bar plus its margins, kept clear at the bottom of every screen. */
private val NAV_SPACE = 92.dp

@Composable
private fun MainScreen(vm: MainViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // The Scan screen runs its camera up under the status bar; the others start below it.
        Box(
            Modifier
                .fillMaxSize()
                .then(if (tab == 0) Modifier else Modifier.statusBarsPadding())
                .navigationBarsPadding()
                .padding(bottom = NAV_SPACE),
        ) {
            when (tab) {
                0 -> ScanScreen(vm)
                1 -> ConnectScreen(vm)
                2 -> HistoryScreen(vm)
                else -> SettingsScreen(vm)
            }
        }
        FloatingNav(
            tab = tab,
            onSelect = { tab = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .fillMaxWidth(),
        )
    }
}

/** Rounded floating bar; the selected tab widens and shows its name. */
@Composable
private fun FloatingNav(tab: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier = modifier, shape = CircleShape, color = colors.surfaceContainerHigh) {
        Row(Modifier.padding(8.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TABS.forEachIndexed { i, (label, icon) ->
                val selected = i == tab
                Box(
                    Modifier
                        .weight(if (selected) 1.8f else 1f)
                        .height(52.dp)
                        .clip(CircleShape)
                        .background(if (selected) colors.primary else Color.Transparent)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(i) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(icon, contentDescription = if (selected) null else label, tint = if (selected) colors.onPrimary else colors.onSurfaceVariant)
                        if (selected) Text(label, color = colors.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
    }
}
