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

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.android.media.audiotestharness.server.core.AudioCapturer;

import io.grpc.stub.ServerCallStreamObserver;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Tests for the {@link AudioCaptureSessionFactoryImpl} class. */
@RunWith(JUnit4.class)
public class AudioCaptureSessionFactoryImplTests {

    @Rule public MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock StreamObserverOutputStreamFactory mStreamObserverOutputStreamFactory;

    @Mock ServerCallStreamObserver mStreamObserver;

    @Mock AudioCapturer mAudioCapturer;

    CaptureChunkStreamObserverOutputStream mCaptureChunkStreamObserverOutputStream;

    AudioCaptureSessionFactoryImpl mAudioCaptureSessionFactory;

    @Before
    public void setUp() throws Exception {
        mCaptureChunkStreamObserverOutputStream =
                CaptureChunkStreamObserverOutputStream.create(mStreamObserver);
        when(mStreamObserverOutputStreamFactory.createNewCaptureChunkStreamObserverOutputStream(
                        any()))
                .thenReturn(mCaptureChunkStreamObserverOutputStream);
        this.mAudioCaptureSessionFactory =
                new AudioCaptureSessionFactoryImpl(mStreamObserverOutputStreamFactory);
    }

    @Test
    public void createCaptureSession_returnsNonNullCaptureSession() {
        assertNotNull(
                mAudioCaptureSessionFactory.createCaptureSession(mStreamObserver, mAudioCapturer));
    }

    @Test
    public void createCaptureSession_usesUniqueIdentifiers() throws Exception {
        AudioCaptureSession audioCaptureSessionOne =
                mAudioCaptureSessionFactory.createCaptureSession(mStreamObserver, mAudioCapturer);
        AudioCaptureSession audioCaptureSessionTwo =
                mAudioCaptureSessionFactory.createCaptureSession(mStreamObserver, mAudioCapturer);
        AudioCaptureSession audioCaptureSessionThree =
                mAudioCaptureSessionFactory.createCaptureSession(mStreamObserver, mAudioCapturer);

        assertNotEquals(
                audioCaptureSessionOne.getSessionId(), audioCaptureSessionTwo.getSessionId());
        assertNotEquals(
                audioCaptureSessionOne.getSessionId(), audioCaptureSessionThree.getSessionId());
        assertNotEquals(
                audioCaptureSessionTwo.getSessionId(), audioCaptureSessionThree.getSessionId());
    }
}
