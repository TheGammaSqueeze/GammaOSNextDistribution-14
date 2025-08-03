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

package com.android.adservices.common;

import android.annotation.NonNull;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.SystemProperties;
import android.util.Log;

import com.android.compatibility.common.util.ShellUtils;
import com.android.modules.utils.build.SdkLevel;

import java.util.List;

/** Class to place Adservices CTS related helper method. */
public final class AdservicesTestHelper {
    // Used to get the package name. Copied over from com.android.adservices.AdServicesCommon
    private static final String TOPICS_SERVICE_NAME = "android.adservices.TOPICS_SERVICE";
    private static final String DEFAULT_LOG_TAG = "adservices";
    private static final String FORCE_KILL_PROCESS_COMMAND = "am force-stop";
    // Used to differentiate between AdServices APK package name and AdExtServices APK package name.
    private static final String ADSERVICES_APK_PACKAGE_NAME_SUFFIX = "android.adservices.api";

    /**
     * Used to get the package name. Copied over from com.android.adservices.AndroidServiceBinder
     *
     * @param context the context
     * @param logTag the tag used for logging
     * @return Adservices package name
     */
    public static String getAdServicesPackageName(
            @NonNull Context context, @NonNull String logTag) {
        final Intent intent = new Intent(TOPICS_SERVICE_NAME);
        final List<ResolveInfo> resolveInfos =
                context.getPackageManager()
                        .queryIntentServices(intent, PackageManager.MATCH_SYSTEM_ONLY);
        final ServiceInfo serviceInfo =
                resolveAdServicesService(resolveInfos, TOPICS_SERVICE_NAME, logTag);
        if (serviceInfo == null) {
            Log.e(logTag, "Failed to find serviceInfo for adServices service");
            return null;
        }

        return serviceInfo.packageName;
    }

    /**
     * Used to get the package name. An overloading method of {@code
     * getAdservicesPackageName(context, logTag)} by using {@code DEFAULT_LOG_TAG}.
     *
     * @param context the context
     * @return Adservices package name
     */
    public static String getAdServicesPackageName(@NonNull Context context) {
        return getAdServicesPackageName(context, DEFAULT_LOG_TAG);
    }

    /**
     * Kill the Adservices process.
     *
     * @param context the context used to get Adservices package name.
     * @param logTag the tag used for logging
     */
    public static void killAdservicesProcess(@NonNull Context context, @NonNull String logTag) {
        ShellUtils.runShellCommand(
                "%s %s", FORCE_KILL_PROCESS_COMMAND, getAdServicesPackageName(context, logTag));

        try {
            // Sleep 100 ms to allow AdServices process to recover
            Thread.sleep(/* millis= */ 100);
        } catch (InterruptedException ignored) {
            Log.e(logTag, "Recovery from restarting AdServices process interrupted", ignored);
        }
    }

    /**
     * Kill the Adservices process. An overloading method of {@code killAdservicesProcess(context,
     * logTag)} by using {@code DEFAULT_LOG_TAG}.
     *
     * @param context the context used to get Adservices package name.
     */
    public static void killAdservicesProcess(@NonNull Context context) {
        killAdservicesProcess(context, DEFAULT_LOG_TAG);
    }

    /**
     * Kill the Adservices process. An overloading method of {@code killAdservicesProcess(context,
     * logTag)} by using Adservices package name directly.
     *
     * @param adservicesPackageName the Adservices package name.
     */
    public static void killAdservicesProcess(@NonNull String adservicesPackageName) {
        ShellUtils.runShellCommand("%s %s", FORCE_KILL_PROCESS_COMMAND, adservicesPackageName);
    }

    /**
     * Check whether the device is supported. Adservices doesn't support non-phone device.
     *
     * @return if the device is supported.
     * @deprecated use {@link AdServicesDeviceSupportedRule} instead.
     */
    @Deprecated
    @SuppressWarnings("InlineMeSuggester")
    public static boolean isDeviceSupported() {
        return AdServicesSupportHelper.getInstance().isDeviceSupported();
    }

    /**
     * Checks if the device is debuggable, as the {@code Build.isDebuggable()} was just added on
     * Android S.
     */
    public static boolean isDebuggable() {
        if (SdkLevel.isAtLeastS()) {
            return Build.isDebuggable();
        }
        return SystemProperties.getInt("ro.debuggable", 0) == 1;
    }

    /**
     * Resolve package name of the active AdServices APK on this device.
     *
     * <p>Copied from AdServicesCommon.
     */
    private static ServiceInfo resolveAdServicesService(
            List<ResolveInfo> intentResolveInfos, String intentAction, String logTag) {
        if (intentResolveInfos == null || intentResolveInfos.isEmpty()) {
            Log.e(
                    logTag,
                    "Failed to find resolveInfo for adServices service. Intent action: "
                            + intentAction);
            return null;
        }

        // On T+ devices, we may have two versions of the services present due to b/263904312.
        if (intentResolveInfos.size() > 2) {
            StringBuilder intents = new StringBuilder("");
            for (ResolveInfo intentResolveInfo : intentResolveInfos) {
                if (intentResolveInfo != null && intentResolveInfo.serviceInfo != null) {
                    intents.append(intentResolveInfo.serviceInfo.packageName);
                }
            }
            Log.e(logTag, "Found multiple services " + intents + " for " + intentAction);
            return null;
        }

        // On T+ devices, only use the service that comes from AdServices APK. The package name of
        // AdService is com.[google.]android.adservices.api while the package name of ExtServices
        // APK is com.[google.]android.ext.services.
        ServiceInfo serviceInfo = null;

        // We have already checked if there are 0 OR more than 2 services returned.
        switch (intentResolveInfos.size()) {
            case 2:
                // In the case of 2, always use the one from AdServicesApk.
                if (intentResolveInfos.get(0) != null
                        && intentResolveInfos.get(0).serviceInfo != null
                        && intentResolveInfos.get(0).serviceInfo.packageName != null
                        && intentResolveInfos
                                .get(0)
                                .serviceInfo
                                .packageName
                                .endsWith(ADSERVICES_APK_PACKAGE_NAME_SUFFIX)) {
                    serviceInfo = intentResolveInfos.get(0).serviceInfo;
                } else if (intentResolveInfos.get(1) != null
                        && intentResolveInfos.get(1).serviceInfo != null
                        && intentResolveInfos.get(1).serviceInfo.packageName != null
                        && intentResolveInfos
                                .get(1)
                                .serviceInfo
                                .packageName
                                .endsWith(ADSERVICES_APK_PACKAGE_NAME_SUFFIX)) {
                    serviceInfo = intentResolveInfos.get(1).serviceInfo;
                }
                break;

            case 1:
                serviceInfo = intentResolveInfos.get(0).serviceInfo;
                break;
        }
        return serviceInfo;
    }
}
