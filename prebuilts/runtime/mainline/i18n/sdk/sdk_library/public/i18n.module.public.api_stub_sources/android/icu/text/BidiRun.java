/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
*******************************************************************************
*   Copyright (C) 2001-2016, International Business Machines
*   Corporation and others.  All Rights Reserved.
*******************************************************************************
*/
/* Written by Simon Montagu, Matitiahu Allouche
 * (ported from C code written by Markus W. Scherer)
 */


package android.icu.text;


/**
 * A BidiRun represents a sequence of characters at the same embedding level.
 * The Bidi algorithm decomposes a piece of text into sequences of characters
 * at the same embedding level, each such sequence is called a "run".
 *
 * <p>A BidiRun represents such a run by storing its essential properties,
 * but does not duplicate the characters which form the run.
 *
 * <p>The &quot;limit&quot; of the run is the position just after the
 * last character, i.e., one more than that position.
 *
 * <p>This class has no public constructor, and its members cannot be
 * modified by users.
 *
 * @see android.icu.text.Bidi
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class BidiRun {

BidiRun() { throw new RuntimeException("Stub!"); }

/**
 * Get the first logical position of the run in the source text
 */

public int getStart() { throw new RuntimeException("Stub!"); }

/**
 * Get position of one character after the end of the run in the source text
 */

public int getLimit() { throw new RuntimeException("Stub!"); }

/**
 * Get length of run
 */

public int getLength() { throw new RuntimeException("Stub!"); }

/**
 * Get level of run
 */

public byte getEmbeddingLevel() { throw new RuntimeException("Stub!"); }

/**
 * Check if run level is odd
 * @return true if the embedding level of this run is odd, i.e. it is a
 *  right-to-left run.
 */

public boolean isOddRun() { throw new RuntimeException("Stub!"); }

/**
 * Check if run level is even
 * @return true if the embedding level of this run is even, i.e. it is a
 *  left-to-right run.
 */

public boolean isEvenRun() { throw new RuntimeException("Stub!"); }

/**
 * Get direction of run
 */

public byte getDirection() { throw new RuntimeException("Stub!"); }

/**
 * String to display run
 */

public java.lang.String toString() { throw new RuntimeException("Stub!"); }
}

