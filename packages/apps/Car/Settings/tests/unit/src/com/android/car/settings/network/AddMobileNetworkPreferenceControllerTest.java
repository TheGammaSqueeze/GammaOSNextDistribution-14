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

package com.android.car.settings.network;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;
import static com.android.car.settings.common.PreferenceController.UNSUPPORTED_ON_DEVICE;
import static com.android.car.settings.network.AddMobileNetworkPreferenceController.ADD_NETWORK_INTENT;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.telephony.euicc.EuiccManager;

import androidx.preference.Preference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.ui.preference.CarUiPreference;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public class AddMobileNetworkPreferenceControllerTest {

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private Preference mPreference;
    private AddMobileNetworkPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private EuiccManager mMockEuiccManager;
    @Mock
    private PackageManager mMockPackageManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mContext.getSystemService(EuiccManager.class)).thenReturn(mMockEuiccManager);
        when(mContext.getPackageManager()).thenReturn(mMockPackageManager);

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mPreference = new CarUiPreference(mContext);
        mPreferenceController = new AddMobileNetworkPreferenceController(mContext,
                "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
    }

    @Test
    public void getAvailabilityStatus_euiccDisabled_isUnsupported() {
        when(mMockEuiccManager.isEnabled()).thenReturn(false);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_euiccDisabled_isUnsupported_zoneWrite() {
        when(mMockEuiccManager.isEnabled()).thenReturn(false);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_euiccDisabled_isUnsupported_zoneRead() {
        when(mMockEuiccManager.isEnabled()).thenReturn(false);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_euiccDisabled_isUnsupported_zoneHidden() {
        when(mMockEuiccManager.isEnabled()).thenReturn(false);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_euiccEnabled_intentResolves_isAvailable() {
        when(mMockEuiccManager.isEnabled()).thenReturn(true);
        when(mMockPackageManager.queryIntentActivities(ADD_NETWORK_INTENT, /* flags= */ 0))
                .thenReturn(Collections.singletonList(new ResolveInfo()));

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_euiccEnabled_intentResolves_isAvailable_zoneWrite() {
        when(mMockEuiccManager.isEnabled()).thenReturn(true);
        when(mMockPackageManager.queryIntentActivities(ADD_NETWORK_INTENT, /* flags= */ 0))
                .thenReturn(Collections.singletonList(new ResolveInfo()));
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_euiccEnabled_intentResolves_isAvailable_zoneRead() {
        when(mMockEuiccManager.isEnabled()).thenReturn(true);
        when(mMockPackageManager.queryIntentActivities(ADD_NETWORK_INTENT, /* flags= */ 0))
                .thenReturn(Collections.singletonList(new ResolveInfo()));
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_euiccEnabled_intentResolves_isAvailable_zoneHidden() {
        when(mMockEuiccManager.isEnabled()).thenReturn(true);
        when(mMockPackageManager.queryIntentActivities(ADD_NETWORK_INTENT, /* flags= */ 0))
                .thenReturn(Collections.singletonList(new ResolveInfo()));
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_euiccEnabled_intentFailsToResolve_isUnsupported() {
        when(mMockEuiccManager.isEnabled()).thenReturn(true);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_euiccEnabled_intentFailsToResolve_isUnsupported_zoneWrite() {
        when(mMockEuiccManager.isEnabled()).thenReturn(true);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_euiccEnabled_intentFailsToResolve_isUnsupported_zoneRead() {
        when(mMockEuiccManager.isEnabled()).thenReturn(true);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_euiccEnabled_intentFailsToResolve_isUnsupported_zoneHidden() {
        when(mMockEuiccManager.isEnabled()).thenReturn(true);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }
}
