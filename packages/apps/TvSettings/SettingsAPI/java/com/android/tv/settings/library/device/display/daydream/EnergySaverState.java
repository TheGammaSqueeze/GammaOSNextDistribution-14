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

import static com.android.tv.settings.library.ManagerUtil.STATE_ENERGY_SAVER;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateUtils;

import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceControllerState;
import com.android.tv.settings.library.util.AbstractPreferenceController;
import com.android.tv.settings.library.util.ResourcesUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * State to provide data for rendering energy saver settings screen.
 */
public class EnergySaverState extends PreferenceControllerState {
    public static final String KEY_SLEEP_TIME = "sleepTime";
    public static final String KEY_ALLOW_TURN_SCREEN_OFF = "allowTurnScreenOff";
    private static final int DEFAULT_SLEEP_TIME_MS = (int) (24 * DateUtils.HOUR_IN_MILLIS);
    private static final int WARNING_THRESHOLD_SLEEP_TIME_MS = (int) (4 * DateUtils.HOUR_IN_MILLIS);
    private AllowTurnScreenOffWithWakeLockPC mAllowTurnScreenOffWithWakeLockPC;
    private SleepTimePC mSleepTimePC;

    public EnergySaverState(Context context,
            UIUpdateCallback callback) {
        super(context, callback);
    }

    @Override
    public void onCreate(Bundle extras) {
        super.onCreate(extras);
        if (allowTurnOffWithWakeLock()) {
            int validatedAttentiveSleepTime = getValidatedTimeout(getAttentiveSleepTime());
            mSleepTimePC.setValue(String.valueOf(validatedAttentiveSleepTime));
            if (getAttentiveSleepTime() != validatedAttentiveSleepTime) {
                setAttentiveSleepTime(validatedAttentiveSleepTime);
            }
        } else {
            int validatedSleepTime = getValidatedTimeout(getSleepTime());
            mSleepTimePC.setValue(String.valueOf(validatedSleepTime));
            if (getSleepTime() != validatedSleepTime) {
                setSleepTime(validatedSleepTime);
            }
        }
        mSleepTimePC.updateAndNotify();
    }

    @Override
    public boolean onPreferenceChange(String[] key, Object newValue) {
        switch (key[0]) {
            case KEY_SLEEP_TIME:
                final int newSleepTime = Integer.parseInt((String) newValue);
                if (EnergySaverState.showStandbyTimeout(mContext)
                        && (newSleepTime > WARNING_THRESHOLD_SLEEP_TIME_MS || newSleepTime == -1)) {
                    // Some regions require a warning to be presented.
                    new AlertDialog.Builder(mContext)
                            .setTitle(ResourcesUtil.getString(mContext,
                                    "device_energy_saver_confirmation_title"))
                            .setMessage(getConfirmationDialogDescription(newSleepTime))
                            .setPositiveButton(
                                    ResourcesUtil.getString(mContext, "settings_confirm"),
                                    (dialog, which) -> confirmNewSleepTime(newSleepTime))
                            .setNegativeButton(ResourcesUtil.getString(mContext, "settings_cancel"),
                                    (dialog, which) -> dialog.dismiss())
                            .create()
                            .show();
                    return false;
                } else {
                    updateTimeOut(allowTurnOffWithWakeLock(), newSleepTime);
                    return true;
                }
            case KEY_ALLOW_TURN_SCREEN_OFF:
                updateTimeOut((boolean) newValue, Integer.parseInt(mSleepTimePC.getValue()));
                return true;
            default:
                return false;
        }
    }

    private void confirmNewSleepTime(int newSleepTime) {
        if (mSleepTimePC != null) {
            updateTimeOut(allowTurnOffWithWakeLock(), newSleepTime);
            mSleepTimePC.setValue(String.valueOf(newSleepTime));
        }
    }

    private void updateTimeOut(boolean allowTurnScreenOffWithWakeLock, int value) {
        if (allowTurnScreenOffWithWakeLock) {
            setSleepTime(value);
            if (showStandbyTimeout(mContext)) {
                setAttentiveSleepTime(value);
            }
        } else {
            setSleepTime(value);
            if (showStandbyTimeout(mContext)) {
                setAttentiveSleepTime(-1);
            }
        }
        mSleepTimePC.setValue(String.valueOf(value));
        mAllowTurnScreenOffWithWakeLockPC.updateAndNotify();
    }

    private String getConfirmationDialogDescription(int newSleepTime) {
        String sleepTimeText = null;
        String[] optionsValues = ResourcesUtil.getStringArray(mContext,
                "screen_off_timeout_values");
        String[] optionsStrings = ResourcesUtil.getStringArray(mContext,
                "screen_off_timeout_entries");
        for (int i = 0; i < optionsValues.length; i++) {
            if (newSleepTime == Integer.parseInt(optionsValues[i])) {
                sleepTimeText = optionsStrings[i];
            }
        }
        return ResourcesUtil.getString(
                mContext, "device_energy_saver_confirmation_text", sleepTimeText);
    }

    private int getSleepTime() {
        return Settings.Secure.getInt(mContext.getContentResolver(), SLEEP_TIMEOUT,
                DEFAULT_SLEEP_TIME_MS);
    }

    private int getAttentiveSleepTime() {
        return Settings.Secure.getInt(mContext.getContentResolver(), ATTENTIVE_TIMEOUT,
                DEFAULT_SLEEP_TIME_MS);
    }

    private void setSleepTime(int ms) {
        Settings.Secure.putInt(mContext.getContentResolver(), SLEEP_TIMEOUT, ms);
    }

    private void setAttentiveSleepTime(int ms) {
        Settings.Secure.putInt(mContext.getContentResolver(), ATTENTIVE_TIMEOUT, ms);
    }

    static boolean showStandbyTimeout(Context context) {
        return ResourcesUtil.getBoolean(context, "config_show_standby_timeout");
    }

    private boolean allowTurnOffWithWakeLock() {
        return showStandbyTimeout(mContext) && mAllowTurnScreenOffWithWakeLockPC.isChecked();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

    }

    // The SLEEP_TIMEOUT and ATTENTIVE_TIMEOUT could be defined in overlay by OEMs. We validate the
    // value to make sure that we select from the predefined options. If the value from overlay is
    // not one of the predefined options, we round it to the closest predefined value, except -1.
    private int getValidatedTimeout(int purposedTimeout) {
        int validatedTimeout = DEFAULT_SLEEP_TIME_MS;
        if (purposedTimeout < 0) {
            return -1;
        }
        String[] optionsString = ResourcesUtil.getStringArray(mContext,
                "screen_off_timeout_values");
        // Find the value from the predefined values that is closest to the proposed value except -1
        int diff = Integer.MAX_VALUE;
        for (String option : optionsString) {
            if (Integer.parseInt(option) != -1) {
                int currentDiff = Math.abs(purposedTimeout - Integer.parseInt(option));
                if (currentDiff < diff) {
                    diff = currentDiff;
                    validatedTimeout = Integer.parseInt(option);
                }
            }
        }
        return validatedTimeout;
    }

    @Override
    public int getStateIdentifier() {
        return STATE_ENERGY_SAVER;
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        List<AbstractPreferenceController> preferenceControllers = new ArrayList<>();
        mAllowTurnScreenOffWithWakeLockPC = new AllowTurnScreenOffWithWakeLockPC(
                mContext, mUIUpdateCallback, getStateIdentifier(), mPreferenceCompatManager);
        mSleepTimePC = new SleepTimePC(
                mContext, mUIUpdateCallback, getStateIdentifier(), mPreferenceCompatManager);
        preferenceControllers.add(mAllowTurnScreenOffWithWakeLockPC);
        preferenceControllers.add(mSleepTimePC);
        return preferenceControllers;
    }
}
