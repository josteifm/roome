package com.roome.lamp.ble

/**
 * BlitzWolf / Roome BLE Lamp Protocol
 *
 * Nordic UART Service (NUS) over BLE
 *   Service UUID: 6e400001-b5a3-f393-e0a9-e50e24dcca9e
 *   RX (write):   6e400002-b5a3-f393-e0a9-e50e24dcca9e  (app → device)
 *   TX (notify):  6e400003-b5a3-f393-e0a9-e50e24dcca9e  (device → app)
 *
 * Command frame: 01 <cmd_id> <01=write|00=read> [params as hex bytes]
 */
object LampProtocol {

    const val NUS_SERVICE_UUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
    const val NUS_RX_UUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
    const val NUS_TX_UUID = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"

    val DEVICE_NAME_PREFIXES = listOf("RoomeLightMini", "RoomeLight", "RoomeSwitch", "RoomeSwitchAce")

    const val BRIGHTNESS_OFF_THRESHOLD = 3

    // Command IDs
    private const val CMD_SWITCH_CONTROL = 0x43
    private const val CMD_BRIGHTNESS = 0x4F
    private const val CMD_LIGHT_FULL = 0x49
    private const val CMD_LIGHT_EXTENDED = 0x48
    private const val CMD_LIGHT_SAVE = 0x61
    private const val CMD_AUTO_CONTROL = 0x4A
    private const val CMD_DELAY = 0x4B
    private const val CMD_SLEEP_ON = 0x47
    private const val CMD_NO_PEOPLE_OFF = 0x46
    private const val CMD_REST_LIGHT = 0x4C
    private const val CMD_RECOVERY = 0x4D
    private const val CMD_SET_DATETIME = 0x06
    private const val CMD_QUERY_LIGHT = 0x82
    private const val CMD_QUERY_LIGHT_NEW = 0x8A
    private const val CMD_QUERY_ENV_LIGHT = 0x8B
    private const val CMD_QUERY_FIRMWARE = 0x83
    private const val CMD_QUERY_BATTERY = 0x7E
    private const val CMD_QUERY_MAC = 0x03

    private fun buildCmd(cmdId: Int, write: Boolean, vararg params: Int): ByteArray {
        val bytes = mutableListOf<Byte>()
        bytes.add(0x01)
        bytes.add((cmdId and 0xFF).toByte())
        bytes.add(if (write) 0x01 else 0x00)
        for (p in params) {
            bytes.add((p and 0xFF).toByte())
        }
        return bytes.toByteArray()
    }

    fun cmdSetBrightness(brightness: Int): ByteArray {
        val b = brightness.coerceIn(0, 100)
        return buildCmd(CMD_BRIGHTNESS, true, b, 0)
    }

    fun cmdLightFull(on: Int, brightness: Int, warm: Int, cool: Int, r: Int, g: Int, b: Int): ByteArray {
        return buildCmd(CMD_LIGHT_FULL, true, on, brightness, warm, cool, r, g, b)
    }

    fun cmdPowerOn(brightness: Int = 100): ByteArray {
        val b = brightness.coerceIn(1, 100)
        return cmdSetBrightness(b)
    }

    fun cmdPowerOff(): ByteArray = cmdSetBrightness(0)

    fun cmdSetColor(r: Int, g: Int, b: Int, brightness: Int = 100): ByteArray {
        return cmdLightFull(1, brightness, 0, 0, r, g, b)
    }

    fun cmdSetWarmWhite(brightness: Int = 100, warm: Int = 100, cool: Int = 0): ByteArray {
        return cmdLightFull(1, brightness, warm, cool, 0, 0, 0)
    }

    fun cmdAutoControl(enable: Boolean, sensitivity: Int = 128): ByteArray {
        return buildCmd(CMD_AUTO_CONTROL, true, if (enable) 1 else 0, sensitivity)
    }

    fun cmdSleepTimer(minutes: Int): ByteArray {
        val seconds = minutes * 60
        val high = (seconds shr 8) and 0xFF
        val low = seconds and 0xFF
        return buildCmd(CMD_DELAY, true, high, low)
    }

    fun cmdQueryState(): ByteArray = buildCmd(CMD_BRIGHTNESS, false)

    fun cmdQueryStateNew(): ByteArray = buildCmd(CMD_QUERY_LIGHT_NEW, false)

    fun cmdQueryBattery(): ByteArray = buildCmd(CMD_QUERY_BATTERY, false)

    fun cmdQueryFirmware(): ByteArray = buildCmd(CMD_QUERY_FIRMWARE, false, 0, 0, 0)

    fun cmdFactoryReset(): ByteArray = buildCmd(CMD_RECOVERY, true)

    fun cmdQueryDevice(): ByteArray = byteArrayOf(0x01, 0x02)

    fun cmdHandshakeAck(): ByteArray = byteArrayOf(0x01, 0x00, 0x01, 0x04, 0x00, 0x00)

    fun receiptAck(cmdIdHex: Int): ByteArray {
        return byteArrayOf(0x01, 0x00, (cmdIdHex and 0xFF).toByte(), 0x00, 0x00)
    }

    // --- Response parsing ---

    data class LightState(
        val isOn: Boolean,
        val brightness: Int,
        val warm: Int = 0,
        val cool: Int = 0,
        val r: Int = 0,
        val g: Int = 0,
        val b: Int = 0
    )

    data class ParsedResponse(
        val type: ResponseType,
        val message: String,
        val lightState: LightState? = null,
        val batteryLevel: Int? = null,
        val firmwareVersion: String? = null
    )

    enum class ResponseType {
        HANDSHAKE, ACK, PUSH, UNKNOWN
    }

    fun parseNotification(data: ByteArray): ParsedResponse {
        val hex = data.joinToString("") { "%02x".format(it) }

        // Handshake response
        if (hex == "0104") {
            return ParsedResponse(ResponseType.HANDSHAKE, "Device ready (handshake)")
        }

        if (data.size < 2) {
            return ParsedResponse(ResponseType.UNKNOWN, "Unknown: $hex")
        }

        // ACK response: byte[1] == 0x00 and len >= 6
        if (data[1].toInt() == 0x00 && data.size >= 6) {
            val cmdId = data[3].toInt() and 0xFF
            val success = data[4].toInt() == 0
            val extra = if (data.size > 6) data.copyOfRange(6, data.size) else byteArrayOf()
            return parseAck(cmdId, success, extra)
        }

        // Push notification
        val cmdId = data[1].toInt() and 0xFF
        val payload = if (data.size > 2) data.copyOfRange(2, data.size) else byteArrayOf()
        return parsePush(cmdId, payload)
    }

    private fun parseAck(cmdId: Int, success: Boolean, extra: ByteArray): ParsedResponse {
        val successStr = if (success) "OK" else "FAIL"

        when (cmdId) {
            0x49 -> if (extra.size >= 7) {
                val onOff = extra[0].toInt() and 0xFF
                val bright = extra[1].toInt() and 0xFF
                val warm = extra[2].toInt() and 0xFF
                val cool = extra[3].toInt() and 0xFF
                val r = extra[4].toInt() and 0xFF
                val g = extra[5].toInt() and 0xFF
                val b = extra[6].toInt() and 0xFF
                val state = LightState(onOff != 0, bright, warm, cool, r, g, b)
                val onStr = if (onOff != 0) "ON" else "OFF"
                return ParsedResponse(
                    ResponseType.ACK,
                    "Light: $onStr bright=$bright warm=$warm cool=$cool RGB=($r,$g,$b)",
                    lightState = state
                )
            }
            0x8A -> if (extra.size >= 2) {
                val openEnv = extra[0].toInt() and 0xFF
                val closeEnv = extra[1].toInt() and 0xFF
                return ParsedResponse(ResponseType.ACK, "Ambient: open=$openEnv close=$closeEnv")
            }
            0x7E -> if (extra.isNotEmpty()) {
                val level = extra[0].toInt() and 0xFF
                return ParsedResponse(ResponseType.ACK, "Battery: $level%", batteryLevel = level)
            }
            0x83 -> if (extra.size >= 3) {
                val ver = "${extra[0].toInt() and 0xFF}.${extra[1].toInt() and 0xFF}.${extra[2].toInt() and 0xFF}"
                return ParsedResponse(ResponseType.ACK, "Firmware: v$ver", firmwareVersion = ver)
            }
            0x4F -> if (extra.isNotEmpty()) {
                val bright = extra[0].toInt() and 0xFF
                val isOn = bright > BRIGHTNESS_OFF_THRESHOLD
                val state = LightState(isOn, bright)
                val onStr = if (isOn) "ON" else "OFF"
                return ParsedResponse(ResponseType.ACK, "Brightness: $onStr bright=$bright", lightState = state)
            }
        }

        val extraHex = extra.joinToString("") { "%02x".format(it) }
        return ParsedResponse(ResponseType.ACK, "ACK cmd=0x${"%02x".format(cmdId)} $successStr data=$extraHex")
    }

    private fun parsePush(cmdId: Int, payload: ByteArray): ParsedResponse {
        when (cmdId) {
            0x81 -> if (payload.size >= 5) {
                val brightness = payload[3].toInt() and 0xFF
                val isOn = brightness > BRIGHTNESS_OFF_THRESHOLD
                val state = LightState(isOn, brightness)
                val onStr = if (isOn) "ON" else "OFF"
                return ParsedResponse(ResponseType.PUSH, "Status: $onStr bright=$brightness", lightState = state)
            }
            0x4F -> if (payload.isNotEmpty()) {
                val bright = payload[0].toInt() and 0xFF
                return ParsedResponse(ResponseType.PUSH, "Brightness: $bright")
            }
        }

        val payloadHex = payload.joinToString("") { "%02x".format(it) }
        return ParsedResponse(ResponseType.PUSH, "Push cmd=0x${"%02x".format(cmdId)} data=$payloadHex")
    }
}
