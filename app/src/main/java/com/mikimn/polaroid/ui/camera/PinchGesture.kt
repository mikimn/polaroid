package com.mikimn.polaroid.ui.camera

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs

/**
 * Intercepts two-finger pinch gestures on the preview using [PointerEventPass.Initial] so that
 * pointer events are captured before underlying views consume them.
 */
internal suspend fun PointerInputScope.detectPinch(
    onGestureStart: () -> Unit = {},
    onZoom: (Float) -> Unit,
) {
    awaitEachGesture {
        var zoom = 1f
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop

        awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
        do {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                val zoomChange = event.calculateZoom()
                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1f - zoom) * centroidSize
                    if (zoomMotion > touchSlop) {
                        pastTouchSlop = true
                        onGestureStart()
                    }
                }
                if (pastTouchSlop && zoomChange != 1f) {
                    onZoom(zoomChange)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}
