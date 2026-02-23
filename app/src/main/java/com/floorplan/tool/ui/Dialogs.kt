package com.floorplan.tool.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CalibrationDialog(
    pixelDist: Double,
    currentScaleFactor: Double,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var metersText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calibration") },
        text = {
            Column {
                Text("Enter the real-world distance (meters) between the two points you selected.")
                Spacer(Modifier.height(8.dp))
                Text("Pixel distance: ${String.format("%.1f", pixelDist)} px",
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = metersText,
                    onValueChange = { metersText = it },
                    label = { Text("Distance (meters)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val m = metersText.toDoubleOrNull()
                if (m != null && m > 0) onConfirm(m) else onDismiss()
            }) { Text("Set Scale") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun EditDialog(
    label: String,
    details: String,
    onLabelChange: (String) -> Unit,
    onDetailsChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Annotation") },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = onLabelChange,
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = details,
                    onValueChange = onDetailsChange,
                    label = { Text("Details") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
