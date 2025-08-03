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

import com.android.media.audiotestharness.proto.AudioTestHarnessGrpc;

import com.google.common.base.Preconditions;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;

/** Factory for the {@link GrpcAudioCaptureStream}. */
public class GrpcAudioCaptureStreamFactory {

    private final ScheduledExecutorService mScheduledExecutorService;

    private GrpcAudioCaptureStreamFactory(ScheduledExecutorService scheduledExecutorService) {
        mScheduledExecutorService = scheduledExecutorService;
    }

    public static GrpcAudioCaptureStreamFactory create(
            ScheduledExecutorService scheduledExecutorService) {
        Preconditions.checkNotNull(scheduledExecutorService);
        return new GrpcAudioCaptureStreamFactory(scheduledExecutorService);
    }

    GrpcAudioCaptureStream newStream(AudioTestHarnessGrpc.AudioTestHarnessStub audioTestHarnessStub)
            throws IOException {
        Preconditions.checkNotNull(audioTestHarnessStub, "audioTestHarnessStub cannot be null");
        return GrpcAudioCaptureStream.create(audioTestHarnessStub, mScheduledExecutorService);
    }
}
