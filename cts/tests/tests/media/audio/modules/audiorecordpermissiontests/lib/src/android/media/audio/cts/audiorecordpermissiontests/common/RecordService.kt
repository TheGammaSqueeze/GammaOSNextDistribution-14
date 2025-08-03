/*
 * Copyright 2023 The Android Open Source Project
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

package android.media.audio.cts.audiorecordpermissiontests.common

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.os.Bundle
import android.os.IBinder
import android.util.Log

import kotlin.math.abs

import java.util.concurrent.Future
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Service which can records and sends response intents when recording moves between silenced
 * and unsilenced state.
 */
open class RecordService : Service() {
    val TAG = getAppName() + "RecordService"
    val PREFIX = "android.media.audio.cts." + getAppName()

    val mIsRecording = AtomicBoolean(false)
    val mExecutor = Executors.newFixedThreadPool(2)

    var mFuture : Future<Any>? = null

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "Receive onStartCommand" + intent)
        when (intent.getAction()) {
            PREFIX + ACTION_START_RECORD -> {
                Log.i(TAG, "Receive START_RECORD" + intent.getExtras())
                if (mIsRecording.compareAndSet(false, true)) {
                    try {
                        if (intent.getBooleanExtra(EXTRA_IS_FOREGROUND, false)) {
                            Log.i(TAG, "Going foreground with capabilities " + getCapabilities())
                            startForeground(1, buildNotification(), getCapabilities())
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "exception", e)
                        throw e
                    }
                    mFuture = mExecutor.submit(::record, Object())
                }
            }
            PREFIX + ACTION_STOP_RECORD -> {
                Log.i(TAG, "Receive STOP_RECORD")
                cleanup()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        cleanup()
        mExecutor.shutdown()
    }

    // Binding cannot be used since that affects the proc state
    override fun onBind(intent: Intent): IBinder? { return null }

    /**
     * For subclasses to return the package name for receiving intents.
     */
    open fun getAppName(): String { return "Base" }

    /**
     * For subclasses to return the capabilities to start the service with.
     */
    open fun getCapabilities(): Int { return 0 }

    /**
     * If recording, stop recording, send response intent, and stop the service
     */
    private fun cleanup() {
        if (mIsRecording.compareAndSet(true, false)) {
            mFuture!!.get()
            mFuture = null
            Log.i(TAG, "FINISH_TEARDOWN")
            sendBroadcast(Intent(PREFIX + ACTION_FINISH_TEARDOWN).setPackage(TARGET_PACKAGE))
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /**
     * Continuously record while {@link mIsRecording} is true. Returns when false.
     * Send intents as stream moves in and out of being silenced.
     */
    private fun record() {
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val sampleRate = 32000
        val format = AudioFormat.ENCODING_PCM_16BIT
        val bufferSizeInBytes = 2 * AudioRecord.getMinBufferSize(sampleRate, channelConfig,
                format)
        val audioRecord = AudioRecord.Builder()
                        .setAudioFormat(
                                AudioFormat.Builder()
                                        .setEncoding(format)
                                        .setSampleRate(sampleRate)
                                        .setChannelMask(channelConfig)
                                        .build())
                        .setBufferSizeInBytes(bufferSizeInBytes)
                        .build()

        audioRecord.startRecording()
        var isSilenced = true
        val data = ShortArray(bufferSizeInBytes / 2)

        while (mIsRecording.get()) {
            val result = audioRecord.read(data, 0, data.size)
            if (result < 0) {
                throw IllegalStateException("AudioRecord read invalid result: " + result)
            } else if (result == 0) {
                continue
            }
            val power = data.take(result).map {abs(it.toInt())}.sum()
            if (isSilenced && power != 0) {
                mExecutor.execute {
                    Log.i(TAG, "BEGAN_RECEIVE_AUDIO")
                    sendBroadcast(Intent(PREFIX + ACTION_BEGAN_RECEIVE_AUDIO)
                            .setPackage(TARGET_PACKAGE))
                }
                isSilenced = false
            } else if (!isSilenced && power == 0) {
                mExecutor.execute {
                    Log.i(TAG, "BEGAN_RECEIVE_SILENCE")
                    sendBroadcast(Intent(PREFIX + ACTION_BEGAN_RECEIVE_SILENCE)
                            .setPackage(TARGET_PACKAGE))
                }
                isSilenced = true
            }
        }
        audioRecord.stop()
        audioRecord.release()
    }

    /**
     * Create a notification which is required to start a foreground service
     */
    private fun buildNotification() : Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(NotificationChannel(
                        "all",
                        "All Notifications",
                        NotificationManager.IMPORTANCE_NONE))

        return Notification.Builder(this, "all")
                .setContentTitle("Recording audio")
                .setContentText("recording...")
                .setSmallIcon(R.drawable.ic_fg)
                .build()
    }
}
