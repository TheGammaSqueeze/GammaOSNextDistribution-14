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

package com.android.tv.settings.library.device.display.displaysound;

import static com.android.tv.settings.library.device.display.displaysound.HdrFormatSelectionState.KEY_FORMAT_INFO_ON_MANUAL;
import static com.android.tv.settings.library.device.display.displaysound.HdrFormatSelectionState.KEY_HDR_FORMAT_PREFIX;
import static com.android.tv.settings.library.device.display.displaysound.HdrFormatSelectionState.KEY_SUPPORTED_HDR_FORMATS;
import static com.android.tv.settings.library.device.display.displaysound.HdrFormatSelectionState.KEY_UNSUPPORTED_HDR_FORMATS;

import android.annotation.NonNull;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.text.TextUtils;

import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceCompatManager;
import com.android.tv.settings.library.util.AbstractPreferenceController;

import java.util.Set;

/**
 * Controller for the hdr formats switch preferences.
 */
public class HdrFormatPreferenceController extends AbstractPreferenceController {
    private static final String TAG = "HdrFormatController";
    private final int mHdrType;
    private final DisplayManager mDisplayManager;
    private final Set<Integer> mDisplayReportedHdrTypes;
    private final Set<Integer> mUserDisabledHdrTypes;

    private boolean mAvailable;

    public HdrFormatPreferenceController(Context context,
            UIUpdateCallback callback, int stateIdentifier,
            PreferenceCompatManager preferenceCompatManager,
            int hdrType,
            DisplayManager displayManager,
            @NonNull Set<Integer> displayReportedHdrTypes,
            @NonNull Set<Integer> userDisabledHdrTypes) {
        super(context, callback, stateIdentifier, preferenceCompatManager);
        mHdrType = hdrType;
        mDisplayManager = displayManager;
        mDisplayReportedHdrTypes = displayReportedHdrTypes;
        mUserDisabledHdrTypes = userDisabledHdrTypes;
    }

    @Override
    public boolean isAvailable() {
        return mAvailable;
    }

    @Override
    public void init() {
        String title = HdrFormatSelectionState.getFormatPreferenceTitle(mContext, mHdrType);
        mAvailable = !TextUtils.isEmpty(title);
        mPreferenceCompat.setTitle(title);
        update();
    }

    @Override
    public void update() {
        if (mDisplayReportedHdrTypes.contains(mHdrType)) {
            mPreferenceCompat.setType(PreferenceCompat.TYPE_SWITCH);
            mPreferenceCompat.setChecked(!mUserDisabledHdrTypes.contains(mHdrType));
            mPreferenceCompat.setEnabled(true);
        } else {
            mPreferenceCompat.setType(PreferenceCompat.TYPE_PREFERENCE);
        }
    }

    @Override
    public boolean handlePreferenceTreeClick(boolean status) {
        final boolean enabled = mPreferenceCompat.getChecked() == PreferenceCompat.STATUS_ON;

        if (enabled) {
            mUserDisabledHdrTypes.remove(mHdrType);
        } else {
            mUserDisabledHdrTypes.add(mHdrType);
        }
        mDisplayManager.setUserDisabledHdrTypes(toArray(mUserDisabledHdrTypes));
        return true;
    }

    private int[] toArray(Set<Integer> set) {
        return set.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    public String[] getPreferenceKey() {
        String key = KEY_HDR_FORMAT_PREFIX + mHdrType;
        String[] compoundKey = mDisplayReportedHdrTypes.contains(mHdrType)
                ? new String[]{KEY_FORMAT_INFO_ON_MANUAL, KEY_SUPPORTED_HDR_FORMATS, key}
                : new String[]{KEY_FORMAT_INFO_ON_MANUAL, KEY_UNSUPPORTED_HDR_FORMATS, key};
        return compoundKey;
    }
}
