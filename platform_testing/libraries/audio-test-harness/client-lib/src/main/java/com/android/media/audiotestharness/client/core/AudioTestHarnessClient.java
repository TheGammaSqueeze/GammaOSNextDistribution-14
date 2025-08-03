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

package com.android.media.audiotestharness.client.core;

import java.util.HashSet;
import java.util.Set;

/**
 * API for the Audio Test Harness infrastructure.
 *
 * <p>Exposes methods for starting interactions with the infrastructure, then the returned can be
 * used for interaction with each created session.
 *
 * <p>Implementing classes should manage the resources for both the standard connection to the
 * host-side server but also any resources for the underlying sessions created within the system.
 */
public abstract class AudioTestHarnessClient implements AutoCloseable {

    /**
     * {@link Set} of all {@link AudioCaptureStream}s created by this client so that when the client
     * is closed, all of the associated streams can also be closed.
     */
    protected Set<AudioCaptureStream> mAudioCaptureStreams;

    protected AudioTestHarnessClient() {
        mAudioCaptureStreams = new HashSet<>();
    }

    /**
     * Starts an Audio Capture Session and returns a {@link AudioCaptureStream} for interacting with
     * that session.
     */
    public abstract AudioCaptureStream startCapture();

    /**
     * Tears down the Audio Test Harness by disconnecting from the host-side service and tearing
     * down any associated resources managed by this class.
     *
     * <p>Additionally, any open sessions created by one of the start methods should be closed at
     * this point as well.
     */
    @Override
    public abstract void close();
}
