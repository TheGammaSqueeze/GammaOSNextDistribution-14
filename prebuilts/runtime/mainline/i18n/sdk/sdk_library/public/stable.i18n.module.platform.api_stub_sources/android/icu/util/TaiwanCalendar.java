/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 1996-2010, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */


package android.icu.util;


/** 
 * <code>TaiwanCalendar</code> is a subclass of <code>GregorianCalendar</code>
 * that numbers years since 1912.
 * <p>
 * The Taiwan calendar is identical to the Gregorian calendar in all respects
 * except for the year and era.  Years are numbered since 1912 AD (Gregorian).
 * <p>
 * The Taiwan Calendar has one era: <code>MINGUO</code>.
 * <p>
 * This class should not be subclassed.</p>
 * <p>
 * TaiwanCalendar usually should be instantiated using
 * {@link android.icu.util.Calendar#getInstance(ULocale)} passing in a <code>ULocale</code>
 * with the tag <code>"@calendar=roc"</code>.</p>
 *
 * @see android.icu.util.Calendar
 * @see android.icu.util.GregorianCalendar
 *
 * @author Laura Werner
 * @author Alan Liu
 * @author Steven R. Loomis
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class TaiwanCalendar extends android.icu.util.GregorianCalendar {

/**
 * Constructs a <code>TaiwanCalendar</code> using the current time
 * in the default time zone with the default locale.
 */

public TaiwanCalendar() { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>TaiwanCalendar</code> based on the current time
 * in the given time zone with the default locale.
 *
 * @param zone the given time zone.
 */

public TaiwanCalendar(android.icu.util.TimeZone zone) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>TaiwanCalendar</code> based on the current time
 * in the default time zone with the given locale.
 *
 * @param aLocale the given locale.
 */

public TaiwanCalendar(java.util.Locale aLocale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>TaiwanCalendar</code> based on the current time
 * in the default time zone with the given locale.
 *
 * @param locale the given ulocale.
 */

public TaiwanCalendar(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>TaiwanCalendar</code> based on the current time
 * in the given time zone with the given locale.
 *
 * @param zone the given time zone.
 */

public TaiwanCalendar(android.icu.util.TimeZone zone, java.util.Locale aLocale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>TaiwanCalendar</code> based on the current time
 * in the given time zone with the given locale.
 *
 * @param zone the given time zone.
 * @param locale the given ulocale.
 */

public TaiwanCalendar(android.icu.util.TimeZone zone, android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>TaiwanCalendar</code> with the given date set
 * in the default time zone with the default locale.
 *
 * @param date      The date to which the new calendar is set.
 */

public TaiwanCalendar(java.util.Date date) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>TaiwanCalendar</code> with the given date set
 * in the default time zone with the default locale.
 *
 * @param year      The value used to set the calendar's {@link #YEAR YEAR} time field.
 *
 * @param month     The value used to set the calendar's {@link #MONTH MONTH} time field.
 *                  The value is 0-based. e.g., 0 for January.
 *
 * @param date      The value used to set the calendar's {@link #DATE DATE} time field.
 */

public TaiwanCalendar(int year, int month, int date) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a TaiwanCalendar with the given date
 * and time set for the default time zone with the default locale.
 *
 * @param year      The value used to set the calendar's {@link #YEAR YEAR} time field.
 *
 * @param month     The value used to set the calendar's {@link #MONTH MONTH} time field.
 *                  The value is 0-based. e.g., 0 for January.
 * @param date      The value used to set the calendar's {@link #DATE DATE} time field.
 * @param hour      The value used to set the calendar's {@link #HOUR_OF_DAY HOUR_OF_DAY} time field.
 * @param minute    The value used to set the calendar's {@link #MINUTE MINUTE} time field.
 * @param second    The value used to set the calendar's {@link #SECOND SECOND} time field.
 */

public TaiwanCalendar(int year, int month, int date, int hour, int minute, int second) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

protected int handleGetExtendedYear() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

protected void handleComputeFields(int julianDay) { throw new RuntimeException("Stub!"); }

/**
 * Override GregorianCalendar.  There is only one Taiwan ERA.  We
 * should really handle YEAR, YEAR_WOY, and EXTENDED_YEAR here too to
 * implement the 1..5000000 range, but it's not critical.
 */

protected int handleGetLimit(int field, int limitType) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.String getType() { throw new RuntimeException("Stub!"); }

/**
 * Constant for the Taiwan Era for years before Minguo 1.
 * Brefore Minuo 1 is Gregorian 1911, Before Minguo 2 is Gregorian 1910
 * and so on.
 *
 * @see android.icu.util.Calendar#ERA
 */

public static final int BEFORE_MINGUO = 0; // 0x0

/**
 * Constant for the Taiwan Era for Minguo.  Minguo 1 is 1912 in
 * Gregorian calendar.
 *
 * @see android.icu.util.Calendar#ERA
 */

public static final int MINGUO = 1; // 0x1
}

