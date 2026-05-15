package com.roome.lamp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roome.lamp.ui.theme.*
import com.roome.lamp.viewmodel.LampViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceControlScreen(
    viewModel: LampViewModel,
    onBack: () -> Unit
) {
    val brightness by viewModel.brightness.collectAsState()
    val warmth by viewModel.warmth.collectAsState()
    val coolness by viewModel.coolness.collectAsState()
    val colorR by viewModel.colorR.collectAsState()
    val colorG by viewModel.colorG.collectAsState()
    val colorB by viewModel.colorB.collectAsState()
    val lightState by viewModel.lightState.collectAsState()
    val logEntries by viewModel.logEntries.collectAsState()
    val connectedAddress by viewModel.connectedAddress.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(connectedAddress ?: "Control") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.disconnect(); onBack() }) {
                        Icon(Icons.Default.BluetoothDisabled, "Disconnect")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Text("Controls", modifier = Modifier.padding(12.dp))
                }
//                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
//                    Text("Color", modifier = Modifier.padding(12.dp))
//                }
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                    Text("Log", modifier = Modifier.padding(12.dp))
                }
            }

            when (selectedTab) {
                0 -> ControlsTab(viewModel, brightness, warmth, coolness, lightState)
//                1 -> ColorTab(viewModel, brightness, colorR, colorG, colorB)
                    2 -> LogTab(viewModel, logEntries)
            }
        }
    }
}

@Composable
fun ControlsTab(
    viewModel: LampViewModel,
    brightness: Int,
    warmth: Int,
    coolness: Int,
    lightState: com.roome.lamp.ble.LampProtocol.LightState?
) {
    var sleepMinutes by remember { mutableStateOf("30") }
    var localBrightness by remember(brightness) { mutableIntStateOf(brightness) }
    var localWarmth by remember(warmth) { mutableIntStateOf(warmth) }
    var localCoolness by remember(coolness) { mutableIntStateOf(coolness) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Power buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.powerOn(30) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VaporCyan,
                        contentColor = VaporNavy
                    )
                ) {
                    Icon(Icons.Default.LightMode, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("ON")
                }
                Button(
                    onClick = { viewModel.powerOff() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VaporHotPink,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.DarkMode, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("OFF")
                }
            }
        }

        // Current state
        if (lightState != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "State: ${if (lightState.isOn) "ON" else "OFF"} • Brightness: ${lightState.brightness}",
                            style = MaterialTheme.typography.bodyMedium
                        )
//                        if (lightState.warm > 0 || lightState.cool > 0) {
//                            Text(
//                                "Warm: ${lightState.warm} • Cool: ${lightState.cool}",
//                                style = MaterialTheme.typography.bodySmall
//                            )
//                        }
//                        if (lightState.r > 0 || lightState.g > 0 || lightState.b > 0) {
//                            Text(
//                                "RGB: (${lightState.r}, ${lightState.g}, ${lightState.b})",
//                                style = MaterialTheme.typography.bodySmall
//                            )
//                        }
                    }
                }
            }
        }

        // Brightness slider
        item {
            Text("Brightness: $localBrightness%", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = localBrightness.toFloat(),
                onValueChange = { localBrightness = it.toInt() },
                onValueChangeFinished = { viewModel.setBrightness(localBrightness) },
                valueRange = 0f..100f,
                steps = 99
            )
        }

        // Warm/Cool white
//        item {
//            Text("Warm White: $localWarmth", style = MaterialTheme.typography.titleSmall)
//            Slider(
//                value = localWarmth.toFloat(),
//                onValueChange = { localWarmth = it.toInt() },
//                onValueChangeFinished = { viewModel.setWarmWhite(localWarmth, localCoolness) },
//                valueRange = 0f..100f,
//                steps = 99
//            )
//        }
//
//        item {
//            Text("Cool White: $localCoolness", style = MaterialTheme.typography.titleSmall)
//            Slider(
//                value = localCoolness.toFloat(),
//                onValueChange = { localCoolness = it.toInt() },
//                onValueChangeFinished = { viewModel.setWarmWhite(localWarmth, localCoolness) },
//                valueRange = 0f..100f,
//                steps = 99
//            )
//        }

        // Auto control
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.setAutoControl(true) },
                    modifier = Modifier.weight(1f)
                ) { Text("Auto ON") }
                OutlinedButton(
                    onClick = { viewModel.setAutoControl(false) },
                    modifier = Modifier.weight(1f)
                ) { Text("Auto OFF") }
            }
        }

        // Sleep timer
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = sleepMinutes,
                    onValueChange = { sleepMinutes = it.filter { c -> c.isDigit() } },
                    label = { Text("Sleep (min)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
                Button(
                    onClick = {
                        sleepMinutes.toIntOrNull()?.let { viewModel.setSleepTimer(it) }
                    }
                ) { Text("Set") }
            }
        }

        // Query buttons
        item {
            Text("Query", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { viewModel.queryState() }) { Text("Status") }
                OutlinedButton(onClick = { viewModel.queryBattery() }) { Text("Battery") }
                OutlinedButton(onClick = { viewModel.queryFirmware() }) { Text("Firmware") }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun ColorTab(
    viewModel: LampViewModel,
    brightness: Int,
    colorR: Int,
    colorG: Int,
    colorB: Int
) {
    var localR by remember(colorR) { mutableIntStateOf(colorR) }
    var localG by remember(colorG) { mutableIntStateOf(colorG) }
    var localB by remember(colorB) { mutableIntStateOf(colorB) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Color preview
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(localR, localG, localB)
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "RGB($localR, $localG, $localB)",
                    color = if (localR + localG + localB > 380) Color.Black else Color.White
                )
            }
        }

        // R slider
        Text("Red: $localR", style = MaterialTheme.typography.titleSmall)
        Slider(
            value = localR.toFloat(),
            onValueChange = { localR = it.toInt() },
            onValueChangeFinished = { viewModel.setColor(localR, localG, localB) },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(thumbColor = Color.Red, activeTrackColor = Color.Red)
        )

        // G slider
        Text("Green: $localG", style = MaterialTheme.typography.titleSmall)
        Slider(
            value = localG.toFloat(),
            onValueChange = { localG = it.toInt() },
            onValueChangeFinished = { viewModel.setColor(localR, localG, localB) },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(thumbColor = Color.Green, activeTrackColor = Color.Green)
        )

        // B slider
        Text("Blue: $localB", style = MaterialTheme.typography.titleSmall)
        Slider(
            value = localB.toFloat(),
            onValueChange = { localB = it.toInt() },
            onValueChangeFinished = { viewModel.setColor(localR, localG, localB) },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(thumbColor = Color.Blue, activeTrackColor = Color.Blue)
        )

        // Preset colors
        Text("Presets", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            data class ColorPreset(val name: String, val r: Int, val g: Int, val b: Int)
            val presets = listOf(
                ColorPreset("Red", 255, 0, 0),
                ColorPreset("Green", 0, 255, 0),
                ColorPreset("Blue", 0, 0, 255),
                ColorPreset("Yellow", 255, 255, 0),
                ColorPreset("Purple", 128, 0, 255),
                ColorPreset("Cyan", 0, 255, 255),
            )
            presets.forEach { preset ->
                FilledTonalButton(
                    onClick = {
                        localR = preset.r; localG = preset.g; localB = preset.b
                        viewModel.setColor(preset.r, preset.g, preset.b)
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    Text(preset.name, fontSize = 11.sp, maxLines = 1)
                }
            }
        }

        // White presets
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.setWarmWhite(100, 0) },
                modifier = Modifier.weight(1f)
            ) { Text("Warm White") }
            OutlinedButton(
                onClick = { viewModel.setWarmWhite(0, 100) },
                modifier = Modifier.weight(1f)
            ) { Text("Cool White") }
        }
    }
}

@Composable
fun LogTab(viewModel: LampViewModel, logEntries: List<String>) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var rawHex by remember { mutableStateOf("") }

    // Auto-scroll to bottom
    LaunchedEffect(logEntries.size) {
        if (logEntries.isNotEmpty()) {
            listState.animateScrollToItem(logEntries.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Raw command input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = rawHex,
                onValueChange = { rawHex = it },
                label = { Text("Raw hex") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (rawHex.isNotBlank()) {
                        viewModel.sendRaw(rawHex.replace(" ", ""))
                        rawHex = ""
                    }
                })
            )
            Button(onClick = {
                if (rawHex.isNotBlank()) {
                    viewModel.sendRaw(rawHex.replace(" ", ""))
                    rawHex = ""
                }
            }) { Text("Send") }
            IconButton(onClick = { viewModel.clearLog() }) {
                Icon(Icons.Default.Delete, "Clear log")
            }
        }

        // Log list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
        ) {
            items(logEntries) { entry ->
                Text(
                    entry,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }
    }
}
