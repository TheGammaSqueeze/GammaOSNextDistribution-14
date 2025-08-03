/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *   Copyright (C) 1996-2016, International Business Machines
 *   Corporation and others.  All Rights Reserved.
 */


package android.icu.text;

import java.util.Locale;
import android.icu.util.Calendar;
import android.icu.util.GregorianCalendar;
import java.text.FieldPosition;
import java.util.Date;
import android.icu.util.TimeZone;
import java.text.ParsePosition;
import java.text.Format;
import java.text.ParseException;
import android.icu.util.ULocale.Category;
import java.io.InvalidObjectException;

/**
 * <strong>[icu enhancement]</strong> ICU's replacement for {@link java.text.DateFormat}.&nbsp;Methods, fields, and other functionality specific to ICU are labeled '<strong>[icu]</strong>'.
 *
 * <p>
 * DateFormat is an abstract class for date/time formatting subclasses which formats and parses dates or time in a
 * language-independent manner. The date/time formatting subclass, such as SimpleDateFormat, allows for formatting
 * (i.e., date -&gt; text), parsing (text -&gt; date), and normalization. The date is represented as a <code>Date</code>
 * object or as the milliseconds since January 1, 1970, 00:00:00 GMT.
 *
 * <p>
 * DateFormat helps you to format and parse dates for any locale. Your code can be completely independent of the locale
 * conventions for months, days of the week, or even the calendar format: lunar vs. solar. It provides many class
 * methods for obtaining default date/time formatters based on the default for a given locale and a number of formatting
 * styles or arbitrary "skeletons".
 * <ol>
 * <li>The formatting styles include FULL, LONG, MEDIUM, and SHORT. More detail and examples of using these styles are
 * provided in the method descriptions.
 * <li>The formatting styles only cover a fraction of the necessary usage. You often need to have just certain
 * combinations of fields, like Month and Year, but have it to be formatted appropriate to a given locale. This is done
 * using the (misnamed) getPatternInstance() method, supplying a skeleton. There are a number of constants that have
 * common pre-defined skeletons, such as {@link #MINUTE_SECOND} for something like "13:45" or {@link #YEAR_ABBR_MONTH}
 * for something like "Sept 2012".
 * </ol>
 *
 * <p>
 * To format a date for the current Locale, use one of the static factory methods:
 *
 * <pre>
 * myString = DateFormat.getDateInstance().format(myDate);
 * myString = DateFormat.getPatternInstance(DateFormat.YEAR_ABBR_MONTH).format(myDate);
 * </pre>
 * <p>
 * If you are formatting multiple numbers, it is more efficient to get the format and use it multiple times so that the
 * system doesn't have to fetch the information about the local language and country conventions multiple times.
 *
 * <pre>
 * DateFormat df = DateFormat.getDateInstance();
 * for (int i = 0; i &lt; a.length; ++i) {
 *     output.println(df.format(myDate[i]) + &quot;; &quot;);
 * }
 * </pre>
 * <p>
 * To format a date for a different Locale, specify it in the call to getDateInstance().
 *
 * <pre>
 * DateFormat df = DateFormat.getDateInstance(DateFormat.LONG, Locale.FRANCE);
 * </pre>
 * <p>
 * You can use a DateFormat to parse also.
 *
 * <pre>
 * myDate = df.parse(myString);
 * </pre>
 * <p>
 * There are many static factory methods available. Use getDateInstance to get the normal date format for that country.
 * Use getTimeInstance to get the time format for that country. Use getDateTimeInstance to get a date and time format.
 * You can pass in different options to these factory methods to control the length of the result; from SHORT to MEDIUM
 * to LONG to FULL. The exact result depends on the locale, but generally:
 * <ul>
 * <li>SHORT is completely numeric, such as 12.13.52 or 3:30pm
 * <li>MEDIUM is longer, such as Jan 12, 1952
 * <li>LONG is longer, such as January 12, 1952 or 3:30:32pm
 * <li>FULL is pretty completely specified, such as Tuesday, April 12, 1952 AD or 3:30:42pm PST.
 * </ul>
 *
 * <p>
 * Use getPatternInstance to format with a skeleton. Typically this is with a predefined skeleton, like
 * {@link #YEAR_ABBR_MONTH} for something like "Sept 2012". If you don't want to use one of the predefined skeletons,
 * you can supply your own. The skeletons are like the patterns in SimpleDateFormat, except they:
 * <ol>
 * <li>only keep the field pattern letter and ignore all other parts in a pattern, such as space, punctuation, and
 * string literals.
 * <li>are independent of the order of fields.
 * <li>ignore certain differences in the field's pattern letter length:
 * <ol>
 * <li>For those non-digit calendar fields, the pattern letter length is important, such as MMM, MMMM, and MMMMM; E and
 * EEEE, and the field's pattern letter length is honored.
 * <li>For the digit calendar fields, such as M or MM, d or dd, yy or yyyy, the field pattern length is ignored and the
 * best match, which is defined in date time patterns, will be returned without honor the field pattern letter length in
 * skeleton.
 * </ol>
 * </ol>
 *
 * <p>
 * You can also set the time zone on the format if you wish. If you want even more control over the format or parsing,
 * (or want to give your users more control), you can try casting the DateFormat you get from the factory methods to a
 * SimpleDateFormat. This will work for the majority of countries; just remember to put it in a try block in case you
 * encounter an unusual one.
 *
 * <p>
 * You can also use forms of the parse and format methods with ParsePosition and FieldPosition to allow you to
 * <ul>
 * <li>progressively parse through pieces of a string.
 * <li>align any particular field, or find out where it is for selection on the screen.
 * </ul>
 *
 * <h3>Synchronization</h3>
 *
 * Date formats are not synchronized. It is recommended to create separate format instances for each thread. If multiple
 * threads access a format concurrently, it must be synchronized externally.
 *
 * @see android.icu.text.UFormat
 * @see android.icu.text.NumberFormat
 * @see android.icu.text.SimpleDateFormat
 * @see android.icu.util.Calendar
 * @see android.icu.util.GregorianCalendar
 * @see android.icu.util.TimeZone
 * @author Mark Davis, Chen-Lieh Huang, Alan Liu
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public abstract class DateFormat extends android.icu.text.UFormat {

/**
 * Creates a new date format.
 */

protected DateFormat() { throw new RuntimeException("Stub!"); }

/**
 * Formats a time object into a time string. Examples of time objects
 * are a time value expressed in milliseconds and a Date object.
 * @param obj must be a Number or a Date or a Calendar.
 * @param toAppendTo the string buffer for the returning time string.
 * @return the formatted time string.
 * @param fieldPosition keeps track of the position of the field
 * within the returned string.
 * On input: an alignment field,
 * if desired. On output: the offsets of the alignment field. For
 * example, given a time text "1996.07.10 AD at 15:08:56 PDT",
 * if the given fieldPosition is DateFormat.YEAR_FIELD, the
 * begin index and end index of fieldPosition will be set to
 * 0 and 4, respectively.
 * Notice that if the same time field appears
 * more than once in a pattern, the fieldPosition will be set for the first
 * occurrence of that time field. For instance, formatting a Date to
 * the time string "1 PM PDT (Pacific Daylight Time)" using the pattern
 * "h a z (zzzz)" and the alignment field DateFormat.TIMEZONE_FIELD,
 * the begin index and end index of fieldPosition will be set to
 * 5 and 8, respectively, for the first occurrence of the timezone
 * pattern character 'z'.
 * @see java.text.Format
 */

public final java.lang.StringBuffer format(java.lang.Object obj, java.lang.StringBuffer toAppendTo, java.text.FieldPosition fieldPosition) { throw new RuntimeException("Stub!"); }

/**
 * Formats a date into a date/time string.
 * @param cal a Calendar set to the date and time to be formatted
 * into a date/time string.  When the calendar type is different from
 * the internal calendar held by this DateFormat instance, the date
 * and the time zone will be inherited from the input calendar, but
 * other calendar field values will be calculated by the internal calendar.
 * @param toAppendTo the string buffer for the returning date/time string.
 * @param fieldPosition keeps track of the position of the field
 * within the returned string.
 * On input: an alignment field,
 * if desired. On output: the offsets of the alignment field. For
 * example, given a time text "1996.07.10 AD at 15:08:56 PDT",
 * if the given fieldPosition is DateFormat.YEAR_FIELD, the
 * begin index and end index of fieldPosition will be set to
 * 0 and 4, respectively.
 * Notice that if the same time field appears
 * more than once in a pattern, the fieldPosition will be set for the first
 * occurrence of that time field. For instance, formatting a Date to
 * the time string "1 PM PDT (Pacific Daylight Time)" using the pattern
 * "h a z (zzzz)" and the alignment field DateFormat.TIMEZONE_FIELD,
 * the begin index and end index of fieldPosition will be set to
 * 5 and 8, respectively, for the first occurrence of the timezone
 * pattern character 'z'.
 * @return the formatted date/time string.
 */

public abstract java.lang.StringBuffer format(android.icu.util.Calendar cal, java.lang.StringBuffer toAppendTo, java.text.FieldPosition fieldPosition);

/**
 * Formats a Date into a date/time string.
 * @param date a Date to be formatted into a date/time string.
 * @param toAppendTo the string buffer for the returning date/time string.
 * @param fieldPosition keeps track of the position of the field
 * within the returned string.
 * On input: an alignment field,
 * if desired. On output: the offsets of the alignment field. For
 * example, given a time text "1996.07.10 AD at 15:08:56 PDT",
 * if the given fieldPosition is DateFormat.YEAR_FIELD, the
 * begin index and end index of fieldPosition will be set to
 * 0 and 4, respectively.
 * Notice that if the same time field appears
 * more than once in a pattern, the fieldPosition will be set for the first
 * occurrence of that time field. For instance, formatting a Date to
 * the time string "1 PM PDT (Pacific Daylight Time)" using the pattern
 * "h a z (zzzz)" and the alignment field DateFormat.TIMEZONE_FIELD,
 * the begin index and end index of fieldPosition will be set to
 * 5 and 8, respectively, for the first occurrence of the timezone
 * pattern character 'z'.
 * @return the formatted date/time string.
 */

public java.lang.StringBuffer format(java.util.Date date, java.lang.StringBuffer toAppendTo, java.text.FieldPosition fieldPosition) { throw new RuntimeException("Stub!"); }

/**
 * Formats a Date into a date/time string.
 * @param date the time value to be formatted into a time string.
 * @return the formatted time string.
 */

public final java.lang.String format(java.util.Date date) { throw new RuntimeException("Stub!"); }

/**
 * Parses a date/time string. For example, a time text "07/10/96 4:5 PM, PDT"
 * will be parsed into a Date that is equivalent to Date(837039928046).
 * Parsing begins at the beginning of the string and proceeds as far as
 * possible.  Assuming no parse errors were encountered, this function
 * doesn't return any information about how much of the string was consumed
 * by the parsing.  If you need that information, use a version of
 * parse() that takes a ParsePosition.
 *
 * <p> By default, parsing is lenient: If the input is not in the form used
 * by this object's format method but can still be parsed as a date, then
 * the parse succeeds.  Clients may insist on strict adherence to the
 * format by calling setLenient(false).
 *
 * <p> Note that the normal date formats associated with some calendars - such
 * as the Chinese lunar calendar - do not specify enough fields to enable
 * dates to be parsed unambiguously. In the case of the Chinese lunar
 * calendar, while the year within the current 60-year cycle is specified,
 * the number of such cycles since the start date of the calendar (in the
 * ERA field of the Calendar object) is not normally part of the format,
 * and parsing may assume the wrong era. For cases such as this it is
 * recommended that clients parse using the parse method that takes a Calendar
 * with the Calendar passed in set to the current date, or to a date
 * within the era/cycle that should be assumed if absent in the format.
 *
 * @param text  The date/time string to be parsed
 *
 * @return      A Date, or null if the input could not be parsed
 *
 * @exception  java.text.ParseException  If the given string cannot be parsed as a date.
 *
 * @see #parse(String, ParsePosition)
 */

public java.util.Date parse(java.lang.String text) throws java.text.ParseException { throw new RuntimeException("Stub!"); }

/**
 * Parses a date/time string according to the given parse position.
 * For example, a time text "07/10/96 4:5 PM, PDT" will be parsed
 * into a Calendar that is equivalent to Date(837039928046). Before
 * calling this method the caller should initialize the calendar
 * in one of two ways (unless existing field information is to be kept):
 * (1) clear the calendar, or (2) set the calendar to the current date
 * (or to any date whose fields should be used to supply values that
 * are missing in the parsed date). For example, Chinese calendar dates
 * do not normally provide an era/cycle; in this case the calendar that
 * is passed in should be set to a date within the era that should be
 * assumed, normally the current era.
 *
 * <p> By default, parsing is lenient: If the input is not in the form used
 * by this object's format method but can still be parsed as a date, then
 * the parse succeeds.  Clients may insist on strict adherence to the
 * format by calling setLenient(false).
 *
 * @see #setLenient(boolean)
 *
 * @param text  The date/time string to be parsed
 *
 * @param cal   The calendar set on input to the date and time to be used
 *              for missing values in the date/time string being parsed,
 *              and set on output to the parsed date/time. In general, this
 *              should be initialized before calling this method - either
 *              cleared or set to the current date, depending on desired
 *              behavior. If this parse fails, the calendar may still
 *              have been modified. When the calendar type is different
 *              from the internal calendar held by this DateFormat
 *              instance, calendar field values will be parsed based
 *              on the internal calendar initialized with the time and
 *              the time zone taken from this calendar, then the
 *              parse result (time in milliseconds and time zone) will
 *              be set back to this calendar.
 *
 * @param pos   On input, the position at which to start parsing; on
 *              output, the position at which parsing terminated, or the
 *              start position if the parse failed.
 */

public abstract void parse(java.lang.String text, android.icu.util.Calendar cal, java.text.ParsePosition pos);

/**
 * Parses a date/time string according to the given parse position.  For
 * example, a time text "07/10/96 4:5 PM, PDT" will be parsed into a Date
 * that is equivalent to Date(837039928046).
 *
 * <p> By default, parsing is lenient: If the input is not in the form used
 * by this object's format method but can still be parsed as a date, then
 * the parse succeeds.  Clients may insist on strict adherence to the
 * format by calling setLenient(false).
 *
 * <p> Note that the normal date formats associated with some calendars - such
 * as the Chinese lunar calendar - do not specify enough fields to enable
 * dates to be parsed unambiguously. In the case of the Chinese lunar
 * calendar, while the year within the current 60-year cycle is specified,
 * the number of such cycles since the start date of the calendar (in the
 * ERA field of the Calendar object) is not normally part of the format,
 * and parsing may assume the wrong era. For cases such as this it is
 * recommended that clients parse using the parse method that takes a Calendar
 * with the Calendar passed in set to the current date, or to a date
 * within the era/cycle that should be assumed if absent in the format.
 *
 * @see #setLenient(boolean)
 *
 * @param text  The date/time string to be parsed
 *
 * @param pos   On input, the position at which to start parsing; on
 *              output, the position at which parsing terminated, or the
 *              start position if the parse failed.
 *
 * @return      A Date, or null if the input could not be parsed
 */

public java.util.Date parse(java.lang.String text, java.text.ParsePosition pos) { throw new RuntimeException("Stub!"); }

/**
 * Parses a date/time string into an Object.  This convenience method simply
 * calls parse(String, ParsePosition).
 *
 * @see #parse(String, ParsePosition)
 */

public java.lang.Object parseObject(java.lang.String source, java.text.ParsePosition pos) { throw new RuntimeException("Stub!"); }

/**
 * Gets the time formatter with the default formatting style
 * for the default <code>FORMAT</code> locale.
 * @return a time formatter.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public static final android.icu.text.DateFormat getTimeInstance() { throw new RuntimeException("Stub!"); }

/**
 * Returns the time formatter with the given formatting style
 * for the default <code>FORMAT</code> locale.
 * @param style the given formatting style. For example,
 * SHORT for "h:mm a" in the US locale. Relative time styles are not currently
 * supported, and behave just like the corresponding non-relative style.
 * @return a time formatter.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public static final android.icu.text.DateFormat getTimeInstance(int style) { throw new RuntimeException("Stub!"); }

/**
 * Returns the time formatter with the given formatting style
 * for the given locale.
 * @param style the given formatting style. For example,
 * SHORT for "h:mm a" in the US locale. Relative time styles are not currently
 * supported, and behave just like the corresponding non-relative style.
 * @param aLocale the given locale.
 * @return a time formatter.
 */

public static final android.icu.text.DateFormat getTimeInstance(int style, java.util.Locale aLocale) { throw new RuntimeException("Stub!"); }

/**
 * Returns the time formatter with the given formatting style
 * for the given locale.
 * @param style the given formatting style. For example,
 * SHORT for "h:mm a" in the US locale. Relative time styles are not currently
 * supported, and behave just like the corresponding non-relative style.
 * @param locale the given ulocale.
 * @return a time formatter.
 */

public static final android.icu.text.DateFormat getTimeInstance(int style, android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns the date formatter with the default formatting style
 * for the default <code>FORMAT</code> locale.
 * @return a date formatter.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public static final android.icu.text.DateFormat getDateInstance() { throw new RuntimeException("Stub!"); }

/**
 * Returns the date formatter with the given formatting style
 * for the default <code>FORMAT</code> locale.
 * @param style the given formatting style. For example,
 * SHORT for "M/d/yy" in the US locale. As currently implemented, relative date
 * formatting only affects a limited range of calendar days before or after the
 * current date, based on the CLDR &lt;field type="day"&gt;/&lt;relative&gt; data: For example,
 * in English, "Yesterday", "Today", and "Tomorrow". Outside of this range, relative
 * dates are formatted using the corresponding non-relative style.
 * @return a date formatter.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public static final android.icu.text.DateFormat getDateInstance(int style) { throw new RuntimeException("Stub!"); }

/**
 * Returns the date formatter with the given formatting style
 * for the given locale.
 * @param style the given formatting style. For example,
 * SHORT for "M/d/yy" in the US locale. As currently implemented, relative date
 * formatting only affects a limited range of calendar days before or after the
 * current date, based on the CLDR &lt;field type="day"&gt;/&lt;relative&gt; data: For example,
 * in English, "Yesterday", "Today", and "Tomorrow". Outside of this range, relative
 * dates are formatted using the corresponding non-relative style.
 * @param aLocale the given locale.
 * @return a date formatter.
 */

public static final android.icu.text.DateFormat getDateInstance(int style, java.util.Locale aLocale) { throw new RuntimeException("Stub!"); }

/**
 * Returns the date formatter with the given formatting style
 * for the given locale.
 * @param style the given formatting style. For example,
 * SHORT for "M/d/yy" in the US locale. As currently implemented, relative date
 * formatting only affects a limited range of calendar days before or after the
 * current date, based on the CLDR &lt;field type="day"&gt;/&lt;relative&gt; data: For example,
 * in English, "Yesterday", "Today", and "Tomorrow". Outside of this range, relative
 * dates are formatted using the corresponding non-relative style.
 * @param locale the given ulocale.
 * @return a date formatter.
 */

public static final android.icu.text.DateFormat getDateInstance(int style, android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns the date/time formatter with the default formatting style
 * for the default <code>FORMAT</code> locale.
 * @return a date/time formatter.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public static final android.icu.text.DateFormat getDateTimeInstance() { throw new RuntimeException("Stub!"); }

/**
 * Returns the date/time formatter with the given date and time
 * formatting styles for the default <code>FORMAT</code> locale.
 * @param dateStyle the given date formatting style. For example,
 * SHORT for "M/d/yy" in the US locale. As currently implemented, relative date
 * formatting only affects a limited range of calendar days before or after the
 * current date, based on the CLDR &lt;field type="day"&gt;/&lt;relative&gt; data: For example,
 * in English, "Yesterday", "Today", and "Tomorrow". Outside of this range, relative
 * dates are formatted using the corresponding non-relative style.
 * @param timeStyle the given time formatting style. For example,
 * SHORT for "h:mm a" in the US locale. Relative time styles are not currently
 * supported, and behave just like the corresponding non-relative style.
 * @return a date/time formatter.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public static final android.icu.text.DateFormat getDateTimeInstance(int dateStyle, int timeStyle) { throw new RuntimeException("Stub!"); }

/**
 * Returns the date/time formatter with the given formatting styles
 * for the given locale.
 * @param dateStyle the given date formatting style. As currently implemented, relative date
 * formatting only affects a limited range of calendar days before or after the
 * current date, based on the CLDR &lt;field type="day"&gt;/&lt;relative&gt; data: For example,
 * in English, "Yesterday", "Today", and "Tomorrow". Outside of this range, relative
 * dates are formatted using the corresponding non-relative style.
 * @param timeStyle the given time formatting style. Relative time styles are not
 * currently supported, and behave just like the corresponding non-relative style.
 * @param aLocale the given locale.
 * @return a date/time formatter.
 */

public static final android.icu.text.DateFormat getDateTimeInstance(int dateStyle, int timeStyle, java.util.Locale aLocale) { throw new RuntimeException("Stub!"); }

/**
 * Returns the date/time formatter with the given formatting styles
 * for the given locale.
 * @param dateStyle the given date formatting style. As currently implemented, relative date
 * formatting only affects a limited range of calendar days before or after the
 * current date, based on the CLDR &lt;field type="day"&gt;/&lt;relative&gt; data: For example,
 * in English, "Yesterday", "Today", and "Tomorrow". Outside of this range, relative
 * dates are formatted using the corresponding non-relative style.
 * @param timeStyle the given time formatting style. Relative time styles are not
 * currently supported, and behave just like the corresponding non-relative style.
 * @param locale the given ulocale.
 * @return a date/time formatter.
 */

public static final android.icu.text.DateFormat getDateTimeInstance(int dateStyle, int timeStyle, android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns a default date/time formatter that uses the SHORT style for both the
 * date and the time.
 */

public static final android.icu.text.DateFormat getInstance() { throw new RuntimeException("Stub!"); }

/**
 * Returns the set of locales for which DateFormats are installed.
 * @return the set of locales for which DateFormats are installed.
 */

public static java.util.Locale[] getAvailableLocales() { throw new RuntimeException("Stub!"); }

/**
 * Sets the calendar to be used by this date format.  Initially, the default
 * calendar for the specified or default locale is used.
 * @param newCalendar the new Calendar to be used by the date format
 */

public void setCalendar(android.icu.util.Calendar newCalendar) { throw new RuntimeException("Stub!"); }

/**
 * Returns the calendar associated with this date/time formatter.
 * @return the calendar associated with this date/time formatter.
 */

public android.icu.util.Calendar getCalendar() { throw new RuntimeException("Stub!"); }

/**
 * Sets the number formatter.
 * @param newNumberFormat the given new NumberFormat.
 */

public void setNumberFormat(android.icu.text.NumberFormat newNumberFormat) { throw new RuntimeException("Stub!"); }

/**
 * Returns the number formatter which this date/time formatter uses to
 * format and parse a time.
 * @return the number formatter which this date/time formatter uses.
 */

public android.icu.text.NumberFormat getNumberFormat() { throw new RuntimeException("Stub!"); }

/**
 * Sets the time zone for the calendar of this DateFormat object.
 * @param zone the given new time zone.
 */

public void setTimeZone(android.icu.util.TimeZone zone) { throw new RuntimeException("Stub!"); }

/**
 * Returns the time zone.
 * @return the time zone associated with the calendar of DateFormat.
 */

public android.icu.util.TimeZone getTimeZone() { throw new RuntimeException("Stub!"); }

/**
 * Specifies whether date/time parsing is to be lenient.  With
 * lenient parsing, the parser may use heuristics to interpret inputs that
 * do not precisely match this object's format.  Without lenient parsing,
 * inputs must match this object's format more closely.
 * <br><br>
 * <b>Note:</b> ICU 53 introduced finer grained control of leniency (and added
 * new control points) making the preferred method a combination of
 * setCalendarLenient() &amp; setBooleanAttribute() calls.
 * This method supports prior functionality but may not support all
 * future leniency control &amp; behavior of DateFormat. For control of pre 53 leniency,
 * Calendar and DateFormat whitespace &amp; numeric tolerance, this method is safe to
 * use. However, mixing leniency control via this method and modification of the
 * newer attributes via setBooleanAttribute() may produce undesirable
 * results.
 *
 * @param lenient True specifies date/time interpretation to be lenient.
 * @see android.icu.util.Calendar#setLenient
 * @see #setBooleanAttribute(BooleanAttribute, boolean)
 * @see #setCalendarLenient(boolean)
 */

public void setLenient(boolean lenient) { throw new RuntimeException("Stub!"); }

/**
 * Returns whether both date/time parsing in the encapsulated Calendar object and DateFormat whitespace &amp;
 * numeric processing is lenient.
 */

public boolean isLenient() { throw new RuntimeException("Stub!"); }

/**
 * Specifies whether date/time parsing in the encapsulated Calendar object should be lenient.
 * With lenient parsing, the parser may use heuristics to interpret inputs that
 * do not precisely match this object's format.  Without lenient parsing,
 * inputs must match this object's format more closely.
 * @param lenient when true, Calendar parsing is lenient
 * @see android.icu.util.Calendar#setLenient
 */

public void setCalendarLenient(boolean lenient) { throw new RuntimeException("Stub!"); }

/**
 * Returns whether date/time parsing in the encapsulated Calendar object is lenient.
 */

public boolean isCalendarLenient() { throw new RuntimeException("Stub!"); }

/**
 * Sets a boolean attribute for this instance. Aspects of DateFormat leniency are controlled by
 * boolean attributes.
 *
 * @see android.icu.text.DateFormat.BooleanAttribute
 */

public android.icu.text.DateFormat setBooleanAttribute(android.icu.text.DateFormat.BooleanAttribute key, boolean value) { throw new RuntimeException("Stub!"); }

/**
 * Returns the current value for the specified BooleanAttribute for this instance
 *
 * if attribute is missing false is returned.
 *
 * @see android.icu.text.DateFormat.BooleanAttribute
 */

public boolean getBooleanAttribute(android.icu.text.DateFormat.BooleanAttribute key) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Set a particular DisplayContext value in the formatter,
 * such as CAPITALIZATION_FOR_STANDALONE.
 *
 * @param context The DisplayContext value to set.
 */

public void setContext(android.icu.text.DisplayContext context) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Get the formatter's DisplayContext value for the specified DisplayContext.Type,
 * such as CAPITALIZATION.
 *
 * @param type the DisplayContext.Type whose value to return
 * @return the current DisplayContext setting for the specified type
 */

public android.icu.text.DisplayContext getContext(android.icu.text.DisplayContext.Type type) { throw new RuntimeException("Stub!"); }

/**
 * Overrides hashCode.
 */

public int hashCode() { throw new RuntimeException("Stub!"); }

/**
 * Overrides equals.
 */

public boolean equals(java.lang.Object obj) { throw new RuntimeException("Stub!"); }

/**
 * Overrides clone.
 */

public java.lang.Object clone() { throw new RuntimeException("Stub!"); }

/**
 * Creates a {@link android.icu.text.DateFormat DateFormat} object that can be used to format dates in
 * the calendar system specified by <code>cal</code>.
 * <p>
 * @param cal   The calendar system for which a date format is desired.
 *
 * @param dateStyle The type of date format desired.  This can be
 *              {@link android.icu.text.DateFormat#SHORT DateFormat#SHORT}, {@link android.icu.text.DateFormat#MEDIUM DateFormat#MEDIUM},
 *              etc.
 *
 * @param locale The locale for which the date format is desired.
 */

public static final android.icu.text.DateFormat getDateInstance(android.icu.util.Calendar cal, int dateStyle, java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Creates a {@link android.icu.text.DateFormat DateFormat} object that can be used to format dates in
 * the calendar system specified by <code>cal</code>.
 * <p>
 * @param cal   The calendar system for which a date format is desired.
 *
 * @param dateStyle The type of date format desired.  This can be
 *              {@link android.icu.text.DateFormat#SHORT DateFormat#SHORT}, {@link android.icu.text.DateFormat#MEDIUM DateFormat#MEDIUM},
 *              etc.
 *
 * @param locale The locale for which the date format is desired.
 */

public static final android.icu.text.DateFormat getDateInstance(android.icu.util.Calendar cal, int dateStyle, android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Creates a {@link android.icu.text.DateFormat DateFormat} object that can be used to format times in
 * the calendar system specified by <code>cal</code>.
 * @param cal   The calendar system for which a time format is desired.
 *
 * @param timeStyle The type of time format desired.  This can be
 *              {@link android.icu.text.DateFormat#SHORT DateFormat#SHORT}, {@link android.icu.text.DateFormat#MEDIUM DateFormat#MEDIUM},
 *              etc.
 *
 * @param locale The locale for which the time format is desired.
 *
 * @see DateFormat#getTimeInstance
 */

public static final android.icu.text.DateFormat getTimeInstance(android.icu.util.Calendar cal, int timeStyle, java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Creates a {@link android.icu.text.DateFormat DateFormat} object that can be used to format times in
 * the calendar system specified by <code>cal</code>.
 * @param cal   The calendar system for which a time format is desired.
 *
 * @param timeStyle The type of time format desired.  This can be
 *              {@link android.icu.text.DateFormat#SHORT DateFormat#SHORT}, {@link android.icu.text.DateFormat#MEDIUM DateFormat#MEDIUM},
 *              etc.
 *
 * @param locale The locale for which the time format is desired.
 *
 * @see DateFormat#getTimeInstance
 */

public static final android.icu.text.DateFormat getTimeInstance(android.icu.util.Calendar cal, int timeStyle, android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Creates a {@link android.icu.text.DateFormat DateFormat} object that can be used to format dates and times in
 * the calendar system specified by <code>cal</code>.
 * @param cal   The calendar system for which a date/time format is desired.
 *
 * @param dateStyle The type of date format desired.  This can be
 *              {@link android.icu.text.DateFormat#SHORT DateFormat#SHORT}, {@link android.icu.text.DateFormat#MEDIUM DateFormat#MEDIUM},
 *              etc.
 *
 * @param timeStyle The type of time format desired.  This can be
 *              {@link android.icu.text.DateFormat#SHORT DateFormat#SHORT}, {@link android.icu.text.DateFormat#MEDIUM DateFormat#MEDIUM},
 *              etc.
 *
 * @param locale The locale for which the date/time format is desired.
 *
 * @see DateFormat#getDateTimeInstance
 */

public static final android.icu.text.DateFormat getDateTimeInstance(android.icu.util.Calendar cal, int dateStyle, int timeStyle, java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Creates a {@link android.icu.text.DateFormat DateFormat} object that can be used to format dates and times in
 * the calendar system specified by <code>cal</code>.
 * @param cal   The calendar system for which a date/time format is desired.
 *
 * @param dateStyle The type of date format desired.  This can be
 *              {@link android.icu.text.DateFormat#SHORT DateFormat#SHORT}, {@link android.icu.text.DateFormat#MEDIUM DateFormat#MEDIUM},
 *              etc.
 *
 * @param timeStyle The type of time format desired.  This can be
 *              {@link android.icu.text.DateFormat#SHORT DateFormat#SHORT}, {@link android.icu.text.DateFormat#MEDIUM DateFormat#MEDIUM},
 *              etc.
 *
 * @param locale The locale for which the date/time format is desired.
 *
 * @see DateFormat#getDateTimeInstance
 */

public static final android.icu.text.DateFormat getDateTimeInstance(android.icu.util.Calendar cal, int dateStyle, int timeStyle, android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns a date/time formatter that uses the SHORT style
 * for both the date and the time.
 *
 * @param cal   The calendar system for which a date/time format is desired.
 * @param locale The locale for which the date/time format is desired.
 */

public static final android.icu.text.DateFormat getInstance(android.icu.util.Calendar cal, java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns a default date/time formatter that uses the SHORT style for both the
 * date and the time.
 *
 * @param cal   The calendar system for which a date/time format is desired.
 */

public static final android.icu.text.DateFormat getInstance(android.icu.util.Calendar cal) { throw new RuntimeException("Stub!"); }

/**
 * Creates a {@link android.icu.text.DateFormat DateFormat} object for the default locale that can be used
 * to format dates in the calendar system specified by <code>cal</code>.
 * <p>
 * @param cal   The calendar system for which a date format is desired.
 *
 * @param dateStyle The type of date format desired.  This can be
 *              {@link android.icu.text.DateFormat#SHORT DateFormat#SHORT}, {@link android.icu.text.DateFormat#MEDIUM DateFormat#MEDIUM},
 *              etc.
 */

public static final android.icu.text.DateFormat getDateInstance(android.icu.util.Calendar cal, int dateStyle) { throw new RuntimeException("Stub!"); }

/**
 * Creates a {@link android.icu.text.DateFormat DateFormat} object that can be used to format times in
 * the calendar system specified by <code>cal</code>.
 * @param cal   The calendar system for which a time format is desired.
 *
 * @param timeStyle The type of time format desired.  This can be
 *              {@link android.icu.text.DateFormat#SHORT DateFormat#SHORT}, {@link android.icu.text.DateFormat#MEDIUM DateFormat#MEDIUM},
 *              etc.
 *
 * @see DateFormat#getTimeInstance
 */

public static final android.icu.text.DateFormat getTimeInstance(android.icu.util.Calendar cal, int timeStyle) { throw new RuntimeException("Stub!"); }

/**
 * Creates a {@link android.icu.text.DateFormat DateFormat} object for the default locale that can be used to format
 * dates and times in the calendar system specified by <code>cal</code>.
 * @param cal   The calendar system for which a date/time format is desired.
 *
 * @param dateStyle The type of date format desired.  This can be
 *              {@link android.icu.text.DateFormat#SHORT DateFormat#SHORT}, {@link android.icu.text.DateFormat#MEDIUM DateFormat#MEDIUM},
 *              etc.
 *
 * @param timeStyle The type of time format desired.  This can be
 *              {@link android.icu.text.DateFormat#SHORT DateFormat#SHORT}, {@link android.icu.text.DateFormat#MEDIUM DateFormat#MEDIUM},
 *              etc.
 *
 * @see DateFormat#getDateTimeInstance
 */

public static final android.icu.text.DateFormat getDateTimeInstance(android.icu.util.Calendar cal, int dateStyle, int timeStyle) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a {@link android.icu.text.DateFormat DateFormat} object that can be used to format dates and times in
 * the default locale.
 *
 * @param skeleton The skeleton that selects the fields to be formatted. (Uses the
 *              {@link android.icu.text.DateTimePatternGenerator DateTimePatternGenerator}.) This can be {@link android.icu.text.DateFormat#ABBR_MONTH DateFormat#ABBR_MONTH},
 *              {@link android.icu.text.DateFormat#MONTH_WEEKDAY_DAY DateFormat#MONTH_WEEKDAY_DAY}, etc.
 */

public static final android.icu.text.DateFormat getInstanceForSkeleton(java.lang.String skeleton) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a {@link android.icu.text.DateFormat DateFormat} object that can be used to format dates and times in
 * the given locale.
 *
 * @param skeleton The skeleton that selects the fields to be formatted. (Uses the
 *              {@link android.icu.text.DateTimePatternGenerator DateTimePatternGenerator}.) This can be {@link android.icu.text.DateFormat#ABBR_MONTH DateFormat#ABBR_MONTH},
 *              {@link android.icu.text.DateFormat#MONTH_WEEKDAY_DAY DateFormat#MONTH_WEEKDAY_DAY}, etc.
 *
 * @param locale The locale for which the date/time format is desired.
 */

public static final android.icu.text.DateFormat getInstanceForSkeleton(java.lang.String skeleton, java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a {@link android.icu.text.DateFormat DateFormat} object that can be used to format dates and times in
 * the given locale.
 *
 * @param skeleton The skeleton that selects the fields to be formatted. (Uses the
 *              {@link android.icu.text.DateTimePatternGenerator DateTimePatternGenerator}.) This can be {@link android.icu.text.DateFormat#ABBR_MONTH DateFormat#ABBR_MONTH},
 *              {@link android.icu.text.DateFormat#MONTH_WEEKDAY_DAY DateFormat#MONTH_WEEKDAY_DAY}, etc.
 *
 * @param locale The locale for which the date/time format is desired.
 */

public static final android.icu.text.DateFormat getInstanceForSkeleton(java.lang.String skeleton, android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Creates a {@link android.icu.text.DateFormat DateFormat} object that can be used to format dates and
 * times in the calendar system specified by <code>cal</code>.
 *
 * @param cal   The calendar system for which a date/time format is desired.
 *
 * @param skeleton The skeleton that selects the fields to be formatted. (Uses the
 *              {@link android.icu.text.DateTimePatternGenerator DateTimePatternGenerator}.)  This can be
 *              {@link android.icu.text.DateFormat#ABBR_MONTH DateFormat#ABBR_MONTH}, {@link android.icu.text.DateFormat#MONTH_WEEKDAY_DAY DateFormat#MONTH_WEEKDAY_DAY},
 *              etc.
 *
 * @param locale The locale for which the date/time format is desired.
 */

public static final android.icu.text.DateFormat getInstanceForSkeleton(android.icu.util.Calendar cal, java.lang.String skeleton, java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Creates a {@link android.icu.text.DateFormat DateFormat} object that can be used to format dates and
 * times in the calendar system specified by <code>cal</code>.
 *
 * @param cal   The calendar system for which a date/time format is desired.
 *
 * @param skeleton The skeleton that selects the fields to be formatted. (Uses the
 *              {@link android.icu.text.DateTimePatternGenerator DateTimePatternGenerator}.)  This can be
 *              {@link android.icu.text.DateFormat#ABBR_MONTH DateFormat#ABBR_MONTH}, {@link android.icu.text.DateFormat#MONTH_WEEKDAY_DAY DateFormat#MONTH_WEEKDAY_DAY},
 *              etc.
 *
 * @param locale The locale for which the date/time format is desired.
 */

public static final android.icu.text.DateFormat getInstanceForSkeleton(android.icu.util.Calendar cal, java.lang.String skeleton, android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a {@link android.icu.text.DateFormat DateFormat} object that can be used to format dates and times in
 * the default locale.
 * The getInstanceForSkeleton methods are preferred over the getPatternInstance methods.
 *
 * @param skeleton The skeleton that selects the fields to be formatted. (Uses the
 *              {@link android.icu.text.DateTimePatternGenerator DateTimePatternGenerator}.) This can be {@link android.icu.text.DateFormat#ABBR_MONTH DateFormat#ABBR_MONTH},
 *              {@link android.icu.text.DateFormat#MONTH_WEEKDAY_DAY DateFormat#MONTH_WEEKDAY_DAY}, etc.
 */

public static final android.icu.text.DateFormat getPatternInstance(java.lang.String skeleton) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a {@link android.icu.text.DateFormat DateFormat} object that can be used to format dates and times in
 * the given locale.
 * The getInstanceForSkeleton methods are preferred over the getPatternInstance methods.
 *
 * @param skeleton The skeleton that selects the fields to be formatted. (Uses the
 *              {@link android.icu.text.DateTimePatternGenerator DateTimePatternGenerator}.) This can be {@link android.icu.text.DateFormat#ABBR_MONTH DateFormat#ABBR_MONTH},
 *              {@link android.icu.text.DateFormat#MONTH_WEEKDAY_DAY DateFormat#MONTH_WEEKDAY_DAY}, etc.
 *
 * @param locale The locale for which the date/time format is desired.
 */

public static final android.icu.text.DateFormat getPatternInstance(java.lang.String skeleton, java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a {@link android.icu.text.DateFormat DateFormat} object that can be used to format dates and times in
 * the given locale.
 * The getInstanceForSkeleton methods are preferred over the getPatternInstance methods.
 *
 * @param skeleton The skeleton that selects the fields to be formatted. (Uses the
 *              {@link android.icu.text.DateTimePatternGenerator DateTimePatternGenerator}.) This can be {@link android.icu.text.DateFormat#ABBR_MONTH DateFormat#ABBR_MONTH},
 *              {@link android.icu.text.DateFormat#MONTH_WEEKDAY_DAY DateFormat#MONTH_WEEKDAY_DAY}, etc.
 *
 * @param locale The locale for which the date/time format is desired.
 */

public static final android.icu.text.DateFormat getPatternInstance(java.lang.String skeleton, android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Creates a {@link android.icu.text.DateFormat DateFormat} object that can be used to format dates and
 * times in the calendar system specified by <code>cal</code>.
 * The getInstanceForSkeleton methods are preferred over the getPatternInstance methods.
 *
 * @param cal   The calendar system for which a date/time format is desired.
 *
 * @param skeleton The skeleton that selects the fields to be formatted. (Uses the
 *              {@link android.icu.text.DateTimePatternGenerator DateTimePatternGenerator}.)  This can be
 *              {@link android.icu.text.DateFormat#ABBR_MONTH DateFormat#ABBR_MONTH}, {@link android.icu.text.DateFormat#MONTH_WEEKDAY_DAY DateFormat#MONTH_WEEKDAY_DAY},
 *              etc.
 *
 * @param locale The locale for which the date/time format is desired.
 */

public static final android.icu.text.DateFormat getPatternInstance(android.icu.util.Calendar cal, java.lang.String skeleton, java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Creates a {@link android.icu.text.DateFormat DateFormat} object that can be used to format dates and
 * times in the calendar system specified by <code>cal</code>.
 * The getInstanceForSkeleton methods are preferred over the getPatternInstance methods.
 *
 * @param cal   The calendar system for which a date/time format is desired.
 *
 * @param skeleton The skeleton that selects the fields to be formatted. (Uses the
 *              {@link android.icu.text.DateTimePatternGenerator DateTimePatternGenerator}.)  This can be
 *              {@link android.icu.text.DateFormat#ABBR_MONTH DateFormat#ABBR_MONTH}, {@link android.icu.text.DateFormat#MONTH_WEEKDAY_DAY DateFormat#MONTH_WEEKDAY_DAY},
 *              etc.
 *
 * @param locale The locale for which the date/time format is desired.
 */

public static final android.icu.text.DateFormat getPatternInstance(android.icu.util.Calendar cal, java.lang.String skeleton, android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Constant for <i>generic non-location format</i>, abbreviated if possible, such as PT;
 * used in combinations date + time + zone, or time + zone.
 * @see <a href="http://unicode.org/reports/tr35/#Date_Format_Patterns">LDML Date Format Patterns</a>
 * @see <a href="http://unicode.org/reports/tr35/#Time_Zone_Fallback">LDML Time Zone Fallback</a>
 */

public static final java.lang.String ABBR_GENERIC_TZ = "v";

/**
 * <strong>[icu]</strong> Constant for date skeleton with abbreviated month.
 */

public static final java.lang.String ABBR_MONTH = "MMM";

/**
 * <strong>[icu]</strong> Constant for date skeleton with abbreviated month and day.
 * Used in combinations date + time, date + time + zone, or time + zone.
 */

public static final java.lang.String ABBR_MONTH_DAY = "MMMd";

/**
 * <strong>[icu]</strong> Constant for date skeleton with abbreviated month, weekday, and day.
 * Used in combinations date + time, date + time + zone, or time + zone.
 */

public static final java.lang.String ABBR_MONTH_WEEKDAY_DAY = "MMMEd";

/**
 * <strong>[icu]</strong> Constant for date skeleton with abbreviated quarter.
 */

public static final java.lang.String ABBR_QUARTER = "QQQ";

/**
 * <strong>[icu]</strong> Constant for <i>specific non-location format</i>, abbreviated if possible, such as PDT;
 * used in combinations date + time + zone, or time + zone.
 * @see <a href="http://unicode.org/reports/tr35/#Date_Format_Patterns">LDML Date Format Patterns</a>
 * @see <a href="http://unicode.org/reports/tr35/#Time_Zone_Fallback">LDML Time Zone Fallback</a>
 */

public static final java.lang.String ABBR_SPECIFIC_TZ = "z";

/**
 * <strong>[icu]</strong> Constant for <i>localized GMT/UTC format</i>, such as GMT+8:00 or HPG-8:00;
 * used in combinations date + time + zone, or time + zone.
 * @see <a href="http://unicode.org/reports/tr35/#Date_Format_Patterns">LDML Date Format Patterns</a>
 * @see <a href="http://unicode.org/reports/tr35/#Time_Zone_Fallback">LDML Time Zone Fallback</a>
 */

public static final java.lang.String ABBR_UTC_TZ = "ZZZZ";

/**
 * <strong>[icu]</strong> Constant for date skeleton with abbreviated weekday.
 */

public static final java.lang.String ABBR_WEEKDAY = "E";

/**
 * FieldPosition selector for 'a' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#AM_PM Calendar#AM_PM} field.
 */

public static final int AM_PM_FIELD = 14; // 0xe

/**
 * <strong>[icu]</strong> FieldPosition selector for 'b' field alignment.
 * No related Calendar field.
 * This displays the fixed day period (am/pm/midnight/noon).
 */

public static final int AM_PM_MIDNIGHT_NOON_FIELD = 35; // 0x23

/**
 * FieldPosition selector for 'd' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#DATE Calendar#DATE} field.
 */

public static final int DATE_FIELD = 3; // 0x3

/**
 * <strong>[icu]</strong> Constant for date skeleton with day.
 */

public static final java.lang.String DAY = "d";

/**
 * FieldPosition selector for 'E' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#DAY_OF_WEEK Calendar#DAY_OF_WEEK} field.
 */

public static final int DAY_OF_WEEK_FIELD = 9; // 0x9

/**
 * FieldPosition selector for 'F' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#DAY_OF_WEEK_IN_MONTH Calendar#DAY_OF_WEEK_IN_MONTH} field.
 */

public static final int DAY_OF_WEEK_IN_MONTH_FIELD = 11; // 0xb

/**
 * FieldPosition selector for 'D' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#DAY_OF_YEAR Calendar#DAY_OF_YEAR} field.
 */

public static final int DAY_OF_YEAR_FIELD = 10; // 0xa

/**
 * Constant for default style pattern.  Its value is MEDIUM.
 */

public static final int DEFAULT = 2; // 0x2

/**
 * <strong>[icu]</strong> FieldPosition selector for 'e' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#DOW_LOCAL Calendar#DOW_LOCAL} field.
 */

public static final int DOW_LOCAL_FIELD = 19; // 0x13

/**
 * FieldPosition selector for 'G' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#ERA Calendar#ERA} field.
 */

public static final int ERA_FIELD = 0; // 0x0

/**
 * <strong>[icu]</strong> FieldPosition selector for 'u' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#EXTENDED_YEAR Calendar#EXTENDED_YEAR} field.
 */

public static final int EXTENDED_YEAR_FIELD = 20; // 0x14

/**
 * <strong>[icu]</strong> FieldPosition selector for 'B' field alignment.
 * No related Calendar field.
 * This displays the flexible day period.
 */

public static final int FLEXIBLE_DAY_PERIOD_FIELD = 36; // 0x24

/**
 * <strong>[icu]</strong> FieldPosition selector for 'S' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#MILLISECOND Calendar#MILLISECOND} field.
 *
 * Note: Time formats that use 'S' can display a maximum of three
 * significant digits for fractional seconds, corresponding to millisecond
 * resolution and a fractional seconds sub-pattern of SSS. If the
 * sub-pattern is S or SS, the fractional seconds value will be truncated
 * (not rounded) to the number of display places specified. If the
 * fractional seconds sub-pattern is longer than SSS, the additional
 * display places will be filled with zeros.
 */

public static final int FRACTIONAL_SECOND_FIELD = 8; // 0x8

/**
 * Constant for full style pattern.
 */

public static final int FULL = 0; // 0x0

/**
 * <strong>[icu]</strong> Constant for <i>generic non-location format</i>, such as Pacific Time;
 * used in combinations date + time + zone, or time + zone.
 * @see <a href="http://unicode.org/reports/tr35/#Date_Format_Patterns">LDML Date Format Patterns</a>
 * @see <a href="http://unicode.org/reports/tr35/#Time_Zone_Fallback">LDML Time Zone Fallback</a>
 */

public static final java.lang.String GENERIC_TZ = "vvvv";

/**
 * <strong>[icu]</strong> Constant for date skeleton with hour, with the locale's preferred hour format (12 or 24).
 */

public static final java.lang.String HOUR = "j";

/**
 * FieldPosition selector for 'K' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#HOUR Calendar#HOUR} field.
 * HOUR0_FIELD is used for the zero-based 12-hour clock.
 * For example, 11:30 PM + 1 hour results in 00:30 AM.
 */

public static final int HOUR0_FIELD = 16; // 0x10

/**
 * FieldPosition selector for 'h' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#HOUR Calendar#HOUR} field.
 * HOUR1_FIELD is used for the one-based 12-hour clock.
 * For example, 11:30 PM + 1 hour results in 12:30 AM.
 */

public static final int HOUR1_FIELD = 15; // 0xf

/**
 * <strong>[icu]</strong> Constant for date skeleton with hour in 24-hour presentation.
 */

public static final java.lang.String HOUR24 = "H";

/**
 * <strong>[icu]</strong> Constant for date skeleton with hour and minute in 24-hour presentation.
 * Used in combinations date + time, date + time + zone, or time + zone.
 */

public static final java.lang.String HOUR24_MINUTE = "Hm";

/**
 * <strong>[icu]</strong> Constant for date skeleton with hour, minute, and second in
 * 24-hour presentation.
 * Used in combinations date + time, date + time + zone, or time + zone.
 */

public static final java.lang.String HOUR24_MINUTE_SECOND = "Hms";

/**
 * <strong>[icu]</strong> Constant for date skeleton with hour and minute, with the locale's preferred hour format (12 or 24).
 * Used in combinations date + time, date + time + zone, or time + zone.
 */

public static final java.lang.String HOUR_MINUTE = "jm";

/**
 * <strong>[icu]</strong> Constant for date skeleton with hour, minute, and second,
 * with the locale's preferred hour format (12 or 24).
 * Used in combinations date + time, date + time + zone, or time + zone.
 */

public static final java.lang.String HOUR_MINUTE_SECOND = "jms";

/**
 * FieldPosition selector for 'H' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#HOUR_OF_DAY Calendar#HOUR_OF_DAY} field.
 * HOUR_OF_DAY0_FIELD is used for the zero-based 24-hour clock.
 * For example, 23:59 + 01:00 results in 00:59.
 */

public static final int HOUR_OF_DAY0_FIELD = 5; // 0x5

/**
 * FieldPosition selector for 'k' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#HOUR_OF_DAY Calendar#HOUR_OF_DAY} field.
 * HOUR_OF_DAY1_FIELD is used for the one-based 24-hour clock.
 * For example, 23:59 + 01:00 results in 24:59.
 */

public static final int HOUR_OF_DAY1_FIELD = 4; // 0x4

/**
 * <strong>[icu]</strong> FieldPosition selector for 'g' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#JULIAN_DAY Calendar#JULIAN_DAY} field.
 */

public static final int JULIAN_DAY_FIELD = 21; // 0x15

/**
 * <strong>[icu]</strong> Constant for <i>generic location format</i>, such as Los Angeles Time;
 * used in combinations date + time + zone, or time + zone.
 * @see <a href="http://unicode.org/reports/tr35/#Date_Format_Patterns">LDML Date Format Patterns</a>
 * @see <a href="http://unicode.org/reports/tr35/#Time_Zone_Fallback">LDML Time Zone Fallback</a>
 */

public static final java.lang.String LOCATION_TZ = "VVVV";

/**
 * Constant for long style pattern.
 */

public static final int LONG = 1; // 0x1

/**
 * Constant for medium style pattern.
 */

public static final int MEDIUM = 2; // 0x2

/**
 * <strong>[icu]</strong> FieldPosition selector for 'A' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#MILLISECONDS_IN_DAY Calendar#MILLISECONDS_IN_DAY} field.
 */

public static final int MILLISECONDS_IN_DAY_FIELD = 22; // 0x16

/**
 * Alias for FRACTIONAL_SECOND_FIELD.
 */

public static final int MILLISECOND_FIELD = 8; // 0x8

/**
 * <strong>[icu]</strong> Constant for date skeleton with minute.
 */

public static final java.lang.String MINUTE = "m";

/**
 * FieldPosition selector for 'm' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#MINUTE Calendar#MINUTE} field.
 */

public static final int MINUTE_FIELD = 6; // 0x6

/**
 * <strong>[icu]</strong> Constant for date skeleton with minute and second.
 * Used in combinations date + time, date + time + zone, or time + zone.
 */

public static final java.lang.String MINUTE_SECOND = "ms";

/**
 * <strong>[icu]</strong> Constant for date skeleton with month.
 */

public static final java.lang.String MONTH = "MMMM";

/**
 * <strong>[icu]</strong> Constant for date skeleton with long month and day.
 * Used in combinations date + time, date + time + zone, or time + zone.
 */

public static final java.lang.String MONTH_DAY = "MMMMd";

/**
 * FieldPosition selector for 'M' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#MONTH Calendar#MONTH} field.
 */

public static final int MONTH_FIELD = 2; // 0x2

/**
 * <strong>[icu]</strong> Constant for date skeleton with month, weekday, and day.
 * Used in combinations date + time, date + time + zone, or time + zone.
 */

public static final java.lang.String MONTH_WEEKDAY_DAY = "MMMMEEEEd";

/**
 * <strong>[icu]</strong> Constant for empty style pattern.
 */

public static final int NONE = -1; // 0xffffffff

/**
 * <strong>[icu]</strong> Constant for date skeleton with numeric month.
 */

public static final java.lang.String NUM_MONTH = "M";

/**
 * <strong>[icu]</strong> Constant for date skeleton with numeric month and day.
 * Used in combinations date + time, date + time + zone, or time + zone.
 */

public static final java.lang.String NUM_MONTH_DAY = "Md";

/**
 * <strong>[icu]</strong> Constant for date skeleton with numeric month, weekday, and day.
 * Used in combinations date + time, date + time + zone, or time + zone.
 */

public static final java.lang.String NUM_MONTH_WEEKDAY_DAY = "MEd";

/**
 * <strong>[icu]</strong> Constant for date skeleton with quarter.
 */

public static final java.lang.String QUARTER = "QQQQ";

/**
 * <strong>[icu]</strong> FieldPosition selector for 'Q' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#MONTH Calendar#MONTH} field.
 * This displays the quarter.
 */

public static final int QUARTER_FIELD = 27; // 0x1b

/**
 * <strong>[icu]</strong> Constant for relative style mask.
 */

public static final int RELATIVE = 128; // 0x80

/**
 * <strong>[icu]</strong> Constant for relative default style pattern.
 */

public static final int RELATIVE_DEFAULT = 130; // 0x82

/**
 * <strong>[icu]</strong> Constant for relative full style pattern.
 */

public static final int RELATIVE_FULL = 128; // 0x80

/**
 * <strong>[icu]</strong> Constant for relative style pattern.
 */

public static final int RELATIVE_LONG = 129; // 0x81

/**
 * <strong>[icu]</strong> Constant for relative style pattern.
 */

public static final int RELATIVE_MEDIUM = 130; // 0x82

/**
 * <strong>[icu]</strong> Constant for relative style pattern.
 */

public static final int RELATIVE_SHORT = 131; // 0x83

/**
 * <strong>[icu]</strong> Constant for date skeleton with second.
 */

public static final java.lang.String SECOND = "s";

/**
 * FieldPosition selector for 's' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#SECOND Calendar#SECOND} field.
 */

public static final int SECOND_FIELD = 7; // 0x7

/**
 * Constant for short style pattern.
 */

public static final int SHORT = 3; // 0x3

/**
 * <strong>[icu]</strong> Constant for <i>specific non-location format</i>, such as Pacific Daylight Time;
 * used in combinations date + time + zone, or time + zone.
 * @see <a href="http://unicode.org/reports/tr35/#Date_Format_Patterns">LDML Date Format Patterns</a>
 * @see <a href="http://unicode.org/reports/tr35/#Time_Zone_Fallback">LDML Time Zone Fallback</a>
 */

public static final java.lang.String SPECIFIC_TZ = "zzzz";

/**
 * <strong>[icu]</strong> FieldPosition selector for 'c' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#DAY_OF_WEEK Calendar#DAY_OF_WEEK} field.
 * This displays the stand alone day name, if available.
 */

public static final int STANDALONE_DAY_FIELD = 25; // 0x19

/**
 * <strong>[icu]</strong> FieldPosition selector for 'L' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#MONTH Calendar#MONTH} field.
 * This displays the stand alone month name, if available.
 */

public static final int STANDALONE_MONTH_FIELD = 26; // 0x1a

/**
 * <strong>[icu]</strong> FieldPosition selector for 'q' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#MONTH Calendar#MONTH} field.
 * This displays the stand alone quarter, if available.
 */

public static final int STANDALONE_QUARTER_FIELD = 28; // 0x1c

/**
 * FieldPosition selector for 'z' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#ZONE_OFFSET Calendar#ZONE_OFFSET} and
 * {@link android.icu.util.Calendar#DST_OFFSET Calendar#DST_OFFSET} fields.
 */

public static final int TIMEZONE_FIELD = 17; // 0x11

/**
 * <strong>[icu]</strong> FieldPosition selector for 'v' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#ZONE_OFFSET Calendar#ZONE_OFFSET} and
 * {@link android.icu.util.Calendar#DST_OFFSET Calendar#DST_OFFSET} fields.  This displays the generic zone
 * name, if available.
 */

public static final int TIMEZONE_GENERIC_FIELD = 24; // 0x18

/**
 * <strong>[icu]</strong> FieldPosition selector for 'X' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#ZONE_OFFSET Calendar#ZONE_OFFSET} and
 * {@link android.icu.util.Calendar#DST_OFFSET Calendar#DST_OFFSET} fields.  This displays the
 * ISO 8601 local time offset format or UTC indicator ("Z").
 */

public static final int TIMEZONE_ISO_FIELD = 32; // 0x20

/**
 * <strong>[icu]</strong> FieldPosition selector for 'x' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#ZONE_OFFSET Calendar#ZONE_OFFSET} and
 * {@link android.icu.util.Calendar#DST_OFFSET Calendar#DST_OFFSET} fields.  This displays the
 * ISO 8601 local time offset format.
 */

public static final int TIMEZONE_ISO_LOCAL_FIELD = 33; // 0x21

/**
 * <strong>[icu]</strong> FieldPosition selector for 'O' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#ZONE_OFFSET Calendar#ZONE_OFFSET} and
 * {@link android.icu.util.Calendar#DST_OFFSET Calendar#DST_OFFSET} fields.  This displays the
 * localized GMT format.
 */

public static final int TIMEZONE_LOCALIZED_GMT_OFFSET_FIELD = 31; // 0x1f

/**
 * <strong>[icu]</strong> FieldPosition selector for 'Z' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#ZONE_OFFSET Calendar#ZONE_OFFSET} and
 * {@link android.icu.util.Calendar#DST_OFFSET Calendar#DST_OFFSET} fields.
 */

public static final int TIMEZONE_RFC_FIELD = 23; // 0x17

/**
 * <strong>[icu]</strong> FieldPosition selector for 'V' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#ZONE_OFFSET Calendar#ZONE_OFFSET} and
 * {@link android.icu.util.Calendar#DST_OFFSET Calendar#DST_OFFSET} fields.  This displays the fallback timezone
 * name when VVVV is specified, and the short standard or daylight
 * timezone name ignoring commonlyUsed when a single V is specified.
 */

public static final int TIMEZONE_SPECIAL_FIELD = 29; // 0x1d

/**
 * <strong>[icu]</strong> Constant for date skeleton with weekday.
 */

public static final java.lang.String WEEKDAY = "EEEE";

/**
 * FieldPosition selector for 'W' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#WEEK_OF_MONTH Calendar#WEEK_OF_MONTH} field.
 */

public static final int WEEK_OF_MONTH_FIELD = 13; // 0xd

/**
 * FieldPosition selector for 'w' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#WEEK_OF_YEAR Calendar#WEEK_OF_YEAR} field.
 */

public static final int WEEK_OF_YEAR_FIELD = 12; // 0xc

/**
 * <strong>[icu]</strong> Constant for date skeleton with year.
 */

public static final java.lang.String YEAR = "y";

/**
 * <strong>[icu]</strong> Constant for date skeleton with year and abbreviated month.
 */

public static final java.lang.String YEAR_ABBR_MONTH = "yMMM";

/**
 * <strong>[icu]</strong> Constant for date skeleton with year, abbreviated month, and day.
 * Used in combinations date + time, date + time + zone, or time + zone.
 */

public static final java.lang.String YEAR_ABBR_MONTH_DAY = "yMMMd";

/**
 * <strong>[icu]</strong> Constant for date skeleton with year, abbreviated month, weekday, and day.
 * Used in combinations date + time, date + time + zone, or time + zone.
 */

public static final java.lang.String YEAR_ABBR_MONTH_WEEKDAY_DAY = "yMMMEd";

/**
 * <strong>[icu]</strong> Constant for date skeleton with year and abbreviated quarter.
 */

public static final java.lang.String YEAR_ABBR_QUARTER = "yQQQ";

/**
 * FieldPosition selector for 'y' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#YEAR Calendar#YEAR} field.
 */

public static final int YEAR_FIELD = 1; // 0x1

/**
 * <strong>[icu]</strong> Constant for date skeleton with year and month.
 */

public static final java.lang.String YEAR_MONTH = "yMMMM";

/**
 * <strong>[icu]</strong> Constant for date skeleton with year, month, and day.
 * Used in combinations date + time, date + time + zone, or time + zone.
 */

public static final java.lang.String YEAR_MONTH_DAY = "yMMMMd";

/**
 * <strong>[icu]</strong> Constant for date skeleton with year, month, weekday, and day.
 * Used in combinations date + time, date + time + zone, or time + zone.
 */

public static final java.lang.String YEAR_MONTH_WEEKDAY_DAY = "yMMMMEEEEd";

/**
 * <strong>[icu]</strong> FieldPosition selector for 'U' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#YEAR Calendar#YEAR} field.
 * This displays the cyclic year name, if available.
 */

public static final int YEAR_NAME_FIELD = 30; // 0x1e

/**
 * <strong>[icu]</strong> Constant for date skeleton with year and numeric month.
 */

public static final java.lang.String YEAR_NUM_MONTH = "yM";

/**
 * <strong>[icu]</strong> Constant for date skeleton with year, numeric month, and day.
 * Used in combinations date + time, date + time + zone, or time + zone.
 */

public static final java.lang.String YEAR_NUM_MONTH_DAY = "yMd";

/**
 * <strong>[icu]</strong> Constant for date skeleton with year, numeric month, weekday, and day.
 * Used in combinations date + time, date + time + zone, or time + zone.
 */

public static final java.lang.String YEAR_NUM_MONTH_WEEKDAY_DAY = "yMEd";

/**
 * <strong>[icu]</strong> Constant for date skeleton with year and quarter.
 */

public static final java.lang.String YEAR_QUARTER = "yQQQQ";

/**
 * <strong>[icu]</strong> FieldPosition selector for 'Y' field alignment,
 * corresponding to the {@link android.icu.util.Calendar#YEAR_WOY Calendar#YEAR_WOY} field.
 */

public static final int YEAR_WOY_FIELD = 18; // 0x12

/**
 * The calendar that <code>DateFormat</code> uses to produce the time field
 * values needed to implement date and time formatting.  Subclasses should
 * initialize this to a calendar appropriate for the locale associated with
 * this <code>DateFormat</code>.
 * @serial
 */

protected android.icu.util.Calendar calendar;

/**
 * The number formatter that <code>DateFormat</code> uses to format numbers
 * in dates and times.  Subclasses should initialize this to a number format
 * appropriate for the locale associated with this <code>DateFormat</code>.
 * @serial
 */

protected android.icu.text.NumberFormat numberFormat;
/**
 * boolean attributes
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public enum BooleanAttribute {
/**
 * indicates whitespace tolerance. Also included is trailing dot tolerance.
 */

PARSE_ALLOW_WHITESPACE,
/**
 * indicates tolerance of numeric data when String data may be assumed.
 * e.g. YEAR_NAME_FIELD
 */

PARSE_ALLOW_NUMERIC,
/**
 * indicates tolerance of pattern mismatch between input data and specified format pattern.
 * e.g. accepting "September" for a month pattern of MMM ("Sep")
 */

PARSE_MULTIPLE_PATTERNS_FOR_MATCH,
/**
 * indicates tolerance of a partial literal match
 * e.g. accepting "--mon-02-march-2011" for a pattern of "'--: 'EEE-WW-MMMM-yyyy"
 */

PARSE_PARTIAL_LITERAL_MATCH;
}

/**
 * The instances of this inner class are used as attribute keys and values
 * in AttributedCharacterIterator that
 * DateFormat.formatToCharacterIterator() method returns.
 *
 * <p>There is no public constructor to this class, the only instances are the
 * constants defined here.
 * <p>
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static class Field extends java.text.Format.Field {

/**
 * Constructs a <code>DateFormat.Field</code> with the given name and
 * the <code>Calendar</code> field which this attribute represents.  Use -1 for
 * <code>calendarField</code> if this field does not have a corresponding
 * <code>Calendar</code> field.
 *
 * @param name          Name of the attribute
 * @param calendarField <code>Calendar</code> field constant
 */

protected Field(java.lang.String name, int calendarField) { super(null); throw new RuntimeException("Stub!"); }

/**
 * Returns the <code>Field</code> constant that corresponds to the <code>
 * Calendar</code> field <code>calendarField</code>.  If there is no
 * corresponding <code>Field</code> is available, null is returned.
 *
 * @param calendarField <code>Calendar</code> field constant
 * @return <code>Field</code> associated with the <code>calendarField</code>,
 * or null if no associated <code>Field</code> is available.
 * @throws java.lang.IllegalArgumentException if <code>calendarField</code> is not
 * a valid <code>Calendar</code> field constant.
 */

public static android.icu.text.DateFormat.Field ofCalendarField(int calendarField) { throw new RuntimeException("Stub!"); }

/**
 * Returns the <code>Calendar</code> field associated with this attribute.
 * If there is no corresponding <code>Calendar</code> available, this will
 * return -1.
 *
 * @return <code>Calendar</code> constant for this attribute.
 */

public int getCalendarField() { throw new RuntimeException("Stub!"); }

/**
 * Resolves instances being deserialized to the predefined constants.
 *
 * @throws java.io.InvalidObjectException if the constant could not be resolved.
 */

protected java.lang.Object readResolve() throws java.io.InvalidObjectException { throw new RuntimeException("Stub!"); }

/**
 * Constant identifying the time of day indicator(am/pm).
 */

public static final android.icu.text.DateFormat.Field AM_PM;
static { AM_PM = null; }

/**
 * <strong>[icu]</strong> Constant identifying the am/pm/midnight/noon field.
 */

public static final android.icu.text.DateFormat.Field AM_PM_MIDNIGHT_NOON;
static { AM_PM_MIDNIGHT_NOON = null; }

/**
 * Constant identifying the day of month field.
 */

public static final android.icu.text.DateFormat.Field DAY_OF_MONTH;
static { DAY_OF_MONTH = null; }

/**
 * Constant identifying the day of week field.
 */

public static final android.icu.text.DateFormat.Field DAY_OF_WEEK;
static { DAY_OF_WEEK = null; }

/**
 * Constant identifying the day of week in month field.
 */

public static final android.icu.text.DateFormat.Field DAY_OF_WEEK_IN_MONTH;
static { DAY_OF_WEEK_IN_MONTH = null; }

/**
 * Constant identifying the day of year field.
 */

public static final android.icu.text.DateFormat.Field DAY_OF_YEAR;
static { DAY_OF_YEAR = null; }

/**
 * Constant identifying the local day of week field.
 */

public static final android.icu.text.DateFormat.Field DOW_LOCAL;
static { DOW_LOCAL = null; }

/**
 * Constant identifying the era field.
 */

public static final android.icu.text.DateFormat.Field ERA;
static { ERA = null; }

/**
 * Constant identifying the extended year field.
 */

public static final android.icu.text.DateFormat.Field EXTENDED_YEAR;
static { EXTENDED_YEAR = null; }

/**
 * <strong>[icu]</strong> Constant identifying the flexible day period field.
 */

public static final android.icu.text.DateFormat.Field FLEXIBLE_DAY_PERIOD;
static { FLEXIBLE_DAY_PERIOD = null; }

/**
 * Constant identifying the hour(0-11) field.
 */

public static final android.icu.text.DateFormat.Field HOUR0;
static { HOUR0 = null; }

/**
 * Constant identifying the hour(1-12) field.
 */

public static final android.icu.text.DateFormat.Field HOUR1;
static { HOUR1 = null; }

/**
 * Constant identifying the hour(0-23) of day field.
 */

public static final android.icu.text.DateFormat.Field HOUR_OF_DAY0;
static { HOUR_OF_DAY0 = null; }

/**
 * Constant identifying the hour(1-24) of day field.
 */

public static final android.icu.text.DateFormat.Field HOUR_OF_DAY1;
static { HOUR_OF_DAY1 = null; }

/**
 * Constant identifying the Julian day field.
 */

public static final android.icu.text.DateFormat.Field JULIAN_DAY;
static { JULIAN_DAY = null; }

/**
 * Constant identifying the millisecond field.
 */

public static final android.icu.text.DateFormat.Field MILLISECOND;
static { MILLISECOND = null; }

/**
 * Constant identifying the milliseconds in day field.
 */

public static final android.icu.text.DateFormat.Field MILLISECONDS_IN_DAY;
static { MILLISECONDS_IN_DAY = null; }

/**
 * Constant identifying the minute field.
 */

public static final android.icu.text.DateFormat.Field MINUTE;
static { MINUTE = null; }

/**
 * Constant identifying the month field.
 */

public static final android.icu.text.DateFormat.Field MONTH;
static { MONTH = null; }

/**
 * Constant identifying the quarter field.
 */

public static final android.icu.text.DateFormat.Field QUARTER;
static { QUARTER = null; }

/**
 * Constant identifying the second field.
 */

public static final android.icu.text.DateFormat.Field SECOND;
static { SECOND = null; }

/**
 * Constant identifying the time zone field.
 */

public static final android.icu.text.DateFormat.Field TIME_ZONE;
static { TIME_ZONE = null; }

/**
 * Constant identifying the week of month field.
 */

public static final android.icu.text.DateFormat.Field WEEK_OF_MONTH;
static { WEEK_OF_MONTH = null; }

/**
 * Constant identifying the week of year field.
 */

public static final android.icu.text.DateFormat.Field WEEK_OF_YEAR;
static { WEEK_OF_YEAR = null; }

/**
 * Constant identifying the year field.
 */

public static final android.icu.text.DateFormat.Field YEAR;
static { YEAR = null; }

/**
 * Constant identifying the year used with week of year field.
 */

public static final android.icu.text.DateFormat.Field YEAR_WOY;
static { YEAR_WOY = null; }
}

}

