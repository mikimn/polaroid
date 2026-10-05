package com.mikimn.polaroid.ui.camera

import android.hardware.usb.UsbDevice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.mikimn.libpolaroid.StreamMode
import com.mikimn.libpolaroid.compose.UvcCameraPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mikimn.libpolaroid.compose.UvcCameraState
import com.mikimn.libpolaroid.compose.rememberIsStarted
import com.mikimn.libpolaroid.compose.rememberUsbPermission
import com.mikimn.libpolaroid.compose.rememberUvcCamera
import com.mikimn.libpolaroid.compose.rememberUvcDevices

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
        UvcCameraState.Opening -> Message("Opening camera…")
        is UvcCameraState.Failed -> Message(state.message)
        is UvcCameraState.Ready -> CameraContent(state)
    }
}

/** The preview with its mode picker and (collapsible) controls panel, for an opened camera. */
@Composable
private fun CameraContent(state: UvcCameraState.Ready) {
    // The preview stays composed on error so the TextureView (and its lifecycle) is unaffected.
    var error by remember(state) { mutableStateOf<String?>(null) }
    var requested by remember(state.camera) { mutableStateOf<StreamMode?>(null) }
    var showControls by rememberSaveable { mutableStateOf(false) }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    // Hoisted per camera: values are cached here, not re-queried per frame. Built off the main thread because
    // reading `camera.controls` takes the camera lock, which a stream start can hold for up to a second.
    var controls by remember(state.camera) { mutableStateOf<ControlsState?>(null) }
    LaunchedEffect(state.camera) {
        val lookup = withContext(Dispatchers.IO) { state.camera.controls.asLookup() }
        controls = ControlsState(lookup, scope, onError = { message -> scope.launch { snackbar.showSnackbar(message) } })
            .also { it.load() }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            // Pinch to zoom is mapped onto the camera's zoom control, when it has one.
            // Only two-finger gestures are taken: a single finger still scrolls the surrounding column.
            Box(Modifier.pointerInput(controls) { detectPinch { zoom -> controls?.pinchZoom(zoom) } }) {
                UvcCameraPreview(
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
            }
            error?.let { Message(it) }
            Row {
                TextButton(onClick = { showControls = !showControls }) { Text(if (showControls) "Hide controls" else "Controls") }
                TextButton(onClick = { showInfo = true }) { Text("Camera info") }
            }
            if (showControls) {
                controls?.let {
                    ControlsPanel(
                        modes = state.modes,
                        selectedMode = requested,
                        onSelectMode = { requested = it; error = null },
                        state = it,
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        if (showInfo) CameraInfoDialog(state.camera, onDismiss = { showInfo = false })
    }
}

@Composable
private fun Message(text: String) = Text(text, modifier = Modifier.padding(16.dp))
