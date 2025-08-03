/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 1996-2012, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */


package android.icu.util;


/**
 * <code>BuddhistCalendar</code> is a subclass of <code>GregorianCalendar</code>
 * that numbers years since the birth of the Buddha.  This is the civil calendar
 * in some predominantly Buddhist countries such as Thailand, and it is used for
 * religious purposes elsewhere.
 * <p>
 * The Buddhist calendar is identical to the Gregorian calendar in all respects
 * except for the year and era.  Years are numbered since the birth of the
 * Buddha in 543 BC (Gregorian), so that 1 AD (Gregorian) is equivalent to 544
 * BE (Buddhist Era) and 1998 AD is 2541 BE.
 * <p>
 * The Buddhist Calendar has only one allowable era: <code>BE</code>.  If the
 * calendar is not in lenient mode (see <code>setLenient</code>), dates before
 * 1/1/1 BE are rejected with an <code>IllegalArgumentException</code>.
 * <p>
 * This class should not be subclassed.</p>
 * <p>
 * BuddhistCalendar usually should be instantiated using
 * {@link android.icu.util.Calendar#getInstance(ULocale)} passing in a <code>ULocale</code>
 * with the tag <code>"@calendar=buddhist"</code>.</p>
 *
 * @see android.icu.util.Calendar
 * @see android.icu.util.GregorianCalendar
 *
 * @author Laura Werner
 * @author Alan Liu
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class BuddhistCalendar extends android.icu.util.GregorianCalendar {

/**
 * Constructs a <code>BuddhistCalendar</code> using the current time
 * in the default time zone with the default locale.
 */

public BuddhistCalendar() { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>BuddhistCalendar</code> based on the current time
 * in the given time zone with the default locale.
 *
 * @param zone the given time zone.
 */

public BuddhistCalendar(android.icu.util.TimeZone zone) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>BuddhistCalendar</code> based on the current time
 * in the default time zone with the given locale.
 *
 * @param aLocale the given locale.
 */

public BuddhistCalendar(java.util.Locale aLocale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>BuddhistCalendar</code> based on the current time
 * in the default time zone with the given locale.
 *
 * @param locale the given ulocale.
 */

public BuddhistCalendar(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>BuddhistCalendar</code> based on the current time
 * in the given time zone with the given locale.
 *
 * @param zone the given time zone.
 *
 * @param aLocale the given locale.
 */

public BuddhistCalendar(android.icu.util.TimeZone zone, java.util.Locale aLocale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>BuddhistCalendar</code> based on the current time
 * in the given time zone with the given locale.
 *
 * @param zone the given time zone.
 *
 * @param locale the given ulocale.
 */

public BuddhistCalendar(android.icu.util.TimeZone zone, android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>BuddhistCalendar</code> with the given date set
 * in the default time zone with the default locale.
 *
 * @param date      The date to which the new calendar is set.
 */

public BuddhistCalendar(java.util.Date date) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>BuddhistCalendar</code> with the given date set
 * in the default time zone with the default locale.
 *
 * @param year      The value used to set the calendar's {@link #YEAR YEAR} time field.
 *
 * @param month     The value used to set the calendar's {@link #MONTH MONTH} time field.
 *                  The value is 0-based. e.g., 0 for January.
 *
 * @param date      The value used to set the calendar's {@link #DATE DATE} time field.
 */

public BuddhistCalendar(int year, int month, int date) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a BuddhistCalendar with the given date
 * and time set for the default time zone with the default locale.
 *
 * @param year      The value used to set the calendar's {@link #YEAR YEAR} time field.
 *
 * @param month     The value used to set the calendar's {@link #MONTH MONTH} time field.
 *                  The value is 0-based. e.g., 0 for January.
 *
 * @param date      The value used to set the calendar's {@link #DATE DATE} time field.
 *
 * @param hour      The value used to set the calendar's {@link #HOUR_OF_DAY HOUR_OF_DAY} time field.
 *
 * @param minute    The value used to set the calendar's {@link #MINUTE MINUTE} time field.
 *
 * @param second    The value used to set the calendar's {@link #SECOND SECOND} time field.
 */

public BuddhistCalendar(int year, int month, int date, int hour, int minute, int second) { throw new RuntimeException("Stub!"); }

/**
 */

protected int handleGetExtendedYear() { throw new RuntimeException("Stub!"); }

/**
 */

protected int handleComputeMonthStart(int eyear, int month, boolean useMonth) { throw new RuntimeException("Stub!"); }

/**
 */

protected void handleComputeFields(int julianDay) { throw new RuntimeException("Stub!"); }

/**
 * Override GregorianCalendar.  There is only one Buddhist ERA.  We
 * should really handle YEAR, YEAR_WOY, and EXTENDED_YEAR here too to
 * implement the 1..5000000 range, but it's not critical.
 */

protected int handleGetLimit(int field, int limitType) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.String getType() { throw new RuntimeException("Stub!"); }

/**
 * Constant for the Buddhist Era.  This is the only allowable <code>ERA</code>
 * value for the Buddhist calendar.
 *
 * @see android.icu.util.Calendar#ERA
 */

public static final int BE = 0; // 0x0
}

