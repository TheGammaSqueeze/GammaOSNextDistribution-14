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

import android.media.MediaMetadata;

import androidx.annotation.Nullable;

import java.time.Duration;
import java.util.Timer;
import java.util.TimerTask;

final class MockMediaPlayer {

    private final static int PERIOD = 1000; // milliseconds

    private long mCurrentPosition; // current position in milliseconds.
    private Timer mTimer;
    @Nullable
    private MediaMetadata mCurrentSource;
    private Runnable mOnCompletionListener;

    public MockMediaPlayer() {
        mCurrentPosition = 0;
    }

    public void start() {
        mTimer = new Timer();
        mTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                mCurrentPosition += PERIOD;
                if (mCurrentPosition >= getDuration()) {
                    onCompletion();
                }
            }
        }, 0, PERIOD);
    }

    private void onCompletion() {
        reset();
        if (mOnCompletionListener != null) {
            mOnCompletionListener.run();
        }
    }

    public void setOnCompletionListener(@Nullable Runnable listener) {
        mOnCompletionListener = listener;
    }

    public void reset() {
        pause();
        mCurrentPosition = 0;
    }

    public void pause() {
        if (mTimer != null) {
            mTimer.cancel();
        }
        mTimer = null;
    }

    public void stop() {
        reset();
    }

    public long getCurrentPosition() {
        return mCurrentPosition;
    }

    public void setDataSource(MediaMetadata source) {
        mCurrentSource = source;
    }

    private long getDuration() {
        return mCurrentSource.getLong(MediaMetadata.METADATA_KEY_DURATION);
    }
}
