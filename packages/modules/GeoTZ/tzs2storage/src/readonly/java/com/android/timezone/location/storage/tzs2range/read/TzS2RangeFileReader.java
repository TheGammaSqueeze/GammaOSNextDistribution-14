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

package com.android.timezone.location.storage.tzs2range.read;

import com.android.storage.block.read.Block;
import com.android.storage.block.read.BlockFileReader;
import com.android.storage.block.read.BlockInfo;
import com.android.storage.s2.S2Support;
import com.android.storage.util.Conditions;
import com.android.storage.util.Visitor;
import com.android.timezone.location.storage.tzs2range.BankedTzIdSets;
import com.android.timezone.location.storage.tzs2range.SuffixTableRange;
import com.android.timezone.location.storage.tzs2range.TzS2Range;
import com.android.timezone.location.storage.tzs2range.TzS2RangeFileFormat;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Provides access to the content of a TZ S2 data file. */
public final class TzS2RangeFileReader implements AutoCloseable {

    private final BlockFileReader mBlockFileReader;

    private HeaderBlock mHeaderBlock;

    private SuffixTableExtraInfo[] mSuffixTableExtraInfos;

    /** Convenience field to avoid calling {@link HeaderBlock#getFileFormat()} repeatedly. */
    private TzS2RangeFileFormat mFileFormat;

    private boolean mClosed;

    private TzS2RangeFileReader(BlockFileReader blockFileReader) {
        mBlockFileReader = Objects.requireNonNull(blockFileReader);
    }

    /**
     * Opens the specified file. Throws {@link IOException} in the event of a access problem reading
     * the file. Throws {@link IllegalArgumentException} if the file has a format / syntax problem.
     *
     * <p>After open, use methods like {@link #findEntryByCellId(long)} to access the data.
     */
    public static TzS2RangeFileReader open(File file) throws IOException {
        boolean memoryMapBlocks = false;
        BlockFileReader blockFileReader = BlockFileReader.open(
                memoryMapBlocks, file, TzS2RangeFileFormat.MAGIC, TzS2RangeFileFormat.VERSION);
        TzS2RangeFileReader tzS2RangeFileReader = new TzS2RangeFileReader(blockFileReader);
        tzS2RangeFileReader.initialize();
        return tzS2RangeFileReader;
    }

    private void initialize() throws IOException {
        // Check the BlockInfo for the header block is what we expect.
        int headerBlockId = 0;
        BlockInfo firstBlockInfo = mBlockFileReader.getBlockInfo(headerBlockId);
        if (firstBlockInfo.getType() != TzS2RangeFileFormat.BLOCK_TYPE_HEADER) {
            throw new IllegalArgumentException("headerBlockInfo.getType()="
                    + firstBlockInfo.getType() + " must be "
                    + TzS2RangeFileFormat.BLOCK_TYPE_HEADER);
        }

        // So far so good. Open the header block itself and extract the information held there.
        Block firstBlock = mBlockFileReader.getBlock(headerBlockId);
        if (firstBlock.getType() != TzS2RangeFileFormat.BLOCK_TYPE_HEADER) {
            throw new IllegalArgumentException("firstBlock.getType()=" + firstBlock.getType()
                    + " must be " + TzS2RangeFileFormat.BLOCK_TYPE_HEADER);
        }
        mHeaderBlock = HeaderBlock.wrap(firstBlock.getData());

        // Optimization: hold a direct reference to fileFormat since it is referenced often.
        mFileFormat = mHeaderBlock.getFileFormat();

        // Read all the BlockInfos for data blocks and precache the SuffixTableBlock.Info instances.
        mSuffixTableExtraInfos = new SuffixTableExtraInfo[mFileFormat.getMaxPrefixValue() + 1];
        for (int prefix = 0; prefix < mSuffixTableExtraInfos.length; prefix++) {
            int blockId = prefix + mFileFormat.getSuffixTableBlockIdOffset();
            BlockInfo blockInfo = mBlockFileReader.getBlockInfo(blockId);
            int type = blockInfo.getType();
            if (type == TzS2RangeFileFormat.BLOCK_TYPE_SUFFIX_TABLE) {
                mSuffixTableExtraInfos[prefix] =
                        SuffixTableExtraInfo.create(mFileFormat, blockInfo);
            } else {
                throw new IllegalStateException("Unknown block type=" + type);
            }
        }
    }

    /** A {@link Visitor} for the {@link TzS2RangeFileReader}. See {@link #visit} */
    public interface TzS2RangeFileVisitor extends Visitor {

        /** Called after {@link #begin()}, once only. */
        void visitHeaderBlock(HeaderBlock headerBlock) throws Visitor.VisitException;

        /**
         * Called after {@link #visitHeaderBlock(HeaderBlock)}}, once for each suffix table in the
         * file.
         */
        void visitSuffixTableExtraInfo(SuffixTableExtraInfo suffixTableExtraInfo)
                throws Visitor.VisitException;

        /**
         * Called after {@link #visitHeaderBlock(HeaderBlock)}, once per suffix table in the file.
         */
        void visitSuffixTableBlock(SuffixTableBlock suffixTableBlock)
                throws Visitor.VisitException;
    }

    /**
     * Issues callbacks to the supplied {@link TzS2RangeFileVisitor} containing information from the
     * TZ S2 data file.
     */
    public void visit(TzS2RangeFileVisitor visitor) throws Visitor.VisitException {
        try {
            visitor.begin();

            visitor.visitHeaderBlock(mHeaderBlock);

            for (int i = 0; i < mSuffixTableExtraInfos.length; i++) {
                visitor.visitSuffixTableExtraInfo(mSuffixTableExtraInfos[i]);
            }

            try {
                for (int i = 0; i < mSuffixTableExtraInfos.length; i++) {
                    SuffixTableBlock suffixTableBlock = getSuffixTableBlockForPrefix(i);
                    visitor.visitSuffixTableBlock(suffixTableBlock);
                }
            } catch (IOException e) {
                throw new Visitor.VisitException(e);
            }
        } finally {
            visitor.end();
        }
    }

    /**
     * Finds an {@link Entry} associated with a range covering {@code cellId}.
     * Returns {@code null} if no range exists. Throws {@link IllegalArgumentException} if
     * {@code cellId} is not the correct S2 level for the file. See {@link #getS2Level()}.
     */
    public Entry findEntryByCellId(long cellId) throws IOException {
        checkNotClosed();
        int dataS2Level = mFileFormat.getS2Level();
        int searchS2Level = S2Support.getS2Level(cellId);
        if (dataS2Level != searchS2Level) {
            throw new IllegalArgumentException(
                    "data S2 level=" + dataS2Level + ", search S2 level=" + searchS2Level);
        }

        int prefix = mFileFormat.extractPrefixValueFromCellId(cellId);
        SuffixTableBlock suffixTableBlock = getSuffixTableBlockForPrefix(prefix);
        SuffixTableBlock.Entry suffixTableEntry = suffixTableBlock.findEntryByCellId(cellId);
        if (suffixTableEntry == null) {
            return null;
        }
        return new Entry(suffixTableEntry);
    }

    private SuffixTableExtraInfo getSuffixTableExtraInfoForPrefix(int prefixValue) {
        Conditions.checkArgInRange(
                "prefixValue", prefixValue, "minPrefixValue", 0, "maxPrefixValue",
                mFileFormat.getMaxPrefixValue());

        return mSuffixTableExtraInfos[prefixValue];
    }

    private SuffixTableBlock getSuffixTableBlockForPrefix(int prefix) throws IOException {
        SuffixTableExtraInfo suffixTableExtraInfo = getSuffixTableExtraInfoForPrefix(prefix);
        if (suffixTableExtraInfo.isEmpty()) {
            return SuffixTableBlock.createEmpty(mFileFormat, prefix);
        }
        Block block = mBlockFileReader.getBlock(prefix + mFileFormat.getSuffixTableBlockIdOffset());
        SuffixTableBlock suffixTableBlock =
                SuffixTableBlock.createPopulated(mFileFormat, block.getData());
        if (prefix != suffixTableBlock.getPrefix()) {
            throw new IllegalArgumentException("prefixValue=" + prefix
                    + " != suffixTableBlock.getPrefix()=" + suffixTableBlock.getPrefix());
        }
        return suffixTableBlock;
    }

    @Override
    public void close() throws IOException {
        mClosed = true;
        mHeaderBlock = null;
        mBlockFileReader.close();
    }

    private void checkNotClosed() throws IOException {
        if (mClosed) {
            throw new IOException("Closed");
        }
    }

    /** Returns the S2 level for the file. See also {@link #findEntryByCellId(long)}. */
    public int getS2Level() throws IOException {
        checkNotClosed();
        return mHeaderBlock.getFileFormat().getS2Level();
    }

    /** An entry for a {@link TzS2Range} from a TZ S2 data file. */
    public final class Entry {

        private final SuffixTableBlock.Entry mSuffixTableEntry;

        private TzS2Range mTzS2Range;

        Entry(SuffixTableBlock.Entry suffixTableEntry) {
            mSuffixTableEntry = suffixTableEntry;
        }

        /** Returns the {@link TzS2Range} associated with this entry. */
        public TzS2Range getTzS2Range() {
            if (mTzS2Range == null) {
                SuffixTableRange suffixTableRange = mSuffixTableEntry.getSuffixTableRange();
                long rangeStartCellId = suffixTableRange.getStartCellId();
                long rangeEndCellId = suffixTableRange.getEndCellId();
                int tzIdSetId = suffixTableRange.getTzIdSetId();
                BankedTzIdSets.Bank tzIdBank =
                        mHeaderBlock.getBankedTzIdSets()
                                .getBank(mSuffixTableEntry.getTzIdSetBank());
                List<String> tzIds = tzIdBank.getTzIdSet(tzIdSetId).getTzIds();
                mTzS2Range = new TzS2Range(rangeStartCellId, rangeEndCellId, tzIds);
            }
            return mTzS2Range;
        }
    }
}
