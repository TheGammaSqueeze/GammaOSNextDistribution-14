/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 1996-2014, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */

package android.icu.util;


/**
 * <code>JapaneseCalendar</code> is a subclass of <code>GregorianCalendar</code>
 * that numbers years and eras based on the reigns of the Japanese emperors.
 * The Japanese calendar is identical to the Gregorian calendar in all respects
 * except for the year and era.  The ascension of each  emperor to the throne
 * begins a new era, and the years of that era are numbered starting with the
 * year of ascension as year 1.
 * <p>
 * Note that in the year of an imperial ascension, there are two possible sets
 * of year and era values: that for the old era and for the new.  For example, a
 * new era began on January 7, 1989 AD.  Strictly speaking, the first six days
 * of that year were in the Showa era, e.g. "January 6, 64 Showa", while the rest
 * of the year was in the Heisei era, e.g. "January 7, 1 Heisei".  This class
 * handles this distinction correctly when computing dates.  However, in lenient
 * mode either form of date is acceptable as input.
 * <p>
 * In modern times, eras have started on January 8, 1868 AD, Gregorian (Meiji),
 * July 30, 1912 (Taisho), December 25, 1926 (Showa), and January 7, 1989 (Heisei).  Constants
 * for these eras, suitable for use in the <code>ERA</code> field, are provided
 * in this class.  Note that the <em>number</em> used for each era is more or
 * less arbitrary.  Currently, the era starting in 645 AD is era #0; however this
 * may change in the future.  Use the predefined constants rather than using actual,
 * absolute numbers.
 * <p>
 * Since ICU4J 63, start date of each era is imported from CLDR. CLDR era data
 * may contain tentative era in near future with placeholder names. By default,
 * such era data is not enabled. ICU4J users who want to test the behavior of
 * the future era can enable this by one of following settings (in the priority
 * order):
 * <ol>
 * <li>Java system property <code>ICU_ENABLE_TENTATIVE_ERA=true</code>.</li>
 * <li>Environment variable <code>ICU_ENABLE_TENTATIVE_ERA=true</code>.</li>
 * <li>Java system property <code>jdk.calendar.japanese.supplemental.era=xxx</code>.
 *     (Note: This configuration is used for specifying a new era's start date and
 *     names in OpenJDK. ICU4J implementation enables the CLDR tentative era when
 *     this property is defined, but it does not use the start date and names specified
 *     by the property value.)</li>
 * </ol>
 * <p>
 * This class should not be subclassed.</p>
 * <p>
 * JapaneseCalendar usually should be instantiated using
 * {@link android.icu.util.Calendar#getInstance(ULocale)} passing in a <code>ULocale</code>
 * with the tag <code>"@calendar=japanese"</code>.</p>
 *
 * @see android.icu.util.GregorianCalendar
 * @see android.icu.util.Calendar
 *
 * @author Laura Werner
 * @author Alan Liu
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class JapaneseCalendar extends android.icu.util.GregorianCalendar {

/**
 * Constructs a default <code>JapaneseCalendar</code> using the current time
 * in the default time zone with the default locale.
 */

public JapaneseCalendar() { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>JapaneseCalendar</code> based on the current time
 * in the given time zone with the default locale.
 * @param zone the given time zone.
 */

public JapaneseCalendar(android.icu.util.TimeZone zone) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>JapaneseCalendar</code> based on the current time
 * in the default time zone with the given locale.
 * @param aLocale the given locale.
 */

public JapaneseCalendar(java.util.Locale aLocale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>JapaneseCalendar</code> based on the current time
 * in the default time zone with the given locale.
 * @param locale the given ulocale.
 */

public JapaneseCalendar(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>JapaneseCalendar</code> based on the current time
 * in the given time zone with the given locale.
 *
 * @param zone the given time zone.
 *
 * @param aLocale the given locale.
 */

public JapaneseCalendar(android.icu.util.TimeZone zone, java.util.Locale aLocale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>JapaneseCalendar</code> based on the current time
 * in the given time zone with the given locale.
 *
 * @param zone the given time zone.
 *
 * @param locale the given ulocale.
 */

public JapaneseCalendar(android.icu.util.TimeZone zone, android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>JapaneseCalendar</code> with the given date set
 * in the default time zone with the default locale.
 *
 * @param date      The date to which the new calendar is set.
 */

public JapaneseCalendar(java.util.Date date) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>JapaneseCalendar</code> with the given date set
 * in the default time zone with the default locale.
 *
 * @param era       The imperial era used to set the calendar's {@link #ERA ERA} field.
 *                  Eras are numbered starting with the Tenki era, which
 *                  began in 1053 AD Gregorian, as era zero.  Recent
 *                  eras can be specified using the constants
 *                  {@link #MEIJI} (which started in 1868 AD),
 *                  {@link #TAISHO} (1912 AD),
 *                  {@link #SHOWA} (1926 AD), and
 *                  {@link #HEISEI} (1989 AD).
 *
 * @param year      The value used to set the calendar's {@link #YEAR YEAR} field,
 *                  in terms of the era.
 *
 * @param month     The value used to set the calendar's {@link #MONTH MONTH} field.
 *                  The value is 0-based. e.g., 0 for January.
 *
 * @param date      The value used to set the calendar's DATE field.
 */

public JapaneseCalendar(int era, int year, int month, int date) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>JapaneseCalendar</code> with the given date set
 * in the default time zone with the default locale.
 *
 * @param year      The value used to set the calendar's {@link #YEAR YEAR} field,
 *                  in the era Heisei, the most current at the time this
 *                  class was last updated.
 *
 * @param month     The value used to set the calendar's {@link #MONTH MONTH} field.
 *                  The value is 0-based. e.g., 0 for January.
 *
 * @param date      The value used to set the calendar's {@link #DATE DATE} field.
 */

public JapaneseCalendar(int year, int month, int date) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>JapaneseCalendar</code> with the given date
 * and time set for the default time zone with the default locale.
 *
 * @param year      The value used to set the calendar's {@link #YEAR YEAR} time field,
 *                  in the era Heisei, the most current at the time of this
 *                  writing.
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

public JapaneseCalendar(int year, int month, int date, int hour, int minute, int second) { throw new RuntimeException("Stub!"); }

/**
 */

protected int handleGetExtendedYear() { throw new RuntimeException("Stub!"); }

/**
 */

protected void handleComputeFields(int julianDay) { throw new RuntimeException("Stub!"); }

/**
 * Override GregorianCalendar.  We should really handle YEAR_WOY and
 * EXTENDED_YEAR here too to implement the 1..5000000 range, but it's
 * not critical.
 */

protected int handleGetLimit(int field, int limitType) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.String getType() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public int getActualMaximum(int field) { throw new RuntimeException("Stub!"); }

/**
 * Constant for the era starting on Jan. 7, 1989 AD.
 */

public static final int HEISEI;
static { HEISEI = 0; }

/**
 * Constant for the era starting on Sept. 8, 1868 AD.
 */

public static final int MEIJI;
static { MEIJI = 0; }

/**
 * Constant for the era starting on May 1, 2019 AD.
 */

public static final int REIWA;
static { REIWA = 0; }

/**
 * Constant for the era starting on Dec. 25, 1926 AD.
 */

public static final int SHOWA;
static { SHOWA = 0; }

/**
 * Constant for the era starting on July 30, 1912 AD.
 */

public static final int TAISHO;
static { TAISHO = 0; }
}

