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
package com.android.timezone.location.provider;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

/** An interface for classes that know how to obtain time zone geo data. */
public interface GeoDataFileManager {

    /**
     * Performs any actions needed to make the geo data file available via {@link
     * #getGeoDataFile()}.
     */
    void init(@NonNull Context context, @NonNull Properties configProperties) throws IOException;

    /**
     * Returns the location of the geo data file. Throws an exception if {@link
     * #init(Context, Properties)} failed.
     */
    @NonNull
    File getGeoDataFile();
}
