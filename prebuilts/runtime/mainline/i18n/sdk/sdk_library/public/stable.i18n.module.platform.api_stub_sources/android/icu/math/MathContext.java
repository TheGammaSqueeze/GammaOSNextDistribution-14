/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/* Generated from 'MathContext.nrx' 8 Sep 2000 11:07:48 [v2.00] */
/* Options: Binary Comments Crossref Format Java Logo Strictargs Strictcase Trace2 Verbose3 */

package android.icu.math;


/**
 * The <code>MathContext</code> immutable class encapsulates the
 * settings understood by the operator methods of the {@link android.icu.math.BigDecimal BigDecimal}
 * class (and potentially other classes).  Operator methods are those
 * that effect an operation on a number or a pair of numbers.
 * <p>
 * The settings, which are not base-dependent, comprise:
 * <ol>
 * <li><code>digits</code>:
 * the number of digits (precision) to be used for an operation
 * <li><code>form</code>:
 * the form of any exponent that results from the operation
 * <li><code>lostDigits</code>:
 * whether checking for lost digits is enabled
 * <li><code>roundingMode</code>:
 * the algorithm to be used for rounding.
 * </ol>
 * <p>
 * When provided, a <code>MathContext</code> object supplies the
 * settings for an operation directly.
 * <p>
 * When <code>MathContext.DEFAULT</code> is provided for a
 * <code>MathContext</code> parameter then the default settings are used
 * (<code>9, SCIENTIFIC, false, ROUND_HALF_UP</code>).
 * <p>
 * In the <code>BigDecimal</code> class, all methods which accept a
 * <code>MathContext</code> object defaults) also have a version of the
 * method which does not accept a MathContext parameter.  These versions
 * carry out unlimited precision fixed point arithmetic (as though the
 * settings were (<code>0, PLAIN, false, ROUND_HALF_UP</code>).
 * <p>
 * The instance variables are shared with default access (so they are
 * directly accessible to the <code>BigDecimal</code> class), but must
 * never be changed.
 * <p>
 * The rounding mode constants have the same names and values as the
 * constants of the same name in <code>java.math.BigDecimal</code>, to
 * maintain compatibility with earlier versions of
 * <code>BigDecimal</code>.
 *
 * @see     android.icu.math.BigDecimal
 * @author  Mike Cowlishaw
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class MathContext implements java.io.Serializable {

/**
 * Constructs a new <code>MathContext</code> with a specified
 * precision.
 * The other settings are set to the default values
 * (see {@link #DEFAULT}).
 *
 * An <code>IllegalArgumentException</code> is thrown if the
 * <code>setdigits</code> parameter is out of range
 * (&lt;0 or &gt;999999999).
 *
 * @param setdigits     The <code>int</code> digits setting
 *                      for this <code>MathContext</code>.
 * @throws java.lang.IllegalArgumentException parameter out of range.
 */

public MathContext(int setdigits) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a new <code>MathContext</code> with a specified
 * precision and form.
 * The other settings are set to the default values
 * (see {@link #DEFAULT}).
 *
 * An <code>IllegalArgumentException</code> is thrown if the
 * <code>setdigits</code> parameter is out of range
 * (&lt;0 or &gt;999999999), or if the value given for the
 * <code>setform</code> parameter is not one of the appropriate
 * constants.
 *
 * @param setdigits     The <code>int</code> digits setting
 *                      for this <code>MathContext</code>.
 * @param setform       The <code>int</code> form setting
 *                      for this <code>MathContext</code>.
 * @throws java.lang.IllegalArgumentException parameter out of range.
 */

public MathContext(int setdigits, int setform) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a new <code>MathContext</code> with a specified
 * precision, form, and lostDigits setting.
 * The roundingMode setting is set to its default value
 * (see {@link #DEFAULT}).
 *
 * An <code>IllegalArgumentException</code> is thrown if the
 * <code>setdigits</code> parameter is out of range
 * (&lt;0 or &gt;999999999), or if the value given for the
 * <code>setform</code> parameter is not one of the appropriate
 * constants.
 *
 * @param setdigits     The <code>int</code> digits setting
 *                      for this <code>MathContext</code>.
 * @param setform       The <code>int</code> form setting
 *                      for this <code>MathContext</code>.
 * @param setlostdigits The <code>boolean</code> lostDigits
 *                      setting for this <code>MathContext</code>.
 * @throws java.lang.IllegalArgumentException parameter out of range.
 */

public MathContext(int setdigits, int setform, boolean setlostdigits) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a new <code>MathContext</code> with a specified
 * precision, form, lostDigits, and roundingMode setting.
 *
 * An <code>IllegalArgumentException</code> is thrown if the
 * <code>setdigits</code> parameter is out of range
 * (&lt;0 or &gt;999999999), or if the value given for the
 * <code>setform</code> or <code>setroundingmode</code> parameters is
 * not one of the appropriate constants.
 *
 * @param setdigits       The <code>int</code> digits setting
 *                        for this <code>MathContext</code>.
 * @param setform         The <code>int</code> form setting
 *                        for this <code>MathContext</code>.
 * @param setlostdigits   The <code>boolean</code> lostDigits
 *                        setting for this <code>MathContext</code>.
 * @param setroundingmode The <code>int</code> roundingMode setting
 *                        for this <code>MathContext</code>.
 * @throws java.lang.IllegalArgumentException parameter out of range.
 */

public MathContext(int setdigits, int setform, boolean setlostdigits, int setroundingmode) { throw new RuntimeException("Stub!"); }

/**
 * Returns the digits setting.
 * This value is always non-negative.
 *
 * @return an <code>int</code> which is the value of the digits
 *         setting
 */

public int getDigits() { throw new RuntimeException("Stub!"); }

/**
 * Returns the form setting.
 * This will be one of
 * {@link #ENGINEERING},
 * {@link #PLAIN}, or
 * {@link #SCIENTIFIC}.
 *
 * @return an <code>int</code> which is the value of the form setting
 */

public int getForm() { throw new RuntimeException("Stub!"); }

/**
 * Returns the lostDigits setting.
 * This will be either <code>true</code> (enabled) or
 * <code>false</code> (disabled).
 *
 * @return a <code>boolean</code> which is the value of the lostDigits
 *           setting
 */

public boolean getLostDigits() { throw new RuntimeException("Stub!"); }

/**
 * Returns the roundingMode setting.
 * This will be one of
 * {@link  #ROUND_CEILING},
 * {@link  #ROUND_DOWN},
 * {@link  #ROUND_FLOOR},
 * {@link  #ROUND_HALF_DOWN},
 * {@link  #ROUND_HALF_EVEN},
 * {@link  #ROUND_HALF_UP},
 * {@link  #ROUND_UNNECESSARY}, or
 * {@link  #ROUND_UP}.
 *
 * @return an <code>int</code> which is the value of the roundingMode
 *         setting
 */

public int getRoundingMode() { throw new RuntimeException("Stub!"); }

/** Returns the <code>MathContext</code> as a readable string.
 * The <code>String</code> returned represents the settings of the
 * <code>MathContext</code> object as four blank-delimited words
 * separated by a single blank and with no leading or trailing blanks,
 * as follows:
 * <ol>
 * <li>
 * <code>digits=</code>, immediately followed by
 * the value of the digits setting as a numeric word.
 * <li>
 * <code>form=</code>, immediately followed by
 * the value of the form setting as an uppercase word
 * (one of <code>SCIENTIFIC</code>, <code>PLAIN</code>, or
 * <code>ENGINEERING</code>).
 * <li>
 * <code>lostDigits=</code>, immediately followed by
 * the value of the lostDigits setting
 * (<code>1</code> if enabled, <code>0</code> if disabled).
 * <li>
 * <code>roundingMode=</code>, immediately followed by
 * the value of the roundingMode setting as a word.
 * This word will be the same as the name of the corresponding public
 * constant.
 * </ol>
 * <p>
 * For example:
 * <br><code>
 * digits=9 form=SCIENTIFIC lostDigits=0 roundingMode=ROUND_HALF_UP
 * </code>
 * <p>
 * Additional words may be appended to the result of
 * <code>toString</code> in the future if more properties are added
 * to the class.
 *
 * @return a <code>String</code> representing the context settings.
 */

public java.lang.String toString() { throw new RuntimeException("Stub!"); }

/**
 * A <code>MathContext</code> object initialized to the default
 * settings for general-purpose arithmetic.  That is,
 * <code>digits=9 form=SCIENTIFIC lostDigits=false
 * roundingMode=ROUND_HALF_UP</code>.
 *
 * @see #SCIENTIFIC
 * @see #ROUND_HALF_UP
 */

public static final android.icu.math.MathContext DEFAULT;
static { DEFAULT = null; }

/**
 * Standard floating point notation (with engineering exponential
 * format, where the power of ten is a multiple of 3).
 * Used as a setting to control the form of the result of a
 * <code>BigDecimal</code> operation.
 * A zero result in plain form may have a decimal part of one or
 * more zeros.
 *
 * @see #PLAIN
 * @see #SCIENTIFIC
 */

public static final int ENGINEERING = 2; // 0x2

/**
 * Plain (fixed point) notation, without any exponent.
 * Used as a setting to control the form of the result of a
 * <code>BigDecimal</code> operation.
 * A zero result in plain form may have a decimal part of one or
 * more zeros.
 *
 * @see #ENGINEERING
 * @see #SCIENTIFIC
 */

public static final int PLAIN = 0; // 0x0

/**
 * Rounding mode to round to a more positive number.
 * Used as a setting to control the rounding mode used during a
 * <code>BigDecimal</code> operation.
 * <p>
 * If any of the discarded digits are non-zero then the result
 * should be rounded towards the next more positive digit.
 */

public static final int ROUND_CEILING = 2; // 0x2

/**
 * Rounding mode to round towards zero.
 * Used as a setting to control the rounding mode used during a
 * <code>BigDecimal</code> operation.
 * <p>
 * All discarded digits are ignored (truncated).  The result is
 * neither incremented nor decremented.
 */

public static final int ROUND_DOWN = 1; // 0x1

/**
 * Rounding mode to round to a more negative number.
 * Used as a setting to control the rounding mode used during a
 * <code>BigDecimal</code> operation.
 * <p>
 * If any of the discarded digits are non-zero then the result
 * should be rounded towards the next more negative digit.
 */

public static final int ROUND_FLOOR = 3; // 0x3

/**
 * Rounding mode to round to nearest neighbor, where an equidistant
 * value is rounded down.
 * Used as a setting to control the rounding mode used during a
 * <code>BigDecimal</code> operation.
 * <p>
 * If the discarded digits represent greater than half (0.5 times)
 * the value of a one in the next position then the result should be
 * rounded up (away from zero).  Otherwise the discarded digits are
 * ignored.
 */

public static final int ROUND_HALF_DOWN = 5; // 0x5

/**
 * Rounding mode to round to nearest neighbor, where an equidistant
 * value is rounded to the nearest even neighbor.
 * Used as a setting to control the rounding mode used during a
 * <code>BigDecimal</code> operation.
 * <p>
 * If the discarded digits represent greater than half (0.5 times)
 * the value of a one in the next position then the result should be
 * rounded up (away from zero).  If they represent less than half,
 * then the result should be rounded down.
 * <p>
 * Otherwise (they represent exactly half) the result is rounded
 * down if its rightmost digit is even, or rounded up if its
 * rightmost digit is odd (to make an even digit).
 */

public static final int ROUND_HALF_EVEN = 6; // 0x6

/**
 * Rounding mode to round to nearest neighbor, where an equidistant
 * value is rounded up.
 * Used as a setting to control the rounding mode used during a
 * <code>BigDecimal</code> operation.
 * <p>
 * If the discarded digits represent greater than or equal to half
 * (0.5 times) the value of a one in the next position then the result
 * should be rounded up (away from zero).  Otherwise the discarded
 * digits are ignored.
 */

public static final int ROUND_HALF_UP = 4; // 0x4

/**
 * Rounding mode to assert that no rounding is necessary.
 * Used as a setting to control the rounding mode used during a
 * <code>BigDecimal</code> operation.
 * <p>
 * Rounding (potential loss of information) is not permitted.
 * If any of the discarded digits are non-zero then an
 * <code>ArithmeticException</code> should be thrown.
 */

public static final int ROUND_UNNECESSARY = 7; // 0x7

/**
 * Rounding mode to round away from zero.
 * Used as a setting to control the rounding mode used during a
 * <code>BigDecimal</code> operation.
 * <p>
 * If any of the discarded digits are non-zero then the result will
 * be rounded up (away from zero).
 */

public static final int ROUND_UP = 0; // 0x0

/**
 * Standard floating point notation (with scientific exponential
 * format, where there is one digit before any decimal point).
 * Used as a setting to control the form of the result of a
 * <code>BigDecimal</code> operation.
 * A zero result in plain form may have a decimal part of one or
 * more zeros.
 *
 * @see #ENGINEERING
 * @see #PLAIN
 */

public static final int SCIENTIFIC = 1; // 0x1
}

