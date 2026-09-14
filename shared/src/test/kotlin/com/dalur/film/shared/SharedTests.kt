package com.dalur.film.shared

import org.junit.Assert.*
import org.junit.Test

class FilmRecipeTest {
    @Test fun `recipe ids are constrained`() {
        FilmRecipe(id = "dalur_memory_01", name = "Memory")
        try {
            FilmRecipe(id = "Bad Name!", name = "x")
            fail("should throw")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test fun `intensity range enforced`() {
        try {
            FilmRecipe(id = "dalur_x_01", name = "x", intensity = 2f)
            fail("should throw")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }
}

class ChronologyTest {
    @Test fun `captures order chronologically with stable tiebreak`() {
        val a = CaptureMetadata("b", 1000, null, false, "photo", "file:///a.jpg")
        val b = CaptureMetadata("a", 1000, null, false, "photo", "file:///b.jpg")
        val c = CaptureMetadata("c", 500, null, false, "photo", "file:///c.jpg")
        assertEquals(listOf("c", "a", "b"), orderCapturesChronologically(listOf(a, b, c)).map { it.mediaId })
    }

    @Test fun `journey keeps only gps captures in order`() {
        val noGps = CaptureMetadata("n", 100, null, true, "photo", "file:///n.jpg")
        val g1 = CaptureMetadata("g1", 300, GpsPoint(37.5, 127.0), false, "photo", "file:///1.jpg")
        val g2 = CaptureMetadata("g2", 200, GpsPoint(37.6, 127.1), false, "photo", "file:///2.jpg")
        val j = journeyRouteFromCaptures("j1", "Seoul", listOf(noGps, g1, g2), "MEMORY", "NINE_SIXTEEN_1080P_HEVC", 999)
        assertEquals(listOf("g2", "g1"), j.mediaIds)
        assertEquals(2, j.routePoints.size)
    }
}

class ExportPresetTest {
    @Test fun `hevc preferred when supported else h264 fallback`() {
        assertEquals("HEVC", pickExportPreset("9:16", true).codec)
        assertEquals("H264", pickExportPreset("9:16", false).codec)
        assertEquals("H264", pickExportPreset("1:1", false).codec)
    }
}

class CubeLutTest {
    private fun cube(size: Int): String = buildString {
        appendLine("TITLE \"DALUR test\"")
        appendLine("LUT_3D_SIZE $size")
        appendLine("DOMAIN_MIN 0.0 0.0 0.0")
        appendLine("DOMAIN_MAX 1.0 1.0 1.0")
        repeat(size * size * size) { appendLine("0.5 0.5 0.5") }
    }

    @Test fun `valid 33 cube parses`() {
        val p = CubeLut.parse(cube(33))
        assertEquals(33, p.size)
        assertTrue(CubeLut.validateSizeSupported(33))
    }

    @Test fun `valid 17 cube parses`() {
        assertEquals(17, CubeLut.parse(cube(17)).size)
    }

    @Test fun `truncated cube fails closed`() {
        try {
            CubeLut.parse("LUT_3D_SIZE 2\n0.0 0.0 0.0\n")
            fail("should throw")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }
}

class NamingTest {
    @Test fun `filenames are deterministic`() {
        assertEquals(
            dalurFileName(0L, "abc123", "JPG"),
            "DALUR_19700101_000000_000_abc123.jpg"
        )
    }

    @Test fun `journey hold decreases with count`() {
        assertTrue(journeyHoldSeconds(1) > journeyHoldSeconds(5))
        assertTrue(journeyHoldSeconds(5) > journeyHoldSeconds(50))
        assertEquals(0.8, journeyHoldSeconds(50), 1e-9)
    }
}
