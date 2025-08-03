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

package com.android.internal.net.ipsec.test.ike.ike3gpp;

import static com.android.internal.net.ipsec.test.ike.ike3gpp.Ike3gppExtensionExchange.NOTIFY_TYPE_BACKOFF_TIMER;
import static com.android.internal.net.ipsec.test.ike.ike3gpp.Ike3gppExtensionExchange.NOTIFY_TYPE_DEVICE_IDENTITY;
import static com.android.internal.net.ipsec.test.ike.ike3gpp.Ike3gppExtensionExchange.NOTIFY_TYPE_N1_MODE_CAPABILITY;
import static com.android.internal.net.ipsec.test.ike.ike3gpp.Ike3gppExtensionExchange.NOTIFY_TYPE_N1_MODE_INFORMATION;
import static com.android.internal.net.ipsec.test.ike.message.IkeMessage.IKE_EXCHANGE_SUBTYPE_GENERIC_INFO;
import static com.android.internal.net.ipsec.test.ike.message.IkeMessage.IKE_EXCHANGE_SUBTYPE_IKE_AUTH;
import static com.android.internal.net.ipsec.test.ike.message.IkeMessage.IKE_EXCHANGE_SUBTYPE_IKE_INIT;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import android.net.ipsec.test.ike.ike3gpp.Ike3gppExtension;
import android.net.ipsec.test.ike.ike3gpp.Ike3gppExtension.Ike3gppDataListener;
import android.net.ipsec.test.ike.ike3gpp.Ike3gppParams;

import com.android.internal.net.ipsec.test.ike.message.IkeNotifyPayload;
import com.android.internal.net.ipsec.test.ike.message.IkePayload;
import com.android.internal.util.HexDump;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

public class Ike3gppExtensionExchangeTest {
    private static final byte PDU_SESSION_ID = (byte) 0x01;
    private static final byte[] N1_MODE_CAPABILITY_DATA = HexDump.hexStringToByteArray("0101");

    private static final byte[] N1_MODE_INFORMATION_DATA =
            HexDump.hexStringToByteArray("0411223344");
    private static final byte[] BACKOFF_TIMER_DATA = HexDump.hexStringToByteArray("01AF");
    private static final String DEVICE_IDENTITY_IMEI = "123456789123456";
    private static final byte[] DEVICE_IDENTITY_PAYLOAD_IMEI =
            HexDump.hexStringToByteArray("00090121436587193254F6");

    private static final IkeNotifyPayload N1_MODE_INFORMATION =
            new IkeNotifyPayload(NOTIFY_TYPE_N1_MODE_INFORMATION, N1_MODE_INFORMATION_DATA);
    private static final IkeNotifyPayload FRAGMENTATION_SUPPORTED =
            new IkeNotifyPayload(IkeNotifyPayload.NOTIFY_TYPE_IKEV2_FRAGMENTATION_SUPPORTED);
    private static final IkeNotifyPayload BACKOFF_TIMER =
            new IkeNotifyPayload(NOTIFY_TYPE_BACKOFF_TIMER, BACKOFF_TIMER_DATA);
    private static final IkeNotifyPayload DEVICE_IDENTITY_REQUEST =
            new IkeNotifyPayload(NOTIFY_TYPE_DEVICE_IDENTITY, DEVICE_IDENTITY_PAYLOAD_IMEI);

    private static final Executor INLINE_EXECUTOR = Runnable::run;

    private Ike3gppDataListener mMockIke3gppDataListener;

    private Ike3gppParams mIke3gppParams;
    private Ike3gppExtensionExchange mIke3gppExtensionExchange;

    @Before
    public void setUp() {
        mMockIke3gppDataListener = mock(Ike3gppDataListener.class);

        mIke3gppParams =
                new Ike3gppParams.Builder()
                        .setPduSessionId(PDU_SESSION_ID)
                        .setMobileDeviceIdentity(DEVICE_IDENTITY_IMEI)
                        .build();
        mIke3gppExtensionExchange =
                new Ike3gppExtensionExchange(
                        new Ike3gppExtension(mIke3gppParams, mMockIke3gppDataListener),
                        INLINE_EXECUTOR);
    }

    @Test
    public void testGetRequestPayloadsInEapIkeAuthServerAuthenticatedAndNetworkRequested()
            throws Exception {
        mIke3gppExtensionExchange.handle3gppResponsePayloads(
                IKE_EXCHANGE_SUBTYPE_IKE_AUTH, Arrays.asList((IkePayload) DEVICE_IDENTITY_REQUEST));
        List<IkePayload> result =
                mIke3gppExtensionExchange.getRequestPayloadsInEap(true /* serverAuthenticated */);

        assertEquals(1, result.size());

        IkeNotifyPayload deviceIdentity = (IkeNotifyPayload) result.get(0);
        assertEquals(NOTIFY_TYPE_DEVICE_IDENTITY, deviceIdentity.notifyType);
        assertArrayEquals(DEVICE_IDENTITY_PAYLOAD_IMEI, deviceIdentity.notifyData);
    }

    @Test
    public void testGetRequestPayloadsInEapIkeAuthServerNotAuthenticatedAndNetworkRequested()
            throws Exception {
        mIke3gppExtensionExchange.handle3gppResponsePayloads(
                IKE_EXCHANGE_SUBTYPE_IKE_AUTH, Arrays.asList((IkePayload) DEVICE_IDENTITY_REQUEST));
        List<IkePayload> result =
                mIke3gppExtensionExchange.getRequestPayloadsInEap(false /* serverAuthenticated */);

        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetRequestPayloadsInEapIkeAuthServerAuthenticatedAndNotRequestedByNetwork()
            throws Exception {
        List<IkePayload> result =
                mIke3gppExtensionExchange.getRequestPayloadsInEap(true /* serverAuthenticated */);

        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetRequestPayloadsInEapIkeAuthNotConfigured() throws Exception {
        mIke3gppExtensionExchange = new Ike3gppExtensionExchange(null, INLINE_EXECUTOR);

        List<IkePayload> result =
                mIke3gppExtensionExchange.getRequestPayloads(IKE_EXCHANGE_SUBTYPE_IKE_AUTH);

        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetRequestPayloadsIkeAuth() throws Exception {
        List<IkePayload> result =
                mIke3gppExtensionExchange.getRequestPayloads(IKE_EXCHANGE_SUBTYPE_IKE_AUTH);

        assertEquals(1, result.size());

        IkeNotifyPayload n1ModeCapability = (IkeNotifyPayload) result.get(0);
        assertEquals(NOTIFY_TYPE_N1_MODE_CAPABILITY, n1ModeCapability.notifyType);
        assertArrayEquals(N1_MODE_CAPABILITY_DATA, n1ModeCapability.notifyData);
    }

    @Test
    public void testGetResponsePayloadsIkeInfo() throws Exception {
        List<IkePayload> result =
                mIke3gppExtensionExchange.getResponsePayloads(
                        IKE_EXCHANGE_SUBTYPE_GENERIC_INFO,
                        Arrays.asList((IkePayload) DEVICE_IDENTITY_REQUEST));

        assertEquals(1, result.size());

        IkeNotifyPayload deviceIdentity = (IkeNotifyPayload) result.get(0);
        assertEquals(NOTIFY_TYPE_DEVICE_IDENTITY, deviceIdentity.notifyType);
        assertArrayEquals(DEVICE_IDENTITY_PAYLOAD_IMEI, deviceIdentity.notifyData);
    }

    @Test
    public void testGetResponsePayloadsIkeInfoNotRequestedByNetwork() throws Exception {
        List<IkePayload> result =
                mIke3gppExtensionExchange.getResponsePayloads(
                        IKE_EXCHANGE_SUBTYPE_GENERIC_INFO, Collections.EMPTY_LIST);

        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetResponsePayloadsIkeInfo3gppNotConfigured() throws Exception {
        mIke3gppExtensionExchange = new Ike3gppExtensionExchange(null, INLINE_EXECUTOR);

        List<IkePayload> result =
                mIke3gppExtensionExchange.getRequestPayloads(IKE_EXCHANGE_SUBTYPE_GENERIC_INFO);

        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetRequestPayloadsIkeAuthNotConfigured() throws Exception {
        mIke3gppExtensionExchange = new Ike3gppExtensionExchange(null, INLINE_EXECUTOR);

        List<IkePayload> result =
                mIke3gppExtensionExchange.getRequestPayloads(IKE_EXCHANGE_SUBTYPE_IKE_AUTH);

        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetRequestPayloadsIkeInit() throws Exception {
        List<IkePayload> result =
                mIke3gppExtensionExchange.getRequestPayloads(IKE_EXCHANGE_SUBTYPE_IKE_INIT);

        assertTrue(result.isEmpty());
    }

    @Test
    public void testExtract3gppResponsePayloadsIkeAuth() throws Exception {
        List<IkePayload> result =
                mIke3gppExtensionExchange.extract3gppResponsePayloads(
                        IKE_EXCHANGE_SUBTYPE_IKE_AUTH,
                        Arrays.asList(
                                N1_MODE_INFORMATION,
                                BACKOFF_TIMER,
                                FRAGMENTATION_SUPPORTED,
                                DEVICE_IDENTITY_REQUEST));

        assertEquals(3, result.size());

        IkeNotifyPayload n1ModeInformation = null;
        IkeNotifyPayload backoffTimer = null;
        IkeNotifyPayload deviceIdentity = null;
        for (IkePayload payload : result) {
            if (payload instanceof IkeNotifyPayload) {
                IkeNotifyPayload notifyPayload = (IkeNotifyPayload) payload;
                if (notifyPayload.notifyType == NOTIFY_TYPE_N1_MODE_INFORMATION) {
                    n1ModeInformation = notifyPayload;
                } else if (notifyPayload.notifyType == NOTIFY_TYPE_BACKOFF_TIMER) {
                    backoffTimer = notifyPayload;
                } else if (notifyPayload.notifyType == NOTIFY_TYPE_DEVICE_IDENTITY) {
                    deviceIdentity = notifyPayload;
                }

            }
        }

        assertArrayEquals(N1_MODE_INFORMATION_DATA, n1ModeInformation.notifyData);
        assertArrayEquals(BACKOFF_TIMER_DATA, backoffTimer.notifyData);
        assertArrayEquals(DEVICE_IDENTITY_PAYLOAD_IMEI, deviceIdentity.notifyData);
    }

    @Test
    public void testExtract3gppResponsePayloadsIkeAuthNotConfigured() throws Exception {
        mIke3gppExtensionExchange = new Ike3gppExtensionExchange(null, INLINE_EXECUTOR);

        List<IkePayload> result =
                mIke3gppExtensionExchange.extract3gppResponsePayloads(
                        IKE_EXCHANGE_SUBTYPE_IKE_INIT,
                        Arrays.asList(N1_MODE_INFORMATION, BACKOFF_TIMER, FRAGMENTATION_SUPPORTED));
        assertTrue(result.isEmpty());
    }

    @Test
    public void testExtract3gppResponsePayloadsIkeInit() throws Exception {
        List<IkePayload> result =
                mIke3gppExtensionExchange.extract3gppResponsePayloads(
                        IKE_EXCHANGE_SUBTYPE_IKE_INIT,
                        Arrays.asList(N1_MODE_INFORMATION, BACKOFF_TIMER, FRAGMENTATION_SUPPORTED));
        assertTrue(result.isEmpty());
    }

    @Test
    public void testExtract3gppResponsePayloadsIkeAuthNo3gpp() throws Exception {
        List<IkePayload> result =
                mIke3gppExtensionExchange.extract3gppResponsePayloads(
                        IKE_EXCHANGE_SUBTYPE_IKE_AUTH, Arrays.asList(FRAGMENTATION_SUPPORTED));
        assertTrue(result.isEmpty());
    }
}
