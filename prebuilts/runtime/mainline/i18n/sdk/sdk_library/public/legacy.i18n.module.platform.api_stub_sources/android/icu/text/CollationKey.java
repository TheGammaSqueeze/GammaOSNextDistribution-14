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
 * A <code>CollationKey</code> represents a <code>String</code>
 * under the rules of a specific <code>Collator</code>
 * object. Comparing two <code>CollationKey</code>s returns the
 * relative order of the <code>String</code>s they represent.
 *
 * <p>Since the rule set of <code>Collator</code>s can differ, the
 * sort orders of the same string under two different
 * <code>Collator</code>s might differ.  Hence comparing
 * <code>CollationKey</code>s generated from different
 * <code>Collator</code>s can give incorrect results.
 
 * <p>Both the method
 * <code>CollationKey.compareTo(CollationKey)</code> and the method
 * <code>Collator.compare(String, String)</code> compare two strings
 * and returns their relative order.  The performance characteristics
 * of these two approaches can differ.
 * Note that collation keys are often less efficient than simply doing comparison.
 * For more details, see the ICU User Guide.
 *
 * <p>During the construction of a <code>CollationKey</code>, the
 * entire source string is examined and processed into a series of
 * bits terminated by a null, that are stored in the <code>CollationKey</code>.
 * When <code>CollationKey.compareTo(CollationKey)</code> executes, it
 * performs bitwise comparison on the bit sequences.  This can incurs
 * startup cost when creating the <code>CollationKey</code>, but once
 * the key is created, binary comparisons are fast.  This approach is
 * recommended when the same strings are to be compared over and over
 * again.
 *
 * <p>On the other hand, implementations of
 * <code>Collator.compare(String, String)</code> can examine and
 * process the strings only until the first characters differing in
 * order.  This approach is recommended if the strings are to be
 * compared only once.</p>
 *
 * <p>More information about the composition of the bit sequence can
 * be found in the
 * <a href="http://www.icu-project.org/userguide/Collate_ServiceArchitecture.html">
 * user guide</a>.</p>
 *
 * <p>The following example shows how <code>CollationKey</code>s can be used
 * to sort a list of <code>String</code>s.</p>
 * <blockquote>
 * <pre>
 * // Create an array of CollationKeys for the Strings to be sorted.
 * Collator myCollator = Collator.getInstance();
 * CollationKey[] keys = new CollationKey[3];
 * keys[0] = myCollator.getCollationKey("Tom");
 * keys[1] = myCollator.getCollationKey("Dick");
 * keys[2] = myCollator.getCollationKey("Harry");
 * sort( keys );
 * <br>
 * //...
 * <br>
 * // Inside body of sort routine, compare keys this way
 * if( keys[i].compareTo( keys[j] ) &gt; 0 )
 *    // swap keys[i] and keys[j]
 * <br>
 * //...
 * <br>
 * // Finally, when we've returned from sort.
 * System.out.println( keys[0].getSourceString() );
 * System.out.println( keys[1].getSourceString() );
 * System.out.println( keys[2].getSourceString() );
 * </pre>
 * </blockquote>
 * <p>
 * This class is not subclassable
 * @see android.icu.text.Collator
 * @see android.icu.text.RuleBasedCollator
 * @author Syn Wee Quek
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class CollationKey implements java.lang.Comparable<android.icu.text.CollationKey> {

/**
 * CollationKey constructor.
 * This constructor is given public access, unlike the JDK version, to
 * allow access to users extending the Collator class. See
 * {@link android.icu.text.Collator#getCollationKey(java.lang.String) Collator#getCollationKey(String)}.
 * @param source string this CollationKey is to represent
 * @param key array of bytes that represent the collation order of argument
 *            source terminated by a null
 * @see android.icu.text.Collator
 */

public CollationKey(java.lang.String source, byte[] key) { throw new RuntimeException("Stub!"); }

/**
 * Return the source string that this CollationKey represents.
 * @return source string that this CollationKey represents
 */

public java.lang.String getSourceString() { throw new RuntimeException("Stub!"); }

/**
 * Duplicates and returns the value of this CollationKey as a sequence
 * of big-endian bytes terminated by a null.
 *
 * <p>If two CollationKeys can be legitimately compared, then one can
 * compare the byte arrays of each to obtain the same result, e.g.
 * <pre>
 * byte key1[] = collationkey1.toByteArray();
 * byte key2[] = collationkey2.toByteArray();
 * int key, targetkey;
 * int i = 0;
 * do {
 *       key = key1[i] &amp; 0xFF;
 *     targetkey = key2[i] &amp; 0xFF;
 *     if (key &lt; targetkey) {
 *         System.out.println("String 1 is less than string 2");
 *         return;
 *     }
 *     if (targetkey &lt; key) {
 *         System.out.println("String 1 is more than string 2");
 *     }
 *     i ++;
 * } while (key != 0 &amp;&amp; targetKey != 0);
 *
 * System.out.println("Strings are equal.");
 * </pre>
 *
 * @return CollationKey value in a sequence of big-endian byte bytes
 *         terminated by a null.
 */

public byte[] toByteArray() { throw new RuntimeException("Stub!"); }

/**
 * Compare this CollationKey to another CollationKey.  The
 * collation rules of the Collator that created this key are
 * applied.
 *
 * <p><strong>Note:</strong> Comparison between CollationKeys
 * created by different Collators might return incorrect
 * results.  See class documentation.
 *
 * @param target target CollationKey
 * @return an integer value.  If the value is less than zero this CollationKey
 *         is less than than target, if the value is zero they are equal, and
 *         if the value is greater than zero this CollationKey is greater
 *         than target.
 * @exception java.lang.NullPointerException is thrown if argument is null.
 * @see android.icu.text.Collator#compare(String, String)
 */

public int compareTo(android.icu.text.CollationKey target) { throw new RuntimeException("Stub!"); }

/**
 * Compare this CollationKey and the specified Object for
 * equality.  The collation rules of the Collator that created
 * this key are applied.
 *
 * <p>See note in compareTo(CollationKey) for warnings about
 * possible incorrect results.
 *
 * @param target the object to compare to.
 * @return true if the two keys compare as equal, false otherwise.
 * @see #compareTo(CollationKey)
 * @exception java.lang.ClassCastException is thrown when the argument is not
 *            a CollationKey.  NullPointerException is thrown when the argument
 *            is null.
 */

public boolean equals(java.lang.Object target) { throw new RuntimeException("Stub!"); }

/**
 * Compare this CollationKey and the argument target CollationKey for
 * equality.
 * The collation
 * rules of the Collator object which created these objects are applied.
 * <p>
 * See note in compareTo(CollationKey) for warnings of incorrect results
 *
 * @param target the CollationKey to compare to.
 * @return true if two objects are equal, false otherwise.
 * @exception java.lang.NullPointerException is thrown when the argument is null.
 */

public boolean equals(android.icu.text.CollationKey target) { throw new RuntimeException("Stub!"); }

/**
 * Returns a hash code for this CollationKey. The hash value is calculated
 * on the key itself, not the String from which the key was created. Thus
 * if x and y are CollationKeys, then x.hashCode(x) == y.hashCode()
 * if x.equals(y) is true. This allows language-sensitive comparison in a
 * hash table.
 *
 * @return the hash value.
 */

public int hashCode() { throw new RuntimeException("Stub!"); }

/**
 * Produces a bound for the sort order of a given collation key and a
 * strength level. This API does not attempt to find a bound for the
 * CollationKey String representation, hence null will be returned in its
 * place.
 * <p>
 * Resulting bounds can be used to produce a range of strings that are
 * between upper and lower bounds. For example, if bounds are produced
 * for a sortkey of string "smith", strings between upper and lower
 * bounds with primary strength would include "Smith", "SMITH", "sMiTh".
 * <p>
 * There are two upper bounds that can be produced. If BoundMode.UPPER
 * is produced, strings matched would be as above. However, if a bound
 * is produced using BoundMode.UPPER_LONG is used, the above example will
 * also match "Smithsonian" and similar.
 * <p>
 * For more on usage, see example in test procedure
 * <a href="http://source.icu-project.org/repos/icu/icu4j/trunk/src/com/ibm/icu/dev/test/collator/CollationAPITest.java">
 * src/com/ibm/icu/dev/test/collator/CollationAPITest/TestBounds.
 * </a>
 * <p>
 * Collation keys produced may be compared using the <TT>compare</TT> API.
 * @param boundType Mode of bound required. It can be BoundMode.LOWER, which
 *              produces a lower inclusive bound, BoundMode.UPPER, that
 *              produces upper bound that matches strings of the same
 *              length or BoundMode.UPPER_LONG that matches strings that
 *              have the same starting substring as the source string.
 * @param noOfLevels Strength levels required in the resulting bound
 *                 (for most uses, the recommended value is PRIMARY). This
 *                 strength should be less than the maximum strength of
 *                 this CollationKey.
 *                 See users guide for explanation on the strength levels a
 *                 collation key can have.
 * @return the result bounded CollationKey with a valid sort order but
 *         a null String representation.
 * @exception java.lang.IllegalArgumentException thrown when the strength level
 *            requested is higher than or equal to the strength in this
 *            CollationKey.
 *            In the case of an Exception, information
 *            about the maximum strength to use will be returned in the
 *            Exception. The user can then call getBound() again with the
 *            appropriate strength.
 * @see android.icu.text.CollationKey
 * @see android.icu.text.CollationKey.BoundMode
 * @see android.icu.text.Collator#PRIMARY
 * @see android.icu.text.Collator#SECONDARY
 * @see android.icu.text.Collator#TERTIARY
 * @see android.icu.text.Collator#QUATERNARY
 * @see android.icu.text.Collator#IDENTICAL
 */

public android.icu.text.CollationKey getBound(int boundType, int noOfLevels) { throw new RuntimeException("Stub!"); }

/**
 * Merges this CollationKey with another.
 * The levels are merged with their corresponding counterparts
 * (primaries with primaries, secondaries with secondaries etc.).
 * Between the values from the same level a separator is inserted.
 *
 * <p>This is useful, for example, for combining sort keys from first and last names
 * to sort such pairs.
 * See http://www.unicode.org/reports/tr10/#Merging_Sort_Keys
 *
 * <p>The recommended way to achieve "merged" sorting is by
 * concatenating strings with U+FFFE between them.
 * The concatenation has the same sort order as the merged sort keys,
 * but merge(getSortKey(str1), getSortKey(str2)) may differ from getSortKey(str1 + '\uFFFE' + str2).
 * Using strings with U+FFFE may yield shorter sort keys.
 *
 * <p>For details about Sort Key Features see
 * https://unicode-org.github.io/icu/userguide/collation/api#sort-key-features
 *
 * <p>It is possible to merge multiple sort keys by consecutively merging
 * another one with the intermediate result.
 *
 * <p>Only the sort key bytes of the CollationKeys are merged.
 * This API does not attempt to merge the
 * String representations of the CollationKeys, hence null will be returned
 * as the result's String representation.
 *
 * <p>Example (uncompressed):
 * <pre>191B1D 01 050505 01 910505 00
 * 1F2123 01 050505 01 910505 00</pre>
 * will be merged as
 * <pre>191B1D 02 1F2123 01 050505 02 050505 01 910505 02 910505 00</pre>
 *
 * @param source CollationKey to merge with
 * @return a CollationKey that contains the valid merged sort keys
 *         with a null String representation,
 *         i.e. <tt>new CollationKey(null, merged_sort_keys)</tt>
 * @exception java.lang.IllegalArgumentException thrown if source CollationKey
 *            argument is null or of 0 length.
 */

public android.icu.text.CollationKey merge(android.icu.text.CollationKey source) { throw new RuntimeException("Stub!"); }
/**
 * Options that used in the API CollationKey.getBound() for getting a
 * CollationKey based on the bound mode requested.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static final class BoundMode {

private BoundMode() { throw new RuntimeException("Stub!"); }

/**
 * Lower bound
 */

public static final int LOWER = 0; // 0x0

/**
 * Upper bound that will match strings of exact size
 */

public static final int UPPER = 1; // 0x1

/**
 * Upper bound that will match all the strings that have the same
 * initial substring as the given string
 */

public static final int UPPER_LONG = 2; // 0x2
}

}

