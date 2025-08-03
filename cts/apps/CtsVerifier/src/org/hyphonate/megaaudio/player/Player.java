/*
 * Copyright 2020 The Android Open Source Project
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
package org.hyphonate.megaaudio.player;

import android.media.AudioFormat;
import android.media.AudioTimestamp;
import android.media.AudioTrack;

import org.hyphonate.megaaudio.common.StreamBase;

import java.util.ArrayList;

/**
 * An abstract class defining the common operations and attributes for all
 * player (concrete) sub-classes.
 */
public abstract class Player extends StreamBase {
    @SuppressWarnings("unused")
    private static final String TAG = Player.class.getSimpleName();
    @SuppressWarnings("unused")
    private static final boolean LOG = false;

    private ArrayList<BufferCallback> mCallbacks = new ArrayList<BufferCallback>();

    public Player(AudioSourceProvider sourceProvider) {
        mSourceProvider = sourceProvider;
    }

    /*
     * Audio Source
     */
    /**
     * The AudioSource object providing audio data to play.
     */
    protected AudioSource mAudioSource;

    protected AudioSourceProvider mSourceProvider;

    /** <code>true</code> if currently playing audio data */
    protected boolean mPlaying;

    /**
     * @return The AudioSource object providing audio for this Player.
     */
    public AudioSource getAudioSource() {
        return mAudioSource;
    }

    //
    // Status
    //
    public boolean isPlaying() {
        return mPlaying;
    }

    /*
     * Channel utils
     */
    // TODO Consider moving these to a "Utility" library.
    /**
     * @param channelCount  The number of channels for which to generate an output position mask.
     * @return An output channel-position mask corresponding to the supplied number of channels.
     */
    public static int channelCountToChannelMask(int channelCount) {
        switch (channelCount) {
            case 1:
                return AudioFormat.CHANNEL_OUT_MONO;

            case 2:
                return AudioFormat.CHANNEL_OUT_STEREO;

            case 3:
                return AudioFormat.CHANNEL_OUT_STEREO | AudioFormat.CHANNEL_OUT_FRONT_CENTER;

            case 4:
                return AudioFormat.CHANNEL_OUT_QUAD;

            case 5: // 5.0
                return AudioFormat.CHANNEL_OUT_QUAD | AudioFormat.CHANNEL_OUT_FRONT_CENTER;

            case 6: // 5.1
                return AudioFormat.CHANNEL_OUT_5POINT1;

            case 7: // 6.1
                return AudioFormat.CHANNEL_OUT_5POINT1 | AudioFormat.CHANNEL_OUT_BACK_CENTER;

            case 8:
                return AudioFormat.CHANNEL_OUT_7POINT1;

            default:
                return AudioTrack.ERROR_BAD_VALUE;
        }
    }

    //
    // TimeStamp
    //
    /**
     * Gets a timestamp from the audio stream
     * @param timestamp
     * @return
     */
    public abstract boolean getTimestamp(AudioTimestamp timestamp);

    //
    // BufferCallback Stuff
    //
    /**
     * Defines an interface for buffer callback objects
     */
    public interface BufferCallback {
        /**
         * Called when player executes a pull() on the associated AudioSource
         */
        void onPull();
    }

    /**
     * Adds a callback object
     * @param callback
     */
    public void addBufferCallback(BufferCallback callback) {
        mCallbacks.add(callback);
    }

    /**
     * Removes a previously added callback object
     * @param callback
     */
    public void removeBufferCallback(BufferCallback callback) {
        mCallbacks.remove(callback);
    }

    /**
     * Iterates through callback objects and calls their onPull() method.
     */
    public void onPull() {
        for (BufferCallback callback : mCallbacks) {
            callback.onPull();
        }
    }
}
