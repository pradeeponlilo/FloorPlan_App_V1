package com.floorplan.tool.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import com.floorplan.tool.model.PointD
import com.floorplan.tool.renderer.FloorPlanRenderer
import com.floorplan.tool.renderer.LabelRect
import com.floorplan.tool.renderer.ModeHelperState
import com.floorplan.tool.util.TransformUtil
import com.floorplan.tool.viewmodel.FloorPlanState
import com.floorplan.tool.viewmodel.FloorPlanViewModel
import com.floorplan.tool.viewmodel.Mode

@Composable
fun FloorPlanCanvas(
    state: FloorPlanState,
    baseBitmap: Bitmap?,
    vm: FloorPlanViewModel,
    onSizeChanged: (Int, Int) -> Unit
) {
    val renderer = remember { FloorPlanRenderer() }
    var labelRects by remember { mutableStateOf<List<LabelRect>>(emptyList()) }
    var viewW by remember { mutableIntStateOf(0) }
    var viewH by remember { mutableIntStateOf(0) }
    var initialFitDone by remember { mutableStateOf(false) }

    // Build mode helper state for rubber-band drawing
    val modeHelper = remember(state.mode, state.calA, state.calB, state.placeA,
        state.refA, state.refB, state.rrACur, state.rrBCur, state.cursorPlan) {
        buildModeHelperState(state)
    }

    // Drag state for label dragging
    var draggingLabelIndex by remember { mutableIntStateOf(-1) }
    var dragPrevPlan by remember { mutableStateOf(PointD(0.0, 0.0)) }

    // Pinch-to-zoom + pan (two-finger)
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        if (zoomChange != 1f) {
            vm.onPinchZoom(viewW / 2f, viewH / 2f, zoomChange)
        }
        if (panChange != Offset.Zero) {
            vm.onPan(panChange.x, panChange.y)
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                viewW = size.width
                viewH = size.height
                onSizeChanged(size.width, size.height)
                if (!initialFitDone) {
                    vm.fitToScreen(size.width, size.height)
                    initialFitDone = true
                }
            }
            .transformable(state = transformState)
            .pointerInput(state.mode, state.editorOpen) {
                detectTapGestures(
                    onTap = { offset ->
                        // First check label hit for selection
                        val hitLabel = labelRects.lastOrNull { rect ->
                            offset.x >= rect.screenRect.left && offset.x <= rect.screenRect.right &&
                            offset.y >= rect.screenRect.top && offset.y <= rect.screenRect.bottom
                        }
                        if (hitLabel != null && state.mode == Mode.NONE) {
                            vm.setSelected(hitLabel.index)
                        } else {
                            vm.onCanvasTap(offset.x, offset.y)
                        }
                    },
                    onLongPress = { offset ->
                        // Long press on label → edit
                        val hitLabel = labelRects.lastOrNull { rect ->
                            offset.x >= rect.screenRect.left && offset.x <= rect.screenRect.right &&
                            offset.y >= rect.screenRect.top && offset.y <= rect.screenRect.bottom
                        }
                        if (hitLabel != null) {
                            vm.setSelected(hitLabel.index)
                            vm.openEditor(hitLabel.index)
                        }
                    }
                )
            }
            .pointerInput(state.mode) {
                detectDragGestures(
                    onDragStart = { offset ->
                        // Check if starting on a label → drag label offset
                        val hitLabel = labelRects.lastOrNull { rect ->
                            offset.x >= rect.screenRect.left && offset.x <= rect.screenRect.right &&
                            offset.y >= rect.screenRect.top && offset.y <= rect.screenRect.bottom
                        }
                        if (hitLabel != null) {
                            draggingLabelIndex = hitLabel.index
                            val planPt = TransformUtil.screenToPlan(offset.x.toDouble(), offset.y.toDouble(), state.camera)
                            dragPrevPlan = planPt
                            vm.setSelected(hitLabel.index)
                        } else if (state.mode == Mode.PAN || state.mode == Mode.NONE) {
                            draggingLabelIndex = -1
                        } else {
                            draggingLabelIndex = -1
                        }
                    },
                    onDrag = { change, dragAmount ->
                        if (draggingLabelIndex >= 0) {
                            // Label dragging in plan coords
                            val planNow = TransformUtil.screenToPlan(
                                change.position.x.toDouble(), change.position.y.toDouble(), state.camera)
                            val dxPlan = planNow.x - dragPrevPlan.x
                            val dyPlan = planNow.y - dragPrevPlan.y
                            vm.onLabelDragDelta(draggingLabelIndex, dxPlan, dyPlan)
                            dragPrevPlan = planNow
                        } else if (state.mode == Mode.PAN || state.mode == Mode.NONE) {
                            vm.onPan(dragAmount.x, dragAmount.y)
                        }
                        // Update cursor for rubber-band rendering
                        vm.updateCursor(change.position.x, change.position.y)
                    },
                    onDragEnd = { draggingLabelIndex = -1 },
                    onDragCancel = { draggingLabelIndex = -1 }
                )
            }
    ) {
        val nativeCanvas = drawContext.canvas.nativeCanvas

        labelRects = renderer.drawScene(
            canvas = nativeCanvas,
            viewW = viewW,
            viewH = viewH,
            baseBitmap = baseBitmap,
            cam = state.camera,
            annotations = state.annotations,
            selectedIndex = state.selectedIndex,
            scaleFactor = state.scaleFactor,
            refAnchors = state.refAnchors,
            crosshairSize = state.crosshairSize,
            showGrid = state.showGrid,
            showA4 = state.showA4,
            gridMajor = state.gridMajor,
            gridMinor = state.gridMinor,
            gridLabelEvery = state.gridLabelEvery,
            modeHelperState = modeHelper
        )
    }
}

private fun buildModeHelperState(state: FloorPlanState): ModeHelperState? {
    return when (state.mode) {
        Mode.CALIBRATION -> state.calA?.let {
            ModeHelperState.Calibration(a = it, b = state.calB, cursor = state.cursorPlan)
        }
        Mode.PLACE1 -> state.placeA?.let {
            ModeHelperState.Place1(origin = it, cursor = state.cursorPlan)
        }
        Mode.PLACE2 -> state.refA?.let {
            ModeHelperState.Place2(refA = it, refB = state.refB, cursor = state.cursorPlan)
        }
        Mode.REREGISTER -> state.rrACur?.let {
            ModeHelperState.ReRegister(pickA = it, pickB = state.rrBCur, cursor = state.cursorPlan)
        }
        else -> null
    }
}
