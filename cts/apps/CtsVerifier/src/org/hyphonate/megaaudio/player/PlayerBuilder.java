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

import android.util.Log;

import org.hyphonate.megaaudio.common.BuilderBase;

/**
 * Class to construct contrete Player objects.
 */
public class PlayerBuilder extends BuilderBase {
    @SuppressWarnings("unused")
    private static final String TAG = PlayerBuilder.class.getSimpleName();
    @SuppressWarnings("unused")
    private static final boolean LOG = false;

    /**
     * Provides audio data for this stream.
     */
    private AudioSourceProvider mSourceProvider;

    public PlayerBuilder() {
    }

    //
    // Player-Specific Attributes
    //
    /**
     * Specifies the player type
     * @param playerType Composed from API Types & API subtypes (defined in BuilderBase)
     * @return this PlayerBuilder (for cascaded calls)
     */
    public PlayerBuilder setPlayerType(int playerType) {
        mType = playerType;
        return this;
    }

    /**
     * Specifies the AudioSourceProvider which will allocate an AudioSource subclass object
     * to provide audio data for this stream.
     * @param sourceProvider Allocates the AudioSource to for provide audio
     * for the created stream.
     * @return this PlayerBuilder (for cascaded calls)
     */
    public PlayerBuilder setSourceProvider(AudioSourceProvider sourceProvider) {
        mSourceProvider = sourceProvider;
        return this;
    }

    /**
     * Allocates an initializes an API-specific player stream.
     * @return The allocated player or null in case of error or if a player type of TYPE_NONE
     * is specified.
     * @throws BadStateException if an invalid API has been specified.
     */
    public Player build() throws BadStateException {
        if (LOG) {
            Log.i(TAG, "build() mSourceProvider:" + mSourceProvider);
        }
        if (mSourceProvider == null) {
            throw new BadStateException();
        }

        Player player = null;
        int playerType = mType & TYPE_MASK;
        switch (playerType) {
            case TYPE_NONE:
                // NOP
                break;

            case TYPE_JAVA:
                player = new JavaPlayer(this, mSourceProvider);
                break;

            case TYPE_OBOE: {
                int playerSubType = mType & SUB_TYPE_MASK;
                player = new OboePlayer(this, mSourceProvider, playerSubType);
            }
            break;

            default:
                throw new BadStateException();
        }

        return player;
    }

    /**
     * Exception class used to signal a failure to allocate an API-specific stream in the build()
     * method.
     */
    public class BadStateException extends Throwable {
    }
}
