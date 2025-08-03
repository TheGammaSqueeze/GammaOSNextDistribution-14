/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tv.settings.library.startup.startup;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.tv.settings.library.util.ResourcesUtil;

import java.util.List;

/**
 * Implementation of {@link StartupVerificationFeatureProvider} that can be enabled using runtime
 * resource overlay.
 */
public class StartupVerificationFeatureProviderImplX implements StartupVerificationFeatureProvider {

    private static final String TAG = "StartupVerifyFeatureX";

    @Override
    public boolean startStartupVerificationActivityForResult(
            @NonNull Activity activity, int requestCode) {
        final String startupVerificationAction =
                ResourcesUtil.getString(activity, "startup_verification_action");
        final String startupVerificationPackage =
                ResourcesUtil.getString(activity, "startup_verification_package");
        if (TextUtils.isEmpty(startupVerificationAction)
                || TextUtils.isEmpty(startupVerificationPackage)) {
            Log.e(TAG, "Activity for handling startup verification is undefined.");
            return false;
        }
        Intent intent =
                new Intent(startupVerificationAction).setPackage(startupVerificationPackage);
        List<ResolveInfo> intentHandlingActivities =
                activity.getPackageManager().queryIntentActivities(
                        intent, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo info : intentHandlingActivities) {
            if (info.activityInfo != null && info.activityInfo.enabled) {
                Log.v(TAG, "Starting startup verification activity.");
                activity.startActivityForResult(intent, requestCode);
                return true;
            }
        }
        Log.v(TAG, "Missing Activity for handling startup verification.");
        return false;
    }
}
