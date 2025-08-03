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

package com.android.server.wifi;

import static com.android.server.wifi.ConnectToNetworkNotificationBuilder.ACTION_CONNECT_TO_NETWORK;

import static org.mockito.Mockito.*;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.net.wifi.ScanResult;
import android.net.wifi.ScanResult.InformationElement;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiSsid;
import android.os.Looper;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.util.WifiPermissionsUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.charset.StandardCharsets;

/**
 * Unit tests for {@link com.android.server.wifi.EapFailureNotifier}.
 */
@SmallTest
public class AvailableNetworkNotifierTest extends WifiBaseTest {
    private AvailableNetworkNotifier mAvailableNetworkNotifier;
    @Mock WifiContext mContext;
    @Mock Looper mLooper;
    @Mock FrameworkFacade mFrameworkFacade;
    @Mock Clock mClock;
    @Mock WifiMetrics mWifiMetrics;
    @Mock WifiConfigManager mWifiConfigManager;
    @Mock WifiConfigStore mWifiConfigStore;
    @Mock ConnectHelper mConnectHelper;
    @Mock ConnectToNetworkNotificationBuilder mConnectToNetworkNotificationBuilder;
    @Mock MakeBeforeBreakManager mMakeBeforeBreakManager;
    @Mock WifiNotificationManager mWifiNotificationManager;
    @Mock WifiPermissionsUtil mWifiPermissionsUtil;

    BroadcastReceiver mBroadcastReceiver;

    /**
     * Sets up for unit test
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mAvailableNetworkNotifier = new AvailableNetworkNotifier(
                "AvailableNetworkNotifierTest",
                "storeDataIdentifier",
                "toggleSettingsName",
                1, // notificationIdentifier
                1, // nominatorId
                mContext,
                mLooper,
                mFrameworkFacade,
                mClock,
                mWifiMetrics,
                mWifiConfigManager,
                mWifiConfigStore,
                mConnectHelper,
                mConnectToNetworkNotificationBuilder,
                mMakeBeforeBreakManager,
                mWifiNotificationManager,
                mWifiPermissionsUtil);

        ArgumentCaptor<BroadcastReceiver> captor = ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(captor.capture(), any(), any(), any());
        mBroadcastReceiver = captor.getValue();
    }

    @After
    public void cleanUp() throws Exception {
        validateMockitoUsage();
    }

    /** Verify that connecting to an unknown akm network works normally.
     */
    @Test
    public void testConnectToUnknownAkmNetwork() throws Exception {
        mAvailableNetworkNotifier.mState =
                AvailableNetworkNotifier.STATE_SHOWING_RECOMMENDATION_NOTIFICATION;
        final String ssid = "UnknownAkm-Network";
        final String caps = "[RSN-?-TKIP+CCMP][ESS][WPS]";
        ScanResult result = new ScanResult.Builder(WifiSsid.fromUtf8Text(ssid),
                "ab:cd:01:ef:45:89")
                .setHessid(1245)
                .setCaps(caps)
                .setRssi(-78)
                .setFrequency(2450)
                .setTsf(1025)
                .setDistanceCm(22)
                .setDistanceSdCm(33)
                .setIs80211McRTTResponder(true)
                .build();
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_SSID;
        ie.bytes = ssid.getBytes(StandardCharsets.UTF_8);
        result.informationElements = new InformationElement[] { ie };
        mAvailableNetworkNotifier.mRecommendedNetwork = result;

        Intent intent = new Intent();
        intent.setAction(ACTION_CONNECT_TO_NETWORK);
        mBroadcastReceiver.onReceive(mContext, intent);
        verify(mWifiConfigManager, never()).addOrUpdateNetwork(any(), anyInt());
    }
}
