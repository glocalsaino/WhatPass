package com.glocalsaino.miwallet.unittest

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import com.glocalsaino.miwallet.Tracker
import com.glocalsaino.miwallet.model.ApplePassbookQuirkCorrector
import com.glocalsaino.miwallet.model.pass.PassField
import com.glocalsaino.miwallet.model.pass.PassImpl
import org.mockito.Mockito
import org.threeten.bp.ZonedDateTime
import java.util.*

private const val DATE_PROBE = "2016-07-06T21:00:00-04:00"

class TheAppleStyleQuirkCorrector {
    private val tested = ApplePassbookQuirkCorrector(Mockito.mock(Tracker::class.java))

    @Test
    fun testThatItDoesNothingWithNoField() {
        val pass = PassImpl(UUID.randomUUID().toString())

        tested.correctQuirks(pass)

        assertThat(pass.calendarTimespan).isNull()
    }


    @Test
    fun testThatDateIsExtracted() {
        val pass = PassImpl(UUID.randomUUID().toString())

        pass.fields = mutableListOf(PassField("date", "foo", DATE_PROBE, false))

        tested.correctQuirks(pass)

        assertThat(pass.calendarTimespan!!.from).isEqualTo(ZonedDateTime.parse(DATE_PROBE))
    }

    @Test
    fun testThatInvalidDateIsIgnored() {
        val pass = PassImpl(UUID.randomUUID().toString())

        pass.fields = mutableListOf(PassField("date", "foo", "invalid", false))

        tested.correctQuirks(pass)

        assertThat(pass.calendarTimespan).isNull()
    }

    @Test
    fun testThatDateIsExtractedAfterWrongDatesBefore() {
        val pass = PassImpl(UUID.randomUUID().toString())

        pass.fields = mutableListOf(PassField("date", "foo", "invalid", false), PassField("date", "foo", DATE_PROBE, false))

        tested.correctQuirks(pass)

        assertThat(pass.calendarTimespan!!.from).isEqualTo(ZonedDateTime.parse(DATE_PROBE))
    }

}
