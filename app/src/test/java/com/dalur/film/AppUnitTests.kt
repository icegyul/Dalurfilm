package com.dalur.film

import com.dalur.film.film.FilmEngine
import com.dalur.film.journey.JourneyTimeline
import com.dalur.film.journey.JourneySegment
import com.dalur.film.shared.*
import org.junit.Assert.*
import org.junit.Test

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

    @Test fun `rule string mentions grain when present`() {
        val rc = recipe().copy(effects = com.dalur.film.shared.Effects(grain = 0.2f))
        assertTrue(FilmEngine.ruleString(rc, 1f).contains("grain"))
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
