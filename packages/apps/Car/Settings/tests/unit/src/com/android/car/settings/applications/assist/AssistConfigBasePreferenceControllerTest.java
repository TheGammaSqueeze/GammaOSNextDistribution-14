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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.SwitchPreference;
import androidx.preference.TwoStatePreference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.internal.app.AssistUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class AssistConfigBasePreferenceControllerTest {

    private static final String TEST_PACKAGE_NAME = "com.test.package";
    private static final String TEST_SERVICE = "TestService";
    private final int mUserId = UserHandle.myUserId();
    private static final Uri ASSIST_URI = Settings.Secure.getUriFor(Settings.Secure.ASSISTANT);
    private static final Uri ASSIST_STRUCTURE_URI =
            Settings.Secure.getUriFor(Settings.Secure.ASSIST_STRUCTURE_ENABLED);

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private TwoStatePreference mPreference;
    private TestAssistConfigBasePreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private ComponentName mComponentName;

    @Mock
    private FragmentController mMockFragmentController;
    @Mock
    private AssistUtils mMockAssistUtils;
    @Mock
    private ContentResolver mMockContentResolver;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mComponentName = new ComponentName(TEST_PACKAGE_NAME, TEST_SERVICE);

        mPreference = new SwitchPreference(mContext);
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreferenceController = new TestAssistConfigBasePreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController,
                mCarUxRestrictions, mMockAssistUtils);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
    }

    @Test
    public void getAvailabilityStatus_hasAssistComponent_isAvailable() {
        when(mMockAssistUtils.getAssistComponentForUser(mUserId)).thenReturn(mComponentName);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_hasAssistComponent_isAvailable_zoneWrite() {
        when(mMockAssistUtils.getAssistComponentForUser(mUserId)).thenReturn(mComponentName);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_hasAssistComponent_isAvailable_zoneRead() {
        when(mMockAssistUtils.getAssistComponentForUser(mUserId)).thenReturn(mComponentName);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_hasAssistComponent_isAvailable_zoneHidden() {
        when(mMockAssistUtils.getAssistComponentForUser(mUserId)).thenReturn(mComponentName);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_noAssistComponent_conditionallyUnavailable() {
        when(mMockAssistUtils.getAssistComponentForUser(mUserId)).thenReturn(null);
        assertThat(mPreferenceController.getAvailabilityStatus())
                .isEqualTo(CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_noAssistComponent_conditionallyUnavailable_zoneWrite() {
        when(mMockAssistUtils.getAssistComponentForUser(mUserId)).thenReturn(null);
        mPreferenceController.setAvailabilityStatusForZone("write");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_noAssistComponent_conditionallyUnavailable_zoneRead() {
        when(mMockAssistUtils.getAssistComponentForUser(mUserId)).thenReturn(null);
        mPreferenceController.setAvailabilityStatusForZone("read");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_noAssistComponent_conditionallyUnavailable_zoneHidden() {
        when(mMockAssistUtils.getAssistComponentForUser(mUserId)).thenReturn(null);
        mPreferenceController.setAvailabilityStatusForZone("hidden");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onStart_registersObserver() {
        when(mMockAssistUtils.getAssistComponentForUser(mUserId)).thenReturn(mComponentName);
        when(mContext.getContentResolver()).thenReturn(mMockContentResolver);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        verify(mMockContentResolver).registerContentObserver(eq(ASSIST_URI), eq(false),
                any());
        verify(mMockContentResolver).registerContentObserver(eq(ASSIST_STRUCTURE_URI),
                eq(false), any());
    }

    @Test
    public void onStop_unregistersObserver() {
        when(mMockAssistUtils.getAssistComponentForUser(mUserId)).thenReturn(mComponentName);
        when(mContext.getContentResolver()).thenReturn(mMockContentResolver);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        mPreferenceController.onStop(mLifecycleOwner);

        verify(mMockContentResolver).unregisterContentObserver(any());
    }

    @Test
    public void onChange_changeRegisteredSetting_callsRefreshUi() {
        when(mMockAssistUtils.getAssistComponentForUser(mUserId)).thenReturn(mComponentName);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        int currentCount = mPreferenceController.getNumCallsToUpdateState();
        mPreferenceController.mSettingObserver
                .onChange(/* selfChange= */ false, ASSIST_STRUCTURE_URI);

        assertThat(mPreferenceController.getNumCallsToUpdateState())
                .isEqualTo(currentCount + 1);
    }

    private static class TestAssistConfigBasePreferenceController extends
            AssistConfigBasePreferenceController {

        private int mNumCallsToUpdateState;

        TestAssistConfigBasePreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController, CarUxRestrictions uxRestrictions,
                AssistUtils assistUtils) {
            super(context, preferenceKey, fragmentController, uxRestrictions, assistUtils);
            mNumCallsToUpdateState = 0;
        }

        public int getNumCallsToUpdateState() {
            return mNumCallsToUpdateState;
        }

        @Override
        protected void updateState(TwoStatePreference preference) {
            mNumCallsToUpdateState++;
        }

        @Override
        protected List<Uri> getSettingUris() {
            return Collections.singletonList(ASSIST_STRUCTURE_URI);
        }
    }
}
