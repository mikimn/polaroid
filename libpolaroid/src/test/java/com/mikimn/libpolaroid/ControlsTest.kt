package com.mikimn.libpolaroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlsTest {

    /** In-memory device: values are stored per (unit, selector, request) as little-endian transfers. */
    private class FakeBackend : ControlBackend {
        val values = mutableMapOf<Triple<Int, Int, Int>, ByteArray>()
        val writes = mutableListOf<Triple<Int, Int, List<Byte>>>()
        var failure: ControlException? = null

        fun put(unit: Int, selector: Int, request: Int, value: Int, size: Int) {
            values[Triple(unit, selector, request)] = ByteArray(size) { (value shr (8 * it)).toByte() }
        }

        override fun get(unit: Int, selector: Int, request: Int, length: Int): ByteArray {
            failure?.let { throw it }
            return values[Triple(unit, selector, request)] ?: throw ControlException("stalled", -9)
        }

        override fun set(unit: Int, selector: Int, data: ByteArray) {
            failure?.let { throw it }
            writes += Triple(unit, selector, data.toList())
            values[Triple(unit, selector, Control.GET_CUR)] = data
        }
    }

    private val backend = FakeBackend()

    // Camera terminal id 1: zoom (bit 9), focus (5), auto focus (15), AE mode (1), pan/tilt (11).
    // Processing unit id 3: brightness (bit 0), contrast (1), white balance auto (12).
    private val controls = CameraControls(
        backend,
        intArrayOf(
            1, (1 shl 9) or (1 shl 5) or (1 shl 15) or (1 shl 1) or (1 shl 11),
            3, (1 shl 0) or (1 shl 1) or (1 shl 12),
        ),
    )

    private fun range(unit: Int, selector: Int, size: Int, min: Int, max: Int, res: Int, def: Int, cur: Int) {
        backend.put(unit, selector, Control.GET_MIN, min, size)
        backend.put(unit, selector, Control.GET_MAX, max, size)
        backend.put(unit, selector, Control.GET_RES, res, size)
        backend.put(unit, selector, Control.GET_DEF, def, size)
        backend.put(unit, selector, Control.GET_CUR, cur, size)
    }

    @Test
    fun `supported controls come from the unit bitmaps`() {
        assertEquals(
            setOf(
                ControlId.ZOOM, ControlId.FOCUS, ControlId.AUTO_FOCUS, ControlId.AUTO_EXPOSURE_MODE,
                ControlId.PAN, ControlId.TILT, ControlId.BRIGHTNESS, ControlId.CONTRAST, ControlId.AUTO_WHITE_BALANCE,
            ),
            controls.supported,
        )
    }

    @Test
    fun `unsupported controls are absent instead of failing`() {
        assertNull(controls.exposureTime)
        assertNull(controls[ControlId.HUE])
        assertFalse(controls.isSupported(ControlId.GAIN))
        assertNotNull(controls.zoom)
    }

    @Test
    fun `a missing terminal or unit reports no controls`() {
        val none = CameraControls(backend, intArrayOf(0, -1, 0, -1))
        assertTrue(none.supported.isEmpty())
    }

    @Test
    fun `range and value are read with the right requests and unit`() {
        range(unit = 1, selector = 0x0B, size = 2, min = 100, max = 500, res = 10, def = 100, cur = 300)
        val zoom = controls.zoom!!
        assertEquals(ControlRange(min = 100, max = 500, step = 10, default = 100), zoom.range)
        assertEquals(300, zoom.value)
    }

    @Test
    fun `signed controls decode negative values`() {
        range(unit = 3, selector = 0x02, size = 2, min = -64, max = 64, res = 1, def = 0, cur = -10)
        val brightness = controls.brightness!!
        assertEquals(-64, brightness.range!!.min)
        assertEquals(-10, brightness.value)
    }

    @Test
    fun `set writes little-endian bytes to the right unit and selector`() {
        range(unit = 1, selector = 0x0B, size = 2, min = 100, max = 500, res = 10, def = 100, cur = 100)
        controls.zoom!!.set(0x012C)
        assertEquals(Triple(1, 0x0B, listOf(0x2C.toByte(), 0x01.toByte())), backend.writes.single())
    }

    @Test
    fun `set encodes negative values in two's complement`() {
        range(unit = 3, selector = 0x02, size = 2, min = -64, max = 64, res = 1, def = 0, cur = 0)
        controls.brightness!!.set(-2)
        assertEquals(listOf(0xFE.toByte(), 0xFF.toByte()), backend.writes.single().third)
    }

    @Test
    fun `set rejects values outside the range without writing`() {
        range(unit = 1, selector = 0x0B, size = 2, min = 100, max = 500, res = 10, def = 100, cur = 100)
        val zoom = controls.zoom!!
        assertThrows(IllegalArgumentException::class.java) { zoom.set(501) }
        assertThrows(IllegalArgumentException::class.java) { zoom.set(99) }
        assertTrue(backend.writes.isEmpty())
    }

    @Test
    fun `reset writes the device default`() {
        range(unit = 1, selector = 0x0B, size = 2, min = 100, max = 500, res = 10, def = 150, cur = 400)
        controls.zoom!!.reset()
        assertEquals(listOf(150.toByte(), 0.toByte()), backend.writes.single().third)
    }

    @Test
    fun `switches are 0 or 1 and read their default`() {
        backend.put(1, 0x08, Control.GET_DEF, 1, 1)
        val autoFocus = controls.autoFocus!!
        assertEquals(ControlRange(0, 1, 1, 1), autoFocus.range)
        autoFocus.set(0)
        assertThrows(IllegalArgumentException::class.java) { autoFocus.set(2) }
        assertEquals(listOf<Byte>(0), backend.writes.single().third)
    }

    @Test
    fun `mode controls expose the supported options and validate against them`() {
        // GET_RES is a bitmask: manual (1) and aperture priority (8).
        backend.put(1, 0x02, Control.GET_RES, 0b1001, 1)
        val mode = controls.autoExposureMode!!
        assertNull(mode.range)
        assertEquals(setOf(AutoExposureMode.MANUAL, AutoExposureMode.APERTURE_PRIORITY), mode.options)
        mode.set(AutoExposureMode.MANUAL)
        assertThrows(IllegalArgumentException::class.java) { mode.set(AutoExposureMode.AUTO) }
    }

    @Test
    fun `pan and tilt share a transfer and keep each other's value`() {
        // 8 bytes: pan = 1000 (little-endian int32), tilt = -5.
        backend.values[Triple(1, 0x0D, Control.GET_CUR)] =
            byteArrayOf(0xE8.toByte(), 0x03, 0, 0, 0xFB.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        backend.values[Triple(1, 0x0D, Control.GET_MIN)] = ByteArray(8) { if (it % 4 == 3) 0xFF.toByte() else 0 }
        backend.values[Triple(1, 0x0D, Control.GET_MAX)] = ByteArray(8) { if (it % 4 == 3) 0 else 0xFF.toByte() }
        backend.values[Triple(1, 0x0D, Control.GET_RES)] = byteArrayOf(1, 0, 0, 0, 1, 0, 0, 0)
        backend.values[Triple(1, 0x0D, Control.GET_DEF)] = ByteArray(8)

        assertEquals(1000, controls.pan!!.value)
        assertEquals(-5, controls.tilt!!.value)

        controls.pan!!.set(2000)
        assertEquals(
            listOf(0xD0.toByte(), 0x07, 0, 0, 0xFB.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
            backend.writes.single().third,
        )
    }

    @Test
    fun `device errors surface as ControlException with a reason`() {
        backend.failure = ControlException("gone", -4)
        val e = assertThrows(ControlException::class.java) { controls.zoom!!.value }
        assertEquals(ControlException.Reason.DISCONNECTED, e.reason)
        assertEquals(ControlException.Reason.UNSUPPORTED_OR_INVALID, ControlException("x", -9).reason)
        assertEquals(ControlException.Reason.TIMEOUT, ControlException("x", -7).reason)
        assertEquals(ControlException.Reason.OTHER, ControlException("x", -1).reason)
    }

    @Test
    fun `short transfers fail clearly`() {
        backend.values[Triple(1, 0x0B, Control.GET_CUR)] = ByteArray(1)
        assertThrows(IllegalStateException::class.java) { controls.zoom!!.value }
    }
}
