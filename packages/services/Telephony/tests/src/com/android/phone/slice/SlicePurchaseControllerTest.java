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

package com.android.phone.slice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.annotation.NonNull;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.data.NetworkSliceInfo;
import android.telephony.data.NetworkSlicingConfig;
import android.telephony.data.RouteSelectionDescriptor;
import android.telephony.data.TrafficDescriptor;
import android.telephony.data.UrspRule;
import android.testing.TestableLooper;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.TelephonyTestBase;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.data.DataSettingsManager;
import com.android.internal.telephony.flags.FeatureFlags;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class SlicePurchaseControllerTest extends TelephonyTestBase {
    private static final String CARRIER = "Some Carrier";
    private static final String DAILY_NOTIFICATION_COUNT_KEY = "daily_notification_count0";
    private static final String MONTHLY_NOTIFICATION_COUNT_KEY = "monthly_notification_count0";
    private static final int YEAR = 2000;
    private static final int MONTH = 6;
    private static final int DATE = 1;
    private static final int PHONE_ID = 0;
    private static final int DAILY_NOTIFICATION_MAX = 3;
    private static final int MONTHLY_NOTIFICATION_MAX = 5;
    private static final long NOTIFICATION_TIMEOUT = 1000;
    private static final long PURCHASE_CONDITION_TIMEOUT = 2000;
    private static final long NETWORK_SETUP_TIMEOUT = 3000;
    private static final long THROTTLE_TIMEOUT = 4000;

    @Mock Phone mPhone;
    @Mock FeatureFlags mFeatureFlags;
    @Mock CarrierConfigManager mCarrierConfigManager;
    @Mock CommandsInterface mCommandsInterface;
    @Mock ServiceState mServiceState;
    @Mock DataSettingsManager mDataSettingsManager;
    @Mock PremiumNetworkEntitlementApi mPremiumNetworkEntitlementApi;
    @Mock SharedPreferences mSharedPreferences;
    @Mock SharedPreferences.Editor mEditor;

    private SlicePurchaseController mSlicePurchaseController;
    private PersistableBundle mBundle;
    private PremiumNetworkEntitlementResponse mEntitlementResponse;
    private Handler mHandler;
    private TestableLooper mTestableLooper;
    @TelephonyManager.PurchasePremiumCapabilityResult private int mResult;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        HandlerThread handlerThread = new HandlerThread("SlicePurchaseControllerTest");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                AsyncResult ar = (AsyncResult) msg.obj;
                mResult = (int) ar.result;
            }
        };
        mTestableLooper = new TestableLooper(mHandler.getLooper());

        doReturn(PHONE_ID).when(mPhone).getPhoneId();
        doReturn(mContext).when(mPhone).getContext();
        doReturn(mServiceState).when(mPhone).getServiceState();
        doReturn(mDataSettingsManager).when(mPhone).getDataSettingsManager();
        mPhone.mCi = mCommandsInterface;

        doReturn(mCarrierConfigManager).when(mContext)
                .getSystemService(Context.CARRIER_CONFIG_SERVICE);
        mBundle = new PersistableBundle();
        mBundle.putInt(
                CarrierConfigManager.KEY_PREMIUM_CAPABILITY_MAXIMUM_DAILY_NOTIFICATION_COUNT_INT,
                DAILY_NOTIFICATION_MAX);
        mBundle.putInt(
                CarrierConfigManager.KEY_PREMIUM_CAPABILITY_MAXIMUM_MONTHLY_NOTIFICATION_COUNT_INT,
                MONTHLY_NOTIFICATION_MAX);
        doReturn(mBundle).when(mCarrierConfigManager).getConfigForSubId(anyInt());

        doReturn(mSharedPreferences).when(mContext).getSharedPreferences(anyString(), anyInt());
        doReturn(mEditor).when(mSharedPreferences).edit();
        doAnswer(invocation -> {
            doReturn(invocation.getArgument(1)).when(mSharedPreferences)
                    .getInt(eq(invocation.getArgument(0)), anyInt());
            return null;
        }).when(mEditor).putInt(anyString(), anyInt());
        doAnswer(invocation -> {
            doReturn(invocation.getArgument(1)).when(mSharedPreferences)
                    .getString(eq(invocation.getArgument(0)), anyString());
            return null;
        }).when(mEditor).putString(anyString(), anyString());

        // create a spy to mock final PendingIntent methods
        SlicePurchaseController slicePurchaseController =
                new SlicePurchaseController(mPhone, mFeatureFlags, mHandler.getLooper());
        mSlicePurchaseController = spy(slicePurchaseController);
        doReturn(null).when(mSlicePurchaseController).createPendingIntent(
                anyString(), anyInt(), anyBoolean());
        doReturn(CARRIER).when(mSlicePurchaseController).getSimOperator();
        replaceInstance(SlicePurchaseController.class, "sInstances", mSlicePurchaseController,
                Map.of(PHONE_ID, mSlicePurchaseController));
        replaceInstance(SlicePurchaseController.class, "mIsSlicingUpsellEnabled",
                mSlicePurchaseController, true);
        mEntitlementResponse = new PremiumNetworkEntitlementResponse();
        doReturn(mPremiumNetworkEntitlementApi).when(mSlicePurchaseController)
                .getPremiumNetworkEntitlementApi();
        doReturn(mEntitlementResponse).when(mPremiumNetworkEntitlementApi)
                .checkEntitlementStatus(anyInt());
    }

    @Test
    public void testCreatePendingIntent() {
        doCallRealMethod().when(mSlicePurchaseController).createPendingIntent(
                anyString(), anyInt(), anyBoolean());
        try {
            mSlicePurchaseController.createPendingIntent(
                    "com.android.phone.slice.action.SLICE_PURCHASE_APP_RESPONSE_CANCELED",
                    TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY,
                    true);
        } catch (Exception expected) {
            return;
        }
        fail("Expected createPendingIntent to throw an exception");
    }

    @Test
    public void testIsPremiumCapabilityAvailableForPurchase() {
        assertFalse(mSlicePurchaseController.isPremiumCapabilityAvailableForPurchase(
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY));

        // all conditions met
        doReturn((int) TelephonyManager.NETWORK_TYPE_BITMASK_NR).when(mPhone)
                .getCachedAllowedNetworkTypesBitmask();
        mBundle.putIntArray(CarrierConfigManager.KEY_SUPPORTED_PREMIUM_CAPABILITIES_INT_ARRAY,
                new int[]{TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY});
        mBundle.putString(CarrierConfigManager.KEY_PREMIUM_CAPABILITY_PURCHASE_URL_STRING,
                SlicePurchaseController.SLICE_PURCHASE_TEST_FILE);
        doReturn(SubscriptionManager.getDefaultDataSubscriptionId()).when(mPhone).getSubId();

        // retry to verify available
        assertTrue(mSlicePurchaseController.isPremiumCapabilityAvailableForPurchase(
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY));
    }

    @Test
    public void testGetPurchaseURL() {
        mEntitlementResponse.mServiceFlowURL = SlicePurchaseController.SLICE_PURCHASE_TEST_FILE;
        String purchaseUrl = mSlicePurchaseController.getPurchaseUrl(mEntitlementResponse);
        assertEquals(purchaseUrl, SlicePurchaseController.SLICE_PURCHASE_TEST_FILE);

        mEntitlementResponse.mServiceFlowURL = null;
        mBundle.putString(CarrierConfigManager.KEY_PREMIUM_CAPABILITY_PURCHASE_URL_STRING,
                SlicePurchaseController.SLICE_PURCHASE_TEST_FILE);
        purchaseUrl = mSlicePurchaseController.getPurchaseUrl(mEntitlementResponse);
        assertEquals(purchaseUrl, SlicePurchaseController.SLICE_PURCHASE_TEST_FILE);

        String[] invalidUrls = new String[] {
                null,
                "",
                "www.google.com",
                "htt://www.google.com",
                "http//www.google.com",
                "http:/www.google.com",
                "file:///android_asset/",
                "file:///android_asset/slice_store_test.html"
        };
        for (String url : invalidUrls) {
            mBundle.putString(CarrierConfigManager.KEY_PREMIUM_CAPABILITY_PURCHASE_URL_STRING, url);
            assertEquals("", mSlicePurchaseController.getPurchaseUrl(mEntitlementResponse));
        }
    }

    @Test
    public void testUpdateNotificationCounts() {
        mSlicePurchaseController.setLocalDate(LocalDate.of(YEAR, MONTH, DATE));
        mSlicePurchaseController.updateNotificationCounts();

        // change only date, month and year remain the same
        Mockito.clearInvocations(mEditor);
        mSlicePurchaseController.setLocalDate(LocalDate.of(YEAR, MONTH, DATE + 1));
        mSlicePurchaseController.updateNotificationCounts();
        verify(mEditor).putInt(eq(DAILY_NOTIFICATION_COUNT_KEY), eq(0));
        verify(mEditor, never()).putInt(eq(MONTHLY_NOTIFICATION_COUNT_KEY), eq(0));

        // change only month, date and year remain the same
        Mockito.clearInvocations(mEditor);
        mSlicePurchaseController.setLocalDate(LocalDate.of(YEAR, MONTH + 1, DATE + 1));
        mSlicePurchaseController.updateNotificationCounts();
        verify(mEditor).putInt(eq(DAILY_NOTIFICATION_COUNT_KEY), eq(0));
        verify(mEditor).putInt(eq(MONTHLY_NOTIFICATION_COUNT_KEY), eq(0));

        // change only year, date and month remain the same
        Mockito.clearInvocations(mEditor);
        mSlicePurchaseController.setLocalDate(LocalDate.of(YEAR + 1, MONTH + 1, DATE + 1));
        mSlicePurchaseController.updateNotificationCounts();
        verify(mEditor).putInt(eq(DAILY_NOTIFICATION_COUNT_KEY), eq(0));
        verify(mEditor).putInt(eq(MONTHLY_NOTIFICATION_COUNT_KEY), eq(0));

        // change only month and year, date remains the same
        Mockito.clearInvocations(mEditor);
        mSlicePurchaseController.setLocalDate(LocalDate.of(YEAR + 2, MONTH + 2, DATE + 1));
        mSlicePurchaseController.updateNotificationCounts();
        verify(mEditor).putInt(eq(DAILY_NOTIFICATION_COUNT_KEY), eq(0));
        verify(mEditor).putInt(eq(MONTHLY_NOTIFICATION_COUNT_KEY), eq(0));

        // change only date and year, month remains the same
        Mockito.clearInvocations(mEditor);
        mSlicePurchaseController.setLocalDate(LocalDate.of(YEAR + 3, MONTH + 2, DATE + 2));
        mSlicePurchaseController.updateNotificationCounts();
        verify(mEditor).putInt(eq(DAILY_NOTIFICATION_COUNT_KEY), eq(0));
        verify(mEditor).putInt(eq(MONTHLY_NOTIFICATION_COUNT_KEY), eq(0));

        // change only date and month, year remains the same
        Mockito.clearInvocations(mEditor);
        mSlicePurchaseController.setLocalDate(LocalDate.of(YEAR + 3, MONTH + 3, DATE + 3));
        mSlicePurchaseController.updateNotificationCounts();
        verify(mEditor).putInt(eq(DAILY_NOTIFICATION_COUNT_KEY), eq(0));
        verify(mEditor).putInt(eq(MONTHLY_NOTIFICATION_COUNT_KEY), eq(0));
    }

    @Test
    public void testPurchasePremiumCapabilityResultFeatureNotSupported() {
        mSlicePurchaseController.purchasePremiumCapability(
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY, mHandler.obtainMessage());
        mTestableLooper.processAllMessages();
        assertEquals(TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_FEATURE_NOT_SUPPORTED,
                mResult);

        // retry after enabling feature
        doReturn((int) TelephonyManager.NETWORK_TYPE_BITMASK_NR).when(mPhone)
                .getCachedAllowedNetworkTypesBitmask();

        mSlicePurchaseController.purchasePremiumCapability(
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY, mHandler.obtainMessage());
        mTestableLooper.processAllMessages();
        assertNotEquals(TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_FEATURE_NOT_SUPPORTED,
                mResult);
    }

    @Test
    public void testPurchasePremiumCapabilityResultCarrierDisabled() {
        doReturn((int) TelephonyManager.NETWORK_TYPE_BITMASK_NR).when(mPhone)
                .getCachedAllowedNetworkTypesBitmask();

        mSlicePurchaseController.purchasePremiumCapability(
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY, mHandler.obtainMessage());
        mTestableLooper.processAllMessages();
        assertEquals(TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_CARRIER_DISABLED, mResult);

        // retry after enabling carrier configs
        mBundle.putIntArray(CarrierConfigManager.KEY_SUPPORTED_PREMIUM_CAPABILITIES_INT_ARRAY,
                new int[]{TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY});
        mBundle.putString(CarrierConfigManager.KEY_PREMIUM_CAPABILITY_PURCHASE_URL_STRING,
                SlicePurchaseController.SLICE_PURCHASE_TEST_FILE);

        mSlicePurchaseController.purchasePremiumCapability(
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY, mHandler.obtainMessage());
        mTestableLooper.processAllMessages();
        assertNotEquals(TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_CARRIER_DISABLED,
                mResult);
    }

    @Test
    public void testPurchasePremiumCapabilityResultNotDefaultDataSubscription() {
        doReturn((int) TelephonyManager.NETWORK_TYPE_BITMASK_NR).when(mPhone)
                .getCachedAllowedNetworkTypesBitmask();
        mBundle.putIntArray(CarrierConfigManager.KEY_SUPPORTED_PREMIUM_CAPABILITIES_INT_ARRAY,
                new int[]{TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY});
        mBundle.putString(CarrierConfigManager.KEY_PREMIUM_CAPABILITY_PURCHASE_URL_STRING,
                SlicePurchaseController.SLICE_PURCHASE_TEST_FILE);

        mSlicePurchaseController.purchasePremiumCapability(
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY, mHandler.obtainMessage());
        mTestableLooper.processAllMessages();
        assertEquals(
                TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_NOT_DEFAULT_DATA_SUBSCRIPTION,
                mResult);

        // retry on default data subscription
        doReturn(SubscriptionManager.getDefaultDataSubscriptionId()).when(mPhone).getSubId();

        mSlicePurchaseController.purchasePremiumCapability(
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY, mHandler.obtainMessage());
        mTestableLooper.processAllMessages();
        assertNotEquals(
                TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_NOT_DEFAULT_DATA_SUBSCRIPTION,
                mResult);
    }

    @Test
    public void testPurchasePremiumCapabilityResultNetworkNotAvailable() {
        doReturn((int) TelephonyManager.NETWORK_TYPE_BITMASK_NR).when(mPhone)
                .getCachedAllowedNetworkTypesBitmask();
        mBundle.putIntArray(CarrierConfigManager.KEY_SUPPORTED_PREMIUM_CAPABILITIES_INT_ARRAY,
                new int[]{TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY});
        mBundle.putString(CarrierConfigManager.KEY_PREMIUM_CAPABILITY_PURCHASE_URL_STRING,
                SlicePurchaseController.SLICE_PURCHASE_TEST_FILE);
        doReturn(SubscriptionManager.getDefaultDataSubscriptionId()).when(mPhone).getSubId();

        mSlicePurchaseController.purchasePremiumCapability(
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY, mHandler.obtainMessage());
        mTestableLooper.processAllMessages();
        assertEquals(TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_NETWORK_NOT_AVAILABLE,
                mResult);

        // retry with valid network
        doReturn(TelephonyManager.NETWORK_TYPE_NR).when(mServiceState).getDataNetworkType();
        doReturn(true).when(mDataSettingsManager).isDataEnabledForReason(anyInt());

        mSlicePurchaseController.purchasePremiumCapability(
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY, mHandler.obtainMessage());
        mTestableLooper.processAllMessages();
        assertNotEquals(TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_NETWORK_NOT_AVAILABLE,
                mResult);
    }

    @Test
    public void testPurchasePremiumCapabilityResultEntitlementCheckFailed() {
        doReturn((int) TelephonyManager.NETWORK_TYPE_BITMASK_NR).when(mPhone)
                .getCachedAllowedNetworkTypesBitmask();
        mBundle.putIntArray(CarrierConfigManager.KEY_SUPPORTED_PREMIUM_CAPABILITIES_INT_ARRAY,
                new int[]{TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY});
        mBundle.putString(CarrierConfigManager.KEY_PREMIUM_CAPABILITY_PURCHASE_URL_STRING,
                SlicePurchaseController.SLICE_PURCHASE_TEST_FILE);
        doReturn(SubscriptionManager.getDefaultDataSubscriptionId()).when(mPhone).getSubId();
        doReturn(TelephonyManager.NETWORK_TYPE_NR).when(mServiceState).getDataNetworkType();
        doReturn(true).when(mDataSettingsManager).isDataEnabledForReason(anyInt());
        doReturn(null).when(mPremiumNetworkEntitlementApi).checkEntitlementStatus(anyInt());

        mSlicePurchaseController.purchasePremiumCapability(
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY, mHandler.obtainMessage());
        mTestableLooper.processAllMessages();
        assertEquals(TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_CARRIER_ERROR, mResult);

        // retry with provisioned response
        mEntitlementResponse.mEntitlementStatus =
                PremiumNetworkEntitlementResponse.PREMIUM_NETWORK_ENTITLEMENT_STATUS_INCLUDED;
        mEntitlementResponse.mProvisionStatus =
                PremiumNetworkEntitlementResponse.PREMIUM_NETWORK_PROVISION_STATUS_PROVISIONED;
        doReturn(mEntitlementResponse).when(mPremiumNetworkEntitlementApi)
                .checkEntitlementStatus(anyInt());

        mSlicePurchaseController.purchasePremiumCapability(
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY, mHandler.obtainMessage());
        mTestableLooper.processAllMessages();
        assertEquals(TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_ALREADY_PURCHASED,
                mResult);

        // retry with provisioning response
        mEntitlementResponse.mEntitlementStatus =
                PremiumNetworkEntitlementResponse.PREMIUM_NETWORK_ENTITLEMENT_STATUS_PROVISIONING;
        mEntitlementResponse.mProvisionStatus =
                PremiumNetworkEntitlementResponse.PREMIUM_NETWORK_PROVISION_STATUS_IN_PROGRESS;

        mSlicePurchaseController.purchasePremiumCapability(
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY, mHandler.obtainMessage());
        mTestableLooper.processAllMessages();
        assertEquals(TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_ALREADY_IN_PROGRESS,
                mResult);

        // retry with disallowed response and throttling
        mEntitlementResponse.mProvisionStatus =
                PremiumNetworkEntitlementResponse.PREMIUM_NETWORK_PROVISION_STATUS_NOT_PROVISIONED;
        mEntitlementResponse.mEntitlementStatus =
                PremiumNetworkEntitlementResponse.PREMIUM_NETWORK_ENTITLEMENT_STATUS_INCOMPATIBLE;
        mBundle.putLong(CarrierConfigManager
                .KEY_PREMIUM_CAPABILITY_PURCHASE_CONDITION_BACKOFF_HYSTERESIS_TIME_MILLIS_LONG,
                PURCHASE_CONDITION_TIMEOUT);

        mSlicePurchaseController.purchasePremiumCapability(
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY, mHandler.obtainMessage());
        mTestableLooper.processAllMessages();
        assertEquals(TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_ENTITLEMENT_CHECK_FAILED,
                mResult);

        // retry to verify throttled
        mSlicePurchaseController.purchasePremiumCapability(
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY, mHandler.obtainMessage());
        mTestableLooper.processAllMessages();
        assertEquals(TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_THROTTLED, mResult);

        // retry with valid entitlement check to verify unthrottled
        mTestableLooper.moveTimeForward(PURCHASE_CONDITION_TIMEOUT);
        mTestableLooper.processAllMessages();

        testPurchasePremiumCapabilityResultSuccess();
    }

    @Test
    public void testPurchasePremiumCapabilityResultAlreadyInProgress() {
        sendValidPurchaseRequest();

        mSlicePurchaseController.purchasePremiumCapability(
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY, mHandler.obtainMessage());
        mTestableLooper.processAllMessages();
        assertEquals(TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_ALREADY_IN_PROGRESS,
                mResult);

        // retry to verify same result
        mSlicePurchaseController.purchasePremiumCapability(
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY, mHandler.obtainMessage());
        mTestableLooper.processAllMessages();
        assertEquals(TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_ALREADY_IN_PROGRESS,
                mResult);
    }

    @Test
    public void testPurchasePremiumCapabilityResultSuccess() {
        sendValidPurchaseRequest();

        // broadcast SUCCESS response from slice purchase application
        Intent intent = new Intent();
        intent.setAction("com.android.phone.slice.action.SLICE_PURCHASE_APP_RESPONSE_SUCCESS");
        intent.putExtra(SlicePurchaseController.EXTRA_PHONE_ID, PHONE_ID);
        intent.putExtra(SlicePurchaseController.EXTRA_PREMIUM_CAPABILITY,
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY);
        mContext.getBroadcastReceiver().onReceive(mContext, intent);
        mTestableLooper.processAllMessages();
        assertEquals(TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_SUCCESS, mResult);

        // retry tested in testPurchasePremiumCapabilityResultPendingNetworkSetup
    }

    @Test
    public void testPurchasePremiumCapabilityResultPendingNetworkSetup() {
        testPurchasePremiumCapabilityResultSuccess();

        mSlicePurchaseController.purchasePremiumCapability(
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY, mHandler.obtainMessage());
        mTestableLooper.processAllMessages();
        assertEquals(TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_PENDING_NETWORK_SETUP,
                mResult);

        // retry to verify unthrottled
        mTestableLooper.moveTimeForward(NETWORK_SETUP_TIMEOUT);
        mTestableLooper.processAllMessages();

        testPurchasePremiumCapabilityResultSuccess();
    }

    @Test
    public void testPurchasePremiumCapabilityResultAlreadyPurchased() {
        testPurchasePremiumCapabilityResultSuccess();

        sendNetworkSlicingConfig(TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY, true);

        mSlicePurchaseController.purchasePremiumCapability(
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY, mHandler.obtainMessage());
        mTestableLooper.processAllMessages();
        assertEquals(TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_ALREADY_PURCHASED,
                mResult);

        // retry to verify same result
        mSlicePurchaseController.purchasePremiumCapability(
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY, mHandler.obtainMessage());
        mTestableLooper.processAllMessages();
        assertEquals(TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_ALREADY_PURCHASED,
                mResult);

        // retry to verify purchase expired
        sendNetworkSlicingConfig(TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY, false);

        testPurchasePremiumCapabilityResultSuccess();
    }

    @Test
    public void testPurchasePremiumCapabilityResultTimeout() {
        sendValidPurchaseRequest();

        mTestableLooper.moveTimeForward(NOTIFICATION_TIMEOUT);
        mTestableLooper.processAllMessages();
        assertEquals(TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_TIMEOUT, mResult);

        // retry to verify throttled
        mSlicePurchaseController.purchasePremiumCapability(
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY, mHandler.obtainMessage());
        mTestableLooper.processAllMessages();
        assertEquals(TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_THROTTLED, mResult);

        // retry to verify unthrottled
        mTestableLooper.moveTimeForward(THROTTLE_TIMEOUT);
        mTestableLooper.processAllMessages();

        testPurchasePremiumCapabilityResultSuccess();
    }

    @Test
    public void testPurchasePremiumCapabilityResultUserCanceled() {
        sendValidPurchaseRequest();

        // broadcast CANCELED response from slice purchase application
        Intent intent = new Intent();
        intent.setAction("com.android.phone.slice.action.SLICE_PURCHASE_APP_RESPONSE_CANCELED");
        intent.putExtra(SlicePurchaseController.EXTRA_PHONE_ID, PHONE_ID);
        intent.putExtra(SlicePurchaseController.EXTRA_PREMIUM_CAPABILITY,
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY);
        mContext.getBroadcastReceiver().onReceive(mContext, intent);
        mTestableLooper.processAllMessages();
        assertEquals(TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_USER_CANCELED, mResult);

        // retry to verify throttled
        mSlicePurchaseController.purchasePremiumCapability(
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY, mHandler.obtainMessage());
        mTestableLooper.processAllMessages();
        assertEquals(TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_THROTTLED, mResult);

        // retry to verify unthrottled
        mTestableLooper.moveTimeForward(THROTTLE_TIMEOUT);
        mTestableLooper.processAllMessages();

        testPurchasePremiumCapabilityResultSuccess();
    }

    @Test
    public void testPurchasePremiumCapabilityResultCarrierError() {
        sendValidPurchaseRequest();

        // broadcast CARRIER_ERROR response from slice purchase application
        Intent intent = new Intent();
        intent.setAction(
                "com.android.phone.slice.action.SLICE_PURCHASE_APP_RESPONSE_CARRIER_ERROR");
        intent.putExtra(SlicePurchaseController.EXTRA_PHONE_ID, PHONE_ID);
        intent.putExtra(SlicePurchaseController.EXTRA_PREMIUM_CAPABILITY,
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY);
        intent.putExtra(SlicePurchaseController.EXTRA_FAILURE_CODE,
                SlicePurchaseController.FAILURE_CODE_CARRIER_URL_UNAVAILABLE);
        mContext.getBroadcastReceiver().onReceive(mContext, intent);
        mTestableLooper.processAllMessages();
        assertEquals(TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_CARRIER_ERROR, mResult);

        // retry to verify throttled
        mSlicePurchaseController.purchasePremiumCapability(
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY, mHandler.obtainMessage());
        mTestableLooper.processAllMessages();
        assertEquals(TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_THROTTLED, mResult);

        // retry to verify unthrottled
        mTestableLooper.moveTimeForward(PURCHASE_CONDITION_TIMEOUT);
        mTestableLooper.processAllMessages();

        testPurchasePremiumCapabilityResultSuccess();
    }

    @Test
    public void testPurchasePremiumCapabilityResultRequestFailed() {
        sendValidPurchaseRequest();

        // broadcast REQUEST_FAILED response from slice purchase application
        Intent intent = new Intent();
        intent.setAction(
                "com.android.phone.slice.action.SLICE_PURCHASE_APP_RESPONSE_REQUEST_FAILED");
        intent.putExtra(SlicePurchaseController.EXTRA_PHONE_ID, PHONE_ID);
        intent.putExtra(SlicePurchaseController.EXTRA_PREMIUM_CAPABILITY,
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY);
        mContext.getBroadcastReceiver().onReceive(mContext, intent);
        mTestableLooper.processAllMessages();
        assertEquals(TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_REQUEST_FAILED, mResult);

        // retry to verify no throttling
        testPurchasePremiumCapabilityResultSuccess();
    }

    @Test
    public void testPurchasePremiumCapabilityResultNotDefaultDataSubscriptionResponse() {
        sendValidPurchaseRequest();

        // broadcast NOT_DEFAULT_DATA_SUBSCRIPTION response from slice purchase application
        Intent intent = new Intent();
        intent.setAction("com.android.phone.slice.action."
                + "SLICE_PURCHASE_APP_RESPONSE_NOT_DEFAULT_DATA_SUBSCRIPTION");
        intent.putExtra(SlicePurchaseController.EXTRA_PHONE_ID, PHONE_ID);
        intent.putExtra(SlicePurchaseController.EXTRA_PREMIUM_CAPABILITY,
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY);
        mContext.getBroadcastReceiver().onReceive(mContext, intent);
        mTestableLooper.processAllMessages();
        assertEquals(
                TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_NOT_DEFAULT_DATA_SUBSCRIPTION,
                mResult);

        // retry to verify no throttling
        testPurchasePremiumCapabilityResultSuccess();
    }

    @Test
    public void testPurchasePremiumCapabilityResultNotificationsDisabled() {
        doReturn(true).when(mFeatureFlags).slicingAdditionalErrorCodes();
        sendValidPurchaseRequest();

        // broadcast NOTIFICATIONS_DISABLED response from slice purchase application
        Intent intent = new Intent();
        intent.setAction("com.android.phone.slice.action."
                + "SLICE_PURCHASE_APP_RESPONSE_NOTIFICATIONS_DISABLED");
        intent.putExtra(SlicePurchaseController.EXTRA_PHONE_ID, PHONE_ID);
        intent.putExtra(SlicePurchaseController.EXTRA_PREMIUM_CAPABILITY,
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY);
        mContext.getBroadcastReceiver().onReceive(mContext, intent);
        mTestableLooper.processAllMessages();
        assertEquals(TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_USER_DISABLED, mResult);

        // retry to verify throttled
        mSlicePurchaseController.purchasePremiumCapability(
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY, mHandler.obtainMessage());
        mTestableLooper.processAllMessages();
        assertEquals(TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_THROTTLED, mResult);

        // retry to verify unthrottled
        mTestableLooper.moveTimeForward(THROTTLE_TIMEOUT);
        mTestableLooper.processAllMessages();

        testPurchasePremiumCapabilityResultSuccess();
    }

    @Test
    public void testPurchasePremiumCapabilityResultNotificationThrottled() {
        mSlicePurchaseController.setLocalDate(LocalDate.of(YEAR, MONTH, DATE));
        mSlicePurchaseController.updateNotificationCounts();

        for (int count = 1; count <= DAILY_NOTIFICATION_MAX; count++) {
            completeSuccessfulPurchase();
            verify(mEditor).putInt(eq(DAILY_NOTIFICATION_COUNT_KEY), eq(count));
            verify(mEditor).putInt(eq(MONTHLY_NOTIFICATION_COUNT_KEY), eq(count));
        }

        // retry to verify throttled
        mSlicePurchaseController.purchasePremiumCapability(
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY, mHandler.obtainMessage());
        mTestableLooper.processAllMessages();
        assertEquals(TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_THROTTLED, mResult);

        // change the date to trigger daily reset
        mSlicePurchaseController.setLocalDate(LocalDate.of(YEAR, MONTH, DATE + 1));
        Mockito.clearInvocations(mEditor);

        for (int count = 1; count <= (MONTHLY_NOTIFICATION_MAX - DAILY_NOTIFICATION_MAX); count++) {
            completeSuccessfulPurchase();
            verify(mEditor).putInt(eq(DAILY_NOTIFICATION_COUNT_KEY), eq(count));
            verify(mEditor).putInt(eq(MONTHLY_NOTIFICATION_COUNT_KEY),
                    eq(count + DAILY_NOTIFICATION_MAX));
        }

        // retry to verify throttled
        mSlicePurchaseController.purchasePremiumCapability(
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY, mHandler.obtainMessage());
        mTestableLooper.processAllMessages();
        assertEquals(TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_THROTTLED, mResult);
    }

    @Test
    public void testIsSlicingConfigActive_emptyUrspRules() {
        int capability = TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY;
        NetworkSliceInfo sliceInfo = createNetworkSliceInfo(
                getRandomSliceServiceType(capability), true);
        NetworkSlicingConfig slicingConfig = new NetworkSlicingConfig(
                Collections.emptyList(), Collections.singletonList(sliceInfo));
        mSlicePurchaseController.setSlicingConfig(slicingConfig);

        assertFalse(mSlicePurchaseController.isSlicingConfigActive(capability));
    }

    @Test
    public void testIsSlicingConfigActive_noMatchingTrafficDescriptor() {
        int capability = TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY;
        NetworkSliceInfo sliceInfo = createNetworkSliceInfo(
                getRandomSliceServiceType(capability), true);
        TrafficDescriptor trafficDescriptor = createTrafficDescriptor("ENTERPRISE");
        RouteSelectionDescriptor routeSelectionDescriptor = createRouteSelectionDescriptor(
                Collections.singletonList(sliceInfo));
        NetworkSlicingConfig slicingConfig = createNetworkSlicingConfig(
                Collections.singletonList(sliceInfo),
                Collections.singletonList(trafficDescriptor),
                Collections.singletonList(routeSelectionDescriptor));
        mSlicePurchaseController.setSlicingConfig(slicingConfig);

        assertFalse(mSlicePurchaseController.isSlicingConfigActive(capability));
    }

    @Test
    public void testIsSlicingConfigActive_multipleElements() {
        int capability = TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY;
        NetworkSliceInfo sliceInfo1 = createNetworkSliceInfo(
                getRandomSliceServiceType(SlicePurchaseController.PREMIUM_CAPABILITY_INVALID),
                false);
        NetworkSliceInfo sliceInfo2 = createNetworkSliceInfo(
                getRandomSliceServiceType(capability), true);
        List<NetworkSliceInfo> sliceInfos = new ArrayList<>();
        sliceInfos.add(sliceInfo1);
        sliceInfos.add(sliceInfo2);

        TrafficDescriptor trafficDescriptor1 = createTrafficDescriptor("ENTERPRISE");
        TrafficDescriptor trafficDescriptor2 = createTrafficDescriptor(
                SlicePurchaseController.getAppId(capability));
        List<TrafficDescriptor> trafficDescriptors = new ArrayList<>();
        trafficDescriptors.add(trafficDescriptor1);
        trafficDescriptors.add(trafficDescriptor2);

        RouteSelectionDescriptor routeSelectionDescriptor1 = createRouteSelectionDescriptor(
                Collections.emptyList());
        RouteSelectionDescriptor routeSelectionDescriptor2 = createRouteSelectionDescriptor(
                sliceInfos);
        List<RouteSelectionDescriptor> routeSelectionDescriptors = new ArrayList<>();
        routeSelectionDescriptors.add(routeSelectionDescriptor1);
        routeSelectionDescriptors.add(routeSelectionDescriptor2);

        NetworkSlicingConfig slicingConfig = createNetworkSlicingConfig(
                sliceInfos, trafficDescriptors, routeSelectionDescriptors);
        mSlicePurchaseController.setSlicingConfig(slicingConfig);

        assertTrue(mSlicePurchaseController.isSlicingConfigActive(capability));
    }

    private void completeSuccessfulPurchase() {
        sendValidPurchaseRequest();

        // broadcast NOTIFICATION_SHOWN response from slice purchase application
        Intent intent = new Intent();
        intent.setAction(
                "com.android.phone.slice.action.SLICE_PURCHASE_APP_RESPONSE_NOTIFICATION_SHOWN");
        intent.putExtra(SlicePurchaseController.EXTRA_PHONE_ID, PHONE_ID);
        intent.putExtra(SlicePurchaseController.EXTRA_PREMIUM_CAPABILITY,
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY);
        mContext.getBroadcastReceiver().onReceive(mContext, intent);
        mTestableLooper.processAllMessages();

        // broadcast SUCCESS response from slice purchase application
        intent.setAction("com.android.phone.slice.action.SLICE_PURCHASE_APP_RESPONSE_SUCCESS");
        mContext.getBroadcastReceiver().onReceive(mContext, intent);
        mTestableLooper.processAllMessages();
        assertEquals(TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_SUCCESS, mResult);

        // complete network setup
        sendNetworkSlicingConfig(TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY, true);
        // purchase expired
        sendNetworkSlicingConfig(TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY, false);
    }

    private void sendValidPurchaseRequest() {
        clearInvocations(mContext);

        // feature supported
        doReturn((int) TelephonyManager.NETWORK_TYPE_BITMASK_NR).when(mPhone)
                .getCachedAllowedNetworkTypesBitmask();
        // carrier supported
        mBundle.putIntArray(CarrierConfigManager.KEY_SUPPORTED_PREMIUM_CAPABILITIES_INT_ARRAY,
                new int[]{TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY});
        mBundle.putString(CarrierConfigManager.KEY_PREMIUM_CAPABILITY_PURCHASE_URL_STRING,
                SlicePurchaseController.SLICE_PURCHASE_TEST_FILE);
        mBundle.putLong(CarrierConfigManager
                .KEY_PREMIUM_CAPABILITY_NOTIFICATION_DISPLAY_TIMEOUT_MILLIS_LONG,
                NOTIFICATION_TIMEOUT);
        mBundle.putLong(CarrierConfigManager.KEY_PREMIUM_CAPABILITY_NETWORK_SETUP_TIME_MILLIS_LONG,
                NETWORK_SETUP_TIMEOUT);
        mBundle.putLong(CarrierConfigManager
                .KEY_PREMIUM_CAPABILITY_NOTIFICATION_BACKOFF_HYSTERESIS_TIME_MILLIS_LONG,
                THROTTLE_TIMEOUT);
        mBundle.putLong(CarrierConfigManager
                .KEY_PREMIUM_CAPABILITY_PURCHASE_CONDITION_BACKOFF_HYSTERESIS_TIME_MILLIS_LONG,
                PURCHASE_CONDITION_TIMEOUT);
        // default data subscription
        doReturn(SubscriptionManager.getDefaultDataSubscriptionId()).when(mPhone).getSubId();
        // network available
        doReturn(TelephonyManager.NETWORK_TYPE_NR).when(mServiceState).getDataNetworkType();
        doReturn(true).when(mDataSettingsManager).isDataEnabledForReason(anyInt());
        // entitlement check passed
        mEntitlementResponse.mEntitlementStatus =
                PremiumNetworkEntitlementResponse.PREMIUM_NETWORK_ENTITLEMENT_STATUS_ENABLED;
        mEntitlementResponse.mProvisionStatus =
                PremiumNetworkEntitlementResponse.PREMIUM_NETWORK_PROVISION_STATUS_NOT_PROVISIONED;

        // send purchase request
        mSlicePurchaseController.purchasePremiumCapability(
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY, mHandler.obtainMessage());
        mTestableLooper.processAllMessages();

        // verify that the purchase request was sent successfully
        verify(mContext).sendBroadcast(any(Intent.class));
        assertEquals(SlicePurchaseController.ACTION_START_SLICE_PURCHASE_APP,
                mContext.getBroadcast().getAction());
        assertTrue(mSlicePurchaseController.hasMessages(4 /* EVENT_PURCHASE_TIMEOUT */,
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY));
        verify(mContext).registerReceiver(any(BroadcastReceiver.class), any(IntentFilter.class),
                eq(Context.RECEIVER_NOT_EXPORTED));
    }

    private void sendNetworkSlicingConfig(int capability, boolean configActive) {
        NetworkSliceInfo sliceInfo = createNetworkSliceInfo(
                getRandomSliceServiceType(capability), configActive);
        TrafficDescriptor trafficDescriptor = createTrafficDescriptor(
                SlicePurchaseController.getAppId(capability));
        RouteSelectionDescriptor routeSelectionDescriptor = createRouteSelectionDescriptor(
                Collections.singletonList(sliceInfo));
        NetworkSlicingConfig slicingConfig = createNetworkSlicingConfig(
                Collections.singletonList(sliceInfo),
                Collections.singletonList(trafficDescriptor),
                Collections.singletonList(routeSelectionDescriptor));
        mSlicePurchaseController.obtainMessage(2 /* EVENT_SLICING_CONFIG_CHANGED */,
                new AsyncResult(null, slicingConfig, null)).sendToTarget();
        mTestableLooper.processAllMessages();
    }

    @NetworkSliceInfo.SliceServiceType private int getRandomSliceServiceType(
            @TelephonyManager.PremiumCapability int capability) {
        for (int sliceServiceType : SlicePurchaseController.getSliceServiceTypes(capability)) {
            // Get a random valid sst from the set
            return sliceServiceType;
        }
        return NetworkSliceInfo.SLICE_SERVICE_TYPE_NONE;
    }

    @NonNull private NetworkSliceInfo createNetworkSliceInfo(
            @NetworkSliceInfo.SliceServiceType int sliceServiceType, boolean active) {
        return new NetworkSliceInfo.Builder()
                .setStatus(active ? NetworkSliceInfo.SLICE_STATUS_ALLOWED
                        : NetworkSliceInfo.SLICE_STATUS_UNKNOWN)
                .setSliceServiceType(sliceServiceType)
                .build();
    }

    @NonNull private TrafficDescriptor createTrafficDescriptor(@NonNull String appId) {
        TrafficDescriptor.OsAppId osAppId = new TrafficDescriptor.OsAppId(
                TrafficDescriptor.OsAppId.ANDROID_OS_ID, appId);
        return new TrafficDescriptor.Builder()
                .setOsAppId(osAppId.getBytes())
                .build();
    }

    @NonNull private RouteSelectionDescriptor createRouteSelectionDescriptor(
            @NonNull List<NetworkSliceInfo> sliceInfos) {
        return new RouteSelectionDescriptor(
                RouteSelectionDescriptor.MIN_ROUTE_PRECEDENCE,
                RouteSelectionDescriptor.SESSION_TYPE_IPV4,
                RouteSelectionDescriptor.ROUTE_SSC_MODE_1,
                sliceInfos, Collections.emptyList());
    }

    @NonNull private NetworkSlicingConfig createNetworkSlicingConfig(
            @NonNull List<NetworkSliceInfo> sliceInfos,
            @NonNull List<TrafficDescriptor> trafficDescriptors,
            @NonNull List<RouteSelectionDescriptor> routeSelectionDescriptors) {
        UrspRule urspRule = new UrspRule(0, trafficDescriptors, routeSelectionDescriptors);
        return new NetworkSlicingConfig(Collections.singletonList(urspRule), sliceInfos);
    }
}
