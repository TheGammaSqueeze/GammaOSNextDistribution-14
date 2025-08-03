/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.uwb;

import android.annotation.NonNull;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Wrapper for context to override getResources method. Resources for uwb mainline jar needs to be
 * fetched from the resources APK.
 */
public class UwbContext extends ContextWrapper {
    private static final String TAG = "UwbContext";
    /** Intent action that is used to identify ServiceUwbResources.apk */
    private static final String ACTION_RESOURCES_APK =
            "com.android.server.uwb.intent.action.SERVICE_UWB_RESOURCES_APK";

    /** Since service-uwb runs within system_server, its package name is "android". */
    private static final String SERVICE_UWB_PACKAGE_NAME = "android";

    private String mUwbOverlayApkPkgName;

    // Cached resources from the resources APK.
    private AssetManager mUwbAssetsFromApk;
    private Resources mUwbResourcesFromApk;
    private Resources.Theme mUwbThemeFromApk;

    public UwbContext(@NonNull Context contextBase) {
        super(contextBase);
    }

    /** Get the package name of ServiceUwbResources.apk */
    public String getUwbOverlayApkPkgName() {
        if (mUwbOverlayApkPkgName != null) {
            return mUwbOverlayApkPkgName;
        }

        List<ResolveInfo> resolveInfos = getPackageManager().queryIntentActivities(
                new Intent(ACTION_RESOURCES_APK),
                PackageManager.MATCH_SYSTEM_ONLY | PackageManager.MATCH_DISABLED_COMPONENTS);

        // remove apps that don't live in the Uwb apex
        resolveInfos.removeIf(info ->
                !UwbInjector.isAppInUwbApex(info.activityInfo.applicationInfo));

        if (resolveInfos.isEmpty()) {
            // Resource APK not loaded yet, print a stack trace to see where this is called from
            Log.e(TAG, "Attempted to fetch resources before Uwb Resources APK is loaded!",
                    new IllegalStateException());
            return null;
        }

        if (resolveInfos.size() > 1) {
            // multiple apps found, log a warning, but continue
            Log.w(TAG, "Found > 1 APK that can resolve Uwb Resources APK intent: "
                    + resolveInfos.stream()
                            .map(info -> info.activityInfo.applicationInfo.packageName)
                            .collect(Collectors.joining(", ")));
        }

        // Assume the first ResolveInfo is the one we're looking for
        ResolveInfo info = resolveInfos.get(0);
        mUwbOverlayApkPkgName = info.activityInfo.applicationInfo.packageName;
        Log.i(TAG, "Found Uwb Resources APK at: " + mUwbOverlayApkPkgName);
        int enabledState = getPackageManager().getApplicationEnabledSetting(mUwbOverlayApkPkgName);
        if (enabledState != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            Log.w(TAG, "Uwb resources APK disabled, state: " + enabledState
                    + ". Trying to re-enable");
            getPackageManager().setApplicationEnabledSetting(
                    mUwbOverlayApkPkgName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0);
        }
        return mUwbOverlayApkPkgName;
    }

    private Context getResourcesApkContext() {
        try {
            return createPackageContext(getUwbOverlayApkPkgName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.wtf(TAG, "Failed to load resources", e);
        }
        return null;
    }

    /**
     * Retrieve assets held in the uwb resources APK.
     */
    @Override
    public AssetManager getAssets() {
        if (mUwbAssetsFromApk == null) {
            Context resourcesApkContext = getResourcesApkContext();
            if (resourcesApkContext != null) {
                mUwbAssetsFromApk = resourcesApkContext.getAssets();
            }
        }
        return mUwbAssetsFromApk;
    }

    /**
     * Retrieve resources held in the uwb resources APK.
     */
    @Override
    public Resources getResources() {
        if (mUwbResourcesFromApk == null) {
            Context resourcesApkContext = getResourcesApkContext();
            if (resourcesApkContext != null) {
                mUwbResourcesFromApk = resourcesApkContext.getResources();
            }
        }
        return mUwbResourcesFromApk;
    }

    /**
     * Retrieve theme held in the uwb resources APK.
     */
    @Override
    public Resources.Theme getTheme() {
        if (mUwbThemeFromApk == null) {
            Context resourcesApkContext = getResourcesApkContext();
            if (resourcesApkContext != null) {
                mUwbThemeFromApk = resourcesApkContext.getTheme();
            }
        }
        return mUwbThemeFromApk;
    }

    /** Get the package name that service-uwb runs under. */
    public String getServiceUwbPackageName() {
        return SERVICE_UWB_PACKAGE_NAME;
    }
}
