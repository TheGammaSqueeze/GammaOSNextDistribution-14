/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.internal.util;

import android.util.ArraySet;
import android.util.LongSparseArray;
import android.util.SparseArray;

import com.android.internal.util.Preconditions;

/**
 * SparseArray mapping two integer keys to an Object. It encodes the two {@code int}s into a {@code
 * long} with the first key stored in the most significant 32 bits and the second key stored in the
 * least significant 32 bits. This class is wrapper for {@link LongSparseArray} and handles encoding
 * and decoding the keys.
 *
 * @see LongSparseArray
 * @param <E> value to be stored
 *
 * @hide
 */
public class PairSparseArray<E> {
    /** Bitmask for casting an {@code int} into a {@code long} without sign extension. */
    private static final long LEAST_SIGNIFICANT_BITMASK = 0xffffffffL;
    private static final int INITIAL_CAPACITY = 10;

    /**
     * Underlying long->Object map data structure.
     */
    private final LongSparseArray<E> mValues;
    /**
     * First key to second keys mapping to allow easier operation applied to all the entries with
     * the first key.
     */
    private final SparseArray<ArraySet<Integer>> mSecondKeysByFirstKey = new SparseArray<>();

    /** Creates a new PairSparseArray with initial capacity of {@link #INITIAL_CAPACITY}. */
    public PairSparseArray() {
        this(INITIAL_CAPACITY);
    }

    /** Creates a new PairSparseArray. */
    public PairSparseArray(int initialCapacity) {
        mValues = new LongSparseArray<>(initialCapacity);
    }

    /**
     * Gets all the second keys for the first key.
     */
    public ArraySet<Integer> getSecondKeysForFirstKey(int firstKey) {
        if (!mSecondKeysByFirstKey.contains(firstKey)) {
            return new ArraySet<>();
        }
        return new ArraySet<>(mSecondKeysByFirstKey.get(firstKey));
    }

    /**
     * Gets all the first keys.
     */
    public ArraySet<Integer> getFirstKeys() {
        ArraySet<Integer> firstKeys = new ArraySet<>();
        for (int i = 0; i < mSecondKeysByFirstKey.size(); i++) {
            firstKeys.add(mSecondKeysByFirstKey.keyAt(i));
        }
        return new ArraySet<>(firstKeys);
    }

    /**
     * Puts the keys and value into the array, optimizing for the case where
     * the encoded key is greater than all existing keys in the array.
     */
    public void append(int firstKey, int secondKey, E value) {
        Preconditions.checkArgument(value != null, "Value must not be null");
        long key = encodeKeyPair(firstKey, secondKey);
        mValues.append(key, value);
        addSecondKeyByFirstKey(firstKey, secondKey);
    }

    /**
     * Removes all key-value mappings from this array.
     */
    public void clear() {
        mValues.clear();
        mSecondKeysByFirstKey.clear();
    }

    /**
     * Returns true if the key pair exists in the array. This is equivalent to
     * {@link #indexOfKeyPair(int, int)} >= 0.
     *
     * @return {@code true} if the keys are mapped
     */
    public boolean contains(int firstKey, int secondKey) {
        return indexOfKeyPair(firstKey, secondKey) >= 0;
    }

    /**
     * Removes the mapping from the specified keys, if there was any.
     */
    public void delete(int firstKey, int secondKey) {
        long key = encodeKeyPair(firstKey, secondKey);
        mValues.delete(key);
        removeSecondKeyByFirstKey(firstKey, secondKey);
    }

    /**
     * Gets the Object mapped from the specified key, or {@code null}
     * if no such mapping has been made.
     *
     * @see #get(int, int, Object)
     */
    public E get(int firstKey, int secondKey) {
        return get(firstKey, secondKey, null);
    }

    /**
     * Gets the Object mapped from the specified key, or the specified Object
     * if no such mapping has been made.
     *
     * @param firstKey the integer key stored in the most significant bits
     * @param secondKey the integer key stored in the least significant bits
     * @param valueIfKeyPairNotFound the value to return if {@code firstKey} and {@code secondKey}
     * have not been mapped.
     *
     * @return the value mapped to {@code firstKey} and {@code secondKey}, or {@code
     * valueIfKeyPairNotFound} if keys have not been mapped.
     */
    public E get(int firstKey, int secondKey, E valueIfKeyPairNotFound) {
        int index = indexOfKeyPair(firstKey, secondKey);
        if (index < 0) {
            return valueIfKeyPairNotFound;
        }
        return valueAt(index);
    }

    /**
     * Returns the index for which {@link #keyPairAt} would return the
     * specified keys, or a negative number if the specified
     * keys are not mapped.
     */
    public int indexOfKeyPair(int firstKey, int secondKey) {
        long key = encodeKeyPair(firstKey, secondKey);
        return mValues.indexOfKey(key);
    }

    /**
     * Returns an index for which {@link #valueAt} would return the
     * specified key, or a negative number if no keys map to the
     * specified value.
     *
     * @see LongSparseArray#indexOfValue(Object)
     */
    public int indexOfValue(E value) {
        Preconditions.checkArgument(value != null, "Value must not be null");
        return mValues.indexOfValue(value);
    }

    /**
     * Given an index in the range <code>0...size()-1</code>, returns
     * the key pair from the <code>index</code>th key-value mapping that this
     * PairSparseArray stores.
     *
     * @return int array of size 2 with the first and second key at indices 0 and 1 respectively.
     * @see LongSparseArray#keyAt(int)
     */
    public int[] keyPairAt(int index) {
        return decodeKeyPair(mValues.keyAt(index));
    }

    /**
     * Adds a mapping from the specified keys to the specified value,
     * replacing the previous mapping if there was one.
     */
    public void put(int firstKey, int secondKey, E value) {
        Preconditions.checkArgument(value != null, "Value must not be null");
        long key = encodeKeyPair(firstKey, secondKey);
        mValues.put(key, value);
        addSecondKeyByFirstKey(firstKey, secondKey);
    }

    /**
     * Alias for {@link #delete(int, int)}
     */
    public void remove(int firstKey, int secondKey) {
        delete(firstKey, secondKey);
    }

    /**
     * Removes the mapping at the specified index.
     *
     * @see LongSparseArray#removeAt(int)
     */
    public void removeAt(int index) {
        int[] keyPair = keyPairAt(index);
        removeSecondKeyByFirstKey(keyPair[0], keyPair[1]);
        mValues.removeAt(index);
    }

    /**
     * Given an index in the range <code>0...size()-1</code>, sets a new
     * value for the <code>index</code>th key-value mapping that this
     * PairSparseArray stores.
     *
     * @see LongSparseArray#setValueAt(int, Object)
     */
    public void setValueAt(int index, E value) {
        Preconditions.checkArgument(value != null, "Value must not be null");
        mValues.setValueAt(index, value);
    }

    /** Returns the number of key-value mappings that this PairSparseArray currently stores. */
    public int size() {
        return mValues.size();
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation composes a string by iterating over its mappings.
     */
    @Override
    public String toString() {
        if (size() <= 0) {
            return "{}";
        }

        // 34 is an overestimate of the number of characters
        // to expect per mapping to avoid resizing the buffer.
        StringBuilder buffer = new StringBuilder(size() * 34);
        buffer.append('{');
        for (int i = 0; i < size(); i++) {
            if (i > 0) {
                buffer.append(", ");
            }
            buffer.append('[');
            int[] keyPair = keyPairAt(i);
            buffer.append(keyPair[0]);
            buffer.append(", ");
            buffer.append(keyPair[1]);
            buffer.append(']');
            buffer.append('=');
            Object value = valueAt(i);
            if (value != this) {
                buffer.append(value);
            } else {
                buffer.append("(this Map)");
            }
        }
        buffer.append('}');
        return buffer.toString();
    }

    /**
     * Given an index in the range <code>0...size()-1</code>, returns
     * the value from the <code>index</code>th key-value mapping that this
     * PairSparseArray stores.
     *
     * @see LongSparseArray#valueAt(int)
     */
    public E valueAt(int index) {
        return mValues.valueAt(index);
    }

    /**
     * Encodes the given pair of integer keys into a combined long with
     * {@code firstKey} stored in the most significant 32 bits and
     * {@code secondKey} stored in the least significant 32 bits.
     */
    private long encodeKeyPair(int firstKey, int secondKey) {
        return (((long) firstKey) << 32) | (secondKey & LEAST_SIGNIFICANT_BITMASK);
    }

    /**
     * Decode the {@code long} key used for {@link #mValues} into an
     * integer array of size two, with the first key extracted from
     * the most significant 32 bits and the second key extracted from
     * the least significant 32 bits.
     */
    private int[] decodeKeyPair(long key) {
        int firstKey = (int) (key >> 32);
        int secondKey = (int) key;
        return new int[] {firstKey, secondKey};
    }

    private void addSecondKeyByFirstKey(int firstKey, int secondKey) {
        if (!mSecondKeysByFirstKey.contains(firstKey)) {
            mSecondKeysByFirstKey.put(firstKey, new ArraySet<>());
        }
        mSecondKeysByFirstKey.get(firstKey).add(secondKey);
    }

    private void removeSecondKeyByFirstKey(int firstKey, int secondKey) {
        int index = mSecondKeysByFirstKey.indexOfKey(firstKey);
        if (index < 0) {
            // First key not found, do nothing.
            return;
        }
        mSecondKeysByFirstKey.valueAt(index).remove(secondKey);
        if (mSecondKeysByFirstKey.valueAt(index).isEmpty()) {
            mSecondKeysByFirstKey.removeAt(index);
        }
    }
}
