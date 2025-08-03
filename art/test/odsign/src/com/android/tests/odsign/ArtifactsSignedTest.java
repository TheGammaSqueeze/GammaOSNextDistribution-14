/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tests.odsign;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assume.assumeTrue;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ArtifactsSignedTest {
    private static final String TAG = "VerifyArtArtifactsSignedTest";
    private static final String ARTIFACTS_DIR = "/data/misc/apexdata/com.android.art/dalvik-cache";
    private static final String FS_VERITY_PROC_PATH = "/proc/sys/fs/verity";

    // Note that some of these files may exist multiple times - for different architectures
    // Verifying that they are generated for the correct architectures is currently out of
    // scope for this test.
    private static final String[] REQUIRED_ARTIFACT_NAMES = {
        "boot.art",
        "boot.oat",
        "boot.vdex",
        "system@framework@services.jar@classes.vdex",
        "system@framework@services.jar@classes.odex",
        "system@framework@services.jar@classes.art",
    };

    static {
        System.loadLibrary("OdsignTestAppJni");
    }

    private static native boolean hasFsverityNative(@NonNull String path);

    public boolean isFsVeritySupported() {
        return new File(FS_VERITY_PROC_PATH).exists();
    }

    @Test
    public void testArtArtifactsHaveFsverity() throws Exception {
        assumeTrue("fs-verity is not supported on this device.", isFsVeritySupported());
        assertWithMessage("Found artifacts not in fs-verity")
                .that(getArtifacts().map(File::getPath).filter((path) -> !hasFsverityNative(path))
                        .collect(Collectors.toList()))
                .isEmpty();
    }

    @Test
    public void testGeneratesRequiredArtArtifacts() throws Exception {
        assertThat(getArtifacts().map(File::getName).collect(Collectors.toList()))
                .containsAtLeastElementsIn(REQUIRED_ARTIFACT_NAMES);
    }

    @Test
    public void testGeneratesAnyArtArtifacts() throws Exception {
        assertThat(getArtifacts().collect(Collectors.toList())).isNotEmpty();
    }

    private Stream<File> getArtifacts() throws Exception {
        return Files.walk(Paths.get(ARTIFACTS_DIR), Integer.MAX_VALUE)
                .map(Path::toFile)
                .filter(File::isFile);
    }
}
