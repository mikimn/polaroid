package com.mikimn.droiduvc.app.ui.camera

import com.mikimn.droiduvc.AutoExposureMode
import com.mikimn.droiduvc.CameraInfo
import com.mikimn.droiduvc.ControlId
import com.mikimn.droiduvc.StreamFormat
import com.mikimn.droiduvc.StreamMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraReportTest {
    private val info = CameraInfo(0x046d, 0x0825, "1.10", "Acme", "Cam 1", null)
    private val noInfo = CameraInfo(null, null, "1.00", null, null, null)

    private fun snapshot(modes: List<StreamMode>, controls: List<ControlReport>) = CameraSnapshot(info, modes, controls)

    @Test
    fun `report has the device identity with hex ids`() {
        val text = formatReport(snapshot(emptyList(), emptyList()))
        assertTrue(text.contains("Product:      Cam 1"))
        assertTrue(text.contains("Manufacturer: Acme"))
        assertTrue(text.contains("Serial:       unknown"))
        assertTrue(text.contains("Vendor ID:    0x046d"))
        assertTrue(text.contains("Product ID:   0x0825"))
        assertTrue(text.contains("UVC version:  1.10"))
    }

    @Test
    fun `modes are sorted so reports from different devices diff cleanly`() {
        val modes = listOf(
            StreamMode(StreamFormat.YUYV, 640, 480, 15),
            StreamMode(StreamFormat.MJPEG, 640, 480, 30),
            StreamMode(StreamFormat.MJPEG, 1280, 720, 30),
            StreamMode(StreamFormat.MJPEG, 1280, 720, 15),
        )
        val lines = formatReport(snapshot(modes, emptyList())).lines()
        val start = lines.indexOf("Stream modes (4)")
        assertEquals(
            listOf("  MJPEG 1280×720 @30", "  MJPEG 1280×720 @15", "  MJPEG 640×480 @30", "  YUYV 640×480 @15"),
            lines.subList(start + 1, start + 5),
        )
        // The same data in another order gives the same report.
        assertEquals(formatReport(snapshot(modes, emptyList())), formatReport(snapshot(modes.reversed(), emptyList())))
    }

    @Test
    fun `controls table lists supported controls with their ranges and unsupported ones as no`() {
        val controls = listOf(
            ControlReport(ControlId.ZOOM, supported = true, min = 100, max = 500, step = 10, default = 100, current = 300),
            ControlReport(ControlId.HUE, supported = false),
        )
        val text = formatReport(snapshot(emptyList(), controls))
        assertTrue(text.contains("Controls (1 of 2 supported)"))
        assertTrue(text.contains("Name                       Supported  Min      Max      Step   Default  Current"))
        assertTrue(text.contains("Zoom                       yes        100      500      10     100      300"))
        assertTrue(text.contains("Hue                        no         -        -        -      -        -"))
    }

    @Test
    fun `mode controls list their options and unreadable controls say why`() {
        val controls = listOf(
            ControlReport(
                ControlId.AUTO_EXPOSURE_MODE, supported = true, current = AutoExposureMode.AUTO,
                options = setOf(AutoExposureMode.MANUAL, AutoExposureMode.AUTO),
            ),
            ControlReport(ControlId.FOCUS, supported = true, error = "stalled"),
        )
        val text = formatReport(snapshot(emptyList(), controls))
        assertTrue(text.contains("options: Manual, Auto"))
        assertTrue(text.contains("(unreadable: stalled)"))
    }

    @Test
    fun `empty mode list and trailing newline`() {
        val text = formatReport(snapshot(emptyList(), emptyList()))
        assertTrue(text.contains("Stream modes (0)\n  none"))
        assertTrue(text.endsWith("\n") && !text.endsWith("\n\n"))
    }

    @Test
    fun `serial numbers are masked to the last four characters`() {
        assertEquals("****A1B2", maskSerial("00123456A1B2"))
        assertEquals("****", maskSerial("12"))
        assertEquals("****", maskSerial("1234"))
        assertEquals("unknown", maskSerial(null))
        assertEquals("unknown", maskSerial(""))
        val withSerial = CameraSnapshot(info.copy(serialNumber = "SN-9876-5432"), emptyList(), emptyList())
        val text = formatReport(withSerial)
        assertTrue(text.contains("Serial:       ****5432"))
        assertFalse(text.contains("SN-9876"))
    }

    @Test
    fun `unreadable ids are reported as unknown instead of zeros`() {
        val text = formatReport(CameraSnapshot(noInfo, emptyList(), emptyList()))
        assertTrue(text.contains("Vendor ID:    unknown"))
        assertTrue(text.contains("Product ID:   unknown"))
    }
}
