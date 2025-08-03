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

package com.android.server.healthconnect.utils;

import android.os.Environment;

import java.io.File;

/**
 * Class to help with the filesystem related methods.
 *
 * @hide
 */
public final class FilesUtil {
    /**
     * Get the health connect dir for the user to store sensitive data in a credential encrypted
     * dir.
     */
    public static File getDataSystemCeHCDirectoryForUser(int userId) {
        // Duplicates the implementation of Environment#getDataSystemCeDirectory
        // TODO(b/191059409): Unhide Environment#getDataSystemCeDirectory and switch to it.
        File systemCeDir = new File(Environment.getDataDirectory(), "system_ce");
        File systemCeUserDir = new File(systemCeDir, String.valueOf(userId));
        return new File(systemCeUserDir, "healthconnect");
    }

    /** Delete the dir recursively. */
    public static void deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (var file : files) {
                if (file.isDirectory()) {
                    deleteDir(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }

    private FilesUtil() {}
}
