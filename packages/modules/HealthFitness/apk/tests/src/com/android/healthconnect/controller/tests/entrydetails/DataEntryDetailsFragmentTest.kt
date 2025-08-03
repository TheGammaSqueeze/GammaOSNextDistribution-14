/*
 * Copyright (C) 2023 The Android Open Source Project
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
 *
 *
 */

/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.tests.entrydetails

import android.content.Context
import android.health.connect.datatypes.ExerciseRoute
import androidx.lifecycle.MutableLiveData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.data.entries.FormattedEntry.ExerciseSessionEntry
import com.android.healthconnect.controller.data.entries.FormattedEntry.SeriesDataEntry
import com.android.healthconnect.controller.data.entries.FormattedEntry.SleepSessionEntry
import com.android.healthconnect.controller.entrydetails.DataEntryDetailsFragment
import com.android.healthconnect.controller.entrydetails.DataEntryDetailsViewModel
import com.android.healthconnect.controller.entrydetails.DataEntryDetailsViewModel.DateEntryFragmentState.Loading
import com.android.healthconnect.controller.entrydetails.DataEntryDetailsViewModel.DateEntryFragmentState.LoadingFailed
import com.android.healthconnect.controller.entrydetails.DataEntryDetailsViewModel.DateEntryFragmentState.WithData
import com.android.healthconnect.controller.permissions.data.HealthPermissionType.EXERCISE
import com.android.healthconnect.controller.permissions.data.HealthPermissionType.HEART_RATE
import com.android.healthconnect.controller.permissions.data.HealthPermissionType.SLEEP
import com.android.healthconnect.controller.shared.DataType
import com.android.healthconnect.controller.tests.utils.TestData.WARSAW_ROUTE
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.tests.utils.setLocale
import com.android.healthconnect.controller.utils.logging.DataEntriesElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as whenever
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify

@HiltAndroidTest
class DataEntryDetailsFragmentTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    @BindValue
    val viewModel: DataEntryDetailsViewModel = mock(DataEntryDetailsViewModel::class.java)
    private lateinit var context: Context
    @BindValue val healthConnectLogger: HealthConnectLogger = org.mockito.kotlin.mock()

    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().context
        context.setLocale(Locale.UK)
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))
    }

    @After
    fun tearDown() {
        reset(healthConnectLogger)
    }

    @Test
    fun dataEntriesDetailsInit_showsFragment() {
        whenever(viewModel.sessionData).thenReturn(MutableLiveData(WithData(emptyList())))

        launchFragment<DataEntryDetailsFragment>(
            DataEntryDetailsFragment.createBundle(
                permissionType = SLEEP, entryId = "1", showDataOrigin = true))

        onView(withId(R.id.loading)).check(matches(not(isDisplayed())))
    }

    @Test
    fun dataEntriesDetailsInit_error_showsError() {
        whenever(viewModel.sessionData).thenReturn(MutableLiveData(LoadingFailed))

        launchFragment<DataEntryDetailsFragment>(
            DataEntryDetailsFragment.createBundle(
                permissionType = SLEEP, entryId = "1", showDataOrigin = true))

        onView(withId(R.id.error_view)).check(matches(isDisplayed()))
    }

    @Test
    fun dataEntriesDetailsInit_loading_showsLoading() {
        whenever(viewModel.sessionData).thenReturn(MutableLiveData(Loading))

        launchFragment<DataEntryDetailsFragment>(
            DataEntryDetailsFragment.createBundle(
                permissionType = SLEEP, entryId = "1", showDataOrigin = true))

        onView(withId(R.id.loading)).check(matches(isDisplayed()))
    }

    @Test
    fun dataEntriesDetailsInit_withData_showsItem() {
        whenever(viewModel.sessionData)
            .thenReturn(
                MutableLiveData(
                    WithData(
                        listOf(
                            SleepSessionEntry(
                                uuid = "1",
                                header = "07:06 • TEST_APP_NAME",
                                headerA11y = "07:06 • TEST_APP_NAME",
                                title = "12 hour sleeping",
                                titleA11y = "12 hour sleeping",
                                dataType = DataType.SLEEP,
                                notes = "notes")))))

        launchFragment<DataEntryDetailsFragment>(
            DataEntryDetailsFragment.createBundle(
                permissionType = SLEEP, entryId = "1", showDataOrigin = true))

        onView(withText("07:06 • TEST_APP_NAME")).check(matches(isDisplayed()))
        onView(withText("12 hour sleeping")).check(matches(isDisplayed()))
        onView(withText("notes")).check(matches(isDisplayed()))

        verify(healthConnectLogger, atLeast(1)).setPageId(PageName.ENTRY_DETAILS_PAGE)
        verify(healthConnectLogger).logPageImpression()
        verify(healthConnectLogger).logImpression(DataEntriesElement.SLEEP_SESSION_ENTRY_BUTTON)
    }

    @Test
    fun dataEntriesDetailsInit_withDetails_showsItem_showsDetails() {
        val list = buildList {
            add(getFormattedSleepSession())
            addAll(getSleepStages())
        }
        whenever(viewModel.sessionData).thenReturn(MutableLiveData(WithData(list)))

        launchFragment<DataEntryDetailsFragment>(
            DataEntryDetailsFragment.createBundle(
                permissionType = SLEEP, entryId = "1", showDataOrigin = true))

        onView(withText("12 hour sleeping")).check(matches(isDisplayed()))
        onView(withText("6 hour light sleeping")).check(matches(isDisplayed()))
        onView(withText("6 hour deep sleeping")).check(matches(isDisplayed()))
    }

    @Test
    fun dataEntriesDetailsInit_withHeartRate_showsItem_showsDetails() {
        val list = buildList { add(getFormattedSeriesData()) }
        whenever(viewModel.sessionData).thenReturn(MutableLiveData(WithData(list)))

        launchFragment<DataEntryDetailsFragment>(
            DataEntryDetailsFragment.createBundle(
                permissionType = HEART_RATE, entryId = "1", showDataOrigin = true))

        onView(withText("07:06 - 8:06 • TEST_APP_NAME")).check(matches(isDisplayed()))
        onView(withText("100 bpm")).check(matches(isDisplayed()))
        verify(healthConnectLogger).logImpression(DataEntriesElement.DATA_ENTRY_VIEW)
    }

    @Test
    fun dataEntriesDetailsInit_withRouteDetails_showsMapView() {
        val list = buildList { add(getFormattedExerciseSession(showSession = true)) }
        whenever(viewModel.sessionData).thenReturn(MutableLiveData(WithData(list)))
        launchFragment<DataEntryDetailsFragment>(
            DataEntryDetailsFragment.createBundle(
                permissionType = EXERCISE, entryId = "1", showDataOrigin = true))

        onView(withText("12 hour running")).check(matches(isDisplayed()))
        onView(withId(R.id.map_view)).check(matches(isDisplayed()))
        verify(healthConnectLogger).logImpression(DataEntriesElement.EXERCISE_SESSION_ENTRY_BUTTON)
        verify(healthConnectLogger).logImpression(DataEntriesElement.EXERCISE_SESSION_MAP_VIEW)
    }

    @Test
    fun dataEntriesDetailsInit_noRouteDetails_hidesMapView() {
        val list = buildList { add(getFormattedExerciseSession(showSession = false)) }
        whenever(viewModel.sessionData).thenReturn(MutableLiveData(WithData(list)))
        launchFragment<DataEntryDetailsFragment>(
            DataEntryDetailsFragment.createBundle(
                permissionType = EXERCISE, entryId = "1", showDataOrigin = true))

        onView(withText("12 hour running")).check(matches(isDisplayed()))
        onView(withId(R.id.map_view)).check(matches(not(isDisplayed())))
    }

    private fun getSleepStages(): List<FormattedEntry> {
        return listOf(
            FormattedEntry.SessionHeader(header = "Stages"),
            FormattedEntry.FormattedSessionDetail(
                uuid = "1",
                header = "07:06 • TEST_APP_NAME",
                headerA11y = "07:06 • TEST_APP_NAME",
                title = "6 hour light sleeping",
                titleA11y = "6 hour light sleeping",
            ),
            FormattedEntry.FormattedSessionDetail(
                uuid = "1",
                header = "07:06 • TEST_APP_NAME",
                headerA11y = "07:06 • TEST_APP_NAME",
                title = "6 hour deep sleeping",
                titleA11y = "6 hour deep sleeping",
            ))
    }

    private fun getFormattedSleepSession(): SleepSessionEntry {
        return SleepSessionEntry(
            uuid = "1",
            header = "07:06 • TEST_APP_NAME",
            headerA11y = "07:06 • TEST_APP_NAME",
            title = "12 hour sleeping",
            titleA11y = "12 hour sleeping",
            dataType = DataType.SLEEP,
            notes = "notes")
    }

    private fun getFormattedExerciseSession(showSession: Boolean): ExerciseSessionEntry {
        return ExerciseSessionEntry(
            uuid = "1",
            header = "07:06 • TEST_APP_NAME",
            headerA11y = "07:06 • TEST_APP_NAME",
            title = "12 hour running",
            titleA11y = "12 hour running",
            dataType = DataType.EXERCISE,
            notes = "notes",
            route =
                if (showSession) {
                    ExerciseRoute(WARSAW_ROUTE)
                } else {
                    null
                })
    }

    private fun getFormattedSeriesData(): SeriesDataEntry {
        return SeriesDataEntry(
            uuid = "1",
            header = "07:06 - 8:06 • TEST_APP_NAME",
            headerA11y = "07:06 - 8:06 • TEST_APP_NAME",
            title = "100 bpm",
            titleA11y = "100 beats per minute",
            dataType = DataType.HEART_RATE)
    }
}
