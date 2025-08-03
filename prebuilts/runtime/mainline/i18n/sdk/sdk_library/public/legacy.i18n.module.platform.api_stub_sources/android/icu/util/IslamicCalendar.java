/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 1996-2016, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */

package android.icu.util;

import android.icu.util.ULocale.Category;

/**
 * <code>IslamicCalendar</code> is a subclass of <code>Calendar</code>
 * that that implements the Islamic civil and religious calendars.  It
 * is used as the civil calendar in most of the Arab world and the
 * liturgical calendar of the Islamic faith worldwide.  This calendar
 * is also known as the "Hijri" calendar, since it starts at the time
 * of Mohammed's emigration (or "hijra") to Medinah on Thursday,
 * July 15, 622 AD (Julian).
 * <p>
 * The Islamic calendar is strictly lunar, and thus an Islamic year of twelve
 * lunar months does not correspond to the solar year used by most other
 * calendar systems, including the Gregorian.  An Islamic year is, on average,
 * about 354 days long, so each successive Islamic year starts about 11 days
 * earlier in the corresponding Gregorian year.
 * <p>
 * Each month of the calendar starts when the new moon's crescent is visible
 * at sunset.  However, in order to keep the time fields in this class
 * synchronized with those of the other calendars and with local clock time,
 * we treat days and months as beginning at midnight,
 * roughly 6 hours after the corresponding sunset.
 * <p>
 * There are three main variants of the Islamic calendar in existence.  The first
 * is the <em>civil</em> calendar, which uses a fixed cycle of alternating 29-
 * and 30-day months, with a leap day added to the last month of 11 out of
 * every 30 years.  This calendar is easily calculated and thus predictable in
 * advance, so it is used as the civil calendar in a number of Arab countries.
 * This is the default behavior of a newly-created <code>IslamicCalendar</code>
 * object.
 * <p>
 * The Islamic <em>religious</em> calendar and Saudi Arabia's <em>Umm al-Qura</em>
 * calendar, however, are based on the <em>observation</em> of the crescent moon.
 * It is thus affected by the position at which the
 * observations are made, seasonal variations in the time of sunset, the
 * eccentricities of the moon's orbit, and even the weather at the observation
 * site.  This makes it impossible to calculate in advance, and it causes the
 * start of a month in the religious calendar to differ from the civil calendar
 * by up to three days.
 * <p>
 * Using astronomical calculations for the position of the sun and moon, the
 * moon's illumination, and other factors, it is possible to determine the start
 * of a lunar month with a fairly high degree of certainty.  However, these
 * calculations are extremely complicated and thus slow, so most algorithms,
 * including the one used here, are only approximations of the true astronomical
 * calculations.  At present, the approximations used in this class are fairly
 * simplistic; they will be improved in later versions of the code.
 * <p>
 * Like the Islamic religious calendar, <em>Umm al-Qura</em> is also based
 * on the sighting method of the crescent moon but is standardized by Saudi Arabia.
 * <p>
 * The fixed-cycle <em>civil</em> calendar is used.
 * <p>
 * This class should not be subclassed.</p>
 * <p>
 * IslamicCalendar usually should be instantiated using
 * {@link android.icu.util.Calendar#getInstance(ULocale)} passing in a <code>ULocale</code>
 * with the tag <code>"@calendar=islamic"</code> or <code>"@calendar=islamic-civil"</code>
 * or <code>"@calendar=islamic-umalqura"</code>.</p>
 *
 * @see android.icu.util.GregorianCalendar
 * @see android.icu.util.Calendar
 *
 * @author Laura Werner
 * @author Alan Liu
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class IslamicCalendar extends android.icu.util.Calendar {

/**
 * Constructs a default <code>IslamicCalendar</code> using the current time
 * in the default time zone with the default <code>FORMAT</code> locale.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public IslamicCalendar() { throw new RuntimeException("Stub!"); }

/**
 * Constructs an <code>IslamicCalendar</code> based on the current time
 * in the given time zone with the default <code>FORMAT</code> locale.
 * @param zone the given time zone.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public IslamicCalendar(android.icu.util.TimeZone zone) { throw new RuntimeException("Stub!"); }

/**
 * Constructs an <code>IslamicCalendar</code> based on the current time
 * in the default time zone with the given locale.
 *
 * @param aLocale the given locale.
 */

public IslamicCalendar(java.util.Locale aLocale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs an <code>IslamicCalendar</code> based on the current time
 * in the default time zone with the given locale.
 *
 * @param locale the given ulocale.
 */

public IslamicCalendar(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs an <code>IslamicCalendar</code> based on the current time
 * in the given time zone with the given locale.
 *
 * @param zone the given time zone.
 * @param aLocale the given locale.
 */

public IslamicCalendar(android.icu.util.TimeZone zone, java.util.Locale aLocale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs an <code>IslamicCalendar</code> based on the current time
 * in the given time zone with the given locale.
 *
 * @param zone the given time zone.
 * @param locale the given ulocale.
 */

public IslamicCalendar(android.icu.util.TimeZone zone, android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs an <code>IslamicCalendar</code> with the given date set
 * in the default time zone with the default <code>FORMAT</code> locale.
 *
 * @param date      The date to which the new calendar is set.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public IslamicCalendar(java.util.Date date) { throw new RuntimeException("Stub!"); }

/**
 * Constructs an <code>IslamicCalendar</code> with the given date set
 * in the default time zone with the default <code>FORMAT</code> locale.
 *
 * @param year the value used to set the {@link #YEAR YEAR} time field in the calendar.
 * @param month the value used to set the {@link #MONTH MONTH} time field in the calendar.
 *              Note that the month value is 0-based. e.g., 0 for Muharram.
 * @param date the value used to set the {@link #DATE DATE} time field in the calendar.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public IslamicCalendar(int year, int month, int date) { throw new RuntimeException("Stub!"); }

/**
 * Constructs an <code>IslamicCalendar</code> with the given date
 * and time set for the default time zone with the default <code>FORMAT</code> locale.
 *
 * @param year  the value used to set the {@link #YEAR YEAR} time field in the calendar.
 * @param month the value used to set the {@link #MONTH MONTH} time field in the calendar.
 *              Note that the month value is 0-based. e.g., 0 for Muharram.
 * @param date  the value used to set the {@link #DATE DATE} time field in the calendar.
 * @param hour  the value used to set the {@link #HOUR_OF_DAY HOUR_OF_DAY} time field
 *              in the calendar.
 * @param minute the value used to set the {@link #MINUTE MINUTE} time field
 *              in the calendar.
 * @param second the value used to set the {@link #SECOND SECOND} time field
 *              in the calendar.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public IslamicCalendar(int year, int month, int date, int hour, int minute, int second) { throw new RuntimeException("Stub!"); }

/**
 */

protected int handleGetLimit(int field, int limitType) { throw new RuntimeException("Stub!"); }

/**
 * Return the length (in days) of the given month.
 *
 * @param extendedYear  The hijri year
 * @param month The hijri month, 0-based
 */

protected int handleGetMonthLength(int extendedYear, int month) { throw new RuntimeException("Stub!"); }

/**
 * Return the number of days in the given Islamic year
 */

protected int handleGetYearLength(int extendedYear) { throw new RuntimeException("Stub!"); }

/**
 */

protected int handleComputeMonthStart(int eyear, int month, boolean useMonth) { throw new RuntimeException("Stub!"); }

/**
 */

protected int handleGetExtendedYear() { throw new RuntimeException("Stub!"); }

/**
 * Override Calendar to compute several fields specific to the Islamic
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
 * method is called. The getGregorianXxx() methods return Gregorian
 * calendar equivalents for the given Julian day.
 */

protected void handleComputeFields(int julianDay) { throw new RuntimeException("Stub!"); }

/**
 * sets the calculation type for this calendar.
 */

public void setCalculationType(android.icu.util.IslamicCalendar.CalculationType type) { throw new RuntimeException("Stub!"); }

/**
 * gets the calculation type for this calendar.
 */

public android.icu.util.IslamicCalendar.CalculationType getCalculationType() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.String getType() { throw new RuntimeException("Stub!"); }

/**
 * Constant for Dhu al-Hijjah, the 12th month of the Islamic year.
 */

public static final int DHU_AL_HIJJAH = 11; // 0xb

/**
 * Constant for Dhu al-Qi'dah, the 11th month of the Islamic year.
 */

public static final int DHU_AL_QIDAH = 10; // 0xa

/**
 * Constant for Jumada al-awwal or (Jumada I), the 5th month of the Islamic year.
 */

public static final int JUMADA_1 = 4; // 0x4

/**
 * Constant for Jumada al-thani or (Jumada II), the 6th month of the Islamic year.
 */

public static final int JUMADA_2 = 5; // 0x5

/**
 * Constant for Muharram, the 1st month of the Islamic year.
 */

public static final int MUHARRAM = 0; // 0x0

/**
 * Constant for Rabi' al-awwal (or Rabi' I), the 3rd month of the Islamic year.
 */

public static final int RABI_1 = 2; // 0x2

/**
 * Constant for Rabi' al-thani or (Rabi' II), the 4th month of the Islamic year.
 */

public static final int RABI_2 = 3; // 0x3

/**
 * Constant for Rajab, the 7th month of the Islamic year.
 */

public static final int RAJAB = 6; // 0x6

/**
 * Constant for Ramadan, the 9th month of the Islamic year.
 */

public static final int RAMADAN = 8; // 0x8

/**
 * Constant for Safar, the 2nd month of the Islamic year.
 */

public static final int SAFAR = 1; // 0x1

/**
 * Constant for Sha'ban, the 8th month of the Islamic year.
 */

public static final int SHABAN = 7; // 0x7

/**
 * Constant for Shawwal, the 10th month of the Islamic year.
 */

public static final int SHAWWAL = 9; // 0x9
/**
 *  enumeration of available calendar calculation types
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public enum CalculationType {
/**
 * Religious calendar (atronomical simulation)
 */

ISLAMIC,
/**
 * Tabular (intercalary years [2,5,7,10,13,16,18,21,24,26,29]) algorithm
 * with civil (Friday) epoch.
 */

ISLAMIC_CIVIL,
/**
 * Umm al-Qura calendar
 */

ISLAMIC_UMALQURA,
/**
 * Tabular (intercalary years [2,5,7,10,13,16,18,21,24,26,29]) algorithm
 * with astronomical (Thursday) epoch.
 */

ISLAMIC_TBLA;
}

}

