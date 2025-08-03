/*
 * Copyright (C) 2020 The Android Open Source Project
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
import static com.android.tv.settings.util.InstrumentationUtils.logEntrySelected;
import static com.android.tv.settings.util.InstrumentationUtils.logToggleInteracted;

import android.app.tvsettings.TvSettingsEnums;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreference;

import com.android.internal.annotations.VisibleForTesting;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.tv.settings.PreferenceControllerFragment;
import com.android.tv.settings.R;
import com.android.tv.settings.RadioPreference;
import com.android.tv.settings.overlay.FlavorUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The "Advanced sound settings" screen in TV Settings.
 */
@Keep
public class AdvancedVolumeFragment extends PreferenceControllerFragment {
    static final String KEY_ADVANCED_SOUND_OPTION = "advanced_sound_settings_option";
    static final String KEY_SURROUND_SOUND_AUTO = "surround_sound_auto";
    static final String KEY_SURROUND_SOUND_NONE = "surround_sound_none";
    static final String KEY_SURROUND_SOUND_MANUAL = "surround_sound_manual";
    static final String KEY_SURROUND_SOUND_FORMAT_PREFIX = "surround_sound_format_";
    static final String KEY_SURROUND_SOUND_FORMAT_INFO_PREFIX = "surround_sound_format_info_";
    static final String KEY_SUPPORTED_SURROUND_SOUND = "supported_formats";
    static final String KEY_UNSUPPORTED_SURROUND_SOUND = "unsupported_formats";
    static final String KEY_FORMAT_INFO = "surround_sound_format_info";
    static final String KEY_SHOW_HIDE_FORMAT_INFO = "surround_sound_show_hide_format_info";
    static final String KEY_ENABLED_FORMATS = "enabled_formats";
    static final String KEY_DISABLED_FORMATS = "disabled_formats";

    static final int[] SURROUND_SOUND_DISPLAY_ORDER = {
            AudioFormat.ENCODING_AC3, AudioFormat.ENCODING_E_AC3, AudioFormat.ENCODING_DOLBY_TRUEHD,
            AudioFormat.ENCODING_E_AC3_JOC, AudioFormat.ENCODING_DOLBY_MAT,
            AudioFormat.ENCODING_DTS, AudioFormat.ENCODING_DTS_HD, AudioFormat.ENCODING_DTS_UHD_P1,
            AudioFormat.ENCODING_DRA
    };

    private Map<Integer, Boolean> mFormats;
    private List<Integer> mReportedFormats;
    private AudioManager mAudioManager;
    private List<AbstractPreferenceController> mPreferenceControllers;
    private PreferenceCategory mSupportedFormatsPreferenceCategory;
    private PreferenceCategory mUnsupportedFormatsPreferenceCategory;
    private PreferenceCategory mFormatsInfoPreferenceCategory;
    private PreferenceCategory mEnabledFormatsPreferenceCategory;
    private PreferenceCategory mDisabledFormatsPreferenceCategory;

    @Override
    public void onAttach(Context context) {
        mAudioManager = getAudioManager();
        mFormats = mAudioManager.getSurroundFormats();
        mReportedFormats = mAudioManager.getReportedSurroundFormats();

        // For the first time, when the user has never changed the surround sound setting, enable
        // all the surround sound formats supported by android and audio device, and disable the
        // formats supported by Android device, but not by audio device.
        String formatString = Settings.Global.getString(getContext().getContentResolver(),
                Settings.Global.ENCODED_SURROUND_OUTPUT_ENABLED_FORMATS);
        if (formatString == null) {
            for (int format : mFormats.keySet()) {
                mAudioManager.setSurroundFormatEnabled(format, mReportedFormats.contains(format));
            }
        }
        super.onAttach(context);
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.advanced_sound;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.advanced_sound, null /* key */);

        String surroundSoundSettingKey = getSurroundPassthroughSetting(getContext());
        selectRadioPreference(findPreference(surroundSoundSettingKey));

        // Do not show sidebar info texts in case of 1 panel settings.
        if (FlavorUtils.getFlavor(getContext()) != FLAVOR_CLASSIC) {
            createInfoFragments();
        }

        createFormatInfoPreferences();
        createFormatPreferences();
        if (surroundSoundSettingKey == KEY_SURROUND_SOUND_MANUAL) {
            showFormatPreferences();
        } else {
            hideFormatPreferences();
        }
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        mPreferenceControllers = new ArrayList<>(mFormats.size());
        for (Map.Entry<Integer, Boolean> format : mFormats.entrySet()) {
            mPreferenceControllers.add(new SoundFormatPreferenceController(context,
                    format.getKey() /*formatId*/, mAudioManager, mFormats, mReportedFormats));
        }
        return mPreferenceControllers;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        final String key = preference.getKey();
        if (key == null) {
            return super.onPreferenceTreeClick(preference);
        }

        if (preference instanceof RadioPreference) {
            selectRadioPreference(preference);

            switch (key) {
                case KEY_SURROUND_SOUND_AUTO: {
                    logEntrySelected(
                            TvSettingsEnums.DISPLAY_SOUND_ADVANCED_SOUNDS_SELECT_FORMATS_AUTO);
                    mAudioManager.setEncodedSurroundMode(
                            Settings.Global.ENCODED_SURROUND_OUTPUT_AUTO);
                    hideFormatPreferences();
                    break;
                }
                case KEY_SURROUND_SOUND_NONE: {
                    logEntrySelected(
                            TvSettingsEnums.DISPLAY_SOUND_ADVANCED_SOUNDS_SELECT_FORMATS_NONE);
                    mAudioManager.setEncodedSurroundMode(
                            Settings.Global.ENCODED_SURROUND_OUTPUT_NEVER);
                    hideFormatPreferences();
                    break;
                }
                case KEY_SURROUND_SOUND_MANUAL: {
                    logEntrySelected(
                            TvSettingsEnums.DISPLAY_SOUND_ADVANCED_SOUNDS_SELECT_FORMATS_MANUAL);
                    mAudioManager.setEncodedSurroundMode(
                            Settings.Global.ENCODED_SURROUND_OUTPUT_MANUAL);
                    showFormatPreferences();
                    break;
                }
                default:
                    throw new IllegalArgumentException("Unknown surround sound pref value: "
                            + key);
            }
            updateFormatPreferencesStates();
        }

        if (key.equals(KEY_SHOW_HIDE_FORMAT_INFO)) {
            if (preference.getTitle().equals(
                    getContext().getString(R.string.surround_sound_hide_formats))) {
                hideFormatInfoPreferences();
                preference.setTitle(R.string.surround_sound_show_formats);
            } else {
                showFormatInfoPreferences();
                preference.setTitle(R.string.surround_sound_hide_formats);
            }
        }

        if (key.contains(KEY_SURROUND_SOUND_FORMAT_INFO_PREFIX)) {
            if (preference.getParent() == mEnabledFormatsPreferenceCategory) {
                showToast(R.string.surround_sound_enabled_format_info_clicked);
            } else {
                showToast(R.string.surround_sound_disabled_format_info_clicked);
            }
        }

        return super.onPreferenceTreeClick(preference);
    }

    @VisibleForTesting
    AudioManager getAudioManager() {
        return getContext().getSystemService(AudioManager.class);
    }

    private PreferenceGroup getPreferenceGroup() {
        return (PreferenceGroup) findPreference(KEY_ADVANCED_SOUND_OPTION);
    }

    private void selectRadioPreference(Preference preference) {
        final RadioPreference radioPreference = (RadioPreference) preference;
        radioPreference.setChecked(true);
        radioPreference.clearOtherRadioPreferences(getPreferenceGroup());
    }

    /** Creates titles and switches for each surround sound format. */
    private void createFormatPreferences() {
        mSupportedFormatsPreferenceCategory = createPreferenceCategory(
                R.string.surround_sound_supported_title, KEY_SUPPORTED_SURROUND_SOUND);
        getPreferenceScreen().addPreference(mSupportedFormatsPreferenceCategory);
        mUnsupportedFormatsPreferenceCategory = createPreferenceCategory(
                R.string.surround_sound_unsupported_title, KEY_UNSUPPORTED_SURROUND_SOUND);
        getPreferenceScreen().addPreference(mUnsupportedFormatsPreferenceCategory);

        for (int formatId : SURROUND_SOUND_DISPLAY_ORDER) {
            if (mFormats.containsKey(formatId)) {
                boolean enabled = mFormats.get(formatId);

                // If the format is not a known surround sound format, do not create a preference
                // for it.
                int titleId = getFormatDisplayResourceId(formatId);
                if (titleId == -1) {
                    continue;
                }
                final SwitchPreference pref = new SwitchPreference(getContext()) {
                    @Override
                    public void onBindViewHolder(PreferenceViewHolder holder) {
                        super.onBindViewHolder(holder);
                        // Enabling the view will ensure that the preference is focusable even if it
                        // the preference is disabled. This allows the user to scroll down over the
                        // disabled surround sound formats and see them all.
                        holder.itemView.setEnabled(true);
                    }
                };
                pref.setTitle(titleId);
                pref.setKey(KEY_SURROUND_SOUND_FORMAT_PREFIX + formatId);
                pref.setChecked(enabled);
                if (getEntryId(formatId) != -1) {
                    pref.setOnPreferenceClickListener(
                            preference -> {
                                logToggleInteracted(getEntryId(formatId), pref.isChecked());
                                return false;
                            }
                    );
                }
                if (mReportedFormats.contains(formatId)) {
                    mSupportedFormatsPreferenceCategory.addPreference(pref);
                } else {
                    mUnsupportedFormatsPreferenceCategory.addPreference(pref);
                }
            }
        }
    }

    /** Creates titles and preferences for each surround sound format. */
    private void createFormatInfoPreferences() {
        mFormatsInfoPreferenceCategory = createPreferenceCategory(
                R.string.surround_sound_format_info, KEY_FORMAT_INFO);
        getPreferenceScreen().addPreference(mFormatsInfoPreferenceCategory);

        Preference pref = createPreference(
                R.string.surround_sound_show_formats, KEY_SHOW_HIDE_FORMAT_INFO);
        mFormatsInfoPreferenceCategory.addPreference(pref);

        mEnabledFormatsPreferenceCategory = createPreferenceCategory(
                R.string.surround_sound_enabled_formats, KEY_ENABLED_FORMATS);
        mFormatsInfoPreferenceCategory.addPreference(mEnabledFormatsPreferenceCategory);

        mDisabledFormatsPreferenceCategory = createPreferenceCategory(
                R.string.surround_sound_disabled_formats, KEY_DISABLED_FORMATS);
        mFormatsInfoPreferenceCategory.addPreference(mDisabledFormatsPreferenceCategory);

        for (int formatId : SURROUND_SOUND_DISPLAY_ORDER) {
            if (mFormats.containsKey(formatId)) {
                // If the format is not a known surround sound format, do not create a preference
                // for it.
                int titleId = getFormatDisplayResourceId(formatId);
                if (titleId == -1) {
                    continue;
                }
                pref = createPreference(titleId, KEY_SURROUND_SOUND_FORMAT_INFO_PREFIX + formatId);
                if (mReportedFormats.contains(formatId)) {
                    mEnabledFormatsPreferenceCategory.addPreference(pref);
                } else {
                    mDisabledFormatsPreferenceCategory.addPreference(pref);
                }
            }
        }
        hideFormatInfoPreferences();
    }

    private void showFormatPreferences() {
        getPreferenceScreen().addPreference(mSupportedFormatsPreferenceCategory);
        getPreferenceScreen().addPreference(mUnsupportedFormatsPreferenceCategory);
        updateFormatPreferencesStates();
        // hide the formats info section.
        getPreferenceScreen().removePreference(mFormatsInfoPreferenceCategory);
    }

    private void hideFormatPreferences() {
        getPreferenceScreen().removePreference(mSupportedFormatsPreferenceCategory);
        getPreferenceScreen().removePreference(mUnsupportedFormatsPreferenceCategory);
        updateFormatPreferencesStates();
        // show the formats info section.
        getPreferenceScreen().addPreference(mFormatsInfoPreferenceCategory);
    }

    private void showFormatInfoPreferences() {
        mFormatsInfoPreferenceCategory.addPreference(mEnabledFormatsPreferenceCategory);
        mFormatsInfoPreferenceCategory.addPreference(mDisabledFormatsPreferenceCategory);
    }

    private void hideFormatInfoPreferences() {
        mFormatsInfoPreferenceCategory.removePreference(mEnabledFormatsPreferenceCategory);
        mFormatsInfoPreferenceCategory.removePreference(mDisabledFormatsPreferenceCategory);
    }

    private void showToast(int resId) {
        Toast.makeText(getContext(), getContext().getString(resId), Toast.LENGTH_SHORT)
                .show();
    }

    private PreferenceCategory createPreferenceCategory(int titleResourceId, String key) {
        PreferenceCategory preferenceCategory = new PreferenceCategory(getContext());
        preferenceCategory.setTitle(titleResourceId);
        preferenceCategory.setKey(key);
        return preferenceCategory;
    }

    private Preference createPreference(int titleResourceId, String key) {
        Preference preference = new Preference(getContext());
        preference.setTitle(titleResourceId);
        preference.setKey(key);
        return preference;
    }

    /**
     * @return the display id for each surround sound format.
     */
    private int getFormatDisplayResourceId(int formatId) {
        switch (formatId) {
            case AudioFormat.ENCODING_AC3:
                return R.string.surround_sound_format_ac3;
            case AudioFormat.ENCODING_E_AC3:
                return R.string.surround_sound_format_e_ac3;
            case AudioFormat.ENCODING_DTS:
                return R.string.surround_sound_format_dts;
            case AudioFormat.ENCODING_DTS_HD:
                return R.string.surround_sound_format_dts_hd;
            case AudioFormat.ENCODING_DTS_UHD_P1:
                return R.string.surround_sound_format_dts_uhd;
            case AudioFormat.ENCODING_DOLBY_TRUEHD:
                return R.string.surround_sound_format_dolby_truehd;
            case AudioFormat.ENCODING_E_AC3_JOC:
                return R.string.surround_sound_format_e_ac3_joc;
            case AudioFormat.ENCODING_DOLBY_MAT:
                return R.string.surround_sound_format_dolby_mat;
            case AudioFormat.ENCODING_DRA:
                return R.string.surround_sound_format_dra;
            default:
                return -1;
        }
    }

    private void updateFormatPreferencesStates() {
        for (AbstractPreferenceController controller : mPreferenceControllers) {
            Preference preference = findPreference(
                    controller.getPreferenceKey());
            if (preference != null) {
                controller.updateState(preference);
            }
        }
    }

    static String getSurroundPassthroughSetting(Context context) {
        final int value = Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.ENCODED_SURROUND_OUTPUT,
                Settings.Global.ENCODED_SURROUND_OUTPUT_AUTO);

        switch (value) {
            case Settings.Global.ENCODED_SURROUND_OUTPUT_MANUAL:
                return KEY_SURROUND_SOUND_MANUAL;
            case Settings.Global.ENCODED_SURROUND_OUTPUT_NEVER:
                return KEY_SURROUND_SOUND_NONE;
            case Settings.Global.ENCODED_SURROUND_OUTPUT_AUTO:
            default:
                return KEY_SURROUND_SOUND_AUTO;
        }
    }

    private int getEntryId(int formatId) {
        switch (formatId) {
            case AudioFormat.ENCODING_AC4:
                return TvSettingsEnums.DISPLAY_SOUND_ADVANCED_SOUNDS_DAC4;
            case AudioFormat.ENCODING_E_AC3_JOC:
                return TvSettingsEnums.DISPLAY_SOUND_ADVANCED_SOUNDS_DADDP;
            case AudioFormat.ENCODING_AC3:
                return TvSettingsEnums.DISPLAY_SOUND_ADVANCED_SOUNDS_DD;
            case AudioFormat.ENCODING_E_AC3:
                return TvSettingsEnums.DISPLAY_SOUND_ADVANCED_SOUNDS_DDP;
            case AudioFormat.ENCODING_DTS:
                return TvSettingsEnums.DISPLAY_SOUND_ADVANCED_SOUNDS_DTS;
            case AudioFormat.ENCODING_DTS_HD:
                return TvSettingsEnums.DISPLAY_SOUND_ADVANCED_SOUNDS_DTSHD;
            case AudioFormat.ENCODING_DTS_UHD_P1:
                return TvSettingsEnums.DISPLAY_SOUND_ADVANCED_SOUNDS_DTSUHD;
            case AudioFormat.ENCODING_AAC_LC:
                return TvSettingsEnums.DISPLAY_SOUND_ADVANCED_SOUNDS_AAC;
            case AudioFormat.ENCODING_DOLBY_TRUEHD:
                return TvSettingsEnums.DISPLAY_SOUND_ADVANCED_SOUNDS_DTHD;
            case AudioFormat.ENCODING_DRA:
                return TvSettingsEnums.DISPLAY_SOUND_ADVANCED_SOUNDS_DRA;
            default:
                return -1;
        }
    }

    private void createInfoFragments() {
        Preference autoPreference = findPreference(KEY_SURROUND_SOUND_AUTO);
        autoPreference.setFragment(AdvancedVolumeInfo.AutoInfoFragment.class.getName());

        Preference manualPreference = findPreference(KEY_SURROUND_SOUND_MANUAL);
        manualPreference.setFragment(AdvancedVolumeInfo.ManualInfoFragment.class.getName());
    }

    @Override
    protected int getPageId() {
        return TvSettingsEnums.DISPLAY_SOUND_ADVANCED_SOUNDS;
    }
}
