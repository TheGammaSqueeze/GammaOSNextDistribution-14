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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.android.media.audiotestharness.proto.AudioTestHarnessGrpc;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;

import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class GrpcAudioCaptureStreamTests {

    @Rule public GrpcCleanupRule mGrpcCleanupRule = new GrpcCleanupRule();

    @Rule public MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Rule public ExpectedException mExceptionRule = ExpectedException.none();

    @Mock ScheduledExecutorService mScheduledExecutorService;

    private ListeningExecutorService mListeningExecutorService;

    private GrpcAudioCaptureStreamFactory mGrpcAudioCaptureStreamFactory;

    private AudioTestHarnessGrpc.AudioTestHarnessStub mAudioTestHarnessStub;

    @Before
    public void setUp() throws Exception {
        mListeningExecutorService = MoreExecutors.newDirectExecutorService();

        String serverName = InProcessServerBuilder.generateName();

        mGrpcCleanupRule.register(
                InProcessServerBuilder.forName(serverName)
                        .executor(mListeningExecutorService)
                        .addService(new AudioTestHarnessTestImpl())
                        .build()
                        .start());

        ManagedChannel channel =
                mGrpcCleanupRule.register(
                        InProcessChannelBuilder.forName(serverName)
                                .executor(mListeningExecutorService)
                                .build());
        mAudioTestHarnessStub = AudioTestHarnessGrpc.newStub(channel);

        mGrpcAudioCaptureStreamFactory =
                GrpcAudioCaptureStreamFactory.create(mScheduledExecutorService);
    }

    @Test
    public void create_returnsNotNullInstance() throws Exception {
        assertNotNull(
                GrpcAudioCaptureStream.create(mAudioTestHarnessStub, mScheduledExecutorService));
    }

    @Test
    public void create_schedulesCancellationTaskWithProperDeadline() throws Exception {
        ArgumentCaptor<Long> timeValueCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<TimeUnit> timeUnitCaptor = ArgumentCaptor.forClass(TimeUnit.class);

        GrpcAudioCaptureStream.create(mAudioTestHarnessStub, mScheduledExecutorService);

        // Grab the timeout from the call to the Scheduled Executor Service as a Duration.
        verify(mScheduledExecutorService)
                .schedule((Runnable) any(), timeValueCaptor.capture(), timeUnitCaptor.capture());
        Duration actualScheduledDuration =
                Duration.ofNanos(
                        TimeUnit.NANOSECONDS.convert(
                                timeValueCaptor.getValue(), timeUnitCaptor.getValue()));

        // Ensure that we are within our expected timeout within a 1s epsilon.
        Duration difference = actualScheduledDuration.minus(Duration.ofHours(1)).abs();
        assertTrue(difference.getSeconds() < 1L);
    }

    @Test(expected = NullPointerException.class)
    public void newStream_throwsNullPointerException_nullStub() throws Exception {
        assertNotNull(mGrpcAudioCaptureStreamFactory.newStream(/* audioTestHarnessStub= */ null));
    }

    @Test(expected = NullPointerException.class)
    public void create_throwsNullPointerException_nullStub() throws Exception {
        GrpcAudioCaptureStream.create(/* audioTestHarnessStub= */ null, mScheduledExecutorService);
    }

    @Test(expected = NullPointerException.class)
    public void create_throwsNullPointerException_nullScheduledExecutorService() throws Exception {
        GrpcAudioCaptureStream.create(mAudioTestHarnessStub, /* scheduledExecutorService= */ null);
    }

    @Test
    public void available_throwsProperIOException_grpcError() throws Exception {
        expectGrpcCommunicationErrorException();
        GrpcAudioCaptureStream grpcAudioCaptureStream = buildCaptureStreamWithPendingGrpcError();

        grpcAudioCaptureStream.available();
    }

    @Test
    public void read_returnsProperDataFromGrpc() throws Exception {
        GrpcAudioCaptureStream grpcAudioCaptureStream =
                GrpcAudioCaptureStream.create(mAudioTestHarnessStub, mScheduledExecutorService);

        byte[] readBytes = new byte[AudioTestHarnessTestImpl.MESSAGE.length];

        int numBytesRead = grpcAudioCaptureStream.read(readBytes);

        assertEquals(AudioTestHarnessTestImpl.MESSAGE.length, numBytesRead);
        assertArrayEquals(AudioTestHarnessTestImpl.MESSAGE, readBytes);
    }

    @Test
    public void read_singleByte_throwsProperIOException_whenStreamClosed() throws Exception {
        expectInternalErrorException();
        GrpcAudioCaptureStream grpcAudioCaptureStream =
                GrpcAudioCaptureStream.create(mAudioTestHarnessStub, mScheduledExecutorService);
        grpcAudioCaptureStream.close();

        grpcAudioCaptureStream.read();
    }

    @Test
    public void read_multipleBytes_throwsProperIOException_whenStreamClosed() throws Exception {
        expectInternalErrorException();
        GrpcAudioCaptureStream grpcAudioCaptureStream =
                GrpcAudioCaptureStream.create(mAudioTestHarnessStub, mScheduledExecutorService);

        grpcAudioCaptureStream.close();

        grpcAudioCaptureStream.read(new byte[AudioTestHarnessTestImpl.MESSAGE.length]);
    }

    @Test
    public void read_multipleBytesWithOffset_throwsProperIOException_whenStreamClosed()
            throws Exception {
        expectInternalErrorException();
        GrpcAudioCaptureStream grpcAudioCaptureStream =
                GrpcAudioCaptureStream.create(mAudioTestHarnessStub, mScheduledExecutorService);
        grpcAudioCaptureStream.close();

        grpcAudioCaptureStream.read(
                new byte[AudioTestHarnessTestImpl.MESSAGE.length], /* off= */ 1, /* len= */ 2);
    }

    @Test
    public void read_throwsIOException_grpcError() throws Exception {
        expectGrpcCommunicationErrorException();
        GrpcAudioCaptureStream grpcAudioCaptureStream = buildCaptureStreamWithPendingGrpcError();

        grpcAudioCaptureStream.read(new byte[AudioTestHarnessTestImpl.MESSAGE.length]);
    }

    @Test
    public void read_shortSamples_readsAsExpected() throws Exception {
        GrpcAudioCaptureStream grpcAudioCaptureStream =
                GrpcAudioCaptureStream.create(mAudioTestHarnessStub, mScheduledExecutorService);
        short[] expected = new short[AudioTestHarnessTestImpl.MESSAGE.length / 2];
        ByteBuffer.wrap(AudioTestHarnessTestImpl.MESSAGE)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer()
                .get(expected);

        short[] actual = new short[AudioTestHarnessTestImpl.MESSAGE.length / 2];
        int numRead =
                grpcAudioCaptureStream.read(actual, /* offset= */ 0, /* max= */ actual.length);

        assertArrayEquals(expected, actual);
        assertEquals(AudioTestHarnessTestImpl.MESSAGE.length / 2, numRead);
    }

    @Test(expected = IllegalArgumentException.class)
    public void read_shortSamples_throwsIllegalArgumentException_negativeOffset() throws Exception {
        GrpcAudioCaptureStream grpcAudioCaptureStream =
                GrpcAudioCaptureStream.create(mAudioTestHarnessStub, mScheduledExecutorService);
        grpcAudioCaptureStream.read(/* samples= */ new short[4], /* offset= */ -25, /* max= */ 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void read_shortSamples_throwsIllegalArgumentException_offsetTooLarge() throws Exception {
        GrpcAudioCaptureStream grpcAudioCaptureStream =
                GrpcAudioCaptureStream.create(mAudioTestHarnessStub, mScheduledExecutorService);
        grpcAudioCaptureStream.read(/* samples= */ new short[4], /* offset= */ 25, /* max= */ 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void read_shortSamples_throwsIllegalArgumentException_negativeLen() throws Exception {
        GrpcAudioCaptureStream grpcAudioCaptureStream =
                GrpcAudioCaptureStream.create(mAudioTestHarnessStub, mScheduledExecutorService);
        grpcAudioCaptureStream.read(/* samples= */ new short[4], /* offset= */ 0, /* max= */ -24);
    }

    @Test(expected = IllegalArgumentException.class)
    public void read_shortSamples_throwsIllegalArgumentException_lenTooLarge() throws Exception {
        GrpcAudioCaptureStream grpcAudioCaptureStream =
                GrpcAudioCaptureStream.create(mAudioTestHarnessStub, mScheduledExecutorService);
        grpcAudioCaptureStream.read(/* samples= */ new short[4], /* offset= */ 0, /* max= */ 24);
    }

    @Test
    public void reset_throwsProperIOException_whenStreamClosed() throws Exception {
        expectInternalErrorException();
        GrpcAudioCaptureStream grpcAudioCaptureStream =
                GrpcAudioCaptureStream.create(mAudioTestHarnessStub, mScheduledExecutorService);
        grpcAudioCaptureStream.close();

        grpcAudioCaptureStream.reset();
    }

    @Test
    public void reset_throwsProperIOException_grpcError() throws Exception {
        expectGrpcCommunicationErrorException();
        GrpcAudioCaptureStream grpcAudioCaptureStream = buildCaptureStreamWithPendingGrpcError();

        grpcAudioCaptureStream.reset();
    }

    @Test
    public void skip_throwsProperIOException_grpcError() throws Exception {
        expectGrpcCommunicationErrorException();
        GrpcAudioCaptureStream grpcAudioCaptureStream = buildCaptureStreamWithPendingGrpcError();

        grpcAudioCaptureStream.skip(/* n= */ 1);
    }

    /**
     * Builds a new {@link GrpcAudioCaptureStream} with a pending gRPC error internally.
     *
     * <p>This method is used to verify that gRPC errors take precedence and are propagated properly
     * to callers.
     */
    private GrpcAudioCaptureStream buildCaptureStreamWithPendingGrpcError() throws Exception {
        AudioTestHarnessGrpc.AudioTestHarnessStub disconnectedStub =
                AudioTestHarnessGrpc.newStub(
                        mGrpcCleanupRule.register(
                                ManagedChannelBuilder.forAddress("localhost", 12345)
                                        .executor(mListeningExecutorService)
                                        .build()));

        GrpcAudioCaptureStream grpcAudioCaptureStream =
                GrpcAudioCaptureStream.create(disconnectedStub, mScheduledExecutorService);

        // Read once from the stream, giving the gRPC exception time to propagate. Even with
        // the direct executor this is necessary and *should* be deterministic.
        grpcAudioCaptureStream.read(new byte[1]);
        return grpcAudioCaptureStream;
    }

    /** Configures the exception rule to expect the gRPC Communication Error exception. */
    private void expectGrpcCommunicationErrorException() throws Exception {
        mExceptionRule.expectMessage("Audio Test Harness gRPC Communication Error");
        mExceptionRule.expectCause(CoreMatchers.notNullValue(Throwable.class));
    }

    /** Configures the exception rule to expect the gRPC Internal Error exception. */
    private void expectInternalErrorException() throws Exception {
        mExceptionRule.expectMessage("Audio Test Harness gRPC Internal Error");
        mExceptionRule.expectCause(CoreMatchers.notNullValue(Throwable.class));
    }
}
