package com.floorplan.tool.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Top-level document: everything needed to save/restore a floor plan session.
 */
@Serializable
data class PlanDocument(
    val version: Int = 1,
    val planWidth: Int,
    val planHeight: Int,
    val scaleFactor: Double,
    val refAnchors: RefAnchors? = null,
    val crosshairSize: Int = 12,
    val annotations: List<Annotation> = emptyList()
)

@Serializable
data class RefAnchors(
    @SerialName("A") val a: PointD,
    @SerialName("B") val b: PointD
)

@Serializable
data class Annotation(
    val x: Double,
    val y: Double,
    val label: String = "Connector",
    val details: String = "",
    val type: AnnoType = AnnoType.FLOOR,
    val labelOffset: OffsetD = OffsetD(18.0, -18.0)
)

@Serializable
enum class AnnoType {
    @SerialName("floor") FLOOR,
    @SerialName("wall") WALL,
    @SerialName("roof") ROOF
}

@Serializable
data class PointD(val x: Double, val y: Double)

@Serializable
data class OffsetD(val dx: Double, val dy: Double)
