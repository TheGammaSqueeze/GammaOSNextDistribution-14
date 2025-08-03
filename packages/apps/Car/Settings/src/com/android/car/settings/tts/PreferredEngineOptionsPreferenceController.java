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

import static android.provider.Settings.Secure.TTS_DEFAULT_SYNTH;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TtsEngines;
import android.text.TextUtils;

import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;
import com.android.car.ui.preference.CarUiPreference;

/** Populates the possible tts engines to set as the preferred engine. */
public class PreferredEngineOptionsPreferenceController extends
        PreferenceController<PreferenceGroup> {

    private static final Logger LOG = new Logger(PreferredEngineOptionsPreferenceController.class);

    private final TtsEngines mEnginesHelper;
    private String mPreviousEngine;
    private TextToSpeech mTts;

    public PreferredEngineOptionsPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mEnginesHelper = createEnginesHelper();
    }

    @Override
    protected Class<PreferenceGroup> getPreferenceType() {
        return PreferenceGroup.class;
    }

    /**
     * Creates the initial TTS object and constructs the related preferences when underlying
     * fragment is created.
     */
    @Override
    protected void onCreateInternal() {
        mTts = createTts(/* listener= */ null, /* engine= */ null);

        for (TextToSpeech.EngineInfo engine : mEnginesHelper.getEngines()) {
            CarUiPreference preference = new CarUiPreference(getContext());
            preference.setKey(engine.name);
            preference.setTitle(engine.label);
            preference.setShowChevron(false);
            preference.setOnPreferenceClickListener(pref -> {
                TextToSpeech.EngineInfo current = mEnginesHelper.getEngineInfo(
                        mTts.getCurrentEngine());
                if (areEnginesEqual(current, engine)) {
                    return false;
                }
                updateDefaultEngine(engine.name);
                return true;
            });
            getPreference().addPreference(preference);
        }
    }

    /** Cleans up the TTS object and clears the preferences representing the TTS engines. */
    @Override
    protected void onDestroyInternal() {
        if (mTts != null) {
            mTts.shutdown();
            mTts = null;
        }
    }

    @Override
    protected void updateState(PreferenceGroup preference) {
        TextToSpeech.EngineInfo current = mEnginesHelper.getEngineInfo(mTts.getCurrentEngine());
        for (int i = 0; i < preference.getPreferenceCount(); i++) {
            CarUiPreference pref = (CarUiPreference) preference.getPreference(i);
            if (areEnginesEqual(current, pref.getKey(), pref.getTitle())) {
                pref.setSummary(R.string.text_to_speech_current_engine);
            } else {
                pref.setSummary("");
            }
        }
    }

    private boolean areEnginesEqual(TextToSpeech.EngineInfo engine1,
            TextToSpeech.EngineInfo engine2) {
        return areEnginesEqual(engine1, engine2.name, engine2.label);
    }

    private boolean areEnginesEqual(TextToSpeech.EngineInfo engine, CharSequence name,
            CharSequence label) {
        return TextUtils.equals(engine.name, name) && TextUtils.equals(engine.label, label);
    }

    private void updateDefaultEngine(String engineName) {
        LOG.d("Updating default synth to : " + engineName);

        // Keep track of the previous engine that was being used. So that
        // we can reuse the previous engine.
        //
        // Note that if TextToSpeech#getCurrentEngine is not null, it means at
        // the very least that we successfully bound to the engine service.
        mPreviousEngine = mTts.getCurrentEngine();

        // Step 1: Shut down the existing TTS engine.
        LOG.i("Shutting down current tts engine");
        if (mTts != null) {
            mTts.shutdown();
        }

        // Step 2: Connect to the new TTS engine.
        // Step 3 is continued on #onUpdateEngine (below) which is called when
        // the app binds successfully to the engine.
        LOG.i("Updating engine : Attempting to connect to engine: " + engineName);
        mTts = createTts(status -> {
            if (isStarted()) {
                onUpdateEngine(status);
                refreshUi();
            }
        }, engineName);
        LOG.i("Success");
    }

    /**
     * We have now bound to the TTS engine the user requested. We will attempt to check voice data
     * for the engine if we successfully bound to it, or revert to the previous engine if we
     * didn't.
     */
    @VisibleForTesting
    void onUpdateEngine(int status) {
        if (status == TextToSpeech.SUCCESS) {
            LOG.d("Updating engine: Successfully bound to the engine: "
                    + mTts.getCurrentEngine());
            Settings.Secure.putString(getContext().getContentResolver(), TTS_DEFAULT_SYNTH,
                    mTts.getCurrentEngine());
        } else {
            LOG.d("Updating engine: Failed to bind to engine, reverting.");
            if (mPreviousEngine != null) {
                // This is guaranteed to at least bind, since mPreviousEngine would be
                // null if the previous bind to this engine failed.
                mTts = createTts(/* listener= */ null, mPreviousEngine);
            }
            mPreviousEngine = null;
        }
    }

    @VisibleForTesting
    TtsEngines createEnginesHelper() {
        return new TtsEngines(getContext());
    }

    @VisibleForTesting
    TextToSpeech createTts(TextToSpeech.OnInitListener listener, String engine) {
        return new TextToSpeech(getContext(), listener, engine);
    }
}
