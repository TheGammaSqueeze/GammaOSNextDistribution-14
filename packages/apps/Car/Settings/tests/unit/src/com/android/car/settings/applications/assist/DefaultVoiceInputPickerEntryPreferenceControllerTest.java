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

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.car.drivingstate.CarUxRestrictions;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.lifecycle.LifecycleOwner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.car.ui.preference.CarUiTwoActionIconPreference;
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

@RunWith(AndroidJUnit4.class)
public class DefaultVoiceInputPickerEntryPreferenceControllerTest {

    private static final String TEST_PACKAGE = "com.android.car.settings.testutils";
    private static final String TEST_ASSIST = "TestAssistService";
    private static final String TEST_VOICE = "TestVoiceService";
    private static final Uri ASSIST_URI = Settings.Secure.getUriFor(Settings.Secure.ASSISTANT);
    private final int mUserId = UserHandle.myUserId();

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private CarUiTwoActionIconPreference mPreference;
    private DefaultVoiceInputPickerEntryPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private MockitoSession mSession;

    @Mock
    private FragmentController mMockFragmentController;
    @Mock
    private AssistUtils mMockAssistUtils;
    @Mock
    private VoiceInputInfoProvider mMockVoiceInputInfoProvider;
    @Mock
    private VoiceInputInfoProvider.VoiceInputInfo mMockVoiceInputInfo;
    @Mock
    private ContentResolver mMockContentResolver;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mPreference = new CarUiTwoActionIconPreference(mContext);
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreferenceController = new DefaultVoiceInputPickerEntryPreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController,
                mCarUxRestrictions, mMockVoiceInputInfoProvider, mMockAssistUtils);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);

        mSession = ExtendedMockito.mockitoSession().mockStatic(
                VoiceInputUtils.class, withSettings().lenient()).startMocking();

        ExtendedMockito.when(VoiceInputUtils.getCurrentService(mContext)).thenReturn(
                new ComponentName(TEST_PACKAGE, TEST_ASSIST));
        when(mMockAssistUtils.getAssistComponentForUser(mUserId)).thenReturn(
                new ComponentName(TEST_PACKAGE, TEST_VOICE));
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void getAvailabilityStatus_sameComponents_returnsConditionallyUnavailable() {
        ExtendedMockito.when(VoiceInputUtils.getCurrentService(mContext)).thenReturn(
                new ComponentName(TEST_PACKAGE, TEST_VOICE));
        when(mMockAssistUtils.getAssistComponentForUser(mUserId)).thenReturn(
                new ComponentName(TEST_PACKAGE, TEST_VOICE));

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_sameComponents_returnsConditionallyUnavailable_zoneWrite() {
        ExtendedMockito.when(VoiceInputUtils.getCurrentService(mContext)).thenReturn(
                new ComponentName(TEST_PACKAGE, TEST_VOICE));
        when(mMockAssistUtils.getAssistComponentForUser(mUserId)).thenReturn(
                new ComponentName(TEST_PACKAGE, TEST_VOICE));
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_sameComponents_returnsConditionallyUnavailable_zoneRead() {
        ExtendedMockito.when(VoiceInputUtils.getCurrentService(mContext)).thenReturn(
                new ComponentName(TEST_PACKAGE, TEST_VOICE));
        when(mMockAssistUtils.getAssistComponentForUser(mUserId)).thenReturn(
                new ComponentName(TEST_PACKAGE, TEST_VOICE));
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_sameComponents_returnsConditionallyUnavailable_zoneHidden() {
        ExtendedMockito.when(VoiceInputUtils.getCurrentService(mContext)).thenReturn(
                new ComponentName(TEST_PACKAGE, TEST_VOICE));
        when(mMockAssistUtils.getAssistComponentForUser(mUserId)).thenReturn(
                new ComponentName(TEST_PACKAGE, TEST_VOICE));
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_bothNull_returnsConditionallyUnavailable() {
        ExtendedMockito.when(VoiceInputUtils.getCurrentService(mContext)).thenReturn(null);
        when(mMockAssistUtils.getAssistComponentForUser(mUserId)).thenReturn(null);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_bothNull_returnsConditionallyUnavailable_zoneWrite() {
        ExtendedMockito.when(VoiceInputUtils.getCurrentService(mContext)).thenReturn(null);
        when(mMockAssistUtils.getAssistComponentForUser(mUserId)).thenReturn(null);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_bothNull_returnsConditionallyUnavailable_zoneRead() {
        ExtendedMockito.when(VoiceInputUtils.getCurrentService(mContext)).thenReturn(null);
        when(mMockAssistUtils.getAssistComponentForUser(mUserId)).thenReturn(null);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_bothNull_returnsConditionallyUnavailable_zoneHidden() {
        ExtendedMockito.when(VoiceInputUtils.getCurrentService(mContext)).thenReturn(null);
        when(mMockAssistUtils.getAssistComponentForUser(mUserId)).thenReturn(null);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_differentComponents_returnsAvailable() {
        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(
                PreferenceController.AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_differentComponents_returnsAvailable_zoneWrite() {
        mPreferenceController.setAvailabilityStatusForZone("write");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_differentComponents_returnsAvailable_zoneRead() {
        mPreferenceController.setAvailabilityStatusForZone("read");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_differentComponents_returnsAvailable_zoneHidden() {
        mPreferenceController.setAvailabilityStatusForZone("hidden");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onStart_registersObserver() {
        when(mContext.getContentResolver()).thenReturn(mMockContentResolver);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        verify(mMockContentResolver).registerContentObserver(eq(ASSIST_URI), eq(false),
                any());
    }

    @Test
    public void onStop_unregistersObserver() {
        when(mContext.getContentResolver()).thenReturn(mMockContentResolver);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        mPreferenceController.onStop(mLifecycleOwner);

        verify(mMockContentResolver).unregisterContentObserver(any());
    }

    @Test
    public void onChange_changeRegisteredSetting_callsRefreshUi() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus())
                .isEqualTo(PreferenceController.AVAILABLE);

        when(mMockAssistUtils.getAssistComponentForUser(mUserId)).thenReturn(
                new ComponentName(TEST_PACKAGE, TEST_ASSIST));
        mPreferenceController.mSettingObserver.onChange(/* selfChange= */ false,
                Settings.Secure.getUriFor(Settings.Secure.ASSISTANT));

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onChange_changeRegisteredSetting_callsRefreshUi_zoneWrite() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);

        when(mMockAssistUtils.getAssistComponentForUser(mUserId)).thenReturn(
                new ComponentName(TEST_PACKAGE, TEST_ASSIST));
        mPreferenceController.mSettingObserver.onChange(/* selfChange= */ false,
                Settings.Secure.getUriFor(Settings.Secure.ASSISTANT));

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onChange_changeRegisteredSetting_callsRefreshUi_zoneRead() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);

        when(mMockAssistUtils.getAssistComponentForUser(mUserId)).thenReturn(
                new ComponentName(TEST_PACKAGE, TEST_ASSIST));
        mPreferenceController.mSettingObserver.onChange(/* selfChange= */ false,
                Settings.Secure.getUriFor(Settings.Secure.ASSISTANT));

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onChange_changeRegisteredSetting_callsRefreshUi_zoneHidden() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);

        when(mMockAssistUtils.getAssistComponentForUser(mUserId)).thenReturn(
                new ComponentName(TEST_PACKAGE, TEST_ASSIST));
        mPreferenceController.mSettingObserver.onChange(/* selfChange= */ false,
                Settings.Secure.getUriFor(Settings.Secure.ASSISTANT));

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getCurrentDefaultAppInfo_providerHasCurrentService_returnsValidDefaultAppInfo() {
        when(mMockVoiceInputInfoProvider.getInfoForComponent(any()))
                .thenReturn(mMockVoiceInputInfo);

        assertThat(mPreferenceController.getCurrentDefaultAppInfo()).isNotNull();
    }

    @Test
    public void getCurrentDefaultAppInfo_providerHasNoService_returnsNull() {
        when(mMockVoiceInputInfoProvider.getInfoForComponent(any())).thenReturn(null);
        assertThat(mPreferenceController.getCurrentDefaultAppInfo()).isNull();
    }

    @Test
    public void getSettingIntent_nullInput_returnsNull() {
        assertThat(mPreferenceController.getSettingIntent(null)).isEqualTo(null);
    }

    @Test
    public void getSettingIntent_inputIsWrongType_returnsNull() {
        DefaultAppInfo info = mock(DefaultAppInfo.class);
        assertThat(mPreferenceController.getSettingIntent(info)).isEqualTo(null);
    }

    @Test
    public void getSettingIntent_validInput_returnsIntent() {
        Intent intent = new Intent();
        DefaultVoiceInputServiceInfo info = mock(DefaultVoiceInputServiceInfo.class);
        when(info.getSettingIntent()).thenReturn(intent);

        assertThat(mPreferenceController.getSettingIntent(info)).isEqualTo(intent);
    }
}
