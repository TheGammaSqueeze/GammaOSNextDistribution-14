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

package com.android.timezone.location.tools.tzs2.dump;

import static com.android.storage.tools.block.dump.DumpUtils.binaryStringLength;
import static com.android.storage.tools.block.dump.DumpUtils.hexStringLength;
import static com.android.storage.tools.block.dump.DumpUtils.zeroPadBinary;
import static com.android.storage.tools.block.dump.DumpUtils.zeroPadHex;

import com.android.storage.tools.block.dump.SingleFileDumper;
import com.android.storage.util.Visitor;
import com.android.timezone.location.storage.tzs2range.TzS2RangeFileFormat;
import com.android.timezone.location.storage.tzs2range.read.HeaderBlock;
import com.android.timezone.location.storage.tzs2range.read.SuffixTableBlock;
import com.android.timezone.location.storage.tzs2range.read.SuffixTableExtraInfo;
import com.android.timezone.location.storage.tzs2range.read.TzS2RangeFileReader;

import java.io.File;

/** A {@link TzS2RangeFileReader.TzS2RangeFileVisitor} that dumps information to a file. */
public final class TzS2RangeFileDumper implements TzS2RangeFileReader.TzS2RangeFileVisitor {

    private final File mOutputDir;

    private int mMaxPrefix;

    private int mMaxPrefixBinaryLength;

    private int mMaxPrefixHexLength;

    private SingleFileDumper mExtraInfoDumper;

    public TzS2RangeFileDumper(File outputDir) {
        mOutputDir = outputDir;
    }

    @Override
    public void begin() throws VisitException {
        mExtraInfoDumper = new SingleFileDumper(new File(mOutputDir, "suffixtable_extrainfo.txt"));
        mExtraInfoDumper.begin();
    }

    @Override
    public void visitSuffixTableExtraInfo(SuffixTableExtraInfo suffixTableExtraInfo) {
        int prefix = suffixTableExtraInfo.getPrefix();
        mExtraInfoDumper.println("prefix=" + zeroPadBinary(mMaxPrefixBinaryLength, prefix)
                + "(" + zeroPadHex(mMaxPrefixHexLength, prefix) + ")"
                + ", entryCount=" + suffixTableExtraInfo.getEntryCount());
    }

    @Override
    public void visitHeaderBlock(HeaderBlock headerBlock) throws Visitor.VisitException {
        File headerFile = new File(mOutputDir, "header.txt");
        headerBlock.visit(new HeaderBlockDumper(headerFile));
        TzS2RangeFileFormat fileFormat = headerBlock.getFileFormat();
        mMaxPrefix = fileFormat.getMaxPrefixValue();
        mMaxPrefixBinaryLength = binaryStringLength(mMaxPrefix);
        mMaxPrefixHexLength = hexStringLength(mMaxPrefix);
    }

    @Override
    public void visitSuffixTableBlock(SuffixTableBlock suffixTableBlock)
            throws Visitor.VisitException {
        suffixTableBlock.visit(new SuffixTableBlockDumper(mOutputDir, mMaxPrefix));
    }

    @Override
    public void end() throws VisitException {
        mExtraInfoDumper.end();
    }
}
