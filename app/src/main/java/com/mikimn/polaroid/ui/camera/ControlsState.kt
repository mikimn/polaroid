package com.mikimn.polaroid.ui.camera

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.mikimn.libpolaroid.AutoExposureMode
import com.mikimn.libpolaroid.CameraControls
import com.mikimn.libpolaroid.ControlId
import com.mikimn.libpolaroid.ControlRange
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One control as the panel needs it. An interface (with [Control] adapted by [toPort]) so [ControlsState]'s writer,
 * refresh and reset logic can be tested with a fake device. All members are blocking USB transfers.
 */
internal interface ControlPort {
    val range: ControlRange?
    val options: Set<Int>?
    fun read(): Int
    fun write(value: Int)
    fun reset()
}

internal fun com.mikimn.libpolaroid.Control.toPort(): ControlPort = object : ControlPort {
    override val range get() = this@toPort.range
    override val options get() = this@toPort.options
    override fun read() = value
    override fun write(value: Int) = set(value)
    override fun reset() = this@toPort.reset()
}

internal fun CameraControls.asLookup(): (ControlId) -> ControlPort? = { id -> this[id]?.toPort() }

/**
 * Hoisted state of the controls panel. Ranges and options are read once by [load], values are cached here (not
 * re-queried per frame) and written optimistically: [set] updates the cached value at once and a single writer
 * coroutine sends only the latest value per control, so dragging a slider cannot flood the USB endpoint. Control
 * transfers run on [io]; failures are reported through [onError] and the cached value is re-read from the device.
 *
 * Concurrency: `pending` and `writerRunning` are guarded by one lock. The writer clears `writerRunning` in the same
 * synchronized block in which it finds `pending` empty, and [set] starts a writer, under that lock, whenever it adds a
 * value and none is running, so the last value of a drag can never be left behind.
 */
@Stable
internal class ControlsState(
    private val lookup: (ControlId) -> ControlPort?,
    private val scope: CoroutineScope,
    private val onError: (String) -> Unit,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val main: CoroutineDispatcher = Dispatchers.Main,
) {
    /** Controls that could be read, in display order. */
    val available = mutableStateOf<List<ControlId>>(emptyList())

    /** Current values. */
    val values = mutableStateMapOf<ControlId, Int>()

    /** Ranges of ranged controls and switches (absent for the auto-exposure mode, which has [options]). */
    val ranges = mutableStateMapOf<ControlId, ControlRange>()
    val options = mutableStateMapOf<ControlId, Set<Int>>()

    private val lock = Any()
    private val pending = LinkedHashMap<ControlId, Int>() // guarded by lock
    private var writerRunning = false // guarded by lock
    private var refreshAfterWrites = false // guarded by lock: an auto mode was written, other values may have changed

    /** Reads the range/options and the current value of every supported control. */
    suspend fun load() {
        val loaded = withContext(io) {
            controlDisplayOrder.mapNotNull { id -> lookup(id)?.let { id to it } }.mapNotNull { (id, port) ->
                try {
                    Loaded(id, port.range, port.options, port.read())
                } catch (e: Exception) {
                    null // listed as supported but not readable: leave it out instead of failing the panel
                }
            }
        }
        for (item in loaded) {
            item.range?.let { ranges[item.id] = it }
            item.options?.let { options[item.id] = it }
            values[item.id] = item.value
        }
        available.value = loaded.map { it.id }
    }

    /** Re-reads every control's current value, e.g. after an auto mode changed what the device is doing. */
    fun refresh() {
        scope.launch {
            val fresh = withContext(io) {
                available.value.mapNotNull { id -> lookup(id)?.let { port -> runCatching { id to port.read() }.getOrNull() } }
            }
            // Do not overwrite a value the user has just changed and that has not been written yet.
            val unsent = synchronized(lock) { pending.keys.toSet() }
            fresh.forEach { (id, value) -> if (id !in unsent) values[id] = value }
        }
    }

    fun set(id: ControlId, value: Int) {
        values[id] = value
        val start = synchronized(lock) {
            pending[id] = value
            if (id.isAutoControl()) refreshAfterWrites = true
            (!writerRunning).also { if (it) writerRunning = true }
        }
        if (start) scope.launch(io) { writeLoop() }
    }

    private suspend fun writeLoop() {
        while (true) {
            var refreshNow = false
            val next = synchronized(lock) {
                val first = pending.entries.firstOrNull()
                if (first == null) {
                    writerRunning = false // cleared in the same block that found nothing left to send
                    refreshNow = refreshAfterWrites
                    refreshAfterWrites = false
                    null
                } else {
                    pending.remove(first.key)
                    first.key to first.value
                }
            }
            if (next == null) {
                if (refreshNow) refresh() // auto modes change other values: re-read once everything has gone out
                return
            }
            val (id, value) = next
            val port = lookup(id) ?: continue
            try {
                port.write(value)
            } catch (e: Exception) {
                withContext(main) { onError("${id.label()}: ${e.message ?: "write failed"}") }
                runCatching { port.read() }.getOrNull()?.let { actual -> withContext(main) { values[id] = actual } }
            }
        }
    }

    /** Maps a pinch gesture onto the zoom control, if the camera has one. */
    fun pinchZoom(zoomFactor: Float) {
        val range = ranges[ControlId.ZOOM] ?: return
        val current = values[ControlId.ZOOM] ?: return
        val next = pinchedZoom(current, range, zoomFactor)
        if (next != current) set(ControlId.ZOOM, next)
    }

    /** Restores the device default of [id]. Manual controls are only resettable while their auto mode is off. */
    fun reset(id: ControlId) {
        scope.launch {
            try {
                withContext(io) { lookup(id)?.reset() }
                values[id] = withContext(io) { lookup(id)?.read() } ?: return@launch
            } catch (e: Exception) {
                onError("${id.label()}: ${e.message ?: "reset failed"}")
            }
            if (id.isAutoControl()) refresh()
        }
    }

    /**
     * Restores every control to the device default. A normal camera defaults its auto modes to *on*, and the device
     * rejects writes to a manual control while its auto mode owns it, so for each manual control that has an auto
     * owner: switch the owner to manual, reset the manual control, then reset the owner to its own default.
     */
    fun resetAll() {
        scope.launch {
            val ids = available.value
            val owners = ids.filter { it.isAutoControl() }
            val owned = ids.filter { it.autoControl() in owners }
            val plain = ids - owners.toSet() - owned.toSet()
            withContext(io) {
                for (id in plain) resetQuietly(id)
                for (owner in owners) {
                    val manual = owned.filter { it.autoControl() == owner }
                    if (manual.isNotEmpty()) {
                        runStep(owner) { lookup(owner)?.write(manualValueFor(owner)) }
                        for (id in manual) resetQuietly(id)
                    }
                    resetQuietly(owner)
                }
            }
            refresh()
        }
    }

    private suspend fun resetQuietly(id: ControlId) = runStep(id) { lookup(id)?.reset() }

    private suspend fun runStep(id: ControlId, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            withContext(main) { onError("${id.label()}: ${e.message ?: "reset failed"}") }
        }
    }

    private class Loaded(val id: ControlId, val range: ControlRange?, val options: Set<Int>?, val value: Int)
}

/** The value that hands an auto control's manual counterpart back to the user. */
internal fun manualValueFor(auto: ControlId): Int = if (auto == ControlId.AUTO_EXPOSURE_MODE) AutoExposureMode.MANUAL else 0

/** The switches and modes that take a manual control away from the user. */
internal fun ControlId.isAutoControl(): Boolean =
    this == ControlId.AUTO_EXPOSURE_MODE || this == ControlId.AUTO_FOCUS || this == ControlId.AUTO_WHITE_BALANCE
