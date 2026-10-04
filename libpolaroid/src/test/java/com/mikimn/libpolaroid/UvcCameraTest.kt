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
        private val packedSize: Long = (1280L shl 32) or 720L,
    ) : UvcNative {
        val calls = mutableListOf<String>()
        var failStart = false
        var failOpen = false

        override fun open(fileDescriptor: Int): Long {
            calls += "open($fileDescriptor)"
            if (failOpen) throw IOException("open failed")
            return handle
        }

        override fun start(handle: Long, surface: Surface, width: Int, height: Int, fps: Int): Long {
            calls += "start($handle,${width}x$height@$fps)"
            if (failStart) throw IOException("unsupported")
            return packedSize
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
    fun `start returns the negotiated size and forwards the request`() {
        val fake = FakeNative()
        val size = UvcCamera.open(fake, 7).start(surface, 640, 480, 30)
        assertEquals(StreamSize(1280, 720), size)
        assertEquals("start(42,640x480@30)", fake.calls.last())
    }

    @Test
    fun `start uses default mode when none is given`() {
        val fake = FakeNative()
        UvcCamera.open(fake, 7).start(surface)
        assertEquals("start(42,640x480@30)", fake.calls.last())
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

    @Test
    fun `unpack decodes width and height`() {
        assertEquals(StreamSize(1920, 1080), StreamSize.unpack((1920L shl 32) or 1080L))
        assertEquals(StreamSize(0, 0), StreamSize.unpack(0))
    }
}
