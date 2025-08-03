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

package com.android.documentsui.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.UserProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.documentsui.base.UserId;
import com.android.modules.utils.build.SdkLevel;

/**
 * A utility class for cross-profile usage.
 */
public class CrossProfileUtils {

    private static final String TAG = "CrossProfileUtils";
    private static final String PROFILE_TARGET_ACTIVITY =
            "com.android.internal.app.IntentForwarderActivity";

    private CrossProfileUtils() {
    }

    /**
     * Returns whether this resolution represents the intent forwarder activity.
     */
    public static boolean isCrossProfileIntentForwarderActivity(ResolveInfo info) {
        if (VersionUtils.isAtLeastR()) {
            return info.isCrossProfileIntentForwarderActivity();
        }
        if (info.activityInfo != null) {
            return PROFILE_TARGET_ACTIVITY.equals(info.activityInfo.targetActivity);
        }
        return false;
    }

    /**
     * Returns the {@link ResolveInfo} if this intent is a cross-profile intent or {@code null}
     * otherwise.
     */
    @Nullable
    public static ResolveInfo getCrossProfileResolveInfo(UserId currentUser,
            PackageManager packageManager, Intent intent, Context context,
            boolean isPrivateSpaceEnabled) {
        if (isPrivateSpaceEnabled && SdkLevel.isAtLeastV()) {
            return getCrossProfileResolveInfoPostV(currentUser, packageManager,
                    intent, context);
        }
        return getCrossProfileResolveInfoPreV(packageManager, intent);
    }

    @Nullable
    @TargetApi(35)
    private static ResolveInfo getCrossProfileResolveInfoPostV(
            UserId currentUser, PackageManager packageManager,
            Intent intent, Context context) {
        UserManager userManager = context.getSystemService(UserManager.class);
        UserHandle queringUser = UserHandle.of(currentUser.getIdentifier());
        if (userManager == null) {
            Log.e(TAG, "cannot obtain user manager");
            return null;
        }

        UserProperties userProperties = userManager.getUserProperties(queringUser);
        if (userProperties == null) {
            Log.e(TAG, "cannot obtain user properties");
            return null;
        }

        if (userProperties.getCrossProfileContentSharingStrategy()
                == UserProperties.CROSS_PROFILE_CONTENT_SHARING_DELEGATE_FROM_PARENT) {
            queringUser = userManager.getProfileParent(queringUser);
        }

        for (ResolveInfo info : packageManager.queryIntentActivitiesAsUser(intent,
                PackageManager.MATCH_DEFAULT_ONLY, queringUser)) {
            if (isCrossProfileIntentForwarderActivity(info)) {
                return info;
            }
        }
        return null;
    }

    @Nullable
    private static ResolveInfo getCrossProfileResolveInfoPreV(
            PackageManager packageManager,
            Intent intent) {
        for (ResolveInfo info : packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY)) {
            if (isCrossProfileIntentForwarderActivity(info)) {
                return info;
            }
        }
        return null;
    }
}
