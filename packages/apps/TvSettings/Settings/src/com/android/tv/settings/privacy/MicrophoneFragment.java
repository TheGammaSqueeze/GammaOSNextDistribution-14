/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tv.settings.privacy;

import static android.provider.Settings.Global.RECEIVE_EXPLICIT_USER_INTERACTION_AUDIO_ENABLED;

import static com.android.tv.settings.util.InstrumentationUtils.logToggleInteracted;
import static com.android.tv.twopanelsettings.slices.SlicesConstants.EXTRA_PREFERENCE_INFO_SUMMARY;
import static com.android.tv.twopanelsettings.slices.SlicesConstants.EXTRA_PREFERENCE_INFO_TEXT;
import static com.android.tv.twopanelsettings.slices.SlicesConstants.EXTRA_PREFERENCE_INFO_TITLE_ICON;

import android.app.tvsettings.TvSettingsEnums;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.Keep;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.tv.settings.R;
import com.android.tv.settings.overlay.FlavorUtils;
import com.android.tv.twopanelsettings.TwoPanelSettingsFragment;
import com.android.tv.twopanelsettings.slices.InfoFragment;

/**
 * The microphone settings screen in TV settings.
 */
@Keep
public class MicrophoneFragment extends SensorFragment {
    private static final String MIC_REMOTE_CATEGORY_KEY = "mic_remote_category";
    private static final String MIC_REMOTE_TOGGLE_KEY = "mic_remote_toggle";
    private static final String MIC_REMOTE_TOGGLE_INFO_KEY = "mic_remote_toggle_info";
    private SwitchPreference mMicRemoteToggle;
    private static final int DEFAULT_MIC_REMOTE_TOGGLE_STATE = 1;

    @Override
    protected final void addHardwareToggle(PreferenceScreen screen, Context themedContext) {
        PreferenceCategory category = new PreferenceCategory(themedContext);
        category.setKey(MIC_REMOTE_CATEGORY_KEY);
        category.setTitle(themedContext.getString(R.string.privacy_assistant_settings_title));
        category.setVisible(false);
        screen.addPreference(category);

        mMicRemoteToggle = new SwitchPreference(themedContext);
        mMicRemoteToggle.setFragment(InfoFragment.class.getCanonicalName());
        mMicRemoteToggle.setKey(MIC_REMOTE_TOGGLE_KEY);
        mMicRemoteToggle.setTitle(themedContext.getString(R.string.mic_remote_toggle_title));
        mMicRemoteToggle.setChecked(getMicRemoteToggleState());
        Bundle b = mMicRemoteToggle.getExtras();
        b.putParcelable(EXTRA_PREFERENCE_INFO_TITLE_ICON,
                Icon.createWithResource(themedContext, R.drawable.ic_info_outline_base));
        category.addPreference(mMicRemoteToggle);
        if (!FlavorUtils.isTwoPanel(themedContext)) {
            // Show the toggle info text beneath instead.
            Preference toggleInfo = new Preference(themedContext);
            toggleInfo.setKey(MIC_REMOTE_TOGGLE_INFO_KEY);
            toggleInfo.setLayoutResource(R.layout.sensor_toggle_info);
            toggleInfo.setSelectable(false);
            category.addPreference(toggleInfo);
        }
    }

    private boolean getMicRemoteToggleState() {
        return Settings.Global.getInt(
                getActivity().getContentResolver(),
                RECEIVE_EXPLICIT_USER_INTERACTION_AUDIO_ENABLED,
                DEFAULT_MIC_REMOTE_TOGGLE_STATE) == 1;
    }

    private void setMicRemoteToggleState(boolean enabled) {
        Settings.Global.putInt(getActivity().getContentResolver(),
                RECEIVE_EXPLICIT_USER_INTERACTION_AUDIO_ENABLED,
                enabled ? 1 : 0);
    }

    @Override
    protected final void updateHardwareToggle() {
        if (mMicRemoteToggle == null) {
            return;
        }
        Preference remoteCategory = findPreference(MIC_REMOTE_CATEGORY_KEY);
        if (remoteCategory != null) {
            remoteCategory.setVisible(!mSensorToggle.isChecked());
        }
        Context themedContext = getPreferenceManager().getContext();
        Bundle b = mMicRemoteToggle.getExtras();
        b.putCharSequence(EXTRA_PREFERENCE_INFO_TEXT,
                themedContext.getString(
                        mMicRemoteToggle.isChecked() ? R.string.mic_remote_toggle_on_info_title
                                : R.string.mic_remote_toggle_off_info_title));
        b.putCharSequence(EXTRA_PREFERENCE_INFO_SUMMARY,
                themedContext.getString(
                        mMicRemoteToggle.isChecked() ? R.string.mic_remote_toggle_on_info_content
                                : R.string.mic_remote_toggle_off_info_content));
        if (!FlavorUtils.isTwoPanel(themedContext)) {
            Preference toggleInfo = findPreference(MIC_REMOTE_TOGGLE_INFO_KEY);
            if (toggleInfo != null) {
                toggleInfo.setSummary(mMicRemoteToggle.isChecked()
                        ? R.string.mic_remote_toggle_on_info_content
                        : R.string.mic_remote_toggle_off_info_content);
            }
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (SENSOR_TOGGLE_KEY.equals(preference.getKey())) {
            super.onPreferenceTreeClick(preference);
            logToggleInteracted(
                    TvSettingsEnums.PRIVACY_MICROPHONE_ACCESS,
                    ((SwitchPreference) preference).isChecked());
            return true;
        } else if (MIC_REMOTE_TOGGLE_KEY.equals(preference.getKey())) {
            setMicRemoteToggleState(mMicRemoteToggle.isChecked());
            updateHardwareToggle();
            if (getParentFragment() instanceof TwoPanelSettingsFragment) {
                ((TwoPanelSettingsFragment) getParentFragment()).refocusPreferenceForceRefresh(
                        mMicRemoteToggle, this);
            }
            logToggleInteracted(
                    TvSettingsEnums.PRIVACY_MICROPHONE_ACCESS_ON_REMOTE,
                    ((SwitchPreference) preference).isChecked());
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public int getPageId() {
        return TvSettingsEnums.PRIVACY_MICROPHONE;
    }
}
