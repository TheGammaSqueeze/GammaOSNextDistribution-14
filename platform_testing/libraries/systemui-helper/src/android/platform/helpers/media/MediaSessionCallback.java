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

package android.platform.helpers.media;

import android.media.session.MediaSession;
import android.media.session.PlaybackState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class MediaSessionCallback extends MediaSession.Callback {
    private final List<Consumer<Integer>> mOnMediaStateChangedListeners;
    private final MockMediaPlayer mPlayer;

    public MediaSessionCallback(MockMediaPlayer player) {
        mPlayer = player;
        mOnMediaStateChangedListeners = new ArrayList<>();
    }

    @Override
    public void onPlay() {
        mPlayer.start();
        setCurrentMediaState(PlaybackState.STATE_PLAYING);
    }

    @Override
    public void onPause() {
        mPlayer.pause();
        setCurrentMediaState(PlaybackState.STATE_PAUSED);
    }

    @Override
    public void onSkipToNext() {
        mPlayer.pause();
        setCurrentMediaState(PlaybackState.STATE_SKIPPING_TO_NEXT);
    }

    @Override
    public void onSkipToPrevious() {
        mPlayer.pause();
        setCurrentMediaState(PlaybackState.STATE_SKIPPING_TO_PREVIOUS);
    }

    public void addOnMediaStateChangedListener(Consumer<Integer> listener) {
        mOnMediaStateChangedListeners.add(listener);
    }

    private void setCurrentMediaState(int state) {
        for (Consumer<Integer> listener : mOnMediaStateChangedListeners) {
            listener.accept(state);
        }
    }
}
