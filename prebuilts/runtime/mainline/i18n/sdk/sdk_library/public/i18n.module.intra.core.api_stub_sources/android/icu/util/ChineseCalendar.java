/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*********************************************************************
 * Copyright (C) 2000-2014, International Business Machines
 * Corporation and others. All Rights Reserved.
 *********************************************************************
 */


package android.icu.util;

import android.icu.text.DateFormat;
import android.icu.util.ULocale.Category;

/**
 * <code>ChineseCalendar</code> is a concrete subclass of {@link android.icu.util.Calendar Calendar}
 * that implements a traditional Chinese calendar.  The traditional Chinese
 * calendar is a lunisolar calendar: Each month starts on a new moon, and
 * the months are numbered according to solar events, specifically, to
 * guarantee that month 11 always contains the winter solstice.  In order
 * to accomplish this, leap months are inserted in certain years.  Leap
 * months are numbered the same as the month they follow.  The decision of
 * which month is a leap month depends on the relative movements of the sun
 * and moon.
 *
 * <p>All astronomical computations are performed with respect to a time
 * zone of GMT+8:00 and a longitude of 120 degrees east.  Although some
 * calendars implement a historically more accurate convention of using
 * Beijing's local longitude (116 degrees 25 minutes east) and time zone
 * (GMT+7:45:40) for dates before 1929, we do not implement this here.
 *
 * <p>Years are counted in two different ways in the Chinese calendar.  The
 * first method is by sequential numbering from the 61st year of the reign
 * of Huang Di, 2637 BCE, which is designated year 1 on the Chinese
 * calendar.  The second method uses 60-year cycles from the same starting
 * point, which is designated year 1 of cycle 1.  In this class, the
 * <code>EXTENDED_YEAR</code> field contains the sequential year count.
 * The <code>ERA</code> field contains the cycle number, and the
 * <code>YEAR</code> field contains the year of the cycle, a value between
 * 1 and 60.
 *
 * <p>There is some variation in what is considered the starting point of
 * the calendar, with some sources starting in the first year of the reign
 * of Huang Di, rather than the 61st.  This gives continuous year numbers
 * 60 years greater and cycle numbers one greater than what this class
 * implements.
 *
 * <p>Because <code>ChineseCalendar</code> defines an additional field and
 * redefines the way the <code>ERA</code> field is used, it requires a new
 * format class, <code>ChineseDateFormat</code>.  As always, use the
 * methods <code>DateFormat.getXxxInstance(Calendar cal,...)</code> to
 * obtain a formatter for this calendar.
 *
 * <p>References:<ul>
 *
 * <li>Dershowitz and Reingold, <i>Calendrical Calculations</i>,
 * Cambridge University Press, 1997</li>
 *
 * <li>Helmer Aslaksen's
 * <a href="http://www.math.nus.edu.sg/aslaksen/calendar/chinese.shtml">
 * Chinese Calendar page</a></li>
 *
 * <li>The <a href="http://www.tondering.dk/claus/calendar.html">
 * Calendar FAQ</a></li>
 *
 * </ul>
 *
 * <p>
 * This class should not be subclassed.</p>
 * <p>
 * ChineseCalendar usually should be instantiated using
 * {@link android.icu.util.Calendar#getInstance(ULocale)} passing in a <code>ULocale</code>
 * with the tag <code>"@calendar=chinese"</code>.</p>
 *
 * @see android.icu.util.Calendar
 * @author Alan Liu
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class ChineseCalendar extends android.icu.util.Calendar {

/**
 * Construct a <code>ChineseCalendar</code> with the default time zone and locale.
 */

public ChineseCalendar() { throw new RuntimeException("Stub!"); }

/**
 * Construct a <code>ChineseCalendar</code> with the give date set in the default time zone
 * with the default locale.
 * @param date The date to which the new calendar is set.
 */

public ChineseCalendar(java.util.Date date) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>ChineseCalendar</code> with the given date set
 * in the default time zone with the default <code>FORMAT</code> locale.
 *
 * @param year      The value used to set the calendar's {@link #YEAR YEAR} time field.
 * @param month     The value used to set the calendar's {@link #MONTH MONTH} time field.
 *                  The value is 0-based. e.g., 0 for January.
 * @param isLeapMonth The value used to set the Chinese calendar's {@link #IS_LEAP_MONTH}
 *                  time field.
 * @param date      The value used to set the calendar's {@link #DATE DATE} time field.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public ChineseCalendar(int year, int month, int isLeapMonth, int date) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>ChineseCalendar</code> with the given date
 * and time set for the default time zone with the default <code>FORMAT</code> locale.
 *
 * @param year  the value used to set the {@link #YEAR YEAR} time field in the calendar.
 * @param month the value used to set the {@link #MONTH MONTH} time field in the calendar.
 *              Note that the month value is 0-based. e.g., 0 for January.
 * @param isLeapMonth the value used to set the {@link #IS_LEAP_MONTH} time field
 *              in the calendar.
 * @param date  the value used to set the {@link #DATE DATE} time field in the calendar.
 * @param hour  the value used to set the {@link #HOUR_OF_DAY HOUR_OF_DAY} time field
 *              in the calendar.
 * @param minute the value used to set the {@link #MINUTE MINUTE} time field
 *              in the calendar.
 * @param second the value used to set the {@link #SECOND SECOND} time field
 *              in the calendar.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public ChineseCalendar(int year, int month, int isLeapMonth, int date, int hour, int minute, int second) { throw new RuntimeException("Stub!"); }

/** 
 * Constructs a <code>ChineseCalendar</code> with the given date set
 * in the default time zone with the default <code>FORMAT</code> locale.
 *
 * @param era       The value used to set the calendar's {@link #ERA ERA} time field.
 * @param year      The value used to set the calendar's {@link #YEAR YEAR} time field.
 * @param month     The value used to set the calendar's {@link #MONTH MONTH} time field.
 *                  The value is 0-based. e.g., 0 for January.
 * @param isLeapMonth The value used to set the Chinese calendar's {@link #IS_LEAP_MONTH}
 *                  time field.
 * @param date      The value used to set the calendar's {@link #DATE DATE} time field.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public ChineseCalendar(int era, int year, int month, int isLeapMonth, int date) { throw new RuntimeException("Stub!"); }

/** 
 * Constructs a <code>ChineseCalendar</code> with the given date
 * and time set for the default time zone with the default <code>FORMAT</code> locale.
 *
 * @param era   the value used to set the calendar's {@link #ERA ERA} time field.
 * @param year  the value used to set the {@link #YEAR YEAR} time field in the calendar.
 * @param month the value used to set the {@link #MONTH MONTH} time field in the calendar.
 *              Note that the month value is 0-based. e.g., 0 for January.
 * @param isLeapMonth the value used to set the {@link #IS_LEAP_MONTH} time field
 *              in the calendar.
 * @param date  the value used to set the {@link #DATE DATE} time field in the calendar.
 * @param hour  the value used to set the {@link #HOUR_OF_DAY HOUR_OF_DAY} time field
 *              in the calendar.
 * @param minute the value used to set the {@link #MINUTE MINUTE} time field
 *              in the calendar.
 * @param second the value used to set the {@link #SECOND SECOND} time field
 *              in the calendar.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public ChineseCalendar(int era, int year, int month, int isLeapMonth, int date, int hour, int minute, int second) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>ChineseCalendar</code> based on the current time
 * in the default time zone with the given locale.
 * @param aLocale The given locale
 */

public ChineseCalendar(java.util.Locale aLocale) { throw new RuntimeException("Stub!"); }

/**
 * Construct a <code>ChineseCalendar</code> based on the current time
 * in the given time zone with the default <code>FORMAT</code> locale.
 * @param zone the given time zone
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public ChineseCalendar(android.icu.util.TimeZone zone) { throw new RuntimeException("Stub!"); }

/**
 * Construct a <code>ChineseCalendar</code> based on the current time
 * in the given time zone with the given locale.
 * @param zone the given time zone
 * @param aLocale the given locale
 */

public ChineseCalendar(android.icu.util.TimeZone zone, java.util.Locale aLocale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>ChineseCalendar</code> based on the current time
 * in the default time zone with the given locale.
 *
 * @param locale the given ulocale
 */

public ChineseCalendar(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Construct a <code>ChineseCalendar</code>  based on the current time
 * with the given time zone with the given locale.
 * @param zone the given time zone
 * @param locale the given ulocale
 */

public ChineseCalendar(android.icu.util.TimeZone zone, android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Override Calendar to return the limit value for the given field.
 */

protected int handleGetLimit(int field, int limitType) { throw new RuntimeException("Stub!"); }

/**
 * Implement abstract Calendar method to return the extended year
 * defined by the current fields.  This will use either the ERA and
 * YEAR field as the cycle and year-of-cycle, or the EXTENDED_YEAR
 * field as the continuous year count, depending on which is newer.
 */

protected int handleGetExtendedYear() { throw new RuntimeException("Stub!"); }

/**
 * Override Calendar method to return the number of days in the given
 * extended year and month.
 *
 * <p>Note: This method also reads the IS_LEAP_MONTH field to determine
 * whether or not the given month is a leap month.
 */

protected int handleGetMonthLength(int extendedYear, int month) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

protected android.icu.text.DateFormat handleGetDateFormat(java.lang.String pattern, java.lang.String override, android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Override Calendar to add IS_LEAP_MONTH to the field resolution
 * table.
 */

protected int[][][] getFieldResolutionTable() { throw new RuntimeException("Stub!"); }

/**
 * Override Calendar to handle leap months properly.
 */

public void add(int field, int amount) { throw new RuntimeException("Stub!"); }

/**
 * Override Calendar to handle leap months properly.
 */

public void roll(int field, int amount) { throw new RuntimeException("Stub!"); }

/**
 * Override Calendar to compute several fields specific to the Chinese
 * calendar system.  These are:
 *
 * <ul><li>ERA
 * <li>YEAR
 * <li>MONTH
 * <li>DAY_OF_MONTH
 * <li>DAY_OF_YEAR
 * <li>EXTENDED_YEAR</ul>
 *
 * The DAY_OF_WEEK and DOW_LOCAL fields are already set when this
 * method is called.  The getGregorianXxx() methods return Gregorian
 * calendar equivalents for the given Julian day.
 *
 * <p>Compute the ChineseCalendar-specific field IS_LEAP_MONTH.
 */

protected void handleComputeFields(int julianDay) { throw new RuntimeException("Stub!"); }

/**
 * Return the Julian day number of day before the first day of the
 * given month in the given extended year.
 *
 * <p>Note: This method reads the IS_LEAP_MONTH field to determine
 * whether the given month is a leap month.
 * @param eyear the extended year
 * @param month the zero-based month.  The month is also determined
 * by reading the IS_LEAP_MONTH field.
 * @return the Julian day number of the day before the first
 * day of the given month and year
 */

protected int handleComputeMonthStart(int eyear, int month, boolean useMonth) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.String getType() { throw new RuntimeException("Stub!"); }
}

