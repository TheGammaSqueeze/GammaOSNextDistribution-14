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
import com.android.media.audiotestharness.server.core.AudioCapturer;

import com.google.inject.Inject;

import io.grpc.stub.ServerCallStreamObserver;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/** Base factory for {@link AudioCaptureSession}s. */
public class AudioCaptureSessionFactoryImpl implements AudioCaptureSessionFactory {

    /**
     * Incrementing value to create identifiers for capture streams for logging and debugging
     * purposes. This value doesn't need to be unique across instances of the server, just unique
     * per run, thus an incrementing integer is used.
     *
     * <p>Since different calls to the Capture procedure could come across threads, and {@link
     * AtomicInteger} is used.
     */
    private static AtomicInteger sIdBase = new AtomicInteger(/* initialValue= */ 1);

    private static final Logger LOGGER =
            Logger.getLogger(AudioCaptureSessionFactoryImpl.class.getName());

    private final StreamObserverOutputStreamFactory mStreamObserverOutputStreamFactory;

    @Inject
    public AudioCaptureSessionFactoryImpl(
            StreamObserverOutputStreamFactory streamObserverOutputStreamFactory) {
        mStreamObserverOutputStreamFactory = streamObserverOutputStreamFactory;
    }

    @Override
    public AudioCaptureSession createCaptureSession(
            ServerCallStreamObserver<AudioTestHarnessService.CaptureChunk> chunkStreamObserver,
            AudioCapturer audioCapturer) {
        int id = sIdBase.getAndIncrement();
        LOGGER.info(String.format("Starting new capture session with id %d", id));

        return AudioCaptureSession.create(
                id,
                audioCapturer,
                mStreamObserverOutputStreamFactory.createNewCaptureChunkStreamObserverOutputStream(
                        chunkStreamObserver));
    }
}
