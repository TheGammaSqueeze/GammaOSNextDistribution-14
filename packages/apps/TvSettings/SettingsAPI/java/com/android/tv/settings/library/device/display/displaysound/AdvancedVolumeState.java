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

package com.android.tv.settings.library.device.display.displaysound;

import static com.android.tv.settings.library.overlay.FlavorUtils.FLAVOR_CLASSIC;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;

import com.android.tv.settings.library.ManagerUtil;
import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceControllerState;
import com.android.tv.settings.library.overlay.FlavorUtils;
import com.android.tv.settings.library.util.AbstractPreferenceController;
import com.android.tv.settings.library.util.LibUtils;
import com.android.tv.settings.library.util.PreferenceCompatUtils;
import com.android.tv.settings.library.util.ResourcesUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * State to provide data for rendering advanced volume screen.
 */
public class AdvancedVolumeState extends PreferenceControllerState {
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
    static final String KEY_FORMAT_INFO_ON_MANUAL = "surround_sound_format_info_on_manual";

    static final int[] SURROUND_SOUND_DISPLAY_ORDER = {
            AudioFormat.ENCODING_AC3, AudioFormat.ENCODING_E_AC3, AudioFormat.ENCODING_DOLBY_TRUEHD,
            AudioFormat.ENCODING_E_AC3_JOC, AudioFormat.ENCODING_DOLBY_MAT,
            AudioFormat.ENCODING_DTS, AudioFormat.ENCODING_DTS_HD, AudioFormat.ENCODING_DTS_UHD_P1,
            AudioFormat.ENCODING_DRA
    };

    private Map<Integer, Boolean> mFormats;
    private List<Integer> mReportedFormats;
    private AudioManager mAudioManager;
    private PreferenceCompat mSupportedFormatsPreferenceCategory;
    private PreferenceCompat mUnsupportedFormatsPreferenceCategory;
    private PreferenceCompat mFormatsInfoPreferenceCategory;
    private PreferenceCompat mFormatsInfoOnManualPreferenceCategory;
    private PreferenceCompat mEnabledFormatsPreferenceCategory;
    private PreferenceCompat mDisabledFormatsPreferenceCategory;
    private PreferenceCompat mSurroundSoundCategory;
    private PreferenceCompat mSurroundSoundAuto;
    private PreferenceCompat mSurroundSoundNone;
    private PreferenceCompat mSurroundSoundManual;
    private PreferenceCompat mShowHideFormatInfo;

    public AdvancedVolumeState(Context context,
            UIUpdateCallback callback) {
        super(context, callback);
    }

    @Override
    public int getStateIdentifier() {
        return ManagerUtil.STATE_ADVANCED_VOLUME;
    }


    @Override
    public void onAttach() {
        super.onAttach();
        mAudioManager = getAudioManager();
        mFormats = mAudioManager.getSurroundFormats();
        mReportedFormats = mAudioManager.getReportedSurroundFormats();

        // For the first time, when the user has never changed the surround sound setting, enable
        // all the surround sound formats supported by android and audio device, and disable the
        // formats supported by Android device, but not by audio device.
        String formatString = Settings.Global.getString(mContext.getContentResolver(),
                Settings.Global.ENCODED_SURROUND_OUTPUT_ENABLED_FORMATS);
        if (formatString == null) {
            for (int format : mFormats.keySet()) {
                mAudioManager.setSurroundFormatEnabled(format, mReportedFormats.contains(format));
            }
        }
    }

    @Override
    public void onCreate(Bundle extras) {
        super.onCreate(extras);

        mSurroundSoundCategory = mPreferenceCompatManager.getOrCreatePrefCompat(
                KEY_ADVANCED_SOUND_OPTION);
        mSurroundSoundCategory.setType(PreferenceCompat.TYPE_PREFERENCE_CATEGORY);

        mSurroundSoundAuto = mPreferenceCompatManager.getOrCreatePrefCompat(
                new String[]{KEY_ADVANCED_SOUND_OPTION, KEY_SURROUND_SOUND_AUTO});
        mSurroundSoundAuto.setTitle(ResourcesUtil.getString(mContext, "surround_sound_auto_title"));
        mSurroundSoundAuto.setType(PreferenceCompat.TYPE_RADIO);
        mSurroundSoundAuto.setRadioGroup(KEY_ADVANCED_SOUND_OPTION);

        mSurroundSoundNone = mPreferenceCompatManager.getOrCreatePrefCompat(
                new String[]{KEY_ADVANCED_SOUND_OPTION, KEY_SURROUND_SOUND_NONE});
        mSurroundSoundNone.setTitle(ResourcesUtil.getString(mContext, "surround_sound_none_title"));
        mSurroundSoundNone.setType(PreferenceCompat.TYPE_RADIO);
        mSurroundSoundNone.setRadioGroup(KEY_ADVANCED_SOUND_OPTION);

        mSurroundSoundManual = mPreferenceCompatManager.getOrCreatePrefCompat(
                new String[]{KEY_ADVANCED_SOUND_OPTION, KEY_SURROUND_SOUND_MANUAL});
        mSurroundSoundManual.setTitle(
                ResourcesUtil.getString(mContext, "surround_sound_manual_title"));
        mSurroundSoundManual.setType(PreferenceCompat.TYPE_RADIO);
        mSurroundSoundManual.setRadioGroup(KEY_ADVANCED_SOUND_OPTION);

        mSurroundSoundCategory.addChildPrefCompat(mSurroundSoundAuto);
        mSurroundSoundCategory.addChildPrefCompat(mSurroundSoundNone);
        mSurroundSoundCategory.addChildPrefCompat(mSurroundSoundManual);

        PreferenceCompat surroundSoundSettingsCompat = getSurroundPassthroughSetting(mContext);
        selectSurroundSoundRadioPreference(surroundSoundSettingsCompat);

        // Do not show sidebar info texts in case of 1 panel settings.
        if (FlavorUtils.getFlavor(mContext) != FLAVOR_CLASSIC) {
            createInfoFragments();
        }

        createFormatInfoPreferences();
        createFormatInfoPreferencesOnManual();
        if (mSurroundSoundManual.getChecked() == PreferenceCompat.STATUS_ON) {
            showFormatInfoOnManual();
        } else {
            hideFormatInfoOnManual();
        }
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        return null;
    }

    @Override
    public boolean onPreferenceTreeClick(String[] key, boolean status) {
        PreferenceCompat pref = mPreferenceCompatManager.getPrefCompat(key);
        if (pref == null) {
            return false;
        }
        if (pref.getType() == PreferenceCompat.TYPE_RADIO) {
            selectSurroundSoundRadioPreference(pref);

            if (pref.getKey().length != 2) {
                return false;
            }
            switch (key[1]) {
                case KEY_SURROUND_SOUND_AUTO: {
                    mAudioManager.setEncodedSurroundMode(
                            Settings.Global.ENCODED_SURROUND_OUTPUT_AUTO);
                    hideFormatInfoOnManual();
                    break;
                }
                case KEY_SURROUND_SOUND_NONE: {
                    mAudioManager.setEncodedSurroundMode(
                            Settings.Global.ENCODED_SURROUND_OUTPUT_NEVER);
                    hideFormatInfoOnManual();
                    break;
                }
                case KEY_SURROUND_SOUND_MANUAL: {
                    mAudioManager.setEncodedSurroundMode(
                            Settings.Global.ENCODED_SURROUND_OUTPUT_MANUAL);
                    showFormatInfoOnManual();
                    break;
                }
                default:
                    throw new IllegalArgumentException("Unknown surround sound pref value: "
                            + key);
            }
            updateFormatPreferencesStates();
            return true;
        }

        if (key.length == 2 && key[0].equals(KEY_FORMAT_INFO) && key[1].equals(
                KEY_SHOW_HIDE_FORMAT_INFO)) {
            if (pref.getTitle().equals(
                    ResourcesUtil.getString(mContext, "surround_sound_hide_formats"))) {
                pref.setTitle(ResourcesUtil.getString(mContext, "surround_sound_show_formats"));
                hideFormatInfo();
            } else {
                pref.setTitle(ResourcesUtil.getString(mContext, "surround_sound_hide_formats"));
                showFormatInfo();
            }
            return true;
        }

        if (key.length == 3 && key[0].equals(KEY_FORMAT_INFO) && key[2].contains(
                KEY_SURROUND_SOUND_FORMAT_INFO_PREFIX)) {
            if (PreferenceCompatUtils.isParent(pref, mEnabledFormatsPreferenceCategory)) {
                LibUtils.showToast(mContext, "surround_sound_enabled_format_info_clicked");
            } else {
                LibUtils.showToast(mContext, "surround_sound_disabled_format_info_clicked");
            }
            return true;
        }

        return super.onPreferenceTreeClick(key, status);
    }


    AudioManager getAudioManager() {
        return mContext.getSystemService(AudioManager.class);
    }

    private void selectSurroundSoundRadioPreference(PreferenceCompat preferenceCompat) {
        preferenceCompat.setChecked(true);
        mSurroundSoundCategory.getChildPrefCompats().stream()
                .filter(radioPref -> radioPref != preferenceCompat)
                .forEach(radioPref -> radioPref.setChecked(false));
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mSurroundSoundCategory);
    }

    /** Creates titles and switches for each surround sound format. */
    private void createFormatInfoPreferencesOnManual() {
        mFormatsInfoOnManualPreferenceCategory = createPreferenceCategory(
                "", new String[]{KEY_FORMAT_INFO_ON_MANUAL}
        );
        mSupportedFormatsPreferenceCategory = createPreferenceCategory(
                "surround_sound_supported_title",
                new String[]{KEY_FORMAT_INFO_ON_MANUAL, KEY_SUPPORTED_SURROUND_SOUND});
        mUnsupportedFormatsPreferenceCategory = createPreferenceCategory(
                "surround_sound_unsupported_title",
                new String[]{KEY_FORMAT_INFO_ON_MANUAL, KEY_UNSUPPORTED_SURROUND_SOUND});

        mFormatsInfoOnManualPreferenceCategory.addChildPrefCompat(
                mSupportedFormatsPreferenceCategory);
        mFormatsInfoOnManualPreferenceCategory.addChildPrefCompat(
                mUnsupportedFormatsPreferenceCategory);
        List<AbstractPreferenceController> preferenceControllers = new ArrayList<>();
        for (int formatId : SURROUND_SOUND_DISPLAY_ORDER) {
            if (mFormats.containsKey(formatId)) {
                SoundFormatPreferenceController soundFormatPC = new SoundFormatPreferenceController(
                        mContext,
                        mUIUpdateCallback,
                        getStateIdentifier(),
                        mPreferenceCompatManager,
                        formatId /*formatId*/, mAudioManager, mFormats, mReportedFormats);
                preferenceControllers.add(soundFormatPC);
                soundFormatPC.initAndNotify();
                if (soundFormatPC.isAvailable()) {
                    if (mReportedFormats.contains(formatId)) {
                        mSupportedFormatsPreferenceCategory.addChildPrefCompat(
                                soundFormatPC.getPreferenceCompat());
                    } else {
                        mUnsupportedFormatsPreferenceCategory.addChildPrefCompat(
                                soundFormatPC.getPreferenceCompat());
                    }
                }
            }
        }
        mPreferenceControllers.addAll(preferenceControllers);
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(),
                mFormatsInfoOnManualPreferenceCategory);
    }

    /** Creates titles and preferences for each surround sound format. */
    private void createFormatInfoPreferences() {
        mFormatsInfoPreferenceCategory = createPreferenceCategory(
                "surround_sound_format_info", new String[]{KEY_FORMAT_INFO});

        mShowHideFormatInfo = createPreference("surround_sound_show_formats",
                new String[]{KEY_FORMAT_INFO, KEY_SHOW_HIDE_FORMAT_INFO});
        mFormatsInfoPreferenceCategory.addChildPrefCompat(mShowHideFormatInfo);

        mEnabledFormatsPreferenceCategory = createPreferenceCategory(
                "surround_sound_enabled_formats",
                new String[]{KEY_FORMAT_INFO, KEY_ENABLED_FORMATS});
        mFormatsInfoPreferenceCategory.addChildPrefCompat(mEnabledFormatsPreferenceCategory);

        mDisabledFormatsPreferenceCategory = createPreferenceCategory(
                "surround_sound_disabled_formats",
                new String[]{KEY_FORMAT_INFO, KEY_DISABLED_FORMATS});
        mFormatsInfoPreferenceCategory.addChildPrefCompat(mDisabledFormatsPreferenceCategory);

        for (int formatId : SURROUND_SOUND_DISPLAY_ORDER) {
            if (mFormats.containsKey(formatId)) {
                String title = getFormatDisplayResource(mContext, formatId);
                if (TextUtils.isEmpty(title)) {
                    continue;
                }
                String key = KEY_SURROUND_SOUND_FORMAT_INFO_PREFIX + formatId;
                String[] compoundKey = mReportedFormats.contains(formatId)
                        ? new String[]{KEY_FORMAT_INFO, KEY_ENABLED_FORMATS, key}
                        : new String[]{KEY_FORMAT_INFO, KEY_DISABLED_FORMATS, key};
                PreferenceCompat pref = createPreference(title, compoundKey);
                pref.setTitle(title);
                if (mReportedFormats.contains(formatId)) {
                    mEnabledFormatsPreferenceCategory.addChildPrefCompat(pref);
                } else {
                    mDisabledFormatsPreferenceCategory.addChildPrefCompat(pref);
                }
            }
        }
        hideFormatInfo();
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mFormatsInfoPreferenceCategory);
    }

    private void notifyUpdateFormatInfo() {
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(),
                mFormatsInfoOnManualPreferenceCategory);
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mFormatsInfoPreferenceCategory);
    }

    private void showFormatInfoOnManual() {
        mSupportedFormatsPreferenceCategory.setVisible(true);
        mUnsupportedFormatsPreferenceCategory.setVisible(true);
        updateFormatPreferencesStates();
        // hide the formats info section.
        mFormatsInfoPreferenceCategory.setVisible(false);
        notifyUpdateFormatInfo();
    }

    private void hideFormatInfoOnManual() {
        mSupportedFormatsPreferenceCategory.setVisible(false);
        mUnsupportedFormatsPreferenceCategory.setVisible(false);
        updateFormatPreferencesStates();
        // show the formats info section.
        mFormatsInfoPreferenceCategory.setVisible(true);
        notifyUpdateFormatInfo();
    }

    private void showFormatInfo() {
        mEnabledFormatsPreferenceCategory.setVisible(true);
        mDisabledFormatsPreferenceCategory.setVisible(true);
        notifyUpdateFormatInfo();
    }

    private void hideFormatInfo() {
        mEnabledFormatsPreferenceCategory.setVisible(false);
        mDisabledFormatsPreferenceCategory.setVisible(false);
        notifyUpdateFormatInfo();
    }


    /**
     * @return the display id for each surround sound format.
     */
    static String getFormatDisplayResource(Context context, int formatId) {
        switch (formatId) {
            case AudioFormat.ENCODING_AC3:
                return ResourcesUtil.getString(context, "surround_sound_format_ac3");
            case AudioFormat.ENCODING_E_AC3:
                return ResourcesUtil.getString(context, "surround_sound_format_e_ac3");
            case AudioFormat.ENCODING_DTS:
                return ResourcesUtil.getString(context, "surround_sound_format_dts");
            case AudioFormat.ENCODING_DTS_HD:
                return ResourcesUtil.getString(context, "surround_sound_format_dts_hd");
            case AudioFormat.ENCODING_DTS_UHD_P1:
                return ResourcesUtil.getString(context, "surround_sound_format_dts_uhd");
            case AudioFormat.ENCODING_DOLBY_TRUEHD:
                return ResourcesUtil.getString(context, "surround_sound_format_dolby_truehd");
            case AudioFormat.ENCODING_E_AC3_JOC:
                return ResourcesUtil.getString(context, "surround_sound_format_e_ac3_joc");
            case AudioFormat.ENCODING_DOLBY_MAT:
                return ResourcesUtil.getString(context, "surround_sound_format_dolby_mat");
            case AudioFormat.ENCODING_DRA:
                return ResourcesUtil.getString(context, "surround_sound_format_dra");
            default:
                return "";
        }
    }

    private PreferenceCompat createPreferenceCategory(String valueName, String[] key) {
        PreferenceCompat preferenceCategory = mPreferenceCompatManager.getOrCreatePrefCompat(key);
        preferenceCategory.setType(PreferenceCompat.TYPE_PREFERENCE_CATEGORY);
        preferenceCategory.setTitle(ResourcesUtil.getString(mContext, valueName));
        return preferenceCategory;
    }

    private PreferenceCompat createPreference(String titleResourceId, String[] key) {
        PreferenceCompat preference = mPreferenceCompatManager.getOrCreatePrefCompat(key);
        preference.setTitle(ResourcesUtil.getString(mContext, titleResourceId));
        return preference;
    }

    static String getSurroundPassthroughSettingKey(Context context) {
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

    private void updateFormatPreferencesStates() {
        for (AbstractPreferenceController controller : mPreferenceControllers) {
            if (controller instanceof SoundFormatPreferenceController) {
                ((SoundFormatPreferenceController) controller).update();
            }
        }
    }


    private void createInfoFragments() {
        PreferenceCompat autoPreference = mPreferenceCompatManager
                .getOrCreatePrefCompat(KEY_SURROUND_SOUND_AUTO);

        PreferenceCompat manualPreference = mPreferenceCompatManager
                .getOrCreatePrefCompat(KEY_SURROUND_SOUND_MANUAL);
    }

    private PreferenceCompat getSurroundPassthroughSetting(Context context) {
        final int value = Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.ENCODED_SURROUND_OUTPUT,
                Settings.Global.ENCODED_SURROUND_OUTPUT_AUTO);

        switch (value) {
            case Settings.Global.ENCODED_SURROUND_OUTPUT_MANUAL:
                return mSurroundSoundManual;
            case Settings.Global.ENCODED_SURROUND_OUTPUT_NEVER:
                return mSurroundSoundNone;
            case Settings.Global.ENCODED_SURROUND_OUTPUT_AUTO:
            default:
                return mSurroundSoundAuto;
        }
    }
}
