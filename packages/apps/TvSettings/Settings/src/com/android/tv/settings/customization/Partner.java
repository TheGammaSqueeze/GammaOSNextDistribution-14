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

package com.android.tv.settings.customization;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * Singleton class that is aware finds the settings customization apk available in the
 * system image. Exposes methods to get resources from this package.
 */
public final class Partner {
    private static final String TAG = "Partner";

    private static final String CUSTOMIZATION_ACTION =
            "com.tv.settings.action.PARTNER_CUSTOMIZATION";

    private Resources mResources;
    private String mPackage;
    private static Partner sPartner = null;

    private Partner(Context context) {
        try {
            final PackageManager packageManager = context.getPackageManager();
            final Intent intent = new Intent(CUSTOMIZATION_ACTION);
            final List<ResolveInfo> resolveInfoList = packageManager
                    .queryBroadcastReceivers(intent, 0);
            if (resolveInfoList == null || resolveInfoList.isEmpty()) {
                Log.i(TAG, "Partner customization apk not found");
            } else {
                mPackage = resolveInfoList.get(0).activityInfo.packageName;
                Log.i(TAG, "Found partner customization apk: " + mPackage);
                mResources = context.getPackageManager().getResourcesForApplication(
                        mPackage);
            }
        } catch (PackageManager.NameNotFoundException nameNotFoundException) {
            Log.e(TAG, "Error in getting resources of partner customization apk");
        }
    }

    public static Partner getInstance(Context context) {
        if (sPartner == null) {
            sPartner = new Partner(context);
        }
        return sPartner;
    }

    public boolean isCustomizationPackageProvided() {
        return mPackage != null && mResources != null;
    }

    @Nullable
    public Integer getInteger(String name) {
        final Integer id = getIdentifier(name, "integer");
        if (id == null) {
            Log.i(TAG, "Unable to find resource id of integer: " + name);
            return null;
        }
        return mResources.getInteger(id);
    }

    @Nullable
    private Integer getIdentifier(String name, String defType) {
        if (mResources == null) {
            Log.i(TAG, "Partner customization resource reference is null");
            return null;
        }
        return mResources.getIdentifier(name, defType, mPackage);
    }

    @Nullable
    public String getString(String name) {
        final Integer id = getIdentifier(name, "string");
        if (id == null) {
            Log.i(TAG, "Unable to find resource id of string: " + name);
            return null;
        }
        return mResources.getString(id);
    }

    @Nullable
    public Drawable getDrawable(String name, Resources.Theme theme) {
        final Integer id = getIdentifier(name, "drawable");
        if (id == null) {
            Log.i(TAG, "Unable to find resource id of drawable: " + name);
            return null;
        }
        return mResources.getDrawable(id, theme);
    }

    @Nullable
    public String[] getArray(String name) {
        final Integer id = getIdentifier(name, "array");
        if (id == null) {
            Log.i(TAG, "Unable to find resource id of array: " + name);
            return null;
        }
        return mResources.getStringArray(id);
    }
}
