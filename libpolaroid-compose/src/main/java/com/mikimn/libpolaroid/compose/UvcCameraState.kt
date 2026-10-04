package com.mikimn.libpolaroid.compose

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.mikimn.libpolaroid.StreamMode
import com.mikimn.libpolaroid.UvcCamera
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext

/** The state of opening a USB camera, see [rememberUvcCamera]. */
@Stable
public sealed interface UvcCameraState {
    /** The device is being opened. */
    public object Opening : UvcCameraState

    /** The camera is open; [modes] are the modes it can stream (see [UvcCamera.supportedModes]). */
    public class Ready(public val camera: UvcCamera, public val modes: List<StreamMode>) : UvcCameraState

    /** The camera could not be opened. */
    public class Failed(public val message: String) : UvcCameraState
}

private sealed interface Opened {
    class Success(val connection: UsbDeviceConnection, val camera: UvcCamera, val modes: List<StreamMode>) : Opened
    class Failure(val message: String) : Opened
}

/**
 * Opens [device] (its permissions must already be granted, see [rememberUsbPermission]) and closes the camera and
 * its [UsbDeviceConnection] again when [device] changes or the calling composable leaves the composition. Opening
 * happens off the main thread.
 */
@Composable
public fun rememberUvcCamera(device: UsbDevice): UvcCameraState {
    val manager = LocalContext.current.getSystemService(UsbManager::class.java)
    var state by remember(device) { mutableStateOf<UvcCameraState>(UvcCameraState.Opening) }

    LaunchedEffect(device) {
        // NonCancellable: if the composition leaves while opening, the result must not be dropped, or the
        // native camera / USB connection would leak. Both are closed in the finally block below.
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
            state = UvcCameraState.Failed(opened.message)
            return@LaunchedEffect
        }
        opened as Opened.Success
        state = UvcCameraState.Ready(opened.camera, opened.modes)
        try {
            awaitCancellation()
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
