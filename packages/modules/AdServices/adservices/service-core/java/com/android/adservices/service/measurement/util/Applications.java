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

package com.android.adservices.service.measurement.util;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import com.android.adservices.service.common.compat.PackageManagerCompatUtils;

import java.util.List;
import java.util.stream.Collectors;

/** Application utilities for measurement. */
public class Applications {
    public static final String ANDROID_APP_SCHEME = "android-app";
    public static final String SCHEME_DELIMITER = "://";

    /**
     * @param context the context of the application.
     * @return the list of currently installed applications.
     */
    public static List<Uri> getCurrentInstalledApplicationsList(Context context) {
        PackageManager packageManager = context.getPackageManager();
        List<ApplicationInfo> applicationInfoList =
                PackageManagerCompatUtils.getInstalledApplications(
                        packageManager, PackageManager.GET_META_DATA);

        return applicationInfoList.stream()
                .map(
                        applicationInfo ->
                                Uri.parse(
                                        ANDROID_APP_SCHEME
                                                + SCHEME_DELIMITER
                                                + applicationInfo.packageName))
                .collect(Collectors.toList());
    }

    /**
     * @param context the context of the application.
     * @param appDestinations the list of apps to check install status.
     * @return true if any of the apps are installed currently.
     */
    public static boolean anyAppsInstalled(Context context, List<Uri> appDestinations) {
        return appDestinations.stream()
                .anyMatch(
                        uri -> {
                            try {
                                context.getPackageManager().getApplicationInfo(uri.getHost(), 0);
                                return true;
                            } catch (PackageManager.NameNotFoundException e) {
                                return false;
                            }
                        });
    }
}
