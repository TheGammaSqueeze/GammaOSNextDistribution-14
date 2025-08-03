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

package com.android.intentresolver;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

/**
 * Loads the icon and label for the provided ApplicationInfo. Defaults to using the application icon
 * and label over any IntentFilter or Activity icon to increase user understanding, with an
 * exception for applications that hold the right permission. Always attempts to use available
 * resources over PackageManager loading mechanisms so badging can be done by iconloader. Uses
 * Strings to strip creative formatting.
 *
 * Use one of the {@link TargetPresentationGetter.Factory} methods to create an instance of the
 * appropriate concrete type.
 *
 * TODO: once this component (and its tests) are merged, it should be possible to refactor and
 * vastly simplify by precomputing conditional logic at initialization.
 */
public abstract class TargetPresentationGetter {
    private static final String TAG = "ResolverListAdapter";

    /** Helper to build appropriate type-specific {@link TargetPresentationGetter} instances. */
    public static class Factory {
        private final Context mContext;
        private final int mIconDpi;

        public Factory(Context context, int iconDpi) {
            mContext = context;
            mIconDpi = iconDpi;
        }

        /** Make a {@link TargetPresentationGetter} for an {@link ActivityInfo}. */
        public TargetPresentationGetter makePresentationGetter(ActivityInfo activityInfo) {
            return new ActivityInfoPresentationGetter(mContext, mIconDpi, activityInfo);
        }

        /** Make a {@link TargetPresentationGetter} for a {@link ResolveInfo}. */
        public TargetPresentationGetter makePresentationGetter(ResolveInfo resolveInfo) {
            return new ResolveInfoPresentationGetter(mContext, mIconDpi, resolveInfo);
        }
    }

    @Nullable
    protected abstract Drawable getIconSubstituteInternal();

    @Nullable
    protected abstract String getAppSubLabelInternal();

    @Nullable
    protected abstract String getAppLabelForSubstitutePermission();

    private Context mContext;
    private final int mIconDpi;
    private final boolean mHasSubstitutePermission;
    private final ApplicationInfo mAppInfo;

    protected PackageManager mPm;

    /**
     * Retrieve the image that should be displayed as the icon when this target is presented to the
     * specified {@code userHandle}.
     */
    public Drawable getIcon(UserHandle userHandle) {
        return new BitmapDrawable(mContext.getResources(), getIconBitmap(userHandle));
    }

    /**
     * Retrieve the image that should be displayed as the icon when this target is presented to the
     * specified {@code userHandle}.
     */
    public Bitmap getIconBitmap(@Nullable UserHandle userHandle) {
        Drawable drawable = null;
        if (mHasSubstitutePermission) {
            drawable = getIconSubstituteInternal();
        }

        if (drawable == null) {
            try {
                if (mAppInfo.icon != 0) {
                    drawable = loadIconFromResource(
                            mPm.getResourcesForApplication(mAppInfo), mAppInfo.icon);
                }
            } catch (PackageManager.NameNotFoundException ignore) { }
        }

        // Fall back to ApplicationInfo#loadIcon if nothing has been loaded
        if (drawable == null) {
            drawable = mAppInfo.loadIcon(mPm);
        }

        SimpleIconFactory iconFactory = SimpleIconFactory.obtain(mContext);
        Bitmap icon = iconFactory.createUserBadgedIconBitmap(drawable, userHandle);
        iconFactory.recycle();

        return icon;
    }

    /** Get the label to display for the target. */
    public String getLabel() {
        String label = null;
        // Apps with the substitute permission will always show the activity label as the app label
        // if provided.
        if (mHasSubstitutePermission) {
            label = getAppLabelForSubstitutePermission();
        }

        if (label == null) {
            label = (String) mAppInfo.loadLabel(mPm);
        }

        return label;
    }

    /**
     * Get the sublabel to display for the target. Clients are responsible for deduping their
     * presentation if this returns the same value as {@link #getLabel()}.
     * TODO: this class should take responsibility for that deduping internally so it's an
     * authoritative record of exactly the content that should be presented.
     */
    public String getSubLabel() {
        // Apps with the substitute permission will always show the resolve info label as the
        // sublabel if provided
        if (mHasSubstitutePermission) {
            String appSubLabel = getAppSubLabelInternal();
            // Use the resolve info label as sublabel if it is set
            if (!TextUtils.isEmpty(appSubLabel) && !TextUtils.equals(appSubLabel, getLabel())) {
                return appSubLabel;
            }
            return null;
        }
        return getAppSubLabelInternal();
    }

    protected String loadLabelFromResource(Resources res, int resId) {
        return res.getString(resId);
    }

    @Nullable
    protected Drawable loadIconFromResource(Resources res, int resId) {
        return res.getDrawableForDensity(resId, mIconDpi);
    }

    private TargetPresentationGetter(Context context, int iconDpi, ApplicationInfo appInfo) {
        mContext = context;
        mPm = context.getPackageManager();
        mAppInfo = appInfo;
        mIconDpi = iconDpi;
        mHasSubstitutePermission = (PackageManager.PERMISSION_GRANTED == mPm.checkPermission(
                android.Manifest.permission.SUBSTITUTE_SHARE_TARGET_APP_NAME_AND_ICON,
                mAppInfo.packageName));
    }

    /** Loads the icon and label for the provided ResolveInfo. */
    private static class ResolveInfoPresentationGetter extends ActivityInfoPresentationGetter {
        private final ResolveInfo mResolveInfo;

        ResolveInfoPresentationGetter(
                Context context, int iconDpi, ResolveInfo resolveInfo) {
            super(context, iconDpi, resolveInfo.activityInfo);
            mResolveInfo = resolveInfo;
        }

        @Override
        protected Drawable getIconSubstituteInternal() {
            Drawable drawable = null;
            try {
                // Do not use ResolveInfo#getIconResource() as it defaults to the app
                if (mResolveInfo.resolvePackageName != null && mResolveInfo.icon != 0) {
                    drawable = loadIconFromResource(
                            mPm.getResourcesForApplication(mResolveInfo.resolvePackageName),
                            mResolveInfo.icon);
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "SUBSTITUTE_SHARE_TARGET_APP_NAME_AND_ICON permission granted but "
                        + "couldn't find resources for package", e);
            }

            // Fall back to ActivityInfo if no icon is found via ResolveInfo
            if (drawable == null) {
                drawable = super.getIconSubstituteInternal();
            }

            return drawable;
        }

        @Override
        protected String getAppSubLabelInternal() {
            // Will default to app name if no intent filter or activity label set, make sure to
            // check if subLabel matches label before final display
            return mResolveInfo.loadLabel(mPm).toString();
        }

        @Override
        protected String getAppLabelForSubstitutePermission() {
            // Will default to app name if no activity label set
            return mResolveInfo.getComponentInfo().loadLabel(mPm).toString();
        }
    }

    /** Loads the icon and label for the provided {@link ActivityInfo}. */
    private static class ActivityInfoPresentationGetter extends TargetPresentationGetter {
        private final ActivityInfo mActivityInfo;

        ActivityInfoPresentationGetter(
                Context context, int iconDpi, ActivityInfo activityInfo) {
            super(context, iconDpi, activityInfo.applicationInfo);
            mActivityInfo = activityInfo;
        }

        @Override
        protected Drawable getIconSubstituteInternal() {
            Drawable drawable = null;
            try {
                // Do not use ActivityInfo#getIconResource() as it defaults to the app
                if (mActivityInfo.icon != 0) {
                    drawable = loadIconFromResource(
                            mPm.getResourcesForApplication(mActivityInfo.applicationInfo),
                            mActivityInfo.icon);
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "SUBSTITUTE_SHARE_TARGET_APP_NAME_AND_ICON permission granted but "
                        + "couldn't find resources for package", e);
            }

            return drawable;
        }

        @Override
        protected String getAppSubLabelInternal() {
            // Will default to app name if no activity label set, make sure to check if subLabel
            // matches label before final display
            return (String) mActivityInfo.loadLabel(mPm);
        }

        @Override
        protected String getAppLabelForSubstitutePermission() {
            return getAppSubLabelInternal();
        }
    }
}
