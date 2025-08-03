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

import static org.junit.Assert.assertNotNull;

import com.android.media.audiotestharness.proto.AudioTestHarnessService;

import io.grpc.stub.ServerCallStreamObserver;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class StreamObserverOutputStreamFactoryTests {

    @Test
    public void createNewCaptureChunkStreamObserverOutputStream_returnsNonNullInstance()
            throws Exception {
        StreamObserverOutputStreamFactory factory = new StreamObserverOutputStreamFactory();
        assertNotNull(
                factory.createNewCaptureChunkStreamObserverOutputStream(
                        new ServerCallStreamObserver<AudioTestHarnessService.CaptureChunk>() {
                            @Override
                            public boolean isReady() {
                                return false;
                            }

                            @Override
                            public void setOnReadyHandler(Runnable onReadyHandler) {}

                            @Override
                            public void disableAutoInboundFlowControl() {}

                            @Override
                            public void request(int count) {}

                            @Override
                            public void setMessageCompression(boolean enable) {}

                            @Override
                            public boolean isCancelled() {
                                return false;
                            }

                            @Override
                            public void setOnCancelHandler(Runnable onCancelHandler) {}

                            @Override
                            public void setCompression(String compression) {}

                            @Override
                            public void onNext(AudioTestHarnessService.CaptureChunk value) {}

                            @Override
                            public void onError(Throwable t) {}

                            @Override
                            public void onCompleted() {}
                        }));
    }
}
