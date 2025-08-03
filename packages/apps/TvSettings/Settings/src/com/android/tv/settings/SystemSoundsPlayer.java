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

package com.android.tv.settings;

import android.annotation.IntDef;
import android.annotation.MainThread;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ProcessLifecycleOwner;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * System sounds player used to play system sounds like select / deselect.
 * To keep the sound effects in memory only when the app is active, this class observes the
 * {@link androidx.lifecycle.Lifecycle} of the {@link androidx.lifecycle.ProcessLifecycleOwner} and
 * loads the sound effect when ON_START occurs and unloads them when ON_STOP occurs.
 * To achieve a consistent volume among all system sounds the {@link SoundPool} used here is
 * initialized in the same way as the SoundPool for framework system sounds in SoundEffectsHelper
 * and the volume attenuation is calculated in the same way as it's done by SoundEffectsHelper.
 */
public class SystemSoundsPlayer implements LifecycleObserver {
    public static final int FX_SELECT = 0;
    public static final int FX_DESELECT = 1;
    /** @hide */
    @IntDef(prefix = "FX_", value = {FX_SELECT, FX_DESELECT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface SystemSoundEffect {}
    private static final String TAG = SystemSoundsPlayer.class.getSimpleName();
    private static final int NUM_SOUNDPOOL_STREAMS = 2;
    private static final int MSG_PRELOAD_SOUNDS = 0;
    private static final int MSG_UNLOAD_SOUNDS = 1;
    private static final int MSG_PLAY_SOUND = 2;
    private static final int[] FX_RESOURCES = new int[]{
            R.raw.Select,
            R.raw.Deselect
    };
    private final Handler mHandler;
    private final Context mContext;
    private final Map<Integer, Integer> mEffectIdToSoundPoolId = new ConcurrentHashMap<>();
    private final Set<Integer> mLoadedSoundPoolIds = new HashSet<>();
    private final float mVolumeAttenuation;
    private SoundPool mSoundPool;

    public SystemSoundsPlayer(Context context) {
        mContext = context.getApplicationContext();
        float attenuationDb = mContext.getResources().getInteger(
                mContext.getResources().getIdentifier("config_soundEffectVolumeDb",
                        "integer", "android"));
        // This is the same value that is used for framework system sounds as set by
        // com.android.server.audio.SoundEffectsHelper#onPlaySoundEffect()
        mVolumeAttenuation = (float) Math.pow(10, attenuationDb / 20);
        HandlerThread handlerThread = new HandlerThread(TAG + ".handler");
        handlerThread.start();
        mHandler = new SoundPoolHandler(handlerThread.getLooper());
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
    }

    /**
     * Plays a sound effect
     *
     * @param effect The effect id.
     */
    public void playSoundEffect(@SystemSoundEffect int effect) {
        if (mSoundPool == null || !querySoundEffectsEnabled()) {
            return;
        }
        switch (effect) {
            case FX_SELECT:
            case FX_DESELECT:
                // any other "case X:" in the future
                int soundPoolSoundId = getSoundPoolIdForEffect(effect);
                if (soundPoolSoundId >= 0) {
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_PLAY_SOUND, soundPoolSoundId, 0,
                            mSoundPool));
                } else {
                    Log.w(TAG, "playSoundEffect() called but SoundPool is not ready");
                }
                break;
            default:
                Log.w(TAG, "Invalid sound id: " + effect);
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    private void prepareSoundPool() {
        if (mSoundPool == null) {
            mSoundPool = new SoundPool.Builder()
                    .setMaxStreams(NUM_SOUNDPOOL_STREAMS)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .build();
            mSoundPool.setOnLoadCompleteListener(new SoundPoolLoadCompleteListener());
            mHandler.sendMessage(mHandler.obtainMessage(MSG_PRELOAD_SOUNDS, mSoundPool));
        } else {
            throw new IllegalStateException("prepareSoundPool() was called but SoundPool not null");
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    private void releaseSoundPool() {
        if (mSoundPool == null) {
            throw new IllegalStateException("releaseSoundPool() was called but SoundPool is null");
        }
        mHandler.sendMessage(mHandler.obtainMessage(MSG_UNLOAD_SOUNDS, mSoundPool));
        mSoundPool.setOnLoadCompleteListener(null);
        mSoundPool = null;
        mLoadedSoundPoolIds.clear();
    }

    /**
     * Settings has an in memory cache, so this is fast.
     */
    private boolean querySoundEffectsEnabled() {
        return Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.SOUND_EFFECTS_ENABLED, 0, mContext.getUserId()) != 0;
    }

    /**
     * @param effect Any of the defined effect ids.
     * @return Returns the SoundPool sound id if the sound has been loaded, -1 otherwise.
     */
    private int getSoundPoolIdForEffect(@SystemSoundEffect int effect) {
        Integer soundPoolSoundId = mEffectIdToSoundPoolId.getOrDefault(effect, -1);
        if (mLoadedSoundPoolIds.contains(soundPoolSoundId)) {
            return soundPoolSoundId;
        } else {
            return -1;
        }
    }

    private class SoundPoolHandler extends Handler {
        SoundPoolHandler(@NonNull Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            SoundPool soundPool = (SoundPool) msg.obj;
            switch (msg.what) {
                case MSG_PRELOAD_SOUNDS:
                    for (int effectId = 0; effectId < FX_RESOURCES.length; effectId++) {
                        int soundPoolSoundId = soundPool.load(mContext,
                                FX_RESOURCES[effectId], /* priority= */ 1);
                        mEffectIdToSoundPoolId.put(effectId, soundPoolSoundId);
                    }
                    break;
                case MSG_UNLOAD_SOUNDS:
                    mEffectIdToSoundPoolId.clear();
                    soundPool.release();
                    break;
                case MSG_PLAY_SOUND:
                    int soundId = msg.arg1;
                    soundPool.play(soundId, mVolumeAttenuation, mVolumeAttenuation, /* priority= */
                            0, /* loop= */0, /* rate= */ 1.0f);
                    break;
            }
        }
    }

    private class SoundPoolLoadCompleteListener implements
            SoundPool.OnLoadCompleteListener {
        @MainThread
        @Override
        public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
            if (mSoundPool != soundPool) {
                // in case the soundPool has already been released we do not care
                return;
            }
            if (status == 0) {
                // sound loaded successfully
                mLoadedSoundPoolIds.add(sampleId);
            } else {
                // error while loading sound, remove it from map to mark it as unloaded
                Integer effectId = 0;
                for (; effectId < mEffectIdToSoundPoolId.size(); effectId++) {
                    Integer soundPoolId = mEffectIdToSoundPoolId.get(effectId);
                    if (soundPoolId != null && soundPoolId == sampleId) {
                        break;
                    }
                }
                mEffectIdToSoundPoolId.remove(effectId);
            }
            int remainingToLoad = mEffectIdToSoundPoolId.size() - mLoadedSoundPoolIds.size();
            if (remainingToLoad == 0) {
                soundPool.setOnLoadCompleteListener(null);
            }
        }
    }
}
