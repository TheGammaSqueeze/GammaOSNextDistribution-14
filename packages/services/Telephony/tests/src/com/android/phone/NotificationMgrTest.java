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

package com.android.phone;

import static android.telephony.RadioAccessFamily.RAF_1xRTT;
import static android.telephony.RadioAccessFamily.RAF_EDGE;
import static android.telephony.RadioAccessFamily.RAF_EHRPD;
import static android.telephony.RadioAccessFamily.RAF_EVDO_0;
import static android.telephony.RadioAccessFamily.RAF_EVDO_A;
import static android.telephony.RadioAccessFamily.RAF_EVDO_B;
import static android.telephony.RadioAccessFamily.RAF_GPRS;
import static android.telephony.RadioAccessFamily.RAF_GSM;
import static android.telephony.RadioAccessFamily.RAF_HSDPA;
import static android.telephony.RadioAccessFamily.RAF_HSPA;
import static android.telephony.RadioAccessFamily.RAF_HSPAP;
import static android.telephony.RadioAccessFamily.RAF_HSUPA;
import static android.telephony.RadioAccessFamily.RAF_IS95A;
import static android.telephony.RadioAccessFamily.RAF_IS95B;
import static android.telephony.RadioAccessFamily.RAF_LTE;
import static android.telephony.RadioAccessFamily.RAF_LTE_CA;
import static android.telephony.RadioAccessFamily.RAF_TD_SCDMA;
import static android.telephony.RadioAccessFamily.RAF_UMTS;
import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;

import static com.android.phone.NotificationMgr.DATA_ROAMING_NOTIFICATION;
import static com.android.phone.NotificationMgr.LIMITED_SIM_FUNCTION_NOTIFICATION;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.StatusBarManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.os.Build;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.TelephonyTestBase;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.SignalStrengthController;
import com.android.internal.telephony.data.DataConfigManager;
import com.android.internal.telephony.data.DataNetworkController;
import com.android.internal.telephony.data.DataSettingsManager;
import com.android.internal.telephony.util.NotificationChannelController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Unit Test for NotificationMgr
 */
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class NotificationMgrTest extends TelephonyTestBase {
    private static final int TEST_SUB_ID = 1;
    private static final long SERIAL_NUMBER_OF_USER = 1234567L;
    private static final String TEST_LABEL_CF = "test_call_forwarding";
    private static final String TEST_SUB_INFO_DISPLAY_NAME = "display_name";
    private static final String TEST_PACKAGE_NAME = "com.android.phone";
    private static final String TEST_SELECTED_NETWORK_OPERATOR_NAME = "TheOperator";
    private static final String MOBILE_NETWORK_SELECTION_PACKAGE = "com.android.phone";
    private static final String MOBILE_NETWORK_SELECTION_CLASS = ".testClass";
    private static final String CARRIER_NAME = "CoolCarrier";

    @Mock PhoneGlobals mApp;
    @Mock StatusBarManager mStatusBarManager;
    @Mock UserManager mUserManager;
    @Mock SubscriptionManager mSubscriptionManager;
    @Mock TelecomManager mTelecomManager;
    @Mock TelephonyManager mTelephonyManager;
    @Mock Phone mPhone;
    @Mock SharedPreferences mSharedPreferences;
    @Mock NotificationManager mNotificationManager;
    @Mock SubscriptionInfo mSubscriptionInfo;
    @Mock Resources mResources;
    @Mock Context mMockedContext;
    @Mock ServiceStateTracker mServiceStateTracker;
    @Mock ServiceState mServiceState;
    @Mock CarrierConfigManager mCarrierConfigManager;
    @Mock DataNetworkController mDataNetworkController;
    @Mock DataSettingsManager mDataSettingsManager;
    @Mock DataConfigManager mDataConfigManager;
    @Mock SignalStrengthController mSignalStrengthController;

    private Phone[] mPhones;
    private NotificationMgr mNotificationMgr;
    private TestableLooper mTestableLooper;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mPhones = new Phone[]{mPhone};
        replaceInstance(PhoneFactory.class, "sPhones", null, mPhones);
        when(mPhone.getPhoneType()).thenReturn(PhoneConstants.PHONE_TYPE_GSM);
        when(mPhone.getContext()).thenReturn(mMockedContext);
        when(mMockedContext.getResources()).thenReturn(mResources);
        when(mPhone.getServiceState()).thenReturn(mServiceState);
        when(mServiceState.getNetworkRegistrationInfo(anyInt(), anyInt())).thenReturn(
                new NetworkRegistrationInfo.Builder()
                        .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                        .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                        .build());
        when(mPhone.getServiceStateTracker()).thenReturn(mServiceStateTracker);
        mServiceStateTracker.mSS = mServiceState;
        when(mPhone.getSignalStrengthController()).thenReturn(mSignalStrengthController);
        when(mPhone.getDataNetworkController()).thenReturn(mDataNetworkController);
        when(mDataNetworkController.getInternetDataDisallowedReasons()).thenReturn(
                Collections.emptyList());
        when(mDataNetworkController.getDataConfigManager()).thenReturn(mDataConfigManager);
        when(mPhone.getDataSettingsManager()).thenReturn(mDataSettingsManager);
        when(mDataSettingsManager.isDataEnabledForReason(anyInt())).thenReturn(true);
        when(mApp.getSharedPreferences(anyString(), anyInt())).thenReturn(mSharedPreferences);

        when(mApp.getPackageName()).thenReturn(TEST_PACKAGE_NAME);
        when(mApp.getSystemService(Context.STATUS_BAR_SERVICE)).thenReturn(mStatusBarManager);
        when(mApp.getSystemService(Context.USER_SERVICE)).thenReturn(mUserManager);
        when(mApp.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)).thenReturn(
                mSubscriptionManager);
        when(mApp.getSystemServiceName(TelecomManager.class)).thenReturn(Context.TELECOM_SERVICE);
        when(mApp.getSystemService(TelecomManager.class)).thenReturn(mTelecomManager);
        when(mApp.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(mTelephonyManager);
        when(mApp.getSystemServiceName(CarrierConfigManager.class)).thenReturn(
                Context.CARRIER_CONFIG_SERVICE);
        when(mApp.getSystemService(CarrierConfigManager.class)).thenReturn(mCarrierConfigManager);
        when(mApp.getSystemServiceName(CarrierConfigManager.class)).thenReturn(
                Context.CARRIER_CONFIG_SERVICE);
        when(mApp.getSystemService(CarrierConfigManager.class)).thenReturn(mCarrierConfigManager);

        when(mApp.createPackageContextAsUser(any(), eq(0), any())).thenReturn(mApp);
        when(mApp.getSystemService(Context.NOTIFICATION_SERVICE)).thenReturn(mNotificationManager);
        when(mUserManager.getSerialNumbersOfUsers(true)).thenReturn(
                new long[]{SERIAL_NUMBER_OF_USER});
        when(mUserManager.getUserForSerialNumber(eq(SERIAL_NUMBER_OF_USER))).thenReturn(
                UserHandle.SYSTEM);
        when(mApp.getResources()).thenReturn(mResources);
        when(mResources.getString(R.string.labelCF)).thenReturn(TEST_LABEL_CF);
        ApplicationInfo appWithSdkS = buildApplicationInfo(Build.VERSION_CODES.S);
        when(mApp.getApplicationInfo()).thenReturn(appWithSdkS);
        when(mTelephonyManager.createForSubscriptionId(anyInt())).thenReturn(mTelephonyManager);
        when(mTelephonyManager.getServiceState()).thenReturn(mServiceState);

        mTestableLooper = TestableLooper.get(this);
        // Spy it only to avoid sleep for SystemClock.elapsedRealtime()
        mNotificationMgr = spy(new NotificationMgr(mApp));
        mTestableLooper.processAllMessages();
    }

    @Test
    public void testUpdateCfi_visible_noActiveSubscription_notificationNeverSent() {
        // Given no active subscription available
        when(mSubscriptionManager.getActiveSubscriptionInfo(eq(TEST_SUB_ID))).thenReturn(null);

        // When updateCfi method is called
        mNotificationMgr.updateCfi(TEST_SUB_ID, /*visible=*/true, /*isFresh=*/false);

        // Then the notification should never be sent
        verify(mNotificationManager, never()).notify(any(), anyInt(), any());
    }

    @Test
    public void testUpdateCfi_visible_hasActiveSub_singleSIM_notificationSent() {
        when(mTelephonyManager.getPhoneCount()).thenReturn(1);
        when(mSubscriptionManager.getActiveSubscriptionInfo(eq(TEST_SUB_ID))).thenReturn(
                mSubscriptionInfo);

        mNotificationMgr.updateCfi(TEST_SUB_ID, /*visible=*/true, /*isFresh=*/false);

        verifyNotificationSentWithChannelId(NotificationChannelController.CHANNEL_ID_CALL_FORWARD);
    }

    @Test
    public void testUpdateCfi_visible_hasActiveSub_multiSIM_notificationSentWithoutDisplayName() {
        when(mTelephonyManager.getPhoneCount()).thenReturn(2);
        when(mSubscriptionManager.getActiveSubscriptionInfo(eq(TEST_SUB_ID))).thenReturn(
                mSubscriptionInfo);
        when(mSubscriptionInfo.getDisplayName()).thenReturn(null);

        mNotificationMgr.updateCfi(TEST_SUB_ID, /*visible=*/true, /*isFresh=*/false);

        verifyNotificationSentWithChannelId(NotificationChannelController.CHANNEL_ID_CALL_FORWARD);
    }

    @Test
    public void testUpdateCfi_visible_hasActiveSub_multiSIM_notificationSentWithDisplayName() {
        when(mTelephonyManager.getPhoneCount()).thenReturn(2);
        when(mSubscriptionManager.getActiveSubscriptionInfo(eq(TEST_SUB_ID))).thenReturn(
                mSubscriptionInfo);
        when(mSubscriptionInfo.getDisplayName()).thenReturn(TEST_SUB_INFO_DISPLAY_NAME);

        mNotificationMgr.updateCfi(TEST_SUB_ID, /*visible=*/true, /*isFresh=*/false);

        verifyNotificationSentWithChannelId(NotificationChannelController.CHANNEL_ID_CALL_FORWARD);
    }

    @Test
    public void testUpdateCfi_invisible_hasUnmanagedProfile_notificationCanceled() {
        when(mUserManager.isManagedProfile(anyInt())).thenReturn(false);

        mNotificationMgr.updateCfi(TEST_SUB_ID, /*visible=*/false, /*isFresh=*/false);

        verify(mNotificationManager).cancel(any(), anyInt());
    }

    @Test
    public void testUpdateCfi_invisible_allProfilesAreManaged_notificationNeverCanceled() {
        when(mUserManager.isManagedProfile(anyInt())).thenReturn(true);

        mNotificationMgr.updateCfi(TEST_SUB_ID, /*visible=*/false, /*isFresh=*/false);

        verify(mNotificationManager, never()).cancel(any(), anyInt());
    }

    @Test
    public void testShowDataRoamingNotification_roamingOn() {
        mNotificationMgr.showDataRoamingNotification(TEST_SUB_ID, /*roamingOn=*/true);

        verifyNotificationSentWithChannelId(
                NotificationChannelController.CHANNEL_ID_MOBILE_DATA_STATUS);
    }

    @Test
    public void testShowDataRoamingNotification_roamingOff() {
        mNotificationMgr.showDataRoamingNotification(TEST_SUB_ID, /*roamingOn=*/false);

        verifyNotificationSentWithChannelId(
                NotificationChannelController.CHANNEL_ID_MOBILE_DATA_STATUS);
    }

    @Test
    public void testHideDataRoamingNotification() {
        mNotificationMgr.hideDataRoamingNotification();

        verify(mNotificationManager).cancel(any(), eq(DATA_ROAMING_NOTIFICATION));
    }

    @Test
    public void testUpdateNetworkSelection_justOutOfService_notificationNeverSent() {
        prepareResourcesForNetworkSelection();

        mNotificationMgr.updateNetworkSelection(ServiceState.STATE_OUT_OF_SERVICE, TEST_SUB_ID);
        moveTimeForward(2 /* seconds */);
        mNotificationMgr.updateNetworkSelection(ServiceState.STATE_OUT_OF_SERVICE, TEST_SUB_ID);

        verify(mNotificationManager, never()).notify(any(), anyInt(), any());
    }

    @Test
    public void testUpdateNetworkSelection_oosEnoughTime_selectionVisibleToUser_notificationSent() {
        prepareResourcesForNetworkSelection();
        when(mTelephonyManager.isManualNetworkSelectionAllowed()).thenReturn(true);
        PersistableBundle config = new PersistableBundle();
        config.putBoolean(CarrierConfigManager.KEY_OPERATOR_SELECTION_EXPAND_BOOL, true);
        config.putBoolean(CarrierConfigManager.KEY_HIDE_CARRIER_NETWORK_SETTINGS_BOOL, false);
        config.putBoolean(CarrierConfigManager.KEY_CSP_ENABLED_BOOL, false);
        config.putBoolean(CarrierConfigManager.KEY_WORLD_PHONE_BOOL, true);
        when(mCarrierConfigManager.getConfigForSubId(TEST_SUB_ID)).thenReturn(config);

        // update to OOS as base state
        mNotificationMgr.updateNetworkSelection(ServiceState.STATE_OUT_OF_SERVICE, TEST_SUB_ID);
        // 10 seconds later
        moveTimeForward(10 /* seconds */);
        // verify the behavior on new request
        mNotificationMgr.updateNetworkSelection(ServiceState.STATE_OUT_OF_SERVICE, TEST_SUB_ID);

        verifyNotificationSentWithChannelId(NotificationChannelController.CHANNEL_ID_ALERT);
    }

    @Test
    public void testUpdateNetworkSelection_invalidSubscription_notificationNotSent() {
        prepareResourcesForNetworkSelection();
        when(mTelephonyManager.isManualNetworkSelectionAllowed()).thenReturn(true);
        PersistableBundle config = new PersistableBundle();
        config.putBoolean(CarrierConfigManager.KEY_OPERATOR_SELECTION_EXPAND_BOOL, true);
        config.putBoolean(CarrierConfigManager.KEY_HIDE_CARRIER_NETWORK_SETTINGS_BOOL, false);
        config.putBoolean(CarrierConfigManager.KEY_CSP_ENABLED_BOOL, false);
        config.putBoolean(CarrierConfigManager.KEY_WORLD_PHONE_BOOL, true);
        when(mCarrierConfigManager.getConfigForSubId(TEST_SUB_ID)).thenReturn(config);

        mNotificationMgr.updateNetworkSelection(ServiceState.STATE_OUT_OF_SERVICE,
                INVALID_SUBSCRIPTION_ID);
        moveTimeForward(10 /* seconds */);
        mNotificationMgr.updateNetworkSelection(ServiceState.STATE_OUT_OF_SERVICE,
                INVALID_SUBSCRIPTION_ID);

        verify(mNotificationManager, never()).notify(any(), anyInt(), any());
    }

    @Test
    public void testUpdateNetworkSelection_nullCarrierConfig_notificationNotSent() {
        prepareResourcesForNetworkSelection();

        when(mCarrierConfigManager.getConfigForSubId(TEST_SUB_ID)).thenReturn(null);

        mNotificationMgr.updateNetworkSelection(ServiceState.STATE_OUT_OF_SERVICE, TEST_SUB_ID);
        moveTimeForward(10 /* seconds */);
        mNotificationMgr.updateNetworkSelection(ServiceState.STATE_OUT_OF_SERVICE, TEST_SUB_ID);

        verify(mNotificationManager, never()).notify(any(), anyInt(), any());
    }

    @Test
    public void testUpdateNetworkSelection_userNotAllowedToChooseOperator_notificationNotSent() {
        prepareResourcesForNetworkSelection();

        PersistableBundle config = new PersistableBundle();
        // User is NOT allowed to choose operator
        config.putBoolean(CarrierConfigManager.KEY_OPERATOR_SELECTION_EXPAND_BOOL, false);
        config.putBoolean(CarrierConfigManager.KEY_HIDE_CARRIER_NETWORK_SETTINGS_BOOL, false);
        config.putBoolean(CarrierConfigManager.KEY_CSP_ENABLED_BOOL, false);
        when(mTelephonyManager.isManualNetworkSelectionAllowed()).thenReturn(false);
        config.putBoolean(CarrierConfigManager.KEY_WORLD_PHONE_BOOL, true);
        when(mCarrierConfigManager.getConfigForSubId(TEST_SUB_ID)).thenReturn(config);

        mNotificationMgr.updateNetworkSelection(ServiceState.STATE_OUT_OF_SERVICE, TEST_SUB_ID);
        moveTimeForward(10 /* seconds */);
        mNotificationMgr.updateNetworkSelection(ServiceState.STATE_OUT_OF_SERVICE, TEST_SUB_ID);

        verify(mNotificationManager, never()).notify(any(), anyInt(), any());
    }

    @Test
    public void
            testUpdateNetworkSelection_OverrideHideCarrierNetworkSelection_notificationNotSent() {
        prepareResourcesForNetworkSelection();

        PersistableBundle config = new PersistableBundle();
        // Hide network selection menu
        config.putBoolean(CarrierConfigManager.KEY_HIDE_CARRIER_NETWORK_SETTINGS_BOOL, true);
        config.putBoolean(CarrierConfigManager.KEY_OPERATOR_SELECTION_EXPAND_BOOL, true);
        config.putBoolean(CarrierConfigManager.KEY_CSP_ENABLED_BOOL, false);
        when(mTelephonyManager.isManualNetworkSelectionAllowed()).thenReturn(false);
        config.putBoolean(CarrierConfigManager.KEY_WORLD_PHONE_BOOL, true);
        when(mCarrierConfigManager.getConfigForSubId(TEST_SUB_ID)).thenReturn(config);

        mNotificationMgr.updateNetworkSelection(ServiceState.STATE_OUT_OF_SERVICE, TEST_SUB_ID);
        moveTimeForward(10 /* seconds */);
        mNotificationMgr.updateNetworkSelection(ServiceState.STATE_OUT_OF_SERVICE, TEST_SUB_ID);

        verify(mNotificationManager, never()).notify(any(), anyInt(), any());
    }

    @Test
    public void testUpdateNetworkSelection_simPreventManualSelection_notificationNotSent()
            throws Exception {
        prepareResourcesForNetworkSelection();

        PersistableBundle config = new PersistableBundle();
        config.putBoolean(CarrierConfigManager.KEY_OPERATOR_SELECTION_EXPAND_BOOL, true);
        config.putBoolean(CarrierConfigManager.KEY_HIDE_CARRIER_NETWORK_SETTINGS_BOOL, false);
        // SIM card can prevent manual network selection which is forbidden
        config.putBoolean(CarrierConfigManager.KEY_CSP_ENABLED_BOOL, true);
        when(mTelephonyManager.isManualNetworkSelectionAllowed()).thenReturn(false);
        config.putBoolean(CarrierConfigManager.KEY_WORLD_PHONE_BOOL, true);
        when(mCarrierConfigManager.getConfigForSubId(TEST_SUB_ID)).thenReturn(config);

        mNotificationMgr.updateNetworkSelection(ServiceState.STATE_OUT_OF_SERVICE, TEST_SUB_ID);
        moveTimeForward(10 /* seconds */);
        mNotificationMgr.updateNetworkSelection(ServiceState.STATE_OUT_OF_SERVICE, TEST_SUB_ID);

        verify(mNotificationManager, never()).notify(any(), anyInt(), any());
    }

    @Test
    public void testUpdateNetworkSelection_worldMode_userSetLTE_notificationNotSent() {
        prepareResourcesForNetworkSelection();

        PersistableBundle config = new PersistableBundle();
        config.putBoolean(CarrierConfigManager.KEY_OPERATOR_SELECTION_EXPAND_BOOL, true);
        config.putBoolean(CarrierConfigManager.KEY_HIDE_CARRIER_NETWORK_SETTINGS_BOOL, false);
        config.putBoolean(CarrierConfigManager.KEY_CSP_ENABLED_BOOL, false);
        config.putBoolean(CarrierConfigManager.KEY_WORLD_PHONE_BOOL, true);

        // World mode is on
        config.putBoolean(CarrierConfigManager.KEY_WORLD_MODE_ENABLED_BOOL, true);
        // User set Network mode as LTE
        when(mTelephonyManager.getAllowedNetworkTypesForReason(
                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER)).thenReturn(
                (long) (RAF_LTE | RAF_LTE_CA | RAF_IS95A | RAF_IS95B | RAF_1xRTT | RAF_EVDO_0
                        | RAF_EVDO_A | RAF_EVDO_B | RAF_EHRPD));
        when(mCarrierConfigManager.getConfigForSubId(TEST_SUB_ID)).thenReturn(config);

        mNotificationMgr.updateNetworkSelection(ServiceState.STATE_OUT_OF_SERVICE, TEST_SUB_ID);
        moveTimeForward(10 /* seconds */);
        mNotificationMgr.updateNetworkSelection(ServiceState.STATE_OUT_OF_SERVICE, TEST_SUB_ID);

        verify(mNotificationManager, never()).notify(any(), anyInt(), any());
    }

    @Test
    public void testUpdateNetworkSelection_worldMode_userSetTDSCDMA_notSupported_notifNotSent() {
        prepareResourcesForNetworkSelection();

        PersistableBundle config = new PersistableBundle();
        config.putBoolean(CarrierConfigManager.KEY_OPERATOR_SELECTION_EXPAND_BOOL, true);
        config.putBoolean(CarrierConfigManager.KEY_HIDE_CARRIER_NETWORK_SETTINGS_BOOL, false);
        config.putBoolean(CarrierConfigManager.KEY_CSP_ENABLED_BOOL, false);
        config.putBoolean(CarrierConfigManager.KEY_WORLD_PHONE_BOOL, true);

        // World mode is on
        config.putBoolean(CarrierConfigManager.KEY_WORLD_MODE_ENABLED_BOOL, true);
        // User set Network mode as NETWORK_MODE_LTE_TDSCDMA_GSM
        when(mTelephonyManager.getAllowedNetworkTypesForReason(
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER)).thenReturn(
                (long) (RAF_LTE | RAF_LTE_CA | RAF_TD_SCDMA | RAF_GSM | RAF_GPRS | RAF_EDGE));
        // But TDSCDMA is NOT supported
        config.putBoolean(CarrierConfigManager.KEY_SUPPORT_TDSCDMA_BOOL, false);
        when(mCarrierConfigManager.getConfigForSubId(TEST_SUB_ID)).thenReturn(config);

        mNotificationMgr.updateNetworkSelection(ServiceState.STATE_OUT_OF_SERVICE, TEST_SUB_ID);
        moveTimeForward(10 /* seconds */);
        mNotificationMgr.updateNetworkSelection(ServiceState.STATE_OUT_OF_SERVICE, TEST_SUB_ID);

        verify(mNotificationManager, never()).notify(any(), anyInt(), any());
    }

    @Test
    public void testUpdateNetworkSelection_worldMode_userSetWCDMA_notificationSent() {
        prepareResourcesForNetworkSelection();

        PersistableBundle config = new PersistableBundle();
        config.putBoolean(CarrierConfigManager.KEY_OPERATOR_SELECTION_EXPAND_BOOL, true);
        config.putBoolean(CarrierConfigManager.KEY_HIDE_CARRIER_NETWORK_SETTINGS_BOOL, false);
        config.putBoolean(CarrierConfigManager.KEY_CSP_ENABLED_BOOL, false);
        config.putBoolean(CarrierConfigManager.KEY_WORLD_PHONE_BOOL, true);

        // World mode is on
        config.putBoolean(CarrierConfigManager.KEY_WORLD_MODE_ENABLED_BOOL, true);
        // User set Network mode as NETWORK_MODE_LTE_TDSCDMA_GSM
        when(mTelephonyManager.getAllowedNetworkTypesForReason(
                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER)).thenReturn(
                (long) (RAF_LTE | RAF_LTE_CA | RAF_GSM | RAF_GPRS | RAF_EDGE | RAF_HSUPA | RAF_HSDPA
                        | RAF_HSPA | RAF_HSPAP | RAF_UMTS));
        // But TDSCDMA is NOT supported
        config.putBoolean(CarrierConfigManager.KEY_SUPPORT_TDSCDMA_BOOL, false);
        when(mCarrierConfigManager.getConfigForSubId(TEST_SUB_ID)).thenReturn(config);

        mNotificationMgr.updateNetworkSelection(ServiceState.STATE_OUT_OF_SERVICE, TEST_SUB_ID);
        moveTimeForward(10 /* seconds */);
        mNotificationMgr.updateNetworkSelection(ServiceState.STATE_OUT_OF_SERVICE, TEST_SUB_ID);

        verifyNotificationSentWithChannelId(NotificationChannelController.CHANNEL_ID_ALERT);
    }

    @Test
    public void testUpdateNetworkSelection_worldPhone_networkSelectionNotHide_notificationSent() {
        prepareResourcesForNetworkSelection();

        PersistableBundle config = new PersistableBundle();
        config.putBoolean(CarrierConfigManager.KEY_OPERATOR_SELECTION_EXPAND_BOOL, true);
        config.putBoolean(CarrierConfigManager.KEY_HIDE_CARRIER_NETWORK_SETTINGS_BOOL, false);
        config.putBoolean(CarrierConfigManager.KEY_CSP_ENABLED_BOOL, false);
        // World mode is off
        config.putBoolean(CarrierConfigManager.KEY_WORLD_MODE_ENABLED_BOOL, false);
        // World phone is on
        config.putBoolean(CarrierConfigManager.KEY_WORLD_PHONE_BOOL, true);
        when(mCarrierConfigManager.getConfigForSubId(TEST_SUB_ID)).thenReturn(config);

        mNotificationMgr.updateNetworkSelection(ServiceState.STATE_OUT_OF_SERVICE, TEST_SUB_ID);
        moveTimeForward(10 /* seconds */);
        mNotificationMgr.updateNetworkSelection(ServiceState.STATE_OUT_OF_SERVICE, TEST_SUB_ID);

        verifyNotificationSentWithChannelId(NotificationChannelController.CHANNEL_ID_ALERT);
    }

    @Test
    public void testUpdateNetworkSelection_gsmBasicOptionOn_notificationSent() {
        prepareResourcesForNetworkSelection();

        PersistableBundle config = new PersistableBundle();
        config.putBoolean(CarrierConfigManager.KEY_OPERATOR_SELECTION_EXPAND_BOOL, true);
        config.putBoolean(CarrierConfigManager.KEY_HIDE_CARRIER_NETWORK_SETTINGS_BOOL, false);
        config.putBoolean(CarrierConfigManager.KEY_CSP_ENABLED_BOOL, false);
        // World phone is on
        config.putBoolean(CarrierConfigManager.KEY_WORLD_PHONE_BOOL, true);
        // World mode is off
        config.putBoolean(CarrierConfigManager.KEY_WORLD_MODE_ENABLED_BOOL, false);
        when(mTelephonyManager.getPhoneType()).thenReturn(TelephonyManager.PHONE_TYPE_GSM);
        when(mCarrierConfigManager.getConfigForSubId(TEST_SUB_ID)).thenReturn(config);

        mNotificationMgr.updateNetworkSelection(ServiceState.STATE_OUT_OF_SERVICE, TEST_SUB_ID);
        moveTimeForward(10 /* seconds */);
        mNotificationMgr.updateNetworkSelection(ServiceState.STATE_OUT_OF_SERVICE, TEST_SUB_ID);

        verifyNotificationSentWithChannelId(NotificationChannelController.CHANNEL_ID_ALERT);
    }

    @Test
    public void testUpdateNetworkSelection_gsmBasicOptionOff_notificationNotSent() {
        prepareResourcesForNetworkSelection();

        PersistableBundle config = new PersistableBundle();
        config.putBoolean(CarrierConfigManager.KEY_OPERATOR_SELECTION_EXPAND_BOOL, true);
        config.putBoolean(CarrierConfigManager.KEY_HIDE_CARRIER_NETWORK_SETTINGS_BOOL, false);
        config.putBoolean(CarrierConfigManager.KEY_CSP_ENABLED_BOOL, false);
        // World mode is off
        config.putBoolean(CarrierConfigManager.KEY_WORLD_MODE_ENABLED_BOOL, false);
        when(mCarrierConfigManager.getConfigForSubId(TEST_SUB_ID)).thenReturn(config);
        when(mTelephonyManager.getPhoneType()).thenReturn(TelephonyManager.PHONE_TYPE_CDMA);

        mNotificationMgr.updateNetworkSelection(ServiceState.STATE_OUT_OF_SERVICE, TEST_SUB_ID);
        moveTimeForward(10 /* seconds */);
        mNotificationMgr.updateNetworkSelection(ServiceState.STATE_OUT_OF_SERVICE, TEST_SUB_ID);

        verify(mNotificationManager, never()).notify(any(), anyInt(), any());
    }

    @Test
    public void testShowLimitedSimFunctionWarningNotification_forTheFirstTime_notificationSent() {
        when(mResources.getText(R.string.limited_sim_function_notification_message)).thenReturn(
                CARRIER_NAME);
        when(mResources.getText(
                R.string.limited_sim_function_with_phone_num_notification_message)).thenReturn(
                "123");

        mNotificationMgr.showLimitedSimFunctionWarningNotification(TEST_SUB_ID, CARRIER_NAME);

        verifyNotificationSentWithChannelId(
                NotificationChannelController.CHANNEL_ID_SIM_HIGH_PRIORITY);
    }

    @Test
    public void
            testShowLimitedSimFunctionWarningNotification_consecutiveCall_notificationSentOnce() {
        when(mResources.getText(R.string.limited_sim_function_notification_message)).thenReturn(
                CARRIER_NAME);
        when(mResources.getText(
                R.string.limited_sim_function_with_phone_num_notification_message)).thenReturn(
                "123");

        // Call the method TWICE with the same subscription
        mNotificationMgr.showLimitedSimFunctionWarningNotification(TEST_SUB_ID, CARRIER_NAME);
        mNotificationMgr.showLimitedSimFunctionWarningNotification(TEST_SUB_ID, CARRIER_NAME);

        // Verify the notification is only sent ONCE
        verifyNotificationSentWithChannelId(
                NotificationChannelController.CHANNEL_ID_SIM_HIGH_PRIORITY);
    }

    @Test
    public void testDismissLimitedSimFunctionWarningNotification_noShowCalledBefore_noCancelSent() {
        // showLimitedSimFunctionWarningNotification was never called before

        mNotificationMgr.dismissLimitedSimFunctionWarningNotification(TEST_SUB_ID);

        verify(mNotificationManager, never()).cancel(any(), anyInt());
    }

    @Test
    public void testDismissLimitedSimFunctionWarningNotification_showCalledBefore_cancelSent() {
        when(mResources.getText(R.string.limited_sim_function_notification_message)).thenReturn(
                CARRIER_NAME);
        when(mResources.getText(
                R.string.limited_sim_function_with_phone_num_notification_message)).thenReturn(
                "123");
        mNotificationMgr.showLimitedSimFunctionWarningNotification(TEST_SUB_ID, CARRIER_NAME);

        mNotificationMgr.dismissLimitedSimFunctionWarningNotification(TEST_SUB_ID);

        verify(mNotificationManager).cancel(any(), eq(LIMITED_SIM_FUNCTION_NOTIFICATION));
    }

    private ApplicationInfo buildApplicationInfo(int targetSdkVersion) {
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.targetSdkVersion = targetSdkVersion;
        return applicationInfo;
    }

    private void verifyNotificationSentWithChannelId(String expectedNotificationChannelId) {
        ArgumentCaptor<Notification> notificationArgumentCaptor = ArgumentCaptor.forClass(
                Notification.class);
        verify(mNotificationManager).notify(any(), anyInt(), notificationArgumentCaptor.capture());
        Notification capturedNotification = notificationArgumentCaptor.getAllValues().get(0);
        assertThat(capturedNotification.getChannelId()).isEqualTo(expectedNotificationChannelId);
    }

    private void prepareResourcesForNetworkSelection() {
        when(mSharedPreferences.getString(Phone.NETWORK_SELECTION_NAME_KEY + TEST_SUB_ID,
                "")).thenReturn(TEST_SELECTED_NETWORK_OPERATOR_NAME);
        when(mResources.getBoolean(
                com.android.internal.R.bool.skip_restoring_network_selection)).thenReturn(false);
        when(mServiceState.getState()).thenReturn(ServiceState.STATE_OUT_OF_SERVICE);
        when(mApp.getString(R.string.mobile_network_settings_package)).thenReturn(
                MOBILE_NETWORK_SELECTION_PACKAGE);
        when(mApp.getString(R.string.mobile_network_settings_class)).thenReturn(
                MOBILE_NETWORK_SELECTION_CLASS);
        when(mSubscriptionManager.isActiveSubId(anyInt())).thenReturn(true);
    }

    private void moveTimeForward(long seconds) {
        final long millis = TimeUnit.SECONDS.toMillis(seconds);
        mTestableLooper.moveTimeForward(millis);
        mTestableLooper.processAllMessages();
        doReturn(SystemClock.elapsedRealtime() + millis).when(mNotificationMgr).getTimeStamp();
    }
}
