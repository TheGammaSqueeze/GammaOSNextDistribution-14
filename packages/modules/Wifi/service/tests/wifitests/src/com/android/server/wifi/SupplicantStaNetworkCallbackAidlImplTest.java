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
package com.android.server.wifi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/**
 * Unit tests for SupplicantStaNetworkHalAidlImpl
 */
@SmallTest
public class SupplicantStaNetworkCallbackAidlImplTest extends WifiBaseTest {
    private static final int TEST_NETWORK_ID = 9;
    private static final String TEST_SSID = "TestSsid";
    private static final String TEST_INTERFACE = "wlan1";

    @Mock private SupplicantStaNetworkHalAidlImpl mSupplicantStaNetworkHalAidlImpl;
    @Mock private Object mLock;
    @Mock private WifiMonitor mWifiMonitor;
    @Mock private CertificateFactory mCertificateFactory;
    @Mock private X509Certificate mX509Certificate;

    private MockitoSession mSession;
    private SupplicantStaNetworkCallbackAidlImpl mSupplicantStaNetworkCallbackAidlImpl;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        // static mocking
        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(CertificateFactory.class, withSettings().lenient())
                .strictness(Strictness.LENIENT)
                .startMocking();
        when(CertificateFactory.getInstance(any())).thenReturn(mCertificateFactory);
        when(mCertificateFactory.generateCertificate(any())).thenReturn(mX509Certificate);
        when(mX509Certificate.getBasicConstraints()).thenReturn(0);

        mSupplicantStaNetworkCallbackAidlImpl =  new SupplicantStaNetworkCallbackAidlImpl(
                mSupplicantStaNetworkHalAidlImpl, TEST_NETWORK_ID, TEST_SSID, TEST_INTERFACE,
                mLock, mWifiMonitor);
    }

    /**
     * Called after each test
     */
    @After
    public void cleanup() {
        validateMockitoUsage();
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    /** verify onServerCertificateAvailable sunny case. */
    @Test
    public void testOnCertificateSuccess() throws Exception {
        mSupplicantStaNetworkCallbackAidlImpl.onServerCertificateAvailable(
                0, "subject".getBytes(), "certHash".getBytes(), "cert".getBytes());
        ArgumentCaptor<CertificateEventInfo> certificateEventInfoArgumentCaptor =
                ArgumentCaptor.forClass(CertificateEventInfo.class);
        verify(mWifiMonitor).broadcastCertificationEvent(
                eq(TEST_INTERFACE), eq(TEST_NETWORK_ID), eq(TEST_SSID), eq(0),
                certificateEventInfoArgumentCaptor.capture());

        assertEquals(mX509Certificate, certificateEventInfoArgumentCaptor.getValue().getCert());
        assertTrue("certHash".equals(certificateEventInfoArgumentCaptor.getValue().getCertHash()));
    }

    /** verify onServerCertificateAvailable with illegal arguments. */
    @Test
    public void testOnCertificateIllegalInput() throws Exception {
        // Illegal argument: negative depth.
        mSupplicantStaNetworkCallbackAidlImpl.onServerCertificateAvailable(
                -1, "subject".getBytes(), "certHash".getBytes(), "cert".getBytes());
        verify(mWifiMonitor, never()).broadcastCertificationEvent(
                any(), anyInt(), any(), anyInt(), any());

        // Illegal argument: depth over 100.
        mSupplicantStaNetworkCallbackAidlImpl.onServerCertificateAvailable(
                101, "subject".getBytes(), "certHash".getBytes(), "cert".getBytes());
        verify(mWifiMonitor, never()).broadcastCertificationEvent(
                any(), anyInt(), any(), anyInt(), any());

        // Illegal argument: null subject
        mSupplicantStaNetworkCallbackAidlImpl.onServerCertificateAvailable(
                0, null, "certHash".getBytes(), "cert".getBytes());
        verify(mWifiMonitor, never()).broadcastCertificationEvent(
                any(), anyInt(), any(), anyInt(), any());

        // Illegal argument: null cert hash
        mSupplicantStaNetworkCallbackAidlImpl.onServerCertificateAvailable(
                0, "subject".getBytes(), null, "cert".getBytes());
        verify(mWifiMonitor, never()).broadcastCertificationEvent(
                any(), anyInt(), any(), anyInt(), any());

        // Illegal argument: null cert.
        mSupplicantStaNetworkCallbackAidlImpl.onServerCertificateAvailable(
                0, "subject".getBytes(), "certHash".getBytes(), null);
        verify(mWifiMonitor, never()).broadcastCertificationEvent(
                any(), anyInt(), any(), anyInt(), any());
    }

    /** verify onServerCertificateAvailable with CertificateException. */
    @Test
    public void testOnCertificateWithCertificateException() throws Exception {
        doThrow(new CertificateException())
                .when(mCertificateFactory).generateCertificate(any());
        mSupplicantStaNetworkCallbackAidlImpl.onServerCertificateAvailable(
                0, "subject".getBytes(), "certHash".getBytes(), "cert".getBytes());
        verify(mWifiMonitor, never()).broadcastCertificationEvent(
                any(), anyInt(), any(), anyInt(), any());
    }

    /** verify onServerCertificateAvailable with IllegalArgumentException. */
    @Test
    public void testOnCertificateWithIllegalArgumentException() throws Exception {
        doThrow(new IllegalArgumentException())
                .when(mCertificateFactory).generateCertificate(any());
        mSupplicantStaNetworkCallbackAidlImpl.onServerCertificateAvailable(
                0, "subject".getBytes(), "certHash".getBytes(), "cert".getBytes());
        verify(mWifiMonitor, never()).broadcastCertificationEvent(
                any(), anyInt(), any(), anyInt(), any());
    }
}
