package com.mikimn.libpolaroid

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
        assertEquals(StreamMode(PixelFormat.MJPEG, 1280, 720, 30), mode)
        assertEquals("start(42,640x480@30,format=-1)", fake.calls.last())
    }

    @Test
    fun `start forwards the preferred format`() {
        val fake = FakeNative()
        UvcCamera.open(fake, 7).start(surface, 640, 480, 30, PixelFormat.YUYV)
        assertEquals("start(42,640x480@30,format=1)", fake.calls.last())
    }

    @Test
    fun `start with a mode requests its size, rate and format`() {
        val fake = FakeNative()
        UvcCamera.open(fake, 7).start(surface, StreamMode(PixelFormat.YUYV, 640, 480, 15))
        assertEquals("start(42,640x480@15,format=1)", fake.calls.last())
    }

    @Test
    fun `supportedModes decodes the native list`() {
        val modes = UvcCamera.open(FakeNative(), 7).supportedModes()
        assertEquals(
            listOf(StreamMode(PixelFormat.MJPEG, 1280, 720, 30), StreamMode(PixelFormat.YUYV, 640, 480, 15)),
            modes,
        )
    }

    @Test
    fun `supportedModes after close throws`() {
        val camera = UvcCamera.open(FakeNative(), 7)
        camera.close()
        assertThrows(IllegalStateException::class.java) { camera.supportedModes() }
    }

    @Test
    fun `unknown format codes decode as OTHER`() {
        assertEquals(PixelFormat.OTHER, PixelFormat.fromCode(2))
        assertEquals(PixelFormat.OTHER, PixelFormat.fromCode(99))
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
