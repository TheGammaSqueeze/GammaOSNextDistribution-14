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

package com.android.cts.appcloning;


import static org.junit.Assert.assertNotNull;

import com.android.modules.utils.build.testing.DeviceSdkLevel;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.NativeDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.RunUtil;

import com.google.common.collect.Iterables;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;


abstract class BaseHostTestCase extends BaseHostJUnit4Test {
    private int mCurrentUserId = NativeDevice.INVALID_USER_ID;
    private static final String ERROR_MESSAGE_TAG = "[ERROR]";
    protected static ITestDevice sDevice = null;
    private static final String MEDIA_PROVIDER_MODULE_NAME = "android.providers.media.module";

    protected static void setDevice(ITestDevice device) {
        sDevice = device;
    }

    protected String executeShellCommand(String cmd, Object... args) throws Exception {
        return sDevice.executeShellCommand(String.format(cmd, args));
    }

    protected CommandResult executeShellV2Command(String cmd, Object... args) throws Exception {
        return sDevice.executeShellV2Command(String.format(cmd, args));
    }

    protected boolean isPackageInstalled(String packageName, String userId) throws Exception {
        return sDevice.isPackageInstalled(packageName, userId);
    }

    // TODO (b/174775905) remove after exposing the check from ITestDevice.
    protected static boolean isHeadlessSystemUserMode() throws DeviceNotAvailableException {
        String result = sDevice
                .executeShellCommand("getprop ro.fw.mu.headless_system_user").trim();
        return "true".equalsIgnoreCase(result);
    }

    protected static boolean supportsMultipleUsers() throws DeviceNotAvailableException {
        return sDevice.getMaxNumberOfUsersSupported() > 1;
    }

    protected static boolean isAtLeastS() throws DeviceNotAvailableException {
        DeviceSdkLevel deviceSdkLevel = new DeviceSdkLevel(sDevice);
        return deviceSdkLevel.isDeviceAtLeastS();
    }

    protected boolean isAtLeastT() throws DeviceNotAvailableException {
        DeviceSdkLevel deviceSdkLevel = new DeviceSdkLevel(sDevice);
        return deviceSdkLevel.isDeviceAtLeastT();
    }

    protected static boolean isAtLeastU(ITestDevice device) throws DeviceNotAvailableException {
        DeviceSdkLevel deviceSdkLevel = new DeviceSdkLevel(device);
        return deviceSdkLevel.isDeviceAtLeastU();
    }

    protected static boolean isAtLeastV(ITestDevice device) throws DeviceNotAvailableException {
        DeviceSdkLevel deviceSdkLevel = new DeviceSdkLevel(device);
        return deviceSdkLevel.isDeviceAtLeastV();
    }

    protected static void throwExceptionIfTimeout(long start, long timeoutMillis, Throwable e) {
        if (System.currentTimeMillis() - start < timeoutMillis) {
            RunUtil.getDefault().sleep(100);
        } else {
            throw new RuntimeException(e);
        }
    }

    protected static void eventually(ThrowingRunnable r, long timeoutMillis) {
        long start = System.currentTimeMillis();

        while (true) {
            try {
                r.run();
                return;
            } catch (Throwable e) {
                throwExceptionIfTimeout(start, timeoutMillis, e);
            }
        }
    }

    protected static void eventually(ThrowingBooleanSupplier booleanSupplier,
            long timeoutMillis, String failureMessage) {
        long start = System.currentTimeMillis();

        while (true) {
            try {
                if (booleanSupplier.getAsBoolean()) {
                    return;
                }

                throw new RuntimeException(failureMessage);
            } catch (Throwable e) {
                throwExceptionIfTimeout(start, timeoutMillis, e);
            }
        }
    }

    protected int getCurrentUserId() throws Exception {
        setCurrentUserId();

        return mCurrentUserId;
    }

    protected static boolean isSuccessful(CommandResult result) {
        if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
            return false;
        }
        String stdout = result.getStdout();
        if (stdout.contains(ERROR_MESSAGE_TAG)) {
            return false;
        }
        String stderr = result.getStderr();
        return (stderr == null || stderr.trim().isEmpty());
    }

    private void setCurrentUserId() throws Exception {
        if (mCurrentUserId != NativeDevice.INVALID_USER_ID) return;

        mCurrentUserId = sDevice.getCurrentUser();
        CLog.i("Current user: %d");
    }

    protected interface ThrowingRunnable {
        /**
         * Similar to {@link Runnable#run} but has {@code throws Exception}.
         */
        void run() throws Exception;
    }

    protected interface ThrowingBooleanSupplier {
        /**
         * Similar to {@link BooleanSupplier#getAsBoolean} but has {@code throws Exception}.
         */
        boolean getAsBoolean() throws Exception;
    }

    protected static String getPublicVolumeExcluding(String excludingVolume) throws Exception {
        List<String> volList = splitMultiLineOutput(sDevice.executeShellCommand("sm list-volumes"));

        // list volumes will result in something like
        // private mounted null
        // public:7,281 mounted 3080-17E8
        // emulated;0 mounted null
        // and we are interested in 3080-17E8
        for (String volume: volList) {
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

    protected static boolean partitionDisks() {
        try {
            List<String> diskNames = splitMultiLineOutput(sDevice
                    .executeShellCommand("sm list-disks"));
            if (!diskNames.isEmpty() && !Iterables.getLast(diskNames).isEmpty()) {
                sDevice.executeShellCommand("sm partition "
                        + Iterables.getLast(diskNames) + " public");
                return true;
            }
        } catch (Exception ignored) {
            //ignored
        }
        return false;
    }

    protected String getMediaProviderProcess(String userId) throws Exception {
        String processUserPrefix = "u" + userId;
        List<String> mediaProcessList = splitMultiLineOutput(sDevice
                .executeShellCommand("ps -A -o user,name,pid | grep -i "
                        + MEDIA_PROVIDER_MODULE_NAME));
        // Media Process list will be something like:
        // u0_a246      com.google.android.providers.media.module 21403
        // u10_a246     com.google.android.providers.media.module 25576
        for (String mediaProcess : mediaProcessList) {
            if (mediaProcess.startsWith(processUserPrefix)) {
                //u0_a246      com.google.android.providers.media.module 21403
                String[] splits = mediaProcess.split(" ");
                //Return the last snippet, that is 21403
                return splits[splits.length - 1];
            }
        }
        return null;
    }

    protected boolean isUserVolumeMounted(String userId) throws Exception {
        String mountStatus = sDevice
                .executeShellCommand("dumpsys mount | grep mountUserId=" + userId);
        // Mount Status will be something like:
        // type=EMULATED diskId=null partGuid= mountFlags=PRIMARY|VISIBLE_FOR_WRITE mountUserId=11
        // state=MOUNTED
        assertNotNull("No Volumes Found for user " + userId, mountStatus);
        return mountStatus.contains("state=MOUNTED");
    }

    private static List<String> splitMultiLineOutput(String input) throws Exception {
        if (input == null) {
            return new ArrayList<>();
        }
        return Arrays.asList(input.split("\\r?\\n"));
    }
}
