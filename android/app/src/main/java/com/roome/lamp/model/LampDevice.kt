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
            "lamp1" to "C5:72:80:E2:F2:16",
            "lamp2" to "FC:FB:D9:CA:76:F5",
            "lamp3" to "EB:53:F2:ED:59:3F",
        )

        fun knownDevicesList(): List<LampDevice> = KNOWN_DEVICES.map { (alias, address) ->
            LampDevice(name = "", address = address, alias = alias)
        }
    }
}
