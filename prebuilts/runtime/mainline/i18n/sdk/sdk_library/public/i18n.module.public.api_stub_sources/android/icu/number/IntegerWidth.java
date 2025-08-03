/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html

package android.icu.number;


/**
 * A class that defines the strategy for padding and truncating integers before the decimal separator.
 *
 * <p>
 * To create an IntegerWidth, use one of the factory methods.
 *
 * @see android.icu.number.NumberFormatter
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class IntegerWidth {

private IntegerWidth() { throw new RuntimeException("Stub!"); }

/**
 * Pad numbers at the beginning with zeros to guarantee a certain number of numerals before the
 * decimal separator.
 *
 * <p>
 * For example, with minInt=3, the number 55 will get printed as "055".
 *
 * @param minInt
 *            The minimum number of places before the decimal separator.
 * @return An IntegerWidth for chaining or passing to the NumberFormatter integerWidth() setter.
 * @throws java.lang.IllegalArgumentException if the input number is too big or smaller than 0.
 * @see android.icu.number.NumberFormatter
 */

public static android.icu.number.IntegerWidth zeroFillTo(int minInt) { throw new RuntimeException("Stub!"); }

/**
 * Truncate numbers exceeding a certain number of numerals before the decimal separator.
 *
 * For example, with maxInt=3, the number 1234 will get printed as "234".
 *
 * @param maxInt
 *            The maximum number of places before the decimal separator. maxInt == -1 means no
 *            truncation.
 * @return An IntegerWidth for passing to the NumberFormatter integerWidth() setter.
 * @throws java.lang.IllegalArgumentException if the input number is too big or smaller than -1.
 * @see android.icu.number.NumberFormatter
 */

public android.icu.number.IntegerWidth truncateAt(int maxInt) { throw new RuntimeException("Stub!"); }
}

