/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.ondevicepersonalization.services.data.vendor;

import com.android.ondevicepersonalization.internal.util.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FileUtils {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "FileUtils";
    private FileUtils() {}

    /**
     * Deletes all files from the directory that no longer
     * exist in the given keySet or are not the most recent version.
     */
    public static void cleanUpFilesDir(Set<String> keySet, File dir) {
        // Delete any non-recent files with same key or non-existent key.
        List<File> filesToDelete = new ArrayList<>();
        Map<String, File> filesSeen = new HashMap<>();
        if (dir.isDirectory()) {
            for (File f : dir.listFiles()) {
                try {
                    String[] fileNameList = f.getName().split("_");
                    long timestamp = Long.parseLong(fileNameList[1]);
                    String fKey = fileNameList[0];

                    // Key no longer exists in DB. Mark for deletion
                    if (!keySet.contains(fKey)) {
                        filesToDelete.add(f);
                    }

                    // If duplicate key, mark oldest key for deletion
                    if (filesSeen.containsKey(fKey)) {
                        File existingFile = filesSeen.get(fKey);
                        if (timestamp < Long.parseLong(existingFile.getName().split("_")[1])) {
                            filesToDelete.add(f);
                        } else {
                            filesToDelete.add(existingFile);
                            filesSeen.put(fKey, f);
                        }
                    } else {
                        filesSeen.put(fKey, f);
                    }
                } catch (Exception e) {
                    // Delete any files that do not match expected format.
                    sLogger.w(TAG + " :Failed to parse file: " + f.getName(), e);
                    filesToDelete.add(f);
                }
            }
        }
        for (File f : filesToDelete) {
            f.delete();
        }
    }

    /**
     * Deletes a directory and all files recursively
     */
    public static void deleteDirectory(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteDirectory(child);
            }
        }
        fileOrDirectory.delete();
    }

}
