/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/**
 *******************************************************************************
 * Copyright (C) 1996-2016, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */


package android.icu.lang;

import android.icu.text.BreakIterator;
import android.icu.util.RangeValueIterator;
import android.icu.util.ValueIterator;
import android.icu.lang.UCharacterEnums.ECharacterDirection;
import android.icu.lang.UCharacterEnums.ECharacterCategory;

/**
 * <strong>[icu enhancement]</strong> ICU's replacement for {@link java.lang.Character}.&nbsp;Methods, fields, and other functionality specific to ICU are labeled '<strong>[icu]</strong>'.
 *
 * <p>The UCharacter class provides extensions to the {@link java.lang.Character} class.
 * These extensions provide support for more Unicode properties.
 * Each ICU release supports the latest version of Unicode available at that time.
 *
 * <p>For some time before Java 5 added support for supplementary Unicode code points,
 * The ICU UCharacter class and many other ICU classes already supported them.
 * Some UCharacter methods and constants were widened slightly differently than
 * how the Character class methods and constants were widened later.
 * In particular, {@link java.lang.Character#MAX_VALUE Character#MAX_VALUE} is still a char with the value U+FFFF,
 * while the {@link android.icu.lang.UCharacter#MAX_VALUE UCharacter#MAX_VALUE} is an int with the value U+10FFFF.
 *
 * <p>Code points are represented in these API using ints. While it would be
 * more convenient in Java to have a separate primitive datatype for them,
 * ints suffice in the meantime.
 *
 * <p>Aside from the additions for UTF-16 support, and the updated Unicode
 * properties, the main differences between UCharacter and Character are:
 * <ul>
 * <li> UCharacter is not designed to be a char wrapper and does not have
 *      APIs to which involves management of that single char.<br>
 *      These include:
 *      <ul>
 *        <li> char charValue(),
 *        <li> int compareTo(java.lang.Character, java.lang.Character), etc.
 *      </ul>
 * <li> UCharacter does not include Character APIs that are deprecated, nor
 *      does it include the Java-specific character information, such as
 *      boolean isJavaIdentifierPart(char ch).
 * <li> Character maps characters 'A' - 'Z' and 'a' - 'z' to the numeric
 *      values '10' - '35'. UCharacter also does this in digit and
 *      getNumericValue, to adhere to the java semantics of these
 *      methods.  New methods unicodeDigit, and
 *      getUnicodeNumericValue do not treat the above code points
 *      as having numeric values.  This is a semantic change from ICU4J 1.3.1.
 * </ul>
 * <p>
 * In addition to Java compatibility functions, which calculate derived properties,
 * this API provides low-level access to the Unicode Character Database.
 * <p>
 * Unicode assigns each code point (not just assigned character) values for
 * many properties.
 * Most of them are simple boolean flags, or constants from a small enumerated list.
 * For some properties, values are strings or other relatively more complex types.
 * <p>
 * For more information see
 * <a href="http://www.unicode/org/ucd/">"About the Unicode Character Database"</a>
 * (http://www.unicode.org/ucd/)
 * and the <a href="http://www.icu-project.org/userguide/properties.html">ICU
 * User Guide chapter on Properties</a>
 * (http://www.icu-project.org/userguide/properties.html).
 * <p>
 * There are also functions that provide easy migration from C/POSIX functions
 * like isblank(). Their use is generally discouraged because the C/POSIX
 * standards do not define their semantics beyond the ASCII range, which means
 * that different implementations exhibit very different behavior.
 * Instead, Unicode properties should be used directly.
 * <p>
 * There are also only a few, broad C/POSIX character classes, and they tend
 * to be used for conflicting purposes. For example, the "isalpha()" class
 * is sometimes used to determine word boundaries, while a more sophisticated
 * approach would at least distinguish initial letters from continuation
 * characters (the latter including combining marks).
 * (In ICU, BreakIterator is the most sophisticated API for word boundaries.)
 * Another example: There is no "istitle()" class for titlecase characters.
 * <p>
 * ICU 3.4 and later provides API access for all twelve C/POSIX character classes.
 * ICU implements them according to the Standard Recommendations in
 * Annex C: Compatibility Properties of UTS #18 Unicode Regular Expressions
 * (http://www.unicode.org/reports/tr18/#Compatibility_Properties).
 * <p>
 * API access for C/POSIX character classes is as follows:
 * <pre>{@code
 * - alpha:     isUAlphabetic(c) or hasBinaryProperty(c, UProperty.ALPHABETIC)
 * - lower:     isULowercase(c) or hasBinaryProperty(c, UProperty.LOWERCASE)
 * - upper:     isUUppercase(c) or hasBinaryProperty(c, UProperty.UPPERCASE)
 * - punct:     ((1<<getType(c)) & ((1<<DASH_PUNCTUATION)|(1<<START_PUNCTUATION)|
 *               (1<<END_PUNCTUATION)|(1<<CONNECTOR_PUNCTUATION)|(1<<OTHER_PUNCTUATION)|
 *               (1<<INITIAL_PUNCTUATION)|(1<<FINAL_PUNCTUATION)))!=0
 * - digit:     isDigit(c) or getType(c)==DECIMAL_DIGIT_NUMBER
 * - xdigit:    hasBinaryProperty(c, UProperty.POSIX_XDIGIT)
 * - alnum:     hasBinaryProperty(c, UProperty.POSIX_ALNUM)
 * - space:     isUWhiteSpace(c) or hasBinaryProperty(c, UProperty.WHITE_SPACE)
 * - blank:     hasBinaryProperty(c, UProperty.POSIX_BLANK)
 * - cntrl:     getType(c)==CONTROL
 * - graph:     hasBinaryProperty(c, UProperty.POSIX_GRAPH)
 * - print:     hasBinaryProperty(c, UProperty.POSIX_PRINT)}</pre>
 * <p>
 * The C/POSIX character classes are also available in UnicodeSet patterns,
 * using patterns like [:graph:] or \p{graph}.
 *
 * <p><strong>[icu] Note:</strong> There are several ICU (and Java) whitespace functions.
 * Comparison:<ul>
 * <li> isUWhiteSpace=UCHAR_WHITE_SPACE: Unicode White_Space property;
 *       most of general categories "Z" (separators) + most whitespace ISO controls
 *       (including no-break spaces, but excluding IS1..IS4)
 * <li> isWhitespace: Java isWhitespace; Z + whitespace ISO controls but excluding no-break spaces
 * <li> isSpaceChar: just Z (including no-break spaces)</ul>
 *
 * <p>
 * This class is not subclassable.
 *
 * @author Syn Wee Quek
 * @see android.icu.lang.UCharacterEnums
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class UCharacter implements android.icu.lang.UCharacterEnums.ECharacterCategory, android.icu.lang.UCharacterEnums.ECharacterDirection {

private UCharacter() { throw new RuntimeException("Stub!"); }

/**
 * Returnss the numeric value of a decimal digit code point.
 * <br>This method observes the semantics of
 * <code>java.lang.Character.digit()</code>.  Note that this
 * will return positive values for code points for which isDigit
 * returns false, just like java.lang.Character.
 * <br><em>Semantic Change:</em> In release 1.3.1 and
 * prior, this did not treat the European letters as having a
 * digit value, and also treated numeric letters and other numbers as
 * digits.
 * This has been changed to conform to the java semantics.
 * <br>A code point is a valid digit if and only if:
 * <ul>
 *   <li>ch is a decimal digit or one of the european letters, and
 *   <li>the value of ch is less than the specified radix.
 * </ul>
 * @param ch the code point to query
 * @param radix the radix
 * @return the numeric value represented by the code point in the
 * specified radix, or -1 if the code point is not a decimal digit
 * or if its value is too large for the radix
 */

public static int digit(int ch, int radix) { throw new RuntimeException("Stub!"); }

/**
 * Returnss the numeric value of a decimal digit code point.
 * <br>This is a convenience overload of <code>digit(int, int)</code>
 * that provides a decimal radix.
 * <br><em>Semantic Change:</em> In release 1.3.1 and prior, this
 * treated numeric letters and other numbers as digits.  This has
 * been changed to conform to the java semantics.
 * @param ch the code point to query
 * @return the numeric value represented by the code point,
 * or -1 if the code point is not a decimal digit or if its
 * value is too large for a decimal radix
 */

public static int digit(int ch) { throw new RuntimeException("Stub!"); }

/**
 * Returns the numeric value of the code point as a nonnegative
 * integer.
 * <br>If the code point does not have a numeric value, then -1 is returned.
 * <br>
 * If the code point has a numeric value that cannot be represented as a
 * nonnegative integer (for example, a fractional value), then -2 is
 * returned.
 * @param ch the code point to query
 * @return the numeric value of the code point, or -1 if it has no numeric
 * value, or -2 if it has a numeric value that cannot be represented as a
 * nonnegative integer
 */

public static int getNumericValue(int ch) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the numeric value for a Unicode code point as defined in the
 * Unicode Character Database.
 * <p>A "double" return type is necessary because some numeric values are
 * fractions, negative, or too large for int.
 * <p>For characters without any numeric values in the Unicode Character
 * Database, this function will return NO_NUMERIC_VALUE.
 * Note: This is different from the Unicode Standard which specifies NaN as the default value.
 * <p><em>API Change:</em> In release 2.2 and prior, this API has a
 * return type int and returns -1 when the argument ch does not have a
 * corresponding numeric value. This has been changed to synch with ICU4C
 *
 * This corresponds to the ICU4C function u_getNumericValue.
 * @param ch Code point to get the numeric value for.
 * @return numeric value of ch, or NO_NUMERIC_VALUE if none is defined.
 */

public static double getUnicodeNumericValue(int ch) { throw new RuntimeException("Stub!"); }

/**
 * Returns a value indicating a code point's Unicode category.
 * Up-to-date Unicode implementation of java.lang.Character.getType()
 * except for the above mentioned code points that had their category
 * changed.<br>
 * Return results are constants from the interface
 * <a href=UCharacterCategory.html>UCharacterCategory</a><br>
 * <em>NOTE:</em> the UCharacterCategory values are <em>not</em> compatible with
 * those returned by java.lang.Character.getType.  UCharacterCategory values
 * match the ones used in ICU4C, while java.lang.Character type
 * values, though similar, skip the value 17.
 * @param ch code point whose type is to be determined
 * @return category which is a value of UCharacterCategory
 */

public static int getType(int ch) { throw new RuntimeException("Stub!"); }

/**
 * Determines if a code point has a defined meaning in the up-to-date
 * Unicode standard.
 * E.g. supplementary code points though allocated space are not defined in
 * Unicode yet.<br>
 * Up-to-date Unicode implementation of java.lang.Character.isDefined()
 * @param ch code point to be determined if it is defined in the most
 *        current version of Unicode
 * @return true if this code point is defined in unicode
 */

public static boolean isDefined(int ch) { throw new RuntimeException("Stub!"); }

/**
 * Determines if a code point is a Java digit.
 * <br>This method observes the semantics of
 * <code>java.lang.Character.isDigit()</code>. It returns true for decimal
 * digits only.
 * <br><em>Semantic Change:</em> In release 1.3.1 and prior, this treated
 * numeric letters and other numbers as digits.
 * This has been changed to conform to the java semantics.
 * @param ch code point to query
 * @return true if this code point is a digit
 */

public static boolean isDigit(int ch) { throw new RuntimeException("Stub!"); }

/**
 * Determines if the specified code point is an ISO control character.
 * A code point is considered to be an ISO control character if it is in
 * the range &#92;u0000 through &#92;u001F or in the range &#92;u007F through
 * &#92;u009F.<br>
 * Up-to-date Unicode implementation of java.lang.Character.isISOControl()
 * @param ch code point to determine if it is an ISO control character
 * @return true if code point is a ISO control character
 */

public static boolean isISOControl(int ch) { throw new RuntimeException("Stub!"); }

/**
 * Determines if the specified code point is a letter.
 * Up-to-date Unicode implementation of java.lang.Character.isLetter()
 * @param ch code point to determine if it is a letter
 * @return true if code point is a letter
 */

public static boolean isLetter(int ch) { throw new RuntimeException("Stub!"); }

/**
 * Determines if the specified code point is a letter or digit.
 * <strong>[icu] Note:</strong> This method, unlike java.lang.Character does not regard the ascii
 * characters 'A' - 'Z' and 'a' - 'z' as digits.
 * @param ch code point to determine if it is a letter or a digit
 * @return true if code point is a letter or a digit
 */

public static boolean isLetterOrDigit(int ch) { throw new RuntimeException("Stub!"); }

/**
 * Compatibility override of Java method, delegates to
 * java.lang.Character.isJavaIdentifierStart.
 * @param cp the code point
 * @return true if the code point can start a java identifier.
 */

public static boolean isJavaIdentifierStart(int cp) { throw new RuntimeException("Stub!"); }

/**
 * Compatibility override of Java method, delegates to
 * java.lang.Character.isJavaIdentifierPart.
 * @param cp the code point
 * @return true if the code point can continue a java identifier.
 */

public static boolean isJavaIdentifierPart(int cp) { throw new RuntimeException("Stub!"); }

/**
 * Determines if the specified code point is a lowercase character.
 * UnicodeData only contains case mappings for code points where they are
 * one-to-one mappings; it also omits information about context-sensitive
 * case mappings.<br> For more information about Unicode case mapping
 * please refer to the
 * <a href=http://www.unicode.org/unicode/reports/tr21/>Technical report
 * #21</a>.<br>
 * Up-to-date Unicode implementation of java.lang.Character.isLowerCase()
 * @param ch code point to determine if it is in lowercase
 * @return true if code point is a lowercase character
 */

public static boolean isLowerCase(int ch) { throw new RuntimeException("Stub!"); }

/**
 * Determines if the specified code point is a white space character.
 * A code point is considered to be an whitespace character if and only
 * if it satisfies one of the following criteria:
 * <ul>
 * <li> It is a Unicode Separator character (categories "Z" = "Zs" or "Zl" or "Zp"), but is not
 *      also a non-breaking space (&#92;u00A0 or &#92;u2007 or &#92;u202F).
 * <li> It is &#92;u0009, HORIZONTAL TABULATION.
 * <li> It is &#92;u000A, LINE FEED.
 * <li> It is &#92;u000B, VERTICAL TABULATION.
 * <li> It is &#92;u000C, FORM FEED.
 * <li> It is &#92;u000D, CARRIAGE RETURN.
 * <li> It is &#92;u001C, FILE SEPARATOR.
 * <li> It is &#92;u001D, GROUP SEPARATOR.
 * <li> It is &#92;u001E, RECORD SEPARATOR.
 * <li> It is &#92;u001F, UNIT SEPARATOR.
 * </ul>
 *
 * This API tries to sync with the semantics of Java's
 * java.lang.Character.isWhitespace(), but it may not return
 * the exact same results because of the Unicode version
 * difference.
 * <p>Note: Unicode 4.0.1 changed U+200B ZERO WIDTH SPACE from a Space Separator (Zs)
 * to a Format Control (Cf). Since then, isWhitespace(0x200b) returns false.
 * See http://www.unicode.org/versions/Unicode4.0.1/
 * @param ch code point to determine if it is a white space
 * @return true if the specified code point is a white space character
 */

public static boolean isWhitespace(int ch) { throw new RuntimeException("Stub!"); }

/**
 * Determines if the specified code point is a Unicode specified space
 * character, i.e. if code point is in the category Zs, Zl and Zp.
 * Up-to-date Unicode implementation of java.lang.Character.isSpaceChar().
 * @param ch code point to determine if it is a space
 * @return true if the specified code point is a space character
 */

public static boolean isSpaceChar(int ch) { throw new RuntimeException("Stub!"); }

/**
 * Determines if the specified code point is a titlecase character.
 * UnicodeData only contains case mappings for code points where they are
 * one-to-one mappings; it also omits information about context-sensitive
 * case mappings.<br>
 * For more information about Unicode case mapping please refer to the
 * <a href=http://www.unicode.org/unicode/reports/tr21/>
 * Technical report #21</a>.<br>
 * Up-to-date Unicode implementation of java.lang.Character.isTitleCase().
 * @param ch code point to determine if it is in title case
 * @return true if the specified code point is a titlecase character
 */

public static boolean isTitleCase(int ch) { throw new RuntimeException("Stub!"); }

/**
 * Determines if the specified code point may be any part of a Unicode
 * identifier other than the starting character.
 * A code point may be part of a Unicode identifier if and only if it is
 * one of the following:
 * <ul>
 * <li> Lu Uppercase letter
 * <li> Ll Lowercase letter
 * <li> Lt Titlecase letter
 * <li> Lm Modifier letter
 * <li> Lo Other letter
 * <li> Nl Letter number
 * <li> Pc Connecting punctuation character
 * <li> Nd decimal number
 * <li> Mc Spacing combining mark
 * <li> Mn Non-spacing mark
 * <li> Cf formatting code
 * </ul>
 * Up-to-date Unicode implementation of
 * java.lang.Character.isUnicodeIdentifierPart().<br>
 * See <a href=http://www.unicode.org/unicode/reports/tr8/>UTR #8</a>.
 * @param ch code point to determine if is can be part of a Unicode
 *        identifier
 * @return true if code point is any character belonging a unicode
 *         identifier suffix after the first character
 */

public static boolean isUnicodeIdentifierPart(int ch) { throw new RuntimeException("Stub!"); }

/**
 * Determines if the specified code point is permissible as the first
 * character in a Unicode identifier.
 * A code point may start a Unicode identifier if it is of type either
 * <ul>
 * <li> Lu Uppercase letter
 * <li> Ll Lowercase letter
 * <li> Lt Titlecase letter
 * <li> Lm Modifier letter
 * <li> Lo Other letter
 * <li> Nl Letter number
 * </ul>
 * Up-to-date Unicode implementation of
 * java.lang.Character.isUnicodeIdentifierStart().<br>
 * See <a href=http://www.unicode.org/unicode/reports/tr8/>UTR #8</a>.
 * @param ch code point to determine if it can start a Unicode identifier
 * @return true if code point is the first character belonging a unicode
 *              identifier
 */

public static boolean isUnicodeIdentifierStart(int ch) { throw new RuntimeException("Stub!"); }

/**
 * Determines if the specified code point should be regarded as an
 * ignorable character in a Java identifier.
 * A character is Java-identifier-ignorable if it has the general category
 * Cf Formatting Control, or it is a non-Java-whitespace ISO control:
 * U+0000..U+0008, U+000E..U+001B, U+007F..U+009F.<br>
 * Up-to-date Unicode implementation of
 * java.lang.Character.isIdentifierIgnorable().<br>
 * See <a href=http://www.unicode.org/unicode/reports/tr8/>UTR #8</a>.
 * <p>Note that Unicode just recommends to ignore Cf (format controls).
 * @param ch code point to be determined if it can be ignored in a Unicode
 *        identifier.
 * @return true if the code point is ignorable
 */

public static boolean isIdentifierIgnorable(int ch) { throw new RuntimeException("Stub!"); }

/**
 * Determines if the specified code point is an uppercase character.
 * UnicodeData only contains case mappings for code point where they are
 * one-to-one mappings; it also omits information about context-sensitive
 * case mappings.<br>
 * For language specific case conversion behavior, use
 * toUpperCase(locale, str). <br>
 * For example, the case conversion for dot-less i and dotted I in Turkish,
 * or for final sigma in Greek.
 * For more information about Unicode case mapping please refer to the
 * <a href=http://www.unicode.org/unicode/reports/tr21/>
 * Technical report #21</a>.<br>
 * Up-to-date Unicode implementation of java.lang.Character.isUpperCase().
 * @param ch code point to determine if it is in uppercase
 * @return true if the code point is an uppercase character
 */

public static boolean isUpperCase(int ch) { throw new RuntimeException("Stub!"); }

/**
 * The given code point is mapped to its lowercase equivalent; if the code
 * point has no lowercase equivalent, the code point itself is returned.
 * Up-to-date Unicode implementation of java.lang.Character.toLowerCase()
 *
 * <p>This function only returns the simple, single-code point case mapping.
 * Full case mappings should be used whenever possible because they produce
 * better results by working on whole strings.
 * They take into account the string context and the language and can map
 * to a result string with a different length as appropriate.
 * Full case mappings are applied by the case mapping functions
 * that take String parameters rather than code points (int).
 * See also the User Guide chapter on C/POSIX migration:
 * http://www.icu-project.org/userguide/posix.html#case_mappings
 *
 * @param ch code point whose lowercase equivalent is to be retrieved
 * @return the lowercase equivalent code point
 */

public static int toLowerCase(int ch) { throw new RuntimeException("Stub!"); }

/**
 * Converts argument code point and returns a String object representing
 * the code point's value in UTF-16 format.
 * The result is a string whose length is 1 for BMP code points, 2 for supplementary ones.
 *
 * <p>Up-to-date Unicode implementation of java.lang.Character.toString().
 *
 * @param ch code point
 * @return string representation of the code point, null if code point is not
 *         defined in unicode
 */

public static java.lang.String toString(int ch) { throw new RuntimeException("Stub!"); }

/**
 * Converts the code point argument to titlecase.
 * If no titlecase is available, the uppercase is returned. If no uppercase
 * is available, the code point itself is returned.
 * Up-to-date Unicode implementation of java.lang.Character.toTitleCase()
 *
 * <p>This function only returns the simple, single-code point case mapping.
 * Full case mappings should be used whenever possible because they produce
 * better results by working on whole strings.
 * They take into account the string context and the language and can map
 * to a result string with a different length as appropriate.
 * Full case mappings are applied by the case mapping functions
 * that take String parameters rather than code points (int).
 * See also the User Guide chapter on C/POSIX migration:
 * http://www.icu-project.org/userguide/posix.html#case_mappings
 *
 * @param ch code point  whose title case is to be retrieved
 * @return titlecase code point
 */

public static int toTitleCase(int ch) { throw new RuntimeException("Stub!"); }

/**
 * Converts the character argument to uppercase.
 * If no uppercase is available, the character itself is returned.
 * Up-to-date Unicode implementation of java.lang.Character.toUpperCase()
 *
 * <p>This function only returns the simple, single-code point case mapping.
 * Full case mappings should be used whenever possible because they produce
 * better results by working on whole strings.
 * They take into account the string context and the language and can map
 * to a result string with a different length as appropriate.
 * Full case mappings are applied by the case mapping functions
 * that take String parameters rather than code points (int).
 * See also the User Guide chapter on C/POSIX migration:
 * http://www.icu-project.org/userguide/posix.html#case_mappings
 *
 * @param ch code point whose uppercase is to be retrieved
 * @return uppercase code point
 */

public static int toUpperCase(int ch) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Determines if the code point is a supplementary character.
 * A code point is a supplementary character if and only if it is greater
 * than <a href=#SUPPLEMENTARY_MIN_VALUE>SUPPLEMENTARY_MIN_VALUE</a>
 * @param ch code point to be determined if it is in the supplementary
 *        plane
 * @return true if code point is a supplementary character
 */

public static boolean isSupplementary(int ch) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Determines if the code point is in the BMP plane.
 * @param ch code point to be determined if it is not a supplementary
 *        character
 * @return true if code point is not a supplementary character
 */

public static boolean isBMP(int ch) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Determines whether the specified code point is a printable character
 * according to the Unicode standard.
 * @param ch code point to be determined if it is printable
 * @return true if the code point is a printable character
 */

public static boolean isPrintable(int ch) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Determines whether the specified code point is of base form.
 * A code point of base form does not graphically combine with preceding
 * characters, and is neither a control nor a format character.
 * @param ch code point to be determined if it is of base form
 * @return true if the code point is of base form
 */

public static boolean isBaseForm(int ch) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the Bidirection property of a code point.
 * For example, 0x0041 (letter A) has the LEFT_TO_RIGHT directional
 * property.<br>
 * Result returned belongs to the interface
 * <a href=UCharacterDirection.html>UCharacterDirection</a>
 * @param ch the code point to be determined its direction
 * @return direction constant from UCharacterDirection.
 */

public static int getDirection(int ch) { throw new RuntimeException("Stub!"); }

/**
 * Determines whether the code point has the "mirrored" property.
 * This property is set for characters that are commonly used in
 * Right-To-Left contexts and need to be displayed with a "mirrored"
 * glyph.
 * @param ch code point whose mirror is to be determined
 * @return true if the code point has the "mirrored" property
 */

public static boolean isMirrored(int ch) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Maps the specified code point to a "mirror-image" code point.
 * For code points with the "mirrored" property, implementations sometimes
 * need a "poor man's" mapping to another code point such that the default
 * glyph may serve as the mirror-image of the default glyph of the
 * specified code point.<br>
 * This is useful for text conversion to and from codepages with visual
 * order, and for displays without glyph selection capabilities.
 * @param ch code point whose mirror is to be retrieved
 * @return another code point that may serve as a mirror-image substitute,
 *         or ch itself if there is no such mapping or ch does not have the
 *         "mirrored" property
 */

public static int getMirror(int ch) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Maps the specified character to its paired bracket character.
 * For Bidi_Paired_Bracket_Type!=None, this is the same as getMirror(int).
 * Otherwise c itself is returned.
 * See http://www.unicode.org/reports/tr9/
 *
 * @param c the code point to be mapped
 * @return the paired bracket code point,
 *         or c itself if there is no such mapping
 *         (Bidi_Paired_Bracket_Type=None)
 *
 * @see android.icu.lang.UProperty#BIDI_PAIRED_BRACKET
 * @see android.icu.lang.UProperty#BIDI_PAIRED_BRACKET_TYPE
 * @see #getMirror(int)
 */

public static int getBidiPairedBracket(int c) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the combining class of the argument codepoint
 * @param ch code point whose combining is to be retrieved
 * @return the combining class of the codepoint
 */

public static int getCombiningClass(int ch) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> A code point is illegal if and only if
 * <ul>
 * <li> Out of bounds, less than 0 or greater than UCharacter.MAX_VALUE
 * <li> A surrogate value, 0xD800 to 0xDFFF
 * <li> Not-a-character, having the form 0x xxFFFF or 0x xxFFFE
 * </ul>
 * Note: legal does not mean that it is assigned in this version of Unicode.
 * @param ch code point to determine if it is a legal code point by itself
 * @return true if and only if legal.
 */

public static boolean isLegal(int ch) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> A string is legal iff all its code points are legal.
 * A code point is illegal if and only if
 * <ul>
 * <li> Out of bounds, less than 0 or greater than UCharacter.MAX_VALUE
 * <li> A surrogate value, 0xD800 to 0xDFFF
 * <li> Not-a-character, having the form 0x xxFFFF or 0x xxFFFE
 * </ul>
 * Note: legal does not mean that it is assigned in this version of Unicode.
 * @param str containing code points to examin
 * @return true if and only if legal.
 */

public static boolean isLegal(java.lang.String str) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the version of Unicode data used.
 * @return the unicode version number used
 */

public static android.icu.util.VersionInfo getUnicodeVersion() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the most current Unicode name of the argument code point, or
 * null if the character is unassigned or outside the range
 * UCharacter.MIN_VALUE and UCharacter.MAX_VALUE or does not have a name.
 * <br>
 * Note calling any methods related to code point names, e.g. get*Name*()
 * incurs a one-time initialisation cost to construct the name tables.
 * @param ch the code point for which to get the name
 * @return most current Unicode name
 */

public static java.lang.String getName(int ch) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the names for each of the characters in a string
 * @param s string to format
 * @param separator string to go between names
 * @return string of names
 */

public static java.lang.String getName(java.lang.String s, java.lang.String separator) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a name for a valid codepoint. Unlike, getName(int) and
 * getName1_0(int), this method will return a name even for codepoints that
 * are not assigned a name in UnicodeData.txt.
 *
 * <p>The names are returned in the following order.
 * <ul>
 * <li> Most current Unicode name if there is any
 * <li> Unicode 1.0 name if there is any
 * <li> Extended name in the form of
 *      "&lt;codepoint_type-codepoint_hex_digits&gt;". E.g., &lt;noncharacter-fffe&gt;
 * </ul>
 * Note calling any methods related to code point names, e.g. get*Name*()
 * incurs a one-time initialisation cost to construct the name tables.
 * @param ch the code point for which to get the name
 * @return a name for the argument codepoint
 */

public static java.lang.String getExtendedName(int ch) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the corrected name from NameAliases.txt if there is one.
 * Returns null if the character is unassigned or outside the range
 * UCharacter.MIN_VALUE and UCharacter.MAX_VALUE or does not have a name.
 * <br>
 * Note calling any methods related to code point names, e.g. get*Name*()
 * incurs a one-time initialisation cost to construct the name tables.
 * @param ch the code point for which to get the name alias
 * @return Unicode name alias, or null
 */

public static java.lang.String getNameAlias(int ch) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> <p>Finds a Unicode code point by its most current Unicode name and
 * return its code point value. All Unicode names are in uppercase.
 * Note calling any methods related to code point names, e.g. get*Name*()
 * incurs a one-time initialisation cost to construct the name tables.
 * @param name most current Unicode character name whose code point is to
 *        be returned
 * @return code point or -1 if name is not found
 */

public static int getCharFromName(java.lang.String name) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> <p>Find a Unicode character by either its name and return its code
 * point value. All Unicode names are in uppercase.
 * Extended names are all lowercase except for numbers and are contained
 * within angle brackets.
 * The names are searched in the following order
 * <ul>
 * <li> Most current Unicode name if there is any
 * <li> Unicode 1.0 name if there is any
 * <li> Extended name in the form of
 *      "&lt;codepoint_type-codepoint_hex_digits&gt;". E.g. &lt;noncharacter-FFFE&gt;
 * </ul>
 * Note calling any methods related to code point names, e.g. get*Name*()
 * incurs a one-time initialisation cost to construct the name tables.
 * @param name codepoint name
 * @return code point associated with the name or -1 if the name is not
 *         found.
 */

public static int getCharFromExtendedName(java.lang.String name) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> <p>Find a Unicode character by its corrected name alias and return
 * its code point value. All Unicode names are in uppercase.
 * Note calling any methods related to code point names, e.g. get*Name*()
 * incurs a one-time initialisation cost to construct the name tables.
 * @param name Unicode name alias whose code point is to be returned
 * @return code point or -1 if name is not found
 */

public static int getCharFromNameAlias(java.lang.String name) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Return the Unicode name for a given property, as given in the
 * Unicode database file PropertyAliases.txt.  Most properties
 * have more than one name.  The nameChoice determines which one
 * is returned.
 *
 * In addition, this function maps the property
 * UProperty.GENERAL_CATEGORY_MASK to the synthetic names "gcm" /
 * "General_Category_Mask".  These names are not in
 * PropertyAliases.txt.
 *
 * @param property UProperty selector.
 *
 * @param nameChoice UProperty.NameChoice selector for which name
 * to get.  All properties have a long name.  Most have a short
 * name, but some do not.  Unicode allows for additional names; if
 * present these will be returned by UProperty.NameChoice.LONG + i,
 * where i=1, 2,...
 *
 * @return a name, or null if Unicode explicitly defines no name
 * ("n/a") for a given property/nameChoice.  If a given nameChoice
 * throws an exception, then all larger values of nameChoice will
 * throw an exception.  If null is returned for a given
 * nameChoice, then other nameChoice values may return non-null
 * results.
 *
 * @exception java.lang.IllegalArgumentException thrown if property or
 * nameChoice are invalid.
 *
 * @see android.icu.lang.UProperty
 * @see android.icu.lang.UProperty.NameChoice
 */

public static java.lang.String getPropertyName(int property, int nameChoice) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Return the UProperty selector for a given property name, as
 * specified in the Unicode database file PropertyAliases.txt.
 * Short, long, and any other variants are recognized.
 *
 * In addition, this function maps the synthetic names "gcm" /
 * "General_Category_Mask" to the property
 * UProperty.GENERAL_CATEGORY_MASK.  These names are not in
 * PropertyAliases.txt.
 *
 * @param propertyAlias the property name to be matched.  The name
 * is compared using "loose matching" as described in
 * PropertyAliases.txt.
 *
 * @return a UProperty enum.
 *
 * @exception java.lang.IllegalArgumentException thrown if propertyAlias
 * is not recognized.
 *
 * @see android.icu.lang.UProperty
 */

public static int getPropertyEnum(java.lang.CharSequence propertyAlias) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Return the Unicode name for a given property value, as given in
 * the Unicode database file PropertyValueAliases.txt.  Most
 * values have more than one name.  The nameChoice determines
 * which one is returned.
 *
 * Note: Some of the names in PropertyValueAliases.txt can only be
 * retrieved using UProperty.GENERAL_CATEGORY_MASK, not
 * UProperty.GENERAL_CATEGORY.  These include: "C" / "Other", "L" /
 * "Letter", "LC" / "Cased_Letter", "M" / "Mark", "N" / "Number", "P"
 * / "Punctuation", "S" / "Symbol", and "Z" / "Separator".
 *
 * @param property UProperty selector constant.
 * UProperty.INT_START &lt;= property &lt; UProperty.INT_LIMIT or
 * UProperty.BINARY_START &lt;= property &lt; UProperty.BINARY_LIMIT or
 * UProperty.MASK_START &lt; = property &lt; UProperty.MASK_LIMIT.
 * If out of range, null is returned.
 *
 * @param value selector for a value for the given property.  In
 * general, valid values range from 0 up to some maximum.  There
 * are a few exceptions: (1.) UProperty.BLOCK values begin at the
 * non-zero value BASIC_LATIN.getID().  (2.)
 * UProperty.CANONICAL_COMBINING_CLASS values are not contiguous
 * and range from 0..240.  (3.)  UProperty.GENERAL_CATEGORY_MASK values
 * are mask values produced by left-shifting 1 by
 * UCharacter.getType().  This allows grouped categories such as
 * [:L:] to be represented.  Mask values are non-contiguous.
 *
 * @param nameChoice UProperty.NameChoice selector for which name
 * to get.  All values have a long name.  Most have a short name,
 * but some do not.  Unicode allows for additional names; if
 * present these will be returned by UProperty.NameChoice.LONG + i,
 * where i=1, 2,...
 *
 * @return a name, or null if Unicode explicitly defines no name
 * ("n/a") for a given property/value/nameChoice.  If a given
 * nameChoice throws an exception, then all larger values of
 * nameChoice will throw an exception.  If null is returned for a
 * given nameChoice, then other nameChoice values may return
 * non-null results.
 *
 * @exception java.lang.IllegalArgumentException thrown if property, value,
 * or nameChoice are invalid.
 *
 * @see android.icu.lang.UProperty
 * @see android.icu.lang.UProperty.NameChoice
 */

public static java.lang.String getPropertyValueName(int property, int value, int nameChoice) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Return the property value integer for a given value name, as
 * specified in the Unicode database file PropertyValueAliases.txt.
 * Short, long, and any other variants are recognized.
 *
 * Note: Some of the names in PropertyValueAliases.txt will only be
 * recognized with UProperty.GENERAL_CATEGORY_MASK, not
 * UProperty.GENERAL_CATEGORY.  These include: "C" / "Other", "L" /
 * "Letter", "LC" / "Cased_Letter", "M" / "Mark", "N" / "Number", "P"
 * / "Punctuation", "S" / "Symbol", and "Z" / "Separator".
 *
 * @param property UProperty selector constant.
 * UProperty.INT_START &lt;= property &lt; UProperty.INT_LIMIT or
 * UProperty.BINARY_START &lt;= property &lt; UProperty.BINARY_LIMIT or
 * UProperty.MASK_START &lt; = property &lt; UProperty.MASK_LIMIT.
 * Only these properties can be enumerated.
 *
 * @param valueAlias the value name to be matched.  The name is
 * compared using "loose matching" as described in
 * PropertyValueAliases.txt.
 *
 * @return a value integer.  Note: UProperty.GENERAL_CATEGORY
 * values are mask values produced by left-shifting 1 by
 * UCharacter.getType().  This allows grouped categories such as
 * [:L:] to be represented.
 *
 * @see android.icu.lang.UProperty
 * @throws java.lang.IllegalArgumentException if property is not a valid UProperty
 *         selector or valueAlias is not a value of this property
 */

public static int getPropertyValueEnum(int property, java.lang.CharSequence valueAlias) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a code point corresponding to the two surrogate code units.
 *
 * @param lead the lead char
 * @param trail the trail char
 * @return code point if surrogate characters are valid.
 * @exception java.lang.IllegalArgumentException thrown when the code units do
 *            not form a valid code point
 */

public static int getCodePoint(char lead, char trail) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the code point corresponding to the BMP code point.
 *
 * @param char16 the BMP code point
 * @return code point if argument is a valid character.
 * @exception java.lang.IllegalArgumentException thrown when char16 is not a valid
 *            code point
 */

public static int getCodePoint(char char16) { throw new RuntimeException("Stub!"); }

/**
 * Returns the uppercase version of the argument string.
 * Casing is dependent on the default locale and context-sensitive.
 * @param str source string to be performed on
 * @return uppercase version of the argument string
 */

public static java.lang.String toUpperCase(java.lang.String str) { throw new RuntimeException("Stub!"); }

/**
 * Returns the lowercase version of the argument string.
 * Casing is dependent on the default locale and context-sensitive
 * @param str source string to be performed on
 * @return lowercase version of the argument string
 */

public static java.lang.String toLowerCase(java.lang.String str) { throw new RuntimeException("Stub!"); }

/**
 * <p>Returns the titlecase version of the argument string.
 * <p>Position for titlecasing is determined by the argument break
 * iterator, hence the user can customize his break iterator for
 * a specialized titlecasing. In this case only the forward iteration
 * needs to be implemented.
 * If the break iterator passed in is null, the default Unicode algorithm
 * will be used to determine the titlecase positions.
 *
 * <p>Only positions returned by the break iterator will be title cased,
 * character in between the positions will all be in lower case.
 * <p>Casing is dependent on the default locale and context-sensitive
 * @param str source string to be performed on
 * @param breakiter break iterator to determine the positions in which
 *        the character should be title cased.
 * @return titlecase version of the argument string
 */

public static java.lang.String toTitleCase(java.lang.String str, android.icu.text.BreakIterator breakiter) { throw new RuntimeException("Stub!"); }

/**
 * Returns the uppercase version of the argument string.
 * Casing is dependent on the argument locale and context-sensitive.
 * @param locale which string is to be converted in
 * @param str source string to be performed on
 * @return uppercase version of the argument string
 */

public static java.lang.String toUpperCase(java.util.Locale locale, java.lang.String str) { throw new RuntimeException("Stub!"); }

/**
 * Returns the uppercase version of the argument string.
 * Casing is dependent on the argument locale and context-sensitive.
 * @param locale which string is to be converted in
 * @param str source string to be performed on
 * @return uppercase version of the argument string
 */

public static java.lang.String toUpperCase(android.icu.util.ULocale locale, java.lang.String str) { throw new RuntimeException("Stub!"); }

/**
 * Returns the lowercase version of the argument string.
 * Casing is dependent on the argument locale and context-sensitive
 * @param locale which string is to be converted in
 * @param str source string to be performed on
 * @return lowercase version of the argument string
 */

public static java.lang.String toLowerCase(java.util.Locale locale, java.lang.String str) { throw new RuntimeException("Stub!"); }

/**
 * Returns the lowercase version of the argument string.
 * Casing is dependent on the argument locale and context-sensitive
 * @param locale which string is to be converted in
 * @param str source string to be performed on
 * @return lowercase version of the argument string
 */

public static java.lang.String toLowerCase(android.icu.util.ULocale locale, java.lang.String str) { throw new RuntimeException("Stub!"); }

/**
 * <p>Returns the titlecase version of the argument string.
 * <p>Position for titlecasing is determined by the argument break
 * iterator, hence the user can customize his break iterator for
 * a specialized titlecasing. In this case only the forward iteration
 * needs to be implemented.
 * If the break iterator passed in is null, the default Unicode algorithm
 * will be used to determine the titlecase positions.
 *
 * <p>Only positions returned by the break iterator will be title cased,
 * character in between the positions will all be in lower case.
 * <p>Casing is dependent on the argument locale and context-sensitive
 * @param locale which string is to be converted in
 * @param str source string to be performed on
 * @param breakiter break iterator to determine the positions in which
 *        the character should be title cased.
 * @return titlecase version of the argument string
 */

public static java.lang.String toTitleCase(java.util.Locale locale, java.lang.String str, android.icu.text.BreakIterator breakiter) { throw new RuntimeException("Stub!"); }

/**
 * <p>Returns the titlecase version of the argument string.
 * <p>Position for titlecasing is determined by the argument break
 * iterator, hence the user can customize his break iterator for
 * a specialized titlecasing. In this case only the forward iteration
 * needs to be implemented.
 * If the break iterator passed in is null, the default Unicode algorithm
 * will be used to determine the titlecase positions.
 *
 * <p>Only positions returned by the break iterator will be title cased,
 * character in between the positions will all be in lower case.
 * <p>Casing is dependent on the argument locale and context-sensitive
 * @param locale which string is to be converted in
 * @param str source string to be performed on
 * @param titleIter break iterator to determine the positions in which
 *        the character should be title cased.
 * @return titlecase version of the argument string
 */

public static java.lang.String toTitleCase(android.icu.util.ULocale locale, java.lang.String str, android.icu.text.BreakIterator titleIter) { throw new RuntimeException("Stub!"); }

/**
 * <p>Returns the titlecase version of the argument string.
 * <p>Position for titlecasing is determined by the argument break
 * iterator, hence the user can customize his break iterator for
 * a specialized titlecasing. In this case only the forward iteration
 * needs to be implemented.
 * If the break iterator passed in is null, the default Unicode algorithm
 * will be used to determine the titlecase positions.
 *
 * <p>Only positions returned by the break iterator will be title cased,
 * character in between the positions will all be in lower case.
 * <p>Casing is dependent on the argument locale and context-sensitive
 * @param locale which string is to be converted in
 * @param str source string to be performed on
 * @param titleIter break iterator to determine the positions in which
 *        the character should be title cased.
 * @param options bit set to modify the titlecasing operation
 * @return titlecase version of the argument string
 * @see #TITLECASE_NO_LOWERCASE
 * @see #TITLECASE_NO_BREAK_ADJUSTMENT
 */

public static java.lang.String toTitleCase(android.icu.util.ULocale locale, java.lang.String str, android.icu.text.BreakIterator titleIter, int options) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> <p>Returns the titlecase version of the argument string.
 * <p>Position for titlecasing is determined by the argument break
 * iterator, hence the user can customize his break iterator for
 * a specialized titlecasing. In this case only the forward iteration
 * needs to be implemented.
 * If the break iterator passed in is null, the default Unicode algorithm
 * will be used to determine the titlecase positions.
 *
 * <p>Only positions returned by the break iterator will be title cased,
 * character in between the positions will all be in lower case.
 * <p>Casing is dependent on the argument locale and context-sensitive
 * @param locale which string is to be converted in
 * @param str source string to be performed on
 * @param titleIter break iterator to determine the positions in which
 *        the character should be title cased.
 * @param options bit set to modify the titlecasing operation
 * @return titlecase version of the argument string
 * @see #TITLECASE_NO_LOWERCASE
 * @see #TITLECASE_NO_BREAK_ADJUSTMENT
 */

public static java.lang.String toTitleCase(java.util.Locale locale, java.lang.String str, android.icu.text.BreakIterator titleIter, int options) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> The given character is mapped to its case folding equivalent according
 * to UnicodeData.txt and CaseFolding.txt; if the character has no case
 * folding equivalent, the character itself is returned.
 *
 * <p>This function only returns the simple, single-code point case mapping.
 * Full case mappings should be used whenever possible because they produce
 * better results by working on whole strings.
 * They can map to a result string with a different length as appropriate.
 * Full case mappings are applied by the case mapping functions
 * that take String parameters rather than code points (int).
 * See also the User Guide chapter on C/POSIX migration:
 * http://www.icu-project.org/userguide/posix.html#case_mappings
 *
 * @param ch             the character to be converted
 * @param defaultmapping Indicates whether the default mappings defined in
 *                       CaseFolding.txt are to be used, otherwise the
 *                       mappings for dotted I and dotless i marked with
 *                       'T' in CaseFolding.txt are included.
 * @return               the case folding equivalent of the character, if
 *                       any; otherwise the character itself.
 * @see                  #foldCase(String, boolean)
 */

public static int foldCase(int ch, boolean defaultmapping) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> The given string is mapped to its case folding equivalent according to
 * UnicodeData.txt and CaseFolding.txt; if any character has no case
 * folding equivalent, the character itself is returned.
 * "Full", multiple-code point case folding mappings are returned here.
 * For "simple" single-code point mappings use the API
 * foldCase(int ch, boolean defaultmapping).
 * @param str            the String to be converted
 * @param defaultmapping Indicates whether the default mappings defined in
 *                       CaseFolding.txt are to be used, otherwise the
 *                       mappings for dotted I and dotless i marked with
 *                       'T' in CaseFolding.txt are included.
 * @return               the case folding equivalent of the character, if
 *                       any; otherwise the character itself.
 * @see                  #foldCase(int, boolean)
 */

public static java.lang.String foldCase(java.lang.String str, boolean defaultmapping) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> The given character is mapped to its case folding equivalent according
 * to UnicodeData.txt and CaseFolding.txt; if the character has no case
 * folding equivalent, the character itself is returned.
 *
 * <p>This function only returns the simple, single-code point case mapping.
 * Full case mappings should be used whenever possible because they produce
 * better results by working on whole strings.
 * They can map to a result string with a different length as appropriate.
 * Full case mappings are applied by the case mapping functions
 * that take String parameters rather than code points (int).
 * See also the User Guide chapter on C/POSIX migration:
 * http://www.icu-project.org/userguide/posix.html#case_mappings
 *
 * @param ch the character to be converted
 * @param options A bit set for special processing. Currently the recognised options
 * are FOLD_CASE_EXCLUDE_SPECIAL_I and FOLD_CASE_DEFAULT
 * @return the case folding equivalent of the character, if any; otherwise the
 * character itself.
 * @see #foldCase(String, boolean)
 */

public static int foldCase(int ch, int options) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> The given string is mapped to its case folding equivalent according to
 * UnicodeData.txt and CaseFolding.txt; if any character has no case
 * folding equivalent, the character itself is returned.
 * "Full", multiple-code point case folding mappings are returned here.
 * For "simple" single-code point mappings use the API
 * foldCase(int ch, boolean defaultmapping).
 * @param str the String to be converted
 * @param options A bit set for special processing. Currently the recognised options
 *                are FOLD_CASE_EXCLUDE_SPECIAL_I and FOLD_CASE_DEFAULT
 * @return the case folding equivalent of the character, if any; otherwise the
 *         character itself.
 * @see #foldCase(int, boolean)
 */

public static java.lang.String foldCase(java.lang.String str, int options) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the numeric value of a Han character.
 *
 * <p>This returns the value of Han 'numeric' code points,
 * including those for zero, ten, hundred, thousand, ten thousand,
 * and hundred million.
 * This includes both the standard and 'checkwriting'
 * characters, the 'big circle' zero character, and the standard
 * zero character.
 *
 * <p>Note: The Unicode Standard has numeric values for more
 * Han characters recognized by this method
 * (see {@link #getNumericValue(int)} and the UCD file DerivedNumericValues.txt),
 * and a {@link android.icu.text.NumberFormat} can be used with
 * a Chinese {@link android.icu.text.NumberingSystem}.
 *
 * @param ch code point to query
 * @return value if it is a Han 'numeric character,' otherwise return -1.
 */

public static int getHanNumericValue(int ch) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> <p>Returns an iterator for character types, iterating over codepoints.
 * <p>Example of use:<br>
 * <pre>
 * RangeValueIterator iterator = UCharacter.getTypeIterator();
 * RangeValueIterator.Element element = new RangeValueIterator.Element();
 * while (iterator.next(element)) {
 *     System.out.println("Codepoint \\u" +
 *                        Integer.toHexString(element.start) +
 *                        " to codepoint \\u" +
 *                        Integer.toHexString(element.limit - 1) +
 *                        " has the character type " +
 *                        element.value);
 * }
 * </pre>
 * @return an iterator
 */

public static android.icu.util.RangeValueIterator getTypeIterator() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> <p>Returns an iterator for character names, iterating over codepoints.
 * <p>This API only gets the iterator for the modern, most up-to-date
 * Unicode names. For older 1.0 Unicode names use get1_0NameIterator() or
 * for extended names use getExtendedNameIterator().
 * <p>Example of use:<br>
 * <pre>
 * ValueIterator iterator = UCharacter.getNameIterator();
 * ValueIterator.Element element = new ValueIterator.Element();
 * while (iterator.next(element)) {
 *     System.out.println("Codepoint \\u" +
 *                        Integer.toHexString(element.codepoint) +
 *                        " has the name " + (String)element.value);
 * }
 * </pre>
 * <p>The maximal range which the name iterator iterates is from
 * UCharacter.MIN_VALUE to UCharacter.MAX_VALUE.
 * @return an iterator
 */

public static android.icu.util.ValueIterator getNameIterator() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> <p>Returns an iterator for character names, iterating over codepoints.
 * <p>This API only gets the iterator for the extended names.
 * For modern, most up-to-date Unicode names use getNameIterator() or
 * for older 1.0 Unicode names use get1_0NameIterator().
 * <p>Example of use:<br>
 * <pre>
 * ValueIterator iterator = UCharacter.getExtendedNameIterator();
 * ValueIterator.Element element = new ValueIterator.Element();
 * while (iterator.next(element)) {
 *     System.out.println("Codepoint \\u" +
 *                        Integer.toHexString(element.codepoint) +
 *                        " has the name " + (String)element.value);
 * }
 * </pre>
 * <p>The maximal range which the name iterator iterates is from
 * @return an iterator
 */

public static android.icu.util.ValueIterator getExtendedNameIterator() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the "age" of the code point.
 * <p>The "age" is the Unicode version when the code point was first
 * designated (as a non-character or for Private Use) or assigned a
 * character.
 * <p>This can be useful to avoid emitting code points to receiving
 * processes that do not accept newer characters.
 * <p>The data is from the UCD file DerivedAge.txt.
 * @param ch The code point.
 * @return the Unicode version number
 */

public static android.icu.util.VersionInfo getAge(int ch) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Check a binary Unicode property for a code point.
 * <p>Unicode, especially in version 3.2, defines many more properties
 * than the original set in UnicodeData.txt.
 * <p>This API is intended to reflect Unicode properties as defined in
 * the Unicode Character Database (UCD) and Unicode Technical Reports
 * (UTR).
 * <p>For details about the properties see
 * <a href=http://www.unicode.org/>http://www.unicode.org/</a>.
 * <p>For names of Unicode properties see the UCD file
 * PropertyAliases.txt.
 * <p>This API does not check the validity of the codepoint.
 * <p>Important: If ICU is built with UCD files from Unicode versions
 * below 3.2, then properties marked with "new" are not or
 * not fully available.
 * @param ch code point to test.
 * @param property selector constant from android.icu.lang.UProperty,
 *        identifies which binary property to check.
 * @return true or false according to the binary Unicode property value
 *         for ch. Also false if property is out of bounds or if the
 *         Unicode version does not have data for the property at all, or
 *         not for this code point.
 * @see android.icu.lang.UProperty
 */

public static boolean hasBinaryProperty(int ch, int property) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> <p>Check if a code point has the Alphabetic Unicode property.
 * <p>Same as UCharacter.hasBinaryProperty(ch, UProperty.ALPHABETIC).
 * <p>Different from UCharacter.isLetter(ch)!
 * @param ch codepoint to be tested
 */

public static boolean isUAlphabetic(int ch) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> <p>Check if a code point has the Lowercase Unicode property.
 * <p>Same as UCharacter.hasBinaryProperty(ch, UProperty.LOWERCASE).
 * <p>This is different from UCharacter.isLowerCase(ch)!
 * @param ch codepoint to be tested
 */

public static boolean isULowercase(int ch) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> <p>Check if a code point has the Uppercase Unicode property.
 * <p>Same as UCharacter.hasBinaryProperty(ch, UProperty.UPPERCASE).
 * <p>This is different from UCharacter.isUpperCase(ch)!
 * @param ch codepoint to be tested
 */

public static boolean isUUppercase(int ch) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> <p>Check if a code point has the White_Space Unicode property.
 * <p>Same as UCharacter.hasBinaryProperty(ch, UProperty.WHITE_SPACE).
 * <p>This is different from both UCharacter.isSpace(ch) and
 * UCharacter.isWhitespace(ch)!
 * @param ch codepoint to be tested
 */

public static boolean isUWhiteSpace(int ch) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the property value for a Unicode property type of a code point.
 * Also returns binary and mask property values.
 * <p>Unicode, especially in version 3.2, defines many more properties than
 * the original set in UnicodeData.txt.
 * <p>The properties APIs are intended to reflect Unicode properties as
 * defined in the Unicode Character Database (UCD) and Unicode Technical
 * Reports (UTR). For details about the properties see
 * http://www.unicode.org/.
 * <p>For names of Unicode properties see the UCD file PropertyAliases.txt.
 *
 * <pre>
 * Sample usage:
 * int ea = UCharacter.getIntPropertyValue(c, UProperty.EAST_ASIAN_WIDTH);
 * int ideo = UCharacter.getIntPropertyValue(c, UProperty.IDEOGRAPHIC);
 * boolean b = (ideo == 1) ? true : false;
 * </pre>
 * @param ch code point to test.
 * @param type UProperty selector constant, identifies which binary
 *        property to check. Must be
 *        UProperty.BINARY_START &lt;= type &lt; UProperty.BINARY_LIMIT or
 *        UProperty.INT_START &lt;= type &lt; UProperty.INT_LIMIT or
 *        UProperty.MASK_START &lt;= type &lt; UProperty.MASK_LIMIT.
 * @return numeric value that is directly the property value or,
 *         for enumerated properties, corresponds to the numeric value of
 *         the enumerated constant of the respective property value type
 *         ({@link android.icu.lang.UCharacterEnums.ECharacterCategory ECharacterCategory}, {@link android.icu.lang.UCharacterEnums.ECharacterDirection ECharacterDirection},
 *         {@link android.icu.lang.UCharacter.DecompositionType DecompositionType}, etc.).
 *         Returns 0 or 1 (for false / true) for binary Unicode properties.
 *         Returns a bit-mask for mask properties.
 *         Returns 0 if 'type' is out of bounds or if the Unicode version
 *         does not have data for the property at all, or not for this code
 *         point.
 * @see android.icu.lang.UProperty
 * @see #hasBinaryProperty
 * @see #getIntPropertyMinValue
 * @see #getIntPropertyMaxValue
 * @see #getUnicodeVersion
 */

public static int getIntPropertyValue(int ch, int type) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the minimum value for an integer/binary Unicode property type.
 * Can be used together with UCharacter.getIntPropertyMaxValue(int)
 * to allocate arrays of android.icu.text.UnicodeSet or similar.
 * @param type UProperty selector constant, identifies which binary
 *        property to check. Must be
 *        UProperty.BINARY_START &lt;= type &lt; UProperty.BINARY_LIMIT or
 *        UProperty.INT_START &lt;= type &lt; UProperty.INT_LIMIT.
 * @return Minimum value returned by UCharacter.getIntPropertyValue(int)
 *         for a Unicode property. 0 if the property
 *         selector 'type' is out of range.
 * @see android.icu.lang.UProperty
 * @see #hasBinaryProperty
 * @see #getUnicodeVersion
 * @see #getIntPropertyMaxValue
 * @see #getIntPropertyValue
 */

public static int getIntPropertyMinValue(int type) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the maximum value for an integer/binary Unicode property.
 * Can be used together with UCharacter.getIntPropertyMinValue(int)
 * to allocate arrays of android.icu.text.UnicodeSet or similar.
 * Examples for min/max values (for Unicode 3.2):
 * <ul>
 * <li> UProperty.BIDI_CLASS:    0/18
 * (UCharacterDirection.LEFT_TO_RIGHT/UCharacterDirection.BOUNDARY_NEUTRAL)
 * <li> UProperty.SCRIPT:        0/45 (UScript.COMMON/UScript.TAGBANWA)
 * <li> UProperty.IDEOGRAPHIC:   0/1  (false/true)
 * </ul>
 * For undefined UProperty constant values, min/max values will be 0/-1.
 * @param type UProperty selector constant, identifies which binary
 *        property to check. Must be
 *        UProperty.BINARY_START &lt;= type &lt; UProperty.BINARY_LIMIT or
 *        UProperty.INT_START &lt;= type &lt; UProperty.INT_LIMIT.
 * @return Maximum value returned by u_getIntPropertyValue for a Unicode
 *         property. &lt;= 0 if the property selector 'type' is out of range.
 * @see android.icu.lang.UProperty
 * @see #hasBinaryProperty
 * @see #getUnicodeVersion
 * @see #getIntPropertyMaxValue
 * @see #getIntPropertyValue
 */

public static int getIntPropertyMaxValue(int type) { throw new RuntimeException("Stub!"); }

/**
 * Provide the java.lang.Character forDigit API, for convenience.
 */

public static char forDigit(int digit, int radix) { throw new RuntimeException("Stub!"); }

/**
 * Equivalent to {@link java.lang.Character#isValidCodePoint Character#isValidCodePoint}.
 *
 * @param cp the code point to check
 * @return true if cp is a valid code point
 */

public static boolean isValidCodePoint(int cp) { throw new RuntimeException("Stub!"); }

/**
 * Same as {@link java.lang.Character#isSupplementaryCodePoint Character#isSupplementaryCodePoint}.
 *
 * @param cp the code point to check
 * @return true if cp is a supplementary code point
 */

public static boolean isSupplementaryCodePoint(int cp) { throw new RuntimeException("Stub!"); }

/**
 * Same as {@link java.lang.Character#isHighSurrogate Character#isHighSurrogate}.
 *
 * @param ch the char to check
 * @return true if ch is a high (lead) surrogate
 */

public static boolean isHighSurrogate(char ch) { throw new RuntimeException("Stub!"); }

/**
 * Same as {@link java.lang.Character#isLowSurrogate Character#isLowSurrogate}.
 *
 * @param ch the char to check
 * @return true if ch is a low (trail) surrogate
 */

public static boolean isLowSurrogate(char ch) { throw new RuntimeException("Stub!"); }

/**
 * Same as {@link java.lang.Character#isSurrogatePair Character#isSurrogatePair}.
 *
 * @param high the high (lead) char
 * @param low the low (trail) char
 * @return true if high, low form a surrogate pair
 */

public static boolean isSurrogatePair(char high, char low) { throw new RuntimeException("Stub!"); }

/**
 * Same as {@link java.lang.Character#charCount Character#charCount}.
 * Returns the number of chars needed to represent the code point (1 or 2).
 * This does not check the code point for validity.
 *
 * @param cp the code point to check
 * @return the number of chars needed to represent the code point
 */

public static int charCount(int cp) { throw new RuntimeException("Stub!"); }

/**
 * Same as {@link java.lang.Character#toCodePoint Character#toCodePoint}.
 * Returns the code point represented by the two surrogate code units.
 * This does not check the surrogate pair for validity.
 *
 * @param high the high (lead) surrogate
 * @param low the low (trail) surrogate
 * @return the code point formed by the surrogate pair
 */

public static int toCodePoint(char high, char low) { throw new RuntimeException("Stub!"); }

/**
 * Same as {@link java.lang.Character#codePointAt(java.lang.CharSequence,int) Character#codePointAt(CharSequence, int)}.
 * Returns the code point at index.
 * This examines only the characters at index and index+1.
 *
 * @param seq the characters to check
 * @param index the index of the first or only char forming the code point
 * @return the code point at the index
 */

public static int codePointAt(java.lang.CharSequence seq, int index) { throw new RuntimeException("Stub!"); }

/**
 * Same as {@link java.lang.Character#codePointAt(char[],int) Character#codePointAt(char[], int)}.
 * Returns the code point at index.
 * This examines only the characters at index and index+1.
 *
 * @param text the characters to check
 * @param index the index of the first or only char forming the code point
 * @return the code point at the index
 */

public static int codePointAt(char[] text, int index) { throw new RuntimeException("Stub!"); }

/**
 * Same as {@link java.lang.Character#codePointAt(char[],int,int) Character#codePointAt(char[], int, int)}.
 * Returns the code point at index.
 * This examines only the characters at index and index+1.
 *
 * @param text the characters to check
 * @param index the index of the first or only char forming the code point
 * @param limit the limit of the valid text
 * @return the code point at the index
 */

public static int codePointAt(char[] text, int index, int limit) { throw new RuntimeException("Stub!"); }

/**
 * Same as {@link java.lang.Character#codePointBefore(java.lang.CharSequence,int) Character#codePointBefore(CharSequence, int)}.
 * Return the code point before index.
 * This examines only the characters at index-1 and index-2.
 *
 * @param seq the characters to check
 * @param index the index after the last or only char forming the code point
 * @return the code point before the index
 */

public static int codePointBefore(java.lang.CharSequence seq, int index) { throw new RuntimeException("Stub!"); }

/**
 * Same as {@link java.lang.Character#codePointBefore(char[],int) Character#codePointBefore(char[], int)}.
 * Returns the code point before index.
 * This examines only the characters at index-1 and index-2.
 *
 * @param text the characters to check
 * @param index the index after the last or only char forming the code point
 * @return the code point before the index
 */

public static int codePointBefore(char[] text, int index) { throw new RuntimeException("Stub!"); }

/**
 * Same as {@link java.lang.Character#codePointBefore(char[],int,int) Character#codePointBefore(char[], int, int)}.
 * Return the code point before index.
 * This examines only the characters at index-1 and index-2.
 *
 * @param text the characters to check
 * @param index the index after the last or only char forming the code point
 * @param limit the start of the valid text
 * @return the code point before the index
 */

public static int codePointBefore(char[] text, int index, int limit) { throw new RuntimeException("Stub!"); }

/**
 * Same as {@link java.lang.Character#toChars(int,char[],int) Character#toChars(int, char[], int)}.
 * Writes the chars representing the
 * code point into the destination at the given index.
 *
 * @param cp the code point to convert
 * @param dst the destination array into which to put the char(s) representing the code point
 * @param dstIndex the index at which to put the first (or only) char
 * @return the count of the number of chars written (1 or 2)
 * @throws java.lang.IllegalArgumentException if cp is not a valid code point
 */

public static int toChars(int cp, char[] dst, int dstIndex) { throw new RuntimeException("Stub!"); }

/**
 * Same as {@link java.lang.Character#toChars(int) Character#toChars(int)}.
 * Returns a char array representing the code point.
 *
 * @param cp the code point to convert
 * @return an array containing the char(s) representing the code point
 * @throws java.lang.IllegalArgumentException if cp is not a valid code point
 */

public static char[] toChars(int cp) { throw new RuntimeException("Stub!"); }

/**
 * Equivalent to the {@link java.lang.Character#getDirectionality(char) Character#getDirectionality(char)} method, for
 * convenience. Returns a byte representing the directionality of the
 * character.
 *
 * <strong>[icu] Note:</strong> Unlike {@link java.lang.Character#getDirectionality(char) Character#getDirectionality(char)}, this returns
 * DIRECTIONALITY_LEFT_TO_RIGHT for undefined or out-of-bounds characters.
 *
 * <strong>[icu] Note:</strong> The return value must be tested using the constants defined in {@link android.icu.lang.UCharacterDirection UCharacterDirection} and its interface {@link android.icu.lang.UCharacterEnums.ECharacterDirection UCharacterEnums.ECharacterDirection} since the values are different from the ones
 * defined by <code>java.lang.Character</code>.
 * @param cp the code point to check
 * @return the directionality of the code point
 * @see #getDirection
 */

public static byte getDirectionality(int cp) { throw new RuntimeException("Stub!"); }

/**
 * Equivalent to the {@link java.lang.Character#codePointCount(java.lang.CharSequence,int,int) Character#codePointCount(CharSequence, int, int)}
 * method, for convenience.  Counts the number of code points in the range
 * of text.
 * @param text the characters to check
 * @param start the start of the range
 * @param limit the limit of the range
 * @return the number of code points in the range
 */

public static int codePointCount(java.lang.CharSequence text, int start, int limit) { throw new RuntimeException("Stub!"); }

/**
 * Equivalent to the {@link java.lang.Character#codePointCount(char[],int,int) Character#codePointCount(char[], int, int)} method, for
 * convenience. Counts the number of code points in the range of text.
 * @param text the characters to check
 * @param start the start of the range
 * @param limit the limit of the range
 * @return the number of code points in the range
 */

public static int codePointCount(char[] text, int start, int limit) { throw new RuntimeException("Stub!"); }

/**
 * Equivalent to the {@link java.lang.Character#offsetByCodePoints(java.lang.CharSequence,int,int) Character#offsetByCodePoints(CharSequence, int, int)}
 * method, for convenience.  Adjusts the char index by a code point offset.
 * @param text the characters to check
 * @param index the index to adjust
 * @param codePointOffset the number of code points by which to offset the index
 * @return the adjusted index
 */

public static int offsetByCodePoints(java.lang.CharSequence text, int index, int codePointOffset) { throw new RuntimeException("Stub!"); }

/**
 * Equivalent to the
 * {@link java.lang.Character#offsetByCodePoints(char[],int,int,int,int) Character#offsetByCodePoints(char[], int, int, int, int)}
 * method, for convenience.  Adjusts the char index by a code point offset.
 * @param text the characters to check
 * @param start the start of the range to check
 * @param count the length of the range to check
 * @param index the index to adjust
 * @param codePointOffset the number of code points by which to offset the index
 * @return the adjusted index
 */

public static int offsetByCodePoints(char[] text, int start, int count, int index, int codePointOffset) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Option value for case folding: use default mappings defined in
 * CaseFolding.txt.
 */

public static final int FOLD_CASE_DEFAULT = 0; // 0x0

/**
 * <strong>[icu]</strong> Option value for case folding:
 * Use the modified set of mappings provided in CaseFolding.txt to handle dotted I
 * and dotless i appropriately for Turkic languages (tr, az).
 *
 * <p>Before Unicode 3.2, CaseFolding.txt contains mappings marked with 'I' that
 * are to be included for default mappings and
 * excluded for the Turkic-specific mappings.
 *
 * <p>Unicode 3.2 CaseFolding.txt instead contains mappings marked with 'T' that
 * are to be excluded for default mappings and
 * included for the Turkic-specific mappings.
 */

public static final int FOLD_CASE_EXCLUDE_SPECIAL_I = 1; // 0x1

/**
 * Constant U+10FFFF, same as {@link java.lang.Character#MAX_CODE_POINT Character#MAX_CODE_POINT}.
 */

public static final int MAX_CODE_POINT = 1114111; // 0x10ffff

/**
 * Constant U+DBFF, same as {@link java.lang.Character#MAX_HIGH_SURROGATE Character#MAX_HIGH_SURROGATE}.
 */

public static final char MAX_HIGH_SURROGATE = 56319; // 0xdbff '\udbff'

/**
 * Constant U+DFFF, same as {@link java.lang.Character#MAX_LOW_SURROGATE Character#MAX_LOW_SURROGATE}.
 */

public static final char MAX_LOW_SURROGATE = 57343; // 0xdfff '\udfff'

/**
 * Compatibility constant for Java Character's MAX_RADIX.
 */

public static final int MAX_RADIX = 36; // 0x24

/**
 * Constant U+DFFF, same as {@link java.lang.Character#MAX_SURROGATE Character#MAX_SURROGATE}.
 */

public static final char MAX_SURROGATE = 57343; // 0xdfff '\udfff'

/**
 * The highest Unicode code point value (scalar value), constant U+10FFFF (uses 21 bits).
 * Same as {@link java.lang.Character#MAX_CODE_POINT Character#MAX_CODE_POINT}.
 *
 * <p>Up-to-date Unicode implementation of {@link java.lang.Character#MAX_VALUE Character#MAX_VALUE}
 * which is still a char with the value U+FFFF.
 */

public static final int MAX_VALUE = 1114111; // 0x10ffff

/**
 * Constant U+0000, same as {@link java.lang.Character#MIN_CODE_POINT Character#MIN_CODE_POINT}.
 */

public static final int MIN_CODE_POINT = 0; // 0x0

/**
 * Constant U+D800, same as {@link java.lang.Character#MIN_HIGH_SURROGATE Character#MIN_HIGH_SURROGATE}.
 */

public static final char MIN_HIGH_SURROGATE = 55296; // 0xd800 '\ud800'

/**
 * Constant U+DC00, same as {@link java.lang.Character#MIN_LOW_SURROGATE Character#MIN_LOW_SURROGATE}.
 */

public static final char MIN_LOW_SURROGATE = 56320; // 0xdc00 '\udc00'

/**
 * Compatibility constant for Java Character's MIN_RADIX.
 */

public static final int MIN_RADIX = 2; // 0x2

/**
 * Constant U+10000, same as {@link java.lang.Character#MIN_SUPPLEMENTARY_CODE_POINT Character#MIN_SUPPLEMENTARY_CODE_POINT}.
 */

public static final int MIN_SUPPLEMENTARY_CODE_POINT = 65536; // 0x10000

/**
 * Constant U+D800, same as {@link java.lang.Character#MIN_SURROGATE Character#MIN_SURROGATE}.
 */

public static final char MIN_SURROGATE = 55296; // 0xd800 '\ud800'

/**
 * The lowest Unicode code point value, constant 0.
 * Same as {@link java.lang.Character#MIN_CODE_POINT Character#MIN_CODE_POINT}, same integer value as {@link java.lang.Character#MIN_VALUE Character#MIN_VALUE}.
 */

public static final int MIN_VALUE = 0; // 0x0

/**
 * Special value that is returned by getUnicodeNumericValue(int) when no
 * numeric value is defined for a code point.
 * @see #getUnicodeNumericValue
 */

public static final double NO_NUMERIC_VALUE = -1.23456789E8;

/**
 * Unicode value used when translating into Unicode encoding form and there
 * is no existing character.
 */

public static final int REPLACEMENT_CHAR = 65533; // 0xfffd

/**
 * The minimum value for Supplementary code points, constant U+10000.
 * Same as {@link java.lang.Character#MIN_SUPPLEMENTARY_CODE_POINT Character#MIN_SUPPLEMENTARY_CODE_POINT}.
 */

public static final int SUPPLEMENTARY_MIN_VALUE = 65536; // 0x10000

/**
 * Do not adjust the titlecasing indexes from BreakIterator::next() indexes;
 * titlecase exactly the characters at breaks from the iterator.
 * Option bit for titlecasing APIs that take an options bit set.
 *
 * By default, titlecasing will take each break iterator index,
 * adjust it by looking for the next cased character, and titlecase that one.
 * Other characters are lowercased.
 *
 * This follows Unicode 4 &amp; 5 section 3.13 Default Case Operations:
 *
 * R3  toTitlecase(X): Find the word boundaries based on Unicode Standard Annex
 * #29, "Text Boundaries." Between each pair of word boundaries, find the first
 * cased character F. If F exists, map F to default_title(F); then map each
 * subsequent character C to default_lower(C).
 *
 * @see #toTitleCase
 * @see #TITLECASE_NO_LOWERCASE
 */

public static final int TITLECASE_NO_BREAK_ADJUSTMENT = 512; // 0x200

/**
 * Do not lowercase non-initial parts of words when titlecasing.
 * Option bit for titlecasing APIs that take an options bit set.
 *
 * By default, titlecasing will titlecase the first cased character
 * of a word and lowercase all other characters.
 * With this option, the other characters will not be modified.
 *
 * @see #toTitleCase
 */

public static final int TITLECASE_NO_LOWERCASE = 256; // 0x100
/**
 * Bidi Paired Bracket Type constants.
 *
 * @see android.icu.lang.UProperty#BIDI_PAIRED_BRACKET_TYPE
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static interface BidiPairedBracketType {

/**
 * Close paired bracket.
 */

public static final int CLOSE = 2; // 0x2

/**
 * Not a paired bracket.
 */

public static final int NONE = 0; // 0x0

/**
 * Open paired bracket.
 */

public static final int OPEN = 1; // 0x1
}

/**
 * Decomposition Type constants.
 * @see android.icu.lang.UProperty#DECOMPOSITION_TYPE
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static interface DecompositionType {

/**
 */

public static final int CANONICAL = 1; // 0x1

/**
 */

public static final int CIRCLE = 3; // 0x3

/**
 */

public static final int COMPAT = 2; // 0x2

/**
 */

public static final int FINAL = 4; // 0x4

/**
 */

public static final int FONT = 5; // 0x5

/**
 */

public static final int FRACTION = 6; // 0x6

/**
 */

public static final int INITIAL = 7; // 0x7

/**
 */

public static final int ISOLATED = 8; // 0x8

/**
 */

public static final int MEDIAL = 9; // 0x9

/**
 */

public static final int NARROW = 10; // 0xa

/**
 */

public static final int NOBREAK = 11; // 0xb

/**
 */

public static final int NONE = 0; // 0x0

/**
 */

public static final int SMALL = 12; // 0xc

/**
 */

public static final int SQUARE = 13; // 0xd

/**
 */

public static final int SUB = 14; // 0xe

/**
 */

public static final int SUPER = 15; // 0xf

/**
 */

public static final int VERTICAL = 16; // 0x10

/**
 */

public static final int WIDE = 17; // 0x11
}

/**
 * East Asian Width constants.
 * @see android.icu.lang.UProperty#EAST_ASIAN_WIDTH
 * @see android.icu.lang.UCharacter#getIntPropertyValue
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static interface EastAsianWidth {

/**
 */

public static final int AMBIGUOUS = 1; // 0x1

/**
 */

public static final int FULLWIDTH = 3; // 0x3

/**
 */

public static final int HALFWIDTH = 2; // 0x2

/**
 */

public static final int NARROW = 4; // 0x4

/**
 */

public static final int NEUTRAL = 0; // 0x0

/**
 */

public static final int WIDE = 5; // 0x5
}

/**
 * Grapheme Cluster Break constants.
 * @see android.icu.lang.UProperty#GRAPHEME_CLUSTER_BREAK
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static interface GraphemeClusterBreak {

/**
 */

public static final int CONTROL = 1; // 0x1

/**
 */

public static final int CR = 2; // 0x2

/**
 */

public static final int EXTEND = 3; // 0x3

/***/

public static final int E_BASE = 13; // 0xd

/***/

public static final int E_BASE_GAZ = 14; // 0xe

/***/

public static final int E_MODIFIER = 15; // 0xf

/***/

public static final int GLUE_AFTER_ZWJ = 16; // 0x10

/**
 */

public static final int L = 4; // 0x4

/**
 */

public static final int LF = 5; // 0x5

/**
 */

public static final int LV = 6; // 0x6

/**
 */

public static final int LVT = 7; // 0x7

/**
 */

public static final int OTHER = 0; // 0x0

/**
 */

public static final int PREPEND = 11; // 0xb

/***/

public static final int REGIONAL_INDICATOR = 12; // 0xc

/**
 */

public static final int SPACING_MARK = 10; // 0xa

/**
 */

public static final int T = 8; // 0x8

/**
 */

public static final int V = 9; // 0x9

/***/

public static final int ZWJ = 17; // 0x11
}

/**
 * Hangul Syllable Type constants.
 *
 * @see android.icu.lang.UProperty#HANGUL_SYLLABLE_TYPE
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static interface HangulSyllableType {

/**
 */

public static final int LEADING_JAMO = 1; // 0x1

/**
 */

public static final int LVT_SYLLABLE = 5; // 0x5

/**
 */

public static final int LV_SYLLABLE = 4; // 0x4

/**
 */

public static final int NOT_APPLICABLE = 0; // 0x0

/**
 */

public static final int TRAILING_JAMO = 3; // 0x3

/**
 */

public static final int VOWEL_JAMO = 2; // 0x2
}

/**
 * Indic Positional Category constants.
 *
 * @see android.icu.lang.UProperty#INDIC_POSITIONAL_CATEGORY
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static interface IndicPositionalCategory {

/***/

public static final int BOTTOM = 1; // 0x1

/***/

public static final int BOTTOM_AND_LEFT = 2; // 0x2

/***/

public static final int BOTTOM_AND_RIGHT = 3; // 0x3

/***/

public static final int LEFT = 4; // 0x4

/***/

public static final int LEFT_AND_RIGHT = 5; // 0x5

/***/

public static final int NA = 0; // 0x0

/***/

public static final int OVERSTRUCK = 6; // 0x6

/***/

public static final int RIGHT = 7; // 0x7

/***/

public static final int TOP = 8; // 0x8

/***/

public static final int TOP_AND_BOTTOM = 9; // 0x9

/***/

public static final int TOP_AND_BOTTOM_AND_LEFT = 15; // 0xf

/***/

public static final int TOP_AND_BOTTOM_AND_RIGHT = 10; // 0xa

/***/

public static final int TOP_AND_LEFT = 11; // 0xb

/***/

public static final int TOP_AND_LEFT_AND_RIGHT = 12; // 0xc

/***/

public static final int TOP_AND_RIGHT = 13; // 0xd

/***/

public static final int VISUAL_ORDER_LEFT = 14; // 0xe
}

/**
 * Indic Syllabic Category constants.
 *
 * @see android.icu.lang.UProperty#INDIC_SYLLABIC_CATEGORY
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static interface IndicSyllabicCategory {

/***/

public static final int AVAGRAHA = 1; // 0x1

/***/

public static final int BINDU = 2; // 0x2

/***/

public static final int BRAHMI_JOINING_NUMBER = 3; // 0x3

/***/

public static final int CANTILLATION_MARK = 4; // 0x4

/***/

public static final int CONSONANT = 5; // 0x5

/***/

public static final int CONSONANT_DEAD = 6; // 0x6

/***/

public static final int CONSONANT_FINAL = 7; // 0x7

/***/

public static final int CONSONANT_HEAD_LETTER = 8; // 0x8

/***/

public static final int CONSONANT_INITIAL_POSTFIXED = 9; // 0x9

/***/

public static final int CONSONANT_KILLER = 10; // 0xa

/***/

public static final int CONSONANT_MEDIAL = 11; // 0xb

/***/

public static final int CONSONANT_PLACEHOLDER = 12; // 0xc

/***/

public static final int CONSONANT_PRECEDING_REPHA = 13; // 0xd

/***/

public static final int CONSONANT_PREFIXED = 14; // 0xe

/***/

public static final int CONSONANT_SUBJOINED = 15; // 0xf

/***/

public static final int CONSONANT_SUCCEEDING_REPHA = 16; // 0x10

/***/

public static final int CONSONANT_WITH_STACKER = 17; // 0x11

/***/

public static final int GEMINATION_MARK = 18; // 0x12

/***/

public static final int INVISIBLE_STACKER = 19; // 0x13

/***/

public static final int JOINER = 20; // 0x14

/***/

public static final int MODIFYING_LETTER = 21; // 0x15

/***/

public static final int NON_JOINER = 22; // 0x16

/***/

public static final int NUKTA = 23; // 0x17

/***/

public static final int NUMBER = 24; // 0x18

/***/

public static final int NUMBER_JOINER = 25; // 0x19

/***/

public static final int OTHER = 0; // 0x0

/***/

public static final int PURE_KILLER = 26; // 0x1a

/***/

public static final int REGISTER_SHIFTER = 27; // 0x1b

/***/

public static final int SYLLABLE_MODIFIER = 28; // 0x1c

/***/

public static final int TONE_LETTER = 29; // 0x1d

/***/

public static final int TONE_MARK = 30; // 0x1e

/***/

public static final int VIRAMA = 31; // 0x1f

/***/

public static final int VISARGA = 32; // 0x20

/***/

public static final int VOWEL = 33; // 0x21

/***/

public static final int VOWEL_DEPENDENT = 34; // 0x22

/***/

public static final int VOWEL_INDEPENDENT = 35; // 0x23
}

/**
 * Joining Group constants.
 * @see android.icu.lang.UProperty#JOINING_GROUP
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static interface JoiningGroup {

/***/

public static final int AFRICAN_FEH = 86; // 0x56

/***/

public static final int AFRICAN_NOON = 87; // 0x57

/***/

public static final int AFRICAN_QAF = 88; // 0x58

/**
 */

public static final int AIN = 1; // 0x1

/**
 */

public static final int ALAPH = 2; // 0x2

/**
 */

public static final int ALEF = 3; // 0x3

/**
 */

public static final int BEH = 4; // 0x4

/**
 */

public static final int BETH = 5; // 0x5

/**
 */

public static final int BURUSHASKI_YEH_BARREE = 54; // 0x36

/**
 */

public static final int DAL = 6; // 0x6

/**
 */

public static final int DALATH_RISH = 7; // 0x7

/**
 */

public static final int E = 8; // 0x8

/***/

public static final int FARSI_YEH = 55; // 0x37

/**
 */

public static final int FE = 51; // 0x33

/**
 */

public static final int FEH = 9; // 0x9

/**
 */

public static final int FINAL_SEMKATH = 10; // 0xa

/**
 */

public static final int GAF = 11; // 0xb

/**
 */

public static final int GAMAL = 12; // 0xc

/**
 */

public static final int HAH = 13; // 0xd

/**
 */

public static final int HAMZA_ON_HEH_GOAL = 14; // 0xe

/***/

public static final int HANIFI_ROHINGYA_KINNA_YA = 100; // 0x64

/***/

public static final int HANIFI_ROHINGYA_PA = 101; // 0x65

/**
 */

public static final int HE = 15; // 0xf

/**
 */

public static final int HEH = 16; // 0x10

/**
 */

public static final int HEH_GOAL = 17; // 0x11

/**
 */

public static final int HETH = 18; // 0x12

/**
 */

public static final int KAF = 19; // 0x13

/**
 */

public static final int KAPH = 20; // 0x14

/**
 */

public static final int KHAPH = 52; // 0x34

/**
 */

public static final int KNOTTED_HEH = 21; // 0x15

/**
 */

public static final int LAM = 22; // 0x16

/**
 */

public static final int LAMADH = 23; // 0x17

/***/

public static final int MALAYALAM_BHA = 89; // 0x59

/***/

public static final int MALAYALAM_JA = 90; // 0x5a

/***/

public static final int MALAYALAM_LLA = 91; // 0x5b

/***/

public static final int MALAYALAM_LLLA = 92; // 0x5c

/***/

public static final int MALAYALAM_NGA = 93; // 0x5d

/***/

public static final int MALAYALAM_NNA = 94; // 0x5e

/***/

public static final int MALAYALAM_NNNA = 95; // 0x5f

/***/

public static final int MALAYALAM_NYA = 96; // 0x60

/***/

public static final int MALAYALAM_RA = 97; // 0x61

/***/

public static final int MALAYALAM_SSA = 98; // 0x62

/***/

public static final int MALAYALAM_TTA = 99; // 0x63

/***/

public static final int MANICHAEAN_ALEPH = 58; // 0x3a

/***/

public static final int MANICHAEAN_AYIN = 59; // 0x3b

/***/

public static final int MANICHAEAN_BETH = 60; // 0x3c

/***/

public static final int MANICHAEAN_DALETH = 61; // 0x3d

/***/

public static final int MANICHAEAN_DHAMEDH = 62; // 0x3e

/***/

public static final int MANICHAEAN_FIVE = 63; // 0x3f

/***/

public static final int MANICHAEAN_GIMEL = 64; // 0x40

/***/

public static final int MANICHAEAN_HETH = 65; // 0x41

/***/

public static final int MANICHAEAN_HUNDRED = 66; // 0x42

/***/

public static final int MANICHAEAN_KAPH = 67; // 0x43

/***/

public static final int MANICHAEAN_LAMEDH = 68; // 0x44

/***/

public static final int MANICHAEAN_MEM = 69; // 0x45

/***/

public static final int MANICHAEAN_NUN = 70; // 0x46

/***/

public static final int MANICHAEAN_ONE = 71; // 0x47

/***/

public static final int MANICHAEAN_PE = 72; // 0x48

/***/

public static final int MANICHAEAN_QOPH = 73; // 0x49

/***/

public static final int MANICHAEAN_RESH = 74; // 0x4a

/***/

public static final int MANICHAEAN_SADHE = 75; // 0x4b

/***/

public static final int MANICHAEAN_SAMEKH = 76; // 0x4c

/***/

public static final int MANICHAEAN_TAW = 77; // 0x4d

/***/

public static final int MANICHAEAN_TEN = 78; // 0x4e

/***/

public static final int MANICHAEAN_TETH = 79; // 0x4f

/***/

public static final int MANICHAEAN_THAMEDH = 80; // 0x50

/***/

public static final int MANICHAEAN_TWENTY = 81; // 0x51

/***/

public static final int MANICHAEAN_WAW = 82; // 0x52

/***/

public static final int MANICHAEAN_YODH = 83; // 0x53

/***/

public static final int MANICHAEAN_ZAYIN = 84; // 0x54

/**
 */

public static final int MEEM = 24; // 0x18

/**
 */

public static final int MIM = 25; // 0x19

/**
 */

public static final int NOON = 26; // 0x1a

/**
 */

public static final int NO_JOINING_GROUP = 0; // 0x0

/**
 */

public static final int NUN = 27; // 0x1b

/***/

public static final int NYA = 56; // 0x38

/**
 */

public static final int PE = 28; // 0x1c

/**
 */

public static final int QAF = 29; // 0x1d

/**
 */

public static final int QAPH = 30; // 0x1e

/**
 */

public static final int REH = 31; // 0x1f

/**
 */

public static final int REVERSED_PE = 32; // 0x20

/***/

public static final int ROHINGYA_YEH = 57; // 0x39

/**
 */

public static final int SAD = 33; // 0x21

/**
 */

public static final int SADHE = 34; // 0x22

/**
 */

public static final int SEEN = 35; // 0x23

/**
 */

public static final int SEMKATH = 36; // 0x24

/**
 */

public static final int SHIN = 37; // 0x25

/***/

public static final int STRAIGHT_WAW = 85; // 0x55

/**
 */

public static final int SWASH_KAF = 38; // 0x26

/**
 */

public static final int SYRIAC_WAW = 39; // 0x27

/**
 */

public static final int TAH = 40; // 0x28

/**
 */

public static final int TAW = 41; // 0x29

/**
 */

public static final int TEH_MARBUTA = 42; // 0x2a

/***/

public static final int TEH_MARBUTA_GOAL = 14; // 0xe

/**
 */

public static final int TETH = 43; // 0x2b

/**
 */

public static final int WAW = 44; // 0x2c

/**
 */

public static final int YEH = 45; // 0x2d

/**
 */

public static final int YEH_BARREE = 46; // 0x2e

/**
 */

public static final int YEH_WITH_TAIL = 47; // 0x2f

/**
 */

public static final int YUDH = 48; // 0x30

/**
 */

public static final int YUDH_HE = 49; // 0x31

/**
 */

public static final int ZAIN = 50; // 0x32

/**
 */

public static final int ZHAIN = 53; // 0x35
}

/**
 * Joining Type constants.
 * @see android.icu.lang.UProperty#JOINING_TYPE
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static interface JoiningType {

/**
 */

public static final int DUAL_JOINING = 2; // 0x2

/**
 */

public static final int JOIN_CAUSING = 1; // 0x1

/**
 */

public static final int LEFT_JOINING = 3; // 0x3

/**
 */

public static final int NON_JOINING = 0; // 0x0

/**
 */

public static final int RIGHT_JOINING = 4; // 0x4

/**
 */

public static final int TRANSPARENT = 5; // 0x5
}

/**
 * Line Break constants.
 * @see android.icu.lang.UProperty#LINE_BREAK
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static interface LineBreak {

/**
 */

public static final int ALPHABETIC = 2; // 0x2

/**
 */

public static final int AMBIGUOUS = 1; // 0x1

/**
 */

public static final int BREAK_AFTER = 4; // 0x4

/**
 */

public static final int BREAK_BEFORE = 5; // 0x5

/**
 */

public static final int BREAK_BOTH = 3; // 0x3

/**
 */

public static final int BREAK_SYMBOLS = 27; // 0x1b

/**
 */

public static final int CARRIAGE_RETURN = 10; // 0xa

/***/

public static final int CLOSE_PARENTHESIS = 36; // 0x24

/**
 */

public static final int CLOSE_PUNCTUATION = 8; // 0x8

/**
 */

public static final int COMBINING_MARK = 9; // 0x9

/**
 */

public static final int COMPLEX_CONTEXT = 24; // 0x18

/***/

public static final int CONDITIONAL_JAPANESE_STARTER = 37; // 0x25

/**
 */

public static final int CONTINGENT_BREAK = 7; // 0x7

/**
 */

public static final int EXCLAMATION = 11; // 0xb

/***/

public static final int E_BASE = 40; // 0x28

/***/

public static final int E_MODIFIER = 41; // 0x29

/**
 */

public static final int GLUE = 12; // 0xc

/**
 */

public static final int H2 = 31; // 0x1f

/**
 */

public static final int H3 = 32; // 0x20

/***/

public static final int HEBREW_LETTER = 38; // 0x26

/**
 */

public static final int HYPHEN = 13; // 0xd

/**
 */

public static final int IDEOGRAPHIC = 14; // 0xe

/**
 */

public static final int INFIX_NUMERIC = 16; // 0x10

/**
 * Renamed from the misspelled "inseperable" in Unicode 4.0.1.
 */

public static final int INSEPARABLE = 15; // 0xf

/**
 * @see #INSEPARABLE
 */

public static final int INSEPERABLE = 15; // 0xf

/**
 */

public static final int JL = 33; // 0x21

/**
 */

public static final int JT = 34; // 0x22

/**
 */

public static final int JV = 35; // 0x23

/**
 */

public static final int LINE_FEED = 17; // 0x11

/**
 */

public static final int MANDATORY_BREAK = 6; // 0x6

/**
 */

public static final int NEXT_LINE = 29; // 0x1d

/**
 */

public static final int NONSTARTER = 18; // 0x12

/**
 */

public static final int NUMERIC = 19; // 0x13

/**
 */

public static final int OPEN_PUNCTUATION = 20; // 0x14

/**
 */

public static final int POSTFIX_NUMERIC = 21; // 0x15

/**
 */

public static final int PREFIX_NUMERIC = 22; // 0x16

/**
 */

public static final int QUOTATION = 23; // 0x17

/***/

public static final int REGIONAL_INDICATOR = 39; // 0x27

/**
 */

public static final int SPACE = 26; // 0x1a

/**
 */

public static final int SURROGATE = 25; // 0x19

/**
 */

public static final int UNKNOWN = 0; // 0x0

/**
 */

public static final int WORD_JOINER = 30; // 0x1e

/***/

public static final int ZWJ = 42; // 0x2a

/**
 */

public static final int ZWSPACE = 28; // 0x1c
}

/**
 * Numeric Type constants.
 * @see android.icu.lang.UProperty#NUMERIC_TYPE
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static interface NumericType {

/**
 */

public static final int DECIMAL = 1; // 0x1

/**
 */

public static final int DIGIT = 2; // 0x2

/**
 */

public static final int NONE = 0; // 0x0

/**
 */

public static final int NUMERIC = 3; // 0x3
}

/**
 * Sentence Break constants.
 * @see android.icu.lang.UProperty#SENTENCE_BREAK
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static interface SentenceBreak {

/**
 */

public static final int ATERM = 1; // 0x1

/**
 */

public static final int CLOSE = 2; // 0x2

/**
 */

public static final int CR = 11; // 0xb

/**
 */

public static final int EXTEND = 12; // 0xc

/**
 */

public static final int FORMAT = 3; // 0x3

/**
 */

public static final int LF = 13; // 0xd

/**
 */

public static final int LOWER = 4; // 0x4

/**
 */

public static final int NUMERIC = 5; // 0x5

/**
 */

public static final int OLETTER = 6; // 0x6

/**
 */

public static final int OTHER = 0; // 0x0

/**
 */

public static final int SCONTINUE = 14; // 0xe

/**
 */

public static final int SEP = 7; // 0x7

/**
 */

public static final int SP = 8; // 0x8

/**
 */

public static final int STERM = 9; // 0x9

/**
 */

public static final int UPPER = 10; // 0xa
}

/**
 * <strong>[icu enhancement]</strong> ICU's replacement for {@link java.lang.Character.UnicodeBlock}.&nbsp;Methods, fields, and other functionality specific to ICU are labeled '<strong>[icu]</strong>'.
 *
 * A family of character subsets representing the character blocks in the
 * Unicode specification, generated from Unicode Data file Blocks.txt.
 * Character blocks generally define characters used for a specific script
 * or purpose. A character is contained by at most one Unicode block.
 *
 * <strong>[icu] Note:</strong> All fields named XXX_ID are specific to ICU.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static final class UnicodeBlock extends java.lang.Character.Subset {

private UnicodeBlock() { super(null); throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the only instance of the UnicodeBlock with the argument ID.
 * If no such ID exists, a INVALID_CODE UnicodeBlock will be returned.
 * @param id UnicodeBlock ID
 * @return the only instance of the UnicodeBlock with the argument ID
 *         if it exists, otherwise a INVALID_CODE UnicodeBlock will be
 *         returned.
 */

public static android.icu.lang.UCharacter.UnicodeBlock getInstance(int id) { throw new RuntimeException("Stub!"); }

/**
 * Returns the Unicode allocation block that contains the code point,
 * or null if the code point is not a member of a defined block.
 * @param ch code point to be tested
 * @return the Unicode allocation block that contains the code point
 */

public static android.icu.lang.UCharacter.UnicodeBlock of(int ch) { throw new RuntimeException("Stub!"); }

/**
 * Alternative to the {@link java.lang.Character.UnicodeBlock#forName(String)} method.
 * Returns the Unicode block with the given name. <strong>[icu] Note:</strong> Unlike
 * {@link java.lang.Character.UnicodeBlock#forName(String)}, this only matches
 * against the official UCD name and the Java block name
 * (ignoring case).
 * @param blockName the name of the block to match
 * @return the UnicodeBlock with that name
 * @throws java.lang.IllegalArgumentException if the blockName could not be matched
 */

public static android.icu.lang.UCharacter.UnicodeBlock forName(java.lang.String blockName) { throw new RuntimeException("Stub!"); }

/**
 * {icu} Returns the type ID of this Unicode block
 * @return integer type ID of this Unicode block
 */

public int getID() { throw new RuntimeException("Stub!"); }

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock ADLAM;
static { ADLAM = null; }

/***/

public static final int ADLAM_ID = 263; // 0x107

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock AEGEAN_NUMBERS;
static { AEGEAN_NUMBERS = null; }

/**
 */

public static final int AEGEAN_NUMBERS_ID = 119; // 0x77

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock AHOM;
static { AHOM = null; }

/***/

public static final int AHOM_ID = 253; // 0xfd

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock ALCHEMICAL_SYMBOLS;
static { ALCHEMICAL_SYMBOLS = null; }

/***/

public static final int ALCHEMICAL_SYMBOLS_ID = 208; // 0xd0

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock ALPHABETIC_PRESENTATION_FORMS;
static { ALPHABETIC_PRESENTATION_FORMS = null; }

/**
 */

public static final int ALPHABETIC_PRESENTATION_FORMS_ID = 80; // 0x50

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock ANATOLIAN_HIEROGLYPHS;
static { ANATOLIAN_HIEROGLYPHS = null; }

/***/

public static final int ANATOLIAN_HIEROGLYPHS_ID = 254; // 0xfe

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock ANCIENT_GREEK_MUSICAL_NOTATION;
static { ANCIENT_GREEK_MUSICAL_NOTATION = null; }

/**
 */

public static final int ANCIENT_GREEK_MUSICAL_NOTATION_ID = 126; // 0x7e

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock ANCIENT_GREEK_NUMBERS;
static { ANCIENT_GREEK_NUMBERS = null; }

/**
 */

public static final int ANCIENT_GREEK_NUMBERS_ID = 127; // 0x7f

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock ANCIENT_SYMBOLS;
static { ANCIENT_SYMBOLS = null; }

/**
 */

public static final int ANCIENT_SYMBOLS_ID = 165; // 0xa5

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock ARABIC;
static { ARABIC = null; }

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock ARABIC_EXTENDED_A;
static { ARABIC_EXTENDED_A = null; }

/***/

public static final int ARABIC_EXTENDED_A_ID = 210; // 0xd2

/**
 */

public static final int ARABIC_ID = 12; // 0xc

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock ARABIC_MATHEMATICAL_ALPHABETIC_SYMBOLS;
static { ARABIC_MATHEMATICAL_ALPHABETIC_SYMBOLS = null; }

/***/

public static final int ARABIC_MATHEMATICAL_ALPHABETIC_SYMBOLS_ID = 211; // 0xd3

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock ARABIC_PRESENTATION_FORMS_A;
static { ARABIC_PRESENTATION_FORMS_A = null; }

/**
 */

public static final int ARABIC_PRESENTATION_FORMS_A_ID = 81; // 0x51

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock ARABIC_PRESENTATION_FORMS_B;
static { ARABIC_PRESENTATION_FORMS_B = null; }

/**
 */

public static final int ARABIC_PRESENTATION_FORMS_B_ID = 85; // 0x55

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock ARABIC_SUPPLEMENT;
static { ARABIC_SUPPLEMENT = null; }

/**
 */

public static final int ARABIC_SUPPLEMENT_ID = 128; // 0x80

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock ARMENIAN;
static { ARMENIAN = null; }

/**
 */

public static final int ARMENIAN_ID = 10; // 0xa

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock ARROWS;
static { ARROWS = null; }

/**
 */

public static final int ARROWS_ID = 46; // 0x2e

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock AVESTAN;
static { AVESTAN = null; }

/***/

public static final int AVESTAN_ID = 188; // 0xbc

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock BALINESE;
static { BALINESE = null; }

/**
 */

public static final int BALINESE_ID = 147; // 0x93

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock BAMUM;
static { BAMUM = null; }

/***/

public static final int BAMUM_ID = 177; // 0xb1

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock BAMUM_SUPPLEMENT;
static { BAMUM_SUPPLEMENT = null; }

/***/

public static final int BAMUM_SUPPLEMENT_ID = 202; // 0xca

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock BASIC_LATIN;
static { BASIC_LATIN = null; }

/**
 */

public static final int BASIC_LATIN_ID = 1; // 0x1

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock BASSA_VAH;
static { BASSA_VAH = null; }

/***/

public static final int BASSA_VAH_ID = 221; // 0xdd

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock BATAK;
static { BATAK = null; }

/***/

public static final int BATAK_ID = 199; // 0xc7

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock BENGALI;
static { BENGALI = null; }

/**
 */

public static final int BENGALI_ID = 16; // 0x10

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock BHAIKSUKI;
static { BHAIKSUKI = null; }

/***/

public static final int BHAIKSUKI_ID = 264; // 0x108

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock BLOCK_ELEMENTS;
static { BLOCK_ELEMENTS = null; }

/**
 */

public static final int BLOCK_ELEMENTS_ID = 53; // 0x35

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock BOPOMOFO;
static { BOPOMOFO = null; }

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock BOPOMOFO_EXTENDED;
static { BOPOMOFO_EXTENDED = null; }

/**
 */

public static final int BOPOMOFO_EXTENDED_ID = 67; // 0x43

/**
 */

public static final int BOPOMOFO_ID = 64; // 0x40

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock BOX_DRAWING;
static { BOX_DRAWING = null; }

/**
 */

public static final int BOX_DRAWING_ID = 52; // 0x34

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock BRAHMI;
static { BRAHMI = null; }

/***/

public static final int BRAHMI_ID = 201; // 0xc9

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock BRAILLE_PATTERNS;
static { BRAILLE_PATTERNS = null; }

/**
 */

public static final int BRAILLE_PATTERNS_ID = 57; // 0x39

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock BUGINESE;
static { BUGINESE = null; }

/**
 */

public static final int BUGINESE_ID = 129; // 0x81

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock BUHID;
static { BUHID = null; }

/**
 */

public static final int BUHID_ID = 100; // 0x64

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock BYZANTINE_MUSICAL_SYMBOLS;
static { BYZANTINE_MUSICAL_SYMBOLS = null; }

/**
 */

public static final int BYZANTINE_MUSICAL_SYMBOLS_ID = 91; // 0x5b

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock CARIAN;
static { CARIAN = null; }

/**
 */

public static final int CARIAN_ID = 168; // 0xa8

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock CAUCASIAN_ALBANIAN;
static { CAUCASIAN_ALBANIAN = null; }

/***/

public static final int CAUCASIAN_ALBANIAN_ID = 222; // 0xde

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock CHAKMA;
static { CHAKMA = null; }

/***/

public static final int CHAKMA_ID = 212; // 0xd4

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock CHAM;
static { CHAM = null; }

/**
 */

public static final int CHAM_ID = 164; // 0xa4

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock CHEROKEE;
static { CHEROKEE = null; }

/**
 */

public static final int CHEROKEE_ID = 32; // 0x20

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock CHEROKEE_SUPPLEMENT;
static { CHEROKEE_SUPPLEMENT = null; }

/***/

public static final int CHEROKEE_SUPPLEMENT_ID = 255; // 0xff

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock CHESS_SYMBOLS;
static { CHESS_SYMBOLS = null; }

/***/

public static final int CHESS_SYMBOLS_ID = 281; // 0x119

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock CHORASMIAN;
static { CHORASMIAN = null; }

/***/

public static final int CHORASMIAN_ID = 301; // 0x12d

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock CJK_COMPATIBILITY;
static { CJK_COMPATIBILITY = null; }

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock CJK_COMPATIBILITY_FORMS;
static { CJK_COMPATIBILITY_FORMS = null; }

/**
 */

public static final int CJK_COMPATIBILITY_FORMS_ID = 83; // 0x53

/**
 */

public static final int CJK_COMPATIBILITY_ID = 69; // 0x45

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock CJK_COMPATIBILITY_IDEOGRAPHS;
static { CJK_COMPATIBILITY_IDEOGRAPHS = null; }

/**
 */

public static final int CJK_COMPATIBILITY_IDEOGRAPHS_ID = 79; // 0x4f

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT;
static { CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT = null; }

/**
 */

public static final int CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT_ID = 95; // 0x5f

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock CJK_RADICALS_SUPPLEMENT;
static { CJK_RADICALS_SUPPLEMENT = null; }

/**
 */

public static final int CJK_RADICALS_SUPPLEMENT_ID = 58; // 0x3a

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock CJK_STROKES;
static { CJK_STROKES = null; }

/**
 */

public static final int CJK_STROKES_ID = 130; // 0x82

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock CJK_SYMBOLS_AND_PUNCTUATION;
static { CJK_SYMBOLS_AND_PUNCTUATION = null; }

/**
 */

public static final int CJK_SYMBOLS_AND_PUNCTUATION_ID = 61; // 0x3d

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock CJK_UNIFIED_IDEOGRAPHS;
static { CJK_UNIFIED_IDEOGRAPHS = null; }

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A;
static { CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A = null; }

/**
 */

public static final int CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A_ID = 70; // 0x46

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B;
static { CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B = null; }

/**
 */

public static final int CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B_ID = 94; // 0x5e

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C;
static { CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C = null; }

/***/

public static final int CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C_ID = 197; // 0xc5

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D;
static { CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D = null; }

/***/

public static final int CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D_ID = 209; // 0xd1

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock CJK_UNIFIED_IDEOGRAPHS_EXTENSION_E;
static { CJK_UNIFIED_IDEOGRAPHS_EXTENSION_E = null; }

/***/

public static final int CJK_UNIFIED_IDEOGRAPHS_EXTENSION_E_ID = 256; // 0x100

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock CJK_UNIFIED_IDEOGRAPHS_EXTENSION_F;
static { CJK_UNIFIED_IDEOGRAPHS_EXTENSION_F = null; }

/***/

public static final int CJK_UNIFIED_IDEOGRAPHS_EXTENSION_F_ID = 274; // 0x112

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock CJK_UNIFIED_IDEOGRAPHS_EXTENSION_G;
static { CJK_UNIFIED_IDEOGRAPHS_EXTENSION_G = null; }

/***/

public static final int CJK_UNIFIED_IDEOGRAPHS_EXTENSION_G_ID = 302; // 0x12e

/**
 */

public static final int CJK_UNIFIED_IDEOGRAPHS_ID = 71; // 0x47

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock COMBINING_DIACRITICAL_MARKS;
static { COMBINING_DIACRITICAL_MARKS = null; }

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock COMBINING_DIACRITICAL_MARKS_EXTENDED;
static { COMBINING_DIACRITICAL_MARKS_EXTENDED = null; }

/***/

public static final int COMBINING_DIACRITICAL_MARKS_EXTENDED_ID = 224; // 0xe0

/**
 */

public static final int COMBINING_DIACRITICAL_MARKS_ID = 7; // 0x7

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock COMBINING_DIACRITICAL_MARKS_SUPPLEMENT;
static { COMBINING_DIACRITICAL_MARKS_SUPPLEMENT = null; }

/**
 */

public static final int COMBINING_DIACRITICAL_MARKS_SUPPLEMENT_ID = 131; // 0x83

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock COMBINING_HALF_MARKS;
static { COMBINING_HALF_MARKS = null; }

/**
 */

public static final int COMBINING_HALF_MARKS_ID = 82; // 0x52

/**
 * Unicode 3.2 renames this block to "Combining Diacritical Marks for
 * Symbols".
 */

public static final android.icu.lang.UCharacter.UnicodeBlock COMBINING_MARKS_FOR_SYMBOLS;
static { COMBINING_MARKS_FOR_SYMBOLS = null; }

/**
 * Unicode 3.2 renames this block to "Combining Diacritical Marks for
 * Symbols".
 */

public static final int COMBINING_MARKS_FOR_SYMBOLS_ID = 43; // 0x2b

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock COMMON_INDIC_NUMBER_FORMS;
static { COMMON_INDIC_NUMBER_FORMS = null; }

/***/

public static final int COMMON_INDIC_NUMBER_FORMS_ID = 178; // 0xb2

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock CONTROL_PICTURES;
static { CONTROL_PICTURES = null; }

/**
 */

public static final int CONTROL_PICTURES_ID = 49; // 0x31

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock COPTIC;
static { COPTIC = null; }

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock COPTIC_EPACT_NUMBERS;
static { COPTIC_EPACT_NUMBERS = null; }

/***/

public static final int COPTIC_EPACT_NUMBERS_ID = 223; // 0xdf

/**
 */

public static final int COPTIC_ID = 132; // 0x84

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock COUNTING_ROD_NUMERALS;
static { COUNTING_ROD_NUMERALS = null; }

/**
 */

public static final int COUNTING_ROD_NUMERALS_ID = 154; // 0x9a

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock CUNEIFORM;
static { CUNEIFORM = null; }

/**
 */

public static final int CUNEIFORM_ID = 152; // 0x98

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock CUNEIFORM_NUMBERS_AND_PUNCTUATION;
static { CUNEIFORM_NUMBERS_AND_PUNCTUATION = null; }

/**
 */

public static final int CUNEIFORM_NUMBERS_AND_PUNCTUATION_ID = 153; // 0x99

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock CURRENCY_SYMBOLS;
static { CURRENCY_SYMBOLS = null; }

/**
 */

public static final int CURRENCY_SYMBOLS_ID = 42; // 0x2a

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock CYPRIOT_SYLLABARY;
static { CYPRIOT_SYLLABARY = null; }

/**
 */

public static final int CYPRIOT_SYLLABARY_ID = 123; // 0x7b

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock CYRILLIC;
static { CYRILLIC = null; }

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock CYRILLIC_EXTENDED_A;
static { CYRILLIC_EXTENDED_A = null; }

/**
 */

public static final int CYRILLIC_EXTENDED_A_ID = 158; // 0x9e

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock CYRILLIC_EXTENDED_B;
static { CYRILLIC_EXTENDED_B = null; }

/**
 */

public static final int CYRILLIC_EXTENDED_B_ID = 160; // 0xa0

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock CYRILLIC_EXTENDED_C;
static { CYRILLIC_EXTENDED_C = null; }

/***/

public static final int CYRILLIC_EXTENDED_C_ID = 265; // 0x109

/**
 */

public static final int CYRILLIC_ID = 9; // 0x9

/**
 * Unicode 4.0.1 renames the "Cyrillic Supplementary" block to "Cyrillic Supplement".
 */

public static final android.icu.lang.UCharacter.UnicodeBlock CYRILLIC_SUPPLEMENT;
static { CYRILLIC_SUPPLEMENT = null; }

/**
 * Unicode 4.0.1 renames the "Cyrillic Supplementary" block to "Cyrillic Supplement".
 */

public static final android.icu.lang.UCharacter.UnicodeBlock CYRILLIC_SUPPLEMENTARY;
static { CYRILLIC_SUPPLEMENTARY = null; }

/**
 * Unicode 4.0.1 renames the "Cyrillic Supplementary" block to "Cyrillic Supplement".
 */

public static final int CYRILLIC_SUPPLEMENTARY_ID = 97; // 0x61

/**
 * Unicode 4.0.1 renames the "Cyrillic Supplementary" block to "Cyrillic Supplement".
 */

public static final int CYRILLIC_SUPPLEMENT_ID = 97; // 0x61

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock DESERET;
static { DESERET = null; }

/**
 */

public static final int DESERET_ID = 90; // 0x5a

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock DEVANAGARI;
static { DEVANAGARI = null; }

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock DEVANAGARI_EXTENDED;
static { DEVANAGARI_EXTENDED = null; }

/***/

public static final int DEVANAGARI_EXTENDED_ID = 179; // 0xb3

/**
 */

public static final int DEVANAGARI_ID = 15; // 0xf

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock DINGBATS;
static { DINGBATS = null; }

/**
 */

public static final int DINGBATS_ID = 56; // 0x38

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock DIVES_AKURU;
static { DIVES_AKURU = null; }

/***/

public static final int DIVES_AKURU_ID = 303; // 0x12f

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock DOGRA;
static { DOGRA = null; }

/***/

public static final int DOGRA_ID = 282; // 0x11a

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock DOMINO_TILES;
static { DOMINO_TILES = null; }

/**
 */

public static final int DOMINO_TILES_ID = 171; // 0xab

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock DUPLOYAN;
static { DUPLOYAN = null; }

/***/

public static final int DUPLOYAN_ID = 225; // 0xe1

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock EARLY_DYNASTIC_CUNEIFORM;
static { EARLY_DYNASTIC_CUNEIFORM = null; }

/***/

public static final int EARLY_DYNASTIC_CUNEIFORM_ID = 257; // 0x101

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock EGYPTIAN_HIEROGLYPHS;
static { EGYPTIAN_HIEROGLYPHS = null; }

/***/

public static final int EGYPTIAN_HIEROGLYPHS_ID = 194; // 0xc2

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock EGYPTIAN_HIEROGLYPH_FORMAT_CONTROLS;
static { EGYPTIAN_HIEROGLYPH_FORMAT_CONTROLS = null; }

/***/

public static final int EGYPTIAN_HIEROGLYPH_FORMAT_CONTROLS_ID = 292; // 0x124

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock ELBASAN;
static { ELBASAN = null; }

/***/

public static final int ELBASAN_ID = 226; // 0xe2

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock ELYMAIC;
static { ELYMAIC = null; }

/***/

public static final int ELYMAIC_ID = 293; // 0x125

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock EMOTICONS;
static { EMOTICONS = null; }

/***/

public static final int EMOTICONS_ID = 206; // 0xce

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock ENCLOSED_ALPHANUMERICS;
static { ENCLOSED_ALPHANUMERICS = null; }

/**
 */

public static final int ENCLOSED_ALPHANUMERICS_ID = 51; // 0x33

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock ENCLOSED_ALPHANUMERIC_SUPPLEMENT;
static { ENCLOSED_ALPHANUMERIC_SUPPLEMENT = null; }

/***/

public static final int ENCLOSED_ALPHANUMERIC_SUPPLEMENT_ID = 195; // 0xc3

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock ENCLOSED_CJK_LETTERS_AND_MONTHS;
static { ENCLOSED_CJK_LETTERS_AND_MONTHS = null; }

/**
 */

public static final int ENCLOSED_CJK_LETTERS_AND_MONTHS_ID = 68; // 0x44

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock ENCLOSED_IDEOGRAPHIC_SUPPLEMENT;
static { ENCLOSED_IDEOGRAPHIC_SUPPLEMENT = null; }

/***/

public static final int ENCLOSED_IDEOGRAPHIC_SUPPLEMENT_ID = 196; // 0xc4

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock ETHIOPIC;
static { ETHIOPIC = null; }

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock ETHIOPIC_EXTENDED;
static { ETHIOPIC_EXTENDED = null; }

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock ETHIOPIC_EXTENDED_A;
static { ETHIOPIC_EXTENDED_A = null; }

/***/

public static final int ETHIOPIC_EXTENDED_A_ID = 200; // 0xc8

/**
 */

public static final int ETHIOPIC_EXTENDED_ID = 133; // 0x85

/**
 */

public static final int ETHIOPIC_ID = 31; // 0x1f

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock ETHIOPIC_SUPPLEMENT;
static { ETHIOPIC_SUPPLEMENT = null; }

/**
 */

public static final int ETHIOPIC_SUPPLEMENT_ID = 134; // 0x86

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock GENERAL_PUNCTUATION;
static { GENERAL_PUNCTUATION = null; }

/**
 */

public static final int GENERAL_PUNCTUATION_ID = 40; // 0x28

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock GEOMETRIC_SHAPES;
static { GEOMETRIC_SHAPES = null; }

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock GEOMETRIC_SHAPES_EXTENDED;
static { GEOMETRIC_SHAPES_EXTENDED = null; }

/***/

public static final int GEOMETRIC_SHAPES_EXTENDED_ID = 227; // 0xe3

/**
 */

public static final int GEOMETRIC_SHAPES_ID = 54; // 0x36

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock GEORGIAN;
static { GEORGIAN = null; }

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock GEORGIAN_EXTENDED;
static { GEORGIAN_EXTENDED = null; }

/***/

public static final int GEORGIAN_EXTENDED_ID = 283; // 0x11b

/**
 */

public static final int GEORGIAN_ID = 29; // 0x1d

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock GEORGIAN_SUPPLEMENT;
static { GEORGIAN_SUPPLEMENT = null; }

/**
 */

public static final int GEORGIAN_SUPPLEMENT_ID = 135; // 0x87

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock GLAGOLITIC;
static { GLAGOLITIC = null; }

/**
 */

public static final int GLAGOLITIC_ID = 136; // 0x88

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock GLAGOLITIC_SUPPLEMENT;
static { GLAGOLITIC_SUPPLEMENT = null; }

/***/

public static final int GLAGOLITIC_SUPPLEMENT_ID = 266; // 0x10a

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock GOTHIC;
static { GOTHIC = null; }

/**
 */

public static final int GOTHIC_ID = 89; // 0x59

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock GRANTHA;
static { GRANTHA = null; }

/***/

public static final int GRANTHA_ID = 228; // 0xe4

/**
 * Unicode 3.2 renames this block to "Greek and Coptic".
 */

public static final android.icu.lang.UCharacter.UnicodeBlock GREEK;
static { GREEK = null; }

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock GREEK_EXTENDED;
static { GREEK_EXTENDED = null; }

/**
 */

public static final int GREEK_EXTENDED_ID = 39; // 0x27

/**
 * Unicode 3.2 renames this block to "Greek and Coptic".
 */

public static final int GREEK_ID = 8; // 0x8

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock GUJARATI;
static { GUJARATI = null; }

/**
 */

public static final int GUJARATI_ID = 18; // 0x12

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock GUNJALA_GONDI;
static { GUNJALA_GONDI = null; }

/***/

public static final int GUNJALA_GONDI_ID = 284; // 0x11c

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock GURMUKHI;
static { GURMUKHI = null; }

/**
 */

public static final int GURMUKHI_ID = 17; // 0x11

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock HALFWIDTH_AND_FULLWIDTH_FORMS;
static { HALFWIDTH_AND_FULLWIDTH_FORMS = null; }

/**
 */

public static final int HALFWIDTH_AND_FULLWIDTH_FORMS_ID = 87; // 0x57

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock HANGUL_COMPATIBILITY_JAMO;
static { HANGUL_COMPATIBILITY_JAMO = null; }

/**
 */

public static final int HANGUL_COMPATIBILITY_JAMO_ID = 65; // 0x41

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock HANGUL_JAMO;
static { HANGUL_JAMO = null; }

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock HANGUL_JAMO_EXTENDED_A;
static { HANGUL_JAMO_EXTENDED_A = null; }

/***/

public static final int HANGUL_JAMO_EXTENDED_A_ID = 180; // 0xb4

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock HANGUL_JAMO_EXTENDED_B;
static { HANGUL_JAMO_EXTENDED_B = null; }

/***/

public static final int HANGUL_JAMO_EXTENDED_B_ID = 185; // 0xb9

/**
 */

public static final int HANGUL_JAMO_ID = 30; // 0x1e

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock HANGUL_SYLLABLES;
static { HANGUL_SYLLABLES = null; }

/**
 */

public static final int HANGUL_SYLLABLES_ID = 74; // 0x4a

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock HANIFI_ROHINGYA;
static { HANIFI_ROHINGYA = null; }

/***/

public static final int HANIFI_ROHINGYA_ID = 285; // 0x11d

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock HANUNOO;
static { HANUNOO = null; }

/**
 */

public static final int HANUNOO_ID = 99; // 0x63

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock HATRAN;
static { HATRAN = null; }

/***/

public static final int HATRAN_ID = 258; // 0x102

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock HEBREW;
static { HEBREW = null; }

/**
 */

public static final int HEBREW_ID = 11; // 0xb

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock HIGH_PRIVATE_USE_SURROGATES;
static { HIGH_PRIVATE_USE_SURROGATES = null; }

/**
 */

public static final int HIGH_PRIVATE_USE_SURROGATES_ID = 76; // 0x4c

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock HIGH_SURROGATES;
static { HIGH_SURROGATES = null; }

/**
 */

public static final int HIGH_SURROGATES_ID = 75; // 0x4b

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock HIRAGANA;
static { HIRAGANA = null; }

/**
 */

public static final int HIRAGANA_ID = 62; // 0x3e

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock IDEOGRAPHIC_DESCRIPTION_CHARACTERS;
static { IDEOGRAPHIC_DESCRIPTION_CHARACTERS = null; }

/**
 */

public static final int IDEOGRAPHIC_DESCRIPTION_CHARACTERS_ID = 60; // 0x3c

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock IDEOGRAPHIC_SYMBOLS_AND_PUNCTUATION;
static { IDEOGRAPHIC_SYMBOLS_AND_PUNCTUATION = null; }

/***/

public static final int IDEOGRAPHIC_SYMBOLS_AND_PUNCTUATION_ID = 267; // 0x10b

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock IMPERIAL_ARAMAIC;
static { IMPERIAL_ARAMAIC = null; }

/***/

public static final int IMPERIAL_ARAMAIC_ID = 186; // 0xba

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock INDIC_SIYAQ_NUMBERS;
static { INDIC_SIYAQ_NUMBERS = null; }

/***/

public static final int INDIC_SIYAQ_NUMBERS_ID = 286; // 0x11e

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock INSCRIPTIONAL_PAHLAVI;
static { INSCRIPTIONAL_PAHLAVI = null; }

/***/

public static final int INSCRIPTIONAL_PAHLAVI_ID = 190; // 0xbe

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock INSCRIPTIONAL_PARTHIAN;
static { INSCRIPTIONAL_PARTHIAN = null; }

/***/

public static final int INSCRIPTIONAL_PARTHIAN_ID = 189; // 0xbd

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock INVALID_CODE;
static { INVALID_CODE = null; }

/**
 */

public static final int INVALID_CODE_ID = -1; // 0xffffffff

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock IPA_EXTENSIONS;
static { IPA_EXTENSIONS = null; }

/**
 */

public static final int IPA_EXTENSIONS_ID = 5; // 0x5

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock JAVANESE;
static { JAVANESE = null; }

/***/

public static final int JAVANESE_ID = 181; // 0xb5

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock KAITHI;
static { KAITHI = null; }

/***/

public static final int KAITHI_ID = 193; // 0xc1

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock KANA_EXTENDED_A;
static { KANA_EXTENDED_A = null; }

/***/

public static final int KANA_EXTENDED_A_ID = 275; // 0x113

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock KANA_SUPPLEMENT;
static { KANA_SUPPLEMENT = null; }

/***/

public static final int KANA_SUPPLEMENT_ID = 203; // 0xcb

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock KANBUN;
static { KANBUN = null; }

/**
 */

public static final int KANBUN_ID = 66; // 0x42

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock KANGXI_RADICALS;
static { KANGXI_RADICALS = null; }

/**
 */

public static final int KANGXI_RADICALS_ID = 59; // 0x3b

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock KANNADA;
static { KANNADA = null; }

/**
 */

public static final int KANNADA_ID = 22; // 0x16

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock KATAKANA;
static { KATAKANA = null; }

/**
 */

public static final int KATAKANA_ID = 63; // 0x3f

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock KATAKANA_PHONETIC_EXTENSIONS;
static { KATAKANA_PHONETIC_EXTENSIONS = null; }

/**
 */

public static final int KATAKANA_PHONETIC_EXTENSIONS_ID = 107; // 0x6b

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock KAYAH_LI;
static { KAYAH_LI = null; }

/**
 */

public static final int KAYAH_LI_ID = 162; // 0xa2

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock KHAROSHTHI;
static { KHAROSHTHI = null; }

/**
 */

public static final int KHAROSHTHI_ID = 137; // 0x89

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock KHITAN_SMALL_SCRIPT;
static { KHITAN_SMALL_SCRIPT = null; }

/***/

public static final int KHITAN_SMALL_SCRIPT_ID = 304; // 0x130

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock KHMER;
static { KHMER = null; }

/**
 */

public static final int KHMER_ID = 36; // 0x24

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock KHMER_SYMBOLS;
static { KHMER_SYMBOLS = null; }

/**
 */

public static final int KHMER_SYMBOLS_ID = 113; // 0x71

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock KHOJKI;
static { KHOJKI = null; }

/***/

public static final int KHOJKI_ID = 229; // 0xe5

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock KHUDAWADI;
static { KHUDAWADI = null; }

/***/

public static final int KHUDAWADI_ID = 230; // 0xe6

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock LAO;
static { LAO = null; }

/**
 */

public static final int LAO_ID = 26; // 0x1a

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock LATIN_1_SUPPLEMENT;
static { LATIN_1_SUPPLEMENT = null; }

/**
 */

public static final int LATIN_1_SUPPLEMENT_ID = 2; // 0x2

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock LATIN_EXTENDED_A;
static { LATIN_EXTENDED_A = null; }

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock LATIN_EXTENDED_ADDITIONAL;
static { LATIN_EXTENDED_ADDITIONAL = null; }

/**
 */

public static final int LATIN_EXTENDED_ADDITIONAL_ID = 38; // 0x26

/**
 */

public static final int LATIN_EXTENDED_A_ID = 3; // 0x3

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock LATIN_EXTENDED_B;
static { LATIN_EXTENDED_B = null; }

/**
 */

public static final int LATIN_EXTENDED_B_ID = 4; // 0x4

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock LATIN_EXTENDED_C;
static { LATIN_EXTENDED_C = null; }

/**
 */

public static final int LATIN_EXTENDED_C_ID = 148; // 0x94

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock LATIN_EXTENDED_D;
static { LATIN_EXTENDED_D = null; }

/**
 */

public static final int LATIN_EXTENDED_D_ID = 149; // 0x95

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock LATIN_EXTENDED_E;
static { LATIN_EXTENDED_E = null; }

/***/

public static final int LATIN_EXTENDED_E_ID = 231; // 0xe7

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock LEPCHA;
static { LEPCHA = null; }

/**
 */

public static final int LEPCHA_ID = 156; // 0x9c

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock LETTERLIKE_SYMBOLS;
static { LETTERLIKE_SYMBOLS = null; }

/**
 */

public static final int LETTERLIKE_SYMBOLS_ID = 44; // 0x2c

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock LIMBU;
static { LIMBU = null; }

/**
 */

public static final int LIMBU_ID = 111; // 0x6f

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock LINEAR_A;
static { LINEAR_A = null; }

/***/

public static final int LINEAR_A_ID = 232; // 0xe8

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock LINEAR_B_IDEOGRAMS;
static { LINEAR_B_IDEOGRAMS = null; }

/**
 */

public static final int LINEAR_B_IDEOGRAMS_ID = 118; // 0x76

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock LINEAR_B_SYLLABARY;
static { LINEAR_B_SYLLABARY = null; }

/**
 */

public static final int LINEAR_B_SYLLABARY_ID = 117; // 0x75

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock LISU;
static { LISU = null; }

/***/

public static final int LISU_ID = 176; // 0xb0

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock LISU_SUPPLEMENT;
static { LISU_SUPPLEMENT = null; }

/***/

public static final int LISU_SUPPLEMENT_ID = 305; // 0x131

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock LOW_SURROGATES;
static { LOW_SURROGATES = null; }

/**
 */

public static final int LOW_SURROGATES_ID = 77; // 0x4d

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock LYCIAN;
static { LYCIAN = null; }

/**
 */

public static final int LYCIAN_ID = 167; // 0xa7

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock LYDIAN;
static { LYDIAN = null; }

/**
 */

public static final int LYDIAN_ID = 169; // 0xa9

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock MAHAJANI;
static { MAHAJANI = null; }

/***/

public static final int MAHAJANI_ID = 233; // 0xe9

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock MAHJONG_TILES;
static { MAHJONG_TILES = null; }

/**
 */

public static final int MAHJONG_TILES_ID = 170; // 0xaa

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock MAKASAR;
static { MAKASAR = null; }

/***/

public static final int MAKASAR_ID = 287; // 0x11f

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock MALAYALAM;
static { MALAYALAM = null; }

/**
 */

public static final int MALAYALAM_ID = 23; // 0x17

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock MANDAIC;
static { MANDAIC = null; }

/***/

public static final int MANDAIC_ID = 198; // 0xc6

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock MANICHAEAN;
static { MANICHAEAN = null; }

/***/

public static final int MANICHAEAN_ID = 234; // 0xea

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock MARCHEN;
static { MARCHEN = null; }

/***/

public static final int MARCHEN_ID = 268; // 0x10c

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock MASARAM_GONDI;
static { MASARAM_GONDI = null; }

/***/

public static final int MASARAM_GONDI_ID = 276; // 0x114

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock MATHEMATICAL_ALPHANUMERIC_SYMBOLS;
static { MATHEMATICAL_ALPHANUMERIC_SYMBOLS = null; }

/**
 */

public static final int MATHEMATICAL_ALPHANUMERIC_SYMBOLS_ID = 93; // 0x5d

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock MATHEMATICAL_OPERATORS;
static { MATHEMATICAL_OPERATORS = null; }

/**
 */

public static final int MATHEMATICAL_OPERATORS_ID = 47; // 0x2f

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock MAYAN_NUMERALS;
static { MAYAN_NUMERALS = null; }

/***/

public static final int MAYAN_NUMERALS_ID = 288; // 0x120

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock MEDEFAIDRIN;
static { MEDEFAIDRIN = null; }

/***/

public static final int MEDEFAIDRIN_ID = 289; // 0x121

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock MEETEI_MAYEK;
static { MEETEI_MAYEK = null; }

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock MEETEI_MAYEK_EXTENSIONS;
static { MEETEI_MAYEK_EXTENSIONS = null; }

/***/

public static final int MEETEI_MAYEK_EXTENSIONS_ID = 213; // 0xd5

/***/

public static final int MEETEI_MAYEK_ID = 184; // 0xb8

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock MENDE_KIKAKUI;
static { MENDE_KIKAKUI = null; }

/***/

public static final int MENDE_KIKAKUI_ID = 235; // 0xeb

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock MEROITIC_CURSIVE;
static { MEROITIC_CURSIVE = null; }

/***/

public static final int MEROITIC_CURSIVE_ID = 214; // 0xd6

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock MEROITIC_HIEROGLYPHS;
static { MEROITIC_HIEROGLYPHS = null; }

/***/

public static final int MEROITIC_HIEROGLYPHS_ID = 215; // 0xd7

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock MIAO;
static { MIAO = null; }

/***/

public static final int MIAO_ID = 216; // 0xd8

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock MISCELLANEOUS_MATHEMATICAL_SYMBOLS_A;
static { MISCELLANEOUS_MATHEMATICAL_SYMBOLS_A = null; }

/**
 */

public static final int MISCELLANEOUS_MATHEMATICAL_SYMBOLS_A_ID = 102; // 0x66

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock MISCELLANEOUS_MATHEMATICAL_SYMBOLS_B;
static { MISCELLANEOUS_MATHEMATICAL_SYMBOLS_B = null; }

/**
 */

public static final int MISCELLANEOUS_MATHEMATICAL_SYMBOLS_B_ID = 105; // 0x69

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock MISCELLANEOUS_SYMBOLS;
static { MISCELLANEOUS_SYMBOLS = null; }

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock MISCELLANEOUS_SYMBOLS_AND_ARROWS;
static { MISCELLANEOUS_SYMBOLS_AND_ARROWS = null; }

/**
 */

public static final int MISCELLANEOUS_SYMBOLS_AND_ARROWS_ID = 115; // 0x73

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS;
static { MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS = null; }

/***/

public static final int MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS_ID = 205; // 0xcd

/**
 */

public static final int MISCELLANEOUS_SYMBOLS_ID = 55; // 0x37

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock MISCELLANEOUS_TECHNICAL;
static { MISCELLANEOUS_TECHNICAL = null; }

/**
 */

public static final int MISCELLANEOUS_TECHNICAL_ID = 48; // 0x30

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock MODI;
static { MODI = null; }

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock MODIFIER_TONE_LETTERS;
static { MODIFIER_TONE_LETTERS = null; }

/**
 */

public static final int MODIFIER_TONE_LETTERS_ID = 138; // 0x8a

/***/

public static final int MODI_ID = 236; // 0xec

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock MONGOLIAN;
static { MONGOLIAN = null; }

/**
 */

public static final int MONGOLIAN_ID = 37; // 0x25

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock MONGOLIAN_SUPPLEMENT;
static { MONGOLIAN_SUPPLEMENT = null; }

/***/

public static final int MONGOLIAN_SUPPLEMENT_ID = 269; // 0x10d

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock MRO;
static { MRO = null; }

/***/

public static final int MRO_ID = 237; // 0xed

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock MULTANI;
static { MULTANI = null; }

/***/

public static final int MULTANI_ID = 259; // 0x103

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock MUSICAL_SYMBOLS;
static { MUSICAL_SYMBOLS = null; }

/**
 */

public static final int MUSICAL_SYMBOLS_ID = 92; // 0x5c

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock MYANMAR;
static { MYANMAR = null; }

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock MYANMAR_EXTENDED_A;
static { MYANMAR_EXTENDED_A = null; }

/***/

public static final int MYANMAR_EXTENDED_A_ID = 182; // 0xb6

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock MYANMAR_EXTENDED_B;
static { MYANMAR_EXTENDED_B = null; }

/***/

public static final int MYANMAR_EXTENDED_B_ID = 238; // 0xee

/**
 */

public static final int MYANMAR_ID = 28; // 0x1c

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock NABATAEAN;
static { NABATAEAN = null; }

/***/

public static final int NABATAEAN_ID = 239; // 0xef

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock NANDINAGARI;
static { NANDINAGARI = null; }

/***/

public static final int NANDINAGARI_ID = 294; // 0x126

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock NEWA;
static { NEWA = null; }

/***/

public static final int NEWA_ID = 270; // 0x10e

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock NEW_TAI_LUE;
static { NEW_TAI_LUE = null; }

/**
 */

public static final int NEW_TAI_LUE_ID = 139; // 0x8b

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock NKO;
static { NKO = null; }

/**
 */

public static final int NKO_ID = 146; // 0x92

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock NO_BLOCK;
static { NO_BLOCK = null; }

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock NUMBER_FORMS;
static { NUMBER_FORMS = null; }

/**
 */

public static final int NUMBER_FORMS_ID = 45; // 0x2d

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock NUSHU;
static { NUSHU = null; }

/***/

public static final int NUSHU_ID = 277; // 0x115

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock NYIAKENG_PUACHUE_HMONG;
static { NYIAKENG_PUACHUE_HMONG = null; }

/***/

public static final int NYIAKENG_PUACHUE_HMONG_ID = 295; // 0x127

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock OGHAM;
static { OGHAM = null; }

/**
 */

public static final int OGHAM_ID = 34; // 0x22

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock OLD_HUNGARIAN;
static { OLD_HUNGARIAN = null; }

/***/

public static final int OLD_HUNGARIAN_ID = 260; // 0x104

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock OLD_ITALIC;
static { OLD_ITALIC = null; }

/**
 */

public static final int OLD_ITALIC_ID = 88; // 0x58

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock OLD_NORTH_ARABIAN;
static { OLD_NORTH_ARABIAN = null; }

/***/

public static final int OLD_NORTH_ARABIAN_ID = 240; // 0xf0

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock OLD_PERMIC;
static { OLD_PERMIC = null; }

/***/

public static final int OLD_PERMIC_ID = 241; // 0xf1

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock OLD_PERSIAN;
static { OLD_PERSIAN = null; }

/**
 */

public static final int OLD_PERSIAN_ID = 140; // 0x8c

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock OLD_SOGDIAN;
static { OLD_SOGDIAN = null; }

/***/

public static final int OLD_SOGDIAN_ID = 290; // 0x122

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock OLD_SOUTH_ARABIAN;
static { OLD_SOUTH_ARABIAN = null; }

/***/

public static final int OLD_SOUTH_ARABIAN_ID = 187; // 0xbb

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock OLD_TURKIC;
static { OLD_TURKIC = null; }

/***/

public static final int OLD_TURKIC_ID = 191; // 0xbf

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock OL_CHIKI;
static { OL_CHIKI = null; }

/**
 */

public static final int OL_CHIKI_ID = 157; // 0x9d

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock OPTICAL_CHARACTER_RECOGNITION;
static { OPTICAL_CHARACTER_RECOGNITION = null; }

/**
 */

public static final int OPTICAL_CHARACTER_RECOGNITION_ID = 50; // 0x32

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock ORIYA;
static { ORIYA = null; }

/**
 */

public static final int ORIYA_ID = 19; // 0x13

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock ORNAMENTAL_DINGBATS;
static { ORNAMENTAL_DINGBATS = null; }

/***/

public static final int ORNAMENTAL_DINGBATS_ID = 242; // 0xf2

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock OSAGE;
static { OSAGE = null; }

/***/

public static final int OSAGE_ID = 271; // 0x10f

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock OSMANYA;
static { OSMANYA = null; }

/**
 */

public static final int OSMANYA_ID = 122; // 0x7a

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock OTTOMAN_SIYAQ_NUMBERS;
static { OTTOMAN_SIYAQ_NUMBERS = null; }

/***/

public static final int OTTOMAN_SIYAQ_NUMBERS_ID = 296; // 0x128

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock PAHAWH_HMONG;
static { PAHAWH_HMONG = null; }

/***/

public static final int PAHAWH_HMONG_ID = 243; // 0xf3

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock PALMYRENE;
static { PALMYRENE = null; }

/***/

public static final int PALMYRENE_ID = 244; // 0xf4

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock PAU_CIN_HAU;
static { PAU_CIN_HAU = null; }

/***/

public static final int PAU_CIN_HAU_ID = 245; // 0xf5

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock PHAGS_PA;
static { PHAGS_PA = null; }

/**
 */

public static final int PHAGS_PA_ID = 150; // 0x96

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock PHAISTOS_DISC;
static { PHAISTOS_DISC = null; }

/**
 */

public static final int PHAISTOS_DISC_ID = 166; // 0xa6

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock PHOENICIAN;
static { PHOENICIAN = null; }

/**
 */

public static final int PHOENICIAN_ID = 151; // 0x97

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock PHONETIC_EXTENSIONS;
static { PHONETIC_EXTENSIONS = null; }

/**
 */

public static final int PHONETIC_EXTENSIONS_ID = 114; // 0x72

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock PHONETIC_EXTENSIONS_SUPPLEMENT;
static { PHONETIC_EXTENSIONS_SUPPLEMENT = null; }

/**
 */

public static final int PHONETIC_EXTENSIONS_SUPPLEMENT_ID = 141; // 0x8d

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock PLAYING_CARDS;
static { PLAYING_CARDS = null; }

/***/

public static final int PLAYING_CARDS_ID = 204; // 0xcc

/**
 * Same as public static final int PRIVATE_USE_AREA.
 * Until Unicode 3.1.1; the corresponding block name was "Private Use";
 * and multiple code point ranges had this block.
 * Unicode 3.2 renames the block for the BMP PUA to "Private Use Area"
 * and adds separate blocks for the supplementary PUAs.
 */

public static final android.icu.lang.UCharacter.UnicodeBlock PRIVATE_USE;
static { PRIVATE_USE = null; }

/**
 * Same as public static final int PRIVATE_USE.
 * Until Unicode 3.1.1; the corresponding block name was "Private Use";
 * and multiple code point ranges had this block.
 * Unicode 3.2 renames the block for the BMP PUA to "Private Use Area"
 * and adds separate blocks for the supplementary PUAs.
 */

public static final android.icu.lang.UCharacter.UnicodeBlock PRIVATE_USE_AREA;
static { PRIVATE_USE_AREA = null; }

/**
 * Same as public static final int PRIVATE_USE.
 * Until Unicode 3.1.1; the corresponding block name was "Private Use";
 * and multiple code point ranges had this block.
 * Unicode 3.2 renames the block for the BMP PUA to "Private Use Area"
 * and adds separate blocks for the supplementary PUAs.
 */

public static final int PRIVATE_USE_AREA_ID = 78; // 0x4e

/**
 * Same as public static final int PRIVATE_USE_AREA.
 * Until Unicode 3.1.1; the corresponding block name was "Private Use";
 * and multiple code point ranges had this block.
 * Unicode 3.2 renames the block for the BMP PUA to "Private Use Area"
 * and adds separate blocks for the supplementary PUAs.
 */

public static final int PRIVATE_USE_ID = 78; // 0x4e

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock PSALTER_PAHLAVI;
static { PSALTER_PAHLAVI = null; }

/***/

public static final int PSALTER_PAHLAVI_ID = 246; // 0xf6

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock REJANG;
static { REJANG = null; }

/**
 */

public static final int REJANG_ID = 163; // 0xa3

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock RUMI_NUMERAL_SYMBOLS;
static { RUMI_NUMERAL_SYMBOLS = null; }

/***/

public static final int RUMI_NUMERAL_SYMBOLS_ID = 192; // 0xc0

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock RUNIC;
static { RUNIC = null; }

/**
 */

public static final int RUNIC_ID = 35; // 0x23

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock SAMARITAN;
static { SAMARITAN = null; }

/***/

public static final int SAMARITAN_ID = 172; // 0xac

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock SAURASHTRA;
static { SAURASHTRA = null; }

/**
 */

public static final int SAURASHTRA_ID = 161; // 0xa1

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock SHARADA;
static { SHARADA = null; }

/***/

public static final int SHARADA_ID = 217; // 0xd9

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock SHAVIAN;
static { SHAVIAN = null; }

/**
 */

public static final int SHAVIAN_ID = 121; // 0x79

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock SHORTHAND_FORMAT_CONTROLS;
static { SHORTHAND_FORMAT_CONTROLS = null; }

/***/

public static final int SHORTHAND_FORMAT_CONTROLS_ID = 247; // 0xf7

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock SIDDHAM;
static { SIDDHAM = null; }

/***/

public static final int SIDDHAM_ID = 248; // 0xf8

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock SINHALA;
static { SINHALA = null; }

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock SINHALA_ARCHAIC_NUMBERS;
static { SINHALA_ARCHAIC_NUMBERS = null; }

/***/

public static final int SINHALA_ARCHAIC_NUMBERS_ID = 249; // 0xf9

/**
 */

public static final int SINHALA_ID = 24; // 0x18

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock SMALL_FORM_VARIANTS;
static { SMALL_FORM_VARIANTS = null; }

/**
 */

public static final int SMALL_FORM_VARIANTS_ID = 84; // 0x54

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock SMALL_KANA_EXTENSION;
static { SMALL_KANA_EXTENSION = null; }

/***/

public static final int SMALL_KANA_EXTENSION_ID = 297; // 0x129

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock SOGDIAN;
static { SOGDIAN = null; }

/***/

public static final int SOGDIAN_ID = 291; // 0x123

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock SORA_SOMPENG;
static { SORA_SOMPENG = null; }

/***/

public static final int SORA_SOMPENG_ID = 218; // 0xda

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock SOYOMBO;
static { SOYOMBO = null; }

/***/

public static final int SOYOMBO_ID = 278; // 0x116

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock SPACING_MODIFIER_LETTERS;
static { SPACING_MODIFIER_LETTERS = null; }

/**
 */

public static final int SPACING_MODIFIER_LETTERS_ID = 6; // 0x6

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock SPECIALS;
static { SPECIALS = null; }

/**
 */

public static final int SPECIALS_ID = 86; // 0x56

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock SUNDANESE;
static { SUNDANESE = null; }

/**
 */

public static final int SUNDANESE_ID = 155; // 0x9b

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock SUNDANESE_SUPPLEMENT;
static { SUNDANESE_SUPPLEMENT = null; }

/***/

public static final int SUNDANESE_SUPPLEMENT_ID = 219; // 0xdb

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock SUPERSCRIPTS_AND_SUBSCRIPTS;
static { SUPERSCRIPTS_AND_SUBSCRIPTS = null; }

/**
 */

public static final int SUPERSCRIPTS_AND_SUBSCRIPTS_ID = 41; // 0x29

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock SUPPLEMENTAL_ARROWS_A;
static { SUPPLEMENTAL_ARROWS_A = null; }

/**
 */

public static final int SUPPLEMENTAL_ARROWS_A_ID = 103; // 0x67

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock SUPPLEMENTAL_ARROWS_B;
static { SUPPLEMENTAL_ARROWS_B = null; }

/**
 */

public static final int SUPPLEMENTAL_ARROWS_B_ID = 104; // 0x68

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock SUPPLEMENTAL_ARROWS_C;
static { SUPPLEMENTAL_ARROWS_C = null; }

/***/

public static final int SUPPLEMENTAL_ARROWS_C_ID = 250; // 0xfa

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock SUPPLEMENTAL_MATHEMATICAL_OPERATORS;
static { SUPPLEMENTAL_MATHEMATICAL_OPERATORS = null; }

/**
 */

public static final int SUPPLEMENTAL_MATHEMATICAL_OPERATORS_ID = 106; // 0x6a

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock SUPPLEMENTAL_PUNCTUATION;
static { SUPPLEMENTAL_PUNCTUATION = null; }

/**
 */

public static final int SUPPLEMENTAL_PUNCTUATION_ID = 142; // 0x8e

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock SUPPLEMENTAL_SYMBOLS_AND_PICTOGRAPHS;
static { SUPPLEMENTAL_SYMBOLS_AND_PICTOGRAPHS = null; }

/***/

public static final int SUPPLEMENTAL_SYMBOLS_AND_PICTOGRAPHS_ID = 261; // 0x105

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock SUPPLEMENTARY_PRIVATE_USE_AREA_A;
static { SUPPLEMENTARY_PRIVATE_USE_AREA_A = null; }

/**
 */

public static final int SUPPLEMENTARY_PRIVATE_USE_AREA_A_ID = 109; // 0x6d

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock SUPPLEMENTARY_PRIVATE_USE_AREA_B;
static { SUPPLEMENTARY_PRIVATE_USE_AREA_B = null; }

/**
 */

public static final int SUPPLEMENTARY_PRIVATE_USE_AREA_B_ID = 110; // 0x6e

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock SUTTON_SIGNWRITING;
static { SUTTON_SIGNWRITING = null; }

/***/

public static final int SUTTON_SIGNWRITING_ID = 262; // 0x106

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock SYLOTI_NAGRI;
static { SYLOTI_NAGRI = null; }

/**
 */

public static final int SYLOTI_NAGRI_ID = 143; // 0x8f

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock SYMBOLS_AND_PICTOGRAPHS_EXTENDED_A;
static { SYMBOLS_AND_PICTOGRAPHS_EXTENDED_A = null; }

/***/

public static final int SYMBOLS_AND_PICTOGRAPHS_EXTENDED_A_ID = 298; // 0x12a

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock SYMBOLS_FOR_LEGACY_COMPUTING;
static { SYMBOLS_FOR_LEGACY_COMPUTING = null; }

/***/

public static final int SYMBOLS_FOR_LEGACY_COMPUTING_ID = 306; // 0x132

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock SYRIAC;
static { SYRIAC = null; }

/**
 */

public static final int SYRIAC_ID = 13; // 0xd

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock SYRIAC_SUPPLEMENT;
static { SYRIAC_SUPPLEMENT = null; }

/***/

public static final int SYRIAC_SUPPLEMENT_ID = 279; // 0x117

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock TAGALOG;
static { TAGALOG = null; }

/**
 */

public static final int TAGALOG_ID = 98; // 0x62

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock TAGBANWA;
static { TAGBANWA = null; }

/**
 */

public static final int TAGBANWA_ID = 101; // 0x65

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock TAGS;
static { TAGS = null; }

/**
 */

public static final int TAGS_ID = 96; // 0x60

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock TAI_LE;
static { TAI_LE = null; }

/**
 */

public static final int TAI_LE_ID = 112; // 0x70

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock TAI_THAM;
static { TAI_THAM = null; }

/***/

public static final int TAI_THAM_ID = 174; // 0xae

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock TAI_VIET;
static { TAI_VIET = null; }

/***/

public static final int TAI_VIET_ID = 183; // 0xb7

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock TAI_XUAN_JING_SYMBOLS;
static { TAI_XUAN_JING_SYMBOLS = null; }

/**
 */

public static final int TAI_XUAN_JING_SYMBOLS_ID = 124; // 0x7c

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock TAKRI;
static { TAKRI = null; }

/***/

public static final int TAKRI_ID = 220; // 0xdc

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock TAMIL;
static { TAMIL = null; }

/**
 */

public static final int TAMIL_ID = 20; // 0x14

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock TAMIL_SUPPLEMENT;
static { TAMIL_SUPPLEMENT = null; }

/***/

public static final int TAMIL_SUPPLEMENT_ID = 299; // 0x12b

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock TANGUT;
static { TANGUT = null; }

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock TANGUT_COMPONENTS;
static { TANGUT_COMPONENTS = null; }

/***/

public static final int TANGUT_COMPONENTS_ID = 273; // 0x111

/***/

public static final int TANGUT_ID = 272; // 0x110

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock TANGUT_SUPPLEMENT;
static { TANGUT_SUPPLEMENT = null; }

/***/

public static final int TANGUT_SUPPLEMENT_ID = 307; // 0x133

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock TELUGU;
static { TELUGU = null; }

/**
 */

public static final int TELUGU_ID = 21; // 0x15

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock THAANA;
static { THAANA = null; }

/**
 */

public static final int THAANA_ID = 14; // 0xe

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock THAI;
static { THAI = null; }

/**
 */

public static final int THAI_ID = 25; // 0x19

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock TIBETAN;
static { TIBETAN = null; }

/**
 */

public static final int TIBETAN_ID = 27; // 0x1b

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock TIFINAGH;
static { TIFINAGH = null; }

/**
 */

public static final int TIFINAGH_ID = 144; // 0x90

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock TIRHUTA;
static { TIRHUTA = null; }

/***/

public static final int TIRHUTA_ID = 251; // 0xfb

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock TRANSPORT_AND_MAP_SYMBOLS;
static { TRANSPORT_AND_MAP_SYMBOLS = null; }

/***/

public static final int TRANSPORT_AND_MAP_SYMBOLS_ID = 207; // 0xcf

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock UGARITIC;
static { UGARITIC = null; }

/**
 */

public static final int UGARITIC_ID = 120; // 0x78

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS;
static { UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS = null; }

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS_EXTENDED;
static { UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS_EXTENDED = null; }

/***/

public static final int UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS_EXTENDED_ID = 173; // 0xad

/**
 */

public static final int UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS_ID = 33; // 0x21

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock VAI;
static { VAI = null; }

/**
 */

public static final int VAI_ID = 159; // 0x9f

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock VARIATION_SELECTORS;
static { VARIATION_SELECTORS = null; }

/**
 */

public static final int VARIATION_SELECTORS_ID = 108; // 0x6c

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock VARIATION_SELECTORS_SUPPLEMENT;
static { VARIATION_SELECTORS_SUPPLEMENT = null; }

/**
 */

public static final int VARIATION_SELECTORS_SUPPLEMENT_ID = 125; // 0x7d

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock VEDIC_EXTENSIONS;
static { VEDIC_EXTENSIONS = null; }

/***/

public static final int VEDIC_EXTENSIONS_ID = 175; // 0xaf

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock VERTICAL_FORMS;
static { VERTICAL_FORMS = null; }

/**
 */

public static final int VERTICAL_FORMS_ID = 145; // 0x91

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock WANCHO;
static { WANCHO = null; }

/***/

public static final int WANCHO_ID = 300; // 0x12c

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock WARANG_CITI;
static { WARANG_CITI = null; }

/***/

public static final int WARANG_CITI_ID = 252; // 0xfc

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock YEZIDI;
static { YEZIDI = null; }

/***/

public static final int YEZIDI_ID = 308; // 0x134

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock YIJING_HEXAGRAM_SYMBOLS;
static { YIJING_HEXAGRAM_SYMBOLS = null; }

/**
 */

public static final int YIJING_HEXAGRAM_SYMBOLS_ID = 116; // 0x74

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock YI_RADICALS;
static { YI_RADICALS = null; }

/**
 */

public static final int YI_RADICALS_ID = 73; // 0x49

/**
 */

public static final android.icu.lang.UCharacter.UnicodeBlock YI_SYLLABLES;
static { YI_SYLLABLES = null; }

/**
 */

public static final int YI_SYLLABLES_ID = 72; // 0x48

/***/

public static final android.icu.lang.UCharacter.UnicodeBlock ZANABAZAR_SQUARE;
static { ZANABAZAR_SQUARE = null; }

/***/

public static final int ZANABAZAR_SQUARE_ID = 280; // 0x118
}

/**
 * Vertical Orientation constants.
 *
 * @see android.icu.lang.UProperty#VERTICAL_ORIENTATION
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static interface VerticalOrientation {

/***/

public static final int ROTATED = 0; // 0x0

/***/

public static final int TRANSFORMED_ROTATED = 1; // 0x1

/***/

public static final int TRANSFORMED_UPRIGHT = 2; // 0x2

/***/

public static final int UPRIGHT = 3; // 0x3
}

/**
 * Word Break constants.
 * @see android.icu.lang.UProperty#WORD_BREAK
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static interface WordBreak {

/**
 */

public static final int ALETTER = 1; // 0x1

/**
 */

public static final int CR = 8; // 0x8

/***/

public static final int DOUBLE_QUOTE = 16; // 0x10

/**
 */

public static final int EXTEND = 9; // 0x9

/**
 */

public static final int EXTENDNUMLET = 7; // 0x7

/***/

public static final int E_BASE = 17; // 0x11

/***/

public static final int E_BASE_GAZ = 18; // 0x12

/***/

public static final int E_MODIFIER = 19; // 0x13

/**
 */

public static final int FORMAT = 2; // 0x2

/***/

public static final int GLUE_AFTER_ZWJ = 20; // 0x14

/***/

public static final int HEBREW_LETTER = 14; // 0xe

/**
 */

public static final int KATAKANA = 3; // 0x3

/**
 */

public static final int LF = 10; // 0xa

/**
 */

public static final int MIDLETTER = 4; // 0x4

/**
 */

public static final int MIDNUM = 5; // 0x5

/**
 */

public static final int MIDNUMLET = 11; // 0xb

/**
 */

public static final int NEWLINE = 12; // 0xc

/**
 */

public static final int NUMERIC = 6; // 0x6

/**
 */

public static final int OTHER = 0; // 0x0

/***/

public static final int REGIONAL_INDICATOR = 13; // 0xd

/***/

public static final int SINGLE_QUOTE = 15; // 0xf

/***/

public static final int WSEGSPACE = 22; // 0x16

/***/

public static final int ZWJ = 21; // 0x15
}

}

