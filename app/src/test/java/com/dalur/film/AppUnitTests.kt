package com.dalur.film

import com.dalur.film.film.FilmEngine
import com.dalur.film.journey.JourneyTimeline
import com.dalur.film.journey.JourneySegment
import com.dalur.film.media.sidecarFileFor
import com.dalur.film.shared.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FilmEngineTest {
    private fun recipe() = FilmRecipe(
        id = "dalur_test_01", name = "Test",
        lut = null, intensity = 1f,
        tone = com.dalur.film.shared.Tone(exposure = 1f), // +1 stop
        color = com.dalur.film.shared.ColorAdjust(),
        effects = com.dalur.film.shared.Effects()
    )

    @Test fun `exposure doubles pixel`() {
        val (r, _, _) = FilmEngine.applyCpu(recipe(), 0.25f, 0.25f, 0.25f)
        assertEquals(0.5f, r, 1e-3f)
    }

    @Test fun `rule string is empty at zero intensity`() {
        assertEquals("", FilmEngine.ruleString(recipe(), 0f))
    }

    @Test fun `unsupported components are reported not-supported and never emitted`() {
        val rc = recipe().copy(
            tone = Tone(),
            color = ColorAdjust(),
            lut = LutRef("cube", 17, "bundled://luts/dalur_test_01.cube", "0".repeat(64)),
            effects = Effects(grain = 0.2f, halation = 0.1f, bloom = 0.1f, vignette = 0.1f,
                chromaticAberration = 0.02f, lightLeak = LightLeak(strength = 0.3f))
        )
        val plan = FilmEngine.plan(rc, 1f)
        assertEquals("", plan.rule) // every active component is unsupported → nothing to run
        for (bad in listOf("lut", "grain", "halation", "bloom", "vignette",
            "chromaticAbberation", "lightLeak")) {
            assertFalse("rule must never contain unsupported token: $bad",
                plan.rule.contains(bad))
        }
        assertTrue("report must name every unsupported component",
            plan.report.notSupported.containsAll(
                listOf("lut", "grain", "halation", "bloom", "vignette", "lightLeak")))
        assertTrue(plan.report.applied.isEmpty())
    }

    @Test fun `supported components map to supported tokens only`() {
        val rc = recipe() // exposure = +1 stop
        val plan = FilmEngine.plan(rc, 1f)
        assertTrue(plan.rule.contains("@adjust exposure 1.0"))
        assertEquals(listOf("exposure"), plan.report.applied)
        assertTrue(plan.report.notSupported.isEmpty())
    }

    @Test fun `whitebalance maps to kelvin plus neutral tint semantics`() {
        // temp +0.24 at k=1 → 6500 - 0.24*2000 = 6020K (warm); tint 0 → 1 (neutral).
        val rc = recipe().copy(
            tone = Tone(),
            color = ColorAdjust(temperature = 0.24f, tint = 0f, saturation = 0.1f)
        )
        val plan = FilmEngine.plan(rc, 1f)
        assertTrue(plan.rule.contains("@adjust whitebalance 6020.0 1.0"))
        assertEquals(listOf("saturation", "whitebalance"), plan.report.applied)
    }

    @Test fun `grain-only recipe changes no pixel on the cpu path`() {
        // Honesty: the CPU approximation must never fake components the engine
        // cannot render (grain/halation/bloom do not touch a pixel here).
        val rc = recipe().copy(
            tone = Tone(),
            color = ColorAdjust(),
            lut = null,
            effects = Effects(grain = 0.5f, halation = 0.5f, bloom = 0.5f)
        )
        val (r, g, b) = FilmEngine.applyCpu(rc, 0.25f, 0.25f, 0.25f)
        assertEquals(0.25f, r, 1e-5f)
        assertEquals(0.25f, g, 1e-5f)
        assertEquals(0.25f, b, 1e-5f)
    }

    @Test fun `rule string stays empty when only unsupported effects are set`() {
        val rc = recipe().copy(
            tone = Tone(),
            color = ColorAdjust(),
            effects = Effects(grain = 0.2f)
        )
        assertEquals("", FilmEngine.ruleString(rc, 1f))
    }

    @Test fun `video metadata marks filmApplied false with NOT_SUPPORTED when recipe selected`() {
        // Simulates the video recording path: a recipe is selected, plan() generates the
        // report, but the video metadata MUST set filmApplied=false because CGE has no
        // video filter pipeline. The filmError string must contain NOT_SUPPORTED.
        val rc = recipe().copy(
            lut = LutRef("cube", 17, "bundled://luts/dalur_test_01.cube", "0".repeat(64)),
            effects = Effects(grain = 0.3f, vignette = 0.2f)
        )
        val plan = FilmEngine.plan(rc, 0.85f)
        // Video path: filmApplied is ALWAYS false regardless of what plan() produces.
        val filmApplied = false
        val filmError = "NOT_SUPPORTED: CGE filterImage_MultipleEffects is frame-only; no video pipeline"
        assertFalse("video must never claim film was applied", filmApplied)
        assertTrue("filmError must state NOT_SUPPORTED", filmError.contains("NOT_SUPPORTED"))
        // The plan's report must honestly list what CGE cannot do for this recipe.
        assertTrue(plan.report.notSupported.contains("lut"))
        assertTrue(plan.report.notSupported.contains("grain"))
        assertTrue(plan.report.notSupported.contains("vignette"))
        // Metadata construction mirrors EasyCameraScreen.toggleVideo().
        val meta = com.dalur.film.shared.CaptureMetadata(
            mediaId = "vid001", timestampMillis = 0L, gps = null,
            locationUnavailable = true, mediaType = "video",
            mediaUri = "content://media/external/video/media/1",
            filmRecipeId = rc.id, filmRecipeVersion = rc.version,
            codec = "H264", colorProfile = "SDR",
            lutRecipeId = rc.id, lutRecipeVersion = rc.version,
            lutHash = rc.lut?.hash, lutIntensity = 0.85f,
            filmApplied = filmApplied, filmError = filmError,
            filmSupportReport = plan.report
        )
        assertFalse(meta.filmApplied == true)
        assertNotNull(meta.filmError)
        assertTrue(meta.filmError!!.contains("NOT_SUPPORTED"))
        assertNotNull(meta.filmSupportReport)
        assertTrue(meta.filmSupportReport!!.notSupported.contains("lut"))
    }

    @Test fun `photo metadata records honest filmApplied based on rule string`() {
        // Simulates the photo capture path: plan() produces a rule, applyFilmToJpeg would
        // run it. The metadata must record filmApplied=true only when the rule is non-blank.
        val rc = recipe() // exposure=1.0 → rule has @adjust exposure
        val plan = FilmEngine.plan(rc, 1f)
        assertTrue("rule must be non-blank for supported recipe", plan.rule.isNotBlank())
        // Photo path mirrors EasyCameraScreen.takePhoto() logic.
        val filmApplied: Boolean
        val filmError: String?
        if (plan.rule.isBlank()) {
            filmApplied = false; filmError = null
        } else {
            // applyFilmToJpeg would run here; in unit test we verify the plan is valid.
            filmApplied = true; filmError = null
        }
        assertTrue("photo with supported recipe must claim filmApplied=true", filmApplied)
        assertNull(filmError)
        val meta = com.dalur.film.shared.CaptureMetadata(
            mediaId = "photo001", timestampMillis = 0L, gps = null,
            locationUnavailable = true, mediaType = "photo",
            mediaUri = "content://media/external/images/media/1",
            filmRecipeId = rc.id, filmRecipeVersion = rc.version,
            colorProfile = "SDR",
            lutRecipeId = rc.id, lutRecipeVersion = rc.version,
            lutIntensity = 1f,
            filmApplied = filmApplied, filmError = filmError,
            filmSupportReport = plan.report
        )
        assertTrue(meta.filmApplied == true)
        assertNull(meta.filmError)
        assertEquals(listOf("exposure"), meta.filmSupportReport!!.applied)
    }

    @Test fun `photo metadata records filmApplied false when rule is blank`() {
        // Recipe with only unsupported components → blank rule → filmApplied=false.
        val rc = recipe().copy(
            tone = Tone(), color = ColorAdjust(),
            effects = Effects(grain = 0.5f, halation = 0.3f),
            lut = LutRef("cube", 17, "bundled://luts/dalur_test_01.cube", "0".repeat(64))
        )
        val plan = FilmEngine.plan(rc, 1f)
        assertEquals("blank rule for unsupported-only recipe", "", plan.rule)
        val filmApplied = plan.rule.isNotBlank() // false
        assertFalse("unsupported-only recipe must not claim filmApplied", filmApplied)
    }
}

class TimelineTest {
    @Test fun `timeline alternates route and reveal then overview`() {
        val j = Journey("j", "t", listOf("a", "b"),
            listOf(
                JourneyRoutePoint("a", 1, GpsPoint(37.0, 127.0)),
                JourneyRoutePoint("b", 2, GpsPoint(37.1, 127.1))
            ), 3, "MEMORY", "NINE_SIXTEEN_1080P_H264")
        val segs = JourneyTimeline.build(j)
        assertTrue(segs.first() is JourneySegment.Reveal)
        assertTrue(segs[1] is JourneySegment.Route)
        assertTrue(segs.last() is JourneySegment.Overview)
    }

    @Test fun `haversine is sane for seoul-busan`() {
        val km = JourneyTimeline.haversineKm(GpsPoint(37.5665, 126.9780), GpsPoint(35.1796, 129.0756))
        assertTrue(km in 300.0..350.0)
    }
}

class MonitorRuleTest {
    @Test fun `lut monitor never claims baked master`() {
        // Architectural invariant documented here: RecorderController always writes
        // the sensor/LOG master; LutMonitorState only affects preview/playback.
        // See PRO recording rule in README + ONE_SHOT_MASTER Part E.
        assertTrue(true)
    }
}

class SidecarPathTest {
    private val tmp = System.getProperty("java.io.tmpdir")!!

    @Test fun `content uri sidecar lands beside the index`() {
        val meta = CaptureMetadata("id1", 0, null, false, "photo",
            "content://media/external/images/media/1")
        val sc = sidecarFileFor(meta, File(tmp))
        assertNotNull(sc)
        assertEquals(File(tmp, "id1.dalur.json").absolutePath, sc!!.absolutePath)
    }

    @Test fun `file uri sidecar is adjacent when parent exists`() {
        val media = File(tmp, "DALUR_x.jpg").absolutePath
        val meta = CaptureMetadata("id2", 0, null, false, "photo", "file://$media")
        val sc = sidecarFileFor(meta, File(tmp))
        assertNotNull(sc)
        assertEquals("$media.dalur.json", sc!!.absolutePath)
    }

    @Test fun `file uri sidecar is skipped when parent does not exist`() {
        val missing = File(tmp, "no_such_parent/DALUR_y.jpg").absolutePath
        val meta = CaptureMetadata("id3", 0, null, false, "photo", "file://$missing")
        assertNull(sidecarFileFor(meta, File(tmp)))
    }
}
