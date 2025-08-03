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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;

import androidx.lifecycle.LifecycleOwner;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.ResourceTestUtils;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.wifitrackerlib.WifiEntry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class NetworkPasswordPreferenceControllerTest {
    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private NetworkNameRestrictedPasswordEditTextPreference mPreference;
    private NetworkPasswordPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private MockitoSession mSession;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private LocalBroadcastManager mMockLocalBroadcastManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mPreference = new NetworkNameRestrictedPasswordEditTextPreference(mContext);
        mPreferenceController = new NetworkPasswordPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mSession = ExtendedMockito.mockitoSession().mockStatic(
                LocalBroadcastManager.class, withSettings().lenient()).startMocking();
        when(LocalBroadcastManager.getInstance(any())).thenReturn(
                mMockLocalBroadcastManager);
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void onCreate_registersReceivers() {
        mPreferenceController.onCreate(mLifecycleOwner);

        ArgumentCaptor<IntentFilter> argumentCaptor = ArgumentCaptor.forClass(
                IntentFilter.class);
        verify(mMockLocalBroadcastManager, times(2))
                .registerReceiver(any(), argumentCaptor.capture());

        List<IntentFilter> capturedIntentFilters = argumentCaptor.getAllValues();
        assertThat(capturedIntentFilters.get(0).hasAction(
                NetworkNamePreferenceController.ACTION_NAME_CHANGE)).isTrue();
        assertThat(capturedIntentFilters.get(1).hasAction(
                NetworkSecurityPreferenceController.ACTION_SECURITY_CHANGE)).isTrue();
    }

    @Test
    public void onDestroy_unregistersReceivers() {
        mPreferenceController.onCreate(mLifecycleOwner);
        Mockito.reset(mMockLocalBroadcastManager);
        mPreferenceController.onDestroy(mLifecycleOwner);

        verify(mMockLocalBroadcastManager, times(2))
                .unregisterReceiver(any());
    }

    @Test
    public void receiveNameChangeIntent_emptyName_dialogNameRemoved() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        Intent intent = new Intent(NetworkNamePreferenceController.ACTION_NAME_CHANGE);
        intent.putExtra(NetworkNamePreferenceController.KEY_NETWORK_NAME, "");

        mPreferenceController.mNameChangeReceiver.onReceive(mContext, intent);

        assertThat(mPreference.getDialogTitle().toString()).isEqualTo(
                ResourceTestUtils.getString(mContext, "wifi_password"));
    }

    @Test
    public void receiveNameChangeIntent_name_dialogNameSet() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        String networkName = "test_network_name";
        Intent intent = new Intent(NetworkNamePreferenceController.ACTION_NAME_CHANGE);
        intent.putExtra(NetworkNamePreferenceController.KEY_NETWORK_NAME, networkName);

        mPreferenceController.mNameChangeReceiver.onReceive(mContext, intent);

        assertThat(mPreference.getDialogTitle().toString()).isEqualTo(networkName);
    }

    @Test
    public void receiveSecurityChangeIntent_setUnsecureType_preferenceHidden() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        Intent intent = new Intent(NetworkSecurityPreferenceController.ACTION_SECURITY_CHANGE);
        intent.putExtra(NetworkSecurityPreferenceController.KEY_SECURITY_TYPE,
                WifiEntry.SECURITY_NONE);

        mPreferenceController.mSecurityChangeReceiver.onReceive(mContext, intent);

        assertThat(mPreference.isVisible()).isFalse();
    }

    @Test
    public void receiveSecurityChangeIntent_setSecureType_preferenceVisible() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        Intent intent = new Intent(NetworkSecurityPreferenceController.ACTION_SECURITY_CHANGE);
        intent.putExtra(NetworkSecurityPreferenceController.KEY_SECURITY_TYPE,
                WifiEntry.SECURITY_PSK);

        mPreferenceController.mSecurityChangeReceiver.onReceive(mContext, intent);

        assertThat(mPreference.isVisible()).isTrue();
    }

    @Test
    public void handlePreferenceChanged_hasSecurity_networkNameSet_wifiConnected() {
        WifiManager mockWifiManager = mock(WifiManager.class);
        when(mContext.getSystemService(WifiManager.class)).thenReturn(mockWifiManager);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        String networkName = "network_name";
        String password = "password";
        Intent intent = new Intent(NetworkSecurityPreferenceController.ACTION_SECURITY_CHANGE);
        intent.putExtra(NetworkSecurityPreferenceController.KEY_SECURITY_TYPE,
                WifiEntry.SECURITY_PSK);
        mPreferenceController.mSecurityChangeReceiver.onReceive(mContext, intent);

        intent = new Intent(NetworkNamePreferenceController.ACTION_NAME_CHANGE);
        intent.putExtra(NetworkNamePreferenceController.KEY_NETWORK_NAME, networkName);
        mPreferenceController.mNameChangeReceiver.onReceive(mContext, intent);
        mPreference.callChangeListener(password);

        ArgumentCaptor<WifiConfiguration> argumentCaptor = ArgumentCaptor.forClass(
                WifiConfiguration.class);
        verify(mockWifiManager).connect(argumentCaptor.capture(), any());

        WifiConfiguration config = argumentCaptor.getValue();
        assertThat(config.SSID).contains(networkName);
        assertThat(config.getAuthType()).isEqualTo(WifiConfiguration.KeyMgmt.WPA_PSK);
        assertThat(config.preSharedKey).contains(password);
    }
}
