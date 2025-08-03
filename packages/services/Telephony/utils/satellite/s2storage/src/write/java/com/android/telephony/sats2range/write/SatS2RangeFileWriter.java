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

package com.android.telephony.sats2range.write;

import com.android.storage.block.write.BlockFileWriter;
import com.android.storage.block.write.BlockWriter;
import com.android.storage.block.write.EmptyBlockWriter;
import com.android.storage.s2.S2LevelRange;
import com.android.storage.s2.S2Support;
import com.android.telephony.sats2range.read.SatS2RangeFileFormat;
import com.android.telephony.sats2range.read.SuffixTableSharedData;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/** Writes a satellite S2 data file. */
public final class SatS2RangeFileWriter implements AutoCloseable {

    private final HeaderBlockWriter mHeaderBlockWriter;

    private final List<BlockWriter> mSuffixTableBlockWriters = new ArrayList<>();

    private final BlockFileWriter mBlockFileWriter;

    private final SatS2RangeFileFormat mFileFormat;

    private SatS2RangeFileWriter(SatS2RangeFileFormat fileFormat, BlockFileWriter blockFileWriter)
            throws IOException {
        mBlockFileWriter = blockFileWriter;
        mFileFormat = fileFormat;

        mHeaderBlockWriter = HeaderBlockWriter.create(fileFormat);
    }

    /** Opens a file for writing with the specified format. */
    public static SatS2RangeFileWriter open(File outFile, SatS2RangeFileFormat fileFormat)
            throws IOException {
        BlockFileWriter writer = BlockFileWriter.open(
                SatS2RangeFileFormat.MAGIC, SatS2RangeFileFormat.VERSION, outFile);
        return new SatS2RangeFileWriter(fileFormat, writer);
    }

    /**
     * Group the sorted ranges into contiguous suffix blocks. Big ranges might get split as
     * needed to fit them into suffix blocks. The ranges must be of the expected S2 level
     * and ordered by cell ID.
     */
    public void createSortedSuffixBlocks(Iterator<S2LevelRange> ranges) throws IOException {
        PushBackIterator<S2LevelRange> pushBackIterator = new PushBackIterator<>(ranges);

        // For each prefix value, collect all the ranges that match.
        for (int currentPrefix = 0;
                currentPrefix <= mFileFormat.getMaxPrefixValue();
                currentPrefix++) {

            // Step 1:
            // populate samePrefixRanges, which holds ranges that have a prefix of currentPrefix.
            List<S2LevelRange> samePrefixRanges =
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

    private List<S2LevelRange> collectSamePrefixRanges(
            PushBackIterator<S2LevelRange> pushBackIterator, int currentPrefix) {
        List<S2LevelRange> samePrefixRanges = new ArrayList<>();
        while (pushBackIterator.hasNext()) {
            S2LevelRange currentRange = pushBackIterator.next();

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
                    S2LevelRange satS2Range = new S2LevelRange(startCellId, newEndCellId);
                    samePrefixRanges.add(satS2Range);
                }

                Deque<S2LevelRange> otherRanges = new ArrayDeque<>();
                // Intermediate prefixes.
                startCellPrefix = startCellPrefix + 1;
                while (startCellPrefix != endCellPrefixValue) {
                    long newStartCellId = mFileFormat.createCellId(startCellPrefix, 0);
                    long newEndCellId = mFileFormat.createCellId(startCellPrefix + 1, 0);
                    S2LevelRange satS2Range = new S2LevelRange(newStartCellId, newEndCellId);
                    otherRanges.add(satS2Range);
                    startCellPrefix++;
                }

                // Final prefix.
                {
                    long newStartCellId = mFileFormat.createCellId(endCellPrefixValue, 0);
                    if (newStartCellId != endCellId) {
                        S2LevelRange satS2Range = new S2LevelRange(newStartCellId, endCellId);
                        otherRanges.add(satS2Range);
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

    private BlockWriter writeSamePrefixRanges(
            int currentPrefix, List<S2LevelRange> samePrefixRanges) throws IOException {
        BlockWriter blockWriter;
        if (samePrefixRanges.size() == 0) {
            // Add an empty block.
            blockWriter = SuffixTableWriter.createEmptyBlockWriter();
        } else {
            // Create a suffix table block.
            SuffixTableSharedData sharedData = new SuffixTableSharedData(currentPrefix);
            SuffixTableWriter suffixTableWriter =
                    SuffixTableWriter.createPopulated(mFileFormat, sharedData);
            S2LevelRange lastRange = null;
            for (S2LevelRange currentRange : samePrefixRanges) {
                // Validate ranges don't overlap.
                if (lastRange != null) {
                    if (lastRange.overlaps(currentRange)) {
                        throw new IllegalStateException("lastRange=" + lastRange + " overlaps"
                                + " currentRange=" + currentRange);
                    }
                }
                lastRange = currentRange;

                // Split the range so it fits.
                final int maxRangeLength = mFileFormat.getTableEntryMaxRangeLengthValue();
                long startCellId = currentRange.getStartCellId();
                long endCellId = currentRange.getEndCellId();
                int rangeLength = mFileFormat.calculateRangeLength(startCellId, endCellId);
                while (rangeLength > maxRangeLength) {
                    long newEndCellId = S2Support.offsetCellId(startCellId, maxRangeLength);
                    S2LevelRange suffixTableRange = new S2LevelRange(startCellId, newEndCellId);
                    suffixTableWriter.addRange(suffixTableRange);
                    startCellId = newEndCellId;
                    rangeLength = mFileFormat.calculateRangeLength(startCellId, endCellId);
                }
                S2LevelRange suffixTableRange = new S2LevelRange(startCellId, endCellId);
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
                    new EmptyBlockWriter(SatS2RangeFileFormat.BLOCK_TYPE_PADDING);
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

    /** Returns the{@link SatS2RangeFileFormat} for the file being written. */
    public SatS2RangeFileFormat getFileFormat() {
        return mFileFormat;
    }
}
