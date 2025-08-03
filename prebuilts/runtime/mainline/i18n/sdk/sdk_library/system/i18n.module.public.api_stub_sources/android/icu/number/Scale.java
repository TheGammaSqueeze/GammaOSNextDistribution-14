/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2018 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html

package android.icu.number;

import java.math.BigDecimal;

/**
 * A class that defines a quantity by which a number should be multiplied when formatting.
 *
 * <p>
 * To create a Multiplier, use one of the factory methods.
 *
 * @see android.icu.number.NumberFormatter
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class Scale {

private Scale() { throw new RuntimeException("Stub!"); }

/**
 * Do not change the value of numbers when formatting or parsing.
 *
 * @return A Multiplier to prevent any multiplication.
 * @see android.icu.number.NumberFormatter
 */

public static android.icu.number.Scale none() { throw new RuntimeException("Stub!"); }

/**
 * Multiply numbers by 100 before formatting. Useful for combining with a percent unit:
 *
 * <pre>
 * NumberFormatter.with().unit(NoUnit.PERCENT).multiplier(Multiplier.powerOfTen(2))
 * </pre>
 *
 * @return A Multiplier for passing to the setter in NumberFormatter.
 * @see android.icu.number.NumberFormatter
 */

public static android.icu.number.Scale powerOfTen(int power) { throw new RuntimeException("Stub!"); }

/**
 * Multiply numbers by an arbitrary value before formatting. Useful for unit conversions.
 * <p>
 * This method takes a BigDecimal; also see the version that takes a double.
 *
 * @return A Multiplier for passing to the setter in NumberFormatter.
 * @see android.icu.number.NumberFormatter
 */

public static android.icu.number.Scale byBigDecimal(java.math.BigDecimal multiplicand) { throw new RuntimeException("Stub!"); }

/**
 * Multiply numbers by an arbitrary value before formatting. Useful for unit conversions.
 * <p>
 * This method takes a double; also see the version that takes a BigDecimal.
 *
 * @return A Multiplier for passing to the setter in NumberFormatter.
 * @see android.icu.number.NumberFormatter
 */

public static android.icu.number.Scale byDouble(double multiplicand) { throw new RuntimeException("Stub!"); }

/**
 * Multiply a number by both a power of ten and by an arbitrary double value before formatting.
 *
 * @return A Multiplier for passing to the setter in NumberFormatter.
 * @see android.icu.number.NumberFormatter
 */

public static android.icu.number.Scale byDoubleAndPowerOfTen(double multiplicand, int power) { throw new RuntimeException("Stub!"); }
}

