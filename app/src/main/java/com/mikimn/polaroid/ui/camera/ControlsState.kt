package com.mikimn.polaroid.ui.camera

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.mikimn.libpolaroid.CameraControls
import com.mikimn.libpolaroid.Control
import com.mikimn.libpolaroid.ControlId
import com.mikimn.libpolaroid.ControlRange
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hoisted state of the controls panel. Ranges and options are read once by [load], values are cached here (not
 * re-queried per frame) and written optimistically: [set] updates the cached value at once and a single writer
 * coroutine sends only the latest value per control, so dragging a slider cannot flood the USB endpoint. Control
 * transfers run on [io]; failures are reported through [onError] and the cached value is re-read from the device.
 */
@Stable
internal class ControlsState(
    private val controls: CameraControls,
    private val scope: CoroutineScope,
    private val onError: (String) -> Unit,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /** Controls that could be read, in display order. */
    val available = mutableStateOf<List<ControlId>>(emptyList())

    /** Current values. */
    val values = mutableStateMapOf<ControlId, Int>()

    /** Ranges of ranged controls and switches (null for the auto-exposure mode, which has [options]). */
    val ranges = mutableStateMapOf<ControlId, ControlRange>()
    val options = mutableStateMapOf<ControlId, Set<Int>>()

    private val pending = LinkedHashMap<ControlId, Int>()
    private var writer: Job? = null

    /** Reads the range/options and the current value of every supported control. */
    suspend fun load() {
        val loaded = withContext(io) {
            controlDisplayOrder.mapNotNull { id -> controls[id]?.let { id to it } }.mapNotNull { (id, control) ->
                try {
                    Loaded(id, control.range, control.options, control.value)
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
                available.value.mapNotNull { id -> controls[id]?.let { c -> runCatching { id to c.value }.getOrNull() } }
            }
            fresh.forEach { (id, value) -> if (id !in pending) values[id] = value }
        }
    }

    fun set(id: ControlId, value: Int) {
        values[id] = value
        synchronized(pending) { pending[id] = value }
        startWriter()
        if (id.isAutoControl()) afterWrites { refresh() } // auto modes change other values
    }

    /** Maps a pinch gesture onto the zoom control, if the camera has one. */
    fun pinchZoom(zoomFactor: Float) {
        val range = ranges[ControlId.ZOOM] ?: return
        val current = values[ControlId.ZOOM] ?: return
        val next = pinchedZoom(current, range, zoomFactor)
        if (next != current) set(ControlId.ZOOM, next)
    }

    fun reset(id: ControlId) {
        scope.launch {
            val control = controls[id] ?: return@launch
            try {
                withContext(io) { control.reset() }
                values[id] = withContext(io) { control.value }
            } catch (e: Exception) {
                onError("${id.label()}: ${e.message ?: "reset failed"}")
            }
            if (id.isAutoControl()) refresh()
        }
    }

    fun resetAll() {
        scope.launch {
            // Auto modes first, so manual controls are not rejected while an auto mode still owns them.
            val ordered = available.value.sortedBy { if (it.isAutoControl()) 0 else 1 }
            for (id in ordered) {
                val control = controls[id] ?: continue
                try {
                    withContext(io) { control.reset() }
                } catch (e: Exception) {
                    onError("${id.label()}: ${e.message ?: "reset failed"}")
                }
            }
            refresh()
        }
    }

    private fun startWriter() {
        if (writer?.isActive == true) return
        writer = scope.launch(io) {
            while (true) {
                val next = synchronized(pending) {
                    val first = pending.entries.firstOrNull() ?: return@launch
                    pending.remove(first.key)
                    first.key to first.value
                }
                val (id, value) = next
                val control: Control = controls[id] ?: continue
                try {
                    control.set(value)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { onError("${id.label()}: ${e.message ?: "write failed"}") }
                    runCatching { control.value }.getOrNull()?.let { actual -> withContext(Dispatchers.Main) { values[id] = actual } }
                }
            }
        }
    }

    private fun afterWrites(block: () -> Unit) {
        scope.launch {
            writer?.join()
            block()
        }
    }

    private class Loaded(val id: ControlId, val range: ControlRange?, val options: Set<Int>?, val value: Int)
}

/** The switches and modes that take a manual control away from the user. */
private fun ControlId.isAutoControl(): Boolean =
    this == ControlId.AUTO_EXPOSURE_MODE || this == ControlId.AUTO_FOCUS || this == ControlId.AUTO_WHITE_BALANCE
