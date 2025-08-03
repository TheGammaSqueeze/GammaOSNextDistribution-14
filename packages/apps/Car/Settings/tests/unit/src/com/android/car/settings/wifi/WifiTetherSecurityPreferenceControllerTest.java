/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.SoftApCapability;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.WifiManager;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.preference.ListPreference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;

import com.google.common.annotations.VisibleForTesting;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
public class WifiTetherSecurityPreferenceControllerTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private ListPreference mPreference;
    private TestWifiTetherSecurityPreferenceController mController;

    private LifecycleOwner mLifecycleOwner;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private Lifecycle mMockLifecycle;
    @Mock
    private SoftApConfiguration mConfig;
    @Mock
    private CarWifiManager mCarWifiManager;

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    @Before
    public void setup() {
        mLifecycleOwner = new TestLifecycleOwner();
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        when(mFragmentController.getSettingsLifecycle()).thenReturn(mMockLifecycle);
        mPreference = new ListPreference(mContext);
        when(mCarWifiManager.getSoftApConfig()).thenReturn(mConfig);
        mController = new TestWifiTetherSecurityPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions, mConfig,
                mCarWifiManager);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);
    }

    @Test
    public void onStart_securityTypeSetToNone_setsValueToNone() {
        when(mConfig.getSecurityType()).thenReturn(SoftApConfiguration.SECURITY_TYPE_OPEN);
        when(mConfig.getPassphrase()).thenReturn(null);
        mController.setTestConfig(mConfig);
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        assertThat(Integer.parseInt(mPreference.getValue()))
                .isEqualTo(SoftApConfiguration.SECURITY_TYPE_OPEN);
    }

    @Test
    public void onStart_securityTypeSetToWPA2PSK_setsValueToWPA2PSK() {
        String testPassword = "TEST_PASSWORD";
        when(mConfig.getSecurityType()).thenReturn(SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        when(mConfig.getPassphrase()).thenReturn(testPassword);
        mController.setTestConfig(mConfig);
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        assertThat(Integer.parseInt(mPreference.getValue()))
                .isEqualTo(SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
    }

    @Test
    public void onStart_securityTypeSetToWPA3SAE_setsValueForSupportedDevicesToWPA3SAE() {
        String testPassword = "TEST_PASSWORD";
        when(mConfig.getSecurityType()).thenReturn(SoftApConfiguration.SECURITY_TYPE_WPA3_SAE);
        when(mConfig.getPassphrase()).thenReturn(testPassword);
        mController.setTestConfig(mConfig);
        SoftApCapability softApCapability = mock(SoftApCapability.class);
        when(softApCapability.areFeaturesSupported(
                SoftApCapability.SOFTAP_FEATURE_WPA3_SAE)).thenReturn(true);
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        // calling callback manually to access the compatibility with WPA3
        mController.onCapabilityChanged(softApCapability);
        assertThat(Integer.parseInt(mPreference.getValue()))
                .isEqualTo(SoftApConfiguration.SECURITY_TYPE_WPA3_SAE);
    }

    @Test
    public void onStart_securityTypeSetToWPA3SAE_setsValueForUnSupportedDevicesToWPA2PSK() {
        String testPassword = "TEST_PASSWORD";
        when(mConfig.getSecurityType()).thenReturn(SoftApConfiguration.SECURITY_TYPE_WPA3_SAE);
        when(mConfig.getPassphrase()).thenReturn(testPassword);
        mController.setTestConfig(mConfig);
        SoftApCapability softApCapability = mock(SoftApCapability.class);
        when(softApCapability.areFeaturesSupported(
                SoftApCapability.SOFTAP_FEATURE_WPA3_SAE)).thenReturn(false);
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        // calling callback manually to access the compatibility with WPA3
        mController.onCapabilityChanged(softApCapability);
        assertThat(Integer.parseInt(mPreference.getValue()))
                .isEqualTo(SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
    }

    @Test
    public void onStart_registerSoftApCallbackCalled() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        verify(mController.getCarWifiManager(), atLeastOnce())
                .registerSoftApCallback(any(Executor.class), any(WifiManager.SoftApCallback.class));
    }

    @Test
    public void onPreferenceChangedToNone_updatesSharedSecurityTypeToNone() {
        String testPassword = "TEST_PASSWORD";
        when(mConfig.getSecurityType()).thenReturn(SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        when(mConfig.getPassphrase()).thenReturn(testPassword);
        mController.setTestConfig(mConfig);
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        mController.handlePreferenceChanged(mPreference,
                Integer.toString(SoftApConfiguration.SECURITY_TYPE_OPEN));
        SharedPreferences sp = mContext.getSharedPreferences(
                WifiTetherPasswordPreferenceController.SHARED_PREFERENCE_PATH,
                Context.MODE_PRIVATE);
        assertThat(sp.getInt(WifiTetherSecurityPreferenceController.KEY_SECURITY_TYPE, -1))
                .isEqualTo(SoftApConfiguration.SECURITY_TYPE_OPEN);
    }

    @Test
    public void onPreferenceChangedToWPA2PSK_updatesSharedSecurityTypeToWPA2PSK() {
        when(mConfig.getSecurityType()).thenReturn(SoftApConfiguration.SECURITY_TYPE_OPEN);
        when(mConfig.getPassphrase()).thenReturn(null);
        mController.setTestConfig(mConfig);
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        mController.handlePreferenceChanged(mPreference,
                Integer.toString(SoftApConfiguration.SECURITY_TYPE_WPA2_PSK));
        SharedPreferences sp = mContext.getSharedPreferences(
                WifiTetherPasswordPreferenceController.SHARED_PREFERENCE_PATH,
                Context.MODE_PRIVATE);
        assertThat(sp.getInt(WifiTetherSecurityPreferenceController.KEY_SECURITY_TYPE, -1))
                .isEqualTo(SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
    }

    @Test
    public void onPreferenceChangedToWPA3SAE_updatesSharedSecurityTypeToWPA3SAE() {
        // For devices that support WPA3
        String testPassword = "TEST_PASSWORD";
        when(mConfig.getSecurityType()).thenReturn(SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        when(mConfig.getPassphrase()).thenReturn(testPassword);
        mController.setTestConfig(mConfig);
        SoftApCapability softApCapability = mock(SoftApCapability.class);
        when(softApCapability.areFeaturesSupported(
                SoftApCapability.SOFTAP_FEATURE_WPA3_SAE)).thenReturn(true);
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        // calling callback manually to access the compatibility with WPA3
        mController.onCapabilityChanged(softApCapability);
        mController.handlePreferenceChanged(mPreference,
                Integer.toString(SoftApConfiguration.SECURITY_TYPE_WPA3_SAE));
        SharedPreferences sp = mContext.getSharedPreferences(
                WifiTetherPasswordPreferenceController.SHARED_PREFERENCE_PATH,
                Context.MODE_PRIVATE);
        assertThat(sp.getInt(WifiTetherSecurityPreferenceController.KEY_SECURITY_TYPE, -1))
                .isEqualTo(SoftApConfiguration.SECURITY_TYPE_WPA3_SAE);
    }

    @Test
    public void onPreferenceChangedToWPA3SAE_updatesSharedSecurityTypeToWPA2PSK() {
        // For devices that does not support WPA3
        String testPassword = "TEST_PASSWORD";
        when(mConfig.getSecurityType()).thenReturn(SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        when(mConfig.getPassphrase()).thenReturn(testPassword);
        mController.setTestConfig(mConfig);
        SoftApCapability softApCapability = mock(SoftApCapability.class);
        when(softApCapability.areFeaturesSupported(
                SoftApCapability.SOFTAP_FEATURE_WPA3_SAE)).thenReturn(false);
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        // calling callback manually to access the compatibility with WPA3
        mController.onCapabilityChanged(softApCapability);
        mController.handlePreferenceChanged(mPreference,
                Integer.toString(SoftApConfiguration.SECURITY_TYPE_WPA3_SAE));
        SharedPreferences sp = mContext.getSharedPreferences(
                WifiTetherPasswordPreferenceController.SHARED_PREFERENCE_PATH,
                Context.MODE_PRIVATE);
        assertThat(sp.getInt(WifiTetherSecurityPreferenceController.KEY_SECURITY_TYPE, -1))
                .isEqualTo(SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
    }

    private static class TestWifiTetherSecurityPreferenceController
            extends WifiTetherSecurityPreferenceController {
        SoftApConfiguration mConfig;
        CarWifiManager mCarWifiManager;

        TestWifiTetherSecurityPreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController, CarUxRestrictions uxRestrictions,
                SoftApConfiguration config, CarWifiManager carWifiManager) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
            mConfig = config;
            mCarWifiManager = carWifiManager;
        }

        @VisibleForTesting
        void setTestConfig(SoftApConfiguration config) {
            mConfig = config;
        }

        @Override
        protected SoftApConfiguration getCarSoftApConfig() {
            return mConfig;
        }

        @Override
        protected CarWifiManager getCarWifiManager() {
            return mCarWifiManager;
        }
    }
}
