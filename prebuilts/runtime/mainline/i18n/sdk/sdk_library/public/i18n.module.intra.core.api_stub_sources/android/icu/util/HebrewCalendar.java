/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 1996-2016, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */

package android.icu.util;

import android.icu.util.ULocale.Category;

/**
 * <code>HebrewCalendar</code> is a subclass of <code>Calendar</code>
 * that that implements the traditional Hebrew calendar.
 * This is the civil calendar in Israel and the liturgical calendar
 * of the Jewish faith worldwide.
 * <p>
 * The Hebrew calendar is lunisolar and thus has a number of interesting
 * properties that distinguish it from the Gregorian.  Months start
 * on the day of (an arithmetic approximation of) each new moon.  Since the
 * solar year (approximately 365.24 days) is not an even multiple of
 * the lunar month (approximately 29.53 days) an extra "leap month" is
 * inserted in 7 out of every 19 years.  To make matters even more
 * interesting, the start of a year can be delayed by up to three days
 * in order to prevent certain holidays from falling on the Sabbath and
 * to prevent certain illegal year lengths.  Finally, the lengths of certain
 * months can vary depending on the number of days in the year.
 * <p>
 * The leap month is known as "Adar 1" and is inserted between the
 * months of Shevat and Adar in leap years.  Since the leap month does
 * not come at the end of the year, calculations involving
 * month numbers are particularly complex.  Users of this class should
 * make sure to use the {@link #roll roll} and {@link #add add} methods
 * rather than attempting to perform date arithmetic by manipulating
 * the fields directly.
 * <p>
 * <b>Note:</b> In the traditional Hebrew calendar, days start at sunset.
 * However, in order to keep the time fields in this class
 * synchronized with those of the other calendars and with local clock time,
 * we treat days and months as beginning at midnight,
 * roughly 6 hours after the corresponding sunset.
 * <p>
 * If you are interested in more information on the rules behind the Hebrew
 * calendar, see one of the following references:
 * <ul>
 * <li>"<a href="http://www.amazon.com/exec/obidos/ASIN/0521564743">Calendrical Calculations</a>",
 *      by Nachum Dershowitz &amp; Edward Reingold, Cambridge University Press, 1997, pages 85-91.
 *
 * <li>Hebrew Calendar Science and Myths,
 *      <a href="http://web.archive.org/web/20090423084613/http://www.geocities.com/Athens/1584/">
 *      http://web.archive.org/web/20090423084613/http://www.geocities.com/Athens/1584/</a>
 *
 * <li>The Calendar FAQ,
 *      <a href="http://www.faqs.org/faqs/calendars/faq/">
 *      http://www.faqs.org/faqs/calendars/faq/</a>
 * </ul>
 *
 * <p>
 * This class should not be subclassed.</p>
 * <p>
 * HebrewCalendar usually should be instantiated using
 * {@link android.icu.util.Calendar#getInstance(ULocale)} passing in a <code>ULocale</code>
 * with the tag <code>"@calendar=hebrew"</code>.</p>
 *
 * @see android.icu.util.GregorianCalendar
 * @see android.icu.util.Calendar
 *
 * @author Laura Werner
 * @author Alan Liu
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class HebrewCalendar extends android.icu.util.Calendar {

/**
 * Constructs a default <code>HebrewCalendar</code> using the current time
 * in the default time zone with the default <code>FORMAT</code> locale.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public HebrewCalendar() { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>HebrewCalendar</code> based on the current time
 * in the given time zone with the default <code>FORMAT</code> locale.
 *
 * @param zone The time zone for the new calendar.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public HebrewCalendar(android.icu.util.TimeZone zone) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>HebrewCalendar</code> based on the current time
 * in the default time zone with the given locale.
 *
 * @param aLocale The locale for the new calendar.
 */

public HebrewCalendar(java.util.Locale aLocale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>HebrewCalendar</code> based on the current time
 * in the default time zone with the given locale.
 *
 * @param locale The locale for the new calendar.
 */

public HebrewCalendar(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>HebrewCalendar</code> based on the current time
 * in the given time zone with the given locale.
 *
 * @param zone The time zone for the new calendar.
 *
 * @param aLocale The locale for the new calendar.
 */

public HebrewCalendar(android.icu.util.TimeZone zone, java.util.Locale aLocale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>HebrewCalendar</code> based on the current time
 * in the given time zone with the given locale.
 *
 * @param zone The time zone for the new calendar.
 *
 * @param locale The locale for the new calendar.
 */

public HebrewCalendar(android.icu.util.TimeZone zone, android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>HebrewCalendar</code> with the given date set
 * in the default time zone with the default <code>FORMAT</code> locale.
 *
 * @param year      The value used to set the calendar's {@link #YEAR YEAR} time field.
 *
 * @param month     The value used to set the calendar's {@link #MONTH MONTH} time field.
 *                  The value is 0-based. e.g., 0 for Tishri.
 *
 * @param date      The value used to set the calendar's {@link #DATE DATE} time field.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public HebrewCalendar(int year, int month, int date) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>HebrewCalendar</code> with the given date set
 * in the default time zone with the default <code>FORMAT</code> locale.
 *
 * @param date      The date to which the new calendar is set.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public HebrewCalendar(java.util.Date date) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>HebrewCalendar</code> with the given date
 * and time set for the default time zone with the default <code>FORMAT</code> locale.
 *
 * @param year      The value used to set the calendar's {@link #YEAR YEAR} time field.
 *
 * @param month     The value used to set the calendar's {@link #MONTH MONTH} time field.
 *                  The value is 0-based. e.g., 0 for Tishri.
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

public HebrewCalendar(int year, int month, int date, int hour, int minute, int second) { throw new RuntimeException("Stub!"); }

/**
 * Add a signed amount to a specified field, using this calendar's rules.
 * For example, to add three days to the current date, you can call
 * <code>add(Calendar.DATE, 3)</code>.
 * <p>
 * When adding to certain fields, the values of other fields may conflict and
 * need to be changed.  For example, when adding one to the {@link #MONTH MONTH} field
 * for the date "30 Av 5758", the {@link #DAY_OF_MONTH DAY_OF_MONTH} field
 * must be adjusted so that the result is "29 Elul 5758" rather than the invalid
 * "30 Elul 5758".
 * <p>
 * This method is able to add to
 * all fields except for {@link #ERA ERA}, {@link #DST_OFFSET DST_OFFSET},
 * and {@link #ZONE_OFFSET ZONE_OFFSET}.
 * <p>
 * <b>Note:</b> You should always use {@link #roll roll} and add rather
 * than attempting to perform arithmetic operations directly on the fields
 * of a <tt>HebrewCalendar</tt>.  Since the {@link #MONTH MONTH} field behaves
 * discontinuously in non-leap years, simple arithmetic can give invalid results.
 * <p>
 * @param field     the time field.
 * @param amount    the amount to add to the field.
 *
 * @exception   java.lang.IllegalArgumentException if the field is invalid or refers
 *              to a field that cannot be handled by this method.
 */

public void add(int field, int amount) { throw new RuntimeException("Stub!"); }

/**
 * Rolls (up/down) a specified amount time on the given field.  For
 * example, to roll the current date up by three days, you can call
 * <code>roll(Calendar.DATE, 3)</code>.  If the
 * field is rolled past its maximum allowable value, it will "wrap" back
 * to its minimum and continue rolling.
 * For example, calling <code>roll(Calendar.DATE, 10)</code>
 * on a Hebrew calendar set to "25 Av 5758" will result in the date "5 Av 5758".
 * <p>
 * When rolling certain fields, the values of other fields may conflict and
 * need to be changed.  For example, when rolling the {@link #MONTH MONTH} field
 * upward by one for the date "30 Av 5758", the {@link #DAY_OF_MONTH DAY_OF_MONTH} field
 * must be adjusted so that the result is "29 Elul 5758" rather than the invalid
 * "30 Elul".
 * <p>
 * This method is able to roll
 * all fields except for {@link #ERA ERA}, {@link #DST_OFFSET DST_OFFSET},
 * and {@link #ZONE_OFFSET ZONE_OFFSET}.  Subclasses may, of course, add support for
 * additional fields in their overrides of <code>roll</code>.
 * <p>
 * <b>Note:</b> You should always use roll and {@link #add add} rather
 * than attempting to perform arithmetic operations directly on the fields
 * of a <tt>HebrewCalendar</tt>.  Since the {@link #MONTH MONTH} field behaves
 * discontinuously in non-leap years, simple arithmetic can give invalid results.
 * <p>
 * @param field     the time field.
 * @param amount    the amount by which the field should be rolled.
 *
 * @exception   java.lang.IllegalArgumentException if the field is invalid or refers
 *              to a field that cannot be handled by this method.
 */

public void roll(int field, int amount) { throw new RuntimeException("Stub!"); }

/**
 */

protected int handleGetLimit(int field, int limitType) { throw new RuntimeException("Stub!"); }

/**
 * Returns the length of the given month in the given year
 */

protected int handleGetMonthLength(int extendedYear, int month) { throw new RuntimeException("Stub!"); }

/**
 * Returns the number of days in the given Hebrew year
 */

protected int handleGetYearLength(int eyear) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 * <p>
 * Overrides {@link android.icu.util.Calendar#validateField(int) Calendar#validateField(int)} to provide
 * special handling for month validation for Hebrew calendar.
 * @deprecated This API is ICU internal only.
 * @hide original deprecated declaration
 * @hide draft / provisional / internal are hidden on Android
 */

@Deprecated
protected void validateField(int field) { throw new RuntimeException("Stub!"); }

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
 * Subclasses can refer to the DAY_OF_WEEK and DOW_LOCAL fields,
 * which will be set when this method is called.  Subclasses can
 * also call the getGregorianXxx() methods to obtain Gregorian
 * calendar equivalents for the given Julian day.
 *
 * <p>In addition, subclasses should compute any subclass-specific
 * fields, that is, fields from BASE_FIELD_COUNT to
 * getFieldCount() - 1.
 */

protected void handleComputeFields(int julianDay) { throw new RuntimeException("Stub!"); }

/**
 */

protected int handleGetExtendedYear() { throw new RuntimeException("Stub!"); }

/**
 * Return JD of start of given month/year.
 */

protected int handleComputeMonthStart(int eyear, int month, boolean useMonth) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.String getType() { throw new RuntimeException("Stub!"); }

/**
 * Constant for the Adar, the 7th month of the Hebrew year.
 */

public static final int ADAR = 6; // 0x6

/**
 * Constant for Adar I, the 6th month of the Hebrew year
 * (present in leap years only). In non-leap years, the calendar
 * jumps from Shevat (5th month) to Adar (7th month).
 */

public static final int ADAR_1 = 5; // 0x5

/**
 * Constant for Av, the 12th month of the Hebrew year.
 */

public static final int AV = 11; // 0xb

/**
 * Constant for Elul, the 13th month of the Hebrew year.
 */

public static final int ELUL = 12; // 0xc

/**
 * Constant for Heshvan, the 2nd month of the Hebrew year.
 */

public static final int HESHVAN = 1; // 0x1

/**
 * Constant for Iyar, the 9th month of the Hebrew year.
 */

public static final int IYAR = 8; // 0x8

/**
 * Constant for Kislev, the 3rd month of the Hebrew year.
 */

public static final int KISLEV = 2; // 0x2

/**
 * Constant for Nisan, the 8th month of the Hebrew year.
 */

public static final int NISAN = 7; // 0x7

/**
 * Constant for Shevat, the 5th month of the Hebrew year.
 */

public static final int SHEVAT = 4; // 0x4

/**
 * Constant for Sivan, the 10th month of the Hebrew year.
 */

public static final int SIVAN = 9; // 0x9

/**
 * Constant for Tammuz, the 11th month of the Hebrew year.
 */

public static final int TAMUZ = 10; // 0xa

/**
 * Constant for Tevet, the 4th month of the Hebrew year.
 */

public static final int TEVET = 3; // 0x3

/**
 * Constant for Tishri, the 1st month of the Hebrew year.
 */

public static final int TISHRI = 0; // 0x0
}

