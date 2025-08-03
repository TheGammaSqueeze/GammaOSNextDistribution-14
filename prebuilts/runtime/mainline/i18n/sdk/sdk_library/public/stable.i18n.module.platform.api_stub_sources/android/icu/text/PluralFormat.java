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

import android.icu.util.ULocale;
import java.util.Locale;
import android.icu.util.ULocale.Category;

/**
 * <code>PluralFormat</code> supports the creation of internationalized
 * messages with plural inflection. It is based on <i>plural
 * selection</i>, i.e. the caller specifies messages for each
 * plural case that can appear in the user's language and the
 * <code>PluralFormat</code> selects the appropriate message based on
 * the number.
 *
 * <h3>The Problem of Plural Forms in Internationalized Messages</h3>
 * <p>
 * Different languages have different ways to inflect
 * plurals. Creating internationalized messages that include plural
 * forms is only feasible when the framework is able to handle plural
 * forms of <i>all</i> languages correctly. <code>ChoiceFormat</code>
 * doesn't handle this well, because it attaches a number interval to
 * each message and selects the message whose interval contains a
 * given number. This can only handle a finite number of
 * intervals. But in some languages, like Polish, one plural case
 * applies to infinitely many intervals (e.g., the paucal case applies to
 * numbers ending with 2, 3, or 4 except those ending with 12, 13, or
 * 14). Thus <code>ChoiceFormat</code> is not adequate.
 * <p>
 * <code>PluralFormat</code> deals with this by breaking the problem
 * into two parts:
 * <ul>
 * <li>It uses <code>PluralRules</code> that can define more complex
 *     conditions for a plural case than just a single interval. These plural
 *     rules define both what plural cases exist in a language, and to
 *     which numbers these cases apply.
 * <li>It provides predefined plural rules for many languages. Thus, the programmer
 *     need not worry about the plural cases of a language and
 *     does not have to define the plural cases; they can simply
 *     use the predefined keywords. The whole plural formatting of messages can
 *     be done using localized patterns from resource bundles. For predefined plural
 *     rules, see the CLDR <i>Language Plural Rules</i> page at
 *    http://unicode.org/repos/cldr-tmp/trunk/diff/supplemental/language_plural_rules.html
 * </ul>
 *
 * <h4>Usage of <code>PluralFormat</code></h4>
 * <p>Note: Typically, plural formatting is done via <code>MessageFormat</code>
 * with a <code>plural</code> argument type,
 * rather than using a stand-alone <code>PluralFormat</code>.
 * <p>
 * This discussion assumes that you use <code>PluralFormat</code> with
 * a predefined set of plural rules. You can create one using one of
 * the constructors that takes a <code>ULocale</code> object. To
 * specify the message pattern, you can either pass it to the
 * constructor or set it explicitly using the
 * <code>applyPattern()</code> method. The <code>format()</code>
 * method takes a number object and selects the message of the
 * matching plural case. This message will be returned.
 *
 * <h5>Patterns and Their Interpretation</h5>
 * <p>
 * The pattern text defines the message output for each plural case of the
 * specified locale. Syntax:
 * <blockquote><pre>
 * pluralStyle = [offsetValue] (selector '{' message '}')+
 * offsetValue = "offset:" number
 * selector = explicitValue | keyword
 * explicitValue = '=' number  // adjacent, no white space in between
 * keyword = [^[[:Pattern_Syntax:][:Pattern_White_Space:]]]+
 * message: see {@link android.icu.text.MessageFormat MessageFormat}
 * </pre></blockquote>
 * Pattern_White_Space between syntax elements is ignored, except
 * between the {curly braces} and their sub-message,
 * and between the '=' and the number of an explicitValue.
 * <p>
 * There are 6 predefined case keywords in CLDR/ICU - 'zero', 'one', 'two', 'few', 'many' and
 * 'other'. You always have to define a message text for the default plural case
 * "<code>other</code>" which is contained in every rule set.
 * If you do not specify a message text for a particular plural case, the
 * message text of the plural case "<code>other</code>" gets assigned to this
 * plural case.
 * <p>
 * When formatting, the input number is first matched against the explicitValue clauses.
 * If there is no exact-number match, then a keyword is selected by calling
 * the <code>PluralRules</code> with the input number <em>minus the offset</em>.
 * (The offset defaults to 0 if it is omitted from the pattern string.)
 * If there is no clause with that keyword, then the "other" clauses is returned.
 * <p>
 * An unquoted pound sign (<code>#</code>) in the selected sub-message
 * itself (i.e., outside of arguments nested in the sub-message)
 * is replaced by the input number minus the offset.
 * The number-minus-offset value is formatted using a
 * <code>NumberFormat</code> for the <code>PluralFormat</code>'s locale. If you
 * need special number formatting, you have to use a <code>MessageFormat</code>
 * and explicitly specify a <code>NumberFormat</code> argument.
 * <strong>Note:</strong> That argument is formatting without subtracting the offset!
 * If you need a custom format and have a non-zero offset, then you need to pass the
 * number-minus-offset value as a separate parameter.
 *
 * <p>For a usage example, see the {@link android.icu.text.MessageFormat MessageFormat} class documentation.
 *
 * <h4>Defining Custom Plural Rules</h4>
 * <p>If you need to use <code>PluralFormat</code> with custom rules, you can
 * create a <code>PluralRules</code> object and pass it to
 * <code>PluralFormat</code>'s constructor. If you also specify a locale in this
 * constructor, this locale will be used to format the number in the message
 * texts.
 * <p>
 * For more information about <code>PluralRules</code>, see
 * {@link android.icu.text.PluralRules PluralRules}.
 *
 * @author tschumann (Tim Schumann)
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class PluralFormat extends android.icu.text.UFormat {

/**
 * Creates a new cardinal-number <code>PluralFormat</code> for the default <code>FORMAT</code> locale.
 * This locale will be used to get the set of plural rules and for standard
 * number formatting.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public PluralFormat() { throw new RuntimeException("Stub!"); }

/**
 * Creates a new cardinal-number <code>PluralFormat</code> for a given locale.
 * @param ulocale the <code>PluralFormat</code> will be configured with
 *        rules for this locale. This locale will also be used for standard
 *        number formatting.
 */

public PluralFormat(android.icu.util.ULocale ulocale) { throw new RuntimeException("Stub!"); }

/**
 * Creates a new cardinal-number <code>PluralFormat</code> for a given
 * {@link java.util.Locale}.
 * @param locale the <code>PluralFormat</code> will be configured with
 *        rules for this locale. This locale will also be used for standard
 *        number formatting.
 */

public PluralFormat(java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Creates a new cardinal-number <code>PluralFormat</code> for a given set of rules.
 * The standard number formatting will be done using the default <code>FORMAT</code> locale.
 * @param rules defines the behavior of the <code>PluralFormat</code>
 *        object.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public PluralFormat(android.icu.text.PluralRules rules) { throw new RuntimeException("Stub!"); }

/**
 * Creates a new cardinal-number <code>PluralFormat</code> for a given set of rules.
 * The standard number formatting will be done using the given locale.
 * @param ulocale the default number formatting will be done using this
 *        locale.
 * @param rules defines the behavior of the <code>PluralFormat</code>
 *        object.
 */

public PluralFormat(android.icu.util.ULocale ulocale, android.icu.text.PluralRules rules) { throw new RuntimeException("Stub!"); }

/**
 * Creates a new cardinal-number <code>PluralFormat</code> for a given set of rules.
 * The standard number formatting will be done using the given locale.
 * @param locale the default number formatting will be done using this
 *        locale.
 * @param rules defines the behavior of the <code>PluralFormat</code>
 *        object.
 */

public PluralFormat(java.util.Locale locale, android.icu.text.PluralRules rules) { throw new RuntimeException("Stub!"); }

/**
 * Creates a new <code>PluralFormat</code> for the plural type.
 * The standard number formatting will be done using the given locale.
 * @param ulocale the default number formatting will be done using this
 *        locale.
 * @param type The plural type (e.g., cardinal or ordinal).
 */

public PluralFormat(android.icu.util.ULocale ulocale, android.icu.text.PluralRules.PluralType type) { throw new RuntimeException("Stub!"); }

/**
 * Creates a new <code>PluralFormat</code> for the plural type.
 * The standard number formatting will be done using the given {@link java.util.Locale}.
 * @param locale the default number formatting will be done using this
 *        locale.
 * @param type The plural type (e.g., cardinal or ordinal).
 */

public PluralFormat(java.util.Locale locale, android.icu.text.PluralRules.PluralType type) { throw new RuntimeException("Stub!"); }

/**
 * Creates a new cardinal-number <code>PluralFormat</code> for a given pattern string.
 * The default <code>FORMAT</code> locale will be used to get the set of plural rules and for
 * standard number formatting.
 * @param  pattern the pattern for this <code>PluralFormat</code>.
 * @throws java.lang.IllegalArgumentException if the pattern is invalid.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public PluralFormat(java.lang.String pattern) { throw new RuntimeException("Stub!"); }

/**
 * Creates a new cardinal-number <code>PluralFormat</code> for a given pattern string and
 * locale.
 * The locale will be used to get the set of plural rules and for
 * standard number formatting.
 * <p>Example code:{@sample external/icu/android_icu4j/src/samples/java/android/icu/samples/text/pluralformat/PluralFormatSample.java PluralFormatExample}
 * @param ulocale the <code>PluralFormat</code> will be configured with
 *        rules for this locale. This locale will also be used for standard
 *        number formatting.
 * @param  pattern the pattern for this <code>PluralFormat</code>.
 * @throws java.lang.IllegalArgumentException if the pattern is invalid.
 */

public PluralFormat(android.icu.util.ULocale ulocale, java.lang.String pattern) { throw new RuntimeException("Stub!"); }

/**
 * Creates a new cardinal-number <code>PluralFormat</code> for a given set of rules and a
 * pattern.
 * The standard number formatting will be done using the default <code>FORMAT</code> locale.
 * @param rules defines the behavior of the <code>PluralFormat</code>
 *        object.
 * @param  pattern the pattern for this <code>PluralFormat</code>.
 * @throws java.lang.IllegalArgumentException if the pattern is invalid.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public PluralFormat(android.icu.text.PluralRules rules, java.lang.String pattern) { throw new RuntimeException("Stub!"); }

/**
 * Creates a new cardinal-number <code>PluralFormat</code> for a given set of rules, a
 * pattern and a locale.
 * @param ulocale the <code>PluralFormat</code> will be configured with
 *        rules for this locale. This locale will also be used for standard
 *        number formatting.
 * @param rules defines the behavior of the <code>PluralFormat</code>
 *        object.
 * @param  pattern the pattern for this <code>PluralFormat</code>.
 * @throws java.lang.IllegalArgumentException if the pattern is invalid.
 */

public PluralFormat(android.icu.util.ULocale ulocale, android.icu.text.PluralRules rules, java.lang.String pattern) { throw new RuntimeException("Stub!"); }

/**
 * Creates a new <code>PluralFormat</code> for a plural type, a
 * pattern and a locale.
 * @param ulocale the <code>PluralFormat</code> will be configured with
 *        rules for this locale. This locale will also be used for standard
 *        number formatting.
 * @param type The plural type (e.g., cardinal or ordinal).
 * @param  pattern the pattern for this <code>PluralFormat</code>.
 * @throws java.lang.IllegalArgumentException if the pattern is invalid.
 */

public PluralFormat(android.icu.util.ULocale ulocale, android.icu.text.PluralRules.PluralType type, java.lang.String pattern) { throw new RuntimeException("Stub!"); }

/**
 * Sets the pattern used by this plural format.
 * The method parses the pattern and creates a map of format strings
 * for the plural rules.
 * Patterns and their interpretation are specified in the class description.
 *
 * @param pattern the pattern for this plural format.
 * @throws java.lang.IllegalArgumentException if the pattern is invalid.
 */

public void applyPattern(java.lang.String pattern) { throw new RuntimeException("Stub!"); }

/**
 * Returns the pattern for this PluralFormat.
 *
 * @return the pattern string
 */

public java.lang.String toPattern() { throw new RuntimeException("Stub!"); }

/**
 * Formats a plural message for a given number.
 *
 * @param number a number for which the plural message should be formatted.
 *        If no pattern has been applied to this
 *        <code>PluralFormat</code> object yet, the formatted number will
 *        be returned.
 * @return the string containing the formatted plural message.
 */

public final java.lang.String format(double number) { throw new RuntimeException("Stub!"); }

/**
 * Formats a plural message for a given number and appends the formatted
 * message to the given <code>StringBuffer</code>.
 * @param number a number object (instance of <code>Number</code> for which
 *        the plural message should be formatted. If no pattern has been
 *        applied to this <code>PluralFormat</code> object yet, the
 *        formatted number will be returned.
 *        Note: If this object is not an instance of <code>Number</code>,
 *              the <code>toAppendTo</code> will not be modified.
 * @param toAppendTo the formatted message will be appended to this
 *        <code>StringBuffer</code>.
 * @param pos will be ignored by this method.
 * @return the string buffer passed in as toAppendTo, with formatted text
 *         appended.
 * @throws java.lang.IllegalArgumentException if number is not an instance of Number
 */

public java.lang.StringBuffer format(java.lang.Object number, java.lang.StringBuffer toAppendTo, java.text.FieldPosition pos) { throw new RuntimeException("Stub!"); }

/**
 * This method is not yet supported by <code>PluralFormat</code>.
 * @param text the string to be parsed.
 * @param parsePosition defines the position where parsing is to begin,
 * and upon return, the position where parsing left off.  If the position
 * has not changed upon return, then parsing failed.
 * @return nothing because this method is not yet implemented.
 * @throws java.lang.UnsupportedOperationException will always be thrown by this method.
 */

public java.lang.Number parse(java.lang.String text, java.text.ParsePosition parsePosition) { throw new RuntimeException("Stub!"); }

/**
 * This method is not yet supported by <code>PluralFormat</code>.
 * @param source the string to be parsed.
 * @param pos defines the position where parsing is to begin,
 * and upon return, the position where parsing left off.  If the position
 * has not changed upon return, then parsing failed.
 * @return nothing because this method is not yet implemented.
 * @throws java.lang.UnsupportedOperationException will always be thrown by this method.
 */

public java.lang.Object parseObject(java.lang.String source, java.text.ParsePosition pos) { throw new RuntimeException("Stub!"); }

/**
 * Sets the number format used by this formatter.  You only need to
 * call this if you want a different number format than the default
 * formatter for the locale.
 * @param format the number format to use.
 */

public void setNumberFormat(android.icu.text.NumberFormat format) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public boolean equals(java.lang.Object rhs) { throw new RuntimeException("Stub!"); }

/**
 * Returns true if this equals the provided PluralFormat.
 * @param rhs the PluralFormat to compare against
 * @return true if this equals rhs
 */

public boolean equals(android.icu.text.PluralFormat rhs) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public int hashCode() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.String toString() { throw new RuntimeException("Stub!"); }
}

