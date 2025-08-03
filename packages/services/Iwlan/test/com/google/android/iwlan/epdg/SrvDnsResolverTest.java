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

package com.google.android.iwlan.epdg;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static com.google.android.iwlan.epdg.SrvDnsResolver.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;

import android.net.DnsResolver;
import android.net.Network;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import libcore.net.InetAddressUtils;

import com.google.android.iwlan.epdg.SrvDnsResolver.SrvRecordInetAddress;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class SrvDnsResolverTest {
    private static final String TAG = "SrvDnsResolverTest";

    private static final String TEST_QUERY = "_imaps._tcp.gmail.com";

    // SRV record response to TEST_QUERY. Reproduced with "dig _imaps._tcp.gmail.com -tSRV".
    // This contains both the SRV record corresponding to the target name, as well as the IP
    // addresses corresponding to the FQDN in the SRV record.
    private static final byte[] TEST_QUERY_SRV_RESPONSE_IP_ADDRESSES = {
        82, -42, -127, -128, 0, 1, 0, 1, 0, 0, 0, 4, 6, 95, 105, 109, 97, 112, 115, 4, 95, 116, 99,
        112, 5, 103, 109, 97, 105, 108, 3, 99, 111, 109, 0, 0, 33, 0, 1, -64, 12, 0, 33, 0, 1, 0, 1,
        81, -128, 0, 22, 0, 5, 0, 0, 3, -31, 4, 105, 109, 97, 112, 5, 103, 109, 97, 105, 108, 3, 99,
        111, 109, 0, -64, 57, 0, 1, 0, 1, 0, 0, 0, 25, 0, 4, -114, -5, 2, 109, -64, 57, 0, 1, 0, 1,
        0, 0, 0, 25, 0, 4, -114, -5, 2, 108, -64, 57, 0, 28, 0, 1, 0, 0, 0, 25, 0, 16, 38, 7, -8,
        -80, 64, 35, 12, 3, 0, 0, 0, 0, 0, 0, 0, 109, -64, 57, 0, 28, 0, 1, 0, 0, 0, 25, 0, 16, 38,
        7, -8, -80, 64, 35, 12, 3, 0, 0, 0, 0, 0, 0, 0, 108
    };

    // SRV record response to TEST_QUERY, but on a different AP / DNS server, containing only
    // the SRV record corresponding to the target name. Additional TYPE_A DNS lookups would be
    // needed to pull the IP addresses corresponding to the target name.
    private static final byte[] TEST_QUERY_SRV_RESPONSE = {
        3, -109, -127, -128, 0, 1, 0, 1, 0, 0, 0, 0, 6, 95, 105, 109, 97, 112, 115, 4, 95, 116, 99,
        112, 5, 103, 109, 97, 105, 108, 3, 99, 111, 109, 0, 0, 33, 0, 1, -64, 12, 0, 33, 0, 1, 0, 1,
        80, -11, 0, 22, 0, 5, 0, 0, 3, -31, 4, 105, 109, 97, 112, 5, 103, 109, 97, 105, 108, 3, 99,
        111, 109, 0
    };

    // Response to the SRV query 'TEST_QUERY', with an unexpected record type in its answer (TYPE_A
    // instead of QUERY_TYPE_SRV).
    private static final byte[] TEST_QUERY_INVALID_SRV_RESPONSE = {
        3, -109, -127, -128, 0, 1, 0, 1, 0, 0, 0, 0, 6, 95, 105, 109, 97, 112, 115, 4, 95, 116, 99,
        112, 5, 103, 109, 97, 105, 108, 3, 99, 111, 109, 0, 0, 33, 0, 1, -64, 12, 0, 0, 0, 1, 0, 1,
        80, -11, 0, 22, 0, 5, 0, 0, 3, -31, 4, 105, 109, 97, 112, 5, 103, 109, 97, 105, 108, 3, 99,
        111, 109, 0
    };

    // The IP addresses corresponding to the SRV record in TEST_QUERY_SRV_RESPONSE.
    List<InetAddress> TEST_QUERY_RESPONSE_IP_ADDRESSES =
            Arrays.asList(
                    InetAddressUtils.parseNumericAddress("142.250.101.108"),
                    InetAddressUtils.parseNumericAddress("142.250.101.109"),
                    InetAddressUtils.parseNumericAddress("2607:f8b0:4023:c03::6d"),
                    InetAddressUtils.parseNumericAddress("2607:f8b0:4023:c03::6c"));

    @Mock private Network mMockNetwork;
    @Mock private DnsResolver mMockDnsResolver;

    MockitoSession mStaticMockSession;
    final CompletableFuture<List<SrvRecordInetAddress>> mSrvDnsResult;
    final DnsResolver.Callback<List<SrvRecordInetAddress>> mSrvDnsCb;

    public SrvDnsResolverTest() {
        mSrvDnsResult = new CompletableFuture<>();
        mSrvDnsCb =
                new DnsResolver.Callback<List<SrvRecordInetAddress>>() {
                    @Override
                    public void onAnswer(
                            @NonNull final List<SrvRecordInetAddress> answer, final int rcode) {
                        if (rcode == 0 && answer.size() != 0) {
                            mSrvDnsResult.complete(answer);
                        } else {
                            mSrvDnsResult.completeExceptionally(new UnknownHostException());
                        }
                    }

                    @Override
                    public void onError(@Nullable final DnsResolver.DnsException error) {
                        mSrvDnsResult.completeExceptionally(error);
                    }
                };
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mStaticMockSession = mockitoSession().mockStatic(DnsResolver.class).startMocking();

        // lenient() here is used to mock the static method.
        lenient().when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);
    }

    @After
    public void cleanUp() throws Exception {
        mStaticMockSession.finishMocking();
    }

    // Tests the case where the DNS server response includes both the SRV record and additionally,
    // the IP address records corresponding to the FQDN in the SRV record.
    @Test
    public void testQueryGivesSrvAndIpAddressResponse()
            throws ExecutionException, InterruptedException {
        doAnswer(
                        invocation -> {
                            final Executor executor = invocation.getArgument(5);
                            final DnsResolver.Callback<byte[]> callback = invocation.getArgument(7);
                            executor.execute(
                                    () ->
                                            callback.onAnswer(
                                                    TEST_QUERY_SRV_RESPONSE_IP_ADDRESSES, 0));
                            return null;
                        })
                .when(mMockDnsResolver)
                .rawQuery(
                        any(),
                        ArgumentMatchers.eq(TEST_QUERY),
                        ArgumentMatchers.eq(DnsResolver.CLASS_IN),
                        ArgumentMatchers.eq(QUERY_TYPE_SRV),
                        anyInt(),
                        any(),
                        any(),
                        any());

        SrvDnsResolver.query(mMockNetwork, TEST_QUERY, Runnable::run, null, mSrvDnsCb);
        final List<SrvRecordInetAddress> records = mSrvDnsResult.join();

        assertEquals(records.size(), 4);

        SrvRecordInetAddress record = records.get(0);
        assertEquals(record.mInetAddress.getHostAddress(), "142.251.2.109");
        assertEquals(record.mPort, 993);

        record = records.get(1);
        assertEquals(record.mInetAddress.getHostAddress(), "142.251.2.108");
        assertEquals(record.mPort, 993);

        record = records.get(2);
        assertEquals(record.mInetAddress.getHostAddress(), "2607:f8b0:4023:c03::6d");
        assertEquals(record.mPort, 993);

        record = records.get(3);
        assertEquals(record.mInetAddress.getHostAddress(), "2607:f8b0:4023:c03::6c");
        assertEquals(record.mPort, 993);
    }

    // Tests the case where the DNS server's Type SRV response includes only the SRV record, and the
    // corresponding TYPE_A/AAAA records a pulled with a second-level DNS query.
    @Test
    public void testQueryGivesSrvResponseFollowUpQueriesGiveIpAddress()
            throws ExecutionException, InterruptedException {
        doAnswer(
                        invocation -> {
                            Executor executor = invocation.getArgument(5);
                            DnsResolver.Callback<byte[]> callback = invocation.getArgument(7);
                            executor.execute(() -> callback.onAnswer(TEST_QUERY_SRV_RESPONSE, 0));
                            return null;
                        })
                .when(mMockDnsResolver)
                .rawQuery(
                        any(),
                        ArgumentMatchers.eq(TEST_QUERY),
                        ArgumentMatchers.eq(DnsResolver.CLASS_IN),
                        ArgumentMatchers.eq(QUERY_TYPE_SRV),
                        anyInt(),
                        any(),
                        any(),
                        any());

        doAnswer(
                        invocation -> {
                            Executor executor = invocation.getArgument(3);
                            DnsResolver.Callback<? super List<InetAddress>> callback =
                                    invocation.getArgument(5);
                            executor.execute(
                                    () -> callback.onAnswer(TEST_QUERY_RESPONSE_IP_ADDRESSES, 0));
                            return null;
                        })
                .when(mMockDnsResolver)
                .query(
                        any(),
                        ArgumentMatchers.eq("imap.gmail.com"),
                        ArgumentMatchers.eq(DnsResolver.FLAG_EMPTY),
                        any(),
                        any(),
                        any());

        SrvDnsResolver.query(mMockNetwork, TEST_QUERY, Runnable::run, null, mSrvDnsCb);
        List<SrvRecordInetAddress> records = mSrvDnsResult.join();
        assertEquals(records.size(), 4);

        SrvRecordInetAddress record = records.get(0);
        assertEquals(record.mInetAddress.getHostAddress(), "142.250.101.108");
        assertEquals(record.mPort, 993);

        record = records.get(1);
        assertEquals(record.mInetAddress.getHostAddress(), "142.250.101.109");
        assertEquals(record.mPort, 993);

        record = records.get(2);
        assertEquals(record.mInetAddress.getHostAddress(), "2607:f8b0:4023:c03::6d");
        assertEquals(record.mPort, 993);

        record = records.get(3);
        assertEquals(record.mInetAddress.getHostAddress(), "2607:f8b0:4023:c03::6c");
        assertEquals(record.mPort, 993);
    }

    // Tests the case where the DNS server response contains a TYPE_A record instead of a
    // QUERY_TYPE_SRV record in the answer field, and the implementation throws a DnsException.
    @Test
    public void testInvalidResponseThrowsParseException()
            throws ExecutionException, InterruptedException {
        doAnswer(
                        invocation -> {
                            final Executor executor = invocation.getArgument(5);
                            final DnsResolver.Callback<byte[]> callback = invocation.getArgument(7);
                            executor.execute(
                                    () -> callback.onAnswer(TEST_QUERY_INVALID_SRV_RESPONSE, 0));
                            return null;
                        })
                .when(mMockDnsResolver)
                .rawQuery(
                        any(),
                        ArgumentMatchers.eq(TEST_QUERY),
                        ArgumentMatchers.eq(DnsResolver.CLASS_IN),
                        ArgumentMatchers.eq(QUERY_TYPE_SRV),
                        anyInt(),
                        any(),
                        any(),
                        any());

        SrvDnsResolver.query(mMockNetwork, TEST_QUERY, Runnable::run, null, mSrvDnsCb);
        DnsResolver.DnsException exception = null;
        try {
            mSrvDnsResult.join();
        } catch (CompletionException e) {
            exception = (DnsResolver.DnsException) e.getCause();
            Log.d(TAG, e.getMessage() + e.getCause());
        }
        assertNotNull("Exception wasn't thrown!", exception);
        assertEquals(exception.code, DnsResolver.ERROR_PARSE);
    }
}
