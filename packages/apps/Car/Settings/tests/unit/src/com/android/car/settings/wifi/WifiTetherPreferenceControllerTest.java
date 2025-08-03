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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.net.TetheringManager;
import android.net.wifi.SoftApConfiguration;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.car.ui.preference.CarUiTwoActionSwitchPreference;
import com.android.settingslib.wifi.WifiUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
public class WifiTetherPreferenceControllerTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private CarUiTwoActionSwitchPreference mPreference;
    private WifiTetherPreferenceController mController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private Lifecycle mMockLifecycle;
    @Mock
    private TetheringManager mTetheringManager;
    @Mock
    private CarWifiManager mCarWifiManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        when(mFragmentController.getSettingsLifecycle()).thenReturn(mMockLifecycle);

        mPreference = new CarUiTwoActionSwitchPreference(mContext);
        mController = new WifiTetherPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions,
                mCarWifiManager, mTetheringManager);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);
    }

    @Test
    public void onStart_isAvailableForViewing() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(
                AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void onStart_isAvailableForViewing_zoneWrite() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void onStart_isAvailableForViewing_zoneRead() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void onStart_isAvailableForViewing_zoneHidden() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onStart_registersTetheringEventCallback() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);

        verify(mTetheringManager).registerTetheringEventCallback(
                any(Executor.class), any(TetheringManager.TetheringEventCallback.class));
    }

    @Test
    public void onStop_unregistersTetheringEventCallback() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        mController.onStop(mLifecycleOwner);

        verify(mTetheringManager).unregisterTetheringEventCallback(
                any(TetheringManager.TetheringEventCallback.class));
    }

    @Test
    public void onTetheringSupported_false_isUnsupportedOnDevice() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);

        setTetheringSupported(false);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(
                UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void onTetheringSupported_false_isUnsupportedOnDevice_zoneWrite() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("write");

        setTetheringSupported(false);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void onTetheringSupported_false_isUnsupportedOnDevice_zoneRead() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("read");

        setTetheringSupported(false);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void onTetheringSupported_false_isUnsupportedOnDevice_zoneHidden() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("hidden");

        setTetheringSupported(false);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void onTetheringSupported_true_isAvailable() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);

        setTetheringSupported(true);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void onTetheringSupported_true_isAvailable_zoneWrite() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("write");

        setTetheringSupported(true);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE);
    }

    @Test
    public void onTetheringSupported_true_isAvailable_zoneRead() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("read");

        setTetheringSupported(true);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void onTetheringSupported_true_isAvailable_zoneHidden() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("hidden");

        setTetheringSupported(true);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onTetheringOff_subtitleOff() {
        when(mCarWifiManager.isWifiApEnabled()).thenReturn(false);
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        setTetheringSupported(true);

        assertThat(mPreference.getSummary()).isEqualTo(
                mContext.getString(R.string.wifi_hotspot_state_off));
    }

    @Test
    public void onTetheringOn_showsSSIDAndPassword() {
        String testSSID = "TEST_SSID";
        String testPassword = "TEST_PASSWORD";
        when(mCarWifiManager.isWifiApEnabled()).thenReturn(true);
        SoftApConfiguration config = mock(SoftApConfiguration.class);
        when(config.getSsid()).thenReturn(testSSID);
        when(config.getSecurityType()).thenReturn(SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        when(config.getPassphrase()).thenReturn(testPassword);
        when(mCarWifiManager.getSoftApConfig()).thenReturn(config);
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        setTetheringSupported(true);

        assertThat(mPreference.getSummary()).isEqualTo(
                testSSID + " / " + testPassword);
    }

    @Test
    public void onDeviceConnected_showsDeviceConnectedSubtitle() {
        int connectedClients = 2;
        when(mCarWifiManager.isWifiApEnabled()).thenReturn(true);
        mController.onConnectedClientsChanged(connectedClients);
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        setTetheringSupported(true);

        assertThat(mPreference.getSummary()).isEqualTo(
                WifiUtils.getWifiTetherSummaryForConnectedDevices(mContext, connectedClients));
    }

    private void setTetheringSupported(boolean supported) {
        ArgumentCaptor<TetheringManager.TetheringEventCallback> captor =
                ArgumentCaptor.forClass(TetheringManager.TetheringEventCallback.class);
        verify(mTetheringManager).registerTetheringEventCallback(
                any(Executor.class), captor.capture());
        captor.getValue().onTetheringSupported(supported);
    }
}
