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

package com.android.tv.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

/** A utility class for Google TV */
public class GtvUtils {
    private static final String TAG = "GtvUtils";
    private static final String AMATI_FEATURE = "com.google.android.feature.AMATI_EXPERIENCE";
    private static final String PERMISSION_WRITE_EPG_DATA =
            "com.android.providers.tv.permission.WRITE_EPG_DATA";
    private static final String ACTION_INPUT_SELECTED = "android.apps.tv.launcherx.INPUT_SELECTED";
    private static final String EXTRA_INPUT_ID = "extra_input_id";
    private static final String LAUNCHERX_PACKAGE_NAME = "com.google.android.apps.tv.launcherx";
    private static Boolean mEnabled = null;

    private static boolean isEnabled(Context context) {
        if (mEnabled == null) {
            PackageManager pm = context.getPackageManager();
            mEnabled = pm.hasSystemFeature(AMATI_FEATURE);
        }
        return mEnabled;
    }

    /** Broadcasts the intent with inputId to the Launcher */
    public static void broadcastInputId(Context context, String inputId) {
        if (isEnabled(context)) {
            if (inputId == null) {
                Log.e(TAG, "Will not broadcast inputId because it is null");
            } else {
                Intent intent = new Intent(ACTION_INPUT_SELECTED);
                intent.putExtra(EXTRA_INPUT_ID, inputId);
                intent.setPackage(LAUNCHERX_PACKAGE_NAME);
                context.sendBroadcast(intent, PERMISSION_WRITE_EPG_DATA);
            }
        }
    }
}
