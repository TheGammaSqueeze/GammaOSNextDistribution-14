/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html

package android.icu.number;

import java.text.Format;
import java.math.BigInteger;
import android.icu.math.BigDecimal;
import android.icu.util.Measure;
import android.icu.util.CurrencyAmount;

/**
 * A NumberFormatter that has a locale associated with it; this means .format() methods are available.
 *
 * Instances of this class are immutable and thread-safe.
 *
 * @see android.icu.number.NumberFormatter
 * @see android.icu.number.NumberFormatter
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class LocalizedNumberFormatter extends android.icu.number.NumberFormatterSettings<android.icu.number.LocalizedNumberFormatter> {

LocalizedNumberFormatter() { throw new RuntimeException("Stub!"); }

/**
 * Format the given byte, short, int, or long to a string using the settings specified in the
 * NumberFormatter fluent setting chain.
 *
 * @param input
 *            The number to format.
 * @return A FormattedNumber object; call .toString() to get the string.
 * @see android.icu.number.NumberFormatter
 */

public android.icu.number.FormattedNumber format(long input) { throw new RuntimeException("Stub!"); }

/**
 * Format the given float or double to a string using the settings specified in the NumberFormatter
 * fluent setting chain.
 *
 * @param input
 *            The number to format.
 * @return A FormattedNumber object; call .toString() to get the string.
 * @see android.icu.number.NumberFormatter
 */

public android.icu.number.FormattedNumber format(double input) { throw new RuntimeException("Stub!"); }

/**
 * Format the given {@link java.math.BigInteger BigInteger}, {@link android.icu.math.BigDecimal BigDecimal}, or other {@link java.lang.Number Number} to a string using
 * the settings specified in the NumberFormatter fluent setting chain.
 *
 * @param input
 *            The number to format.
 * @return A FormattedNumber object; call .toString() to get the string.
 * @see android.icu.number.NumberFormatter
 */

public android.icu.number.FormattedNumber format(java.lang.Number input) { throw new RuntimeException("Stub!"); }

/**
 * Format the given {@link android.icu.util.Measure Measure} or {@link android.icu.util.CurrencyAmount CurrencyAmount} to a string using the settings
 * specified in the NumberFormatter fluent setting chain.
 *
 * <p>
 * The unit specified here overrides any unit that may have been specified in the setter chain. This
 * method is intended for cases when each input to the number formatter has a different unit.
 *
 * @param input
 *            The number to format.
 * @return A FormattedNumber object; call .toString() to get the string.
 * @see android.icu.number.NumberFormatter
 */

public android.icu.number.FormattedNumber format(android.icu.util.Measure input) { throw new RuntimeException("Stub!"); }

/**
 * Creates a representation of this LocalizedNumberFormat as a {@link java.text.Format}, enabling the
 * use of this number formatter with APIs that need an object of that type, such as MessageFormat.
 * <p>
 * This API is not intended to be used other than for enabling API compatibility. The {@link #format}
 * methods should normally be used when formatting numbers, not the Format object returned by this
 * method.
 *
 * @return A Format wrapping this LocalizedNumberFormatter.
 * @see android.icu.number.NumberFormatter
 */

public java.text.Format toFormat() { throw new RuntimeException("Stub!"); }
}

