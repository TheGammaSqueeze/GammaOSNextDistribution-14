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

import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.media.audiotestharness.server.core.AudioCapturer;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Tests for the {@link AudioCaptureSession} class. */
@RunWith(JUnit4.class)
public class AudioCaptureSessionTests {

    @Rule public MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Rule public ExpectedException mExceptionRule = ExpectedException.none();

    @Mock AudioCapturer mAudioCapturer;

    @Mock CaptureChunkStreamObserverOutputStream mCaptureChunkStreamObserverOutputStream;

    /**
     * Tests for the {@link AudioCaptureSession#create(int, AudioCapturer,
     * CaptureChunkStreamObserverOutputStream)} method.
     */
    @Test(expected = NullPointerException.class)
    public void create_throwsNullPointerException_nullAudioCapturer() throws Exception {
        AudioCaptureSession.create(
                /* id= */ 1, /* audioCapturer= */ null, mCaptureChunkStreamObserverOutputStream);
    }

    @Test(expected = NullPointerException.class)
    public void create_throwsNullPointerException_nullCaptureChunkStreamObserverOutputStream()
            throws Exception {
        AudioCaptureSession.create(
                /* id= */ 1, mAudioCapturer, /* captureChunkStreamObserverOutputStream= */ null);
    }

    @Test
    public void create_attachesOutputStreamToAudioCapturer() throws Exception {
        AudioCaptureSession.create(1, mAudioCapturer, mCaptureChunkStreamObserverOutputStream);
        verify(mAudioCapturer).attachOutput(mCaptureChunkStreamObserverOutputStream);
    }

    @Test
    public void create_returnsNonNullSession() throws Exception {
        assertNotNull(
                AudioCaptureSession.create(
                        1, mAudioCapturer, mCaptureChunkStreamObserverOutputStream));
    }

    /** Tests for the {@link AudioCaptureSession#start()} methods. */
    @Test
    public void start_opensAudioCapturer() throws Exception {
        AudioCaptureSession audioCaptureSession =
                AudioCaptureSession.create(
                        1, mAudioCapturer, mCaptureChunkStreamObserverOutputStream);

        audioCaptureSession.start();

        verify(mAudioCapturer).open();
    }

    @Test
    public void start_throwsIOException_whenCapturerFails() throws Exception {
        AudioCaptureSession audioCaptureSession =
                AudioCaptureSession.create(
                        1, mAudioCapturer, mCaptureChunkStreamObserverOutputStream);
        doThrow(IOException.class).when(mAudioCapturer).open();
        mExceptionRule.expectMessage("Failed to open AudioCapturer for Audio Capture Session");
        mExceptionRule.expectCause(notNullValue(Throwable.class));

        audioCaptureSession.start();
    }

    /** Tests for the {@link AudioCaptureSession#stop()} method. */
    @Test
    public void stop_closesAudioCapturer() throws Exception {
        AudioCaptureSession audioCaptureSession =
                AudioCaptureSession.create(
                        1, mAudioCapturer, mCaptureChunkStreamObserverOutputStream);

        audioCaptureSession.stop();

        verify(mAudioCapturer).close();
    }

    @Test
    public void stop_closesCaptureChunkOutputStream() throws Exception {
        AudioCaptureSession audioCaptureSession =
                AudioCaptureSession.create(
                        1, mAudioCapturer, mCaptureChunkStreamObserverOutputStream);

        audioCaptureSession.stop();

        verify(mCaptureChunkStreamObserverOutputStream).close();
    }

    @Test
    public void stop_suppressesErrorsWhenAudioCapturerIsClosed() throws Exception {
        AudioCaptureSession audioCaptureSession =
                AudioCaptureSession.create(
                        1, mAudioCapturer, mCaptureChunkStreamObserverOutputStream);
        doThrow(IOException.class).when(mAudioCapturer).close();

        audioCaptureSession.stop();

        // No need to assert since test should pass as long as no exception is thrown.
    }

    /** Tests for the {@link AudioCaptureSession#awaitStop(long, TimeUnit)} method. */
    @Test
    public void awaitStop_waitsOnCaptureChunkStreamObserverToClose() throws Exception {
        AudioCaptureSession audioCaptureSession =
                AudioCaptureSession.create(
                        1, mAudioCapturer, mCaptureChunkStreamObserverOutputStream);

        audioCaptureSession.awaitStop(1234, TimeUnit.SECONDS);

        verify(mCaptureChunkStreamObserverOutputStream).awaitClose(1234, TimeUnit.SECONDS);
    }

    @Test
    public void awaitStop_closesAudioCapturerWhenTimeoutReached() throws Exception {
        AudioCaptureSession audioCaptureSession =
                AudioCaptureSession.create(
                        1, mAudioCapturer, mCaptureChunkStreamObserverOutputStream);
        when(mCaptureChunkStreamObserverOutputStream.awaitClose(anyLong(), any()))
                .thenReturn(false);

        audioCaptureSession.awaitStop(1234, TimeUnit.SECONDS);

        verify(mAudioCapturer).close();
    }

    @Test
    public void awaitStop_closesCaptureChunkOutputStreamWhenTimeoutReached() throws Exception {
        AudioCaptureSession audioCaptureSession =
                AudioCaptureSession.create(
                        1, mAudioCapturer, mCaptureChunkStreamObserverOutputStream);
        when(mCaptureChunkStreamObserverOutputStream.awaitClose(anyLong(), any()))
                .thenReturn(false);

        audioCaptureSession.awaitStop(1234, TimeUnit.SECONDS);

        verify(mCaptureChunkStreamObserverOutputStream).close();
    }

    @Test
    public void awaitStop_throwsIOException_whenInterrupted() throws Exception {
        AudioCaptureSession audioCaptureSession =
                AudioCaptureSession.create(
                        1, mAudioCapturer, mCaptureChunkStreamObserverOutputStream);
        when(mCaptureChunkStreamObserverOutputStream.awaitClose(anyLong(), any()))
                .thenThrow(InterruptedException.class);
        mExceptionRule.expectMessage(
                "Interrupted while waiting for capture to finish for Audio Capture Session");
        mExceptionRule.expectCause(notNullValue(InterruptedException.class));

        audioCaptureSession.awaitStop(1234, TimeUnit.SECONDS);
    }
}
