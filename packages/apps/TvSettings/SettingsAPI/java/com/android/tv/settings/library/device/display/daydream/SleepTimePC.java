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

package com.android.tv.settings.library.device.display.daydream;

import static com.android.tv.settings.library.device.display.daydream.EnergySaverState.KEY_SLEEP_TIME;

import android.content.Context;
import android.os.UserManager;
import android.text.format.DateUtils;

import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceCompatManager;
import com.android.tv.settings.library.util.ResourcesUtil;
import com.android.tv.settings.library.util.RestrictedPreferenceController;

public class SleepTimePC extends RestrictedPreferenceController {
    private static final int WARNING_THRESHOLD_SLEEP_TIME_MS = (int) (4 * DateUtils.HOUR_IN_MILLIS);

    public SleepTimePC(Context context,
            UIUpdateCallback callback, int stateIdentifier,
            PreferenceCompatManager preferenceCompatManager) {
        super(context, callback, stateIdentifier, preferenceCompatManager);
    }

    @Override
    public boolean useAdminDisabledSummary() {
        return false;
    }

    @Override
    public void update() {
        mPreferenceCompat.setHasOnPreferenceChangeListener(true);
        mPreferenceCompat.setType(PreferenceCompat.TYPE_LIST);
        UserManager userManager = UserManager.get(mContext);
        if (userManager.hasUserRestriction(getAttrUserRestriction())) {
            mPreferenceCompat.setEnabled(false);
        }
        mPreferenceCompat.setEntries(
                ResourcesUtil.getStringArray(mContext, "screen_off_timeout_entries"));
        mPreferenceCompat.setEntryValues(
                ResourcesUtil.getStringArray(mContext, "screen_off_timeout_values"));
    }

    @Override
    public String getAttrUserRestriction() {
        return UserManager.DISALLOW_CONFIG_SCREEN_TIMEOUT;
    }

    @Override
    public String[] getPreferenceKey() {
        return new String[]{KEY_SLEEP_TIME};
    }

    public void setValue(String value) {
        mPreferenceCompat.setValue(value);
    }

    public String getValue() {
        return mPreferenceCompat.getValue();
    }
}
