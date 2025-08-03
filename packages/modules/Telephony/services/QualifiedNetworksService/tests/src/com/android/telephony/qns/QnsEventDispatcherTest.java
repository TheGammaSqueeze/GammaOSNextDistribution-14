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

package com.android.telephony.qns;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.telephony.qns.wfc.WfcActivationHelper.ACTION_TRY_WFC_CONNECTION;
import static com.android.telephony.qns.wfc.WfcActivationHelper.EXTRA_SUB_ID;
import static com.android.telephony.qns.wfc.WfcActivationHelper.EXTRA_TRY_STATUS;
import static com.android.telephony.qns.wfc.WfcActivationHelper.STATUS_END;
import static com.android.telephony.qns.wfc.WfcActivationHelper.STATUS_START;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.TelephonyIntents;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.ArrayList;
import java.util.List;

public class QnsEventDispatcherTest extends QnsTest {

    @Mock private Context mMockContext;
    @Mock private Handler mMockHandler;
    @Mock private Message mMockMessage;
    @Mock private Message mMockMessage1;
    @Mock private Message mMockMessage2;
    @Mock private Message mMockMessage3;
    @Mock private Message mMockMessage4;
    @Mock private Message mMockMessage5;
    @Mock private WifiManager mMockWifiManager;
    @Mock private SubscriptionManager mMockSubscriptionManager;
    @Mock private SubscriptionInfo mMockSubscriptionInfo;
    @Mock private ContentResolver mMockContentResolver;
    @Mock private TelephonyManager mMockTelephonyManager;
    @Mock private ConnectivityManager mMockConnectivityManager;

    private static final int DEFAULT_SLOT_INDEX = 0;
    private static final int DEFAULT_CARRIER_INDEX = 0;
    private static final Uri CROSS_SIM_URI =
            Uri.parse("content://telephony/siminfo/cross_sim_calling_enabled/0");
    private static final Uri WFC_ENABLED_URI = Uri.parse("content://telephony/siminfo/wfc/0");
    private static final Uri WFC_MODE_URI = Uri.parse("content://telephony/siminfo/wfc_mode/0");
    private static final Uri WFC_ROAMING_ENABLED_URI =
            Uri.parse("content://telephony/siminfo/wfc_roaming_enabled/0");
    private static final Uri WFC_ROAMING_MODE_URI =
            Uri.parse("content://telephony/siminfo/wfc_roaming_mode/0");
    private QnsEventDispatcher mQnsEventDispatcher;

    MockitoSession mStaticMockSession;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        super.setUp();

        mStaticMockSession =
                mockitoSession()
                        .mockStatic(SubscriptionManager.class)
                        .mockStatic(Settings.Global.class)
                        .startMocking();

        when(mMockContext.getSystemService(eq(WifiManager.class))).thenReturn(mMockWifiManager);
        when(mMockContext.getSystemService(eq(SubscriptionManager.class)))
                .thenReturn(mMockSubscriptionManager);
        when(mMockSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(
                        eq(DEFAULT_SLOT_INDEX)))
                .thenReturn(mMockSubscriptionInfo);
        when(mMockContext.getContentResolver()).thenReturn(mMockContentResolver);
        when(mMockContext.getSystemService(eq(TelephonyManager.class)))
                .thenReturn(mMockTelephonyManager);
        when(mMockTelephonyManager.createForSubscriptionId(eq(0)))
                .thenReturn(mMockTelephonyManager);
        when(mMockContext.getSystemService(eq(ConnectivityManager.class)))
                .thenReturn(mMockConnectivityManager);
        lenient()
                .when(
                        Settings.Global.getInt(
                                mMockContentResolver, Settings.Global.AIRPLANE_MODE_ON, 0))
                .thenReturn(0);

        when(mMockSubscriptionInfo.getSubscriptionId()).thenReturn(0);

        mQnsEventDispatcher =
                new QnsEventDispatcher(
                        sMockContext, mMockQnsProvisioningListener, mMockQnsImsManager, 0);
        // wait for QnsEventDispatcher to complete initial setting load
        waitForLastHandlerAction(mQnsEventDispatcher.mQnsEventDispatcherHandler);
    }

    @After
    public void cleanUp() {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testOnReceivedCarrierConfigChangedIntent() {
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_CARRIER_CONFIG_CHANGED)))
                .thenReturn(mMockMessage);
        when(mMockHandler.obtainMessage(
                        eq(QnsEventDispatcher.QNS_EVENT_CARRIER_CONFIG_UNKNOWN_CARRIER)))
                .thenReturn(mMockMessage2);

        List<Integer> events = new ArrayList<Integer>();
        events.add(QnsEventDispatcher.QNS_EVENT_CARRIER_CONFIG_CHANGED);
        events.add(QnsEventDispatcher.QNS_EVENT_CARRIER_CONFIG_UNKNOWN_CARRIER);
        mQnsEventDispatcher.registerEvent(events, mMockHandler);

        // Send ACTION_CARRIER_CONFIG_CHANGED intent with valid Carrier id
        final Intent validCarrierIdIntent =
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        validCarrierIdIntent.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, DEFAULT_SLOT_INDEX);
        validCarrierIdIntent.putExtra(TelephonyManager.EXTRA_CARRIER_ID, DEFAULT_CARRIER_INDEX);
        mQnsEventDispatcher.mIntentReceiver.onReceive(mMockContext, validCarrierIdIntent);
        verify(mMockMessage, times(1)).sendToTarget();

        // Send ACTION_CARRIER_CONFIG_CHANGED intent with invalid Carrier id
        final Intent invalidCarrierIdIntent =
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        invalidCarrierIdIntent.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, DEFAULT_SLOT_INDEX);
        invalidCarrierIdIntent.putExtra(
                TelephonyManager.EXTRA_CARRIER_ID, TelephonyManager.UNKNOWN_CARRIER_ID);
        mQnsEventDispatcher.mIntentReceiver.onReceive(mMockContext, invalidCarrierIdIntent);
        verify(mMockMessage2, times(1)).sendToTarget();
    }

    @Test
    public void testOnReceivedCarrierConfigChangedIntentWithInvalidSlot() {
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_CARRIER_CONFIG_CHANGED)))
                .thenReturn(mMockMessage);
        when(mMockHandler.obtainMessage(
                        eq(QnsEventDispatcher.QNS_EVENT_CARRIER_CONFIG_UNKNOWN_CARRIER)))
                .thenReturn(mMockMessage2);

        List<Integer> events = new ArrayList<Integer>();
        events.add(QnsEventDispatcher.QNS_EVENT_CARRIER_CONFIG_CHANGED);
        events.add(QnsEventDispatcher.QNS_EVENT_CARRIER_CONFIG_UNKNOWN_CARRIER);
        mQnsEventDispatcher.registerEvent(events, mMockHandler);

        // ACTION_CARRIER_CONFIG_CHANGED intent with valid Carrier id to be not received
        final Intent validCarrierIdIntent =
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        validCarrierIdIntent.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, 1);
        validCarrierIdIntent.putExtra(TelephonyManager.EXTRA_CARRIER_ID, DEFAULT_CARRIER_INDEX);
        mQnsEventDispatcher.mIntentReceiver.onReceive(mMockContext, validCarrierIdIntent);
        verify(mMockMessage, never()).sendToTarget();

        // Send ACTION_CARRIER_CONFIG_CHANGED intent with invalid Carrier id to be not received
        final Intent invalidCarrierIdIntent =
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        invalidCarrierIdIntent.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, 1);
        invalidCarrierIdIntent.putExtra(
                TelephonyManager.EXTRA_CARRIER_ID, TelephonyManager.UNKNOWN_CARRIER_ID);
        mQnsEventDispatcher.mIntentReceiver.onReceive(mMockContext, invalidCarrierIdIntent);
        verify(mMockMessage2, never()).sendToTarget();
    }

    @Test
    public void testOnSimLoadedStateEvent() {
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_SIM_LOADED)))
                .thenReturn(mMockMessage);

        List<Integer> events = new ArrayList<Integer>();
        events.add(QnsEventDispatcher.QNS_EVENT_SIM_LOADED);
        mQnsEventDispatcher.registerEvent(events, mMockHandler);

        // Send ACTION_SIM_CARD_STATE_CHANGED intent with SIM_STATE_LOADED
        final Intent validSimCardStateintent =
                new Intent(TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED);
        validSimCardStateintent.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, DEFAULT_SLOT_INDEX);
        validSimCardStateintent.putExtra(
                TelephonyManager.EXTRA_SIM_STATE, TelephonyManager.SIM_STATE_LOADED);
        mQnsEventDispatcher.mIntentReceiver.onReceive(mMockContext, validSimCardStateintent);
        verify(mMockMessage, times(1)).sendToTarget();

        // Send ACTION_SIM_APPLICATION_STATE_CHANGED intent with SIM_STATE_LOADED
        final Intent validSimCardStateintent1 =
                new Intent(TelephonyManager.ACTION_SIM_APPLICATION_STATE_CHANGED);
        validSimCardStateintent1.putExtra(
                CarrierConfigManager.EXTRA_SLOT_INDEX, DEFAULT_SLOT_INDEX);
        validSimCardStateintent1.putExtra(
                TelephonyManager.EXTRA_SIM_STATE, TelephonyManager.SIM_STATE_LOADED);
        mQnsEventDispatcher.mIntentReceiver.onReceive(mMockContext, validSimCardStateintent1);
        verify(mMockMessage, atLeast(2)).sendToTarget();
    }

    @Test
    public void testOnSimUnknownStateEvent() {
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_SIM_ABSENT)))
                .thenReturn(mMockMessage);

        List<Integer> events = new ArrayList<Integer>();
        events.add(QnsEventDispatcher.QNS_EVENT_SIM_ABSENT);
        mQnsEventDispatcher.registerEvent(events, mMockHandler);

        // Send ACTION_SIM_CARD_STATE_CHANGED intent with SIM_STATE_ABSENT
        final Intent validSimCardStateintent =
                new Intent(TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED);
        validSimCardStateintent.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, DEFAULT_SLOT_INDEX);
        validSimCardStateintent.putExtra(
                TelephonyManager.EXTRA_SIM_STATE, TelephonyManager.SIM_STATE_ABSENT);
        mQnsEventDispatcher.mIntentReceiver.onReceive(mMockContext, validSimCardStateintent);
        verify(mMockMessage, times(1)).sendToTarget();

        // Send ACTION_SIM_APPLICATION_STATE_CHANGED intent with SIM_STATE_ABSENT
        final Intent validSimCardStateintent1 =
                new Intent(TelephonyManager.ACTION_SIM_APPLICATION_STATE_CHANGED);
        validSimCardStateintent1.putExtra(
                CarrierConfigManager.EXTRA_SLOT_INDEX, DEFAULT_SLOT_INDEX);
        validSimCardStateintent1.putExtra(
                TelephonyManager.EXTRA_SIM_STATE, TelephonyManager.SIM_STATE_ABSENT);
        mQnsEventDispatcher.mIntentReceiver.onReceive(mMockContext, validSimCardStateintent1);
        verify(mMockMessage, atLeast(2)).sendToTarget();
    }

    @Test
    public void testOnSimStateEventWithInvalidSlot() {
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_SIM_LOADED)))
                .thenReturn(mMockMessage);

        List<Integer> events = new ArrayList<Integer>();
        events.add(QnsEventDispatcher.QNS_EVENT_SIM_LOADED);
        mQnsEventDispatcher.registerEvent(events, mMockHandler);

        // Send ACTION_SIM_CARD_STATE_CHANGED intent with SIM_STATE_LOADED
        final Intent validSimCardStateintent =
                new Intent(TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED);
        validSimCardStateintent.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, 1);
        validSimCardStateintent.putExtra(
                TelephonyManager.EXTRA_SIM_STATE, TelephonyManager.SIM_STATE_LOADED);
        mQnsEventDispatcher.mIntentReceiver.onReceive(mMockContext, validSimCardStateintent);
        verify(mMockMessage, never()).sendToTarget();

        // Send ACTION_SIM_APPLICATION_STATE_CHANGED intent with SIM_STATE_LOADED
        final Intent validSimCardStateintent1 =
                new Intent(TelephonyManager.ACTION_SIM_APPLICATION_STATE_CHANGED);
        validSimCardStateintent1.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, 1);
        validSimCardStateintent1.putExtra(
                TelephonyManager.EXTRA_SIM_STATE, TelephonyManager.SIM_STATE_ABSENT);
        mQnsEventDispatcher.mIntentReceiver.onReceive(mMockContext, validSimCardStateintent1);
        verify(mMockMessage, never()).sendToTarget();
    }

    @Test
    public void testOnAirplaneModeEvent() {
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_APM_ENABLED)))
                .thenReturn(mMockMessage);
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_APM_DISABLED)))
                .thenReturn(mMockMessage2);

        List<Integer> events = new ArrayList<Integer>();
        events.add(QnsEventDispatcher.QNS_EVENT_APM_ENABLED);
        events.add(QnsEventDispatcher.QNS_EVENT_APM_DISABLED);
        mQnsEventDispatcher.registerEvent(events, mMockHandler);

        // Send ACTION_AIRPLANE_MODE_CHANGED intent with On
        final Intent validCarrierIdIntent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        validCarrierIdIntent.putExtra("state", true);
        mQnsEventDispatcher.mIntentReceiver.onReceive(mMockContext, validCarrierIdIntent);
        verify(mMockMessage, times(1)).sendToTarget();

        // Check for Duplicate Event Scenario On Case : ACTION_AIRPLANE_MODE_CHANGED
        final Intent validCarrierIdIntent1 = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        validCarrierIdIntent.putExtra("state", true);
        mQnsEventDispatcher.mIntentReceiver.onReceive(mMockContext, validCarrierIdIntent1);
        verify(mMockMessage, times(1)).sendToTarget();

        // Send ACTION_AIRPLANE_MODE_CHANGED intent with Off
        final Intent invalidCarrierIdIntent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        invalidCarrierIdIntent.putExtra("state", false);
        mQnsEventDispatcher.mIntentReceiver.onReceive(mMockContext, invalidCarrierIdIntent);
        verify(mMockMessage2, times(1)).sendToTarget();

        // Check for Duplicate Event Scenario Off Case:ACTION_AIRPLANE_MODE_CHANGED
        final Intent invalidCarrierIdIntent1 = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        invalidCarrierIdIntent.putExtra("state", false);
        mQnsEventDispatcher.mIntentReceiver.onReceive(mMockContext, invalidCarrierIdIntent1);
        verify(mMockMessage2, times(1)).sendToTarget();
    }

    @Test
    public void testOnWifiStateChangedEvent() {
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_WIFI_DISABLING)))
                .thenReturn(mMockMessage);

        List<Integer> events = new ArrayList<Integer>();
        events.add(QnsEventDispatcher.QNS_EVENT_WIFI_DISABLING);
        mQnsEventDispatcher.registerEvent(events, mMockHandler);

        // Send WIFI_STATE_CHANGED_ACTION intent with WIFI_STATE_DISABLING
        final Intent validWifiStateIntent = new Intent(WifiManager.WIFI_STATE_CHANGED_ACTION);
        validWifiStateIntent.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, DEFAULT_SLOT_INDEX);
        validWifiStateIntent.putExtra(
                WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_DISABLING);
        mQnsEventDispatcher.mIntentReceiver.onReceive(mMockContext, validWifiStateIntent);
        verify(mMockMessage, times(1)).sendToTarget();

        // Check for Duplicate Event Scenario :WIFI_STATE_CHANGED_ACTION
        final Intent validWifiStateIntent1 = new Intent(WifiManager.WIFI_STATE_CHANGED_ACTION);
        validWifiStateIntent1.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, DEFAULT_SLOT_INDEX);
        validWifiStateIntent1.putExtra(
                WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_DISABLING);
        mQnsEventDispatcher.mIntentReceiver.onReceive(mMockContext, validWifiStateIntent1);
        verify(mMockMessage, times(1)).sendToTarget();
    }

    @Test
    public void testOnEmergencyCallbackModeChanged() {
        when(mMockHandler.obtainMessage(
                        eq(QnsEventDispatcher.QNS_EVENT_EMERGENCY_CALLBACK_MODE_ON)))
                .thenReturn(mMockMessage);
        when(mMockHandler.obtainMessage(
                        eq(QnsEventDispatcher.QNS_EVENT_EMERGENCY_CALLBACK_MODE_OFF)))
                .thenReturn(mMockMessage2);

        List<Integer> events = new ArrayList<Integer>();
        events.add(QnsEventDispatcher.QNS_EVENT_EMERGENCY_CALLBACK_MODE_ON);
        events.add(QnsEventDispatcher.QNS_EVENT_EMERGENCY_CALLBACK_MODE_OFF);
        mQnsEventDispatcher.registerEvent(events, mMockHandler);

        // Send ACTION_EMERGENCY_CALLBACK_MODE_CHANGED  intent with On
        final Intent validEcbmIntent =
                new Intent(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED);
        validEcbmIntent.putExtra(TelephonyManager.EXTRA_PHONE_IN_ECM_STATE, true);
        mQnsEventDispatcher.mIntentReceiver.onReceive(mMockContext, validEcbmIntent);
        verify(mMockMessage, times(1)).sendToTarget();

        // Send ACTION_EMERGENCY_CALLBACK_MODE_CHANGED intent with Off
        final Intent validEcbmIntent1 =
                new Intent(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED);
        validEcbmIntent1.putExtra(TelephonyManager.EXTRA_PHONE_IN_ECM_STATE, false);
        mQnsEventDispatcher.mIntentReceiver.onReceive(mMockContext, validEcbmIntent1);
        verify(mMockMessage2, times(1)).sendToTarget();
    }

    @Test
    public void testNotifyImmediatelyWfcSettingsWithDisabledStatus() {
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_WFC_PLATFORM_DISABLED)))
                .thenReturn(mMockMessage);
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_WFC_DISABLED)))
                .thenReturn(mMockMessage1);
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_DISABLED)))
                .thenReturn(mMockMessage2);

        List<Integer> events = new ArrayList<Integer>();
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_PLATFORM_DISABLED);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_DISABLED);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_DISABLED);
        mQnsEventDispatcher.registerEvent(events, mMockHandler);

        // WFC settings Disable Case
        verify(mMockMessage, times(1)).sendToTarget();
        verify(mMockMessage1, times(1)).sendToTarget();
        verify(mMockMessage2, times(1)).sendToTarget();
    }

    @Test
    public void testNotifyImmediatelyWfcSettingsWithEnabledStatus() {
        setEnabledStatusForWfcSettingsCrossSimSettings();

        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_WFC_PLATFORM_ENABLED)))
                .thenReturn(mMockMessage);
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_WFC_ENABLED)))
                .thenReturn(mMockMessage1);
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_ENABLED)))
                .thenReturn(mMockMessage2);

        List<Integer> events = new ArrayList<>();
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_PLATFORM_ENABLED);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_ENABLED);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_ENABLED);
        mQnsEventDispatcher.registerEvent(events, mMockHandler);

        // WFC settings Enabled Case
        verify(mMockMessage, times(1)).sendToTarget();
        verify(mMockMessage1, times(1)).sendToTarget();
        verify(mMockMessage2, times(1)).sendToTarget();
    }

    @Test
    public void testNotifyImmediatelyDifferentWfcModes() {
        setEnabledStatusForWfcSettingsCrossSimSettings();
        when(mMockQnsImsManager.getWfcMode(false)).thenReturn(1, 2);

        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_WFC_MODE_TO_WIFI_ONLY)))
                .thenReturn(mMockMessage);
        when(mMockHandler.obtainMessage(
                        eq(QnsEventDispatcher.QNS_EVENT_WFC_MODE_TO_CELLULAR_PREFERRED)))
                .thenReturn(mMockMessage1);
        when(mMockHandler.obtainMessage(
                        eq(QnsEventDispatcher.QNS_EVENT_WFC_MODE_TO_WIFI_PREFERRED)))
                .thenReturn(mMockMessage2);

        List<Integer> events = new ArrayList<Integer>();
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_MODE_TO_WIFI_ONLY);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_MODE_TO_CELLULAR_PREFERRED);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_MODE_TO_WIFI_PREFERRED);
        mQnsEventDispatcher.registerEvent(events, mMockHandler);

        // WFC Modes Enabled Case
        verify(mMockMessage, times(1)).sendToTarget();
        mQnsEventDispatcher.mUserSettingObserver.onChange(true, WFC_MODE_URI);
        verify(mMockMessage1, times(1)).sendToTarget();
        mQnsEventDispatcher.mUserSettingObserver.onChange(true, WFC_MODE_URI);
        verify(mMockMessage2, times(1)).sendToTarget();
    }

    @Test
    public void testNotifyCurrentSettingWithDisabledStatus() {
        setDisabledStatusForWfcSettingsCrossSimSettings();
        when(mMockHandler.obtainMessage(
                        eq(QnsEventDispatcher.QNS_EVENT_CROSS_SIM_CALLING_DISABLED)))
                .thenReturn(mMockMessage);
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_WFC_DISABLED)))
                .thenReturn(mMockMessage1);
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_WFC_MODE_TO_WIFI_ONLY)))
                .thenReturn(mMockMessage4);
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_DISABLED)))
                .thenReturn(mMockMessage2);
        when(mMockHandler.obtainMessage(
                        eq(QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_MODE_TO_WIFI_ONLY)))
                .thenReturn(mMockMessage5);
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_CARRIER_CONFIG_CHANGED)))
                .thenReturn(mMockMessage3);

        List<Integer> events = new ArrayList<Integer>();
        events.add(QnsEventDispatcher.QNS_EVENT_CROSS_SIM_CALLING_DISABLED);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_DISABLED);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_MODE_TO_WIFI_ONLY);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_DISABLED);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_MODE_TO_WIFI_ONLY);
        events.add(QnsEventDispatcher.QNS_EVENT_CARRIER_CONFIG_CHANGED);
        mQnsEventDispatcher.registerEvent(events, mMockHandler);

        // Send ACTION_CARRIER_CONFIG_CHANGED intent with valid Carrier id
        final Intent validCarrierIdIntent =
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        validCarrierIdIntent.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, DEFAULT_SLOT_INDEX);
        validCarrierIdIntent.putExtra(TelephonyManager.EXTRA_CARRIER_ID, DEFAULT_CARRIER_INDEX);
        mQnsEventDispatcher.mIntentReceiver.onReceive(mMockContext, validCarrierIdIntent);
        verify(mMockMessage, times(2)).sendToTarget();
        verify(mMockMessage1, times(2)).sendToTarget();
        verify(mMockMessage4, times(2)).sendToTarget();
        verify(mMockMessage2, times(2)).sendToTarget();
        verify(mMockMessage5, times(2)).sendToTarget();
        verify(mMockMessage3, times(1)).sendToTarget();
    }

    @Test
    public void testNotifyCurrentSettingWithEnabledStatus() {
        setEnabledStatusForWfcSettingsCrossSimSettings();

        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_CROSS_SIM_CALLING_ENABLED)))
                .thenReturn(mMockMessage);
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_WFC_ENABLED)))
                .thenReturn(mMockMessage1);
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_WFC_MODE_TO_WIFI_ONLY)))
                .thenReturn(mMockMessage4);
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_ENABLED)))
                .thenReturn(mMockMessage2);
        when(mMockHandler.obtainMessage(
                        eq(QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_MODE_TO_WIFI_ONLY)))
                .thenReturn(mMockMessage5);
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_CARRIER_CONFIG_CHANGED)))
                .thenReturn(mMockMessage3);

        List<Integer> events = new ArrayList<>();
        events.add(QnsEventDispatcher.QNS_EVENT_CROSS_SIM_CALLING_ENABLED);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_ENABLED);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_MODE_TO_WIFI_ONLY);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_ENABLED);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_MODE_TO_WIFI_ONLY);
        events.add(QnsEventDispatcher.QNS_EVENT_CARRIER_CONFIG_CHANGED);
        mQnsEventDispatcher.registerEvent(events, mMockHandler);

        // Send ACTION_CARRIER_CONFIG_CHANGED intent with valid Carrier id
        final Intent validCarrierIdIntent =
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        validCarrierIdIntent.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, DEFAULT_SLOT_INDEX);
        validCarrierIdIntent.putExtra(TelephonyManager.EXTRA_CARRIER_ID, DEFAULT_CARRIER_INDEX);
        mQnsEventDispatcher.mIntentReceiver.onReceive(mMockContext, validCarrierIdIntent);
        verify(mMockMessage, times(2)).sendToTarget();
        verify(mMockMessage1, times(2)).sendToTarget();
        verify(mMockMessage4, times(2)).sendToTarget();
        verify(mMockMessage2, times(2)).sendToTarget();
        verify(mMockMessage5, times(2)).sendToTarget();
        verify(mMockMessage3, times(1)).sendToTarget();
    }

    private void setEnabledStatusForWfcSettingsCrossSimSettings() {
        when(mMockQnsImsManager.isCrossSimCallingEnabled()).thenReturn(true);
        when(mMockQnsImsManager.isWfcEnabledByPlatform()).thenReturn(true);
        when(mMockQnsImsManager.isWfcProvisionedOnDevice()).thenReturn(true);
        when(mMockQnsImsManager.isWfcRoamingEnabledByUser()).thenReturn(true);
        when(mMockQnsImsManager.isWfcEnabledByUser()).thenReturn(true);
        when(mMockQnsProvisioningListener.getLastProvisioningWfcRoamingEnabledInfo())
                .thenReturn(true);

        // Initialise stubbed variables in QnsUtils:
        final Intent validCarrierIdIntent =
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        validCarrierIdIntent.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, DEFAULT_SLOT_INDEX);
        validCarrierIdIntent.putExtra(TelephonyManager.EXTRA_CARRIER_ID, DEFAULT_CARRIER_INDEX);
        mQnsEventDispatcher.mIntentReceiver.onReceive(mMockContext, validCarrierIdIntent);
    }

    private void setDisabledStatusForWfcSettingsCrossSimSettings() {
        when(mMockQnsImsManager.isCrossSimCallingEnabled()).thenReturn(false);
        when(mMockQnsImsManager.isWfcEnabledByPlatform()).thenReturn(false);
        when(mMockQnsImsManager.isWfcProvisionedOnDevice()).thenReturn(false);
        when(mMockQnsImsManager.isWfcRoamingEnabledByUser()).thenReturn(false);
        when(mMockQnsImsManager.isWfcEnabledByUser()).thenReturn(false);
        when(mMockQnsProvisioningListener.getLastProvisioningWfcRoamingEnabledInfo())
                .thenReturn(false);

        // Initialise stubbed variables in QnsUtils:
        final Intent validCarrierIdIntent =
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        validCarrierIdIntent.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, DEFAULT_SLOT_INDEX);
        validCarrierIdIntent.putExtra(TelephonyManager.EXTRA_CARRIER_ID, DEFAULT_CARRIER_INDEX);
        mQnsEventDispatcher.mIntentReceiver.onReceive(mMockContext, validCarrierIdIntent);
    }

    @Test
    public void testUnregisterEventAndUserSettingObserver() {
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_CARRIER_CONFIG_CHANGED)))
                .thenReturn(mMockMessage);

        List<Integer> events = new ArrayList<Integer>();
        events.add(QnsEventDispatcher.QNS_EVENT_CARRIER_CONFIG_CHANGED);
        mQnsEventDispatcher.registerEvent(events, mMockHandler);

        // Send ACTION_CARRIER_CONFIG_CHANGED intent with valid Carrier id
        final Intent validCarrierIdIntent =
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        validCarrierIdIntent.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, DEFAULT_SLOT_INDEX);
        validCarrierIdIntent.putExtra(TelephonyManager.EXTRA_CARRIER_ID, DEFAULT_CARRIER_INDEX);
        mQnsEventDispatcher.mIntentReceiver.onReceive(mMockContext, validCarrierIdIntent);
        verify(mMockMessage, times(1)).sendToTarget();

        mQnsEventDispatcher.unregisterEvent(mMockHandler);
    }

    @Test
    public void testOnUserSettingChangedEventDisableStatus() {
        when(mMockHandler.obtainMessage(
                        eq(QnsEventDispatcher.QNS_EVENT_CROSS_SIM_CALLING_DISABLED)))
                .thenReturn(mMockMessage);
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_WFC_DISABLED)))
                .thenReturn(mMockMessage1);
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_WFC_MODE_TO_WIFI_ONLY)))
                .thenReturn(mMockMessage4);
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_DISABLED)))
                .thenReturn(mMockMessage2);
        when(mMockHandler.obtainMessage(
                        eq(QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_MODE_TO_WIFI_ONLY)))
                .thenReturn(mMockMessage5);
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_CARRIER_CONFIG_CHANGED)))
                .thenReturn(mMockMessage3);

        List<Integer> events = new ArrayList<Integer>();
        events.add(QnsEventDispatcher.QNS_EVENT_CROSS_SIM_CALLING_DISABLED);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_DISABLED);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_MODE_TO_WIFI_ONLY);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_DISABLED);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_MODE_TO_WIFI_ONLY);
        events.add(QnsEventDispatcher.QNS_EVENT_CARRIER_CONFIG_CHANGED);
        mQnsEventDispatcher.registerEvent(events, mMockHandler);

        // Send ACTION_CARRIER_CONFIG_CHANGED intent with valid Carrier id
        final Intent validCarrierIdIntent =
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        validCarrierIdIntent.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, DEFAULT_SLOT_INDEX);
        validCarrierIdIntent.putExtra(TelephonyManager.EXTRA_CARRIER_ID, DEFAULT_CARRIER_INDEX);
        mQnsEventDispatcher.mIntentReceiver.onReceive(mMockContext, validCarrierIdIntent);

        verify(mMockMessage, times(2)).sendToTarget();
        verify(mMockMessage1, times(2)).sendToTarget();
        verify(mMockMessage4, atLeastOnce()).sendToTarget();
        verify(mMockMessage2, times(2)).sendToTarget();
        verify(mMockMessage5, atLeastOnce()).sendToTarget();
        verify(mMockMessage3, times(1)).sendToTarget();

        mQnsEventDispatcher.mUserSettingObserver.onChange(true, CROSS_SIM_URI);
        mQnsEventDispatcher.mUserSettingObserver.onChange(true, WFC_ENABLED_URI);
        mQnsEventDispatcher.mUserSettingObserver.onChange(true, WFC_MODE_URI);
        mQnsEventDispatcher.mUserSettingObserver.onChange(true, WFC_ROAMING_ENABLED_URI);
        mQnsEventDispatcher.mUserSettingObserver.onChange(true, WFC_ROAMING_MODE_URI);

        verify(mMockMessage, times(2)).sendToTarget();
        verify(mMockMessage1, times(2)).sendToTarget();
        verify(mMockMessage4, atLeastOnce()).sendToTarget();
        verify(mMockMessage2, times(2)).sendToTarget();
        verify(mMockMessage5, atLeastOnce()).sendToTarget();
    }

    @Test
    public void testOnUserSettingChangedEventEnabledStatus() {
        setEnabledStatusForWfcSettingsCrossSimSettings();
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_CROSS_SIM_CALLING_ENABLED)))
                .thenReturn(mMockMessage);
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_WFC_ENABLED)))
                .thenReturn(mMockMessage1);
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_WFC_MODE_TO_WIFI_ONLY)))
                .thenReturn(mMockMessage4);
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_ENABLED)))
                .thenReturn(mMockMessage2);
        when(mMockHandler.obtainMessage(
                        eq(QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_MODE_TO_WIFI_ONLY)))
                .thenReturn(mMockMessage5);
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_CARRIER_CONFIG_CHANGED)))
                .thenReturn(mMockMessage3);

        List<Integer> events = new ArrayList<Integer>();
        events.add(QnsEventDispatcher.QNS_EVENT_CROSS_SIM_CALLING_ENABLED);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_ENABLED);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_MODE_TO_WIFI_ONLY);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_ENABLED);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_MODE_TO_WIFI_ONLY);
        events.add(QnsEventDispatcher.QNS_EVENT_CARRIER_CONFIG_CHANGED);
        mQnsEventDispatcher.registerEvent(events, mMockHandler);

        // Send ACTION_CARRIER_CONFIG_CHANGED intent with valid Carrier id
        final Intent validCarrierIdIntent =
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        validCarrierIdIntent.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, DEFAULT_SLOT_INDEX);
        validCarrierIdIntent.putExtra(TelephonyManager.EXTRA_CARRIER_ID, DEFAULT_CARRIER_INDEX);
        mQnsEventDispatcher.mIntentReceiver.onReceive(mMockContext, validCarrierIdIntent);

        verify(mMockMessage, times(2)).sendToTarget();
        verify(mMockMessage1, times(2)).sendToTarget();
        verify(mMockMessage4, times(2)).sendToTarget();
        verify(mMockMessage2, times(2)).sendToTarget();
        verify(mMockMessage5, times(2)).sendToTarget();
        verify(mMockMessage3, times(1)).sendToTarget();

        mQnsEventDispatcher.mUserSettingObserver.onChange(true, CROSS_SIM_URI);
        mQnsEventDispatcher.mUserSettingObserver.onChange(true, WFC_ENABLED_URI);
        mQnsEventDispatcher.mUserSettingObserver.onChange(true, WFC_MODE_URI);
        mQnsEventDispatcher.mUserSettingObserver.onChange(true, WFC_ROAMING_ENABLED_URI);
        mQnsEventDispatcher.mUserSettingObserver.onChange(true, WFC_ROAMING_MODE_URI);

        verify(mMockMessage, times(2)).sendToTarget();
        verify(mMockMessage1, times(2)).sendToTarget();
        verify(mMockMessage4, times(2)).sendToTarget();
        verify(mMockMessage2, times(2)).sendToTarget();
        verify(mMockMessage5, times(2)).sendToTarget();
    }

    private final Object mLock = new Object();

    protected void waitForWfcModeChange(long timeout, QnsEventDispatcher dispatcher, int mode)
            throws InterruptedException {
        synchronized (mLock) {
            long now = System.currentTimeMillis();
            long deadline = now + timeout;
            while (dispatcher.mLastWfcMode != mode && now < deadline) {
                mLock.wait(timeout / 10);
                now = System.currentTimeMillis();
            }
        }
    }

    @Test
    public void testLoadWfcSettingsWhenCreate() throws InterruptedException {
        List<Integer> events = new ArrayList<>();
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_MODE_TO_WIFI_PREFERRED);
        when(mMockHandler.obtainMessage(
                        eq(QnsEventDispatcher.QNS_EVENT_WFC_MODE_TO_WIFI_PREFERRED)))
                .thenReturn(mMockMessage1);
        when(mMockQnsImsManager.getWfcMode(false)).thenReturn(QnsConstants.WIFI_PREF);
        mQnsEventDispatcher =
                new QnsEventDispatcher(
                        sMockContext, mMockQnsProvisioningListener, mMockQnsImsManager, 0);
        waitForWfcModeChange(500, mQnsEventDispatcher, QnsConstants.WIFI_PREF);
        assertEquals(QnsConstants.WIFI_PREF, mQnsEventDispatcher.mLastWfcMode);
        mQnsEventDispatcher.registerEvent(events, mMockHandler);
        mQnsEventDispatcher.mUserSettingObserver.onChange(true, WFC_MODE_URI);
        verify(mMockMessage1, times(1)).sendToTarget();
    }

    @Test
    public void testOnReceivedTryWfcConnectionIntent() {
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_TRY_WFC_ACTIVATION)))
                .thenReturn(mMockMessage);
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_CANCEL_TRY_WFC_ACTIVATION)))
                .thenReturn(mMockMessage2);

        List<Integer> events = new ArrayList<Integer>();
        events.add(QnsEventDispatcher.QNS_EVENT_TRY_WFC_ACTIVATION);
        events.add(QnsEventDispatcher.QNS_EVENT_CANCEL_TRY_WFC_ACTIVATION);
        mQnsEventDispatcher.registerEvent(events, mMockHandler);

        // Send WfcActivationHelper.ACTION_TRY_WFC_CONNECTION intent
        // with WfcActivationHelper.STATUS_START and valid subId
        final Intent validWfcActivationdStartIntent = new Intent(ACTION_TRY_WFC_CONNECTION);
        validWfcActivationdStartIntent.putExtra(EXTRA_SUB_ID, 0);
        validWfcActivationdStartIntent.putExtra(EXTRA_TRY_STATUS, STATUS_START);
        mQnsEventDispatcher.mWfcActivationIntentReceiver.onReceive(
                mMockContext, validWfcActivationdStartIntent);
        verify(mMockMessage, times(1)).sendToTarget();

        // Send WfcActivationHelper.ACTION_TRY_WFC_CONNECTION intent
        // with WfcActivationHelper.STATUS_END and valid subId
        final Intent validWfcActivationdEndIntent = new Intent(ACTION_TRY_WFC_CONNECTION);
        validWfcActivationdEndIntent.putExtra(EXTRA_SUB_ID, 0);
        validWfcActivationdEndIntent.putExtra(EXTRA_TRY_STATUS, STATUS_END);
        mQnsEventDispatcher.mWfcActivationIntentReceiver.onReceive(
                mMockContext, validWfcActivationdEndIntent);
        verify(mMockMessage2, times(1)).sendToTarget();
    }

    @Test
    public void testOnReceivedTryWfcConnectionIntentWithInvalidSubId() {
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_TRY_WFC_ACTIVATION)))
                .thenReturn(mMockMessage);
        when(mMockHandler.obtainMessage(eq(QnsEventDispatcher.QNS_EVENT_CANCEL_TRY_WFC_ACTIVATION)))
                .thenReturn(mMockMessage2);

        List<Integer> events = new ArrayList<Integer>();
        events.add(QnsEventDispatcher.QNS_EVENT_TRY_WFC_ACTIVATION);
        events.add(QnsEventDispatcher.QNS_EVENT_CANCEL_TRY_WFC_ACTIVATION);
        mQnsEventDispatcher.registerEvent(events, mMockHandler);

        // Send WfcActivationHelper.ACTION_TRY_WFC_CONNECTION intent
        // with WfcActivationHelper.STATUS_START and invalid subId
        final Intent validWfcActivationdStartIntent = new Intent(ACTION_TRY_WFC_CONNECTION);
        validWfcActivationdStartIntent.putExtra(EXTRA_SUB_ID, 1);
        validWfcActivationdStartIntent.putExtra(EXTRA_TRY_STATUS, STATUS_START);
        mQnsEventDispatcher.mWfcActivationIntentReceiver.onReceive(
                mMockContext, validWfcActivationdStartIntent);
        verify(mMockMessage, never()).sendToTarget();

        // Send WfcActivationHelper.ACTION_TRY_WFC_CONNECTION intent
        // with WfcActivationHelper.STATUS_END and invalid subId
        final Intent validWfcActivationdEndIntent = new Intent(ACTION_TRY_WFC_CONNECTION);
        validWfcActivationdEndIntent.putExtra(EXTRA_SUB_ID, 1);
        validWfcActivationdEndIntent.putExtra(EXTRA_TRY_STATUS, STATUS_END);
        mQnsEventDispatcher.mWfcActivationIntentReceiver.onReceive(
                mMockContext, validWfcActivationdEndIntent);
        verify(mMockMessage2, never()).sendToTarget();
    }
}
