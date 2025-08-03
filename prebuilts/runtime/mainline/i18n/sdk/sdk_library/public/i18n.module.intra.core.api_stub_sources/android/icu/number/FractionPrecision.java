/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html

package android.icu.number;


/**
 * A class that defines a rounding strategy based on a number of fraction places and optionally
 * significant digits to be used when formatting numbers in NumberFormatter.
 *
 * <p>
 * To create a FractionPrecision, use one of the factory methods on Precision.
 *
 * @see android.icu.number.NumberFormatter
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public abstract class FractionPrecision extends android.icu.number.Precision {

FractionPrecision() { throw new RuntimeException("Stub!"); }

/**
 * Ensure that no less than this number of significant digits are retained when rounding according to
 * fraction rules.
 *
 * <p>
 * For example, with integer rounding, the number 3.141 becomes "3". However, with minimum figures
 * set to 2, 3.141 becomes "3.1" instead.
 *
 * <p>
 * This setting does not affect the number of trailing zeros. For example, 3.01 would print as "3",
 * not "3.0".
 *
 * @param minSignificantDigits
 *            The number of significant figures to guarantee.
 * @return A Precision for chaining or passing to the NumberFormatter rounding() setter.
 * @throws java.lang.IllegalArgumentException if the input number is too big or smaller than 1.
 * @see android.icu.number.NumberFormatter
 */

public android.icu.number.Precision withMinDigits(int minSignificantDigits) { throw new RuntimeException("Stub!"); }

/**
 * Ensure that no more than this number of significant digits are retained when rounding according to
 * fraction rules.
 *
 * <p>
 * For example, with integer rounding, the number 123.4 becomes "123". However, with maximum figures
 * set to 2, 123.4 becomes "120" instead.
 *
 * <p>
 * This setting does not affect the number of trailing zeros. For example, with fixed fraction of 2,
 * 123.4 would become "120.00".
 *
 * @param maxSignificantDigits
 *            Round the number to no more than this number of significant figures.
 * @return A Precision for chaining or passing to the NumberFormatter rounding() setter.
 * @throws java.lang.IllegalArgumentException if the input number is too big or smaller than 1.
 * @see android.icu.number.NumberFormatter
 */

public android.icu.number.Precision withMaxDigits(int maxSignificantDigits) { throw new RuntimeException("Stub!"); }
}

