package com.mikimn.libpolaroid.compose

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.mikimn.libpolaroid.StreamMode
import com.mikimn.libpolaroid.UvcCamera
import java.util.concurrent.Executors

/**
 * Live preview of [camera] in [requestedMode] (the camera's closest default when null), streaming
 * exactly while the texture exists and sized to the stream's aspect ratio.
 *
 * A [TextureView] is used instead of a SurfaceView: on some devices a SurfaceView created inside
 * Compose never received its surface (blank preview) until the app was backgrounded and resumed,
 * whereas a TextureView is part of the normal view hierarchy and always draws. Changing
 * [requestedMode] restarts the stream on the same view (see [PreviewController]). [onError] receives the mode that
 * failed to start (null for the default) so callers can ignore failures of a mode they have already moved on from.
 */
@Composable
public fun UvcCameraPreview(
    camera: UvcCamera,
    modifier: Modifier = Modifier,
    requestedMode: StreamMode? = null,
    onError: (failed: StreamMode?, error: Exception) -> Unit = { _, _ -> },
    onTextureViewCreated: ((TextureView) -> Unit)? = null,
): Unit {
    val currentOnError by rememberUpdatedState(onError)
    // Until the stream size is known, assume 16:9; afterwards match the camera so the image is not stretched.
    var aspectRatio by remember(camera) { mutableStateOf(16f / 9f) }

    key(camera) {
        AndroidView(
            modifier = modifier.aspectRatio(aspectRatio),
            factory = { context ->
                TextureView(context).also { view ->
                    val controller = PreviewController(
                        camera = camera,
                        post = view::post,
                        onStarted = { mode -> aspectRatio = mode.width.toFloat() / mode.height },
                        onError = { failed, e -> currentOnError(failed, e) },
                    )
                    view.tag = controller
                    view.surfaceTextureListener = controller
                    onTextureViewCreated?.invoke(view)
                }
            },
            update = { view -> (view.tag as PreviewController).setMode(requestedMode) },
            onRelease = { view -> (view.tag as PreviewController).release() },
        )
    }
}

/**
 * Owns the stream lifecycle of one [TextureView].
 *
 * All camera calls that can block (negotiating a stream takes up to a second) run on one
 * single-thread executor, so starts, restarts and stops are strictly ordered with no reliance on
 * when Compose adds or removes views: a mode change queues "stop, then start" on that executor, and
 * destroying the texture queues a final stop and waits for it, so the surface is never used after
 * [onSurfaceTextureDestroyed] returns. State (`surface`, `mode`, `executor`) is only touched from
 * the main thread; `generation` lets queued starts that were superseded or whose surface is gone
 * skip themselves.
 */
private class PreviewController(
    private val camera: UvcCamera,
    private val post: (Runnable) -> Boolean,
    private val onStarted: (StreamMode) -> Unit,
    private val onError: (failed: StreamMode?, error: Exception) -> Unit,
) : TextureView.SurfaceTextureListener {
    private var surface: Surface? = null
    private var mode: StreamMode? = null
    private var executor = Executors.newSingleThreadExecutor()

    @Volatile
    private var generation = 0

    /** Switches to [newMode] (null = the camera's default), restarting the stream if it is running. */
    fun setMode(newMode: StreamMode?) {
        if (newMode == mode) return
        mode = newMode
        if (surface != null) startStream()
    }

    private fun startStream() {
        val target = surface ?: return
        val requested = mode
        val mine = ++generation
        executor.execute {
            if (mine != generation) return@execute
            try {
                camera.stop()
                val started = requested?.let { camera.start(target, it) } ?: camera.start(target)
                post(Runnable { onStarted(started) })
            } catch (e: Exception) {
                post(Runnable { onError(requested, e) })
            }
        }
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        surface = Surface(texture)
        if (executor.isShutdown) executor = Executors.newSingleThreadExecutor()
        startStream()
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        generation++ // skip any start that has not begun
        executor.submit { camera.stop() }.get() // runs after a start that is in flight
        executor.shutdown()
        surface?.release()
        surface = null
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    fun release() {
        camera.stop()
    }
}
