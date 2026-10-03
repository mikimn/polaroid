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

/**
 * Live preview of [camera], streaming exactly while the texture exists, sized to the stream's aspect ratio.
 *
 * A [TextureView] is used instead of a SurfaceView: on some devices a SurfaceView created
 * inside Compose never received its surface (blank preview) until the app was backgrounded
 * and resumed, whereas a TextureView is part of the normal view hierarchy and always draws.
 * Streaming starts and stops directly in the listener callbacks, because the surface must
 * not be used after `onSurfaceTextureDestroyed` returns.
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
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        private var surface: Surface? = null

                        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                            val s = Surface(texture).also { surface = it }
                            try {
                                val size = camera.start(s)
                                aspectRatio = size.width.toFloat() / size.height
                            } catch (e: Exception) {
                                currentOnError(e)
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

                        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                            camera.stop()
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
