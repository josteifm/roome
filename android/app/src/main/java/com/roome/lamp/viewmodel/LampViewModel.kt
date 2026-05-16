package com.roome.lamp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roome.lamp.ble.BleManager
import com.roome.lamp.ble.LampProtocol
import com.roome.lamp.data.DeviceStorage
import com.roome.lamp.model.LampDevice
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LampViewModel(application: Application) : AndroidViewModel(application) {

    val bleManager = BleManager(application.applicationContext)
    private val deviceStorage = DeviceStorage(application.applicationContext)

    val connectionState = bleManager.connectionState
    val connectedAddress = bleManager.connectedAddress
    val scannedDevices = bleManager.scannedDevices
    val isScanning = bleManager.isScanning

    private val _savedDevices = MutableStateFlow<List<LampDevice>>(emptyList())
    val savedDevices: StateFlow<List<LampDevice>> = _savedDevices

    // Log entries
    private val _logEntries = MutableStateFlow<List<String>>(emptyList())
    val logEntries: StateFlow<List<String>> = _logEntries

    // Light state from device responses
    private val _lightState = MutableStateFlow<LampProtocol.LightState?>(null)
    val lightState: StateFlow<LampProtocol.LightState?> = _lightState

    // UI state
    private val _brightness = MutableStateFlow(100)
    val brightness: StateFlow<Int> = _brightness

    private val _warmth = MutableStateFlow(100)
    val warmth: StateFlow<Int> = _warmth

    private val _coolness = MutableStateFlow(0)
    val coolness: StateFlow<Int> = _coolness

    private val _colorR = MutableStateFlow(0)
    val colorR: StateFlow<Int> = _colorR
    private val _colorG = MutableStateFlow(0)
    val colorG: StateFlow<Int> = _colorG
    private val _colorB = MutableStateFlow(0)
    val colorB: StateFlow<Int> = _colorB

    init {
        reloadSavedDevices()
        // Collect log messages
        viewModelScope.launch {
            bleManager.log.collect { msg ->
                _logEntries.update { entries ->
                    (entries + msg).takeLast(200)
                }
            }
        }
        // Collect responses and update light state
        viewModelScope.launch {
            bleManager.responses.collect { response ->
                response.lightState?.let { state ->
                    _lightState.value = state
                    _brightness.value = state.brightness
                    _warmth.value = state.warm
                    _coolness.value = state.cool
                    _colorR.value = state.r
                    _colorG.value = state.g
                    _colorB.value = state.b
                }
            }
        }
    }

    fun startScan() = bleManager.startScan()

    fun connect(address: String) = bleManager.connect(address)

    fun disconnect() = bleManager.disconnect()

    fun isBluetoothEnabled() = bleManager.isBluetoothEnabled()

    fun powerOn(brightness: Int = 100) {
        _brightness.value = brightness
        bleManager.sendCommand(LampProtocol.cmdPowerOn(brightness))
    }

    fun powerOff() {
        bleManager.sendCommand(LampProtocol.cmdPowerOff())
    }

    fun setBrightness(value: Int) {
        _brightness.value = value
        bleManager.sendCommand(LampProtocol.cmdSetBrightness(value))
    }

    fun setColor(r: Int, g: Int, b: Int) {
        _colorR.value = r
        _colorG.value = g
        _colorB.value = b
        bleManager.sendCommand(LampProtocol.cmdSetColor(r, g, b, _brightness.value))
    }

    fun setWarmWhite(warm: Int, cool: Int) {
        _warmth.value = warm
        _coolness.value = cool
        bleManager.sendCommand(LampProtocol.cmdSetWarmWhite(_brightness.value, warm, cool))
    }

    fun setAutoControl(enable: Boolean) {
        bleManager.sendCommand(LampProtocol.cmdAutoControl(enable))
    }

    fun setSleepTimer(minutes: Int) {
        bleManager.sendCommand(LampProtocol.cmdSleepTimer(minutes))
    }

    fun queryState() {
        bleManager.sendCommand(LampProtocol.cmdQueryState())
    }

    fun queryBattery() {
        bleManager.sendCommand(LampProtocol.cmdQueryBattery())
    }

    fun queryFirmware() {
        bleManager.sendCommand(LampProtocol.cmdQueryFirmware())
    }

    fun sendRaw(hex: String) {
        try {
            val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            bleManager.sendCommand(bytes)
        } catch (e: Exception) {
            _logEntries.update { it + "Invalid hex: ${e.message}" }
        }
    }

    fun clearLog() {
        _logEntries.value = emptyList()
    }

    // --- Saved devices ---

    private fun reloadSavedDevices() {
        _savedDevices.value = deviceStorage.loadDevices()
    }

    fun saveDevice(device: LampDevice, alias: String) {
        deviceStorage.saveDevice(device.copy(alias = alias))
        reloadSavedDevices()
    }

    fun renameDevice(address: String, newAlias: String) {
        deviceStorage.renameDevice(address, newAlias)
        reloadSavedDevices()
    }

    fun removeDevice(address: String) {
        deviceStorage.removeDevice(address)
        reloadSavedDevices()
    }

    fun isDeviceSaved(address: String): Boolean {
        return _savedDevices.value.any { it.address.equals(address, ignoreCase = true) }
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.destroy()
    }
}
