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

package com.android.telephony.tools.sats2;

import com.android.storage.tools.block.DumpBlockFile;
import com.android.telephony.sats2range.read.SatS2RangeFileReader;
import com.android.telephony.tools.sats2.dump.SatS2RangeFileDumper;

import java.io.File;

/**
 * Dumps information about a Sat S2 data file. Like {@link DumpBlockFile} but it knows details about
 * the Sat S2 format and can provide more detailed information.
 */
public final class DumpSatS2File {

    /**
     * Usage:
     * DumpSatFile <[input] sat s2 file name> <[output] output directory name>
     */
    public static void main(String[] args) throws Exception {
        String satS2FileName = args[0];
        String outputDirName = args[1];

        File outputDir = new File(outputDirName);
        outputDir.mkdirs();

        File satS2File = new File(satS2FileName);
        try (SatS2RangeFileReader reader = SatS2RangeFileReader.open(satS2File)) {
            reader.visit(new SatS2RangeFileDumper(outputDir));
        }
    }
}
