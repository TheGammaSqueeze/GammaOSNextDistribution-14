/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2018 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html

package android.icu.number;


/**
 * A NumberRangeFormatter that has a locale associated with it; this means .formatRange() methods are available.
 *
 * Instances of this class are immutable and thread-safe.
 *
 * @author sffc
 * @see android.icu.number.NumberRangeFormatter
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class LocalizedNumberRangeFormatter extends android.icu.number.NumberRangeFormatterSettings<android.icu.number.LocalizedNumberRangeFormatter> {

LocalizedNumberRangeFormatter() { throw new RuntimeException("Stub!"); }

/**
 * Format the given integers to a string using the settings specified in the NumberRangeFormatter fluent setting
 * chain.
 *
 * @param first
 *            The first number in the range, usually to the left in LTR locales.
 * @param second
 *            The second number in the range, usually to the right in LTR locales.
 * @return A FormattedNumberRange object; call .toString() to get the string.
 * @see android.icu.number.NumberRangeFormatter
 */

public android.icu.number.FormattedNumberRange formatRange(int first, int second) { throw new RuntimeException("Stub!"); }

/**
 * Format the given doubles to a string using the settings specified in the NumberRangeFormatter fluent setting
 * chain.
 *
 * @param first
 *            The first number in the range, usually to the left in LTR locales.
 * @param second
 *            The second number in the range, usually to the right in LTR locales.
 * @return A FormattedNumberRange object; call .toString() to get the string.
 * @see android.icu.number.NumberRangeFormatter
 */

public android.icu.number.FormattedNumberRange formatRange(double first, double second) { throw new RuntimeException("Stub!"); }

/**
 * Format the given Numbers to a string using the settings specified in the NumberRangeFormatter fluent setting
 * chain.
 *
 * @param first
 *            The first number in the range, usually to the left in LTR locales.
 * @param second
 *            The second number in the range, usually to the right in LTR locales.
 * @return A FormattedNumberRange object; call .toString() to get the string.
 * @throws java.lang.IllegalArgumentException if first or second is null
 * @see android.icu.number.NumberRangeFormatter
 */

public android.icu.number.FormattedNumberRange formatRange(java.lang.Number first, java.lang.Number second) { throw new RuntimeException("Stub!"); }
}

