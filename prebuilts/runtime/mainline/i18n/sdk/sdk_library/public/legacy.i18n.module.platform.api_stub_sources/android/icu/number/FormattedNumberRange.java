/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2018 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html

package android.icu.number;

import java.math.BigDecimal;
import java.text.AttributedCharacterIterator;

/**
 * The result of a number range formatting operation. This class allows the result to be exported in several data types,
 * including a String, an AttributedCharacterIterator, and a BigDecimal.
 *
 * Instances of this class are immutable and thread-safe.
 *
 * @author sffc
 * @see android.icu.number.NumberRangeFormatter
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class FormattedNumberRange implements android.icu.text.FormattedValue {

FormattedNumberRange() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.String toString() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public <A extends java.lang.Appendable> A appendTo(A appendable) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public char charAt(int index) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public int length() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.CharSequence subSequence(int start, int end) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public boolean nextPosition(android.icu.text.ConstrainedFieldPosition cfpos) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.text.AttributedCharacterIterator toCharacterIterator() { throw new RuntimeException("Stub!"); }

/**
 * Export the first formatted number as a BigDecimal. This endpoint is useful for obtaining the exact number being
 * printed after scaling and rounding have been applied by the number range formatting pipeline.
 *
 * @return A BigDecimal representation of the first formatted number.
 * @see android.icu.number.NumberRangeFormatter
 * @see #getSecondBigDecimal
 */

public java.math.BigDecimal getFirstBigDecimal() { throw new RuntimeException("Stub!"); }

/**
 * Export the second formatted number as a BigDecimal. This endpoint is useful for obtaining the exact number being
 * printed after scaling and rounding have been applied by the number range formatting pipeline.
 *
 * @return A BigDecimal representation of the second formatted number.
 * @see android.icu.number.NumberRangeFormatter
 * @see #getFirstBigDecimal
 */

public java.math.BigDecimal getSecondBigDecimal() { throw new RuntimeException("Stub!"); }

/**
 * Returns whether the pair of numbers was successfully formatted as a range or whether an identity fallback was
 * used. For example, if the first and second number were the same either before or after rounding occurred, an
 * identity fallback was used.
 *
 * @return A RangeIdentityType indicating the resulting identity situation in the formatted number range.
 * @see android.icu.number.NumberRangeFormatter
 * @see android.icu.number.NumberRangeFormatter.RangeIdentityFallback
 */

public android.icu.number.NumberRangeFormatter.RangeIdentityResult getIdentityResult() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public int hashCode() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public boolean equals(java.lang.Object other) { throw new RuntimeException("Stub!"); }
}

