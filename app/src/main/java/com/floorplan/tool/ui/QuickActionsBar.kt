package com.floorplan.tool.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.floorplan.tool.viewmodel.FloorPlanState
import com.floorplan.tool.viewmodel.Mode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionsBar(
    state: FloorPlanState,
    onFit: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onToggleGrid: () -> Unit,
    onToggleA4: () -> Unit
) {
    TopAppBar(
        title = {
            Text("Floor Plan Tool", style = MaterialTheme.typography.titleMedium)
        },
        actions = {
            IconButton(onClick = onFit) {
                Icon(Icons.Default.FitScreen, contentDescription = "Fit")
            }
            IconButton(onClick = onZoomIn) {
                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In")
            }
            IconButton(onClick = onZoomOut) {
                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out")
            }
            IconButton(onClick = onToggleGrid) {
                Icon(
                    Icons.Default.GridOn,
                    contentDescription = "Grid",
                    tint = if (state.showGrid) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onToggleA4) {
                Icon(
                    Icons.Default.Crop,
                    contentDescription = "A4",
                    tint = if (state.showA4) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun HudOverlay(
    scaleFactor: Double,
    cameraScale: Double,
    mode: Mode
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomStart
    ) {
        Surface(
            modifier = Modifier.padding(8.dp),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            tonalElevation = 2.dp
        ) {
            Text(
                text = "m/px: ${String.format("%.6f", scaleFactor)} | " +
                       "Zoom: ${String.format("%.2f", cameraScale)} | " +
                       "Mode: ${mode.name.lowercase()}",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
    }
}
