/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html

package android.icu.text;

import android.icu.util.ULocale;
import android.icu.lang.UCharacter;
import java.text.ParsePosition;
import android.icu.util.ULocale.Category;
import android.icu.math.MathContext;
import android.icu.math.BigDecimal;
import java.math.RoundingMode;
import java.math.BigInteger;

/**
 * <strong>[icu enhancement]</strong> ICU's replacement for {@link java.text.DecimalFormat}.&nbsp;Methods, fields, and other functionality specific to ICU are labeled '<strong>[icu]</strong>'.
 *
 * <code>DecimalFormat</code> is the primary
 * concrete subclass of {@link android.icu.text.NumberFormat NumberFormat}. It has a variety of features designed to make it
 * possible to parse and format numbers in any locale, including support for Western, Arabic, or
 * Indic digits. It supports different flavors of numbers, including integers ("123"), fixed-point
 * numbers ("123.4"), scientific notation ("1.23E4"), percentages ("12%"), and currency amounts
 * ("$123.00", "USD123.00", "123.00 US dollars"). All of these flavors can be easily localized.
 *
 * <p>To obtain a number formatter for a specific locale (including the default locale), call one of
 * NumberFormat's factory methods such as {@link android.icu.text.NumberFormat#getInstance NumberFormat#getInstance}. Do not call
 * DecimalFormat constructors directly unless you know what you are doing.
 *
 * <p>DecimalFormat aims to comply with the specification <a
 * href="http://unicode.org/reports/tr35/tr35-numbers.html#Number_Format_Patterns">UTS #35</a>. Read
 * the specification for more information on how all the properties in DecimalFormat fit together.
 *
 * <p><strong>NOTE:</strong> Starting in ICU 60, there is a new set of APIs for localized number
 * formatting that are designed to be an improvement over DecimalFormat.  New users are discouraged
 * from using DecimalFormat.  For more information, see the package android.icu.number.
 *
 * <h3>Example Usage</h3>
 *
 * <p>Customize settings on a DecimalFormat instance from the NumberFormat factory:
 *
 * <blockquote>
 *
 * <pre>
 * NumberFormat f = NumberFormat.getInstance(loc);
 * if (f instanceof DecimalFormat) {
 *     ((DecimalFormat) f).setDecimalSeparatorAlwaysShown(true);
 *     ((DecimalFormat) f).setMinimumGroupingDigits(2);
 * }
 * </pre>
 *
 * </blockquote>
 *
 * <p>Quick and dirty print out a number using the localized number, currency, and percent format
 * for each locale:
 *
 * <blockquote>
 *
 * <pre>
 * for (ULocale uloc : ULocale.getAvailableLocales()) {
 *     System.out.print(uloc + ":\t");
 *     System.out.print(NumberFormat.getInstance(uloc).format(1.23));
 *     System.out.print("\t");
 *     System.out.print(NumberFormat.getCurrencyInstance(uloc).format(1.23));
 *     System.out.print("\t");
 *     System.out.print(NumberFormat.getPercentInstance(uloc).format(1.23));
 *     System.out.println();
 * }
 * </pre>
 *
 * </blockquote>
 *
 * <h3>Properties and Symbols</h3>
 *
 * <p>A DecimalFormat object encapsulates a set of <em>properties</em> and a set of
 * <em>symbols</em>. Grouping size, rounding mode, and affixes are examples of properties. Locale
 * digits and the characters used for grouping and decimal separators are examples of symbols.
 *
 * <p>To set a custom set of symbols, use {@link #setDecimalFormatSymbols}. Use the various other
 * setters in this class to set custom values for the properties.
 *
 * <h3>Rounding</h3>
 *
 * <p>DecimalFormat provides three main strategies to specify the position at which numbers should
 * be rounded:
 *
 * <ol>
 *   <li><strong>Magnitude:</strong> Display a fixed number of fraction digits; this is the most
 *       common form.
 *   <li><strong>Increment:</strong> Round numbers to the closest multiple of a certain increment,
 *       such as 0.05. This is common in currencies.
 *   <li><strong>Significant Digits:</strong> Round numbers such that a fixed number of nonzero
 *       digits are shown. This is most common in scientific notation.
 * </ol>
 *
 * <p>It is not possible to specify more than one rounding strategy. For example, setting a rounding
 * increment in conjunction with significant digits results in undefined behavior.
 *
 * <p>It is also possible to specify the <em>rounding mode</em> to use. The default rounding mode is
 * "half even", which rounds numbers to their closest increment, with ties broken in favor of
 * trailing numbers being even. For more information, see {@link #setRoundingMode} and <a
 * href="https://unicode-org.github.io/icu/userguide/format_parse/numbers/rounding-modes">the ICU
 * User Guide</a>.
 *
 * <h3>Pattern Strings</h3>
 *
 * <p>A <em>pattern string</em> is a way to serialize some of the available properties for decimal
 * formatting. However, not all properties are capable of being serialized into a pattern string;
 * see {@link #applyPattern} for more information.
 *
 * <p>Most users should not need to interface with pattern strings directly.
 *
 * <p>ICU DecimalFormat aims to follow the specification for pattern strings in <a
 * href="http://unicode.org/reports/tr35/tr35-numbers.html#Number_Format_Patterns">UTS #35</a>.
 * Refer to that specification for more information on pattern string syntax.
 *
 * <h4>Pattern String BNF</h4>
 *
 * The following BNF is used when parsing the pattern string into property values:
 *
 * <pre>
 * pattern    := subpattern (';' subpattern)?
 * subpattern := prefix? number exponent? suffix?
 * number     := (integer ('.' fraction)?) | sigDigits
 * prefix     := '&#92;u0000'..'&#92;uFFFD' - specialCharacters
 * suffix     := '&#92;u0000'..'&#92;uFFFD' - specialCharacters
 * integer    := '#'* '0'* '0'
 * fraction   := '0'* '#'*
 * sigDigits  := '#'* '@' '@'* '#'*
 * exponent   := 'E' '+'? '0'* '0'
 * padSpec    := '*' padChar
 * padChar    := '&#92;u0000'..'&#92;uFFFD' - quote
 * &#32;
 * Notation:
 *   X*       0 or more instances of X
 *   X?       0 or 1 instances of X
 *   X|Y      either X or Y
 *   C..D     any character from C up to D, inclusive
 *   S-T      characters in S, except those in T
 * </pre>
 *
 * <p>The first subpattern is for positive numbers. The second (optional) subpattern is for negative
 * numbers.
 *
 * <p>Not indicated in the BNF syntax above:
 *
 * <ul>
 *   <li>The grouping separator ',' can occur inside the integer and sigDigits elements, between any
 *       two pattern characters of that element, as long as the integer or sigDigits element is not
 *       followed by the exponent element.
 *   <li>Two grouping intervals are recognized: That between the decimal point and the first
 *       grouping symbol, and that between the first and second grouping symbols. These intervals
 *       are identical in most locales, but in some locales they differ. For example, the pattern
 *       &quot;#,##,###&quot; formats the number 123456789 as &quot;12,34,56,789&quot;.
 *   <li>The pad specifier <code>padSpec</code> may appear before the prefix, after the prefix,
 *       before the suffix, after the suffix, or not at all.
 *   <li>In place of '0', the digits '1' through '9' may be used to indicate a rounding increment.
 * </ul>
 *
 * <h3>Parsing</h3>
 *
 * <p>DecimalFormat aims to be able to parse anything that it can output as a formatted string.
 *
 * <p>There are two primary parse modes: <em>lenient</em> and <em>strict</em>. Lenient mode should
 * be used if the goal is to parse user input to a number; strict mode should be used if the goal is
 * validation. The default is lenient mode. For more information, see {@link #setParseStrict}.
 *
 * <p><code>DecimalFormat</code> parses all Unicode characters that represent decimal digits, as
 * defined by {@link android.icu.lang.UCharacter#digit UCharacter#digit}. In addition, <code>DecimalFormat</code> also recognizes as
 * digits the ten consecutive characters starting with the localized zero digit defined in the
 * {@link android.icu.text.DecimalFormatSymbols DecimalFormatSymbols} object. During formatting, the {@link android.icu.text.DecimalFormatSymbols DecimalFormatSymbols}-based
 * digits are output.
 *
 * <p>Grouping separators are ignored in lenient mode (default). In strict mode, grouping separators
 * must match the locale-specified grouping sizes.
 *
 * <p>When using {@link #parseCurrency}, all currencies are accepted, not just the currency
 * currently set in the formatter. In addition, the formatter is able to parse every currency style
 * format for a particular locale no matter which style the formatter is constructed with. For
 * example, a formatter instance gotten from NumberFormat.getInstance(ULocale,
 * NumberFormat.CURRENCYSTYLE) can parse both "USD1.00" and "3.00 US dollars".
 *
 * <p>Whitespace characters (lenient mode) and control characters (lenient and strict mode),
 * collectively called "ignorables", do not need to match in identity or quantity between the
 * pattern string and the input string. For example, the pattern "# %" matches "35 %" (with a single
 * space), "35%" (with no space), "35&nbsp;%" (with a non-breaking space), and "35&nbsp; %" (with
 * multiple spaces). Arbitrary ignorables are also allowed at boundaries between the parts of the
 * number: prefix, number, exponent separator, and suffix. Ignorable whitespace characters are those
 * having the Unicode "blank" property for regular expressions, defined in UTS #18 Annex C, which is
 * "horizontal" whitespace, like spaces and tabs, but not "vertical" whitespace, like line breaks.
 * Ignorable control characters are those in the Unicode set [:Default_Ignorable_Code_Point:].
 *
 * <p>If {@link #parse(java.lang.String,java.text.ParsePosition)} fails to parse a string, it returns <code>null</code>
 * and leaves the parse position unchanged. The convenience method {@link #parse(java.lang.String)} indicates
 * parse failure by throwing a {@link java.text.ParseException}.
 *
 * <p>Under the hood, a state table parsing engine is used. To debug a parsing failure during
 * development, use the following pattern to print details about the state table transitions:
 *
 * <pre>
 * android.icu.impl.number.Parse.DEBUGGING = true;
 * df.parse("123.45", ppos);
 * android.icu.impl.number.Parse.DEBUGGING = false;
 * </pre>
 *
 * <h3>Thread Safety and Best Practices</h3>
 *
 * <p>Starting with ICU 59, instances of DecimalFormat are thread-safe.
 *
 * <p>Under the hood, DecimalFormat maintains an immutable formatter object that is rebuilt whenever
 * any of the property setters are called. It is therefore best practice to call property setters
 * only during construction and not when formatting numbers online.
 *
 * @see java.text.Format
 * @see android.icu.text.NumberFormat
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class DecimalFormat extends android.icu.text.NumberFormat {

/**
 * Creates a DecimalFormat based on the number pattern and symbols for the default locale. This is
 * a convenient way to obtain a DecimalFormat instance when internationalization is not the main
 * concern.
 *
 * <p>Most users should call the factory methods on NumberFormat, such as {@link android.icu.text.NumberFormat#getNumberInstance  }, which return localized formatter objects, instead of the
 * DecimalFormat constructors.
 *
 * @see NumberFormat#getInstance
 * @see NumberFormat#getNumberInstance
 * @see NumberFormat#getCurrencyInstance
 * @see NumberFormat#getPercentInstance
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public DecimalFormat() { throw new RuntimeException("Stub!"); }

/**
 * Creates a DecimalFormat based on the given pattern, using symbols for the default locale. This
 * is a convenient way to obtain a DecimalFormat instance when internationalization is not the
 * main concern.
 *
 * <p>Most users should call the factory methods on NumberFormat, such as {@link android.icu.text.NumberFormat#getNumberInstance  }, which return localized formatter objects, instead of the
 * DecimalFormat constructors.
 *
 * @param pattern A pattern string such as "#,##0.00" conforming to <a
 *     href="http://unicode.org/reports/tr35/tr35-numbers.html#Number_Format_Patterns">UTS
 *     #35</a>.
 * @throws java.lang.IllegalArgumentException if the given pattern is invalid.
 * @see NumberFormat#getInstance
 * @see NumberFormat#getNumberInstance
 * @see NumberFormat#getCurrencyInstance
 * @see NumberFormat#getPercentInstance
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public DecimalFormat(java.lang.String pattern) { throw new RuntimeException("Stub!"); }

/**
 * Creates a DecimalFormat based on the given pattern and symbols. Use this constructor if you
 * want complete control over the behavior of the formatter.
 *
 * <p>Most users should call the factory methods on NumberFormat, such as {@link android.icu.text.NumberFormat#getNumberInstance  }, which return localized formatter objects, instead of the
 * DecimalFormat constructors.
 *
 * @param pattern A pattern string such as "#,##0.00" conforming to <a
 *     href="http://unicode.org/reports/tr35/tr35-numbers.html#Number_Format_Patterns">UTS
 *     #35</a>.
 * @param symbols The set of symbols to be used.
 * @exception java.lang.IllegalArgumentException if the given pattern is invalid
 * @see NumberFormat#getInstance
 * @see NumberFormat#getNumberInstance
 * @see NumberFormat#getCurrencyInstance
 * @see NumberFormat#getPercentInstance
 * @see android.icu.text.DecimalFormatSymbols
 */

public DecimalFormat(java.lang.String pattern, android.icu.text.DecimalFormatSymbols symbols) { throw new RuntimeException("Stub!"); }

/**
 * Creates a DecimalFormat based on the given pattern and symbols, with additional control over
 * the behavior of currency. The style argument determines whether currency rounding rules should
 * override the pattern, and the {@link android.icu.text.CurrencyPluralInfo CurrencyPluralInfo} object is used for customizing the
 * plural forms used for currency long names.
 *
 * <p>Most users should call the factory methods on NumberFormat, such as {@link android.icu.text.NumberFormat#getNumberInstance  }, which return localized formatter objects, instead of the
 * DecimalFormat constructors.
 *
 * @param pattern a non-localized pattern string
 * @param symbols the set of symbols to be used
 * @param infoInput the information used for currency plural format, including currency plural
 *     patterns and plural rules.
 * @param style the decimal formatting style, it is one of the following values:
 *     NumberFormat.NUMBERSTYLE; NumberFormat.CURRENCYSTYLE; NumberFormat.PERCENTSTYLE;
 *     NumberFormat.SCIENTIFICSTYLE; NumberFormat.INTEGERSTYLE; NumberFormat.ISOCURRENCYSTYLE;
 *     NumberFormat.PLURALCURRENCYSTYLE;
 */

public DecimalFormat(java.lang.String pattern, android.icu.text.DecimalFormatSymbols symbols, android.icu.text.CurrencyPluralInfo infoInput, int style) { throw new RuntimeException("Stub!"); }

/**
 * Parses the given pattern string and overwrites the settings specified in the pattern string.
 * The properties corresponding to the following setters are overwritten, either with their
 * default values or with the value specified in the pattern string:
 *
 * <ol>
 *   <li>{@link #setDecimalSeparatorAlwaysShown}
 *   <li>{@link #setExponentSignAlwaysShown}
 *   <li>{@link #setFormatWidth}
 *   <li>{@link #setGroupingSize}
 *   <li>{@link #setMultiplier} (percent/permille)
 *   <li>{@link #setMaximumFractionDigits}
 *   <li>{@link #setMaximumIntegerDigits}
 *   <li>{@link #setMaximumSignificantDigits}
 *   <li>{@link #setMinimumExponentDigits}
 *   <li>{@link #setMinimumFractionDigits}
 *   <li>{@link #setMinimumIntegerDigits}
 *   <li>{@link #setMinimumSignificantDigits}
 *   <li>{@link #setPadPosition}
 *   <li>{@link #setPadCharacter}
 *   <li>{@link #setRoundingIncrement}
 *   <li>{@link #setSecondaryGroupingSize}
 * </ol>
 *
 * All other settings remain untouched.
 *
 * <p>For more information on pattern strings, see <a
 * href="http://unicode.org/reports/tr35/tr35-numbers.html#Number_Format_Patterns">UTS #35</a>.
 */

public synchronized void applyPattern(java.lang.String pattern) { throw new RuntimeException("Stub!"); }

/**
 * Converts the given string to standard notation and then parses it using {@link #applyPattern}.
 * This method is provided for backwards compatibility and should not be used in new projects.
 *
 * <p>Localized notation means that instead of using generic placeholders in the pattern, you use
 * the corresponding locale-specific characters instead. For example, in locale <em>fr-FR</em>,
 * the period in the pattern "0.000" means "decimal" in standard notation (as it does in every
 * other locale), but it means "grouping" in localized notation.
 *
 * @param localizedPattern The pattern string in localized notation.
 */

public synchronized void applyLocalizedPattern(java.lang.String localizedPattern) { throw new RuntimeException("Stub!"); }

/***/

public java.lang.Object clone() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.StringBuffer format(double number, java.lang.StringBuffer result, java.text.FieldPosition fieldPosition) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.StringBuffer format(long number, java.lang.StringBuffer result, java.text.FieldPosition fieldPosition) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.StringBuffer format(java.math.BigInteger number, java.lang.StringBuffer result, java.text.FieldPosition fieldPosition) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.StringBuffer format(java.math.BigDecimal number, java.lang.StringBuffer result, java.text.FieldPosition fieldPosition) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.StringBuffer format(android.icu.math.BigDecimal number, java.lang.StringBuffer result, java.text.FieldPosition fieldPosition) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.text.AttributedCharacterIterator formatToCharacterIterator(java.lang.Object obj) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.StringBuffer format(android.icu.util.CurrencyAmount currAmt, java.lang.StringBuffer result, java.text.FieldPosition fieldPosition) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.Number parse(java.lang.String text, java.text.ParsePosition parsePosition) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public android.icu.util.CurrencyAmount parseCurrency(java.lang.CharSequence text, java.text.ParsePosition parsePosition) { throw new RuntimeException("Stub!"); }

/**
 * Returns a copy of the decimal format symbols used by this formatter.
 *
 * @return desired DecimalFormatSymbols
 * @see android.icu.text.DecimalFormatSymbols
 */

public synchronized android.icu.text.DecimalFormatSymbols getDecimalFormatSymbols() { throw new RuntimeException("Stub!"); }

/**
 * Sets the decimal format symbols used by this formatter. The formatter uses a copy of the
 * provided symbols.
 *
 * @param newSymbols desired DecimalFormatSymbols
 * @see android.icu.text.DecimalFormatSymbols
 */

public synchronized void setDecimalFormatSymbols(android.icu.text.DecimalFormatSymbols newSymbols) { throw new RuntimeException("Stub!"); }

/**
 * <strong>Affixes:</strong> Gets the positive prefix string currently being used to format
 * numbers.
 *
 * <p>If the affix was specified via the pattern, the string returned by this method will have
 * locale symbols substituted in place of special characters according to the LDML specification.
 * If the affix was specified via {@link #setPositivePrefix}, the string will be returned
 * literally.
 *
 * @return The string being prepended to positive numbers.
 */

public synchronized java.lang.String getPositivePrefix() { throw new RuntimeException("Stub!"); }

/**
 * <strong>Affixes:</strong> Sets the string to prepend to positive numbers. For example, if you
 * set the value "#", then the number 123 will be formatted as "#123" in the locale
 * <em>en-US</em>.
 *
 * <p>Using this method overrides the affix specified via the pattern, and unlike the pattern, the
 * string given to this method will be interpreted literally WITHOUT locale symbol substitutions.
 *
 * @param prefix The literal string to prepend to positive numbers.
 */

public synchronized void setPositivePrefix(java.lang.String prefix) { throw new RuntimeException("Stub!"); }

/**
 * <strong>Affixes:</strong> Gets the negative prefix string currently being used to format
 * numbers.
 *
 * <p>If the affix was specified via the pattern, the string returned by this method will have
 * locale symbols substituted in place of special characters according to the LDML specification.
 * If the affix was specified via {@link #setNegativePrefix}, the string will be returned
 * literally.
 *
 * @return The string being prepended to negative numbers.
 */

public synchronized java.lang.String getNegativePrefix() { throw new RuntimeException("Stub!"); }

/**
 * <strong>Affixes:</strong> Sets the string to prepend to negative numbers. For example, if you
 * set the value "#", then the number -123 will be formatted as "#123" in the locale
 * <em>en-US</em> (overriding the implicit default '-' in the pattern).
 *
 * <p>Using this method overrides the affix specified via the pattern, and unlike the pattern, the
 * string given to this method will be interpreted literally WITHOUT locale symbol substitutions.
 *
 * @param prefix The literal string to prepend to negative numbers.
 */

public synchronized void setNegativePrefix(java.lang.String prefix) { throw new RuntimeException("Stub!"); }

/**
 * <strong>Affixes:</strong> Gets the positive suffix string currently being used to format
 * numbers.
 *
 * <p>If the affix was specified via the pattern, the string returned by this method will have
 * locale symbols substituted in place of special characters according to the LDML specification.
 * If the affix was specified via {@link #setPositiveSuffix}, the string will be returned
 * literally.
 *
 * @return The string being appended to positive numbers.
 */

public synchronized java.lang.String getPositiveSuffix() { throw new RuntimeException("Stub!"); }

/**
 * <strong>Affixes:</strong> Sets the string to append to positive numbers. For example, if you
 * set the value "#", then the number 123 will be formatted as "123#" in the locale
 * <em>en-US</em>.
 *
 * <p>Using this method overrides the affix specified via the pattern, and unlike the pattern, the
 * string given to this method will be interpreted literally WITHOUT locale symbol substitutions.
 *
 * @param suffix The literal string to append to positive numbers.
 */

public synchronized void setPositiveSuffix(java.lang.String suffix) { throw new RuntimeException("Stub!"); }

/**
 * <strong>Affixes:</strong> Gets the negative suffix string currently being used to format
 * numbers.
 *
 * <p>If the affix was specified via the pattern, the string returned by this method will have
 * locale symbols substituted in place of special characters according to the LDML specification.
 * If the affix was specified via {@link #setNegativeSuffix}, the string will be returned
 * literally.
 *
 * @return The string being appended to negative numbers.
 */

public synchronized java.lang.String getNegativeSuffix() { throw new RuntimeException("Stub!"); }

/**
 * <strong>Affixes:</strong> Sets the string to append to negative numbers. For example, if you
 * set the value "#", then the number 123 will be formatted as "123#" in the locale
 * <em>en-US</em>.
 *
 * <p>Using this method overrides the affix specified via the pattern, and unlike the pattern, the
 * string given to this method will be interpreted literally WITHOUT locale symbol substitutions.
 *
 * @param suffix The literal string to append to negative numbers.
 */

public synchronized void setNegativeSuffix(java.lang.String suffix) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns whether the sign is being shown on positive numbers.
 *
 * @return Whether the sign is shown on positive numbers and zero.
 * @see #setSignAlwaysShown
 */

public synchronized boolean isSignAlwaysShown() { throw new RuntimeException("Stub!"); }

/**
 * Sets whether to always shown the plus sign ('+' in <em>en</em>) on positive numbers. The rules
 * in UTS #35 section 3.2.1 will be followed to ensure a locale-aware placement of the sign.
 *
 * <p>More specifically, the following strategy will be used to place the plus sign:
 *
 * <ol>
 *   <li><em>Patterns without a negative subpattern:</em> The locale's plus sign will be prepended
 *       to the positive prefix.
 *   <li><em>Patterns with a negative subpattern without a '-' sign (e.g., accounting):</em> The
 *       locale's plus sign will be prepended to the positive prefix, as in case 1.
 *   <li><em>Patterns with a negative subpattern that has a '-' sign:</em> The locale's plus sign
 *       will substitute the '-' in the negative subpattern. The positive subpattern will be
 *       unused.
 * </ol>
 *
 * This method is designed to be used <em>instead of</em> applying a pattern containing an
 * explicit plus sign, such as "+0;-0". The behavior when combining this method with explicit plus
 * signs in the pattern is undefined.
 *
 * @param value true to always show a sign; false to hide the sign on positive numbers and zero.
 */

public synchronized void setSignAlwaysShown(boolean value) { throw new RuntimeException("Stub!"); }

/**
 * Returns the multiplier being applied to numbers before they are formatted.
 *
 * @see #setMultiplier
 */

public synchronized int getMultiplier() { throw new RuntimeException("Stub!"); }

/**
 * Sets a number that will be used to multiply all numbers prior to formatting. For example, when
 * formatting percents, a multiplier of 100 can be used.
 *
 * <p>If a percent or permille sign is specified in the pattern, the multiplier is automatically
 * set to 100 or 1000, respectively.
 *
 * <p>If the number specified here is a power of 10, a more efficient code path will be used.
 *
 * @param multiplier The number by which all numbers passed to {@link #format} will be multiplied.
 * @throws java.lang.IllegalArgumentException If the given multiplier is zero.
 * @throws java.lang.ArithmeticException when inverting multiplier produces a non-terminating decimal result
 *         in conjunction with MathContext of unlimited precision.
 */

public synchronized void setMultiplier(int multiplier) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the increment to which numbers are being rounded.
 *
 * @see #setRoundingIncrement
 */

public synchronized java.math.BigDecimal getRoundingIncrement() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> <strong>Rounding and Digit Limits:</strong> Sets an increment, or interval, to which
 * numbers are rounded. For example, a rounding increment of 0.05 will cause the number 1.23 to be
 * rounded to 1.25 in the default rounding mode.
 *
 * <p>The rounding increment can be specified via the pattern string: for example, the pattern
 * "#,##0.05" encodes a rounding increment of 0.05.
 *
 * <p>The rounding increment is applied <em>after</em> any multipliers might take effect; for
 * example, in scientific notation or when {@link #setMultiplier} is used.
 *
 * <p>See {@link #setMaximumFractionDigits} and {@link #setMaximumSignificantDigits} for two other
 * ways of specifying rounding strategies.
 *
 * @param increment The increment to which numbers are to be rounded.
 * @see #setRoundingMode
 * @see #setMaximumFractionDigits
 * @see #setMaximumSignificantDigits
 */

public synchronized void setRoundingIncrement(java.math.BigDecimal increment) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> <strong>Rounding and Digit Limits:</strong> Overload of {@link
 * #setRoundingIncrement(java.math.BigDecimal)}.
 *
 * @param increment The increment to which numbers are to be rounded.
 * @see #setRoundingIncrement
 */

public synchronized void setRoundingIncrement(android.icu.math.BigDecimal increment) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> <strong>Rounding and Digit Limits:</strong> Overload of {@link
 * #setRoundingIncrement(java.math.BigDecimal)}.
 *
 * @param increment The increment to which numbers are to be rounded.
 * @see #setRoundingIncrement
 */

public synchronized void setRoundingIncrement(double increment) { throw new RuntimeException("Stub!"); }

/**
 * Returns the rounding mode being used to round numbers.
 *
 * @see #setRoundingMode
 */

public synchronized int getRoundingMode() { throw new RuntimeException("Stub!"); }

/**
 * <strong>Rounding and Digit Limits:</strong> Sets the {@link java.math.RoundingMode RoundingMode} used to round
 * numbers. The default rounding mode is HALF_EVEN, which rounds decimals to their closest whole
 * number, and rounds to the closest even number if at the midpoint.
 *
 * <p>For more detail on rounding modes, see <a
 * href="https://unicode-org.github.io/icu/userguide/format_parse/numbers/rounding-modes">the ICU
 * User Guide</a>.
 *
 * <p>For backwards compatibility, the rounding mode is specified as an int argument, which can be
 * from either the constants in {@link android.icu.math.BigDecimal BigDecimal} or the ordinal value of {@link java.math.RoundingMode RoundingMode}.
 * The following two calls are functionally equivalent.
 *
 * <pre>
 * df.setRoundingMode(BigDecimal.ROUND_CEILING);
 * df.setRoundingMode(RoundingMode.CEILING.ordinal());
 * </pre>
 *
 * @param roundingMode The integer constant rounding mode to use when formatting numbers.
 */

public synchronized void setRoundingMode(int roundingMode) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the {@link java.math.MathContext} being used to round numbers.
 *
 * @see #setMathContext
 */

public synchronized java.math.MathContext getMathContext() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> <strong>Rounding and Digit Limits:</strong> Sets the {@link java.math.MathContext} used
 * to round numbers. A "math context" encodes both a rounding mode and a number of significant
 * digits. Most users should call {@link #setRoundingMode} and/or {@link
 * #setMaximumSignificantDigits} instead of this method.
 *
 * <p>When formatting, since no division is ever performed, the default MathContext is unlimited
 * significant digits. However, when division occurs during parsing to correct for percentages and
 * multipliers, a MathContext of 34 digits, the IEEE 754R Decimal128 standard, is used by default.
 * If you require more than 34 digits when parsing, you can set a custom MathContext using this
 * method.
 *
 * @param mathContext The MathContext to use when rounding numbers.
 * @throws java.lang.ArithmeticException when inverting multiplier produces a non-terminating decimal result
 *         in conjunction with MathContext of unlimited precision.
 * @see java.math.MathContext
 */

public synchronized void setMathContext(java.math.MathContext mathContext) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the {@link android.icu.math.MathContext} being used to round numbers.
 *
 * @see #setMathContext
 */

public synchronized android.icu.math.MathContext getMathContextICU() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> <strong>Rounding and Digit Limits:</strong> Overload of {@link #setMathContext} for
 * {@link android.icu.math.MathContext}.
 *
 * @param mathContextICU The MathContext to use when rounding numbers.
 * @throws java.lang.ArithmeticException when inverting multiplier produces a non-terminating decimal result
 *         in conjunction with MathContext of unlimited precision.
 * @see #setMathContext(java.math.MathContext)
 */

public synchronized void setMathContextICU(android.icu.math.MathContext mathContextICU) { throw new RuntimeException("Stub!"); }

/**
 * Returns the effective minimum number of digits before the decimal separator.
 *
 * @see #setMinimumIntegerDigits
 */

public synchronized int getMinimumIntegerDigits() { throw new RuntimeException("Stub!"); }

/**
 * <strong>Rounding and Digit Limits:</strong> Sets the minimum number of digits to display before
 * the decimal separator. If the number has fewer than this many digits, the number is padded with
 * zeros.
 *
 * <p>For example, if minimum integer digits is 3, the number 12.3 will be printed as "001.23".
 *
 * <p>Minimum integer and minimum and maximum fraction digits can be specified via the pattern
 * string. For example, "#,#00.00#" has 2 minimum integer digits, 2 minimum fraction digits, and 3
 * maximum fraction digits. Note that it is not possible to specify maximium integer digits in the
 * pattern except in scientific notation.
 *
 * <p>If minimum and maximum integer, fraction, or significant digits conflict with each other,
 * the most recently specified value is used. For example, if there is a formatter with minInt=5,
 * and then you set maxInt=3, then minInt will be changed to 3.
 *
 * @param value The minimum number of digits before the decimal separator.
 */

public synchronized void setMinimumIntegerDigits(int value) { throw new RuntimeException("Stub!"); }

/**
 * Returns the effective maximum number of digits before the decimal separator.
 *
 * @see #setMaximumIntegerDigits
 */

public synchronized int getMaximumIntegerDigits() { throw new RuntimeException("Stub!"); }

/**
 * <strong>Rounding and Digit Limits:</strong> Sets the maximum number of digits to display before
 * the decimal separator. If the number has more than this many digits, the number is truncated.
 *
 * <p>For example, if maximum integer digits is 3, the number 12345 will be printed as "345".
 *
 * <p>Minimum integer and minimum and maximum fraction digits can be specified via the pattern
 * string. For example, "#,#00.00#" has 2 minimum integer digits, 2 minimum fraction digits, and 3
 * maximum fraction digits. Note that it is not possible to specify maximium integer digits in the
 * pattern except in scientific notation.
 *
 * <p>If minimum and maximum integer, fraction, or significant digits conflict with each other,
 * the most recently specified value is used. For example, if there is a formatter with minInt=5,
 * and then you set maxInt=3, then minInt will be changed to 3.
 *
 * @param value The maximum number of digits before the decimal separator.
 */

public synchronized void setMaximumIntegerDigits(int value) { throw new RuntimeException("Stub!"); }

/**
 * Returns the effective minimum number of integer digits after the decimal separator.
 *
 * @see #setMaximumIntegerDigits
 */

public synchronized int getMinimumFractionDigits() { throw new RuntimeException("Stub!"); }

/**
 * <strong>Rounding and Digit Limits:</strong> Sets the minimum number of digits to display after
 * the decimal separator. If the number has fewer than this many digits, the number is padded with
 * zeros.
 *
 * <p>For example, if minimum fraction digits is 2, the number 123.4 will be printed as "123.40".
 *
 * <p>Minimum integer and minimum and maximum fraction digits can be specified via the pattern
 * string. For example, "#,#00.00#" has 2 minimum integer digits, 2 minimum fraction digits, and 3
 * maximum fraction digits. Note that it is not possible to specify maximium integer digits in the
 * pattern except in scientific notation.
 *
 * <p>If minimum and maximum integer, fraction, or significant digits conflict with each other,
 * the most recently specified value is used. For example, if there is a formatter with minInt=5,
 * and then you set maxInt=3, then minInt will be changed to 3.
 *
 * <p>See {@link #setRoundingIncrement} and {@link #setMaximumSignificantDigits} for two other
 * ways of specifying rounding strategies.
 *
 * @param value The minimum number of integer digits after the decimal separator.
 * @see #setRoundingMode
 * @see #setRoundingIncrement
 * @see #setMaximumSignificantDigits
 */

public synchronized void setMinimumFractionDigits(int value) { throw new RuntimeException("Stub!"); }

/**
 * Returns the effective maximum number of integer digits after the decimal separator.
 *
 * @see #setMaximumIntegerDigits
 */

public synchronized int getMaximumFractionDigits() { throw new RuntimeException("Stub!"); }

/**
 * <strong>Rounding and Digit Limits:</strong> Sets the maximum number of digits to display after
 * the decimal separator. If the number has more than this many digits, the number is rounded
 * according to the rounding mode.
 *
 * <p>For example, if maximum fraction digits is 2, the number 123.456 will be printed as
 * "123.46".
 *
 * <p>Minimum integer and minimum and maximum fraction digits can be specified via the pattern
 * string. For example, "#,#00.00#" has 2 minimum integer digits, 2 minimum fraction digits, and 3
 * maximum fraction digits. Note that it is not possible to specify maximium integer digits in the
 * pattern except in scientific notation.
 *
 * <p>If minimum and maximum integer, fraction, or significant digits conflict with each other,
 * the most recently specified value is used. For example, if there is a formatter with minInt=5,
 * and then you set maxInt=3, then minInt will be changed to 3.
 *
 * @param value The maximum number of integer digits after the decimal separator.
 * @see #setRoundingMode
 */

public synchronized void setMaximumFractionDigits(int value) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns whether significant digits are being used in rounding.
 *
 * @see #setSignificantDigitsUsed
 */

public synchronized boolean areSignificantDigitsUsed() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> <strong>Rounding and Digit Limits:</strong> Sets whether significant digits are to be
 * used in rounding.
 *
 * <p>Calling <code>df.setSignificantDigitsUsed(true)</code> is functionally equivalent to:
 *
 * <pre>
 * df.setMinimumSignificantDigits(1);
 * df.setMaximumSignificantDigits(6);
 * </pre>
 *
 * @param useSignificantDigits true to enable significant digit rounding; false to disable it.
 */

public synchronized void setSignificantDigitsUsed(boolean useSignificantDigits) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the effective minimum number of significant digits displayed.
 *
 * @see #setMinimumSignificantDigits
 */

public synchronized int getMinimumSignificantDigits() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> <strong>Rounding and Digit Limits:</strong> Sets the minimum number of significant
 * digits to be displayed. If the number of significant digits is less than this value, the number
 * will be padded with zeros as necessary.
 *
 * <p>For example, if minimum significant digits is 3 and the number is 1.2, the number will be
 * printed as "1.20".
 *
 * <p>If minimum and maximum integer, fraction, or significant digits conflict with each other,
 * the most recently specified value is used. For example, if there is a formatter with minInt=5,
 * and then you set maxInt=3, then minInt will be changed to 3.
 *
 * @param value The minimum number of significant digits to display.
 */

public synchronized void setMinimumSignificantDigits(int value) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the effective maximum number of significant digits displayed.
 *
 * @see #setMaximumSignificantDigits
 */

public synchronized int getMaximumSignificantDigits() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> <strong>Rounding and Digit Limits:</strong> Sets the maximum number of significant
 * digits to be displayed. If the number of significant digits in the number exceeds this value,
 * the number will be rounded according to the current rounding mode.
 *
 * <p>For example, if maximum significant digits is 3 and the number is 12345, the number will be
 * printed as "12300".
 *
 * <p>If minimum and maximum integer, fraction, or significant digits conflict with each other,
 * the most recently specified value is used. For example, if there is a formatter with minInt=5,
 * and then you set maxInt=3, then minInt will be changed to 3.
 *
 * <p>See {@link #setRoundingIncrement} and {@link #setMaximumFractionDigits} for two other ways
 * of specifying rounding strategies.
 *
 * @param value The maximum number of significant digits to display.
 * @see #setRoundingMode
 * @see #setRoundingIncrement
 * @see #setMaximumFractionDigits
 */

public synchronized void setMaximumSignificantDigits(int value) { throw new RuntimeException("Stub!"); }

/**
 * Returns the minimum number of characters in formatted output.
 *
 * @see #setFormatWidth
 */

public synchronized int getFormatWidth() { throw new RuntimeException("Stub!"); }

/**
 * <strong>Padding:</strong> Sets the minimum width of the string output by the formatting
 * pipeline. For example, if padding is enabled and paddingWidth is set to 6, formatting the
 * number "3.14159" with the pattern "0.00" will result in "??3.14" if '?' is your padding string.
 *
 * <p>If the number is longer than your padding width, the number will display as if no padding
 * width had been specified, which may result in strings longer than the padding width.
 *
 * <p>Padding can be specified in the pattern string using the '*' symbol. For example, the format
 * "*x######0" has a format width of 7 and a pad character of 'x'.
 *
 * <p>Padding is currently counted in UTF-16 code units; see <a
 * href="http://bugs.icu-project.org/trac/ticket/13034">ticket #13034</a> for more information.
 *
 * @param width The minimum number of characters in the output.
 * @see #setPadCharacter
 * @see #setPadPosition
 */

public synchronized void setFormatWidth(int width) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the character used for padding.
 *
 * @see #setPadCharacter
 */

public synchronized char getPadCharacter() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> <strong>Padding:</strong> Sets the character used to pad numbers that are narrower than
 * the width specified in {@link #setFormatWidth}.
 *
 * <p>In the pattern string, the padding character is the token that follows '*' before or after
 * the prefix or suffix.
 *
 * @param padChar The character used for padding.
 * @see #setFormatWidth
 */

public synchronized void setPadCharacter(char padChar) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the position used for padding.
 *
 * @see #setPadPosition
 */

public synchronized int getPadPosition() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> <strong>Padding:</strong> Sets the position where to insert the pad character when
 * narrower than the width specified in {@link #setFormatWidth}. For example, consider the pattern
 * "P123S" with padding width 8 and padding char "*". The four positions are:
 *
 * <ul>
 *   <li>{@link android.icu.text.DecimalFormat#PAD_BEFORE_PREFIX DecimalFormat#PAD_BEFORE_PREFIX} ? "***P123S"
 *   <li>{@link android.icu.text.DecimalFormat#PAD_AFTER_PREFIX DecimalFormat#PAD_AFTER_PREFIX} ? "P***123S"
 *   <li>{@link android.icu.text.DecimalFormat#PAD_BEFORE_SUFFIX DecimalFormat#PAD_BEFORE_SUFFIX} ? "P123***S"
 *   <li>{@link android.icu.text.DecimalFormat#PAD_AFTER_SUFFIX DecimalFormat#PAD_AFTER_SUFFIX} ? "P123S***"
 * </ul>
 *
 * @param padPos The position used for padding.
 * @see #setFormatWidth
 */

public synchronized void setPadPosition(int padPos) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns whether scientific (exponential) notation is enabled on this formatter.
 *
 * @see #setScientificNotation
 */

public synchronized boolean isScientificNotation() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> <strong>Scientific Notation:</strong> Sets whether this formatter should print in
 * scientific (exponential) notation. For example, if scientific notation is enabled, the number
 * 123000 will be printed as "1.23E5" in locale <em>en-US</em>. A locale-specific symbol is used
 * as the exponent separator.
 *
 * <p>Calling <code>df.setScientificNotation(true)</code> is functionally equivalent to calling
 * <code>df.setMinimumExponentDigits(1)</code>.
 *
 * @param useScientific true to enable scientific notation; false to disable it.
 * @see #setMinimumExponentDigits
 */

public synchronized void setScientificNotation(boolean useScientific) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the minimum number of digits printed in the exponent in scientific notation.
 *
 * @see #setMinimumExponentDigits
 */

public synchronized byte getMinimumExponentDigits() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> <strong>Scientific Notation:</strong> Sets the minimum number of digits to be printed in
 * the exponent. For example, if minimum exponent digits is 3, the number 123000 will be printed
 * as "1.23E005".
 *
 * <p>This setting corresponds to the number of zeros after the 'E' in a pattern string such as
 * "0.00E000".
 *
 * @param minExpDig The minimum number of digits in the exponent.
 */

public synchronized void setMinimumExponentDigits(byte minExpDig) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns whether the sign (plus or minus) is always printed in scientific notation.
 *
 * @see #setExponentSignAlwaysShown
 */

public synchronized boolean isExponentSignAlwaysShown() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> <strong>Scientific Notation:</strong> Sets whether the sign (plus or minus) is always to
 * be shown in the exponent in scientific notation. For example, if this setting is enabled, the
 * number 123000 will be printed as "1.23E+5" in locale <em>en-US</em>. The number 0.0000123 will
 * always be printed as "1.23E-5" in locale <em>en-US</em> whether or not this setting is enabled.
 *
 * <p>This setting corresponds to the '+' in a pattern such as "0.00E+0".
 *
 * @param expSignAlways true to always shown the sign in the exponent; false to show it for
 *     negatives but not positives.
 */

public synchronized void setExponentSignAlwaysShown(boolean expSignAlways) { throw new RuntimeException("Stub!"); }

/**
 * Returns whether or not grouping separators are being printed in the output.
 *
 * @see #setGroupingUsed
 */

public synchronized boolean isGroupingUsed() { throw new RuntimeException("Stub!"); }

/**
 * <strong>Grouping:</strong> Sets whether grouping is to be used when formatting numbers.
 * Grouping means whether the thousands, millions, billions, and larger powers of ten should be
 * separated by a grouping separator (a comma in <em>en-US</em>).
 *
 * <p>For example, if grouping is enabled, 12345 will be printed as "12,345" in <em>en-US</em>. If
 * grouping were disabled, it would instead be printed as simply "12345".
 *
 * @param enabled true to enable grouping separators; false to disable them.
 * @see #setGroupingSize
 * @see #setSecondaryGroupingSize
 */

public synchronized void setGroupingUsed(boolean enabled) { throw new RuntimeException("Stub!"); }

/**
 * Returns the primary grouping size in use.
 *
 * @see #setGroupingSize
 */

public synchronized int getGroupingSize() { throw new RuntimeException("Stub!"); }

/**
 * <strong>Grouping:</strong> Sets the primary grouping size (distance between grouping
 * separators) used when formatting large numbers. For most locales, this defaults to 3: the
 * number of digits between the ones and thousands place, between thousands and millions, and so
 * forth.
 *
 * <p>For example, with a grouping size of 3, the number 1234567 will be formatted as "1,234,567".
 *
 * <p>Grouping size can also be specified in the pattern: for example, "#,##0" corresponds to a
 * grouping size of 3.
 *
 * @param width The grouping size to use.
 * @see #setSecondaryGroupingSize
 */

public synchronized void setGroupingSize(int width) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the secondary grouping size in use.
 *
 * @see #setSecondaryGroupingSize
 */

public synchronized int getSecondaryGroupingSize() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> <strong>Grouping:</strong> Sets the secondary grouping size (distance between grouping
 * separators after the first separator) used when formatting large numbers. In many south Asian
 * locales, this is set to 2.
 *
 * <p>For example, with primary grouping size 3 and secondary grouping size 2, the number 1234567
 * will be formatted as "12,34,567".
 *
 * <p>Grouping size can also be specified in the pattern: for example, "#,##,##0" corresponds to a
 * primary grouping size of 3 and a secondary grouping size of 2.
 *
 * @param width The secondary grouping size to use.
 * @see #setGroupingSize
 */

public synchronized void setSecondaryGroupingSize(int width) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the minimum number of digits before grouping is triggered.
 *
 * @see #setMinimumGroupingDigits
 */

public synchronized int getMinimumGroupingDigits() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Sets the minimum number of digits that must be before the first grouping separator in
 * order for the grouping separator to be printed. For example, if minimum grouping digits is set
 * to 2, in <em>en-US</em>, 1234 will be printed as "1234" and 12345 will be printed as "12,345".
 *
 * Set the value to:
 * <ul>
 * <li>1 to turn off minimum grouping digits.</li>
 * <li>MINIMUM_GROUPING_DIGITS_AUTO to display grouping using the default
 * strategy for all locales.</li>
 * <li>MINIMUM_GROUPING_DIGITS_MIN2 to display grouping using locale defaults,
 * except do not show grouping on values smaller than 10000 (such that there is a minimum of
 * two digits before the first separator).</li>
 * </ul>
 *
 * @param number The minimum number of digits before grouping is triggered.
 */

public synchronized void setMinimumGroupingDigits(int number) { throw new RuntimeException("Stub!"); }

/**
 * Returns whether the decimal separator is shown on integers.
 *
 * @see #setDecimalSeparatorAlwaysShown
 */

public synchronized boolean isDecimalSeparatorAlwaysShown() { throw new RuntimeException("Stub!"); }

/**
 * <strong>Separators:</strong> Sets whether the decimal separator (a period in <em>en-US</em>) is
 * shown on integers. For example, if this setting is turned on, formatting 123 will result in
 * "123." with the decimal separator.
 *
 * <p>This setting can be specified in the pattern for integer formats: "#,##0." is an example.
 *
 * @param value true to always show the decimal separator; false to show it only when there is a
 *     fraction part of the number.
 */

public synchronized void setDecimalSeparatorAlwaysShown(boolean value) { throw new RuntimeException("Stub!"); }

/**
 * Returns the currency used to display currency amounts. May be null.
 *
 * @see #setCurrency
 * @see android.icu.text.DecimalFormatSymbols#getCurrency
 */

public synchronized android.icu.util.Currency getCurrency() { throw new RuntimeException("Stub!"); }

/**
 * Sets the currency to be used when formatting numbers. The effect is twofold:
 *
 * <ol>
 *   <li>Substitutions for currency symbols in the pattern string will use this currency
 *   <li>The rounding mode will obey the rules for this currency (see {@link #setCurrencyUsage})
 * </ol>
 *
 * <strong>Important:</strong> Displaying the currency in the output requires that the patter
 * associated with this formatter contains a currency symbol '?'. This will be the case if the
 * instance was created via {@link #getCurrencyInstance} or one of its friends.
 *
 * @param currency The currency to use.
 */

public synchronized void setCurrency(android.icu.util.Currency currency) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the strategy for rounding currency amounts.
 *
 * @see #setCurrencyUsage
 */

public synchronized android.icu.util.Currency.CurrencyUsage getCurrencyUsage() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Sets the currency-dependent strategy to use when rounding numbers. There are two
 * strategies:
 *
 * <ul>
 *   <li>STANDARD: When the amount displayed is intended for banking statements or electronic
 *       transfer.
 *   <li>CASH: When the amount displayed is intended to be representable in physical currency,
 *       like at a cash register.
 * </ul>
 *
 * CASH mode is relevant in currencies that do not have tender down to the penny. For more
 * information on the two rounding strategies, see <a
 * href="http://unicode.org/reports/tr35/tr35-numbers.html#Supplemental_Currency_Data">UTS
 * #35</a>. If omitted, the strategy defaults to STANDARD. To override currency rounding
 * altogether, use {@link #setMinimumFractionDigits} and {@link #setMaximumFractionDigits} or
 * {@link #setRoundingIncrement}.
 *
 * @param usage The strategy to use when rounding in the current currency.
 */

public synchronized void setCurrencyUsage(android.icu.util.Currency.CurrencyUsage usage) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the current instance of CurrencyPluralInfo.
 *
 * @see #setCurrencyPluralInfo
 */

public synchronized android.icu.text.CurrencyPluralInfo getCurrencyPluralInfo() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Sets a custom instance of CurrencyPluralInfo. CurrencyPluralInfo generates pattern
 * strings for printing currency long names.
 *
 * <p><strong>Most users should not call this method directly.</strong> You should instead create
 * your formatter via <code>NumberFormat.getInstance(NumberFormat.PLURALCURRENCYSTYLE)</code>.
 *
 * @param newInfo The CurrencyPluralInfo to use when printing currency long names.
 */

public synchronized void setCurrencyPluralInfo(android.icu.text.CurrencyPluralInfo newInfo) { throw new RuntimeException("Stub!"); }

/**
 * Returns whether {@link #parse} will always return a BigDecimal.
 *
 * @see #setParseBigDecimal
 */

public synchronized boolean isParseBigDecimal() { throw new RuntimeException("Stub!"); }

/**
 * Whether to make {@link #parse} prefer returning a {@link android.icu.math.BigDecimal} when
 * possible. For strings corresponding to return values of Infinity, -Infinity, NaN, and -0.0, a
 * Double will be returned even if ParseBigDecimal is enabled.
 *
 * @param value true to cause {@link #parse} to prefer BigDecimal; false to let {@link #parse}
 *     return additional data types like Long or BigInteger.
 */

public synchronized void setParseBigDecimal(boolean value) { throw new RuntimeException("Stub!"); }

/**
 * Always returns 1000, the default prior to ICU 59.
 *
 * @deprecated Setting max parse digits has no effect since ICU4J 59.
 */

@Deprecated
public int getParseMaxDigits() { throw new RuntimeException("Stub!"); }

/**
 * @param maxDigits Prior to ICU 59, the maximum number of digits in the output number after
 *     exponential notation is applied.
 * @deprecated Setting max parse digits has no effect since ICU4J 59.
 */

@Deprecated
public void setParseMaxDigits(int maxDigits) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public synchronized boolean isParseStrict() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public synchronized void setParseStrict(boolean parseStrict) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 *
 * @see #setParseIntegerOnly
 */

public synchronized boolean isParseIntegerOnly() { throw new RuntimeException("Stub!"); }

/**
 * <strong>Parsing:</strong> {@inheritDoc}
 *
 * <p>This is functionally equivalent to calling {@link #setDecimalPatternMatchRequired} and a
 * pattern without a decimal point.
 *
 * @param parseIntegerOnly true to ignore fractional parts of numbers when parsing; false to
 *     consume fractional parts.
 */

public synchronized void setParseIntegerOnly(boolean parseIntegerOnly) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns whether the presence of a decimal point must match the pattern.
 *
 * @see #setDecimalPatternMatchRequired
 */

public synchronized boolean isDecimalPatternMatchRequired() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> <strong>Parsing:</strong> This method is used to either <em>require</em> or
 * <em>forbid</em> the presence of a decimal point in the string being parsed (disabled by
 * default). This feature was designed to be an extra layer of strictness on top of strict
 * parsing, although it can be used in either lenient mode or strict mode.
 *
 * <p>To <em>require</em> a decimal point, call this method in combination with either a pattern
 * containing a decimal point or with {@link #setDecimalSeparatorAlwaysShown}.
 *
 * <pre>
 * // Require a decimal point in the string being parsed:
 * df.applyPattern("#.");
 * df.setDecimalPatternMatchRequired(true);
 *
 * // Alternatively:
 * df.setDecimalSeparatorAlwaysShown(true);
 * df.setDecimalPatternMatchRequired(true);
 * </pre>
 *
 * To <em>forbid</em> a decimal point, call this method in combination with a pattern containing
 * no decimal point. Alternatively, use {@link #setParseIntegerOnly} for the same behavior without
 * depending on the contents of the pattern string.
 *
 * <pre>
 * // Forbid a decimal point in the string being parsed:
 * df.applyPattern("#");
 * df.setDecimalPatternMatchRequired(true);
 * </pre>
 *
 * @param value true to either require or forbid the decimal point according to the pattern; false
 *     to disable this feature.
 * @see #setParseIntegerOnly
 */

public synchronized void setDecimalPatternMatchRequired(boolean value) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns whether to ignore exponents when parsing.
 *
 * @see #setParseNoExponent
 */

public synchronized boolean isParseNoExponent() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Specifies whether to stop parsing when an exponent separator is encountered. For
 * example, parses "123E4" to 123 (with parse position 3) instead of 1230000 (with parse position
 * 5).
 *
 * @param value true to prevent exponents from being parsed; false to allow them to be parsed.
 */

public synchronized void setParseNoExponent(boolean value) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns whether to force case (uppercase/lowercase) to match when parsing.
 *
 * @see #setParseNoExponent
 */

public synchronized boolean isParseCaseSensitive() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Specifies whether parsing should require cases to match in affixes, exponent separators,
 * and currency codes. Case mapping is performed for each code point using {@link android.icu.lang.UCharacter#foldCase  }.
 *
 * @param value true to force case (uppercase/lowercase) to match when parsing; false to ignore
 *     case and perform case folding.
 */

public synchronized void setParseCaseSensitive(boolean value) { throw new RuntimeException("Stub!"); }

/**
 * Tests for equality between this formatter and another formatter.
 *
 * <p>If two DecimalFormat instances are equal, then they will always produce the same output.
 * However, the reverse is not necessarily true: if two DecimalFormat instances always produce the
 * same output, they are not necessarily equal.
 */

public synchronized boolean equals(java.lang.Object obj) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public synchronized int hashCode() { throw new RuntimeException("Stub!"); }

/**
 * Returns the default value of toString() with extra DecimalFormat-specific information appended
 * to the end of the string. This extra information is intended for debugging purposes, and the
 * format is not guaranteed to be stable.
 */

public java.lang.String toString() { throw new RuntimeException("Stub!"); }

/**
 * Serializes this formatter object to a decimal format pattern string. The result of this method
 * is guaranteed to be <em>functionally</em> equivalent to the pattern string used to create this
 * instance after incorporating values from the setter methods.
 *
 * <p>For more information on decimal format pattern strings, see <a
 * href="http://unicode.org/reports/tr35/tr35-numbers.html#Number_Format_Patterns">UTS #35</a>.
 *
 * <p><strong>Important:</strong> Not all properties are capable of being encoded in a pattern
 * string. See a list of properties in {@link #applyPattern}.
 *
 * @return A decimal format pattern string.
 */

public synchronized java.lang.String toPattern() { throw new RuntimeException("Stub!"); }

/**
 * Calls {@link #toPattern} and converts the string to localized notation. For more information on
 * localized notation, see {@link #applyLocalizedPattern}. This method is provided for backwards
 * compatibility and should not be used in new projects.
 *
 * @return A decimal format pattern string in localized notation.
 */

public synchronized java.lang.String toLocalizedPattern() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Constant for {@link #getPadPosition()} and {@link #setPadPosition(int)} to specify pad
 * characters inserted after the prefix.
 *
 * @see #setPadPosition
 * @see #getPadPosition
 * @see #PAD_BEFORE_PREFIX
 * @see #PAD_BEFORE_SUFFIX
 * @see #PAD_AFTER_SUFFIX
 */

public static final int PAD_AFTER_PREFIX = 1; // 0x1

/**
 * <strong>[icu]</strong> Constant for {@link #getPadPosition()} and {@link #setPadPosition(int)} to specify pad
 * characters inserted after the suffix.
 *
 * @see #setPadPosition
 * @see #getPadPosition
 * @see #PAD_BEFORE_PREFIX
 * @see #PAD_AFTER_PREFIX
 * @see #PAD_BEFORE_SUFFIX
 */

public static final int PAD_AFTER_SUFFIX = 3; // 0x3

/**
 * <strong>[icu]</strong> Constant for {@link #getPadPosition()} and {@link #setPadPosition(int)} to specify pad
 * characters inserted before the prefix.
 *
 * @see #setPadPosition
 * @see #getPadPosition
 * @see #PAD_AFTER_PREFIX
 * @see #PAD_BEFORE_SUFFIX
 * @see #PAD_AFTER_SUFFIX
 */

public static final int PAD_BEFORE_PREFIX = 0; // 0x0

/**
 * <strong>[icu]</strong> Constant for {@link #getPadPosition()} and {@link #setPadPosition(int)} to specify pad
 * characters inserted before the suffix.
 *
 * @see #setPadPosition
 * @see #getPadPosition
 * @see #PAD_BEFORE_PREFIX
 * @see #PAD_AFTER_PREFIX
 * @see #PAD_AFTER_SUFFIX
 */

public static final int PAD_BEFORE_SUFFIX = 2; // 0x2
}

