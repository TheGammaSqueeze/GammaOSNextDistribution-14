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

package com.android.devicelockcontroller.policy;

import static android.Manifest.permission.RECEIVE_EMERGENCY_BROADCAST;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Telephony;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.android.devicelockcontroller.util.LogUtil;

import java.util.Locale;

/** This class provides utility functions related to CellBroadcast. */
final class CellBroadcastUtils {
    private static final String TAG = "CellBroadcastUtils";

    private CellBroadcastUtils() {}

    /** Utility method to query the default CBR's package name. */
    @Nullable
    static String getDefaultCellBroadcastReceiverPackageName(Context context) {
        final PackageManager packageManager = context.getPackageManager();
        final ResolveInfo resolveInfo =
                packageManager.resolveActivity(
                        new Intent(Telephony.Sms.Intents.SMS_CB_RECEIVED_ACTION),
                        PackageManager.MATCH_SYSTEM_ONLY);

        if (resolveInfo == null) {
            LogUtil.e(TAG, "getDefaultCellBroadcastReceiverPackageName: no package found");

            return null;
        }

        final String packageName = resolveInfo.activityInfo.applicationInfo.packageName;
        LogUtil.d(TAG, String.format(Locale.US,
                "getDefaultCellBroadcastReceiverPackageName: found package: %s", packageName));

        if (TextUtils.isEmpty(packageName)
                || packageManager.checkPermission(RECEIVE_EMERGENCY_BROADCAST, packageName)
                == PackageManager.PERMISSION_DENIED) {
            LogUtil.e(TAG, String.format(Locale.US, "getDefaultCellBroadcastReceiverPackageName: "
                        + "returning null; permission check failed for : %s", packageName));

            return null;
        }

        return packageName;
    }
}
