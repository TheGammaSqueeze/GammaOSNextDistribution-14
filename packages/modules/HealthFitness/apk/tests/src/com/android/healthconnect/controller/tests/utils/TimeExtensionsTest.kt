/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.tests.utils

import com.android.healthconnect.controller.utils.atStartOfDay
import com.android.healthconnect.controller.utils.getInstant
import com.android.healthconnect.controller.utils.isAtLeastOneDayAfter
import com.android.healthconnect.controller.utils.isOnDayAfter
import com.android.healthconnect.controller.utils.isOnDayBefore
import com.android.healthconnect.controller.utils.isOnSameDay
import com.android.healthconnect.controller.utils.randomInstant
import com.android.healthconnect.controller.utils.toInstant
import com.android.healthconnect.controller.utils.toInstantAtStartOfDay
import com.android.healthconnect.controller.utils.toLocalDate
import com.android.healthconnect.controller.utils.toLocalDateTime
import com.android.healthconnect.controller.utils.toLocalTime
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone
import org.junit.Test

class TimeExtensionsTest {

    @Test
    fun getInstant_returnsCorrectInstant() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))

        assertThat(getInstant(2022, 10, 23).toEpochMilli()).isEqualTo(1666483200000)
    }

    @Test
    fun longToInstant_returnsCorrectInstant() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))

        val testLong = 1698317930L
        assertThat(testLong.toInstant()).isEqualTo(Instant.ofEpochMilli(testLong))
    }

    @Test
    fun instantToLocalDate_returnsCorrectLocalDate() {
        // UTC - 7
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("America/Los_Angeles")))

        val instant = Instant.parse("2023-02-14T05:00:00Z")
        val expected = LocalDate.of(2023, 2, 13)
        val actual = instant.toLocalDate()
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun instantToLocalTime_returnsCorrectLocalTime() {
        // UTC + 9
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Tokyo")))

        val instant = Instant.parse("2023-02-14T20:00:00Z")
        val expected = LocalTime.of(5, 0, 0)
        val actual = instant.toLocalTime()
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun instantIsOnSameDay_whenOtherInstantOnSameLocalDate_returnsTrue() {
        // UTC + 9
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Tokyo")))

        val thisInstant = Instant.parse("2023-02-14T20:00:00Z")
        val otherInstant = Instant.parse("2023-02-15T02:00:00Z")
        assertThat(thisInstant.isOnSameDay(otherInstant)).isTrue()
    }

    @Test
    fun instantIsOnSameDay_whenOtherInstantNotOnSameLocalDate_returnsFalse() {
        // UTC + 9
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Tokyo")))

        val thisInstant = Instant.parse("2023-02-14T10:00:00Z")
        val otherInstant = Instant.parse("2023-02-14T20:00:00Z")
        assertThat(thisInstant.isOnSameDay(otherInstant)).isFalse()
    }

    @Test
    fun instantIsOnDayBefore_whenOtherInstantIsOnDayAfter_returnsTrue() {
        // UTC + 2
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Africa/Cairo")))

        val thisInstant = Instant.parse("2023-02-14T10:00:00Z")
        val otherInstant = Instant.parse("2023-02-15T20:00:00Z")
        assertThat(thisInstant.isOnDayBefore(otherInstant)).isTrue()
    }

    @Test
    fun instantIsOnDayBefore_whenOtherInstantIsOnSameDay_returnsFalse() {
        // UTC + 2
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Africa/Cairo")))

        val thisInstant = Instant.parse("2023-02-14T10:00:00Z")
        val otherInstant = Instant.parse("2023-02-14T20:00:00Z")
        assertThat(thisInstant.isOnDayBefore(otherInstant)).isFalse()
    }

    @Test
    fun instantIsOnDayAfter_whenOtherInstantIsOnDayBefore_returnsTrue() {
        // UTC + 5:30
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Kolkata")))

        val thisInstant = Instant.parse("2023-02-14T10:00:00Z")
        val otherInstant = Instant.parse("2023-02-13T12:00:00Z")
        assertThat(thisInstant.isOnDayAfter(otherInstant)).isTrue()
    }

    @Test
    fun instantIsOnDayAfter_whenOtherInstantIsOnDayAfter_returnsFalse() {
        // UTC + 5:30
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Kolkata")))

        val thisInstant = Instant.parse("2023-02-14T10:00:00Z")
        val otherInstant = Instant.parse("2023-02-15T12:00:00Z")
        assertThat(thisInstant.isOnDayAfter(otherInstant)).isFalse()
    }

    @Test
    fun instantAtStartOfDay_returnsLocalizedStartOfDay() {
        // UTC + 8
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Australia/Perth")))

        val testInstant = Instant.parse("2023-02-14T20:00:00Z")
        val expectedInstant = Instant.parse("2023-02-14T16:00:00Z")

        assertThat(testInstant.atStartOfDay()).isEqualTo(expectedInstant)
    }

    @Test
    fun instantIsAtLeastOneDayAfter_whenOtherInstantOneDayBefore_returnsTrue() {
        // UTC - 7
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("America/Denver")))

        val thisInstant = Instant.parse("2023-02-14T10:00:00Z")
        val otherInstant = Instant.parse("2023-02-13T12:00:00Z")
        assertThat(thisInstant.isAtLeastOneDayAfter(otherInstant)).isTrue()
    }

    @Test
    fun instantIsAtLeastOneDayAfter_whenOtherInstantTwoDaysBefore_returnsTrue() {
        // UTC - 7
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("America/Denver")))

        val thisInstant = Instant.parse("2023-02-15T10:00:00Z")
        val otherInstant = Instant.parse("2023-02-13T12:00:00Z")
        assertThat(thisInstant.isAtLeastOneDayAfter(otherInstant)).isTrue()
    }

    @Test
    fun instantIsAtLeastOneDayAfter_whenOtherInstantAtLeastOnSameDay_returnsFalse() {
        // UTC - 7
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("America/Denver")))

        val thisInstant = Instant.parse("2023-02-14T03:00:00Z")
        val otherInstant = Instant.parse("2023-02-13T12:00:00Z")
        assertThat(thisInstant.isAtLeastOneDayAfter(otherInstant)).isFalse()
    }

    @Test
    fun localDateToInstantAtStartOfDay_returnsCorrectInstant() {
        // UTC - 3
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("America/Sao_Paulo")))

        val testLocalDate = LocalDate.of(2021, 10, 1)
        val expectedInstant = Instant.parse("2021-10-01T03:00:00Z")
        assertThat(testLocalDate.toInstantAtStartOfDay()).isEqualTo(expectedInstant)
    }

    @Test
    fun instantToLocalDateTime_returnsLocalizedDateTime() {
        // UTC + 8
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Australia/Perth")))

        val testInstant = Instant.parse("2023-05-18T20:00:00Z")
        val expectedLocalDateTime = LocalDateTime.of(2023, 5, 19, 4, 0)

        assertThat(testInstant.toLocalDateTime()).isEqualTo(expectedLocalDateTime)
    }

    @Test
    fun localDateRandomInstant_returnsInstantOnTheLocalDate() {
        // UTC + 5:30
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Kolkata")))

        val localDate = LocalDate.of(2023, 7, 18)
        val randomInstant = localDate.randomInstant()

        assertThat(randomInstant.isOnSameDay(localDate.toInstantAtStartOfDay())).isTrue()
    }

    @Test
    fun localDateTimeToInstant_returnsCorrectInstant() {
        // UTC - 3
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("America/Sao_Paulo")))

        val testLocalDateTime = LocalDateTime.of(2021, 10, 1, 18, 0)
        val expectedInstant = Instant.parse("2021-10-01T21:00:00Z")
        assertThat(testLocalDateTime.toInstant()).isEqualTo(expectedInstant)
    }
}
