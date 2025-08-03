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

import com.android.media.audiotestharness.client.core.AudioCaptureStream;
import com.android.media.audiotestharness.client.core.AudioTestHarnessClient;
import com.android.media.audiotestharness.client.core.AudioTestHarnessCommunicationException;
import com.android.media.audiotestharness.common.Defaults;
import com.android.media.audiotestharness.proto.AudioTestHarnessGrpc;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/** {@link AudioTestHarnessClient} that uses gRPC as its communication method. */
public class GrpcAudioTestHarnessClient extends AudioTestHarnessClient {
    private static final Logger LOGGER =
            Logger.getLogger(GrpcAudioTestHarnessClient.class.getName());

    private static final int MIN_PORT = 0;
    private static final int MAX_PORT = 65535;
    private static final int DEFAULT_NUM_THREADS = 8;
    private static final String LOCALHOST = "localhost";

    private final ManagedChannel mManagedChannel;
    private final GrpcAudioCaptureStreamFactory mGrpcAudioCaptureStreamFactory;

    private GrpcAudioTestHarnessClient(
            GrpcAudioCaptureStreamFactory grpcAudioCaptureStreamFactory,
            ManagedChannel managedChannel) {
        mManagedChannel = managedChannel;
        mGrpcAudioCaptureStreamFactory = grpcAudioCaptureStreamFactory;
    }

    public static GrpcAudioTestHarnessClient.Builder builder() {
        return new Builder().setAddress(LOCALHOST, Defaults.DEVICE_PORT);
    }

    @Override
    public AudioCaptureStream startCapture() {
        AudioCaptureStream newStream;

        try {
            newStream =
                    mGrpcAudioCaptureStreamFactory.newStream(
                            AudioTestHarnessGrpc.newStub(mManagedChannel));
        } catch (IOException ioe) {
            throw new AudioTestHarnessCommunicationException(
                    "Unable to start a new capture stream.", ioe);
        }

        mAudioCaptureStreams.add(newStream);
        return newStream;
    }

    @Override
    public void close() {
        mAudioCaptureStreams.forEach(
                stream -> {
                    try {
                        stream.close();
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Failed to close AudioCaptureStream", e);
                    }
                });

        mManagedChannel.shutdown();
    }

    /**
     * Builder for {@link GrpcAudioTestHarnessClient}s that allows for the injection of certain
     * members for testing purposes.
     */
    public static class Builder {

        private String mHostname;
        private int mPort;
        private ScheduledExecutorService mExecutor;
        private GrpcAudioCaptureStreamFactory mGrpcAudioCaptureStreamFactory;
        private ManagedChannel mManagedChannel;

        private Builder() {}

        public Builder setAddress(String hostname, int port) {
            Preconditions.checkNotNull(hostname, "Hostname cannot be null");
            Preconditions.checkArgument(
                    port >= MIN_PORT && port <= MAX_PORT,
                    String.format(
                            Locale.getDefault(),
                            "Port expected in range [%d, %d]",
                            MIN_PORT,
                            MAX_PORT));

            mHostname = hostname;
            mPort = port;

            return this;
        }

        public Builder setExecutor(ScheduledExecutorService executor) {
            mExecutor = executor;
            return this;
        }

        @VisibleForTesting
        Builder setCaptureStreamFactory(
                GrpcAudioCaptureStreamFactory grpcAudioCaptureStreamFactory) {
            Preconditions.checkNotNull(
                    grpcAudioCaptureStreamFactory, "grpcAudioCaptureStreamFactory cannot be null");
            mGrpcAudioCaptureStreamFactory = grpcAudioCaptureStreamFactory;
            return this;
        }

        @VisibleForTesting
        Builder setManagedChannel(ManagedChannel managedChannel) {
            mManagedChannel = managedChannel;
            return this;
        }

        public GrpcAudioTestHarnessClient build() {
            if (mManagedChannel == null) {
                Preconditions.checkState(mHostname != null, "Address must be set.");

                if (mExecutor == null) {
                    mExecutor = Executors.newScheduledThreadPool(DEFAULT_NUM_THREADS);
                }

                if (mGrpcAudioCaptureStreamFactory == null) {
                    mGrpcAudioCaptureStreamFactory =
                            GrpcAudioCaptureStreamFactory.create(mExecutor);
                }

                mManagedChannel =
                        ManagedChannelBuilder.forAddress(mHostname, mPort)
                                .usePlaintext()
                                .executor(mExecutor)
                                .build();
                LOGGER.info(String.format("New Client on for %s:%d", mHostname, mPort));
            }

            return new GrpcAudioTestHarnessClient(mGrpcAudioCaptureStreamFactory, mManagedChannel);
        }
    }
}
