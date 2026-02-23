package com.floorplan.tool.renderer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.floorplan.tool.model.Annotation
import com.floorplan.tool.model.AnnoType
import com.floorplan.tool.model.PointD
import com.floorplan.tool.util.Camera
import com.floorplan.tool.util.TransformUtil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Holds the computed rectangle + metadata for one label box.
 * Used for hit-testing and for drawing leader lines.
 */
data class LabelRect(
    val index: Int,
    /** Plan-coordinate rect */
    val boxX: Float, val boxY: Float, val boxW: Float, val boxH: Float,
    /** Screen-coordinate rect (set after layout) */
    val screenRect: RectF,
    /** Leader-line endpoints in plan coords */
    val leaderFromX: Float, val leaderFromY: Float,
    val leaderToX: Float, val leaderToY: Float,
    /** Text rendering helpers */
    val padX: Float, val padY: Float,
    val nameAscent: Float, val nameDescent: Float,
    val detailLines: List<String>,
    val gap: Float
)

/**
 * Draws the floor plan onto an android.graphics.Canvas.
 * Works for both on-screen (Compose drawIntoCanvas) and offscreen (export bitmap).
 */
class FloorPlanRenderer {

    // ── Paints (reused to avoid allocation) ──────────────────────────
    private val bgPaint = Paint().apply { color = Color.parseColor("#F2F2F2"); style = Paint.Style.FILL }
    private val ringPaint = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true }
    private val crossPaint = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true }
    private val textPaint = Paint().apply { isAntiAlias = true; textSize = 12f; typeface = Typeface.DEFAULT }
    private val boldPaint = Paint().apply { isAntiAlias = true; textSize = 12f; typeface = Typeface.DEFAULT_BOLD }
    private val linePaint = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true }
    private val fillPaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val dashPaint = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true }

    // ── Public: full scene redraw ────────────────────────────────────
    /**
     * Draw everything onto [canvas] in on-screen mode.
     * Returns list of label rects for hit-testing.
     */
    fun drawScene(
        canvas: Canvas,
        viewW: Int, viewH: Int,
        baseBitmap: android.graphics.Bitmap?,
        cam: Camera,
        annotations: List<Annotation>,
        selectedIndex: Int?,
        scaleFactor: Double,
        refAnchors: Pair<PointD, PointD>?,
        crosshairSize: Int,
        showGrid: Boolean,
        showA4: Boolean,
        gridMajor: Double, gridMinor: Double, gridLabelEvery: Double,
        modeHelperState: ModeHelperState?
    ): List<LabelRect> {
        // 1. Background
        canvas.save()
        canvas.setMatrix(android.graphics.Matrix())
        bgPaint.color = Color.parseColor("#F2F2F2")
        canvas.drawRect(0f, 0f, viewW.toFloat(), viewH.toFloat(), bgPaint)
        canvas.restore()

        // 2. Apply camera transform
        canvas.save()
        canvas.translate(cam.tx.toFloat(), cam.ty.toFloat())
        canvas.scale(cam.scale.toFloat(), cam.scale.toFloat())

        // 3. Base plan
        if (baseBitmap != null) {
            canvas.drawBitmap(baseBitmap, 0f, 0f, null)
        }

        // 4. Grid
        val isCalibrated = TransformUtil.isCalibrated(scaleFactor, refAnchors?.first, refAnchors?.second)
        if (showGrid && isCalibrated) {
            drawGrid(canvas, cam, refAnchors!!.first, scaleFactor,
                gridMajor, gridMinor, gridLabelEvery,
                baseBitmap?.width ?: 1200, baseBitmap?.height ?: 800, 1f)
        }

        // 5. Crosshairs + clusters
        val clusters = TransformUtil.clustersFromAnnotations(annotations)
        for ((_, list) in clusters) {
            val a = list[0]
            val idx = annotations.indexOf(a)
            drawCrosshairWithRing(canvas, a.x.toFloat(), a.y.toFloat(),
                getAnnoColor(a.type), crosshairSize.toFloat(), list.size,
                cam, selectedIndex == idx)
        }

        // 6. Reference anchors
        if (refAnchors != null) {
            drawAnchorWithLabel(canvas, refAnchors.first, "#FF9800", "Ref A", cam)
            drawAnchorWithLabel(canvas, refAnchors.second, "#FF9800", "Ref B", cam)
        }

        // 7. Mode helpers (rubber bands)
        if (modeHelperState != null) {
            drawModeHelpers(canvas, cam, modeHelperState, scaleFactor)
        }

        // 8. Labels + leader lines
        val labelRects = layoutLabels(canvas, cam, annotations, crosshairSize)

        // Draw leader lines (RED)
        linePaint.color = Color.RED
        linePaint.strokeWidth = 1.5f / cam.scale.toFloat()
        for (entry in labelRects) {
            canvas.drawLine(entry.leaderFromX, entry.leaderFromY,
                entry.leaderToX, entry.leaderToY, linePaint)
        }

        // Draw label boxes + text
        for (entry in labelRects) {
            val a = annotations[entry.index]
            val isSel = (selectedIndex == entry.index)

            fillPaint.color = if (isSel) Color.argb(242, 255, 255, 120) else Color.argb(235, 255, 255, 255)
            canvas.drawRect(entry.boxX, entry.boxY, entry.boxX + entry.boxW, entry.boxY + entry.boxH, fillPaint)

            linePaint.color = Color.RED
            linePaint.strokeWidth = 1f / cam.scale.toFloat()
            canvas.drawRect(entry.boxX, entry.boxY, entry.boxX + entry.boxW, entry.boxY + entry.boxH, linePaint)

            // Name (bold)
            val s = 1f / cam.scale.toFloat()
            boldPaint.textSize = 12f * s
            boldPaint.color = Color.BLACK
            val nameBaseline = entry.boxY + entry.padY + entry.nameAscent
            canvas.drawText(a.label.ifEmpty { "Connector" }, entry.boxX + entry.padX, nameBaseline, boldPaint)

            // Details
            if (entry.detailLines.isNotEmpty()) {
                textPaint.textSize = 12f * s
                textPaint.color = Color.BLACK
                val firstDetailBaseline = nameBaseline + entry.nameDescent + entry.gap + 9f * s
                for ((i, line) in entry.detailLines.withIndex()) {
                    val y = firstDetailBaseline + i * 12f * s
                    canvas.drawText(line, entry.boxX + entry.padX, y, textPaint)
                }
            }
        }

        canvas.restore()

        // 9. A4 overlay (screen coords)
        if (showA4) {
            drawA4Overlay(canvas, viewW, viewH)
        }

        return labelRects
    }

    // ── Export: offscreen render ──────────────────────────────────────
    fun renderForExport(
        canvas: Canvas,
        baseBitmap: android.graphics.Bitmap?,
        planW: Int, planH: Int,
        scale: Float,
        annotations: List<Annotation>,
        scaleFactor: Double,
        refAnchors: Pair<PointD, PointD>?,
        crosshairSize: Int,
        showGrid: Boolean,
        gridMajor: Double, gridMinor: Double, gridLabelEvery: Double,
        includeDetails: Boolean
    ) {
        val sw = (planW * scale).roundToInt()
        val sh = (planH * scale).roundToInt()
        val detailsBlockH = if (includeDetails) 180 else 0

        // Draw plan image
        if (baseBitmap != null) {
            canvas.save()
            canvas.scale(sw.toFloat() / baseBitmap.width, sh.toFloat() / baseBitmap.height)
            canvas.drawBitmap(baseBitmap, 0f, 0f, null)
            canvas.restore()
        }

        canvas.save()
        canvas.scale(scale, scale)

        // Grid (at identity camera)
        val isCalibrated = TransformUtil.isCalibrated(scaleFactor, refAnchors?.first, refAnchors?.second)
        if (showGrid && isCalibrated) {
            val identityCam = Camera(1.0, 0.0, 0.0)
            drawGrid(canvas, identityCam, refAnchors!!.first, scaleFactor,
                gridMajor, gridMinor, gridLabelEvery, planW, planH, 1f)
        }

        // Ref anchors
        if (refAnchors != null) {
            drawExportCrosshair(canvas, refAnchors.first.x.toFloat(), refAnchors.first.y.toFloat(), Color.parseColor("#FF9800"), 12f, 0)
            drawExportCrosshair(canvas, refAnchors.second.x.toFloat(), refAnchors.second.y.toFloat(), Color.parseColor("#FF9800"), 12f, 0)
        }

        // Leader lines (RED)
        val clusters = TransformUtil.clustersFromAnnotations(annotations)
        linePaint.strokeWidth = 1.5f
        linePaint.color = Color.RED
        for ((_, list) in clusters) {
            val a = list[0]
            val layout = layoutForExport(canvas, a, crosshairSize)
            val (tx, ty) = computeLeaderTarget(a.x.toFloat(), a.y.toFloat(), layout)
            canvas.drawLine(a.x.toFloat(), a.y.toFloat(), tx, ty, linePaint)
        }

        // Crosshairs + boxes + text
        for ((_, list) in clusters) {
            val a = list[0]
            drawExportCrosshair(canvas, a.x.toFloat(), a.y.toFloat(), getAnnoColorInt(a.type), crosshairSize.toFloat(), list.size)

            val layout = layoutForExport(canvas, a, crosshairSize)
            fillPaint.color = Color.argb(242, 255, 255, 255)
            canvas.drawRect(layout.boxX, layout.boxY, layout.boxX + layout.boxW, layout.boxY + layout.boxH, fillPaint)
            linePaint.color = Color.RED
            linePaint.strokeWidth = 1f
            canvas.drawRect(layout.boxX, layout.boxY, layout.boxX + layout.boxW, layout.boxY + layout.boxH, linePaint)

            boldPaint.textSize = 12f
            boldPaint.color = Color.BLACK
            val nameBaseline = layout.boxY + layout.padY + layout.nameAscent
            canvas.drawText(a.label, layout.boxX + layout.padX, nameBaseline, boldPaint)

            if (layout.detailLines.isNotEmpty()) {
                textPaint.textSize = 12f
                textPaint.color = Color.BLACK
                val firstDetailBaseline = nameBaseline + layout.nameDescent + layout.gap + 9f
                for ((i, line) in layout.detailLines.withIndex()) {
                    canvas.drawText(line, layout.boxX + layout.padX, firstDetailBaseline + i * 12f, textPaint)
                }
            }
        }

        canvas.restore()

        // Details block below image
        if (includeDetails) {
            val top = (planH * scale).roundToInt()
            fillPaint.color = Color.WHITE
            canvas.drawRect(0f, top.toFloat(), sw.toFloat(), (top + detailsBlockH).toFloat(), fillPaint)

            textPaint.color = Color.BLACK
            textPaint.textSize = 12f
            canvas.drawText("Annotation Details:", 20f, top + 30f, textPaint)
            var y = top + 50f

            if (refAnchors != null) {
                val ax = String.format("%.2f", refAnchors.first.x * scaleFactor)
                val ay = String.format("%.2f", refAnchors.first.y * scaleFactor)
                val bx = String.format("%.2f", refAnchors.second.x * scaleFactor)
                val by = String.format("%.2f", refAnchors.second.y * scaleFactor)
                canvas.drawText("Ref A | X:${ax} m, Y:${ay} m", 20f, y, textPaint); y += 18f
                canvas.drawText("Ref B | X:${bx} m, Y:${by} m", 20f, y, textPaint); y += 18f
                canvas.drawText("---", 20f, y, textPaint); y += 18f
            }

            for ((i, a) in annotations.withIndex()) {
                val xm = String.format("%.2f", a.x * scaleFactor)
                val ym = String.format("%.2f", a.y * scaleFactor)
                val det = if (a.details.isNotBlank()) " | ${a.details.replace(Regex("\\s+"), " ")}" else ""
                canvas.drawText("${i + 1}. ${a.label} | ${a.type.name.lowercase()} | X:${xm} m, Y:${ym} m${det}", 20f, y, textPaint)
                y += 18f
            }
        }
    }

    // ── Grid ─────────────────────────────────────────────────────────
    private fun drawGrid(
        canvas: Canvas, cam: Camera, origin: PointD, scaleFactor: Double,
        gridMajor: Double, gridMinor: Double, gridLabelEvery: Double,
        planW: Int, planH: Int, lineScale: Float
    ) {
        val m2px = { m: Double -> m / scaleFactor }
        val minorStepPx = m2px(gridMinor).toFloat()
        val majorStepPx = m2px(gridMajor).toFloat()
        val labelEveryPx = m2px(gridLabelEvery).toFloat()
        if (minorStepPx <= 0 || majorStepPx <= 0) return

        val minorStepScreen = minorStepPx * cam.scale.toFloat()
        val majorStepScreen = majorStepPx * cam.scale.toFloat()
        val labelEveryScreen = labelEveryPx * cam.scale.toFloat()
        val drawMinor = minorStepScreen >= 8
        val drawMajorLines = majorStepScreen >= 8
        val drawLabels = labelEveryScreen >= 50

        val minX = 0f; val minY = 0f
        val maxX = planW.toFloat(); val maxY = planH.toFloat()
        val ox = origin.x.toFloat(); val oy = origin.y.toFloat()

        val s = 1f / cam.scale.toFloat()

        if (drawMinor) {
            linePaint.strokeWidth = 1f * s * lineScale
            linePaint.color = Color.argb(25, 0, 0, 0) // 0.10 opacity
            var x = ox + kotlin.math.ceil(((minX - ox) / minorStepPx).toDouble()).toFloat() * minorStepPx
            while (x <= maxX) {
                canvas.drawLine(x, minY, x, maxY, linePaint); x += minorStepPx
            }
            var y = oy + kotlin.math.ceil(((minY - oy) / minorStepPx).toDouble()).toFloat() * minorStepPx
            while (y <= maxY) {
                canvas.drawLine(minX, y, maxX, y, linePaint); y += minorStepPx
            }
        }

        if (drawMajorLines) {
            linePaint.strokeWidth = 2f * s * lineScale
            linePaint.color = Color.argb(64, 0, 0, 0) // 0.25 opacity
            var x = ox + kotlin.math.ceil(((minX - ox) / majorStepPx).toDouble()).toFloat() * majorStepPx
            while (x <= maxX) {
                canvas.drawLine(x, minY, x, maxY, linePaint); x += majorStepPx
            }
            var y = oy + kotlin.math.ceil(((minY - oy) / majorStepPx).toDouble()).toFloat() * majorStepPx
            while (y <= maxY) {
                canvas.drawLine(minX, y, maxX, y, linePaint); y += majorStepPx
            }
        }

        if (drawLabels) {
            textPaint.textSize = 12f * s
            textPaint.color = Color.argb(153, 0, 0, 0) // 0.6 opacity
            var labX = ox + kotlin.math.ceil(((minX - ox) / labelEveryPx).toDouble()).toFloat() * labelEveryPx
            var guard = 0
            while (labX <= maxX && guard < 400) {
                val xm = String.format("%.1f", (labX - ox) * scaleFactor)
                canvas.drawText("${xm}m", labX + 4 * s, minY + 14 * s, textPaint)
                labX += labelEveryPx; guard++
            }
            var labY = oy + kotlin.math.ceil(((minY - oy) / labelEveryPx).toDouble()).toFloat() * labelEveryPx
            guard = 0
            while (labY <= maxY && guard < 400) {
                val ym = String.format("%.1f", (labY - oy) * scaleFactor)
                canvas.drawText("${ym}m", minX + 4 * s, labY - 4 * s, textPaint)
                labY += labelEveryPx; guard++
            }
        }

        // Origin axes (stronger lines)
        linePaint.strokeWidth = 2.5f * s * lineScale
        linePaint.color = Color.argb(102, 0, 0, 0) // 0.4 opacity
        if (ox in minX..maxX) canvas.drawLine(ox, minY, ox, maxY, linePaint)
        if (oy in minY..maxY) canvas.drawLine(minX, oy, maxX, oy, linePaint)
    }

    // ── Crosshair + Ring ─────────────────────────────────────────────
    private fun drawCrosshairWithRing(
        canvas: Canvas, x: Float, y: Float, color: Int, sizePx: Float,
        count: Int, cam: Camera, highlighted: Boolean
    ) {
        val s = sizePx / cam.scale.toFloat()
        val r = (sizePx * 0.55f) / cam.scale.toFloat()

        ringPaint.color = color
        ringPaint.strokeWidth = (if (highlighted) 3f else 2f) / cam.scale.toFloat()
        canvas.drawCircle(x, y, r, ringPaint)

        crossPaint.color = color
        crossPaint.strokeWidth = ringPaint.strokeWidth
        canvas.drawLine(x - s, y, x + s, y, crossPaint)
        canvas.drawLine(x, y - s, x, y + s, crossPaint)

        if (count > 1) {
            textPaint.textSize = 14f / cam.scale.toFloat()
            textPaint.color = Color.BLACK
            canvas.drawText(count.toString(), x + s + 4f / cam.scale.toFloat(), y - s - 4f / cam.scale.toFloat(), textPaint)
        }
    }

    private fun drawExportCrosshair(canvas: Canvas, x: Float, y: Float, color: Int, size: Float, count: Int) {
        ringPaint.color = color; ringPaint.strokeWidth = 2f
        canvas.drawCircle(x, y, size * 0.55f, ringPaint)
        crossPaint.color = color; crossPaint.strokeWidth = 2f
        canvas.drawLine(x - size, y, x + size, y, crossPaint)
        canvas.drawLine(x, y - size, x, y + size, crossPaint)
        if (count > 1) {
            textPaint.textSize = 14f; textPaint.color = Color.BLACK
            canvas.drawText(count.toString(), x + size + 4f, y - size - 4f, textPaint)
        }
    }

    // ── Anchor with label ────────────────────────────────────────────
    private fun drawAnchorWithLabel(canvas: Canvas, pt: PointD, colorHex: String, label: String, cam: Camera) {
        val color = Color.parseColor(colorHex)
        drawCrosshairWithRing(canvas, pt.x.toFloat(), pt.y.toFloat(), color, 12f, 0, cam, false)
        val s = 1f / cam.scale.toFloat()
        textPaint.textSize = 12f * s
        textPaint.color = Color.parseColor("#222222")
        canvas.drawText(label, pt.x.toFloat() + 10f * s, pt.y.toFloat() - 10f * s, textPaint)
    }

    // ── Mode helpers (rubber bands) ──────────────────────────────────
    fun drawModeHelpers(canvas: Canvas, cam: Camera, state: ModeHelperState, scaleFactor: Double) {
        val s = 1f / cam.scale.toFloat()

        when (state) {
            is ModeHelperState.Calibration -> {
                drawAnchorWithLabel(canvas, state.a, "#1976D2", "A?", cam)
                if (state.b != null) {
                    drawAnchorWithLabel(canvas, state.b, "#1976D2", "B?", cam)
                    drawDashedLine(canvas, state.a, state.b, cam)
                } else if (state.cursor != null) {
                    drawDashedLine(canvas, state.a, state.cursor, cam)
                    val px = hypot(state.cursor.x - state.a.x, state.cursor.y - state.a.y)
                    drawTag(canvas, state.cursor.x.toFloat(), state.cursor.y.toFloat(), "${String.format("%.2f", px * scaleFactor)} m", cam)
                }
            }
            is ModeHelperState.Place1 -> {
                drawAnchorWithLabel(canvas, state.origin, "green", "Origin", cam)
                if (state.cursor != null) {
                    drawDashedLine(canvas, state.origin, state.cursor, cam)
                    val dist = hypot(state.cursor.x - state.origin.x, state.cursor.y - state.origin.y)
                    drawTag(canvas, state.cursor.x.toFloat(), state.cursor.y.toFloat(), "${String.format("%.2f", dist * scaleFactor)} m", cam)
                }
            }
            is ModeHelperState.Place2 -> {
                drawAnchorWithLabel(canvas, state.refA, "purple", "Ref 1", cam)
                if (state.refB != null) {
                    drawAnchorWithLabel(canvas, state.refB, "purple", "Ref 2", cam)
                    drawDashedLine(canvas, state.refA, state.refB, cam)
                    if (state.cursor != null) {
                        drawDashedLine(canvas, state.refA, state.cursor, cam)
                        drawDashedLine(canvas, state.refB, state.cursor, cam)
                        val dA = hypot(state.cursor.x - state.refA.x, state.cursor.y - state.refA.y) * scaleFactor
                        val dB = hypot(state.cursor.x - state.refB.x, state.cursor.y - state.refB.y) * scaleFactor
                        drawTag(canvas, state.cursor.x.toFloat(), state.cursor.y.toFloat(),
                            "A:${String.format("%.2f", dA)}m  B:${String.format("%.2f", dB)}m", cam)
                    }
                } else if (state.cursor != null) {
                    drawDashedLine(canvas, state.refA, state.cursor, cam)
                }
            }
            is ModeHelperState.ReRegister -> {
                drawAnchorWithLabel(canvas, state.pickA, "purple", "Pick A", cam)
                if (state.pickB != null) {
                    drawAnchorWithLabel(canvas, state.pickB, "purple", "Pick B", cam)
                    drawDashedLine(canvas, state.pickA, state.pickB, cam)
                } else if (state.cursor != null) {
                    drawDashedLine(canvas, state.pickA, state.cursor, cam)
                }
            }
        }
    }

    // ── Dashed line (red rubber band) ────────────────────────────────
    private fun drawDashedLine(canvas: Canvas, a: PointD, b: PointD, cam: Camera) {
        val s = 1f / cam.scale.toFloat()
        dashPaint.color = Color.RED
        dashPaint.strokeWidth = 2f * s
        dashPaint.pathEffect = DashPathEffect(floatArrayOf(8f * s, 6f * s), 0f)
        canvas.drawLine(a.x.toFloat(), a.y.toFloat(), b.x.toFloat(), b.y.toFloat(), dashPaint)
        dashPaint.pathEffect = null
    }

    // ── Tag (distance label near cursor) ─────────────────────────────
    private fun drawTag(canvas: Canvas, px: Float, py: Float, text: String, cam: Camera) {
        val s = 1f / cam.scale.toFloat()
        textPaint.textSize = 12f * s
        val w = textPaint.measureText(text) + 10f * s
        val h = 18f * s

        fillPaint.color = Color.argb(230, 255, 255, 200)
        canvas.drawRect(px + 12f * s, py - h - 2f * s, px + 12f * s + w, py - 2f * s, fillPaint)
        linePaint.color = Color.RED; linePaint.strokeWidth = 1f * s
        canvas.drawRect(px + 12f * s, py - h - 2f * s, px + 12f * s + w, py - 2f * s, linePaint)
        textPaint.color = Color.parseColor("#333333")
        canvas.drawText(text, px + 17f * s, py - 6f * s, textPaint)
    }

    // ── A4 overlay ───────────────────────────────────────────────────
    private fun drawA4Overlay(canvas: Canvas, viewW: Int, viewH: Int) {
        val ratio = 210f / 297f
        val pad = 40f
        var w = viewW - pad * 2
        var h = w / ratio
        if (h > viewH - pad * 2) {
            h = viewH - pad * 2
            w = h * ratio
        }
        val x = (viewW - w) / 2f
        val y = (viewH - h) / 2f

        canvas.save()
        canvas.setMatrix(android.graphics.Matrix())
        linePaint.color = Color.argb(153, 0, 0, 0); linePaint.strokeWidth = 2f
        canvas.drawRect(x, y, x + w, y + h, linePaint)
        fillPaint.color = Color.argb(13, 0, 0, 0)
        canvas.drawRect(x, y, x + w, y + h, fillPaint)
        textPaint.textSize = 12f; textPaint.color = Color.parseColor("#333333")
        canvas.drawText("A4 Reference Box", x + 8, y + 16, textPaint)
        canvas.restore()
    }

    // ── Label layout (returns rects for hit-testing) ─────────────────
    fun layoutLabels(
        canvas: Canvas, cam: Camera,
        annotations: List<Annotation>, crosshairSize: Int
    ): List<LabelRect> {
        val results = mutableListOf<LabelRect>()
        val s = 1f / cam.scale.toFloat()

        for ((idx, a) in annotations.withIndex()) {
            val dx = a.labelOffset.dx.toFloat()
            val dy = a.labelOffset.dy.toFloat()
            val lx = a.x.toFloat() + dx
            val ly = a.y.toFloat() + dy

            // Name metrics
            boldPaint.textSize = 12f * s
            val nameW = boldPaint.measureText(a.label.ifEmpty { "Connector" })
            val fm = boldPaint.fontMetrics
            val nameAsc = -fm.ascent
            val nameDesc = fm.descent

            // Detail metrics
            textPaint.textSize = 12f * s
            val lines = a.details.split(Regex("\\r?\\n")).filter { it.isNotEmpty() }
            var detW = 0f
            for (t in lines) { detW = max(detW, textPaint.measureText(t)) }

            val padX = 4f * s; val padY = 3f * s; val gap = 4f * s
            val nameH = nameAsc + nameDesc
            val detH = lines.size * (textPaint.fontMetrics.let { -it.ascent + it.descent })
            val textH = nameH + if (lines.isNotEmpty()) (gap + detH) else 0f
            val textW = max(nameW, detW)

            val boxX = lx
            val boxY = ly - textH
            val boxW = textW + padX * 2
            val boxH = textH + padY * 2

            // Leader target (nearest edge point on box to annotation)
            val (targetX, targetY) = computeLeaderTarget(a.x.toFloat(), a.y.toFloat(),
                ExportLayout(boxX, boxY, boxW, boxH, padX, padY, gap, nameAsc, nameDesc, lines))

            // Screen-coordinate rect
            val scrTopLeft = TransformUtil.planToScreen(boxX.toDouble(), boxY.toDouble(), cam)
            val scrBotRight = TransformUtil.planToScreen((boxX + boxW).toDouble(), (boxY + boxH).toDouble(), cam)
            val screenRect = RectF(scrTopLeft.x.toFloat(), scrTopLeft.y.toFloat(), scrBotRight.x.toFloat(), scrBotRight.y.toFloat())

            results.add(LabelRect(
                index = idx,
                boxX = boxX, boxY = boxY, boxW = boxW, boxH = boxH,
                screenRect = screenRect,
                leaderFromX = a.x.toFloat(), leaderFromY = a.y.toFloat(),
                leaderToX = targetX, leaderToY = targetY,
                padX = padX, padY = padY,
                nameAscent = nameAsc, nameDescent = nameDesc,
                detailLines = lines, gap = gap
            ))
        }
        return results
    }

    // ── Layout for export (identity scale) ───────────────────────────
    private fun layoutForExport(canvas: Canvas, a: Annotation, crosshairSize: Int): ExportLayout {
        val dx = a.labelOffset.dx.toFloat()
        val dy = a.labelOffset.dy.toFloat()
        val lx = a.x.toFloat() + dx
        val ly = a.y.toFloat() + dy

        boldPaint.textSize = 12f
        val nameW = boldPaint.measureText(a.label)
        val fm = boldPaint.fontMetrics
        val nameAsc = -fm.ascent
        val nameDesc = fm.descent

        textPaint.textSize = 12f
        val lines = a.details.split(Regex("\\r?\\n")).filter { it.isNotEmpty() }
        var detW = 0f
        for (t in lines) detW = max(detW, textPaint.measureText(t))

        val padX = 4f; val padY = 3f; val gap = 4f
        val nameH = nameAsc + nameDesc
        val detH = lines.size * 12f
        val textH = nameH + if (lines.isNotEmpty()) (gap + detH) else 0f
        val textW = max(nameW, detW)

        return ExportLayout(lx, ly - textH, textW + padX * 2, textH + padY * 2,
            padX, padY, gap, nameAsc, nameDesc, lines)
    }

    private data class ExportLayout(
        val boxX: Float, val boxY: Float, val boxW: Float, val boxH: Float,
        val padX: Float, val padY: Float, val gap: Float,
        val nameAscent: Float, val nameDescent: Float,
        val detailLines: List<String>
    )

    private fun computeLeaderTarget(ax: Float, ay: Float, layout: ExportLayout): Pair<Float, Float> {
        val tx = when {
            ax < layout.boxX -> layout.boxX
            ax > layout.boxX + layout.boxW -> layout.boxX + layout.boxW
            else -> ax
        }
        val ty = when {
            ay < layout.boxY -> layout.boxY
            ay > layout.boxY + layout.boxH -> layout.boxY + layout.boxH
            else -> ay
        }
        return Pair(tx, ty)
    }

    // ── Color helpers ────────────────────────────────────────────────
    private fun getAnnoColor(type: AnnoType): Int = when (type) {
        AnnoType.ROOF -> Color.GREEN
        AnnoType.WALL -> Color.BLUE
        AnnoType.FLOOR -> Color.parseColor("#800080") // purple
    }

    private fun getAnnoColorInt(type: AnnoType): Int = getAnnoColor(type)

    companion object {
        fun getAnnoColorStatic(type: AnnoType): Int = when (type) {
            AnnoType.ROOF -> Color.GREEN
            AnnoType.WALL -> Color.BLUE
            AnnoType.FLOOR -> Color.parseColor("#800080")
        }
    }
}

/**
 * Sealed class to carry mode-specific transient drawing state.
 */
sealed class ModeHelperState {
    data class Calibration(val a: PointD, val b: PointD? = null, val cursor: PointD? = null) : ModeHelperState()
    data class Place1(val origin: PointD, val cursor: PointD? = null) : ModeHelperState()
    data class Place2(val refA: PointD, val refB: PointD? = null, val cursor: PointD? = null) : ModeHelperState()
    data class ReRegister(val pickA: PointD, val pickB: PointD? = null, val cursor: PointD? = null) : ModeHelperState()
}
