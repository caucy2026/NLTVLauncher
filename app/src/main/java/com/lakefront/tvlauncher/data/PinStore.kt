package com.lakefront.tvlauncher.data

import com.lakefront.tvlauncher.logic.ComponentKey
import com.lakefront.tvlauncher.logic.PinCodec

class PinStore(private val preferences: LauncherPreferences) {
    fun read(): List<ComponentKey> = PinCodec.decode(preferences.pinsRaw)

    fun write(order: List<ComponentKey>) {
        preferences.pinsRaw = PinCodec.encode(order)
    }
}
