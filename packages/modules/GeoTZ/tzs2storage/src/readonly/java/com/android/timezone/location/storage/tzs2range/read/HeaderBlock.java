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

import com.android.storage.block.read.BlockData;
import com.android.storage.util.Visitor;
import com.android.timezone.location.storage.tzs2range.BankedTzIdSets;
import com.android.timezone.location.storage.tzs2range.TzS2RangeFileFormat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a {@link BlockData}, interpreting it as a TZ S2 data file header (block 0). This class
 * provides typed access to the information held in the header for use when reading a TZ S2 data
 * file.
 */
public final class HeaderBlock {

    private final TzS2RangeFileFormat mFileFormat;

    private final BankedTzIdSets mBankedTzIdSets;

    private HeaderBlock(BlockData blockData) {
        int offset = 0;

        // Read the format information.
        int dataS2Level = blockData.getUnsignedByte(offset++);
        int prefixBitCount = blockData.getUnsignedByte(offset++);
        int suffixBitCount = blockData.getUnsignedByte(offset++);
        int suffixRecordBitCount = blockData.getUnsignedByte(offset++);
        int endCellIdOffsetBitCount = blockData.getUnsignedByte(offset++);
        int suffixTableBlockIdOffset = blockData.getUnsignedByte(offset++);
        mFileFormat = new TzS2RangeFileFormat(
                dataS2Level, prefixBitCount, suffixBitCount, suffixTableBlockIdOffset,
                suffixRecordBitCount,
                endCellIdOffsetBitCount);

        // Read the TZ ID strings.
        char stringCount = blockData.getChar(offset);
        offset += Character.BYTES;

        // Read the banked TZ ID sets.
        List<String> strings = new ArrayList<>(stringCount);
        for (int i = 0; i < stringCount; i++) {
            byte[] bytes = blockData.getTinyByteArray(offset);
            offset += 1 + bytes.length;

            String string = new String(bytes, StandardCharsets.UTF_8);

            // Intern the strings to reduce memory from duplicated strings.
            string = string.intern();
            strings.add(string);
        }
        BankedTzIdSets.Builder bankedTzIdSetsBuilder = new BankedTzIdSets.Builder();
        bankedTzIdSetsBuilder.setStringsByIndex(strings);

        // Read banks
        int bankCount = blockData.getUnsignedByte(offset++);
        for (int bankIndex = 0; bankIndex < bankCount; bankIndex++) {
            BankedTzIdSets.Builder.BankBuilder bankBuilder = bankedTzIdSetsBuilder.newBank();
            // Read sets
            int setCount = blockData.getUnsignedByte(offset++);
            for (int setIndex = 0; setIndex < setCount; setIndex++) {
                char[] stringIdsAsCharArray = blockData.getTinyCharArray(offset);
                offset += 1 + (stringIdsAsCharArray.length * Character.BYTES);

                List<Integer> stringIds = new ArrayList<>();
                for (char stringIdAsChar : stringIdsAsCharArray) {
                    stringIds.add((int) stringIdAsChar);
                }
                bankBuilder.addTzIdSet(stringIds);
            }
        }
        mBankedTzIdSets = bankedTzIdSetsBuilder.build();
    }

    /** Creates a {@link HeaderBlock} from low-level block data from a block file. */
    public static HeaderBlock wrap(BlockData blockData) {
        return new HeaderBlock(blockData);
    }

    /** Returns the {@link TzS2RangeFileFormat} for the file. */
    public TzS2RangeFileFormat getFileFormat() {
        return mFileFormat;
    }

    /**
     * Returns the {@link BankedTzIdSets} to use when interpreting
     * {@link com.android.timezone.location.storage.tzs2range.TzS2Range} objects from the file.
     */
    public BankedTzIdSets getBankedTzIdSets() {
        return mBankedTzIdSets;
    }

    /** A {@link Visitor} for the {@link HeaderBlock}. See {@link #visit} */
    public interface HeaderBlockVisitor extends Visitor {

        /** Called after {@link #begin()}, once. */
        void visitFileFormat(TzS2RangeFileFormat fileFormat);

        /** Called after {@link #visitFileFormat(TzS2RangeFileFormat)}, once. */
        void visitBankedTzIdSets(BankedTzIdSets bankedTzIdSets);
    }

    /**
     * Issues callbacks to the supplied {@link HeaderBlockVisitor} containing information from the
     * header block.
     */
    public void visit(HeaderBlockVisitor visitor) throws Visitor.VisitException {
        try {
            visitor.begin();
            visitor.visitFileFormat(mFileFormat);
            visitor.visitBankedTzIdSets(mBankedTzIdSets);
        } finally {
            visitor.end();
        }
    }
}
