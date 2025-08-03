/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car.settings.tts;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TtsEngines;

import androidx.annotation.VisibleForTesting;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;
import com.android.car.ui.preference.CarUiTwoActionIconPreference;

/** Business logic to set the summary for the preferred engine entry setting. */
public class PreferredEngineEntryPreferenceController extends
        PreferenceController<CarUiTwoActionIconPreference> {

    private static final Logger LOG = new Logger(PreferredEngineEntryPreferenceController.class);
    private TtsEngines mEnginesHelper;

    public PreferredEngineEntryPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        this(context, preferenceKey, fragmentController, uxRestrictions, new TtsEngines(context));
    }

    @VisibleForTesting
    public PreferredEngineEntryPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions,
            TtsEngines enginesHelper) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mEnginesHelper = enginesHelper;
    }

    @Override
    protected Class<CarUiTwoActionIconPreference> getPreferenceType() {
        return CarUiTwoActionIconPreference.class;
    }

    @Override
    protected void onCreateInternal() {
        getPreference().setOnSecondaryActionClickListener(() -> {
            TextToSpeech.EngineInfo info = mEnginesHelper.getEngineInfo(
                    mEnginesHelper.getDefaultEngine());
            if (info == null) {
                LOG.e("EngineInfo is null");
                return;
            }
            Intent subSettingsIntent = mEnginesHelper.getSettingsIntent(info.name);
            if (subSettingsIntent != null) {
                getContext().startActivity(subSettingsIntent);
            } else {
                LOG.e("subSettingsIntent is null");
            }
        });
    }

    @Override
    protected void updateState(CarUiTwoActionIconPreference preference) {
        TextToSpeech.EngineInfo info = mEnginesHelper.getEngineInfo(
                mEnginesHelper.getDefaultEngine());
        if (info == null) {
            LOG.e("EngineInfo is null");
            return;
        }
        preference.setSummary(info.label);
    }
}
