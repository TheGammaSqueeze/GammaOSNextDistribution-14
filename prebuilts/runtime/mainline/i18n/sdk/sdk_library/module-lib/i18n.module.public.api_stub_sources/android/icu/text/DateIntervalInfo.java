/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2008-2016, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */


package android.icu.text;

import java.util.Locale;
import android.icu.util.Calendar;

/**
 * DateIntervalInfo is a public class for encapsulating localizable
 * date time interval patterns. It is used by DateIntervalFormat.
 *
 * <P>
 * For most users, ordinary use of DateIntervalFormat does not need to create
 * DateIntervalInfo object directly.
 * DateIntervalFormat will take care of it when creating a date interval
 * formatter when user pass in skeleton and locale.
 *
 * <P>
 * For power users, who want to create their own date interval patterns,
 * or want to re-set date interval patterns, they could do so by
 * directly creating DateIntervalInfo and manipulating it.
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
 * year &gt; month &gt; date &gt; am-pm &gt; hour &gt;  minute &gt; second
 *
 * The largest different calendar fields between 2 calendars is the
 * first different calendar field in above order.
 *
 * For example: the largest different calendar fields between "Jan 10, 2007"
 * and "Feb 20, 2008" is year.
 *
 * <P>
 * There is a set of pre-defined static skeleton strings.
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
 *
 * <P>
 * There are 2 dates in interval pattern. For most locales, the first date
 * in an interval pattern is the earlier date. There might be a locale in which
 * the first date in an interval pattern is the later date.
 * We use fallback format for the default order for the locale.
 * For example, if the fallback format is "{0} - {1}", it means
 * the first date in the interval pattern for this locale is earlier date.
 * If the fallback format is "{1} - {0}", it means the first date is the
 * later date.
 * For a particular interval pattern, the default order can be overriden
 * by prefixing "latestFirst:" or "earliestFirst:" to the interval pattern.
 * For example, if the fallback format is "{0}-{1}",
 * but for skeleton "yMMMd", the interval pattern when day is different is
 * "latestFirst:d-d MMM yy", it means by default, the first date in interval
 * pattern is the earlier date. But for skeleton "yMMMd", when day is different,
 * the first date in "d-d MMM yy" is the later date.
 *
 * <P>
 * The recommended way to create a DateIntervalFormat object is to pass in
 * the locale.
 * By using a Locale parameter, the DateIntervalFormat object is
 * initialized with the pre-defined interval patterns for a given or
 * default locale.
 * <P>
 * Users can also create DateIntervalFormat object
 * by supplying their own interval patterns.
 * It provides flexibility for power usage.
 *
 * <P>
 * After a DateIntervalInfo object is created, clients may modify
 * the interval patterns using setIntervalPattern function as so desired.
 * Currently, users can only set interval patterns when the following
 * calendar fields are different: ERA, YEAR, MONTH, DATE,  DAY_OF_MONTH,
 * DAY_OF_WEEK, AM_PM,  HOUR, HOUR_OF_DAY, MINUTE, SECOND, and MILLISECOND.
 * Interval patterns when other calendar fields are different is not supported.
 * <P>
 * DateIntervalInfo objects are cloneable.
 * When clients obtain a DateIntervalInfo object,
 * they can feel free to modify it as necessary.
 * <P>
 * DateIntervalInfo are not expected to be subclassed.
 * Data for a calendar is loaded out of resource bundles.
 * Through ICU 4.4, date interval patterns are only supported in the Gregoria
 * calendar; non-Gregorian calendars are supported from ICU 4.4.1.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class DateIntervalInfo implements java.lang.Cloneable, android.icu.util.Freezable<android.icu.text.DateIntervalInfo>, java.io.Serializable {

/**
 * Construct DateIntervalInfo for the given locale,
 * @param locale  the interval patterns are loaded from the appropriate
 *                calendar data (specified calendar or default calendar)
 *                in this locale.
 */

public DateIntervalInfo(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Construct DateIntervalInfo for the given {@link java.util.Locale}.
 * @param locale  the interval patterns are loaded from the appropriate
 *                calendar data (specified calendar or default calendar)
 *                in this locale.
 */

public DateIntervalInfo(java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Provides a way for client to build interval patterns.
 * User could construct DateIntervalInfo by providing
 * a list of skeletons and their patterns.
 * <P>
 * For example:
 * <pre>
 * DateIntervalInfo dIntervalInfo = new DateIntervalInfo();
 * dIntervalInfo.setIntervalPattern("yMd", Calendar.YEAR, "'from' yyyy-M-d 'to' yyyy-M-d");
 * dIntervalInfo.setIntervalPattern("yMMMd", Calendar.MONTH, "'from' yyyy MMM d 'to' MMM d");
 * dIntervalInfo.setIntervalPattern("yMMMd", Calendar.DAY, "yyyy MMM d-d");
 * dIntervalInfo.setFallbackIntervalPattern("{0} ~ {1}");
 * </pre>
 *
 * Restriction:
 * Currently, users can only set interval patterns when the following
 * calendar fields are different: ERA, YEAR, MONTH, DATE,  DAY_OF_MONTH,
 * DAY_OF_WEEK, AM_PM,  HOUR, HOUR_OF_DAY, MINUTE, SECOND, and MILLISECOND.
 * Interval patterns when other calendar fields are different are
 * not supported.
 *
 * @param skeleton         the skeleton on which interval pattern based
 * @param lrgDiffCalUnit   the largest different calendar unit.
 * @param intervalPattern  the interval pattern on the largest different
 *                         calendar unit.
 *                         For example, if lrgDiffCalUnit is
 *                         "year", the interval pattern for en_US when year
 *                         is different could be "'from' yyyy 'to' yyyy".
 * @throws java.lang.IllegalArgumentException  if setting interval pattern on
 *                            a calendar field that is smaller
 *                            than the MINIMUM_SUPPORTED_CALENDAR_FIELD
 * @throws java.lang.UnsupportedOperationException  if the object is frozen
 */

public void setIntervalPattern(java.lang.String skeleton, int lrgDiffCalUnit, java.lang.String intervalPattern) { throw new RuntimeException("Stub!"); }

/**
 * Get the interval pattern given the largest different calendar field.
 * @param skeleton   the skeleton
 * @param field      the largest different calendar field
 * @return interval pattern  return null if interval pattern is not found.
 * @throws java.lang.IllegalArgumentException  if getting interval pattern on
 *                            a calendar field that is smaller
 *                            than the MINIMUM_SUPPORTED_CALENDAR_FIELD
 */

public android.icu.text.DateIntervalInfo.PatternInfo getIntervalPattern(java.lang.String skeleton, int field) { throw new RuntimeException("Stub!"); }

/**
 * Get the fallback interval pattern.
 * @return fallback interval pattern
 */

public java.lang.String getFallbackIntervalPattern() { throw new RuntimeException("Stub!"); }

/**
 * Re-set the fallback interval pattern.
 *
 * In construction, default fallback pattern is set as "{0} - {1}".
 * And constructor taking locale as parameter will set the
 * fallback pattern as what defined in the locale resource file.
 *
 * This method provides a way for user to replace the fallback pattern.
 *
 * @param fallbackPattern                 fall-back interval pattern.
 * @throws java.lang.UnsupportedOperationException  if the object is frozen
 * @throws java.lang.IllegalArgumentException       if there is no pattern {0} or
 *                                        pattern {1} in fallbakckPattern
 */

public void setFallbackIntervalPattern(java.lang.String fallbackPattern) { throw new RuntimeException("Stub!"); }

/**
 * Get default order -- whether the first date in pattern is later date
 *                      or not.
 *
 * return default date ordering in interval pattern. TRUE if the first date
 *        in pattern is later date, FALSE otherwise.
 */

public boolean getDefaultOrder() { throw new RuntimeException("Stub!"); }

/**
 * Clone this object.
 * @return     a copy of the object
 */

public java.lang.Object clone() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public boolean isFrozen() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public android.icu.text.DateIntervalInfo freeze() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public android.icu.text.DateIntervalInfo cloneAsThawed() { throw new RuntimeException("Stub!"); }

/**
 * Override equals
 */

public boolean equals(java.lang.Object a) { throw new RuntimeException("Stub!"); }

/**
 * Override hashcode
 */

public int hashCode() { throw new RuntimeException("Stub!"); }
/**
 * PatternInfo class saves the first and second part of interval pattern,
 * and whether the interval pattern is earlier date first.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static final class PatternInfo implements java.lang.Cloneable, java.io.Serializable {

/**
 * Constructs a <code>PatternInfo</code> object.
 * @param firstPart     The first part of interval pattern.
 * @param secondPart    The second part of interval pattern.
 * @param firstDateInPtnIsLaterDate Whether the first date in interval patter is later date or not.
 */

public PatternInfo(java.lang.String firstPart, java.lang.String secondPart, boolean firstDateInPtnIsLaterDate) { throw new RuntimeException("Stub!"); }

/**
 * Returns the first part of interval pattern.
 * @return The first part of interval pattern.
 */

public java.lang.String getFirstPart() { throw new RuntimeException("Stub!"); }

/**
 * Returns the second part of interval pattern.
 * @return The second part of interval pattern.
 */

public java.lang.String getSecondPart() { throw new RuntimeException("Stub!"); }

/**
 * Returns whether the first date in interval patter is later date or not.
 * @return Whether the first date in interval patter is later date or not.
 */

public boolean firstDateInPtnIsLaterDate() { throw new RuntimeException("Stub!"); }

/**
 * Compares the specified object with this <code>PatternInfo</code> for equality.
 * @param a The object to be compared.
 * @return <code>true</code> if the specified object is equal to this <code>PatternInfo</code>.
 */

public boolean equals(java.lang.Object a) { throw new RuntimeException("Stub!"); }

/**
 * Returns the hash code of this <code>PatternInfo</code>.
 * @return A hash code value for this object.
 */

public int hashCode() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.String toString() { throw new RuntimeException("Stub!"); }
}

}

