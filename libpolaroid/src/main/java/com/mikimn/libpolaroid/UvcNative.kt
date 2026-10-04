package com.mikimn.libpolaroid

import android.view.Surface

/**
 * The native (JNI) operations behind [UvcCamera]. Extracted as an interface so the Kotlin
 * lifecycle logic can be unit tested on the JVM with a fake.
 *
 * Every function throws [java.io.IOException] on failure. Modes are exchanged as `IntArray`s of
 * `{format, width, height, fps}` (format codes are those of [PixelFormat]); [listModes] returns
 * several concatenated, [start] returns the one negotiated.
 */
internal interface UvcNative {
    fun open(fileDescriptor: Int): Long
    fun listModes(handle: Long): IntArray
    fun start(handle: Long, surface: Surface, width: Int, height: Int, fps: Int, preferredFormat: Int): IntArray
    fun stop(handle: Long)
    fun close(handle: Long)
}

/** JNI implementation, see `uvc_camera.cpp`. */
internal object NativeUvc : UvcNative {
    init {
        System.loadLibrary("polaroid")
    }

    external override fun open(fileDescriptor: Int): Long
    external override fun listModes(handle: Long): IntArray
    external override fun start(handle: Long, surface: Surface, width: Int, height: Int, fps: Int, preferredFormat: Int): IntArray
    external override fun stop(handle: Long)
    external override fun close(handle: Long)
}
