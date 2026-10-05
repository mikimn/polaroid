package com.mikimn.polaroid.ui.camera

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikimn.libpolaroid.UvcCamera
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Camera info": the plain-text report of [camera] (identity, stream modes, every control with its range and
 * current value) that can be copied or shared and pasted into a bug report. It only needs the camera to be open, not
 * streaming.
 */
@Composable
internal fun CameraInfoDialog(camera: UvcCamera, onDismiss: () -> Unit) {
    // USB reads: off the main thread, once per time the dialog is opened.
    val report by produceState<String?>(null, camera) {
        value = withContext(Dispatchers.IO) {
            try {
                formatReport(camera.snapshot())
            } catch (e: Exception) {
                "Could not read the camera: ${e.message ?: e.javaClass.simpleName}\n"
            }
        }
    }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Camera info") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "The serial number is masked (last four characters only) so the report is safe to paste into a public issue.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = report ?: "Reading the camera…",
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                )
            }
        },
        confirmButton = {
            Column {
                TextButton(enabled = report != null, onClick = { clipboard.setText(AnnotatedString(report.orEmpty())) }) { Text("Copy") }
                TextButton(enabled = report != null, onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report.orEmpty())
                    context.startActivity(Intent.createChooser(send, "Share camera report"))
                }) { Text("Share") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
