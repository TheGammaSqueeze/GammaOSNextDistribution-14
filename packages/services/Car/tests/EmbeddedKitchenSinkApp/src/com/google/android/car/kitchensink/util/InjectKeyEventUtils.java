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

package com.google.android.car.kitchensink.util;

import android.car.CarOccupantZoneManager;
import android.util.Log;

import com.android.internal.util.Preconditions;

public final class InjectKeyEventUtils {
    private static final String TAG = InjectKeyEventUtils.class.getSimpleName();
    private static final String PREFIX_INJECTING_KEY_CMD = "cmd car_service inject-key ";
    private static final String OPTION_SEAT = " -s ";

    public static void injectKeyByShell(CarOccupantZoneManager.OccupantZoneInfo zone,
            int keyCode) {
        Preconditions.checkArgument(zone != null, " zone cannot be null");
        // generate a command message
        StringBuilder sb = new StringBuilder()
                .append(PREFIX_INJECTING_KEY_CMD)
                .append(OPTION_SEAT)
                .append(zone.seat)
                .append(' ')
                .append(keyCode)
                .append("\n");
        try {
            Runtime.getRuntime().exec(sb.toString());
        } catch (Exception e) {
            Log.e(TAG, "Cannot flush", e);
        }
    }
}
