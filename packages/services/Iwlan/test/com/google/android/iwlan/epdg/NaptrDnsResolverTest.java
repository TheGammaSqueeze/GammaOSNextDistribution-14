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

import static com.google.android.iwlan.epdg.NaptrDnsResolver.*;

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

import com.google.android.iwlan.epdg.NaptrDnsResolver.NaptrTarget;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class NaptrDnsResolverTest {
    private static final String TAG = "NaptrDnsResolverTest";

    private static final String TEST_DOMAIN_NAME = "columbia.edu";
    private static final String TEST_DOMAIN_NAME_U_RECORD = "columbia.urecord.edu";

    // SRV record response to TEST_DOMAIN_NAME. Reproduced with "dig columbia.edu -tNAPTR".
    private static final byte[] TEST_DOMAIN_NAME_NAPTR_RESPONSE = {
        -15, 85, -127, -128, 0, 1, 0, 1, 0, 0, 0, 0, 8, 99, 111, 108, 117, 109, 98, 105, 97, 3, 101,
        100, 117, 0, 0, 35, 0, 1, -64, 12, 0, 35, 0, 1, 0, 0, 11, -88, 0, 39, 0, 1, 0, 0, 1, 115, 7,
        83, 73, 80, 43, 68, 50, 85, 0, 4, 95, 115, 105, 112, 4, 95, 117, 100, 112, 8, 99, 111, 108,
        117, 109, 98, 105, 97, 3, 101, 100, 117, 0
    };

    // Same as TEST_DOMAIN_NAME_NAPTR_RESPONSE, but with flag field set to 'u' instead of 's'.
    private static final byte[] TEST_DOMAIN_NAME_U_RECORD_NAPTR_RESPONSE = {
        -15, 85, -127, -128, 0, 1, 0, 1, 0, 0, 0, 0, 8, 99, 111, 108, 117, 109, 98, 105, 97, 3, 101,
        100, 117, 0, 0, 35, 0, 1, -64, 12, 0, 35, 0, 1, 0, 0, 11, -88, 0, 39, 0, 1, 0, 0, 1, 117, 7,
        83, 73, 80, 43, 68, 50, 85, 0, 4, 95, 115, 105, 112, 4, 95, 117, 100, 112, 8, 99, 111, 108,
        117, 109, 98, 105, 97, 3, 101, 100, 117, 0
    };

    @Mock private Network mMockNetwork;
    @Mock private DnsResolver mMockDnsResolver;

    MockitoSession mStaticMockSession;
    CompletableFuture<List<NaptrTarget>> mNaptrDnsResult;
    DnsResolver.Callback<List<NaptrTarget>> mNaptrDnsCb;

    public NaptrDnsResolverTest() {
        mNaptrDnsResult = new CompletableFuture<>();
        mNaptrDnsCb =
                new DnsResolver.Callback<List<NaptrTarget>>() {
                    @Override
                    public void onAnswer(@NonNull final List<NaptrTarget> answer, final int rcode) {
                        if (rcode == 0 && answer.size() != 0) {
                            mNaptrDnsResult.complete(answer);
                        } else {
                            mNaptrDnsResult.completeExceptionally(new UnknownHostException());
                        }
                    }

                    @Override
                    public void onError(@Nullable final DnsResolver.DnsException error) {
                        mNaptrDnsResult.completeExceptionally(error);
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

    // Demonstrates that NAPTR responses with flag field 'S' and 'A' will be parsed as expected.
    @Test
    public void testValidHostNameGivesNaptrResponse()
            throws ExecutionException, InterruptedException {
        doAnswer(
                        invocation -> {
                            final Executor executor = invocation.getArgument(5);
                            final DnsResolver.Callback<byte[]> callback = invocation.getArgument(7);
                            executor.execute(
                                    () -> callback.onAnswer(TEST_DOMAIN_NAME_NAPTR_RESPONSE, 0));
                            return null;
                        })
                .when(mMockDnsResolver)
                .rawQuery(
                        any(),
                        ArgumentMatchers.eq(TEST_DOMAIN_NAME),
                        ArgumentMatchers.eq(DnsResolver.CLASS_IN),
                        ArgumentMatchers.eq(QUERY_TYPE_NAPTR),
                        anyInt(),
                        any(),
                        any(),
                        any());

        NaptrDnsResolver.query(
                mMockNetwork,
                TEST_DOMAIN_NAME,
                Executors.newSingleThreadExecutor(),
                null,
                mNaptrDnsCb);
        List<NaptrTarget> records = mNaptrDnsResult.join();
        assertEquals(records.size(), 1);

        NaptrTarget record = records.get(0);
        assertEquals(record.mName, "_sip._udp.columbia.edu");
        // SRV record type.
        assertEquals(record.mType, TYPE_SRV);
    }

    // Demonstrates that NAPTR responses with flag field 'U' and 'P' are unexpected and with throw
    // a DnsException.
    @Test
    public void testValidHostNameGivesParsingErrorForUnexpectedResponse()
            throws ExecutionException, InterruptedException {
        doAnswer(
                        invocation -> {
                            final Executor executor = invocation.getArgument(5);
                            final DnsResolver.Callback<byte[]> callback = invocation.getArgument(7);
                            executor.execute(
                                    () ->
                                            callback.onAnswer(
                                                    TEST_DOMAIN_NAME_U_RECORD_NAPTR_RESPONSE, 0));
                            return null;
                        })
                .when(mMockDnsResolver)
                .rawQuery(
                        any(),
                        ArgumentMatchers.eq(TEST_DOMAIN_NAME_U_RECORD),
                        ArgumentMatchers.eq(DnsResolver.CLASS_IN),
                        ArgumentMatchers.eq(QUERY_TYPE_NAPTR),
                        anyInt(),
                        any(),
                        any(),
                        any());

        NaptrDnsResolver.query(
                mMockNetwork,
                TEST_DOMAIN_NAME_U_RECORD,
                Executors.newSingleThreadExecutor(),
                null,
                mNaptrDnsCb);

        DnsResolver.DnsException exception = null;
        try {
            mNaptrDnsResult.join();
        } catch (CompletionException e) {
            exception = (DnsResolver.DnsException) e.getCause();
            Log.d(TAG, e.getMessage() + e.getCause());
        }
        assertNotNull("Exception wasn't thrown!", exception);
        assertEquals(exception.code, DnsResolver.ERROR_PARSE);
    }
}
