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

package com.android.telephony.sats2range.read;

import static com.android.storage.s2.S2Support.cellIdToString;
import static com.android.storage.s2.S2Support.getS2Level;

import com.android.storage.block.read.BlockData;
import com.android.storage.s2.S2LevelRange;
import com.android.storage.table.packed.read.IntValueTypedPackedTable;
import com.android.storage.util.BitwiseUtils;
import com.android.storage.util.Visitor;

import java.util.Objects;

/**
 * The main type of block for a satellite S2 data file.
 *
 * <p>Logically, each suffix table block holds zero or more entries for S2 ranges, e.g.:
 * <pre>
 *     startCellId=X, endCellId=Y
 * </pre>
 *
 * <p>Tables are generated so that all entries in the table have the same S2 level and "prefix
 * bits" for the S2 cell IDs they describe, i.e. if the table's assigned prefix is "1011000", then
 * every cell ID included in every range entry (i.e. from X to Y) must start with "1011000". The
 * entries in the table are ordered by startCellId and ranges cannot overlap. There is only one
 * block / suffix table for each possible prefix.
 *
 * <p>Note that because the endCellId is <em>exclusive</em>, the last entry's endCellId <em>can</em>
 * refer the first S2 cell ID from the next prefix, or wrap around to face 0 for the last entry
 * for face ID 5.
 *
 * <p>Any S2 cell id with a prefix that is not covered by a range entry in the associated table can
 * be inferred to have a value of zero.
 *
 * <p>Entries can be obtained by called methods such as {@link #getEntryByIndex(int)},
 * {@link #findEntryByCellId(long)}.
 */
public final class SuffixTableBlock {

    private final SatS2RangeFileFormat mFileFormat;

    private final SuffixTableBlockDelegate mDelegate;

    private final int mPrefix;

    /**
     * The implementation of the suffix table block. Suffix table blocks have two main
     * implementations: zero-length blocks used to represent empty tables, and blocks containing
     * {@link IntValueTypedPackedTable} data. Since they are so different they are implemented
     * independently.
     */
    interface SuffixTableBlockDelegate {

        /** Returns the prefix for cell IDs in this table. */
        int getPrefix();

        /**
         * Returns the entry containing the specified cell ID, or {@code null} if there isn't one.
         */
        Entry findEntryByCellId(long cellId);

        /**
         * Returns the entry with the specified index. Throws {@link IndexOutOfBoundsException} if
         * the index is invalid.
         */
        Entry findEntryByIndex(int i);

        /** Returns the number of entries in the table. */
        int getEntryCount();
    }

    private SuffixTableBlock(SatS2RangeFileFormat fileFormat, SuffixTableBlockDelegate delegate) {
        mFileFormat = Objects.requireNonNull(fileFormat);
        mDelegate = Objects.requireNonNull(delegate);
        mPrefix = delegate.getPrefix();
    }

    /**
     * Creates a populated {@link SuffixTableBlock} by interpreting {@link BlockData} and using
     * the supplied format information.
     */
    public static SuffixTableBlock createPopulated(
            SatS2RangeFileFormat fileFormat, BlockData blockData) {
        if (blockData.getSize() == 0) {
            throw new IllegalArgumentException("blockData=" + blockData + ", is zero length");
        }
        IntValueTypedPackedTable packedTable = new IntValueTypedPackedTable(blockData);
        PopulatedSuffixTableBlock delegate = new PopulatedSuffixTableBlock(fileFormat, packedTable);
        return new SuffixTableBlock(fileFormat, delegate);
    }

    /**
     * Creates an unpopulated {@link SuffixTableBlock} for the supplied prefix and using
     * the supplied format information.
     */
    public static SuffixTableBlock createEmpty(SatS2RangeFileFormat fileFormat, int prefix) {
        return new SuffixTableBlock(fileFormat, new UnpopulatedSuffixTableBlock(prefix));
    }

    /** Returns the prefix for this table. */
    public int getPrefix() {
        return mDelegate.getPrefix();
    }

    /**
     * Returns the entry for a given cell ID or {@code null} if there isn't one. The
     * {@code cellId} must be the same level as the table and have the same prefix otherwise an
     * {@link IllegalArgumentException} is thrown.
     */
    public Entry findEntryByCellId(long cellId) {
        if (getS2Level(cellId) != mFileFormat.getS2Level()) {
            throw new IllegalArgumentException(
                    cellIdToString(cellId) + " s2 level is not "
                            + mFileFormat.getS2Level());
        }
        if (mFileFormat.extractPrefixValueFromCellId(cellId) != mPrefix) {
            String prefixBitString =
                    BitwiseUtils.toUnsignedString(mFileFormat.getPrefixBitCount(), mPrefix);
            throw new IllegalArgumentException(
                    cellId + "(" + mFileFormat.cellIdToString(cellId)
                            + ") does not have prefix bits " + mPrefix
                            + " (" + prefixBitString + ")");
        }

        return mDelegate.findEntryByCellId(cellId);
    }

    /** Returns the entry at the specified index. */
    public Entry getEntryByIndex(int i) {
        return mDelegate.findEntryByIndex(i);
    }

    /** Returns the number of entries in the table. */
    public int getEntryCount() {
        return mDelegate.getEntryCount();
    }

    /** A {@link Visitor} for the {@link SuffixTableBlock}. See {@link #visit} */
    public interface SuffixTableBlockVisitor extends Visitor {

        /** Called after {@link #begin()}, once. */
        void visit(SuffixTableBlock suffixTableBlock) throws VisitException;
    }

    /**
     * Issues callbacks to the supplied {@link SuffixTableBlockVisitor}.
     */
    public void visit(SuffixTableBlockVisitor visitor) throws Visitor.VisitException {
        try {
            visitor.begin();
            visitor.visit(this);
        } finally {
            visitor.end();
        }
    }

    /**
     * An entry from the {@link SuffixTableBlock}. Use {@link #getSuffixTableRange()} to get the
     * full, interpreted entry data.
     */
    public abstract static class Entry {

        /** Returns the position of this entry in the table. */
        public abstract int getIndex();

        /** Returns the data for this entry. */
        public abstract S2LevelRange getSuffixTableRange();
    }
}
