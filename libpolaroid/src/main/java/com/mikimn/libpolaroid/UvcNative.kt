package com.mikimn.libpolaroid

import android.view.Surface

/**
 * The native (JNI) operations behind [UvcCamera]. Extracted as an interface so the Kotlin
 * lifecycle logic can be unit tested on the JVM with a fake.
 *
 * Every function throws [java.io.IOException] on failure. [start] returns the negotiated size
 * packed as `(width shl 32) or height`.
 */
internal interface UvcNative {
    fun open(fileDescriptor: Int): Long
    fun start(handle: Long, surface: Surface, width: Int, height: Int, fps: Int): Long
    fun stop(handle: Long)
    fun close(handle: Long)
}

/** JNI implementation, see `uvc_camera.cpp`. */
internal object NativeUvc : UvcNative {
    init {
        System.loadLibrary("polaroid")
    }

    external override fun open(fileDescriptor: Int): Long
    external override fun start(handle: Long, surface: Surface, width: Int, height: Int, fps: Int): Long
    external override fun stop(handle: Long)
    external override fun close(handle: Long)
}
