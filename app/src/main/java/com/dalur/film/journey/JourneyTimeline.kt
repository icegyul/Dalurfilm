package com.dalur.film.journey

import com.dalur.film.shared.*

/** Timeline segment model for the DALUR signature reveal animation. */
sealed interface JourneySegment {
    data class Route(val from: GpsPoint, val to: GpsPoint, val durationSec: Double) : JourneySegment
    data class Reveal(val mediaId: String, val holdSec: Double, val style: String) : JourneySegment
    data object Overview : JourneySegment
}

object JourneyTimeline {
    /**
     * Build render timeline: route -> reveal -> ... -> overview.
     * Mirrors google-timeline-visualizer concepts (path compression, camera presets)
     * but operates on DALUR-captured GPS only. No Google import, no account.
     */
    fun build(journey: Journey, style: String = journey.stylePreset): List<JourneySegment> {
        if (journey.routePoints.isEmpty()) return listOf(JourneySegment.Overview)
        val segs = mutableListOf<JourneySegment>()
        val pts = journey.routePoints
        val hold = journeyHoldSeconds(pts.size)
        for (i in pts.indices) {
            if (i > 0) {
                segs += JourneySegment.Route(pts[i - 1].gps, pts[i].gps, routeDurationSec(pts[i - 1], pts[i], style))
            }
            segs += JourneySegment.Reveal(pts[i].mediaId, hold, style)
        }
        segs += JourneySegment.Overview
        return segs
    }

    private fun routeDurationSec(a: JourneyRoutePoint, b: JourneyRoutePoint, style: String): Double {
        val distKm = haversineKm(a.gps, b.gps)
        val base = when (style) {
            "CINEMA" -> 2.6
            "POSTCARD" -> 1.6
            "JOURNAL" -> 1.8
            else -> 2.2 // MEMORY
        }
        // Long-trip compression: cap per-leg duration, keep total watchable.
        return (base + distKm * 0.4).coerceIn(1.2, 4.5)
    }

    fun haversineKm(a: GpsPoint, b: GpsPoint): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val s = Math.sin(dLat / 2).pow(2.0) +
            Math.cos(Math.toRadians(a.latitude)) * Math.cos(Math.toRadians(b.latitude)) *
            Math.sin(dLon / 2).pow(2.0)
        return 2 * r * Math.asin(Math.sqrt(s))
    }

    private fun Double.pow(e: Double) = Math.pow(this, e)
}
