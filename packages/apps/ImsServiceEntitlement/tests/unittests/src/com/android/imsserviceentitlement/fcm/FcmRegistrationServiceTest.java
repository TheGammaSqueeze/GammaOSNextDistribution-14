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

package com.android.imsserviceentitlement.fcm;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.Context;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.android.imsserviceentitlement.job.JobManager;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.FirebaseMessaging;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class FcmRegistrationServiceTest {
    @Rule public final MockitoRule rule = MockitoJUnit.rule();

    @Spy private Context mContext = ApplicationProvider.getApplicationContext();

    @Mock private JobParameters mJobParameters;
    @Mock private FirebaseInstanceId mInstanceID;
    @Mock private SubscriptionManager mSubscriptionManager;
    @Mock private SubscriptionInfo mSubscriptionInfo;
    @Mock private CarrierConfigManager mCarrierConfigManager;

    private FcmRegistrationService mService;
    private JobScheduler mScheduler;

    private static final int SUB_ID = 1;
    private static final String TOKEN = "TEST_TOKEN";
    private static final String SENDER_ID = "SENDER_ID";

    @Before
    public void setup() throws Exception {
        setActiveSubscriptionInfoList();
        setFcmSenderIdString(SENDER_ID);
        mService = new FcmRegistrationService();
        mService.attachBaseContext(mContext);
        mService.onCreate();
        mService.onBind(null);
        mScheduler = mContext.getSystemService(JobScheduler.class);
        FcmTokenStore.setToken(mContext, SUB_ID, "");
    }

    @Test
    public void enqueueJob_getPendingJob_registerFcmOnceNetworkReady() {
        mService.enqueueJob(mContext);

        assertThat(mScheduler.getPendingJob(JobManager.REGISTER_FCM_JOB_ID)).isNotNull();
    }

    @Test
    public void onStartJob_setToken_getToken() throws Exception {
        when(mInstanceID.getToken(SENDER_ID, FirebaseMessaging.INSTANCE_ID_SCOPE))
                .thenReturn(TOKEN);
        mService.setFakeInstanceID(mInstanceID);

        mService.onStartJob(mJobParameters);
        mService.mOngoingTask.get(); // wait for job finish.

        assertThat(FcmTokenStore.getToken(mContext, SUB_ID)).isEqualTo(TOKEN);
    }

    @Test
    public void onStopJob_alwaysRetunedTrue() {
        assertThat(mService.onStopJob(mJobParameters)).isTrue();
    }

    private void setActiveSubscriptionInfoList() {
        when(mSubscriptionInfo.getSimSlotIndex()).thenReturn(0);
        when(mSubscriptionManager.getActiveSubscriptionInfo(SUB_ID)).thenReturn(mSubscriptionInfo);
        when(mSubscriptionInfo.getSubscriptionId()).thenReturn(SUB_ID);
        List<SubscriptionInfo> mSubscriptionInfoList = new ArrayList<>();
        mSubscriptionInfoList.add(mSubscriptionInfo);
        when(mSubscriptionManager.getActiveSubscriptionInfoList())
                .thenReturn(mSubscriptionInfoList);
        when(mContext.getSystemService(SubscriptionManager.class)).thenReturn(mSubscriptionManager);
    }

    private void setFcmSenderIdString(String senderId) {
        PersistableBundle carrierConfig = new PersistableBundle();
        carrierConfig.putString(
                CarrierConfigManager.ImsServiceEntitlement.KEY_FCM_SENDER_ID_STRING,
                senderId
        );
        when(mCarrierConfigManager.getConfigForSubId(SUB_ID)).thenReturn(carrierConfig);
        when(mContext.getSystemService(CarrierConfigManager.class))
                .thenReturn(mCarrierConfigManager);
    }
}
