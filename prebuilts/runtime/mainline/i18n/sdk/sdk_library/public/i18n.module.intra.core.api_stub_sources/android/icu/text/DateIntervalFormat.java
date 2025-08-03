/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
*   Copyright (C) 2008-2016, International Business Machines
*   Corporation and others.  All Rights Reserved.
*/


package android.icu.text;

import java.util.Locale;
import android.icu.util.Calendar;
import android.icu.util.DateInterval;
import java.text.FieldPosition;
import android.icu.util.ULocale;
import android.icu.util.ULocale.Category;
import android.icu.util.Output;
import android.icu.util.TimeZone;

/**
 * DateIntervalFormat is a class for formatting and parsing date
 * intervals in a language-independent manner.
 * Only formatting is supported. Parsing is not supported.
 *
 * <P>
 * Date interval means from one date to another date,
 * for example, from "Jan 11, 2008" to "Jan 18, 2008".
 * We introduced class DateInterval to represent it.
 * DateInterval is a pair of UDate, which is
 * the standard milliseconds since 24:00 GMT, Jan 1, 1970.
 *
 * <P>
 * DateIntervalFormat formats a DateInterval into
 * text as compactly as possible.
 * For example, the date interval format from "Jan 11, 2008" to "Jan 18,. 2008"
 * is "Jan 11-18, 2008" for English.
 * And it parses text into DateInterval,
 * although initially, parsing is not supported.
 *
 * <P>
 * There is no structural information in date time patterns.
 * For any punctuations and string literals inside a date time pattern,
 * we do not know whether it is just a separator, or a prefix, or a suffix.
 * Without such information, so, it is difficult to generate a sub-pattern
 * (or super-pattern) by algorithm.
 * So, formatting a DateInterval is pattern-driven. It is very
 * similar to formatting in SimpleDateFormat.
 * We introduce class DateIntervalInfo to save date interval
 * patterns, similar to date time pattern in SimpleDateFormat.
 *
 * <P>
 * Logically, the interval patterns are mappings
 * from (skeleton, the_largest_different_calendar_field)
 * to (date_interval_pattern).
 *
 * <P>
 * A skeleton
 * <ol>
 * <li>
 * only keeps the field pattern letter and ignores all other parts
 * in a pattern, such as space, punctuations, and string literals.
 * <li>
 * hides the order of fields.
 * <li>
 * might hide a field's pattern letter length.
 *
 * For those non-digit calendar fields, the pattern letter length is
 * important, such as MMM, MMMM, and MMMMM; EEE and EEEE,
 * and the field's pattern letter length is honored.
 *
 * For the digit calendar fields,  such as M or MM, d or dd, yy or yyyy,
 * the field pattern length is ignored and the best match, which is defined
 * in date time patterns, will be returned without honor the field pattern
 * letter length in skeleton.
 * </ol>
 *
 * <P>
 * The calendar fields we support for interval formatting are:
 * year, month, date, day-of-week, am-pm, hour, hour-of-day, minute, and
 * second (though we do not currently have specific intervalFormat data for
 * skeletons with seconds).
 * Those calendar fields can be defined in the following order:
 * year &gt; month &gt; date &gt; hour (in day) &gt; minute &gt; second
 *
 * The largest different calendar fields between 2 calendars is the
 * first different calendar field in above order.
 *
 * For example: the largest different calendar fields between "Jan 10, 2007"
 * and "Feb 20, 2008" is year.
 *
 * <P>
 * For other calendar fields, the compact interval formatting is not
 * supported. And the interval format will be fall back to fall-back
 * patterns, which is mostly "{date0} - {date1}".
 *
 * <P>
 * There is a set of pre-defined static skeleton strings in DateFormat,
 * There are pre-defined interval patterns for those pre-defined skeletons
 * in locales' resource files.
 * For example, for a skeleton YEAR_ABBR_MONTH_DAY, which is  "yMMMd",
 * in  en_US, if the largest different calendar field between date1 and date2
 * is "year", the date interval pattern  is "MMM d, yyyy - MMM d, yyyy",
 * such as "Jan 10, 2007 - Jan 10, 2008".
 * If the largest different calendar field between date1 and date2 is "month",
 * the date interval pattern is "MMM d - MMM d, yyyy",
 * such as "Jan 10 - Feb 10, 2007".
 * If the largest different calendar field between date1 and date2 is "day",
 * the date interval pattern is ""MMM d-d, yyyy", such as "Jan 10-20, 2007".
 *
 * For date skeleton, the interval patterns when year, or month, or date is
 * different are defined in resource files.
 * For time skeleton, the interval patterns when am/pm, or hour, or minute is
 * different are defined in resource files.
 *
 * <P>
 * If a skeleton is not found in a locale's DateIntervalInfo, which means
 * the interval patterns for the skeleton is not defined in resource file,
 * the interval pattern will falls back to the interval "fallback" pattern
 * defined in resource file.
 * If the interval "fallback" pattern is not defined, the default fall-back
 * is "{date0} - {data1}".
 *
 * <P>
 * For the combination of date and time,
 * The rule to genearte interval patterns are:
 * <ol>
 * <li>
 *    when the year, month, or day differs, falls back to fall-back
 *    interval pattern, which mostly is the concatenate the two original
 *    expressions with a separator between,
 *    For example, interval pattern from "Jan 10, 2007 10:10 am"
 *    to "Jan 11, 2007 10:10am" is
 *    "Jan 10, 2007 10:10 am - Jan 11, 2007 10:10am"
 * <li>
 *    otherwise, present the date followed by the range expression
 *    for the time.
 *    For example, interval pattern from "Jan 10, 2007 10:10 am"
 *    to "Jan 10, 2007 11:10am" is "Jan 10, 2007 10:10 am - 11:10am"
 * </ol>
 *
 *
 * <P>
 * If two dates are the same, the interval pattern is the single date pattern.
 * For example, interval pattern from "Jan 10, 2007" to "Jan 10, 2007" is
 * "Jan 10, 2007".
 *
 * Or if the presenting fields between 2 dates have the exact same values,
 * the interval pattern is the  single date pattern.
 * For example, if user only requests year and month,
 * the interval pattern from "Jan 10, 2007" to "Jan 20, 2007" is "Jan 2007".
 *
 * <P>
 * DateIntervalFormat needs the following information for correct
 * formatting: time zone, calendar type, pattern, date format symbols,
 * and date interval patterns.
 * It can be instantiated in several ways:
 * <ol>
 * <li>
 *    create an instance using default or given locale plus given skeleton.
 *    Users are encouraged to created date interval formatter this way and
 *    to use the pre-defined skeleton macros, such as
 *    YEAR_NUM_MONTH, which consists the calendar fields and
 *    the format style.
 * </li>
 * <li>
 *    create an instance using default or given locale plus given skeleton
 *    plus a given DateIntervalInfo.
 *    This factory method is for powerful users who want to provide their own
 *    interval patterns.
 *    Locale provides the timezone, calendar, and format symbols information.
 *    Local plus skeleton provides full pattern information.
 *    DateIntervalInfo provides the date interval patterns.
 * </li>
 * </ol>
 *
 * <P>
 * For the calendar field pattern letter, such as G, y, M, d, a, h, H, m, s etc.
 * DateIntervalFormat uses the same syntax as that of
 * DateTime format.
 *
 * <P>
 * Code Sample: general usage
 * <pre>
 *
 *   // the date interval object which the DateIntervalFormat formats on
 *   // and parses into
 *   DateInterval dtInterval = new DateInterval(1000*3600*24L, 1000*3600*24*2L);
 *   DateIntervalFormat dtIntervalFmt = DateIntervalFormat.getInstance(
 *                   YEAR_MONTH_DAY, Locale("en", "GB", ""));
 *   StringBuffer str = new StringBuffer("");
 *   FieldPosition pos = new FieldPosition(0);
 *   // formatting
 *   dtIntervalFmt.format(dtInterval, dateIntervalString, pos);
 *
 * </pre>
 *
 * <P>
 * Code Sample: for powerful users who wants to use their own interval pattern
 * <pre>
 *
 *     import android.icu.text.DateIntervalInfo;
 *     import android.icu.text.DateIntervalFormat;
 *     ....................
 *
 *     // Get DateIntervalFormat instance using default locale
 *     DateIntervalFormat dtitvfmt = DateIntervalFormat.getInstance(YEAR_MONTH_DAY);
 *
 *     // Create an empty DateIntervalInfo object, which does not have any interval patterns inside.
 *     dtitvinf = new DateIntervalInfo();
 *
 *     // a series of set interval patterns.
 *     // Only ERA, YEAR, MONTH, DATE,  DAY_OF_MONTH, DAY_OF_WEEK, AM_PM,  HOUR, HOUR_OF_DAY,
 *     MINUTE, SECOND and MILLISECOND are supported.
 *     dtitvinf.setIntervalPattern("yMMMd", Calendar.YEAR, "'y ~ y'");
 *     dtitvinf.setIntervalPattern("yMMMd", Calendar.MONTH, "yyyy 'diff' MMM d - MMM d");
 *     dtitvinf.setIntervalPattern("yMMMd", Calendar.DATE, "yyyy MMM d ~ d");
 *     dtitvinf.setIntervalPattern("yMMMd", Calendar.HOUR_OF_DAY, "yyyy MMM d HH:mm ~ HH:mm");
 *
 *     // Set fallback interval pattern. Fallback pattern is used when interval pattern is not found.
 *     // If the fall-back pattern is not set,  falls back to {date0} - {date1} if interval pattern is not found.
 *     dtitvinf.setFallbackIntervalPattern("{0} - {1}");
 *
 *     // Set above DateIntervalInfo object as the interval patterns of date interval formatter
 *     dtitvfmt.setDateIntervalInfo(dtitvinf);
 *
 *     // Prepare to format
 *     pos = new FieldPosition(0);
 *     str = new StringBuffer("");
 *
 *     // The 2 calendars should be equivalent, otherwise,  IllegalArgumentException will be thrown by format()
 *     Calendar fromCalendar = (Calendar) dtfmt.getCalendar().clone();
 *     Calendar toCalendar = (Calendar) dtfmt.getCalendar().clone();
 *     fromCalendar.setTimeInMillis(....);
 *     toCalendar.setTimeInMillis(...);
 *
 *     //Formatting given 2 calendars
 *     dtitvfmt.format(fromCalendar, toCalendar, str, pos);
 *
 *
 * </pre>
 * <h3>Synchronization</h3>
 *
 * The format methods of DateIntervalFormat may be used concurrently from multiple threads.
 * Functions that alter the state of a DateIntervalFormat object (setters)
 * may not be used concurrently with any other functions.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class DateIntervalFormat extends android.icu.text.UFormat {

DateIntervalFormat() { throw new RuntimeException("Stub!"); }

/**
 * Construct a DateIntervalFormat from skeleton and  the default <code>FORMAT</code> locale.
 *
 * This is a convenient override of
 * getInstance(String skeleton, ULocale locale)
 * with the value of locale as default <code>FORMAT</code> locale.
 *
 * @param skeleton  the skeleton on which interval format based.
 * @return          a date time interval formatter.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public static final android.icu.text.DateIntervalFormat getInstance(java.lang.String skeleton) { throw new RuntimeException("Stub!"); }

/**
 * Construct a DateIntervalFormat from skeleton and a given locale.
 *
 * This is a convenient override of
 * getInstance(String skeleton, ULocale locale)
 *
 * <p>Example code:{@sample external/icu/android_icu4j/src/samples/java/android/icu/samples/text/dateintervalformat/DateIntervalFormatSample.java dtitvfmtPreDefinedExample}
 * @param skeleton  the skeleton on which interval format based.
 * @param locale    the given locale
 * @return          a date time interval formatter.
 */

public static final android.icu.text.DateIntervalFormat getInstance(java.lang.String skeleton, java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Construct a DateIntervalFormat from skeleton and a given locale.
 * <P>
 * In this factory method,
 * the date interval pattern information is load from resource files.
 * Users are encouraged to created date interval formatter this way and
 * to use the pre-defined skeleton macros.
 *
 * <P>
 * There are pre-defined skeletons in DateFormat,
 * such as MONTH_DAY, YEAR_MONTH_WEEKDAY_DAY etc.
 *
 * Those skeletons have pre-defined interval patterns in resource files.
 * Users are encouraged to use them.
 * For example:
 * DateIntervalFormat.getInstance(DateFormat.MONTH_DAY, false, loc);
 *
 * The given Locale provides the interval patterns.
 * For example, for en_GB, if skeleton is YEAR_ABBR_MONTH_WEEKDAY_DAY,
 * which is "yMMMEEEd",
 * the interval patterns defined in resource file to above skeleton are:
 * "EEE, d MMM, yyyy - EEE, d MMM, yyyy" for year differs,
 * "EEE, d MMM - EEE, d MMM, yyyy" for month differs,
 * "EEE, d - EEE, d MMM, yyyy" for day differs,
 * @param skeleton  the skeleton on which interval format based.
 * @param locale    the given locale
 * @return          a date time interval formatter.
 */

public static final android.icu.text.DateIntervalFormat getInstance(java.lang.String skeleton, android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Construct a DateIntervalFormat from skeleton
 *  DateIntervalInfo, and the default <code>FORMAT</code> locale.
 *
 * This is a convenient override of
 * getInstance(String skeleton, ULocale locale, DateIntervalInfo dtitvinf)
 * with the locale value as default <code>FORMAT</code> locale.
 *
 * @param skeleton  the skeleton on which interval format based.
 * @param dtitvinf  the DateIntervalInfo object to be adopted.
 * @return          a date time interval formatter.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public static final android.icu.text.DateIntervalFormat getInstance(java.lang.String skeleton, android.icu.text.DateIntervalInfo dtitvinf) { throw new RuntimeException("Stub!"); }

/**
 * Construct a DateIntervalFormat from skeleton
 * a DateIntervalInfo, and the given locale.
 *
 * This is a convenient override of
 * getInstance(String skeleton, ULocale locale, DateIntervalInfo dtitvinf)
 *
 * <p>Example code:{@sample external/icu/android_icu4j/src/samples/java/android/icu/samples/text/dateintervalformat/DateIntervalFormatSample.java dtitvfmtCustomizedExample}
 * @param skeleton  the skeleton on which interval format based.
 * @param locale    the given locale
 * @param dtitvinf  the DateIntervalInfo object to be adopted.
 * @return          a date time interval formatter.
 */

public static final android.icu.text.DateIntervalFormat getInstance(java.lang.String skeleton, java.util.Locale locale, android.icu.text.DateIntervalInfo dtitvinf) { throw new RuntimeException("Stub!"); }

/**
 * Construct a DateIntervalFormat from skeleton
 * a DateIntervalInfo, and the given locale.
 *
 * <P>
 * In this factory method, user provides its own date interval pattern
 * information, instead of using those pre-defined data in resource file.
 * This factory method is for powerful users who want to provide their own
 * interval patterns.
 *
 * <P>
 * There are pre-defined skeleton in DateFormat,
 * such as MONTH_DAY, YEAR_MONTH_WEEKDAY_DAY etc.
 *
 * Those skeletons have pre-defined interval patterns in resource files.
 * Users are encouraged to use them.
 * For example:
 * DateIntervalFormat.getInstance(DateFormat.MONTH_DAY, false, loc,itvinf);
 *
 * the DateIntervalInfo provides the interval patterns.
 *
 * User are encouraged to set default interval pattern in DateIntervalInfo
 * as well, if they want to set other interval patterns ( instead of
 * reading the interval patterns from resource files).
 * When the corresponding interval pattern for a largest calendar different
 * field is not found ( if user not set it ), interval format fallback to
 * the default interval pattern.
 * If user does not provide default interval pattern, it fallback to
 * "{date0} - {date1}"
 *
 * @param skeleton  the skeleton on which interval format based.
 * @param locale    the given locale
 * @param dtitvinf  the DateIntervalInfo object to be adopted.
 * @return          a date time interval formatter.
 */

public static final android.icu.text.DateIntervalFormat getInstance(java.lang.String skeleton, android.icu.util.ULocale locale, android.icu.text.DateIntervalInfo dtitvinf) { throw new RuntimeException("Stub!"); }

/**
 * Clone this Format object polymorphically.
 * @return    A copy of the object.
 */

public synchronized java.lang.Object clone() { throw new RuntimeException("Stub!"); }

/**
 * Format an object to produce a string. This method handles Formattable
 * objects with a DateInterval type.
 * If a the Formattable object type is not a DateInterval,
 * IllegalArgumentException is thrown.
 *
 * @param obj               The object to format.
 *                          Must be a DateInterval.
 * @param appendTo          Output parameter to receive result.
 *                          Result is appended to existing contents.
 * @param fieldPosition     On input: an alignment field, if desired.
 *                          On output: the offsets of the alignment field.
 *                          There may be multiple instances of a given field type
 *                          in an interval format; in this case the fieldPosition
 *                          offsets refer to the first instance.
 * @return                  Reference to 'appendTo' parameter.
 * @throws    java.lang.IllegalArgumentException  if the formatted object is not
 *                                      DateInterval object
 */

public final java.lang.StringBuffer format(java.lang.Object obj, java.lang.StringBuffer appendTo, java.text.FieldPosition fieldPosition) { throw new RuntimeException("Stub!"); }

/**
 * Format a DateInterval to produce a string.
 *
 * @param dtInterval        DateInterval to be formatted.
 * @param appendTo          Output parameter to receive result.
 *                          Result is appended to existing contents.
 * @param fieldPosition     On input: an alignment field, if desired.
 *                          On output: the offsets of the alignment field.
 *                          There may be multiple instances of a given field type
 *                          in an interval format; in this case the fieldPosition
 *                          offsets refer to the first instance.
 * @return                  Reference to 'appendTo' parameter.
 */

public final java.lang.StringBuffer format(android.icu.util.DateInterval dtInterval, java.lang.StringBuffer appendTo, java.text.FieldPosition fieldPosition) { throw new RuntimeException("Stub!"); }

/**
 * Format a DateInterval to produce a FormattedDateInterval.
 *
 * The FormattedDateInterval exposes field information about the formatted string.
 *
 * @param dtInterval        DateInterval to be formatted.
 * @return                  A FormattedDateInterval containing the format result.
 */

public android.icu.text.DateIntervalFormat.FormattedDateInterval formatToValue(android.icu.util.DateInterval dtInterval) { throw new RuntimeException("Stub!"); }

/**
 * Format 2 Calendars to produce a string.
 *
 * @param fromCalendar      calendar set to the from date in date interval
 *                          to be formatted into date interval string
 * @param toCalendar        calendar set to the to date in date interval
 *                          to be formatted into date interval string
 * @param appendTo          Output parameter to receive result.
 *                          Result is appended to existing contents.
 * @param pos               On input: an alignment field, if desired.
 *                          On output: the offsets of the alignment field.
 *                          There may be multiple instances of a given field type
 *                          in an interval format; in this case the fieldPosition
 *                          offsets refer to the first instance.
 * @return                  Reference to 'appendTo' parameter.
 * @throws    java.lang.IllegalArgumentException  if the two calendars are not equivalent.
 */

public final java.lang.StringBuffer format(android.icu.util.Calendar fromCalendar, android.icu.util.Calendar toCalendar, java.lang.StringBuffer appendTo, java.text.FieldPosition pos) { throw new RuntimeException("Stub!"); }

/**
 * Format 2 Calendars to produce a FormattedDateInterval.
 *
 * The FormattedDateInterval exposes field information about the formatted string.
 *
 * @param fromCalendar      calendar set to the from date in date interval
 *                          to be formatted into date interval string
 * @param toCalendar        calendar set to the to date in date interval
 *                          to be formatted into date interval string
 * @return                  A FormattedDateInterval containing the format result.
 */

public android.icu.text.DateIntervalFormat.FormattedDateInterval formatToValue(android.icu.util.Calendar fromCalendar, android.icu.util.Calendar toCalendar) { throw new RuntimeException("Stub!"); }

/**
 * Date interval parsing is not supported.
 * <P>
 * This method should handle parsing of
 * date time interval strings into Formattable objects with
 * DateInterval type, which is a pair of UDate.
 * <P>
 * Before calling, set parse_pos.index to the offset you want to start
 * parsing at in the source. After calling, parse_pos.index is the end of
 * the text you parsed. If error occurs, index is unchanged.
 * <P>
 * When parsing, leading whitespace is discarded (with a successful parse),
 * while trailing whitespace is left as is.
 * <P>
 * See Format.parseObject() for more.
 *
 * @param source    The string to be parsed into an object.
 * @param parse_pos The position to start parsing at. Since no parsing
 *                  is supported, upon return this param is unchanged.
 * @return          A newly created Formattable* object, or NULL
 *                  on failure.
 * @deprecated This API is ICU internal only.
 * @hide original deprecated declaration
 * @hide draft / provisional / internal are hidden on Android
 */

@Deprecated
public java.lang.Object parseObject(java.lang.String source, java.text.ParsePosition parse_pos) { throw new RuntimeException("Stub!"); }

/**
 * Gets the date time interval patterns.
 * @return a copy of the date time interval patterns associated with
 * this date interval formatter.
 */

public android.icu.text.DateIntervalInfo getDateIntervalInfo() { throw new RuntimeException("Stub!"); }

/**
 * Set the date time interval patterns.
 * @param newItvPattern   the given interval patterns to copy.
 */

public void setDateIntervalInfo(android.icu.text.DateIntervalInfo newItvPattern) { throw new RuntimeException("Stub!"); }

/**
 * Get the TimeZone
 * @return A copy of the TimeZone associated with this date interval formatter.
 */

public android.icu.util.TimeZone getTimeZone() { throw new RuntimeException("Stub!"); }

/**
 * Set the TimeZone for the calendar used by this DateIntervalFormat object.
 * @param zone The new TimeZone, will be cloned for use by this DateIntervalFormat.
 */

public void setTimeZone(android.icu.util.TimeZone zone) { throw new RuntimeException("Stub!"); }

/**
 * Gets the date formatter
 * @return a copy of the date formatter associated with
 * this date interval formatter.
 */

public synchronized android.icu.text.DateFormat getDateFormat() { throw new RuntimeException("Stub!"); }
/**
 * An immutable class containing the result of a date interval formatting operation.
 *
 * Instances of this class are immutable and thread-safe.
 *
 * Not intended for public subclassing.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static final class FormattedDateInterval implements android.icu.text.FormattedValue {

FormattedDateInterval() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.String toString() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public int length() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public char charAt(int index) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.CharSequence subSequence(int start, int end) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public <A extends java.lang.Appendable> A appendTo(A appendable) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public boolean nextPosition(android.icu.text.ConstrainedFieldPosition cfpos) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.text.AttributedCharacterIterator toCharacterIterator() { throw new RuntimeException("Stub!"); }
}

}

