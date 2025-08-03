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

package com.android.server.sdksandbox;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.SharedLibraryInfo;
import android.os.Build;
import android.util.ArrayMap;

import com.android.internal.annotations.GuardedBy;
import com.android.server.sdksandbox.helpers.PackageManagerHelper;

import java.util.List;

/**
 * Class for handling restrictions
 *
 * @hide
 */
// TODO(b/308607306): Extract restrictions related code from {@link SdkSandboxManagerService} to
// this class
class SdkSandboxRestrictionManager {

    private static final String TAG = "SdkSandboxManager";
    private static final int DEFAULT_TARGET_SDK_VERSION = Build.VERSION_CODES.UPSIDE_DOWN_CAKE;

    private final Object mLock = new Object();
    private final Injector mInjector;

    // The key will be the client's {@link CallingInfo}
    @GuardedBy("mLock")
    private final ArrayMap<CallingInfo, Integer> mEffectiveTargetSdkVersions = new ArrayMap<>();

    SdkSandboxRestrictionManager(Context context) {
        this(new Injector(context));
    }

    SdkSandboxRestrictionManager(Injector injector) {
        mInjector = injector;
    }

    static class Injector {
        private final Context mContext;

        Injector(Context context) {
            mContext = context;
        }

        PackageManagerHelper getPackageManagerHelper(int callingUid) {
            return new PackageManagerHelper(mContext, callingUid);
        }

        int getCurrentSdkLevel() {
            return Build.VERSION.SDK_INT;
        }
    }

    /** Cache and get the effectiveTargetSdkVersion for the sdk sandbox process */
    public int getEffectiveTargetSdkVersion(int appUid)
            throws PackageManager.NameNotFoundException {
        PackageManagerHelper packageManagerHelper = mInjector.getPackageManagerHelper(appUid);
        List<String> packageNames = packageManagerHelper.getPackageNamesForUid(appUid);

        // Initializing the effectiveTargetSdkVersion as the current SDK level
        int effectiveTargetSdkVersion = mInjector.getCurrentSdkLevel();

        for (String packageName : packageNames) {
            int effectiveTargetSdkVersionForPackage =
                    getEffectiveTargetSdkVersion(
                            new CallingInfo(appUid, packageName), packageManagerHelper);
            if (effectiveTargetSdkVersionForPackage == DEFAULT_TARGET_SDK_VERSION) {
                return effectiveTargetSdkVersionForPackage;
            }
            effectiveTargetSdkVersion =
                    Integer.min(effectiveTargetSdkVersion, effectiveTargetSdkVersionForPackage);
        }

        return effectiveTargetSdkVersion;
    }

    private int getEffectiveTargetSdkVersion(
            CallingInfo callingInfo, PackageManagerHelper packageManagerHelper)
            throws PackageManager.NameNotFoundException {
        // If the device's SDK version is equal to the default value, we can return it immediately.
        if (mInjector.getCurrentSdkLevel() <= DEFAULT_TARGET_SDK_VERSION) {
            synchronized (mLock) {
                mEffectiveTargetSdkVersions.put(callingInfo, DEFAULT_TARGET_SDK_VERSION);
                return mEffectiveTargetSdkVersions.get(callingInfo);
            }
        }

        List<SharedLibraryInfo> sharedLibraries =
                packageManagerHelper.getSdkSharedLibraryInfo(callingInfo.getPackageName());

        int targetSdkVersion = mInjector.getCurrentSdkLevel();
        for (int i = 0; i < sharedLibraries.size(); i++) {
            ApplicationInfo applicationInfo =
                    packageManagerHelper.getApplicationInfoForSharedLibrary(
                            sharedLibraries.get(i),
                            PackageManager.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES
                                    | PackageManager.MATCH_ANY_USER);

            targetSdkVersion = Integer.min(targetSdkVersion, applicationInfo.targetSdkVersion);
            // If the targetSdkVersion of an SDK is equal to the default value, we can return it
            // immediately.
            if (targetSdkVersion == DEFAULT_TARGET_SDK_VERSION) {
                break;
            }
        }
        // Return defaultTargetSdkVersion if the calculated targetSdkVersion is less than the
        // defaultTargetSdkVersion because restrictions logic starts from UPSIDE_DOWN_CAKE
        synchronized (mLock) {
            mEffectiveTargetSdkVersions.put(
                    callingInfo, Integer.max(targetSdkVersion, DEFAULT_TARGET_SDK_VERSION));
            return mEffectiveTargetSdkVersions.get(callingInfo);
        }
    }
}
