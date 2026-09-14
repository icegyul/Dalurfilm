package com.dalur.film.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shredzone.commons.suncalc.SunTimes
import java.time.ZoneId
import java.time.ZonedDateTime

class SunCalcSanityTest {
    private val seoul = ZoneId.of("Asia/Seoul")

    @Test
    fun `seoul sunrise on autumnal equinox is a reasonable morning time`() {
        val times = SunTimes.compute()
            .on(ZonedDateTime.of(2026, 9, 22, 0, 0, 0, 0, seoul))
            .at(37.5665, 126.978)
            .execute()

        val rise = times.getRise()
        assertNotNull("sun must rise in Seoul in September", rise)
        rise!!

        assertEquals("rise must be on the requested local date", "2026-09-22", rise.toLocalDate().toString())
        assertEquals("rise must be reported in Seoul timezone", seoul, rise.zone)

        val ldt = rise.toLocalTime()
        val minutes = ldt.hour * 60 + ldt.minute
        assertTrue("sunrise $ldt should be between 05:00 and 08:00 KST (got $minutes min)", minutes in 300..480)
        assertTrue("sunrise must be before local noon", rise.isBefore(times.getNoon()))
    }
}