package com.mikimn.polaroid.ui.camera

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
private fun BroadcastEffect(
    vararg actions: String,
    flags: Int = ContextCompat.RECEIVER_EXPORTED,
    receiver: (Intent) -> Unit,
) {
    val context = LocalContext.current
    DisposableEffect(context, flags, *actions) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = receiver(intent)
        }
        val filter = IntentFilter().apply { actions.forEach(::addAction) }
        ContextCompat.registerReceiver(context, r, filter, flags)
        onDispose { context.unregisterReceiver(r) }
    }
}

/** The currently attached UVC devices, kept up to date as cameras are plugged in and out. */
@Composable
fun rememberUvcDevices(): State<List<UsbDevice>> {
    val manager = LocalContext.current.getSystemService(UsbManager::class.java)
    val devices = remember { mutableStateOf(manager.uvcDevices()) }
    BroadcastEffect(UsbManager.ACTION_USB_DEVICE_ATTACHED, UsbManager.ACTION_USB_DEVICE_DETACHED) {
        devices.value = manager.uvcDevices()
    }
    return devices
}

/** Permission state for opening a USB device; [request] shows the system dialog and updates [granted]. */
class UsbPermission(val granted: Boolean, val request: () -> Unit)

@Composable
fun rememberUsbPermission(device: UsbDevice): UsbPermission {
    val context = LocalContext.current
    val manager = context.getSystemService(UsbManager::class.java)

    fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun hasUsbPermission() = manager.hasPermission(device)

    val cameraGranted = remember { mutableStateOf(hasCameraPermission()) }
    val usbGranted = remember(device) { mutableStateOf(hasUsbPermission()) }

    fun requestUsbPermission() {
        val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        manager.requestPermission(device, PendingIntent.getBroadcast(context, 0, intent, flags))
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        cameraGranted.value = isGranted
        if (isGranted && !hasUsbPermission()) {
            requestUsbPermission()
        }
    }

    BroadcastEffect(ACTION_USB_PERMISSION) {
        usbGranted.value = hasUsbPermission()
    }

    val granted = cameraGranted.value && usbGranted.value

    return remember(device, granted) {
        UsbPermission(granted) {
            if (!hasCameraPermission()) {
                cameraLauncher.launch(Manifest.permission.CAMERA)
            } else if (!hasUsbPermission()) {
                requestUsbPermission()
            }
        }
    }
}

private const val ACTION_USB_PERMISSION = "com.mikimn.polaroid.USB_PERMISSION"
