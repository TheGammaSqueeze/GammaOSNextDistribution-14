/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 1996-2016, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */


package android.icu.text;

import java.util.Locale;
import java.text.FieldPosition;
import java.text.ParsePosition;
import android.icu.number.NumberFormatter;
import java.text.Format;
import java.math.BigInteger;
import android.icu.util.CurrencyAmount;
import java.text.ParseException;
import java.util.Set;
import android.icu.util.ULocale.Category;
import android.icu.util.Currency;

/**
 * <strong>[icu enhancement]</strong> ICU's replacement for {@link java.text.NumberFormat}.&nbsp;Methods, fields, and other functionality specific to ICU are labeled '<strong>[icu]</strong>'.
 *
 * <p>
 * <strong>IMPORTANT:</strong> New users are strongly encouraged to see if
 * {@code NumberFormatter} fits their use case.  Although not deprecated, this
 * class, NumberFormat, is only provided for java.text.NumberFormat compatibility.
 * <hr>
 *
 * <code>NumberFormat</code> is the abstract base class for all number
 * formats. This class provides the interface for formatting and parsing
 * numbers. <code>NumberFormat</code> also provides methods for determining
 * which locales have number formats, and what their names are.
 *
 * <code>NumberFormat</code> helps you to format and parse numbers for any locale.
 * Your code can be completely independent of the locale conventions for
 * decimal points, thousands-separators, or even the particular decimal
 * digits used, or whether the number format is even decimal.
 *
 * <p>
 * To format a number for the current Locale, use one of the factory
 * class methods:
 * <blockquote>
 * <pre>
 *  myString = NumberFormat.getInstance().format(myNumber);
 * </pre>
 * </blockquote>
 * If you are formatting multiple numbers, it is
 * more efficient to get the format and use it multiple times so that
 * the system doesn't have to fetch the information about the local
 * language and country conventions multiple times.
 * <blockquote>
 * <pre>
 * NumberFormat nf = NumberFormat.getInstance();
 * for (int i = 0; i &lt; a.length; ++i) {
 *     output.println(nf.format(myNumber[i]) + "; ");
 * }
 * </pre>
 * </blockquote>
 * To format a number for a different Locale, specify it in the
 * call to <code>getInstance</code>.
 * <blockquote>
 * <pre>
 * NumberFormat nf = NumberFormat.getInstance(Locale.FRENCH);
 * </pre>
 * </blockquote>
 * You can also use a <code>NumberFormat</code> to parse numbers:
 * <blockquote>
 * <pre>
 * myNumber = nf.parse(myString);
 * </pre>
 * </blockquote>
 * Use <code>getInstance</code> or <code>getNumberInstance</code> to get the
 * normal number format. Use <code>getIntegerInstance</code> to get an
 * integer number format. Use <code>getCurrencyInstance</code> to get the
 * currency number format. And use <code>getPercentInstance</code> to get a
 * format for displaying percentages. Some factory methods are found within
 * subclasses of NumberFormat. With this format, a fraction like
 * 0.53 is displayed as 53%.
 *
 * <p>
 * Starting from ICU 4.2, you can use getInstance() by passing in a 'style'
 * as parameter to get the correct instance.
 * For example,
 * use getInstance(...NUMBERSTYLE) to get the normal number format,
 * getInstance(...PERCENTSTYLE) to get a format for displaying percentage,
 * getInstance(...SCIENTIFICSTYLE) to get a format for displaying scientific number,
 * getInstance(...INTEGERSTYLE) to get an integer number format,
 * getInstance(...CURRENCYSTYLE) to get the currency number format,
 * in which the currency is represented by its symbol, for example, "$3.00".
 * getInstance(...ISOCURRENCYSTYLE)  to get the currency number format,
 * in which the currency is represented by its ISO code, for example "USD3.00".
 * getInstance(...PLURALCURRENCYSTYLE) to get the currency number format,
 * in which the currency is represented by its full name in plural format,
 * for example, "3.00 US dollars" or "1.00 US dollar".
 *
 *
 * <p>
 * You can also control the display of numbers with such methods as
 * <code>setMinimumFractionDigits</code>.
 * If you want even more control over the format or parsing,
 * or want to give your users more control,
 * you can try casting the <code>NumberFormat</code> you get from the factory methods
 * to a <code>DecimalFormat</code>. This will work for the vast majority
 * of locales; just remember to put it in a <code>try</code> block in case you
 * encounter an unusual one.
 *
 * <p>
 * NumberFormat is designed such that some controls
 * work for formatting and others work for parsing.  The following is
 * the detailed description for each these control methods,
 * <p>
 * setParseIntegerOnly : only affects parsing, e.g.
 * if true,  "3456.78" -&gt; 3456 (and leaves the parse position just after '6')
 * if false, "3456.78" -&gt; 3456.78 (and leaves the parse position just after '8')
 * This is independent of formatting.  If you want to not show a decimal point
 * where there might be no digits after the decimal point, use
 * setDecimalSeparatorAlwaysShown on DecimalFormat.
 * <p>
 * You can also use forms of the <code>parse</code> and <code>format</code>
 * methods with <code>ParsePosition</code> and <code>FieldPosition</code> to
 * allow you to:
 * <ul>
 * <li> progressively parse through pieces of a string
 * <li> align the decimal point and other areas
 * </ul>
 * For example, you can align numbers in two ways:
 * <ol>
 * <li> If you are using a monospaced font with spacing for alignment,
 *      you can pass the <code>FieldPosition</code> in your format call, with
 *      <code>field</code> = <code>INTEGER_FIELD</code>. On output,
 *      <code>getEndIndex</code> will be set to the offset between the
 *      last character of the integer and the decimal. Add
 *      (desiredSpaceCount - getEndIndex) spaces at the front of the string.
 *
 * <li> If you are using proportional fonts,
 *      instead of padding with spaces, measure the width
 *      of the string in pixels from the start to <code>getEndIndex</code>.
 *      Then move the pen by
 *      (desiredPixelWidth - widthToAlignmentPoint) before drawing the text.
 *      It also works where there is no decimal, but possibly additional
 *      characters at the end, e.g., with parentheses in negative
 *      numbers: "(12)" for -12.
 * </ol>
 *
 * <h3>Synchronization</h3>
 * <p>
 * Number formats are generally not synchronized. It is recommended to create
 * separate format instances for each thread. If multiple threads access a format
 * concurrently, it must be synchronized externally.
 *
 * <h4>DecimalFormat</h4>
 * <p>DecimalFormat is the concrete implementation of NumberFormat, and the
 * NumberFormat API is essentially an abstraction from DecimalFormat's API.
 * Refer to DecimalFormat for more information about this API.</p>
 *
 * see          DecimalFormat
 * see          java.text.ChoiceFormat
 * @author       Mark Davis
 * @author       Helena Shih
 * @author       Alan Liu
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public abstract class NumberFormat extends android.icu.text.UFormat {

/**
 * Empty constructor.  Public for API compatibility with historic versions of
 * {@link java.text.NumberFormat} which had public constructor even though this is
 * an abstract class.
 */

public NumberFormat() { throw new RuntimeException("Stub!"); }

/**
 * Formats a number and appends the resulting text to the given string buffer.
 * <strong>[icu] Note:</strong> recognizes <code>BigInteger</code>
 * and <code>BigDecimal</code> objects.
 * @see java.text.Format#format(Object, StringBuffer, FieldPosition)
 */

public java.lang.StringBuffer format(java.lang.Object number, java.lang.StringBuffer toAppendTo, java.text.FieldPosition pos) { throw new RuntimeException("Stub!"); }

/**
 * Parses text from a string to produce a number.
 * @param source the String to parse
 * @param parsePosition the position at which to start the parse
 * @return the parsed number, or null
 * @see java.text.NumberFormat#parseObject(String, ParsePosition)
 */

public final java.lang.Object parseObject(java.lang.String source, java.text.ParsePosition parsePosition) { throw new RuntimeException("Stub!"); }

/**
 * Specialization of format.
 * @see java.text.Format#format(Object)
 */

public final java.lang.String format(double number) { throw new RuntimeException("Stub!"); }

/**
 * Specialization of format.
 * @see java.text.Format#format(Object)
 */

public final java.lang.String format(long number) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Convenience method to format a BigInteger.
 */

public final java.lang.String format(java.math.BigInteger number) { throw new RuntimeException("Stub!"); }

/**
 * Convenience method to format a BigDecimal.
 */

public final java.lang.String format(java.math.BigDecimal number) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Convenience method to format an ICU BigDecimal.
 */

public final java.lang.String format(android.icu.math.BigDecimal number) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Convenience method to format a CurrencyAmount.
 */

public final java.lang.String format(android.icu.util.CurrencyAmount currAmt) { throw new RuntimeException("Stub!"); }

/**
 * Specialization of format.
 * @see java.text.Format#format(Object, StringBuffer, FieldPosition)
 */

public abstract java.lang.StringBuffer format(double number, java.lang.StringBuffer toAppendTo, java.text.FieldPosition pos);

/**
 * Specialization of format.
 * @see java.text.Format#format(Object, StringBuffer, FieldPosition)
 */

public abstract java.lang.StringBuffer format(long number, java.lang.StringBuffer toAppendTo, java.text.FieldPosition pos);

/**
 * <strong>[icu]</strong> Formats a BigInteger. Specialization of format.
 * @see java.text.Format#format(Object, StringBuffer, FieldPosition)
 */

public abstract java.lang.StringBuffer format(java.math.BigInteger number, java.lang.StringBuffer toAppendTo, java.text.FieldPosition pos);

/**
 * <strong>[icu]</strong> Formats a BigDecimal. Specialization of format.
 * @see java.text.Format#format(Object, StringBuffer, FieldPosition)
 */

public abstract java.lang.StringBuffer format(java.math.BigDecimal number, java.lang.StringBuffer toAppendTo, java.text.FieldPosition pos);

/**
 * <strong>[icu]</strong> Formats an ICU BigDecimal. Specialization of format.
 * @see java.text.Format#format(Object, StringBuffer, FieldPosition)
 */

public abstract java.lang.StringBuffer format(android.icu.math.BigDecimal number, java.lang.StringBuffer toAppendTo, java.text.FieldPosition pos);

/**
 * <strong>[icu]</strong> Formats a CurrencyAmount. Specialization of format.
 * @see java.text.Format#format(Object, StringBuffer, FieldPosition)
 */

public java.lang.StringBuffer format(android.icu.util.CurrencyAmount currAmt, java.lang.StringBuffer toAppendTo, java.text.FieldPosition pos) { throw new RuntimeException("Stub!"); }

/**
 * Returns a Long if possible (e.g., within the range [Long.MIN_VALUE,
 * Long.MAX_VALUE] and with no decimals); otherwise, returns another type,
 * such as a BigDecimal, BigInteger, or Double. The return type is not
 * guaranteed other than for the Long case.
 *
 * <p>If IntegerOnly is set, will stop at a decimal
 * point (or equivalent; e.g., for rational numbers "1 2/3", will stop
 * after the 1).
 *
 * <p>Does not throw an exception; if no object can be parsed, index is
 * unchanged!
 *
 * <p>For more detail on parsing, see the "Parsing" header in the class
 * documentation of {@link android.icu.text.DecimalFormat DecimalFormat}.
 *
 * @see #isParseIntegerOnly
 * @see android.icu.text.DecimalFormat#setParseBigDecimal
 * @see java.text.Format#parseObject(String, ParsePosition)
 */

public abstract java.lang.Number parse(java.lang.String text, java.text.ParsePosition parsePosition);

/**
 * Parses text from the beginning of the given string to produce a number.
 * The method might not use the entire text of the given string.
 *
 * @param text A String whose beginning should be parsed.
 * @return A Number parsed from the string.
 * @throws java.text.ParseException if the beginning of the specified string
 * cannot be parsed.
 * @see #format
 */

public java.lang.Number parse(java.lang.String text) throws java.text.ParseException { throw new RuntimeException("Stub!"); }

/**
 * Parses text from the given string as a CurrencyAmount.  Unlike
 * the parse() method, this method will attempt to parse a generic
 * currency name, searching for a match of this object's locale's
 * currency display names, or for a 3-letter ISO currency code.
 * This method will fail if this format is not a currency format,
 * that is, if it does not contain the currency pattern symbol
 * (U+00A4) in its prefix or suffix.
 *
 * @param text the text to parse
 * @param pos input-output position; on input, the position within
 * text to match; must have 0 &lt;= pos.getIndex() &lt; text.length();
 * on output, the position after the last matched character. If
 * the parse fails, the position in unchanged upon output.
 * @return a CurrencyAmount, or null upon failure
 */

public android.icu.util.CurrencyAmount parseCurrency(java.lang.CharSequence text, java.text.ParsePosition pos) { throw new RuntimeException("Stub!"); }

/**
 * Returns true if this format will parse numbers as integers only.
 * For example in the English locale, with ParseIntegerOnly true, the
 * string "1234." would be parsed as the integer value 1234 and parsing
 * would stop at the "." character.  The decimal separator accepted
 * by the parse operation is locale-dependent and determined by the
 * subclass.
 *
 * @return true if this will parse integers only
 */

public boolean isParseIntegerOnly() { throw new RuntimeException("Stub!"); }

/**
 * Sets whether to ignore the fraction part of a number when parsing
 * (defaults to false). If a string contains a decimal point, parsing will stop before the decimal
 * point. Note that determining whether a character is a decimal point depends on the locale.
 *
 * <p>For example, in <em>en-US</em>, parsing the string "123.45" will return the number 123 and
 * parse position 3.
 *
 * @param value true if this should parse integers only
 * @see #isParseIntegerOnly
 */

public void setParseIntegerOnly(boolean value) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Sets whether strict parsing is in effect.  When this is true, the string
 * is required to be a stronger match to the pattern than when lenient parsing is in
 * effect.  More specifically, the following conditions cause a parse failure relative
 * to lenient mode (examples use the pattern "#,##0.#"):<ul>
 * <li>The presence and position of special symbols, including currency, must match the
 * pattern.<br>
 * '+123' fails (there is no plus sign in the pattern)</li>
 * <li>Leading or doubled grouping separators<br>
 * ',123' and '1,,234" fail</li>
 * <li>Groups of incorrect length when grouping is used<br>
 * '1,23' and '1234,567' fail, but '1234' passes</li>
 * <li>Grouping separators used in numbers followed by exponents<br>
 * '1,234E5' fails, but '1234E5' and '1,234E' pass ('E' is not an exponent when
 * not followed by a number)</li>
 * </ul>
 * When strict parsing is off,  all grouping separators are ignored.
 * This is the default behavior.
 * @param value True to enable strict parsing.  Default is false.
 * @see #isParseStrict
 */

public void setParseStrict(boolean value) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns whether strict parsing is in effect.
 * @return true if strict parsing is in effect
 * @see #setParseStrict
 */

public boolean isParseStrict() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Set a particular DisplayContext value in the formatter,
 * such as CAPITALIZATION_FOR_STANDALONE.
 *
 * @param context The DisplayContext value to set.
 */

public void setContext(android.icu.text.DisplayContext context) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Get the formatter's DisplayContext value for the specified DisplayContext.Type,
 * such as CAPITALIZATION.
 *
 * @param type the DisplayContext.Type whose value to return
 * @return the current DisplayContext setting for the specified type
 */

public android.icu.text.DisplayContext getContext(android.icu.text.DisplayContext.Type type) { throw new RuntimeException("Stub!"); }

/**
 * Returns the default number format for the current default <code>FORMAT</code> locale.
 * The default format is one of the styles provided by the other
 * factory methods: getNumberInstance, getIntegerInstance,
 * getCurrencyInstance or getPercentInstance.
 * Exactly which one is locale-dependent.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public static final android.icu.text.NumberFormat getInstance() { throw new RuntimeException("Stub!"); }

/**
 * Returns the default number format for the specified locale.
 * The default format is one of the styles provided by the other
 * factory methods: getNumberInstance, getCurrencyInstance or getPercentInstance.
 * Exactly which one is locale-dependent.
 */

public static android.icu.text.NumberFormat getInstance(java.util.Locale inLocale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the default number format for the specified locale.
 * The default format is one of the styles provided by the other
 * factory methods: getNumberInstance, getCurrencyInstance or getPercentInstance.
 * Exactly which one is locale-dependent.
 */

public static android.icu.text.NumberFormat getInstance(android.icu.util.ULocale inLocale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a specific style number format for default <code>FORMAT</code> locale.
 * @param style  number format style
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public static final android.icu.text.NumberFormat getInstance(int style) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a specific style number format for a specific locale.
 * @param inLocale  the specific locale.
 * @param style     number format style
 */

public static android.icu.text.NumberFormat getInstance(java.util.Locale inLocale, int style) { throw new RuntimeException("Stub!"); }

/**
 * Returns a general-purpose number format for the current default <code>FORMAT</code> locale.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public static final android.icu.text.NumberFormat getNumberInstance() { throw new RuntimeException("Stub!"); }

/**
 * Returns a general-purpose number format for the specified locale.
 */

public static android.icu.text.NumberFormat getNumberInstance(java.util.Locale inLocale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a general-purpose number format for the specified locale.
 */

public static android.icu.text.NumberFormat getNumberInstance(android.icu.util.ULocale inLocale) { throw new RuntimeException("Stub!"); }

/**
 * Returns an integer number format for the current default <code>FORMAT</code> locale. The
 * returned number format is configured to round floating point numbers
 * to the nearest integer using IEEE half-even rounding (see {@link
 * android.icu.math.BigDecimal#ROUND_HALF_EVEN ROUND_HALF_EVEN}) for formatting,
 * and to parse only the integer part of an input string (see {@link
 * #isParseIntegerOnly isParseIntegerOnly}).
 *
 * @return a number format for integer values
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public static final android.icu.text.NumberFormat getIntegerInstance() { throw new RuntimeException("Stub!"); }

/**
 * Returns an integer number format for the specified locale. The
 * returned number format is configured to round floating point numbers
 * to the nearest integer using IEEE half-even rounding (see {@link
 * android.icu.math.BigDecimal#ROUND_HALF_EVEN ROUND_HALF_EVEN}) for formatting,
 * and to parse only the integer part of an input string (see {@link
 * #isParseIntegerOnly isParseIntegerOnly}).
 *
 * @param inLocale the locale for which a number format is needed
 * @return a number format for integer values
 */

public static android.icu.text.NumberFormat getIntegerInstance(java.util.Locale inLocale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns an integer number format for the specified locale. The
 * returned number format is configured to round floating point numbers
 * to the nearest integer using IEEE half-even rounding (see {@link
 * android.icu.math.BigDecimal#ROUND_HALF_EVEN ROUND_HALF_EVEN}) for formatting,
 * and to parse only the integer part of an input string (see {@link
 * #isParseIntegerOnly isParseIntegerOnly}).
 *
 * @param inLocale the locale for which a number format is needed
 * @return a number format for integer values
 */

public static android.icu.text.NumberFormat getIntegerInstance(android.icu.util.ULocale inLocale) { throw new RuntimeException("Stub!"); }

/**
 * Returns a currency format for the current default <code>FORMAT</code> locale.
 * @return a number format for currency
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public static final android.icu.text.NumberFormat getCurrencyInstance() { throw new RuntimeException("Stub!"); }

/**
 * Returns a currency format for the specified locale.
 * @return a number format for currency
 */

public static android.icu.text.NumberFormat getCurrencyInstance(java.util.Locale inLocale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a currency format for the specified locale.
 * @return a number format for currency
 */

public static android.icu.text.NumberFormat getCurrencyInstance(android.icu.util.ULocale inLocale) { throw new RuntimeException("Stub!"); }

/**
 * Returns a percentage format for the current default <code>FORMAT</code> locale.
 * @return a number format for percents
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public static final android.icu.text.NumberFormat getPercentInstance() { throw new RuntimeException("Stub!"); }

/**
 * Returns a percentage format for the specified locale.
 * @return a number format for percents
 */

public static android.icu.text.NumberFormat getPercentInstance(java.util.Locale inLocale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a percentage format for the specified locale.
 * @return a number format for percents
 */

public static android.icu.text.NumberFormat getPercentInstance(android.icu.util.ULocale inLocale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a scientific format for the current default <code>FORMAT</code> locale.
 * @return a scientific number format
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public static final android.icu.text.NumberFormat getScientificInstance() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a scientific format for the specified locale.
 * @return a scientific number format
 */

public static android.icu.text.NumberFormat getScientificInstance(java.util.Locale inLocale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a scientific format for the specified locale.
 * @return a scientific number format
 */

public static android.icu.text.NumberFormat getScientificInstance(android.icu.util.ULocale inLocale) { throw new RuntimeException("Stub!"); }

/**
 * Returns the list of Locales for which NumberFormats are available.
 * @return the available locales
 */

public static java.util.Locale[] getAvailableLocales() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public int hashCode() { throw new RuntimeException("Stub!"); }

/**
 * Overrides equals.
 * Two NumberFormats are equal they are of the same class
 * and the user-specified values for settings
 * (groupingUsed, parseIntegerOnly, maximumIntegerDigits, etc.)
 * are equal.
 * @param obj the object to compare against
 * @return true if the object is equal to this.
 */

public boolean equals(java.lang.Object obj) { throw new RuntimeException("Stub!"); }

/**
 * Overrides clone.
 */

public java.lang.Object clone() { throw new RuntimeException("Stub!"); }

/**
 * Returns true if grouping is used in this format. For example, in the
 * en_US locale, with grouping on, the number 1234567 will be formatted
 * as "1,234,567". The grouping separator as well as the size of each group
 * is locale-dependent and is determined by subclasses of NumberFormat.
 * Grouping affects both parsing and formatting.
 * @return true if grouping is used
 * @see #setGroupingUsed
 */

public boolean isGroupingUsed() { throw new RuntimeException("Stub!"); }

/**
 * Sets whether or not grouping will be used in this format.  Grouping
 * affects both parsing and formatting.
 * @see #isGroupingUsed
 * @param newValue true to use grouping.
 */

public void setGroupingUsed(boolean newValue) { throw new RuntimeException("Stub!"); }

/**
 * Returns the maximum number of digits allowed in the integer portion of a
 * number.  The default value is 40, which subclasses can override.
 *
 * When formatting, if the number of digits exceeds this value, the highest-
 * significance digits are truncated until the limit is reached, in accordance
 * with UTS#35.
 *
 * This setting has no effect on parsing.
 *
 * @return the maximum number of integer digits
 * @see #setMaximumIntegerDigits
 */

public int getMaximumIntegerDigits() { throw new RuntimeException("Stub!"); }

/**
 * Sets the maximum number of digits allowed in the integer portion of a
 * number. This must be &gt;= minimumIntegerDigits.  If the
 * new value for maximumIntegerDigits is less than the current value
 * of minimumIntegerDigits, then minimumIntegerDigits will also be set to
 * the new value.
 * @param newValue the maximum number of integer digits to be shown; if
 * less than zero, then zero is used.  Subclasses might enforce an
 * upper limit to this value appropriate to the numeric type being formatted.
 * @see #getMaximumIntegerDigits
 */

public void setMaximumIntegerDigits(int newValue) { throw new RuntimeException("Stub!"); }

/**
 * Returns the minimum number of digits allowed in the integer portion of a
 * number.  The default value is 1, which subclasses can override.
 * When formatting, if this value is not reached, numbers are padded on the
 * left with the locale-specific '0' character to ensure at least this
 * number of integer digits.  When parsing, this has no effect.
 * @return the minimum number of integer digits
 * @see #setMinimumIntegerDigits
 */

public int getMinimumIntegerDigits() { throw new RuntimeException("Stub!"); }

/**
 * Sets the minimum number of digits allowed in the integer portion of a
 * number.  This must be &lt;= maximumIntegerDigits.  If the
 * new value for minimumIntegerDigits is more than the current value
 * of maximumIntegerDigits, then maximumIntegerDigits will also be set to
 * the new value.
 * @param newValue the minimum number of integer digits to be shown; if
 * less than zero, then zero is used. Subclasses might enforce an
 * upper limit to this value appropriate to the numeric type being formatted.
 * @see #getMinimumIntegerDigits
 */

public void setMinimumIntegerDigits(int newValue) { throw new RuntimeException("Stub!"); }

/**
 * Returns the maximum number of digits allowed in the fraction
 * portion of a number.  The default value is 3, which subclasses
 * can override.  When formatting, the exact behavior when this
 * value is exceeded is subclass-specific.  When parsing, this has
 * no effect.
 * @return the maximum number of fraction digits
 * @see #setMaximumFractionDigits
 */

public int getMaximumFractionDigits() { throw new RuntimeException("Stub!"); }

/**
 * Sets the maximum number of digits allowed in the fraction portion of a
 * number. This must be &gt;= minimumFractionDigits.  If the
 * new value for maximumFractionDigits is less than the current value
 * of minimumFractionDigits, then minimumFractionDigits will also be set to
 * the new value.
 * @param newValue the maximum number of fraction digits to be shown; if
 * less than zero, then zero is used. The concrete subclass may enforce an
 * upper limit to this value appropriate to the numeric type being formatted.
 * @see #getMaximumFractionDigits
 */

public void setMaximumFractionDigits(int newValue) { throw new RuntimeException("Stub!"); }

/**
 * Returns the minimum number of digits allowed in the fraction portion of a
 * number.  The default value is 0, which subclasses can override.
 * When formatting, if this value is not reached, numbers are padded on
 * the right with the locale-specific '0' character to ensure at least
 * this number of fraction digits.  When parsing, this has no effect.
 * @return the minimum number of fraction digits
 * @see #setMinimumFractionDigits
 */

public int getMinimumFractionDigits() { throw new RuntimeException("Stub!"); }

/**
 * Sets the minimum number of digits allowed in the fraction portion of a
 * number.  This must be &lt;= maximumFractionDigits.  If the
 * new value for minimumFractionDigits exceeds the current value
 * of maximumFractionDigits, then maximumFractionDigits will also be set to
 * the new value.
 * @param newValue the minimum number of fraction digits to be shown; if
 * less than zero, then zero is used.  Subclasses might enforce an
 * upper limit to this value appropriate to the numeric type being formatted.
 * @see #getMinimumFractionDigits
 */

public void setMinimumFractionDigits(int newValue) { throw new RuntimeException("Stub!"); }

/**
 * Sets the <tt>Currency</tt> object used to display currency
 * amounts.  This takes effect immediately, if this format is a
 * currency format.  If this format is not a currency format, then
 * the currency object is used if and when this object becomes a
 * currency format.
 * @param theCurrency new currency object to use.  May be null for
 * some subclasses.
 */

public void setCurrency(android.icu.util.Currency theCurrency) { throw new RuntimeException("Stub!"); }

/**
 * Returns the <tt>Currency</tt> object used to display currency
 * amounts.  This may be null.
 */

public android.icu.util.Currency getCurrency() { throw new RuntimeException("Stub!"); }

/**
 * Returns the rounding mode used in this NumberFormat.  The default implementation of
 * tis method in NumberFormat always throws <code>UnsupportedOperationException</code>.
 * @return A rounding mode, between <code>BigDecimal.ROUND_UP</code>
 * and <code>BigDecimal.ROUND_UNNECESSARY</code>.
 * @see #setRoundingMode(int)
 */

public int getRoundingMode() { throw new RuntimeException("Stub!"); }

/**
 * Set the rounding mode used in this NumberFormat.  The default implementation of
 * tis method in NumberFormat always throws <code>UnsupportedOperationException</code>.
 * @param roundingMode A rounding mode, between
 * <code>BigDecimal.ROUND_UP</code> and
 * <code>BigDecimal.ROUND_UNNECESSARY</code>.
 * @see #getRoundingMode()
 */

public void setRoundingMode(int roundingMode) { throw new RuntimeException("Stub!"); }

/**
 * Returns a specific style number format for a specific locale.
 * @param desiredLocale  the specific locale.
 * @param choice         number format style
 * @throws java.lang.IllegalArgumentException  if choice is not one of
 *                                   NUMBERSTYLE, CURRENCYSTYLE,
 *                                   PERCENTSTYLE, SCIENTIFICSTYLE,
 *                                   INTEGERSTYLE, ISOCURRENCYSTYLE,
 *                                   PLURALCURRENCYSTYLE, ACCOUNTINGCURRENCYSTYLE.
 *                                   CASHCURRENCYSTYLE, STANDARDCURRENCYSTYLE.
 */

public static android.icu.text.NumberFormat getInstance(android.icu.util.ULocale desiredLocale, int choice) { throw new RuntimeException("Stub!"); }

/**
 * Returns the pattern for the provided locale and choice.
 * @param forLocale the locale of the data.
 * @param choice the pattern format.
 * @return the pattern
 */

protected static java.lang.String getPattern(android.icu.util.ULocale forLocale, int choice) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Constant to specify currency style of format which uses currency symbol
 * to represent currency for accounting, for example: "($3.00), instead of
 * "-$3.00" ({@link #CURRENCYSTYLE}).
 * Overrides any style specified using -cf- key in locale.
 */

public static final int ACCOUNTINGCURRENCYSTYLE = 7; // 0x7

/**
 * <strong>[icu]</strong> Constant to specify currency cash style of format which uses currency
 * ISO code to represent currency, for example: "NT$3" instead of "NT$3.23".
 */

public static final int CASHCURRENCYSTYLE = 8; // 0x8

/**
 * <strong>[icu]</strong> Constant to specify general currency style of format. Defaults to
 * STANDARDCURRENCYSTYLE, using currency symbol, for example "$3.00", with
 * non-accounting style for negative values (e.g. minus sign).
 * The specific style may be specified using the -cf- locale key.
 */

public static final int CURRENCYSTYLE = 1; // 0x1

/**
 * Field constant used to construct a FieldPosition object. Signifies that
 * the position of the fraction part of a formatted number should be returned.
 * @see java.text.FieldPosition
 */

public static final int FRACTION_FIELD = 1; // 0x1

/**
 * <strong>[icu]</strong> Constant to specify a integer number style format.
 */

public static final int INTEGERSTYLE = 4; // 0x4

/**
 * Field constant used to construct a FieldPosition object. Signifies that
 * the position of the integer part of a formatted number should be returned.
 * @see java.text.FieldPosition
 */

public static final int INTEGER_FIELD = 0; // 0x0

/**
 * <strong>[icu]</strong> Constant to specify currency style of format which uses currency
 * ISO code to represent currency, for example: "USD3.00".
 */

public static final int ISOCURRENCYSTYLE = 5; // 0x5

/**
 * <strong>[icu]</strong> Constant to specify normal number style of format.
 */

public static final int NUMBERSTYLE = 0; // 0x0

/**
 * <strong>[icu]</strong> Constant to specify a style of format to display percent.
 */

public static final int PERCENTSTYLE = 2; // 0x2

/**
 * <strong>[icu]</strong> Constant to specify currency style of format which uses currency
 * long name with plural format to represent currency, for example,
 * "3.00 US Dollars".
 */

public static final int PLURALCURRENCYSTYLE = 6; // 0x6

/**
 * <strong>[icu]</strong> Constant to specify a style of format to display scientific number.
 */

public static final int SCIENTIFICSTYLE = 3; // 0x3

/**
 * <strong>[icu]</strong> Constant to specify currency style of format which uses currency symbol
 * to represent currency, for example "$3.00", using non-accounting style for
 * negative values (e.g. minus sign).
 * Overrides any style specified using -cf- key in locale.
 */

public static final int STANDARDCURRENCYSTYLE = 9; // 0x9
/**
 * The instances of this inner class are used as attribute keys and values
 * in AttributedCharacterIterator that
 * NumberFormat.formatToCharacterIterator() method returns.
 * <p>
 * There is no public constructor to this class, the only instances are the
 * constants defined here.
 * <p>
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static class Field extends java.text.Format.Field {

/**
 * Constructs a new instance of NumberFormat.Field with the given field
 * name.
 */

protected Field(java.lang.String fieldName) { super(null); throw new RuntimeException("Stub!"); }

/**
 * serizalization method resolve instances to the constant
 * NumberFormat.Field values
 */

protected java.lang.Object readResolve() throws java.io.InvalidObjectException { throw new RuntimeException("Stub!"); }

/**
 */

public static final android.icu.text.NumberFormat.Field COMPACT;
static { COMPACT = null; }

/**
 */

public static final android.icu.text.NumberFormat.Field CURRENCY;
static { CURRENCY = null; }

/**
 */

public static final android.icu.text.NumberFormat.Field DECIMAL_SEPARATOR;
static { DECIMAL_SEPARATOR = null; }

/**
 */

public static final android.icu.text.NumberFormat.Field EXPONENT;
static { EXPONENT = null; }

/**
 */

public static final android.icu.text.NumberFormat.Field EXPONENT_SIGN;
static { EXPONENT_SIGN = null; }

/**
 */

public static final android.icu.text.NumberFormat.Field EXPONENT_SYMBOL;
static { EXPONENT_SYMBOL = null; }

/**
 */

public static final android.icu.text.NumberFormat.Field FRACTION;
static { FRACTION = null; }

/**
 */

public static final android.icu.text.NumberFormat.Field GROUPING_SEPARATOR;
static { GROUPING_SEPARATOR = null; }

/**
 */

public static final android.icu.text.NumberFormat.Field INTEGER;
static { INTEGER = null; }

/**
 */

public static final android.icu.text.NumberFormat.Field MEASURE_UNIT;
static { MEASURE_UNIT = null; }

/**
 */

public static final android.icu.text.NumberFormat.Field PERCENT;
static { PERCENT = null; }

/**
 */

public static final android.icu.text.NumberFormat.Field PERMILLE;
static { PERMILLE = null; }

/**
 */

public static final android.icu.text.NumberFormat.Field SIGN;
static { SIGN = null; }
}

}

