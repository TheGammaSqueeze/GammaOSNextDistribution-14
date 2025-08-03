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

package com.android.server.healthconnect.migration.notification;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Icon;
import android.health.connect.Constants;
import android.util.Slog;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.io.File;
import java.util.List;

/**
 * Utility context that fetches (string) resources from the Health Connect APK.
 *
 * @hide
 */
public final class HealthConnectResourcesContext extends ContextWrapper {
    private static final String TAG = "HealthConnectResContext";

    private static final String RESOURCES_APK_ACTION =
            "android.health.connect.action.HEALTH_HOME_SETTINGS";

    private static final String APEX_MODULE_NAME = "com.android.healthfitness";

    private static final String APEX_MODULE_PATH =
            new File("/apex", APEX_MODULE_NAME).getAbsolutePath();

    /** Intent action that is used to identify the Health Connect resources APK */
    private final String mResourcesApkAction;

    /** The path where the Health Connect resources APK is expected to be installed */
    @Nullable private final String mResourcesApkPath;

    /** Specific flags used for retrieving resolve info */
    private final int mFlags;

    // Cached package name and resources from the resources APK
    @Nullable private String mResourcesApkPkgName;
    @Nullable private Resources mResourcesFromApk;
    // The AOSP package name needed for loading the resources
    @Nullable private String mResourceLoadPackageName;

    public HealthConnectResourcesContext(@NonNull Context base) {
        this(base, RESOURCES_APK_ACTION, APEX_MODULE_PATH, PackageManager.MATCH_SYSTEM_ONLY);
    }

    HealthConnectResourcesContext(
            @NonNull Context base,
            @Nullable String resourcesApkAction,
            @Nullable String resourcesApkPath,
            int flags) {
        super(base);
        mResourcesApkAction = requireNonNull(resourcesApkAction);
        mResourcesApkPath = resourcesApkPath;
        mFlags = flags;

        initialisePackageNames();
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    private void initialisePackageNames() {
        ResolveInfo info = resolvePackageInfo();

        if (info != null) {
            mResourcesApkPkgName = info.activityInfo.applicationInfo.packageName;
            int iconResource = info.activityInfo.getIconResource();
            mResourceLoadPackageName = getResources().getResourcePackageName(iconResource);
        }
    }

    @Nullable
    private ResolveInfo resolvePackageInfo() {
        List<ResolveInfo> resolveInfos =
                getPackageManager().queryIntentActivities(new Intent(mResourcesApkAction), mFlags);

        if (resolveInfos.size() > 1) {
            // multiple apps found, log a warning, but continue
            if (Constants.DEBUG) {
                Slog.w(TAG, "Found > 1 APK that can resolve Health Connect APK intent:");
                for (ResolveInfo resolveInfo : resolveInfos) {
                    Slog.w(
                            TAG,
                            String.format(
                                    "- pkg:%s at:%s",
                                    resolveInfo.activityInfo.applicationInfo.packageName,
                                    resolveInfo.activityInfo.applicationInfo.sourceDir));
                }
            }
        }

        ResolveInfo info = null;
        for (ResolveInfo resolveInfo : resolveInfos) {
            if (mResourcesApkPath != null
                    && resolveInfo.activityInfo.applicationInfo.sourceDir.startsWith(
                            mResourcesApkPath)) {
                // apps that don't live in the HealthFitness apex will be skipped
                info = resolveInfo;
                break;
            }
        }

        if (info == null) {
            // Resource APK not loaded yet, print a stack trace to see where this is called from
            Slog.e(
                    TAG,
                    "Attempted to fetch resources before Health Connect resources APK is loaded!",
                    new IllegalStateException());
            return null;
        }

        return info;
    }

    @Nullable
    private String getResourcesApkPkgName() {
        if (mResourcesApkPkgName == null) {
            initialisePackageNames();
        }
        return mResourcesApkPkgName;
    }

    @Nullable
    private String getResourceLoadPackageName() {
        if (mResourceLoadPackageName == null) {
            initialisePackageNames();
        }
        return mResourceLoadPackageName;
    }

    @Nullable
    private Context getResourcesApkContext() {

        String name = getResourcesApkPkgName();
        if (name == null) {
            return null;
        }
        try {
            return createPackageContext(name, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Slog.wtf(TAG, "Failed to load resources", e);
        }
        return null;
    }

    /** Retrieve the Resources object held in the Health Connect resources APK. */
    @Nullable
    @Override
    public Resources getResources() {
        if (mResourcesFromApk == null) {
            Context resourcesApkContext = getResourcesApkContext();
            if (resourcesApkContext != null) {
                mResourcesFromApk = resourcesApkContext.getResources();
            }
        }
        return mResourcesFromApk;
    }

    /** Returns a string by its resource name. */
    @Nullable
    public String getStringByName(@NonNull String name) {
        int id = getStringRes(name);
        return getOptionalString(id);
    }

    /** Returns a string by its resource name formatted with supplied arguments */
    @Nullable
    public String getStringByNameWithArgs(@NonNull String name, Object... formatArgs) {
        int id = getStringRes(name);
        return getOptionalStringWithArgs(id, formatArgs);
    }

    @NonNull
    @StringRes
    private int getStringRes(@NonNull String name) {
        String resourceApkPkgName = getResourcesApkPkgName();
        String resourcePkgName = getResourceLoadPackageName();
        if (resourceApkPkgName == null) {
            return Resources.ID_NULL;
        }

        Resources resources = getResources();
        if (resources == null) {
            return Resources.ID_NULL;
        }

        return resources.getIdentifier(name, "string", resourcePkgName);
    }

    @Nullable
    private String getOptionalString(@StringRes int stringId) {
        if (stringId == Resources.ID_NULL) {
            return null;
        }

        return getString(stringId);
    }

    @Nullable
    private String getOptionalStringWithArgs(@StringRes int stringId, Object... formatArgs) {
        if (stringId == Resources.ID_NULL) {
            return null;
        }

        return getString(stringId, formatArgs);
    }

    /** Returns an Icon by its drawable resource name. */
    @Nullable
    public Icon getIconByDrawableName(@NonNull String drawableResName) {
        String resourceApkPkgName = getResourcesApkPkgName();
        String resourcePkgName = getResourceLoadPackageName();
        if (resourceApkPkgName == null) {
            return null;
        }

        Resources resources = getResources();
        if (resources == null) {
            return null;
        }

        int resId = resources.getIdentifier(drawableResName, "drawable", resourcePkgName);
        if (resId != Resources.ID_NULL) {
            return Icon.createWithResource(resourceApkPkgName, resId);
        }

        if (Constants.DEBUG) {
            Slog.w(TAG, "Drawable resource " + drawableResName + " not found");
        }
        return null;
    }
}
