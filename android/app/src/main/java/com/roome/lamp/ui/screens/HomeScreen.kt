package com.roome.lamp.ui.screens

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
import com.roome.lamp.ui.theme.*
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
    val savedDevices by viewModel.savedDevices.collectAsState()

    // Dialog state
    var showSaveDialog by remember { mutableStateOf(false) }
    var deviceToSave by remember { mutableStateOf<LampDevice?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var deviceToRename by remember { mutableStateOf<LampDevice?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deviceToDelete by remember { mutableStateOf<LampDevice?>(null) }

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

            // Saved devices
            item {
                Text(
                    "Saved Devices",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }

            if (savedDevices.isEmpty()) {
                item {
                    Text(
                        "No saved devices. Scan and save a device below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            items(savedDevices, key = { "saved_${it.address}" }) { device ->
                SavedDeviceCard(
                    device = device,
                    isConnected = connectedAddress?.equals(device.address, ignoreCase = true) == true,
                    connectionState = connectionState,
                    onConnect = { viewModel.connect(device.address) },
                    onControl = { onDeviceSelected(device.address) },
                    onRename = { deviceToRename = device; showRenameDialog = true },
                    onDelete = { deviceToDelete = device; showDeleteConfirm = true }
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
                        enabled = !isScanning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VaporPink,
                            contentColor = VaporNavy
                        )
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

            items(scannedDevices, key = { "scan_${it.address}" }) { device ->
                ScannedDeviceCard(
                    device = device,
                    isConnected = connectedAddress?.equals(device.address, ignoreCase = true) == true,
                    isSaved = viewModel.isDeviceSaved(device.address),
                    connectionState = connectionState,
                    onConnect = { viewModel.connect(device.address) },
                    onControl = { onDeviceSelected(device.address) },
                    onSave = { deviceToSave = device; showSaveDialog = true }
                )
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    // Save dialog
    if (showSaveDialog && deviceToSave != null) {
        SaveDeviceDialog(
            device = deviceToSave!!,
            onDismiss = { showSaveDialog = false; deviceToSave = null },
            onSave = { alias ->
                viewModel.saveDevice(deviceToSave!!, alias)
                showSaveDialog = false
                deviceToSave = null
            }
        )
    }

    // Rename dialog
    if (showRenameDialog && deviceToRename != null) {
        SaveDeviceDialog(
            device = deviceToRename!!,
            initialName = deviceToRename!!.alias ?: "",
            title = "Rename Device",
            onDismiss = { showRenameDialog = false; deviceToRename = null },
            onSave = { alias ->
                viewModel.renameDevice(deviceToRename!!.address, alias)
                showRenameDialog = false
                deviceToRename = null
            }
        )
    }

    // Delete confirmation
    if (showDeleteConfirm && deviceToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false; deviceToDelete = null },
            title = { Text("Remove Device") },
            text = { Text("Remove \"${deviceToDelete!!.displayName}\" from saved devices?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeDevice(deviceToDelete!!.address)
                    showDeleteConfirm = false
                    deviceToDelete = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false; deviceToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SaveDeviceDialog(
    device: LampDevice,
    initialName: String = device.name.ifEmpty { device.address },
    title: String = "Save Device",
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(device.address, style = MaterialTheme.typography.bodySmall)
                if (device.name.isNotEmpty()) {
                    Text(device.name, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Device name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name) },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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
fun SavedDeviceCard(
    device: LampDevice,
    isConnected: Boolean,
    connectionState: BleManager.ConnectionState,
    onConnect: () -> Unit,
    onControl: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
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
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Edit/delete actions
            IconButton(onClick = onRename, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, "Rename", Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, "Delete", Modifier.size(18.dp))
            }
            Spacer(Modifier.width(4.dp))
            if (isConnected && connectionState == BleManager.ConnectionState.READY) {
                FilledTonalButton(
                    onClick = onControl,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = VaporCyan,
                        contentColor = VaporNavy
                    )
                ) { Text("Control") }
            } else if (!isConnected) {
                OutlinedButton(
                    onClick = onConnect,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VaporPink)
                ) { Text("Connect") }
            }
        }
    }
}

@Composable
fun ScannedDeviceCard(
    device: LampDevice,
    isConnected: Boolean,
    isSaved: Boolean,
    connectionState: BleManager.ConnectionState,
    onConnect: () -> Unit,
    onControl: () -> Unit,
    onSave: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
                        if (device.rssi != 0) {
                            append(" • ${device.rssi} dBm")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isSaved) {
                IconButton(onClick = onSave, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Save, "Save", Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(4.dp))
            if (isConnected && connectionState == BleManager.ConnectionState.READY) {
                FilledTonalButton(
                    onClick = onControl,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = VaporCyan,
                        contentColor = VaporNavy
                    )
                ) { Text("Control") }
            } else if (!isConnected) {
                OutlinedButton(
                    onClick = onConnect,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VaporPink)
                ) { Text("Connect") }
            }
        }
    }
}
