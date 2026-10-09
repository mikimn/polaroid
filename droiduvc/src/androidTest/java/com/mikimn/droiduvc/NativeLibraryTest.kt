package com.mikimn.droiduvc

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Exercises the real JNI layer on a device. No camera is needed: these tests catch a library that
 * fails to load, JNI symbols that no longer match the Kotlin declarations, and error propagation.
 */
@RunWith(AndroidJUnit4::class)
class NativeLibraryTest {

    @Test
    fun nativeLibraryLoads() {
        // Touching the object runs System.loadLibrary("droiduvc").
        NativeUvc.toString()
    }

    @Test
    fun openingAnInvalidDescriptorThrowsIOException() {
        try {
            UvcCamera.open(-1)
            fail("Expected an IOException")
        } catch (expected: IOException) {
            // thrown by the native layer through JNI
        }
    }
}
