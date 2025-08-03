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

package com.android.imsserviceentitlement;

import static android.telephony.TelephonyManager.SIM_STATE_LOADED;
import static android.telephony.TelephonyManager.SIM_STATE_PIN_REQUIRED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PersistableBundle;
import android.os.UserManager;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.android.imsserviceentitlement.entitlement.EntitlementConfiguration;
import com.android.imsserviceentitlement.job.JobManager;
import com.android.imsserviceentitlement.utils.Executors;
import com.android.imsserviceentitlement.utils.TelephonyUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.lang.reflect.Field;

@RunWith(AndroidJUnit4.class)
public class ImsEntitlementReceiverTest {
    private static final int SUB_ID = 1;
    private static final int LAST_SUB_ID = 2;
    private static final String RAW_XML =
            "<wap-provisioningdoc version=\"1.1\">\n"
                    + "    <characteristic type=\"APPLICATION\">\n"
                    + "        <parm name=\"AppID\" value=\"ap2004\"/>\n"
                    + "        <parm name=\"EntitlementStatus\" value=\"1\"/>\n"
                    + "    </characteristic>\n"
                    + "</wap-provisioningdoc>\n";

    private static final String RAW_XML_VERSION_0_VALIDITY_0 =
            "<wap-provisioningdoc version=\"1.1\">\n"
                    + "    <characteristic type=\"VERS\">\n"
                    + "        <parm name=\"version\" value=\"0\"/>\n"
                    + "        <parm name=\"validity\" value=\"0\"/>\n"
                    + "    </characteristic>\n"
                    + "</wap-provisioningdoc>\n";

    private static final String RAW_XML_INVALID_VERS =
            "<wap-provisioningdoc version=\"1.1\">\n"
                    + "    <characteristic type=\"VERS\">\n"
                    + "        <parm name=\"version\" value=\"-1\"/>\n"
                    + "        <parm name=\"validity\" value=\"-1\"/>\n"
                    + "    </characteristic>\n"
                    + "</wap-provisioningdoc>\n";
    private static final ComponentName POLLING_SERVICE_COMPONENT_NAME =
            ComponentName.unflattenFromString(
                    "com.android.imsserviceentitlement/.ImsEntitlementPollingService");

    @Rule public final MockitoRule rule = MockitoJUnit.rule();

    @Mock private TelephonyUtils mMockTelephonyUtils;
    @Mock private UserManager mMockUserManager;
    @Mock private CarrierConfigManager mCarrierConfigManager;
    @Mock private JobManager mMockJobManager;

    @Spy private final Context mContext = ApplicationProvider.getApplicationContext();

    private ImsEntitlementReceiver mReceiver;
    private boolean mIsBootUp;

    @Before
    public void setUp() throws Exception {
        mReceiver = new ImsEntitlementReceiver() {
            @Override
            protected Dependencies createDependency(Context context, int subId) {
                Dependencies dependencies = new Dependencies();
                dependencies.userManager = mMockUserManager;
                dependencies.telephonyUtils = mMockTelephonyUtils;
                dependencies.jobManager = mMockJobManager;
                return dependencies;
            }

            @Override
            protected boolean isBootUp(Context context, int slotId) {
                return mIsBootUp;
            }
        };
        mIsBootUp = false;

        new EntitlementConfiguration(mContext, LAST_SUB_ID).update(RAW_XML);
        new EntitlementConfiguration(mContext, SUB_ID).reset();

        when(mMockUserManager.isSystemUser()).thenReturn(true);
        when(mMockTelephonyUtils.getSimApplicationState()).thenReturn(SIM_STATE_LOADED);

        setLastSubId(LAST_SUB_ID, 0);
        setupCarrierConfig();
        useDirectExecutor();
    }

    @Test
    public void onReceive_simChanged_dataReset() {
        mReceiver.onReceive(mContext, getCarrierConfigChangedIntent(SUB_ID, /* slotId= */ 0));

        assertThat(
                new EntitlementConfiguration(mContext, LAST_SUB_ID).getVoWifiStatus()).isEqualTo(2);
        verify(mMockJobManager, times(1)).queryEntitlementStatusOnceNetworkReady();
    }

    @Test
    public void onReceive_simChanged_simPinLockedThenLoaded() {
        // SIM PIN locked
        when(mMockTelephonyUtils.getSimApplicationState()).thenReturn(SIM_STATE_PIN_REQUIRED);

        mReceiver.onReceive(mContext, getCarrierConfigChangedIntent(SUB_ID, /* slotId= */ 0));

        // no-op
        assertThat(
                new EntitlementConfiguration(mContext, LAST_SUB_ID).getVoWifiStatus()).isEqualTo(1);
        verify(mMockJobManager, never()).queryEntitlementStatusOnceNetworkReady();

        // SIM LOADED
        when(mMockTelephonyUtils.getSimApplicationState()).thenReturn(SIM_STATE_LOADED);

        mReceiver.onReceive(mContext, getCarrierConfigChangedIntent(SUB_ID, /* slotId= */ 0));

        // configuration reset and entitlement query scheduled.
        assertThat(
                new EntitlementConfiguration(mContext, LAST_SUB_ID).getVoWifiStatus()).isEqualTo(2);
        verify(mMockJobManager, times(1)).queryEntitlementStatusOnceNetworkReady();
    }

    @Test
    public void onReceive_theSameSim_dataNotReset() {
        mReceiver.onReceive(
                mContext, getCarrierConfigChangedIntent(LAST_SUB_ID, /* slotId= */ 0));

        assertThat(
                new EntitlementConfiguration(mContext, LAST_SUB_ID).getVoWifiStatus()).isEqualTo(1);
        verify(mMockJobManager, never()).queryEntitlementStatusOnceNetworkReady();
    }

    @Test
    public void onReceive_differentSlot_dataNotReset() {
        setLastSubId(LAST_SUB_ID, 1);

        mReceiver.onReceive(
                mContext, getCarrierConfigChangedIntent(LAST_SUB_ID, /* slotId= */ 1));

        assertThat(
                new EntitlementConfiguration(mContext, LAST_SUB_ID).getVoWifiStatus()).isEqualTo(1);
        verify(mMockJobManager, never()).queryEntitlementStatusOnceNetworkReady();
    }

    @Test
    public void onReceive_simChangedAndDifferentSlotId_dataReset() {
        setLastSubId(LAST_SUB_ID, 1);

        mReceiver.onReceive(mContext, getCarrierConfigChangedIntent(SUB_ID, /* slotId= */ 1));

        assertThat(
                new EntitlementConfiguration(mContext, LAST_SUB_ID).getVoWifiStatus()).isEqualTo(2);
        verify(mMockJobManager).queryEntitlementStatusOnceNetworkReady();
    }

    @Test
    public void onReceive_isSystemUser_jobScheduled() {
        mReceiver.onReceive(
                mContext, getCarrierConfigChangedIntent(SUB_ID, /* slotId= */ 0));

        verify(mMockJobManager).queryEntitlementStatusOnceNetworkReady();
    }

    @Test
    public void onReceive_notSystemUser_noJobScheduled() {
        ImsEntitlementReceiver receiver = new ImsEntitlementReceiver();

        receiver.onReceive(
                mContext, getCarrierConfigChangedIntent(SUB_ID, /* slotId= */ 0));

        verify(mMockJobManager, never()).queryEntitlementStatusOnceNetworkReady();
    }

    @Test
    public void onReceive_deviceBootUp_jobScheduled() {
        new EntitlementConfiguration(mContext, LAST_SUB_ID).update(RAW_XML_VERSION_0_VALIDITY_0);
        mIsBootUp = true;

        mReceiver.onReceive(mContext, getCarrierConfigChangedIntent(LAST_SUB_ID, /* slotId= */ 0));

        verify(mMockJobManager).queryEntitlementStatusOnceNetworkReady();
    }

    @Test
    public void onReceive_bootCompleteInvalidVers_noJobScheduled() {
        new EntitlementConfiguration(mContext, LAST_SUB_ID).update(RAW_XML_INVALID_VERS);
        mIsBootUp = true;

        mReceiver.onReceive(mContext, getCarrierConfigChangedIntent(LAST_SUB_ID, /* slotId= */ 0));

        verify(mMockJobManager, never()).queryEntitlementStatusOnceNetworkReady();
    }

    @Test
    public void onReceive_invalidSubId_noJobScheduled() {
        mReceiver.onReceive(mContext,
                getCarrierConfigChangedIntent(SubscriptionManager.INVALID_SUBSCRIPTION_ID, 0));

        verify(mMockJobManager, never()).queryEntitlementStatusOnceNetworkReady();
    }

    @Test
    public void isBootUp_compareWithLastBootCount_returnResult() {
        int currentBootCount =
                Settings.Global.getInt(
                        mContext.getContentResolver(), Settings.Global.BOOT_COUNT, /* def= */ -1);
        setLastBootCount(/* slotId= */ 0, /* count=*/ currentBootCount);
        setLastBootCount(/* slotId= */ 1, /* count=*/ currentBootCount - 1);
        ImsEntitlementReceiver receiver = new ImsEntitlementReceiver();

        assertThat(receiver.isBootUp(mContext, 0)).isFalse();
        assertThat(receiver.isBootUp(mContext, 1)).isTrue();
    }

    private Intent getCarrierConfigChangedIntent(int subId, int slotId) {
        Intent intent = new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        intent.putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, subId);
        intent.putExtra(SubscriptionManager.EXTRA_SLOT_INDEX, slotId);
        return intent;
    }

    private void setupCarrierConfig() {
        PersistableBundle carrierConfig = new PersistableBundle();
        carrierConfig.putBoolean(
                CarrierConfigManager.ImsServiceEntitlement.KEY_IMS_PROVISIONING_BOOL, true);
        when(mContext.getSystemService(CarrierConfigManager.class))
                .thenReturn(mCarrierConfigManager);
        when(mCarrierConfigManager.getConfigForSubId(SUB_ID)).thenReturn(carrierConfig);
        when(mCarrierConfigManager.getConfigForSubId(LAST_SUB_ID)).thenReturn(carrierConfig);
    }

    private void setLastSubId(int subId, int slotId) {
        SharedPreferences preferences =
                mContext.getSharedPreferences("PREFERENCE_ACTIVATION_INFO", Context.MODE_PRIVATE);
        preferences.edit().putInt("last_sub_id_" + slotId, subId).apply();
    }

    private void setLastBootCount(int slotId, int count) {
        SharedPreferences preferences =
                mContext.getSharedPreferences("PREFERENCE_ACTIVATION_INFO", Context.MODE_PRIVATE);
        preferences.edit().putInt("last_boot_count_" + slotId, count).apply();
    }

    private void useDirectExecutor() throws Exception {
        Field field = Executors.class.getDeclaredField("sUseDirectExecutorForTest");
        field.setAccessible(true);
        field.set(null, true);
    }
}
