/*
 * Copyright 2020 The Android Open Source Project
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

package com.google.android.iwlan;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import android.content.Context;
import android.content.Intent;
import android.net.Network;
import android.net.wifi.WifiManager;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;

import com.google.android.iwlan.epdg.EpdgSelector;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.*;

public class IwlanBroadcastReceiverTest {
    private static final String TAG = "IwlanBroadcastReceiverTest";
    private IwlanBroadcastReceiver mBroadcastReceiver;

    private static final String ACTION_CARRIER_SIGNAL_PCO_VALUE =
            TelephonyManager.ACTION_CARRIER_SIGNAL_PCO_VALUE;
    private static final String EXTRA_APN_TYPE_INT_KEY = TelephonyManager.EXTRA_APN_TYPE;
    private static final String EXTRA_PCO_ID_KEY = TelephonyManager.EXTRA_PCO_ID;
    private static final String EXTRA_PCO_VALUE_KEY = TelephonyManager.EXTRA_PCO_VALUE;

    private static final String TEST_PCO_STRING = "testPcoData";
    private byte[] pcoData = TEST_PCO_STRING.getBytes();
    private static int testSubId = 5;
    private static int testSlotId = 6;
    private static int testPcoIdIPv6 = 0xFF01;
    private static int testPcoIdIPv4 = 0xFF02;

    MockitoSession mStaticMockSession;
    @Mock private Context mMockContext;
    @Mock private Network mMockNetwork;
    @Mock private EpdgSelector mMockEpdgSelector;
    @Mock private IwlanEventListener mMockIwlanEventListener;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mStaticMockSession =
                mockitoSession()
                        .mockStatic(EpdgSelector.class)
                        .mockStatic(IwlanDataService.class)
                        .mockStatic(IwlanHelper.class)
                        .mockStatic(SubscriptionManager.class)
                        .mockStatic(IwlanEventListener.class)
                        .startMocking();

        lenient().when(SubscriptionManager.getSlotIndex(eq(testSubId))).thenReturn(testSlotId);

        lenient().when(IwlanDataService.getContext()).thenReturn(mMockContext);

        lenient()
                .when(EpdgSelector.getSelectorInstance(eq(mMockContext), eq(testSlotId)))
                .thenReturn(mMockEpdgSelector);

        lenient()
                .when(
                        IwlanHelper.getConfig(
                                eq(CarrierConfigManager.Iwlan.KEY_EPDG_PCO_ID_IPV6_INT),
                                any(),
                                eq(testSlotId)))
                .thenReturn(testPcoIdIPv6);

        lenient()
                .when(
                        IwlanHelper.getConfig(
                                eq(CarrierConfigManager.Iwlan.KEY_EPDG_PCO_ID_IPV4_INT),
                                any(),
                                eq(testSlotId)))
                .thenReturn(testPcoIdIPv4);

        lenient()
                .when(IwlanEventListener.getInstance(eq(mMockContext), eq(testSlotId)))
                .thenReturn(mMockIwlanEventListener);

        // New BroadcastReceiver object
        mBroadcastReceiver = new IwlanBroadcastReceiver();
    }

    @After
    public void cleanUp() throws Exception {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testOnReceiveNoPcoData() throws Exception {
        onReceiveMethodWithArgs(ApnSetting.TYPE_IMS, testPcoIdIPv6, null);

        // Verify the called times of setPcoData method
        verify(mMockEpdgSelector, times(0)).setPcoData(anyInt(), any(byte[].class));
    }

    @Test
    public void testOnReceiveIPv6Pass() throws Exception {
        onReceiveMethodWithArgs(ApnSetting.TYPE_IMS, testPcoIdIPv6);

        // Verify the called times of setPcoData method
        verify(mMockEpdgSelector, times(1)).setPcoData(testPcoIdIPv6, pcoData);
    }

    @Test
    public void testOnReceiveIPv4Pass() throws Exception {
        onReceiveMethodWithArgs(ApnSetting.TYPE_IMS, testPcoIdIPv4);

        // Verify the called times of setPcoData method
        verify(mMockEpdgSelector, times(1)).setPcoData(testPcoIdIPv4, pcoData);
    }

    @Test
    public void testOnReceiveIncorrectApnType() throws Exception {
        onReceiveMethodWithArgs(ApnSetting.TYPE_DEFAULT, testPcoIdIPv6);

        // Verify the called times of setPcoData method
        verify(mMockEpdgSelector, times(0)).setPcoData(testPcoIdIPv6, pcoData);
    }

    @Test
    public void testOnReceiveMethodIncorrectPcoId() throws Exception {
        onReceiveMethodWithArgs(ApnSetting.TYPE_IMS, 0xFF00);

        // Verify the called times of setPcoData method
        verify(mMockEpdgSelector, times(0)).setPcoData(0xFF00, pcoData);
    }

    @Test
    public void testCarrierConfigChanged() throws Exception {
        final Intent intent = new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        intent.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, testSlotId);

        // Trigger the onReceive
        mBroadcastReceiver.onReceive(mMockContext, intent);

        verify(mMockIwlanEventListener).onBroadcastReceived(intent);
    }

    @Test
    public void testWifiStateChanged() throws Exception {
        final Intent intent = new Intent(WifiManager.WIFI_STATE_CHANGED_ACTION);
        intent.putExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_DISABLED);

        // Trigger broadcast
        mBroadcastReceiver.onReceive(mMockContext, intent);

        verify(mMockIwlanEventListener).onBroadcastReceived(intent);
    }
    private void onReceiveMethodWithArgs(int apnType, int pcoId) {
        // Create intent object
        final Intent mIntent = new Intent(ACTION_CARRIER_SIGNAL_PCO_VALUE);
        mIntent.putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, testSubId);
        mIntent.putExtra(EXTRA_APN_TYPE_INT_KEY, apnType);
        mIntent.putExtra(EXTRA_PCO_ID_KEY, pcoId);
        mIntent.putExtra(EXTRA_PCO_VALUE_KEY, pcoData);

        // Trigger onReceive method
        mBroadcastReceiver.onReceive(mMockContext, mIntent);
    }

    private void onReceiveMethodWithArgs(int apnType, int pcoId, byte[] pcoData) {
        // Create intent object
        final Intent mIntent = new Intent(ACTION_CARRIER_SIGNAL_PCO_VALUE);
        mIntent.putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, testSubId);
        mIntent.putExtra(EXTRA_APN_TYPE_INT_KEY, apnType);
        mIntent.putExtra(EXTRA_PCO_ID_KEY, pcoId);
        mIntent.putExtra(EXTRA_PCO_VALUE_KEY, pcoData);

        // Trigger onReceive method
        mBroadcastReceiver.onReceive(mMockContext, mIntent);
    }
}
