package com.mikimn.polaroid.ui.camera

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.mikimn.libpolaroid.StreamMode
import com.mikimn.libpolaroid.UvcCamera
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

sealed interface CameraState {
    object Opening : CameraState
    class Ready(val camera: UvcCamera, val modes: List<StreamMode>) : CameraState
    class Failed(val message: String) : CameraState
}

private sealed interface Opened {
    class Success(val connection: UsbDeviceConnection, val camera: UvcCamera, val modes: List<StreamMode>) : Opened
    class Failure(val message: String) : Opened
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
        // NonCancellable: if the composition leaves while opening, the result must not be dropped,
        // or the native camera / USB connection would leak. Both are closed in the finally block below.
        val opened = withContext(Dispatchers.IO + NonCancellable) {
            val connection = manager.openDevice(device)
                ?: return@withContext Opened.Failure("Could not open USB device")
            try {
                val camera = UvcCamera.open(connection.fileDescriptor)
                // Listing modes reads the camera's descriptors; do it here, off the main thread.
                val modes = try {
                    camera.supportedModes()
                } catch (e: Exception) {
                    camera.close() // otherwise the native handle would leak
                    throw e
                }
                Opened.Success(connection, camera, modes)
            } catch (e: Exception) {
                connection.close()
                Opened.Failure(e.message ?: "Could not open camera")
            }
        }
        if (opened is Opened.Failure) {
            state = CameraState.Failed(opened.message)
            return@LaunchedEffect
        }
        opened as Opened.Success
        state = CameraState.Ready(opened.camera, opened.modes)
        try {
            kotlinx.coroutines.awaitCancellation()
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                // The connection must outlive the camera, which uses its file descriptor.
                opened.camera.close()
                opened.connection.close()
            }
        }
    }
    return state
}
