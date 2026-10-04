package com.mikimn.polaroid.ui.camera

import com.mikimn.libpolaroid.AutoExposureMode
import com.mikimn.libpolaroid.ControlId
import com.mikimn.libpolaroid.ControlRange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ControlsStateTest {

    /** A fake control that logs every operation into a shared [log]. */
    private class FakePort(
        private val id: ControlId,
        private val log: MutableList<String>,
        var current: Int = 0,
        override val range: ControlRange? = ControlRange(0, 100, 1, 0),
        override val options: Set<Int>? = null,
    ) : ControlPort {
        var failWrites = false
        var failReads = false
        var onWrite: (Int) -> Unit = {}

        override fun read(): Int {
            if (failReads) error("unreadable")
            return current
        }

        override fun write(value: Int) {
            log += "write ${id.name}=$value"
            onWrite(value)
            if (failWrites) error("stalled")
            current = value
        }

        override fun reset() {
            log += "reset ${id.name}"
        }
    }

    private val log = mutableListOf<String>()
    private val errors = mutableListOf<String>()
    private val ports = mutableMapOf<ControlId, FakePort>()

    private fun port(id: ControlId, current: Int = 0, range: ControlRange? = ControlRange(0, 100, 1, 0), options: Set<Int>? = null) =
        FakePort(id, log, current, range, options).also { ports[id] = it }

    private fun TestScope.state(): ControlsState {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return ControlsState({ ports[it] }, this, { errors += it }, io = dispatcher, main = dispatcher)
    }

    @Test
    fun `load lists readable controls in display order and leaves unreadable ones out`() = runTest {
        port(ControlId.BRIGHTNESS, current = 5)
        port(ControlId.ZOOM, current = 120, range = ControlRange(100, 500, 10, 100))
        port(ControlId.FOCUS).failReads = true
        val state = state()
        state.load()
        assertEquals(listOf(ControlId.ZOOM, ControlId.BRIGHTNESS), state.available.value)
        assertEquals(120, state.values[ControlId.ZOOM])
        assertEquals(ControlRange(100, 500, 10, 100), state.ranges[ControlId.ZOOM])
    }

    @Test
    fun `dragging a slider writes only the latest value`() = runTest {
        port(ControlId.ZOOM)
        val state = state()
        state.load()
        state.set(ControlId.ZOOM, 10)
        state.set(ControlId.ZOOM, 20)
        state.set(ControlId.ZOOM, 30)
        assertEquals(30, state.values[ControlId.ZOOM]) // the UI sees the value at once
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("write ZOOM=30"), log)
    }

    @Test
    fun `a value set while the writer is busy is not lost`() = runTest {
        val zoom = port(ControlId.ZOOM)
        port(ControlId.BRIGHTNESS)
        val state = state()
        state.load()
        // While the first write is in flight, the user moves another slider.
        zoom.onWrite = { if (it == 5) state.set(ControlId.BRIGHTNESS, 9) }
        state.set(ControlId.ZOOM, 5)
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("write ZOOM=5", "write BRIGHTNESS=9"), log)
        // And the writer can be started again afterwards.
        state.set(ControlId.ZOOM, 6)
        testScheduler.advanceUntilIdle()
        assertEquals("write ZOOM=6", log.last())
    }

    @Test
    fun `a failed write is reported and the real value is read back`() = runTest {
        val zoom = port(ControlId.ZOOM, current = 50)
        val state = state()
        state.load()
        zoom.failWrites = true
        state.set(ControlId.ZOOM, 80)
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("Zoom: stalled"), errors)
        assertEquals(50, state.values[ControlId.ZOOM])
    }

    @Test
    fun `writing an auto mode re-reads the other values afterwards`() = runTest {
        val focus = port(ControlId.FOCUS, current = 10)
        port(ControlId.AUTO_FOCUS, current = 0, range = ControlRange(0, 1, 1, 1))
        val state = state()
        state.load()
        // Turning auto focus on makes the device move the focus by itself.
        ports.getValue(ControlId.AUTO_FOCUS).onWrite = { focus.current = 42 }
        state.set(ControlId.AUTO_FOCUS, 1)
        testScheduler.advanceUntilIdle()
        assertEquals(42, state.values[ControlId.FOCUS])
    }

    @Test
    fun `reset all hands manual controls back before resetting their auto owner`() = runTest {
        port(ControlId.BRIGHTNESS)
        port(ControlId.AUTO_FOCUS, current = 1, range = ControlRange(0, 1, 1, 1))
        port(ControlId.FOCUS)
        port(ControlId.AUTO_EXPOSURE_MODE, current = AutoExposureMode.AUTO, range = null,
            options = setOf(AutoExposureMode.MANUAL, AutoExposureMode.AUTO))
        port(ControlId.EXPOSURE_TIME)
        val state = state()
        state.load()
        log.clear()
        state.resetAll()
        testScheduler.advanceUntilIdle()
        assertEquals(
            listOf(
                "reset BRIGHTNESS",
                // auto-exposure owns exposure time: switch to manual, reset, then restore the mode's default
                "write AUTO_EXPOSURE_MODE=1", "reset EXPOSURE_TIME", "reset AUTO_EXPOSURE_MODE",
                "write AUTO_FOCUS=0", "reset FOCUS", "reset AUTO_FOCUS",
            ),
            log,
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `manual values and auto classification`() {
        assertEquals(AutoExposureMode.MANUAL, manualValueFor(ControlId.AUTO_EXPOSURE_MODE))
        assertEquals(0, manualValueFor(ControlId.AUTO_FOCUS))
        assertTrue(ControlId.AUTO_WHITE_BALANCE.isAutoControl())
        assertFalse(ControlId.ZOOM.isAutoControl())
    }
}
