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

package com.android.tv.settings.device.displaysound;

import android.annotation.NonNull;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.SwitchPreference;

import com.android.settingslib.core.AbstractPreferenceController;
import com.android.tv.settings.R;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Controller for the surround sound switch preferences.
 */
public class SoundFormatPreferenceController extends AbstractPreferenceController {

    private static final String TAG = "SoundFormatController";

    private int mFormatId;
    private Map<Integer, Boolean> mFormats;
    private List<Integer> mReportedFormats;
    private AudioManager mAudioManager;

    public SoundFormatPreferenceController(
            Context context,
            int formatId,
            AudioManager audioManager,
            @NonNull Map<Integer, Boolean> formats,
            @NonNull List<Integer> reportedFormats) {
        super(context);
        mFormatId = formatId;
        mAudioManager = audioManager;
        mFormats = formats;
        mReportedFormats = reportedFormats;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getPreferenceKey() {
        return AdvancedVolumeFragment.KEY_SURROUND_SOUND_FORMAT_PREFIX + mFormatId;
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        if (preference.getKey().equals(getPreferenceKey())) {
            preference.setEnabled(getFormatPreferencesEnabledState());
            ((SwitchPreference) preference).setChecked(getFormatPreferenceCheckedState());
        }
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (preference.getKey().equals(getPreferenceKey())) {
            onPreferenceClicked((SwitchPreference) preference);
        }
        return super.handlePreferenceTreeClick(preference);
    }

    /**
     * @return checked state of a surround sound format switch based on passthrough setting and
     * audio manager state for the format.
     */
    private boolean getFormatPreferenceCheckedState() {
        switch (AdvancedVolumeFragment.getSurroundPassthroughSetting(mContext)) {
            case AdvancedVolumeFragment.KEY_SURROUND_SOUND_AUTO:
                return isReportedFormat();
            case AdvancedVolumeFragment.KEY_SURROUND_SOUND_MANUAL:
                return getEnabledFormats().contains(mFormatId);
            default:
                return false;
        }
    }

    /** @return true if the format checkboxes should be enabled, i.e. in manual mode. */
    private boolean getFormatPreferencesEnabledState() {
        return AdvancedVolumeFragment.getSurroundPassthroughSetting(mContext)
                == AdvancedVolumeFragment.KEY_SURROUND_SOUND_MANUAL;
    }

    /** @return the formats that are enabled in global settings */
    HashSet<Integer> getEnabledFormats() {
        HashSet<Integer> formats = new HashSet<>();
        String enabledFormats = Settings.Global.getString(mContext.getContentResolver(),
                Settings.Global.ENCODED_SURROUND_OUTPUT_ENABLED_FORMATS);
        if (enabledFormats == null) {
            // Starting with Android P passthrough setting ALWAYS has been replaced with MANUAL.
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

    /**
     * Handler for when this particular format preference is clicked.
     */
    private void onPreferenceClicked(SwitchPreference preference) {
        final boolean enabled = preference.isChecked();
        // In case of enabling unsupported format, show a warning dialog
        if (!isReportedFormat() && enabled) {
            showWarningDialogOnEnableUnsupportedFormat(preference);
        } else {
            setSurroundFormatEnabled(mFormatId, enabled);
        }
    }

    /** @return true if the given format is reported by the device. */
    private boolean isReportedFormat() {
        return mReportedFormats.contains(mFormatId);
    }

    private void showWarningDialogOnEnableUnsupportedFormat(SwitchPreference preference) {
        new AlertDialog.Builder(mContext)
            .setTitle(R.string.surround_sound_enable_unsupported_dialog_title)
            .setMessage(R.string.surround_sound_enable_unsupported_dialog_desc)
            .setPositiveButton(
                    R.string.surround_sound_enable_unsupported_dialog_ok,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            setSurroundFormatEnabled(mFormatId, true);
                            dialog.dismiss();
                        }
                    })
            .setNegativeButton(
                    R.string.surround_sound_enable_unsupported_dialog_cancel,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            preference.setChecked(false);
                            dialog.dismiss();
                        }
                    })
            .show();
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
