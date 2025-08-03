/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.emergency.widgets.countdown;

import android.content.Context;
import android.graphics.PixelFormat;
import android.icu.text.MessageFormat;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.UiThread;

import com.android.emergency.action.R;

import com.google.common.util.concurrent.Uninterruptibles;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The view includes an animation which circle around the view when counting down, and a text view
 * to show the seconds left.
 */
public class CountDownAnimationView extends SurfaceView implements SurfaceHolder.Callback {

    private LoopingAnimationThread mLoopingAnimationThread;
    private boolean mIsSurfaceCreated;
    private boolean mIsStarted;
    private Duration mCountDownDuration;
    private boolean mShowCountDown;

    public CountDownAnimationView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // This is required to draw on top of existing graphics.
        setZOrderOnTop(true);
        // This is required to ensure background is transparent before draw instead of a blank
        // black canvas.
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        getHolder().addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // When surface is created, that's when the looping animation is created. If the surface
        // is not created, the looping animation does not exist. This assumption is made throughout
        // this class.
        mIsSurfaceCreated = true;
        mLoopingAnimationThread =
                new LoopingAnimationThread(
                        holder,
                        getContext());
        if (mIsStarted) {
            startInternal();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mLoopingAnimationThread.updateSize(width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stop();
        mIsSurfaceCreated = false;
        // We have to tell thread to shut down & wait for it to finish, or else
        // it might touch the Surface.
        Uninterruptibles.joinUninterruptibly(mLoopingAnimationThread);
        mLoopingAnimationThread = null;
    }

    /**
     * Start the animation.
     *
     * <p>This is not thread safe, and is only meant to be called from the main thread.
     */
    @UiThread
    public void start(Duration countDownDuration) {
        if (mIsStarted) {
            return;
        }

        mIsStarted = true;
        this.mCountDownDuration = countDownDuration;
        // The animation does not start until the surface has been created, see
        // {@link #surfaceCreated(SurfaceHolder)} for details.
        if (mIsSurfaceCreated) {
            startInternal();
        }
    }

    private void startInternal() {
        if (mIsSurfaceCreated && mIsStarted) {
            mLoopingAnimationThread.setCountDownLeft(mCountDownDuration);
            mLoopingAnimationThread.start();
            if (mShowCountDown) {
                mLoopingAnimationThread.showCountDown();
            }
        }
    }

    /** Starts rendering count down text that is set with {@link #setCountDownLeft}. */
    public void showCountDown() {
        mShowCountDown = true;
        if (mIsSurfaceCreated) {
            mLoopingAnimationThread.showCountDown();
        }
    }

    /** Sets the count down to be rendered. */
    public void setCountDownLeft(Duration timeLeft) {
        if (mIsSurfaceCreated) {
            mLoopingAnimationThread.setCountDownLeft(timeLeft);

            MessageFormat msgFormat = new MessageFormat(
                    mContext.getString(R.string.countdown_text_content_description),
                    Locale.getDefault());
            Map<String, Object> msgArgs = new HashMap<>();
            msgArgs.put("seconds_left", timeLeft.getSeconds());
            setContentDescription(msgFormat.format(msgArgs));
        }
    }

    /** Stop the animation. This is only meant to be called from the main thread. */
    public void stop() {
        mIsStarted = false;
        if (!mIsSurfaceCreated) {
            return;
        }
        mLoopingAnimationThread.stopDrawing();
    }
}
