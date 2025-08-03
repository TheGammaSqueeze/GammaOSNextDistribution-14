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

package com.android.federatedcompute.services.common;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import com.android.federatedcompute.internal.util.LogUtil;

/** Checks the battery status of the device. */
public class BatteryInfo {
    private static final String TAG = BatteryInfo.class.getSimpleName();
    private final Context mContext;
    private final Flags mFlags;

    public BatteryInfo(Context context, Flags flags) {
        this.mContext = context;
        this.mFlags = flags;
    }

    /** Gets the level and returns -1 if not enough info is available. */
    private float handleBatteryStatusIntent(Intent batteryStatus) {
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        if (level != -1 && scale > 0) {
            return level / (float) scale;
        } else {
            LogUtil.e(TAG, "Bad Battery Changed intent: batteryLevel= %d, scale=%d", level, scale);
            return -1;
        }
    }

    /** Checks if battery level is sufficient to continute training. */
    public boolean batteryOkForTraining(boolean requireBatteryNotLow) {
        Intent stickyIntent =
                mContext.registerReceiver(
                        null,
                        new IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                        Context.RECEIVER_EXPORTED);
        // The integer values of the enums line up between the two. Returns OK if we can't get
        // battery status intent success.
        int status = stickyIntent.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
        float level = handleBatteryStatusIntent(stickyIntent);

        // Check if battery level is sufficient.
        float minBatteryLevel = mFlags.getTrainingMinBatteryLevel() / 100.0f;
        if (requireBatteryNotLow && level < minBatteryLevel) {
            LogUtil.i(
                    TAG,
                    "Battery level insufficient (%f < %f), skipping training.",
                    level,
                    minBatteryLevel);
            return false;
        }
        return true;
    }
}
