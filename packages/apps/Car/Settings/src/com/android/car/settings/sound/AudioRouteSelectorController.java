/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static android.car.media.CarAudioManager.AUDIO_FEATURE_DYNAMIC_ROUTING;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.car.feature.Flags;

import androidx.annotation.VisibleForTesting;
import androidx.preference.ListPreference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;

import java.util.ArrayList;
import java.util.List;

/**
 * Controls the audio destination selection.
 */
public class AudioRouteSelectorController extends PreferenceController<ListPreference> {
    private static final Logger LOG = new Logger(AudioRouteSelectorController.class);
    private AudioRoutesManager mAudioRoutesManager;
    private AudioRouteItem mAudioRouteItem;
    private int mUsage;
    private AudioRoutesManager.AudioZoneConfigUpdateListener mUpdateListener =
            () -> updateState(getPreference());

    public AudioRouteSelectorController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mUsage = context.getResources().getInteger(R.integer.audio_route_selector_usage);
        mAudioRoutesManager = new AudioRoutesManager(context, mUsage);
        mAudioRoutesManager.setUpdateListener(mUpdateListener);
    }

    @Override
    protected Class<ListPreference> getPreferenceType() {
        return ListPreference.class;
    }

    @Override
    protected void onCreateInternal() {
        super.onCreateInternal();
        updatePreferenceOptions();

    }

    @Override
    protected boolean handlePreferenceChanged(ListPreference preference,
            Object newValue) {
        String newAddress = (String) newValue;
        String activeDeviceAddress = mAudioRoutesManager.getActiveDeviceAddress();
        if (newAddress.equals(activeDeviceAddress)) {
            return true;
        }
        mAudioRouteItem = mAudioRoutesManager.updateAudioRoute(newAddress);
        return true;
    }

    @Override
    protected int getDefaultAvailabilityStatus() {
        if (!Flags.carAudioDynamicDevices()) {
            return CONDITIONALLY_UNAVAILABLE;
        }
        if (getContext().getResources().getBoolean(R.bool.config_allow_audio_destination_selection)
                && mAudioRoutesManager.getCarAudioManager()
                .isAudioFeatureEnabled(AUDIO_FEATURE_DYNAMIC_ROUTING)) {
            return AVAILABLE;
        }
        return CONDITIONALLY_UNAVAILABLE;
    }

    private void updatePreferenceOptions() {
        if (getAvailabilityStatus() == CONDITIONALLY_UNAVAILABLE) {
            return;
        }
        List<String> entryValues = mAudioRoutesManager.getAudioRouteList();
        List<String> entries = new ArrayList<>();
        entryValues.stream().forEach(
                v -> entries.add(mAudioRoutesManager.getAudioRouteItemMap().get(v).getName()));

        getPreference().setTitle(getContext().getString(R.string.audio_route_selector_title));
        getPreference().setEntries(entries.toArray(new CharSequence[entries.size()]));
        getPreference().setEntryValues(entryValues.toArray(new CharSequence[entries.size()]));
        String entryValue = mAudioRoutesManager.getActiveDeviceAddress();
        CharSequence entry = mAudioRoutesManager.getAudioRouteItemMap().get(
                mAudioRoutesManager.getActiveDeviceAddress()).getName();
        getPreference().setValue(entryValue);
        getPreference().setSummary(entry);
    }

    @Override
    protected void updateState(ListPreference preference) {
        super.updateState(preference);
        if (mAudioRouteItem != null) {
            getPreference().setValue(mAudioRouteItem.getAddress());
            getPreference().setSummary(mAudioRouteItem.getName());
        }
    }

    @Override
    protected void onDestroyInternal() {
        mAudioRoutesManager.tearDown();
    }

    @VisibleForTesting
    void setAudioRoutesManager(AudioRoutesManager audioRoutesManager) {
        mAudioRoutesManager = audioRoutesManager;
    }
}
