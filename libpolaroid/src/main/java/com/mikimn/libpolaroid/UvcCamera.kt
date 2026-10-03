package com.mikimn.libpolaroid

import android.util.Log
import android.util.Size
import android.view.Surface
import java.io.IOException

/**
 * A UVC camera opened from a file descriptor obtained via
 * [android.hardware.usb.UsbDeviceConnection.getFileDescriptor]. The caller must keep that
 * connection open until [close] returns.
 */
class UvcCamera private constructor() : AutoCloseable {
    private var handle = 0L

    /**
     * Streams frames to [surface] until [stop] or [close], returning the size actually negotiated
     * (the closest the camera supports to the requested one). Throws [IOException] on failure.
     */
    @Synchronized
    fun start(surface: Surface, width: Int = 640, height: Int = 480, fps: Int = 30): Size {
        check(handle != 0L) { "Camera is closed" }
        Log.i(TAG, "start(valid=${surface.isValid}, ${width}x$height@$fps)")
        val packed = nativeStart(handle, surface, width, height, fps)
        return Size((packed shr 32).toInt(), (packed and 0xFFFFFFFFL).toInt())
    }

    @Synchronized
    fun stop() {
        Log.i(TAG, "stop()")
        if (handle != 0L) nativeStop(handle)
    }

    @Synchronized
    override fun close() {
        if (handle != 0L) nativeClose(handle)
        handle = 0L
    }

    private external fun nativeOpen(fd: Int): Long
    private external fun nativeStart(handle: Long, surface: Surface, width: Int, height: Int, fps: Int): Long
    private external fun nativeStop(handle: Long)
    private external fun nativeClose(handle: Long)

    companion object {
        private const val TAG = "UvcCamera"

        init {
            System.loadLibrary("polaroid")
        }

        /** @throws IOException if the descriptor is not a usable UVC device. */
        fun open(fileDescriptor: Int): UvcCamera =
            UvcCamera().apply { handle = nativeOpen(fileDescriptor) }
    }
}
