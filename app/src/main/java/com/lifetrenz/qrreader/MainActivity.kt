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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
        MainScaffold(vm)
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
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.QrCodeScanner, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp))
        Text("Lifetrenz QR Reader", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "The camera reads barcodes. It never saves or uploads images.\n\n" +
                "Nearby devices lets the phone connect to your laptop as a Bluetooth keyboard.",
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAllow) { Text("Allow camera and nearby devices") }
        OutlinedButton(onClick = onOpenSettings) { Text("Open app settings") }
    }
}

@Composable
private fun MainScaffold(vm: MainViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val items = listOf(
        "Scan" to Icons.Filled.QrCodeScanner,
        "Connect" to Icons.Filled.Bluetooth,
        "History" to Icons.Filled.History,
        "Settings" to Icons.Filled.Settings,
    )
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                items.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icon, label) }, label = { Text(label) })
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> ScanScreen(vm)
                1 -> ConnectScreen(vm)
                2 -> HistoryScreen(vm)
                else -> SettingsScreen(vm)
            }
        }
    }
}
