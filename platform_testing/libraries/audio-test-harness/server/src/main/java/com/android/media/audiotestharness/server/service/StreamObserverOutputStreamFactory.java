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

import io.grpc.stub.ServerCallStreamObserver;

/**
 * Factory class for {@link java.io.OutputStream}s that package written data into specialized the
 * proper format before writing the packaged data to a {@link io.grpc.stub.StreamObserver}
 */
public class StreamObserverOutputStreamFactory {

    /**
     * Creates a new {@link CaptureChunkStreamObserverOutputStream} for the provided {@link
     * ServerCallStreamObserver}.
     */
    public CaptureChunkStreamObserverOutputStream createNewCaptureChunkStreamObserverOutputStream(
            ServerCallStreamObserver<AudioTestHarnessService.CaptureChunk> streamObserver) {
        return CaptureChunkStreamObserverOutputStream.create(streamObserver);
    }
}
