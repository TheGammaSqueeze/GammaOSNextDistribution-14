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

package com.android.adservices.service.common.compat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.android.modules.utils.build.SdkLevel;

import java.io.File;

/** Utility class for handling file names in a backward-compatible manner */
@SuppressLint("NewAdServicesFile")
public final class FileCompatUtils {
    private static final String ADSERVICES_PREFIX = "adservices";

    private FileCompatUtils() {
        // prevent instantiation
    }

    /**
     * returns the appropriate filename to use based on Android version, prepending "adservices_"
     * for S-. The underscore is for human readability. The code for deleting files after OTA will
     * check only for "adservices" so files that begin with this already do not need to be updated
     */
    public static String getAdservicesFilename(String basename) {
        if (SdkLevel.isAtLeastT()
                || ADSERVICES_PREFIX.regionMatches(
                        /* ignoreCase= */ true,
                        /* toffset= */ 0,
                        basename,
                        /* ooffset= */ 0,
                        /* len= */ ADSERVICES_PREFIX.length())) {
            return basename;
        }

        return ADSERVICES_PREFIX + "_" + basename;
    }

    /**
     * returns a RoomDatabase.Builder instance for the given context, class, and name, while
     * ensuring the filename is prepended with "adservices" on S-.
     */
    @SuppressLint("NewAdServicesFile")
    public static <T extends RoomDatabase> RoomDatabase.Builder<T> roomDatabaseBuilderHelper(
            Context context, Class<T> klass, String name) {
        return Room.databaseBuilder(
                context,
                klass,
                getAdservicesFilename(name) /* make sure filename is valid for S- */);
    }

    /**
     * calls Context.getDataPath to return a File for the given context and name, while ensuring the
     * filename is prepended with "adservices" on S-.
     */
    public static File getDatabasePathHelper(Context context, String name) {
        return context.getDatabasePath(getAdservicesFilename(name));
    }

    /**
     * creates a new File from the given parent and child, while ensuring the child filename is
     * prepended with "adservices" on S-.
     */
    public static File newFileHelper(File parent, String child) {
        return new File(parent, getAdservicesFilename(child));
    }

    /**
     * returns a Sharedpreferences for the given context, name, and mode, while ensuring the
     * filename is prepended with "adservices" on S-.
     */
    public static SharedPreferences getSharedPreferencesHelper(
            Context context, String name, int mode) {
        return context.getSharedPreferences(getAdservicesFilename(name), mode);
    }
}
