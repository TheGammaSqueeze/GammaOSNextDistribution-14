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


import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.view.SurfaceHolder;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.GuardedBy;

import com.android.emergency.action.R;

import java.time.Duration;
import java.time.Instant;

/**
 * Thread that handles the looping "pending" animation with optional count down text.
 */
final class LoopingAnimationThread extends Thread {

    private final SurfaceHolder mSurfaceHolder;
    private final Context mContext;
    private final Paint mBackgroundPaint;
    private final Paint mLoopPaint;
    private final Paint mLoopHeadPaint;
    private final TimeInterpolator mDecelerateInterpolator = new DecelerateInterpolator();
    private final Duration mLoopInterval;
    private final Duration mLoopTrailDelay;

    private volatile boolean mIsDrawing = false;

    @GuardedBy("this")
    private RectF mLoopBounds;

    @GuardedBy("this")
    private float mLoopRadius;

    @GuardedBy("this")
    private float mLoopHeadRadius;

    @GuardedBy("this")
    private final CountDownRenderer mCountDownRenderer;

    @GuardedBy("this")
    private int mTotalDiameter;

    /**
     * Constructor for looping animation thread.
     *
     * @param surfaceHolder Surface holder from surface view.
     * @param context       The context from the view.
     */
    LoopingAnimationThread(
            SurfaceHolder surfaceHolder,
            Context context) {
        super(LoopingAnimationThread.class.getSimpleName());
        this.mSurfaceHolder = surfaceHolder;
        mContext = context;
        mBackgroundPaint = new Paint();
        mBackgroundPaint.setColor(Color.BLACK);
        mBackgroundPaint.setStyle(Paint.Style.FILL);
        mBackgroundPaint.setAntiAlias(true);
        mLoopPaint = new Paint();
        mLoopPaint.setColor(Color.WHITE);
        mLoopPaint.setStyle(Paint.Style.STROKE);
        mLoopPaint.setAntiAlias(true);
        mLoopHeadPaint = new Paint();
        mLoopHeadPaint.setColor(Color.WHITE);
        mLoopHeadPaint.setStyle(Paint.Style.FILL);
        mLoopHeadPaint.setAntiAlias(true);

        mCountDownRenderer = new CountDownRenderer(context);

        mLoopInterval = Duration.ofMillis(
                mContext.getResources().getInteger(R.integer.count_down_view_loop_interval_millis));
        mLoopTrailDelay = Duration.ofMillis(
                mContext.getResources().getInteger(R.integer.count_down_view_loop_delay_millis));
    }

    @Override
    public void run() {
        mIsDrawing = true;
        Instant now = Instant.ofEpochMilli(System.currentTimeMillis());
        updateSize(
                mSurfaceHolder.getSurfaceFrame().width(),
                mSurfaceHolder.getSurfaceFrame().height());
        while (mIsDrawing) {
            if (isInterrupted()) {
                mIsDrawing = false;
                return;
            }
            Canvas canvas = null;
            try {
                synchronized (mSurfaceHolder) {
                    canvas = mSurfaceHolder.lockCanvas(null);
                    draw(canvas, now);
                }
            } finally {
                // Make sure we don't leave the Surface in an inconsistent state.
                if (canvas != null) {
                    mSurfaceHolder.unlockCanvasAndPost(canvas);
                }
            }
        }
    }

    /**
     * Reveals the count down if animation started, otherwise count down will show when animation
     * starts.
     */
    synchronized void showCountDown() {
        mCountDownRenderer.show();
    }

    /** Sets the count down left duration to be drawn. */
    synchronized void setCountDownLeft(Duration timeLeft) {
        if (timeLeft.isNegative() || timeLeft.isZero()) {
            mCountDownRenderer.setCountDownLeft(Duration.ZERO);
        } else {
            mCountDownRenderer.setCountDownLeft(timeLeft);
        }
    }

    /**
     * Draw frame.
     *
     * @param canvas    Canvas to draw on.
     * @param startTime start time of animation.
     */
    private synchronized void draw(Canvas canvas, Instant startTime) {
        if (!mIsDrawing) {
            // It is possible to lose the canvas because surface got destroyed here.
            return;
        }
        // Clear background.
        canvas.drawColor(0, PorterDuff.Mode.CLEAR);
        // Draw background.
        canvas.drawCircle(mLoopBounds.centerX(), mLoopBounds.centerY(), mLoopRadius,
                mBackgroundPaint);

        // Calculate each end of the loop's elapsed time for calculating what arc to draw.

        long progressedMillis =
                Duration.between(
                        startTime, Instant.ofEpochMilli(System.currentTimeMillis())).toMillis();
        long loopHeadT = progressedMillis % mLoopInterval.toMillis();
        long loopTailT = loopHeadT - mLoopTrailDelay.toMillis();

        // 0 means track start, 1 means track end.

        Duration loopTrackInterval = mLoopInterval.minus(mLoopTrailDelay);
        float loopTailTrackRatio =
                loopTailT <= 0f ? 0f : loopTailT / (float) loopTrackInterval.toMillis();
        float loopHeadTrackRatio =
                loopHeadT <= loopTrackInterval.toMillis()
                        ? loopHeadT / (float) loopTrackInterval.toMillis()
                        : 1f;
        // Interpolate and convert track completion ratio to degrees. From a clockwise perspective,
        // tail is starting angle and head is the ending angle so we intentionally swap terminology
        // here.
        float interpolatedSweepAngleStart =
                mDecelerateInterpolator.getInterpolation(loopTailTrackRatio) * 360f;
        float interpolatedSweepAngleEnd = mDecelerateInterpolator.getInterpolation(
                loopHeadTrackRatio) * 360f;
        float finalSweepAngleStart = interpolatedSweepAngleStart - 90f;
        float finalSweepAngleEnd = interpolatedSweepAngleEnd - 90f;
        canvas.drawArc(
                mLoopBounds,
                finalSweepAngleStart,
                finalSweepAngleEnd - finalSweepAngleStart,
                false,
                mLoopPaint);
        float leadingDotX =
                (float) Math.cos(Math.toRadians(finalSweepAngleEnd)) * mLoopRadius
                        + mLoopBounds.centerX();
        float leadingDotY =
                (float) Math.sin(Math.toRadians(finalSweepAngleEnd)) * mLoopRadius
                        + mLoopBounds.centerY();
        canvas.drawCircle(leadingDotX, leadingDotY, mLoopHeadRadius, mLoopHeadPaint);
        if (mCountDownRenderer.isRevealed()) {
            mCountDownRenderer.draw(canvas);
        }
    }

    /** Update size of loop based on new width and height. */
    synchronized void updateSize(int w, int h) {
        float lookTrackDiameterToBoundsRatio =
                mContext.getResources().getFloat(
                        R.dimen.count_down_view_loop_track_diameter_to_bounds_ratio);
        float lookHeadDiameterToBoundsRatio =
                mContext.getResources().getFloat(
                        R.dimen.count_down_view_loop_head_diameter_to_bounds_ratio);
        float lookStrokeWidthToBoundsRatio =
                mContext.getResources().getFloat(
                        R.dimen.count_down_view_loop_stoke_width_to_bounds_ratio);

        mTotalDiameter = Math.min(w, h);
        // Use ratios to calculate loop/track radius.
        mLoopRadius = mTotalDiameter * lookTrackDiameterToBoundsRatio * 0.5f;
        // Use ratios to calculate loop head radius.
        mLoopHeadRadius = mTotalDiameter * lookHeadDiameterToBoundsRatio * 0.5f;
        // Use ratios to calculate loop stroke width.
        mLoopPaint.setStrokeWidth(mTotalDiameter * lookStrokeWidthToBoundsRatio);
        mLoopBounds = new RectF(0, 0, mLoopRadius * 2, mLoopRadius * 2);
        float updatedSizeCenterOffsetX = (w - (mLoopRadius + mLoopHeadRadius) * 2) * 0.5f;
        float updatedSizeCenterOffsetY = (h - (mLoopRadius + mLoopHeadRadius) * 2) * 0.5f;
        mLoopBounds.offset(
                updatedSizeCenterOffsetX + mLoopHeadRadius,
                updatedSizeCenterOffsetY + mLoopHeadRadius);
        // Update bounds for count down text.
        mCountDownRenderer.updateBounds(mLoopBounds, mTotalDiameter);
    }

    /** Stop animation from drawing. */
    void stopDrawing() {
        mIsDrawing = false;
    }
}
