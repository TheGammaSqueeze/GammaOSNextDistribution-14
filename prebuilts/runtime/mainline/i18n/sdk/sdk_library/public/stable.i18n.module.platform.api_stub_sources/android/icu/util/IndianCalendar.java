/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 1996-2011, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */


package android.icu.util;

import android.icu.util.ULocale.Category;

/**
 * <code>IndianCalendar</code> is a subclass of <code>GregorianCalendar</code>
 * that numbers years since the birth of the Buddha.  This is the civil calendar
 * which is accepted by government of India as Indian National Calendar.
 * The two calendars most widely used in India today are the Vikrama calendar
 * followed in North India and the Shalivahana or Saka calendar which is followed
 * in South India and Maharashtra.
 
 * A variant of the Shalivahana Calendar was reformed and standardized as the
 * Indian National calendar in 1957.
 * <p>
 * Some details of Indian National Calendar (to be implemented) :
 * The Months
 * Month          Length      Start date (Gregorian)
 * =================================================
 * 1 Chaitra      30/31          March 22*
 * 2 Vaisakha     31             April 21
 * 3 Jyaistha     31             May 22
 * 4 Asadha       31             June 22
 * 5 Sravana      31             July 23
 * 6 Bhadra       31             August 23
 * 7 Asvina       30             September 23
 * 8 Kartika      30             October 23
 * 9 Agrahayana   30             November 22
 * 10 Pausa       30             December 22
 * 11 Magha       30             January 21
 * 12 Phalguna    30             February 20
 
 * In leap years, Chaitra has 31 days and starts on March 21 instead.
 * The leap years of Gregorian calendar and Indian National Calendar are in synchornization.
 * So When its a leap year in Gregorian calendar then Chaitra has 31 days.
 *
 * The Years
 * Years are counted in the Saka Era, which starts its year 0 in 78AD (by gregorian calendar).
 * So for eg. 9th June 2006 by Gregorian Calendar, is same as 19th of Jyaistha in 1928 of Saka
 * era by Indian National Calendar.
 * <p>
 * The Indian Calendar has only one allowable era: <code>Saka Era</code>.  If the
 * calendar is not in lenient mode (see <code>setLenient</code>), dates before
 * 1/1/1 Saka Era are rejected with an <code>IllegalArgumentException</code>.
 * <p>
 * This class should not be subclassed.</p>
 * <p>
 * IndianCalendar usually should be instantiated using
 * {@link android.icu.util.Calendar#getInstance(ULocale)} passing in a <code>ULocale</code>
 * with the tag <code>"@calendar=Indian"</code>.</p>
 *
 * @see android.icu.util.Calendar
 * @see android.icu.util.GregorianCalendar
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class IndianCalendar extends android.icu.util.Calendar {

/**
 * Constructs a <code>IndianCalendar</code> using the current time
 * in the default time zone with the default <code>FORMAT</code> locale.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public IndianCalendar() { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>IndianCalendar</code> based on the current time
 * in the given time zone with the default <code>FORMAT</code> locale.
 *
 * @param zone the given time zone.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public IndianCalendar(android.icu.util.TimeZone zone) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>IndianCalendar</code> based on the current time
 * in the default time zone with the given locale.
 *
 * @param aLocale the given locale.
 */

public IndianCalendar(java.util.Locale aLocale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>IndianCalendar</code> based on the current time
 * in the default time zone with the given locale.
 *
 * @param locale the given ulocale.
 */

public IndianCalendar(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>IndianCalendar</code> based on the current time
 * in the given time zone with the given locale.
 *
 * @param zone the given time zone.
 *
 * @param aLocale the given locale.
 */

public IndianCalendar(android.icu.util.TimeZone zone, java.util.Locale aLocale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>IndianCalendar</code> based on the current time
 * in the given time zone with the given locale.
 *
 * @param zone the given time zone.
 *
 * @param locale the given ulocale.
 */

public IndianCalendar(android.icu.util.TimeZone zone, android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>IndianCalendar</code> with the given date set
 * in the default time zone with the default <code>FORMAT</code> locale.
 *
 * @param date      The date to which the new calendar is set.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public IndianCalendar(java.util.Date date) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>IndianCalendar</code> with the given date set
 * in the default time zone with the default <code>FORMAT</code> locale.
 *
 * @param year      The value used to set the calendar's {@link #YEAR YEAR} time field.
 *
 * @param month     The value used to set the calendar's {@link #MONTH MONTH} time field.
 *                  The value is 0-based. e.g., 0 for January.
 *
 * @param date      The value used to set the calendar's {@link #DATE DATE} time field.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public IndianCalendar(int year, int month, int date) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a IndianCalendar with the given date
 * and time set for the default time zone with the default <code>FORMAT</code> locale.
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
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public IndianCalendar(int year, int month, int date, int hour, int minute, int second) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

protected int handleGetExtendedYear() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

protected int handleGetYearLength(int extendedYear) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

protected int handleGetMonthLength(int extendedYear, int month) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

protected void handleComputeFields(int julianDay) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

protected int handleGetLimit(int field, int limitType) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

protected int handleComputeMonthStart(int year, int month, boolean useMonth) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.String getType() { throw new RuntimeException("Stub!"); }

/** 
 * Constant for Agrahayana, the 9th month of the Indian year.
 */

public static final int AGRAHAYANA = 8; // 0x8

/** 
 * Constant for Asadha, the 4th month of the Indian year.
 */

public static final int ASADHA = 3; // 0x3

/** 
 * Constant for Asvina, the 7th month of the Indian year.
 */

public static final int ASVINA = 6; // 0x6

/** 
 * Constant for Bhadra, the 6th month of the Indian year.
 */

public static final int BHADRA = 5; // 0x5

/** 
 * Constant for Chaitra, the 1st month of the Indian year.
 */

public static final int CHAITRA = 0; // 0x0

/**
 * Constant for the Indian Era.  This is the only allowable <code>ERA</code>
 * value for the Indian calendar.
 *
 * @see android.icu.util.Calendar#ERA
 */

public static final int IE = 0; // 0x0

/** 
 * Constant for Jyaistha, the 3rd month of the Indian year.
 */

public static final int JYAISTHA = 2; // 0x2

/** 
 * Constant for Kartika, the 8th month of the Indian year.
 */

public static final int KARTIKA = 7; // 0x7

/** 
 * Constant for Magha, the 11th month of the Indian year.
 */

public static final int MAGHA = 10; // 0xa

/** 
 * Constant for Pausa, the 10th month of the Indian year.
 */

public static final int PAUSA = 9; // 0x9

/** 
 * Constant for Phalguna, the 12th month of the Indian year.
 */

public static final int PHALGUNA = 11; // 0xb

/** 
 * Constant for Sravana, the 5th month of the Indian year.
 */

public static final int SRAVANA = 4; // 0x4

/** 
 * Constant for Vaisakha, the 2nd month of the Indian year.
 */

public static final int VAISAKHA = 1; // 0x1
}

