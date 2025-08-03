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

package com.android.timezone.location.tools.tzs2;

import com.android.storage.tools.block.DumpBlockFile;
import com.android.timezone.location.storage.tzs2range.read.TzS2RangeFileReader;
import com.android.timezone.location.tools.tzs2.dump.TzS2RangeFileDumper;

import java.io.File;

/**
 * Dumps information about a TZ S2 data file. Like {@link DumpBlockFile} but it knows details about
 * the TZ S2 format and can provide more detailed information.
 */
public final class DumpTzS2File {

    /**
     * Usage:
     * DumpTzS2File <[input] tz s2 file name> <[output] output directory name>
     */
    public static void main(String[] args) throws Exception {
        String tzS2FileName = args[0];
        String outputDirName = args[1];

        File outputDir = new File(outputDirName);
        outputDir.mkdirs();

        File tzS2File = new File(tzS2FileName);
        try (TzS2RangeFileReader reader = TzS2RangeFileReader.open(tzS2File)) {
            reader.visit(new TzS2RangeFileDumper(outputDir));
        }
    }
}
