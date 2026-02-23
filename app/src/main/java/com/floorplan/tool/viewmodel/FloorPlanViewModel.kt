package com.floorplan.tool.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.floorplan.tool.model.*
import com.floorplan.tool.util.Camera
import com.floorplan.tool.util.TransformUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ── Interaction Modes ────────────────────────────────────────────────
enum class Mode {
    NONE, PAN, CALIBRATION, ANNOTATION, PLACE1, PLACE2, REREGISTER
}

// ── UI State ─────────────────────────────────────────────────────────
data class FloorPlanState(
    val mode: Mode = Mode.NONE,
    val annotations: List<Annotation> = emptyList(),
    val selectedIndex: Int? = null,
    val scaleFactor: Double = 1.0,
    val rrASaved: PointD? = null,
    val rrBSaved: PointD? = null,
    val crosshairSize: Int = 12,
    val camera: Camera = Camera(),
    val showGrid: Boolean = false,
    val showA4: Boolean = false,
    val gridMajor: Double = 1.0,
    val gridMinor: Double = 0.25,
    val gridLabelEvery: Double = 5.0,
    val exportScale: Float = 0.5f,
    val includeDetails: Boolean = true,
    val planWidth: Int = 1200,
    val planHeight: Int = 800,
    val editorOpen: Boolean = false,
    // Mode-specific temporary state
    val calA: PointD? = null,
    val calB: PointD? = null,
    val placeA: PointD? = null,
    val refA: PointD? = null,
    val refB: PointD? = null,
    val rrACur: PointD? = null,
    val rrBCur: PointD? = null,
    val pdfDpi: Int = 144,
    val fileName: String = "floorplan",
    val annoName: String = "",
    val annoDetails: String = "",
    val annoType: AnnoType = AnnoType.FLOOR,
    // Cursor for rubber-band rendering
    val cursorPlan: PointD? = null,
    // Snackbar / toast messages
    val message: String? = null,
    // Dialogs
    val showCalibrationDialog: Boolean = false,
    val calibrationPixelDist: Double = 0.0,
    val showEditDialog: Boolean = false,
    val editIndex: Int? = null,
    val editLabel: String = "",
    val editDetails: String = "",
) {
    val isCalibrated: Boolean
        get() = TransformUtil.isCalibrated(scaleFactor, rrASaved, rrBSaved)

    val refAnchors: Pair<PointD, PointD>?
        get() = if (rrASaved != null && rrBSaved != null) Pair(rrASaved, rrBSaved) else null
}

// ── ViewModel ────────────────────────────────────────────────────────
class FloorPlanViewModel : ViewModel() {

    private val _state = MutableStateFlow(FloorPlanState())
    val state: StateFlow<FloorPlanState> = _state.asStateFlow()

    // The base plan bitmap (not in state to avoid serialization)
    var baseBitmap: Bitmap? = null
        private set

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    // ── Mode ─────────────────────────────────────────────────────
    fun setMode(m: Mode) {
        _state.update { s ->
            if (s.editorOpen) return@update s
            s.copy(
                mode = m,
                calA = if (m == Mode.CALIBRATION) null else s.calA,
                calB = if (m == Mode.CALIBRATION) null else s.calB,
                placeA = if (m != Mode.PLACE1) null else s.placeA,
                refA = if (m != Mode.PLACE2) null else s.refA,
                refB = if (m != Mode.PLACE2) null else s.refB,
                rrACur = if (m != Mode.REREGISTER) null else s.rrACur,
                rrBCur = if (m != Mode.REREGISTER) null else s.rrBCur,
                cursorPlan = null,
                message = null
            )
        }
    }

    // ── Camera ───────────────────────────────────────────────────
    fun fitToScreen(viewW: Int, viewH: Int) {
        _state.update { s ->
            s.copy(camera = TransformUtil.fitToScreen(s.planWidth, s.planHeight, viewW, viewH))
        }
    }

    fun zoomIn(viewW: Int, viewH: Int) {
        _state.update { s ->
            s.copy(camera = TransformUtil.zoomAt(1.2, viewW / 2.0, viewH / 2.0, s.camera))
        }
    }

    fun zoomOut(viewW: Int, viewH: Int) {
        _state.update { s ->
            s.copy(camera = TransformUtil.zoomAt(1.0 / 1.2, viewW / 2.0, viewH / 2.0, s.camera))
        }
    }

    fun onPinchZoom(focusX: Float, focusY: Float, zoomFactor: Float) {
        _state.update { s ->
            s.copy(camera = TransformUtil.zoomAt(zoomFactor.toDouble(), focusX.toDouble(), focusY.toDouble(), s.camera))
        }
    }

    fun onPan(dx: Float, dy: Float) {
        _state.update { s ->
            s.copy(camera = s.camera.copy(tx = s.camera.tx + dx, ty = s.camera.ty + dy))
        }
    }

    // ── Cursor (for rubber-band rendering) ───────────────────────
    fun updateCursor(screenX: Float, screenY: Float) {
        _state.update { s ->
            val plan = TransformUtil.screenToPlan(screenX.toDouble(), screenY.toDouble(), s.camera)
            s.copy(cursorPlan = plan)
        }
    }

    // ── Taps (mode-dependent) ────────────────────────────────────
    fun onCanvasTap(screenX: Float, screenY: Float) {
        _state.update { s ->
            if (s.editorOpen) return@update s
            val planPt = TransformUtil.screenToPlan(screenX.toDouble(), screenY.toDouble(), s.camera)

            when (s.mode) {
                Mode.CALIBRATION -> handleCalibrationTap(s, planPt)
                Mode.ANNOTATION -> handleAnnotationTap(s, planPt)
                Mode.PLACE1 -> handlePlace1Tap(s, planPt)
                Mode.PLACE2 -> handlePlace2Tap(s, planPt)
                Mode.REREGISTER -> handleReRegisterTap(s, planPt)
                Mode.NONE -> handleNormalTap(s, planPt)
                Mode.PAN -> s // pan handled by gesture
            }
        }
    }

    private fun handleCalibrationTap(s: FloorPlanState, pt: PointD): FloorPlanState {
        return if (s.calA == null) {
            s.copy(calA = pt)
        } else {
            val pxDist = hypot(pt.x - s.calA.x, pt.y - s.calA.y)
            s.copy(
                calB = pt,
                showCalibrationDialog = true,
                calibrationPixelDist = pxDist
            )
        }
    }

    fun onCalibrationConfirm(meters: Double) {
        _state.update { s ->
            if (meters > 0 && s.calibrationPixelDist > 0) {
                val sf = meters / s.calibrationPixelDist
                s.copy(
                    scaleFactor = sf,
                    rrASaved = s.calA,
                    rrBSaved = s.calB,
                    showGrid = true,
                    showCalibrationDialog = false,
                    calA = null, calB = null,
                    mode = Mode.NONE,
                    message = "Scale set: ${String.format("%.6f", sf)} m/px"
                )
            } else {
                s.copy(showCalibrationDialog = false, calA = null, calB = null, mode = Mode.NONE)
            }
        }
    }

    fun onCalibrationCancel() {
        _state.update { it.copy(showCalibrationDialog = false, calA = null, calB = null, mode = Mode.NONE) }
    }

    private fun handleAnnotationTap(s: FloorPlanState, pt: PointD): FloorPlanState {
        val newAnno = createAnnotation(s, pt)
        return s.copy(annotations = s.annotations + newAnno)
    }

    private fun handlePlace1Tap(s: FloorPlanState, pt: PointD): FloorPlanState {
        return if (s.placeA == null) {
            s.copy(placeA = pt)
        } else {
            val newAnno = createAnnotation(s, pt)
            s.copy(annotations = s.annotations + newAnno, placeA = null, mode = Mode.NONE)
        }
    }

    private fun handlePlace2Tap(s: FloorPlanState, pt: PointD): FloorPlanState {
        return when {
            s.refA == null -> s.copy(refA = pt)
            s.refB == null -> s.copy(refB = pt)
            else -> {
                val newAnno = createAnnotation(s, pt)
                s.copy(annotations = s.annotations + newAnno, refA = null, refB = null, mode = Mode.NONE)
            }
        }
    }

    private fun handleReRegisterTap(s: FloorPlanState, pt: PointD): FloorPlanState {
        return if (s.rrACur == null) {
            s.copy(rrACur = pt)
        } else {
            if (s.rrASaved == null || s.rrBSaved == null) {
                return s.copy(message = "No saved reference anchors found.", rrACur = null, rrBCur = null, mode = Mode.NONE)
            }
            val result = TransformUtil.applyReRegister(s.rrASaved, s.rrBSaved, s.rrACur, pt, s.annotations)
            if (result != null) {
                s.copy(
                    annotations = result.first,
                    rrASaved = result.second.first,
                    rrBSaved = result.second.second,
                    rrACur = null, rrBCur = null,
                    mode = Mode.NONE,
                    message = "Re-register applied to all annotations."
                )
            } else {
                s.copy(message = "Invalid re-register points.", rrACur = null, rrBCur = null, mode = Mode.NONE)
            }
        }
    }

    private fun handleNormalTap(s: FloorPlanState, pt: PointD): FloorPlanState {
        // Hit test clusters
        val clusters = TransformUtil.clustersFromAnnotations(s.annotations)
        val hitRadius = (s.crosshairSize * 1.2) / s.camera.scale
        for ((_, list) in clusters) {
            val a = list[0]
            if (kotlin.math.abs(a.x - pt.x) <= hitRadius && kotlin.math.abs(a.y - pt.y) <= hitRadius) {
                val idx = s.annotations.indexOf(a)
                return s.copy(selectedIndex = idx)
            }
        }
        return s.copy(selectedIndex = null)
    }

    private fun createAnnotation(s: FloorPlanState, pt: PointD): Annotation {
        return Annotation(
            x = pt.x, y = pt.y,
            label = s.annoName.ifBlank { "Connector" },
            details = s.annoDetails,
            type = s.annoType,
            labelOffset = OffsetD(
                dx = (s.crosshairSize + 6).toDouble(),
                dy = -(s.crosshairSize + 6).toDouble()
            )
        )
    }

    // ── Selection / Delete ───────────────────────────────────────
    fun setSelected(index: Int?) {
        _state.update { it.copy(selectedIndex = index) }
    }

    fun deleteSelected() {
        _state.update { s ->
            if (s.selectedIndex == null) return@update s
            val newList = s.annotations.toMutableList().apply { removeAt(s.selectedIndex) }
            s.copy(annotations = newList, selectedIndex = null)
        }
    }

    // ── Label dragging ───────────────────────────────────────────
    fun onLabelDragDelta(index: Int, dxPlan: Double, dyPlan: Double) {
        _state.update { s ->
            val a = s.annotations.getOrNull(index) ?: return@update s
            val newOffset = OffsetD(a.labelOffset.dx + dxPlan, a.labelOffset.dy + dyPlan)
            val newList = s.annotations.toMutableList()
            newList[index] = a.copy(labelOffset = newOffset)
            s.copy(annotations = newList)
        }
    }

    // ── Edit dialog ──────────────────────────────────────────────
    fun openEditor(index: Int) {
        _state.update { s ->
            val a = s.annotations.getOrNull(index) ?: return@update s
            s.copy(
                showEditDialog = true, editorOpen = true,
                editIndex = index, editLabel = a.label, editDetails = a.details,
                selectedIndex = index
            )
        }
    }

    fun onEditLabelChange(v: String) { _state.update { it.copy(editLabel = v) } }
    fun onEditDetailsChange(v: String) { _state.update { it.copy(editDetails = v) } }

    fun onEditSave() {
        _state.update { s ->
            val idx = s.editIndex ?: return@update s
            val a = s.annotations.getOrNull(idx) ?: return@update s
            val newList = s.annotations.toMutableList()
            newList[idx] = a.copy(
                label = s.editLabel.ifBlank { "Connector" },
                details = s.editDetails
            )
            s.copy(annotations = newList, showEditDialog = false, editorOpen = false,
                editIndex = null, editLabel = "", editDetails = "")
        }
    }

    fun onEditCancel() {
        _state.update { it.copy(showEditDialog = false, editorOpen = false,
            editIndex = null, editLabel = "", editDetails = "") }
    }

    // ── Settings ─────────────────────────────────────────────────
    fun setCrosshairSize(size: Int) { _state.update { it.copy(crosshairSize = size.coerceIn(4, 40)) } }
    fun setAnnoName(v: String) { _state.update { it.copy(annoName = v) } }
    fun setAnnoDetails(v: String) { _state.update { it.copy(annoDetails = v) } }
    fun setAnnoType(t: AnnoType) { _state.update { it.copy(annoType = t) } }
    fun setFileName(v: String) { _state.update { it.copy(fileName = v) } }
    fun setExportScale(v: Float) { _state.update { it.copy(exportScale = v) } }
    fun setIncludeDetails(v: Boolean) { _state.update { it.copy(includeDetails = v) } }
    fun setPdfDpi(v: Int) { _state.update { it.copy(pdfDpi = v) } }

    fun toggleGrid() {
        _state.update { s ->
            if (!s.showGrid && !s.isCalibrated) {
                return@update s.copy(message = "Calibrate first (two points + meters).")
            }
            s.copy(showGrid = !s.showGrid)
        }
    }

    fun toggleA4() { _state.update { it.copy(showA4 = !it.showA4) } }
    fun setGridMajor(v: Double) { _state.update { it.copy(gridMajor = max(0.01, v)) } }
    fun setGridMinor(v: Double) { _state.update { it.copy(gridMinor = max(0.005, v)) } }
    fun setGridLabelEvery(v: Double) { _state.update { it.copy(gridLabelEvery = max(0.1, v)) } }
    fun clearMessage() { _state.update { it.copy(message = null) } }

    // ── Import Image ─────────────────────────────────────────────
    fun importImage(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bmp = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bmp != null) {
                    baseBitmap = bmp
                    _state.update { s ->
                        s.copy(planWidth = bmp.width, planHeight = bmp.height)
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(message = "Failed to load image: ${e.message}") }
            }
        }
    }

    // ── Import PDF ───────────────────────────────────────────────
    fun importPdf(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@launch
                val renderer = PdfRenderer(pfd)
                val page = renderer.openPage(0)
                val dpi = _state.value.pdfDpi
                val scaleRatio = dpi.toFloat() / 72f
                val w = (page.width * scaleRatio).roundToInt()
                val h = (page.height * scaleRatio).roundToInt()
                // Guard against huge bitmaps
                val maxDim = 4096
                val finalW = min(w, maxDim)
                val finalH = min(h, maxDim)
                val bmp = Bitmap.createBitmap(finalW, finalH, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                canvas.drawColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                renderer.close()
                pfd.close()
                baseBitmap = bmp
                _state.update { s ->
                    s.copy(planWidth = finalW, planHeight = finalH)
                }
            } catch (e: Exception) {
                _state.update { it.copy(message = "Failed to load PDF: ${e.message}") }
            }
        }
    }

    // ── Import JSON ──────────────────────────────────────────────
    fun importJson(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val text = inputStream?.bufferedReader()?.readText() ?: return@launch
                inputStream.close()
                val plan = json.decodeFromString<PlanDocument>(text)
                _state.update { s ->
                    s.copy(
                        annotations = plan.annotations,
                        scaleFactor = plan.scaleFactor,
                        rrASaved = plan.refAnchors?.a,
                        rrBSaved = plan.refAnchors?.b,
                        crosshairSize = plan.crosshairSize,
                        planWidth = plan.planWidth,
                        planHeight = plan.planHeight,
                        selectedIndex = null, editorOpen = false
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(message = "Invalid JSON: ${e.message}") }
            }
        }
    }

    // ── Export JSON ──────────────────────────────────────────────
    fun exportJson(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = _state.value
                val plan = PlanDocument(
                    version = 1,
                    planWidth = s.planWidth,
                    planHeight = s.planHeight,
                    scaleFactor = s.scaleFactor,
                    refAnchors = if (s.rrASaved != null && s.rrBSaved != null)
                        RefAnchors(s.rrASaved, s.rrBSaved) else null,
                    crosshairSize = s.crosshairSize,
                    annotations = s.annotations
                )
                val jsonStr = json.encodeToString(plan)
                val os = context.contentResolver.openOutputStream(uri)
                os?.write(jsonStr.toByteArray())
                os?.close()
                _state.update { it.copy(message = "JSON exported.") }
            } catch (e: Exception) {
                _state.update { it.copy(message = "Export JSON failed: ${e.message}") }
            }
        }
    }

    // ── Export PNG ────────────────────────────────────────────────
    fun exportPng(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = _state.value
                val scale = s.exportScale
                val detailsH = if (s.includeDetails) 180 else 0
                val w = (s.planWidth * scale).roundToInt()
                val h = (s.planHeight * scale).roundToInt() + detailsH

                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                val renderer = com.floorplan.tool.renderer.FloorPlanRenderer()
                renderer.renderForExport(
                    canvas, baseBitmap, s.planWidth, s.planHeight, scale,
                    s.annotations, s.scaleFactor, s.refAnchors,
                    s.crosshairSize, s.showGrid,
                    s.gridMajor, s.gridMinor, s.gridLabelEvery,
                    s.includeDetails
                )

                val os = context.contentResolver.openOutputStream(uri)
                bmp.compress(Bitmap.CompressFormat.PNG, 100, os!!)
                os.close()
                bmp.recycle()
                _state.update { it.copy(message = "PNG exported.") }
            } catch (e: Exception) {
                _state.update { it.copy(message = "Export PNG failed: ${e.message}") }
            }
        }
    }

    // ── Export PDF ────────────────────────────────────────────────
    fun exportPdf(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = _state.value
                val scale = s.exportScale
                val detailsH = if (s.includeDetails) 180 else 0
                val pdfW = (s.planWidth * scale).roundToInt()
                val pdfH = (s.planHeight * scale).roundToInt() + detailsH

                val pdfDoc = android.graphics.pdf.PdfDocument()
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pdfW, pdfH, 1).create()
                val page = pdfDoc.startPage(pageInfo)

                val renderer = com.floorplan.tool.renderer.FloorPlanRenderer()
                renderer.renderForExport(
                    page.canvas, baseBitmap, s.planWidth, s.planHeight, scale,
                    s.annotations, s.scaleFactor, s.refAnchors,
                    s.crosshairSize, s.showGrid,
                    s.gridMajor, s.gridMinor, s.gridLabelEvery,
                    s.includeDetails
                )
                pdfDoc.finishPage(page)

                val os = context.contentResolver.openOutputStream(uri)
                pdfDoc.writeTo(os!!)
                os.close()
                pdfDoc.close()
                _state.update { it.copy(message = "PDF exported.") }
            } catch (e: Exception) {
                _state.update { it.copy(message = "Export PDF failed: ${e.message}") }
            }
        }
    }

    // ── Test helpers (exposed for instrumented tests) ────────────
    fun setStateForTest(
        scaleFactor: Double? = null,
        rrASaved: PointD? = null,
        rrBSaved: PointD? = null,
        annotations: List<Annotation>? = null,
        planWidth: Int? = null,
        planHeight: Int? = null
    ) {
        _state.update { s ->
            s.copy(
                scaleFactor = scaleFactor ?: s.scaleFactor,
                rrASaved = rrASaved ?: s.rrASaved,
                rrBSaved = rrBSaved ?: s.rrBSaved,
                annotations = annotations ?: s.annotations,
                planWidth = planWidth ?: s.planWidth,
                planHeight = planHeight ?: s.planHeight
            )
        }
    }

    fun setBaseBitmapForTest(bmp: Bitmap) {
        baseBitmap = bmp
        _state.update { it.copy(planWidth = bmp.width, planHeight = bmp.height) }
    }
}
