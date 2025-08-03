/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.storage.table.packed.read;

import com.android.storage.block.read.BlockData;
import com.android.storage.table.reader.IntValueTable.IntValueEntryMatcher;
import com.android.storage.table.reader.LongValueTable.LongValueEntryMatcher;
import com.android.storage.util.BitwiseUtils;

import java.util.Objects;

/**
 * A helper class that knows how to read packed tables from {@link BlockData}. Packed tables are a
 * sorted structure containing fixed-width records made up of a key of N bits and a value of the
 * remaining bits. Keys do not have to be unique. The combination of ordering by key and fixed-width
 * records makes packed tables quick to search by key and also compact.
 *
 * <p>The information about the entry size / key size / how the value is to be treated is encoded in
 * the first few bytes of the block data along with the shared table data.
 *
 * <p>The implementation is limited in that it can only read entries up to 8 bytes wide. Keys can be
 * (at most) signed 31-bit values. Entries can only be multiples of a byte.
 */
public final class PackedTableReader {

    /** The bit in the bit field used to store whether the value is to be treated as signed. */
    public static final int BIT_FIELD_SIGNED_VALUE_MASK = 0x01;

    /** The block data to read from . */
    private final BlockData mBlockData;

    /** The number of bytes in the BlockData used to hold table / entry metadata. */
    private final int mHeaderLengthBytes;

    /** The number of bytes per entry. */
    private final int mEntrySizeBytes;

    private final int mEntrySizeBits;

    private final int mKeySizeBits;

    private final int mValueSizeBits;

    private final int mEntryCount;

    /** Domain-specific data that should be common to all entries. */
    private final byte[] mSharedData;

    /**
     * True if the value is to be treated as a signed value, i.e. whether its sign should be
     * extended when converting it to a Java type.
     */
    private final boolean mSignedValue;

    /**
     * True if the value can fit in a signed / unsigned int (depending on {@link #mSignedValue}
     * without risk of overflow.
     */
    private final boolean mIntValueSupported;

    public PackedTableReader(BlockData blockData) {
        mBlockData = Objects.requireNonNull(blockData);

        int offset = 0;

        mSharedData = blockData.getTinyByteArray(offset);
        offset += Byte.BYTES + mSharedData.length;

        // Boolean properties are extracted from a 32-bit bit field.
        int bitField = blockData.getUnsignedByte(offset);
        offset += 1;
        mSignedValue = (bitField & BIT_FIELD_SIGNED_VALUE_MASK) > 0;

        mEntrySizeBytes = blockData.getUnsignedByte(offset);
        offset += 1;
        if (mEntrySizeBytes > 8) {
            throw new IllegalStateException(
                    "Entries cannot exceed 8 bytes in width, entrySizeBytes=" + mEntrySizeBytes);
        }
        mEntrySizeBits = mEntrySizeBytes * 8;

        mKeySizeBits = blockData.getUnsignedByte(offset);
        offset += 1;

        // There must be at least one key bit and at least one value bit. The key cannot exceed 31
        // bits in size.
        int maxKeySizeBits = Math.min(31, mEntrySizeBits - 1);
        if (mKeySizeBits < 1 || mKeySizeBits > maxKeySizeBits) {
            throw new IllegalStateException("keySizeBits must be >= 1 and <= " + maxKeySizeBits);
        }

        // The value bits are whatever entry bits are not key bits.
        mValueSizeBits = mEntrySizeBits - mKeySizeBits;
        if (mSignedValue) {
            mIntValueSupported = mValueSizeBits <= Integer.SIZE;
        } else {
            mIntValueSupported = mValueSizeBits <= Integer.SIZE - 1;
        }

        mHeaderLengthBytes = offset;

        // Calculate the entry count. Note: There can be more than 2^N entries because duplicate
        // keys are supported.
        mEntryCount = (blockData.getSize() - mHeaderLengthBytes) / mEntrySizeBytes;
    }

    /** Returns the number of bytes used to store each entry. */
    public int getEntrySizeBytes() {
        return mEntrySizeBytes;
    }

    /** Returns the number of bits devoted to the key. */
    public int getKeySizeBits() {
        return mKeySizeBits;
    }

    /** Returns true if the value is signed. */
    public boolean isValueSigned() {
        return mSignedValue;
    }

    /** Returns the number of bits devoted to the value. */
    public int getValueSizeBits() {
        return mValueSizeBits;
    }

    /** Returns the table's shared data. */
    public byte[] getSharedData() {
        return mSharedData;
    }

    /** Returns the number of entries in the table. */
    public int getEntryCount() {
        return mEntryCount;
    }

    /**
     * Returns an {@link Entry} with the specified key or {@code null} if there isn't one. If
     * multiple entries have the key, then an arbitrary entry with the key is returned.
     */
    public Entry getEntry(int key) {
        BitwiseUtils.checkUnsignedValueInRange(mKeySizeBits, key);
        return findLongValueEntry((k, v) -> key - k);
    }

    /** Returns the {@link Entry} with the specified index. */
    public Entry getEntryByIndex(int i) {
        long entryBytes = getEntryBytesForIndex(i);
        return new Entry(i, entryBytes);
    }

    /**
     * Finds an entry in the table using the supplied matcher, return {@code null} if none match. If
     * multiple entries in the table match then an arbitrary matching entry is returned.
     * Throws an {@link IllegalStateException} if the table's settings mean that there is a risk
     * of overflow with an int.
     */
    public Entry findIntValueEntry(IntValueEntryMatcher entryMatcher) {
        checkIntValueSupported();
        return findLongValueEntry(convertToLongValueEntryMatcher(entryMatcher));
    }

    /**
     * Finds an entry in the table using the supplied matcher, return {@code null} if none match. If
     * multiple entries in the table match then an arbitrary matching entry is returned.
     */
    public Entry findLongValueEntry(LongValueEntryMatcher entryMatcher) {
        int lower = 0;
        int upper = mEntryCount - 1;
        return binarySearch(lower, upper, entryMatcher);
    }

    private Entry binarySearch(int lower, int upper, LongValueEntryMatcher entryMatcher) {
        while (lower <= upper) {
            int mid = (lower + upper) / 2;

            long entryBytes = getEntryBytesForIndex(mid);
            int entryKey = getKeyFromEntryBytes(entryBytes);
            long entryValue = getLongValueFromEntryBytes(entryBytes);
            int compareResult = entryMatcher.compare(entryKey, entryValue);
            if (compareResult > 0) {
                lower = mid + 1;
            } else if (compareResult < 0) {
                upper = mid - 1;
            } else {
                return new Entry(mid, entryBytes);
            }
        }
        return null;
    }

    private long getEntryBytesForIndex(int index) {
        if (index < 0 || index >= mEntryCount) {
            throw new IndexOutOfBoundsException();
        }
        int byteOffset = mHeaderLengthBytes + (index * mEntrySizeBytes);
        return mBlockData.getValueAsLong(mEntrySizeBytes, byteOffset, mSignedValue);
    }

    private int getKeyFromEntryBytes(long entryBytes) {
        return getKeyFromEntryBytes(mEntrySizeBits, mKeySizeBits, entryBytes);
    }

    /**
     * Throws an {@link IllegalStateException} if the table's settings mean that there is a risk
     * of overflow with an int.
     */
    private int getIntValueFromEntryBytes(long entryBytes) {
        checkIntValueSupported();
        long value = getLongValueFromEntryBytes(entryBytes);
        return (int) value;
    }

    private long getLongValueFromEntryBytes(long entryBytes) {
        long value = BitwiseUtils.extractLowBits(mValueSizeBits, entryBytes);
        if (mSignedValue) {
            value = BitwiseUtils.signExtendToLong(mValueSizeBits, value);
        }
        return value;
    }

    private void checkIntValueSupported() {
        if (!mIntValueSupported) {
            throw new IllegalStateException("value size is too big to support int,"
                    + " mSignedValue=" + mSignedValue + ", mValueSizeBits=" + mValueSizeBits);
        }
    }

    /**
     * An entry from a packed table.
     */
    public final class Entry {

        final int mIndex;

        /** The entry bytes held in a long. */
        private final long mEntryBytes;

        /** The key, lazily initialized. Use {@link #getKey()} to access the key. */
        private int mKey = -1;

        Entry(int index, long entryBytes) {
            mIndex = index;
            mEntryBytes = entryBytes;
        }

        /** Returns the next entry in the table, {@code null} if there isn't one. */
        public Entry getNext() {
            if (mIndex >= mEntryCount - 1) {
                return null;
            }
            int nextIndex = mIndex + 1;
            return getEntryByIndex(nextIndex);
        }

        /** Returns the previous entry in the table, {@code null} if there isn't one. */
        public Entry getPrevious() {
            if (mIndex == 0) {
                return null;
            }
            int previousIndex = mIndex - 1;
            return getEntryByIndex(previousIndex);
        }

        /**
         * Finds an entry using the supplied matcher via a binary search. Returns {@code null} if no
         * entry matches. Uses this entry as a starting point.
         * Throws an {@link IllegalStateException} if the table's settings mean that there is a risk
         * of overflow with an int.
         */
        public Entry findNearbyEntry(IntValueEntryMatcher entryMatcher) {
            checkIntValueSupported();
            return findNearbyEntry(convertToLongValueEntryMatcher(entryMatcher));
        }

        /**
         * Finds an entry using the supplied matcher via a binary search. Returns {@code null} if no
         * entry matches. Uses this entry as a starting point.
         */
        public Entry findNearbyEntry(LongValueEntryMatcher entryMatcher) {
            int key = getKey();
            int compare = entryMatcher.compare(key, getLongValue());
            if (compare == 0) {
                return this;
            }

            int lower, upper;
            if (compare < 0) {
                lower = 0;
                upper = mIndex;
            } else {
                lower = mIndex;
                upper = mEntryCount - 1;
            }
            return binarySearch(lower, upper, entryMatcher);
        }

        /** Returns the key for this entry. */
        public int getKey() {
            if (mKey == -1) {
                mKey = getKeyFromEntryBytes(mEntryBytes);
            }
            return mKey;
        }

        /** Returns the index for this entry. */
        public int getIndex() {
            return mIndex;
        }

        /**
         * Returns the entry's value as an int.
         * Throws an {@link IllegalStateException} if the table's settings mean that there is a risk
         * of overflow with an int.
         */
        public int getIntValue() {
            return getIntValueFromEntryBytes(mEntryBytes);
        }

        /**
         * Returns the entry's value as a long.
         */
        public long getLongValue() {
            return getLongValueFromEntryBytes(mEntryBytes);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Entry entry = (Entry) o;
            return mIndex == entry.mIndex
                    && mEntryBytes == entry.mEntryBytes;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mIndex);
        }
    }

    private static int getKeyFromEntryBytes(int entrySizeBits, int keySizeBits, long entryBytes) {
        long mask = BitwiseUtils.getMidBitsMask(entrySizeBits, keySizeBits);

        // Extract the key bits and shift them right.
        long keyBits = mask & entryBytes;
        int valueSizeBits = entrySizeBits - keySizeBits;
        return (int) (keyBits >>> valueSizeBits);
    }

    private static LongValueEntryMatcher convertToLongValueEntryMatcher(
            IntValueEntryMatcher entryMatcher) {
        return (key, value) -> entryMatcher.compare(key, (int) value);
    }
}
