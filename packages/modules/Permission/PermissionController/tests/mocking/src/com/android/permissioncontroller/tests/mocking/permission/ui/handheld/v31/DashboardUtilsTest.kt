/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.permissioncontroller.tests.mocking.permission.ui.handheld.v31

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.permissioncontroller.permission.ui.handheld.v31.getDurationUsedStr
import com.android.permissioncontroller.permission.ui.handheld.v31.getTimeDiffStr
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith

/** A suite of unit tests to test the permission dashboard utils. */
@RunWith(AndroidJUnit4::class)
class DashboardUtilsTest {

    @Test
    fun getTimeDiffStr_durationSecondsOne() {
        val duration: Long = TimeUnit.SECONDS.toMillis(1L)
        assertThat(getTimeDiffStr(ApplicationProvider.getApplicationContext(), duration))
            .isEqualTo("1 second")
    }

    @Test
    fun getTimeDiffStr_durationSecondsOther() {
        val duration: Long = TimeUnit.SECONDS.toMillis(59L)
        assertThat(getTimeDiffStr(ApplicationProvider.getApplicationContext(), duration))
            .isEqualTo("59 seconds")
    }

    @Test
    fun getTimeDiffStr_durationMinutesOne() {
        val duration: Long = TimeUnit.MINUTES.toMillis(1L)
        assertThat(getTimeDiffStr(ApplicationProvider.getApplicationContext(), duration))
            .isEqualTo("1 minute")
    }

    @Test
    fun getTimeDiffStr_durationMinutesOther() {
        val duration: Long = TimeUnit.MINUTES.toMillis(59L)
        assertThat(getTimeDiffStr(ApplicationProvider.getApplicationContext(), duration))
            .isEqualTo("59 minutes")
    }

    @Test
    fun getTimeDiffStr_durationHoursOne() {
        val duration: Long = TimeUnit.HOURS.toMillis(1L)
        assertThat(getTimeDiffStr(ApplicationProvider.getApplicationContext(), duration))
            .isEqualTo("1 hour")
    }

    @Test
    fun getTimeDiffStr_durationHoursOther() {
        val duration: Long = TimeUnit.HOURS.toMillis(23L)
        assertThat(getTimeDiffStr(ApplicationProvider.getApplicationContext(), duration))
            .isEqualTo("23 hours")
    }

    @Test
    fun getTimeDiffStr_durationDaysOne() {
        val duration: Long = TimeUnit.DAYS.toMillis(1L)
        assertThat(getTimeDiffStr(ApplicationProvider.getApplicationContext(), duration))
            .isEqualTo("1 day")
    }

    @Test
    fun getTimeDiffStr_durationDaysOther() {
        val duration: Long = TimeUnit.DAYS.toMillis(2L)
        assertThat(getTimeDiffStr(ApplicationProvider.getApplicationContext(), duration))
            .isEqualTo("2 days")
    }

    @Test
    fun getDurationUsedStr_durationSecondsOne() {
        val duration: Long = TimeUnit.SECONDS.toMillis(1L)
        assertThat(getDurationUsedStr(ApplicationProvider.getApplicationContext(), duration))
            .isEqualTo("1 sec")
    }

    @Test
    fun getDurationUsedStr_durationSecondsOther() {
        val duration: Long = TimeUnit.SECONDS.toMillis(59L)
        assertThat(getDurationUsedStr(ApplicationProvider.getApplicationContext(), duration))
            .isEqualTo("59 secs")
    }

    @Test
    fun getDurationUsedStr_durationMinutesOne() {
        val duration: Long = TimeUnit.MINUTES.toMillis(1L)
        assertThat(getDurationUsedStr(ApplicationProvider.getApplicationContext(), duration))
            .isEqualTo("1 min")
    }

    @Test
    fun getDurationUsedStr_durationMinutesOther() {
        val duration: Long = TimeUnit.MINUTES.toMillis(59L)
        assertThat(getDurationUsedStr(ApplicationProvider.getApplicationContext(), duration))
            .isEqualTo("59 mins")
    }

    @Test
    fun getDurationUsedStr_durationHoursOne() {
        val duration: Long = TimeUnit.HOURS.toMillis(1L)
        assertThat(getDurationUsedStr(ApplicationProvider.getApplicationContext(), duration))
            .isEqualTo("1 hour")
    }

    @Test
    fun getDurationUsedStr_durationHoursOther() {
        val duration: Long = TimeUnit.HOURS.toMillis(23L)
        assertThat(getDurationUsedStr(ApplicationProvider.getApplicationContext(), duration))
            .isEqualTo("23 hours")
    }

    @Test
    fun getDurationUsedStr_durationDaysOne() {
        val duration: Long = TimeUnit.DAYS.toMillis(1L)
        assertThat(getDurationUsedStr(ApplicationProvider.getApplicationContext(), duration))
            .isEqualTo("1 day")
    }

    @Test
    fun getDurationUsedStr_durationDaysOther() {
        val duration: Long = TimeUnit.DAYS.toMillis(2L)
        assertThat(getDurationUsedStr(ApplicationProvider.getApplicationContext(), duration))
            .isEqualTo("2 days")
    }
}
