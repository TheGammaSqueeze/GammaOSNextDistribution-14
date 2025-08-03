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

package com.android.timezone.location.data_pipeline.steps.mergetzs2ranges;

import static com.android.timezone.location.data_pipeline.steps.TestSupport.copyTestResource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.timezone.location.data_pipeline.steps.MergeTzS2Ranges;
import com.android.timezone.location.data_pipeline.steps.TestSupport;
import com.android.timezone.location.data_pipeline.steps.Types.ProtoStorageFormat;
import com.android.timezone.location.data_pipeline.steps.Types.TzS2Ranges;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Tests for {@link MergeTzS2Ranges}. */
public final class MergeTzS2RangesTest {

    private Path mTempDirPath;

    @Before
    public void setUp() throws IOException {
        mTempDirPath = TestSupport.createTempDir(this.getClass());
    }

    @After
    public void tearDown() throws IOException {
        TestSupport.deleteDirectory(mTempDirPath);
    }

    /**
     * A regression test that should spot obvious, possibly unintentional, changes to the output
     * data by executing the test class with known input and output data.
     */
    @Test
    public void regressionTest() throws Exception {
        // Resources containing the ranges for Europe/London, Europe/Isle_of_Man and Europe/Paris.
        // Europe/Isle_of_Man and Europe/Paris overlap with Europe/London, but not each other. The
        // ranges for Europe/London, Europe/Isle_of_Man and Europe/Paris will have to be split to
        // merge them together.
        String inputResource1 = "Europe_London_tzs2ranges.prototxt";
        String inputResource2 = "Europe_Isle_of_Man_tzs2ranges.prototxt";
        String inputResource3 = "Europe_Paris_tzs2ranges.prototxt";
        ProtoStorageFormat storageFormat = ProtoStorageFormat.TEXT;

        Path inputDirPath = mTempDirPath.resolve("input");
        copyTestResource(getClass(), inputResource1, inputDirPath);
        copyTestResource(getClass(), inputResource2, inputDirPath);
        copyTestResource(getClass(), inputResource3, inputDirPath);

        String outputFileName = "merged" + TzS2Ranges.getFileSuffix(storageFormat);

        final int threads = 1;
        Path workingDirPath = mTempDirPath.resolve("working");
        Files.createDirectory(workingDirPath);

        Path actualDirPath = mTempDirPath.resolve("actual");
        Files.createDirectory(actualDirPath);
        Path actualFilePath = actualDirPath.resolve(outputFileName);

        String[] args = {
                "--input", inputDirPath.toAbsolutePath().toString(),
                "--num-threads", Integer.toString(threads),
                "--working-dir", workingDirPath.toAbsolutePath().toString(),
                "--output-file", actualFilePath.toAbsolutePath().toString(),
        };
        MergeTzS2Ranges.main(args);

        File actualFile = actualFilePath.toFile();
        assertTrue("Expecting " + actualFile + " to exist", actualFile.exists());

        TzS2Ranges actual = TzS2Ranges.load(actualFile, storageFormat);

        Path expectedOutputDirPath = mTempDirPath.resolve("expected");
        Path expectedOutputFilePath =
                copyTestResource(getClass(), outputFileName, expectedOutputDirPath);
        TzS2Ranges expected = TzS2Ranges.load(expectedOutputFilePath.toFile(), storageFormat);
        assertEquals(expected, actual);
    }
}
