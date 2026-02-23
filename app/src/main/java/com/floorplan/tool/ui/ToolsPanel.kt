package com.floorplan.tool.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.floorplan.tool.model.AnnoType
import com.floorplan.tool.viewmodel.FloorPlanState
import com.floorplan.tool.viewmodel.FloorPlanViewModel
import com.floorplan.tool.viewmodel.Mode

@Composable
fun ToolsPanel(
    state: FloorPlanState,
    vm: FloorPlanViewModel,
    onImportFile: () -> Unit,
    onImportJson: () -> Unit,
    onExportJson: () -> Unit,
    onExportPng: () -> Unit,
    onExportPdf: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Mode selection ───────────────────────────────────────
        Text("Modes", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ModeChip("Normal", Mode.NONE, state.mode) { vm.setMode(Mode.NONE) }
            ModeChip("Pan", Mode.PAN, state.mode) { vm.setMode(Mode.PAN) }
            ModeChip("Calibration", Mode.CALIBRATION, state.mode) { vm.setMode(Mode.CALIBRATION) }
            ModeChip("Annotation", Mode.ANNOTATION, state.mode) { vm.setMode(Mode.ANNOTATION) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ModeChip("Place 1 Ref", Mode.PLACE1, state.mode) { vm.setMode(Mode.PLACE1) }
            ModeChip("Place 2 Refs", Mode.PLACE2, state.mode) { vm.setMode(Mode.PLACE2) }
            ModeChip("Re-register", Mode.REREGISTER, state.mode) { vm.setMode(Mode.REREGISTER) }
        }
        Text("Mode: ${state.mode.name.lowercase()}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        HorizontalDivider()

        // ── Annotation settings ──────────────────────────────────
        Text("Annotation Settings", style = MaterialTheme.typography.titleSmall)

        Text("Crosshair Size: ${state.crosshairSize}", fontSize = 12.sp)
        Slider(
            value = state.crosshairSize.toFloat(),
            onValueChange = { vm.setCrosshairSize(it.toInt()) },
            valueRange = 4f..40f,
            steps = 35
        )

        OutlinedTextField(
            value = state.annoName,
            onValueChange = { vm.setAnnoName(it) },
            label = { Text("Annotation Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.annoDetails,
            onValueChange = { vm.setAnnoDetails(it) },
            label = { Text("Details (optional)") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )

        // Type dropdown
        var typeExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
            OutlinedTextField(
                value = when (state.annoType) {
                    AnnoType.FLOOR -> "Floor (🟣 Purple)"
                    AnnoType.WALL -> "Wall (🔵 Blue)"
                    AnnoType.ROOF -> "Roof (🟢 Green)"
                },
                onValueChange = {},
                readOnly = true,
                label = { Text("Placement Type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                DropdownMenuItem(text = { Text("Floor (🟣 Purple)") }, onClick = { vm.setAnnoType(AnnoType.FLOOR); typeExpanded = false })
                DropdownMenuItem(text = { Text("Wall (🔵 Blue)") }, onClick = { vm.setAnnoType(AnnoType.WALL); typeExpanded = false })
                DropdownMenuItem(text = { Text("Roof (🟢 Green)") }, onClick = { vm.setAnnoType(AnnoType.ROOF); typeExpanded = false })
            }
        }

        // Delete selected
        Button(
            onClick = { vm.deleteSelected() },
            enabled = state.selectedIndex != null,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Delete Selected") }

        HorizontalDivider()

        // ── Grid settings ────────────────────────────────────────
        Text("Grid Settings", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.gridMajor.toString(),
                onValueChange = { it.toDoubleOrNull()?.let { v -> vm.setGridMajor(v) } },
                label = { Text("Major (m)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = state.gridMinor.toString(),
                onValueChange = { it.toDoubleOrNull()?.let { v -> vm.setGridMinor(v) } },
                label = { Text("Minor (m)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = state.gridLabelEvery.toString(),
            onValueChange = { it.toDoubleOrNull()?.let { v -> vm.setGridLabelEvery(v) } },
            label = { Text("Labels every (m)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider()

        // ── Import ───────────────────────────────────────────────
        Text("Import", style = MaterialTheme.typography.titleSmall)

        // PDF DPI dropdown
        var dpiExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = dpiExpanded, onExpandedChange = { dpiExpanded = it }) {
            OutlinedTextField(
                value = when (state.pdfDpi) {
                    144 -> "144 — Fast"
                    200 -> "200 — Sharp"
                    300 -> "300 — Very Sharp"
                    else -> "${state.pdfDpi} DPI"
                },
                onValueChange = {},
                readOnly = true,
                label = { Text("PDF Quality (DPI)") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dpiExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = dpiExpanded, onDismissRequest = { dpiExpanded = false }) {
                DropdownMenuItem(text = { Text("144 — Fast") }, onClick = { vm.setPdfDpi(144); dpiExpanded = false })
                DropdownMenuItem(text = { Text("200 — Sharp") }, onClick = { vm.setPdfDpi(200); dpiExpanded = false })
                DropdownMenuItem(text = { Text("300 — Very Sharp") }, onClick = { vm.setPdfDpi(300); dpiExpanded = false })
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onImportFile, modifier = Modifier.weight(1f)) {
                Text("Import Image/PDF")
            }
            OutlinedButton(onClick = onImportJson, modifier = Modifier.weight(1f)) {
                Text("Import JSON")
            }
        }

        HorizontalDivider()

        // ── Export ───────────────────────────────────────────────
        Text("Export", style = MaterialTheme.typography.titleSmall)

        OutlinedTextField(
            value = state.fileName,
            onValueChange = { vm.setFileName(it) },
            label = { Text("File Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Export scale dropdown
        var scaleExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = scaleExpanded, onExpandedChange = { scaleExpanded = it }) {
            OutlinedTextField(
                value = "${(state.exportScale * 100).toInt()}%",
                onValueChange = {},
                readOnly = true,
                label = { Text("Export Scale") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = scaleExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = scaleExpanded, onDismissRequest = { scaleExpanded = false }) {
                listOf(0.5f to "50%", 0.75f to "75%", 1f to "100%", 1.5f to "150%", 2f to "200%", 3f to "300%").forEach { (v, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { vm.setExportScale(v); scaleExpanded = false })
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = state.includeDetails, onCheckedChange = { vm.setIncludeDetails(it) })
            Text("Include annotation details", fontSize = 13.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onExportJson, modifier = Modifier.weight(1f)) { Text("JSON") }
            Button(onClick = onExportPng, modifier = Modifier.weight(1f)) { Text("PNG") }
            Button(onClick = onExportPdf, modifier = Modifier.weight(1f)) { Text("PDF") }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "• Pinch to zoom · Drag to pan\n• Tap label to select · Long-press to edit\n• Drag label to reposition offset",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 15.sp
        )

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ModeChip(label: String, mode: Mode, current: Mode, onClick: () -> Unit) {
    FilterChip(
        selected = mode == current,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) }
    )
}
