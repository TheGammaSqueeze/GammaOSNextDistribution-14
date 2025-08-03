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

package android.os.storage.cts;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.collect.Iterables;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

final class StorageManagerHelper {

    private static final long POLLING_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(20);
    private static final long POLLING_SLEEP_MILLIS = 100;

    /**
     * Creates a virtual disk that simulates SDCard on a device. It is
     * mounted as a public visible disk.
     * @return the volume name of the disk just created
     * @throws Exception, if the volume could not be created
     */
    static String createSDCardVirtualDisk() throws Exception {
        return createDiskAndGetVolumeName(true);
    }
    /**
     * Creates a virtual disk that simulates USB on a device. It is
     * mounted as a public invisible disk.
     * @return the volume name of the disk just created
     * @throws Exception, if the volume could not be created
     */
    static String createUSBVirtualDisk() throws Exception {
        return createDiskAndGetVolumeName(false);
    }

    /**
     * Removes the simulated disk
     */
    static void removeVirtualDisk() throws Exception {
        executeShellCommand("sm set-virtual-disk false");
        //sleep to make sure that it is unmounted
        Thread.sleep(5000);
    }

    /**
     * Create a public volume for testing and only return the one newly created as the volumeName.
     */
    public static String createDiskAndGetVolumeName(boolean visible) throws Exception {
        //remove any existing volume that was mounted before
        removeVirtualDisk();
        String existingPublicVolume = getPublicVolumeExcluding(null);
        executeShellCommand("sm set-force-adoptable " + (visible ? "on" : "off"));
        executeShellCommand("sm set-virtual-disk true");
        Thread.sleep(10000);
        pollForCondition(StorageManagerHelper::partitionDisks,
                "Could not create public volume in time");
        return getPublicVolumeExcluding(existingPublicVolume);
    }

    private static boolean partitionDisks() {
        try {
            List<String> diskNames = executeShellCommand("sm list-disks");
            if (!diskNames.isEmpty()) {
                executeShellCommand("sm partition " + Iterables.getLast(diskNames) + " public");
                return true;
            }
        } catch (Exception ignored) {
            //ignored
        }
        return false;
    }

    private static void pollForCondition(Supplier<Boolean> condition, String errorMessage)
            throws Exception {
        Thread.sleep(2000);
        for (int i = 0; i < POLLING_TIMEOUT_MILLIS / POLLING_SLEEP_MILLIS; i++) {
            if (condition.get()) {
                return;
            }
            Thread.sleep(POLLING_SLEEP_MILLIS);
        }
        throw new TimeoutException(errorMessage);
    }

    private static String getPublicVolumeExcluding(String excludingVolume) throws Exception {

        List<String> volumes = executeShellCommand("sm list-volumes");
        // list volumes will result in something like
        // private mounted null
        // public:7,281 mounted 3080-17E8
        // emulated;0 mounted null
        // and we are interested in 3080-17E8
        for (String volume: volumes) {
            if (volume.contains("public")
                    && (excludingVolume == null || !volume.contains(excludingVolume))) {
                //public:7,281 mounted 3080-17E8
                String[] splits = volume.split(" ");
                //Return the last snippet, that is 3080-17E8
                return splits[splits.length - 1];
            }
        }
        return null;
    }

    public static boolean isAdoptableStorageSupported(Context context) throws Exception {
        return hasAdoptableStorageFeature(context) || hasAdoptableStorageFstab();
    }

    private static boolean hasAdoptableStorageFstab() throws Exception {
        List<String> hasAdoptable = executeShellCommand("sm has-adoptable");
        if (hasAdoptable.isEmpty()) {
            return false;
        }
        return Boolean.parseBoolean(hasAdoptable.get(0).trim());
    }

    private static boolean hasAdoptableStorageFeature(Context context) throws Exception {
        return context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_ADOPTABLE_STORAGE);
    }

    private static List<String> executeShellCommand(String command) throws Exception {
        final ParcelFileDescriptor pfd = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand(command);
        BufferedReader br = null;
        try (InputStream in = new FileInputStream(pfd.getFileDescriptor())) {
            br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String str = null;
            List<String> output = new ArrayList<>();
            while ((str = br.readLine()) != null) {
                output.add(str);
            }
            return output;
        } finally {
            if (br != null) {
                closeQuietly(br);
            }
            closeQuietly(pfd);
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {

            try {
                closeable.close();
            } catch (RuntimeException rethrown) {
                throw rethrown;
            } catch (Exception ignored) {
                Log.w("StorageManagerHelper", ignored.getMessage());
            }
        }
    }
}
