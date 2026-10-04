package com.mikimn.libpolaroid

import android.util.Log
import android.view.Surface
import java.io.IOException

/**
 * Pixel format of a camera stream. The codes are shared with the native layer
 * (`PixelFormatCode` in `core/stream_mode.h`); `UvcCameraTest` and `stream_mode_test.cpp` both pin
 * them so the two sides cannot drift apart silently.
 */
enum class StreamFormat(internal val code: Int) {
    MJPEG(0),
    YUYV(1),

    /** Any other format the camera advertises (NV12, H.264, ...); it cannot be rendered. */
    OTHER(2);

    /** True for the formats the library can convert for display (everything but [OTHER]). */
    val isRenderable: Boolean get() = this != OTHER

    internal companion object {
        fun fromCode(code: Int) = values().firstOrNull { it.code == code } ?: OTHER
    }
}

/** A stream mode: pixel format, size in pixels and frame rate. */
data class StreamMode(val format: StreamFormat, val width: Int, val height: Int, val fps: Int) {
    internal companion object {
        /** Reads one `{format, width, height, fps}` quadruple starting at [offset]. */
        fun fromInts(ints: IntArray, offset: Int = 0) =
            StreamMode(StreamFormat.fromCode(ints[offset]), ints[offset + 1], ints[offset + 2], ints[offset + 3])
    }
}

/**
 * A UVC camera opened from a file descriptor obtained via
 * [android.hardware.usb.UsbDeviceConnection.getFileDescriptor]. The caller must keep that
 * connection open until [close] returns.
 */
class UvcCamera internal constructor(private val native: UvcNative, private var handle: Long) : AutoCloseable {

    /**
     * The renderable modes the camera advertises ([StreamFormat.MJPEG] and [StreamFormat.YUYV]; a size
     * with several frame rates appears once per rate). Other formats are omitted because [start] cannot
     * display them.
     */
    @Synchronized
    fun supportedModes(): List<StreamMode> {
        check(handle != 0L) { "Camera is closed" }
        val ints = native.listModes(handle)
        return (0 until ints.size / 4).map { StreamMode.fromInts(ints, it * 4) }.filter { it.format.isRenderable }
    }

    /**
     * Streams frames to [surface] until [stop] or [close], returning the mode actually negotiated:
     * the closest the camera supports to the requested size and rate, using [preferredFormat] first
     * if given (default order: MJPEG, YUYV, anything else). Throws [IllegalArgumentException] for a
     * non-renderable [preferredFormat] and [IOException] on failure.
     */
    @Synchronized
    fun start(
        surface: Surface,
        width: Int = 640,
        height: Int = 480,
        fps: Int = 30,
        preferredFormat: StreamFormat? = null,
    ): StreamMode {
        check(handle != 0L) { "Camera is closed" }
        require(preferredFormat?.isRenderable != false) { "$preferredFormat streams cannot be displayed" }
        Log.i(TAG, "start(valid=${surface.isValid}, ${width}x$height@$fps, $preferredFormat)")
        return StreamMode.fromInts(native.start(handle, surface, width, height, fps, preferredFormat?.code ?: -1))
    }

    /** Streams the given [mode] (see [supportedModes]); returns the mode actually negotiated. */
    fun start(surface: Surface, mode: StreamMode): StreamMode =
        start(surface, mode.width, mode.height, mode.fps, mode.format)

    /** Stops streaming. Safe to call at any time, including before [start] or after [close]. */
    @Synchronized
    fun stop() {
        Log.i(TAG, "stop()")
        if (handle != 0L) native.stop(handle)
    }

    /** Releases the camera. Safe to call more than once. */
    @Synchronized
    override fun close() {
        if (handle != 0L) native.close(handle)
        handle = 0L
    }

    companion object {
        private const val TAG = "UvcCamera"

        /** @throws IOException if the descriptor is not a usable UVC device. */
        fun open(fileDescriptor: Int): UvcCamera = open(NativeUvc, fileDescriptor)

        internal fun open(native: UvcNative, fileDescriptor: Int): UvcCamera =
            UvcCamera(native, native.open(fileDescriptor))
    }
}
