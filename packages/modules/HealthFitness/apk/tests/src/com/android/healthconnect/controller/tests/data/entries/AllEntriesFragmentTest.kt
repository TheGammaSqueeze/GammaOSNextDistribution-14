/**
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.healthconnect.controller.tests.data.entries

import android.content.Context
import android.content.pm.ActivityInfo
import androidx.core.os.bundleOf
import androidx.lifecycle.MutableLiveData
import androidx.test.espresso.Espresso.onIdle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.entries.AllEntriesFragment
import com.android.healthconnect.controller.data.entries.EntriesViewModel
import com.android.healthconnect.controller.data.entries.EntriesViewModel.EntriesFragmentState.Empty
import com.android.healthconnect.controller.data.entries.EntriesViewModel.EntriesFragmentState.Loading
import com.android.healthconnect.controller.data.entries.EntriesViewModel.EntriesFragmentState.LoadingFailed
import com.android.healthconnect.controller.data.entries.EntriesViewModel.EntriesFragmentState.With
import com.android.healthconnect.controller.data.entries.FormattedEntry.FormattedDataEntry
import com.android.healthconnect.controller.data.entries.datenavigation.DateNavigationPeriod
import com.android.healthconnect.controller.permissions.data.HealthPermissionType.STEPS
import com.android.healthconnect.controller.permissiontypes.HealthPermissionTypesFragment.Companion.PERMISSION_TYPE_KEY
import com.android.healthconnect.controller.shared.DataType
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.tests.utils.setLocale
import com.android.healthconnect.controller.tests.utils.withIndex
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

@HiltAndroidTest
class AllEntriesFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @BindValue val viewModel: EntriesViewModel = Mockito.mock(EntriesViewModel::class.java)

    private lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().context
        context.setLocale(Locale.UK)
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))

        Mockito.`when`(viewModel.currentSelectedDate).thenReturn(MutableLiveData())
        Mockito.`when`(viewModel.period)
            .thenReturn(MutableLiveData(DateNavigationPeriod.PERIOD_DAY))
        Mockito.`when`(viewModel.appInfo)
            .thenReturn(
                MutableLiveData(
                    AppMetadata(
                        TEST_APP_PACKAGE_NAME,
                        TEST_APP_NAME,
                        context.getDrawable(R.drawable.health_connect_logo))))
    }

    @Test
    fun appEntriesInit_showsDateNavigationPreference() {
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(With(emptyList())))

        val scenario =
                launchFragment<AllEntriesFragment>(
                        bundleOf(PERMISSION_TYPE_KEY to STEPS))
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(withId(R.id.date_picker_spinner)).check(matches(isDisplayed()))
    }

    @Test
    fun allEntriesInit_noData_showsNoData() {
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(Empty))

        val scenario =
                launchFragment<AllEntriesFragment>(
                        bundleOf(PERMISSION_TYPE_KEY to STEPS))
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(withId(R.id.no_data_view)).check(matches(isDisplayed()))
    }

    @Test
    fun appEntriesInit_error_showsErrorView() {
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(LoadingFailed))

        val scenario =
                launchFragment<AllEntriesFragment>(
                        bundleOf(PERMISSION_TYPE_KEY to STEPS))
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(withId(R.id.error_view)).check(matches(isDisplayed()))
    }

    @Test
    fun appEntriesInit_loading_showsLoading() {
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(Loading))

        val scenario =
                launchFragment<AllEntriesFragment>(
                        bundleOf(PERMISSION_TYPE_KEY to STEPS))
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(withId(R.id.loading)).check(matches(isDisplayed()))
    }

    @Test
    fun appEntriesInit_withData_showsListOfEntries() {
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(With(FORMATTED_STEPS_LIST)))

        val scenario =
                launchFragment<AllEntriesFragment>(
                        bundleOf(PERMISSION_TYPE_KEY to STEPS))
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(withText("7:06 - 7:06")).check(matches(isDisplayed()))
        onView(withText("12 steps")).check(matches(isDisplayed()))
        onView(withText("8:06 - 8:06")).check(matches(isDisplayed()))
        onView(withText("15 steps")).check(matches(isDisplayed()))
    }

    @Test
    fun appEntries_withData_notShowingDeleteAction() {
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(With(FORMATTED_STEPS_LIST)))

        val scenario =
                launchFragment<AllEntriesFragment>(
                        bundleOf(PERMISSION_TYPE_KEY to STEPS))
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(withIndex(withId(R.id.item_data_entry_delete), 0))
                .check(matches(withEffectiveVisibility(ViewMatchers.Visibility.GONE)))
    }

    @Test
    fun appEntries_withData_onOrientationChange() {
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(With(FORMATTED_STEPS_LIST)))

        val scenario =
                launchFragment<AllEntriesFragment>(
                        bundleOf(PERMISSION_TYPE_KEY to STEPS))
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(withText("7:06 - 7:06")).check(matches(isDisplayed()))
        onView(withText("12 steps")).check(matches(isDisplayed()))
        onView(withText("8:06 - 8:06")).check(matches(isDisplayed()))
        onView(withText("15 steps")).check(matches(isDisplayed()))

        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        onIdle()
        onView(withText("7:06 - 7:06")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("12 steps")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("8:06 - 8:06")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("15 steps")).perform(scrollTo()).check(matches(isDisplayed()))
    }

}

private val FORMATTED_STEPS_LIST =
    listOf(
        FormattedDataEntry(
            uuid = "test_id",
            header = "7:06 - 7:06",
            headerA11y = "from 7:06 to 7:06",
            title = "12 steps",
            titleA11y = "12 steps",
            dataType = DataType.STEPS),
        FormattedDataEntry(
            uuid = "test_id",
            header = "8:06 - 8:06",
            headerA11y = "from 8:06 to 8:06",
            title = "15 steps",
            titleA11y = "15 steps",
            dataType = DataType.STEPS))
