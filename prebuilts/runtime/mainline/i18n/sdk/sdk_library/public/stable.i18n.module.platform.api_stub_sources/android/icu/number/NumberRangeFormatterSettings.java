/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2018 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html

package android.icu.number;


/**
 * An abstract base class for specifying settings related to number formatting. This class is implemented by
 * {@link android.icu.number.UnlocalizedNumberRangeFormatter UnlocalizedNumberRangeFormatter} and {@link android.icu.number.LocalizedNumberRangeFormatter LocalizedNumberRangeFormatter}. This class is not intended for
 * public subclassing.
 *
 * @author sffc
 * @see android.icu.number.NumberRangeFormatter
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public abstract class NumberRangeFormatterSettings<T extends android.icu.number.NumberRangeFormatterSettings<?>> {

NumberRangeFormatterSettings() { throw new RuntimeException("Stub!"); }

/**
 * Sets the NumberFormatter instance to use for the numbers in the range. The same formatter is applied to both
 * sides of the range.
 * <p>
 * The NumberFormatter instances must not have a locale applied yet; the locale specified on the
 * NumberRangeFormatter will be used.
 *
 * @param formatter
 *            The formatter to use for both numbers in the range.
 * @return The fluent chain.
 * @see android.icu.number.NumberFormatter
 * @see android.icu.number.NumberRangeFormatter
 */

public T numberFormatterBoth(android.icu.number.UnlocalizedNumberFormatter formatter) { throw new RuntimeException("Stub!"); }

/**
 * Sets the NumberFormatter instance to use for the first number in the range.
 * <p>
 * The NumberFormatter instance must not have a locale applied yet; the locale specified on the
 * NumberRangeFormatter will be used.
 *
 * @param formatterFirst
 *            The formatter to use for the first number in the range.
 * @return The fluent chain.
 * @see android.icu.number.NumberFormatter
 * @see android.icu.number.NumberRangeFormatter
 */

public T numberFormatterFirst(android.icu.number.UnlocalizedNumberFormatter formatterFirst) { throw new RuntimeException("Stub!"); }

/**
 * Sets the NumberFormatter instances to use for the second number in the range.
 * <p>
 * The NumberFormatter instance must not have a locale applied yet; the locale specified on the
 * NumberRangeFormatter will be used.
 *
 * @param formatterSecond
 *            The formatter to use for the second number in the range.
 * @return The fluent chain.
 * @see android.icu.number.NumberFormatter
 * @see android.icu.number.NumberRangeFormatter
 */

public T numberFormatterSecond(android.icu.number.UnlocalizedNumberFormatter formatterSecond) { throw new RuntimeException("Stub!"); }

/**
 * Sets the aggressiveness of "collapsing" fields across the range separator. Possible values:
 * <ul>
 * <li>ALL: "3-5K miles"</li>
 * <li>UNIT: "3K - 5K miles"</li>
 * <li>NONE: "3K miles - 5K miles"</li>
 * <li>AUTO: usually UNIT or NONE, depending on the locale and formatter settings</li>
 * </ul>
 * <p>
 * The default value is AUTO.
 *
 * @param collapse
 *            The collapsing strategy to use for this range.
 * @return The fluent chain.
 * @see android.icu.number.NumberRangeFormatter
 */

public T collapse(android.icu.number.NumberRangeFormatter.RangeCollapse collapse) { throw new RuntimeException("Stub!"); }

/**
 * Sets the behavior when the two sides of the range are the same. This could happen if the same two numbers are
 * passed to the formatRange function, or if different numbers are passed to the function but they become the same
 * after rounding rules are applied. Possible values:
 * <ul>
 * <li>SINGLE_VALUE: "5 miles"</li>
 * <li>APPROXIMATELY_OR_SINGLE_VALUE: "~5 miles" or "5 miles", depending on whether the number was the same before
 * rounding was applied</li>
 * <li>APPROXIMATELY: "~5 miles"</li>
 * <li>RANGE: "5-5 miles" (with collapse=UNIT)</li>
 * </ul>
 * <p>
 * The default value is APPROXIMATELY.
 *
 * @param identityFallback
 *            The strategy to use when formatting two numbers that end up being the same.
 * @return The fluent chain.
 * @see android.icu.number.NumberRangeFormatter
 */

public T identityFallback(android.icu.number.NumberRangeFormatter.RangeIdentityFallback identityFallback) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public int hashCode() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public boolean equals(java.lang.Object other) { throw new RuntimeException("Stub!"); }
}

