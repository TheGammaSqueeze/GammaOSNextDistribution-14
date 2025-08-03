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

import com.android.media.audiotestharness.proto.AudioDeviceOuterClass.AudioDevice;
import com.android.media.audiotestharness.proto.AudioFormatOuterClass.AudioFormat;
import com.android.media.audiotestharness.server.core.AudioCapturer;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sound.sampled.TargetDataLine;

/**
 * {@link AudioCapturer} that makes use of the {@link javax.sound.sampled} libraries to capture
 * audio from an attached audio device.
 */
public final class JavaAudioCapturer implements AudioCapturer {

    public static final Logger LOGGER = Logger.getLogger(JavaAudioCapturer.class.getName());

    /** Size of the byte buffer used in reading audio samples from the audio */
    public static final int BUFFER_SIZE = 2048;

    /** Set containing all of the currently registered outputs. */
    private final Set<OutputStream> mOutputs;

    private final AudioDevice mAudioDevice;

    private final AudioFormat mAudioFormat;

    /** The underlying {@link TargetDataLine} from which raw audio data is read. */
    private final TargetDataLine mTargetDataLine;

    /**
     * The {@link ExecutorService} that should be used for running the
     * TargetDataLineWatchingPublisher background tasks.
     */
    private final Executor mExecutorService;

    /**
     * The {@link TargetDataLineWatchingPublisher} that publishes data read from the {@link
     * TargetDataLine} to the various registered outputs.
     */
    private final TargetDataLineWatchingPublisher mPublisher;

    /**
     * Flag variable that determines whether the {@link #close()} method has been called and thus
     * this Capturer's data line has already been closed and cannot be reused.
     */
    private boolean mDisposed = false;

    private JavaAudioCapturer(
            AudioDevice audioDevice,
            AudioFormat audioFormat,
            TargetDataLine targetDataLine,
            Executor executor) {
        LOGGER.finest("new JavaAudioCapturer()");
        mAudioDevice = audioDevice;
        mAudioFormat = audioFormat;
        mTargetDataLine = targetDataLine;
        mExecutorService = executor;

        // Used for thread safety, in general, the set will be iterated over more than written to
        // since the TargetDataLineWatchingPublisher will continually iterate over this set
        // to write to current outputs.
        //
        // This allows for new outputs to be added after the publishers is already running.
        mOutputs = new CopyOnWriteArraySet<>();
        mPublisher = new TargetDataLineWatchingPublisher(mTargetDataLine, mOutputs);
    }

    public static JavaAudioCapturer create(
            AudioDevice audioDevice,
            AudioFormat audioFormat,
            TargetDataLine targetDataLine,
            Executor executor) {
        Preconditions.checkArgument(
                targetDataLine.isOpen(),
                "Provided TargetDataLine should already be opened when passed to the"
                        + " JavaAudioCapturer");
        return new JavaAudioCapturer(audioDevice, audioFormat, targetDataLine, executor);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method starts the underlying target data line which should trigger the data line to
     * start filling its internal buffer and thus, publishing data to attached outputs.
     *
     * <p>This assumes that the data line has already been opened and thus all resources have been
     * reserved for capture.
     */
    @Override
    public void open() {
        Preconditions.checkState(
                !mDisposed,
                "Cannot reopen a disposed AudioCapturer, a new one should be requested instead.");
        Preconditions.checkState(
                !mTargetDataLine.isRunning(), "The AudioCapturer is already open.");

        mTargetDataLine.start();
        mExecutorService.execute(mPublisher);

        LOGGER.info("AudioCapturer Opened");
    }

    @Override
    public void attachOutput(File file) {
        try {
            attachOutput(new FileOutputStream(file));
        } catch (FileNotFoundException fnfe) {
            LOGGER.warning(
                    String.format("Failed to attach file %s as output to JavaAudioCapturer", file));
        }
    }

    @Override
    public void attachOutput(OutputStream outputStream) {
        Preconditions.checkNotNull(outputStream, "Cannot attach a null output");
        mOutputs.add(outputStream);
        LOGGER.fine(String.format("Attatched new Output - %s", outputStream));
    }

    @Override
    public AudioFormat getAudioFormat() {
        return mAudioFormat;
    }

    @Override
    public AudioDevice getAudioDevice() {
        return mAudioDevice;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Stops the publishing of data to attached outputs, stops the {@link TargetDataLine}, and
     * then closes the line thus freeing the resources for use.
     */
    @Override
    public void close() {
        mPublisher.stop();

        mTargetDataLine.stop();
        mTargetDataLine.close();

        mDisposed = true;
        LOGGER.info("AudioCapturer Closed");
    }

    /**
     * {@link Runnable} that watches the {@link TargetDataLine}, reading raw audio data from it, and
     * then publishing it to any of the attached outputs.
     */
    @VisibleForTesting
    static class TargetDataLineWatchingPublisher implements Runnable {
        private static final Logger LOGGER =
                Logger.getLogger(TargetDataLineWatchingPublisher.class.getName());

        private final Set<OutputStream> mOutputs;
        private final TargetDataLine mTargetDataLine;
        private final byte[] mAudioBuffer = new byte[BUFFER_SIZE];
        private volatile boolean mRunning;

        private TargetDataLineWatchingPublisher(
                TargetDataLine targetDataLine, Set<OutputStream> outputs) {
            mTargetDataLine = targetDataLine;
            mOutputs = outputs;
            mRunning = true;
        }

        @Override
        public void run() {
            LOGGER.info("Publisher running");

            int read;
            do {

                // Read exactly BUFFER_SIZE bytes from the TargetDataLine. This should always return
                // 2048 bytes unless the targetDataLine is closed at which point it will return the
                // remaining bytes left in the DataLine's internal buffer.
                //
                // This call will block until exactly 2048 bytes are read.
                read =
                        mTargetDataLine.read(
                                mAudioBuffer, /* off= */ 0, /* len= */ mAudioBuffer.length);
                LOGGER.finest(
                        String.format("Successfully read %d bytes from mTargetDataLine", read));
                for (OutputStream output : mOutputs) {
                    try {

                        // Verify that we are still running since there is a chance that the
                        // state could have changed while waiting on the data to be read from the
                        // TargetDataLine.
                        if (mRunning) {
                            output.write(mAudioBuffer, 0, read);
                        }
                    } catch (IOException ioe) {
                        LOGGER.log(
                                Level.WARNING,
                                String.format(
                                        "Failed to write raw audio data to output. Data may have"
                                                + " been lost (Buffer Size: %d, Output: %s)",
                                        BUFFER_SIZE, output),
                                ioe);
                    }
                }
            } while (mRunning);
        }

        public void stop() {
            mRunning = false;
            LOGGER.info("Publisher stopped");
        }

        public boolean isRunning() {
            return mRunning;
        }
    }
}
