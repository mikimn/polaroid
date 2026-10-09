package com.mikimn.droiduvc

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito.mock
import java.io.IOException

class UvcCameraTest {

    private class FakeNative(
        private val handle: Long = 42L,
        private val negotiated: IntArray = intArrayOf(0, 1280, 720, 30),
        private val modes: IntArray = intArrayOf(0, 1280, 720, 30, 1, 640, 480, 15),
    ) : UvcNative {
        val calls = mutableListOf<String>()
        var failStart = false
        var failOpen = false

        override fun open(fileDescriptor: Int): Long {
            calls += "open($fileDescriptor)"
            if (failOpen) throw IOException("open failed")
            return handle
        }

        override fun listModes(handle: Long): IntArray {
            calls += "listModes($handle)"
            return modes
        }

        override fun start(handle: Long, surface: Surface, width: Int, height: Int, fps: Int, preferredFormat: Int): IntArray {
            calls += "start($handle,${width}x$height@$fps,format=$preferredFormat)"
            if (failStart) throw IOException("unsupported")
            return negotiated
        }

        var infoReads = 0

        override fun deviceInfo(handle: Long): IntArray {
            infoReads++
            return intArrayOf(0x046d, 0x0825, 0x0110)
        }

        override fun deviceStrings(handle: Long): Array<String?> = arrayOf("Acme", "Cam 1", null)

        override fun controlInfo(handle: Long): IntArray {
            calls += "controlInfo($handle)"
            return intArrayOf(1, 1 shl 9, 3, 1 shl 0) // zoom + brightness
        }

        override fun getControl(handle: Long, unit: Int, selector: Int, request: Int, length: Int): ByteArray =
            ByteArray(length)

        override fun setControl(handle: Long, unit: Int, selector: Int, data: ByteArray) {
            calls += "setControl($handle,$unit,$selector,${data.size})"
        }

        override fun stop(handle: Long) {
            calls += "stop($handle)"
        }

        override fun close(handle: Long) {
            calls += "close($handle)"
        }
    }

    private val surface = mock(Surface::class.java)

    @Test
    fun `open passes the file descriptor to native`() {
        val fake = FakeNative()
        UvcCamera.open(fake, 7)
        assertEquals(listOf("open(7)"), fake.calls)
    }

    @Test
    fun `open propagates IOException`() {
        val fake = FakeNative().apply { failOpen = true }
        assertThrows(IOException::class.java) { UvcCamera.open(fake, 7) }
    }

    @Test
    fun `start returns the negotiated mode and forwards the request`() {
        val fake = FakeNative()
        val mode = UvcCamera.open(fake, 7).start(surface, 640, 480, 30)
        assertEquals(StreamMode(StreamFormat.MJPEG, 1280, 720, 30), mode)
        assertEquals("start(42,640x480@30,format=-1)", fake.calls.last())
    }

    @Test
    fun `start forwards the preferred format`() {
        val fake = FakeNative()
        UvcCamera.open(fake, 7).start(surface, 640, 480, 30, StreamFormat.YUYV)
        assertEquals("start(42,640x480@30,format=1)", fake.calls.last())
    }

    @Test
    fun `start with a mode requests its size, rate and format`() {
        val fake = FakeNative()
        UvcCamera.open(fake, 7).start(surface, StreamMode(StreamFormat.YUYV, 640, 480, 15))
        assertEquals("start(42,640x480@15,format=1)", fake.calls.last())
    }

    @Test
    fun `supportedModes decodes the native list`() {
        val modes = UvcCamera.open(FakeNative(), 7).supportedModes()
        assertEquals(
            listOf(StreamMode(StreamFormat.MJPEG, 1280, 720, 30), StreamMode(StreamFormat.YUYV, 640, 480, 15)),
            modes,
        )
    }

    @Test
    fun `supportedModes omits formats that cannot be rendered`() {
        val fake = FakeNative(modes = intArrayOf(0, 1280, 720, 30, 2, 1280, 720, 30, 1, 640, 480, 15))
        val modes = UvcCamera.open(fake, 7).supportedModes()
        assertEquals(listOf(StreamFormat.MJPEG, StreamFormat.YUYV), modes.map { it.format })
    }

    @Test
    fun `start rejects a preferred format that cannot be rendered`() {
        val camera = UvcCamera.open(FakeNative(), 7)
        assertThrows(IllegalArgumentException::class.java) { camera.start(surface, 640, 480, 30, StreamFormat.OTHER) }
        assertThrows(IllegalArgumentException::class.java) {
            camera.start(surface, StreamMode(StreamFormat.OTHER, 640, 480, 30))
        }
    }

    @Test
    fun `format codes match the native layer`() {
        // Keep in sync with PixelFormatCode in cpp/core/stream_mode.h (pinned there by stream_mode_test.cpp).
        assertEquals(0, StreamFormat.MJPEG.code)
        assertEquals(1, StreamFormat.YUYV.code)
        assertEquals(2, StreamFormat.OTHER.code)
    }

    @Test
    fun `supportedModes after close throws`() {
        val camera = UvcCamera.open(FakeNative(), 7)
        camera.close()
        assertThrows(IllegalStateException::class.java) { camera.supportedModes() }
    }

    @Test
    fun `controls are built from the native control info and fail after close`() {
        val camera = UvcCamera.open(FakeNative(), 7)
        assertEquals(setOf(ControlId.ZOOM, ControlId.BRIGHTNESS), camera.controls.supported)
        assertEquals(camera.controls, camera.controls) // cached
        camera.close()
        assertThrows(IllegalStateException::class.java) { camera.controls }
    }

    @Test
    fun `info combines the native ids and strings`() {
        val info = UvcCamera.open(FakeNative(), 7).info
        assertEquals(CameraInfo(0x046d, 0x0825, "1.10", "Acme", "Cam 1", null), info)
    }

    @Test
    fun `info is read once and cached`() {
        val fake = FakeNative()
        val camera = UvcCamera.open(fake, 7)
        assertEquals(camera.info, camera.info)
        assertEquals(1, fake.infoReads)
    }

    @Test
    fun `unreadable ids become null`() {
        val info = CameraInfo.from(intArrayOf(-1, -1, 0x0200), arrayOf(null, null, null))
        assertEquals(null, info.vendorId)
        assertEquals(null, info.productId)
        assertEquals("2.00", info.uvcVersion)
    }

    @Test
    fun `uvc version is formatted from the bcd value`() {
        assertEquals("1.00", CameraInfo.from(intArrayOf(1, 2, 0x0100), arrayOf(null, null, null)).uvcVersion)
        assertEquals("1.50", CameraInfo.from(intArrayOf(1, 2, 0x0150), arrayOf(null, null, null)).uvcVersion)
        assertEquals("1.10", CameraInfo.from(intArrayOf(1, 2, 0x0110), arrayOf(null, null, null)).uvcVersion)
    }

    @Test
    fun `info after close throws`() {
        val camera = UvcCamera.open(FakeNative(), 7)
        camera.close()
        assertThrows(IllegalStateException::class.java) { camera.info }
    }

    @Test
    fun `control writes are forwarded to native with the camera handle`() {
        val fake = FakeNative()
        val camera = UvcCamera.open(fake, 7)
        // The fake reports zero for every read, so the range is 0..0 and only 0 can be written.
        camera.controls.zoom!!.set(0)
        assertEquals("setControl(42,1,11,2)", fake.calls.last())
    }

    @Test
    fun `unknown format codes decode as OTHER`() {
        assertEquals(StreamFormat.OTHER, StreamFormat.fromCode(2))
        assertEquals(StreamFormat.OTHER, StreamFormat.fromCode(99))
    }

    @Test
    fun `start uses default mode when none is given`() {
        val fake = FakeNative()
        UvcCamera.open(fake, 7).start(surface)
        assertEquals("start(42,640x480@30,format=-1)", fake.calls.last())
    }

    @Test
    fun `start propagates IOException`() {
        val fake = FakeNative().apply { failStart = true }
        val camera = UvcCamera.open(fake, 7)
        assertThrows(IOException::class.java) { camera.start(surface) }
    }

    @Test
    fun `start after close throws`() {
        val camera = UvcCamera.open(FakeNative(), 7)
        camera.close()
        assertThrows(IllegalStateException::class.java) { camera.start(surface) }
    }

    @Test
    fun `close is idempotent`() {
        val fake = FakeNative()
        val camera = UvcCamera.open(fake, 7)
        camera.close()
        camera.close()
        assertEquals(1, fake.calls.count { it.startsWith("close") })
    }

    @Test
    fun `stop before start and after close is safe`() {
        val fake = FakeNative()
        val camera = UvcCamera.open(fake, 7)
        camera.stop()
        camera.close()
        camera.stop()
        assertEquals(listOf("open(7)", "stop(42)", "close(42)"), fake.calls)
    }
}
