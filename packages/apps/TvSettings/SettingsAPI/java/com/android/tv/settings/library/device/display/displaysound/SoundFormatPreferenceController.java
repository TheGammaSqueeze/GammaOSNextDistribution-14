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

import static com.android.tv.settings.library.device.display.displaysound.AdvancedVolumeState.KEY_FORMAT_INFO_ON_MANUAL;
import static com.android.tv.settings.library.device.display.displaysound.AdvancedVolumeState.KEY_SUPPORTED_SURROUND_SOUND;
import static com.android.tv.settings.library.device.display.displaysound.AdvancedVolumeState.KEY_SURROUND_SOUND_FORMAT_PREFIX;
import static com.android.tv.settings.library.device.display.displaysound.AdvancedVolumeState.KEY_UNSUPPORTED_SURROUND_SOUND;

import android.annotation.NonNull;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceCompatManager;
import com.android.tv.settings.library.util.AbstractPreferenceController;
import com.android.tv.settings.library.util.ResourcesUtil;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Controller for the surround sound switch preference compats.
 */
public class SoundFormatPreferenceController extends AbstractPreferenceController {
    private static final String TAG = "SoundFormatController";

    private final int mFormatId;
    private final Map<Integer, Boolean> mFormats;
    private final List<Integer> mReportedFormats;
    private final AudioManager mAudioManager;

    public SoundFormatPreferenceController(
            Context context,
            UIUpdateCallback uiUpdateCallback,
            int stateIdentifier,
            PreferenceCompatManager preferenceCompatManager,
            int formatId,
            AudioManager audioManager,
            @NonNull Map<Integer, Boolean> formats,
            @NonNull List<Integer> reportedFormats) {
        super(context, uiUpdateCallback, stateIdentifier, preferenceCompatManager);
        mFormatId = formatId;
        mAudioManager = audioManager;
        mFormats = formats;
        mReportedFormats = reportedFormats;
    }

    @Override
    protected void init() {
        // If the format is not a known surround sound format, do not create a preference
        // for it.
        String title = AdvancedVolumeState.getFormatDisplayResource(mContext, mFormatId);
        mPreferenceCompat.setVisible(!TextUtils.isEmpty(title));
        mPreferenceCompat.setTitle(title);
        mPreferenceCompat.setType(PreferenceCompat.TYPE_SWITCH);
        update();
    }

    @Override
    protected void update() {
        mPreferenceCompat.setEnabled(getFormatPreferencesEnabledState());
        mPreferenceCompat.setChecked(getFormatPreferenceCheckedState());
    }

    @Override
    public boolean handlePreferenceTreeClick(boolean status) {
        // In case of enabling unsupported format, show a warning dialog
        if (!isReportedFormat() && status) {
            showWarningDialogOnEnableUnsupportedFormat();
        } else {
            setSurroundFormatEnabled(mFormatId, status);
        }
        return true;
    }


    /**
     * @return checked state of a surround sound format switch based on passthrough setting and
     * audio manager state for the format.
     */
    private boolean getFormatPreferenceCheckedState() {
        switch (AdvancedVolumeState.getSurroundPassthroughSettingKey(mContext)) {
            case AdvancedVolumeState.KEY_SURROUND_SOUND_AUTO:
                return isReportedFormat();
            case AdvancedVolumeState.KEY_SURROUND_SOUND_MANUAL:
                return getEnabledFormats().contains(mFormatId);
            default:
                return false;
        }
    }

    private void showWarningDialogOnEnableUnsupportedFormat() {
        new AlertDialog.Builder(mContext)
                .setTitle(ResourcesUtil.getString(mContext,
                        "surround_sound_enable_unsupported_dialog_title"))
                .setMessage(ResourcesUtil.getString(mContext,
                        "surround_sound_enable_unsupported_dialog_desc"))
                .setPositiveButton(
                        ResourcesUtil.getString(mContext,
                                "surround_sound_enable_unsupported_dialog_ok"),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                setSurroundFormatEnabled(mFormatId, true);
                                dialog.dismiss();
                            }
                        })
                .setNegativeButton(
                        ResourcesUtil.getString(mContext,
                                "surround_sound_enable_unsupported_dialog_cancel"),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                mPreferenceCompat.setChecked(false);
                                mUIUpdateCallback.notifyUpdate(mStateIdentifier, mPreferenceCompat);
                                dialog.dismiss();
                            }
                        })
                .show();
    }

    /** @return true if the given format is reported by the device. */
    private boolean isReportedFormat() {
        return mReportedFormats.contains(mFormatId);
    }

    /** @return the formats that are enabled in global settings */
    HashSet<Integer> getEnabledFormats() {
        HashSet<Integer> formats = new HashSet<>();
        String enabledFormats = Settings.Global.getString(mContext.getContentResolver(),
                Settings.Global.ENCODED_SURROUND_OUTPUT_ENABLED_FORMATS);
        if (enabledFormats == null) {
            // , PreferenceCompatManager preferenceCompatManagerStarting with Android P
            // passthrough setting ALWAYS has been replaced with MANUAL.
            // In that case all formats will be enabled when in MANUAL mode.
            formats.addAll(mFormats.keySet());
        } else {
            try {
                Arrays.stream(TextUtils.split(enabledFormats, ",")).mapToInt(Integer::parseInt)
                        .forEach(formats::add);
            } catch (NumberFormatException e) {
                Log.w(TAG, "ENCODED_SURROUND_OUTPUT_ENABLED_FORMATS misformatted.", e);
            }
        }
        return formats;
    }

    /** @return true if the format checkboxes should be enabled, i.e. in manual mode. */
    private boolean getFormatPreferencesEnabledState() {
        return AdvancedVolumeState.getSurroundPassthroughSettingKey(mContext)
                .equals(AdvancedVolumeState.KEY_SURROUND_SOUND_MANUAL);
    }

    @Override
    public String[] getPreferenceKey() {
        String key = KEY_SURROUND_SOUND_FORMAT_PREFIX + mFormatId;
        String[] compoundKey = mReportedFormats.contains(mFormatId)
                ? new String[]{KEY_FORMAT_INFO_ON_MANUAL, KEY_SUPPORTED_SURROUND_SOUND, key}
                : new String[]{KEY_FORMAT_INFO_ON_MANUAL, KEY_UNSUPPORTED_SURROUND_SOUND, key};
        return compoundKey;
    }

    public boolean isAvailable() {
        return mPreferenceCompat.getVisible() == PreferenceCompat.STATUS_ON;
    }

    public PreferenceCompat getPreferenceCompat() {
        return mPreferenceCompat;
    }

    // Utility wrapper to handle multiple formats (e.g. DTS-HD and DTS-HD MA) with one setting
    private void setSurroundFormatEnabled(int audioFormat, boolean enabled) {
        mAudioManager.setSurroundFormatEnabled(audioFormat, enabled);

        if (audioFormat == AudioFormat.ENCODING_DTS_HD) {
            mAudioManager.setSurroundFormatEnabled(AudioFormat.ENCODING_DTS_HD_MA, enabled);
        } else if (audioFormat == AudioFormat.ENCODING_DTS_UHD_P1) {
            mAudioManager.setSurroundFormatEnabled(AudioFormat.ENCODING_DTS_UHD_P2, enabled);
        }
    }
}
