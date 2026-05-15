package com.roome.lamp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.roome.lamp.ui.screens.DeviceControlScreen
import com.roome.lamp.ui.screens.HomeScreen
import com.roome.lamp.ui.theme.RoomeLampTheme
import com.roome.lamp.viewmodel.LampViewModel

class MainActivity : ComponentActivity() {

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions granted or denied — UI will react */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request permissions if not granted
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }

        setContent {
            RoomeLampTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RoomeLampApp()
                }
            }
        }
    }
}

@Composable
fun RoomeLampApp() {
    val viewModel: LampViewModel = viewModel()
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

    when (currentScreen) {
        is Screen.Home -> {
            HomeScreen(
                viewModel = viewModel,
                onDeviceSelected = { currentScreen = Screen.Control(it) }
            )
        }
        is Screen.Control -> {
            DeviceControlScreen(
                viewModel = viewModel,
                onBack = { currentScreen = Screen.Home }
            )
        }
    }
}

sealed class Screen {
    data object Home : Screen()
    data class Control(val address: String) : Screen()
}
