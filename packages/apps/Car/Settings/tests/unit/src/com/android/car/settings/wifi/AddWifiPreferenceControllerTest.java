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

package com.android.car.settings.wifi;

import static android.os.UserManager.DISALLOW_ADD_WIFI_CONFIG;
import static android.os.UserManager.DISALLOW_WIFI_TETHERING;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;
import static com.android.car.settings.common.PreferenceController.UNSUPPORTED_ON_DEVICE;
import static com.android.car.settings.testutils.EnterpriseTestUtils.mockUserRestrictionSetByDpm;
import static com.android.car.settings.testutils.EnterpriseTestUtils.mockUserRestrictionSetByUm;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.car.test.AbstractExpectableTestCase;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.UserManager;
import android.util.Log;

import androidx.preference.Preference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class AddWifiPreferenceControllerTest extends AbstractExpectableTestCase {

    private static final String TAG = AddWifiPreferenceControllerTest.class.getSimpleName();

    private static final List<Integer> VISIBLE_STATES = Arrays.asList(
            WifiManager.WIFI_STATE_ENABLED,
            WifiManager.WIFI_STATE_DISABLING,
            WifiManager.WIFI_STATE_ENABLING,
            WifiManager.WIFI_STATE_UNKNOWN);
    private static final List<Integer> INVISIBLE_STATES = Arrays.asList(
            WifiManager.WIFI_STATE_DISABLED);

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private FragmentController mMockFragmentController;
    @Mock
    private PackageManager mMockPackageManager;
    @Mock
    private UserManager mMockUserManager;

    private Context mContext;
    private CarUxRestrictions mCarUxRestrictions;
    private Preference mPreference;
    private AddWifiPreferenceController mController;

    @Before
    public void setUp() {
        mContext = spy(ApplicationProvider.getApplicationContext());
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreference = new Preference(mContext);
        mController = new AddWifiPreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);
        when(mContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);
        when(mContext.getPackageManager()).thenReturn(mMockPackageManager);

        // WIFI is enabled by default.
        when(mMockPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(true);
    }

    @Test
    public void getAvailabilityStatus_wifiFeatureDisabled() {
        when(mMockPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(false);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_wifiFeatureDisabled_zoneWrite() {
        when(mMockPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(false);
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_wifiFeatureDisabled_zoneRead() {
        when(mMockPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(false);
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_wifiFeatureDisabled_zoneHidden() {
        when(mMockPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(false);
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_restrictionByUm() {
        mockUserRestrictionSetByUm(mMockUserManager, DISALLOW_ADD_WIFI_CONFIG, true);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_restrictionByUm_zoneWrite() {
        mockUserRestrictionSetByUm(mMockUserManager, DISALLOW_ADD_WIFI_CONFIG, true);
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_restrictionByUm_zoneRead() {
        mockUserRestrictionSetByUm(mMockUserManager, DISALLOW_ADD_WIFI_CONFIG, true);
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_restrictionByUm_zoneHidden() {
        mockUserRestrictionSetByUm(mMockUserManager, DISALLOW_ADD_WIFI_CONFIG, true);
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_restrictionByDpm() {
        mockUserRestrictionSetByDpm(mMockUserManager, DISALLOW_ADD_WIFI_CONFIG, true);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_restrictionByDpm_zoneWrite() {
        mockUserRestrictionSetByDpm(mMockUserManager, DISALLOW_ADD_WIFI_CONFIG, true);
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_restrictionByDpm_zoneRead() {
        mockUserRestrictionSetByDpm(mMockUserManager, DISALLOW_ADD_WIFI_CONFIG, true);
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_restrictionByDpm_zoneHidden() {
        mockUserRestrictionSetByDpm(mMockUserManager, DISALLOW_ADD_WIFI_CONFIG, true);
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_differentRestriction() {
        mockUserRestrictionSetByUm(mMockUserManager, DISALLOW_WIFI_TETHERING, true);
        mockUserRestrictionSetByDpm(mMockUserManager, DISALLOW_WIFI_TETHERING, true);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_differentRestriction_zoneWrite() {
        mockUserRestrictionSetByUm(mMockUserManager, DISALLOW_WIFI_TETHERING, true);
        mockUserRestrictionSetByDpm(mMockUserManager, DISALLOW_WIFI_TETHERING, true);
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_differentRestriction_zoneRead() {
        mockUserRestrictionSetByUm(mMockUserManager, DISALLOW_WIFI_TETHERING, true);
        mockUserRestrictionSetByDpm(mMockUserManager, DISALLOW_WIFI_TETHERING, true);
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_differentRestriction_zoneHidden() {
        mockUserRestrictionSetByUm(mMockUserManager, DISALLOW_WIFI_TETHERING, true);
        mockUserRestrictionSetByDpm(mMockUserManager, DISALLOW_WIFI_TETHERING, true);
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_noRestrictions() {
        // UserManager#hasRestriciton() returns false by default. No need to mock.

        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_noRestrictions_zoneWrite() {
        // UserManager#hasRestriciton() returns false by default. No need to mock.
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_noRestrictions_zoneRead() {
        // UserManager#hasRestriciton() returns false by default. No need to mock.
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_noRestrictions_zoneHidden() {
        // UserManager#hasRestriciton() returns false by default. No need to mock.
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onWifiStateChanged_invisible() {
        for (int state : INVISIBLE_STATES) {
            Log.d(TAG, "Calling onWifiStateChanged(" + state + ")");
            mController.onWifiStateChanged(state);

            expectThat(mPreference.isVisible()).isEqualTo(false);
        }
    }

    @Test
    public void onWifiStateChanged_visible() {
        for (int state : VISIBLE_STATES) {
            Log.d(TAG, "Calling onWifiStateChanged(" + state + ")");
            mController.onWifiStateChanged(state);

            expectThat(mPreference.isVisible()).isEqualTo(true);
        }
    }
}
