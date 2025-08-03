/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html

package android.icu.text;


/**
 * Records lengths of string edits but not replacement text. Supports replacements, insertions, deletions
 * in linear progression. Does not support moving/reordering of text.
 * <p>
 * There are two types of edits: <em>change edits</em> and <em>no-change edits</em>. Add edits to
 * instances of this class using {@link #addReplace(int,int)} (for change edits) and
 * {@link #addUnchanged(int)} (for no-change edits). Change edits are retained with full granularity,
 * whereas adjacent no-change edits are always merged together. In no-change edits, there is a one-to-one
 * mapping between code points in the source and destination strings.
 * <p>
 * After all edits have been added, instances of this class should be considered immutable, and an
 * {@link android.icu.text.Edits.Iterator Edits.Iterator} can be used for queries.
 * <p>
 * There are four flavors of Edits.Iterator:
 * <ul>
 * <li>{@link #getFineIterator()} retains full granularity of change edits.
 * <li>{@link #getFineChangesIterator()} retains full granularity of change edits, and when calling
 * next() on the iterator, skips over no-change edits (unchanged regions).
 * <li>{@link #getCoarseIterator()} treats adjacent change edits as a single edit. (Adjacent no-change
 * edits are automatically merged during the construction phase.)
 * <li>{@link #getCoarseChangesIterator()} treats adjacent change edits as a single edit, and when
 * calling next() on the iterator, skips over no-change edits (unchanged regions).
 * </ul>
 * <p>
 * For example, consider the string "abc?DeF", which case-folds to "abcssdef". This string has the
 * following fine edits:
 * <ul>
 * <li>abc ? abc (no-change)
 * <li>? ? ss (change)
 * <li>D ? d (change)
 * <li>e ? e (no-change)
 * <li>F ? f (change)
 * </ul>
 * and the following coarse edits (note how adjacent change edits get merged together):
 * <ul>
 * <li>abc ? abc (no-change)
 * <li>?D ? ssd (change)
 * <li>e ? e (no-change)
 * <li>F ? f (change)
 * </ul>
 * <p>
 * The "fine changes" and "coarse changes" iterators will step through only the change edits when their
 * {@link android.icu.text.Edits.Iterator#next() Edits.Iterator#next()} methods are called. They are identical to the non-change iterators when
 * their {@link android.icu.text.Edits.Iterator#findSourceIndex(int) Edits.Iterator#findSourceIndex(int)} method is used to walk through the string.
 * <p>
 * For examples of how to use this class, see the test <code>TestCaseMapEditsIteratorDocs</code> in
 * UCharacterCaseTest.java.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class Edits {

/**
 * Constructs an empty object.
 */

public Edits() { throw new RuntimeException("Stub!"); }

/**
 * Resets the data but may not release memory.
 */

public void reset() { throw new RuntimeException("Stub!"); }

/**
 * Adds a no-change edit: a record for an unchanged segment of text.
 * Normally called from inside ICU string transformation functions, not user code.
 */

public void addUnchanged(int unchangedLength) { throw new RuntimeException("Stub!"); }

/**
 * Adds a change edit: a record for a text replacement/insertion/deletion.
 * Normally called from inside ICU string transformation functions, not user code.
 */

public void addReplace(int oldLength, int newLength) { throw new RuntimeException("Stub!"); }

/**
 * How much longer is the new text compared with the old text?
 * @return new length minus old length
 */

public int lengthDelta() { throw new RuntimeException("Stub!"); }

/**
 * @return true if there are any change edits
 */

public boolean hasChanges() { throw new RuntimeException("Stub!"); }

/**
 * @return the number of change edits
 */

public int numberOfChanges() { throw new RuntimeException("Stub!"); }

/**
 * Returns an Iterator for coarse-grained change edits
 * (adjacent change edits are treated as one).
 * Can be used to perform simple string updates.
 * Skips no-change edits.
 * @return an Iterator that merges adjacent changes.
 */

public android.icu.text.Edits.Iterator getCoarseChangesIterator() { throw new RuntimeException("Stub!"); }

/**
 * Returns an Iterator for coarse-grained change and no-change edits
 * (adjacent change edits are treated as one).
 * Can be used to perform simple string updates.
 * Adjacent change edits are treated as one edit.
 * @return an Iterator that merges adjacent changes.
 */

public android.icu.text.Edits.Iterator getCoarseIterator() { throw new RuntimeException("Stub!"); }

/**
 * Returns an Iterator for fine-grained change edits
 * (full granularity of change edits is retained).
 * Can be used for modifying styled text.
 * Skips no-change edits.
 * @return an Iterator that separates adjacent changes.
 */

public android.icu.text.Edits.Iterator getFineChangesIterator() { throw new RuntimeException("Stub!"); }

/**
 * Returns an Iterator for fine-grained change and no-change edits
 * (full granularity of change edits is retained).
 * Can be used for modifying styled text.
 * @return an Iterator that separates adjacent changes.
 */

public android.icu.text.Edits.Iterator getFineIterator() { throw new RuntimeException("Stub!"); }

/**
 * Merges the two input Edits and appends the result to this object.
 *
 * <p>Consider two string transformations (for example, normalization and case mapping)
 * where each records Edits in addition to writing an output string.<br>
 * Edits ab reflect how substrings of input string a
 * map to substrings of intermediate string b.<br>
 * Edits bc reflect how substrings of intermediate string b
 * map to substrings of output string c.<br>
 * This function merges ab and bc such that the additional edits
 * recorded in this object reflect how substrings of input string a
 * map to substrings of output string c.
 *
 * <p>If unrelated Edits are passed in where the output string of the first
 * has a different length than the input string of the second,
 * then an IllegalArgumentException is thrown.
 *
 * @param ab reflects how substrings of input string a
 *     map to substrings of intermediate string b.
 * @param bc reflects how substrings of intermediate string b
 *     map to substrings of output string c.
 * @return this, with the merged edits appended
 */

public android.icu.text.Edits mergeAndAppend(android.icu.text.Edits ab, android.icu.text.Edits bc) { throw new RuntimeException("Stub!"); }
/**
 * Access to the list of edits.
 * <p>
 * At any moment in time, an instance of this class points to a single edit: a "window" into a span
 * of the source string and the corresponding span of the destination string. The source string span
 * starts at {@link #sourceIndex()} and runs for {@link #oldLength()} chars; the destination string
 * span starts at {@link #destinationIndex()} and runs for {@link #newLength()} chars.
 * <p>
 * The iterator can be moved between edits using the {@link #next()} and
 * {@link #findSourceIndex(int)} methods. Calling any of these methods mutates the iterator
 * to make it point to the corresponding edit.
 * <p>
 * For more information, see the documentation for {@link android.icu.text.Edits Edits}.
 * <p>
 * Note: Although this class is called "Iterator", it does not implement {@link java.util.Iterator}.
 *
 * @see #getCoarseIterator
 * @see #getFineIterator
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static final class Iterator {

private Iterator() { throw new RuntimeException("Stub!"); }

/**
 * Advances the iterator to the next edit.
 * @return true if there is another edit
 */

public boolean next() { throw new RuntimeException("Stub!"); }

/**
 * Moves the iterator to the edit that contains the source index.
 * The source index may be found in a no-change edit
 * even if normal iteration would skip no-change edits.
 * Normal iteration can continue from a found edit.
 *
 * <p>The iterator state before this search logically does not matter.
 * (It may affect the performance of the search.)
 *
 * <p>The iterator state after this search is undefined
 * if the source index is out of bounds for the source string.
 *
 * @param i source index
 * @return true if the edit for the source index was found
 */

public boolean findSourceIndex(int i) { throw new RuntimeException("Stub!"); }

/**
 * Moves the iterator to the edit that contains the destination index.
 * The destination index may be found in a no-change edit
 * even if normal iteration would skip no-change edits.
 * Normal iteration can continue from a found edit.
 *
 * <p>The iterator state before this search logically does not matter.
 * (It may affect the performance of the search.)
 *
 * <p>The iterator state after this search is undefined
 * if the source index is out of bounds for the source string.
 *
 * @param i destination index
 * @return true if the edit for the destination index was found
 */

public boolean findDestinationIndex(int i) { throw new RuntimeException("Stub!"); }

/**
 * Computes the destination index corresponding to the given source index.
 * If the source index is inside a change edit (not at its start),
 * then the destination index at the end of that edit is returned,
 * since there is no information about index mapping inside a change edit.
 *
 * <p>(This means that indexes to the start and middle of an edit,
 * for example around a grapheme cluster, are mapped to indexes
 * encompassing the entire edit.
 * The alternative, mapping an interior index to the start,
 * would map such an interval to an empty one.)
 *
 * <p>This operation will usually but not always modify this object.
 * The iterator state after this search is undefined.
 *
 * @param i source index
 * @return destination index; undefined if i is not 0..string length
 */

public int destinationIndexFromSourceIndex(int i) { throw new RuntimeException("Stub!"); }

/**
 * Computes the source index corresponding to the given destination index.
 * If the destination index is inside a change edit (not at its start),
 * then the source index at the end of that edit is returned,
 * since there is no information about index mapping inside a change edit.
 *
 * <p>(This means that indexes to the start and middle of an edit,
 * for example around a grapheme cluster, are mapped to indexes
 * encompassing the entire edit.
 * The alternative, mapping an interior index to the start,
 * would map such an interval to an empty one.)
 *
 * <p>This operation will usually but not always modify this object.
 * The iterator state after this search is undefined.
 *
 * @param i destination index
 * @return source index; undefined if i is not 0..string length
 */

public int sourceIndexFromDestinationIndex(int i) { throw new RuntimeException("Stub!"); }

/**
 * Returns whether the edit currently represented by the iterator is a change edit.
 *
 * @return true if this edit replaces oldLength() units with newLength() different ones.
 *         false if oldLength units remain unchanged.
 */

public boolean hasChange() { throw new RuntimeException("Stub!"); }

/**
 * The length of the current span in the source string, which starts at {@link #sourceIndex}.
 *
 * @return the number of units in the source string which are replaced or remain unchanged.
 */

public int oldLength() { throw new RuntimeException("Stub!"); }

/**
 * The length of the current span in the destination string, which starts at
 * {@link #destinationIndex}, or in the replacement string, which starts at
 * {@link #replacementIndex}.
 *
 * @return the number of units in the destination string, if hasChange() is true. Same as
 *         oldLength if hasChange() is false.
 */

public int newLength() { throw new RuntimeException("Stub!"); }

/**
 * The start index of the current span in the source string; the span has length
 * {@link #oldLength}.
 *
 * @return the current index into the source string
 */

public int sourceIndex() { throw new RuntimeException("Stub!"); }

/**
 * The start index of the current span in the replacement string; the span has length
 * {@link #newLength}. Well-defined only if the current edit is a change edit.
 * <p>
 * The <em>replacement string</em> is the concatenation of all substrings of the destination
 * string corresponding to change edits.
 * <p>
 * This method is intended to be used together with operations that write only replacement
 * characters (e.g., {@link android.icu.text.CaseMap#omitUnchangedText() CaseMap#omitUnchangedText()}). The source string can then be modified
 * in-place.
 *
 * @return the current index into the replacement-characters-only string, not counting unchanged
 *         spans
 */

public int replacementIndex() { throw new RuntimeException("Stub!"); }

/**
 * The start index of the current span in the destination string; the span has length
 * {@link #newLength}.
 *
 * @return the current index into the full destination string
 */

public int destinationIndex() { throw new RuntimeException("Stub!"); }

/**
 * A string representation of the current edit represented by the iterator for debugging. You
 * should not depend on the contents of the return string; it may change over time.
 * @return a string representation of the object.
 */

public java.lang.String toString() { throw new RuntimeException("Stub!"); }
}

}

