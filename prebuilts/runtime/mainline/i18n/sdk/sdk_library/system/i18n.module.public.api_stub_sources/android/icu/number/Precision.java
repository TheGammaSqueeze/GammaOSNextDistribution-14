/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html

package android.icu.number;

import java.math.BigDecimal;

/**
 * A class that defines the rounding precision to be used when formatting numbers in NumberFormatter.
 *
 * <p>
 * To create a Precision, use one of the factory methods.
 *
 * @see android.icu.number.NumberFormatter
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public abstract class Precision {

Precision() { throw new RuntimeException("Stub!"); }

/**
 * Show all available digits to full precision.
 *
 * <p>
 * <strong>NOTE:</strong> When formatting a <em>double</em>, this method, along with
 * {@link #minFraction} and {@link #minSignificantDigits}, will trigger complex algorithm similar to
 * <em>Dragon4</em> to determine the low-order digits and the number of digits to display based on
 * the value of the double. If the number of fraction places or significant digits can be bounded,
 * consider using {@link #maxFraction} or {@link #maxSignificantDigits} instead to maximize performance.
 * For more information, read the following blog post.
 *
 * <p>
 * http://www.serpentine.com/blog/2011/06/29/here-be-dragons-advances-in-problems-you-didnt-even-know-you-had/
 *
 * @return A Precision for chaining or passing to the NumberFormatter precision() setter.
 * @see android.icu.number.NumberFormatter
 */

public static android.icu.number.Precision unlimited() { throw new RuntimeException("Stub!"); }

/**
 * Show numbers rounded if necessary to the nearest integer.
 *
 * @return A FractionPrecision for chaining or passing to the NumberFormatter precision() setter.
 * @see android.icu.number.NumberFormatter
 */

public static android.icu.number.FractionPrecision integer() { throw new RuntimeException("Stub!"); }

/**
 * Show numbers rounded if necessary to a certain number of fraction places (numerals after the
 * decimal separator). Additionally, pad with zeros to ensure that this number of places are always
 * shown.
 *
 * <p>
 * Example output with minMaxFractionPlaces = 3:
 *
 * <p>
 * 87,650.000<br>
 * 8,765.000<br>
 * 876.500<br>
 * 87.650<br>
 * 8.765<br>
 * 0.876<br>
 * 0.088<br>
 * 0.009<br>
 * 0.000 (zero)
 *
 * <p>
 * This method is equivalent to {@link #minMaxFraction} with both arguments equal.
 *
 * @param minMaxFractionPlaces
 *            The minimum and maximum number of numerals to display after the decimal separator
 *            (rounding if too long or padding with zeros if too short).
 * @return A FractionPrecision for chaining or passing to the NumberFormatter precision() setter.
 * @throws java.lang.IllegalArgumentException if the input number is too big or smaller than 0.
 * @see android.icu.number.NumberFormatter
 */

public static android.icu.number.FractionPrecision fixedFraction(int minMaxFractionPlaces) { throw new RuntimeException("Stub!"); }

/**
 * Always show at least a certain number of fraction places after the decimal separator, padding with
 * zeros if necessary. Do not perform rounding (display numbers to their full precision).
 *
 * <p>
 * <strong>NOTE:</strong> If you are formatting <em>doubles</em>, see the performance note in
 * {@link #unlimited}.
 *
 * @param minFractionPlaces
 *            The minimum number of numerals to display after the decimal separator (padding with
 *            zeros if necessary).
 * @return A FractionPrecision for chaining or passing to the NumberFormatter precision() setter.
 * @throws java.lang.IllegalArgumentException if the input number is too big or smaller than 0.
 * @see android.icu.number.NumberFormatter
 */

public static android.icu.number.FractionPrecision minFraction(int minFractionPlaces) { throw new RuntimeException("Stub!"); }

/**
 * Show numbers rounded if necessary to a certain number of fraction places (numerals after the
 * decimal separator). Unlike the other fraction rounding strategies, this strategy does <em>not</em>
 * pad zeros to the end of the number.
 *
 * @param maxFractionPlaces
 *            The maximum number of numerals to display after the decimal mark (rounding if
 *            necessary).
 * @return A FractionPrecision for chaining or passing to the NumberFormatter precision() setter.
 * @throws java.lang.IllegalArgumentException if the input number is too big or smaller than 0.
 * @see android.icu.number.NumberFormatter
 */

public static android.icu.number.FractionPrecision maxFraction(int maxFractionPlaces) { throw new RuntimeException("Stub!"); }

/**
 * Show numbers rounded if necessary to a certain number of fraction places (numerals after the
 * decimal separator); in addition, always show at least a certain number of places after the decimal
 * separator, padding with zeros if necessary.
 *
 * @param minFractionPlaces
 *            The minimum number of numerals to display after the decimal separator (padding with
 *            zeros if necessary).
 * @param maxFractionPlaces
 *            The maximum number of numerals to display after the decimal separator (rounding if
 *            necessary).
 * @return A FractionPrecision for chaining or passing to the NumberFormatter precision() setter.
 * @throws java.lang.IllegalArgumentException if the input number is too big or smaller than 0.
 * @see android.icu.number.NumberFormatter
 */

public static android.icu.number.FractionPrecision minMaxFraction(int minFractionPlaces, int maxFractionPlaces) { throw new RuntimeException("Stub!"); }

/**
 * Show numbers rounded if necessary to a certain number of significant digits or significant
 * figures. Additionally, pad with zeros to ensure that this number of significant digits/figures are
 * always shown.
 *
 * <p>
 * This method is equivalent to {@link #minMaxSignificantDigits} with both arguments equal.
 *
 * @param minMaxSignificantDigits
 *            The minimum and maximum number of significant digits to display (rounding if too long
 *            or padding with zeros if too short).
 * @return A Precision for chaining or passing to the NumberFormatter precision() setter.
 * @throws java.lang.IllegalArgumentException if the input number is too big or smaller than 1.
 * @see android.icu.number.NumberFormatter
 */

public static android.icu.number.Precision fixedSignificantDigits(int minMaxSignificantDigits) { throw new RuntimeException("Stub!"); }

/**
 * Always show at least a certain number of significant digits/figures, padding with zeros if
 * necessary. Do not perform rounding (display numbers to their full precision).
 *
 * <p>
 * <strong>NOTE:</strong> If you are formatting <em>doubles</em>, see the performance note in
 * {@link #unlimited}.
 *
 * @param minSignificantDigits
 *            The minimum number of significant digits to display (padding with zeros if too short).
 * @return A Precision for chaining or passing to the NumberFormatter precision() setter.
 * @throws java.lang.IllegalArgumentException if the input number is too big or smaller than 1.
 * @see android.icu.number.NumberFormatter
 */

public static android.icu.number.Precision minSignificantDigits(int minSignificantDigits) { throw new RuntimeException("Stub!"); }

/**
 * Show numbers rounded if necessary to a certain number of significant digits/figures.
 *
 * @param maxSignificantDigits
 *            The maximum number of significant digits to display (rounding if too long).
 * @return A Precision for chaining or passing to the NumberFormatter precision() setter.
 * @throws java.lang.IllegalArgumentException if the input number is too big or smaller than 1.
 * @see android.icu.number.NumberFormatter
 */

public static android.icu.number.Precision maxSignificantDigits(int maxSignificantDigits) { throw new RuntimeException("Stub!"); }

/**
 * Show numbers rounded if necessary to a certain number of significant digits/figures; in addition,
 * always show at least a certain number of significant digits, padding with zeros if necessary.
 *
 * @param minSignificantDigits
 *            The minimum number of significant digits to display (padding with zeros if necessary).
 * @param maxSignificantDigits
 *            The maximum number of significant digits to display (rounding if necessary).
 * @return A Precision for chaining or passing to the NumberFormatter precision() setter.
 * @throws java.lang.IllegalArgumentException if the input number is too big or smaller than 1.
 * @see android.icu.number.NumberFormatter
 */

public static android.icu.number.Precision minMaxSignificantDigits(int minSignificantDigits, int maxSignificantDigits) { throw new RuntimeException("Stub!"); }

/**
 * Show numbers rounded if necessary to the closest multiple of a certain rounding increment. For
 * example, if the rounding increment is 0.5, then round 1.2 to 1 and round 1.3 to 1.5.
 *
 * <p>
 * In order to ensure that numbers are padded to the appropriate number of fraction places, set the
 * scale on the rounding increment BigDecimal. For example, to round to the nearest 0.5 and always
 * display 2 numerals after the decimal separator (to display 1.2 as "1.00" and 1.3 as "1.50"), you
 * can run:
 *
 * <pre>
 * Precision.increment(new BigDecimal("0.50"))
 * </pre>
 *
 * <p>
 * For more information on the scale of Java BigDecimal, see {@link java.math.BigDecimal#scale()}.
 *
 * @param roundingIncrement
 *            The increment to which to round numbers.
 * @return A Precision for chaining or passing to the NumberFormatter precision() setter.
 * @throws java.lang.IllegalArgumentException if the rounding increment is null or non-positive.
 * @see android.icu.number.NumberFormatter
 */

public static android.icu.number.Precision increment(java.math.BigDecimal roundingIncrement) { throw new RuntimeException("Stub!"); }

/**
 * Show numbers rounded and padded according to the rules for the currency unit. The most common
 * rounding precision settings for currencies include <code>Precision.fixedFraction(2)</code>,
 * <code>Precision.integer()</code>, and <code>Precision.increment(0.05)</code> for cash transactions
 * ("nickel rounding").
 *
 * <p>
 * The exact rounding details will be resolved at runtime based on the currency unit specified in the
 * NumberFormatter chain. To round according to the rules for one currency while displaying the
 * symbol for another currency, the withCurrency() method can be called on the return value of this
 * method.
 *
 * @param currencyUsage
 *            Either STANDARD (for digital transactions) or CASH (for transactions where the rounding
 *            increment may be limited by the available denominations of cash or coins).
 * @return A CurrencyPrecision for chaining or passing to the NumberFormatter precision() setter.
 * @throws java.lang.IllegalArgumentException if currencyUsage is null.
 * @see android.icu.number.NumberFormatter
 */

public static android.icu.number.CurrencyPrecision currency(android.icu.util.Currency.CurrencyUsage currencyUsage) { throw new RuntimeException("Stub!"); }
}

