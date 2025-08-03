/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.car.radio.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.android.car.radio.R;
import com.android.car.radio.util.Log;

/**
 * An {@link ImageView} that renders a play/pause button like a floating action button.
 */
public class PlayPauseButton extends ImageView {
    private static final String TAG = "BcRadioApp.PlayPauseBtn";

    private static final int[] STATE_PLAYING = {R.attr.state_playing};
    private static final int[] STATE_PAUSED = {R.attr.state_paused};
    private static final int[] STATE_DISABLED = {R.attr.state_disabled};

    @Nullable private Callback mCallback;

    @PlaybackStateCompat.State
    private int mPlaybackState = PlaybackStateCompat.STATE_NONE;

    /**
     * Callback for toggle event.
     */
    public interface Callback {
        /**
         * Called when the button was clicked.
         *
         * @param newPlayState New playback state to switch to.
         */
        void onSwitchTo(@PlaybackStateCompat.State int newPlayState);
    }

    public PlayPauseButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOnClickListener(this::onClick);
    }

    public void setCallback(@Nullable Callback callback) {
        mCallback = callback;
    }

    /**
     * Set the current play state of the button.
     *
     * @param playState Current playback state
     */
    public void setPlayState(@PlaybackStateCompat.State int playState) {
        Log.v(TAG, "New playback state: " + playState);
        mPlaybackState = playState;
    }

    private void onClick(View v) {
        Callback callback = mCallback;
        if (callback == null) return;

        int switchTo;
        switch(mPlaybackState) {
            case PlaybackStateCompat.STATE_PLAYING:
            case PlaybackStateCompat.STATE_CONNECTING:
            case PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS:
            case PlaybackStateCompat.STATE_SKIPPING_TO_NEXT:
                switchTo = PlaybackStateCompat.STATE_PAUSED;
                break;
            case PlaybackStateCompat.STATE_NONE:
            case PlaybackStateCompat.STATE_PAUSED:
            case PlaybackStateCompat.STATE_STOPPED:
            case PlaybackStateCompat.STATE_ERROR:
                switchTo = PlaybackStateCompat.STATE_PLAYING;
                break;
            default:
                Log.e(TAG, "Unsupported PlaybackState: " + mPlaybackState);
                return;
        }

        Log.v(TAG, "Requesting switch to playback state: " + switchTo);
        callback.onSwitchTo(switchTo);
    }

    @Override
    public int[] onCreateDrawableState(int extraSpace) {
        // + 1 so we can potentially add our custom PlayState
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);

        switch(mPlaybackState) {
            case PlaybackStateCompat.STATE_PLAYING:
                mergeDrawableStates(drawableState, STATE_PLAYING);
                break;
            case PlaybackStateCompat.STATE_STOPPED:
            case PlaybackStateCompat.STATE_PAUSED:
            case PlaybackStateCompat.STATE_CONNECTING:
            case PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS:
            case PlaybackStateCompat.STATE_SKIPPING_TO_NEXT:
                mergeDrawableStates(drawableState, STATE_PAUSED);
                break;
            case PlaybackStateCompat.STATE_NONE:
            case PlaybackStateCompat.STATE_ERROR:
                mergeDrawableStates(drawableState, STATE_DISABLED);
                break;
            default:
                Log.e(TAG, "Unsupported PlaybackState: " + mPlaybackState);
        }

        Drawable background = getBackground();
        if (background != null) background.setState(drawableState);

        return drawableState;
    }
}
