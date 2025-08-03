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

import static android.provider.Settings.Secure.ATTENTIVE_TIMEOUT;
import static android.provider.Settings.Secure.SLEEP_TIMEOUT;

import static com.android.tv.settings.library.PreferenceCompat.STATUS_ON;
import static com.android.tv.settings.library.device.display.daydream.EnergySaverState.KEY_ALLOW_TURN_SCREEN_OFF;

import android.content.Context;
import android.os.UserManager;
import android.provider.Settings;
import android.text.format.DateUtils;

import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceCompatManager;
import com.android.tv.settings.library.util.RestrictedPreferenceController;

public class AllowTurnScreenOffWithWakeLockPC extends RestrictedPreferenceController {
    private static final int DEFAULT_SLEEP_TIME_MS = (int) (24 * DateUtils.HOUR_IN_MILLIS);

    public AllowTurnScreenOffWithWakeLockPC(Context context,
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
        updateAllowTurnScreenOffWithWakeLockPref();
    }

    private void updateAllowTurnScreenOffWithWakeLockPref() {
        UserManager userManager = UserManager.get(mContext);
        if (userManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_SCREEN_TIMEOUT)
                && !mPreferenceCompat.isDisabledByAdmin()) {
            mPreferenceCompat.setEnabled(false);
        }
        mPreferenceCompat.setType(PreferenceCompat.TYPE_SWITCH);
        boolean canChangeEnabled = !userManager
                .hasUserRestriction(UserManager.DISALLOW_CONFIG_SCREEN_TIMEOUT);
        if (getSleepTime() == -1) {
            mPreferenceCompat.setChecked(false);
            if (canChangeEnabled) {
                mPreferenceCompat.setEnabled(false);
            }
        } else if (getAttentiveSleepTime() == -1) {
            mPreferenceCompat.setChecked(false);
            if (canChangeEnabled) {
                mPreferenceCompat.setEnabled(true);
            }
        } else {
            mPreferenceCompat.setChecked(true);
            if (canChangeEnabled) {
                mPreferenceCompat.setEnabled(true);
            }
        }
    }

    boolean isChecked() {
        return mPreferenceCompat.getChecked() == STATUS_ON;
    }

    private int getSleepTime() {
        return Settings.Secure.getInt(mContext.getContentResolver(), SLEEP_TIMEOUT,
                DEFAULT_SLEEP_TIME_MS);
    }

    private int getAttentiveSleepTime() {
        return Settings.Secure.getInt(mContext.getContentResolver(), ATTENTIVE_TIMEOUT,
                DEFAULT_SLEEP_TIME_MS);
    }

    @Override
    public String getAttrUserRestriction() {
        return UserManager.DISALLOW_CONFIG_SCREEN_TIMEOUT;
    }

    @Override
    public String[] getPreferenceKey() {
        return new String[]{KEY_ALLOW_TURN_SCREEN_OFF};
    }
}
