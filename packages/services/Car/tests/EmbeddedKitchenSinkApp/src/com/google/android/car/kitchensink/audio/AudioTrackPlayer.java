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

package com.google.android.car.kitchensink.audio;

import static android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY;
import static android.media.AudioFormat.CHANNEL_OUT_MONO;
import static android.media.AudioFormat.ENCODING_PCM_16BIT;
import static android.media.AudioTrack.MODE_STREAM;

import static com.google.android.car.kitchensink.audio.AudioTestFragment.getAudioLogTag;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.Preconditions;

final class AudioTrackPlayer {

    private static final String TAG = getAudioLogTag(AudioTrackPlayer.class);
    private static final boolean DEBUG = true;
    private static final int SAMPLE_RATE = 48_000;
    private static final int MS_TO_SECONDS = 1_000;
    private static final double DURATION_MS = 100;
    private static final int WAIT_TIME_MS = 10;
    private static final int AUDIBLE_RANGE_BOTTOM = 20;
    private static final int AUDIBLE_RANGE_TOP = 20_000;

    private static final AudioAttributes AUDIO_ATTRIBUTES = new AudioAttributes.Builder()
            .setUsage(USAGE_ASSISTANCE_ACCESSIBILITY)
            .build();
    private static final AudioFormat AUDIO_FORMAT = new AudioFormat.Builder()
            .setChannelMask(CHANNEL_OUT_MONO)
            .setEncoding(ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .build();

    private final int mToneFreq;
    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private Thread mThread;
    @GuardedBy("mLock")
    private boolean mRunning = false;

    AudioTrackPlayer(int toneFreq) {
        Preconditions.checkArgumentInRange(toneFreq, AUDIBLE_RANGE_BOTTOM, AUDIBLE_RANGE_TOP,
                "toneFreq");
        mToneFreq = toneFreq;
    }

    void start() {
        if (DEBUG) {
            Log.d(TAG, "Starting!");
        }
        synchronized (mLock) {
            if (!mRunning) {
                mRunning = true;
                startTrackLocked();
            }
        }
    }

    void stop() {
        if (DEBUG) {
            Log.d(TAG, "Stopping!");
        }
        synchronized (mLock) {
            mRunning = false;
            if (mThread != null) {
                try {
                    mThread.join(WAIT_TIME_MS);
                    mThread = null;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.w(TAG, "Failed to stop ", e);
                }
            }
        }
    }

    private void startTrackLocked() {
        mThread = new Thread(() -> {
            AudioTrack track = new AudioTrack.Builder()
                    .setAudioAttributes(AUDIO_ATTRIBUTES)
                    .setAudioFormat(AUDIO_FORMAT)
                    .setTransferMode(MODE_STREAM)
                    .build();
            track.flush();
            track.play();
            short[] buffer = getBuffer();
            while (true) {
                synchronized (mLock) {
                    if (!mRunning) break;
                }
                track.write(buffer, 0, buffer.length);
            }
        }, TAG + ":PlaybackThread");
        mThread.start();
    }

    private short[] getBuffer() {
        double periodMs = MS_TO_SECONDS / ((double) mToneFreq);
        double samplesInCycle =   ((double) SAMPLE_RATE) / ((double) mToneFreq);
        int samplesInDuration = (int) ((DURATION_MS / periodMs) * samplesInCycle);
        short[] buffer = new short[samplesInDuration];
        double kw = 2 * Math.PI  / (SAMPLE_RATE / mToneFreq);
        for (int i = 0; i < samplesInDuration; ++i) {
            // Higher amplitude increases volume
            buffer[i] = (short) (Math.sin(kw * i) * Short.MAX_VALUE);
        }
        return buffer;
    }

    public boolean isPlaying() {
        synchronized (mLock) {
            return mRunning;
        }
    }
}
