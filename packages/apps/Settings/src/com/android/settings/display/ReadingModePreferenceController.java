/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.settings.display;

import android.content.Context;

import com.android.settings.R;
import com.android.settings.core.TogglePreferenceController;

import lineageos.hardware.LineageHardwareManager;

public class ReadingModePreferenceController extends TogglePreferenceController {

    private final LineageHardwareManager mHardware;

    public ReadingModePreferenceController(Context context, String key) {
        super(context, key);
        mHardware = LineageHardwareManager.getInstance(context);
    }

    @Override
    public int getAvailabilityStatus() {
        return mHardware.isSupported(LineageHardwareManager.FEATURE_READING_ENHANCEMENT)
                ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public boolean isChecked() {
        return mHardware.get(LineageHardwareManager.FEATURE_READING_ENHANCEMENT);
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        return mHardware.set(LineageHardwareManager.FEATURE_READING_ENHANCEMENT, isChecked);
    }

    @Override
    public int getSliceHighlightMenuRes() {
        return R.string.menu_key_display;
    }
}
