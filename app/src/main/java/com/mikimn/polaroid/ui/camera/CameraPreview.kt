package com.mikimn.polaroid.ui.camera

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.mikimn.libpolaroid.UvcCamera

/** Live preview of [camera], streaming while the surface exists. */
@Composable
fun CameraPreview(
    camera: UvcCamera,
    modifier: Modifier = Modifier,
    onError: (Exception) -> Unit = {},
) {
    var surfaceHolder by remember { mutableStateOf<SurfaceHolder?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(
                    object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            surfaceHolder = holder
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                            surfaceHolder = holder
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            surfaceHolder = null
                        }
                    },
                )
            }
        },
    )

    val currentHolder = surfaceHolder
    DisposableEffect(camera, currentHolder) {
        if ((currentHolder != null) && currentHolder.surface.isValid) {
            try {
                camera.start(currentHolder.surface)
            } catch (e: Exception) {
                onError(e)
            }
        }
        onDispose {
            try {
                camera.stop()
            } catch (_: Exception) {
                // Ignore error during dispose
            }
        }
    }
}
