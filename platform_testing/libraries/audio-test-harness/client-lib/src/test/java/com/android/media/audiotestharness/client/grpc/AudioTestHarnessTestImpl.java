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
import com.android.media.audiotestharness.proto.AudioTestHarnessService;

import com.google.protobuf.ByteString;

import io.grpc.stub.StreamObserver;

/**
 * Test implementation of the {@link AudioTestHarnessGrpc.AudioTestHarnessImplBase} that contains
 * stubbed methods.
 */
public class AudioTestHarnessTestImpl extends AudioTestHarnessGrpc.AudioTestHarnessImplBase {

    public static final byte[] MESSAGE = {0x0, 0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7};

    /**
     * Test implementation of the <code>Capture</code> procedure that simply ignores the request and
     * sends back a predefined response containing the {@link #MESSAGE} bytes.
     */
    @Override
    public void capture(
            AudioTestHarnessService.CaptureRequest request,
            StreamObserver<AudioTestHarnessService.CaptureChunk> responseObserver) {
        responseObserver.onNext(
                AudioTestHarnessService.CaptureChunk.newBuilder()
                        .setData(ByteString.copyFrom(MESSAGE))
                        .build());
        responseObserver.onCompleted();
    }
}
