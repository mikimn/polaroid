package com.mikimn.droiduvc.app.ui.camera

import com.mikimn.droiduvc.AutoExposureMode
import com.mikimn.droiduvc.ControlId
import com.mikimn.droiduvc.ControlRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlPresentationTest {
    @Test
    fun `every control has a label and a place in the display order`() {
        assertEquals(ControlId.values().toSet(), controlDisplayOrder.toSet())
        assertEquals(controlDisplayOrder.size, controlDisplayOrder.toSet().size)
        assertTrue(ControlId.values().all { it.label().isNotBlank() })
    }

    @Test
    fun `an auto control is listed right above the manual control it governs`() {
        for (id in ControlId.values()) {
            val auto = id.autoControl() ?: continue
            assertEquals(controlDisplayOrder.indexOf(id) - 1, controlDisplayOrder.indexOf(auto))
        }
    }

    @Test
    fun `focus and white balance are disabled while their auto switch is on`() {
        assertFalse(isEditable(ControlId.FOCUS, mapOf(ControlId.AUTO_FOCUS to 1)))
        assertTrue(isEditable(ControlId.FOCUS, mapOf(ControlId.AUTO_FOCUS to 0)))
        assertFalse(isEditable(ControlId.WHITE_BALANCE_TEMPERATURE, mapOf(ControlId.AUTO_WHITE_BALANCE to 1)))
        assertTrue(isEditable(ControlId.WHITE_BALANCE_TEMPERATURE, mapOf(ControlId.AUTO_WHITE_BALANCE to 0)))
    }

    @Test
    fun `exposure time is only editable in manual and shutter priority modes`() {
        fun editable(mode: Int) = isEditable(ControlId.EXPOSURE_TIME, mapOf(ControlId.AUTO_EXPOSURE_MODE to mode))
        assertTrue(editable(AutoExposureMode.MANUAL))
        assertTrue(editable(AutoExposureMode.SHUTTER_PRIORITY))
        assertFalse(editable(AutoExposureMode.AUTO))
        assertFalse(editable(AutoExposureMode.APERTURE_PRIORITY))
    }

    @Test
    fun `controls without a known auto mode are always editable`() {
        assertTrue(isEditable(ControlId.FOCUS, emptyMap()))
        assertTrue(isEditable(ControlId.ZOOM, mapOf(ControlId.AUTO_FOCUS to 1)))
        assertNull(ControlId.ZOOM.autoControl())
    }

    @Test
    fun `values are formatted with their unit`() {
        assertEquals("33.3 ms", formatValue(ControlId.EXPOSURE_TIME, 333))
        assertEquals("4500 K", formatValue(ControlId.WHITE_BALANCE_TEMPERATURE, 4500))
        assertEquals("On", formatValue(ControlId.AUTO_FOCUS, 1))
        assertEquals("Off", formatValue(ControlId.AUTO_WHITE_BALANCE, 0))
        assertEquals("50 Hz", formatValue(ControlId.POWER_LINE_FREQUENCY, 1))
        assertEquals("Auto", formatValue(ControlId.AUTO_EXPOSURE_MODE, AutoExposureMode.AUTO))
        assertEquals("Mode 16", exposureModeName(16))
        assertEquals("-3", formatValue(ControlId.BRIGHTNESS, -3))
    }

    @Test
    fun `pinching out zooms in by a fraction of the range`() {
        val range = ControlRange(min = 100, max = 500, step = 1, default = 100)
        assertEquals(240, pinchedZoom(200, range, 1.1f)) // +10 % of 400
        assertEquals(160, pinchedZoom(200, range, 0.9f))
        assertEquals(200, pinchedZoom(200, range, 1f))
    }

    @Test
    fun `pinched zoom is clamped and snapped to the step`() {
        val range = ControlRange(min = 100, max = 500, step = 10, default = 100)
        assertEquals(500, pinchedZoom(480, range, 2f))
        assertEquals(100, pinchedZoom(120, range, 0f))
        assertEquals(240, pinchedZoom(200, range, 1.11f)) // 244 snaps down to the step grid
    }

    @Test
    fun `slider positions snap to the nearest step`() {
        assertEquals(110, snap(106f, min = 100, step = 10, max = 500))
        assertEquals(100, snap(104f, min = 100, step = 10, max = 500))
        assertEquals(500, snap(499f, min = 100, step = 10, max = 500))
        assertEquals(7, snap(7.4f, min = 0, step = 1, max = 100))
    }
}
