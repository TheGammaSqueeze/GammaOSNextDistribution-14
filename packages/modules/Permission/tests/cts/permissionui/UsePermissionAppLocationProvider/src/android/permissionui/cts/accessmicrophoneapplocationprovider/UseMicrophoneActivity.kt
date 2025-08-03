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
package android.permissionui.cts.accessmicrophoneapplocationprovider

import android.app.Activity
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler

private const val USE_DURATION_MS = 10000L
private const val SAMPLE_RATE_HZ = 44100

/** An activity that uses microphone. */
class UseMicrophoneActivity : Activity() {
    private var recorder: AudioRecord? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val attrContext = createAttributionContext("test.tag")
        useMic(attrContext)
        setResult(RESULT_OK)
        finish()
    }

    override fun finish() {
        recorder?.stop()
        recorder = null
        super.finish()
    }

    override fun onStop() {
        super.onStop()
        finish()
    }

    private fun useMic(context: Context) {
        recorder =
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE_HZ)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setContext(context)
                .build()
        recorder?.startRecording()
        Handler().postDelayed({ finish() }, USE_DURATION_MS)
    }
}
