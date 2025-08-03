/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 * Copyright (C) 1996-2016, International Business Machines
 * Corporation and others.  All Rights Reserved.
 */


package android.icu.util;

import java.util.Date;
import android.icu.util.ULocale.Category;

/**
 * <strong>[icu enhancement]</strong> ICU's replacement for {@link java.util.GregorianCalendar}.&nbsp;Methods, fields, and other functionality specific to ICU are labeled '<strong>[icu]</strong>'.
 *
 * <p><code>GregorianCalendar</code> is a concrete subclass of
 * {@link android.icu.util.Calendar Calendar}
 * and provides the standard calendar used by most of the world.
 *
 * <p>The standard (Gregorian) calendar has 2 eras, BC and AD.
 *
 * <p>This implementation handles a single discontinuity, which corresponds by
 * default to the date the Gregorian calendar was instituted (October 15, 1582
 * in some countries, later in others).  The cutover date may be changed by the
 * caller by calling <code>setGregorianChange()</code>.
 *
 * <p>Historically, in those countries which adopted the Gregorian calendar first,
 * October 4, 1582 was thus followed by October 15, 1582. This calendar models
 * this correctly.  Before the Gregorian cutover, <code>GregorianCalendar</code>
 * implements the Julian calendar.  The only difference between the Gregorian
 * and the Julian calendar is the leap year rule. The Julian calendar specifies
 * leap years every four years, whereas the Gregorian calendar omits century
 * years which are not divisible by 400.
 *
 * <p><code>GregorianCalendar</code> implements <em>proleptic</em> Gregorian and
 * Julian calendars. That is, dates are computed by extrapolating the current
 * rules indefinitely far backward and forward in time. As a result,
 * <code>GregorianCalendar</code> may be used for all years to generate
 * meaningful and consistent results. However, dates obtained using
 * <code>GregorianCalendar</code> are historically accurate only from March 1, 4
 * AD onward, when modern Julian calendar rules were adopted.  Before this date,
 * leap year rules were applied irregularly, and before 45 BC the Julian
 * calendar did not even exist.
 *
 * <p>Prior to the institution of the Gregorian calendar, New Year's Day was
 * March 25. To avoid confusion, this calendar always uses January 1. A manual
 * adjustment may be made if desired for dates that are prior to the Gregorian
 * changeover and which fall between January 1 and March 24.
 *
 * <p>Values calculated for the <code>WEEK_OF_YEAR</code> field range from 1 to
 * 53.  Week 1 for a year is the earliest seven day period starting on
 * <code>getFirstDayOfWeek()</code> that contains at least
 * <code>getMinimalDaysInFirstWeek()</code> days from that year.  It thus
 * depends on the values of <code>getMinimalDaysInFirstWeek()</code>,
 * <code>getFirstDayOfWeek()</code>, and the day of the week of January 1.
 * Weeks between week 1 of one year and week 1 of the following year are
 * numbered sequentially from 2 to 52 or 53 (as needed).
 
 * <p>For example, January 1, 1998 was a Thursday.  If
 * <code>getFirstDayOfWeek()</code> is <code>MONDAY</code> and
 * <code>getMinimalDaysInFirstWeek()</code> is 4 (these are the values
 * reflecting ISO 8601 and many national standards), then week 1 of 1998 starts
 * on December 29, 1997, and ends on January 4, 1998.  If, however,
 * <code>getFirstDayOfWeek()</code> is <code>SUNDAY</code>, then week 1 of 1998
 * starts on January 4, 1998, and ends on January 10, 1998; the first three days
 * of 1998 then are part of week 53 of 1997.
 *
 * <p>Values calculated for the <code>WEEK_OF_MONTH</code> field range from 0 or
 * 1 to 4 or 5.  Week 1 of a month (the days with <code>WEEK_OF_MONTH =
 * 1</code>) is the earliest set of at least
 * <code>getMinimalDaysInFirstWeek()</code> contiguous days in that month,
 * ending on the day before <code>getFirstDayOfWeek()</code>.  Unlike
 * week 1 of a year, week 1 of a month may be shorter than 7 days, need
 * not start on <code>getFirstDayOfWeek()</code>, and will not include days of
 * the previous month.  Days of a month before week 1 have a
 * <code>WEEK_OF_MONTH</code> of 0.
 *
 * <p>For example, if <code>getFirstDayOfWeek()</code> is <code>SUNDAY</code>
 * and <code>getMinimalDaysInFirstWeek()</code> is 4, then the first week of
 * January 1998 is Sunday, January 4 through Saturday, January 10.  These days
 * have a <code>WEEK_OF_MONTH</code> of 1.  Thursday, January 1 through
 * Saturday, January 3 have a <code>WEEK_OF_MONTH</code> of 0.  If
 * <code>getMinimalDaysInFirstWeek()</code> is changed to 3, then January 1
 * through January 3 have a <code>WEEK_OF_MONTH</code> of 1.
 *
 * <p>
 * <strong>Example:</strong>
 * <blockquote>
 * <pre>
 * // get the supported ids for GMT-08:00 (Pacific Standard Time)
 * String[] ids = TimeZone.getAvailableIDs(-8 * 60 * 60 * 1000);
 * // if no ids were returned, something is wrong. get out.
 * if (ids.length == 0)
 *     System.exit(0);
 *
 *  // begin output
 * System.out.println("Current Time");
 *
 * // create a Pacific Standard Time time zone
 * SimpleTimeZone pdt = new SimpleTimeZone(-8 * 60 * 60 * 1000, ids[0]);
 *
 * // set up rules for daylight savings time
 * pdt.setStartRule(Calendar.MARCH, 2, Calendar.SUNDAY, 2 * 60 * 60 * 1000);
 * pdt.setEndRule(Calendar.NOVEMBER, 1, Calendar.SUNDAY, 2 * 60 * 60 * 1000);
 *
 * // create a GregorianCalendar with the Pacific Daylight time zone
 * // and the current date and time
 * Calendar calendar = new GregorianCalendar(pdt);
 * Date trialTime = new Date();
 * calendar.setTime(trialTime);
 *
 * // print out a bunch of interesting things
 * System.out.println("ERA: " + calendar.get(Calendar.ERA));
 * System.out.println("YEAR: " + calendar.get(Calendar.YEAR));
 * System.out.println("MONTH: " + calendar.get(Calendar.MONTH));
 * System.out.println("WEEK_OF_YEAR: " + calendar.get(Calendar.WEEK_OF_YEAR));
 * System.out.println("WEEK_OF_MONTH: " + calendar.get(Calendar.WEEK_OF_MONTH));
 * System.out.println("DATE: " + calendar.get(Calendar.DATE));
 * System.out.println("DAY_OF_MONTH: " + calendar.get(Calendar.DAY_OF_MONTH));
 * System.out.println("DAY_OF_YEAR: " + calendar.get(Calendar.DAY_OF_YEAR));
 * System.out.println("DAY_OF_WEEK: " + calendar.get(Calendar.DAY_OF_WEEK));
 * System.out.println("DAY_OF_WEEK_IN_MONTH: "
 *                    + calendar.get(Calendar.DAY_OF_WEEK_IN_MONTH));
 * System.out.println("AM_PM: " + calendar.get(Calendar.AM_PM));
 * System.out.println("HOUR: " + calendar.get(Calendar.HOUR));
 * System.out.println("HOUR_OF_DAY: " + calendar.get(Calendar.HOUR_OF_DAY));
 * System.out.println("MINUTE: " + calendar.get(Calendar.MINUTE));
 * System.out.println("SECOND: " + calendar.get(Calendar.SECOND));
 * System.out.println("MILLISECOND: " + calendar.get(Calendar.MILLISECOND));
 * System.out.println("ZONE_OFFSET: "
 *                    + (calendar.get(Calendar.ZONE_OFFSET)/(60*60*1000)));
 * System.out.println("DST_OFFSET: "
 *                    + (calendar.get(Calendar.DST_OFFSET)/(60*60*1000)));
 
 * System.out.println("Current Time, with hour reset to 3");
 * calendar.clear(Calendar.HOUR_OF_DAY); // so doesn't override
 * calendar.set(Calendar.HOUR, 3);
 * System.out.println("ERA: " + calendar.get(Calendar.ERA));
 * System.out.println("YEAR: " + calendar.get(Calendar.YEAR));
 * System.out.println("MONTH: " + calendar.get(Calendar.MONTH));
 * System.out.println("WEEK_OF_YEAR: " + calendar.get(Calendar.WEEK_OF_YEAR));
 * System.out.println("WEEK_OF_MONTH: " + calendar.get(Calendar.WEEK_OF_MONTH));
 * System.out.println("DATE: " + calendar.get(Calendar.DATE));
 * System.out.println("DAY_OF_MONTH: " + calendar.get(Calendar.DAY_OF_MONTH));
 * System.out.println("DAY_OF_YEAR: " + calendar.get(Calendar.DAY_OF_YEAR));
 * System.out.println("DAY_OF_WEEK: " + calendar.get(Calendar.DAY_OF_WEEK));
 * System.out.println("DAY_OF_WEEK_IN_MONTH: "
 *                    + calendar.get(Calendar.DAY_OF_WEEK_IN_MONTH));
 * System.out.println("AM_PM: " + calendar.get(Calendar.AM_PM));
 * System.out.println("HOUR: " + calendar.get(Calendar.HOUR));
 * System.out.println("HOUR_OF_DAY: " + calendar.get(Calendar.HOUR_OF_DAY));
 * System.out.println("MINUTE: " + calendar.get(Calendar.MINUTE));
 * System.out.println("SECOND: " + calendar.get(Calendar.SECOND));
 * System.out.println("MILLISECOND: " + calendar.get(Calendar.MILLISECOND));
 * System.out.println("ZONE_OFFSET: "
 *        + (calendar.get(Calendar.ZONE_OFFSET)/(60*60*1000))); // in hours
 * System.out.println("DST_OFFSET: "
 *        + (calendar.get(Calendar.DST_OFFSET)/(60*60*1000))); // in hours</pre>
 * </blockquote>
 * <p>
 * GregorianCalendar usually should be instantiated using
 * {@link android.icu.util.Calendar#getInstance(ULocale)} passing in a <code>ULocale</code>
 * with the tag <code>"@calendar=gregorian"</code>.</p>
 
 * @see          android.icu.util.Calendar
 * @see          android.icu.util.TimeZone
 * @author Deborah Goldsmith, Mark Davis, Chen-Lieh Huang, Alan Liu
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class GregorianCalendar extends android.icu.util.Calendar {

/**
 * Constructs a default GregorianCalendar using the current time
 * in the default time zone with the default <code>FORMAT</code> locale.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public GregorianCalendar() { throw new RuntimeException("Stub!"); }

/**
 * Constructs a GregorianCalendar based on the current time
 * in the given time zone with the default <code>FORMAT</code> locale.
 * @param zone the given time zone.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public GregorianCalendar(android.icu.util.TimeZone zone) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a GregorianCalendar based on the current time
 * in the default time zone with the given locale.
 * @param aLocale the given locale.
 */

public GregorianCalendar(java.util.Locale aLocale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Constructs a GregorianCalendar based on the current time
 * in the default time zone with the given locale.
 * @param locale the given ulocale.
 */

public GregorianCalendar(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Constructs a GregorianCalendar based on the current time
 * in the given time zone with the given locale.
 * @param zone the given time zone.
 * @param aLocale the given locale.
 */

public GregorianCalendar(android.icu.util.TimeZone zone, java.util.Locale aLocale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a GregorianCalendar based on the current time
 * in the given time zone with the given locale.
 * @param zone the given time zone.
 * @param locale the given ulocale.
 */

public GregorianCalendar(android.icu.util.TimeZone zone, android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a GregorianCalendar with the given date set
 * in the default time zone with the default <code>FORMAT</code> locale.
 * @param year the value used to set the YEAR time field in the calendar.
 * @param month the value used to set the MONTH time field in the calendar.
 * Month value is 0-based. e.g., 0 for January.
 * @param date the value used to set the DATE time field in the calendar.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public GregorianCalendar(int year, int month, int date) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a GregorianCalendar with the given date
 * and time set for the default time zone with the default <code>FORMAT</code> locale.
 * @param year the value used to set the YEAR time field in the calendar.
 * @param month the value used to set the MONTH time field in the calendar.
 * Month value is 0-based. e.g., 0 for January.
 * @param date the value used to set the DATE time field in the calendar.
 * @param hour the value used to set the HOUR_OF_DAY time field
 * in the calendar.
 * @param minute the value used to set the MINUTE time field
 * in the calendar.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public GregorianCalendar(int year, int month, int date, int hour, int minute) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a GregorianCalendar with the given date
 * and time set for the default time zone with the default <code>FORMAT</code> locale.
 * @param year the value used to set the YEAR time field in the calendar.
 * @param month the value used to set the MONTH time field in the calendar.
 * Month value is 0-based. e.g., 0 for January.
 * @param date the value used to set the DATE time field in the calendar.
 * @param hour the value used to set the HOUR_OF_DAY time field
 * in the calendar.
 * @param minute the value used to set the MINUTE time field
 * in the calendar.
 * @param second the value used to set the SECOND time field
 * in the calendar.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public GregorianCalendar(int year, int month, int date, int hour, int minute, int second) { throw new RuntimeException("Stub!"); }

/**
 */

protected int handleGetLimit(int field, int limitType) { throw new RuntimeException("Stub!"); }

/**
 * Sets the GregorianCalendar change date. This is the point when the switch
 * from Julian dates to Gregorian dates occurred. Default is October 15,
 * 1582. Previous to this, dates will be in the Julian calendar.
 * <p>
 * To obtain a pure Julian calendar, set the change date to
 * <code>Date(Long.MAX_VALUE)</code>.  To obtain a pure Gregorian calendar,
 * set the change date to <code>Date(Long.MIN_VALUE)</code>.
 *
 * @param date the given Gregorian cutover date.
 */

public void setGregorianChange(java.util.Date date) { throw new RuntimeException("Stub!"); }

/**
 * Gets the Gregorian Calendar change date.  This is the point when the
 * switch from Julian dates to Gregorian dates occurred. Default is
 * October 15, 1582. Previous to this, dates will be in the Julian
 * calendar.
 * @return the Gregorian cutover date for this calendar.
 */

public final java.util.Date getGregorianChange() { throw new RuntimeException("Stub!"); }

/**
 * Determines if the given year is a leap year. Returns true if the
 * given year is a leap year.
 * @param year the given year.
 * @return true if the given year is a leap year; false otherwise.
 */

public boolean isLeapYear(int year) { throw new RuntimeException("Stub!"); }

/**
 * Returns true if the given Calendar object is equivalent to this
 * one.  Calendar override.
 *
 * @param other the Calendar to be compared with this Calendar
 */

public boolean isEquivalentTo(android.icu.util.Calendar other) { throw new RuntimeException("Stub!"); }

/**
 * Override hashCode.
 * Generates the hash code for the GregorianCalendar object
 */

public int hashCode() { throw new RuntimeException("Stub!"); }

/**
 * Roll a field by a signed amount.
 */

public void roll(int field, int amount) { throw new RuntimeException("Stub!"); }

/**
 * Return the minimum value that this field could have, given the current date.
 * For the Gregorian calendar, this is the same as getMinimum() and getGreatestMinimum().
 */

public int getActualMinimum(int field) { throw new RuntimeException("Stub!"); }

/**
 * Return the maximum value that this field could have, given the current date.
 * For example, with the date "Feb 3, 1997" and the DAY_OF_MONTH field, the actual
 * maximum would be 28; for "Feb 3, 1996" it s 29.  Similarly for a Hebrew calendar,
 * for some years the actual maximum for MONTH is 12, and for others 13.
 */

public int getActualMaximum(int field) { throw new RuntimeException("Stub!"); }

/**
 */

protected int handleGetMonthLength(int extendedYear, int month) { throw new RuntimeException("Stub!"); }

/**
 */

protected int handleGetYearLength(int eyear) { throw new RuntimeException("Stub!"); }

/**
 * Override Calendar to compute several fields specific to the hybrid
 * Gregorian-Julian calendar system.  These are:
 *
 * <ul><li>ERA
 * <li>YEAR
 * <li>MONTH
 * <li>DAY_OF_MONTH
 * <li>DAY_OF_YEAR
 * <li>EXTENDED_YEAR</ul>
 */

protected void handleComputeFields(int julianDay) { throw new RuntimeException("Stub!"); }

/**
 */

protected int handleGetExtendedYear() { throw new RuntimeException("Stub!"); }

/**
 */

protected int handleComputeJulianDay(int bestField) { throw new RuntimeException("Stub!"); }

/**
 * Return JD of start of given month/year
 */

protected int handleComputeMonthStart(int eyear, int month, boolean useMonth) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.String getType() { throw new RuntimeException("Stub!"); }

/**
 * Value of the <code>ERA</code> field indicating
 * the common era (Anno Domini), also known as CE.
 * The sequence of years at the transition from <code>BC</code> to <code>AD</code> is
 * ..., 2 BC, 1 BC, 1 AD, 2 AD,...
 * @see android.icu.util.Calendar#ERA
 */

public static final int AD = 1; // 0x1

/**
 * Value of the <code>ERA</code> field indicating
 * the period before the common era (before Christ), also known as BCE.
 * The sequence of years at the transition from <code>BC</code> to <code>AD</code> is
 * ..., 2 BC, 1 BC, 1 AD, 2 AD,...
 * @see android.icu.util.Calendar#ERA
 */

public static final int BC = 0; // 0x0

/**
 * Used by handleComputeJulianDay() and handleComputeMonthStart().
 */

protected transient boolean invertGregorian;

/**
 * Used by handleComputeJulianDay() and handleComputeMonthStart().
 */

protected transient boolean isGregorian;
}

