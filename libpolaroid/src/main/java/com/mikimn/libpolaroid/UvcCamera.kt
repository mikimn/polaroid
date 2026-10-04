package com.mikimn.libpolaroid

import android.util.Log
import android.view.Surface
import java.io.IOException

/** Width and height, in pixels, of a camera stream. */
data class StreamSize(val width: Int, val height: Int) {
    companion object {
        internal fun unpack(packed: Long) = StreamSize((packed shr 32).toInt(), (packed and 0xFFFFFFFFL).toInt())
    }
}

/**
 * A UVC camera opened from a file descriptor obtained via
 * [android.hardware.usb.UsbDeviceConnection.getFileDescriptor]. The caller must keep that
 * connection open until [close] returns.
 */
class UvcCamera internal constructor(private val native: UvcNative, private var handle: Long) : AutoCloseable {

    /**
     * Streams frames to [surface] until [stop] or [close], returning the size actually negotiated
     * (the closest the camera supports to the requested one). Throws [IOException] on failure.
     */
    @Synchronized
    fun start(surface: Surface, width: Int = 640, height: Int = 480, fps: Int = 30): StreamSize {
        check(handle != 0L) { "Camera is closed" }
        Log.i(TAG, "start(valid=${surface.isValid}, ${width}x$height@$fps)")
        return StreamSize.unpack(native.start(handle, surface, width, height, fps))
    }

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
