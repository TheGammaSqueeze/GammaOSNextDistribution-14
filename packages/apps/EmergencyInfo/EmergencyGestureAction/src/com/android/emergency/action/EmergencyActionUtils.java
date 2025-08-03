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

package com.android.emergency.action;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

/**
 * Util to for emergency action handling
 */
public class EmergencyActionUtils {

    private static final Uri EMERGENCY_ACTION_AUTHORITY = new Uri.Builder().scheme(
            ContentResolver.SCHEME_CONTENT)
            .authority("com.android.settings.emergency")
            .build();
    private static final String ACTION_START_EMERGENCY_CALL =
            "com.android.settings.emergency.MAKE_EMERGENCY_CALL";

    /**
     * Triggers the action to place emergency call
     */
    public static void placeEmergencyCall(Context context) {
        context.getContentResolver().call(EMERGENCY_ACTION_AUTHORITY,
                ACTION_START_EMERGENCY_CALL, null, null);
    }

    public static boolean isDefaultEmergencyGestureEnabled(Context context) {
        return context.getResources().getBoolean(
                com.android.internal.R.bool.config_defaultEmergencyGestureEnabled);
    }

    public static boolean isDefaultEmergencyGestureSoundEnabled(Context context) {
        return context.getResources().getBoolean(
                com.android.internal.R.bool.config_defaultEmergencyGestureSoundEnabled);
    }
}
