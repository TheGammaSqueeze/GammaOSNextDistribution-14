/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2007-2016, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */


package android.icu.text;

import java.io.Serializable;
import java.text.ParseException;
import java.util.Locale;
import android.icu.number.FormattedNumber;
import android.icu.number.NumberFormatter;

/**
 * <p>
 * Defines rules for mapping non-negative numeric values onto a small set of keywords.
 * </p>
 * <p>
 * Rules are constructed from a text description, consisting of a series of keywords and conditions. The {@link #select}
 * method examines each condition in order and returns the keyword for the first condition that matches the number. If
 * none match, {@link #KEYWORD_OTHER} is returned.
 * </p>
 * <p>
 * A PluralRules object is immutable. It contains caches for sample values, but those are synchronized.
 * <p>
 * PluralRules is Serializable so that it can be used in formatters, which are serializable.
 * </p>
 * <p>
 * For more information, details, and tips for writing rules, see the <a
 * href="http://www.unicode.org/draft/reports/tr35/tr35.html#Language_Plural_Rules">LDML spec, C.11 Language Plural
 * Rules</a>
 * </p>
 * <p>
 * Examples:
 * </p>
 *
 * <pre>
 * &quot;one: n is 1; few: n in 2..4&quot;
 * </pre>
 * <p>
 * This defines two rules, for 'one' and 'few'. The condition for 'one' is "n is 1" which means that the number must be
 * equal to 1 for this condition to pass. The condition for 'few' is "n in 2..4" which means that the number must be
 * between 2 and 4 inclusive - and be an integer - for this condition to pass. All other numbers are assigned the
 * keyword "other" by the default rule.
 * </p>
 *
 * <pre>
 * &quot;zero: n is 0; one: n is 1; zero: n mod 100 in 1..19&quot;
 * </pre>
 * <p>
 * This illustrates that the same keyword can be defined multiple times. Each rule is examined in order, and the first
 * keyword whose condition passes is the one returned. Also notes that a modulus is applied to n in the last rule. Thus
 * its condition holds for 119, 219, 319...
 * </p>
 *
 * <pre>
 * &quot;one: n is 1; few: n mod 10 in 2..4 and n mod 100 not in 12..14&quot;
 * </pre>
 * <p>
 * This illustrates conjunction and negation. The condition for 'few' has two parts, both of which must be met:
 * "n mod 10 in 2..4" and "n mod 100 not in 12..14". The first part applies a modulus to n before the test as in the
 * previous example. The second part applies a different modulus and also uses negation, thus it matches all numbers
 * _not_ in 12, 13, 14, 112, 113, 114, 212, 213, 214...
 * </p>
 * <p>
 * Syntax:
 * </p>
 * <pre>
 * rules         = rule (';' rule)*
 * rule          = keyword ':' condition
 * keyword       = &lt;identifier&gt;
 * condition     = and_condition ('or' and_condition)*
 * and_condition = relation ('and' relation)*
 * relation      = not? expr not? rel not? range_list
 * expr          = ('n' | 'i' | 'f' | 'v' | 't') (mod value)?
 * not           = 'not' | '!'
 * rel           = 'in' | 'is' | '=' | '?' | 'within'
 * mod           = 'mod' | '%'
 * range_list    = (range | value) (',' range_list)*
 * value         = digit+
 * digit         = 0|1|2|3|4|5|6|7|8|9
 * range         = value'..'value
 * </pre>
 * <p>Each <b>not</b> term inverts the meaning; however, there should not be more than one of them.</p>
 * <p>
 * The i, f, t, and v values are defined as follows:
 * </p>
 * <ul>
 * <li>i to be the integer digits.</li>
 * <li>f to be the visible decimal digits, as an integer.</li>
 * <li>t to be the visible decimal digits?without trailing zeros?as an integer.</li>
 * <li>v to be the number of visible fraction digits.</li>
 * <li>j is defined to only match integers. That is j is 3 fails if v != 0 (eg for 3.1 or 3.0).</li>
 * </ul>
 * <p>
 * Examples are in the following table:
 * </p>
 * <table border='1' style="border-collapse:collapse">
 * <tbody>
 * <tr>
 * <th>n</th>
 * <th>i</th>
 * <th>f</th>
 * <th>v</th>
 * </tr>
 * <tr>
 * <td>1.0</td>
 * <td>1</td>
 * <td align="right">0</td>
 * <td>1</td>
 * </tr>
 * <tr>
 * <td>1.00</td>
 * <td>1</td>
 * <td align="right">0</td>
 * <td>2</td>
 * </tr>
 * <tr>
 * <td>1.3</td>
 * <td>1</td>
 * <td align="right">3</td>
 * <td>1</td>
 * </tr>
 * <tr>
 * <td>1.03</td>
 * <td>1</td>
 * <td align="right">3</td>
 * <td>2</td>
 * </tr>
 * <tr>
 * <td>1.23</td>
 * <td>1</td>
 * <td align="right">23</td>
 * <td>2</td>
 * </tr>
 * </tbody>
 * </table>
 * <p>
 * An "identifier" is a sequence of characters that do not have the Unicode Pattern_Syntax or Pattern_White_Space
 * properties.
 * <p>
 * The difference between 'in' and 'within' is that 'in' only includes integers in the specified range, while 'within'
 * includes all values. Using 'within' with a range_list consisting entirely of values is the same as using 'in' (it's
 * not an error).
 * </p>
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class PluralRules implements java.io.Serializable {

private PluralRules() { throw new RuntimeException("Stub!"); }

/**
 * Parses a plural rules description and returns a PluralRules.
 * @param description the rule description.
 * @throws java.text.ParseException if the description cannot be parsed.
 *    The exception index is typically not set, it will be -1.
 */

public static android.icu.text.PluralRules parseDescription(java.lang.String description) throws java.text.ParseException { throw new RuntimeException("Stub!"); }

/**
 * Creates a PluralRules from a description if it is parsable,
 * otherwise returns null.
 * @param description the rule description.
 * @return the PluralRules
 */

public static android.icu.text.PluralRules createRules(java.lang.String description) { throw new RuntimeException("Stub!"); }

/**
 * Provides access to the predefined cardinal-number <code>PluralRules</code> for a given
 * locale.
 * Same as forLocale(locale, PluralType.CARDINAL).
 *
 * <p>ICU defines plural rules for many locales based on CLDR <i>Language Plural Rules</i>.
 * For these predefined rules, see CLDR page at
 * http://unicode.org/repos/cldr-tmp/trunk/diff/supplemental/language_plural_rules.html
 *
 * @param locale The locale for which a <code>PluralRules</code> object is
 *   returned.
 * @return The predefined <code>PluralRules</code> object for this locale.
 *   If there's no predefined rules for this locale, the rules
 *   for the closest parent in the locale hierarchy that has one will
 *   be returned.  The final fallback always returns the default
 *   rules.
 */

public static android.icu.text.PluralRules forLocale(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Provides access to the predefined cardinal-number <code>PluralRules</code> for a given
 * {@link java.util.Locale}.
 * Same as forLocale(locale, PluralType.CARDINAL).
 *
 * <p>ICU defines plural rules for many locales based on CLDR <i>Language Plural Rules</i>.
 * For these predefined rules, see CLDR page at
 * http://unicode.org/repos/cldr-tmp/trunk/diff/supplemental/language_plural_rules.html
 *
 * @param locale The locale for which a <code>PluralRules</code> object is
 *   returned.
 * @return The predefined <code>PluralRules</code> object for this locale.
 *   If there's no predefined rules for this locale, the rules
 *   for the closest parent in the locale hierarchy that has one will
 *   be returned.  The final fallback always returns the default
 *   rules.
 */

public static android.icu.text.PluralRules forLocale(java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Provides access to the predefined <code>PluralRules</code> for a given
 * locale and the plural type.
 *
 * <p>ICU defines plural rules for many locales based on CLDR <i>Language Plural Rules</i>.
 * For these predefined rules, see CLDR page at
 * http://unicode.org/repos/cldr-tmp/trunk/diff/supplemental/language_plural_rules.html
 *
 * @param locale The locale for which a <code>PluralRules</code> object is
 *   returned.
 * @param type The plural type (e.g., cardinal or ordinal).
 * @return The predefined <code>PluralRules</code> object for this locale.
 *   If there's no predefined rules for this locale, the rules
 *   for the closest parent in the locale hierarchy that has one will
 *   be returned.  The final fallback always returns the default
 *   rules.
 */

public static android.icu.text.PluralRules forLocale(android.icu.util.ULocale locale, android.icu.text.PluralRules.PluralType type) { throw new RuntimeException("Stub!"); }

/**
 * Provides access to the predefined <code>PluralRules</code> for a given
 * {@link java.util.Locale} and the plural type.
 *
 * <p>ICU defines plural rules for many locales based on CLDR <i>Language Plural Rules</i>.
 * For these predefined rules, see CLDR page at
 * http://unicode.org/repos/cldr-tmp/trunk/diff/supplemental/language_plural_rules.html
 *
 * @param locale The locale for which a <code>PluralRules</code> object is
 *   returned.
 * @param type The plural type (e.g., cardinal or ordinal).
 * @return The predefined <code>PluralRules</code> object for this locale.
 *   If there's no predefined rules for this locale, the rules
 *   for the closest parent in the locale hierarchy that has one will
 *   be returned.  The final fallback always returns the default
 *   rules.
 */

public static android.icu.text.PluralRules forLocale(java.util.Locale locale, android.icu.text.PluralRules.PluralType type) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 * @hide original deprecated declaration
 */

public int hashCode() { throw new RuntimeException("Stub!"); }

/**
 * Given a floating-point number, returns the keyword of the first rule
 * that applies to the number.
 *
 * @param number The number for which the rule has to be determined.
 * @return The keyword of the selected rule.
 */

public java.lang.String select(double number) { throw new RuntimeException("Stub!"); }

/**
 * Given a formatted number, returns the keyword of the first rule that
 * applies to the number.
 *
 * A FormattedNumber allows you to specify an exponent or trailing zeros,
 * which can affect the plural category. To get a FormattedNumber, see
 * {@link android.icu.number.NumberFormatter NumberFormatter}.
 *
 * @param number The number for which the rule has to be determined.
 * @return The keyword of the selected rule.
 */

public java.lang.String select(android.icu.number.FormattedNumber number) { throw new RuntimeException("Stub!"); }

/**
 * Returns a set of all rule keywords used in this <code>PluralRules</code>
 * object.  The rule "other" is always present by default.
 *
 * @return The set of keywords.
 */

public java.util.Set<java.lang.String> getKeywords() { throw new RuntimeException("Stub!"); }

/**
 * Returns the unique value that this keyword matches, or {@link #NO_UNIQUE_VALUE}
 * if the keyword matches multiple values or is not defined for this PluralRules.
 *
 * @param keyword the keyword to check for a unique value
 * @return The unique value for the keyword, or NO_UNIQUE_VALUE.
 */

public double getUniqueKeywordValue(java.lang.String keyword) { throw new RuntimeException("Stub!"); }

/**
 * Returns all the values that trigger this keyword, or null if the number of such
 * values is unlimited.
 *
 * @param keyword the keyword
 * @return the values that trigger this keyword, or null.  The returned collection
 * is immutable. It will be empty if the keyword is not defined.
 */

public java.util.Collection<java.lang.Double> getAllKeywordValues(java.lang.String keyword) { throw new RuntimeException("Stub!"); }

/**
 * Returns a list of integer values for which select() would return that keyword,
 * or null if the keyword is not defined. The returned collection is unmodifiable.
 * The returned list is not complete, and there might be additional values that
 * would return the keyword.
 *
 * @param keyword the keyword to test
 * @return a list of values matching the keyword.
 */

public java.util.Collection<java.lang.Double> getSamples(java.lang.String keyword) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.String toString() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public boolean equals(java.lang.Object rhs) { throw new RuntimeException("Stub!"); }

/**
 * Returns true if rhs is equal to this.
 * @param rhs the PluralRules to compare to.
 * @return true if this and rhs are equal.
 */

public boolean equals(android.icu.text.PluralRules rhs) { throw new RuntimeException("Stub!"); }

/**
 * The default rules that accept any number and return
 * {@link #KEYWORD_OTHER}.
 */

public static final android.icu.text.PluralRules DEFAULT;
static { DEFAULT = null; }

/**
 * Common name for the 'paucal' or other special plural form.
 */

public static final java.lang.String KEYWORD_FEW = "few";

/**
 * Common name for the arabic (11 to 99) plural form.
 */

public static final java.lang.String KEYWORD_MANY = "many";

/**
 * Common name for the 'singular' plural form.
 */

public static final java.lang.String KEYWORD_ONE = "one";

/**
 * Common name for the default plural form.  This name is returned
 * for values to which no other form in the rule applies.  It
 * can additionally be assigned rules of its own.
 */

public static final java.lang.String KEYWORD_OTHER = "other";

/**
 * Common name for the 'dual' plural form.
 */

public static final java.lang.String KEYWORD_TWO = "two";

/**
 * Common name for the 'zero' plural form.
 */

public static final java.lang.String KEYWORD_ZERO = "zero";

/**
 * Value returned by {@link #getUniqueKeywordValue} when there is no
 * unique value to return.
 */

public static final double NO_UNIQUE_VALUE = -0.00123456777;
/**
 * Type of plurals and PluralRules.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public enum PluralType {
/**
 * Plural rules for cardinal numbers: 1 file vs. 2 files.
 */

CARDINAL,
/**
 * Plural rules for ordinal numbers: 1st file, 2nd file, 3rd file, 4th file, etc.
 */

ORDINAL;
}

}

