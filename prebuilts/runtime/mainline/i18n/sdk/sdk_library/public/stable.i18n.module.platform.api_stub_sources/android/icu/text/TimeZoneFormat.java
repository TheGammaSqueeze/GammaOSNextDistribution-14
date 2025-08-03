/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2011-2016, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */

package android.icu.text;

import java.util.Locale;
import android.icu.util.ULocale;
import java.text.ParsePosition;
import android.icu.util.TimeZone;
import android.icu.util.Output;
import java.util.EnumSet;
import java.text.ParseException;

/**
 * <code>TimeZoneFormat</code> supports time zone display name formatting and parsing.
 * An instance of TimeZoneFormat works as a subformatter of {@link android.icu.text.SimpleDateFormat SimpleDateFormat},
 * but you can also directly get a new instance of <code>TimeZoneFormat</code> and
 * formatting/parsing time zone display names.
 * <p>
 * ICU implements the time zone display names defined by <a href="http://www.unicode.org/reports/tr35/">UTS#35
 * Unicode Locale Data Markup Language (LDML)</a>. {@link android.icu.text.TimeZoneNames TimeZoneNames} represents the
 * time zone display name data model and this class implements the algorithm for actual
 * formatting and parsing.
 *
 * @see android.icu.text.SimpleDateFormat
 * @see android.icu.text.TimeZoneNames
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class TimeZoneFormat extends android.icu.text.UFormat implements android.icu.util.Freezable<android.icu.text.TimeZoneFormat>, java.io.Serializable {

/**
 * The protected constructor for subclassing.
 * @param locale the locale
 */

protected TimeZoneFormat(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns a frozen instance of <code>TimeZoneFormat</code> for the given locale.
 * <p><b>Note</b>: The instance returned by this method is frozen. If you want to
 * customize a TimeZoneFormat, you must use {@link #cloneAsThawed()} to get a
 * thawed copy first.
 *
 * @param locale the locale.
 * @return a frozen instance of <code>TimeZoneFormat</code> for the given locale.
 */

public static android.icu.text.TimeZoneFormat getInstance(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns a frozen instance of <code>TimeZoneFormat</code> for the given
 * {@link java.util.Locale}.
 * <p><b>Note</b>: The instance returned by this method is frozen. If you want to
 * customize a TimeZoneFormat, you must use {@link #cloneAsThawed()} to get a
 * thawed copy first.
 *
 * @param locale the {@link java.util.Locale Locale}.
 * @return a frozen instance of <code>TimeZoneFormat</code> for the given locale.
 */

public static android.icu.text.TimeZoneFormat getInstance(java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns the time zone display name data used by this instance.
 *
 * @return the time zone display name data.
 * @see #setTimeZoneNames(TimeZoneNames)
 */

public android.icu.text.TimeZoneNames getTimeZoneNames() { throw new RuntimeException("Stub!"); }

/**
 * Sets the time zone display name data to this instance.
 *
 * @param tznames the time zone display name data.
 * @return this object.
 * @throws java.lang.UnsupportedOperationException when this object is frozen.
 * @see #getTimeZoneNames()
 */

public android.icu.text.TimeZoneFormat setTimeZoneNames(android.icu.text.TimeZoneNames tznames) { throw new RuntimeException("Stub!"); }

/**
 * Returns the localized GMT format pattern.
 *
 * @return the localized GMT format pattern.
 * @see #setGMTPattern(String)
 */

public java.lang.String getGMTPattern() { throw new RuntimeException("Stub!"); }

/**
 * Sets the localized GMT format pattern. The pattern must contain
 * a single argument {0}, for example "GMT {0}".
 *
 * @param pattern the localized GMT format pattern string
 * @return this object.
 * @throws java.lang.IllegalArgumentException when the pattern string does not contain "{0}"
 * @throws java.lang.UnsupportedOperationException when this object is frozen.
 * @see #getGMTPattern()
 */

public android.icu.text.TimeZoneFormat setGMTPattern(java.lang.String pattern) { throw new RuntimeException("Stub!"); }

/**
 * Returns the offset pattern used for localized GMT format.
 *
 * @param type the offset pattern enum
 * @see #setGMTOffsetPattern(GMTOffsetPatternType, String)
 */

public java.lang.String getGMTOffsetPattern(android.icu.text.TimeZoneFormat.GMTOffsetPatternType type) { throw new RuntimeException("Stub!"); }

/**
 * Sets the offset pattern for the given offset type.
 *
 * @param type the offset pattern.
 * @param pattern the pattern string.
 * @return this object.
 * @throws java.lang.IllegalArgumentException when the pattern string does not have required time field letters.
 * @throws java.lang.UnsupportedOperationException when this object is frozen.
 * @see #getGMTOffsetPattern(GMTOffsetPatternType)
 */

public android.icu.text.TimeZoneFormat setGMTOffsetPattern(android.icu.text.TimeZoneFormat.GMTOffsetPatternType type, java.lang.String pattern) { throw new RuntimeException("Stub!"); }

/**
 * Returns the decimal digit characters used for localized GMT format in a single string
 * containing from 0 to 9 in the ascending order.
 *
 * @return the decimal digits for localized GMT format.
 * @see #setGMTOffsetDigits(String)
 */

public java.lang.String getGMTOffsetDigits() { throw new RuntimeException("Stub!"); }

/**
 * Sets the decimal digit characters used for localized GMT format.
 *
 * @param digits a string contains the decimal digit characters from 0 to 9 n the ascending order.
 * @return this object.
 * @throws java.lang.IllegalArgumentException when the string did not contain ten characters.
 * @throws java.lang.UnsupportedOperationException when this object is frozen.
 * @see #getGMTOffsetDigits()
 */

public android.icu.text.TimeZoneFormat setGMTOffsetDigits(java.lang.String digits) { throw new RuntimeException("Stub!"); }

/**
 * Returns the localized GMT format string for GMT(UTC) itself (GMT offset is 0).
 *
 * @return the localized GMT string string for GMT(UTC) itself.
 * @see #setGMTZeroFormat(String)
 */

public java.lang.String getGMTZeroFormat() { throw new RuntimeException("Stub!"); }

/**
 * Sets the localized GMT format string for GMT(UTC) itself (GMT offset is 0).
 *
 * @param gmtZeroFormat the localized GMT format string for GMT(UTC).
 * @return this object.
 * @throws java.lang.UnsupportedOperationException when this object is frozen.
 * @see #getGMTZeroFormat()
 */

public android.icu.text.TimeZoneFormat setGMTZeroFormat(java.lang.String gmtZeroFormat) { throw new RuntimeException("Stub!"); }

/**
 * Sets the default parse options.
 * <p>
 * <b>Note:</b> By default, an instance of <code>TimeZoneFormat</code>
 * created by {@link #getInstance(android.icu.util.ULocale)} has no parse options set.
 *
 * @param options the default parse options.
 * @return this object.
 * @see android.icu.text.TimeZoneFormat.ParseOption
 */

public android.icu.text.TimeZoneFormat setDefaultParseOptions(java.util.EnumSet<android.icu.text.TimeZoneFormat.ParseOption> options) { throw new RuntimeException("Stub!"); }

/**
 * Returns the default parse options used by this <code>TimeZoneFormat</code> instance.
 * @return the default parse options.
 * @see android.icu.text.TimeZoneFormat.ParseOption
 */

public java.util.EnumSet<android.icu.text.TimeZoneFormat.ParseOption> getDefaultParseOptions() { throw new RuntimeException("Stub!"); }

/**
 * Returns the ISO 8601 basic time zone string for the given offset.
 * For example, "-08", "-0830" and "Z"
 *
 * @param offset the offset from GMT(UTC) in milliseconds.
 * @param useUtcIndicator true if ISO 8601 UTC indicator "Z" is used when the offset is 0.
 * @param isShort true if shortest form is used.
 * @param ignoreSeconds true if non-zero offset seconds is appended.
 * @return the ISO 8601 basic format.
 * @throws java.lang.IllegalArgumentException if the specified offset is out of supported range
 * (-24 hours &lt; offset &lt; +24 hours).
 * @see #formatOffsetISO8601Extended(int, boolean, boolean, boolean)
 * @see #parseOffsetISO8601(String, ParsePosition)
 */

public final java.lang.String formatOffsetISO8601Basic(int offset, boolean useUtcIndicator, boolean isShort, boolean ignoreSeconds) { throw new RuntimeException("Stub!"); }

/**
 * Returns the ISO 8601 extended time zone string for the given offset.
 * For example, "-08:00", "-08:30" and "Z"
 *
 * @param offset the offset from GMT(UTC) in milliseconds.
 * @param useUtcIndicator true if ISO 8601 UTC indicator "Z" is used when the offset is 0.
 * @param isShort true if shortest form is used.
 * @param ignoreSeconds true if non-zero offset seconds is appended.
 * @return the ISO 8601 extended format.
 * @throws java.lang.IllegalArgumentException if the specified offset is out of supported range
 * (-24 hours &lt; offset &lt; +24 hours).
 * @see #formatOffsetISO8601Basic(int, boolean, boolean, boolean)
 * @see #parseOffsetISO8601(String, ParsePosition)
 */

public final java.lang.String formatOffsetISO8601Extended(int offset, boolean useUtcIndicator, boolean isShort, boolean ignoreSeconds) { throw new RuntimeException("Stub!"); }

/**
 * Returns the localized GMT(UTC) offset format for the given offset.
 * The localized GMT offset is defined by;
 * <ul>
 * <li>GMT format pattern (e.g. "GMT {0}" - see {@link #getGMTPattern()})
 * <li>Offset time pattern (e.g. "+HH:mm" - see {@link #getGMTOffsetPattern(android.icu.text.TimeZoneFormat.GMTOffsetPatternType)})
 * <li>Offset digits (e.g. "0123456789" - see {@link #getGMTOffsetDigits()})
 * <li>GMT zero format (e.g. "GMT" - see {@link #getGMTZeroFormat()})
 * </ul>
 * This format always uses 2 digit hours and minutes. When the given offset has non-zero
 * seconds, 2 digit seconds field will be appended. For example,
 * GMT+05:00 and GMT+05:28:06.
 * @param offset the offset from GMT(UTC) in milliseconds.
 * @return the localized GMT format string
 * @see #parseOffsetLocalizedGMT(String, ParsePosition)
 * @throws java.lang.IllegalArgumentException if the specified offset is out of supported range
 * (-24 hours &lt; offset &lt; +24 hours).
 */

public java.lang.String formatOffsetLocalizedGMT(int offset) { throw new RuntimeException("Stub!"); }

/**
 * Returns the short localized GMT(UTC) offset format for the given offset.
 * The short localized GMT offset is defined by;
 * <ul>
 * <li>GMT format pattern (e.g. "GMT {0}" - see {@link #getGMTPattern()})
 * <li>Offset time pattern (e.g. "+HH:mm" - see {@link #getGMTOffsetPattern(android.icu.text.TimeZoneFormat.GMTOffsetPatternType)})
 * <li>Offset digits (e.g. "0123456789" - see {@link #getGMTOffsetDigits()})
 * <li>GMT zero format (e.g. "GMT" - see {@link #getGMTZeroFormat()})
 * </ul>
 * This format uses the shortest representation of offset. The hours field does not
 * have leading zero and lower fields with zero will be truncated. For example,
 * GMT+5 and GMT+530.
 * @param offset the offset from GMT(UTC) in milliseconds.
 * @return the short localized GMT format string
 * @see #parseOffsetLocalizedGMT(String, ParsePosition)
 * @throws java.lang.IllegalArgumentException if the specified offset is out of supported range
 * (-24 hours &lt; offset &lt; +24 hours).
 */

public java.lang.String formatOffsetShortLocalizedGMT(int offset) { throw new RuntimeException("Stub!"); }

/**
 * Returns the display name of the time zone at the given date for
 * the style.
 *
 * <p><b>Note</b>: A style may have fallback styles defined. For example,
 * when <code>GENERIC_LONG</code> is requested, but there is no display name
 * data available for <code>GENERIC_LONG</code> style, the implementation
 * may use <code>GENERIC_LOCATION</code> or <code>LOCALIZED_GMT</code>.
 * See UTS#35 UNICODE LOCALE DATA MARKUP LANGUAGE (LDML)
 * <a href="http://www.unicode.org/reports/tr35/#Time_Zone_Fallback">Appendix J: Time Zone Display Name</a>
 * for the details.
 *
 * @param style the style enum (e.g. <code>GENERIC_LONG</code>, <code>LOCALIZED_GMT</code>...)
 * @param tz the time zone.
 * @param date the date.
 * @return the display name of the time zone.
 * @see android.icu.text.TimeZoneFormat.Style
 * @see #format(Style, TimeZone, long, Output)
 */

public final java.lang.String format(android.icu.text.TimeZoneFormat.Style style, android.icu.util.TimeZone tz, long date) { throw new RuntimeException("Stub!"); }

/**
 * Returns the display name of the time zone at the given date for
 * the style. This method takes an extra argument <code>Output&lt;TimeType&gt; timeType</code>
 * in addition to the argument list of {@link #format(android.icu.text.TimeZoneFormat.Style,android.icu.util.TimeZone,long)}.
 * The argument is used for receiving the time type (standard time
 * or daylight saving time, or unknown) actually used for the display name.
 *
 * @param style the style enum (e.g. <code>GENERIC_LONG</code>, <code>LOCALIZED_GMT</code>...)
 * @param tz the time zone.
 * @param date the date.
 * @param timeType the output argument for receiving the time type (standard/daylight/unknown)
 * used for the display name, or specify null if the information is not necessary.
 * @return the display name of the time zone.
 * @see android.icu.text.TimeZoneFormat.Style
 * @see #format(Style, TimeZone, long)
 */

public java.lang.String format(android.icu.text.TimeZoneFormat.Style style, android.icu.util.TimeZone tz, long date, android.icu.util.Output<android.icu.text.TimeZoneFormat.TimeType> timeType) { throw new RuntimeException("Stub!"); }

/**
 * Returns offset from GMT(UTC) in milliseconds for the given ISO 8601
 * basic or extended time zone string. When the given string is not an ISO 8601 time
 * zone string, this method sets the current position as the error index
 * to <code>ParsePosition pos</code> and returns 0.
 *
 * @param text the text contains ISO 8601 style time zone string (e.g. "-08", "-0800", "-08:00", and "Z")
 * at the position.
 * @param pos the position.
 * @return the offset from GMT(UTC) in milliseconds for the given ISO 8601 style
 * time zone string.
 * @see #formatOffsetISO8601Basic(int, boolean, boolean, boolean)
 * @see #formatOffsetISO8601Extended(int, boolean, boolean, boolean)
 */

public final int parseOffsetISO8601(java.lang.String text, java.text.ParsePosition pos) { throw new RuntimeException("Stub!"); }

/**
 * Returns offset from GMT(UTC) in milliseconds for the given localized GMT
 * offset format string. When the given string cannot be parsed, this method
 * sets the current position as the error index to <code>ParsePosition pos</code>
 * and returns 0.
 *
 * @param text the text contains a localized GMT offset string at the position.
 * @param pos the position.
 * @return the offset from GMT(UTC) in milliseconds for the given localized GMT
 * offset format string.
 * @see #formatOffsetLocalizedGMT(int)
 */

public int parseOffsetLocalizedGMT(java.lang.String text, java.text.ParsePosition pos) { throw new RuntimeException("Stub!"); }

/**
 * Returns offset from GMT(UTC) in milliseconds for the given short localized GMT
 * offset format string. When the given string cannot be parsed, this method
 * sets the current position as the error index to <code>ParsePosition pos</code>
 * and returns 0.
 *
 * @param text the text contains a short localized GMT offset string at the position.
 * @param pos the position.
 * @return the offset from GMT(UTC) in milliseconds for the given short localized GMT
 * offset format string.
 * @see #formatOffsetShortLocalizedGMT(int)
 */

public int parseOffsetShortLocalizedGMT(java.lang.String text, java.text.ParsePosition pos) { throw new RuntimeException("Stub!"); }

/**
 * Returns a <code>TimeZone</code> by parsing the time zone string according to
 * the parse position, the style and the parse options.
 *
 * @param text the text contains a time zone string at the position.
 * @param style the format style.
 * @param pos the position.
 * @param options the parse options.
 * @param timeType The output argument for receiving the time type (standard/daylight/unknown),
 * or specify null if the information is not necessary.
 * @return A <code>TimeZone</code>, or null if the input could not be parsed.
 * @see android.icu.text.TimeZoneFormat.Style
 * @see #format(Style, TimeZone, long, Output)
 */

public android.icu.util.TimeZone parse(android.icu.text.TimeZoneFormat.Style style, java.lang.String text, java.text.ParsePosition pos, java.util.EnumSet<android.icu.text.TimeZoneFormat.ParseOption> options, android.icu.util.Output<android.icu.text.TimeZoneFormat.TimeType> timeType) { throw new RuntimeException("Stub!"); }

/**
 * Returns a <code>TimeZone</code> by parsing the time zone string according to
 * the parse position, the style and the default parse options.
 * <p>
 * <b>Note</b>: This method is equivalent to {@link #parse(android.icu.text.TimeZoneFormat.Style,java.lang.String,java.text.ParsePosition,java.util.EnumSet,android.icu.util.Output)
 * parse(style, text, pos, null, timeType)}.
 *
 * @param text the text contains a time zone string at the position.
 * @param style the format style
 * @param pos the position.
 * @param timeType The output argument for receiving the time type (standard/daylight/unknown),
 * or specify null if the information is not necessary.
 * @return A <code>TimeZone</code>, or null if the input could not be parsed.
 * @see android.icu.text.TimeZoneFormat.Style
 * @see #parse(Style, String, ParsePosition, EnumSet, Output)
 * @see #format(Style, TimeZone, long, Output)
 * @see #setDefaultParseOptions(EnumSet)
 */

public android.icu.util.TimeZone parse(android.icu.text.TimeZoneFormat.Style style, java.lang.String text, java.text.ParsePosition pos, android.icu.util.Output<android.icu.text.TimeZoneFormat.TimeType> timeType) { throw new RuntimeException("Stub!"); }

/**
 * Returns a <code>TimeZone</code> by parsing the time zone string according to
 * the given parse position.
 * <p>
 * <b>Note</b>: This method is equivalent to {@link #parse(android.icu.text.TimeZoneFormat.Style,java.lang.String,java.text.ParsePosition,java.util.EnumSet,android.icu.util.Output)
 * parse(Style.GENERIC_LOCATION, text, pos, EnumSet.of(ParseOption.ALL_STYLES), timeType)}.
 *
 * @param text the text contains a time zone string at the position.
 * @param pos the position.
 * @return A <code>TimeZone</code>, or null if the input could not be parsed.
 * @see #parse(Style, String, ParsePosition, EnumSet, Output)
 */

public final android.icu.util.TimeZone parse(java.lang.String text, java.text.ParsePosition pos) { throw new RuntimeException("Stub!"); }

/**
 * Returns a <code>TimeZone</code> for the given text.
 * <p>
 * <b>Note</b>: The behavior of this method is equivalent to {@link #parse(java.lang.String,java.text.ParsePosition)}.
 * @param text the time zone string
 * @return A <code>TimeZone</code>.
 * @throws java.text.ParseException when the input could not be parsed as a time zone string.
 * @see #parse(String, ParsePosition)
 */

public final android.icu.util.TimeZone parse(java.lang.String text) throws java.text.ParseException { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.StringBuffer format(java.lang.Object obj, java.lang.StringBuffer toAppendTo, java.text.FieldPosition pos) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.text.AttributedCharacterIterator formatToCharacterIterator(java.lang.Object obj) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.Object parseObject(java.lang.String source, java.text.ParsePosition pos) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public boolean isFrozen() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public android.icu.text.TimeZoneFormat freeze() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public android.icu.text.TimeZoneFormat cloneAsThawed() { throw new RuntimeException("Stub!"); }
/**
 * Offset pattern type enum.
 *
 * @see android.icu.text.TimeZoneFormat#getGMTOffsetPattern(GMTOffsetPatternType)
 * @see android.icu.text.TimeZoneFormat#setGMTOffsetPattern(GMTOffsetPatternType, String)
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public enum GMTOffsetPatternType {
/**
 * Positive offset with hours and minutes fields
 */

POSITIVE_HM,
/**
 * Positive offset with hours, minutes and seconds fields
 */

POSITIVE_HMS,
/**
 * Negative offset with hours and minutes fields
 */

NEGATIVE_HM,
/**
 * Negative offset with hours, minutes and seconds fields
 */

NEGATIVE_HMS,
/**
 * Positive offset with hours field
 */

POSITIVE_H,
/**
 * Negative offset with hours field
 */

NEGATIVE_H;
}

/**
 * Parse option enum, used for specifying optional parse behavior.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public enum ParseOption {
/**
 * When a time zone display name is not found within a set of display names
 * used for the specified style, look for the name from display names used
 * by other styles.
 */

ALL_STYLES,
/**
 * When parsing a time zone display name in {@link android.icu.text.TimeZoneFormat.Style#SPECIFIC_SHORT Style#SPECIFIC_SHORT},
 * look for the IANA tz database compatible zone abbreviations in addition
 * to the localized names coming from the {@link android.icu.text.TimeZoneNames TimeZoneNames} currently
 * used by the {@link android.icu.text.TimeZoneFormat TimeZoneFormat}.
 */

TZ_DATABASE_ABBREVIATIONS;
}

/**
 * Time zone display format style enum used by format/parse APIs in <code>TimeZoneFormat</code>.
 *
 * @see android.icu.text.TimeZoneFormat#format(Style, TimeZone, long)
 * @see android.icu.text.TimeZoneFormat#format(Style, TimeZone, long, Output)
 * @see android.icu.text.TimeZoneFormat#parse(Style, String, ParsePosition, Output)
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public enum Style {
/**
 * Generic location format, such as "United States Time (New York)" and "Italy Time".
 * This style is equivalent to the LDML date format pattern "VVVV".
 */

GENERIC_LOCATION,
/**
 * Generic long non-location format, such as "Eastern Time".
 * This style is equivalent to the LDML date format pattern "vvvv".
 */

GENERIC_LONG,
/**
 * Generic short non-location format, such as "ET".
 * This style is equivalent to the LDML date format pattern "v".
 */

GENERIC_SHORT,
/**
 * Specific long format, such as "Eastern Standard Time".
 * This style is equivalent to the LDML date format pattern "zzzz".
 */

SPECIFIC_LONG,
/**
 * Specific short format, such as "EST", "PDT".
 * This style is equivalent to the LDML date format pattern "z".
 */

SPECIFIC_SHORT,
/**
 * Localized GMT offset format, such as "GMT-05:00", "UTC+0100"
 * This style is equivalent to the LDML date format pattern "OOOO" and "ZZZZ"
 */

LOCALIZED_GMT,
/**
 * Short localized GMT offset format, such as "GMT-5", "UTC+1:30"
 * This style is equivalent to the LDML date format pattern "O".
 */

LOCALIZED_GMT_SHORT,
/**
 * Short ISO 8601 local time difference (basic format) or the UTC indicator.
 * For example, "-05", "+0530", and "Z"(UTC).
 * This style is equivalent to the LDML date format pattern "X".
 */

ISO_BASIC_SHORT,
/**
 * Short ISO 8601 locale time difference (basic format).
 * For example, "-05" and "+0530".
 * This style is equivalent to the LDML date format pattern "x".
 */

ISO_BASIC_LOCAL_SHORT,
/**
 * Fixed width ISO 8601 local time difference (basic format) or the UTC indicator.
 * For example, "-0500", "+0530", and "Z"(UTC).
 * This style is equivalent to the LDML date format pattern "XX".
 */

ISO_BASIC_FIXED,
/**
 * Fixed width ISO 8601 local time difference (basic format).
 * For example, "-0500" and "+0530".
 * This style is equivalent to the LDML date format pattern "xx".
 */

ISO_BASIC_LOCAL_FIXED,
/**
 * ISO 8601 local time difference (basic format) with optional seconds field, or the UTC indicator.
 * For example, "-0500", "+052538", and "Z"(UTC).
 * This style is equivalent to the LDML date format pattern "XXXX".
 */

ISO_BASIC_FULL,
/**
 * ISO 8601 local time difference (basic format) with optional seconds field.
 * For example, "-0500" and "+052538".
 * This style is equivalent to the LDML date format pattern "xxxx".
 */

ISO_BASIC_LOCAL_FULL,
/**
 * Fixed width ISO 8601 local time difference (extended format) or the UTC indicator.
 * For example, "-05:00", "+05:30", and "Z"(UTC).
 * This style is equivalent to the LDML date format pattern "XXX".
 */

ISO_EXTENDED_FIXED,
/**
 * Fixed width ISO 8601 local time difference (extended format).
 * For example, "-05:00" and "+05:30".
 * This style is equivalent to the LDML date format pattern "xxx" and "ZZZZZ".
 */

ISO_EXTENDED_LOCAL_FIXED,
/**
 * ISO 8601 local time difference (extended format) with optional seconds field, or the UTC indicator.
 * For example, "-05:00", "+05:25:38", and "Z"(UTC).
 * This style is equivalent to the LDML date format pattern "XXXXX".
 */

ISO_EXTENDED_FULL,
/**
 * ISO 8601 local time difference (extended format) with optional seconds field.
 * For example, "-05:00" and "+05:25:38".
 * This style is equivalent to the LDML date format pattern "xxxxx".
 */

ISO_EXTENDED_LOCAL_FULL,
/**
 * Time Zone ID, such as "America/Los_Angeles".
 */

ZONE_ID,
/**
 * Short Time Zone ID (BCP 47 Unicode location extension, time zone type value), such as "uslax".
 */

ZONE_ID_SHORT,
/**
 * Exemplar location, such as "Los Angeles" and "Paris".
 */

EXEMPLAR_LOCATION;
}

/**
 * Time type enum used for receiving time type (standard time, daylight time or unknown)
 * in <code>TimeZoneFormat</code> APIs.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public enum TimeType {
/**
 * Unknown
 */

UNKNOWN,
/**
 * Standard time
 */

STANDARD,
/**
 * Daylight saving time
 */

DAYLIGHT;
}

}

