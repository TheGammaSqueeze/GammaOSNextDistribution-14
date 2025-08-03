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

package com.android.car.settings.system;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;
import static com.android.car.settings.common.PreferenceController.UNSUPPORTED_ON_DEVICE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.provider.Settings;
import android.telephony.euicc.EuiccManager;

import androidx.preference.SwitchPreference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit test for {@link ResetEsimPreferenceController}. */
@RunWith(AndroidJUnit4.class)
public class ResetEsimPreferenceControllerTest {
    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private SwitchPreference mPreference;
    private ResetEsimPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    // Store values for reset after test run
    private int mDevelopmentSettingsEnabled;
    private int mEuiccProvisioned;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private EuiccManager mMockEuiccManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        // store before values
        mDevelopmentSettingsEnabled = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0);
        mEuiccProvisioned = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.EUICC_PROVISIONED, 0);

        when(mContext.getSystemService(Context.EUICC_SERVICE)).thenReturn(mMockEuiccManager);
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.EUICC_PROVISIONED, 0);

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mPreference = new SwitchPreference(mContext);
        mPreferenceController = new ResetEsimPreferenceController(mContext,
                "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
    }

    @After
    public void tearDown() {
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, mDevelopmentSettingsEnabled);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.EUICC_PROVISIONED,
                mEuiccProvisioned);
    }

    @Test
    public void getAvailabilityStatus_disabledEuiccManager_unsupportedOnDevice() {
        when(mMockEuiccManager.isEnabled()).thenReturn(false);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_disabledEuiccManager_unsupportedOnDevice_zoneWrite() {
        when(mMockEuiccManager.isEnabled()).thenReturn(false);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_disabledEuiccManager_unsupportedOnDevice_zoneRead() {
        when(mMockEuiccManager.isEnabled()).thenReturn(false);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_disabledEuiccManager_unsupportedOnDevice_zoneHidden() {
        when(mMockEuiccManager.isEnabled()).thenReturn(false);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_euiccNotProvisioned_unsupportedOnDevice() {
        when(mMockEuiccManager.isEnabled()).thenReturn(true);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_euiccNotProvisioned_unsupportedOnDevice_zoneWrite() {
        when(mMockEuiccManager.isEnabled()).thenReturn(true);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_euiccNotProvisioned_unsupportedOnDevice_zoneRead() {
        when(mMockEuiccManager.isEnabled()).thenReturn(true);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_euiccNotProvisioned_unsupportedOnDevice_zoneHidden() {
        when(mMockEuiccManager.isEnabled()).thenReturn(true);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_euiccNotProvisioned_developer_available() {
        when(mMockEuiccManager.isEnabled()).thenReturn(true);
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_euiccNotProvisioned_developer_available_zoneWrite() {
        when(mMockEuiccManager.isEnabled()).thenReturn(true);
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_euiccNotProvisioned_developer_available_zoneRead() {
        when(mMockEuiccManager.isEnabled()).thenReturn(true);
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_euiccNotProvisioned_developer_available_zoneHidden() {
        when(mMockEuiccManager.isEnabled()).thenReturn(true);
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_euiccProvisioned_available() {
        when(mMockEuiccManager.isEnabled()).thenReturn(true);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.EUICC_PROVISIONED, 1);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_euiccProvisioned_available_zoneWrite() {
        when(mMockEuiccManager.isEnabled()).thenReturn(true);
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.EUICC_PROVISIONED, 1);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_euiccProvisioned_available_zoneRead() {
        when(mMockEuiccManager.isEnabled()).thenReturn(true);
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.EUICC_PROVISIONED, 1);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_euiccProvisioned_available_zoneHidden() {
        when(mMockEuiccManager.isEnabled()).thenReturn(true);
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.EUICC_PROVISIONED, 1);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }
}
