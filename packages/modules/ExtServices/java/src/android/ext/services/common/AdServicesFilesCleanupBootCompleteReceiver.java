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

package android.ext.services.common;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.DeviceConfig;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.util.function.ToIntBiFunction;

/**
 * Handles the BootCompleted initialization for ExtServices APK on T+.
 * <p>
 * The BootCompleted receiver deletes files created by the AdServices code on S- that persist on
 * disk after an OTA to T+. Once these files are deleted, this receiver disables itself.
 * <p>
 * Since this receiver disables itself after the first run, it will not be re-run after any code
 * changes to this class. In order to re-enable this receiver and run the updated code, the simplest
 * way is to rename the class every upon every module release that changes the code. Also, in order
 * to protect against accidental name re-use, the {@code testReceiverDoesNotReuseClassNames} unit
 * test tracking used names should be updated upon each rename as well.
 */
public class AdServicesFilesCleanupBootCompleteReceiver extends BroadcastReceiver {
    private static final String TAG = "extservices";
    private static final String KEY_RECEIVER_ENABLED =
            "extservices_adservices_data_cleanup_enabled";

    // All files created by the AdServices code within ExtServices should have this prefix.
    private static final String ADSERVICES_PREFIX = "adservices";

    @TargetApi(Build.VERSION_CODES.TIRAMISU) // Receiver disabled in manifest for S- devices
    @SuppressWarnings("ReturnValueIgnored") // Intentionally ignoring return value of Log.d/Log.e
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "AdServices files cleanup receiver received BOOT_COMPLETED broadcast for user "
                + context.getUser().getIdentifier());

        // Check if the feature flag is enabled, otherwise exit without doing anything.
        if (!isReceiverEnabled()) {
            Log.d(TAG, "AdServices files cleanup receiver not enabled in config, exiting");
            return;
        }

        try {
            // Look through and delete any files in the data dir that have the `adservices` prefix
            boolean success = deleteAdServicesFiles(context.getDataDir());

            // Log as `d` or `e` depending on success or failure.
            ToIntBiFunction<String, String> function = success ? Log::d : Log::e;
            function.applyAsInt(TAG,
                    "AdServices files cleanup receiver data deletion success: " + success);

            scheduleAppsearchDeleteJob(context);
        } finally {
            unregisterSelf(context);
        }
    }

    private void unregisterSelf(Context context) {
        context.getPackageManager().setComponentEnabledSetting(
                new ComponentName(context, this.getClass()),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                /* flags= */ 0);
        Log.d(TAG, "Disabled AdServices files cleanup receiver");
    }

    @VisibleForTesting
    public boolean isReceiverEnabled() {
        return DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* name= */ KEY_RECEIVER_ENABLED,
                /* defaultValue= */ true);
    }

    /**
     * Recursively delete all files with a prefix of "adservices" from the specified directory.
     * <p>
     * Note: It expects the input File object to be a directory and not a regular file. Also,
     * it only deletes the contents of the input directory, and not the directory itself, even if
     * the name of the directory starts with the prefix.
     *
     * @param currentDirectory the directory to scan for files
     * @return {@code true} if all adservices files were successfully deleted; else {@code false}.
     */
    @VisibleForTesting
    public boolean deleteAdServicesFiles(File currentDirectory) {
        if (currentDirectory == null) {
            Log.d(TAG, "Argument passed to deleteAdServicesFiles is null");
            return true;
        }

        try {
            if (!currentDirectory.isDirectory()) {
                Log.d(TAG, "Argument passed to deleteAdServicesFiles is not a directory");
                return true;
            }

            boolean allSuccess = true;

            File[] files = currentDirectory.listFiles();
            for (File file : files) {
                if (file.isDirectory()) {
                    // Delete ALL data if the directory name starts with the adservices prefix.
                    // Otherwise, delete any file in the subtree that starts with the prefix.
                    if (doesFileNameStartWithPrefix(file)) {
                        // Directory starting with adservices, so delete everything inside it.
                        allSuccess = deleteAllData(file) && allSuccess;
                    } else {
                        // Directory but not starting with adservices, so only delete adservices
                        // files.
                        allSuccess = deleteAdServicesFiles(file) && allSuccess;
                    }
                } else if (doesFileNameStartWithPrefix(file)) {
                    allSuccess = safeDelete(file) && allSuccess;
                }
            }

            return allSuccess;
        } catch (RuntimeException e) {
            Log.e(TAG, "Error deleting directory " + currentDirectory.getName(), e);
            return false;
        }
    }

    private boolean doesFileNameStartWithPrefix(File file) {
        // Do a case-insensitive comparison
        return ADSERVICES_PREFIX.regionMatches(
                /* ignoreCase= */ true,
                /* toOffset= */ 0,
                file.getName(),
                /* ooffset= */ 0,
                /* len= */ ADSERVICES_PREFIX.length());
    }

    private boolean deleteAllData(File currentDirectory) {
        if (currentDirectory == null) {
            Log.d(TAG, "Argument passed to deleteAllData is null");
            return true;
        }

        try {
            if (!currentDirectory.isDirectory()) {
                Log.d(TAG, "Argument passed to deleteAllData is not a directory");
                return true;
            }

            boolean allSuccess = true;

            for (File file : currentDirectory.listFiles()) {
                allSuccess = (file.isDirectory() ? deleteAllData(file) : safeDelete(file))
                        && allSuccess;
            }

            // If deleting the entire subdirectory has been successful, then (and only then) delete
            // the current directory.
            allSuccess = allSuccess && safeDelete(currentDirectory);

            return allSuccess;
        } catch (RuntimeException e) {
            Log.e(TAG, "Error deleting directory " + currentDirectory.getName(), e);
            return false;
        }
    }

    private boolean safeDelete(File file) {
        try {
            return file.delete();
        } catch (RuntimeException e) {
            String message = String.format(
                    "AdServices files cleanup receiver: Error deleting %s - %s", file.getName(),
                    e.getMessage());
            Log.e(TAG, message, e);
            return false;
        }
    }

    /**
     * Schedules background periodic job AdservicesAppsearchDeleteJob
     * to delete Appsearch data after OTA and data migration
     *
     * @param context the android context
     **/
    @VisibleForTesting
    public void scheduleAppsearchDeleteJob(Context context) {
        AdServicesAppsearchDeleteJob
                .scheduleAdServicesAppsearchDeletePeriodicJob(context,
                        new AdservicesPhFlags());
    }
}
