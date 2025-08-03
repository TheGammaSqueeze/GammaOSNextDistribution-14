/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.geotz.app;

import android.content.Context;

import androidx.annotation.NonNull;

import com.android.timezone.location.provider.GeoDataFileManager;
import com.google.common.hash.Hashing;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Properties;

/**
 * An implementation of {@link GeoDataFileManager} that copies the tzs2.dat classpath resource to
 * an app file path for reading.
 */
public final class GeoDataFileManagerImpl implements GeoDataFileManager {

    /**
     * The config properties key to get the resource path of the tzs2.dat file to use for time zone
     * boundaries.
     */
    private static final String RESOURCE_CONFIG_KEY_GEODATA_RESOURCE = "geodata.resource";

    /**
     * The config properties key to get the location of the tzs2.dat file to use for time zone
     * boundaries.
     */
    private static final String RESOURCE_CONFIG_KEY_GEODATA_SHA256= "geodata.sha256";

    private File mGeoDataFile;

    @Override
    public void init(@NonNull Context context, @NonNull Properties configProperties)
            throws IOException {
        File geoDataFile = new File(context.getFilesDir(), "tzs2.dat");
        ClassLoader classLoader = getClass().getClassLoader();

        String geoDataResource = configProperties.getProperty(RESOURCE_CONFIG_KEY_GEODATA_RESOURCE);
        String expectedSha256 = configProperties.getProperty(RESOURCE_CONFIG_KEY_GEODATA_SHA256);
        if (!geoDataFile.exists() || !Objects.equals(computeSha256(geoDataFile), expectedSha256)) {
            // Copy the resource to the app persistent files dir.
            copyResource(classLoader, geoDataResource, geoDataFile, expectedSha256);
        }

        mGeoDataFile = geoDataFile;
    }

    @Override
    @NonNull
    public File getGeoDataFile() {
        return Objects.requireNonNull(mGeoDataFile);
    }

    private static void copyResource(@NonNull ClassLoader classLoader, @NonNull String resourceName,
            @NonNull File targetPath, @NonNull String expectedSha256) {
        boolean success = false;
        try (InputStream resourceStream = classLoader.getResourceAsStream(resourceName)) {
            if (resourceStream == null) {
                throw new IllegalStateException("Unable to find "
                        + " resource=" + resourceName);
            }
            Files.copy(resourceStream, targetPath.toPath(), StandardCopyOption.REPLACE_EXISTING);
            String actualSha256 = computeSha256(targetPath);
            if (!actualSha256.equals(expectedSha256)) {
                throw new IllegalStateException(
                        "SHA256 expected=" + expectedSha256 + ", actual=" + actualSha256);
            }
            success = true;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to copy resource from"
                    + " resource=" + resourceName
                    + " to targetPath=" + targetPath, e);
        } finally {
            if (!success) {
                targetPath.delete();
            }
        }
    }

    private static String computeSha256(File file) {
        try {
            return com.google.common.io.Files.asByteSource(file).hash(Hashing.sha256()).toString();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to compute hash for file=" + file);
        }
    }
}
