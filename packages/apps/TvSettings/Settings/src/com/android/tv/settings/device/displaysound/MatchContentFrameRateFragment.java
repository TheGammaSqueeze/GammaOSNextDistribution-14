/*
 * Copyright 2020 The Android Open Source Project
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

package com.android.tv.settings.device.displaysound;

import static com.android.tv.settings.overlay.FlavorUtils.FLAVOR_CLASSIC;
import static com.android.tv.settings.util.InstrumentationUtils.logToggleInteracted;

import android.app.tvsettings.TvSettingsEnums;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Display;

import androidx.annotation.Keep;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.tv.settings.R;
import com.android.tv.settings.RadioPreference;
import com.android.tv.settings.SettingsPreferenceFragment;
import com.android.tv.settings.overlay.FlavorUtils;

/**
 * This Fragment is responsible for allowing the user to express a preference for matching the
 * display frame rate to to the frame rate of a video being played.
 */
@Keep
public class MatchContentFrameRateFragment extends SettingsPreferenceFragment {

    private static final String KEY_MATCH_CONTENT_FRAME_RATE = "match_content_frame_rate_option";

    private static final String KEY_MATCH_CONTENT_FRAME_RATE_SEAMLESS =
            "match_content_frame_rate_seamless";
    private static final String KEY_MATCH_CONTENT_FRAME_RATE_NON_SEAMLESS =
            "match_content_frame_rate_non_seamless";
    private static final String KEY_MATCH_CONTENT_FRAME_RATE_NEVER =
            "match_content_frame_rate_never";

    private String mCurrentPreferenceKey;

    /** @return the new instance of the class */
    public static MatchContentFrameRateFragment newInstance() {
        return new MatchContentFrameRateFragment();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.match_content_frame_rate, null);

        if (!isSeamlessSwitchingSupported()) {
            removeSeamlessPreference();
        }

        // Do not show sidebar info texts in case of 1 panel settings.
        if (FlavorUtils.getFlavor(getContext()) != FLAVOR_CLASSIC) {
            createInfoFragments();
        }

        mCurrentPreferenceKey = preferenceKeyFromSetting();
        getRadioPreference(mCurrentPreferenceKey).setChecked(true);
        getRadioPreference(mCurrentPreferenceKey).clearOtherRadioPreferences(getPreferenceGroup());
    }

    @VisibleForTesting
    boolean isSeamlessSwitchingSupported() {
        return getDisplayManager().supportsSeamlessRefreshRateSwitching();
    }

    @VisibleForTesting
    PreferenceGroup getPreferenceGroup() {
        return (PreferenceGroup) findPreference(KEY_MATCH_CONTENT_FRAME_RATE);
    }

    @VisibleForTesting
    RadioPreference getRadioPreference(String key) {
        return (RadioPreference) findPreference(key);
    }

    @Override
    public void onResume() {
        super.onResume();
    }


    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        final String key = preference.getKey();
        if (key == null) {
            return super.onPreferenceTreeClick(preference);
        }
        if (preference instanceof RadioPreference) {
            final RadioPreference radioPreference = (RadioPreference) preference;
            radioPreference.setChecked(true);
            radioPreference.clearOtherRadioPreferences(getPreferenceGroup());

            if (key != mCurrentPreferenceKey) {
                int newValue;
                switch (key) {
                    case KEY_MATCH_CONTENT_FRAME_RATE_SEAMLESS: {
                        newValue = Settings.Secure.MATCH_CONTENT_FRAMERATE_SEAMLESSS_ONLY;
                        break;
                    }
                    case KEY_MATCH_CONTENT_FRAME_RATE_NON_SEAMLESS: {
                        newValue = Settings.Secure.MATCH_CONTENT_FRAMERATE_ALWAYS;
                        break;
                    }
                    case KEY_MATCH_CONTENT_FRAME_RATE_NEVER: {
                        newValue = Settings.Secure.MATCH_CONTENT_FRAMERATE_NEVER;
                        break;
                    }
                    default:
                        throw new IllegalArgumentException(
                                "Unknown match content frame rate pref value"
                                        + ": " + key);
                }

                int oldValue = getCurrentSettingValue();
                if (newValue != oldValue) {
                    Settings.Secure.putInt(
                            getContext().getContentResolver(),
                            Settings.Secure.MATCH_CONTENT_FRAME_RATE,
                            newValue);
                    logToggleInteracted(toggleIdFromSetting(oldValue), false);
                    logToggleInteracted(toggleIdFromSetting(newValue), true);
                }
            }
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    protected int getPageId() {
        return TvSettingsEnums.DISPLAY_SOUND_MATCH_CONTENT_FRAMERATE;
    }

    @VisibleForTesting
    DisplayManager getDisplayManager() {
        return getContext().getSystemService(DisplayManager.class);
    }

    private int getCurrentSettingValue() {
        int defaultSetting = isSeamlessSwitchingSupported()
                ? Settings.Secure.MATCH_CONTENT_FRAMERATE_SEAMLESSS_ONLY
                : Settings.Secure.MATCH_CONTENT_FRAMERATE_NEVER;
        return Settings.Secure.getInt(
                getContext().getContentResolver(),
                Settings.Secure.MATCH_CONTENT_FRAME_RATE, defaultSetting);
    }

    private String preferenceKeyFromSetting() {
        int matchContentSetting = getCurrentSettingValue();
        switch (matchContentSetting) {
            case (Settings.Secure.MATCH_CONTENT_FRAMERATE_NEVER): {
                return KEY_MATCH_CONTENT_FRAME_RATE_NEVER;
            }
            case (Settings.Secure.MATCH_CONTENT_FRAMERATE_SEAMLESSS_ONLY): {
                return KEY_MATCH_CONTENT_FRAME_RATE_SEAMLESS;
            }
            case (Settings.Secure.MATCH_CONTENT_FRAMERATE_ALWAYS): {
                return KEY_MATCH_CONTENT_FRAME_RATE_NON_SEAMLESS;
            }
            default:
                throw new IllegalArgumentException("Unknown match content frame rate pref "
                        + "value in stored settings");
        }
    }

    private int toggleIdFromSetting(int matchContentSetting) {
        switch (matchContentSetting) {
            case Settings.Secure.MATCH_CONTENT_FRAMERATE_NEVER:
                return TvSettingsEnums.DISPLAY_SOUND_MATCH_CONTENT_FRAMERATE_NEVER;
            case Settings.Secure.MATCH_CONTENT_FRAMERATE_SEAMLESSS_ONLY:
                return TvSettingsEnums.DISPLAY_SOUND_MATCH_CONTENT_FRAMERATE_SEAMLESS;
            case Settings.Secure.MATCH_CONTENT_FRAMERATE_ALWAYS:
                return TvSettingsEnums.DISPLAY_SOUND_MATCH_CONTENT_FRAMERATE_NON_SEAMLESS;
            default:
                throw new IllegalArgumentException("Unknown match content frame rate pref value");
        }
    }

    private void createInfoFragments() {
        Preference seamlessPreference = findPreference(KEY_MATCH_CONTENT_FRAME_RATE_SEAMLESS);
        if (seamlessPreference != null) {
            if (getDisplayManager().getDisplay(Display.DEFAULT_DISPLAY)
                    .getMode().getAlternativeRefreshRates().length != 0) {
                seamlessPreference.setFragment(
                        MatchContentFrameRateInfo.SeamlessInfoFragment.class.getName());
            } else {
                seamlessPreference.setFragment(
                        MatchContentFrameRateInfo.SeamlessUnsupportedInfoFragment.class.getName());
            }
        }

        Preference nonSeamlessPreference = findPreference(
                KEY_MATCH_CONTENT_FRAME_RATE_NON_SEAMLESS);
        if (nonSeamlessPreference != null) {
            nonSeamlessPreference.setFragment(
                    MatchContentFrameRateInfo.NonSeamlessInfoFragment.class.getName());
        }

        Preference neverPreference = findPreference(KEY_MATCH_CONTENT_FRAME_RATE_NEVER);
        if (neverPreference != null) {
            neverPreference.setFragment(
                    MatchContentFrameRateInfo.NeverInfoFragment.class.getName());
        }
    }

    private void removeSeamlessPreference() {
        Preference seamlessPreference = findPreference(KEY_MATCH_CONTENT_FRAME_RATE_SEAMLESS);
        if (seamlessPreference != null) {
            getPreferenceGroup().removePreference(seamlessPreference);
        }
    }
}