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

package android.voiceinteraction.service;

import static android.voiceinteraction.service.MainHotwordDetectionService.FAKE_HOTWORD_AUDIO_DATA;

import android.app.ambientcontext.AmbientContextEventRequest;
import android.app.wearable.WearableSensingManager;
import android.media.AudioFormat;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.SharedMemory;
import android.service.ambientcontext.AmbientContextDetectionResult;
import android.service.ambientcontext.AmbientContextDetectionServiceStatus;
import android.service.voice.HotwordAudioStream;
import android.service.wearable.WearableSensingService;
import android.util.Log;
import android.voiceinteraction.common.Utils;

import java.io.OutputStream;
import java.util.Set;
import java.util.function.Consumer;

/** The {@link WearableSensingService} to use with voice interaction CTS tests. */
public class MainWearableSensingService extends WearableSensingService {

    /** PersistableBundle key that represents an action, such as setup and send audio. */
    public static final String BUNDLE_ACTION_KEY = "ACTION";

    /** PersistableBundle value that represents a request to reset the service. */
    public static final String ACTION_RESET = "RESET";

    /** PersistableBundle value that represents a request to send audio to the audioConsumer. */
    public static final String ACTION_SEND_AUDIO = "SEND_AUDIO";

    /**
     * PersistableBundle value that represents a request to send non-hotword audio to the
     * audioConsumer.
     */
    public static final String ACTION_SEND_NON_HOTWORD_AUDIO = "SEND_NON_HOTWORD_AUDIO";

    /**
     * PersistableBundle value that represents a request to send more audio data to the stream
     * previously sent to audioConsumer.
     */
    public static final String ACTION_SEND_MORE_AUDIO_DATA = "SEND_MORE_AUDIO_DATA";

    /**
     * PersistableBundle value that represents a request to verify
     * onValidatedByHotwordDetectionService is called.
     */
    public static final String ACTION_VERIFY_HOTWORD_VALIDATED_CALLED =
            "VERIFY_HOTWORD_VALIDATED_CALLED";

    /**
     * PersistableBundle value that represents a request to verify onStopHotwordAudioStream
     * is called.
     */
    public static final String ACTION_VERIFY_AUDIO_STOP_CALLED = "VERIFY_DATA_STOP_CALLED";

    /**
     * PersistableBundle value that represents a request to send non-hotword audio to the
     * audioConsumer along with an option that overrides the hotword detection result to positive.
     */
    public static final String ACTION_SEND_NON_HOTWORD_AUDIO_WITH_ACCEPT_DETECTION_OPTIONS =
            "SEND_NON_HOTWORD_AUDIO_WITH_ACCEPT_DETECTION_OPTIONS";

    private static final String TAG = "MainWearableSensingService";
    private static final AudioFormat FAKE_AUDIO_FORMAT =
            new AudioFormat.Builder()
                    .setSampleRate(10000)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build();
    private static final int PIPE_READ_INDEX = 0;
    private static final int PIPE_WRITE_INDEX = 1;
    // MainHotwordDetectionService will reject this byte stream as non-hotword
    private static final byte[] NON_HOTWORD_AUDIO =
            new byte[] {'n', 'o', 'n', 'h', 'o', 't', 'w', 'o', 'r', 'd'};

    private Consumer<HotwordAudioStream> mAudioConsumer;
    private volatile boolean mOnValidatedByHotwordDetectionServiceCalled = false;
    private volatile boolean mOnStopHotwordAudioStreamCalled = false;
    private OutputStream mAudioOutputStream;

    @Override
    public void onCreate() {
        Log.i(TAG, "#onCreate");
    }

    @Override
    public void onStartHotwordRecognition(
            Consumer<HotwordAudioStream> audioConsumer, Consumer<Integer> statusConsumer) {
        mAudioConsumer = audioConsumer;
        statusConsumer.accept(WearableSensingManager.STATUS_SUCCESS);
    }

    @Override
    public void onValidatedByHotwordDetectionService() {
        Log.i(TAG, "#onValidatedByHotwordDetectionService");
        mOnValidatedByHotwordDetectionServiceCalled = true;
    }

    @Override
    public void onStopHotwordAudioStream() {
        Log.i(TAG, "#onStopHotwordAudioStream");
        mOnStopHotwordAudioStreamCalled = true;
    }

    /** Unrelated to voice interaction, but used to set up the service and verify interactions. */
    @Override
    public void onDataProvided(
            PersistableBundle data, SharedMemory sharedMemory, Consumer<Integer> statusConsumer) {
        String action = data.getString(BUNDLE_ACTION_KEY);
        Log.i(TAG, "#onDataProvided, action: " + action);
        try {
            switch (action) {
                case ACTION_RESET:
                    reset();
                    statusConsumer.accept(WearableSensingManager.STATUS_SUCCESS);
                    return;
                case ACTION_SEND_AUDIO:
                    sendAudio(statusConsumer);
                    return;
                case ACTION_SEND_NON_HOTWORD_AUDIO:
                    sendNonHotwordAudio(statusConsumer);
                    return;
                case ACTION_SEND_NON_HOTWORD_AUDIO_WITH_ACCEPT_DETECTION_OPTIONS:
                    sendNonHotwordAudioWithAcceptDetectionOptions(statusConsumer);
                    return;
                case ACTION_SEND_MORE_AUDIO_DATA:
                    sendMoreAudioData(statusConsumer);
                    return;
                case ACTION_VERIFY_HOTWORD_VALIDATED_CALLED:
                    verifyHotwordValidatedCalled(statusConsumer);
                    return;
                case ACTION_VERIFY_AUDIO_STOP_CALLED:
                    verifyAudioStopCalled(statusConsumer);
                    return;
                default:
                    Log.w(TAG, "Unknown action: " + action);
                    statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
                    return;
            }
        } catch (Exception ex) {
            // Exception in this process will not show up in the test runner, so just Log it and
            // return an unknown status code.
            Log.e(TAG, "Unexpected exception in onDataProvided.", ex);
            statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
        }
    }

    private void sendAudio(Consumer<Integer> statusConsumer) throws Exception {
        // MainHotwordDetectionService will accept this data as hotword audio data
        sendAudio(FAKE_HOTWORD_AUDIO_DATA, statusConsumer, PersistableBundle.EMPTY);
    }

    private void sendNonHotwordAudio(Consumer<Integer> statusConsumer) throws Exception {
        // MainHotwordDetectionService will reject this as non-hotword audio data
        sendAudio(NON_HOTWORD_AUDIO, statusConsumer, PersistableBundle.EMPTY);
    }

    private void sendNonHotwordAudioWithAcceptDetectionOptions(Consumer<Integer> statusConsumer)
            throws Exception {
        PersistableBundle options = new PersistableBundle();
        // MainHotwordDetectionService will accept this result after reading the options
        options.putBoolean(Utils.KEY_ACCEPT_DETECTION, true);
        sendAudio(NON_HOTWORD_AUDIO, statusConsumer, options);
    }

    private void sendAudio(
            byte[] audioData,
            Consumer<Integer> statusConsumer,
            PersistableBundle options)
            throws Exception {
        Log.i(TAG, "#sendAudio");
        if (mAudioConsumer == null) {
            Log.e(TAG, "Cannot send audio. mAudioConsumer is null");
            statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
            return;
        }
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createReliablePipe();
        mAudioOutputStream = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[PIPE_WRITE_INDEX]);
        mAudioOutputStream.write(audioData);
        mAudioConsumer.accept(
                new HotwordAudioStream.Builder(FAKE_AUDIO_FORMAT, pipe[PIPE_READ_INDEX])
                        .setMetadata(options)
                        .build());
        pipe[PIPE_READ_INDEX].close();
        statusConsumer.accept(WearableSensingManager.STATUS_SUCCESS);
    }

    private void sendMoreAudioData(Consumer<Integer> statusConsumer) throws Exception {
        if (mAudioOutputStream == null) {
            Log.w(TAG, "Cannot send more audio data. mAudioOutputStream is null");
            statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
            return;
        }
        mAudioOutputStream.write('i'); // the exact value sent doesn't matter
        statusConsumer.accept(WearableSensingManager.STATUS_SUCCESS);
    }

    private void verifyHotwordValidatedCalled(Consumer<Integer> statusConsumer) throws Exception {
        // A better alternative is to have this wait on a latch because the callback is async,
        // but somehow awaiting here prevents other methods on WearableSensingService to be
        // called despite the AIDL being annotated with oneway.
        Log.i(TAG, "#verifyHotwordValidatedCalled");
        if (mOnValidatedByHotwordDetectionServiceCalled) {
            statusConsumer.accept(WearableSensingManager.STATUS_SUCCESS);
        } else {
            statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
        }
    }

    private void verifyAudioStopCalled(Consumer<Integer> statusConsumer) throws Exception {
        Log.i(TAG, "#verifyAudioStopCalled");
        if (mOnStopHotwordAudioStreamCalled) {
            statusConsumer.accept(WearableSensingManager.STATUS_SUCCESS);
        } else {
            statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
        }
    }

    private void reset() throws Exception {
        mOnValidatedByHotwordDetectionServiceCalled = false;
        mOnStopHotwordAudioStreamCalled = false;
        mAudioConsumer = null;
        if (mAudioOutputStream != null) {
            mAudioOutputStream.close();
            mAudioOutputStream = null;
        }
    }

    /********************************************************************************
     * Placeholder implementation of abstract methods unrelated to voice interaction.
     ********************************************************************************/
    @Override
    public void onDataStreamProvided(
            ParcelFileDescriptor parcelFileDescriptor, Consumer<Integer> statusConsumer) {}

    @Override
    public void onStartDetection(
            AmbientContextEventRequest request,
            String packageName,
            Consumer<AmbientContextDetectionServiceStatus> statusConsumer,
            Consumer<AmbientContextDetectionResult> detectionResultConsumer) {}

    @Override
    public void onStopDetection(String packageName) {}

    @Override
    public void onQueryServiceStatus(
            Set<Integer> eventTypes,
            String packageName,
            Consumer<AmbientContextDetectionServiceStatus> consumer) {}
}
