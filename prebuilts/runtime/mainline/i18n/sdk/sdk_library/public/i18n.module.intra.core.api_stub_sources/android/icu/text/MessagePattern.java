/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
*******************************************************************************
*   Copyright (C) 2010-2016, International Business Machines
*   Corporation and others.  All Rights Reserved.
*******************************************************************************
*   created on: 2010aug21
*   created by: Markus W. Scherer
*/


package android.icu.text;


/**
 * Parses and represents ICU MessageFormat patterns.
 * Also handles patterns for ChoiceFormat, PluralFormat and SelectFormat.
 * Used in the implementations of those classes as well as in tools
 * for message validation, translation and format conversion.
 * <p>
 * The parser handles all syntax relevant for identifying message arguments.
 * This includes "complex" arguments whose style strings contain
 * nested MessageFormat pattern substrings.
 * For "simple" arguments (with no nested MessageFormat pattern substrings),
 * the argument style is not parsed any further.
 * <p>
 * The parser handles named and numbered message arguments and allows both in one message.
 * <p>
 * Once a pattern has been parsed successfully, iterate through the parsed data
 * with countParts(), getPart() and related methods.
 * <p>
 * The data logically represents a parse tree, but is stored and accessed
 * as a list of "parts" for fast and simple parsing and to minimize object allocations.
 * Arguments and nested messages are best handled via recursion.
 * For every _START "part", {@link #getLimitPartIndex(int)} efficiently returns
 * the index of the corresponding _LIMIT "part".
 * <p>
 * List of "parts":
 * <pre>
 * message = MSG_START (SKIP_SYNTAX | INSERT_CHAR | REPLACE_NUMBER | argument)* MSG_LIMIT
 * argument = noneArg | simpleArg | complexArg
 * complexArg = choiceArg | pluralArg | selectArg
 *
 * noneArg = ARG_START.NONE (ARG_NAME | ARG_NUMBER) ARG_LIMIT.NONE
 * simpleArg = ARG_START.SIMPLE (ARG_NAME | ARG_NUMBER) ARG_TYPE [ARG_STYLE] ARG_LIMIT.SIMPLE
 * choiceArg = ARG_START.CHOICE (ARG_NAME | ARG_NUMBER) choiceStyle ARG_LIMIT.CHOICE
 * pluralArg = ARG_START.PLURAL (ARG_NAME | ARG_NUMBER) pluralStyle ARG_LIMIT.PLURAL
 * selectArg = ARG_START.SELECT (ARG_NAME | ARG_NUMBER) selectStyle ARG_LIMIT.SELECT
 *
 * choiceStyle = ((ARG_INT | ARG_DOUBLE) ARG_SELECTOR message)+
 * pluralStyle = [ARG_INT | ARG_DOUBLE] (ARG_SELECTOR [ARG_INT | ARG_DOUBLE] message)+
 * selectStyle = (ARG_SELECTOR message)+
 * </pre>
 * <ul>
 *   <li>Literal output text is not represented directly by "parts" but accessed
 *       between parts of a message, from one part's getLimit() to the next part's getIndex().
 *   <li><code>ARG_START.CHOICE</code> stands for an ARG_START Part with ArgType CHOICE.
 *   <li>In the choiceStyle, the ARG_SELECTOR has the '&lt;', the '#' or
 *       the less-than-or-equal-to sign (U+2264).
 *   <li>In the pluralStyle, the first, optional numeric Part has the "offset:" value.
 *       The optional numeric Part between each (ARG_SELECTOR, message) pair
 *       is the value of an explicit-number selector like "=2",
 *       otherwise the selector is a non-numeric identifier.
 *   <li>The REPLACE_NUMBER Part can occur only in an immediate sub-message of the pluralStyle.
 * </ul>
 * <p>
 * This class is not intended for public subclassing.
 *
 * @author Markus Scherer
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class MessagePattern implements java.lang.Cloneable, android.icu.util.Freezable<android.icu.text.MessagePattern> {

/**
 * Constructs an empty MessagePattern with default ApostropheMode.
 */

public MessagePattern() { throw new RuntimeException("Stub!"); }

/**
 * Constructs an empty MessagePattern.
 * @param mode Explicit ApostropheMode.
 */

public MessagePattern(android.icu.text.MessagePattern.ApostropheMode mode) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a MessagePattern with default ApostropheMode and
 * parses the MessageFormat pattern string.
 * @param pattern a MessageFormat pattern string
 * @throws java.lang.IllegalArgumentException for syntax errors in the pattern string
 * @throws java.lang.IndexOutOfBoundsException if certain limits are exceeded
 *         (e.g., argument number too high, argument name too long, etc.)
 * @throws java.lang.NumberFormatException if a number could not be parsed
 */

public MessagePattern(java.lang.String pattern) { throw new RuntimeException("Stub!"); }

/**
 * Parses a MessageFormat pattern string.
 * @param pattern a MessageFormat pattern string
 * @return this
 * @throws java.lang.IllegalArgumentException for syntax errors in the pattern string
 * @throws java.lang.IndexOutOfBoundsException if certain limits are exceeded
 *         (e.g., argument number too high, argument name too long, etc.)
 * @throws java.lang.NumberFormatException if a number could not be parsed
 */

public android.icu.text.MessagePattern parse(java.lang.String pattern) { throw new RuntimeException("Stub!"); }

/**
 * Parses a ChoiceFormat pattern string.
 * @param pattern a ChoiceFormat pattern string
 * @return this
 * @throws java.lang.IllegalArgumentException for syntax errors in the pattern string
 * @throws java.lang.IndexOutOfBoundsException if certain limits are exceeded
 *         (e.g., argument number too high, argument name too long, etc.)
 * @throws java.lang.NumberFormatException if a number could not be parsed
 */

public android.icu.text.MessagePattern parseChoiceStyle(java.lang.String pattern) { throw new RuntimeException("Stub!"); }

/**
 * Parses a PluralFormat pattern string.
 * @param pattern a PluralFormat pattern string
 * @return this
 * @throws java.lang.IllegalArgumentException for syntax errors in the pattern string
 * @throws java.lang.IndexOutOfBoundsException if certain limits are exceeded
 *         (e.g., argument number too high, argument name too long, etc.)
 * @throws java.lang.NumberFormatException if a number could not be parsed
 */

public android.icu.text.MessagePattern parsePluralStyle(java.lang.String pattern) { throw new RuntimeException("Stub!"); }

/**
 * Parses a SelectFormat pattern string.
 * @param pattern a SelectFormat pattern string
 * @return this
 * @throws java.lang.IllegalArgumentException for syntax errors in the pattern string
 * @throws java.lang.IndexOutOfBoundsException if certain limits are exceeded
 *         (e.g., argument number too high, argument name too long, etc.)
 * @throws java.lang.NumberFormatException if a number could not be parsed
 */

public android.icu.text.MessagePattern parseSelectStyle(java.lang.String pattern) { throw new RuntimeException("Stub!"); }

/**
 * Clears this MessagePattern.
 * countParts() will return 0.
 */

public void clear() { throw new RuntimeException("Stub!"); }

/**
 * Clears this MessagePattern and sets the ApostropheMode.
 * countParts() will return 0.
 * @param mode The new ApostropheMode.
 */

public void clearPatternAndSetApostropheMode(android.icu.text.MessagePattern.ApostropheMode mode) { throw new RuntimeException("Stub!"); }

/**
 * @param other another object to compare with.
 * @return true if this object is equivalent to the other one.
 */

public boolean equals(java.lang.Object other) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public int hashCode() { throw new RuntimeException("Stub!"); }

/**
 * @return this instance's ApostropheMode.
 */

public android.icu.text.MessagePattern.ApostropheMode getApostropheMode() { throw new RuntimeException("Stub!"); }

/**
 * @return the parsed pattern string (null if none was parsed).
 */

public java.lang.String getPatternString() { throw new RuntimeException("Stub!"); }

/**
 * Does the parsed pattern have named arguments like {first_name}?
 * @return true if the parsed pattern has at least one named argument.
 */

public boolean hasNamedArguments() { throw new RuntimeException("Stub!"); }

/**
 * Does the parsed pattern have numbered arguments like {2}?
 * @return true if the parsed pattern has at least one numbered argument.
 */

public boolean hasNumberedArguments() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.String toString() { throw new RuntimeException("Stub!"); }

/**
 * Validates and parses an argument name or argument number string.
 * An argument name must be a "pattern identifier", that is, it must contain
 * no Unicode Pattern_Syntax or Pattern_White_Space characters.
 * If it only contains ASCII digits, then it must be a small integer with no leading zero.
 * @param name Input string.
 * @return &gt;=0 if the name is a valid number,
 *         ARG_NAME_NOT_NUMBER (-1) if it is a "pattern identifier" but not all ASCII digits,
 *         ARG_NAME_NOT_VALID (-2) if it is neither.
 */

public static int validateArgumentName(java.lang.String name) { throw new RuntimeException("Stub!"); }

/**
 * Returns a version of the parsed pattern string where each ASCII apostrophe
 * is doubled (escaped) if it is not already, and if it is not interpreted as quoting syntax.
 * <p>
 * For example, this turns "I don't '{know}' {gender,select,female{h''er}other{h'im}}."
 * into "I don''t '{know}' {gender,select,female{h''er}other{h''im}}."
 * @return the deep-auto-quoted version of the parsed pattern string.
 * @see android.icu.text.MessageFormat#autoQuoteApostrophe(String)
 */

public java.lang.String autoQuoteApostropheDeep() { throw new RuntimeException("Stub!"); }

/**
 * Returns the number of "parts" created by parsing the pattern string.
 * Returns 0 if no pattern has been parsed or clear() was called.
 * @return the number of pattern parts.
 */

public int countParts() { throw new RuntimeException("Stub!"); }

/**
 * Gets the i-th pattern "part".
 * @param i The index of the Part data. (0..countParts()-1)
 * @return the i-th pattern "part".
 * @throws java.lang.IndexOutOfBoundsException if i is outside the (0..countParts()-1) range
 */

public android.icu.text.MessagePattern.Part getPart(int i) { throw new RuntimeException("Stub!"); }

/**
 * Returns the Part.Type of the i-th pattern "part".
 * Convenience method for getPart(i).getType().
 * @param i The index of the Part data. (0..countParts()-1)
 * @return The Part.Type of the i-th Part.
 * @throws java.lang.IndexOutOfBoundsException if i is outside the (0..countParts()-1) range
 */

public android.icu.text.MessagePattern.Part.Type getPartType(int i) { throw new RuntimeException("Stub!"); }

/**
 * Returns the pattern index of the specified pattern "part".
 * Convenience method for getPart(partIndex).getIndex().
 * @param partIndex The index of the Part data. (0..countParts()-1)
 * @return The pattern index of this Part.
 * @throws java.lang.IndexOutOfBoundsException if partIndex is outside the (0..countParts()-1) range
 */

public int getPatternIndex(int partIndex) { throw new RuntimeException("Stub!"); }

/**
 * Returns the substring of the pattern string indicated by the Part.
 * Convenience method for getPatternString().substring(part.getIndex(), part.getLimit()).
 * @param part a part of this MessagePattern.
 * @return the substring associated with part.
 */

public java.lang.String getSubstring(android.icu.text.MessagePattern.Part part) { throw new RuntimeException("Stub!"); }

/**
 * Compares the part's substring with the input string s.
 * @param part a part of this MessagePattern.
 * @param s a string.
 * @return true if getSubstring(part).equals(s).
 */

public boolean partSubstringMatches(android.icu.text.MessagePattern.Part part, java.lang.String s) { throw new RuntimeException("Stub!"); }

/**
 * Returns the numeric value associated with an ARG_INT or ARG_DOUBLE.
 * @param part a part of this MessagePattern.
 * @return the part's numeric value, or NO_NUMERIC_VALUE if this is not a numeric part.
 */

public double getNumericValue(android.icu.text.MessagePattern.Part part) { throw new RuntimeException("Stub!"); }

/**
 * Returns the "offset:" value of a PluralFormat argument, or 0 if none is specified.
 * @param pluralStart the index of the first PluralFormat argument style part. (0..countParts()-1)
 * @return the "offset:" value.
 * @throws java.lang.IndexOutOfBoundsException if pluralStart is outside the (0..countParts()-1) range
 */

public double getPluralOffset(int pluralStart) { throw new RuntimeException("Stub!"); }

/**
 * Returns the index of the ARG|MSG_LIMIT part corresponding to the ARG|MSG_START at start.
 * @param start The index of some Part data (0..countParts()-1);
 *        this Part should be of Type ARG_START or MSG_START.
 * @return The first i&gt;start where getPart(i).getType()==ARG|MSG_LIMIT at the same nesting level,
 *         or start itself if getPartType(msgStart)!=ARG|MSG_START.
 * @throws java.lang.IndexOutOfBoundsException if start is outside the (0..countParts()-1) range
 */

public int getLimitPartIndex(int start) { throw new RuntimeException("Stub!"); }

/**
 * Creates and returns a copy of this object.
 * @return a copy of this object (or itself if frozen).
 */

public java.lang.Object clone() { throw new RuntimeException("Stub!"); }

/**
 * Creates and returns an unfrozen copy of this object.
 * @return a copy of this object.
 */

public android.icu.text.MessagePattern cloneAsThawed() { throw new RuntimeException("Stub!"); }

/**
 * Freezes this object, making it immutable and thread-safe.
 * @return this
 */

public android.icu.text.MessagePattern freeze() { throw new RuntimeException("Stub!"); }

/**
 * Determines whether this object is frozen (immutable) or not.
 * @return true if this object is frozen.
 */

public boolean isFrozen() { throw new RuntimeException("Stub!"); }

/**
 * Return value from {@link #validateArgumentName(java.lang.String)} for when
 * the string is a valid "pattern identifier" but not a number.
 */

public static final int ARG_NAME_NOT_NUMBER = -1; // 0xffffffff

/**
 * Return value from {@link #validateArgumentName(java.lang.String)} for when
 * the string is invalid.
 * It might not be a valid "pattern identifier",
 * or it have only ASCII digits but there is a leading zero or the number is too large.
 */

public static final int ARG_NAME_NOT_VALID = -2; // 0xfffffffe

/**
 * Special value that is returned by getNumericValue(Part) when no
 * numeric value is defined for a part.
 * @see #getNumericValue
 */

public static final double NO_NUMERIC_VALUE = -1.23456789E8;
/**
 * Mode for when an apostrophe starts quoted literal text for MessageFormat output.
 * The default is DOUBLE_OPTIONAL unless overridden via ICUConfig
 * (/com/ibm/icu/ICUConfig.properties).
 * <p>
 * A pair of adjacent apostrophes always results in a single apostrophe in the output,
 * even when the pair is between two single, text-quoting apostrophes.
 * <p>
 * The following table shows examples of desired MessageFormat.format() output
 * with the pattern strings that yield that output.
 *
 * <table>
 *   <tr>
 *     <th>Desired output</th>
 *     <th>DOUBLE_OPTIONAL</th>
 *     <th>DOUBLE_REQUIRED</th>
 *   </tr>
 *   <tr>
 *     <td>I see {many}</td>
 *     <td>I see '{many}'</td>
 *     <td>(same)</td>
 *   </tr>
 *   <tr>
 *     <td>I said {'Wow!'}</td>
 *     <td>I said '{''Wow!''}'</td>
 *     <td>(same)</td>
 *   </tr>
 *   <tr>
 *     <td>I don't know</td>
 *     <td>I don't know OR<br> I don''t know</td>
 *     <td>I don''t know</td>
 *   </tr>
 * </table>
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public enum ApostropheMode {
/**
 * A literal apostrophe is represented by
 * either a single or a double apostrophe pattern character.
 * Within a MessageFormat pattern, a single apostrophe only starts quoted literal text
 * if it immediately precedes a curly brace {},
 * or a pipe symbol | if inside a choice format,
 * or a pound symbol # if inside a plural format.
 * <p>
 * This is the default behavior starting with ICU 4.8.
 */

DOUBLE_OPTIONAL,
/**
 * A literal apostrophe must be represented by
 * a double apostrophe pattern character.
 * A single apostrophe always starts quoted literal text.
 * <p>
 * This is the behavior of ICU 4.6 and earlier, and of {@link java.text.MessageFormat}.
 */

DOUBLE_REQUIRED;
}

/**
 * Argument type constants.
 * Returned by Part.getArgType() for ARG_START and ARG_LIMIT parts.
 *
 * Messages nested inside an argument are each delimited by MSG_START and MSG_LIMIT,
 * with a nesting level one greater than the surrounding message.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public enum ArgType {
/**
 * The argument has no specified type.
 */

NONE,
/**
 * The argument has a "simple" type which is provided by the ARG_TYPE part.
 * An ARG_STYLE part might follow that.
 */

SIMPLE,
/**
 * The argument is a ChoiceFormat with one or more
 * ((ARG_INT | ARG_DOUBLE), ARG_SELECTOR, message) tuples.
 */

CHOICE,
/**
 * The argument is a cardinal-number PluralFormat with an optional ARG_INT or ARG_DOUBLE offset
 * (e.g., offset:1)
 * and one or more (ARG_SELECTOR [explicit-value] message) tuples.
 * If the selector has an explicit value (e.g., =2), then
 * that value is provided by the ARG_INT or ARG_DOUBLE part preceding the message.
 * Otherwise the message immediately follows the ARG_SELECTOR.
 */

PLURAL,
/**
 * The argument is a SelectFormat with one or more (ARG_SELECTOR, message) pairs.
 */

SELECT,
/**
 * The argument is an ordinal-number PluralFormat
 * with the same style parts sequence and semantics as {@link android.icu.text.MessagePattern.ArgType#PLURAL ArgType#PLURAL}.
 */

SELECTORDINAL;

/**
 * @return true if the argument type has a plural style part sequence and semantics,
 * for example {@link android.icu.text.MessagePattern.ArgType#PLURAL ArgType#PLURAL} and {@link android.icu.text.MessagePattern.ArgType#SELECTORDINAL ArgType#SELECTORDINAL}.
 */

public boolean hasPluralStyle() { throw new RuntimeException("Stub!"); }
}

/**
 * A message pattern "part", representing a pattern parsing event.
 * There is a part for the start and end of a message or argument,
 * for quoting and escaping of and with ASCII apostrophes,
 * and for syntax elements of "complex" arguments.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static final class Part {

private Part() { throw new RuntimeException("Stub!"); }

/**
 * Returns the type of this part.
 * @return the part type.
 */

public android.icu.text.MessagePattern.Part.Type getType() { throw new RuntimeException("Stub!"); }

/**
 * Returns the pattern string index associated with this Part.
 * @return this part's pattern string index.
 */

public int getIndex() { throw new RuntimeException("Stub!"); }

/**
 * Returns the length of the pattern substring associated with this Part.
 * This is 0 for some parts.
 * @return this part's pattern substring length.
 */

public int getLength() { throw new RuntimeException("Stub!"); }

/**
 * Returns the pattern string limit (exclusive-end) index associated with this Part.
 * Convenience method for getIndex()+getLength().
 * @return this part's pattern string limit index, same as getIndex()+getLength().
 */

public int getLimit() { throw new RuntimeException("Stub!"); }

/**
 * Returns a value associated with this part.
 * See the documentation of each part type for details.
 * @return the part value.
 */

public int getValue() { throw new RuntimeException("Stub!"); }

/**
 * Returns the argument type if this part is of type ARG_START or ARG_LIMIT,
 * otherwise ArgType.NONE.
 * @return the argument type for this part.
 */

public android.icu.text.MessagePattern.ArgType getArgType() { throw new RuntimeException("Stub!"); }

/**
 * @return a string representation of this part.
 */

public java.lang.String toString() { throw new RuntimeException("Stub!"); }

/**
 * @param other another object to compare with.
 * @return true if this object is equivalent to the other one.
 */

public boolean equals(java.lang.Object other) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public int hashCode() { throw new RuntimeException("Stub!"); }
/**
 * Part type constants.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public enum Type {
/**
 * Start of a message pattern (main or nested).
 * The length is 0 for the top-level message
 * and for a choice argument sub-message, otherwise 1 for the '{'.
 * The value indicates the nesting level, starting with 0 for the main message.
 * <p>
 * There is always a later MSG_LIMIT part.
 */

MSG_START,
/**
 * End of a message pattern (main or nested).
 * The length is 0 for the top-level message and
 * the last sub-message of a choice argument,
 * otherwise 1 for the '}' or (in a choice argument style) the '|'.
 * The value indicates the nesting level, starting with 0 for the main message.
 */

MSG_LIMIT,
/**
 * Indicates a substring of the pattern string which is to be skipped when formatting.
 * For example, an apostrophe that begins or ends quoted text
 * would be indicated with such a part.
 * The value is undefined and currently always 0.
 */

SKIP_SYNTAX,
/**
 * Indicates that a syntax character needs to be inserted for auto-quoting.
 * The length is 0.
 * The value is the character code of the insertion character. (U+0027=APOSTROPHE)
 */

INSERT_CHAR,
/**
 * Indicates a syntactic (non-escaped) # symbol in a plural variant.
 * When formatting, replace this part's substring with the
 * (value-offset) for the plural argument value.
 * The value is undefined and currently always 0.
 */

REPLACE_NUMBER,
/**
 * Start of an argument.
 * The length is 1 for the '{'.
 * The value is the ordinal value of the ArgType. Use getArgType().
 * <p>
 * This part is followed by either an ARG_NUMBER or ARG_NAME,
 * followed by optional argument sub-parts (see ArgType constants)
 * and finally an ARG_LIMIT part.
 */

ARG_START,
/**
 * End of an argument.
 * The length is 1 for the '}'.
 * The value is the ordinal value of the ArgType. Use getArgType().
 */

ARG_LIMIT,
/**
 * The argument number, provided by the value.
 */

ARG_NUMBER,
/**
 * The argument name.
 * The value is undefined and currently always 0.
 */

ARG_NAME,
/**
 * The argument type.
 * The value is undefined and currently always 0.
 */

ARG_TYPE,
/**
 * The argument style text.
 * The value is undefined and currently always 0.
 */

ARG_STYLE,
/**
 * A selector substring in a "complex" argument style.
 * The value is undefined and currently always 0.
 */

ARG_SELECTOR,
/**
 * An integer value, for example the offset or an explicit selector value
 * in a PluralFormat style.
 * The part value is the integer value.
 */

ARG_INT,
/**
 * A numeric value, for example the offset or an explicit selector value
 * in a PluralFormat style.
 * The part value is an index into an internal array of numeric values;
 * use getNumericValue().
 */

ARG_DOUBLE;

/**
 * Indicates whether this part has a numeric value.
 * If so, then that numeric value can be retrieved via {@link android.icu.text.MessagePattern#getNumericValue(android.icu.text.MessagePattern.Part) MessagePattern#getNumericValue(Part)}.
 * @return true if this part has a numeric value.
 */

public boolean hasNumericValue() { throw new RuntimeException("Stub!"); }
}

}

}

