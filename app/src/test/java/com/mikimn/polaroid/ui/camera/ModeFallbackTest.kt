package com.mikimn.polaroid.ui.camera

import com.mikimn.libpolaroid.StreamFormat
import com.mikimn.libpolaroid.StreamMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModeFallbackTest {
    private val a = StreamMode(StreamFormat.MJPEG, 1280, 720, 30)
    private val b = StreamMode(StreamFormat.YUYV, 640, 480, 15)

    @Test
    fun `label describes format size and rate`() {
        assertEquals("MJPEG 1280×720 @30", a.label())
    }

    @Test
    fun `a failure of the current mode falls back to the default and names that mode`() {
        val result = resolveStartFailure(failed = a, current = a, reason = "boom")
        assertEquals(StartFailure(null, "boom (MJPEG 1280×720 @30); using the default mode"), result)
    }

    @Test
    fun `a stale failure of a superseded mode is ignored`() {
        // A failed after the user already picked B: B must not be blamed or cleared.
        assertNull(resolveStartFailure(failed = a, current = b, reason = "boom"))
    }

    @Test
    fun `a stale failure of the default mode is ignored once a mode was picked`() {
        assertNull(resolveStartFailure(failed = null, current = b, reason = "boom"))
    }

    @Test
    fun `a failure of the default mode just shows the error without looping`() {
        assertEquals(StartFailure(null, "boom"), resolveStartFailure(failed = null, current = null, reason = "boom"))
    }
}
