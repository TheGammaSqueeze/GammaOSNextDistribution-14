/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.media.audio.cts;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioRecordingConfiguration;
import android.media.audiopolicy.AudioProductStrategy;
import android.os.PowerManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

class AudioTestUtil {
    // Default matches the invalid (empty) attributes from native.
    // The difference is the input source default which is not aligned between native and java
    public static final AudioAttributes DEFAULT_ATTRIBUTES =
            AudioProductStrategy.getDefaultAttributes();
    public static final AudioAttributes INVALID_ATTRIBUTES = new AudioAttributes.Builder().build();

    // Basic Device Attributes
    public static boolean hasAudioOutput(Context context) {
        return context.getPackageManager().hasSystemFeature(
            PackageManager.FEATURE_AUDIO_OUTPUT);
    }

    public static boolean hasAudioInput(Context context) {
        return context.getPackageManager().hasSystemFeature(
            PackageManager.FEATURE_MICROPHONE);
    }

    public static int resetVolumeIndex(int indexMin, int indexMax) {
        return (indexMax + indexMin) / 2;
    }

    public static int incrementVolumeIndex(int index, int indexMin, int indexMax) {
        return (index + 1 > indexMax) ? resetVolumeIndex(indexMin, indexMax) : ++index;
    }

    //-----------------------------------------------------------------------------------

    /**
     * A test helper class to help compare an expected int against the result of an IntSupplier
     * lambda. It supports specifying a max wait time, broken down into Thread.sleep() of the
     * given period. The expected value is compared against the result of the lambda every period.
     * It will assert if the expected value is never returned after the maximum specified time.
     * Example of how to use:
     * <pre>
     *     final SleepAssertIntEquals test = new SleepAssertIntEquals(
     *             5000, // max sleep duration is 5s
     *             100,  // test condition will be checked every 100ms
     *             getContext()); // strictly for the wakelock hold
     *    // do the operation under test
     *    mAudioManager.setStreamVolume(STREAM_MUSIC,
     *             mAudioManager.getMinStreamVolume(STREAM_MUSIC), 0);
     *    // sleep and check until the volume has changed to what the test expects,
     *    // it will throw an Exception if that doesn't happen within 5s
     *    test.assertEqualsSleep( mAudioManager.getMinStreamVolume(STREAM_MUSIC), // expected value
     *                            () -> mAudioManager.getStreamVolume(STREAM_MUSIC),
     *                            "Observed volume not at min for MUSIC");
     * </pre>
     */
    public static class SleepAssertIntEquals {
        final long mMaxWaitMs;
        final long mPeriodMs;
        private PowerManager.WakeLock mWakeLock;

        /**
         * Constructor for the test utility
         * @param maxWaitMs the maximum time this test will ever wait
         * @param periodMs the period to sleep for in between test attempts,
         *                 must be less than maxWaitMs
         * @param context not retained, just for obtaining a partial wakelock from PowerManager
         */
        SleepAssertIntEquals(int maxWaitMs, int periodMs, Context context) {
            if (periodMs >= maxWaitMs) {
                throw new IllegalArgumentException("Period must be lower than max wait time");
            }
            mMaxWaitMs = maxWaitMs;
            mPeriodMs = periodMs;
            PowerManager pm = context.getSystemService(PowerManager.class);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SleepAssertIntEquals");
        }

        /**
         * Compares the expected against the result of the lambda until they're equals, or unless
         * the max wait time has elapsed, whichever happens first. On a timeout (int result wasn't
         * as expected), the method asserts.
         * @param expected the expected int value in the test
         * @param result the function returning an int under test
         * @param message the message to display when asserting
         * @throws InterruptedException
         */
        public void assertEqualsSleep(int expected, IntSupplier result, String message)
                throws InterruptedException {
            final long endMs = System.currentTimeMillis() + mMaxWaitMs;
            try {
                mWakeLock.acquire();
                int actual = Integer.MIN_VALUE;
                while (System.currentTimeMillis() < endMs) {
                    actual = result.getAsInt();
                    if (actual == expected) {
                        // test successful, stop
                        return;
                    } else {
                        // wait some more before expecting the test to be successful
                        Thread.sleep(mPeriodMs);
                    }
                }
                assertEquals(message, expected, actual);
            } finally {
                mWakeLock.release();
            }
        }
    }

    /**
     * A helper class to use when wanting to block in a test on audio recording starting/stopping
     */
    static class AudioRecordingCallbackUtil extends AudioManager.AudioRecordingCallback {
        boolean mCalled;
        private final Object mConfigLock = new Object();
        List<AudioRecordingConfiguration> mConfigs;
        private final int mTestSource;
        private final int mTestSession;
        private CountDownLatch mCountDownLatch;

        void reset() {
            mCountDownLatch = new CountDownLatch(1);
            mCalled = false;
            synchronized (mConfigLock) {
                mConfigs = new ArrayList<AudioRecordingConfiguration>();
            }
        }

        AudioRecordingCallbackUtil(int session, int source) {
            mTestSource = source;
            mTestSession = session;
            reset();
        }

        @Override
        public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs) {
            mCalled = true;
            synchronized (mConfigLock) {
                mConfigs = configs;
            }
            mCountDownLatch.countDown();
        }

        void await(long timeoutMs) {
            try {
                mCountDownLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
            }
        }

        boolean hasRecording(int session, int source) {
            synchronized (mConfigLock) {
                for (AudioRecordingConfiguration config : mConfigs) {
                    if ((config.getClientAudioSessionId() == session)
                            && (config.getAudioSource() == source)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
