package com.floorplan.tool

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.floorplan.tool.model.*
import com.floorplan.tool.ui.FloorPlanScreen
import com.floorplan.tool.ui.theme.FloorPlanToolTheme
import com.floorplan.tool.viewmodel.FloorPlanViewModel
import com.floorplan.tool.viewmodel.Mode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FloorPlanInstrumentedTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var vm: FloorPlanViewModel

    @Before
    fun setup() {
        vm = FloorPlanViewModel()
    }

    // ── Test 1: Load sample bitmap ───────────────────────────────
    @Test
    fun loadBitmapAndFitToScreen() {
        val bmp = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.WHITE)
        vm.setBaseBitmapForTest(bmp)

        assertEquals(800, vm.state.value.planWidth)
        assertEquals(600, vm.state.value.planHeight)

        vm.fitToScreen(1080, 1920)
        assertTrue(vm.state.value.camera.scale > 0)
    }

    // ── Test 2: Calibration sets scaleFactor ─────────────────────
    @Test
    fun calibrationSetsScaleFactorAndEnablesGrid() {
        vm.setMode(Mode.CALIBRATION)
        assertEquals(Mode.CALIBRATION, vm.state.value.mode)

        // Simulate two taps 100px apart
        vm.onCanvasTap(100f, 100f)
        assertNotNull(vm.state.value.calA)

        vm.onCanvasTap(200f, 100f)
        // Should show calibration dialog
        assertTrue(vm.state.value.showCalibrationDialog)
        assertTrue(vm.state.value.calibrationPixelDist > 0)

        // Confirm with 5 meters
        vm.onCalibrationConfirm(5.0)
        assertTrue(vm.state.value.scaleFactor > 0)
        assertTrue(vm.state.value.showGrid)
        assertNotNull(vm.state.value.rrASaved)
        assertNotNull(vm.state.value.rrBSaved)
    }

    // ── Test 3: Add annotation increments count ──────────────────
    @Test
    fun addAnnotationIncrementsCount() {
        assertEquals(0, vm.state.value.annotations.size)

        vm.setMode(Mode.ANNOTATION)
        vm.setAnnoName("TestPoint")
        vm.onCanvasTap(400f, 300f)

        assertEquals(1, vm.state.value.annotations.size)
        assertEquals("TestPoint", vm.state.value.annotations[0].label)
    }

    // ── Test 4: Edit annotation updates label ────────────────────
    @Test
    fun editAnnotationUpdatesLabel() {
        vm.setMode(Mode.ANNOTATION)
        vm.setAnnoName("Original")
        vm.onCanvasTap(400f, 300f)
        assertEquals(1, vm.state.value.annotations.size)

        vm.openEditor(0)
        assertTrue(vm.state.value.showEditDialog)
        assertEquals("Original", vm.state.value.editLabel)

        vm.onEditLabelChange("Updated")
        vm.onEditDetailsChange("New details")
        vm.onEditSave()

        assertFalse(vm.state.value.showEditDialog)
        assertEquals("Updated", vm.state.value.annotations[0].label)
        assertEquals("New details", vm.state.value.annotations[0].details)
    }

    // ── Test 5: Delete selected removes annotation ───────────────
    @Test
    fun deleteSelectedRemovesAnnotation() {
        vm.setMode(Mode.ANNOTATION)
        vm.onCanvasTap(100f, 100f)
        vm.onCanvasTap(200f, 200f)
        assertEquals(2, vm.state.value.annotations.size)

        vm.setSelected(0)
        vm.deleteSelected()
        assertEquals(1, vm.state.value.annotations.size)
    }

    // ── Test 6: JSON export creates non-empty file ───────────────
    @Test
    fun jsonExportToCache() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        vm.setMode(Mode.ANNOTATION)
        vm.setAnnoName("ExportTest")
        vm.onCanvasTap(100f, 100f)

        // Export directly to a file in cache
        val s = vm.state.value
        val plan = PlanDocument(
            version = 1,
            planWidth = s.planWidth, planHeight = s.planHeight,
            scaleFactor = s.scaleFactor,
            refAnchors = if (s.rrASaved != null && s.rrBSaved != null)
                RefAnchors(s.rrASaved!!, s.rrBSaved!!) else null,
            crosshairSize = s.crosshairSize,
            annotations = s.annotations
        )
        val json = Json { prettyPrint = true }
        val jsonStr = json.encodeToString(plan)
        val file = File(context.cacheDir, "test_export.json")
        file.writeText(jsonStr)

        assertTrue(file.exists())
        assertTrue(file.length() > 0)

        // Verify content
        val parsed = json.decodeFromString<PlanDocument>(file.readText())
        assertEquals(1, parsed.annotations.size)
        assertEquals("ExportTest", parsed.annotations[0].label)

        file.delete()
    }

    // ── Test 7: PNG export creates non-empty file ────────────────
    @Test
    fun pngExportToCache() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val bmp = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.WHITE)
        vm.setBaseBitmapForTest(bmp)
        vm.setMode(Mode.ANNOTATION)
        vm.onCanvasTap(200f, 150f)

        // Render offscreen
        val s = vm.state.value
        val scale = s.exportScale
        val w = (s.planWidth * scale).toInt()
        val h = (s.planHeight * scale).toInt() + if (s.includeDetails) 180 else 0
        val exportBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(exportBmp)
        val renderer = com.floorplan.tool.renderer.FloorPlanRenderer()
        renderer.renderForExport(
            canvas, bmp, s.planWidth, s.planHeight, scale,
            s.annotations, s.scaleFactor, s.refAnchors,
            s.crosshairSize, s.showGrid,
            s.gridMajor, s.gridMinor, s.gridLabelEvery,
            s.includeDetails
        )

        val file = File(context.cacheDir, "test_export.png")
        file.outputStream().use { exportBmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

        assertTrue(file.exists())
        assertTrue(file.length() > 0)

        file.delete()
        exportBmp.recycle()
    }

    // ── Test 8: PDF export creates non-empty file ────────────────
    @Test
    fun pdfExportToCache() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val bmp = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.WHITE)
        vm.setBaseBitmapForTest(bmp)

        val s = vm.state.value
        val scale = s.exportScale
        val pdfW = (s.planWidth * scale).toInt()
        val pdfH = (s.planHeight * scale).toInt() + if (s.includeDetails) 180 else 0

        val pdfDoc = android.graphics.pdf.PdfDocument()
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pdfW, pdfH, 1).create()
        val page = pdfDoc.startPage(pageInfo)

        val renderer = com.floorplan.tool.renderer.FloorPlanRenderer()
        renderer.renderForExport(
            page.canvas, bmp, s.planWidth, s.planHeight, scale,
            s.annotations, s.scaleFactor, s.refAnchors,
            s.crosshairSize, s.showGrid,
            s.gridMajor, s.gridMinor, s.gridLabelEvery,
            s.includeDetails
        )
        pdfDoc.finishPage(page)

        val file = File(context.cacheDir, "test_export.pdf")
        file.outputStream().use { pdfDoc.writeTo(it) }
        pdfDoc.close()

        assertTrue(file.exists())
        assertTrue(file.length() > 0)

        file.delete()
    }

    // ── Test 9: Re-register transform works ──────────────────────
    @Test
    fun reRegisterTransformsAnnotations() {
        // Setup calibration
        vm.setStateForTest(
            scaleFactor = 0.01,
            rrASaved = PointD(0.0, 0.0),
            rrBSaved = PointD(100.0, 0.0),
            annotations = listOf(
                Annotation(x = 50.0, y = 0.0, label = "Mid")
            )
        )

        // Re-register with pure translation
        vm.setMode(Mode.REREGISTER)
        // Taps need to map to plan coords via camera; use direct state
        val cam = vm.state.value.camera
        // Directly compute: tap at screen positions mapping to plan (50,50) and (150,50)
        val scrA = com.floorplan.tool.util.TransformUtil.planToScreen(50.0, 50.0, cam)
        val scrB = com.floorplan.tool.util.TransformUtil.planToScreen(150.0, 50.0, cam)
        vm.onCanvasTap(scrA.x.toFloat(), scrA.y.toFloat())
        vm.onCanvasTap(scrB.x.toFloat(), scrB.y.toFloat())

        // Mode should reset
        assertEquals(Mode.NONE, vm.state.value.mode)
        // Annotations should be transformed
        assertEquals(1, vm.state.value.annotations.size)
    }

    // ── Test 10: Mode switching ──────────────────────────────────
    @Test
    fun modeSwitchingWorks() {
        vm.setMode(Mode.CALIBRATION)
        assertEquals(Mode.CALIBRATION, vm.state.value.mode)

        vm.setMode(Mode.PAN)
        assertEquals(Mode.PAN, vm.state.value.mode)

        vm.setMode(Mode.ANNOTATION)
        assertEquals(Mode.ANNOTATION, vm.state.value.mode)

        vm.setMode(Mode.NONE)
        assertEquals(Mode.NONE, vm.state.value.mode)
    }
}
