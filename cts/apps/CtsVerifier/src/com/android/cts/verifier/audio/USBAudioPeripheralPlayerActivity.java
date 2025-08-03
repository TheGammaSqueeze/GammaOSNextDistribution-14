/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.cts.verifier.audio;

import android.os.Bundle;
import android.util.Log;

import com.android.cts.verifier.audio.audiolib.DisplayUtils;

// MegaAudio imports
import org.hyphonate.megaaudio.common.BuilderBase;
import org.hyphonate.megaaudio.common.StreamBase;
import org.hyphonate.megaaudio.player.AudioSourceProvider;
import org.hyphonate.megaaudio.player.JavaPlayer;
import org.hyphonate.megaaudio.player.PlayerBuilder;
import org.hyphonate.megaaudio.player.sources.SinAudioSourceProvider;

public abstract class USBAudioPeripheralPlayerActivity extends USBAudioPeripheralActivity {
    private static final String TAG = "USBAudioPeripheralPlayerActivity";

    // MegaPlayer
    static final int NUM_CHANNELS = 2;
    protected int mSampleRate;
    protected int mNumExchangeFrames;

    JavaPlayer mAudioPlayer;

    protected boolean mIsPlaying = false;

    protected boolean mOverridePlayFlag = true;

    public USBAudioPeripheralPlayerActivity(boolean requiresMandatePeripheral) {
        super(requiresMandatePeripheral); // Mandated peripheral is NOT required
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // MegaAudio Initialization
        StreamBase.setup(this);

        mSampleRate = StreamBase.getSystemSampleRate();
        mNumExchangeFrames = StreamBase.getNumBurstFrames(BuilderBase.TYPE_JAVA);

        DisplayUtils.setKeepScreenOn(this, true);
    }

    protected void setupPlayer() {
        //
        // Allocate the source provider for the sort of signal we want to play
        //
        AudioSourceProvider sourceProvider = new SinAudioSourceProvider();
        try {
            PlayerBuilder builder = new PlayerBuilder();
            builder.setPlayerType(PlayerBuilder.TYPE_JAVA)
                .setSourceProvider(sourceProvider)
                .setChannelCount(NUM_CHANNELS)
                .setSampleRate(mSampleRate)
                .setNumExchangeFrames(mNumExchangeFrames);
            mAudioPlayer = (JavaPlayer) builder.build();
        } catch (PlayerBuilder.BadStateException ex) {
            Log.e(TAG, "Failed MegaPlayer build.");
        }
    }

    // Returns whether the stream started correctly.
    protected boolean startPlay() {
        boolean result = false;
        if (mOutputDevInfo != null && !mIsPlaying) {
            result = (mAudioPlayer.startStream() == StreamBase.OK);
        }
        if (result) {
            mIsPlaying = true;
        }
        return result;
    }

    // Returns whether the stream stopped correctly.
    protected boolean stopPlay() {
        boolean result = false;
        if (mIsPlaying) {
            result = (mAudioPlayer.stopStream() == StreamBase.OK);
        }
        if (result) {
            mIsPlaying = false;
        }
        return result;
    }

    public boolean isPlaying() {
        return mIsPlaying;
    }

    @Override
    protected void onPause() {
        super.onPause();

        stopPlay();
    }
}
