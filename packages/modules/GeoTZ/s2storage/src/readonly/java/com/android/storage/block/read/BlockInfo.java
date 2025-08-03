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

package com.android.storage.block.read;

import com.android.storage.util.Visitor;

import java.util.Arrays;
import java.util.Objects;

/**
 * Information about a block from a block file's header. There is one {@link BlockInfo} for every
 * (logical) block in the file, though the associated block's data can be zero length, in which case
 * the {@link #getBlockStartByteOffset()} will be set but the block itself will occupy no bytes in
 * the file.
 */
public final class BlockInfo {

    private final int mId;

    private final int mType;

    private final long mBlockStartByteOffset;

    private final long mBlockSizeBytes;

    private final byte[] mExtraBytes;

    /**
     * Creates an instance using the supplied information. {@code extraBytes} can be {@code null}.
     */
    public BlockInfo(int id, int type, long blockStartByteOffset, long blockSizeBytes,
            byte[] extraBytes) {
        mId = id;
        mType = type;
        mBlockStartByteOffset = blockStartByteOffset;
        mBlockSizeBytes = blockSizeBytes;
        mExtraBytes = extraBytes;
    }

    /** Returns the block's ID. */
    public int getId() {
        return mId;
    }

    /** Returns the block's type ID. */
    public int getType() {
        return mType;
    }

    /** Returns the "extra" (domain specific) bytes. */
    public byte[] getExtraBytes() {
        return mExtraBytes;
    }

    /** Returns the location of the block in the file. */
    public long getBlockStartByteOffset() {
        return mBlockStartByteOffset;
    }

    /**
     * The block's size in the file. It may be zero to indicate an empty block. When &gt;= 0 it
     * includes the length of the block's header.
     */
    public long getBlockSizeBytes() {
        return mBlockSizeBytes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BlockInfo blockInfo = (BlockInfo) o;
        return mId == blockInfo.mId
                && mType == blockInfo.mType
                && mBlockStartByteOffset == blockInfo.mBlockStartByteOffset
                && mBlockSizeBytes == blockInfo.mBlockSizeBytes
                && Arrays.equals(mExtraBytes, blockInfo.mExtraBytes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(mId, mType, mBlockStartByteOffset, mBlockSizeBytes);
        result = 31 * result + Arrays.hashCode(mExtraBytes);
        return result;
    }

    @Override
    public String toString() {
        return "BlockInfo{"
                + "mId=" + mId + "(" + Integer.toHexString(mId) + ")"
                + ", mType=" + mType
                + ", mBlockStartByteOffset=" + mBlockStartByteOffset
                + ", mBlockSizeBytes=" + mBlockSizeBytes
                + ", mExtraBytes=" + Arrays.toString(mExtraBytes)
                + '}';
    }

    /** A {@link Visitor} for the {@link BlockInfo}. See {@link #visit} */
    public interface BlockInfoVisitor extends Visitor {

        /** Called after {@link #begin()} with the BlockInfo. */
        void visit(BlockInfo blockInfo) throws VisitException;
    }

    /**
     * Issues callbacks to the supplied {@link BlockInfoVisitor} containing information from
     * the block info.
     */
    public void visit(BlockInfoVisitor blockInfoVisitor) throws Visitor.VisitException {
        try {
            blockInfoVisitor.begin();
            blockInfoVisitor.visit(this);
        } finally {
            blockInfoVisitor.end();
        }
    }
}
