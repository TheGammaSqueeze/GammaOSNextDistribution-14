/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.telephony.CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL;
import static android.telephony.TelephonyManager.DATA_ENABLED_REASON_CARRIER;
import static android.telephony.TelephonyManager.DATA_ENABLED_REASON_THERMAL;
import static android.telephony.TelephonyManager.DATA_ENABLED_REASON_USER;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;
import static com.android.server.wifi.WifiCarrierInfoManager.NOTIFICATION_USER_ALLOWED_CARRIER_INTENT_ACTION;
import static com.android.server.wifi.WifiCarrierInfoManager.NOTIFICATION_USER_CLICKED_INTENT_ACTION;
import static com.android.server.wifi.WifiCarrierInfoManager.NOTIFICATION_USER_DISALLOWED_CARRIER_INTENT_ACTION;
import static com.android.server.wifi.WifiCarrierInfoManager.NOTIFICATION_USER_DISMISSED_INTENT_ACTION;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiStringResourceWrapper;
import android.net.wifi.hotspot2.PasspointConfiguration;
import android.net.wifi.hotspot2.pps.Credential;
import android.os.Handler;
import android.os.ParcelUuid;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.os.test.TestLooper;
import android.telephony.CarrierConfigManager;
import android.telephony.ImsiEncryptionInfo;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Base64;
import android.util.Pair;

import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.WifiCarrierInfoManager.SimAuthRequestData;
import com.android.server.wifi.WifiCarrierInfoManager.SimAuthResponseData;
import com.android.server.wifi.entitlement.PseudonymInfo;
import com.android.wifi.resources.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;

/**
 * Unit tests for {@link WifiCarrierInfoManager}.
 */
@SmallTest
public class WifiCarrierInfoManagerTest extends WifiBaseTest {
    private WifiCarrierInfoManager mWifiCarrierInfoManager;

    private static final int DATA_SUBID = 1;
    private static final int NON_DATA_SUBID = 2;
    private static final int INVALID_SUBID = -1;
    private static final int DATA_CARRIER_ID = 10;
    private static final int PARENT_DATA_CARRIER_ID = 11;
    private static final int NON_DATA_CARRIER_ID = 20;
    private static final int PARENT_NON_DATA_CARRIER_ID = 21;
    private static final int DEACTIVE_CARRIER_ID = 30;
    private static final String MATCH_PREFIX_IMSI = "123456*";
    private static final String DATA_FULL_IMSI = "123456789123456";
    private static final String NON_DATA_FULL_IMSI = "123456987654321";
    private static final String NO_MATCH_FULL_IMSI = "654321123456789";
    private static final String NO_MATCH_PREFIX_IMSI = "654321*";
    private static final String DATA_OPERATOR_NUMERIC = "123456";
    private static final String NON_DATA_OPERATOR_NUMERIC = "123456";
    private static final String NO_MATCH_OPERATOR_NUMERIC = "654321";
    private static final String TEST_PACKAGE = "com.test12345";
    private static final String ANONYMOUS_IDENTITY = "anonymous@wlan.mnc456.mcc123.3gppnetwork.org";
    private static final String CARRIER_NAME = "Google";
    private static final ParcelUuid GROUP_UUID = ParcelUuid
            .fromString("0000110B-0000-1000-8000-00805F9B34FB");

    @Mock CarrierConfigManager mCarrierConfigManager;
    @Mock WifiContext mContext;
    @Mock Resources mResources;
    @Mock FrameworkFacade mFrameworkFacade;
    @Mock TelephonyManager mTelephonyManager;
    @Mock TelephonyManager mDataTelephonyManager;
    @Mock TelephonyManager mNonDataTelephonyManager;
    @Mock SubscriptionManager mSubscriptionManager;
    @Mock SubscriptionInfo mDataSubscriptionInfo;
    @Mock SubscriptionInfo mNonDataSubscriptionInfo;
    @Mock WifiConfigStore mWifiConfigStore;
    @Mock WifiInjector mWifiInjector;
    @Mock WifiNetworkFactory mWifiNetworkFactory;
    @Mock UntrustedWifiNetworkFactory mUntrustedWifiNetworkFactory;
    @Mock RestrictedWifiNetworkFactory mRestrictedWifiNetworkFactory;
    @Mock WifiConfigManager mWifiConfigManager;
    @Mock
    WifiCarrierInfoStoreManagerData mWifiCarrierInfoStoreManagerData;
    @Mock WifiNotificationManager mWifiNotificationManager;
    @Mock Notification.Builder mNotificationBuilder;
    @Mock Notification mNotification;
    @Mock WifiDialogManager mWifiDialogManager;
    @Mock WifiDialogManager.DialogHandle mDialogHandle;
    @Mock
    WifiCarrierInfoManager.OnImsiProtectedOrUserApprovedListener mListener;
    @Mock WifiMetrics mWifiMetrics;
    @Mock WifiCarrierInfoManager.OnCarrierOffloadDisabledListener mOnCarrierOffloadDisabledListener;
    @Mock Clock mClock;
    @Mock WifiNetworkSuggestionsManager mWifiNetworkSuggestionsManager;
    @Mock DeviceConfigFacade mDeviceConfigFacade;
    @Mock WifiStringResourceWrapper mWifiStringResourceWrapper;
    @Mock WifiPseudonymManager mWifiPseudonymManager;

    private List<SubscriptionInfo> mSubInfoList;
    private long mCurrentTimeMills = 1000L;

    MockitoSession mMockingSession = null;
    TestLooper mLooper;
    private WifiCarrierInfoStoreManagerData.DataSource mCarrierInfoDataSource;
    private ImsiPrivacyProtectionExemptionStoreData.DataSource mImsiDataSource;
    private ArgumentCaptor<BroadcastReceiver> mBroadcastReceiverCaptor =
            ArgumentCaptor.forClass(BroadcastReceiver.class);
    private ArgumentCaptor<SubscriptionManager.OnSubscriptionsChangedListener>
            mListenerArgumentCaptor = ArgumentCaptor.forClass(
                    SubscriptionManager.OnSubscriptionsChangedListener.class);

    private Consumer<Boolean>
            mOobPseudonymFeatureFlagChangedListener;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mLooper = new TestLooper();
        when(mContext.getSystemService(CarrierConfigManager.class))
                .thenReturn(mCarrierConfigManager);
        when(mContext.getResources()).thenReturn(mResources);
        when(mContext.getWifiOverlayApkPkgName()).thenReturn("test.com.android.wifi.resources");
        when(mFrameworkFacade.makeNotificationBuilder(any(), anyString()))
                .thenReturn(mNotificationBuilder);
        when(mFrameworkFacade.getBroadcast(any(), anyInt(), any(), anyInt()))
                .thenReturn(mock(PendingIntent.class));
        when(mNotificationBuilder.setSmallIcon(any())).thenReturn(mNotificationBuilder);
        when(mNotificationBuilder.setTicker(any())).thenReturn(mNotificationBuilder);
        when(mNotificationBuilder.setContentTitle(any())).thenReturn(mNotificationBuilder);
        when(mNotificationBuilder.setStyle(any())).thenReturn(mNotificationBuilder);
        when(mNotificationBuilder.setContentIntent(any())).thenReturn(mNotificationBuilder);
        when(mNotificationBuilder.setDeleteIntent(any())).thenReturn(mNotificationBuilder);
        when(mNotificationBuilder.setShowWhen(anyBoolean())).thenReturn(mNotificationBuilder);
        when(mNotificationBuilder.setLocalOnly(anyBoolean())).thenReturn(mNotificationBuilder);
        when(mNotificationBuilder.setColor(anyInt())).thenReturn(mNotificationBuilder);
        when(mNotificationBuilder.addAction(any())).thenReturn(mNotificationBuilder);
        when(mNotificationBuilder.setTimeoutAfter(anyLong())).thenReturn(mNotificationBuilder);
        when(mNotificationBuilder.build()).thenReturn(mNotification);
        when(mWifiDialogManager.createSimpleDialog(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mDialogHandle);
        when(mWifiInjector.makeWifiCarrierInfoStoreManagerData(any()))
                .thenReturn(mWifiCarrierInfoStoreManagerData);
        when(mWifiInjector.getWifiConfigManager()).thenReturn(mWifiConfigManager);
        when(mWifiInjector.getWifiNotificationManager()).thenReturn(mWifiNotificationManager);
        when(mWifiInjector.getWifiDialogManager()).thenReturn(mWifiDialogManager);
        when(mWifiInjector.getWifiNetworkSuggestionsManager())
                .thenReturn(mWifiNetworkSuggestionsManager);
        when(mWifiInjector.getDeviceConfigFacade()).thenReturn(mDeviceConfigFacade);
        when(mWifiInjector.getWifiNetworkFactory()).thenReturn(mWifiNetworkFactory);
        when(mWifiInjector.getUntrustedWifiNetworkFactory())
                .thenReturn(mUntrustedWifiNetworkFactory);
        when(mWifiInjector.getRestrictedWifiNetworkFactory())
                 .thenReturn(mRestrictedWifiNetworkFactory);
        when(mContext.getStringResourceWrapper(anyInt(), anyInt()))
                .thenReturn(mWifiStringResourceWrapper);
        mWifiCarrierInfoManager = new WifiCarrierInfoManager(mTelephonyManager,
                mSubscriptionManager, mWifiInjector, mFrameworkFacade, mContext, mWifiConfigStore,
                new Handler(mLooper.getLooper()), mWifiMetrics, mClock, mWifiPseudonymManager);
        mWifiCarrierInfoManager.enableVerboseLogging(true);
        ArgumentCaptor<WifiCarrierInfoStoreManagerData.DataSource>
                carrierInfoSourceArgumentCaptor =
                ArgumentCaptor.forClass(WifiCarrierInfoStoreManagerData.DataSource.class);
        ArgumentCaptor<ImsiPrivacyProtectionExemptionStoreData.DataSource>
                imsiDataSourceArgumentCaptor =
                ArgumentCaptor.forClass(ImsiPrivacyProtectionExemptionStoreData.DataSource.class);
        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(), any(), any(), any());
        verify(mWifiInjector).makeWifiCarrierInfoStoreManagerData(carrierInfoSourceArgumentCaptor
                .capture());
        verify(mWifiInjector).makeImsiPrivacyProtectionExemptionStoreData(
                imsiDataSourceArgumentCaptor.capture());
        ArgumentCaptor<Consumer> oobCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(mDeviceConfigFacade).setOobPseudonymFeatureFlagChangedListener(oobCaptor.capture());
        mOobPseudonymFeatureFlagChangedListener = oobCaptor.getValue();
        mCarrierInfoDataSource = carrierInfoSourceArgumentCaptor.getValue();
        mImsiDataSource = imsiDataSourceArgumentCaptor.getValue();
        mImsiDataSource.fromDeserialized(new HashMap<>());
        assertNotNull(mCarrierInfoDataSource);
        mSubInfoList = new ArrayList<>();
        mSubInfoList.add(mDataSubscriptionInfo);
        mSubInfoList.add(mNonDataSubscriptionInfo);
        when(mTelephonyManager.createForSubscriptionId(eq(DATA_SUBID)))
                .thenReturn(mDataTelephonyManager);
        when(mTelephonyManager.createForSubscriptionId(eq(NON_DATA_SUBID)))
                .thenReturn(mNonDataTelephonyManager);
        when(mTelephonyManager.getSimApplicationState(anyInt()))
                .thenReturn(TelephonyManager.SIM_STATE_LOADED);
        when(mTelephonyManager.getActiveModemCount()).thenReturn(2);
        when(mCarrierConfigManager.getConfigForSubId(anyInt()))
                .thenReturn(generateTestCarrierConfig(false));
        when(mSubscriptionManager.getCompleteActiveSubscriptionInfoList()).thenReturn(mSubInfoList);
        mMockingSession = ExtendedMockito.mockitoSession().strictness(Strictness.LENIENT)
                .mockStatic(SubscriptionManager.class).startMocking();

        doReturn(DATA_SUBID).when(
                () -> SubscriptionManager.getDefaultDataSubscriptionId());
        doReturn(true).when(
                () -> SubscriptionManager.isValidSubscriptionId(DATA_SUBID));
        doReturn(true).when(
                () -> SubscriptionManager.isValidSubscriptionId(NON_DATA_SUBID));

        when(mDataSubscriptionInfo.getCarrierId()).thenReturn(DATA_CARRIER_ID);
        when(mDataSubscriptionInfo.getSubscriptionId()).thenReturn(DATA_SUBID);
        when(mNonDataSubscriptionInfo.getCarrierId()).thenReturn(NON_DATA_CARRIER_ID);
        when(mNonDataSubscriptionInfo.getSubscriptionId()).thenReturn(NON_DATA_SUBID);
        when(mDataTelephonyManager.getSubscriberId()).thenReturn(DATA_FULL_IMSI);
        when(mNonDataTelephonyManager.getSubscriberId()).thenReturn(NON_DATA_FULL_IMSI);
        when(mDataTelephonyManager.getSimOperator()).thenReturn(DATA_OPERATOR_NUMERIC);
        when(mDataTelephonyManager.getSimCarrierIdName()).thenReturn(CARRIER_NAME);
        when(mNonDataTelephonyManager.getSimCarrierIdName()).thenReturn(null);
        when(mNonDataTelephonyManager.getSimOperator())
                .thenReturn(NON_DATA_OPERATOR_NUMERIC);
        when(mDataTelephonyManager.getSimApplicationState())
                .thenReturn(TelephonyManager.SIM_STATE_LOADED);
        when(mNonDataTelephonyManager.getSimApplicationState())
                .thenReturn(TelephonyManager.SIM_STATE_LOADED);
        when(mSubscriptionManager.getActiveSubscriptionIdList())
                .thenReturn(new int[]{DATA_SUBID, NON_DATA_SUBID});
        when(mSubscriptionManager.getSubscriptionsInGroup(GROUP_UUID)).thenReturn(mSubInfoList);

        // setup resource strings for IMSI protection notification.
        when(mResources.getString(eq(R.string.wifi_suggestion_imsi_privacy_title), anyString()))
                .thenAnswer(s -> "blah" + s.getArguments()[1]);
        when(mResources.getString(eq(R.string.wifi_suggestion_imsi_privacy_content)))
                .thenReturn("blah");
        when(mResources.getText(
                eq(R.string.wifi_suggestion_action_allow_imsi_privacy_exemption_carrier)))
                .thenReturn("blah");
        when(mResources.getText(
                eq(R.string.wifi_suggestion_action_disallow_imsi_privacy_exemption_carrier)))
                .thenReturn("blah");
        when(mResources.getString(
                eq(R.string.wifi_suggestion_imsi_privacy_exemption_confirmation_title)))
                .thenReturn("blah");
        when(mResources.getString(
                eq(R.string.wifi_suggestion_imsi_privacy_exemption_confirmation_content),
                anyString())).thenAnswer(s -> "blah" + s.getArguments()[1]);
        when(mResources.getText(
                eq(R.string.wifi_suggestion_action_allow_imsi_privacy_exemption_confirmation)))
                .thenReturn("blah");
        when(mResources.getText(
                eq(R.string.wifi_suggestion_action_disallow_imsi_privacy_exemption_confirmation)))
                .thenReturn("blah");
        when(mResources.getInteger(eq(R.integer.config_wifiImsiProtectionNotificationDelaySeconds)))
                .thenReturn(300);
        mWifiCarrierInfoManager.addImsiProtectedOrUserApprovedListener(mListener);
        verify(mSubscriptionManager).addOnSubscriptionsChangedListener(any(),
                mListenerArgumentCaptor.capture());
        mListenerArgumentCaptor.getValue().onSubscriptionsChanged();
        mLooper.dispatchAll();
        when(mClock.getElapsedSinceBootMillis()).thenReturn(mCurrentTimeMills);
    }

    @After
    public void cleanUp() throws Exception {
        if (mMockingSession != null) {
            mMockingSession.finishMocking();
        }
    }

    /**
     * Verify that the IMSI encryption info is not updated  when non
     * {@link CarrierConfigManager#ACTION_CARRIER_CONFIG_CHANGED} intent is received.
     *
     * @throws Exception
     */
    @Test
    public void receivedNonCarrierConfigChangedIntent() throws Exception {
        ArgumentCaptor<BroadcastReceiver> receiver =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(receiver.capture(), any(IntentFilter.class));
        receiver.getValue().onReceive(mContext, new Intent("placeholderIntent"));
        verify(mCarrierConfigManager, never()).getConfig();
    }

    private PersistableBundle generateTestCarrierConfig(boolean requiresImsiEncryption) {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(KEY_CARRIER_CONFIG_APPLIED_BOOL, true);
        if (requiresImsiEncryption) {
            bundle.putInt(CarrierConfigManager.IMSI_KEY_AVAILABILITY_INT,
                    TelephonyManager.KEY_TYPE_WLAN);
        }
        return bundle;
    }

    private PersistableBundle generateTestCarrierConfig(boolean requiresImsiEncryption,
            boolean requiresEapMethodPrefix) {
        PersistableBundle bundle = generateTestCarrierConfig(requiresImsiEncryption);
        if (requiresEapMethodPrefix) {
            bundle.putBoolean(CarrierConfigManager.ENABLE_EAP_METHOD_PREFIX_BOOL, true);
        }
        return bundle;
    }

    /**
     * Verify getting value about that if the IMSI encryption is required or not when
     * {@link CarrierConfigManager#ACTION_CARRIER_CONFIG_CHANGED} intent is received.
     */
    @Test
    public void receivedCarrierConfigChangedIntent() throws Exception {
        when(mCarrierConfigManager.getConfigForSubId(DATA_SUBID))
                .thenReturn(generateTestCarrierConfig(true));
        when(mCarrierConfigManager.getConfigForSubId(NON_DATA_SUBID))
                .thenReturn(generateTestCarrierConfig(false));
        ArgumentCaptor<BroadcastReceiver> receiver =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(receiver.capture(), any(IntentFilter.class));

        receiver.getValue().onReceive(mContext,
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));

        assertTrue(mWifiCarrierInfoManager.requiresImsiEncryption(DATA_SUBID));
        assertFalse(mWifiCarrierInfoManager.requiresImsiEncryption(NON_DATA_SUBID));
    }

    @Test
    public void receivedDefaultDataSubChangedIntent() throws Exception {
        when(mCarrierConfigManager.getConfigForSubId(DATA_SUBID))
                .thenReturn(generateTestCarrierConfig(true));
        when(mCarrierConfigManager.getConfigForSubId(NON_DATA_SUBID))
                .thenReturn(generateTestCarrierConfig(false));
        ArgumentCaptor<BroadcastReceiver> receiver =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        when(mDeviceConfigFacade.isOobPseudonymEnabled()).thenReturn(true);
        WifiStringResourceWrapper nonDataResourceWrapper = mock(WifiStringResourceWrapper.class);
        when(mContext.getStringResourceWrapper(eq(NON_DATA_SUBID), eq(NON_DATA_CARRIER_ID)))
                .thenReturn(nonDataResourceWrapper);
        when(nonDataResourceWrapper.getBoolean(
                eq(WifiCarrierInfoManager.CONFIG_WIFI_OOB_PSEUDONYM_ENABLED), anyBoolean()))
                .thenReturn(true);

        verify(mContext).registerReceiver(receiver.capture(), any(IntentFilter.class));
        Intent intent = new Intent(TelephonyManager.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
        intent.putExtra("subscription", NON_DATA_SUBID);
        receiver.getValue().onReceive(mContext, intent);

        mLooper.dispatchAll();

        verify(mWifiPseudonymManager).retrieveOobPseudonymIfNeeded(NON_DATA_CARRIER_ID);
        verify(mWifiPseudonymManager, never()).retrieveOobPseudonymIfNeeded(DATA_CARRIER_ID);
    }

    /**
     * Verify the auto-join may be restored if OOB pseudonym is enabled.
     */
    @Test
    public void restoreAutoJoinForOobPseudonymEnabled() throws Exception {
        when(mCarrierConfigManager.getConfigForSubId(DATA_SUBID))
                .thenReturn(generateTestCarrierConfig(true));
        when(mCarrierConfigManager.getConfigForSubId(NON_DATA_SUBID))
                .thenReturn(generateTestCarrierConfig(false));
        when(mDeviceConfigFacade.isOobPseudonymEnabled()).thenReturn(true);
        // enable OOB pseudonym for NON_DATA_SUBID
        WifiStringResourceWrapper nonDataResourceWrapper = mock(WifiStringResourceWrapper.class);
        when(mContext.getStringResourceWrapper(eq(NON_DATA_SUBID), eq(NON_DATA_CARRIER_ID)))
                .thenReturn(nonDataResourceWrapper);
        when(nonDataResourceWrapper.getBoolean(
                eq(WifiCarrierInfoManager.CONFIG_WIFI_OOB_PSEUDONYM_ENABLED), anyBoolean()))
                .thenReturn(true);
        mOobPseudonymFeatureFlagChangedListener.accept(/*isFeatureEnabled=*/ true);
        mLooper.dispatchAll();

        verify(mListener).onImsiProtectedOrUserApprovalChanged(NON_DATA_CARRIER_ID, true);
        verify(mListener, never())
                .onImsiProtectedOrUserApprovalChanged(eq(DATA_CARRIER_ID), anyBoolean());
        verify(mWifiConfigManager).saveToStore(true);
        assertFalse(mWifiCarrierInfoManager.shouldFlipOnAutoJoinForOobPseudonym());

        ArgumentCaptor<BroadcastReceiver> receiver =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(receiver.capture(), any(IntentFilter.class));

        receiver.getValue().onReceive(mContext,
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));
        mLooper.dispatchAll();

        assertFalse(mWifiCarrierInfoManager.shouldFlipOnAutoJoinForOobPseudonym());
        // only called on Pseudonym feature enabled
        verify(mListener, atMost(1))
                .onImsiProtectedOrUserApprovalChanged(NON_DATA_CARRIER_ID, true);
        verify(mWifiPseudonymManager, times(2)).retrieveOobPseudonymIfNeeded(NON_DATA_CARRIER_ID);
    }

    @Test
    public void restoreAutoJoinForOobPseudonymDisabled() throws Exception {
        when(mCarrierConfigManager.getConfigForSubId(DATA_SUBID))
                .thenReturn(generateTestCarrierConfig(true));
        when(mCarrierConfigManager.getConfigForSubId(NON_DATA_SUBID))
                .thenReturn(generateTestCarrierConfig(false));
        when(mDeviceConfigFacade.isOobPseudonymEnabled()).thenReturn(false);
        WifiStringResourceWrapper nonDataResourceWrapper = mock(WifiStringResourceWrapper.class);
        when(mContext.getStringResourceWrapper(eq(NON_DATA_SUBID), eq(NON_DATA_CARRIER_ID)))
                .thenReturn(nonDataResourceWrapper);
        when(nonDataResourceWrapper.getBoolean(
                eq(WifiCarrierInfoManager.CONFIG_WIFI_OOB_PSEUDONYM_ENABLED), anyBoolean()))
                .thenReturn(true);

        mOobPseudonymFeatureFlagChangedListener.accept(/*isFeatureEnabled=*/ false);
        mLooper.dispatchAll();

        verify(mListener).onImsiProtectedOrUserApprovalChanged(NON_DATA_CARRIER_ID, false);
        verify(mListener, never())
                .onImsiProtectedOrUserApprovalChanged(eq(DATA_CARRIER_ID), anyBoolean());
        verify(mWifiConfigManager).saveToStore(true);

        // do nothing for ACTION_CARRIER_CONFIG_CHANGED
        ArgumentCaptor<BroadcastReceiver> receiver =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(receiver.capture(), any(IntentFilter.class));

        receiver.getValue().onReceive(mContext,
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));
        mLooper.dispatchAll();
        // only called on the previous event - feature disabled.
        verify(mListener, atMost(1))
                .onImsiProtectedOrUserApprovalChanged(eq(NON_DATA_CARRIER_ID), anyBoolean());
    }

    /**
     * Validate when KEY_CARRIER_PROVISIONS_WIFI_MERGED_NETWORKS_BOOL is change from true to false,
     * carrier offload will disable for merged network.
     */
    @Test
    public void receivedCarrierConfigChangedAllowMergedNetworkToFalse() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        mWifiCarrierInfoManager.addOnCarrierOffloadDisabledListener(
                mOnCarrierOffloadDisabledListener);
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(KEY_CARRIER_CONFIG_APPLIED_BOOL, true);
        String key = CarrierConfigManager.KEY_CARRIER_PROVISIONS_WIFI_MERGED_NETWORKS_BOOL;
        bundle.putBoolean(key, true);
        when(mCarrierConfigManager.getConfigForSubId(DATA_SUBID)).thenReturn(bundle);
        ArgumentCaptor<BroadcastReceiver> receiver =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(receiver.capture(), any(IntentFilter.class));

        receiver.getValue().onReceive(mContext,
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));
        mLooper.dispatchAll();
        assertTrue(mWifiCarrierInfoManager.areMergedCarrierWifiNetworksAllowed(DATA_SUBID));
        verify(mOnCarrierOffloadDisabledListener, never()).onCarrierOffloadDisabled(anyInt(),
                anyBoolean());

        // When KEY_CARRIER_PROVISIONS_WIFI_MERGED_NETWORKS_BOOL change to false should send merged
        // carrier offload disable callback.
        PersistableBundle disallowedBundle = new PersistableBundle();
        disallowedBundle.putBoolean(KEY_CARRIER_CONFIG_APPLIED_BOOL, true);
        disallowedBundle.putBoolean(key, false);
        when(mCarrierConfigManager.getConfigForSubId(DATA_SUBID)).thenReturn(disallowedBundle);
        receiver.getValue().onReceive(mContext,
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));
        mLooper.dispatchAll();
        assertFalse(mWifiCarrierInfoManager.areMergedCarrierWifiNetworksAllowed(DATA_SUBID));
        verify(mOnCarrierOffloadDisabledListener).onCarrierOffloadDisabled(eq(DATA_SUBID),
                eq(true));
    }

    /**
     * Verify the IMSI encryption is cleared when the configuration in CarrierConfig is removed.
     */
    @Test
    public void imsiEncryptionRequiredInfoIsCleared() {
        when(mCarrierConfigManager.getConfigForSubId(DATA_SUBID))
                .thenReturn(generateTestCarrierConfig(true));
        when(mCarrierConfigManager.getConfigForSubId(NON_DATA_SUBID))
                .thenReturn(generateTestCarrierConfig(true));
        ArgumentCaptor<BroadcastReceiver> receiver =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(receiver.capture(), any(IntentFilter.class));

        receiver.getValue().onReceive(mContext,
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));
        mLooper.dispatchAll();

        assertTrue(mWifiCarrierInfoManager.requiresImsiEncryption(DATA_SUBID));
        assertTrue(mWifiCarrierInfoManager.requiresImsiEncryption(NON_DATA_SUBID));

        when(mCarrierConfigManager.getConfigForSubId(DATA_SUBID))
                .thenReturn(generateTestCarrierConfig(false));
        when(mCarrierConfigManager.getConfigForSubId(NON_DATA_SUBID))
                .thenReturn(generateTestCarrierConfig(false));
        receiver.getValue().onReceive(mContext,
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));
        mLooper.dispatchAll();

        assertFalse(mWifiCarrierInfoManager.requiresImsiEncryption(DATA_SUBID));
        assertFalse(mWifiCarrierInfoManager.requiresImsiEncryption(NON_DATA_SUBID));
    }

    /**
     * Verify that if the IMSI encryption is downloaded and the OOB pseudonym
     * retrieval is triggered.
     */
    @Test
    public void availableOfImsiEncryptionInfoIsUpdatedAndOobPseudonymIsUpdated() {
        when(mCarrierConfigManager.getConfigForSubId(DATA_SUBID))
                .thenReturn(generateTestCarrierConfig(true));
        when(mCarrierConfigManager.getConfigForSubId(NON_DATA_SUBID))
                .thenReturn(generateTestCarrierConfig(false));
        when(mDataTelephonyManager.getCarrierInfoForImsiEncryption(TelephonyManager.KEY_TYPE_WLAN))
                .thenReturn(null);
        when(mNonDataTelephonyManager
                .getCarrierInfoForImsiEncryption(TelephonyManager.KEY_TYPE_WLAN)).thenReturn(null);

        ArgumentCaptor<ContentObserver> observerCaptor =
                ArgumentCaptor.forClass(ContentObserver.class);
        verify(mFrameworkFacade).registerContentObserver(eq(mContext), any(Uri.class), eq(false),
                observerCaptor.capture());
        ContentObserver observer = observerCaptor.getValue();

        observer.onChange(false);
        mLooper.dispatchAll();

        assertTrue(mWifiCarrierInfoManager.requiresImsiEncryption(DATA_SUBID));
        assertFalse(mWifiCarrierInfoManager.isImsiEncryptionInfoAvailable(DATA_SUBID));

        when(mDataTelephonyManager.getCarrierInfoForImsiEncryption(TelephonyManager.KEY_TYPE_WLAN))
                .thenReturn(mock(ImsiEncryptionInfo.class));
        when(mDeviceConfigFacade.isOobPseudonymEnabled()).thenReturn(true);
        WifiStringResourceWrapper nonDataResourceWrapper = mock(WifiStringResourceWrapper.class);
        when(mContext.getStringResourceWrapper(eq(NON_DATA_SUBID), eq(NON_DATA_CARRIER_ID)))
                .thenReturn(nonDataResourceWrapper);
        when(nonDataResourceWrapper.getBoolean(
                eq(WifiCarrierInfoManager.CONFIG_WIFI_OOB_PSEUDONYM_ENABLED), anyBoolean()))
                .thenReturn(true);

        observer.onChange(false);
        mLooper.dispatchAll();

        assertTrue(mWifiCarrierInfoManager.requiresImsiEncryption(DATA_SUBID));
        assertTrue(mWifiCarrierInfoManager.isImsiEncryptionInfoAvailable(DATA_SUBID));
        verify(mWifiPseudonymManager).retrieveOobPseudonymIfNeeded(NON_DATA_CARRIER_ID);
        verify(mWifiPseudonymManager, never()).retrieveOobPseudonymIfNeeded(DATA_CARRIER_ID);
    }

    /**
     * Verify that if the IMSI encryption information is cleared
     */
    @Test
    public void availableOfImsiEncryptionInfoIsCleared() {
        when(mCarrierConfigManager.getConfigForSubId(DATA_SUBID))
                .thenReturn(generateTestCarrierConfig(true));
        when(mCarrierConfigManager.getConfigForSubId(NON_DATA_SUBID))
                .thenReturn(generateTestCarrierConfig(true));
        when(mDataTelephonyManager.getCarrierInfoForImsiEncryption(TelephonyManager.KEY_TYPE_WLAN))
                .thenReturn(mock(ImsiEncryptionInfo.class));
        when(mNonDataTelephonyManager
                .getCarrierInfoForImsiEncryption(TelephonyManager.KEY_TYPE_WLAN))
                        .thenReturn(mock(ImsiEncryptionInfo.class));

        ArgumentCaptor<ContentObserver> observerCaptor =
                ArgumentCaptor.forClass(ContentObserver.class);
        verify(mFrameworkFacade).registerContentObserver(eq(mContext), any(Uri.class), eq(false),
                observerCaptor.capture());
        ContentObserver observer = observerCaptor.getValue();

        observer.onChange(false);
        mLooper.dispatchAll();

        assertTrue(mWifiCarrierInfoManager.isImsiEncryptionInfoAvailable(DATA_SUBID));
        assertTrue(mWifiCarrierInfoManager.isImsiEncryptionInfoAvailable(NON_DATA_SUBID));

        when(mDataTelephonyManager.getCarrierInfoForImsiEncryption(TelephonyManager.KEY_TYPE_WLAN))
                .thenReturn(null);
        when(mNonDataTelephonyManager
                .getCarrierInfoForImsiEncryption(TelephonyManager.KEY_TYPE_WLAN)).thenReturn(null);

        observer.onChange(false);
        mLooper.dispatchAll();

        assertFalse(mWifiCarrierInfoManager.isImsiEncryptionInfoAvailable(DATA_SUBID));
        assertFalse(mWifiCarrierInfoManager.isImsiEncryptionInfoAvailable(NON_DATA_SUBID));
    }

    @Test
    public void getSimIdentityEapSim() {
        final Pair<String, String> expectedIdentity = Pair.create(
                "13214561234567890@wlan.mnc456.mcc321.3gppnetwork.org", "");

        when(mDataTelephonyManager.getSubscriberId()).thenReturn("3214561234567890");
        when(mDataTelephonyManager.getSimOperator()).thenReturn("321456");
        when(mDataTelephonyManager.getCarrierInfoForImsiEncryption(anyInt())).thenReturn(null);
        WifiConfiguration simConfig =
                WifiConfigurationTestUtil.createEapNetwork(WifiEnterpriseConfig.Eap.SIM,
                        WifiEnterpriseConfig.Phase2.NONE);
        simConfig.carrierId = DATA_CARRIER_ID;

        assertEquals(expectedIdentity, mWifiCarrierInfoManager.getSimIdentity(simConfig));

        WifiConfiguration peapSimConfig =
                WifiConfigurationTestUtil.createEapNetwork(WifiEnterpriseConfig.Eap.PEAP,
                        WifiEnterpriseConfig.Phase2.SIM);
        peapSimConfig.carrierId = DATA_CARRIER_ID;

        assertEquals(expectedIdentity, mWifiCarrierInfoManager.getSimIdentity(peapSimConfig));
        verify(mDataTelephonyManager, never()).getCarrierInfoForImsiEncryption(anyInt());
    }

    @Test
    public void getSimIdentityEapAka() {
        final Pair<String, String> expectedIdentity = Pair.create(
                "03214561234567890@wlan.mnc456.mcc321.3gppnetwork.org", "");
        when(mDataTelephonyManager.getSubscriberId()).thenReturn("3214561234567890");

        when(mDataTelephonyManager.getSimOperator()).thenReturn("321456");
        when(mDataTelephonyManager.getCarrierInfoForImsiEncryption(anyInt())).thenReturn(null);
        WifiConfiguration akaConfig =
                WifiConfigurationTestUtil.createEapNetwork(WifiEnterpriseConfig.Eap.AKA,
                        WifiEnterpriseConfig.Phase2.NONE);
        akaConfig.carrierId = DATA_CARRIER_ID;

        assertEquals(expectedIdentity, mWifiCarrierInfoManager.getSimIdentity(akaConfig));

        WifiConfiguration peapAkaConfig =
                WifiConfigurationTestUtil.createEapNetwork(WifiEnterpriseConfig.Eap.PEAP,
                        WifiEnterpriseConfig.Phase2.AKA);
        peapAkaConfig.carrierId = DATA_CARRIER_ID;

        assertEquals(expectedIdentity, mWifiCarrierInfoManager.getSimIdentity(peapAkaConfig));
        verify(mDataTelephonyManager, never()).getCarrierInfoForImsiEncryption(anyInt());
    }

    @Test
    public void getSimIdentityEapAkaPrime() {
        final Pair<String, String> expectedIdentity = Pair.create(
                "63214561234567890@wlan.mnc456.mcc321.3gppnetwork.org", "");

        when(mDataTelephonyManager.getSubscriberId()).thenReturn("3214561234567890");
        when(mDataTelephonyManager.getSimOperator()).thenReturn("321456");
        when(mDataTelephonyManager.getCarrierInfoForImsiEncryption(anyInt())).thenReturn(null);
        WifiConfiguration akaPConfig =
                WifiConfigurationTestUtil.createEapNetwork(WifiEnterpriseConfig.Eap.AKA_PRIME,
                        WifiEnterpriseConfig.Phase2.NONE);
        akaPConfig.carrierId = DATA_CARRIER_ID;

        assertEquals(expectedIdentity, mWifiCarrierInfoManager.getSimIdentity(akaPConfig));

        WifiConfiguration peapAkaPConfig =
                WifiConfigurationTestUtil.createEapNetwork(WifiEnterpriseConfig.Eap.PEAP,
                        WifiEnterpriseConfig.Phase2.AKA_PRIME);
        peapAkaPConfig.carrierId = DATA_CARRIER_ID;

        assertEquals(expectedIdentity, mWifiCarrierInfoManager.getSimIdentity(peapAkaPConfig));
        verify(mDataTelephonyManager, never()).getCarrierInfoForImsiEncryption(anyInt());
    }

    /**
     * Verify that an expected identity is returned when using the encrypted identity
     * encoded by RFC4648.
     */
    @Test
    public void getEncryptedIdentity_WithRfc4648() throws Exception {
        Cipher cipher = mock(Cipher.class);
        PublicKey key = null;
        String imsi = "3214561234567890";
        String permanentIdentity = "03214561234567890@wlan.mnc456.mcc321.3gppnetwork.org";
        String encryptedImsi = Base64.encodeToString(permanentIdentity.getBytes(), 0,
                permanentIdentity.getBytes().length, Base64.NO_WRAP);
        String encryptedIdentity = "\0" + encryptedImsi;
        final Pair<String, String> expectedIdentity = Pair.create(permanentIdentity,
                encryptedIdentity);
        WifiCarrierInfoManager spyTu = spy(mWifiCarrierInfoManager);
        doReturn(true).when(spyTu).requiresImsiEncryption(DATA_SUBID);

        // static mocking
        MockitoSession session = ExtendedMockito.mockitoSession().mockStatic(
                Cipher.class).startMocking();
        try {
            lenient().when(Cipher.getInstance(anyString())).thenReturn(cipher);
            when(cipher.doFinal(any(byte[].class))).thenReturn(permanentIdentity.getBytes());
            when(mDataTelephonyManager.getSubscriberId()).thenReturn(imsi);
            when(mDataTelephonyManager.getSimOperator()).thenReturn("321456");
            ImsiEncryptionInfo info = mock(ImsiEncryptionInfo.class);
            when(info.getPublicKey()).thenReturn(key);
            when(info.getKeyIdentifier()).thenReturn(null);
            when(mDataTelephonyManager.getCarrierInfoForImsiEncryption(
                    eq(TelephonyManager.KEY_TYPE_WLAN)))
                    .thenReturn(info);
            WifiConfiguration config =
                    WifiConfigurationTestUtil.createEapNetwork(WifiEnterpriseConfig.Eap.AKA,
                            WifiEnterpriseConfig.Phase2.NONE);
            config.carrierId = DATA_CARRIER_ID;

            assertEquals(expectedIdentity, spyTu.getSimIdentity(config));
        } finally {
            session.finishMocking();
        }
    }

    /**
     * Verify that an expected identity is returned when using the OOB Pseudonym.
     */
    @Test
    public void getEncryptedIdentityWithOobPseudonymEnabled() throws Exception {
        String expectedPseudonym = "abc123=1";
        final Pair<String, String> expectedIdentity = Pair.create(expectedPseudonym,
                "");
        WifiCarrierInfoManager spyTu = spy(mWifiCarrierInfoManager);
        doReturn(true).when(spyTu).isOobPseudonymFeatureEnabled(DATA_CARRIER_ID);

        WifiConfiguration config =
                WifiConfigurationTestUtil.createEapNetwork(WifiEnterpriseConfig.Eap.AKA,
                        WifiEnterpriseConfig.Phase2.NONE);
        config.carrierId = DATA_CARRIER_ID;
        PseudonymInfo pseudonymInfo = mock(PseudonymInfo.class);
        when(pseudonymInfo.getPseudonym()).thenReturn(expectedPseudonym);
        when(mWifiPseudonymManager.getValidPseudonymInfo(DATA_CARRIER_ID))
                .thenReturn(Optional.of(pseudonymInfo));

        assertEquals(expectedIdentity, spyTu.getSimIdentity(config));
    }

    /**
     * Verify that {@code null} will be returned when IMSI encryption failed.
     *
     * @throws Exception
     */
    @Test
    public void getEncryptedIdentityFailed() throws Exception {
        Cipher cipher = mock(Cipher.class);
        String keyIdentifier = "key=testKey";
        String imsi = "3214561234567890";
        // static mocking
        MockitoSession session = ExtendedMockito.mockitoSession().mockStatic(
                Cipher.class).startMocking();
        try {
            lenient().when(Cipher.getInstance(anyString())).thenReturn(cipher);
            when(cipher.doFinal(any(byte[].class))).thenThrow(BadPaddingException.class);
            when(mDataTelephonyManager.getSubscriberId()).thenReturn(imsi);
            when(mDataTelephonyManager.getSimOperator()).thenReturn("321456");
            ImsiEncryptionInfo info = mock(ImsiEncryptionInfo.class);
            when(info.getPublicKey()).thenReturn(null);
            when(mDataTelephonyManager.getCarrierInfoForImsiEncryption(
                    eq(TelephonyManager.KEY_TYPE_WLAN)))
                    .thenReturn(info);
            WifiCarrierInfoManager spyTu = spy(mWifiCarrierInfoManager);
            doReturn(true).when(spyTu).requiresImsiEncryption(DATA_SUBID);

            WifiConfiguration config =
                    WifiConfigurationTestUtil.createEapNetwork(WifiEnterpriseConfig.Eap.AKA,
                            WifiEnterpriseConfig.Phase2.NONE);
            config.carrierId = DATA_CARRIER_ID;

            assertNull(spyTu.getSimIdentity(config));
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void getSimIdentity2DigitMnc() {
        final Pair<String, String> expectedIdentity = Pair.create(
                "1321560123456789@wlan.mnc056.mcc321.3gppnetwork.org", "");

        when(mDataTelephonyManager.getSubscriberId()).thenReturn("321560123456789");
        when(mDataTelephonyManager.getSimOperator()).thenReturn("32156");
        when(mDataTelephonyManager.getCarrierInfoForImsiEncryption(anyInt())).thenReturn(null);
        WifiConfiguration config =
                WifiConfigurationTestUtil.createEapNetwork(WifiEnterpriseConfig.Eap.SIM,
                        WifiEnterpriseConfig.Phase2.NONE);
        config.carrierId = DATA_CARRIER_ID;

        assertEquals(expectedIdentity, mWifiCarrierInfoManager.getSimIdentity(config));
    }

    @Test
    public void getSimIdentityUnknownMccMnc() {
        final Pair<String, String> expectedIdentity = Pair.create(
                "13214560123456789@wlan.mnc456.mcc321.3gppnetwork.org", "");

        when(mDataTelephonyManager.getSubscriberId()).thenReturn("3214560123456789");
        when(mDataTelephonyManager.getSimOperator()).thenReturn(null);
        when(mDataTelephonyManager.getCarrierInfoForImsiEncryption(anyInt())).thenReturn(null);
        WifiConfiguration config =
                WifiConfigurationTestUtil.createEapNetwork(WifiEnterpriseConfig.Eap.SIM,
                        WifiEnterpriseConfig.Phase2.NONE);
        config.carrierId = DATA_CARRIER_ID;

        assertEquals(expectedIdentity, mWifiCarrierInfoManager.getSimIdentity(config));
    }

    @Test
    public void getSimIdentityNonTelephonyConfig() {
        when(mDataTelephonyManager.getSubscriberId()).thenReturn("321560123456789");
        when(mDataTelephonyManager.getSimOperator()).thenReturn("32156");

        assertEquals(null,
                mWifiCarrierInfoManager.getSimIdentity(WifiConfigurationTestUtil.createEapNetwork(
                        WifiEnterpriseConfig.Eap.TTLS, WifiEnterpriseConfig.Phase2.SIM)));
        assertEquals(null,
                mWifiCarrierInfoManager.getSimIdentity(WifiConfigurationTestUtil.createEapNetwork(
                        WifiEnterpriseConfig.Eap.PEAP, WifiEnterpriseConfig.Phase2.MSCHAPV2)));
        assertEquals(null,
                mWifiCarrierInfoManager.getSimIdentity(WifiConfigurationTestUtil.createEapNetwork(
                        WifiEnterpriseConfig.Eap.TLS, WifiEnterpriseConfig.Phase2.NONE)));
        assertEquals(null,
                mWifiCarrierInfoManager.getSimIdentity(new WifiConfiguration()));
    }

    /**
     * Produce a base64 encoded length byte + data.
     */
    private static String createSimChallengeRequest(byte[] challengeValue) {
        byte[] challengeLengthAndValue = new byte[challengeValue.length + 1];
        challengeLengthAndValue[0] = (byte) challengeValue.length;
        for (int i = 0; i < challengeValue.length; ++i) {
            challengeLengthAndValue[i + 1] = challengeValue[i];
        }
        return Base64.encodeToString(challengeLengthAndValue, android.util.Base64.NO_WRAP);
    }

    /**
     * Produce a base64 encoded data without length.
     */
    private static String create2gUsimChallengeRequest(byte[] challengeValue) {
        return Base64.encodeToString(challengeValue, android.util.Base64.NO_WRAP);
    }

    /**
     * Produce a base64 encoded sres length byte + sres + kc length byte + kc.
     */
    private static String createGsmSimAuthResponse(byte[] sresValue, byte[] kcValue) {
        int overallLength = sresValue.length + kcValue.length + 2;
        byte[] result = new byte[sresValue.length + kcValue.length + 2];
        int idx = 0;
        result[idx++] = (byte) sresValue.length;
        for (int i = 0; i < sresValue.length; ++i) {
            result[idx++] = sresValue[i];
        }
        result[idx++] = (byte) kcValue.length;
        for (int i = 0; i < kcValue.length; ++i) {
            result[idx++] = kcValue[i];
        }
        return Base64.encodeToString(result, Base64.NO_WRAP);
    }

    /**
     * Produce a base64 encoded sres + kc without length.
     */
    private static String create2gUsimAuthResponse(byte[] sresValue, byte[] kcValue) {
        int overallLength = sresValue.length + kcValue.length;
        byte[] result = new byte[sresValue.length + kcValue.length];
        int idx = 0;
        for (int i = 0; i < sresValue.length; ++i) {
            result[idx++] = sresValue[i];
        }
        for (int i = 0; i < kcValue.length; ++i) {
            result[idx++] = kcValue[i];
        }
        return Base64.encodeToString(result, Base64.NO_WRAP);
    }

    @Test
    public void getGsmSimAuthResponseInvalidRequest() {
        final String[] invalidRequests = { null, "", "XXXX" };
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE);

        assertEquals("", mWifiCarrierInfoManager.getGsmSimAuthResponse(invalidRequests, config));
    }

    @Test
    public void getGsmSimAuthResponseFailedSimResponse() {
        final String[] failedRequests = { "5E5F" };
        when(mDataTelephonyManager.getIccAuthentication(anyInt(), anyInt(),
                eq(createSimChallengeRequest(new byte[] { 0x5e, 0x5f })))).thenReturn(null);
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE);

        assertEquals(null, mWifiCarrierInfoManager.getGsmSimAuthResponse(failedRequests, config));
    }

    @Test
    public void getGsmSimAuthResponseUsim() {
        when(mDataTelephonyManager.getIccAuthentication(TelephonyManager.APPTYPE_USIM,
                        TelephonyManager.AUTHTYPE_EAP_SIM,
                        createSimChallengeRequest(new byte[] { 0x1b, 0x2b })))
                .thenReturn(createGsmSimAuthResponse(new byte[] { 0x1D, 0x2C },
                                new byte[] { 0x3B, 0x4A }));
        when(mDataTelephonyManager.getIccAuthentication(TelephonyManager.APPTYPE_USIM,
                        TelephonyManager.AUTHTYPE_EAP_SIM,
                        createSimChallengeRequest(new byte[] { 0x01, 0x22 })))
                .thenReturn(createGsmSimAuthResponse(new byte[] { 0x11, 0x11 },
                                new byte[] { 0x12, 0x34 }));
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE);

        assertEquals(":3b4a:1d2c:1234:1111", mWifiCarrierInfoManager.getGsmSimAuthResponse(
                        new String[] { "1B2B", "0122" }, config));
    }

    @Test
    public void getGsmSimpleSimAuthResponseInvalidRequest() {
        final String[] invalidRequests = { null, "", "XXXX" };
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE);

        assertEquals("",
                mWifiCarrierInfoManager.getGsmSimpleSimAuthResponse(invalidRequests, config));
    }

    @Test
    public void getGsmSimpleSimAuthResponseFailedSimResponse() {
        final String[] failedRequests = { "5E5F" };
        when(mDataTelephonyManager.getIccAuthentication(anyInt(), anyInt(),
                eq(createSimChallengeRequest(new byte[] { 0x5e, 0x5f })))).thenReturn(null);
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE);

        assertEquals(null,
                mWifiCarrierInfoManager.getGsmSimpleSimAuthResponse(failedRequests, config));
    }

    @Test
    public void getGsmSimpleSimAuthResponse() {
        when(mDataTelephonyManager.getIccAuthentication(TelephonyManager.APPTYPE_SIM,
                        TelephonyManager.AUTHTYPE_EAP_SIM,
                        createSimChallengeRequest(new byte[] { 0x1a, 0x2b })))
                .thenReturn(createGsmSimAuthResponse(new byte[] { 0x1D, 0x2C },
                                new byte[] { 0x3B, 0x4A }));
        when(mDataTelephonyManager.getIccAuthentication(TelephonyManager.APPTYPE_SIM,
                        TelephonyManager.AUTHTYPE_EAP_SIM,
                        createSimChallengeRequest(new byte[] { 0x01, 0x23 })))
                .thenReturn(createGsmSimAuthResponse(new byte[] { 0x33, 0x22 },
                                new byte[] { 0x11, 0x00 }));
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE);

        assertEquals(":3b4a:1d2c:1100:3322", mWifiCarrierInfoManager.getGsmSimpleSimAuthResponse(
                        new String[] { "1A2B", "0123" }, config));
    }

    @Test
    public void getGsmSimpleSimNoLengthAuthResponseInvalidRequest() {
        final String[] invalidRequests = { null, "", "XXXX" };
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE);

        assertEquals("", mWifiCarrierInfoManager.getGsmSimpleSimNoLengthAuthResponse(
                invalidRequests, config));
    }

    @Test
    public void getGsmSimpleSimNoLengthAuthResponseFailedSimResponse() {
        final String[] failedRequests = { "5E5F" };
        when(mDataTelephonyManager.getIccAuthentication(anyInt(), anyInt(),
                eq(create2gUsimChallengeRequest(new byte[] { 0x5e, 0x5f })))).thenReturn(null);
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE);

        assertEquals(null, mWifiCarrierInfoManager.getGsmSimpleSimNoLengthAuthResponse(
                failedRequests, config));
    }

    @Test
    public void getGsmSimpleSimNoLengthAuthResponse() {
        when(mDataTelephonyManager.getIccAuthentication(TelephonyManager.APPTYPE_SIM,
                        TelephonyManager.AUTHTYPE_EAP_SIM,
                        create2gUsimChallengeRequest(new byte[] { 0x1a, 0x2b })))
                .thenReturn(create2gUsimAuthResponse(new byte[] { 0x1a, 0x2b, 0x3c, 0x4d },
                                new byte[] { 0x1a, 0x2b, 0x3c, 0x4d, 0x5e, 0x6f, 0x7a, 0x1a }));
        when(mDataTelephonyManager.getIccAuthentication(TelephonyManager.APPTYPE_SIM,
                        TelephonyManager.AUTHTYPE_EAP_SIM,
                        create2gUsimChallengeRequest(new byte[] { 0x01, 0x23 })))
                .thenReturn(create2gUsimAuthResponse(new byte[] { 0x12, 0x34, 0x56, 0x78 },
                                new byte[] { 0x12, 0x34, 0x56, 0x78, 0x12, 0x34, 0x56, 0x78 }));
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE);

        assertEquals(":1a2b3c4d5e6f7a1a:1a2b3c4d:1234567812345678:12345678",
                mWifiCarrierInfoManager.getGsmSimpleSimNoLengthAuthResponse(
                        new String[] { "1A2B", "0123" }, config));
    }

    /**
     * Produce a base64 encoded tag + res length byte + res + ck length byte + ck + ik length byte +
     * ik.
     */
    private static String create3GSimAuthUmtsAuthResponse(byte[] res, byte[] ck, byte[] ik) {
        byte[] result = new byte[res.length + ck.length + ik.length + 4];
        int idx = 0;
        result[idx++] = (byte) 0xdb;
        result[idx++] = (byte) res.length;
        for (int i = 0; i < res.length; ++i) {
            result[idx++] = res[i];
        }
        result[idx++] = (byte) ck.length;
        for (int i = 0; i < ck.length; ++i) {
            result[idx++] = ck[i];
        }
        result[idx++] = (byte) ik.length;
        for (int i = 0; i < ik.length; ++i) {
            result[idx++] = ik[i];
        }
        return Base64.encodeToString(result, Base64.NO_WRAP);
    }

    private static String create3GSimAuthUmtsAutsResponse(byte[] auts) {
        byte[] result = new byte[auts.length + 2];
        int idx = 0;
        result[idx++] = (byte) 0xdc;
        result[idx++] = (byte) auts.length;
        for (int i = 0; i < auts.length; ++i) {
            result[idx++] = auts[i];
        }
        return Base64.encodeToString(result, Base64.NO_WRAP);
    }

    @Test
    public void get3GAuthResponseInvalidRequest() {
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.AKA, WifiEnterpriseConfig.Phase2.NONE);

        assertEquals(null, mWifiCarrierInfoManager.get3GAuthResponse(
                new SimAuthRequestData(0, 0, "SSID", new String[]{"0123"}), config));
        assertEquals(null, mWifiCarrierInfoManager.get3GAuthResponse(
                new SimAuthRequestData(0, 0, "SSID", new String[]{"xyz2", "1234"}),
                config));
        verifyNoMoreInteractions(mDataTelephonyManager);
    }

    @Test
    public void get3GAuthResponseNullIccAuthentication() {
        when(mDataTelephonyManager.getIccAuthentication(TelephonyManager.APPTYPE_USIM,
                        TelephonyManager.AUTHTYPE_EAP_AKA, "AgEjAkVn")).thenReturn(null);
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.AKA, WifiEnterpriseConfig.Phase2.NONE);
        SimAuthResponseData response = mWifiCarrierInfoManager.get3GAuthResponse(
                new SimAuthRequestData(0, 0, "SSID", new String[]{"0123", "4567"}),
                config);

        assertNull(response);
    }

    @Test
    public void get3GAuthResponseIccAuthenticationTooShort() {
        when(mDataTelephonyManager.getIccAuthentication(TelephonyManager.APPTYPE_USIM,
                        TelephonyManager.AUTHTYPE_EAP_AKA, "AgEjAkVn"))
                .thenReturn(Base64.encodeToString(new byte[] {(byte) 0xdc}, Base64.NO_WRAP));
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.AKA, WifiEnterpriseConfig.Phase2.NONE);
        SimAuthResponseData response = mWifiCarrierInfoManager.get3GAuthResponse(
                new SimAuthRequestData(0, 0, "SSID", new String[]{"0123", "4567"}),
                config);

        assertNull(response);
    }

    @Test
    public void get3GAuthResponseBadTag() {
        when(mDataTelephonyManager.getIccAuthentication(TelephonyManager.APPTYPE_USIM,
                        TelephonyManager.AUTHTYPE_EAP_AKA, "AgEjAkVn"))
                .thenReturn(Base64.encodeToString(new byte[] {0x31, 0x1, 0x2, 0x3, 0x4},
                                Base64.NO_WRAP));
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.AKA, WifiEnterpriseConfig.Phase2.NONE);
        SimAuthResponseData response = mWifiCarrierInfoManager.get3GAuthResponse(
                new SimAuthRequestData(0, 0, "SSID", new String[]{"0123", "4567"}),
                config);

        assertNull(response);
    }

    @Test
    public void get3GAuthResponseUmtsAuth() {
        when(mDataTelephonyManager.getIccAuthentication(TelephonyManager.APPTYPE_USIM,
                        TelephonyManager.AUTHTYPE_EAP_AKA, "AgEjAkVn"))
                .thenReturn(create3GSimAuthUmtsAuthResponse(new byte[] {0x11, 0x12},
                                new byte[] {0x21, 0x22, 0x23}, new byte[] {0x31}));
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.AKA, WifiEnterpriseConfig.Phase2.NONE);
        SimAuthResponseData response = mWifiCarrierInfoManager.get3GAuthResponse(
                new SimAuthRequestData(0, 0, "SSID", new String[]{"0123", "4567"}),
                config);

        assertNotNull(response);
        assertEquals("UMTS-AUTH", response.type);
        assertEquals(":31:212223:1112", response.response);
    }

    @Test
    public void get3GAuthResponseUmtsAuts() {
        when(mDataTelephonyManager.getIccAuthentication(TelephonyManager.APPTYPE_USIM,
                        TelephonyManager.AUTHTYPE_EAP_AKA, "AgEjAkVn"))
                .thenReturn(create3GSimAuthUmtsAutsResponse(new byte[] {0x22, 0x33}));
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.AKA, WifiEnterpriseConfig.Phase2.NONE);
        SimAuthResponseData response = mWifiCarrierInfoManager.get3GAuthResponse(
                new SimAuthRequestData(0, 0, "SSID", new String[]{"0123", "4567"}),
                config);
        assertNotNull(response);
        assertEquals("UMTS-AUTS", response.type);
        assertEquals(":2233", response.response);
    }

    /**
     * Verify that anonymous identity should be a valid format based on MCC/MNC of current SIM.
     */
    @Test
    public void getAnonymousIdentityWithSim() {
        String mccmnc = "123456";
        String expectedIdentity = ANONYMOUS_IDENTITY;
        when(mDataTelephonyManager.getSimOperator()).thenReturn(mccmnc);
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.AKA, WifiEnterpriseConfig.Phase2.NONE);

        assertEquals(expectedIdentity,
                mWifiCarrierInfoManager.getAnonymousIdentityWith3GppRealm(config));
    }

    /**
     * Verify that anonymous identity should be {@code null} when SIM is absent.
     */
    @Test
    public void getAnonymousIdentityWithoutSim() {
        when(mDataTelephonyManager.getSimApplicationState())
                .thenReturn(TelephonyManager.SIM_STATE_NOT_READY);
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.AKA, WifiEnterpriseConfig.Phase2.NONE);

        assertNull(mWifiCarrierInfoManager.getAnonymousIdentityWith3GppRealm(config));
    }

    /**
     * Verify SIM is present.
     */
    @Test
    public void isSimPresentWithValidSubscriptionIdList() {
        SubscriptionInfo subInfo1 = mock(SubscriptionInfo.class);
        when(subInfo1.getSubscriptionId()).thenReturn(DATA_SUBID);
        SubscriptionInfo subInfo2 = mock(SubscriptionInfo.class);
        when(subInfo2.getSubscriptionId()).thenReturn(NON_DATA_SUBID);
        when(mSubscriptionManager.getCompleteActiveSubscriptionInfoList())
                .thenReturn(Arrays.asList(subInfo1, subInfo2));
        assertTrue(mWifiCarrierInfoManager.isSimReady(DATA_SUBID));
    }

    /**
     * Verify SIM is not present.
     */
    @Test
    public void isSimPresentWithInvalidOrEmptySubscriptionIdList() {
        when(mSubscriptionManager.getCompleteActiveSubscriptionInfoList())
                .thenReturn(Collections.emptyList());
        mListenerArgumentCaptor.getValue().onSubscriptionsChanged();
        mLooper.dispatchAll();

        assertFalse(mWifiCarrierInfoManager.isSimReady(DATA_SUBID));

        SubscriptionInfo subInfo = mock(SubscriptionInfo.class);
        when(subInfo.getSubscriptionId()).thenReturn(NON_DATA_SUBID);
        when(mSubscriptionManager.getCompleteActiveSubscriptionInfoList())
                .thenReturn(Arrays.asList(subInfo));
        mListenerArgumentCaptor.getValue().onSubscriptionsChanged();
        mLooper.dispatchAll();
        assertFalse(mWifiCarrierInfoManager.isSimReady(DATA_SUBID));
    }

    /**
     * Verify SIM is considered not present when SIM state is not ready
     */
    @Test
    public void isSimPresentWithValidSubscriptionIdListWithSimStateNotReady() {
        SubscriptionInfo subInfo1 = mock(SubscriptionInfo.class);
        when(subInfo1.getSubscriptionId()).thenReturn(DATA_SUBID);
        SubscriptionInfo subInfo2 = mock(SubscriptionInfo.class);
        when(subInfo2.getSubscriptionId()).thenReturn(NON_DATA_SUBID);
        when(mSubscriptionManager.getCompleteActiveSubscriptionInfoList())
                .thenReturn(Arrays.asList(subInfo1, subInfo2));
        when(mDataTelephonyManager.getSimApplicationState())
                .thenReturn(TelephonyManager.SIM_STATE_NETWORK_LOCKED);
        assertFalse(mWifiCarrierInfoManager.isSimReady(DATA_SUBID));
    }

    /**
     * Verify SIM is considered not present when carrierConfig is not ready.
     */
    @Test
    public void isSimPresentWithValidSubscriptionIdListWithCarrierConfigNotReady() {
        SubscriptionInfo subInfo1 = mock(SubscriptionInfo.class);
        when(subInfo1.getSubscriptionId()).thenReturn(DATA_SUBID);
        SubscriptionInfo subInfo2 = mock(SubscriptionInfo.class);
        when(subInfo2.getSubscriptionId()).thenReturn(NON_DATA_SUBID);
        when(mSubscriptionManager.getCompleteActiveSubscriptionInfoList())
                .thenReturn(Arrays.asList(subInfo1, subInfo2));
        when(mCarrierConfigManager.getConfigForSubId(anyInt())).thenReturn(null);
        ArgumentCaptor<BroadcastReceiver> receiver =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(receiver.capture(), any(IntentFilter.class));
        receiver.getValue().onReceive(mContext,
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));

        assertFalse(mWifiCarrierInfoManager.isSimReady(DATA_SUBID));
    }

    /**
     * The active SubscriptionInfo List may be null or empty from Telephony.
     */
    @Test
    public void getBestMatchSubscriptionIdWithEmptyActiveSubscriptionInfoList() {
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.AKA, WifiEnterpriseConfig.Phase2.NONE);
        when(mSubscriptionManager.getCompleteActiveSubscriptionInfoList()).thenReturn(null);
        mListenerArgumentCaptor.getValue().onSubscriptionsChanged();
        mLooper.dispatchAll();

        assertEquals(INVALID_SUBID, mWifiCarrierInfoManager.getBestMatchSubscriptionId(config));

        when(mSubscriptionManager.getCompleteActiveSubscriptionInfoList())
                .thenReturn(Collections.emptyList());
        mListenerArgumentCaptor.getValue().onSubscriptionsChanged();
        mLooper.dispatchAll();

        assertEquals(INVALID_SUBID, mWifiCarrierInfoManager.getBestMatchSubscriptionId(config));
    }

    /**
     * The matched Subscription ID should be that of data SIM when carrier ID is not specified.
     */
    @Test
    public void getBestMatchSubscriptionIdForEnterpriseWithoutCarrierIdFieldForSimConfig() {
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.AKA, WifiEnterpriseConfig.Phase2.NONE);

        assertEquals(DATA_SUBID, mWifiCarrierInfoManager.getBestMatchSubscriptionId(config));
    }

    /**
     * The matched Subscription ID should be invalid if the configuration does not require
     * SIM card and the carrier ID is not specified.
     */
    @Test
    public void getBestMatchSubscriptionIdForEnterpriseWithoutCarrierIdFieldForNonSimConfig() {
        WifiConfiguration config = new WifiConfiguration();

        assertEquals(INVALID_SUBID, mWifiCarrierInfoManager.getBestMatchSubscriptionId(config));
    }

    /**
     * If the carrier ID is specifed for EAP-SIM configuration, the corresponding Subscription ID
     * should be returned.
     */
    @Test
    public void getBestMatchSubscriptionIdForEnterpriseWithNonDataCarrierId() {
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.AKA, WifiEnterpriseConfig.Phase2.NONE);
        config.carrierId = NON_DATA_CARRIER_ID;

        assertEquals(NON_DATA_SUBID, mWifiCarrierInfoManager.getBestMatchSubscriptionId(config));

        config.carrierId = DATA_CARRIER_ID;
        assertEquals(DATA_SUBID, mWifiCarrierInfoManager.getBestMatchSubscriptionId(config));
    }

    /**
     * If the passpoint profile have valid carrier ID, the matching sub ID should be returned.
     */
    @Test
    public void getBestMatchSubscriptionIdForPasspointWithValidCarrierId() {
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.AKA, WifiEnterpriseConfig.Phase2.NONE);
        config.carrierId = DATA_CARRIER_ID;
        WifiConfiguration spyConfig = spy(config);
        doReturn(true).when(spyConfig).isPasspoint();

        assertEquals(DATA_SUBID, mWifiCarrierInfoManager.getBestMatchSubscriptionId(spyConfig));
    }

    /**
     * If there is no matching SIM card, the matching sub ID should be invalid.
     */
    @Test
    public void getBestMatchSubscriptionIdForPasspointInvalidCarrierId() {
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.AKA, WifiEnterpriseConfig.Phase2.NONE);
        WifiConfiguration spyConfig = spy(config);
        doReturn(true).when(spyConfig).isPasspoint();

        assertEquals(INVALID_SUBID, mWifiCarrierInfoManager.getBestMatchSubscriptionId(spyConfig));
    }

    /**
     * The matched Subscription ID should be invalid if the SIM card for the specified carrier ID
     * is absent.
     */
    @Test
    public void getBestMatchSubscriptionIdWithDeactiveCarrierId() {
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.AKA, WifiEnterpriseConfig.Phase2.NONE);
        config.carrierId = DEACTIVE_CARRIER_ID;

        assertEquals(INVALID_SUBID, mWifiCarrierInfoManager.getBestMatchSubscriptionId(config));
    }

    /**
     * The matched Subscription ID should be invalid if the config is null;
     */
    @Test
    public void getBestMatchSubscriptionIdWithNullConfig() {
        assertEquals(INVALID_SUBID, mWifiCarrierInfoManager.getBestMatchSubscriptionId(null));
    }

    /**
     * Verify that the result is null if no active SIM is matched.
     */
    @Test
    public void getMatchingImsiCarrierIdWithDeactiveCarrierId() {
        when(mSubscriptionManager.getCompleteActiveSubscriptionInfoList())
                .thenReturn(Collections.emptyList());

        assertNull(mWifiCarrierInfoManager.getMatchingImsiBySubId(INVALID_SUBID));
    }

    /**
     * Verify that a SIM is matched with carrier ID, and it requires IMSI encryption,
     * when the IMSI encryption info is not available, it should return null.
     */
    @Test
    public void getMatchingImsiCarrierIdWithValidCarrierIdForImsiEncryptionCheck() {
        WifiCarrierInfoManager spyTu = spy(mWifiCarrierInfoManager);
        doReturn(true).when(spyTu).requiresImsiEncryption(DATA_SUBID);
        doReturn(false).when(spyTu).isImsiEncryptionInfoAvailable(DATA_SUBID);

        assertNull(spyTu.getMatchingImsiBySubId(DATA_SUBID));
    }

    /**
     * Verify that a SIM is matched with carrier ID, and OOB pseudonym is enabled,
     * when the OOB pseudonym is not available, it should return null.
     */
    @Test
    public void getMatchingImsiCarrierIdWithValidCarrierIdForOobPseudonymCheck() {
        when(mDataTelephonyManager.getCarrierIdFromSimMccMnc()).thenReturn(DATA_CARRIER_ID);
        when(mDataTelephonyManager.getSimCarrierId()).thenReturn(DATA_CARRIER_ID);
        WifiCarrierInfoManager spyTu = spy(mWifiCarrierInfoManager);
        doReturn(true).when(spyTu).isOobPseudonymFeatureEnabled(DATA_CARRIER_ID);
        when(mWifiPseudonymManager.getValidPseudonymInfo(DATA_CARRIER_ID))
                .thenReturn(Optional.empty());

        assertNull(spyTu.getMatchingImsiBySubId(DATA_SUBID));

        verify(mWifiPseudonymManager).retrievePseudonymOnFailureTimeoutExpired(DATA_CARRIER_ID);
    }

    /**
     * Verify that if there is SIM card whose carrier ID is the same as the input, the correct IMSI
     * and carrier ID would be returned.
     */
    @Test
    public void getMatchingImsiCarrierIdWithValidCarrierId() {
        assertEquals(DATA_FULL_IMSI,
                mWifiCarrierInfoManager.getMatchingImsiBySubId(DATA_SUBID));
    }

    /**
     * Verify that if there is no SIM, it should match nothing.
     */
    @Test
    public void getMatchingImsiCarrierIdWithEmptyActiveSubscriptionInfoList() {
        when(mSubscriptionManager.getCompleteActiveSubscriptionInfoList()).thenReturn(null);
        mListenerArgumentCaptor.getValue().onSubscriptionsChanged();
        mLooper.dispatchAll();

        assertNull(mWifiCarrierInfoManager.getMatchingImsiCarrierId(MATCH_PREFIX_IMSI));

        when(mSubscriptionManager.getCompleteActiveSubscriptionInfoList())
                .thenReturn(Collections.emptyList());
        mListenerArgumentCaptor.getValue().onSubscriptionsChanged();
        mLooper.dispatchAll();

        assertNull(mWifiCarrierInfoManager.getMatchingImsiCarrierId(MATCH_PREFIX_IMSI));
    }

    /**
     * Verify that if there is no matching SIM, it should match nothing.
     */
    @Test
    public void getMatchingImsiCarrierIdWithNoMatchImsi() {
        // data SIM is MNO.
        when(mDataTelephonyManager.getCarrierIdFromSimMccMnc()).thenReturn(DATA_CARRIER_ID);
        when(mDataTelephonyManager.getSimCarrierId()).thenReturn(DATA_CARRIER_ID);
        // non data SIM is MNO.
        when(mNonDataTelephonyManager.getCarrierIdFromSimMccMnc()).thenReturn(NON_DATA_CARRIER_ID);
        when(mNonDataTelephonyManager.getSimCarrierId()).thenReturn(NON_DATA_CARRIER_ID);

        assertNull(mWifiCarrierInfoManager.getMatchingImsiCarrierId(NO_MATCH_PREFIX_IMSI));
    }

    /**
     * Verify that if the matched SIM is the default data SIM and a MNO SIM, the information of it
     * should be returned.
     */
    @Test
    public void getMatchingImsiCarrierIdForDataAndMnoSimMatch() {
        // data SIM is MNO.
        when(mDataTelephonyManager.getCarrierIdFromSimMccMnc()).thenReturn(DATA_CARRIER_ID);
        when(mDataTelephonyManager.getSimCarrierId()).thenReturn(DATA_CARRIER_ID);
        // non data SIM is MNO.
        when(mNonDataTelephonyManager.getCarrierIdFromSimMccMnc()).thenReturn(NON_DATA_CARRIER_ID);
        when(mNonDataTelephonyManager.getSimCarrierId()).thenReturn(NON_DATA_CARRIER_ID);

        Pair<String, Integer> ic = mWifiCarrierInfoManager
                .getMatchingImsiCarrierId(MATCH_PREFIX_IMSI);

        assertEquals(new Pair<>(DATA_FULL_IMSI, DATA_CARRIER_ID), ic);

        // non data SIM is MVNO
        when(mNonDataTelephonyManager.getCarrierIdFromSimMccMnc())
                .thenReturn(PARENT_NON_DATA_CARRIER_ID);
        when(mNonDataTelephonyManager.getSimCarrierId()).thenReturn(NON_DATA_CARRIER_ID);

        assertEquals(new Pair<>(DATA_FULL_IMSI, DATA_CARRIER_ID),
                mWifiCarrierInfoManager.getMatchingImsiCarrierId(MATCH_PREFIX_IMSI));

        // non data SIM doesn't match.
        when(mNonDataTelephonyManager.getCarrierIdFromSimMccMnc()).thenReturn(NON_DATA_CARRIER_ID);
        when(mNonDataTelephonyManager.getSimCarrierId()).thenReturn(NON_DATA_CARRIER_ID);
        when(mNonDataTelephonyManager.getSubscriberId()).thenReturn(NO_MATCH_FULL_IMSI);
        when(mNonDataTelephonyManager.getSimOperator())
                .thenReturn(NO_MATCH_OPERATOR_NUMERIC);

        assertEquals(new Pair<>(DATA_FULL_IMSI, DATA_CARRIER_ID),
                mWifiCarrierInfoManager.getMatchingImsiCarrierId(MATCH_PREFIX_IMSI));
    }

    /**
     * Verify that if the matched SIM is the default data SIM and a MVNO SIM, and no MNO SIM was
     * matched, the information of it should be returned.
     */
    @Test
    public void getMatchingImsiCarrierIdForDataAndMvnoSimMatch() {
        // data SIM is MVNO.
        when(mDataTelephonyManager.getCarrierIdFromSimMccMnc()).thenReturn(PARENT_DATA_CARRIER_ID);
        when(mDataTelephonyManager.getSimCarrierId()).thenReturn(DATA_CARRIER_ID);
        // non data SIM is MVNO.
        when(mNonDataTelephonyManager.getCarrierIdFromSimMccMnc())
                .thenReturn(PARENT_NON_DATA_CARRIER_ID);
        when(mNonDataTelephonyManager.getSimCarrierId()).thenReturn(NON_DATA_CARRIER_ID);

        Pair<String, Integer> ic = mWifiCarrierInfoManager
                .getMatchingImsiCarrierId(MATCH_PREFIX_IMSI);

        assertEquals(new Pair<>(DATA_FULL_IMSI, DATA_CARRIER_ID), ic);

        // non data SIM doesn't match.
        when(mNonDataTelephonyManager.getCarrierIdFromSimMccMnc()).thenReturn(NON_DATA_CARRIER_ID);
        when(mNonDataTelephonyManager.getSimCarrierId()).thenReturn(NON_DATA_CARRIER_ID);
        when(mNonDataTelephonyManager.getSubscriberId()).thenReturn(NO_MATCH_FULL_IMSI);
        when(mNonDataTelephonyManager.getSimOperator())
                .thenReturn(NO_MATCH_OPERATOR_NUMERIC);

        assertEquals(new Pair<>(DATA_FULL_IMSI, DATA_CARRIER_ID),
                mWifiCarrierInfoManager.getMatchingImsiCarrierId(MATCH_PREFIX_IMSI));
    }

    /**
     * Verify that if the matched SIM is a MNO SIM, even the default data SIM is matched as a MVNO
     * SIM, the information of MNO SIM still should be returned.
     */
    @Test
    public void getMatchingImsiCarrierIdForNonDataAndMnoSimMatch() {
        // data SIM is MVNO.
        when(mDataTelephonyManager.getCarrierIdFromSimMccMnc()).thenReturn(PARENT_DATA_CARRIER_ID);
        when(mDataTelephonyManager.getSimCarrierId()).thenReturn(DATA_CARRIER_ID);
        // non data SIM is MNO.
        when(mNonDataTelephonyManager.getCarrierIdFromSimMccMnc()).thenReturn(NON_DATA_CARRIER_ID);
        when(mNonDataTelephonyManager.getSimCarrierId()).thenReturn(NON_DATA_CARRIER_ID);


        Pair<String, Integer> ic = mWifiCarrierInfoManager
                .getMatchingImsiCarrierId(MATCH_PREFIX_IMSI);

        assertEquals(new Pair<>(NON_DATA_FULL_IMSI, NON_DATA_CARRIER_ID), ic);

        // data SIM doesn't match
        when(mDataTelephonyManager.getCarrierIdFromSimMccMnc()).thenReturn(DATA_CARRIER_ID);
        when(mDataTelephonyManager.getSimCarrierId()).thenReturn(DATA_CARRIER_ID);
        when(mDataTelephonyManager.getSubscriberId()).thenReturn(NO_MATCH_FULL_IMSI);
        when(mDataTelephonyManager.getSimOperator()).thenReturn(NO_MATCH_OPERATOR_NUMERIC);

        assertEquals(new Pair<>(NON_DATA_FULL_IMSI, NON_DATA_CARRIER_ID),
                mWifiCarrierInfoManager.getMatchingImsiCarrierId(MATCH_PREFIX_IMSI));
    }

    /**
     * Verify that if only a MVNO SIM is matched, the information of it should be returned.
     */
    @Test
    public void getMatchingImsiCarrierIdForMvnoSimMatch() {
        // data SIM is MNO, but IMSI doesn't match.
        when(mDataTelephonyManager.getCarrierIdFromSimMccMnc()).thenReturn(DATA_CARRIER_ID);
        when(mDataTelephonyManager.getSimCarrierId()).thenReturn(DATA_CARRIER_ID);
        when(mDataTelephonyManager.getSubscriberId()).thenReturn(NO_MATCH_FULL_IMSI);
        when(mDataTelephonyManager.getSimOperator()).thenReturn(NO_MATCH_OPERATOR_NUMERIC);
        // non data SIM is MVNO.
        when(mNonDataTelephonyManager.getCarrierIdFromSimMccMnc())
                .thenReturn(PARENT_NON_DATA_CARRIER_ID);
        when(mNonDataTelephonyManager.getSimCarrierId()).thenReturn(NON_DATA_CARRIER_ID);

        assertEquals(new Pair<>(NON_DATA_FULL_IMSI, NON_DATA_CARRIER_ID),
                mWifiCarrierInfoManager.getMatchingImsiCarrierId(MATCH_PREFIX_IMSI));
    }

    /**
     * Verify that a SIM is matched, and it requires IMSI encryption, when the IMSI encryption
     * info is not available, it should return null.
     */
    @Test
    public void getMatchingImsiCarrierIdForImsiEncryptionCheck() {
        // data SIM is MNO.
        when(mDataTelephonyManager.getCarrierIdFromSimMccMnc()).thenReturn(DATA_CARRIER_ID);
        when(mDataTelephonyManager.getSimCarrierId()).thenReturn(DATA_CARRIER_ID);
        // non data SIM does not match.
        when(mNonDataTelephonyManager.getCarrierIdFromSimMccMnc()).thenReturn(NON_DATA_CARRIER_ID);
        when(mNonDataTelephonyManager.getSimCarrierId()).thenReturn(NON_DATA_CARRIER_ID);
        when(mNonDataTelephonyManager.getSubscriberId()).thenReturn(NO_MATCH_FULL_IMSI);
        when(mNonDataTelephonyManager.getSimOperator())
                .thenReturn(NO_MATCH_OPERATOR_NUMERIC);
        WifiCarrierInfoManager spyTu = spy(mWifiCarrierInfoManager);
        doReturn(true).when(spyTu).requiresImsiEncryption(eq(DATA_SUBID));
        doReturn(false).when(spyTu).isImsiEncryptionInfoAvailable(eq(DATA_SUBID));

        assertNull(spyTu.getMatchingImsiCarrierId(MATCH_PREFIX_IMSI));
    }

    /**
     * Verify that a SIM is matched, and the OOB pseudonym is enabled, when the pseudonym info
     * is not available, it should return null.
     */
    @Test
    public void getMatchingImsiCarrierIdForOobPseudonymCheck() {
        // data SIM is MNO.
        when(mDataTelephonyManager.getCarrierIdFromSimMccMnc()).thenReturn(DATA_CARRIER_ID);
        when(mDataTelephonyManager.getSimCarrierId()).thenReturn(DATA_CARRIER_ID);
        // non data SIM does not match.
        when(mNonDataTelephonyManager.getCarrierIdFromSimMccMnc()).thenReturn(NON_DATA_CARRIER_ID);
        when(mNonDataTelephonyManager.getSimCarrierId()).thenReturn(NON_DATA_CARRIER_ID);
        when(mNonDataTelephonyManager.getSubscriberId()).thenReturn(NO_MATCH_FULL_IMSI);
        when(mNonDataTelephonyManager.getSimOperator())
                .thenReturn(NO_MATCH_OPERATOR_NUMERIC);
        WifiCarrierInfoManager spyTu = spy(mWifiCarrierInfoManager);
        doReturn(true).when(spyTu).isOobPseudonymFeatureEnabled(DATA_CARRIER_ID);
        when(mWifiPseudonymManager.getValidPseudonymInfo(DATA_CARRIER_ID))
                .thenReturn(Optional.empty());

        assertNull(spyTu.getMatchingImsiCarrierId(MATCH_PREFIX_IMSI));

        verify(mWifiPseudonymManager).retrievePseudonymOnFailureTimeoutExpired(DATA_CARRIER_ID);
    }

    /**
     * Verify that if there is no any SIM card, the carrier ID should be updated.
     */
    @Test
    public void tryUpdateCarrierIdForPasspointWithEmptyActiveSubscriptionList() {
        PasspointConfiguration config = mock(PasspointConfiguration.class);
        when(config.getCarrierId()).thenReturn(DATA_CARRIER_ID);
        when(mSubscriptionManager.getCompleteActiveSubscriptionInfoList()).thenReturn(null);

        assertFalse(mWifiCarrierInfoManager.tryUpdateCarrierIdForPasspoint(config));

        when(mSubscriptionManager.getCompleteActiveSubscriptionInfoList())
                .thenReturn(Collections.emptyList());

        assertFalse(mWifiCarrierInfoManager.tryUpdateCarrierIdForPasspoint(config));
    }

    /**
     * Verify that if the carrier ID has been assigned, it shouldn't be updated.
     */
    @Test
    public void tryUpdateCarrierIdForPasspointWithValidCarrieId() {
        PasspointConfiguration config = mock(PasspointConfiguration.class);
        when(config.getCarrierId()).thenReturn(DATA_CARRIER_ID);

        assertFalse(mWifiCarrierInfoManager.tryUpdateCarrierIdForPasspoint(config));
    }

    /**
     * Verify that if the passpoint profile doesn't have SIM credential, it shouldn't be updated.
     */
    @Test
    public void tryUpdateCarrierIdForPasspointWithNonSimCredential() {
        Credential credential = mock(Credential.class);
        PasspointConfiguration spyConfig = spy(new PasspointConfiguration());
        doReturn(credential).when(spyConfig).getCredential();
        when(credential.getSimCredential()).thenReturn(null);

        assertFalse(mWifiCarrierInfoManager.tryUpdateCarrierIdForPasspoint(spyConfig));
    }

    /**
     * Verify that if the passpoint profile only have IMSI prefix(mccmnc*) parameter,
     * it shouldn't be updated.
     */
    @Test
    public void tryUpdateCarrierIdForPasspointWithPrefixImsi() {
        Credential credential = mock(Credential.class);
        PasspointConfiguration spyConfig = spy(new PasspointConfiguration());
        doReturn(credential).when(spyConfig).getCredential();
        Credential.SimCredential simCredential = mock(Credential.SimCredential.class);
        when(credential.getSimCredential()).thenReturn(simCredential);
        when(simCredential.getImsi()).thenReturn(MATCH_PREFIX_IMSI);

        assertFalse(mWifiCarrierInfoManager.tryUpdateCarrierIdForPasspoint(spyConfig));
    }

    /**
     * Verify that if the passpoint profile has the full IMSI and wasn't assigned valid
     * carrier ID, it should be updated.
     */
    @Test
    public void tryUpdateCarrierIdForPasspointWithFullImsiAndActiveSim() {
        Credential credential = mock(Credential.class);
        PasspointConfiguration spyConfig = spy(new PasspointConfiguration());
        doReturn(credential).when(spyConfig).getCredential();
        Credential.SimCredential simCredential = mock(Credential.SimCredential.class);
        when(credential.getSimCredential()).thenReturn(simCredential);
        when(simCredential.getImsi()).thenReturn(DATA_FULL_IMSI);

        assertTrue(mWifiCarrierInfoManager.tryUpdateCarrierIdForPasspoint(spyConfig));
        assertEquals(DATA_CARRIER_ID, spyConfig.getCarrierId());
    }

    /**
     * Verify that if there is no SIM card matching the given IMSI, it shouldn't be updated.
     */
    @Test
    public void tryUpdateCarrierIdForPasspointWithFullImsiAndInactiveSim() {
        Credential credential = mock(Credential.class);
        PasspointConfiguration spyConfig = spy(new PasspointConfiguration());
        doReturn(credential).when(spyConfig).getCredential();
        Credential.SimCredential simCredential = mock(Credential.SimCredential.class);
        when(credential.getSimCredential()).thenReturn(simCredential);
        when(simCredential.getImsi()).thenReturn(NO_MATCH_PREFIX_IMSI);

        assertFalse(mWifiCarrierInfoManager.tryUpdateCarrierIdForPasspoint(spyConfig));
    }

    private void testIdentityWithSimAndEapAkaMethodPrefix(int method, String methodStr)
            throws Exception {
        when(mCarrierConfigManager.getConfigForSubId(DATA_SUBID))
                .thenReturn(generateTestCarrierConfig(true, true));
        when(mCarrierConfigManager.getConfigForSubId(NON_DATA_SUBID))
                .thenReturn(generateTestCarrierConfig(false));
        ArgumentCaptor<BroadcastReceiver> receiver =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(receiver.capture(), any(IntentFilter.class));

        receiver.getValue().onReceive(mContext,
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));

        assertTrue(mWifiCarrierInfoManager.requiresImsiEncryption(DATA_SUBID));

        String mccmnc = "123456";
        String expectedIdentity = methodStr + ANONYMOUS_IDENTITY;
        when(mDataTelephonyManager.getSimOperator()).thenReturn(mccmnc);
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork(
                method, WifiEnterpriseConfig.Phase2.NONE);

        assertEquals(expectedIdentity,
                mWifiCarrierInfoManager.getAnonymousIdentityWith3GppRealm(config));
    }

    /**
     * Verify that EAP Method prefix is added to the anonymous identity when required
     */
    @Test
    public void getAnonymousIdentityWithSimAndEapAkaMethodPrefix() throws Exception {
        testIdentityWithSimAndEapAkaMethodPrefix(WifiEnterpriseConfig.Eap.AKA, "0");
    }

    /**
     * Verify that EAP Method prefix is added to the anonymous identity when required
     */
    @Test
    public void getAnonymousIdentityWithSimAndEapSimMethodPrefix() throws Exception {
        testIdentityWithSimAndEapAkaMethodPrefix(WifiEnterpriseConfig.Eap.SIM, "1");
    }

    /**
     * Verify that EAP Method prefix is added to the anonymous identity when required
     */
    @Test
    public void getAnonymousIdentityWithSimAndEapAkaPrimeMethodPrefix() throws Exception {
        testIdentityWithSimAndEapAkaMethodPrefix(WifiEnterpriseConfig.Eap.AKA_PRIME, "6");
    }

    /**
     * Verify that isAnonymousAtRealmIdentity works as expected for anonymous identities with and
     * without a prefix.
     */
    @Test
    public void testIsAnonymousAtRealmIdentity() throws Exception {
        assertTrue(mWifiCarrierInfoManager.isAnonymousAtRealmIdentity(ANONYMOUS_IDENTITY));
        assertTrue(mWifiCarrierInfoManager.isAnonymousAtRealmIdentity("0" + ANONYMOUS_IDENTITY));
        assertTrue(mWifiCarrierInfoManager.isAnonymousAtRealmIdentity("1" + ANONYMOUS_IDENTITY));
        assertTrue(mWifiCarrierInfoManager.isAnonymousAtRealmIdentity("6" + ANONYMOUS_IDENTITY));
        assertFalse(mWifiCarrierInfoManager.isAnonymousAtRealmIdentity("AKA" + ANONYMOUS_IDENTITY));
    }

    /**
     * Verify when no subscription available, get carrier id for target package will return
     * UNKNOWN_CARRIER_ID.
     */
    @Test
    public void getCarrierPrivilegeWithNoActiveSubscription() {
        when(mSubscriptionManager.getCompleteActiveSubscriptionInfoList()).thenReturn(null);
        assertEquals(TelephonyManager.UNKNOWN_CARRIER_ID,
                mWifiCarrierInfoManager.getCarrierIdForPackageWithCarrierPrivileges(TEST_PACKAGE));

        when(mSubscriptionManager.getCompleteActiveSubscriptionInfoList())
                .thenReturn(Collections.emptyList());
        assertEquals(TelephonyManager.UNKNOWN_CARRIER_ID,
                mWifiCarrierInfoManager.getCarrierIdForPackageWithCarrierPrivileges(TEST_PACKAGE));
    }

    /**
     * Verify when package has no carrier privileges, get carrier id for that package will return
     * UNKNOWN_CARRIER_ID.
     */
    @Test
    public void getCarrierPrivilegeWithPackageHasNoPrivilege() {
        SubscriptionInfo subInfo = mock(SubscriptionInfo.class);
        when(subInfo.getSubscriptionId()).thenReturn(DATA_SUBID);
        when(mSubscriptionManager.getCompleteActiveSubscriptionInfoList())
                .thenReturn(Arrays.asList(subInfo));
        when(mDataTelephonyManager.checkCarrierPrivilegesForPackage(TEST_PACKAGE))
                .thenReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS);
        assertEquals(TelephonyManager.UNKNOWN_CARRIER_ID,
                mWifiCarrierInfoManager.getCarrierIdForPackageWithCarrierPrivileges(TEST_PACKAGE));
    }

    /**
     * Verify when package get carrier privileges from carrier, get carrier id for that package will
     * return the carrier id for that carrier.
     */
    @Test
    public void getCarrierPrivilegeWithPackageHasPrivilege() {
        SubscriptionInfo subInfo = mock(SubscriptionInfo.class);
        when(subInfo.getSubscriptionId()).thenReturn(DATA_SUBID);
        when(subInfo.getCarrierId()).thenReturn(DATA_CARRIER_ID);
        when(mSubscriptionManager.getCompleteActiveSubscriptionInfoList())
                .thenReturn(Arrays.asList(subInfo));
        when(mDataTelephonyManager.checkCarrierPrivilegesForPackage(TEST_PACKAGE))
                .thenReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS);
        assertEquals(DATA_CARRIER_ID,
                mWifiCarrierInfoManager.getCarrierIdForPackageWithCarrierPrivileges(TEST_PACKAGE));
    }

    /**
     * Verify getCarrierNameForSubId returns right value.
     */
    @Test
    public void getCarrierNameFromSubId() {
        assertEquals(CARRIER_NAME, mWifiCarrierInfoManager.getCarrierNameForSubId(DATA_SUBID));
        assertNull(mWifiCarrierInfoManager.getCarrierNameForSubId(NON_DATA_SUBID));
    }

    @Test
    public void testIsCarrierNetworkFromNonDataSim() {
        WifiConfiguration config = new WifiConfiguration();
        assertFalse(mWifiCarrierInfoManager.isCarrierNetworkFromNonDefaultDataSim(config));
        config.carrierId = DATA_CARRIER_ID;
        assertFalse(mWifiCarrierInfoManager.isCarrierNetworkFromNonDefaultDataSim(config));
        config.carrierId = NON_DATA_CARRIER_ID;
        assertTrue(mWifiCarrierInfoManager.isCarrierNetworkFromNonDefaultDataSim(config));
    }

    @Test
    public void testCheckSetClearImsiProtectionExemption() {
        InOrder inOrder = inOrder(mWifiConfigManager);
        assertFalse(mWifiCarrierInfoManager
                .hasUserApprovedImsiPrivacyExemptionForCarrier(DATA_CARRIER_ID));
        mWifiCarrierInfoManager.setHasUserApprovedImsiPrivacyExemptionForCarrier(true,
                DATA_CARRIER_ID);
        verify(mListener).onImsiProtectedOrUserApprovalChanged(DATA_CARRIER_ID, true);
        inOrder.verify(mWifiConfigManager).saveToStore(true);
        assertTrue(mWifiCarrierInfoManager
                .hasUserApprovedImsiPrivacyExemptionForCarrier(DATA_CARRIER_ID));
        mWifiCarrierInfoManager.clearImsiPrivacyExemptionForCarrier(DATA_CARRIER_ID);
        inOrder.verify(mWifiConfigManager).saveToStore(true);
        assertFalse(mWifiCarrierInfoManager
                .hasUserApprovedImsiPrivacyExemptionForCarrier(DATA_CARRIER_ID));
    }

    @Test
    public void testSendImsiProtectionExemptionNotificationWithUserAllowed() {
        // Setup carrier without IMSI privacy protection
        when(mCarrierConfigManager.getConfigForSubId(DATA_SUBID))
                .thenReturn(generateTestCarrierConfig(false));
        ArgumentCaptor<BroadcastReceiver> receiver =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(receiver.capture(), any(IntentFilter.class));

        receiver.getValue().onReceive(mContext,
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));
        assertFalse(mWifiCarrierInfoManager.requiresImsiEncryption(DATA_SUBID));

        mWifiCarrierInfoManager.sendImsiProtectionExemptionNotificationIfRequired(DATA_CARRIER_ID);
        validateImsiProtectionNotification(CARRIER_NAME);
        // Simulate user clicking on allow in the notification.
        sendBroadcastForUserActionOnImsi(NOTIFICATION_USER_ALLOWED_CARRIER_INTENT_ACTION,
                CARRIER_NAME, DATA_CARRIER_ID);
        verify(mWifiNotificationManager).cancel(SystemMessage.NOTE_CARRIER_SUGGESTION_AVAILABLE);
        verify(mWifiMetrics).addUserApprovalCarrierUiReaction(
                WifiCarrierInfoManager.ACTION_USER_ALLOWED_CARRIER, false);
        verify(mWifiConfigManager).saveToStore(true);
        assertTrue(mCarrierInfoDataSource.hasNewDataToSerialize());
        assertTrue(mWifiCarrierInfoManager
                .hasUserApprovedImsiPrivacyExemptionForCarrier(DATA_CARRIER_ID));
        verify(mListener).onImsiProtectedOrUserApprovalChanged(DATA_CARRIER_ID, true);
        verify(mWifiMetrics).addUserApprovalCarrierUiReaction(
                WifiCarrierInfoManager.ACTION_USER_ALLOWED_CARRIER, false);
    }

    @Test
    public void testSendImsiProtectionExemptionNotificationWithUserDisallowed() {
        // Setup carrier without IMSI privacy protection
        when(mCarrierConfigManager.getConfigForSubId(DATA_SUBID))
                .thenReturn(generateTestCarrierConfig(false));
        ArgumentCaptor<BroadcastReceiver> receiver =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(receiver.capture(), any(IntentFilter.class));

        receiver.getValue().onReceive(mContext,
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));
        assertFalse(mWifiCarrierInfoManager.requiresImsiEncryption(DATA_SUBID));

        mWifiCarrierInfoManager.sendImsiProtectionExemptionNotificationIfRequired(DATA_CARRIER_ID);
        validateImsiProtectionNotification(CARRIER_NAME);
        // Simulate user clicking on disallow in the notification.
        sendBroadcastForUserActionOnImsi(NOTIFICATION_USER_DISALLOWED_CARRIER_INTENT_ACTION,
                CARRIER_NAME, DATA_CARRIER_ID);
        verify(mWifiNotificationManager).cancel(SystemMessage.NOTE_CARRIER_SUGGESTION_AVAILABLE);
        verify(mDialogHandle, never()).launchDialog();

        verify(mWifiConfigManager).saveToStore(true);
        assertTrue(mCarrierInfoDataSource.hasNewDataToSerialize());
        assertFalse(mWifiCarrierInfoManager
                .hasUserApprovedImsiPrivacyExemptionForCarrier(DATA_CARRIER_ID));
        verify(mListener, never())
                .onImsiProtectedOrUserApprovalChanged(eq(DATA_CARRIER_ID), anyBoolean());
        verify(mWifiMetrics).addUserApprovalCarrierUiReaction(
                WifiCarrierInfoManager.ACTION_USER_DISALLOWED_CARRIER, false);
    }

    @Test
    public void testSendImsiProtectionExemptionNotificationWithUserDismissal() {
        // Setup carrier without IMSI privacy protection
        when(mCarrierConfigManager.getConfigForSubId(DATA_SUBID))
                .thenReturn(generateTestCarrierConfig(false));
        ArgumentCaptor<BroadcastReceiver> receiver =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(receiver.capture(), any(IntentFilter.class));

        receiver.getValue().onReceive(mContext,
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));
        assertFalse(mWifiCarrierInfoManager.requiresImsiEncryption(DATA_SUBID));

        mWifiCarrierInfoManager.sendImsiProtectionExemptionNotificationIfRequired(DATA_CARRIER_ID);
        validateImsiProtectionNotification(CARRIER_NAME);
        //Simulate user dismissal the notification
        sendBroadcastForUserActionOnImsi(NOTIFICATION_USER_DISMISSED_INTENT_ACTION,
                CARRIER_NAME, DATA_CARRIER_ID);
        verify(mWifiMetrics).addUserApprovalCarrierUiReaction(
                WifiCarrierInfoManager.ACTION_USER_DISMISS, false);
        reset(mWifiNotificationManager);
        // No Notification is active, should send notification again.
        mWifiCarrierInfoManager.sendImsiProtectionExemptionNotificationIfRequired(DATA_CARRIER_ID);
        verifyNoMoreInteractions(mWifiNotificationManager);

        when(mClock.getElapsedSinceBootMillis()).thenReturn(mCurrentTimeMills + 6 * 60 * 1000);
        mWifiCarrierInfoManager.sendImsiProtectionExemptionNotificationIfRequired(DATA_CARRIER_ID);
        validateImsiProtectionNotification(CARRIER_NAME);
        reset(mWifiNotificationManager);

        // As there is notification is active, should not send notification again.
        sendBroadcastForUserActionOnImsi(NOTIFICATION_USER_DISMISSED_INTENT_ACTION,
                CARRIER_NAME, DATA_CARRIER_ID);
        verifyNoMoreInteractions(mWifiNotificationManager);
        verify(mWifiConfigManager, never()).saveToStore(true);
        assertFalse(mCarrierInfoDataSource.hasNewDataToSerialize());
        assertFalse(mWifiCarrierInfoManager
                .hasUserApprovedImsiPrivacyExemptionForCarrier(DATA_CARRIER_ID));
        verify(mListener, never())
                .onImsiProtectedOrUserApprovalChanged(eq(DATA_CARRIER_ID), anyBoolean());
    }

    @Test
    public void testSendImsiProtectionExemptionConfirmationDialogWithUserDisallowed() {
        // Setup carrier without IMSI privacy protection
        when(mCarrierConfigManager.getConfigForSubId(DATA_SUBID))
                .thenReturn(generateTestCarrierConfig(false));
        ArgumentCaptor<BroadcastReceiver> receiver =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(receiver.capture(), any(IntentFilter.class));

        receiver.getValue().onReceive(mContext,
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));
        assertFalse(mWifiCarrierInfoManager.requiresImsiEncryption(DATA_SUBID));

        mWifiCarrierInfoManager.sendImsiProtectionExemptionNotificationIfRequired(DATA_CARRIER_ID);
        validateImsiProtectionNotification(CARRIER_NAME);
        // Simulate user clicking on the notification.
        sendBroadcastForUserActionOnImsi(NOTIFICATION_USER_CLICKED_INTENT_ACTION,
                CARRIER_NAME, DATA_CARRIER_ID);
        verify(mWifiNotificationManager).cancel(SystemMessage.NOTE_CARRIER_SUGGESTION_AVAILABLE);
        validateUserApprovalDialog(CARRIER_NAME);

        // Simulate user clicking on disallow in the dialog.
        ArgumentCaptor<WifiDialogManager.SimpleDialogCallback> dialogCallbackCaptor =
                ArgumentCaptor.forClass(WifiDialogManager.SimpleDialogCallback.class);
        verify(mWifiDialogManager).createSimpleDialog(
                any(), any(), any(), any(), any(), dialogCallbackCaptor.capture(), any());
        dialogCallbackCaptor.getValue().onNegativeButtonClicked();
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendBroadcast(intentCaptor.capture(), any(), any());
        assertEquals(Intent.ACTION_CLOSE_SYSTEM_DIALOGS, intentCaptor.getValue().getAction());
        verify(mWifiConfigManager).saveToStore(true);
        assertTrue(mCarrierInfoDataSource.hasNewDataToSerialize());
        assertFalse(mWifiCarrierInfoManager
                .hasUserApprovedImsiPrivacyExemptionForCarrier(DATA_CARRIER_ID));
        verify(mListener, never())
                .onImsiProtectedOrUserApprovalChanged(eq(DATA_CARRIER_ID), anyBoolean());
        verify(mWifiMetrics).addUserApprovalCarrierUiReaction(
                WifiCarrierInfoManager.ACTION_USER_DISALLOWED_CARRIER, true);
    }

    @Test
    public void testSendImsiProtectionExemptionConfirmationDialogWithUserDismissal() {
        // Setup carrier without IMSI privacy protection
        when(mCarrierConfigManager.getConfigForSubId(DATA_SUBID))
                .thenReturn(generateTestCarrierConfig(false));
        ArgumentCaptor<BroadcastReceiver> receiver =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(receiver.capture(), any(IntentFilter.class));

        receiver.getValue().onReceive(mContext,
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));
        assertFalse(mWifiCarrierInfoManager.requiresImsiEncryption(DATA_SUBID));

        mWifiCarrierInfoManager.sendImsiProtectionExemptionNotificationIfRequired(DATA_CARRIER_ID);
        validateImsiProtectionNotification(CARRIER_NAME);
        // Simulate user clicking on the notification.
        sendBroadcastForUserActionOnImsi(NOTIFICATION_USER_CLICKED_INTENT_ACTION,
                CARRIER_NAME, DATA_CARRIER_ID);
        verify(mWifiNotificationManager).cancel(SystemMessage.NOTE_CARRIER_SUGGESTION_AVAILABLE);
        validateUserApprovalDialog(CARRIER_NAME);

        // Simulate user dismissing the dialog via home/back button.
        ArgumentCaptor<WifiDialogManager.SimpleDialogCallback> dialogCallbackCaptor =
                ArgumentCaptor.forClass(WifiDialogManager.SimpleDialogCallback.class);
        verify(mWifiDialogManager).createSimpleDialog(
                any(), any(), any(), any(), any(), dialogCallbackCaptor.capture(), any());
        dialogCallbackCaptor.getValue().onCancelled();
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendBroadcast(intentCaptor.capture(), any(), any());
        assertEquals(Intent.ACTION_CLOSE_SYSTEM_DIALOGS, intentCaptor.getValue().getAction());

        // As user dismissed the notification, there will be a certain time to delay the next
        // notification
        mWifiCarrierInfoManager.sendImsiProtectionExemptionNotificationIfRequired(DATA_CARRIER_ID);
        verifyNoMoreInteractions(mWifiNotificationManager);

        when(mClock.getElapsedSinceBootMillis()).thenReturn(mCurrentTimeMills + 6 * 60 * 1000);
        validateImsiProtectionNotification(CARRIER_NAME);

        verify(mWifiConfigManager, never()).saveToStore(true);
        assertFalse(mCarrierInfoDataSource.hasNewDataToSerialize());
        assertFalse(mWifiCarrierInfoManager
                .hasUserApprovedImsiPrivacyExemptionForCarrier(DATA_CARRIER_ID));
        verify(mListener, never())
                .onImsiProtectedOrUserApprovalChanged(eq(DATA_CARRIER_ID), anyBoolean());
        verify(mWifiMetrics).addUserApprovalCarrierUiReaction(
                WifiCarrierInfoManager.ACTION_USER_DISMISS, true);
    }

    @Test
    public void testSendImsiProtectionExemptionDialogWithUserAllowed() {
        // Setup carrier without IMSI privacy protection
        when(mCarrierConfigManager.getConfigForSubId(DATA_SUBID))
                .thenReturn(generateTestCarrierConfig(false));
        ArgumentCaptor<BroadcastReceiver> receiver =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(receiver.capture(), any(IntentFilter.class));

        receiver.getValue().onReceive(mContext,
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));
        assertFalse(mWifiCarrierInfoManager.requiresImsiEncryption(DATA_SUBID));

        mWifiCarrierInfoManager.sendImsiProtectionExemptionNotificationIfRequired(DATA_CARRIER_ID);
        validateImsiProtectionNotification(CARRIER_NAME);
        // Simulate user clicking on the notification.
        sendBroadcastForUserActionOnImsi(NOTIFICATION_USER_CLICKED_INTENT_ACTION,
                CARRIER_NAME, DATA_CARRIER_ID);
        verify(mWifiNotificationManager).cancel(SystemMessage.NOTE_CARRIER_SUGGESTION_AVAILABLE);
        validateUserApprovalDialog(CARRIER_NAME);

        // Simulate user clicking on allow in the dialog.
        ArgumentCaptor<WifiDialogManager.SimpleDialogCallback> dialogCallbackCaptor =
                ArgumentCaptor.forClass(WifiDialogManager.SimpleDialogCallback.class);
        verify(mWifiDialogManager).createSimpleDialog(
                any(), any(), any(), any(), any(), dialogCallbackCaptor.capture(), any());
        dialogCallbackCaptor.getValue().onPositiveButtonClicked();
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendBroadcast(intentCaptor.capture(), any(), any());
        assertEquals(Intent.ACTION_CLOSE_SYSTEM_DIALOGS, intentCaptor.getValue().getAction());
        verify(mWifiConfigManager).saveToStore(true);
        assertTrue(mCarrierInfoDataSource.hasNewDataToSerialize());
        verify(mListener).onImsiProtectedOrUserApprovalChanged(DATA_CARRIER_ID, true);
        verify(mWifiMetrics).addUserApprovalCarrierUiReaction(
                WifiCarrierInfoManager.ACTION_USER_ALLOWED_CARRIER, true);
    }

    @Test
    public void testUserDataStoreIsNotLoadedNotificationWillNotBeSent() {
        // reset data source to unloaded state.
        mImsiDataSource.reset();
        // Setup carrier without IMSI privacy protection
        when(mCarrierConfigManager.getConfigForSubId(DATA_SUBID))
                .thenReturn(generateTestCarrierConfig(false));
        ArgumentCaptor<BroadcastReceiver> receiver =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(receiver.capture(), any(IntentFilter.class));

        receiver.getValue().onReceive(mContext,
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));
        assertFalse(mWifiCarrierInfoManager.requiresImsiEncryption(DATA_SUBID));

        mWifiCarrierInfoManager.sendImsiProtectionExemptionNotificationIfRequired(DATA_CARRIER_ID);
        verifyNoMoreInteractions(mWifiNotificationManager);

        // Loaded user data store, notification should be sent
        mImsiDataSource.fromDeserialized(new HashMap<>());
        mWifiCarrierInfoManager.sendImsiProtectionExemptionNotificationIfRequired(DATA_CARRIER_ID);
        validateImsiProtectionNotification(CARRIER_NAME);
    }

    @Test
    public void testCarrierConfigNotAvailableNotificationWillNotBeSent() {
        // Setup carrier without IMSI privacy protection
        when(mCarrierConfigManager.getConfigForSubId(DATA_SUBID))
                .thenReturn(generateTestCarrierConfig(false));
        ArgumentCaptor<BroadcastReceiver> receiver =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(receiver.capture(), any(IntentFilter.class));

        receiver.getValue().onReceive(mContext,
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));
        assertFalse(mWifiCarrierInfoManager.requiresImsiEncryption(DATA_SUBID));
        // Carrier config for Non data carrier is not available, no notification will send.
        mWifiCarrierInfoManager
                .sendImsiProtectionExemptionNotificationIfRequired(NON_DATA_CARRIER_ID);
        verifyNoMoreInteractions(mWifiNotificationManager);

        mWifiCarrierInfoManager.sendImsiProtectionExemptionNotificationIfRequired(DATA_CARRIER_ID);
        validateImsiProtectionNotification(CARRIER_NAME);
    }

    @Test
    public void testImsiProtectionExemptionNotificationNotSentWhenCarrierNameIsInvalid() {
        when(mCarrierConfigManager.getConfigForSubId(DATA_SUBID))
                .thenReturn(generateTestCarrierConfig(false));
        ArgumentCaptor<BroadcastReceiver> receiver =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(receiver.capture(), any(IntentFilter.class));

        receiver.getValue().onReceive(mContext,
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));
        assertFalse(mWifiCarrierInfoManager.requiresImsiEncryption(DATA_SUBID));
        when(mDataTelephonyManager.getSimCarrierIdName()).thenReturn(null);
        mWifiCarrierInfoManager.sendImsiProtectionExemptionNotificationIfRequired(DATA_CARRIER_ID);
        verify(mWifiNotificationManager, never()).notify(
                eq(SystemMessage.NOTE_CARRIER_SUGGESTION_AVAILABLE),
                eq(mNotification));

    }

    @Test
    public void testImsiProtectionExemptionNotificationNotSentWhenOobPseudonymEnabled() {
        // NON_DATA_SUBID enabled the OOB pseudonym.
        when(mCarrierConfigManager.getConfigForSubId(NON_DATA_SUBID))
                .thenReturn(generateTestCarrierConfig(false));
        mWifiCarrierInfoManager.sendImsiProtectionExemptionNotificationIfRequired(
                NON_DATA_CARRIER_ID);
        verify(mWifiNotificationManager, never()).notify(
                eq(SystemMessage.NOTE_CARRIER_SUGGESTION_AVAILABLE),
                eq(mNotification));
    }

    @Test
    public void verifySubIdAndCarrierIdMatching() {
        assertTrue(mWifiCarrierInfoManager.isSubIdMatchingCarrierId(
                SubscriptionManager.INVALID_SUBSCRIPTION_ID, DATA_CARRIER_ID));
        assertFalse(mWifiCarrierInfoManager.isSubIdMatchingCarrierId(
                DATA_SUBID, TelephonyManager.UNKNOWN_CARRIER_ID));

        assertTrue(mWifiCarrierInfoManager.isSubIdMatchingCarrierId(
                DATA_SUBID, DATA_CARRIER_ID));
        assertFalse(mWifiCarrierInfoManager.isSubIdMatchingCarrierId(
                NON_DATA_SUBID, DATA_CARRIER_ID));
    }

    @Test
    public void testSetAndGetUnmergedCarrierNetworkOffload() {
        assertTrue(mWifiCarrierInfoManager.isCarrierNetworkOffloadEnabled(DATA_SUBID, false));
        mWifiCarrierInfoManager.setCarrierNetworkOffloadEnabled(DATA_SUBID, false, false);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).saveToStore(true);
        assertFalse(mWifiCarrierInfoManager.isCarrierNetworkOffloadEnabled(DATA_SUBID, false));
    }

    @Test
    public void testSetAndGetMergedCarrierNetworkOffload() {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mDataTelephonyManager.isDataEnabled()).thenReturn(true);
        ArgumentCaptor<WifiCarrierInfoManager.UserDataEnabledChangedListener> listenerCaptor =
                ArgumentCaptor.forClass(
                        WifiCarrierInfoManager.UserDataEnabledChangedListener.class);
        // Check default value and verify listen is registered.
        assertTrue(mWifiCarrierInfoManager.isCarrierNetworkOffloadEnabled(DATA_SUBID, true));
        verify(mDataTelephonyManager).registerTelephonyCallback(any(), listenerCaptor.capture());

        // Verify result will change with state changes
        listenerCaptor.getValue().onDataEnabledChanged(false, DATA_ENABLED_REASON_THERMAL);
        assertFalse(mWifiCarrierInfoManager.isCarrierNetworkOffloadEnabled(DATA_SUBID, true));

        listenerCaptor.getValue().onDataEnabledChanged(true, DATA_ENABLED_REASON_USER);
        mWifiCarrierInfoManager.setCarrierNetworkOffloadEnabled(DATA_SUBID, true, false);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).saveToStore(true);
        assertFalse(mWifiCarrierInfoManager.isCarrierNetworkOffloadEnabled(DATA_SUBID, true));

    }

    private void validateImsiProtectionNotification(String carrierName) {
        verify(mWifiNotificationManager, atLeastOnce()).notify(
                eq(SystemMessage.NOTE_CARRIER_SUGGESTION_AVAILABLE),
                eq(mNotification));
        ArgumentCaptor<CharSequence> contentCaptor =
                ArgumentCaptor.forClass(CharSequence.class);
        verify(mNotificationBuilder, atLeastOnce()).setContentTitle(contentCaptor.capture());
        CharSequence content = contentCaptor.getValue();
        assertNotNull(content);
        assertTrue(content.toString().contains(carrierName));
    }

    private void validateUserApprovalDialog(String... anyOfExpectedAppNames) {
        verify(mDialogHandle, atLeastOnce()).launchDialog();
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(mWifiDialogManager, atLeastOnce()).createSimpleDialog(
                any(), messageCaptor.capture(), any(), any(), any(), any(), any());
        String message = messageCaptor.getValue();
        assertNotNull(message);

        boolean foundMatch = false;
        for (int i = 0; i < anyOfExpectedAppNames.length; i++) {
            foundMatch = message.contains(anyOfExpectedAppNames[i]);
            if (foundMatch) break;
        }
        assertTrue(foundMatch);
    }

    private void sendBroadcastForUserActionOnImsi(String action, String carrierName,
            int carrierId) {
        Intent intent = new Intent()
                .setAction(action)
                .putExtra(WifiCarrierInfoManager.EXTRA_CARRIER_NAME, carrierName)
                .putExtra(WifiCarrierInfoManager.EXTRA_CARRIER_ID, carrierId);
        assertNotNull(mBroadcastReceiverCaptor.getValue());
        mBroadcastReceiverCaptor.getValue().onReceive(mContext, intent);
    }

    @Test
    public void testSendRefreshUserProvisioningOnUnlockedUserSwitching() {
        PackageManager mockPackageManager = mock(PackageManager.class);
        when(mContext.getPackageManager()).thenReturn(mockPackageManager);
        PackageInfo pi = new PackageInfo();
        pi.packageName = "com.example.app";
        List<PackageInfo> pis = List.of(pi);
        when(mockPackageManager.getPackagesHoldingPermissions(
                eq(new String[] {android.Manifest.permission.NETWORK_CARRIER_PROVISIONING}),
                anyInt())).thenReturn(pis);

        mWifiCarrierInfoManager.onUnlockedUserSwitching(1);

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendBroadcastAsUser(
                intentCaptor.capture(),
                eq(UserHandle.CURRENT),
                eq(android.Manifest.permission.NETWORK_CARRIER_PROVISIONING));
        Intent intent = intentCaptor.getValue();
        assertEquals(intent.getAction(), WifiManager.ACTION_REFRESH_USER_PROVISIONING);
    }

    /**
     * Verify that shouldDisableMacRandomization returns true if the SSID in the input config
     * matches with the SSID list in CarrierConfigManager.
     */
    @Test
    public void testShouldDisableMacRandomization() {
        // Create 2 WifiConfigurations and mock CarrierConfigManager to include the SSID
        // of the first one in the MAC randomization disabled list.
        WifiConfiguration config1 = WifiConfigurationTestUtil.createOpenNetwork();
        WifiConfiguration config2 = WifiConfigurationTestUtil.createOpenNetwork();
        config1.carrierId = DATA_CARRIER_ID;
        config1.subscriptionId = DATA_SUBID;
        PersistableBundle bundle = new PersistableBundle();
        PersistableBundle wifiBundle = new PersistableBundle();
        // Add the first SSID and some garbage SSID to the exception list.
        wifiBundle.putStringArray(
                CarrierConfigManager.Wifi.KEY_SUGGESTION_SSID_LIST_WITH_MAC_RANDOMIZATION_DISABLED,
                new String[]{
                        WifiInfo.sanitizeSsid(config1.SSID),
                        WifiInfo.sanitizeSsid(config2.SSID) + "_GARBAGE"});
        wifiBundle.putBoolean(KEY_CARRIER_CONFIG_APPLIED_BOOL, true);
        bundle.putAll(wifiBundle);
        when(mCarrierConfigManager.getConfigForSubId(anyInt())).thenReturn(bundle);

        if (SdkLevel.isAtLeastS()) {
            // Verify MAC randomization is disable for config1, but not disabled for config2
            assertFalse(mWifiCarrierInfoManager.shouldDisableMacRandomization(config2.SSID,
                    config2.carrierId, config2.subscriptionId));
            assertTrue(mWifiCarrierInfoManager.shouldDisableMacRandomization(config1.SSID,
                    config1.carrierId, config1.subscriptionId));

            // Verify getConfigForSubId is only called once since the CarrierConfig gets cached.
            verify(mCarrierConfigManager).getConfigForSubId(anyInt());
        } else {
            // Verify MAC randomization is not disabled for either configuration.
            assertFalse(mWifiCarrierInfoManager.shouldDisableMacRandomization(config2.SSID,
                    config2.carrierId, config2.subscriptionId));
            assertFalse(mWifiCarrierInfoManager.shouldDisableMacRandomization(config1.SSID,
                    config1.carrierId, config1.subscriptionId));
        }
    }

    /**
     * Verify that shouldDisableMacRandomization returns false if the carrierId is not set.
     */
    @Test
    public void testOnlyDisableMacRandomizationOnCarrierNetworks() {
        // Create 2 WifiConfiguration, but only set the carrierId for the first config.
        WifiConfiguration config1 = WifiConfigurationTestUtil.createOpenNetwork();
        WifiConfiguration config2 = WifiConfigurationTestUtil.createOpenNetwork();
        config1.carrierId = DATA_CARRIER_ID;
        config1.subscriptionId = DATA_SUBID;
        PersistableBundle bundle = new PersistableBundle();
        PersistableBundle wifiBundle = new PersistableBundle();
        // add both the first SSID and second SSID to the exception list.
        wifiBundle.putStringArray(
                CarrierConfigManager.Wifi.KEY_SUGGESTION_SSID_LIST_WITH_MAC_RANDOMIZATION_DISABLED,
                new String[]{
                        WifiInfo.sanitizeSsid(config1.SSID),
                        WifiInfo.sanitizeSsid(config2.SSID)});
        wifiBundle.putBoolean(KEY_CARRIER_CONFIG_APPLIED_BOOL, true);
        bundle.putAll(wifiBundle);
        when(mCarrierConfigManager.getConfigForSubId(anyInt())).thenReturn(bundle);

        if (SdkLevel.isAtLeastS()) {
            // Verify MAC randomization is disable for config1, but not disabled for config2
            assertTrue(mWifiCarrierInfoManager.shouldDisableMacRandomization(config1.SSID,
                    config1.carrierId, config1.subscriptionId));
            assertFalse(mWifiCarrierInfoManager.shouldDisableMacRandomization(config2.SSID,
                    config2.carrierId, config2.subscriptionId));
            // Verify getConfigForSubId is only called once since the CarrierConfig gets cached.
            verify(mCarrierConfigManager).getConfigForSubId(anyInt());
        } else {
            // Verify MAC randomization is not disabled for either configuration.
            assertFalse(mWifiCarrierInfoManager.shouldDisableMacRandomization(config1.SSID,
                    config1.carrierId, config1.subscriptionId));
            assertFalse(mWifiCarrierInfoManager.shouldDisableMacRandomization(config2.SSID,
                    config2.carrierId, config2.subscriptionId));
        }
    }

    @Test
    public void testAllowCarrierWifiForCarrier() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(KEY_CARRIER_CONFIG_APPLIED_BOOL, true);
        String key = CarrierConfigManager.KEY_CARRIER_PROVISIONS_WIFI_MERGED_NETWORKS_BOOL;
        int subId = DATA_SUBID;
        when(mCarrierConfigManager.getConfigForSubId(anyInt())).thenReturn(bundle);

        if (SdkLevel.isAtLeastS()) {
            // not allowed: false
            bundle.putBoolean(key, false);
            assertFalse(
                    mWifiCarrierInfoManager.areMergedCarrierWifiNetworksAllowed(subId));

            // allowed: true
            bundle.putBoolean(key, true);
            assertTrue(
                    mWifiCarrierInfoManager.areMergedCarrierWifiNetworksAllowed(subId));

            // no key
            bundle.clear();
            assertFalse(
                    mWifiCarrierInfoManager.areMergedCarrierWifiNetworksAllowed(subId));
        } else {
            assertFalse(
                    mWifiCarrierInfoManager.areMergedCarrierWifiNetworksAllowed(subId));
        }
    }

    @Test
    public void testResetNotification() {
        mWifiCarrierInfoManager.resetNotification();
        verify(mWifiNotificationManager).cancel(SystemMessage.NOTE_CARRIER_SUGGESTION_AVAILABLE);
    }

    @Test
    public void testClear() {
        when(mDataTelephonyManager.isDataEnabled()).thenReturn(true);
        mWifiCarrierInfoManager.setHasUserApprovedImsiPrivacyExemptionForCarrier(
                true, DATA_CARRIER_ID);
        assertTrue(mWifiCarrierInfoManager.isCarrierNetworkOffloadEnabled(DATA_SUBID, true));
        assertTrue(mWifiCarrierInfoManager.isCarrierNetworkOffloadEnabled(NON_DATA_SUBID, false));
        mWifiCarrierInfoManager.setCarrierNetworkOffloadEnabled(DATA_SUBID, true, false);
        mWifiCarrierInfoManager.setCarrierNetworkOffloadEnabled(NON_DATA_SUBID, false, false);
        // Verify values.
        assertTrue(mWifiCarrierInfoManager
                .hasUserApprovedImsiPrivacyExemptionForCarrier(DATA_CARRIER_ID));
        assertFalse(mWifiCarrierInfoManager.isCarrierNetworkOffloadEnabled(DATA_SUBID, true));
        assertFalse(mWifiCarrierInfoManager.isCarrierNetworkOffloadEnabled(NON_DATA_SUBID, false));
        // Now clear everything.
        mWifiCarrierInfoManager.clear();

        verify(mWifiNotificationManager).cancel(SystemMessage.NOTE_CARRIER_SUGGESTION_AVAILABLE);
        if (SdkLevel.isAtLeastS()) {
            verify(mDataTelephonyManager).unregisterTelephonyCallback(any());
        }

        // Verify restore to default value.
        assertFalse(mWifiCarrierInfoManager
                .hasUserApprovedImsiPrivacyExemptionForCarrier(DATA_CARRIER_ID));
        assertTrue(mWifiCarrierInfoManager.isCarrierNetworkOffloadEnabled(DATA_SUBID, true));
        assertTrue(mWifiCarrierInfoManager.isCarrierNetworkOffloadEnabled(NON_DATA_SUBID, false));

        // Verify active subscription info is not clear
        assertEquals(DATA_SUBID, mWifiCarrierInfoManager.getMatchingSubId(DATA_CARRIER_ID));
    }

    @Test
    public void testOnCarrierOffloadDisabledListener() {
        assumeTrue(SdkLevel.isAtLeastS());
        mWifiCarrierInfoManager.addOnCarrierOffloadDisabledListener(
                mOnCarrierOffloadDisabledListener);
        mWifiCarrierInfoManager.isCarrierNetworkOffloadEnabled(DATA_SUBID, true);
        ArgumentCaptor<WifiCarrierInfoManager.UserDataEnabledChangedListener> captor =
                ArgumentCaptor.forClass(WifiCarrierInfoManager.UserDataEnabledChangedListener
                        .class);
        verify(mDataTelephonyManager).registerTelephonyCallback(any(), captor.capture());

        mWifiCarrierInfoManager.setCarrierNetworkOffloadEnabled(DATA_SUBID, true, false);
        mLooper.dispatchAll();
        verify(mOnCarrierOffloadDisabledListener).onCarrierOffloadDisabled(DATA_SUBID, true);

        captor.getValue().onDataEnabledChanged(false, DATA_ENABLED_REASON_CARRIER);
        verify(mOnCarrierOffloadDisabledListener, times(2))
                .onCarrierOffloadDisabled(DATA_SUBID, true);
    }

    @Test
    public void testGetActiveSubsctionIdInGroup() {
        assertEquals(DATA_SUBID, mWifiCarrierInfoManager
                .getActiveSubscriptionIdInGroup(GROUP_UUID));
    }

    @Test
    public void testCarrierPrivilegedListenerChange() {
        assumeTrue(SdkLevel.isAtLeastT());
        TelephonyManager.CarrierPrivilegesCallback carrierPrivilegesCallback;
        ArgumentCaptor<TelephonyManager.CarrierPrivilegesCallback> callbackArgumentCaptor =
                ArgumentCaptor.forClass(TelephonyManager.CarrierPrivilegesCallback.class);
        verify(mTelephonyManager, times(2))
                .registerCarrierPrivilegesCallback(anyInt(), any(),
                        callbackArgumentCaptor.capture());
        carrierPrivilegesCallback = callbackArgumentCaptor.getValue();
        carrierPrivilegesCallback.onCarrierPrivilegesChanged(Collections.emptySet(),
                Collections.emptySet());
        verify(mWifiNetworkSuggestionsManager).updateCarrierPrivilegedApps(any());
    }

    @Test
    public void testGetMatchingSubId() {
        ArgumentCaptor<BroadcastReceiver> receiver =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(receiver.capture(), any(IntentFilter.class));

        // Make two subscription from same carrier
        when(mDataSubscriptionInfo.getCarrierId()).thenReturn(DATA_CARRIER_ID);
        when(mDataSubscriptionInfo.getSubscriptionId()).thenReturn(DATA_SUBID);
        when(mNonDataSubscriptionInfo.getCarrierId()).thenReturn(DATA_CARRIER_ID);
        when(mNonDataSubscriptionInfo.getSubscriptionId()).thenReturn(NON_DATA_SUBID);
        mListenerArgumentCaptor.getValue().onSubscriptionsChanged();

        // Data sim should be selected
        assertEquals(DATA_SUBID, mWifiCarrierInfoManager.getMatchingSubId(DATA_CARRIER_ID));

        // Disable data sim.
        when(mCarrierConfigManager.getConfigForSubId(DATA_SUBID)).thenReturn(null);
        receiver.getValue().onReceive(mContext,
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));
        mLooper.dispatchAll();
        // Non-data sim should be selected
        assertEquals(NON_DATA_SUBID, mWifiCarrierInfoManager.getMatchingSubId(DATA_CARRIER_ID));
    }

    @Test
    public void testIsOobPseudonymFeatureEnabled_phFlagDisabled() {
        when(mDeviceConfigFacade.isOobPseudonymEnabled()).thenReturn(false);

        assertFalse(mWifiCarrierInfoManager.isOobPseudonymFeatureEnabled(123));
    }

    @Test
    public void testIsOobPseudonymFeatureEnabled_resourceOverrideAsTrue() {
        when(mDeviceConfigFacade.isOobPseudonymEnabled()).thenReturn(true);
        when(mWifiStringResourceWrapper.getBoolean(
                eq(WifiCarrierInfoManager.CONFIG_WIFI_OOB_PSEUDONYM_ENABLED), anyBoolean()))
                .thenReturn(true);

        assertTrue(mWifiCarrierInfoManager.isOobPseudonymFeatureEnabled(1));
    }

    @Test
    public void testActiveSubsChangeUpdateWifiNetworkFactory() {
        SubscriptionInfo subInfo1 = mock(SubscriptionInfo.class);
        when(subInfo1.getSubscriptionId()).thenReturn(DATA_SUBID);
        SubscriptionInfo subInfo2 = mock(SubscriptionInfo.class);
        when(subInfo2.getSubscriptionId()).thenReturn(NON_DATA_SUBID);
        when(mSubscriptionManager.getCompleteActiveSubscriptionInfoList())
                .thenReturn(Arrays.asList(subInfo1, subInfo2));
        mListenerArgumentCaptor.getValue().onSubscriptionsChanged();
        mLooper.dispatchAll();
        ArgumentCaptor<Set<Integer>> restrictedWifiCaptor = ArgumentCaptor.forClass(Set.class);
        ArgumentCaptor<Set<Integer>> untrustedWifiCaptor = ArgumentCaptor.forClass(Set.class);
        ArgumentCaptor<Set<Integer>> wifiCaptor = ArgumentCaptor.forClass(Set.class);
        verify(mRestrictedWifiNetworkFactory, times(2)).updateSubIdsInCapabilitiesFilter(
                restrictedWifiCaptor.capture());
        assertThat(restrictedWifiCaptor.getValue()).containsExactly(DATA_SUBID, NON_DATA_SUBID);
        verify(mUntrustedWifiNetworkFactory, times(2)).updateSubIdsInCapabilitiesFilter(
                untrustedWifiCaptor.capture());
        assertThat(restrictedWifiCaptor.getValue()).containsExactly(DATA_SUBID, NON_DATA_SUBID);
        verify(mWifiNetworkFactory, times(2)).updateSubIdsInCapabilitiesFilter(
                wifiCaptor.capture());
        assertThat(wifiCaptor.getValue()).containsExactly(DATA_SUBID, NON_DATA_SUBID);
    }
}
