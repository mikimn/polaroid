package com.mikimn.libpolaroid.compose

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Whether the app may open a USB camera. [granted] is true once both the runtime CAMERA permission
 * (required by Android to open UVC devices) and the per-device USB permission are held; [request] asks for
 * whichever is missing, showing the system dialogs.
 */
@Immutable
public class UsbPermission(public val granted: Boolean, public val request: () -> Unit)

/** Tracks and requests the permissions needed to open [device]; recomposes when they are granted. */
@Composable
public fun rememberUsbPermission(device: UsbDevice): UsbPermission {
    val context = LocalContext.current
    val manager = context.getSystemService(UsbManager::class.java)
    val action = "${context.packageName}.USB_PERMISSION"

    fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun hasUsbPermission() = manager.hasPermission(device)

    val cameraGranted = remember { mutableStateOf(hasCameraPermission()) }
    val usbGranted = remember(device) { mutableStateOf(hasUsbPermission()) }

    fun requestUsbPermission() {
        // The system fills in extras on this intent, so it must be mutable (API 31+).
        val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val intent = Intent(action).setPackage(context.packageName)
        manager.requestPermission(device, PendingIntent.getBroadcast(context, 0, intent, flags))
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        cameraGranted.value = isGranted
        if (isGranted && !hasUsbPermission()) requestUsbPermission()
    }

    BroadcastEffect(action) { usbGranted.value = hasUsbPermission() }

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
