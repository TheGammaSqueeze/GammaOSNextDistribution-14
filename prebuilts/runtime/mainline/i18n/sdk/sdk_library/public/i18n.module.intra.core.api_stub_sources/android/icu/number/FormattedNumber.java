/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html

package android.icu.number;

import java.math.BigDecimal;
import java.text.AttributedCharacterIterator;

/**
 * The result of a number formatting operation. This class allows the result to be exported in several
 * data types, including a String, an AttributedCharacterIterator, and a BigDecimal.
 *
 * Instances of this class are immutable and thread-safe.
 *
 * @see android.icu.number.NumberFormatter
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class FormattedNumber implements android.icu.text.FormattedValue {

FormattedNumber() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.String toString() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public int length() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public char charAt(int index) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.CharSequence subSequence(int start, int end) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public <A extends java.lang.Appendable> A appendTo(A appendable) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public boolean nextPosition(android.icu.text.ConstrainedFieldPosition cfpos) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.text.AttributedCharacterIterator toCharacterIterator() { throw new RuntimeException("Stub!"); }

/**
 * Export the formatted number as a BigDecimal. This endpoint is useful for obtaining the exact
 * number being printed after scaling and rounding have been applied by the number formatting
 * pipeline.
 *
 * @return A BigDecimal representation of the formatted number.
 * @see android.icu.number.NumberFormatter
 */

public java.math.BigDecimal toBigDecimal() { throw new RuntimeException("Stub!"); }
}

