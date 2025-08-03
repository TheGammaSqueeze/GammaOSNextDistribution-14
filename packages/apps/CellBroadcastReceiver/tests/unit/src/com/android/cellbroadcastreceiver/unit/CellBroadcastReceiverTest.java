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

package com.android.cellbroadcastreceiver.unit;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.ContentResolver;
import android.content.Context;
import android.content.IContentProvider;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.media.AudioDeviceInfo;
import android.os.RemoteException;
import android.os.UserManager;
import android.provider.Telephony;
import android.telephony.CarrierConfigManager;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaSmsCbProgramData;

import com.android.cellbroadcastreceiver.CellBroadcastAlertService;
import com.android.cellbroadcastreceiver.CellBroadcastListActivity;
import com.android.cellbroadcastreceiver.CellBroadcastReceiver;
import com.android.cellbroadcastreceiver.CellBroadcastSettings;
import com.android.cellbroadcastreceiver.R;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CellBroadcastReceiverTest extends CellBroadcastTest {
    private static final long MAX_INIT_WAIT_MS = 5000;

    private static final String[] MCC_TABLE = {
        "gr:202", "nL:204", "Be:206", "US:310"
    };

    CellBroadcastReceiver mCellBroadcastReceiver;
    String mPackageName = "testPackageName";

    @Mock
    UserManager mUserManager;
    @Mock
    Intent mIntent;
    @Mock
    PackageManager mPackageManager;
    @Mock
    PackageInfo mPackageInfo;
    @Mock
    ContentResolver mContentResolver;
    @Mock
    IContentProvider mContentProviderClient;
    @Mock
    TelephonyManager mMockTelephonyManager;
    @Mock
    SubscriptionManager mSubscriptionManager;
    FakeSharedPreferences mFakeSharedPreferences = new FakeSharedPreferences();

    private Configuration mConfiguration = new Configuration();
    private AudioDeviceInfo[] mDevices = new AudioDeviceInfo[0];
    private Object mLock = new Object();
    private boolean mReady;

    protected void waitUntilReady() {
        synchronized (mLock) {
            if (!mReady) {
                try {
                    mLock.wait(MAX_INIT_WAIT_MS);
                } catch (InterruptedException ie) {
                }

                if (!mReady) {
                    Assert.fail("Telephony tests failed to initialize");
                }
            }
        }
    }

    protected void setReady(boolean ready) {
        synchronized (mLock) {
            mReady = ready;
            mLock.notifyAll();
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(this.getClass().getSimpleName());
        doReturn(mConfiguration).when(mResources).getConfiguration();
        doReturn(MCC_TABLE).when(mResources).getStringArray(R.array.iso_country_code_mcc_table);
        mCellBroadcastReceiver = spy(new CellBroadcastReceiver());
        doReturn(mResources).when(mCellBroadcastReceiver).getResourcesMethod();
        doNothing().when(mCellBroadcastReceiver).startConfigServiceToEnableChannels();
        doReturn(mContext).when(mContext).getApplicationContext();
        doReturn(mPackageName).when(mContext).getPackageName();
        doReturn(mFakeSharedPreferences).when(mContext).getSharedPreferences(anyString(), anyInt());
        doReturn(mUserManager).when(mContext).getSystemService(Context.USER_SERVICE);
        doReturn(false).when(mUserManager).isSystemUser();
        setContext();
    }

    @Test
    public void testOnReceive_actionCarrierConfigChanged() {
        doReturn(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED).when(mIntent).getAction();
        doNothing().when(mCellBroadcastReceiver).enableLauncher();
        mCellBroadcastReceiver.onReceive(mContext, mIntent);
        verify(mCellBroadcastReceiver).initializeSharedPreference(any(), anyInt());
        verify(mCellBroadcastReceiver).startConfigServiceToEnableChannels();
        verify(mCellBroadcastReceiver).enableLauncher();
        verify(mCellBroadcastReceiver).resetCellBroadcastChannelRanges();
    }

    @Test
    public void testOnReceive_actionCarrierConfigChangedOnRebroadcast() {
        doReturn(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED).when(mIntent).getAction();
        doReturn(true).when(mIntent)
                .getBooleanExtra("android.telephony.extra.REBROADCAST_ON_UNLOCK", false);
        mCellBroadcastReceiver.onReceive(mContext, mIntent);
        verify(mCellBroadcastReceiver, never()).initializeSharedPreference(any(), anyInt());
        verify(mCellBroadcastReceiver, never()).startConfigServiceToEnableChannels();
        verify(mCellBroadcastReceiver, never()).enableLauncher();
    }

    @Test
    public void testOnReceive_actionBootCompleted() {
        doReturn(mContentResolver).when(mContext).getContentResolver();
        doReturn(mContentProviderClient).when(mContentResolver).acquireContentProviderClient(
                "cellbroadcasts-app");
        doReturn(Intent.ACTION_BOOT_COMPLETED).when(mIntent).getAction();
        mCellBroadcastReceiver.onReceive(mContext, mIntent);
    }

    @Test
    public void testOnReceive_cellbroadcastStartConfigAction() {
        doReturn(CellBroadcastReceiver.CELLBROADCAST_START_CONFIG_ACTION).when(mIntent).getAction();
        mCellBroadcastReceiver.onReceive(mContext, mIntent);

        verify(mCellBroadcastReceiver, never()).initializeSharedPreference(any(), anyInt());
        verify(mCellBroadcastReceiver, never()).startConfigServiceToEnableChannels();
    }

    @Test
    public void testOnReceive_actionDefaultSmsSubscriptionChanged() {
        doReturn(SubscriptionManager.ACTION_DEFAULT_SMS_SUBSCRIPTION_CHANGED)
                .when(mIntent).getAction();
        doReturn(mUserManager).when(mContext).getSystemService(anyString());
        mCellBroadcastReceiver.onReceive(mContext, mIntent);
        verify(mCellBroadcastReceiver, never()).initializeSharedPreference(any(), anyInt());
        verify(mCellBroadcastReceiver).startConfigServiceToEnableChannels();

        doReturn(true).when(mCellBroadcastReceiver).isMockModemRunning();
        mCellBroadcastReceiver.onReceive(mContext, mIntent);
        verify(mCellBroadcastReceiver, times(1)).startConfigServiceToEnableChannels();

        doReturn(false).when(mCellBroadcastReceiver).isMockModemRunning();
        mCellBroadcastReceiver.onReceive(mContext, mIntent);
        verify(mCellBroadcastReceiver, times(2)).startConfigServiceToEnableChannels();
    }

    @Test
    public void testOnReceive_actionSmsEmergencyCbReceived() {
        doReturn(Telephony.Sms.Intents.ACTION_SMS_EMERGENCY_CB_RECEIVED).when(mIntent).getAction();
        doReturn(mIntent).when(mIntent).setClass(mContext, CellBroadcastAlertService.class);
        doReturn(null).when(mContext).startService(mIntent);

        mCellBroadcastReceiver.onReceive(mContext, mIntent);
        verify(mIntent).setClass(mContext, CellBroadcastAlertService.class);
        verify(mContext).startService(mIntent);
    }

    @Test
    public void testOnReceive_smsCbReceivedAction() {
        doReturn(Telephony.Sms.Intents.SMS_CB_RECEIVED_ACTION).when(mIntent).getAction();
        doReturn(mIntent).when(mIntent).setClass(mContext, CellBroadcastAlertService.class);
        doReturn(null).when(mContext).startService(any());

        mCellBroadcastReceiver.onReceive(mContext, mIntent);
        verify(mIntent).setClass(mContext, CellBroadcastAlertService.class);
        verify(mContext).startService(mIntent);
    }

    @Test
    public void testOnReceive_smsServiceCategoryProgramDataReceivedAction() {
        doReturn(Telephony.Sms.Intents.SMS_SERVICE_CATEGORY_PROGRAM_DATA_RECEIVED_ACTION)
                .when(mIntent).getAction();
        doReturn(null).when(mIntent).getParcelableArrayListExtra(anyString());

        mCellBroadcastReceiver.onReceive(mContext, mIntent);
        verify(mIntent).getParcelableArrayListExtra(anyString());
    }

    @Test
    public void testInitializeSharedPreference_ifSystemUser_invalidSub() throws RemoteException {
        doReturn("An invalid action").when(mIntent).getAction();
        doReturn(true).when(mUserManager).isSystemUser();
        doReturn(true).when(mCellBroadcastReceiver).sharedPrefsHaveDefaultValues();
        doNothing().when(mCellBroadcastReceiver).adjustReminderInterval();
        mockTelephonyManager();

        int subId = 1;
        // Not starting ConfigService, as default subId is valid and subId are invalid
        mockDefaultSubId(subId);
        mCellBroadcastReceiver.initializeSharedPreference(mContext,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        verify(mContext, never()).startService(any());
        verify(mCellBroadcastReceiver, never()).saveCarrierIdForDefaultSub(anyInt());

        // Not starting ConfigService, as both default subId and subId are invalid
        mockDefaultSubId(SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        mCellBroadcastReceiver.initializeSharedPreference(mContext,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        verify(mContext, never()).startService(any());
        verify(mCellBroadcastReceiver).saveCarrierIdForDefaultSub(anyInt());
    }

    private void mockTelephonyManager() {
        doReturn(mMockTelephonyManager).when(mMockTelephonyManager)
                .createForSubscriptionId(anyInt());
        doReturn(Context.TELEPHONY_SERVICE).when(mContext).getSystemServiceName(
                TelephonyManager.class);
        doReturn(mMockTelephonyManager).when(mContext).getSystemService(Context.TELEPHONY_SERVICE);
    }

    @Test
    public void testInitializeSharedPreference_ifSystemUser_firstSub() throws Exception {
        doReturn("An invalid action").when(mIntent).getAction();
        doReturn(true).when(mUserManager).isSystemUser();
        doReturn(true).when(mCellBroadcastReceiver).sharedPrefsHaveDefaultValues();
        doNothing().when(mCellBroadcastReceiver).adjustReminderInterval();
        mockTelephonyManager();

        int subId = 1;
        int otherSubId = 2;
        // The subId has to match default sub for it to take action.
        mockDefaultSubId(SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        mCellBroadcastReceiver.initializeSharedPreference(mContext, subId);
        verify(mContext, never()).startService(any());

        // Not starting ConfigService, not matching default subId.
        mockDefaultSubId(otherSubId);
        mCellBroadcastReceiver.initializeSharedPreference(mContext, subId);
        verify(mContext, never()).startService(any());

        // Not starting ConfigService, simCarrierId is UNKNOWN.
        mockDefaultSubId(subId);
        doReturn(TelephonyManager.UNKNOWN_CARRIER_ID).when(mMockTelephonyManager)
                .getSimCarrierId();
        mCellBroadcastReceiver.initializeSharedPreference(mContext, subId);
        verify(mContext, never()).startService(any());

        // Not starting ConfigService, as there was no previous carrierId.
        doReturn(subId).when(mMockTelephonyManager).getSimCarrierId();
        mCellBroadcastReceiver.initializeSharedPreference(mContext, subId);
        verify(mContext, never()).startService(any());
    }

    @Test
    public void testInitializeSharedPreference_ifSystemUser_carrierChange() throws Exception {
        doReturn("An invalid action").when(mIntent).getAction();
        doReturn(true).when(mUserManager).isSystemUser();
        doReturn(true).when(mCellBroadcastReceiver).sharedPrefsHaveDefaultValues();
        doNothing().when(mCellBroadcastReceiver).adjustReminderInterval();
        mockTelephonyManager();

        int firstSubId = 1;
        int secondSubId = 2;
        // Initialize for first sub.
        mockDefaultSubId(firstSubId);
        doReturn(firstSubId).when(mMockTelephonyManager).getSimCarrierId();
        mCellBroadcastReceiver.initializeSharedPreference(mContext, firstSubId);
        verify(mContext, never()).startService(any());

        // InitializeSharedPreference for second sub.
        // Starting ConfigService, as there's a carrierId change.
        mockDefaultSubId(secondSubId);
        doReturn(secondSubId).when(mMockTelephonyManager).getSimCarrierId();
        mCellBroadcastReceiver.initializeSharedPreference(mContext, secondSubId);
        verify(mContext).startService(any());

        // Initialize for first sub and starting ConfigService as same carrierId change.
        mockDefaultSubId(firstSubId);
        doReturn(secondSubId).when(mMockTelephonyManager).getSimCarrierId();
        mCellBroadcastReceiver.initializeSharedPreference(mContext, firstSubId);
        verify(mContext, times(2)).startService(any());
    }

    @Test
    public void testInitializeSharedPreference_ifNotSystemUser() {
        doReturn("An invalid action").when(mIntent).getAction();
        doReturn(false).when(mUserManager).isSystemUser();

        mCellBroadcastReceiver.initializeSharedPreference(any(), anyInt());
        assertThat(mFakeSharedPreferences.getValueCount()).isEqualTo(0);
    }

    @Test
    public void testMigrateSharedPreferenceFromLegacyWhenNoLegacyProvider() {
        doReturn(mContentResolver).when(mContext).getContentResolver();
        doReturn(null).when(mContentResolver).acquireContentProviderClient(
                Telephony.CellBroadcasts.AUTHORITY_LEGACY);

        mCellBroadcastReceiver.migrateSharedPreferenceFromLegacy();
        verify(mContext, never()).getSharedPreferences(anyString(), anyInt());
    }

    @Test
    public void testMigrateSharedPreferenceFromLegacyWhenBundleNull() throws RemoteException {
        doReturn(mContentResolver).when(mContext).getContentResolver();
        doReturn(mContentProviderClient).when(mContentResolver).acquireContentProviderClient(
                Telephony.CellBroadcasts.AUTHORITY_LEGACY);
        doReturn(null).when(mContentProviderClient).call(
                anyString(), anyString(), anyString(), any());

        mCellBroadcastReceiver.migrateSharedPreferenceFromLegacy();
        verify(mContext).getSharedPreferences(anyString(), anyInt());
        assertThat(mFakeSharedPreferences.getValueCount()).isEqualTo(0);
    }

    @Test
    public void testSetTestingMode() {
        assertThat(mCellBroadcastReceiver.isTestingMode(mContext)).isFalse();
        mCellBroadcastReceiver.setTestingMode(true);
        assertThat(mCellBroadcastReceiver.isTestingMode(mContext)).isTrue();
    }

    @Test
    public void testAdjustReminderInterval() {
        mFakeSharedPreferences.putString(CellBroadcastReceiver.CURRENT_INTERVAL_DEFAULT,
                "currentInterval");
        doReturn(mResources).when(mContext).getResources();
        doReturn(mContext).when(mContext).createConfigurationContext(any());
        doReturn(mSubscriptionManager).when(mContext).getSystemService(
                Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        doReturn("newInterval").when(mResources).getString(
                R.string.alert_reminder_interval_in_min_default);

        mCellBroadcastReceiver.adjustReminderInterval();
        assertThat(mFakeSharedPreferences.getString(
                CellBroadcastReceiver.CURRENT_INTERVAL_DEFAULT, ""))
                .isEqualTo("newInterval");
    }

    @Test
    public void testEnableLauncherIfNoLauncherActivity() throws
            PackageManager.NameNotFoundException {
        doReturn(mPackageManager).when(mContext).getPackageManager();
        doReturn(mPackageInfo).when(mPackageManager).getPackageInfo(anyString(), anyInt());

        ActivityInfo activityInfo = new ActivityInfo();
        String activityInfoName = "";
        activityInfo.targetActivity = CellBroadcastListActivity.class.getName();
        activityInfo.name = activityInfoName;
        ActivityInfo[] activityInfos = new ActivityInfo[1];
        activityInfos[0] = activityInfo;
        mPackageInfo.activities = activityInfos;

        mCellBroadcastReceiver.enableLauncher();
        verify(mPackageManager, never()).setComponentEnabledSetting(any(), anyInt(), anyInt());
    }

    @Test
    public void testEnableLauncherIfEnableTrue() throws PackageManager.NameNotFoundException {
        doReturn(mPackageManager).when(mContext).getPackageManager();
        doReturn(mPackageInfo).when(mPackageManager).getPackageInfo(anyString(), anyInt());
        doReturn(true).when(mResources)
                .getBoolean(R.bool.show_message_history_in_launcher);

        ActivityInfo activityInfo = new ActivityInfo();
        String activityInfoName = "testName";
        activityInfo.targetActivity = CellBroadcastListActivity.class.getName();
        activityInfo.name = activityInfoName;
        ActivityInfo[] activityInfos = new ActivityInfo[1];
        activityInfos[0] = activityInfo;
        mPackageInfo.activities = activityInfos;

        mCellBroadcastReceiver.enableLauncher();
        verify(mPackageManager).setComponentEnabledSetting(any(),
                eq(PackageManager.COMPONENT_ENABLED_STATE_ENABLED), anyInt());
    }

    @Test
    public void testEnableLauncherIfEnableFalse() throws PackageManager.NameNotFoundException {
        doReturn(mPackageManager).when(mContext).getPackageManager();
        doReturn(mPackageInfo).when(mPackageManager).getPackageInfo(anyString(), anyInt());
        doReturn(false).when(mResources)
                .getBoolean(R.bool.show_message_history_in_launcher);

        ActivityInfo activityInfo = new ActivityInfo();
        String activityInfoName = "testName";
        activityInfo.targetActivity = CellBroadcastListActivity.class.getName();
        activityInfo.name = activityInfoName;
        ActivityInfo[] activityInfos = new ActivityInfo[1];
        activityInfos[0] = activityInfo;
        mPackageInfo.activities = activityInfos;

        mCellBroadcastReceiver.enableLauncher();
        verify(mPackageManager).setComponentEnabledSetting(any(),
                eq(PackageManager.COMPONENT_ENABLED_STATE_DISABLED), anyInt());
    }

    @Test
    public void testTryCdmaSetCatergory() {
        boolean enable = true;

        mCellBroadcastReceiver.tryCdmaSetCategory(mContext,
                CdmaSmsCbProgramData.CATEGORY_CMAS_EXTREME_THREAT, enable);
        assertThat(mFakeSharedPreferences.getBoolean(
                CellBroadcastSettings.KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS, !enable))
                .isEqualTo(enable);

        mCellBroadcastReceiver.tryCdmaSetCategory(mContext,
                CdmaSmsCbProgramData.CATEGORY_CMAS_SEVERE_THREAT, enable);
        assertThat(mFakeSharedPreferences.getBoolean(
                CellBroadcastSettings.KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS, !enable))
                .isEqualTo(enable);

        mCellBroadcastReceiver.tryCdmaSetCategory(mContext,
                CdmaSmsCbProgramData.CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY, enable);
        assertThat(mFakeSharedPreferences.getBoolean(
                CellBroadcastSettings.KEY_ENABLE_CMAS_AMBER_ALERTS, !enable))
                .isEqualTo(enable);

        mCellBroadcastReceiver.tryCdmaSetCategory(mContext,
                CdmaSmsCbProgramData.CATEGORY_CMAS_TEST_MESSAGE, enable);
        assertThat(mFakeSharedPreferences.getBoolean(
                CellBroadcastSettings.KEY_ENABLE_TEST_ALERTS, !enable))
                .isEqualTo(enable);

        // set the not defined category
        FakeSharedPreferences mockSharedPreferences = spy(mFakeSharedPreferences);
        doReturn(mockSharedPreferences).when(mContext).getSharedPreferences(anyString(), anyInt());
        mCellBroadcastReceiver.tryCdmaSetCategory(mContext,
                CdmaSmsCbProgramData.CATEGORY_CMAS_LAST_RESERVED_VALUE + 1, enable);
        verify(mockSharedPreferences, never()).apply();
    }

    @Test
    public void testHandleCdmaSmsCbProgramDataOperationAddAndDelete() {
        CdmaSmsCbProgramData programData = new CdmaSmsCbProgramData(
                CdmaSmsCbProgramData.OPERATION_ADD_CATEGORY,
                CdmaSmsCbProgramData.CATEGORY_CMAS_EXTREME_THREAT,
                1, 1, 1, "catergoryName");
        mCellBroadcastReceiver.handleCdmaSmsCbProgramData(new ArrayList<>(List.of(programData)));
        verify(mCellBroadcastReceiver).tryCdmaSetCategory(mContext,
                CdmaSmsCbProgramData.CATEGORY_CMAS_EXTREME_THREAT, true);

        programData = new CdmaSmsCbProgramData(CdmaSmsCbProgramData.OPERATION_DELETE_CATEGORY,
                CdmaSmsCbProgramData.CATEGORY_CMAS_EXTREME_THREAT,
                1, 1, 1, "catergoryName");
        mCellBroadcastReceiver.handleCdmaSmsCbProgramData(new ArrayList<>(List.of(programData)));
        verify(mCellBroadcastReceiver).tryCdmaSetCategory(mContext,
                CdmaSmsCbProgramData.CATEGORY_CMAS_EXTREME_THREAT, false);
    }

    @Test
    public void testHandleCdmaSmsCbProgramDataOprationClear() {
        CdmaSmsCbProgramData programData = new CdmaSmsCbProgramData(
                CdmaSmsCbProgramData.OPERATION_CLEAR_CATEGORIES,
                CdmaSmsCbProgramData.CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT,
                1, 1, 1, "catergoryName");
        mCellBroadcastReceiver.handleCdmaSmsCbProgramData(new ArrayList<>(List.of(programData)));
        verify(mCellBroadcastReceiver).tryCdmaSetCategory(mContext,
                CdmaSmsCbProgramData.CATEGORY_CMAS_EXTREME_THREAT, false);
        verify(mCellBroadcastReceiver).tryCdmaSetCategory(mContext,
                CdmaSmsCbProgramData.CATEGORY_CMAS_SEVERE_THREAT, false);
        verify(mCellBroadcastReceiver).tryCdmaSetCategory(mContext,
                CdmaSmsCbProgramData.CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY, false);
        verify(mCellBroadcastReceiver).tryCdmaSetCategory(mContext,
                CdmaSmsCbProgramData.CATEGORY_CMAS_TEST_MESSAGE, false);
    }

    @Test
    public void testHandleCdmaSmsCbProgramDataNotDefinedOperation() {
        CdmaSmsCbProgramData programData = new CdmaSmsCbProgramData(
                -1, CdmaSmsCbProgramData.CATEGORY_CMAS_LAST_RESERVED_VALUE + 1, 1, 1, 1, "");
        mCellBroadcastReceiver.handleCdmaSmsCbProgramData(new ArrayList<>(List.of(programData)));
        verify(mCellBroadcastReceiver, never()).tryCdmaSetCategory(any(), anyInt(), anyBoolean());
    }

    //this method is just to assign mContext to the spied instance mCellBroadcastReceiver
    private void setContext() {
        doReturn("dummy action").when(mIntent).getAction();

        mCellBroadcastReceiver.onReceive(mContext, mIntent);
    }

    @Test
    public void testOnServiceStateChange() {
        mFakeSharedPreferences.putInt("service_state", ServiceState.STATE_OUT_OF_SERVICE);
        mFakeSharedPreferences.putString("roaming_operator_supported", "");
        mockTelephonyManager();
        doReturn("android.intent.action.SERVICE_STATE").when(mIntent).getAction();
        doReturn(ServiceState.STATE_IN_SERVICE).when(mIntent).getIntExtra(anyString(), anyInt());
        doReturn(false).when(mMockTelephonyManager).isNetworkRoaming();
        doReturn("123456").when(mMockTelephonyManager).getNetworkOperator();

        mCellBroadcastReceiver.onReceive(mContext, mIntent);

        verify(mCellBroadcastReceiver, never()).startConfigServiceToEnableChannels();
        assertThat(mFakeSharedPreferences.getInt("service_state", ServiceState.STATE_POWER_OFF))
                .isEqualTo(ServiceState.STATE_IN_SERVICE);

        mFakeSharedPreferences.putInt("service_state", ServiceState.STATE_POWER_OFF);

        mCellBroadcastReceiver.onReceive(mContext, mIntent);

        verify(mCellBroadcastReceiver).startConfigServiceToEnableChannels();
        assertThat(mFakeSharedPreferences.getInt("service_state", ServiceState.STATE_POWER_OFF))
                .isEqualTo(ServiceState.STATE_IN_SERVICE);

        doReturn(true).when(mCellBroadcastReceiver).isMockModemRunning();
        mFakeSharedPreferences.putInt("service_state", ServiceState.STATE_POWER_OFF);
        mCellBroadcastReceiver.onReceive(mContext, mIntent);
        verify(mCellBroadcastReceiver, times(1)).startConfigServiceToEnableChannels();

        doReturn(false).when(mCellBroadcastReceiver).isMockModemRunning();
        mFakeSharedPreferences.putInt("service_state", ServiceState.STATE_POWER_OFF);
        mCellBroadcastReceiver.onReceive(mContext, mIntent);
        verify(mCellBroadcastReceiver, times(2)).startConfigServiceToEnableChannels();
    }


    @Test
    public void testOnNetworkRoamingChange() {
        doReturn("").when(mCellBroadcastReceiver).getSystemProperties(any());

        mFakeSharedPreferences.putInt("service_state", ServiceState.STATE_IN_SERVICE);
        mFakeSharedPreferences.putString("roaming_operator_supported", "");
        mockTelephonyManager();
        doReturn("android.intent.action.SERVICE_STATE").when(mIntent).getAction();
        doReturn(ServiceState.STATE_IN_SERVICE).when(mIntent).getIntExtra(anyString(), anyInt());
        doReturn("123456").when(mMockTelephonyManager).getNetworkOperator();

        // not roaming, verify not to store the network operator, or call enable channel
        doReturn(false).when(mMockTelephonyManager).isNetworkRoaming();

        mCellBroadcastReceiver.onReceive(mContext, mIntent);

        verify(mCellBroadcastReceiver, never()).startConfigServiceToEnableChannels();
        assertThat(mFakeSharedPreferences.getString(
                "roaming_operator_supported", "123456")).isEqualTo("");

        // roaming and network operator changed with wild match, verify to
        // update the network operator, and call enable channel
        doReturn(true).when(mMockTelephonyManager).isNetworkRoaming();
        doReturn(new String[] {"XXXXXX"}).when(mResources).getStringArray(anyInt());
        doReturn("654321").when(mMockTelephonyManager).getSimOperator();

        mCellBroadcastReceiver.onReceive(mContext, mIntent);

        verify(mCellBroadcastReceiver, times(1)).startConfigServiceToEnableChannels();
        assertThat(mFakeSharedPreferences.getString(
                "roaming_operator_supported", "")).isEqualTo("123456");

        // roaming to home case, verify to call enable channel
        doReturn(false).when(mMockTelephonyManager).isNetworkRoaming();

        mCellBroadcastReceiver.onReceive(mContext, mIntent);

        verify(mCellBroadcastReceiver, times(2)).startConfigServiceToEnableChannels();
        assertThat(mFakeSharedPreferences.getString(
                "roaming_operator_supported", "123456")).isEqualTo("");

        // roaming and network operator changed with exact mcc match, verify to
        // update the network operator, and call enable channel
        doReturn(true).when(mMockTelephonyManager).isNetworkRoaming();
        doReturn(new String[] {"123"}).when(mResources).getStringArray(anyInt());
        doReturn("654321").when(mMockTelephonyManager).getSimOperator();

        mCellBroadcastReceiver.onReceive(mContext, mIntent);

        verify(mCellBroadcastReceiver, times(3)).startConfigServiceToEnableChannels();
        assertThat(mFakeSharedPreferences.getString(
                "roaming_operator_supported", "")).isEqualTo("123");

        // roaming to network operator with same mcc and configured as exact mcc match,
        // verify to update the network operator, but not call enable channel
        doReturn("123654").when(mMockTelephonyManager).getNetworkOperator();

        mCellBroadcastReceiver.onReceive(mContext, mIntent);

        verify(mCellBroadcastReceiver, times(3)).startConfigServiceToEnableChannels();
        assertThat(mFakeSharedPreferences.getString(
                "roaming_operator_supported", "")).isEqualTo("123");

        // roaming and network operator changed with exact match, verify to
        // update the network operator, and call enable channel
        doReturn(new String[] {"123456"}).when(mResources).getStringArray(anyInt());
        doReturn("123456").when(mMockTelephonyManager).getNetworkOperator();

        mCellBroadcastReceiver.onReceive(mContext, mIntent);

        verify(mCellBroadcastReceiver, times(4)).startConfigServiceToEnableChannels();
        assertThat(mFakeSharedPreferences.getString(
                "roaming_operator_supported", "")).isEqualTo("123456");

        // roaming to network operator with different mcc and configured as any mcc match,
        // verify to update the network operator, and call enable channel
        doReturn("321456").when(mMockTelephonyManager).getNetworkOperator();
        doReturn(new String[] {"XXX"}).when(mResources).getStringArray(anyInt());

        mCellBroadcastReceiver.onReceive(mContext, mIntent);

        verify(mCellBroadcastReceiver, times(5)).startConfigServiceToEnableChannels();
        assertThat(mFakeSharedPreferences.getString(
                "roaming_operator_supported", "")).isEqualTo("321");

        // roaming to network operator which does not match the configuration,
        // verify to update the network operator to empty, and call enable channel
        doReturn("321456").when(mMockTelephonyManager).getNetworkOperator();
        doReturn(new String[] {"123"}).when(mResources).getStringArray(anyInt());

        mCellBroadcastReceiver.onReceive(mContext, mIntent);

        verify(mCellBroadcastReceiver, times(6)).startConfigServiceToEnableChannels();
        assertThat(mFakeSharedPreferences.getString(
                "roaming_operator_supported", "321")).isEqualTo("");

        // roaming to network operator with different mcc and configured as any mcc match,
        // verify to update the network operator, and call enable channel
        doReturn("310240").when(mMockTelephonyManager).getNetworkOperator();
        doReturn("310260").when(mMockTelephonyManager).getSimOperator();
        doReturn(new String[] {"XXX"}).when(mResources).getStringArray(anyInt());

        mCellBroadcastReceiver.onReceive(mContext, mIntent);

        verify(mCellBroadcastReceiver, times(6)).startConfigServiceToEnableChannels();
        assertThat(mFakeSharedPreferences.getString(
                "roaming_operator_supported", "")).isEqualTo("");
    }

    @Test
    public void testOnSimlessChange() {
        doReturn("").when(mCellBroadcastReceiver).getSystemProperties(any());

        mFakeSharedPreferences.putInt("service_state", ServiceState.STATE_IN_SERVICE);
        mFakeSharedPreferences.putString("roaming_operator_supported", "");
        doReturn("Us").when(mMockTelephonyManager).getNetworkCountryIso();
        mockTelephonyManager();
        doReturn("android.intent.action.SERVICE_STATE").when(mIntent).getAction();
        doReturn(ServiceState.STATE_OUT_OF_SERVICE).when(mIntent)
                .getIntExtra(anyString(), anyInt());
        doReturn("").when(mMockTelephonyManager).getSimOperator();
        doReturn("").when(mMockTelephonyManager).getNetworkOperator();
        doReturn(false).when(mMockTelephonyManager).isNetworkRoaming();
        doReturn(new String[] {"XXX"}).when(mResources).getStringArray(anyInt());

        mCellBroadcastReceiver.onReceive(mContext, mIntent);

        // verify the roaming operator is set correctly for simless case
        verify(mCellBroadcastReceiver, times(1)).startConfigServiceToEnableChannels();
        assertThat(mFakeSharedPreferences.getString(
                "roaming_operator_supported", "")).isEqualTo("310");

        doReturn(ServiceState.STATE_OUT_OF_SERVICE).when(mIntent)
                .getIntExtra(anyString(), anyInt());
        doReturn("123456").when(mMockTelephonyManager).getSimOperator();
        doReturn("123456").when(mMockTelephonyManager).getNetworkOperator();

        mCellBroadcastReceiver.onReceive(mContext, mIntent);

        // verify the roaming operator is reset when sim loaded
        verify(mCellBroadcastReceiver, times(2)).startConfigServiceToEnableChannels();
        assertThat(mFakeSharedPreferences.getString(
                "roaming_operator_supported", "")).isEqualTo("");
    }

    @Test
    public void testResourceOnRoamingState() throws RemoteException {
        int subId = SubscriptionManager.DEFAULT_SUBSCRIPTION_ID;

        doReturn(subId).when(mSubService).getDefaultSubId();
        doReturn(subId).when(mSubService).getDefaultSmsSubId();

        SubscriptionInfo mockSubInfo = mock(SubscriptionInfo.class);
        doReturn(mockSubInfo).when(mSubscriptionManager).getActiveSubscriptionInfo(anyInt());
        Context newContext = mock(Context.class);
        Resources roamingResource = mock(Resources.class);
        doReturn(newContext).when(mContext).createConfigurationContext(any());
        doReturn(roamingResource).when(newContext).getResources();

        doReturn(false).when(mResources).getBoolean(R.bool.enable_led_flash);
        doReturn(true).when(roamingResource).getBoolean(R.bool.enable_led_flash);

        Resources res = CellBroadcastSettings.getResourcesByOperator(mContext, subId, "");
        assertFalse(res.getBoolean(R.bool.enable_led_flash));
        res = CellBroadcastSettings.getResourcesByOperator(mContext, subId, "530");
        assertTrue(res.getBoolean(R.bool.enable_led_flash));

        int[] mexico_vib_pattern = {0, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500,
                500, 500, 500, 500, 500};
        int[] normal_vib_pattern = {0, 2000, 500, 1000, 500, 1000, 500, 2000, 500, 1000, 500, 1000};

        doReturn(normal_vib_pattern).when(mResources)
                .getIntArray(R.array.default_vibration_pattern);
        doReturn(mexico_vib_pattern).when(roamingResource)
                .getIntArray(R.array.default_vibration_pattern);

        res = CellBroadcastSettings.getResourcesByOperator(mContext, subId, "");
        assertArrayEquals(res.getIntArray(R.array.default_vibration_pattern), normal_vib_pattern);
        mFakeSharedPreferences.putString("roaming_operator_supported", "334");
        res = CellBroadcastSettings.getResourcesByOperator(mContext, subId, "334");
        assertArrayEquals(res.getIntArray(R.array.default_vibration_pattern), mexico_vib_pattern);

        doReturn(false).when(mResources)
                .getBoolean(R.bool.mute_by_physical_button);
        doReturn(true).when(roamingResource)
                .getBoolean(R.bool.mute_by_physical_button);

        res = CellBroadcastSettings.getResourcesByOperator(mContext, subId, "");
        assertFalse(res.getBoolean(R.bool.mute_by_physical_button));
        res = CellBroadcastSettings.getResourcesByOperator(mContext, subId, "730");
        assertTrue(res.getBoolean(R.bool.mute_by_physical_button));
    }

    @Test
    public void testGetMccMap() {
        final String[] mccArray = new String[] {
            //valid values
            "gr:202", "nL:204", "Be:206", "US:310",
            //invalid values
            "aaa", "123", "aaa123", "aaa 123"
        };
        int validNum = 4;
        doReturn(mccArray).when(mResources).getStringArray(anyInt());

        Map<String, String> map = CellBroadcastReceiver.getMccMap(mResources);

        assertThat(map.size()).isEqualTo(validNum);
        // 2 times expected as it has been called in setup
        verify(mResources, times(2)).getStringArray(eq(R.array.iso_country_code_mcc_table));

        for (int i = 0; i < validNum; i++) {
            String[] values = mccArray[i].split(":");
            assertThat(map.get(values[0].toLowerCase())).isEqualTo(values[1]);
            assertThat(map.get(values[0].toUpperCase())).isEqualTo(null);
        }
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }
}
