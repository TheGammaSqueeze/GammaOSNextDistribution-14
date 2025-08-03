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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TtsEngines;

import androidx.lifecycle.LifecycleOwner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.car.ui.preference.CarUiTwoActionIconPreference;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class PreferredEngineEntryPreferenceControllerTest {

    private static final TextToSpeech.EngineInfo ENGINE_INFO = new TextToSpeech.EngineInfo();
    private static final String INTENT_ACTION = "test_action";

    static {
        ENGINE_INFO.label = "Test Engine";
        ENGINE_INFO.name = "com.android.car.settings.tts.test.Engine";
    }

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private CarUiTwoActionIconPreference mPreference;
    private PreferredEngineEntryPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private TtsEngines mEnginesHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mPreference = new CarUiTwoActionIconPreference(mContext);
        mPreferenceController = new PreferredEngineEntryPreferenceController(mContext,
                "key", mFragmentController, mCarUxRestrictions, mEnginesHelper);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);

        Intent intent = new Intent(INTENT_ACTION);
        when(mEnginesHelper.getSettingsIntent(ENGINE_INFO.name)).thenReturn(intent);
        when(mEnginesHelper.getEngineInfo(ENGINE_INFO.name)).thenReturn(
                ENGINE_INFO);
        when(mEnginesHelper.getDefaultEngine()).thenReturn(ENGINE_INFO.name);
        doNothing().when(mContext).startActivity(any());
    }

    @Test
    public void performButtonClick_navigateToNextActivity() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreference.performSecondaryActionClick();

        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(
                Intent.class);
        verify(mContext).startActivity(intentArgumentCaptor.capture());

        Intent intent = intentArgumentCaptor.getValue();
        assertThat(intent.getAction()).isEqualTo(INTENT_ACTION);
    }
}
