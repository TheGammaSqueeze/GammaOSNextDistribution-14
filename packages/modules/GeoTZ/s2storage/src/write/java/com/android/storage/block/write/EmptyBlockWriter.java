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

package com.android.storage.block.write;

import com.android.storage.block.read.BlockData;

import java.nio.ByteBuffer;

/**
 * A {@link BlockWriter} that creates minimal, empty blocks.
 */
public final class EmptyBlockWriter implements BlockWriter {

    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    private static final ByteBuffer EMPTY_BYTE_BUFFER =
            ByteBuffer.wrap(EMPTY_BYTE_ARRAY).asReadOnlyBuffer();

    private static final BlockData EMPTY_BLOCK_DATA = new BlockData(EMPTY_BYTE_BUFFER);

    private final int mBlockType;

    /** Creates an instance for the specified block type. */
    public EmptyBlockWriter(int blockType) {
        mBlockType = blockType;
    }

    @Override
    public ReadBack close() {
        return new ReadBack() {
            @Override
            public byte[] getExtraBytes() {
                return EMPTY_BYTE_ARRAY;
            }

            @Override
            public int getType() {
                return mBlockType;
            }

            @Override
            public BlockData getBlockData() {
                return EMPTY_BLOCK_DATA;
            }
        };
    }
}
