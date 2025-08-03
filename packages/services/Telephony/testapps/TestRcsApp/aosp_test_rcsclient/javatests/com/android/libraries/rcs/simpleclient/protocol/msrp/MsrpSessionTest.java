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

package com.android.libraries.rcs.simpleclient.protocol.msrp;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.libraries.rcs.simpleclient.protocol.msrp.MsrpChunk.Continuation;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 *
 */
@RunWith(AndroidJUnit4.class)
public class MsrpSessionTest {
    @Rule
    public final MockitoRule mockito = MockitoJUnit.rule();

    @Mock
    private Socket socket;

    @Before
    public void setUp() throws IOException {
        PipedInputStream input = new PipedInputStream();
        when(socket.getInputStream()).thenReturn(input);
        when(socket.getOutputStream()).thenReturn(new PipedOutputStream(input));
        when(socket.isConnected()).thenReturn(true);
    }

    @Test
    public void foo() throws IOException, ExecutionException, InterruptedException {

        AtomicReference<MsrpChunk> receivedRequest = new AtomicReference<>();

        final MsrpSession session =
                new MsrpSession(
                        socket,
                        (m) -> {
                            receivedRequest.set(m);
                        });

        MsrpChunk request = generateRequest();
        Future<MsrpChunk> future = session.send(request);

        Executors.newSingleThreadExecutor().execute(session::run);

        MsrpChunk response = future.get();

        assertThat(request).isEqualTo(receivedRequest.get());
        assertThat(response).isEqualTo(generateSuccessResponse());
    }

    private MsrpChunk generateRequest() {
        return MsrpChunk.newBuilder()
                .transactionId("txid")
                .method(MsrpChunk.Method.SEND)
                .addHeader(MsrpConstants.HEADER_TO_PATH, "msrp://test:1234/sessionA;tcp")
                .addHeader(MsrpConstants.HEADER_FROM_PATH, "msrp://test:1234/sessionB;tcp")
                .addHeader(MsrpConstants.HEADER_BYTE_RANGE, "1-*/*")
                .addHeader(MsrpConstants.HEADER_MESSAGE_ID, "abcde")
                .addHeader(MsrpConstants.HEADER_CONTENT_TYPE, "text/plain")
                .content("Hallo Welt\r\n".getBytes(StandardCharsets.UTF_8))
                .continuation(Continuation.COMPLETE)
                .build();
    }

    private MsrpChunk generateSuccessResponse() {
        return MsrpChunk.newBuilder()
                .transactionId("txid")
                .responseCode(200)
                .responseReason("OK")
                .addHeader(MsrpConstants.HEADER_TO_PATH, "msrp://test:1234/sessionB;tcp")
                .addHeader(MsrpConstants.HEADER_FROM_PATH, "msrp://test:1234/sessionA;tcp")
                .continuation(Continuation.COMPLETE)
                .build();
    }
}
