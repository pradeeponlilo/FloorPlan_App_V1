package com.floorplan.tool

import com.floorplan.tool.model.*
import com.floorplan.tool.util.Camera
import com.floorplan.tool.util.TransformUtil
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class TransformUtilTest {

    private val EPSILON = 1e-9

    // ── screenToPlan ∘ planToScreen round-trip ────────────────────
    @Test
    fun `planToScreen then screenToPlan returns original`() {
        val cam = Camera(scale = 2.5, tx = 100.0, ty = -50.0)
        val origX = 37.5
        val origY = 122.8

        val scr = TransformUtil.planToScreen(origX, origY, cam)
        val back = TransformUtil.screenToPlan(scr.x, scr.y, cam)

        assertEquals(origX, back.x, EPSILON)
        assertEquals(origY, back.y, EPSILON)
    }

    @Test
    fun `screenToPlan then planToScreen returns original`() {
        val cam = Camera(scale = 0.75, tx = -200.0, ty = 300.0)
        val sx = 450.0
        val sy = 120.0

        val plan = TransformUtil.screenToPlan(sx, sy, cam)
        val scr = TransformUtil.planToScreen(plan.x, plan.y, cam)

        assertEquals(sx, scr.x, EPSILON)
        assertEquals(sy, scr.y, EPSILON)
    }

    @Test
    fun `round trip with identity camera`() {
        val cam = Camera(scale = 1.0, tx = 0.0, ty = 0.0)
        val px = 500.0; val py = 300.0

        val scr = TransformUtil.planToScreen(px, py, cam)
        assertEquals(px, scr.x, EPSILON)
        assertEquals(py, scr.y, EPSILON)

        val back = TransformUtil.screenToPlan(scr.x, scr.y, cam)
        assertEquals(px, back.x, EPSILON)
        assertEquals(py, back.y, EPSILON)
    }

    // ── fitToScreen ──────────────────────────────────────────────
    @Test
    fun `fitToScreen computes correct scale and centering`() {
        val cam = TransformUtil.fitToScreen(1200, 800, 600, 400)
        // scale = min(600/1200, 400/800) * 0.98 = 0.5 * 0.98 = 0.49
        assertEquals(0.49, cam.scale, 0.01)
        // centered
        assertTrue(cam.tx > 0)
        assertTrue(cam.ty > 0)
    }

    // ── zoomAt ───────────────────────────────────────────────────
    @Test
    fun `zoomAt preserves point under cursor`() {
        val cam = Camera(scale = 1.0, tx = 50.0, ty = 50.0)
        val cx = 300.0; val cy = 200.0

        val planBefore = TransformUtil.screenToPlan(cx, cy, cam)
        val zoomed = TransformUtil.zoomAt(2.0, cx, cy, cam)
        val planAfter = TransformUtil.screenToPlan(cx, cy, zoomed)

        assertEquals(planBefore.x, planAfter.x, 0.001)
        assertEquals(planBefore.y, planAfter.y, 0.001)
    }

    @Test
    fun `zoomAt clamps scale to bounds`() {
        val cam = Camera(scale = 15.0, tx = 0.0, ty = 0.0)
        val zoomed = TransformUtil.zoomAt(2.0, 0.0, 0.0, cam)
        assertTrue(zoomed.scale <= 20.0)

        val tiny = Camera(scale = 0.15, tx = 0.0, ty = 0.0)
        val zoomedOut = TransformUtil.zoomAt(0.5, 0.0, 0.0, tiny)
        assertTrue(zoomedOut.scale >= 0.1)
    }

    // ── Re-register transform ────────────────────────────────────
    @Test
    fun `reRegister identity transform preserves positions`() {
        // Same anchors → should not move anything
        val a0 = PointD(100.0, 100.0)
        val b0 = PointD(200.0, 100.0)
        val annos = listOf(
            Annotation(x = 150.0, y = 150.0, label = "P1"),
            Annotation(x = 120.0, y = 180.0, label = "P2")
        )

        val result = TransformUtil.applyReRegister(a0, b0, a0, b0, annos)
        assertNotNull(result)

        val (transformed, anchors) = result!!
        assertEquals(annos.size, transformed.size)
        for (i in annos.indices) {
            assertEquals(annos[i].x, transformed[i].x, EPSILON)
            assertEquals(annos[i].y, transformed[i].y, EPSILON)
        }
    }

    @Test
    fun `reRegister with pure translation`() {
        val a0 = PointD(0.0, 0.0)
        val b0 = PointD(100.0, 0.0)
        val a1 = PointD(50.0, 50.0)
        val b1 = PointD(150.0, 50.0)

        val annos = listOf(Annotation(x = 50.0, y = 0.0, label = "Mid"))
        val result = TransformUtil.applyReRegister(a0, b0, a1, b1, annos)!!

        // Translation of (50,50), scale=1, rotation=0
        assertEquals(100.0, result.first[0].x, EPSILON)
        assertEquals(50.0, result.first[0].y, EPSILON)
    }

    @Test
    fun `reRegister with scale factor 2`() {
        val a0 = PointD(0.0, 0.0)
        val b0 = PointD(100.0, 0.0)
        val a1 = PointD(0.0, 0.0)
        val b1 = PointD(200.0, 0.0)

        val annos = listOf(Annotation(x = 50.0, y = 0.0, label = "Scaled"))
        val result = TransformUtil.applyReRegister(a0, b0, a1, b1, annos)!!

        // Scale 2x around origin
        assertEquals(100.0, result.first[0].x, EPSILON)
        assertEquals(0.0, result.first[0].y, EPSILON)
    }

    @Test
    fun `reRegister with 90 degree rotation`() {
        val a0 = PointD(0.0, 0.0)
        val b0 = PointD(100.0, 0.0)
        val a1 = PointD(0.0, 0.0)
        val b1 = PointD(0.0, 100.0) // 90° CCW

        val annos = listOf(Annotation(x = 100.0, y = 0.0, label = "Rotated"))
        val result = TransformUtil.applyReRegister(a0, b0, a1, b1, annos)!!

        // (100,0) rotated 90° around origin → (0, 100)
        assertEquals(0.0, result.first[0].x, 0.001)
        assertEquals(100.0, result.first[0].y, 0.001)
    }

    @Test
    fun `reRegister returns null for zero distance`() {
        val a0 = PointD(0.0, 0.0)
        val b0 = PointD(0.0, 0.0) // zero distance!
        val a1 = PointD(10.0, 10.0)
        val b1 = PointD(20.0, 20.0)

        val result = TransformUtil.applyReRegister(a0, b0, a1, b1, emptyList())
        assertNull(result)
    }

    @Test
    fun `reRegister updates saved anchors`() {
        val a0 = PointD(0.0, 0.0)
        val b0 = PointD(100.0, 0.0)
        val a1 = PointD(10.0, 10.0)
        val b1 = PointD(110.0, 10.0)

        val result = TransformUtil.applyReRegister(a0, b0, a1, b1, emptyList())!!
        assertEquals(a1.x, result.second.first.x, EPSILON)
        assertEquals(a1.y, result.second.first.y, EPSILON)
        assertEquals(b1.x, result.second.second.x, EPSILON)
        assertEquals(b1.y, result.second.second.y, EPSILON)
    }

    // ── Clustering ───────────────────────────────────────────────
    @Test
    fun `clustersFromAnnotations groups by rounded coords`() {
        val annos = listOf(
            Annotation(x = 100.2, y = 200.4, label = "A"),
            Annotation(x = 100.3, y = 200.5, label = "B"), // same cluster
            Annotation(x = 300.0, y = 400.0, label = "C")
        )
        val clusters = TransformUtil.clustersFromAnnotations(annos)
        assertEquals(2, clusters.size)
        // One cluster has 2, the other has 1
        val sizes = clusters.values.map { it.size }.sorted()
        assertEquals(listOf(1, 2), sizes)
    }

    // ── isCalibrated ─────────────────────────────────────────────
    @Test
    fun `isCalibrated requires all three conditions`() {
        assertFalse(TransformUtil.isCalibrated(0.0, null, null))
        assertFalse(TransformUtil.isCalibrated(1.0, null, null))
        assertFalse(TransformUtil.isCalibrated(1.0, PointD(0.0, 0.0), null))
        assertTrue(TransformUtil.isCalibrated(1.0, PointD(0.0, 0.0), PointD(1.0, 1.0)))
    }
}
