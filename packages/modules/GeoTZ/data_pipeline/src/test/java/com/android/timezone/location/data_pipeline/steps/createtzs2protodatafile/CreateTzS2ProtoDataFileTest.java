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

package com.android.timezone.location.data_pipeline.steps.createtzs2protodatafile;

import static com.android.timezone.location.data_pipeline.steps.TestSupport.copyTestResource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.timezone.location.data_pipeline.steps.CreateTzS2ProtoDataFile;
import com.android.timezone.location.data_pipeline.steps.TestSupport;
import com.android.timezone.location.data_pipeline.steps.Types.ProtoStorageFormat;
import com.android.timezone.location.tools.proto.GeotzProtos;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Tests for {@link CreateTzS2ProtoDataFile}. */
public final class CreateTzS2ProtoDataFileTest {

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
        String inputResource = "merged_tzs2ranges.prototxt";
        Path inputDirPath = mTempDirPath.resolve("input");
        Path inputFilePath = copyTestResource(getClass(), inputResource, inputDirPath);

        Path outputDirPath = mTempDirPath.resolve("output");
        Files.createDirectory(outputDirPath);
        Path outputFilePath = outputDirPath.resolve("tzs2datafile.prototxt");

        String[] args = {
                "--input-file", inputFilePath.toAbsolutePath().toString(),
                "--output-file", outputFilePath.toAbsolutePath().toString()
        };
        CreateTzS2ProtoDataFile.main(args);

        File actualOutputFile = outputFilePath.toFile();
        assertTrue("Expecting " + actualOutputFile + " to exist", actualOutputFile.exists());
        GeotzProtos.TimeZones actual = loadTzS2ProtoDataFile(actualOutputFile);

        Path expectedDirPath = mTempDirPath.resolve("expected");
        Path expectedFilePath = copyTestResource(CreateTzS2ProtoDataFileTest.class,
                "tzs2datafile.prototxt", expectedDirPath
        );
        GeotzProtos.TimeZones expected = loadTzS2ProtoDataFile(expectedFilePath.toFile());

        assertEquals(expected, actual);
    }

    private GeotzProtos.TimeZones loadTzS2ProtoDataFile(File actualOutputFile) throws Exception {
        GeotzProtos.TimeZones.Builder builder = GeotzProtos.TimeZones.newBuilder();
        ProtoStorageFormat.TEXT.load(builder, actualOutputFile);
        return builder.build();
    }
}
