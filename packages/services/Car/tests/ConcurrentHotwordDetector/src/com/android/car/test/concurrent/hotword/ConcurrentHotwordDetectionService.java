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

package com.android.car.test.concurrent.hotword;

import android.annotation.Nullable;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class ConcurrentHotwordDetectionService extends Service {

    static final String TAG = "ConcurrentHotwordDetectionService";

    public static final int MSG_START_DETECT = 1;
    public static final int MSG_STOP_DETECT = 2;
    // Only runs recognizer for one minute
    public static final int MSG_START_RECOGNIZER = 3;
    public static final int MSG_STOP_SERVICE = 4;

    public static final int MSG_START_DETECT_REPLY = 1;
    public static final int MSG_STOP_DETECT_REPLY = 2;
    public static final int MSG_START_RECOGNIZER_REPLY = 3;

    public static final String MESSAGE_REPLY = "reply yo!";
    public static final int RECOGNIZER_RUN_TIME = 60_000;

    private final Object mLock = new Object();

    private Messenger mMessenger;
    @GuardedBy("mLock")
    private SpeechRecognizer mSpeechRecognizer;

    @GuardedBy("mLock")
    private Thread mRecordingThread;

    private final AtomicBoolean mStopRecording = new AtomicBoolean(true);

    @Override
    public IBinder onBind(Intent intent) {
        mMessenger = new Messenger(new Handler(Looper.getMainLooper()) {

            @Override
            public void handleMessage(Message msg) {
                Log.d(TAG, "Handle Message " + msg);
                Message replyMessage = null;
                switch (msg.what) {
                    case MSG_START_DETECT:
                        onDetect();
                        replyMessage =
                                createMessage("Detection Started", MSG_START_DETECT_REPLY);
                        break;
                    case MSG_STOP_DETECT:
                        onStopDetection();
                        replyMessage =
                                createMessage("Detection Stopped", MSG_STOP_DETECT_REPLY);
                        break;
                    case MSG_STOP_SERVICE:
                        onStopDetection();
                        stopSelf();
                        return;
                    case MSG_START_RECOGNIZER:
                        startRecognizer(msg.replyTo);
                        replyMessage =
                                createMessage("Starting Recognizer", MSG_START_RECOGNIZER_REPLY);
                        break;
                    default:
                        super.handleMessage(msg);
                        Log.d(TAG, "Error no handler for message " + msg);
                        return;
                }
                sendReply(msg.replyTo, replyMessage);
            }
        });
        return mMessenger.getBinder();
    }

    private void onDetect() {
        Log.d(TAG, "onDetect for Mic source");
        Thread recordingThread = new Thread(this::recordAudio);
        recordingThread.start();
        synchronized (mLock) {
            mRecordingThread = recordingThread;
        }
    }

    private void onStopDetection() {
        Log.d(TAG, "onStopDetection");
        Thread recordingThread;
        synchronized (mLock) {
            recordingThread = mRecordingThread;
            mRecordingThread = null;
        }

        mStopRecording.set(true);

        try {
            recordingThread.join(/* timeout= */ 100);
        } catch (InterruptedException e) {
            Log.e(TAG, "onStopDetection could join thread", e);
        }
        Log.d(TAG, "onStopDetection detection stopped");
    }

    private void recordAudio() {
        Log.d(TAG, "recordAudio for Mic source");
        mStopRecording.set(false);
        int bytesPerSample = 2; // for ENCODING_PCM_16BIT
        int sampleRate = 16000;
        int bytesPerSecond = bytesPerSample * sampleRate; // for single channel
        AudioRecord record = null;
        try {
            AudioRecord.Builder recordBuilder =
                    new AudioRecord.Builder()
                            .setAudioAttributes(
                                    new AudioAttributes.Builder()
                                            .setInternalCapturePreset(
                                                    MediaRecorder.AudioSource.HOTWORD)
                                            .build())
                            .setAudioFormat(
                                    new AudioFormat.Builder()
                                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                            .setSampleRate(sampleRate)
                                            .build())
                            .setBufferSizeInBytes(bytesPerSecond);

            Log.d(TAG, "recordAudio building");
            record = recordBuilder.build();
            Log.d(TAG, "recordAudio built");
        } catch (Exception e) {
            Log.e(TAG, "recordAudio error", e);
        }

        if (record == null) {
            return;
        }

        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "Failed to initialize AudioRecord");
            record.release();
            return;
        }

        Log.d(TAG, "recordAudio recording starting");
        record.startRecording();
        Log.d(TAG, "recordAudio recording started");

        while (!mStopRecording.get()) {
            boolean canRead = canReadAudio(record, bytesPerSecond);
            Log.i(TAG, "recordAudio can record " + canRead);
        }
        record.stop();
        Log.i(TAG, "recordAudio stopped");
    }

    private boolean canReadAudio(AudioRecord record, int bytesPerSecond) {
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

        int counter = 100;
        for (byte b : buffer) {
            if ((b != 0) && (counter-- < 0)) {
                return true;
            }
        }
        Log.d(TAG, "All data are zero");
        return false;
    }

    private Message createMessage(String replyString, int what) {
        Message replyMessage =
                Message.obtain(/* handler= */ null, what, /* arg1= */ 0, /* arg2= */ 0);
        Bundle data = new Bundle();
        data.putString(MESSAGE_REPLY, replyString);
        replyMessage.setData(data);
        return replyMessage;
    }

    private void sendReply(@Nullable Messenger messenger, @Nullable Message reply) {
        if (messenger == null) {
            Log.i(TAG, "reply null messenger");
            return;
        }

        if (reply == null) {
            Log.i(TAG, "reply null message");
            return;
        }

        try {
            messenger.send(reply);
            Log.i(TAG, "reply message sent " + reply);
        } catch (RemoteException e) {
            Log.e(TAG, "replay error ", e);
        }
    }

    private void startRecognizer(Messenger replyTo) {
        synchronized (mLock) {
            mSpeechRecognizer =
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(getApplicationContext());

            RecognitionListener recognitionListener = new RecognitionListener() {

                @Override
                public void onReadyForSpeech(Bundle params) {
                    sendReply(replyTo, createMessage("Got ready for speech",
                            MSG_START_RECOGNIZER_REPLY));
                }

                @Override
                public void onBeginningOfSpeech() {
                    sendReply(replyTo, createMessage("Got beginning of speech",
                            MSG_START_RECOGNIZER_REPLY));
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    sendReply(replyTo, createMessage("Sound level changed, rms[dB]: " + rmsdB,
                            MSG_START_RECOGNIZER_REPLY));
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                    sendReply(replyTo, createMessage("Buffer Received, length:" + buffer.length,
                            MSG_START_RECOGNIZER_REPLY));
                }

                @Override
                public void onEndOfSpeech() {
                    sendReply(replyTo, createMessage("End of Speech",
                            MSG_START_RECOGNIZER_REPLY));
                }

                @Override
                public void onError(int error) {
                    sendReply(replyTo, createMessage("Got an error:" + error,
                            MSG_START_RECOGNIZER_REPLY));
                }

                @Override
                public void onResults(Bundle results) {
                    replyWithResults(results);

                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    replyWithResults(partialResults);
                }

                @Override
                public void onSegmentResults(Bundle segmentResults) {
                    replyWithResults(segmentResults);
                }

                @Override
                public void onEndOfSegmentedSession() {
                    sendReply(replyTo, createMessage("End of segmented session",
                            MSG_START_RECOGNIZER_REPLY));
                }

                @Override
                public void onEvent(int eventType, Bundle params) {

                }

                private void replyWithResults(Bundle recognitionResults) {
                    ArrayList<String> results = recognitionResults
                            .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    String reply = results.stream().collect(Collectors.joining(" "));

                    sendReply(replyTo, createMessage(reply, MSG_START_RECOGNIZER_REPLY));
                }
            };

            mSpeechRecognizer.setRecognitionListener(recognitionListener);

            Intent requestIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            requestIntent.putExtra(
                    RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                    RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS);
            requestIntent.putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, RECOGNIZER_RUN_TIME);

            mSpeechRecognizer.startListening(requestIntent);
        }
    }
}
