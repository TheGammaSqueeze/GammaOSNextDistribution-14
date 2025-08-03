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

import com.android.media.audiotestharness.server.core.AudioCapturer;

import com.google.common.base.Preconditions;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the lifecycle of an {@link AudioCapturer} for the {@link AudioTestHarnessImpl}'s handling
 * of the Capture gRPC procedure.
 */
public class AudioCaptureSession {
    private static final Logger LOGGER = Logger.getLogger(AudioCaptureSession.class.getName());

    /** The identifier for this session which should be used in logging and errors. */
    private final int mSessionId;

    /** The underlying {@link AudioCapturer} for this session. */
    private final AudioCapturer mAudioCapturer;

    /**
     * The {@link CaptureChunkStreamObserverOutputStream} that is outputting to the gRPC {@link
     * io.grpc.stub.StreamObserver} from the gRPC call.
     */
    private final CaptureChunkStreamObserverOutputStream mCaptureChunkStreamObserverOutputStream;

    private AudioCaptureSession(
            int sessionId,
            AudioCapturer audioCapturer,
            CaptureChunkStreamObserverOutputStream captureChunkStreamObserverOutputStream) {
        LOGGER.finest(
                String.format(
                        "new AudioCaptureSession(id=%d, audioCapturer=%s,"
                                + " captureChunkStreamObserverOutputStream=%s)",
                        sessionId, audioCapturer, captureChunkStreamObserverOutputStream));
        mSessionId = sessionId;
        mAudioCapturer = audioCapturer;
        mCaptureChunkStreamObserverOutputStream = captureChunkStreamObserverOutputStream;
    }

    /** Creates a new {@link AudioCaptureSession}. */
    public static AudioCaptureSession create(
            int id,
            AudioCapturer audioCapturer,
            CaptureChunkStreamObserverOutputStream captureChunkStreamObserverOutputStream) {
        Preconditions.checkNotNull(audioCapturer);
        Preconditions.checkNotNull(captureChunkStreamObserverOutputStream);

        LOGGER.info(
                String.format(
                        "id=%d - Attaching CaptureChunkStreamObserverOutputStream to AudioCapturer",
                        id));
        audioCapturer.attachOutput(captureChunkStreamObserverOutputStream);

        return new AudioCaptureSession(id, audioCapturer, captureChunkStreamObserverOutputStream);
    }

    /**
     * Starts the capture of audio.
     *
     * @throws IOException if any errors occur while opening the underlying {@link AudioCapturer}
     */
    public void start() throws IOException {
        try {
            mAudioCapturer.open();
        } catch (IOException ioe) {
            throw new IOException(
                    String.format(
                            "Failed to open AudioCapturer for Audio Capture Session %d",
                            mSessionId),
                    ioe);
        }
        LOGGER.info(String.format("id=%d - Capture Session Started", mSessionId));
    }

    /** Stops the capture of audio and cleans up any associated resources. */
    public void stop() {
        try {
            mAudioCapturer.close();
        } catch (IOException ioe) {
            LOGGER.log(
                    Level.WARNING,
                    String.format(
                            "Failed to close AudioCapturer, there may be a resource leak for Audio"
                                    + " Capture Session %d",
                            mSessionId));
        }
        mCaptureChunkStreamObserverOutputStream.close();
        LOGGER.info(String.format("id=%d - Capture Session Stopped", mSessionId));
    }

    /**
     * Puts the current thread to sleep until the session is stopped or the provided timeout is
     * reached at which point the method returns.
     */
    public void awaitStop(long timeout, TimeUnit timeUnit) throws IOException {
        LOGGER.info(
                String.format(
                        "id=%d - Waiting for Stop up to %d %s", mSessionId, timeout, timeUnit));

        boolean didFinish;
        try {
            didFinish = mCaptureChunkStreamObserverOutputStream.awaitClose(timeout, timeUnit);
        } catch (InterruptedException ie) {
            throw new IOException(
                    String.format(
                            "Interrupted while waiting for capture to finish for Audio Capture"
                                    + " Session %d",
                            mSessionId),
                    ie);
        }

        if (!didFinish) {
            LOGGER.warning(
                    String.format(
                            "id=%d - Capture session reached maximum timeout duration of %d %s",
                            mSessionId, timeout, timeUnit));
            stop();
        }
    }

    public int getSessionId() {
        return mSessionId;
    }
}
