/*
 * Copyright (C) 2024 The Android Open Source Project
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
package android.virtualdevice.cts.audio;

import android.companion.virtual.audio.AudioCapture;
import android.media.AudioRecord;
import android.util.Log;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Utility class to observe and wait for changes in the AudioRecord audio signal.
 */
class SignalObserver implements AutoCloseable {
    private static final String TAG = SignalObserver.class.getSimpleName();

    private static final Duration READ_BUFFER_DURATION = Duration.ofMillis(48);
    private static final double POWER_THRESHOLD_FOR_SIGNAL_PRESENCE = 0.4f;

    private AudioRecorderThread mAudioRecorderThread;

    private final AtomicBoolean mRunning = new AtomicBoolean(true);

    public interface SignalChangeListener {
        void onSignalChange(Set<Integer> frequencies);
    }

    /**
     * Construct signal observer for the specified audio record and set of frequencies.
     * Immediately after construction, the record will be continuously read from
     * until {@link SignalObserver#close()} is called.
     *
     * @param audioCapture         - Audio capture to record from and observe the changes.
     * @param observedFrequencies - Set of frequencies to test the signal for.
     */
    SignalObserver(AudioCapture audioCapture, Set<Integer> observedFrequencies) {
        this(new AudioCaptureSource(audioCapture), observedFrequencies);
    }

    /**
     * Construct signal observer for the specified audio record and set of frequencies.
     * Immediately after construction, the record will be continuously read from
     * until {@link SignalObserver#close()} is called.
     *
     * @param audioRecord         - Audio record to record from and observe the changes.
     * @param observedFrequencies - Set of frequencies to test the signal for.
     */
    SignalObserver(AudioRecord audioRecord, Set<Integer> observedFrequencies) {
        this(new AudioRecordSource(audioRecord), observedFrequencies);
    }

    private SignalObserver(AudioSource audioSource, Set<Integer> observedFrequencies) {
        mAudioRecorderThread = new AudioRecorderThread(audioSource, observedFrequencies);
        mAudioRecorderThread.start();
    }

    void registerSignalChangeListener(SignalChangeListener signalChangeListener) {
        mAudioRecorderThread.addSignalChangeListener(signalChangeListener);
    }

    void unregisterSignalChangeListener(SignalChangeListener signalChangeListener) {
        mAudioRecorderThread.removeSignalChangeListener(signalChangeListener);
    }

    @Override
    public void close() {
        mRunning.set(false);
    }

    private class AudioRecorderThread extends Thread {

        private final AudioSource mAudioSource;
        private final int mReadBufferSampleCount;
        private final Set<Integer> mObservedFrequencies;

        private final Object mLock = new Object();

        private Set<Integer> mLastObservedFrequencies = Set.of();

        private final ArrayList<SignalChangeListener> mSignalChangeListeners = new ArrayList<>();


        AudioRecorderThread(AudioSource audioSource, Set<Integer> observedFrequencies) {
            mAudioSource = audioSource;
            mObservedFrequencies = observedFrequencies;
            mReadBufferSampleCount =
                    (int) ((READ_BUFFER_DURATION.toMillis() * mAudioSource.getSampleRate()
                            * mAudioSource.getChannelCount()) / 1000);
        }

        void addSignalChangeListener(SignalChangeListener listener) {
            synchronized (mLock) {
                mSignalChangeListeners.add(listener);

                listener.onSignalChange(mLastObservedFrequencies);
            }
        }

        void removeSignalChangeListener(SignalChangeListener listener) {
            synchronized (mLock) {
                mSignalChangeListeners.remove(listener);
            }
        }

        @Override
        public void run() {
            super.run();
            mAudioSource.startRecording();

            short[] buffer = new short[mReadBufferSampleCount];

            while (mRunning.get()) {
                int ret = mAudioSource.read(buffer, 0, buffer.length, AudioRecord.READ_BLOCKING);
                if (ret < 0) {
                    Log.e(TAG, "Error calling read on audio record, read call returned " + ret);
                    break;
                }

                Set<Integer> currentFrequencies = mObservedFrequencies.stream().filter(
                        frequency -> isFrequencyPresent(frequency, buffer)).collect(
                        Collectors.toSet());
                Log.d(TAG, "Read " + ret + "bytes, freq " + currentFrequencies);

                List<SignalObserver.SignalChangeListener> listeners;
                synchronized (mLock) {
                    if (currentFrequencies.equals(mLastObservedFrequencies)) {
                        continue;
                    }

                    listeners = List.copyOf(mSignalChangeListeners);
                    mLastObservedFrequencies = Set.copyOf(currentFrequencies);
                }

                listeners.forEach(
                        listener ->
                                listener.onSignalChange(
                                        Collections.unmodifiableSet(currentFrequencies)));
            }
        }

        private boolean isFrequencyPresent(int frequency, short[] buffer) {
            final int channelCount = mAudioSource.getChannelCount();
            double power = 0.0;
            for (int j = 0; j < channelCount; j++) {
                // Get the power in that channel
                power += goertzel(frequency, mAudioSource.getSampleRate(),
                        buffer, j /*offset*/, buffer.length, channelCount)
                        / channelCount;
            }

            Log.d(TAG, "Freq: " + frequency + " : " + power);
            return power > POWER_THRESHOLD_FOR_SIGNAL_PRESENCE;
        }
    }

    private interface AudioSource {

        void startRecording();

        int getSampleRate();

        int getChannelCount();

        int read(short[] buffer, int offsetInShorts, int length, int readMode);

    }

    private static class AudioRecordSource implements AudioSource {
        private final AudioRecord mAudioRecord;

        AudioRecordSource(AudioRecord audioRecord) {
            mAudioRecord = audioRecord;
        }


        @Override
        public void startRecording() {
            mAudioRecord.startRecording();
        }

        @Override
        public int getSampleRate() {
            return mAudioRecord.getSampleRate();
        }

        @Override
        public int getChannelCount() {
            return mAudioRecord.getChannelCount();
        }

        @Override
        public int read(short[] buffer, int offsetInShorts, int length, int readMode) {
            return mAudioRecord.read(buffer, offsetInShorts, length, readMode);
        }
    }

    private static class AudioCaptureSource implements AudioSource {
        private final AudioCapture mAudioCapture;

        AudioCaptureSource(AudioCapture audioCapture) {
            mAudioCapture = audioCapture;
        }


        @Override
        public void startRecording() {
            mAudioCapture.startRecording();
        }

        @Override
        public int getSampleRate() {
            return mAudioCapture.getFormat().getSampleRate();
        }

        @Override
        public int getChannelCount() {
            return mAudioCapture.getFormat().getChannelCount();
        }

        @Override
        public int read(short[] buffer, int offsetInShorts, int length, int readMode) {
            return mAudioCapture.read(buffer, offsetInShorts, length, readMode);
        }
    }

    /**
     * Computes the relative power of a given frequency within a frame of the signal.
     * See: http://en.wikipedia.org/wiki/Goertzel_algorithm
     */
    static double goertzel(int signalFreq, int samplingFreq,
            short[] samples, int offset, int length, int stride) {
        final int n = length / stride;
        final double coeff = Math.cos(signalFreq * 2 * Math.PI / samplingFreq) * 2;
        double s1 = 0;
        double s2 = 0;
        double rms = 0;
        for (int i = 0; i < n; i++) {
            double hamming = 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (n - 1));
            double x = samples[i * stride + offset] * hamming; // apply hamming window
            double s = x + coeff * s1 - s2;
            s2 = s1;
            s1 = s;
            rms += x * x;
        }
        rms = Math.sqrt(rms / n);
        double magnitude = s2 * s2 + s1 * s1 - coeff * s1 * s2;
        return Math.sqrt(magnitude) / n / rms;
    }
}
