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

package com.android.car.settings.wifi.details;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;

import androidx.lifecycle.LifecycleOwner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.wifitrackerlib.WifiEntry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class WifiInfoProviderTest {
    private static final int LEVEL = 1;
    private static final int RSSI = -55;
    private static final int LINK_SPEED = 123;
    private static final String MAC_ADDRESS = WifiInfo.DEFAULT_MAC_ADDRESS;
    private static final String SECURITY = "None";

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private WifiInfoProvider mWifiInfoProvider;

    @Mock
    private WifiEntry mMockWifiEntry;
    @Mock
    private ConnectivityManager mMockConnectivityManager;
    @Mock
    private Network mMockNetwork;
    @Mock
    private NetworkInfo mMockNetworkInfo;
    @Mock
    private WifiConfiguration mMockWifiConfig;
    @Mock
    private WifiInfo mMockWifiInfo;
    @Mock
    private LinkProperties mMockLinkProperties;
    @Mock
    private LinkProperties mMockChangedLinkProperties;
    @Mock
    private NetworkCapabilities mMockNetworkCapabilities;
    @Mock
    private NetworkCapabilities mMockChangedNetworkCapabilities;
    @Mock
    private WifiManager mMockWifiManager;
    @Mock
    private WifiInfoProvider.Listener mMockListener;

    @Captor
    private ArgumentCaptor<ConnectivityManager.NetworkCallback> mCallbackCaptor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        when(mContext.getSystemService(ConnectivityManager.class))
                .thenReturn(mMockConnectivityManager);
        when(mContext.getSystemService(WifiManager.class)).thenReturn(mMockWifiManager);
        when(mMockWifiEntry.getWifiConfiguration()).thenReturn(mMockWifiConfig);
        when(mMockWifiEntry.getLevel()).thenReturn(LEVEL);
        when(mMockWifiEntry.getSecurityString(false)).thenReturn(SECURITY);
        when(mMockConnectivityManager.getNetworkInfo(any(Network.class)))
                .thenReturn(mMockNetworkInfo);
        when(mMockConnectivityManager.getLinkProperties(any(Network.class)))
                .thenReturn(mMockLinkProperties);
        when(mMockConnectivityManager.getNetworkCapabilities(any(Network.class)))
                .thenReturn(mMockNetworkCapabilities);
        doNothing().when(mMockConnectivityManager).registerNetworkCallback(
                nullable(NetworkRequest.class), mCallbackCaptor.capture(), nullable(Handler.class));
        when(mMockWifiInfo.getLinkSpeed()).thenReturn(LINK_SPEED);
        when(mMockWifiInfo.getRssi()).thenReturn(RSSI);
        when(mMockWifiInfo.getMacAddress()).thenReturn(MAC_ADDRESS);
        when(mMockWifiManager.getConnectionInfo()).thenReturn(mMockWifiInfo);

        when(mMockWifiManager.getCurrentNetwork()).thenReturn(mMockNetwork);

        mWifiInfoProvider = new WifiInfoProvider(mContext, mMockWifiEntry);
        mWifiInfoProvider.addListener(mMockListener);
    }

    @Test
    public void onStart_allFieldsInitialized() {
        mWifiInfoProvider.onStart(mLifecycleOwner);

        assertThat(mWifiInfoProvider.getNetworkInfo()).isNotNull();
        assertThat(mWifiInfoProvider.getWifiInfo()).isNotNull();
        assertThat(mWifiInfoProvider.getNetwork()).isNotNull();
        assertThat(mWifiInfoProvider.getNetworkCapabilities()).isNotNull();
        assertThat(mWifiInfoProvider.getNetworkConfiguration()).isNotNull();
        assertThat(mWifiInfoProvider.getLinkProperties()).isNotNull();
    }

    @Test
    public void onStart_listenerCallback() {
        mWifiInfoProvider.onStart(mLifecycleOwner);
        verify(mMockListener).onWifiEntryUpdated();
    }

    @Test
    public void onStart_getsNetwork() {
        mWifiInfoProvider.onStart(mLifecycleOwner);
        assertThat(mWifiInfoProvider.getNetwork()).isEqualTo(mMockNetwork);
    }

    @Test
    public void networkCallback_shouldBeRegisteredOnStart() {
        mWifiInfoProvider.onStart(mLifecycleOwner);

        verify(mMockConnectivityManager).registerNetworkCallback(
                nullable(NetworkRequest.class), mCallbackCaptor.capture(), nullable(Handler.class));
    }

    @Test
    public void networkCallback_shouldBeUnregisteredOnStop() {
        mWifiInfoProvider.onStart(mLifecycleOwner);
        mWifiInfoProvider.onStop(mLifecycleOwner);

        verify(mMockConnectivityManager)
                .unregisterNetworkCallback(mCallbackCaptor.getValue());
    }

    @Test
    public void onWifiEntryUpdated_listenerCallback() {
        mWifiInfoProvider.onStart(mLifecycleOwner);
        Mockito.reset(mMockListener);
        mWifiInfoProvider.onUpdated();

        verify(mMockListener).onWifiEntryUpdated();
    }

    @Test
    public void onLost_listenerCallback() {
        mWifiInfoProvider.onStart(mLifecycleOwner);

        mCallbackCaptor.getValue().onLost(mMockNetwork);

        verify(mMockListener).onLost(any(Network.class));
    }

    @Test
    public void onLinkPropertiesChanged_listenerCallback() {
        mWifiInfoProvider.onStart(mLifecycleOwner);

        mCallbackCaptor.getValue().onLinkPropertiesChanged(
                mMockNetwork, mMockChangedLinkProperties);

        verify(mMockListener).onLinkPropertiesChanged(
                any(Network.class), eq(mMockChangedLinkProperties));
    }

    @Test
    public void onCapabilitiesChanged_listenerCallback() {
        mWifiInfoProvider.onStart(mLifecycleOwner);

        mCallbackCaptor.getValue().onCapabilitiesChanged(
                mMockNetwork, mMockChangedNetworkCapabilities);

        verify(mMockListener).onCapabilitiesChanged(
                any(Network.class), eq(mMockChangedNetworkCapabilities));
    }
}
