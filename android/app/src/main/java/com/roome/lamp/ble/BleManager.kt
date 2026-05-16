package com.roome.lamp.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import com.roome.lamp.model.LampDevice
import java.util.UUID

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    companion object {
        private const val TAG = "BleManager"
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var handshakeCompleter: CompletableDeferred<Boolean>? = null

    // Public state
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _connectedAddress = MutableStateFlow<String?>(null)
    val connectedAddress: StateFlow<String?> = _connectedAddress

    private val _scannedDevices = MutableStateFlow<List<LampDevice>>(emptyList())
    val scannedDevices: StateFlow<List<LampDevice>> = _scannedDevices

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _responses = MutableSharedFlow<LampProtocol.ParsedResponse>(extraBufferCapacity = 64)
    val responses: SharedFlow<LampProtocol.ParsedResponse> = _responses

    private val _log = MutableSharedFlow<String>(extraBufferCapacity = 256)
    val log: SharedFlow<String> = _log

    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, READY }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    // --- Scanning ---

    fun startScan(timeoutMs: Long = 10_000) {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: run {
            emitLog("Bluetooth LE scanner not available")
            return
        }

        _scannedDevices.value = emptyList()
        _isScanning.value = true
        val found = mutableMapOf<String, LampDevice>()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = result.scanRecord?.deviceName ?: device.name ?: ""
                val addr = device.address
                val rssi = result.rssi

                val existing = found[addr]
                if (existing == null || rssi > existing.rssi) {
                    found[addr] = LampDevice(name = name, address = addr, rssi = rssi)
                    _scannedDevices.value = found.values
                        .sortedWith(compareByDescending<LampDevice> { it.isRoomeDevice }.thenByDescending { it.rssi })
                }
            }

            override fun onScanFailed(errorCode: Int) {
                emitLog("Scan failed: error $errorCode")
                _isScanning.value = false
            }
        }

        val filters = LampProtocol.DEVICE_NAME_PREFIXES.map { prefix ->
            ScanFilter.Builder().build()
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(null, settings, callback)
        emitLog("Scanning...")

        scope.launch {
            delay(timeoutMs)
            scanner.stopScan(callback)
            _isScanning.value = false
            emitLog("Scan complete: ${found.size} device(s)")
        }
    }

    // --- Connection ---

    fun connect(address: String) {
        if (_connectionState.value != ConnectionState.DISCONNECTED) {
            disconnect()
        }

        _connectionState.value = ConnectionState.CONNECTING
        emitLog("Connecting to $address...")

        val device = bluetoothAdapter?.getRemoteDevice(address) ?: run {
            emitLog("Device not found: $address")
            _connectionState.value = ConnectionState.DISCONNECTED
            return
        }

        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        bluetoothGatt?.let {
            it.disconnect()
            it.close()
        }
        bluetoothGatt = null
        rxCharacteristic = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectedAddress.value = null
        emitLog("Disconnected")
    }

    // --- GATT Callback ---

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    emitLog("Connected, discovering services...")
                    _connectedAddress.value = gatt.device.address
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    emitLog("Disconnected (status=$status)")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    _connectedAddress.value = null
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                    rxCharacteristic = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                emitLog("Service discovery failed: $status")
                _connectionState.value = ConnectionState.DISCONNECTED
                return
            }

            val service = gatt.getService(UUID.fromString(LampProtocol.NUS_SERVICE_UUID))
            if (service == null) {
                emitLog("NUS service not found on device")
                _connectionState.value = ConnectionState.DISCONNECTED
                return
            }

            rxCharacteristic = service.getCharacteristic(UUID.fromString(LampProtocol.NUS_RX_UUID))
            val txCharacteristic = service.getCharacteristic(UUID.fromString(LampProtocol.NUS_TX_UUID))

            if (rxCharacteristic == null || txCharacteristic == null) {
                emitLog("NUS characteristics not found")
                _connectionState.value = ConnectionState.DISCONNECTED
                return
            }

            // Enable notifications on TX characteristic
            gatt.setCharacteristicNotification(txCharacteristic, true)
            val descriptor = txCharacteristic.getDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            )
            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }

            _connectionState.value = ConnectionState.CONNECTED
            emitLog("Services discovered, starting handshake...")

            // Start handshake
            scope.launch {
                delay(500)
                performHandshake()
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = characteristic.value ?: return
            val hex = data.joinToString("") { "%02x".format(it) }
            emitLog("← RX: $hex")

            val parsed = LampProtocol.parseNotification(data)

            when (parsed.type) {
                LampProtocol.ResponseType.HANDSHAKE -> {
                    emitLog("Handshake response received")
                    handshakeCompleter?.complete(true)
                }
                else -> {
                    emitLog("   ${parsed.message}")
                    _responses.tryEmit(parsed)

                    // Send receipt ACK for 0x81 pushes
                    if (parsed.type == LampProtocol.ResponseType.PUSH && data.size >= 2) {
                        val cmdId = data[1].toInt() and 0xFF
                        if (cmdId == 0x81) {
                            writeCommand(LampProtocol.receiptAck(0x81))
                        }
                    }
                }
            }
        }
    }

    // --- Handshake ---

    private suspend fun performHandshake() {
        handshakeCompleter = CompletableDeferred()

        emitLog("→ TX (query): 0102")
        writeCommand(LampProtocol.cmdQueryDevice())

        val success = try {
            withTimeout(5_000) { handshakeCompleter!!.await() }
        } catch (e: TimeoutCancellationException) {
            emitLog("Handshake timeout")
            false
        }

        if (success) {
            delay(100)
            val ack = LampProtocol.cmdHandshakeAck()
            emitLog("→ TX (ack): ${ack.joinToString("") { "%02x".format(it) }}")
            writeCommand(ack)
            delay(300)
            _connectionState.value = ConnectionState.READY
            emitLog("Handshake complete — ready")
        } else {
            emitLog("Handshake failed, trying commands anyway")
            _connectionState.value = ConnectionState.READY
        }
    }

    // --- Command sending ---

    fun sendCommand(command: ByteArray) {
        val hex = command.joinToString("") { "%02x".format(it) }
        emitLog("→ TX: $hex")
        writeCommand(command)
    }

    private fun writeCommand(data: ByteArray) {
        val gatt = bluetoothGatt ?: return
        val char = rxCharacteristic ?: return
        char.value = data
        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        gatt.writeCharacteristic(char)
    }

    fun destroy() {
        disconnect()
        scope.cancel()
    }

    private fun emitLog(msg: String) {
        Log.d(TAG, msg)
        _log.tryEmit(msg)
    }
}
