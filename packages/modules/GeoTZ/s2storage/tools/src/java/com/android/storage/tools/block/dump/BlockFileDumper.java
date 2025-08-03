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

import static com.android.storage.tools.block.dump.DumpUtils.createPrintWriter;

import com.android.storage.block.read.Block;
import com.android.storage.block.read.BlockFileReader.BlockFileVisitor;
import com.android.storage.block.read.BlockInfo;

import java.io.File;
import java.io.PrintWriter;

/** A {@link BlockFileVisitor} that dumps information to multiple files. */
public final class BlockFileDumper implements BlockFileVisitor {

    private final File mOutputDir;

    private int mBlockCount;

    public BlockFileDumper(File outputDir) {
        mOutputDir = outputDir;
    }

    @Override
    public void visitFileHeader(char magic, int version, int blockCount) throws VisitException {
        File fileHeaderFile = new File(mOutputDir, "fileheader.txt");
        try (PrintWriter writer = createPrintWriter(fileHeaderFile)) {
            writer.println("Magic=" + Integer.toHexString(magic));
            writer.println("Version=" + version);
            writer.println("Block count=" + blockCount);
        }
        mBlockCount = blockCount;
    }

    @Override
    public void visitBlockInfo(BlockInfo blockInfo) throws VisitException {
        File blockInfoFile = DumpUtils.generateDumpFile(
                mOutputDir, "blockinfo_", blockInfo.getId(), mBlockCount);
        blockInfo.visit(new BlockInfoDumper(blockInfoFile));
    }

    @Override
    public void visitBlock(Block block) throws VisitException {
        File blockFile =
                DumpUtils.generateDumpFile(mOutputDir, "block_", block.getId(), mBlockCount);
        block.visit(new BlockDumper(blockFile));
    }
}
