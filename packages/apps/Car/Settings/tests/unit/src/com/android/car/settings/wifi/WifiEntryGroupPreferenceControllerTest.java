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

package com.android.car.settings.wifi;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;
import static com.android.car.settings.common.PreferenceController.UNSUPPORTED_ON_DEVICE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserManager;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.EnterpriseTestUtils;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class WifiEntryGroupPreferenceControllerTest {
    private static final String TEST_RESTRICTION =
            android.os.UserManager.DISALLOW_CONFIG_WIFI;
    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private LogicalPreferenceGroup mPreference;
    private CarUxRestrictions mCarUxRestrictions;
    private WifiEntryGroupPreferenceController mController;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private Lifecycle mMockLifecycle;
    @Mock
    private UserManager mMockUserManager;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreference = new LogicalPreferenceGroup(mContext);
        mController = new WifiEntryGroupPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);

        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);
        when(mFragmentController.getSettingsLifecycle()).thenReturn(mMockLifecycle);
    }

    @Test
    public void getAvailabilityStatus_wifiAvailable_notRestricted_available() {
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(true);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_wifiAvailable_notRestricted_available_zoneWrite() {
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(true);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_wifiAvailable_notRestricted_available_zoneRead() {
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(true);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_wifiAvailable_notRestricted_available_zoneHidden() {
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(true);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_wifiAvailable_restrictedByUm_viewing() {
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(true);
        EnterpriseTestUtils
                .mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, true);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_wifiAvailable_restrictedByUm_viewing_zoneWrite() {
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(true);
        EnterpriseTestUtils
                .mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, true);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_wifiAvailable_restrictedByUm_viewing_zoneRead() {
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(true);
        EnterpriseTestUtils
                .mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, true);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_wifiAvailable_restrictedByUm_viewing_zoneHidden() {
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(true);
        EnterpriseTestUtils
                .mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, true);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_wifiAvailable_restrictedByDpm_viewing() {
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(true);
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_wifiAvailable_restrictedByDpm_viewing_zoneWrite() {
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(true);
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_wifiAvailable_restrictedByDpm_viewing_zoneRead() {
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(true);
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_wifiAvailable_restrictedByDpm_viewing_zoneHidden() {
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(true);
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_wifiNotAvailable_unsupportedOnDevice() {
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(false);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_wifiNotAvailable_unsupportedOnDevice_zoneWrite() {
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(false);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_wifiNotAvailable_unsupportedOnDevice_zoneRead() {
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(false);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_wifiNotAvailable_unsupportedOnDevice_zoneHidden() {
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(false);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }
}
