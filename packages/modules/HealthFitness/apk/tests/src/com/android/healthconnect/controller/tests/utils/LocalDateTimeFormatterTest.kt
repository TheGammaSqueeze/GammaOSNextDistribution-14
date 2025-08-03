package com.android.healthconnect.controller.tests.utils

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.provider.Settings.System
import com.android.compatibility.common.util.UserSettings
import com.android.compatibility.common.util.UserSettings.Namespace
import com.android.healthconnect.controller.utils.LocalDateTimeFormatter
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class LocalDateTimeFormatterTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject @ApplicationContext lateinit var context: Context
    private lateinit var systemSettings: UserSettings

    private var previousTimeFormat: String? = null
    private var previousDefaultTimeZone: TimeZone? = null
    private var previousLocale: Locale? = null

    private val time = Instant.parse("2022-10-20T14:06:05.432Z")

    @Before
    fun setup() {
        hiltRule.inject()

        systemSettings = UserSettings(context, Namespace.SYSTEM)
        previousTimeFormat = systemSettings.get(System.TIME_12_24)
        if (previousTimeFormat != null) {
            // Clear setting so locale-defined time format is used.
            systemSettings.syncSet(System.TIME_12_24, null)
        }

        previousDefaultTimeZone = TimeZone.getDefault()
        previousLocale = Locale.getDefault()

        // set time zone
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        if (previousTimeFormat != null) {
            systemSettings.syncSet(System.TIME_12_24, previousTimeFormat)
        }
        TimeZone.setDefault(previousDefaultTimeZone)
        previousLocale?.let { locale -> Locale.setDefault(locale) }
    }

    @Test
    fun formatTime_ukLocale() {
        val formatter = setLocaleAndCreateFormatter(Locale.UK)
        assertThat(formatter.formatTime(time)).isEqualTo("14:06")
    }

    @Test
    fun formatTime_usLocale() {
        val formatter = setLocaleAndCreateFormatter(Locale.US)
        assertThat(formatter.formatTime(time)).isEqualTo("2:06 PM")
    }

    @Test
    fun formatLongDate_ukLocale() {
        val formatter = setLocaleAndCreateFormatter(Locale.UK)
        assertThat(formatter.formatLongDate(time)).isEqualTo("20 October 2022")
    }

    @Test
    fun formatLongDate_usLocale() {
        val formatter = setLocaleAndCreateFormatter(Locale.US)
        assertThat(formatter.formatLongDate(time)).isEqualTo("October 20, 2022")
    }

    @Test
    fun formatShortDate_ukLocale() {
        val formatter = setLocaleAndCreateFormatter(Locale.UK)
        assertThat(formatter.formatShortDate(time)).isEqualTo("20 October")
    }

    @Test
    fun formatShortDate_usLocale() {
        val formatter = setLocaleAndCreateFormatter(Locale.US)
        assertThat(formatter.formatShortDate(time)).isEqualTo("October 20")
    }

    @Test
    fun formatTimeRange_ukLocale() {
        val formatter = setLocaleAndCreateFormatter(Locale.UK)
        val end = time.plus(1, ChronoUnit.HOURS)
        assertThat(formatter.formatTimeRange(time, end)).isEqualTo("14:06 - 15:06")
    }

    @Test
    fun formatTimeRange_usLocale() {
        val formatter = setLocaleAndCreateFormatter(Locale.US)
        val end = time.plus(1, ChronoUnit.HOURS)
        assertThat(formatter.formatTimeRange(time, end)).isEqualTo("2:06 PM - 3:06 PM")
    }

    @Test
    fun formatTimeRangeA11y_ukLocale() {
        val formatter = setLocaleAndCreateFormatter(Locale.UK)
        val end = time.plus(1, ChronoUnit.HOURS)
        assertThat(formatter.formatTimeRangeA11y(time, end)).isEqualTo("from 14:06 to 15:06")
    }

    @Test
    fun formatTimeRangeA11y_usLocale() {
        val formatter = setLocaleAndCreateFormatter(Locale.US)
        val end = time.plus(1, ChronoUnit.HOURS)
        assertThat(formatter.formatTimeRangeA11y(time, end)).isEqualTo("from 2:06 PM to 3:06 PM")
    }

    @Test
    fun formatDateRangeWithYear_ukLocale() {
        val formatter = setLocaleAndCreateFormatter(Locale.UK)
        val end = time.plus(10, ChronoUnit.DAYS)
        assertThat(formatter.formatDateRangeWithYear(time, end)).isEqualTo("20–30 Oct 2022")
    }

    @Test
    fun formatDateRangeWithoutYear_ukLocale() {
        val formatter = setLocaleAndCreateFormatter(Locale.UK)
        val end = time.plus(10, ChronoUnit.DAYS)
        assertThat(formatter.formatDateRangeWithoutYear(time, end)).isEqualTo("20–30 Oct")
    }

    @Test
    fun formatMonthWithYear_ukLocale() {
        val formatter = setLocaleAndCreateFormatter(Locale.UK)
        assertThat(formatter.formatMonthWithYear(time)).isEqualTo("October 2022")
    }

    @Test
    fun formatMonthWithoutYear_ukLocale() {
        val formatter = setLocaleAndCreateFormatter(Locale.UK)
        assertThat(formatter.formatMonthWithoutYear(time)).isEqualTo("October")
    }

    @Test
    fun formatWeekdayDateWithYear_ukLocale() {
        val formatter = setLocaleAndCreateFormatter(Locale.UK)
        assertThat(formatter.formatWeekdayDateWithYear(time)).isEqualTo("Thu, 20 Oct 2022")
    }

    @Test
    fun formatWeekdayDateWithYear_usLocale() {
        val formatter = setLocaleAndCreateFormatter(Locale.US)
        assertThat(formatter.formatWeekdayDateWithYear(time)).isEqualTo("Thu, Oct 20, 2022")
    }

    @Test
    fun formatWeekdayDateWithoutYear_ukLocale() {
        val formatter = setLocaleAndCreateFormatter(Locale.UK)
        assertThat(formatter.formatWeekdayDateWithoutYear(time)).isEqualTo("Thu, 20 Oct")
    }

    @Test
    fun formatWeekdayDateWithoutYear_usLocale() {
        val formatter = setLocaleAndCreateFormatter(Locale.US)
        assertThat(formatter.formatWeekdayDateWithoutYear(time)).isEqualTo("Thu, Oct 20")
    }

    private fun setLocaleAndCreateFormatter(locale: Locale): LocalDateTimeFormatter {
        Locale.setDefault(locale)
        val configuration = Configuration()
        configuration.setLocales(LocaleList(locale))
        return LocalDateTimeFormatter(context.createConfigurationContext(configuration))
    }
}
