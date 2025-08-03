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

import static com.android.storage.s2.S2Support.cellIdToString;

import com.android.storage.block.read.BlockData;
import com.android.storage.block.write.BlockWriter;
import com.android.storage.block.write.EmptyBlockWriter;
import com.android.storage.io.write.TypedOutputStream;
import com.android.storage.s2.S2LevelRange;
import com.android.storage.s2.S2Support;
import com.android.storage.table.packed.write.PackedTableWriter;
import com.android.telephony.sats2range.read.SatS2RangeFileFormat;
import com.android.telephony.sats2range.read.SuffixTableExtraInfo;
import com.android.telephony.sats2range.read.SuffixTableSharedData;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

/**
 * A class used to generate suffix tables block info and block data.
 * To write empty tables use {@link #createEmptyBlockWriter()}.
 * To write populated tables use {@link
 * #createPopulated(SatS2RangeFileFormat, SuffixTableSharedData)} and add entries with
 * {@link #addRange(S2LevelRange)}
 */
public final class SuffixTableWriter implements BlockWriter {

    private final SuffixTableSharedData mSharedData;

    private final SatS2RangeFileFormat mFileFormat;

    private final PackedTableWriter mPackedTableWriter;

    private final File mFile;

    private S2LevelRange mLastRangeAdded;

    private SuffixTableWriter(SatS2RangeFileFormat fileFormat, SuffixTableSharedData sharedData)
            throws IOException {
        mFileFormat = fileFormat;
        mSharedData = sharedData;

        int keySizeBits = fileFormat.getSuffixBitCount();
        int entrySizeByteCount = fileFormat.getTableEntryByteCount();
        mFile = File.createTempFile("suffixtablewriter", ".packed");

        byte[] blockSharedData = SuffixTableSharedDataWriter.toBytes(sharedData);
        FileOutputStream fileOutputStream = new FileOutputStream(mFile);
        boolean signedValue = false;
        mPackedTableWriter = PackedTableWriter.create(
                fileOutputStream, entrySizeByteCount, keySizeBits, signedValue, blockSharedData);
    }

    /** Returns a {@link BlockWriter} capable of generating the block data for an empty table. */
    public static BlockWriter createEmptyBlockWriter() {
        return new EmptyBlockWriter(SatS2RangeFileFormat.BLOCK_TYPE_SUFFIX_TABLE);
    }

    /** Returns a {@link BlockWriter} capable of generating the block data for a populated table. */
    public static SuffixTableWriter createPopulated(
            SatS2RangeFileFormat fileFormat, SuffixTableSharedData sharedData) throws IOException {
        return new SuffixTableWriter(fileFormat, sharedData);
    }

    /**
     * Adds the supplied range to the table. The range must start after any previously added range,
     * no overlap is allowed. Gaps are permitted. The range must have the expected S2 cell ID
     * prefix. Invalid ranges will cause {@link IllegalArgumentException}. This method must be
     * called at least once. See {@link SuffixTableWriter#createEmptyBlockWriter()} for empty
     * tables.
     */
    public void addRange(S2LevelRange suffixTableRange) throws IOException {
        checkIsOpen();

        long rangeStartCellId = suffixTableRange.getStartCellId();
        long rangeEndCellId = suffixTableRange.getEndCellId();

        // Check range belongs in this table.
        int rangeStartPrefixValue = mFileFormat.extractPrefixValueFromCellId(rangeStartCellId);
        int rangeStartSuffixValue = mFileFormat.extractSuffixValueFromCellId(rangeStartCellId);
        if (rangeStartPrefixValue != mSharedData.getTablePrefix()) {
            throw new IllegalArgumentException(
                    "rangeStartCellId=" + cellIdToString(rangeStartCellId)
                            + " has a different prefix=" + rangeStartPrefixValue
                            + " than the table prefix=" + mSharedData.getTablePrefix());
        }

        long rangeEndCellIdInclusive = S2Support.offsetCellId(rangeEndCellId, -1);
        int rangeEndPrefixValue = mFileFormat.extractPrefixValueFromCellId(rangeEndCellIdInclusive);
        if (rangeEndPrefixValue != rangeStartPrefixValue) {
            // Because SuffixTableRange has an exclusive end value, rangeEndPrefixValue is allowed
            // to be the next prefix value if the rangeEndSuffixValue == 0.
            int rangeEndSuffixValue = mFileFormat.extractSuffixValueFromCellId(rangeEndCellId);
            if (!(rangeEndPrefixValue == rangeStartPrefixValue + 1 && rangeEndSuffixValue == 0)) {
                throw new IllegalArgumentException("rangeEndPrefixValue=" + rangeEndPrefixValue
                        + " != rangeStartPrefixValue=" + rangeStartPrefixValue);
            }
        }

        // Confirm the new range starts after the end of the last one that was added, if any.
        if (mLastRangeAdded != null) {
            long lastRangeAddedEndCellId = mLastRangeAdded.getEndCellId();
            int lastRangeEndPrefixValue =
                    mFileFormat.extractPrefixValueFromCellId(lastRangeAddedEndCellId);
            if (lastRangeEndPrefixValue != mSharedData.getTablePrefix()) {
                // Deal with the special case where the last range added completed the table.
                throw new IllegalArgumentException(
                        "Suffix table is full: last range added=" + mLastRangeAdded);
            } else {
                int lastRangeEndSuffixValue =
                        mFileFormat.extractSuffixValueFromCellId(lastRangeAddedEndCellId);
                if (rangeStartSuffixValue < lastRangeEndSuffixValue) {
                    throw new IllegalArgumentException("suffixTableRange=" + suffixTableRange
                            + " overlaps with last range added=" + mLastRangeAdded);
                }
            }
        }

        int rangeLength = mFileFormat.calculateRangeLength(rangeStartCellId, rangeEndCellId);

        long value = mFileFormat.createSuffixTableValue(rangeLength);
        mPackedTableWriter.addEntry(rangeStartSuffixValue, value);
        mLastRangeAdded = suffixTableRange;
    }

    @Override
    public ReadBack close() throws IOException {
        checkIsOpen();
        mPackedTableWriter.close();
        mLastRangeAdded = null;

        int entryCount = mPackedTableWriter.getEntryCount();
        if (entryCount == 0) {
            throw new IllegalStateException("No ranges added. For an empty suffix table, use"
                    + " createEmptySuffixTableBlockWriter()");
        }

        FileChannel fileChannel = FileChannel.open(mFile.toPath(), StandardOpenOption.READ);
        MappedByteBuffer map = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, mFile.length());
        fileChannel.close();

        // Writes the number of entries into the extra bytes stored in the BlockInfo. This means the
        // number of entries can be known without reading the block data at all.
        SuffixTableExtraInfo suffixTableExtraInfo =
                new SuffixTableExtraInfo(mSharedData.getTablePrefix(), entryCount);
        byte[] blockInfoExtraBytes = generateBlockInfoExtraBytes(suffixTableExtraInfo);
        BlockData blockData = new BlockData(map);
        return new ReadBack() {
            @Override
            public byte[] getExtraBytes() {
                return blockInfoExtraBytes;
            }

            @Override
            public int getType() {
                return SatS2RangeFileFormat.BLOCK_TYPE_SUFFIX_TABLE;
            }

            @Override
            public BlockData getBlockData() {
                return blockData;
            }
        };
    }

    private void checkIsOpen() {
        if (!mPackedTableWriter.isOpen()) {
            throw new IllegalStateException("Writer is closed.");
        }
    }

    private static byte[] generateBlockInfoExtraBytes(SuffixTableExtraInfo suffixTableBlockInfo) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (TypedOutputStream tos = new TypedOutputStream(baos)) {
            tos.writeInt(suffixTableBlockInfo.getEntryCount());
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected IOException writing to byte array", e);
        }
        return baos.toByteArray();
    }
}
