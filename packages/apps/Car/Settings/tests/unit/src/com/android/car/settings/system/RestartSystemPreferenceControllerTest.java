/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.car.settings.system;

import static com.android.car.settings.common.PreferenceController.UNSUPPORTED_ON_DEVICE;
import static com.android.car.settings.system.RestartSystemPreferenceController.RESTART_SYSTEM_CONFIRM_DIALOG_TAG;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.os.Bundle;
import android.os.PowerManager;

import androidx.lifecycle.LifecycleOwner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.car.ui.preference.CarUiPreference;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

/** Unit test for {@link RestartSystemPreferenceController}. */
@RunWith(AndroidJUnit4.class)
public class RestartSystemPreferenceControllerTest {

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private CarUxRestrictions mCarUxRestrictions;
    private CarUiPreference mPreference;
    private MockitoSession mSession;
    private RestartSystemPreferenceController mPreferenceController;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private PowerManager mMockPowerManager;

    @Before
    public void setUp() {
        mLifecycleOwner = new TestLifecycleOwner();
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mSession = ExtendedMockito.mockitoSession()
                .initMocks(this)
                .mockStatic(PowerManager.class)
                .strictness(Strictness.LENIENT)
                .startMocking();

        when(mContext.getSystemService(PowerManager.class)).thenReturn(mMockPowerManager);

        mPreference = new CarUiPreference(mContext);
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void handlePreferenceClicked_showsDialog() {
        mPreferenceController = new RestartSystemPreferenceController(mContext,
                "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);

        mPreferenceController.onCreate(mLifecycleOwner);
        assertThat(mPreferenceController.mDialogFragment.getConfirmListener()).isNotNull();
        assertThat(mPreferenceController.mDialogFragment.getRejectListener()).isNull();

        mPreference.performClick();

        verify(mFragmentController).showDialog(any(ConfirmationDialogFragment.class),
                eq(RESTART_SYSTEM_CONFIRM_DIALOG_TAG));
    }

    @Test
    public void restartSystemConfirmationListener_continueToReboot() {
        mPreferenceController = new RestartSystemPreferenceController(mContext,
                "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.mRestartSystemConfirmListener.onConfirm(new Bundle());

        verify(mMockPowerManager, times(1)).reboot(null);
    }

    @Test
    public void getAvailabilityStatus_noAccessPrivilege_unsupportedOnDevice() {
        mPreferenceController = new RestartSystemPreferenceController(mContext,
                "key", mFragmentController, mCarUxRestrictions,
                /* rebootPermissionGranted */ false);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_noAccessPrivilege_unsupportedOnDevice_zoneWrite() {
        mPreferenceController = new RestartSystemPreferenceController(mContext,
                "key", mFragmentController, mCarUxRestrictions,
                /* rebootPermissionGranted */ false);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_noAccessPrivilege_unsupportedOnDevice_zoneRead() {
        mPreferenceController = new RestartSystemPreferenceController(mContext,
                "key", mFragmentController, mCarUxRestrictions,
                /* rebootPermissionGranted */ false);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_noAccessPrivilege_unsupportedOnDevice_zoneHidden() {
        mPreferenceController = new RestartSystemPreferenceController(mContext,
                "key", mFragmentController, mCarUxRestrictions,
                /* rebootPermissionGranted */ false);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }
}
