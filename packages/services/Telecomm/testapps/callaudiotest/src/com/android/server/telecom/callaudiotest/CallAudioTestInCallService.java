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
 * limitations under the License
 */

package com.android.server.telecom.callaudiotest;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.IBinder;
import android.telecom.Call;
import android.telecom.InCallService;
import android.telecom.Log;
import android.telecom.VideoProfile;

import java.io.IOException;
import java.lang.String;

/**
 * Simple test InCallService which answers a call automatically and plays back some looping audio.
 * Intended for testing call audio between devices.
 */
public class CallAudioTestInCallService extends InCallService {
    private Call mIncomingCall;
    private boolean mIsAutoAnswerEnabled = false;
    private MediaPlayer mMediaPlayer;
    private Call.Callback mCallback = new Call.Callback() {
        @Override
        public void onStateChanged(Call call, int state) {
            if (state == Call.STATE_ACTIVE) {
                startPlaying();
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        SharedPreferences prefs = getSharedPreferences(CallAudioTestActivity.AUDIO_TEST_PREFS,
                MODE_PRIVATE);
        mIsAutoAnswerEnabled = prefs.getBoolean(CallAudioTestActivity.AUTO_ANSWER_ENABLED, false);
        return super.onBind(intent);
    }

    @Override
    public void onCallAdded(Call call) {
        if (!mIsAutoAnswerEnabled) {
            Log.i(this, "onCallAdded - autoanswer disabled, skip");
            return;
        }
        if (call.getDetails().getState() == Call.STATE_RINGING) {
            mIncomingCall = call;
            mIncomingCall.registerCallback(mCallback);
            mIncomingCall.answer(VideoProfile.STATE_AUDIO_ONLY);

            Log.i(this, "onCallAdded - ringing call");
        } else {
            Log.i(this, "onCallAdded - nonringing call");
        }
    }

    @Override
    public void onCallRemoved(Call call) {
        if (mIncomingCall == call) {
            mIncomingCall = null;
            if (mMediaPlayer != null) {
                mMediaPlayer.stop();
                mMediaPlayer.release();
            }
        }
    }

    private void startPlaying() {
        AudioDeviceInfo telephonyDevice = getTelephonyDevice(getSystemService(AudioManager.class));
        if (telephonyDevice != null) {
            Log.i(this, "startPlaying: create player for speech");
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setOnCompletionListener(mediaPlayer -> Log.w(this, "startPlaying: done"));
            mMediaPlayer.setOnErrorListener(
                (mediaPlayer, what, extra) -> {
                        Log.w(this, "startPlaying: playback failed!");
                        return true; // Error handled
                        });
            mMediaPlayer.setLooping(true);
            mMediaPlayer.setVolume(1.0f);
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).build();
            mMediaPlayer.setAudioAttributes(audioAttributes);
            try {
                mMediaPlayer.setDataSource(getResources().openRawResourceFd(R.raw.speech));
                mMediaPlayer.prepare();
            } catch (IOException e) {
                mMediaPlayer.release();
                throw new IllegalStateException(e);
            }
            if (!mMediaPlayer.setPreferredDevice(telephonyDevice)) {
                Log.w(this, "startPlaying: setPreferredDevice failed");
            }
            mMediaPlayer.start();

        }
    }

    private AudioDeviceInfo getTelephonyDevice(AudioManager audioManager) {
        AudioDeviceInfo[] deviceList = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        for (AudioDeviceInfo device: deviceList) {
            if (device.getType() == AudioDeviceInfo.TYPE_TELEPHONY) {
                return device;
            }
        }
        return null;
    }
}
