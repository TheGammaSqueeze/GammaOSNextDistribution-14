/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.google.android.car.kitchensink.audio;

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

import static com.google.android.car.kitchensink.audio.AudioTestFragment.getAudioLogTag;

import android.annotation.IntDef;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.AudioRouting.OnRoutingChangedListener;
import android.media.MediaPlayer;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.internal.annotations.GuardedBy;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Class for playing music.
 *
 * This is not thread safe and all public calls should be made from main thread.
 *
 * MP3 used is from
 * http://freemusicarchive
 * .org/music/John_Harrison_with_the_Wichita_State_University_Chamber_Players
 * /The_Four_Seasons_Vivaldi/05_-_Vivaldi_Summer_mvt_2_Adagio_-_John_Harrison_violin
 * from John Harrison with the Wichita State University Chamber Players
 * Copyright under Create Commons license.
 */
public class AudioPlayer {

    private static final String TAG = getAudioLogTag(AudioPlayer.class);

    @IntDef(flag = false, prefix = "PLAYER_STATE", value = {
            PLAYER_STATE_COMPLETED,
            PLAYER_STATE_STARTED,
            PLAYER_STATE_STOPPED,
            PLAYER_STATE_DELAYED,
            PLAYER_STATE_PAUSED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AudioPlayerState {
    }

    public static final int PLAYER_STATE_COMPLETED = 0;
    public static final int PLAYER_STATE_STARTED = 1;
    public static final int PLAYER_STATE_STOPPED = 2;
    public static final int PLAYER_STATE_DELAYED = 3;
    public static final int PLAYER_STATE_PAUSED = 4;

    public interface PlayStateListener {
        void onStateChange(@AudioPlayerState int state);
    }

    private final AudioManager.OnAudioFocusChangeListener mFocusListener =
            new AudioManager.OnAudioFocusChangeListener() {

                @Override
                public void onAudioFocusChange(int focusChange) {
                    if (mPlayer == null) {
                        Log.e(TAG, "mPlayer is null");
                        return;
                    }
                    switch (focusChange) {
                        case AUDIOFOCUS_GAIN:
                        case AUDIOFOCUS_GAIN_TRANSIENT:
                        case AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:
                        case AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                            Log.i(TAG, "Audio focus change AUDIOFOCUS_GAIN for usage "
                                    + mAttrib.getUsage());
                            mPlayer.setVolume(1.0f, 1.0f);
                            if (mRepeat && isPlaying()) {
                                // Resume
                                Log.i(TAG, "resuming player");
                                mPlayer.start();
                                sendPlayerStateChanged(PLAYER_STATE_STARTED);
                            }
                            return;
                        case AUDIOFOCUS_LOSS_TRANSIENT:
                            Log.i(TAG, "Audio focus change AUDIOFOCUS_LOSS_TRANSIENT for usage "
                                    + mAttrib.getUsage());
                            if (mRepeat && isPlaying()) {
                                Log.i(TAG, "pausing repeating player");
                                mPlayer.pause();
                                sendPlayerStateChanged(PLAYER_STATE_PAUSED);
                            } else {
                                Log.i(TAG, "stopping one shot player");
                                stop();
                            }
                            return;
                        case AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                            // While we used to setVolume on the player to 20%, we don't do this
                            // anymore
                            // because we expect the car's audio hal do handle ducking as it sees
                            // fit.
                            Log.i(TAG, "Audio focus change AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK "
                                    + "-> do nothing");
                            return;
                        case AUDIOFOCUS_LOSS:
                        default:
                            if (isPlaying()) {
                                Log.i(TAG, "stopping player");
                                stop();
                            }
                    }
                }
            };

    private final Object mLock = new Object();
    private AudioManager mAudioManager;
    private MediaPlayer mPlayer;

    private final Context mContext;
    private final int mResourceId;
    private final AudioAttributes mAttrib;
    private final AudioDeviceInfo mPreferredDeviceInfo;

    private final AtomicBoolean mPlaying = new AtomicBoolean(false);

    @GuardedBy("mLock")
    @Nullable
    private OnRoutingChangedListener mAudioRoutingListener;

    private volatile boolean mHandleFocus;
    private volatile boolean mRepeat;
    private PlayStateListener mListener;
    private AudioFocusRequest mFocusRequest;


    public AudioPlayer(Context context, int resourceId, AudioAttributes attrib) {
        this(context, resourceId, attrib, /* preferredDeviceInfo= */ null,
                /* routingListener= */ null);
    }

    public AudioPlayer(Context context, int resourceId, AudioAttributes attrib,
            @Nullable AudioDeviceInfo preferredDeviceInfo,
            @Nullable OnRoutingChangedListener routingListener) {
        mContext = context;
        mResourceId = resourceId;
        mAttrib = attrib;
        mPreferredDeviceInfo = preferredDeviceInfo;
        mAudioRoutingListener = routingListener;
    }

    public int getUsage() {
        return mAttrib.getSystemUsage();
    }

    /**
     * Starts player
     *
     * @param handleFocus  {@code true} to handle focus
     * @param repeat       {@code true} to repeat track
     * @param focusRequest type of focus to request
     */
    public void start(boolean handleFocus, boolean repeat, int focusRequest) {
        mHandleFocus = handleFocus;
        mRepeat = repeat;
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        int ret = AUDIOFOCUS_REQUEST_GRANTED;
        if (mHandleFocus) {
            // NOTE:  We are CONSCIOUSLY asking for focus again even if already playing in order
            // exercise the framework's focus logic when faced with a (sloppy) application which
            // might do this.
            Log.i(TAG, "Asking for focus for usage " + mAttrib.getUsage());
            mFocusRequest = new AudioFocusRequest
                    .Builder(focusRequest)
                    .setAudioAttributes(mAttrib)
                    .setOnAudioFocusChangeListener(mFocusListener)
                    .setForceDucking(false)
                    .setWillPauseWhenDucked(false)
                    .setAcceptsDelayedFocusGain(false)
                    .build();
            ret = mAudioManager.requestAudioFocus(mFocusRequest);
        }
        switch (ret) {
            case AUDIOFOCUS_REQUEST_GRANTED:
                Log.i(TAG, "MediaPlayer got focus for usage " + mAttrib.getUsage());
                doStart();
                break;
            case AUDIOFOCUS_REQUEST_DELAYED:
                Log.i(TAG, "MediaPlayer delayed focus for usage " + mAttrib.getUsage());
                sendPlayerStateChanged(PLAYER_STATE_DELAYED);
                break;
            case AUDIOFOCUS_REQUEST_FAILED:
            default:
                Log.i(TAG, "MediaPlayer denied focus for usage " + mAttrib.getUsage());
        }
    }

    public void start(boolean handleFocus, boolean repeat, int focusRequest,
            PlayStateListener listener) {
        mListener = listener;
        start(handleFocus, repeat, focusRequest);
    }

    /**
     * Sets the audio routing listener, runs in main handler
     */
    void setAudioRoutingListener(
            @Nullable OnRoutingChangedListener audioRoutingListener) {
        synchronized (mLock) {
            mAudioRoutingListener = audioRoutingListener;
        }
    }

    private void doStart() {
        if (mPlaying.getAndSet(true)) {
            Log.i(TAG, "already playing");
            return;
        }
        Log.i(TAG, "doStart audio");
        mPlayer = new MediaPlayer();
        mPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {

            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.e(TAG, "audio error what " + what + " extra " + extra);
                mPlaying.set(false);
                if (!mRepeat && mHandleFocus) {
                    mPlayer.stop();
                    mPlayer.release();
                    mPlayer = null;
                    mAudioManager.abandonAudioFocus(mFocusListener);
                    sendPlayerStateChanged(PLAYER_STATE_STOPPED);
                }
                return false;
            }

        });
        mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                Log.i(TAG, "AudioPlayer onCompletion");
                mPlaying.set(false);
                if (!mRepeat && mHandleFocus) {
                    mPlayer.stop();
                    mPlayer = null;
                    mAudioManager.abandonAudioFocus(mFocusListener);
                    sendPlayerStateChanged(PLAYER_STATE_COMPLETED);
                }
            }
        });
        mPlayer.setAudioAttributes(mAttrib);
        mPlayer.setLooping(mRepeat);
        mPlayer.setVolume(1.0f, 1.0f);
        try {
            AssetFileDescriptor afd =
                    mContext.getResources().openRawResourceFd(mResourceId);
            if (afd == null) {
                throw new RuntimeException("resource not found");
            }
            mPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(),
                    afd.getLength());
            afd.close();
            mPlayer.prepare();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Search for preferred device
        if (mPreferredDeviceInfo != null) {
            mPlayer.setPreferredDevice(mPreferredDeviceInfo);
            Log.d(TAG, "doStart preferred device address: " + mPreferredDeviceInfo.getAddress());
        }

        mPlayer.start();
        OnRoutingChangedListener routingChangedListener;
        synchronized (mLock) {
            routingChangedListener = mAudioRoutingListener;
        }
        if (routingChangedListener != null) {
            Log.i(TAG, "doStart addOnRoutingChangedListener " + routingChangedListener);
            mPlayer.addOnRoutingChangedListener(routingChangedListener, Handler.getMain());
        }
        sendPlayerStateChanged(PLAYER_STATE_STARTED);
    }

    public void stop() {
        if (!mPlaying.getAndSet(false)) {
            Log.i(TAG, "already stopped");
            if (mPlayer == null) {
                return;
            }
            mPlayer.release();
            mPlayer = null;
            return;
        }
        Log.i(TAG, "stop");

        mPlayer.stop();
        mPlayer.release();
        mPlayer = null;
        sendPlayerStateChanged(PLAYER_STATE_STOPPED);

        if (mHandleFocus) {
            mAudioManager.abandonAudioFocusRequest(mFocusRequest);
        }
    }

    public boolean isPlaying() {
        return mPlaying.get();
    }

    private void sendPlayerStateChanged(@AudioPlayerState int state) {
        if (mListener != null) {
            mListener.onStateChange(state);
        }
    }
}
