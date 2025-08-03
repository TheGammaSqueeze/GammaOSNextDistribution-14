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

package com.android.tv.settings.library.basic;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.tv.settings.library.util.ResourcesUtil;

import java.util.List;

/** Implementation of {@link BasicModeFeatureProvider}. */
public class BasicModeFeatureProviderImplX implements BasicModeFeatureProvider {

    private static final String TAG = "BasicModeFeatureX";

    // The string "offline_mode" is a static protocol and should not be changed in general.
    private static final String KEY_BASIC_MODE = "offline_mode";

    @Override
    public boolean isBasicMode(@NonNull Context context) {
        final String providerUriString = ResourcesUtil.getString(context,
                "basic_mode_provider_uri");
        if (TextUtils.isEmpty(providerUriString)) {
            Log.e(TAG, "ContentProvider for basic mode is undefined.");
            return false;
        }
        try {
            Uri contentUri = Uri.parse(providerUriString);
            Cursor cursor = context.getContentResolver().query(contentUri, null, null, null);
            if (cursor != null && cursor.getCount() != 0) {
                cursor.moveToFirst();
                String basicMode = cursor.getString(cursor.getColumnIndex(KEY_BASIC_MODE));
                return "1".equals(basicMode);
            }
        } catch (IllegalArgumentException | NullPointerException e) {
            Log.e(TAG, "Unable to query the ContentProvider for basic mode.", e);
            return false;
        }
        return false;
    }

    @Override
    public void startBasicModeExitActivity(@NonNull Activity activity) {
        final String basicModeExitPackage = ResourcesUtil.getString(activity,
                "basic_mode_exit_package");
        final String basicModeExitComponent =
                ResourcesUtil.getString(activity, "basic_mode_exit_component");
        final String basicModeExitData = ResourcesUtil.getString(activity, "basic_mode_exit_data");
        if (TextUtils.isEmpty(basicModeExitPackage) || TextUtils.isEmpty(basicModeExitComponent)
                || TextUtils.isEmpty(basicModeExitData)) {
            Log.e(TAG, "Basic mode exit activity undefined.");
            return;
        }
        ComponentName componentName =
                new ComponentName(basicModeExitPackage, basicModeExitComponent);
        Uri dataUri = Uri.parse(basicModeExitData);
        Intent intent = new Intent().setComponent(componentName).setData(dataUri);
        List<ResolveInfo> intentHandlingActivities =
                activity.getPackageManager().queryIntentActivities(intent, 0);
        for (ResolveInfo info : intentHandlingActivities) {
            if (info.activityInfo != null && info.activityInfo.enabled) {
                Log.d(TAG, "Starting basic mode exit activity.");
                activity.startActivity(intent);
                if (!activity.isFinishing()) {
                    // We finish TvSettings instead of leaving it dangling in the activity stack
                    // as the expected Activity for handling basic mode exit is a HOME that also
                    // intercepts BACK key pressing.
                    activity.finish();
                }
            }
        }
        Log.e(TAG, "Basic mode exit activity not found.");
    }
}
