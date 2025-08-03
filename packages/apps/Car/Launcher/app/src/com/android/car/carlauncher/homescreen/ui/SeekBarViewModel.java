/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.car.carlauncher.homescreen.ui;

import com.android.car.carlauncher.homescreen.audio.MediaViewModel;

/**
 * Seek Bar View Model that holds info for seekbar thumb & progress tint color, progress
 * and callback that is attached to update model when seekbar thumb is used for setting
 * progress.
 */
public class SeekBarViewModel {

    private CharSequence mTimes;
    private boolean mIsSeekEnabled;
    private int mSeekBarColor;
    private int mProgress;
    private MediaViewModel.PlaybackCallback mPlaybackCallback;

    public SeekBarViewModel(CharSequence times, boolean isSeekEnabled,
            int seekBarColor, int progress, MediaViewModel.PlaybackCallback callback) {
        mTimes = times;
        mIsSeekEnabled = isSeekEnabled;
        mSeekBarColor = seekBarColor;
        mProgress = progress;
        mPlaybackCallback = callback;
    }

    public CharSequence getTimes() {
        return mTimes;
    }

    public boolean isSeekEnabled() {
        return mIsSeekEnabled;
    }

    public int getSeekBarColor() {
        return mSeekBarColor;
    }

    public int getProgress() {
        return mProgress;
    }

    public MediaViewModel.PlaybackCallback getPlaybackCallback() {
        return mPlaybackCallback;
    }
}
