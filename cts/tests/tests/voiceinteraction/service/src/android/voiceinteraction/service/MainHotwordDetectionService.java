/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.voiceinteraction.service;

import static android.media.AudioFormat.CHANNEL_IN_FRONT;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.SharedMemory;
import android.os.SystemClock;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.HotwordAudioStream;
import android.service.voice.HotwordDetectedResult;
import android.service.voice.HotwordDetectionService;
import android.service.voice.HotwordRejectedResult;
import android.service.voice.SandboxedDetectionInitializer;
import android.system.ErrnoException;
import android.text.TextUtils;
import android.util.Log;
import android.voiceinteraction.common.Utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.IntConsumer;

import javax.annotation.concurrent.GuardedBy;

public class MainHotwordDetectionService extends HotwordDetectionService {
    static final String TAG = "MainHotwordDetectionService";

    public static byte[] FAKE_HOTWORD_AUDIO_DATA =
            new byte[]{'h', 'o', 't', 'w', 'o', 'r', 'd', '!'};
    public static String KEY_FAKE_DATA = "fakeData";
    public static String VALUE_FAKE_DATA = "fakeData";
    public static final int DEFAULT_PHRASE_ID = 5;
    public static final HotwordDetectedResult DETECTED_RESULT =
            new HotwordDetectedResult.Builder()
                    .setAudioChannel(CHANNEL_IN_FRONT)
                    .setConfidenceLevel(HotwordDetectedResult.CONFIDENCE_LEVEL_HIGH)
                    .setHotwordDetectionPersonalized(true)
                    .setHotwordDurationMillis(1000)
                    .setHotwordOffsetMillis(500)
                    .setHotwordPhraseId(DEFAULT_PHRASE_ID)
                    .setPersonalizedScore(10)
                    .setScore(15)
                    .setBackgroundAudioPower(50)
                    .build();
    public static final HotwordDetectedResult DETECTED_RESULT_AFTER_STOP_DETECTION =
            new HotwordDetectedResult.Builder()
                    .setHotwordPhraseId(DEFAULT_PHRASE_ID)
                    .setScore(57)
                    .build();
    public static final HotwordDetectedResult DETECTED_RESULT_FOR_MIC_FAILURE =
            new HotwordDetectedResult.Builder()
                    .setHotwordPhraseId(DEFAULT_PHRASE_ID)
                    .setScore(58)
                    .build();
    public static final HotwordRejectedResult REJECTED_RESULT =
            new HotwordRejectedResult.Builder()
                    .setConfidenceLevel(HotwordRejectedResult.CONFIDENCE_LEVEL_MEDIUM)
                    .build();
    private static final int PIPE_READ_END_INDEX = 0;
    private static final int PIPE_WRITE_END_INDEX = 1;

    @NonNull
    private final Object mLock = new Object();
    private Handler mHandler;
    @GuardedBy("mLock")
    private boolean mStopDetectionCalled;
    @GuardedBy("mLock")
    private int mDetectionDelayMs = 0;
    private long mServiceCreatedTimeMillis = -1;

    @GuardedBy("mLock")
    @Nullable
    private Runnable mDetectionJob;

    private boolean mIsTestUnexpectedCallback;

    private boolean mIsTestAudioEgress;

    /**
     * It only works when {@link #mIsTestAudioEgress} is true
     */
    private boolean mUseIllegalAudioEgressCopyBufferSize;

    private boolean mIsNoNeedActionDuringDetection;

    private boolean mCheckAudioDataIsNotZero;

    private boolean mShouldCloseExternalAudioStreamAfterRead = true;

    // Only used in certain scenarios where we don't want to close it immediately after reading from
    // it.
    private InputStream mExternalAudioStreamReceived;
    // Only used in certain scenarios where we want to verify whether the stream is writable.
    private OutputStream mDetectedResultOutputStream;

    @Override
    public void onCreate() {
        super.onCreate();
        mHandler = Handler.createAsync(Looper.getMainLooper());
        mServiceCreatedTimeMillis = SystemClock.elapsedRealtime();
        Log.d(TAG, "onCreate");
    }

    @Override
    public void onDetect(@NonNull AlwaysOnHotwordDetector.EventPayload eventPayload,
            long timeoutMillis, @NonNull Callback callback) {
        Log.d(TAG, "onDetect for DSP source");

        if (mIsNoNeedActionDuringDetection) {
            mIsNoNeedActionDuringDetection = false;
            return;
        }

        if (!canReadAudio()) {
            callback.onDetected(DETECTED_RESULT_FOR_MIC_FAILURE);
            return;
        }

        // TODO: Check the capture session (needs to be reflectively accessed).
        byte[] data = eventPayload.getData();
        if (data != null && data.length > 0) {
            if (mIsTestUnexpectedCallback) {
                Log.d(TAG, "callback onDetected twice");
                callback.onDetected(DETECTED_RESULT);
                callback.onDetected(DETECTED_RESULT);
                mIsTestUnexpectedCallback = false;
                return;
            }

            // Create the unaccepted HotwordDetectedResult first to test the protection in the
            // onDetected callback function of HotwordDetectionService. When the bundle data of
            // HotwordDetectedResult is larger than max bundle size, it will throw the
            // IllegalArgumentException.
            PersistableBundle persistableBundle = new PersistableBundle();
            HotwordDetectedResult hotwordDetectedResult =
                    new HotwordDetectedResult.Builder()
                            .setHotwordPhraseId(eventPayload.getKeyphraseRecognitionExtras().get(
                                    0).getKeyphraseId())
                            .setExtras(persistableBundle)
                            .build();
            int key = 0;
            do {
                persistableBundle.putInt(Integer.toString(key), 0);
                key++;
            } while (Utils.getParcelableSize(persistableBundle)
                    <= HotwordDetectedResult.getMaxBundleSize());

            synchronized (mLock) {
                mHandler.postDelayed(() -> {
                    try {
                        if (mIsTestAudioEgress) {
                            if (mUseIllegalAudioEgressCopyBufferSize) {
                                callback.onDetected(
                                        Utils.AUDIO_EGRESS_DETECTED_RESULT_WRONG_COPY_BUFFER_SIZE);
                            } else {
                                callback.onDetected(Utils.AUDIO_EGRESS_DETECTED_RESULT);
                            }
                        } else {
                            callback.onDetected(hotwordDetectedResult);
                        }
                    } catch (IllegalArgumentException e) {
                        callback.onDetected(DETECTED_RESULT);
                    }
                }, mDetectionDelayMs);
            }
        } else {
            callback.onRejected(REJECTED_RESULT);
            if (mIsTestUnexpectedCallback) {
                Log.d(TAG, "callback onRejected again");
                callback.onRejected(REJECTED_RESULT);
                mIsTestUnexpectedCallback = false;
            }
        }
    }

    @Override
    public void onDetect(
            @NonNull ParcelFileDescriptor audioStream,
            @NonNull AudioFormat audioFormat,
            @Nullable PersistableBundle options,
            @NonNull Callback callback) {
        Log.d(TAG, "onDetect for external source");

        if (callback == null) {
            Log.w(TAG, "callback is null");
            return;
        }
        if (audioStream == null) {
            Log.w(TAG, "audioStream is null");
            return;
        }
        if (options != null) {
            if (options.getBoolean(Utils.KEY_DETECTION_REJECTED, false)) {
                Log.d(TAG, "Call onRejected for external source");
                callback.onRejected(REJECTED_RESULT);
                return;
            }
            if (options.getBoolean(Utils.KEY_ACCEPT_DETECTION, false)) {
                Log.d(TAG, "Accepting the detection based on options value.");
                callback.onDetected(DETECTED_RESULT);
                return;
            }
        }

        long startTime = System.currentTimeMillis();
        InputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(audioStream);
        try {
            // We added the fake audio data and set "hotword!" string at the head. Then we simulated
            // to verify the audio data with "hotword!" in HotwordDetectionService. If the audio
            // data includes "hotword!", it means that the hotword is valid.
            while (fis.available() < 8) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    // Nothing
                }
                if (System.currentTimeMillis() - startTime > 3000) {
                    Log.w(TAG, "Over timeout");
                    return;
                }
            }
            Log.d(TAG, "fis.available() = " + fis.available());
            byte[] buffer = new byte[8];
            fis.read(buffer, 0, 8);
            if (isSame(buffer, FAKE_HOTWORD_AUDIO_DATA,
                    buffer.length)) {
                Runnable sendDetect = () -> {
                    Log.d(TAG, "call callback.onDetected");
                    if (mIsTestAudioEgress) {
                        if (mUseIllegalAudioEgressCopyBufferSize) {
                            callback.onDetected(
                                    Utils.AUDIO_EGRESS_DETECTED_RESULT_WRONG_COPY_BUFFER_SIZE);
                        } else {
                            try {
                                ParcelFileDescriptor[] audioPipe =
                                        ParcelFileDescriptor.createPipe();
                                OutputStream audioPipeOutputStream =
                                        new ParcelFileDescriptor.AutoCloseOutputStream(
                                                audioPipe[PIPE_WRITE_END_INDEX]);
                                audioPipeOutputStream.write(FAKE_HOTWORD_AUDIO_DATA);
                                HotwordAudioStream resultAudioStream =
                                        Utils.createNewHotwordAudioStream(
                                                audioPipe[PIPE_READ_END_INDEX]);
                                HotwordDetectedResult detectedResult =
                                        Utils.createNewAudioEgressDetectedResult(
                                                resultAudioStream);
                                mExternalAudioStreamReceived = fis;
                                mDetectedResultOutputStream = audioPipeOutputStream;
                                callback.onDetected(detectedResult);
                                audioPipe[PIPE_READ_END_INDEX].close();
                            } catch (IOException ex) {
                                Log.e(TAG, "Unexpected IOException: ", ex);
                            }
                        }
                    } else {
                        callback.onDetected(DETECTED_RESULT);
                    }
                };

                synchronized (mLock) {
                    if (mDetectionDelayMs > 0) {
                        mHandler.postDelayed(sendDetect, mDetectionDelayMs);
                    } else {
                        sendDetect.run();
                    }
                }
            } else {
                callback.onRejected(REJECTED_RESULT);
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to read data : ", e);
        } finally {
            if (mShouldCloseExternalAudioStreamAfterRead) {
                Log.d(TAG, "Closing external audio stream.");
                try {
                    fis.close();
                } catch (IOException e2) {
                    Log.e(TAG, "Unable to close external audio stream.", e2);
                }
            }
        }
    }

    @Override
    public void onDetect(@NonNull Callback callback) {
        Log.d(TAG, "onDetect for Mic source");

        synchronized (mLock) {
            if (mDetectionJob != null) {
                throw new IllegalStateException("onDetect called while already detecting");
            }
            if (!mStopDetectionCalled) {
                // Delaying this allows us to test other flows, such as stopping detection. It's
                // also more realistic to schedule it onto another thread.
                mDetectionJob = () -> {
                    Log.d(TAG, "Sending detected result");
                    if (mIsTestUnexpectedCallback) {
                        Log.d(TAG, "callback onDetected twice");
                        callback.onDetected(DETECTED_RESULT);
                        callback.onDetected(DETECTED_RESULT);
                        mIsTestUnexpectedCallback = false;
                        return;
                    }
                    if (canReadAudio()) {
                        if (mIsTestAudioEgress) {
                            if (mUseIllegalAudioEgressCopyBufferSize) {
                                callback.onDetected(
                                        Utils.AUDIO_EGRESS_DETECTED_RESULT_WRONG_COPY_BUFFER_SIZE);
                            } else {
                                callback.onDetected(Utils.AUDIO_EGRESS_DETECTED_RESULT);
                            }
                        } else {
                            callback.onDetected(DETECTED_RESULT);
                        }
                    } else {
                        callback.onDetected(DETECTED_RESULT_FOR_MIC_FAILURE);
                    }
                };
                mHandler.postDelayed(mDetectionJob, mDetectionDelayMs);
            } else {
                Log.d(TAG, "Sending detected result after stop detection");
                // We can't store and use this callback in onStopDetection (not valid anymore
                // there), so instead we trigger detection again to report the event.
                callback.onDetected(DETECTED_RESULT_AFTER_STOP_DETECTION);
            }
        }
    }

    @Override
    public void onStopDetection() {
        super.onStopDetection();
        Log.d(TAG, "onStopDetection");
        synchronized (mLock) {
            mHandler.removeCallbacks(mDetectionJob);
            mDetectionJob = null;
            mStopDetectionCalled = true;
        }
    }

    @Override
    public void onUpdateState(
            @Nullable PersistableBundle options,
            @Nullable SharedMemory sharedMemory,
            long callbackTimeoutMillis,
            @Nullable IntConsumer statusCallback) {
        super.onUpdateState(options, sharedMemory, callbackTimeoutMillis, statusCallback);
        Log.d(TAG, "onUpdateState");

        // Reset mDetectionJob and mStopDetectionCalled when service is initializing.
        synchronized (mLock) {
            // When the service is initializing, the statusCallback will be not null.
            if (statusCallback != null) {
                Log.i(TAG, "onUpdateState called with statusCallback");
                if (mDetectionJob != null) {
                    Log.d(TAG, "onUpdateState mDetectionJob is not null");
                    mHandler.removeCallbacks(mDetectionJob);
                    mDetectionJob = null;
                }
                mStopDetectionCalled = false;

                if (options != null) {
                    if (options.getInt(Utils.KEY_TEST_SCENARIO, -1)
                            == Utils.EXTRA_HOTWORD_DETECTION_SERVICE_SEND_OVER_MAX_INIT_STATUS) {
                        Log.d(TAG, "send over the max custom initialization status");
                        final int initializationStatus =
                                SandboxedDetectionInitializer.getMaxCustomInitializationStatus();
                        try {
                            statusCallback.accept(initializationStatus + 1);
                        } catch (IllegalArgumentException ex) {
                            Log.d(TAG, "expect to get IllegalArgumentException here");
                            statusCallback.accept(initializationStatus);
                        }
                        return;
                    } else if (options.getInt(Utils.KEY_TEST_SCENARIO, -1)
                            == Utils.EXTRA_HOTWORD_DETECTION_SERVICE_SEND_CUSTOM_INIT_STATUS) {
                        Log.d(TAG, "send custom initialization status");
                        statusCallback.accept(options.getInt(Utils.KEY_INITIALIZATION_STATUS, -1));
                        return;
                    } else if (options.getInt(Utils.KEY_TEST_SCENARIO, -1)
                            == Utils.EXTRA_HOTWORD_DETECTION_SERVICE_SEND_SUCCESS_IF_CREATED_AFTER) {
                        // verify that the HotwordDetectionService was created after the timestamp
                        // passed in via the options parameter
                        long optionsTimestampMillis = options.getLong(Utils.KEY_TIMESTAMP_MILLIS,
                                -1);
                        Log.d(TAG, "send initialization success if created after: "
                                + optionsTimestampMillis
                                + ", onCreate timestamp=" + mServiceCreatedTimeMillis);
                        statusCallback.accept((mServiceCreatedTimeMillis > optionsTimestampMillis)
                                ? SandboxedDetectionInitializer.INITIALIZATION_STATUS_SUCCESS
                                : SandboxedDetectionInitializer.getMaxCustomInitializationStatus());
                        return;
                    }
                }
            }
            if (options != null) {
                mDetectionDelayMs = options.getInt(Utils.KEY_DETECTION_DELAY_MS, 0);

                if (options.getInt(Utils.KEY_TEST_SCENARIO, -1)
                        == Utils.EXTRA_HOTWORD_DETECTION_SERVICE_CLEAR_SOFTWARE_DETECTION_JOB) {
                    Log.d(TAG, "options : Clear software detection job");
                    if (mDetectionJob != null) {
                        Log.d(TAG, "Clear mDetectionJob");
                        mHandler.removeCallbacks(mDetectionJob);
                        mDetectionJob = null;
                    }
                    return;
                }
            }
        }

        if (options != null) {
            if (options.getInt(Utils.KEY_TEST_SCENARIO, -1)
                    == Utils.EXTRA_HOTWORD_DETECTION_SERVICE_ON_UPDATE_STATE_CRASH) {
                Log.d(TAG, "Crash itself. Pid: " + Process.myPid());
                Process.killProcess(Process.myPid());
                return;
            }
            if (options.getInt(Utils.KEY_TEST_SCENARIO, -1)
                    == Utils.EXTRA_HOTWORD_DETECTION_SERVICE_ON_UPDATE_STATE_UNEXPECTED_CALLBACK) {
                Log.d(TAG, "options : Test unexpected callback");
                mIsTestUnexpectedCallback = true;
                return;
            }
            if (options.getInt(Utils.KEY_TEST_SCENARIO, -1)
                    == Utils.EXTRA_HOTWORD_DETECTION_SERVICE_ENABLE_AUDIO_EGRESS) {
                mUseIllegalAudioEgressCopyBufferSize = options.getBoolean(
                        Utils.KEY_AUDIO_EGRESS_USE_ILLEGAL_COPY_BUFFER_SIZE,
                        /* defaultValue= */ false);
                mShouldCloseExternalAudioStreamAfterRead =
                        options.getBoolean(
                                Utils.KEY_AUDIO_EGRESS_CLOSE_AUDIO_STREAM_AFTER_READ,
                                /* defaultValue= */ true);
                Log.d(TAG, "options : Test audio egress use illegal copy buffer size = "
                        + mUseIllegalAudioEgressCopyBufferSize);
                mIsTestAudioEgress = true;
                return;
            }
            if (options.getInt(Utils.KEY_TEST_SCENARIO, -1)
                    == Utils.EXTRA_HOTWORD_DETECTION_SERVICE_NO_NEED_ACTION_DURING_DETECTION) {
                Log.d(TAG, "options : Test no need action during detection");
                mIsNoNeedActionDuringDetection = true;
                return;
            }
            if (options.getInt(Utils.KEY_TEST_SCENARIO, -1)
                    == Utils.EXTRA_HOTWORD_DETECTION_SERVICE_CAN_READ_AUDIO_DATA_IS_NOT_ZERO) {
                Log.d(TAG, "options : Test can read audio, and data is not zero");
                mCheckAudioDataIsNotZero = true;
                return;
            }
            if (options.getBoolean(
                    Utils.KEY_CLOSE_INPUT_AUDIO_STREAM_IF_OUTPUT_PIPE_BROKEN, false)) {
                Log.d(TAG, "Received request to close input audio stream if output is closed");
                if (mExternalAudioStreamReceived == null) {
                    Log.e(TAG, "Ignored request because input stream is null");
                    return;
                }
                if (mDetectedResultOutputStream == null) {
                    Log.e(TAG, "Ignored request because result output stream is null");
                    return;
                }
                try {
                    /*
                     * Write an arbitrary byte. This should trigger an error in the system_server
                     * thread that copies from the result this service returns to the result
                     * received by the test.
                     */
                    mDetectedResultOutputStream.write(1);
                    /*
                     * Wait for the copy thread mentioned above to clean up, which should close the
                     * read-end of mDetectedResultOutputStream. Then write one more byte to trigger
                     * an IOException here.
                     */
                    SystemClock.sleep(2000);
                    mDetectedResultOutputStream.write(1);

                } catch (IOException ex) {
                    Log.d(
                            TAG,
                            "Output stream unwritable (which is expected), closing external audio"
                                    + " stream received from wearable.");
                    try {
                        mExternalAudioStreamReceived.close();
                    } catch (IOException ex2) {
                        Log.e(
                                TAG,
                                "Unable to close external audio stream received from wearable.",
                                ex2);
                    }
                }
                return;
            }

            String fakeData = options.getString(KEY_FAKE_DATA);
            if (!TextUtils.equals(fakeData, VALUE_FAKE_DATA)) {
                Log.d(TAG, "options : data is not the same");
                return;
            }
        }

        if (sharedMemory != null) {
            try {
                sharedMemory.mapReadWrite();
                Log.d(TAG, "sharedMemory : is not read-only");
                return;
            } catch (ErrnoException e) {
                // For read-only case
            } finally {
                sharedMemory.close();
            }
        }

        // Report success
        Log.d(TAG, "onUpdateState success");
        if (statusCallback != null) {
            statusCallback.accept(INITIALIZATION_STATUS_SUCCESS);
        }
    }

    private boolean isSame(byte[] array1, byte[] array2, int length) {
        if (length <= 0) {
            return false;
        }
        if (array1 == null || array2 == null || array1.length < length || array2.length < length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (array1[i] != array2[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean canReadAudio() {
        int bytesPerSample = 2; // for ENCODING_PCM_16BIT
        int sampleRate = 16000;
        int bytesPerSecond = bytesPerSample * sampleRate; // for single channel
        AudioRecord record =
                new AudioRecord.Builder()
                        .setAudioAttributes(
                                new AudioAttributes.Builder()
                                        .setInternalCapturePreset(MediaRecorder.AudioSource.HOTWORD)
                                        .build())
                        .setAudioFormat(
                                new AudioFormat.Builder()
                                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                        .setSampleRate(sampleRate)
                                        .build())
                        .setBufferSizeInBytes(bytesPerSecond)
                        .build();
        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "Failed to initialize AudioRecord");
            record.release();
            return false;
        }

        record.startRecording();
        try {
            byte[] buffer = new byte[bytesPerSecond]; // read 1 second of audio
            int numBytes = 0;
            while (numBytes < buffer.length) {
                int bytesRead =
                        record.read(buffer, numBytes, Math.min(1024, buffer.length - numBytes));
                if (bytesRead < 0) {
                    Log.e(TAG, "Error reading from mic: " + bytesRead);
                    return false;
                }
                numBytes += bytesRead;
            }
            // The audio data will be zero on virtual device, so it would be better to skip to
            // check the audio data.
            if (Utils.isVirtualDevice()) {
                return true;
            }
            if (mCheckAudioDataIsNotZero) {
                for (byte b : buffer) {
                    // TODO: Maybe check that some portion of the bytes are non-zero.
                    if (b != 0) {
                        return true;
                    }
                }
                Log.d(TAG, "All data are zero");
                return false;
            }

            return true;
        } finally {
            record.release();
        }
    }
}
