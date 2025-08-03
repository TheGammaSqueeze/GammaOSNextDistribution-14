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
import static com.android.car.settings.system.FactoryResetEsimPreferenceController.KEY_SHOW_ESIM_RESET_CHECKBOX;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.euicc.EuiccManager;

import androidx.preference.SwitchPreference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

/** Unit test for {@link FactoryResetEsimPreferenceController}. */
@RunWith(AndroidJUnit4.class)
public class FactoryResetEsimPreferenceControllerTest {

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private SwitchPreference mPreference;
    private FactoryResetEsimPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private MockitoSession mSession;

    // Store value for reset after test run
    private int mEuiccProvisioned;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private EuiccManager mMockEuiccManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        // store before value
        mEuiccProvisioned = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.EUICC_PROVISIONED, 0);

        when(mContext.getSystemService(Context.EUICC_SERVICE)).thenReturn(mMockEuiccManager);
        when(mMockEuiccManager.isEnabled()).thenReturn(true);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.EUICC_PROVISIONED, 1);
        // mocking SystemProperties since we can't set them here
        mSession = ExtendedMockito.mockitoSession().mockStatic(
                SystemProperties.class, withSettings().lenient()).startMocking();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mPreference = new SwitchPreference(mContext);
        mPreferenceController = new FactoryResetEsimPreferenceController(mContext,
                "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
        // put back previous value
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.EUICC_PROVISIONED,
                mEuiccProvisioned);
    }

    @Test
    public void getAvailabilityStatus_showEsimPropertyTrue_available() {
        ExtendedMockito.when(SystemProperties.get(eq(KEY_SHOW_ESIM_RESET_CHECKBOX), anyString()))
                .thenReturn(Boolean.TRUE.toString());

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_showEsimPropertyTrue_available_zoneWrite() {
        ExtendedMockito.when(SystemProperties.get(eq(KEY_SHOW_ESIM_RESET_CHECKBOX), anyString()))
                .thenReturn(Boolean.TRUE.toString());
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_showEsimPropertyTrue_available_zoneRead() {
        ExtendedMockito.when(SystemProperties.get(eq(KEY_SHOW_ESIM_RESET_CHECKBOX), anyString()))
                .thenReturn(Boolean.TRUE.toString());
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_showEsimPropertyTrue_available_zoneHidden() {
        ExtendedMockito.when(SystemProperties.get(eq(KEY_SHOW_ESIM_RESET_CHECKBOX), anyString()))
                .thenReturn(Boolean.TRUE.toString());
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_showEsimPropertyFalse_unsupportedOnDevice() {
        ExtendedMockito.when(SystemProperties.get(eq(KEY_SHOW_ESIM_RESET_CHECKBOX), anyString()))
                .thenReturn(Boolean.FALSE.toString());

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_showEsimPropertyFalse_unsupportedOnDevice_zoneWrite() {
        ExtendedMockito.when(SystemProperties.get(eq(KEY_SHOW_ESIM_RESET_CHECKBOX), anyString()))
                .thenReturn(Boolean.FALSE.toString());
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_showEsimPropertyFalse_unsupportedOnDevice_zoneRead() {
        ExtendedMockito.when(SystemProperties.get(eq(KEY_SHOW_ESIM_RESET_CHECKBOX), anyString()))
                .thenReturn(Boolean.FALSE.toString());
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_showEsimPropertyFalse_unsupportedOnDevice_zoneHidden() {
        ExtendedMockito.when(SystemProperties.get(eq(KEY_SHOW_ESIM_RESET_CHECKBOX), anyString()))
                .thenReturn(Boolean.FALSE.toString());
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }
}
