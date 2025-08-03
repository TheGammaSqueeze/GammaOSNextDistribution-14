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

package com.android.media.audiotestharness.server.javasound;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.media.audiotestharness.common.Defaults;
import com.android.media.audiotestharness.proto.AudioDeviceOuterClass;
import com.android.media.audiotestharness.proto.AudioFormatOuterClass;
import com.android.media.audiotestharness.server.core.AudioCapturer;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.concurrent.Executor;

import javax.sound.sampled.TargetDataLine;

@RunWith(JUnit4.class)
public class JavaAudioCapturerTests {

    @Rule public MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Rule public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    /**
     * Timeout to catch breakages where the TargetDataLineWatchingPublisher infinite loops. The
     * slowest test takes about 50ms to run so a 20x buffer for timeout should be acceptable.
     */
    @Rule public Timeout mTimeout = Timeout.millis(1000);

    @Mock Executor mExecutor;

    @Mock TargetDataLine mTargetDataLine;

    private static final AudioDeviceOuterClass.AudioDevice TEST_DEVICE = Defaults.AUDIO_DEVICE;
    private static final AudioFormatOuterClass.AudioFormat TEST_FORMAT = Defaults.AUDIO_FORMAT;

    @Test(expected = IllegalArgumentException.class)
    public void create_throwsIllegalArgumentException_closedDataLine() throws Exception {
        when(mTargetDataLine.isOpen()).thenReturn(false);
        JavaAudioCapturer.create(TEST_DEVICE, TEST_FORMAT, mTargetDataLine, mExecutor);
    }

    @Test
    public void open_startsDataLine() throws Exception {
        when(mTargetDataLine.isOpen()).thenReturn(true);
        when(mTargetDataLine.isRunning()).thenReturn(false);

        JavaAudioCapturer.create(TEST_DEVICE, TEST_FORMAT, mTargetDataLine, mExecutor).open();

        verify(mTargetDataLine).start();
    }

    @Test
    public void open_schedulesPublisherTask() throws Exception {
        when(mTargetDataLine.isOpen()).thenReturn(true);
        when(mTargetDataLine.isRunning()).thenReturn(false);

        JavaAudioCapturer.create(TEST_DEVICE, TEST_FORMAT, mTargetDataLine, mExecutor).open();

        verify(mExecutor).execute(any(JavaAudioCapturer.TargetDataLineWatchingPublisher.class));
    }

    @Test(expected = IllegalStateException.class)
    public void open_throwsIllegalStateException_alreadyRunningLine() throws Exception {
        when(mTargetDataLine.isOpen()).thenReturn(true);
        when(mTargetDataLine.isRunning()).thenReturn(true);

        JavaAudioCapturer.create(TEST_DEVICE, TEST_FORMAT, mTargetDataLine, mExecutor).open();
    }

    @Test(expected = IllegalStateException.class)
    public void open_throwsIllegalStateException_afterCloseCalled() throws Exception {
        when(mTargetDataLine.isOpen()).thenReturn(true);
        when(mTargetDataLine.isRunning()).thenReturn(false);
        JavaAudioCapturer capturer =
                JavaAudioCapturer.create(TEST_DEVICE, TEST_FORMAT, mTargetDataLine, mExecutor);

        capturer.open();
        capturer.close();

        capturer.open();
    }

    @Test
    public void attachOutput_attachesOutputSuccessfully_outputStream() throws Exception {
        byte[] expectedBytes = {0x1, 0x2, 0x3, 0x4};

        when(mTargetDataLine.isOpen()).thenReturn(true);
        when(mTargetDataLine.isRunning()).thenReturn(false);

        // Simulate a "read" from the data line when running through.
        when(mTargetDataLine.read(any(), anyInt(), anyInt()))
                .then(
                        invocation -> {
                            byte[] buffer = (byte[]) invocation.getArgument(0);

                            buffer[0] = expectedBytes[0];
                            buffer[1] = expectedBytes[1];
                            buffer[2] = expectedBytes[2];
                            buffer[3] = expectedBytes[3];

                            return 4;
                        });
        ArgumentCaptor<Runnable> runnableArgCaptor = ArgumentCaptor.forClass(Runnable.class);

        // Attach a byte array output stream to the runner.
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        AudioCapturer capturer =
                JavaAudioCapturer.create(TEST_DEVICE, TEST_FORMAT, mTargetDataLine, mExecutor);
        capturer.attachOutput(byteArrayOutputStream);
        capturer.open();

        verify(mExecutor).execute(runnableArgCaptor.capture());
        JavaAudioCapturer.TargetDataLineWatchingPublisher publisher =
                (JavaAudioCapturer.TargetDataLineWatchingPublisher) runnableArgCaptor.getValue();

        // Ensure the publisher only loops once by adding a stream that stops it.
        capturer.attachOutput(new RunnableOutputStream(publisher::stop));
        publisher.run();

        // Verify output is as expected to the array.
        assertArrayEquals(expectedBytes, byteArrayOutputStream.toByteArray());
    }

    @Test
    public void attachOutput_attachesOutputSuccessfully_file() throws Exception {
        byte[] expectedBytes = {0x1, 0x2, 0x3, 0x4};

        when(mTargetDataLine.isOpen()).thenReturn(true);
        when(mTargetDataLine.isRunning()).thenReturn(false);

        // Simulate a "read" from the data line when running through.
        when(mTargetDataLine.read(any(), anyInt(), anyInt()))
                .then(
                        invocation -> {
                            byte[] buffer = (byte[]) invocation.getArgument(0);

                            buffer[0] = expectedBytes[0];
                            buffer[1] = expectedBytes[1];
                            buffer[2] = expectedBytes[2];
                            buffer[3] = expectedBytes[3];

                            return 4;
                        });
        ArgumentCaptor<Runnable> runnableArgCaptor = ArgumentCaptor.forClass(Runnable.class);

        File testFile = mTemporaryFolder.newFile();
        AudioCapturer capturer =
                JavaAudioCapturer.create(TEST_DEVICE, TEST_FORMAT, mTargetDataLine, mExecutor);
        capturer.attachOutput(testFile);
        capturer.open();

        verify(mExecutor).execute(runnableArgCaptor.capture());
        JavaAudioCapturer.TargetDataLineWatchingPublisher publisher =
                (JavaAudioCapturer.TargetDataLineWatchingPublisher) runnableArgCaptor.getValue();

        // Ensure the publisher only loops once by adding a stream that stops it.
        capturer.attachOutput(new RunnableOutputStream(publisher::stop));
        publisher.run();

        // Verify output is as expected to the array.
        assertArrayEquals(expectedBytes, Files.readAllBytes(testFile.toPath()));
    }

    @Test
    public void close_stopsPublisherAsExpected() throws Exception {
        when(mTargetDataLine.isOpen()).thenReturn(true);
        when(mTargetDataLine.isRunning()).thenReturn(false);
        ArgumentCaptor<Runnable> runnableArgCaptor = ArgumentCaptor.forClass(Runnable.class);

        JavaAudioCapturer capturer =
                JavaAudioCapturer.create(TEST_DEVICE, TEST_FORMAT, mTargetDataLine, mExecutor);
        capturer.open();

        verify(mExecutor).execute(runnableArgCaptor.capture());
        JavaAudioCapturer.TargetDataLineWatchingPublisher publisher =
                (JavaAudioCapturer.TargetDataLineWatchingPublisher) runnableArgCaptor.getValue();

        assertTrue(publisher.isRunning());
        capturer.close();
        assertFalse(publisher.isRunning());
    }

    @Test
    public void close_closesDataLineAsExpected() throws Exception {
        when(mTargetDataLine.isOpen()).thenReturn(true);
        when(mTargetDataLine.isRunning()).thenReturn(false);
        JavaAudioCapturer capturer =
                JavaAudioCapturer.create(TEST_DEVICE, TEST_FORMAT, mTargetDataLine, mExecutor);
        capturer.open();

        capturer.close();

        verify(mTargetDataLine).stop();
        verify(mTargetDataLine).close();
    }

    /** Custom {@link OutputStream} that triggers a contained Runnable when written to. */
    private static class RunnableOutputStream extends OutputStream {
        private final Runnable mRunnable;

        private RunnableOutputStream(Runnable runnable) {
            mRunnable = runnable;
        }

        @Override
        public void write(int b) throws IOException {
            mRunnable.run();
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            mRunnable.run();
        }

        @Override
        public void write(byte[] b) {
            mRunnable.run();
        }
    }
}
