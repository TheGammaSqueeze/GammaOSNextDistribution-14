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

package com.android.server.telecom.transactionalVoipApp;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioRecordingConfiguration;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.OutcomeReceiver;
import android.telecom.CallException;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.util.Log;

import java.util.List;

public class Utils {
    public static final String TAG = "TransactionalAppUtils";
    public static final String CALLER_NAME = "Sundar Pichai";
    public static final String sEXTRAS_KEY = "ExtrasKey";
    public static final String sCALL_DIRECTION_KEY = "CallDirectionKey";
    public static final String CHANNEL_ID = "TelecomVoipAppChannelId";
    public static final int CALL_NOTIFICATION_ID = 123456;
    private static final int SAMPLING_RATE_HZ = 44100;

    public static final PhoneAccountHandle PHONE_ACCOUNT_HANDLE = new PhoneAccountHandle(
            new ComponentName("com.android.server.telecom.transactionalVoipApp",
                    "com.android.server.telecom.transactionalVoipApp.VoipAppMainActivity"), "123");

    public static final PhoneAccount PHONE_ACCOUNT =
            PhoneAccount.builder(PHONE_ACCOUNT_HANDLE, "test label")
                    .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED |
                            PhoneAccount.CAPABILITY_SUPPORTS_TRANSACTIONAL_OPERATIONS).build();


    public static Notification createCallStyleNotification(Context context) {
        Intent answerIntent = new Intent(context, InCallActivity.class);
        Intent rejectIntent = new Intent(context, InCallActivity.class);

        // Creating a pending intent and wrapping our intent
        PendingIntent pendingAnswer = PendingIntent.getActivity(context, 0,
                answerIntent, PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pendingReject = PendingIntent.getActivity(context, 0,
                rejectIntent, PendingIntent.FLAG_IMMUTABLE);


        Notification callStyleNotification = new Notification.Builder(context,
                CHANNEL_ID)
                .setContentText("Answer/Reject call")
                .setContentTitle("Incoming call")
                .setSmallIcon(R.drawable.ic_android_black_24dp)
                .setStyle(Notification.CallStyle.forIncomingCall(
                        new Person.Builder().setName(CALLER_NAME).setImportant(true).build(),
                        pendingAnswer, pendingReject)
                )
                .setFullScreenIntent(pendingAnswer, true)
                .setOngoing(true)
                .build();

        return callStyleNotification;
    }

    public static void postIncomingCallStyleNotification(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        nm.notify(Utils.CALL_NOTIFICATION_ID, createCallStyleNotification(context));
    }

    public static void updateCallStyleNotification_toOngoingCall(Context context) {
        PendingIntent ongoingCall = PendingIntent.getActivity(context, 0,
                new Intent(""), PendingIntent.FLAG_IMMUTABLE);

        Notification callStyleNotification = new Notification.Builder(context,
                CHANNEL_ID)
                .setContentText("active call in the TransactionalTestApp")
                .setContentTitle("Ongoing call")
                .setSmallIcon(R.drawable.ic_android_black_24dp)
                .setStyle(Notification.CallStyle.forOngoingCall(
                        new Person.Builder().setName(CALLER_NAME).setImportant(true).build(),
                        ongoingCall)
                )
                .setFullScreenIntent(ongoingCall, true)
                .setOngoing(true)
                .build();

        NotificationManager notificationManager =
                context.getSystemService(NotificationManager.class);

        notificationManager.notify(CALL_NOTIFICATION_ID, callStyleNotification);
    }

    public static void clearNotification(Context context) {
        NotificationManager notificationManager =
                context.getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.cancel(CALL_NOTIFICATION_ID);
        }
    }

    public static MediaPlayer createMediaPlayer(Context context) {
        int audioToPlay = (Math.random() > 0.5f) ?
                com.android.server.telecom.transactionalVoipApp.R.raw.sample_audio :
                com.android.server.telecom.transactionalVoipApp.R.raw.sample_audio2;
        MediaPlayer mediaPlayer = MediaPlayer.create(context, audioToPlay);
        mediaPlayer.setLooping(true);
        return mediaPlayer;
    }

    public static AudioRecord createAudioRecord() {
        return new AudioRecord.Builder()
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(SAMPLING_RATE_HZ)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                .setAudioSource(MediaRecorder.AudioSource.DEFAULT)
                .setBufferSizeInBytes(
                        AudioRecord.getMinBufferSize(SAMPLING_RATE_HZ,
                                AudioFormat.CHANNEL_IN_MONO,
                                AudioFormat.ENCODING_PCM_16BIT) * 10)
                .build();
    }


    public static AudioManager.AudioRecordingCallback getAudioRecordingCallback() {
        return new AudioManager.AudioRecordingCallback() {
            @Override
            public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs) {
                super.onRecordingConfigChanged(configs);

                for (AudioRecordingConfiguration config : configs) {
                    if (config != null) {
                        Log.i(TAG, String.format("onRecordingConfigChanged: random: "
                                        + "isClientSilenced=[%b], config=[%s]",
                                config.isClientSilenced(), config));
                    }
                }
            }
        };
    }

    public static OutcomeReceiver<Void, CallException> getLoggableOutcomeReceiver(String tag) {
        return new OutcomeReceiver<Void, CallException>() {
            @Override
            public void onResult(Void result) {
                Log.i(TAG, tag + " : onResult");
            }

            @Override
            public void onError(CallException exception) {
                Log.i(TAG, tag + " : onError");
            }
        };
    }
}