package com.mikimn.polaroid.ui.camera

import android.hardware.usb.UsbDevice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikimn.libpolaroid.StreamMode

/** Shows a preview of the first attached UVC camera, guiding the user through connecting it. */
@Composable
fun CameraScreen(modifier: Modifier = Modifier) {
    val devices by rememberUvcDevices()
    val device = devices.firstOrNull()
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (device == null) {
            Message("Connect a UVC camera via USB")
        } else {
            DevicePreview(device)
        }
    }
}

@Composable
private fun DevicePreview(device: UsbDevice) {
    val permission = rememberUsbPermission(device)
    if (!permission.granted) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Message(device.productName ?: "USB camera")
            Button(onClick = permission.request) { Text("Allow camera access") }
        }
        return
    }
    // Release the camera (and its USB connection) while the app is in the background.
    if (!rememberIsStarted()) return
    when (val state = rememberUvcCamera(device)) {
        CameraState.Opening -> Message("Opening camera…")
        is CameraState.Failed -> Message(state.message)
        is CameraState.Ready -> {
            // The preview stays composed on error so the SurfaceView (and its lifecycle) is unaffected.
            var error by remember(state) { mutableStateOf<String?>(null) }
            var requested by remember(state.camera) { mutableStateOf<StreamMode?>(null) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CameraPreview(
                    camera = state.camera,
                    modifier = Modifier.fillMaxWidth(),
                    requestedMode = requested,
                    onError = { failed, e ->
                        // Ignore failures of a mode the user has already moved on from.
                        resolveStartFailure(failed, requested, e.message ?: "Could not start preview")?.let {
                            requested = it.requested
                            error = it.message
                        }
                    },
                )
                ModePicker(modes = state.modes, selected = requested, onSelect = { requested = it; error = null })
                error?.let { Message(it) }
            }
        }
    }
}

@Composable
private fun Message(text: String) = Text(text, modifier = Modifier.padding(16.dp))
