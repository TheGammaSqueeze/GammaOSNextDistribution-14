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

package com.android.car.settings.sound;

import android.annotation.WorkerThread;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.settingslib.utils.ThreadUtils;

/** Business logic for changing the default ringtone. */
public class RingtonePreferenceController extends PreferenceController<RingtonePreference> {

    private RingtoneManager mRingtoneManager;

    public RingtonePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mRingtoneManager = new RingtoneManager(context);
    }

    @Override
    protected void onCreateInternal() {
        mRingtoneManager.setType(getPreference().getRingtoneType());
    }

    @Override
    protected Class<RingtonePreference> getPreferenceType() {
        return RingtonePreference.class;
    }

    @Override
    public void onStartInternal() {
        super.onStartInternal();
        ThreadUtils.postOnBackgroundThread(() -> updateSummary(getPreference()));
    }

    @Override
    protected boolean handlePreferenceClicked(RingtonePreference preference) {
        getFragmentController().launchFragment(createRingtonePickerFragment(preference));
        return true;
    }

    @WorkerThread
    private void updateSummary(RingtonePreference preference) {
        Uri ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(getContext(),
                preference.getRingtoneType());
        String summary = Ringtone.getTitle(getContext(), ringtoneUri,
                /* followSettingsUri= */ false, /* allowRemote= */ true);
        ThreadUtils.postOnMainThread(() -> preference.setSummary(summary));
    }

    /**
     * Prepares the fragment to launch the ringtone picker. This can be modified
     * to adjust the parameters of the ringtone picker.
     */
    private Fragment createRingtonePickerFragment(RingtonePreference ringtonePreference) {
        Fragment fragment = new RingtonePickerFragment();
        Bundle args = fragment.getArguments();
        if (args == null) {
            args = new Bundle();
            fragment.setArguments(args);
        }
        args.putCharSequence(RingtoneManager.EXTRA_RINGTONE_TITLE, ringtonePreference.getTitle());
        args.putInt(RingtoneManager.EXTRA_RINGTONE_TYPE, ringtonePreference.getRingtoneType());
        args.putBoolean(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,
                ringtonePreference.getShowSilent());

        // Allow playback in external activity.
        args.putInt(RingtoneManager.EXTRA_RINGTONE_AUDIO_ATTRIBUTES_FLAGS,
                AudioAttributes.FLAG_BYPASS_INTERRUPTION_POLICY);

        return fragment;
    }
}
