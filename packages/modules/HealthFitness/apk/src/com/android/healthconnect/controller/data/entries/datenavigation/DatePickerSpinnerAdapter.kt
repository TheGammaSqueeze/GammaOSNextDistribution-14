package com.android.healthconnect.controller.data.entries.datenavigation

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.entries.datenavigation.DateNavigationPeriod.PERIOD_DAY
import com.android.healthconnect.controller.data.entries.datenavigation.DateNavigationPeriod.PERIOD_MONTH
import com.android.healthconnect.controller.data.entries.datenavigation.DateNavigationPeriod.PERIOD_WEEK
import com.android.healthconnect.controller.utils.LocalDateTimeFormatter
import com.android.healthconnect.controller.utils.SystemTimeSource
import com.android.healthconnect.controller.utils.TimeSource
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/** Adapter for the date picker in [DateNavigationView]. */
class DatePickerSpinnerAdapter(
    context: Context,
    private var displayedStartDate: Instant,
    var period: DateNavigationPeriod,
    private val timeSource: TimeSource = SystemTimeSource
) :
    ArrayAdapter<String>(
        context,
        R.layout.date_navigation_spinner_item,
        listOf(
            context.getString(R.string.date_picker_day),
            context.getString(R.string.date_picker_week),
            context.getString(R.string.date_picker_month))) {
    private val dateFormatter = LocalDateTimeFormatter(context)

    fun setStartTimeAndPeriod(displayedStartTime: Instant, period: DateNavigationPeriod) {
        this.displayedStartDate = displayedStartTime
        this.period = period
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        if (view is TextView) {
            getItem(position)?.let {
                val dateView = formatDateTimeForTimePeriod(displayedStartDate, period)
                view.text = maybeReplaceWithTemporalDeixis(dateView, displayedStartDate, period)
            }
        }
        return view
    }

    /**
     * Formats [startTime] and [period] as follows:
     * * Day: "Sun, Aug 20" or "Mon, Aug 20, 2022"
     * * Week: "Aug 21-27" or "Aug 21-27, 2022"
     * * Month: "August" or "August 2022"
     */
    private fun formatDateTimeForTimePeriod(
        startTime: Instant,
        period: DateNavigationPeriod
    ): String {
        if (areInSameYear(startTime, Instant.ofEpochMilli(timeSource.currentTimeMillis()))) {
            return when (period) {
                PERIOD_DAY -> {
                    dateFormatter.formatWeekdayDateWithoutYear(startTime)
                }
                PERIOD_WEEK -> {
                    dateFormatter.formatDateRangeWithoutYear(
                        startTime, startTime.plus(Period.ofWeeks(1)))
                }
                PERIOD_MONTH -> {
                    dateFormatter.formatMonthWithoutYear(startTime)
                }
            }
        }
        return when (period) {
            PERIOD_DAY -> {
                dateFormatter.formatWeekdayDateWithYear(startTime)
            }
            PERIOD_WEEK -> {
                dateFormatter.formatDateRangeWithYear(startTime, startTime.plus(Period.ofWeeks(1)))
            }
            PERIOD_MONTH -> {
                dateFormatter.formatMonthWithYear(startTime)
            }
        }
    }

    /**
     * Replaces recent dates with:
     * * Day: "Today", "Yesterday"
     * * Week: "This week", "Last week"
     * * Month: "This month", "Last month"
     *
     * <p>No-op for other dates.
     */
    private fun maybeReplaceWithTemporalDeixis(
        dateView: String,
        selectedDate: Instant,
        period: DateNavigationPeriod
    ): String {
        val currentPeriod =
            LocalDate.ofInstant(
                    Instant.ofEpochMilli(timeSource.currentTimeMillis()),
                    timeSource.deviceZoneOffset())
                .atStartOfDay(timeSource.deviceZoneOffset())
                .toInstant()
        val previousPeriod =
            LocalDate.ofInstant(currentPeriod, timeSource.deviceZoneOffset())
                .minus(toPeriod(period))
                .atStartOfDay(timeSource.deviceZoneOffset())
                .toInstant()

        return if (!areInSameYear(selectedDate, currentPeriod)) {
            dateView
        } else if (areInSamePeriod(selectedDate, currentPeriod, period)) {
            temporalDeixisForCurrentPeriod(period)
        } else if (areInSamePeriod(selectedDate, previousPeriod, period)) {
            temporalDeixisForLastPeriod(period)
        } else {
            dateView
        }
    }

    /** Returns "Today", "This week", "This month". */
    private fun temporalDeixisForCurrentPeriod(period: DateNavigationPeriod): String {
        return when (period) {
            PERIOD_DAY -> context.getString(R.string.today_header)
            PERIOD_WEEK -> context.getString(R.string.this_week_header)
            PERIOD_MONTH -> context.getString(R.string.this_month_header)
        }
    }

    /** Returns "Yesterday", "Last week", "Last month". */
    private fun temporalDeixisForLastPeriod(period: DateNavigationPeriod): String {
        return when (period) {
            PERIOD_DAY -> context.getString(R.string.yesterday_header)
            PERIOD_WEEK -> context.getString(R.string.last_week_header)
            PERIOD_MONTH -> context.getString(R.string.last_month_header)
        }
    }

    /** Whether [instant1] and [instant2] are in the same [DateNavigationPeriod]. */
    private fun areInSamePeriod(
        instant1: Instant,
        instant2: Instant,
        period: DateNavigationPeriod
    ): Boolean {
        return when (period) {
            PERIOD_DAY -> areOnSameDay(instant1, instant2)
            PERIOD_WEEK -> areOnSameWeek(instant1, instant2)
            PERIOD_MONTH -> areInSameMonth(instant1, instant2)
        }
    }

    /** Whether [instant1] and [instant2] are in the same calendar day. */
    private fun areOnSameDay(instant1: Instant, instant2: Instant): Boolean {
        val localDate1 = instant1.atZone(timeSource.deviceZoneOffset()).toLocalDate()
        val localDate2 = instant2.atZone(timeSource.deviceZoneOffset()).toLocalDate()
        return localDate1 == localDate2
    }

    /** Whether [instant1] and [instant2] are on the same calendar week. */
    private fun areOnSameWeek(instant1: Instant, instant2: Instant): Boolean {
        val firstDayOfWeekField = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        val localDate1 = instant1.atZone(timeSource.deviceZoneOffset()).toLocalDate()
        val localDate2 = instant2.atZone(timeSource.deviceZoneOffset()).toLocalDate()
        val firstDayOfWeek1 = localDate1.with(TemporalAdjusters.previousOrSame(firstDayOfWeekField))
        val firstDayOfWeek2 = localDate2.with(TemporalAdjusters.previousOrSame(firstDayOfWeekField))
        return firstDayOfWeek1 == firstDayOfWeek2
    }

    /** Whether [instant1] and [instant2] are inn the same calendar month. */
    private fun areInSameMonth(instant1: Instant, instant2: Instant): Boolean {
        val monthOfYear1 = instant1.atZone(timeSource.deviceZoneOffset()).toLocalDate().month
        val monthOfYear2 = instant2.atZone(timeSource.deviceZoneOffset()).toLocalDate().month
        return monthOfYear1 == monthOfYear2
    }

    /** Whether [instant1] and [instant2] are inn the same calendar year. */
    private fun areInSameYear(instant1: Instant, instant2: Instant): Boolean {
        val year1 = instant1.atZone(timeSource.deviceZoneOffset()).toLocalDate().year
        val year2 = instant2.atZone(timeSource.deviceZoneOffset()).toLocalDate().year
        return year1 == year2
    }
}
