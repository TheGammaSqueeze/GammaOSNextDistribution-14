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

package com.android.adservices.service.devapi;

import android.annotation.NonNull;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.provider.Settings;

import com.android.adservices.LogUtil;
import com.android.adservices.service.common.SdkRuntimeUtil;
import com.android.adservices.service.common.compat.BuildCompatUtils;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/**
 * Creates a {@link DevContext} instance using the information related to the caller of the current
 * API.
 */
public class DevContextFilter {
    private final ContentResolver mContentResolver;
    private final AppPackageNameRetriever mAppPackageNameRetriever;
    private final PackageManager mPackageManager;

    @VisibleForTesting
    DevContextFilter(
            @NonNull ContentResolver contentResolver,
            @NonNull PackageManager packageManager,
            @NonNull AppPackageNameRetriever appPackageNameRetriever) {
        Objects.requireNonNull(contentResolver);
        Objects.requireNonNull(packageManager);
        Objects.requireNonNull(appPackageNameRetriever);

        mAppPackageNameRetriever = appPackageNameRetriever;
        mContentResolver = contentResolver;
        mPackageManager = packageManager;
    }

    /** Creates an instance of {@link DevContextFilter}. */
    public static DevContextFilter create(@NonNull Context context) {
        Objects.requireNonNull(context);

        return new DevContextFilter(
                context.getContentResolver(),
                context.getPackageManager(),
                AppPackageNameRetriever.create(context));
    }

    /**
     * Creates a {@link DevContext} for the current binder call. It is assumed to be called by APIs
     * after having collected the caller UID in the API thread..
     *
     * @return A dev context specifying if the developer options are enabled for this API call or a
     *     context with developer options disabled if there is any error retrieving info for the
     *     calling application.
     * @throws IllegalStateException if the current thread is not currently executing an incoming
     *     transaction.
     */
    public DevContext createDevContext() throws IllegalStateException {
        return createDevContextFromCallingUid(Binder.getCallingUidOrThrow());
    }

    /**
     * Creates a {@link DevContext} for a given Binder calling UID.
     *
     * @param callingUid The Binder calling UID.
     * @return A dev context specifying if the developer options are enabled for this API call or a
     *     context with developer options disabled if there is any error retrieving info for the
     *     calling application.
     */
    public DevContext createDevContextFromCallingUid(int callingUid) {
        return createDevContext(SdkRuntimeUtil.getCallingAppUid(callingUid));
    }

    /**
     * Creates a {@link DevContext} for a given app UID.
     *
     * @param callingAppUid The UID of the caller APP.
     * @return A dev context specifying if the developer options are enabled for this API call or a
     *     context with developer options disabled if there is any error retrieving info for the
     *     calling application.
     */
    @VisibleForTesting
    public DevContext createDevContext(int callingAppUid) {
        if (!isDeveloperMode()) {
            LogUtil.v("Developer mode is disabled, creating dev context as disabled");
            return DevContext.createForDevOptionsDisabled();
        }

        try {
            String callingAppPackage =
                    mAppPackageNameRetriever.getAppPackageNameForUid(callingAppUid);
            if (!isDebuggable(callingAppPackage)) {
                LogUtil.v("Not debuggable, creating dev context as disabled");
                return DevContext.createForDevOptionsDisabled();
            }

            LogUtil.v("Creating Dev Context for calling app with package " + callingAppPackage);
            return DevContext.builder()
                    .setDevOptionsEnabled(true)
                    .setCallingAppPackageName(callingAppPackage)
                    .build();
        } catch (IllegalArgumentException e) {
            LogUtil.w(
                    "Unable to retrieve the package name for UID %d. Creating a DevContext with "
                            + "developer options disabled.",
                    callingAppUid);
            return DevContext.createForDevOptionsDisabled();
        }
    }

    /**
     * Returns true if the callingAppPackage is debuggable and false if it is not or if {@code
     * callingAppPackage} is null.
     *
     * @param callingAppPackage the calling app package
     */
    @VisibleForTesting
    public boolean isDebuggable(String callingAppPackage) {
        if (Objects.isNull(callingAppPackage)) {
            return false;
        }
        try {
            ApplicationInfo applicationInfo =
                    PackageManagerCompatUtils.getApplicationInfo(
                            mPackageManager, callingAppPackage, 0);
            return (applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;

        } catch (PackageManager.NameNotFoundException e) {
            LogUtil.w(
                    "Unable to retrieve application info for app with ID %s and resolved package "
                            + "name '%s', considering not debuggable for safety.",
                    callingAppPackage, callingAppPackage);
            return false;
        }
    }

    /** Returns true if developer options are enabled. */
    @VisibleForTesting
    public boolean isDeveloperMode() {
        return BuildCompatUtils.isDebuggable()
                || Settings.Global.getInt(
                                mContentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
                        != 0;
    }
}
