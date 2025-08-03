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
package com.android.geotz.apex;

import android.content.Context;

import androidx.annotation.NonNull;

import com.android.timezone.location.provider.GeoDataFileManager;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Properties;

/**
 * An implementation of {@link GeoDataFileManager} that reads the tzs2.dat file from a file path.
 */
public final class GeoDataFileManagerImpl implements GeoDataFileManager {

    /**
     * The config properties key to get the file system path of the tzs2.dat file to use for time
     * zone boundaries.
     */
    private static final String RESOURCE_CONFIG_KEY_GEODATA_PATH = "geodata.path";

    private File mGeoDataFile;

    @Override
    public void init(@NonNull Context context, @NonNull Properties configProperties)
            throws IOException {
        String geoDataPath = configProperties.getProperty(RESOURCE_CONFIG_KEY_GEODATA_PATH);
        mGeoDataFile = new File(geoDataPath);
        if (!(mGeoDataFile.isFile() && mGeoDataFile.canRead())) {
            throw new IOException("File not found or is not readable: " + mGeoDataFile);
        }
    }

    @Override
    @NonNull
    public File getGeoDataFile() {
        return Objects.requireNonNull(mGeoDataFile);
    }
}
