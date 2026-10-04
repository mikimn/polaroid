package com.mikimn.polaroid.ui.camera

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.mikimn.libpolaroid.UvcCamera
import java.util.concurrent.Executors

/**
 * Live preview of [camera], streaming exactly while the texture exists, sized to the stream's aspect ratio.
 *
 * A [TextureView] is used instead of a SurfaceView: on some devices a SurfaceView created
 * inside Compose never received its surface (blank preview) until the app was backgrounded
 * and resumed, whereas a TextureView is part of the normal view hierarchy and always draws.
 * Streaming starts (on a background thread) and stops (synchronously) in the listener callbacks, because
 * the surface must not be used after `onSurfaceTextureDestroyed` returns.
 */
@Composable
fun CameraPreview(
    camera: UvcCamera,
    modifier: Modifier = Modifier,
    onError: (Exception) -> Unit = {},
) {
    val currentOnError by rememberUpdatedState(onError)
    // Until the stream size is known, assume 16:9; afterwards match the camera so the image is not stretched.
    var aspectRatio by remember(camera) { mutableStateOf(16f / 9f) }

    key(camera) {
        AndroidView(
            modifier = modifier.aspectRatio(aspectRatio),
            factory = { context ->
                TextureView(context).apply {
                    val view = this
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        private var surface: Surface? = null

                        // Negotiating the stream takes up to a second, so `start` runs off the main thread.
                        // Both are recreated per texture, so a view that is detached and re-attached
                        // starts again instead of submitting to a shut-down executor.
                        private var starter = Executors.newSingleThreadExecutor()

                        @Volatile
                        private var destroyed = false

                        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                            val s = Surface(texture).also { surface = it }
                            destroyed = false
                            if (starter.isShutdown) starter = Executors.newSingleThreadExecutor()
                            starter.execute {
                                if (destroyed) return@execute
                                try {
                                    val size = camera.start(s)
                                    // Compose state and the error callback belong on the main thread.
                                    view.post { aspectRatio = size.width.toFloat() / size.height }
                                } catch (e: Exception) {
                                    view.post { currentOnError(e) }
                                }
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

                        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                            // Stop on the same single-thread executor: it runs after a start that is queued
                            // or in flight, so a start can never begin on the released surface, and the
                            // surface is not used after this method returns.
                            destroyed = true
                            starter.submit { camera.stop() }.get()
                            starter.shutdown()
                            surface?.release()
                            surface = null
                            return true
                        }

                        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                    }
                }
            },
            onRelease = { camera.stop() },
        )
    }
}
