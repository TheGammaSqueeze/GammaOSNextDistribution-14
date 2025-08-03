/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 ********************************************************************************
 * Copyright (C) 2006-2016, Google, International Business Machines Corporation
 * and others. All Rights Reserved.
 ********************************************************************************
 */

package android.icu.text;

import android.icu.util.ULocale.Category;
import java.util.Locale;
import java.util.Map;

/**
 * This class provides flexible generation of date format patterns, like
 * "yy-MM-dd". The user can build up the generator by adding successive
 * patterns. Once that is done, a query can be made using a "skeleton", which is
 * a pattern which just includes the desired fields and lengths. The generator
 * will return the "best fit" pattern corresponding to that skeleton.
 * <p>
 * The main method people will use is getBestPattern(String skeleton), since
 * normally this class is pre-built with data from a particular locale. However,
 * generators can be built directly from other data as well.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class DateTimePatternGenerator implements android.icu.util.Freezable<android.icu.text.DateTimePatternGenerator>, java.lang.Cloneable {

/**
 * Only for use by subclasses
 */

protected DateTimePatternGenerator() { throw new RuntimeException("Stub!"); }

/**
 * Create empty generator, to be constructed with addPattern(...) etc.
 */

public static android.icu.text.DateTimePatternGenerator getEmptyInstance() { throw new RuntimeException("Stub!"); }

/**
 * Construct a flexible generator according to data for the default <code>FORMAT</code> locale.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public static android.icu.text.DateTimePatternGenerator getInstance() { throw new RuntimeException("Stub!"); }

/**
 * Construct a flexible generator according to data for a given locale.
 * @param uLocale The locale to pass.
 */

public static android.icu.text.DateTimePatternGenerator getInstance(android.icu.util.ULocale uLocale) { throw new RuntimeException("Stub!"); }

/**
 * Construct a flexible generator according to data for a given locale.
 * @param locale The {@link java.util.Locale} to pass.
 */

public static android.icu.text.DateTimePatternGenerator getInstance(java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Return the best pattern matching the input skeleton. It is guaranteed to
 * have all of the fields in the skeleton.
 * <p>Example code:{@sample external/icu/android_icu4j/src/samples/java/android/icu/samples/text/datetimepatterngenerator/DateTimePatternGeneratorSample.java getBestPatternExample}
 * @param skeleton The skeleton is a pattern containing only the variable fields.
 *            For example, "MMMdd" and "mmhh" are skeletons.
 * @return Best pattern matching the input skeleton.
 */

public java.lang.String getBestPattern(java.lang.String skeleton) { throw new RuntimeException("Stub!"); }

/**
 * Return the best pattern matching the input skeleton. It is guaranteed to
 * have all of the fields in the skeleton.
 *
 * @param skeleton The skeleton is a pattern containing only the variable fields.
 *            For example, "MMMdd" and "mmhh" are skeletons.
 * @param options MATCH_xxx options for forcing the length of specified fields in
 *            the returned pattern to match those in the skeleton (when this would
 *            not happen otherwise). For default behavior, use MATCH_NO_OPTIONS.
 * @return Best pattern matching the input skeleton (and options).
 */

public java.lang.String getBestPattern(java.lang.String skeleton, int options) { throw new RuntimeException("Stub!"); }

/**
 * Adds a pattern to the generator. If the pattern has the same skeleton as
 * an existing pattern, and the override parameter is set, then the previous
 * value is overridden. Otherwise, the previous value is retained. In either
 * case, the conflicting information is returned in PatternInfo.
 * <p>
 * Note that single-field patterns (like "MMM") are automatically added, and
 * don't need to be added explicitly!
 * * <p>Example code:{@sample external/icu/android_icu4j/src/samples/java/android/icu/samples/text/datetimepatterngenerator/DateTimePatternGeneratorSample.java addPatternExample}
 * @param pattern Pattern to add.
 * @param override When existing values are to be overridden use true, otherwise
 *            use false.
 * @param returnInfo Returned information.
 */

public android.icu.text.DateTimePatternGenerator addPattern(java.lang.String pattern, boolean override, android.icu.text.DateTimePatternGenerator.PatternInfo returnInfo) { throw new RuntimeException("Stub!"); }

/**
 * Utility to return a unique skeleton from a given pattern. For example,
 * both "MMM-dd" and "dd/MMM" produce the skeleton "MMMdd".
 *
 * @param pattern Input pattern, such as "dd/MMM"
 * @return skeleton, such as "MMMdd"
 */

public java.lang.String getSkeleton(java.lang.String pattern) { throw new RuntimeException("Stub!"); }

/**
 * Utility to return a unique base skeleton from a given pattern. This is
 * the same as the skeleton, except that differences in length are minimized
 * so as to only preserve the difference between string and numeric form. So
 * for example, both "MMM-dd" and "d/MMM" produce the skeleton "MMMd"
 * (notice the single d).
 *
 * @param pattern Input pattern, such as "dd/MMM"
 * @return skeleton, such as "MMMdd"
 */

public java.lang.String getBaseSkeleton(java.lang.String pattern) { throw new RuntimeException("Stub!"); }

/**
 * Return a list of all the skeletons (in canonical form) from this class,
 * and the patterns that they map to.
 *
 * @param result an output Map in which to place the mapping from skeleton to
 *            pattern. If you want to see the internal order being used,
 *            supply a LinkedHashMap. If the input value is null, then a
 *            LinkedHashMap is allocated.
 *            <p>
 *            <i>Issue: an alternate API would be to just return a list of
 *            the skeletons, and then have a separate routine to get from
 *            skeleton to pattern.</i>
 * @return the input Map containing the values.
 */

public java.util.Map<java.lang.String,java.lang.String> getSkeletons(java.util.Map<java.lang.String,java.lang.String> result) { throw new RuntimeException("Stub!"); }

/**
 * Return a list of all the base skeletons (in canonical form) from this class
 */

public java.util.Set<java.lang.String> getBaseSkeletons(java.util.Set<java.lang.String> result) { throw new RuntimeException("Stub!"); }

/**
 * Adjusts the field types (width and subtype) of a pattern to match what is
 * in a skeleton. That is, if you supply a pattern like "d-M H:m", and a
 * skeleton of "MMMMddhhmm", then the input pattern is adjusted to be
 * "dd-MMMM hh:mm". This is used internally to get the best match for the
 * input skeleton, but can also be used externally.
 * <p>Example code:{@sample external/icu/android_icu4j/src/samples/java/android/icu/samples/text/datetimepatterngenerator/DateTimePatternGeneratorSample.java replaceFieldTypesExample}
 * @param pattern input pattern
 * @param skeleton For the pattern to match to.
 * @return pattern adjusted to match the skeleton fields widths and subtypes.
 */

public java.lang.String replaceFieldTypes(java.lang.String pattern, java.lang.String skeleton) { throw new RuntimeException("Stub!"); }

/**
 * Adjusts the field types (width and subtype) of a pattern to match what is
 * in a skeleton. That is, if you supply a pattern like "d-M H:m", and a
 * skeleton of "MMMMddhhmm", then the input pattern is adjusted to be
 * "dd-MMMM hh:mm". This is used internally to get the best match for the
 * input skeleton, but can also be used externally.
 *
 * @param pattern input pattern
 * @param skeleton For the pattern to match to.
 * @param options MATCH_xxx options for forcing the length of specified fields in
 *            the returned pattern to match those in the skeleton (when this would
 *            not happen otherwise). For default behavior, use MATCH_NO_OPTIONS.
 * @return pattern adjusted to match the skeleton fields widths and subtypes.
 */

public java.lang.String replaceFieldTypes(java.lang.String pattern, java.lang.String skeleton, int options) { throw new RuntimeException("Stub!"); }

/**
 * The date time format is a message format pattern used to compose date and
 * time patterns. The default value is "{1} {0}", where {1} will be replaced
 * by the date pattern and {0} will be replaced by the time pattern.
 * <p>
 * This is used when the input skeleton contains both date and time fields,
 * but there is not a close match among the added patterns. For example,
 * suppose that this object was created by adding "dd-MMM" and "hh:mm", and
 * its datetimeFormat is the default "{1} {0}". Then if the input skeleton
 * is "MMMdhmm", there is not an exact match, so the input skeleton is
 * broken up into two components "MMMd" and "hmm". There are close matches
 * for those two skeletons, so the result is put together with this pattern,
 * resulting in "d-MMM h:mm".
 *
 * @param dateTimeFormat message format pattern, where {1} will be replaced by the date
 *            pattern and {0} will be replaced by the time pattern.
 */

public void setDateTimeFormat(java.lang.String dateTimeFormat) { throw new RuntimeException("Stub!"); }

/**
 * Getter corresponding to setDateTimeFormat.
 *
 * @return pattern
 */

public java.lang.String getDateTimeFormat() { throw new RuntimeException("Stub!"); }

/**
 * The decimal value is used in formatting fractions of seconds. If the
 * skeleton contains fractional seconds, then this is used with the
 * fractional seconds. For example, suppose that the input pattern is
 * "hhmmssSSSS", and the best matching pattern internally is "H:mm:ss", and
 * the decimal string is ",". Then the resulting pattern is modified to be
 * "H:mm:ss,SSSS"
 *
 * @param decimal The decimal to set to.
 */

public void setDecimal(java.lang.String decimal) { throw new RuntimeException("Stub!"); }

/**
 * Getter corresponding to setDecimal.
 * @return string corresponding to the decimal point
 */

public java.lang.String getDecimal() { throw new RuntimeException("Stub!"); }

/**
 * An AppendItem format is a pattern used to append a field if there is no
 * good match. For example, suppose that the input skeleton is "GyyyyMMMd",
 * and there is no matching pattern internally, but there is a pattern
 * matching "yyyyMMMd", say "d-MM-yyyy". Then that pattern is used, plus the
 * G. The way these two are conjoined is by using the AppendItemFormat for G
 * (era). So if that value is, say "{0}, {1}" then the final resulting
 * pattern is "d-MM-yyyy, G".
 * <p>
 * There are actually three available variables: {0} is the pattern so far,
 * {1} is the element we are adding, and {2} is the name of the element.
 * <p>
 * This reflects the way that the CLDR data is organized.
 *
 * @param field such as ERA
 * @param value pattern, such as "{0}, {1}"
 */

public void setAppendItemFormat(int field, java.lang.String value) { throw new RuntimeException("Stub!"); }

/**
 * Getter corresponding to setAppendItemFormats. Values below 0 or at or
 * above TYPE_LIMIT are illegal arguments.
 *
 * @param field The index to retrieve the append item formats.
 * @return append pattern for field
 */

public java.lang.String getAppendItemFormat(int field) { throw new RuntimeException("Stub!"); }

/**
 * Sets the names of fields, eg "era" in English for ERA. These are only
 * used if the corresponding AppendItemFormat is used, and if it contains a
 * {2} variable.
 * <p>
 * This reflects the way that the CLDR data is organized.
 *
 * @param field Index of the append item names.
 * @param value The value to set the item to.
 */

public void setAppendItemName(int field, java.lang.String value) { throw new RuntimeException("Stub!"); }

/**
 * Getter corresponding to setAppendItemName. Values below 0 or at or above
 * TYPE_LIMIT are illegal arguments. Note: The more general method
 * for getting date/time field display names is getFieldDisplayName.
 *
 * @param field The index to get the append item name.
 * @return name for field
 */

public java.lang.String getAppendItemName(int field) { throw new RuntimeException("Stub!"); }

/**
 * The general interface to get a display name for a particular date/time field,
 * in one of several possible display widths.
 *
 * @param field The field type, such as ERA.
 * @param width The desired DisplayWidth, such as DisplayWidth.ABBREVIATED.
 * @return      The display name for the field
 */

public java.lang.String getFieldDisplayName(int field, android.icu.text.DateTimePatternGenerator.DisplayWidth width) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public boolean isFrozen() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public android.icu.text.DateTimePatternGenerator freeze() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public android.icu.text.DateTimePatternGenerator cloneAsThawed() { throw new RuntimeException("Stub!"); }

/**
 * Returns a copy of this <code>DateTimePatternGenerator</code> object.
 * @return A copy of this <code>DateTimePatternGenerator</code> object.
 */

public java.lang.Object clone() { throw new RuntimeException("Stub!"); }

/**
 */

public static final int DAY = 7; // 0x7

/**
 */

public static final int DAYPERIOD = 10; // 0xa

/**
 */

public static final int DAY_OF_WEEK_IN_MONTH = 9; // 0x9

/**
 */

public static final int DAY_OF_YEAR = 8; // 0x8

/**
 */

public static final int ERA = 0; // 0x0

/**
 */

public static final int FRACTIONAL_SECOND = 14; // 0xe

/**
 */

public static final int HOUR = 11; // 0xb

/**
 * Option mask for forcing the width of all date and time fields.
 * @see #getBestPattern(String, int)
 * @see #replaceFieldTypes(String, String, int)
 */

public static final int MATCH_ALL_FIELDS_LENGTH = 65535; // 0xffff

/**
 * Option mask for forcing the width of hour field.
 * @see #getBestPattern(String, int)
 * @see #replaceFieldTypes(String, String, int)
 */

public static final int MATCH_HOUR_FIELD_LENGTH = 2048; // 0x800

/**
 * Default option mask used for {@link #getBestPattern(java.lang.String,int)}
 * and {@link #replaceFieldTypes(java.lang.String,java.lang.String,int)}.
 * @see #getBestPattern(String, int)
 * @see #replaceFieldTypes(String, String, int)
 */

public static final int MATCH_NO_OPTIONS = 0; // 0x0

/**
 */

public static final int MINUTE = 12; // 0xc

/**
 */

public static final int MONTH = 3; // 0x3

/**
 */

public static final int QUARTER = 2; // 0x2

/**
 */

public static final int SECOND = 13; // 0xd

/**
 */

public static final int WEEKDAY = 6; // 0x6

/**
 */

public static final int WEEK_OF_MONTH = 5; // 0x5

/**
 */

public static final int WEEK_OF_YEAR = 4; // 0x4

/**
 */

public static final int YEAR = 1; // 0x1

/**
 */

public static final int ZONE = 15; // 0xf
/**
 * Field display name width constants for getFieldDisplayName
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public enum DisplayWidth {
/**
 * The full field name
 */

WIDE,
/**
 * An abbreviated field name
 * (may be the same as the wide version, if short enough)
 */

ABBREVIATED,
/**
 * The shortest possible field name
 * (may be the same as the abbreviated version)
 */

NARROW;
}

/**
 * PatternInfo supplies output parameters for addPattern(...). It is used because
 * Java doesn't have real output parameters. It is treated like a struct (eg
 * Point), so all fields are public.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static final class PatternInfo {

/**
 * Simple constructor, since this is treated like a struct.
 */

public PatternInfo() { throw new RuntimeException("Stub!"); }

/**
 */

public static final int BASE_CONFLICT = 1; // 0x1

/**
 */

public static final int CONFLICT = 2; // 0x2

/**
 */

public static final int OK = 0; // 0x0

/**
 */

public java.lang.String conflictingPattern;

/**
 */

public int status;
}

}

