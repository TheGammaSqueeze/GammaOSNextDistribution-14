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

package com.android.safetycenter.resources;

import static java.util.Objects.requireNonNull;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.util.Log;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.io.InputStream;
import java.util.List;

/**
 * A class to access Safety Center resources that need to be fetched from a dedicated APK.
 *
 * <p>You must check whether Safety Center is enabled or the value returned by {@link #init()} prior
 * to interacting with this class. Failure to do so may cause an {@link IllegalStateException} if
 * the resources APK cannot be accessed.
 *
 * <p>This class isn't thread safe. Thread safety must be handled by the caller, or this may cause
 * the resources APK {@link Context} to be initialized multiple times.
 */
public final class SafetyCenterResourcesApk {

    private static final String TAG = "SafetyCenterResApk";

    /** Intent action that is used to identify the Safety Center resources APK */
    private static final String RESOURCES_APK_ACTION =
            "com.android.safetycenter.intent.action.SAFETY_CENTER_RESOURCES_APK";

    /** Permission APEX name */
    private static final String APEX_MODULE_NAME = "com.android.permission";

    /**
     * The path where the Permission apex is mounted. Current value = "/apex/com.android.permission"
     */
    private static final String APEX_MODULE_PATH =
            new File("/apex", APEX_MODULE_NAME).getAbsolutePath();

    /** Raw XML config resource name */
    private static final String CONFIG_NAME = "safety_center_config";

    private final Context mContext;

    /** Intent action that is used to identify the Safety Center resources APK */
    private final String mResourcesApkAction;

    /** The path where the Safety Center resources APK is expected to be installed */
    private final String mResourcesApkPath;

    /** Specific flags used for retrieving resolve info. */
    private final int mFlags;

    /**
     * Whether we should fallback with an empty string / null values when calling the methods of
     * this class for a resource that does not exist.
     */
    private final boolean mShouldFallbackIfNamedResourceNotFound;

    // Cached context from the resources APK.
    @Nullable private Context mResourcesApkContext;

    public SafetyCenterResourcesApk(Context context) {
        this(context, /* shouldFallbackIfNamedResourceNotFound */ true);
    }

    private SafetyCenterResourcesApk(
            Context context, boolean shouldFallbackIfNamedResourceNotFound) {
        this(
                context,
                RESOURCES_APK_ACTION,
                APEX_MODULE_PATH,
                PackageManager.MATCH_SYSTEM_ONLY,
                shouldFallbackIfNamedResourceNotFound);
    }

    @VisibleForTesting
    SafetyCenterResourcesApk(
            Context context,
            String resourcesApkAction,
            String resourcesApkPath,
            int flags,
            boolean shouldFallbackIfNamedResourceNotFound) {
        mContext = requireNonNull(context);
        mResourcesApkAction = requireNonNull(resourcesApkAction);
        mResourcesApkPath = requireNonNull(resourcesApkPath);
        mFlags = flags;
        mShouldFallbackIfNamedResourceNotFound = shouldFallbackIfNamedResourceNotFound;
    }

    /** Creates a new {@link SafetyCenterResourcesApk} for testing. */
    @VisibleForTesting
    public static SafetyCenterResourcesApk forTests(Context context) {
        return new SafetyCenterResourcesApk(
                context, /* shouldFallbackIfNamedResourceNotFound */ false);
    }

    /**
     * Initializes the resources APK {@link Context}, and returns whether this was successful.
     *
     * <p>This call is optional as this can also be lazily instantiated. It can be used to ensure
     * that the resources APK context is loaded prior to interacting with this class. This
     * initialization code needs to run in the same user as the provided base {@link Context}. This
     * may not be the case with a binder call, which is why it can be more appropriate to do this
     * explicitly.
     */
    public boolean init() {
        mResourcesApkContext = loadResourcesApkContext();
        return mResourcesApkContext != null;
    }

    /**
     * Returns the {@link Context} of the Safety Center resources APK.
     *
     * <p>Throws an {@link IllegalStateException} if the resources APK is not available
     */
    public Context getContext() {
        if (mResourcesApkContext != null) {
            return mResourcesApkContext;
        }

        mResourcesApkContext = loadResourcesApkContext();
        if (mResourcesApkContext == null) {
            throw new IllegalStateException("Resources APK context not found");
        }

        return mResourcesApkContext;
    }

    @Nullable
    private Context loadResourcesApkContext() {
        List<ResolveInfo> resolveInfos =
                mContext.getPackageManager()
                        .queryIntentActivities(new Intent(mResourcesApkAction), mFlags);

        if (resolveInfos.size() > 1) {
            // multiple apps found, log a warning, but continue
            Log.w(TAG, "Found > 1 APK that can resolve Safety Center resources APK intent:");
            final int resolveInfosSize = resolveInfos.size();
            for (int i = 0; i < resolveInfosSize; i++) {
                ResolveInfo resolveInfo = resolveInfos.get(i);
                Log.w(
                        TAG,
                        String.format(
                                "- pkg:%s at:%s",
                                resolveInfo.activityInfo.applicationInfo.packageName,
                                resolveInfo.activityInfo.applicationInfo.sourceDir));
            }
        }

        ResolveInfo info = null;
        // Assume the first good ResolveInfo is the one we're looking for
        final int resolveInfosSize = resolveInfos.size();
        for (int i = 0; i < resolveInfosSize; i++) {
            ResolveInfo resolveInfo = resolveInfos.get(i);
            if (!resolveInfo.activityInfo.applicationInfo.sourceDir.startsWith(mResourcesApkPath)) {
                // skip apps that don't live in the Permission apex
                continue;
            }
            info = resolveInfo;
            break;
        }

        if (info == null) {
            // Resource APK not loaded yet, print a stack trace to see where this is called from
            Log.e(TAG, "Could not find Safety Center resources APK", new IllegalStateException());
            return null;
        }

        String resourcesApkPkgName = info.activityInfo.applicationInfo.packageName;
        Log.i(TAG, "Found Safety Center resources APK at: " + resourcesApkPkgName);
        return getPackageContext(resourcesApkPkgName);
    }

    @Nullable
    private Context getPackageContext(String packageName) {
        try {
            return mContext.createPackageContext(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to load package context for: " + packageName, e);
        }
        return null;
    }

    /** Calls {@link Context#getResources()} for the resources APK {@link Context}. */
    public Resources getResources() {
        return getContext().getResources();
    }

    /**
     * Returns the raw XML resource representing the Safety Center configuration file from the
     * Safety Center resources APK.
     */
    @Nullable
    public InputStream getSafetyCenterConfig() {
        return getSafetyCenterConfig(CONFIG_NAME);
    }

    @VisibleForTesting
    @Nullable
    InputStream getSafetyCenterConfig(String configName) {
        int resId = getResIdAndMaybeThrowIfNull(configName, "raw");
        if (resId == Resources.ID_NULL) {
            return null;
        }
        return getResources().openRawResource(resId);
    }

    /** Calls {@link Context#getString(int)} for the resources APK {@link Context}. */
    public String getString(@StringRes int stringId) {
        return getContext().getString(stringId);
    }

    /** Same as {@link #getString(int)} but with the given {@code formatArgs}. */
    public String getString(@StringRes int stringId, Object... formatArgs) {
        return getContext().getString(stringId, formatArgs);
    }

    /**
     * Returns the {@link String} with the given resource name.
     *
     * <p>If the {@link String} cannot be accessed, returns {@code ""} or throws {@link
     * Resources.NotFoundException} depending on {@link #mShouldFallbackIfNamedResourceNotFound}.
     */
    public String getStringByName(String name) {
        int resId = getResIdAndMaybeThrowIfNull(name, "string");
        if (resId == Resources.ID_NULL) {
            return "";
        }
        return getString(resId);
    }

    /** Same as {@link #getStringByName(String)} but with the given {@code formatArgs}. */
    public String getStringByName(String name, Object... formatArgs) {
        int resId = getResIdAndMaybeThrowIfNull(name, "string");
        if (resId == Resources.ID_NULL) {
            return "";
        }
        return getString(resId, formatArgs);
    }

    /**
     * Returns an optional {@link String} resource with the given {@code stringId}.
     *
     * <p>Returns {@code null} if {@code stringId} is equal to {@link Resources#ID_NULL}. Otherwise,
     * throws a {@link Resources.NotFoundException}.
     */
    @Nullable
    public String getOptionalString(@StringRes int stringId) {
        if (stringId == Resources.ID_NULL) {
            return null;
        }
        return getString(stringId);
    }

    /** Same as {@link #getOptionalString(int)} but with the given resource name rather than ID. */
    @Nullable
    public String getOptionalStringByName(String name) {
        return getOptionalString(getResId(name, "string"));
    }

    /**
     * Returns the {@link Drawable} with the given resource name.
     *
     * <p>If the {@link Drawable} cannot be accessed, returns {@code null} or throws {@link
     * Resources.NotFoundException} depending on {@link #mShouldFallbackIfNamedResourceNotFound}.
     *
     * @param theme the theme used to style the drawable attributes, may be {@code null}
     */
    @Nullable
    public Drawable getDrawableByName(String name, @Nullable Resources.Theme theme) {
        int resId = getResIdAndMaybeThrowIfNull(name, "drawable");
        if (resId == Resources.ID_NULL) {
            return null;
        }
        return getResources().getDrawable(resId, theme);
    }

    /**
     * Returns an {@link Icon} containing the {@link Drawable} with the given resource name.
     *
     * <p>If the {@link Drawable} cannot be accessed, returns {@code null} or throws {@link
     * Resources.NotFoundException} depending on {@link #mShouldFallbackIfNamedResourceNotFound}.
     */
    @Nullable
    public Icon getIconByDrawableName(String name) {
        int resId = getResIdAndMaybeThrowIfNull(name, "drawable");
        if (resId == Resources.ID_NULL) {
            return null;
        }
        return Icon.createWithResource(getContext().getPackageName(), resId);
    }

    /**
     * Returns the {@link ColorInt} with the given resource name.
     *
     * <p>If the {@link ColorInt} cannot be accessed, returns {@code null} or throws {@link
     * Resources.NotFoundException} depending on {@link #mShouldFallbackIfNamedResourceNotFound}.
     */
    @ColorInt
    @Nullable
    public Integer getColorByName(String name) {
        int resId = getResIdAndMaybeThrowIfNull(name, "color");
        if (resId == Resources.ID_NULL) {
            return null;
        }
        return getResources().getColor(resId, getContext().getTheme());
    }

    private int getResIdAndMaybeThrowIfNull(String name, String type) {
        int resId = getResId(name, type);
        if (resId != Resources.ID_NULL) {
            return resId;
        }
        if (!mShouldFallbackIfNamedResourceNotFound) {
            throw new Resources.NotFoundException();
        }
        Log.w(TAG, "Named " + type + " resource: " + name + " not found");
        return resId;
    }

    private int getResId(String name, String type) {
        // TODO(b/227738283): profile the performance of this operation and consider adding caching
        //  or finding some alternative solution.
        return getResources().getIdentifier(name, type, getContext().getPackageName());
    }
}
