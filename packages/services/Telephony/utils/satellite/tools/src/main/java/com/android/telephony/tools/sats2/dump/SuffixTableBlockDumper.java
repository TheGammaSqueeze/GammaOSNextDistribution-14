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

package com.android.telephony.tools.sats2.dump;

import static com.android.storage.tools.block.dump.DumpUtils.binaryStringLength;
import static com.android.storage.tools.block.dump.DumpUtils.createPrintWriter;
import static com.android.storage.tools.block.dump.DumpUtils.generateDumpFile;
import static com.android.storage.tools.block.dump.DumpUtils.hexStringLength;
import static com.android.storage.tools.block.dump.DumpUtils.zeroPadBinary;
import static com.android.storage.tools.block.dump.DumpUtils.zeroPadHex;

import com.android.telephony.sats2range.read.SuffixTableBlock;

import java.io.File;
import java.io.PrintWriter;
import java.util.Objects;

/** A {@link SuffixTableBlock.SuffixTableBlockVisitor} that dumps information to a file. */
public final class SuffixTableBlockDumper implements SuffixTableBlock.SuffixTableBlockVisitor {

    private final File mOutputDir;

    private final int mMaxPrefix;

    public SuffixTableBlockDumper(File outputDir, int maxPrefix) {
        mOutputDir = Objects.requireNonNull(outputDir);
        mMaxPrefix = maxPrefix;
    }

    @Override
    public void visit(SuffixTableBlock suffixTableBlock) throws VisitException {
        int tablePrefix = suffixTableBlock.getPrefix();
        int prefixHexLength = hexStringLength(tablePrefix);
        int prefixBinaryLength = binaryStringLength(tablePrefix);
        File suffixTableFile =
                generateDumpFile(mOutputDir, "suffixtable_", tablePrefix, mMaxPrefix);
        try (PrintWriter writer = createPrintWriter(suffixTableFile)) {
            writer.println("Prefix value=" + zeroPadBinary(prefixBinaryLength, tablePrefix)
                    + " (" + zeroPadHex(prefixHexLength, tablePrefix) + ")");
            int entryCount = suffixTableBlock.getEntryCount();
            writer.println("Entry count=" + entryCount);
            if (entryCount > 0) {
                for (int i = 0; i < entryCount; i++) {
                    writer.println(
                            "[" + i + "]=" + suffixTableBlock.getEntryByIndex(i)
                                    .getSuffixTableRange());
                }
            }
        }
    }
}

