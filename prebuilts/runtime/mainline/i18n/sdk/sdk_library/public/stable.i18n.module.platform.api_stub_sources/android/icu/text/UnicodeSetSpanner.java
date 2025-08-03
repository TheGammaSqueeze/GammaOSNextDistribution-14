/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2014-2016, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */

package android.icu.text;

import android.icu.text.UnicodeSet.SpanCondition;

/**
 * A helper class used to count, replace, and trim CharSequences based on UnicodeSet matches.
 * An instance is immutable (and thus thread-safe) iff the source UnicodeSet is frozen.
 * <p><b>Note:</b> The counting, deletion, and replacement depend on alternating a {@link android.icu.text.UnicodeSet.SpanCondition SpanCondition} with
 * its inverse. That is, the code spans, then spans for the inverse, then spans, and so on.
 * For the inverse, the following mapping is used:
 * <ul>
 * <li>{@link android.icu.text.UnicodeSet.SpanCondition#SIMPLE UnicodeSet.SpanCondition#SIMPLE} ? {@link android.icu.text.UnicodeSet.SpanCondition#NOT_CONTAINED UnicodeSet.SpanCondition#NOT_CONTAINED}</li>
 * <li>{@link android.icu.text.UnicodeSet.SpanCondition#CONTAINED UnicodeSet.SpanCondition#CONTAINED} ? {@link android.icu.text.UnicodeSet.SpanCondition#NOT_CONTAINED UnicodeSet.SpanCondition#NOT_CONTAINED}</li>
 * <li>{@link android.icu.text.UnicodeSet.SpanCondition#NOT_CONTAINED UnicodeSet.SpanCondition#NOT_CONTAINED} ? {@link android.icu.text.UnicodeSet.SpanCondition#SIMPLE UnicodeSet.SpanCondition#SIMPLE}</li>
 * </ul>
 * These are actually not complete inverses. However, the alternating works because there are no gaps.
 * For example, with [a{ab}{bc}], you get the following behavior when scanning forward:
 *
 * <table border="1">
 * <tr><th>SIMPLE</th><td>xxx[ab]cyyy</td></tr>
 * <tr><th>CONTAINED</th><td>xxx[abc]yyy</td></tr>
 * <tr><th>NOT_CONTAINED</th><td>[xxx]ab[cyyy]</td></tr>
 * </table>
 * <p>So here is what happens when you alternate:
 *
 * <table border="1">
 * <tr><th>start</th><td>|xxxabcyyy</td></tr>
 * <tr><th>NOT_CONTAINED</th><td>xxx|abcyyy</td></tr>
 * <tr><th>CONTAINED</th><td>xxxabc|yyy</td></tr>
 * <tr><th>NOT_CONTAINED</th><td>xxxabcyyy|</td></tr>
 * </table>
 * <p>The entire string is traversed.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class UnicodeSetSpanner {

/**
 * Create a spanner from a UnicodeSet. For speed and safety, the UnicodeSet should be frozen. However, this class
 * can be used with a non-frozen version to avoid the cost of freezing.
 *
 * @param source
 *            the original UnicodeSet
 */

public UnicodeSetSpanner(android.icu.text.UnicodeSet source) { throw new RuntimeException("Stub!"); }

/**
 * Returns the UnicodeSet used for processing. It is frozen iff the original was.
 *
 * @return the construction set.
 */

public android.icu.text.UnicodeSet getUnicodeSet() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public boolean equals(java.lang.Object other) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public int hashCode() { throw new RuntimeException("Stub!"); }

/**
 * Returns the number of matching characters found in a character sequence,
 * counting by CountMethod.MIN_ELEMENTS using SpanCondition.SIMPLE.
 * The code alternates spans; see the class doc for {@link android.icu.text.UnicodeSetSpanner UnicodeSetSpanner} for a note about boundary conditions.
 * @param sequence
 *            the sequence to count characters in
 * @return the count. Zero if there are none.
 */

public int countIn(java.lang.CharSequence sequence) { throw new RuntimeException("Stub!"); }

/**
 * Returns the number of matching characters found in a character sequence, using SpanCondition.SIMPLE.
 * The code alternates spans; see the class doc for {@link android.icu.text.UnicodeSetSpanner UnicodeSetSpanner} for a note about boundary conditions.
 * @param sequence
 *            the sequence to count characters in
 * @param countMethod
 *            whether to treat an entire span as a match, or individual elements as matches
 * @return the count. Zero if there are none.
 */

public int countIn(java.lang.CharSequence sequence, android.icu.text.UnicodeSetSpanner.CountMethod countMethod) { throw new RuntimeException("Stub!"); }

/**
 * Returns the number of matching characters found in a character sequence.
 * The code alternates spans; see the class doc for {@link android.icu.text.UnicodeSetSpanner UnicodeSetSpanner} for a note about boundary conditions.
 * @param sequence
 *            the sequence to count characters in
 * @param countMethod
 *            whether to treat an entire span as a match, or individual elements as matches
 * @param spanCondition
 *            the spanCondition to use. SIMPLE or CONTAINED means only count the elements in the span;
 *            NOT_CONTAINED is the reverse.
 *            <br><b>WARNING: </b> when a UnicodeSet contains strings, there may be unexpected behavior in edge cases.
 * @return the count. Zero if there are none.
 */

public int countIn(java.lang.CharSequence sequence, android.icu.text.UnicodeSetSpanner.CountMethod countMethod, android.icu.text.UnicodeSet.SpanCondition spanCondition) { throw new RuntimeException("Stub!"); }

/**
 * Delete all the matching spans in sequence, using SpanCondition.SIMPLE
 * The code alternates spans; see the class doc for {@link android.icu.text.UnicodeSetSpanner UnicodeSetSpanner} for a note about boundary conditions.
 * @param sequence
 *            charsequence to replace matching spans in.
 * @return modified string.
 */

public java.lang.String deleteFrom(java.lang.CharSequence sequence) { throw new RuntimeException("Stub!"); }

/**
 * Delete all matching spans in sequence, according to the spanCondition.
 * The code alternates spans; see the class doc for {@link android.icu.text.UnicodeSetSpanner UnicodeSetSpanner} for a note about boundary conditions.
 * @param sequence
 *            charsequence to replace matching spans in.
 * @param spanCondition
 *            specify whether to modify the matching spans (CONTAINED or SIMPLE) or the non-matching (NOT_CONTAINED)
 * @return modified string.
 */

public java.lang.String deleteFrom(java.lang.CharSequence sequence, android.icu.text.UnicodeSet.SpanCondition spanCondition) { throw new RuntimeException("Stub!"); }

/**
 * Replace all matching spans in sequence by the replacement,
 * counting by CountMethod.MIN_ELEMENTS using SpanCondition.SIMPLE.
 * The code alternates spans; see the class doc for {@link android.icu.text.UnicodeSetSpanner UnicodeSetSpanner} for a note about boundary conditions.
 * @param sequence
 *            charsequence to replace matching spans in.
 * @param replacement
 *            replacement sequence. To delete, use ""
 * @return modified string.
 */

public java.lang.String replaceFrom(java.lang.CharSequence sequence, java.lang.CharSequence replacement) { throw new RuntimeException("Stub!"); }

/**
 * Replace all matching spans in sequence by replacement, according to the CountMethod, using SpanCondition.SIMPLE.
 * The code alternates spans; see the class doc for {@link android.icu.text.UnicodeSetSpanner UnicodeSetSpanner} for a note about boundary conditions.
 *
 * @param sequence
 *            charsequence to replace matching spans in.
 * @param replacement
 *            replacement sequence. To delete, use ""
 * @param countMethod
 *            whether to treat an entire span as a match, or individual elements as matches
 * @return modified string.
 */

public java.lang.String replaceFrom(java.lang.CharSequence sequence, java.lang.CharSequence replacement, android.icu.text.UnicodeSetSpanner.CountMethod countMethod) { throw new RuntimeException("Stub!"); }

/**
 * Replace all matching spans in sequence by replacement, according to the countMethod and spanCondition.
 * The code alternates spans; see the class doc for {@link android.icu.text.UnicodeSetSpanner UnicodeSetSpanner} for a note about boundary conditions.
 * @param sequence
 *            charsequence to replace matching spans in.
 * @param replacement
 *            replacement sequence. To delete, use ""
 * @param countMethod
 *            whether to treat an entire span as a match, or individual elements as matches
 * @param spanCondition
 *            specify whether to modify the matching spans (CONTAINED or SIMPLE) or the non-matching
 *            (NOT_CONTAINED)
 * @return modified string.
 */

public java.lang.String replaceFrom(java.lang.CharSequence sequence, java.lang.CharSequence replacement, android.icu.text.UnicodeSetSpanner.CountMethod countMethod, android.icu.text.UnicodeSet.SpanCondition spanCondition) { throw new RuntimeException("Stub!"); }

/**
 * Returns a trimmed sequence (using CharSequence.subsequence()), that omits matching elements at the start and
 * end of the string, using TrimOption.BOTH and SpanCondition.SIMPLE. For example:
 *
 * <pre>
 * {@code
 *
 *   new UnicodeSet("[ab]").trim("abacatbab")}
 * </pre>
 *
 * ... returns {@code "cat"}.
 * @param sequence
 *            the sequence to trim
 * @return a subsequence
 */

public java.lang.CharSequence trim(java.lang.CharSequence sequence) { throw new RuntimeException("Stub!"); }

/**
 * Returns a trimmed sequence (using CharSequence.subsequence()), that omits matching elements at the start or
 * end of the string, using the trimOption and SpanCondition.SIMPLE. For example:
 *
 * <pre>
 * {@code
 *
 *   new UnicodeSet("[ab]").trim("abacatbab", TrimOption.LEADING)}
 * </pre>
 *
 * ... returns {@code "catbab"}.
 *
 * @param sequence
 *            the sequence to trim
 * @param trimOption
 *            LEADING, TRAILING, or BOTH
 * @return a subsequence
 */

public java.lang.CharSequence trim(java.lang.CharSequence sequence, android.icu.text.UnicodeSetSpanner.TrimOption trimOption) { throw new RuntimeException("Stub!"); }

/**
 * Returns a trimmed sequence (using CharSequence.subsequence()), that omits matching elements at the start or
 * end of the string, depending on the trimOption and spanCondition. For example:
 *
 * <pre>
 * {@code
 *
 *   new UnicodeSet("[ab]").trim("abacatbab", TrimOption.LEADING, SpanCondition.SIMPLE)}
 * </pre>
 *
 * ... returns {@code "catbab"}.
 *
 * @param sequence
 *            the sequence to trim
 * @param trimOption
 *            LEADING, TRAILING, or BOTH
 * @param spanCondition
 *            SIMPLE, CONTAINED or NOT_CONTAINED
 * @return a subsequence
 */

public java.lang.CharSequence trim(java.lang.CharSequence sequence, android.icu.text.UnicodeSetSpanner.TrimOption trimOption, android.icu.text.UnicodeSet.SpanCondition spanCondition) { throw new RuntimeException("Stub!"); }
/**
 * Options for replaceFrom and countIn to control how to treat each matched span.
 * It is similar to whether one is replacing [abc] by x, or [abc]* by x.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public enum CountMethod {
/**
 * Collapse spans. That is, modify/count the entire matching span as a single item, instead of separate
 * set elements.
 */

WHOLE_SPAN,
/**
 * Use the smallest number of elements in the spanned range for counting and modification,
 * based on the {@link android.icu.text.UnicodeSet.SpanCondition UnicodeSet.SpanCondition}.
 * If the set has no strings, this will be the same as the number of spanned code points.
 * <p>For example, in the string "abab" with SpanCondition.SIMPLE:
 * <ul>
 * <li>spanning with [ab] will count four MIN_ELEMENTS.</li>
 * <li>spanning with [{ab}] will count two MIN_ELEMENTS.</li>
 * <li>spanning with [ab{ab}] will also count two MIN_ELEMENTS.</li>
 * </ul>
 */

MIN_ELEMENTS;
}

/**
 * Options for the trim() method
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public enum TrimOption {
/**
 * Trim leading spans.
 */

LEADING,
/**
 * Trim leading and trailing spans.
 */

BOTH,
/**
 * Trim trailing spans.
 */

TRAILING;
}

}

