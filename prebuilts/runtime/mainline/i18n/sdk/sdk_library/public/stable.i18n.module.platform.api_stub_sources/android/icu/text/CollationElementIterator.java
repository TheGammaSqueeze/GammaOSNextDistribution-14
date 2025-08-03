/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/**
*******************************************************************************
* Copyright (C) 1996-2016, International Business Machines Corporation and
* others. All Rights Reserved.
*******************************************************************************
*/

package android.icu.text;


/**
 * <code>CollationElementIterator</code> is an iterator created by
 * a RuleBasedCollator to walk through a string. The return result of
 * each iteration is a 32-bit collation element (CE) that defines the
 * ordering priority of the next character or sequence of characters
 * in the source string.
 *
 * <p>For illustration, consider the following in Slovak and in traditional Spanish collation:
 * <blockquote>
 * <pre>
 * "ca" -&gt; the first collation element is CE('c') and the second
 *         collation element is CE('a').
 * "cha" -&gt; the first collation element is CE('ch') and the second
 *          collation element is CE('a').
 * </pre>
 * </blockquote>
 * And in German phonebook collation,
 * <blockquote>
 * <pre>
 * Since the character '&#230;' is a composed character of 'a' and 'e', the
 * iterator returns two collation elements for the single character '&#230;'
 *
 * "&#230;b" -&gt; the first collation element is collation_element('a'), the
 *              second collation element is collation_element('e'), and the
 *              third collation element is collation_element('b').
 * </pre>
 * </blockquote>
 *
 * <p>For collation ordering comparison, the collation element results
 * can not be compared simply by using basic arithmetic operators,
 * e.g. &lt;, == or &gt;, further processing has to be done. Details
 * can be found in the ICU
 * <a href="https://unicode-org.github.io/icu/userguide/collation/architecture">
 * User Guide</a>. An example of using the CollationElementIterator
 * for collation ordering comparison is the class
 * {@link android.icu.text.StringSearch}.
 *
 * <p>To construct a CollationElementIterator object, users
 * call the method getCollationElementIterator() on a
 * RuleBasedCollator that defines the desired sorting order.
 *
 * <p> Example:
 * <blockquote>
 * <pre>
 *  String testString = "This is a test";
 *  RuleBasedCollator rbc = new RuleBasedCollator("&amp;a&lt;b");
 *  CollationElementIterator iterator = rbc.getCollationElementIterator(testString);
 *  int primaryOrder = iterator.IGNORABLE;
 *  while (primaryOrder != iterator.NULLORDER) {
 *      int order = iterator.next();
 *      if (order != iterator.IGNORABLE &amp;&amp;
 *          order != iterator.NULLORDER) {
 *          // order is valid, not ignorable and we have not passed the end
 *          // of the iteration, we do something
 *          primaryOrder = CollationElementIterator.primaryOrder(order);
 *          System.out.println("Next primary order 0x" +
 *                             Integer.toHexString(primaryOrder));
 *      }
 *  }
 * </pre>
 * </blockquote>
 * <p>
 * The method next() returns the collation order of the next character based on
 * the comparison level of the collator. The method previous() returns the
 * collation order of the previous character based on the comparison level of
 * the collator. The Collation Element Iterator moves only in one direction
 * between calls to reset(), setOffset(), or setText(). That is, next() and
 * previous() can not be inter-used. Whenever previous() is to be called after
 * next() or vice versa, reset(), setOffset() or setText() has to be called first
 * to reset the status, shifting current position to either the end or the start of
 * the string (reset() or setText()), or the specified position (setOffset()).
 * Hence at the next call of next() or previous(), the first or last collation order,
 * or collation order at the specified position will be returned. If a change of
 * direction is done without one of these calls, the result is undefined.
 * <p>
 * This class is not subclassable.
 * @see android.icu.text.Collator
 * @see android.icu.text.RuleBasedCollator
 * @see android.icu.text.StringSearch
 * @author Syn Wee Quek
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class CollationElementIterator {

CollationElementIterator() { throw new RuntimeException("Stub!"); }

/**
 * Return the primary order of the specified collation element,
 * i.e. the first 16 bits.  This value is unsigned.
 * @param ce the collation element
 * @return the element's 16 bits primary order.
 */

public static int primaryOrder(int ce) { throw new RuntimeException("Stub!"); }

/**
 * Return the secondary order of the specified collation element,
 * i.e. the 16th to 23th bits, inclusive.  This value is unsigned.
 * @param ce the collation element
 * @return the element's 8 bits secondary order
 */

public static int secondaryOrder(int ce) { throw new RuntimeException("Stub!"); }

/**
 * Return the tertiary order of the specified collation element, i.e. the last
 * 8 bits.  This value is unsigned.
 * @param ce the collation element
 * @return the element's 8 bits tertiary order
 */

public static int tertiaryOrder(int ce) { throw new RuntimeException("Stub!"); }

/**
 * Returns the character offset in the source string
 * corresponding to the next collation element. I.e., getOffset()
 * returns the position in the source string corresponding to the
 * collation element that will be returned by the next call to
 * next() or previous(). This value could be any of:
 * <ul>
 * <li> The index of the <b>first</b> character corresponding to
 * the next collation element. (This means that if
 * <code>setOffset(offset)</code> sets the index in the middle of
 * a contraction, <code>getOffset()</code> returns the index of
 * the first character in the contraction, which may not be equal
 * to the original offset that was set. Hence calling getOffset()
 * immediately after setOffset(offset) does not guarantee that the
 * original offset set will be returned.)
 * <li> If normalization is on, the index of the <b>immediate</b>
 * subsequent character, or composite character with the first
 * character, having a combining class of 0.
 * <li> The length of the source string, if iteration has reached
 * the end.
 *</ul>
 *
 * @return The character offset in the source string corresponding to the
 *         collation element that will be returned by the next call to
 *         next() or previous().
 */

public int getOffset() { throw new RuntimeException("Stub!"); }

/**
 * Get the next collation element in the source string.
 *
 * <p>This iterator iterates over a sequence of collation elements
 * that were built from the string. Because there isn't
 * necessarily a one-to-one mapping from characters to collation
 * elements, this doesn't mean the same thing as "return the
 * collation element [or ordering priority] of the next character
 * in the string".
 *
 * <p>This function returns the collation element that the
 * iterator is currently pointing to, and then updates the
 * internal pointer to point to the next element.
 *
 * @return the next collation element or NULLORDER if the end of the
 *         iteration has been reached.
 */

public int next() { throw new RuntimeException("Stub!"); }

/**
 * Get the previous collation element in the source string.
 *
 * <p>This iterator iterates over a sequence of collation elements
 * that were built from the string. Because there isn't
 * necessarily a one-to-one mapping from characters to collation
 * elements, this doesn't mean the same thing as "return the
 * collation element [or ordering priority] of the previous
 * character in the string".
 *
 * <p>This function updates the iterator's internal pointer to
 * point to the collation element preceding the one it's currently
 * pointing to and then returns that element, while next() returns
 * the current element and then updates the pointer.
 *
 * @return the previous collation element, or NULLORDER when the start of
 *             the iteration has been reached.
 */

public int previous() { throw new RuntimeException("Stub!"); }

/**
 * Resets the cursor to the beginning of the string. The next
 * call to next() or previous() will return the first and last
 * collation element in the string, respectively.
 *
 * <p>If the RuleBasedCollator used by this iterator has had its
 * attributes changed, calling reset() will reinitialize the
 * iterator to use the new attributes.
 */

public void reset() { throw new RuntimeException("Stub!"); }

/**
 * Sets the iterator to point to the collation element
 * corresponding to the character at the specified offset. The
 * value returned by the next call to next() will be the collation
 * element corresponding to the characters at offset.
 *
 * <p>If offset is in the middle of a contracting character
 * sequence, the iterator is adjusted to the start of the
 * contracting sequence. This means that getOffset() is not
 * guaranteed to return the same value set by this method.
 *
 * <p>If the decomposition mode is on, and offset is in the middle
 * of a decomposible range of source text, the iterator may not
 * return a correct result for the next forwards or backwards
 * iteration.  The user must ensure that the offset is not in the
 * middle of a decomposible range.
 *
 * @param newOffset the character offset into the original source string to
 *        set. Note that this is not an offset into the corresponding
 *        sequence of collation elements.
 */

public void setOffset(int newOffset) { throw new RuntimeException("Stub!"); }

/**
 * Set a new source string for iteration, and reset the offset
 * to the beginning of the text.
 *
 * @param source the new source string for iteration.
 */

public void setText(java.lang.String source) { throw new RuntimeException("Stub!"); }

/**
 * Set a new source string iterator for iteration, and reset the
 * offset to the beginning of the text.
 *
 * <p>The source iterator's integrity will be preserved since a new copy
 * will be created for use.
 * @param source the new source string iterator for iteration.
 */

public void setText(android.icu.text.UCharacterIterator source) { throw new RuntimeException("Stub!"); }

/**
 * Set a new source string iterator for iteration, and reset the
 * offset to the beginning of the text.
 *
 * @param source the new source string iterator for iteration.
 */

public void setText(java.text.CharacterIterator source) { throw new RuntimeException("Stub!"); }

/**
 * Returns the maximum length of any expansion sequence that ends with
 * the specified collation element. If there is no expansion with this
 * collation element as the last element, returns 1.
 *
 * @param ce a collation element returned by previous() or next().
 * @return the maximum length of any expansion sequence ending
 *         with the specified collation element.
 */

public int getMaxExpansion(int ce) { throw new RuntimeException("Stub!"); }

/**
 * Tests that argument object is equals to this CollationElementIterator.
 * Iterators are equal if the objects uses the same RuleBasedCollator,
 * the same source text and have the same current position in iteration.
 * @param that object to test if it is equals to this
 *             CollationElementIterator
 */

public boolean equals(java.lang.Object that) { throw new RuntimeException("Stub!"); }

/**
 * Mock implementation of hashCode(). This implementation always returns a constant
 * value. When Java assertion is enabled, this method triggers an assertion failure.
 * @hide original deprecated declaration
 */

public int hashCode() { throw new RuntimeException("Stub!"); }

/**
 * This constant is returned by the iterator in the methods
 * next() and previous() when a collation element result is to be
 * ignored.
 *
 * <p>See class documentation for an example of use.
 * @see #next
 * @see #previous */

public static final int IGNORABLE = 0; // 0x0

/**
 * This constant is returned by the iterator in the methods
 * next() and previous() when the end or the beginning of the
 * source string has been reached, and there are no more valid
 * collation elements to return.
 *
 * <p>See class documentation for an example of use.
 * @see #next
 * @see #previous */

public static final int NULLORDER = -1; // 0xffffffff
}

