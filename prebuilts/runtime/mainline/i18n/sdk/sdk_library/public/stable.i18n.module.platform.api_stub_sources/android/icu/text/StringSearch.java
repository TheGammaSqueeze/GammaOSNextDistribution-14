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


/**
 *
 * <tt>StringSearch</tt> is a {@link android.icu.text.SearchIterator SearchIterator} that provides
 * language-sensitive text searching based on the comparison rules defined
 * in a {@link android.icu.text.RuleBasedCollator RuleBasedCollator} object.
 * StringSearch ensures that language eccentricity can be
 * handled, e.g. for the German collator, characters &szlig; and SS will be matched
 * if case is chosen to be ignored.
 * See the <a href="http://source.icu-project.org/repos/icu/icuhtml/trunk/design/collation/ICU_collation_design.htm">
 * "ICU Collation Design Document"</a> for more information.
 * <p>
 * There are 2 match options for selection:<br>
 * Let S' be the sub-string of a text string S between the offsets start and
 * end [start, end].
 * <br>
 * A pattern string P matches a text string S at the offsets [start, end]
 * if
 * <pre>
 * option 1. Some canonical equivalent of P matches some canonical equivalent
 *           of S'
 * option 2. P matches S' and if P starts or ends with a combining mark,
 *           there exists no non-ignorable combining mark before or after S?
 *           in S respectively.
 * </pre>
 * Option 2. is the default.
 * <p>
 * This search has APIs similar to that of other text iteration mechanisms
 * such as the break iterators in {@link android.icu.text.BreakIterator BreakIterator}. Using these
 * APIs, it is easy to scan through text looking for all occurrences of
 * a given pattern. This search iterator allows changing of direction by
 * calling a {@link #reset} followed by a {@link #next} or {@link #previous}.
 * Though a direction change can occur without calling {@link #reset} first,
 * this operation comes with some speed penalty.
 * Match results in the forward direction will match the result matches in
 * the backwards direction in the reverse order
 * <p>
 * {@link android.icu.text.SearchIterator SearchIterator} provides APIs to specify the starting position
 * within the text string to be searched, e.g. {@link android.icu.text.SearchIterator#setIndex setIndex},
 * {@link android.icu.text.SearchIterator#preceding preceding} and {@link android.icu.text.SearchIterator#following following}.
 * Since the starting position will be set as it is specified, please take note that
 * there are some danger points at which the search may render incorrect
 * results:
 * <ul>
 * <li> In the midst of a substring that requires normalization.
 * <li> If the following match is to be found, the position should not be the
 *      second character which requires swapping with the preceding
 *      character. Vice versa, if the preceding match is to be found, the
 *      position to search from should not be the first character which
 *      requires swapping with the next character. E.g certain Thai and
 *      Lao characters require swapping.
 * <li> If a following pattern match is to be found, any position within a
 *      contracting sequence except the first will fail. Vice versa if a
 *      preceding pattern match is to be found, an invalid starting point
 *      would be any character within a contracting sequence except the last.
 * </ul>
 * <p>
 * A {@link android.icu.text.BreakIterator BreakIterator} can be used if only matches at logical breaks are desired.
 * Using a {@link android.icu.text.BreakIterator BreakIterator} will only give you results that exactly matches the
 * boundaries given by the {@link android.icu.text.BreakIterator BreakIterator}. For instance the pattern "e" will
 * not be found in the string "\u00e9" if a character break iterator is used.
 * <p>
 * Options are provided to handle overlapping matches.
 * E.g. In English, overlapping matches produces the result 0 and 2
 * for the pattern "abab" in the text "ababab", where mutually
 * exclusive matches only produces the result of 0.
 * <p>
 * Options are also provided to implement "asymmetric search" as described in
 * <a href="http://www.unicode.org/reports/tr10/#Asymmetric_Search">
 * UTS #10 Unicode Collation Algorithm</a>, specifically the ElementComparisonType
 * values.
 * <p>
 * Though collator attributes will be taken into consideration while
 * performing matches, there are no APIs here for setting and getting the
 * attributes. These attributes can be set by getting the collator
 * from {@link #getCollator} and using the APIs in {@link android.icu.text.RuleBasedCollator RuleBasedCollator}.
 * Lastly to update <tt>StringSearch</tt> to the new collator attributes,
 * {@link #reset} has to be called.
 * <p>
 * Restriction: <br>
 * Currently there are no composite characters that consists of a
 * character with combining class &gt; 0 before a character with combining
 * class == 0. However, if such a character exists in the future,
 * <tt>StringSearch</tt> does not guarantee the results for option 1.
 * <p>
 * Consult the {@link android.icu.text.SearchIterator SearchIterator} documentation for information on
 * and examples of how to use instances of this class to implement text
 * searching.
 * <p>
 * Note, <tt>StringSearch</tt> is not to be subclassed.
 * </p>
 * @see android.icu.text.SearchIterator
 * @see android.icu.text.RuleBasedCollator
 * @author Laura Werner, synwee
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class StringSearch extends android.icu.text.SearchIterator {

/**
 * Initializes the iterator to use the language-specific rules defined in
 * the argument collator to search for argument pattern in the argument
 * target text. The argument <code>breakiter</code> is used to define logical matches.
 * See super class documentation for more details on the use of the target
 * text and {@link android.icu.text.BreakIterator BreakIterator}.
 * @param pattern text to look for.
 * @param target target text to search for pattern.
 * @param collator {@link android.icu.text.RuleBasedCollator RuleBasedCollator} that defines the language rules
 * @param breakiter A {@link android.icu.text.BreakIterator BreakIterator} that is used to determine the
 *                boundaries of a logical match. This argument can be null.
 * @throws java.lang.IllegalArgumentException thrown when argument target is null,
 *            or of length 0
 * @see android.icu.text.BreakIterator
 * @see android.icu.text.RuleBasedCollator
 */

public StringSearch(java.lang.String pattern, java.text.CharacterIterator target, android.icu.text.RuleBasedCollator collator, android.icu.text.BreakIterator breakiter) { super(null, null); throw new RuntimeException("Stub!"); }

/**
 * Initializes the iterator to use the language-specific rules defined in
 * the argument collator to search for argument pattern in the argument
 * target text. No {@link android.icu.text.BreakIterator BreakIterator}s are set to test for logical matches.
 * @param pattern text to look for.
 * @param target target text to search for pattern.
 * @param collator {@link android.icu.text.RuleBasedCollator RuleBasedCollator} that defines the language rules
 * @throws java.lang.IllegalArgumentException thrown when argument target is null,
 *            or of length 0
 * @see android.icu.text.RuleBasedCollator
 */

public StringSearch(java.lang.String pattern, java.text.CharacterIterator target, android.icu.text.RuleBasedCollator collator) { super(null, null); throw new RuntimeException("Stub!"); }

/**
 * Initializes the iterator to use the language-specific rules and
 * break iterator rules defined in the argument locale to search for
 * argument pattern in the argument target text.
 * @param pattern text to look for.
 * @param target target text to search for pattern.
 * @param locale locale to use for language and break iterator rules
 * @throws java.lang.IllegalArgumentException thrown when argument target is null,
 *            or of length 0. ClassCastException thrown if the collator for
 *            the specified locale is not a RuleBasedCollator.
 */

public StringSearch(java.lang.String pattern, java.text.CharacterIterator target, java.util.Locale locale) { super(null, null); throw new RuntimeException("Stub!"); }

/**
 * Initializes the iterator to use the language-specific rules and
 * break iterator rules defined in the argument locale to search for
 * argument pattern in the argument target text.
 * See super class documentation for more details on the use of the target
 * text and {@link android.icu.text.BreakIterator BreakIterator}.
 * @param pattern text to look for.
 * @param target target text to search for pattern.
 * @param locale locale to use for language and break iterator rules
 * @throws java.lang.IllegalArgumentException thrown when argument target is null,
 *            or of length 0. ClassCastException thrown if the collator for
 *            the specified locale is not a RuleBasedCollator.
 * @see android.icu.text.BreakIterator
 * @see android.icu.text.RuleBasedCollator
 * @see android.icu.text.SearchIterator
 */

public StringSearch(java.lang.String pattern, java.text.CharacterIterator target, android.icu.util.ULocale locale) { super(null, null); throw new RuntimeException("Stub!"); }

/**
 * Initializes the iterator to use the language-specific rules and
 * break iterator rules defined in the default locale to search for
 * argument pattern in the argument target text.
 * @param pattern text to look for.
 * @param target target text to search for pattern.
 * @throws java.lang.IllegalArgumentException thrown when argument target is null,
 *            or of length 0. ClassCastException thrown if the collator for
 *            the default locale is not a RuleBasedCollator.
 */

public StringSearch(java.lang.String pattern, java.lang.String target) { super(null, null); throw new RuntimeException("Stub!"); }

/**
 * Gets the {@link android.icu.text.RuleBasedCollator RuleBasedCollator} used for the language rules.
 * <p>
 * Since <tt>StringSearch</tt> depends on the returned {@link android.icu.text.RuleBasedCollator RuleBasedCollator}, any
 * changes to the {@link android.icu.text.RuleBasedCollator RuleBasedCollator} result should follow with a call to
 * either {@link #reset()} or {@link #setCollator(android.icu.text.RuleBasedCollator)} to ensure the correct
 * search behavior.
 * </p>
 * @return {@link android.icu.text.RuleBasedCollator RuleBasedCollator} used by this <tt>StringSearch</tt>
 * @see android.icu.text.RuleBasedCollator
 * @see #setCollator
 */

public android.icu.text.RuleBasedCollator getCollator() { throw new RuntimeException("Stub!"); }

/**
 * Sets the {@link android.icu.text.RuleBasedCollator RuleBasedCollator} to be used for language-specific searching.
 * <p>
 * The iterator's position will not be changed by this method.
 * @param collator to use for this <tt>StringSearch</tt>
 * @throws java.lang.IllegalArgumentException thrown when collator is null
 * @see #getCollator
 */

public void setCollator(android.icu.text.RuleBasedCollator collator) { throw new RuntimeException("Stub!"); }

/**
 * Returns the pattern for which <tt>StringSearch</tt> is searching for.
 * @return the pattern searched for
 */

public java.lang.String getPattern() { throw new RuntimeException("Stub!"); }

/**
 * Set the pattern to search for.
 * The iterator's position will not be changed by this method.
 * @param pattern for searching
 * @see #getPattern
 * @exception java.lang.IllegalArgumentException thrown if pattern is null or of
 *               length 0
 */

public void setPattern(java.lang.String pattern) { throw new RuntimeException("Stub!"); }

/**
 * Determines whether canonical matches (option 1, as described in the
 * class documentation) is set.
 * See setCanonical(boolean) for more information.
 * @see #setCanonical
 * @return true if canonical matches is set, false otherwise
 */

public boolean isCanonical() { throw new RuntimeException("Stub!"); }

/**
 * Set the canonical match mode. See class documentation for details.
 * The default setting for this property is false.
 * @param allowCanonical flag indicator if canonical matches are allowed
 * @see #isCanonical
 */

public void setCanonical(boolean allowCanonical) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public void setTarget(java.text.CharacterIterator text) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public int getIndex() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public void setIndex(int position) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public void reset() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

protected int handleNext(int position) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

protected int handlePrevious(int position) { throw new RuntimeException("Stub!"); }
}

