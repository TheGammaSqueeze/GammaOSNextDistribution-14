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

package android.attributionsource.cts.blamed2

import android.content.ContextParams
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioRecord.Builder
import android.media.MediaRecorder
import android.speech.RecognitionService

class MyRecognitionService : RecognitionService() {
    var recorder: AudioRecord? = null
        get() = field
        set(recorder) { field = recorder }

    override fun onStartListening(intent: Intent, callback: Callback) {
        when (intent.extras?.get(OPERATION)!!) {
            OPERATION_MIC_RECO_WITH_ATTRIBUTION -> {
                performOperationMicRecoWithAttribution(callback)
                return
            }
            OPERATION_INJECT_RECO_WITHOUT_ATTRIBUTION -> {
                performOperationInjectRecoWithoutAttribution(callback)
                return
            }
        }
    }

    override fun onStopListening(callback: Callback) {}

    override fun onCancel(callback: Callback) {
        val finalRecorder = recorder
        if (finalRecorder != null) {
            finalRecorder.stop()
        }
        mainExecutor.execute({
            callback.bufferReceived(ByteArray(0))
        })
    }

    fun performOperationMicRecoWithAttribution(callback: Callback) {
        val attributionContext = createContext(ContextParams.Builder()
                .setNextAttributionSource(callback.callingAttributionSource)
                .build())

        // Setup a recorder
        val localRecorder = Builder()
                .setContext(attributionContext)
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setBufferSizeInBytes(
                        AudioRecord.getMinBufferSize(8000,
                                AudioFormat.CHANNEL_IN_MONO,
                                AudioFormat.ENCODING_PCM_16BIT) * 10)
                .setAudioFormat(AudioFormat.Builder()
                        .setSampleRate(8000)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build())
                .build()

        // Start recognition
        localRecorder.startRecording()

        // Pretend we do something...
        callback.bufferReceived(ByteArray(0))

        recorder = localRecorder
    }

    fun performOperationInjectRecoWithoutAttribution(callback: Callback) {
        mainExecutor.execute({
            callback.bufferReceived(ByteArray(0))
        })
    }

    companion object {
        val OPERATION = "operation"
        val OPERATION_MIC_RECO_WITH_ATTRIBUTION = "operation:mic_reco_with_attribution"
        val OPERATION_INJECT_RECO_WITHOUT_ATTRIBUTION = "operation:inject_reco_without_attribution"
    }
}
