package com.mikimn.droiduvc.app.ui.camera

import com.mikimn.droiduvc.StreamMode

/** Human-readable description of a mode, e.g. `MJPEG 1280×720 @30`. */
internal fun StreamMode.label(): String = "$format ${width}×$height @$fps"

/** What to show after a failed start: the mode to request from now on (null = default) and a message. */
internal data class StartFailure(val requested: StreamMode?, val message: String)

/**
 * Decides how the UI reacts when starting [failed] (null = the camera's default mode) went wrong
 * while [current] is the mode the user has requested *now*.
 *
 * Returns null when the failure is stale: a start already in flight can fail after the user picked
 * another mode, and that failure says nothing about [current], so it must not be blamed on it.
 * Otherwise it falls back to the default mode, and the message names the mode that really failed.
 */
internal fun resolveStartFailure(failed: StreamMode?, current: StreamMode?, reason: String): StartFailure? = when {
    failed != current -> null
    failed == null -> StartFailure(null, reason)
    else -> StartFailure(null, "$reason (${failed.label()}); using the default mode")
}
