package com.mikimn.polaroid.ui.camera

import com.mikimn.libpolaroid.AutoExposureMode
import com.mikimn.libpolaroid.ControlId
import com.mikimn.libpolaroid.ControlRange
import java.util.Locale
import kotlin.math.roundToInt

/** Pure presentation rules for the controls panel (kept free of Compose so they are unit tested). */

/** Order in which controls are listed: each auto switch sits right above the manual control it governs. */
internal val controlDisplayOrder: List<ControlId> = listOf(
    ControlId.AUTO_EXPOSURE_MODE, ControlId.EXPOSURE_TIME,
    ControlId.AUTO_FOCUS, ControlId.FOCUS,
    ControlId.IRIS, ControlId.ZOOM, ControlId.PAN, ControlId.TILT,
    ControlId.AUTO_WHITE_BALANCE, ControlId.WHITE_BALANCE_TEMPERATURE,
    ControlId.BRIGHTNESS, ControlId.CONTRAST, ControlId.SATURATION, ControlId.SHARPNESS,
    ControlId.GAMMA, ControlId.HUE, ControlId.GAIN, ControlId.BACKLIGHT_COMPENSATION,
    ControlId.POWER_LINE_FREQUENCY,
)

internal fun ControlId.label(): String = when (this) {
    ControlId.AUTO_EXPOSURE_MODE -> "Auto-exposure mode"
    ControlId.EXPOSURE_TIME -> "Exposure time"
    ControlId.FOCUS -> "Focus"
    ControlId.AUTO_FOCUS -> "Auto focus"
    ControlId.IRIS -> "Iris"
    ControlId.ZOOM -> "Zoom"
    ControlId.PAN -> "Pan"
    ControlId.TILT -> "Tilt"
    ControlId.BACKLIGHT_COMPENSATION -> "Backlight compensation"
    ControlId.BRIGHTNESS -> "Brightness"
    ControlId.CONTRAST -> "Contrast"
    ControlId.GAIN -> "Gain"
    ControlId.POWER_LINE_FREQUENCY -> "Power-line frequency"
    ControlId.HUE -> "Hue"
    ControlId.SATURATION -> "Saturation"
    ControlId.SHARPNESS -> "Sharpness"
    ControlId.GAMMA -> "Gamma"
    ControlId.WHITE_BALANCE_TEMPERATURE -> "White balance"
    ControlId.AUTO_WHITE_BALANCE -> "Auto white balance"
}

/** The auto mode that, while active, overrides this manual control. */
internal fun ControlId.autoControl(): ControlId? = when (this) {
    ControlId.EXPOSURE_TIME -> ControlId.AUTO_EXPOSURE_MODE
    ControlId.FOCUS -> ControlId.AUTO_FOCUS
    ControlId.WHITE_BALANCE_TEMPERATURE -> ControlId.AUTO_WHITE_BALANCE
    else -> null
}

/**
 * Whether the slider of [id] can be used given the current [values]: manual controls are disabled while
 * the matching auto mode is on. A control whose auto mode is unsupported or unknown is always editable.
 */
internal fun isEditable(id: ControlId, values: Map<ControlId, Int>): Boolean {
    val auto = id.autoControl() ?: return true
    val autoValue = values[auto] ?: return true
    return when (auto) {
        // Exposure time only applies in the manual and shutter-priority modes.
        ControlId.AUTO_EXPOSURE_MODE ->
            autoValue == AutoExposureMode.MANUAL || autoValue == AutoExposureMode.SHUTTER_PRIORITY
        else -> autoValue == 0
    }
}

internal fun formatValue(id: ControlId, value: Int): String = when (id) {
    ControlId.EXPOSURE_TIME -> "%.1f ms".format(Locale.ROOT, value / 10.0) // units of 100 µs; fixed locale keeps the text comparable between devices
    ControlId.WHITE_BALANCE_TEMPERATURE -> "$value K"
    ControlId.AUTO_FOCUS, ControlId.AUTO_WHITE_BALANCE -> if (value == 0) "Off" else "On"
    ControlId.POWER_LINE_FREQUENCY -> when (value) {
        0 -> "Disabled"
        1 -> "50 Hz"
        2 -> "60 Hz"
        else -> "$value"
    }
    ControlId.AUTO_EXPOSURE_MODE -> exposureModeName(value)
    else -> "$value"
}

internal fun exposureModeName(mode: Int): String = when (mode) {
    AutoExposureMode.MANUAL -> "Manual"
    AutoExposureMode.AUTO -> "Auto"
    AutoExposureMode.SHUTTER_PRIORITY -> "Shutter priority"
    AutoExposureMode.APERTURE_PRIORITY -> "Aperture priority"
    else -> "Mode $mode"
}

/**
 * The zoom value after a pinch gesture of scale [zoomFactor] (1 = no change): a factor of 1.1 moves the value up
 * by 10 % of the control's range, so pinching out zooms in. The result is clamped to [range] and snapped to its step.
 */
internal fun pinchedZoom(current: Int, range: ControlRange, zoomFactor: Float): Int {
    val span = range.max - range.min
    val moved = (current + (zoomFactor - 1f) * span).roundToInt().coerceIn(range.min, range.max)
    val step = range.step.coerceAtLeast(1)
    return (range.min + ((moved - range.min) / step) * step).coerceIn(range.min, range.max)
}
