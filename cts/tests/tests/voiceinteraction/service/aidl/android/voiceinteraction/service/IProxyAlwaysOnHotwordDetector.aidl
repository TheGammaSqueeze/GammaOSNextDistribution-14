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

package android.voiceinteraction.service;

import java.util.List;
import android.hardware.soundtrigger.SoundTrigger.KeyphraseRecognitionExtra;
import android.media.AudioFormat;
import android.os.PersistableBundle;
import android.os.SharedMemory;

interface IProxyAlwaysOnHotwordDetector {
    void updateState(
            in @nullable PersistableBundle options,
            in @nullable SharedMemory sharedMemory);
    boolean startRecognitionOnFakeAudioStream();
    boolean startRecognitionWithAudioStream(
            inout ParcelFileDescriptor audioStream,
            in AudioFormat audioFormat,
            in @nullable PersistableBundle options);
    boolean startRecognitionWithFlagsAndData(int recognitionFlags, in byte[] data);
    boolean startRecognitionWithFlags(int recognitionFlags);
    boolean startRecognition();
    boolean stopRecognition();
    void triggerHardwareRecognitionEventForTest(
            int status,
            int soundModelHandle,
            long halEventReceivedMillis,
            boolean captureAvailable,
            int captureSession,
            int captureDelayMs,
            int capturePreambleMs,
            boolean triggerInData,
            in AudioFormat captureFormat,
            in @nullable byte[] data,
            in List<KeyphraseRecognitionExtra> keyphraseExtras);
    void overrideAvailability(int availability);
    void resetAvailability();
    void destroy();
}