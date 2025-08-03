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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;

import androidx.annotation.GuardedBy;

import com.android.emergency.action.R;

import java.time.Duration;

/**
 * The renderer which renders the text view to show how many seconds left to count down.
 */
public class CountDownRenderer {

    @GuardedBy("this")
    private final Paint mTimerTextPaint;

    @GuardedBy("this")
    private final Paint mUnitTextPaint;

    @GuardedBy("this")
    private RectF mBounds = null;

    @GuardedBy("this")
    private boolean mIsRevealed = false;

    @GuardedBy("this")
    private Duration mCountDownLeft;

    private Context mContext;
    private String mSecondUnitText;
    private float mUnitTextLeftMargin;

    CountDownRenderer(Context context) {
        mContext = context;
        mTimerTextPaint = new Paint();
        mTimerTextPaint.setTextAlign(Paint.Align.CENTER);
        mTimerTextPaint.setTypeface(Typeface.SANS_SERIF);
        mTimerTextPaint.setColor(Color.WHITE);
        mTimerTextPaint.setAntiAlias(true);
        mUnitTextPaint = new Paint();
        mUnitTextPaint.setTypeface(Typeface.SANS_SERIF);
        mUnitTextPaint.setTextAlign(Paint.Align.CENTER);
        mUnitTextPaint.setColor(Color.WHITE);
        mUnitTextPaint.setAntiAlias(true);
        mSecondUnitText = context.getString(R.string.count_down_unit);
    }

    /** Shows the count down rendering. */
    synchronized void show() {
        mIsRevealed = true;
    }

    /** Returns true if count down text should be displayed, false otherwise. */
    synchronized boolean isRevealed() {
        return mIsRevealed;
    }

    /**
     * Draws the count down text.
     *
     * <p>Called from {@link LoopingAnimationThread}.
     */
    synchronized void draw(Canvas canvas) {
        if (!mIsRevealed || mBounds == null || mCountDownLeft == null) {
            return;
        }

        String timerText = Long.toString(mCountDownLeft.getSeconds());
        RectF timerTextBounds = measureText(timerText, mTimerTextPaint);
        RectF unitTextBounds = measureText(mSecondUnitText, mUnitTextPaint);
        // Shift timer text and unit text to share baseline.
        unitTextBounds.offset(0, timerTextBounds.height() - unitTextBounds.height());
        // Calculate final text bound that all text will be drawn inside of.
        RectF finalTextBounds =
                new RectF(
                        0,
                        0,
                        timerTextBounds.width() + unitTextBounds.width() + mUnitTextLeftMargin,
                        timerTextBounds.height());
        // Offset unit text bounds to right of timer text bounds.
        unitTextBounds.offset(timerTextBounds.width() + mUnitTextLeftMargin, 0);

        // Center final text bounds in provided bounds and place timer text and unit text inside.
        finalTextBounds.offset(
                mBounds.left + (mBounds.width() - finalTextBounds.width()) * 0.5f,
                mBounds.bottom - (mBounds.height() - finalTextBounds.height()));
        timerTextBounds.offset(finalTextBounds.left, finalTextBounds.top);
        unitTextBounds.offset(finalTextBounds.left, finalTextBounds.top);

        // Draw text.
        canvas.drawText(
                timerText, timerTextBounds.centerX(), timerTextBounds.bottom, mTimerTextPaint);
        canvas.drawText(
                mSecondUnitText, unitTextBounds.centerX(), unitTextBounds.bottom, mUnitTextPaint);
    }

    /**
     * Sets the bounds text should be drawn in.
     *
     * <p>Called from ui thread.
     */
    synchronized void updateBounds(RectF loopBounds, float totalDiameter) {
        this.mBounds =
                new RectF(loopBounds.left, loopBounds.top, loopBounds.right, loopBounds.bottom);
        float textSizeToBoundsRatio =
                mContext.getResources().getFloat(
                        R.dimen.count_down_view_text_size_to_diameter_ratio);
        float unitTextToTimerTextSizeRatio =
                mContext.getResources().getFloat(
                        R.dimen.count_down_view_unit_text_to_time_text_size_ratio);
        float unitTextLeftMarginToBoundsRatio =
                mContext.getResources().getFloat(
                        R.dimen.count_down_view_unit_text_left_margin_to_diameter_ratio);
        mTimerTextPaint.setTextSize(textSizeToBoundsRatio * totalDiameter);
        mUnitTextPaint.setTextSize(
                textSizeToBoundsRatio * unitTextToTimerTextSizeRatio * totalDiameter);
        mUnitTextLeftMargin = unitTextLeftMarginToBoundsRatio * totalDiameter;
    }

    private static RectF measureText(String text, Paint textPaint) {
        Rect textBounds = new Rect();
        textPaint.getTextBounds(text, 0, text.length(), textBounds);
        RectF textBoundsF = new RectF(textBounds);
        // Readjust text to be aligned to origin as their top left.
        textBoundsF.offset(-textBounds.left, -textBounds.top);
        return textBoundsF;
    }

    synchronized void setCountDownLeft(Duration timeLeft) {
        mCountDownLeft = timeLeft;
    }
}
