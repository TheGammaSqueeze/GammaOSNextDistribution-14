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

package com.android.timezone.location.data_pipeline.steps.canonicalizetzs2polygons;

import static com.android.timezone.location.data_pipeline.steps.TestSupport.copyTestResource;
import static com.android.timezone.location.data_pipeline.steps.TestSupport.copyTestResourceWithoutLicense;
import static com.android.timezone.location.data_pipeline.steps.Types.DEFAULT_PROTO_STORAGE_FORMAT;

import static org.junit.Assert.assertEquals;

import static java.time.ZoneOffset.UTC;

import com.android.timezone.location.data_pipeline.steps.CanonicalizeTzS2Polygons;
import com.android.timezone.location.data_pipeline.steps.TestSupport;
import com.android.timezone.location.data_pipeline.steps.Types.TzS2Polygons;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/** Tests for {@link CanonicalizeTzS2Polygons}. */
public final class CanonicalizeTzS2PolygonsTest {

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
        String[] inputTzS2PolygonsResources = {
                "America_Argentina_Cordoba_tzs2polygons.prototxt",
                "America_Argentina_Ushuaia_tzs2polygons.prototxt",
                "GB_tzs2polygons.prototxt",
        };
        Path inputPolysDirPath = mTempDirPath.resolve("input_polygons");
        for (String inputTzS2PolygonResource : inputTzS2PolygonsResources) {
            copyTestResource(getClass(), "input_polygons/" + inputTzS2PolygonResource,
                    inputPolysDirPath);
        }

        String inputTzIdsResource = "tzids.prototxt";
        Path tzIdsDir = mTempDirPath.resolve("tzids");
        Path tzIdsFile = copyTestResourceWithoutLicense(getClass(), inputTzIdsResource, tzIdsDir);

        Path outputDirPath = mTempDirPath.resolve("output");
        Files.createDirectory(outputDirPath);

        String[] args = {
                "--input", inputPolysDirPath.toAbsolutePath().toString(),
                "--tz-ids", tzIdsFile.toAbsolutePath().toString(),
                "--replacement-threshold",
                LocalDateTime.of(2020, 1, 1, 0, 0, 0).toInstant(UTC).toString(),
                "--output", outputDirPath.toAbsolutePath().toString()
        };
        CanonicalizeTzS2Polygons.main(args);

        List<File> outputPolygons =
                TzS2Polygons.listFiles(outputDirPath.toFile(), DEFAULT_PROTO_STORAGE_FORMAT);
        assertEquals(2, outputPolygons.size());

        Path expectedDirPath = mTempDirPath.resolve("expected");
        String[] expectedTzS2PolygonsResources = {
                "Europe_London_tzs2polygons.prototxt",
                "America_Argentina_Buenos_Aires_tzs2polygons.prototxt",
        };
        for (String expectedTzS2PolygonsResource : expectedTzS2PolygonsResources) {
            checkOutputPolyFile(outputDirPath, expectedDirPath, expectedTzS2PolygonsResource);
        }
    }

    private void checkOutputPolyFile(
            Path outputDirPath, Path expectedDirPath, String expectedTzS2PolygonResource)
            throws Exception {
        String expectedPolyResource = "output_polygons/" + expectedTzS2PolygonResource;
        Path expectedPolyFile =
                copyTestResource(getClass(), expectedPolyResource, expectedDirPath);
        TzS2Polygons expectedTzS2Polygons =
                TzS2Polygons.load(expectedPolyFile.toFile(), DEFAULT_PROTO_STORAGE_FORMAT);
        File actualTzS2PolygonsFile = new File(outputDirPath.toFile(), expectedTzS2PolygonResource);
        TzS2Polygons actualTzS2Polygons =
                TzS2Polygons.load(actualTzS2PolygonsFile, DEFAULT_PROTO_STORAGE_FORMAT);
        assertEquals(expectedPolyFile + " is expected to match " + actualTzS2PolygonsFile,
                expectedTzS2Polygons, actualTzS2Polygons);
    }
}
