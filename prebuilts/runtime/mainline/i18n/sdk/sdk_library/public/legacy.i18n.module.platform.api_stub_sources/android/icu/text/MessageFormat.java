/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
**********************************************************************
* Copyright (c) 2004-2016, International Business Machines
* Corporation and others.  All Rights Reserved.
**********************************************************************
* Author: Alan Liu
* Created: April 6, 2004
* Since: ICU 3.0
**********************************************************************
*/

package android.icu.text;

import java.util.Locale;
import android.icu.util.ULocale;
import java.util.HashMap;
import java.util.Date;
import java.text.Format;
import java.util.Map;
import android.icu.util.ULocale.Category;
import java.util.Set;
import java.text.FieldPosition;
import java.text.AttributedCharacterIterator;
import java.text.ParsePosition;
import java.text.ParseException;
import java.io.InvalidObjectException;

/**
 * <strong>[icu enhancement]</strong> ICU's replacement for {@link java.text.MessageFormat}.&nbsp;Methods, fields, and other functionality specific to ICU are labeled '<strong>[icu]</strong>'.
 *
 * <p>MessageFormat prepares strings for display to users,
 * with optional arguments (variables/placeholders).
 * The arguments can occur in any order, which is necessary for translation
 * into languages with different grammars.
 *
 * <p>A MessageFormat is constructed from a <em>pattern</em> string
 * with arguments in {curly braces} which will be replaced by formatted values.
 *
 * <p><code>MessageFormat</code> differs from the other <code>Format</code>
 * classes in that you create a <code>MessageFormat</code> object with one
 * of its constructors (not with a <code>getInstance</code> style factory
 * method). Factory methods aren't necessary because <code>MessageFormat</code>
 * itself doesn't implement locale-specific behavior. Any locale-specific
 * behavior is defined by the pattern that you provide and the
 * subformats used for inserted arguments.
 *
 * <p>Arguments can be named (using identifiers) or numbered (using small ASCII-digit integers).
 * Some of the API methods work only with argument numbers and throw an exception
 * if the pattern has named arguments (see {@link #usesNamedArguments()}).
 *
 * <p>An argument might not specify any format type. In this case,
 * a Number value is formatted with a default (for the locale) NumberFormat,
 * a Date value is formatted with a default (for the locale) DateFormat,
 * and for any other value its toString() value is used.
 *
 * <p>An argument might specify a "simple" type for which the specified
 * Format object is created, cached and used.
 *
 * <p>An argument might have a "complex" type with nested MessageFormat sub-patterns.
 * During formatting, one of these sub-messages is selected according to the argument value
 * and recursively formatted.
 *
 * <p>After construction, a custom Format object can be set for
 * a top-level argument, overriding the default formatting and parsing behavior
 * for that argument.
 * However, custom formatting can be achieved more simply by writing
 * a typeless argument in the pattern string
 * and supplying it with a preformatted string value.
 *
 * <p>When formatting, MessageFormat takes a collection of argument values
 * and writes an output string.
 * The argument values may be passed as an array
 * (when the pattern contains only numbered arguments)
 * or as a Map (which works for both named and numbered arguments).
 *
 * <p>Each argument is matched with one of the input values by array index or map key
 * and formatted according to its pattern specification
 * (or using a custom Format object if one was set).
 * A numbered pattern argument is matched with a map key that contains that number
 * as an ASCII-decimal-digit string (without leading zero).
 *
 * <h3><a name="patterns">Patterns and Their Interpretation</a></h3>
 *
 * <code>MessageFormat</code> uses patterns of the following form:
 * <blockquote><pre>
 * message = messageText (argument messageText)*
 * argument = noneArg | simpleArg | complexArg
 * complexArg = choiceArg | pluralArg | selectArg | selectordinalArg
 *
 * noneArg = '{' argNameOrNumber '}'
 * simpleArg = '{' argNameOrNumber ',' argType [',' argStyle] '}'
 * choiceArg = '{' argNameOrNumber ',' "choice" ',' choiceStyle '}'
 * pluralArg = '{' argNameOrNumber ',' "plural" ',' pluralStyle '}'
 * selectArg = '{' argNameOrNumber ',' "select" ',' selectStyle '}'
 * selectordinalArg = '{' argNameOrNumber ',' "selectordinal" ',' pluralStyle '}'
 *
 * choiceStyle: see {@link java.text.ChoiceFormat ChoiceFormat}
 * pluralStyle: see {@link android.icu.text.PluralFormat PluralFormat}
 * selectStyle: see {@link android.icu.text.SelectFormat SelectFormat}
 *
 * argNameOrNumber = argName | argNumber
 * argName = [^[[:Pattern_Syntax:][:Pattern_White_Space:]]]+
 * argNumber = '0' | ('1'..'9' ('0'..'9')*)
 *
 * argType = "number" | "date" | "time" | "spellout" | "ordinal" | "duration"
 * argStyle = "short" | "medium" | "long" | "full" | "integer" | "currency" | "percent" | argStyleText
 * </pre></blockquote>
 *
 * <ul>
 *   <li>messageText can contain quoted literal strings including syntax characters.
 *       A quoted literal string begins with an ASCII apostrophe and a syntax character
 *       (usually a {curly brace}) and continues until the next single apostrophe.
 *       A double ASCII apostrohpe inside or outside of a quoted string represents
 *       one literal apostrophe.
 *   <li>Quotable syntax characters are the {curly braces} in all messageText parts,
 *       plus the '#' sign in a messageText immediately inside a pluralStyle,
 *       and the '|' symbol in a messageText immediately inside a choiceStyle.
 *   <li>See also {@link android.icu.text.MessagePattern.ApostropheMode MessagePattern.ApostropheMode}
 *   <li>In argStyleText, every single ASCII apostrophe begins and ends quoted literal text,
 *       and unquoted {curly braces} must occur in matched pairs.
 * </ul>
 *
 * <p>Recommendation: Use the real apostrophe (single quote) character \\u2019 for
 * human-readable text, and use the ASCII apostrophe (\\u0027 ' )
 * only in program syntax, like quoting in MessageFormat.
 * See the annotations for U+0027 Apostrophe in The Unicode Standard.
 *
 * <p>The <code>choice</code> argument type is deprecated.
 * Use <code>plural</code> arguments for proper plural selection,
 * and <code>select</code> arguments for simple selection among a fixed set of choices.
 *
 * <p>The <code>argType</code> and <code>argStyle</code> values are used to create
 * a <code>Format</code> instance for the format element. The following
 * table shows how the values map to Format instances. Combinations not
 * shown in the table are illegal. Any <code>argStyleText</code> must
 * be a valid pattern string for the Format subclass used.
 *
 * <table border=1>
 *    <tr>
 *       <th>argType
 *       <th>argStyle
 *       <th>resulting Format object
 *    <tr>
 *       <td colspan=2><i>(none)</i>
 *       <td><code>null</code>
 *    <tr>
 *       <td rowspan=5><code>number</code>
 *       <td><i>(none)</i>
 *       <td><code>NumberFormat.getInstance(getLocale())</code>
 *    <tr>
 *       <td><code>integer</code>
 *       <td><code>NumberFormat.getIntegerInstance(getLocale())</code>
 *    <tr>
 *       <td><code>currency</code>
 *       <td><code>NumberFormat.getCurrencyInstance(getLocale())</code>
 *    <tr>
 *       <td><code>percent</code>
 *       <td><code>NumberFormat.getPercentInstance(getLocale())</code>
 *    <tr>
 *       <td><i>argStyleText</i>
 *       <td><code>new DecimalFormat(argStyleText, new DecimalFormatSymbols(getLocale()))</code>
 *    <tr>
 *       <td rowspan=6><code>date</code>
 *       <td><i>(none)</i>
 *       <td><code>DateFormat.getDateInstance(DateFormat.DEFAULT, getLocale())</code>
 *    <tr>
 *       <td><code>short</code>
 *       <td><code>DateFormat.getDateInstance(DateFormat.SHORT, getLocale())</code>
 *    <tr>
 *       <td><code>medium</code>
 *       <td><code>DateFormat.getDateInstance(DateFormat.DEFAULT, getLocale())</code>
 *    <tr>
 *       <td><code>long</code>
 *       <td><code>DateFormat.getDateInstance(DateFormat.LONG, getLocale())</code>
 *    <tr>
 *       <td><code>full</code>
 *       <td><code>DateFormat.getDateInstance(DateFormat.FULL, getLocale())</code>
 *    <tr>
 *       <td><i>argStyleText</i>
 *       <td><code>new SimpleDateFormat(argStyleText, getLocale())</code>
 *    <tr>
 *       <td rowspan=6><code>time</code>
 *       <td><i>(none)</i>
 *       <td><code>DateFormat.getTimeInstance(DateFormat.DEFAULT, getLocale())</code>
 *    <tr>
 *       <td><code>short</code>
 *       <td><code>DateFormat.getTimeInstance(DateFormat.SHORT, getLocale())</code>
 *    <tr>
 *       <td><code>medium</code>
 *       <td><code>DateFormat.getTimeInstance(DateFormat.DEFAULT, getLocale())</code>
 *    <tr>
 *       <td><code>long</code>
 *       <td><code>DateFormat.getTimeInstance(DateFormat.LONG, getLocale())</code>
 *    <tr>
 *       <td><code>full</code>
 *       <td><code>DateFormat.getTimeInstance(DateFormat.FULL, getLocale())</code>
 *    <tr>
 *       <td><i>argStyleText</i>
 *       <td><code>new SimpleDateFormat(argStyleText, getLocale())</code>
 *    <tr>
 *       <td><code>spellout</code>
 *       <td><i>argStyleText (optional)</i>
 *       <td><code>new RuleBasedNumberFormat(getLocale(), RuleBasedNumberFormat.SPELLOUT)
 *           <br>&nbsp;&nbsp;&nbsp;&nbsp;.setDefaultRuleset(argStyleText);</code>
 *    <tr>
 *       <td><code>ordinal</code>
 *       <td><i>argStyleText (optional)</i>
 *       <td><code>new RuleBasedNumberFormat(getLocale(), RuleBasedNumberFormat.ORDINAL)
 *           <br>&nbsp;&nbsp;&nbsp;&nbsp;.setDefaultRuleset(argStyleText);</code>
 *    <tr>
 *       <td><code>duration</code>
 *       <td><i>argStyleText (optional)</i>
 *       <td><code>new RuleBasedNumberFormat(getLocale(), RuleBasedNumberFormat.DURATION)
 *           <br>&nbsp;&nbsp;&nbsp;&nbsp;.setDefaultRuleset(argStyleText);</code>
 * </table>
 *
 * <h4><a name="diffsjdk">Differences from java.text.MessageFormat</a></h4>
 *
 * <p>The ICU MessageFormat supports both named and numbered arguments,
 * while the JDK MessageFormat only supports numbered arguments.
 * Named arguments make patterns more readable.
 *
 * <p>ICU implements a more user-friendly apostrophe quoting syntax.
 * In message text, an apostrophe only begins quoting literal text
 * if it immediately precedes a syntax character (mostly {curly braces}).<br>
 * In the JDK MessageFormat, an apostrophe always begins quoting,
 * which requires common text like "don't" and "aujourd'hui"
 * to be written with doubled apostrophes like "don''t" and "aujourd''hui".
 * For more details see {@link android.icu.text.MessagePattern.ApostropheMode MessagePattern.ApostropheMode}.
 *
 * <p>ICU does not create a ChoiceFormat object for a choiceArg, pluralArg or selectArg
 * but rather handles such arguments itself.
 * The JDK MessageFormat does create and use a ChoiceFormat object
 * (<code>new ChoiceFormat(argStyleText)</code>).
 * The JDK does not support plural and select arguments at all.
 *
 * <h4>Usage Information</h4>
 *
 * <p>Here are some examples of usage:
 * <blockquote>
 * <pre>
 * Object[] arguments = {
 *     7,
 *     new Date(System.currentTimeMillis()),
 *     "a disturbance in the Force"
 * };
 *
 * String result = MessageFormat.format(
 *     "At {1,time} on {1,date}, there was {2} on planet {0,number,integer}.",
 *     arguments);
 *
 * <em>output</em>: At 12:30 PM on Jul 3, 2053, there was a disturbance
 *           in the Force on planet 7.
 *
 * </pre>
 * </blockquote>
 * Typically, the message format will come from resources, and the
 * arguments will be dynamically set at runtime.
 *
 * <p>Example 2:
 * <blockquote>
 * <pre>
 * Object[] testArgs = { 3, "MyDisk" };
 *
 * MessageFormat form = new MessageFormat(
 *     "The disk \"{1}\" contains {0} file(s).");
 *
 * System.out.println(form.format(testArgs));
 *
 * // output, with different testArgs
 * <em>output</em>: The disk "MyDisk" contains 0 file(s).
 * <em>output</em>: The disk "MyDisk" contains 1 file(s).
 * <em>output</em>: The disk "MyDisk" contains 1,273 file(s).
 * </pre>
 * </blockquote>
 *
 * <p>For messages that include plural forms, you can use a plural argument:
 * <pre>
 * MessageFormat msgFmt = new MessageFormat(
 *     "{num_files, plural, " +
 *     "=0{There are no files on disk \"{disk_name}\".}" +
 *     "=1{There is one file on disk \"{disk_name}\".}" +
 *     "other{There are # files on disk \"{disk_name}\".}}",
 *     ULocale.ENGLISH);
 * Map args = new HashMap();
 * args.put("num_files", 0);
 * args.put("disk_name", "MyDisk");
 * System.out.println(msgFmt.format(args));
 * args.put("num_files", 3);
 * System.out.println(msgFmt.format(args));
 *
 * <em>output</em>:
 * There are no files on disk "MyDisk".
 * There are 3 files on "MyDisk".
 * </pre>
 * See {@link android.icu.text.PluralFormat PluralFormat} and {@link android.icu.text.PluralRules PluralRules} for details.
 *
 * <h4><a name="synchronization">Synchronization</a></h4>
 *
 * <p>MessageFormats are not synchronized.
 * It is recommended to create separate format instances for each thread.
 * If multiple threads access a format concurrently, it must be synchronized
 * externally.
 *
 * @see          java.util.Locale
 * @see          java.text.Format
 * @see          android.icu.text.NumberFormat
 * @see          android.icu.text.DecimalFormat
 * @see          java.text.ChoiceFormat
 * @see          android.icu.text.PluralFormat
 * @see          android.icu.text.SelectFormat
 * @author       Mark Davis
 * @author       Markus Scherer
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class MessageFormat extends android.icu.text.UFormat {

/**
 * Constructs a MessageFormat for the default <code>FORMAT</code> locale and the
 * specified pattern.
 * Sets the locale and calls applyPattern(pattern).
 *
 * @param pattern the pattern for this message format
 * @exception java.lang.IllegalArgumentException if the pattern is invalid
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public MessageFormat(java.lang.String pattern) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a MessageFormat for the specified locale and
 * pattern.
 * Sets the locale and calls applyPattern(pattern).
 *
 * @param pattern the pattern for this message format
 * @param locale the locale for this message format
 * @exception java.lang.IllegalArgumentException if the pattern is invalid
 */

public MessageFormat(java.lang.String pattern, java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a MessageFormat for the specified locale and
 * pattern.
 * Sets the locale and calls applyPattern(pattern).
 *
 * @param pattern the pattern for this message format
 * @param locale the locale for this message format
 * @exception java.lang.IllegalArgumentException if the pattern is invalid
 */

public MessageFormat(java.lang.String pattern, android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Sets the locale to be used for creating argument Format objects.
 * This affects subsequent calls to the {@link #applyPattern applyPattern}
 * method as well as to the <code>format</code> and
 * {@link #formatToCharacterIterator formatToCharacterIterator} methods.
 *
 * @param locale the locale to be used when creating or comparing subformats
 */

public void setLocale(java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Sets the locale to be used for creating argument Format objects.
 * This affects subsequent calls to the {@link #applyPattern applyPattern}
 * method as well as to the <code>format</code> and
 * {@link #formatToCharacterIterator formatToCharacterIterator} methods.
 *
 * @param locale the locale to be used when creating or comparing subformats
 */

public void setLocale(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns the locale that's used when creating or comparing subformats.
 *
 * @return the locale used when creating or comparing subformats
 */

public java.util.Locale getLocale() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the locale that's used when creating argument Format objects.
 *
 * @return the locale used when creating or comparing subformats
 */

public android.icu.util.ULocale getULocale() { throw new RuntimeException("Stub!"); }

/**
 * Sets the pattern used by this message format.
 * Parses the pattern and caches Format objects for simple argument types.
 * Patterns and their interpretation are specified in the
 * <a href="#patterns">class description</a>.
 *
 * @param pttrn the pattern for this message format
 * @throws java.lang.IllegalArgumentException if the pattern is invalid
 */

public void applyPattern(java.lang.String pttrn) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Sets the ApostropheMode and the pattern used by this message format.
 * Parses the pattern and caches Format objects for simple argument types.
 * Patterns and their interpretation are specified in the
 * <a href="#patterns">class description</a>.
 * <p>
 * This method is best used only once on a given object to avoid confusion about the mode,
 * and after constructing the object with an empty pattern string to minimize overhead.
 *
 * @param pattern the pattern for this message format
 * @param aposMode the new ApostropheMode
 * @throws java.lang.IllegalArgumentException if the pattern is invalid
 * @see android.icu.text.MessagePattern.ApostropheMode
 */

public void applyPattern(java.lang.String pattern, android.icu.text.MessagePattern.ApostropheMode aposMode) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong>
 * @return this instance's ApostropheMode.
 */

public android.icu.text.MessagePattern.ApostropheMode getApostropheMode() { throw new RuntimeException("Stub!"); }

/**
 * Returns the applied pattern string.
 * @return the pattern string
 * @throws java.lang.IllegalStateException after custom Format objects have been set
 *         via setFormat() or similar APIs
 */

public java.lang.String toPattern() { throw new RuntimeException("Stub!"); }

/**
 * Sets the Format objects to use for the values passed into
 * <code>format</code> methods or returned from <code>parse</code>
 * methods. The indices of elements in <code>newFormats</code>
 * correspond to the argument indices used in the previously set
 * pattern string.
 * The order of formats in <code>newFormats</code> thus corresponds to
 * the order of elements in the <code>arguments</code> array passed
 * to the <code>format</code> methods or the result array returned
 * by the <code>parse</code> methods.
 * <p>
 * If an argument index is used for more than one format element
 * in the pattern string, then the corresponding new format is used
 * for all such format elements. If an argument index is not used
 * for any format element in the pattern string, then the
 * corresponding new format is ignored. If fewer formats are provided
 * than needed, then only the formats for argument indices less
 * than <code>newFormats.length</code> are replaced.
 *
 * This method is only supported if the format does not use
 * named arguments, otherwise an IllegalArgumentException is thrown.
 *
 * @param newFormats the new formats to use
 * @throws java.lang.NullPointerException if <code>newFormats</code> is null
 * @throws java.lang.IllegalArgumentException if this formatter uses named arguments
 */

public void setFormatsByArgumentIndex(java.text.Format[] newFormats) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Sets the Format objects to use for the values passed into
 * <code>format</code> methods or returned from <code>parse</code>
 * methods. The keys in <code>newFormats</code> are the argument
 * names in the previously set pattern string, and the values
 * are the formats.
 * <p>
 * Only argument names from the pattern string are considered.
 * Extra keys in <code>newFormats</code> that do not correspond
 * to an argument name are ignored.  Similarly, if there is no
 * format in newFormats for an argument name, the formatter
 * for that argument remains unchanged.
 * <p>
 * This may be called on formats that do not use named arguments.
 * In this case the map will be queried for key Strings that
 * represent argument indices, e.g. "0", "1", "2" etc.
 *
 * @param newFormats a map from String to Format providing new
 *        formats for named arguments.
 */

public void setFormatsByArgumentName(java.util.Map<java.lang.String,java.text.Format> newFormats) { throw new RuntimeException("Stub!"); }

/**
 * Sets the Format objects to use for the format elements in the
 * previously set pattern string.
 * The order of formats in <code>newFormats</code> corresponds to
 * the order of format elements in the pattern string.
 * <p>
 * If more formats are provided than needed by the pattern string,
 * the remaining ones are ignored. If fewer formats are provided
 * than needed, then only the first <code>newFormats.length</code>
 * formats are replaced.
 * <p>
 * Since the order of format elements in a pattern string often
 * changes during localization, it is generally better to use the
 * {@link #setFormatsByArgumentIndex setFormatsByArgumentIndex}
 * method, which assumes an order of formats corresponding to the
 * order of elements in the <code>arguments</code> array passed to
 * the <code>format</code> methods or the result array returned by
 * the <code>parse</code> methods.
 *
 * @param newFormats the new formats to use
 * @exception java.lang.NullPointerException if <code>newFormats</code> is null
 */

public void setFormats(java.text.Format[] newFormats) { throw new RuntimeException("Stub!"); }

/**
 * Sets the Format object to use for the format elements within the
 * previously set pattern string that use the given argument
 * index.
 * The argument index is part of the format element definition and
 * represents an index into the <code>arguments</code> array passed
 * to the <code>format</code> methods or the result array returned
 * by the <code>parse</code> methods.
 * <p>
 * If the argument index is used for more than one format element
 * in the pattern string, then the new format is used for all such
 * format elements. If the argument index is not used for any format
 * element in the pattern string, then the new format is ignored.
 *
 * This method is only supported when exclusively numbers are used for
 * argument names. Otherwise an IllegalArgumentException is thrown.
 *
 * @param argumentIndex the argument index for which to use the new format
 * @param newFormat the new format to use
 * @throws java.lang.IllegalArgumentException if this format uses named arguments
 */

public void setFormatByArgumentIndex(int argumentIndex, java.text.Format newFormat) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Sets the Format object to use for the format elements within the
 * previously set pattern string that use the given argument
 * name.
 * <p>
 * If the argument name is used for more than one format element
 * in the pattern string, then the new format is used for all such
 * format elements. If the argument name is not used for any format
 * element in the pattern string, then the new format is ignored.
 * <p>
 * This API may be used on formats that do not use named arguments.
 * In this case <code>argumentName</code> should be a String that names
 * an argument index, e.g. "0", "1", "2"... etc.  If it does not name
 * a valid index, the format will be ignored.  No error is thrown.
 *
 * @param argumentName the name of the argument to change
 * @param newFormat the new format to use
 */

public void setFormatByArgumentName(java.lang.String argumentName, java.text.Format newFormat) { throw new RuntimeException("Stub!"); }

/**
 * Sets the Format object to use for the format element with the given
 * format element index within the previously set pattern string.
 * The format element index is the zero-based number of the format
 * element counting from the start of the pattern string.
 * <p>
 * Since the order of format elements in a pattern string often
 * changes during localization, it is generally better to use the
 * {@link #setFormatByArgumentIndex setFormatByArgumentIndex}
 * method, which accesses format elements based on the argument
 * index they specify.
 *
 * @param formatElementIndex the index of a format element within the pattern
 * @param newFormat the format to use for the specified format element
 * @exception java.lang.ArrayIndexOutOfBoundsException if formatElementIndex is equal to or
 *            larger than the number of format elements in the pattern string
 */

public void setFormat(int formatElementIndex, java.text.Format newFormat) { throw new RuntimeException("Stub!"); }

/**
 * Returns the Format objects used for the values passed into
 * <code>format</code> methods or returned from <code>parse</code>
 * methods. The indices of elements in the returned array
 * correspond to the argument indices used in the previously set
 * pattern string.
 * The order of formats in the returned array thus corresponds to
 * the order of elements in the <code>arguments</code> array passed
 * to the <code>format</code> methods or the result array returned
 * by the <code>parse</code> methods.
 * <p>
 * If an argument index is used for more than one format element
 * in the pattern string, then the format used for the last such
 * format element is returned in the array. If an argument index
 * is not used for any format element in the pattern string, then
 * null is returned in the array.
 *
 * This method is only supported when exclusively numbers are used for
 * argument names. Otherwise an IllegalArgumentException is thrown.
 *
 * @return the formats used for the arguments within the pattern
 * @throws java.lang.IllegalArgumentException if this format uses named arguments
 */

public java.text.Format[] getFormatsByArgumentIndex() { throw new RuntimeException("Stub!"); }

/**
 * Returns the Format objects used for the format elements in the
 * previously set pattern string.
 * The order of formats in the returned array corresponds to
 * the order of format elements in the pattern string.
 * <p>
 * Since the order of format elements in a pattern string often
 * changes during localization, it's generally better to use the
 * {@link #getFormatsByArgumentIndex()}
 * method, which assumes an order of formats corresponding to the
 * order of elements in the <code>arguments</code> array passed to
 * the <code>format</code> methods or the result array returned by
 * the <code>parse</code> methods.
 *
 * This method is only supported when exclusively numbers are used for
 * argument names. Otherwise an IllegalArgumentException is thrown.
 *
 * @return the formats used for the format elements in the pattern
 * @throws java.lang.IllegalArgumentException if this format uses named arguments
 */

public java.text.Format[] getFormats() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the top-level argument names. For more details, see
 * {@link #setFormatByArgumentName(java.lang.String,java.text.Format)}.
 * @return a Set of argument names
 */

public java.util.Set<java.lang.String> getArgumentNames() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the first top-level format associated with the given argument name.
 * For more details, see {@link #setFormatByArgumentName(java.lang.String,java.text.Format)}.
 * @param argumentName The name of the desired argument.
 * @return the Format associated with the name, or null if there isn't one.
 */

public java.text.Format getFormatByArgumentName(java.lang.String argumentName) { throw new RuntimeException("Stub!"); }

/**
 * Formats an array of objects and appends the <code>MessageFormat</code>'s
 * pattern, with arguments replaced by the formatted objects, to the
 * provided <code>StringBuffer</code>.
 * <p>
 * The text substituted for the individual format elements is derived from
 * the current subformat of the format element and the
 * <code>arguments</code> element at the format element's argument index
 * as indicated by the first matching line of the following table. An
 * argument is <i>unavailable</i> if <code>arguments</code> is
 * <code>null</code> or has fewer than argumentIndex+1 elements.  When
 * an argument is unavailable no substitution is performed.
 *
 * <table border=1>
 *    <tr>
 *       <th>argType or Format
 *       <th>value object
 *       <th>Formatted Text
 *    <tr>
 *       <td><i>any</i>
 *       <td><i>unavailable</i>
 *       <td><code>"{" + argNameOrNumber + "}"</code>
 *    <tr>
 *       <td><i>any</i>
 *       <td><code>null</code>
 *       <td><code>"null"</code>
 *    <tr>
 *       <td>custom Format <code>!= null</code>
 *       <td><i>any</i>
 *       <td><code>customFormat.format(argument)</code>
 *    <tr>
 *       <td>noneArg, or custom Format <code>== null</code>
 *       <td><code>instanceof Number</code>
 *       <td><code>NumberFormat.getInstance(getLocale()).format(argument)</code>
 *    <tr>
 *       <td>noneArg, or custom Format <code>== null</code>
 *       <td><code>instanceof Date</code>
 *       <td><code>DateFormat.getDateTimeInstance(DateFormat.SHORT,
 *           DateFormat.SHORT, getLocale()).format(argument)</code>
 *    <tr>
 *       <td>noneArg, or custom Format <code>== null</code>
 *       <td><code>instanceof String</code>
 *       <td><code>argument</code>
 *    <tr>
 *       <td>noneArg, or custom Format <code>== null</code>
 *       <td><i>any</i>
 *       <td><code>argument.toString()</code>
 *    <tr>
 *       <td>complexArg
 *       <td><i>any</i>
 *       <td>result of recursive formatting of a selected sub-message
 * </table>
 * <p>
 * If <code>pos</code> is non-null, and refers to
 * <code>Field.ARGUMENT</code>, the location of the first formatted
 * string will be returned.
 *
 * This method is only supported when the format does not use named
 * arguments, otherwise an IllegalArgumentException is thrown.
 *
 * @param arguments an array of objects to be formatted and substituted.
 * @param result where text is appended.
 * @param pos On input: an alignment field, if desired.
 *            On output: the offsets of the alignment field.
 * @throws java.lang.IllegalArgumentException if a value in the
 *         <code>arguments</code> array is not of the type
 *         expected by the corresponding argument or custom Format object.
 * @throws java.lang.IllegalArgumentException if this format uses named arguments
 */

public final java.lang.StringBuffer format(java.lang.Object[] arguments, java.lang.StringBuffer result, java.text.FieldPosition pos) { throw new RuntimeException("Stub!"); }

/**
 * Formats a map of objects and appends the <code>MessageFormat</code>'s
 * pattern, with arguments replaced by the formatted objects, to the
 * provided <code>StringBuffer</code>.
 * <p>
 * The text substituted for the individual format elements is derived from
 * the current subformat of the format element and the
 * <code>arguments</code> value corresopnding to the format element's
 * argument name.
 * <p>
 * A numbered pattern argument is matched with a map key that contains that number
 * as an ASCII-decimal-digit string (without leading zero).
 * <p>
 * An argument is <i>unavailable</i> if <code>arguments</code> is
 * <code>null</code> or does not have a value corresponding to an argument
 * name in the pattern.  When an argument is unavailable no substitution
 * is performed.
 *
 * @param arguments a map of objects to be formatted and substituted.
 * @param result where text is appended.
 * @param pos On input: an alignment field, if desired.
 *            On output: the offsets of the alignment field.
 * @throws java.lang.IllegalArgumentException if a value in the
 *         <code>arguments</code> array is not of the type
 *         expected by the corresponding argument or custom Format object.
 * @return the passed-in StringBuffer
 */

public final java.lang.StringBuffer format(java.util.Map<java.lang.String,java.lang.Object> arguments, java.lang.StringBuffer result, java.text.FieldPosition pos) { throw new RuntimeException("Stub!"); }

/**
 * Creates a MessageFormat with the given pattern and uses it
 * to format the given arguments. This is equivalent to
 * <blockquote>
 *     <code>(new {@link #MessageFormat(java.lang.String) MessageFormat}(pattern)).{@link
 *     #format(java.lang.Object[],java.lang.StringBuffer,java.text.FieldPosition)
 *     format}(arguments, new StringBuffer(), null).toString()</code>
 * </blockquote>
 *
 * @throws java.lang.IllegalArgumentException if the pattern is invalid
 * @throws java.lang.IllegalArgumentException if a value in the
 *         <code>arguments</code> array is not of the type
 *         expected by the corresponding argument or custom Format object.
 * @throws java.lang.IllegalArgumentException if this format uses named arguments
 */

public static java.lang.String format(java.lang.String pattern, java.lang.Object... arguments) { throw new RuntimeException("Stub!"); }

/**
 * Creates a MessageFormat with the given pattern and uses it to
 * format the given arguments.  The pattern must identifyarguments
 * by name instead of by number.
 * <p>
 * @throws java.lang.IllegalArgumentException if the pattern is invalid
 * @throws java.lang.IllegalArgumentException if a value in the
 *         <code>arguments</code> array is not of the type
 *         expected by the corresponding argument or custom Format object.
 * @see #format(Map, StringBuffer, FieldPosition)
 * @see #format(String, Object[])
 */

public static java.lang.String format(java.lang.String pattern, java.util.Map<java.lang.String,java.lang.Object> arguments) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns true if this MessageFormat uses named arguments,
 * and false otherwise.  See class description.
 *
 * @return true if named arguments are used.
 */

public boolean usesNamedArguments() { throw new RuntimeException("Stub!"); }

/**
 * Formats a map or array of objects and appends the <code>MessageFormat</code>'s
 * pattern, with format elements replaced by the formatted objects, to the
 * provided <code>StringBuffer</code>.
 * This is equivalent to either of
 * <blockquote>
 *     <code>{@link #format(java.lang.Object[],java.lang.StringBuffer,java.text.FieldPosition) format}((Object[]) arguments, result, pos)</code>
 *     <code>{@link #format(java.util.Map,java.lang.StringBuffer,java.text.FieldPosition) format}((Map) arguments, result, pos)</code>
 * </blockquote>
 * A map must be provided if this format uses named arguments, otherwise
 * an IllegalArgumentException will be thrown.
 * @param arguments a map or array of objects to be formatted
 * @param result where text is appended
 * @param pos On input: an alignment field, if desired
 *            On output: the offsets of the alignment field
 * @throws java.lang.IllegalArgumentException if an argument in
 *         <code>arguments</code> is not of the type
 *         expected by the format element(s) that use it
 * @throws java.lang.IllegalArgumentException if <code>arguments</code> is
 *         an array of Object and this format uses named arguments
 */

public final java.lang.StringBuffer format(java.lang.Object arguments, java.lang.StringBuffer result, java.text.FieldPosition pos) { throw new RuntimeException("Stub!"); }

/**
 * Formats an array of objects and inserts them into the
 * <code>MessageFormat</code>'s pattern, producing an
 * <code>AttributedCharacterIterator</code>.
 * You can use the returned <code>AttributedCharacterIterator</code>
 * to build the resulting String, as well as to determine information
 * about the resulting String.
 * <p>
 * The text of the returned <code>AttributedCharacterIterator</code> is
 * the same that would be returned by
 * <blockquote>
 *     <code>{@link #format(java.lang.Object[],java.lang.StringBuffer,java.text.FieldPosition) format}(arguments, new StringBuffer(), null).toString()</code>
 * </blockquote>
 * <p>
 * In addition, the <code>AttributedCharacterIterator</code> contains at
 * least attributes indicating where text was generated from an
 * argument in the <code>arguments</code> array. The keys of these attributes are of
 * type <code>MessageFormat.Field</code>, their values are
 * <code>Integer</code> objects indicating the index in the <code>arguments</code>
 * array of the argument from which the text was generated.
 * <p>
 * The attributes/value from the underlying <code>Format</code>
 * instances that <code>MessageFormat</code> uses will also be
 * placed in the resulting <code>AttributedCharacterIterator</code>.
 * This allows you to not only find where an argument is placed in the
 * resulting String, but also which fields it contains in turn.
 *
 * @param arguments an array of objects to be formatted and substituted.
 * @return AttributedCharacterIterator describing the formatted value.
 * @exception java.lang.NullPointerException if <code>arguments</code> is null.
 * @throws java.lang.IllegalArgumentException if a value in the
 *         <code>arguments</code> array is not of the type
 *         expected by the corresponding argument or custom Format object.
 */

public java.text.AttributedCharacterIterator formatToCharacterIterator(java.lang.Object arguments) { throw new RuntimeException("Stub!"); }

/**
 * Parses the string.
 *
 * <p>Caveats: The parse may fail in a number of circumstances.
 * For example:
 * <ul>
 * <li>If one of the arguments does not occur in the pattern.
 * <li>If the format of an argument loses information, such as
 *     with a choice format where a large number formats to "many".
 * <li>Does not yet handle recursion (where
 *     the substituted strings contain {n} references.)
 * <li>Will not always find a match (or the correct match)
 *     if some part of the parse is ambiguous.
 *     For example, if the pattern "{1},{2}" is used with the
 *     string arguments {"a,b", "c"}, it will format as "a,b,c".
 *     When the result is parsed, it will return {"a", "b,c"}.
 * <li>If a single argument is parsed more than once in the string,
 *     then the later parse wins.
 * </ul>
 * When the parse fails, use ParsePosition.getErrorIndex() to find out
 * where in the string did the parsing failed. The returned error
 * index is the starting offset of the sub-patterns that the string
 * is comparing with. For example, if the parsing string "AAA {0} BBB"
 * is comparing against the pattern "AAD {0} BBB", the error index is
 * 0. When an error occurs, the call to this method will return null.
 * If the source is null, return an empty array.
 *
 * @throws java.lang.IllegalArgumentException if this format uses named arguments
 */

public java.lang.Object[] parse(java.lang.String source, java.text.ParsePosition pos) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Parses the string, returning the results in a Map.
 * This is similar to the version that returns an array
 * of Object.  This supports both named and numbered
 * arguments-- if numbered, the keys in the map are the
 * corresponding ASCII-decimal-digit strings (e.g. "0", "1", "2"...).
 *
 * @param source the text to parse
 * @param pos the position at which to start parsing.  on return,
 *        contains the result of the parse.
 * @return a Map containing key/value pairs for each parsed argument.
 */

public java.util.Map<java.lang.String,java.lang.Object> parseToMap(java.lang.String source, java.text.ParsePosition pos) { throw new RuntimeException("Stub!"); }

/**
 * Parses text from the beginning of the given string to produce an object
 * array.
 * The method may not use the entire text of the given string.
 * <p>
 * See the {@link #parse(java.lang.String,java.text.ParsePosition)} method for more information
 * on message parsing.
 *
 * @param source A <code>String</code> whose beginning should be parsed.
 * @return An <code>Object</code> array parsed from the string.
 * @exception java.text.ParseException if the beginning of the specified string cannot be parsed.
 * @exception java.lang.IllegalArgumentException if this format uses named arguments
 */

public java.lang.Object[] parse(java.lang.String source) throws java.text.ParseException { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Parses text from the beginning of the given string to produce a map from
 * argument to values. The method may not use the entire text of the given string.
 *
 * <p>See the {@link #parse(java.lang.String,java.text.ParsePosition)} method for more information on
 * message parsing.
 *
 * @param source A <code>String</code> whose beginning should be parsed.
 * @return A <code>Map</code> parsed from the string.
 * @throws java.text.ParseException if the beginning of the specified string cannot
 *         be parsed.
 * @see #parseToMap(String, ParsePosition)
 */

public java.util.Map<java.lang.String,java.lang.Object> parseToMap(java.lang.String source) throws java.text.ParseException { throw new RuntimeException("Stub!"); }

/**
 * Parses text from a string to produce an object array or Map.
 * <p>
 * The method attempts to parse text starting at the index given by
 * <code>pos</code>.
 * If parsing succeeds, then the index of <code>pos</code> is updated
 * to the index after the last character used (parsing does not necessarily
 * use all characters up to the end of the string), and the parsed
 * object array is returned. The updated <code>pos</code> can be used to
 * indicate the starting point for the next call to this method.
 * If an error occurs, then the index of <code>pos</code> is not
 * changed, the error index of <code>pos</code> is set to the index of
 * the character where the error occurred, and null is returned.
 * <p>
 * See the {@link #parse(java.lang.String,java.text.ParsePosition)} method for more information
 * on message parsing.
 *
 * @param source A <code>String</code>, part of which should be parsed.
 * @param pos A <code>ParsePosition</code> object with index and error
 *            index information as described above.
 * @return An <code>Object</code> parsed from the string, either an
 *         array of Object, or a Map, depending on whether named
 *         arguments are used.  This can be queried using <code>usesNamedArguments</code>.
 *         In case of error, returns null.
 * @throws java.lang.NullPointerException if <code>pos</code> is null.
 */

public java.lang.Object parseObject(java.lang.String source, java.text.ParsePosition pos) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.Object clone() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public boolean equals(java.lang.Object obj) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public int hashCode() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Converts an 'apostrophe-friendly' pattern into a standard
 * pattern.
 * <em>This is obsolete for ICU 4.8 and higher MessageFormat pattern strings.</em>
 * It can still be useful together with {@link java.text.MessageFormat}.
 *
 * <p>See the class description for more about apostrophes and quoting,
 * and differences between ICU and {@link java.text.MessageFormat}.
 *
 * <p>{@link java.text.MessageFormat} and ICU 4.6 and earlier MessageFormat
 * treat all ASCII apostrophes as
 * quotes, which is problematic in some languages, e.g.
 * French, where apostrophe is commonly used.  This utility
 * assumes that only an unpaired apostrophe immediately before
 * a brace is a true quote.  Other unpaired apostrophes are paired,
 * and the resulting standard pattern string is returned.
 *
 * <p><b>Note</b>: It is not guaranteed that the returned pattern
 * is indeed a valid pattern.  The only effect is to convert
 * between patterns having different quoting semantics.
 *
 * <p><b>Note</b>: This method only works on top-level messageText,
 * not messageText nested inside a complexArg.
 *
 * @param pattern the 'apostrophe-friendly' pattern to convert
 * @return the standard equivalent of the original pattern
 */

public static java.lang.String autoQuoteApostrophe(java.lang.String pattern) { throw new RuntimeException("Stub!"); }
/**
 * Defines constants that are used as attribute keys in the
 * <code>AttributedCharacterIterator</code> returned
 * from <code>MessageFormat.formatToCharacterIterator</code>.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static class Field extends java.text.Format.Field {

/**
 * Create a <code>Field</code> with the specified name.
 *
 * @param name The name of the attribute
 */

protected Field(java.lang.String name) { super(null); throw new RuntimeException("Stub!"); }

/**
 * Resolves instances being deserialized to the predefined constants.
 *
 * @return resolved MessageFormat.Field constant
 * @throws java.io.InvalidObjectException if the constant could not be resolved.
 */

protected java.lang.Object readResolve() throws java.io.InvalidObjectException { throw new RuntimeException("Stub!"); }

/**
 * Constant identifying a portion of a message that was generated
 * from an argument passed into <code>formatToCharacterIterator</code>.
 * The value associated with the key will be an <code>Integer</code>
 * indicating the index in the <code>arguments</code> array of the
 * argument from which the text was generated.
 */

public static final android.icu.text.MessageFormat.Field ARGUMENT;
static { ARGUMENT = null; }
}

}

