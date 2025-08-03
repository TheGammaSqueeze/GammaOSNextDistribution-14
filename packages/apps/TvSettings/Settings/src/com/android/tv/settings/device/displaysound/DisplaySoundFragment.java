/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.tv.settings.device.displaysound.DisplaySoundUtils.getMatchContentDynamicRangeStatus;
import static com.android.tv.settings.device.displaysound.DisplaySoundUtils.setMatchContentDynamicRangeStatus;
import static com.android.tv.settings.overlay.FlavorUtils.FLAVOR_CLASSIC;
import static com.android.tv.settings.overlay.FlavorUtils.FLAVOR_TWO_PANEL;
import static com.android.tv.settings.overlay.FlavorUtils.FLAVOR_VENDOR;
import static com.android.tv.settings.overlay.FlavorUtils.FLAVOR_X;
import static com.android.tv.settings.util.InstrumentationUtils.logToggleInteracted;

import android.app.tvsettings.TvSettingsEnums;
import android.content.ContentResolver;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.hdmi.HdmiControlManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Display;

import androidx.annotation.Keep;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;
import androidx.preference.TwoStatePreference;

import com.android.tv.settings.R;
import com.android.tv.settings.SettingsPreferenceFragment;
import com.android.tv.settings.overlay.FlavorUtils;
import com.android.tv.settings.util.ResolutionSelectionUtils;
import com.android.tv.settings.util.SliceUtils;
import com.android.tv.twopanelsettings.slices.SlicePreference;

import java.util.Objects;

/**
 * The "Display & sound" screen in TV Settings.
 */
@Keep
public class DisplaySoundFragment extends SettingsPreferenceFragment implements
        DisplayManager.DisplayListener {

    static final String KEY_SOUND_EFFECTS = "sound_effects";
    private static final String KEY_CEC = "cec";
    private static final String KEY_DEFAULT_AUDIO_OUTPUT_SETTINGS_SLICE =
            "default_audio_output_settings";
    private static final String KEY_FRAMERATE = "match_content_frame_rate";
    private static final String KEY_RESOLUTION_TITLE = "resolution_selection";
    private static final String KEY_DYNAMIC_RANGE = "match_content_dynamic_range";

    private AudioManager mAudioManager;
    private HdmiControlManager mHdmiControlManager;

    private Display.Mode mCurrentMode = null;
    private DisplayManager mDisplayManager;

    public static DisplaySoundFragment newInstance() {
        return new DisplaySoundFragment();
    }

    public static boolean getSoundEffectsEnabled(ContentResolver contentResolver) {
        return Settings.System.getInt(contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, 1)
                != 0;
    }

    @Override
    public void onAttach(Context context) {
        mAudioManager = context.getSystemService(AudioManager.class);
        mHdmiControlManager = context.getSystemService(HdmiControlManager.class);
        super.onAttach(context);
    }

    private int getPreferenceScreenResId() {
        switch (FlavorUtils.getFlavor(getContext())) {
            case FLAVOR_CLASSIC:
            case FLAVOR_TWO_PANEL:
                return R.xml.display_sound;
            case FLAVOR_X:
            case FLAVOR_VENDOR:
                return R.xml.display_sound_x;
            default:
                return R.xml.display_sound;
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(getPreferenceScreenResId(), null);

        final TwoStatePreference soundPref = findPreference(KEY_SOUND_EFFECTS);
        soundPref.setChecked(getSoundEffectsEnabled());
        updateCecPreference();
        updateDefaultAudioOutputSettings();

        if (!getResources().getBoolean(R.bool.enable_framerate_config)) {
            removePreference(findPreference(KEY_FRAMERATE));
        }

        mDisplayManager = getDisplayManager();
        Display display = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
        if (display.getSystemPreferredDisplayMode() != null) {
            mDisplayManager.registerDisplayListener(this, null);
            mCurrentMode = mDisplayManager.getGlobalUserPreferredDisplayMode();
            updateResolutionTitleDescription(ResolutionSelectionUtils.modeToString(
                    mCurrentMode, getContext()));
        } else {
            removePreference(findPreference(KEY_RESOLUTION_TITLE));
        }

        SwitchPreference dynamicRangePreference = findPreference(KEY_DYNAMIC_RANGE);
        if (mDisplayManager.getSupportedHdrOutputTypes().length == 0) {
            removePreference(dynamicRangePreference);
            return;
        }
        // Do not show sidebar info texts in case of 1 panel settings.
        if (FlavorUtils.getFlavor(getContext()) != FLAVOR_CLASSIC) {
            createInfoFragments();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mDisplayManager.unregisterDisplayListener(this);
    }


    @Override
    public void onResume() {
        super.onResume();
        // Update the subtitle of CEC setting when navigating back to this page.
        updateCecPreference();

        SwitchPreference dynamicRangePreference = findPreference(KEY_DYNAMIC_RANGE);
        if (dynamicRangePreference != null) {
            dynamicRangePreference.setChecked(getMatchContentDynamicRangeStatus(mDisplayManager));
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (TextUtils.equals(preference.getKey(), KEY_SOUND_EFFECTS)) {
            final TwoStatePreference soundPref = (TwoStatePreference) preference;
            logToggleInteracted(TvSettingsEnums.DISPLAY_SOUND_SYSTEM_SOUNDS, soundPref.isChecked());
            setSoundEffectsEnabled(soundPref.isChecked());
        }
        if (TextUtils.equals(preference.getKey(), KEY_DYNAMIC_RANGE)) {
            final SwitchPreference dynamicPref = (SwitchPreference) preference;
            setMatchContentDynamicRangeStatus(mDisplayManager, dynamicPref.isChecked());
        }
        return super.onPreferenceTreeClick(preference);
    }

    private boolean getSoundEffectsEnabled() {
        return getSoundEffectsEnabled(getActivity().getContentResolver());
    }

    private void setSoundEffectsEnabled(boolean enabled) {
        if (enabled) {
            mAudioManager.loadSoundEffects();
        } else {
            mAudioManager.unloadSoundEffects();
        }
        Settings.System.putInt(getActivity().getContentResolver(),
                Settings.System.SOUND_EFFECTS_ENABLED, enabled ? 1 : 0);
    }

    private void updateCecPreference() {
        Preference cecPreference = findPreference(KEY_CEC);
        if (cecPreference instanceof SlicePreference
                && SliceUtils.isSliceProviderValid(
                        getContext(), ((SlicePreference) cecPreference).getUri())) {
            ContentResolver resolver = getContext().getContentResolver();
            boolean cecEnabled = mHdmiControlManager.getHdmiCecEnabled()
                    == HdmiControlManager.HDMI_CEC_CONTROL_ENABLED;
            cecPreference.setSummary(cecEnabled ? R.string.enabled : R.string.disabled);
            cecPreference.setVisible(true);
        } else {
            cecPreference.setVisible(false);
        }
    }

    private void updateDefaultAudioOutputSettings() {
        final SlicePreference defaultAudioOutputSlicePref = findPreference(
                KEY_DEFAULT_AUDIO_OUTPUT_SETTINGS_SLICE);
        if (defaultAudioOutputSlicePref != null) {
            defaultAudioOutputSlicePref.setVisible(
                    SliceUtils.isSliceProviderValid(getContext(),
                        defaultAudioOutputSlicePref.getUri())
                    && SliceUtils.isSettingsSliceEnabled(getContext(),
                        defaultAudioOutputSlicePref.getUri(), null));
        }
    }

    @Override
    protected int getPageId() {
        return TvSettingsEnums.DISPLAY_SOUND;
    }

    @Override
    public void onDisplayAdded(int displayId) {}

    @Override
    public void onDisplayRemoved(int displayId) {}

    @Override
    public void onDisplayChanged(int displayId) {
        Display.Mode newMode = mDisplayManager.getGlobalUserPreferredDisplayMode();
        if (!Objects.equals(mCurrentMode, newMode)) {
            updateResolutionTitleDescription(
                    ResolutionSelectionUtils.modeToString(newMode, getContext()));
            mCurrentMode = newMode;
        }
    }

    @VisibleForTesting
    DisplayManager getDisplayManager() {
        return getContext().getSystemService(DisplayManager.class);
    }

    private void updateResolutionTitleDescription(String summary) {
        Preference titlePreference = findPreference(KEY_RESOLUTION_TITLE);
        if (titlePreference != null) {
            titlePreference.setSummary(summary);
        }
    }

    private void removePreference(Preference preference) {
        if (preference != null) {
            getPreferenceScreen().removePreference(preference);
        }
    }

    private void createInfoFragments() {
        Preference dynamicRangePref = findPreference(KEY_DYNAMIC_RANGE);
        if (dynamicRangePref != null) {
            dynamicRangePref.setFragment(
                    PreferredDynamicRangeInfo.MatchContentDynamicRangeInfoFragment.class.getName());
        }
    }
}
