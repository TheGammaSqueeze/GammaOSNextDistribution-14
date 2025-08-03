/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.sdksandbox.helpers;

import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.SharedLibraryInfo;
import android.os.UserHandle;
import android.util.Log;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Helper class for operations involving {@link PackageManager}
 *
 * @hide
 */
public class PackageManagerHelper {
    private static final String TAG = "SdkSandboxManager";
    private final PackageManager mPackageManager;

    public PackageManagerHelper(Context context, int callingUid) {
        UserHandle userHandle = UserHandle.getUserHandleForUid(callingUid);
        Context userContext = context.createContextAsUser(userHandle, /* flags= */ 0);
        mPackageManager = userContext.getPackageManager();
    }

    /** Returns {@link SharedLibraryInfo} object for the SDK belonging to the package */
    @NonNull
    public SharedLibraryInfo getSdkSharedLibraryInfoForSdk(
            @NonNull String packageName, @NonNull String sdkName)
            throws PackageManager.NameNotFoundException {
        List<SharedLibraryInfo> sharedLibraryInfos = getSdkSharedLibraryInfo(packageName);
        sharedLibraryInfos =
                sharedLibraryInfos.stream()
                        .filter(sharedLibraryInfo -> sharedLibraryInfo.getName().equals(sdkName))
                        .collect(Collectors.toList());

        if (sharedLibraryInfos.size() == 0) {
            throw new PackageManager.NameNotFoundException(sdkName);
        }
        return sharedLibraryInfos.get(0);
    }

    /**
     * Returns a list of {@link SharedLibraryInfo} object for all the SDKs belonging to the package
     */
    @NonNull
    public List<SharedLibraryInfo> getSdkSharedLibraryInfo(@NonNull String packageName)
            throws PackageManager.NameNotFoundException {
        ApplicationInfo info =
                mPackageManager.getApplicationInfo(
                        packageName,
                        PackageManager.ApplicationInfoFlags.of(
                                PackageManager.GET_SHARED_LIBRARY_FILES));

        return info.getSharedLibraryInfos().stream()
                .filter(
                        sharedLibraryInfo ->
                                (sharedLibraryInfo.getType() == SharedLibraryInfo.TYPE_SDK_PACKAGE))
                .collect(Collectors.toList());
    }

    /** Returns {@link PackageManager.Property} object of the propertyName for the package */
    @NonNull
    public PackageManager.Property getProperty(
            @NonNull String propertyName, @NonNull String packageName)
            throws PackageManager.NameNotFoundException {
        return mPackageManager.getProperty(propertyName, packageName);
    }

    /** Returns {@link ApplicationInfo} for the {@link SharedLibraryInfo} with certain flags */
    @NonNull
    public ApplicationInfo getApplicationInfoForSharedLibrary(
            @NonNull SharedLibraryInfo sharedLibrary, int flags)
            throws PackageManager.NameNotFoundException {
        return mPackageManager.getPackageInfo(sharedLibrary.getDeclaringPackage(), flags)
                .applicationInfo;
    }

    /** Returns the packageNames for the UID */
    @NonNull
    public List<String> getPackageNamesForUid(int callingUid)
            throws PackageManager.NameNotFoundException {
        String[] possibleAppPackages = mPackageManager.getPackagesForUid(callingUid);

        if (possibleAppPackages == null || possibleAppPackages.length == 0) {
            throw new PackageManager.NameNotFoundException(
                    "Could not find package for " + callingUid);
        }
        if (possibleAppPackages.length > 1) {
            Log.d(
                    TAG,
                    "More than one package name available for UID "
                            + callingUid
                            + ". Count: "
                            + possibleAppPackages.length);
        }
        return List.of(possibleAppPackages);
    }
}
