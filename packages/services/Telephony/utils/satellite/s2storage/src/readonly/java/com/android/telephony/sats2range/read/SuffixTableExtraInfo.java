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

import com.android.storage.block.read.BlockInfo;
import com.android.storage.io.read.TypedInputStream;
import com.android.storage.util.Conditions;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Information about a suffix table block held in the header of a satellite S2 data file. It can be
 * used to work out whether to read the associated block data.
 */
public final class SuffixTableExtraInfo {

    /**
     * The suffix table's S2 cell ID prefix. This information is not stored in the block info
     * directly; during file read it is calculated from the block ID, i.e.  {block id} - {suffix
     * table block id offset}.
     */
    private final int mPrefix;

    private final int mEntryCount;

    /** Creates metadata about a suffix table. */
    public SuffixTableExtraInfo(int prefix, int entryCount) {
        if (prefix < 0) {
            throw new IllegalArgumentException("prefix=" + prefix + " must be >= 0");
        }
        mPrefix = prefix;

        if (entryCount < 0) {
            throw new IllegalArgumentException("entryCount=" + entryCount + " must be >= 0");
        }
        mEntryCount = entryCount;
    }

    /**
     * Creates a {@link SuffixTableExtraInfo} from a {@link BlockInfo}. Throws an
     * {@link IllegalArgumentException} if the block info is the wrong type or malformed.
     */
    public static SuffixTableExtraInfo create(
            SatS2RangeFileFormat fileFormat, BlockInfo blockInfo) {
        if (blockInfo.getType() != SatS2RangeFileFormat.BLOCK_TYPE_SUFFIX_TABLE) {
            throw new IllegalArgumentException("blockType=" + blockInfo.getType()
                    + " is not of expected type=" + SatS2RangeFileFormat.BLOCK_TYPE_SUFFIX_TABLE);
        }
        int prefix = blockInfo.getId() - fileFormat.getSuffixTableBlockIdOffset();
        if (blockInfo.getBlockSizeBytes() == 0) {
            // Empty blocks have no data and no extra bytes but we know they have zero elements.
            return new SuffixTableExtraInfo(prefix, 0 /* entryCount */);
        }

        byte[] extraBytes = blockInfo.getExtraBytes();
        if (extraBytes == null || extraBytes.length == 0) {
            throw new IllegalArgumentException(
                    "Extra bytes null or empty in blockInfo=" + blockInfo);
        }

        try (TypedInputStream typedInputStream = new TypedInputStream(
                new ByteArrayInputStream(extraBytes))) {
            int entryCount = typedInputStream.readInt();
            Conditions.checkStateInRange(
                    "entryCount", entryCount, "minSuffixValue", 0, "maxSuffixValue",
                    fileFormat.getMaxSuffixValue());
            return new SuffixTableExtraInfo(prefix, entryCount);
        } catch (IOException e) {
            // This shouldn't happen with a byte[]
            throw new IllegalStateException("Unexpected exception while reading a byte[]", e);
        }
    }

    /** Returns the prefix of the associated suffix table. */
    public int getPrefix() {
        return mPrefix;
    }

    /** Returns the number of entries in the associated suffix table. */
    public int getEntryCount() {
        return mEntryCount;
    }

    /** Returns true if the number of entries in the associated suffix table is zero. */
    public boolean isEmpty() {
        return mEntryCount == 0;
    }
}
