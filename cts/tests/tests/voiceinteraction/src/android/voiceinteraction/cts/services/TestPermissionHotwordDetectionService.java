/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.voiceinteraction.cts.services;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.SharedMemory;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.HotwordDetectionService;
import android.util.Log;
import android.voiceinteraction.cts.testcore.Helper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.function.IntConsumer;

/**
 * This service is used to test the permissions that granted by HotwordDetectionService.
 */
public class TestPermissionHotwordDetectionService extends HotwordDetectionService {
    static final String TAG = "TestPermissionHotwordDetectionService";

    @Override
    public void onDetect(@NonNull AlwaysOnHotwordDetector.EventPayload eventPayload,
            long timeoutMillis, @NonNull Callback callback) {
        Log.d(TAG, "onDetect for DSP source");

        AudioFormat recFormat =
                new AudioFormat.Builder()
                        .setSampleRate(8000)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build();

        // If the HotwordDetectionService doesn't have the CAPTURE_AUDIO_OUTPUT permission,
        // it can't create the AudioRecord with the VOICE_DOWNLINK of audio Source Successfully.
        AudioRecord audioRecord = null;
        try {
            Log.d(TAG, "Create VOICE_DOWNLINK AudioRecord Start");
            audioRecord = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_DOWNLINK)
                    .setAudioFormat(recFormat)
                    .setBufferSizeInBytes(recFormat.getSampleRate() * 2)
                    .build();
            Log.d(TAG, "Create VOICE_DOWNLINK AudioRecord Successfully");
            callback.onDetected(Helper.DETECTED_RESULT);
        } catch (Exception e) {
            Log.d(TAG, "Create VOICE_DOWNLINK AudioRecord Exception = " + e);
            callback.onDetected(Helper.DETECTED_RESULT_FOR_MIC_FAILURE);
        } finally {
            if (audioRecord != null) {
                audioRecord.release();
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
    }

    @Override
    public void onDetect(@NonNull Callback callback) {
        Log.d(TAG, "onDetect for Mic source");
    }

    @Override
    public void onUpdateState(
            @Nullable PersistableBundle options,
            @Nullable SharedMemory sharedMemory,
            long callbackTimeoutMillis,
            @Nullable IntConsumer statusCallback) {
        super.onUpdateState(options, sharedMemory, callbackTimeoutMillis, statusCallback);
        Log.d(TAG, "onUpdateState");

        // onUpdateState has been tested in the MainHotwordDetectionService
        if (statusCallback != null) {
            statusCallback.accept(INITIALIZATION_STATUS_SUCCESS);
        }
    }
}
