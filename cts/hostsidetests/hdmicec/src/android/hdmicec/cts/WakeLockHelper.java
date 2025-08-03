/*
 * Copyright 2021 The Android Open Source Project
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

package android.hdmicec.cts;

import com.android.tradefed.device.ITestDevice;

/**
 * Helper class to help standby test acquire and release wakelock to avoid device going into deep
 * sleep.
 */
public final class WakeLockHelper {
    private static final String TAG = "HdmiCecWakeLock";
    private static final String ACQUIRE_LOCK = "android.hdmicec.app.ACQUIRE_LOCK";
    private static final String RELEASE_LOCK = "android.hdmicec.app.RELEASE_LOCK";

    /** The package name of the APK. */
    private static final String PACKAGE = "android.hdmicec.app";

    /** The class name of the wake lock activity in the APK. */
    private static final String CLASS = "HdmiCecWakeLock";

    /** The command to launch the wake lock activity. */
    private static final String START_COMMAND =
            String.format("am start -n %s/%s.%s -a ", PACKAGE, PACKAGE, CLASS);

    /** The command to clear the wake lock activity. */
    private static final String CLEAR_COMMAND = String.format("pm clear %s", PACKAGE);

    private static boolean mWakelockAcquired = false;

    public static void acquirePartialWakeLock(ITestDevice device) throws Exception {
        if (mWakelockAcquired == false) {
            // Clear activity if any.
            device.executeShellCommand(CLEAR_COMMAND);
            // Start the APK to acquire the wake lock and wait for it to complete.
            device.executeShellCommand(START_COMMAND + ACQUIRE_LOCK);
            LogHelper.assumeLog(device, TAG, "Acquired wake lock.");
            mWakelockAcquired = true;
        }
    }

    public static void releasePartialWakeLock(ITestDevice device) throws Exception {
        if (mWakelockAcquired == true) {
            // Release the acquired wake lock.
            device.executeShellCommand(START_COMMAND + RELEASE_LOCK);
            // Clear activity
            device.executeShellCommand(CLEAR_COMMAND);
            mWakelockAcquired = false;
        }
    }
}
