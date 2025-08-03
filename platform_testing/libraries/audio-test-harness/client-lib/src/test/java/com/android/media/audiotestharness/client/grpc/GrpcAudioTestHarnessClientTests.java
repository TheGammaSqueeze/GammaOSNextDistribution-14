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

package com.android.media.audiotestharness.client.grpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.media.audiotestharness.proto.AudioTestHarnessGrpc;

import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.concurrent.Executors;

@RunWith(JUnitParamsRunner.class)
public class GrpcAudioTestHarnessClientTests {

    @Rule public MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock GrpcAudioCaptureStreamFactory mGrpcAudioCaptureStreamFactory;

    @Mock GrpcAudioCaptureStream mGrpcAudioCaptureStream;

    @Mock ManagedChannel mManagedChannel;

    /** Tests for the {@link GrpcAudioTestHarnessClient.Builder} */
    @Test(expected = NullPointerException.class)
    public void setAddress_throwsNullPointerException_nullHostname() throws Exception {
        GrpcAudioTestHarnessClient.builder().setAddress(/* hostname= */ null, /* port= */ 8080);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setAddress_throwsIllegalArgumentException_badPort() throws Exception {
        GrpcAudioTestHarnessClient.builder().setAddress("localhost", /* port= */ -123);
    }

    @Test
    @Parameters(method = "getBuildParameters")
    public void build_returnsNonNullInstance_validParameters(
            GrpcAudioTestHarnessClient.Builder builder) throws Exception {
        assertNotNull(builder.build());
    }

    public static Object[] getBuildParameters() {
        return new Object[][] {
            {GrpcAudioTestHarnessClient.builder().setAddress("localhost", /* port= */ 8080)},
            {
                GrpcAudioTestHarnessClient.builder()
                        .setAddress("service.google.com", 49152)
                        .setExecutor(Executors.newSingleThreadScheduledExecutor())
            },
            {
                GrpcAudioTestHarnessClient.builder()
                        .setManagedChannel(
                                ManagedChannelBuilder.forAddress("localhost", /* port= */ 8080)
                                        .usePlaintext()
                                        .build())
            }
        };
    }

    @Test
    public void startCapture_returnsNonNullAudioCaptureStream() throws Exception {
        GrpcAudioTestHarnessClient client = initMocksAndClient();
        assertNotNull(client.startCapture());
    }

    @Test
    public void startCapture_createsNewStubWithProperChannel() throws Exception {
        ArgumentCaptor<AudioTestHarnessGrpc.AudioTestHarnessStub>
                audioTestHarnessStubArgumentCaptor =
                        ArgumentCaptor.forClass(AudioTestHarnessGrpc.AudioTestHarnessStub.class);
        GrpcAudioTestHarnessClient client = initMocksAndClient();

        client.startCapture();

        verify(mGrpcAudioCaptureStreamFactory)
                .newStream(audioTestHarnessStubArgumentCaptor.capture());
        Channel channel = audioTestHarnessStubArgumentCaptor.getValue().getChannel();
        assertEquals(mManagedChannel, channel);
    }

    @Test
    public void close_closesManagedChannel() throws Exception {
        GrpcAudioTestHarnessClient client = initMocksAndClient();

        client.close();

        verify(mManagedChannel).shutdown();
    }

    @Test
    public void close_closesCreatedAudioCaptureStreams() throws Exception {
        GrpcAudioTestHarnessClient client = initMocksAndClient();
        client.startCapture();

        client.close();

        verify(mGrpcAudioCaptureStream).close();
    }

    public GrpcAudioTestHarnessClient initMocksAndClient() throws Exception {
        when(mGrpcAudioCaptureStreamFactory.newStream(any())).thenReturn(mGrpcAudioCaptureStream);
        return GrpcAudioTestHarnessClient.builder()
                .setManagedChannel(mManagedChannel)
                .setCaptureStreamFactory(mGrpcAudioCaptureStreamFactory)
                .build();
    }
}
