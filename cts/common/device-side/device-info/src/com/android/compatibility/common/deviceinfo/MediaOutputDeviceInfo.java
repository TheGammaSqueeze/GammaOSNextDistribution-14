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

package com.android.compatibility.common.deviceinfo;

import android.content.res.Resources;
import android.util.Log;
import android.util.TypedValue;

import com.android.compatibility.common.util.DeviceInfoStore;

/**
 * Media output information collector.
 */
public final class MediaOutputDeviceInfo extends DeviceInfo {
    private static final String TAG = "MediaOutputDeviceInfo";

    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        int outputSwitcherVersion = 0;
        int identifier =
                Resources.getSystem()
                        .getIdentifier("config_mediaOutputSwitchDialogVersion", "integer",
                                "android");
        try {
            if (identifier != 0) {
                TypedValue typedValue = new TypedValue();
                Resources.getSystem().getValue(identifier, typedValue, true);
                if (typedValue.type == TypedValue.TYPE_INT_DEC
                        || typedValue.type == TypedValue.TYPE_INT_HEX) {
                    outputSwitcherVersion = typedValue.data;
                }
            }
        } catch (Resources.NotFoundException e) {
            Log.w(TAG, "failed to get media output version", e);
        }
        store.addResult("media_output_switch_dialog_version", outputSwitcherVersion);
    }
}
