/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 1996-2014, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */

package android.icu.text;


/**
 * UnicodeSetIterator iterates over the contents of a UnicodeSet.  It
 * iterates over either code points or code point ranges.  After all
 * code points or ranges have been returned, it returns the
 * multicharacter strings of the UnicodSet, if any.
 *
 * <p>To iterate over code points and multicharacter strings,
 * use a loop like this:
 * <pre>
 * for (UnicodeSetIterator it = new UnicodeSetIterator(set); it.next();) {
 *   processString(it.getString());
 * }
 * </pre>
 *
 * <p>To iterate over code point ranges, use a loop like this:
 * <pre>
 * for (UnicodeSetIterator it = new UnicodeSetIterator(set); it.nextRange();) {
 *   if (it.codepoint != UnicodeSetIterator.IS_STRING) {
 *     processCodepointRange(it.codepoint, it.codepointEnd);
 *   } else {
 *     processString(it.getString());
 *   }
 * }
 * </pre>
 * <p><b>Warning: </b>For speed, UnicodeSet iteration does not check for concurrent modification.
 * Do not alter the UnicodeSet while iterating.
 * @author M. Davis
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class UnicodeSetIterator {

/**
 * Create an iterator over the given set.
 * @param set set to iterate over
 */

public UnicodeSetIterator(android.icu.text.UnicodeSet set) { throw new RuntimeException("Stub!"); }

/**
 * Create an iterator over nothing.  <tt>next()</tt> and
 * <tt>nextRange()</tt> return false. This is a convenience
 * constructor allowing the target to be set later.
 */

public UnicodeSetIterator() { throw new RuntimeException("Stub!"); }

/**
 * Returns the next element in the set, either a single code point
 * or a string.  If there are no more elements in the set, return
 * false.  If <tt>codepoint == IS_STRING</tt>, the value is a
 * string in the <tt>string</tt> field.  Otherwise the value is a
 * single code point in the <tt>codepoint</tt> field.
 *
 * <p>The order of iteration is all code points in sorted order,
 * followed by all strings sorted order.  <tt>codepointEnd</tt> is
 * undefined after calling this method.  <tt>string</tt> is
 * undefined unless <tt>codepoint == IS_STRING</tt>.  Do not mix
 * calls to <tt>next()</tt> and <tt>nextRange()</tt> without
 * calling <tt>reset()</tt> between them.  The results of doing so
 * are undefined.
 * <p><b>Warning: </b>For speed, UnicodeSet iteration does not check for concurrent modification.
 * Do not alter the UnicodeSet while iterating.
 * @return true if there was another element in the set and this
 * object contains the element.
 */

public boolean next() { throw new RuntimeException("Stub!"); }

/**
 * Returns the next element in the set, either a code point range
 * or a string.  If there are no more elements in the set, return
 * false.  If <tt>codepoint == IS_STRING</tt>, the value is a
 * string in the <tt>string</tt> field.  Otherwise the value is a
 * range of one or more code points from <tt>codepoint</tt> to
 * <tt>codepointeEnd</tt> inclusive.
 *
 * <p>The order of iteration is all code points ranges in sorted
 * order, followed by all strings sorted order.  Ranges are
 * disjoint and non-contiguous.  <tt>string</tt> is undefined
 * unless <tt>codepoint == IS_STRING</tt>.  Do not mix calls to
 * <tt>next()</tt> and <tt>nextRange()</tt> without calling
 * <tt>reset()</tt> between them.  The results of doing so are
 * undefined.
 *
 * @return true if there was another element in the set and this
 * object contains the element.
 */

public boolean nextRange() { throw new RuntimeException("Stub!"); }

/**
 * Sets this iterator to visit the elements of the given set and
 * resets it to the start of that set.  The iterator is valid only
 * so long as <tt>set</tt> is valid.
 * @param uset the set to iterate over.
 */

public void reset(android.icu.text.UnicodeSet uset) { throw new RuntimeException("Stub!"); }

/**
 * Resets this iterator to the start of the set.
 */

public void reset() { throw new RuntimeException("Stub!"); }

/**
 * Gets the current string from the iterator. Only use after calling next(), not nextRange().
 */

public java.lang.String getString() { throw new RuntimeException("Stub!"); }

/**
 * Value of <tt>codepoint</tt> if the iterator points to a string.
 * If <tt>codepoint == IS_STRING</tt>, then examine
 * <tt>string</tt> for the current iteration result.
 */

public static int IS_STRING = -1; // 0xffffffff

/**
 * Current code point, or the special value <tt>IS_STRING</tt>, if
 * the iterator points to a string.
 */

public int codepoint;

/**
 * When iterating over ranges using <tt>nextRange()</tt>,
 * <tt>codepointEnd</tt> contains the inclusive end of the
 * iteration range, if <tt>codepoint != IS_STRING</tt>.  If
 * iterating over code points using <tt>next()</tt>, or if
 * <tt>codepoint == IS_STRING</tt>, then the value of
 * <tt>codepointEnd</tt> is undefined.
 */

public int codepointEnd;

/**
 * If <tt>codepoint == IS_STRING</tt>, then <tt>string</tt> points
 * to the current string.  If <tt>codepoint != IS_STRING</tt>, the
 * value of <tt>string</tt> is undefined.
 */

public java.lang.String string;
}

