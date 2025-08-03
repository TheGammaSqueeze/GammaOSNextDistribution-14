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

import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;
import static com.android.car.settings.common.PreferenceController.UNSUPPORTED_ON_DEVICE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class BluetoothMacAddressPreferenceControllerTest {
    private static final String TEST_ADDRESS = "address";

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private Preference mPreference;
    private BluetoothMacAddressPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private PackageManager mMockPm;
    @Mock
    private BluetoothAdapter mMockBluetoothAdapter;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        when(mContext.getPackageManager()).thenReturn(mMockPm);
        when(mMockBluetoothAdapter.getAddress()).thenReturn(TEST_ADDRESS);

        mPreference = new Preference(mContext);
        mPreferenceController = new BluetoothMacAddressPreferenceController(mContext,
                "key", mFragmentController, mCarUxRestrictions, mMockBluetoothAdapter);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
    }

    @Test
    public void onCreate_setsAddress() {
        when(mMockPm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)).thenReturn(true);

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreference.getSummary()).isEqualTo(TEST_ADDRESS);
    }

    @Test
    public void getAvailabilityStatus_featureBluetooth_unsupportedOnDevice() {
        when(mMockPm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)).thenReturn(false);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(
                UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_featureBluetooth_unsupportedOnDevice_zoneWrite() {
        when(mMockPm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)).thenReturn(false);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_featureBluetooth_unsupportedOnDevice_zoneRead() {
        when(mMockPm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)).thenReturn(false);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_featureBluetooth_unsupportedOnDevice_zoneHidden() {
        when(mMockPm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)).thenReturn(false);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_enableDefaultAdapter_availableForViewing() {
        when(mMockPm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)).thenReturn(true);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(
                AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_enableDefaultAdapter_availableForViewing_zoneWrite() {
        when(mMockPm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)).thenReturn(true);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_enableDefaultAdapter_availableForViewing_zoneRead() {
        when(mMockPm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)).thenReturn(true);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_enableDefaultAdapter_availableForViewing_zoneHidden() {
        when(mMockPm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)).thenReturn(true);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }
}
