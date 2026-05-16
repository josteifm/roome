package com.roome.lamp.data

import android.content.Context
import com.roome.lamp.model.LampDevice
import org.json.JSONArray
import org.json.JSONObject

class DeviceStorage(context: Context) {

    private val prefs = context.getSharedPreferences("saved_devices", Context.MODE_PRIVATE)
    private val key = "devices"

    fun loadDevices(): List<LampDevice> {
        val json = prefs.getString(key, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                LampDevice(
                    name = obj.optString("name", ""),
                    address = obj.getString("address"),
                    alias = obj.optString("alias", null)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveDevice(device: LampDevice) {
        val devices = loadDevices().toMutableList()
        val idx = devices.indexOfFirst { it.address.equals(device.address, ignoreCase = true) }
        if (idx >= 0) {
            devices[idx] = device
        } else {
            devices.add(device)
        }
        persist(devices)
    }

    fun removeDevice(address: String) {
        val devices = loadDevices().filter { !it.address.equals(address, ignoreCase = true) }
        persist(devices)
    }

    fun renameDevice(address: String, newAlias: String) {
        val devices = loadDevices().map {
            if (it.address.equals(address, ignoreCase = true)) {
                it.copy(alias = newAlias)
            } else it
        }
        persist(devices)
    }

    private fun persist(devices: List<LampDevice>) {
        val arr = JSONArray()
        for (d in devices) {
            arr.put(JSONObject().apply {
                put("name", d.name)
                put("address", d.address)
                if (d.alias != null) put("alias", d.alias)
            })
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }
}
