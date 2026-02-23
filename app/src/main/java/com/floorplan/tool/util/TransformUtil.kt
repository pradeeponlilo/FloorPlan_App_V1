package com.floorplan.tool.util

import com.floorplan.tool.model.Annotation
import com.floorplan.tool.model.PointD
import kotlin.math.*

/**
 * Transform utilities — mirrors the HTML/JS math exactly.
 */
object TransformUtil {

    /** Convert screen coordinates to plan (world) coordinates. */
    fun screenToPlan(sx: Double, sy: Double, cam: Camera): PointD {
        return PointD(
            x = (sx - cam.tx) / cam.scale,
            y = (sy - cam.ty) / cam.scale
        )
    }

    /** Convert plan (world) coordinates to screen coordinates. */
    fun planToScreen(px: Double, py: Double, cam: Camera): PointD {
        return PointD(
            x = px * cam.scale + cam.tx,
            y = py * cam.scale + cam.ty
        )
    }

    /**
     * Compute a camera that fits the plan into the given viewport with 2% margin.
     */
    fun fitToScreen(planW: Int, planH: Int, viewW: Int, viewH: Int): Camera {
        if (planW <= 0 || planH <= 0 || viewW <= 0 || viewH <= 0) {
            return Camera()
        }
        val scale = (min(viewW.toDouble() / planW, viewH.toDouble() / planH) * 0.98)
            .coerceAtLeast(0.1)
        val tx = (viewW - planW * scale) * 0.5
        val ty = (viewH - planH * scale) * 0.5
        return Camera(scale = scale, tx = tx, ty = ty)
    }

    /**
     * Zoom the camera by [factor] around the screen point (cx, cy).
     */
    fun zoomAt(factor: Double, cx: Double, cy: Double, cam: Camera): Camera {
        val before = screenToPlan(cx, cy, cam)
        val newScale = (cam.scale * factor).coerceIn(0.1, 20.0)
        val newCam = cam.copy(scale = newScale)
        val after = screenToPlan(cx, cy, newCam)
        return newCam.copy(
            tx = newCam.tx + (after.x - before.x) * newScale,
            ty = newCam.ty + (after.y - before.y) * newScale
        )
    }

    /**
     * Apply re-register transform: given saved anchors (A0,B0) and new anchors (A1,B1),
     * transform all annotation positions via scale + rotation + translation.
     *
     * Returns pair: (transformed annotations, new saved anchors as Pair<PointD,PointD>)
     */
    fun applyReRegister(
        a0: PointD, b0: PointD,
        a1: PointD, b1: PointD,
        annotations: List<Annotation>
    ): Pair<List<Annotation>, Pair<PointD, PointD>>? {
        val v0x = b0.x - a0.x
        val v0y = b0.y - a0.y
        val v1x = b1.x - a1.x
        val v1y = b1.y - a1.y
        val d0 = hypot(v0x, v0y)
        val d1 = hypot(v1x, v1y)
        if (d0 == 0.0 || d1 == 0.0) return null

        val s = d1 / d0
        val a0Angle = atan2(v0y, v0x)
        val a1Angle = atan2(v1y, v1x)
        val theta = a1Angle - a0Angle
        val cosT = cos(theta)
        val sinT = sin(theta)

        val transformed = annotations.map { p ->
            val dx = p.x - a0.x
            val dy = p.y - a0.y
            val rx = dx * cosT - dy * sinT
            val ry = dx * sinT + dy * cosT
            p.copy(x = a1.x + s * rx, y = a1.y + s * ry)
        }
        return Pair(transformed, Pair(PointD(a1.x, a1.y), PointD(b1.x, b1.y)))
    }

    /**
     * Group annotations by their rounded pixel coordinate (cluster key).
     */
    fun clustersFromAnnotations(list: List<Annotation>): Map<String, List<Annotation>> {
        val clusters = mutableMapOf<String, MutableList<Annotation>>()
        for (a in list) {
            val key = "${a.x.roundToInt()},${a.y.roundToInt()}"
            clusters.getOrPut(key) { mutableListOf() }.add(a)
        }
        return clusters
    }

    /** Check if calibration is complete. */
    fun isCalibrated(scaleFactor: Double, rrASaved: PointD?, rrBSaved: PointD?): Boolean {
        return scaleFactor > 0 && rrASaved != null && rrBSaved != null
    }

    /** Meters to pixels. */
    fun m2px(meters: Double, scaleFactor: Double): Double {
        if (scaleFactor <= 0) return 0.0
        return meters / scaleFactor
    }

    /** Distance between two points. */
    fun distance(a: PointD, b: PointD): Double {
        return hypot(b.x - a.x, b.y - a.y)
    }

    /** Round to int (Kotlin extension). */
    private fun Double.roundToInt(): Int = kotlin.math.roundToInt(this)
    private fun kotlin.math.roundToInt(d: Double): Int = d.roundToInt()
}
