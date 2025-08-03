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
package com.android.healthconnect.controller.tests.data.entries.datenavigation

import android.content.Context
import android.view.View
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.entries.datenavigation.DateNavigationPeriod
import com.android.healthconnect.controller.data.entries.datenavigation.DateNavigationView
import com.android.healthconnect.controller.tests.utils.MIDNIGHT
import com.android.healthconnect.controller.tests.utils.NOW
import com.android.healthconnect.controller.tests.utils.TestTimeSource
import com.android.healthconnect.controller.tests.utils.setLocale
import com.android.healthconnect.controller.utils.TimeSource
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Duration
import java.util.Locale
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

@HiltAndroidTest
class DateNavigationViewTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    private lateinit var dateNavigationView: DateNavigationView
    private lateinit var previousDayButton: ImageButton
    private lateinit var nextDayButton: ImageButton
    private lateinit var datePickerSpinner: Spinner

    private lateinit var context: Context
    private val dateChangedListener =
        Mockito.mock(DateNavigationView.OnDateChangedListener::class.java)
    private val timeSource: TimeSource = TestTimeSource

    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().context
        context.setLocale(Locale.US)

        dateNavigationView =
            DateNavigationView(context = context, attrs = null, timeSource = timeSource)
        datePickerSpinner = dateNavigationView.findViewById(R.id.date_picker_spinner) as Spinner
        previousDayButton = dateNavigationView.findViewById(R.id.navigation_previous_day)
        nextDayButton = dateNavigationView.findViewById(R.id.navigation_next_day)
    }

    @Test
    fun initDateNavigationPreference_titleSetToToday() {
        assertThat(datePickerSpinner.visibility).isEqualTo(View.VISIBLE)

        assertSpinnerView("Today")
    }

    @Test
    fun setPeriodToDay_yesterday_showsYesterday() {
        dateNavigationView.setDate(NOW.minus(Duration.ofDays(1)))

        assertSpinnerView("Yesterday")
    }

    @Test
    fun setPeriodToDay_twoDaysAgo_showsDate() {
        dateNavigationView.setDate(NOW.minus(Duration.ofDays(2)))

        assertSpinnerView("Tue, Oct 18")
    }

    @Test
    fun setPeriodToDay_thisTimeLastYear_showsYear() {
        dateNavigationView.setDate(NOW.minus(Duration.ofDays(365)))

        assertSpinnerView("Wed, Oct 20, 2021")
    }

    @Test
    fun setPeriodToDay_lastYear_showsYear() {
        dateNavigationView.setDate(NOW.minus(Duration.ofDays(367)))

        assertSpinnerView("Mon, Oct 18, 2021")
    }

    @Test
    fun setPeriodToWeek_navigateToMonday_showsThisWeek() {
        dateNavigationView.setDate(NOW.minus(Duration.ofDays(3)))
        dateNavigationView.setPeriod(DateNavigationPeriod.PERIOD_WEEK)

        assertSpinnerView("This week")
    }

    @Test
    fun setPeriodToWeek_navigateToSunday_showsLastWeek() {
        dateNavigationView.setDate(NOW.minus(Duration.ofDays(4)))
        dateNavigationView.setPeriod(DateNavigationPeriod.PERIOD_WEEK)

        assertSpinnerView("Last week")
    }

    @Test
    fun setPeriodToWeek_twoWeeksAgo_showsTwoWeeksAgo() {
        dateNavigationView.setDate(NOW.minus(Duration.ofDays(14)))
        dateNavigationView.setPeriod(DateNavigationPeriod.PERIOD_WEEK)

        assertSpinnerView("Oct 3 – 9")
    }

    @Test
    fun setPeriodToWeek_thisTimeLastYear_showsYear() {
        dateNavigationView.setDate(NOW.minus(Duration.ofDays(365)))
        dateNavigationView.setPeriod(DateNavigationPeriod.PERIOD_WEEK)

        assertSpinnerView("Oct 18 – 24, 2021")
    }

    @Test
    fun setPeriodToWeek_lastYear_showsYear() {
        dateNavigationView.setDate(NOW.minus(Duration.ofDays(379)))
        dateNavigationView.setPeriod(DateNavigationPeriod.PERIOD_WEEK)

        assertSpinnerView("Oct 4 – 10, 2021")
    }

    @Test
    fun setPeriodToWeek_spansAcrossMonths_showsBothMonths() {
        dateNavigationView.setDate(NOW.minus(Duration.ofDays(21)))
        dateNavigationView.setPeriod(DateNavigationPeriod.PERIOD_WEEK)

        assertSpinnerView("Sep 26 – Oct 2")
    }

    @Test
    fun setPeriodToMonth_thisMonth_showsThisMonth() {
        dateNavigationView.setPeriod(DateNavigationPeriod.PERIOD_MONTH)

        assertSpinnerView("This month")
    }

    @Test
    fun setPeriodToMonth_navigateToFirstDayOfMonth_showsThisMonth() {
        dateNavigationView.setDate(NOW.minus(Duration.ofDays(19)))
        dateNavigationView.setPeriod(DateNavigationPeriod.PERIOD_MONTH)

        assertSpinnerView("This month")
    }

    @Test
    fun setPeriodToMonth_navigateToLastDayOfPreviousMonth_showsLastMonth() {
        dateNavigationView.setDate(NOW.minus(Duration.ofDays(21)))
        dateNavigationView.setPeriod(DateNavigationPeriod.PERIOD_MONTH)

        assertSpinnerView("Last month")
    }

    @Test
    fun setPeriodToMonth_twoMonthsAgo_showsTwoMonthsAgo() {
        dateNavigationView.setDate(NOW.minus(Duration.ofDays(60)))
        dateNavigationView.setPeriod(DateNavigationPeriod.PERIOD_MONTH)

        assertSpinnerView("August")
    }

    @Test
    fun setPeriodToMonth_thisTimeLastYear_showsYear() {
        dateNavigationView.setDate(NOW.minus(Duration.ofDays(365)))
        dateNavigationView.setPeriod(DateNavigationPeriod.PERIOD_MONTH)

        assertSpinnerView("October 2021")
    }

    @Test
    fun setPeriodToMonth_lastYear_showsYear() {
        dateNavigationView.setDate(NOW.minus(Duration.ofDays(425)))
        dateNavigationView.setPeriod(DateNavigationPeriod.PERIOD_MONTH)

        assertSpinnerView("August 2021")
    }

    @Test
    fun initDateNavigationPreference_nextNavigationDisabled() {
        assertThat(nextDayButton.visibility).isEqualTo(View.VISIBLE)
        assertThat(nextDayButton.isEnabled).isEqualTo(false)
    }

    @Test
    fun initDateNavigationPreference_prevNavigationEnabled() {
        assertThat(previousDayButton.visibility).isEqualTo(View.VISIBLE)
        assertThat(previousDayButton.isEnabled).isEqualTo(true)
    }

    @Test
    fun setDate_withValidFutureDates_nextButtonIsEnabled() {
        dateNavigationView.setDate(NOW.minus(Duration.ofDays(1)))

        assertThat(nextDayButton.isEnabled).isEqualTo(true)
    }

    @Test
    fun onDateChanged_setDate_listenerIsCalled() {
        dateNavigationView.setPeriod(DateNavigationPeriod.PERIOD_DAY)
        dateNavigationView.setDateChangedListener(dateChangedListener)

        val newDate = NOW.minus(Duration.ofDays(1))
        dateNavigationView.setDate(newDate)

        val expectedDate = MIDNIGHT.minus(Duration.ofDays(1))
        Mockito.verify(dateChangedListener)
            .onDateChanged(expectedDate, DateNavigationPeriod.PERIOD_DAY)
    }

    @Test
    fun onDateChanged_setPeriod_listenerIsCalled() {
        dateNavigationView.setDate(NOW)
        dateNavigationView.setDateChangedListener(dateChangedListener)

        dateNavigationView.setPeriod(DateNavigationPeriod.PERIOD_WEEK)

        // Expected date is the beginning of the week.
        val expectedDate = MIDNIGHT.minus(Duration.ofDays(3))
        Mockito.verify(dateChangedListener)
            .onDateChanged(expectedDate, DateNavigationPeriod.PERIOD_WEEK)
    }

    @Test
    fun checkDropDowns_dayWeekMonthShown() {
        assertSpinnerDropDownView("Day", position = 0)
        assertSpinnerDropDownView("Week", position = 1)
        assertSpinnerDropDownView("Month", position = 2)
    }

    private fun assertSpinnerView(expected: String) {
        val textView: TextView =
            datePickerSpinner.adapter.getView(
                datePickerSpinner.selectedItemPosition, null, datePickerSpinner) as TextView
        assertThat(textView.text).isEqualTo(expected)
    }

    private fun assertSpinnerDropDownView(expected: String, position: Int) {
        val textView: TextView =
            datePickerSpinner.adapter.getDropDownView(position, null, datePickerSpinner) as TextView
        assertThat(textView.text).isEqualTo(expected)
    }
}
