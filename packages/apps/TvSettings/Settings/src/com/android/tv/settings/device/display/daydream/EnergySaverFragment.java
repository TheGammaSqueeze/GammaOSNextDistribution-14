/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tv.settings.device.display.daydream;

import static android.provider.Settings.Secure.ATTENTIVE_TIMEOUT;
import static android.provider.Settings.Secure.SLEEP_TIMEOUT;

import static com.android.tv.settings.util.InstrumentationUtils.logEntrySelected;

import android.app.AlertDialog;
import android.app.tvsettings.TvSettingsEnums;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.UserManager;
import android.provider.Settings;
import android.text.format.DateUtils;
import android.util.Log;

import androidx.annotation.Keep;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.tv.settings.R;
import com.android.tv.settings.RestrictedPreferenceAdapter;
import com.android.tv.settings.SettingsPreferenceFragment;
import com.android.tv.twopanelsettings.TwoPanelSettingsFragment;

/**
 * The energy saver screen in TV settings.
 */
@Keep
public class EnergySaverFragment extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {
    private static final String TAG = "EnergySaverFragment";
    private static final String KEY_SLEEP_TIME = "sleepTime";
    private static final String KEY_ATTENTIVE_TIME = "attentiveTime";

    private static final String SHARED_PREFS_NAME = "energy_saver";
    private static final String PREF_RESET_ATTENTIVE_TIMEOUT = "reset_attentive_timeout";

    private static final int DEFAULT_SLEEP_TIME_MS = (int) (20 * DateUtils.MINUTE_IN_MILLIS);
    private static final int WARNING_THRESHOLD_SLEEP_TIME_MS =
            (int) (20 * DateUtils.MINUTE_IN_MILLIS);
    private static final int WARNING_THRESHOLD_ATTENTIVE_TIME_MS =
            (int) (4 * DateUtils.HOUR_IN_MILLIS);

    private ListPreference mSleepTimePref;
    private ListPreference mAttentiveTimePref;
    private RestrictedPreferenceAdapter<ListPreference> mRestrictedSleepTime;
    private RestrictedPreferenceAdapter<ListPreference> mRestrictedAttentiveTime;
    private int mDefaultAttentiveTimeoutConfig;

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        setPreferencesFromResource(R.xml.energy_saver, null);

        mDefaultAttentiveTimeoutConfig = getResources()
            .getInteger(com.android.internal.R.integer.config_attentiveTimeout);

        mSleepTimePref = findPreference(KEY_SLEEP_TIME);
        int validatedSleepTime = getValidatedTimeout(getSleepTime(), true);
        mSleepTimePref.setValue(String.valueOf(validatedSleepTime));
        if (getSleepTime() != validatedSleepTime) {
            setSleepTime(validatedSleepTime);
        }
        mSleepTimePref.setOnPreferenceChangeListener(this);
        mSleepTimePref.setOnPreferenceClickListener(
                preference -> {
                    logEntrySelected(TvSettingsEnums.SYSTEM_ENERGYSAVER_START_DELAY);
                    return false;
                });

        mAttentiveTimePref = findPreference(KEY_ATTENTIVE_TIME);
        mAttentiveTimePref.setOnPreferenceChangeListener(this);

        mRestrictedSleepTime = RestrictedPreferenceAdapter.adapt(
                mSleepTimePref, UserManager.DISALLOW_CONFIG_SCREEN_TIMEOUT);
        mRestrictedAttentiveTime = RestrictedPreferenceAdapter.adapt(
                mAttentiveTimePref, UserManager.DISALLOW_CONFIG_SCREEN_TIMEOUT);

        if (!showAttentiveSleepTimeoutSetting()) {
            mAttentiveTimePref.setVisible(false);
            mRestrictedAttentiveTime.updatePreference();
        } else {
            int validatedAttentiveSleepTime = getValidatedTimeout(getAttentiveSleepTime(), false);
            mAttentiveTimePref.setValue(String.valueOf(validatedAttentiveSleepTime));
            if (getAttentiveSleepTime() != validatedAttentiveSleepTime) {
                setAttentiveSleepTime(validatedAttentiveSleepTime);
            }
        }
    }

    private boolean showAttentiveSleepTimeoutSetting() {
        return getResources().getBoolean(R.bool.config_show_standby_timeout);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        switch (preference.getKey()) {
            case KEY_SLEEP_TIME:
                final int newSleepTime = Integer.parseInt((String) newValue);
                if (getSleepTimeEntryId(newSleepTime) != -1) {
                    logEntrySelected(getSleepTimeEntryId(newSleepTime));
                }
                if (showAttentiveSleepTimeoutSetting() && isTimeLargerThan(
                        newSleepTime, getAttentiveSleepTime())) {
                    new AlertDialog.Builder(getContext())
                            .setMessage(R.string.device_energy_saver_validation_sleep)
                            .setPositiveButton(
                                    R.string.settings_ok, (dialog, which) -> dialog.dismiss())
                            .create()
                            .show();
                    return false;
                }  else if (newSleepTime > WARNING_THRESHOLD_SLEEP_TIME_MS || newSleepTime == -1) {
                    // Some regions require a warning to be presented.
                    showConfirmChangeSettingDialog(false, newSleepTime);
                    return false;
                } else {
                    confirmNewSleepTime(newSleepTime);
                    return true;
                }
            case KEY_ATTENTIVE_TIME:
                final int attentiveTime = Integer.parseInt((String) newValue);
                if (isTimeLargerThan(getSleepTime(), attentiveTime)) {
                    new AlertDialog.Builder(getContext())
                            .setMessage(R.string.device_energy_saver_validation_attentive)
                            .setPositiveButton(
                                    R.string.settings_ok, (dialog, which) -> dialog.dismiss())
                            .create()
                            .show();
                    return false;
                } else if (attentiveTime > WARNING_THRESHOLD_ATTENTIVE_TIME_MS
                        || attentiveTime == -1) {
                    showConfirmChangeSettingDialog(true, attentiveTime);
                    return false;
                }
                confirmAttentiveSleepTime(attentiveTime);
                return true;
            default:
                return false;
        }
    }

    private boolean isTimeLargerThan(int x, int y) {
        if (x == -1 && y == -1) {
            return false;
        }
        if (x == -1) {
            return true;
        }
        if (y == -1) {
            return false;
        }
        return x > y;
    }

    private void showConfirmChangeSettingDialog(boolean isAttentiveTimer, int newTime) {
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.device_energy_saver_confirmation_title)
                .setMessage(R.string.device_energy_saver_confirmation_message)
                .setPositiveButton(R.string.settings_confirm,
                        (dialog, which) -> {
                            if (isAttentiveTimer) {
                                confirmAttentiveSleepTime(newTime);
                            } else {
                                confirmNewSleepTime(newTime);
                            }
                        })
                .setNegativeButton(R.string.settings_cancel,
                        (dialog, which) -> dialog.dismiss())
                .create()
                .show();
    }

    private void confirmAttentiveSleepTime(int attentiveTime) {
        if (mAttentiveTimePref != null) {
            mAttentiveTimePref.setValue(String.valueOf(attentiveTime));
            setAttentiveSleepTime(attentiveTime);
            mRestrictedAttentiveTime.updatePreference();
            if (getCallbackFragment() instanceof TwoPanelSettingsFragment) {
                ((TwoPanelSettingsFragment) getCallbackFragment()).refocusPreference(this);
            }
        }
    }

    private int getSleepTime() {
        return Settings.Secure.getInt(getActivity().getContentResolver(), SLEEP_TIMEOUT,
                DEFAULT_SLEEP_TIME_MS);
    }

    private void setSleepTime(int ms) {
        Settings.Secure.putInt(getActivity().getContentResolver(), SLEEP_TIMEOUT, ms);
    }

    private int getAttentiveSleepTime() {
        return getAttentiveSleepTime(mDefaultAttentiveTimeoutConfig);
    }

    private int getAttentiveSleepTime(int def) {
        return Settings.Secure.getInt(getActivity().getContentResolver(), ATTENTIVE_TIMEOUT, def);
    }

    private void setAttentiveSleepTime(int ms) {
        Settings.Secure.putInt(getActivity().getContentResolver(), ATTENTIVE_TIMEOUT, ms);
    }

    // The SLEEP_TIMEOUT and ATTENTIVE_TIMEOUT could be defined in overlay by OEMs. We validate the
    // value to make sure that we select from the predefined options. If the value from overlay is
    // not one of the predefined options, we round it to the closest predefined value, except -1.
    private int getValidatedTimeout(int purposedTimeout, boolean isSleepTimeout) {
        int validatedTimeout =
                isSleepTimeout ? DEFAULT_SLEEP_TIME_MS : mDefaultAttentiveTimeoutConfig;
        if (purposedTimeout < 0) {
            return -1;

        }
        String[] optionsString = isSleepTimeout
                ? getResources().getStringArray(R.array.device_energy_saver_sleep_timeout_values)
                : getResources().getStringArray(
                        R.array.device_energy_saver_attentive_timeout_values);
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

    private void confirmNewSleepTime(int newSleepTime) {
        if (mSleepTimePref != null) {
            setSleepTime(newSleepTime);
            mSleepTimePref.setValue(String.valueOf(newSleepTime));
            mRestrictedSleepTime.updatePreference();
            if (getCallbackFragment() instanceof TwoPanelSettingsFragment) {
                ((TwoPanelSettingsFragment) getCallbackFragment()).refocusPreference(this);
            }
        }
    }

    // TODO(b/158783050): update logging for new options 4H, 8H, 24H.
    // Map @array/screen_off_timeout_entries to defined log enum
    private int getSleepTimeEntryId(int sleepTimeValue) {
        switch (sleepTimeValue) {
            case -1:
                return TvSettingsEnums.SYSTEM_ENERGYSAVER_START_DELAY_NEVER;
            case 900000:
                return TvSettingsEnums.SYSTEM_ENERGYSAVER_START_DELAY_15M;
            case 1800000:
                return TvSettingsEnums.SYSTEM_ENERGYSAVER_START_DELAY_30M;
            case 3600000:
                return TvSettingsEnums.SYSTEM_ENERGYSAVER_START_DELAY_1H;
            case 43200000:
                return TvSettingsEnums.SYSTEM_ENERGYSAVER_START_DELAY_12H;
            default:
                return -1;
        }
    }

    @Override
    protected int getPageId() {
        return TvSettingsEnums.SYSTEM_ENERGYSAVER;
    }

    /**
     * Fix for b/286356445:
     * The attentive timeout was previously set incorrectly when this Fragment was created.
     * This method resets the attentive timeout setting to its default value if the setting
     * is not supposed to be shown and this hasn't been run before.
     */
    public static void resetAttentiveTimeoutIfHidden(Context context) {
        //
        boolean showAttentiveSleepTimeoutSetting = context.getResources().getBoolean(
                R.bool.config_show_standby_timeout);
        if (showAttentiveSleepTimeoutSetting) {
            // Keep current setting, as user can change it and may have changed it
            return;
        }

        try {
            final SharedPreferences sharedPreferences = context.getSharedPreferences(
                    SHARED_PREFS_NAME, Context.MODE_PRIVATE);
            boolean hasResetAttentiveTimeout = sharedPreferences.getBoolean(
                    PREF_RESET_ATTENTIVE_TIMEOUT, false);
            if (!hasResetAttentiveTimeout) {
                Settings.Secure.putString(context.getContentResolver(), ATTENTIVE_TIMEOUT, "");
                sharedPreferences.edit()
                        .putBoolean(PREF_RESET_ATTENTIVE_TIMEOUT, true)
                        .apply();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to reset attentive timeout", e);
        }
    }
}
