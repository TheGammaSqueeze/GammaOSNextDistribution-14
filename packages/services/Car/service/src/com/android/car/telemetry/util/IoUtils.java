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

package com.android.car.telemetry.util;

import android.annotation.NonNull;
import android.car.builtin.util.Slogf;
import android.os.PersistableBundle;
import android.util.AtomicFile;

import com.android.car.CarLog;

import com.google.protobuf.MessageLite;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Utility class for car telemetry I/O operations. */
public class IoUtils {

    /**
     * Reads a {@link PersistableBundle} from the file system.
     *
     * @param bundleFile file location of the PersistableBundle.
     * @return {@link PersistableBundle} stored in the given file.
     * @throws IOException for read failure.
     */
    @NonNull
    public static PersistableBundle readBundle(@NonNull File bundleFile) throws IOException {
        AtomicFile atomicFile = new AtomicFile(bundleFile);
        try (FileInputStream fis = atomicFile.openRead()) {
            return PersistableBundle.readFromStream(fis);
        }
    }

    /**
     * Saves a {@link PersistableBundle} to a file.
     *
     * @param dir      directory to save the bundle to.
     * @param fileName file name to save the file as.
     * @param bundle   to be saved.
     * @throws IOException for write failure.
     */
    public static void writeBundle(
            @NonNull File dir, @NonNull String fileName, @NonNull PersistableBundle bundle)
            throws IOException {
        writeBundle(new File(dir, fileName), bundle);
    }

    /**
     * Saves a {@link PersistableBundle} to a file.
     *
     * @param dest   file location to save the {@link PersistableBundle}.
     * @param bundle to be saved.
     * @throws IOException for write failure.
     */
    public static void writeBundle(@NonNull File dest, @NonNull PersistableBundle bundle)
            throws IOException {
        AtomicFile atomicFile = new AtomicFile(dest);
        try (FileOutputStream fos = atomicFile.startWrite()) {
            try {
                bundle.writeToStream(fos);
                atomicFile.finishWrite(fos);
            } catch (IOException e) {
                atomicFile.failWrite(fos);
                throw e;
            }
        }
    }

    /**
     * Saves a protobuf message to file.
     *
     * @param dir directory to save the file to.
     * @param fileName name to save the file as.
     * @param proto to be saved.
     * @throws IOException for write failure.
     */
    public static void writeProto(
            @NonNull File dir, @NonNull String fileName, @NonNull MessageLite proto)
            throws IOException {
        writeProto(new File(dir, fileName), proto);
    }

    /**
     * Savea  protobuf message to file.
     *
     * @param dest  file location to save the protobuf message.
     * @param proto to be saved.
     * @throws IOException for write failure.
     */
    public static void writeProto(
            @NonNull File dest, @NonNull MessageLite proto) throws IOException {
        AtomicFile atomicFile = new AtomicFile(dest);
        try (FileOutputStream fos = atomicFile.startWrite()) {
            try {
                fos.write(proto.toByteArray());
                atomicFile.finishWrite(fos);
            } catch (IOException e) {
                atomicFile.failWrite(fos);
                throw e;
            }
        }
    }

    /**
     * Deletes the file silently from the file system if it exists. Return true for success, false
     * for failure.
     */
    public static boolean deleteSilently(@NonNull File directory, @NonNull String fileName) {
        try {
            return Files.deleteIfExists(Paths.get(
                    directory.getAbsolutePath(), fileName));
        } catch (IOException e) {
            Slogf.w(CarLog.TAG_TELEMETRY, "Failed to delete file " + fileName + " in directory "
                    + directory.getAbsolutePath(), e);
            // TODO(b/197153560): record failure
        }
        return false;
    }

    /**
     * Deletes all files silently from the directory. This method does not delete recursively.
     */
    public static void deleteAllSilently(@NonNull File directory) {
        File[] files = directory.listFiles();
        if (files == null) {
            Slogf.i(CarLog.TAG_TELEMETRY, "Skip deleting the empty dir %s", directory.getName());
            return;
        }
        for (File file : files) {
            if (!file.delete()) {
                Slogf.w(CarLog.TAG_TELEMETRY, "Failed to delete file " + file.getName()
                        + " in directory " + directory.getAbsolutePath());
            }
        }
    }

    /**
     * Deletes all files in the specified directories that are stale/older than some threshold.
     * This method does not delete recursively.
     *
     * @param staleThresholdMillis the threshold to classify a file as stale.
     * @param dirs                 the directories to remove stale files from.
     */
    public static void deleteOldFiles(long staleThresholdMillis, @NonNull File... dirs) {
        long currTimeMs = System.currentTimeMillis();
        for (File dir : dirs) {
            File[] files = dir.listFiles();
            if (files == null) {
                Slogf.i(CarLog.TAG_TELEMETRY, "Skip deleting the empty dir %s", dir.getName());
                continue;
            }
            for (File file : files) {
                // delete stale data
                if (file.lastModified() + staleThresholdMillis < currTimeMs) {
                    file.delete();
                }
            }
        }
    }

    /** Quietly closes Java Closeables, ignoring IOException. */
    public static void closeQuietly(@NonNull Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            // Ignore
        }
    }
}
