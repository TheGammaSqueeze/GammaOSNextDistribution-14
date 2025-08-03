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

package com.android.storage.tools.block;

import com.android.storage.block.read.BlockFileReader;
import com.android.storage.tools.block.dump.BlockFileDumper;

import java.io.File;

/** Dumps low-level information about a block file in text form for debugging / analysis. */
public final class DumpBlockFile {

    /**
     * Usage:
     * DumpBlockFile <[input] block file name> <[output] directory name>
     */
    public static void main(String[] args) throws Exception {
        String blockFileName = args[0];
        String outputDirName = args[1];
        boolean memoryMapBlocks = false;

        File outputDir = new File(outputDirName);
        outputDir.mkdirs();

        File blockFile = new File(blockFileName);
        try (BlockFileReader reader = BlockFileReader.open(memoryMapBlocks, blockFile)) {
            reader.visit(new BlockFileDumper(outputDir));
        }
    }
}
