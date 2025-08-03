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

import android.icu.lang.UCharacter;
import java.util.Collection;
import android.icu.lang.UProperty;
import android.icu.util.Freezable;
import java.util.Iterator;

/**
 * A mutable set of Unicode characters and multicharacter strings.
 * Objects of this class represent <em>character classes</em> used
 * in regular expressions. A character specifies a subset of Unicode
 * code points.  Legal code points are U+0000 to U+10FFFF, inclusive.
 *
 * Note: method freeze() will not only make the set immutable, but
 * also makes important methods much higher performance:
 * contains(c), containsNone(...), span(...), spanBack(...) etc.
 * After the object is frozen, any subsequent call that wants to change
 * the object will throw UnsupportedOperationException.
 *
 * <p>The UnicodeSet class is not designed to be subclassed.
 *
 * <p><code>UnicodeSet</code> supports two APIs. The first is the
 * <em>operand</em> API that allows the caller to modify the value of
 * a <code>UnicodeSet</code> object. It conforms to Java 2's
 * <code>java.util.Set</code> interface, although
 * <code>UnicodeSet</code> does not actually implement that
 * interface. All methods of <code>Set</code> are supported, with the
 * modification that they take a character range or single character
 * instead of an <code>Object</code>, and they take a
 * <code>UnicodeSet</code> instead of a <code>Collection</code>.  The
 * operand API may be thought of in terms of boolean logic: a boolean
 * OR is implemented by <code>add</code>, a boolean AND is implemented
 * by <code>retain</code>, a boolean XOR is implemented by
 * <code>complement</code> taking an argument, and a boolean NOT is
 * implemented by <code>complement</code> with no argument.  In terms
 * of traditional set theory function names, <code>add</code> is a
 * union, <code>retain</code> is an intersection, <code>remove</code>
 * is an asymmetric difference, and <code>complement</code> with no
 * argument is a set complement with respect to the superset range
 * <code>MIN_VALUE-MAX_VALUE</code>
 *
 * <p>The second API is the
 * <code>applyPattern()</code>/<code>toPattern()</code> API from the
 * <code>java.text.Format</code>-derived classes.  Unlike the
 * methods that add characters, add categories, and control the logic
 * of the set, the method <code>applyPattern()</code> sets all
 * attributes of a <code>UnicodeSet</code> at once, based on a
 * string pattern.
 *
 * <p><b>Pattern syntax</b></p>
 *
 * Patterns are accepted by the constructors and the
 * <code>applyPattern()</code> methods and returned by the
 * <code>toPattern()</code> method.  These patterns follow a syntax
 * similar to that employed by version 8 regular expression character
 * classes.  Here are some simple examples:
 *
 * <blockquote>
 *   <table>
 *     <tr style="vertical-align: top">
 *       <td style="white-space: nowrap; vertical-align: top; horizontal-align: left;"><code>[]</code></td>
 *       <td style="vertical-align: top;">No characters</td>
 *     </tr><tr style="vertical-align: top">
 *       <td style="white-space: nowrap; vertical-align: top; horizontal-align: left;"><code>[a]</code></td>
 *       <td style="vertical-align: top;">The character 'a'</td>
 *     </tr><tr style="vertical-align: top">
 *       <td style="white-space: nowrap; vertical-align: top; horizontal-align: left;"><code>[ae]</code></td>
 *       <td style="vertical-align: top;">The characters 'a' and 'e'</td>
 *     </tr>
 *     <tr>
 *       <td style="white-space: nowrap; vertical-align: top; horizontal-align: left;"><code>[a-e]</code></td>
 *       <td style="vertical-align: top;">The characters 'a' through 'e' inclusive, in Unicode code
 *       point order</td>
 *     </tr>
 *     <tr>
 *       <td style="white-space: nowrap; vertical-align: top; horizontal-align: left;"><code>[\\u4E01]</code></td>
 *       <td style="vertical-align: top;">The character U+4E01</td>
 *     </tr>
 *     <tr>
 *       <td style="white-space: nowrap; vertical-align: top; horizontal-align: left;"><code>[a{ab}{ac}]</code></td>
 *       <td style="vertical-align: top;">The character 'a' and the multicharacter strings &quot;ab&quot; and
 *       &quot;ac&quot;</td>
 *     </tr>
 *     <tr>
 *       <td style="white-space: nowrap; vertical-align: top; horizontal-align: left;"><code>[\p{Lu}]</code></td>
 *       <td style="vertical-align: top;">All characters in the general category Uppercase Letter</td>
 *     </tr>
 *   </table>
 * </blockquote>
 *
 * Any character may be preceded by a backslash in order to remove any special
 * meaning.  White space characters, as defined by the Unicode Pattern_White_Space property, are
 * ignored, unless they are escaped.
 *
 * <p>Property patterns specify a set of characters having a certain
 * property as defined by the Unicode standard.  Both the POSIX-like
 * "[:Lu:]" and the Perl-like syntax "\p{Lu}" are recognized.  For a
 * complete list of supported property patterns, see the User's Guide
 * for UnicodeSet at
 * <a href="http://www.icu-project.org/userguide/unicodeSet.html">
 * http://www.icu-project.org/userguide/unicodeSet.html</a>.
 * Actual determination of property data is defined by the underlying
 * Unicode database as implemented by UCharacter.
 *
 * <p>Patterns specify individual characters, ranges of characters, and
 * Unicode property sets.  When elements are concatenated, they
 * specify their union.  To complement a set, place a '^' immediately
 * after the opening '['.  Property patterns are inverted by modifying
 * their delimiters; "[:^foo]" and "\P{foo}".  In any other location,
 * '^' has no special meaning.
 *
 * <p>Ranges are indicated by placing two a '-' between two
 * characters, as in "a-z".  This specifies the range of all
 * characters from the left to the right, in Unicode order.  If the
 * left character is greater than or equal to the
 * right character it is a syntax error.  If a '-' occurs as the first
 * character after the opening '[' or '[^', or if it occurs as the
 * last character before the closing ']', then it is taken as a
 * literal.  Thus "[a\\-b]", "[-ab]", and "[ab-]" all indicate the same
 * set of three characters, 'a', 'b', and '-'.
 *
 * <p>Sets may be intersected using the '&amp;' operator or the asymmetric
 * set difference may be taken using the '-' operator, for example,
 * "[[:L:]&amp;[\\u0000-\\u0FFF]]" indicates the set of all Unicode letters
 * with values less than 4096.  Operators ('&amp;' and '|') have equal
 * precedence and bind left-to-right.  Thus
 * "[[:L:]-[a-z]-[\\u0100-\\u01FF]]" is equivalent to
 * "[[[:L:]-[a-z]]-[\\u0100-\\u01FF]]".  This only really matters for
 * difference; intersection is commutative.
 *
 * <table>
 * <tr style="vertical-align: top;"><td style="white-space: nowrap;"><code>[a]</code><td>The set containing 'a'
 * <tr style="vertical-align: top;"><td style="white-space: nowrap;"><code>[a-z]</code><td>The set containing 'a'
 * through 'z' and all letters in between, in Unicode order
 * <tr style="vertical-align: top;"><td style="white-space: nowrap;"><code>[^a-z]</code><td>The set containing
 * all characters but 'a' through 'z',
 * that is, U+0000 through 'a'-1 and 'z'+1 through U+10FFFF
 * <tr style="vertical-align: top;"><td style="white-space: nowrap;"><code>[[<em>pat1</em>][<em>pat2</em>]]</code>
 * <td>The union of sets specified by <em>pat1</em> and <em>pat2</em>
 * <tr style="vertical-align: top;"><td style="white-space: nowrap;"><code>[[<em>pat1</em>]&amp;[<em>pat2</em>]]</code>
 * <td>The intersection of sets specified by <em>pat1</em> and <em>pat2</em>
 * <tr style="vertical-align: top;"><td style="white-space: nowrap;"><code>[[<em>pat1</em>]-[<em>pat2</em>]]</code>
 * <td>The asymmetric difference of sets specified by <em>pat1</em> and
 * <em>pat2</em>
 * <tr style="vertical-align: top;"><td style="white-space: nowrap;"><code>[:Lu:] or \p{Lu}</code>
 * <td>The set of characters having the specified
 * Unicode property; in
 * this case, Unicode uppercase letters
 * <tr style="vertical-align: top;"><td style="white-space: nowrap;"><code>[:^Lu:] or \P{Lu}</code>
 * <td>The set of characters <em>not</em> having the given
 * Unicode property
 * </table>
 *
 * <p><b>Warning</b>: you cannot add an empty string ("") to a UnicodeSet.</p>
 *
 * <p><b>Formal syntax</b></p>
 *
 * <blockquote>
 *   <table>
 *     <tr style="vertical-align: top">
 *       <td style="white-space: nowrap; vertical-align: top;" align="right"><code>pattern :=&nbsp; </code></td>
 *       <td style="vertical-align: top;"><code>('[' '^'? item* ']') |
 *       property</code></td>
 *     </tr>
 *     <tr style="vertical-align: top">
 *       <td style="white-space: nowrap; vertical-align: top;" align="right"><code>item :=&nbsp; </code></td>
 *       <td style="vertical-align: top;"><code>char | (char '-' char) | pattern-expr<br>
 *       </code></td>
 *     </tr>
 *     <tr style="vertical-align: top">
 *       <td style="white-space: nowrap; vertical-align: top;" align="right"><code>pattern-expr :=&nbsp; </code></td>
 *       <td style="vertical-align: top;"><code>pattern | pattern-expr pattern |
 *       pattern-expr op pattern<br>
 *       </code></td>
 *     </tr>
 *     <tr style="vertical-align: top">
 *       <td style="white-space: nowrap; vertical-align: top;" align="right"><code>op :=&nbsp; </code></td>
 *       <td style="vertical-align: top;"><code>'&amp;' | '-'<br>
 *       </code></td>
 *     </tr>
 *     <tr style="vertical-align: top">
 *       <td style="white-space: nowrap; vertical-align: top;" align="right"><code>special :=&nbsp; </code></td>
 *       <td style="vertical-align: top;"><code>'[' | ']' | '-'<br>
 *       </code></td>
 *     </tr>
 *     <tr style="vertical-align: top">
 *       <td style="white-space: nowrap; vertical-align: top;" align="right"><code>char :=&nbsp; </code></td>
 *       <td style="vertical-align: top;"><em>any character that is not</em><code> special<br>
 *       | ('\\' </code><em>any character</em><code>)<br>
 *       | ('&#92;u' hex hex hex hex)<br>
 *       </code></td>
 *     </tr>
 *     <tr style="vertical-align: top">
 *       <td style="white-space: nowrap; vertical-align: top;" align="right"><code>hex :=&nbsp; </code></td>
 *       <td style="vertical-align: top;"><em>any character for which
 *       </em><code>Character.digit(c, 16)</code><em>
 *       returns a non-negative result</em></td>
 *     </tr>
 *     <tr>
 *       <td style="white-space: nowrap; vertical-align: top;" align="right"><code>property :=&nbsp; </code></td>
 *       <td style="vertical-align: top;"><em>a Unicode property set pattern</em></td>
 *     </tr>
 *   </table>
 *   <br>
 *   <table border="1">
 *     <tr>
 *       <td>Legend: <table>
 *         <tr>
 *           <td style="white-space: nowrap; vertical-align: top;"><code>a := b</code></td>
 *           <td style="width: 20; vertical-align: top;">&nbsp; </td>
 *           <td style="vertical-align: top;"><code>a</code> may be replaced by <code>b</code> </td>
 *         </tr>
 *         <tr>
 *           <td style="white-space: nowrap; vertical-align: top;"><code>a?</code></td>
 *           <td style="vertical-align: top;"></td>
 *           <td style="vertical-align: top;">zero or one instance of <code>a</code><br>
 *           </td>
 *         </tr>
 *         <tr>
 *           <td style="white-space: nowrap; vertical-align: top;"><code>a*</code></td>
 *           <td style="vertical-align: top;"></td>
 *           <td style="vertical-align: top;">one or more instances of <code>a</code><br>
 *           </td>
 *         </tr>
 *         <tr>
 *           <td style="white-space: nowrap; vertical-align: top;"><code>a | b</code></td>
 *           <td style="vertical-align: top;"></td>
 *           <td style="vertical-align: top;">either <code>a</code> or <code>b</code><br>
 *           </td>
 *         </tr>
 *         <tr>
 *           <td style="white-space: nowrap; vertical-align: top;"><code>'a'</code></td>
 *           <td style="vertical-align: top;"></td>
 *           <td style="vertical-align: top;">the literal string between the quotes </td>
 *         </tr>
 *       </table>
 *       </td>
 *     </tr>
 *   </table>
 * </blockquote>
 * <p>To iterate over contents of UnicodeSet, the following are available:
 * <ul><li>{@link #ranges()} to iterate through the ranges</li>
 * <li>{@link #strings()} to iterate through the strings</li>
 * <li>{@link #iterator()} to iterate through the entire contents in a single loop.
 * That method is, however, not particularly efficient, since it "boxes" each code point into a String.
 * </ul>
 * All of the above can be used in <b>for</b> loops.
 * The {@link android.icu.text.UnicodeSetIterator UnicodeSetIterator} can also be used, but not in <b>for</b> loops.
 * <p>To replace, count elements, or delete spans, see {@link android.icu.text.UnicodeSetSpanner UnicodeSetSpanner}.
 *
 * @author Alan Liu
 * @see android.icu.text.UnicodeSetIterator
 * @see android.icu.text.UnicodeSetSpanner
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class UnicodeSet extends android.icu.text.UnicodeFilter implements java.lang.Iterable<java.lang.String>, java.lang.Comparable<android.icu.text.UnicodeSet>, android.icu.util.Freezable<android.icu.text.UnicodeSet> {

/**
 * Constructs an empty set.
 */

public UnicodeSet() { throw new RuntimeException("Stub!"); }

/**
 * Constructs a copy of an existing set.
 */

public UnicodeSet(android.icu.text.UnicodeSet other) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a set containing the given range. If <code>end &gt;
 * start</code> then an empty set is created.
 *
 * @param start first character, inclusive, of range
 * @param end last character, inclusive, of range
 */

public UnicodeSet(int start, int end) { throw new RuntimeException("Stub!"); }

/**
 * Quickly constructs a set from a set of ranges &lt;s0, e0, s1, e1, s2, e2, ..., sn, en&gt;.
 * There must be an even number of integers, and they must be all greater than zero,
 * all less than or equal to Character.MAX_CODE_POINT.
 * In each pair (..., si, ei, ...) it must be true that si &lt;= ei
 * Between adjacent pairs (...ei, sj...), it must be true that ei+1 &lt; sj
 * @param pairs pairs of character representing ranges
 */

public UnicodeSet(int... pairs) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a set from the given pattern.  See the class description
 * for the syntax of the pattern language.  Whitespace is ignored.
 * @param pattern a string specifying what characters are in the set
 * @exception java.lang.IllegalArgumentException if the pattern contains
 * a syntax error.
 */

public UnicodeSet(java.lang.String pattern) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a set from the given pattern.  See the class description
 * for the syntax of the pattern language.
 * @param pattern a string specifying what characters are in the set
 * @param ignoreWhitespace if true, ignore Unicode Pattern_White_Space characters
 * @exception java.lang.IllegalArgumentException if the pattern contains
 * a syntax error.
 */

public UnicodeSet(java.lang.String pattern, boolean ignoreWhitespace) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a set from the given pattern.  See the class description
 * for the syntax of the pattern language.
 * @param pattern a string specifying what characters are in the set
 * @param options a bitmask indicating which options to apply.
 * Valid options are IGNORE_SPACE and CASE.
 * @exception java.lang.IllegalArgumentException if the pattern contains
 * a syntax error.
 */

public UnicodeSet(java.lang.String pattern, int options) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a set from the given pattern.  See the class description
 * for the syntax of the pattern language.
 * @param pattern a string specifying what characters are in the set
 * @param pos on input, the position in pattern at which to start parsing.
 * On output, the position after the last character parsed.
 * @param symbols a symbol table mapping variables to char[] arrays
 * and chars to UnicodeSets
 * @exception java.lang.IllegalArgumentException if the pattern
 * contains a syntax error.
 */

public UnicodeSet(java.lang.String pattern, java.text.ParsePosition pos, android.icu.text.SymbolTable symbols) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a set from the given pattern.  See the class description
 * for the syntax of the pattern language.
 * @param pattern a string specifying what characters are in the set
 * @param pos on input, the position in pattern at which to start parsing.
 * On output, the position after the last character parsed.
 * @param symbols a symbol table mapping variables to char[] arrays
 * and chars to UnicodeSets
 * @param options a bitmask indicating which options to apply.
 * Valid options are IGNORE_SPACE and CASE.
 * @exception java.lang.IllegalArgumentException if the pattern
 * contains a syntax error.
 */

public UnicodeSet(java.lang.String pattern, java.text.ParsePosition pos, android.icu.text.SymbolTable symbols, int options) { throw new RuntimeException("Stub!"); }

/**
 * Return a new set that is equivalent to this one.
 */

public java.lang.Object clone() { throw new RuntimeException("Stub!"); }

/**
 * Make this object represent the range <code>start - end</code>.
 * If <code>end &gt; start</code> then this object is set to an empty range.
 *
 * @param start first character in the set, inclusive
 * @param end last character in the set, inclusive
 */

public android.icu.text.UnicodeSet set(int start, int end) { throw new RuntimeException("Stub!"); }

/**
 * Make this object represent the same set as <code>other</code>.
 * @param other a <code>UnicodeSet</code> whose value will be
 * copied to this object
 */

public android.icu.text.UnicodeSet set(android.icu.text.UnicodeSet other) { throw new RuntimeException("Stub!"); }

/**
 * Modifies this set to represent the set specified by the given pattern.
 * See the class description for the syntax of the pattern language.
 * Whitespace is ignored.
 * @param pattern a string specifying what characters are in the set
 * @exception java.lang.IllegalArgumentException if the pattern
 * contains a syntax error.
 */

public final android.icu.text.UnicodeSet applyPattern(java.lang.String pattern) { throw new RuntimeException("Stub!"); }

/**
 * Modifies this set to represent the set specified by the given pattern,
 * optionally ignoring whitespace.
 * See the class description for the syntax of the pattern language.
 * @param pattern a string specifying what characters are in the set
 * @param ignoreWhitespace if true then Unicode Pattern_White_Space characters are ignored
 * @exception java.lang.IllegalArgumentException if the pattern
 * contains a syntax error.
 */

public android.icu.text.UnicodeSet applyPattern(java.lang.String pattern, boolean ignoreWhitespace) { throw new RuntimeException("Stub!"); }

/**
 * Modifies this set to represent the set specified by the given pattern,
 * optionally ignoring whitespace.
 * See the class description for the syntax of the pattern language.
 * @param pattern a string specifying what characters are in the set
 * @param options a bitmask indicating which options to apply.
 * Valid options are IGNORE_SPACE and CASE.
 * @exception java.lang.IllegalArgumentException if the pattern
 * contains a syntax error.
 */

public android.icu.text.UnicodeSet applyPattern(java.lang.String pattern, int options) { throw new RuntimeException("Stub!"); }

/**
 * Returns a string representation of this set.  If the result of
 * calling this function is passed to a UnicodeSet constructor, it
 * will produce another set that is equal to this one.
 */

public java.lang.String toPattern(boolean escapeUnprintable) { throw new RuntimeException("Stub!"); }

/**
 * Generate and append a string representation of this set to result.
 * This does not use this.pat, the cleaned up copy of the string
 * passed to applyPattern().
 * @param result the buffer into which to generate the pattern
 * @param escapeUnprintable escape unprintable characters if true
 */

public java.lang.StringBuffer _generatePattern(java.lang.StringBuffer result, boolean escapeUnprintable) { throw new RuntimeException("Stub!"); }

/**
 * Generate and append a string representation of this set to result.
 * This does not use this.pat, the cleaned up copy of the string
 * passed to applyPattern().
 * @param includeStrings if false, doesn't include the strings.
 */

public java.lang.StringBuffer _generatePattern(java.lang.StringBuffer result, boolean escapeUnprintable, boolean includeStrings) { throw new RuntimeException("Stub!"); }

/**
 * Returns the number of elements in this set (its cardinality)
 * Note than the elements of a set may include both individual
 * codepoints and strings.
 *
 * @return the number of elements in this set (its cardinality).
 */

public int size() { throw new RuntimeException("Stub!"); }

/**
 * Returns <tt>true</tt> if this set contains no elements.
 *
 * @return <tt>true</tt> if this set contains no elements.
 */

public boolean isEmpty() { throw new RuntimeException("Stub!"); }

/**
 * Implementation of UnicodeMatcher API.  Returns <tt>true</tt> if
 * this set contains any character whose low byte is the given
 * value.  This is used by <tt>RuleBasedTransliterator</tt> for
 * indexing.
 */

public boolean matchesIndexValue(int v) { throw new RuntimeException("Stub!"); }

/**
 * Implementation of UnicodeMatcher.matches().  Always matches the
 * longest possible multichar string.
 */

public int matches(android.icu.text.Replaceable text, int[] offset, int limit, boolean incremental) { throw new RuntimeException("Stub!"); }

/**
 * Implementation of UnicodeMatcher API.  Union the set of all
 * characters that may be matched by this object into the given
 * set.
 * @param toUnionTo the set into which to union the source characters
 */

public void addMatchSetTo(android.icu.text.UnicodeSet toUnionTo) { throw new RuntimeException("Stub!"); }

/**
 * Returns the index of the given character within this set, where
 * the set is ordered by ascending code point.  If the character
 * is not in this set, return -1.  The inverse of this method is
 * <code>charAt()</code>.
 * @return an index from 0..size()-1, or -1
 */

public int indexOf(int c) { throw new RuntimeException("Stub!"); }

/**
 * Returns the character at the given index within this set, where
 * the set is ordered by ascending code point.  If the index is
 * out of range, return -1.  The inverse of this method is
 * <code>indexOf()</code>.
 * @param index an index from 0..size()-1
 * @return the character at the given index, or -1.
 */

public int charAt(int index) { throw new RuntimeException("Stub!"); }

/**
 * Adds the specified range to this set if it is not already
 * present.  If this set already contains the specified range,
 * the call leaves this set unchanged.  If <code>end &gt; start</code>
 * then an empty range is added, leaving the set unchanged.
 *
 * @param start first character, inclusive, of range to be added
 * to this set.
 * @param end last character, inclusive, of range to be added
 * to this set.
 */

public android.icu.text.UnicodeSet add(int start, int end) { throw new RuntimeException("Stub!"); }

/**
 * Adds all characters in range (uses preferred naming convention).
 * @param start The index of where to start on adding all characters.
 * @param end The index of where to end on adding all characters.
 * @return a reference to this object
 */

public android.icu.text.UnicodeSet addAll(int start, int end) { throw new RuntimeException("Stub!"); }

/**
 * Adds the specified character to this set if it is not already
 * present.  If this set already contains the specified character,
 * the call leaves this set unchanged.
 */

public final android.icu.text.UnicodeSet add(int c) { throw new RuntimeException("Stub!"); }

/**
 * Adds the specified multicharacter to this set if it is not already
 * present.  If this set already contains the multicharacter,
 * the call leaves this set unchanged.
 * Thus "ch" =&gt; {"ch"}
 * <br><b>Warning: you cannot add an empty string ("") to a UnicodeSet.</b>
 * @param s the source string
 * @return this object, for chaining
 */

public final android.icu.text.UnicodeSet add(java.lang.CharSequence s) { throw new RuntimeException("Stub!"); }

/**
 * Adds each of the characters in this string to the set. Thus "ch" =&gt; {"c", "h"}
 * If this set already any particular character, it has no effect on that character.
 * @param s the source string
 * @return this object, for chaining
 */

public final android.icu.text.UnicodeSet addAll(java.lang.CharSequence s) { throw new RuntimeException("Stub!"); }

/**
 * Retains EACH of the characters in this string. Note: "ch" == {"c", "h"}
 * If this set already any particular character, it has no effect on that character.
 * @param s the source string
 * @return this object, for chaining
 */

public final android.icu.text.UnicodeSet retainAll(java.lang.CharSequence s) { throw new RuntimeException("Stub!"); }

/**
 * Complement EACH of the characters in this string. Note: "ch" == {"c", "h"}
 * If this set already any particular character, it has no effect on that character.
 * @param s the source string
 * @return this object, for chaining
 */

public final android.icu.text.UnicodeSet complementAll(java.lang.CharSequence s) { throw new RuntimeException("Stub!"); }

/**
 * Remove EACH of the characters in this string. Note: "ch" == {"c", "h"}
 * If this set already any particular character, it has no effect on that character.
 * @param s the source string
 * @return this object, for chaining
 */

public final android.icu.text.UnicodeSet removeAll(java.lang.CharSequence s) { throw new RuntimeException("Stub!"); }

/**
 * Remove all strings from this UnicodeSet
 * @return this object, for chaining
 */

public final android.icu.text.UnicodeSet removeAllStrings() { throw new RuntimeException("Stub!"); }

/**
 * Makes a set from a multicharacter string. Thus "ch" =&gt; {"ch"}
 * <br><b>Warning: you cannot add an empty string ("") to a UnicodeSet.</b>
 * @param s the source string
 * @return a newly created set containing the given string
 */

public static android.icu.text.UnicodeSet from(java.lang.CharSequence s) { throw new RuntimeException("Stub!"); }

/**
 * Makes a set from each of the characters in the string. Thus "ch" =&gt; {"c", "h"}
 * @param s the source string
 * @return a newly created set containing the given characters
 */

public static android.icu.text.UnicodeSet fromAll(java.lang.CharSequence s) { throw new RuntimeException("Stub!"); }

/**
 * Retain only the elements in this set that are contained in the
 * specified range.  If <code>end &gt; start</code> then an empty range is
 * retained, leaving the set empty.
 *
 * @param start first character, inclusive, of range to be retained
 * to this set.
 * @param end last character, inclusive, of range to be retained
 * to this set.
 */

public android.icu.text.UnicodeSet retain(int start, int end) { throw new RuntimeException("Stub!"); }

/**
 * Retain the specified character from this set if it is present.
 * Upon return this set will be empty if it did not contain c, or
 * will only contain c if it did contain c.
 * @param c the character to be retained
 * @return this object, for chaining
 */

public final android.icu.text.UnicodeSet retain(int c) { throw new RuntimeException("Stub!"); }

/**
 * Retain the specified string in this set if it is present.
 * Upon return this set will be empty if it did not contain s, or
 * will only contain s if it did contain s.
 * @param cs the string to be retained
 * @return this object, for chaining
 */

public final android.icu.text.UnicodeSet retain(java.lang.CharSequence cs) { throw new RuntimeException("Stub!"); }

/**
 * Removes the specified range from this set if it is present.
 * The set will not contain the specified range once the call
 * returns.  If <code>end &gt; start</code> then an empty range is
 * removed, leaving the set unchanged.
 *
 * @param start first character, inclusive, of range to be removed
 * from this set.
 * @param end last character, inclusive, of range to be removed
 * from this set.
 */

public android.icu.text.UnicodeSet remove(int start, int end) { throw new RuntimeException("Stub!"); }

/**
 * Removes the specified character from this set if it is present.
 * The set will not contain the specified character once the call
 * returns.
 * @param c the character to be removed
 * @return this object, for chaining
 */

public final android.icu.text.UnicodeSet remove(int c) { throw new RuntimeException("Stub!"); }

/**
 * Removes the specified string from this set if it is present.
 * The set will not contain the specified string once the call
 * returns.
 * @param s the string to be removed
 * @return this object, for chaining
 */

public final android.icu.text.UnicodeSet remove(java.lang.CharSequence s) { throw new RuntimeException("Stub!"); }

/**
 * Complements the specified range in this set.  Any character in
 * the range will be removed if it is in this set, or will be
 * added if it is not in this set.  If <code>end &gt; start</code>
 * then an empty range is complemented, leaving the set unchanged.
 *
 * @param start first character, inclusive, of range to be removed
 * from this set.
 * @param end last character, inclusive, of range to be removed
 * from this set.
 */

public android.icu.text.UnicodeSet complement(int start, int end) { throw new RuntimeException("Stub!"); }

/**
 * Complements the specified character in this set.  The character
 * will be removed if it is in this set, or will be added if it is
 * not in this set.
 */

public final android.icu.text.UnicodeSet complement(int c) { throw new RuntimeException("Stub!"); }

/**
 * This is equivalent to
 * <code>complement(MIN_VALUE, MAX_VALUE)</code>.
 */

public android.icu.text.UnicodeSet complement() { throw new RuntimeException("Stub!"); }

/**
 * Complement the specified string in this set.
 * The set will not contain the specified string once the call
 * returns.
 * <br><b>Warning: you cannot add an empty string ("") to a UnicodeSet.</b>
 * @param s the string to complement
 * @return this object, for chaining
 */

public final android.icu.text.UnicodeSet complement(java.lang.CharSequence s) { throw new RuntimeException("Stub!"); }

/**
 * Returns true if this set contains the given character.
 * @param c character to be checked for containment
 * @return true if the test condition is met
 */

public boolean contains(int c) { throw new RuntimeException("Stub!"); }

/**
 * Returns true if this set contains every character
 * of the given range.
 * @param start first character, inclusive, of the range
 * @param end last character, inclusive, of the range
 * @return true if the test condition is met
 */

public boolean contains(int start, int end) { throw new RuntimeException("Stub!"); }

/**
 * Returns <tt>true</tt> if this set contains the given
 * multicharacter string.
 * @param s string to be checked for containment
 * @return <tt>true</tt> if this set contains the specified string
 */

public final boolean contains(java.lang.CharSequence s) { throw new RuntimeException("Stub!"); }

/**
 * Returns true if this set contains all the characters and strings
 * of the given set.
 * @param b set to be checked for containment
 * @return true if the test condition is met
 */

public boolean containsAll(android.icu.text.UnicodeSet b) { throw new RuntimeException("Stub!"); }

/**
 * Returns true if there is a partition of the string such that this set contains each of the partitioned strings.
 * For example, for the Unicode set [a{bc}{cd}]<br>
 * containsAll is true for each of: "a", "bc", ""cdbca"<br>
 * containsAll is false for each of: "acb", "bcda", "bcx"<br>
 * @param s string containing characters to be checked for containment
 * @return true if the test condition is met
 */

public boolean containsAll(java.lang.String s) { throw new RuntimeException("Stub!"); }

/**
 * Returns true if this set contains none of the characters
 * of the given range.
 * @param start first character, inclusive, of the range
 * @param end last character, inclusive, of the range
 * @return true if the test condition is met
 */

public boolean containsNone(int start, int end) { throw new RuntimeException("Stub!"); }

/**
 * Returns true if none of the characters or strings in this UnicodeSet appears in the string.
 * For example, for the Unicode set [a{bc}{cd}]<br>
 * containsNone is true for: "xy", "cb"<br>
 * containsNone is false for: "a", "bc", "bcd"<br>
 * @param b set to be checked for containment
 * @return true if the test condition is met
 */

public boolean containsNone(android.icu.text.UnicodeSet b) { throw new RuntimeException("Stub!"); }

/**
 * Returns true if this set contains none of the characters
 * of the given string.
 * @param s string containing characters to be checked for containment
 * @return true if the test condition is met
 */

public boolean containsNone(java.lang.CharSequence s) { throw new RuntimeException("Stub!"); }

/**
 * Returns true if this set contains one or more of the characters
 * in the given range.
 * @param start first character, inclusive, of the range
 * @param end last character, inclusive, of the range
 * @return true if the condition is met
 */

public final boolean containsSome(int start, int end) { throw new RuntimeException("Stub!"); }

/**
 * Returns true if this set contains one or more of the characters
 * and strings of the given set.
 * @param s set to be checked for containment
 * @return true if the condition is met
 */

public final boolean containsSome(android.icu.text.UnicodeSet s) { throw new RuntimeException("Stub!"); }

/**
 * Returns true if this set contains one or more of the characters
 * of the given string.
 * @param s string containing characters to be checked for containment
 * @return true if the condition is met
 */

public final boolean containsSome(java.lang.CharSequence s) { throw new RuntimeException("Stub!"); }

/**
 * Adds all of the elements in the specified set to this set if
 * they're not already present.  This operation effectively
 * modifies this set so that its value is the <i>union</i> of the two
 * sets.  The behavior of this operation is unspecified if the specified
 * collection is modified while the operation is in progress.
 *
 * @param c set whose elements are to be added to this set.
 */

public android.icu.text.UnicodeSet addAll(android.icu.text.UnicodeSet c) { throw new RuntimeException("Stub!"); }

/**
 * Retains only the elements in this set that are contained in the
 * specified set.  In other words, removes from this set all of
 * its elements that are not contained in the specified set.  This
 * operation effectively modifies this set so that its value is
 * the <i>intersection</i> of the two sets.
 *
 * @param c set that defines which elements this set will retain.
 */

public android.icu.text.UnicodeSet retainAll(android.icu.text.UnicodeSet c) { throw new RuntimeException("Stub!"); }

/**
 * Removes from this set all of its elements that are contained in the
 * specified set.  This operation effectively modifies this
 * set so that its value is the <i>asymmetric set difference</i> of
 * the two sets.
 *
 * @param c set that defines which elements will be removed from
 *          this set.
 */

public android.icu.text.UnicodeSet removeAll(android.icu.text.UnicodeSet c) { throw new RuntimeException("Stub!"); }

/**
 * Complements in this set all elements contained in the specified
 * set.  Any character in the other set will be removed if it is
 * in this set, or will be added if it is not in this set.
 *
 * @param c set that defines which elements will be complemented from
 *          this set.
 */

public android.icu.text.UnicodeSet complementAll(android.icu.text.UnicodeSet c) { throw new RuntimeException("Stub!"); }

/**
 * Removes all of the elements from this set.  This set will be
 * empty after this call returns.
 */

public android.icu.text.UnicodeSet clear() { throw new RuntimeException("Stub!"); }

/**
 * Iteration method that returns the number of ranges contained in
 * this set.
 * @see #getRangeStart
 * @see #getRangeEnd
 */

public int getRangeCount() { throw new RuntimeException("Stub!"); }

/**
 * Iteration method that returns the first character in the
 * specified range of this set.
 * @exception java.lang.ArrayIndexOutOfBoundsException if index is outside
 * the range <code>0..getRangeCount()-1</code>
 * @see #getRangeCount
 * @see #getRangeEnd
 */

public int getRangeStart(int index) { throw new RuntimeException("Stub!"); }

/**
 * Iteration method that returns the last character in the
 * specified range of this set.
 * @exception java.lang.ArrayIndexOutOfBoundsException if index is outside
 * the range <code>0..getRangeCount()-1</code>
 * @see #getRangeStart
 * @see #getRangeEnd
 */

public int getRangeEnd(int index) { throw new RuntimeException("Stub!"); }

/**
 * Reallocate this objects internal structures to take up the least
 * possible space, without changing this object's value.
 */

public android.icu.text.UnicodeSet compact() { throw new RuntimeException("Stub!"); }

/**
 * Compares the specified object with this set for equality.  Returns
 * <tt>true</tt> if the specified object is also a set, the two sets
 * have the same size, and every member of the specified set is
 * contained in this set (or equivalently, every member of this set is
 * contained in the specified set).
 *
 * @param o Object to be compared for equality with this set.
 * @return <tt>true</tt> if the specified Object is equal to this set.
 */

public boolean equals(java.lang.Object o) { throw new RuntimeException("Stub!"); }

/**
 * Returns the hash code value for this set.
 *
 * @return the hash code value for this set.
 * @see java.lang.Object#hashCode()
 */

public int hashCode() { throw new RuntimeException("Stub!"); }

/**
 * Return a programmer-readable string representation of this object.
 */

public java.lang.String toString() { throw new RuntimeException("Stub!"); }

/**
 * Add the contents of the UnicodeSet (as strings) into a collection.
 * @param target collection to add into
 */

public <T extends java.util.Collection<java.lang.String>> T addAllTo(T target) { throw new RuntimeException("Stub!"); }

/**
 * Add the contents of the collection (as strings) into this UnicodeSet.
 * The collection must not contain null.
 * @param source the collection to add
 * @return a reference to this object
 */

public android.icu.text.UnicodeSet add(java.lang.Iterable<?> source) { throw new RuntimeException("Stub!"); }

/**
 * Add a collection (as strings) into this UnicodeSet.
 * Uses standard naming convention.
 * @param source collection to add into
 * @return a reference to this object
 */

public android.icu.text.UnicodeSet addAll(java.lang.Iterable<?> source) { throw new RuntimeException("Stub!"); }

/**
 * Modifies this set to contain those code points which have the
 * given value for the given binary or enumerated property, as
 * returned by UCharacter.getIntPropertyValue.  Prior contents of
 * this set are lost.
 *
 * @param prop a property in the range
 * UProperty.BIN_START..UProperty.BIN_LIMIT-1 or
 * UProperty.INT_START..UProperty.INT_LIMIT-1 or.
 * UProperty.MASK_START..UProperty.MASK_LIMIT-1.
 *
 * @param value a value in the range
 * UCharacter.getIntPropertyMinValue(prop)..
 * UCharacter.getIntPropertyMaxValue(prop), with one exception.
 * If prop is UProperty.GENERAL_CATEGORY_MASK, then value should not be
 * a UCharacter.getType() result, but rather a mask value produced
 * by logically ORing (1 &lt;&lt; UCharacter.getType()) values together.
 * This allows grouped categories such as [:L:] to be represented.
 *
 * @return a reference to this set
 */

public android.icu.text.UnicodeSet applyIntPropertyValue(int prop, int value) { throw new RuntimeException("Stub!"); }

/**
 * Modifies this set to contain those code points which have the
 * given value for the given property.  Prior contents of this
 * set are lost.
 *
 * @param propertyAlias a property alias, either short or long.
 * The name is matched loosely.  See PropertyAliases.txt for names
 * and a description of loose matching.  If the value string is
 * empty, then this string is interpreted as either a
 * General_Category value alias, a Script value alias, a binary
 * property alias, or a special ID.  Special IDs are matched
 * loosely and correspond to the following sets:
 *
 * "ANY" = [\\u0000-\\U0010FFFF],
 * "ASCII" = [\\u0000-\\u007F].
 *
 * @param valueAlias a value alias, either short or long.  The
 * name is matched loosely.  See PropertyValueAliases.txt for
 * names and a description of loose matching.  In addition to
 * aliases listed, numeric values and canonical combining classes
 * may be expressed numerically, e.g., ("nv", "0.5") or ("ccc",
 * "220").  The value string may also be empty.
 *
 * @return a reference to this set
 */

public android.icu.text.UnicodeSet applyPropertyAlias(java.lang.String propertyAlias, java.lang.String valueAlias) { throw new RuntimeException("Stub!"); }

/**
 * Modifies this set to contain those code points which have the
 * given value for the given property.  Prior contents of this
 * set are lost.
 * @param propertyAlias A string of the property alias.
 * @param valueAlias A string of the value alias.
 * @param symbols if not null, then symbols are first called to see if a property
 * is available. If true, then everything else is skipped.
 * @return this set
 */

public android.icu.text.UnicodeSet applyPropertyAlias(java.lang.String propertyAlias, java.lang.String valueAlias, android.icu.text.SymbolTable symbols) { throw new RuntimeException("Stub!"); }

/**
 * Close this set over the given attribute.  For the attribute
 * CASE, the result is to modify this set so that:
 *
 * 1. For each character or string 'a' in this set, all strings
 * 'b' such that foldCase(a) == foldCase(b) are added to this set.
 * (For most 'a' that are single characters, 'b' will have
 * b.length() == 1.)
 *
 * 2. For each string 'e' in the resulting set, if e !=
 * foldCase(e), 'e' will be removed.
 *
 * Example: [aq\u00DF{Bc}{bC}{Fi}] =&gt; [aAqQ\u00DF\uFB01{ss}{bc}{fi}]
 *
 * (Here foldCase(x) refers to the operation
 * UCharacter.foldCase(x, true), and a == b actually denotes
 * a.equals(b), not pointer comparison.)
 *
 * @param attribute bitmask for attributes to close over.
 * Currently only the CASE bit is supported.  Any undefined bits
 * are ignored.
 * @return a reference to this set.
 */

public android.icu.text.UnicodeSet closeOver(int attribute) { throw new RuntimeException("Stub!"); }

/**
 * Is this frozen, according to the Freezable interface?
 *
 * @return value
 */

public boolean isFrozen() { throw new RuntimeException("Stub!"); }

/**
 * Freeze this class, according to the Freezable interface.
 *
 * @return this
 */

public android.icu.text.UnicodeSet freeze() { throw new RuntimeException("Stub!"); }

/**
 * Span a string using this UnicodeSet.
 * <p>To replace, count elements, or delete spans, see {@link android.icu.text.UnicodeSetSpanner UnicodeSetSpanner}.
 * @param s The string to be spanned
 * @param spanCondition The span condition
 * @return the length of the span
 */

public int span(java.lang.CharSequence s, android.icu.text.UnicodeSet.SpanCondition spanCondition) { throw new RuntimeException("Stub!"); }

/**
 * Span a string using this UnicodeSet.
 *   If the start index is less than 0, span will start from 0.
 *   If the start index is greater than the string length, span returns the string length.
 * <p>To replace, count elements, or delete spans, see {@link android.icu.text.UnicodeSetSpanner UnicodeSetSpanner}.
 * @param s The string to be spanned
 * @param start The start index that the span begins
 * @param spanCondition The span condition
 * @return the string index which ends the span (i.e. exclusive)
 */

public int span(java.lang.CharSequence s, int start, android.icu.text.UnicodeSet.SpanCondition spanCondition) { throw new RuntimeException("Stub!"); }

/**
 * Span a string backwards (from the end) using this UnicodeSet.
 * <p>To replace, count elements, or delete spans, see {@link android.icu.text.UnicodeSetSpanner UnicodeSetSpanner}.
 * @param s The string to be spanned
 * @param spanCondition The span condition
 * @return The string index which starts the span (i.e. inclusive).
 */

public int spanBack(java.lang.CharSequence s, android.icu.text.UnicodeSet.SpanCondition spanCondition) { throw new RuntimeException("Stub!"); }

/**
 * Span a string backwards (from the fromIndex) using this UnicodeSet.
 * If the fromIndex is less than 0, spanBack will return 0.
 * If fromIndex is greater than the string length, spanBack will start from the string length.
 * <p>To replace, count elements, or delete spans, see {@link android.icu.text.UnicodeSetSpanner UnicodeSetSpanner}.
 * @param s The string to be spanned
 * @param fromIndex The index of the char (exclusive) that the string should be spanned backwards
 * @param spanCondition The span condition
 * @return The string index which starts the span (i.e. inclusive).
 */

public int spanBack(java.lang.CharSequence s, int fromIndex, android.icu.text.UnicodeSet.SpanCondition spanCondition) { throw new RuntimeException("Stub!"); }

/**
 * Clone a thawed version of this class, according to the Freezable interface.
 * @return the clone, not frozen
 */

public android.icu.text.UnicodeSet cloneAsThawed() { throw new RuntimeException("Stub!"); }

/**
 * Provide for faster iteration than by String. Returns an Iterable/Iterator over ranges of code points.
 * The UnicodeSet must not be altered during the iteration.
 * The EntryRange instance is the same each time; the contents are just reset.
 *
 * <p><b>Warning: </b>To iterate over the full contents, you have to also iterate over the strings.
 *
 * <p><b>Warning: </b>For speed, UnicodeSet iteration does not check for concurrent modification.
 * Do not alter the UnicodeSet while iterating.
 *
 * <pre>
 * // Sample code
 * for (EntryRange range : us1.ranges()) {
 *     // do something with code points between range.codepoint and range.codepointEnd;
 * }
 * for (String s : us1.strings()) {
 *     // do something with each string;
 * }
 * </pre>
 */

public java.lang.Iterable<android.icu.text.UnicodeSet.EntryRange> ranges() { throw new RuntimeException("Stub!"); }

/**
 * Returns a string iterator. Uses the same order of iteration as {@link android.icu.text.UnicodeSetIterator UnicodeSetIterator}.
 * <p><b>Warning: </b>For speed, UnicodeSet iteration does not check for concurrent modification.
 * Do not alter the UnicodeSet while iterating.
 * @see java.util.Set#iterator()
 */

public java.util.Iterator<java.lang.String> iterator() { throw new RuntimeException("Stub!"); }

/**
 * @see #containsAll(android.icu.text.UnicodeSet)
 */

public <T extends java.lang.CharSequence> boolean containsAll(java.lang.Iterable<T> collection) { throw new RuntimeException("Stub!"); }

/**
 * @see #containsNone(android.icu.text.UnicodeSet)
 */

public <T extends java.lang.CharSequence> boolean containsNone(java.lang.Iterable<T> collection) { throw new RuntimeException("Stub!"); }

/**
 * @see #containsAll(android.icu.text.UnicodeSet)
 */

public final <T extends java.lang.CharSequence> boolean containsSome(java.lang.Iterable<T> collection) { throw new RuntimeException("Stub!"); }

/**
 * @see #addAll(android.icu.text.UnicodeSet)
 */

public <T extends java.lang.CharSequence> android.icu.text.UnicodeSet addAll(T... collection) { throw new RuntimeException("Stub!"); }

/**
 * @see #removeAll(android.icu.text.UnicodeSet)
 */

public <T extends java.lang.CharSequence> android.icu.text.UnicodeSet removeAll(java.lang.Iterable<T> collection) { throw new RuntimeException("Stub!"); }

/**
 * @see #retainAll(android.icu.text.UnicodeSet)
 */

public <T extends java.lang.CharSequence> android.icu.text.UnicodeSet retainAll(java.lang.Iterable<T> collection) { throw new RuntimeException("Stub!"); }

/**
 * Compares UnicodeSets, where shorter come first, and otherwise lexigraphically
 * (according to the comparison of the first characters that differ).
 * @see java.lang.Comparable#compareTo(java.lang.Object)
 */

public int compareTo(android.icu.text.UnicodeSet o) { throw new RuntimeException("Stub!"); }

/**
 * Compares UnicodeSets, in three different ways.
 * @see java.lang.Comparable#compareTo(java.lang.Object)
 */

public int compareTo(android.icu.text.UnicodeSet o, android.icu.text.UnicodeSet.ComparisonStyle style) { throw new RuntimeException("Stub!"); }

/**
 */

public int compareTo(java.lang.Iterable<java.lang.String> other) { throw new RuntimeException("Stub!"); }

/**
 * For iterating through the strings in the set. Example:
 * <pre>
 * for (String key : myUnicodeSet.strings()) {
 *   doSomethingWith(key);
 * }
 * </pre>
 */

public java.util.Collection<java.lang.String> strings() { throw new RuntimeException("Stub!"); }

/**
 * Bitmask for constructor, applyPattern(), and closeOver()
 * indicating letter case.  This may be ORed together with other
 * selectors.
 *
 * Enable case insensitive matching.  E.g., "[ab]" with this flag
 * will match 'a', 'A', 'b', and 'B'.  "[^ab]" with this flag will
 * match all except 'a', 'A', 'b', and 'B'. This adds the lower-,
 * title-, and uppercase mappings as well as the case folding
 * of each existing element in the set.
 */

public static final int ADD_CASE_MAPPINGS = 4; // 0x4

/**
 * Constant for the set of all code points. (Since UnicodeSets can include strings, does not include everything that a UnicodeSet can.)
 */

public static final android.icu.text.UnicodeSet ALL_CODE_POINTS;
static { ALL_CODE_POINTS = null; }

/**
 * Bitmask for constructor, applyPattern(), and closeOver()
 * indicating letter case.  This may be ORed together with other
 * selectors.
 *
 * Enable case insensitive matching.  E.g., "[ab]" with this flag
 * will match 'a', 'A', 'b', and 'B'.  "[^ab]" with this flag will
 * match all except 'a', 'A', 'b', and 'B'. This performs a full
 * closure over case mappings, e.g. U+017F for s.
 *
 * The resulting set is a superset of the input for the code points but
 * not for the strings.
 * It performs a case mapping closure of the code points and adds
 * full case folding strings for the code points, and reduces strings of
 * the original set to their full case folding equivalents.
 *
 * This is designed for case-insensitive matches, for example
 * in regular expressions. The full code point case closure allows checking of
 * an input character directly against the closure set.
 * Strings are matched by comparing the case-folded form from the closure
 * set with an incremental case folding of the string in question.
 *
 * The closure set will also contain single code points if the original
 * set contained case-equivalent strings (like U+00DF for "ss" or "Ss" etc.).
 * This is not necessary (that is, redundant) for the above matching method
 * but results in the same closure sets regardless of whether the original
 * set contained the code point or a string.
 */

public static final int CASE = 2; // 0x2

/**
 * Alias for UnicodeSet.CASE, for ease of porting from C++ where ICU4C
 * also has both USET_CASE and USET_CASE_INSENSITIVE (see uset.h).
 * @see #CASE
 */

public static final int CASE_INSENSITIVE = 2; // 0x2

/**
 * Constant for the empty set.
 */

public static final android.icu.text.UnicodeSet EMPTY;
static { EMPTY = null; }

/**
 * Bitmask for constructor and applyPattern() indicating that
 * white space should be ignored.  If set, ignore Unicode Pattern_White_Space characters,
 * unless they are quoted or escaped.  This may be ORed together
 * with other selectors.
 */

public static final int IGNORE_SPACE = 1; // 0x1

/**
 * Maximum value that can be stored in a UnicodeSet.
 */

public static final int MAX_VALUE = 1114111; // 0x10ffff

/**
 * Minimum value that can be stored in a UnicodeSet.
 */

public static final int MIN_VALUE = 0; // 0x0
/**
 * Comparison style enums used by {@link android.icu.text.UnicodeSet#compareTo(android.icu.text.UnicodeSet,android.icu.text.UnicodeSet.ComparisonStyle) UnicodeSet#compareTo(UnicodeSet, ComparisonStyle)}.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public enum ComparisonStyle {
/**
 */

SHORTER_FIRST,
/**
 */

LEXICOGRAPHIC,
/**
 */

LONGER_FIRST;
}

/**
 * A struct-like class used for iteration through ranges, for faster iteration than by String.
 * Read about the restrictions on usage in {@link android.icu.text.UnicodeSet#ranges() UnicodeSet#ranges()}.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static class EntryRange {

EntryRange() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.String toString() { throw new RuntimeException("Stub!"); }

/**
 * The starting code point of the range.
 */

public int codepoint;

/**
 * The ending code point of the range
 */

public int codepointEnd;
}

/**
 * Argument values for whether span() and similar functions continue while the current character is contained vs.
 * not contained in the set.
 * <p>
 * The functionality is straightforward for sets with only single code points, without strings (which is the common
 * case):
 * <ul>
 * <li>CONTAINED and SIMPLE work the same.
 * <li>CONTAINED and SIMPLE are inverses of NOT_CONTAINED.
 * <li>span() and spanBack() partition any string the
 * same way when alternating between span(NOT_CONTAINED) and span(either "contained" condition).
 * <li>Using a
 * complemented (inverted) set and the opposite span conditions yields the same results.
 * </ul>
 * When a set contains multi-code point strings, then these statements may not be true, depending on the strings in
 * the set (for example, whether they overlap with each other) and the string that is processed. For a set with
 * strings:
 * <ul>
 * <li>The complement of the set contains the opposite set of code points, but the same set of strings.
 * Therefore, complementing both the set and the span conditions may yield different results.
 * <li>When starting spans
 * at different positions in a string (span(s, ...) vs. span(s+1, ...)) the ends of the spans may be different
 * because a set string may start before the later position.
 * <li>span(SIMPLE) may be shorter than
 * span(CONTAINED) because it will not recursively try all possible paths. For example, with a set which
 * contains the three strings "xy", "xya" and "ax", span("xyax", CONTAINED) will return 4 but span("xyax",
 * SIMPLE) will return 3. span(SIMPLE) will never be longer than span(CONTAINED).
 * <li>With either "contained" condition, span() and spanBack() may partition a string in different ways. For example,
 * with a set which contains the two strings "ab" and "ba", and when processing the string "aba", span() will yield
 * contained/not-contained boundaries of { 0, 2, 3 } while spanBack() will yield boundaries of { 0, 1, 3 }.
 * </ul>
 * Note: If it is important to get the same boundaries whether iterating forward or backward through a string, then
 * either only span() should be used and the boundaries cached for backward operation, or an ICU BreakIterator could
 * be used.
 * <p>
 * Note: Unpaired surrogates are treated like surrogate code points. Similarly, set strings match only on code point
 * boundaries, never in the middle of a surrogate pair.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public enum SpanCondition {
/**
 * Continues a span() while there is no set element at the current position.
 * Increments by one code point at a time.
 * Stops before the first set element (character or string).
 * (For code points only, this is like while contains(current)==false).
 * <p>
 * When span() returns, the substring between where it started and the position it returned consists only of
 * characters that are not in the set, and none of its strings overlap with the span.
 */

NOT_CONTAINED,
/**
 * Spans the longest substring that is a concatenation of set elements (characters or strings).
 * (For characters only, this is like while contains(current)==true).
 * <p>
 * When span() returns, the substring between where it started and the position it returned consists only of set
 * elements (characters or strings) that are in the set.
 * <p>
 * If a set contains strings, then the span will be the longest substring for which there
 * exists at least one non-overlapping concatenation of set elements (characters or strings).
 * This is equivalent to a POSIX regular expression for <code>(OR of each set element)*</code>.
 * (Java/ICU/Perl regex stops at the first match of an OR.)
 */

CONTAINED,
/**
 * Continues a span() while there is a set element at the current position.
 * Increments by the longest matching element at each position.
 * (For characters only, this is like while contains(current)==true).
 * <p>
 * When span() returns, the substring between where it started and the position it returned consists only of set
 * elements (characters or strings) that are in the set.
 * <p>
 * If a set only contains single characters, then this is the same as CONTAINED.
 * <p>
 * If a set contains strings, then the span will be the longest substring with a match at each position with the
 * longest single set element (character or string).
 * <p>
 * Use this span condition together with other longest-match algorithms, such as ICU converters
 * (ucnv_getUnicodeSet()).
 */

SIMPLE,
/**
 * One more than the last span condition.
 */

CONDITION_COUNT;
}

}

