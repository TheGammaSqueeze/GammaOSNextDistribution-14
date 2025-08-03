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

import com.android.media.audiotestharness.proto.AudioTestHarnessService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;

import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * {@link OutputStream} that streams data written to it to a provided {@link StreamObserver} in the
 * form of {@link AudioTestHarnessService.CaptureChunk}s.
 *
 * <p>This class is thread compatible but not thread safe. That is, with proper synchronization,
 * this class could be used by multiple threads, however there is no built-in synchronization.
 * However, the {@link #awaitClose()} methods are provided so that other threads can wait on the
 * this {@link OutputStream} to be closed before continuing.
 *
 * <p>This class should not be extended, however is left non-final for mocking purposes.
 */
public class CaptureChunkStreamObserverOutputStream extends OutputStream {
    private static final Logger LOGGER =
            Logger.getLogger(CaptureChunkStreamObserverOutputStream.class.getName());

    /**
     * Used for synchronizing actions during gRPC execution. Thus, a main thread can delegate
     * streaming actions to this {@link OutputStream} and then when done can take back control and
     * finish execution of the procedure.
     */
    private final CountDownLatch mCountDownLatch;

    /**
     * {@link StreamObserver} that underlies this {@link OutputStream} and is written to whenever
     * any of this class's write methods are called.
     */
    private final ServerCallStreamObserver<AudioTestHarnessService.CaptureChunk>
            mCaptureChunkStreamObserver;

    /**
     * Flag to track whether or not this {@link OutputStream} has been closed. If so, then does not
     * allow write actions to occur to prevent a stray call to onNext after onCompleted has been
     * called on the underlying {@link StreamObserver}.
     *
     * <p>Atomic since this value could be written to and read from separate threads thus ensuring
     * that no race condition occurs where the thread writing to the stream thinks that it is open
     * while the stream is in fact closed.
     */
    private AtomicBoolean mClosed = new AtomicBoolean(false);

    private CaptureChunkStreamObserverOutputStream(
            ServerCallStreamObserver<AudioTestHarnessService.CaptureChunk>
                    captureChunkStreamObserver,
            CountDownLatch countDownLatch) {
        mCaptureChunkStreamObserver = captureChunkStreamObserver;
        mCountDownLatch = countDownLatch;
        LOGGER.finest("new CaptureChunkStreamObserverOutputStream");
    }

    public static CaptureChunkStreamObserverOutputStream create(
            ServerCallStreamObserver<AudioTestHarnessService.CaptureChunk>
                    captureChunkStreamObserver) {
        return create(captureChunkStreamObserver, new CountDownLatch(1));
    }

    @VisibleForTesting
    static CaptureChunkStreamObserverOutputStream create(
            ServerCallStreamObserver<AudioTestHarnessService.CaptureChunk>
                    captureChunkStreamObserver,
            CountDownLatch countDownLatch) {
        return new CaptureChunkStreamObserverOutputStream(
                Preconditions.checkNotNull(captureChunkStreamObserver),
                Preconditions.checkNotNull(countDownLatch));
    }

    @Override
    public void write(int b) {
        Preconditions.checkState(
                !mClosed.get(),
                "CaptureChunkStreamObserverOutputStream has already been closed and cannot be"
                        + " written to.");

        byte[] toWrite = new byte[1];

        // Grab only the lowest byte per the docs for the write(int) method.
        toWrite[0] = (byte) (b & 0xFF);

        write(toWrite);
    }

    @Override
    public void write(byte[] b) {
        Preconditions.checkState(
                !mClosed.get(),
                "CaptureChunkStreamObserverOutputStream has already been closed and cannot be"
                        + " written to.");
        write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) {
        Preconditions.checkState(
                !mClosed.get(),
                "CaptureChunkStreamObserverOutputStream has already been closed and cannot be"
                        + " written to.");

        ByteString chunkBytes = ByteString.copyFrom(b, off, len);

        AudioTestHarnessService.CaptureChunk captureChunk =
                AudioTestHarnessService.CaptureChunk.newBuilder().setData(chunkBytes).build();

        // Skip sending any chunks that are written to the stream after cancellation.
        //
        // Since the writing to this Output Stream comes from a separate thread from the original
        // gRPC handling thread, there is a chance that an extra chunk of data will be written
        // before the cancellation can propagate to the AudioCapturer that is publishing data. In
        // these cases, simply ignore the extra chunk of data and log that it was seen.
        if (mCaptureChunkStreamObserver.isCancelled()) {
            LOGGER.fine("Extra chunk sent after cancellation will be discarded");
        } else {
            mCaptureChunkStreamObserver.onNext(captureChunk);
        }
    }

    @Override
    public void close() {
        mClosed.set(true);
        mCountDownLatch.countDown();
        LOGGER.info("Stream Closed");
    }

    public boolean isClosed() {
        return mClosed.get();
    }

    /**
     * Causes the current thread to wait until the stream is closed.
     *
     * @throws InterruptedException if the waiting thread is interrupted before this stream is
     *     closed.
     */
    public void awaitClose() throws InterruptedException {
        mCountDownLatch.await();
    }

    /**
     * Causes the current thread to wait until the stream is closed or the provided timeout has
     * elapsed. Returns true if the stream is closed before the end of the timeout, false otherwise.
     *
     * @param timeout the maximum wait time
     * @param timeUnit the {@link TimeUnit} of the timeout arg
     * @throws InterruptedException if the waiting thread is interrupted before this stream is
     *     closed.
     */
    public boolean awaitClose(long timeout, TimeUnit timeUnit) throws InterruptedException {
        return mCountDownLatch.await(timeout, timeUnit);
    }
}
