/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *   Copyright (C) 1996-2016, International Business Machines
 *   Corporation and others.  All Rights Reserved.
 */


package android.icu.util;

import android.icu.text.DateFormat;
import java.util.Date;
import android.icu.util.ULocale.Category;
import android.icu.text.DateFormatSymbols;

/**
 * <strong>[icu enhancement]</strong> ICU's replacement for {@link java.util.Calendar}.&nbsp;Methods, fields, and other functionality specific to ICU are labeled '<strong>[icu]</strong>'.
 *
 * <p><code>Calendar</code> is an abstract base class for converting between
 * a <code>Date</code> object and a set of integer fields such as
 * <code>YEAR</code>, <code>MONTH</code>, <code>DAY</code>, <code>HOUR</code>,
 * and so on. (A <code>Date</code> object represents a specific instant in
 * time with millisecond precision. See
 * {@link java.util.Date Date}
 * for information about the <code>Date</code> class.)
 *
 * <p>Subclasses of <code>Calendar</code> interpret a <code>Date</code>
 * according to the rules of a specific calendar system.  ICU4J contains
 * several subclasses implementing different international calendar systems.
 *
 * <p>
 * Like other locale-sensitive classes, <code>Calendar</code> provides a
 * class method, <code>getInstance</code>, for getting a generally useful
 * object of this type. <code>Calendar</code>'s <code>getInstance</code> method
 * returns a calendar of a type appropriate to the locale, whose
 * time fields have been initialized with the current date and time:
 * <blockquote>
 * <pre>Calendar rightNow = Calendar.getInstance()</pre>
 * </blockquote>
 *
 * <p>When a <code>ULocale</code> is used by <code>getInstance</code>, its
 * '<code>calendar</code>' tag and value are retrieved if present.  If a recognized
 * value is supplied, a calendar is provided and configured as appropriate.
 * Currently recognized tags are "buddhist", "chinese", "coptic", "ethiopic",
 * "gregorian", "hebrew", "islamic", "islamic-civil", "japanese", and "roc".  For
 * example: <blockquote>
 * <pre>Calendar cal = Calendar.getInstance(new ULocale("en_US@calendar=japanese"));</pre>
 * </blockquote> will return an instance of JapaneseCalendar (using en_US conventions for
 * minimum days in first week, start day of week, et cetera).
 *
 * <p>A <code>Calendar</code> object can produce all the time field values
 * needed to implement the date-time formatting for a particular language and
 * calendar style (for example, Japanese-Gregorian, Japanese-Traditional).
 * <code>Calendar</code> defines the range of values returned by certain fields,
 * as well as their meaning.  For example, the first month of the year has value
 * <code>MONTH</code> == <code>JANUARY</code> for all calendars.  Other values
 * are defined by the concrete subclass, such as <code>ERA</code> and
 * <code>YEAR</code>.  See individual field documentation and subclass
 * documentation for details.
 *
 * <p>When a <code>Calendar</code> is <em>lenient</em>, it accepts a wider range
 * of field values than it produces.  For example, a lenient
 * <code>GregorianCalendar</code> interprets <code>MONTH</code> ==
 * <code>JANUARY</code>, <code>DAY_OF_MONTH</code> == 32 as February 1.  A
 * non-lenient <code>GregorianCalendar</code> throws an exception when given
 * out-of-range field settings.  When calendars recompute field values for
 * return by <code>get()</code>, they normalize them.  For example, a
 * <code>GregorianCalendar</code> always produces <code>DAY_OF_MONTH</code>
 * values between 1 and the length of the month.
 *
 * <p><code>Calendar</code> defines a locale-specific seven day week using two
 * parameters: the first day of the week and the minimal days in first week
 * (from 1 to 7).  These numbers are taken from the locale resource data when a
 * <code>Calendar</code> is constructed.  They may also be specified explicitly
 * through the API.
 *
 * <p>When setting or getting the <code>WEEK_OF_MONTH</code> or
 * <code>WEEK_OF_YEAR</code> fields, <code>Calendar</code> must determine the
 * first week of the month or year as a reference point.  The first week of a
 * month or year is defined as the earliest seven day period beginning on
 * <code>getFirstDayOfWeek()</code> and containing at least
 * <code>getMinimalDaysInFirstWeek()</code> days of that month or year.  Weeks
 * numbered ..., -1, 0 precede the first week; weeks numbered 2, 3,... follow
 * it.  Note that the normalized numbering returned by <code>get()</code> may be
 * different.  For example, a specific <code>Calendar</code> subclass may
 * designate the week before week 1 of a year as week <em>n</em> of the previous
 * year.
 *
 * <p> When computing a <code>Date</code> from time fields, some special
 * circumstances may arise: there may be insufficient information to compute the
 * <code>Date</code> (such as only year and month but no day in the month),
 * there may be inconsistent information (such as "Tuesday, July 15, 1996" --
 * July 15, 1996 is actually a Monday), or the input time might be ambiguous
 * because of time zone transition.
 *
 * <p><strong>Insufficient information.</strong> The calendar will use default
 * information to specify the missing fields. This may vary by calendar; for
 * the Gregorian calendar, the default for a field is the same as that of the
 * start of the epoch: i.e., YEAR = 1970, MONTH = JANUARY, DATE = 1, etc.
 *
 * <p><strong>Inconsistent information.</strong> If fields conflict, the calendar
 * will give preference to fields set more recently. For example, when
 * determining the day, the calendar will look for one of the following
 * combinations of fields.  The most recent combination, as determined by the
 * most recently set single field, will be used.
 *
 * <blockquote>
 * <pre>
 * MONTH + DAY_OF_MONTH
 * MONTH + WEEK_OF_MONTH + DAY_OF_WEEK
 * MONTH + DAY_OF_WEEK_IN_MONTH + DAY_OF_WEEK
 * DAY_OF_YEAR
 * DAY_OF_WEEK + WEEK_OF_YEAR</pre>
 * </blockquote>
 *
 * For the time of day:
 *
 * <blockquote>
 * <pre>
 * HOUR_OF_DAY
 * AM_PM + HOUR</pre>
 * </blockquote>
 *
 * <p><strong>Ambiguous Wall Clock Time.</strong> When time offset from UTC has
 * changed, it produces an ambiguous time slot around the transition. For example,
 * many US locations observe daylight saving time. On the date switching to daylight
 * saving time in US, wall clock time jumps from 12:59 AM (standard) to 2:00 AM
 * (daylight). Therefore, wall clock time from 1:00 AM to 1:59 AM do not exist on
 * the date. When the input wall time fall into this missing time slot, the ICU
 * Calendar resolves the time using the UTC offset before the transition by default.
 * In this example, 1:30 AM is interpreted as 1:30 AM standard time (non-exist),
 * so the final result will be 2:30 AM daylight time.
 *
 * <p>On the date switching back to standard time, wall clock time is moved back one
 * hour at 2:00 AM. So wall clock time from 1:00 AM to 1:59 AM occur twice. In this
 * case, the ICU Calendar resolves the time using the UTC offset after the transition
 * by default. For example, 1:30 AM on the date is resolved as 1:30 AM standard time.
 *
 * <p>Ambiguous wall clock time resolution behaviors can be customized by Calendar APIs
 * {@link #setRepeatedWallTimeOption(int)} and {@link #setSkippedWallTimeOption(int)}.
 * These methods are available in ICU 49 or later versions.
 *
 * <p><strong>Note:</strong> for some non-Gregorian calendars, different
 * fields may be necessary for complete disambiguation. For example, a full
 * specification of the historial Arabic astronomical calendar requires year,
 * month, day-of-month <em>and</em> day-of-week in some cases.
 *
 * <p><strong>Note:</strong> There are certain possible ambiguities in
 * interpretation of certain singular times, which are resolved in the
 * following ways:
 * <ol>
 *     <li> 24:00:00 "belongs" to the following day. That is,
 *          23:59 on Dec 31, 1969 &lt; 24:00 on Jan 1, 1970 &lt; 24:01:00 on Jan 1, 1970
 *
 *     <li> Although historically not precise, midnight also belongs to "am",
 *          and noon belongs to "pm", so on the same day,
 *          12:00 am (midnight) &lt; 12:01 am, and 12:00 pm (noon) &lt; 12:01 pm
 * </ol>
 *
 * <p>The date or time format strings are not part of the definition of a
 * calendar, as those must be modifiable or overridable by the user at
 * runtime. Use {@link android.icu.text.DateFormat DateFormat}
 * to format dates.
 *
 * <p><strong>Field manipulation methods</strong></p>
 *
 * <p><code>Calendar</code> fields can be changed using three methods:
 * <code>set()</code>, <code>add()</code>, and <code>roll()</code>.</p>
 *
 * <p><strong><code>set(f, value)</code></strong> changes field
 * <code>f</code> to <code>value</code>.  In addition, it sets an
 * internal member variable to indicate that field <code>f</code> has
 * been changed. Although field <code>f</code> is changed immediately,
 * the calendar's milliseconds is not recomputed until the next call to
 * <code>get()</code>, <code>getTime()</code>, or
 * <code>getTimeInMillis()</code> is made. Thus, multiple calls to
 * <code>set()</code> do not trigger multiple, unnecessary
 * computations. As a result of changing a field using
 * <code>set()</code>, other fields may also change, depending on the
 * field, the field value, and the calendar system. In addition,
 * <code>get(f)</code> will not necessarily return <code>value</code>
 * after the fields have been recomputed. The specifics are determined by
 * the concrete calendar class.</p>
 *
 * <p><em>Example</em>: Consider a <code>GregorianCalendar</code>
 * originally set to August 31, 1999. Calling <code>set(Calendar.MONTH,
 * Calendar.SEPTEMBER)</code> sets the calendar to September 31,
 * 1999. This is a temporary internal representation that resolves to
 * October 1, 1999 if <code>getTime()</code>is then called. However, a
 * call to <code>set(Calendar.DAY_OF_MONTH, 30)</code> before the call to
 * <code>getTime()</code> sets the calendar to September 30, 1999, since
 * no recomputation occurs after <code>set()</code> itself.</p>
 *
 * <p><strong><code>add(f, delta)</code></strong> adds <code>delta</code>
 * to field <code>f</code>.  This is equivalent to calling <code>set(f,
 * get(f) + delta)</code> with two adjustments:</p>
 *
 * <blockquote>
 *   <p><strong>Add rule 1</strong>. The value of field <code>f</code>
 *   after the call minus the value of field <code>f</code> before the
 *   call is <code>delta</code>, modulo any overflow that has occurred in
 *   field <code>f</code>. Overflow occurs when a field value exceeds its
 *   range and, as a result, the next larger field is incremented or
 *   decremented and the field value is adjusted back into its range.</p>
 *
 *   <p><strong>Add rule 2</strong>. If a smaller field is expected to be
 *   invariant, but &nbsp; it is impossible for it to be equal to its
 *   prior value because of changes in its minimum or maximum after field
 *   <code>f</code> is changed, then its value is adjusted to be as close
 *   as possible to its expected value. A smaller field represents a
 *   smaller unit of time. <code>HOUR</code> is a smaller field than
 *   <code>DAY_OF_MONTH</code>. No adjustment is made to smaller fields
 *   that are not expected to be invariant. The calendar system
 *   determines what fields are expected to be invariant.</p>
 * </blockquote>
 *
 * <p>In addition, unlike <code>set()</code>, <code>add()</code> forces
 * an immediate recomputation of the calendar's milliseconds and all
 * fields.</p>
 *
 * <p><em>Example</em>: Consider a <code>GregorianCalendar</code>
 * originally set to August 31, 1999. Calling <code>add(Calendar.MONTH,
 * 13)</code> sets the calendar to September 30, 2000. <strong>Add rule
 * 1</strong> sets the <code>MONTH</code> field to September, since
 * adding 13 months to August gives September of the next year. Since
 * <code>DAY_OF_MONTH</code> cannot be 31 in September in a
 * <code>GregorianCalendar</code>, <strong>add rule 2</strong> sets the
 * <code>DAY_OF_MONTH</code> to 30, the closest possible value. Although
 * it is a smaller field, <code>DAY_OF_WEEK</code> is not adjusted by
 * rule 2, since it is expected to change when the month changes in a
 * <code>GregorianCalendar</code>.</p>
 *
 * <p><strong><code>roll(f, delta)</code></strong> adds
 * <code>delta</code> to field <code>f</code> without changing larger
 * fields. This is equivalent to calling <code>add(f, delta)</code> with
 * the following adjustment:</p>
 *
 * <blockquote>
 *   <p><strong>Roll rule</strong>. Larger fields are unchanged after the
 *   call. A larger field represents a larger unit of
 *   time. <code>DAY_OF_MONTH</code> is a larger field than
 *   <code>HOUR</code>.</p>
 * </blockquote>
 *
 * <p><em>Example</em>: Consider a <code>GregorianCalendar</code>
 * originally set to August 31, 1999. Calling <code>roll(Calendar.MONTH,
 * 8)</code> sets the calendar to April 30, <strong>1999</strong>.  Add
 * rule 1 sets the <code>MONTH</code> field to April. Using a
 * <code>GregorianCalendar</code>, the <code>DAY_OF_MONTH</code> cannot
 * be 31 in the month April. Add rule 2 sets it to the closest possible
 * value, 30. Finally, the <strong>roll rule</strong> maintains the
 * <code>YEAR</code> field value of 1999.</p>
 *
 * <p><em>Example</em>: Consider a <code>GregorianCalendar</code>
 * originally set to Sunday June 6, 1999. Calling
 * <code>roll(Calendar.WEEK_OF_MONTH, -1)</code> sets the calendar to
 * Tuesday June 1, 1999, whereas calling
 * <code>add(Calendar.WEEK_OF_MONTH, -1)</code> sets the calendar to
 * Sunday May 30, 1999. This is because the roll rule imposes an
 * additional constraint: The <code>MONTH</code> must not change when the
 * <code>WEEK_OF_MONTH</code> is rolled. Taken together with add rule 1,
 * the resultant date must be between Tuesday June 1 and Saturday June
 * 5. According to add rule 2, the <code>DAY_OF_WEEK</code>, an invariant
 * when changing the <code>WEEK_OF_MONTH</code>, is set to Tuesday, the
 * closest possible value to Sunday (where Sunday is the first day of the
 * week).</p>
 *
 * <p><strong>Usage model</strong>. To motivate the behavior of
 * <code>add()</code> and <code>roll()</code>, consider a user interface
 * component with increment and decrement buttons for the month, day, and
 * year, and an underlying <code>GregorianCalendar</code>. If the
 * interface reads January 31, 1999 and the user presses the month
 * increment button, what should it read? If the underlying
 * implementation uses <code>set()</code>, it might read March 3, 1999. A
 * better result would be February 28, 1999. Furthermore, if the user
 * presses the month increment button again, it should read March 31,
 * 1999, not March 28, 1999. By saving the original date and using either
 * <code>add()</code> or <code>roll()</code>, depending on whether larger
 * fields should be affected, the user interface can behave as most users
 * will intuitively expect.</p>
 *
 * <p><b>Note:</b> You should always use {@link #roll roll} and {@link #add add} rather
 * than attempting to perform arithmetic operations directly on the fields
 * of a <tt>Calendar</tt>.  It is quite possible for <tt>Calendar</tt> subclasses
 * to have fields with non-linear behavior, for example missing months
 * or days during non-leap years.  The subclasses' <tt>add</tt> and <tt>roll</tt>
 * methods will take this into account, while simple arithmetic manipulations
 * may give invalid results.
 *
 * <p><big><big><b>Calendar Architecture in ICU4J</b></big></big></p>
 *
 * <p>Recently the implementation of <code>Calendar</code> has changed
 * significantly in order to better support subclassing. The original
 * <code>Calendar</code> class was designed to support subclassing, but
 * it had only one implemented subclass, <code>GregorianCalendar</code>.
 * With the implementation of several new calendar subclasses, including
 * the <code>BuddhistCalendar</code>, <code>ChineseCalendar</code>,
 * <code>HebrewCalendar</code>, <code>IslamicCalendar</code>, and
 * <code>JapaneseCalendar</code>, the subclassing API has been reworked
 * thoroughly. This section details the new subclassing API and other
 * ways in which <code>android.icu.util.Calendar</code> differs from
 * <code>java.util.Calendar</code>.
 * </p>
 *
 * <p><big><b>Changes</b></big></p>
 *
 * <p>Overview of changes between the classic <code>Calendar</code>
 * architecture and the new architecture.
 *
 * <ul>
 *
 *   <li>The <code>fields[]</code> array is <code>private</code> now
 *     instead of <code>protected</code>.  Subclasses must access it
 *     using the methods {@link #internalSet} and
 *     {@link #internalGet}.  <b>Motivation:</b> Subclasses should
 *     not directly access data members.</li>
 *
 *   <li>The <code>time</code> long word is <code>private</code> now
 *     instead of <code>protected</code>.  Subclasses may access it using
 *     the method {@link #internalGetTimeInMillis}, which does not
 *     provoke an update. <b>Motivation:</b> Subclasses should not
 *     directly access data members.</li>
 *
 *   <li>The scope of responsibility of subclasses has been drastically
 *     reduced. As much functionality as possible is implemented in the
 *     <code>Calendar</code> base class. As a result, it is much easier
 *     to subclass <code>Calendar</code>. <b>Motivation:</b> Subclasses
 *     should not have to reimplement common code. Certain behaviors are
 *     common across calendar systems: The definition and behavior of
 *     week-related fields and time fields, the arithmetic
 *     ({@link #add(int,int) add} and {@link #roll(int,int) roll}) behavior of many
 *     fields, and the field validation system.</li>
 *
 *   <li>The subclassing API has been completely redesigned.</li>
 *
 *   <li>The <code>Calendar</code> base class contains some Gregorian
 *     calendar algorithmic support that subclasses can use (specifically
 *     in {@link #handleComputeFields}).  Subclasses can use the
 *     methods <code>getGregorianXxx()</code> to obtain precomputed
 *     values. <b>Motivation:</b> This is required by all
 *     <code>Calendar</code> subclasses in order to implement consistent
 *     time zone behavior, and Gregorian-derived systems can use the
 *     already computed data.</li>
 *
 *   <li>The <code>FIELD_COUNT</code> constant has been removed. Use
 *     {@link #getFieldCount}.  In addition, framework API has been
 *     added to allow subclasses to define additional fields.
 *     <b>Motivation: </b>The number of fields is not constant across
 *     calendar systems.</li>
 *
 *   <li>The range of handled dates has been narrowed from +/-
 *     ~300,000,000 years to +/- ~5,000,000 years. In practical terms
 *     this should not affect clients. However, it does mean that client
 *     code cannot be guaranteed well-behaved results with dates such as
 *     <code>Date(Long.MIN_VALUE)</code> or
 *     <code>Date(Long.MAX_VALUE)</code>. Instead, the
 *     <code>Calendar</code> protected constants should be used.
 *     <b>Motivation:</b> With
 *     the addition of the {@link #JULIAN_DAY} field, Julian day
 *     numbers must be restricted to a 32-bit <code>int</code>.  This
 *     restricts the overall supported range. Furthermore, restricting
 *     the supported range simplifies the computations by removing
 *     special case code that was used to accomodate arithmetic overflow
 *     at millis near <code>Long.MIN_VALUE</code> and
 *     <code>Long.MAX_VALUE</code>.</li>
 *
 *   <li>New fields are implemented: {@link #JULIAN_DAY} defines
 *     single-field specification of the
 *     date. {@link #MILLISECONDS_IN_DAY} defines a single-field
 *     specification of the wall time. {@link #DOW_LOCAL} and
 *     {@link #YEAR_WOY} implement localized day-of-week and
 *     week-of-year behavior.</li>
 *
 *   <li>Subclasses can access protected millisecond constants
 *   defined in <code>Calendar</code>.</li>
 *
 *   <li>New API has been added to support calendar-specific subclasses
 *     of <code>DateFormat</code>.</li>
 *
 *   <li>Several subclasses have been implemented, representing
 *     various international calendar systems.</li>
 *
 * </ul>
 *
 * <p><big><b>Subclass API</b></big></p>
 *
 * <p>The original <code>Calendar</code> API was based on the experience
 * of implementing a only a single subclass,
 * <code>GregorianCalendar</code>. As a result, all of the subclassing
 * kinks had not been worked out. The new subclassing API has been
 * refined based on several implemented subclasses. This includes methods
 * that must be overridden and methods for subclasses to call. Subclasses
 * no longer have direct access to <code>fields</code> and
 * <code>stamp</code>. Instead, they have new API to access
 * these. Subclasses are able to allocate the <code>fields</code> array
 * through a protected framework method; this allows subclasses to
 * specify additional fields. </p>
 *
 * <p>More functionality has been moved into the base class. The base
 * class now contains much of the computational machinery to support the
 * Gregorian calendar. This is based on two things: (1) Many calendars
 * are based on the Gregorian calendar (such as the Buddhist and Japanese
 * imperial calendars). (2) <em>All</em> calendars require basic
 * Gregorian support in order to handle timezone computations. </p>
 *
 * <p>Common computations have been moved into
 * <code>Calendar</code>. Subclasses no longer compute the week related
 * fields and the time related fields. These are commonly handled for all
 * calendars by the base class. </p>
 *
 * <p><b>Subclass computation of time <tt>=&gt;</tt> fields</b>
 *
 * <p>The {@link #ERA}, {@link #YEAR},
 * {@link #EXTENDED_YEAR}, {@link #MONTH},
 * {@link #DAY_OF_MONTH}, and {@link #DAY_OF_YEAR} fields are
 * computed by the subclass, based on the Julian day. All other fields
 * are computed by <code>Calendar</code>.
 *
 * <ul>
 *
 *   <li>Subclasses should implement {@link #handleComputeFields}
 *     to compute the {@link #ERA}, {@link #YEAR},
 *     {@link #EXTENDED_YEAR}, {@link #MONTH},
 *     {@link #DAY_OF_MONTH}, and {@link #DAY_OF_YEAR} fields,
 *     based on the value of the {@link #JULIAN_DAY} field. If there
 *     are calendar-specific fields not defined by <code>Calendar</code>,
 *     they must also be computed. These are the only fields that the
 *     subclass should compute. All other fields are computed by the base
 *     class, so time and week fields behave in a consistent way across
 *     all calendars. The default version of this method in
 *     <code>Calendar</code> implements a proleptic Gregorian
 *     calendar. Within this method, subclasses may call
 *     <code>getGregorianXxx()</code> to obtain the Gregorian calendar
 *     month, day of month, and extended year for the given date.</li>
 *
 * </ul>
 *
 * <p><b>Subclass computation of fields <tt>=&gt;</tt> time</b>
 *
 * <p>The interpretation of most field values is handled entirely by
 * <code>Calendar</code>. <code>Calendar</code> determines which fields
 * are set, which are not, which are set more recently, and so on. In
 * addition, <code>Calendar</code> handles the computation of the time
 * from the time fields and handles the week-related fields. The only
 * thing the subclass must do is determine the extended year, based on
 * the year fields, and then, given an extended year and a month, it must
 * return a Julian day number.
 *
 * <ul>
 *
 *   <li>Subclasses should implement {@link #handleGetExtendedYear}
 *     to return the extended year for this calendar system, based on the
 *     {@link #YEAR}, {@link #EXTENDED_YEAR}, and any fields that
 *     the calendar system uses that are larger than a year, such as
 *     {@link #ERA}.</li>
 *
 *   <li>Subclasses should implement {@link #handleComputeMonthStart}
 *     to return the Julian day number
 *     associated with a month and extended year. This is the Julian day
 *     number of the day before the first day of the month. The month
 *     number is zero-based. This computation should not depend on any
 *     field values.</li>
 *
 * </ul>
 *
 * <p><b>Other methods</b>
 *
 * <ul>
 *
 *   <li>Subclasses should implement {@link #handleGetMonthLength}
 *     to return the number of days in a
 *     given month of a given extended year. The month number, as always,
 *     is zero-based.</li>
 *
 *   <li>Subclasses should implement {@link #handleGetYearLength}
 *     to return the number of days in the given
 *     extended year. This method is used by
 *     <tt>computeWeekFields</tt> to compute the
 *     {@link #WEEK_OF_YEAR} and {@link #YEAR_WOY} fields.</li>
 *
 *   <li>Subclasses should implement {@link #handleGetLimit}
 *     to return the protected values of a field, depending on the value of
 *     <code>limitType</code>. This method only needs to handle the
 *     fields {@link #ERA}, {@link #YEAR}, {@link #MONTH},
 *     {@link #WEEK_OF_YEAR}, {@link #WEEK_OF_MONTH},
 *     {@link #DAY_OF_MONTH}, {@link #DAY_OF_YEAR},
 *     {@link #DAY_OF_WEEK_IN_MONTH}, {@link #YEAR_WOY}, and
 *     {@link #EXTENDED_YEAR}.  Other fields are invariant (with
 *     respect to calendar system) and are handled by the base
 *     class.</li>
 *
 *   <li>Optionally, subclasses may override {@link #validateField}
 *     to check any subclass-specific fields. If the
 *     field's value is out of range, the method should throw an
 *     <code>IllegalArgumentException</code>. The method may call
 *     <code>super.validateField(field)</code> to handle fields in a
 *     generic way, that is, to compare them to the range
 *     <code>getMinimum(field)</code>..<code>getMaximum(field)</code>.</li>
 *
 *   <li>Optionally, subclasses may override
 *     {@link #handleCreateFields} to create an <code>int[]</code>
 *     array large enough to hold the calendar's fields. This is only
 *     necessary if the calendar defines additional fields beyond those
 *     defined by <code>Calendar</code>. The length of the result must be
 *     be between the base and maximum field counts.</li>
 *
 *   <li>Optionally, subclasses may override
 *     {@link #handleGetDateFormat} to create a
 *     <code>DateFormat</code> appropriate to this calendar. This is only
 *     required if a calendar subclass redefines the use of a field (for
 *     example, changes the {@link #ERA} field from a symbolic field
 *     to a numeric one) or defines an additional field.</li>
 *
 *   <li>Optionally, subclasses may override {@link #roll roll} and
 *     {@link #add add} to handle fields that are discontinuous. For
 *     example, in the Hebrew calendar the month &quot;Adar I&quot; only
 *     occurs in leap years; in other years the calendar jumps from
 *     Shevat (month #4) to Adar (month #6). The {@link android.icu.util.HebrewCalendar#add HebrewCalendar.add} and {@link android.icu.util.HebrewCalendar#roll HebrewCalendar.roll} methods take this into
 *     account, so that adding 1 month to Shevat gives the proper result
 *     (Adar) in a non-leap year. The protected utility method {@link
 *     #pinField pinField} is often useful when implementing these two
 *     methods. </li>
 *
 * </ul>
 *
 * <p><big><b>Normalized behavior</b></big>
 *
 * <p>The behavior of certain fields has been made consistent across all
 * calendar systems and implemented in <code>Calendar</code>.
 *
 * <ul>
 *
 *   <li>Time is normalized. Even though some calendar systems transition
 *     between days at sunset or at other times, all ICU4J calendars
 *     transition between days at <em>local zone midnight</em>.  This
 *     allows ICU4J to centralize the time computations in
 *     <code>Calendar</code> and to maintain basic correpsondences
 *     between calendar systems. Affected fields: {@link #AM_PM},
 *     {@link #HOUR}, {@link #HOUR_OF_DAY}, {@link #MINUTE},
 *     {@link #SECOND}, {@link #MILLISECOND},
 *     {@link #ZONE_OFFSET}, and {@link #DST_OFFSET}.</li>
 *
 *   <li>DST behavior is normalized. Daylight savings time behavior is
 *     computed the same for all calendar systems, and depends on the
 *     value of several <code>GregorianCalendar</code> fields: the
 *     {@link #YEAR}, {@link #MONTH}, and
 *     {@link #DAY_OF_MONTH}. As a result, <code>Calendar</code>
 *     always computes these fields, even for non-Gregorian calendar
 *     systems. These fields are available to subclasses.</li>
 *
 *   <li>Weeks are normalized. Although locales define the week
 *     differently, in terms of the day on which it starts, and the
 *     designation of week number one of a month or year, they all use a
 *     common mechanism. Furthermore, the day of the week has a simple
 *     and consistent definition throughout history. For example,
 *     although the Gregorian calendar introduced a discontinuity when
 *     first instituted, the day of week was not disrupted. For this
 *     reason, the fields {@link #DAY_OF_WEEK}, <code>WEEK_OF_YEAR,
 *     WEEK_OF_MONTH</code>, {@link #DAY_OF_WEEK_IN_MONTH},
 *     {@link #DOW_LOCAL}, {@link #YEAR_WOY} are all computed in
 *     a consistent way in the base class, based on the
 *     {@link #EXTENDED_YEAR}, {@link #DAY_OF_YEAR},
 *     {@link #MONTH}, and {@link #DAY_OF_MONTH}, which are
 *     computed by the subclass.</li>
 *
 * </ul>
 *
 * <p><big><b>Supported range</b></big>
 *
 * <p>The allowable range of <code>Calendar</code> has been
 * narrowed. <code>GregorianCalendar</code> used to attempt to support
 * the range of dates with millisecond values from
 * <code>Long.MIN_VALUE</code> to <code>Long.MAX_VALUE</code>. This
 * introduced awkward constructions (hacks) which slowed down
 * performance. It also introduced non-uniform behavior at the
 * boundaries. The new <code>Calendar</code> protocol specifies the
 * maximum range of supportable dates as those having Julian day numbers
 * of <code>-0x7F000000</code> to <code>+0x7F000000</code>. This
 * corresponds to years from ~5,800,000 BCE to ~5,800,000 CE. Programmers
 * should use the protected constants in <code>Calendar</code> to
 * specify an extremely early or extremely late date.</p>
 *
 * <p><big><b>General notes</b></big>
 *
 * <ul>
 *
 *   <li>Calendars implementations are <em>proleptic</em>. For example,
 *     even though the Gregorian calendar was not instituted until the
 *     16th century, the <code>GregorianCalendar</code> class supports
 *     dates before the historical onset of the calendar by extending the
 *     calendar system backward in time. Similarly, the
 *     <code>HebrewCalendar</code> extends backward before the start of
 *     its epoch into zero and negative years. Subclasses do not throw
 *     exceptions because a date precedes the historical start of a
 *     calendar system. Instead, they implement
 *     {@link #handleGetLimit} to return appropriate limits on
 *     {@link #YEAR}, {@link #ERA}, etc. fields. Then, if the
 *     calendar is set to not be lenient, out-of-range field values will
 *     trigger an exception.</li>
 *
 *   <li>Calendar system subclasses compute a <em>extended
 *     year</em>. This differs from the {@link #YEAR} field in that
 *     it ranges over all integer values, including zero and negative
 *     values, and it encapsulates the information of the
 *     {@link #YEAR} field and all larger fields.  Thus, for the
 *     Gregorian calendar, the {@link #EXTENDED_YEAR} is computed as
 *     <code>ERA==AD ? YEAR : 1-YEAR</code>. Another example is the Mayan
 *     long count, which has years (<code>KUN</code>) and nested cycles
 *     of years (<code>KATUN</code> and <code>BAKTUN</code>). The Mayan
 *     {@link #EXTENDED_YEAR} is computed as <code>TUN + 20 * (KATUN
 *     + 20 * BAKTUN)</code>. The <code>Calendar</code> base class uses
 *     the {@link #EXTENDED_YEAR} field to compute the week-related
 *     fields.</li>
 *
 * </ul>
 *
 * @see          java.util.Date
 * @see          android.icu.util.GregorianCalendar
 * @see          android.icu.util.TimeZone
 * @see          android.icu.text.DateFormat
 * @author Mark Davis, Deborah Goldsmith, Chen-Lieh Huang, Alan Liu, Laura Werner
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public abstract class Calendar implements java.io.Serializable, java.lang.Cloneable, java.lang.Comparable<android.icu.util.Calendar> {

/**
 * Constructs a Calendar with the default time zone
 * and the default <code>FORMAT</code> locale.
 * @see     android.icu.util.TimeZone#getDefault
 * @see android.icu.util.ULocale.Category#FORMAT
 */

protected Calendar() { throw new RuntimeException("Stub!"); }

/**
 * Constructs a calendar with the specified time zone and locale.
 * @param zone the time zone to use
 * @param aLocale the locale for the week data
 */

protected Calendar(android.icu.util.TimeZone zone, java.util.Locale aLocale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a calendar with the specified time zone and locale.
 * @param zone the time zone to use
 * @param locale the ulocale for the week data
 */

protected Calendar(android.icu.util.TimeZone zone, android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns a calendar using the default time zone and locale.
 * @return a Calendar.
 */

public static android.icu.util.Calendar getInstance() { throw new RuntimeException("Stub!"); }

/**
 * Returns a calendar using the specified time zone and default locale.
 * @param zone the time zone to use
 * @return a Calendar.
 */

public static android.icu.util.Calendar getInstance(android.icu.util.TimeZone zone) { throw new RuntimeException("Stub!"); }

/**
 * Returns a calendar using the default time zone and specified locale.
 * @param aLocale the locale for the week data
 * @return a Calendar.
 */

public static android.icu.util.Calendar getInstance(java.util.Locale aLocale) { throw new RuntimeException("Stub!"); }

/**
 * Returns a calendar using the default time zone and specified locale.
 * @param locale the ulocale for the week data
 * @return a Calendar.
 */

public static android.icu.util.Calendar getInstance(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns a calendar with the specified time zone and locale.
 * @param zone the time zone to use
 * @param aLocale the locale for the week data
 * @return a Calendar.
 */

public static android.icu.util.Calendar getInstance(android.icu.util.TimeZone zone, java.util.Locale aLocale) { throw new RuntimeException("Stub!"); }

/**
 * Returns a calendar with the specified time zone and locale.
 * @param zone the time zone to use
 * @param locale the ulocale for the week data
 * @return a Calendar.
 */

public static android.icu.util.Calendar getInstance(android.icu.util.TimeZone zone, android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns the list of locales for which Calendars are installed.
 * @return the list of locales for which Calendars are installed.
 */

public static java.util.Locale[] getAvailableLocales() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Given a key and a locale, returns an array of string values in a preferred
 * order that would make a difference. These are all and only those values where
 * the open (creation) of the service with the locale formed from the input locale
 * plus input keyword and that value has different behavior than creation with the
 * input locale alone.
 * @param key           one of the keys supported by this service.  For now, only
 *                      "calendar" is supported.
 * @param locale        the locale
 * @param commonlyUsed  if set to true it will return only commonly used values
 *                      with the given locale in preferred order.  Otherwise,
 *                      it will return all the available values for the locale.
 * @return an array of string values for the given key and the locale.
 */

public static final java.lang.String[] getKeywordValuesForLocale(java.lang.String key, android.icu.util.ULocale locale, boolean commonlyUsed) { throw new RuntimeException("Stub!"); }

/**
 * Returns this Calendar's current time.
 * @return the current time.
 */

public final java.util.Date getTime() { throw new RuntimeException("Stub!"); }

/**
 * Sets this Calendar's current time with the given Date.
 *
 * <p>Note: Calling <code>setTime</code> with
 * <code>Date(Long.MAX_VALUE)</code> or <code>Date(Long.MIN_VALUE)</code>
 * may yield incorrect field values from {@link #get(int)}.
 * @param date the given Date.
 */

public final void setTime(java.util.Date date) { throw new RuntimeException("Stub!"); }

/**
 * Returns this Calendar's current time as a long.
 * @return the current time as UTC milliseconds from the epoch.
 */

public long getTimeInMillis() { throw new RuntimeException("Stub!"); }

/**
 * Sets this Calendar's current time from the given long value.
 * An IllegalIcuArgumentException is thrown when millis is outside the range permitted
 * by a Calendar object when in strict mode.
 * When in lenient mode the out of range values are pinned to their respective min/max.
 * @param millis the new time in UTC milliseconds from the epoch.
 */

public void setTimeInMillis(long millis) { throw new RuntimeException("Stub!"); }

/**
 * Returns the value for a given time field.
 * @param field the given time field.
 * @return the value for the given time field.
 */

public final int get(int field) { throw new RuntimeException("Stub!"); }

/**
 * Returns the value for a given time field.  This is an internal method
 * for subclasses that does <em>not</em> trigger any calculations.
 * @param field the given time field.
 * @return the value for the given time field.
 */

protected final int internalGet(int field) { throw new RuntimeException("Stub!"); }

/**
 * Returns the value for a given time field, or return the given default
 * value if the field is not set.  This is an internal method for
 * subclasses that does <em>not</em> trigger any calculations.
 * @param field the given time field.
 * @param defaultValue value to return if field is not set
 * @return the value for the given time field of defaultValue if the
 * field is unset
 */

protected final int internalGet(int field, int defaultValue) { throw new RuntimeException("Stub!"); }

/**
 * Sets the time field with the given value.
 * @param field the given time field.
 * @param value the value to be set for the given time field.
 */

public final void set(int field, int value) { throw new RuntimeException("Stub!"); }

/**
 * Sets the values for the fields year, month, and date.
 * Previous values of other fields are retained.  If this is not desired,
 * call {@link #clear()} first.
 * @param year the value used to set the YEAR time field.
 * @param month the value used to set the MONTH time field.
 * Month value is 0-based. e.g., 0 for January.
 * @param date the value used to set the DATE time field.
 */

public final void set(int year, int month, int date) { throw new RuntimeException("Stub!"); }

/**
 * Sets the values for the fields year, month, date, hour, and minute.
 * Previous values of other fields are retained.  If this is not desired,
 * call {@link #clear()} first.
 * @param year the value used to set the YEAR time field.
 * @param month the value used to set the MONTH time field.
 * Month value is 0-based. e.g., 0 for January.
 * @param date the value used to set the DATE time field.
 * @param hour the value used to set the HOUR_OF_DAY time field.
 * @param minute the value used to set the MINUTE time field.
 */

public final void set(int year, int month, int date, int hour, int minute) { throw new RuntimeException("Stub!"); }

/**
 * Sets the values for the fields year, month, date, hour, minute, and second.
 * Previous values of other fields are retained.  If this is not desired,
 * call {@link #clear} first.
 * @param year the value used to set the YEAR time field.
 * @param month the value used to set the MONTH time field.
 * Month value is 0-based. e.g., 0 for January.
 * @param date the value used to set the DATE time field.
 * @param hour the value used to set the HOUR_OF_DAY time field.
 * @param minute the value used to set the MINUTE time field.
 * @param second the value used to set the SECOND time field.
 */

public final void set(int year, int month, int date, int hour, int minute, int second) { throw new RuntimeException("Stub!"); }

/**
 * Clears the values of all the time fields.
 */

public final void clear() { throw new RuntimeException("Stub!"); }

/**
 * Clears the value in the given time field.
 * @param field the time field to be cleared.
 */

public final void clear(int field) { throw new RuntimeException("Stub!"); }

/**
 * Determines if the given time field has a value set.
 * @return true if the given time field has a value set; false otherwise.
 */

public final boolean isSet(int field) { throw new RuntimeException("Stub!"); }

/**
 * Fills in any unset fields in the time field list.
 */

protected void complete() { throw new RuntimeException("Stub!"); }

/**
 * Compares this calendar to the specified object.
 * The result is <code>true</code> if and only if the argument is
 * not <code>null</code> and is a <code>Calendar</code> object that
 * represents the same calendar as this object.
 * @param obj the object to compare with.
 * @return <code>true</code> if the objects are the same;
 * <code>false</code> otherwise.
 */

public boolean equals(java.lang.Object obj) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns true if the given Calendar object is equivalent to this
 * one.  An equivalent Calendar will behave exactly as this one
 * does, but it may be set to a different time.  By contrast, for
 * the equals() method to return true, the other Calendar must
 * be set to the same time.
 *
 * @param other the Calendar to be compared with this Calendar
 */

public boolean isEquivalentTo(android.icu.util.Calendar other) { throw new RuntimeException("Stub!"); }

/**
 * Returns a hash code for this calendar.
 * @return a hash code value for this object.
 */

public int hashCode() { throw new RuntimeException("Stub!"); }

/**
 * Compares the time field records.
 * Equivalent to comparing result of conversion to UTC.
 * @param when the Calendar to be compared with this Calendar.
 * @return true if the current time of this Calendar is before
 * the time of Calendar when; false otherwise.
 */

public boolean before(java.lang.Object when) { throw new RuntimeException("Stub!"); }

/**
 * Compares the time field records.
 * Equivalent to comparing result of conversion to UTC.
 * @param when the Calendar to be compared with this Calendar.
 * @return true if the current time of this Calendar is after
 * the time of Calendar when; false otherwise.
 */

public boolean after(java.lang.Object when) { throw new RuntimeException("Stub!"); }

/**
 * Returns the maximum value that this field could have, given the
 * current date.  For example, with the Gregorian date February 3, 1997
 * and the {@link #DAY_OF_MONTH DAY_OF_MONTH} field, the actual maximum
 * is 28; for February 3, 1996 it is 29.
 *
 * <p>The actual maximum computation ignores smaller fields and the
 * current value of like-sized fields.  For example, the actual maximum
 * of the DAY_OF_YEAR or MONTH depends only on the year and supra-year
 * fields.  The actual maximum of the DAY_OF_MONTH depends, in
 * addition, on the MONTH field and any other fields at that
 * granularity (such as IS_LEAP_MONTH).  The
 * DAY_OF_WEEK_IN_MONTH field does not depend on the current
 * DAY_OF_WEEK; it returns the maximum for any day of week in the
 * current month.  Likewise for the WEEK_OF_MONTH and WEEK_OF_YEAR
 * fields.
 *
 * @param field the field whose maximum is desired
 * @return the maximum of the given field for the current date of this calendar
 * @see #getMaximum
 * @see #getLeastMaximum
 */

public int getActualMaximum(int field) { throw new RuntimeException("Stub!"); }

/**
 * Returns the minimum value that this field could have, given the current date.
 * For most fields, this is the same as {@link #getMinimum getMinimum}
 * and {@link #getGreatestMinimum getGreatestMinimum}.  However, some fields,
 * especially those related to week number, are more complicated.
 * <p>
 * For example, assume {@link #getMinimalDaysInFirstWeek getMinimalDaysInFirstWeek}
 * returns 4 and {@link #getFirstDayOfWeek getFirstDayOfWeek} returns SUNDAY.
 * If the first day of the month is Sunday, Monday, Tuesday, or Wednesday
 * there will be four or more days in the first week, so it will be week number 1,
 * and <code>getActualMinimum(WEEK_OF_MONTH)</code> will return 1.  However,
 * if the first of the month is a Thursday, Friday, or Saturday, there are
 * <em>not</em> four days in that week, so it is week number 0, and
 * <code>getActualMinimum(WEEK_OF_MONTH)</code> will return 0.
 * <p>
 * @param field the field whose actual minimum value is desired.
 * @return the minimum of the given field for the current date of this calendar
 *
 * @see #getMinimum
 * @see #getGreatestMinimum
 */

public int getActualMinimum(int field) { throw new RuntimeException("Stub!"); }

/**
 * Prepare this calendar for computing the actual minimum or maximum.
 * This method modifies this calendar's fields; it is called on a
 * temporary calendar.
 *
 * <p>Rationale: The semantics of getActualXxx() is to return the
 * maximum or minimum value that the given field can take, taking into
 * account other relevant fields.  In general these other fields are
 * larger fields.  For example, when computing the actual maximum
 * DAY_OF_MONTH, the current value of DAY_OF_MONTH itself is ignored,
 * as is the value of any field smaller.
 *
 * <p>The time fields all have fixed minima and maxima, so we don't
 * need to worry about them.  This also lets us set the
 * MILLISECONDS_IN_DAY to zero to erase any effects the time fields
 * might have when computing date fields.
 *
 * <p>DAY_OF_WEEK is adjusted specially for the WEEK_OF_MONTH and
 * WEEK_OF_YEAR fields to ensure that they are computed correctly.
 */

protected void prepareGetActual(int field, boolean isMinimum) { throw new RuntimeException("Stub!"); }

/**
 * Rolls (up/down) a single unit of time on the given field.  If the
 * field is rolled past its maximum allowable value, it will "wrap" back
 * to its minimum and continue rolling. For
 * example, to roll the current date up by one day, you can call:
 * <p>
 * <code>roll({@link #DATE}, true)</code>
 * <p>
 * When rolling on the {@link #YEAR} field, it will roll the year
 * value in the range between 1 and the value returned by calling
 * {@link #getMaximum getMaximum}({@link #YEAR}).
 * <p>
 * When rolling on certain fields, the values of other fields may conflict and
 * need to be changed.  For example, when rolling the <code>MONTH</code> field
 * for the Gregorian date 1/31/96 upward, the <code>DAY_OF_MONTH</code> field
 * must be adjusted so that the result is 2/29/96 rather than the invalid
 * 2/31/96.
 * <p>
 * Rolling up always means rolling forward in time (unless
 * the limit of the field is reached, in which case it may pin or wrap), so for the
 * Gregorian calendar, starting with 100 BC and rolling the year up results in 99 BC.
 * When eras have a definite beginning and end (as in the Chinese calendar, or as in
 * most eras in the Japanese calendar) then rolling the year past either limit of the
 * era will cause the year to wrap around. When eras only have a limit at one end,
 * then attempting to roll the year past that limit will result in pinning the year
 * at that limit. Note that for most calendars in which era 0 years move forward in
 * time (such as Buddhist, Hebrew, or Islamic), it is possible for add or roll to
 * result in negative years for era 0 (that is the only way to represent years before
 * the calendar epoch in such calendars).
 * <p>
 * <b>Note:</b> Calling <tt>roll(field, true)</tt> N times is <em>not</em>
 * necessarily equivalent to calling <tt>roll(field, N)</tt>.  For example,
 * imagine that you start with the date Gregorian date January 31, 1995.  If you call
 * <tt>roll(Calendar.MONTH, 2)</tt>, the result will be March 31, 1995.
 * But if you call <tt>roll(Calendar.MONTH, true)</tt>, the result will be
 * February 28, 1995.  Calling it one more time will give March 28, 1995, which
 * is usually not the desired result.
 * <p>
 * <b>Note:</b> You should always use <tt>roll</tt> and <tt>add</tt> rather
 * than attempting to perform arithmetic operations directly on the fields
 * of a <tt>Calendar</tt>.  It is quite possible for <tt>Calendar</tt> subclasses
 * to have fields with non-linear behavior, for example missing months
 * or days during non-leap years.  The subclasses' <tt>add</tt> and <tt>roll</tt>
 * methods will take this into account, while simple arithmetic manipulations
 * may give invalid results.
 * <p>
 * @param field the calendar field to roll.
 *
 * @param up    indicates if the value of the specified time field is to be
 *              rolled up or rolled down. Use <code>true</code> if rolling up,
 *              <code>false</code> otherwise.
 *
 * @exception   java.lang.IllegalArgumentException if the field is invalid or refers
 *              to a field that cannot be handled by this method.
 * @see #roll(int, int)
 * @see #add
 */

public final void roll(int field, boolean up) { throw new RuntimeException("Stub!"); }

/**
 * Rolls (up/down) a specified amount time on the given field.  For
 * example, to roll the current date up by three days, you can call
 * <code>roll(Calendar.DATE, 3)</code>.  If the
 * field is rolled past its maximum allowable value, it will "wrap" back
 * to its minimum and continue rolling.
 * For example, calling <code>roll(Calendar.DATE, 10)</code>
 * on a Gregorian calendar set to 4/25/96 will result in the date 4/5/96.
 * <p>
 * When rolling on certain fields, the values of other fields may conflict and
 * need to be changed.  For example, when rolling the {@link #MONTH MONTH} field
 * for the Gregorian date 1/31/96 by +1, the {@link #DAY_OF_MONTH DAY_OF_MONTH} field
 * must be adjusted so that the result is 2/29/96 rather than the invalid
 * 2/31/96.
 * <p>
 * Rolling by a positive value always means rolling forward in time (unless
 * the limit of the field is reached, in which case it may pin or wrap), so for the
 * Gregorian calendar, starting with 100 BC and rolling the year by + 1 results in 99 BC.
 * When eras have a definite beginning and end (as in the Chinese calendar, or as in
 * most eras in the Japanese calendar) then rolling the year past either limit of the
 * era will cause the year to wrap around. When eras only have a limit at one end,
 * then attempting to roll the year past that limit will result in pinning the year
 * at that limit. Note that for most calendars in which era 0 years move forward in
 * time (such as Buddhist, Hebrew, or Islamic), it is possible for add or roll to
 * result in negative years for era 0 (that is the only way to represent years before
 * the calendar epoch in such calendars).
 * <p>
 * <strong>[icu] Note:</strong> the ICU implementation of this method is able to roll
 * all fields except for {@link #ERA ERA}, {@link #DST_OFFSET DST_OFFSET},
 * and {@link #ZONE_OFFSET ZONE_OFFSET}.  Subclasses may, of course, add support for
 * additional fields in their overrides of <code>roll</code>.
 * <p>
 * <b>Note:</b> You should always use <tt>roll</tt> and <tt>add</tt> rather
 * than attempting to perform arithmetic operations directly on the fields
 * of a <tt>Calendar</tt>.  It is quite possible for <tt>Calendar</tt> subclasses
 * to have fields with non-linear behavior, for example missing months
 * or days during non-leap years.  The subclasses' <tt>add</tt> and <tt>roll</tt>
 * methods will take this into account, while simple arithmetic manipulations
 * may give invalid results.
 * <p>
 * <b>Subclassing:</b><br>
 * This implementation of <code>roll</code> assumes that the behavior of the
 * field is continuous between its minimum and maximum, which are found by
 * calling {@link #getActualMinimum getActualMinimum} and {@link #getActualMaximum getActualMaximum}.
 * For most such fields, simple addition, subtraction, and modulus operations
 * are sufficient to perform the roll.  For week-related fields,
 * the results of {@link #getFirstDayOfWeek getFirstDayOfWeek} and
 * {@link #getMinimalDaysInFirstWeek getMinimalDaysInFirstWeek} are also necessary.
 * Subclasses can override these two methods if their values differ from the defaults.
 * <p>
 * Subclasses that have fields for which the assumption of continuity breaks
 * down must overide <code>roll</code> to handle those fields specially.
 * For example, in the Hebrew calendar the month "Adar I"
 * only occurs in leap years; in other years the calendar jumps from
 * Shevat (month #4) to Adar (month #6).  The
 * {@link android.icu.util.HebrewCalendar#roll HebrewCalendar.roll} method takes this into account,
 * so that rolling the month of Shevat by one gives the proper result (Adar) in a
 * non-leap year.
 * <p>
 * @param field     the calendar field to roll.
 * @param amount    the amount by which the field should be rolled.
 *
 * @exception   java.lang.IllegalArgumentException if the field is invalid or refers
 *              to a field that cannot be handled by this method.
 * @see #roll(int, boolean)
 * @see #add
 */

public void roll(int field, int amount) { throw new RuntimeException("Stub!"); }

/**
 * Add a signed amount to a specified field, using this calendar's rules.
 * For example, to add three days to the current date, you can call
 * <code>add(Calendar.DATE, 3)</code>.
 * <p>
 * When adding to certain fields, the values of other fields may conflict and
 * need to be changed.  For example, when adding one to the {@link #MONTH MONTH} field
 * for the Gregorian date 1/31/96, the {@link #DAY_OF_MONTH DAY_OF_MONTH} field
 * must be adjusted so that the result is 2/29/96 rather than the invalid
 * 2/31/96.
 * <p>
 * Adding a positive value always means moving forward in time, so for the Gregorian
 * calendar, starting with 100 BC and adding +1 to year results in 99 BC (even though
 * this actually reduces the numeric value of the field itself).
 * <p>
 * <strong>[icu] Note:</strong> The ICU implementation of this method is able to add to
 * all fields except for {@link #ERA ERA}, {@link #DST_OFFSET DST_OFFSET},
 * and {@link #ZONE_OFFSET ZONE_OFFSET}.  Subclasses may, of course, add support for
 * additional fields in their overrides of <code>add</code>.
 * <p>
 * <b>Note:</b> You should always use <tt>roll</tt> and <tt>add</tt> rather
 * than attempting to perform arithmetic operations directly on the fields
 * of a <tt>Calendar</tt>.  It is quite possible for <tt>Calendar</tt> subclasses
 * to have fields with non-linear behavior, for example missing months
 * or days during non-leap years.  The subclasses' <tt>add</tt> and <tt>roll</tt>
 * methods will take this into account, while simple arithmetic manipulations
 * may give invalid results.
 * <p>
 * <b>Subclassing:</b><br>
 * This implementation of <code>add</code> assumes that the behavior of the
 * field is continuous between its minimum and maximum, which are found by
 * calling {@link #getActualMinimum getActualMinimum} and
 * {@link #getActualMaximum getActualMaximum}.
 * For such fields, simple arithmetic operations are sufficient to
 * perform the add.
 * <p>
 * Subclasses that have fields for which this assumption of continuity breaks
 * down must overide <code>add</code> to handle those fields specially.
 * For example, in the Hebrew calendar the month "Adar I"
 * only occurs in leap years; in other years the calendar jumps from
 * Shevat (month #4) to Adar (month #6).  The
 * {@link android.icu.util.HebrewCalendar#add HebrewCalendar.add} method takes this into account,
 * so that adding one month
 * to a date in Shevat gives the proper result (Adar) in a non-leap year.
 * <p>
 * @param field     the time field.
 * @param amount    the amount to add to the field.
 *
 * @exception   java.lang.IllegalArgumentException if the field is invalid or refers
 *              to a field that cannot be handled by this method.
 * @see #roll(int, int)
 */

public void add(int field, int amount) { throw new RuntimeException("Stub!"); }

/**
 * Returns the name of this calendar in the language of the given locale.
 */

public java.lang.String getDisplayName(java.util.Locale loc) { throw new RuntimeException("Stub!"); }

/**
 * Returns the name of this calendar in the language of the given locale.
 */

public java.lang.String getDisplayName(android.icu.util.ULocale loc) { throw new RuntimeException("Stub!"); }

/**
 * Compares the times (in millis) represented by two
 * <code>Calendar</code> objects.
 *
 * @param that the <code>Calendar</code> to compare to this.
 * @return <code>0</code> if the time represented by
 * this <code>Calendar</code> is equal to the time represented
 * by that <code>Calendar</code>, a value less than
 * <code>0</code> if the time represented by this is before
 * the time represented by that, and a value greater than
 * <code>0</code> if the time represented by this
 * is after the time represented by that.
 * @throws java.lang.NullPointerException if that
 * <code>Calendar</code> is null.
 * @throws java.lang.IllegalArgumentException if the time of that
 * <code>Calendar</code> can't be obtained because of invalid
 * calendar values.
 */

public int compareTo(android.icu.util.Calendar that) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a <code>DateFormat</code> appropriate to this calendar.
 * Subclasses wishing to specialize this behavior should override
 * {@link #handleGetDateFormat}.
 */

public android.icu.text.DateFormat getDateTimeFormat(int dateStyle, int timeStyle, java.util.Locale loc) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a <code>DateFormat</code> appropriate to this calendar.
 * Subclasses wishing to specialize this behavior should override
 * {@link #handleGetDateFormat}.
 */

public android.icu.text.DateFormat getDateTimeFormat(int dateStyle, int timeStyle, android.icu.util.ULocale loc) { throw new RuntimeException("Stub!"); }

/**
 * Creates a <code>DateFormat</code> appropriate to this calendar.
 * This is a framework method for subclasses to override.  This method
 * is responsible for creating the calendar-specific DateFormat and
 * DateFormatSymbols objects as needed.
 * @param pattern the pattern, specific to the <code>DateFormat</code>
 * subclass
 * @param locale the locale for which the symbols should be drawn
 * @return a <code>DateFormat</code> appropriate to this calendar
 */

protected android.icu.text.DateFormat handleGetDateFormat(java.lang.String pattern, java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Creates a <code>DateFormat</code> appropriate to this calendar.
 * This is a framework method for subclasses to override.  This method
 * is responsible for creating the calendar-specific DateFormat and
 * DateFormatSymbols objects as needed.
 * @param pattern the pattern, specific to the <code>DateFormat</code>
 * subclass
 * @param override The override string.  A numbering system override string can take one of the following forms:
 *     1). If just a numbering system name is specified, it applies to all numeric fields in the date format pattern.
 *     2). To specify an alternate numbering system on a field by field basis, use the field letters from the pattern
 *         followed by an = sign, followed by the numbering system name.  For example, to specify that just the year
 *         be formatted using Hebrew digits, use the override "y=hebr".  Multiple overrides can be specified in a single
 *         string by separating them with a semi-colon. For example, the override string "m=thai;y=deva" would format using
 *         Thai digits for the month and Devanagari digits for the year.
 * @param locale the locale for which the symbols should be drawn
 * @return a <code>DateFormat</code> appropriate to this calendar
 */

protected android.icu.text.DateFormat handleGetDateFormat(java.lang.String pattern, java.lang.String override, java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Creates a <code>DateFormat</code> appropriate to this calendar.
 * This is a framework method for subclasses to override.  This method
 * is responsible for creating the calendar-specific DateFormat and
 * DateFormatSymbols objects as needed.
 * @param pattern the pattern, specific to the <code>DateFormat</code>
 * subclass
 * @param locale the locale for which the symbols should be drawn
 * @return a <code>DateFormat</code> appropriate to this calendar
 */

protected android.icu.text.DateFormat handleGetDateFormat(java.lang.String pattern, android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Adjust the specified field so that it is within
 * the allowable range for the date to which this calendar is set.
 * For example, in a Gregorian calendar pinning the {@link #DAY_OF_MONTH DAY_OF_MONTH}
 * field for a calendar set to April 31 would cause it to be set
 * to April 30.
 * <p>
 * <b>Subclassing:</b>
 * <br>
 * This utility method is intended for use by subclasses that need to implement
 * their own overrides of {@link #roll roll} and {@link #add add}.
 * <p>
 * <b>Note:</b>
 * <code>pinField</code> is implemented in terms of
 * {@link #getActualMinimum getActualMinimum}
 * and {@link #getActualMaximum getActualMaximum}.  If either of those methods uses
 * a slow, iterative algorithm for a particular field, it would be
 * unwise to attempt to call <code>pinField</code> for that field.  If you
 * really do need to do so, you should override this method to do
 * something more efficient for that field.
 * <p>
 * @param field The calendar field whose value should be pinned.
 *
 * @see #getActualMinimum
 * @see #getActualMaximum
 */

protected void pinField(int field) { throw new RuntimeException("Stub!"); }

/**
 * Returns the week number of a day, within a period. This may be the week number in
 * a year or the week number in a month. Usually this will be a value &gt;= 1, but if
 * some initial days of the period are excluded from week 1, because
 * {@link #getMinimalDaysInFirstWeek getMinimalDaysInFirstWeek} is &gt; 1, then
 * the week number will be zero for those
 * initial days. This method requires the day number and day of week for some
 * known date in the period in order to determine the day of week
 * on the desired day.
 * <p>
 * <b>Subclassing:</b>
 * <br>
 * This method is intended for use by subclasses in implementing their
 * {@link #computeTime computeTime} and/or {@link #computeFields computeFields} methods.
 * It is often useful in {@link #getActualMinimum getActualMinimum} and
 * {@link #getActualMaximum getActualMaximum} as well.
 * <p>
 * This variant is handy for computing the week number of some other
 * day of a period (often the first or last day of the period) when its day
 * of the week is not known but the day number and day of week for some other
 * day in the period (e.g. the current date) <em>is</em> known.
 * <p>
 * @param desiredDay    The {@link #DAY_OF_YEAR DAY_OF_YEAR} or
 *              {@link #DAY_OF_MONTH DAY_OF_MONTH} whose week number is desired.
 *              Should be 1 for the first day of the period.
 *
 * @param dayOfPeriod   The {@link #DAY_OF_YEAR DAY_OF_YEAR}
 *              or {@link #DAY_OF_MONTH DAY_OF_MONTH} for a day in the period whose
 *              {@link #DAY_OF_WEEK DAY_OF_WEEK} is specified by the
 *              <code>dayOfWeek</code> parameter.
 *              Should be 1 for first day of period.
 *
 * @param dayOfWeek  The {@link #DAY_OF_WEEK DAY_OF_WEEK} for the day
 *              corresponding to the <code>dayOfPeriod</code> parameter.
 *              1-based with 1=Sunday.
 *
 * @return      The week number (one-based), or zero if the day falls before
 *              the first week because
 *              {@link #getMinimalDaysInFirstWeek getMinimalDaysInFirstWeek}
 *              is more than one.
 */

protected int weekNumber(int desiredDay, int dayOfPeriod, int dayOfWeek) { throw new RuntimeException("Stub!"); }

/**
 * Returns the week number of a day, within a period. This may be the week number in
 * a year, or the week number in a month. Usually this will be a value &gt;= 1, but if
 * some initial days of the period are excluded from week 1, because
 * {@link #getMinimalDaysInFirstWeek getMinimalDaysInFirstWeek} is &gt; 1,
 * then the week number will be zero for those
 * initial days. This method requires the day of week for the given date in order to
 * determine the result.
 * <p>
 * <b>Subclassing:</b>
 * <br>
 * This method is intended for use by subclasses in implementing their
 * {@link #computeTime computeTime} and/or {@link #computeFields computeFields} methods.
 * It is often useful in {@link #getActualMinimum getActualMinimum} and
 * {@link #getActualMaximum getActualMaximum} as well.
 * <p>
 * @param dayOfPeriod   The {@link #DAY_OF_YEAR DAY_OF_YEAR} or
 *                      {@link #DAY_OF_MONTH DAY_OF_MONTH} whose week number is desired.
 *                      Should be 1 for the first day of the period.
 *
 * @param dayOfWeek     The {@link #DAY_OF_WEEK DAY_OF_WEEK} for the day
 *                      corresponding to the <code>dayOfPeriod</code> parameter.
 *                      1-based with 1=Sunday.
 *
 * @return      The week number (one-based), or zero if the day falls before
 *              the first week because
 *              {@link #getMinimalDaysInFirstWeek getMinimalDaysInFirstWeek}
 *              is more than one.
 */

protected final int weekNumber(int dayOfPeriod, int dayOfWeek) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the difference between the given time and the time this
 * calendar object is set to.  If this calendar is set
 * <em>before</em> the given time, the returned value will be
 * positive.  If this calendar is set <em>after</em> the given
 * time, the returned value will be negative.  The
 * <code>field</code> parameter specifies the units of the return
 * value.  For example, if <code>fieldDifference(when,
 * Calendar.MONTH)</code> returns 3, then this calendar is set to
 * 3 months before <code>when</code>, and possibly some additional
 * time less than one month.
 *
 * <p>As a side effect of this call, this calendar is advanced
 * toward <code>when</code> by the given amount.  That is, calling
 * this method has the side effect of calling <code>add(field,
 * n)</code>, where <code>n</code> is the return value.
 *
 * <p>Usage: To use this method, call it first with the largest
 * field of interest, then with progressively smaller fields.  For
 * example:
 *
 * <pre>
 * int y = cal.fieldDifference(when, Calendar.YEAR);
 * int m = cal.fieldDifference(when, Calendar.MONTH);
 * int d = cal.fieldDifference(when, Calendar.DATE);</pre>
 *
 * computes the difference between <code>cal</code> and
 * <code>when</code> in years, months, and days.
 *
 * <p>Note: <code>fieldDifference()</code> is
 * <em>asymmetrical</em>.  That is, in the following code:
 *
 * <pre>
 * cal.setTime(date1);
 * int m1 = cal.fieldDifference(date2, Calendar.MONTH);
 * int d1 = cal.fieldDifference(date2, Calendar.DATE);
 * cal.setTime(date2);
 * int m2 = cal.fieldDifference(date1, Calendar.MONTH);
 * int d2 = cal.fieldDifference(date1, Calendar.DATE);</pre>
 *
 * one might expect that <code>m1 == -m2 &amp;&amp; d1 == -d2</code>.
 * However, this is not generally the case, because of
 * irregularities in the underlying calendar system (e.g., the
 * Gregorian calendar has a varying number of days per month).
 *
 * @param when the date to compare this calendar's time to
 * @param field the field in which to compute the result
 * @return the difference, either positive or negative, between
 * this calendar's time and <code>when</code>, in terms of
 * <code>field</code>.
 */

public int fieldDifference(java.util.Date when, int field) { throw new RuntimeException("Stub!"); }

/**
 * Sets the time zone with the given time zone value.
 * @param value the given time zone.
 */

public void setTimeZone(android.icu.util.TimeZone value) { throw new RuntimeException("Stub!"); }

/**
 * Returns the time zone.
 * @return the time zone object associated with this calendar.
 */

public android.icu.util.TimeZone getTimeZone() { throw new RuntimeException("Stub!"); }

/**
 * Specify whether or not date/time interpretation is to be lenient.  With
 * lenient interpretation, a date such as "February 942, 1996" will be
 * treated as being equivalent to the 941st day after February 1, 1996.
 * With strict interpretation, such dates will cause an exception to be
 * thrown.
 *
 * @see android.icu.text.DateFormat#setLenient
 */

public void setLenient(boolean lenient) { throw new RuntimeException("Stub!"); }

/**
 * Tell whether date/time interpretation is to be lenient.
 */

public boolean isLenient() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong>Sets the behavior for handling wall time repeating multiple times
 * at negative time zone offset transitions. For example, 1:30 AM on
 * November 6, 2011 in US Eastern time (Ameirca/New_York) occurs twice;
 * 1:30 AM EDT, then 1:30 AM EST one hour later. When <code>WALLTIME_FIRST</code>
 * is used, the wall time 1:30AM in this example will be interpreted as 1:30 AM EDT
 * (first occurrence). When <code>WALLTIME_LAST</code> is used, it will be
 * interpreted as 1:30 AM EST (last occurrence). The default value is
 * <code>WALLTIME_LAST</code>.
 *
 * @param option the behavior for handling repeating wall time, either
 * <code>WALLTIME_FIRST</code> or <code>WALLTIME_LAST</code>.
 * @throws java.lang.IllegalArgumentException when <code>option</code> is neither
 * <code>WALLTIME_FIRST</code> nor <code>WALLTIME_LAST</code>.
 *
 * @see #getRepeatedWallTimeOption()
 * @see #WALLTIME_FIRST
 * @see #WALLTIME_LAST
 */

public void setRepeatedWallTimeOption(int option) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong>Gets the behavior for handling wall time repeating multiple times
 * at negative time zone offset transitions.
 *
 * @return the behavior for handling repeating wall time, either
 * <code>WALLTIME_FIRST</code> or <code>WALLTIME_LAST</code>.
 *
 * @see #setRepeatedWallTimeOption(int)
 * @see #WALLTIME_FIRST
 * @see #WALLTIME_LAST
 */

public int getRepeatedWallTimeOption() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong>Sets the behavior for handling skipped wall time at positive time zone offset
 * transitions. For example, 2:30 AM on March 13, 2011 in US Eastern time (America/New_York)
 * does not exist because the wall time jump from 1:59 AM EST to 3:00 AM EDT. When
 * <code>WALLTIME_FIRST</code> is used, 2:30 AM is interpreted as 30 minutes before 3:00 AM
 * EDT, therefore, it will be resolved as 1:30 AM EST. When <code>WALLTIME_LAST</code>
 * is used, 2:30 AM is interpreted as 31 minutes after 1:59 AM EST, therefore, it will be
 * resolved as 3:30 AM EDT. When <code>WALLTIME_NEXT_VALID</code> is used, 2:30 AM will
 * be resolved as next valid wall time, that is 3:00 AM EDT. The default value is
 * <code>WALLTIME_LAST</code>.
 * <p>
 * <b>Note:</b>This option is effective only when this calendar is {@link #isLenient() lenient}.
 * When the calendar is strict, such non-existing wall time will cause an exception.
 *
 * @param option the behavior for handling skipped wall time at positive time zone
 * offset transitions, one of <code>WALLTIME_FIRST</code>, <code>WALLTIME_LAST</code> and
 * <code>WALLTIME_NEXT_VALID</code>.
 * @throws java.lang.IllegalArgumentException when <code>option</code> is not any of
 * <code>WALLTIME_FIRST</code>, <code>WALLTIME_LAST</code> and <code>WALLTIME_NEXT_VALID</code>.
 *
 * @see #getSkippedWallTimeOption()
 * @see #WALLTIME_FIRST
 * @see #WALLTIME_LAST
 * @see #WALLTIME_NEXT_VALID
 */

public void setSkippedWallTimeOption(int option) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong>Gets the behavior for handling skipped wall time at positive time zone offset
 * transitions.
 *
 * @return the behavior for handling skipped wall time, one of
 * <code>WALLTIME_FIRST</code>, <code>WALLTIME_LAST</code> and <code>WALLTIME_NEXT_VALID</code>.
 *
 * @see #setSkippedWallTimeOption(int)
 * @see #WALLTIME_FIRST
 * @see #WALLTIME_LAST
 * @see #WALLTIME_NEXT_VALID
 */

public int getSkippedWallTimeOption() { throw new RuntimeException("Stub!"); }

/**
 * Sets what the first day of the week is,
 * where 1 = {@link #SUNDAY} and 7 = {@link #SATURDAY}.
 * @param value the given first day of the week, where 1 = {@link #SUNDAY} and 7 = {@link #SATURDAY}.
 */

public void setFirstDayOfWeek(int value) { throw new RuntimeException("Stub!"); }

/**
 * Returns what the first day of the week is,
 * where 1 = {@link #SUNDAY} and 7 = {@link #SATURDAY}.
 * e.g., Sunday in US, Monday in France
 * @return the first day of the week, where 1 = {@link #SUNDAY} and 7 = {@link #SATURDAY}.
 */

public int getFirstDayOfWeek() { throw new RuntimeException("Stub!"); }

/**
 * Sets what the minimal days required in the first week of the year are.
 * For example, if the first week is defined as one that contains the first
 * day of the first month of a year, call the method with value 1. If it
 * must be a full week, use value 7.
 * @param value the given minimal days required in the first week
 * of the year.
 */

public void setMinimalDaysInFirstWeek(int value) { throw new RuntimeException("Stub!"); }

/**
 * Returns what the minimal days required in the first week of the year are.
 * That is, if the first week is defined as one that contains the first day
 * of the first month of a year, getMinimalDaysInFirstWeek returns 1. If
 * the minimal days required must be a full week, getMinimalDaysInFirstWeek
 * returns 7.
 * @return the minimal days required in the first week of the year.
 */

public int getMinimalDaysInFirstWeek() { throw new RuntimeException("Stub!"); }

/**
 * Subclass API for defining limits of different types.
 * Subclasses must implement this method to return limits for the
 * following fields:
 *
 * <pre>ERA
 * YEAR
 * MONTH
 * WEEK_OF_YEAR
 * WEEK_OF_MONTH
 * DAY_OF_MONTH
 * DAY_OF_YEAR
 * DAY_OF_WEEK_IN_MONTH
 * YEAR_WOY
 * EXTENDED_YEAR</pre>
 *
 * @param field one of the above field numbers
 * @param limitType one of <code>MINIMUM</code>, <code>GREATEST_MINIMUM</code>,
 * <code>LEAST_MAXIMUM</code>, or <code>MAXIMUM</code>
 */

protected abstract int handleGetLimit(int field, int limitType);

/**
 * Returns a limit for a field.
 * @param field the field, from 0..<code>getFieldCount()-1</code>
 * @param limitType the type specifier for the limit
 * @see #MINIMUM
 * @see #GREATEST_MINIMUM
 * @see #LEAST_MAXIMUM
 * @see #MAXIMUM
 */

protected int getLimit(int field, int limitType) { throw new RuntimeException("Stub!"); }

/**
 * Returns the minimum value for the given time field.
 * e.g., for Gregorian DAY_OF_MONTH, 1.
 * @param field the given time field.
 * @return the minimum value for the given time field.
 */

public final int getMinimum(int field) { throw new RuntimeException("Stub!"); }

/**
 * Returns the maximum value for the given time field.
 * e.g. for Gregorian DAY_OF_MONTH, 31.
 * @param field the given time field.
 * @return the maximum value for the given time field.
 */

public final int getMaximum(int field) { throw new RuntimeException("Stub!"); }

/**
 * Returns the highest minimum value for the given field if varies.
 * Otherwise same as getMinimum(). For Gregorian, no difference.
 * @param field the given time field.
 * @return the highest minimum value for the given time field.
 */

public final int getGreatestMinimum(int field) { throw new RuntimeException("Stub!"); }

/**
 * Returns the lowest maximum value for the given field if varies.
 * Otherwise same as getMaximum(). e.g., for Gregorian DAY_OF_MONTH, 28.
 * @param field the given time field.
 * @return the lowest maximum value for the given time field.
 */

public final int getLeastMaximum(int field) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns true if the given date and time is in the weekend in this calendar
 * system.  Equivalent to calling setTime() followed by isWeekend().  Note: This
 * method changes the time this calendar is set to.
 * @param date the date and time
 * @return true if the given date and time is part of the
 * weekend
 * @see #isWeekend()
 */

public boolean isWeekend(java.util.Date date) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns true if this Calendar's current date and time is in the weekend in
 * this calendar system.
 * @return true if the given date and time is part of the
 * weekend
 * @see #isWeekend(Date)
 */

public boolean isWeekend() { throw new RuntimeException("Stub!"); }

/**
 * Overrides Cloneable
 */

public java.lang.Object clone() { throw new RuntimeException("Stub!"); }

/**
 * Returns a string representation of this calendar. This method
 * is intended to be used only for debugging purposes, and the
 * format of the returned string may vary between implementations.
 * The returned string may be empty but may not be <code>null</code>.
 *
 * @return  a string representation of this calendar.
 */

public java.lang.String toString() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Return simple, immutable struct-like class for access to the CLDR week data.
 * @param region The input region. The results are undefined if the region code is not valid.
 * @return the WeekData for the input region. It is never null.
 */

public static android.icu.util.Calendar.WeekData getWeekDataForRegion(java.lang.String region) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Return simple, immutable struct-like class for access to the week data in this calendar.
 * @return the WeekData for this calendar.
 */

public android.icu.util.Calendar.WeekData getWeekData() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Set data in this calendar based on the WeekData input.
 * @param wdata The week data to use
 * @return this, for chaining
 */

public android.icu.util.Calendar setWeekData(android.icu.util.Calendar.WeekData wdata) { throw new RuntimeException("Stub!"); }

/**
 * Converts the current millisecond time value <code>time</code> to
 * field values in <code>fields[]</code>.  This synchronizes the time
 * field values with a new time that is set for the calendar.  The time
 * is <em>not</em> recomputed first; to recompute the time, then the
 * fields, call the <code>complete</code> method.
 * @see #complete
 */

protected void computeFields() { throw new RuntimeException("Stub!"); }

/**
 * Compute the Gregorian calendar year, month, and day of month from the
 * Julian day.  These values are not stored in fields, but in member
 * variables gregorianXxx.  They are used for time zone computations and by
 * subclasses that are Gregorian derivatives.  Subclasses may call this
 * method to perform a Gregorian calendar millis-&gt;fields computation.
 * To perform a Gregorian calendar fields-&gt;millis computation, call
 * computeGregorianMonthStart().
 * @see #computeGregorianMonthStart
 */

protected final void computeGregorianFields(int julianDay) { throw new RuntimeException("Stub!"); }

/**
 * Given a precedence table, return the newest field combination in
 * the table, or -1 if none is found.
 *
 * <p>The precedence table is a 3-dimensional array of integers.  It
 * may be thought of as an array of groups.  Each group is an array of
 * lines.  Each line is an array of field numbers.  Within a line, if
 * all fields are set, then the time stamp of the line is taken to be
 * the stamp of the most recently set field.  If any field of a line is
 * unset, then the line fails to match.  Within a group, the line with
 * the newest time stamp is selected.  The first field of the line is
 * returned to indicate which line matched.
 *
 * <p>In some cases, it may be desirable to map a line to field that
 * whose stamp is NOT examined.  For example, if the best field is
 * DAY_OF_WEEK then the DAY_OF_WEEK_IN_MONTH algorithm may be used.  In
 * order to do this, insert the value <code>REMAP_RESOLVE | F</code> at
 * the start of the line, where <code>F</code> is the desired return
 * field value.  This field will NOT be examined; it only determines
 * the return value if the other fields in the line are the newest.
 *
 * <p>If all lines of a group contain at least one unset field, then no
 * line will match, and the group as a whole will fail to match.  In
 * that case, the next group will be processed.  If all groups fail to
 * match, then -1 is returned.
 */

protected int resolveFields(int[][][] precedenceTable) { throw new RuntimeException("Stub!"); }

/**
 * Returns the newest stamp of a given range of fields.
 */

protected int newestStamp(int first, int last, int bestStampSoFar) { throw new RuntimeException("Stub!"); }

/**
 * Returns the timestamp of a field.
 */

protected final int getStamp(int field) { throw new RuntimeException("Stub!"); }

/**
 * Returns the field that is newer, either defaultField, or
 * alternateField.  If neither is newer or neither is set, return defaultField.
 */

protected int newerField(int defaultField, int alternateField) { throw new RuntimeException("Stub!"); }

/**
 * Ensure that each field is within its valid range by calling {@link
 * #validateField(int)} on each field that has been set.  This method
 * should only be called if this calendar is not lenient.
 * @see #isLenient
 * @see #validateField(int)
 */

protected void validateFields() { throw new RuntimeException("Stub!"); }

/**
 * Validate a single field of this calendar.  Subclasses should
 * override this method to validate any calendar-specific fields.
 * Generic fields can be handled by
 * <code>Calendar.validateField()</code>.
 * @see #validateField(int, int, int)
 */

protected void validateField(int field) { throw new RuntimeException("Stub!"); }

/**
 * Validate a single field of this calendar given its minimum and
 * maximum allowed value.  If the field is out of range, throw a
 * descriptive <code>IllegalArgumentException</code>.  Subclasses may
 * use this method in their implementation of {@link
 * #validateField(int)}.
 */

protected final void validateField(int field, int min, int max) { throw new RuntimeException("Stub!"); }

/**
 * Converts the current field values in <code>fields[]</code> to the
 * millisecond time value <code>time</code>.
 */

protected void computeTime() { throw new RuntimeException("Stub!"); }

/**
 * Compute the milliseconds in the day from the fields.  This is a
 * value from 0 to 23:59:59.999 inclusive, unless fields are out of
 * range, in which case it can be an arbitrary value.  This value
 * reflects local zone wall time.
 * @deprecated ICU 60
 */

@Deprecated
protected int computeMillisInDay() { throw new RuntimeException("Stub!"); }

/**
 * This method can assume EXTENDED_YEAR has been set.
 * @param millis milliseconds of the date fields (local midnight millis)
 * @param millisInDay milliseconds of the time fields; may be out
 * or range.
 * @return total zone offset (raw + DST) for the given moment
 * @deprecated ICU 60
 */

@Deprecated
protected int computeZoneOffset(long millis, int millisInDay) { throw new RuntimeException("Stub!"); }

/**
 * Compute the Julian day number as specified by this calendar's fields.
 */

protected int computeJulianDay() { throw new RuntimeException("Stub!"); }

/**
 * Returns the field resolution array for this calendar.  Calendars that
 * define additional fields or change the semantics of existing fields
 * should override this method to adjust the field resolution semantics
 * accordingly.  Other subclasses should not override this method.
 * @see #resolveFields
 */

protected int[][][] getFieldResolutionTable() { throw new RuntimeException("Stub!"); }

/**
 * Returns the Julian day number of day before the first day of the
 * given month in the given extended year.  Subclasses should override
 * this method to implement their calendar system.
 * @param eyear the extended year
 * @param month the zero-based month, or 0 if useMonth is false
 * @param useMonth if false, compute the day before the first day of
 * the given year, otherwise, compute the day before the first day of
 * the given month
 * @return the Julian day number of the day before the first
 * day of the given month and year
 */

protected abstract int handleComputeMonthStart(int eyear, int month, boolean useMonth);

/**
 * Returns the extended year defined by the current fields.  This will
 * use the EXTENDED_YEAR field or the YEAR and supra-year fields (such
 * as ERA) specific to the calendar system, depending on which set of
 * fields is newer.
 * @return the extended year
 */

protected abstract int handleGetExtendedYear();

/**
 * Returns the number of days in the given month of the given extended
 * year of this calendar system.  Subclasses should override this
 * method if they can provide a more correct or more efficient
 * implementation than the default implementation in Calendar.
 */

protected int handleGetMonthLength(int extendedYear, int month) { throw new RuntimeException("Stub!"); }

/**
 * Returns the number of days in the given extended year of this
 * calendar system.  Subclasses should override this method if they can
 * provide a more correct or more efficient implementation than the
 * default implementation in Calendar.
 */

protected int handleGetYearLength(int eyear) { throw new RuntimeException("Stub!"); }

/**
 * Subclasses that use additional fields beyond those defined in
 * <code>Calendar</code> should override this method to return an
 * <code>int[]</code> array of the appropriate length.  The length
 * must be at least <code>BASE_FIELD_COUNT</code> and no more than
 * <code>MAX_FIELD_COUNT</code>.
 */

protected int[] handleCreateFields() { throw new RuntimeException("Stub!"); }

/**
 * Subclasses may override this.  This method calls
 * handleGetMonthLength() to obtain the calendar-specific month
 * length.
 */

protected int handleComputeJulianDay(int bestField) { throw new RuntimeException("Stub!"); }

/**
 * Compute the Julian day of a month of the Gregorian calendar.
 * Subclasses may call this method to perform a Gregorian calendar
 * fields-&gt;millis computation.  To perform a Gregorian calendar
 * millis-&gt;fields computation, call computeGregorianFields().
 * @param year extended Gregorian year
 * @param month zero-based Gregorian month
 * @return the Julian day number of the day before the first
 * day of the given month in the given extended year
 * @see #computeGregorianFields
 */

protected int computeGregorianMonthStart(int year, int month) { throw new RuntimeException("Stub!"); }

/**
 * Subclasses may override this method to compute several fields
 * specific to each calendar system.  These are:
 *
 * <ul><li>ERA
 * <li>YEAR
 * <li>MONTH
 * <li>DAY_OF_MONTH
 * <li>DAY_OF_YEAR
 * <li>EXTENDED_YEAR</ul>
 *
 * Subclasses can refer to the DAY_OF_WEEK and DOW_LOCAL fields, which
 * will be set when this method is called.  Subclasses can also call
 * the getGregorianXxx() methods to obtain Gregorian calendar
 * equivalents for the given Julian day.
 *
 * <p>In addition, subclasses should compute any subclass-specific
 * fields, that is, fields from BASE_FIELD_COUNT to
 * getFieldCount() - 1.
 *
 * <p>The default implementation in <code>Calendar</code> implements
 * a pure proleptic Gregorian calendar.
 */

protected void handleComputeFields(int julianDay) { throw new RuntimeException("Stub!"); }

/**
 * Returns the extended year on the Gregorian calendar as computed by
 * <code>computeGregorianFields()</code>.
 * @see #computeGregorianFields
 */

protected final int getGregorianYear() { throw new RuntimeException("Stub!"); }

/**
 * Returns the month (0-based) on the Gregorian calendar as computed by
 * <code>computeGregorianFields()</code>.
 * @see #computeGregorianFields
 */

protected final int getGregorianMonth() { throw new RuntimeException("Stub!"); }

/**
 * Returns the day of year (1-based) on the Gregorian calendar as
 * computed by <code>computeGregorianFields()</code>.
 * @see #computeGregorianFields
 */

protected final int getGregorianDayOfYear() { throw new RuntimeException("Stub!"); }

/**
 * Returns the day of month (1-based) on the Gregorian calendar as
 * computed by <code>computeGregorianFields()</code>.
 * @see #computeGregorianFields
 */

protected final int getGregorianDayOfMonth() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the number of fields defined by this calendar.  Valid field
 * arguments to <code>set()</code> and <code>get()</code> are
 * <code>0..getFieldCount()-1</code>.
 */

public final int getFieldCount() { throw new RuntimeException("Stub!"); }

/**
 * Set a field to a value.  Subclasses should use this method when
 * computing fields.  It sets the time stamp in the
 * <code>stamp[]</code> array to <code>INTERNALLY_SET</code>.  If a
 * field that may not be set by subclasses is passed in, an
 * <code>IllegalArgumentException</code> is thrown.  This prevents
 * subclasses from modifying fields that are intended to be
 * calendar-system invariant.
 */

protected final void internalSet(int field, int value) { throw new RuntimeException("Stub!"); }

/**
 * Determines if the given year is a leap year. Returns true if the
 * given year is a leap year.
 * @param year the given year.
 * @return true if the given year is a leap year; false otherwise.
 */

protected static final boolean isGregorianLeapYear(int year) { throw new RuntimeException("Stub!"); }

/**
 * Returns the length of a month of the Gregorian calendar.
 * @param y the extended year
 * @param m the 0-based month number
 * @return the number of days in the given month
 */

protected static final int gregorianMonthLength(int y, int m) { throw new RuntimeException("Stub!"); }

/**
 * Returns the length of a previous month of the Gregorian calendar.
 * @param y the extended year
 * @param m the 0-based month number
 * @return the number of days in the month previous to the given month
 */

protected static final int gregorianPreviousMonthLength(int y, int m) { throw new RuntimeException("Stub!"); }

/**
 * Divide two long integers, returning the floor of the quotient.
 * <p>
 * Unlike the built-in division, this is mathematically well-behaved.
 * E.g., <code>-1/4</code> =&gt; 0
 * but <code>floorDivide(-1,4)</code> =&gt; -1.
 * @param numerator the numerator
 * @param denominator a divisor which must be &gt; 0
 * @return the floor of the quotient.
 */

protected static final long floorDivide(long numerator, long denominator) { throw new RuntimeException("Stub!"); }

/**
 * Divide two integers, returning the floor of the quotient.
 * <p>
 * Unlike the built-in division, this is mathematically well-behaved.
 * E.g., <code>-1/4</code> =&gt; 0
 * but <code>floorDivide(-1,4)</code> =&gt; -1.
 * @param numerator the numerator
 * @param denominator a divisor which must be &gt; 0
 * @return the floor of the quotient.
 */

protected static final int floorDivide(int numerator, int denominator) { throw new RuntimeException("Stub!"); }

/**
 * Divide two integers, returning the floor of the quotient, and
 * the modulus remainder.
 * <p>
 * Unlike the built-in division, this is mathematically well-behaved.
 * E.g., <code>-1/4</code> =&gt; 0 and <code>-1%4</code> =&gt; -1,
 * but <code>floorDivide(-1,4)</code> =&gt; -1 with <code>remainder[0]</code> =&gt; 3.
 * @param numerator the numerator
 * @param denominator a divisor which must be &gt; 0
 * @param remainder an array of at least one element in which the value
 * <code>numerator mod denominator</code> is returned. Unlike <code>numerator
 * % denominator</code>, this will always be non-negative.
 * @return the floor of the quotient.
 */

protected static final int floorDivide(int numerator, int denominator, int[] remainder) { throw new RuntimeException("Stub!"); }

/**
 * Divide two integers, returning the floor of the quotient, and
 * the modulus remainder.
 * <p>
 * Unlike the built-in division, this is mathematically well-behaved.
 * E.g., <code>-1/4</code> =&gt; 0 and <code>-1%4</code> =&gt; -1,
 * but <code>floorDivide(-1,4)</code> =&gt; -1 with <code>remainder[0]</code> =&gt; 3.
 * @param numerator the numerator
 * @param denominator a divisor which must be &gt; 0
 * @param remainder an array of at least one element in which the value
 * <code>numerator mod denominator</code> is returned. Unlike <code>numerator
 * % denominator</code>, this will always be non-negative.
 * @return the floor of the quotient.
 */

protected static final int floorDivide(long numerator, int denominator, int[] remainder) { throw new RuntimeException("Stub!"); }

/**
 * Returns a string name for a field, for debugging and exceptions.
 */

protected java.lang.String fieldName(int field) { throw new RuntimeException("Stub!"); }

/**
 * Converts time as milliseconds to Julian day.
 * @param millis the given milliseconds.
 * @return the Julian day number.
 */

protected static final int millisToJulianDay(long millis) { throw new RuntimeException("Stub!"); }

/**
 * Converts Julian day to time as milliseconds.
 * @param julian the given Julian day number.
 * @return time as milliseconds.
 */

protected static final long julianDayToMillis(int julian) { throw new RuntimeException("Stub!"); }

/**
 * Returns the day of week, from SUNDAY to SATURDAY, given a Julian day.
 */

protected static final int julianDayToDayOfWeek(int julian) { throw new RuntimeException("Stub!"); }

/**
 * Returns the current milliseconds without recomputing.
 */

protected final long internalGetTimeInMillis() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the calendar type name string for this Calendar object.
 * The returned string is the legacy ICU calendar attribute value,
 * for example, "gregorian" or "japanese".
 *
 * <p>See type="old type name" for the calendar attribute of locale IDs
 * at http://www.unicode.org/reports/tr35/#Key_Type_Definitions
 *
 * @return legacy calendar type name string
 */

public java.lang.String getType() { throw new RuntimeException("Stub!"); }

/**
 * Value of the <code>AM_PM</code> field indicating the
 * period of the day from midnight to just before noon.
 */

public static final int AM = 0; // 0x0

/**
 * Field number for <code>get</code> and <code>set</code> indicating
 * whether the <code>HOUR</code> is before or after noon.
 * E.g., at 10:04:15.250 PM the <code>AM_PM</code> is <code>PM</code>.
 * @see #AM
 * @see #PM
 * @see #HOUR
 */

public static final int AM_PM = 9; // 0x9

/**
 * Value of the <code>MONTH</code> field indicating the
 * fourth month of the year.
 */

public static final int APRIL = 3; // 0x3

/**
 * Value of the <code>MONTH</code> field indicating the
 * eighth month of the year.
 */

public static final int AUGUST = 7; // 0x7

/**
 * The number of fields defined by this class.  Subclasses may define
 * addition fields starting with this number.
 * @deprecated ICU 58 The numeric value may change over time, see ICU ticket #12420.
 */

@Deprecated protected static final int BASE_FIELD_COUNT = 23; // 0x17

/**
 * Field number for <code>get</code> and <code>set</code> indicating the
 * day of the month. This is a synonym for {@link #DAY_OF_MONTH}.
 * The first day of the month has value 1.
 * @see #DAY_OF_MONTH
 */

public static final int DATE = 5; // 0x5

/**
 * Field number for <code>get</code> and <code>set</code> indicating the
 * day of the month. This is a synonym for {@link #DATE}.
 * The first day of the month has value 1.
 * @see #DATE
 */

public static final int DAY_OF_MONTH = 5; // 0x5

/**
 * Field number for <code>get</code> and <code>set</code> indicating the day
 * of the week.  This field takes values {@link #SUNDAY},
 * {@link #MONDAY}, {@link #TUESDAY}, {@link #WEDNESDAY},
 * {@link #THURSDAY}, {@link #FRIDAY}, and {@link #SATURDAY}.
 * @see #SUNDAY
 * @see #MONDAY
 * @see #TUESDAY
 * @see #WEDNESDAY
 * @see #THURSDAY
 * @see #FRIDAY
 * @see #SATURDAY
 */

public static final int DAY_OF_WEEK = 7; // 0x7

/**
 * Field number for <code>get</code> and <code>set</code> indicating the
 * ordinal number of the day of the week within the current month. Together
 * with the {@link #DAY_OF_WEEK} field, this uniquely specifies a day
 * within a month.  Unlike {@link #WEEK_OF_MONTH} and
 * {@link #WEEK_OF_YEAR}, this field's value does <em>not</em> depend on
 * {@link #getFirstDayOfWeek()} or
 * {@link #getMinimalDaysInFirstWeek()}.  <code>DAY_OF_MONTH 1</code>
 * through <code>7</code> always correspond to <code>DAY_OF_WEEK_IN_MONTH
 * 1</code>; <code>8</code> through <code>15</code> correspond to
 * <code>DAY_OF_WEEK_IN_MONTH 2</code>, and so on.
 * <code>DAY_OF_WEEK_IN_MONTH 0</code> indicates the week before
 * <code>DAY_OF_WEEK_IN_MONTH 1</code>.  Negative values count back from the
 * end of the month, so the last Sunday of a month is specified as
 * <code>DAY_OF_WEEK = SUNDAY, DAY_OF_WEEK_IN_MONTH = -1</code>.  Because
 * negative values count backward they will usually be aligned differently
 * within the month than positive values.  For example, if a month has 31
 * days, <code>DAY_OF_WEEK_IN_MONTH -1</code> will overlap
 * <code>DAY_OF_WEEK_IN_MONTH 5</code> and the end of <code>4</code>.
 * @see #DAY_OF_WEEK
 * @see #WEEK_OF_MONTH
 */

public static final int DAY_OF_WEEK_IN_MONTH = 8; // 0x8

/**
 * Field number for <code>get</code> and <code>set</code> indicating the day
 * number within the current year.  The first day of the year has value 1.
 */

public static final int DAY_OF_YEAR = 6; // 0x6

/**
 * Value of the <code>MONTH</code> field indicating the
 * twelfth month of the year.
 */

public static final int DECEMBER = 11; // 0xb

/**
 * <strong>[icu]</strong> Field number for <code>get()</code> and <code>set()</code>
 * indicating the localized day of week.  This will be a value from 1
 * to 7 inclusive, with 1 being the localized first day of the week.
 */

public static final int DOW_LOCAL = 18; // 0x12

/**
 * Field number for <code>get</code> and <code>set</code> indicating the
 * daylight savings offset in milliseconds.
 */

public static final int DST_OFFSET = 16; // 0x10

/**
 * The Julian day of the epoch, that is, January 1, 1970 on the
 * Gregorian calendar.
 */

protected static final int EPOCH_JULIAN_DAY = 2440588; // 0x253d8c

/**
 * Field number for <code>get</code> and <code>set</code> indicating the
 * era, e.g., AD or BC in the Julian calendar. This is a calendar-specific
 * value; see subclass documentation.
 * @see android.icu.util.GregorianCalendar#AD
 * @see android.icu.util.GregorianCalendar#BC
 */

public static final int ERA = 0; // 0x0

/**
 * <strong>[icu]</strong> Field number for <code>get()</code> and <code>set()</code>
 * indicating the extended year.  This is a single number designating
 * the year of this calendar system, encompassing all supra-year
 * fields.  For example, for the Julian calendar system, year numbers
 * are positive, with an era of BCE or CE.  An extended year value for
 * the Julian calendar system assigns positive values to CE years and
 * negative values to BCE years, with 1 BCE being year 0.
 */

public static final int EXTENDED_YEAR = 19; // 0x13

/**
 * Value of the <code>MONTH</code> field indicating the
 * second month of the year.
 */

public static final int FEBRUARY = 1; // 0x1

/**
 * Value of the <code>DAY_OF_WEEK</code> field indicating
 * Friday.
 */

public static final int FRIDAY = 6; // 0x6

/**
 * Limit type for <code>getLimit()</code> and <code>handleGetLimit()</code>
 * indicating the greatest minimum value that a field can take.
 * @see #getLimit
 * @see #handleGetLimit
 */

protected static final int GREATEST_MINIMUM = 1; // 0x1

/**
 * Field number for <code>get</code> and <code>set</code> indicating the
 * hour of the morning or afternoon. <code>HOUR</code> is used for the 12-hour
 * clock.
 * E.g., at 10:04:15.250 PM the <code>HOUR</code> is 10.
 * @see #AM_PM
 * @see #HOUR_OF_DAY
 */

public static final int HOUR = 10; // 0xa

/**
 * Field number for <code>get</code> and <code>set</code> indicating the
 * hour of the day. <code>HOUR_OF_DAY</code> is used for the 24-hour clock.
 * E.g., at 10:04:15.250 PM the <code>HOUR_OF_DAY</code> is 22.
 * @see #HOUR
 */

public static final int HOUR_OF_DAY = 11; // 0xb

/**
 * Value of the time stamp <code>stamp[]</code> indicating that a field
 * has been set via computations from the time or from other fields.
 * @see #UNSET
 * @see #MINIMUM_USER_STAMP
 */

protected static final int INTERNALLY_SET = 1; // 0x1

/**
 * <strong>[icu]</strong> Field indicating whether or not the current month is a leap month.
 * Should have a value of 0 for non-leap months, and 1 for leap months.
 */

public static final int IS_LEAP_MONTH = 22; // 0x16

/**
 * Value of the <code>MONTH</code> field indicating the
 * first month of the year.
 */

public static final int JANUARY = 0; // 0x0

/**
 * The Julian day of the Gregorian epoch, that is, January 1, 1 on the
 * Gregorian calendar.
 */

protected static final int JAN_1_1_JULIAN_DAY = 1721426; // 0x1a4452

/**
 * <strong>[icu]</strong> Field number for <code>get()</code> and <code>set()</code>
 * indicating the modified Julian day number.  This is different from
 * the conventional Julian day number in two regards.  First, it
 * demarcates days at local zone midnight, rather than noon GMT.
 * Second, it is a local number; that is, it depends on the local time
 * zone.  It can be thought of as a single number that encompasses all
 * the date-related fields.
 */

public static final int JULIAN_DAY = 20; // 0x14

/**
 * Value of the <code>MONTH</code> field indicating the
 * seventh month of the year.
 */

public static final int JULY = 6; // 0x6

/**
 * Value of the <code>MONTH</code> field indicating the
 * sixth month of the year.
 */

public static final int JUNE = 5; // 0x5

/**
 * Limit type for <code>getLimit()</code> and <code>handleGetLimit()</code>
 * indicating the least maximum value that a field can take.
 * @see #getLimit
 * @see #handleGetLimit
 */

protected static final int LEAST_MAXIMUM = 2; // 0x2

/**
 * Value of the <code>MONTH</code> field indicating the
 * third month of the year.
 */

public static final int MARCH = 2; // 0x2

/**
 * Limit type for <code>getLimit()</code> and <code>handleGetLimit()</code>
 * indicating the maximum value that a field can take (greatest maximum).
 * @see #getLimit
 * @see #handleGetLimit
 */

protected static final int MAXIMUM = 3; // 0x3

/**
 * The maximum supported <code>Date</code>.  This value is equivalent
 * to {@link #MAX_JULIAN} and {@link #MAX_MILLIS}.
 */

protected static final java.util.Date MAX_DATE;
static { MAX_DATE = null; }

/**
 * The maximum number of fields possible.  Subclasses must not define
 * more total fields than this number.
 * @deprecated ICU 58 The numeric value may change over time, see ICU ticket #12420.
 */

@Deprecated protected static final int MAX_FIELD_COUNT = 32; // 0x20

/**
 * The maximum supported Julian day.  This value is equivalent to
 * {@link #MAX_MILLIS} and {@link #MAX_DATE}.
 * @see #JULIAN_DAY
 */

protected static final int MAX_JULIAN = 2130706432; // 0x7f000000

/**
 * The maximum supported epoch milliseconds.  This value is equivalent
 * to {@link #MAX_JULIAN} and {@link #MAX_DATE}.
 */

protected static final long MAX_MILLIS = 183882168921600000L; // 0x28d47dbbf19b000L

/**
 * Value of the <code>MONTH</code> field indicating the
 * fifth month of the year.
 */

public static final int MAY = 4; // 0x4

/**
 * Field number for <code>get</code> and <code>set</code> indicating the
 * millisecond within the second.
 * E.g., at 10:04:15.250 PM the <code>MILLISECOND</code> is 250.
 */

public static final int MILLISECOND = 14; // 0xe

/**
 * <strong>[icu]</strong> Field number for <code>get()</code> and <code>set()</code>
 * indicating the milliseconds in the day.  This ranges from 0 to
 * 23:59:59.999 (regardless of DST).  This field behaves
 * <em>exactly</em> like a composite of all time-related fields, not
 * including the zone fields.  As such, it also reflects
 * discontinuities of those fields on DST transition days.  On a day of
 * DST onset, it will jump forward.  On a day of DST cessation, it will
 * jump backward.  This reflects the fact that is must be combined with
 * the DST_OFFSET field to obtain a unique local time value.
 */

public static final int MILLISECONDS_IN_DAY = 21; // 0x15

/**
 * Limit type for <code>getLimit()</code> and <code>handleGetLimit()</code>
 * indicating the minimum value that a field can take (least minimum).
 * @see #getLimit
 * @see #handleGetLimit
 */

protected static final int MINIMUM = 0; // 0x0

/**
 * If the time stamp <code>stamp[]</code> has a value greater than or
 * equal to <code>MINIMUM_USER_SET</code> then it has been set by the
 * user via a call to <code>set()</code>.
 * @see #UNSET
 * @see #INTERNALLY_SET
 */

protected static final int MINIMUM_USER_STAMP = 2; // 0x2

/**
 * Field number for <code>get</code> and <code>set</code> indicating the
 * minute within the hour.
 * E.g., at 10:04:15.250 PM the <code>MINUTE</code> is 4.
 */

public static final int MINUTE = 12; // 0xc

/**
 * The minimum supported <code>Date</code>.  This value is equivalent
 * to {@link #MIN_JULIAN} and {@link #MIN_MILLIS}.
 */

protected static final java.util.Date MIN_DATE;
static { MIN_DATE = null; }

/**
 * The minimum supported Julian day.  This value is equivalent to
 * {@link #MIN_MILLIS} and {@link #MIN_DATE}.
 * @see #JULIAN_DAY
 */

protected static final int MIN_JULIAN = -2130706432; // 0x81000000

/**
 * The minimum supported epoch milliseconds.  This value is equivalent
 * to {@link #MIN_JULIAN} and {@link #MIN_DATE}.
 */

protected static final long MIN_MILLIS = -184303902528000000L; // 0xfd713893bf19b000L

/**
 * Value of the <code>DAY_OF_WEEK</code> field indicating
 * Monday.
 */

public static final int MONDAY = 2; // 0x2

/**
 * Field number for <code>get</code> and <code>set</code> indicating the
 * month. This is a calendar-specific value. The first month of the year is
 * <code>JANUARY</code>; the last depends on the number of months in a year.
 * @see #JANUARY
 * @see #FEBRUARY
 * @see #MARCH
 * @see #APRIL
 * @see #MAY
 * @see #JUNE
 * @see #JULY
 * @see #AUGUST
 * @see #SEPTEMBER
 * @see #OCTOBER
 * @see #NOVEMBER
 * @see #DECEMBER
 * @see #UNDECIMBER
 */

public static final int MONTH = 2; // 0x2

/**
 * Value of the <code>MONTH</code> field indicating the
 * eleventh month of the year.
 */

public static final int NOVEMBER = 10; // 0xa

/**
 * Value of the <code>MONTH</code> field indicating the
 * tenth month of the year.
 */

public static final int OCTOBER = 9; // 0x9

/**
 * The number of milliseconds in one day.  Although ONE_DAY and
 * ONE_WEEK can fit into ints, they must be longs in order to prevent
 * arithmetic overflow when performing (bug 4173516).
 */

protected static final long ONE_DAY = 86400000L; // 0x5265c00L

/**
 * The number of milliseconds in one hour.
 */

protected static final int ONE_HOUR = 3600000; // 0x36ee80

/**
 * The number of milliseconds in one minute.
 */

protected static final int ONE_MINUTE = 60000; // 0xea60

/**
 * The number of milliseconds in one second.
 */

protected static final int ONE_SECOND = 1000; // 0x3e8

/**
 * The number of milliseconds in one week.  Although ONE_DAY and
 * ONE_WEEK can fit into ints, they must be longs in order to prevent
 * arithmetic overflow when performing (bug 4173516).
 */

protected static final long ONE_WEEK = 604800000L; // 0x240c8400L

/**
 * Value of the <code>AM_PM</code> field indicating the
 * period of the day from noon to just before midnight.
 */

public static final int PM = 1; // 0x1

/**
 * Value to OR against resolve table field values for remapping.
 * @see #resolveFields
 */

protected static final int RESOLVE_REMAP = 32; // 0x20

/**
 * Value of the <code>DAY_OF_WEEK</code> field indicating
 * Saturday.
 */

public static final int SATURDAY = 7; // 0x7

/**
 * Field number for <code>get</code> and <code>set</code> indicating the
 * second within the minute.
 * E.g., at 10:04:15.250 PM the <code>SECOND</code> is 15.
 */

public static final int SECOND = 13; // 0xd

/**
 * Value of the <code>MONTH</code> field indicating the
 * ninth month of the year.
 */

public static final int SEPTEMBER = 8; // 0x8

/**
 * Value of the <code>DAY_OF_WEEK</code> field indicating
 * Sunday.
 */

public static final int SUNDAY = 1; // 0x1

/**
 * Value of the <code>DAY_OF_WEEK</code> field indicating
 * Thursday.
 */

public static final int THURSDAY = 5; // 0x5

/**
 * Value of the <code>DAY_OF_WEEK</code> field indicating
 * Tuesday.
 */

public static final int TUESDAY = 3; // 0x3

/**
 * Value of the <code>MONTH</code> field indicating the
 * thirteenth month of the year. Although {@link android.icu.util.GregorianCalendar GregorianCalendar}
 * does not use this value, lunar calendars do.
 */

public static final int UNDECIMBER = 12; // 0xc

/**
 * Value of the time stamp <code>stamp[]</code> indicating that
 * a field has not been set since the last call to <code>clear()</code>.
 * @see #INTERNALLY_SET
 * @see #MINIMUM_USER_STAMP
 */

protected static final int UNSET = 0; // 0x0

/**
 * <strong>[icu]</strong>Option used by {@link #setRepeatedWallTimeOption(int)} and
 * {@link #setSkippedWallTimeOption(int)} specifying an ambiguous wall time
 * to be interpreted as the earliest.
 * @see #setRepeatedWallTimeOption(int)
 * @see #getRepeatedWallTimeOption()
 * @see #setSkippedWallTimeOption(int)
 * @see #getSkippedWallTimeOption()
 */

public static final int WALLTIME_FIRST = 1; // 0x1

/**
 * <strong>[icu]</strong>Option used by {@link #setRepeatedWallTimeOption(int)} and
 * {@link #setSkippedWallTimeOption(int)} specifying an ambiguous wall time
 * to be interpreted as the latest.
 * @see #setRepeatedWallTimeOption(int)
 * @see #getRepeatedWallTimeOption()
 * @see #setSkippedWallTimeOption(int)
 * @see #getSkippedWallTimeOption()
 */

public static final int WALLTIME_LAST = 0; // 0x0

/**
 * <strong>[icu]</strong>Option used by {@link #setSkippedWallTimeOption(int)} specifying an
 * ambiguous wall time to be interpreted as the next valid wall time.
 * @see #setSkippedWallTimeOption(int)
 * @see #getSkippedWallTimeOption()
 */

public static final int WALLTIME_NEXT_VALID = 2; // 0x2

/**
 * Value of the <code>DAY_OF_WEEK</code> field indicating
 * Wednesday.
 */

public static final int WEDNESDAY = 4; // 0x4

/**
 * Field number for <code>get</code> and <code>set</code> indicating the
 * week number within the current month.  The first week of the month, as
 * defined by {@link #getFirstDayOfWeek()} and
 * {@link #getMinimalDaysInFirstWeek()}, has value 1.  Subclasses define
 * the value of {@link #WEEK_OF_MONTH} for days before the first week of
 * the month.
 * @see #getFirstDayOfWeek
 * @see #getMinimalDaysInFirstWeek
 */

public static final int WEEK_OF_MONTH = 4; // 0x4

/**
 * Field number for <code>get</code> and <code>set</code> indicating the
 * week number within the current year.  The first week of the year, as
 * defined by {@link #getFirstDayOfWeek()} and
 * {@link #getMinimalDaysInFirstWeek()}, has value 1.  Subclasses define
 * the value of {@link #WEEK_OF_YEAR} for days before the first week of
 * the year.
 * @see #getFirstDayOfWeek
 * @see #getMinimalDaysInFirstWeek
 */

public static final int WEEK_OF_YEAR = 3; // 0x3

/**
 * Field number for <code>get</code> and <code>set</code> indicating the
 * year. This is a calendar-specific value; see subclass documentation.
 */

public static final int YEAR = 1; // 0x1

/**
 * <strong>[icu]</strong> Field number for <code>get()</code> and <code>set()</code>
 * indicating the extended year corresponding to the
 * {@link #WEEK_OF_YEAR} field.  This may be one greater or less
 * than the value of {@link #EXTENDED_YEAR}.
 */

public static final int YEAR_WOY = 17; // 0x11

/**
 * Field number for <code>get</code> and <code>set</code> indicating the
 * raw offset from GMT in milliseconds.
 */

public static final int ZONE_OFFSET = 15; // 0xf
/**
 * Simple, immutable struct-like class for access to the CLDR week data.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static final class WeekData {

/**
 * Constructor
 *
 * @param fdow the first day of the week, where 1 = {@link #SUNDAY} and 7 = {@link #SATURDAY}
 * @param mdifw the minimal number of days in the first week
 * @param weekendOnset the onset day, where 1 = Sunday and 7 = Saturday
 * @param weekendOnsetMillis the onset time in millis during the onset day
 * @param weekendCease the cease day, where 1 = Sunday and 7 = Saturday
 * @param weekendCeaseMillis the cease time in millis during the cease day.
 */

public WeekData(int fdow, int mdifw, int weekendOnset, int weekendOnsetMillis, int weekendCease, int weekendCeaseMillis) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public int hashCode() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public boolean equals(java.lang.Object other) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.String toString() { throw new RuntimeException("Stub!"); }

/**
 * the first day of the week, where 1 = {@link #SUNDAY} and 7 = {@link #SATURDAY}
 */

public final int firstDayOfWeek;
{ firstDayOfWeek = 0; }

/**
 * the minimal number of days in the first week
 */

public final int minimalDaysInFirstWeek;
{ minimalDaysInFirstWeek = 0; }

/**
 * the cease day, where 1 = {@link #SUNDAY} and 7 = {@link #SATURDAY}
 */

public final int weekendCease;
{ weekendCease = 0; }

/**
 * the cease time in millis during the cease day. Exclusive, so the max is 24:00:00.000.
 * Note that this will format as 00:00 the next day.
 */

public final int weekendCeaseMillis;
{ weekendCeaseMillis = 0; }

/**
 * the onset day, where 1 = {@link #SUNDAY} and 7 = {@link #SATURDAY}
 */

public final int weekendOnset;
{ weekendOnset = 0; }

/**
 * the onset time in millis during the onset day
 */

public final int weekendOnsetMillis;
{ weekendOnsetMillis = 0; }
}

}

