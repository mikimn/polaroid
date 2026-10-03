package com.mikimn.libpolaroid

import android.view.Surface
import java.io.IOException

/**
 * A UVC camera opened from a file descriptor obtained via
 * [android.hardware.usb.UsbDeviceConnection.getFileDescriptor]. The caller must keep that
 * connection open until [close] returns.
 */
class UvcCamera private constructor() : AutoCloseable {
    private var handle = 0L

    /** Streams frames to [surface] until [stop] or [close]. Throws [IOException] if unsupported. */
    @Synchronized
    fun start(surface: Surface, width: Int = 640, height: Int = 480, fps: Int = 30) {
        check(handle != 0L) { "Camera is closed" }
        nativeStart(handle, surface, width, height, fps)
    }

    @Synchronized
    fun stop() {
        if (handle != 0L) nativeStop(handle)
    }

    @Synchronized
    override fun close() {
        if (handle != 0L) nativeClose(handle)
        handle = 0L
    }

    private external fun nativeOpen(fd: Int): Long
    private external fun nativeStart(handle: Long, surface: Surface, width: Int, height: Int, fps: Int)
    private external fun nativeStop(handle: Long)
    private external fun nativeClose(handle: Long)

    companion object {
        init {
            System.loadLibrary("polaroid")
        }

        /** @throws IOException if the descriptor is not a usable UVC device. */
        fun open(fileDescriptor: Int): UvcCamera =
            UvcCamera().apply { handle = nativeOpen(fileDescriptor) }
    }
}
