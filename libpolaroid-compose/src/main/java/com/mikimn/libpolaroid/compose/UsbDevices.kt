package com.mikimn.libpolaroid.compose

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.mikimn.libpolaroid.uvcDevices

/** Registers [receiver] for [actions] while the calling composable is in the composition. */
@Composable
internal fun BroadcastEffect(
    vararg actions: String,
    flags: Int = ContextCompat.RECEIVER_EXPORTED,
    receiver: (Intent) -> Unit,
) {
    val context = LocalContext.current
    DisposableEffect(context, flags, *actions) {
        val registered = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = receiver(intent)
        }
        val filter = IntentFilter().apply { actions.forEach(::addAction) }
        ContextCompat.registerReceiver(context, registered, filter, flags)
        onDispose { context.unregisterReceiver(registered) }
    }
}

/** The currently attached UVC devices, kept up to date as cameras are plugged in and out. */
@Composable
public fun rememberUvcDevices(): State<List<UsbDevice>> {
    val manager = LocalContext.current.getSystemService(UsbManager::class.java)
    val devices = remember { mutableStateOf(manager.uvcDevices()) }
    BroadcastEffect(UsbManager.ACTION_USB_DEVICE_ATTACHED, UsbManager.ACTION_USB_DEVICE_DETACHED) {
        devices.value = manager.uvcDevices()
    }
    return devices
}
