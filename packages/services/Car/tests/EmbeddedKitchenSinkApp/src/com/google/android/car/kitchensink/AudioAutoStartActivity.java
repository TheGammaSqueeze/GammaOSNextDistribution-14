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

package com.google.android.car.kitchensink;

import static android.car.Car.APP_FOCUS_SERVICE;
import static android.car.Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER;
import static android.car.Car.createCar;
import static android.car.CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION;
import static android.car.CarAppFocusManager.OnAppFocusChangedListener;
import static android.car.media.CarAudioManager.AUDIO_FEATURE_DYNAMIC_ROUTING;
import static android.media.AudioManager.AUDIOFOCUS_GAIN;
import static android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT;
import static android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE;
import static android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK;
import static android.media.AudioManager.AUDIOFOCUS_LOSS;
import static android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
import static android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_DELAYED;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_FAILED;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
import static android.util.Log.DEBUG;

import android.app.Activity;
import android.car.Car;
import android.car.CarAppFocusManager;
import android.car.media.CarAudioManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

import java.io.IOException;

import javax.annotation.concurrent.GuardedBy;

public final class AudioAutoStartActivity extends Activity {

    private static final String TAG = "CAR.AUDIO.KS.AUTO_START";
    private static final AudioAttributes MUSIC_AUDIO_ATTRIBUTES = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build();

    private Handler mHandler;
    private Car mCar;
    private CarAppFocusManager mAppFocusManager;
    private CarAudioManager mCarAudioManager;
    private AudioManager mAudioManager;

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private AudioFocusRequest mFocusRequest;
    @GuardedBy("mLock")
    private OnAudioFocusChangeListener mMediaFocusListener;
    @GuardedBy("mLock")
    private MediaPlayer mPlayer;
    private TextView mPlayerStatusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        connectCar();
        setContentView(R.layout.audio_auto_start_activity);

        mAudioManager = getSystemService(AudioManager.class);

        TextView currentZoneIdTextView = findViewById(R.id.activity_current_zone);
        setActivityCurrentZoneId(currentZoneIdTextView);

        mPlayerStatusText = findViewById(R.id.player_status);

        requestAudioFocus();
    }

    private void connectCar() {
        mHandler = new Handler(Looper.getMainLooper(), null, true);
        mCar = createCar(this, null,
                CAR_WAIT_TIMEOUT_WAIT_FOREVER, (car, ready) -> {
                    if (!ready) {
                        return;
                    }
                    mAppFocusManager =
                            (CarAppFocusManager) car.getCarManager(APP_FOCUS_SERVICE);
                    mAppFocusManager.addFocusListener(
                            (OnAppFocusChangedListener) (appType, active) -> {

                            },
                            APP_FOCUS_TYPE_NAVIGATION);

                    mCarAudioManager = (CarAudioManager) car.getCarManager(AUDIO_SERVICE);
                });
    }

    private void setActivityCurrentZoneId(TextView currentZoneIdTextView) {
        if (mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_DYNAMIC_ROUTING)) {
            try {
                ApplicationInfo info = getPackageManager().getApplicationInfo(
                        getPackageName(), 0);
                int audioZoneId = mCarAudioManager.getZoneIdForUid(info.uid);
                currentZoneIdTextView.setText(Integer.toString(audioZoneId));
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "setActivityCurrentZoneId Failed to find package name "
                        + getPackageName(), e);
            }
        }
    }

    private void requestAudioFocus() {
        int delayedFocusRequestResults;
        synchronized (mLock) {
            if (mFocusRequest != null) {
                return;
            }
            mMediaFocusListener = new AutoStartFocusListener();
            mFocusRequest = new AudioFocusRequest
                    .Builder(AUDIOFOCUS_GAIN)
                    .setAudioAttributes(MUSIC_AUDIO_ATTRIBUTES)
                    .setOnAudioFocusChangeListener(mMediaFocusListener)
                    .setForceDucking(false)
                    .setWillPauseWhenDucked(false)
                    .setAcceptsDelayedFocusGain(true)
                    .build();
            delayedFocusRequestResults = mAudioManager.requestAudioFocus(mFocusRequest);
        }

        switch (delayedFocusRequestResults) {
            case AUDIOFOCUS_REQUEST_GRANTED:
                if (Log.isLoggable(TAG, DEBUG)) {
                    Log.d(TAG, "Audio Auto Start Player granted");
                }
                mHandler.post(() -> startMediaPlayer());
                break;
            case AUDIOFOCUS_REQUEST_DELAYED:
                if (Log.isLoggable(TAG, DEBUG)) {
                    Log.d(TAG, "Media With Delayed Focus delayed");
                }
                mHandler.post(() -> delayMediaPlayer());
                break;
            case AUDIOFOCUS_REQUEST_FAILED:
            default:
                if (Log.isLoggable(TAG, DEBUG)) {
                    Log.d(TAG, "Audio Auto Start Player rejected");
                }
                mHandler.post(()  -> stopMediaPlayer());
                synchronized (mLock) {
                    mMediaFocusListener = null;
                    mFocusRequest = null;
                }
        }
    }

    private MediaPlayer createPlayer() throws IOException {
        MediaPlayer mediaPlayer = new MediaPlayer();
        AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.testmp3_2);
        mediaPlayer.setAudioAttributes(MUSIC_AUDIO_ATTRIBUTES);
        mediaPlayer.setDataSource(afd);
        mediaPlayer.setLooping(true);
        mediaPlayer.setVolume(1.0f, 1.0f);
        mediaPlayer.prepare();
        return mediaPlayer;
    }

    private void stopMediaPlayer() {
        synchronized (mLock) {
            mPlayerStatusText.setText(R.string.player_stopped);
            if (mPlayer == null) {
                Log.i(TAG, "Can not stop non existent player");
                return;
            }
            mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }
    }

    private void pauseMediaPlayer() {
        synchronized (mLock) {
            mPlayerStatusText.setText(R.string.player_paused);
            if (mPlayer == null) {
                Log.i(TAG, "Can not pause non existent player");
                return;
            }
            mPlayer.pause();
        }
    }

    private void startMediaPlayer() {
        synchronized (mLock) {
            if (mPlayer == null) {
                try {
                    mPlayer = createPlayer();
                } catch (IOException e) {
                    Log.e(TAG, "Can not media start player", e);
                    return;
                }
            }
            mPlayer.start();
            mPlayerStatusText.setText(R.string.player_started);
        }
    }

    private void delayMediaPlayer() {
        synchronized (mLock) {
            mPlayerStatusText.setText(R.string.player_delayed);
        }
    }

    private class AutoStartFocusListener implements OnAudioFocusChangeListener {

        @Override
        public void onAudioFocusChange(int focusChange) {
            if (Log.isLoggable(TAG, DEBUG)) {
                Log.d(TAG, "Audio Auto Start Player Focus change:" + focusChange);
            }
            switch (focusChange) {
                case AUDIOFOCUS_GAIN:
                case AUDIOFOCUS_GAIN_TRANSIENT:
                case AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:
                case AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                    mHandler.post(() -> startMediaPlayer());
                    return;
                case AUDIOFOCUS_LOSS_TRANSIENT:
                case AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    mHandler.post(() -> pauseMediaPlayer());
                    return;
                case AUDIOFOCUS_LOSS:
                default:
                    mHandler.post(() -> stopMediaPlayer());
                    synchronized (mLock) {
                        mFocusRequest = null;
                        mMediaFocusListener = null;
                    }
                    return;
            }
        }
    }
}
