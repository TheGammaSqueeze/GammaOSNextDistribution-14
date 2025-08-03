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

package com.android.storage.tools.block.dump;

import com.android.storage.block.read.Block;

import java.io.File;

/** A {@link Block.BlockVisitor} that dumps information to a file. */
public final class BlockDumper extends SingleFileDumper implements Block.BlockVisitor {

    public BlockDumper(File blockFile) {
        super(blockFile);
    }

    @Override
    public void visit(Block block) {
        println("id=" + block.getId());
        println("type=" + block.getType());
        println("block size=" + block.getData().getSize());
    }
}
