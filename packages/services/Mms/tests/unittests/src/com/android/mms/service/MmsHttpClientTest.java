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

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Bundle;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.test.core.app.ApplicationProvider;

import com.android.mms.service.exception.VoluntaryDisconnectMmsHttpException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MmsHttpClientTest {
    // Mocked classes
    private Context mContext;
    private TelephonyManager mTelephonyManager;
    private SubscriptionManager mSubscriptionManager;

    // The raw phone number from TelephonyManager.getLine1Number
    private static final String MACRO_LINE1 = "LINE1";
    // The phone number without country code
    private static final String MACRO_LINE1NOCOUNTRYCODE = "LINE1NOCOUNTRYCODE";
    private String line1Number = "1234567890";
    private String subscriberPhoneNumber = "0987654321";
    private int subId = 1;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = spy(ApplicationProvider.getApplicationContext());
        mTelephonyManager = mock(TelephonyManager.class);
        mSubscriptionManager = mock(SubscriptionManager.class);

        when(mContext.getSystemService(Context.TELEPHONY_SERVICE))
            .thenReturn(mTelephonyManager);
        when(mTelephonyManager.createForSubscriptionId(anyInt()))
            .thenReturn(mTelephonyManager);
        when(mContext.getSystemService(SubscriptionManager.class))
            .thenReturn(mSubscriptionManager);
    }

    @After
    public void tearDown() {
        mContext = null;
        mTelephonyManager = null;
        mSubscriptionManager = null;
    }

    @Test
    public void getPhoneNumberForMacroLine1() {
        String macro = MACRO_LINE1;
        Bundle mmsConfig = new Bundle();
        String emptyStr = "";
        String phoneNo;

        /* when getLine1Number returns valid number */
        doReturn(line1Number).when(mTelephonyManager).getLine1Number();
        phoneNo = MmsHttpClient.getMacroValue(mContext, macro, mmsConfig, subId);
        assertThat(phoneNo).isEqualTo(line1Number);
        // getLine1NumberAPI should be called
        verify(mTelephonyManager).getLine1Number();
        // getPhoneNumber should never be called
        verify(mSubscriptionManager, never()).getPhoneNumber(subId);

        /* when getLine1Number returns empty string */
        doReturn(emptyStr).when(mTelephonyManager).getLine1Number();
        when(mSubscriptionManager.getPhoneNumber(subId)).thenReturn(subscriberPhoneNumber);
        phoneNo = MmsHttpClient.getMacroValue(mContext, macro, mmsConfig, subId);
        assertThat(phoneNo).isEqualTo(subscriberPhoneNumber);
        verify(mSubscriptionManager).getPhoneNumber(subId);

        /* when getLine1Number returns null */
        reset(mSubscriptionManager);
        when(mSubscriptionManager.getPhoneNumber(subId)).thenReturn(subscriberPhoneNumber);
        doReturn(null).when(mTelephonyManager).getLine1Number();
        phoneNo = MmsHttpClient.getMacroValue(mContext, macro, mmsConfig, subId);
        assertThat(phoneNo).isEqualTo(subscriberPhoneNumber);
        verify(mSubscriptionManager).getPhoneNumber(subId);
    }

    @Test
    public void getPhoneNumberForMacroLine1CountryCode() throws Exception {
        String macro = MACRO_LINE1NOCOUNTRYCODE;
        String emptyStr = "";
        String phoneNo;
        Bundle mmsConfig = new Bundle();

        /* when getLine1Number returns valid number */
        doReturn(line1Number).when(mTelephonyManager).getLine1Number();
        phoneNo = MmsHttpClient.getMacroValue(mContext, macro, mmsConfig, subId);
        assertThat(phoneNo).contains(line1Number);
        // getLine1NumberAPI should be called
        verify(mTelephonyManager).getLine1Number();
        // getPhoneNumber should never be called
        verify(mSubscriptionManager, never()).getPhoneNumber(subId);

        /* when getLine1Number returns empty string */
        doReturn(emptyStr).when(mTelephonyManager).getLine1Number();
        when(mSubscriptionManager.getPhoneNumber(subId)).thenReturn(subscriberPhoneNumber);
        phoneNo = MmsHttpClient.getMacroValue(mContext, macro, mmsConfig, subId);
        assertThat(phoneNo).contains(subscriberPhoneNumber);
        verify(mSubscriptionManager).getPhoneNumber(subId);

        /* when getLine1Number returns null */
        reset(mSubscriptionManager);
        when(mSubscriptionManager.getPhoneNumber(subId)).thenReturn(subscriberPhoneNumber);
        doReturn(null).when(mTelephonyManager).getLine1Number();
        phoneNo = MmsHttpClient.getMacroValue(mContext, macro, mmsConfig, subId);
        assertThat(phoneNo).contains(subscriberPhoneNumber);
        verify(mSubscriptionManager).getPhoneNumber(subId);
    }

    @Test
    public void testDisconnectAllUrlConnections() throws IOException {
        Network mockNetwork = mock(Network.class);
        HttpURLConnection mockConnection = mock(HttpURLConnection.class);
        doReturn(mockConnection).when(mockNetwork).openConnection(any(), any());
        doReturn(mockNetwork).when(mockNetwork).getPrivateDnsBypassingCopy();
        ConnectivityManager mockCm = mock(ConnectivityManager.class);
        Bundle config = new Bundle();

        // The external thread that voluntarily silently close the socket.
        CountDownLatch latch = new CountDownLatch(1);
        final ExecutorService externalThread = Executors.newSingleThreadExecutor();
        doAnswer(invok -> {
            latch.countDown();
            return null;
        }).when(mockConnection).disconnect();

        MmsHttpClient clientUT = new MmsHttpClient(mContext, mockNetwork, mockCm);
        doAnswer(invok -> {
            externalThread.execute(clientUT::disconnectAllUrlConnections);
            // connection.disconnect is silent, but it will trigger SocketException thrown from the
            // connect thread.
            if (latch.await(1, TimeUnit.SECONDS)) {
                throw new SocketException("Socket Closed");
            }
            return null;
        }).when(mockConnection).getResponseCode();

        // Verify SocketException is transformed into VoluntaryDisconnectMmsHttpException
        assertThrows(VoluntaryDisconnectMmsHttpException.class, () -> {
            clientUT.execute("http://test", new byte[0], "GET", false,
                                "", 0, config, 1, "requestId");
        });
    }
}
