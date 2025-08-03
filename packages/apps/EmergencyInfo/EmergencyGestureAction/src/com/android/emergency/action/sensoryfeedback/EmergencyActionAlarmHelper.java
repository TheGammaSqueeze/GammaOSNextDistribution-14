/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.emergency.action.sensoryfeedback;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.provider.Settings;
import android.util.Log;

import com.android.emergency.action.EmergencyActionUtils;
import com.android.emergency.action.R;


public class EmergencyActionAlarmHelper {
    private static final String TAG = "AlarmSoundManager";
    private static final int USER_SET_ALARM_VOLUME_UNKNOWN = -1;
    private final Context mContext;

    private MediaPlayer mMediaPlayer;
    private AudioManager mAudioManager;
    private int mUserSetAlarmVolume;
    private boolean mResetAlarmVolumeNeeded;

    public EmergencyActionAlarmHelper(Context context) {
        this(context, USER_SET_ALARM_VOLUME_UNKNOWN);
    }

    public int getUserSetAlarmVolume() {
        return mUserSetAlarmVolume;
    }

    public EmergencyActionAlarmHelper(Context context, int userSetAlarmVolume) {
        mContext = context;
        mAudioManager = context.getSystemService(AudioManager.class);
        mUserSetAlarmVolume = userSetAlarmVolume;
    }

    public void playWarningSound() {
        if (!isPlayWarningSoundEnabled()) {
            return;
        }

        if (mMediaPlayer == null) {
            mMediaPlayer = MediaPlayer.create(
                    mContext,
                    R.raw.alarm,
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    /* audioSessionId= */ 0);
        }

        mMediaPlayer.setOnCompletionListener(mp -> mp.release());
        mMediaPlayer.setOnErrorListener(
                (MediaPlayer mp, int what, int extra) -> {
                    Log.w(TAG, "MediaPlayer playback failed with error code: " + what
                            + ", and extra code: " + extra);
                    mp.release();
                    return false;
                });

        setAlarmVolumeToFull();
        mMediaPlayer.start();
    }

    public void stopWarningSound() {
        if (mMediaPlayer != null) {
            try {
                mMediaPlayer.stop();
                mMediaPlayer.release();
            } catch (IllegalStateException e) {
                Log.w(TAG, "Exception when trying to stop media player");
            }
            mMediaPlayer = null;
        }

        resetAlarmVolume();
    }

    private void setAlarmVolumeToFull() {
        int streamType = AudioManager.STREAM_ALARM;
        if (mUserSetAlarmVolume == USER_SET_ALARM_VOLUME_UNKNOWN) {
            mUserSetAlarmVolume = mAudioManager.getStreamVolume(streamType);
        }
        mResetAlarmVolumeNeeded = true;

        Log.d(TAG, "Setting alarm volume from " + mUserSetAlarmVolume + "to full");
        mAudioManager.setStreamVolume(streamType,
                mAudioManager.getStreamMaxVolume(streamType), 0);
    }

    private void resetAlarmVolume() {
        if (mResetAlarmVolumeNeeded) {
            Log.d(TAG, "Resetting alarm volume to back to " + mUserSetAlarmVolume);
            mAudioManager.setStreamVolume(AudioManager.STREAM_ALARM, mUserSetAlarmVolume, 0);
            mResetAlarmVolumeNeeded = false;
        }
    }

    private boolean isPlayWarningSoundEnabled() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.EMERGENCY_GESTURE_SOUND_ENABLED,
                EmergencyActionUtils.isDefaultEmergencyGestureSoundEnabled(mContext) ? 1 : 0) != 0;
    }
}
