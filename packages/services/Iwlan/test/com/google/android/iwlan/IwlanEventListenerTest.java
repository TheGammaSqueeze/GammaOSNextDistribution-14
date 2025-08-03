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

import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.telephony.CellInfo;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;

import com.google.android.iwlan.flags.FeatureFlags;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.ArrayList;
import java.util.List;

public class IwlanEventListenerTest {
    private static final String TAG = "IwlanEventListenerTest";

    @Mock private Context mMockContext;
    @Mock private Handler mMockHandler;
    @Mock private Message mMockMessage;
    @Mock private Message mMockMessage_2;
    @Mock private WifiManager mMockWifiManager;
    @Mock private WifiInfo mMockWifiInfo;
    @Mock private SubscriptionManager mMockSubscriptionManager;
    @Mock private SubscriptionInfo mMockSubscriptionInfo;
    @Mock private ContentResolver mMockContentResolver;
    @Mock private ImsManager mMockImsManager;
    @Mock private ImsMmTelManager mMockImsMmTelManager;
    @Mock private TelephonyManager mMockTelephonyManager;
    @Mock private FeatureFlags mFakeFeatureFlags;

    private static final int DEFAULT_SLOT_INDEX = 0;
    private static final int OTHER_SLOT_INDEX = 1;
    private static final int DEFAULT_SUB_ID = 0;
    private static final int DEFAULT_CARRIER_INDEX = 0;
    private static final String WIFI_SSID_1 = "TEST_AP_NAME_1";
    private static final String WIFI_SSID_2 = "TEST_AP_NAME_2";
    private static final Uri CROSS_SIM_URI =
            Uri.parse("content://telephony/siminfo/cross_sim_calling_enabled/2");
    private static final Uri WFC_ENABLED_URI = Uri.parse("content://telephony/siminfo/wfc/2");
    private IwlanEventListener mIwlanEventListener;
    private IwlanEventListener mSpyIwlanEventListener;
    private List<Integer> events;

    MockitoSession mStaticMockSession;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mStaticMockSession =
                mockitoSession()
                        .mockStatic(IwlanHelper.class)
                        .mockStatic(SubscriptionManager.class)
                        .mockStatic(TelephonyManager.class)
                        .startMocking();

        when(mMockContext.getSystemService(eq(WifiManager.class))).thenReturn(mMockWifiManager);

        when(mMockWifiManager.getConnectionInfo()).thenReturn(mMockWifiInfo);

        when(mMockContext.getSystemService(eq(SubscriptionManager.class)))
                .thenReturn(mMockSubscriptionManager);

        when(mMockSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(anyInt()))
                .thenReturn(mMockSubscriptionInfo);

        when(mMockContext.getContentResolver()).thenReturn(mMockContentResolver);

        when(mMockImsMmTelManager.isVoWiFiSettingEnabled()).thenReturn(true).thenReturn(false);

        when(mMockImsManager.getImsMmTelManager(anyInt())).thenReturn(mMockImsMmTelManager);

        when(mMockContext.getSystemService(eq(ImsManager.class))).thenReturn(mMockImsManager);

        when(mMockContext.getSystemService(eq(TelephonyManager.class)))
                .thenReturn(mMockTelephonyManager);

        when(mMockTelephonyManager.createForSubscriptionId(eq(0)))
                .thenReturn(mMockTelephonyManager);

        IwlanEventListener.resetAllInstances();
        mIwlanEventListener = IwlanEventListener.getInstance(mMockContext, DEFAULT_SLOT_INDEX);
        mSpyIwlanEventListener =
                spy(new IwlanEventListener(mMockContext, DEFAULT_SLOT_INDEX, mFakeFeatureFlags));
    }

    @After
    public void cleanUp() throws Exception {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testWifiApChanged() throws Exception {
        when(mMockHandler.obtainMessage(
                        eq(IwlanEventListener.WIFI_AP_CHANGED_EVENT),
                        eq(DEFAULT_SLOT_INDEX),
                        anyInt()))
                .thenReturn(mMockMessage);

        events = new ArrayList<Integer>();
        events.add(IwlanEventListener.WIFI_AP_CHANGED_EVENT);
        mIwlanEventListener.addEventListener(events, mMockHandler);

        // First Wifi connected should not trigger WIFI_AP_CHANGED_EVENT
        when(mMockWifiInfo.getSSID()).thenReturn(WIFI_SSID_1);
        mIwlanEventListener.onWifiConnected(mMockContext);
        verify(mMockMessage, times(0)).sendToTarget();

        when(mMockWifiInfo.getSSID()).thenReturn(WIFI_SSID_2);
        mIwlanEventListener.onWifiConnected(mMockContext);
        verify(mMockMessage, times(1)).sendToTarget();
    }

    @Test
    public void testCrossSimCallingSettingEnableChanged() throws Exception {
        when(mMockHandler.obtainMessage(
                        eq(IwlanEventListener.CROSS_SIM_CALLING_ENABLE_EVENT),
                        eq(DEFAULT_SLOT_INDEX),
                        anyInt()))
                .thenReturn(mMockMessage);

        events = new ArrayList<Integer>();
        events.add(IwlanEventListener.CROSS_SIM_CALLING_ENABLE_EVENT);
        mIwlanEventListener.addEventListener(events, mMockHandler);
        mIwlanEventListener.setCrossSimCallingUri(CROSS_SIM_URI);

        lenient()
                .when(
                        IwlanHelper.isCrossSimCallingEnabled(
                                eq(mMockContext), eq(DEFAULT_SLOT_INDEX)))
                .thenReturn(true);

        // Trigger CROSS_SIM_CALLING_ENABLE_EVENT when cross sim calling setting is enabled
        mIwlanEventListener.notifyCurrentSetting(CROSS_SIM_URI);
        verify(mMockMessage, times(1)).sendToTarget();
    }

    @Test
    public void testCrossSimCallingSettingDisableChanged() throws Exception {
        when(mMockHandler.obtainMessage(
                        eq(IwlanEventListener.CROSS_SIM_CALLING_DISABLE_EVENT),
                        eq(DEFAULT_SLOT_INDEX),
                        anyInt()))
                .thenReturn(mMockMessage);

        events = new ArrayList<Integer>();
        events.add(IwlanEventListener.CROSS_SIM_CALLING_DISABLE_EVENT);
        mIwlanEventListener.addEventListener(events, mMockHandler);
        mIwlanEventListener.setCrossSimCallingUri(CROSS_SIM_URI);

        lenient()
                .when(
                        IwlanHelper.isCrossSimCallingEnabled(
                                eq(mMockContext), eq(DEFAULT_SLOT_INDEX)))
                .thenReturn(false);

        // Trigger CROSS_SIM_CALLING_DISABLE_EVENT when cross sim calling setting is disabled
        mIwlanEventListener.notifyCurrentSetting(CROSS_SIM_URI);
        verify(mMockMessage, times(1)).sendToTarget();
    }

    @Test
    public void testOnReceivedCarrierConfigChangedIntent() throws Exception {
        when(mMockHandler.obtainMessage(
                        eq(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT),
                        eq(DEFAULT_SLOT_INDEX),
                        anyInt()))
                .thenReturn(mMockMessage);
        when(mMockHandler.obtainMessage(
                        eq(IwlanEventListener.CARRIER_CONFIG_UNKNOWN_CARRIER_EVENT),
                        eq(DEFAULT_SLOT_INDEX),
                        anyInt()))
                .thenReturn(mMockMessage_2);

        events = new ArrayList<Integer>();
        events.add(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT);
        events.add(IwlanEventListener.CARRIER_CONFIG_UNKNOWN_CARRIER_EVENT);
        mIwlanEventListener.addEventListener(events, mMockHandler);

        // onCarrierConfigChanged with valid Carrier id
        mIwlanEventListener.onCarrierConfigChanged(
                mMockContext, DEFAULT_SLOT_INDEX, DEFAULT_SUB_ID, DEFAULT_CARRIER_INDEX);

        verify(mMockMessage, times(1)).sendToTarget();

        // onCarrierConfigChanged with invalid Carrier id
        mIwlanEventListener.onCarrierConfigChanged(
                mMockContext,
                DEFAULT_SLOT_INDEX,
                DEFAULT_SUB_ID,
                TelephonyManager.UNKNOWN_CARRIER_ID);

        verify(mMockMessage_2, times(1)).sendToTarget();
    }

    @Test
    public void testWfcSettingChanged() throws Exception {
        when(mMockHandler.obtainMessage(
                        eq(IwlanEventListener.WIFI_CALLING_ENABLE_EVENT),
                        eq(DEFAULT_SLOT_INDEX),
                        anyInt()))
                .thenReturn(mMockMessage);
        when(mMockHandler.obtainMessage(
                        eq(IwlanEventListener.WIFI_CALLING_DISABLE_EVENT),
                        eq(DEFAULT_SLOT_INDEX),
                        anyInt()))
                .thenReturn(mMockMessage_2);

        events = new ArrayList<Integer>();
        events.add(IwlanEventListener.WIFI_CALLING_ENABLE_EVENT);
        events.add(IwlanEventListener.WIFI_CALLING_DISABLE_EVENT);
        mIwlanEventListener.addEventListener(events, mMockHandler);
        mIwlanEventListener.setWfcEnabledUri(WFC_ENABLED_URI);

        mIwlanEventListener.notifyCurrentSetting(WFC_ENABLED_URI);
        verify(mMockMessage, times(1)).sendToTarget();

        mIwlanEventListener.notifyCurrentSetting(WFC_ENABLED_URI);
        verify(mMockMessage_2, times(1)).sendToTarget();
    }

    @Test
    public void testCellInfoChanged() throws Exception {
        List<CellInfo> arrayCi = new ArrayList<>();
        lenient()
                .when(IwlanHelper.getSubId(eq(mMockContext), eq(DEFAULT_SLOT_INDEX)))
                .thenReturn(0);

        when(mMockHandler.obtainMessage(
                        eq(IwlanEventListener.CELLINFO_CHANGED_EVENT),
                        eq(DEFAULT_SLOT_INDEX),
                        anyInt(),
                        eq(arrayCi)))
                .thenReturn(mMockMessage);

        events = new ArrayList<Integer>();
        events.add(IwlanEventListener.CELLINFO_CHANGED_EVENT);
        mIwlanEventListener.addEventListener(events, mMockHandler);

        mIwlanEventListener.registerTelephonyCallback();

        TelephonyCallback.CellInfoListener mTelephonyCallback =
                mIwlanEventListener.getTelephonyCallback();
        mTelephonyCallback.onCellInfoChanged(arrayCi);

        verify(mMockMessage, times(1)).sendToTarget();
    }

    @Test
    public void testCallStateChanged() throws Exception {
        when(mMockHandler.obtainMessage(
                        eq(IwlanEventListener.CALL_STATE_CHANGED_EVENT),
                        eq(DEFAULT_SLOT_INDEX),
                        eq(TelephonyManager.CALL_STATE_OFFHOOK)))
                .thenReturn(mMockMessage);

        events = new ArrayList<Integer>();
        events.add(IwlanEventListener.CALL_STATE_CHANGED_EVENT);
        mIwlanEventListener.addEventListener(events, mMockHandler);

        mIwlanEventListener.registerTelephonyCallback();

        TelephonyCallback.CallStateListener mTelephonyCallback =
                mIwlanEventListener.getTelephonyCallback();
        mTelephonyCallback.onCallStateChanged(TelephonyManager.CALL_STATE_OFFHOOK);

        verify(mMockMessage, times(1)).sendToTarget();
    }

    @Test
    public void testCallStateChangedMultipleSlots() throws Exception {
        IwlanEventListener otherSlotIwlanEventListener =
                IwlanEventListener.getInstance(mMockContext, OTHER_SLOT_INDEX);

        when(mMockHandler.obtainMessage(
                        eq(IwlanEventListener.CALL_STATE_CHANGED_EVENT),
                        eq(DEFAULT_SLOT_INDEX),
                        eq(TelephonyManager.CALL_STATE_OFFHOOK)))
                .thenReturn(mMockMessage);
        when(mMockHandler.obtainMessage(
                        eq(IwlanEventListener.CALL_STATE_CHANGED_EVENT),
                        eq(OTHER_SLOT_INDEX),
                        eq(TelephonyManager.CALL_STATE_OFFHOOK)))
                .thenReturn(mMockMessage_2);

        events = new ArrayList<Integer>();
        events.add(IwlanEventListener.CALL_STATE_CHANGED_EVENT);
        mIwlanEventListener.addEventListener(events, mMockHandler);
        otherSlotIwlanEventListener.addEventListener(events, mMockHandler);

        mIwlanEventListener.registerTelephonyCallback();
        otherSlotIwlanEventListener.registerTelephonyCallback();

        TelephonyCallback.CallStateListener mTelephonyCallback =
                mIwlanEventListener.getTelephonyCallback();
        mTelephonyCallback.onCallStateChanged(TelephonyManager.CALL_STATE_OFFHOOK);

        verify(mMockMessage, times(1)).sendToTarget();
        verify(mMockMessage_2, never()).sendToTarget();
    }

    @Test
    public void testWfcChangeThrowIAE() throws Exception {
        when(mMockHandler.obtainMessage(
                        eq(IwlanEventListener.WIFI_CALLING_DISABLE_EVENT),
                        eq(DEFAULT_SLOT_INDEX),
                        anyInt()))
                .thenReturn(mMockMessage);

        events = new ArrayList<Integer>();
        events.add(IwlanEventListener.WIFI_CALLING_DISABLE_EVENT);
        mIwlanEventListener.addEventListener(events, mMockHandler);
        mIwlanEventListener.setWfcEnabledUri(WFC_ENABLED_URI);

        doThrow(new IllegalArgumentException("IllegalArgumentException at isVoWiFiSettingEnabled"))
                .when(mMockImsMmTelManager)
                .isVoWiFiSettingEnabled();

        mIwlanEventListener.notifyCurrentSetting(WFC_ENABLED_URI);
        verify(mMockMessage, times(1)).sendToTarget();
    }

    @SuppressLint("MissingPermission")
    @Test
    public void testDisable5gViaUi() throws Exception {
        when(mMockHandler.obtainMessage(
                        eq(IwlanEventListener.PREFERRED_NETWORK_TYPE_CHANGED_EVENT),
                        eq(DEFAULT_SLOT_INDEX),
                        anyInt(),
                        eq(TelephonyManager.NETWORK_TYPE_BITMASK_LTE)))
                .thenReturn(mMockMessage);

        events = new ArrayList<>();
        events.add(IwlanEventListener.PREFERRED_NETWORK_TYPE_CHANGED_EVENT);
        mIwlanEventListener.addEventListener(events, mMockHandler);
        mSpyIwlanEventListener.registerTelephonyCallback();
        TelephonyCallback.AllowedNetworkTypesListener mTelephonyCallback =
                mSpyIwlanEventListener.getTelephonyCallback();

        mTelephonyCallback.onAllowedNetworkTypesChanged(
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER,
                TelephonyManager.NETWORK_TYPE_BITMASK_LTE);
        verify(mMockMessage, times(1)).sendToTarget();
    }

    @SuppressLint("MissingPermission")
    @Test
    public void testEnable5gViaUi() throws Exception {
        when(mMockHandler.obtainMessage(
                        eq(IwlanEventListener.PREFERRED_NETWORK_TYPE_CHANGED_EVENT),
                        eq(DEFAULT_SLOT_INDEX),
                        anyInt(),
                        eq(TelephonyManager.NETWORK_TYPE_BITMASK_NR)))
                .thenReturn(mMockMessage);

        events = new ArrayList<>();
        events.add(IwlanEventListener.PREFERRED_NETWORK_TYPE_CHANGED_EVENT);
        mIwlanEventListener.addEventListener(events, mMockHandler);
        mSpyIwlanEventListener.registerTelephonyCallback();
        TelephonyCallback.AllowedNetworkTypesListener mTelephonyCallback =
                mSpyIwlanEventListener.getTelephonyCallback();

        mTelephonyCallback.onAllowedNetworkTypesChanged(
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER,
                TelephonyManager.NETWORK_TYPE_BITMASK_NR);
        verify(mMockMessage, times(1)).sendToTarget();
    }
}
