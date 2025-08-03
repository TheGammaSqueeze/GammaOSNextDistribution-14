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

package com.android.media.audiotestharness.server.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.media.audiotestharness.proto.AudioTestHarnessGrpc;
import com.android.media.audiotestharness.proto.AudioTestHarnessService;
import com.android.media.audiotestharness.server.config.SharedHostConfiguration;
import com.android.media.audiotestharness.server.core.AudioCapturer;
import com.android.media.audiotestharness.server.core.AudioSystemService;

import com.google.protobuf.ByteString;

import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;

import org.hamcrest.CustomMatcher;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/** Tests for the {@link AudioTestHarnessImpl} class. */
@RunWith(JUnit4.class)
public class AudioTestHarnessImplTests {

    public static final byte[] TEST_PAYLOAD = {0x1, 0x2, 0x3, 0x4};

    @Rule public GrpcCleanupRule mGrpcCleanupRule = new GrpcCleanupRule();

    @Rule public MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Rule public ExpectedException mExceptionRule = ExpectedException.none();

    @Mock AudioSystemService mAudioSystemService;

    @Mock AudioCapturer mAudioCapturer;

    @Mock AudioCaptureSession mAudioCaptureSession;

    @Mock AudioCaptureSessionFactory mAudioCaptureSessionFactory;

    /** Stubs used to communicate with the service-under-test. */
    private AudioTestHarnessGrpc.AudioTestHarnessBlockingStub mBlockingStub;

    private AudioTestHarnessGrpc.AudioTestHarnessStub mStub;

    @Before
    public void setUp() throws Exception {
        String serverName = InProcessServerBuilder.generateName();

        // Create and Start In-Process Server
        mGrpcCleanupRule.register(
                InProcessServerBuilder.forName(serverName)
                        .directExecutor()
                        .addService(
                                new AudioTestHarnessImpl(
                                        mAudioSystemService,
                                        mAudioCaptureSessionFactory,
                                        SharedHostConfiguration.getDefault()))
                        .build()
                        .start());

        // Create and Start Stubs for interacting with the Service
        ManagedChannel channel =
                mGrpcCleanupRule.register(
                        InProcessChannelBuilder.forName(serverName).directExecutor().build());
        mBlockingStub = AudioTestHarnessGrpc.newBlockingStub(channel);
        mStub = AudioTestHarnessGrpc.newStub(channel);

        // Ensure the mocks output is valid.
        when(mAudioSystemService.createWithDefaultAudioFormat(any())).thenReturn(mAudioCapturer);
        when(mAudioCaptureSessionFactory.createCaptureSession(any(), any()))
                .then(
                        (inv) -> {

                            // Ensure that the stream observer is closed properly so it can be
                            // cleaned up.
                            ServerCallStreamObserver<AudioTestHarnessService.CaptureChunk>
                                    streamObserver = inv.getArgument(0);
                            streamObserver.onCompleted();

                            return mAudioCaptureSession;
                        });
    }

    @Test
    public void capture_properlyAllocatesDefaultCapturer() throws Exception {
        mBlockingStub.capture(AudioTestHarnessService.CaptureRequest.getDefaultInstance());
        verify(mAudioSystemService)
                .createWithDefaultAudioFormat(
                        SharedHostConfiguration.getDefault().captureDevices().get(0));
    }

    @Test
    public void capture_properlyCreatesCaptureSession() throws Exception {
        mBlockingStub.capture(AudioTestHarnessService.CaptureRequest.getDefaultInstance());
        verify(mAudioCaptureSessionFactory).createCaptureSession(any(), eq(mAudioCapturer));
    }

    @Test
    public void capture_callsStopOnSessionWhenCanceled() throws Exception {
        AtomicReference<StreamObserver<AudioTestHarnessService.CaptureChunk>>
                streamObserverReference = new AtomicReference<>();
        reset(mAudioCaptureSessionFactory);
        when(mAudioCaptureSessionFactory.createCaptureSession(any(), any()))
                .thenAnswer(
                        (invocation -> {
                            // Grab a reference to the stream observer, then return the mock.
                            streamObserverReference.set(invocation.getArgument(0));
                            return mAudioCaptureSession;
                        }));

        doAnswer(
                        (invocation) -> {
                            if (streamObserverReference.get() != null) {
                                streamObserverReference
                                        .get()
                                        .onNext(
                                                AudioTestHarnessService.CaptureChunk.newBuilder()
                                                        .setData(ByteString.copyFrom(TEST_PAYLOAD))
                                                        .build());
                            }
                            return null;
                        })
                .when(mAudioCaptureSession)
                .start();

        Context.CancellableContext cancellableContext = Context.current().withCancellation();
        cancellableContext.run(
                () ->
                        mStub.capture(
                                AudioTestHarnessService.CaptureRequest.getDefaultInstance(),
                                new StreamObserver<AudioTestHarnessService.CaptureChunk>() {
                                    @Override
                                    public void onNext(AudioTestHarnessService.CaptureChunk value) {
                                        cancellableContext.cancel(Status.CANCELLED.asException());
                                    }

                                    @Override
                                    public void onError(Throwable t) {}

                                    @Override
                                    public void onCompleted() {}
                                }));

        verify(mAudioCaptureSession).stop();
    }

    @Test
    public void capture_properlyStartsCaptureSession() throws Exception {
        mBlockingStub.capture(AudioTestHarnessService.CaptureRequest.getDefaultInstance());
        verify(mAudioCaptureSession).start();
    }

    @Test
    public void capture_throwsProperStatusException_failureToOpenCapturer() throws Exception {
        when(mAudioSystemService.createWithDefaultAudioFormat(any()))
                .thenThrow(new IOException("Some exception occurred."));

        mExceptionRule.expect(
                generateCustomMatcherForExpected(
                        /* expectedDescription= */ String.format(
                                "Failed to allocate AudioCapturer %s",
                                SharedHostConfiguration.getDefault().captureDevices().get(0)),
                        Status.UNAVAILABLE));
        mBlockingStub
                .capture(AudioTestHarnessService.CaptureRequest.getDefaultInstance())
                .forEachRemaining(chunk -> {});
    }

    @Test
    public void capture_throwsProperStatusException_failureToStartCapturer() throws Exception {
        reset(mAudioCaptureSessionFactory);
        when(mAudioCaptureSessionFactory.createCaptureSession(any(), any()))
                .thenReturn(mAudioCaptureSession);
        doThrow(new IOException("Capturer Start Failure!")).when(mAudioCaptureSession).start();

        mExceptionRule.expect(
                generateCustomMatcherForExpected(
                        /* expectedDescription= */ "Capturer Start Failure!", Status.INTERNAL));

        mBlockingStub
                .capture(AudioTestHarnessService.CaptureRequest.getDefaultInstance())
                .forEachRemaining(chunk -> {});
    }

    /**
     * Generates a {@link org.hamcrest.Matcher} that matches a given {@link StatusRuntimeException}
     * if the description and status code parameters are an exact match.
     */
    public Matcher<StatusRuntimeException> generateCustomMatcherForExpected(
            String expectedDescription, Status expectedStatus) {
        return new CustomMatcher<StatusRuntimeException>(
                String.format(
                        "StatusRuntimeException with Message (%s) and Status (%s)",
                        expectedDescription, expectedStatus)) {
            @Override
            public boolean matches(Object item) {
                if (item instanceof StatusRuntimeException) {
                    StatusRuntimeException exception = (StatusRuntimeException) item;
                    return exception.getStatus().getCode().equals(expectedStatus.getCode())
                            && exception.getStatus().getDescription() != null
                            && exception.getStatus().getDescription().equals(expectedDescription);
                }
                return false;
            }
        };
    }
}
