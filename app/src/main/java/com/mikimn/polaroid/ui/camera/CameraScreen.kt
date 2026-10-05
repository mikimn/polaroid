package com.mikimn.polaroid.ui.camera

import android.hardware.usb.UsbDevice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mikimn.libpolaroid.StreamMode
import com.mikimn.libpolaroid.compose.UvcCameraPreview
import com.mikimn.libpolaroid.compose.UvcCameraState
import com.mikimn.libpolaroid.compose.rememberIsStarted
import com.mikimn.libpolaroid.compose.rememberUsbPermission
import com.mikimn.libpolaroid.compose.rememberUvcCamera
import com.mikimn.libpolaroid.compose.rememberUvcDevices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = showControls,
                    onClick = { showControls = !showControls },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Controls") },
                    label = { Text("Controls") },
                )
                NavigationBarItem(
                    selected = showInfo,
                    onClick = { showInfo = true },
                    icon = { Icon(Icons.Default.Info, contentDescription = "Camera info") },
                    label = { Text("Camera info") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
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
                val activeMode = requested ?: state.modes.firstOrNull()
                activeMode?.let { mode ->
                    Text(
                        text = "Resolution & format: ${mode.width}×${mode.height} (${mode.format}, ${mode.fps} fps)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp),
                        textAlign = TextAlign.Center,
                    )
                }
                error?.let { Message(it) }
            }
            if (showControls) {
                controls?.let {
                    ModalBottomSheet(
                        onDismissRequest = { showControls = false },
                    ) {
                        ControlsPanel(
                            modes = state.modes,
                            selectedMode = requested,
                            onSelectMode = { requested = it; error = null },
                            state = it,
                        )
                    }
                }
            }
            if (showInfo) CameraInfoDialog(state.camera, onDismiss = { showInfo = false })
        }
    }
}

@Composable
private fun Message(text: String) = Text(text, modifier = Modifier.padding(16.dp))
