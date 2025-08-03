/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.mms.service;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.CarrierConfigManager;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.test.core.app.ApplicationProvider;

import com.android.mms.service.metrics.MmsStats;
import com.android.mms.service.metrics.PersistMmsAtomsStorage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class MmsRequestRoboTest {
    // Mocked classes
    private Context mContext;
    private TelephonyManager mTelephonyManager;
    private SubscriptionManager mSubscriptionManager;

    private final int mSubId = 1;
    private MmsService mMmsService;
    private MmsStats mMmsStats;
    private static final String sFakeUri = "http://greatdogs.com";
    private static final String sFakeLocationUri = "http://greatdogs.com";
    private static final long sFakeMessageId = 8675309L;
    private PersistMmsAtomsStorage mPersistMmsAtomsStorage;
    private SmsManager mSmsManager;
    private Bundle mCarrierConfigValues;
    private static final int sMaxPduSize = 3 * 1000;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = spy(ApplicationProvider.getApplicationContext());
        mTelephonyManager = mock(TelephonyManager.class);
        mSubscriptionManager = mock(SubscriptionManager.class);
        mSmsManager = spy(SmsManager.getSmsManagerForSubscriptionId(mSubId));

        when(mContext.getSystemService(Context.TELEPHONY_SERVICE))
                .thenReturn(mTelephonyManager);
        when(mTelephonyManager.createForSubscriptionId(anyInt()))
                .thenReturn(mTelephonyManager);
        when(mContext.getSystemService(SubscriptionManager.class))
                .thenReturn(mSubscriptionManager);

        mPersistMmsAtomsStorage = mock(PersistMmsAtomsStorage.class);
        mMmsStats = new MmsStats(mContext, mPersistMmsAtomsStorage, mSubId,
                mTelephonyManager, null, true);
        mCarrierConfigValues = new Bundle();
        mCarrierConfigValues.putInt(
                CarrierConfigManager.KEY_MMS_MAX_NTN_PAYLOAD_SIZE_BYTES_INT,
                sMaxPduSize);
    }

    @After
    public void tearDown() {
        mContext = null;
        mTelephonyManager = null;
        mSubscriptionManager = null;
    }

    @Test
    public void sendRequest_noSatellite_sendSuccessful() {
        SendRequest request = new SendRequest(mMmsService, mSubId, Uri.parse(sFakeUri),
                sFakeLocationUri, /* sentIntent= */ null, /* callingPkg= */ null,
                mCarrierConfigValues, /* context= */ mMmsService,
                sFakeMessageId, mMmsStats, mTelephonyManager);
        request.mPduData = new byte[sMaxPduSize + 100];

        boolean okToSend = request.canTransferPayloadOnCurrentNetwork();

        assertThat(okToSend).isTrue();
    }

    @Test
    public void sendRequest_connectedToSatellite_smallPdu_sendSuccessful() {
        ServiceState ss = new ServiceState();
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setIsNonTerrestrialNetwork(true)
                .build();
        ss.addNetworkRegistrationInfo(nri);
        doReturn(ss).when(mTelephonyManager).getServiceState();
        SendRequest request = new SendRequest(mMmsService, mSubId, Uri.parse(sFakeUri),
                sFakeLocationUri, /* sentIntent= */ null, /* callingPkg= */ null,
                mCarrierConfigValues, /* context= */ mMmsService,
                sFakeMessageId, mMmsStats, mTelephonyManager);
        request.mPduData = new byte[sMaxPduSize - 1];

        boolean okToSend = request.canTransferPayloadOnCurrentNetwork();

        assertThat(okToSend).isTrue();
    }

    @Test
    public void sendRequest_connectedToSatellite_largePdu_sendSFails() {
        ServiceState ss = new ServiceState();
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setIsNonTerrestrialNetwork(true)
                .build();
        ss.addNetworkRegistrationInfo(nri);
        doReturn(ss).when(mTelephonyManager).getServiceState();
        SendRequest request = new SendRequest(mMmsService, mSubId, Uri.parse(sFakeUri),
                sFakeLocationUri, /* sentIntent= */ null, /* callingPkg= */ null,
                mCarrierConfigValues, /* context= */ mMmsService,
                sFakeMessageId, mMmsStats, mTelephonyManager);
        request.mPduData = new byte[sMaxPduSize + 1];

        boolean okToSend = request.canTransferPayloadOnCurrentNetwork();

        assertThat(okToSend).isFalse();
    }

    @Test
    public void downloadRequest_noSatellite_downloadSuccessful() {
        doReturn(150L).when(mSmsManager).getWapMessageSize(sFakeUri);
        DownloadRequest request = new DownloadRequest(mMmsService, mSubId, sFakeUri,
                Uri.parse(sFakeUri), /* downloadIntent= */ null, /* callingPkg= */ null,
                mCarrierConfigValues, /* context= */ mMmsService, sFakeMessageId, mMmsStats,
                mTelephonyManager);

        boolean okToDownload = request.canTransferPayloadOnCurrentNetwork();

        assertThat(okToDownload).isTrue();
    }
}
