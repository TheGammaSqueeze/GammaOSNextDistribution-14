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

import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import com.android.telephony.sats2range.read.SatS2RangeFileFormat;
import com.android.telephony.sats2range.read.SatS2RangeFileReader;
import com.android.telephony.sats2range.utils.TestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


/** Tests for {@link CreateSatS2File} */
public final class CreateSatS2FileTest {
    private Path mTempDirPath;

    @Before
    public void setUp() throws IOException {
        mTempDirPath = TestUtils.createTempDir(this.getClass());
    }

    @After
    public void tearDown() throws IOException {
        if (mTempDirPath != null) {
            TestUtils.deleteDirectory(mTempDirPath);
        }
    }

    @Test
    public void testCreateSatS2FileWithValidInput_AllowedList() throws Exception {
        testCreateSatS2FileWithValidInput(true);
    }

    @Test
    public void testCreateSatS2FileWithValidInput_DisallowedList() throws Exception {
        testCreateSatS2FileWithValidInput(false);
    }

    @Test
    public void testCreateSatS2FileWithInvalidInput() throws Exception {
        int s2Level = 12;
        boolean isAllowedList = true;
        Path inputDirPath = mTempDirPath.resolve("input");
        Files.createDirectory(inputDirPath);
        Path inputFilePath = inputDirPath.resolve("s2cells.txt");

        Path outputDirPath = mTempDirPath.resolve("output");
        Files.createDirectory(outputDirPath);
        Path outputFilePath = outputDirPath.resolve("sats2.dat");

        // Create test input S2 cell file
        SatS2RangeFileFormat fileFormat = FileFormats.getFileFormatForLevel(s2Level, isAllowedList);
        TestUtils.createInvalidTestS2CellFile(inputFilePath.toFile(), fileFormat);

        // Commandline input arguments
        String[] args = {
                "--input-file", inputFilePath.toAbsolutePath().toString(),
                "--s2-level", String.valueOf(s2Level),
                "--is-allowed-list", isAllowedList ? "true" : "false",
                "--output-file", outputFilePath.toAbsolutePath().toString()
        };

        // Execute the tool CreateSatS2File and expect exception
        try {
            CreateSatS2File.main(args);
        } catch (Exception ex) {
            // Expected exception
            return;
        }
        fail("Exception should have been caught");
    }

    private void testCreateSatS2FileWithValidInput(boolean isAllowedList) throws Exception {
        int s2Level = 12;
        Path inputDirPath = mTempDirPath.resolve("input");
        Files.createDirectory(inputDirPath);
        Path inputFilePath = inputDirPath.resolve("s2cells.txt");

        Path outputDirPath = mTempDirPath.resolve("output");
        Files.createDirectory(outputDirPath);
        Path outputFilePath = outputDirPath.resolve("sats2.dat");

        /*
         * Create test input S2 cell file with the following ranges:
         * 1) [(prefix=0b100_11111111, suffix=1000), (prefix=0b100_11111111, suffix=2000))
         * 2) [(prefix=0b100_11111111, suffix=2001), (prefix=0b100_11111111, suffix=3000))
         * 3) [(prefix=0b101_11111111, suffix=1000), (prefix=0b101_11111111, suffix=2001))
         */
        SatS2RangeFileFormat fileFormat = FileFormats.getFileFormatForLevel(s2Level, isAllowedList);
        TestUtils.createValidTestS2CellFile(inputFilePath.toFile(), fileFormat);

        // Commandline input arguments
        String[] args = {
                "--input-file", inputFilePath.toAbsolutePath().toString(),
                "--s2-level", String.valueOf(s2Level),
                "--is-allowed-list", isAllowedList ? "true" : "false",
                "--output-file", outputFilePath.toAbsolutePath().toString()
        };

        // Execute the tool CreateSatS2File and expect successful result
        try {
            CreateSatS2File.main(args);
        } catch (Exception ex) {
            fail("Unexpected exception when executing the tool ex=" + ex);
        }

        // Validate the output block file
        try {
            SatS2RangeFileReader satS2RangeFileReader =
                         SatS2RangeFileReader.open(outputFilePath.toFile());
            if (isAllowedList != satS2RangeFileReader.isAllowedList()) {
                fail("isAllowedList="
                        + satS2RangeFileReader.isAllowedList() + " does not match the input "
                        + "argument=" + isAllowedList);
            }

            // Verify an edge cell (prefix=0b100_11111111, suffix=100)
            long s2CellId = fileFormat.createCellId(0b100_11111111, 100);
            assertNull(satS2RangeFileReader.findEntryByCellId(s2CellId));

            // Verify a middle cell (prefix=0b100_11111111, suffix=2000)
            s2CellId = fileFormat.createCellId(0b100_11111111, 2000);
            assertNull(satS2RangeFileReader.findEntryByCellId(s2CellId));

            // Verify an edge cell (prefix=0b100_11111111, suffix=4000)
            s2CellId = fileFormat.createCellId(0b100_11111111, 4000);
            assertNull(satS2RangeFileReader.findEntryByCellId(s2CellId));

            // Verify an edge cell (prefix=0b101_11111111, suffix=500)
            s2CellId = fileFormat.createCellId(0b101_11111111, 500);
            assertNull(satS2RangeFileReader.findEntryByCellId(s2CellId));

            // Verify an edge cell (prefix=0b101_11111111, suffix=2001)
            s2CellId = fileFormat.createCellId(0b101_11111111, 2500);
            assertNull(satS2RangeFileReader.findEntryByCellId(s2CellId));

            // Verify an edge cell (prefix=0b101_11111111, suffix=2500)
            s2CellId = fileFormat.createCellId(0b101_11111111, 2500);
            assertNull(satS2RangeFileReader.findEntryByCellId(s2CellId));
        } catch (Exception ex) {
            fail("Unexpected exception when validating the output ex=" + ex);
        }
    }
}
