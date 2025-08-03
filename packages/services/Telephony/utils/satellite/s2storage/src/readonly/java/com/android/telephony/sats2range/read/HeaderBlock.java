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

import com.android.storage.block.read.BlockData;
import com.android.storage.util.Visitor;

/**
 * Wraps a {@link BlockData}, interpreting it as a satellite S2 data file header (block 0). This
 * class provides typed access to the information held in the header for use when reading a
 * satellite S2 data file.
 */
public final class HeaderBlock {
    /** Used for converting from bool type to int type */
    public static final int TRUE = 1;
    public static final int FALSE = 0;

    private final SatS2RangeFileFormat mFileFormat;

    private HeaderBlock(BlockData blockData) {
        int offset = 0;

        // Read the format information.
        int dataS2Level = blockData.getUnsignedByte(offset++);
        int prefixBitCount = blockData.getUnsignedByte(offset++);
        int suffixBitCount = blockData.getUnsignedByte(offset++);
        int suffixRecordBitCount = blockData.getUnsignedByte(offset++);
        int suffixTableBlockIdOffset = blockData.getUnsignedByte(offset++);
        boolean isAllowedList = (blockData.getUnsignedByte(offset) == TRUE);
        mFileFormat = new SatS2RangeFileFormat(
                dataS2Level, prefixBitCount, suffixBitCount, suffixTableBlockIdOffset,
                suffixRecordBitCount, isAllowedList);
    }

    /** Creates a {@link HeaderBlock} from low-level block data from a block file. */
    public static HeaderBlock wrap(BlockData blockData) {
        return new HeaderBlock(blockData);
    }

    /** Returns the {@link SatS2RangeFileFormat} for the file. */
    public SatS2RangeFileFormat getFileFormat() {
        return mFileFormat;
    }

    /** A {@link Visitor} for the {@link HeaderBlock}. See {@link #visit} */
    public interface HeaderBlockVisitor extends Visitor {

        /** Called after {@link #begin()}, once. */
        void visitFileFormat(SatS2RangeFileFormat fileFormat);
    }

    /**
     * Issues callbacks to the supplied {@link HeaderBlockVisitor} containing information from the
     * header block.
     */
    public void visit(HeaderBlockVisitor visitor) throws Visitor.VisitException {
        try {
            visitor.begin();
            visitor.visitFileFormat(mFileFormat);
        } finally {
            visitor.end();
        }
    }
}
