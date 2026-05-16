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
    }
}
