package com.floorplan.tool

import com.floorplan.tool.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class JsonRoundTripTest {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @Test
    fun `empty document round trip`() {
        val doc = PlanDocument(
            version = 1,
            planWidth = 1200,
            planHeight = 800,
            scaleFactor = 1.0,
            crosshairSize = 12
        )
        val str = json.encodeToString(doc)
        val parsed = json.decodeFromString<PlanDocument>(str)
        assertEquals(doc, parsed)
    }

    @Test
    fun `full document round trip with annotations and anchors`() {
        val doc = PlanDocument(
            version = 1,
            planWidth = 2400,
            planHeight = 1600,
            scaleFactor = 0.005,
            refAnchors = RefAnchors(
                a = PointD(100.5, 200.3),
                b = PointD(500.7, 200.1)
            ),
            crosshairSize = 16,
            annotations = listOf(
                Annotation(
                    x = 150.0, y = 250.0,
                    label = "Outlet",
                    details = "2m above floor\nCat6, to rack A",
                    type = AnnoType.WALL,
                    labelOffset = OffsetD(22.0, -22.0)
                ),
                Annotation(
                    x = 300.5, y = 400.8,
                    label = "Vent",
                    details = "",
                    type = AnnoType.ROOF,
                    labelOffset = OffsetD(18.0, -18.0)
                ),
                Annotation(
                    x = 50.0, y = 50.0,
                    label = "Connector",
                    details = "Floor level",
                    type = AnnoType.FLOOR,
                    labelOffset = OffsetD(10.0, -30.0)
                )
            )
        )
        val str = json.encodeToString(doc)
        val parsed = json.decodeFromString<PlanDocument>(str)

        assertEquals(doc.version, parsed.version)
        assertEquals(doc.planWidth, parsed.planWidth)
        assertEquals(doc.planHeight, parsed.planHeight)
        assertEquals(doc.scaleFactor, parsed.scaleFactor, 1e-12)
        assertEquals(doc.crosshairSize, parsed.crosshairSize)
        assertEquals(doc.refAnchors, parsed.refAnchors)
        assertEquals(doc.annotations.size, parsed.annotations.size)

        for (i in doc.annotations.indices) {
            val orig = doc.annotations[i]
            val dest = parsed.annotations[i]
            assertEquals(orig.x, dest.x, 1e-12)
            assertEquals(orig.y, dest.y, 1e-12)
            assertEquals(orig.label, dest.label)
            assertEquals(orig.details, dest.details)
            assertEquals(orig.type, dest.type)
            assertEquals(orig.labelOffset.dx, dest.labelOffset.dx, 1e-12)
            assertEquals(orig.labelOffset.dy, dest.labelOffset.dy, 1e-12)
        }
    }

    @Test
    fun `AnnoType serializes as lowercase string`() {
        val anno = Annotation(x = 0.0, y = 0.0, type = AnnoType.WALL)
        val str = json.encodeToString(anno)
        assertTrue(str.contains("\"wall\""))
    }

    @Test
    fun `null refAnchors round trip`() {
        val doc = PlanDocument(
            planWidth = 800, planHeight = 600,
            scaleFactor = 0.01,
            refAnchors = null,
            crosshairSize = 12
        )
        val str = json.encodeToString(doc)
        val parsed = json.decodeFromString<PlanDocument>(str)
        assertNull(parsed.refAnchors)
    }

    @Test
    fun `labelOffset defaults are applied when missing`() {
        // Simulate importing JSON with no labelOffset field
        val jsonStr = """
        {
            "x": 100.0,
            "y": 200.0,
            "label": "Test",
            "details": "",
            "type": "floor"
        }
        """.trimIndent()
        val parsed = json.decodeFromString<Annotation>(jsonStr)
        // Should use default offset
        assertEquals(18.0, parsed.labelOffset.dx, 1e-12)
        assertEquals(-18.0, parsed.labelOffset.dy, 1e-12)
    }

    @Test
    fun `refAnchors A and B use correct JSON keys`() {
        val anchors = RefAnchors(a = PointD(10.0, 20.0), b = PointD(30.0, 40.0))
        val str = json.encodeToString(anchors)
        assertTrue(str.contains("\"A\""))
        assertTrue(str.contains("\"B\""))
    }
}
