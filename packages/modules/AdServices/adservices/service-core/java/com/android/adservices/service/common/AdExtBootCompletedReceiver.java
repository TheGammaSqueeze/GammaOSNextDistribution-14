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

package com.android.adservices.service.common;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;

import com.android.adservices.AdServicesCommon;
import com.android.adservices.LogUtil;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;

import java.util.List;
import java.util.Objects;

/** Handles the BootCompleted initialization for AdExtServices APK on S-. */
// TODO(b/269798827): Enable for R.
public class AdExtBootCompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        LogUtil.i("AdExtBootCompletedReceiver onReceive invoked");

        // On T+ devices, always disable the AdExtServices activities and services.
        if (SdkLevel.isAtLeastT()) {
            // If this is not an S- device, disable the activities, services, unregister the
            // broadcast receivers, and unschedule any background jobs.
            unregisterPackageChangedBroadcastReceivers(context);
            updateAdExtServicesActivities(context, /* shouldEnable= */ false);
            updateAdExtServicesServices(context, /* shouldEnable= */ false);
            disableScheduledBackgroundJobs(context);
            return;
        }

        // If this is an S- device but the flags are disabled, do nothing.
        if (!FlagsFactory.getFlags().getEnableBackCompat()
                || !FlagsFactory.getFlags().getAdServicesEnabled()
                || FlagsFactory.getFlags().getGlobalKillSwitch()) {
            LogUtil.d("Exiting AdExtBootCompletedReceiver because flags are disabled");
            return;
        }

        registerPackagedChangedBroadcastReceivers(context);
        updateAdExtServicesActivities(context, /* shouldEnable= */ true);
        updateAdExtServicesServices(context, /* shouldEnable= */ true);
    }

    /**
     * Cancels all scheduled jobs if running within the ExtServices APK. Needed because we could
     * have some persistent jobs that were scheduled on S before an OTA to T.
     */
    @VisibleForTesting
    void disableScheduledBackgroundJobs(@NonNull Context context) {
        Objects.requireNonNull(context);

        try {
            String packageName = context.getPackageName();
            if (packageName == null
                    || packageName.endsWith(AdServicesCommon.ADSERVICES_APK_PACKAGE_NAME_SUFFIX)) {
                // Running within the AdServices package, so don't do anything.
                LogUtil.e("Running within AdServices package, not changing scheduled job state");
                return;
            }

            JobScheduler scheduler = context.getSystemService(JobScheduler.class);
            if (scheduler == null) {
                LogUtil.e("Could not retrieve JobScheduler instance, so not cancelling jobs");
                return;
            }
            for (JobInfo jobInfo : scheduler.getAllPendingJobs()) {
                String jobClassName = jobInfo.getService().getClassName();
                // Cancel jobs from AdServices only
                if (jobClassName.startsWith(AdServicesCommon.ADSERVICES_CLASS_PATH_PREFIX)) {
                    int jobId = jobInfo.getId();
                    LogUtil.d("Deleting ext AdServices job %d %s", jobId, jobClassName);
                    scheduler.cancel(jobId);
                }
            }
            LogUtil.d("All AdServices scheduled jobs cancelled on package %s", packageName);
        } catch (Exception e) {
            LogUtil.e(e, "Error when cancelling scheduled jobs");
            e.printStackTrace();
        }
    }

    /**
     * Registers a receiver for any broadcasts regarding changes to any packages for all users on
     * the device at boot up. After receiving the broadcast, send an explicit broadcast to the
     * AdServices module as that user.
     */
    @VisibleForTesting
    void registerPackagedChangedBroadcastReceivers(Context context) {
        PackageChangedReceiver.enableReceiver(context, FlagsFactory.getFlags());
        LogUtil.d(
                "Package changed broadcast receivers registered from package %s",
                context.getPackageName());
    }

    @VisibleForTesting
    void unregisterPackageChangedBroadcastReceivers(Context context) {
        PackageChangedReceiver.disableReceiver(context, FlagsFactory.getFlags());
        LogUtil.d(
                "Package change broadcast receivers unregistered from package %s",
                context.getPackageName());
    }

    /**
     * Activities for user consent and control are disabled by default. Only on S- devices, after
     * the flag is enabled, we enable the activities.
     */
    @VisibleForTesting
    void updateAdExtServicesActivities(@NonNull Context context, boolean shouldEnable) {
        Objects.requireNonNull(context);

        try {
            String packageName = context.getPackageName();
            updateComponents(
                    context,
                    PackageManagerCompatUtils.CONSENT_ACTIVITIES_CLASSES,
                    packageName,
                    shouldEnable);
            LogUtil.d("Updated state of AdExtServices activities: [enabled=" + shouldEnable + "]");
        } catch (Exception e) {
            LogUtil.e("Error when updating activities: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Disables services with intent filters defined in AdExtServicesManifest to avoid dupes on T+
     * devices, or enables the same services on S to make sure they are re-enabled after OTA from R.
     */
    @VisibleForTesting
    void updateAdExtServicesServices(@NonNull Context context, boolean shouldEnable) {
        Objects.requireNonNull(context);

        try {
            String packageName = context.getPackageName();
            updateComponents(
                    context, PackageManagerCompatUtils.SERVICE_CLASSES, packageName, shouldEnable);
            LogUtil.d("Updated state of AdExtServices services: [enable=" + shouldEnable + "]");
        } catch (Exception e) {
            LogUtil.e("Error when updating services: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @VisibleForTesting
    static void updateComponents(
            @NonNull Context context,
            @NonNull List<String> components,
            @NonNull String adServicesPackageName,
            boolean shouldEnable) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(components);
        Objects.requireNonNull(adServicesPackageName);
        if (adServicesPackageName.endsWith(AdServicesCommon.ADSERVICES_APK_PACKAGE_NAME_SUFFIX)) {
            throw new IllegalStateException(
                    "Components for package with AdServices APK package suffix should not be "
                            + "updated!");
        }

        PackageManager packageManager = context.getPackageManager();
        for (String component : components) {
            packageManager.setComponentEnabledSetting(
                    new ComponentName(adServicesPackageName, component),
                    shouldEnable
                            ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        }
    }
}
