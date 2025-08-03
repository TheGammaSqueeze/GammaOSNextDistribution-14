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

package com.android.car.settings.applications.assist;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.car.drivingstate.CarUxRestrictions;
import android.content.ComponentName;
import android.content.Context;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.internal.app.AssistUtils;
import com.android.settingslib.applications.DefaultAppInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class DefaultVoiceInputPickerPreferenceControllerTest {

    private static final String TEST_PACKAGE_NAME = "com.test.package";
    private static final String TEST_SERVICE = "TestService";
    private static final String TEST_OTHER_SERVICE = "TestOtherService";
    private static final String TEST_RECOGNIZER = "TestRecognizer";
    private final int mUserId = UserHandle.myUserId();

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LogicalPreferenceGroup mPreference;
    private DefaultVoiceInputPickerPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private MockitoSession mSession;

    @Mock
    private FragmentController mMockFragmentController;
    @Mock
    private AssistUtils mMockAssistUtils;
    @Mock
    private VoiceInputInfoProvider mMockVoiceInputInfoProvider;
    @Mock
    private VoiceInputInfoProvider.VoiceInteractionInfo mMockVoiceInteractionInfo;
    @Mock
    private VoiceInputInfoProvider.VoiceRecognitionInfo mMockVoiceRecognitionInfo;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mSession = ExtendedMockito.mockitoSession().mockStatic(
                VoiceInputUtils.class, withSettings().lenient()).startMocking();

        ExtendedMockito.when(VoiceInputUtils.getCurrentService(mContext)).thenReturn(
                new ComponentName(TEST_PACKAGE_NAME, TEST_SERVICE));
        when(mMockAssistUtils.getAssistComponentForUser(mUserId)).thenReturn(
                new ComponentName(TEST_PACKAGE_NAME, TEST_SERVICE));

        mPreference = new LogicalPreferenceGroup(mContext);
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreferenceController = new DefaultVoiceInputPickerPreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController,
                mCarUxRestrictions, mMockAssistUtils, mMockVoiceInputInfoProvider);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
    }

    @After
    @UiThreadTest
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void getCandidates_voiceInteractionService_hasOneElement() {
        when(mMockVoiceInputInfoProvider.getVoiceInteractionInfoList()).thenReturn(
                Collections.singletonList(mMockVoiceInteractionInfo));
        when(mMockVoiceInteractionInfo.getComponentName()).thenReturn(
                new ComponentName(TEST_PACKAGE_NAME, TEST_SERVICE));
        when(mMockVoiceInputInfoProvider.getVoiceRecognitionInfoList()).thenReturn(
                Collections.emptyList());

        assertThat(mPreferenceController.getCandidates()).hasSize(1);
    }

    @Test
    public void getCandidates_voiceRecognitionService_hasOneElement() {
        when(mMockVoiceInputInfoProvider.getVoiceInteractionInfoList()).thenReturn(
                Collections.emptyList());
        when(mMockVoiceInputInfoProvider.getVoiceRecognitionInfoList()).thenReturn(
                Collections.singletonList(mMockVoiceRecognitionInfo));

        assertThat(mPreferenceController.getCandidates()).hasSize(1);
    }

    @Test
    public void getCandidates_oneIsSameAsAssistant_hasTwoElements() {
        when(mMockVoiceInputInfoProvider.getVoiceInteractionInfoList()).thenReturn(
                Collections.singletonList(mMockVoiceInteractionInfo));
        when(mMockVoiceInteractionInfo.getComponentName()).thenReturn(
                new ComponentName(TEST_PACKAGE_NAME, TEST_SERVICE));
        when(mMockVoiceInputInfoProvider.getVoiceRecognitionInfoList()).thenReturn(
                Collections.singletonList(mMockVoiceRecognitionInfo));

        assertThat(mPreferenceController.getCandidates()).hasSize(2);
    }

    @Test
    public void getCandidates_oneIsSameAsAssistant_sameOneIsEnabled() {
        when(mMockVoiceInputInfoProvider.getVoiceInteractionInfoList()).thenReturn(
                Collections.singletonList(mMockVoiceInteractionInfo));
        when(mMockVoiceInteractionInfo.getComponentName()).thenReturn(
                new ComponentName(TEST_PACKAGE_NAME, TEST_SERVICE));
        when(mMockVoiceInputInfoProvider.getVoiceRecognitionInfoList()).thenReturn(
                Collections.emptyList());

        List<DefaultAppInfo> defaultAppInfo = mPreferenceController.getCandidates();
        assertThat(defaultAppInfo).hasSize(1);

        assertThat(defaultAppInfo.get(0)).isNotNull();
        assertThat(defaultAppInfo.get(0).enabled).isTrue();
    }

    @Test
    public void getCandidates_oneIsSameAsAssistant_differentOneIsDisabled() {
        when(mMockVoiceInputInfoProvider.getVoiceInteractionInfoList()).thenReturn(
                Collections.singletonList(mMockVoiceInteractionInfo));
        when(mMockVoiceInteractionInfo.getComponentName()).thenReturn(
                new ComponentName(TEST_PACKAGE_NAME, TEST_OTHER_SERVICE));
        when(mMockVoiceInputInfoProvider.getVoiceRecognitionInfoList()).thenReturn(
                Collections.emptyList());

        List<DefaultAppInfo> defaultAppInfo = mPreferenceController.getCandidates();
        assertThat(defaultAppInfo).hasSize(1);

        assertThat(defaultAppInfo.get(0)).isNotNull();
        assertThat(defaultAppInfo.get(0).enabled).isFalse();
    }

    @Test
    public void getCandidates_voiceRecognitionInfo_IsEnabled() {
        when(mMockVoiceInputInfoProvider.getVoiceInteractionInfoList()).thenReturn(
                Collections.singletonList(mMockVoiceInteractionInfo));
        when(mMockVoiceInteractionInfo.getComponentName()).thenReturn(
                new ComponentName(TEST_PACKAGE_NAME, TEST_SERVICE));
        when(mMockVoiceInputInfoProvider.getVoiceRecognitionInfoList()).thenReturn(
                Collections.singletonList(mMockVoiceRecognitionInfo));
        when(mMockVoiceRecognitionInfo.getComponentName()).thenReturn(
                new ComponentName(TEST_PACKAGE_NAME, TEST_OTHER_SERVICE));

        DefaultAppInfo defaultAppInfo = null;
        for (DefaultAppInfo info : mPreferenceController.getCandidates()) {
            if (info.componentName.equals(
                    new ComponentName(TEST_PACKAGE_NAME, TEST_OTHER_SERVICE))) {
                defaultAppInfo = info;
            }
        }
        assertThat(defaultAppInfo).isNotNull();
        assertThat(defaultAppInfo.enabled).isTrue();
    }

    @Test
    public void getCurrentDefaultKey_defaultIsNull_returnsNull() {
        ExtendedMockito.when(VoiceInputUtils.getCurrentService(mContext)).thenReturn(null);

        assertThat(mPreferenceController.getCurrentDefaultKey()).isNull();
    }

    @Test
    public void getCurrentDefaultKey_defaultExists_returnsComponentName() {
        ComponentName cn = new ComponentName(TEST_PACKAGE_NAME, TEST_SERVICE);

        assertThat(mPreferenceController.getCurrentDefaultKey()).isEqualTo(cn.flattenToString());
    }

    @Test
    public void setCurrentDefault_typeVoiceInteractionInfo_setsServices() {
        when(mMockVoiceInputInfoProvider.getInfoForComponent(any()))
                .thenReturn(mMockVoiceInteractionInfo);
        when(mMockVoiceInteractionInfo.getPackageName()).thenReturn(TEST_PACKAGE_NAME);
        when(mMockVoiceInteractionInfo.getRecognitionService()).thenReturn(TEST_RECOGNIZER);
        String key = new ComponentName(TEST_PACKAGE_NAME, TEST_SERVICE).flattenToString();
        String recognizer = new ComponentName(TEST_PACKAGE_NAME, TEST_RECOGNIZER).flattenToString();
        mPreferenceController.setCurrentDefault(key);

        assertThat(Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.VOICE_INTERACTION_SERVICE)).isEqualTo(key);
        assertThat(Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.VOICE_RECOGNITION_SERVICE)).isEqualTo(recognizer);
    }

    @Test
    public void setCurrentDefault_typeVoiceRecognitionInfo_setsRecognitionService() {
        when(mMockVoiceInputInfoProvider.getInfoForComponent(any()))
                .thenReturn(mMockVoiceRecognitionInfo);
        String key = new ComponentName(TEST_PACKAGE_NAME, TEST_RECOGNIZER).flattenToString();
        mPreferenceController.setCurrentDefault(key);

        assertThat(Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.VOICE_INTERACTION_SERVICE)).isEmpty();
        assertThat(Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.VOICE_RECOGNITION_SERVICE)).isEqualTo(key);
    }
}
