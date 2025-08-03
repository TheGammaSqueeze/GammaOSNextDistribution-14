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
import com.android.storage.block.read.BlockInfo;

import java.io.IOException;

/**
 * The base interface for objects that can write block data. Once {@link #close()} is called, the
 * returned {@link ReadBack} can be used to obtain information about the block, e.g. for copying
 * elsewhere.
 */
public interface BlockWriter {

    /**
     * Completes writing the block, and returns an object that can be used to read back the block's
     * information, e.g. for inclusion in a block file.
     */
    ReadBack close() throws IOException;

    /** Information about a written block. */
    interface ReadBack {

        /**
         * Returns the block's extra bytes, e.g. for inclusion in a {@link BlockInfo}.
         * Never {@code null}, but may be empty.
         */
        byte[] getExtraBytes();

        /** Returns the type of the block. */
        int getType();

        /**
         * Returns the block's data. Never {@code null} but may be empty.
         */
        BlockData getBlockData();
    }
}
