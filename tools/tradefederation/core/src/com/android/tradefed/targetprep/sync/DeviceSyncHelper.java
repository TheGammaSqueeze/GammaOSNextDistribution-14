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
package com.android.tradefed.targetprep.sync;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.invoker.tracing.CloseableTraceScope;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RunUtil;

import com.google.common.collect.ImmutableSet;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Helper that helps syncing a new device image to the device. Future improvements: compute files
 * that need to be deleted and files that needs to be pushed instead of full folders.
 */
public class DeviceSyncHelper {

    private final ITestDevice mDevice;
    private final File mTargetFilesFolder;

    // Partitions known by adb in "adb sync"
    private static final Set<String> ADB_PARTITIONS =
            ImmutableSet.of("data", "odm", "oem", "product", "system", "system_ext", "vendor");

    public DeviceSyncHelper(ITestDevice device, File targetFilesFolder) {
        mDevice = device;
        mTargetFilesFolder = targetFilesFolder;
    }

    public boolean sync() throws DeviceNotAvailableException {
        try {
            Set<String> partitions = getPartitions(mTargetFilesFolder);
            partitions.add("data");
            CLog.d("Partitions: %s", partitions);
            lowerCaseDirectory(mTargetFilesFolder);
            syncFiles(mDevice, partitions);
            return true;
        } catch (IOException e) {
            CLog.e(e);
        }
        return false;
    }

    private Set<String> getPartitions(File rootFolder) throws IOException {
        File abPartitions = new File(rootFolder, "META/ab_partitions.txt");
        String partitionString = FileUtil.readStringFromFile(abPartitions);
        return new LinkedHashSet<>(Arrays.asList(partitionString.split("\n")));
    }

    private void lowerCaseDirectory(File rootFolder) {
        for (File f : rootFolder.listFiles()) {
            if (f.isDirectory()) {
                File newName = new File(f.getParentFile(), f.getName().toLowerCase());
                f.renameTo(newName);
            }
        }
        CLog.d("Directory content: %s", Arrays.asList(rootFolder.listFiles()));
    }

    private void syncFiles(ITestDevice device, Set<String> partitions)
            throws DeviceNotAvailableException, IOException {
        device.enableAdbRoot();
        device.remountSystemWritable();
        device.enableAdbRoot();
        String outputRemount = device.executeAdbCommand("remount", "-R");
        CLog.d("%s", outputRemount);
        device.waitForDeviceAvailable();
        device.executeAdbCommand("shell", "stop");
        RunUtil.getDefault().sleep(20000L);

        device.setRecoveryMode(RecoveryMode.NONE);
        // Use adb sync when supported
        try (CloseableTraceScope push = new CloseableTraceScope("sync all")) {
            Map<String, String> env = new HashMap<>();
            env.put("ANDROID_PRODUCT_OUT", mTargetFilesFolder.getAbsolutePath());
            String output = device.executeAdbCommand(0L, env, "sync", "all");
            if (output == null) {
                throw new IOException("Failed to sync all");
            }
            CLog.d("%s", output);
        }

        for (String partition : partitions) {
            File localToPush = new File(mTargetFilesFolder, partition);
            if (!localToPush.exists()) {
                CLog.w("%s is in the partition but doesn't exist", partition);
                continue;
            }
            if (ADB_PARTITIONS.contains(partition)) {
                continue;
            }
            // Push after deleting to ensure shell is fine
            try (CloseableTraceScope push = new CloseableTraceScope("push " + partition)) {
                String output =
                        device.executeAdbCommand(0L, "push", localToPush.getAbsolutePath(), "/");
                if (output == null) {
                    throw new IOException("Failed to push " + localToPush);
                }
            }
        }

        try (CloseableTraceScope reboot = new CloseableTraceScope("reboot")) {
            String output = device.executeAdbCommand("reboot");
            CLog.d("reboot output: %s", output);
            device.waitForDeviceNotAvailable(30 * 1000L);
            device.waitForDeviceAvailable(15 * 60 * 1000);
        }
        device.enableAdbRoot();
    }

}
