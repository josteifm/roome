package com.roome.lamp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.roome.lamp.ble.BleManager
import com.roome.lamp.model.LampDevice
import com.roome.lamp.viewmodel.LampViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: LampViewModel,
    onDeviceSelected: (String) -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val connectedAddress by viewModel.connectedAddress.collectAsState()
    val scannedDevices by viewModel.scannedDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Roome Lamp") },
                actions = {
                    if (connectionState == BleManager.ConnectionState.READY ||
                        connectionState == BleManager.ConnectionState.CONNECTED
                    ) {
                        IconButton(onClick = { viewModel.disconnect() }) {
                            Icon(Icons.Default.BluetoothDisabled, "Disconnect")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Connection status
            item {
                ConnectionStatusCard(connectionState, connectedAddress)
            }

            // Known devices
            item {
                Text(
                    "Known Devices",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }

            items(viewModel.knownDevices) { device ->
                DeviceCard(
                    device = device,
                    isConnected = connectedAddress?.equals(device.address, ignoreCase = true) == true,
                    connectionState = connectionState,
                    onConnect = { viewModel.connect(device.address) },
                    onControl = { onDeviceSelected(device.address) }
                )
            }

            // Scan section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Scanned Devices", style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = { viewModel.startScan() },
                        enabled = !isScanning
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Scanning...")
                        } else {
                            Icon(Icons.Default.BluetoothSearching, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Scan")
                        }
                    }
                }
            }

            items(scannedDevices) { device ->
                DeviceCard(
                    device = device,
                    isConnected = connectedAddress?.equals(device.address, ignoreCase = true) == true,
                    connectionState = connectionState,
                    onConnect = { viewModel.connect(device.address) },
                    onControl = { onDeviceSelected(device.address) }
                )
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun ConnectionStatusCard(state: BleManager.ConnectionState, address: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (state) {
                BleManager.ConnectionState.READY -> MaterialTheme.colorScheme.primaryContainer
                BleManager.ConnectionState.CONNECTED,
                BleManager.ConnectionState.CONNECTING -> MaterialTheme.colorScheme.secondaryContainer
                BleManager.ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                when (state) {
                    BleManager.ConnectionState.READY -> Icons.Default.BluetoothConnected
                    BleManager.ConnectionState.CONNECTED,
                    BleManager.ConnectionState.CONNECTING -> Icons.Default.Bluetooth
                    BleManager.ConnectionState.DISCONNECTED -> Icons.Default.BluetoothDisabled
                },
                contentDescription = null
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    when (state) {
                        BleManager.ConnectionState.READY -> "Connected & Ready"
                        BleManager.ConnectionState.CONNECTED -> "Connected (handshaking...)"
                        BleManager.ConnectionState.CONNECTING -> "Connecting..."
                        BleManager.ConnectionState.DISCONNECTED -> "Disconnected"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (address != null) {
                    Text(address, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun DeviceCard(
    device: LampDevice,
    isConnected: Boolean,
    connectionState: BleManager.ConnectionState,
    onConnect: () -> Unit,
    onControl: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (device.isRoomeDevice) Icons.Default.Lightbulb else Icons.Default.Devices,
                contentDescription = null,
                tint = if (device.isRoomeDevice) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    device.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    buildString {
                        append(device.address)
                        if (device.name.isNotEmpty() && device.alias != null) {
                            append(" • ${device.name}")
                        }
                        if (device.rssi != 0) {
                            append(" • ${device.rssi} dBm")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isConnected && connectionState == BleManager.ConnectionState.READY) {
                FilledTonalButton(onClick = onControl) {
                    Text("Control")
                }
            } else if (!isConnected) {
                OutlinedButton(onClick = onConnect) {
                    Text("Connect")
                }
            }
        }
    }
}
