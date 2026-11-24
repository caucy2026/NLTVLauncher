package com.lakefront.tvlauncher.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.storage.StorageManager
import com.lakefront.tvlauncher.logic.RadioState
import com.lakefront.tvlauncher.logic.SystemStatus
import com.lakefront.tvlauncher.logic.UsbVolumeRules
import android.text.format.DateFormat as AndroidDateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SystemStatusMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val pokes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun poke() {
        pokes.tryEmit(Unit)
    }

    fun observe(): Flow<SystemStatus> = callbackFlow {
        fun push() {
            trySend(read())
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                push()
            }
        }
        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        val media = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        }
        if (Build.VERSION.SDK_INT >= 33) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            appContext.registerReceiver(receiver, media, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appContext.registerReceiver(receiver, filter)
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appContext.registerReceiver(receiver, media)
        }

        val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = push()
            override fun onLost(network: Network) = push()
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = push()
        }
        runCatching { connectivity?.registerDefaultNetworkCallback(networkCallback) }

        val clock = launch {
            while (currentCoroutineContext().isActive) {
                push()
                delay(minuteDelayMillis(System.currentTimeMillis()))
            }
        }
        val refreshes = launch {
            pokes.collect { push() }
        }
        push()
        awaitClose {
            clock.cancel()
            refreshes.cancel()
            runCatching { appContext.unregisterReceiver(receiver) }
            runCatching { connectivity?.unregisterNetworkCallback(networkCallback) }
        }
    }

    fun read(): SystemStatus = SystemStatus(
        time = formatTime(appContext),
        wifi = readWifi(),
        bluetooth = readBluetooth(),
        usbMounted = readUsbMounted(),
    )

    private fun readUsbMounted(): Boolean {
        val storage = appContext.getSystemService(StorageManager::class.java) ?: return false
        val massStorage = usbMassStorageConnected()
        return try {
            storage.storageVolumes.any { volume ->
                UsbVolumeRules.showIcon(
                    removable = volume.isRemovable,
                    primary = volume.isPrimary,
                    state = volume.state,
                    description = runCatching { volume.getDescription(appContext) }.getOrNull(),
                    usbMassStorageConnected = massStorage,
                )
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun usbMassStorageConnected(): Boolean {
        val usb = appContext.getSystemService(UsbManager::class.java) ?: return false
        return try {
            usb.deviceList.values.any { device ->
                (0 until device.interfaceCount).any { index ->
                    device.getInterface(index).interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun readWifi(): RadioState {
        if (!appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            return RadioState.Absent
        }
        val wifi = appContext.getSystemService(WifiManager::class.java) ?: return RadioState.Absent
        val enabled = try {
            wifi.isWifiEnabled
        } catch (_: Exception) {
            return RadioState.Absent
        }
        if (!enabled) return RadioState.Off
        val connectivity = appContext.getSystemService(ConnectivityManager::class.java) ?: return RadioState.On
        val network = connectivity.activeNetwork ?: return RadioState.On
        val caps = connectivity.getNetworkCapabilities(network) ?: return RadioState.On
        return if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            RadioState.Connected
        } else {
            RadioState.On
        }
    }

    private fun readBluetooth(): RadioState {
        if (!appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            return RadioState.Absent
        }
        val manager = appContext.getSystemService(BluetoothManager::class.java) ?: return RadioState.Absent
        val adapter = try {
            manager.adapter
        } catch (_: Exception) {
            null
        } ?: return RadioState.Absent
        val needsRuntimePermission = Build.VERSION.SDK_INT >= 31 &&
            appContext.checkSelfPermission(BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        if (needsRuntimePermission) return RadioState.NeedsPermission
        return try {
            val on = adapter.state == BluetoothAdapter.STATE_ON ||
                adapter.state == BluetoothAdapter.STATE_TURNING_ON
            if (!on) {
                RadioState.Off
            } else if (bluetoothConnected(adapter)) {
                RadioState.Connected
            } else {
                RadioState.On
            }
        } catch (_: SecurityException) {
            RadioState.NeedsPermission
        } catch (_: Exception) {
            RadioState.Absent
        }
    }

    @SuppressLint("MissingPermission", "WrongConstant")
    private fun bluetoothConnected(adapter: BluetoothAdapter): Boolean {
        if (Build.VERSION.SDK_INT >= 31 &&
            appContext.checkSelfPermission(BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val profiles = intArrayOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)
        return profiles.any { profile ->
            try {
                adapter.getProfileConnectionState(profile) == BluetoothProfile.STATE_CONNECTED
            } catch (_: SecurityException) {
                false
            }
        }
    }

    companion object {
        const val BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"

        fun formatTime(context: Context, now: Long = System.currentTimeMillis()): String {
            val pattern = if (AndroidDateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
            return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(now))
        }
    }
}

/** Keeps a cold clock flow available for tests of the delay math without Android. */
fun minuteDelayMillis(now: Long): Long = (60_000L - (now % 60_000L)).coerceIn(1_000L, 60_000L)
