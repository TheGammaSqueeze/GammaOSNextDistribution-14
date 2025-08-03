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

package com.android.telephony.sats2range;

import static org.junit.Assert.assertEquals;

import com.android.storage.s2.S2LevelRange;
import com.android.telephony.sats2range.read.SatS2RangeFileFormat;
import com.android.telephony.sats2range.read.SatS2RangeFileReader;
import com.android.telephony.sats2range.utils.TestUtils;
import com.android.telephony.sats2range.write.SatS2RangeFileWriter;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SatS2RangeFileReaderTest {
    @Test
    public void findEntryByCellId() throws IOException {
        File file = File.createTempFile("test", ".dat");

        SatS2RangeFileFormat fileFormat;
        boolean isAllowedList = true;
        S2LevelRange expectedRange1, expectedRange2, expectedRange3;
        try (SatS2RangeFileWriter satS2RangeFileWriter = SatS2RangeFileWriter.open(
                file, TestUtils.createS2RangeFileFormat(isAllowedList))) {
            fileFormat = satS2RangeFileWriter.getFileFormat();

            // Two ranges that share a prefix.
            expectedRange1 = new S2LevelRange(
                    TestUtils.createCellId(fileFormat, 1, 1000, 1000),
                    TestUtils.createCellId(fileFormat, 1, 1000, 2000));
            expectedRange2 = new S2LevelRange(
                    TestUtils.createCellId(fileFormat, 1, 1000, 2000),
                    TestUtils.createCellId(fileFormat, 1, 1000, 3000));
            // This range has a different prefix, so will be in a different suffix table.
            expectedRange3 = new S2LevelRange(
                    TestUtils.createCellId(fileFormat, 1, 1001, 1000),
                    TestUtils.createCellId(fileFormat, 1, 1001, 2000));

            List<S2LevelRange> ranges = new ArrayList<>();
            ranges.add(expectedRange1);
            ranges.add(expectedRange2);
            ranges.add(expectedRange3);
            satS2RangeFileWriter.createSortedSuffixBlocks(ranges.iterator());
        }

        try (SatS2RangeFileReader satS2RangeFileReader = SatS2RangeFileReader.open(file)) {
            assertEquals(isAllowedList, satS2RangeFileReader.isAllowedList());

            S2LevelRange range1 = satS2RangeFileReader.findEntryByCellId(
                    TestUtils.createCellId(fileFormat, 1, 1000, 1500));
            assertEquals(expectedRange1, range1);

            S2LevelRange range2 = satS2RangeFileReader.findEntryByCellId(
                    TestUtils.createCellId(fileFormat, 1, 1000, 2500));
            assertEquals(expectedRange2, range2);

            S2LevelRange range3 = satS2RangeFileReader.findEntryByCellId(
                    TestUtils.createCellId(fileFormat, 1, 1001, 1500));
            assertEquals(expectedRange3, range3);
        }
    }
}
