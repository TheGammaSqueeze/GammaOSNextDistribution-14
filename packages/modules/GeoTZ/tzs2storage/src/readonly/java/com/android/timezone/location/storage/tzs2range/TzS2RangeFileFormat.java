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

package com.android.timezone.location.storage.tzs2range;

import static com.android.storage.s2.S2Support.FACE_BIT_COUNT;
import static com.android.storage.s2.S2Support.MAX_FACE_ID;
import static com.android.storage.s2.S2Support.MAX_S2_LEVEL;

import com.android.storage.block.read.BlockFileReader;
import com.android.storage.s2.S2Support;
import com.android.storage.util.BitwiseUtils;
import com.android.storage.util.Conditions;

import java.util.Objects;

/**
 * Holds information about the format of a tz s2 data file, which is a type of block file (see
 * {@link BlockFileReader}). Some information is hardcoded and some is parameterized using
 * information read from the file's header block. This class contains useful methods for
 * validation, interpretation and storage of data in a file with the specified format.
 */
public final class TzS2RangeFileFormat {

    /** The block type of the tz s2 data file header (held in block 0). */
    public static final int BLOCK_TYPE_HEADER = 1;

    /**
     * The block type used for padding between the header and suffix tables that allows for future
     * expansion. See {@link #getSuffixTableBlockIdOffset()}.
     */
    public static final int BLOCK_TYPE_PADDING = 20;

    /** The block type of a populated suffix table. */
    public static final int BLOCK_TYPE_SUFFIX_TABLE = 10;

    /** The expected magic value of a tz s2 data file. */
    public static final char MAGIC = 0xAFCF;

    /** The format version of the tz s2 data file, read and written. */
    public static final int VERSION = 1;

    private final int mDataS2Level;

    private final int mPrefixBitCount;

    private final int mMaxPrefixValue;

    private final int mSuffixTableBlockIdOffset;

    private final int mSuffixBitCount;

    private final int mMaxSuffixValue;

    private final int mTableEntryByteCount;

    private final int mTableEntryBitCount;

    private final int mTableEntryRangeLengthBitCount;

    private final int mTableEntryMaxRangeLengthValue;

    /** The number of bits in each suffix table entry used to store the TZ ID Set ID. */
    private final int mTableEntryTzIdSetIdBitCount;

    private final int mTableEntryMaxTzIdSetIdValue;

    /**
     * The number of bits in a cell ID of the data storage level that have fixed values, i.e. the
     * final "1" followed by all zeros.
     */
    private final int mUnusedCellIdBitCount;

    /**
     * Creates a new file format. This constructor validates the values against various hard-coded
     * constraints and will throw an {@link IllegalArgumentException} if they are not satisfied.
     */
    public TzS2RangeFileFormat(int s2Level, int prefixBitCount, int suffixBitCount,
            int suffixTableBlockIdOffset, int tableEntryBitCount, int entryRangeLengthBitCount) {

        Conditions.checkArgInRange("s2Level", s2Level, 0, MAX_S2_LEVEL);

        // prefixBitCount must include at least the face bits and one more, it makes the logic
        // for mMaxPrefixValue easier below. We also assume that prefix and suffix will be 31-bits
        // as that makes sure they can be represented, unsigned in a Java int. A prefix / suffix
        // of 31-bits (each) should be enough for anyone(TM). 31-bits = ~15 S2 levels.
        // Anything more than level 18 for time zone data (i.e. prefix PLUS suffix) will be very
        // detailed so it's unlikely this constraint will be a problem.
        Conditions.checkArgInRange("prefixBitCount", prefixBitCount, 4, Integer.SIZE - 1);

        // The suffix table uses fixed length records that are broken into a key and a value. The
        // implementation requires the key is an unsigned int value, i.e. 31-bits, and the value can
        // be held in an int value.

        // The key of a suffix table entry is used to store the suffix for the cell ID at the start
        // of a range of S2 cells (the prefix for that cell ID is implicit and the same for every
        // entry in the table, see prefixBitCount).
        int tableEntryKeyBitCount = suffixBitCount;
        Conditions.checkArgInRange(
                "tableEntryKeyBitCount", tableEntryKeyBitCount, 1, Integer.SIZE - 1);

        // The value of a suffix table entry is used to hold both the range length and the TZ ID set
        // ID. See methods below that extract these components from an int. Hence, we check they
        // will fit.
        int tableEntryValueBitCount = tableEntryBitCount - tableEntryKeyBitCount;
        Conditions.checkArgInRange(
                "tableEntryValueBitCount", tableEntryValueBitCount, 1, Integer.SIZE);

        if (S2Support.storageBitCountForLevel(s2Level) != prefixBitCount + suffixBitCount) {
            // s2Level implies cellIds have a certain number of "storage bits", the prefix and
            // suffix must consume all the bits.
            throw new IllegalArgumentException("prefixBitCount=" + prefixBitCount
                    + " + suffixBitCount=" + suffixBitCount + " must be correct for the s2Level ("
                    + S2Support.storageBitCountForLevel(s2Level) + ")");
        }
        if (suffixTableBlockIdOffset < 1) {
            // The format includes a header block, so there will always be an adjustment for at
            // least that one block.
            throw new IllegalArgumentException(
                    "suffixTableBlockIdOffset=" + suffixTableBlockIdOffset + " must be >= 1");
        }
        if (tableEntryBitCount < 0 || tableEntryBitCount % Byte.SIZE != 0
                || tableEntryBitCount > Long.SIZE) {
            // The classes used to read suffix tables only support entries that are a multiples of
            // a byte. They also restrict to up a maximum of 8-bytes per table entry.
            throw new IllegalArgumentException(
                    "suffixTableEntryBitCount=" + tableEntryBitCount
                            + " must be >= 0, be divisible by 8, and be no more than 64 bits");
        }

        // The range length bit count must be positive. Also, the suffix table entry has to fit
        // the start value (suffixBitCount), the range length (min 2 bit but typically more) and
        // the TZ ID Set ID (min 2 bit, but typically more). For simplicity below we ensure we
        // can hold the maximum range length value in an unsigned Java int, so 31-bits.
        Conditions.checkArgInRange("entryRangeLengthBitCount", entryRangeLengthBitCount,
                2, Math.min(tableEntryBitCount - 2, Integer.SIZE - 1));

        // Set all the fields. The fields are either set directly from parameters or derived from
        // the values given.

        mDataS2Level = s2Level;
        mPrefixBitCount = prefixBitCount;

        // Prefix value: contains the face ID plus one or more bits for the index.
        int cellIdIndexBitCount = prefixBitCount - FACE_BIT_COUNT;
        mMaxPrefixValue = (int)
                ((((long) MAX_FACE_ID) << cellIdIndexBitCount)
                        | BitwiseUtils.maxUnsignedValue(cellIdIndexBitCount));

        mSuffixBitCount = suffixBitCount;
        mMaxSuffixValue = (int) BitwiseUtils.maxUnsignedValue(suffixBitCount);

        // prefixBitCount + suffixBitCount are all the "useful" bits. The remaining bits in a 64-bit
        // cell ID are the trailing "1" (which we don't need to store) and the rest are zeros.
        mUnusedCellIdBitCount = Long.SIZE - (prefixBitCount + suffixBitCount);

        mTableEntryBitCount = tableEntryBitCount;
        mTableEntryByteCount = tableEntryBitCount / 8;

        mTableEntryRangeLengthBitCount = entryRangeLengthBitCount;
        mTableEntryMaxRangeLengthValue =
                (int) BitwiseUtils.maxUnsignedValue(entryRangeLengthBitCount);

        // Everything in a suffix table entry that isn't the suffix or range length is the TZ ID Set
        // ID so we can calculate it from the information given.
        mTableEntryTzIdSetIdBitCount =
                tableEntryBitCount - (suffixBitCount + entryRangeLengthBitCount);
        mTableEntryMaxTzIdSetIdValue =
                (int) BitwiseUtils.maxUnsignedValue(mTableEntryTzIdSetIdBitCount);

        mSuffixTableBlockIdOffset = suffixTableBlockIdOffset;
    }

    /** Returns the S2 level of all geo data stored in the file. */
    public int getS2Level() {
        return mDataS2Level;
    }

    /**
     * Returns the number of prefix bits from an S2 cell ID used to identify the block containing
     * ranges.
     */
    public int getPrefixBitCount() {
        return mPrefixBitCount;
    }

    /**
     * Returns the maximum valid value that {@link #getPrefixBitCount()} can represent. Note: This
     * is not just the number of bits: the prefix contains the face ID which can only be 0 - 5
     * (inclusive).
     */
    public int getMaxPrefixValue() {
        return mMaxPrefixValue;
    }

    /**
     * Returns the number of "useful" bits of an S2 cell ID in the data after
     * {@link #getPrefixBitCount()}, i.e. not including the trailing "1".
     * Dependent on the {@link #getS2Level()}, which dictates the number of storage bits in every
     * cell ID in a file, and {@link #getPrefixBitCount()}.
     */
    public int getSuffixBitCount() {
        return mSuffixBitCount;
    }

    /**
     * Returns the maximum value that {@link #getSuffixBitCount()} can represent.
     */
    public int getMaxSuffixValue() {
        return mMaxSuffixValue;
    }

    /**
     * Returns the number of bits in each suffix table entry. i.e.
     * {@link #getTableEntryByteCount()} * 8
     */
    public int getTableEntryBitCount() {
        return mTableEntryBitCount;
    }

    /** Returns the number of bytes in each suffix table entry. */
    public int getTableEntryByteCount() {
        return mTableEntryByteCount;
    }

    /** Return the number of bits in each suffix table entry used to store the length of a range. */
    public int getTableEntryRangeLengthBitCount() {
        return mTableEntryRangeLengthBitCount;
    }

    /** Returns the maximum value that {@link #getTableEntryRangeLengthBitCount()} can represent. */
    public int getTableEntryMaxRangeLengthValue() {
        return mTableEntryMaxRangeLengthValue;
    }

    /** Returns the maximum value that can be used as a TZ IDS set ID. */
    public int getMaxTzIdSetIdValue() {
        return mTableEntryMaxTzIdSetIdValue;
    }

    /**
     * Returns thee offset to apply to the prefix value to compute the block ID holding the data for
     * that prefix. Always &gt;= 1 to account for the header block.
     */
    public int getSuffixTableBlockIdOffset() {
        return mSuffixTableBlockIdOffset;
    }

    /** Extracts the prefix bits from a cell ID and returns them as an unsigned int. */
    public int extractPrefixValueFromCellId(long cellId) {
        checkS2Level("cellId", cellId);
        return (int) (cellId >>> (mSuffixBitCount + mUnusedCellIdBitCount));
    }

    /** Extracts the suffix bits from a cell ID and returns them as an unsigned int. */
    public int extractSuffixValueFromCellId(long cellId) {
        checkS2Level("cellId", cellId);
        return (int) (cellId >>> (mUnusedCellIdBitCount)) & mMaxSuffixValue;
    }

    /** Extracts the range length from a table entry value. */
    public int extractRangeLengthFromTableEntryValue(int value) {
        return extractRangeLength(value,
                mTableEntryRangeLengthBitCount + mTableEntryTzIdSetIdBitCount,
                mTableEntryRangeLengthBitCount);
    }

    private static int extractRangeLength(int value, int suffixRecordBitCount,
            int rangeLengthBitCount) {
        long mask = BitwiseUtils.getMidBitsMask(suffixRecordBitCount, rangeLengthBitCount);
        return (int) ((value & mask) >> (suffixRecordBitCount - rangeLengthBitCount));
    }

    /** Extracts the TZ IDs Set ID from a table entry value. */
    public int extractTzIdSetIdFromTableEntryValue(int value) {
        return extractTzIdSetId(value, mTableEntryTzIdSetIdBitCount);
    }

    private static int extractTzIdSetId(int value, int bitCount) {
        long mask = BitwiseUtils.getLowBitsMask(bitCount);
        return (int) (value & mask);
    }

    /** Creates a table entry value from a range length and a TZ IDs set ID. */
    public long createSuffixTableValue(int rangeLength, int tzIdSetId) {
        Conditions.checkArgInRange(
                "rangeLength", rangeLength, 0, getTableEntryMaxRangeLengthValue());
        Conditions.checkArgInRange("tzIdSetId", tzIdSetId, 0, getMaxTzIdSetIdValue());
        long value = ((long) rangeLength) << mTableEntryTzIdSetIdBitCount;
        value |= tzIdSetId;
        return value;
    }

    /** Creates a cell ID from a prefix and a suffix component. */
    public long createCellId(int prefixValue, int suffixValue) {
        Conditions.checkArgInRange("prefixValue", prefixValue, 0, mMaxPrefixValue);
        Conditions.checkArgInRange("suffixValue", suffixValue, 0, mMaxSuffixValue);
        long cellId = prefixValue;
        cellId <<= mSuffixBitCount;
        cellId |= suffixValue;
        cellId <<= 1;
        cellId |= 1;
        cellId <<= mUnusedCellIdBitCount - 1;

        checkS2Level("cellId", cellId);
        return cellId;
    }

    /** Extracts the face ID bits from a prefix value. */
    public int extractFaceIdFromPrefix(int prefixValue) {
        return prefixValue >>> (mPrefixBitCount - FACE_BIT_COUNT);
    }

    /**
     * Calculates the number of cell IDs in the given range. Throws {@link IllegalArgumentException}
     * if {@code rangeStartCellId} is "higher" than {@code rangeEndCellId} or the range length would
     * be outside of the int range.
     *
     * @param rangeStartCellId the start of the range (inclusive)
     * @param rangeEndCellId the end of the range (exclusive)
     */
    public int calculateRangeLength(long rangeStartCellId, long rangeEndCellId) {
        checkS2Level("rangeStartCellId", rangeStartCellId);
        checkS2Level("rangeEndCellId", rangeEndCellId);

        // Convert to numeric values that can just be subtracted without worrying about sign
        // issues.
        long rangeEndCellIdNumeric = rangeEndCellId >>> mUnusedCellIdBitCount;
        long rangeStartCellIdNumeric = rangeStartCellId >>> mUnusedCellIdBitCount;
        if (rangeStartCellIdNumeric >= rangeEndCellIdNumeric) {
            throw new IllegalArgumentException(
                    "rangeStartCellId=" + cellIdToString(rangeStartCellId)
                            + " is >= rangeEndCellId=" + cellIdToString(rangeEndCellId));
        }
        long differenceNumeric = rangeEndCellIdNumeric - rangeStartCellIdNumeric;
        if (differenceNumeric < 0 || differenceNumeric > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("rangeLength=" + differenceNumeric
                    + " is outside of expected range");
        }
        return (int) differenceNumeric;
    }

    /** Formats a cellId in terms of prefix and suffix values. */
    public String cellIdToString(long cellId) {
        int prefix = extractPrefixValueFromCellId(cellId);
        int suffix = extractSuffixValueFromCellId(cellId);
        String prefixBitsString = BitwiseUtils.toUnsignedString(mPrefixBitCount, prefix);
        String suffixBitsString = BitwiseUtils.toUnsignedString(mSuffixBitCount, suffix);
        return "cellId{prefix=" + prefix + " (" + prefixBitsString + ")"
                + ", suffix=" + suffix + " (" + suffixBitsString + ")"
                + "}";
    }

    @Override
    public String toString() {
        return "TzS2RangeFileFormat{"
                + "mDataS2Level=" + mDataS2Level
                + ", mPrefixBitCount=" + mPrefixBitCount
                + ", mMaxPrefixValue=" + mMaxPrefixValue
                + ", mSuffixBitCount=" + mSuffixBitCount
                + ", mMaxSuffixValue=" + mMaxSuffixValue
                + ", mTableEntryBitCount=" + mTableEntryBitCount
                + ", mTableEntryRangeLengthBitCount=" + mTableEntryRangeLengthBitCount
                + ", mTableEntryMaxRangeLengthValue=" + mTableEntryMaxRangeLengthValue
                + ", mTableEntryTzIdSetIdBitCount=" + mTableEntryTzIdSetIdBitCount
                + ", mTableEntryMaxTzIdSetIdValue=" + mTableEntryMaxTzIdSetIdValue
                + ", mSuffixTableBlockIdOffset=" + mSuffixTableBlockIdOffset
                + ", mUnusedCellIdBitCount=" + mUnusedCellIdBitCount
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TzS2RangeFileFormat that = (TzS2RangeFileFormat) o;
        return mDataS2Level == that.mDataS2Level
                && mPrefixBitCount == that.mPrefixBitCount
                && mMaxPrefixValue == that.mMaxPrefixValue
                && mSuffixBitCount == that.mSuffixBitCount
                && mMaxSuffixValue == that.mMaxSuffixValue
                && mTableEntryBitCount == that.mTableEntryBitCount
                && mTableEntryRangeLengthBitCount == that.mTableEntryRangeLengthBitCount
                && mTableEntryMaxRangeLengthValue == that.mTableEntryMaxRangeLengthValue
                && mTableEntryTzIdSetIdBitCount == that.mTableEntryTzIdSetIdBitCount
                && mTableEntryMaxTzIdSetIdValue == that.mTableEntryMaxTzIdSetIdValue
                && mSuffixTableBlockIdOffset == that.mSuffixTableBlockIdOffset
                && mUnusedCellIdBitCount == that.mUnusedCellIdBitCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mDataS2Level, mPrefixBitCount, mMaxPrefixValue, mSuffixBitCount,
                mMaxSuffixValue, mTableEntryBitCount, mTableEntryRangeLengthBitCount,
                mTableEntryMaxRangeLengthValue, mTableEntryTzIdSetIdBitCount,
                mTableEntryMaxTzIdSetIdValue, mSuffixTableBlockIdOffset, mUnusedCellIdBitCount);
    }

    private void checkS2Level(String name, long cellId) {
        if (S2Support.getS2Level(cellId) != mDataS2Level) {
            throw new IllegalArgumentException(
                    name + "=" + S2Support.cellIdToString(cellId) + " is at the wrong level");
        }
    }
}
