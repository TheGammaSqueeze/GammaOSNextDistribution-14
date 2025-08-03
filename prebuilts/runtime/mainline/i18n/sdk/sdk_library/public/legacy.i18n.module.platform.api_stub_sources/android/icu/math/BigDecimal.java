/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/* Generated from 'BigDecimal.nrx' 8 Sep 2000 11:10:50 [v2.00] */
/* Options: Binary Comments Crossref Format Java Logo Strictargs Strictcase Trace2 Verbose3 */

package android.icu.math;

import java.math.BigInteger;

/**
 * The <code>BigDecimal</code> class implements immutable arbitrary-precision decimal numbers. The methods of the
 * <code>BigDecimal</code> class provide operations for fixed and floating point arithmetic, comparison, format
 * conversions, and hashing.
 * <p>
 * As the numbers are decimal, there is an exact correspondence between an instance of a <code>BigDecimal</code> object
 * and its <code>String</code> representation; the <code>BigDecimal</code> class provides direct conversions to and from
 * <code>String</code> and character array (<code>char[]</code>) objects, as well as conversions to and from the Java
 * primitive types (which may not be exact) and <code>BigInteger</code>.
 * <p>
 * In the descriptions of constructors and methods in this documentation, the value of a <code>BigDecimal</code> number
 * object is shown as the result of invoking the <code>toString()</code> method on the object. The internal
 * representation of a decimal number is neither defined nor exposed, and is not permitted to affect the result of any
 * operation.
 * <p>
 * The floating point arithmetic provided by this class is defined by the ANSI X3.274-1996 standard, and is also
 * documented at <code>http://www2.hursley.ibm.com/decimal</code> <br>
 * <i>[This URL will change.]</i>
 *
 * <h3>Operator methods</h3>
 * <p>
 * Operations on <code>BigDecimal</code> numbers are controlled by a {@link android.icu.math.MathContext MathContext} object, which provides the
 * context (precision and other information) for the operation. Methods that can take a <code>MathContext</code>
 * parameter implement the standard arithmetic operators for <code>BigDecimal</code> objects and are known as
 * <i>operator methods</i>. The default settings provided by the constant {@link android.icu.math.MathContext#DEFAULT MathContext#DEFAULT} (<code>digits=9,
 * form=SCIENTIFIC, lostDigits=false, roundingMode=ROUND_HALF_UP</code>) perform general-purpose floating point
 * arithmetic to nine digits of precision. The <code>MathContext</code> parameter must not be <code>null</code>.
 * <p>
 * Each operator method also has a version provided which does not take a <code>MathContext</code> parameter. For this
 * version of each method, the context settings used are <code>digits=0,
 * form=PLAIN, lostDigits=false, roundingMode=ROUND_HALF_UP</code>; these settings perform fixed point arithmetic with
 * unlimited precision, as defined for the original BigDecimal class in Java 1.1 and Java 1.2.
 * <p>
 * For monadic operators, only the optional <code>MathContext</code> parameter is present; the operation acts upon the
 * current object.
 * <p>
 * For dyadic operators, a <code>BigDecimal</code> parameter is always present; it must not be <code>null</code>. The
 * operation acts with the current object being the left-hand operand and the <code>BigDecimal</code> parameter being
 * the right-hand operand.
 * <p>
 * For example, adding two <code>BigDecimal</code> objects referred to by the names <code>award</code> and
 * <code>extra</code> could be written as any of:
 * <p>
 * <code>
 *     award.add(extra)
 * <br>award.add(extra, MathContext.DEFAULT)
 * <br>award.add(extra, acontext)
 * </code>
 * <p>
 * (where <code>acontext</code> is a <code>MathContext</code> object), which would return a <code>BigDecimal</code>
 * object whose value is the result of adding <code>award</code> and <code>extra</code> under the appropriate context
 * settings.
 * <p>
 * When a <code>BigDecimal</code> operator method is used, a set of rules define what the result will be (and, by
 * implication, how the result would be represented as a character string). These rules are defined in the BigDecimal
 * arithmetic documentation (see the URL above), but in summary:
 * <ul>
 * <li>Results are normally calculated with up to some maximum number of significant digits. For example, if the
 * <code>MathContext</code> parameter for an operation were <code>MathContext.DEFAULT</code> then the result would be
 * rounded to 9 digits; the division of 2 by 3 would then result in 0.666666667. <br>
 * You can change the default of 9 significant digits by providing the method with a suitable <code>MathContext</code>
 * object. This lets you calculate using as many digits as you need -- thousands, if necessary. Fixed point (scaled)
 * arithmetic is indicated by using a <code>digits</code> setting of 0 (or omitting the <code>MathContext</code>
 * parameter). <br>
 * Similarly, you can change the algorithm used for rounding from the default "classic" algorithm.
 * <li>
 * In standard arithmetic (that is, when the <code>form</code> setting is not <code>PLAIN</code>), a zero result is
 * always expressed as the single digit <code>'0'</code> (that is, with no sign, decimal point, or exponent part).
 * <li>
 * Except for the division and power operators in standard arithmetic, trailing zeros are preserved (this is in contrast
 * to binary floating point operations and most electronic calculators, which lose the information about trailing zeros
 * in the fractional part of results). <br>
 * So, for example:
 * <p>
 * <code>
 *     new BigDecimal("2.40").add(     new BigDecimal("2"))      =&gt; "4.40"
 * <br>new BigDecimal("2.40").subtract(new BigDecimal("2"))      =&gt; "0.40"
 * <br>new BigDecimal("2.40").multiply(new BigDecimal("2"))      =&gt; "4.80"
 * <br>new BigDecimal("2.40").divide(  new BigDecimal("2"), def) =&gt; "1.2"
 * </code>
 * <p>
 * where the value on the right of the <code>=&gt;</code> would be the result of the operation, expressed as a
 * <code>String</code>, and <code>def</code> (in this and following examples) refers to <code>MathContext.DEFAULT</code>
 * ). This preservation of trailing zeros is desirable for most calculations (including financial calculations). If
 * necessary, trailing zeros may be easily removed using division by 1.
 * <li>
 * In standard arithmetic, exponential form is used for a result depending on its value and the current setting of
 * <code>digits</code> (the default is 9 digits). If the number of places needed before the decimal point exceeds the
 * <code>digits</code> setting, or the absolute value of the number is less than <code>0.000001</code>, then the number
 * will be expressed in exponential notation; thus
 * <p>
 * <code>
 *   new BigDecimal("1e+6").multiply(new BigDecimal("1e+6"), def)
 * </code>
 * <p>
 * results in <code>1E+12</code> instead of <code>1000000000000</code>, and
 * <p>
 * <code>
 *   new BigDecimal("1").divide(new BigDecimal("3E+10"), def)
 * </code>
 * <p>
 * results in <code>3.33333333E-11</code> instead of <code>0.0000000000333333333</code>.
 * <p>
 * The form of the exponential notation (scientific or engineering) is determined by the <code>form</code> setting.
 * </ul>
 * <p>
 * The names of methods in this class follow the conventions established by <code>java.lang.Number</code>,
 * <code>java.math.BigInteger</code>, and <code>java.math.BigDecimal</code> in Java 1.1 and Java 1.2.
 *
 * @see android.icu.math.MathContext
 * @author Mike Cowlishaw
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class BigDecimal extends java.lang.Number implements java.io.Serializable, java.lang.Comparable<android.icu.math.BigDecimal> {

/**
 * Constructs a <code>BigDecimal</code> object from a <code>java.math.BigDecimal</code>.
 * <p>
 * Constructs a <code>BigDecimal</code> as though the parameter had been represented as a <code>String</code> (using
 * its <code>toString</code> method) and the {@link #BigDecimal(java.lang.String)} constructor had then been used.
 * The parameter must not be <code>null</code>.
 * <p>
 * <i>(Note: this constructor is provided only in the <code>android.icu.math</code> version of the BigDecimal class.
 * It would not be present in a <code>java.math</code> version.)</i>
 *
 * @param bd The <code>BigDecimal</code> to be translated.
 */

public BigDecimal(java.math.BigDecimal bd) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>BigDecimal</code> object from a <code>BigInteger</code>, with scale 0.
 * <p>
 * Constructs a <code>BigDecimal</code> which is the exact decimal representation of the <code>BigInteger</code>,
 * with a scale of zero. The value of the <code>BigDecimal</code> is identical to the value of the <code>BigInteger
 * </code>. The parameter must not be <code>null</code>.
 * <p>
 * The <code>BigDecimal</code> will contain only decimal digits, prefixed with a leading minus sign (hyphen) if the
 * <code>BigInteger</code> is negative. A leading zero will be present only if the <code>BigInteger</code> is zero.
 *
 * @param bi The <code>BigInteger</code> to be converted.
 */

public BigDecimal(java.math.BigInteger bi) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>BigDecimal</code> object from a <code>BigInteger</code> and a scale.
 * <p>
 * Constructs a <code>BigDecimal</code> which is the exact decimal representation of the <code>BigInteger</code>,
 * scaled by the second parameter, which may not be negative. The value of the <code>BigDecimal</code> is the <code>
 * BigInteger</code> divided by ten to the power of the scale. The <code>BigInteger</code> parameter must not be
 * <code>null</code>.
 * <p>
 * The <code>BigDecimal</code> will contain only decimal digits, (with an embedded decimal point followed by <code>
 * scale</code> decimal digits if the scale is positive), prefixed with a leading minus sign (hyphen) if the <code>
 * BigInteger</code> is negative. A leading zero will be present only if the <code>BigInteger</code> is zero.
 *
 * @param bi The <code>BigInteger</code> to be converted.
 * @param scale The <code>int</code> specifying the scale.
 * @throws java.lang.NumberFormatException If the scale is negative.
 */

public BigDecimal(java.math.BigInteger bi, int scale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>BigDecimal</code> object from an array of characters.
 * <p>
 * Constructs a <code>BigDecimal</code> as though a <code>String</code> had been constructed from the character
 * array and the {@link #BigDecimal(java.lang.String)} constructor had then been used. The parameter must not be
 * <code>null</code>.
 * <p>
 * Using this constructor is faster than using the <code>BigDecimal(String)</code> constructor if the string is
 * already available in character array form.
 *
 * @param inchars The <code>char[]</code> array containing the number to be converted.
 * @throws java.lang.NumberFormatException If the parameter is not a valid number.
 */

public BigDecimal(char[] inchars) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>BigDecimal</code> object from an array of characters.
 * <p>
 * Constructs a <code>BigDecimal</code> as though a <code>String</code> had been constructed from the character
 * array (or a subarray of that array) and the {@link #BigDecimal(java.lang.String)} constructor had then been used.
 * The first parameter must not be <code>null</code>, and the subarray must be wholly contained within it.
 * <p>
 * Using this constructor is faster than using the <code>BigDecimal(String)</code> constructor if the string is
 * already available within a character array.
 *
 * @param inchars The <code>char[]</code> array containing the number to be converted.
 * @param offset The <code>int</code> offset into the array of the start of the number to be converted.
 * @param length The <code>int</code> length of the number.
 * @throws java.lang.NumberFormatException If the parameter is not a valid number for any reason.
 */

public BigDecimal(char[] inchars, int offset, int length) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>BigDecimal</code> object directly from a <code>double</code>.
 * <p>
 * Constructs a <code>BigDecimal</code> which is the exact decimal representation of the 64-bit signed binary
 * floating point parameter.
 * <p>
 * Note that this constructor it an exact conversion; it does not give the same result as converting <code>num
 * </code> to a <code>String</code> using the <code>Double.toString()</code> method and then using the
 * {@link #BigDecimal(java.lang.String)} constructor. To get that result, use the static {@link #valueOf(double)}
 * method to construct a <code>BigDecimal</code> from a <code>double</code>.
 *
 * @param num The <code>double</code> to be converted.
 * @throws java.lang.NumberFormatException If the parameter is infinite or not a number.
 */

public BigDecimal(double num) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>BigDecimal</code> object directly from a <code>int</code>.
 * <p>
 * Constructs a <code>BigDecimal</code> which is the exact decimal representation of the 32-bit signed binary
 * integer parameter. The <code>BigDecimal</code> will contain only decimal digits, prefixed with a leading minus
 * sign (hyphen) if the parameter is negative. A leading zero will be present only if the parameter is zero.
 *
 * @param num The <code>int</code> to be converted.
 */

public BigDecimal(int num) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>BigDecimal</code> object directly from a <code>long</code>.
 * <p>
 * Constructs a <code>BigDecimal</code> which is the exact decimal representation of the 64-bit signed binary
 * integer parameter. The <code>BigDecimal</code> will contain only decimal digits, prefixed with a leading minus
 * sign (hyphen) if the parameter is negative. A leading zero will be present only if the parameter is zero.
 *
 * @param num The <code>long</code> to be converted.
 */

public BigDecimal(long num) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>BigDecimal</code> object from a <code>String</code>.
 * <p>
 * Constructs a <code>BigDecimal</code> from the parameter, which must not be <code>null</code> and must represent a
 * valid <i>number</i>, as described formally in the documentation referred to {@link android.icu.math.BigDecimal above}.
 * <p>
 * In summary, numbers in <code>String</code> form must have at least one digit, may have a leading sign, may have a
 * decimal point, and exponential notation may be used. They follow conventional syntax, and may not contain blanks.
 * <p>
 * Some valid strings from which a <code>BigDecimal</code> might be constructed are:
 *
 * <pre>
 *
 * "0" -- Zero "12" -- A whole number "-76" -- A signed whole number "12.70" -- Some decimal places "+0.003" -- Plus
 * sign is allowed "17." -- The same as 17 ".5" -- The same as 0.5 "4E+9" -- Exponential notation "0.73e-7" --
 * Exponential notation
 *
 * </pre>
 * <p>
 * (Exponential notation means that the number includes an optional sign and a power of ten following an
 * '<code>E</code>' that indicates how the decimal point will be shifted. Thus the <code>"4E+9"</code> above is
 * just a short way of writing <code>4000000000</code>, and the <code>"0.73e-7"</code> is short for <code>
 * 0.000000073</code>.)
 * <p>
 * The <code>BigDecimal</code> constructed from the String is in a standard form, with no blanks, as though the
 * {@link #add(android.icu.math.BigDecimal)} method had been used to add zero to the number with unlimited precision. If the string
 * uses exponential notation (that is, includes an <code>e</code> or an <code>E</code>), then the <code>BigDecimal
 * </code> number will be expressed in scientific notation (where the power of ten is adjusted so there is a single
 * non-zero digit to the left of the decimal point); in this case if the number is zero then it will be expressed as
 * the single digit 0, and if non-zero it will have an exponent unless that exponent would be 0. The exponent must
 * fit in nine digits both before and after it is expressed in scientific notation.
 * <p>
 * Any digits in the parameter must be decimal; that is, <code>Character.digit(c, 10)</code> (where <code>c</code>
 * is the character in question) would not return -1.
 *
 * @param string The <code>String</code> to be converted.
 * @throws java.lang.NumberFormatException If the parameter is not a valid number.
 */

public BigDecimal(java.lang.String string) { throw new RuntimeException("Stub!"); }

/**
 * Returns a plain <code>BigDecimal</code> whose value is the absolute value of this <code>BigDecimal</code>.
 * <p>
 * The same as {@link #abs(android.icu.math.MathContext)}, where the context is <code>new MathContext(0, MathContext.PLAIN)</code>.
 * <p>
 * The length of the decimal part (the scale) of the result will be <code>this.scale()</code>
 *
 * @return A <code>BigDecimal</code> whose value is the absolute value of this <code>BigDecimal</code>.
 */

public android.icu.math.BigDecimal abs() { throw new RuntimeException("Stub!"); }

/**
 * Returns a <code>BigDecimal</code> whose value is the absolute value of this <code>BigDecimal</code>.
 * <p>
 * If the current object is zero or positive, then the same result as invoking the {@link #plus(android.icu.math.MathContext)} method
 * with the same parameter is returned. Otherwise, the same result as invoking the {@link #negate(android.icu.math.MathContext)}
 * method with the same parameter is returned.
 *
 * @param set The <code>MathContext</code> arithmetic settings.
 * @return A <code>BigDecimal</code> whose value is the absolute value of this <code>BigDecimal</code>.
 */

public android.icu.math.BigDecimal abs(android.icu.math.MathContext set) { throw new RuntimeException("Stub!"); }

/**
 * Returns a plain <code>BigDecimal</code> whose value is <code>this+rhs</code>, using fixed point arithmetic.
 * <p>
 * The same as {@link #add(android.icu.math.BigDecimal,android.icu.math.MathContext)}, where the <code>BigDecimal</code> is <code>rhs</code>, and the
 * context is <code>new MathContext(0, MathContext.PLAIN)</code>.
 * <p>
 * The length of the decimal part (the scale) of the result will be the maximum of the scales of the two operands.
 *
 * @param rhs The <code>BigDecimal</code> for the right hand side of the addition.
 * @return A <code>BigDecimal</code> whose value is <code>this+rhs</code>, using fixed point arithmetic.
 */

public android.icu.math.BigDecimal add(android.icu.math.BigDecimal rhs) { throw new RuntimeException("Stub!"); }

/**
 * Returns a <code>BigDecimal</code> whose value is <code>this+rhs</code>.
 * <p>
 * Implements the addition (<b><code>+</code></b>) operator (as defined in the decimal documentation, see
 * {@link android.icu.math.BigDecimal class header}), and returns the result as a <code>BigDecimal</code> object.
 *
 * @param rhs The <code>BigDecimal</code> for the right hand side of the addition.
 * @param set The <code>MathContext</code> arithmetic settings.
 * @return A <code>BigDecimal</code> whose value is <code>this+rhs</code>.
 */

public android.icu.math.BigDecimal add(android.icu.math.BigDecimal rhs, android.icu.math.MathContext set) { throw new RuntimeException("Stub!"); }

/**
 * Compares this <code>BigDecimal</code> to another, using unlimited precision.
 * <p>
 * The same as {@link #compareTo(android.icu.math.BigDecimal,android.icu.math.MathContext)}, where the <code>BigDecimal</code> is <code>rhs</code>,
 * and the context is <code>new MathContext(0, MathContext.PLAIN)</code>.
 *
 * @param rhs The <code>BigDecimal</code> for the right hand side of the comparison.
 * @return An <code>int</code> whose value is -1, 0, or 1 as <code>this</code> is numerically less than, equal to,
 *         or greater than <code>rhs</code>.
 */

public int compareTo(android.icu.math.BigDecimal rhs) { throw new RuntimeException("Stub!"); }

/**
 * Compares this <code>BigDecimal</code> to another.
 * <p>
 * Implements numeric comparison, (as defined in the decimal documentation, see {@link android.icu.math.BigDecimal class header}),
 * and returns a result of type <code>int</code>.
 * <p>
 * The result will be:
 * <table cellpadding=2>
 * <tr>
 * <td align=right><b>-1</b></td> <td>if the current object is less than the first parameter</td>
 * </tr>
 * <tr>
 * <td align=right><b>0</b></td> <td>if the current object is equal to the first parameter</td>
 * </tr>
 * <tr>
 * <td align=right><b>1</b></td> <td>if the current object is greater than the first parameter.</td>
 * </tr>
 * </table>
 * <p>
 * A {@link #compareTo(android.icu.math.BigDecimal)} method is also provided.
 *
 * @param rhs The <code>BigDecimal</code> for the right hand side of the comparison.
 * @param set The <code>MathContext</code> arithmetic settings.
 * @return An <code>int</code> whose value is -1, 0, or 1 as <code>this</code> is numerically less than, equal to,
 *         or greater than <code>rhs</code>.
 */

public int compareTo(android.icu.math.BigDecimal rhs, android.icu.math.MathContext set) { throw new RuntimeException("Stub!"); }

/**
 * Returns a plain <code>BigDecimal</code> whose value is <code>this/rhs</code>, using fixed point arithmetic.
 * <p>
 * The same as {@link #divide(android.icu.math.BigDecimal,int)}, where the <code>BigDecimal</code> is <code>rhs</code>, and the
 * rounding mode is {@link android.icu.math.MathContext#ROUND_HALF_UP MathContext#ROUND_HALF_UP}.
 *
 * The length of the decimal part (the scale) of the result will be the same as the scale of the current object, if
 * the latter were formatted without exponential notation.
 *
 * @param rhs The <code>BigDecimal</code> for the right hand side of the division.
 * @return A plain <code>BigDecimal</code> whose value is <code>this/rhs</code>, using fixed point arithmetic.
 * @throws java.lang.ArithmeticException If <code>rhs</code> is zero.
 */

public android.icu.math.BigDecimal divide(android.icu.math.BigDecimal rhs) { throw new RuntimeException("Stub!"); }

/**
 * Returns a plain <code>BigDecimal</code> whose value is <code>this/rhs</code>, using fixed point arithmetic and a
 * rounding mode.
 * <p>
 * The same as {@link #divide(android.icu.math.BigDecimal,int,int)}, where the <code>BigDecimal</code> is <code>rhs</code>, and the
 * second parameter is <code>this.scale()</code>, and the third is <code>round</code>.
 * <p>
 * The length of the decimal part (the scale) of the result will therefore be the same as the scale of the current
 * object, if the latter were formatted without exponential notation.
 * <p>
 *
 * @param rhs The <code>BigDecimal</code> for the right hand side of the division.
 * @param round The <code>int</code> rounding mode to be used for the division (see the {@link android.icu.math.MathContext MathContext} class).
 * @return A plain <code>BigDecimal</code> whose value is <code>this/rhs</code>, using fixed point arithmetic and
 *         the specified rounding mode.
 * @throws java.lang.IllegalArgumentException if <code>round</code> is not a valid rounding mode.
 * @throws java.lang.ArithmeticException if <code>rhs</code> is zero.
 * @throws java.lang.ArithmeticException if <code>round</code> is {@link android.icu.math.MathContext#ROUND_UNNECESSARY MathContext#ROUND_UNNECESSARY} and <code>this.scale()</code> is insufficient to represent the result exactly.
 */

public android.icu.math.BigDecimal divide(android.icu.math.BigDecimal rhs, int round) { throw new RuntimeException("Stub!"); }

/**
 * Returns a plain <code>BigDecimal</code> whose value is <code>this/rhs</code>, using fixed point arithmetic and a
 * given scale and rounding mode.
 * <p>
 * The same as {@link #divide(android.icu.math.BigDecimal,android.icu.math.MathContext)}, where the <code>BigDecimal</code> is <code>rhs</code>,
 * <code>new MathContext(0, MathContext.PLAIN, false, round)</code>, except that the length of the decimal part (the
 * scale) to be used for the result is explicit rather than being taken from <code>this</code>.
 * <p>
 * The length of the decimal part (the scale) of the result will be the same as the scale of the current object, if
 * the latter were formatted without exponential notation.
 * <p>
 *
 * @param rhs The <code>BigDecimal</code> for the right hand side of the division.
 * @param scale The <code>int</code> scale to be used for the result.
 * @param round The <code>int</code> rounding mode to be used for the division (see the {@link android.icu.math.MathContext MathContext} class).
 * @return A plain <code>BigDecimal</code> whose value is <code>this/rhs</code>, using fixed point arithmetic and
 *         the specified rounding mode.
 * @throws java.lang.IllegalArgumentException if <code>round</code> is not a valid rounding mode.
 * @throws java.lang.ArithmeticException if <code>rhs</code> is zero.
 * @throws java.lang.ArithmeticException if <code>scale</code> is negative.
 * @throws java.lang.ArithmeticException if <code>round</code> is {@link android.icu.math.MathContext#ROUND_UNNECESSARY MathContext#ROUND_UNNECESSARY} and <code>scale</code> is insufficient
 *             to represent the result exactly.
 */

public android.icu.math.BigDecimal divide(android.icu.math.BigDecimal rhs, int scale, int round) { throw new RuntimeException("Stub!"); }

/**
 * Returns a <code>BigDecimal</code> whose value is <code>this/rhs</code>.
 * <p>
 * Implements the division (<b><code>/</code></b>) operator (as defined in the decimal documentation, see
 * {@link android.icu.math.BigDecimal class header}), and returns the result as a <code>BigDecimal</code> object.
 *
 * @param rhs The <code>BigDecimal</code> for the right hand side of the division.
 * @param set The <code>MathContext</code> arithmetic settings.
 * @return A <code>BigDecimal</code> whose value is <code>this/rhs</code>.
 * @throws java.lang.ArithmeticException if <code>rhs</code> is zero.
 */

public android.icu.math.BigDecimal divide(android.icu.math.BigDecimal rhs, android.icu.math.MathContext set) { throw new RuntimeException("Stub!"); }

/**
 * Returns a plain <code>BigDecimal</code> whose value is the integer part of <code>this/rhs</code>.
 * <p>
 * The same as {@link #divideInteger(android.icu.math.BigDecimal,android.icu.math.MathContext)}, where the <code>BigDecimal</code> is <code>rhs
 * </code>, and the context is <code>new MathContext(0, MathContext.PLAIN)</code>.
 *
 * @param rhs The <code>BigDecimal</code> for the right hand side of the integer division.
 * @return A <code>BigDecimal</code> whose value is the integer part of <code>this/rhs</code>.
 * @throws java.lang.ArithmeticException if <code>rhs</code> is zero.
 */

public android.icu.math.BigDecimal divideInteger(android.icu.math.BigDecimal rhs) { throw new RuntimeException("Stub!"); }

/**
 * Returns a <code>BigDecimal</code> whose value is the integer part of <code>this/rhs</code>.
 * <p>
 * Implements the integer division operator (as defined in the decimal documentation, see {@link android.icu.math.BigDecimal  header}), and returns the result as a <code>BigDecimal</code> object.
 *
 * @param rhs The <code>BigDecimal</code> for the right hand side of the integer division.
 * @param set The <code>MathContext</code> arithmetic settings.
 * @return A <code>BigDecimal</code> whose value is the integer part of <code>this/rhs</code>.
 * @throws java.lang.ArithmeticException if <code>rhs</code> is zero.
 * @throws java.lang.ArithmeticException if the result will not fit in the number of digits specified for the context.
 */

public android.icu.math.BigDecimal divideInteger(android.icu.math.BigDecimal rhs, android.icu.math.MathContext set) { throw new RuntimeException("Stub!"); }

/**
 * Returns a plain <code>BigDecimal</code> whose value is the maximum of <code>this</code> and <code>rhs</code>.
 * <p>
 * The same as {@link #max(android.icu.math.BigDecimal,android.icu.math.MathContext)}, where the <code>BigDecimal</code> is <code>rhs</code>, and the
 * context is <code>new MathContext(0, MathContext.PLAIN)</code>.
 *
 * @param rhs The <code>BigDecimal</code> for the right hand side of the comparison.
 * @return A <code>BigDecimal</code> whose value is the maximum of <code>this</code> and <code>rhs</code>.
 */

public android.icu.math.BigDecimal max(android.icu.math.BigDecimal rhs) { throw new RuntimeException("Stub!"); }

/**
 * Returns a <code>BigDecimal</code> whose value is the maximum of <code>this</code> and <code>rhs</code>.
 * <p>
 * Returns the larger of the current object and the first parameter.
 * <p>
 * If calling the {@link #compareTo(android.icu.math.BigDecimal,android.icu.math.MathContext)} method with the same parameters would return <code>1
 * </code> or <code>0</code>, then the result of calling the {@link #plus(android.icu.math.MathContext)} method on the current object
 * (using the same <code>MathContext</code> parameter) is returned. Otherwise, the result of calling the
 * {@link #plus(android.icu.math.MathContext)} method on the first parameter object (using the same <code>MathContext</code>
 * parameter) is returned.
 *
 * @param rhs The <code>BigDecimal</code> for the right hand side of the comparison.
 * @param set The <code>MathContext</code> arithmetic settings.
 * @return A <code>BigDecimal</code> whose value is the maximum of <code>this</code> and <code>rhs</code>.
 */

public android.icu.math.BigDecimal max(android.icu.math.BigDecimal rhs, android.icu.math.MathContext set) { throw new RuntimeException("Stub!"); }

/**
 * Returns a plain <code>BigDecimal</code> whose value is the minimum of <code>this</code> and <code>rhs</code>.
 * <p>
 * The same as {@link #min(android.icu.math.BigDecimal,android.icu.math.MathContext)}, where the <code>BigDecimal</code> is <code>rhs</code>, and the
 * context is <code>new MathContext(0, MathContext.PLAIN)</code>.
 *
 * @param rhs The <code>BigDecimal</code> for the right hand side of the comparison.
 * @return A <code>BigDecimal</code> whose value is the minimum of <code>this</code> and <code>rhs</code>.
 */

public android.icu.math.BigDecimal min(android.icu.math.BigDecimal rhs) { throw new RuntimeException("Stub!"); }

/**
 * Returns a <code>BigDecimal</code> whose value is the minimum of <code>this</code> and <code>rhs</code>.
 * <p>
 * Returns the smaller of the current object and the first parameter.
 * <p>
 * If calling the {@link #compareTo(android.icu.math.BigDecimal,android.icu.math.MathContext)} method with the same parameters would return <code>-1
 * </code> or <code>0</code>, then the result of calling the {@link #plus(android.icu.math.MathContext)} method on the current object
 * (using the same <code>MathContext</code> parameter) is returned. Otherwise, the result of calling the
 * {@link #plus(android.icu.math.MathContext)} method on the first parameter object (using the same <code>MathContext</code>
 * parameter) is returned.
 *
 * @param rhs The <code>BigDecimal</code> for the right hand side of the comparison.
 * @param set The <code>MathContext</code> arithmetic settings.
 * @return A <code>BigDecimal</code> whose value is the minimum of <code>this</code> and <code>rhs</code>.
 */

public android.icu.math.BigDecimal min(android.icu.math.BigDecimal rhs, android.icu.math.MathContext set) { throw new RuntimeException("Stub!"); }

/**
 * Returns a plain <code>BigDecimal</code> whose value is <code>this*rhs</code>, using fixed point arithmetic.
 * <p>
 * The same as {@link #add(android.icu.math.BigDecimal,android.icu.math.MathContext)}, where the <code>BigDecimal</code> is <code>rhs</code>, and the
 * context is <code>new MathContext(0, MathContext.PLAIN)</code>.
 * <p>
 * The length of the decimal part (the scale) of the result will be the sum of the scales of the operands, if they
 * were formatted without exponential notation.
 *
 * @param rhs The <code>BigDecimal</code> for the right hand side of the multiplication.
 * @return A <code>BigDecimal</code> whose value is <code>this*rhs</code>, using fixed point arithmetic.
 */

public android.icu.math.BigDecimal multiply(android.icu.math.BigDecimal rhs) { throw new RuntimeException("Stub!"); }

/**
 * Returns a <code>BigDecimal</code> whose value is <code>this*rhs</code>.
 * <p>
 * Implements the multiplication (<b><code>&#42;</code></b>) operator (as defined in the decimal documentation, see
 * {@link android.icu.math.BigDecimal class header}), and returns the result as a <code>BigDecimal</code> object.
 *
 * @param rhs The <code>BigDecimal</code> for the right hand side of the multiplication.
 * @param set The <code>MathContext</code> arithmetic settings.
 * @return A <code>BigDecimal</code> whose value is <code>this*rhs</code>.
 */

public android.icu.math.BigDecimal multiply(android.icu.math.BigDecimal rhs, android.icu.math.MathContext set) { throw new RuntimeException("Stub!"); }

/**
 * Returns a plain <code>BigDecimal</code> whose value is <code>-this</code>.
 * <p>
 * The same as {@link #negate(android.icu.math.MathContext)}, where the context is <code>new MathContext(0, MathContext.PLAIN)</code>
 * .
 * <p>
 * The length of the decimal part (the scale) of the result will be be <code>this.scale()</code>
 *
 *
 * @return A <code>BigDecimal</code> whose value is <code>-this</code>.
 */

public android.icu.math.BigDecimal negate() { throw new RuntimeException("Stub!"); }

/**
 * Returns a <code>BigDecimal</code> whose value is <code>-this</code>.
 * <p>
 * Implements the negation (Prefix <b><code>-</code></b>) operator (as defined in the decimal documentation, see
 * {@link android.icu.math.BigDecimal class header}), and returns the result as a <code>BigDecimal</code> object.
 *
 * @param set The <code>MathContext</code> arithmetic settings.
 * @return A <code>BigDecimal</code> whose value is <code>-this</code>.
 */

public android.icu.math.BigDecimal negate(android.icu.math.MathContext set) { throw new RuntimeException("Stub!"); }

/**
 * Returns a plain <code>BigDecimal</code> whose value is <code>+this</code>. Note that <code>this</code> is not
 * necessarily a plain <code>BigDecimal</code>, but the result will always be.
 * <p>
 * The same as {@link #plus(android.icu.math.MathContext)}, where the context is <code>new MathContext(0, MathContext.PLAIN)</code>.
 * <p>
 * The length of the decimal part (the scale) of the result will be be <code>this.scale()</code>
 *
 * @return A <code>BigDecimal</code> whose value is <code>+this</code>.
 */

public android.icu.math.BigDecimal plus() { throw new RuntimeException("Stub!"); }

/**
 * Returns a <code>BigDecimal</code> whose value is <code>+this</code>.
 * <p>
 * Implements the plus (Prefix <b><code>+</code></b>) operator (as defined in the decimal documentation, see
 * {@link android.icu.math.BigDecimal class header}), and returns the result as a <code>BigDecimal</code> object.
 * <p>
 * This method is useful for rounding or otherwise applying a context to a decimal value.
 *
 * @param set The <code>MathContext</code> arithmetic settings.
 * @return A <code>BigDecimal</code> whose value is <code>+this</code>.
 */

public android.icu.math.BigDecimal plus(android.icu.math.MathContext set) { throw new RuntimeException("Stub!"); }

/**
 * Returns a plain <code>BigDecimal</code> whose value is <code>this**rhs</code>, using fixed point arithmetic.
 * <p>
 * The same as {@link #pow(android.icu.math.BigDecimal,android.icu.math.MathContext)}, where the <code>BigDecimal</code> is <code>rhs</code>, and the
 * context is <code>new MathContext(0, MathContext.PLAIN)</code>.
 * <p>
 * The parameter is the power to which the <code>this</code> will be raised; it must be in the range 0 through
 * 999999999, and must have a decimal part of zero. Note that these restrictions may be removed in the future, so
 * they should not be used as a test for a whole number.
 * <p>
 * In addition, the power must not be negative, as no <code>MathContext</code> is used and so the result would then
 * always be 0.
 *
 * @param rhs The <code>BigDecimal</code> for the right hand side of the operation (the power).
 * @return A <code>BigDecimal</code> whose value is <code>this**rhs</code>, using fixed point arithmetic.
 * @throws java.lang.ArithmeticException if <code>rhs</code> is out of range or is not a whole number.
 */

public android.icu.math.BigDecimal pow(android.icu.math.BigDecimal rhs) { throw new RuntimeException("Stub!"); }

/**
 * Returns a <code>BigDecimal</code> whose value is <code>this**rhs</code>.
 * <p>
 * Implements the power (<b><code>^</code></b>) operator (as defined in the decimal documentation, see
 * {@link android.icu.math.BigDecimal class header}), and returns the result as a <code>BigDecimal</code> object.
 * <p>
 * The first parameter is the power to which the <code>this</code> will be raised; it must be in the range
 * -999999999 through 999999999, and must have a decimal part of zero. Note that these restrictions may be removed
 * in the future, so they should not be used as a test for a whole number.
 * <p>
 * If the <code>digits</code> setting of the <code>MathContext</code> parameter is 0, the power must be zero or
 * positive.
 *
 * @param rhs The <code>BigDecimal</code> for the right hand side of the operation (the power).
 * @param set The <code>MathContext</code> arithmetic settings.
 * @return A <code>BigDecimal</code> whose value is <code>this**rhs</code>.
 * @throws java.lang.ArithmeticException if <code>rhs</code> is out of range or is not a whole number.
 */

public android.icu.math.BigDecimal pow(android.icu.math.BigDecimal rhs, android.icu.math.MathContext set) { throw new RuntimeException("Stub!"); }

/**
 * Returns a plain <code>BigDecimal</code> whose value is the remainder of <code>this/rhs</code>, using fixed point
 * arithmetic.
 * <p>
 * The same as {@link #remainder(android.icu.math.BigDecimal,android.icu.math.MathContext)}, where the <code>BigDecimal</code> is <code>rhs</code>,
 * and the context is <code>new MathContext(0, MathContext.PLAIN)</code>.
 * <p>
 * This is not the modulo operator -- the result may be negative.
 *
 * @param rhs The <code>BigDecimal</code> for the right hand side of the remainder operation.
 * @return A <code>BigDecimal</code> whose value is the remainder of <code>this/rhs</code>, using fixed point
 *         arithmetic.
 * @throws java.lang.ArithmeticException if <code>rhs</code> is zero.
 */

public android.icu.math.BigDecimal remainder(android.icu.math.BigDecimal rhs) { throw new RuntimeException("Stub!"); }

/**
 * Returns a <code>BigDecimal</code> whose value is the remainder of <code>this/rhs</code>.
 * <p>
 * Implements the remainder operator (as defined in the decimal documentation, see {@link android.icu.math.BigDecimal class header}),
 * and returns the result as a <code>BigDecimal</code> object.
 * <p>
 * This is not the modulo operator -- the result may be negative.
 *
 * @param rhs The <code>BigDecimal</code> for the right hand side of the remainder operation.
 * @param set The <code>MathContext</code> arithmetic settings.
 * @return A <code>BigDecimal</code> whose value is the remainder of <code>this+rhs</code>.
 * @throws java.lang.ArithmeticException if <code>rhs</code> is zero.
 * @throws java.lang.ArithmeticException  if the integer part of the result will not fit in the number of digits specified for the context.
 */

public android.icu.math.BigDecimal remainder(android.icu.math.BigDecimal rhs, android.icu.math.MathContext set) { throw new RuntimeException("Stub!"); }

/**
 * Returns a plain <code>BigDecimal</code> whose value is <code>this-rhs</code>, using fixed point arithmetic.
 * <p>
 * The same as {@link #subtract(android.icu.math.BigDecimal,android.icu.math.MathContext)}, where the <code>BigDecimal</code> is <code>rhs</code>,
 * and the context is <code>new MathContext(0, MathContext.PLAIN)</code>.
 * <p>
 * The length of the decimal part (the scale) of the result will be the maximum of the scales of the two operands.
 *
 * @param rhs The <code>BigDecimal</code> for the right hand side of the subtraction.
 * @return A <code>BigDecimal</code> whose value is <code>this-rhs</code>, using fixed point arithmetic.
 */

public android.icu.math.BigDecimal subtract(android.icu.math.BigDecimal rhs) { throw new RuntimeException("Stub!"); }

/**
 * Returns a <code>BigDecimal</code> whose value is <code>this-rhs</code>.
 * <p>
 * Implements the subtraction (<b><code>-</code></b>) operator (as defined in the decimal documentation, see
 * {@link android.icu.math.BigDecimal class header}), and returns the result as a <code>BigDecimal</code> object.
 *
 * @param rhs The <code>BigDecimal</code> for the right hand side of the subtraction.
 * @param set The <code>MathContext</code> arithmetic settings.
 * @return A <code>BigDecimal</code> whose value is <code>this-rhs</code>.
 */

public android.icu.math.BigDecimal subtract(android.icu.math.BigDecimal rhs, android.icu.math.MathContext set) { throw new RuntimeException("Stub!"); }

/**
 * Converts this <code>BigDecimal</code> to a <code>byte</code>. If the <code>BigDecimal</code> has a non-zero
 * decimal part or is out of the possible range for a <code>byte</code> (8-bit signed integer) result then an <code>
 * ArithmeticException</code> is thrown.
 *
 * @return A <code>byte</code> equal in value to <code>this</code>.
 * @throws java.lang.ArithmeticException if <code>this</code> has a non-zero decimal part, or will not fit in a <code>byte</code>.
 */

public byte byteValueExact() { throw new RuntimeException("Stub!"); }

/**
 * Converts this <code>BigDecimal</code> to a <code>double</code>. If the <code>BigDecimal</code> is out of the
 * possible range for a <code>double</code> (64-bit signed floating point) result then an <code>ArithmeticException
 * </code> is thrown.
 * <p>
 * The double produced is identical to result of expressing the <code>BigDecimal</code> as a <code>String</code> and
 * then converting it using the <code>Double(String)</code> constructor; this can result in values of <code>
 * Double.NEGATIVE_INFINITY</code> or <code>Double.POSITIVE_INFINITY</code>.
 *
 * @return A <code>double</code> corresponding to <code>this</code>.
 */

public double doubleValue() { throw new RuntimeException("Stub!"); }

/**
 * Compares this <code>BigDecimal</code> with <code>rhs</code> for equality.
 * <p>
 * If the parameter is <code>null</code>, or is not an instance of the BigDecimal type, or is not exactly equal to
 * the current <code>BigDecimal</code> object, then <i>false</i> is returned. Otherwise, <i>true</i> is returned.
 * <p>
 * "Exactly equal", here, means that the <code>String</code> representations of the <code>BigDecimal</code> numbers
 * are identical (they have the same characters in the same sequence).
 * <p>
 * The {@link #compareTo(android.icu.math.BigDecimal,android.icu.math.MathContext)} method should be used for more general comparisons.
 *
 * @param obj The <code>Object</code> for the right hand side of the comparison.
 * @return A <code>boolean</code> whose value <i>true</i> if and only if the operands have identical string
 *         representations.
 * @throws java.lang.ClassCastException if <code>rhs</code> cannot be cast to a <code>BigDecimal</code> object.
 * @see #compareTo(BigDecimal)
 * @see #compareTo(BigDecimal, MathContext)
 */

public boolean equals(java.lang.Object obj) { throw new RuntimeException("Stub!"); }

/**
 * Converts this <code>BigDecimal</code> to a <code>float</code>. If the <code>BigDecimal</code> is out of the
 * possible range for a <code>float</code> (32-bit signed floating point) result then an <code>ArithmeticException
 * </code> is thrown.
 * <p>
 * The float produced is identical to result of expressing the <code>BigDecimal</code> as a <code>String</code> and
 * then converting it using the <code>Float(String)</code> constructor; this can result in values of <code>
 * Float.NEGATIVE_INFINITY</code> or <code>Float.POSITIVE_INFINITY</code>.
 *
 * @return A <code>float</code> corresponding to <code>this</code>.
 */

public float floatValue() { throw new RuntimeException("Stub!"); }

/**
 * Returns the <code>String</code> representation of this <code>BigDecimal</code>, modified by layout parameters.
 * <p>
 * <i>This method is provided as a primitive for use by more sophisticated classes, such as <code>DecimalFormat
 * </code>, that can apply locale-sensitive editing of the result. The level of formatting that it provides is a
 * necessary part of the BigDecimal class as it is sensitive to and must follow the calculation and rounding rules
 * for BigDecimal arithmetic. However, if the function is provided elsewhere, it may be removed from this class.
 * </i>
 * <p>
 * The parameters, for both forms of the <code>format</code> method are all of type <code>int</code>. A value of -1
 * for any parameter indicates that the default action or value for that parameter should be used.
 * <p>
 * The parameters, <code>before</code> and <code>after</code>, specify the number of characters to be used for the
 * integer part and decimal part of the result respectively. Exponential notation is not used. If either parameter
 * is -1 (which indicates the default action), the number of characters used will be exactly as many as are needed
 * for that part.
 * <p>
 * <code>before</code> must be a positive number; if it is larger than is needed to contain the integer part, that
 * part is padded on the left with blanks to the requested length. If <code>before</code> is not large enough to
 * contain the integer part of the number (including the sign, for negative numbers) an exception is thrown.
 * <p>
 * <code>after</code> must be a non-negative number; if it is not the same size as the decimal part of the number,
 * the number will be rounded (or extended with zeros) to fit. Specifying 0 for <code>after</code> will cause the
 * number to be rounded to an integer (that is, it will have no decimal part or decimal point). The rounding method
 * will be the default, <code>MathContext.ROUND_HALF_UP</code>.
 * <p>
 * Other rounding methods, and the use of exponential notation, can be selected by using
 * {@link #format(int,int,int,int,int,int)}. Using the two-parameter form of the method has exactly the same effect
 * as using the six-parameter form with the final four parameters all being -1.
 *
 * @param before The <code>int</code> specifying the number of places before the decimal point. Use -1 for 'as many as are needed'.
 * @param after The <code>int</code> specifying the number of places after the decimal point. Use -1 for 'as many as are needed'.
 * @return A <code>String</code> representing this <code>BigDecimal</code>, laid out according to the specified parameters
 * @throws java.lang.ArithmeticException if the number cannot be laid out as requested.
 * @throws java.lang.IllegalArgumentException if a parameter is out of range.
 * @see #toString
 * @see #toCharArray
 */

public java.lang.String format(int before, int after) { throw new RuntimeException("Stub!"); }

/**
 * Returns the <code>String</code> representation of this <code>BigDecimal</code>, modified by layout parameters and
 * allowing exponential notation.
 * <p>
 * <i>This method is provided as a primitive for use by more sophisticated classes, such as <code>DecimalFormat
 * </code>, that can apply locale-sensitive editing of the result. The level of formatting that it provides is a
 * necessary part of the BigDecimal class as it is sensitive to and must follow the calculation and rounding rules
 * for BigDecimal arithmetic. However, if the function is provided elsewhere, it may be removed from this class.
 * </i>
 * <p>
 * The parameters are all of type <code>int</code>. A value of -1 for any parameter indicates that the default
 * action or value for that parameter should be used.
 * <p>
 * The first two parameters (<code>before</code> and <code>after</code>) specify the number of characters to be used
 * for the integer part and decimal part of the result respectively, as defined for {@link #format(int,int)}. If
 * either of these is -1 (which indicates the default action), the number of characters used will be exactly as many
 * as are needed for that part.
 * <p>
 * The remaining parameters control the use of exponential notation and rounding. Three (<code>explaces</code>,
 * <code>exdigits</code>, and <code>exform</code>) control the exponent part of the result. As before, the default
 * action for any of these parameters may be selected by using the value -1.
 * <p>
 * <code>explaces</code> must be a positive number; it sets the number of places (digits after the sign of the
 * exponent) to be used for any exponent part, the default (when <code>explaces</code> is -1) being to use as many
 * as are needed. If <code>explaces</code> is not -1, space is always reserved for an exponent; if one is not needed
 * (for example, if the exponent will be 0) then <code>explaces</code>+2 blanks are appended to the result.
 * (This preserves vertical alignment of similarly formatted numbers in a monospace font.) If <code>explaces
 * </code> is not -1 and is not large enough to contain the exponent, an exception is thrown.
 * <p>
 * <code>exdigits</code> sets the trigger point for use of exponential notation. If, before any rounding, the number
 * of places needed before the decimal point exceeds <code>exdigits</code>, or if the absolute value of the result
 * is less than <code>0.000001</code>, then exponential form will be used, provided that <code>exdigits</code> was
 * specified. When <code>exdigits</code> is -1, exponential notation will never be used. If 0 is specified for
 * <code>exdigits</code>, exponential notation is always used unless the exponent would be 0.
 * <p>
 * <code>exform</code> sets the form for exponential notation (if needed). It may be either
 * {@link android.icu.math.MathContext#SCIENTIFIC MathContext#SCIENTIFIC} or {@link android.icu.math.MathContext#ENGINEERING MathContext#ENGINEERING}. If the latter, engineering, form is requested,
 * up to three digits (plus sign, if negative) may be needed for the integer part of the result (<code>before</code>
 * ). Otherwise, only one digit (plus sign, if negative) is needed.
 * <p>
 * Finally, the sixth argument, <code>exround</code>, selects the rounding algorithm to be used, and must be one of
 * the values indicated by a public constant in the {@link android.icu.math.MathContext MathContext} class whose name starts with <code>ROUND_
 * </code>. The default (<code>ROUND_HALF_UP</code>) may also be selected by using the value -1, as before.
 * <p>
 * The special value <code>MathContext.ROUND_UNNECESSARY</code> may be used to detect whether non-zero digits are
 * discarded -- if <code>exround</code> has this value than if non-zero digits would be discarded (rounded) during
 * formatting then an <code>ArithmeticException</code> is thrown.
 *
 * @param before The <code>int</code> specifying the number of places before the decimal point. Use -1 for 'as many as
 *            are needed'.
 * @param after The <code>int</code> specifying the number of places after the decimal point. Use -1 for 'as many as
 *            are needed'.
 * @param explaces The <code>int</code> specifying the number of places to be used for any exponent. Use -1 for 'as many
 *            as are needed'.
 * @param exdigits The <code>int</code> specifying the trigger (digits before the decimal point) which if exceeded causes
 *            exponential notation to be used. Use 0 to force exponential notation. Use -1 to force plain notation
 *            (no exponential notation).
 * @param exformint The <code>int</code> specifying the form of exponential notation to be used (
 *            {@link android.icu.math.MathContext#SCIENTIFIC MathContext#SCIENTIFIC} or {@link android.icu.math.MathContext#ENGINEERING MathContext#ENGINEERING}).
 * @param exround The <code>int</code> specifying the rounding mode to use. Use -1 for the default,
 *            {@link android.icu.math.MathContext#ROUND_HALF_UP MathContext#ROUND_HALF_UP}.
 * @return A <code>String</code> representing this <code>BigDecimal</code>, laid out according to the specified
 *         parameters
 * @throws java.lang.ArithmeticException if the number cannot be laid out as requested.
 * @throws java.lang.IllegalArgumentException if a parameter is out of range.
 * @see #toString
 * @see #toCharArray
 */

public java.lang.String format(int before, int after, int explaces, int exdigits, int exformint, int exround) { throw new RuntimeException("Stub!"); }

/**
 * Returns the hashcode for this <code>BigDecimal</code>. This hashcode is suitable for use by the <code>
 * java.util.Hashtable</code> class.
 * <p>
 * Note that two <code>BigDecimal</code> objects are only guaranteed to produce the same hashcode if they are
 * exactly equal (that is, the <code>String</code> representations of the <code>BigDecimal</code> numbers are
 * identical -- they have the same characters in the same sequence).
 *
 * @return An <code>int</code> that is the hashcode for <code>this</code>.
 */

public int hashCode() { throw new RuntimeException("Stub!"); }

/**
 * Converts this <code>BigDecimal</code> to an <code>int</code>. If the <code>BigDecimal</code> has a non-zero
 * decimal part it is discarded. If the <code>BigDecimal</code> is out of the possible range for an <code>int</code>
 * (32-bit signed integer) result then only the low-order 32 bits are used. (That is, the number may be
 * <i>decapitated</i>.) To avoid unexpected errors when these conditions occur, use the {@link #intValueExact}
 * method.
 *
 * @return An <code>int</code> converted from <code>this</code>, truncated and decapitated if necessary.
 */

public int intValue() { throw new RuntimeException("Stub!"); }

/**
 * Converts this <code>BigDecimal</code> to an <code>int</code>. If the <code>BigDecimal</code> has a non-zero
 * decimal part or is out of the possible range for an <code>int</code> (32-bit signed integer) result then an
 * <code>ArithmeticException</code> is thrown.
 *
 * @return An <code>int</code> equal in value to <code>this</code>.
 * @throws java.lang.ArithmeticException if <code>this</code> has a non-zero decimal part, or will not fit in an <code>int</code>.
 */

public int intValueExact() { throw new RuntimeException("Stub!"); }

/**
 * Converts this <code>BigDecimal</code> to a <code>long</code>. If the <code>BigDecimal</code> has a non-zero
 * decimal part it is discarded. If the <code>BigDecimal</code> is out of the possible range for a <code>long</code>
 * (64-bit signed integer) result then only the low-order 64 bits are used. (That is, the number may be
 * <i>decapitated</i>.) To avoid unexpected errors when these conditions occur, use the {@link #longValueExact}
 * method.
 *
 * @return A <code>long</code> converted from <code>this</code>, truncated and decapitated if necessary.
 */

public long longValue() { throw new RuntimeException("Stub!"); }

/**
 * Converts this <code>BigDecimal</code> to a <code>long</code>. If the <code>BigDecimal</code> has a non-zero
 * decimal part or is out of the possible range for a <code>long</code> (64-bit signed integer) result then an
 * <code>ArithmeticException</code> is thrown.
 *
 * @return A <code>long</code> equal in value to <code>this</code>.
 * @throws java.lang.ArithmeticException if <code>this</code> has a non-zero decimal part, or will not fit in a <code>long</code>.
 */

public long longValueExact() { throw new RuntimeException("Stub!"); }

/**
 * Returns a plain <code>BigDecimal</code> whose decimal point has been moved to the left by a specified number of
 * positions. The parameter, <code>n</code>, specifies the number of positions to move the decimal point. That is,
 * if <code>n</code> is 0 or positive, the number returned is given by:
 * <p>
 * <code> this.multiply(TEN.pow(new BigDecimal(-n))) </code>
 * <p>
 * <code>n</code> may be negative, in which case the method returns the same result as <code>movePointRight(-n)
 * </code>.
 *
 * @param n The <code>int</code> specifying the number of places to move the decimal point leftwards.
 * @return A <code>BigDecimal</code> derived from <code>this</code>, with the decimal point moved <code>n</code>
 *         places to the left.
 */

public android.icu.math.BigDecimal movePointLeft(int n) { throw new RuntimeException("Stub!"); }

/**
 * Returns a plain <code>BigDecimal</code> whose decimal point has been moved to the right by a specified number of
 * positions. The parameter, <code>n</code>, specifies the number of positions to move the decimal point. That is,
 * if <code>n</code> is 0 or positive, the number returned is given by:
 * <p>
 * <code> this.multiply(TEN.pow(new BigDecimal(n))) </code>
 * <p>
 * <code>n</code> may be negative, in which case the method returns the same result as <code>movePointLeft(-n)
 * </code>.
 *
 * @param n The <code>int</code> specifying the number of places to move the decimal point rightwards.
 * @return A <code>BigDecimal</code> derived from <code>this</code>, with the decimal point moved <code>n</code>
 *         places to the right.
 */

public android.icu.math.BigDecimal movePointRight(int n) { throw new RuntimeException("Stub!"); }

/**
 * Returns the scale of this <code>BigDecimal</code>. Returns a non-negative <code>int</code> which is the scale of
 * the number. The scale is the number of digits in the decimal part of the number if the number were formatted
 * without exponential notation.
 *
 * @return An <code>int</code> whose value is the scale of this <code>BigDecimal</code>.
 */

public int scale() { throw new RuntimeException("Stub!"); }

/**
 * Returns a plain <code>BigDecimal</code> with a given scale.
 * <p>
 * If the given scale (which must be zero or positive) is the same as or greater than the length of the decimal part
 * (the scale) of this <code>BigDecimal</code> then trailing zeros will be added to the decimal part as necessary.
 * <p>
 * If the given scale is less than the length of the decimal part (the scale) of this <code>BigDecimal</code> then
 * trailing digits will be removed, and in this case an <code>ArithmeticException</code> is thrown if any discarded
 * digits are non-zero.
 * <p>
 * The same as {@link #setScale(int,int)}, where the first parameter is the scale, and the second is <code>
 * MathContext.ROUND_UNNECESSARY</code>.
 *
 * @param scale The <code>int</code> specifying the scale of the resulting <code>BigDecimal</code>.
 * @return A plain <code>BigDecimal</code> with the given scale.
 * @throws java.lang.ArithmeticException if <code>scale</code> is negative.
 * @throws java.lang.ArithmeticException if reducing scale would discard non-zero digits.
 */

public android.icu.math.BigDecimal setScale(int scale) { throw new RuntimeException("Stub!"); }

/**
 * Returns a plain <code>BigDecimal</code> with a given scale.
 * <p>
 * If the given scale (which must be zero or positive) is the same as or greater than the length of the decimal part
 * (the scale) of this <code>BigDecimal</code> then trailing zeros will be added to the decimal part as necessary.
 * <p>
 * If the given scale is less than the length of the decimal part (the scale) of this <code>BigDecimal</code> then
 * trailing digits will be removed, and the rounding mode given by the second parameter is used to determine if the
 * remaining digits are affected by a carry. In this case, an <code>IllegalArgumentException</code> is thrown if
 * <code>round</code> is not a valid rounding mode.
 * <p>
 * If <code>round</code> is <code>MathContext.ROUND_UNNECESSARY</code>, an <code>ArithmeticException</code> is
 * thrown if any discarded digits are non-zero.
 *
 * @param scale The <code>int</code> specifying the scale of the resulting <code>BigDecimal</code>.
 * @param round The <code>int</code> rounding mode to be used for the division (see the {@link android.icu.math.MathContext MathContext} class).
 * @return A plain <code>BigDecimal</code> with the given scale.
 * @throws java.lang.IllegalArgumentException if <code>round</code> is not a valid rounding mode.
 * @throws java.lang.ArithmeticException if <code>scale</code> is negative.
 * @throws java.lang.ArithmeticException if <code>round</code> is <code>MathContext.ROUND_UNNECESSARY</code>, and reducing scale would discard
 *             non-zero digits.
 */

public android.icu.math.BigDecimal setScale(int scale, int round) { throw new RuntimeException("Stub!"); }

/**
 * Converts this <code>BigDecimal</code> to a <code>short</code>. If the <code>BigDecimal</code> has a non-zero
 * decimal part or is out of the possible range for a <code>short</code> (16-bit signed integer) result then an
 * <code>ArithmeticException</code> is thrown.
 *
 * @return A <code>short</code> equal in value to <code>this</code>.
 * @throws java.lang.ArithmeticException if <code>this</code> has a non-zero decimal part, or will not fit in a <code>short</code>.
 */

public short shortValueExact() { throw new RuntimeException("Stub!"); }

/**
 * Returns the sign of this <code>BigDecimal</code>, as an <code>int</code>. This returns the <i>signum</i> function
 * value that represents the sign of this <code>BigDecimal</code>. That is, -1 if the <code>BigDecimal</code> is
 * negative, 0 if it is numerically equal to zero, or 1 if it is positive.
 *
 * @return An <code>int</code> which is -1 if the <code>BigDecimal</code> is negative, 0 if it is numerically equal
 *         to zero, or 1 if it is positive.
 */

public int signum() { throw new RuntimeException("Stub!"); }

/**
 * Converts this <code>BigDecimal</code> to a <code>java.math.BigDecimal</code>.
 * <p>
 * This is an exact conversion; the result is the same as if the <code>BigDecimal</code> were formatted as a plain
 * number without any rounding or exponent and then the <code>java.math.BigDecimal(java.lang.String)</code>
 * constructor were used to construct the result.
 * <p>
 * <i>(Note: this method is provided only in the <code>android.icu.math</code> version of the BigDecimal class. It
 * would not be present in a <code>java.math</code> version.)</i>
 *
 * @return The <code>java.math.BigDecimal</code> equal in value to this <code>BigDecimal</code>.
 */

public java.math.BigDecimal toBigDecimal() { throw new RuntimeException("Stub!"); }

/**
 * Converts this <code>BigDecimal</code> to a <code>java.math.BigInteger</code>.
 * <p>
 * Any decimal part is truncated (discarded). If an exception is desired should the decimal part be non-zero, use
 * {@link #toBigIntegerExact()}.
 *
 * @return The <code>java.math.BigInteger</code> equal in value to the integer part of this <code>BigDecimal</code>.
 */

public java.math.BigInteger toBigInteger() { throw new RuntimeException("Stub!"); }

/**
 * Converts this <code>BigDecimal</code> to a <code>java.math.BigInteger</code>.
 * <p>
 * An exception is thrown if the decimal part (if any) is non-zero.
 *
 * @return The <code>java.math.BigInteger</code> equal in value to the integer part of this <code>BigDecimal</code>.
 * @throws java.lang.ArithmeticException if <code>this</code> has a non-zero decimal part.
 */

public java.math.BigInteger toBigIntegerExact() { throw new RuntimeException("Stub!"); }

/**
 * Returns the <code>BigDecimal</code> as a character array. The result of this method is the same as using the
 * sequence <code>toString().toCharArray()</code>, but avoids creating the intermediate <code>String</code> and
 * <code>char[]</code> objects.
 *
 * @return The <code>char[]</code> array corresponding to this <code>BigDecimal</code>.
 */

public char[] toCharArray() { throw new RuntimeException("Stub!"); }

/**
 * Returns the <code>BigDecimal</code> as a <code>String</code>. This returns a <code>String</code> that exactly
 * represents this <code>BigDecimal</code>, as defined in the decimal documentation (see {@link android.icu.math.BigDecimal  header}).
 * <p>
 * By definition, using the {@link #BigDecimal(java.lang.String)} constructor on the result <code>String</code> will create a
 * <code>BigDecimal</code> that is exactly equal to the original <code>BigDecimal</code>.
 *
 * @return The <code>String</code> exactly corresponding to this <code>BigDecimal</code>.
 * @see #format(int, int)
 * @see #format(int, int, int, int, int, int)
 * @see #toCharArray()
 */

public java.lang.String toString() { throw new RuntimeException("Stub!"); }

/**
 * Returns the number as a <code>BigInteger</code> after removing the scale. That is, the number is expressed as a
 * plain number, any decimal point is then removed (retaining the digits of any decimal part), and the result is
 * then converted to a <code>BigInteger</code>.
 *
 * @return The <code>java.math.BigInteger</code> equal in value to this <code>BigDecimal</code> multiplied by ten to
 *         the power of <code>this.scale()</code>.
 */

public java.math.BigInteger unscaledValue() { throw new RuntimeException("Stub!"); }

/**
 * Translates a <code>double</code> to a <code>BigDecimal</code>.
 * <p>
 * Returns a <code>BigDecimal</code> which is the decimal representation of the 64-bit signed binary floating point
 * parameter. If the parameter is infinite, or is not a number (NaN), a <code>NumberFormatException</code> is
 * thrown.
 * <p>
 * The number is constructed as though <code>num</code> had been converted to a <code>String</code> using the <code>
 * Double.toString()</code> method and the {@link #BigDecimal(java.lang.String)} constructor had then been used.
 * This is typically not an exact conversion.
 *
 * @param dub The <code>double</code> to be translated.
 * @return The <code>BigDecimal</code> equal in value to <code>dub</code>.
 * @throws java.lang.NumberFormatException if the parameter is infinite or not a number.
 */

public static android.icu.math.BigDecimal valueOf(double dub) { throw new RuntimeException("Stub!"); }

/**
 * Translates a <code>long</code> to a <code>BigDecimal</code>. That is, returns a plain <code>BigDecimal</code>
 * whose value is equal to the given <code>long</code>.
 *
 * @param lint The <code>long</code> to be translated.
 * @return The <code>BigDecimal</code> equal in value to <code>lint</code>.
 */

public static android.icu.math.BigDecimal valueOf(long lint) { throw new RuntimeException("Stub!"); }

/**
 * Translates a <code>long</code> to a <code>BigDecimal</code> with a given scale. That is, returns a plain <code>
 * BigDecimal</code> whose unscaled value is equal to the given <code>long</code>, adjusted by the second parameter,
 * <code>scale</code>.
 * <p>
 * The result is given by:
 * <p>
 * <code> (new BigDecimal(lint)).divide(TEN.pow(new BigDecimal(scale))) </code>
 * <p>
 * A <code>NumberFormatException</code> is thrown if <code>scale</code> is negative.
 *
 * @param lint The <code>long</code> to be translated.
 * @param scale The <code>int</code> scale to be applied.
 * @return The <code>BigDecimal</code> equal in value to <code>lint</code>.
 * @throws java.lang.NumberFormatException if the scale is negative.
 */

public static android.icu.math.BigDecimal valueOf(long lint, int scale) { throw new RuntimeException("Stub!"); }

/**
 * The <code>BigDecimal</code> constant "1".
 *
 * @see #TEN
 * @see #ZERO
 */

public static final android.icu.math.BigDecimal ONE;
static { ONE = null; }

/**
 * Rounding mode to round to a more positive number.
 *
 * @see android.icu.math.MathContext#ROUND_CEILING
 */

public static final int ROUND_CEILING = 2; // 0x2

/**
 * Rounding mode to round towards zero.
 *
 * @see android.icu.math.MathContext#ROUND_DOWN
 */

public static final int ROUND_DOWN = 1; // 0x1

/**
 * Rounding mode to round to a more negative number.
 *
 * @see android.icu.math.MathContext#ROUND_FLOOR
 */

public static final int ROUND_FLOOR = 3; // 0x3

/**
 * Rounding mode to round to nearest neighbor, where an equidistant value is rounded down.
 *
 * @see android.icu.math.MathContext#ROUND_HALF_DOWN
 */

public static final int ROUND_HALF_DOWN = 5; // 0x5

/**
 * Rounding mode to round to nearest neighbor, where an equidistant value is rounded to the nearest even neighbor.
 *
 * @see android.icu.math.MathContext#ROUND_HALF_EVEN
 */

public static final int ROUND_HALF_EVEN = 6; // 0x6

/**
 * Rounding mode to round to nearest neighbor, where an equidistant value is rounded up.
 *
 * @see android.icu.math.MathContext#ROUND_HALF_UP
 */

public static final int ROUND_HALF_UP = 4; // 0x4

/**
 * Rounding mode to assert that no rounding is necessary.
 *
 * @see android.icu.math.MathContext#ROUND_UNNECESSARY
 */

public static final int ROUND_UNNECESSARY = 7; // 0x7

/**
 * Rounding mode to round away from zero.
 *
 * @see android.icu.math.MathContext#ROUND_UP
 */

public static final int ROUND_UP = 0; // 0x0

/**
 * The <code>BigDecimal</code> constant "10".
 *
 * @see #ONE
 * @see #ZERO
 */

public static final android.icu.math.BigDecimal TEN;
static { TEN = null; }

/**
 * The <code>BigDecimal</code> constant "0".
 *
 * @see #ONE
 * @see #TEN
 */

public static final android.icu.math.BigDecimal ZERO;
static { ZERO = null; }
}

