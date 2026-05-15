package com.roome.lamp.model

data class LampDevice(
    val name: String,
    val address: String,
    val alias: String? = null,
    val rssi: Int = 0
) {
    val displayName: String
        get() = alias ?: name.ifEmpty { address }

    val isRoomeDevice: Boolean
        get() = DEVICE_NAME_PREFIXES.any { name.startsWith(it) }

    companion object {
        private val DEVICE_NAME_PREFIXES = listOf("RoomeLightMini", "RoomeLight", "RoomeSwitch", "RoomeSwitchAce")

        val KNOWN_DEVICES = mapOf(
            "lamp1" to "AA:BB:CC:DD:EE:01",  // Replace with your device MAC
            "lamp2" to "AA:BB:CC:DD:EE:02",  // Replace with your device MAC
            "lamp3" to "AA:BB:CC:DD:EE:03",  // Replace with your device MAC
        )

        fun knownDevicesList(): List<LampDevice> = KNOWN_DEVICES.map { (alias, address) ->
            LampDevice(name = "", address = address, alias = alias)
        }
    }
}
