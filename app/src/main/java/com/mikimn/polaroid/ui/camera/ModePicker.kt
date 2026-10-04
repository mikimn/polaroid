package com.mikimn.polaroid.ui.camera

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikimn.libpolaroid.StreamMode

/** Horizontally scrolling list of [modes]; the [selected] one is highlighted. */
@Composable
fun ModePicker(
    modes: List<StreamMode>,
    selected: StreamMode?,
    onSelect: (StreamMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (mode in modes) {
            val label = mode.label()
            if (mode == selected) {
                Button(onClick = { onSelect(mode) }) { Text(label) }
            } else {
                OutlinedButton(onClick = { onSelect(mode) }) { Text(label) }
            }
        }
    }
}
