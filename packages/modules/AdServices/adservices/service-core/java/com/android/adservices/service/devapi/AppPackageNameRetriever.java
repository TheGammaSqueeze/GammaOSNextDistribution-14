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

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;

import com.android.adservices.LogUtil;
import com.android.adservices.service.common.SdkRuntimeUtil;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/** Abstracts the logic to determine the ID of the calling App. */
public class AppPackageNameRetriever {
    private final PackageManager mPackageManager;

    /** Creates an instance of {@link AppPackageNameRetriever} */
    public static AppPackageNameRetriever create(@NonNull Context context) {
        Objects.requireNonNull(context);
        return new AppPackageNameRetriever(context.getPackageManager());
    }

    @VisibleForTesting
    AppPackageNameRetriever(@NonNull PackageManager packageManager) {
        Objects.requireNonNull(packageManager);

        mPackageManager = packageManager;
    }

    /**
     * @param appUid The UUID of the app, for example the ID of the app calling a given API
     *     retrieved using {@code Binder.getCallingUid()} which has to be then processed using
     *     {@link SdkRuntimeUtil#getCallingAppUid(int)} to take care of the fact that the caller
     *     could be the SDK Sandbox.
     * @return the AppID (package name) for the application associated to the given UID In the rare
     *     case that there are multiple apps associated to the same UID the first one returned by
     *     the OS is returned.
     * @throws IllegalArgumentException if the system cannot find any app package for the given UID.
     */
    public String getAppPackageNameForUid(int appUid) throws IllegalArgumentException {
        // We could have more than one package name for the same UID if the UID is shared by
        // different apps. This is a rare case and we are going to use the ID of the first one.
        // See https://yaqs.corp.google.com/eng/q/4727253374861312#a5649050225344512
        String[] possibleAppPackages = mPackageManager.getPackagesForUid(appUid);
        if (possibleAppPackages == null || possibleAppPackages.length == 0) {
            throw new IllegalArgumentException(
                    "Unable to retrieve a package name for caller UID " + appUid);
        }
        if (possibleAppPackages.length > 1) {
            LogUtil.w(
                    "More than one package name available for UID %d, returning package "
                            + "name %s",
                    possibleAppPackages.length, possibleAppPackages[0]);
        }
        return possibleAppPackages[0];
    }
}
