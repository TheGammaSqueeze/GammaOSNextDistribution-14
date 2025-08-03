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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.android.imsserviceentitlement.job.JobManager;
import com.android.libraries.entitlement.ServiceEntitlement;

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
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class FcmServiceTest {
    @Rule public final MockitoRule rule = MockitoJUnit.rule();

    @Spy private Context mContext = ApplicationProvider.getApplicationContext();

    @Mock private SubscriptionManager mSubscriptionManager;
    @Mock private SubscriptionInfo mSubscriptionInfo;
    @Mock private CarrierConfigManager mCarrierConfigManager;
    @Mock private JobManager mJobManager;

    private FcmService mService;

    private static final String DATA_APP_KEY = "app";
    private static final String ERROR_DATA_APP_KEY = "error_app";
    private static final String DATA_TIMESTAMP_KEY = "timestamp";
    private static final String TIME_STAMP = "2019-01-29T13:15:31-08:00";
    private static final String SENDER_ID = "SENDER_ID";
    private static final int SUB_ID = 1;

    @Before
    public void setup() throws Exception {
        setActiveSubscriptionInfoList();
        setFcmSenderIdString(SENDER_ID);
        mService = new FcmService();
        mService.attachBaseContext(mContext);
        mService.onCreate();
        mService.setMockJobManager(mJobManager);
    }

    @Test
    public void onMessageReceived_isFcmSupported_queryEntitlementStatusOnceNetworkReady() {
        Map<String, String> dataMap = setFcmData(DATA_APP_KEY, ServiceEntitlement.APP_VOWIFI);

        mService.onMessageReceived(SENDER_ID, dataMap);

        verify(mJobManager).queryEntitlementStatusOnceNetworkReady();
    }

    @Test
    public void onMessageReceived_isNotTs43EntitlementsChangeEvent_noJobs() {
        Map<String, String> dataMap = setFcmData(ERROR_DATA_APP_KEY, ServiceEntitlement.APP_VOWIFI);

        mService.onMessageReceived(SENDER_ID, dataMap);

        verify(mJobManager, never()).queryEntitlementStatusOnceNetworkReady();
    }

    @Test
    public void onMessageReceived_emptySenderId_isNotFcmSupported() {
        setFcmSenderIdString("");
        Map<String, String> dataMap = setFcmData(DATA_APP_KEY, ServiceEntitlement.APP_VOWIFI);

        mService.onMessageReceived(SENDER_ID, dataMap);

        verify(mJobManager, never()).queryEntitlementStatusOnceNetworkReady();
    }

    private Map<String, String> setFcmData(String dataAppKey, String dataAppValue) {
        Map<String, String> dataMap = Map.of(
                dataAppKey, dataAppValue,
                DATA_TIMESTAMP_KEY, TIME_STAMP);
        return dataMap;
    }

    private void setActiveSubscriptionInfoList() {
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
