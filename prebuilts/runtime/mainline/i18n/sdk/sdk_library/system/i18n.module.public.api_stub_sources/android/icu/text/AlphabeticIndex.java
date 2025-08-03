/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2008-2016, Google Inc, International Business Machines Corporation
 * and others. All Rights Reserved.
 *******************************************************************************
 */

package android.icu.text;

import android.icu.util.ULocale;
import android.icu.text.AlphabeticIndex.Bucket;
import java.util.Iterator;

/**
 * AlphabeticIndex supports the creation of a UI index appropriate for a given language.
 * It can support either direct use, or use with a client that doesn't support localized collation.
 * The following is an example of what an index might look like in a UI:
 *
 * <pre>
 *  <b>... A B C D E F G H I J K L M N O P Q R S T U V W X Y Z  ...</b>
 *
 *  <b>A</b>
 *     Addison
 *     Albertson
 *     Azensky
 *  <b>B</b>
 *     Baecker
 *  ...
 * </pre>
 *
 * The class can generate a list of labels for use as a UI "index", that is, a list of
 * clickable characters (or character sequences) that allow the user to see a segment
 * (bucket) of a larger "target" list. That is, each label corresponds to a bucket in
 * the target list, where everything in the bucket is greater than or equal to the character
 * (according to the locale's collation). Strings can be added to the index;
 * they will be in sorted order in the right bucket.
 * <p>
 * The class also supports having buckets for strings before the first (underflow),
 * after the last (overflow), and between scripts (inflow). For example, if the index
 * is constructed with labels for Russian and English, Greek characters would fall
 * into an inflow bucket between the other two scripts.
 *
 * <p><em>Note:</em> If you expect to have a lot of ASCII or Latin characters
 * as well as characters from the user's language,
 * then it is a good idea to call addLabels(ULocale.English).
 *
 * <h2>Direct Use</h2>
 * <p>The following shows an example of building an index directly.
 *  The "show..." methods below are just to illustrate usage.
 *
 * <pre>
 * // Create a simple index where the values for the strings are Integers, and add the strings
 *
 * AlphabeticIndex&lt;Integer&gt; index = new AlphabeticIndex&lt;Integer&gt;(desiredLocale).addLabels(additionalLocale);
 * int counter = 0;
 * for (String item : test) {
 *     index.addRecord(item, counter++);
 * }
 * ...
 * // Show index at top. We could skip or gray out empty buckets
 *
 * for (AlphabeticIndex.Bucket&lt;Integer&gt; bucket : index) {
 *     if (showAll || bucket.size() != 0) {
 *         showLabelAtTop(UI, bucket.getLabel());
 *     }
 * }
 *  ...
 * // Show the buckets with their contents, skipping empty buckets
 *
 * for (AlphabeticIndex.Bucket&lt;Integer&gt; bucket : index) {
 *     if (bucket.size() != 0) {
 *         showLabelInList(UI, bucket.getLabel());
 *         for (AlphabeticIndex.Record&lt;Integer&gt; item : bucket) {
 *             showIndexedItem(UI, item.getName(), item.getData());
 *         }
 * </pre>
 *
 * The caller can build different UIs using this class.
 * For example, an index character could be omitted or grayed-out
 * if its bucket is empty. Small buckets could also be combined based on size, such as:
 *
 * <pre>
 * <b>... A-F G-N O-Z ...</b>
 * </pre>
 *
 * <h2>Client Support</h2>
 * <p>Callers can also use the {@link android.icu.text.AlphabeticIndex.ImmutableIndex AlphabeticIndex.ImmutableIndex}, or the AlphabeticIndex itself,
 * to support sorting on a client that doesn't support AlphabeticIndex functionality.
 *
 * <p>The ImmutableIndex is both immutable and thread-safe.
 * The corresponding AlphabeticIndex methods are not thread-safe because
 * they "lazily" build the index buckets.
 * <ul>
 * <li>ImmutableIndex.getBucket(index) provides random access to all
 *     buckets and their labels and label types.
 * <li>AlphabeticIndex.getBucketLabels() or the bucket iterator on either class
 *     can be used to get a list of the labels,
 *     such as "...", "A", "B",..., and send that list to the client.
 * <li>When the client has a new name, it sends that name to the server.
 * The server needs to call the following methods,
 * and communicate the bucketIndex and collationKey back to the client.
 *
 * <pre>
 * int bucketIndex = index.getBucketIndex(name);
 * String label = immutableIndex.getBucket(bucketIndex).getLabel();  // optional
 * RawCollationKey collationKey = collator.getRawCollationKey(name, null);
 * </pre>
 *
 * <li>The client would put the name (and associated information) into its bucket for bucketIndex. The collationKey is a
 * sequence of bytes that can be compared with a binary compare, and produce the right localized result.</li>
 * </ul>
 *
 * @author Mark Davis
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class AlphabeticIndex<V> implements java.lang.Iterable<android.icu.text.AlphabeticIndex.Bucket<V>> {

/**
 * Create the index object.
 *
 * @param locale
 *            The locale for the index.
 */

public AlphabeticIndex(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Create the index object.
 *
 * @param locale
 *            The locale for the index.
 */

public AlphabeticIndex(java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Create an AlphabeticIndex that uses a specific collator.
 *
 * <p>The index will be created with no labels; the addLabels() function must be called
 * after creation to add the desired labels to the index.
 *
 * <p>The index will work directly with the supplied collator. If the caller will need to
 * continue working with the collator it should be cloned first, so that the
 * collator provided to the AlphabeticIndex remains unchanged after creation of the index.
 *
 * @param collator The collator to use to order the contents of this index.
 */

public AlphabeticIndex(android.icu.text.RuleBasedCollator collator) { throw new RuntimeException("Stub!"); }

/**
 * Add more index characters (aside from what are in the locale)
 * @param additions additional characters to add to the index, such as A-Z.
 * @return this, for chaining
 */

public android.icu.text.AlphabeticIndex<V> addLabels(android.icu.text.UnicodeSet additions) { throw new RuntimeException("Stub!"); }

/**
 * Add more index characters (aside from what are in the locale)
 * @param additions additional characters to add to the index, such as those in Swedish.
 * @return this, for chaining
 */

public android.icu.text.AlphabeticIndex<V> addLabels(android.icu.util.ULocale... additions) { throw new RuntimeException("Stub!"); }

/**
 * Add more index characters (aside from what are in the locale)
 * @param additions additional characters to add to the index, such as those in Swedish.
 * @return this, for chaining
 */

public android.icu.text.AlphabeticIndex<V> addLabels(java.util.Locale... additions) { throw new RuntimeException("Stub!"); }

/**
 * Set the overflow label
 * @param overflowLabel see class description
 * @return this, for chaining
 */

public android.icu.text.AlphabeticIndex<V> setOverflowLabel(java.lang.String overflowLabel) { throw new RuntimeException("Stub!"); }

/**
 * Get the default label used in the IndexCharacters' locale for underflow, eg the last item in: X Y Z ...
 *
 * @return underflow label
 */

public java.lang.String getUnderflowLabel() { throw new RuntimeException("Stub!"); }

/**
 * Set the underflowLabel label
 * @param underflowLabel see class description
 * @return this, for chaining
 */

public android.icu.text.AlphabeticIndex<V> setUnderflowLabel(java.lang.String underflowLabel) { throw new RuntimeException("Stub!"); }

/**
 * Get the default label used in the IndexCharacters' locale for overflow, eg the first item in: ... A B C
 *
 * @return overflow label
 */

public java.lang.String getOverflowLabel() { throw new RuntimeException("Stub!"); }

/**
 * Set the inflowLabel label
 * @param inflowLabel see class description
 * @return this, for chaining
 */

public android.icu.text.AlphabeticIndex<V> setInflowLabel(java.lang.String inflowLabel) { throw new RuntimeException("Stub!"); }

/**
 * Get the default label used for abbreviated buckets <i>between</i> other labels. For example, consider the labels
 * for Latin and Greek are used: X Y Z ... &#x0391; &#x0392; &#x0393;.
 *
 * @return inflow label
 */

public java.lang.String getInflowLabel() { throw new RuntimeException("Stub!"); }

/**
 * Get the limit on the number of labels in the index. The number of buckets can be slightly larger: see getBucketCount().
 *
 * @return maxLabelCount maximum number of labels.
 */

public int getMaxLabelCount() { throw new RuntimeException("Stub!"); }

/**
 * Set a limit on the number of labels in the index. The number of buckets can be slightly larger: see
 * getBucketCount().
 *
 * @param maxLabelCount Set the maximum number of labels. Currently, if the number is exceeded, then every
 *         nth item is removed to bring the count down. A more sophisticated mechanism may be available in the
 *         future.
 * @return this, for chaining
 */

public android.icu.text.AlphabeticIndex<V> setMaxLabelCount(int maxLabelCount) { throw new RuntimeException("Stub!"); }

/**
 * Builds an immutable, thread-safe version of this instance, without data records.
 *
 * @return an immutable index instance
 */

public android.icu.text.AlphabeticIndex.ImmutableIndex<V> buildImmutableIndex() { throw new RuntimeException("Stub!"); }

/**
 * Get the labels.
 *
 * @return The list of bucket labels, after processing.
 */

public java.util.List<java.lang.String> getBucketLabels() { throw new RuntimeException("Stub!"); }

/**
 * Get a clone of the collator used internally. Note that for performance reasons, the clone is only done once, and
 * then stored. The next time it is accessed, the same instance is returned.
 * <p>
 * <b><i>Don't use this method across threads if you are changing the settings on the collator, at least not without
 * synchronizing.</i></b>
 *
 * @return a clone of the collator used internally
 */

public android.icu.text.RuleBasedCollator getCollator() { throw new RuntimeException("Stub!"); }

/**
 * Add a record (name and data) to the index. The name will be used to sort the items into buckets, and to sort
 * within the bucket. Two records may have the same name. When they do, the sort order is according to the order added:
 * the first added comes first.
 *
 * @param name
 *            Name, such as a name
 * @param data
 *            Data, such as an address or link
 * @return this, for chaining
 */

public android.icu.text.AlphabeticIndex<V> addRecord(java.lang.CharSequence name, V data) { throw new RuntimeException("Stub!"); }

/**
 * Get the bucket number for the given name. This routine permits callers to implement their own bucket handling
 * mechanisms, including client-server handling. For example, when a new name is created on the client, it can ask
 * the server for the bucket for that name, and the sortkey (using getCollator). Once the client has that
 * information, it can put the name into the right bucket, and sort it within that bucket, without having access to
 * the index or collator.
 * <p>
 * Note that the bucket number (and sort key) are only valid for the settings of the current AlphabeticIndex; if
 * those are changed, then the bucket number and sort key must be regenerated.
 *
 * @param name
 *            Name, such as a name
 * @return the bucket index for the name
 */

public int getBucketIndex(java.lang.CharSequence name) { throw new RuntimeException("Stub!"); }

/**
 * Clear the index.
 *
 * @return this, for chaining
 */

public android.icu.text.AlphabeticIndex<V> clearRecords() { throw new RuntimeException("Stub!"); }

/**
 * Return the number of buckets in the index. This will be the same as the number of labels, plus buckets for the underflow, overflow, and inflow(s).
 *
 * @return number of buckets
 */

public int getBucketCount() { throw new RuntimeException("Stub!"); }

/**
 * Return the number of records in the index: that is, the total number of distinct &lt;name,data&gt; pairs added with addRecord(...), over all the buckets.
 *
 * @return total number of records in buckets
 */

public int getRecordCount() { throw new RuntimeException("Stub!"); }

/**
 * Return an iterator over the buckets.
 *
 * @return iterator over buckets.
 */

public java.util.Iterator<android.icu.text.AlphabeticIndex.Bucket<V>> iterator() { throw new RuntimeException("Stub!"); }
/**
 * An index "bucket" with a label string and type.
 * It is referenced by {@link android.icu.text.AlphabeticIndex#getBucketIndex(java.lang.CharSequence) AlphabeticIndex#getBucketIndex(CharSequence)}
 * and {@link android.icu.text.AlphabeticIndex.ImmutableIndex#getBucketIndex(java.lang.CharSequence) AlphabeticIndex.ImmutableIndex#getBucketIndex(CharSequence)},
 * returned by {@link android.icu.text.AlphabeticIndex.ImmutableIndex#getBucket(int) AlphabeticIndex.ImmutableIndex#getBucket(int)},
 * and {@link android.icu.text.AlphabeticIndex#addRecord(java.lang.CharSequence,java.lang.Object) AlphabeticIndex#addRecord(CharSequence, Object)} adds a record
 * into a bucket according to the record's name.
 *
 * @param <V>
 *            Data type
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static class Bucket<V> implements java.lang.Iterable<android.icu.text.AlphabeticIndex.Record<V>> {

private Bucket() { throw new RuntimeException("Stub!"); }

/**
 * Get the label
 *
 * @return label for the bucket
 */

public java.lang.String getLabel() { throw new RuntimeException("Stub!"); }

/**
 * Is a normal, underflow, overflow, or inflow bucket
 *
 * @return is an underflow, overflow, or inflow bucket
 */

public android.icu.text.AlphabeticIndex.Bucket.LabelType getLabelType() { throw new RuntimeException("Stub!"); }

/**
 * Get the number of records in the bucket.
 *
 * @return number of records in bucket
 */

public int size() { throw new RuntimeException("Stub!"); }

/**
 * Iterator over the records in the bucket
 */

public java.util.Iterator<android.icu.text.AlphabeticIndex.Record<V>> iterator() { throw new RuntimeException("Stub!"); }

/**
 * Standard toString()
 */

public java.lang.String toString() { throw new RuntimeException("Stub!"); }
/**
 * Type of the label
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public enum LabelType {
/**
 * Normal
 */

NORMAL,
/**
 * Underflow (before the first)
 */

UNDERFLOW,
/**
 * Inflow (between scripts)
 */

INFLOW,
/**
 * Overflow (after the last)
 */

OVERFLOW;
}

}

/**
 * Immutable, thread-safe version of {@link android.icu.text.AlphabeticIndex AlphabeticIndex}.
 * This class provides thread-safe methods for bucketing,
 * and random access to buckets and their properties,
 * but does not offer adding records to the index.
 *
 * @param <V> The Record value type is unused. It can be omitted for this class
 * if it was omitted for the AlphabeticIndex that built it.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static final class ImmutableIndex<V> implements java.lang.Iterable<android.icu.text.AlphabeticIndex.Bucket<V>> {

private ImmutableIndex() { throw new RuntimeException("Stub!"); }

/**
 * Returns the number of index buckets and labels, including underflow/inflow/overflow.
 *
 * @return the number of index buckets
 */

public int getBucketCount() { throw new RuntimeException("Stub!"); }

/**
 * Finds the index bucket for the given name and returns the number of that bucket.
 * Use {@link #getBucket(int)} to get the bucket's properties.
 *
 * @param name the string to be sorted into an index bucket
 * @return the bucket number for the name
 */

public int getBucketIndex(java.lang.CharSequence name) { throw new RuntimeException("Stub!"); }

/**
 * Returns the index-th bucket. Returns null if the index is out of range.
 *
 * @param index bucket number
 * @return the index-th bucket
 */

public android.icu.text.AlphabeticIndex.Bucket<V> getBucket(int index) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.util.Iterator<android.icu.text.AlphabeticIndex.Bucket<V>> iterator() { throw new RuntimeException("Stub!"); }
}

/**
 * A (name, data) pair, to be sorted by name into one of the index buckets.
 * The user data is not used by the index implementation.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static class Record<V> {

private Record() { throw new RuntimeException("Stub!"); }

/**
 * Get the name
 *
 * @return the name
 */

public java.lang.CharSequence getName() { throw new RuntimeException("Stub!"); }

/**
 * Get the data
 *
 * @return the data
 */

public V getData() { throw new RuntimeException("Stub!"); }

/**
 * Standard toString()
 */

public java.lang.String toString() { throw new RuntimeException("Stub!"); }
}

}

