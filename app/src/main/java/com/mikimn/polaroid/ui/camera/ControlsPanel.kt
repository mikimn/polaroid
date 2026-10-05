package com.mikimn.polaroid.ui.camera

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.mikimn.libpolaroid.ControlId
import com.mikimn.libpolaroid.StreamMode

/**
 * Lists every control the camera supports (nothing is hard-coded): dropdown for resolution & format selection,
 * sliders for ranged controls using the device's min/max/step, a switch for auto modes, buttons for the
 * auto-exposure modes the device offers, with the current value, a per-control reset and "Reset all".
 * A manual control is disabled while its auto mode owns it.
 */
@Composable
internal fun ControlsPanel(
    modes: List<StreamMode>,
    selectedMode: StreamMode?,
    onSelectMode: (StreamMode) -> Unit,
    state: ControlsState,
    modifier: Modifier = Modifier,
) {
    val available = state.available.value
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (modes.isNotEmpty()) {
            ModePicker(modes = modes, selected = selectedMode, onSelect = onSelectMode)
        }
        if (available.isEmpty()) {
            Text("This camera does not report any adjustable controls.", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = state::refresh) { Text("Refresh") }
            TextButton(onClick = state::resetAll) { Text("Reset all") }
        }
        for (id in available) ControlRow(id, state)
    }
}

@Composable
private fun ControlRow(id: ControlId, state: ControlsState) {
    val value = state.values[id] ?: return
    val editable = isEditable(id, state.values)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(id.label(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(formatValue(id, value), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { state.reset(id) }, enabled = editable) { Text("Reset") }
        }
        val range = state.ranges[id]
        val modes = state.options[id]
        when {
            modes != null -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (mode in modes.sorted()) {
                    val label = exposureModeName(mode)
                    if (mode == value) {
                        Button(onClick = { state.set(id, mode) }) { Text(label) }
                    } else {
                        OutlinedButton(onClick = { state.set(id, mode) }) { Text(label) }
                    }
                }
            }
            range != null && range.max == 1 && range.min == 0 && id in switches ->
                Switch(checked = value != 0, onCheckedChange = { state.set(id, if (it) 1 else 0) })
            range != null && range.max > range.min -> {
                val steps = ((range.max - range.min) / range.step - 1).coerceIn(0, 1000)
                Slider(
                    value = value.toFloat(),
                    onValueChange = { state.set(id, snap(it, range.min, range.step, range.max)) },
                    valueRange = range.min.toFloat()..range.max.toFloat(),
                    steps = steps,
                    enabled = editable,
                )
            }
            else -> Text("Fixed at ${formatValue(id, value)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private val switches = setOf(ControlId.AUTO_FOCUS, ControlId.AUTO_WHITE_BALANCE)

/** Rounds a slider position to the nearest multiple of the control's step (counted from its minimum). */
internal fun snap(raw: Float, min: Int, step: Int, max: Int): Int {
    val s = step.coerceAtLeast(1)
    return (min + ((raw - min) / s).roundToInt() * s).coerceIn(min, max)
}
