/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html

package android.icu.number;


/**
 * A class that defines the scientific notation style to be used when formatting numbers in
 * NumberFormatter.
 *
 * <p>
 * To create a ScientificNotation, use one of the factory methods in {@link android.icu.number.Notation Notation}.
 *
 * @see android.icu.number.NumberFormatter
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class ScientificNotation extends android.icu.number.Notation {

ScientificNotation() { throw new RuntimeException("Stub!"); }

/**
 * Sets the minimum number of digits to show in the exponent of scientific notation, padding with
 * zeros if necessary. Useful for fixed-width display.
 *
 * <p>
 * For example, with minExponentDigits=2, the number 123 will be printed as "1.23E02" in
 * <em>en-US</em> instead of the default "1.23E2".
 *
 * @param minExponentDigits
 *            The minimum number of digits to show in the exponent.
 * @return A ScientificNotation, for chaining.
 * @throws java.lang.IllegalArgumentException if minExponentDigits is too big or smaller than 1
 * @see android.icu.number.NumberFormatter
 */

public android.icu.number.ScientificNotation withMinExponentDigits(int minExponentDigits) { throw new RuntimeException("Stub!"); }

/**
 * Sets whether to show the sign on positive and negative exponents in scientific notation. The
 * default is AUTO, showing the minus sign but not the plus sign.
 *
 * <p>
 * For example, with exponentSignDisplay=ALWAYS, the number 123 will be printed as "1.23E+2" in
 * <em>en-US</em> instead of the default "1.23E2".
 *
 * @param exponentSignDisplay
 *            The strategy for displaying the sign in the exponent.
 * @return A ScientificNotation, for chaining.
 * @see android.icu.number.NumberFormatter
 */

public android.icu.number.ScientificNotation withExponentSignDisplay(android.icu.number.NumberFormatter.SignDisplay exponentSignDisplay) { throw new RuntimeException("Stub!"); }
}

