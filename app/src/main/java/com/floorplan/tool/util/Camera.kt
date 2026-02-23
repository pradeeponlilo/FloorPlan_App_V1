package com.floorplan.tool.util

/**
 * Camera state for pan & zoom on the canvas.
 * tx, ty are in screen-pixel coordinates.
 */
data class Camera(
    val scale: Double = 1.0,
    val tx: Double = 0.0,
    val ty: Double = 0.0
)
