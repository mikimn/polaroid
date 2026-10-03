package com.mikimn.polaroid.ui.camera

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.mikimn.libpolaroid.UvcCamera
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

sealed interface CameraState {
    object Opening : CameraState
    class Ready(val camera: UvcCamera) : CameraState
    class Failed(val message: String) : CameraState
}

/**
 * Opens [device] (permission must already be granted) and closes it again when it leaves
 * the composition or changes.
 */
@Composable
fun rememberUvcCamera(device: UsbDevice): CameraState {
    val manager = LocalContext.current.getSystemService(UsbManager::class.java)
    var state by remember(device) { mutableStateOf<CameraState>(CameraState.Opening) }

    LaunchedEffect(device) {
        val connection = manager.openDevice(device)
        if (connection == null) {
            state = CameraState.Failed("Could not open USB device")
            return@LaunchedEffect
        }
        val camera = try {
            // NonCancellable: if the composition leaves while opening, the result must not be
            // dropped, or the native camera would leak. It is closed in the finally block below.
            withContext(Dispatchers.IO + NonCancellable) { UvcCamera.open(connection.fileDescriptor) }
        } catch (e: Exception) {
            connection.close()
            state = CameraState.Failed(e.message ?: "Could not open camera")
            return@LaunchedEffect
        }
        state = CameraState.Ready(camera)
        try {
            kotlinx.coroutines.awaitCancellation()
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                // The connection must outlive the camera, which uses its file descriptor.
                camera.close()
                connection.close()
            }
        }
    }
    return state
}
