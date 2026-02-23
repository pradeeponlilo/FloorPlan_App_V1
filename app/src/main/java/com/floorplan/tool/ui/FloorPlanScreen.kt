package com.floorplan.tool.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.floorplan.tool.viewmodel.FloorPlanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloorPlanScreen(vm: FloorPlanViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle messages as snackbar
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            vm.clearMessage()
        }
    }

    // SAF launchers
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val mime = context.contentResolver.getType(it) ?: ""
            if (mime == "application/pdf") vm.importPdf(context, it)
            else vm.importImage(context, it)
        }
    }

    val jsonImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { vm.importJson(context, it) } }

    val jsonExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? -> uri?.let { vm.exportJson(context, it) } }

    val pngExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri: Uri? -> uri?.let { vm.exportPng(context, it) } }

    val pdfExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? -> uri?.let { vm.exportPdf(context, it) } }

    // Track view size for fit-to-screen
    var viewSize by remember { mutableStateOf(Pair(0, 0)) }

    // Bottom sheet
    val sheetState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded)
    )

    BottomSheetScaffold(
        scaffoldState = sheetState,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        sheetPeekHeight = BottomSheetDefaults.SheetPeekHeight,
        sheetContent = {
            ToolsPanel(
                state = state,
                vm = vm,
                onImportFile = {
                    imagePickerLauncher.launch(arrayOf("image/*", "application/pdf"))
                },
                onImportJson = {
                    jsonImportLauncher.launch(arrayOf("application/json", "*/*"))
                },
                onExportJson = {
                    jsonExportLauncher.launch("${state.fileName}.json")
                },
                onExportPng = {
                    pngExportLauncher.launch("${state.fileName}.png")
                },
                onExportPdf = {
                    pdfExportLauncher.launch("${state.fileName}.pdf")
                }
            )
        },
        topBar = {
            QuickActionsBar(
                state = state,
                onFit = { vm.fitToScreen(viewSize.first, viewSize.second) },
                onZoomIn = { vm.zoomIn(viewSize.first, viewSize.second) },
                onZoomOut = { vm.zoomOut(viewSize.first, viewSize.second) },
                onToggleGrid = { vm.toggleGrid() },
                onToggleA4 = { vm.toggleA4() }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            FloorPlanCanvas(
                state = state,
                baseBitmap = vm.baseBitmap,
                vm = vm,
                onSizeChanged = { w, h -> viewSize = Pair(w, h) }
            )

            // HUD overlay
            HudOverlay(
                scaleFactor = state.scaleFactor,
                cameraScale = state.camera.scale,
                mode = state.mode
            )
        }
    }

    // Calibration dialog
    if (state.showCalibrationDialog) {
        CalibrationDialog(
            pixelDist = state.calibrationPixelDist,
            currentScaleFactor = state.scaleFactor,
            onConfirm = { meters -> vm.onCalibrationConfirm(meters) },
            onDismiss = { vm.onCalibrationCancel() }
        )
    }

    // Edit dialog
    if (state.showEditDialog) {
        EditDialog(
            label = state.editLabel,
            details = state.editDetails,
            onLabelChange = { vm.onEditLabelChange(it) },
            onDetailsChange = { vm.onEditDetailsChange(it) },
            onSave = { vm.onEditSave() },
            onDismiss = { vm.onEditCancel() }
        )
    }
}
