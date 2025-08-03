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

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * A Block from a {@link BlockFileReader}, consisting of an ID, a type and the block's data.
 */
public final class Block {

    private final int mId;

    private final int mType;

    private final BlockData mBlockData;

    /** Creates a Block. The {@link ByteBuffer} must be read-only and is not copied. */
    public Block(int id, int type, ByteBuffer dataBytes) {
        mId = id;
        mType = type;
        mBlockData = new BlockData(Objects.requireNonNull(dataBytes));
    }

    /** Returns the ID for this block. */
    public int getId() {
        return mId;
    }

    /** Returns the type ID for this block. */
    public int getType() {
        return mType;
    }

    /** Returns the data for this block. */
    public BlockData getData() {
        return mBlockData;
    }

    /** A {@link Visitor} for the {@link Block}. See {@link #visit} */
    public interface BlockVisitor extends Visitor {

        /** Called after {@link #begin()} with the Block. */
        void visit(Block block) throws VisitException;
    }

    /**
     * Issues callbacks to the supplied {@link BlockVisitor} containing information from the
     * block.
     */
    public void visit(BlockVisitor visitor) throws Visitor.VisitException {
        try {
            visitor.begin();
            visitor.visit(this);
        } finally {
            visitor.end();
        }
    }
}
