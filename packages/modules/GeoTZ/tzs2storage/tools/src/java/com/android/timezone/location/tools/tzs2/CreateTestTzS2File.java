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

import com.android.timezone.location.storage.tzs2range.TzS2Range;
import com.android.timezone.location.storage.tzs2range.TzS2RangeFileFormat;
import com.android.timezone.location.storage.tzs2range.write.TzS2RangeFileWriter;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/** Creates a TZ S2 file with a small amount of test data. Useful for testing other tools. */
public final class CreateTestTzS2File {

    /**
     * Usage:
     * CreateTestTzS2File &lt;file name&gt;
     */
    public static void main(String[] args) throws Exception {
        File file = new File(args[0]);

        TzS2RangeFileFormat fileFormat = FileFormats.getFileFormatForLevel(12);
        if (fileFormat.getPrefixBitCount() != 11) {
            throw new IllegalStateException("Fake data requires 11 prefix bits");
        }

        try (TzS2RangeFileWriter tzS2RangeFileWriter = TzS2RangeFileWriter.open(file, fileFormat)) {
            // Two ranges that share a prefix.
            TzS2Range range1 = new TzS2Range(
                    fileFormat.createCellId(0b100_11111111, 1000),
                    fileFormat.createCellId(0b100_11111111, 2000),
                    listOf("One"));
            TzS2Range range2 = new TzS2Range(
                    fileFormat.createCellId(0b100_11111111, 2000),
                    fileFormat.createCellId(0b100_11111111, 3000),
                    listOf("Two"));
            // This range has a different face, so a different prefix, and will be in a different
            // suffix table.
            TzS2Range range3 = new TzS2Range(
                    fileFormat.createCellId(0b101_11111111, 1000),
                    fileFormat.createCellId(0b101_11111111, 2000),
                    listOf("Three"));
            List<TzS2Range> allRanges = listOf(range1, range2, range3);
            tzS2RangeFileWriter.processRanges(allRanges.iterator());
        }

    }

    @SafeVarargs
    private static <E> List<E> listOf(E... values) {
        return Arrays.asList(values);
    }
}
