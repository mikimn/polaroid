package com.mikimn.libpolaroid

import android.util.Log
import android.view.Surface
import java.io.IOException

/** Pixel format of a camera stream. */
enum class PixelFormat(internal val code: Int) {
    MJPEG(0),
    YUYV(1),
    OTHER(2);

    internal companion object {
        fun fromCode(code: Int) = values().firstOrNull { it.code == code } ?: OTHER
    }
}

/** A stream mode: pixel format, size in pixels and frame rate. */
data class StreamMode(val format: PixelFormat, val width: Int, val height: Int, val fps: Int) {
    internal companion object {
        /** Reads one `{format, width, height, fps}` quadruple starting at [offset]. */
        fun fromInts(ints: IntArray, offset: Int = 0) =
            StreamMode(PixelFormat.fromCode(ints[offset]), ints[offset + 1], ints[offset + 2], ints[offset + 3])
    }
}

/**
 * A UVC camera opened from a file descriptor obtained via
 * [android.hardware.usb.UsbDeviceConnection.getFileDescriptor]. The caller must keep that
 * connection open until [close] returns.
 */
class UvcCamera internal constructor(private val native: UvcNative, private var handle: Long) : AutoCloseable {

    /** Every mode the camera advertises (a size with several frame rates appears once per rate). */
    @Synchronized
    fun supportedModes(): List<StreamMode> {
        check(handle != 0L) { "Camera is closed" }
        val ints = native.listModes(handle)
        return (0 until ints.size / 4).map { StreamMode.fromInts(ints, it * 4) }
    }

    /**
     * Streams frames to [surface] until [stop] or [close], returning the mode actually negotiated:
     * the closest the camera supports to the requested size and rate, using [preferredFormat] first
     * if given (default order: MJPEG, YUYV, anything else). Throws [IOException] on failure.
     */
    @Synchronized
    fun start(
        surface: Surface,
        width: Int = 640,
        height: Int = 480,
        fps: Int = 30,
        preferredFormat: PixelFormat? = null,
    ): StreamMode {
        check(handle != 0L) { "Camera is closed" }
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
