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
import static com.android.storage.tools.block.dump.DumpUtils.hexStringLength;
import static com.android.storage.tools.block.dump.DumpUtils.zeroPadBinary;
import static com.android.storage.tools.block.dump.DumpUtils.zeroPadHex;

import com.android.storage.tools.block.dump.SingleFileDumper;
import com.android.telephony.sats2range.read.HeaderBlock;
import com.android.telephony.sats2range.read.SatS2RangeFileFormat;
import com.android.telephony.sats2range.read.SatS2RangeFileReader;
import com.android.telephony.sats2range.read.SuffixTableBlock;
import com.android.telephony.sats2range.read.SuffixTableExtraInfo;

import java.io.File;

/** A {@link SatS2RangeFileReader.SatS2RangeFileVisitor} that dumps information to a file. */
public final class SatS2RangeFileDumper implements SatS2RangeFileReader.SatS2RangeFileVisitor {

    private final File mOutputDir;

    private int mMaxPrefix;

    private int mMaxPrefixBinaryLength;

    private int mMaxPrefixHexLength;

    private SingleFileDumper mExtraInfoDumper;

    public SatS2RangeFileDumper(File outputDir) {
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
    public void visitHeaderBlock(HeaderBlock headerBlock) throws VisitException {
        File headerFile = new File(mOutputDir, "header.txt");
        headerBlock.visit(new HeaderBlockDumper(headerFile));
        SatS2RangeFileFormat fileFormat = headerBlock.getFileFormat();
        mMaxPrefix = fileFormat.getMaxPrefixValue();
        mMaxPrefixBinaryLength = binaryStringLength(mMaxPrefix);
        mMaxPrefixHexLength = hexStringLength(mMaxPrefix);
    }

    @Override
    public void visitSuffixTableBlock(SuffixTableBlock suffixTableBlock)
            throws VisitException {
        suffixTableBlock.visit(new SuffixTableBlockDumper(mOutputDir, mMaxPrefix));
    }

    @Override
    public void end() throws VisitException {
        mExtraInfoDumper.end();
    }
}

