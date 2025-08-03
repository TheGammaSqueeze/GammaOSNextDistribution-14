/*
 * Copyright (C) 2021-2024 The LineageOS Project
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
 * limitations under the License
 */

package com.android.tv.settings.system;

import android.app.tvsettings.TvSettingsEnums;
import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;

import androidx.annotation.Keep;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;

import com.android.internal.app.AssistUtils;
import com.android.tv.settings.R;
import com.android.tv.settings.SettingsPreferenceFragment;

import lineageos.providers.LineageSettings;

import org.lineageos.internal.logging.LineageMetricsLogger;

/**
 * The button settings screen in TV settings.
 */
@Keep
public class ButtonsFragment extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {
    private static final String KEY_ADVANCED_REBOOT = "advanced_reboot";
    private static final String KEY_POWER_BUTTON_LONG_PRESS_ACTION =
            "power_button_long_press_action";

    private static final int LONG_PRESS_POWER_BUTTON_FOR_ASSISTANT = 1;
    private static final int LONG_PRESS_POWER_BUTTON_FOR_POWER_MENU = 0;

    private AssistUtils mAssistUtils;

    private ListPreference mPowerButtonLongPressAction;

    public static ButtonsFragment newInstance() {
        return new ButtonsFragment();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        setPreferencesFromResource(R.xml.buttons, null);

        TwoStatePreference advancedReboot = findPreference(KEY_ADVANCED_REBOOT);
        advancedReboot.setOnPreferenceChangeListener(this);

        mAssistUtils = new AssistUtils(context);

        mPowerButtonLongPressAction = findPreference(KEY_POWER_BUTTON_LONG_PRESS_ACTION);
        if (PowerMenuSettingsUtils.isLongPressPowerSettingAvailable(context) && hasAssistant()) {
            mPowerButtonLongPressAction.setOnPreferenceChangeListener(
                (preference, newValue) -> {
                    int action = Integer.parseInt((String) newValue);
                    switch (action) {
                        case LONG_PRESS_POWER_BUTTON_FOR_ASSISTANT:
                            PowerMenuSettingsUtils.setLongPressPowerForAssistant(context);
                            break;
                        case LONG_PRESS_POWER_BUTTON_FOR_POWER_MENU:
                            PowerMenuSettingsUtils.setLongPressPowerForPowerMenu(context);
                            break;
                    }
                    return true;
                });
        } else {
            getPreferenceScreen().removePreference(mPowerButtonLongPressAction);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (KEY_ADVANCED_REBOOT.equals(preference.getKey())) {
            LineageSettings.Secure.putInt(getContext().getContentResolver(),
                    LineageSettings.Secure.ADVANCED_REBOOT, (Boolean) newValue ? 1 : 0);
        }
        return true;
    }

    private boolean hasAssistant() {
        return mAssistUtils.getAssistComponentForUser(UserHandle.myUserId()) != null;
    }
}
