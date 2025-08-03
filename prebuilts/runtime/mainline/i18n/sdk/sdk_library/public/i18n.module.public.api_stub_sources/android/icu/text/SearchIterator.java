/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 1996-2015, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */


package android.icu.text;

import java.text.CharacterIterator;

/**
 * <tt>SearchIterator</tt> is an abstract base class that provides
 * methods to search for a pattern within a text string. Instances of
 * <tt>SearchIterator</tt> maintain a current position and scan over the
 * target text, returning the indices the pattern is matched and the length
 * of each match.
 * <p>
 * <tt>SearchIterator</tt> defines a protocol for text searching.
 * Subclasses provide concrete implementations of various search algorithms.
 * For example, <tt>StringSearch</tt> implements language-sensitive pattern
 * matching based on the comparison rules defined in a
 * <tt>RuleBasedCollator</tt> object.
 * <p>
 * Other options for searching include using a BreakIterator to restrict
 * the points at which matches are detected.
 * <p>
 * <tt>SearchIterator</tt> provides an API that is similar to that of
 * other text iteration classes such as <tt>BreakIterator</tt>. Using
 * this class, it is easy to scan through text looking for all occurrences of
 * a given pattern. The following example uses a <tt>StringSearch</tt>
 * object to find all instances of "fox" in the target string. Any other
 * subclass of <tt>SearchIterator</tt> can be used in an identical
 * manner.
 * <pre><code>
 * String target = "The quick brown fox jumped over the lazy fox";
 * String pattern = "fox";
 * SearchIterator iter = new StringSearch(pattern, target);
 * for (int pos = iter.first(); pos != SearchIterator.DONE;
 *         pos = iter.next()) {
 *     System.out.println("Found match at " + pos +
 *             ", length is " + iter.getMatchLength());
 * }
 * </code></pre>
 *
 * @author Laura Werner, synwee
 * @see android.icu.text.BreakIterator
 * @see android.icu.text.RuleBasedCollator
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public abstract class SearchIterator {

/**
 * Protected constructor for use by subclasses.
 * Initializes the iterator with the argument target text for searching
 * and sets the BreakIterator.
 * See class documentation for more details on the use of the target text
 * and {@link android.icu.text.BreakIterator BreakIterator}.
 *
 * @param target The target text to be searched.
 * @param breaker A {@link android.icu.text.BreakIterator BreakIterator} that is used to determine the
 *                boundaries of a logical match. This argument can be null.
 * @exception java.lang.IllegalArgumentException thrown when argument target is null,
 *            or of length 0
 * @see android.icu.text.BreakIterator
 */

protected SearchIterator(java.text.CharacterIterator target, android.icu.text.BreakIterator breaker) { throw new RuntimeException("Stub!"); }

/**
 * <p>
 * Sets the position in the target text at which the next search will start.
 * This method clears any previous match.
 * </p>
 * @param position position from which to start the next search
 * @exception java.lang.IndexOutOfBoundsException thrown if argument position is out
 *            of the target text range.
 * @see #getIndex
 */

public void setIndex(int position) { throw new RuntimeException("Stub!"); }

/**
 * Determines whether overlapping matches are returned. See the class
 * documentation for more information about overlapping matches.
 * <p>
 * The default setting of this property is false
 *
 * @param allowOverlap flag indicator if overlapping matches are allowed
 * @see #isOverlapping
 */

public void setOverlapping(boolean allowOverlap) { throw new RuntimeException("Stub!"); }

/**
 * Set the BreakIterator that will be used to restrict the points
 * at which matches are detected.
 *
 * @param breakiter A BreakIterator that will be used to restrict the
 *                points at which matches are detected. If a match is
 *                found, but the match's start or end index is not a
 *                boundary as determined by the {@link android.icu.text.BreakIterator BreakIterator},
 *                the match will be rejected and another will be searched
 *                for. If this parameter is <tt>null</tt>, no break
 *                detection is attempted.
 * @see android.icu.text.BreakIterator
 */

public void setBreakIterator(android.icu.text.BreakIterator breakiter) { throw new RuntimeException("Stub!"); }

/**
 * Set the target text to be searched. Text iteration will then begin at
 * the start of the text string. This method is useful if you want to
 * reuse an iterator to search within a different body of text.
 *
 * @param text new text iterator to look for match,
 * @exception java.lang.IllegalArgumentException thrown when text is null or has
 *               0 length
 * @see #getTarget
 */

public void setTarget(java.text.CharacterIterator text) { throw new RuntimeException("Stub!"); }

/**
 * Returns the index to the match in the text string that was searched.
 * This call returns a valid result only after a successful call to
 * {@link #first}, {@link #next}, {@link #previous}, or {@link #last}.
 * Just after construction, or after a searching method returns
 * {@link #DONE}, this method will return {@link #DONE}.
 * <p>
 * Use {@link #getMatchLength} to get the matched string length.
 *
 * @return index of a substring within the text string that is being
 *         searched.
 * @see #first
 * @see #next
 * @see #previous
 * @see #last
 */

public int getMatchStart() { throw new RuntimeException("Stub!"); }

/**
 * Return the current index in the text being searched.
 * If the iteration has gone past the end of the text
 * (or past the beginning for a backwards search), {@link #DONE}
 * is returned.
 *
 * @return current index in the text being searched.
 */

public abstract int getIndex();

/**
 * Returns the length of text in the string which matches the search
 * pattern. This call returns a valid result only after a successful call
 * to {@link #first}, {@link #next}, {@link #previous}, or {@link #last}.
 * Just after construction, or after a searching method returns
 * {@link #DONE}, this method will return 0.
 *
 * @return The length of the match in the target text, or 0 if there
 *         is no match currently.
 * @see #first
 * @see #next
 * @see #previous
 * @see #last
 */

public int getMatchLength() { throw new RuntimeException("Stub!"); }

/**
 * Returns the BreakIterator that is used to restrict the indexes at which
 * matches are detected. This will be the same object that was passed to
 * the constructor or to {@link #setBreakIterator}.
 * If the {@link android.icu.text.BreakIterator BreakIterator} has not been set, <tt>null</tt> will be returned.
 * See {@link #setBreakIterator} for more information.
 *
 * @return the BreakIterator set to restrict logic matches
 * @see #setBreakIterator
 * @see android.icu.text.BreakIterator
 */

public android.icu.text.BreakIterator getBreakIterator() { throw new RuntimeException("Stub!"); }

/**
 * Return the string text to be searched.
 * @return text string to be searched.
 */

public java.text.CharacterIterator getTarget() { throw new RuntimeException("Stub!"); }

/**
 * Returns the text that was matched by the most recent call to
 * {@link #first}, {@link #next}, {@link #previous}, or {@link #last}.
 * If the iterator is not pointing at a valid match (e.g. just after
 * construction or after {@link #DONE} has been returned,
 * returns an empty string.
 *
 * @return  the substring in the target test of the most recent match,
 *          or null if there is no match currently.
 * @see #first
 * @see #next
 * @see #previous
 * @see #last
 */

public java.lang.String getMatchedText() { throw new RuntimeException("Stub!"); }

/**
 * Returns the index of the next point at which the text matches the
 * search pattern, starting from the current position
 * The iterator is adjusted so that its current index (as returned by
 * {@link #getIndex}) is the match position if one was found.
 * If a match is not found, {@link #DONE} will be returned and
 * the iterator will be adjusted to a position after the end of the text
 * string.
 *
 * @return The index of the next match after the current position,
 *          or {@link #DONE} if there are no more matches.
 * @see #getIndex
 */

public int next() { throw new RuntimeException("Stub!"); }

/**
 * Returns the index of the previous point at which the string text
 * matches the search pattern, starting at the current position.
 * The iterator is adjusted so that its current index (as returned by
 * {@link #getIndex}) is the match position if one was found.
 * If a match is not found, {@link #DONE} will be returned and
 * the iterator will be adjusted to the index {@link #DONE}.
 *
 * @return The index of the previous match before the current position,
 *          or {@link #DONE} if there are no more matches.
 * @see #getIndex
 */

public int previous() { throw new RuntimeException("Stub!"); }

/**
 * Return true if the overlapping property has been set.
 * See {@link #setOverlapping(boolean)} for more information.
 *
 * @see #setOverlapping
 * @return true if the overlapping property has been set, false otherwise
 */

public boolean isOverlapping() { throw new RuntimeException("Stub!"); }

/** 
 * Resets the iteration.
 * Search will begin at the start of the text string if a forward
 * iteration is initiated before a backwards iteration. Otherwise if a
 * backwards iteration is initiated before a forwards iteration, the
 * search will begin at the end of the text string.
 */

public void reset() { throw new RuntimeException("Stub!"); }

/**
 * Returns the first index at which the string text matches the search
 * pattern. The iterator is adjusted so that its current index (as
 * returned by {@link #getIndex()}) is the match position if one
 *
 * was found.
 * If a match is not found, {@link #DONE} will be returned and
 * the iterator will be adjusted to the index {@link #DONE}.
 * @return The character index of the first match, or
 *         {@link #DONE} if there are no matches.
 *
 * @see #getIndex
 */

public final int first() { throw new RuntimeException("Stub!"); }

/**
 * Returns the first index equal or greater than <tt>position</tt> at which the
 * string text matches the search pattern. The iterator is adjusted so
 * that its current index (as returned by {@link #getIndex()}) is the
 * match position if one was found.
 * If a match is not found, {@link #DONE} will be returned and the
 * iterator will be adjusted to the index {@link #DONE}.
 *
 * @param  position where search if to start from.
 * @return The character index of the first match following
 *         <tt>position</tt>, or {@link #DONE} if there are no matches.
 * @throws java.lang.IndexOutOfBoundsException    If position is less than or greater
 *      than the text range for searching.
 * @see #getIndex
 */

public final int following(int position) { throw new RuntimeException("Stub!"); }

/**
 * Returns the last index in the target text at which it matches the
 * search pattern. The iterator is adjusted so that its current index
 * (as returned by {@link #getIndex}) is the match position if one was
 * found.
 * If a match is not found, {@link #DONE} will be returned and
 * the iterator will be adjusted to the index {@link #DONE}.
 *
 * @return The index of the first match, or {@link #DONE} if
 *         there are no matches.
 * @see #getIndex
 */

public final int last() { throw new RuntimeException("Stub!"); }

/**
 * Returns the first index less than <tt>position</tt> at which the string
 * text matches the search pattern. The iterator is adjusted so that its
 * current index (as returned by {@link #getIndex}) is the match
 * position if one was found. If a match is not found,
 * {@link #DONE} will be returned and the iterator will be
 * adjusted to the index {@link #DONE}
 * <p>
 * When the overlapping option ({@link #isOverlapping}) is off, the last index of the
 * result match is always less than <tt>position</tt>.
 * When the overlapping option is on, the result match may span across
 * <tt>position</tt>.
 *
 * @param  position where search is to start from.
 * @return The character index of the first match preceding
 *         <tt>position</tt>, or {@link #DONE} if there are
 *         no matches.
 * @throws java.lang.IndexOutOfBoundsException If position is less than or greater than
 *                                   the text range for searching
 * @see #getIndex
 */

public final int preceding(int position) { throw new RuntimeException("Stub!"); }

/**
 * Sets the length of the most recent match in the target text.
 * Subclasses' handleNext() and handlePrevious() methods should call this
 * after they find a match in the target text.
 *
 * @param length new length to set
 * @see #handleNext
 * @see #handlePrevious
 */

protected void setMatchLength(int length) { throw new RuntimeException("Stub!"); }

/**
 * Abstract method which subclasses override to provide the mechanism
 * for finding the next match in the target text. This allows different
 * subclasses to provide different search algorithms.
 * <p>
 * If a match is found, the implementation should return the index at
 * which the match starts and should call
 * {@link #setMatchLength} with the number of characters
 * in the target text that make up the match. If no match is found, the
 * method should return {@link #DONE}.
 *
 * @param start The index in the target text at which the search
 *              should start.
 * @return index at which the match starts, else if match is not found
 *         {@link #DONE} is returned
 * @see #setMatchLength
 */

protected abstract int handleNext(int start);

/**
 * Abstract method which subclasses override to provide the mechanism for
 * finding the previous match in the target text. This allows different
 * subclasses to provide different search algorithms.
 * <p>
 * If a match is found, the implementation should return the index at
 * which the match starts and should call
 * {@link #setMatchLength} with the number of characters
 * in the target text that make up the match. If no match is found, the
 * method should return {@link #DONE}.
 *
 * @param startAt   The index in the target text at which the search
 *                  should start.
 * @return index at which the match starts, else if match is not found
 *         {@link #DONE} is returned
 * @see #setMatchLength
 */

protected abstract int handlePrevious(int startAt);

/**
 * Sets the collation element comparison type.
 * <p>
 * The default comparison type is {@link android.icu.text.SearchIterator.ElementComparisonType#STANDARD_ELEMENT_COMPARISON ElementComparisonType#STANDARD_ELEMENT_COMPARISON}.
 *
 * @see android.icu.text.SearchIterator.ElementComparisonType
 * @see #getElementComparisonType()
 */

public void setElementComparisonType(android.icu.text.SearchIterator.ElementComparisonType type) { throw new RuntimeException("Stub!"); }

/**
 * Returns the collation element comparison type.
 *
 * @see android.icu.text.SearchIterator.ElementComparisonType
 * @see #setElementComparisonType(ElementComparisonType)
 */

public android.icu.text.SearchIterator.ElementComparisonType getElementComparisonType() { throw new RuntimeException("Stub!"); }

/**
 * DONE is returned by previous() and next() after all valid matches have
 * been returned, and by first() and last() if there are no matches at all.
 * @see #previous
 * @see #next
 */

public static final int DONE = -1; // 0xffffffff

/**
 * The BreakIterator to define the boundaries of a logical match.
 * This value can be a null.
 * See class documentation for more information.
 * @see #setBreakIterator(BreakIterator)
 * @see #getBreakIterator
 * @see android.icu.text.BreakIterator
 */

protected android.icu.text.BreakIterator breakIterator;

/**
 * Length of the most current match in target text.
 * Value 0 is the default value.
 * @see #setMatchLength
 * @see #getMatchLength
 */

protected int matchLength;

/**
 * Target text for searching.
 * @see #setTarget(CharacterIterator)
 * @see #getTarget
 */

protected java.text.CharacterIterator targetText;
/**
 * Option to control how collation elements are compared.
 * The default value will be {@link #STANDARD_ELEMENT_COMPARISON}.
 * <p>
 * PATTERN_BASE_WEIGHT_IS_WILDCARD supports "asymmetric search" as described in
 * <a href="http://www.unicode.org/reports/tr10/#Asymmetric_Search">
 * UTS #10 Unicode Collation Algorithm</a>, while ANY_BASE_WEIGHT_IS_WILDCARD
 * supports a related option in which "unmarked" characters in either the
 * pattern or the searched text are treated as wildcards that match marked or
 * unmarked versions of the same character.
 *
 * @see #setElementComparisonType(ElementComparisonType)
 * @see #getElementComparisonType()
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public enum ElementComparisonType {
/**
 * Standard collation element comparison at the specified collator strength.
 */

STANDARD_ELEMENT_COMPARISON,
/**
 * Collation element comparison is modified to effectively provide behavior
 * between the specified strength and strength - 1.
 * <p>
 * Collation elements in the pattern that have the base weight for the specified
 * strength are treated as "wildcards" that match an element with any other
 * weight at that collation level in the searched text. For example, with a
 * secondary-strength English collator, a plain 'e' in the pattern will match
 * a plain e or an e with any diacritic in the searched text, but an e with
 * diacritic in the pattern will only match an e with the same diacritic in
 * the searched text.
 */

PATTERN_BASE_WEIGHT_IS_WILDCARD,
/**
 * Collation element comparison is modified to effectively provide behavior
 * between the specified strength and strength - 1.
 * <p>
 * Collation elements in either the pattern or the searched text that have the
 * base weight for the specified strength are treated as "wildcards" that match
 * an element with any other weight at that collation level. For example, with
 * a secondary-strength English collator, a plain 'e' in the pattern will match
 * a plain e or an e with any diacritic in the searched text, but an e with
 * diacritic in the pattern will only match an e with the same diacritic or a
 * plain e in the searched text.
 */

ANY_BASE_WEIGHT_IS_WILDCARD;
}

}

