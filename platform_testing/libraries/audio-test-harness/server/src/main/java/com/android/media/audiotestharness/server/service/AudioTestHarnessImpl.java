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

import com.android.media.audiotestharness.proto.AudioDeviceOuterClass;
import com.android.media.audiotestharness.proto.AudioTestHarnessGrpc;
import com.android.media.audiotestharness.proto.AudioTestHarnessService;
import com.android.media.audiotestharness.server.config.SharedHostConfiguration;
import com.android.media.audiotestharness.server.core.AudioCapturer;
import com.android.media.audiotestharness.server.core.AudioSystemService;

import com.google.inject.Inject;

import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@inheritDoc}
 *
 * <p>Core service implementation for the Audio Test Harness that utilizes that Java Sound API to
 * expose audio devices connected to a host to client devices for capture and playback.
 */
public final class AudioTestHarnessImpl extends AudioTestHarnessGrpc.AudioTestHarnessImplBase {

    private static final Logger LOGGER = Logger.getLogger(AudioTestHarnessImpl.class.getName());

    /** The maximum duration that a client can capture before the server manually stops capture. */
    public static final Duration MAX_CAPTURE_DURATION = Duration.ofHours(1);

    /** {@link AudioSystemService} that should be used to access audio system resources. */
    private final AudioSystemService mAudioSystemService;

    /** Factory for StreamObserverOutputStreams used during the procedure handling process. */
    private final AudioCaptureSessionFactory mAudioCaptureSessionFactory;

    private final SharedHostConfiguration mSharedHostConfiguration;

    @Inject
    public AudioTestHarnessImpl(
            AudioSystemService audioSystemService,
            AudioCaptureSessionFactory audioCaptureSessionFactory,
            SharedHostConfiguration sharedHostConfiguration) {
        mAudioSystemService = audioSystemService;
        mAudioCaptureSessionFactory = audioCaptureSessionFactory;
        mSharedHostConfiguration = sharedHostConfiguration;
    }

    @Override
    public void capture(
            AudioTestHarnessService.CaptureRequest request,
            StreamObserver<AudioTestHarnessService.CaptureChunk> responseObserver) {
        ServerCallStreamObserver<AudioTestHarnessService.CaptureChunk> serverCallResponseObserver =
                (ServerCallStreamObserver<AudioTestHarnessService.CaptureChunk>) responseObserver;
        LOGGER.info("Handling Capture procedure");

        // Allocate the default AudioCapturer from the Audio System Service.
        AudioCapturer capturer;
        AudioDeviceOuterClass.AudioDevice captureDevice =
                mSharedHostConfiguration.captureDevices().get(0);
        try {
            // Attempt to allocate with the first requested device, this list should always contain
            // at least one device.
            capturer = mAudioSystemService.createWithDefaultAudioFormat(captureDevice);
        } catch (IOException ioe) {
            LOGGER.log(
                    Level.SEVERE,
                    String.format("Failed to allocate AudioCapturer %s", captureDevice),
                    ioe);
            serverCallResponseObserver.onError(
                    Status.UNAVAILABLE
                            .withCause(ioe)
                            .withDescription(
                                    String.format(
                                            "Failed to allocate AudioCapturer %s", captureDevice))
                            .asException());
            return;
        } catch (IndexOutOfBoundsException ioobe) {
            LOGGER.log(
                    Level.SEVERE,
                    "Invalid Shared Host Configuration, no capture device provided. This "
                            + "indicates there is an issue with the server as this"
                            + " should never happen.",
                    ioobe);
            serverCallResponseObserver.onError(
                    Status.INTERNAL.withDescription("Internal Configuration Error.").asException());
            return;
        }

        // Start a new capture session
        AudioCaptureSession captureSession =
                mAudioCaptureSessionFactory.createCaptureSession(
                        serverCallResponseObserver, capturer);

        // Start capturing and continue until either cancelled by the client or MAX_CAPTURE_DURATION
        // is hit.
        serverCallResponseObserver.setOnCancelHandler(captureSession::stop);
        try {
            captureSession.start();
        } catch (IOException ioe) {
            LOGGER.log(Level.SEVERE, "Internal Error while Capturing", ioe);
            serverCallResponseObserver.onError(
                    Status.INTERNAL.withCause(ioe).withDescription(ioe.getMessage()).asException());
        }
    }
}
