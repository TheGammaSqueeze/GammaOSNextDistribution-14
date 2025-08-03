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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.Context;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.SparseArray;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.android.imsserviceentitlement.entitlement.EntitlementResult;
import com.android.imsserviceentitlement.job.JobManager;
import com.android.imsserviceentitlement.ts43.Ts43SmsOverIpStatus;
import com.android.imsserviceentitlement.ts43.Ts43VolteStatus;
import com.android.imsserviceentitlement.ts43.Ts43VowifiStatus;
import com.android.imsserviceentitlement.ts43.Ts43VowifiStatus.AddrStatus;
import com.android.imsserviceentitlement.ts43.Ts43VowifiStatus.EntitlementStatus;
import com.android.imsserviceentitlement.ts43.Ts43VowifiStatus.ProvStatus;
import com.android.imsserviceentitlement.ts43.Ts43VowifiStatus.TcStatus;
import com.android.imsserviceentitlement.utils.ImsUtils;

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
public class ImsEntitlementPollingServiceTest {
    @Rule public final MockitoRule rule = MockitoJUnit.rule();

    @Spy private Context mContext = ApplicationProvider.getApplicationContext();

    @Mock private ImsUtils mImsUtils;
    @Mock private JobParameters mJobParameters;
    @Mock private SubscriptionManager mSubscriptionManager;
    @Mock private SubscriptionInfo mSubscriptionInfo;
    @Mock private ImsEntitlementApi mImsEntitlementApi;
    @Mock private CarrierConfigManager mCarrierConfigManager;

    private ImsEntitlementPollingService mService;
    private JobScheduler mScheduler;

    private static final int SUB_ID = 1;
    private static final int SLOT_ID = 0;

    @Before
    public void setUp() throws Exception {
        mService = new ImsEntitlementPollingService();
        mService.attachBaseContext(mContext);
        mService.onCreate();
        mService.onBind(null);
        mService.injectImsEntitlementApi(mImsEntitlementApi);
        mScheduler = mContext.getSystemService(JobScheduler.class);
        setActivedSubscription();
        setupImsUtils();
        setJobParameters();
        setWfcEnabledByUser(true);
        setImsProvisioningBool(false);
    }

    @Test
    public void doEntitlementCheck_isWfcEnabledByUserFalse_doNothing() throws Exception {
        setWfcEnabledByUser(false);

        mService.onStartJob(mJobParameters);
        mService.mOngoingTask.get(); // wait for job finish.

        verify(mImsEntitlementApi, never()).checkEntitlementStatus();
    }


    @Test
    public void doEntitlementCheck_shouldTurnOffWfc_disableWfc() throws Exception {
        EntitlementResult entitlementResult = getEntitlementResult(sDisableVoWiFi);
        when(mImsEntitlementApi.checkEntitlementStatus()).thenReturn(entitlementResult);

        mService.onStartJob(mJobParameters);
        mService.mOngoingTask.get(); // wait for job finish.

        verify(mImsUtils).disableWfc();
    }

    @Test
    public void doEntitlementCheck_shouldNotTurnOffWfc_enableWfc() throws Exception {
        EntitlementResult entitlementResult = getEntitlementResult(sEnableVoWiFi);
        when(mImsEntitlementApi.checkEntitlementStatus()).thenReturn(entitlementResult);

        mService.onStartJob(mJobParameters);
        mService.mOngoingTask.get(); // wait for job finish.

        verify(mImsUtils, never()).disableWfc();
    }

    @Test
    public void doEntitlementCheck_shouldTurnOffImsApps_setAllProvisionedFalse() throws Exception {
        setImsProvisioningBool(true);
        EntitlementResult entitlementResult = getImsEntitlementResult(
                sDisableVoWiFi,
                sDisableVoLte,
                sDisableSmsoverip
        );
        when(mImsEntitlementApi.checkEntitlementStatus()).thenReturn(entitlementResult);

        mService.onStartJob(mJobParameters);
        mService.mOngoingTask.get(); // wait for job finish.

        verify(mImsUtils).setVolteProvisioned(false);
        verify(mImsUtils).setVowifiProvisioned(false);
        verify(mImsUtils).setSmsoipProvisioned(false);
    }

    @Test
    public void doEntitlementCheck_shouldTurnOnImsApps_setAllProvisionedTrue() throws Exception {
        setImsProvisioningBool(true);
        EntitlementResult entitlementResult = getImsEntitlementResult(
                sEnableVoWiFi,
                sEnableVoLte,
                sEnableSmsoverip
        );
        when(mImsEntitlementApi.checkEntitlementStatus()).thenReturn(entitlementResult);

        mService.onStartJob(mJobParameters);
        mService.mOngoingTask.get(); // wait for job finish.

        verify(mImsUtils).setVolteProvisioned(true);
        verify(mImsUtils).setVowifiProvisioned(true);
        verify(mImsUtils).setSmsoipProvisioned(true);
    }

    @Test
    public void doEntitlementCheck_ImsEntitlementShouldRetry_rescheduleJob() throws Exception {
        setImsProvisioningBool(true);
        EntitlementResult entitlementResult =
                EntitlementResult.builder().setRetryAfterSeconds(120).build();
        when(mImsEntitlementApi.checkEntitlementStatus()).thenReturn(entitlementResult);

        mService.onStartJob(mJobParameters);
        mService.mOngoingTask.get(); // wait for job finish.

        verify(mImsUtils, never()).setVolteProvisioned(anyBoolean());
        verify(mImsUtils, never()).setVowifiProvisioned(anyBoolean());
        verify(mImsUtils, never()).setSmsoipProvisioned(anyBoolean());
        assertThat(
                mScheduler.getPendingJob(
                        jobIdWithSubId(JobManager.QUERY_ENTITLEMENT_STATUS_JOB_ID, SUB_ID)))
                .isNotNull();
    }

    @Test
    public void doEntitlementCheck_WfcEntitlementShouldRetry_rescheduleJob() throws Exception {
        EntitlementResult entitlementResult =
                EntitlementResult.builder().setRetryAfterSeconds(120).build();
        when(mImsEntitlementApi.checkEntitlementStatus()).thenReturn(entitlementResult);

        mService.onStartJob(mJobParameters);
        mService.mOngoingTask.get(); // wait for job finish.

        verify(mImsUtils, never()).setVolteProvisioned(anyBoolean());
        verify(mImsUtils, never()).setVowifiProvisioned(anyBoolean());
        verify(mImsUtils, never()).setSmsoipProvisioned(anyBoolean());
        assertThat(
                mScheduler.getPendingJob(
                        jobIdWithSubId(JobManager.QUERY_ENTITLEMENT_STATUS_JOB_ID, SUB_ID)))
                .isNotNull();
    }

    @Test
    public void enqueueJob_hasJob() {
        ImsEntitlementPollingService.enqueueJob(mContext, SUB_ID, 0);

        assertThat(
                mScheduler.getPendingJob(
                        jobIdWithSubId(JobManager.QUERY_ENTITLEMENT_STATUS_JOB_ID, SUB_ID)))
                .isNotNull();
    }

    private void setActivedSubscription() {
        when(mSubscriptionInfo.getSimSlotIndex()).thenReturn(SLOT_ID);
        when(mSubscriptionManager.getActiveSubscriptionInfo(SUB_ID)).thenReturn(mSubscriptionInfo);
        when(mContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE))
                .thenReturn(mSubscriptionManager);
    }

    private void setupImsUtils() throws Exception {
        SparseArray<ImsUtils> imsUtilsInstances = new SparseArray<>();
        imsUtilsInstances.put(SUB_ID, mImsUtils);
        Field field = ImsUtils.class.getDeclaredField("sInstances");
        field.setAccessible(true);
        field.set(null, imsUtilsInstances);
    }

    private void setWfcEnabledByUser(boolean isEnabled) {
        when(mImsUtils.isWfcEnabledByUser()).thenReturn(isEnabled);
    }

    private void setJobParameters() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, SUB_ID);
        bundle.putInt(JobManager.EXTRA_SLOT_ID, SLOT_ID);
        when(mJobParameters.getExtras()).thenReturn(bundle);
        when(mJobParameters.getJobId()).thenReturn(JobManager.QUERY_ENTITLEMENT_STATUS_JOB_ID);
    }

    private void setImsProvisioningBool(boolean provisioning) {
        PersistableBundle carrierConfig = new PersistableBundle();
        carrierConfig.putBoolean(
                CarrierConfigManager.ImsServiceEntitlement.KEY_IMS_PROVISIONING_BOOL,
                provisioning
        );
        when(mCarrierConfigManager.getConfigForSubId(SUB_ID)).thenReturn(carrierConfig);
        when(mContext.getSystemService(CarrierConfigManager.class))
                .thenReturn(mCarrierConfigManager);
    }

    private static EntitlementResult getEntitlementResult(Ts43VowifiStatus vowifiStatus) {
        return EntitlementResult.builder()
                .setVowifiStatus(vowifiStatus)
                .build();
    }

    private static EntitlementResult getImsEntitlementResult(
            Ts43VowifiStatus vowifiStatus,
            Ts43VolteStatus volteStatus,
            Ts43SmsOverIpStatus smsOverIpStatus) {
        return EntitlementResult.builder()
                .setVowifiStatus(vowifiStatus)
                .setVolteStatus(volteStatus)
                .setSmsoveripStatus(smsOverIpStatus)
                .build();
    }

    private int jobIdWithSubId(int jobId, int subId) {
        return 1000 * subId + jobId;
    }

    private static final Ts43VowifiStatus sDisableVoWiFi =
            Ts43VowifiStatus.builder()
                    .setEntitlementStatus(EntitlementStatus.DISABLED)
                    .setTcStatus(TcStatus.NOT_AVAILABLE)
                    .setAddrStatus(AddrStatus.NOT_AVAILABLE)
                    .setProvStatus(ProvStatus.NOT_PROVISIONED)
                    .build();

    private static final Ts43VowifiStatus sEnableVoWiFi =
            Ts43VowifiStatus.builder()
                    .setEntitlementStatus(EntitlementStatus.ENABLED)
                    .setTcStatus(TcStatus.AVAILABLE)
                    .setAddrStatus(AddrStatus.AVAILABLE)
                    .setProvStatus(ProvStatus.PROVISIONED)
                    .build();

    private static final Ts43VolteStatus sDisableVoLte =
            Ts43VolteStatus.builder()
                    .setEntitlementStatus(EntitlementStatus.DISABLED)
                    .build();

    private static final Ts43VolteStatus sEnableVoLte =
            Ts43VolteStatus.builder()
                    .setEntitlementStatus(EntitlementStatus.ENABLED)
                    .build();

    private static final Ts43SmsOverIpStatus sDisableSmsoverip =
            Ts43SmsOverIpStatus.builder()
                    .setEntitlementStatus(EntitlementStatus.DISABLED)
                    .build();

    private static final Ts43SmsOverIpStatus sEnableSmsoverip =
            Ts43SmsOverIpStatus.builder()
                    .setEntitlementStatus(EntitlementStatus.ENABLED)
                    .build();
}
