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

package com.android.car.settings.tts;

import static android.provider.Settings.Secure.TTS_DEFAULT_SYNTH;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TtsEngines;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class PreferredEngineOptionsPreferenceControllerTest {
    private static final TextToSpeech.EngineInfo OTHER_ENGINE_INFO = new TextToSpeech.EngineInfo();
    private static final TextToSpeech.EngineInfo CURRENT_ENGINE_INFO =
            new TextToSpeech.EngineInfo();

    static {
        OTHER_ENGINE_INFO.label = "Test Engine 1";
        OTHER_ENGINE_INFO.name = "com.android.car.settings.tts.test.Engine1";
        CURRENT_ENGINE_INFO.label = "Test Engine 2";
        CURRENT_ENGINE_INFO.name = "com.android.car.settings.tts.test.Engine2";
    }

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private PreferenceGroup mPreferenceGroup;
    private PreferredEngineOptionsPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private String mDefaultTtsEngine;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private TtsEngines mEnginesHelper;
    @Mock
    private TextToSpeech mTextToSpeech;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mDefaultTtsEngine = Settings.Secure.getString(mContext.getContentResolver(),
                TTS_DEFAULT_SYNTH);

        when(mEnginesHelper.getEngines()).thenReturn(
                Arrays.asList(OTHER_ENGINE_INFO, CURRENT_ENGINE_INFO));
        when(mEnginesHelper.getEngineInfo(OTHER_ENGINE_INFO.name)).thenReturn(OTHER_ENGINE_INFO);
        when(mEnginesHelper.getEngineInfo(CURRENT_ENGINE_INFO.name)).thenReturn(
                CURRENT_ENGINE_INFO);

        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        screen.addPreference(mPreferenceGroup);
        mPreferenceController = new TestPreferredEngineOptionsPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceGroup);
    }

    @After
    @UiThreadTest
    public void tearDown() {
        Settings.Secure.putString(mContext.getContentResolver(), TTS_DEFAULT_SYNTH,
                mDefaultTtsEngine);
    }

    @Test
    public void onCreate_populatesGroup() {
        when(mTextToSpeech.getCurrentEngine()).thenReturn(CURRENT_ENGINE_INFO.name);
        mPreferenceController.onCreate(mLifecycleOwner);
        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(2);
    }

    @Test
    public void refreshUi_currentEngineInfoSummarySet() {
        when(mTextToSpeech.getCurrentEngine()).thenReturn(CURRENT_ENGINE_INFO.name);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.refreshUi();

        assertThat(
                mPreferenceGroup.findPreference(CURRENT_ENGINE_INFO.name).getSummary()).isEqualTo(
                mContext.getString(R.string.text_to_speech_current_engine));
    }

    @Test
    public void refreshUi_otherEngineInfoSummaryEmpty() {
        when(mTextToSpeech.getCurrentEngine()).thenReturn(CURRENT_ENGINE_INFO.name);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.refreshUi();

        assertThat(mPreferenceGroup.findPreference(OTHER_ENGINE_INFO.name).getSummary()).isEqualTo(
                "");
    }

    @Test
    public void performClick_currentEngine_returnFalse() {
        when(mTextToSpeech.getCurrentEngine()).thenReturn(CURRENT_ENGINE_INFO.name);
        mPreferenceController.onCreate(mLifecycleOwner);

        Preference currentEngine = mPreferenceGroup.findPreference(CURRENT_ENGINE_INFO.name);
        assertThat(currentEngine.getOnPreferenceClickListener().onPreferenceClick(
                currentEngine)).isFalse();
    }

    @Test
    public void performClick_otherEngine_returnTrue() {
        when(mTextToSpeech.getCurrentEngine()).thenReturn(CURRENT_ENGINE_INFO.name);
        mPreferenceController.onCreate(mLifecycleOwner);

        Preference otherEngine = mPreferenceGroup.findPreference(OTHER_ENGINE_INFO.name);
        assertThat(otherEngine.getOnPreferenceClickListener().onPreferenceClick(
                otherEngine)).isTrue();
    }

    @Test
    public void performClick_otherEngine_initSuccess_changeCurrentEngine() {
        when(mTextToSpeech.getCurrentEngine()).thenReturn(CURRENT_ENGINE_INFO.name);
        Settings.Secure.putString(mContext.getContentResolver(), TTS_DEFAULT_SYNTH,
                CURRENT_ENGINE_INFO.name);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        Preference otherEngine = mPreferenceGroup.findPreference(OTHER_ENGINE_INFO.name);
        otherEngine.performClick();

        when(mTextToSpeech.getCurrentEngine()).thenReturn(OTHER_ENGINE_INFO.name);
        mPreferenceController.onUpdateEngine(TextToSpeech.SUCCESS);
        assertThat(Settings.Secure.getString(mContext.getContentResolver(), TTS_DEFAULT_SYNTH))
                .isEqualTo(OTHER_ENGINE_INFO.name);
    }

    @Test
    public void performClick_otherEngine_initFail_keepCurrentEngine() {
        when(mTextToSpeech.getCurrentEngine()).thenReturn(CURRENT_ENGINE_INFO.name);
        Settings.Secure.putString(mContext.getContentResolver(), TTS_DEFAULT_SYNTH,
                CURRENT_ENGINE_INFO.name);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        Preference otherEngine = mPreferenceGroup.findPreference(OTHER_ENGINE_INFO.name);
        otherEngine.performClick();

        when(mTextToSpeech.getCurrentEngine()).thenReturn(OTHER_ENGINE_INFO.name);
        mPreferenceController.onUpdateEngine(TextToSpeech.ERROR);
        assertThat(Settings.Secure.getString(mContext.getContentResolver(), TTS_DEFAULT_SYNTH))
                .isEqualTo(CURRENT_ENGINE_INFO.name);
    }

    private class TestPreferredEngineOptionsPreferenceController
            extends PreferredEngineOptionsPreferenceController {

        TestPreferredEngineOptionsPreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController,
                CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        TtsEngines createEnginesHelper() {
            return mEnginesHelper;
        }

        @Override
        TextToSpeech createTts(TextToSpeech.OnInitListener listener, String engine) {
            return mTextToSpeech;
        }
    }
}
