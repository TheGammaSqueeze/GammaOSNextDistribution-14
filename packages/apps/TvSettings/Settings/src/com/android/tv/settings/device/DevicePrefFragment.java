/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tv.settings.device;

import static com.android.tv.settings.overlay.FlavorUtils.FLAVOR_CLASSIC;
import static com.android.tv.settings.overlay.FlavorUtils.FLAVOR_TWO_PANEL;
import static com.android.tv.settings.overlay.FlavorUtils.FLAVOR_VENDOR;
import static com.android.tv.settings.overlay.FlavorUtils.FLAVOR_X;
import static com.android.tv.settings.util.InstrumentationUtils.logEntrySelected;
import static com.android.tv.settings.util.InstrumentationUtils.logToggleInteracted;

import android.app.tvsettings.TvSettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;

import androidx.annotation.Keep;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.leanback.preference.LeanbackSettingsFragmentCompat;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;

import com.android.settingslib.applications.DefaultAppInfo;
import com.android.settingslib.development.DevelopmentSettingsEnabler;
import com.android.tv.settings.LongClickPreference;
import com.android.tv.settings.MainFragment;
import com.android.tv.settings.R;
import com.android.tv.settings.SettingsPreferenceFragment;
import com.android.tv.settings.about.RebootConfirmFragment;
import com.android.tv.settings.autofill.AutofillHelper;
import com.android.tv.settings.customization.CustomizationConstants;
import com.android.tv.settings.customization.Partner;
import com.android.tv.settings.customization.PartnerPreferencesMerger;
import com.android.tv.settings.device.eco.PowerAndEnergyFragment;
import com.android.tv.settings.inputmethod.InputMethodHelper;
import com.android.tv.settings.overlay.FlavorUtils;
import com.android.tv.settings.privacy.PrivacyToggle;
import com.android.tv.settings.privacy.SensorFragment;
import com.android.tv.settings.system.SecurityFragment;
import com.android.tv.settings.util.SliceUtils;
import com.android.tv.twopanelsettings.TwoPanelSettingsFragment;
import com.android.tv.twopanelsettings.slices.SlicePreference;

import java.util.List;

/**
 * The "Device Preferences" screen in TV settings.
 */
@Keep
public class DevicePrefFragment extends SettingsPreferenceFragment implements
        LongClickPreference.OnLongClickListener {
    @VisibleForTesting
    static final String KEY_DEVELOPER = "developer";
    @VisibleForTesting
    static final String KEY_CAST_SETTINGS = "cast";
    private static final String KEY_CAST_SETTINGS_SLICE = "cast_settings";
    @VisibleForTesting
    static final String KEY_KEYBOARD = "keyboard";
    private static final String TAG = "DeviceFragment";
    private static final String KEY_USAGE = "usageAndDiag";
    private static final String KEY_INPUTS = "inputs";
    private static final String KEY_SOUNDS = "sound_effects";
    private static final String KEY_SOUNDS_SWITCH = "sound_effects_switch";
    private static final String KEY_GOOGLE_SETTINGS = "google_settings";
    private static final String KEY_HOME_SETTINGS = "home";
    private static final String KEY_REBOOT = "reboot";
    private static final String KEY_MIC = "microphone";
    private static final String KEY_CAMERA = "camera";
    private static final String KEY_FASTPAIR_SETTINGS_SLICE = "fastpair_slice";
    private static final String KEY_OVERLAY_INTERNAL_SETTINGS_SLICE = "overlay_internal";
    private static final String KEY_ASSISTANT_BROADCAST = "assistant_broadcast";
    private static final String KEY_AMBIENT_SETTINGS = "ambient_settings";
    private static final String KEY_ENERGY_SAVER = "energysaver";
    private static final String KEY_POWER_AND_ENERGY = "power_and_energy";
    private static final String RES_TOP_LEVEL_ASSISTANT_SLICE_URI = "top_level_assistant_slice_uri";

    private Preference mSoundsPref;
    private TwoStatePreference mSoundsSwitchPref;
    private boolean mInputSettingNeeded;
    private PackageManager mPm;
    private AudioManager mAudioManager;

    private int getPreferenceScreenResId() {
        if (isRestricted()) {
            return R.xml.device_restricted;
        }
        switch (FlavorUtils.getFlavor(getContext())) {
            case FLAVOR_CLASSIC:
                return R.xml.device;
            case FLAVOR_TWO_PANEL:
                return R.xml.device_two_panel;
            case FLAVOR_X:
                return R.xml.device_x;
            case FLAVOR_VENDOR:
                return R.xml.device_vendor;
            default:
                return R.xml.device;
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(getPreferenceScreenResId(), null);
        if (Partner.getInstance(getContext()).isCustomizationPackageProvided()) {
            PartnerPreferencesMerger.mergePreferences(
                    getContext(),
                    getPreferenceScreen(),
                    CustomizationConstants.DEVICE_SCREEN
            );
        }
        mSoundsPref = findPreference(KEY_SOUNDS);
        mSoundsSwitchPref = findPreference(KEY_SOUNDS_SWITCH);
        if (mSoundsSwitchPref != null) {
            mSoundsSwitchPref.setChecked(getSoundEffectsEnabled());
        }

        final Preference inputPref = findPreference(KEY_INPUTS);
        if (inputPref != null) {
            inputPref.setVisible(mInputSettingNeeded);
        }
        final LongClickPreference restartPref = findPreference(KEY_REBOOT);
        if (restartPref != null) {
            restartPref.setLongClickListener(this);
        }

        PrivacyToggle.MIC_TOGGLE.preparePreferenceWithSensorFragment(getContext(),
                findPreference(KEY_MIC), SensorFragment.TOGGLE_EXTRA);
        PrivacyToggle.CAMERA_TOGGLE.preparePreferenceWithSensorFragment(getContext(),
                findPreference(KEY_CAMERA), SensorFragment.TOGGLE_EXTRA);

        final Preference assistantBroadcastPreference = findPreference(KEY_ASSISTANT_BROADCAST);
        if (assistantBroadcastPreference != null && SliceUtils.isSettingsSliceEnabled(
                getContext(),
                ((SlicePreference) assistantBroadcastPreference).getUri(),
                RES_TOP_LEVEL_ASSISTANT_SLICE_URI)) {
            assistantBroadcastPreference.setVisible(true);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        final TvInputManager manager = (TvInputManager) getContext().getSystemService(
                Context.TV_INPUT_SERVICE);
        if (manager != null) {
            for (final TvInputInfo input : manager.getTvInputList()) {
                if (input.isPassthroughInput()) {
                    mInputSettingNeeded = true;
                }
            }
        }
        mAudioManager = getContext().getSystemService(AudioManager.class);
        if (SystemProperties.getInt("ro.hdmi.device_type", 0) == 4) {
            mInputSettingNeeded = true;
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mPm = context.getPackageManager();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        updateDeveloperOptions();
        updateSounds();
        updateGoogleSettings();
        updateCastSettings();
        updateInternalSettings();
        updateFastpairSettings();
        updateKeyboardAutofillSettings();
        updateAmbientSettings();
        updatePowerAndEnergySettings();
        hideIfIntentUnhandled(findPreference(KEY_HOME_SETTINGS));
        hideIfIntentUnhandled(findPreference(KEY_CAST_SETTINGS));
        hideIfIntentUnhandled(findPreference(KEY_USAGE));
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        switch (preference.getKey()) {
            case KEY_HOME_SETTINGS:
                logEntrySelected(TvSettingsEnums.PREFERENCES_HOME_SCREEN);
                break;
            case KEY_GOOGLE_SETTINGS:
                logEntrySelected(TvSettingsEnums.PREFERENCES_ASSISTANT);
                break;
            case KEY_CAST_SETTINGS:
                logEntrySelected(TvSettingsEnums.PREFERENCES_CHROMECAST_SHELL);
                break;
            case KEY_REBOOT:
                logEntrySelected(TvSettingsEnums.SYSTEM_REBOOT);
                break;
            case KEY_SOUNDS_SWITCH:
                if (mSoundsSwitchPref != null) {
                    logToggleInteracted(TvSettingsEnums.DISPLAY_SOUND_SYSTEM_SOUNDS,
                            mSoundsSwitchPref.isChecked());
                    setSoundEffectsEnabled(mSoundsSwitchPref.isChecked());
                }
                break;
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public boolean onPreferenceLongClick(Preference preference) {
        if (TextUtils.equals(preference.getKey(), KEY_REBOOT)) {
            logEntrySelected(TvSettingsEnums.SYSTEM_REBOOT);
            Fragment fragment = getCallbackFragment();
            if (fragment instanceof LeanbackSettingsFragmentCompat) {
                ((LeanbackSettingsFragmentCompat) fragment).startImmersiveFragment(
                        RebootConfirmFragment.newInstance(true /* safeMode */));
                return true;
            } else if (fragment instanceof TwoPanelSettingsFragment) {
                ((TwoPanelSettingsFragment) fragment).startImmersiveFragment(
                        RebootConfirmFragment.newInstance(true /* safeMode */));
                return true;
            }
        }
        return false;
    }

    public boolean getSoundEffectsEnabled() {
        return Settings.System.getInt(getActivity().getContentResolver(),
                Settings.System.SOUND_EFFECTS_ENABLED, 1) != 0;
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

    private void hideIfIntentUnhandled(Preference preference) {
        if (preference == null || !preference.isVisible()) {
            return;
        }
        preference.setVisible(
                MainFragment.systemIntentIsHandled(getContext(), preference.getIntent()) != null);
    }

    private boolean isRestricted() {
        return SecurityFragment.isRestrictedProfileInEffect(getContext());
    }

    @VisibleForTesting
    void updateDeveloperOptions() {
        final Preference developerPref = findPreference(KEY_DEVELOPER);
        if (developerPref == null) {
            return;
        }

        developerPref.setVisible(DevelopmentSettingsEnabler.isDevelopmentSettingsEnabled(
                getContext()));
    }

    private void updateSounds() {
        if (mSoundsPref == null) {
            return;
        }

        Intent soundIntent = new Intent(MainFragment.ACTION_SOUND);
        final ResolveInfo info = MainFragment.systemIntentIsHandled(getContext(), soundIntent);
        if (info != null) {
            mSoundsPref.setVisible(false);
        }
    }

    private void updateGoogleSettings() {
        final Preference googleSettingsPref = findPreference(KEY_GOOGLE_SETTINGS);
        if (googleSettingsPref != null) {
            final ResolveInfo info = MainFragment.systemIntentIsHandled(getContext(),
                    googleSettingsPref.getIntent());
            googleSettingsPref.setVisible(info != null);
            if (info != null && info.activityInfo != null) {
                googleSettingsPref.setIcon(
                        info.activityInfo.loadIcon(getContext().getPackageManager()));
                googleSettingsPref.setTitle(
                        info.activityInfo.loadLabel(getContext().getPackageManager()));
            }
        }
    }

    @VisibleForTesting
    void updateCastSettings() {
        final Preference castPref = findPreference(KEY_CAST_SETTINGS);
        final SlicePreference castSlicePref = findPreference(KEY_CAST_SETTINGS_SLICE);
        if (castPref != null) {
            final ResolveInfo info = MainFragment.systemIntentIsHandled(
                    getContext(), castPref.getIntent());
            if (info != null) {
                try {
                    final Context targetContext = getContext()
                            .createPackageContext(info.resolvePackageName != null
                                    ? info.resolvePackageName : info.activityInfo.packageName, 0);
                    castPref.setIcon(targetContext.getDrawable(info.getIconResource()));
                } catch (Resources.NotFoundException | PackageManager.NameNotFoundException
                        | SecurityException e) {
                    Log.e(TAG, "Cast settings icon not found", e);
                }
                castPref.setTitle(info.activityInfo.loadLabel(getContext().getPackageManager()));
            }
        }
        if (castSlicePref != null) {
            if (!SliceUtils.isSliceProviderValid(getContext(), castSlicePref.getUri())
                    || FlavorUtils.getFeatureFactory(getContext()).getBasicModeFeatureProvider()
                    .isBasicMode(getContext())) {
                castSlicePref.setVisible(false);
            }
        }
    }

    private void updateInternalSettings() {
        final SlicePreference internalSlicePref = findPreference(
                KEY_OVERLAY_INTERNAL_SETTINGS_SLICE);
        if (internalSlicePref != null) {
            internalSlicePref.setVisible(
                    SliceUtils.isSliceProviderValid(getContext(), internalSlicePref.getUri())
                            && SliceUtils.isSettingsSliceEnabled(getContext(),
                            internalSlicePref.getUri(), null));
        }
    }

    @VisibleForTesting
    void updateFastpairSettings() {
        final SlicePreference fastpairSlicePref = findPreference(KEY_FASTPAIR_SETTINGS_SLICE);
        if (fastpairSlicePref != null) {
            if (SliceUtils.isSliceProviderValid(getContext(), fastpairSlicePref.getUri())) {
                fastpairSlicePref.setVisible(true);
            }
        }
    }

    @VisibleForTesting
    void updateKeyboardAutofillSettings() {
        final Preference keyboardPref = findPreference(KEY_KEYBOARD);

        List<DefaultAppInfo> candidates = AutofillHelper.getAutofillCandidates(getContext(),
                mPm, UserHandle.myUserId());

        // Switch title depends on whether there is autofill
        if (candidates.isEmpty()) {
            keyboardPref.setTitle(R.string.system_keyboard);
        } else {
            keyboardPref.setTitle(R.string.system_keyboard_autofill);
        }

        CharSequence summary = "";
        // append current keyboard to summary
        String defaultImId = InputMethodHelper.getDefaultInputMethodId(getContext());
        if (!TextUtils.isEmpty(defaultImId)) {
            InputMethodInfo info = InputMethodHelper.findInputMethod(defaultImId,
                    InputMethodHelper.getEnabledSystemInputMethodList(getContext()));
            if (info != null) {
                summary = info.loadLabel(getContext().getPackageManager());
            }

        }
        // append current autofill to summary
        DefaultAppInfo appInfo = AutofillHelper.getCurrentAutofill(getContext(), candidates);
        if (appInfo != null) {
            CharSequence autofillInfo = appInfo.loadLabel();
            if (summary.length() > 0) {
                getContext().getString(R.string.string_concat, summary, autofillInfo);
            } else {
                summary = autofillInfo;
            }
        }
        keyboardPref.setSummary(summary);
    }

    private void updateAmbientSettings() {
        final SlicePreference ambientSlicePref = findPreference(KEY_AMBIENT_SETTINGS);
        if (ambientSlicePref != null) {
            if (SliceUtils.isSliceProviderValid(getContext(), ambientSlicePref.getUri())) {
                ambientSlicePref.setVisible(true);
            }
        }
    }

    private void updatePowerAndEnergySettings() {
        final Preference energySaverPref = findPreference(KEY_ENERGY_SAVER);
        final Preference powerAndEnergyPref = findPreference(KEY_POWER_AND_ENERGY);

        if (energySaverPref == null || powerAndEnergyPref == null) {
            return;
        }

        boolean showPowerAndEnergy =
                !PowerAndEnergyFragment.hasOnlyEnergySaverPreference(getContext());
        powerAndEnergyPref.setVisible(showPowerAndEnergy);
        energySaverPref.setVisible(!showPowerAndEnergy);
    }

    @Override
    protected int getPageId() {
        return TvSettingsEnums.SYSTEM;
    }
}
