/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 1996-2016, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */


package android.icu.text;

import java.util.MissingResourceException;
import java.util.Locale;
import android.icu.util.Calendar;
import android.icu.util.ULocale;
import android.icu.util.ULocale.Category;

/**
 * <strong>[icu enhancement]</strong> ICU's replacement for {@link java.text.DateFormatSymbols}.&nbsp;Methods, fields, and other functionality specific to ICU are labeled '<strong>[icu]</strong>'.
 *
 * <p><code>DateFormatSymbols</code> is a public class for encapsulating
 * localizable date-time formatting data, such as the names of the
 * months, the names of the days of the week, and the time zone data.
 * <code>DateFormat</code> and <code>SimpleDateFormat</code> both use
 * <code>DateFormatSymbols</code> to encapsulate this information.
 *
 * <p>Typically you shouldn't use <code>DateFormatSymbols</code> directly.
 * Rather, you are encouraged to create a date-time formatter with the
 * <code>DateFormat</code> class's factory methods: <code>getTimeInstance</code>,
 * <code>getDateInstance</code>, or <code>getDateTimeInstance</code>.
 * These methods automatically create a <code>DateFormatSymbols</code> for
 * the formatter so that you don't have to. After the
 * formatter is created, you may modify its format pattern using the
 * <code>setPattern</code> method. For more information about
 * creating formatters using <code>DateFormat</code>'s factory methods,
 * see {@link android.icu.text.DateFormat DateFormat}.
 *
 * <p>If you decide to create a date-time formatter with a specific
 * format pattern for a specific locale, you can do so with:
 * <blockquote>
 * <pre>
 * new SimpleDateFormat(aPattern, new DateFormatSymbols(aLocale)).
 * </pre>
 * </blockquote>
 *
 * <p><code>DateFormatSymbols</code> objects are clonable. When you obtain
 * a <code>DateFormatSymbols</code> object, feel free to modify the
 * date-time formatting data. For instance, you can replace the localized
 * date-time format pattern characters with the ones that you feel easy
 * to remember. Or you can change the representative cities
 * to your favorite ones.
 *
 * <p>New <code>DateFormatSymbols</code> subclasses may be added to support
 * <code>SimpleDateFormat</code> for date-time formatting for additional locales.
 *
 * @see          android.icu.text.DateFormat
 * @see          android.icu.text.SimpleDateFormat
 * @author       Chen-Lieh Huang
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class DateFormatSymbols implements java.io.Serializable, java.lang.Cloneable {

/**
 * Constructs a DateFormatSymbols object by loading format data from
 * resources for the default <code>FORMAT</code> locale.
 *
 * @throws java.util.MissingResourceException if the resources for the default locale
 *          cannot be found or cannot be loaded.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public DateFormatSymbols() { throw new RuntimeException("Stub!"); }

/**
 * Constructs a DateFormatSymbols object by loading format data from
 * resources for the given locale.
 *
 * @throws java.util.MissingResourceException if the resources for the specified
 *          locale cannot be found or cannot be loaded.
 */

public DateFormatSymbols(java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Constructs a DateFormatSymbols object by loading format data from
 * resources for the given ulocale.
 *
 * @throws java.util.MissingResourceException if the resources for the specified
 *          locale cannot be found or cannot be loaded.
 */

public DateFormatSymbols(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns the {@link android.icu.text.DateFormatSymbols DateFormatSymbols} object that should be used to format a
 * calendar system's dates in the given locale.
 *
 * @param cal       The calendar system whose date format symbols are desired.
 * @param locale    The locale whose symbols are desired.
 *
 * @see android.icu.text.DateFormatSymbols#DateFormatSymbols(java.util.Locale)
 */

public DateFormatSymbols(android.icu.util.Calendar cal, java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns the {@link android.icu.text.DateFormatSymbols DateFormatSymbols} object that should be used to format a
 * calendar system's dates in the given locale.
 * @param cal       The calendar system whose date format symbols are desired.
 * @param locale    The ulocale whose symbols are desired.
 *
 * @see android.icu.text.DateFormatSymbols#DateFormatSymbols(java.util.Locale)
 */

public DateFormatSymbols(android.icu.util.Calendar cal, android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Variant of DateFormatSymbols(Calendar, Locale) that takes the Calendar class
 * instead of a Calendar instance.
 * @see #DateFormatSymbols(Calendar, Locale)
 */

public DateFormatSymbols(java.lang.Class<? extends android.icu.util.Calendar> calendarClass, java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Variant of DateFormatSymbols(Calendar, ULocale) that takes the Calendar class
 * instead of a Calendar instance.
 * @see #DateFormatSymbols(Calendar, Locale)
 */

public DateFormatSymbols(java.lang.Class<? extends android.icu.util.Calendar> calendarClass, android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Fetches a custom calendar's DateFormatSymbols out of the given resource
 * bundle.  Symbols that are not overridden are inherited from the
 * default DateFormatSymbols for the locale.
 * @see android.icu.text.DateFormatSymbols#DateFormatSymbols(java.util.Locale)
 */

public DateFormatSymbols(java.util.ResourceBundle bundle, java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Fetches a custom calendar's DateFormatSymbols out of the given resource
 * bundle.  Symbols that are not overridden are inherited from the
 * default DateFormatSymbols for the locale.
 * @see android.icu.text.DateFormatSymbols#DateFormatSymbols(java.util.Locale)
 */

public DateFormatSymbols(java.util.ResourceBundle bundle, android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns a DateFormatSymbols instance for the default locale.
 *
 * <strong>[icu] Note:</strong> Unlike <code>java.text.DateFormatSymbols#getInstance</code>,
 * this method simply returns <code>new android.icu.text.DateFormatSymbols()</code>.
 * ICU does not support <code>DateFormatSymbolsProvider</code> introduced in Java 6
 * or its equivalent implementation for now.
 *
 * @return A DateFormatSymbols instance.
 */

public static android.icu.text.DateFormatSymbols getInstance() { throw new RuntimeException("Stub!"); }

/**
 * Returns a DateFormatSymbols instance for the given locale.
 *
 * <strong>[icu] Note:</strong> Unlike <code>java.text.DateFormatSymbols#getInstance</code>,
 * this method simply returns <code>new android.icu.text.DateFormatSymbols(locale)</code>.
 * ICU does not support <code>DateFormatSymbolsProvider</code> introduced in Java 6
 * or its equivalent implementation for now.
 *
 * @param locale the locale.
 * @return A DateFormatSymbols instance.
 */

public static android.icu.text.DateFormatSymbols getInstance(java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a DateFormatSymbols instance for the given locale.
 *
 * <strong>[icu] Note:</strong> Unlike <code>java.text.DateFormatSymbols#getInstance</code>,
 * this method simply returns <code>new android.icu.text.DateFormatSymbols(locale)</code>.
 * ICU does not support <code>DateFormatSymbolsProvider</code> introduced in Java 6
 * or its equivalent implementation for now.
 *
 * @param locale the locale.
 * @return A DateFormatSymbols instance.
 */

public static android.icu.text.DateFormatSymbols getInstance(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns an array of all locales for which the <code>getInstance</code> methods of
 * this class can return localized instances.
 *
 * <strong>[icu] Note:</strong> Unlike <code>java.text.DateFormatSymbols#getAvailableLocales</code>,
 * this method simply returns the array of <code>Locale</code>s available in this
 * class.  ICU does not support <code>DateFormatSymbolsProvider</code> introduced in
 * Java 6 or its equivalent implementation for now.
 *
 * @return An array of <code>Locale</code>s for which localized
 * <code>DateFormatSymbols</code> instances are available.
 */

public static java.util.Locale[] getAvailableLocales() { throw new RuntimeException("Stub!"); }

/**
 * Returns abbreviated era strings. For example: "AD" and "BC".
 * @return the era strings.
 */

public java.lang.String[] getEras() { throw new RuntimeException("Stub!"); }

/**
 * Sets abbreviated era strings. For example: "AD" and "BC".
 * @param newEras the new era strings.
 */

public void setEras(java.lang.String[] newEras) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns full era name strings. For example: "Anno Domini" and "Before Christ".
 * @return the era strings.
 */

public java.lang.String[] getEraNames() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Sets full era name strings. For example: "Anno Domini" and "Before Christ".
 * @param newEraNames the new era strings.
 */

public void setEraNames(java.lang.String[] newEraNames) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns narrow era name strings. For example: "A" and "B".
 * @return the narrow era strings.
 */

public java.lang.String[] getNarrowEras() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Sets narrow era name strings. For example: "A" and "B".
 * @param newNarrowEras the new narrow era strings.
 */

public void setNarrowEras(java.lang.String[] newNarrowEras) { throw new RuntimeException("Stub!"); }

/**
 * Returns month strings. For example: "January", "February", etc.
 * @return the month strings.
 */

public java.lang.String[] getMonths() { throw new RuntimeException("Stub!"); }

/**
 * Returns month strings. For example: "January", "February", etc.
 * @param context    The month context, FORMAT or STANDALONE.
 * @param width      The width or the returned month string,
 *                   either WIDE, ABBREVIATED, or NARROW.
 * @return the month strings.
 */

public java.lang.String[] getMonths(int context, int width) { throw new RuntimeException("Stub!"); }

/**
 * Sets month strings. For example: "January", "February", etc.
 * @param newMonths the new month strings.
 */

public void setMonths(java.lang.String[] newMonths) { throw new RuntimeException("Stub!"); }

/**
 * Sets month strings. For example: "January", "February", etc.
 * @param newMonths the new month strings.
 * @param context    The formatting context, FORMAT or STANDALONE.
 * @param width      The width of the month string,
 *                   either WIDE, ABBREVIATED, or NARROW.
 */

public void setMonths(java.lang.String[] newMonths, int context, int width) { throw new RuntimeException("Stub!"); }

/**
 * Returns short month strings. For example: "Jan", "Feb", etc.
 * @return the short month strings.
 */

public java.lang.String[] getShortMonths() { throw new RuntimeException("Stub!"); }

/**
 * Sets short month strings. For example: "Jan", "Feb", etc.
 * @param newShortMonths the new short month strings.
 */

public void setShortMonths(java.lang.String[] newShortMonths) { throw new RuntimeException("Stub!"); }

/**
 * Returns wide weekday strings. For example: "Sunday", "Monday", etc.
 * @return the weekday strings. Use <code>Calendar.SUNDAY</code>,
 * <code>Calendar.MONDAY</code>, etc. to index the result array.
 */

public java.lang.String[] getWeekdays() { throw new RuntimeException("Stub!"); }

/**
 * Returns weekday strings. For example: "Sunday", "Monday", etc.
 * @return the weekday strings. Use <code>Calendar.SUNDAY</code>,
 * <code>Calendar.MONDAY</code>, etc. to index the result array.
 * @param context    Formatting context, either FORMAT or STANDALONE.
 * @param width      Width of strings to be returned, either
 *                   WIDE, ABBREVIATED, SHORT, or NARROW
 */

public java.lang.String[] getWeekdays(int context, int width) { throw new RuntimeException("Stub!"); }

/**
 * Sets weekday strings. For example: "Sunday", "Monday", etc.
 * @param newWeekdays The new weekday strings.
 * @param context     The formatting context, FORMAT or STANDALONE.
 * @param width       The width of the strings,
 *                    either WIDE, ABBREVIATED, SHORT, or NARROW.
 */

public void setWeekdays(java.lang.String[] newWeekdays, int context, int width) { throw new RuntimeException("Stub!"); }

/**
 * Sets wide weekday strings. For example: "Sunday", "Monday", etc.
 * @param newWeekdays the new weekday strings. The array should
 * be indexed by <code>Calendar.SUNDAY</code>,
 * <code>Calendar.MONDAY</code>, etc.
 */

public void setWeekdays(java.lang.String[] newWeekdays) { throw new RuntimeException("Stub!"); }

/**
 * Returns abbreviated weekday strings; for example: "Sun", "Mon", etc.
 * (Note: the method name is misleading; it does not get the CLDR-style
 * "short" weekday strings, e.g. "Su", "Mo", etc.)
 * @return the abbreviated weekday strings. Use <code>Calendar.SUNDAY</code>,
 * <code>Calendar.MONDAY</code>, etc. to index the result array.
 */

public java.lang.String[] getShortWeekdays() { throw new RuntimeException("Stub!"); }

/**
 * Sets abbreviated weekday strings; for example: "Sun", "Mon", etc.
 * (Note: the method name is misleading; it does not set the CLDR-style
 * "short" weekday strings, e.g. "Su", "Mo", etc.)
 * @param newAbbrevWeekdays the new abbreviated weekday strings. The array should
 * be indexed by <code>Calendar.SUNDAY</code>,
 * <code>Calendar.MONDAY</code>, etc.
 */

public void setShortWeekdays(java.lang.String[] newAbbrevWeekdays) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns quarter strings. For example: "1st Quarter", "2nd Quarter", etc.
 * @param context    The quarter context, FORMAT or STANDALONE.
 * @param width      The width or the returned quarter string,
 *                   either WIDE or ABBREVIATED. There are no NARROW quarters.
 * @return the quarter strings.
 */

public java.lang.String[] getQuarters(int context, int width) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Sets quarter strings. For example: "1st Quarter", "2nd Quarter", etc.
 * @param newQuarters the new quarter strings.
 * @param context    The formatting context, FORMAT or STANDALONE.
 * @param width      The width of the quarter string,
 *                   either WIDE or ABBREVIATED. There are no NARROW quarters.
 */

public void setQuarters(java.lang.String[] newQuarters, int context, int width) { throw new RuntimeException("Stub!"); }

/**
 * Returns cyclic year name strings if the calendar has them,
 * for example: "jia-zi", "yi-chou", etc.
 * @param context   The usage context: FORMAT, STANDALONE.
 * @param width     The requested name width: WIDE, ABBREVIATED, SHORT, NARROW.
 * @return          The year name strings, or null if they are not
 *                  available for this calendar.
 */

public java.lang.String[] getYearNames(int context, int width) { throw new RuntimeException("Stub!"); }

/**
 * Sets cyclic year name strings, for example: "jia-zi", "yi-chou", etc.
 * @param yearNames The new cyclic year name strings.
 * @param context   The usage context: FORMAT, STANDALONE (currently only FORMAT is supported).
 * @param width     The name width: WIDE, ABBREVIATED, NARROW (currently only ABBREVIATED is supported).
 */

public void setYearNames(java.lang.String[] yearNames, int context, int width) { throw new RuntimeException("Stub!"); }

/**
 * Returns calendar zodiac name strings if the calendar has them,
 * for example: "Rat", "Ox", "Tiger", etc.
 * @param context   The usage context: FORMAT, STANDALONE.
 * @param width     The requested name width: WIDE, ABBREVIATED, SHORT, NARROW.
 * @return          The zodiac name strings, or null if they are not
 *                  available for this calendar.
 */

public java.lang.String[] getZodiacNames(int context, int width) { throw new RuntimeException("Stub!"); }

/**
 * Sets calendar zodiac name strings, for example: "Rat", "Ox", "Tiger", etc.
 * @param zodiacNames   The new zodiac name strings.
 * @param context   The usage context: FORMAT, STANDALONE (currently only FORMAT is supported).
 * @param width     The name width: WIDE, ABBREVIATED, NARROW (currently only ABBREVIATED is supported).
 */

public void setZodiacNames(java.lang.String[] zodiacNames, int context, int width) { throw new RuntimeException("Stub!"); }

/**
 * Returns am/pm strings. For example: "AM" and "PM".
 * @return the weekday strings.
 */

public java.lang.String[] getAmPmStrings() { throw new RuntimeException("Stub!"); }

/**
 * Sets am/pm strings. For example: "AM" and "PM".
 * @param newAmpms the new ampm strings.
 */

public void setAmPmStrings(java.lang.String[] newAmpms) { throw new RuntimeException("Stub!"); }

/**
 * Returns narrow am/pm strings. For example: "a" and "p".
 * @return narrow ampm strings
 * @hide draft / provisional / internal are hidden on Android
 */

public java.lang.String[] getAmpmNarrowStrings() { throw new RuntimeException("Stub!"); }

/**
 * Returns time zone strings.
 * <p>
 * The array returned by this API is a two dimensional String array and
 * each row contains at least following strings:
 * <ul>
 * <li>ZoneStrings[n][0] - System time zone ID
 * <li>ZoneStrings[n][1] - Long standard time display name
 * <li>ZoneStrings[n][2] - Short standard time display name
 * <li>ZoneStrings[n][3] - Long daylight saving time display name
 * <li>ZoneStrings[n][4] - Short daylight saving time display name
 * </ul>
 * When a localized display name is not available, the corresponding
 * array element will be <code>null</code>.
 * <p>
 * <b>Note</b>: ICU implements the time zone display name formatting algorithm
 * specified by <a href="http://www.unicode.org/reports/tr35/">UTS#35 Unicode
 * Locale Data Markup Language(LDML)</a>. The algorithm supports historic
 * display name changes and various different types of names not available in
 * {@link java.text.DateFormatSymbols#getZoneStrings()}. For accessing the full
 * set of time zone string data used by ICU implementation, you should use
 * {@link android.icu.text.TimeZoneNames TimeZoneNames} APIs instead.
 *
 * @return the time zone strings.
 */

public java.lang.String[][] getZoneStrings() { throw new RuntimeException("Stub!"); }

/**
 * Sets time zone strings.
 * <p>
 * <b>Note</b>: {@link android.icu.text.SimpleDateFormat SimpleDateFormat} no longer uses the
 * zone strings stored in a <code>DateFormatSymbols</code>.
 * Therefore, the time zone strings set by this method have
 * no effects in an instance of <code>SimpleDateFormat</code>
 * for formatting time zones. If you want to customize time
 * zone display names formatted by <code>SimpleDateFormat</code>,
 * you should customize {@link android.icu.text.TimeZoneFormat TimeZoneFormat} and set the
 * instance by {@link android.icu.text.SimpleDateFormat#setTimeZoneFormat(android.icu.text.TimeZoneFormat) SimpleDateFormat#setTimeZoneFormat(TimeZoneFormat)}
 * instead.
 *
 * @param newZoneStrings the new time zone strings.
 */

public void setZoneStrings(java.lang.String[][] newZoneStrings) { throw new RuntimeException("Stub!"); }

/**
 * Returns localized date-time pattern characters. For example: 'u', 't', etc.
 *
 * <p>Note: ICU no longer provides localized date-time pattern characters for a locale
 * starting ICU 3.8.  This method returns the non-localized date-time pattern
 * characters unless user defined localized data is set by setLocalPatternChars.
 * @return the localized date-time pattern characters.
 */

public java.lang.String getLocalPatternChars() { throw new RuntimeException("Stub!"); }

/**
 * Sets localized date-time pattern characters. For example: 'u', 't', etc.
 * @param newLocalPatternChars the new localized date-time
 * pattern characters.
 */

public void setLocalPatternChars(java.lang.String newLocalPatternChars) { throw new RuntimeException("Stub!"); }

/**
 * Overrides clone.
 */

public java.lang.Object clone() { throw new RuntimeException("Stub!"); }

/**
 * Override hashCode.
 * Generates a hash code for the DateFormatSymbols object.
 */

public int hashCode() { throw new RuntimeException("Stub!"); }

/**
 * Overrides equals.
 */

public boolean equals(java.lang.Object obj) { throw new RuntimeException("Stub!"); }

/**
 * Initializes format symbols for the locale and calendar type
 * @param desiredLocale The locale whose symbols are desired.
 * @param type          The calendar type whose date format symbols are desired.
 */

protected void initializeData(android.icu.util.ULocale desiredLocale, java.lang.String type) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Constant for width.
 */

public static final int ABBREVIATED = 0; // 0x0

/**
 * <strong>[icu]</strong> Constant for context.
 */

public static final int FORMAT = 0; // 0x0

/**
 * <strong>[icu]</strong> Constant for width.
 */

public static final int NARROW = 2; // 0x2

/**
 * <strong>[icu]</strong> Constant for width; only supported for weekdays.
 */

public static final int SHORT = 3; // 0x3

/**
 * <strong>[icu]</strong> Constant for context.
 */

public static final int STANDALONE = 1; // 0x1

/**
 * <strong>[icu]</strong> Constant for width.
 */

public static final int WIDE = 1; // 0x1
}

