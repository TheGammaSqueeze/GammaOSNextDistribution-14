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

package com.android.timezone.location.storage.tzs2range.write;

import com.android.storage.block.write.BlockFileWriter;
import com.android.storage.block.write.BlockWriter;
import com.android.storage.block.write.EmptyBlockWriter;
import com.android.storage.s2.S2Support;
import com.android.timezone.location.storage.tzs2range.SuffixTableRange;
import com.android.timezone.location.storage.tzs2range.SuffixTableSharedData;
import com.android.timezone.location.storage.tzs2range.TzS2Range;
import com.android.timezone.location.storage.tzs2range.TzS2RangeFileFormat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/** Writes a TZ S2 data file. */
public final class TzS2RangeFileWriter implements AutoCloseable {

    private final HeaderBlockWriter mHeaderBlockWriter;

    private final List<BlockWriter> mSuffixTableBlockWriters = new ArrayList<>();

    private final BlockFileWriter mBlockFileWriter;

    private final BankedTzIdSetsPacker mBankedTzIdSetsPacker;

    private final TzS2RangeFileFormat mFileFormat;

    private TzS2RangeFileWriter(TzS2RangeFileFormat fileFormat, BlockFileWriter blockFileWriter)
            throws IOException {
        mBlockFileWriter = blockFileWriter;
        mFileFormat = fileFormat;

        mHeaderBlockWriter = HeaderBlockWriter.create(fileFormat);
        mBankedTzIdSetsPacker = mHeaderBlockWriter.getBankedTzIdSetsPacker();
    }

    /** Opens a file for writing with the specified format. */
    public static TzS2RangeFileWriter open(File outFile, TzS2RangeFileFormat fileFormat)
            throws IOException {
        BlockFileWriter writer = BlockFileWriter.open(
                TzS2RangeFileFormat.MAGIC, TzS2RangeFileFormat.VERSION, outFile);
        return new TzS2RangeFileWriter(fileFormat, writer);
    }

    /**
     * Process the set of ranges to store in the file, splitting ranges as needed to fit them into
     * suffix tables. The ranges must be of the expected S2 level and ordered by cell ID.
     */
    public void processRanges(Iterator<TzS2Range> ranges) throws IOException {
        PushBackIterator<TzS2Range> pushBackIterator = new PushBackIterator<>(ranges);

        // For each prefix value, collect all the ranges that match.
        for (int currentPrefix = 0;
                currentPrefix <= mFileFormat.getMaxPrefixValue();
                currentPrefix++) {

            // Step 1:
            // populate samePrefixRanges, which holds ranges that have a prefix of currentPrefix.
            List<TzS2Range> samePrefixRanges =
                    collectSamePrefixRanges(pushBackIterator, currentPrefix);

            // Step 2: Write samePrefixRanges to a suffix table.
            BlockWriter blockWriter = writeSamePrefixRanges(currentPrefix, samePrefixRanges);
            mSuffixTableBlockWriters.add(blockWriter);
        }

        // At this point there should be no data left.
        if (pushBackIterator.hasNext()) {
            throw new IllegalStateException("Unexpected ranges left at the end.");
        }
    }

    private List<TzS2Range> collectSamePrefixRanges(
            PushBackIterator<TzS2Range> pushBackIterator, int currentPrefix) {

        List<TzS2Range> samePrefixRanges = new ArrayList<>();

        while (pushBackIterator.hasNext()) {
            TzS2Range currentRange = pushBackIterator.next();

            long startCellId = currentRange.getStartCellId();
            if (mFileFormat.getS2Level() != S2Support.getS2Level(startCellId)) {
                throw new IllegalArgumentException(
                        "Input data level does not match file format level");
            }
            int startCellPrefix = mFileFormat.extractPrefixValueFromCellId(startCellId);
            if (startCellPrefix != currentPrefix) {
                if (startCellPrefix < currentPrefix) {
                    throw new IllegalStateException("Prefix out of order:"
                            + " currentPrefixValue=" + currentPrefix
                            + " startCellPrefixValue=" + startCellPrefix);
                }
                // The next range is for a later prefix. Put it back and move to step 2.
                pushBackIterator.pushBack(currentRange);
                break;
            }

            long endCellId = currentRange.getEndCellId();
            if (mFileFormat.getS2Level() != S2Support.getS2Level(endCellId)) {
                throw new IllegalArgumentException("endCellId in range " + currentRange
                        + " has the wrong S2 level");
            }

            // Split ranges if they span a prefix.
            int endCellPrefixValue = mFileFormat.extractPrefixValueFromCellId(endCellId);
            if (startCellPrefix != endCellPrefixValue) {
                // Create a range for the current prefix.
                {
                    long newEndCellId = mFileFormat.createCellId(startCellPrefix + 1, 0);
                    TzS2Range tzS2Range =
                            new TzS2Range(startCellId, newEndCellId, currentRange.getTzIdSet());
                    samePrefixRanges.add(tzS2Range);
                }

                Deque<TzS2Range> otherRanges = new ArrayDeque<>();
                // Intermediate prefixes.
                startCellPrefix = startCellPrefix + 1;
                while (startCellPrefix != endCellPrefixValue) {
                    long newStartCellId = mFileFormat.createCellId(startCellPrefix, 0);
                    long newEndCellId = mFileFormat.createCellId(startCellPrefix + 1, 0);
                    TzS2Range tzS2Range = new TzS2Range(
                            newStartCellId, newEndCellId, currentRange.getTzIdSet());
                    otherRanges.add(tzS2Range);
                    startCellPrefix++;
                }

                // Final prefix.
                {
                    long newStartCellId = mFileFormat.createCellId(endCellPrefixValue, 0);
                    if (newStartCellId != endCellId) {
                        TzS2Range tzS2Range = new TzS2Range(
                                newStartCellId, endCellId, currentRange.getTzIdSet());
                        otherRanges.add(tzS2Range);
                    }
                }

                // Push back the ranges in reverse order so they come back out in sorted order.
                while (!otherRanges.isEmpty()) {
                    pushBackIterator.pushBack(otherRanges.removeLast());
                }
                break;
            } else {
                samePrefixRanges.add(currentRange);
            }
        }
        return samePrefixRanges;
    }

    private BlockWriter writeSamePrefixRanges(int currentPrefix, List<TzS2Range> samePrefixRanges)
            throws IOException {
        BlockWriter blockWriter;
        if (samePrefixRanges.size() == 0) {
            // Add an empty block.
            blockWriter = SuffixTableWriter.createEmptyBlockWriter();
        } else {
            // Create a suffix table block.

            // Handle the TZ IDs sets to store.
            List<List<String>> uniqueIdSets =
                    BankedTzIdSetsPacker.extractUniqueTzIdSets(samePrefixRanges);
            BankedTzIdSetsPacker.BankHelper bankHelper =
                    mBankedTzIdSetsPacker.addTzIdSets(uniqueIdSets);

            SuffixTableSharedData sharedData =
                    new SuffixTableSharedData(currentPrefix, bankHelper.getId());
            SuffixTableWriter suffixTableWriter =
                    SuffixTableWriter.createPopulated(mFileFormat, sharedData);
            TzS2Range lastRange = null;
            for (TzS2Range currentRange : samePrefixRanges) {
                // Validate ranges don't overlap.
                if (lastRange != null) {
                    if (lastRange.overlaps(currentRange)) {
                        throw new IllegalStateException("lastRange=" + lastRange + " overlaps"
                                + " currentRange=" + currentRange);
                    }
                }
                lastRange = currentRange;
                int tzIdSetId = bankHelper.getTzIdSetId(currentRange.getTzIdSet());

                // Split the range so it fits.
                final int maxRangeLength = mFileFormat.getTableEntryMaxRangeLengthValue();
                long startCellId = currentRange.getStartCellId();
                long endCellId = currentRange.getEndCellId();
                int rangeLength = mFileFormat.calculateRangeLength(startCellId, endCellId);
                while (rangeLength > maxRangeLength) {
                    long newEndCellId = S2Support.offsetCellId(startCellId, maxRangeLength);
                    SuffixTableRange suffixTableRange =
                            new SuffixTableRange(startCellId, newEndCellId, tzIdSetId);
                    suffixTableWriter.addRange(suffixTableRange);
                    startCellId = newEndCellId;
                    rangeLength = mFileFormat.calculateRangeLength(startCellId, endCellId);
                }
                SuffixTableRange suffixTableRange =
                        new SuffixTableRange(startCellId, endCellId, tzIdSetId);
                suffixTableWriter.addRange(suffixTableRange);
            }
            blockWriter = suffixTableWriter;
        }
        return blockWriter;
    }

    @Override
    public void close() throws IOException {
        try {
            BlockWriter.ReadBack headerReadBack = mHeaderBlockWriter.close();
            mBlockFileWriter.addBlock(headerReadBack.getType(), headerReadBack.getExtraBytes(),
                    headerReadBack.getBlockData());

            // Add empty blocks padding.
            EmptyBlockWriter emptyBlockWriterHelper =
                    new EmptyBlockWriter(TzS2RangeFileFormat.BLOCK_TYPE_PADDING);
            BlockWriter.ReadBack emptyBlockReadBack = emptyBlockWriterHelper.close();
            for (int i = 0; i < mFileFormat.getSuffixTableBlockIdOffset() - 1; i++) {
                mBlockFileWriter.addBlock(
                        emptyBlockReadBack.getType(), emptyBlockReadBack.getExtraBytes(),
                        emptyBlockReadBack.getBlockData());
            }

            // Add the suffix tables.
            for (BlockWriter blockWriter : mSuffixTableBlockWriters) {
                BlockWriter.ReadBack readBack = blockWriter.close();

                mBlockFileWriter.addBlock(readBack.getType(), readBack.getExtraBytes(),
                        readBack.getBlockData());
            }
        } finally {
            mBlockFileWriter.close();
        }
    }

    /** Returns the{@link TzS2RangeFileFormat} for the file being written. */
    public TzS2RangeFileFormat getFileFormat() {
        return mFileFormat;
    }
}
