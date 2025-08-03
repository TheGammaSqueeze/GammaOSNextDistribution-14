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

package com.android.timezone.location.data_pipeline.steps.geojsontz_to_tzs2polygons;

import static com.android.timezone.location.data_pipeline.steps.TestSupport.copyTestResource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.timezone.location.data_pipeline.steps.GeoJsonTzToTzS2Polygons;
import com.android.timezone.location.data_pipeline.steps.TestSupport;
import com.android.timezone.location.data_pipeline.steps.Types.ProtoStorageFormat;
import com.android.timezone.location.data_pipeline.steps.Types.TzS2Polygons;
import com.android.timezone.location.data_pipeline.steps.TzIds;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/** Tests for {@link GeoJsonTzToTzS2Polygons}. */
public final class GeoJsonTzToTzS2PolygonsTest {

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
        // A file containing just the polygons for Europe/London.
        // Created using timezone-boundary-builder:
        // node --max-old-space-size=8192 index.js --included_zones Europe/London
        String inputResource = "Europe_London.geojson";
        String timeZoneId = "Europe/London";
        ProtoStorageFormat storageFormat = ProtoStorageFormat.TEXT;

        Path inputDirPath = mTempDirPath.resolve("input");
        Path inputFilePath = copyTestResource(getClass(), inputResource, inputDirPath);

        final int threads = 1;
        Path outputDirPath = mTempDirPath.resolve("output");

        String[] args = {
                "--geo-json", inputFilePath.toAbsolutePath().toString(),
                "--num-threads", Integer.toString(threads),
                "--output", outputDirPath.toAbsolutePath().toString()
        };
        GeoJsonTzToTzS2Polygons.main(args);

        Path expectedOutputDirPath = mTempDirPath.resolve("expected");
        Path expectedOutputFilePath = copyTestResource(getClass(),
                TzIds.fileNameSafe(timeZoneId) + TzS2Polygons.getFileSuffix(storageFormat),
                expectedOutputDirPath
        );
        String expectedFileName = expectedOutputFilePath.getFileName().toString();
        File actualOutputFile = new File(outputDirPath.toFile(), expectedFileName);
        assertTrue("Expecting " + actualOutputFile + " to exist", actualOutputFile.exists());

        TzS2Polygons actual = TzS2Polygons.load(actualOutputFile, storageFormat);
        TzS2Polygons expected = TzS2Polygons.load(expectedOutputFilePath.toFile(), storageFormat);

        assertEquals(expected, actual);
    }
}
