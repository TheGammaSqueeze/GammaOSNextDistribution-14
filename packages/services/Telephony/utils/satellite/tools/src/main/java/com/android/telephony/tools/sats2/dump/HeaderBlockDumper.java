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

import com.android.storage.tools.block.dump.SingleFileDumper;
import com.android.telephony.sats2range.read.HeaderBlock;
import com.android.telephony.sats2range.read.SatS2RangeFileFormat;

import java.io.File;

/** A {@link HeaderBlock.HeaderBlockVisitor} that dumps information to a file. */
final class HeaderBlockDumper extends SingleFileDumper implements HeaderBlock.HeaderBlockVisitor {

    HeaderBlockDumper(File headerBlockFile) {
        super(headerBlockFile);
    }

    @Override
    public void visitFileFormat(SatS2RangeFileFormat fileFormat) {
        println("File format");
        println("===========");
        println(fileFormat.toString());
        println();
    }
}

