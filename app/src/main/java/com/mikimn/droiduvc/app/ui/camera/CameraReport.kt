package com.mikimn.droiduvc.app.ui.camera

import com.mikimn.droiduvc.CameraInfo
import com.mikimn.droiduvc.ControlId
import com.mikimn.droiduvc.StreamMode
import com.mikimn.droiduvc.UvcCamera
import java.util.Locale

/** One row of the controls table: what the camera reports for a control. */
internal data class ControlReport(
    val id: ControlId,
    val supported: Boolean,
    val min: Int? = null,
    val max: Int? = null,
    val step: Int? = null,
    val default: Int? = null,
    val current: Int? = null,
    /** Supported values of a mode control (the auto-exposure mode) instead of a range. */
    val options: Set<Int>? = null,
    /** Why the details could not be read, e.g. a stalled request. */
    val error: String? = null,
)

/** Everything the "Camera info" screen shows, gathered once. */
internal data class CameraSnapshot(
    val info: CameraInfo,
    val modes: List<StreamMode>,
    val controls: List<ControlReport>,
)

/**
 * Reads the info, the stream modes and every control's details from the device. Does USB transfers: call it off the
 * main thread. Works without streaming.
 */
internal fun UvcCamera.snapshot(): CameraSnapshot {
    val controls = controls
    val rows = controlDisplayOrder.map { id ->
        val control = controls[id] ?: return@map ControlReport(id, supported = false)
        try {
            val range = control.range
            ControlReport(
                id, supported = true,
                min = range?.min, max = range?.max, step = range?.step, default = range?.default,
                current = control.value, options = control.options,
            )
        } catch (e: Exception) {
            ControlReport(id, supported = true, error = e.message ?: e.javaClass.simpleName)
        }
    }
    return CameraSnapshot(info, supportedModes(), rows)
}

/**
 * Plain-text report meant to be pasted into an issue. The layout is fixed (modes and controls always in the
 * same order, fixed-width columns, no timestamps or locale-dependent formatting) so two devices' reports
 * can be compared with a diff.
 */
internal fun formatReport(snapshot: CameraSnapshot): String = buildString {
    val info = snapshot.info
    appendLine("Droid UVC camera report")
    appendLine()
    appendLine("Product:      ${info.product ?: "unknown"}")
    appendLine("Manufacturer: ${info.manufacturer ?: "unknown"}")
    appendLine("Serial:       ${maskSerial(info.serialNumber)}")
    appendLine("Vendor ID:    ${hexId(info.vendorId)}")
    appendLine("Product ID:   ${hexId(info.productId)}")
    appendLine("UVC version:  ${info.uvcVersion}")
    appendLine()
    appendLine("Stream modes (${snapshot.modes.size})")
    val modes = snapshot.modes.sortedWith(
        compareBy<StreamMode> { it.format }.thenByDescending { it.width }.thenByDescending { it.height }.thenByDescending { it.fps },
    )
    if (modes.isEmpty()) appendLine("  none") else modes.forEach { appendLine("  ${it.label()}") }
    appendLine()
    appendLine("Controls (${snapshot.controls.count { it.supported }} of ${snapshot.controls.size} supported)")
    val widths = listOf(26, 10, 8, 8, 6, 8, 8)
    appendLine(row(widths, listOf("Name", "Supported", "Min", "Max", "Step", "Default", "Current")))
    for (control in snapshot.controls) {
        val name = control.id.label()
        when {
            !control.supported -> appendLine(row(widths, listOf(name, "no", "-", "-", "-", "-", "-")))
            control.error != null -> appendLine(row(widths, listOf(name, "yes", "-", "-", "-", "-", "-")) + "  (unreadable: ${control.error})")
            control.options != null ->
                appendLine(row(widths, listOf(name, "yes", "-", "-", "-", "-", control.current.text())) + "  options: " +
                    control.options.sorted().joinToString(", ") { exposureModeName(it) })
            else -> appendLine(row(widths, listOf(name, "yes", control.min.text(), control.max.text(), control.step.text(), control.default.text(), control.current.text())))
        }
    }
}.trimEnd() + "\n"

/** `0x046d`, or "unknown" when the device did not report it; fixed locale so digits never vary. */
private fun hexId(id: Int?): String = if (id == null) "unknown" else String.format(Locale.ROOT, "0x%04x", id)

/**
 * The report is meant to be pasted into public issues and a serial number identifies one physical unit, so only the
 * last four characters are kept ("****A1B2"); "unknown" when the device has none.
 */
internal fun maskSerial(serial: String?): String = when {
    serial.isNullOrEmpty() -> "unknown"
    serial.length <= 4 -> "****"
    else -> "****" + serial.takeLast(4)
}

private fun Int?.text(): String = this?.toString() ?: "-"

private fun row(widths: List<Int>, cells: List<String>): String =
    cells.mapIndexed { i, cell -> if (i == cells.lastIndex) cell else cell.padEnd(widths[i]) }.joinToString(" ").trimEnd()
